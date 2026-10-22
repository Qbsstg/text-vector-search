# 子agent注册表

2026-10-23最终阶段；用户原则通过B4核心，当前仅HEALTH-FINAL/UI-FINAL/DEP3-PREP。PM干净分支由ab990bc快进`16356e03aca5046523a16f740c16fb4c7608b288`；五角色/模型配置不变，不表示全部同时运行。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前交付/依赖 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B1—B4核心DONE；5377542最终健康聚合只读实现已交，9目标unit；配合主控Controller合计12目标unit，最终独立/全量待核 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | FE代码/38 Node/HTTP通过；frontend-final-ui已交29项Chrome脚本和证据模板，全部NOT_RUN，Work仍disabled |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | B4独立22 IT保留；下一最终健康/历史组成迁移与Linux锁/冷备/新目录恢复，实际结果待交 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP3-PREP只读预检/本地方案已交；无release/current/DB，htpasswd缺失，未安装/建认证/上传/启动；待固定版本和具体操作批准 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | PM-FINAL四份任务文档；八项交付、候选占位、门禁与批准分组，交接后释放槽位 |

主控持Controller/公共契约、CI、固定候选、根文件/DDL/依赖及合并；后端只内部健康/调度只读状态，QA acceptance/docs/testing，前端static/明确分配交接，服务器deploy，PM docs/tasks。本次PM不改三个角色交接、公共API/CI/Java或历史验收。

已有B4核心229项与ab990bc/run39827330708 success保留；最终健康代码5377542/3cea5eb目标12 unit通过不等于最终全量。候选源码SHA、CI、JAR/manifest/schema hash待主控生成并独立复核，不填写未知值，不拿纯文档HEAD替代候选。

用户确认本人此前公开并明确授权，仓库当前PUBLIC/API private=false，后续保持公开。824历史blob只按指定凭据格式/禁扩展名审计，不作完备秘密保证；不改旧私有/公开历史，密钥/env/运行库不入库。

最多主控+三个子agent、2—3写入任务，各自worktree/H2/端口，不派生、不操作notes/重写历史或清理他人文件。QA随机端口/绝对临时库；PM不启动服务、不运行测试/SSH/CI或读取ZIP/key。

SSL已装、TLS检查DONE；正式业务proxy/认证、systemd启动与Nginx开机策略仍需固定版本/具体操作独立批准。只读预检不构成写操作授权，冷备恢复只到全新独立目录，首发失败回TLS检查而非虚构旧版/恢复生产数据。最终依赖见[准备与审批清单](最终交付准备与审批清单.md)。
