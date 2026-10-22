# B3启用后的历史验收组成迁移

基线52c92de27f888871cc073b85d3ae6e79e61949cb，主控已批准阶段迁移。
C0BootstrapIT、B2AdmissionIT、B2QaEnvironment及ProxyOriginIT仅将Spring启动源切换至后端提供的dev.twelvetimes.b2.B2CompositionTestApplication。该明确测试组成不装配B3 handler/恢复/filter，保留真实B2控制器/Mapper/事务；不是全局关闭生产handler。B1生产装配用例仍使用VectorSearchApplication，新B3生产测试同样必须走真实生产组成。

C0健康仍请求真实控制器：200、UP、businessReady=false断言保留；阶段元数据按真实控制器升级C0→B3，新增taskState=READY/taskReady=true断言。原文件库哨兵两表、QUEUED、原文及大计数断言全部保留，未改成期待SUCCEEDED掩盖执行变化。B2旧RUNNING启动拒接/无handler不领取/容量等原断言保留。

真实回归：2026-10-23 00:48:22 +08:00，Temurin17完整clean verify，70 unit + 63 IT（包括新B3TerminalIT 16）共133通过，失败/错误/跳过0。日志/private/tmp/12times-qa-b3-terminal-verify.log；临时Maven路径沿用本机设置，不作为交付要求。
仅测试组成与健康元数据迁移，无业务/B1/DDL/依赖改动；生产B3恢复/关闭/强杀另验，不据此宣称完成。
