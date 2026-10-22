# FINAL-RELEASE 测试总报告

2026-10-23部署前核证快照，最终结论为**存在未解除的门禁，尚未完成正式交付**。用户已批准固定候选、F05有限例外及D1—D9实际部署；普通操作授权充分，当前阻塞不是再次申请部署许可。

## 版本与真实回归

| 层次 | 实际版本/结果 | 原始证据 |
| --- | --- | --- |
| 批准生产源码 | d016d6e8bb431ff17ba81f8977d31a1b96f51f7f | 原CI39838054946、批准manifest |
| 批准JAR | SHA-256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`，27309072字节 | 原artifact及本地实包逐字节校验 |
| 原253项本轮重跑 | 104 unit＋110 IT＋39 Node，零失败/错误/跳过 | baseline日志、XML、Node原件 |
| 新增自动化 | 14 IT＋4 Node；原12项中间轮另留，不能重复累计 | LocalCasesIT、前端新增测试及中间/最终报告 |
| 最终本地完整回归 | 104 unit＋124 IT＋43 Node＝271，零失败/错误/跳过 | final-reports-14new、Node43日志 |
| 最新Linux CI | [39862014960](https://github.com/Qbsstg/text-vector-search/actions/runs/39862014960)，测试源码5897cfd4764b4a5b32d973f6722249d26a04b800；104/124/43＝271全通过 | 已下载XML、完整CI日志、controller-review.json |
| 固定包一致性 | 生产树与d016的src/main、pom、Wrapper无差异；CI下载原批准JAR，六参校验后实际启动、冷备与新目录恢复 | CI原manifest、final-linux-evidence；未使用新构建包替代批准JAR |

命令为Java17 `./mvnw -B -ntp clean verify`、`node --test tests/frontend/*.test.mjs`。总数由Surefire/Failsafe XML及Node TAP独立解析，不从BUILD SUCCESS推算。Linux隔离恢复的systemctl为inactive shim，不能证明正式systemd已部署。手动性能main、夹具子样本、70主用例和重复运行均不加入271。

## 本地新增70条完整追踪

检查主树、未跟踪材料及角色工作树，共5792个测试相关候选文件：415个源码/计划、5377个生成报告/证据。未发现标准目录外额外可执行入口。用户02 Markdown和04 JSON为同一70编号，保留去重对应，不计140；01问题说明和05执行模板不另造用例数。10个原始资料文件及.DS_Store元数据共11项SHA复核未变，原件继续未跟踪，不原样推公开仓库。

最新矩阵为**64 PASS、1 FAIL、5 NOT_RUN**，逐条来源/断言/执行方法见[用例矩阵](final-release-local-cases-matrix.md)，独立依据见[QA实证核证](final-release-evidence-review.md)。

| 原编号 | 当前结果及后续要求 |
| --- | --- |
| REC-07 | FAIL / 契约冲突待用户裁决。真实R32成功库正常关闭后由R33打开，health200/ready、搜索200空hits。没有混算/迁移/重算，符合冻结B4双版本过滤；原预期却要求版本不兼容阻止就绪。原断言与失败不改、不私自例外、不改核心迎合测试 |
| UI-08 | NOT_RUN：正式公网HTTPS真实Chrome闭环及重启后复查，不能用本地页面代替 |
| OPS-02 | NOT_RUN：正式数据目录、唯一JVM、正常停止/重启和业务持久化 |
| OPS-03 | NOT_RUN：实机停止后冷备、全新目录恢复、完整字段/向量字节摘要比对 |
| OPS-04 | NOT_RUN：正式HTTPS整站认证、Origin、回环端口及管理入口拒绝 |
| OPS-06 | NOT_RUN：实际上传/运行固定版本、发布与首发失败回退现场记录 |

REC-07已向用户提交两种选择：保持冻结B4并明确修订本条验收判据，或保持原判据而暂停发布另审版本策略；尚未收到选择，不能自行解除。原文件和旧失败永远保留；若批准修订，也须记录新判据及真实核证，不能把原FAIL抹掉。

## 浏览器、HTTP与性能分层

原UI-FINAL为28 PASS，F05仍**NOT_RUN / APPROVED_EXCEPTION**，用户已批准，不为验收制造真实FAILED。F03的真实trusted visibilitychange及U12分层证据保留，不重复执行旧28项。

本轮新增本地Chrome差异FR-B01—03实测恶意渠道/错误文本安全显示、零请求路径拒绝、键盘恢复、UTC到北京时间和焦点。另一次发包前受控429观察125.930秒，前端意图1、真实POST0，输入/按钮恢复、不重发；它不冒充后端容量满。真实后端429在独立Linux实验实测。原始DOM/网络/截图及QA结论见[浏览器新增核证](final-release-browser-additions.md)。

固定JAR在Linux独立库实做PERF01—03，单独Mac客户端经SSH转发访问回环8085，不是公网业务：1300次提交中1088接收全部成功、212次429；5000篇检索九组N/K、混合与热点/分散全部逐条记录，unknown0/writeRetry0，所有5000计数增量独立核账。第三独立100长文轮补Java队列getter采样峰32、inFlight33、末0；没有达到容量64，不嫁接首轮峰。各轮CPU/RSS/GC覆盖全生命周期，客户端时长另列，采样存在成本，结果不构成SLA。三轮均已正常停止、数据保留，第一轮143/failed分类及后续模板修订原样保留。

PERF04按原允许的无标签模式记录8组真实分数；未做人工相关性标签、准确率/Recall/NDCG或未定义的性能门槛。有限实验PASS不能宣传无限负载或语义能力。

## 证据、公开与正式门禁

完整原始证据及逐成员SHA保留在本地隔离分支`codex/final-release-local-evidence`，原归档提交为`3a142e78b32d73811e50e1177fee60cf5627276d`；不公开原始请求载荷、截图或资源明细。公开仅提供[部署前核证摘要与原件摘要](evidence/final-release-predeploy-d016d6e/来源说明.md)，不把摘要冒充原件。公开推送原件曾被自动审批拒绝，原因是具体材料潜在敏感性未由有限格式扫描充分排除；已选择更小公开范围。压缩前后字节核对、QA逐成员核证及原失败均保留本地，不force push。有限模式无命中不代表任意秘密已完备排除。

正式服务器D1配置受限备份已完成，D2固定包已于06:17:35无覆盖预置至批准SHA目录，JAR/manifest/SUM本地和远端一致，目录0550/文件0440、服务账号只读；首次tar AppleDouble拒绝与三文件直传修正均保留。SSL及80/443沿用，8080未开放且无监听；current仍无，service inactive/disabled/PID0/NRestarts0，认证/env/unit/Nginx未变。此处上传不是正式业务发布。

ColdSnapshot已完成本地真实H2封存副本及占用拒绝验证，主控与最新Linux CI实际按Java17编译；正式D8尚未运行。三轮实验均已停止，自建18085隧道已关闭且监听不存在。待REC-07明确裁决并完成门禁后执行已获批D3—D9，真实核证认证/六接口/计数/失效、生命周期、冷备恢复和公网Chrome，届时追加C/D/E/F实测结论；本快照不代表正式上线PASS。
