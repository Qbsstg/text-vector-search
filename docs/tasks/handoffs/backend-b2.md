# B2 后端实施交接

日期：2026-10-22。角色：后端工程师，gpt-6.1-sol / high。

## 任务、基线和所有权

- 任务：B2-01/02严格校验及两表接收、B2-03五个管理API、B2-04有界调度与条件领取。
- 工作树：`/Users/qbss/code/12times/.worktrees/backend`；分支：`agent/backend`。
- 起点：`f3850bbecb0ed373a109ff5b80b807c462f12da4`。
- 已读：最新AGENTS、B2实施契约、B2启动与里程碑、API/运行契约、DDL/DTO；只读检查主树用户测试资料入口、测试方案与请求夹具，没有复制整包或把旧待冻结项覆盖当前契约。
- 所有新增注释/JavaDoc及新提交说明使用简体中文。未修改冻结B1文件、公共DTO、DDL、pom、application、VectorProperties或BootstrapHealthController，没有实施B3/B4，没有外部发布。

## 三阶段真实提交

| 顺序 | 提交 | 内容 | 提交前对应测试 |
| --- | --- | --- | --- |
| 1 | `1bfa33f4258366015ea621ea895ae790de49ca59` | 严格轻量校验、MyBatis持久层、接收锁内两表提交、容量/重复及门禁 | 11通过，0失败/错误/跳过 |
| 2 | `f834fa4bfa8954306568da32ffc7ac80e7d47636` | 五个管理API、统一错误、真实字节上限和同源HTTP边界 | 15通过，0失败/错误/跳过 |
| 3 | `4523942f44009d71b243ca31693f5a7ef48b0fdc` | 有界调度、持久扫描、令牌条件领取、可选B3处理器端口 | 全部24个B2单测通过，0失败/错误/跳过 |

本文件随后独立提交，文档提交SHA通过交接消息回报，不在文件内伪造自引用。所有提交使用显式文件路径，提交前检查暂存差异；未rebase/amend历史。

## 文件与实现职责

| 范围 | 文件/关键责任 |
| --- | --- |
| `api/DocumentController.java`、`TaskController.java` | POST接收、GET分页/详情/任务、PUT幂等失效；不新增搜索占位成功接口 |
| `api/error/**` | BusinessException安全错误与字段错误、ApiExceptionHandler统一响应；事务结果无法确认时暴露RESULT_UNKNOWN并关闭接收门禁 |
| `api/validation/**` | RequestValidator按JsonNode严格检查类型/ID/UTF-16/渠道/分页；轻量执行与B1一致的最终规范化长度检查，绝不调用embed；RequestJsonReader拒绝截断及多根JSON |
| `api/filter/ApiRequestFilter.java` | 实际最多读取maxBytes+1，覆盖chunked；413、POST严格application/json、PUT无body、存在Origin时同源校验；不信任任意转发头、不开放CORS |
| `persistence/**` | KnowledgeMapper注解SQL和只读DocumentRow快照；全部参数绑定，无基础MyBatis配置变更、无整行旧实体覆盖 |
| `document/**` | DocumentAdmissionService物理提交接收；DocumentQueryService查询/失效；DocumentViews显式映射就绪/失效与十进制字符串hitCount |
| `task/**` | TaskQueryService四态查询；TaskReadiness旧RUNNING/未知提交门禁和诊断；TaskClaimService真实条件领取；TaskDispatcher有界扫描执行；TaskExecution/Handler为B3窄端口 |
| `config/TaskDispatchConfiguration.java` | 可选处理器装配，不注册生产空处理器 |
| `unit/**` | 新增B2TestSupport、RequestValidatorTest、DocumentAdmissionServiceTest、DocumentManagementApiTest、TaskDispatcherTest；不改既有B1/验收文件 |

## 关键事务与调度审查点

1. 接收先轻量校验，再拒绝外层活动事务。单例锁内以REQUIRES_NEW TransactionTemplate执行重复/两种容量检查、两表插入和真实commit，回调完成不是返回202的依据；只有execute实际结束才返回。总文档包含失败/失效，QUEUED+RUNNING单独计数。
2. 第二表失败通过真实H2回滚验证，不能残留孤立文档。提交后抛异常按本次生成的UUID在独立新事务核对；确认持久任务才承认接收。查不到或查询失败均不盲重插，返回RESULT_UNKNOWN并关闭门禁。
3. 失效只更新invalidated/invalidated_at，WHERE invalidated=FALSE保证首次时间不覆盖；列表、详情、任务GET都不计数。vectorReady只看SUCCEEDED与恰好1024字节向量，允许同时已失效；hitCount显式Long.toString。
4. 领取UPDATE严格WHERE status=QUEUED，原子写新UUID token、attempt_count+1、started_at。只有一行才拥有执行权，commit完成后才返回TaskExecution。提交异常必须独立核对RUNNING与本次token，不能只见RUNNING就计算；确认失败关闭门禁且保留原状态。
5. ThreadPoolExecutor固定Worker、ArrayBlockingQueue、AbortPolicy；Runnable仅携taskId。inFlight只去重派发，不代替数据库领取。队列满移除本地标记并保留QUEUED，未来扫描补派；扫描异常不杀死周期任务。
6. TaskExecutionHandler.handle(TaskExecution)在领取事务与连接释放后调用。未接处理器的生产实例不会扫描/领取，已接收任务保持QUEUED。测试处理器是受控替身，不等于B3终态能力。
7. 启动发现旧RUNNING只关接收门禁，不重置、不声称恢复完成。门禁理由有本地诊断访问与中文日志，HTTP接收返回SERVICE_NOT_READY。处理器越界异常保留RUNNING并关门禁，待B3诊断；明确未领取的暂时异常保留QUEUED可再扫。

## 给QA/B3的窄接口与控制点

- `DocumentAdmissionService.submit(SubmitDocumentRequest)`；构造器可传真实Mapper和装饰的PlatformTransactionManager，事务回调无测试外层TX。
- `DocumentQueryService.detail(String)`、`list(int,int,String,Boolean)`、`invalidate(String)`；`TaskQueryService.get(String)`。
- `TaskClaimService(KnowledgeMapper,TaskReadiness,PlatformTransactionManager).claim(String)`返回Optional<TaskExecution>。
- `TaskExecution`字段：taskId、documentId、runToken、embeddingVersion、int attemptCount、text、channel。`TaskExecutionHandler.handle(TaskExecution)`可抛Exception。
- `TaskDispatcher(KnowledgeMapper,TaskClaimService,TaskReadiness,VectorProperties,TaskExecutionHandler)`；最后参数可null。直接构造后不调用start，只手动scanOnce即可精确控制；scanOnce返回本次入队数，queuedCount/inFlightCount提供诊断。Spring生命周期才开启周期扫描。
- Mapper第二次插入异常通过JDK Proxy包装真实Mapper注入；物理commit前latch及真实commit后抛错通过事务管理器装饰器注入。没有生产调试HTTP或测试开关。

## 真实运行命令与结果

每次Maven使用同一显式Java17环境和本机临时缓存：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 <goals>
```

- 阶段1：`-Dtest=RequestValidatorTest,DocumentAdmissionServiceTest test`最终11通过。首次Mockito动态自附加受本机限制，改为JDK代理装饰真实Mapper后重跑通过，不引入依赖、不削弱断言。
- 阶段2：`-Dtest=DocumentManagementApiTest,RequestValidatorTest,DocumentAdmissionServiceTest test`最终15通过。首次编译发现Jackson3无getFactory，改为其createParser后通过；未修改依赖。
- 阶段3：先运行调度/接收/校验20通过，门禁日志及显式import整理后以`-Dtest=TaskDispatcherTest,DocumentAdmissionServiceTest,RequestValidatorTest,DocumentManagementApiTest test`回归24通过，再提交代码。
- 最终：在代码SHA `4523942f44009d71b243ca31693f5a7ef48b0fdc` 执行`clean verify`，2026-10-22 23:35:58 +08:00完成，**BUILD SUCCESS**。Surefire **53通过**（既有29+B2新增24），Failsafe **22通过**（C0 6+B1 16），均0失败/错误/跳过。既有B1黄金与独立进程验收保持通过。
- HTTP测试获准绑定127.0.0.1随机端口，全部数据库使用用例私有绝对临时文件目录。临时settings/cache不是部署要求；日志和报告不提交。
- 完整日志：`/private/tmp/12times-backend-b2-verify.log`；阶段日志：`/private/tmp/12times-b2-stage1.log`、stage2.log、stage3.log同前缀。XML报告在本工作树target/surefire-reports和target/failsafe-reports。

新增24个单测按类计数：RequestValidator 5、DocumentAdmissionService 6、DocumentManagementApi 4、TaskDispatcher 9。覆盖真实两表回滚、锁内提交最后名额、同ID并发、外层事务拒绝、提交后确认、chunked413、严格类型/空白/长度/分页、Origin/媒体类型、管理闭环、四态/大整数映射、失效幂等、唯一领取/token/attempt、饱和补派、事务外处理、扫描故障续扫、未知领取/接收门禁、旧RUNNING正常重启保留、文档/积压独立容量。并发等待均有界，未用sleep证明顺序。

## 已知边界和未验证项

- 小批扫描可能暂时读到一批已在途QUEUED，从而不能立即填满更大的内存队列；持久任务不会丢失，属后续吞吐待测，未临时扩大优化范围。
- close仅停止本组件扫描并有界等候，不强杀、不无差别中断H2；完整业务关闭、超时组合、处理器终态、失败回写、旧RUNNING恢复及在线恢复诊断由B3完成，均未验收。
- B4查询向量/候选/Top-K/命中统计事务未实现；B2管理GET不计数不等于B4已通过。
- 主控健康仍UP/C0/businessReady=false，不可据此宣称整套业务就绪。生产无B3 handler时向量化不会自动成功，前端需据真实QUEUED展示。
- 此交接只报告后端本树结果。独立QA-B2、主控合入后verify、浏览器、真实业务发布、性能与相关性均未在本交接中记为通过。
- 无远程仓库/服务器发布、无持久数据恢复或删除。完成后等待主控审查和下一工作包。
