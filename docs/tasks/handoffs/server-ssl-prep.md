# TLS检查站点与认证代理模板交接

- 任务：SSL准备；分支 `agent/server`；基线 `e8d45a05f14ed7abc60ff9a5bd4698fca841786a`。
- 范围：用户证书安全核验、新域名独立TLS固定检查、未启用的业务代理与Basic Auth模板。变更前向主控列明TLS目录、证书链裁剪、站点与旧站点保留方案，收到继续确认。
- 修改：`deploy/README.md`、`deploy/nginx/vector-search-tls-preparation.conf`、`deploy/nginx/vector-search-business.conf.example`、本交接。没有改变公共契约、Java、依赖、B1规则或运行参数。

## 安全处理与证书公开信息

用户原ZIP：`/Users/qbss/Desktop/国内正式SSL证书-20261023/国内正式SSL证书-lemoncharger.com.zip`，未修改或复制入项目。用Python zipfile先检查全部成员：无绝对路径、`..`、反斜杠和符号链接，单成员大小上限1MiB；仅提取指定pem/key，不读取或执行包内说明。专用临时目录 `/private/tmp/12times-ssl-prep-iuwy6d5l` 为0700，提取和派生文件0600；远端验收后仅清理该本次自建目录，原ZIP与其他目录不动。

| 字段 | 核验结果 |
| --- | --- |
| Subject | CN=*.lemoncharger.com |
| SAN | DNS:*.lemoncharger.com、DNS:lemoncharger.com |
| Issuer | C=CN, O=Beijing Xinchacha Credit Management Co., Ltd., CN=Xcc Trust DV SSL CA |
| 有效期UTC | 2026-07-29 07:05:35 — 2027-02-13 07:05:34 |
| 叶证书SHA-256指纹 | 5D:07:C3:AB:38:51:A6:74:4A:7F:6E:DF:BA:DA:2F:B6:1E:D5:62:0B:12:6D:C6:14:6A:7D:10:5D:41:EF:52:AC |
| 包内证书链 | 叶证书→Xcc Trust DV SSL CA→Certum Trusted Network CA根 |
| 主机名 | text-vector-search.lemoncharger.com匹配；旧多级域名不作为新证书服务目标 |
| 公私钥 | openssl导出公开部分在内存比较一致，未打印私钥 |
| 信任 | 本地和服务器openssl verify使用各自系统信任库、sslserver用途与新域名校验均OK |

`openssl x509 -checkend 0`、`-checkhost`成功。没有使用包内根证书作为额外信任根，服务器fullchain仅叶+中间共2张，不发送根证书。未自签或生成替代证书。

## 预检与真实变更

- SSH仍既有密钥、root@8.155.137.169:22，BatchMode、10秒超时、StrictHostKeyChecking=yes。没有读取私钥或云metadata凭据。
- `resolvectl query`：新域名A=8.155.137.169，无AAAA；443/8080原先无监听，80为本项目既有Nginx。UFW inactive，后端disabled/inactive。
- 旧站点SHA-256维持 `7a6ecb42d60c5b5f8ec3a055b78e91351d4b50191a6dabd89ab4463a6e8e8fbf`，没有替换旧域名配置。
- `/etc/vector-search/tls`原不存在，使用冲突检查后新建；其与子目录`20261023`均root:root0700。通过SSH stdin安全传输fullchain.pem和privatekey.pem，noclobber、umask077，均root:root0600；内容未打印或入仓库。
- 新建 `/etc/nginx/sites-available/vector-search-tls-preparation`（root0644），链接到sites-enabled。`nginx -t`成功后仅reload Nginx，没有改SSH/防火墙、重启机器、触碰数据库或启动业务unit。
- TLS检查配置SHA-256为 `4116878c5948653c211577db643760bd270e3d6fe6e8984a14e525d65d8733a6`，本地与远端一致。
- 新域名80/443固定检查；443默认server使用`ssl_reject_handshake on`，其他路径404，没有proxy_pass。现监听80/443，8080仍未监听。
- 业务模板副本仅放 `/etc/nginx/sites-available/vector-search-business.conf.example`，未链接到sites-enabled；没有创建htpasswd或真实入口密码。用本次新建临时nginx主配置单独`nginx -t -c`成功，随后仅删除该临时主配置，不reload该业务模板。
- 模板同时保护页面和API，固定Host/X-Forwarded-Host，X-Forwarded-Proto=$scheme、Port=$server_port、For=$remote_addr、清空Forwarded与入口Authorization；禁用proxy_next_upstream重放。应用的可信代理必须限定回环，具体配置与Origin复现归主控/QA协调，当前不宣称发现缺陷或联调通过。

## 真实验证

1. 服务器证书/key公开部分再次匹配、`openssl verify -purpose sslserver -verify_hostname ...`返回OK；stat核实目录0700/文件0600。
2. 实际启用站点与待启用模板单独`nginx -t`均成功；后端仍disabled/inactive，UFW仍inactive，旧站点校验和未变。
3. 服务器通过系统信任curl验证9项：GET/HEAD检查200、POST检查405；首页、GET文档API、POST搜索、H2、actuator/env、health均404；每次ssl_verify_result=0。正确SNI但未知HTTP Host返回404；未知SNI的openssl握手明确unrecognized name失败。
4. 公网正常DNS访问 `https://text-vector-search.lemoncharger.com/deployment-check` 返回200与固定 `stage=tls-preparation`；同URL使用`--resolve text-vector-search.lemoncharger.com:443:8.155.137.169`亦200。没有使用curl -k或跳过信任。
5. 公网定向GET `/api/documents`为404，ssl_verify_result=0。公网443当次可达，但不能代替云控制台/安全组规则审查。
6. 两个既有Shell脚本`bash -n`、`git diff --check`通过。本轮没有Shell行为/Java变更，未重跑Maven或业务测试，不用配置校验冒充业务验收。

## 回滚与未完成

仅回滚本次TLS检查时：先核验无后续修改，移除 `/etc/nginx/sites-enabled/vector-search-tls-preparation` 这一项目链接，`nginx -t`通过后reload；保留旧HTTP历史站点、证书受限目录及sites-available副本供审查，不停止共享Nginx、不恢复默认welcome、不删除既有数据。回滚未执行；若未来业务上线，需另审查其专属回滚而非沿用本步骤。

证书受限材料保留服务器路径 `/etc/vector-search/tls/20261023`，不在仓库。证书到期前需运维续期并重新验证链、key和域名。真实Basic Auth配置与凭据、业务代理启用、应用发布、Origin兼容、浏览器、B3/B4及故障验收均未执行。云安全组80/443规则由用户人工确认。Nginx开机启动策略仍沿用当前disabled，不擅自改为enable。

提交SHA由角色最终回报提供，以本文件所在提交为准。
