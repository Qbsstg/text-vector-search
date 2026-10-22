# 本地新增独立用例实际执行记录

任务FINAL-RELEASE / QA；分支`codex/final-release-qa`，基线`da5867a5ecdb5ae4705e3643e3e96298b9bd7574`，生产源码未改，批准发布包仍为d016候选。全部70条来源/预期/方法映射见`final-release-local-cases-matrix.md`。主树原始10文件与各角色工作树未改。

## 真实执行

在Java17下执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp -DskipTests test-compile
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp '-Dit.test=LocalCasesIT#旧版本成功文档真实HTTP过滤诊断不混算' failsafe:integration-test failsafe:verify
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp -Dit.test=LocalCasesIT failsafe:integration-test failsafe:verify
```

最终完整新增类：**12 tests，0 failures，0 errors，0 skipped**，测试耗时5.512秒，Maven总耗时6.570秒，完成时间2026-10-23 04:50:49+08。此前完整12项也通过；为新增REC-07原断言/HTTP响应留证复跑，不将两轮重复累加。9条原资料内容摘要核对一致，01文件仅路径改名。

这12项不是70条全部通过；部分原用例复用既有方法，由主控本轮完整回归核证。旧CI的253项不自动算本轮新增用例已过；12个新增工程执行项与夹具子样本不重复累计。Server/Browser/PERF仍有独立实际验收范围。

逐方法名称、时间与原始报告/日志摘要见`final-release-local-cases-evidence/execution.json`。脱敏子样本断言结果及8组OBS真实Java分数完整保留在同目录，不报准确率或语义必过门槛。原始Maven XML留在QA树target/failsafe-reports，原始控制台留在/private/tmp；预期坏路径测试的H2启动异常属于已断言故障，不是未处理的业务失败。

## 保留的失败与修正

- 初次test-compile错误是新增测试误用DocumentRow字段；已改用实际status/vectorLength及独立JDBC读取向量，未修改生产或降低断言。原日志`/private/tmp/12times-local-cases-compile.log`保留；后续编译通过。
- 首次REC-07诊断运行因沙箱阻止随机回环Tomcat监听而1 error，根因SocketException/Operation not permitted；原日志`/private/tmp/12times-rec07-diagnostic.log`保留。按已授权隔离HTTP测试提升后1/1通过观察断言，随后全类12/12通过。该环境失败不归业务缺陷。
- REC-07原预期实际**FAIL/契约冲突**：真实R32成功库正常停止后同目录R33启动，health200/businessReady=true，搜索200空hits；冻结B4双版本候选过滤成立，却与原“版本不兼容时阻止就绪”不同。独立记录`REC-07-wire.txt`及`REC-07-original-assertion.txt`保留真实响应与原断言错误。JUnit绿仅表示观察诊断成立，不能写原REC-07 PASS，等待用户明确裁决；不添加会故意拖垮普通CI的原断言失败测试，不改冻结生产语义。

## 范围与审查

新增测试仅在`src/test/java/**/acceptance/LocalCasesIT.java`、已审必要合成数据在`src/test/resources/local-cases/`，不新增生产端点、依赖、DDL、API或参数。每项独立@TempDir绝对文件库；HTTP端口0/127.0.0.1；受控算法只用于异步/失败/数学场景，真实SQL/事务不被替换；latch有界且finally释放。三失败分类原有9unit/16IT继续保留，本次受控FAILED HTTP不冒充F05真实Chrome。

提交前`git diff --check`与新增范围私钥/凭据模式扫描无匹配；夹具只有16篇合成文本、数值oracle与合成请求，不含用户业务库或认证材料。原始用户文件未暂存。主控仍需合入后完整Java17 verify、Node及Linux CI，不能用本报告代替合入阶段检查。
