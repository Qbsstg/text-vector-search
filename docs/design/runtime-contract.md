# 运行与资源契约 v1.0

C0基线；参数是待测起点。配置已绑定并启动校验，业务资源限制需后续Owner实现，不能把配置存在当成限制已生效。

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

JAR产物`target/vector-search.jar`；服务器发布目录可重命名为`releases/<full-sha>/app.jar`。C0健康`/health`明确businessReady=false；后续恢复门禁完成后才升级为业务就绪。公开入口仅经HTTPS/保护代理，不能直接开放8080或H2服务/Console。

算法待B1实现并冻结精确版本：NFKC→Locale.ROOT小写→Unicode空白折叠/首尾去除；按code point生成U:/B:特征，bigram不跨空白，标点作为普通非空白code point；同权重带符号SHA-256映射，固定逐特征摘要轮数，整数桶、double范数、固定下标归一化输出float。字节序BIG_ENDIAN。黄金样例与版本说明通过后再允许业务入库。

失败处理初案：仅对明确回滚且确认可重试的DB错误最多3次，退避50/100ms；提交未知先独立连接查询，最多3次仍无法确认则降级并返回诚实错误。搜索计数提交未知不可重试自增。没有在线超时抢占、无限重试或手工重试旁路。此初案需B2/B3实际错误分类测试后确认，主控保留变更权。

