# 子agent注册表

2026-10-23：B2核心原则通过，当前B3；C0/B1/B2既有DONE保留。PM实际核对五个worktree，写入基线`1c8676b0cf4d347e545bcb4d60486938db5df4ec`。五角色为内部子agent，不表示同时运行或应用重启后自动启动。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前交付 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B3三段已合入，终态16独立IT/主控阶段回归通过；13953cf恢复确认及fe32b5b H2关闭URL已修复合入，74 unit过，最终门禁待验 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | FE1代码/管理HTTP已验，当前主控25 Node通过；真实浏览器REVIEW_UI，B4搜索仍禁用 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2/HTTP/TLS检查DONE；主控curl/SSH独立复核，业务模板未启用、应用inactive/disabled |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | 2734b7e测试组成、a2ddb6a终态16 IT已合入；代理独立3 IT通过；后半6项真实进程按修复后真实默认URL重跑，结果待交 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | PM-B3二次快照：快进1c8676b、九份任务文档/原证据校验，不跑Maven/部署，完成释放槽位 |

后端分配task/persistence/相关配置与unit，统一持有事务/状态机；QA独立acceptance及测试文档，不替业务修错或降断言；前端只static/分配测试；服务器deploy、CI提案；PM只docs/tasks。公共DTO/契约、DDL、基础配置、依赖、根README/AGENTS、CI及合并归主控，变更先提议。

B2历史测试明确隔离并保留原断言，B3真实生产handler另验。新Java/维护注释和新Commit中文、显式路径提交，代码提交前对应测试、阶段完整verify；B1算法/黄金/版本及历史SHA不改。

最多主控+三个子agent、2—3个写入任务，不继续派生；各自worktree，不checkout/reset/清理他人文件，不操作notes。主控8080、后端8081、前端8083、本地部署8084，QA随机端口和绝对临时H2，PM不启动服务；worktree不隔离CPU/端口/环境，数据库不得共享。

私有仓库及SSH22授权保持；新域名text-vector-search.lemoncharger.com的有效证书已核验，私钥仅服务器root受限目录，本次临时副本由服务器角色清理，PM不接触ZIP/key。TLS只检查，不代理真实API；BasicAuth/业务代理仅未启用模板、无密码，应用unit仍inactive/disabled。云安全组人工、真实认证与正式发布另行确认，B4/DEP3不自动展开。

当前主控代码9a8d74d已有73 unit+63 IT+25 Node=161通过，真实JAR正常/空文成功、SIGTERM/正常重启有证；全B3仍待恢复确认修复、独立强杀/生命周期及最终CI，不把组件/冒烟当全部通过。

阶段证据与依赖见[B3报告](B3-阶段进展报告.md)。角色分支、默认配置回归、TLS检查与最终B3源码验收分层记录；最终SHA/verify/CI由主控填写。

## 主控最终本地核验（2026-10-23 01:09）

后端与QA交付已合入，源码`28cd42d7603c862830b51c38bb98e1de10e6427d`执行Java17全量**74 unit+74 IT+25 Node=173通过**，失败/错误/跳过0。恢复门禁、四检查点真实SIGKILL、正常SIGTERM及CPU超时后零SQL均通过，11子进程实际退出；两处发现均有复现/修复证据。角色本轮完成待命；最终CI由主控核验，详见[集成验收](../testing/B3-verification.md)。上文为此前阶段快照，不能覆盖本条已验证结果。B4、真实浏览器与正式业务发布仍未执行。
