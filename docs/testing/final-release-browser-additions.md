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

UI-02已有真实409（旧U04）、本次受控400及键盘恢复，但原用例仍明确列429并观察输入保留。本轮材料未见429页面，故UI-02整体暂留NOT_RUN，不能用HTTP性能实验的212个真实429代替DOM反馈/输入保留证据。待主控提供一次受控或真实429页面后再核证，不扩大生产容量故障。UI-08公网HTTPS页面和服务重启闭环仍NOT_RUN，REC-07契约冲突仍待用户裁决。

PERF-01主控摘要另有1088 accepted/succeeded、212 rejected429、unknown0、writeRetries0；其observedPendingPeakAfterSubmission=996是提交后观察的持久待处理峰值，exactJavaQueuePeak=null，不能写成Java执行队列精确峰值。本文件未将性能摘要直接升级原PERF-01整项PASS，分机原始请求/CPU/RSS/GC等由主控后续合并。

本次真实命令为只读JSON/Network/DOM解析、截图查看、run与日志核对及文档空白检查；无测试重跑、无新增CI结果。受控400严格与真实POST恢复分层，F05保持NOT_RUN/用户批准例外，不改Owner文件。
