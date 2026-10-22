# 固定候选与Chrome验收入口

2026-10-23真实Chrome收尾。旧候选`68d80cb967c6d321047672811dea45b555ba5cbc`因真实计时器异常判UI FAIL；其CI252项、JAR摘要与失败原件保留。修复后选用下表新候选，正式服务器发布尚未批准。后续纯文档HEAD不能替换固定候选。

| 字段 | 实际值 |
| --- | --- |
| 完整源码SHA | `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f` |
| CI | [run39838054946](https://github.com/Qbsstg/text-vector-search/actions/runs/39838054946)，attempt1、completed/success |
| 测试 | 104 unit+110 IT+39 Node=253，失败/错误/跳过0；Linux冷备恢复通过 |
| 发布artifact | `12451951931`，release-candidate-d016d6e8bb431ff17ba81f8977d31a1b96f51f7f |
| 本地JAR | `/Users/qbss/code/12times/.cache/delivery/d016d6e8bb431ff17ba81f8977d31a1b96f51f7f/app.jar` |
| JAR SHA-256 / 大小 | `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50` / 27309072字节 |
| manifest SHA-256 | `5e7b06bae4a2829f788590889c1e519a2bb6118dd006e05e17381596184071ea` |
| DDL SHA-256 | `54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d`，未变 |
| 算法 / Java | `char12-sha256-r32-nfkc-root-ws-v1` / Java17，未变 |

JAR、manifest与SUM同目录，不放入Git；主控独立CI日志hash、实包字节与六参数verify-release已交叉核验。CI原件见[来源说明](../testing/evidence/final-ci-d016d6e/来源说明.md)。

## 真实Chrome结果

权限已恢复，使用真实Chrome及全新临时H2完成28/29项核证。两处真实缺陷已修复：默认计时器receiver错误导致提交后任务查询被阻断，及默认favicon500；旧失败、修复前失败回归与修复后证据均保留。

[独立UI报告](../testing/UI-browser-verification.md)与[项目证据归档](../testing/evidence/ui-browser-d016d6e/来源说明.md)为实际结果，手工脚本仅操作模板。F03/U12已补测通过，F05保持NOT_RUN，例外待用户批准。

F03追加真实切页证据：4次正式visibilitychange均isTrusted=true，17次真实GET=首次1+自动14+手动2；两段隐藏内新GET0，原60秒deadline不变，跨预算无自动恢复。实际时刻与计划有偏差，按原件记录，不伪造卡秒操作。补测证据见[来源说明](../testing/evidence/ui-f03-d016d6e/来源说明.md)。旧无visibility的尝试与focus emulation纠正保留，不作为业务缺陷或真实切页证据。F03仍属于受控RUNNING前端层，真实wire均SUCCEEDED。

本次本地服务`http://127.0.0.1:8083/`运行03:15:39—03:58:03，北京时间，PID59265已正常SIGTERM退出143。hook已清除、网络已恢复，原目录`/private/tmp/12times-chrome-ui-fixed-3z58ph84/`保留，当前服务已停止。最终截图为[干净页面](../testing/evidence/ui-browser-d016d6e/UI-final-clean.jpg)。

## 本轮补测已结束，等待用户决定

补测服务独立目录`/private/tmp/12times-f03-xbvb00_6/`，PID62398已于04:22:16北京时间正常SIGTERM退出143，Hikari关闭，观察器已reload清除。原始数据和证据保留，没有覆盖前轮数据库。

F03/U12无需再重复；F05不再要求刻意制造后端FAILED。请审阅[F05例外申请](../testing/F05-例外申请.md)，仅用户明确接受后记录“NOT_RUN / 例外已批准”，不能改PASS。旧29项脚本仍为历史模板，当前判定以独立报告为准。

正式部署审批见[最终审批单](DEP3-最终发布审批单.md)，固定候选不变，当前不授权服务器业务发布。已有SSL及80/443沿用，8080不对公网开放；不重复索取证书或准备基础环境。
