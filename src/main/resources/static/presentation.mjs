/**
 * 封装原生弹窗的展示生命周期。主动关闭先同步撤销读取权限，避免 close 事件排队期间
 * 迟到 GET 更新已关闭内容；旧 close 事件也不能撤销紧接着重新打开的新会话。
 * 这里只管理展示与焦点，不取消或重试任何写请求。
 */
export function dialogLifecycle(dialog, { onDismiss = () => {}, activeElement = () => document.activeElement, fallbackFocus = () => null } = {}) {
    let opener = null;
    let active = false;
    let revision = 0;
    function dismiss() {
        if (!active) return;
        active = false;
        revision += 1;
        onDismiss();
        const target = opener?.isConnected ? opener : fallbackFocus();
        if (target?.isConnected) target.focus();
    }
    dialog.addEventListener("cancel", (event) => {
        event.preventDefault();
        close();
    });
    dialog.addEventListener("close", () => {
        if (!dialog.open) dismiss();
    });
    function close() {
        if (dialog.open) dialog.close();
        dismiss();
    }
    return {
        open() {
            if (dialog.open) return;
            opener = activeElement();
            active = true;
            revision += 1;
            dialog.showModal();
        },
        close,
        // 写操作可以完成后保留结果，但只有发起时的弹窗会话仍有效时才允许移动字段焦点。
        capture() {
            const started = revision;
            return () => active && dialog.open && started === revision;
        },
    };
}

/** 状态徽章仅改变视觉语义，不把向量就绪或文档失效推断成任务终态。 */
export function taskBadgeClass(status) {
    if (status === "SUCCEEDED") return "badge badge-success";
    if (status === "FAILED") return "badge badge-failed";
    if (status === "QUEUED" || status === "RUNNING") return "badge badge-pending";
    return "badge";
}

/** 业务错误只在服务端确实返回内容时展示；任务四态始终由独立状态区保留。 */
export function taskErrorFields(task) {
    return [["业务错误码", task?.errorCode], ["业务错误信息", task?.errorMessage]]
        .filter(([, value]) => typeof value === "string" && value.length > 0);
}
