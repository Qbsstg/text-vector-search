# 最终真实 Chrome 验收核证

> **历史阶段说明**：本文件记录原 UI 验收过程，含旧候选失败、修复及原 28 项通过；不作为当前部署状态。正文、原始 FAIL、测试数量和时间线原样保留，不代表项目当前仍未交付。最终结论请查阅[《最终交付聚合结果》](最终交付聚合结果.md)。

任务：UI-FINAL / QA。QA文档基线：`8a6713c`，分支`agent/qa`，独立工作树`.worktrees/qa`。本文件仅记录已有真实浏览器证据及待执行矩阵；不启动应用、不操作Chrome、不操作服务器、不修改业务与既有证据。执行步骤沿用`docs/tasks/handoffs/frontend-final-ui.md`的U01—U18、F01—F11，共29项。

## 判定规则

- `REAL`：真实Chrome页面、真实同源接口及浏览器真实断网/阻断；需要页面、Network与响应相互对应。
- `FAULT_INJECTION`：明确包装响应、延迟页面处理或发包前抛错，只能证明前端分支；不能证明真实RUNNING、FAILED、负分或后端未知提交。
- 状态只有`PASS`、`FAIL`、`NOT_RUN`。只执行某项的一部分时保持`NOT_RUN`并列出已观察事实。截图名不是状态证据；Node、HTTP与CI不能升级为浏览器PASS。
- 新候选必须重记完整源码SHA、JAR SHA256、CI与独立临时库；旧候选FAIL永久保留。真实RUNNING失效竞态、真实FAILED及自然负分未发生时不得填PASS。
- 所有计数以响应中的十进制字符串记账；失败/未知提交不依据累计值猜测、不自动重放。正式服务器、认证与HTTPS门禁另验。

## 旧候选：真实阻断失败

- 源码SHA：`68d80cb967c6d321047672811dea45b555ba5cbc`。
- JAR SHA256：`61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f`。
- 本机真实Chrome，Network User-Agent记录`Chrome/155.0.0.0`；URL：`http://127.0.0.1:8083/`。未据User-Agent推断精确安装版本。
- 原始目录：`/private/tmp/12times-chrome-ui-skafk2b5/`，证据在`evidence/`；QA只读核证，未复制/修改原始材料。`run.json`记录开始`2026-10-22T19:04:35.878187+00:00`、停止`2026-10-22T19:08:34.802402+00:00`、退出143。
- 结论：**该候选UI门禁FAIL**。真实提交已返回202，但自动任务追踪和两种手动入口均被计时器异常阻断；不能称完整业务UI通过。

独立证据核对：

| 证据 | 已核事实 |
| --- | --- |
| `chrome-events.json`请求`56882.30`、`chrome-response-bodies.json` | 一次`POST /api/documents`返回202；真实documentId=`ui-1792695973737-a`，taskId=`71295722-9e09-4579-a5b0-5a150c4045e4`，响应状态QUEUED。该事件文件内没有任务GET，不证明后端最终终态。 |
| `console-failure.json` | 三次`TypeError: Illegal invocation`均在`TaskTracker.enable`的`tasks.mjs:65:14`；调用链分别经提交表单、`查看真实任务`按钮、任务查询表单。时间为19:06:14、19:06:19、19:06:32 UTC。 |
| `U02-real-task.png`及同名DOM | 输入真实taskId后仍显示“尚未查询任务”和“尚无本页会话任务”，与计时器阻断一致。`U02-task-succeeded`仅为文件名，不能据此宣称SUCCEEDED已验。 |
| 源码`src/main/resources/static/tasks.mjs`第8—14、65行 | 构造器把宿主`setTimeout`直接存入`this.setTimer`，再以成员调用方式执行，receiver成为TaskTracker；与Chrome异常位置吻合。修复应保留正确宿主receiver，并覆盖`clearTimeout`同类边界，由前端Owner实施；QA不改业务。 |
| `chrome-response-bodies.json`请求`56882.29`与U18截图/DOM | `/health`真实200，B4、READY、taskReady/businessReady及六项checks均true；只证明该快照，不等于UI或发布许可。 |
| `chrome-events.json`静态资源 | 首页200；五个静态文件304且媒体类型有效，非模板要求的全200冷加载；另有`favicon.ico`500及Chrome扩展请求。扩展请求不等于应用引入外部依赖，favicon500保留为待复核发现。 |
| `cases.json` | 原记录U01、U18为PASS、U02为FAIL。QA保留原件；U01完整条件尚缺冷加载全200证据，下面按NOT_RUN记录部分已观察事实。U18按本机同源/健康范围核证PASS，不包含服务器项。 |

## 精简29项矩阵（旧轮与新轮分开判定）

新轮固定候选与实际证据见后续追加核证；旧轮结果不得迁移。U09、F05虽然位置不同，均必须取真实后端状态。U12含真实终态与受控时限两层，分别记录。

| 编号 | 核证要点 | 证据模式 | 旧轮状态与实际范围 | 新轮 |
| --- | --- | --- | --- | --- |
| U01 | 首屏、三Tab、同源静态、初始无搜索 | REAL | NOT_RUN；已有首屏/Tab观察，冷加载全200待验 | PASS |
| U02 | 单次提交202、真实任务入口与按钮恢复 | REAL | FAIL；202后计时器Illegal invocation，任务入口阻断 | PASS |
| U03 | 空文成功、ready、终态后10秒停GET | REAL | NOT_RUN | PASS |
| U04 | 重复ID409、无重提/覆盖 | REAL | NOT_RUN | PASS |
| U05 | 省略渠道：提交default、搜索全部 | REAL | NOT_RUN | PASS |
| U06 | Top-K 0/-1/小数/空拦截、恢复整数 | REAL | NOT_RUN；仅有0的页面记录，不代表完整四种已验 | PASS |
| U07 | 同文同分ID顺序、四位分数、自然负分 | REAL | NOT_RUN；自然负分未发生时单列未执行 | PASS |
| U08 | 空查询零分、ID序、K>N及计数 | REAL | NOT_RUN | PASS |
| U09 | 真RUNNING中PUT失效、终态/有效性正交 | REAL | NOT_RUN；未捕获真实竞态不得PASS | PASS |
| U10 | 取消PUT0、真实网络失败不假成功 | REAL | NOT_RUN | PASS |
| U11 | 真taskId查询、网络失败不改FAILED、404 | REAL | NOT_RUN；任务入口已有阻断，其他分支未验 | PASS |
| U12 | 真终态停GET；预算/隐藏/并发另见F02/F03 | REAL / FAULT_INJECTION分层 | NOT_RUN | PASS |
| U13 | 真Offline搜索仅一次、旧结果历史提示 | REAL | NOT_RUN；Offline不证明服务端已提交 | PASS |
| U14 | 原文/摘要XSS纯文本、无额外脚本网络 | REAL | NOT_RUN | PASS |
| U15 | 字符串计数、搜索+1、GET不变 | REAL | NOT_RUN | PASS |
| U16 | 确认失效、旧结果保留、新搜索排除 | REAL | NOT_RUN | PASS |
| U17 | 键盘、dialog焦点、三视口与200% | REAL | NOT_RUN | PASS |
| U18 | 同源请求、真实B4健康；独立发布边界 | REAL | PASS；限本机同源和健康快照，不含DEP3 | PASS |
| F01 | 注入负分/同显示分不重排 | FAULT_INJECTION | NOT_RUN | PASS |
| F02 | 受控RUNNING、60秒/最多3个、不重叠 | FAULT_INJECTION | NOT_RUN | PASS |
| F03 | 受控任务隐藏/恢复不重置预算 | FAULT_INJECTION | NOT_RUN | PASS |
| F04 | 受控网络失败保留上次状态 | FAULT_INJECTION | NOT_RUN；真实Request blocking另记 | PASS |
| F05 | 真实FAILED错误展示、终态停止 | REAL | NOT_RUN；无真实失败则继续未执行 | NOT_RUN |
| F06 | 注入RESULT_UNKNOWN无重发、旧结果历史 | FAULT_INJECTION | NOT_RUN；无真实POST，不能证明未知commit | PASS |
| F07 | 延迟搜索防重、编辑保留原快照 | FAULT_INJECTION | NOT_RUN | PASS |
| F08 | 浏览器迟到搜索+真实PUT、历史标记 | FAULT_INJECTION | NOT_RUN；不证明后端SQL快照竞态 | PASS |
| F09 | 详情A迟到不覆盖B/不复活dialog | FAULT_INJECTION | NOT_RUN | PASS |
| F10 | 发包前失效失败不乐观成功 | FAULT_INJECTION | NOT_RUN；真实Offline另记 | PASS |
| F11 | 提交未知按原ID确认、不换ID重提 | FAULT_INJECTION | NOT_RUN | PASS |

初始旧轮快照中，列表分页、筛选条件、20/50/100、21条以上及空结果、详情长ID/空原文/独立滚动等补充范围均未执行；新轮补充范围见下文最终核证，不能因29行填完而遗漏。

## 首次旧轮核证快照：本次交付与未执行项

实际命令：`git status --short`、`git branch --show-current`、`git rev-parse HEAD`确认QA树干净；`git merge --ff-only main`快进至`8a6713c`；只读`cat`/`sed`/`rg`与Python解析旧轮JSON/事件；查看`U02-real-task.png`进行视觉核证。提交前执行`git diff --check`及矩阵结构静态检查。首次Git元数据写入受沙箱拒绝后以已授权提升重试成功，不影响文件历史。

本次仅新增本文档，无代码测试新增通过数；Java/Node测试未执行，Chrome操作未执行，应用启动未执行，服务器未执行。新轮候选SHA/摘要/CI、完整真实Chrome矩阵及故障注入分支待主控提供后再核证；最终UI门禁仍未通过。证据位于本机临时目录，尚未归档进仓库，需主控保留或另行归档后再作长期交付。


## 新候选准备核证快照：当时尚未判定UI

2026-10-23追加；本次QA树未快进，继续从`838d614f408d429284f00c53d6ec80ca6b497266`独立提交。旧候选FAIL保留。旧轮项目证据已由主控归档至`docs/testing/evidence/ui-browser-68d80cb-failed/`；`review.json`明确修正原始`cases-initial.json`的U01初步PASS，原件不改。完整含扩展活动trace仍保留在旧临时目录，仓库仅归档项目trace。

- 新源码SHA：`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`。
- 新JAR SHA256：`64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`；大小`27309072`字节。
- CI：[39838054946](https://github.com/Qbsstg/text-vector-search/actions/runs/39838054946)。QA读取主控下载日志`/private/tmp/12times-ui-ci-39838054946.log`及主树`.cache/ui-ci-d016d6e/reports/`，不启动或重跑测试。
- 独立汇总19份Surefire XML：104 tests，0 failures/errors/skipped；14份Failsafe XML：110 tests，0 failures/errors/skipped。日志有`BUILD SUCCESS`及Node 39 tests / 39 pass / 0 fail / 0 skipped，合计253项；这是CI证据核证，非本轮新增本机执行。
- 日志的候选封装manifest与上述源码SHA、JAR摘要、字节数、Java17一致，算法版本仍为`char12-sha256-r32-nfkc-root-ws-v1`，schema摘要`54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d`。本次QA未另读JAR实际字节；主控已报告完成实际下载校验与六参数验证，二者来源分开记录。
- Linux冷备日志确认拒绝活动文件占用/单实例锁占用、冷备校验及权限、拒绝已有目标和错误版本/摘要、独立恢复后真实JAR状态/向量/首次时间/计数/失效保留，原备份未改。日志明确systemctl为进程PATH内inactive shim，仅证明一次性Linux runner演练；真实systemd、服务器恢复与发布仍未执行。

修复范围独立审查：`cda3f8c4fdd3e6744f5889794fca354a9328a16a`只包装默认`setTimeout/clearTimeout`经`globalThis`调用，保留注入与轮询参数；新增receiver敏感回归，覆盖首次查询、2秒刷新和停止清理。`bf7aeac2a5ba349486d7cc4319be9d59e18890bf`只增加同源`favicon.svg`及HTML显式引用。两提交另含前端交接文档。以旧候选与新候选比较，Java生产类、schema、基础application.yml与pom无差异；本次修复未扩大算法或后端业务范围。静态审查与Node回归尚不能证明真实Chrome问题已消失。

新轮主控专用目录为`/private/tmp/12times-chrome-ui-fixed-3z58ph84/`、8083、PID59265，由主控独占浏览器与90分钟有界进程；QA未操作该进程/页面。在该次准备快照中，29项新轮当时全部`NOT_RUN`，未提前填写任何UI PASS；随后依据实际操作、截图、DOM、Network/响应与模式标注逐项核证，最终结果见下文及上方新轮矩阵。

本次实际检查为Git只读diff/show、Python汇总XML、日志与旧归档JSON读取、`git diff --check`和矩阵结构检查；未执行Java/Node测试、Chrome、应用启动、服务器操作。


## 新轮真实Chrome首轮收尾快照（当时保留三项未验证）

固定候选沿用`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`及上述JAR摘要。原始证据根：`/private/tmp/12times-chrome-ui-fixed-3z58ph84/evidence/`。主控操作真实Chrome；QA只读截图、DOM、原始Network/Response、`cases.json`及`controlled-audit.json`，没有接管浏览器或服务。该次收尾快照为26项PASS、3项NOT_RUN（U12/F03/F05），后续F03/U12补测见文末；上方矩阵反映最新核证，不是完整UI门禁通过；F03缺少真实visibility证据、U12依赖该缺失层、F05保留真实未发生边界。初步记录与下述实际证据不符时以下述核证为准。

### 真实页面主流程

- U01/U18：冷加载同源静态、favicon.svg均200；真实健康B4/READY/businessReady=true。旧轮favicon500未在本次冷加载复现；健康不表示认证、HTTPS或发布获批。
- U02/U03/U04：提交202后真实task GET成功，提交按钮恢复，旧Illegal invocation阻断不再出现；重复ID单次409。空文最终SUCCEEDED、vectorReady=true、text为空；终态后观察39.743秒无自动GET。真实QUEUED/成功状态及页面一致，不伪造任务四态。
- U05/U06/U07/U08：请求省略渠道与default/全渠道语义吻合；Top-K四类非法值均由原生表单阻止，新增搜索0。同文A/B完整score相同且ID有序，空查询零分、K>N返回全部；真实自然负分`page-18`为`-0.05415303610738823`，不是F01注入值。四位显示和原响应顺序由对应页面/DOM对照。
- U10/U11/U13：完整新窗口真实Offline分别产生一次搜索、PUT、任务GET失败，Network错误为`net::ERR_INTERNET_DISCONNECTED`；取消失效PUT0。A在失败后真实刷新仍有效；旧成功结果保留并警告可能计数，19.063秒观察无搜索重发。真实任务网络失败保留上次SUCCEEDED、不改FAILED；缺失UUID真实404。早期B的URL阻断未生效且已真实失效，不能作为失败/回滚证据，数据未回滚。
- U14/U15/U16：XSS原文与摘要均纯文本，子元素/unsafe节点0、执行flag0、额外x请求0。A首次搜索后计数字符串`"0"→"1"`，随后三次详情GET仍`"1"`；列表响应字符串计数与搜索账本一致。XSS文档真实PUT后旧24行保留历史状态，明确新搜索23行排除该ID，失效不自动搜索。
- 列表补充：主trace记录page0/page1保留渠道，50/100保留渠道与有效筛选，专用不存在渠道返回空；与对应分页/筛选DOM对照。合成分页夹具只算数据准备，不算每条经页面提交验收。

### U09真实竞态的判定依据

U09核心目标判PASS，但两次尝试必须分别叙述。第一次详情真实显示RUNNING，PUT失效时间比完成时间晚21.056ms，因此**第一次不证明计算中失效**。第二次文档`ui-1792696650449-long2`初始任务/详情为QUEUED，页面没有在该次事前捕获RUNNING；原始响应`57422.81`真实PUT时间为`19:28:16.334595Z`，任务响应`57422.83/.84`为`startedAt=19:28:16.101433Z < invalidatedAt < finishedAt=19:28:16.374424Z`，详情`57422.82`为SUCCEEDED、vectorReady=true、invalidated=true。这段同一服务持久时序直接证明失效发生于真实执行区间且终态未覆盖失效，无状态注入。第一次RUNNING展示只证明该真实UI状态可显示，不与第二次PUT拼成同一观察；与脚本“先看到RUNNING再PUT”的逐帧细节差异明确保留。

### 受控前端分支

F01/F02/F04/F06/F07/F08/F09/F10/F11仅判`FAULT_INJECTION`层PASS。F01改显示分数；F06/F10/F11发包前响应/失败分别只调用一次，真实POST/PUT增量0；F11更改输入后查询原提交ID并诚实显示真实404。F09延迟A响应，B完成后释放A不覆盖B，关闭后释放不复活dialog。F08真实搜索1+PUT1，浏览器延迟hits仍保留“已失效·历史结果”，不冒称后端SQL快照竞态。

F07初步驱动按动态按钮旧名称超时并释放延迟，不计PASS；仅`F07-verified-one-post-history`完整窗口支持一次真实POST、请求中按钮禁用、编辑不重发、返回原条件快照标历史。受控audit初步调用n8曾被延迟约37秒，不能冒充脚本3—6秒释放；正式复测为n10单次请求，页面处理延迟约644ms，目的为防重与旧条件展示，不据此评价后端执行时长。

F02独立按audit首四个真实UUID重算调用数为31/29/30/1，均不重叠，前三任务最后一次请求距首次约58.5秒内，第四仅一次GET。第二/三任务最小完成至下次开始间隔为2001.4/2001.7ms；第一任务夹有主控明确手动刷新，短间隔1233.3ms和800.8ms不能用来否定自动2秒规则，也不能把第一任务所有间隔宣称均≥2秒。`F02-budget-expired`截图/DOM确认60秒后停止自动刷新。所有改写任务的wireBody仍SUCCEEDED，只有浏览器呈现RUNNING；F04受控读取失败保留上次状态。该证据不证明后台真实长时RUNNING或FAILED。

### U17核证与F03当前边界

已核U17的方向键/Home/End、键盘提交、375/768/桌面、128字符ID、长原文独立滚动与表格横向滚动。原生showModal后AX只暴露dialog，背景不可交互；末项Tab时`document.hasFocus=false`只证明网页失焦，不能断言背景HTML获得焦点；Escape返回原触发按钮。PRD第7节要求键盘可操作/焦点可见，UI17要求操作可达/可读，脚本“焦点被约束”未要求阻止进入浏览器Chrome或额外JavaScript首尾强制循环；无确证不报应用焦点逃逸缺陷。

用户实际设置200%的同次CUA返回已由主控补档：`U17-200-original-tool-output.json`记录时间`2026-10-22T19:39:38.763Z`、callId=`call_6cec8a1475334ddea9c7ed2f81448be9`，原始结果width956/height576/dpr4/scale1/pageWidth949，与`U17-200-observed-metrics.json`及同调用截图/DOM对应；基准width1913/dpr2。QA读取并核对上述来源、查看截图，确认200%布局和文字可读、页面宽度949≤956；结合此前实际键盘/三视口/控件可达证据，U17整体判PASS。后续健康按钮点击不能宣称发生于200%。`U17-zoom-metrics.json`是之后width2560/dpr2的窗口状态，原件保留且不用于200%证明。

F03用工具另开标签实际仍hidden=false，不算隐藏通过。用户随后回执已切换同窗标签并返回，但主控读取visibility events仍为空、document.hidden=false；QA核对最终F03-user-switch-returned截图/DOM，page-07仅显示60秒预算已结束。用户操作回执不能替代本页实际隐藏/恢复事件，预算自然结束也不证明隐藏暂停，故该次F03为NOT_RUN。U12真空文终态停止及F02预算/并发已通过，隐藏层缺失，该次整项为NOT_RUN。F05真实FAILED没有安全触发条件，最终NOT_RUN，不用网络异常、404或注入替代。本轮不再等待用户，也不为补齐状态制造后台失败。

采集缺口：`capture-notes.json`记录切Page过滤器时截断窗口，U10已在完整新窗口重验；`controlled-setup-note.txt`记录断点初始化加载截断，该窗口不用于资源/请求次数判定。不能将分段trace拼成无缺口全量历史。本阶段QA仅静态/证据核证，不新增Java/Node测试通过数，未操作Chrome/应用/服务器；仅交付本文件，由主控审查后合入；浏览器解除受控、正常停止服务及归档由主控执行，QA未代行或预先声称已完成。

补充证据格式说明：`Tab.screenshot`初期文件虽命名`.png`，实际原始字节是JPEG；QA用`file`核对200%截图为949×571 JPEG。原件不转换，主控归档将使用正确`.jpg`后缀并保存原名映射与摘要，截图引用须沿映射追溯。`F02-poll-stats.json`已补档，与QA独立计算调用数/间隔及预算结论一致；其lastStartAfterFirst以pollStart为基准，QA前述约58.5秒为各任务首次调用基准，两者不混用。该次收尾时visibility events仍为空且hidden=false，F03/U12/F05继续NOT_RUN；后续独立补测不回改该历史事实。


## 首轮收尾交接快照

任务UI-FINAL / QA，分支agent/qa；本轮起点f2675d2db322614c0c2dd021ad4a213d6153568a（此前候选CI核证提交，未单独合入主树），未快进其他分支。修改范围仅docs/testing/UI-browser-verification.md。旧候选FAIL、修复过程、固定新候选253项CI核证与实际Chrome证据分别保留，不改历史结果。

该次收尾的29项中26 PASS、3 NOT_RUN（U12/F03/F05），无新增已确认应用缺陷；不能写29项全部通过或完整UI验收通过。U12仅隐藏层缺失，真实终态停止已验；F05缺真实FAILED数据。F03缺本页真实visibility事件，不据用户回执编造通过。正式业务部署、认证HTTPS、服务器重启持久化仍属主控独立授权与验收范围。

真实执行的QA验证命令为只读Git diff/show、Python解析XML/JSON与Network事件、file识别截图格式、截图查看及git diff --check；矩阵结构检查确认29行且26 PASS/3 NOT_RUN。QA未重跑Java/Node、未启动应用、未操作Chrome或服务器，CI 253通过不记作QA本轮本机测试。原始新轮证据暂在专用临时目录，主控后续项目过滤归档与清理/停止结果另补，不在本提交预报成功。


## F03/U12本地补测最终独立核证

本次仅根据用户已授权的本地补测与证据整理更新本文。固定候选仍为上述d016完整SHA与JAR摘要；补测证据根为`/private/tmp/12times-f03-xbvb00_6/evidence/`。主控及用户操作真实Chrome，QA不操作浏览器/应用/服务器，不重跑其余26项或代码测试。

保留未成功采集的过程：`audit-user-attempt1.json`记录首次尝试32次GET（initial1、automatic29、manual2），原deadline=`1792700161567`一直不变，但没有visibility事件，故不计F03通过。随后`focus-emulation-reset.json`记录主控关闭`Emulation.setFocusEmulationEnabled`，作为自动化环境纠正，不将设置动作或由此产生的焦点变化作为用户真实切页证据，也不据此定性业务缺陷。

第二尝试使用新真实taskId=`3d3163a9-15f8-4a2b-a8b4-19b8768775c4`及新TaskTracker记录，未修改旧记录deadline。QA直接解析`audit-user-attempt2.json`并对照`analysis-attempt2.json`、原始`chrome-events.json`/响应、`F03-user-attempt2.dom.txt`和截图，独立确认：

- 首次GET为北京时间04:19:03.013；新预算起点为04:19:03.012，原deadline=`1792700403012`，即04:20:03.012，固定60000ms。
- 预检有6次trusted visibilitychange；正式轮询内另外4次均`isTrusted=true`。以首次GET为T0，实际隐藏为T10.762至T28.566、T45.547至T89.779，时长17.804秒与44.232秒；两段内新GET均0。实际动作存在偏差，不宣称按提示计划秒数精确完成。
- 第一段在预算内返回可见后1ms开始下一次GET；第二段跨过原deadline返回后没有自动GET。用户实际手动刷新在T35.306与T100.499，全部请求快照deadline保持原值，手动刷新没有重置预算。
- audit分类为initial1＋automatic14＋manual2＝17，Network同task真实GET也是17，单任务无重叠。原deadline后automatic为0；截止后手动响应完成再观察20.014秒没有自动GET。最终tracker为mode=timeout、auto=false，DOM/截图显示60秒预算结束与停止自动刷新。
- 全部真实wire任务状态为SUCCEEDED，仅在浏览器响应层保持RUNNING以验有限轮询。因此F03为`FAULT_INJECTION`层PASS，不证明后端真实长任务，也不升级F05真实FAILED。

观察器的`visibility-handler-complete`只是微任务快照，可能先于应用其他监听器，不能据名称认定应用已处理完visibility。本次判定使用真实trusted事件、后续请求是否发生、原deadline及最终timeout状态，避免把观察器标签当证明。

据上述独立核证，**F03 PASS**。结合原U03真实空文SUCCEEDED且终态后39.743秒无自动GET、F02三任务自动/第四仅手动与60秒预算、U11真实网络失败保留上次状态及本次真实隐藏/恢复，**U12分层组合验收PASS**；真实终态与受控长时轮询来源仍分别保留。

最新总计为**28 PASS、1 NOT_RUN（F05）**。F05例外申请仍待用户明确批准，风险接受不能写PASS；不能宣称29项全部通过或正式发布获批。本次只修改本文，文档空白与29项矩阵检查通过；未新增代码测试执行数，不预报主控随后解除受控、停止服务或归档已经完成。
