# 前端改版本地任务

2026-10-23；任务UI-REDESIGN-LOCAL；基线5f1740d2141473a7595eeb41eb6efb037a847815。

| 子任务 | Owner | 当前状态 |
| --- | --- | --- |
| 隔离集成/前端分支及线上边界 | 主控 | DONE |
| 全页HTML/CSS/展示交互改版 | 前端Owner | IN_PROGRESS |
| 新增展示/弹窗相关单测与全Node | 前端Owner/独立QA | TODO |
| Java17完整工程回归 | 主控 | TODO |
| 本地真实JAR与Chrome/IAB验证 | 主控 | TODO |
| 桌面/手机视觉比较及问题修复 | 主控/前端Owner | TODO |
| 形成可审阅本地待发布版本 | 主控 | TODO |
| main合并、远程推送、线上发布 | 用户后续安排 | NOT_RUN / DEFERRED |

独立QA已给出NAV/INPUT/SEARCH/SUBMIT/LIST/DETAIL/TASK/DIALOG/SAFE/VISUAL十组矩阵；新UI尚未测试，不从基线43 Node继承PASS。关键新增关注提交关闭后202迟到、列表draft/applied快照、详情旧响应、失效确认链和移动端操作可达。

服务器不参与本轮执行。原正式JAR、Nginx、systemd、认证、线上数据库和正在运行的压测保持现有状态。完整约束见../design/frontend-redesign-contract.md。
