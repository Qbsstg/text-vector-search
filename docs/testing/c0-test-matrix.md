# C0 验收矩阵与真实结果

基线：`0f692387577be629d296d3a5fe0ba626072f93b4`；分支：`agent/qa`。
日期：2026-10-22。测试仅验证C0已实现内容，不将未来业务记为失败。

## 复演命令

使用JDK17，在独立工作区执行：

```sh
./mvnw -B clean verify
```

Surefire执行`*Test`；Failsafe在`integration-test`与`verify`阶段执行/判定`*IT`。
真实本机执行使用Temurin17，并增加`-ntp`、本机临时Maven settings及缓存覆盖；这些是受限本机依赖下载适配，不是交付运行要求。结果如下，报告位于`target/surefire-reports`和`target/failsafe-reports`，报告不提交。

## 已执行并通过

| 范围 | 用例/数量 | 证据与通过含义 |
| --- | --- | --- |
| C0构建 | `clean verify` | BUILD SUCCESS，2026-10-22 21:17:20 +08:00；耗时4.819秒，仅为本次构建记录 |
| 既有DDL单测 | 2个Surefire测试 | DDL重复执行及文件连接重开保留数据；拒绝悬空任务、非法向量长度 |
| T01/C0应用重启与健康 | 1个IT | 生产SpringApplication、随机HTTP端口、临时绝对文件H2；两次真实GET /health均为UP/C0/businessReady=false；完整关闭第一应用及连接池后再启动，文档原文、超过JS安全整数的hitCount、关联QUEUED任务及版本保留 |
| C0生产配置默认值 | 包含于上项 | 生产配置扫描得到13个契约默认值；未手工构造被测配置bean |
| C0配置覆盖 | 1个IT | 对全部13项用非默认值启动生产应用，比较实际绑定bean |
| C0非法配置拒绝 | 3个参数化IT | queue-capacity=0、worker-count=17、max-text-code-points=20001；生产SpringApplication启动失败，异常链必须含BindValidationException |
| C0 DTO | 1个IT | 生产Jackson mapper；UTC ISO时间、字符串hitCount保留大整数、任务枚举、独立vectorReady/invalidated、可空字段稳定null、DocumentDetail往返；空文本/null文本仅检查反序列化区别，不声称业务校验已实现 |

最终：Surefire **2通过**；Failsafe **6通过**；均无失败、错误或跳过。
首次未升级权限的verify因沙箱禁止socket监听（Operation not permitted）导致HTTP项启动报错，其余5项IT通过；授予本机监听权限后重新执行完整clean verify得到上述最终结果。没有削弱断言。

## 未执行/待实现

| 编号 | 当前结论 | 后续层次与门槛 |
| --- | --- | --- |
| T02—T04 | 未执行 | 黄金向量/codec/Top-K独立对照单测；向量增加独立JVM一致性 |
| T05—T11 | 未执行 | 真H2、HTTP、latch/barrier控制提交/领取/完成/失效/计数/容量竞态；禁止sleep猜顺序 |
| T12 | 未执行 | 独立JVM阶段信号后强杀，确认旧进程退出再同库恢复；本轮正常关闭不是崩溃恢复 |
| T13 | 未执行 | 区分明确回滚、提交后抛异常、无法确认；不能用累计hitCount推断某次未知提交 |
| T14 | 未执行业务验收 | 参数/正文/容量/忙碌响应；本轮仅验证配置启动约束 |
| T15、UI01—UI18 | 未执行 | 真实浏览器和API；网络故障/轮询停止/不自动重试/原文XSS/正式包无mock |
| T16 | 未执行 | 单实例、固定持久路径、认证、版本与备份恢复；本轮未登录服务器 |
| T17 | 未执行 | 混合负载、热点与资源实测，无性能结论 |
| T18 | 未执行 | 新目录/新机器仅按README独立复演 |

## 隔离与解释边界

- 测试仅持有自己的JUnit临时绝对目录；重启用例两次启动共享该用例路径，其他用例分离。
- HTTP绑定127.0.0.1随机端口；其他配置/DTO用例使用非Web生产应用，不争用端口。
- 直接JDBC写入仅用于基础设施哨兵，不证明业务提交接口、恢复、run_token或资源限制有效。
- SQL初始化随应用启动执行，两次完整启动保留两表数据；没有在测试中替换生产DDL。
- 当前无Mapper警告是C0尚未实现业务的现状，不伪装成业务已就绪。
- 主控后续DDL、配置、DTO改动及合入后必须重新运行；本报告不代表后续SHA通过。
