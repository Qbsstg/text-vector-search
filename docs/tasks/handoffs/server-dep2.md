# DEP2 服务器基础环境交接

- 任务：DEP2；分支 `agent/server`；基线 `224bc791b114b1e3a788585dad0ee6baa8c86ad2`。
- 用户授权：指定 `8.155.137.169:22`，使用本地已有SSH密钥配置本项目运行环境。主控收到只读盘点与项目专属变更清单后确认继续；禁止应用发布/启动、既有入口变更、SSH/防火墙调整和数据恢复。
- 修改文件：`deploy/README.md`、`deploy/scripts/prepare-host-dep2.sh`、本交接文件。
- 契约/依赖：未改。继续Java17、8080回环、独立H2路径、Worker=1；没有包安装/升级。

## 真实只读检查

- 本机 `ssh -G 8.155.137.169` 默认user为qbss，known_hosts未找到该IP。使用 `ssh -o BatchMode=yes -o ConnectTimeout=10 -o ConnectionAttempts=1 -o StrictHostKeyChecking=accept-new -p 22 root@8.155.137.169` 成功，记录首次主机公钥；后续连接均为StrictHostKeyChecking=yes。没有读出/复制私钥，没有生成或替换认证密钥。
- 服务器 `cat /etc/os-release`：Ubuntu **24.04.5 LTS**，并非此前估计20.04。
- `nproc`=4；`free -m`内存7526MiB，首次检查可用6994MiB，无swap。
- `df -hT / /opt /var`：40G ext4根盘，已用2.9G，可用35G；`lsblk`确认40G虚拟磁盘，但未从操作系统进一步证明底层SSD介质类型。
- 已有Temurin **17.0.15+6**，`readlink -f /usr/bin/java` 为 `/usr/lib/jvm/jdk-17.0.15+6/bin/java`；无nginx，无需安装包。
- 原有运行服务含SSH、云监控/安全/备份代理、chrony和系统服务。TCP监听为SSH22、回环DNS53及备份代理回环40313；UDP含DNS、DHCP、chrony。未改变它们。
- 项目用户/组、四个项目顶层目录及systemd unit均不存在，故不覆盖既有资源。

## 实际变更与结果

执行前已向主控列明范围：仅项目账号、目录、服务器受限env和待启用unit；保留全部既有服务。

| 资源 | 实际权限/状态 |
| --- | --- |
| vector-search用户/组 | 系统用户UID999/GID988，home=/var/lib/vector-search，shell=/usr/sbin/nologin |
| /opt/vector-search、releases | root:root，0755 |
| /var/lib/vector-search | root:vector-search，0750 |
| /var/lib/vector-search/db | vector-search:vector-search，0700 |
| /etc/vector-search | root:vector-search，0750 |
| /etc/vector-search/app.env | root:vector-search，0640；服务器本地生成随机密码，未输出真实值 |
| /var/backups/vector-search | root:root，0700 |
| /etc/systemd/system/vector-search.service | root:root，0644；ExecStart固定已存在的Java17路径 |
| unit状态 | disabled / inactive / dead；NRestarts=0；TimeoutStopUSec=2min |

- 初次内联命令在Java版本管道处因pipefail/SIGPIPE退出141，发生在账号/目录创建前；改成捕获版本输出后再匹配，整理为仓库脚本后执行成功。未跳过失败或声称首次即成功。
- `bash -n deploy/scripts/prepare-host-dep2.sh`：通过。`--help`退出0；缺少执行标志退出2。
- `ssh ... 'bash -s -- --apply-on-authorized-host' < deploy/scripts/prepare-host-dep2.sh`：首次正式脚本执行退出0，账号/目录/env/unit创建成功；没有安装包、启动/enable服务。
- `systemd-analyze verify /etc/systemd/system/vector-search.service`：退出0。工具同时扫描到已有cloudmonitor unit并提示KillMode=none、旧/var/run PID路径警告；没有修改该既有unit。
- `systemctl daemon-reload`：退出0。`systemctl show`确认disabled/inactive、专用User/Group、120s外层超时、重启计数0。
- `runuser -u vector-search -- test ...`：专用账号可写db、可读env，不可写releases或env；未实际写探针文件。
- 服务器Python仅检查本项目env密码已生成、APP_DATA_DIR固定、Worker=1，输出检查结论，未返回密码。
- 再次执行同一准备脚本：退出1，`CONFLICT: /opt/vector-search`；证明已有资源拒绝分支，未覆盖env/unit。
- `ss -H -ltn 'sport = :8080'`：无输出；后验TCP监听与首次一致。current链接和knowledge.mv.db均不存在，没有应用JAR上传。

## 未执行、风险与下一步

- 未启动或部署应用，未创建数据库，未读写其他项目数据库；未push、安装包、全系统升级、改SSH/防火墙/Nginx站点、开端口或重启机器。
- 未执行Nginx/TLS/入口认证、业务就绪、业务冒烟、关闭/崩溃恢复、备份恢复。静态unit通过不证明Java应用启动或关闭行为已通过。
- 未执行Maven构建；本任务仅部署文档/脚本，不变更Java业务。主控集成按阶段运行verify。
- 真实env仅存在该主机；不得cat到对话或Git。当前没有H2数据，此凭据会在后续首次启动使用；后续不得重新生成并误认为能自动改变已有库密码。
- 120s仍为待测外层关闭预算，JVM堆仍为256MiB—1GiB起点；未因4核/8G擅自提高并发。未来Java补丁路径改变须重审unit。
- 主控下一步：审查本提交，待B2/B3业务恢复/关闭门禁完成后指定发布SHA与实机启动验收；域名/TLS/认证方案另行落实。主机可登录不等于入口已可公开。
- 提交SHA：由交接回报提供，以本文件所在提交为准。
