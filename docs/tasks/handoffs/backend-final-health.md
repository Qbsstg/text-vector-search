# 最终业务健康后端交接

日期：2026-10-23。后端工程师，gpt-6.1-sol / high。

## 基线与范围

- 工作树：`/Users/qbss/code/12times/.worktrees/backend`；分支：`agent/backend`；起点`cd400f57406d362fb8b672e24b0ea32a74e7fc4d`。
- 按最新AGENTS及final-readiness-release-contract实施，只做内部健康聚合、调度只读状态和自有unit。先向主控提交接口/构造器方案，获批后编码。
- 代码提交：`5377542997376d2c0559e00a315d52e4645bd17b`，`feat: 聚合只读业务健康事实与调度可用状态`。
- 未改Controller、任务状态机/恢复/关闭顺序、B1算法/版本、搜索实现、DDL、公共六业务DTO、依赖、资源参数或基础配置。没有服务器操作、部署或凭据处理。

## 接口与组件

`health.BusinessReadiness.inspect()`返回内部`BusinessReadiness.Snapshot`记录，字段顺序与冻结契约一致：

```text
TaskReadiness.State taskState
boolean taskReady, databaseReady, databaseWritable, dispatcherReady,
        algorithmReady, searchReady, routesReady
String embeddingVersion
```

`businessReady()`完整合取READY、taskReady和全部检查布尔；没有浏览器、CI或人工授权的硬编码运行旗标。embeddingVersion来自统一TextEmbedder.version()；异常/不可用可为null，同时algorithmReady=false，Controller须用允许null的结构或直接序列化，不能制造占位算法版本。

默认`health.DefaultBusinessReadiness`构造器：

```text
(DataSource, TaskReadiness, TaskDispatcher, TextEmbedder,
 VectorProperties, ApplicationContext)
```

构造器只保存依赖，不启动探测或业务自测。主控持有Controller及公开stage B4/HTTP状态/checks映射，不在本提交中修改。

## 只读检查与关闭边界

- 初始RECOVERING、DEGRADED、STOPPING、STOPPED均不借数据库连接；健康失败不改变TaskReadiness，不自动恢复或修复。
- READY阶段进入共享TaskWriteBarrier后重查READY，连接有效性isValid(2)、两表全部必需列的WHERE1=0查询、Connection/DatabaseMetaData只读标志均通过才具备数据库条件。查询超时2秒；不取用户文本、不写数据、不提交、不做DDL或计数。
- 关闭连接后释放共享许可，再取最新任务状态/屏障/调度事实重算taskReady；关闭不能穿越已进入的探针短读销毁数据源。健康是本次观察快照，不承诺响应送达后状态永不变化。
- TaskDispatcher仅增加只读`executionAvailable()`：运行中、存在处理器、未closed、Worker及scanner未关闭/终止。没有调整状态机、扫描、领取、关闭逻辑；队列满不当作设施损坏，继续沿用429。
- 算法要求唯一TextEmbedder Bean与注入实例一致，版本匹配`char12-sha256-r<配置轮数>-nfkc-root-ws-v1`。只读version()，不调用embed，不碰黄金或计算规则。
- 搜索要求实际SearchService Bean存在；路由从RequestMappingHandlerMapping检查冻结六方法/路径确实装配，缺少任一项businessReady=false。taskReady不依赖搜索/路由，businessReady额外合取它们。
- JDBC只读标志检查不能保证未来磁盘写入永远成功；真实写路径、数学黄金、任务/搜索闭环仍以独立验收证据为准。

## 真实验证

提交前使用：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 \
  -Dtest=DefaultBusinessReadinessTest test
```

显式import整理后重跑，2026-10-23 **02:25:30 +08:00**结束，**BUILD SUCCESS：9项通过，0失败/错误/跳过**，随后才提交代码。日志：`/private/tmp/12times-final-health-unit.log`；报告在本树target/surefire-reports，不提交。

9组覆盖：真实生产Web六路由完整就绪且文档/任务0写入；探针不计算/不改状态；非READY各阶段0借连接；连接或metadata报告只读均不就绪；失效连接/缺必需列失败；缺Search Bean/任一路由失败；算法版本或唯一实例不一致失败；dispatcher启动/无handler/关闭只读观察；DB探针与停止交错时关闭屏障等待连接结束、返回最新STOPPING而非迟到成功。只使用专用临时H2与127.0.0.1随机端口，latch/有界状态等待，不以sleep证明时序。

提交前diff --check通过，TaskDispatcher仅6行只读方法增量。旧B2隔离组成没有生产handler，最终健康应503；健康元数据/组成迁移由主控/QA审查，保留基础DB/文件持久化和原业务断言。本角色未在已知待迁移的旧健康断言下重复运行全量，待Controller/QA合入后统一回归。

全量Java/Node、独立健康/备份恢复、真人浏览器、不可变CI候选与正式DEP3发布不在本角色上述9项目标结果内，未执行不提前PASS。用户已明确公开仓库，未擅自回退私有；本轮未执行远程发布。完成后等待主控审查。
