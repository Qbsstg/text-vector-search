# QA-B4 独立验收结果与交接

实测生产代码基线：`820d1ffd6a435faf25715a8ed15142a35239c65c`加本轮当时未提交的B4验收源码；分支`agent/qa`，Temurin17。不能将这些新测试归入仅基线SHA本身的结果。最终提交及主控集成SHA分别核证。
范围仅新增acceptance与本说明；B1算法/期望/版本、B3实现与历史故障原件、DDL、DTO、pom及生产资源参数均未改。

## 真实运行

| 执行 | 实际结果 |
| --- | --- |
| 首批`verify -Dit.test=B4SearchIT,B4CountAndResourceIT` | 15新IT首次通过，2026-10-23 01:39:12 +08:00 |
| `verify -Dit.test=B4HttpIT` | 5新IT首次通过，01:41:12 +08:00 |
| 增加有界前缀/防御复制与事件记录后的全部目标 | 95 unit + 21新IT通过，01:42:28 +08:00 |
| 最终`./mvnw -B clean verify` | **95 unit + 95 IT = 190通过**，失败/错误/跳过0，01:43:27 +08:00 BUILD SUCCESS；保留B3真实进程全部回归 |
| `node --test tests/frontend/*.test.mjs` | **38通过**，失败/取消/跳过0 |

本轮总计**228项实际通过**。无编译/测试失败、无为迎合实现调整错误预期，未发现所测场景的阻塞缺陷。
实际Java命令使用JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home、MAVEN_USER_HOME=/private/tmp/12times-maven-user-home，另附`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`；这些仅本机下载适配，交付仍为Java17标准clean verify。
原始命令结果分别在`/private/tmp/12times-qa-b4-target-phase1.log`、`12times-qa-b4-http-target.log`、`12times-qa-b4-target-all.log`、`12times-qa-b4-final-verify.log`及`12times-qa-b4-node.log`。JUnit报告在target/surefire-reports与failsafe-reports，合成事件在target/b4-evidence，由主控配置CI产物保留，不提交运行数据库/凭据。

## 独立用例与契约映射

| 契约 | 本次实际证据 | 结论 |
| --- | --- | --- |
| Q01 API | B4HttpIT真实JSON类型/缺失/null/小数1.0/越界/溢出/UTF16/显式空白/原文与NFKC扩张/渠道64补充字符；Origin同源200/跨源403；原生无Content-Length chunked 262143/262144字节200、262145字节413 | 通过这些边界，统一错误形状；坏请求不落计数 |
| Q02 正常闭环 | 真VectorSearchApplication，HTTP提交正常/空文、等待真实B1成功、HTTP搜索及详情/列表/任务GET；正常相同文本余弦1.0、只选中项+1 | 通过，未用人工向量冒充正常路径 |
| Q03 过滤 | B4SearchIT人工H2资格夹具覆盖QUEUED/RUNNING/FAILED、成功无向量、失效、任务/文档版本分别旧、渠道A/a/B；最高分非法资格项先过滤，K=1仍选中合法项 | 通过；过滤夹具采用明确B2组成防自动任务争抢，真实搜索SQL/服务未换 |
| Q04 分数排序 | 真实B1查询配正向/反向/零数学向量；零负分/同分ASCII ID/K>N/空查询；113候选、独立全排序取100；200码点emoji边界snippet | 通过；参考不用生产TopK比较器，样例不代表语义准确率 |
| Q05 最终计数 | 所有候选前后对账，仅Top-K增加；重复显式查询逐次+1；GET不计数；实际更新a→b固定顺序而结果维持分数顺序 | 通过；原文、失效、时间及向量不因计数整行覆盖 |
| Q06 并发 | 真HTTP默认许可1：首请求CPU暂停，8请求barrier起跑均429，首放行及显式再搜后attempted10/committed2/rejected8/hit2；另在真实commit前停首请求，下一请求429。测试构造许可8，8线程×8轮真实搜索/计数 | 通过；高许可层confirmedCommitted64，最终a/b各64、未选中0；生产配置仍1 |
| Q07 失效快照 | 真实候选load返回并退出事务后latch，独立失效提交再释放；此前快照命中仍返回并+1，首次失效时间保持；新搜索不返回失效项 | 通过候选快照语义，不声称响应送达瞬间有效 |
| Q08 回滚 | 计数第二项前异常、写后异常、受影响0行、真实H2 BIGINT溢出四参数场景；独立读a/b前后值 | 通过整笔回滚，SQL顺序a,b且无重放；溢出b保持Long.MAX_VALUE、a保持0 |
| Q09 未知提交 | 真实commit后抛事务异常与实际rollback后抛未知异常两反例；Mapper只准incrementHit，记录调用1 | 通过；均503 RESULT_UNKNOWN/DEGRADED，不查询累计猜提交、不重放；实际增量分别1/0，后续接收SERVICE_NOT_READY |
| Q10 网络 | 真HTTP完整POST后，实际commit已返回、响应前latch；客户端SO_LINGER=0关闭发送RST，放行服务端；等待HTTP链完成再核对 | 通过服务端入口1/计数SQL1/持久增量1，客户端未接收成功响应；Node网络失败回归已过；真实浏览器尚未执行 |
| Q11 资源/停止 | 单连接池：真实B1计算暂停和load返回后的评分前暂停均无活跃TX/资源绑定，其他短写可完成；原文2000 UTF16单元的候选前缀有界、防御复制；测试缩小maxDocs=1但实际2文档明确失败；坏向量503且计数0；活动外层TX三入口拒绝。CPU暂停→真实context关闭/DS关闭→再放CPU | 通过所测边界；晚到搜索SERVICE_NOT_READY、共享barrier已关、Mapper调用0且持久计数0。既有B3短TX排空/真实SIGTERM/强杀回归保持通过，不扩张为任意挂死SQL保证 |
| Q12 集成 | 本地Java190+Node38通过，B3历史断言和进程测试保留 | 本地通过；本轮Linux CI/真实JAR最终冒烟/真实浏览器由主控后续核证，当前这些项NOT_RUN |

## 21个新IT与组成

- B4SearchIT：6项，包含结果/过滤/113候选参考、快照失效竞态、坏数据/超容量、有界前缀与防御复制。
- B4CountAndResourceIT：10项（回滚4、未知2、64次并发、物理commit许可、CPU关闭零SQL、外层TX拒绝）。
- B4HttpIT：5项，包含真实闭环、API/chunked、码点边界、真实HTTP过载、真实RST。
- B4QaEnvironment：生产组成用于正常HTTP/B1/过载/网络；数学与资格夹具在明确B2组成中，直接执行真实B4 reader/service/counter。所有应用使用独立临时绝对APP_DATA_DIR、生产默认H2安全关闭URL，HTTP随机回环端口。
- 测试HTTP钩子必须`qa.b4-http-hook=true`才装配，避免B2显式扫描误载；只在src/test，未添加生产故障端点或参数。其余竞态直接构造公开服务并装饰真实接口/TX。
- 既有B3/代理测试源码、原始错误90121-240及强杀历史证据完全未改。最终B3全部子进程均自行退出，无服务器操作。

## 事件与原始范围

最终target/b4-evidence记录运行标识、实际源码SHA、线程与合成场景：BUSY/RST、4种ROLLBACK、2种UNKNOWN、ATOMIC64、STOPPED与BOUNDED。RST顺序是SQL→真实COMMIT返回→客户端RST→HTTP链返回→独立持久对账，不以一个普通异常冒充网络断连。未知提交的实际提交事实由测试装饰器知晓，只用于验收对账，生产代码未据累计hitCount进行确认。

当前树提交前全部测试完成，无Maven/测试应用后台工作。本交付供主控合入后对最终SHA复跑，不能把本机工作树结果直接冒充最终发布/CI版本。真实浏览器、性能/语义准确率与DEP3正式业务发布仍NOT_RUN。

## Q11追加独立排空证据（后续小提交）

主控复核指出，前述21IT尚未直接覆盖“B4计数已进物理commit时真实context.close等待该事务”。新增`B4CountAndResourceIT.真实关闭等待B4计数物理提交后才销毁数据源`，保留原默认许可释放测试和B3文件不变。
实测基线`4ba76e5878b2fb9e585f526d2b412f98ec93e291`加该项当时未提交测试：计数commit前latch→另一线程真实context.close→共享barrier关闭/STOPPING但close尚未返回、Hikari未关、独立连接计数0→放行真实commit→搜索返回1命中→close完成/STOPPED/Hikari已关→新独立连接持久计数1。
目标`verify -Dit.test=B4CountAndResourceIT`于2026-10-23 01:46:20 +08:00通过95 unit+11 IT，失败/错误/跳过0；新增目标首次通过，日志`/private/tmp/12times-qa-b4-count-drain-target.log`。真实事件`target/b4-evidence/COUNT_DRAIN-*.txt`记录COUNT_BEFORE_COMMIT→COUNT_COMMITTED→CONTEXT_CLOSE_RETURNED及前后持久值。
当前B4新增用例为22项；上述228是前一次完整回归的历史结果，不回写成已执行的新全量数量。此小提交只做对应目标验证，主控负责合入后的完整main回归；本次未重新运行Node或浏览器。
