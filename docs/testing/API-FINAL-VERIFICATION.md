# API-FINAL-VERIFICATION 隔离原题与API核证

## 最终main集成CI独立复核

主控实读CI39943929209为`completed/success`；QA本次仅读取已下载XML及Node日志核证，没有重新运行测试、访问生产或更换运行包。受测公开main源码为`06d78321c0dc233d3d88795840242264ea796778`，已保留既有文档提交历史；这是最终文档/工程集成SHA，不是生产源码身份。

| 本次集成CI独立实计 | 结果 |
| --- | --- |
| Surefire XML | 20组、105项，failures/errors/skipped均0 |
| Failsafe XML | 16组、126项，failures/errors/skipped均0 |
| Node TAP | tests52/pass52，fail/cancelled/skipped/todo均0 |
| 唯一合计 | 283项；不与此前282或候选283重复相加 |
| CI原日志SHA-256 | `db41c0dfb492f95cc0186777bd209b1640fc58e311480dcad6e1674a82917481` |

QA另实际执行`git diff --exit-code 9883400451c9b2a22d13134cbd698d7478851b3d 06d78321c0dc233d3d88795840242264ea796778 -- src/main pom.xml mvnw mvnw.cmd .mvn`，退出0；CI日志的Linux隔离冷备恢复与单实例保护步骤实际通过。原XML/日志仅本地受限保存，公开只列聚合和hash。

**最后工程集成门禁已核证关闭；QA同意按上述真实分层及既有有限例外作FINAL_ACCEPTED收口。** 当前实际生产仍固定批准`9883400451c9b2a22d13134cbd698d7478851b3d` / `becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece` / 发布CI39927516112；没有用06d783的新打包物替换生产JAR。F05原有限例外、物理手机/真实读屏NOT_RUN、旧缓存历史分层、旧503及旧数据严格断言FAIL、用户新baseline独立PASS和18:00未达事实全部不变，不把历史改写全绿。


## 最终独立QA结论（发布后必需UI已补齐）

**QA技术核证：本轮原70判据的必需UI门禁按分层证据可以关闭；已批准988/becb实际部署、原题/API工程与双平台35分钟/兼容/冷备恢复均已有实际证据。** 最终公开main文档集成CI已由QA按顶部原件核证通过，不用该后续打包物替换固定运行988 JAR。下方早期BLOCKED、待批准、NOT_RUN/RUNNING为当时真实快照，不代表这些已经补齐的当前结果。

- 当前运行源码`9883400451c9b2a22d13134cbd698d7478851b3d`、JAR `becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece`、H2 2.5.252、产物CI39927516112；QA核283项，Mac/Linux完整35分钟及正常重启、旧2.4副本升级兼容、实际部署与旧41API字段保持、独立生产冷备恢复41字段的证据层各自保留。
- UI-02最后缺口已按原判据允许的模拟方式真实Chrome补测：客户端Request阶段400/429夹具，两个原件的expected与result.fields逐字段完全相同，按钮恢复；400纯文本字段错unsafeNodes0并聚焦submit-text，429明确未加入成功且taskButtonHidden=true。完整trace truncated=false，仅两次主动POST对应400/429，均未出网，0新doc/0后台任务/无自动重试。不是服务器实际容量耗尽。
- 必需轮询：首次真实202最后已知QUEUED三auto/第四limit；独立新会话phase5完整17事件证明单在途、首次polling到timeout60300ms、手动触发新GET到timeout59700.834ms（未重置），hidden墙钟继续；phase6隐藏取消旧GET、恢复新GET真200/SUCCEEDED，约35秒无续刷。使用Chrome受控焦点模拟触发真实visibilitychange及客户端GET延迟，不伪造业务状态，不说后端持续排队。
- UI-04失败反馈：唯一PUT在客户端发包前ConnectionReset，保持有效/计数2/空失效时间；9.166秒后真GET200仍一致，无重试。新包3篇真实闭环则两次搜索、P1失效；最终P1=1失效、P2/P3=2有效，均成功/就绪，总44。最终Owner监督18:56:35 READY/重启0/ERROR0、旧41只有授权B/Z/C各+1，其余摘要diff[]。
- 原文/摘要XSS真实入库和错误文字客户端GET400夹具分别核证；错误pure-text unsafeNodes0保留真实SUCCEEDED。旧缓存混用修复及预置复现历史保留，本轮普通缓存/三个条件请求及静态hash另有真实证据。
- 原25/30分钟旧包503与确定性旧红永久FAIL；原旧33完全不变FAIL永久保留。用户新baseline裁决与独立新PASS、五hits各+1解释分别记，不扣回计数、不回填原断言。三次前期不成功的隐藏尝试及2659.87秒工具阻塞/18:00未完成也保留，不用最终补测倒写历史。
- F05自然FAILED浏览器仍`NOT_RUN / APPROVED_EXCEPTION`，不扩例外；物理手机、真实读屏未执行，旧缓存预置本轮不重造。原70联合范围的内部竞态/失败/版本/恢复由本轮真实隔离方法覆盖，历史性能仅REUSED_EVIDENCE，不报告新压力或SLA。不是把所有70条都写ONLINE_PASS。

| 最后UI补证逻辑名 | QA实际计算SHA-256 |
| --- | --- |
| 提交400保留输入 | `814ed4cf4165356510cfb9a34a1dea18a3f942be0146abbc9eacaa01f211eec7` |
| 提交429保留输入 | `6ca08e8dd0a47fbc0ada77930259ab3f35b849eafaffc2c231ce3ba03509a857` |
| 两次主动提交完整trace | `feecd2a06a41e246ec211a26abde6fdce2f1c42c577de519adaf79b9abd946b3` |

原始请求/截图/认证/数据库/详细运维保持私有，公开仅本聚合与摘要。QA没有自行操作生产、浏览器或追加压力；Owner执行/QA只读核证与QA最初实际282重跑分别标明。


## 必需客户端补测收尾过程（历史，UI-02已由顶部补齐）

**已关闭核心轮询、隐藏恢复、错误DOM与失效失败反馈的本轮客户端缺口；UI-02的400/429表单输入保留当前仍待新回执或明确历史复用，不提前给完整FINAL_ACCEPT。** 生产已是批准988/becb、发布/283工程/Mac与Linux35分钟/正常重启/兼容与恢复均已有分层实证；最终main集成CI由主控另行核证，不替换固定运行JAR。

1. `phase5`只读MutationObserver源码仅观察polling文本并console.info，未写DOM、派发visibilitychange或伪造任务状态。真实Chrome焦点模拟触发hidden/visible，单会话同一task事件17条完整（truncated=false/hasMore=false）。首次polling 1792752711833→timeout 1792752772133为60300ms，Chrome后台计时迟300ms；实际手动触发新GET的wallTime 1792752712432.166→timeout为59700.834ms，未重新给60秒。操作后marker1792752712660不是原生click的精确时刻，不据它报精准点击延迟。
2. 完整phase5事件逐条核active task GET，最大同时在途1；首GETConnectionReset后手动新GET，hidden后旧GET ERR_ABORTED；隐藏墙钟继续，到原预算暂停。恢复仍显示timeout，不自动重新启用；之后手动读取200真状态。该序列的最后已知状态为未知，不伪称后端仍QUEUED/RUNNING。
3. `phase6`另一真实页面会话，同一既有P2隐藏时显示隐藏暂停，恢复产生新GET；按真实task URL过滤数组只含2条，旧221取消、新222真200并显示SUCCEEDED/terminal，约35秒后未多发task GET。扩展/favicon请求已排除。分会话验证预算内恢复与预算后不重置，不把两会话拼成同一计时器。
4. 原首次真实202的三任务auto和第四旧task limit、10s读取失败保留QUEUE均保留；第四limit是禁止加入auto，不是禁止单次手动GET。旧失败尝试仍保留：首次实际visible、第二次隐藏前已终态、第三次2659.87秒工具阻塞不算PASS，18:00未完成不改写。
5. 恶意错误文本是仅客户端GET400夹具，img/script标签完整按纯文本显示、unsafeNodes0，保留已知真实SUCCEEDED；截图与result已核。不能称服务器自然发出恶意错误，也不能作为F05业务FAILED。拦截清空后恢复真实读取，focus emulation恢复，helper关闭由主控实操回执记录。
6. UI-04本轮新补：有效P2的唯一PUT在Fetch Request阶段ConnectionReset（未出网），UI保留有效/计数2/失效时间空且提示未确认；9.166秒后唯一真实手动GET200仍有效/计数2。trace只有1失败PUT与1GET，无重试；截图与三JSON均核。这是客户端网络故障，不冒称数据库写入失败。
7. 新包3篇真实Chrome闭环与18:56:35Owner末检相符：P1首搜+1后失效，P2/P3两搜各+2且有效；三任务SUCCEEDED/向量就绪、总44。旧41只允许新包Owner专属HTTP搜索的B/Z/C各+1，其他API摘要diff[]。末检1list+1health，0新写/搜索/重启，已停止生产请求。此后客户端阻断补测不增加文档/计数。

| 最终原件逻辑名 | QA实际计算SHA-256 |
| --- | --- |
| phase5完整检查 | `1841eed7114b3638db219cf550fa941ebb5a2eead1d61c4d231ed70c7224b22c` |
| phase5完整17事件 | `0563144ef17933981b0bfc8d0dc311359e988f8835ca1c340abf8572c5dc57f6` |
| phase6隐藏恢复真终态 | `2900cf5f0e48c6fd9977866f3938ecdc755774456aa7ba27ec194bdc87086982` |
| 客户端错误纯文本结果 | `ac8cfcdaca469076e996141d7ea303809075ea4e2b1dd5fd4cad7c6ed37af296` |
| 失效失败最后真实GET | `523161acd0fea5f9e8fe57d481f3881f4cd4d83b3a1531cc77ed4400dfcfe4ce` |
| 新包Chrome后末次账本 | `16f60466213b6fa5fb1eec68b32fd7ca656232963680dfed436a9f7b7fa3926f` |

真实FAILED仍F05原有限例外；物理手机/读屏和旧缓存预置历史复用边界不变。生产新3篇未被额外测试性重启，不把旧41升级保持或隔离35篇重启说成这3篇实际生产重启后读取。QA只读审证据，没有自行操作浏览器/服务、发送生产请求或追加压力。


## 发布与18:46:41监督快照（历史，已由顶部结果补全）

**生产已按用户精确批准替换为988/becb；发布与隔离修复子门禁PASS，完整UI门禁仍BLOCKED，不能给FINAL_ACCEPT。** 本轮额外3篇预算已用于真实Chrome提交，不再新增。原旧包503 FAIL、旧33严格不变FAIL、用户新baseline独立PASS均永久保留。此前“待批准/未部署”的文字属于下方历史快照，不作为当前生产状态。

- 实际生产源码`9883400451c9b2a22d13134cbd698d7478851b3d`、JAR `becb3b46be10e25822fcfafc4636d7516d29c87b001b3d173d0943fbb0cb7ece`、CI39927516112、内嵌H2 2.5.252；算法/schema不变。QA实际读取Owner四份安全聚合并核hash链，不冒称读取全部私有journal/原始请求。
- Owner正常停旧并确认Hikari结束/无H2持有后，保留原永久锁并作全新非覆盖冷备，冷备来源2764，文件SHA `1a3b6fa4fd56d3b1ed91622e5d570073d490ece9e9c3705d5ba89e3d266f3c5d`；原子切current后新包启动READY。unit/Nginx字节、env与认证材料保持，未自动降级H2。
- 升级立即旧41文档+41任务完整API JSON字段一致；这里是接口字段，不声称本轮在线全量vector原始字节。新2.5包在独立目录/端口18192从旧冷备恢复，IFEXISTS、41文档/41任务全JSON字段同、0写/0搜索、独立持有；正常停止/Hikari关闭，端口与文件持有释放。
- 新包Owner零新增27请求实证：200×10/401×8/403×2/404×6/409×1，正常专属搜索1次使旧B5→6、Z4→5、C5→6；失效排除/负分/GET计数/认证Origin/管理拒绝均按聚合范围。unknown0/writeRetries0/Owner新增0；不能把这一轮与旧109或其他只读混成一次运行。
- Chrome新增P1/P2/P3真实202，18:46监督均SUCCEEDED/vectorReady/有效/计数0，共44篇；后续搜索失效闭环尚待实际回执，不提前通过。18:46:41生产原新进程44692、唯一H2持有、同锁261932、READY/重启0/启动后ERROR0/数据库不可用记录0，旧41按授权搜索账本无差异。只读health不替代新的写入稳定性测试，Mac/Linux完整35分钟结果单独核证。
- 首次三auto/fourth-limit与10秒真实客户端超时保留最后已知QUEUED、不伪FAILED有原件；第四条limit仍允许一次手动GET。首次隐藏实际visible；第二次隐藏时已终态；第三次工具调用从18:00:58阻塞至18:45:13（2659.87秒），虽一次手动点击成功，不能证明60秒预算不重置或非终态隐藏恢复。post-release脱敏网络本段`truncated=true/hasMore=false`，不能据它宣称全程请求数或完整单在途证明。没有伪造payload/status、暂停worker或制造FAILED。
- 18:00目标未按时完成，真实工具阻塞与未补齐项目保留。正式浏览器60秒/手动不重置/非终态隐藏恢复仍NOT_RUN，F05原有限例外不扩展，旧受控错误DOM不充当本轮恶意错误字符串正式服务回执。

| 发布安全原件逻辑名 | QA实际核对SHA-256 |
| --- | --- |
| 新包生产升级总摘要 | `fea1f9cf8d0054d6f680dc0d8becf05acd7a71a5a955a6578cfe10eed350fa2e` |
| 新包零新增27请求 | `f67cef1a74dee337a6e8e65aa4cfd5586a5f7663ed248a7cfc41e7959ec45826` |
| 旧41升级立即API字段 | `f603d38fee6dba0ecb7ebcb3661d715a26db2660252428201142c876ca5f8397` |
| 独立恢复41字段/0写0搜索 | `ef4489ba64ce1607055f2cad917c207a77c073a21a3f053c82fa8adc45a0affb` |
| 工具恢复后18:46只读监督 | `53e79285ff69777deeb0d731f8835374f001dc2631f76be4c8b2271e61aaa9a2` |

原始请求/截图/数据库/凭据及完整运维元数据保持受限，不提交公开Git。后续最终Chrome与监督回执到达后再新增当前结果，不回写旧FAIL或将NOT_RUN改作例外。


## 部署前QA收口快照（历史）

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
