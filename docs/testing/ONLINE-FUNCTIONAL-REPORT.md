# FINAL-QUALITY 线上全面功能验收报告

任务ID：FINAL-QUALITY-QA。公开基线 `5f1740d2141473a7595eeb41eb6efb037a847815`；QA分支 `codex/final-quality-qa`。本轮39个声明HTTP场景通过，其中9项引用同一轮首次执行的真实PASS，30项在受控承接后完成；额外完成1次精确渠道负分补测及有界混合负载。本报告独立核证原始白名单事件和聚合口径，**不将历史70条整体改为本轮PASS**。Chrome正式单文档闭环已完成并独立核对，保留网络截断与未观察中间态限制。

## 正式环境、版本与时间

入口为正式HTTPS域名，经过Nginx和Basic Auth；客户端校验TLS、不自动重定向/重试。批准源码 `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，批准JAR SHA-256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`。主控与服务器Owner执行运行版本、Java17进程、systemd、文件占用、businessReady及磁盘/内存/容量门禁；环境来源为本轮`preflight-report.json`，主控提供SHA-256 `08adf0b5d2eb376c0fc6312430b96b3235f1fd4f71aac099786725c9c33f95f1`。QA独立读取功能/压力白名单原件，不读取认证材料、SSH或数据库；该环境报告摘要由主控交接，未由QA重复访问服务器。本报告不把源码HEAD当运行JAR版本。

正式配置保持Worker=1、Queue=64、SearchPermits=1、Hikari最大连接数4。功能基线为9篇既有文档，不能沿用历史7篇。功能新增12篇，压力阶段另新增4篇；Chrome独立渠道另新增1篇，截至本次审查共新增17篇，仍低于全轮20篇上限。最终只读GET确认总文档26篇、剩余容量4974篇，本轮17篇全部SUCCEEDED且vectorReady=true。失效不释放容量，不删除/覆盖数据。

| 阶段 | UTC实测时间 | 实际结果 |
| --- | --- | --- |
| 功能首次执行 | 2026-10-23 04:10:50.205—04:10:55.092 | 9 PASS / 1 FAIL / 29 NOT_RUN；14请求，新增0篇 |
| 精确已知拒绝承接 | 2026-10-23 04:17:54.544—04:18:29.870 | 累计39场景PASS，其中9项真实继承；新增12篇 |
| 独立负分补测 | 2026-10-23 04:26:46.949发起 | 1次搜索，9个命中；前后各1次GET留存计数基线 |
| Chrome正式页面闭环 | 2026-10-23 04:47:49.414结束 | 1篇新增、两次手动搜索、失效和计数闭环；网络捕获不完整 |
| 正式混合阶梯 | 2026-10-23 04:29:55.472—04:31:53.290 | 631请求，首请求至末响应117.818秒；4篇接收且最终成功 |

原始事件仅在授权本地受限目录保留；公开只记录文件名、摘要与聚合数值，不发布凭据、原始敏感载荷或内部路径。

## 功能执行、预算与网关分层

[用例定义](../../tests/performance/functional-cases.json)与[标准库执行器](../../tests/performance/functional_runner.py)均已保存。功能硬预算为总240、GET180（全局最多4RPS）、提交尝试32、搜索16、失效4、成功新增12篇。全部失败/拒绝/超时发出前扣账，无自动重试；任何未知或5xx立即停后续写入，包含搜索。

实际累计116请求：GET72、提交尝试26、搜索16、失效2。首次14条与第二次102条事件按序号合并恰为1—116，无重复/缺号；最短GET发起间隔317.848ms。状态分类为200×70、202×12、400×25、401×1、403×1、404×2、409×2、413×2、429×1；没有未知结果或5xx。202只表示持久化接收，12篇均有真实SUCCEEDED、关联详情及时间字段才确认完成。

首次V-bytes实际收到413但非JSON，旧执行器报`ERROR_NOT_JSON`并正常停止。**该历史FAIL永久保留**。服务器Owner随后只读确认Nginx拒绝262145字节正文；主控明确允许仅对该已知拒绝再次发送一次，既无未知提交也无新增文档。窄续跑继承14请求和原9项PASS及其原件SHA，不覆盖首次目录，不重复9个已通过请求。入口重新读取原9篇基线、精确渠道为空并比对原metadata SHA。

第二次V-bytes实测413、Content-Type `text/html`且Server为Nginx，按冻结契约允许的网关非JSON边界记录`layer=GATEWAY_413`、`actual_code=null`。没有伪造`REQUEST_TOO_LARGE`；应用层JSON 413分支为**NOT_RUN**，chunked精确字节边界也未在线执行。只有该case/状态/媒体类型/服务器组合享有此分层，其它应用错误仍断言真实JSON code/message/fieldErrors。

| 接口 | 实测覆盖 | 结果与边界 |
| --- | --- | --- |
| POST /api/documents | 12篇接收、重复ID竞争、ID/文本/渠道/JSON/媒体类型/长度拒绝、HTTPS同源与跨源 | 12次202、2次409；两客户端重复ID只有一个202；参数与错误结构按真实code验证 |
| GET /api/tasks/{taskId} | 有界轮询、UUID拒绝、不存在任务、成功关联 | 12篇SUCCEEDED；未强制观察全部中间态，不冒充受控失败四态 |
| GET /api/documents | 全量基线、精确渠道、3条分页、排序、空尾页与非法分页 | 检查不重不漏、时间倒序/ID升序及GET不计数 |
| GET /api/documents/{documentId} | 12篇原文/状态/计数前中后快照、404 | 原文保留，20000码点边界与201补充码点摘要真实检查 |
| PUT /api/documents/{documentId}/invalidation | race失效及重复失效 | 首次时间不变，SUCCEEDED与invalidated正交；没有证明CPU计算中失效 |
| POST /api/search | 参数拒绝、渠道隔离、空查询、等价规范化、计数、有限并发与失效排除 | 16次内有9次验证400、6次200、1次SEARCH_BUSY；另有独立负分补测1次 |

认证GET实际401，跨源写实际403，同源HTTPS实际202；没有关闭认证。所有成功搜索都明确指定本轮精确channel，neighbor/default从不进入主渠道结果。省略/null搜索会影响历史计数，本轮未执行，保留隔离待测。

## 命中账本、负分与数据不变

QA逐事件重建6个成功搜索的40次命中，完全吻合执行器计数差分。首次空查询S01返回主渠道10篇零分结果并按ID排序；S02前两篇canonical/normalized均为1.0、其余按原始分数排序，**S02本身没有负分**。失效完成后S05只返回其余9篇。

独立补测用批准JAR提取B1类的离线oracle选定查询，再在正式精确渠道单发Top-K100搜索，实际返回8篇0分及ordinary的`-0.06299407930421365`，证明负分未被截断。该请求单列，不能改写runner的16次预算或S02结果。

| 合成别名 | 功能阶段增量 | 额外负分增量 | 混合负载增量 |
| --- | ---: | ---: | ---: |
| canonical | 4 | 1 | 149 |
| ordinary | 5 | 1 | 0 |
| race（功能后失效） | 3 | 0 | 0 |
| empty / whitespace / unicode / normalized / duplicate / snippet / boundary（每篇） | 4 | 1 | 0 |
| neighbor / default（每篇） | 0 | 0 | 0 |

正式混合阶段149次成功搜索每次仅命中canonical，独立计数审计delta恰为149；57次429不计成功、不增加命中。压力新文档走独立-ingest渠道，搜索始终固定9篇有效候选，避免把候选增长误归因于并发变化。

功能与压测执行器分别在内存逐字段比对既有9篇metadata及计数，完成结果均为不变；非命中测试文档除明确许可的失效/计数变化外保持不变。QA重算事件账本并核对完成断言与散列；白名单事件不含原行明文，因此不声称从公开摘要重新构造了全部数据库字段。没有删除、清空、覆盖、直接打开生产H2或重新部署。

## 并发、任务时间与性能口径

功能12篇全部SUCCEEDED；按服务器submittedAt/startedAt/finishedAt独立计算排队38.154—489.011ms，处理区间3.592—409.684ms，最大同时处理时间区间数为1、无重叠。压力4篇也全部SUCCEEDED、最大区间数1。客户端Barrier只证明同时发起；没有从这些区间推断多个Worker并行，Worker2/4为NOT_RUN。

功能第二次监控36次采样、35点jstat，无abort、进程PID不变；压力119次采样、118点jstat，无abort、PID不变。单次线程快照观察到1个WAITING Worker，但未捕获B1计算栈。Hikari精确active/idle/waiting、执行器精确队列长度和逐次GC暂停均NOT_MEASURED，不能用零个借连接等待栈替代池指标。

压力预算在开压前有记录地由480搜索/960GET修订为2400搜索/4800GET，提交仍最多4；每VU160搜索/320GET，1/2/4/8并发及总120秒窗口未扩大。实际GET421（详情214、列表207）、搜索206（149成功、57拒绝）、提交4；状态合计200×570、202×4、429×57，共574个业务成功响应；无未知结果或5xx，没有BUDGET_LIMIT。额外负分搜索、前后诊断GET及客户端TLS单GET均单列，不混入631条压力样本。

| 客户端并发 | 27秒内发起RPS | 27秒内业务成功QPS | 成功响应p95 ms | 成功响应p99 ms | 429（含收尾） |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2.926 | 2.889 | 495.10 | 509.82 | 0 |
| 2 | 3.852 | 3.741 | 777.90 | 909.38 | 1 |
| 4 | 7.037 | 6.296 | 1269.00 | 1281.56 | 17 |
| 8 | 9.556 | 7.852 | 1589.20 | 2828.66 | 39 |

每阶段固定27秒发起/完成窗，最多3秒收尾；阶段起点0/30/60/90秒，实际无重叠。QPS仅计27秒内完成的200/202，排除429；202是接收成功，不能当文档完成吞吐。成功延迟样本为27秒内发起且在收尾前完成的成功响应，保留尾请求，按线性插值计算分位数。429全阶段数为0/1/17/39，其中27秒内完成0/1/16/38；各阶段收尾响应数1/2/4/8。不能把QPS分子和延迟样本数直接等同。

这是固定N=9、K=1、12000码点查询的**短时混合负载**，不是纯GET/纯搜索最大QPS或SLA。资源采样、图表及更多限制由主控在独立并发/性能报告记录；Worker2/4、N100/1000/5000与完整饱和恢复未测。

最终2026-10-23 04:49:20.880 UTC的独立1次GET审计确认：原9篇metadata与计数SHA仍为`1f3b24885328d543217b2ecbdb823eafc28e52fe308d507d44239e578ce15500`，与功能首轮基线完全相同；压力前21篇仅canonical按149次成功命中增加，其余字段与计数保持；4篇ingest计数0，browser已失效且计数1。此终场GET独立记录，不追加进功能/压力原预算或样本。

## Chrome正式页面闭环与证据限制

主控使用正式HTTPS Chrome真实页面，在独立browser渠道新增1篇，响应202后查看真实任务，首次可见状态已经SUCCEEDED；没有观察RUNNING转移，不能声称重测轮询状态迁移。列表实际1条；详情为就绪、有效、hitCount=0。同文Top-K1手动搜索返回1条、显示1.0000，读取详情计数为1；接受原生confirm失效后，任务仍SUCCEEDED、vectorReady=true且invalidated=true。第二次手动搜索返回0条，再次GET详情计数仍为1。

Unicode与含`<script>`的原文按纯文本显示；结果材料记录`#detail-text`内script子节点0，console warn/error为0。QA独立读取各阶段可访问性快照并目检最终截图，确认最终SUCCEEDED/就绪/已失效/count1、原文纯文本与UTC+8时间。旧搜索结果/列表可能保持历史快照，不把这些快照当新请求结果。

网络白名单只有15个已捕获事件，confirm与第二次搜索期间工具返回`truncated=true`，因此`networkComplete=false`。已捕获提交202、任务/详情/列表及首次搜索的部分200可核对；**不能把15个事件当15次完整HTTP请求，不能据此报告Chrome完整网络总数或GET全程RPS**。两次手动搜索与一次失效按实际界面动作、后续状态和计数记录，不并入功能runner116请求或压力631样本。Chrome覆盖范围为正常生产闭环，受控网络异常、旧响应覆盖、小屏/键盘和重启仍未本轮重演。

## 历史70条与本轮实际覆盖

来源：[原70条映射](final-release-local-cases-matrix.md)及[REC-07修订裁决](../design/REC-07-验收判据裁决-20261023.md)。原件、历史273自动化和REC-07原FAIL均保留；39个HTTP场景与70个需求编号是不同维度，不相加。

70个原编号本轮分层结果为7 ONLINE_PASS、1 LOCAL_PASS、29 PARTIAL、33 NOT_RUN；这不是70条全部通过，也不能与39个HTTP场景数量相加。

本轮`ONLINE_PASS`表示该行可安全观察的接口不变量已有实际证据；`PARTIAL`只覆盖描述的子范围，不能当整条原联合断言通过；`LOCAL_PASS`为本轮重新执行的本地工程回归，不能算线上PASS；`NOT_RUN`没有本轮覆盖，历史证据复用也不转新PASS。执行分层保留原计划，实际列单独更新。Chrome仅按实际子范围更新为PARTIAL。

| 原编号 | 目标摘要 | 执行分层 | 实际映射与限制 | 本轮状态 |
| --- | --- | --- | --- | --- |
| ALG-01 | 256维、返回类型与有限数值 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-02 | 重复调用按位相同且结果不共享可变数组 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-03 | 混合文本并发向量化无串扰 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-04 | 独立JVM、默认Locale及固定环境的确定性 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-05 | 空字符串和纯空白产生零向量 | ONLINE_PARTIAL | F01/F02/S01可验证空文成功、就绪及零查询零分；256个float逐位为零需隔离。 | PARTIAL |
| ALG-06 | 规范化等价对与原文不改写 | ONLINE_PARTIAL | F01/R02/S02检查规范化等价检索及原文保留；按位相同需隔离。 | PARTIAL |
| ALG-07 | Unicode与n-gram边界 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-08 | 向量二进制codec、字节序和损坏输入 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-09 | 余弦数学oracle含零、负值和非单位向量 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-10 | Top-K固定oracle：负分、同分和K边界 | ONLINE_PARTIAL | S01/S02/S03检查零分、同分、K>N与空候选；人为负向量/随机oracle需隔离。 | PARTIAL |
| ALG-11 | Top-K随机对照与近似同分 | REUSE_ONLY | 六接口不返回向量；内部位模式/codec/数学oracle及JVM确定性仅引用历史证据，不能算本轮线上PASS；新增复验需隔离。 | NOT_RUN |
| ALG-12 | 入库/查询同版本、增量稳定与CPU实现审查 | ONLINE_PARTIAL | 主控版本/配置只读核对与F01/S02真实入库查询；位级稳定/CPU实现需独立源码证据。 | PARTIAL |
| API-01 | 完整业务闭环与元信息 | ONLINE_SAFE | F01/F02/R01/R02/C01/S05完成六接口闭环；只按实际观察状态记录。 | ONLINE_PASS |
| API-02 | documentId校验和重复ID | ONLINE_SAFE | F03/F04/V-submit-bad_id检查409、非法ID、同ID并发一次接收与原文不覆盖。 | ONLINE_PASS |
| API-03 | text空值、长度与规范化膨胀边界 | ONLINE_SAFE | F01及V-submit-*检查空/null/缺失、20000/20001码点与NFKC膨胀；每次拒绝核对无新增。 | PARTIAL |
| API-04 | 提交与搜索的渠道缺省语义不同 | ONLINE_PARTIAL | default提交、精确channel/邻渠道/不存在channel与空白拒绝可线上；省略/null搜索会影响历史计数，移隔离；default第二篇null提交由容量余量另分配。 | PARTIAL |
| API-05 | topK与分页的数值边界 | ONLINE_SAFE | V-topK-*及R03/S01验证类型边界、K>N与分页；所有失败保留实际code。 | PARTIAL |
| API-06 | 先过滤后Top-K，不遗漏低分合格候选 | ONLINE_PARTIAL | S01/S05验证真实当前成功、渠道及失效过滤；FAILED/双版本/人为负分候选需隔离。 | PARTIAL |
| API-07 | 文档列表、详情和任务关联不改变计数 | ONLINE_SAFE | F02/R01/R02核对任务关联、列表排序分页、UTC/null与字符串计数，GET前后不增计数。 | ONLINE_PASS |
| API-08 | 不存在对象与幂等失效 | ONLINE_SAFE | R04/R05/C02验证404与失效幂等首次时间，真实SUCCEEDED+invalidated独立。 | ONLINE_PASS |
| API-09 | 请求体、Content-Type与非法JSON | ONLINE_PARTIAL | V-json/V-media实际400；超体积由Nginx返回HTML 413，网关拒绝已核证，应用JSON 413及chunked边界未触达。 | PARTIAL |
| API-10 | 异常结构、安全信息与数据库故障 | ONLINE_PARTIAL | 线上所有实际错误检验code/message/fieldErrors及脱敏；数据库故障/损坏不在线制造。 | PARTIAL |
| API-11 | 只给最终结果计数，读取和被过滤候选不计数 | ONLINE_SAFE | S01—S05/R02以成功hits逐篇建立精确delta账本；分阶段冻结其它runner，未知即停写。 | ONLINE_PASS |
| API-12 | 空库、空查询、零分和负分规则 | ONLINE_PARTIAL | S01空查询/零分、S03空渠道及额外负分补测均真实执行；正式库非空，不把空渠道冒充真空库。 | PARTIAL |
| TASK-01 | 受控证明异步提交，不靠响应速度猜测 | ONLINE_PARTIAL | F01/F02记录真实202和有界最终成功，不保证观察到QUEUED/RUNNING；受控暂停/失败四态不能线上注入。 | PARTIAL |
| TASK-02 | 四态、计算失败可查且不拖死后续任务 | ONLINE_PARTIAL | F01/F02记录真实202和有界最终成功，不保证观察到QUEUED/RUNNING；受控暂停/失败四态不能线上注入。 | PARTIAL |
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
| CON-03 | 计算中失效，完成不复活 | ONLINE_PARTIAL | C01在任务已经SUCCEEDED后失效；没有本轮“CPU计算中失效”证据，不算该竞态已测。 | NOT_RUN |
| CON-04 | 在途检索与失效的重叠语义 | ONLINE_PARTIAL | C01真实并发发起且按快照语义接受已捕获候选；未捕获内部交错不得写成竞态全覆盖。 | PARTIAL |
| CON-05 | 失效已经完成后开始的新检索必须排除 | ONLINE_SAFE | C01完成后S05新搜索明确排除race，详情保留。 | ONLINE_PASS |
| CON-06 | 多次并发命中同文档无丢失更新 | ONLINE_SAFE | S04两客户端200/429已核；正式混合阶梯149次成功搜索均命中canonical，压测前后delta精确149，429不计数。 | ONLINE_PASS |
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
| UI-01 | 展示真实状态、排序与分数，不制造AI指标 | ONLINE_PARTIAL | Chrome真实状态、1.0000分数、列表1条/详情计数已核；没有把分数叫语义准确率；复杂排序范围仍以HTTP为证，UI联合项只部分覆盖。 | PARTIAL |
| UI-02 | 提交表单边界、缺省渠道和重复ID反馈 | ONLINE_PARTIAL | Chrome提交Unicode/HTML纯文本及精确独立渠道真实202；未通过UI重演全部表单边界/default/重复反馈。 | PARTIAL |
| UI-03 | 任务轮询停止、网络错误不伪造FAILED | ONLINE_PARTIAL | 查看任务后首次可见已SUCCEEDED；未观察RUNNING转移，未重演轮询停止/错误或历史F05，不声称轮询状态机全覆盖。 | PARTIAL |
| UI-04 | 失效确认、失败反馈及历史搜索结果 | ONLINE_PARTIAL | 原生confirm接受后失效，详情保留SUCCEEDED/ready并显示失效；未注入失效失败，旧结果为历史快照。 | PARTIAL |
| UI-05 | 搜索不自动重试，旧响应不覆盖新结果 | ONLINE_PARTIAL | 两次手动检索及计数1保持已核；网络truncated=true，不能证明全量请求数/全部无重试或过期响应竞态。 | PARTIAL |
| UI-06 | 原文与错误文本不执行HTML/脚本 | ONLINE_PARTIAL | Unicode与script样本文字显示，DOM script子节点0，warn/error0；QA目检最终截图；错误文本XSS范围未重演。 | PARTIAL |
| UI-07 | 错误可操作、键盘、小屏和时间展示 | ONLINE_PARTIAL | 详情文字状态、UTC+8时间及按钮可见；未独立重演小屏和键盘可达性，因此仅部分覆盖。 | PARTIAL |
| UI-08 | 服务器上的真实页面端到端闭环 | ONLINE_PARTIAL | 正式HTTPS同源提交/任务/列表/详情/检索/失效闭环已核；本轮未重启，网络捕获不完整，不能整体覆盖原联合项。 | PARTIAL |
| OPS-01 | 干净构建与测试实际执行 | LOCAL_REGRESSION | 文稿复核后本轮实际Java17 clean verify通过104 Unit+126 IT，Node另43通过；首轮沙箱bind权限错误7项原件保留，允许回环端口后全量重跑成功；这是本地工程回归，不是线上HTTP测试。 | LOCAL_PASS |
| OPS-02 | 服务器数据目录、进程与正常重启 | REUSE_ONLY | 服务器Owner只读进程/文件占用/资源可新查；重启冷备恢复未本轮重演不算新PASS。 | PARTIAL |
| OPS-03 | 备份恢复实际演练，不只生成文件 | REUSE_ONLY | 服务器Owner只读进程/文件占用/资源可新查；重启冷备恢复未本轮重演不算新PASS。 | NOT_RUN |
| OPS-04 | 公网访问保护覆盖页面和API | ONLINE_SAFE | 主控TLS/端口只读预检，A01/A02/A03认证拒绝与HTTPS同源/跨源实测，Chrome页面认证另取证。 | PARTIAL |
| OPS-05 | Git、AI记录与交付材料的真实性和脱敏 | ONLINE_SAFE | 本轮实际Git diff、敏感扫描、原始证据散列和公开摘要核对；不得声明无限范围无秘密。 | PARTIAL |
| OPS-06 | 发布与回滚对应同一可追溯版本 | ONLINE_PARTIAL | 主控实际JAR摘要/进程与批准版本比对；未发布/回退，不宣称本轮演练回滚。 | PARTIAL |
| PERF-01 | 纯入库与CPU模拟负载测量 | ONLINE_PARTIAL | 正式1/2/4/8客户端有限混合阶梯已测，固定N=9、K=1、查询12000码点，27秒测量+最多3秒收尾；纯接口上限、规模阶梯、Worker2/4及故障恢复未测。 | PARTIAL |
| PERF-02 | 纯检索规模、并发与混合负载 | ONLINE_PARTIAL | 正式1/2/4/8客户端有限混合阶梯已测，固定N=9、K=1、查询12000码点，27秒测量+最多3秒收尾；纯接口上限、规模阶梯、Worker2/4及故障恢复未测。 | PARTIAL |
| PERF-03 | 热点计数与资源耗尽后的恢复 | ONLINE_PARTIAL | 正式1/2/4/8客户端有限混合阶梯已测，固定N=9、K=1、查询12000码点，27秒测量+最多3秒收尾；纯接口上限、规模阶梯、Worker2/4及故障恢复未测。 | PARTIAL |
| PERF-04 | 相关性实验：与功能正确性验收分开 | REUSE_ONLY | 历史语言关系观察不变；本轮合成数据无完整人工标签，不报告语义准确率。 | NOT_RUN |

## 脱敏证据索引与独立核证

| 本地原件逻辑名 | SHA-256 |
| --- | --- |
| 终场 final-audit.json（1次只读GET） | `eae95ec4a1502699bc120bf58e3a0cceff5112d1ff17312dac5826d8310e2fc0` |
| Chrome result.json | `60bf559daa337e30a8767e04ee908686eec571fc299b8eaaa7a97f160229fb5a` |
| Chrome 12-final-detail.txt | `703826582a2bdc72470e97252ec192e1220666cdf28b3c08f464935650638bfd` |
| Chrome 12-final-detail.png（本地只读目检） | `e854e44f064087182876824e1198577fe79e21eca935124e76e602de68ea401a` |
| Chrome network-whitelist.json（非完整网络） | `94a8790c5d7b90adc18e7de275a51ba305f3218b62245fc4ddfcb4b8e8e012e3` |
| 服务器Owner preflight-report.json（主控交接） | `08adf0b5d2eb376c0fc6312430b96b3235f1fd4f71aac099786725c9c33f95f1` |
| 首轮 events.jsonl | `3906a325985d635daf173857bebce71570f5832f741ca9900318ee494ada5f1a` |
| 首轮 case-status.json（FAIL保留） | `87acdd60f9893f4cdeb1c809ce5c07cedeb2c7b1715f45677d8612d880f83c85` |
| 第二次 events.jsonl | `ac5a51e60e0aaf5d9a49112bf6f61b0549437124f80d1d3a09a756a79d76bc99` |
| 第二次 case-status.json | `ea3f0bd7af8959ba886d23b0275db207d946809f3306ce1d93d39c0f340768f1` |
| 第二次 consistency.json | `eb16c94f2a261140c3b4f04828c08a6cd77d246090ef0b4813eb4c8c18e773f2` |
| 第二次 task-timing.json | `c111309e43e33a9c87a50aa03915b95195ac38c3b4cc5c2659cd30f14b75b97a` |
| 独立负分 negative-probe.json | `bee73a2f3060948220a4bab50eb7eb2094fc93bffd9e92bea4a9e0fe2aa5654d` |
| 压力 console.jsonl | `32986415b2172f73ef9beb07232af1736d1ee3e7b2cf79a18a055b0849ee7f49` |
| 压力 metrics.jsonl | `f96756f10b0bd03799d526bbce16d5b8898bad2685aded79ee64835e7959c4f5` |
| 压力 summary.json | `d2335f21d1037abb2723f23f15dd695806c857f1618ef18cf7f834aa94f52529` |
| 压力 counter-audit.json | `144499290d69b26bc370c5a5bc739fd08e7e78263a422156040ba1aa3caaaeb0` |
| 开压前 pressure-budget-revision.json | `897ff8a3514e7089a98b836ca54bc661ccfff34a9dca4f3bc1f754e46bd5df8b` |
| 性能 aggregate.json | `84fbc19680518163e7d6b3f3b133aa66ad9eef9a0616296bcca40157e396c15d` |

QA只读JSON/JSONL原件，独立执行Python断言：合并116连续序号与分类预算、9项继承原件SHA、40次功能hits ledger、631条压力请求及149次增量、四阶段窗口/无重叠、27秒QPS和含尾分位数、12+4任务时序、4份SVG的XML语法。全部通过；SVG未由QA做渲染目检，不据XML声称图像版式通过。没有由QA发起额外线上请求或读取凭据。

## 当前结论与未执行项

声明HTTP功能场景39 PASS，额外负分1次及有限混合阶梯有真实证据；首次413解析FAIL保持历史。Chrome单文档闭环已核，含网络不完整与未观察RUNNING的明确限制；全轮新增17篇。应用JSON 413、内部故障/旧token/提交未知/受控回滚、真实强杀和本轮重启冷备、完整Worker/候选规模对比均未在线重演，不把此前273/70结果冒充本轮新测。

没有从本轮HTTP成功推断向量内部字节、锁覆盖、全部任务中间态或内部交错。生产默认1 Worker，保护性429正常返回后服务保持就绪，但不等于完成资源耗尽和故障恢复验收。F05历史批准例外不扩展；REC-07-R1仅保留历史判据与证据，原FAIL不改。

## 收尾工程回归（与线上样本分开）

主控于2026-10-23 12:59:56北京时间完成本轮Java17 `./mvnw -B -ntp clean verify`，104 Unit + 126 IT通过，失败/错误/跳过0；另实际执行Node43通过。两者合计273是本次重新运行同一套工程测试，不与历史273、39个HTTP场景或70需求映射相加。首次沙箱禁止Socket.bind导致7个启动错误，原Surefire报告完整保留；未削弱断言，允许临时回环端口后全量重跑成功。完整verify日志SHA `25a4ef9e8d8104871fa5eeabaf16c76ef68e8c9c2372ab308e108abaf1e5acf2`。新建本地测试产物未部署，不替换批准生产JAR。
