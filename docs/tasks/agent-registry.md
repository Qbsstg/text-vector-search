# 子agent注册表

2026-10-23：B2核心原则通过，当前B3；C0/B1/B2既有DONE保留。PM实际核对五个worktree，写入基线`de71eac7c0ee1ea9eda3266dc2e3497c49410714`。五角色为内部子agent，不表示同时运行或应用重启后自动启动。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前交付 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B3进行中，单一Owner守终态/恢复/生命周期；观察到角色00c5af5，测试/主控集成待核证 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | FE1代码/25 Node/管理HTTP已验；真实浏览器REVIEW_UI，B4搜索仍禁用 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2/HTTP准备DONE；f9cf401新域名TLS检查已交接，主控独立复核待补；业务模板未启用 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | ecf7cb5的15组B3计划已合入、执行NOT_RUN；当前ProxyOriginIT独立观测 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | PM-B3九份任务文档；不跑Maven/部署，交接后结束释放槽位 |

后端分配task/persistence/相关配置与unit，统一持有事务/状态机；QA独立acceptance及测试文档，不替业务修错或降断言；前端只static/分配测试；服务器deploy、CI提案；PM只docs/tasks。公共DTO/契约、DDL、基础配置、依赖、根README/AGENTS、CI及合并归主控，变更先提议。

B2历史测试明确隔离并保留原断言，B3真实生产handler另验。新Java/维护注释和新Commit中文、显式路径提交，代码提交前对应测试、阶段完整verify；B1算法/黄金/版本及历史SHA不改。

最多主控+三个子agent、2—3个写入任务，不继续派生；各自worktree，不checkout/reset/清理他人文件，不操作notes。主控8080、后端8081、前端8083、本地部署8084，QA随机端口和绝对临时H2，PM不启动服务；worktree不隔离CPU/端口/环境，数据库不得共享。

私有仓库及SSH22授权保持；新域名text-vector-search.lemoncharger.com的有效证书已核验，私钥仅服务器root受限目录，本次临时副本由服务器角色清理，PM不接触ZIP/key。TLS只检查，不代理真实API；BasicAuth/业务代理仅未启用模板、无密码，应用unit仍inactive/disabled。云安全组人工、真实认证与正式发布另行确认，B4/DEP3不自动展开。

阶段证据与依赖见[B3报告](B3-阶段进展报告.md)。角色分支、默认配置回归、TLS检查与最终B3源码验收分层记录；最终SHA/verify/CI由主控填写。
