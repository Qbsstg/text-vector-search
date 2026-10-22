# 子agent注册表

五角色已实际启动并完成首轮只读分析。模型为用户指定gpt-6.1-sol；极高映射xhigh。C0基线提交后创建独立工作树，实际SHA与结果另记录。角色卡可复用，但不会在应用重启后自动启动子agent。

| 角色 | 实际子agent | 推理 | 分支 / 工作树 | 当前状态 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | high | agent/backend / .worktrees/backend | 首轮只读完成，待C0写入 |
| 前端工程师 | /root/frontend_engineer | high | agent/frontend / .worktrees/frontend | 首轮只读完成，待C0写入 |
| 服务器工程师 | /root/server_engineer | medium | agent/server / .worktrees/server | 首轮只读完成，待C0写入 |
| 测试工程师 | /root/test_engineer | medium | agent/qa / .worktrees/qa | 首轮只读完成，待C0写入 |
| 项目管理 | /root/project_manager | xhigh | agent/pm / .worktrees/pm | 首轮只读完成，待C0写入 |

最多主控+三个子agent并发，最多2—3个写入任务。主控唯一集成，PM追踪不替代公共文件Owner。
