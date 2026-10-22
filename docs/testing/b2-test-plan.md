# B2独立验收设计

设计基线：`f3850bbecb0ed373a109ff5b80b807c462f12da4`；角色：QA；分支：`agent/qa`。
依据：最新AGENTS、冻结API v1.0、`docs/design/b2-implementation-contract.md`，并只读参考主工作区`docs/测试工作`中的TASK/CON/API用例。旧资料中待定契约以当前冻结契约为准；未修改或提交该资料目录。

本阶段只交设计与既有验收注释中文化，不生成缺少B2实现的测试。以下全部B2用例状态为 **NOT_RUN**。B1算法、输入、黄金、版本和历史SHA保持冻结。

## 验收矩阵

| 编号 | 对应旧用例 | 受控步骤 | 通过定义与证据 |
| --- | --- | --- | --- |
| B2-Q01 | TASK-03 | 真文件H2，接收两次插入间注入异常，无测试外层事务；等回滚后独立连接读取 | 文档与任务均0行；响应非202；异常不泄露SQL |
| B2-Q02 | CON-09 | 上限1，A已插入但实际Connection.commit前latch暂停；barrier释放B竞争；记录锁进入/commit/返回事件 | B不能在A物理提交前越过接收锁；A提交后至多接受1个，B容量429；两表无孤立行 |
| B2-Q03 | CON-09 | 同ID并发与不同ID最后名额分别测试；Worker暂停保持未完成数 | 同ID仅一个202，其余409；不同ID不超名额；响应及持久行数对账 |
| B2-Q04 | TASK-06 | 分别将未完成容量与总文档容量设为极小值；构造失效/失败但仍占总文档名额的数据 | QUEUED+RUNNING与总量分别生效；失效不释放总名额；拒绝请求不留行 |
| B2-Q05 | TASK-04 | 不注册处理器，提交后显式调用多个扫描周期 | 持久QUEUED、token空、attemptCount=0；没有默认空处理器偷领；查询可用，health仍业务未就绪 |
| B2-Q06 | TASK-07、CON-01 | 两独立线程/事务barrier争抢同一QUEUED，直接走领取组件避免仅测内存去重 | 条件UPDATE成功行数总计1；持久RUNNING、新UUID token、attemptCount加1、startedAt存在；仅确认提交获胜者进入处理器 |
| B2-Q07 | CON-10 | 测试处理器进入后latch阻塞，独立连接检查领取已提交；同时执行其他短写入 | 处理器线程无活跃Spring事务；连接不跨处理器保留；别的短事务能完成；不伪造SUCCEEDED |
| B2-Q08 | TASK-05 | 1 Worker/1队列，处理器阻塞第一个任务；填满队列并触发额外拒绝；释放后手动触发补扫 | 饱和项仍QUEUED、无假FAILED；拒绝释放本地in-flight，后续能重新派发；不在请求/扫描线程执行CPU处理 |
| B2-Q09 | TASK-04 | 暂停派发，接收后独立连接确认两表提交，再恢复扫描 | 只依赖持久扫描即可补派，主动唤醒丢失不丢任务；无处理器场景不领取 |
| B2-Q10 | 管理API | 真HTTP提交/详情/任务/分页；数据库夹具覆盖四态；各状态与失效正交 | 原文保留、字符串hitCount、UTC/null字段、排序和筛选正确；详情/列表/任务GET不增hitCount；不因夹具终态声称生产终态实现 |
| B2-Q11 | CON-03的B2子集 | 并发两次失效并复查任务/向量/计数；重复请求 | 首次时间保留，仅更新失效字段；任务原状、原文、向量与计数不覆盖；完成/失效交错归B3 |
| B2-Q12 | API边界 | 真实HTTP覆盖空/缺失/null文本、JSON错误类型、非法UTF16、原文与规范化扩张、渠道空白/64补充字符/大小写、ID与分页溢出 | 按冻结规则400/404/409及统一错误结构；空文本合法；提交省略/null渠道default、列表省略渠道全部；不更改B1算法来通过 |
| B2-Q13 | API正文限制 | 原生HTTP/1.1客户端发送无Content-Length的chunked JSON：小正文、上限、上限+1；另测有Content-Length超限 | 按实际读取字节统一413，未落库；不能只检查头部；超限畸形JSON也不能转为假成功 |
| B2-Q14 | Origin/类型 | 无Origin命令行、同源Origin、跨源Origin和不同Content-Type；失效PUT无body | 无Origin与同源按契约工作，跨源403；无任意CORS；JSON类型错误按明确协议返回 |
| B2-Q15 | REC的B2边界 | 专用文件库预置旧RUNNING后完整启动，B3处理器缺席 | 拒绝新接收SERVICE_NOT_READY；旧RUNNING/token/attempt不重置；不宣称恢复完成 |
| B2-Q16 | 未知领取提交 | 包装真实连接：实际commit后抛SQLException；独立读取确认持久token；另测确认读取失败 | 未确认前不计算/重领；不能确认则停止新接收，保留原记录。最终降级/完整恢复效果归B3，不将普通回滚异常冒充提交未知 |

## 给后端的窄接口需求

1. `TaskExecutionHandler`及实际领取结果类型：应传taskId、documentId、runToken与attempt信息，或提供可查询的窄上下文；测试可注册受控Bean，生产缺失Bean时绝不派发。
2. 可显式执行一次扫描的方法，以及可停用自动扫描的装配方式；不需要公网调试端点。不以等待固定毫秒数猜测已扫过。
3. 可直接调用的条件领取组件与领取确认结果，使测试绕过本地in-flight重复优化竞争真实SQL；返回0行必须可辨识。
4. 接收服务可被直接调用，明确外层事务是拒绝还是隔离；用生产事务管理器和真实H2验证。测试包装DataSource/Connection可拦截指定线程的commit以及两次插入间错误，不要求为QA新增生产故障开关。
5. 饱和测试可采用受控处理器和手动扫描；若需观察在途集合，只提议只读状态或包内测试适配，不暴露线上管理接口。

接口名字/Bean装配方式待后端回报后映射到实际类，不修改公共DTO/pom/配置。若无法无侵入包装事务边界，先给主控提议，不通过复制生产实现制造“通过”。

## 执行与证据规则

- 主控提供含B2实现的新基线后才编写/编译B2 acceptance。先目标验收，再Java17完整`clean verify`；依赖变化由主控统一处理。
- 每个用例临时绝对文件H2目录；HTTP随机127.0.0.1端口；独立连接读取已提交状态。禁止测试方法外层事务掩盖物理提交。
- latch/barrier等待、Future与进程均有超时，finally释放并关上下文。可用事件顺序证明commit边界，不靠sleep；测试不无差别中断正在操作H2的线程。
- 报告记录基线、命令、实际测试数、事件序列、响应、受影响行数、前后表状态；accepted/rejected/confirmedCommitted/confirmedRolledBack/outcomeUnknown分别计数。
- B3终态原子写回、旧token迟到完成、失败写回、在线降级、强杀恢复、完整停止；B4检索/计数；UI与正式发布均 **NOT_RUN**。

## 已确认的后端接口映射

后端于设计阶段确认：`DocumentAdmissionService.submit`、`DocumentQueryService.detail/list/invalidate`、`TaskQueryService.get`、`TaskReadiness`、`TaskClaimService.claim(taskId) -> Optional<TaskExecution>`、`TaskDispatcher.scanOnce()`、`TaskExecutionHandler.handle(TaskExecution)`；执行上下文携taskId/documentId/runToken/embeddingVersion/attemptCount/text/channel。测试可装饰真实PlatformTransactionManager或Mapper控制故障/提交点，不增加HTTP测试开关。自动扫描关闭/直接构造装配方式待含实现基线核对。这是接口对接记录，不是B2执行结果。
