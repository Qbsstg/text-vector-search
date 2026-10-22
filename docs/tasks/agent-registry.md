# 子agent注册表

2026-10-22更新：C0五角色已验收；当前B1跟踪及经授权DEP2/GitHub准备。五角色为当前会话内部子agent，模型/推理沿用用户配置，不代表五角色同时运行或应用重启后自动启动。

五个独立worktree已实际创建；PM本轮再次执行git worktree list核对。PM分支本次安全快进基线为`2dfd90d1e3529dc13a36605d221f1c47793ce056`，不假定其他角色使用相同阶段基线，具体见各交接。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（均在/Users/qbss/code/12times下） | 当前交付状态 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B1 REVIEW；源码fafcbd5/847210d、交接6dae03b已合入，29 unit+6 IT为后端证据 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | C0 DONE；业务FE1-3等待明确派发 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2基础准备DONE；3f0ef5c已合入并经主控独立SSH复核，DEP3未执行 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | QA-B1 REVIEW；a82b425完整29 unit+22 IT，已观察主控合入27b850c，最终集成门禁待主控 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | PM-B1 REVIEW；仅指定六份任务文档，实际SHA在交付消息回报 |

## 所有权与依赖

- 后端：分配的Java业务/Mapper及unit；B1→B2→B3→B4，公共契约/DDL/依赖变更提主控。Java17、清晰命名、显式import、关键逻辑详细注释，代码提交前运行对应unit，阶段合入前完整verify。
- 前端：static及显式分配测试；FE2依赖真API，FE3实际浏览器/网络验证，不改Java/DDL/pom。
- 服务器：deploy；DEP2基础准备已结束，DEP3按后续具体方案；CI仅提案，不代主控发布。
- 测试：acceptance、docs/testing；B1独立算法验收已交接，B2+竞态/进程/UI待派发，不削弱断言或改业务迎合结果。
- PM：docs/tasks；本轮只改backlog、registry、backend/server/qa角色卡及pm-b1交接，不改公共文件或代主控合并。

主控独占pom、Wrapper、基础application、schema、公共DTO/契约、根README/AGENTS、CI及合并。角色分支通过仍须集成核证；C0历史证据见C0-verification，B1最终DONE由主控登记。

## 隔离、并发与授权

最多主控+三个子agent工作、2—3个写入任务；禁止继续派生子agent。写入各自分支/worktree，不在其他角色或主控目录checkout/reset、清理/全仓格式化/撤销，不操作notes，不重写历史。

本地主控8080、后端8081、前端8083、部署骨架8084；QA随机端口与绝对临时H2目录，PM不启动服务。worktree不隔离CPU/环境/端口，各自H2不可共用主库。

用户已授权主控用个人GitHub账号创建私有仓库并推送，目标为`Qbsstg/text-vector-search`，私有性已双重复核；公开另需指令。运维已获`8.155.137.169:22`基础配置授权，DEP2专用资源准备与独立复核已完成，unit disabled/inactive。正式发布/入口认证TLS待后续方案；不改SSH/防火墙、开公网端口、重启机器或恢复/清理数据，密钥/env不进入Git或对话。
