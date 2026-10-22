# B3独立验收设计

设计基线：`e8d45a05f14ed7abc60ff9a5bd4698fca841786a`；分支`agent/qa`；2026-10-23。
依据：最新AGENTS、B3实施契约及原测试包TASK-08/09、CON-02/03、REC-02—06（原包只读）。旧资料REC-07的全库版本处理建议不覆盖最新B3契约；本轮验证任务版本不匹配明确失败，不擅自扩大为全库迁移。

**本文件为设计，所有B3用例NOT_RUN；未执行构建、信号或进程故障。** B1黄金、算法版本和历史SHA冻结，B2结果保留。

## 分层矩阵

| 编号 | 类型与受控操作 | 明确通过定义 |
| --- | --- | --- |
| B3-Q01 | 真实生产组成、HTTP接收空文/正常文，统一B1与codec | 最终SUCCEEDED、向量1024字节及两表时间一致；空文仍就绪且全零；正常文与冻结B1独立黄金一致。不能用固定向量替身证明成功 |
| B3-Q02 | 直接终态短事务，在所有权确认/任务更新后、文档写入前失败；另测两表SQL执行后回滚及更新0行 | 独立连接观察任务和文档均未半提交；受影响行数不符整体回滚；完整handler的重试另测，不让重试掩盖事务组件错误 |
| B3-Q03 | 旧token A与持久新token B，用不同向量提交旧成功/旧失败；重复本次token终态调用 | 旧A不写向量、不标新任务FAILED；同token重放不覆盖首次终态时间、向量与错误；任务完成/失败终态不能互相覆写 |
| B3-Q04 | 包裹真实B1计算，latch阻塞后失效并独立确认提交；释放计算完成 | 最终成功且仍失效，首次失效时间/原文/hitCount保持；完成SQL不整行覆盖 |
| B3-Q05 | 正常真实B1成功；非法输入/版本不符/计算异常注入分别执行 | 错误分类稳定且脱敏、码≤64、消息≤512；FAILED只在可靠终态提交后可见，失败不生成假向量。替身只用于异常路径，不能替代真实成功路径 |
| B3-Q06 | 明确回滚可重试DB错误，记录每次TX与尝试；不可重试错误另测 | 有限次数，不超过契约3次；50/100ms是退避起点，不用sleep判定正确性；成功仅一次，耗尽后有明确门禁/诊断而非永久失联 |
| B3-Q07 | 真实终态commit后才抛异常，新事务分别读到匹配结果、token不符、向量不一致、读取失败 | 先确认状态/token/向量一致性；已成功不能反写FAILED；无法确认DEGRADED、拒接/停派、不盲重算或重复终态写入。普通回滚异常不能冒充提交未知 |
| B3-Q08 | 初始RECOVERING，恢复commit前latch；独立调用真实接收与scan；确认提交后放行 | 恢复前实际拒接且不派发，事件顺序RECOVERY_COMMITTED→READY→领取/处理；恢复失败绝不READY，不只看健康页 |
| B3-Q09 | 专用库四态夹具，旧进程已退出，再真实恢复并B1处理 | 旧RUNNING转QUEUED时清token/执行终态时间/错误，保留attempt/提交时间/原文/失效/计数；再领取attempt递增；已有SUCCEEDED/FAILED不重做，成功向量/首次时间不改 |
| B3-Q10 | 进程SIGTERM：真实任务在途，通过检查点释放并观察关机事件 | 先STOPPING拒接/停派，再有界等待短事务/handler，最后数据源关闭；JVM真实SIGTERM证据与普通context.close分开记录 |
| B3-Q11 | 超时CPU：latch使计算跨越等待上限；关闭写入许可/数据源后释放，装饰Mapper统计写SQL | 超时后不再进入终态SQL，不向已关H2写入；已进入短TX须等待结束；任务保留可恢复状态，无无差别中断H2线程 |
| B3-Q12 | 独立子JVM，接收物理commit后尚未派发，父进程强杀 | 退出后文件锁释放；重启同专用库，QUEUE记录保留、真实扫描+B1最终成功，无需客户端重新提交 |
| B3-Q13 | 独立子JVM，领取物理commit后计算检查点强杀 | 退出后旧RUNNING恢复、换token/attempt递增，再真实B1成功；允许重复计算，不声称恰好一次 |
| B3-Q14 | 独立子JVM，成功事务部分SQL后、commit前强杀 | 崩溃后两表不存在半成功；恢复再完成，向量与SUCCEEDED一致 |
| B3-Q15 | 独立子JVM，终态物理commit后/内存清理前强杀 | 已成功不重新领取或完成；两表向量、时间、attempt保留，最终值与B1黄金一致 |

B3-Q12—15每个场景使用不同临时绝对文件库、独立JVM与随机端口。**Process.destroyForcibly/SIGKILL是真实强杀，不能用RuntimeException、context.close、SIGTERM代替。** SIGKILL不等于真实断电，不扩大可靠性结论。

## 独立子进程协议与证据

测试源码中的探针通过装饰真实生产组件发结构化事件，日志和事件通道分离，包含runId、完整基线SHA、pid、事件序号、taskId、token、attempt、数据库路径。父进程仅在以下具体事件后发送目标信号：

- `QUEUED_COMMITTED`：接收实际commit完成，扫描尚未领取。
- `RUNNING_COMMITTED`：领取实际commit完成，真实B1计算前或计算后受控暂停。
- `TERMINAL_BEFORE_COMMIT`：实际终态SQL已部分执行但物理commit未发生。
- `TERMINAL_COMMITTED`：真实commit返回后发事件，不能在TransactionTemplate回调结束时冒充提交完成。
- `WRITE_GATE_CLOSED`、`DATASOURCE_CLOSED`：生命周期测试区分写入许可与数据源销毁，CPU释放后继续记录写SQL次数。

父进程保留真实pid、事件、发送信号、waitFor结果/退出码与前后数据库快照；未确认旧pid退出不打开同库。退出后先独立检查数据库可打开/锁释放再启动，不使用AUTO_SERVER。每次等待有界；失败finally清理仅本测试子进程并等待退出，不能误杀其他工作区。测试启动classpath正确包含依赖，不复制算法代码或使用硬编码成功端点。

## 旧测试迁移提案（等待主控审查，当前未修改）

| 现有测试 | B3影响 | 保留测量边界的提案 |
| --- | --- | --- |
| C0BootstrapIT | 同库重启QUEUED哨兵可能被真实handler执行，health阶段也变化 | 对基础设施测试使用明确C0测试组成，保留同库/DDL/DTO/配置与QUEUED原断言；真实B3健康/就绪另测。不能把QUEUED直接改SUCCEEDED消掉失败 |
| B2QaEnvironment及B2AdmissionIT | 生产handler抢走容量与claim夹具；旧RUNNING现在应恢复 | 明确B2隔离组成保留当时无handler/旧RUNNING拒接原断言；B3验收另建生产环境，不能全局禁handler |
| B2DispatchIT | 手动dispatcher和生产自动扫描可能争抢夹具 | B2环境只装配B2生命周期与无handler；受控handler仍直接new dispatcher，不新增线上配置开关 |
| B2HttpIT | 人工四态/大计数夹具及无handler原断言 | 仍在B2隔离组成运行；真实B3 API从接收到成功再查询另测，禁止将夹具当真实终态 |
| B1AlgorithmIT | Spring装配用例加载新恢复组件，纯算法无变化 | 保留冻结golden和输入；生产装配仍应使用统一TextEmbedder，必要时仅明确其无业务夹具的测量边界 |

后端提供test composition方案后，QA将具体类/注解/导入差异先交主控审查再迁移。迁移后分别统计旧阶段回归与真实B3组成，不删失败断言，不全局排除生产handler以冒充生产验收。

## 向后端请求的窄控制点

- 成功/失败终态组件公开方法及返回“已写入/本次重放已确认/失去所有权”的结果；可装饰真实Mapper/TX检查更新行数、两表间异常和commit后未知。
- handler构造器可包裹统一TextEmbedder，以latch暂停真实B1前后，异常路径注入受控异常。
- 恢复独立组件、初始readiness状态与启动调用顺序可观察，能装饰恢复commit暂停；明确读API在RECOVERING/STOPPING可用范围。
- 写入许可/生命周期屏障组件可直接驱动；可观察许可关闭、已进入TX退出与数据源销毁顺序，不要求添加生产HTTP故障端点。
- B2历史隔离组成的正式方案，避免QA猜测业务装配规则或新增环境参数。

## HTTPS反代Origin只读分析与待复现设计

当前ApiRequestFilter将Origin的scheme/host/port与Servlet request.getScheme/getServerName/getServerPort逐一比较；源码不直接读取X-Forwarded-*。现有application.yml未显式配置转发头策略。因此TLS在代理终止、后端接HTTP时，是否识别HTTPS取决于服务器转发头处理和可信代理配置。**这是源码风险分析，当前未执行请求，不称已确认缺陷。**

拟独立真实HTTP矩阵（生产代理配置由主控/部署统一确认）：

1. 默认配置，原始HTTP连接到随机回环端口，带`Origin: https://text-vector-search.lemoncharger.com`和完整X-Forwarded-Proto=https、X-Forwarded-Host、X-Forwarded-Port=443；记录状态码/错误、测试观察器所见scheme/host/port。单带Proto也单列，防止将host/port不匹配误归因scheme。
2. 同一默认配置，无转发头的本地同源Origin应正常；外站Origin应403。没有可信代理配置时，任意客户端伪造头不能改变信任边界。
3. 拟发布的可信回环代理配置，保持真实HTTP后端和HTTPS Origin，通过覆盖后的转发头观察scheme=https、host新域名、port=443及请求成功；不同外站Origin仍403。
4. 以可控原始头/可信链检查Nginx覆盖客户端Forwarded及X-Forwarded头，不无条件透传；在可用的独立环境测试不可信来源伪造头无效。仅本机回环请求无法证明所有外部不可信来源策略，需部署侧补证。

不在本轮修改ApiRequestFilter、健康类、主配置、代理或证书；当前公开站点禁止代理真实API，故这组先在本机隔离配置执行，不能借测试授权开启服务器真实API。

## 执行门禁

主控提供含B3实现和已审查迁移方案的新基线后，才编写/编译新acceptance。先目标验收与真实故障，再Java17完整clean verify；25项Node由主控/前端按最终集成回归。新增依赖/公共变更只提议给主控。
每轮保留真实源码SHA、命令、实际发现/通过/失败/跳过数、故障事件、进程退出码；未执行/阻塞分别记录，不编造测试通过。
当前只交本计划，全部B3/Origin复现均NOT_RUN；B4检索/计数、前端浏览器与DEP3正式发布不在本轮证明范围。

## 后端接口对接记录（方案，尚未合入/执行）

- `TaskTerminalService.succeed(TaskExecution,float[])`与`fail(TaskExecution,TaskFailure)`返回`TerminalResult`：APPLIED/ALREADY_APPLIED/STALE/WRITE_CLOSED；未知确认比对token/状态/版本/完整1024字节。
- `ProductionTaskExecutionHandler`调用真实B1；失败码INPUT_INVALID/VERSION_MISMATCH/COMPUTATION_FAILED。
- `TaskRecoveryService`通过SmartInitializingSingleton先恢复commit，再READY，早于dispatcher的SmartLifecycle扫描。
- `TaskReadiness.State`及`state()/accepting()/reason()`区分RECOVERING/READY/DEGRADED/STOPPING/STOPPED。
- `TaskWriteBarrier`仅短SQL持许可，CPU不持有；关闭先STOPPING/停派/等待Worker，再关闭新写许可并等待已进入短事务，迟到CPU返回WRITE_CLOSED。接收与失效亦受写许可保护。
- 新ReadinessFilter在恢复/停止阶段对全部API返回503，DEGRADED只保留GET诊断；独立验收将覆盖真实入口而非仅健康。
- 测试源码`B2CompositionTestApplication`排除B3 handler/恢复/filter，保留B2历史组成；QA待主控确认后仅将旧测试环境切到明确B2组成。

主控健康方案为stage=B3、taskState/taskReady与businessReady=false。C0健康具体迁移提案已交主控：保留真实GET状态/基础连接和businessReady=false断言，不创建假C0响应；若采用当前真实controller，则明确将阶段元数据断言从C0迁为B3，并新增taskState/taskReady与测试组成状态相符的断言；C0同库QUEUED哨兵原断言仍在B2隔离组成保留。新B3生产健康/恢复门禁另测，不把阶段元数据变化混为已完成业务。此方案尚未实际修改，需主控审查后执行。
