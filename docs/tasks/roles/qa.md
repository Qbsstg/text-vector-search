# 测试工程师

模型`gpt-6.1-sol / medium`，`/root/test_engineer`；使用`.worktrees/qa`、`agent/qa`，只改分配acceptance/测试资源及docs/testing。先读AGENTS、B3/代理契约、b3-test-plan及B2最终证据。

B1/B2 DONE，B2原53 unit+44 IT+25 Node保留。ecf7cb5的15组设计保留，2734b7e/a2ddb6a已合入：代理3 IT、终态16 IT通过；原B2组成/断言保留。后半6项真实进程测试按修复后真实默认URL重跑，四强杀/恢复/SIGTERM/超时结果待交，不能提前计通过。

主控实测默认HTTP同源202、默认HTTPS头组合403；仅回环可信proxy配置同HTTPS202/跨源403。独立IT已记录有效三元组及Tomcat组件对非信任来源的反例；127.0.0.2真实socket未完成，组件反例不等于外部网络验证。仍不修改过滤器或开启公网真实API。

终态前段已实测两表原子/旧token/未知提交等16 IT，组件写许可不替代完整生命周期。当前后半继续验证真实B3：终态两表回滚、旧token拒写、失效交错、未知commit、恢复先于接收、SIGTERM与超时写许可、四点SIGKILL。用真实B1、真实commit/进程事件、latch/barrier和有限等待；普通异常/正常重启不冒充强杀，SIGKILL不承诺断电安全。

13953cf已修任务自身时间确认。正常SIGTERM真实复现H2默认JVM hook先关库的90121-240；仅加DB_CLOSE_ON_EXIT=FALSE A/B同断言成功、exit143。fe32b5b已修生产默认URL且74 unit过，后半测试改为直接读取真实生产URL重跑，不能以自造测试URL代替；最终结果待交。

B2历史无handler/旧RUNNING断言保留在隔离组成，真实B3生产另测，禁止全局禁handler、假终态或降断言。随机端口/绝对临时H2，只清理明确拥有的测试资源。中文注释/新Commit、提交前对应测试、阶段完整verify；用户70条资料不计为已执行，不派生agent或部署。
