# 项目文档目录

本目录按用途整理系统设计、使用说明、AI协作记录、技术契约、测试报告和部署资料。首次阅读可从项目交付资料了解整体成果，也可以直接查看系统设计或使用说明。

**[查看设计思路](DESIGN.md) | [查看使用说明](USER-GUIDE.md) | [查看AI关键对话](AI-COLLABORATION.md)**

[返回项目首页](../README.md) · [项目交付资料](DELIVERY.md)

## A. 首次阅读

| 资料类别 | 文档名称与用途 |
| --- | --- |
| 交付总览 | [项目交付资料](DELIVERY.md)：汇总项目成果、技术资料、当前版本、测试与部署记录。 |
| 设计思路 | [系统设计与技术选型](DESIGN.md)：解释需求边界、候选方案、算法选择、线程与事务、恢复及工程取舍。 |
| 使用说明 | [项目使用说明](USER-GUIDE.md)：从克隆、自动建表到页面操作、六接口示例和正常重启。 |
| AI协作 | [AI协作与关键对话](AI-COLLABORATION.md)：真实用户指令、Codex原始回复节选与人工决定分别展示，注明可核验来源。 |

## B. 技术设计与实现

这里保留详细契约，不要求首次阅读者先读完它们。契约中的阶段基线、旧状态和待开发描述属于当时背景；当前实现概览看[系统设计与技术选型](DESIGN.md)，当前运行版本看[项目交付资料](DELIVERY.md)。

| 文档 | 能看到什么 |
| --- | --- |
| [B1算法契约](design/b1-algorithm-contract.md) | 精确的规范化、256维特征哈希、算法版本、codec字节序、余弦和Top-K顺序。 |
| [B2接收与调度](design/b2-implementation-contract.md) | 提交事务、容量检查、Java有界队列、数据库条件领取和补偿扫描。 |
| [B3状态机、事务和恢复](design/b3-implementation-contract.md) | RUNNING/run_token终态、失败分类、启动恢复与优雅关闭的正确性约束。 |
| [B4检索与计数](design/b4-implementation-contract.md) | 候选快照、搜索许可、事务外计算、原子计数、失效竞态与未知提交。 |
| [系统架构图](design/system-architecture.svg) | 两条计算路径、H2任务事实来源、入口保护与单实例生命周期的静态总览。 |
| [API契约](api/api-contract.md) | 六接口字段、空值/渠道/分页规则、错误码、字符串计数及健康语义。 |
| [业务就绪与发布契约](design/final-readiness-release-contract.md) | 运行就绪如何从组件事实推导，以及它与UI、认证和发布批准的区别。 |

## C. 测试与验收

| 文档 | 能看到什么 |
| --- | --- |
| [测试与质量验证](testing/FINAL-ACCEPTANCE-REPORT.md) | 当前固定版本的283项自动化、两平台35分钟、HTTPS/Chrome和恢复验证，包含分层范围、原FAIL与未验证项。 |
| [前端改版本地验收](testing/UI-REDESIGN-LOCAL-verification.md) | 新版页面在本地的操作、视口、竞态及自动化证据；不代替线上验收。 |
| [原Chrome验收](testing/UI-browser-verification.md) | 首发候选的真实Illegal invocation失败、修复、新候选28 PASS及原始浏览器证据边界。 |
| [并发与一致性报告](testing/CONCURRENCY-REPORT.md) | 默认Worker=1下的真实线程、计数、失效竞态与未执行对照；不把HTTP并发当Worker并行。 |
| [性能基线报告](testing/PERFORMANCE-BASELINE.md) | 首发d016包的有界混合QPS、尾延迟、资源图和统计口径；不是当前新版复测或SLA。 |
| [线上功能专项](testing/ONLINE-FUNCTIONAL-REPORT.md) | 首发包后续39场景与70需求编号的分层映射，保留首次脚本失败和网络采集限制。 |
| [首发交付聚合结果](testing/最终交付聚合结果.md) | 首发273项、70主用例及正式入口和恢复验证的历史记录。 |
| [F05有限例外](testing/F05-例外申请.md) | 真实FAILED浏览器分支为何仍是NOT_RUN / APPROVED_EXCEPTION，后端/前端替身能证明什么。 |
| [REC-07修订实测](testing/REC-07-修订验收.md) | 新判据独立执行结果，与原预期FAIL分开追溯。 |

各版本的验证范围与问题记录见[测试与质量验证](testing/FINAL-ACCEPTANCE-REPORT.md)；B1—B4报告、部署前271项、前端改版282项和旧BLOCKED均保留历史阶段定位。

## D. 部署与交付

| 文档 | 能看到什么 |
| --- | --- |
| [固定版本发布检查单](testing/RELEASE-FINAL-CHECKLIST.md) | 当前生产源码/JAR/CI、正常停机冷备、升级字段保持、独立恢复、末次账本及禁止自动降级的边界。 |
| [运行与资源契约](design/runtime-contract.md) | 单JVM/H2、容量和并发初值、恢复关闭顺序；末尾主机准备段落是部署前历史。 |
| [HTTPS代理契约](design/proxy-deployment-contract.md) | Origin与转发头识别的复现、可信回环边界和对应独立验证，不把历史准备态当当前入口状态。 |
| [公开部署模板目录](../deploy/) | 可审查脚本和配置模板；完整实机操作原件不在此公开，模板存在不等于操作已执行。 |
| [真实提交历史](https://github.com/Qbsstg/text-vector-search/commits/main/) | 实际迭代与修复时间线，已推送SHA不重写。 |
| [历史提交中文说明](git/提交历史中文说明.md) | 对历史英文Commit逐条提供中文说明，不改原提交。 |

正式演示需要授权入口凭据；访问方式及本地操作见[项目使用说明](USER-GUIDE.md)，本目录不提供密码、生产数据库或受限运维附件下载。

## E. AI协作与历史决策

| 文档 | 能看到什么 |
| --- | --- |
| [AI协作与关键对话](AI-COLLABORATION.md) | 首次阅读的AI主入口，直接包含已核原文摘录，不必先翻阶段笔记或完整会话。 |
| [REC-07人工裁决](design/REC-07-验收判据裁决-20261023.md) | 用户怎样处理旧用例与冻结契约冲突，为什么保留旧FAIL并另建新判据。 |
| [C0关键对话与决策](ai-notes/001-c0-kickoff.md) | 初始化时的真实用户要求、角色分工和明确标注的助手执行摘要。 |
| [历史关键决策摘要](ai-notes/003-关键对话与人工决策.md) | 上一轮已审核的少量摘录和工程摘要；不称作完整Codex逐字记录。 |
| [前期评审资料阅读说明](项目评审和实施资料/00_阅读说明.md) | 当时方案、用户新增范围和资料优先级；研究建议和实验不冒充最终实现或新验收。 |

原始需求附件、完整私人聊天、被限制公开的请求/截图、认证材料和数据库继续保持原有受限状态。所有新导航只指向实际公开文件，未为链接可点击而上传原件。
