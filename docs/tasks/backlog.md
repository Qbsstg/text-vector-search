# 任务板

2026-10-23：用户原则通过B2核心并授权立即B3。PM读取基线`1c8676b0cf4d347e545bcb4d60486938db5df4ec`；主控在集成源码28cd42d完成173项本地回归；CI门禁另登记，不混用旧阶段结果。B1/B2及全部历史证据保留，详见[B3阶段进展报告](B3-阶段进展报告.md)。

| ID | Owner | 状态 | 真实证据与下一门禁 |
| --- | --- | --- | --- |
| C0 | 主控及五角色 | DONE | 3 unit+6 IT、JAR/浏览器，见C0-verification |
| B1 / QA1-B1 / PM-B1 | 后端/QA/PM/主控 | DONE | 原算法/版本/黄金及29 unit+22 IT证据冻结 |
| B2-01/02、03、04、05 / QA-B2前后段 / PM-B2 | 后端/QA/PM/主控 | DONE | 53769c6完整53 unit+44 IT+25 Node=122通过；用户原则通过核心；不倒退为待实现 |
| GH创建/私有推送/阶段CI | 主控 | DONE | B3启动前本地/远程main bd18c49一致，run39808883527完整success（53 unit+44 IT+25 Node）；不是B3验证 |
| DEP2基础准备 / NGINX-PREP | 服务器 | DONE | 原账号/目录/独立HTTP检查证据保留；后端unit仍inactive/disabled；新TLS状态另列 |
| CN-存量注释 / GIT-CN历史对照 | 主控/QA/服务器 | DONE | 三独立整改、28条历史中文映射及原SHA保留，不改写历史 |
| FE1 | 前端 | REVIEW_UI | 304e5ed、25 Node及真实管理HTTP已通过；Work本地浏览器禁用，视觉/键盘/真实DOM/XSS未执行 |
| B3-01生产handler/原子终态 | 后端 | DONE | 00c5af5/90bcdf4/bb6e5eb及QA16终态IT已合入；真实生产B1、空文、令牌/两表事务与失效竞态通过 |
| B3-02启动恢复/就绪 | 后端 | DONE | 13953cf任务时间确认修复及独立IT通过；恢复commit前拒接/扫描0，commit后READY并真实B1恢复 |
| B3-03关闭/写入许可/降级 | 后端 | DONE | fe32b5b修H2退出钩子；真实生产默认URL的正常/超时SIGTERM及短事务排空通过，迟到CPU SQL0 |
| QA-B3设计 | 测试 | DONE | 15组设计保留，16终态+11进程/生命周期IT已执行通过，不把设计数量另算测试 |
| QA-PROXY | 测试 | DONE | 3个独立IT通过、实际profile观测有记录；非可信来源是Tomcat组件反例，127.0.0.2真实socket未完成，保留限制 |
| PROXY配置 | 主控 | REVIEW | 5044e07配置及独立观测通过；默认组合403、可信profile同源202/跨源403；外部不可直达8080/实际业务代理仍须发布阶段验收 |
| QA-B3后半执行 | 测试 | DONE | 4523a67及证据已合入；四点真实强杀137、正常/超时SIGTERM143、同库恢复与5生命周期IT全过，11子进程退出 |
| TLS-CHECK | 服务器/主控 | DONE | f9cf401角色交接后，主控系统信任curl及SSH独立复核通过；仅固定检查站点，业务inactive/disabled且8080无监听 |
| B3-04集成门禁 | 主控 | IN_PROGRESS | 源码28cd42d Java17全量74 unit+74 IT+25 Node=173通过，原件归档；私有main最终CI待核证 |
| PM-B3 | 项目管理 | DONE | 9e2ef91/be13843真实任务快照已合入；主控继续登记最终源码/CI，不改B1/B2结果 |
| B4 | 后端 | TODO | B3终态/恢复/关闭/READY门禁通过后另行派发搜索/过滤/计数，不自动展开 |
| FE2/FE3、QA交付复演 | 前端/测试 | TODO | 真终态/搜索联调、浏览器与新环境复演，按对应实现派发 |
| DEP3正式业务发布 | 服务器 | TODO | 业务门禁、真实入口认证和用户具体发布确认；检查站点/模板不等于正式发布 |

主控真实JAR普通/空文成功、失效操作后重启保持终态；pid35214/35215均SIGTERM退出143，结果在`/private/tmp/12times-root-b3-smoke-np2hkgyz/result.json`。这是生产冒烟，不替代QA后半真实故障。B1及两套B2原CI已留存，PM校验17+31+31份原文件均匹配，不新增测试数量。

B3依据[实施契约](../design/b3-implementation-contract.md)，后端单一Owner持有状态机/事务。B2原“无handler保持QUEUED/旧RUNNING拒接”断言在明确隔离的历史测试组成保留，另测真实B3；不全局禁handler或假写终态。

最新服务域名为`text-vector-search.lemoncharger.com`，旧多级域名只留历史。证书已提供且角色核验有效，不再列“待提供”；当前80/443仅固定检查，真实API/H2/管理404、未知SNI拒握手，业务proxy/BasicAuth仅未启用模板，无入口密码，后端8080无监听。云安全组仍由用户人工核对，正式业务未获自动发布授权。

关键路径：本地最终门禁完成→私有main最终CI核验/证据归档；B4和DEP3不抢跑。个人目标2026-10-23 18:00，不沿用B2工时或把剩余自然时间当有效工时。FE1浏览器、语义相关性/性能、B4计数仍未验证；用户未跟踪测试资料原样保留，70条不能算已执行。
