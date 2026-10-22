# B2后段独立HTTP与调度验收

执行基线：`a03e3beb371c92ceb8562e8fbe5c97c3324e448a`；分支`agent/qa`；JDK17。
只读核对当前领取、队列、Spring装配及API；不改生产、DDL、公共契约、B1黄金、pom或配置。

## 真实运行记录

| 阶段 | 命令主体 | 实际结果 |
| --- | --- | --- |
| 首次目标验收 | `./mvnw -B -Dit.test=B2HttpIT,B2DispatchIT verify` | 53 unit + 13新IT通过，失败/错误/跳过0，2026-10-22 23:41:11 +08:00 BUILD SUCCESS |
| 增强单连接池后的完整回归 | `./mvnw -B clean verify` | 53 unit + 44 IT，共97通过，失败/错误/跳过0，23:42:01 +08:00 BUILD SUCCESS |

完整IT拆分：C0 6、B1 16、B2接收9、B2 HTTP5、B2调度8。
实际命令使用Temurin17的JAVA_HOME及MAVEN_USER_HOME=/private/tmp/12times-maven-user-home，附`-ntp -s /private/tmp/12times-maven-settings.xml -Dmaven.repo.local=/private/tmp/12times-m2`。这些为本机下载适配，交付仍以Java17及标准clean verify复演。
日志：`/private/tmp/12times-qa-b2-http-dispatch-target.log`及`/private/tmp/12times-qa-b2-http-dispatch-verify.log`。测试均使用临时绝对文件H2目录；HTTP使用经授权的127.0.0.1随机端口。
首次编译和目标验收即通过，无失败后降低断言行为。完整回归前主动将事务外处理器测试连接池缩为1，增强未持有连接证明，该增强亦通过。

## 已验证的13项新IT

| 测试类/数量 | 证据与断言 |
| --- | --- |
| B2HttpIT / 1 | 真POST接收UUID/QUEUED；空原文与原文空白保留；省略/null渠道为default；64个补充字符渠道Unicode去首尾；列表全渠道/指定渠道；重复409不覆盖。生产没有handler，显式5次scan均0，原任务QUEUED、token空、attempt=0 |
| B2HttpIT / 1 | 缺失/null/错误JSON类型、空白渠道、孤立代理、非对象/多根/畸形JSON均400；分页溢出/重复/非法筛选；404/任务UUID校验；跨Origin403、同源可提交、非JSON400、无任意CORS；失效请求有正文400且状态不变 |
| B2HttpIT / 1 | 原生HTTP/1.1报文明示Transfer-Encoding: chunked且无Content-Length；默认262144字节上限-1与精确上限均202，+1与超限畸形JSON均413；有Content-Length超限也413；两表仅保存两条合法请求 |
| B2HttpIT / 1 | 人工夹具四态持久映射；成功独有document详情；超过JS安全整数hitCount是精确字符串；UTC、vectorReady、失效正交；相同提交时间按ID分页；重复失效首次时间不变；列表/详情/任务GET不增加计数 |
| B2HttpIT / 1 | 缩小文本上限为2：两个emoji通过，三个emoji/连字NFKC扩张/原文空白超长拒绝，只保留合法记录 |
| B2DispatchIT / 1 | 两线程barrier竞争同一任务的真实独立事务；只一个Optional有值，UUID token与持久值吻合，attempt=1、startedAt存在，再次领取为空且attempt不加 |
| B2DispatchIT / 1 | Worker处理器当前线程无活跃事务与数据源资源绑定；另连接读取到已提交RUNNING/token；连接池仅1连接，处理器latch暂停时另线程真实接收仍完成 |
| B2DispatchIT / 1 | 1 Worker/1队列，首处理器暂停；第二任务入队，第三任务被拒绝仍QUEUED/attempt0，队列1/in-flight2；释放后再扫第三项可入队，最终三个不同任务各被处理一次，attempt各1，无假终态 |
| B2DispatchIT / 1 | 首次queued查询注入异常，scanOnce返回0且任务QUEUED/门禁仍可接收；下一次manual scan领取成功，处理器调用，attempt=1 |
| B2DispatchIT / 1 | 真commit之后抛事务异常；按本次token确认后只调用一次handler，只发生一次条件领取、一次确认读取；token一致，后续扫描不重做 |
| B2DispatchIT / 3 | 真commit后分别构造确认读取失败、令牌不符、状态不符；handler调用0、条件领取始终1、attempt=1；门禁关闭，后续scan不重领，新提交SERVICE_NOT_READY |

## 解释边界与剩余项

- 测试handler只用于B2执行端口，未执行向量计算和生产终态。RUNNING保持是阶段边界，不是B3完成证据。
- 四态、向量及大计数来自独立SQL夹具，只证明查询/失效字段职责，不证明生产能到达SUCCEEDED/FAILED。
- 扫描错误恢复使用直接构造dispatcher的manual scan两次，验证生产共用扫描路径的续扫能力；没有声称定时线程所有调度故障均已复现。
- 确认反例直接改token/状态仅用于检测错误授权，不表示生产支持在线抢占或自动恢复。
- 本轮失效幂等是连续两次HTTP请求；并发失效、与B3完成交错仍需后续阶段验收。
- 旧RUNNING启动门禁已有前段证据，本轮未重复造恢复结论。
- B3终态两表原子写回/失败回写/旧token迟到、在线恢复及强杀恢复、完整关闭，B4搜索计数，浏览器UI与正式部署均NOT_RUN。
- 合入后主控必须对最终集成SHA复跑；本结果不能替代后续集成版本。
