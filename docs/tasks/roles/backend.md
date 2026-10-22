# 后端工程师

主控最终状态：B4本地及Linux CI 95unit+96IT+38Node=229通过，核心实现/独立验收已交付；完整浏览器仍NOT_RUN，下一优先实际启用后的UI复演，DEP3须独立发布授权。以下B4派发段保留任务范围，不要求重新实施已完成工作。

2026-10-23 B4派发：当前任务B4-01/02：立即实现真实POST /api/search、候选快照/资源许可、冻结B1评分与Top-K、最终命中短事务原子计数。先读`../../design/b4-implementation-contract.md`；可修改新SearchController、RequestValidator最小扩展、搜索业务包/专用Mapper及unit。不得修改冻结B1类/DDL/DTO/参数或B3状态机。提交前真实unit，分段中文commit，未知提交不重复计数。

模型`gpt-6.1-sol / high`，`/root/backend_engineer`；使用`.worktrees/backend`、`agent/backend`。先读AGENTS、API/运行/B3契约，保留B1版本和B2最终证据。

用户已批准立即B3，Owner统一持有状态机/事务。范围为task/**、persistence/**、分配的相关配置/unit；公共DTO/DDL/依赖/基础配置/健康契约变更提主控。00c5af5/90bcdf4/bb6e5eb三段已合入，终态9/恢复等33/生命周期等26个对应unit分别有交接，数量有重叠不相加；QA终态16 IT和主控阶段161项回归通过，完整B3仍未最终验收。

生产handler真实调用冻结B1；RUNNING+run_token确认后向量/时间/成功同短事务，旧token成功/失败均不能覆盖新尝试。失败分类脱敏持久化；明确回滚有限重试，未知提交先独立核对，不假FAILED或盲写。

先RECOVERING拒接/停派→恢复旧RUNNING并确认commit→READY/扫描。关闭先拒接/停派、有界等待，再关闭新写许可并等已进入短事务退出；超时CPU不能在数据源关闭后回写，不无差别中断H2。已有unit与主控正常JAR/SIGTERM冒烟；独立恢复/四强杀/完整关闭后半仍待验。

恢复未知提交确认的跨表时间误比较已由13953cf改为核对任务自身时间并合入。QA真实SIGTERM另复现H2[90121-240]先关库，DB_CLOSE_ON_EXIT=FALSE A/B同断言成功、exit143；主控fe32b5b已修生产URL/运行契约，74 unit过，容量/算法/等待预算不变。最终恢复/进程/全集成仍待核证。

B2无handler/旧RUNNING原断言保留在明确隔离组成，另测真实B3，不能全局禁handler或假终态。B4搜索/计数与DEP3发布不自动实现；代理Origin先真实配置实测再归因，不信任任意外来转发头。

Java17、中文关键Javadoc/注释、新Commit中文；提交前对应unit、阶段完整verify。端口8081、独立H2，交付基线/文件/真实命令数量结果/未执行/风险/SHA；不派生agent、部署或清理持久数据。

## 主控最终本地核验（2026-10-23 01:09）

后端与QA交付已合入，源码`28cd42d7603c862830b51c38bb98e1de10e6427d`执行Java17全量**74 unit+74 IT+25 Node=173通过**，失败/错误/跳过0。恢复门禁、四检查点真实SIGKILL、正常SIGTERM及CPU超时后零SQL均通过，11子进程实际退出；两处发现均有复现/修复证据。Linux CI run39820606756在main 5cf634e同为173通过且原件已归档；角色本轮完成待命，详见[集成验收](../../testing/B3-verification.md)。上文为此前阶段快照，不能覆盖本条已验证结果。B4、真实浏览器与正式业务发布仍未执行。
