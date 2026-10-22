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
