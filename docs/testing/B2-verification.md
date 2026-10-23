# B2主控集成验收

> **历史阶段说明**：本文件记录B2 接收、持久任务与调度的历史阶段验收。正文、原始 FAIL、测试数量和时间线原样保留，不代表项目当前仍未交付。最终结论请查阅[《最终交付聚合结果》](最终交付聚合结果.md)。

2026-10-22，个人交付目标仍为2026-10-23 18:00（北京时间），不因此放宽事务、安全或测试门禁。

## 代码与历史

本轮启动时本地/远程main均为`1f2d81793433adfb465e111722e633d68a676445`，对应CI run39797284687已完整success。用户已原则通过B1。原28个历史提交SHA仍全部是当前祖先；未force push、rebase或amend。B1六个算法文件与原验收HEAD相比，去除注释后的词元完全一致，版本仍为`char12-sha256-r32-nfkc-root-ws-v1`。

原B1 CI下载产物17文件已逐字节留存`evidence/b1-ci-de57693/`，并附SHA256SUMS；原临时日志/下载未删除。历史英文提交完整正文与中文释义见`docs/git/提交历史中文说明.md`。

| 子阶段 | 原始真实提交 | 提交前验证 |
| --- | --- | --- |
| 校验/接收/容量 | `1bfa33f4258366015ea621ea895ae790de49ca59` | 11个对应unit通过 |
| 五个管理API/HTTP | `f834fa4bfa8954306568da32ffc7ac80e7d47636` | 15个对应unit通过，含前段 |
| 队列/扫描/条件领取 | `4523942f44009d71b243ca31693f5a7ef48b0fdc` | 24个对应B2 unit通过，含前段 |
| 独立接收验收 | `9242a38e70deab78baa2ffb8871edcc39256dc63` | 新增9 IT；当时完整40 unit+31 IT通过 |
| 独立HTTP/调度验收 | `450e4c290d0f705dd229ddc124998e4672da4ecd` | 新增13 IT；完整53 unit+44 IT通过 |
| FE1 | `304e5ede3ea641b61e3b1b02bd5a97550e815568` | 25个Node测试及静态检查通过 |

数字反映各阶段当时的套件，不把多次运行累计为不同用例。所有新代码/合并提交说明中文，注释整改独立于B2新增文件。

## 最终主控回归

实际集成代码SHA：`53769c69477cf43351280338a0938117ca6f0252`。23:44:05 +08:00结束，Java17完整`clean verify`退出0：**53 unit、44 IT，失败/错误/跳过均0**。同一集成树Node22.16.0执行`node --test tests/frontend/*.test.mjs`，**25通过，失败/取消/跳过均0**。合计122项。后续仅文档/状态记录提交不改变运行源码、构建或CI；若再改代码须重新测试。

实际Java命令：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
-Dmaven.repo.local=/private/tmp/12times-m2 clean verify
```

临时settings/cache仅为本机受限依赖下载适配，交付仍使用JDK17下`./mvnw -B clean verify`。报告在target；本机最终日志`/private/tmp/12times-b2-final-integrated-verify.log`和`/private/tmp/12times-fe1-final-node.log`保留。

## 已证明的范围

- 轻量校验不调用CPU向量化；严格JSON类型、原文/规范化code point、UTF16、渠道、ID、分页、统一错误及Origin。
- 单实例接收锁覆盖真正commit。独立QA先用ThreadMXBean确认第二线程确实阻塞于首线程的锁，再核对事件顺序；两表真实回滚、并发重复ID、两类容量、提交未知精确UUID确认与拒接均验证。
- 五个管理API保留原文、UTC/字符串大计数，列表过滤排序、四态映射、失效首次时间及字段独立。GET不增加hitCount。人工终态夹具只证明映射，不证明B3执行。
- 原生HTTP/1.1无Content-Length的chunked正文验证256KiB的上限前/恰好/超限，超限413且不落库；同源、非法Origin和媒体类型校验有真实HTTP证据。
- 数据库条件领取唯一成功，token/attemptCount匹配；确认提交后才调用处理器。单连接池下处理器暂停仍可完成其他写入，验证不跨处理占事务/连接。
- 1 Worker+1队列受控饱和，被拒绝任务仍QUEUED且可补扫；扫描故障后续可再扫；提交未知按本次token确认，不能确认/状态或token不符时绝不处理或重领。
- 生产缺少B3 handler时不派发/领取，保持真实QUEUED；发现旧RUNNING时拒绝新接收且保留原状态，不伪装恢复。

## FE1与本地联调

前端已实现真实管理请求、详情纯文本、确认失效、有限轮询及错误反馈；搜索按钮仍禁用。25项Node测试覆盖请求不自动重试、Java空白边界、字符串大整数、迟到响应、最大3任务/2秒间隔/60秒预算/隐藏暂停/终态停止。

主控实际启动JAR于127.0.0.1:18080，数据仅在`/private/tmp/12times-b2-browser/db`。通过HTTP完成202提交、QUEUED查询、详情/列表、重复409、失效幂等、任务独立、读取计数0；`.mjs`均以text/javascript返回。证据`evidence/b2-http-check.json`。验证后SIGTERM停止，日志确认Web优雅停止及Hikari关闭。

**真实浏览器未执行**：调用本地浏览器工具返回“Local browser use is disabled in Work settings”。未绕过该限制，未生成假截图；视觉、键盘、真实DOM交互/XSS执行、浏览器请求竞争仍需启用后再验收。HTTP和Node通过不替代UI验收。

## 网络准备与用户人工项

Nginx交接见`docs/tasks/handoffs/server-nginx.md`。主控独立SSH及公网验证：80监听、443/8080无监听；指定域名GET/HEAD `/deployment-check`固定文本200，POST405，其他API/H2/管理路径及未知Host404，无业务代理。Nginx active但开机启动disabled；后端unit仍inactive/disabled。

域名A经服务器/1.1.1.1核对8.155.137.169，无AAAA答案；本机代理DNS差异未被当作修改依据。UFW inactive及iptables ACCEPT保持原样。公网HTTP成功不是云安全组审查结论。

用户需：在云控制台核对本实例80/443入站及来源，保留SSH规则、不开放8080/H2；以安全文件方式提供匹配域名的证书链与私钥，告知受限主机路径（不贴聊天、不进Git）；后续确定入口认证与DEP3正式发布。若需真实UI验收，启用Work本地浏览器访问。证书和UI设置不阻塞本轮B2代码验证。

## 未完成与风险

B3终态两表写回、失败回写、旧token迟到完成、旧RUNNING/强杀恢复、完整关闭；B4搜索/命中并发；语义相关性与性能；真实UI；HTTPS/认证/业务发布/备份恢复均未验证。简单小批扫描可能不能立即填满较大内存队列，吞吐待测，未用未经测量的优化改动B2。用户`docs/测试工作/`保持未跟踪原样，其中70条并未整体执行。

本轮B2核心主控门禁通过，等待用户阶段审阅；不自动启动B3或正式发布。

## 远程核证

本地HEAD与私有仓库远程main已独立核对为`38eeb26156db997f590066e51f72bb30c8ef88f6`，运行源码/构建/CI与主控实测代码53769c6完全一致。该提交的[CI run39808131629](https://github.com/Qbsstg/text-vector-search/actions/runs/39808131629)于2026-10-22 23:48:43 +08:00完整completed/success，Java、Node测试和报告上传全部成功。

主控实际下载`test-reports-38eeb26156db997f590066e51f72bb30c8ef88f6`并解析XML：53 unit+44 IT，失败/错误/跳过0；实际CI日志确认Node tests25/pass25/fail0/skipped0。产物留在`/private/tmp/12times-b2-ci-artifacts`。本节为随后独立文档提交，不能把文件所在SHA倒填为已执行的38eeb26；最新文档HEAD在最终交付与远端再核对。
