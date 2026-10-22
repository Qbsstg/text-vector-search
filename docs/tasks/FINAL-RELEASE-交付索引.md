# FINAL-RELEASE 交付索引（执行中，非正式交付完成）

2026-10-23；PM只读主树ac02e5cc67a2db4550cf38c9f9a203c46d919c3d最新QA实证后建立。本文件只是A—F产物和缺口入口，不替代原件或实际部署报告。**当前整体BLOCKED：REC-07契约FAIL待用户裁决，正式5项NOT_RUN。** [实时任务板](backlog.md)与[完整要求/执行跟踪](FINAL-RELEASE-执行跟踪.md)保留逐项门禁及历史快照。

## 两个版本维度与计数

- 批准部署包固定source `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`、JAR SHA256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`、原CI39838054946；旧68有真实ChromeFAIL，不能发布。
- 最新测试源码 `230c3471b789e994c46346c06d3dd83989273f95`、[Linux CI39859349901](https://github.com/Qbsstg/text-vector-search/actions/runs/39859349901) success，104 unit+124 IT+43 Node=271。旧253＋新增14 IT＋4 Node去重，性能main/Chrome/70需求编号不累加。
- 最新70主用例：64 PASS、REC-07 FAIL1、NOT_RUN5（UI-08、OPS-02/03/04/06）。F05另保留NOT_RUN/APPROVED_EXCEPTION；只有F05已有有限例外，不外推到REC-07。
- 最新CI实际下载/校验/启动/冷备恢复原d016/64bc JAR，生产树与批准版本一致；测试构建包或后续文档HEAD不替换生产候选。systemctl inactive为隔离shim，不是实机运行证据。

## A—F产物、当前完整性与缺口

| 用户交付物 | 已有可读入口 | 当前产物状态 | 仍不能宣布正式已交付的原因 |
| --- | --- | --- | --- |
| A 最终测试总报告 | [本轮总报告](../testing/FINAL-RELEASE-测试总报告.md)、[新增执行记录](../testing/final-release-local-cases-results.md)、[独立核证](../testing/final-release-evidence-review.md)、[本地Chrome差异](../testing/final-release-browser-additions.md) | IN_PROGRESS：工程/本地分层结果已交，原失败/修复/复验保留 | REC-07原FAIL仍待决；正式服务器/公网5项未验，不能只用工程271绿色给全部PASS |
| B 本地新增测试矩阵 | [70条原编号/来源/方法/结果矩阵](../testing/final-release-local-cases-matrix.md) | DONE（矩阵产物），不是70全PASS | 64/1/5为真实结果；原资料10文件只读保护，02/04不算140，后续现场结果由QA更新 |
| C DEP3正式部署报告 | [服务器执行记录](handoffs/server-final-release.md)；D1真实预检/受限配置备份已交，隔离PERF及工具范围分别记录 | IN_PROGRESS：目前正式D1完成，D2—D9 WAIT_GO/NOT_RUN | 尚未正式上传/启动/切Nginx/Auth/enable，不把配置备份、TLS检查或临时实验unit当正式DEP3 |
| D 完整业务验收记录 | QA矩阵/核证、本地真实HTTP/Chrome及预部署清单已有工程范围；正式范围按跟踪文件第六节 | IN_PROGRESS，正式业务未交付 | 公网HTTPS六接口/计数失效、服务器重启持久化、真实D8冷备独立恢复、UI-08尚未现场执行 |
| E 发布与回退说明 | [原操作范围清单](DEP3-最终发布审批单.md)、[发布操作计划](../../deploy/DEP3发布操作计划.md)、D1受限配置备份记录 | IN_PROGRESS：方案和授权已具备，实际运行配置/版本/状态未收尾 | 旧审批单“待批准”为历史，本轮D1—D9/reviewer/两项enable已批准；GO后需真实unit/proxy/env/日志/重启/回退记录。无旧版失败回TLS检查，源库/备份不覆盖 |
| F 最终项目交付总结 | 本索引/执行跟踪为当前进度入口，最终结论由主控出具 | BLOCKED，不能标完整交付PASS | 保留DONE/PASS/FAIL/NOT_RUN/APPROVED_EXCEPTION/BLOCKED；REC-07裁决及五个正式未验项尚缺，P0/P1分级不得编造 |

旧[FINAL-verification](../testing/FINAL-verification.md)、B1—B4报告和真实失败原件只作各自历史范围，不倒填为本轮全部通过。所有总结均需链接日志/XML/TAP/截图/响应/版本和原始方法，文件存在不等于范围验收完成。

## 本轮归档与真实性边界

- [公开摘要与本地原件说明](../testing/evidence/final-release-predeploy-d016d6e/来源说明.md)：原始请求、截图、资源明细与逐成员清单保留本地隔离分支，原提交3a142e7不推公开；本页只发布聚合结果及原件SHA。原件公开推送被自动审批拒绝后，主控选择更小公开范围，不丢弃证据或改历史。
- [新增本地Chrome核证](../testing/final-release-browser-additions.md)：本机回环REAL/FAULT_INJECTION与单次受控429分层，不代正式HTTPS认证/服务器UI-08；44个原件条目和12张JPEG完整保留本地。
- PERF分机HTTP、组件样本、全生命周期CPU/RSS/GC和独立队列轮分别归档；只证明本轮有界观察，采样峰不是连续精确最大，不宣传未定义SLA或无标签语义准确率。
- 原REC-07 wire/原断言错误保留，JUnit诊断绿不消除原FAIL；失败和早期快照不抹。PUBLIC有限格式扫描不保证任意秘密都已识别，新归档继续审查；用户原材料不原样公开，秘密不进索引/日志。

## 当前关键路径

REC-07明确裁决→主控核候选/新增结论并GO→已授权D2—D9/两项enable→正式UI-08、OPS02/03/04/06现场核证→A—F完整收尾。等待裁决期间继续其他不依赖项，不向用户重复普通部署审批。不得自行豁免REC-07或改核心迎合旧预期；若必须换生产包，仍需新SHA/hash/CI精确版本确认。

本次索引未操作Chrome/服务器/代码或重跑测试，未宣称C/D/E正式报告已经交付；实际新证据仅由对应Owner和主控核证回填。
