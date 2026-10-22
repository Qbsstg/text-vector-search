# DEP3-PREP交接

- 分支：`agent/server`；基线 `cd400f5`，从干净工作树按主控指定快进。任务仅本地发布准备与远端只读预检，没有正式发布授权。
- 修改：`deploy/README.md`、`deploy/DEP3发布操作计划.md`、manifest示例、`verify-release.sh`、`cold-copy.sh`、systemd示例与本交接。
- 没有改变公共契约、Java、CI、依赖、DDL、资源参数、真实env或私钥。提交SHA由最终角色回报提供。

## 真实只读预检

使用既有SSH密钥、root@8.155.137.169:22，BatchMode、10秒连接超时和StrictHostKeyChecking=yes。只读命令包括getent、openssl x509公开元数据、nginx -t、systemctl is-active/is-enabled/cat、stat/find目录元信息、df、java -version、command -v和ss。没有读取env内容或密码，没有远端写入、包安装、上传、启动/重启/认证创建、打开H2或恢复删除。

- DNS A为8.155.137.169；证书新域名匹配、到期2027-02-13，公开SHA256指纹仍为5D:07:C3:AB:38:51:A6:74:4A:7F:6E:DF:BA:DA:2F:B6:1E:D5:62:0B:12:6D:C6:14:6A:7D:10:5D:41:EF:52:AC。
- nginx -t成功，Nginx active；启用站点仍只有本项目旧HTTP和新TLS检查。80/443监听，8080未监听，既有SSH/云代理保留。
- vector-search disabled/inactive；实际unit尚未启用proxy或flock，本次只修改仓库模板，不冒称已安装新版unit。
- 无current链接，release目录空，无knowledge.mv.db。首发没有可回滚旧版或可冷备份数据库。
- env root:vector-search0640、TLS目录root0700、key root0600；受限DB目录0700；可用磁盘35G。
- Java为Temurin17.0.15+6；flock/lsof/python3/rsync可用，htpasswd缺失、认证文件不存在。工具补装与凭据创建留待具体正式操作授权，本轮未执行。

## 产物与限制

- manifest按主控CI格式：schemaVersion/sourceSha/jarFile/jarSha256/sizeBytes/javaTarget/embeddingVersion/schemaSha256/createdAtUtc；SHA256SUMS覆盖app.jar和manifest。验证器检查固定SHA、类型、路径、大小、DDL/算法、两个校验值，不能替代CI真实性/UI/用户批准。
- 主控复核后增加独立审批JAR SHA-256必填参数：参数顺序为源码SHA、审批JAR SHA、schema SHA、manifest、JAR、SUM。manifest自报值和实际字节均必须等于审批值；审批值不能从当前未核实候选重新抄取。时间仍严格接受UTC的Z后缀，与主控更新的CI helper一致。
- unit示例显式proxy，使用 `flock --no-fork` 加持久 `/var/lib/vector-search/db/.instance.lock`；备份共用同一inode，stop/start不删除重建，H2原生文件锁继续保留。首次锁文件预建与实际unit修改尚未执行，需批准后的运维步骤。
- 冷复制脚本要求Linux root、service明确inactive、永久锁独占、lsof无其他进程占用；只白名单源/目标、全新目录和普通文件，禁止覆盖及生产恢复。复制主数据库并记录SHA，restore-check只进入新目录校验；失败保留现场，恢复库业务可读需另在独立端口验收。
- 操作计划明确单版本不可变release、全入口认证/可信转发头、同源/跨源测试、正常停止、首发失败回TLS检查与保留数据，旧版兼容才能代码回切；生产恢复/清理另获具体授权。

## 真实本地验证

- 全部deploy Shell `bash -n`通过；`git diff --check`通过。
- manifest使用本次/private/tmp专用普通字节夹具验证，非真实发布JAR：完整匹配通过；源码SHA、路径jarFile、Java类型、大小、DDL hash、算法不符及SHA256SUMS路径均拒绝；额外包篡改拒绝曾单独验证。夹具仅清理本次自建临时目录。
- 审批值绑定修正后另做真实夹具：独立审批SHA匹配通过；同时重写JAR、manifest的hash/大小和两项SUM使三者一致，仍因独立审批SHA不符拒绝；缺审批参数及非法审批值均拒绝。`bash -n`和`git diff --check`通过。仅修改校验器/计划/交接，不触main、CI或远端；此夹具不是真实候选验收。
- 冷复制六项拒绝检查通过：缺标志、未知模式、相对路径、非本项目源、restore指向生产db、备份指向其他目录。没有执行生产备份或恢复。
- 未运行本轮Maven（无Java变化）；未对新unit做远端systemd-analyze verify或stop/start；本轮禁止远端写入，不能借“准备”执行测试变更。

## QA/CI待验证

Linux隔离环境先用项目同路径专用夹具，测试持久锁跨stop/start同inode、备份持锁阻止新实例、真实活动H2/lsof拒绝、已存在目标/符号链接拒绝、备份和新目录恢复校验、篡改/丢文件拒绝、恢复库匹配版本启动可读及源库不变。不得指向真实服务器数据库。Mac语法与拒绝路径不是Linux冷备份/恢复PASS。

下一步由主控选定成功CI不可变候选、补齐健康/UI/独立QA门禁，给用户固定SHA/hash和操作列表，获明确确认后再执行DEP3。无候选/认证/UI不应被发布脚本绕过；云安全组仍用户人工审查。

## 2026-10-23 02:41北京时间追加只读快照

此节为新快照，不覆盖前述历史结果。工作树保持 `6c261b3ed2bbf25ab891e0436e559c7b7099f098`，未快进到main或修改其他角色文件。主控提供上下文：main `8fd97e1`已推、104unit/110IT/38Node本地通过，CI run39833314291仍运行中；这些是主控通报，本角色本次没有另行核验CI或产物，不把它记作发布候选已批准。

只读SSH在 **2026-10-23 02:41:17 CST +0800** 开始并结束。命令为 `ssh -o BatchMode=yes -o ConnectTimeout=10 -o ConnectionAttempts=1 -o StrictHostKeyChecking=yes root@8.155.137.169 'bash -s'`，脚本仅执行以下检查：

| 检查命令 | 当次实际结果 |
| --- | --- |
| `TZ=Asia/Shanghai date` | 2026-10-23 02:41:17 CST +0800 |
| `getent ahostsv4 text-vector-search.lemoncharger.com` | 8.155.137.169（STREAM/DGRAM/RAW） |
| `openssl x509 -in /etc/vector-search/tls/20261023/fullchain.pem -noout -dates -fingerprint -sha256 -checkhost text-vector-search.lemoncharger.com` | 新域名匹配；有效期2026-07-29 07:05:35至2027-02-13 07:05:34 GMT；指纹仍为5D:07:C3:AB:38:51:A6:74:4A:7F:6E:DF:BA:DA:2F:B6:1E:D5:62:0B:12:6D:C6:14:6A:7D:10:5D:41:EF:52:AC |
| `nginx -t` | syntax is ok；test is successful |
| `systemctl show nginx.service -p ActiveState -p SubState -p UnitFileState -p NRestarts` | active / running / disabled / 0 |
| 同上检查vector-search.service | inactive / dead / disabled / 0 |
| `ss -ltn '( sport = :80 or sport = :443 or sport = :8080 )'` | IPv4/IPv6均监听80与443，8080无监听 |
| `stat`检查current、release、knowledge.mv.db及htpasswd | current不存在；releases为root:root0755目录；knowledge.mv.db不存在；/etc/nginx/vector-search.htpasswd不存在 |
| `find /opt/vector-search/releases -mindepth 1 -maxdepth 2` | 无条目，未发布任何release |
| `command -v htpasswd` | 无输出，工具仍缺失 |
| `df -h /opt /var/lib/vector-search` | /dev/vda3，40G总量，已用3.2G、可用35G，使用率9% |

公网快照 **02:41:21 CST +0800**：`curl --noproxy '*' --connect-timeout 5 --max-time 10`普通DNS访问HTTPS `/deployment-check`返回固定 `stage=tls-preparation`，HTTP200、ssl_verify_result=0；本机当次remote_ip为28.0.3.211（本地代理解析路径），不能记成直接连接服务器IP。相同方式GET `/api/documents`返回404、ssl_verify_result=0，没有公开业务代理。

补充定向快照 **02:41:30 CST +0800**：上述curl加 `--resolve text-vector-search.lemoncharger.com:443:8.155.137.169`，同检查端点HTTP200、ssl_verify_result=0、remote_ip=8.155.137.169，正文仍为 `stage=tls-preparation`。两次均未使用-k，单次请求最长10秒；没有请求用户数据或其他目标。

复核结论：仍属**首发**，无旧release/生产数据可回切；SSL现有有效材料无需用户重给。当前仅TLS检查入口PASS，不是正式业务入口PASS。执行前仍需批准固定source SHA、独立JAR SHA-256与对应成功CI候选，以及具体上传/新release/current、持久锁和unit/proxy修改、必要apache2-utils安装及安全认证创建、启动策略、受保护业务站点切换和重启验收操作。首发失败回TLS检查并保留现场；任何生产恢复/删除另批。

正式systemd业务启动/单实例/停止/重启持久化，以及真实入口401/认证/同源写/跨源403/六接口闭环仍 **NOT_RUN**。云安全组仍用户人工。此次没有远端写入、安装、认证生成、上传/启动/reload/restart，没有读env内容或私钥、打开H2、备份/恢复/删除。本次仅追加文档，`git diff --check`通过，未重跑Maven或Node。
