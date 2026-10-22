# B2接收前段独立验收结果

代码基线：`6345bd97403c7398df8a8576d4e2763d4dd81494`；分支`agent/qa`；执行日期2026-10-22，JDK17。
只使用已合入公开接口；无业务、B1、DDL、pom、配置或依赖变更。

## 实际执行

目标命令为`./mvnw -B -Dit.test=B2AdmissionIT verify`，首次执行通过：40个unit、9个接收IT，失败/错误/跳过均0，23:29:15 +08:00完成。
随后`./mvnw -B clean verify`完整通过：40个unit、31个IT（6个C0、16个B1、9个B2），合计71项，失败/错误/跳过均0；23:29:44 +08:00 BUILD SUCCESS。
本机真实命令另带`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`，JAVA_HOME指向Temurin17、MAVEN_USER_HOME=/private/tmp/12times-maven-user-home；仅下载环境适配，不作为交付隐式要求。完整回归允许127.0.0.1随机监听；本次B2用例自身不启动HTTP。
真实日志：`/private/tmp/12times-qa-b2-admission-target.log`、`/private/tmp/12times-qa-b2-admission-verify.log`。未发生编译/测试失败，没有为通过降低断言。

## 已通过的9项验收

| 用例 | 独立证据 |
| --- | --- |
| 两表实际写入后异常回滚 | 装饰真实Mapper，在第二次INSERT返回后抛异常，确认两SQL都实际执行，再独立连接验证文档/任务/关联数均0 |
| 接收锁覆盖物理提交与最后名额 | 装饰真实事务管理器，在首笔commit前暂停；ThreadMXBean确认第二线程确实BLOCKED、owner为首线程且栈在submit；此时第二getTransaction未发生，另连接看不到首笔两表；释放后事件严格为首BEGIN、首COMMIT进入、首COMMIT结束、第二BEGIN；最后仅1笔接收 |
| 12个并发重复ID | barrier同时释放12请求；一个接受、11个409；UUID合法，获胜原文与持久数据一致，两表各1行无孤行 |
| 文档总量 | 已FAILED且已失效夹具仍占唯一总文档名额，pending为0仍拒绝新接收429 |
| 待处理容量 | 已RUNNING且已失效夹具仍占pending名额，拒绝新接收，保持两表一致 |
| 提交后抛错且核对成功 | 真实物理commit完成后抛TransactionSystemException；确认确实用本次UUID查询，返回接受且只保存1笔，版本为现有冻结版本 |
| 提交后抛错且核对失败 | 真正提交后确认查询再报数据库异常；返回503 RESULT_UNKNOWN，记录仍存在，门禁关闭，下一次接收SERVICE_NOT_READY且不新增行 |
| 外层事务 | 使用真实外层事务调用接收，明确SERVICE_NOT_READY，不借外层提交，两表0行 |
| 旧RUNNING同库重启 | 专用文件库完整关闭/重启，旧状态、token与attemptCount=7不改，新接收拒绝；不把此用例称为恢复成功 |

所有数据库均为JUnit临时绝对文件路径；每次快照直接统计两表及关联数，避免JOIN隐藏孤行。受控等待有界，无sleep猜时序；线程结束后才关闭应用和数据源。

## 未执行边界

本轮仅完成接收前段；管理HTTP、正文chunked、Origin、领取/扫描/饱和/handler事务外待后端后段合入后执行，状态NOT_RUN。B3终态两表写回、旧token迟到完成、失败终态、运行任务恢复、完整停止、B4搜索计数以及UI/部署仍NOT_RUN。
提交未知测试证明接收组件当前的保留记录与停止新接收语义，不替代B3完整在线降级和恢复验收。伪造任务状态仅用于容量/重启门禁夹具，不声称生产状态转换已完成。
