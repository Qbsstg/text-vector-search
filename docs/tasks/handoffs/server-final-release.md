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
