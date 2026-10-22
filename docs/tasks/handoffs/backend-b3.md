# B3 后端实施交接

日期：2026-10-23。角色：后端工程师，gpt-6.1-sol / high。

## 基线、范围与真实提交

- 工作树：`/Users/qbss/code/12times/.worktrees/backend`；分支：`agent/backend`；起点`e8d45a05f14ed7abc60ff9a5bd4698fca841786a`。
- 先阅读最新AGENTS及B3契约，再向主控/QA同步终态、恢复、readiness、关闭及测试组成方案。主控额外批准提交/失效服务最小共享写许可接入；健康、Origin过滤器、基础配置继续由主控持有。
- 不改B1算法/版本、DDL、公共DTO、依赖、运行参数，不实施B4，不访问服务器或发布。
- 所有新增及维护注释、提交说明为简体中文；没有重写已存在历史。

| 阶段 | 提交 | 提交前测试 |
| --- | --- | --- |
| 终态事务/完整向量确认 | `00c5af5e472d656f97111d83de2532ea0dd426a2` | TaskTerminalServiceTest 9通过，0失败/错误/跳过 |
| 生产真实处理器/恢复/门禁及B2组成 | `90bcdf4d3faa18e17afc5ff0e4689ff48cbe1722` | 对应33个unit通过（终态9、恢复5、原B2相关19） |
| 优雅停止/CPU超时回写屏障 | `bb6e5eb96be7a3f0a3a38570c9aa71fd1e633d68` | 对应26个unit通过（终态9、恢复5、生命周期3、原调度9） |

## 组件、公开接口与字段责任

- `TaskTerminalMapper`/`TerminalSnapshot`：成功先条件更新本次RUNNING+run_token+版本任务，再只写文档vector_bytes/embedding_version/vector_completed_at；同一独立物理事务提交。失败只条件更新本次任务FAILED/结束时间/安全错误，不改原文、失效、计数。
- `TaskTerminalService(TaskTerminalMapper,TaskReadiness,TaskWriteBarrier,PlatformTransactionManager)`：`succeed(TaskExecution,float[])`、`fail(TaskExecution,TaskFailure)`返回内部枚举TerminalResult：APPLIED、ALREADY_APPLIED、STALE、WRITE_CLOSED。同令牌同终态重放幂等，旧令牌成功/失败均拒写；向量预先编码为新字节数组，避免调用方后续修改影响确认。
- 成功确认必须匹配文档ID、本次令牌、任务/文档版本、SUCCEEDED、结束/向量完成时间一致、无错误以及全部1024字节；同长度不等于同向量。失败确认匹配本次令牌、版本、FAILED、结束时间及固定错误码/消息。
- `ProductionTaskExecutionHandler`：事务外调用唯一TextEmbedder真实B1。VERSION_MISMATCH不计算；非法输入INPUT_INVALID；其他计算RuntimeException为COMPUTATION_FAILED。数据库终态异常不被捕获改写成计算FAILED，而是向调度传播并降级。
- `TaskFailure`稳定分类码和固定中文消息，不存异常原文、SQL、用户文本或文件路径；码≤64、消息≤512。
- `TaskRecoveryMapper`/`RecoveryTask`及`TaskRecoveryService.recover()`：启动阶段同事务把旧RUNNING恢复QUEUED，清token/开始/结束时间/错误，保留attempt_count、版本和提交时间，完全不更新文档。未知提交独立检查预恢复任务元信息及清理结果后才READY，不再次盲目恢复。
- `TaskReadiness`：初始RECOVERING；确认恢复后READY；未知提交或不可可靠完成进入DEGRADED；关闭STOPPING→STOPPED，不能重新READY。getter为state()/accepting()/reason()/writeBarrier()；`enterWrite()`在服务层保护接收、失效、领取完整短事务及未知结果确认。
- `ReadinessFilter`由B3配置注册：RECOVERING/STOPPING/STOPPED实际拒绝全部`/api/**`；DEGRADED允许GET诊断、拒绝写操作。健康路径不经过此过滤器，健康展示由主控维护，任务READY仍不代表B4/整套业务完成。
- `B3TaskConfiguration`集中注册真实处理器、终态、启动恢复、过滤器；没有全局关闭生产handler的属性或公网测试端点。

## 事务失败与生命周期关键审查点

1. 所有终态事务使用REQUIRES_NEW，显式拒绝未知外层活动事务。任一步受影响行数不对必须回滚；陈旧令牌返回STALE，不把新尝试改FAILED。
2. 仅回调失败且真实回滚完成的暂时数据库故障允许最多三次操作尝试，退避50/100ms。提交未知先独立事务最多三次确认，不盲目执行写入；确认失败DEGRADED、拒接/停派、保留持久现场。
3. 恢复在SmartInitializingSingleton执行，完成后SmartLifecycle才启动扫描。TaskReadiness初始化不能先READY再查库；恢复失败不READY。H2文件模式维持单进程锁，不启用AUTO_SERVER或在线超时抢占。
4. 同一个readiness.writeBarrier被生产终态、恢复、接收、失效、领取共享。公平读写锁许可只跨短SQL/物理提交/确认查询，CPU不持许可。
5. 停止顺序为STOPPING拒接→停扫描/派发→有界等待Worker→关闭新写许可并排空已进入的短事务→STOPPED→Spring销毁数据源。CPU超时不强制中断H2；迟到CPU调用终态得到WRITE_CLOSED，不进入SQL，保留RUNNING待下次恢复。
6. 重复/并发close等待第一次完整排空结束，不能因第二次close提前返回而提前销毁数据源。关闭许可isOpen=false和STOPPED是测试可观察控制点。
7. shutdownWaitSeconds只是CPU/Worker等待预算，不宣称底层数据库永久卡住时排空已有物理事务也固定在30秒内结束；安全屏障宁可等待已入事务，不通过中断H2伪造有界完成。

## B2测试组成迁移与新增证据

- 新增`src/test/java/dev/twelvetimes/b2/B2CompositionTestApplication.java`，位于生产扫描包外；显式排除VectorSearchApplication和B3TaskConfiguration，扫描原控制器/配置/Mapper。B2TestSupport及自有DocumentManagementApiTest只切换启动源，原QUEUED/旧RUNNING拒接/容量等断言保留。
- 此隔离只用于历史B2阶段测试；新增TaskRecoveryServiceTest直接启动真实VectorSearchApplication，生产组成始终接真实处理器与真实B1，不以全局禁用handler换通过。
- 自有新增B3单测共17项：终态9、恢复5、生命周期3。真实B1正常/空文就绪、两表第二步回滚、旧令牌拒写、失效/计数不覆盖、失败分类、暂时锁错误有限重试、提交后抛错独立完整确认、同长度错误向量拒认、恢复前拒接/扫描0、旧RUNNING重算attempt递增、成功不重做、恢复失败门禁、正常关闭、CPU超时后数据源实际关闭仍SQL调用0、已有终态物理commit前屏障不提前完成。
- 故障通过测试源码装饰真实Mapper/PlatformTransactionManager和包裹真实B1来控制，latch/状态等待均有界；没有用sleep证明竞态。生产中的50/100ms仅作数据库失败退避。
- 独立子进程强杀及SIGTERM证据由QA独立执行，本角色未以普通异常/正常关闭替代强杀，也不把其他角色尚未交付的结果提前记为通过。

## 本机命令与阶段结果

Maven统一显式Java17与临时本机缓存：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 <goals>
```

- `-Dtest=TaskTerminalServiceTest test`：9通过，再提交终态段。
- `-Dtest=TaskRecoveryServiceTest,TaskTerminalServiceTest,TaskDispatcherTest,DocumentAdmissionServiceTest,DocumentManagementApiTest test`：33通过，再提交恢复段。
- `-Dtest=TaskLifecycleTest,TaskRecoveryServiceTest,TaskTerminalServiceTest,TaskDispatcherTest test`：26通过，再提交生命周期段；2026-10-23 00:47:04 +08:00结束。
- `node --test tests/frontend/*.test.mjs`：先在bb6e5eb代码树运行25通过，最终集成源码9fd56d4再次运行25通过，均0失败/取消/跳过。此结果不替代浏览器验收。
- 阶段日志：`/private/tmp/12times-b3-terminal.log`、`/private/tmp/12times-b3-recovery.log`、`/private/tmp/12times-b3-lifecycle.log`、`/private/tmp/12times-backend-b3-node.log`。测试仅使用自己的绝对临时H2及随机本机HTTP端口，不共享主库；日志/报告不提交。
- 主控审查并合入QA组成迁移`2734b7e8e6b2a60ee7ef0dc519e0ecdb16629732`、真实B3健康与代理专项后，将本树快进至`9fd56d4d61aebd2b18950f339aef271a2c0ec591`。该SHA保留原B2业务断言，健康断言跟随B3阶段元数据；没有为制造记录而运行已知阶段组成不适用的旧整套测试。
- 在**源码SHA `9fd56d4d61aebd2b18950f339aef271a2c0ec591`**，执行上述Java17环境下`clean verify`，于2026-10-23 **00:51:01 +08:00**完成，退出0，**BUILD SUCCESS：73 unit、47 IT，失败/错误/跳过均0**。unit含原B2 53、本角色B3新增17及主控健康3；IT为C0 6、B1 16、B2 22及代理Origin 3。同期Node **25通过**。合计145项，不将当时尚未合入的QA-B3专项加入数量。
- 最终完整日志：`/private/tmp/12times-backend-b3-verify.log`；报告为本树`target/surefire-reports/`、`target/failsafe-reports/`。本交接为随后文档提交，不把文档SHA倒填成已测源码SHA。

## 未验证和外部边界

B4全量检索与原子计数、真实浏览器、相关性/性能、HTTPS代理Origin归因、正式业务发布不在本角色本轮验收中。未改ApiRequestFilter、健康、代理或基础参数；待主控/QA先实测代理风险。没有服务器变更、凭据处理、持久数据恢复/删除或远端发布操作。完成后等待主控审查，保留全部B1/B2历史证据。
