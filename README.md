# 文本向量化与检索

Java17 + Spring Boot 4.0.3 + MyBatis Starter 4.0.1 + H2 2.4.240文件数据库的单服务项目。已完成C0、B1及B2接收/管理/调度基础，FE1提供真实管理入口；B3已接入真实向量化、原子终态、启动恢复与关闭屏障，本地和Linux GitHub CI的173项完整回归均已通过。B4搜索与最终命中原子计数、FE2/FE3真实接口已实现，本地和Linux CI的229项回归均通过；完整浏览器仍待验，`/health`继续明确businessReady=false。

私有源码仓库：[Qbsstg/text-vector-search](https://github.com/Qbsstg/text-vector-search)。GitHub Actions使用固定完整SHA的官方Actions，在Temurin17.0.15下运行单测及验收，不自动部署。

## B2与FE1

已实现文档提交、列表、详情、任务查询、幂等失效五接口。接收锁覆盖两表物理提交，持久任务为事实来源；有界队列、周期扫描、token条件领取通过受控处理器验收。B2历史“无handler保持QUEUED、旧RUNNING拒接”断言保留在明确隔离的测试组成中。

B3生产处理器在事务外调用冻结B1，向量、完成时间和SUCCEEDED同事务提交；失败按稳定类别持久化。终态只允许匹配RUNNING及run_token的执行者写入，不覆盖失效或命中计数。启动恢复物理提交后才READY、接收与调度；关闭先拒接/停派，等待Worker，再排空短事务并关闭回写许可。超时CPU不能迟到写入已销毁的数据源。30秒是Worker等待预算，不能保证永久卡住的数据库事务也在此期限内结束。精确范围见[B3契约](docs/design/b3-implementation-contract.md)。

前端真实同源请求、详情纯文本、确认失效、有限任务轮询已实现。前端测试命令：`node --test tests/frontend/*.test.mjs`（Node22.16.0）；服务器无需Node。浏览器验证目前因Work本地浏览器禁用未执行，不能把Node/HTTP测试当作完整UI验收。

## B4检索与计数

`POST /api/search`复用冻结B1算法和Top-K，先以任务成功/向量就绪/未失效/同版本/渠道过滤，再遍历全部候选评分。仅最终命中按固定ID顺序同事务原子+1；快照后失效的在途结果仍可计数，后续新搜索排除。默认搜索许可1，忙429；提交未知503并降级，不重放增量，网络失败前后端均不自动重试。候选只读有界前缀与向量，CPU不持事务或数据库连接。

真实JAR/前端HTTP闭环、独立并发/回滚/真实RST/关闭排空均有记录，见[B4验收](docs/testing/B4-verification.md)。完整浏览器、负载性能、语义效果和正式发布未验；`/health`沿用B3任务就绪探针，businessReady仍false，不作为B4或正式发布验收标志。

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

首次Wrapper和Maven构建需要下载官方依赖。访问`http://127.0.0.1:8080/`，健康`http://127.0.0.1:8080/health`。B3健康区分`taskState`、`taskReady`与`businessReady:false`；任务就绪不代表B4或正式上线通过。

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
- [九项阶段报告](docs/tasks/B2-阶段进展报告.md)：职责、依赖、工时、网络与用户人工项。
- [评审资料入口](docs/项目评审和实施资料/00_阅读说明.md)：保留原始材料，不把历史计划当运行结果。

原题PDF保留本地并忽略，不推送运行数据库、运行日志或密钥。仓库保持私有，后续公开仍需明确指令。指定服务器已完成基础准备和有效证书接入，仅[HTTPS部署检查端点](https://text-vector-search.lemoncharger.com/deployment-check)提供非敏感固定文本，API/管理路径拒绝；业务服务仍inactive/disabled。见[SSL交接](docs/tasks/handoffs/server-ssl-prep.md)。未来业务代理须显式启用`proxy`配置，仅信任回环Nginx覆盖后的转发头，见[代理契约](docs/design/proxy-deployment-contract.md)。TLS检查就绪不等于正式业务发布获批。

