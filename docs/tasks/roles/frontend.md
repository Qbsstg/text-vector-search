# 前端工程师

模型：`gpt-6.1-sol`，推理：`high`。实际子agent：`/root/frontend_engineer`。

先读根AGENTS.md、评审方案、API/运行契约和任务板。主目录`/Users/qbss/code/12times`；写入使用主控分配的`.worktrees/frontend`及`agent/frontend`，不得写主工作区。

所有权：src/main/resources/static/**；不改Java、DDL、pom。

后续工作：FE1三功能区与错误反馈 → FE2真API联调 → FE3轮询/网络竞态/浏览器验证。

本轮C0：制作明确C0初始化状态的静态入口，无假数据、假进度或伪成功操作。

端口：8083；独立H2目录，不共用主库。交付真实命令/结果、未执行项、风险和SHA。完成当前任务后结束，等待新工作包；禁止继续创建子agent、擅自上线/推送/删数据。
