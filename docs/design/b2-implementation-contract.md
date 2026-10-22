# B2实施与验收边界

2026-10-22；用户已批准启动，Owner主控。基线为本地/远程一致的`1f2d81793433adfb465e111722e633d68a676445`，该HEAD的CI成功；B1代码实测基线、黄金与历史记录继续保留。

## 生产能力与B3边界

B2实现五个文档/任务管理API及调度基础，`POST /api/search`属于B4。公共DTO、现有DDL、运行参数、B1算法与版本均不改。基础健康兼容当前响应且businessReady=false，不代表整套业务就绪。

调度通过窄接口`TaskExecutionHandler`（具体类名由后端先通知QA）交给未来B3。B3处理器Bean不存在时不扫描派发/领取生产任务，提交记录仍可持久化并查询QUEUED。测试可注入受控处理器以验证真实扫描/队列/条件领取/事务外调用；不在生产默认使用空处理器、不假写SUCCEEDED、不计算后遗留无归属RUNNING。终态两表写回、失败回写、遗留RUNNING恢复及完整关闭验收归B3。

若启动发现旧RUNNING而B3恢复尚未提供，拒绝新接收并明确SERVICE_NOT_READY，保留原状态，不能偷偷重置或宣称恢复完成。

## 接收与参数

- 严格按API v1.0处理ID、text、channel、分页、状态、JSON类型；null文本400、空文合法；省略/null渠道提交default、查询全渠道、显式空白400。
- 校验原始及规范化后code point限额，仅做轻量校验，不在提交线程调用B1耗时向量化。拒绝非法UTF16。
- JSON正文按实际读取字节限制256KiB，覆盖无Content-Length/chunked；统一413。只检查表单大小或Content-Length不算完成。
- 写入JSON要求application/json；失效PUT按契约无body。浏览器Origin存在时校验同源，跨源403可用`ORIGIN_NOT_ALLOWED`，命令行无Origin可正常调用；不开放任意CORS。
- 未实现搜索不返回伪造成功；错误遵循现有code/message/fieldErrors结构，隐藏SQL/堆栈。

## 事务与SQL

- 接收锁内完成重复ID检查、文档总量/QUEUED+RUNNING容量检查、两表插入和物理commit。使用TransactionTemplate明确独立物理事务，不依赖锁释放之后的代理提交；对未知外层事务须明确拒绝或隔离并测试。
- 原子保存文档与QUEUED任务，taskId为UUID，记录冻结embeddingVersion；提交成功后才202。重复409/容量429/持久化失败不留孤行。
- MyBatis使用绑定参数；映射Instant、long计数十进制字符串及就绪/失效独立状态；列表按submittedAt降序/ID升序，分页/渠道/失效过滤符合契约。
- 失效仅更新invalidated/invalidated_at，首次时间保留，重复幂等，不整体覆盖旧记录。
- 领取用WHERE status=QUEUED条件更新，成功1行才拥有任务；原子产生新run_token、attempt_count+1及started_at。领取提交确认后才调用处理器。
- 领取提交未知不得假定失败并计算/重领；确认不了时停止新接收并暴露待B3处理的降级，原任务记录保留。

## 队列与扫描

固定Worker+ArrayBlockingQueue+AbortPolicy，Runnable仅带taskId；在途集合只减少重复入队。满队列暂缓派发并清理本地在途标记，数据库QUEUED不丢、不伪失败；周期扫描可补派。

处理器调用在事务外，数据库连接/行锁不跨CPU运算。扫描按稳定提交顺序小批次执行，单次异常不终止未来扫描；本阶段不做在线超时抢占或手工重试旁路。

## 必要证据

真实H2两表回滚；锁覆盖真实commit的受控竞态；最后名额并发接收不超限；重复ID并发只接受一次；列表/详情/任务四态映射；失效幂等及状态独立；真实HTTP空值/类型/长度/chunked错误；停派后补扫、重复领取只有一成功、饱和不丢任务、处理器无事务；旧RUNNING启动不假恢复。所有等待有界，latch/barrier优先，不以sleep证明时序。

按校验/持久化、管理API、扫描领取三个可审查子阶段真实提交，提交前对应unit，阶段集成完整verify。B1黄金回归必须保持；未验证语义效果、B3恢复/终态、B4计数、正式发布仍NOT_RUN。
