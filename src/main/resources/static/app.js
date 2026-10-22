"use strict";

const tabs = Array.from(document.querySelectorAll('[role="tab"]'));

function selectTab(selected) {
  for (const tab of tabs) {
    const active = tab === selected;
    tab.setAttribute("aria-selected", String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute("aria-controls")).hidden = !active;
  }
}

for (const tab of tabs) {
  tab.addEventListener("click", () => selectTab(tab));
  tab.addEventListener("keydown", (event) => {
    let nextIndex;
    const currentIndex = tabs.indexOf(tab);
    if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % tabs.length;
    if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = tabs.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    selectTab(tabs[nextIndex]);
    tabs[nextIndex].focus();
  });
}

const healthButton = document.getElementById("check-health");
const healthMessage = document.getElementById("health-message");
const healthResult = document.getElementById("health-result");

healthButton.addEventListener("click", async () => {
  if (healthButton.disabled) return;
  healthButton.disabled = true;
  healthResult.hidden = true;
  healthMessage.textContent = "正在读取基础健康接口…";
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 5000);

  try {
    const response = await fetch("/health", {
      method: "GET",
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: controller.signal,
    });
    if (!response.ok) {
      healthMessage.textContent = `基础健康检查未成功（HTTP ${response.status}），无法确认基础连接状态。业务入口仍未开放。`;
      return;
    }
    const health = await response.json();
    if (health === null || typeof health !== "object" ||
        typeof health.status !== "string" || typeof health.stage !== "string" ||
        typeof health.businessReady !== "boolean") {
      healthMessage.textContent = "健康接口响应格式与契约不符，无法确认基础连接状态。业务入口仍未开放。";
      return;
    }
    document.getElementById("health-status").textContent = health.status;
    document.getElementById("health-stage").textContent = health.stage;
    document.getElementById("health-ready").textContent = String(health.businessReady);
    healthResult.hidden = false;
    healthMessage.textContent = health.businessReady === false
      ? "已收到基础健康响应；businessReady=false，业务尚未就绪。此响应不能证明检索、任务或恢复功能可用。"
      : "已展示接口实际返回值；当前 C0 页面没有接入业务功能，业务按钮仍不可用。";
  } catch (error) {
    healthMessage.textContent = error.name === "AbortError"
      ? "基础健康检查已超时，无法确认基础连接状态。可手动再次检查。"
      : "无法读取有效的基础健康响应，连接或响应解析可能失败。可手动再次检查。";
  } finally {
    window.clearTimeout(timeout);
    healthButton.disabled = false;
  }
});
