import test from "node:test";
import assert from "node:assert/strict";
import { dialogLifecycle, taskBadgeClass, taskErrorFields } from "../../src/main/resources/static/presentation.mjs";
import { LatestRead } from "../../src/main/resources/static/api.mjs";

// 模拟原生 close 事件排队，专门覆盖关闭后立即重开这一竞态，不使用定时等待证明正确性。
function fixture() {
    const listeners = new Map();
    const pending = [];
    const opener = { isConnected: true, focused: 0, focus() { this.focused++; } };
    const dialog = {
        open: false, shows: 0,
        addEventListener(type, callback) { listeners.set(type, callback); },
        showModal() { this.open = true; this.shows++; },
        close() { this.open = false; pending.push(() => listeners.get("close")?.()); },
    };
    return { dialog, opener, flush: () => { while (pending.length) pending.shift()(); },
        cancel: () => { let prevented = false; listeners.get("cancel")({ preventDefault() { prevented = true; } }); return prevented; } };
}

test("UI-R01：重复展示同一弹窗不覆盖触发者，关闭只清理一次并恢复焦点", () => {
    const f = fixture();
    let dismissed = 0;
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, onDismiss: () => dismissed++ });
    modal.open();
    modal.open();
    assert.equal(f.dialog.shows, 1);
    modal.close();
    assert.equal(dismissed, 1);
    assert.equal(f.opener.focused, 1);
    f.flush();
    modal.close();
    assert.equal(dismissed, 1);
    assert.equal(f.opener.focused, 1);
});

test("UI-R02：关闭同步取消旧详情读取，迟到响应不能更新关闭内容", async () => {
    const f = fixture();
    const latest = new LatestRead();
    let resolve;
    const response = new Promise((done) => { resolve = done; });
    const rendered = [];
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, onDismiss: () => latest.cancel() });
    modal.open();
    const read = latest.begin();
    const work = response.then((value) => { if (read.current()) rendered.push(value); });
    modal.close();
    assert.equal(read.signal.aborted, true);
    resolve("旧详情");
    await work;
    assert.deepEqual(rendered, []);
});

test("UI-R03：旧 close 事件不能取消重开后的新详情读取或恢复旧焦点", () => {
    const f = fixture();
    const latest = new LatestRead();
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, onDismiss: () => latest.cancel() });
    modal.open();
    const old = latest.begin();
    modal.close();
    modal.open();
    const fresh = latest.begin();
    f.flush();
    assert.equal(old.current(), false);
    assert.equal(fresh.current(), true);
    assert.equal(f.dialog.open, true);
    assert.equal(f.opener.focused, 1);
});

test("UI-R04：Escape 经过统一关闭路径，确认窗取消不会触发写操作", () => {
    const f = fixture();
    let confirmation = "合成文档";
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, onDismiss: () => { confirmation = null; } });
    modal.open();
    assert.equal(f.cancel(), true);
    assert.equal(f.dialog.open, false);
    assert.equal(confirmation, null);
    assert.equal(f.opener.focused, 1);
});

test("UI-R05：触发节点因历史结果重绘被移除时恢复到备用导航", () => {
    const f = fixture();
    const fallback = { isConnected: true, focused: 0, focus() { this.focused++; } };
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, fallbackFocus: () => fallback });
    modal.open();
    f.opener.isConnected = false;
    modal.close();
    assert.equal(f.opener.focused, 0);
    assert.equal(fallback.focused, 1);
});

test("UI-R06：关闭后重开的提交会话不允许旧请求移动字段焦点", () => {
    const f = fixture();
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener });
    modal.open();
    const oldRequestVisible = modal.capture();
    assert.equal(oldRequestVisible(), true);
    modal.close();
    assert.equal(oldRequestVisible(), false);
    modal.open();
    const newRequestVisible = modal.capture();
    f.flush();
    assert.equal(oldRequestVisible(), false);
    assert.equal(newRequestVisible(), true);
});

test("UI-R07：原生直接关闭也执行清理；已脱离页面的节点不再获取焦点", () => {
    const f = fixture();
    let dismissed = 0;
    const modal = dialogLifecycle(f.dialog, { activeElement: () => f.opener, onDismiss: () => dismissed++ });
    modal.open();
    f.opener.isConnected = false;
    f.dialog.close();
    f.flush();
    assert.equal(dismissed, 1);
    assert.equal(f.opener.focused, 0);
});

test("UI-R08：徽章只映射真实四态，不从空值或文档属性伪造成功", () => {
    assert.equal(taskBadgeClass("QUEUED"), "badge badge-pending");
    assert.equal(taskBadgeClass("RUNNING"), "badge badge-pending");
    assert.equal(taskBadgeClass("SUCCEEDED"), "badge badge-success");
    assert.equal(taskBadgeClass("FAILED"), "badge badge-failed");
    for (const unknown of [null, undefined, "", "invalidated", "vectorReady"]) assert.equal(taskBadgeClass(unknown), "badge");
});

test("UI-R09：只展示真实存在的业务错误，正常任务不占空行且不按状态隐去错误", () => {
    for (const task of [null, undefined, {}, { status: "SUCCEEDED", errorCode: null, errorMessage: null },
        { status: "RUNNING", errorCode: "", errorMessage: "" }]) assert.deepEqual(taskErrorFields(task), []);
    assert.deepEqual(taskErrorFields({ status: "FAILED", errorCode: "ALGORITHM_ERROR", errorMessage: "合成错误" }),
        [["业务错误码", "ALGORITHM_ERROR"], ["业务错误信息", "合成错误"]]);
    assert.deepEqual(taskErrorFields({ errorCode: "ALGORITHM_ERROR" }), [["业务错误码", "ALGORITHM_ERROR"]]);
    assert.deepEqual(taskErrorFields({ status: "SUCCEEDED", errorMessage: "异常快照仍保留原消息" }),
        [["业务错误信息", "异常快照仍保留原消息"]]);
});
