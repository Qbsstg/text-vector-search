# 固定候选与Chrome人工验收入口

2026-10-23。以下是实际成功CI下载的候选，尚未批准正式服务器发布。后续纯文档HEAD或新构建不能替代本候选；JAR字节改变必须重新核对CI和审批。

| 字段 | 实际值 |
| --- | --- |
| 源码SHA | `68d80cb967c6d321047672811dea45b555ba5cbc` |
| CI | [run39834086512](https://github.com/Qbsstg/text-vector-search/actions/runs/39834086512)，completed/success，attempt1 |
| 下载产物 | `release-candidate-68d80cb967c6d321047672811dea45b555ba5cbc`，artifact id12450815373 |
| 本地JAR | `/Users/qbss/code/12times/.cache/delivery/68d80cb967c6d321047672811dea45b555ba5cbc/app.jar` |
| JAR SHA-256 | `61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f` |
| 大小 | 27308443字节 |
| manifest / SUM | 与app.jar同目录的`release-manifest.json`、`SHA256SUMS` |
| DDL SHA-256 | `54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d` |
| 算法 | `char12-sha256-r32-nfkc-root-ws-v1`，Java17 |

主控已从独立CI日志取得JAR摘要，与实际下载字节及6参数验证器交叉核对。该候选本地临时库HTTP闭环通过，不能代替浏览器或真实服务器HTTPS验收。详细结果见[最终验证报告](../testing/FINAL-verification.md)。

## 人工操作入口

本轮再次实测Work本地浏览器仍被禁用，Mac处于锁定状态；不能自动操作。请本人使用Chrome按[完整29项脚本及证据模板](handoffs/frontend-final-ui.md)执行。当前U01—U18、F01—F11全部NOT_RUN，模板不是通过记录。

先在本机Terminal复制以下实际值；随后执行上述脚本第1节中“确认没有其他进程使用该端口后”的启动代码。不要再次复制其中的占位变量覆盖这些值。`lsof`若显示8083已有监听，先停下并选择空闲端口，不终止未知进程；更换端口时同步核对浏览器脚本的origin保护。

```bash
CANDIDATE_JAR='/Users/qbss/code/12times/.cache/delivery/68d80cb967c6d321047672811dea45b555ba5cbc/app.jar'
CANDIDATE_SOURCE_SHA='68d80cb967c6d321047672811dea45b555ba5cbc'
CANDIDATE_SHA256='61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f'
UI_JAVA='/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java'
UI_PORT=8083
lsof -nP -iTCP:"$UI_PORT" -sTCP:LISTEN
```

启动脚本会核对JAR、建立全新`/private/tmp/12times-final-ui.*`专用H2和证据目录，打印PID及`http://127.0.0.1:8083/`，最多运行2小时。本人可在该Terminal按Ctrl+C正常关闭；保留数据与证据，不删除或覆盖任何数据库。不要使用主控已经结束的冒烟库、项目默认data或服务器数据库。

按29项脚本分别记录真实页面和FAULT_INJECTION分支，截图、Network请求次数/响应、Console、候选SHA/hash与操作时间。XSS、键盘、窄屏和dialog焦点必须实际观察；Node或HTTP结果不升级为浏览器PASS。罕见真实失败/竞态没有实际发生时保留NOT_RUN并说明，与受控前端分支分开。

完成后保留本次`evidence/`、`app.log`、PID退出情况和数据库目录，交主控/QA核证。不要在对话、HAR或Git上传入口密码、Authorization、Cookie或真实用户原文。本轮只用合成数据。

## 发布审批入口

人工UI证据核证后，按[最终交付准备与审批清单](最终交付准备与审批清单.md)单独批准此完整SHA、JAR摘要和具体操作。服务器仍只有TLS检查，尚未上传或启动该JAR。需明确入口账号的安全交互方式及应用/Nginx各自开机策略；密码不在聊天中提供。生产数据库替换、恢复、删除均不包含在普通发布批准中。
