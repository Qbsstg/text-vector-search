import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import * as apiModule from "../../src/main/resources/static/api.mjs";
import * as vectorModule from "../../src/main/resources/static/vector-info.mjs";
import * as presentation from "../../src/main/resources/static/presentation.mjs";
import * as tasks from "../../src/main/resources/static/tasks.mjs";
import * as search from "../../src/main/resources/static/search.mjs";

const { createApi } = apiModule;
const { validVectorInfo, serializeVector, previewVector, createVectorPanel } = vectorModule;
const taskId = "5e3672b1-9c19-4c56-b2d8-18232f60b406";
const info = (overrides = {}) => ({ embeddingVersion: "合成版本-v2", dimensions: 256, byteLength: 1024,
    zeroVector: false, sha256: "abcd".repeat(16), values: [0.1, -0, ...Array(254).fill(0)], ...overrides });
const document = (overrides = {}) => ({ documentId: "doc-A", channel: "default", taskId, taskStatus: "SUCCEEDED",
    vectorReady: true, invalidated: false, submittedAt: "2026-10-22T02:00:00Z", vectorCompletedAt: "2026-10-22T02:00:01Z",
    invalidatedAt: null, hitCount: "9223372036854775807", text: "合成原文", vectorInfo: info(), ...overrides });
// 保留 JSON 中的 -0，避免测试夹具在发包前先丢失符号。
const response = (body) => new Response(JSON.stringify(body).replace('"values":[0.1,0,', '"values":[0.1,-0,'), { status: 200 });

class Node {
    constructor() {
        this.textContent = "";
        this.hidden = false;
        this.disabled = false;
        this.attributes = new Map();
        this.listeners = new Map();
        this.children = [];
        this.isConnected = true;
        this.classList = { toggle() {} };
    }
    setAttribute(key, value) { this.attributes.set(key, value); }
    getAttribute(key) { return this.attributes.get(key); }
    addEventListener(type, action) { this.listeners.set(type, action); }
    trigger(type, extra = {}) { return this.listeners.get(type)?.({ currentTarget: this, preventDefault() {}, ...extra }); }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; this.textContent = ""; }
    focus() { this.focused = true; }
    querySelectorAll() { return []; }
    querySelector() { return null; }
    showModal() { this.open = true; }
    close() { this.open = false; }
}
function fixture(options = {}) {
    const nodes = Object.fromEntries(["content", "message", "version", "dimensions", "bytes", "zero", "fingerprint", "values", "heading", "toggle", "copyFingerprint", "copyVector"].map((key) => [key, new Node()]));
    const copies = [], messages = [];
    const panel = createVectorPanel(nodes, { writeText: async (value) => { copies.push(value); }, notify: (...args) => messages.push(args), ...options });
    return { nodes, panel, copies, messages };
}

test("VI-01：详情读取真实向量字段并保留负零；仅发送一次GET", async () => {
    const calls = [];
    const api = createApi(async (path, init) => { calls.push([path, init.method]); return response(document()); });
    const detail = await api.detail("doc-A");
    assert.equal(detail.vectorInfo.embeddingVersion, "合成版本-v2");
    assert.equal(Object.is(detail.vectorInfo.values[1], -0), true);
    assert.deepEqual(calls, [["/api/documents/doc-A", "GET"]]);
});

test("VI-02：旧详情缺字段和nullable字段均兼容，不伪造任务失败", async () => {
    for (const vectorReady of [true, false]) for (const vectorInfo of [null, undefined]) {
        const body = document({ vectorReady, vectorInfo });
        const result = await createApi(async () => response(body)).detail("doc-A");
        assert.equal(result.taskStatus, "SUCCEEDED");
        assert.equal(result.vectorInfo, vectorInfo);
    }
});

test("VI-03：成功任务内嵌文档可省略或返回null，不强制提供完整向量", async () => {
    for (const vectorInfo of [undefined, null]) {
        const body = { taskId, documentId: "doc-A", status: "SUCCEEDED", submittedAt: document().submittedAt,
            startedAt: null, finishedAt: null, errorCode: null, errorMessage: null, document: document({ vectorInfo }) };
        assert.equal((await createApi(async () => response(body)).task(taskId)).document.vectorReady, true);
    }
});

test("VI-04：固定维数、字节数、字段类型和小写完整指纹严格校验", () => {
    for (const change of [{ dimensions: 255 }, { dimensions: "256" }, { byteLength: 1023 }, { byteLength: "1024" },
        { embeddingVersion: " " }, { embeddingVersion: null }, { zeroVector: "false" }, { sha256: "a".repeat(63) },
        { sha256: "A".repeat(64) }, { sha256: "g".repeat(64) }, { values: Array(255).fill(0) }, { values: {} }]) {
        assert.equal(validVectorInfo(info(change), document()), false, JSON.stringify(change));
    }
    for (const malformed of [undefined, [], "向量", 1, true]) assert.equal(validVectorInfo(malformed, document()), false);
});

test("VI-05：拒绝非数值、非有限值与float32溢出；允许codec合法有限范围", () => {
    for (const value of [NaN, Infinity, -Infinity, 1e39, -1e39, "0", null]) {
        assert.equal(validVectorInfo(info({ values: [value, ...Array(255).fill(0)] }), document()), false);
    }
    for (const value of [2, -2, Math.fround(3.4028234663852886e38), -Math.fround(3.4028234663852886e38), Math.fround(2 ** -149)]) {
        assert.equal(validVectorInfo(info({ values: [value, ...Array(255).fill(0)] }), document()), true);
    }
});

test("VI-06：零向量标记与数值一致，只有已就绪成功详情可携带向量", () => {
    assert.equal(validVectorInfo(info({ zeroVector: true, values: Array(256).fill(-0) }), document()), true);
    assert.equal(validVectorInfo(info({ zeroVector: true }), document()), false);
    assert.equal(validVectorInfo(info({ values: Array(256).fill(0) }), document()), false);
    assert.equal(validVectorInfo(info(), document({ vectorReady: false })), false);
    for (const taskStatus of ["RUNNING", "QUEUED", "FAILED"]) assert.equal(validVectorInfo(info(), document({ taskStatus })), false);
});

test("VI-07：畸形向量响应被API拒绝，未知读取结果不重试", async () => {
    let calls = 0;
    const api = createApi(async () => { calls++; return response(document({ vectorInfo: info({ dimensions: 255 }) })); });
    await assert.rejects(api.detail("doc-A"), (error) => error.kind === "invalid");
    assert.equal(calls, 1);
});

test("VI-08：所有字段按服务端值显示，恶意版本仅进入文本节点", () => {
    const f = fixture();
    const version = '<img src=x onerror="throw 1">';
    f.panel.set(document({ vectorInfo: info({ embeddingVersion: version }) }));
    assert.equal(f.nodes.content.hidden, false);
    assert.equal(f.nodes.version.textContent, version);
    assert.equal(f.nodes.dimensions.textContent, "256 维 · float32");
    assert.equal(f.nodes.bytes.textContent, "1024 字节");
    assert.equal(f.nodes.zero.textContent, "否");
    assert.equal(f.nodes.fingerprint.textContent, "abcd".repeat(16));
    assert.deepEqual(f.nodes.version.children, []);
});

test("VI-09：未生成与服务未提供按vectorReady解释，缺字段与null均不伪造结果", () => {
    for (const vectorReady of [true, false]) for (const vectorInfo of [null, undefined]) {
        const f = fixture();
        f.panel.set(document({ vectorReady, vectorInfo }));
        assert.equal(f.nodes.content.hidden, true);
        assert.match(f.nodes.message.textContent, vectorReady ? /服务未提供向量详情/ : /尚未生成/);
        assert.equal(f.nodes.copyVector.disabled, true);
        assert.equal(f.nodes.fingerprint.textContent, "");
    }
});

test("VI-10：前8维六位小数预览与全部256维展开收起，不重新计算向量", () => {
    const f = fixture();
    f.panel.set(document());
    assert.equal(f.nodes.values.textContent.split("\n").length, 8);
    assert.match(f.nodes.values.textContent, /0\.100000/);
    assert.match(f.nodes.values.textContent, /-0\.000000/);
    assert.equal(f.nodes.toggle.getAttribute("aria-expanded"), "false");
    f.nodes.toggle.trigger("click");
    assert.equal(f.nodes.values.textContent.split("\n").length, 256);
    assert.equal(f.nodes.toggle.getAttribute("aria-expanded"), "true");
    f.nodes.toggle.trigger("click");
    assert.equal(f.nodes.values.textContent.split("\n").length, 8);
});

test("VI-11：复制完整向量保留全部float32往返精度、负零及极值", async () => {
    const values = [0.1, -0, Math.fround(2 ** -149), -Math.fround(3.4028234663852886e38), ...Array(252).fill(0.123456789)];
    const f = fixture();
    f.panel.set(document({ vectorInfo: info({ values }) }));
    await f.nodes.copyVector.trigger("click");
    const copied = JSON.parse(f.copies[0]);
    assert.equal(copied.length, 256);
    values.forEach((value, index) => assert.equal(Object.is(copied[index], Math.fround(value)), true, String(index)));
    assert.match(f.copies[0], /0\.10000000149011612/);
    assert.equal(Object.is(copied[1], -0), true);
    assert.equal(f.nodes.values.textContent.split("\n").length, 8);
    assert.equal(serializeVector([-0]), "[-0]");
    assert.equal(previewVector([-0]), "  0    -0.000000");
});

test("VI-12：指纹复制完整64位，只调用本地剪贴板", async () => {
    const f = fixture();
    f.panel.set(document());
    await f.nodes.copyFingerprint.trigger("click");
    assert.deepEqual(f.copies, ["abcd".repeat(16)]);
    assert.match(f.messages[0][0], /完整 SHA-256/);
});

test("VI-13：清空和新详情均移除旧向量、指纹与展开状态", () => {
    const f = fixture();
    f.panel.set(document());
    f.nodes.toggle.trigger("click");
    f.panel.clear();
    assert.equal(f.nodes.values.textContent, "");
    assert.equal(f.nodes.fingerprint.textContent, "");
    assert.equal(f.nodes.content.hidden, true);
    f.panel.set(document({ vectorInfo: info({ sha256: "1".repeat(64) }) }));
    assert.equal(f.nodes.values.textContent.split("\n").length, 8);
    assert.equal(f.nodes.toggle.getAttribute("aria-expanded"), "false");
    assert.equal(f.nodes.fingerprint.textContent, "1".repeat(64));
    f.nodes.toggle.trigger("click");
    f.panel.set(document());
    assert.equal(f.nodes.values.textContent.split("\n").length, 8);
});

test("VI-14：复制失败仅提示当前文档；迟到剪贴板回调不能污染重开详情", async () => {
    let resolve;
    const pending = new Promise((done) => { resolve = done; });
    const f = fixture({ writeText: () => pending });
    f.panel.set(document());
    const copying = f.nodes.copyVector.trigger("click");
    f.panel.clear();
    f.panel.set(document({ documentId: "doc-B" }));
    resolve();
    await copying;
    assert.deepEqual(f.messages, []);
    const denied = fixture({ writeText: async () => { throw new Error("拒绝剪贴板"); } });
    denied.panel.set(document());
    await denied.nodes.copyVector.trigger("click");
    assert.match(denied.messages[0][0], /未允许复制完整向量/);
    assert.equal(denied.messages[0][1], true);
});

/** 用最小DOM适配器执行真实app.js事件装配，不把模拟DOM当作浏览器布局或焦点验收。 */
async function appFixture() {
    const html = await readFile(new URL("../../src/main/resources/static/index.html", import.meta.url), "utf8");
    const source = await readFile(new URL("../../src/main/resources/static/app.js", import.meta.url), "utf8");
    const nodes = new Map([...html.matchAll(/\bid="([^"]+)"/g)].map((match) => [match[1], new Node()]));
    const pending = [], calls = [];
    const api = createApi((path, init) => {
        calls.push([path, init.method]);
        return new Promise((resolve) => pending.push({ resolve, signal: init.signal }));
    });
    const fakeDocument = { hidden: false, activeElement: new Node(), getElementById: (id) => nodes.get(id),
        querySelectorAll: () => [], addEventListener() {}, createElement: () => new Node() };
    const dependencies = { ...apiModule, ...presentation, ...vectorModule, ...tasks, ...search, createApi: () => api,
        dialogLifecycle: (dialog, options) => presentation.dialogLifecycle(dialog, { ...options, activeElement: () => fakeDocument.activeElement }) };
    const names = Object.keys(dependencies);
    const run = new Function("document", "window", "FormData", ...names, source.replace(/^import .*;\n/gm, ""));
    run(fakeDocument, { addEventListener() {} }, class { constructor(form) { return Object.entries(form.values); } }, ...names.map((name) => dependencies[name]));
    return { nodes, calls, pending, open(id) {
        nodes.get("detail-form").values = { documentId: id };
        nodes.get("detail-form").trigger("submit");
    } };
}
const flush = () => new Promise((done) => setImmediate(done));

test("VI-15：真实详情装配拒绝A迟到覆盖B，刷新收起且关闭清空", async () => {
    const f = await appFixture();
    f.open("doc-A");
    f.open("doc-B");
    assert.equal(f.pending[0].signal.aborted, true);
    f.pending[1].resolve(response(document({ documentId: "doc-B", text: "B原文", vectorInfo: info({ sha256: "b".repeat(64) }) })));
    await flush();
    assert.equal(f.nodes.get("vector-fingerprint").textContent, "b".repeat(64));
    f.pending[0].resolve(response(document()));
    await flush();
    assert.equal(f.nodes.get("detail-text").textContent, "B原文");
    assert.equal(f.nodes.get("vector-fingerprint").textContent, "b".repeat(64));
    f.nodes.get("toggle-vector").trigger("click");
    assert.equal(f.nodes.get("vector-values").textContent.split("\n").length, 256);
    f.nodes.get("detail-refresh").trigger("click");
    assert.equal(f.nodes.get("vector-values").textContent, "");
    f.pending[2].resolve(response(document({ documentId: "doc-B" })));
    await flush();
    assert.equal(f.nodes.get("vector-values").textContent.split("\n").length, 8);
    f.nodes.get("close-detail").trigger("click");
    assert.equal(f.nodes.get("detail-content").hidden, true);
    assert.equal(f.nodes.get("vector-fingerprint").textContent, "");
    assert.equal(f.nodes.get("detail-text").textContent, "");
    assert.ok(f.calls.every(([, method]) => method === "GET"));
});

test("VI-16：真实Escape关闭与立即重开隔离旧响应，不泄露旧内容", async () => {
    const f = await appFixture();
    f.open("doc-A");
    f.nodes.get("detail-dialog").trigger("cancel");
    assert.equal(f.pending[0].signal.aborted, true);
    f.open("doc-B");
    f.pending[0].resolve(response(document()));
    await flush();
    assert.equal(f.nodes.get("vector-fingerprint").textContent, "");
    assert.equal(f.nodes.get("detail-content").hidden, true);
    f.pending[1].resolve(response(document({ documentId: "doc-B" })));
    await flush();
    assert.equal(f.nodes.get("detail-content").hidden, false);
    assert.equal(f.nodes.get("detail-text").textContent, "合成原文");
});
