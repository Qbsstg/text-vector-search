# 交付与验收索引

本页按照原始面试题组织交付物。下列链接分别说明实现结果、设计思路、使用方法、AI关键对话与验证证据。

**文档类型：原题要求与实际交付成果对照清单**

**阅读目的：直接找到“题目要求什么、交付了什么、证据在哪里”**

| 对应笔试要求 | 资料类别 | 文档名称 | 能看到什么 |
| --- | --- | --- | --- |
| 必交项：完整可启动项目 | 项目源码 | [完整项目源码](https://github.com/Qbsstg/text-vector-search) | Java、静态页、DDL、Wrapper与测试代码 |
| 加分项：设计思路 | 设计思路 | [系统设计与技术选型](DESIGN.md) | 需求、方案对比、线程、事务、恢复与取舍 |
| 加分项：使用说明 | 使用说明 | [项目使用操作手册](USER-GUIDE.md) | 本地启动、自动建表、页面和六接口完整示例 |
| 必交项：AI关键对话 | AI协作 | [AI关键对话与人工决策](AI-COLLABORATION.md) | 已核原文节选、AI建议、人工判断和实现结果 |
| 必交项：Git记录 | Git记录 | [真实提交历史](https://github.com/Qbsstg/text-vector-search/commits/main/) | 真实迭代时间线，不重写已推送历史 |
| 加分项：单元测试 | 测试报告 | [测试与质量验证](testing/UI-RELEASE-verification.md) | 当前282项自动化、有限现场核证与已知失败 |
| 加分项：自行部署 | 在线演示 | [正式HTTPS演示站点](https://text-vector-search.lemoncharger.com) | 已部署应用，须经授权渠道获取入口凭据 |

**材料已提供不等于具体测试全部通过。** 当前前端已上线，有限线上功能核证通过；本地约25分钟稳定性检查真实FAILED（提交503），原因未定位，不能宣称整体稳定性通过。该失败不按F05有限例外或CI成功处理。

[返回项目首页](../README.md) · [项目文档导航](README.md)

项目采用Java17、Spring Boot、MyBatis、H2文件数据库及Java有界执行队列，模拟确定性字面特征向量，不调用LLM/Embedding API，不保证语义理解。源码仓库是本次主要交付形态；若另交源码压缩包，应排除编译产物、日志、数据库和未获授权材料。

## 原题功能与技术要求对照

| 原题能力 | 已实现行为 | 设计与验收入口 |
| --- | --- | --- |
| 文档提交与异步处理 | `POST /api/documents` 校验参数，文档和任务同事务持久化，返回 taskId；后台队列执行 | [B2 接收与调度](design/b2-implementation-contract.md)、[B2 验收](testing/B2-verification.md) |
| 固定向量 | 256 维 float；规范化、字符 1/2-gram、带符号 SHA-256 特征映射、L2 归一化；同文确定，空文为零向量 | [B1 算法与 codec](design/b1-algorithm-contract.md)、[B1 验收](testing/B1-verification.md) |
| 任务四态 | `GET /api/tasks/{taskId}` 返回 QUEUED / RUNNING / SUCCEEDED / FAILED；成功后关联文档；终态和恢复持久化 | [B3 状态机与事务](design/b3-implementation-contract.md)、[B3 验收](testing/B3-verification.md) |
| 同步相似检索 | `POST /api/search` 使用同一向量算法；成功、就绪、未失效、双版本匹配及可选渠道过滤后评分和 Top-K | [B4 检索与计数](design/b4-implementation-contract.md)、[B4 验收](testing/B4-verification.md) |
| 文档管理 | 文档列表、详情；主动失效及幂等处理；失效与任务终态、向量就绪独立 | [六接口契约](api/api-contract.md)、[正式交付闭环](testing/最终交付聚合结果.md) |
| 溯源与统计 | 保留渠道、提交/完成时间等元信息；仅最终命中原子增加 hitCount；GET 不计数 | [B4 事务规则](design/b4-implementation-contract.md)、[并发与一致性报告](testing/CONCURRENCY-REPORT.md) |

### 技术硬约束

| 原题要求 | 实现落点 | 可复核入口 |
| --- | --- | --- |
| Java17以上 / Spring / JPA或MyBatis | 本项目锁定Java17、Spring Boot与MyBatis | [构建依赖](../pom.xml)、[系统设计与技术选型](DESIGN.md) |
| H2文件持久化、DDL随项目、启动自动建表 | 文件库与幂等建表脚本，重复启动保留数据 | [schema.sql](../src/main/resources/schema.sql)、[初始化配置](../src/main/resources/application.yml)、[项目使用操作手册](USER-GUIDE.md) |
| 无队列中间件，Java实现队列 | ThreadPoolExecutor、ArrayBlockingQueue及数据库任务补偿 | [TaskDispatcher](../src/main/java/dev/twelvetimes/vector/task/TaskDispatcher.java)、[B2接收与调度](design/b2-implementation-contract.md) |

实现入口证明代码落点，验收记录证明已执行范围；两者都不能单独抹去当前稳定性失败。

## 版本、CI与发布范围

本索引于 2026-10-23 基于已公开提交 `46ebe7d9b29a616721a66a10c9ce65d89fc2e33a` 整理。这里只核读 Git、源码和既有报告、CI；本轮没有重新构建、启动应用、访问业务接口或登录服务器。下表区分公开源码、当前已记录的生产包和首发历史。生产事实来自公开发布核证，本轮没有独立探测当前运行状态；不能以较新的 main 代替生产包身份。

| 维度 | 可追溯事实与来源 |
| --- | --- |
| 本轮公开源码基线 | [46ebe7d](https://github.com/Qbsstg/text-vector-search/commit/46ebe7d9b29a616721a66a10c9ce65d89fc2e33a)，包含前端改版和后续质量、文档整合；是源码快照，不是线上版本探针 |
| 当前生产源码（据发布记录） | `2764eddf34418fdcc8ed666e36937673c7feadb8`，依据[测试与质量验证](testing/UI-RELEASE-verification.md) |
| 当前生产 JAR SHA-256 | `b8b67460ee6af26ec5ea7854377b63919c18c37760b11e3fbf49f7672be7f45f`；来源[发布CI39915764822](https://github.com/Qbsstg/text-vector-search/actions/runs/39915764822)，104 Unit + 126 IT + 52 Node = 282，Success |
| 后续模板与主线核证 | [a4be0aa / CI39921238979](https://github.com/Qbsstg/text-vector-search/actions/runs/39921238979) Success；后续缓存代理模板更新未替换上述固定应用JAR；文档HEAD不冒充运行版本 |
| 本次发布状态 | 已部署供用户最后测试；有限HTTPS、Chrome、重启持久化和独立恢复通过；本地25分钟提交503的稳定性失败仍开放，完整35分钟及其后本地重启段未执行。见[发布跟踪](tasks/UI-RELEASE-20261023.md) |
| 首发历史生产包 | `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f` / `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`；[原产物CI39838054946](https://github.com/Qbsstg/text-vector-search/actions/runs/39838054946)，仍是后端字节冻结和代码回退基线 |
| 首发完整工程回归 | [661ed08 / CI39891645582](https://github.com/Qbsstg/text-vector-search/actions/runs/39891645582)：104 Unit + 126 IT + 43 Node = 273；其[最终聚合结论](testing/最终交付聚合结果.md)只对应当时已批准范围 |

首发记录中的 `661ed08`、273项和d016固定包描述的是首发FINAL-RELEASE，不能继续标为当前前端生产包。新版使用其自己的源码、JAR摘要与282项CI；[测试与质量验证](testing/UI-RELEASE-verification.md)记录新旧包差异仅在static，后端字节、算法/API/DDL与资源参数保持。既有首发PASS与新版稳定性FAILED分别保留，不将用户最后测试视为已经验收完成。

## 测试证据与未完成边界

| 统计维度 | 已有真实结论 | 边界与来源 |
| --- | --- | --- |
| 首发自动化 | 273 项通过 | 固定首发交付范围；[最终聚合结果](testing/最终交付聚合结果.md)与对应 CI；不把旧253、后续复跑继续累加 |
| 原70条主验收 | 按批准的最新判据，70 PASS | 属于需求映射，不是另外70个独立自动化测试；[历史矩阵](testing/final-release-local-cases-matrix.md)保留原阶段结果，当前结论看最终聚合 |
| REC-07 | 修订判据 REC-07-R1 独立实测通过；原预期 FAIL 保留 | [人工裁决](design/REC-07-验收判据裁决-20261023.md)、[修订实测](testing/REC-07-修订验收.md)；不把历史 FAIL 改成 PASS |
| 首发真实入口 | 64/64 HTTPS 请求符合预期，正式 Chrome 闭环、重启持久化、冷备及独立恢复有记录 | [正式交付聚合](testing/最终交付聚合结果.md)；请求次数不等于测试数量，Chrome 网络中段不连续的限制保留 |
| 原 UI 验收 | 28 PASS；F05 为 NOT_RUN / APPROVED_EXCEPTION | [原 Chrome 报告](testing/UI-browser-verification.md)、[有限例外](testing/F05-例外申请.md)；例外不是测试通过 |
| 首发包的后续线上专项 | 39个功能声明场景通过；631次有界压力请求，含57次保护性429、无5xx和未知结果 | [线上功能](testing/ONLINE-FUNCTIONAL-REPORT.md)、[性能基线](testing/PERFORMANCE-BASELINE.md)对应d016包，不能作为当前2764新版复测；固定 N=9 / K=1 / 长查询短窗混合负载的最高观测成功QPS为7.852，不是系统最大容量 |
| 当前前端改版 | 282项自动化通过；另有新版64次HTTPS请求和真实Chrome、重启/独立恢复实测；本地稳定性FAILED | [测试与质量验证](testing/UI-RELEASE-verification.md)、[本地改版历史验收](testing/UI-REDESIGN-LOCAL-verification.md)；新版与首发各自的64请求不得混算，不把HTTP/Chrome加入282，短样本不覆盖稳定性失败 |

后续线上专项并未重新在线覆盖原70条全部联合断言；其报告单独记录7 ONLINE_PASS、1 LOCAL_PASS、29 PARTIAL、33 NOT_RUN。该分层不推翻首发70条已批准结果，也不把历史结果冒充本轮线上通过。

### 历史与未执行内容

历史报告开头的 BLOCKED、REC-07 FAIL、271项和“尚未部署”，记录的是当时事实；阅读最新结论后再按需要追溯，不以旧阶段快照判断当前项目未完成。

- F05 的真实 FAILED Chrome 端到端分支仍未执行，只有已批准的有限风险接受；不制造生产失败来补齐表格。
- 首发 D9 未触发，没有上一已验版本的旧版回滚演练；代码回退方案不等于实际回滚通过。该结论只描述首发，不替后续版本回退作结论。
- 正式环境破坏性故障、整机重启验证、无限长期负载/SLA、带人工标签的语义准确率未验证；既有隔离强杀/故障测试不能改写为生产故障注入。
- 后续专项的 Worker=2/4、N=100/1000/5000、队列极限对照仍未执行；Hikari精确占用、精确队列长度与GC单次暂停未测量。
- 旧临时预览503与本次新包独立本地稳定性503均有真实记录；最新门禁在1500.13秒失败，完整35分钟及其后本地重启未完成，原因未定位。线上有限64请求和服务器重启不是对该故障的修复证明。本轮不修改后端或操作服务器排查。

## AI、Git和资料公开范围

[AI关键对话与人工决策](AI-COLLABORATION.md)直接展示本次核对的用户指令、Codex原始回复及人工决定，不把AI技术说明或执行摘要统一称作聊天原文。更早的[C0笔记](ai-notes/001-c0-kickoff.md)和[关键决策摘要](ai-notes/003-关键对话与人工决策.md)保留历史定位。

[历史提交中文说明](git/提交历史中文说明.md)解释原英文提交，不改原SHA。原题PDF、完整私人聊天、受限原始请求/截图、证书、认证、数据库和完整实机操作材料不因导航需要而公开；引用摘要和原件SHA不表示全部原件可匿名下载。

部署事实看[测试与质量验证](testing/UI-RELEASE-verification.md)，运行/恢复约束看[运行与资源契约](design/runtime-contract.md)，[公开部署模板目录](../deploy/)只提供模板和脚本，不把部署准备快照当当前实机状态。

## 实际操作从哪里开始

按[项目使用操作手册](USER-GUIDE.md)克隆、构建并启动，完成“提交 → 任务查询 → 列表/详情 → 检索计数 → 失效 → 新检索排除”闭环。它明确了Java17、H2持久目录和启动自动建表，无须另装H2服务。

演示站点使用HTTPS和入口认证，凭据安全另行交付。提交会占用持久容量，失效不释放，搜索真实增加计数；采用少量合成文本，不在正式站点进行故障注入或批量压测。本轮只整理文档，没有重新构建、执行示例、探测当前服务或排查503。
