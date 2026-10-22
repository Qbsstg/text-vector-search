# 运行与资源契约 v1.0

C0/B1参数基线保持不变；B2已实现队列、提交容量和HTTP字节限制，B3接入真实任务、终态、恢复和关闭。搜索许可及Top-K请求约束的完整业务执行仍待B4。参数存在不等于对应业务性能已经验收。

| 配置 | 初值 | 责任/含义 |
| --- | --- | --- |
| vector.worker-count | 1 | 后台CPU Worker |
| vector.queue-capacity | 64 | 执行器ArrayBlockingQueue，不另套无界队列 |
| vector.max-pending-tasks | 1000 | QUEUED+RUNNING，接收锁内检查 |
| vector.max-documents | 5000 | 包括失败/失效，失效不释放名额 |
| vector.search-permits | 1 | 覆盖查询计算、读取、评分、计数提交 |
| vector.max-text-code-points | 20000 | 原始与规范化文本各自检查 |
| vector.max-request-bytes | 262144 | JSON实际读取字节数，需覆盖chunked；不是表单参数 |
| vector.max-top-k / max-page-size | 100 / 100 | 请求参数约束 |
| vector.cpu-rounds | 32 | 待测真实确定性摘要计算，改变时纳入版本 |
| vector.scan-interval-ms / scan-batch-size | 500 / 32 | 单调度器扫描小批次 |
| vector.shutdown-wait-seconds | 30 | 业务停止等待初值 |
| spring.lifecycle.timeout-per-shutdown-phase | 45s | 框架停止阶段等待；systemd另留余量 |
| datasource hikari maximum-pool-size / connection-timeout | 4 / 5000ms | 连接数与获取超时 |

环境变量按Spring relaxed binding，如`VECTOR_WORKER_COUNT`。`APP_DATA_DIR`由application.yml显式插值，H2 URL为`jdbc:h2:file:${APP_DATA_DIR:./data}/knowledge;WRITE_DELAY=0`。`PORT`默认8080，监听127.0.0.1；`DB_PASSWORD`通过环境设置，默认仅适合本地开发。服务端不得使用默认空密码作为公网部署已验收证据。

本地主控8080、后端8081、前端8083、部署骨架8084；每个worktree的`data/`互相独立。集成测试随机端口、绝对临时H2目录。部署数据目录固定绝对路径，例如`/var/lib/vector-search/db`，release切换不改变该目录；单实例，不使用AUTO_SERVER。

JAR产物`target/vector-search.jar`；服务器发布目录可重命名为`releases/<full-sha>/app.jar`。B3健康`/health`区分taskState/taskReady与businessReady=false；任务READY不代表包含B4的全部业务就绪。公开入口仅经HTTPS/保护代理，不能直接开放8080或H2服务/Console。

B1算法已实现：NFKC→Locale.ROOT小写→Unicode空白折叠/首尾去除；按code point生成U:/B:特征，bigram不跨空白，标点作为普通非空白code point；同权重带符号SHA-256映射，固定逐特征摘要轮数，整数桶、double范数、固定下标归一化输出float。字节序BIG_ENDIAN。默认版本`char12-sha256-r32-nfkc-root-ws-v1`，精确规则与黄金验证见`b1-algorithm-contract.md`和B1验收记录。

用户指定服务器`8.155.137.169:22`已完成DEP2基础准备。实测Ubuntu24.04.5LTS、4CPU、7526MiB内存、40G根盘，已有Temurin17.0.15+6。项目数据目录/受限env/独立unit已创建，unit disabled/inactive且无应用release。未因4核8G自动提高并发；新域名`text-vector-search.lemoncharger.com`的TLS固定检查已启用，业务代理/入口认证仅未启用模板，正式业务发布仍需用户确认。仅未来受控Nginx业务代理启用时加载proxy配置，应用仍绑定回环，详见proxy-deployment-contract.md。

B3终态仅对明确回滚的暂时DB错误最多3次，退避50/100ms；提交未知先独立事务查询，最多3次仍无法确认则DEGRADED、拒接/停派，保留RUNNING现场。成功确认匹配token、版本、状态、时间和完整向量字节，不能只比长度。搜索计数提交未知不可重试自增，具体实现待B4。没有在线超时抢占、无限重试或手工重试旁路。

启动恢复在单例初始化后、调度生命周期前执行：旧RUNNING恢复QUEUED并清理旧token/执行时间/错误，保留尝试次数和文档元数据；确认物理提交后才READY。只允许旧进程退出且H2文件锁释放后重启，不启用AUTO_SERVER。关闭先STOPPING拒接、停止派发，有界等待Worker，再关闭新写许可并排空已进入的短事务，最后STOPPED并销毁数据源。CPU不持写许可，超时计算不能迟到回写；30秒是Worker等待预算，永久卡住的数据库事务排空不承诺固定时限。

