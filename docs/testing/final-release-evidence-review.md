# FINAL-RELEASE最终独立实证核证（部署前门禁）

> **历史阶段说明**：本文件记录部署前独立核证，包含当时的 271 项测试和 REC-07 待裁决状态。正文、原始 FAIL、测试数量和时间线原样保留，不代表项目当前仍未交付。最终结论请查阅[《最终交付聚合结果》](最终交付聚合结果.md)。
> 当前采用用户批准的 REC-07-R1 与首发验收范围，70 条主用例通过；原 REC-07 FAIL 不改写，F05 仍为 `NOT_RUN / APPROVED_EXCEPTION`。原材料及受限证据引用仅用于溯源，不是公开下载链接。

## 最新CI39862014960追加核证

测试源码`5897cfd4764b4a5b32d973f6722249d26a04b800`，QA再次独立读取19份Surefire、15份Failsafe和Node原日志：104 unit＋124 IT＋43 Node＝271，零失败/错误/跳过。Java17实际同时编译ColdSnapshot与QueueReadOnlyAgent，六条警告均来自后者Unsafe/shouldBeInitialized；没有将诊断编译计为新增JUnit项。

CI实际核对生产树无变更、从原39838054946下载d016/64bc原JAR并执行六参验证。原JAR在回环43433启动、SIGTERM143后冷复制，独立34557恢复启动、再次143；API任务/向量就绪/首次时间/计数/失效及备份原件一致。systemctl仍为runner内部inactive shim；ColdSnapshot在本CI仅编译，不把这些结果冒充正式systemd或D8完整向量字节验收。原始报告/日志保留本地，公开仅含[核证元数据](evidence/final-release-predeploy-d016d6e/CI39862014960核证.json)及摘要SHA，不披露被自动审批拦截的原始载荷。

70主编号仍64 PASS、REC-07契约冲突1 FAIL、UI-08及OPS-02/03/04/06共5 NOT_RUN。正式GO等待用户裁决，F05继续NOT_RUN / APPROVED_EXCEPTION。以下各次历史核证原样保留，不把新CI或公开摘要当作正式部署完成。

QA只读核证，未操作原件、Chrome、应用或服务器，未重跑已有测试。原用例来自主树用户材料`docs/测试工作/02_测试用例与验收方案.md`与同编号04 JSON，原件保留。测试源码版本与部署包分别记录：Linux CI测试源码`fb47197eec15993c7e864c931c262b716edbcd2c`；用户批准生产包仍为`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，JAR SHA256为`64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`。

## 判断口径

实验PASS表示原用例要求的实际输入、分类、观察、故障与恢复证据已覆盖，不表示达到预先不存在的吞吐/延迟门槛。允许明确分层组合覆盖不同观察目标，前提是各运行版本、参数、输入、时间与观测影响分别保留，不能拼造同轮数据。未解除实际FAIL仍阻塞“全部交付通过”；F05只有用户已批准有限例外，继续NOT_RUN。

## PERF-01：按分层组合可PASS，不需要新例外

原02文件1848行起的PERF-01要求短/中/长、单请求/少量并发/超过接收速率三阶段；步骤包括记录接收/排队/处理时间、成功拒绝失败未完成与CPU/RSS/GC，停止新提交并收尾。其“应保留证据”为配置、原始样本、任务完成数、队列峰值、资源曲线；没有规定所有指标必须由同一次运行同步采集、每阶段必须取得队列峰或队列必须达到64。

本次按原预期组合的充分证据：

1. **主压力轮**：`perf-ingest/requests.jsonl`独立重算1300次POST，1088个202、212个429；1088个terminal响应全部SUCCEEDED，1088条排队/处理时序保留，unknown0/writeRetries0。短单10、中8并发40、长64并发1250三阶段及延迟原始样本齐全。提交后持久pending观察峰996；当轮未采集Java执行队列getter，exactJavaQueuePeak=null，原件不回写。
2. **主压力资源**：`server-resources/ingest`的/proc、GC与元数据摘要校验一致。885条样本跨度884.542秒，CPU增量610.45秒，RSS最大1468198912字节，GC完成暂停621条、合计9165.903ms。它覆盖启动/空闲/工作/关闭全生命周期，不是客户端414.669秒的纯负载窗口，不能用该客户端时长计算这些全周期CPU均值。Java正常SIGTERM/Hikari关闭成立；该临时unit未配置SuccessExitStatus=143而标failed的事实保留，不虚构systemd成功分类。
3. **独立队列补观测轮**：同固定JAR、原参数，另100篇20k长文全部成功，独立请求原件位于`queue-probe/`。`server-resources/queue.tar.gz`SHA256为`6ec7b6f4be90251cdd5d06fb4a4daaedd60a3a05e61e5f0ce104f9dda278573d`，与给定远程摘要一致；QA只读tar内manifest和JSONL，2398条中2251条OBSERVED，queuedCount采样峰32、inFlightCount采样峰33，末次两者0，READ_UNAVAILABLE0。其/proc/GC为该第三轮自己的记录。
4. **观察影响**：agent源码摘要`1ce54197346b05848aabdfee0f16a8306096567221c3a4bccbca354b60426a59`匹配元数据；QA读源确认不注册字节码转换、不建Bean/调用getBean、不连接数据库，仅在已有类初始化后取现存singleton及public getter，50ms读取/flush有观测开销。queuedCount/inFlight是相邻非原子读取；32/33是采样峰，不是连续精确最大值，也不是队列容量64。该独立100文档轮不能冒充主1300轮峰值或吞吐。

原用例要求的是有界负载实验及真实观察，以上没有省略三阶段或队列观察，**PERF-01 PASS**。不需要自批验收例外；也不宣称额外的“首轮逐阶段队列相关性”“连续极值”“饱和至64”已验。若未来要这些更强结论，应新做对应同轮采集，不能从本组合推导。

## PERF-02/03：分机HTTP、组件计时与故障层分别PASS

`perf-search/environment.json`固定Mac客户端、独立Linux目标、同d016包。QA读取保存的实际driver与请求：候选N100/N1000由互异渠道分组，N5000为全库；九个N/K纯搜索组各12条成功样本；同N1000/K10后台只写其他渠道的混合组30条。预热、拒绝与成功延迟分开，5000篇准备全部真实接收/完成，超总容量另返回429。没有将HTTP墙钟当查询向量化或纯CPU耗时。

组件探针在冻结池4下的27条正式样本，分别使用实际B1、真实H2候选读取、逐候选decode/Cosine物化、TopK和真实计数commit；报告已声明同组件微基准不是HTTP trace/生产内存模式。Linux CI显式main也实际执行，非JUnit计数。`server-resources/search`766条/proc样本跨度765.472秒，CPU增量168.47秒，RSS最大590733312字节；GC暂停133条、886.032ms。该全生命周期与客户端368.585秒分开，不混算。

PERF-03的HTTP热点128次中4成功/124拒绝，分散64次中7成功/57拒绝，过载后恢复1次200；热点后独立快照及只读回查核对5000文档全部delta，散点+恢复8笔有明确成功响应，unknown0，无重放。组件并发热点/分散各80笔明确提交，SQL含等待与commit独立原样本保留；隔离真实锁超时返回DATABASE_UNAVAILABLE，失败delta0，释放锁后同SearchService恢复delta1，许可可复用/READY、无遗留在途。没有以HTTP未知结果强行归账，也没有宣称固定锁序绝无死锁或其他无限负载保证。

据原1873/1898行起的预期，**PERF-02、PERF-03 PASS**，范围是这次有限N/K/并发与隔离故障；不把组件微基准、分机HTTP或全生命周期资源曲线混称同一层。正式生产运维仍另验。

## PERF-04：仅按原允许的无标签观察模式PASS

原1923行起前置条件明确“尚无标签则只记录分数不算准确率”。实际夹具没有人工相关性标签，本轮LocalCasesIT已用固定实际Java版本处理8个OBS，全部原始分数、同义/否定/碰撞局限和数据源SHA保留。无调参、无擅定语义高低分阈值、无准确率/Recall/NDCG，**PERF-04在无标签观察模式PASS**。这不是带标签保留集效果评估，后者没有执行，不能借本PASS宣传语义能力。

## OPS-01：实际报告和批准原JAR一致性PASS

QA独立汇总`linux-ci-39856764435`全部XML：104 unit＋124 IT，零失败/错误/跳过；原始CI日志Node43/43、零失败/跳过，总271个唯一自动化项。测试源码完整SHA为fb47197…；旧253＋新增14IT＋新增4Node不重复计；性能main不加入JUnit。

CI日志实际执行生产树`git diff --exit-code d016 HEAD -- src/main pom.xml mvnw mvnw.cmd .mvn`且通过；QA本地同范围只读diff也无差异。该CI从39838054946下载**原批准JAR**、独立64bc摘要及六参数验证通过，随后真实原JAR启动和独立冷备恢复：原/恢复端口43687/44573、SIGTERM143，关键状态/向量/时间/计数/失效保留，备份原件未改。元数据仍属于原39838054946，不把新测试构建包替代批准包。CI的systemctl为inactive shim，仅Linux runner隔离复演，不证明正式服务器systemd/恢复/部署。**OPS-01 PASS**，正式OPS项不继承通过。

## OPS-05上次快照：扫描子项成立，README当时待主控同步

`git-history-scan.json`记录截至HEAD `e5e38327e1ed8470b226ab1721ea6bd5a791fa5d`全部可达1522 blob/36106820字节，私钥/GitHub token/AWS访问key格式无命中，禁文件路径为空；QA当前只读`git ls-files`亦无mv.db/trace.db/jfr/pem/key/zip。公开授权、真实迭代与原失败/修复证据保留，原始用户材料未原样纳入公开仓库。模式扫描不是识别任意秘密的完备证明，后续新增归档仍须主控提交前扫描。

但QA本次读取主树README：首段已写F05/D1—D9获批，后面概述和FE段仍写F05申请/待批准、正式部署待独立授权，B4概述仍总写负载性能未验。原OPS-05要求README能力描述与已验/未验一致，当前存在可定位过时陈述。根README属主控所有权，QA不改；**OPS-05整项暂NOT_RUN（扫描子项已核证，待文档同步后复核）**，不是凭新增测试扫描自动关闭。

## 上次63 PASS快照与当时剩余门禁

70原编号当前**63 PASS、1 FAIL（REC-07契约冲突待用户裁决）、6 NOT_RUN**：UI-08、OPS-02/03/04/05/06。正式OPS02/03/04/06与公网ChromeUI08未提供已部署实证，不能因隔离Linux实验或本机Chrome通过关闭。REC-07原断言失败保留，不以冻结B4观察测试绿消除；用户尚未裁决，仍为未解除实际冲突。F05为用户已批准例外、NOT_RUN，不伪造FAILED浏览器结果。

本核证只读取本地既有证据、源码与报告、检查摘要/计数/来源，未执行真实服务器/浏览器操作或不必要旧测试。配置、SQL、API、算法、生产JAR均未改；原件不更名/重算覆盖。新文档与矩阵提交前空白检查通过，敏感扫描仅核新增摘要，不输出任何凭据。


## 最新README与Linux CI独立追加核证

上文63 PASS、OPS-05待同步结论是上一轮真实快照，保留不回写。主控随后更新根README，QA仅只读当前主树文件复核：F05明确NOT_RUN / APPROVED_EXCEPTION、用户已批准有限例外；d016候选D1—D9及应用/Nginx enable已授权；有限性能样本实际执行且不报SLA、PERF-04无标签观察不宣称带标签效果；正式业务尚未启动、UI-08/正式服务器重启恢复待现场实测、REC-07待用户裁决。之前定位的授权/例外/性能过时陈述已同步，不再和当前记录冲突。根README由主控修改，QA未改。

结合前述全部可达历史1522 blob/36106820字节有限格式扫描无命中与禁文件为空、当前受跟踪路径无运行库/私钥/ZIP、真实旧失败与修复/测试时间线保留、原题与用户原始材料受保护，原OPS-05的README真实性缺口已解除，**OPS-05 PASS**。该判断是已查范围，模式扫描不保证识别任意秘密，后续新增归档仍应在提交前检查，不能把本结论当所有未来文件的免检许可。

最新[Linux CI 39859349901](https://github.com/Qbsstg/text-vector-search/actions/runs/39859349901)的原件目录为`/private/tmp/12times-final-release-evidence/linux-ci-39859349901/`，测试源码`230c3471b789e994c46346c06d3dd83989273f95`。QA重新独立汇总19份Surefire XML＝104 tests、15份Failsafe XML＝124 tests，全部failures/errors/skipped=0；原始CI日志Node tests43/pass43/fail0/skipped0，唯一自动化计数仍271，和上一CI是复验，不叠加成542。

原始日志确认生产树相同检查与批准包校验实际执行，本地只读`git diff d016 230c347 -- src/main pom.xml mvnw mvnw.cmd .mvn`也无差异。approved-candidate manifest仍属于原39838054946的d016/64bc包，不把测试提交JAR替代它。最新冷备原件实际启动原JAR端口35297、恢复目录JAR端口38879，均SIGTERM143，向量/任务/首次时间/计数/失效保留，备份原件未改；systemctl依然inactive shim，不证明正式服务器部署。手动组件探针另实际执行不计JUnit；队列只读观察器javac17实际产生**6条警告**（含Unsafe/shouldBeInitialized废弃相关），构建成功，不编造零警告。

最新70条矩阵为**64 PASS、1 FAIL（REC-07原预期/冻结契约冲突待用户）、5 NOT_RUN（UI-08、OPS-02、OPS-03、OPS-04、OPS-06）**。这些正式目标一个也未删除或降级，不能用本地Chrome/隔离Linux数据来关闭。F05仍NOT_RUN/用户批准例外；REC-07没有新裁决，原FAIL仍保留，不能宣布全部交付通过。

本次只更新矩阵和本文件，读取README/XML/Node/summary/manifest与只读diff，未重跑271项、不操作Chrome/服务器、不改原件或生产。新增说明空白与有限敏感模式扫描通过，不声称任意秘密皆无。
