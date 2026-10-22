# DEP3最终发布审批单（待用户批准）

2026-10-23。此文件是可审阅的操作范围，不是执行授权。F03/U12补测已经独立核证PASS，当前28 PASS、F05保持NOT_RUN；用户仅授权本地补测与证据整理。**F05例外和正式DEP3操作均尚未批准。** 不再展开B1—B4工程评审，不重跑已通过26项，不重做SSL/80/443基础准备。

## G0：F05例外决定，与发布操作分开

- [ ] 接受[F05例外申请](../testing/F05-例外申请.md)披露的缺口：固定生产JAR真实FAILED经HTTP进入Chrome后的错误显示及停止轮询组合未实测。允许作为UI例外保留，状态仍为NOT_RUN，不改PASS。
- [ ] 不接受该例外，继续保留UI门禁未完成；不授权破坏H2、OOM、伪造FAILED或改变算法来补齐。

以上为互斥选择，均未预选。已有9 unit、16 IT与前端替身是CI253子集，不重复累加；不能把风险接受写成测试通过。若不接受例外，以下操作仍不能执行。

## G1：待正式批准的固定版本

| 项目 | 不可变值 |
| --- | --- |
| 完整源码SHA | `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f` |
| JAR SHA-256 | `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50` |
| CI | [39838054946](https://github.com/Qbsstg/text-vector-search/actions/runs/39838054946)，completed/success，104 unit+110 IT+39 Node=253，零失败/错误/跳过 |
| JAR大小 / Java | 27309072字节 / Java17 |
| 发布产物ID | `12451951931`，与本地实包、独立CI日志hash及manifest已核对 |
| 新release目录 | `/opt/vector-search/releases/d016d6e8bb431ff17ba81f8977d31a1b96f51f7f/` |
| 持久数据目录 | `/var/lib/vector-search/db`，不随release切换 |
| 目标 | `8.155.137.169:22`，`https://text-vector-search.lemoncharger.com` |

- [ ] 明确批准上述固定版本作为本次正式发布包。后续文档HEAD或别的CI构建不替换它；旧68候选永久排除，旧失败与修复记录保留。

## G2：服务器操作范围（当前全部未执行）

| 编号 | 需明确批准的操作 | 文件/边界 |
| --- | --- | --- |
| D1 | 发布前只读核对进程、端口、证书/配置元信息、current/release/DB占用；在服务器新建受限变更备份目录保存本项目配置副本 | 沿用现有SSL与80/443，不重新索取/安装证书，不改云安全组、SSH、其他项目或全系统升级；不读取/输出密码或私钥 |
| D2 | 上传app.jar、manifest、SUM到上表全新release目录，核对服务器实际字节；在旧进程退出后切换current | `/opt/vector-search/current`；不覆盖已有release，不把DB放release |
| D3 | 必要时仅安装apache2-utils，并通过安全终端交互创建用户指定入口账号 | `/etc/nginx/vector-search.htpasswd`，root维护、Nginx只读；账号见G3，密码不进对话、命令参数、日志、URL、JS或Git |
| D4 | 核对并启用本项目proxy配置、受限env、单实例锁和审阅后unit，daemon-reload | `/etc/vector-search/app.env`、`/etc/systemd/system/vector-search.service`、`/var/lib/vector-search/db/.instance.lock`；保留既有真实DB密码，vector-search非root运行；flock与H2文件锁并用，锁同inode保留，不启用AUTO_SERVER |
| D5 | 正常启动/停止/重启唯一应用，先验回环真实健康与启动日志、进程/重启次数 | `vector-search.service`；仍只绑定127.0.0.1:8080，单JVM/单H2，保持原算法、DDL及资源参数，不强杀H2线程 |
| D6 | 仅将本项目新域名TLS检查server切为已审阅的整站认证业务代理，nginx -t后reload | 现有项目配置`/etc/nginx/sites-available/vector-search-tls-preparation`及其项目链接；执行前列明实际路径并保留副本，只改目标server，不叠加重复server_name，不覆盖其他站点；复用现有证书 |
| D7 | 执行实际HTTPS入口和重启持久化验收，使用明确标识的合成数据 | 未认证页面/API401、有效认证、HTTPS同源写成功/跨源403、六接口、计数、失效、正常重启后状态保持、重复health/日志；不自动重试提交或搜索，不自动删除测试记录 |
| D8 | 如已有业务DB，正常停机后持同一永久锁、lsof无占用时冷复制到全新备份目录；独立新目录/端口恢复演练 | `/var/backups/vector-search/<新唯一标签>`与`/var/lib/vector-search/restore-check-<新唯一标签>`；不覆盖生产或备份、不删除源库；若确为首发无DB则记不适用，不为备份创建空库 |
| D9 | 发布失败时，正常停本项目应用，保持或切回原TLS检查入口，配置检查通过再reload | 保留release、数据库、备份及诊断；不虚构旧版、不删除新库。已验旧版存在时，先审查schema/H2/算法兼容才代码回切，不默认恢复数据 |

D6的可信代理只信任回环Nginx覆盖的Host/X-Forwarded头，清除客户端Forwarded与入口Authorization，禁止代理自动重放；外部`/health`、H2 Console与管理路径拒绝。HTTP访问策略沿现有配置，不以开放业务绕过HTTPS或认证。

- [ ] 批准D1—D9的本项目范围；如有不批准项目，应明确编号，依赖项未获授权则停在对应门禁。

## G3：执行前需要用户填写的选择

| 项目 | 待用户明确的值 |
| --- | --- |
| 入口账号名 | 待填写；不要在聊天提供密码，密码由用户在安全终端交互设置 |
| 应用开机启动 | 启用 / 保持disabled，必须单独选择 |
| Nginx开机启动 | 启用 / 保持disabled，必须单独选择；不从应用选项推断 |

即使批准开机策略，也不重启整机测试。未获enable批准时只做获授权的手动服务运行，不擅自enable。

## 执行与终止条件

顺序：G0明确接受例外、G1/G2/G3正式授权齐备→只读预检/配置保留/必要冷备→固定JAR校验→单实例回环健康→整站认证代理→实际HTTPS/六接口/计数/失效→正常重启持久化→启动策略核对与交接。任一步证据失败按D9退回并保留现场，不降低断言或公开未保护业务。

**不包含：** 删除或覆盖生产数据、备份覆盖生产、丢弃新写入的恢复、清理已有release/目录、修改SSH、整机重启、全系统升级或其他项目变更。这些如以后需要，必须另列数据影响并批准。

## 当前结果与剩余工作

本次本地补测原件见[来源说明](../testing/evidence/ui-f03-d016d6e/来源说明.md)，QA结论见[UI报告](../testing/UI-browser-verification.md)。本地PID62398已正常停止，hook清除，专用H2保留；服务器本轮零操作，仓库保持PUBLIC。

剩余有效工时：获授权后DEP3及实际入口/重启约1—2小时，交接约15—30分钟；等待用户例外决定、账号安全交互与操作批准另计。18:00目标不放宽授权、安全、事务或真实证据门禁。
