/** 向量只来自详情响应；这里校验结构和一致性，不重新计算向量或指纹。 */
export function validVectorInfo(info, document) {
    if (info === null) return true;
    return info !== null && typeof info === "object" && !Array.isArray(info) &&
        typeof info.embeddingVersion === "string" && info.embeddingVersion.trim().length > 0 &&
        info.dimensions === 256 && info.byteLength === 1024 && typeof info.zeroVector === "boolean" &&
        typeof info.sha256 === "string" && /^[a-f0-9]{64}$/.test(info.sha256) &&
        Array.isArray(info.values) && info.values.length === info.dimensions &&
        info.values.every((value) => typeof value === "number" && Number.isFinite(value) && Number.isFinite(Math.fround(value))) &&
        info.zeroVector === info.values.every((value) => value === 0) &&
        document.vectorReady === true && document.taskStatus === "SUCCEEDED";
}

/** Java Float 的 JSON 短小数先还原 float32；JS 最短往返表示保留其全部有效精度和负零。 */
export function serializeVector(values) {
    return "[" + values.map((value) => {
        const actual = Math.fround(value);
        return Object.is(actual, -0) ? "-0" : String(actual);
    }).join(", ") + "]";
}

/** 六位小数仅用于预览，绝不把预览文字拿来复制完整向量。 */
export function previewVector(values, expanded = false) {
    return values.slice(0, expanded ? values.length : 8).map((value, index) => {
        const actual = Math.fround(value);
        const shown = Object.is(actual, -0) ? "-0.000000" : actual.toFixed(6);
        return `${String(index).padStart(3, " ")}    ${shown}`;
    }).join("\n");
}

/**
 * 展示会话与读取代次分开管理。任何清空/新快照都会撤销旧剪贴板回调的提示权限，
 * 防止关闭后重开或从 A 切到 B 时，旧复制结果污染当前文档提示。
 */
export function createVectorPanel(nodes, { writeText = (value) => navigator.clipboard.writeText(value), notify = () => {} } = {}) {
    let info = null;
    let expanded = false;
    let revision = 0;
    function renderValues() {
        nodes.values.textContent = info ? previewVector(info.values, expanded) : "";
        nodes.heading.textContent = info ? `向量数值 · ${expanded ? "全部" : "前 8"} / ${info.dimensions} 维` : "向量数值";
        nodes.toggle.textContent = expanded ? "收起为前 8 维" : `展开全部 ${info?.dimensions ?? ""} 维`;
        nodes.toggle.setAttribute("aria-expanded", String(expanded));
    }
    function clear(message = "") {
        revision += 1;
        info = null;
        expanded = false;
        nodes.content.hidden = true;
        nodes.message.textContent = message;
        for (const key of ["version", "dimensions", "bytes", "zero", "fingerprint"]) nodes[key].textContent = "";
        nodes.copyFingerprint.disabled = true;
        nodes.copyVector.disabled = true;
        nodes.toggle.disabled = true;
        renderValues();
    }
    async function copy(kind) {
        if (!info) return;
        const current = revision;
        const value = kind === "fingerprint" ? info.sha256 : serializeVector(info.values);
        try {
            await writeText(value);
            if (current === revision) notify(kind === "fingerprint" ? "已复制完整 SHA-256 指纹。" : "已复制完整向量，保留 Float32 可往返精度。", false);
        } catch {
            if (current === revision) notify(kind === "fingerprint" ? "未能自动复制，可选择完整指纹手动复制。" : "浏览器未允许复制完整向量，请检查剪贴板权限后重试。", true);
        }
    }
    nodes.toggle.addEventListener("click", () => {
        if (!info) return;
        expanded = !expanded;
        renderValues();
    });
    nodes.copyFingerprint.addEventListener("click", () => copy("fingerprint"));
    nodes.copyVector.addEventListener("click", () => copy("vector"));
    clear();
    return {
        clear,
        set(document) {
            clear(!document.vectorInfo ? (document.vectorReady ? "服务未提供向量详情。" : "向量尚未生成。") : "");
            if (!document.vectorInfo) return;
            info = document.vectorInfo;
            nodes.content.hidden = false;
            nodes.version.textContent = info.embeddingVersion;
            nodes.dimensions.textContent = `${info.dimensions} 维 · float32`;
            nodes.bytes.textContent = `${info.byteLength} 字节`;
            nodes.zero.textContent = info.zeroVector ? "是" : "否";
            nodes.fingerprint.textContent = info.sha256;
            nodes.copyFingerprint.disabled = false;
            nodes.copyVector.disabled = false;
            nodes.toggle.disabled = false;
            renderValues();
        },
    };
}
