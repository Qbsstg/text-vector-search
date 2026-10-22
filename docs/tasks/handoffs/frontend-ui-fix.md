# UI-FINAL 任务轮询计时器修复交接

- 任务：UI-FINAL 浏览器缺陷修复；Owner：前端工程师。
- 工作树：`/Users/qbss/code/12times/.worktrees/frontend`；分支：`agent/frontend`。
- 原工作树基线：`4e722994fbec2c1cdf78272a0bfb6f3e6b760d43`；经主控明确批准执行 `git merge --ff-only main` 后，修复基线为 `8a6713c22f3204f8c1b87509fe47200567bdd87e`。
- 本交接与代码、回归测试同一提交；精确提交 SHA 在提交后的交付消息中提供，可用 `git log -1 --format=%H -- docs/tasks/handoffs/frontend-ui-fix.md` 核验。

## 复现与根因

主控在旧候选 `68d80cb` 的真实 Chrome 中记录：提交返回 202 后自动任务查询及手动启用有限刷新报 `TypeError: Illegal invocation`，任务区停留“尚未查询”。原始浏览器证据由主控保存于 `/private/tmp/12times-chrome-ui-skafk2b5/evidence/`，本任务未重新运行浏览器，也未把旧证据作为修复后通过证据。

`TaskTracker` 将默认 `setTimeout`、`clearTimeout` 原样保存为实例属性，随后通过 `this.setTimer(...)`、`this.clearTimer(...)` 调用，使浏览器宿主函数收到 `TaskTracker` 而非全局对象作为接收者。既有 Node 测试注入箭头函数计时器，未检验此默认路径。

## 修改文件与行为

1. `src/main/resources/static/tasks.mjs`：仅将两个默认计时函数改为显式调用 `globalThis.setTimeout(...)` / `globalThis.clearTimeout(...)` 的箭头包装；保留调用方注入计时函数的能力及所有轮询状态机逻辑。
2. `tests/frontend/tasks.test.mjs`：新增默认路径回归，将全局计时器替换为接收者敏感替身；验证默认调用接收者、60000ms 预算计时、响应后 2000ms 刷新、手动停止同时清除刷新与预算计时器、停止后不再 GET。原有 38 项测试与断言未修改。
3. `docs/tasks/handoffs/frontend-ui-fix.md`：本交接。

已检查 `static` 下同类宿主函数用法：`SearchSession` 的 `performance.now()` 保留对象接收者，API 计时器及 `fetchImpl` 采用普通函数调用，事件注册/取消在对应对象上调用；未发现另一个已证实的同类错误，因此没有扩大修改。

## 真实命令和结果

- Node：`v22.16.0`。
- 修复前先新增回归，执行 `node --test --test-name-pattern='默认计时器' tests/frontend/tasks.test.mjs`：退出 1，1 项失败，错误 `TypeError: Illegal invocation`，确认能检出旧实现。
- 修复后执行 `node --test tests/frontend/*.test.mjs`：退出 0，39 项全部通过，0 失败、0 跳过。既有 2 秒、60 秒、3 个自动任务上限、网络有限退避、写入不自动重试等回归继续通过。
- `git diff --check`：退出 0。

## 未执行、边界与风险

- 修复后的真实 Chrome、DOM/Network/Console 验收：未执行，交主控集成后重新验证；Node 替身通过不能宣称浏览器验收通过。
- Java17 全量 `./mvnw -B clean verify`、CI、不可变候选构建：未执行，按主控派发由集成方完成。
- 未启动应用或浏览器，未连接服务器，未构建或发布候选，未改 Java、API、DDL、算法、依赖、资源参数或其他角色文件。
- 风险限于默认计时函数调用方式；轮询调度、预算、并发上限、取消与写请求重试规则均未改变。需使用新候选在真实 Chrome 核验提交后查询、手动查询和停止，不能沿用旧 JAR 的结论。
