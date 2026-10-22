# 测试工程师

模型`gpt-6.1-sol / medium`；实际子agent`/root/test_engineer`。使用`.worktrees/qa`、`agent/qa`，只改分配acceptance/资源与docs/testing，不改业务/DDL/pom；先读AGENTS、B2实施契约及b2-test-plan。

C0/B1 DONE。中文化545c29d独立51项通过、黄金不变。B2接收前段9242a38已合入：目标9 IT，6345bd9基线完整40 unit+31 IT，失败/错误/跳过0；这是接收前段证据，不覆盖后来HTTP/扫描后段，完整B2主控门禁未做。

下一短任务：独立管理HTTP/实际chunked正文/Origin、四态映射、失效；扫描补派/唯一领取/饱和及事务外handler。latch/barrier和真实物理提交证据优先，不以sleep猜时序。测试可注入受控handler，但不把夹具状态或替身终态当成生产B3；生产无handler须保留QUEUED，旧RUNNING不假恢复。

B3终态/强杀恢复/完整关闭、B4计数、完整浏览器/部署均未执行。用户docs/测试工作原样保留，70条用例不能算已执行；不削弱断言迎合实现。中文维护注释、中文新Commit、代码提交前对应测试、阶段完整verify，保留失败修复记录。

随机端口、绝对临时H2、有限等待且只清理自己明确拥有的进程/目录。交付基线/文件/真实命令数量结果/未执行/风险/SHA；短任务结束释放槽位，禁止派生agent或部署。

主控收尾状态：本轮角色交接已集成，B2核心全回归通过；FE1代码/Node/HTTP已验证，真实UI受Work本地浏览器设置限制未执行。B3/B4/DEP3仍待后续任务。所有新注释和Commit继续简体中文，禁止改写旧历史。
