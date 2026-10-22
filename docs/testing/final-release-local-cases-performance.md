# 本地同组件性能探针实际记录

范围：PERF-02/03组件分阶段测量与隔离真实锁超时恢复；不是生产HTTP trace，不替代主控独立Mac客户端/Linux固定JAR的CPU/RSS/GC实验。源基线da5867a，测试提交276b9ad之后仅新增手动探针，无生产修改/参数变化。

## 执行与环境

新增`LocalPerformanceProbe.java`不匹配Test/IT默认发现规则；显式main运行，无新依赖。Java17先执行`./mvnw -B -ntp -DskipTests test-compile`，再从最近真实Failsafe报告的java.class.path提取测试运行classpath到临时文件，执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17)
"$JAVA_HOME/bin/java" -cp "$(cat /private/tmp/12times-performance-classpath.txt)" dev.twelvetimes.vector.acceptance.LocalPerformanceProbe /private/tmp/12times-local-components-results.json
```

首次内联环境变量导致/bin/java不存在，尚未启动实验；日志`/private/tmp/12times-local-components-run.log`保留。纠正为独立变量赋值后退出0，原日志`/private/tmp/12times-local-components-run-final.log`。测试数据只在新临时H2目录，已全部正常关闭且保留文件，未删除。没有启动HTTP监听/Chrome/服务器。

环境：17.0.15+6；Mac OS X aarch64；可见逻辑CPU=10；算法=char12-sha256-r32-nfkc-root-ws-v1。开始2026-10-22T20:53:58.408762Z；结束2026-10-22T20:54:01.005284Z。同JVM分阶段组件计时，应用/驱动共享本机资源，不能当独立负载客户端实验。

每N独立库，预构造N个成功文档，8种合成原文各使用对应真实B1向量；准备批量事务不计时。N=100/1000/5000、K=1/10/100，各先预热1次再保留3次，总27条正式样本。读候选使用真实SearchCandidateReader，评分含decode并先物化全部分数，用真实TopKSelector与SearchHitCountService。物化改变对象生命周期，不冒称生产实现内存/HTTP各阶段。

## 分阶段数据

下表单位ms，为每组合3条正式样本中位数，仅描述本次测量；原始nano逐条保存在`final-release-local-cases-evidence/component-performance.json`。无QPS/p95 SLA或性能优劣保证。

| N | K | 查询向量化 | 候选读取 | 解码/评分/物化 | Top-K | 计数含真实commit |
| --- | --- | --- | --- | --- | --- | --- |
| 100 | 1 | 0.3402 | 2.7541 | 0.3840 | 0.0367 | 0.7733 |
| 100 | 10 | 0.1837 | 1.8005 | 0.3560 | 0.0305 | 2.0640 |
| 100 | 100 | 0.1719 | 1.3159 | 0.0873 | 0.0970 | 7.8898 |
| 1000 | 1 | 0.2061 | 5.3590 | 0.8482 | 0.0819 | 0.5128 |
| 1000 | 10 | 0.1490 | 4.0810 | 0.8648 | 0.1027 | 1.2819 |
| 1000 | 100 | 0.1655 | 4.0454 | 0.9328 | 0.1781 | 3.4435 |
| 5000 | 1 | 0.1643 | 19.8957 | 3.4694 | 0.6158 | 0.5834 |
| 5000 | 10 | 0.1607 | 14.1604 | 3.4173 | 0.6301 | 0.7553 |
| 5000 | 100 | 0.1639 | 8.6629 | 2.9533 | 0.4213 | 2.3771 |

## 热点、分散与真实锁超时

每组4线程×20调用=80个真实短事务；Mapper装饰器记录SQL更新耗时（包含锁等待与执行，不能拆称纯等待），事务管理器装饰器单独记录真实commit耗时。统计不读取累计值确认单个未知提交；没有未知时才按返回明确成功事务数核对持久增量。

| 场景 | 尝试 | 明确提交 | 未知 | 其他错误 | 持久增量 | 总耗时p50 / p95 ms | SQL含等待p95 ms | commit p95 ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| hotspot | 80 | 80 | 0 | 0 | 80 | 0.6218 / 1.5771 | 0.1831 | 0.4885 |
| distributed | 80 | 80 | 0 | 0 | 80 | 0.5936 / 0.8038 | 0.1247 | 0.3202 |

p50/p95按80条原始样本最近秩ceil(q*N)-1取值；本次两组全部明确提交，最终增量均80。样本量小且非独立HTTP环境，不能仅凭该差值断言生产热点瓶颈。

独立JDBC事务锁住目标行，测试库H2 LOCK_TIMEOUT=200ms；真实搜索262.460ms后返回DATABASE_UNAVAILABLE，已确认失败增量0。释放并rollback测试锁后，同一SearchService再次检索成功、只增加1，证明该异常出口的搜索许可可复用，readiness=READY。收尾TaskDispatcher在途0；该组成无后台处理器，不将此观察夸大成压力下Worker恢复证明。

该测试不破坏数据库文件、不改生产运行参数、不诱发提交未知、不调用真实服务器；无SLA阈值。PERF-02/03的固定JAR HTTP全链路、CPU/RSS/GC及混合负载仍交主控/服务器现场实测。PERF-04原8OBS分数仍完整保留，无准确率结论。

原始JSON SHA256=11cf006a583d318459cd2d6eac8e47fe39ccfb1182cfd1115fb1f595504f862a；原始日志SHA256=aaf969d734d941c383dd1d3e3a0d316c102bd11538a924bd2c16ee119cc4dc77。提交前diff空白/私钥凭据模式扫描通过；探针为手动实验，不增加默认JUnit测试计数。


## 冻结连接池4的最终对照（新增运行，保留前次原样本）

前述`component-performance.json`及提交`3f972a2`的全部原始样本保留不改：其分阶段N/K环境使用默认池4，但计数争用环境曾显式使用池8。本次将计数池恢复为冻结4，并在实际Hikari实例读取/断言maximumPoolSize=4。故障URL同时保留`WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE`，只额外使用`LOCK_TIMEOUT=200`作为专用隔离故障注入参数，不改变生产配置或任何原用例语义。

本次源码由3f972a2后续独立中文提交承载，非amend。先Java17 `./mvnw -B -ntp -DskipTests test-compile`通过，再使用相同测试classpath执行新输出路径：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17)
"$JAVA_HOME/bin/java" -cp "$(cat /private/tmp/12times-performance-classpath.txt)" dev.twelvetimes.vector.acceptance.LocalPerformanceProbe /private/tmp/12times-local-components-pool4-results.json
```

实际退出0；原日志`/private/tmp/12times-local-components-pool4-run.log`，新原始JSON另存`final-release-local-cases-evidence/component-performance-pool4.json`，不会覆盖前次记录。新运行仍有27条N/K阶段正式样本，每组合预热1、正式3；热点/分散各4线程20次。实验顺序运行，单独行锁场景不与4线程计数组同时持连接，因此不需要扩大连接池。

| 场景 | 连接池 | 尝试 | 明确提交 | 未知/其他错误 | 持久增量 | wall p50/p95 ms | SQL含等待p95 ms | commit p95 ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 前次pool8/hotspot | 8 | 80 | 80 | 0/0 | 80 | 0.6218/1.5771 | 0.1831 | 0.4885 |
| 前次pool8/distributed | 8 | 80 | 80 | 0/0 | 80 | 0.5936/0.8038 | 0.1247 | 0.3202 |
| 本次pool4/hotspot | 4 | 80 | 80 | 0/0 | 80 | 0.6153/1.4809 | 0.2384 | 0.3925 |
| 本次pool4/distributed | 4 | 80 | 80 | 0/0 | 80 | 0.4667/0.6473 | 0.0750 | 0.2458 |

本次锁超时实际261.069ms后DATABASE_UNAVAILABLE，明确失败delta0；释放锁后同一搜索实例再次成功delta1，许可可复用，READY、在途0。开始2026-10-22T20:57:31.280583Z，结束2026-10-22T20:57:33.905187Z。两轮都有小样本/预热/本机JIT和调度差异，不以表中变化证明池大小的生产性能因果关系或SLA。

新JSON SHA256=e81accb7631b09a424ea92413c3582827a74e2fd7dd9ed828bb0155599f7113c；日志SHA256=8d4f12045eb23191e43c62aeb91f98b217dff3539e3d3bd0485090be36d68a4a。新探针非默认JUnit，CI显式main的实验数不计入JUnit；其余原始70条语义及REC-07待决状态未动。
