import { searchInput, InputError } from "./api.mjs";

/**
 * 搜索会产生计数副作用，因此只允许用户明确触发一个在途请求，不取消后自动重放。
 * 响应结果始终对应发出时的参数快照；编辑表单、失效或刷新详情都不能触发搜索。
 */
export class SearchSession {
    constructor({ load, onChange = () => {}, now = () => performance.now() }) {
        this.load = load;
        this.onChange = onChange;
        this.now = now;
        this.busy = false;
        this.result = null;
        this.error = null;
        this.draft = null;
        this.invalidated = new Set();
    }

    snapshot() {
        let current = null;
        try { current = searchInput(this.draft ?? {}); } catch { /* 输入尚不合法时，旧结果仍只属于旧条件。 */ }
        return { busy: this.busy, result: this.result, error: this.error,
            draftChanged: Boolean(this.result && JSON.stringify(current) !== JSON.stringify(this.result.query)),
            changedIds: this.result?.hits.filter((hit) => this.invalidated.has(hit.documentId)).map((hit) => hit.documentId) ?? [] };
    }

    emit() { this.onChange(this.snapshot()); }

    edit(values) {
        this.draft = { ...values };
        if (this.error instanceof InputError || (this.error?.kind === "http" && this.error.status === 400)) this.error = null;
        this.emit();
    }

    markInvalidated(id) {
        this.invalidated.add(id);
        this.emit();
    }

    async submit(values) {
        if (this.busy) return false;
        this.draft = { ...values };
        let query;
        try { query = searchInput(values); }
        catch (error) { this.error = error; this.emit(); return false; }
        this.busy = true;
        this.error = null;
        const started = this.now();
        this.emit();
        try {
            const response = await this.load(query);
            // 不按四位小数重排，不过滤零负分，也不在本地递增命中次数。
            this.result = { query, hits: response.hits, duration: Math.max(0, this.now() - started) };
            return true;
        } catch (error) {
            this.error = error;
            return false;
        } finally {
            this.busy = false;
            this.emit();
        }
    }
}

export function formatScore(score) { return score.toFixed(4); }
