# FINAL-QUALITY 线上全面功能验收报告（待测）

任务ID：FINAL-QUALITY-QA。基线公开提交 `5f1740d2141473a7595eeb41eb6efb037a847815`，工作分支 `codex/final-quality-qa`。本文件建立本轮独立执行入口，不改历史70矩阵、273项自动化、REC-07历史FAIL或原始材料。当前没有发起线上请求，没有本轮线上PASS。测试开始/结束UTC、运行版本核验、真实HTTP/Chrome证据：均NOT_RUN。

## 执行门禁与环境

批准业务源码 `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，批准JAR SHA-256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`。这两个值是比对目标，当前实际运行值未核验。主控需记录Java17进程、systemd、TLS、认证、独占文件占用、磁盘/内存余量、文档数量/剩余容量、真实health/businessReady及异常日志；未全部完成不得写入。

正式默认Worker=1、Queue=64、SearchPermits=1、Hikari=4；不改变配置或JAR。最新用户允许正式服务1/2/4/8并发，每阶30秒，总升压窗口不超过120秒并自动中止；由主控执行和单列统计，功能测试不自行占用升压窗口。线上功能先低速串行，只有明确的重复ID与搜索/失效用例有限并发。

URL通过`QUALITY_BASE_URL`配置；凭据仅安全环境或0600受限文件。公开报告只放脱敏汇总与原件SHA，不记录秘密、敏感原始载荷、内部路径或认证头。未经主控GO，本报告和JSON均只是定义。

## 数据、请求和计数预算

本Owner最多成功新增12篇：主精确渠道10篇（空文、空白、Unicode、规范化等价两篇、普通、重复竞争、失效竞态、摘要边界、20000码点），邻渠道1篇，default渠道1篇。ID统一唯一前缀；主渠道与邻渠道均含本轮唯一值。全轮最多20篇由主控统一扣账；失效不释放名额，失败/未知提交须保守占用容量，禁止自动换ID重提。不得删除或覆盖历史数据。

功能硬预算：所有HTTP总计不超过240；提交尝试不超过32，搜索尝试不超过16，失效不超过4，GET不超过180，全局GET不超过4 RPS。各分项是上限而非执行目标。所有失败、429、超时、轮询均计入，发出前原子扣账；不得以重定向、预检或认证失败绕过总账。轮询总计不超过120，每篇不超过10次、每2秒一次、最长30秒，任一预算先到即结束。所有GET来源含Chrome/k6须主控统一限流或串行。

功能搜索全部使用明确精确channel，包括空候选独立channel；禁止省略/null搜索影响历史hitCount。搜索200的每个真实hit记账，每篇期末减期初必须等于已确认200响应命中次数。429和验证失败不计成功。功能前后与性能阶段分别建立计数快照和增量；压测搜索另用主控预算，不能重复使用功能16次额度。真实结果未知立即停止后续全部写入（含搜索），不自动重试，也不从累计计数猜本请求是否提交。

## 六接口与证据字段

| 真实接口 | 定义入口 | 本轮状态 |
| --- | --- | --- |
| POST /api/documents | F01/F03/F04/V-submit-*、A02/A03 | NOT_RUN |
| GET /api/tasks/{taskId} | F02/R05/R06 | NOT_RUN |
| GET /api/documents | R01/R03/A01 | NOT_RUN |
| GET /api/documents/{documentId} | R02/R04 | NOT_RUN |
| PUT /api/documents/{documentId}/invalidation | C01/C02 | NOT_RUN |
| POST /api/search | S01—S05、V-topK-*、V-search-channel | NOT_RUN |

[功能用例定义](../../tests/performance/functional-cases.json)声明输入、边界、替代fixture操作和脱敏事件接口，当前没有执行器。每请求必须保留caseId、请求序号、路由模板、UTC时间、耗时、真实HTTP/code、结果分类和预算余额；不把预期code写成实际code。任务轮询记录真实状态；202不等于向量化完成。非JSON代理响应也保留HTTP事实。失败时先记录证据，禁止削弱断言。

## 历史70条与本轮执行映射

来源：[原70条映射](final-release-local-cases-matrix.md)及[REC-07修订裁决](../design/REC-07-验收判据裁决-20261023.md)。下面只用原编号和目标摘要做追踪，不复制受限原始请求/截图。`ONLINE_SAFE`表示可在线完整实测该项范围；`ONLINE_PARTIAL`明确只能覆盖子范围，剩余必须独立记录；`ISOLATED`需要隔离；`REUSE_ONLY`本轮仅追溯旧证据，绝不能标新PASS。所有分层均是计划，当前实际状态统一NOT_RUN；旧PASS不继承。联合项只有全部子断言取得本轮证据才能标整体PASS。

| 原编号 | 目标摘要 | 本轮分层 | 执行映射与限制 | 本轮实际状态 |
| --- | --- | --- | --- | --- |
| ALG-01 | 256维、返回类型与有限数值 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-02 | 重复调用按位相同且结果不共享可变数组 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-03 | 混合文本并发向量化无串扰 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-04 | 独立JVM、默认Locale及固定环境的确定性 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-05 | 空字符串和纯空白产生零向量 | ONLINE_PARTIAL | F01/F02/S01可验证空文成功、就绪及零查询零分；256个float逐位为零需隔离。 | NOT_RUN |
| ALG-06 | 规范化等价对与原文不改写 | ONLINE_PARTIAL | F01/R02/S02检查规范化等价检索及原文保留；按位相同需隔离。 | NOT_RUN |
| ALG-07 | Unicode与n-gram边界 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-08 | 向量二进制codec、字节序和损坏输入 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-09 | 余弦数学oracle含零、负值和非单位向量 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-10 | Top-K固定oracle：负分、同分和K边界 | ONLINE_PARTIAL | S01/S02/S03检查零分、同分、K>N与空候选；人为负向量/随机oracle需隔离。 | NOT_RUN |
| ALG-11 | Top-K随机对照与近似同分 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-12 | 入库/查询同版本、增量稳定与CPU实现审查 | ONLINE_PARTIAL | 主控版本/配置只读核对与F01/S02真实入库查询；位级稳定/CPU实现需独立源码证据。 | NOT_RUN |
| API-01 | 完整业务闭环与元信息 | ONLINE_SAFE | F01/F02/R01/R02/C01/S05完成六接口闭环；只按实际观察状态记录。 | NOT_RUN |
| API-02 | documentId校验和重复ID | ONLINE_SAFE | F03/F04/V-submit-bad_id检查409、非法ID、同ID并发一次接收与原文不覆盖。 | NOT_RUN |
| API-03 | text空值、长度与规范化膨胀边界 | ONLINE_SAFE | F01及V-submit-*检查空/null/缺失、20000/20001码点与NFKC膨胀；每次拒绝核对无新增。 | NOT_RUN |
| API-04 | 提交与搜索的渠道缺省语义不同 | ONLINE_PARTIAL | default提交、精确channel/邻渠道/不存在channel与空白拒绝可线上；省略/null搜索会影响历史计数，移隔离；default第二篇null提交由容量余量另分配。 | NOT_RUN |
| API-05 | topK与分页的数值边界 | ONLINE_SAFE | V-topK-*及R03/S01验证类型边界、K>N与分页；所有失败保留实际code。 | NOT_RUN |
| API-06 | 先过滤后Top-K，不遗漏低分合格候选 | ONLINE_PARTIAL | S01/S05验证真实当前成功、渠道及失效过滤；FAILED/双版本/人为负分候选需隔离。 | NOT_RUN |
| API-07 | 文档列表、详情和任务关联不改变计数 | ONLINE_SAFE | F02/R01/R02核对任务关联、列表排序分页、UTC/null与字符串计数，GET前后不增计数。 | NOT_RUN |
| API-08 | 不存在对象与幂等失效 | ONLINE_SAFE | R04/R05/C02验证404与失效幂等首次时间，真实SUCCEEDED+invalidated独立。 | NOT_RUN |
| API-09 | 请求体、Content-Type与非法JSON | ONLINE_PARTIAL | V-json/V-media/V-bytes检查有限单次400/413；chunked精确字节边界全组合需隔离。 | NOT_RUN |
| API-10 | 异常结构、安全信息与数据库故障 | ONLINE_PARTIAL | 线上所有实际错误检验code/message/fieldErrors及脱敏；数据库故障/损坏不在线制造。 | NOT_RUN |
| API-11 | 只给最终结果计数，读取和被过滤候选不计数 | ONLINE_SAFE | S01—S05/R02以成功hits逐篇建立精确delta账本；分阶段冻结其它runner，未知即停写。 | NOT_RUN |
| API-12 | 空库、空查询、零分和负分规则 | ONLINE_PARTIAL | S01验证零分/空查询，S03精确空渠道替代空库外观；真空库和负分oracle需隔离。 | NOT_RUN |
| TASK-01 | 受控证明异步提交，不靠响应速度猜测 | ONLINE_PARTIAL | F01/F02记录真实202和有界最终成功，不保证观察到QUEUED/RUNNING；受控暂停/失败四态不能线上注入。 | NOT_RUN |
| TASK-02 | 四态、计算失败可查且不拖死后续任务 | ONLINE_PARTIAL | F01/F02记录真实202和有界最终成功，不保证观察到QUEUED/RUNNING；受控暂停/失败四态不能线上注入。 | NOT_RUN |
| TASK-03 | 接收两表原子性：插入Document后任务写入失败 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-04 | 持久接收后未派发，扫描能补派 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-05 | 执行队列饱和与拒绝后的在途集合清理 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-06 | 持久积压与总文档容量分别限制 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-07 | 重复派发不造成重复执行，在途清理可继续工作 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-08 | 终态两表写入中间故障不能半成功 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-09 | 失败回写带状态与token条件 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| TASK-10 | 调度/终态数据库异常必须显式处理 | ISOLATED | 需故障注入/队列或容量饱和/重复派发/旧token/数据库异常；线上12篇不证明内部条件和恢复。 | NOT_RUN |
| CON-01 | 两个独立事务竞争同一QUEUED领取权 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| CON-02 | 旧token迟到结果与重复成功提交 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| CON-03 | 计算中失效，完成不复活 | ONLINE_PARTIAL | 可记录提交后立即失效的实际终态独立性；不能保证失效时CPU尚在计算；内部交错仍需隔离latch证据。 | NOT_RUN |
| CON-04 | 在途检索与失效的重叠语义 | ONLINE_PARTIAL | C01真实并发发起且按快照语义接受已捕获候选；未捕获内部交错不得写成竞态全覆盖。 | NOT_RUN |
| CON-05 | 失效已经完成后开始的新检索必须排除 | ONLINE_SAFE | C01完成后S05新搜索明确排除race，详情保留。 | NOT_RUN |
| CON-06 | 多次并发命中同文档无丢失更新 | ONLINE_SAFE | S04两客户端有限并发，200命中总次数对delta，429不计成功；全429不能标一致性PASS。 | NOT_RUN |
| CON-07 | 多个结果的计数锁顺序与事务完整性 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| CON-08 | 计数第二条写失败，整次统计回滚 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| CON-09 | 接收锁必须覆盖物理提交 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| CON-10 | CPU计算不持有长事务，检索门禁可释放 | ISOLATED | 需受控事务交错、锁/回滚/旧token/屏障及连接观测；有限HTTP并发不能独立证明。 | NOT_RUN |
| REC-01 | 正常停止重启，全部业务数据保留 | REUSE_ONLY | 本轮不为QA停机重启；只读现状不证明恢复时序。历史正常重启证据单列，新增复验需主控另行安排。 | NOT_RUN |
| REC-02 | 启动恢复四态与实际入口门禁 | REUSE_ONLY | 本轮不为QA停机重启；只读现状不证明恢复时序。历史正常重启证据单列，新增复验需主控另行安排。 | NOT_RUN |
| REC-03 | 真实进程崩溃：接收提交后尚未入队 | ISOLATED | 强杀、未知提交、坏存储/初始化/损坏向量仅隔离，禁止作用正式库。 | NOT_RUN |
| REC-04 | 真实进程崩溃：领取后计算中 | ISOLATED | 强杀、未知提交、坏存储/初始化/损坏向量仅隔离，禁止作用正式库。 | NOT_RUN |
| REC-05 | 真实进程崩溃：终态提交前与提交后 | ISOLATED | 强杀、未知提交、坏存储/初始化/损坏向量仅隔离，禁止作用正式库。 | NOT_RUN |
| REC-06 | 提交结果未知：先提交再抛错的三条路径 | ISOLATED | 强杀、未知提交、坏存储/初始化/损坏向量仅隔离，禁止作用正式库。 | NOT_RUN |
| REC-07 | 算法版本不兼容不混算 | ISOLATED | 采用已批准REC-07-R1双版本过滤判据；两版本真实文件库切换/行与向量不变需隔离；旧FAIL永久保留。 | NOT_RUN |
| REC-08 | 存储和初始化失败，以及损坏向量诊断 | ISOLATED | 强杀、未知提交、坏存储/初始化/损坏向量仅隔离，禁止作用正式库。 | NOT_RUN |
| UI-01 | 展示真实状态、排序与分数，不制造AI指标 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-02 | 提交表单边界、缺省渠道和重复ID反馈 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-03 | 任务轮询停止、网络错误不伪造FAILED | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-04 | 失效确认、失败反馈及历史搜索结果 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-05 | 搜索不自动重试，旧响应不覆盖新结果 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-06 | 原文与错误文本不执行HTML/脚本 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-07 | 错误可操作、键盘、小屏和时间展示 | ONLINE_PARTIAL | 主控Chrome Owner实际逐项浏览器证据；普通线上路径可测，受控网络迟到/异常/轮询上限需隔离；旧F05例外不扩展。 | NOT_RUN |
| UI-08 | 服务器上的真实页面端到端闭环 | ONLINE_PARTIAL | 主控Chrome真实HTTPS六接口闭环可新测；本轮未重启时不得把历史重启持久化写成新PASS。 | NOT_RUN |
| OPS-01 | 干净构建与测试实际执行 | REUSE_ONLY | 历史273自动化与CI构建仅来源复用；本轮未clean verify则NOT_RUN，不与HTTP样本加总。 | NOT_RUN |
| OPS-02 | 服务器数据目录、进程与正常重启 | REUSE_ONLY | 服务器Owner只读进程/文件占用/资源可新查；重启冷备恢复未本轮重演不算新PASS。 | NOT_RUN |
| OPS-03 | 备份恢复实际演练，不只生成文件 | REUSE_ONLY | 服务器Owner只读进程/文件占用/资源可新查；重启冷备恢复未本轮重演不算新PASS。 | NOT_RUN |
| OPS-04 | 公网访问保护覆盖页面和API | ONLINE_SAFE | 主控TLS/端口只读预检，A01/A02/A03认证拒绝与HTTPS同源/跨源实测，Chrome页面认证另取证。 | NOT_RUN |
| OPS-05 | Git、AI记录与交付材料的真实性和脱敏 | ONLINE_SAFE | 本轮实际Git diff、敏感扫描、原始证据散列和公开摘要核对；不得声明无限范围无秘密。 | NOT_RUN |
| OPS-06 | 发布与回滚对应同一可追溯版本 | ONLINE_PARTIAL | 主控实际JAR摘要/进程与批准版本比对；未发布/回退，不宣称本轮演练回滚。 | NOT_RUN |
| PERF-01 | 纯入库与CPU模拟负载测量 | ONLINE_PARTIAL | 主控已获正式服务1/2/4/8并发每阶30秒总<=120秒，独立性能预算/原始统计；N100/1000/5000、耗尽及故障恢复仍需隔离或记限制。 | NOT_RUN |
| PERF-02 | 纯检索规模、并发与混合负载 | ONLINE_PARTIAL | 主控已获正式服务1/2/4/8并发每阶30秒总<=120秒，独立性能预算/原始统计；N100/1000/5000、耗尽及故障恢复仍需隔离或记限制。 | NOT_RUN |
| PERF-03 | 热点计数与资源耗尽后的恢复 | ONLINE_PARTIAL | 主控已获正式服务1/2/4/8并发每阶30秒总<=120秒，独立性能预算/原始统计；N100/1000/5000、耗尽及故障恢复仍需隔离或记限制。 | NOT_RUN |
| PERF-04 | 相关性实验：与功能正确性验收分开 | REUSE_ONLY | 历史语言关系观察不变；本轮合成数据无完整人工标签，不报告语义准确率。 | NOT_RUN |

## 当前结论与未执行项

本轮线上、Chrome、资源预检、并发、性能执行均NOT_RUN。尚未形成实际code分布、计数差分、成功QPS、时延或资源结论。旧273自动化与70主编号是不同维度，不加总为343；同一请求/同一方法映射多编号不得重复增加执行数。

线上不能通过公开API观察向量内部字节、run_token和锁状态；不能据HTTP并发推断后台多Worker并行。生产默认一个Worker，竞争用例未观察到特定交错应写“未捕获”，不能改为PASS。数学负分夹具、版本混合、故障回滚、队列1000/容量5000、强杀及数据库破坏不会在线制造；保留隔离待测或环境限制。历史REC-07原FAIL与新R1分开，F05例外不扩展。

执行后由主控提供脱敏请求事件、统一预算账本、每阶段计数快照、服务器只读观测和Chrome证据索引，QA再逐项复核状态与结论。本文件不授权更改服务、生产库或批准产物。

## 定义文件静态校验

2026-10-23本地仅执行Python JSON解析与断言、70编号去重映射、请求上限核算、`git diff --check`及两新增文件有限敏感格式扫描。结果：39个声明用例、12个合成fixture、70个唯一历史编号；当前展开计划上限GET 171、搜索16、提交尝试25、失效2，共214个请求，低于240总预算。替代fixture操作只执行一次，轮询提前结束会减少请求。没有执行任何线上请求，也没有执行Java/Maven测试；静态定义校验不是业务测试PASS。有限格式扫描无匹配，不代表证明所有潜在秘密均不存在。
