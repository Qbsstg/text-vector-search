# Nginx准备与部署注释中文化交接

- 任务：证书前Nginx网络准备、deploy存量维护注释中文化；分支 `agent/server`；基线 `f3850bb`。
- 修改范围：`deploy/**`、本交接。未改公共契约、Java、B1算法、运行参数或业务代码。
- 注释整改独立提交：`6d935a8`；逐文件翻译Shell、env、systemd维护注释，保留shebang、标识符、输出字符串和所有有效配置。逐行过滤注释后比对未变化，两个脚本 `bash -n` 通过。没有改写历史提交。

## 授权、预检与变更清单

用户允许指定服务器安装Nginx、独立域名站点及必要主机80/443准备，未提供证书。实际变更前已向主控列明安装包、临时屏蔽新服务、禁用本次新建welcome链接、固定检查端点、其余拒绝、UFW不启用等项目，主控确认继续。

- SSH：既有密钥、root@8.155.137.169:22，BatchMode、10秒连接超时、StrictHostKeyChecking=yes。未读取私钥或云凭据，未查询实例metadata角色密钥。
- 首次 `ss -ltnp`：80/443空闲；既有22、回环DNS与备份代理保留。Nginx包和 `/etc/nginx` 不存在，无既有站点。
- `ufw status verbose`：inactive；`nft list ruleset`空；`iptables -S`、`ip6tables -S`均默认ACCEPT、无规则。未enable UFW、未添加规则、未改变默认策略或SSH规则。
- 服务器 `getent ahostsv4`、`resolvectl query -t A`及 `dig @1.1.1.1 ... A`：8.155.137.169。resolvectl明确无AAAA，1.1.1.1 AAAA无答案。8.8.8.8查询超时，未视为DNS成功证据。本机dig给28.0.3.185/2001:2::3b2，与服务器/公共解析不一致，可能为本地代理假IP；未据此改DNS。
- 没有可见aliyun CLI；没有云安全组控制台/API授权或凭据可用证据，云安全组未核查，保留用户人工项。公网HTTP成功仅证明当次访问路径可达，不能代替规则审查。

## 实际命令与资源

1. 新资源冲突检查后执行 `systemctl mask nginx.service`，临时阻止包安装时启动默认欢迎页。未使用或更改policy-rc.d，未触及其他服务。
2. `apt-get update`、`NEEDRESTART_MODE=l DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y nginx`：成功。仅新增nginx、nginx-common **1.24.0-2ubuntu7.18**，0升级、0删除；未全系统升级。安装报告无需重启既有服务，未重启机器。
3. 以noclobber新建 `/etc/nginx/sites-available/vector-search-http-preparation`（root拥有，0644），内容对应仓库 `deploy/nginx/vector-search-http-preparation.conf`。
4. 仅在确认sites-enabled只有本次安装生成default链接后，`unlink /etc/nginx/sites-enabled/default`；保留 `/etc/nginx/sites-available/default` 原文件。新建同名项目站点链接，未覆盖其他配置。
5. `nginx -t`成功，`systemctl unmask nginx.service`、`systemctl start nginx.service`成功；当前active、开机启动disabled，临时mask已移除。没有启用443 SSL、没有提供或生成证书、没有proxy_pass。
6. 后端 `vector-search.service` 保持disabled/inactive，未上传或运行JAR、未访问H2数据、未输出app.env。

配置SHA-256：`7a6ecb42d60c5b5f8ec3a055b78e91351d4b50191a6dabd89ab4463a6e8e8fbf`。

## 真实验证

- 服务器 `nginx -t`真实成功；Nginx新监听80（IPv4/IPv6），未监听443/8080。
- 服务器回环用指定Host执行11项检查：GET/HEAD `/deployment-check`=200；GET正文精确 `stage=network-preparation\n`；POST该端点=405；GET `/`、`/api/documents`、`/h2-console`、`/actuator/env`、`/health`以及POST `/api/search`均404；未知Host和IP Host访问检查路径均404。
- 公网 `curl --noproxy '*' --connect-timeout 5 --max-time 10 --resolve text.vector.search.lemoncharger.com:80:8.155.137.169 http://text.vector.search.lemoncharger.com/deployment-check`：200，固定文本，无用户数据。直接IP HEAD同路径404。只探测本次指定目标。
- 注释变更后Java17 `./mvnw -B clean verify`成功，Surefire29、Failsafe22，失败/错误/跳过均0。首次沙箱内Wrapper缓存不可写失败，按授权升级后真实运行成功；不把第一次失败当通过。
- 两个既有Shell脚本 `bash -n`、`git diff --check`通过。新配置中的注释使用简体中文。

## 回滚方案（未执行）

当前主机Nginx为本次新装、仅有本项目启用站点。若需撤销全部网络准备：在确认没有后续新增站点/用途后停止Nginx，移除本次项目sites-enabled链接并保留/归档sites-available配置；不重新启用welcome，不卸载包或自动清理依赖，不动SSH/防火墙/后端/数据。若后续有其他站点使用Nginx，不能直接停止共享服务，应先单独移除项目链接，`nginx -t`后reload并明确剩余默认Host策略。任何清理执行前重新确认范围。

## 未完成与下一步

用户人工确认云安全组80/443、提供域名匹配的有效证书链及私钥安全文件；证书不进仓库/对话。443、HTTPS、认证、应用发布/业务代理、真正业务就绪与恢复验收均未执行。后续站点切换必须保留认证、API/页面保护和回环监听契约，不能因HTTP检查成功发布未完成业务。当前Nginx尚未enable开机启动，后续受控上线再明确持久运行策略。

提交SHA在角色最终交接中提供，本文件所在提交即网络配置交付版本。
