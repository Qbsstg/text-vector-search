# DEP3固定候选与操作批准清单

## 当前权威注记：FINAL-RELEASE授权已生效

用户最新FINAL-RELEASE指令优先于下方历史准备稿。已批准固定候选：源码 **d016d6e8bb431ff17ba81f8977d31a1b96f51f7f**，JAR SHA-256 **64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50**，CI **39838054946**。F05保持NOT_RUN并标记“用户批准例外”，不能伪造真实FAILED浏览器结果。

用户已明确批准D1—D9、reviewer认证密码安全生成或安全终端交互，以及vector-search.service和Nginx两项enable。普通已批准范围内操作无需重复请示；密码、env及私钥仍不得输出或入库，生产覆盖/删除、SSH修改、整机重启、其他站点变更等禁止边界保持。

D1已完成真实预检及受限配置备份。D2—D9当前仍等待 **REC07原FAIL的用户裁决和主控明确GO**，并非缺少普通部署授权；未下达GO前不上传/启动正式应用或切换业务代理。生产源码/JAR若改变，仍须给用户新的完整SHA/hash/CI确认，不沿用d016批准值。

当前公开集成main位于 `.worktrees/final-public`，主控通报5897cfd已正常FF推送，仅含13个安全源码/摘要文件，不含自动审批阻止公开的原始载荷；新CI39862014960在该通报时运行中。本注记只记录主控状态，不把新CI提前标为成功或更换批准业务JAR。真实D1/独立实验及工具记录见 `docs/tasks/handoffs/server-final-release.md`。

## 历史准备稿（保留原当时事实、清单与命令）

以下正文写于用户正式授权前，诸如“尚无批准SHA”“需另批准”等为当时准备阶段描述，**不再代表当前普通D1—D9授权状态**。保留用于追溯，不据此重复申请已获普通授权；当前执行边界以上方权威注记和用户最新直接指令为准。

本文件仅准备。当前没有被用户批准的固定发布SHA，本轮不得上传JAR、修改服务器配置、生成认证、启用业务或恢复数据。操作标志不构成授权。历史B1—B4证据保持原样，后续纯文档HEAD不代替真实候选。

## 提交用户审阅的候选

由主控选定完整源码SHA，确认对应CI成功与实际Java17/Node报告，下载 `release-candidate-<完整SHA>`。扁平内容必须包含 `app.jar`、`release-manifest.json`、`SHA256SUMS`；manifest格式见同目录示例，实际值由CI生成，不手填假通过。记录CI run URL、JAR大小/校验值、DDL校验值、健康/UI验收状态和未完成项。

```bash
# 仅验证下载的候选，替换为主控明确选定的真实值；不发布。
bash deploy/scripts/verify-release.sh APPROVED_FULL_SOURCE_SHA APPROVED_JAR_SHA256 FROZEN_SCHEMA_SHA256 \
  /ABSOLUTE/CANDIDATE/release-manifest.json /ABSOLUTE/CANDIDATE/app.jar \
  /ABSOLUTE/CANDIDATE/SHA256SUMS
```

验证器核对类型、固定SHA、独立审批JAR SHA-256、大小、算法/DDL版本及两个文件校验和。审批JAR值必须来自候选包之外已核实的CI记录/审批记录，不得读取当前manifest再作为“审批值”传回；即便JAR、manifest和SUM同时被换成相互一致的内容，也必须因审批值不符拒绝。但校验器不验证CI的网络真实性、JAR代码安全或真人UI，主控仍需把候选与成功CI对应；候选变更必须重验并重新批准。公开仓库不意味着证书/env/数据库可公开。

## 需另外批准的具体操作

服务器 `8.155.137.169:22`；域名 `text-vector-search.lemoncharger.com`。向用户列明以下动作及候选SHA、hash后再执行：

1. 只读重验主机、证书、已有进程/目录/端口和权限；保存本项目当前unit/Nginx配置副本到新的受限变更目录，逐条列配置路径，不收集真实env到工作区。
2. 记录是否已有current和数据库。当前预检均不存在，属于首发；不伪造旧版本。若后来已有应用，先将入口切回TLS检查、停接收和派发，执行正常systemctl stop，确认退出及日志关闭完成；failed/超时不能直接视为干净停止。不要强杀H2线程或删除文件锁。
3. 若已有H2数据，先按下面冷备份流程。首发没有数据库则明确“不适用”，不为备份创建空库。数据库永不放release/target。
4. 上传不可变 `/opt/vector-search/releases/<批准的完整SHA>/app.jar`、manifest和SHA256SUMS，只创建新目录，不覆盖旧版本；在服务器再次验证下载与上传后的校验值。代码目录root维护、服务账号只读；current只在旧进程退出后切换。
5. 安装审阅后的unit模板，保留已有真实env，不重新生成DB_PASSWORD。显式加载proxy，监听仍为127.0.0.1:8080；不改变算法/DDL/资源参数，H2 URL保留DB_CLOSE_ON_EXIT=FALSE、WRITE_DELAY=0，不启用AUTO_SERVER。
6. 单实例使用持久 `/var/lib/vector-search/db/.instance.lock`。首次在无进程时独占创建普通文件，属主vector-search、0600；已存在则核验其路径/属主/权限和inode，禁止删锁重建。unit使用 `flock --exclusive --nonblock --no-fork`；锁跨stop/start保留，备份持同一个inode，H2原生文件锁另外保留。首次准备文件和unit修改仍需本次正式操作授权。120s外层停止预算不是卡住DB事务的安全保证。
7. 当前htpasswd工具缺失。如批准入口认证，另安装必要 `apache2-utils` 并通过交互方式生成用户指定账号/密码，不在命令行、日志、对话或仓库存明文。认证文件root维护、Nginx只读，限制权限；页面和API统一认证，管理/H2端点仍拒绝。不能为了赶时间临时取消认证。
8. 先启动唯一应用，在回环以真实health要求HTTP200且businessReady=true，重复检查启动完成日志/进程/重启计数。health合取不代表UI/TLS/认证验收全部完成。
9. 保留现有TLS默认拒绝和旧HTTP历史站点；仅替换明确列出的新域名TLS检查server，不能直接叠加同server_name造成冲突。启用已审查业务模板前确认Basic Auth和可信头覆盖，nginx -t成功再reload；不批量删除sites-enabled。
10. 逐项执行真实受保护入口验收：未认证页面/API401、有效认证页面与API可用、同源写成功/跨源403、提交→终态→查询/列表/详情→搜索计数→失效排除、正常重启后持久化、重复health/日志/进程证据。搜索与提交不自动重试。浏览器禁用时手工Chrome结果仍需真人补齐，不能用curl冒充。
11. 明确是否批准应用/Nginx开机启动，本轮原状态均不自动变为enable；交付实际SHA、时间、hash与PASS/FAIL/NOT_RUN。

## 冷备份与独立恢复验证

`scripts/cold-copy.sh` 不控制服务、不打开H2、不覆盖或删除文件。仅在操作另获授权后，要求Linux root、systemctl明确inactive、共用永久锁可独占、lsof无其他进程占用，复制到全新受限目录并校验源/目标SHA。出现检查异常、活动库、符号链接、未知附件、覆盖或生产恢复目标均拒绝。执行失败保留现场，不能把残留目录当作有效备份。

```bash
# 仍需另获具体授权；SOURCE_SHA是该库对应版本，不一定等于新候选。
bash deploy/scripts/cold-copy.sh --authorized-cold-copy backup \
  /var/lib/vector-search/db /var/backups/vector-search/NEW_UNIQUE_BACKUP SOURCE_SHA
bash deploy/scripts/cold-copy.sh --authorized-cold-copy restore-check \
  /var/backups/vector-search/NEW_UNIQUE_BACKUP \
  /var/lib/vector-search/restore-check-NEW_UNIQUE_LABEL SOURCE_SHA
```

restore-check只做新目录冷复制与校验，不自动启动应用或证明内容业务可读。后续在新目录、独立端口以匹配版本验证文档/任务/计数，不能使用生产端口或生产路径；服务账号的访问权限仅对这个新目录单独设置并记录。Linux/容器QA须实测同inode锁阻止并发start、活动DB拒绝、备份校验、篡改拒绝、原库不变，以及恢复库启动可读；Mac安全拒绝测试不代替这些结果。

生产库替换、清理及任何会丢弃备份后写入的恢复必须重新列明影响并获具体授权；本脚本没有生产替换模式。H2备份文件和manifest不进Git，不放公网目录，保留周期另行批准。

## 失败与代码回滚

若首发失败且无旧release：保持/恢复本项目TLS检查站点，正常停应用，保留新release、数据与诊断，不删除新库，不虚构可回切旧版本。TLS配置回切也先nginx -t再reload，不能影响其他站点。

若存在已验收旧release：先审查旧程序与当前schema、H2和算法版本兼容，入口回TLS检查，正常停机/确认退出和冷备份后只回切current；再次回环与受保护入口验收。**代码回滚不默认恢复数据**。不兼容则停止回切并另拟方案；任何旧备份覆盖生产均另需授权。
