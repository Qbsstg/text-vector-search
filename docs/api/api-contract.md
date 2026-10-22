# API契约 v1.0（C0冻结）

依据已评审V1.1方案，日期2026-10-22，最终健康说明更新于2026-10-23。Owner：主控。六业务接口字段/路径/规则仍为冻结v1.0。B2已实现五个文档/任务管理接口，B3接入真实生产任务及恢复，B4已实现POST /api/search及最终命中原子计数；`GET /health`按运行事实报告任务与完整业务就绪，具体字段、检查及503规则见下文。

## 通用规则

- JSON请求/响应UTF-8。写入接口要求`application/json`；失效PUT无请求体。浏览器写操作后续需同源Origin/CSRF保护。
- `documentId`字符串，正则`[A-Za-z0-9][A-Za-z0-9._-]{0,127}`；不自动裁剪/转大小写。路径参数URL编码。`taskId`为UUID字符串。
- 文本必填且非null；空字符串/纯空白合法，保留原文。原始与规范化文本分别不超过20000 code points。
- channel省略或JSON null表示未指定：提交使用`default`，搜索/列表不筛渠道。显式空字符串或Unicode纯空白400。非空渠道去除首尾Unicode空白，最长64 code points，大小写敏感精确匹配，不作NFKC。
- `topK`必填JSON整数1—100；拒绝字符串、小数、null。页面默认5不等于服务端缺省值。
- 分页`page=0,size=20`；page≥0、size为1—100；非法/溢出400。列表顺序`submittedAt DESC, documentId ASC`；invalidated省略查全部。
- 时间UTC ISO-8601；可空时间/错误字段稳定输出null。ID均字符串；`hitCount`采用十进制字符串，避免前端BIGINT精度丢失；`totalElements`为整数（文档总量≤5000）。
- 成功响应不套统一data壳。错误统一`{code,message,fieldErrors:[{field,message}]}`；无字段错误时`[]`，不输出假requestId、SQL、堆栈。

## 六接口

| 方法与路径 | 请求 | 成功响应 |
| --- | --- | --- |
| POST /api/documents | `{documentId,text,channel?}` | 202 `{taskId,documentId,status:"QUEUED"}` |
| GET /api/tasks/{taskId} | UUID | 200 TaskView |
| GET /api/documents | page/size/channel/invalidated | 200 `{items:DocumentSummary[],page,size,totalElements}` |
| GET /api/documents/{documentId} | 文档ID | 200 DocumentDetail |
| PUT /api/documents/{documentId}/invalidation | 无body | 200 `{documentId,invalidated:true,invalidatedAt}` |
| POST /api/search | `{text,topK,channel?}` | 200 `{hits:[{documentId,channel,score,snippet}]}` |

DocumentSummary：`documentId,channel,taskId,taskStatus,vectorReady,invalidated,submittedAt,vectorCompletedAt,invalidatedAt,hitCount`。

DocumentDetail：DocumentSummary全部字段（平铺）加`text`；不包含向量。TaskView：`taskId,documentId,status,submittedAt,startedAt,finishedAt,errorCode,errorMessage,document`，`document`仅成功时为DocumentDetail，否则null，满足完成后获取文档信息。状态枚举`QUEUED/RUNNING/SUCCEEDED/FAILED`。

具体Java类型见`src/main/java/dev/twelvetimes/vector/api/contract/ApiModels.java`；修改必须同时更新契约、代码和测试。

## 业务一致性

- 重复ID409，不覆盖、不增建任务；失效200幂等保留首次时间，不提供恢复/覆盖/手工重试。
- vectorReady=任务SUCCEEDED且向量恰好1024字节；与invalidated正交。有效、就绪、同算法版本才可检索；零向量也可就绪。
- 过滤在Top-K之前；完整分数降序，同分按ASCII文档ID升序；零范数相似度0。负分/零分保留，不加阈值；K大于候选数返回全部。
- 空查询正常生成零向量，返回稳定零分Top-K并计数。snippet为原文前200 code points，前端按纯文本展示。
- 候选读取快照语义：失效完成后新开始的检索排除；重叠检索可能返回此前候选。
- 只对最终Top-K以固定ID顺序在同一短事务原子自增。命中不是阅读；搜索非只读。计数提交成功但网络断开仍已计数；提交结果未知不自动重试。

## 错误与未知结果

400 `VALIDATION_ERROR`/`MALFORMED_JSON`，404 `DOCUMENT_NOT_FOUND`/`TASK_NOT_FOUND`，409 `DOCUMENT_EXISTS`，413 `REQUEST_TOO_LARGE`，429 `CAPACITY_EXCEEDED`/`SEARCH_BUSY`，503 `SERVICE_NOT_READY`/`DATABASE_UNAVAILABLE`/`RESULT_UNKNOWN`，500 `INTERNAL_ERROR`。网关可能返回非JSON；前端需兜底。

提交结果未知时可按已知documentId查询，不自动换ID/重提。搜索RESULT_UNKNOWN可能已计数；不能由当前累计值推断本请求是否提交，不能盲重试。DB最终状态无法确认时进入可观测降级并拒绝新接收。

## 最终业务健康与恢复门禁

`GET /health`实现阶段为B4，HTTP200仅当真实businessReady=true，否则503。保留status(UP/DOWN)、stage、taskState、taskReady、businessReady；新增embeddingVersion（字符串或探针无法给出时null）与checks对象：databaseReady/databaseWritable/dispatcherReady/algorithmReady/searchReady/routesReady六布尔。六业务接口v1.0无变化。

业务就绪是当前运行事实的合取：taskState=READY、关闭屏障开放、连接有效及必需表列可读、JDBC/数据库元数据非只读、调度器已运行且有handler/执行器未停、唯一统一算法实例版本匹配冻结配置、搜索服务与六个真实方法/路径映射已装配。taskReady要求上述任务/数据库/调度/算法条件，businessReady进一步要求搜索/六映射。不以硬编码true代替检查，也不把队列满等同于服务损坏。

非READY不借数据库连接；READY探针短读取受共享关闭屏障保护，完成后重新核对状态。探针不调用embed、不新增文档/任务/计数、不执行DDL或修改readiness，响应不暴露SQL/路径/异常原因。只读标志检查不能保证后续磁盘写入永远成功，真实写路径由发布验收验证。

RECOVERING/STOPPING/STOPPED拒绝全部`/api/**`并返回503 SERVICE_NOT_READY；DEGRADED保留GET诊断读取、拒绝业务写入。恢复物理提交后才READY。businessReady表示应用运行就绪，不包含真人UI、HTTPS认证或人工发布批准；这些仍需独立门禁。C0/B3旧健康形状及原验收证据保留，当前测试仅明确迁移健康元数据与状态预期，不伪造旧阶段成功。
