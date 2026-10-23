# 任务板

## 当前最终结论

2026-10-23，按用户批准范围FINAL-RELEASE PASS，当前有效70主编号、273唯一自动化及正式接口/浏览器/生命周期/独立恢复已核证。生产固定d016/64bc未换，原REC-07历史FAIL、F05批准例外和首发无旧版回滚未执行分别保留。详见[最终交付聚合结果](../testing/最终交付聚合结果.md)。下方原任务板为历史部署前快照，不能据其未启动/待裁决状态倒退当前结果；完整任务与实机交接在授权本地保留，不公开具体运维元数据。

## 历史部署前快照


2026-10-23 FINAL-RELEASE执行中。用户已正式选择保持冻结B4并批准修订REC-07，见[裁决记录](../design/REC-07-验收判据裁决-20261023.md)。**当前IN_PROGRESS：REC-07-R1正在独立实测，不再等待用户裁决；旧FAIL永久保留，未测不填新PASS。** 完整历史见[执行跟踪](FINAL-RELEASE-执行跟踪.md)，A—F完整性见[交付索引](FINAL-RELEASE-交付索引.md)。

| ID | Owner | 状态 | 真实结果/证据与下一门禁 |
| --- | --- | --- | --- |
| C0 / B1 / B2 / B3 | 后端/QA/主控 | DONE | 算法/事务/真实强杀/生命周期及原SHA、失败修复、阶段CI保留，不重写历史 |
| B4核心/健康/独立验收 | 后端/QA/主控 | DONE | 用户已原则通过，原229/253及任务/搜索/计数/健康事实证据保持；不等于本轮70全过或正式上线 |
| CN注释 / GIT-CN / PUBLIC | 主控/各Owner | DONE | 中文化/历史对照及公开授权有效；OPS-05由QA复核README现状与限定格式扫描后PASS，不将格式扫描当任意秘密完备保证 |
| 固定批准生产包 | 主控 | DONE | source d016d6e8bb431ff17ba81f8977d31a1b96f51f7f / JAR 64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50 / 原CI39838054946；旧68因真实Chrome失败禁止发布 |
| 既有253重跑+新增自动化 | QA/主控/前端 | PASS | Java104 unit+124 IT+43 Node=271，253+新增14 IT+4 Node分层去重；14含此前12/2，43含新4，不能重复相加 |
| 最新Linux/原包一致性 | QA/主控 | PASS | 测试源码5897cfd4764b4a5b32d973f6722249d26a04b800、CI39862014960 success；原批准d016/64bc JAR实际运行/冷备/独立恢复通过；systemctl inactive仍是shim，不算实机unit |
| 本地新增70矩阵 | QA/主控 | IN_PROGRESS（修订核证） | 原70历史维度64 PASS、REC-07历史FAIL1、5 NOT_RUN保留；有效修订口径暂64 PASS、R1及5正式项共6 NOT_RUN，待真实执行更新，不和工程数相加 |
| REC-07原判据 | QA/主控/用户 | HISTORICAL_FAIL / 已裁决 | 原预期、原断言/wire、旧文件与SHA全部保留；用户批准以冻结双版本过滤修订验收，不把旧FAIL改写成PASS |
| REC-07-R1 | QA/主控 | IN_PROGRESS / NOT_RUN | 独立新类实测旧行完整、双版本四组合、无迁移/重算、真实健康及R33新提交检索；通过后才新记PASS，不改生产算法/DDL/API/状态机 |
| 隔离PERF及队列观测 | 主控/QA/服务器 | DONE / PASS（有限范围） | 三轮实际实验结束，PERF01—04按原预期分层核证PASS；主1300请求、独立100文档队列、组件/HTTP/生命周期资源分别保留，不拼成同一轮或宣传SLA/语义准确率 |
| 原本地Chrome/F03/U12 | 主控/用户/QA | DONE | 28 PASS已核证并复用；原68 FAIL与补测过程保留，不无意义重跑 |
| F05 | 用户/QA | NOT_RUN / APPROVED_EXCEPTION | 用户正式接受有限例外，不伪造FAILED，不扩大到REC-07或其他未验项 |
| 新增本地Chrome差异 | 主控/前端/QA | PASS | FR-B01—03、受控400/429与真实键盘恢复/渠道错误文本DOM分层核证；UI-02/06/07已关闭，不重复算JUnit/Node；UI-08公网仍未执行 |
| 预部署/本地浏览器归档 | 主控/QA | DONE（本地） | 原件归档3a142e7及全部SHA保留本地隔离分支；自动审批拦截88文件公开推送后，公开仅提供聚合结果/原件摘要，不包含原始请求、截图或资源明细 |
| DEP2 / TLS基础 | 服务器/主控 | DONE | SSL、80/443沿用，不重做DEP2、不重索证书；TLS检查可达不等于认证业务上线 |
| D1预检/配置受限备份 | 服务器 | DONE | 04:44:19主机/Java/证书/端口/应用与目录核对、配置新备份完成；是配置副本，不是业务H2冷备 |
| D2固定包预置 | 服务器/主控 | DONE（未激活） | 06:17:35批准d016/64bc三文件六参/远端SUM通过，RENAME_NOREPLACE无覆盖落位；root:vector-search目录0550/文件0440。首次tar AppleDouble拒绝现场保留，三文件直传后成功；current不存在、应用inactive/disabled、8080无监听 |
| D3认证材料 | 服务器 | DONE（未引用） | reviewer强随机密码仅服务器root受限文件保存，stdin bcrypt离线验证成功；hash仅Nginx受限文件，未输出秘密，未被当前站点引用。真实HTTP401/认证未验 |
| D4停用态unit/永久锁 | 服务器 | DONE（静态准备） | 原unit受限备份后安装审阅模板，verify/daemon-reload成功；永久锁O_EXCL新建inode261932并保留，原env与D1完全一致。service仍inactive/disabled/PID0，未切current/启动或enable，无生产H2 |
| Nginx pending | 服务器 | DONE（未加载） | 独立nginx -t成功，整站认证/可信头/内部路径拒绝/禁重放配置已准备；无enabled链接或reload，当前HTTP/TLS配置不变，不能当真实入口验收 |
| D8冷副本观测工具 | 服务器 | DONE（本地准备） | ColdSnapshot通过本地真实H2封存副本/占用与安全拒绝分支；主控及CI39862014960已实际javac17编译通过，不代表正式D8备份恢复PASS |
| DEP3正式启用GO | 主控 | WAIT_REVISED_VERIFY | 用户裁决已到且D1—D9/两项enable授权有效；R1实测、完整回归/CI与其他门禁核证后立即继续D5—D9，不重复申请普通授权 |
| 正式D5—D9与两项enable | 服务器/主控 | NOT_RUN | 包/认证材料/停用态unit/pending已准备，未切current/启动业务/加载Nginx或enable；真实认证、运行态单实例、业务与重启恢复均未验。D9未触发，不虚构回滚 |
| UI-08、OPS-02/03/04/06 | QA/服务器/主控 | NOT_RUN（5项） | 正式公网Chrome、服务器单实例/重启持久化、非覆盖冷备新目录恢复、真实认证保护、实际发布回退记录均待实机；不能以本机/隔离Linux关闭 |
| A—F最终交付 | 各Owner/主控/PM | IN_PROGRESS / BLOCKED | B矩阵及工程/本地证据已交，C已有D1/D2及D3/D4静态准备，正式C/D/E仍缺D5—D9和真实入口/运行实证；F被REC-07与5未验项阻止 |

最新结果来源为`final-release-evidence-review.md`及矩阵ac02e5c；保留先前55/14、63/6等真实快照，当前不得倒退为70全NOT_RUN、F05待批准或D1仅计划。271项JUnit/Node绿色只证明该工程断言；REC-07诊断JUnit绿不能覆盖其原预期FAIL。

当前继续推进不依赖裁决的证据整理/脱敏与工具准备；解除冲突后由主控按已授权范围决定GO，不重复普通部署审批。若需改变生产源码/JAR，重新给用户完整SHA/hash/CI确认；算法/API/DDL/核心事务/资源参数改动须停止报告。首发失败正常停机回TLS检查保留现场，无旧版不虚构代码回滚，不覆盖生产或备份。

公开集成在主控专用`.worktrees/final-public`的main，起点19604d6；原项目树改分支`codex/final-release-local-evidence`保留未推送原件归档3a142e7，无其他角色依赖该未发布提交，未reset/rebase/amend或修改用户原件。公开推送只包含已审源码、测试及不含原载荷的摘要，不推送本地隔离分支。

本次PM仅只读主树/Owner证据与现有文件元信息，未运行测试/Chrome/服务器、未改根README/测试证据/业务，用户原材料未动；本轮提交只含本任务授权的三份任务文档。
