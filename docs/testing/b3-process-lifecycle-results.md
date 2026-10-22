# B3后半独立进程与生命周期验收

最终源码基线：`fe32b5bc4ef1e8d4efb66938e4f8af72b729b6fc`加本轮当时未提交的三个后段测试文件，分支`agent/qa`，Temurin17。不是声称仅基线SHA本身已含这些新测试。
测试源码本次新增，提交前实测；主控对合入后的提交SHA仍需最终核证。仅acceptance及docs/testing变更，不改生产、DDL、公共DTO、pom或B1黄金。

## 实际发现与修复依据

1. 首次目标运行遇到测试探针自身隔离错误：B2显式ComponentScan拾取探针@TestConfiguration，mode为空导致旧unit启动异常（73项中35错误，新进程IT未执行）。修正为测试源码内显式`qa.process-probe=true`条件，仅探针主函数启用；不改生产装配或断言。原日志`/private/tmp/12times-qa-b3-process-target.log`。
2. 隔离修复后的6项进程验收，4个强杀与超时路径通过，正常SIGTERM失败：pid36013/36351/36938在STOPPING后仍RUNNING。增加测试专用事务异常事件后，实际根因是H2 `JdbcSQLNonTransientConnectionException`、`[90121-240] Database is already closed`，事务尚未取到连接，非Mapper回写问题。原命令/日志分别为`/private/tmp/12times-qa-b3-process-target-r2.log`、`/private/tmp/12times-qa-b3-sigterm-diagnose.log`、`/private/tmp/12times-qa-b3-sigterm-tx-diagnose.log`。
3. 按主控批准仅给测试URL补`DB_CLOSE_ON_EXIT=FALSE`做A/B，原正常SIGTERM成功断言不变；pid37186实际exit143、SUCCEEDED、真实B1黄金及两表时间一致。日志`/private/tmp/12times-qa-b3-sigterm-close-false.log`。主控随后将该最小关闭配置纳入生产默认URL，并修复任务表时间确认。
4. 最终探针不再覆盖`spring.datasource.url`，只传`APP_DATA_DIR`隔离目录，从实际HikariDataSource.getJdbcUrl读取并断言生产默认含关闭flag，快照也使用该实际URL。故最终通过不是继续依赖测试专用URL绕过生产配置。

原失败和A/B进程事件在最终clean前已完整保留到`/private/tmp/12times-qa-b3-process-evidence-before-final-clean/`。按主控明确要求，pid36938默认失败和pid37186最小A/B成功的原始子进程日志字节不变复制为`docs/testing/evidence/b3-sigterm-close-race/default-failure-pid36938.txt`及`close-on-exit-false-success-pid37186.txt`，附SHA256SUMS；不含数据库、私钥或真实业务文本。最终完整进程日志位于`target/b3-process-evidence/`，已由主控配置CI产物上传。

## 最终真实运行

- 目标：`./mvnw -B -Dit.test=B3ProcessIT,B3LifecycleIT verify`，74 unit + 11新IT通过，2026-10-23 01:04:22 +08:00 BUILD SUCCESS。
- 全量：`./mvnw -B clean verify`，**74 unit + 74 IT = 148项通过**，失败/错误/跳过0，01:05:45 +08:00 BUILD SUCCESS。
- 前端既有回归：`node --test tests/frontend/*.test.mjs`，**25通过**，失败/取消/跳过0；未声称完成真实浏览器验收。
- Java实际命令附本机`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`，JAVA_HOME指向Temurin17，MAVEN_USER_HOME=/private/tmp/12times-maven-user-home；这些不是交付隐式依赖。
- 最终日志：`/private/tmp/12times-qa-b3-process-lifecycle-final-target.log`、`/private/tmp/12times-qa-b3-final-verify.log`、`/private/tmp/12times-qa-b3-node.log`。

## 六项真实子进程验收

每个用例独立临时文件H2、真实VectorSearchApplication、随机回环HTTP端口。测试BPP只装饰真实Mapper/TX/handler/统一B1；四检查点为QUEUED已物理提交、RUNNING已物理提交、终态SQL已执行未commit、终态真实commit后，均实际destroyForcibly发送强杀，waitFor确认退出后才打开文件库。新连接打开成功是锁释放证据，再启动真实生产组成恢复。不会用普通异常、context.close或SIGTERM冒充强杀。

最终完整回归的实际进程证据摘要：

| 检查点 | 强杀PID/退出码 | 重启PID/退出码 | attempt前→后 | 关键状态 |
| --- | --- | --- | --- | --- |
| QUEUED_COMMITTED | 38438 / 137 | 38440 / 143 | 0→1 | 接收记录保留，无客户端重提；B1完成 |
| RUNNING_COMMITTED | 38443 / 137 | 38446 / 143 | 1→2 | 旧token被恢复清理，新token领取，B1完成 |
| TERMINAL_BEFORE_COMMIT | 38449 / 137 | 38451 / 143 | 1→2 | 强杀后独立读仍RUNNING且向量/完成时间空，无半成功；重启完成 |
| TERMINAL_COMMITTED | 38455 / 137 | 38457 / 143 | 1→1 | 任务/向量/token/首次两表时间整个快照不变，成功不重做 |

以上每次恢复完成都比对完整向量SHA：`ad82c864c2dec3476778eeaf5f6e83a64d5827b9f029f22737e7e33523125ad1`（原冻结ababa/R32黄金），两表完成时间一致；任务ID保持，非已成功的恢复attempt递增且换token。

- 正常SIGTERM：pid38461，实际外部`/bin/kill -TERM`，保留stdin/stdout使父进程可等真实STOPPING事件后释放CPU；生产handler在等待期内完成，数据源关闭后快照SUCCEEDED、黄金一致，exit143。
- CPU超时SIGTERM：pid38406，真实B1先算完再latch阻塞；生产等待上限1秒后关闭写许可及实际数据源，测试hook在Hikari.isClosed确认后才释放CPU；HANDLER_RETURNED，`LATE_SQL_COUNT=0`，持久RUNNING、无向量，exit143。重启pid38432，真实B1恢复成功、attempt1→2、换token，exit143。
- 证据包含APP_READY实际端口、生产URL、PID、事件、前后状态/token/attempt/时间/向量SHA及退出码；最终11个子进程均已查询确认不存活。没有操作任何服务器或演示数据库。

## 五项独立生命周期/恢复验收

1. 真生产启动恢复：恢复物理commit前latch暂停，确认RECOVERING、真实接收服务拒绝SERVICE_NOT_READY、manual scan为0且没有扫描/领取事件；放行后事件先RECOVERY_COMMITTED，再READY扫描/领取。原RUNNING attempt7→8、真实B1完成且失效/首次提交时间/大计数保持。连接器在恢复前可能未监听，未虚构此时可达HTTP503。
2. 真恢复commit后抛异常，两表submitted_at刻意不同；任务表专用确认成功READY，task时间10/21、doc时间10/20都保留，attempt7和提交现场不被覆盖。这是对主控合入P2修复的独立回归。
3. 恢复SQL失败：明确DEGRADED、不可接收、持久RUNNING原状，绝不先READY。
4. 实际生产HTTP：确认真实ProductionTaskExecutionHandler与READY；切STOPPING后真实GET文档/任务/健康及POST提交均503、不新增行。RECOVERING启动门禁由第1项事件顺序证明，不将未监听错误写成HTTP503；恢复filter其他分支仍由既有unit补证。
5. 关闭排空：终态短事务物理commit前暂停，发生产dispatcher.close；写许可已关闭、STOPPING且close未返回，独立读取仍RUNNING；放行实际commit后才允许STOPPED，最终成功。证明已进入短事务不会被数据源抢先销毁。

## 范围限制

测试只证明这些真实受控时序，不承诺任意卡死SQL均在固定秒数退出，不等同于实际断电可靠性或跨任意JDK平台。关闭观察器只在测试源码，故障钩子没有生产端点；正常/恢复成功路径均调用冻结B1，不硬编码终态或算法输出。
B3后半本地验收已完成，主控合入/CI结果另核证；B4检索计数、真实前端浏览器及DEP3正式业务发布仍未执行。当前无测试/子进程/Maven后台工作，交接后可释放并行槽位。
