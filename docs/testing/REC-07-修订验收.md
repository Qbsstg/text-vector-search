# REC-07-R1修订验收实际记录

任务FINAL-RELEASE / QA，裁决`DEC-REC07-20261023-01`。已读用户直接回复经主控记录的`docs/design/REC-07-验收判据裁决-20261023.md`：用户选择保持冻结B4双版本过滤，正式批准修订REC-07判据，旧材料、旧预期、历史FAIL及全部执行证据永久保留。本文件不改原70条材料或矩阵，也不覆盖旧`LocalCasesIT`诊断。

## 版本和范围

独立分支`codex/rec07-revised-qa`从主控`0888d06d2ab5e5091294550f7d0d0ddb33d3812d`创建，旧QA分支保留，无reset/rebase/amend。仅新增`Rec07RevisedIT.java`和本文；生产/API/DDL/B1/B4/状态机、依赖、Wrapper与旧诊断均未改。生产候选仍为d016完整SHA与64bc9c0b…原JAR；本次是同生产源码的真实Spring/HTTP/H2组成测试，不把新测试提交或R33测试配置当新的批准生产包。

两方法均使用@TempDir独立绝对H2文件目录与随机回环端口。一个方法内先完整关闭R32，再顺序同库R33启动，只有一个JVM、每个文件库同时一个组成。实际读取并断言worker1、queue64、pending1000、maxDocuments5000、pool4、searchPermit1、scan500ms、batch32、shutdown30秒及原文本/请求/TopK/分页边界。只有32/33 CPU算法版本、测试临时路径、测试账号与随机回环端口属于隔离测试配置，没有修改生产参数。

## 新判据实际结果

**REC-07-R1：PASS（2个新增JUnit执行项）**，不替换历史REC-07原判据FAIL。

1. `R32真实旧库经R33顺序重启全字段不变且当前业务可用`：三篇真实HTTP提交且由冻结B1异步成功，包括超过摘要长度的Unicode原文、零文与已失效文档。用真实搜索和失效路径产生非零计数/首次失效时间。对每篇文档全部10列、任务全部11列逐列快照；原文用完整UTF-8 SHA/字节数/code point数，向量用完整1024字节SHA，未用摘要前缀代替全文。

   正常关闭后同路径R33启动，只有旧版本数据时health真实200、taskReady/businessReady和六checks全真，算法版本真实R33。旧版本任务/文档版本、run_token、attempt、全部时间、状态、失效、计数和完整向量/原文摘要逐项相等；旧记录没有迁移、重算或重领，仍可详情/任务GET。旧版本搜索空hits而非错误健康。随后真实HTTP提交R33新文、真实B1成功，搜索只返回当前新文并计数1；旧三行在新业务后仍全字段等于R32原快照。

2. `文档任务双版本四组合在TopK前过滤且只计合格行`：另一独立库只构造明确合成夹具，覆盖文档当前/任务旧、文档旧/任务当前、两旧、两当前。仅修改本方法fixture版本字段，绝不修改上一个方法真实旧数据。三个应排除对象都使用真实查询向量方向的1分人工夹具；唯一双当前合格行是0分零向量。真实HTTP K=1仍返回该0分行，K=100也只有它，证明先资格过滤后Top-K且不加阈值。三个排除行计数0且全字段不变，合格行两次各+1。此为资格数学夹具，不冒充旧业务向量的真实生成结果。

健康非就绪/关闭的负例不因旧数据存在而改变：本轮另真实运行`FinalHealthIT`和`B3LifecycleIT`，保留非READY/停止/缺组件/数据库异常503、恢复前拒接与短事务关闭屏障等原断言；没有用一条READY正例代表所有健康分支。

## 真实命令、数量及初次环境失败

Java17执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp -DskipTests test-compile
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp -Dit.test=Rec07RevisedIT failsafe:integration-test failsafe:verify
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -B -ntp -Dit.test=Rec07RevisedIT,B4SearchIT,FinalHealthIT,B3LifecycleIT failsafe:integration-test failsafe:verify
```

最终关联运行于2026-10-23 09:40:46+08完成：Rec07RevisedIT **2**、FinalHealthIT **13**、B4SearchIT **6**、B3LifecycleIT **5**，合计**26，零失败/错误/跳过**，Maven总耗时7.591秒。新增数为2，关联24来自既有工程，不能把2＋26重复累计，也不能把本次直接写成完整回归已过。主控接着运行完整verify/Node/Linux CI并核证。

首次新组成复用旧B4帮助类时，帮助类固定50ms和测试恢复500ms的重复CLI合并为`50,500`，导致两个环境绑定errors，尚未启动业务；原日志`/private/tmp/12times-rec07-r1-run.log`与`target/rec07-revised-evidence/first-environment-failure.xml`保留。这是新测试环境问题，不是产品FAIL。改为新类内独立最小生产环境后2/2通过；补全冻结资源实际断言后最终26/26通过。没有改旧helper/生产或放宽R1断言，首次errors不计入最终成功数。

## 原件与交接

独立原件在`target/rec07-revised-evidence/`，每次以新UUID子目录追加，包含R32关闭前、R33启动后及新业务后三份21列快照、当前真实健康、四组合前后、冻结资源、各自结论及verification-summary。只写新目录，不覆盖旧REC-07文件名或旧原件。失败与最终原始控制台分别在上述run.log及`/private/tmp/12times-rec07-r1-related-final.log`；真实JUnit XML在`target/failsafe-reports/`。原件只在本地/CI附件保存，本提交不把日志、原文、向量、数据库或机器元数据批量推公开仓库。

提交前`git diff -- src/main pom.xml mvnw mvnw.cmd .mvn`及旧`LocalCasesIT.java`均为空，表明冻结生产与旧诊断未改；新增文件空白及有限私钥/token/访问key格式扫描无匹配，不宣称能识别任意秘密。QA未改公共矩阵/CI/服务器配置，未操作正式服务器或Chrome、未执行全量271项。主控负责按裁决更新有效验收口径与历史FAIL的分层统计、完整回归及正式发布GO。
