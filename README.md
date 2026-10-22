# 文本向量化与检索

Java17 + Spring Boot 4.0.3 + MyBatis Starter 4.0.1 + H2 2.4.240文件数据库的单服务项目。已完成C0初始化与B1算法基础；六业务API、持久任务调度和完整演示页面将在后续阶段实现。现有页面及`/health`仍明确表示业务未就绪。

私有源码仓库：[Qbsstg/text-vector-search](https://github.com/Qbsstg/text-vector-search)。GitHub Actions使用固定完整SHA的官方Actions，在Temurin17.0.15下运行单测及验收，不自动部署。

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
- [评审资料入口](docs/项目评审和实施资料/00_阅读说明.md)：保留原始材料，不把历史计划当运行结果。

原题PDF保留本地并忽略，不推送运行数据库、日志或密钥。用户已授权创建私有仓库并推送，后续公开仍需明确指令。指定服务器已完成基础账号/目录/待启用unit准备，应用尚未发布启动；实机事实与边界见[DEP2交接](docs/tasks/handoffs/server-dep2.md)。

