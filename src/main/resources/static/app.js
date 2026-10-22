import { createApi, documentId, taskId, listQuery, TASK_LABELS, InputError, errorText, formatTime, LatestRead } from "./api.mjs";
import { TaskTracker, pollingText } from "./tasks.mjs";
import { SearchSession, formatScore } from "./search.mjs";

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
function documentFields(doc) {
    return [["文档 ID", doc.documentId], ["渠道", doc.channel], ["taskId", doc.taskId],
        ["任务状态", `${TASK_LABELS[doc.taskStatus]} · ${doc.taskStatus}`], ["向量就绪", doc.vectorReady ? "是" : "否"],
        ["有效状态", doc.invalidated ? "已失效" : "有效"], ["命中次数（快照）", doc.hitCount],
        ["提交时间（UTC+8）", formatTime(doc.submittedAt)], ["向量完成时间（UTC+8）", formatTime(doc.vectorCompletedAt)],
        ["失效时间（UTC+8）", formatTime(doc.invalidatedAt)]];
}

const search = new SearchSession({ load: (query) => api.search(query), onChange: renderSearch });
function searchValues() {
    return { ...formValues(byId("search-form")), topK: byId("search-top-k").valueAsNumber };
}
function renderSearch(state) {
    byId("search-button").disabled = state.busy;
    byId("search-button").textContent = state.busy ? "检索中…" : "检索";
    if (state.error) {
        feedback("search-message", errorText(state.error, "search"), true);
        fieldErrors(byId("search-form"), state.error);
    } else if (state.busy) {
        feedback("search-message", "正在执行一次真实检索。完成前不会发送第二次搜索请求；当前若有结果仍是上次快照。");
    } else if (state.result) {
        feedback("search-message", state.result.hits.length === 0
            ? "检索成功，当前条件没有命中。没有可展示的文档，不会补造结果。"
            : "已收到真实检索结果。详情和列表可手动刷新计数快照，不会因读取额外计数。");
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
    byId("search-history").textContent = reasons.length ? reasons.join("。") + "。" : "本次查询快照；详情有效状态可能随之后的操作变化。";
    fields(byId("search-summary"), [["实际结果数量", String(hits.length)], ["请求往返耗时", `${duration.toFixed(1)} ms`]]);
    byId("search-query-options").textContent = `Top-K：${query.topK}；渠道：${query.channel ?? "全部渠道（未指定）"}；查询文本如下（空白原样保留）。`;
    byId("search-query-text").textContent = query.text;
    const rows = byId("search-rows");
    rows.replaceChildren();
    hits.forEach((hit, index) => {
        const row = element("tr");
        row.append(element("td", index + 1), element("td", hit.documentId), element("td", formatScore(hit.score)), element("td", hit.channel));
        row.append(element("td", hit.snippet, "snippet"));
        const actions = element("td");
        const detail = element("button", "详情 / 计数快照");
        detail.type = "button";
        detail.addEventListener("click", () => void openDetail(hit.documentId));
        actions.append(detail);
        if (state.changedIds.includes(hit.documentId)) actions.append(element("span", "已失效 · 历史结果", "hint"));
        row.append(actions);
        rows.append(row);
    });
    if (hits.length === 0) {
        const row = element("tr");
        const cell = element("td", "没有符合本次条件的文档。");
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
    selectedTask = id.toLowerCase();
    byId("task-id").value = selectedTask;
    selectTab(byId("tab-tasks"));
    void tracker.query(selectedTask, { auto, initialTask });
}
function renderTasks(records) {
    const list = byId("session-tasks");
    list.replaceChildren();
    if (records.length === 0) list.append(element("li", "尚无本页会话任务。"));
    for (const record of records) {
        const item = element("li");
        const button = element("button", record.id);
        button.type = "button";
        button.addEventListener("click", () => {
            selectedTask = record.id;
            byId("task-id").value = record.id;
            renderTasks(tracker.snapshots());
        });
        const label = record.task ? `${TASK_LABELS[record.task.status]} · ${record.task.status}` : "尚未取得任务状态";
        item.append(button, element("span", `${label}；${record.error ? "最近读取失败；" : ""}${pollingText(record)}`));
        list.append(item);
    }
    const record = tracker.get(selectedTask);
    byId("task-result").hidden = !record;
    if (!record) return;
    const task = record.task;
    fields(byId("task-fields"), [["taskId", record.id], ["关联文档", task?.documentId],
        ["上次已知状态", task ? `${TASK_LABELS[task.status]} · ${task.status}` : "尚未取得"],
        ["提交时间（UTC+8）", formatTime(task?.submittedAt)], ["开始时间（UTC+8）", formatTime(task?.startedAt)],
        ["结束时间（UTC+8）", formatTime(task?.finishedAt)], ["业务错误码", task?.errorCode], ["业务错误信息", task?.errorMessage]]);
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
byId("submit-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting) return;
    const form = event.currentTarget;
    clearErrors(form);
    const values = formValues(form);
    submittedId = null;
    submittedTask = null;
    byId("submit-actions").hidden = true;
    byId("submitted-task").hidden = true;
    try {
        // 先校验 ID，结果未知时保留本次快照用于查询，不能读取用户之后修改的表单值。
        submittedId = documentId(values.documentId);
        submitting = true;
        byId("submit-button").disabled = true;
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
        fieldErrors(form, error);
        byId("submit-actions").hidden = !submittedId || error instanceof InputError;
    } finally {
        submitting = false;
        byId("submit-button").disabled = false;
    }
});
byId("submitted-detail").addEventListener("click", () => { if (submittedId) void openDetail(submittedId); });
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
        const idButton = element("button", doc.documentId, "id-button");
        idButton.type = "button";
        idButton.title = doc.documentId;
        idButton.addEventListener("click", () => void openDetail(doc.documentId));
        idCell.append(idButton);
        row.append(idCell);
        for (const value of [doc.channel, `${TASK_LABELS[doc.taskStatus]} · ${doc.taskStatus}`, doc.vectorReady ? "是" : "否",
            doc.invalidated ? "已失效" : "有效", formatTime(doc.submittedAt), formatTime(doc.vectorCompletedAt), doc.hitCount]) row.append(element("td", value));
        const actions = element("td");
        const detail = element("button", "详情 / 失效");
        detail.type = "button";
        detail.addEventListener("click", () => void openDetail(doc.documentId));
        actions.append(detail);
        row.append(actions);
        body.append(row);
    }
    if (listData.items.length === 0) {
        const cell = element("td", "当前页没有文档。可以更改筛选或返回上一页。");
        cell.colSpan = 9;
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

const detailRead = new LatestRead();
const dialog = byId("detail-dialog");
let detailId = null;
let detailData = null;
let invalidating = false;
let detailLoading = false;
function detailButtons() {
    byId("detail-refresh").disabled = !detailId || detailLoading || invalidating;
    byId("detail-task").disabled = !detailData || invalidating;
    byId("invalidate-document").disabled = !detailData || detailData.invalidated || detailLoading || invalidating;
}
function renderDetail() {
    byId("detail-content").hidden = !detailData;
    if (detailData) {
        fields(byId("detail-fields"), documentFields(detailData));
        byId("detail-text").textContent = detailData.text;
        byId("invalidate-document").textContent = detailData.invalidated ? "已失效" : "失效文档";
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
    if (!dialog.open) dialog.showModal();
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
byId("close-detail").addEventListener("click", () => dialog.close());
dialog.addEventListener("close", () => {
    detailRead.cancel();
    detailLoading = false;
    // 正在进行的失效 PUT 不随抽屉关闭取消；关闭浏览器等待也无法证明后台写入回滚。
});
byId("detail-refresh").addEventListener("click", () => { if (detailId) void openDetail(detailId); });
byId("detail-task").addEventListener("click", () => {
    if (!detailData) return;
    const id = detailData.taskId;
    dialog.close();
    showTask(id);
});
byId("invalidate-document").addEventListener("click", async () => {
    if (!detailData || detailData.invalidated || invalidating) return;
    if (!window.confirm("失效后该文档不会参与新的检索，本演示不提供恢复有效操作。是否继续？")) return;
    const id = detailData.documentId;
    detailRead.cancel();
    invalidating = true;
    detailButtons();
    feedback("detail-message", "正在请求失效；成功响应前保留原有效状态。此请求不自动重试。");
    try {
        const result = await api.invalidate(id);
        // 即使检索仍在途，也记录真实失效：迟到的候选快照不得冒充最新有效状态。
        search.markInvalidated(id);
        if (detailData?.documentId === id) {
            detailData = { ...detailData, ...result };
            renderDetail();
            feedback("detail-message", "服务器已确认失效。任务状态独立保留；没有自动发起检索。其他字段仍是原读取快照，可手动刷新。");
        }
        // 旧列表保持原快照，避免失效筛选中的条目被本地删改伪装成新查询。
        if (listData) feedback("list-message", "文档状态已变化。以下为失效前的历史列表快照，请手动刷新确认筛选结果。");
    } catch (error) {
        feedback("detail-message", `尚未确认失效，保留上次已知状态。${errorText(error, "invalidate")}`, true);
    } finally {
        invalidating = false;
        detailButtons();
    }
});

byId("check-health").addEventListener("click", async () => {
    const button = byId("check-health");
    if (button.disabled) return;
    button.disabled = true;
    byId("health-result").hidden = true;
    feedback("health-message", "正在读取基础健康接口…");
    try {
        const health = await api.health();
        const values = [["status", health.status], ["stage", health.stage], ["businessReady", String(health.businessReady)]];
        if (typeof health.taskState === "string") values.push(["taskState", health.taskState]);
        if (typeof health.taskReady === "boolean") values.push(["taskReady", String(health.taskReady)]);
        fields(byId("health-result"), values);
        byId("health-result").hidden = false;
        feedback("health-message", health.businessReady === false
            ? "已收到真实基础健康响应；businessReady=false，完整业务尚未就绪。"
            : "已展示真实健康响应；这不替代完整业务、浏览器与部署验收，也不会自动发起检索。");
    } catch (error) { feedback("health-message", errorText(error), true); }
    finally { button.disabled = false; }
});
