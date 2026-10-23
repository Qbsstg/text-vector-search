# FINAL-ACCEPTANCE 本地稳定性诊断

状态：原固定包稳定性门禁 **BLOCKED**，真实失败与根因保留。修复候选的 **Mac 完整 35 分钟＋正常重启、确定性回归、旧库副本升级兼容均 PASS**。Linux 旧包自然对照 FAIL，新包 Linux 结果由服务器 Owner 最终追加；生产新包尚待批准，本文不能据此宣称已发布。任务 ID：`FINAL-ACCEPTANCE-BE`。

## 固定候选与范围

- 生产固定源码：`2764eddf34418fdcc8ed666e36937673c7feadb8`。
- 固定 CI：`39915764822`。
- 受测 JAR SHA-256：`b8b67460ee6af26ec5ea7854377b63919c18c37760b11e3fbf49f7672be7f45f`。
- 后端工作分支：`codex/final-acceptance-backend`；基线 `46ebe7d`。
- 本轮只操作本机新绝对临时目录和回环端口，不修改、重启或压测生产，不改算法、API、DDL、资源参数。

修复后另行固定候选：源码 `9883400451c9b2a22d13134cbd698d7478851b3d`、成功 [CI39927516112](https://github.com/Qbsstg/text-vector-search/actions/runs/39927516112)、JAR SHA-256 `becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece`、27,322,432 bytes。后端独立核对实际文件、manifest 源码／CI、两项 SHA256SUMS、唯一 `h2-2.5.252.jar` 与冻结 schema SHA `54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d` 后才启动。报告后续文档提交不替换这个受测源码标识，生产仍为原固定包。完整自动化数量由主控与独立 QA 的聚合报告核证；下文仅把后端 Owner 实际执行的新增单项回归记为亲自执行。

## 已核实的旧故障事实

固定 JAR 的前一轮隔离运行在业务计时 `1500.13s` 失败；第 25 分钟健康检查返回 200，随后提交返回 `503 / DATABASE_UNAVAILABLE`，此前成功写入 22 篇。线程转储存在，但原脚本失败后直接退出，子 JVM 后续已经不存活；不能把那次运行描述为持续保留现场。

源码显示，提交服务在物理提交前捕获 `DataAccessException` 后仅构造公开业务异常，未保留原 cause；原日志无法据此外部错误码确定 SQL 根因。健康检查仅验证连接有效、列可读和非只读状态，不执行真实业务写入，因此健康 200 与提交 503 并不矛盾。

## 隔离诊断设计

2026-10-23 `08:17:59.140319Z` 开始固定原 JAR 的 35 分钟同序列运行；本机端口 `18187`，全新绝对 H2 目录，业务资源参数保持冻结值。仅添加 Hikari／Spring 事务 DEBUG 观测和 JFR `jdk.JavaExceptionThrow` 事件；原始日志、请求、JFR、线程、资源和数据库原件仅本地保留。

脚本 SHA-256：`2cd9a6594cacc7deb92664a6f14ead973765d047e56f4db7d98dc0157f678841`。最小 JFC SHA-256：`9e4d1f7616b409c118bbdcd6c4267716de28591b330522196343f16f0a9a00d4`。

有限业务序列为：启动提交 3 篇并核对终态、失效第一篇、搜索及核计数；每分钟健康检查，第 8—12 分钟仅健康检查，其余分钟增加一篇，每 5 分钟在混合阶段搜索并全量核对；满 35 分钟后全量核对，正常 SIGTERM，同库重启，再核对旧元信息与计数、提交两篇并搜索核对，最终正常退出。业务写入没有客户端重试。

额外只读观测每 30 秒保存完整健康快照、该 JVM 的资源和 FD 数，与业务请求分开计数。若失败，停止业务流量，立即采集线程、JFR、Hikari／事务日志、OS／FD 和健康；保留同一 JVM 最多 60 分钟，不自动重启。

JFR 启动期包含 H2 预建的 OOM 异常哨兵和可选 JTS 类探测异常；这些构造事件不等于真实运行故障，必须结合时间、业务响应和事务栈判断。

## 已执行的独立副本检查

在旧失败进程已退出后，将失败数据库复制到全新诊断目录，原件与副本初始 SHA-256 一致：`8db2457b0ffbd5fad1e1fe02b5df894516ea3c04f5ab19392b4d661a721b6345`。仅在副本使用固定 JAR 内 H2 驱动，查询为 22 篇；用第 25 分钟失败文档的同一 ID 完成文档与任务两表插入并回滚，真实结果 `COPY_INSERT_BOTH_SUCCEEDED_ROLLBACK`。

该结果说明离线副本在新连接下可写，不能将旧 503 直接判定为永久数据库损坏；它不能解释原 JVM 当时的连接、事务或 SQL 异常。

## 连接生命周期窄诊断

从固定 JAR 内 `HikariCP-7.0.2` 的实际字节码核对，默认连接寿命 30 分钟，随机提前最多四分之一，覆盖约 22.5—30 分钟。因此将自然连接回收列为待验证假设，没有改变该参数。

另外两个互相独立的新库／随机端口诊断，均直接使用固定 JAR 提取的原生产编译类和依赖。每轮只调用 Hikari 合法 `softEvictConnections()` 回收连接，再完成真实提交、真实任务成功和真实搜索；第一组直接调用生产服务 24 轮，第二组提交经过真实 HTTP／Tomcat 线程 24 轮，两组都完成至 `conn99` 并正常关闭。JFR 提取没有业务期 SQL 异常，仅有前述启动探测事件。

这两组实测不能排除特定长时交错。随后确定性复现发现，全池回收可能使数据库整体关闭再打开，恰好避开下面的单连接关闭路径，因此不能据此前两组通过否定该路径；不将其替代 35 分钟原业务序列，也不作为旧 503 已修复的证据。

资源观测口径：观察脚本原 `fdCount` 字段实际保存的是 `lsof -Fn` 输出行数，含 cwd／txt 等非数字 FD，不能直接引用为实际 FD 数。另以 `lsof -Ff` 仅统计数字字段的真实核对为 18；原观测数据保留，不改写历史。

## 已确定性复现的故障机制

在固定原生产编译类、原依赖和原 DDL 的另一个新库中，借出创建 schema 的物理 `conn0`，同时保留 `conn1` 维持数据库实例，只合法回收 `conn0`。此后真实 `embedding_tasks` 插入确定性失败，实际捕获的 `SQLException.getSQLState()`／`getErrorCode()` 为 `23514 / 23514`；cause 链最深层 JDBC 异常的真实字段为 `90098 / 90098`，堆栈经过 `ConditionInConstantSet.getValue()`、`SessionLocal.compare()`。这是仍打开数据库中的 CHECK 表达式持有已关闭创建会话，不能将异常文本的 “database has been closed” 解释为整个文件数据库已关闭或损坏。

第二个独立新库重复单连接回收后，实际 HTTP 顺序为 `/health` 200、`POST /api/documents` 503 `DATABASE_UNAVAILABLE`、`/health` 200；前后健康 `businessReady` 与各项 checks 均为 true。完整 SQL cause 字段、栈和 HTTP 形态同时核证。诊断程序退出 0 表示预期故障被重现，不是业务稳定性 PASS。

上游 H2 官方问题 [#4308](https://github.com/h2database/h2database/issues/4308)、[#4302](https://github.com/h2database/h2database/issues/4302) 记录同版本 CHECK IN 缓存创建 session 的回归，与本项目 `embedding_tasks.status` 的冻结约束相符。外部资料只作机制交叉验证，项目结论以本项目上述真实异常和 HTTP 复现为依据。

本轮自然长时序列随后核证同一机制，见下一节；没有通过修改 DDL、算法、API 或资源参数绕过故障。

## 固定原 JAR 自然寿命序列：FAILED

原候选在第 25 分钟、`1500.093s`、22 篇文档、207 次业务请求时再次返回 `503 / DATABASE_UNAVAILABLE`。此前失败的历史结论保留，本次新的异常观测不能改写为历史原件已经包含 SQL 根因。

北京时间关键顺序为：

1. `16:40:38.288`：普通 `conn2` 因 `maxLifetime` 自然关闭，后续业务继续正常。
2. `16:42:55.084`：创建约束的 `conn0` 因同一寿命策略自然关闭；池正常创建 `conn5`，仍为 4 个连接、无等待。
3. `16:42:59.120`：第 25 分钟提交在仍正常的 `conn1` 开启短事务。
4. JFR 真实事件 `08:42:59.146494167Z` 开始记录 `JdbcSQLNonTransientConnectionException`，接着 `DbException [90098-240]`，栈为 `ConditionInConstantSet.getValue:80 → ConstraintCheck.checkRow:99`；随后为约束异常 `23514` 和 Spring `DataIntegrityViolationException`。
5. `16:42:59.199—.202`：事务真实 rollback 并释放连接，没有进入提交未知分支。健康在失败前、失败时单独采集和失败后持续返回 200，各项 checks 均为 true。

失败时停止业务写入并保留原 JVM 与数据库文件锁，没有为了继续测试重启；线程、JFR、SQL 事件、池／事务日志、健康、OS／FD 分别采集。新候选门禁完成并封存证据后，于 `2026-10-23T09:34:01.821614Z` 对该诊断 JVM 正常 SIGTERM 收尾，原数据库和证据完整保留。旧运行 **35 分钟未完成，正常重启阶段未执行**，不得记为 PASS。自然回收时间、完整异常链和独立确定性复现共同确定了 H2 CHECK 表达式持有关闭 session 的根因。

## 确定性自动回归

新增 `SchemaConnectionLifecycleTest`，使用绝对临时文件库、冻结 `schema.sql` 和两条真实 JDBC 连接，明确在 keeper 存活时关闭 initializer，再断言合法文档与任务能够提交并按 JOIN 查询核对原文、任务、状态及零计数。没有使用 sleep，也没有改约束或把预期失败当业务成功。

Java 17 执行 `./mvnw -B -ntp -Dtest=SchemaConnectionLifecycleTest test`：旧 H2 `2.4.240` 真实结果为 **1 test，0 failures，1 error，0 skipped**，错误链为 `23514 → 90098`、`ConditionInConstantSet.getValue()`；旧红日志、XML／文本报告及其 SHA 已先行独立保留。

主控在其拥有的 `pom.xml` 中只锁定 `h2.version=2.5.252`；专用试验提交 `a05ce179c1c7b78a7e8f93a2e6b7419017b089e5` 已由后端分支合入。Java 17 实际命令为 `./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2 -Dtest=SchemaConnectionLifecycleTest test`，**1 test，0 failures，0 errors，0 skipped**，XML classpath 实际为 `h2-2.5.252.jar`。单元回归提交 `7c801af`。

第一次新版本命令遗漏现有 Maven `central-only` settings，父 POM 解析失败，未执行测试；该原件保留，不能计为代码或测试失败。修正 settings 后才获得上述真实绿色。

## 修复新 JAR 的 Mac 35 分钟＋正常重启：PASS

新 JAR 业务计时于 `2026-10-23T08:56:36.414191Z` 开始，端口 `18190`、新绝对 H2，诊断参数和业务时间表与旧失败序列一致。启动时 PID `35571`；脚本 SHA-256 `8e8c7bd0c303632dafea933bf73be8393d56a211ce909bbc2ff8a5c022a577b9`。额外只读观测正确统计数字 FD，并在重启时跟随 report PID。

关键窗口：北京时间 `17:22:26.949`，建表 `conn0` 因 `maxLifetime` 自然关闭；第 26 分钟随后的真实提交、任务成功及详情查询均通过。`17:23:37.515` 前，原始四条连接均已自然回收，应用仍保持同一 PID、重启数为 0，后续业务继续通过；没有通过整库重开避开该路径。

实际执行新固定 JAR 的诊断脚本，传入上文完整 JAR hash 和源码 SHA：主序列满 35 分钟、33 篇文档后，正常 SIGTERM，同一 H2 重启，重新核对已有元信息、状态、失效和精确计数，再提交两篇并搜索核账，最终再次正常停机。脚本退出码 0，主报告实际为 **PASS／2104.004 秒／35 篇／492 次业务序列请求／正常重启 1 次／写重试 0**，命中计数总和 37。另有 34 次 readiness 尝试和 70 次额外只读观测，后者全部健康 200、数字 FD 保持 18，不与业务计数混算。

两次 JVM 的 JFR 提取只有启动期哨兵／可选 JTS 探测异常，没有业务 SQL 异常。完整主报告封存 SHA-256 为 `9354a3db7ee13148f83124f9d1fc70b0bef9bccfcfe32cba544579dde4a34fd8`，没有覆盖旧 FAILED 报告。

证据层明确区分：重启前后对照为 API 元信息、任务状态、就绪标记、原文、失效和计数；这本身没有提供向量原始字节的前后快照。正常停机后另复制全新冷副本，只读使用最终 CI JAR 中的冻结 B1（32 轮、20,000 code points 上限）逐篇重算编码；35 篇的存储原始字节共 35,840 bytes 与重算结果全部 `Arrays.equals`，双版本与成功终态同时匹配。最终冷副本向量快照 SHA 为 `76e82ff7ff1cf80713d3608d00fdd18a612c471a20862f6f1c8f289448fdaaf7`；源库只复制／核 hash，没有打开源库，hash 前后相同。该补充证明最终存储 raw 与冻结 B1 完全一致，不冒称主序列重启前后 raw 已逐字节比较。

此 PASS 仅限该固定 JAR、有限数据量和上述序列，不扩展为无限负载或 SLA。

## 原 2.4 文件全新副本升级兼容：PASS

使用上述同一新 CI JAR，在另一新绝对目录复制旧失败库，回环端口 `18189`，执行真实业务读取／写入、正常 SIGTERM、同库重启和再次核对。实际 114 次请求、1 次正常重启；新增两篇文档真实成功，搜索仅作用于新渠道，重启后新增数据和计数保持。

启动新 JAR 前，用旧 2.4 驱动只读导出原 22 篇文档和 22 条任务的全部持久字段；升级后停机、同库重启后停机，用新 2.5 驱动各导出一次。比较包含原始向量完整字节、原文、渠道、任务／token／尝试数、全部时间、计数、失效与错误字段，三份快照逐字节相同，SHA-256 均为 `a2f799e4e135b48acdc2d2cc139687427bec8757aa0c9b3806ea92e023f29d2b`。不是只比向量长度。

原 2.4 数据库原件只做 hash 和文件复制，没有打开原件，检查前后 SHA 均为 `8db2457b0ffbd5fad1e1fe02b5df894516ea3c04f5ab19392b4d661a721b6345`。此检查证明本次独立副本升级读写与重启兼容，不证明降级回退；生产尚未替换新包。

## Linux 独立对照分层状态

服务器 Owner 对原 `2764 / b8b` 固定包的独立自然序列于第 30 分钟真实失败：`16:57:04.974` 建表 `conn0` 因寿命关闭，随后业务 JFR 为 `90098 → ConditionInConstantSet → ConstraintCheck → 23514 → DataIntegrityViolationException`，返回 503；27 篇、269 次请求、重启 0，健康仍为 200。该原始证据仅在服务器受限目录保留，Owner 聚合摘要 v2 SHA 为 `f73f1cd421ccf93c495281b8cc5e903c4de766895a750a4412f6c4da9e5340c5`。这与 Mac 自然失败和本地确定性复现交叉核证，不改写为新候选失败。

新 `988 / becb` 的 Linux 最终结果由服务器 Owner／主控追加，本后端报告没有代其提前记 PASS。

## 未执行与后续边界

- 本后端 Owner 没有代替主控重复执行完整 Java＋Node；完整自动化采用主控与独立 QA／CI 的核证，本人的新增回归命令和实际数量已单独记录。
- 新候选生产部署未执行，另行申请完整 SHA／JAR hash／CI 批准；当前生产包仍为原 `b8b`。
- 未证明 H2 降级回退兼容，未执行无限负载或 SLA 验证；未将未运行项标为 PASS。
