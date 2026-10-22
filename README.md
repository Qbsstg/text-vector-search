# 文本向量化与检索

Java17 + Spring Boot 4.0.3 + MyBatis Starter 4.0.1 + H2 2.4.240文件数据库的单服务项目。用户已原则批准B1—B4后端核心，六业务接口及FE1—FE3已实现，当前进行最终健康、完整UI验收及DEP3发布准备。B4阶段本地与Linux CI的229项回归证据保留；最终健康由真实运行事实推导，真实Chrome已核证28项通过，F05保持NOT_RUN并申请用户例外批准；正式业务部署仍待独立授权。

公开源码仓库：[Qbsstg/text-vector-search](https://github.com/Qbsstg/text-vector-search)。用户已确认并授权公开，见[公开复核](docs/git/公开授权与安全复核-20261023.md)。GitHub Actions使用固定完整SHA的官方Actions，在Temurin17.0.15下运行单测及验收，成功后保存固定源码、JAR校验和与manifest的待批准候选，不自动部署。

## B2与FE1

已实现文档提交、列表、详情、任务查询、幂等失效五接口。接收锁覆盖两表物理提交，持久任务为事实来源；有界队列、周期扫描、token条件领取通过受控处理器验收。B2历史“无handler保持QUEUED、旧RUNNING拒接”断言保留在明确隔离的测试组成中。

B3生产处理器在事务外调用冻结B1，向量、完成时间和SUCCEEDED同事务提交；失败按稳定类别持久化。终态只允许匹配RUNNING及run_token的执行者写入，不覆盖失效或命中计数。启动恢复物理提交后才READY、接收与调度；关闭先拒接/停派，等待Worker，再排空短事务并关闭回写许可。超时CPU不能迟到写入已销毁的数据源。30秒是Worker等待预算，不能保证永久卡住的数据库事务也在此期限内结束。精确范围见[B3契约](docs/design/b3-implementation-contract.md)。

前端真实同源请求、详情纯文本、确认失效、有限任务轮询已实现。前端测试命令：`node --test tests/frontend/*.test.mjs`（Node22.16.0）；服务器无需Node。Work浏览器权限已恢复，已使用真实Chrome完成28/29项核证，F03隐藏/恢复和U12组合补测通过；F05真实FAILED展示保持NOT_RUN，例外待用户批准。两处Chrome实测缺陷已修复，详情见[独立UI核证](docs/testing/UI-browser-verification.md)，不能把Node/HTTP测试当作完整UI验收。

## B4检索与计数

`POST /api/search`复用冻结B1算法和Top-K，先以任务成功/向量就绪/未失效/同版本/渠道过滤，再遍历全部候选评分。仅最终命中按固定ID顺序同事务原子+1；快照后失效的在途结果仍可计数，后续新搜索排除。默认搜索许可1，忙429；提交未知503并降级，不重放增量，网络失败前后端均不自动重试。候选只读有界前缀与向量，CPU不持事务或数据库连接。

真实JAR/前端HTTP闭环、独立并发/回滚/真实RST/关闭排空均有记录，见[B4验收](docs/testing/B4-verification.md)。完整浏览器门禁、负载性能、语义效果和正式发布仍未通过或未验；最终`/health`报告B4阶段，只有恢复、数据库、调度、算法、搜索及六路由检查均通过才返回200/businessReady=true。此值表示当前应用运行就绪，UI和发布许可仍须独立验收，见[最终契约](docs/design/final-readiness-release-contract.md)。

## B1已实现

- `TextEmbedder` / `CharNgramTextEmbedder`：固定规范化、Unicode字符1+2 gram、带符号SHA-256、256维float及L2归一化；计算状态独立，同文按位稳定。
- `VectorCodec`：固定BIG_ENDIAN的1024字节，有限float原始位往返（含负零）。
- `CosineSimilarity`：支持未单位化向量、零范数、有限数校验与[-1,1]范围。
- `TopKSelector`：小顶堆精确选取，分数降序/同分ID升序，保留零负分；不修改候选，返回不可变列表。

精确算法和版本见[B1算法契约](docs/design/b1-algorithm-contract.md)，独立黄金参考见[验收计划](docs/testing/b1-test-plan.md)。当前算法版本为`char12-sha256-r32-nfkc-root-ws-v1`；轮数改变时版本随之改变。算法只模拟字面特征，不作语义准确率承诺。

## 本地构建

要求JDK17；构建会拒绝其他主版本，确保开发与评审环境一致。本机Java/Maven默认JVM曾不一致，执行前先核对：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # macOS；Linux指向实际JDK17
./mvnw -version
./mvnw -B clean verify
APP_DATA_DIR="$(pwd)/data" java -jar target/vector-search.jar
```

首次Wrapper和Maven构建需要下载官方依赖。访问`http://127.0.0.1:8080/`，健康`http://127.0.0.1:8080/health`。最终健康区分`taskState`、`taskReady`、`businessReady`及六项`checks`；非就绪返回503，探针只读且不调用向量化。运行就绪不代表真人UI或正式上线通过。

构建产物`target/vector-search.jar`；单测报告`target/surefire-reports/`，验收报告`target/failsafe-reports/`。`*Test`与`*IT`均由`verify`执行；业务测试尚未实现时不代表业务通过。

H2数据默认在启动工作目录的`data/knowledge.mv.db`，推荐显式`APP_DATA_DIR`为绝对路径。不要两个应用共享同一文件，不在活动数据库上直接复制备份。更多参数、部署/停止约束见[运行契约](docs/design/runtime-contract.md)。

## 文档与协作

- [协作规范](AGENTS.md)：角色所有权、Git纪律、验证与发布边界。
- [C0分析](docs/design/c0-analysis.md)：已评审方案的变化和实施关键路径。
- [API契约](docs/api/api-contract.md)：唯一业务协议依据。
- [任务空间](docs/tasks/README.md)：五角色、任务板和交接。
- [C0集成验收](docs/testing/C0-verification.md)：3项单测、6项验收及真实JAR/浏览器证据。
- [B1集成验收](docs/testing/B1-verification.md)：29项单测、22项验收及提交前测试记录。
- [B2集成验收](docs/testing/B2-verification.md)：53项单测、44项验收、25项前端测试及明确未验证范围。
- [B3集成验收](docs/testing/B3-verification.md)：真实生产终态、四点强杀恢复、正常/超时关闭及明确未验证范围。
- [B4启动拆解](docs/tasks/B4-启动与任务拆解.md)：责任人、依赖、12组验收矩阵、工时估计及人工门禁。
- [最终准备与审批](docs/tasks/最终交付准备与审批清单.md)：固定候选、真实健康、Chrome验收和DEP3门禁。
- [最终验证报告](docs/testing/FINAL-verification.md)：保留原252项与失败历史；修复候选d016d6e的Linux CI为104 unit+110 IT+39 Node=253通过，另记F03/U12补测后28项Chrome通过与F05待批准例外。
- [固定候选与Chrome入口](docs/tasks/最终候选与Chrome验收入口.md)：修复候选d016d6e的源码SHA、JAR摘要和真实Chrome结果，正式发布仍待单独批准。
- [Chrome手工验收](docs/tasks/handoffs/frontend-final-ui.md)：29项真实与受控浏览器步骤、专用临时库及证据模板；实际结果以独立UI核证为准。
- [F05例外申请](docs/testing/F05-例外申请.md)：已有终态/前端替身证据与真实FAILED浏览器缺口；状态不改PASS，待用户决定。
- [DEP3最终审批单](docs/tasks/DEP3-最终发布审批单.md)：固定版本、认证、服务、代理、启动策略和备份/回退的待批准操作。
- [DEP3发布计划](deploy/DEP3发布操作计划.md)：校验、全入口认证、持久单实例锁、冷备与首发失败处理，正式执行仍需单独批准。
- [九项阶段报告](docs/tasks/B2-阶段进展报告.md)：职责、依赖、工时、网络与用户人工项。
- [评审资料入口](docs/项目评审和实施资料/00_阅读说明.md)：保留原始材料，不把历史计划当运行结果。

原题PDF保留本地并忽略，不推送运行数据库、运行日志或密钥。仓库按用户最新授权保持公开，真实历史不改写。指定服务器已完成基础准备和有效证书接入，仅[HTTPS部署检查端点](https://text-vector-search.lemoncharger.com/deployment-check)提供非敏感固定文本，API/管理路径拒绝；业务服务仍inactive/disabled。见[SSL交接](docs/tasks/handoffs/server-ssl-prep.md)。未来业务代理须显式启用`proxy`配置，仅信任回环Nginx覆盖后的转发头，见[代理契约](docs/design/proxy-deployment-contract.md)。TLS检查就绪不等于正式业务发布获批。

