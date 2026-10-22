# 子agent注册表

2026-10-23：用户已原则批准B3工程验收，当前B4；C0—B3既有DONE/故障/修复原件保留。PM从干净dd96021快进`ad2fa1435007ab61622002672e9fd3ff5c0455d6`并实际核对五worktree。角色均为内部子agent，不表示五人同时运行。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前真实派发 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B4候选/评分/许可/原子计数已交付，95unit及独立验收通过；待阶段评审/实际缺陷分派 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | FE2/FE3代码38Node及真实JAR HTTP闭环通过；REVIEW_UI，下一优先完整浏览器 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | B4新增22IT已交；本地/Linux全量229通过，浏览器/负载另验 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2/TLS检查DONE；SSL已装，不重复索取；正式代理/认证/服务及开机策略等待独立DEP3授权 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | B4拆解/估算已交，主控最终证据已登记；追踪浏览器与DEP3独立门禁 |

B3已核：源码28cd42d本地173项通过，最新dd96021的CI run39821247938完整success（74 unit+74 IT+25 Node），用户原则批准；不回改B3-verification或原始证据。B4完整验收/CI仍未执行，不用B3数量代替。

B4后端只新增搜索边界/服务/Mapper/unit并复用冻结B1；QA独立acceptance/docs/testing；前端只static/tests/frontend。后端/前端/QA角色卡已有本轮派发及历史，本次PM不修改；服务器deploy、CI提案；PM只授权任务文档。公共契约/DTO/DDL/基础配置/依赖、根README/AGENTS、CI与合并归主控。

最多主控+三个子agent、2—3个写入任务，禁止继续派生；各自worktree，不操作notes、不改历史、不checkout/reset或清理他人文件。主控8080、后端8081、前端8083、本地部署8084，QA随机端口/绝对临时H2，PM不启动服务；worktree不隔离CPU/端口/环境，不共享主库。

浏览器最新实测仍disabled：用户先选稍后，随后询问开启方法，主控已告知官方Settings Browser/Work入口，未收到已启用确认。B4通过后优先完整浏览器，不能拿Node/HTTP替代。私有仓库保持，新域名text-vector-search.lemoncharger.com只开放TLS检查；业务proxy/BasicAuth未启用、应用inactive/disabled，DEP3需独立发布授权，密钥/env不进Git或对话。

工时与矩阵依赖见[B4启动与任务拆解](B4-启动与任务拆解.md)。中文维护注释/新Commit，真实分层测试/源码SHA交接；PM不跑Maven/服务器或替主控关闭门禁。

## 主控实施结果更新（2026-10-23 01:48）

后端c106e18/59f3c76、前端8a1ea67/2b93ec4、QA4ba76e5/9703c7f均已合入。主控源码b55473e全量95unit+96IT+38Node=229通过，22个新IT覆盖计数/并发/真实RST/资源关闭；真实前端HTTP闭环通过且本地进程45035已退出143。Linux CI run39826245521（main5f2f058）同为229通过且原件已归档，浏览器最新仍禁用/NOT_RUN；上文为启动时快照，不覆盖本条实际结果。完整证据见[集成验收](../testing/B4-verification.md)。初始工时为估计，实际提交和测试时间均保留，不补造时间线。
