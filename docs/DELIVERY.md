# 文本向量化与检索｜交付与验收索引

本项目覆盖文本提交、异步向量化、持久任务恢复、同步 Top-K 检索、文档管理及命中计数。使用 Java 17、Spring Boot、MyBatis、H2 文件数据库和 Java 有界执行队列，提供原生 Web 演示台。算法提取确定性的字面特征，不调用 LLM / Embedding API，不声称语义理解或检索准确率。

> **当前状态：前端改版已上线，有限线上功能核证通过；本地约25分钟稳定性检查真实 FAILED（提交503），原因未定位，不宣称整体稳定性通过。** 该失败不按人工例外或CI通过处理，详见[本次发布核证](testing/UI-RELEASE-verification.md)。

[项目首页与本地运行](../README.md) · [首发历史交付结论](testing/最终交付聚合结果.md) · [关键 AI 对话与人工决定](ai-notes/003-关键对话与人工决策.md)

## 先看版本与状态

本索引于 2026-10-23 基于已公开提交 `2fc85652c0dda81efdc6cab9ccf9660d8af59e09` 整理。这里只核读 Git、源码和既有报告、CI；本轮没有重新构建、启动应用、访问业务接口或登录服务器。下表区分公开源码、当前已记录的生产包和首发历史。生产事实来自公开发布核证，本轮没有独立探测当前运行状态；不能以较新的 main 代替生产包身份。

| 维度 | 可追溯事实与来源 |
| --- | --- |
| 本轮公开源码基线 | [2fc8565](https://github.com/Qbsstg/text-vector-search/commit/2fc85652c0dda81efdc6cab9ccf9660d8af59e09)，包含前端改版和后续质量、文档整合；是源码快照，不是线上版本探针 |
| 当前生产源码（据发布记录） | `2764eddf34418fdcc8ed666e36937673c7feadb8`，依据[UI-RELEASE实测](testing/UI-RELEASE-verification.md) |
| 当前生产 JAR SHA-256 | `b8b67460ee6af26ec5ea7854377b63919c18c37760b11e3fbf49f7672be7f45f`；来源[发布CI39915764822](https://github.com/Qbsstg/text-vector-search/actions/runs/39915764822)，104 Unit + 126 IT + 52 Node = 282，Success |
| 后续模板与主线核证 | [a4be0aa / CI39921238979](https://github.com/Qbsstg/text-vector-search/actions/runs/39921238979) Success；后续缓存代理模板更新未替换上述固定应用JAR；文档HEAD不冒充运行版本 |
| 本次发布状态 | 已部署供用户最后测试；有限HTTPS、Chrome、重启持久化和独立恢复通过；本地25分钟提交503的稳定性失败仍开放，完整35分钟及其后本地重启段未执行。见[发布跟踪](tasks/UI-RELEASE-20261023.md) |
| 首发历史生产包 | `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f` / `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`；[原产物CI39838054946](https://github.com/Qbsstg/text-vector-search/actions/runs/39838054946)，仍是后端字节冻结和代码回退基线 |
| 首发完整工程回归 | [661ed08 / CI39891645582](https://github.com/Qbsstg/text-vector-search/actions/runs/39891645582)：104 Unit + 126 IT + 43 Node = 273；其[最终聚合结论](testing/最终交付聚合结果.md)只对应当时已批准范围 |

附件中 `661ed08`、273项和d016固定包描述的是首发FINAL-RELEASE，不能继续标为当前前端生产包。新版使用其自己的源码、JAR摘要与282项CI；[发布核证](testing/UI-RELEASE-verification.md)记录新旧包差异仅在static，后端字节、算法/API/DDL与资源参数保持。既有首发PASS与新版稳定性FAILED分别保留，不将用户最后测试视为已经验收完成。

## 原题六项能力与阅读入口

| 原题能力 | 已实现行为 | 设计与验收入口 |
| --- | --- | --- |
| 文档提交与异步处理 | `POST /api/documents` 校验参数，文档和任务同事务持久化，返回 taskId；后台队列执行 | [B2 接收与调度](design/b2-implementation-contract.md)、[B2 验收](testing/B2-verification.md) |
| 固定向量 | 256 维 float；规范化、字符 1/2-gram、带符号 SHA-256 特征映射、L2 归一化；同文确定，空文为零向量 | [B1 算法与 codec](design/b1-algorithm-contract.md)、[B1 验收](testing/B1-verification.md) |
| 任务四态 | `GET /api/tasks/{taskId}` 返回 QUEUED / RUNNING / SUCCEEDED / FAILED；成功后关联文档；终态和恢复持久化 | [B3 状态机与事务](design/b3-implementation-contract.md)、[B3 验收](testing/B3-verification.md) |
| 同步相似检索 | `POST /api/search` 使用同一向量算法；成功、就绪、未失效、双版本匹配及可选渠道过滤后评分和 Top-K | [B4 检索与计数](design/b4-implementation-contract.md)、[B4 验收](testing/B4-verification.md) |
| 文档管理 | 文档列表、详情；主动失效及幂等处理；失效与任务终态、向量就绪独立 | [六接口契约](api/api-contract.md)、[正式交付闭环](testing/最终交付聚合结果.md) |
| 溯源与统计 | 保留渠道、提交/完成时间等元信息；仅最终命中原子增加 hitCount；GET 不计数 | [B4 事务规则](design/b4-implementation-contract.md)、[并发与一致性报告](testing/CONCURRENCY-REPORT.md) |

## 核心设计与取舍

- [架构总览](design/system-architecture.svg)：HTTPS / 认证入口、API、H2、后台向量路径、同步检索路径与生命周期。README 同时保留可展开的原生 Mermaid。
- [运行与资源契约](design/runtime-contract.md)：单 JVM、单 H2 文件；默认 Worker=1、Queue=64、SearchPermits=1、连接池最大4。H2 任务表为事实来源，内存队列仅作缓冲。
- [B3 事务与恢复](design/b3-implementation-contract.md)：提交锁覆盖物理提交；CPU 计算在事务外；完成以 RUNNING + run_token 条件更新，向量与成功终态同一短事务写入；启动先恢复再就绪，关闭禁止迟到回写。
- [B4 检索与计数](design/b4-implementation-contract.md)：候选快照、余弦和稳定 Top-K；评分完成后只给最终命中计数；提交未知和网络失败不盲目重试。
- [业务就绪与发布契约](design/final-readiness-release-contract.md)、[受信任代理契约](design/proxy-deployment-contract.md)：健康判断依赖真实组件状态，HTTPS 同源写入与代理信任边界分别验证。

这些设计解决题目中的工程一致性和资源边界，不是大规模语义检索平台、水平扩展架构或 SLA 承诺。

## 测试结果必须分层阅读

| 统计维度 | 已有真实结论 | 边界与来源 |
| --- | --- | --- |
| 首发自动化 | 273 项通过 | 固定首发交付范围；[最终聚合结果](testing/最终交付聚合结果.md)与对应 CI；不把旧253、后续复跑继续累加 |
| 原70条主验收 | 按批准的最新判据，70 PASS | 属于需求映射，不是另外70个独立自动化测试；[历史矩阵](testing/final-release-local-cases-matrix.md)保留原阶段结果，当前结论看最终聚合 |
| REC-07 | 修订判据 REC-07-R1 独立实测通过；原预期 FAIL 保留 | [人工裁决](design/REC-07-验收判据裁决-20261023.md)、[修订实测](testing/REC-07-修订验收.md)；不把历史 FAIL 改成 PASS |
| 首发真实入口 | 64/64 HTTPS 请求符合预期，正式 Chrome 闭环、重启持久化、冷备及独立恢复有记录 | [正式交付聚合](testing/最终交付聚合结果.md)；请求次数不等于测试数量，Chrome 网络中段不连续的限制保留 |
| 原 UI 验收 | 28 PASS；F05 为 NOT_RUN / APPROVED_EXCEPTION | [原 Chrome 报告](testing/UI-browser-verification.md)、[有限例外](testing/F05-例外申请.md)；例外不是测试通过 |
| 首发包的后续线上专项 | 39个功能声明场景通过；631次有界压力请求，含57次保护性429、无5xx和未知结果 | [线上功能](testing/ONLINE-FUNCTIONAL-REPORT.md)、[性能基线](testing/PERFORMANCE-BASELINE.md)对应d016包，不能作为当前2764新版复测；固定 N=9 / K=1 / 长查询短窗混合负载的最高观测成功QPS为7.852，不是系统最大容量 |
| 当前前端改版 | 282项自动化通过；另有新版64次HTTPS请求和真实Chrome、重启/独立恢复实测；本地稳定性FAILED | [新版发布核证](testing/UI-RELEASE-verification.md)、[本地改版历史验收](testing/UI-REDESIGN-LOCAL-verification.md)；新版与首发各自的64请求不得混算，不把HTTP/Chrome加入282，短样本不覆盖稳定性失败 |

后续线上专项并未重新在线覆盖原70条全部联合断言；其报告单独记录7 ONLINE_PASS、1 LOCAL_PASS、29 PARTIAL、33 NOT_RUN。该分层不推翻首发70条已批准结果，也不把历史结果冒充本轮线上通过。

### 历史与未执行内容

历史报告开头的 BLOCKED、REC-07 FAIL、271项和“尚未部署”，记录的是当时事实；阅读最新结论后再按需要追溯，不以旧阶段快照判断当前项目未完成。

- F05 的真实 FAILED Chrome 端到端分支仍未执行，只有已批准的有限风险接受；不制造生产失败来补齐表格。
- 首发 D9 未触发，没有上一已验版本的旧版回滚演练；代码回退方案不等于实际回滚通过。该结论只描述首发，不替后续版本回退作结论。
- 正式环境破坏性故障、整机重启验证、无限长期负载/SLA、带人工标签的语义准确率未验证；既有隔离强杀/故障测试不能改写为生产故障注入。
- 后续专项的 Worker=2/4、N=100/1000/5000、队列极限对照仍未执行；Hikari精确占用、精确队列长度与GC单次暂停未测量。
- 旧临时预览503与本次新包独立本地稳定性503均有真实记录；最新门禁在1500.13秒失败，完整35分钟及其后本地重启未完成，原因未定位。线上有限64请求和服务器重启不是对该故障的修复证明。本轮不修改后端或操作服务器排查。

## AI、Git 与交付资料

原题要求保留关键 AI 对话及真实 Git 迭代。本项目公开少量真实问答节选和明确标注的执行摘要，人工负责范围、契约冲突裁决、版本与例外批准；不把 AI 建议当作未经审查的事实。

| 阅读目的 | 实际入口 |
| --- | --- |
| 初始化时的真实用户要求与角色分工 | [C0 关键对话与决策](ai-notes/001-c0-kickoff.md) |
| 契约冲突、F05边界、压测范围与人工决定 | [关键对话与人工决策](ai-notes/003-关键对话与人工决策.md) |
| 已有迭代过程 | [真实 Git 历史](https://github.com/Qbsstg/text-vector-search/commits/main/)、[历史英文提交中文对照](git/提交历史中文说明.md) |
| PUBLIC 授权与公开范围 | [公开授权与安全复核](git/公开授权与安全复核-20261023.md)；既有历史判断按当时范围阅读 |
| 部署设计与模板 | [运行契约](design/runtime-contract.md)、[代理契约](design/proxy-deployment-contract.md)、[公开部署模板目录](../deploy/)；模板与部署准备文件不是最新实机执行结论 |
| 项目阶段与发布状态 | [阶段任务入口](tasks/README.md)、[前端改版发布跟踪](tasks/UI-RELEASE-20261023.md)；按实测PASS/FAILED/未执行逐项阅读，不以发布完成推导所有门禁通过 |

原始笔试 PDF、完整私人聊天、未批准原始请求/截图、诊断和服务器操作材料、凭据、证书及数据库不因导航需要而公开；完整原件保持原有受限状态。已有公开摘要和原件 SHA 用于溯源，不承诺全部原件可匿名下载。

## 本地运行与演示

按 [README 本地运行](../README.md#本地运行) 使用 JDK17、Maven Wrapper 构建并测试，再以 `APP_DATA_DIR` 指向新的本地持久 H2 目录启动 `target/vector-search.jar`。同一数据目录只允许一个实例，不使用生产库或其他工作树的数据；本轮仅核对命令与仓库配置/CI一致，没有重新执行构建和启动。

可复核的源码入口：[构建描述](../pom.xml)、[Maven Wrapper](../mvnw)、[随项目提供的自动建表 DDL](../src/main/resources/schema.sql)。

演示地址：**https://text-vector-search.lemoncharger.com**。页面和API使用HTTPS及入口认证；访问凭据由项目负责人通过授权渠道另行提供，不在仓库、截图或公开聊天中给出。本索引引用已有发布记录，不保证本文打开时服务实时可用；当前稳定性失败及用户最后测试状态见上文。

建议五分钟演示：说明字面算法边界 → 提交一篇合成文档 → 查看真实任务状态 → 精确渠道检索及计数 → 失效后新搜索排除 → 查阅CI、事务恢复证据与未测边界。提交会持久占用容量，失效不释放容量；搜索真实计数。现场不进行压测、强杀或数据库故障注入。
