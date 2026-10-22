import { isTerminal } from "./api.mjs";

/**
 * 每个任务只有一个在途 GET。自动刷新预算从首次启用起按墙钟累计，隐藏页面和手动刷新均不重置。
 * 网络异常仅记录读取错误，绝不改写服务端任务状态；写请求也不由此模块重试。
 */
export class TaskTracker {
    constructor({ load, onChange = () => {}, now = Date.now, setTimer = setTimeout, clearTimer = clearTimeout,
        hidden = false, interval = 2000, budget = 60000, maxAuto = 3, maxRecords = 20, maxFailures = 3 } = {}) {
        this.load = load;
        this.onChange = onChange;
        this.now = now;
        this.setTimer = setTimer;
        this.clearTimer = clearTimer;
        this.hidden = hidden;
        Object.assign(this, { interval, budget, maxAuto, maxRecords, maxFailures });
        this.records = new Map();
    }

    snapshots() {
        return Array.from(this.records.values()).reverse().map((record) => this.snapshot(record));
    }

    snapshot(record) {
        return { id: record.id, task: record.task, error: record.error, mode: record.mode,
            busy: Boolean(record.pending), deadline: record.deadline, auto: record.auto };
    }

    get(id) {
        const record = this.records.get(id);
        return record ? this.snapshot(record) : null;
    }

    emit() { this.onChange(this.snapshots()); }

    ensure(id) {
        if (!this.records.has(id)) {
            if (this.records.size >= this.maxRecords) this.forget(this.records.keys().next().value);
            this.records.set(id, { id, task: null, error: null, mode: "manual", auto: false,
                deadline: null, timer: null, expiry: null, pending: null, controller: null, failures: 0 });
        }
        return this.records.get(id);
    }

    async query(id, { auto = true, initialTask = null } = {}) {
        const record = this.ensure(id);
        if (initialTask && !record.task) record.task = initialTask;
        if (auto) this.enable(record);
        this.clearTimer(record.timer);
        record.timer = null;
        if (this.hidden && auto && record.auto) { this.emit(); return; }
        return this.run(record);
    }

    enable(record) {
        if (isTerminal(record.task?.status)) { record.mode = "terminal"; return; }
        if (record.deadline !== null && this.now() >= record.deadline) { this.stop(record.id, "timeout"); return; }
        if (!record.auto && Array.from(this.records.values()).filter((item) => item.auto).length >= this.maxAuto) {
            record.mode = "limit";
            return;
        }
        if (record.deadline === null) record.deadline = this.now() + this.budget;
        record.auto = true;
        record.mode = this.hidden ? "hidden" : "polling";
        this.clearTimer(record.expiry);
        record.expiry = this.setTimer(() => this.stop(record.id, "timeout"), Math.max(0, record.deadline - this.now()));
    }

    async run(record) {
        if (record.pending) return record.pending;
        const controller = new AbortController();
        record.controller = controller;
        const work = Promise.resolve().then(async () => {
            try {
                const task = await this.load(record.id, controller.signal);
                if (this.records.get(record.id) !== record || controller.signal.aborted) return;
                record.task = task;
                record.error = null;
                record.failures = 0;
                if (isTerminal(task.status)) this.stop(record.id, "terminal", false);
            } catch (error) {
                if (this.records.get(record.id) !== record || controller.signal.aborted) return;
                record.error = error;
                record.failures += 1;
                const retryable = error.kind === "network" || error.kind === "timeout" ||
                    (error.kind === "http" && error.status >= 500);
                if (!retryable || record.failures >= this.maxFailures) this.stop(record.id, "error", false);
            } finally {
                if (this.records.get(record.id) === record) {
                    record.pending = null;
                    record.controller = null;
                    this.schedule(record);
                    this.emit();
                }
            }
        });
        record.pending = work;
        this.emit();
        return work;
    }

    schedule(record, immediate = false) {
        this.clearTimer(record.timer);
        record.timer = null;
        if (!record.auto) return;
        if (this.now() >= record.deadline) { this.stop(record.id, "timeout", false); return; }
        if (this.hidden) { record.mode = "hidden"; return; }
        record.mode = "polling";
        const delay = immediate ? 0 : Math.min(this.interval * (2 ** record.failures), 8000);
        record.timer = this.setTimer(() => {
            record.timer = null;
            if (!record.auto || this.hidden) return;
            if (this.now() >= record.deadline) { this.stop(record.id, "timeout"); return; }
            void this.run(record);
        }, delay);
    }

    stop(id, reason = "manual", emit = true) {
        const record = this.records.get(id);
        if (!record) return;
        record.auto = false;
        record.mode = reason;
        this.clearTimer(record.timer);
        this.clearTimer(record.expiry);
        record.timer = record.expiry = null;
        // 取消只影响当前 GET 的等待，不推断任务执行、数据库事务或状态发生变化。
        if (reason === "timeout" || reason === "manual") record.controller?.abort();
        if (emit) this.emit();
    }

    setHidden(hidden) {
        this.hidden = hidden;
        for (const record of this.records.values()) {
            if (!record.auto) continue;
            if (this.now() >= record.deadline) { this.stop(record.id, "timeout", false); continue; }
            this.clearTimer(record.timer);
            record.timer = null;
            if (hidden) {
                record.mode = "hidden";
                record.controller?.abort();
            } else if (!record.pending) {
                this.schedule(record, true);
            }
        }
        this.emit();
    }

    forget(id) {
        this.stop(id, "manual", false);
        this.records.delete(id);
    }

    dispose() {
        for (const id of this.records.keys()) this.forget(id);
    }
}

export function pollingText(record) {
    if (!record) return "尚未查询";
    const messages = {
        polling: "自动刷新中：每次响应后至少间隔 2 秒，累计最长 60 秒。",
        hidden: "页面隐藏，自动刷新已暂停；60 秒总预算继续累计。",
        timeout: "自动刷新已暂停：60 秒总预算已结束，任务可能仍在后台运行。可手动刷新。",
        limit: "已有 3 个任务自动刷新，本任务仅手动查询。",
        error: "读取失败，自动刷新已暂停。可手动刷新；任务状态以服务器为准。",
        terminal: "服务器已返回终态，自动刷新已停止。",
        manual: "自动刷新已停止，可手动刷新。",
    };
    return messages[record.mode] ?? "仅手动查询。";
}
