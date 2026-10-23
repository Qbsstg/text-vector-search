# 本地新增70条用例盘点与方法映射（盘点与分层执行跟踪）

> **历史阶段说明**：本文件记录70 条用例的来源映射及部署前历次结果；表内原 REC-07 FAIL 和未执行状态不回填。正文、原始 FAIL、测试数量和时间线原样保留，不代表项目当前仍未交付。最终结论请查阅[《最终交付聚合结果》](最终交付聚合结果.md)。
> 当前采用用户批准的 REC-07-R1 与首发验收范围，70 条主用例通过；原 REC-07 FAIL 不改写，F05 仍为 `NOT_RUN / APPROVED_EXCEPTION`。原材料及受限证据引用仅用于溯源，不是公开下载链接。

基线`da5867a5ecdb5ae4705e3643e3e96298b9bd7574`；批准发布包仍为d016候选，测试源码新增不冒充发布包改变。原始资料只读保留，未原样推送公开仓库。下表是逐条执行追踪入口，不把旧CI自动当作本轮新增用例通过。

## 来源及去重

主树`docs/测试工作/`10个未跟踪文件（已核9条内容SHA全部一致；01在README/SHA256SUMS中的旧文件名与实际文件名不同，内容相同），含02规格、04同一70条JSON、01问题说明、README、05执行模板、SHA256SUMS及4个夹具JSON。五个agent worktree盘点时均干净；未发现其他未提交自动测试。02与04的70编号及全部预期逐条对应，因此是一套70主用例、不是140；01的Q01—Q26和05模板引用同一编号，不另外计数。夹具16文档/4查询、4工程等价对/8观察对、6余弦/5TopK、14请求是子样本，不擅自替代或增加主用例总数。REQ-13/14原未冻结项按当前API分别200/400；原件不回写。

REC-07原预期禁止过滤旧数据后空hits成功，与冻结B4双版本候选过滤冲突；将用隔离真实H2/HTTP诊断记录原预期FAIL/契约冲突，不能改生产、不能将原预期直接标PASS。TASK-08的随后写FAILED是可选路径；实际数据库提交未知按冻结契约降级保留RUNNING，不能强制伪FAILED。

## 逐条映射

所有来源均为主树原件；JSON定位统一为`docs/测试工作/04_结构化测试用例.json#/cases/<原编号>`，下面列02精确行号。方法路径：IT位于`src/test/java/dev/twelvetimes/vector/acceptance/`，Test位于`src/test/java/dev/twelvetimes/vector/unit/`；LocalCasesIT为本轮新增独立实现。既有方法有多编号映射时只运行/计一次工程测试，逐条核对各原预期。

| 原编号 | 原始来源 | 测试目标/预期 | 类型 | 具体方法/实际步骤映射 | 本轮实际/状态/证据 |
| --- | --- | --- | --- | --- | --- |
| ALG-01 | docs/测试工作/02_测试用例与验收方案.md:224；04#ALG-01 | 256维、返回类型与有限数值；结果为非null float[256]。；所有元素有限，不是NaN/Infinity。；不因非空文本就强制要求范数非零；若非零，归一化误差按测试契约例如1e-6校验。 | Unit/IT/HTTP | B1AlgorithmIT#matchesIndependentlyGeneratedGoldenBytes;CharNgramTextEmbedderTest#singleChineseAndSupplementaryCodePointsProduceFiniteUnitVectors | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-02 | docs/测试工作/02_测试用例与验收方案.md:248；04#ALG-02 | 重复调用按位相同且结果不共享可变数组；同文所有位模式一致。；调用方修改某次返回值不影响后续计算。；不强制不同文本向量必不同，因为存在哈希碰撞。 | Unit/IT/HTTP | B1AlgorithmIT#sharedInstanceIsDeterministicUnderControlledConcurrencyAndReturnsFreshArrays | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-03 | docs/测试工作/02_测试用例与验收方案.md:273；04#ALG-03 | 混合文本并发向量化无串扰；没有共享摘要器、数组或累加器造成的跨调用污染。；无异常、NaN或丢失结果。 | Unit/IT/HTTP | B1AlgorithmIT#sharedInstanceIsDeterministicUnderControlledConcurrencyAndReturnsFreshArrays | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-04 | docs/测试工作/02_测试用例与验收方案.md:297；04#ALG-04 | 独立JVM、默认Locale及固定环境的确定性；相同算法/运行环境下逐位一致，不受默认Locale影响。；不同JDK升级结果不预先假定相同；差异需要版本评审。 | Unit/IT/HTTP | B1AlgorithmIT#separateJvmReportsSameVersionDigestAndEveryEncodedByte;B1AlgorithmIT#unicodeBoundariesExpansionAndDefaultLocaleFollowContract | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-05 | docs/测试工作/02_测试用例与验收方案.md:321；04#ALG-05 | 空字符串和纯空白产生零向量；每个元素是0.0f，不出现除零。；空文本不被当作向量器失败。 | Unit/IT/HTTP | CharNgramTextEmbedderTest#emptyAndUnicodeWhitespaceProduceFreshPositiveZeroVectors | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-06 | docs/测试工作/02_测试用例与验收方案.md:344；04#ALG-06 | 规范化等价对与原文不改写；等价规范化结果生成相同向量。；详情保存并返回各自原文，不把规范化文本覆盖原文。 | Unit/IT/HTTP | CharNgramTextEmbedderTest#nfkcRootCaseAndWhitespaceRulesMatchCanonicalText;LocalCasesIT#四组工程等价对按位一致且各自原文真实落库 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-07 | docs/测试工作/02_测试用例与验收方案.md:367；04#ALG-07 | Unicode与n-gram边界；特征与约定一致；不声称code point就是完整字素簇。；不能用“这两个最终向量必须不同”替代特征断言，哈希仍可碰撞。 | Unit/IT/HTTP | B1AlgorithmIT#unicodeBoundariesExpansionAndDefaultLocaleFollowContract | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-08 | docs/测试工作/02_测试用例与验收方案.md:391；04#ALG-08 | 向量二进制codec、字节序和损坏输入；合法向量逐位往返不变。；损坏数据不被静默补零或截断；错误可诊断。 | Unit/IT/HTTP | B1AlgorithmIT#codecPreservesRawFiniteBitsAndBigEndianWithIndependentReference;LocalCasesIT#数学向量经真实H2往返保持每个原始位 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-09 | docs/测试工作/02_测试用例与验收方案.md:415；04#ALG-09 | 余弦数学oracle含零、负值和非单位向量；同向1、反向-1、正交0、一侧或两侧零向量0、与3/4向量为0.6。；所有分数有限，规范范围[-1,1]；不把负分转为0。 | Unit/IT/HTTP | B1AlgorithmIT#cosineHandlesNonUnitExtremeZeroOppositeAndInvalidVectors;LocalCasesIT#六组余弦与五组TopK原始数学夹具逐条断言 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-10 | docs/测试工作/02_测试用例与验收方案.md:438；04#ALG-10 | Top-K固定oracle：负分、同分和K边界；按score降序、精确同分按ID升序；同分边界保留正确ID。；零分与负分不擅自丢弃；数量min(K,N)，空候选返回空。 | Unit/IT/HTTP | B1AlgorithmIT#topKMatchesIndependentFullSortAcrossShufflesTiesAndKBoundaries;LocalCasesIT#六组余弦与五组TopK原始数学夹具逐条断言 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-11 | docs/测试工作/02_测试用例与验收方案.md:462；04#ALG-11 | Top-K随机对照与近似同分；选中ID和顺序完全一致；使用原始分数排序，不先四舍五入。；重复运行可复现反例。 | Unit/IT/HTTP | B1AlgorithmIT#topKMatchesIndependentFullSortAcrossShufflesTiesAndKBoundaries | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| ALG-12 | docs/测试工作/02_测试用例与验收方案.md:485；04#ALG-12 | 入库/查询同版本、增量稳定与CPU实现审查；两条路径同配置同向量，新增文档不改变既有映射。；轮数或规则影响输出时版本随之改变。；不以“至少耗时若干毫秒”作为正确性阈值；真实CPU量留性能实验。 | Unit/IT/HTTP | B1AlgorithmIT#productionSpringWiringUsesSingleEmbedderAndConfiguredRounds;LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-01 | docs/测试工作/02_测试用例与验收方案.md:512；04#API-01 | 完整业务闭环与元信息；提交202，任务可查并关联正确文档；成功后就绪。；提交和完成时间存在、渠道正确；只返回有效就绪候选。；失效完成后的新检索排除该篇，详情仍可查。 | Unit/IT/HTTP | LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-02 | docs/测试工作/02_测试用例与验收方案.md:537；04#API-02 | documentId校验和重复ID；重复409，不覆盖原文，不新增任务。；非法ID按契约400，无残留文档或任务。；ID按字符串处理，不能前端私自转数字。 | Unit/IT/HTTP | B2HttpIT#JSON类型空白分页与来源错误保持统一结构且不落库;B2AdmissionIT#十二个并发重复ID只接收一次并保留获胜原文 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-03 | docs/测试工作/02_测试用例与验收方案.md:561；04#API-03 | text空值、长度与规范化膨胀边界；null和缺失400；空串和约定空白合法，最终就绪零向量。；超过任一文本上限明确拒绝，无静默截断。；按约定code point计数而非误用UTF-16长度；输入类型不合法按冻结契约处理。 | Unit/IT/HTTP | B2HttpIT#规范化扩张上限与补充码点通过真实HTTP校验;B4HttpIT#原始规范化长度和渠道码点边界在真实HTTP校验 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-04 | docs/测试工作/02_测试用例与验收方案.md:586；04#API-04 | 提交与搜索的渠道缺省语义不同；提交缺省为default；搜索缺省搜索全部渠道。；明确渠道只返回对应候选，不存在渠道返回空。；显式空白400；null处理不擅自从旧资料补默认。 | Unit/IT/HTTP | B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界;LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-05 | docs/测试工作/02_测试用例与验收方案.md:611；04#API-05 | topK与分页的数值边界；合法值按契约处理；K>N返回全部合格候选。；非法数值400，不发生计数和文档写入。；字符串数字是否拒绝须以冻结类型契约为准，不让框架默认悄悄决定。 | Unit/IT/HTTP | B2HttpIT#JSON类型空白分页与来源错误保持统一结构且不落库;B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-06 | docs/测试工作/02_测试用例与验收方案.md:635；04#API-06 | 先过滤后Top-K，不遗漏低分合格候选；只返回FAQ-A、FAQ-B，顺序为0再-1，不能因高分非法候选占位而缺项。；未完成、失败、失效、跨渠道均排除；不暗设分数阈值。 | Unit/IT/HTTP | B4SearchIT#状态双版本失效与渠道在TopK之前过滤;LocalCasesIT#原始过滤数学夹具先过滤再返回零负分 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-07 | docs/测试工作/02_测试用例与验收方案.md:659；04#API-07 | 文档列表、详情和任务关联不改变计数；元信息与关联正确，分页筛选不丢失；空页合法。；GET不增加hitCount。；未完成时间为null/缺省等按契约统一，不伪造时间。 | Unit/IT/HTTP | B4HttpIT#真HTTP提交真实B1后检索计数详情且snippet按码点;B2HttpIT#四态查询字符串大计数排序与失效幂等保持独立 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-08 | docs/测试工作/02_测试用例与验收方案.md:684；04#API-08 | 不存在对象与幂等失效；未知对象404，不生成占位记录。；重复失效成功且首次invalidatedAt不变。；任务成功与失效可同时展示。 | Unit/IT/HTTP | B2HttpIT#四态查询字符串大计数排序与失效幂等保持独立;LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-09 | docs/测试工作/02_测试用例与验收方案.md:708；04#API-09 | 请求体、Content-Type与非法JSON；超体积413；非法JSON400；媒体类型按已冻结错误码拒绝。；所有拒绝无业务落库；不将字节限额与字符限额混为一谈。 | Unit/IT/HTTP | B2HttpIT#默认正文上限按实际chunked字节判断含边界且超限不落库;B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-10 | docs/测试工作/02_测试用例与验收方案.md:732；04#API-10 | 异常结构、安全信息与数据库故障；HTTP码/业务码与契约一致，不把失败伪装为200。；不返回SQL、连接密码、文件路径敏感信息或完整堆栈。；errorMessage有界；若requestId未实现，不伪造字段。 | Unit/IT/HTTP | B3TerminalIT#失败分类可靠持久化且不泄露异常原文;B4SearchIT#坏候选和超容量失败不能计数且许可释放 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-11 | docs/测试工作/02_测试用例与验收方案.md:756；04#API-11 | 只给最终结果计数，读取和被过滤候选不计数；非最终候选、失效或跨渠道文档不加。；每次搜索每篇至多加一次；重复客户端请求是新检索。；结果成功以计数提交成功为前提。 | Unit/IT/HTTP | B4SearchIT#成功零负同分无阈值且仅最终TopK计数;LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| API-12 | docs/测试工作/02_测试用例与验收方案.md:781；04#API-12 | 空库、空查询、零分和负分规则；空库成功返回空hits且无统计写入。；空查询按ID升序返回min(K,N)个0分结果并正常计数。；负分仍可入选，不被错误剔除。 | Unit/IT/HTTP | B4SearchIT#成功零负同分无阈值且仅最终TopK计数;LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-01 | docs/测试工作/02_测试用例与验收方案.md:808；04#TASK-01 | 受控证明异步提交，不靠响应速度猜测；HTTP返回不依赖计算完成。；release前文档不可检索；状态按观察阶段为QUEUED或RUNNING，不要求202返回时仍QUEUED。；释放后成功就绪；测试超时只是失败保护，不是性能SLA。 | Unit/IT/HTTP | B2DispatchIT#Worker调用处理器前已提交且无事务连接绑定;LocalCasesIT#真实HTTP在计算暂停时已返回接收且不可搜索 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-02 | docs/测试工作/02_测试用例与验收方案.md:833；04#TASK-02 | 四态、计算失败可查且不拖死后续任务；四态能在受控测试中观察，失败有安全错误信息。；失败没有就绪向量与成功完成时间，不参与检索。；单次异常不导致调度器静默永久停止；终态不被轮询修改。 | Unit/IT/HTTP | LocalCasesIT#受控任务失败可查且后续任务继续成功 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-03 | docs/测试工作/02_测试用例与验收方案.md:858；04#TASK-03 | 接收两表原子性：插入Document后任务写入失败；接口不返回接收成功，两表均无该任务数据。；不存在孤立文档或孤立任务。；失败不会被调度器当作已接受任务处理。 | Unit/IT/HTTP | B2AdmissionIT#两表实际写入后异常使整笔接收回滚 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-04 | docs/测试工作/02_测试用例与验收方案.md:882；04#TASK-04 | 持久接收后未派发，扫描能补派；不需要客户端重发，持久任务最终被执行。；before enqueue故障不让记录永久悬挂。 | Unit/IT/HTTP | B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-05 | docs/测试工作/02_测试用例与验收方案.md:906；04#TASK-05 | 执行队列饱和与拒绝后的在途集合清理；内存队列不超容量，第三个仍持久QUEUED，不假FAILED或丢失。；没有在HTTP线程执行计算。；拒绝留下的在途标记被清理，所有已接收任务最后有终态。 | Unit/IT/HTTP | B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-06 | docs/测试工作/02_测试用例与验收方案.md:931；04#TASK-06 | 持久积压与总文档容量分别限制；积压满429且两表不留新记录。；任务完成释放积压额度，但不自动释放总文档额度。；总文档上限的错误码按明确契约，不静默丢弃旧文档。 | Unit/IT/HTTP | B2AdmissionIT#文档总量包含已失败已失效数据而不只计算待处理任务;B2AdmissionIT#待处理容量包含RUNNING而失效不释放名额 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-07 | docs/测试工作/02_测试用例与验收方案.md:956；04#TASK-07 | 重复派发不造成重复执行，在途清理可继续工作；本次无故障执行中只有一个成功领取与一次计算。；完成后状态正确，在途集合无永久泄漏。；重复项退出不影响其他任务。 | Unit/IT/HTTP | B2DispatchIT#两个独立领取事务仅一个成功且令牌和尝试次数吻合;B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-08 | docs/测试工作/02_测试用例与验收方案.md:981；04#TASK-08 | 终态两表写入中间故障不能半成功；成功事务回滚：原RUNNING和未写向量保持，不可见半成功。；完整Worker可以随后单独落FAILED，但不能出现SUCCEEDED无向量或向量已提交而任务未成功。 | Unit/IT/HTTP | B3TerminalIT#成功两表写入任一步失败都不能半提交 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-09 | docs/测试工作/02_测试用例与验收方案.md:1005；04#TASK-09 | 失败回写带状态与token条件；合法当前尝试失败有原因；已成功终态不被覆盖为FAILED。；旧尝试不能改当前状态；影响0行必须被正确处理。 | Unit/IT/HTTP | B3TerminalIT#旧令牌成功失败均拒写且本次终态重放不覆盖首次结果 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| TASK-10 | docs/测试工作/02_测试用例与验收方案.md:1029；04#TASK-10 | 调度/终态数据库异常必须显式处理；单次扫描异常不静默终止定时任务；恢复后继续或显式失败待恢复。；无法确认终态时不报成功，明确降级并停止新接收。；无无限忙重试；恢复路径明确，不隐藏RUNNING。 | Unit/IT/HTTP | B2DispatchIT#单次扫描数据库错误不阻止下一次真实扫描;B3TerminalIT#明确回滚的暂时错误最多三次且耗尽不伪造FAILED;B3LifecycleIT#恢复SQL失败绝不READY并保留RUNNING现场 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-01 | docs/测试工作/02_测试用例与验收方案.md:1056；04#CON-01 | 两个独立事务竞争同一QUEUED领取权；只能一个当前尝试成功领取；另一个0行或按明确数据库冲突策略退出。；只有已确认提交且持有token的Worker可以执行。；最终只有一个有效终态。 | Unit/IT/HTTP | B2DispatchIT#两个独立领取事务仅一个成功且令牌和尝试次数吻合;B2DispatchIT#领取物理提交后抛错经本次令牌确认只执行一次 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-02 | docs/测试工作/02_测试用例与验收方案.md:1081；04#CON-02 | 旧token迟到结果与重复成功提交；旧token不能提交向量；重复终态不修改首次完成时间或成功向量。；成功后迟到失败不覆盖SUCCEEDED。 | Unit/IT/HTTP | B3TerminalIT#旧令牌成功失败均拒写且本次终态重放不覆盖首次结果 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-03 | docs/测试工作/02_测试用例与验收方案.md:1104；04#CON-03 | 计算中失效，完成不复活；SUCCEEDED与invalidated=true同时成立。；首次失效时间保留；新检索不返回该文档。；Worker未覆盖失效或命中字段。 | Unit/IT/HTTP | B3TerminalIT#计算暂停期间失效完成后成功回写不复活或覆盖计数;B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-04 | docs/测试工作/02_测试用例与验收方案.md:1129；04#CON-04 | 在途检索与失效的重叠语义；A允许返回其此前读取的文档并计数，不强求剔除。；C必须排除该文档。；计数回写不恢复失效标记，前端旧结果应标历史。 | Unit/IT/HTTP | B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-05 | docs/测试工作/02_测试用例与验收方案.md:1154；04#CON-05 | 失效已经完成后开始的新检索必须排除；所有这些新检索都排除失效文档。；不能用“最终一致”解释已完成失效后的长期可见。 | Unit/IT/HTTP | B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-06 | docs/测试工作/02_测试用例与验收方案.md:1177；04#CON-06 | 多次并发命中同文档无丢失更新；无故障条件下最终hitCount=h0+S。；限流拒绝不计数；不能假设100个发出请求都被接受。；若任何提交未知则该轮不能以HTTP成功数作精确oracle。 | Unit/IT/HTTP | B4CountAndResourceIT#八路实际搜索多轮并发只对最终热点原子累加 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-07 | docs/测试工作/02_测试用例与验收方案.md:1202；04#CON-07 | 多个结果的计数锁顺序与事务完整性；数据库更新按统一ID顺序；展示仍保留相关性顺序。；成功事务的两篇各+1；冲突失败不能留下部分增量。；无“固定锁顺序绝不死锁”的过度结论。 | Unit/IT/HTTP | B4CountAndResourceIT#八路实际搜索多轮并发只对最终热点原子累加;SearchHitCountServiceTest#仅最终ID按ASCII顺序自增且不覆盖失效或向量字段 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-08 | docs/测试工作/02_测试用例与验收方案.md:1227；04#CON-08 | 计数第二条写失败，整次统计回滚；两篇delta均为0，不是一篇+1另一篇0。；本次搜索不返回成功结果；错误符合契约。；已确认回滚场景与提交未知场景分开。 | Unit/IT/HTTP | B4CountAndResourceIT#最终命中多行事务任何失败整体回滚 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-09 | docs/测试工作/02_测试用例与验收方案.md:1251；04#CON-09 | 接收锁必须覆盖物理提交；不同ID最多一个被接受，另一个429；容量不超1。；同ID最多一条文档/任务，另一请求409，错误响应不泄露数据库细节。；若外层事务使锁提前释放，测试应失败。 | Unit/IT/HTTP | B2AdmissionIT#第二线程真实阻塞于接收锁且事务开启晚于首笔物理提交;B2AdmissionIT#调用者外层事务被明确拒绝且不会借用其提交 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| CON-10 | docs/测试工作/02_测试用例与验收方案.md:1276；04#CON-10 | CPU计算不持有长事务，检索门禁可释放；纯计算不长期占用任务数据库事务或连接；其他短操作可完成。；搜索异常、返回和取消路径释放自身持有的许可，不永久繁忙。；不要求不同业务请求共享的CPU被完全隔离。 | Unit/IT/HTTP | B4CountAndResourceIT#CPU暂停时无事务连接关闭后迟到搜索零SQL且不计数;B2DispatchIT#Worker调用处理器前已提交且无事务连接绑定 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-01 | docs/测试工作/02_测试用例与验收方案.md:1302；04#REC-01 | 正常停止重启，全部业务数据保留；文档原文/渠道、向量、终态、失效及计数均保留。；自动DDL不清表；这是正常重启证明，不叫断电测试。 | Unit/IT/HTTP | BackupRestoreIT#真实B1检索失效正常关闭冷复制校验后独立目录恢复保持全部状态;C0BootstrapIT#realHttpHealthDefaultsAndCompleteApplicationRestartPreserveBothTables | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-02 | docs/测试工作/02_测试用例与验收方案.md:1326；04#REC-02 | 启动恢复四态与实际入口门禁；恢复前不接收新工作，不只是健康页显示未就绪。；QUEUED继续，遗留RUNNING重新排队；成功不重做，普通FAILED不无限自动重试。；实际启动顺序符合单实例前提。 | Unit/IT/HTTP | B3LifecycleIT#恢复真实提交前拒接不派发且恢复后真实B1完成保持失效计数;LocalCasesIT#四态文件库重启只恢复未完成任务 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-03 | docs/测试工作/02_测试用例与验收方案.md:1350；04#REC-03 | 真实进程崩溃：接收提交后尚未入队；已持久接收记录存在并被扫描执行，不要求客户端再次提交。；最终向量/任务一致；不把此实验等同于断电可靠性。 | Unit/IT/HTTP | B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-04 | docs/测试工作/02_测试用例与验收方案.md:1374；04#REC-04 | 真实进程崩溃：领取后计算中；遗留RUNNING不永久悬挂，恢复后可到达终态。；允许计算重复，不承诺恰好一次；最终只存在一个有效成功结果。 | Unit/IT/HTTP | B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-05 | docs/测试工作/02_测试用例与验收方案.md:1397；04#REC-05 | 真实进程崩溃：终态提交前与提交后；A不出现半提交，恢复后可重新完成。；B保留成功和向量，不重复完成或改首次完成时间。 | Unit/IT/HTTP | B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-06 | docs/测试工作/02_测试用例与验收方案.md:1421；04#REC-06 | 提交结果未知：先提交再抛错的三条路径；任务所有权和终态通过读取确认，不假定异常=回滚。；计数只增加已提交的一次，不盲目补偿或重试。；若数据库仍不可访问，进入显式降级与恢复流程。 | Unit/IT/HTTP | B2DispatchIT#领取物理提交后抛错经本次令牌确认只执行一次;B3TerminalIT#真提交后抛错必须核对完整终态向量且不能反写FAILED;B4CountAndResourceIT#提交未知不读取累计数不重放且实际增量与真实提交一致 | PASS；本轮baseline XML＋新增LocalCasesIT实际方法结果，见执行来源核证 |
| REC-07 | docs/测试工作/02_测试用例与验收方案.md:1446；04#REC-07 | 算法版本不兼容不混算；显式提示版本不兼容，不用过滤旧数据后返回空库伪装成功。；迁移未实现就标待迁移，不能宣称自动重算。 | Unit/IT/HTTP | LocalCasesIT#旧版本成功文档真实HTTP过滤诊断不混算 | FAIL；原预期与冻结B4冲突，原断言/HTTP实证已保存，待用户裁决 |
| REC-08 | docs/测试工作/02_测试用例与验收方案.md:1469；04#REC-08 | 存储和初始化失败，以及损坏向量诊断；初始化失败不继续开放正常服务；不偷偷换一个空库目录。；损坏向量不静默跳过并伪称完整Top-K，不返回非有限分数。；错误可诊断且对外脱敏。 | Unit/IT/HTTP | C0BootstrapIT#invalidProductionConfigurationPreventsStartup;B4SearchIT#坏候选和超容量失败不能计数且许可释放;LocalCasesIT#无效存储路径启动失败且不偷偷换空库 ;LocalCasesIT#只读临时目录真实拒绝启动且恢复权限后原路径可用;LocalCasesIT#测试资源非法DDL真实初始化失败且恢复默认DDL后可用 | PASS；本轮坏路径/坏向量＋只读目录/非法DDL真实拒启并同路径恢复，见rec08-execution |
| UI-01 | docs/测试工作/02_测试用例与验收方案.md:1495；04#UI-01 | 展示真实状态、排序与分数，不制造AI指标；score原样合理格式化，可为负，不转成准确率/置信度百分比。；保持服务端顺序，不能基于显示小数二次排序。；不伪造进度百分比、版本、耗时或全库统计。 | Browser | Chrome U01/U07/U08/U09/U18；主控补核本地UI-01范围 | PASS；复用用户已认可本地Chrome原件；UI-03的F05保持NOT_RUN/用户批准例外 |
| UI-02 | docs/测试工作/02_测试用例与验收方案.md:1520；04#UI-02 | 提交表单边界、缺省渠道和重复ID反馈；空文本不被required阻止；不裁剪或规范化原文。；渠道空表单通过省略字段表达，而非发送显式空白。；409不自动换ID再提交，失败不假加入成功列表；错误保留输入。 | Browser | Chrome U02/U03/U04/U05/U06；主控补核400/429输入保留 | PASS；原409/空文/渠道＋FR-B02受控400恢复＋独立125.930秒受控429无重发/输入保留 |
| UI-03 | docs/测试工作/02_测试用例与验收方案.md:1545；04#UI-03 | 任务轮询停止、网络错误不伪造FAILED；终态停止、超时暂停并可手动刷新，隐藏时暂停。；网络错误显示暂不可获取及上次状态，不把任务改FAILED。；默认最多3个自动轮询；本会话记录不是全局任务。 | Browser | Chrome U03/U11/U12/F02/F03/F04；FAILED终态为用户批准F05例外，不写PASS | PASS；复用用户已认可本地Chrome原件；UI-03的F05保持NOT_RUN/用户批准例外 |
| UI-04 | docs/测试工作/02_测试用例与验收方案.md:1570；04#UI-04 | 失效确认、失败反馈及历史搜索结果；仅后端成功才显示失效。；旧结果标注历史，不自动重新搜索增加计数。；任务完成且失效可以同时显示。 | Browser | Chrome U09/U10/U16/F08/F10 | PASS；复用用户已认可本地Chrome原件；UI-03的F05保持NOT_RUN/用户批准例外 |
| UI-05 | docs/测试工作/02_测试用例与验收方案.md:1595；04#UI-05 | 搜索不自动重试，旧响应不覆盖新结果；没有无意的重复搜索；失败提示可能已完成并计数。；过期结果不覆盖最新请求结果；修改输入后的旧结果明确标记。；浏览器取消不被解释为服务器已回滚。 | Browser | Chrome U13/F06/F07/F09 | PASS；复用用户已认可本地Chrome原件；UI-03的F05保持NOT_RUN/用户批准例外 |
| UI-06 | docs/测试工作/02_测试用例与验收方案.md:1620；04#UI-06 | 原文与错误文本不执行HTML/脚本；没有脚本执行和意外HTML节点注入；采用textContent或等价安全方式。；不通过拒绝全部合法HTML文本来假装修复展示层问题。 | Browser | Chrome U14；主控补核原夹具D-013和错误文本 | PASS；原Chrome＋本次FR-B01/B02/B03独立核证，见final-release-browser-additions |
| UI-07 | docs/测试工作/02_测试用例与验收方案.md:1643；04#UI-07 | 错误可操作、键盘、小屏和时间展示；核心操作可达，无内容遮挡；状态有文字而非仅颜色。；未完成时间显示—，不捏造时间；显示明确时区。；路径参数按契约编码，允许字符范围不由前端自创。 | Browser | Chrome U17；主控核对UTC+8/空时间/label证据 | PASS；原Chrome＋本次FR-B01/B02/B03独立核证，见final-release-browser-additions |
| UI-08 | docs/测试工作/02_测试用例与验收方案.md:1668；04#UI-08 | 服务器上的真实页面端到端闭环；三个功能区使用同源真实API，无开发mock或硬编码结果。；完成前不可搜，完成后可检索，失效后新搜索排除；重启保留。；不以本机页面截图代替服务器验收。 | Browser | 主控正式HTTPS Chrome闭环及服务重启后复查 | NOT_RUN；待本轮真实执行/Owner回填 |
| OPS-01 | docs/测试工作/02_测试用例与验收方案.md:1695；04#OPS-01 | 干净构建与测试实际执行；干净构建成功，DDL幂等且不清数据。；测试报告明确已执行/失败/跳过；0测试不能当全通过。；编译目标与运行Java一致，JAR可独立启动。 | Server/审查 | 主控Java17 clean verify+Node全量；独立目录JAR两次启动 | PASS；LinuxCI39856764435实际271＋原d016包字节/生产树一致＋真实JAR隔离冷备恢复；见final-release-evidence-review |
| OPS-02 | docs/测试工作/02_测试用例与验收方案.md:1720；04#OPS-02 | 服务器数据目录、进程与正常重启；不会因切换release或工作目录悄悄打开新空库。；数据保留且权限正确，不同时运行两个应用挂同一嵌入式文件库。；停止时先停接收/调度再等待任务和关闭数据源；不无差别中断H2写线程。 | Server/审查 | 服务器Owner正式单实例/目录/权限/正常重启现场证据 | NOT_RUN；待本轮真实执行/Owner回填 |
| OPS-03 | docs/测试工作/02_测试用例与验收方案.md:1745；04#OPS-03 | 备份恢复实际演练，不只生成文件；备份可恢复，各关键字段与向量校验一致。；不是对运行中活动文件直接cp；不覆盖唯一运行库做试验。；不能把“文件存在”当作恢复已通过。 | Server/审查 | 服务器Owner真实冷备与独立非覆盖恢复 | NOT_RUN；待本轮真实执行/Owner回填 |
| OPS-04 | docs/测试工作/02_测试用例与验收方案.md:1770；04#OPS-04 | 公网访问保护覆盖页面和API；未授权不能调用管理和搜索API；页面保护不是唯一防线。；不暴露H2控制台/数据库端口/敏感管理端点或凭据。；没有把Basic Auth本身当作完整CSRF防护；不允许任意源CORS。；使用隧道方案时明确不是公网HTTPS已上线，不夸大验收。 | Server/审查 | 服务器Owner认证/Origin/TLS/回环端口真实现场证据 | NOT_RUN；待本轮真实执行/Owner回填 |
| OPS-05 | docs/测试工作/02_测试用例与验收方案.md:1796；04#OPS-05 | Git、AI记录与交付材料的真实性和脱敏；没有伪造commit时间线、测试经历或性能数据。；公开材料已脱敏；凭据若曾泄漏需要轮换，不能只删当前文件。；交付含源码/DDL/说明和可核对历史。 | Server/审查 | 主控Git历史/敏感扫描/AI决策与交付审查 | PASS；历史有限格式扫描/禁文件＋主控README现状同步后独立复核，见evidence-review追加 |
| OPS-06 | docs/测试工作/02_测试用例与验收方案.md:1821；04#OPS-06 | 发布与回滚对应同一可追溯版本；服务器实际版本能对上源码与产物。；没有双实例同库、误删数据或将程序回滚当数据回滚。；不兼容时明确阻止盲回滚并保留恢复步骤。 | Server/审查 | 服务器Owner D1—D9版本摘要与无旧版回退TLS入口 | NOT_RUN；待本轮真实执行/Owner回填 |
| PERF-01 | docs/测试工作/02_测试用例与验收方案.md:1848；04#PERF-01 | 纯入库与CPU模拟负载测量；输出原始数据和观察，不预填QPS或p95门槛。；资源限制有效，已接收任务最终有可解释状态；超额显式拒绝。；不以提交接口快证明后台吞吐高。 | 实验 | 主控独立隔离库有界入库实验，CPU/RSS/GC/原始时延 | PASS；主1300三阶段＋独立100文档队列采样，峰32不拼首轮；资源全周期分开；见final-release-evidence-review |
| PERF-02 | docs/测试工作/02_测试用例与验收方案.md:1873；04#PERF-02 | 纯检索规模、并发与混合负载；得到真实瓶颈证据；不能宣称不同线程池已经CPU隔离。；没有无界内存增长或隐式候选截断；拒绝不计入成功延迟。；p50/p95等记录样本量和定义，结果不是提前保证。 | 实验 | 主控隔离纯搜索/混合有界负载，N/K/并发分层 | PASS；分机HTTP九N/K及混合＋池4阶段27样本＋真实资源，非HTTP分阶段trace；见final-release-evidence-review |
| PERF-03 | docs/测试工作/02_测试用例与验收方案.md:1898；04#PERF-03 | 热点计数与资源耗尽后的恢复；报告热点行是否成为瓶颈，不预先认定。；未确认提交不能按HTTP成功数强行归账。；恢复后没有许可/在途集合泄漏和无解释任务悬挂。 | 实验 | 主控隔离热点/分散计数及受控故障恢复实验 | PASS；HTTP所有delta独立核对＋锁超时明确回滚/同搜索恢复＋资源原件；见final-release-evidence-review |
| PERF-04 | docs/测试工作/02_测试用例与验收方案.md:1923；04#PERF-04 | 相关性实验：与功能正确性验收分开；同义改写或否定句不是无语义模型的必过高低分断言。；指标仅在定义和标签完整时报告，不能把cosine直接叫准确率。；算法数值未在实际Java实现跑出前保持未测。 | 实验 | LocalCasesIT#八组语言关系记录真实Java分数不预设语义高低；无人工相关性标签不报准确率 | PASS；按原允许无标签观察模式8OBS真实Java分数；不报准确率/保留集指标；见final-release-evidence-review |

## 本轮新增独立执行与原计划

LocalCasesIT计划补：16篇原夹具真实HTTP逐篇提交/原文/零文/渠道/搜索计数/失效/增量稳定，4工程等价对按位及原文保存，14请求夹具，5个数学向量H2位往返、6余弦/5TopK答案、原始过滤场景，真实HTTP计算暂停异步边界、受控失败后正常任务继续、四态文件库重启、无效存储目录启动诊断、REC-07真实HTTP版本过滤诊断、8语言观察对原始分数（不报准确率）。不添加生产调试端点。原始向量JSON只抽取已审合成数据到明确test资源，不提交运行库/凭据。

命令拟为Java17 `./mvnw -B -ntp -DskipTests package`后`./mvnw -B -ntp -Dit.test=LocalCasesIT failsafe:integration-test failsafe:verify`；具体以实际执行记录为准。Server/Browser/PERF由主控独立现场补证，不能依据上表标题标PASS。


## 本轮独立自动化结果与待合入边界

新增`LocalCasesIT`12个JUnit执行项全部实际通过（0失败/错误/跳过），具体逐方法记录、命令、原始报告与摘要见`final-release-local-cases-results.md`及同前缀evidence目录。12项是新增工程执行数，不能把同一方法映射的多条主用例或其16/14/4/6/5子样本另行相加；也不与主控将重跑的已有253项混算。

矩阵仍保留所有70条，既有方法由主控本轮全量执行后按各预期回填，不用旧CI自动PASS。新增结果能补足对应条目的子范围，不代表该条联合范围已全验。REC-07原预期为FAIL/契约冲突，等待主控交用户明确决定，观察测试绿不解除。Browser/Server/性能仍由Owner真实执行；性能观察数据不等于达到SLA。

原始资料来源清单：README、01开发问题、02测试方案、04结构化用例、05空白执行模板、SHA256SUMS、fixtures/documents、request_cases、text_relations、vector_oracles。它们保留在主树，未编辑/暂存/复制整包进公开Git。仅将已审合成夹具的必要数据值和源摘要整理到`src/test/resources/local-cases/`；没有真实凭据、原题PDF、运行库或日志。

### 尚需主控确认的细分覆盖

- UI-02原用例还列400/429页面错误输入保留；既有Chrome409与边界证据可复用，但不得因节点单测自动声称真实429 UI已验。
- UI-06原本同时要求恶意错误文案；既有U14覆盖真实原文/摘要XSS，错误文案若未真实含标签仍需补测。
- REC-08先有错误路径拒启、既有坏向量与非法配置；随后已独立补测只读目录与非法DDL真实拒启/恢复，所有环境故障都只在临时库，不扩大生产权限。
- PERF-04本轮8语言对真实Java分数已落盘；资料没有人工相关性标签，明确不报准确率/Recall/NDCG，带标签保留集评估仍未执行。
- OPS-06首次发布无已验旧版时按已批准D9回TLS检查入口；不能宣称实际旧版程序回滚已验，也不能为演练回退覆盖数据。


## 本轮实际报告独立回填（不采用旧CI自动结论）

已读取主控`/private/tmp/12times-final-release-evidence/baseline-summary.json`与baseline-reports所有JUnit XML：本轮da5867a源码104 unit/110 IT，零失败/错误/跳过；主控baseline-node为39/39，随后`integrated-node-43.log`为43/43、零失败/跳过，含新增FR-N01—04；不是本轮重复统计39＋43。QA新增12IT的最终实际报告单独保留。矩阵所有标PASS的工程映射方法逐个在本轮XML或新增12IT执行receipt查到且无失败/跳过，不用2026-10-22旧CI代替本轮执行。

补充REC-08两项后，当前原70条追踪为55 PASS、1 FAIL（REC-07契约冲突待用户裁决）、14 NOT_RUN。工程执行数为原253＋新增14＋新增Node4＝271个唯一自动化项；性能手动探针不并入JUnit/Node数量，55个主用例也不能再加到271。原70的多个编号可共同引用同一实际方法而不重复计工程数。

UI-01/03/04/05的PASS仅复用用户已认可的28项真实Chrome及受控分层证据，UI-03的真实FAILED继续NOT_RUN/用户批准例外；FR-N04只证明恶意错误字符串解析，不能替代UI-06错误DOM安全。UI-02/06/07等待前端FR-B01—03的真实差异步骤；UI-08/服务器OPS及分机HTTP性能未到原始证据继续NOT_RUN。REC-08原全部故障输入现已结合本轮只读目录/非法DDL新增实测关闭，不以坏路径或非法配置冒充这些输入。

本次仅只读核证既有实际报告与更新矩阵，不重跑测试。待合入后完整verify/Linux CI仍是主控门禁；运行文档SHA与批准生产JAR SHA分开。


### REC-08补测收尾

新增两项在原12项基础上实际执行：指定两方法2/2通过，再完整LocalCasesIT为14 tests/0失败/0错误/0跳过。只读目录是本方法新建@TempDir子目录，先核真实路径归属及非symlink，只给此子目录设0500，确认本用户实际不可写；真实H2 AccessDenied导致启动拒绝，无新文件/备用空目录；finally恢复原权限，同路径默认配置重新启动健康200。非法DDL仅test classpath资源注入，真实初始化失败且数据库位于声明目录，无新空库目录；恢复默认schema同路径启动健康200。未打开正常业务的失败启动不存在公开错误响应，内部原故障日志与实际报告完整保留，没有真实凭据或原始业务数据。

本次完整14项收尾时间2026-10-23 05:01:16+08，7.260秒；Maven总8.308秒。完整14及REC08两方法receipt在同前缀evidence目录，先前12项receipt不覆盖。55 PASS/1契约FAIL/14 NOT_RUN为本轮报告核证结果，尚不是最终交付全部通过。


## 新增Chrome差异核证快照

主控本地真实Chrome FR-B01—03原始cases/audit、Network/Response、DOM与截图已独立读取，UI-06/07新增缺口关闭，详情见`final-release-browser-additions.md`。本快照为57 PASS、1契约FAIL（REC-07）、12 NOT_RUN；UI-02还缺原明确429页面输入保留证据，UI-08公网与服务器/分机HTTP性能不提前关闭。新增Chrome步骤不增加271个唯一自动化项，不重复累计原28项。


### UI-02单次429最后补齐

独立新目录7c556_cc的真实Chrome窗口用发包前FAULT_INJECTION返回429，125.930秒内前端意图1、Network POST0，输入原样/按钮恢复/无成功任务/不重发。QA对初末JSON、原始事件、DOM/截图核证，UI-02关闭；这不等同于真实后端容量满。最新矩阵58 PASS、REC-07契约FAIL 1、11 NOT_RUN，UI-08及未知Server/PERF层不提前关闭；详细来源/剩余审查清单见final-release-browser-additions.md。


## 部署前最终独立实证核证快照

新读LinuxCI39856764435的104unit/124IT/43Node、原批准JAR字节/生产树/真实冷备恢复，服务器ingest/search/queue资源与manifest、分机原始请求账本和只读队列agent源码。判据与原02精确章节及分层范围见`final-release-evidence-review.md`。PERF01—04、OPS01关闭，队列第三轮采样峰32不嫁接首轮，资源全生命周期不当客户端纯窗口，PERF04只用原允许的无标签观察模式。当前63 PASS、REC07契约FAIL 1、6 NOT_RUN：UI08、OPS02/03/04/05/06。OPS05扫描子项已证，但README当前仍有F05/授权/性能过时陈述待主控同步，根README QA不改。正式服务器、公网Chrome和REC07用户裁决均未被本核证绕过。


### OPS-05当前陈述同步与最新Linux CI追加

上一63PASS快照保留。QA只读复核主控当前README，F05已批准例外、D1—D9已授权、有限性能已验不报SLA、正式业务未启动与REC07待裁决均一致，OPS05解除文档缺口。最新LinuxCI39859349901/测试230c347完整SHA的XML、Node、批准原包manifest及真实JAR冷备恢复已独立核证：104unit/124IT/43Node＝271，零失败/错误/跳过，队列观察器javac17的6警告如实保留，不重复加计。当前64 PASS、REC07契约FAIL 1、5 NOT_RUN（UI08、OPS02/03/04/06）；正式目标全保留，未操作Chrome/服务器/生产或重跑旧测试。详见final-release-evidence-review.md末节。
