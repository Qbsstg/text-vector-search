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

权限已恢复，使用真实Chrome及全新临时H2完成26/29项核证。两处真实缺陷已修复：默认计时器receiver错误导致提交后任务查询被阻断，及默认favicon500；旧失败、修复前失败回归与修复后证据均保留。

[独立UI报告](../testing/UI-browser-verification.md)与[项目证据归档](../testing/evidence/ui-browser-d016d6e/来源说明.md)为实际结果，手工脚本仅操作模板。U12/F03/F05保持NOT_RUN：本页未捕获真实隐藏/恢复事件；U12其他终态/预算/并发层已验但整体不升级；真实FAILED无安全触发条件。不能用合成事件、HTTP/Node或伪终态补齐。

用户已协助200%真实缩放，原件支持布局通过；标签切换虽有用户回执，本页visibility事件仍为空，不能据回执猜测通过。受控分支仅说明前端展示/竞态，不证明数据库未知提交或真实FAILED。

本次本地服务`http://127.0.0.1:8083/`运行03:15:39—03:58:03，北京时间，PID59265已正常SIGTERM退出143。hook已清除、网络已恢复，原目录`/private/tmp/12times-chrome-ui-fixed-3z58ph84/`保留，当前服务已停止。最终截图为[干净页面](../testing/evidence/ui-browser-d016d6e/UI-final-clean.jpg)。

## 后续人工补验入口

继续补验时使用[原29项脚本](handoffs/frontend-final-ui.md)建立另一个全新临时库；先核对空闲端口，不重启或共享已保留库。实际变量如下，脚本中的占位值不得覆盖它们：

```bash
CANDIDATE_JAR='/Users/qbss/code/12times/.cache/delivery/d016d6e8bb431ff17ba81f8977d31a1b96f51f7f/app.jar'
CANDIDATE_SOURCE_SHA='d016d6e8bb431ff17ba81f8977d31a1b96f51f7f'
CANDIDATE_SHA256='64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50'
UI_JAVA='/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java'
UI_PORT=8083
lsof -nP -iTCP:"$UI_PORT" -sTCP:LISTEN
```

F03须确认确是该验收标签所在窗口：实际记录hidden/visible时间、隐藏期GET、预算内恢复及跨预算不重启，不能dispatch合成visibility事件。F05只接受真实后台FAILED与持久错误，未实际发生继续NOT_RUN，不损坏数据库或制造OOM。若只能完成部分，分别记录，完整门禁保持未通过。

正式发布须在UI门禁满足后按[审批清单](最终交付准备与审批清单.md)对新完整SHA/hash及具体操作单独确认。SSL沿用；账号密码不进聊天、URL、JS或Git；生产覆盖/恢复/删除不在普通发布批准中。
