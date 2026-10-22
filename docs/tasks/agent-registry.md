# 子agent注册表

2026-10-23固定候选快照；本次PM分支以c5aebad3f7b916f05a0ac6ea26f9e87242effc56为父提交，按指令**不快进**。用户已原则批准B4核心，候选保持68d80cb967c6d321047672811dea45b555ba5cbc；后续纯文档HEAD/其他CI不替换候选。五角色配置与历史不改。

| 角色 | 实际子agent | 模型 / 推理 | 分支 / 工作树（/Users/qbss/code/12times下） | 当前交付/依赖 |
| --- | --- | --- | --- | --- |
| 后端工程师 | /root/backend_engineer | gpt-6.1-sol / high | agent/backend / .worktrees/backend | B1—B4及最终只读事实健康已验；候选Linux104 unit+110 IT+38 Node=252通过，后续只接实际缺陷 |
| 前端工程师 | /root/frontend_engineer | gpt-6.1-sol / high | agent/frontend / .worktrees/frontend | 代码/38 Node/HTTP保留；Chrome29项仍NOT_RUN，Work disabled且Mac locked，待用户实际操作固定候选 |
| 测试工程师 | /root/test_engineer | gpt-6.1-sol / medium | agent/qa / .worktrees/qa | 最终健康与Linux活动H2/锁/冷备/新目录真实JAR恢复已验；systemctl inactive为shim，不推成实机运行；真人UI/DEP3另验 |
| 服务器工程师 | /root/server_engineer | gpt-6.1-sol / medium | agent/server / .worktrees/server | 02:41只读刷新TLS检查正常、应用inactive/disabled/NRestarts0；无release/current/DB、htpasswd缺失；待固定版本与必要安装/账号交互/上线操作批准 |
| 项目管理 | /root/project_manager | gpt-6.1-sol / xhigh | agent/pm / .worktrees/pm | 本次仅四Owner文档与现有候选字节核对，不快进/测试/服务器操作，交接后结束 |

成功CI39834086512/source68d80cb、artifact12450815373已由主控下载并以独立CI日志JAR hash、实际字节和六参验证器核证；失败8fd97e1原件保留，52cda701/caa6d020修复诊断不改历史；成功194份原件、FINAL-verification及固定Chrome入口已由主控88d1565提交。固定下载JAR另25个本机HTTP请求通过、pid54327 SIGTERM143，非浏览器/真实TLS或实机认证，不计入252项。PM读取本地候选仅复核hash/大小/manifest/SUM/schema，不声称自己运行CI。

当前PUBLIC/private=false符合用户直接授权，旧私有/公开快照保留；824blob格式审计不是任意秘密的完备证明。公共API/CI/Java、其他角色交接和历史验收由对应Owner持有，本次PM不修改。

最多主控+三个子agent、2—3写入任务，分支/端口/H2隔离，不派生、不操作notes/重写历史或清理他人目录。候选文件只读，ZIP/key/env不入Git或对话。

当前只剩真人Chrome29项、云安全组人工确认及固定68发布批准/安全交互凭据/实际受保护服务器验收。SSL已装无需重给；必要apache2-utils、上传、proxy-env、unit持久flock、应用启动/业务Nginx认证切换/重启和分别开机策略均待独立授权。首发失败回TLS检查保留数据，无旧版，恢复生产另批。详见[固定候选与审批清单](最终交付准备与审批清单.md)。
