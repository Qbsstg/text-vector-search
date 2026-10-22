# 测试工程师

模型`gpt-6.1-sol / medium`，`/root/test_engineer`；使用`.worktrees/qa`、`agent/qa`，只改分配acceptance/测试资源及docs/testing。先读AGENTS、B3/代理契约、b3-test-plan及B2最终证据。

B1/B2 DONE，B2原53 unit+44 IT+25 Node保留。ecf7cb5已交15组B3设计，含Q12—15四个真实强杀检查点；仅计划完成，执行均NOT_RUN。当前短任务ProxyOriginIT独立观察有效scheme/host/port、可信/不可信转发头，不改生产过滤器或借测试开启公网API。

主控实测默认HTTP同源202、默认HTTPS头组合403；仅回环可信proxy配置同HTTPS202/跨源403。它证明配置组合不匹配和初步对照，不代表所有信任边界已验，更不能未经实测归因代码缺陷。

收到实现及审查后的组成基线再执行真实B3：终态两表回滚、旧token拒写、失效交错、未知commit、恢复先于接收、SIGTERM与超时写许可、四点SIGKILL。用真实B1、真实commit/进程事件、latch/barrier和有限等待；普通异常/正常重启不冒充强杀，SIGKILL不承诺断电安全。

B2历史无handler/旧RUNNING断言保留在隔离组成，真实B3生产另测，禁止全局禁handler、假终态或降断言。随机端口/绝对临时H2，只清理明确拥有的测试资源。中文注释/新Commit、提交前对应测试、阶段完整verify；用户70条资料不计为已执行，不派生agent或部署。
