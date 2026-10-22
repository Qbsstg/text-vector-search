# B4 集成验收记录

2026-10-23，北京时间。用户已原则批准B3工程验收并授权B4；本轮只实现文本相似检索、命中计数及FE2/FE3真实接口接入，DEP3仍按独立发布门禁。任务拆解/估算见`../tasks/B4-启动与任务拆解.md`，独立矩阵见`b4-test-plan.md`。

## 基线、证据与冻结范围

B4启动本地/远程main均为`dd96021a5dcc49acb31b4639eb4d1ae9ed6e4054`，对应[CI run39821247938](https://github.com/Qbsstg/text-vector-search/actions/runs/39821247938)完整success，B3的74 unit+74 IT+25 Node=173通过。原报告、历史SHA及真实故障修复不改；本轮另将该最终CI原件补充归档在`evidence/b3-ci-dd96021/`，逐字节复制、带SHA256，不倒填为B4结果。

| 产物 | 真实提交 |
| --- | --- |
| B3批准登记、B4任务/契约 | `3f5764e43674ef5e8c3e5288f3967f5da4ad5e5c` |
| QA独立12组设计 | `c9902c955d104d17f9916169e030a65e3297d66f` |
| QA独立搜索/并发/真实网络与资源验收 | `4ba76e5878b2fb9e585f526d2b412f98ec93e291` |
| QA计数物理提交与真实关闭补证 | `9703c7fb4264b75b9e5740666db0ed571b63cc1f` |
| 严格输入、有界候选快照 | `c106e18cbe453d72e4eef48ef40e80ffaf0f48ab` |
| 搜索编排、最终命中原子计数及故障unit | `59f3c76cb2d5a231ee57191b81bffe3e9f7acb8d` |
| FE2/FE3真实接口、状态快照与38项Node | `8a1ea67b1dab9ba21028c7d5a19c96c52a5dd81b` |
| 后端完整回归交接 | `20659ab0727e34d5352602559fb59f0fee6a6a8e` |
| PM启动任务/估算/人工项 | `03cf199cf9a3cae9c6cc3582c59f61131cc0da5f` |

冻结的B1文件、算法版本、codec、余弦、Top-K、DTO、DDL、依赖、application资源参数及B3状态机均不改。版本仍`char12-sha256-r32-nfkc-root-ws-v1`。全部新增维护注释与Commit说明使用简体中文，不amend/rebase/force push；用户未跟踪的`docs/测试工作/`保留原样，不纳入已执行数量。

## 实现与关键不变量

POST `/api/search`按API v1.0返回`{hits:[{documentId,channel,score,snippet}]}`。text非null且可空；topK必须JSON整数1—100，字符串、小数/null/溢出拒绝；搜索渠道省略/null为全部，显式空白400。复用现有实际字节限制、单根JSON与Origin校验，无新增协议或外部依赖。

SearchCandidateReader在独立短只读事务中读取候选快照：任务SUCCEEDED、1024字节向量、未失效、文档/任务双版本匹配、可选渠道精确过滤。容量上限按冻结5000检查，多读一条仅检测非法越限，一旦越限返回错误，不交付截断Top-K。候选仅包含ID/渠道/向量/SQL有界原文前缀，不加载全量CLOB；字节数组防御复制。

SearchService复用冻结B1生成查询向量，在事务/连接/关闭许可之外遍历全部候选并调用冻结余弦与Top-K。无阈值，零/负分保留、同分ASCII ID升序、K>N全部返回；snippet按原文前200 code points，emoji不被截断。坏向量明确失败且不计数，不伪装无结果。

默认搜索许可1，tryAcquire忙时429 SEARCH_BUSY，不排无界队列。许可覆盖CPU、候选读取、评分直到计数真实物理提交，finally释放。搜索入口拒绝未知活动外层事务，避免REQUIRES_NEW返回后CPU仍持外层事务/连接。候选短读取和计数短事务接入B3共享屏障，CPU不持屏障；停止后迟到搜索不再进入SQL。

SearchHitCountService只复制最终Top-K的唯一ID，按ASCII固定顺序在同一个独立短事务执行`hit_count=hit_count+1`，每行必须更新1行。返回结果仍保持分数顺序；计数只改hit_count，不覆盖失效/原文/向量/时间。多行任一失败、0行或BIGINT溢出整体回滚；空命中不制造写入。

采用不自动重试计数的保守策略。明确回滚返回DATABASE_UNAVAILABLE；无法确认commit结果返回RESULT_UNKNOWN并DEGRADED，不查累计值猜测、不重放增量。成功提交后客户端断开仍已计数；前端任何网络/超时/RESULT_UNKNOWN均不自动第二次POST，明确再次搜索属于新请求。

候选快照后才失效的在途搜索可以返回并计数，不能计数时再过滤导致已返回项漏计；失效已提交后新开始的搜索排除该文档。详情/列表/任务GET不增加命中。

## 测试分层与执行门禁

本机统一Temurin17：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 clean verify
node --test tests/frontend/*.test.mjs
```

| 分层 | 实际基线与结果 |
| --- | --- |
| 后端提交前unit | c106e18前Validator6/Reader4共10通过；59f3c76前相关26通过，集合重叠不相加 |
| 后端阶段完整回归 | 源码59f3c76：95 unit+74 IT+25 Node通过，失败/错误/跳过0，01:38:01完成；尚不含FE新增13和QA B4专项 |
| 主控首轮集成 | 源码`820d1ffd6a435faf25715a8ed15142a35239c65c`：95 unit+74 IT，01:38:55完成；38 Node通过，合计207，失败/错误/跳过0 |
| QA独立专项 | 首21 IT与完整95 unit+95 IT+38 Node=228通过；随后追加计数关闭排空1 IT，目标95 unit+11 IT通过。两阶段真实结果分别保留，不将旧228改写为新全量 |
| 主控最终源码/完整回归 | **`b55473e74e81c67ed1b721f81d4045fc71f50fba`**，Java17 clean verify于01:48:02完成：**95 unit+96 IT+38 Node=229通过**，失败/错误/跳过0。B4新增22 IT；全部报告、11个B3进程回归和12份B4事件原件见`evidence/b4-local-b55473e/` |
| B4 Linux CI | 待推送私有main后核对真实headSha/报告/Node及B4事件，不用旧B3绿色替代 |

## 最终本地验收矩阵

| 编号 | 实测证据 | 结论 |
| --- | --- | --- |
| Q01 | B4HttpIT/Validator：严格类型、Unicode、渠道、真实chunked/Origin和错误结构 | PASS |
| Q02 | 真实VectorSearchApplication/B1提交→成功→HTTP检索/详情，前端真实fetch闭环另层 | PASS |
| Q03 | 成功/向量/失效/双版本/渠道过滤，最高分但不合资格排除 | PASS |
| Q04 | 独立全排序、固定随机样本、零/负/同分/K>N、emoji200码点 | PASS |
| Q05 | 仅最终K自增、返回分数序/SQL固定ID序、GET不计数/字段不覆盖 | PASS |
| Q06 | 默认许可1：10次尝试中2次提交/8次429，持久增量2；仅测试许可8的8×8实际搜索，最终a/b各+64、未入选0 | PASS，生产许可仍1 |
| Q07 | 候选读取后失效，旧快照仍返回/+1且失效不覆盖；新搜索排除 | PASS |
| Q08 | 四种多行失败/0行/真实BIGINT溢出，全部回滚无部分增量 | PASS |
| Q09 | 真commit后异常与实际rollback但调用方未知的两种反例，SQL只一次、无累计确认、DEGRADED/RESULT_UNKNOWN | PASS |
| Q10 | 真实Socket SO_LINGER=0 RST，commit在先响应在后，入口1/SQL1/持久增量1；前端失败只一次POST | PASS，浏览器层仍待验 |
| Q11 | 单连接池CPU无事务/连接；有界前缀/防御复制；关闭后迟到SQL0；真实close等待已入计数commit后才关Hikari | PASS |
| Q12 | Java17/Node/JAR本地通过，Linux CI待核；完整浏览器未执行 | 分层记录，不宣称浏览器通过 |

QA临时较高许可与单连接池只用于明确隔离的验收，未改生产配置。实际事件包括ATOMIC/UNKNOWN/ROLLBACK/RST/BOUNDED/STOPPED/COUNT_DRAIN，不把受控数学向量当真实语义效果。所有参与者有界等待，无降低断言或假终态。

## 前端真实HTTP与浏览器边界

FE2/FE3接入真实同源POST，Top-K默认5，零负分与显示小数同分均不本地重排；snippet/原文通过textContent展示。单个在途搜索防重复，不因编辑、任务完成、失效或详情刷新自动搜索；保留查询条件与历史快照标记。hitCount从真实GET取得十进制字符串，不本地推算。

主控真实JAR来自源码820d1ff，专用临时目录`/private/tmp/12times-b4-live-cl4yi4i4/db`、随机回环端口49490、pid45035。前端createApi包装真实fetch完成两种文档202→真实SUCCEEDED→搜索：普通分数0.6367942239095025、空文0；hitCount字符串0→1，重复详情/列表GET不变；空查询两项均0；普通文档失效后新搜索仅空文，普通最终仍SUCCEEDED+invalidated=true+hitCount="2"。首页、app.js、search.mjs均200，模块text/javascript。真实请求及JSON见`../tasks/handoffs/frontend-b4-http.md`（2b93ec4），不能当作浏览器通过。

本地联调首次启动被自动审批拒绝（没有自动停止期限、临时H2路径未充分明确），未实际启动；改为显式APP_DATA_DIR环境变量、回环随机端口、180秒自动停止及finally回收后获准。前端首次默认沙箱fetch失败，升级本机网络权限后同服务完整通过；保留两次实际记录。联调完成仅对本次pid45035发送SIGTERM，实际退出143；数据/日志保留，未触远程服务器或项目默认数据库。

真实浏览器仍NOT_RUN：本轮cua.getState实际browsers为空，提示Work设置禁用了本地浏览器；用户误选稍后后询问开启方式，主控已提供OpenAI官方Settings→Browser/版本相关Work入口，尚未确认实际启用。B4通过后优先完整浏览器；不通过其他自动化路径绕过禁用，不以Node/HTTP替代视觉、键盘、dialog焦点、真实DOM/XSS或完整交互验收。

## 资源与未验证范围

生产参数仍worker1、queue64、maxPending1000、maxDocuments5000、searchPermits1、maxTopK100、Hikari4；不因本地测试快而提高并发。受控资源测试证明CPU阶段不占数据库连接/长事务、忙时拒绝、关闭后零SQL；不等于测得真实业务吞吐、p95、RSS或高负载容量。相关性只模拟字面特征，不宣称语义准确率。

SSL已装且B3检查通过，不重复索取。B4本轮不登录/变更服务器，不启用正式业务代理、认证、systemd或Nginx开机启动；DEP3另需完整业务/浏览器门禁、发布SHA与回滚、安全入口凭据和用户具体授权。云安全组、浏览器启用、认证及启动策略属于人工后续项，本地B4编码不因此阻塞。

个人目标仍为2026-10-23 18:00；启动估算是并行5—8小时、浏览器另1—2小时的工程判断，实际提交/测试时间以原件为准，不虚构开发时间线。
