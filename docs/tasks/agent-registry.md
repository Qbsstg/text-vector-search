# 子agent注册表

2026-10-22 C0记录。五角色已实际启动并完成首轮只读分析；模型/推理为用户指定配置，均为`gpt-6.1-sol`，极高对应`xhigh`。这是当前会话内部子agent记录，不是五个独立侧栏聊天；角色卡可复用，不保证应用重启后自动启动。

共同写入基线：`0f692387577be629d296d3a5fe0ba626072f93b4`。PM在自己的工作树实际执行`git worktree list --porcelain`，确认下列五个工作树及分支已创建。表中是检查时的角色产物，不推定当前主控集成SHA，也不表示五角色此刻全部并发运行。

| 角色 | 实际子agent | 模型 / 推理 | 实际分支与绝对工作树 | C0状态与角色证据 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend；`/Users/qbss/code/12times/.worktrees/backend` | REVIEW；交接提交`2c3b9a26a06b5297a73975b0a7877d1bcea1d09a`，待主控合入 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend；`/Users/qbss/code/12times/.worktrees/frontend` | REVIEW；静态入口提交`6b0f5de4f88322ed6eba82895c88f386a0704e71`；主控已完成C0浏览器/真实health验证 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server；`/Users/qbss/code/12times/.worktrees/server` | REVIEW；本地样例/preflight提交`0335dc5ae6a75dbc125adf549e30ebf5ea11d2f3`；未部署 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa；`/Users/qbss/code/12times/.worktrees/qa` | REVIEW；提交`7b81d2904c3e40080181c672c966df5d05def3ca`；角色交接记录2个unit+6个IT通过，待合入复跑 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm；`/Users/qbss/code/12times/.worktrees/pm` | REVIEW；仅本轮指定三文档；实际提交SHA由交付消息回报，不写自引用哈希 |

## 职责和交接依赖

| 角色 | Owner职责与可修改范围 | 下一阶段依赖与限制 |
| --- | --- | --- |
| 后端 | Java业务/Mapper与`src/test/java/**/unit/**`；一个Owner守住向量、任务、搜索和事务一致性 | B1→B2→B3→B4；不改公共API/DDL/依赖，不拆散同一事务给多个角色 |
| 前端 | `src/main/resources/static/**`及显式分配测试；真实三功能区、详情和有限轮询 | FE1按冻结契约开发；FE2依赖B4真API；FE3浏览器与网络竞态验收；不改Java/DDL/pom |
| 服务器 | `deploy/**`；本地部署样例、只读预检、实机运行/备份/回滚设计 | DEP1属C0；DEP2/DEP3依赖真实主机和授权，业务发布还依赖集成门禁；CI只向主控提案 |
| 测试 | `src/test/java/**/acceptance/**`、`docs/testing/**`；独立验收、复现和证据 | QA1设计提前、实际断言依赖对应B1-B4；QA2竞态/强杀/UI，QA3交付复演；不削弱断言或代改业务 |
| 项目管理 | `docs/tasks/**`；需求、里程碑、依赖、风险和证据追踪 | 本次仅backlog/registry/pm-c0交接；PM0→PM1→PM2→PM3随主控派发，不改API/DDL/实现或代主控合并 |

主控独占pom、Wrapper、基础application配置、schema、公共DTO/契约、根README/AGENTS、CI及合并；C0最终DONE由主控核证。主控已回报集成代码3个unit+6个IT及C0浏览器验证通过，服务器样例已合入；BE/PM文档后续合入及最终证据仍归主控，详见任务板。角色可写主控显式分配的交接文档；不能因角色卡或规划存在就自动开始后续业务。

## 隔离和并发

- 最多主控+三个子agent工作，最多2—3个写入任务；五个已创建工作树不等于五个角色同时运行。禁止角色继续派生子agent。
- 本地主控8080、后端8081、前端8083、服务器骨架8084；QA使用随机端口及绝对临时H2目录；PM不启动服务。每个worktree自己的`data/`互相隔离，不能共用主库。
- worktree不隔离CPU、环境变量或端口；不在其他角色或主工作区checkout/reset、全仓格式化、撤销或清理文件，不操作notes分支。
- 所有提交显式列本角色文件，由主控审查后集成；没有发布、远程创建/push、登录服务器、开端口或恢复/删除持久数据授权。
- 每个交接包含基线/分支、文件、契约/依赖变化、真实命令与结果、未执行项、风险和SHA；角色分支通过仍须集成复跑。
