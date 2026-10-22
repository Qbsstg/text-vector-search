# 任务板

2026-10-23 真实Chrome收尾。用户已原则批准B1—B4核心；Work浏览器权限已恢复。原68d80cb候选的UI阻断FAIL保留，修复候选改为`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，不因后续纯文档HEAD改变该候选。

| ID | Owner | 状态 | 已核事实与剩余门禁 |
| --- | --- | --- | --- |
| C0 / B1 / B2 / B3 | 后端/QA/主控 | DONE | 原算法、事务、真实强杀、生命周期、SHA/CI原件保持，不改历史 |
| B4核心/独立QA/集成 | 后端/QA/主控 | DONE | 用户原则通过，原229项及计数/并发/未知提交/真实RST证据保留，不扩展六接口 |
| CN注释 / GIT-CN历史 | 主控/QA/服务器 | DONE | 独立中文化、全量回归和28条历史对照保持 |
| PUB-REVIEW | 主控 | DONE | 用户确认本人公开并授权，保持PUBLIC；824blob历史格式审计不是任意秘密的完备保证 |
| HEALTH-FINAL | 后端/QA/主控 | DONE | B4运行事实合取，非硬true；健康不代替UI、HTTPS认证或发布许可 |
| QA-FINAL Linux锁/冷备恢复 | QA/主控 | DONE | 实际H2活动占用/锁拒绝、hash/权限、拒覆盖/错SHA、新目录真实JAR恢复通过；systemctl inactive仅shim |
| UI-FIX-TIMER | 前端/主控 | DONE | cda3f8c修复默认计时器receiver；新增敏感回归修前失败修后通过，真实提交/轮询入口复验通过 |
| UI-FIX-ICON | 前端/主控 | DONE | bf7aeac显式同源SVG；新候选冷加载200，无默认favicon500；旧失败原件不改 |
| CANDIDATE固定包 | 主控 | DONE | d016d6e，CI39838054946 success，artifact12451951931；实包/独立CI日志hash/六参校验一致，253项通过 |
| FINAL-CI/Chrome归档 | 主控/QA | DONE | 新候选CI、首轮26项Chrome及三未验项原件，另追加F03/U12真实补测原件、图片映射/SHA归档；旧失败及252项不删。trace过滤与采集缺口明确 |
| FE1—FE3代码/HTTP | 前端/主控 | REVIEW_UI | 39 Node和既有HTTP通过；真实Chrome已执行，完整UI门禁未通过 |
| UI-MANUAL-PREP | 前端 | DONE | U01—U18/F01—F11共29项脚本保留，模板与实际结果分开 |
| UI-FINAL真实Chrome | 主控/QA/用户协助 | REVIEW_UI | 28 PASS、F03/U12真实补测已核证；F05保持NOT_RUN，例外申请待用户批准，不刻意制造FAILED |
| F03 / U12补测 | 主控/用户/QA | DONE | 4次正式trusted可见性、17次真实GET、隐藏内0、原60秒deadline不变；F03受控层及U12分层组合通过，原尝试保留 |
| F05例外申请 | QA/主控/用户 | REVIEW | 已有9 unit/16 IT与前端替身证据，真实FAILED浏览器缺口继续NOT_RUN，待用户明确接受风险，不标PASS |
| DEP2 / TLS-CHECK | 服务器/主控 | DONE | 沿用02:41快照：TLS检查200/API404，80/443监听，应用inactive/disabled。SSL无需重索 |
| DEP3-PREP | 服务器/主控 | DONE | 不可变包、认证、可信代理、持久单实例锁、冷备/回退计划齐备；更新候选以d016为准，实机运行仍未验 |
| DEP3正式业务发布 | 用户/服务器/主控 | TODO | UI门禁完成或明确接受F05例外后，对固定d016源码/JAR与安装、账号、上传、env/unit、启动、认证代理、重启及分别开机策略独立批准；未执行服务器业务写入 |
| PM-FINAL | 项目管理/主控 | REVIEW | 历史PM交接完成，本轮主控同步真实状态；剩余门禁按清单追踪，不虚报最终交付完成 |

CI新候选：104 unit+110 IT+39 Node=253，失败/错误/跳过0。JAR SHA-256为`64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`，27309072字节；schema和B1版本未改。原68候选虽252项通过，Chrome已FAIL，不再作为待发布候选。旧CI脚本首次失败、修复及原件全部保留。

Chrome在独立临时库运行03:15:39—03:58:03，北京时间；受控hook已reload清除，网络恢复，正常SIGTERM143/Hikari关闭，原DB/证据保留。真实负分、执行区间内失效、计数、分页、XSS、离线不重试、键盘/窄屏/200%已有证据；注入分支不能证明真实FAILED、未知数据库提交或SQL竞态。

本轮追加：F03/U12由真实用户切页完成，初轮无事件和focus emulation纠正均保留；04:22:16本次PID62398正常SIGTERM143，DB保留。旧26项未重跑，核心代码未改。

剩余关键路径：用户决定F05明确例外→对固定版本及具体服务器操作独立批准→DEP3及实际受保护入口/重启验收约1—2小时→最终交接约15—30分钟，等待人工另计。未批准例外前UI门禁保持待决，F05始终NOT_RUN；不再要求制造真实FAILED。18:00目标不降低门禁。详见[审批清单](最终交付准备与审批清单.md)与[独立UI报告](../testing/UI-browser-verification.md)。
