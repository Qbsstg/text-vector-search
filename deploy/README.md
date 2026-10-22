# 部署准备：C0模板与DEP2主机基础环境

本目录包含C0模板和DEP2基础环境准备记录，不表示应用已经部署。C0基线：`0f692387577be629d296d3a5fe0ba626072f93b4`；DEP2基线：`224bc791b114b1e3a788585dad0ee6baa8c86ad2`，运行/API契约 v1.0。用户已授权配置指定服务器，最新域名及TLS检查见下节；真实入口认证与业务发布仍待完成。其他模板路径仍需按真实目标核对。

## 当前TLS检查与新域名

最新域名为 `text-vector-search.lemoncharger.com`；旧多级域名仅保留历史HTTP检查。2026-10-23已核验用户证书ZIP：证书在有效期内、SAN覆盖新域名、私钥匹配、系统信任链验证通过。仅叶证书和中间证书提供给客户端，不发送根证书。材料仅存服务器 `/etc/vector-search/tls/20261023`（目录root0700、文件0600），本地本次临时解压副本已清理；原ZIP未修改，仓库不含证书或私钥。

`nginx/vector-search-tls-preparation.conf` 已启用：新域名80/443仅GET/HEAD `/deployment-check`返回固定 `stage=tls-preparation`。其他路径404，其他方法405，未知SNI拒握手；没有业务代理。正常DNS和`--resolve`公网访问均通过系统信任验证，API拒绝验证通过。后端仍disabled/inactive，UFW未改，云安全组审查仍需用户人工确认。

`nginx/vector-search-business.conf.example`仅为待启用模板，服务器sites-available有副本，未链接到sites-enabled；单独`nginx -t`成功不代表业务联调成功。模板在页面/API统一Basic Auth下覆盖转发头并清除客户端Forwarded与入口Authorization；未来应用只应信任回环Nginx代理。实际Origin兼容性由主控/QA先复现再决定调整，不直接信任任意客户端转发头。没有生成真实入口密码、启动应用或开放真实API。证书公开元数据、命令、回滚和未执行项见 `docs/tasks/handoffs/server-ssl-prep.md`。

## DEP2 实测与已准备资源

2026-10-22通过现有本地SSH密钥，以root登录用户指定的 `8.155.137.169:22`。首次用accept-new保存主机公钥，后续连接严格校验。实测Ubuntu **24.04.5 LTS**，覆盖先前20.04的估计；4CPU、7526MiB内存、40G ext4根盘（当次可用35G）。已有Temurin17.0.15+6，无需安装包；未安装Nginx或改变现有云代理/SSH等服务。

准备了 `vector-search` 专用系统用户/组（nologin）、root拥有的 `/opt/vector-search/releases`、服务可写的 `/var/lib/vector-search/db`、root维护的 `/etc/vector-search`、0700的 `/var/backups/vector-search`。真实随机DB密码只生成在服务器 `/etc/vector-search/app.env`（root:vector-search，0640），未输出或保存到仓库。Worker仍为1，不因主机资源较多自动提高并发。

`/etc/systemd/system/vector-search.service` 使用已核实的 `/usr/lib/jvm/jdk-17.0.15+6/bin/java`；已通过 `systemd-analyze verify` 并执行daemon-reload，保持 **disabled / inactive**，NRestarts=0。校验工具另报告既有cloudmonitor unit的KillMode/旧PID路径警告，未修改它。尚无current链接、JAR或应用数据库，8080未监听。

`scripts/prepare-host-dep2.sh` 是本次一次性准备过程的可审查脚本：仅在明确授权的root会话中使用 `--apply-on-authorized-host`。脚本不通过SSH自行选择主机，不安装包、不上传/启动应用、不启用unit；任何已存在项目目录、用户/组或unit都会拒绝，因此**不是可反复重跑的配置管理工具**。中途失败须先盘点已有状态，不得清理后盲目重跑。此脚本绑定当次已验证Java路径，未来JDK改变后需重新审查。

验证了脚本bash语法、help/缺少执行标志拒绝分支、远端首次执行及第二次因已有目录拒绝覆盖。以专用账号只读检查数据/配置可访问，release及配置不可写；没有写探针或读取现有数据库。完整命令结果与边界见 `docs/tasks/handoffs/server-dep2.md`。

## 冻结契约

- Java17，构建产物 `target/vector-search.jar`；发布时可复制为 `/opt/vector-search/releases/<full-sha>/app.jar`，`current` 指向唯一版本。记录源码 SHA、原始 JAR 名及 SHA-256，禁止两个 JVM 同时打开同一 H2。
- 应用默认监听 `127.0.0.1:8080`。本角色本地骨架端口8084，H2目录独立；测试应使用临时绝对目录和随机端口。
- `APP_DATA_DIR` 由基础 application 配置实际插值，建议服务器固定为 `/var/lib/vector-search/db`；最终 URL 为 `jdbc:h2:file:/var/lib/vector-search/db/knowledge;WRITE_DELAY=0`。目录不随 release 切换，URL基名不附加 `.mv.db`，不启用 AUTO_SERVER。
- `GET /health` C0成功为 `{status:"UP",stage:"C0",businessReady:false}`，仅证明基础连接。HTTP200不能证明任务恢复、调度、业务就绪或上线通过；最终部署等待后端实现就绪门禁。
- 目标关闭顺序：停业务接收与后台派发→等待在途任务有界结束→关闭H2→退出。业务等待30s、Spring每阶段45s；C0尚不含完整任务生命周期实现。unit暂留120s外层预算，须按实际阶段数量与关闭演练校准，不能简单认为45s是总时长。超时强杀不得视作干净关闭或直接进入普通文件备份。

## 文件与应用方式

`env/app.env.example` 不含真实凭据。授权部署时由操作者在目标主机安全填写 `/etc/vector-search/app.env`，限制读取权限（例如 root 拥有、0640、仅专用服务组可读），不提交真实文件，也不把内容输出到日志。`DB_PASSWORD`必须替换占位值；数据库密码不代替入口保护。配置中的容量参数在C0仅绑定/校验，业务执行限制尚未实现。

`systemd/vector-search.service.example` 中的 `@SERVICE_USER@`、`@SERVICE_GROUP@`、`@JAVA17@` 必须按真实主机替换。专用非root服务账号只能写数据目录；release由部署账号维护，数据不放Git、target或release。账号、父目录和权限需事先经授权准备，模板不创建它们。JVM堆256MiB—1GiB仅为4GiB主机待测起点，不是RSS上限。日志交给journal，主机需要单独配置容量/保留周期；重启退避不代表业务健康。

只读预检需显式路径，不读取/source配置文件、不输出凭据、不连接网络、不打开H2、不创建目录：

```bash
bash deploy/scripts/preflight.sh --help
# 以下均为待替换示例；应在已授权主机、以服务账号执行。
bash deploy/scripts/preflight.sh \
  --java /ABSOLUTE/JAVA17/bin/java \
  --data-dir /var/lib/vector-search/db \
  --jar /opt/vector-search/releases/FULL_SHA/app.jar \
  --port 8080
```

可选 `--previous-pid PID` 检查指定旧进程是否已退出。端口检查依赖本机 `lsof` 或 `ss`，不会自行安装。脚本访问检查不写探针文件，不能证明配额/挂载空间充足。lsof受账号可见性影响；无监听也不能证明没有尚未监听的JVM占用H2。发布操作者仍需核对服务状态、完整进程身份、解析后的数据路径、磁盘和文件锁；不要通过删除锁文件“修复”。预检通过不能触发自动发布。

## Nginx网络准备（旧域名证书前历史阶段）

已安装Nginx 1.24.0，为 `text.vector.search.lemoncharger.com` 配置独立HTTP站点；仓库配置为 `nginx/vector-search-http-preparation.conf`，服务器位于 `/etc/nginx/sites-available/vector-search-http-preparation`，同名链接置于 `sites-enabled`。仅精确 `/deployment-check` 的GET/HEAD返回 `stage=network-preparation`；其余方法、路径或Host拒绝，不含任何后端代理。当前Nginx active但未enable开机启动，后端unit继续disabled/inactive。

公网指定域名定向服务器的非敏感检查已返回200，直接IP检查404。主机UFW仍inactive，未修改防火墙规则；服务器DNS及1.1.1.1查询A为8.155.137.169，未取得AAAA记录。本机DNS存在代理解析差异，不能据此修改真实DNS；云安全组控制台未查验，仍需用户确认。443未监听，没有生成或启用替代证书。完整记录和回滚见 `docs/tasks/handoffs/server-nginx.md`。

以下为证书、业务和认证到位后的设计目标，当前证书前站点不执行这些代理规则：

- 全部页面与 `/api/` 同源反代到回环地址；HTTPS配合入口认证，不能只保护首页。域名、证书、认证文件均待提供，凭据单独安全交付。
- 443作为HTTPS入口；80仅按证书/重定向需要；SSH限制来源。8080及H2 TCP/Console、非必要管理端点不对外公开；禁止开放任意CORS。
- 请求体最大256KiB并配合限流；限流数值待测，不替代应用CPU并发限制。应用须实际限制chunked请求读取，C0未实现。
- 禁止自动重放提交和搜索等请求；搜索会自增计数。浏览器写入的Origin/CSRF保护由主控/后端后续实现，Basic Auth不能替代。
- `/health`最终是否仅本机可用由主控部署阶段冻结；外部访问也必须受保护，不暴露数据库或主机详情。证书未就绪时使用SSH隧道/VPN完成受控验收。

## 后续发布、验收与回滚门禁

1. 主控执行锁定JDK的 `./mvnw -B clean verify`，确认Surefire/Failsafe实际报告，记录同一SHA/JAR校验和；C0构建不代表业务验收。
2. 获得明确部署授权后，核对主机指纹/账号/目录/端口，上传独立不可变release；首次先验证骨架入口、文件权限和连接。
3. 更新前停止接收/派发，正常停止并确认旧进程退出和数据库关闭，再复制备份。进程超时退出时需单独诊断，不能盲目接续干净备份流程。记录备份时间、来源版本和校验和；不直接复制活动H2文件。
4. 切换current，启动唯一实例；检查非root、重启次数/日志及重复健康探测。最终必须完成提交→任务→检索→计数→失效→后续排除，正常重启持久化，SSH断开存活，以及未认证页面/API被拒绝与端口隔离。
5. 至少一次备份恢复到独立路径/端口验证可读，不能仅验证文件存在。恢复/删除持久数据须另获明确授权，禁止静默覆盖。
6. 回滚前核对旧程序与新schema、H2、算法版本兼容性；兼容才回切程序。不兼容时不能只切current。恢复旧备份会丢弃备份后写入，必须列明影响并由主控确认具体恢复操作。

DEP2已完成基础主机检查和unit静态校验，Nginx已完成固定HTTP/TLS检查与拒绝规则验证；应用启动、真实入口认证、业务代理/冒烟、正常关闭/崩溃恢复、备份恢复均未执行。正式业务入口与发布仍待后续方案和相应授权，不以基础环境准备代替上线验收。
