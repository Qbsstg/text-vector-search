# 任务板

2026-10-23候选实核更新；本次PM仍在c5aebad3f7b916f05a0ac6ea26f9e87242effc56基础上独立提交，**未快进**。固定候选源码为68d80cb967c6d321047672811dea45b555ba5cbc，后续纯文档HEAD或新CI包不替换它。用户已原则批准B4核心；正式DEP3和真人UI仍分层待办，详见[交付与审批清单](最终交付准备与审批清单.md)。

| ID | Owner | 状态 | 已核证事实与剩余门禁 |
| --- | --- | --- | --- |
| C0 / B1 / B2 / B3 | 后端/QA/主控 | DONE | 原始算法/事务/四强杀/生命周期及各阶段CI证据全部保留，不改历史SHA |
| B4后端核心/独立QA/集成 | 后端/QA/主控 | DONE | 用户原则通过；原229项及计数/并发/未知提交/真实RST证据保留 |
| CN注释 / GIT-CN历史 | 主控/QA/服务器 | DONE | 原独立中文化、回归和28条历史对照保持 |
| PUB-REVIEW | 主控 | DONE | 用户确认本人公开并明确授权；PUBLIC/private=false；824blob指定格式/禁文件审计为当时快照，不作完备秘密检测保证 |
| HEALTH-FINAL | 后端/QA/主控 | DONE | B4健康由只读运行事实合取、非硬true；最终全量含104 unit+110 IT+38 Node=252，失败/错误/跳过0；UI/发布许可仍独立 |
| QA-FINAL Linux锁/冷备恢复 | QA/主控 | DONE | 实际活动H2/锁拒绝、hash/权限、拒覆盖/错SHA、新目录真实JAR恢复状态/计数/失效保持通过；systemctl inactive为shim，不算实机systemd验收 |
| CANDIDATE固定包 | 主控 | DONE | 68d80cb、CI39834086512 completed/success、artifact12450815373，JAR独立CI日志hash与实包/6参验证匹配；大小27308443。候选值固定见清单 |
| FINAL-CI原件归档 | 主控 | DONE | 成功final-ci-68d80cb的194份原件与首次失败96原件均保留；88d1565已提交FINAL-verification及固定Chrome入口，失败不抹除 |
| FE1—FE3代码/HTTP | 前端/主控 | REVIEW_UI | 38 Node及既有真实HTTP通过；不能替代真人DOM/视觉/键盘/XSS |
| UI-MANUAL-PREP | 前端 | DONE | 手工脚本/证据模板已交，18U+11F共29项全部NOT_RUN；主控另给固定候选Chrome入口 |
| UI-FINAL真人验收 | 用户/前端/QA | REVIEW_UI | 最新browsers=[]/Work disabled、Mac locked；需要用户Chrome实际操作固定68候选，未生成假截图或PASS |
| DEP2 / TLS-CHECK | 服务器/主控 | DONE | SSL沿用，02:41刷新TLS检查200/API404、80/443监听、8080无；业务inactive/disabled/NRestarts0 |
| DEP3-PREP | 服务器/主控 | DONE | 只读预检/候选/本地及Linux方案验证完成；当前无release/current/DB，htpasswd缺失、35G可用；实机运行仍未验 |
| DEP3正式业务发布 | 用户/服务器/主控 | TODO | 固定68版本+必要apache2-utils/交互认证/上传/proxy-env/unit持久锁/启动/业务Nginx切换/重启及分别开机策略需独立批准；本轮无正式部署、生产写入或删除 |
| PM-FINAL | 项目管理 | REVIEW | 四份Owner文档更新，仅文档/Git/候选只读字节校验；不快进、不改公共/其他角色文件 |

首次CI失败为lsof rc1有输出被过严判为矛盾；52cda701修解析、caa6d020留原始诊断后，68d80cb真实Linux复验成功。普通夹具/默认inactive shim不冒称实机systemd；原失败96文件路径docs/testing/evidence/final-ci-8fd97e1-failed保留。

固定下载JAR另完成本机25个HTTP请求，覆盖六接口/计数/幂等失效/代理同源允许跨源403；pid54327已SIGTERM退出143，临时库和final-candidate-local-smoke原件保留。这不是浏览器、真实TLS或实机认证；25请求不加到252项测试数量。后续代码/配置与固定68 diff为空，文档HEAD/最后文档CI不替换该候选。

独立审批JAR SHA-256固定为`61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f`，schema为`54758965a66d42092da6049ed67a3043d57b1542705f38198975c424fed3d69d`，算法char12-sha256-r32-nfkc-root-ws-v1。PM本次只读本地JAR/SUM和源码/JAR内schema再次匹配，未重跑代码测试或CI。

剩余关键路径：用户Chrome约1—2小时→固定版本与具体操作独立批准→DEP3约1—2小时→交接约0.25—0.5小时；人工等待不作承诺。先前健康/候选/CI估计已完成，不继续算剩余。首发无旧版，失败回TLS检查并保留数据；恢复生产或清理另批，恢复验证仅新独立目录。SSL无需重索，云安全组仍用户人工。
