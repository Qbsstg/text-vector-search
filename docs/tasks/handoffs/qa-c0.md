# QA-C0 交接

- 任务：C0-QA；分支`agent/qa`；基线`0f692387577be629d296d3a5fe0ba626072f93b4`。
- 修改：`src/test/java/dev/twelvetimes/vector/acceptance/C0BootstrapIT.java`、`docs/testing/c0-test-matrix.md`、本交接文档。
- 契约/依赖/业务/DDL/pom：均未修改，无新增依赖建议。
- 执行：JDK17，Maven Wrapper `clean verify`，附本机临时settings/缓存适配；真实完整命令已向主控回报，不作为交付运行前提。
- 最终结果：2026-10-22 21:17:20 +08:00 BUILD SUCCESS；Surefire 2通过，Failsafe 6通过；失败/错误/跳过均0。
- 首次受限执行：HTTP监听被沙箱拒绝；批准本机监听后完整重跑通过。
- 重点审查：生产应用真实关闭后同库重启；非法配置确实通过生产扫描/@Validated失败；DTO使用生产Jackson，非自建替代mapper。
- 未执行：业务API、算法、竞态、未知提交、强杀恢复、浏览器、部署、负载及新机器复演，详见测试矩阵。
- 风险：该SHA没有业务就绪；正常应用重启不能推断崩溃恢复，配置存在不能推断业务资源限制有效。
- 提交SHA：由提交本身确定；主控以本次交付消息中的SHA为准，合入后须复跑。
