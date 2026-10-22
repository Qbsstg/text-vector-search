# 子agent注册表

本轮：B2、FE1、独立QA、Nginx及中文化；C0/B1既有DONE保留。五角色均为当前会话内部子agent，模型/推理沿用用户配置，不表示全部同时运行。PM本轮实际核对五个worktree，写入基线`2b6330816ec908b25e232f23441f1d7291a826c6`；角色各自基线以交接为准。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前任务 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B1 DONE；B2三段1bfa33f/f834fa4/4523942已合入；调度24个对应unit通过，角色回归及最终门禁待核 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | C0 DONE；FE1运行中，25项Node测试通过待提交；B3/B4缺口不伪报成功 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2/NGINX-PREP DONE；b9e79bc及主控独立复核；DEP3未发布 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | B1 DONE；9242a38接收前段已合入，后段等待槽位与实现；不代主控关闭B2 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | PM-B2：任务板/注册/五角色卡/报告/交接，仅文档，完成后结束释放槽位 |

主控独占pom/Wrapper、基础配置、DDL、公共DTO/契约、根README/AGENTS、CI及合并。后端负责业务/Mapper/unit；前端仅static及分配测试；服务器deploy、CI提案；QA acceptance与docs/testing；PM docs/tasks。公共变更提主控，不越权代改。

B2依据实施契约：无B3处理器时生产不派发/领取，记录QUEUED真实可查；测试受控handler不代表终态/恢复完成。B3/B4/DEP3继续另行派发。编码和注释使用简体中文、代码提交前跑对应测试；存量注释独立提交，新Commit说明中文，旧SHA不重写。

最多主控+三个子agent、2—3个写入任务；禁止继续派生子agent。各自worktree写入，不checkout/reset或清理他人目录，不操作notes；worktree不隔离CPU/端口/环境。主控8080、后端8081、前端8083、本地部署8084，QA随机端口与绝对临时H2，PM不启动服务；数据库不可共用主库。

私有GitHub创建/推送及指定SSH22基础配置已有授权；域名Nginx准备亦已授权并完成检查站点。当前只公开非敏感HTTP检查，真实API/H2/管理端点拒绝代理，后端unit仍disabled/inactive。证书由用户安全提供，云安全组80/443人工核对；正式业务发布/认证TLS按后续具体方案，不据网络准备启动未完成业务。密钥/env不进Git或对话，不借其他项目资源。

真实证据、未执行项和阶段依赖见[B2报告](B2-阶段进展报告.md)。各交接保留当时状态，最终B2集成SHA/门禁由主控核证，不以角色分支通过替代。
