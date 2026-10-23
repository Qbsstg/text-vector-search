# API-FINAL-VERIFICATION 隔离原题与API核证

## 当前QA收口快照

**隔离根因/候选工程/Mac与Linux完整35分钟及兼容子范围PASS，FINAL_ACCEPT仍BLOCKED。** 生产仍旧2764/b8b，988/becb未获替换批准，完整正式UI仍有NOT_RUN。下方282原执行及中期“尚未收到/仍RUNNING”均为历史快照，不用它们覆盖本节最新证据。

- 新候选实际9883400451c9b2a22d13134cbd698d7478851b3d / becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece / CI39927516112；QA独立核本地与CI105 Unit+126 IT+52 Node=283。
- Mac主report实际hash `9354a3db7ee13148f83124f9d1fc70b0bef9bccfcfe32cba544579dde4a34fd8`，2104.004秒，492业务事件、35文档、1正常重启、0写重试；启动探测另34，总526。conn0自然maxLifetime回收后继续真实业务成功；初始/重启日志均正常关闭。该层核原文/元信息/计数，不声称raw前后快照。
- LinuxOwner安全回执实际hash `759c08571749fdf1d7aa41115742f426cc7562e2574bc5aaf97fddbbe9b52bd3`：固定988/becb，2123.218秒、35篇、482业务请求+357启动健康=839总请求、1正常重启、0写重试。conn0在17:27:04.299自然maxLifetime回收后业务继续到35分钟；两次Hikari正常关闭、隔离进程/端口/文件占用释放。JFR共8个SQL类异常含启动探测，原90098/23514业务根因事件0，不能说完全无异常。Linux隔离资源另有限制MemoryMax1536M、CPUQuota100%、Xms256m/Xmx1024m；生产仍旧2764，未因试验改生产配置。QA读取安全摘要及hash，不声称下载完整服务器原日志。
- 后补停机新冷副本raw向量35篇，共35840bytes与冻结B1重算`Arrays.equals`，TSV hash `76e82ff7ff1cf80713d3608d00fdd18a612c471a20862f6f1c8f289448fdaaf7`；源库hash不变，仅副本只读。该层不倒写为35分钟前后raw比较。
- 22旧文档/22旧任务升级兼容三快照实际bytes相同，hash `a2f799e4e135b48acdc2d2cc139687427bec8757aa0c9b3806ea92e023f29d2b`；114请求/1重启/2新文档。禁止将此PASS理解为2.5可降级给2.4。
- 正式HTTPS Owner安全聚合109请求、6新文档、unknown0/writeRetries0；QA实际核聚合/hash，不冒称QA读109原始trace。Chrome另2篇，实际B原响应RUNNING→SUCCEEDED、网络响应后2.004228秒再GET；主控完整UI报告26个已执行子范围与NOT_RUN列表分开。
- 五个误搜索hits与ledger集合逐项一致、五delta=1，原旧33严格不变FAIL永久保留；用户明确批准新baseline，17:26:52首次及17:42:39全部补证后的新33摘要末检diff[]为独立ONLINE_PASS。线上仅API摘要，不与隔离vector字节核证混淆。详见70映射最新表及安全原件hash。
- 入口拒绝另11请求安全聚合hash `3b2db13f203d6cfe43196dc018fbd531467f52a8dfc15e9daab74331652b7129` 已逐条核对：六业务无认证401、错误Basic401、跨源提交/搜索403、actuator404、loopback健康200；0新文档/0成功业务搜索/0写重试，额外预算累计28/32，独立于109。原109本身没有403，未据旧聚合扩大结论。七静态资源及三种条件GET的十条hash还逐条与下载988候选JAR条目离线比对相同，仅证明静态前端相同，不代表新JAR已上线。
- 真正最终安全receipt hash `4296db5cf1b0687c03b290b309d6340ea47132eda5a1528464aa15d24c0dc11e` 已实读/实算：用户新baseline的旧33摘要0差异、原strict FAIL保留、8篇账本完全相符。QA逐ID比较该8篇hitCount/invalidated与先前17:26:52安全receipt完全一致；新增1次list无写/无有效搜索，额外预算终态29/32，已停止生产HTTP。当前生产仍2764/b8b、原进程和锁未改，988未部署。
- QA只读抽看320弹窗和失效历史截图：提交/取消在图中可达，历史2行和原文标签按文本显示；没有据截图宣称读屏、物理手机或未测轮询通过。没有操作浏览器或生产请求。

## 282首次实际执行及中期复核历史（原样保留）


日期：2026-10-23。QA基线/受测源码`46ebe7d9b29a616721a66a10c9ce65d89fc2e33a`，分支`codex/final-acceptance-qa`。与生产2764候选的`src/main pom.xml .mvn mvnw mvnw.cmd`实际git diff退出0。本轮没有新增测试来凑数量，也未改生产实现或既有断言。

**隔离工程：PASS（282项）；FINAL_ACCEPT：BLOCKED。** 根因和35分钟稳定性未真实过关，历史503保持FAIL。此文不充当生产HTTP或浏览器报告。

## 实际命令与环境

Java实测Temurin17.0.15+6；Node实测v22.16.0。独立QA worktree运行：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
-Dmaven.repo.local=/private/tmp/12times-m2 clean verify
/opt/homebrew/opt/node@22/bin/node --test tests/frontend/*.test.mjs
```

`-s`和`-Dmaven.repo.local`为本机已授权镜像/依赖目录附加参数，未伪称不带参数的标准命令。隔离测试用临时绝对H2目录、随机回环端口，真实子进程强杀/正常退出只作用测试自己创建的进程；无生产库、生产端口或线上压力。

首轮Java实际执行104项：0 failures、7 errors、0 skipped，7项均Socket.bind Operation not permitted导致Web组成启动失败；Failsafe未运行。原XML与日志保留在`/private/tmp/12times-final-acceptance-qa-first-bind-failure/`，没有用最终报告覆盖。自动审批允许本机隔离回环后，同命令从clean完整重跑，BUILD SUCCESS，2026-10-23 16:22:23+08:00结束，56.363秒。

最终XML逐组实计19组104 Unit、16组126 IT，失败/错误/跳过均0；Node52项，失败/取消/跳过/todo均0。Java230+Node52=282；首轮、关联方法、参数化样本或映射主编号不另加。

原件保留本地：`/private/tmp/12times-final-acceptance-qa-verify-final.log`、`/private/tmp/12times-final-acceptance-qa-node.log`、本worktree的`target/surefire-reports`与`target/failsafe-reports`；公开只保存聚合与摘要，不提交日志/请求/截图/数据库。

| 原件逻辑名 | SHA-256 |
| --- | --- |
| 首次绑定受限日志 | `a11eb2edde5959a66d83f4a4c72efc25aba1ec632b56e8cb128197705ffe266c` |
| 完整重跑日志 | `27f5debe011f6b36575cdd998578ecebc07db82d0b6c9185c050a618ac262a23` |
| Node22全量日志 | `34b394d0dd7418046a0242605327a0aed0c4583a71e2041ee22fa3323f170946` |

## XML逐组实际计数

| 阶段 | 测试类 | 执行项 | 失败/错误/跳过 | 原XML SHA-256 |
| --- | --- | ---: | --- | --- |
| Unit | BootstrapHealthControllerTest | 3 | 0/0/0 | `07aca9053c1cbde6af0fb4d9abf4633a08455111c30831f73d25097bf458a3a2` |
| Unit | CharNgramTextEmbedderTest | 9 | 0/0/0 | `eb102e3d85dec2c5353f87a943e2d1a3918e9121a40076b9dafa1ba772db30b4` |
| Unit | CosineSimilarityTest | 5 | 0/0/0 | `fbadcfb57e94decc333bad74b4198d2cf63a55b93628c2e0baf6a584d95f2e4b` |
| Unit | DefaultBusinessReadinessTest | 9 | 0/0/0 | `4957b2c7c114569204d64662052071e3aabe3441f72ba37300f2ce42f9ab7705` |
| Unit | DocumentAdmissionServiceTest | 6 | 0/0/0 | `588c6e8771c06ed2417466993ff5d989fd08f9d5b66e7663c97298b074c5cc4c` |
| Unit | DocumentManagementApiTest | 4 | 0/0/0 | `1ff3f782cdc898a64cdbffaa196feee8c9e2b2583656eeeb6ea406211f9f92c4` |
| Unit | EmbeddingConfigurationTest | 1 | 0/0/0 | `6880a0ea5dea4e25e74f06b282a8cb082ef783dd55a2fe78f3cbb215e8b620f3` |
| Unit | RequestValidatorTest | 6 | 0/0/0 | `97c23fc508313b8c70d13c252b5a0ea323e5e706480859682b828a8f468b9901` |
| Unit | SchemaPersistenceTest | 3 | 0/0/0 | `897939d8f9398febd840548ec38d33354833c021c6f99b6813115358f5497086` |
| Unit | SearchCandidateReaderTest | 4 | 0/0/0 | `15f14d890c122fb3aee6d85ad7d44e799a66c8cd9738097969be26d25afc2b1b` |
| Unit | SearchControllerTest | 3 | 0/0/0 | `f90fc338a460e4ff0042ab9d9d5cfc4ff8228a0f15d9c66a1db2ac32225dd0a6` |
| Unit | SearchHitCountServiceTest | 7 | 0/0/0 | `0eb865d8f18ba0072afb38f5c047e7b93394995f7532cd7dc0ed8c5c4bbf7040` |
| Unit | SearchServiceTest | 6 | 0/0/0 | `5c92cbd1f07720aad750979b43fcdb21dfd6fa7f4e233d7a8f6266592674b54c` |
| Unit | TaskDispatcherTest | 9 | 0/0/0 | `5c96e5417615f44be2ab724c9ef623f9d1622688745b142e3d81763b0aebb1f7` |
| Unit | TaskLifecycleTest | 3 | 0/0/0 | `1d350e1421ad4da7ffff7d6bba21ba321fe3eb4b977320d3411774b50a049d95` |
| Unit | TaskRecoveryServiceTest | 6 | 0/0/0 | `1797ce995d708fa9302115523974fd62aaa7edcf362ba70ff25f30f842087f88` |
| Unit | TaskTerminalServiceTest | 9 | 0/0/0 | `2752f951b6f2f9c01221bb917dc4ea119cf25674f70099d038fd84924b2dbb06` |
| Unit | TopKSelectorTest | 7 | 0/0/0 | `c21d06de462d4e246c2da346a3f1a9eaf0dfecef76b2d49c5961b9cb735ae6a0` |
| Unit | VectorCodecTest | 4 | 0/0/0 | `0b9c2cd012a97f554055f44116651d0bc9e8ea34ef354bf696e766dbcfd80872` |
| IT | B1AlgorithmIT | 16 | 0/0/0 | `5fe3a12983abcd4b06e8827fba3c68be720ffe61cc205b274d5ab532aec4dc0d` |
| IT | B2AdmissionIT | 9 | 0/0/0 | `214d7de49557a34486538c8a52261cc552b28e487c5485780edfdb4345c32b8b` |
| IT | B2DispatchIT | 8 | 0/0/0 | `5dd2ae973cdf259bb2d2a7365a7d0fa384fb9683d47c51f0bb7b55be1fc358a2` |
| IT | B2HttpIT | 5 | 0/0/0 | `05b7663cefaaa0ff20ef77b7ef81c3b5cc3637316fa8e541d9f6e59a8ee9ca40` |
| IT | B3LifecycleIT | 5 | 0/0/0 | `3e66f4d926638ec29a7b019ad7d2809a67bdeeab4d5de1c123e00465e163a808` |
| IT | B3ProcessIT | 6 | 0/0/0 | `e5281861820bf00c738fd842b1b56ff499ed74007d4bb3ac8f244bd32c02cb4b` |
| IT | B3TerminalIT | 16 | 0/0/0 | `c2b2a4571a95877bdd68e93ee8bb6ca868db9f7d407e9764f618f451a85d0db6` |
| IT | B4CountAndResourceIT | 11 | 0/0/0 | `b257932df8146212cb587d982362e6b0e08b018306ffcd2979bf49fc65b069b5` |
| IT | B4HttpIT | 5 | 0/0/0 | `415d667fff15cfdb5550b3e6ce5c6a496c94c97ce293f5a691df2170ada8342c` |
| IT | B4SearchIT | 6 | 0/0/0 | `5ac0241428724ff27dc285896de950e10c127918e74539ec608e0fd20352a8d8` |
| IT | BackupRestoreIT | 1 | 0/0/0 | `b92465e04d7ed73ed2c24cfe203db6a7075ef7715ad651e3ddac31697ba2ae67` |
| IT | C0BootstrapIT | 6 | 0/0/0 | `aff2006dab21e0621fd4bb807c3143c32ee690b7d8e9a2a9329a10ca4a9e56ae` |
| IT | FinalHealthIT | 13 | 0/0/0 | `87a7583a4e0ae37c6f98c3b50b482ea3aca29f511cbf6d536ef341a7c18d4091` |
| IT | LocalCasesIT | 14 | 0/0/0 | `cc6f4758f1d577e024e0759e5599cd6a8ce397a96712b3b0f113d03a072d51c8` |
| IT | ProxyOriginIT | 3 | 0/0/0 | `fe838da4bbb59e933f80b8c6918ce6d50ed67a7faa2fd45203a4872cf05b99ad` |
| IT | Rec07RevisedIT | 2 | 0/0/0 | `7278edd49eb70b40dc101b82b41187a07737029b4c0005ba9dc79a41fc9de470` |

## 本轮实际证明的范围

- 原题核心：真实B1确定性/独立JVM/Unicode边界、256维与空文、codec逐位、余弦/Top-K独立oracle；同算法及持久向量增量稳定由本轮对应XML方法证明。
- API：本机真实HTTP的六接口、202接收、参数/JSON/正文/渠道/Top-K/分页、同分零负分、纯文本摘要、大计数字符串、GET不计数、失效正交性和最终Top-K计数。详见逐条70映射，HTTP与持久层/替身边界各自保留。
- 并发/恢复：真实独立事务条件领取、物理提交锁、run_token拒写、短事务两表一致、搜索计数整体回滚、关闭屏障、四检查点子进程强杀恢复、正常SIGTERM及超时后迟到CPU不回SQL。不是生产故障演练。
- REC-07-R1两项本轮重跑成功：真实旧库顺序重启全字段/向量保持、双版本四组合在Top-K前过滤；旧原判据历史FAIL不覆盖。
- FAILED：受控故障分类与后续任务继续的隔离测试成功；不是固定生产包自然产生FAILED，也不是F05 Chrome实际分支。
- `ProxyOriginIT`日志明确127.0.0.2无法绑定，改用真实RemoteIpValve组件验证；非可信来源真实socket未执行。不能把3项JUnit通过写为全部公网代理链已实测。
- 8组语言关系只记录真实Java分数；无人工标签，不宣称语义准确率或SLA。

## 未执行、历史复用与最终门禁

本轮独立QA没有发线上请求、操作浏览器、重启服务器、改Nginx、执行压力或造生产FAILED。本轮生产只读/有界HTTP和UI由Owner回执后分别更新；此前2764/b8b的发布、恢复与Chrome证据只记REUSED_EVIDENCE。当前70映射的ISOLATED_PASS是明确方法范围，不冒充本轮ONLINE_PASS或70全绿。

已知本地25分钟503在同后端字节的新CI包复现，旧失败不能归咎浏览器缓存或被本轮短程282项遮盖。后端Owner须给根因及对照；最终实际候选需真实35分钟序列、精确计数及其后正常重启通过。任一新的503仍阻塞FINAL_ACCEPT；本文件不创建用户例外，也不修改冻结后端来迎合测试。

## H2根因与新候选的独立只读复核（后续追加）

以下由后端/主控实际执行，QA读取原件核证；不是QA再次执行完整回归。旧46ebe7d的282项执行记录仍保留，不改写历史。

新候选为`9883400451c9b2a22d13134cbd698d7478851b3d`，手动CI39927516112；QA实际计算下载JAR SHA-256为`becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece`，与manifest一致，内嵌`h2-2.5.252.jar`。本轮候选未获生产替换批准，生产仍2764/b8b；不得沿用其批准值。

主控最终本地XML逐组实计20组105 Unit、16组126 IT，失败/错误/跳过0；CI下载日志独立按测试类复算同为105/126，Node52通过，共283。新数只来自1项`SchemaConnectionLifecycleTest`，不是283+旧282。主控原件为`/private/tmp/12times-final-acceptance-fixed-final-verify.log`和`/private/tmp/12times-final-acceptance-fixed-node.log`；CI日志及manifest在受限本地候选目录，不入公开Git。

新增测试使用冻结schema和临时绝对文件库；keeper连接保持数据库实例存活，显式关闭建表initializer，再真实INSERT文档/任务、commit、JOIN核原文/taskId/QUEUED/零计数。旧红XML为1项、0 failures、1 error，classpath确为H2 2.4.240；异常链23514→90098，包含`ConditionInConstantSet`与`SessionLocal.compare`。新绿实际XML为H2 2.5.252、1项零失败错误。未改DDL/业务Java/算法/API/资源参数；pom只新增固定H2属性，CI另外检查pom去除此属性后与原冻结语义一致。

自然旧包本轮再次FAILED：1500.093秒、22篇、207业务请求。QA核到Hikari日志16:42:55.084自然maxLifetime关闭建表conn0；16:42:59.120由仍活跃conn1开启事务；JFR导出08:42:59.146494167Z出现90098及CHECK表达式栈，随后23514；事务日志明确rollback。两次独立窄probe实际代码调用`getSQLState/getErrorCode`，原输出与嵌套cause分别23514/90098，且health200→POST503→health200。JFR启动期预构造OOM/JTS探测事件不被当作本次根因。证据共同支持CHECK常量IN表达式缓存已关闭建表session；不是整个H2文件关闭或永久损坏。旧FAIL不因确定性修复绿而改PASS。

### 旧2.4文件副本升级兼容：ISOLATED_PASS

后端Owner实际执行，QA只读检查`/private/tmp/12times-h2-upgrade-compatibility-rpig0zb1/report.json`、Snapshot.java、check.py和三份snapshot。固定新CI包，114请求、0写重试、1次正常重启、新写2篇后保留。三快照实际逐字节一致，均为SHA-256 `a2f799e4e135b48acdc2d2cc139687427bec8757aa0c9b3806ea92e023f29d2b`；独立解析为22文档×10列、22任务×11列、22个原始vector字节字段，含task/token/attempt/全部时间/错误。原文件只作来源且hash保持，启动/写入只作用全新副本。

该PASS仅证明所测22行旧库副本的升级读取/写入/正常重启，不能推出所有历史版本兼容，也不证明2.5文件可降级给2.4回退。Mac/Linux实际新CI包35分钟仍在运行，尚未据此写稳定性PASS。

### 原严格数据断言与用户新起点裁决

主控报告UI误触一次无渠道空查询，旧3123、4123及本轮B/C/EMPTY2五个实际命中各+1。原严格“旧33完全不变”断言必须保留FAIL；解释搜索副作用不使原断言通过，不扣回计数、不回写旧快照。完整五条真实命中回执和前后ledger待主控/Owner提供后独立核账，目前不能把口头摘要当已核原件。

用户本轮直接裁决原话为“允许新基线重验，永久保留原FAIL”（主控转达）。该裁决允许Owner封存新只读快照并从新起点独立严格重验，不是QA自创例外，也不把先前误操作改PASS。新baseline及Chrome结束后的实际逐字段对照尚未收到，状态NOT_RUN。原FAIL与新重验结果必须并列报告。

最终门禁仍BLOCKED：Mac/Linux35分钟及其后重启未完成，根因修复候选未批准替换生产，新基线重验仍待实证；任一新503必须保留FAIL。
