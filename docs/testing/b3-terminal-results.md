# B3终态前段独立验收

实测代码基线52c92de27f888871cc073b85d3ae6e79e61949cb；agent/qa，Java17。

## 真实执行

首次目标命令未升级监听权限，已有HTTP unit遇SocketException Operation not permitted，67个unit中3错误，B3 IT未执行；原日志/private/tmp/12times-qa-b3-terminal-target.log保留，未改断言。
允许本机监听后目标`verify -Dit.test=B3TerminalIT`通过16个IT，00:47:44 +08:00完成。随后随主控健康基线迁移阶段元数据，完整clean verify于2026-10-23 00:48:22 +08:00通过70 unit + 63 IT = 133项，失败/错误/跳过0。
实际Maven额外带`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`，JAVA_HOME=Temurin17，MAVEN_USER_HOME=/private/tmp/12times-maven-user-home；仅本机下载适配。最终日志/private/tmp/12times-qa-b3-terminal-verify.log。

## 16个IT证据

- 真实ProductionTaskExecutionHandler与冻结B1：空文/ababa两种原独立黄金SHA保持，1024字节向量、版本及两表完成时间一致。
- 第二表写前异常、写后异常、返回0行三种故障：任务仍RUNNING、无完成时间或向量；整体回滚且非暂时错误不盲重试。
- 旧token迟到成功/失败均STALE；新token成功后相同内容ALREADY_APPLIED，首次时间/完整向量不变，不能改写FAILED。
- 真实B1计算完成后latch暂停，失效先提交再完成；最终成功且失效，首次失效时间/原文/大计数保留。
- VERSION_MISMATCH、INPUT_INVALID、COMPUTATION_FAILED三种稳定分类可靠FAILED；无向量，码/消息限额，异常secret/SQL不泄露，失败重放幂等。
- 真实commit后抛事务异常：精确匹配成功确认、相同1024长度但内容不同、确认查询失败三种场景；只执行一次终态更新，确认失败降级，不能反写FAILED。
- 明确回滚暂时故障前两次失败第三次成功，以及三次耗尽两个场景：更新尝试均3，耗尽保留RUNNING并关闭接收，不假FAILED。
- 关闭写许可后迟到成功/失败均WRITE_CLOSED，Mapper调用0次，持久任务仍RUNNING。

测试以明确B2组成只准备RUNNING夹具，直接驱动真实终态/handler；无全局禁用生产handler，自动生产装配、恢复/强杀和完整停止另测。故障仅测试JDK代理和真实事务装饰，不改业务、公共DTO、DDL、pom或B1黄金。
终态门禁和写许可组件证据不替代完整生命周期。真实生产恢复/四检查点强杀/SIGTERM/超时不再H2等后段仍NOT_RUN，B4及UI/正式部署未执行。
