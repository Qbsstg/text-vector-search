import test from "node:test";
import assert from "node:assert/strict";
import { createApi, InputError, errorText } from "../../src/main/resources/static/api.mjs";

// 仅使用独立合成数据，不依赖用户本地尚未公开的测试材料或运行数据库。
const taskId = "5e3672b1-9c19-4c56-b2d8-18232f60b406";
const submittedAt = "2026-10-22T02:00:00Z";
const response = (status, body) => new Response(JSON.stringify(body), { status });
const document = (documentId) => ({
    documentId, channel: "default", taskId, taskStatus: "QUEUED", vectorReady: false, invalidated: false,
    submittedAt, vectorCompletedAt: null, invalidatedAt: null, hitCount: "0", text: "合成原文",
});

test("FR-N01 UI-07：合法标点和128位文档ID保持原路径，不裁剪或改变大小写", async () => {
    for (const id of ["A_1.x-y", "Z" + "a".repeat(127)]) {
        const calls = [];
        const api = createApi(async (path, init) => {
            calls.push([path, init.method]);
            return response(200, init.method === "PUT"
                ? { documentId: id, invalidated: true, invalidatedAt: submittedAt } : document(id));
        });
        assert.equal((await api.detail(id)).documentId, id);
        assert.equal((await api.invalidate(id)).documentId, id);
        assert.deepEqual(calls, [[`/api/documents/${id}`, "GET"], [`/api/documents/${id}/invalidation`, "PUT"]]);
    }
    const upper = taskId.toUpperCase();
    const calls = [];
    const api = createApi(async (path) => {
        calls.push(path);
        return response(200, { taskId, documentId: "A_1.x-y", status: "QUEUED", submittedAt,
            startedAt: null, finishedAt: null, errorCode: null, errorMessage: null, document: null });
    });
    assert.equal((await api.task(upper)).taskId, taskId);
    assert.deepEqual(calls, [`/api/tasks/${upper}`]);
});

test("FR-N02 UI-02/UI-07：路径分隔与转义输入在发包前被契约拒绝，不尝试替代地址", () => {
    let calls = 0;
    const api = createApi(async () => { calls++; return response(500, {}); });
    for (const id of ["../a", "a/b", "a\\b", "a%2Fb", "a?x=1", "a#x", "a:8080", " a", "a ",
        "中", "a".repeat(129), "", null, undefined]) {
        for (const operation of [() => api.detail(id), () => api.invalidate(id)]) {
            assert.throws(operation, (error) => error instanceof InputError && error.fieldErrors[0].field === "documentId");
        }
    }
    for (const id of [`${taskId}?x=1`, `${taskId}#x`, `${taskId}%2F`, ` ${taskId}`, "../task", "", null]) {
        assert.throws(() => api.task(id), (error) => error instanceof InputError && error.fieldErrors[0].field === "taskId");
    }
    assert.equal(calls, 0);
});

test("FR-N03 UI-07：渠道中的Unicode和URL保留字符只进入查询参数，不改变路径或筛选", async () => {
    const channel = "合成 A&B/+?%#=_";
    const calls = [];
    const api = createApi(async (path, init) => {
        calls.push([path, init.method]);
        return response(200, { items: [], page: 1, size: 50, totalElements: 0 });
    });
    await api.list({ channel, invalidated: "false", page: 1, size: 50 });
    assert.equal(calls.length, 1);
    const url = new URL(calls[0][0], "https://example.invalid");
    assert.equal(url.origin, "https://example.invalid");
    assert.equal(url.pathname, "/api/documents");
    assert.equal(url.hash, "");
    assert.deepEqual([...url.searchParams], [["page", "1"], ["size", "50"], ["channel", channel], ["invalidated", "false"]]);
    assert.equal(calls[0][1], "GET");
});

test("FR-N04 UI-06：错误消息和字段错误保持原字符串，非法字段结构被过滤且不重发", async () => {
    const message = '<img src="data:," onerror="window.__frontend_error_probe__=1">';
    const code = "<svg onload=window.__frontend_error_probe__=2>";
    const fieldMessage = "<script>window.__frontend_error_probe__=3</script>";
    const unusualField = 'channel\"][data-probe="合成';
    let calls = 0;
    const api = createApi(async () => {
        calls++;
        return response(400, { code, message, fieldErrors: [
            { field: "channel", message: fieldMessage }, { field: unusualField, message },
            null, "错误结构", { field: 1, message }, { field: "text", message: {} },
        ] });
    });
    // 此处只断言解析边界；错误在 DOM 中无脚本执行仍须真实 Chrome 另验。
    await assert.rejects(api.list({}), (error) => {
        assert.equal(error.status, 400);
        assert.equal(error.message, message);
        assert.equal(error.code, code);
        assert.deepEqual(error.fieldErrors, [{ field: "channel", message: fieldMessage }, { field: unusualField, message }]);
        assert.equal(errorText(error), `HTTP 400 · ${code}：${message}`);
        return true;
    });
    assert.equal(calls, 1);
});
