/** 前端只做输入提示和响应边界校验；服务端仍是参数与业务状态的最终依据。 */
export class InputError extends Error {
    constructor(field, message) {
        super(message);
        this.fieldErrors = [{ field, message }];
    }
}

export class ApiError extends Error {
    constructor(kind, message, { status = 0, code = "", fieldErrors = [] } = {}) {
        super(message);
        this.kind = kind;
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }
}

export const TASK_LABELS = Object.freeze({
    QUEUED: "排队中", RUNNING: "处理中", SUCCEEDED: "完成", FAILED: "失败",
});
export const isTerminal = (status) => status === "SUCCEEDED" || status === "FAILED";
// 与 Java Character.isWhitespace || isSpaceChar 一致；U+0085 属于有效渠道字符。
const whitespace = "[\\u0009-\\u000d\\u001c-\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]";
const blankChannel = new RegExp(`^${whitespace}*$`, "u");
const trimChannel = new RegExp(`^${whitespace}+|${whitespace}+$`, "gu");

export function documentId(value) {
    if (typeof value !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/.test(value)) {
        throw new InputError("documentId", "文档 ID 为 1—128 位字母、数字、点、下划线或连字符，首位为字母或数字；不会自动去除空白。");
    }
    return value;
}

export function taskId(value) {
    if (typeof value !== "string" || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
        throw new InputError("taskId", "请输入完整 UUID 格式的 taskId。");
    }
    return value;
}

function text(value) {
    if (typeof value !== "string") throw new InputError("text", "原文必须是字符串，允许空字符串。");
    let length = 0;
    for (const point of value) {
        const code = point.codePointAt(0);
        if (code >= 0xd800 && code <= 0xdfff) throw new InputError("text", "原文包含不完整的 Unicode 字符。");
        length += 1;
    }
    if (length > 20000) throw new InputError("text", "原文不能超过 20000 个 Unicode 字符；规范化后的长度由服务器继续校验。");
    return value;
}

/** 表单中的空渠道表示未填写，必须省略字段；绝不向接口发送显式空白渠道。 */
export function optionalChannel(value = "") {
    if (typeof value !== "string") throw new InputError("channel", "渠道必须是文本。");
    if (blankChannel.test(value)) return undefined;
    const channel = value.replace(trimChannel, "");
    if (Array.from(channel).length > 64) throw new InputError("channel", "渠道不能超过 64 个 Unicode 字符。");
    return channel;
}

export function submission(values) {
    const body = { documentId: documentId(values.documentId), text: text(values.text) };
    const channel = optionalChannel(values.channel);
    if (channel !== undefined) body.channel = channel;
    return body;
}

export function listQuery({ page = 0, size = 20, channel = "", invalidated = "" } = {}) {
    if (!Number.isSafeInteger(page) || page < 0 || page > 2147483647) throw new InputError("page", "页码必须为 0—2147483647 的整数。");
    if (!Number.isSafeInteger(size) || size < 1 || size > 100) throw new InputError("size", "每页数量为 1—100 的整数。");
    if (!["", "true", "false"].includes(invalidated)) throw new InputError("invalidated", "有效状态筛选无效。");
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    const value = optionalChannel(channel);
    if (value !== undefined) query.set("channel", value);
    if (invalidated !== "") query.set("invalidated", invalidated);
    return query.toString();
}

const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
const string = (value) => typeof value === "string";
const time = (value) => value === null || (string(value) && Number.isFinite(Date.parse(value)));
const status = (value) => Object.hasOwn(TASK_LABELS, value);
const nullableString = (value) => value === null || string(value);
export function validDocument(value, detail = false) {
    return object(value) && string(value.documentId) && string(value.channel) && string(value.taskId) &&
        status(value.taskStatus) && typeof value.vectorReady === "boolean" && typeof value.invalidated === "boolean" &&
        string(value.hitCount) && /^\d+$/.test(value.hitCount) && string(value.submittedAt) && time(value.submittedAt) &&
        time(value.vectorCompletedAt) && time(value.invalidatedAt) && (!detail || string(value.text));
}
const validTask = (value) => object(value) && string(value.taskId) && string(value.documentId) && status(value.status) &&
    string(value.submittedAt) && time(value.submittedAt) && time(value.startedAt) && time(value.finishedAt) &&
    nullableString(value.errorCode) && nullableString(value.errorMessage) &&
    (value.status === "SUCCEEDED" ? validDocument(value.document, true) : value.document === null);
const validPage = (value) => object(value) && Array.isArray(value.items) && value.items.every((item) => validDocument(item)) &&
    Number.isSafeInteger(value.page) && value.page >= 0 && Number.isSafeInteger(value.size) && value.size >= 1 && value.size <= 100 &&
    Number.isSafeInteger(value.totalElements) && value.totalElements >= 0 && value.items.length <= value.size;

/** 不自动重试任何请求。超时/取消只停止浏览器等待，不能证明服务器没有接收或回滚。 */
export function createApi(fetchImpl = globalThis.fetch, { timeoutMs = 10000 } = {}) {
    async function request(path, { method = "GET", body, signal, expected = 200, validate = object } = {}) {
        const controller = new AbortController();
        const abort = () => controller.abort();
        if (signal?.aborted) abort();
        signal?.addEventListener("abort", abort, { once: true });
        let timedOut = false;
        const timeout = setTimeout(() => { timedOut = true; abort(); }, timeoutMs);
        try {
            const headers = { Accept: "application/json" };
            if (body !== undefined) headers["Content-Type"] = "application/json";
            const response = await fetchImpl(path, {
                method, headers, credentials: "same-origin", cache: "no-store", signal: controller.signal,
                ...(body === undefined ? {} : { body: JSON.stringify(body) }),
            });
            let payload;
            try { payload = JSON.parse(await response.text()); } catch { payload = null; }
            if (!response.ok) {
                const fields = Array.isArray(payload?.fieldErrors)
                    ? payload.fieldErrors.filter((item) => object(item) && string(item.field) && string(item.message)) : [];
                throw new ApiError("http", string(payload?.message) ? payload.message : "服务器未返回可识别的 JSON 错误。", {
                    status: response.status, code: string(payload?.code) ? payload.code : "", fieldErrors: fields,
                });
            }
            if (response.status !== expected || !validate(payload)) throw new ApiError("invalid", "成功响应格式与冻结契约不符，无法确认操作结果。");
            return payload;
        } catch (error) {
            if (error instanceof ApiError) throw error;
            throw new ApiError(timedOut ? "timeout" : controller.signal.aborted ? "aborted" : "network", "无法读取有效响应。");
        } finally {
            clearTimeout(timeout);
            signal?.removeEventListener("abort", abort);
        }
    }
    return {
        health: (signal) => request("/health", { signal, validate: (value) => object(value) && string(value.status) &&
            string(value.stage) && typeof value.businessReady === "boolean" }),
        submit: (values) => {
            const body = submission(values);
            return request("/api/documents", { method: "POST", body, expected: 202,
                validate: (value) => object(value) && value.documentId === body.documentId && value.status === "QUEUED" &&
                    string(value.taskId) && (() => { try { taskId(value.taskId); return true; } catch { return false; } })() });
        },
        list: (values, signal) => request(`/api/documents?${listQuery(values)}`, { signal, validate: validPage }),
        detail: (id, signal) => request(`/api/documents/${encodeURIComponent(documentId(id))}`, {
            signal, validate: (value) => validDocument(value, true) && value.documentId === id,
        }),
        invalidate: (id) => request(`/api/documents/${encodeURIComponent(documentId(id))}/invalidation`, {
            method: "PUT", validate: (value) => object(value) && value.documentId === id && value.invalidated === true &&
                string(value.invalidatedAt) && time(value.invalidatedAt),
        }),
        task: (id, signal) => request(`/api/tasks/${encodeURIComponent(taskId(id))}`, {
            signal, validate: (value) => validTask(value) && value.taskId.toLowerCase() === id.toLowerCase(),
        }),
    };
}

export function errorText(error, operation = "read") {
    if (error instanceof InputError) return error.message;
    if (error.kind !== "http") {
        const prefix = error.kind === "timeout" ? "请求超时。" : error.kind === "invalid" ? "响应格式与契约不符。" : "网络或响应读取失败。";
        return operation === "submit" ? `${prefix}无法确认文档是否已被接收；请按刚才的文档 ID 查询详情，不要盲目重新提交。`
            : operation === "invalidate" ? `${prefix}无法确认是否已失效；保留上次状态，请手动刷新详情确认。`
            : `${prefix}状态暂不可获取；保留上次已知状态。可手动刷新。`;
    }
    const prefix = `HTTP ${error.status}${error.code ? ` · ${error.code}` : ""}：`;
    if (error.code === "RESULT_UNKNOWN") return prefix + (operation === "submit"
        ? "接收结果未知；请按文档 ID 查询，不要自动重提。" : "操作结果未知，请手动查询确认；不会自动重试写入。");
    if (error.status === 409) return prefix + "文档 ID 已存在，不覆盖已有文档；请查询详情或使用新 ID。";
    if (error.status === 429) return prefix + "服务接收容量已满或请求繁忙，请稍后手动操作；未将文档加入成功记录。";
    if (error.status === 401 || error.status === 403) return prefix + "访问受限，请检查入口授权或同源访问方式。";
    if (error.status === 404) return prefix + "未找到该资源；若是业务接口，可能尚未合入当前后端版本。";
    if (error.status === 413) return prefix + "请求体过大，请缩短原文。";
    if (error.status >= 500) return prefix + "服务暂不可用或尚未就绪，请稍后手动查询；不会自动重试写入。";
    return prefix + error.message;
}

export function formatTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return "—（时间格式无效）";
    return new Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit",
        hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" }).format(date);
}

/** 读请求采用代次和取消双重保护，旧响应即便已无法取消也不会覆盖新选择。 */
export class LatestRead {
    begin() {
        this.cancel();
        this.controller = new AbortController();
        const generation = this.generation;
        return { signal: this.controller.signal, current: () => this.generation === generation };
    }
    cancel() {
        this.generation = (this.generation ?? 0) + 1;
        this.controller?.abort();
    }
}
