# 最终健康与冷备恢复独立验收

实测代码基线：`3cea5eb877de137a111e129e85910d97f753ec1f`加本轮当时未提交的验收源码；agent/qa，Temurin17。旧B1—B4报告/原始故障证据未改，生产、DDL、算法/参数、依赖、服务器数据未改。

## 本地真实执行

- 目标`verify -Dit.test=FinalHealthIT,BackupRestoreIT,C0BootstrapIT`首次通过20 IT（13健康、1恢复、6 C0），2026-10-23 02:30:52 +08:00。
- 完整`./mvnw -B clean verify`：**104 unit + 110 IT = 214通过**，失败/错误/跳过0，02:34:03 +08:00 BUILD SUCCESS。
- `node --test tests/frontend/*.test.mjs`：**38通过**，失败/取消/跳过0。本地合计**252通过**；真实Chrome仍NOT_RUN。
- 实际Maven使用Temurin17 JAVA_HOME、MAVEN_USER_HOME=/private/tmp/12times-maven-user-home，附`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`，仅下载适配，不是交付隐式依赖。
- 日志：`/private/tmp/12times-qa-final-health-backup-target.log`、`/private/tmp/12times-qa-final-health-verify.log`、`/private/tmp/12times-qa-final-health-node.log`。最终合成证据写入target/b4-evidence/FINAL_HEALTH-*及FINAL_BACKUP_RESTORE-*。

## 13项最终健康验收

| 层次 | 实际场景 | 证据 |
| --- | --- | --- |
| 真生产HTTP | 正常B1成功及搜索计数后，重复GET /health 20次 | 200/UP/stage B4，taskReady/businessReady及六checks均true，版本正确；embed调用0，文档/任务数及完整DocumentRow前后相同，hitCount保持1 |
| 真实Service+Controller故障装饰 | RECOVERING/DEGRADED/STOPPING/STOPPED四参数 | 均503，数据库连接借用0；健康不擅自改写状态。状态装饰用于组件边界，未伪称所有状态都在启动期间可达HTTP |
| 真实JDBC+Service+Controller | 连接不可用、连接报告只读、元数据报告只读、必需字段查询失败四参数 | 均503/businessReady=false，失败原因不泄露secret；只运行SELECT，不调用业务commit/rollback；TaskReadiness仍READY，不借健康做修复 |
| 真B2隔离HTTP | 缺少生产handler | 503，databaseReady仍true，dispatcherReady=false；不伪造旧健康UP |
| 真生产HTTP | 实际TaskDispatcher关闭 | 503，不把TaskReady历史状态当业务就绪 |
| 真生产HTTP | 注销真实POST /api/search路由 | 503/routesReady=false，而databaseReady/searchReady仍true，证明缺路由可单独使全业务不就绪 |
| 真实Service+Controller | 算法实例/版本不一致，以及上下文缺SearchService组件 | 分别503且对应checks false；未伪造Snapshot，使用真实DefaultBusinessReadiness聚合 |

C0BootstrapIT按主控批准迁移为最终契约：明确B2缺handler组成503/DOWN/stage B4/taskReady=false/businessReady=false，新增数据库可读/非只读检查true及dispatcherReady=false。原文件库哨兵、两表、QUEUED、重启及大计数断言完整保留，没有为了健康通过将哨兵改为SUCCEEDED。
健康报告只反映本次只读运行事实；只读标志检查不承诺未来磁盘一定可写，200也不表示浏览器验收或正式发布已授权。

## 临时H2冷备与独立恢复（1项真实IT）

BackupRestoreIT只使用JUnit专用绝对目录：真实生产B1提交ababa及空文→空查询命中两项→失效空文→再次检索只命中有效项，最终有效计数2、失效计数1。正常关闭整个应用/Hikari后，对源knowledge.mv.db计算SHA256，复制到全新0700备份目录（文件0600），源/备份哈希一致，覆盖既有文件明确拒绝。
再复制到另一个全新恢复目录，开库前SHA一致；使用真实生产应用打开，保留任务ID/成功状态/token/算法版本/完整向量/两表首次完成时间、计数、失效及首次时间、原文，pending=0；恢复并未改动源或备份原件。
此用例采用契约允许的等价冷文件复制，**不是声称已在Mac执行Linux生产cold-copy脚本**，不替代服务器恢复授权。

## Linux脚本交付与当前状态

新增`docs/testing/final-linux-check.sh`：

```sh
bash docs/testing/final-linux-check.sh --github-hosted-test-only target/vector-search.jar
```

前置：一次性GitHub-hosted Linux runner（GITHUB_ACTIONS=true、RUNNER_ENVIRONMENT=github-hosted）、Java17、python3、实际lsof、免交互sudo；固定`/var/lib/vector-search`和`/var/backups/vector-search`必须均不存在，否则拒绝。用独占mkdir创建并打runId标记，finally只清本次新建且标记完全匹配的根目录，不接管/覆盖任何已有目录，不适用自托管runner或服务器。
计划脚本行为：真实JAR创建/使用活动H2→真实lsof拒绝活动冷复制→SIGTERM确认退出→真实flock持锁拒绝→冷复制manifest/SHA/权限→拒绝覆盖及不匹配SHA→新restore-check目录恢复→真实JAR查询状态/向量就绪/计数/失效保持。仅systemctl ActiveState=inactive使用当前进程PATH内shim，明确是前置层模拟；不模拟lsof、H2、锁或复制。
本机已执行`bash -n`及Python AST语法检查，均通过；**Linux脚本行为当前NOT_RUN，不能计为PASS或混入252项**。待主控在一次性Linux CI实际执行并保留target/final-linux-evidence，结果另补证。真实systemd服务启动/服务器备份恢复、认证/TLS业务入口、正式发布仍NOT_RUN。

本轮无测试失败；所有Maven/临时应用及既有B3子进程已结束。主控合入后需按最终SHA全回归/CI核证；本地脚本模板、HTTP和Node结果均不能替代真实浏览器验收。

## 首轮Linux CI失败与只读诊断补丁

主控报告run39833314291/main8fd97e17：Java104 unit+110 IT及Node38通过，Linux真实JAR已ready，活动库冷复制被安全拒绝，但原因是“lsof退出状态与输出矛盾”，不符合本用例要求的“存在其他进程文件占用”，故Linux阶段仍FAIL，不能把任意拒绝改算通过。主控已核对实际H2路径为固定测试目录，finally正常SIGTERM/Hikari完成；原件在主工作区target/delivery/failed-39833314291/reports。
QA诊断小补丁只在每次cold调用前，于同sudo/PATH/cwd及受管锁FD上下文运行真实lsof -nP +D；记录euid/pid/cwd/可执行路径/锁占用情况、真实返回码、未合并未strip的原始stdout/stderr，并记录同上下文lsof -v。诊断释放自己临时持有的锁后再跑原cold命令，不模拟lsof，不更改部署脚本或既有拒绝/恢复断言。
原始诊断及cold命令输出写target/final-linux-evidence/cold-*，有界超时；当前仅bash与两层Python语法检查通过，未在Mac执行Linux行为。等待主控下一轮CI拿原始输出，再交服务器Owner据实修复，不预先认定lsof或H2缺陷。本补丁无Java修改，不重报旧252为本次新的全量结果。

## 修复后Linux CI独立核证（run39834086512，后续实际结果）

本节追加新事实，保留上文首次失败和旧NOT_RUN快照不变。核证源码为`68d80cb967c6d321047672811dea45b555ba5cbc`；[CI run39834086512](https://github.com/Qbsstg/text-vector-search/actions/runs/39834086512)已成功完成。QA只读核对主工作区下载的`target/delivery/ci-39834086512/reports`及工作流原日志`/private/tmp/12times-final-ci-39834086512.log`，没有在本机重跑Linux或操作服务器。

- 逐份解析19个Surefire XML，合计104 tests；14个Failsafe XML，合计110 tests；两组failures/errors/skipped均0，不仅引用摘要。
- 工作流原始Node TAP为1..38、tests38/pass38/fail0/cancelled0/skipped0/todo0。Java+Node仍为252项通过；Linux行为单独列证，不将脚本步骤虚增为JUnit用例数。
- Linux原件96份，完整summary记录原真实JAR pid5108、随机端口42319及正常SIGTERM exit143；恢复JAR pid5252、随机端口36515及exit143。脚本最终COMPLETE，不是只通过语法或只跑到启动就算通过。

逐一核对7次cold原始返回码及未合并stdout/stderr：

| 调用 | 真实退出码 | 真实结论 |
| --- | --- | --- |
| cold-01 active-rejected | 1 | stderr明确“存在其他进程文件占用”，原活动库拒绝断言通过 |
| cold-02 lock-rejected | 1 | stderr明确“单实例锁被占用”，真实flock拒绝断言通过 |
| cold-03 backup-case | 0 | 全新目录冷复制成功；manifest源码、SHA256、目录0700/文件0600断言通过 |
| cold-04 backup-case重复 | 1 | stderr明确目标已存在，未覆盖 |
| cold-05 restore-check-wrong-sha | 1 | stderr明确备份版本或数据库校验和不匹配，拒绝错误SHA |
| cold-06 restore-check-case | 0 | 独立新目录恢复校验成功 |
| cold-07 restore-check-case重复 | 1 | stderr明确目标已存在，未覆盖 |

恢复后的真实应用逐字段保持documentId/taskId/taskStatus/vectorReady/hitCount/invalidated/invalidatedAt/vectorCompletedAt/text，已失效文档检索hits为空，备份原件SHA不变。此次冷备数据库SHA256为`6cc2ddc700632138e29b042e24429b6cff648251b01ca30c0deafa83fb7b0823`。工作流封装候选记录源码同为68d80cb，JAR SHA256为`61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f`；该值由Linux summary与封装输出相互一致，候选二进制下载复核仍由主控独立完成。

### lsof原样诊断核证

- 同sudo上下文euid=0、cwd为runner项目目录、实际可执行文件/usr/bin/lsof。7份`lsof -v`均rc0、stdout空，版本信息正常写在stderr，版本4.95.0；不能把版本命令的stderr与+D查询混淆。
- 10份真实`lsof -nP +D`诊断均rc1、无超时，stderr逐份长度0。活动库stdout除标题外明确含java pid5108的knowledge.mv.db及诊断自身python pid5156的永久锁FD；因此rc1不等于没有占用。
- 冷库cold-03 stdout只含诊断自身pid5182的永久锁FD；不同于活动库中的其他Java PID。cold-02诊断lock_acquired=false，与真实flock保持占用一致。原始stdout、stderr、metadata.json与sudo诊断JSON均保留，未strip或用任意拒绝替代期望。
- 这些实际观测支持服务器Owner对rc0/1严格解析占用行的最小修复：其他PID仍拒绝，允许自身永久锁不等于允许活动H2；首轮失败证据不因此回写为通过。

本次Linux行为在一次性GitHub-hosted runner中实际通过；**systemctl ActiveState仅使用测试PATH内inactive shim，不证明真实systemd启停或实机停止前置**。真实Java/H2、lsof、flock、文件复制/哈希/权限及新目录恢复没有模拟。正式浏览器、服务器业务入口/认证/TLS代理、systemd发布及真实服务器数据恢复仍NOT_RUN；CI健康全绿不替代发布授权。
