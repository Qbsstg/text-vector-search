# FINAL-RELEASE服务器执行记录

## 授权、版本和当前门禁

- 已读取用户附件 `/Users/qbss/.codex/attachments/7b18cd59-7ce2-4938-8929-b89f644275c7/pasted-text-1.txt` 全部680行。用户明确批准D1—D9、reviewer Basic Auth及应用/Nginx两项enable，禁止生产覆盖/删除、其他站点修改、SSH修改和整机重启。
- 固定候选源码 `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`；JAR SHA-256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`；CI39838054946。本角色尚未重新下载候选或把主控通报当作本轮新增测试通过。
- 本角色唯一远端变更Owner，工作树 `agent/server` 干净快进到 `da5867a5ecdb5ae4705e3643e3e96298b9bd7574`。本记录起始只执行D1，D2—D9等主控新增测试门禁明确GO；不重复询问用户已授权的普通操作。
- 本地新增结构化用例在主工作区 `docs/测试工作/04_结构化测试用例.json`，未复制/改写原件。已只读核对OPS01—06和REC01—08；服务器负责OPS02/03/04/06及REC01真实部署部分。REC03—08破坏性/故障注入在隔离环境完成，不为验收破坏生产；OPS01/05与QA/主控证据协同，不重复计数。

## D1：2026-10-23 04:44:19北京时间

真实命令：现有SSH密钥、`ssh -o BatchMode=yes -o ConnectTimeout=10 -o ConnectionAttempts=1 -o StrictHostKeyChecking=yes root@8.155.137.169 'bash -s'`。执行身份root，仅必要只读检查和本次授权的新配置备份。

| 检查 | 实际结果 |
| --- | --- |
| OS/Java | Ubuntu24.04.5LTS；`/usr/lib/jvm/jdk-17.0.15+6/bin/java`，Temurin17.0.15+6 |
| DNS/TLS | 新域名A=8.155.137.169；现有证书匹配，有效至2027-02-13；公开SHA256指纹5D:07:C3:AB:38:51:A6:74:4A:7F:6E:DF:BA:DA:2F:B6:1E:D5:62:0B:12:6D:C6:14:6A:7D:10:5D:41:EF:52:AC |
| Nginx | nginx -t成功；active/running、disabled、MainPID4509、NRestarts0 |
| 应用unit | inactive/dead、disabled、MainPID0、NRestarts0；pgrep -x java无结果 |
| 监听 | Nginx IPv4/IPv6监听80/443，8080无监听 |
| 站点 | 仅旧HTTP和新TLS准备链接；未改SSL、端口或链接 |
| 发布/数据 | 无current，releases为空，DB目录为空，无knowledge.mv.db或永久锁；首发，无旧版可回滚 |
| 权限 | DB目录vector-search:vector-search0700；env root:vector-search0640；unit root0644 |
| 认证/工具 | htpasswd文件/工具均不存在；flock/lsof存在 |
| 磁盘 | 40G根盘，已用3.2G、可用35G、9% |

必要检查命令包括date/getent/openssl x509公开元数据/nginx -t/systemctl show/ss/pgrep/stat/find/command -v/df；未读取私钥或输出env内容，未连接H2。

### D1实际配置备份

新建 `/var/backups/vector-search/config-d1-20261022T204419Z`，root:root0700，子目录nginx/systemd/private均0700。用install复制本项目unit、3份本项目sites-available配置、真实app.env（副本root0600，仅服务器private目录保留），记录本项目enabled链接到0600文本文件。未复制TLS私钥、未收集其他站点、未覆盖任何已有备份，目录使用UTC命名，执行时间为北京时间04:44:19。

源文件均保留；没有触碰生产数据库（尚不存在），没有安装、上传、启动、reload/restart或切业务代理。首发旧库冷备前置不适用，创建合成数据后按D8非覆盖式实测。

## GO后安全认证与验收方案（待执行）

用户已授权必要apache2-utils与reviewer账号。GO后由服务器Python使用secrets安全生成随机密码，仅写 `/etc/vector-search/reviewer-credential.txt` root0600；通过stdin调用bcrypt htpasswd，不把密码放命令参数、工具输出、日志或Git。认证hash文件root:www-data0640。若文件已存在，先核验保留，不盲目覆盖或轮换。

认证验收由服务器root进程在内存读受限文件、构造HTTPS Authorization，输出仅状态/合成业务证据。真实Chrome所需密码由用户通过安全SSH终端查看并本地输入，不能要求发到对话；密码文件不会进入证据或公开材料。保留原DB_PASSWORD，不重建真实env。公网未认证与TLS检查可用外部无凭据请求。

后续D2—D9及真实入口/六接口/重启/冷备新目录恢复/两项enable目前 **NOT_RUN**，不是已完成正式部署。无旧版时D9只能回TLS检查并保留现场，不虚构程序回滚。生产覆盖/删除和高风险故障不在本角色执行范围。

## GO-ISOLATED-PERF：独立Linux实验（不是正式发布GO）

主控在70用例/REC07发布门禁等待期间，另行授权独立Linux性能测试，客户端在本机经SSH转发压测，负载与服务端资源分离。本节不是D2—D9执行：正式current、DB、unit、Nginx、认证均未改变，D1备份保留。

- 04:49:36北京时间只读确认8085空闲、vector-search正式unit inactive、专用用户及Java工具可用。
- 本机两个历史Chrome候选逐一核对SHA，排除旧摘要61bbb02d...；仅使用 `/private/tmp/12times-chrome-ui-fixed-3z58ph84/candidate/app.jar`，SHA精确等于批准值64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50。传输后服务器sha256sum再次一致。
- 新建实验 `/var/lib/vector-search/perf-d016-20261023T0450`，目录/data/logs均vector-search0700，JAR及采样脚本root:vector-search0640；不覆盖已有目录，无current链接。实验H2仅在此目录，未使用真实env或DB_PASSWORD。
- 通过systemd-run建立瞬态 `vector-search-perf-d016-20261023.service`，User/Group=vector-search，独立PORT8085、APP_DATA_DIR，RuntimeMaxSec3600、TimeoutStopSec120，正常超时先SIGTERM。固定Java17、Xms256m/Xmx1024m，原冻结业务参数不变；实验回环直连，不加载proxy配置或开放公网端口。
- JVM PID **7375**；04:51:04健康HTTP200、stageB4、businessReady=true、所有checks为true；ss仅显示回环8085。正式vector-search.service仍inactive/MainPID0/NRestarts0。
- JVM开启 `-XX:StartFlightRecording=filename=.../logs/flight.jfr,settings=default,dumponexit=true,maxsize=256m,maxage=1h`；GC日志位于logs/gc.log，10MiB×3滚动。应用日志logs/app.log。JFR默认配置有观测成本，性能报告须注明，不能与无观测环境混作一组。
- 新脚本 `deploy/scripts/perf-sample.py` 仅读实验PID的/proc stat/statm，每秒写CPU累计秒/单核百分比、RSS和实验H2文件体积；PID starttime改变即退出，最长3600秒，不增加测试API、不访问数据库。瞬态采样unit `vector-search-perf-d016-20261023-sampler.service`，PID7384。首行已真实写入，记录PID7375、cpuSeconds0.16、rssBytes41730048；启动初期h2FileBytes0不表示业务无库。
- 没有第二JVM打开活动H2。现有健康/公开API没有精确Java执行器队列指标，JFR default不可靠提供队列长度峰值；不能把H2文件体积当积压。客户端可另估算接收减终态的pending，但须明确不是精确执行器队列峰值，未采到的指标保持未验证。
- 主控已收到实验端口/目录/PID、固定hash及一小时上限，客户端自行建立本机SSH转发；压测结束由本角色只正常stop以上实验unit，保留H2/JAR/日志，不清理数据。若主控未提前结束，约05:50:51到达一小时上限，需记录实际SIGTERM结果而非假定通过。
- Python AST语法、参数拒绝和/proc字段索引夹具检查通过；真实Linux监控首行与健康已验证。性能吞吐/延迟/队列实验由主控客户端执行，此时尚无性能结论；正式公网业务/认证仍NOT_RUN。

## PERF01正常收尾与PERF02/03新库切换

主控报告PERF01客户端session22413退出0：1300次尝试、1088接收且全部SUCCEEDED、212明确429、unknown0、writeRetry0，观测durablePending峰996（不是Java执行器队列），客户端总414.67秒。本角色不将客户端报告重计为独立服务器断言。

北京时间 **2026-10-23 05:05:27** 仅对第一实验及采样执行 `systemctl stop`。应用日志确认Tomcat05:05:27.854 graceful shutdown complete、Hikari05:05:36.532 Shutdown completed，8085释放，JFR落盘。Java SIGTERM退出码143，瞬态unit未配置SuccessExitStatus143，实际记录Result=exit-code、ExecMainCode=1、ExecMainStatus143、failed；不能把这个unit状态改记inactive，也不能仅据其failed断言强杀或关库失败。日志证明本次执行了正常关闭；正式unit仍inactive。未来正式unit退出状态策略需主控协调，未借此次实验改正式配置。

第一轮原件保留于 `/var/lib/vector-search/perf-d016-20261023T0450`，数据库未删/未重用。logs文件及SHA-256：

| 文件 | 大小bytes | SHA-256 |
| --- | --- | --- |
| app.log | 3924 | a471d6444ea5c7c33d1ad141342647ad74b4b2dcb891a4e59b86cf30bd7eb75b |
| flight.jfr | 16853432 | 3b4d2431faa6210bc91e3b1c705a1f7a72ed745f7f4a7598b93eea0db3fe9e78 |
| gc.log | 962079 | d464f323478218a3bfb7be704a142e5c06e91bd57baec90dd835811e4c5ac86a |
| process-metrics.jsonl | 139754 | a6bd1eba81d00a823d19c42c15cb354d03764ac0e3b7a05cb5b6250997058300 |

资源摘要从这些原件实际计算：885个约1秒采样点、首末跨度884.541903秒，覆盖启动/空闲/实际负载/关闭，**不是414.67秒纯负载窗口**。/proc utime+stime累计CPU从0.16到610.61秒，差610.45秒；每间隔CPU增量÷墙钟÷CLK_TCK，100%表示一核，平均69.0072%、采样峰380.6580%（4CPU主机可超过100%）。RSS采样峰1,468,198,912bytes，包含堆外/线程等，不等于Xmx；H2体积采样峰1,051,131,904bytes，不代表积压或最终压缩后大小。GC统一日志中以Pause且以毫秒结束的完成行621条，行耗时和9165.903ms，最长194.739ms；这是完成暂停行口径，不将其当所有GC阶段/收集次数或请求p95。JFR summary真实可读，2个chunks、875秒，默认观测配置保留，不外推无限持续负载性能。

随后获主控授权只建立第二轮全新目录 `/var/lib/vector-search/perf-search-d016-20261023T0506`，从第一实验已校验JAR复制代码与采样脚本，不复制或清空数据库。新JAR再次sha256sum等于批准64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50。瞬态unit `vector-search-perf-search-d016-20261023.service`、PID **7877**，vector-search用户、独立data、原冻结参数/堆/GC/JFR、8085回环、1小时保护；新采样unit同名-sampler。约06:05:46到达上限，结束后仍须核对实际状态/日志。

05:05:55第一次健康探测发生监听前curl000，没有假记READY或业务重试；05:06:16复核health200、businessReady=true，ss确认PID7877仅回环8085，已通知主控可继续同一SSH转发的第二轮N100/1000/5000、K1/10/100、混合与热点压测。生产unit/current/Nginx/正式DB、D1备份均未改变，正式D2—D9仍等待主控GO。此次仅追加真实记录，无Java/脚本行为变更，git diff --check通过；未重跑Maven/Node。

### SIGTERM退出码配置复核

主控收到第一轮143/failed真实记录后授权部署模板最小调整。只读 `systemctl show ... -p SuccessExitStatus -p MainPID -p ActiveState` 确认实际正式unit与第二实验unit的SuccessExitStatus均为空，第二轮PID7877仍active，正式PID0/inactive。

仅在本地systemd示例新增 `SuccessExitStatus=143`，解释Java在Spring/Hikari优雅关闭后可能以143退出；其他错误仍按失败处理，不改变JAR、线程/容量/等待参数，也不把143单独当作数据库已干净关闭的证明。正式D4必须安装后执行systemd-analyze verify并真实stop核实unit非failed、Hikari完成及进程退出。本地无systemd，当前不把静态文本修改标为Linux验证通过。

已向主控提出第二轮的具体方案：新建仅本实验的/run/systemd/system下SuccessExitStatus143 drop-in，verify后daemon-reload，运行JVM不restart、PID不变、DB不动。此时方案尚未执行；第一轮failed原始状态/证据保持，不重置或覆盖历史。该问题首先属于实验unit的退出状态策略，不据此宣称业务生命周期缺陷。

后续主控明确批准该精确方案，**05:08:18北京时间**已执行：新建 `/run/systemd/system/vector-search-perf-search-d016-20261023.service.d/exit-status.conf`，仅Service的SuccessExitStatus143。`systemd-analyze verify /run/systemd/transient/vector-search-perf-search-d016-20261023.service`退出0（仍提示既有cloudmonitor KillMode/旧PID路径警告，未改该服务）；`systemctl daemon-reload`后show确认SuccessExitStatus143、MainPID7877不变、active、NRestarts0、DropInPaths为上述单文件。没有restart/stop或数据库操作；第一轮仍failed/Result exit-code/ExecMainStatus143，未reset-failed。该实测验证配置加载且进程持续运行，真正停止后的inactive/Hikari结果仍待第二轮结束，不提前记PASS。

## PERF02/03正常停止、资源归档与真实状态

主控报告第二轮客户端完成exit0：全新5000篇真实向量，N100/1000/5000×K1/10/100每组12次200；混合N1000共30成功；热点128次=4成功/124明确429，分散64次=7成功/57明确429，恢复探针200；全5000文档ledger核对热点增量4、分散7+恢复1增量8匹配，unknown0、writeRetry0。客户端负载窗口368.585秒。这些属于主控客户端实测，本角色不重新累计为独立服务器请求断言。

主控通知无负载后，**2026-10-23 05:18:31北京时间**执行 `systemctl stop vector-search-perf-search-d016-20261023.service` 及对应-sampler。Tomcat05:18:31.864 graceful complete、Hikari05:18:32.495 Shutdown completed；05:18:32的 `journalctl -u ... -o short-iso` 明确 `Deactivated successfully` 和 `Stopped`，并记录Consumed 2min49.340s CPU、588.2M cgroup memory peak。PID7877已不存在、8085无监听、正式service仍inactive，实验库/日志不删不复用。

**瞬态状态注意**：成功停止后systemd自动回收该transient unit，后查LoadState=not-found、ActiveState=inactive、SubState=dead；show曾给默认Result=success/ExecMainStatus0/SuccessExitStatus空，这些不是保留下来的真实Java退出码，不能写成“Java退出0”。停前已实际核验SuccessExitStatus143，成功停止证据采用journal及关闭日志。回收后journal出现无法再打开 `/run/systemd/transient/...service` 的诊断，未掩盖，也不将其误归业务异常。实验drop-in留存，第一轮failed原始证据未reset。

### 第二轮全生命周期资源

原件目录 `/var/lib/vector-search/perf-search-d016-20261023T0506`。766个约1秒采样，首末765.472030秒，包含启动、空闲、造数/负载及关闭，**不能当作368.585秒纯客户端负载窗口**。/proc utime+stime从0.26到168.73秒、增量168.47秒；相邻采样以100%=一核计算，平均22.0007%、峰367.3595%。RSS采样峰590,733,312bytes；H2文件体积峰186,638,336bytes，停止后主文件149,983,232bytes。RSS/GC/JFR均含观测开销，RSS不等于Java堆；体积不等于积压。

GC统一日志中Pause且以ms结尾的完成行133条，总886.032ms、最长31.692ms，这是暂停完成行口径，不充当请求延迟或所有GC阶段次数。精确队列峰值未采集，不用客户端durablePending或H2大小冒充Java执行器队列。资源摘要为logs/resource-summary.json，含时间戳/定义/客户端报告窗口；JFR不进Git。

### 第二轮文件manifest与安全归档

服务器 `experiment-file-manifest.json` 记录停止后实验各普通文件，SHA-256为 `fc71b72a628c46ded3daaf6dffb0de01be88b5c9c9aa0b950f4c84d797bf8d1b`；manifest自身摘要单独记录，避免自引用。全部原件保留：

| 相对文件 | bytes | SHA-256 |
| --- | --- | --- |
| app.jar | 27309072 | 64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50 |
| data/knowledge.mv.db | 149983232 | 376bbb9dba22b8555c47eb9185593c795a60d2d17af37cb512b9b0bc8e9dca9c |
| logs/app.log | 3945 | 49924521afe599e6ab7cf6cdfa34edaee54bc9392c36dd18bc00f2cf903ef35a |
| logs/flight.jfr | 7558535 | b57b20fd2594e592c84b37fc756ebe8ead2ad994326f58740fe3339592f7ff24 |
| logs/gc.log | 211115 | b01b3313c44c4de449c8b4499f60d2cbc83467994831139e2e8e850c66a284be |
| logs/process-metrics.jsonl | 121076 | 4209325e9ed58a8f33693aea00eda916cc87e1934d4a58546d9c76dc1fd908a3 |
| logs/resource-summary.json | 646 | 463ac4daca2b07d23a4b3a546f12d5919c4fb0145e167c2b675e2c85d410fb37 |
| perf-sample.py | 1856 | c82bef0f0fbc9738c36ad49b84dd61fd7abc343d542724e051a03ecae7008609 |

另新建root0600归档 `/var/backups/vector-search/perf-search-d016-20261023T0506-resources.tar.gz`，36267bytes，SHA-256 `0795b1ca00cf3fd0c9f70374a8d323535c8f5320eb63261b9969003f58972fc4`，仅含metrics、GC、resource-summary及全文件manifest，供主控安全scp；不含JFR、数据库、env、密码或私钥。没有覆盖已有归档。本次只追加执行记录，git diff --check通过，未重跑Maven/Node；正式D2—D9仍需主控测试/REC07门禁GO，没有启动正式service或改变Nginx。

## 两轮可下载资源包及待审查离线字节核验方案

按主控要求补齐第一轮resource-summary及脱敏startup-stop-metadata JSON，保留原有app/GC/JFR/metrics，新增experiment-file-manifest.json，摘要为 `6e0bf0ef502ebf4aac04b549de94359d346b5f69832be606348096e52039c67a`。启动停止元数据明确第一轮143/failed及未配置预期退出码，不改写历史。新包root拥有且0600，第二包重新只读核验摘要未变：

| 可安全scp绝对路径 | bytes | SHA-256 |
| --- | --- | --- |
| /var/backups/vector-search/perf-d016-20261023T0450-resources.tar.gz | 101235 | 0be219a2fb6c5e29e1dc3be2513d8efa4f7f1a9cd01a0e07857cb9248f3f0e24 |
| /var/backups/vector-search/perf-search-d016-20261023T0506-resources.tar.gz | 36267 | 0795b1ca00cf3fd0c9f70374a8d323535c8f5320eb63261b9969003f58972fc4 |

两包仅metrics/GC/summary/manifest，第一包另含脱敏启动停止JSON；没有数据库或JFR实际内容，也没有env/secret/私钥。manifest仅列原件路径和摘要不等于包含原件。原始库、JAR、JFR、日志继续保留实验目录；正式unit仍inactive，Nginx未变。

### D8完整字节核验替代方案（仅供主控审查，未实现/未执行）

不增加测试API，不同时以第二JVM打开活动库。建议在正式STOP、Hikari完成、永久锁独占且lsof确认H2无占用后，先做已有受控冷复制；验证源/新副本整库文件SHA一致，再仅对新副本执行离线SELECT。如此既能证明停机时原库内容，又避免诊断连接可能在正式目录创建H2边车文件。保持生产原件不变，记录每次副本来源/时间/整库hash。

最小Java诊断工具由主控审查后再实现：使用批准JAR内同版本H2驱动（新受限目录提取，不升级驱动），进程内读取root0600的凭据副本/受限env中DB_PASSWORD，绝不将密码放args、输出或日志。URL显式 `IFEXISTS=TRUE;ACCESS_MODE_DATA=r;DB_CLOSE_ON_EXIT=FALSE`，JDBC再标readOnly，不执行初始化/迁移/写入。只允许主控固定的合成ID前缀参数，以PreparedStatement绑定，排序document_id/task_id；不得直接拼SQL或读取非测试文档。

字段应覆盖documents的ID/channel/submitted_at/invalidated/invalidated_at/hit_count（十进制字符串）/embedding_version/vector_completed_at；对raw_text按固定UTF-8计算SHA-256，vector_bytes以getBytes读取全部1024字节计算SHA-256，null与零向量明确区分，并记录长度。关联embedding_tasks的task_id/status/run_token/attempt_count/embedding_version/submitted_at/started_at/finished_at/error_code及脱敏错误摘要。时间统一UTC ISO，空值保留null，不输出原文/向量原始字节或密码。

建立三份排序快照：正式首次停机冷副本基线、正常重启后的再次冷副本、独立恢复新目录。对已终态合成对象逐字段/完整向量SHA/textSHA比较；pending恢复的token/attempt/time合理变化按任务状态单独解释，不能硬称所有token永不改变。整库文件hash用于证明冷复制一致，因正常运行H2元数据变化不能要求重启后的整库文件hash仍相同；完整行/向量摘要才是跨重启的业务断言。此方案尚未在生产或实验库运行，不把vectorReady或API详情当作完整向量字节验收，后续实现/执行需主控GO。

## 第三轮：只读执行器队列采样（独立测试GO）

主控要求补齐PERF01实际Java执行器队列采样，不能拿durablePending996替代；仅批准100篇20k长文、5分钟第三新库，不重跑1300全压力。新增 `deploy/scripts/QueueReadOnlyAgent.java`，不改固定业务JAR、生产代码、资源参数、HTTP端点或正式运行环境。

先从批准JAR提取真实spring-boot-4.0.3.jar到本次/private/tmp专用构建目录，用JDK17 javap -private/-c核实SpringApplication.shutdownHook为static final；SpringApplicationShutdownHook.contexts为LinkedHashSet，registerApplicationContext在其Class monitor内修改集合。发现Field.get(null)可能触发尚未初始化的SpringApplication，因此先向主控说明风险并获明确批准：JDK17只用Unsafe.shouldBeInitialized读取初始化状态，不get/put/allocate/ensureClassInitialized；确认Boot自己已初始化后才读取静态字段。

agent仅使用Instrumentation.getAllLoadedClasses发现已加载类，绝不添加transformer/redefine。复制contexts在已核实Class monitor内极短持锁，锁外isActive/getBeanFactory、containsSingleton/getSingleton只拿现有完整单例，禁止getBean提前构建；仅调用dispatcher公开queuedCount/inFlightCount/isRunning。daemon最多300秒、每50ms写全新0600 JSONL。独立QUEUE_READ_ONLY_EXPERIMENT=1且APP_DATA_DIR canonical路径必须匹配 `/var/lib/vector-search/perf-queue-d016-专用标签/data`，生产db或其他路径拒绝；只读这两个实验环境变量，不读env秘密或数据库。反射/同步/采样存在观测成本，不承诺零时序影响；保证的是不改字节码、不主动初始化业务类/Bean、不控制调度或参数。两项计数为相邻读取非原子联合快照，峰值仅50ms观测峰，不是连续精确极值。

Java17编译成功（Unsafe内部API及待删除警告如实保留，仅固定JDK17隔离诊断适用），无外部依赖。临时构建产物只在 `/private/tmp/12times-queue-agent-2n979guh`；编译class/JAR不入Git。实际guard夹具确认缺实验flag和非专用APP_DATA_DIR均拒绝；源码git diff --check通过。字段/guard不兼容不能自动退回有副作用办法，观测失败保持NOT_RUN。

第三新目录 `/var/lib/vector-search/perf-queue-d016-20261022T213513Z`（UTC标签），新data/logs0700、专用vector-search用户，8085空闲后创建；不复用前两库。固定业务JAR远端SHA仍64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50；独立agent SHA为1265d322a24b93b0cd2598b0555f15455b742cb16a64c8ac5043fe451b283fb7。瞬态unit `vector-search-perf-queue-d016-20261023.service`、PID8758，05:35:36北京时间启动，RuntimeMax300s、SuccessExitStatus143、原固定参数/堆/JFR/GC及独立/proc采样；约05:40:36到上限。

**05:35:49** /health200、B4 businessReady=true，真实只读样本为 `OBSERVED,queuedCount=0,inFlightCount=0,isRunning=true`，间隔约50ms；不是WAIT或用持久积压替代。已通知主控READY发100长文。样本原件 `logs/queue-observations.jsonl`；此时负载峰/关闭结果尚未产生，不提前PASS。正式unit/Nginx/current/正式DB与D1备份均未改变；正常结束保留第三库/日志，仍不是正式部署GO。
