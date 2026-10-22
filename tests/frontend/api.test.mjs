import test from "node:test";
import assert from "node:assert/strict";
import { createApi, submission, optionalChannel, listQuery, documentId, taskId, InputError, errorText, LatestRead, formatTime } from "../../src/main/resources/static/api.mjs";

const id = "5e3672b1-9c19-4c56-b2d8-18232f60b406";
const response = (status, body) => new Response(typeof body === "string" ? body : JSON.stringify(body), { status });
const document = {
    documentId: "doc-1", channel: "default", taskId: id, taskStatus: "QUEUED", vectorReady: false, invalidated: false,
    submittedAt: "2026-10-22T02:00:00Z", vectorCompletedAt: null, invalidatedAt: null, hitCount: "9223372036854775807", text: "<script>alert(1)</script>",
};

test("空文与原文保持不变，缺省渠道省略而不是发送空白", () => {
    assert.deepEqual(submission({ documentId: "d", text: "", channel: "" }), { documentId: "d", text: "" });
    assert.equal(submission({ documentId: "d", text: "  原文\nＡ  ", channel: " X " }).text, "  原文\nＡ  ");
    assert.equal(optionalChannel(" X "), "X");
    assert.equal(optionalChannel("\u001c\u00a0\u3000"), undefined);
});

test("渠道空白与 Java 谓词一致，U+0085不是空白且不被裁剪", () => {
    assert.equal(optionalChannel("\u0085"), "\u0085");
    assert.equal(optionalChannel(" \u0085 "), "\u0085");
    for (const code of [9, 13, 28, 31, 32, 160, 5760, 8192, 8202, 8232, 8233, 8239, 8287, 12288]) {
        assert.equal(optionalChannel(String.fromCodePoint(code)), undefined, `U+${code.toString(16)}`);
    }
    assert.equal(optionalChannel("\ufeff"), "\ufeff");
});

test("ID不裁剪，null文本/非法Unicode被拒，字符限额按code point", () => {
    for (const value of [" d", "d ", "../a", "a/b", "中文", ""]) assert.throws(() => documentId(value), InputError);
    assert.equal(documentId("A_1.x-y"), "A_1.x-y");
    assert.throws(() => submission({ documentId: "d", text: null }), InputError);
    assert.throws(() => submission({ documentId: "d", text: "\ud800" }), InputError);
    assert.equal(submission({ documentId: "d", text: "😀".repeat(20000) }).text.length, 40000);
    assert.throws(() => submission({ documentId: "d", text: "😀".repeat(20001) }), InputError);
    assert.throws(() => optionalChannel("😀".repeat(65)), InputError);
});

test("列表缺省全渠道，筛选编码，分页范围对齐服务端", () => {
    assert.equal(listQuery(), "page=0&size=20");
    assert.equal(listQuery({ channel: " A&B ", invalidated: "false", page: 1, size: 100 }), "page=1&size=100&channel=A%26B&invalidated=false");
    for (const page of [-1, 0.5, 2147483648]) assert.throws(() => listQuery({ page }), InputError);
    for (const size of [0, 101, 1.5]) assert.throws(() => listQuery({ size }), InputError);
    assert.throws(() => listQuery({ invalidated: "yes" }), InputError);
});

test("POST仅一次，真实202才接受，显式同源凭据及JSON正文", async () => {
    const calls = [];
    const api = createApi(async (url, init) => { calls.push({ url, init }); return response(202, { documentId: "d", taskId: id, status: "QUEUED" }); });
    const accepted = await api.submit({ documentId: "d", text: "", channel: "\u3000" });
    assert.equal(accepted.taskId, id);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, "/api/documents");
    assert.equal(calls[0].init.credentials, "same-origin");
    assert.equal(calls[0].init.method, "POST");
    assert.equal(calls[0].init.headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(calls[0].init.body), { documentId: "d", text: "" });
});

test("409保留错误且不重提/换ID；429不假成功", async () => {
    for (const [status, code] of [[409, "DOCUMENT_EXISTS"], [429, "CAPACITY_EXCEEDED"]]) {
        let calls = 0;
        const api = createApi(async () => { calls++; return response(status, { code, message: "测试错误", fieldErrors: [] }); });
        await assert.rejects(api.submit({ documentId: "d", text: "x" }), (error) => {
            assert.equal(error.status, status);
            assert.equal(error.code, code);
            assert.match(errorText(error, "submit"), status === 409 ? /不覆盖/ : /容量已满/);
            return true;
        });
        assert.equal(calls, 1);
    }
});

test("网络断开或不可信成功响应视为未知，不自动重试提交", async () => {
    for (const fetchImpl of [async () => { throw new TypeError("断网"); }, async () => response(202, { taskId: "伪ID" })]) {
        let calls = 0;
        const api = createApi(async (...args) => { calls++; return fetchImpl(...args); });
        await assert.rejects(api.submit({ documentId: "d", text: "" }), (error) => {
            assert.match(errorText(error, "submit"), /无法确认文档是否已被接收/);
            return true;
        });
        assert.equal(calls, 1);
    }
});

test("超时停止等待且不自动重试，不声称服务器回滚", async () => {
    let calls = 0;
    const api = createApi(async (_url, { signal }) => {
        calls++;
        return new Promise((_resolve, reject) => signal.addEventListener("abort", () => reject(new Error("中止")), { once: true }));
    }, { timeoutMs: 2 });
    await assert.rejects(api.submit({ documentId: "d", text: "" }), (error) => error.kind === "timeout" && /无法确认/.test(errorText(error, "submit")));
    assert.equal(calls, 1);
});

test("网关非JSON错误兜底，字段错误结构保留", async () => {
    const api = createApi(async () => response(503, "<html>代理错误</html>"));
    await assert.rejects(api.list({}), (error) => error.status === 503 && /服务暂不可用/.test(errorText(error)));
    const validation = createApi(async () => response(400, { code: "VALIDATION_ERROR", message: "参数错误", fieldErrors: [{ field: "channel", message: "渠道错误" }] }));
    await assert.rejects(validation.list({}), (error) => error.fieldErrors[0].field === "channel");
});

test("详情保留超大计数字符串和恶意文本，拒绝数值hitCount", async () => {
    const api = createApi(async () => response(200, document));
    const result = await api.detail("doc-1");
    assert.equal(result.hitCount, "9223372036854775807");
    assert.equal(result.text, "<script>alert(1)</script>");
    const bad = createApi(async () => response(200, { ...document, hitCount: 42 }));
    await assert.rejects(bad.detail("doc-1"), (error) => error.kind === "invalid");
});

test("失效PUT无正文，状态仅由真实响应提供，不调用搜索", async () => {
    const calls = [];
    const api = createApi(async (url, init) => { calls.push({ url, init }); return response(200, { documentId: "doc-1", invalidated: true, invalidatedAt: "2026-10-22T03:00:00Z" }); });
    assert.equal((await api.invalidate("doc-1")).invalidated, true);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].init.method, "PUT");
    assert.equal(calls[0].init.body, undefined);
    assert.equal(calls[0].url, "/api/documents/doc-1/invalidation");
});

test("任务UUID校验，四态和成功文档遵循冻结结构", async () => {
    assert.equal(taskId(id.toUpperCase()), id.toUpperCase());
    assert.throws(() => taskId("不存在"), InputError);
    const view = { taskId: id, documentId: "doc-1", status: "FAILED", submittedAt: document.submittedAt,
        startedAt: null, finishedAt: null, errorCode: "测试错误", errorMessage: "失败原因", document: null };
    const api = createApi(async () => response(200, view));
    assert.equal((await api.task(id)).status, "FAILED");
    const invalid = createApi(async () => response(200, { ...view, status: "SUCCEEDED", document: null }));
    await assert.rejects(invalid.task(id), (error) => error.kind === "invalid");
});

test("旧读响应失效，取消不会被当成服务端事务回滚", () => {
    const slot = new LatestRead();
    const first = slot.begin();
    const second = slot.begin();
    assert.equal(first.current(), false);
    assert.equal(first.signal.aborted, true);
    assert.equal(second.current(), true);
    slot.cancel();
    assert.equal(second.current(), false);
});

test("时间固定显示北京时间，缺失时间不伪造", () => {
    assert.equal(formatTime(null), "—");
    assert.match(formatTime("2026-10-22T02:00:00Z"), /10:00:00/);
});
