# B4 后端检索实施交接

日期：2026-10-23。角色：后端工程师，gpt-6.1-sol / high。

## 范围与真实提交

- 工作树：`/Users/qbss/code/12times/.worktrees/backend`；分支：`agent/backend`；起点`3f5764e43674ef5e8c3e5288f3967f5da4ad5e5c`。
- 已读AGENTS、README、API/运行及B4契约，先向主控/QA同步公开构造器与窄控制接口，获批后立即实现B4-01/02。
- 不修改B1算法/codec/余弦/Top-K文件或版本，不改公共DTO、DDL、依赖、运行参数、B3状态机；不修改前端或QA验收文件，不访问服务器、不部署。
- 新注释和提交说明全部简体中文，显式imports；没有重写已提交历史。

| 阶段 | 提交 | 提交前测试 |
| --- | --- | --- |
| 严格搜索输入、有界候选快照 | `c106e18cbe453d72e4eef48ef40e80ffaf0f48ab` | 10项unit通过：Validator 6、CandidateReader 4 |
| 同步搜索/API、许可与最终计数 | `59f3c76cb2d5a231ee57191b81bffe3e9f7acb8d` | 26项对应unit通过：Validator 6、CandidateReader 4、HitCount 7、SearchService 6、Controller 3 |

两次提交前均0失败/错误/跳过并检查暂存diff；文档随后独立提交，SHA通过交接消息回报，不倒填为已测源码。

## 文件与关键行为

- `api/SearchController.java`：真实POST `/api/search`，复用RequestJsonReader与既有实际字节/Origin门禁；返回冻结SearchResponse，没有模拟结果或额外字段。
- `api/validation/RequestValidator.java`：只新增search(JsonNode)/search(SearchRequest)。text沿用轻量UTF-16/双长度检查；topK严格JSON整数1—配置上限，拒绝1.0/字符串/null/溢出；渠道省略/null为全部，显式空白拒绝。
- `persistence/SearchMapper.java`：单次候选SQL先过滤任务SUCCEEDED、文档未失效、向量存在且1024字节、任务与文档双版本、可选渠道。只选ID/渠道/向量和SUBSTRING原文前400，不装载全部CLOB。
- `search/SearchCandidate.java`：本次候选快照，向量数组防御复制；SearchCandidateSource为已释放读取事务/连接后的窄来源接口。
- `search/SearchCandidateReader.java`：REQUIRES_NEW只读短事务、REPEATABLE_READ，使用B3共享关闭许可；先查全库文档量，再以maxDocuments+1作异常越限检测。超过容量明确503 DATABASE_UNAVAILABLE，不能把截断数据当全库Top-K。
- `search/SearchService.java`：入口拒绝活动外层事务，Semaphore.tryAcquire忙429 SEARCH_BUSY；许可覆盖真实查询向量化、候选读取、解码/余弦/Top-K与计数真实提交，finally释放。CPU不持事务/连接/关闭许可，候选与计数短阶段分别重新检查就绪。
- 评分只调用冻结TextEmbedder/VectorCodec/CosineSimilarity/TopKSelector，不全排序替代、不加阈值。完整候选一次性Iterable逐项评分，负分/零分保留，响应排序保持完整Top-K；坏NaN等候选失败而非伪装无命中，计数0。
- 有界原文前缀足够容纳200个code points；最终snippet使用offsetByCodePoints截断，保留中文/emoji边界。候选内存受文档上限约束，不新增共享缓存。
- `search/SearchHitCounter.java`及`SearchHitCountService.java`：最终ID复制后按ASCII排序，拒绝重复/null；单独REQUIRES_NEW短事务逐条SQL hit_count=hit_count+1，每条必须1行。响应仍保持Top-K顺序，计数只更新hit_count，不重筛失效/渠道，不覆盖快照后失效字段。
- 第二项失败、行数异常、BIGINT溢出由真实事务整体回滚。明确回滚返回503 DATABASE_UNAVAILABLE，本实现不自动重试。提交异常一律RESULT_UNKNOWN+DEGRADED，不重放、不查询累计数猜测当前请求结果；真实commit后网络断开仍已计数。
- 空命中不调用计数端口、不制造写事务；搜索/计数/读取入口均拒绝外层事务，防止独立事务返回后恢复外层连接而把CPU包在长事务中。

## QA窄控制接口

- `SearchCandidateSource.load(String version,String channel)`返回List<SearchCandidate>；包裹真实load返回后latch暂停即可控制快照/失效，此时连接与读取事务已释放。
- `SearchCandidateReader(SearchMapper,TaskReadiness,VectorProperties,PlatformTransactionManager)`；Mapper方法countDocuments()/candidates(version,channel,limit)/incrementHit(id)。所有SQL仍为真实H2/绑定参数。
- `SearchHitCounter.increment(List<String>)`；`SearchHitCountService(SearchMapper,TaskReadiness,PlatformTransactionManager)`。装饰真实Mapper控制第二项异常/行数0，装饰真实TX控制物理commit前暂停、commit后抛错或未提交但结果未知反例。
- `SearchService(RequestValidator,TextEmbedder,SearchCandidateSource,SearchHitCounter,TaskReadiness,VectorProperties).search(SearchRequest)`。仅测试构造可提高searchPermits，生产参数不变；包裹真实TextEmbedder控制CPU阻塞。没有公网调试端点或生产故障开关。

## 真实验证命令与证据

本机Maven环境均显式Java17及临时缓存，不作为服务器运行要求：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 <goals>
```

- 第一段：`-Dtest=SearchCandidateReaderTest,RequestValidatorTest test`，10通过；显式import整理后重跑仍10通过，01:32:45 +08:00结束，随后提交。
- 第二段：先跑计数/服务/候选/校验23通过；新增真实HTTP闭环后执行`-Dtest=SearchHitCountServiceTest,SearchServiceTest,SearchControllerTest,SearchCandidateReaderTest,RequestValidatorTest test`，26通过，01:36:48 +08:00结束，随后提交。
- 新增B4单测净增21：候选4、计数7、搜索6、HTTP3、既有Validator新增1。26目标测试含原Validator 5，不重复算新增数量。
- 真实覆盖：生产提交→真实B1成功→搜索→详情计数；严格JSON/UTF-16/渠道/分页上限/字节/Origin；全部资格/双版本过滤；有界前缀；空/负/同分/K>N/200cp；仅最终计数；12独立事务热点准确累计；快照后失效仍返回且计数，新搜索排除；第二项/0行/溢出回滚；提交已成与未成两种未知反例不重放；许可持到物理commit且忙429；坏NaN失败count0后许可可再用；CPU没有事务连接，context/dataSource关闭后返回不能SQL。
- 数学向量仅用于负分/故障边界，正常闭环用生产真实B1；没有把样本分数当语义准确率。并发控制使用latch和有界状态等待，没有用sleep猜测时序。
- 阶段日志：`/private/tmp/12times-b4-candidates.log`、`/private/tmp/12times-b4-search.log`；完整回归日志`/private/tmp/12times-backend-b4-verify.log`；Node日志`/private/tmp/12times-backend-b4-node.log`。测试使用独立临时绝对H2和127.0.0.1随机端口；报告/运行库/日志不提交。
- 最终实测源码 **`59f3c76cb2d5a231ee57191b81bffe3e9f7acb8d`**，上述环境执行`clean verify`于2026-10-23 **01:38:01 +08:00**退出0，BUILD SUCCESS：**95 unit、74 IT，0失败/错误/跳过**。unit是原74+B4净增21，IT保留既有C0/B1/B2/B3/代理全部74项（包括四点真实强杀、正常SIGTERM和超时关闭）；当时尚未合入的QA-B4不能提前计入。
- 同一代码树执行`node --test tests/frontend/*.test.mjs`：**25通过，0失败/取消/跳过**。本角色本次合计194项，不替代后续QA/FE集成树复测；不能把随后交接文档SHA倒填为已测源码。

## 未验证边界与下一步

QA独立B4并发、实际HTTP断连/响应失败、关闭专用故障验收尚未包含在本角色阶段测试；主控合入QA/FE后须再全量回归。真实浏览器按用户最新指令稍后处理，仍保留待办；Node/HTTP不替代UI验收。未测相关性、吞吐、p95或服务器混合负载，不承诺性能。B3状态机与关闭策略不改，新增搜索只复用共享短阶段屏障；底层SQL永久卡住仍不宣称固定期限排空。健康/businessReady、文档与正式发布由主控统一验收和授权，不因本次代码提交自动上线。
