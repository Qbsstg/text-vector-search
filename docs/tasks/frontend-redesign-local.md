# 前端改版本地任务

2026-10-23；UI-REDESIGN-LOCAL；基线5f1740d2141473a7595eeb41eb6efb037a847815。

| 子任务 | Owner | 实际状态与证据 |
| --- | --- | --- |
| 独立工作树及线上边界 | 主控 | DONE；codex/frontend-redesign，与线上/原归档隔离 |
| 全页HTML/CSS/展示交互改版 | 前端Owner | DONE_LOCAL；011439d及三次独立修复，保持原六接口 |
| 展示/弹窗单测与Node全量 | 前端Owner/独立QA | PASS；Node22.16.0，52/52，新增9项，旧43项保留 |
| Java17最终工程回归 | 主控 | PASS；104 unit+126 IT，无失败/错误/跳过，最终执行实际结束12:55:43 |
| 新JAR内嵌静态资源及真实本地六接口 | 主控 | PASS；源码0b0f18b，包90916190…，详情见验收报告 |
| 桌面/手机联合视觉比较及修复 | 主控/前端Owner | PASS；3项P2已关闭，根design-qa.md |
| 浏览器实际交互 | 主控 | PASS于本地列明范围；IAB真实请求和截图，与HTTP/Node分层 |
| 可审阅本地待发布版本 | 主控 | DONE_LOCAL；预览127.0.0.1:18183，临时独立H2，默认日志，15:02:54自动正常停止 |
| 旧临时预览503原因 | 主控/后端后续排查 | OPEN；新JAR同库/重启后未复现；不得宣称已定位修复 |
| main合并、远程推送、CI、线上发布与复验 | 用户后续版本安排 | NOT_RUN / DEFERRED；本轮不执行 |

后端、服务器本轮不参与变更；没有SSH或线上业务请求，没有修改Java/API/DDL/算法/依赖/资源参数。原分支codex/final-release-local-evidence及用户测试材料保留。新增原始浏览器证据只在本地.cache，不推送公开仓库。

交接入口：../testing/UI-REDESIGN-LOCAL-verification.md；视觉入口：../../design-qa.md。实际读屏、真实手机与公网HTTPS/认证/压测仍不在本轮通过范围；后续版本发布不得沿用d016旧JAR批准值。
