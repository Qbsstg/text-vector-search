import test from "node:test";
import assert from "node:assert/strict";
import { TaskTracker } from "../../src/main/resources/static/tasks.mjs";
import { ApiError } from "../../src/main/resources/static/api.mjs";

const tick = async () => { for (let index = 0; index < 8; index++) await Promise.resolve(); };
function clock() {
    let time = 0;
    let next = 0;
    const jobs = new Map();
    return {
        now: () => time,
        setTimer: (fn, delay) => { const id = ++next; jobs.set(id, { fn, due: time + delay }); return id; },
        clearTimer: (id) => jobs.delete(id),
        advance: async (amount) => {
            const end = time + amount;
            while (true) {
                const job = Array.from(jobs.entries()).filter(([, job]) => job.due <= end).sort((a, b) => a[1].due - b[1].due)[0];
                if (!job) break;
                time = job[1].due;
                jobs.delete(job[0]);
                job[1].fn();
                await tick();
            }
            time = end;
            await tick();
        },
    };
}
const task = (id, status = "QUEUED") => ({ taskId: id, documentId: "doc", status });
function tracker(load, options = {}) {
    const time = clock();
    const state = new TaskTracker({ load, ...time, ...options });
    return { state, time };
}

test("默认计时器保留浏览器全局接收者，查询后能刷新并停止", async (context) => {
    const time = clock();
    const scheduled = [];
    const cleared = [];
    // Node 的原生计时器不限制接收者，替换为浏览器式检查，避免注入箭头函数掩盖绑定错误。
    context.mock.method(globalThis, "setTimeout", function (callback, delay) {
        if (this !== globalThis) throw new TypeError("Illegal invocation");
        const id = time.setTimer(callback, delay);
        scheduled.push({ id, delay });
        return id;
    });
    context.mock.method(globalThis, "clearTimeout", function (id) {
        if (this !== globalThis) throw new TypeError("Illegal invocation");
        cleared.push(id);
        time.clearTimer(id);
    });
    let calls = 0;
    const state = new TaskTracker({ now: time.now, load: async (id) => { calls++; return task(id); } });
    await state.query("a");
    assert.equal(calls, 1);
    assert.deepEqual(scheduled.map(({ delay }) => delay), [60000, 2000]);
    await time.advance(1999);
    assert.equal(calls, 1);
    await time.advance(1);
    assert.equal(calls, 2);
    const expiryId = scheduled[0].id;
    const refreshId = scheduled.at(-1).id;
    state.stop("a");
    assert.ok(cleared.includes(expiryId));
    assert.ok(cleared.includes(refreshId));
    await time.advance(60000);
    assert.equal(calls, 2);
    assert.equal(state.get("a").mode, "manual");
    state.dispose();
});

test("响应完成后2秒才刷新，同一任务不重叠", async () => {
    let resolve;
    let calls = 0;
    const { state, time } = tracker(() => { calls++; return new Promise((done) => { resolve = done; }); });
    void state.query("a");
    await tick();
    await time.advance(5000);
    assert.equal(calls, 1);
    void state.query("a");
    await tick();
    assert.equal(calls, 1);
    resolve(task("a"));
    await tick();
    await time.advance(1999);
    assert.equal(calls, 1);
    await time.advance(1);
    assert.equal(calls, 2);
    state.dispose();
});

test("SUCCEEDED和FAILED均立即停止，终态只来自后端", async () => {
    for (const status of ["SUCCEEDED", "FAILED"]) {
        let calls = 0;
        const { state, time } = tracker(async (id) => { calls++; return task(id, status); });
        await state.query("a");
        assert.equal(state.get("a").mode, "terminal");
        await time.advance(100000);
        assert.equal(calls, 1);
        assert.equal(state.get("a").task.status, status);
        state.dispose();
    }
});

test("隐藏暂停，返回可见后查询；隐藏时间计入60秒预算", async () => {
    let calls = 0;
    const { state, time } = tracker(async (id) => { calls++; return task(id); });
    await state.query("a");
    state.setHidden(true);
    await time.advance(10000);
    assert.equal(calls, 1);
    assert.equal(state.get("a").mode, "hidden");
    state.setHidden(false);
    await time.advance(0);
    assert.equal(calls, 2);
    state.setHidden(true);
    await time.advance(50000);
    assert.equal(state.get("a").mode, "timeout");
    state.setHidden(false);
    await time.advance(10000);
    assert.equal(calls, 2);
    state.dispose();
});

test("总预算不因手动刷新或重新启用重置，超时仍允许单次手动查询", async () => {
    let calls = 0;
    const { state, time } = tracker(async (id) => { calls++; return task(id); });
    await state.query("a");
    const deadline = state.get("a").deadline;
    await time.advance(59000);
    await state.query("a", { auto: false });
    assert.equal(state.get("a").deadline, deadline);
    await time.advance(1000);
    assert.equal(state.get("a").mode, "timeout");
    const before = calls;
    await state.query("a");
    assert.equal(calls, before + 1);
    assert.equal(state.get("a").auto, false);
    assert.equal(state.get("a").deadline, deadline);
    await time.advance(10000);
    assert.equal(calls, before + 1);
    state.dispose();
});

test("最多三个任务自动刷新，第四个仅手动查询", async () => {
    const calls = new Map();
    const { state, time } = tracker(async (id) => { calls.set(id, (calls.get(id) ?? 0) + 1); return task(id); });
    for (const id of ["a", "b", "c", "d"]) await state.query(id);
    assert.equal(state.snapshots().filter((record) => record.auto).length, 3);
    assert.equal(state.get("d").mode, "limit");
    await time.advance(2000);
    assert.equal(calls.get("a"), 2);
    assert.equal(calls.get("d"), 1);
    state.dispose();
});

test("网络失败保留RUNNING，有限退避后暂停，不改FAILED", async () => {
    let calls = 0;
    const { state, time } = tracker(async (id) => {
        calls++;
        if (calls === 1) return task(id, "RUNNING");
        throw new ApiError("network", "断网");
    });
    await state.query("a");
    await time.advance(2000);
    assert.equal(state.get("a").task.status, "RUNNING");
    assert.equal(state.get("a").error.kind, "network");
    await time.advance(3999);
    assert.equal(calls, 2);
    await time.advance(1);
    assert.equal(calls, 3);
    await time.advance(8000);
    assert.equal(calls, 4);
    assert.equal(state.get("a").mode, "error");
    assert.equal(state.get("a").task.status, "RUNNING");
    await time.advance(30000);
    assert.equal(calls, 4);
    state.dispose();
});

test("404不自动重试，也不发明FAILED", async () => {
    let calls = 0;
    const { state, time } = tracker(async () => { calls++; throw new ApiError("http", "未找到", { status: 404 }); });
    await state.query("a");
    await time.advance(30000);
    assert.equal(calls, 1);
    assert.equal(state.get("a").task, null);
    assert.equal(state.get("a").mode, "error");
    state.dispose();
});

test("预算耗尽取消在途GET，迟到成功不能覆盖上次状态", async () => {
    let resolve;
    let calls = 0;
    const { state, time } = tracker(async (id) => {
        calls++;
        if (calls === 1) return task(id, "RUNNING");
        return new Promise((done) => { resolve = done; });
    });
    await state.query("a");
    await time.advance(2000);
    await time.advance(58000);
    resolve(task("a", "SUCCEEDED"));
    await tick();
    assert.equal(state.get("a").task.status, "RUNNING");
    assert.equal(state.get("a").mode, "timeout");
    state.dispose();
});

test("手动停止不再刷新，会话最多20项且清除淘汰项轮询", async () => {
    let calls = 0;
    const { state, time } = tracker(async (id) => { calls++; return task(id); });
    await state.query("a");
    state.stop("a");
    await time.advance(5000);
    assert.equal(calls, 1);
    for (let index = 0; index < 20; index++) await state.query(`item-${index}`, { auto: false });
    assert.equal(state.snapshots().length, 20);
    assert.equal(state.get("a"), null);
    state.dispose();
    await time.advance(100000);
    assert.equal(state.snapshots().length, 0);
});

test("隐藏期间新启用自动跟踪不发GET，返回后才读取", async () => {
    let calls = 0;
    const { state, time } = tracker(async (id) => { calls++; return task(id); }, { hidden: true });
    await state.query("a", { initialTask: task("a") });
    assert.equal(calls, 0);
    assert.equal(state.get("a").task.status, "QUEUED");
    state.setHidden(false);
    await time.advance(0);
    assert.equal(calls, 1);
    state.dispose();
});

test("不同任务迟到响应分别保存在自己的记录，不覆盖新选择的任务", async () => {
    let resolveA;
    const { state } = tracker(async (id) => id === "a"
        ? new Promise((done) => { resolveA = done; }) : task(id, "SUCCEEDED"));
    void state.query("a");
    await tick();
    await state.query("b");
    resolveA(task("a", "RUNNING"));
    await tick();
    assert.equal(state.get("b").task.status, "SUCCEEDED");
    assert.equal(state.get("a").task.status, "RUNNING");
    state.dispose();
});
