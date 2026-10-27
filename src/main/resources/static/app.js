import { createApi, documentId, taskId, listQuery, TASK_LABELS, InputError, errorText, formatTime, LatestRead } from "./api.mjs";
import { TaskTracker, pollingText } from "./tasks.mjs";
import { SearchSession, formatScore } from "./search.mjs";
import { dialogLifecycle, taskBadgeClass, taskErrorFields } from "./presentation.mjs";
import { createVectorPanel } from "./vector-info.mjs";

const api = createApi();
const byId = (id) => document.getElementById(id);
const tabs = Array.from(document.querySelectorAll('[role="tab"]'));
const formValues = (form) => Object.fromEntries(new FormData(form));

function selectTab(selected, focus = false) {
    for (const tab of tabs) {
        const active = tab === selected;
        tab.setAttribute("aria-selected", String(active));
        tab.tabIndex = active ? 0 : -1;
        byId(tab.getAttribute("aria-controls")).hidden = !active;
    }
    if (focus) selected.focus();
}
for (const tab of tabs) {
    tab.addEventListener("click", () => selectTab(tab));
    tab.addEventListener("keydown", (event) => {
        const current = tabs.indexOf(tab);
        const positions = { ArrowRight: (current + 1) % tabs.length, ArrowLeft: (current + tabs.length - 1) % tabs.length,
            Home: 0, End: tabs.length - 1 };
        if (!Object.hasOwn(positions, event.key)) return;
        event.preventDefault();
        selectTab(tabs[positions[event.key]], true);
    });
}

/** 所有后端字段与用户输入只进入文本节点，原文不作为 HTML 或可执行链接渲染。 */
function element(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = String(text);
    if (className) node.className = className;
    return node;
}
function feedback(id, message, error = false) {
    const target = byId(id);
    target.textContent = message;
    target.classList.toggle("error", error);
}
function clearErrors(form) {
    for (const node of form.querySelectorAll("[data-error-for]")) node.textContent = "";
    for (const node of form.querySelectorAll('[aria-invalid="true"]')) node.removeAttribute("aria-invalid");
}
function fieldErrors(form, error) {
    for (const field of error.fieldErrors ?? []) {
        const output = Array.from(form.querySelectorAll("[data-error-for]")).find((node) => node.dataset.errorFor === field.field);
        if (output) output.textContent = field.message;
        const input = form.elements.namedItem(field.field);
        if (input) input.setAttribute("aria-invalid", "true");
    }
    form.querySelector('[aria-invalid="true"]')?.focus();
}
function fields(target, values) {
    target.replaceChildren();
    for (const [label, value] of values) {
        const group = element("div");
        group.append(element("dt", label), element("dd", value ?? "—"));
        target.append(group);
    }
}
/** ID 的缩略只发生在 CSS 中；查询、复制和 title 始终使用原始字符串。 */
function idView(id, { onOpen = () => void openDetail(id), copyFeedback = "copy-message" } = {}) {
    const group = element("div", undefined, "id-value");
    const view = element("button", id, "id-button");
    view.type = "button";
    view.title = id;
    view.setAttribute("aria-label", `查看文档 ${id}`);
    view.addEventListener("click", onOpen);
    const copy = element("button", "复制", "copy-button");
    copy.type = "button";
    copy.setAttribute("aria-label", `复制完整文档 ID ${id}`);
    copy.addEventListener("click", async () => {
        try {
            if (!navigator.clipboard?.writeText) throw new Error("clipboard unavailable");
            await navigator.clipboard.writeText(id);
            feedback(copyFeedback, "已复制完整文档 ID。");
        } catch {
            feedback(copyFeedback, `未能自动复制，可在详情中选择完整文档 ID：${id}`, true);
        }
    });
    group.append(view, copy);
    return group;
}
function badge(text, className = "badge") { return element("span", text, className); }
function taskLabel(status) { return `${TASK_LABELS[status]} · ${status}`; }
/** 每个状态字段分别决定颜色，不能由任务完成推断文档有效或向量就绪。 */
function statusFields(target, values) {
    target.replaceChildren();
    for (const [label, text, className] of values) {
        const group = element("div");
        const value = element("dd");
        value.append(badge(text, className));
        group.append(element("dt", label), value);
        target.append(group);
    }
}

const search = new SearchSession({ load: (query) => api.search(query), onChange: renderSearch });
function searchValues() {
    return { ...formValues(byId("search-form")), topK: byId("search-top-k").valueAsNumber };
}
function renderSearch(state) {
    // 正常成功只保留读屏完成通知，视觉摘要统一显示在结果区；异常及历史提示仍占据可见位置。
    const ordinarySuccess = state.result?.hits.some((hit) => hit.score !== 0) && !state.busy && !state.error;
    byId("search-message").classList.toggle("visually-hidden", Boolean(ordinarySuccess));
    byId("search-button").disabled = state.busy;
    byId("search-button").textContent = state.busy ? "检索中…" : "开始检索";
    if (state.error) {
        feedback("search-message", errorText(state.error, "search"), true);
        fieldErrors(byId("search-form"), state.error);
    } else if (state.busy) {
        feedback("search-message", "正在执行一次真实检索。完成前不会发送第二次搜索请求；当前若有结果仍是上次快照。");
    } else if (state.result) {
        feedback("search-message", state.result.hits.length === 0
            ? "本次未返回文档，可检查渠道或文档是否已完成且有效。"
            : state.result.hits.every((hit) => hit.score === 0)
                ? "本次分数均为 0，无法体现字面相关性；仍保留全部返回项并正常计数。"
                : "已取得检索结果；查看详情不会额外计数。");
    } else {
        feedback("search-message", "尚未执行成功检索。输入不会自动提交；余弦分数并非概率或置信度。");
    }
    byId("search-results").hidden = !state.result;
    if (!state.result) return;
    const { hits, query, duration } = state.result;
    const reasons = [];
    if (state.busy) reasons.push("新的检索正在执行，以下仍为上次查询结果");
    if (state.error) reasons.push("最近一次操作未取得有效结果，以下为上次成功查询的快照");
    if (state.draftChanged) reasons.push("表单条件已修改，以下结果仍对应此前条件，请手动检索");
    if (state.changedIds.length > 0) reasons.push("文档有效状态已变化，以下为历史查询结果；未自动重搜或本地重排");
    byId("search-history").textContent = reasons.length ? "历史结果 · " + reasons.join("。") + "。" : "";
    fields(byId("search-summary"), [["结果", `${hits.length} 条`], ["Top-K", String(query.topK)],
        ["渠道", query.channel ?? "全部渠道"], ["请求往返", `${duration.toFixed(1)} ms`]]);
    byId("search-query-options").textContent = `Top-K：${query.topK}；渠道：${query.channel ?? "全部渠道（未指定）"}；查询文本如下（空白原样保留）。`;
    byId("search-query-text").textContent = query.text;
    const rows = byId("search-rows");
    rows.replaceChildren();
    hits.forEach((hit, index) => {
        const row = element("tr");
        row.append(element("td", index + 1, "rank-cell"));
        const idCell = element("td", undefined, "id-cell");
        idCell.append(idView(hit.documentId));
        const channel = element("td", undefined, "channel-cell");
        channel.append(badge(hit.channel, "badge badge-blue"));
        row.append(idCell, channel, element("td", hit.snippet, "snippet"), element("td", formatScore(hit.score), "score-cell"));
        const actions = element("td", undefined, "actions-cell");
        const detail = element("button", "查看详情");
        detail.type = "button";
        detail.addEventListener("click", () => void openDetail(hit.documentId));
        actions.append(detail);
        if (state.changedIds.includes(hit.documentId)) actions.append(element("span", "已失效 · 历史结果", "hint"));
        row.append(actions);
        rows.append(row);
    });
    if (hits.length === 0) {
        const row = element("tr");
        const cell = element("td", "没有符合本次条件的文档。", "empty-cell");
        cell.colSpan = 6;
        row.append(cell);
        rows.append(row);
    }
}
byId("search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    if (search.busy) return;
    clearErrors(event.currentTarget);
    void search.submit(searchValues());
});
byId("search-form").addEventListener("input", () => {
    clearErrors(byId("search-form"));
    search.edit(searchValues());
    byId("empty-search").textContent = byId("search-text").value === ""
        ? "空查询将返回零分排序结果，不能区分相关性，仍会正常计数。" : "查询使用确定性的字面特征；原始文本与渠道不会因输入而自动提交。";
});
byId("search-button").disabled = false;

let selectedTask = null;
const tracker = new TaskTracker({
    load: (id, signal) => api.task(id, signal),
    hidden: document.hidden,
    onChange: renderTasks,
});
document.addEventListener("visibilitychange", () => tracker.setHidden(document.hidden));
window.addEventListener("pagehide", () => {
    for (const record of tracker.snapshots()) tracker.stop(record.id);
});

function showTask(id, { initialTask = null, auto = true } = {}) {
    submitModal.close();
    selectedTask = id.toLowerCase();
    byId("task-id").value = selectedTask;
    selectTab(byId("tab-tasks"));
    byId("task-id").focus();
    void tracker.query(selectedTask, { auto, initialTask });
}
function renderTasks(records) {
    const list = byId("session-tasks");
    list.replaceChildren();
    if (records.length === 0) list.append(element("li", "尚无本页会话任务。"));
    for (const record of records) {
        const item = element("li", undefined, record.id === selectedTask ? "selected" : "");
        const button = element("button", record.task?.documentId ?? record.id);
        button.type = "button";
        button.title = record.id;
        button.addEventListener("click", () => {
            selectedTask = record.id;
            byId("task-id").value = record.id;
            renderTasks(tracker.snapshots());
        });
        const state = element("div", undefined, "session-state");
        state.append(record.task ? badge(taskLabel(record.task.status), taskBadgeClass(record.task.status))
            : badge("尚未取得状态"));
        state.append(element("span", formatTime(record.task?.submittedAt), "session-note"));
        item.append(button, state, element("span", `${record.error ? "最近读取失败；" : ""}${pollingText(record)}`, "session-note"));
        list.append(item);
    }
    const record = tracker.get(selectedTask);
    byId("task-result").hidden = !record;
    if (!record) return;
    const task = record.task;
    byId("task-document-name").textContent = task?.documentId ?? "等待读取任务";
    byId("task-status").textContent = task ? taskLabel(task.status) : "尚未取得状态";
    byId("task-status").className = taskBadgeClass(task?.status);
    fields(byId("task-times"), [["提交时间（UTC+8）", formatTime(task?.submittedAt)],
        ["开始时间（UTC+8）", formatTime(task?.startedAt)], ["完成时间（UTC+8）", formatTime(task?.finishedAt)]]);
    fields(byId("task-fields"), [["任务 ID", record.id], ["关联文档", task?.documentId],
        ["渠道", task?.document?.channel], ["向量就绪", task?.document ? (task.document.vectorReady ? "是" : "否") : "尚未取得文档快照"],
        ["文档有效状态", task?.document ? (task.document.invalidated ? "已失效" : "有效") : "尚未取得文档快照"],
        ...taskErrorFields(task)]);
    feedback("task-message", record.error ? `状态暂不可获取。${errorText(record.error)}${task ? ` 上次已知状态：${task.status}。` : ""}`
        : record.busy ? "正在读取真实任务状态；不会改写上次已知业务状态。"
        : task ? "已展示服务器返回的状态。排队中不表示任务失败或已经完成。" : "尚未取得任务状态。", Boolean(record.error));
    byId("polling-message").textContent = pollingText(record);
    byId("task-refresh").disabled = record.busy;
    byId("task-stop").disabled = !record.auto;
    byId("task-detail").disabled = !task?.documentId;
}
byId("task-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    clearErrors(form);
    try { showTask(taskId(formValues(form).taskId)); }
    catch (error) { fieldErrors(form, error); feedback("task-message", errorText(error), true); }
});
byId("task-refresh").addEventListener("click", () => { if (selectedTask) void tracker.query(selectedTask, { auto: false }); });
byId("task-stop").addEventListener("click", () => tracker.stop(selectedTask));
byId("task-search").addEventListener("click", () => {
    selectTab(byId("tab-search"), true);
    byId("search-text").focus();
});
byId("task-detail").addEventListener("click", () => {
    const task = tracker.get(selectedTask)?.task;
    if (task) void openDetail(task.documentId);
});

let submittedId = null;
let submittedTask = null;
let submitting = false;
const submitDialog = byId("submit-dialog");
const submitModal = dialogLifecycle(submitDialog, { fallbackFocus: () => byId("open-submit") });
byId("open-submit").addEventListener("click", () => submitModal.open());
byId("close-submit").addEventListener("click", () => submitModal.close());
byId("cancel-submit").addEventListener("click", () => submitModal.close());
byId("submit-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting) return;
    const form = event.currentTarget;
    clearErrors(form);
    const values = formValues(form);
    const submitVisible = submitModal.capture();
    submittedId = null;
    submittedTask = null;
    byId("submit-actions").hidden = true;
    byId("submitted-task").hidden = true;
    try {
        // 先校验 ID，结果未知时保留本次快照用于查询，不能读取用户之后修改的表单值。
        submittedId = documentId(values.documentId);
        submitting = true;
        byId("submit-button").disabled = true;
        byId("submit-button").textContent = "提交中…";
        feedback("submit-message", "正在提交文档。此请求不会自动重试。");
        const accepted = await api.submit(values);
        submittedTask = accepted.taskId.toLowerCase();
        byId("submit-actions").hidden = false;
        byId("submitted-task").hidden = false;
        feedback("submit-message", `HTTP 202：已接收文档 ${accepted.documentId}，任务 ${accepted.taskId}。后台向量化尚待真实任务状态确认；完成且有效后才可参与检索。`);
        // 只使用 202 中真实返回的标识与状态，不补造时间、向量就绪或成功状态。
        void tracker.query(submittedTask, { initialTask: accepted });
    } catch (error) {
        feedback("submit-message", errorText(error, "submit"), true);
        // 已关闭的弹窗仍保留真实结果，但字段错误不得抢走当前页面焦点。
        if (submitVisible()) fieldErrors(form, error);
        byId("submit-actions").hidden = !submittedId || error instanceof InputError;
    } finally {
        submitting = false;
        byId("submit-button").disabled = false;
        byId("submit-button").textContent = "提交文档";
    }
});
byId("submitted-detail").addEventListener("click", () => { if (submittedId) { submitModal.close(); void openDetail(submittedId); } });
byId("submitted-task").addEventListener("click", () => { if (submittedTask) showTask(submittedTask); });

const listRead = new LatestRead();
let listData = null;
let listParameters = null;
let listBusy = false;
function currentFilters(page = 0) {
    const values = formValues(byId("filter-form"));
    return { page, size: Number(values.size), channel: values.channel, invalidated: values.invalidated };
}
function filtersChanged() {
    try {
        return listParameters && listQuery({ ...currentFilters(), page: 0 }) !== listQuery({ ...listParameters, page: 0 });
    } catch { return true; }
}
function pagination() {
    const changed = filtersChanged();
    byId("previous-page").disabled = listBusy || changed || !listData || listData.page === 0;
    byId("next-page").disabled = listBusy || changed || !listData || (listData.page + 1) * listData.size >= listData.totalElements;
    byId("list-refresh").disabled = listBusy;
}
function renderList() {
    const body = byId("document-rows");
    body.replaceChildren();
    if (!listData) return;
    for (const doc of listData.items) {
        const row = element("tr");
        const idCell = element("td");
        idCell.className = "id-cell";
        idCell.append(idView(doc.documentId));
        row.append(idCell);
        const channel = element("td");
        channel.append(badge(doc.channel, "badge badge-blue"));
        const status = element("td");
        status.append(badge(TASK_LABELS[doc.taskStatus], taskBadgeClass(doc.taskStatus)));
        status.title = doc.taskStatus;
        const validity = element("td");
        validity.append(badge(doc.invalidated ? "已失效" : "有效", doc.invalidated ? "badge" : "badge badge-success"));
        row.append(channel, status, element("td", doc.vectorReady ? "是" : "否"), validity,
            element("td", doc.hitCount, "mono"), element("td", formatTime(doc.submittedAt)));
        const actions = element("td", undefined, "actions-cell");
        const detail = element("button", "详情");
        detail.type = "button";
        detail.addEventListener("click", () => void openDetail(doc.documentId));
        actions.append(detail);
        row.append(actions);
        body.append(row);
    }
    if (listData.items.length === 0) {
        const cell = element("td", "当前页没有文档。可以更改筛选或返回上一页。");
        cell.colSpan = 8;
        const row = element("tr");
        row.append(cell);
        body.append(row);
    }
    byId("page-message").textContent = `第 ${listData.page + 1} 页 · 每页 ${listData.size} 条 · 筛选结果共 ${listData.totalElements} 条`;
    byId("list-results").hidden = false;
    pagination();
}
async function loadList(parameters) {
    clearErrors(byId("filter-form"));
    try { listQuery(parameters); }
    catch (error) { fieldErrors(byId("filter-form"), error); feedback("list-message", errorText(error), true); return; }
    const read = listRead.begin();
    listBusy = true;
    pagination();
    feedback("list-message", "正在读取列表；此前数据仍是上次读取的快照。");
    try {
        const result = await api.list(parameters, read.signal);
        if (!read.current()) return;
        listData = result;
        listParameters = parameters;
        renderList();
        feedback("list-message", filtersChanged() ? "以下是上次请求条件的快照；表单筛选已修改，请点击应用筛选。"
            : "已读取真实文档列表。列表和详情读取不增加命中次数。");
    } catch (error) {
        if (read.current()) { feedback("list-message", `列表读取失败，以下若有数据仍是上次快照。${errorText(error)}`, true); fieldErrors(byId("filter-form"), error); }
    } finally {
        if (read.current()) { listBusy = false; pagination(); }
    }
}
byId("filter-form").addEventListener("submit", (event) => { event.preventDefault(); void loadList(currentFilters()); });
byId("filter-form").addEventListener("input", () => {
    if (listData && filtersChanged()) feedback("list-message", "筛选已修改。以下仍是上次查询结果，请点击应用筛选后再翻页。");
    pagination();
});
byId("previous-page").addEventListener("click", () => { if (listData && !filtersChanged()) void loadList({ ...listParameters, page: listData.page - 1 }); });
byId("next-page").addEventListener("click", () => { if (listData && !filtersChanged()) void loadList({ ...listParameters, page: listData.page + 1 }); });

const vectorPanel = createVectorPanel({
    content: byId("vector-content"), message: byId("vector-message"), version: byId("vector-version"),
    dimensions: byId("vector-dimensions"), bytes: byId("vector-bytes"), zero: byId("vector-zero"),
    fingerprint: byId("vector-fingerprint"), values: byId("vector-values"), heading: byId("vector-values-heading"),
    toggle: byId("toggle-vector"), copyFingerprint: byId("copy-vector-fingerprint"), copyVector: byId("copy-vector"),
}, { notify: (message, error) => feedback("detail-message", message, error) });
const detailRead = new LatestRead();
const dialog = byId("detail-dialog");
let detailId = null;
let detailData = null;
let invalidating = false;
let detailLoading = false;
const invalidationDialog = byId("invalidation-dialog");
let confirmationId = null;
const invalidationModal = dialogLifecycle(invalidationDialog, { onDismiss: () => { confirmationId = null; } });
const detailModal = dialogLifecycle(dialog, { fallbackFocus: () => tabs.find((tab) => tab.getAttribute("aria-selected") === "true"), onDismiss: () => {
    invalidationModal.close();
    detailRead.cancel();
    detailId = null;
    detailData = null;
    detailLoading = false;
    renderDetail();
} });
function detailButtons() {
    byId("detail-refresh").disabled = !detailId || detailLoading || invalidating;
    byId("detail-task").disabled = !detailData || invalidating;
    byId("invalidate-document").disabled = !detailData || detailData.invalidated || detailLoading || invalidating;
}
function renderDetail() {
    byId("detail-content").hidden = !detailData;
    if (!detailData) {
        for (const id of ["detail-document-id", "detail-status", "detail-stats", "detail-fields", "detail-text"]) byId(id).replaceChildren();
        vectorPanel.clear();
    } else {
        vectorPanel.set(detailData);
        byId("detail-document-id").replaceChildren(element("span", "文档 ID："),
            idView(detailData.documentId, { onOpen: () => byId("detail-text").focus(), copyFeedback: "detail-message" }));
        statusFields(byId("detail-status"), [["任务", taskLabel(detailData.taskStatus), taskBadgeClass(detailData.taskStatus)],
            ["向量", detailData.vectorReady ? "已就绪" : "未就绪", detailData.vectorReady ? "badge badge-success" : "badge"],
            ["文档", detailData.invalidated ? "已失效" : "有效", detailData.invalidated ? "badge" : "badge badge-success"],
            ["渠道", detailData.channel, "badge badge-blue"]]);
        fields(byId("detail-stats"), [["命中次数（读取快照）", detailData.hitCount],
            ["提交时间（UTC+8）", formatTime(detailData.submittedAt)], ["完成时间（UTC+8）", formatTime(detailData.vectorCompletedAt)],
            ["失效时间（UTC+8）", formatTime(detailData.invalidatedAt)]]);
        fields(byId("detail-fields"), [["任务 ID", detailData.taskId]]);
        byId("detail-text").textContent = detailData.text;
        byId("invalidate-document").textContent = detailData.invalidated ? "已失效" : "标记失效";
    }
    detailButtons();
}
async function openDetail(id) {
    try { documentId(id); } catch (error) { fieldErrors(byId("detail-form"), error); return; }
    if (invalidating) return;
    const read = detailRead.begin();
    detailId = id;
    detailData = null;
    detailLoading = true;
    renderDetail();
    detailModal.open();
    feedback("detail-message", `正在读取文档 ${id} 的真实详情。`);
    try {
        const doc = await api.detail(id, read.signal);
        if (!read.current()) return;
        detailData = doc;
        if (doc.invalidated) search.markInvalidated(doc.documentId);
        renderDetail();
        feedback("detail-message", "已读取服务器快照；任务状态与有效状态相互独立。时间为北京时间（UTC+8）。");
    } catch (error) {
        if (read.current()) feedback("detail-message", errorText(error), true);
    } finally {
        if (read.current()) { detailLoading = false; detailButtons(); }
    }
}
byId("detail-form").addEventListener("submit", (event) => {
    event.preventDefault();
    clearErrors(event.currentTarget);
    void openDetail(formValues(event.currentTarget).documentId);
});
byId("close-detail").addEventListener("click", () => detailModal.close());
byId("detail-refresh").addEventListener("click", () => { if (detailId) void openDetail(detailId); });
byId("detail-task").addEventListener("click", () => {
    if (!detailData) return;
    const id = detailData.taskId;
    detailModal.close();
    showTask(id);
});
byId("invalidate-document").addEventListener("click", () => {
    if (!detailData || detailData.invalidated || invalidating) return;
    confirmationId = detailData.documentId;
    byId("invalidation-id").textContent = confirmationId;
    invalidationModal.open();
});
byId("cancel-invalidation").addEventListener("click", () => invalidationModal.close());
byId("confirm-invalidation").addEventListener("click", async () => {
    const id = confirmationId;
    if (!id || !dialog.open || detailData?.documentId !== id || detailData.invalidated || invalidating) return;
    invalidationModal.close();
    detailRead.cancel();
    invalidating = true;
    detailButtons();
    feedback("detail-message", "正在请求失效；成功响应前保留原有效状态。此请求不自动重试。");
    try {
        const result = await api.invalidate(id);
        // 即使检索仍在途，也记录真实失效：迟到的候选快照不得冒充最新有效状态。
        search.markInvalidated(id);
        if (dialog.open && detailData?.documentId === id) {
            detailData = { ...detailData, ...result };
            renderDetail();
            feedback("detail-message", "服务器已确认失效。任务状态独立保留；没有自动发起检索。其他字段仍是原读取快照，可手动刷新。");
        }
        // 旧列表保持原快照，避免失效筛选中的条目被本地删改伪装成新查询。
        if (listData) feedback("list-message", "文档状态已变化。以下为失效前的历史列表快照，请手动刷新确认筛选结果。");
    } catch (error) {
        if (dialog.open && detailData?.documentId === id) feedback("detail-message", `尚未确认失效，保留上次已知状态。${errorText(error, "invalidate")}`, true);
    } finally {
        invalidating = false;
        detailButtons();
    }
});
