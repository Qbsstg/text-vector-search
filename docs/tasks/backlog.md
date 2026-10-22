# 任务板

2026-10-23：用户原则通过B2核心并授权立即B3。PM读取基线`de71eac7c0ee1ea9eda3266dc2e3497c49410714`；下表为阶段快照，不预造最终B3源码SHA/回归。B1/B2及全部历史证据保留，详见[B3阶段进展报告](B3-阶段进展报告.md)。

| ID | Owner | 状态 | 真实证据与下一门禁 |
| --- | --- | --- | --- |
| C0 | 主控及五角色 | DONE | 3 unit+6 IT、JAR/浏览器，见C0-verification |
| B1 / QA1-B1 / PM-B1 | 后端/QA/PM/主控 | DONE | 原算法/版本/黄金及29 unit+22 IT证据冻结 |
| B2-01/02、03、04、05 / QA-B2前后段 / PM-B2 | 后端/QA/PM/主控 | DONE | 53769c6完整53 unit+44 IT+25 Node=122通过；用户原则通过核心；不倒退为待实现 |
| GH创建/私有推送/阶段CI | 主控 | DONE | B3启动前本地/远程main bd18c49一致，run39808883527完整success（53 unit+44 IT+25 Node）；不是B3验证 |
| DEP2基础准备 / NGINX-PREP | 服务器 | DONE | 原账号/目录/独立HTTP检查证据保留；后端unit仍inactive/disabled；新TLS状态另列 |
| CN-存量注释 / GIT-CN历史对照 | 主控/QA/服务器 | DONE | 三独立整改、28条历史中文映射及原SHA保留，不改写历史 |
| FE1 | 前端 | REVIEW_UI | 304e5ed、25 Node及真实管理HTTP已通过；Work本地浏览器禁用，视觉/键盘/真实DOM/XSS未执行 |
| B3-01生产handler/原子终态 | 后端 | REVIEW | PM实际观察角色提交00c5af5（终态事务/handler/写屏障）；不在PM读取基线，提交测试/主控合入及真实生产验收待核证 |
| B3-02启动恢复/就绪 | 后端 | IN_PROGRESS | Owner统一实现；恢复提交前拒接/停派，旧RUNNING恢复后才READY；未有验收结论 |
| B3-03关闭/写入许可/降级 | 后端 | IN_PROGRESS | 有界等待与迟到CPU禁止回写、未知终态先核对；尚未验证，不用普通重启替代强杀 |
| QA-B3设计 | 测试 | DONE | ecf7cb5已合入15组计划，含4个真实强杀检查点；仅设计完成，15组执行均NOT_RUN |
| QA-PROXY | 测试 | IN_PROGRESS | 独立ProxyOriginIT观测中；有效scheme/host/port及不可信来源边界待结论 |
| PROXY配置 | 主控 | REVIEW | 5044e07默认97项Java回归通过；默认HTTPS头403、回环可信proxy配置202/跨源403已实测，完整信任边界仍待QA |
| QA-B3执行 | 测试 | READY | 依赖实现/窄接口与历史B2隔离组成审查；独立终态、恢复顺序、SIGTERM/超时及4场景强杀 |
| TLS-CHECK | 服务器 | REVIEW | f9cf401已合入；新域名证书链/公私钥匹配，角色正常DNS及--resolve验证不用-k；主控独立TLS复核待补 |
| B3-04集成门禁 | 主控 | TODO | 原子终态、真实强杀、关闭/就绪独立验收后Java17完整verify+25 Node，记录最终源码SHA/CI |
| PM-B3 | 项目管理 | REVIEW | 本轮九份任务文档，仅文档检查；交接后结束释放槽位 |
| B4 | 后端 | TODO | B3终态/恢复/关闭/READY门禁通过后另行派发搜索/过滤/计数，不自动展开 |
| FE2/FE3、QA交付复演 | 前端/测试 | TODO | 真终态/搜索联调、浏览器与新环境复演，按对应实现派发 |
| DEP3正式业务发布 | 服务器 | TODO | 业务门禁、真实入口认证和用户具体发布确认；检查站点/模板不等于正式发布 |

B3依据[实施契约](../design/b3-implementation-contract.md)，后端单一Owner持有状态机/事务。B2原“无handler保持QUEUED/旧RUNNING拒接”断言在明确隔离的历史测试组成保留，另测真实B3；不全局禁handler或假写终态。

最新服务域名为`text-vector-search.lemoncharger.com`，旧多级域名只留历史。证书已提供且角色核验有效，不再列“待提供”；当前80/443仅固定检查，真实API/H2/管理404、未知SNI拒握手，业务proxy/BasicAuth仅未启用模板，无入口密码，后端8080无监听。云安全组仍由用户人工核对，正式业务未获自动发布授权。

关键路径：终态提交→恢复/生命周期→独立强杀/信任边界→主控完整门禁；B4和DEP3不抢跑。个人目标2026-10-23 18:00，不沿用B2工时或把剩余自然时间当有效工时。FE1浏览器、语义相关性/性能、B4计数仍未验证；用户未跟踪测试资料原样保留，70条不能算已执行。
