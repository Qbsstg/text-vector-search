import test from "node:test";
import assert from "node:assert/strict";
import { createApi, searchInput, InputError, ApiError, errorText } from "../../src/main/resources/static/api.mjs";
import { SearchSession, formatScore } from "../../src/main/resources/static/search.mjs";

const response = (status, value) => new Response(JSON.stringify(value), { status });
const hit = (documentId, score = 0, snippet = "原文摘要") => ({ documentId, score, channel: "default", snippet });
const query = { text: "", topK: 5, channel: "" };

test("搜索空文合法且原样发送，省略/null/表单空白渠道均不指定渠道", () => {
    for (const channel of [undefined, null, "", " \u3000\u001c"]) {
        assert.deepEqual(searchInput({ ...query, channel }), { text: "", topK: 5 });
    }
    assert.deepEqual(searchInput({ text: "  Ａ\n ", topK: 100, channel: " X " }), { text: "  Ａ\n ", topK: 100, channel: "X" });
    assert.equal(searchInput({ ...query, channel: "\u0085" }).channel, "\u0085");
});

test("Top-K拒绝空值/小数/字符串/越界，文本拒绝null及不完整Unicode", () => {
    for (const topK of [undefined, null, "5", "", 0, -1, 1.5, 101, NaN, Infinity]) {
        assert.throws(() => searchInput({ ...query, topK }), (error) => error instanceof InputError && error.fieldErrors[0].field === "topK");
    }
    for (const text of [null, undefined, "\ud800", "x".repeat(20001)]) assert.throws(() => searchInput({ ...query, text }), InputError);
});

test("真实POST搜索正文是整数topK；零负分及显示同分保留完整后端顺序", async () => {
    const hits = [hit("b", 0.123449), hit("a", 0.123441), hit("zero", 0), hit("negative", -0.3)];
    const calls = [];
    const api = createApi(async (url, init) => { calls.push({ url, init }); return response(200, { hits }); });
    const result = await api.search(query);
    assert.deepEqual(result.hits, hits);
    assert.equal(formatScore(result.hits[0].score), "0.1234");
    assert.equal(formatScore(result.hits[1].score), "0.1234");
    assert.equal(formatScore(result.hits[3].score), "-0.3000");
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, "/api/search");
    assert.equal(calls[0].init.method, "POST");
    assert.equal(calls[0].init.credentials, "same-origin");
    assert.deepEqual(JSON.parse(calls[0].init.body), { text: "", topK: 5 });
});

test("空命中合法，不制造结果；摘要中的HTML保持纯字符串及emoji边界", async () => {
    const empty = createApi(async () => response(200, { hits: [] }));
    assert.deepEqual(await empty.search(query), { hits: [] });
    const snippet = "<img src=x onerror=alert(1)>" + "😀".repeat(100);
    const api = createApi(async () => response(200, { hits: [hit("d", 0, snippet)] }));
    assert.equal((await api.search(query)).hits[0].snippet, snippet);
});

test("拒绝非有限/越界分数、缺少摘要及超Top-K的成功响应，不伪报成功", async () => {
    for (const hits of [[hit("d", null)], [hit("d", 1.1)], [{ documentId: "d", channel: "default", score: 0 }],
        [hit("d", 0, "😀".repeat(201))], [hit("a"), hit("b")]]) {
        const api = createApi(async () => response(200, { hits }));
        await assert.rejects(api.search({ ...query, topK: 1 }), (error) => error.kind === "invalid" && /可能已计数/.test(errorText(error, "search")));
    }
});

test("搜索网络失败/超时/RESULT_UNKNOWN只请求一次并提示可能已计数", async () => {
    const failures = [
        async () => { throw new TypeError("连接中断"); },
        async () => response(503, { code: "RESULT_UNKNOWN", message: "未知", fieldErrors: [] }),
    ];
    for (const fail of failures) {
        let calls = 0;
        const api = createApi(async (...args) => { calls++; return fail(...args); });
        await assert.rejects(api.search(query), (error) => /可能已计数/.test(errorText(error, "search")) && /不会自动重试/.test(errorText(error, "search")));
        assert.equal(calls, 1);
    }
    let calls = 0;
    const api = createApi((_url, { signal }) => {
        calls++;
        return new Promise((_resolve, reject) => signal.addEventListener("abort", () => reject(new Error("取消等待")), { once: true }));
    }, { timeoutMs: 2 });
    await assert.rejects(api.search(query), (error) => error.kind === "timeout" && /取消浏览器等待不表示后台回滚/.test(errorText(error, "search")));
    assert.equal(calls, 1);
});

test("SEARCH_BUSY/服务未就绪/访问受限与字段错误不变成文档提交反馈", async () => {
    for (const [status, code, pattern] of [[429, "SEARCH_BUSY", /搜索正忙/], [503, "SERVICE_NOT_READY", /搜索暂不可用/], [403, "ORIGIN_NOT_ALLOWED", /访问受限/]]) {
        const api = createApi(async () => response(status, { code, message: "测试消息", fieldErrors: [] }));
        await assert.rejects(api.search(query), (error) => pattern.test(errorText(error, "search")) && !/文档加入/.test(errorText(error, "search")));
    }
    const api = createApi(async () => response(400, { code: "VALIDATION_ERROR", message: "参数错误", fieldErrors: [{ field: "topK", message: "必须整数" }] }));
    await assert.rejects(api.search(query), (error) => error.fieldErrors[0].field === "topK");
});

test("只允许一个在途搜索，返回时以原条件快照展示并测量实际等待时长", async () => {
    let finish;
    let calls = 0;
    let now = 10;
    const session = new SearchSession({ now: () => now, load: async () => {
        calls++;
        return new Promise((resolve) => { finish = resolve; });
    } });
    const first = session.submit(query);
    assert.equal(session.snapshot().busy, true);
    assert.equal(await session.submit({ ...query, text: "另一个查询" }), false);
    assert.equal(calls, 1);
    session.edit({ ...query, text: "编辑后的条件" });
    now = 35.5;
    finish({ hits: [hit("d")] });
    assert.equal(await first, true);
    assert.equal(session.snapshot().result.query.text, "");
    assert.equal(session.snapshot().result.duration, 25.5);
    assert.equal(session.snapshot().draftChanged, true);
});

test("参数错误不调用API，修正后可明确提交", async () => {
    let calls = 0;
    const session = new SearchSession({ load: async () => { calls++; return { hits: [] }; } });
    assert.equal(await session.submit({ ...query, topK: 0 }), false);
    assert.equal(calls, 0);
    session.edit(query);
    assert.equal(session.snapshot().error, null);
    assert.equal(await session.submit(query), true);
    assert.equal(calls, 1);
});

test("编辑条件和文档失效均不自动搜索，旧结果不被删除或重排", async () => {
    let calls = 0;
    const hits = [hit("b", 0.8), hit("a", -0.4)];
    const session = new SearchSession({ load: async () => { calls++; return { hits }; } });
    await session.submit(query);
    session.edit({ ...query, channel: "other" });
    session.markInvalidated("b");
    assert.equal(calls, 1);
    assert.deepEqual(session.snapshot().result.hits, hits);
    assert.deepEqual(session.snapshot().changedIds, ["b"]);
    assert.equal(session.snapshot().draftChanged, true);
});

test("在途检索后文档先失效，迟到响应仍标历史结果且不自动重搜", async () => {
    let finish;
    let calls = 0;
    const session = new SearchSession({ load: () => { calls++; return new Promise((resolve) => { finish = resolve; }); } });
    const pending = session.submit(query);
    session.markInvalidated("d");
    finish({ hits: [hit("d")] });
    await pending;
    assert.deepEqual(session.snapshot().changedIds, ["d"]);
    assert.equal(calls, 1);
});

test("搜索失败保留上次结果，未知结果不从累计数推断或重试", async () => {
    let calls = 0;
    const session = new SearchSession({ load: async () => {
        calls++;
        if (calls === 1) return { hits: [hit("d")] };
        throw new ApiError("http", "未知", { status: 503, code: "RESULT_UNKNOWN" });
    } });
    await session.submit(query);
    const old = session.snapshot().result;
    assert.equal(await session.submit({ ...query, text: "新查询" }), false);
    assert.equal(session.snapshot().result, old);
    assert.equal(session.snapshot().busy, false);
    assert.match(errorText(session.snapshot().error, "search"), /不能从累计命中次数确认/);
    assert.equal(calls, 2);
});

test("详情命中数始终取服务器十进制字符串，搜索与读取不在本地合成次数", async () => {
    const paths = [];
    const api = createApi(async (url) => {
        paths.push(url);
        if (url === "/api/search") return response(200, { hits: [hit("d")] });
        return response(200, { documentId: "d", channel: "default", taskId: "5e3672b1-9c19-4c56-b2d8-18232f60b406",
            taskStatus: "SUCCEEDED", vectorReady: true, invalidated: false, submittedAt: "2026-10-22T01:00:00Z",
            vectorCompletedAt: "2026-10-22T01:01:00Z", invalidatedAt: null, hitCount: "9007199254740993", text: "" });
    });
    await api.search(query);
    assert.equal((await api.detail("d")).hitCount, "9007199254740993");
    assert.equal((await api.detail("d")).hitCount, "9007199254740993");
    assert.deepEqual(paths, ["/api/search", "/api/documents/d", "/api/documents/d"]);
});
