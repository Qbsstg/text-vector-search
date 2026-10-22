# 本地新增Chrome差异验收独立核证

任务FINAL-RELEASE / QA；本次只读主控真实Chrome证据，不接管浏览器、不启动应用、不调用服务器、不重跑原28项或代码测试。固定生产候选为`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，JAR SHA256为`64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`，由本轮`run.json`核对。

原始证据根`/private/tmp/12times-final-browser-5r1x23tc/evidence/`，页面`http://127.0.0.1:8083/`，是本机真实Chrome，不是公网HTTPS闭环。QA读取cases/audit、原始Chrome事件/响应、DOM与截图，逐项核对，不将cases的初步PASS直接当完整原用例通过。

| 差异项 | 模式 | 独立核证事实 | 原编号影响 |
| --- | --- | --- | --- |
| FR-B01 | REAL | 文档ID为`FR_ui.PATH-1792703028864`，真实POST202、任务GET与列表/详情/搜索响应均匹配；渠道`<svg onload="window.__frChan=1">`在三处按原字符串显示，marker0、children0、unsafe0。对应截图/DOM可读，无SVG/脚本执行。 | 补UI-06渠道显示、UI-07合法._-路径 |
| FR-B02错误 | FAULT_INJECTION | 观察器一次发包前返回400，audit的wireSent=false且Chrome无对应POST；code/message/fieldErrors按文本显示，marker0、unsafe0，未知带引号field不执行。焦点submit-channel、aria-invalid=true，原文前空格/尾换行与输入保留，按钮恢复。 | 补UI-02的400、UI-06错误DOM、UI-07错误后焦点 |
| FR-B02恢复 | REAL | 键盘修正渠道后真实POST仅1且202，响应requestId=`64276.40`，原文空白保留，invalid清空，真实taskId=`c5b4ca27-1955-4649-ab7c-2e1a2b37f650`，后续真实任务GET成功。 | 补错误后真实恢复，不冒称受控400来自后端 |
| FR-B03 | REAL | 两个非法详情ID`fr/a`、`fr%2Fa`均新增GET0；非法UUID带?x=1也GET0，原值、焦点、aria-invalid保留。合法._-ID键盘查询GET1，requestId=`64276.43`；UTC原值`2026-10-22T21:04:32.504866Z`对应页面北京`2026/10/23 05:04:32`。Escape关闭dialog回detail-id，错误标记清空。 | 补UI-07键盘恢复/路径/时间 |

`console.json`为空数组，仅表示本轮采集窗口内无记录应用异常，不代表整个系统永无错误。`cleanup.json`记录observerPresent=false、badgePresent=false；run记录PID69723于北京时间05:09:52退出143，app.log可见正常graceful shutdown与Hikari关闭，专用库保留。QA仅核证已有记录，未代行清理或停机。

结合此前已认可的原文/摘要XSS、三视口/200%/长ID/dialog与新增Node路径/解析分支，**UI-06与UI-07可关闭PASS**。这些新增Chrome差异不增加JUnit/Node计数；原28项与新增步骤只做覆盖映射，不重复计工程测试数。

UI-02已有真实409（旧U04）、本次受控400及键盘恢复，但原用例仍明确列429并观察输入保留。本轮材料未见429页面，故在该次核证快照中UI-02整体暂留NOT_RUN，不能用HTTP性能实验的212个真实429代替DOM反馈/输入保留证据；后续单次受控429补测已到达，结果见下文，不扩大生产容量故障。UI-08公网HTTPS页面和服务重启闭环仍NOT_RUN，REC-07契约冲突仍待用户裁决。

PERF-01主控摘要另有1088 accepted/succeeded、212 rejected429、unknown0、writeRetries0；其observedPendingPeakAfterSubmission=996是提交后观察的持久待处理峰值，exactJavaQueuePeak=null，不能写成Java执行队列精确峰值。本文件未将性能摘要直接升级原PERF-01整项PASS，分机原始请求/CPU/RSS/GC等由主控后续合并。

本次真实命令为只读JSON/Network/DOM解析、截图查看、run与日志核对及文档空白检查；无测试重跑、无新增CI结果。受控400严格与真实POST恢复分层，F05保持NOT_RUN/用户批准例外，不改Owner文件。


## UI-02最后429独立补测核证

新独立专用目录`/private/tmp/12times-final-browser-7c556_cc/`的run.json确认同一d016固定候选/JAR摘要，仍为回环8083页面。单次包装脚本`one-shot-429.js`在发包前返回429/CAPACITY_EXCEEDED，响应形状来自PERF-01真实拒绝样本；**本页是FAULT_INJECTION，不是本地库真实容量满**，PERF-01的212个真实后端429仍为独立证据。

QA读取`429-initial.json`、`429-final-reviewed.json`、原始Chrome事件及初末DOM/截图，独立计算观察时长125930ms：只有1次前端意图且wireSent=false，Network POST总数0。documentId、含前空格/尾换行原文和渠道与初始值相同；按钮已恢复，未显示成功task按钮，反馈明确稍后手动操作且未加入成功记录，无自动换ID或重发。原始事件无应用exception/console错误；文件没有console.json，原trace包含无关浏览器扩展log，不能描述整个Console绝对为空，也不公开这些扩展内容。

结合此前真实409、受控400/字段错误与键盘真实恢复，以及原U03/U05空文/省略渠道载荷，**UI-02现在PASS**。受控429不增加JUnit/Node或真实后端拒绝样本计数。cleanup.json记录observer清除；run记录PID70616于北京时间05:15:41退出143，主控已正常关闭Hikari，QA仅读已有证据未操作进程。UI-08公网仍NOT_RUN。

## 性能与剩余条目可关闭审查清单（不代填未知服务器）

- PERF-04可按原材料明确允许的“没有人工标签则只记录分数”观察模式关闭：本轮14IT的8个OBS真实Java输出、算法版本及原始输入源SHA齐全，不预设同义/否定高低分，不报告准确率或虚构保留集指标。若主控要求额外带标签评估，那是尚未执行的独立层，不能说已验。
- PERF-01的HTTP层已可核证：原始requests.jsonl重算1088个202、212个429，1088个terminal GET均SUCCEEDED；task-timings包含1088条排队/处理时间，unknown0、writeRetries0，输入分短/中/长及过载三阶段。持久pending观察峰996、exactJavaQueuePeak为null，不能写精确Java队列峰。完整关闭还需对照服务器CPU/RSS/GC、固定参数与收尾原件，本次未据单个summary擅填PASS。
- PERF-02的HTTP原件存在N100/1000/5000和K1/10/100九组各12条纯搜索、N1000/K10混合30条、成功/拒绝分开统计；环境JSON确认Mac客户端/独立Linux目标及d016摘要。结合池4组件探针27条阶段nano，可关闭分阶段与分机HTTP样本范围；组件微基准不是HTTP trace，完整条目还需服务器CPU/RSS/GC及资源趋势原件。
- PERF-03已有HTTP热点128次中4成功/124拒绝、分散64次中7成功/57拒绝、恢复再一次200；独立散点账本包括恢复共8个成功增量，all5000DeltasMatch=true、unknown0且只读回查无重放。结合池4组件探针各80次热点/分散明确提交及真实锁超时后同SearchService恢复，具备等待/提交/明确失败恢复证据。仍需主控核服务器现场收尾/资源原件，不能代填未知运维状态。
- OPS-01可据本轮clean verify实际报告、Node43、新增14IT与固定JAR独立启动证据关闭本机范围；最新Linux CI/正式产物一致性仍是另外的交付门禁，必须取得实际报告。OPS-02/03/04/06及UI-08正式服务器/公网闭环未提供给本次QA核证，继续NOT_RUN。OPS-05全历史/交付敏感扫描不能只凭新增文件扫描代替，留主控审查。
- REC-07原断言FAIL/契约冲突仍待用户裁决，不能以所有数学/过滤测试通过解除；F05用户已批准例外仍NOT_RUN，不改该Owner文件。

上述是证据充分程度与尚缺来源清单，不把未知Server/PERF层自动改PASS。本次只读核证与文档检查，不新增代码执行数。
