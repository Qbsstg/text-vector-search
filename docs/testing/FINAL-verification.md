# 最终健康、UI与DEP3准备验证报告

2026-10-23，北京时间。用户已原则批准B4后端核心，本轮只实现真实健康判断、最终验收及发布准备。正式业务尚未发布，浏览器门禁尚未通过。原B1—B4验收、历史SHA、CI和故障修复原件保留，不重写历史。

## 固定源码和实际候选

| 项目 | 核实结果 |
| --- | --- |
| 固定候选源码 | `68d80cb967c6d321047672811dea45b555ba5cbc` |
| 成功CI | [run39834086512](https://github.com/Qbsstg/text-vector-search/actions/runs/39834086512)，completed/success，attempt1 |
| 原始报告 | [final-ci-68d80cb](evidence/final-ci-68d80cb/来源说明.md)，逐字节下载并有SHA256SUMS |
| 发布artifact | id12450815373；`release-candidate-68d80cb967c6d321047672811dea45b555ba5cbc` |
| JAR SHA-256 | `61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f` |
| 大小 | 27308443字节 |
| DDL SHA-256 | `54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d` |
| 算法版本 | `char12-sha256-r32-nfkc-root-ws-v1`，未变 |

JAR、manifest与SUM已下载到`/Users/qbss/code/12times/.cache/delivery/68d80cb967c6d321047672811dea45b555ba5cbc/`，不放入Git。主控从独立CI日志的`jarSHA256`记录读取期望摘要，再与实际字节比较并运行六参数`deploy/scripts/verify-release.sh`通过。manifest自报hash不能单独充当审批值。artifact digest是归档包摘要，不能混同JAR摘要。

后续纯文档提交与报告HEAD不改变本候选。GitHub另一次构建即使源码仅文档不同，也不能默认为相同字节；必须按明确候选SHA/hash审批。

## 本地与Linux真实回归

| 分层 | 实际结果与边界 |
| --- | --- |
| 主控完整本地 | 2026-10-23 02:38:51完成，`5ba03c8`合并基线加当时未提交的CI/说明变更；Java17 clean verify为104 unit+110 IT，Node38，总252通过，失败/错误/跳过0 |
| 固定源码Linux CI | `68d80cb`原XML为104 unit+110 IT，日志Node38，总252通过，失败/错误/跳过0；所有工作流步骤success |
| 健康验收 | 9项真实聚合unit及3项Controller unit，13项独立健康IT；非READY不借连接、DB不可读/只读/缺字段、缺handler/搜索/映射、调度停止和版本不匹配均拒绝就绪 |
| 无副作用探针 | 生产B1成功并搜索后20次真实health均200/B4/checks全true；embed调用0、文档/任务与计数不变 |
| 临时库恢复IT | 正常关闭后冷复制到全新目录，SHA一致，真实应用打开独立恢复库后任务/token/完整向量/首次时间/原文/计数/失效均保持，源库与备份不变 |
| Linux部署脚本 | 一次性GitHub-hosted Linux中7次冷复制调用保持原断言：活动H2拒绝、永久锁占用拒绝、真实冷备及SHA/0700/0600、覆盖拒绝、错版本拒绝、新目录恢复、再次覆盖拒绝；恢复JAR真实读取状态/计数/失效保持 |
| 下载JAR本机冒烟 | 另建`/private/tmp/12times-final-candidate-wcs_p30l/db`，PID54327、随机127.0.0.1:57517，25次HTTP请求验证六接口、计数0→1、失效幂等/后续排除、重复健康及代理协议同源允许/跨源403；SIGTERM退出143，Hikari正常关闭，专用库保留 |

Linux脚本PASS事件、目标测试和冒烟不累加成JUnit数量。独立QA核证见[final-qa-results](final-qa-results.md)，原件见[成功CI证据](evidence/final-ci-68d80cb/来源说明.md)与[本机冒烟](evidence/final-candidate-local-smoke/来源说明.md)。本机代理检查使用HTTP及受信任代理头，未执行真实TLS/Nginx认证。Linux仅systemctl的inactive前置是测试shim，实际H2、lsof、flock、复制和恢复不模拟；不能据此宣称真实服务器systemd已验收。

真实Maven命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 clean verify
node --test tests/frontend/*.test.mjs
```

下载适配参数不构成交付依赖，CI直接执行`./mvnw -B -ntp clean verify`。本地日志保留于`/private/tmp/12times-final-main-verify.log`、`/private/tmp/12times-final-main-node.log`；Node使用本机捆绑Node22绝对路径。

## 首轮真实失败及修复

`8fd97e1`的[run39833314291](https://github.com/Qbsstg/text-vector-search/actions/runs/39833314291)为failure：Java和Node仍252通过，活动库备份被安全拒绝，但脚本将lsof退出1且非空输出过严判为“矛盾”，不符合原占用分类断言；封装/上传候选skipped。保留[首轮96份原件](evidence/final-ci-8fd97e1-failed/来源说明.md)，不抹去失败。

QA诊断`caa6d020`保留原断言；服务器Owner修复`52cda701`，使用p/f/n机器字段严格检查PID、实际锁FD和路径，其他PID、自身非锁占用、异常格式/诊断仍拒绝。成功Linux原始诊断确认lsof4.95.0确实可rc1、stdout含活动Java与自身锁、stderr空。修复后活动H2仍正确拒绝，正常冷备与恢复通过。此修复不改变算法、任务状态机、API、DDL或资源参数。

## GitHub公开与历史

用户已确认此前公开由本人操作并明确授权公开；本轮执行授权后API为`private=false/visibility=public`。详见[公开授权与复核](../git/公开授权与安全复核-20261023.md)，旧私有/公开观察保留，不回写历史或自动回私有。

公开前824个可达blob未命中指定PEM私钥、GitHub token、AWS key格式，未将PDF/证书ZIP/真实env或运行数据库纳入历史。新归档同样逐文件扫描上述格式，JAR/DB/key不入Git；格式扫描不是任意秘密的完备证明。全部新增维护注释和Commit说明使用简体中文，无force push/rebase/amend已推送历史，用户未跟踪`docs/测试工作/`保留不纳入。

## UI安排与当前未执行状态

本轮`cua.getState()`仍返回browsers为空、Work local browser disabled，另有Mac locked提示，[原结果](evidence/final-candidate-local-smoke/work-browser-state.json)保留。未通过其他方式绕过禁用。U01—U18和F01—F11共29项均NOT_RUN。

用户可从[固定候选与Chrome验收入口](../tasks/最终候选与Chrome验收入口.md)复制实际路径/hash，按[29项手工脚本](../tasks/handoffs/frontend-final-ui.md)在全新临时H2上操作并填写证据模板。覆盖提交/轮询/列表/详情/搜索/计数/失效、键盘窄屏/dialog、XSS和请求竞态；真实与FAULT_INJECTION单列。HTTP/Node/模板不能冒充浏览器通过。罕见真实分支未出现继续NOT_RUN并交主控判断，不编造失败或伪终态。

## 服务器实际快照与DEP3准备

服务器Owner于北京时间02:41:17—02:41:30只读复核：域名`text-vector-search.lemoncharger.com`指向8.155.137.169，现有有效证书匹配、信任验证通过、到期2027-02-13；Nginx检查成功，80/443监听，公网部署检查200/API404。后端inactive/disabled、NRestarts=0，8080无监听，无current/release/knowledge.mv.db，磁盘可用35G；Java17.0.15、flock/lsof/python3/rsync可用，htpasswd及认证文件缺失。实际为Ubuntu24.04.5LTS，沿用DEP2，不按最初20.04估计重配主机。

本轮没有远端写入、包安装、账号/认证创建、env修改、上传/启动、Nginx业务切换、数据库打开/备份/恢复/删除。命令、路径与原结果见[服务器交接](../tasks/handoffs/server-dep3-prep.md)。SSL材料无需重给；公网安全组控制台未查验，需用户人工核对。

[DEP3操作计划](../../deploy/DEP3发布操作计划.md)已准备不可变release、独立JAR审批值验证、受限env、全入口Basic Auth、回环可信代理头覆盖及禁止重放、systemd非root/持久flock与H2原生锁、正常停止、冷备、全新目录恢复和回滚。后端继续127.0.0.1:8080，不开放H2/管理接口。

当前属首发，没有旧release或既有业务库。失败时正常停新应用、保留数据/日志/release，并恢复TLS检查站点；不能虚构回切旧版。以后存在已验旧版时，先核对schema/H2/算法兼容才代码回切，代码回滚不默认恢复数据。冷备仅在进程退出、持同一永久锁且lsof无其他占用后复制到新目录，恢复验证只进全新restore-check目录。生产覆盖/删除、丢弃备份后写入的恢复必须另批。

## 具体批准项与上线门禁

依据用户本次明确要求，正式服务器发布仍需单独确认版本和操作；本报告和脚本不是授权。需审阅并批准：

1. 上表完整源码SHA、JAR SHA-256与成功CI；只上传新`/opt/vector-search/releases/68d80cb967c6d321047672811dea45b555ba5cbc/`，不覆盖旧包，服务器再次校验。
2. 必要的apache2-utils安装、由用户指定入口账号并在安全终端交互创建密码；密码不进对话/命令参数/Git。
3. 保存本项目既有配置副本，保留真实DB密码，配置proxy/固定回环端口、永久锁文件与审阅后unit；不改SSH、不重启整机、不清理已有数据。
4. 正常启动/停止/应用重启、current切换、本项目Nginx全入口认证业务站点替换，nginx -t后reload；不覆盖其他项目站点。
5. 分别明确应用与Nginx开机启动策略，不从普通启动推定enable获批。
6. 发布前若已出现数据库，另确认正常停止后的新目录冷备；独立恢复演练也需确认路径/端口，生产替换/删除不在普通发布授权内。

门禁顺序：真人UI证据核证→固定版本/具体操作批准→只读预检与配置保留/必要冷备→JAR和单实例/回环就绪→受保护HTTPS入口→未认证页面/API401、认证可用、HTTPS同源写成功、跨源403、六接口/计数/失效→应用正常重启后持久化→重复health、启动完成日志、进程/NRestarts与启动策略证据。服务器实测未完成前均NOT_RUN，不能由CI或本地代理头测试提前勾选。

剩余关键路径：用户Chrome与QA核证约1—2小时；获得具体授权后DEP3及真实入口/重启约1—2小时；最终交接约0.25—0.5小时，人工等待另计。目标仍为2026-10-23 18:00，不为赶工降低门禁。语义效果、业务负载性能、故障断电保证等未验证项目保持未验证，探针非只读标志也不保证未来磁盘永不写失败。
