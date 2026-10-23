# API-FINAL-VERIFICATION 隔离原题与API核证

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
