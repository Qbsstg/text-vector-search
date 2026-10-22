# 文本向量化与检索

Java17 + Spring Boot 4.0.3 + MyBatis Starter 4.0.1 + H2 2.4.240文件数据库的单服务项目。已完成C0、B1及B2接收/管理/调度基础，FE1提供真实管理入口；B3终态与恢复、B4搜索仍未实现。生产未注册B3处理器，因此任务保持QUEUED，页面搜索禁用，`/health`继续明确businessReady=false。

私有源码仓库：[Qbsstg/text-vector-search](https://github.com/Qbsstg/text-vector-search)。GitHub Actions使用固定完整SHA的官方Actions，在Temurin17.0.15下运行单测及验收，不自动部署。

## B2与FE1

已实现文档提交、列表、详情、任务查询、幂等失效五接口。接收锁覆盖两表物理提交，持久任务为事实来源；有界队列、周期扫描、token条件领取通过受控处理器验收，不以空处理器伪造任务完成。B3前生产不领取，旧RUNNING拒绝新接收且不重置。

前端真实同源请求、详情纯文本、确认失效、有限任务轮询已实现。前端测试命令：`node --test tests/frontend/*.test.mjs`（Node22.16.0）；服务器无需Node。浏览器验证目前因Work本地浏览器禁用未执行，不能把Node/HTTP测试当作完整UI验收。

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

首次Wrapper和Maven构建需要下载官方依赖。访问`http://127.0.0.1:8080/`，基础健康`http://127.0.0.1:8080/health`。C0健康中的`businessReady:false`表示尚未实现业务就绪，不能作为最终上线验收。

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
- [九项阶段报告](docs/tasks/B2-阶段进展报告.md)：职责、依赖、工时、网络与用户人工项。
- [评审资料入口](docs/项目评审和实施资料/00_阅读说明.md)：保留原始材料，不把历史计划当运行结果。

原题PDF保留本地并忽略，不推送运行数据库、运行日志或密钥。用户已授权创建私有仓库并推送，后续公开仍需明确指令。指定服务器已完成基础准备及证书前Nginx检查站点，应用尚未发布启动。仅[部署检查端点](http://text.vector.search.lemoncharger.com/deployment-check)提供非敏感固定文本，API/管理路径拒绝；HTTPS尚未就绪。见[Nginx交接](docs/tasks/handoffs/server-nginx.md)。

