# 最终健康、UI与DEP3准备契约

2026-10-23；用户已原则批准B4后端核心，停止扩大六业务接口范围。基线`ab990bca9e5b70a8a677a67c167bc610faa326b8`，[CI run39827330708](https://github.com/Qbsstg/text-vector-search/actions/runs/39827330708)success；原B1—B4证据、算法、codec、Top-K、DDL、参数与全部真实历史保留。

用户确认此前公开由本人操作，并明确授权仓库改为公开。正式业务部署仍需要固定版本与操作清单的单独确认；当前不授权破坏性数据操作。

## HEALTH-FINAL

`/health`升级实现阶段为B4；业务就绪由运行事实推导，HTTP200仅在businessReady=true，否则503。保留status/stage/taskState/taskReady/businessReady，新增embeddingVersion及checks布尔对象。不输出SQL、堆栈、路径、密码或用户文本。

后端提供内部`health.BusinessReadiness`接口、`Snapshot`记录及默认Service；主控持有Controller/公共契约。Snapshot字段：TaskReadiness.State taskState，boolean taskReady/databaseReady/databaseWritable/dispatcherReady/algorithmReady/searchReady/routesReady，String embeddingVersion，businessReady()求完整合取。接口方法`inspect()`。

- 初始/恢复/降级/停止均非业务就绪；非READY不得借数据库连接。READY短数据库检查需接共享关闭屏障，退出后再核对最新状态，关闭不能越过探针短读取销毁数据源。
- 数据库：连接有效，documents/embedding_tasks必需字段可读；JDBC连接/元数据未报告只读。全部只读检查，不做提交、计数、测试文档或DDL。只读标志不保证未来磁盘写入一定成功，真实写路径由验收门禁证明。
- 调度器：正在运行、有生产处理器、扫描/Worker未关闭；后端仅增加只读运行状态方法，不改任务状态机/恢复/关闭顺序。
- 算法：统一TextEmbedder已装配且版本与当前冻结配置一致；不在健康请求或启动探针中调用业务向量化（避免额外CPU/业务副作用）。B1真实数学黄金及六接口闭环测试另验。
- 搜索组件和六业务方法/路径实际装配；missing handler/dispatcher停止/缺少映射不能报业务READY。队列满不自动当服务损坏，继续按既有429规则。
- taskReady要求READY、屏障开放、数据库可读且非只读、执行调度/算法实例就绪；businessReady还要求搜索和六接口映射就绪。健康失败不自行改写TaskReadiness，不把一次探针当成数据恢复或自动修复。
- 实际UI、认证、HTTPS、CI、人工发布批准不作为硬编码运行旗标；它们属于独立发布门禁，businessReady不表示允许公开发布。

历史B2隔离组成缺少生产handler，因此新的完整业务健康应为503；QA只迁移健康元数据/状态断言，保留基础数据库/文件重启与所有业务断言，说明差异，不创建假旧健康响应。B3原验收文件/原件不回改。

## UI-FINAL

先实际检查Work browser；若仍禁用，交付Chrome人工逐步脚本、数据与清理边界、预期、截图/Network证据模板，所有结果默认NOT_RUN。覆盖提交、四态/有限轮询、列表/详情/字符串计数、搜索零负同分/渠道、失效历史、键盘/窄屏/dialog、XSS纯文本、请求竞争、断网未知不自动重试。

只使用专用测试H2和合成ID；人工清理限专用验收库且另有明确确认，不给删除生产数据命令。提供固定候选JAR和有界启动方法，用户可Chrome访问127.0.0.1。Node/HTTP/脚本模板不能算真人浏览器PASS。

2026-10-23补测收尾：F03/U12已按真实用户切页与原deadline核证；F05继续NOT_RUN。允许用户对披露范围明确批准验收例外，状态仍NOT_RUN，不由主控默认接受；该例外与固定版本/服务器操作的正式DEP3批准相互独立。

## DEP3-PREP

服务器角色只读预检，允许修改本地deploy及指定交接文件，禁止本轮上传/启动业务JAR、改实际env、创建认证凭据、启用业务Nginx/systemd/开机策略、恢复/删除数据库。SSL现有有效材料沿用，不重索。

准备不可变`releases/<full-sha>/app.jar`与SHA256/manifest，固定Java17、proxy profile、127.0.0.1:8080、单JVM/H2文件。专用账号/受限env、Nginx全入口认证/可信转发头/禁止重放、单实例锁与H2原生锁、冷备份校验、停止/首次发布失败与旧版本回切方案明确。无旧release时回退到TLS检查，不能虚构旧版本。

备份须确认旧进程退出且H2无占用后复制到新独立受限目录，保留源数据并记录校验和。恢复只允许到全新独立目录验证；生产数据替换/清理或会丢弃备份后写入的恢复必须另获具体授权。代码回滚不默认回滚数据。可在本地临时库/CI做备份恢复复演，不触服务器真实数据。

CI在完整Java17和Node通过后保存实际构建JAR、source SHA、SHA256与manifest；主控下载复核，选择一个不可变候选供用户批准，后续纯文档HEAD不冒充候选源码。候选改变必须重新审查hash/CI。

上线顺序与门禁：版本/操作授权→只读预检及保留当前配置→确认停机/冷备份→不可变JAR/校验→单实例与env→回环健康→受保护HTTPS代理→未认证401、同源写允许/跨源403、六业务接口、计数/失效与重启持久化→重复健康/进程/日志→明确启动策略。正式服务器入口项本轮只准备验收方法，未执行不能提前PASS。

## 角色与交付

后端负责健康Service及unit和调度只读方法；主控负责Controller、公共文档/CI/固定候选；前端先交Chrome手工脚本和证据模板（独占显式分配handoff）；QA随后独立健康/备份恢复/契约回归；服务器负责只读预检和deploy方案/脚本；PM轮换维护八项交付/依赖。最多三子agent，不派生，分支/worktree隔离，中文注释/Commit，真实测试和SHA。

目标2026-10-23 18:00；关键路径是健康实现/独立回归→不可变CI候选→人工浏览器与明确部署授权→真实服务器受保护入口/重启验收。时间不足不跳过事务、安全或真人UI门禁。
