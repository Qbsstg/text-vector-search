# B3 集成验收记录

日期：2026-10-23，北京时间。用户原则通过B2核心后授权本阶段；B1算法和历史提交保持不变。主控最终本地集成门禁已通过，GitHub最终状态另行登记；先前阶段结果不倒填为B3验收。

## 代码与历史

启动时本地HEAD、远程main均为`bd18c4939fec576cd47d9a8a24592d15fdcffb70`，GitHub [run39808883527](https://github.com/Qbsstg/text-vector-search/actions/runs/39808883527)完整success，仓库PRIVATE。B2原53 unit、44 IT、25 Node通过的证据不变；两轮原始CI报告及SHA256清单保存在`evidence/b2-ci-38eeb26/`和`evidence/b2-ci-bd18c49/`，逐字节校验通过。原始PDF、用户未跟踪的`docs/测试工作/`、证书ZIP未纳入提交。

| 修改 | 真实提交 |
| --- | --- |
| 生产处理器、令牌终态短事务、完整向量确认 | `00c5af5e472d656f97111d83de2532ea0dd426a2` |
| 启动恢复、真实生产装配、恢复就绪门禁 | `90bcdf4d3faa18e17afc5ff0e4689ff48cbe1722` |
| 优雅关闭、并发close、超时CPU回写屏障 | `bb6e5eb96be7a3f0a3a38570c9aa71fd1e633d68` |
| 明确隔离B2历史测试组成、保留原断言 | `2734b7e8e6b2a60ee7ef0dc519e0ecdb16629732` |
| QA独立终态验收 | `a2ddb6ad34746110aa17f3e4eb8859e99e519326` |
| QA四点真实强杀及完整生命周期验收 | `4523a67ce9cd87dec06023992bc34901b3e38aca` |
| 健康区分taskState/taskReady与businessReady | `52c92de27f888871cc073b85d3ae6e79e61949cb` |
| 恢复未知提交确认核对任务自身提交时间 | `13953cfb889a5172c19da53b19f8aec147508547` |
| H2退出钩子安全配置 | `fe32b5bc4ef1e8d4efb66938e4f8af72b729b6fc` |

未执行amend、rebase、force push或改写历史。新增维护注释和提交说明使用简体中文，生成器输出/第三方资料保留原文。B1的embedding/search算法类、算法版本、业务DTO、DDL及依赖无改动；唯一新增运行安全配置是经真实故障证明的DB_CLOSE_ON_EXIT=FALSE，容量、并发及等待预算保持。

## 实现与审查

后端Owner统一持有状态机和事务。生产handler在事务外调用冻结B1；空文也生成真实零向量并成功就绪。成功先条件确认RUNNING、run_token、文档ID及版本，向量/版本/两表完成时间与SUCCEEDED同一物理事务；任一步行数异常均回滚。失败可靠持久化INPUT_INVALID、VERSION_MISMATCH、COMPUTATION_FAILED固定脱敏分类。终态不覆盖原文、invalidated、首次失效时间或hit_count，旧token不能覆盖新尝试。

未知终态提交先独立事务核对token、状态、版本、时间和完整1024字节；同长度不等于同向量。只对明确回滚的暂时错误最多3次重试，50/100ms退避；无法确认则DEGRADED、拒接/停派并保留现场，不伪造FAILED。

启动先RECOVERING，短事务重排旧RUNNING、清理旧token/执行时间/错误，保留attempt与文档元信息。确认物理提交后才READY并开始调度。RECOVERING/STOPPING/STOPPED实际拒绝全部业务API；DEGRADED仅保留GET诊断。单JVM、单H2文件、旧进程退出后恢复，不在线抢占，不启用AUTO_SERVER。

关闭先STOPPING拒接，再停扫描/派发、等待Worker，关闭新写许可并排空已进入短事务，最后销毁数据源。CPU不持许可，超时后不能迟到进入SQL。并发close共同等待首次排空；不强制中断H2线程。30秒只约束Worker等待，阻塞中的扫描及已经进入的数据库事务排空不承诺固定时限。

## 真实发现与修复

1. 恢复未知提交确认曾拿任务提交时间与文档提交时间比较。常规提交使用同一Instant，DDL却没有强制两者相等。后端先以不同时间及真实commit后抛错复现误降级（1测试1失败），再改为查询任务表时间；33项相关单测通过，恢复READY、attempt及两表各自时间均保持。日志：`/private/tmp/12times-b3-recovery-time-before.log`与`-after.log`。
2. QA真实SIGTERM正常排空首轮失败；pid36938的事务取连接报H2 `90121-240 Database is already closed`。H2默认独立JVM退出钩子可在Spring排空前关库。只增加DB_CLOSE_ON_EXIT=FALSE后，pid37186同一成功断言通过、退出143。主控同步生产URL和运行契约，并在提交前运行74项单测，全通过。原失败和A/B证据保留，未降低断言。最终进程验收须读取生产默认URL，不能只验证测试覆盖配置。

## 测试分层与最终门禁

本机命令使用Temurin17及独立本机Maven缓存：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 clean verify
node --test tests/frontend/*.test.mjs
```

测试随机回环端口、临时绝对H2目录，不共享项目数据。对应Maven监听权限限制的失败和授权重跑分别记录，不删除失败日志或弱化断言。

| 证据层 | 真实代码基线、命令与结果 |
| --- | --- |
| 后端阶段回归 | `9fd56d4`：73 unit+47 IT+25 Node全部通过；尚不含QA终态IT，见backend-b3交接 |
| 主控首次集成 | `9a8d74db44e0082837026b1b1593ad2e3be539d4`：Java17 clean verify 73 unit+63 IT，25 Node，合计161，失败/错误/跳过0；00:54:45完成。日志`/private/tmp/12times-root-b3-pre-lifecycle.log`及`-node.log` |
| QA终态 | 16项IT覆盖真实B1黄金、第二表写前/写后/零行回滚、旧token、失效竞态、三类失败、真实commit后异常完整确认、有限重试和许可关闭后零SQL，详见`b3-terminal-results.md` |
| QA后半 | `fe32b5b`加当时待提交测试：74 unit+74 IT+25 Node全部通过，01:05:45完成。新增6项进程、5项生命周期IT，详见`b3-process-lifecycle-results.md`；原失败/A-B在`evidence/b3-sigterm-ab/` |
| 主控最终集成SHA/回归 | **`28cd42d7603c862830b51c38bb98e1de10e6427d`**：Java17 clean verify，**74 unit+74 IT+25 Node=173通过**，失败/错误/跳过0，01:09:43完成；XML及完整进程/构建/Node原件见`evidence/b3-local-28cd42d/` |
| B3 GitHub CI | 待私有main推送后核对headSha、真实报告及Node日志，不使用B2绿色状态替代 |

真实JAR额外冒烟使用上述首次集成产物：普通文本与空文均由生产handler进入SUCCEEDED/vectorReady；失效后正常重启保持失效、向量就绪、原完成时间及hitCount=0，成功任务不重做。两个进程35214/35215均SIGTERM退出143；健康200、stage=B3、taskState=READY、taskReady=true、businessReady=false。证据`/private/tmp/12times-root-b3-smoke-np2hkgyz/result.json`及两个日志。该冒烟不替代QA强杀验收，也早于H2配置修复。

主控最终回归的11个独立子JVM均实际退出：强杀39264/39274/39279/39284退出137，正常SIGTERM39295、超时39256及5个恢复进程均退出143。四点强杀后先确认旧进程退出及文件锁释放，独立读库验证未提交终态不半成功，再启动同库真实B1；已成功快照完全不变。超时数据源关闭后再释放CPU，终态SQL调用0，重启attempt递增且成功。恢复门禁以RECOVERING拒接/扫描0→物理commit事件→READY扫描/领取证明，不虚构连接器尚未监听时的HTTP503。

最终打包JAR再次实测：源码28cd42d产物SHA256为`4b2bcbd7fe223c52a02c7a7a9a11c2d08570a1b98326e3a726fc8b7b2221b60e`，pid39390使用默认生产配置和专用临时H2，普通/空文均SUCCEEDED，健康READY/taskReady=true/businessReady=false，SIGTERM退出143。最终及修复前冒烟结果/日志均归档在本地原始证据目录的独立子目录，不包含数据库。

历史B2无handler/旧RUNNING拒接、容量和同库QUEUED哨兵断言保留在测试专用B2Composition中；B3进程/恢复验收使用真实VectorSearchApplication与真实B1。测试专用故障控制不进入生产代码、配置开关或公网端点。

## HTTPS代理与服务器

ApiRequestFilter未放宽Origin。默认应用不识别HTTPS转发头时实际403，受控proxy配置只信任回环后实际识别https/新域名/443，同源202、跨源403。独立ProxyOriginIT 3项通过，恶意Forwarded不覆盖native三元组；不可信来源仅Tomcat组件反例通过，127.0.0.2实际socket因本机别名不可用未完成，不能称公网组合已全验。未来Nginx须覆盖Host及X-Forwarded-*、清空Forwarded，应用仍仅监听127.0.0.1。

新域名`text-vector-search.lemoncharger.com`的A记录指向8.155.137.169，无AAAA。用户有效证书匹配新域名，链和公私钥匹配通过；有效期至2027-02-13 07:05:34 UTC，公开指纹和命令/回滚见server-ssl-prep交接。原ZIP未修改，私钥不入仓库/对话，服务器目录0700、文件0600，本地专用临时副本已清理。

服务器角色及主控独立复核：Nginx配置校验成功；系统信任HTTPS检查200、ssl_verify_result=0，真实`/api/documents`404；未知SNI拒握手，80/443监听、8080无监听。vector-search.service inactive/disabled、NRestarts=0，UFW仍inactive。Nginx开机启动策略仍disabled。主控只读复核未修改服务器；业务代理/Basic Auth只是未启用模板，没有真实入口密码，未发布或启动业务。

## 未验证项与后续门禁

- FE1仍REVIEW_UI：25项Node及管理HTTP不替代真实浏览器的视觉、键盘、DOM/XSS验收，Work本地浏览器禁用。
- B4搜索、候选快照、最终Top-K原子计数尚未实现；语义相关性、业务吞吐/性能、断电/磁盘损坏和跨机器备份恢复未验证，不从B1/B2通过推断完成。
- B4前置：B3生产原子终态、失败、恢复顺序、四点强杀与正常/超时关闭通过；同版本持久向量可用；保留单JVM/H2、失效正交和候选快照契约。满足后另行派发B4，不自动扩大本轮范围。
- DEP3前置：B4及完整业务测试、真实浏览器、受保护入口/凭据、明确发布SHA和回滚、用户具体发布确认。TLS检查不等于业务HTTPS验收。
- 用户人工项：云控制台核对80/443实际安全组与来源、保持8080/H2不公开；需要UI验收时启用Work本地浏览器；后续安全创建入口认证凭据、确认发布和Nginx启动策略、证书到期前续期。不再要求重提供现有有效证书，不在对话传密码或私钥。

个人交付目标仍为2026-10-23 18:00（北京时间），不以时间目标替代真实故障与事务门禁。
