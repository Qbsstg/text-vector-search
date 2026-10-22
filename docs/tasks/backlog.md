# 任务板

2026-10-23最终阶段；PM从干净ab990bc快进`16356e03aca5046523a16f740c16fb4c7608b288`。用户已原则批准B4后端核心，仅做最终健康/UI验收与DEP3发布准备，冻结六接口、B1—B4规则/DDL/参数和历史证据。详见[最终交付准备与审批清单](最终交付准备与审批清单.md)。

| ID | Owner | 状态 | 已有证据与剩余门禁 |
| --- | --- | --- | --- |
| C0 | 主控及五角色 | DONE | 原3 unit+6 IT、JAR/浏览器证据保留 |
| B1 / QA1-B1 / PM-B1 | 后端/QA/PM/主控 | DONE | 原算法/黄金/29 unit+22 IT及CI原件冻结 |
| B2核心/独立QA/PM-B2 | 后端/QA/PM/主控 | DONE | 53769c6的53 unit+44 IT+25 Node及远程原件保留 |
| B3终态/恢复/生命周期/独立QA/PM | 后端/QA/PM/主控 | DONE | 28cd42d及Linux173项、四强杀/SIGTERM/11进程与两处真实修复原件保留 |
| B4-01/02后端核心 | 后端 | DONE | c106e18/59f3c76及最终命中/快照/并发/未知提交/RST/关闭已独立验收，用户原则通过 |
| QA-B4设计/执行 | QA | DONE | 12矩阵计划与22个实际新IT分开；4ba76e5/9703c7f已合入，未将编号算通过数 |
| B4-03集成/CI | 主控 | DONE | b55473e本地95 unit+96 IT+38 Node=229；Linux main5f2f058/run39826245521同为229，最新阶段ab990bc/run39827330708 success；不是最终健康候选 |
| FE1—FE3代码/真实HTTP | 前端/主控 | REVIEW_UI | 8a1ea67/2b93ec4与38 Node/真实JAR闭环通过；真人UI仍未验 |
| DEP2 / HTTP准备 / TLS-CHECK | 服务器/主控 | DONE | 原专用账号/目录和独立TLS检查通过；SSL有效，不重复索取；业务仍未发布 |
| CN注释 / GIT-CN历史 | 主控/QA/服务器 | DONE | 原独立中文化/回归、28条提交对照与历史SHA保留 |
| PUB-REVIEW公开授权复核 | 主控 | DONE | 用户确认此前公开为本人操作并明确授权；本轮PUBLIC、API private=false；824历史blob未命中指定凭据格式/禁扩展名，不宣称完备秘密检测 |
| HEALTH-FINAL实现/目标unit | 后端/主控 | REVIEW | 5377542聚合只读事实+3cea5eb Controller已合入，目标12 unit通过；非READY/关闭/六映射等独立回归及最终全量待核 |
| QA-FINAL健康/冷备恢复 | QA/主控 | READY | 迁移健康状态不降旧业务断言；Linux独立锁/活动H2拒绝/新目录恢复/源库不变仍需实际证据 |
| CANDIDATE最终固定包 | 主控 | TODO | 最终全量/CI成功后下载app.jar、manifest、SUM，复核源码SHA/JAR与schema hash；字段待主控生成，不填当前文档HEAD冒充 |
| UI-MANUAL-PREP | 前端 | DONE | frontend-final-ui手工脚本/模板已交；18个U项+11个F项，共29项全部NOT_RUN，模板不是PASS |
| UI-FINAL真人验收 | 用户/前端/QA | REVIEW_UI | Work仍disabled，使用固定候选按Chrome脚本实操并留截图/HAR/Console；Node/HTTP不能替代 |
| DEP3-PREP只读预检/本地方案 | 服务器 | REVIEW | 无current/release/DB；htpasswd缺失；本地计划/验证器/拒绝路径已交，真实Linux冷备与恢复校验待QA |
| DEP3正式业务发布 | 服务器/主控/用户 | TODO | 必须批准固定源码/独立JAR hash和具体操作；本轮未上传/启动/创建认证/改实际env/替换数据 |
| PM-FINAL | 项目管理 | REVIEW | 本次四份任务文档，八项交付/门禁/审批/剩余估计分组；不跑测试、服务器或代主控关闭最终门禁 |

健康businessReady由运行事实推导，不能作为UI/CI/认证/发布许可。最终候选变更须重审CI/hash，纯文档HEAD不替代已测源码。已有229项仅证明B4核心，新增12目标unit不推算成最终全量通过数。

仓库按最新授权保持公开，[公开授权与安全复核](../git/公开授权与安全复核-20261023.md)覆盖旧私有要求；旧可见性检查保留当时事实，不自动回退。格式扫描不是任意敏感内容的完备证明，用户PDF/ZIP/key/env/运行库继续不入库。

当前server只有TLS检查。DEP3要单独批准apache2-utils必要安装/安全交互建入口账号、固定候选上传、proxy/env/unit持久flock/单实例启动、全入口认证切换及启动策略。首发无旧release/DB，失败回TLS检查并保留现场；冷备恢复只到新独立目录，生产替换/清理/丢弃写入的恢复不在本轮授权。

关键路径：健康/独立QA→最终全量CI与不可变候选→Chrome29项→固定版本和操作批准→受保护服务器/重启持久化验收→交付。具体估计见清单，均非完成保证。云安全组用户人工，SSL无需重给；未跟踪测试资料及70条原用例保留，不计作执行数。
