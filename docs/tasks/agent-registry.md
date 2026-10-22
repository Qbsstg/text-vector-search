# 子agent注册表

2026-10-23真实Chrome收尾。固定修复候选为`d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`；后续纯文档HEAD不替换它。历史角色、分支及SHA均保留，不派生新角色。

| 角色 | 实际子agent | 模型/推理 | 分支/工作树 | 当前安排 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B1—B4及真实健康已验，待命仅处理确证缺陷，不扩展接口或改算法 |
| 前端工程师 | /root/frontend_engineer；本轮/root/frontend_ui_fix | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | 计时器receiver与SVG修复已合入，39 Node通过；本轮交接完成，不与主控并行改文件 |
| 测试工程师 | /root/test_engineer；本轮/root/qa_ui_review | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | 独立核证253 CI、26 PASS与3 NOT_RUN，QA提交完成；真实FAILED/visibility及DEP3不提前关闭 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | DEP2/TLS及DEP3方案保持，等待固定版本/具体操作独立批准；本轮未启动业务/代理/创建凭据 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | 历史四份交接完成，主控更新本轮真实状态；继续跟踪未验与授权，不代为验收或合并 |
| 主控 | /root | 当前会话 | main / /Users/qbss/code/12times | 独占Chrome、公共文档、证据归档和集成；清除受控页面并正常停止本轮服务，保留临时DB |

新CI39838054946为completed/success：104 unit+110 IT+39 Node=253，零失败/错误/跳过；JAR实字节与独立CI日志匹配。旧68候选真实Chrome FAIL、两个修复与重新验收过程保留。26/29浏览器PASS不等于完整UI门禁通过；U12/F03/F05仍NOT_RUN。

最多主控加三个子agent、2—3写入任务，使用独立branch/worktree/端口/数据库。所有权和模型沿AGENTS.md；不派生、不操作notes、不重写历史或清理他人目录。当前角色工作均已交接，无需为等待用户维持空转。

正式DEP3、入口认证、实际HTTPS同源/跨源、未认证拒绝、六接口/计数/失效、服务器重启持久化及开机策略仍待独立授权与实测；SSL无需重索，安全组仍由用户人工确认。
