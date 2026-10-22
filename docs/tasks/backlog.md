# 任务板

2026-10-23 FINAL-RELEASE部署前最新实证；PM在现有`codex/final-release-pm`独立更新，未快进。只读主树核证提交`ac02e5cc67a2db4550cf38c9f9a203c46d919c3d`，完整要求/历史见[执行跟踪](FINAL-RELEASE-执行跟踪.md)，A—F产物完整性见[交付索引](FINAL-RELEASE-交付索引.md)。**当前总体BLOCKED：REC-07原预期契约FAIL尚待用户裁决；不宣布最终全PASS。**

| ID | Owner | 状态 | 真实结果/证据与下一门禁 |
| --- | --- | --- | --- |
| C0 / B1 / B2 / B3 | 后端/QA/主控 | DONE | 算法/事务/真实强杀/生命周期及原SHA、失败修复、阶段CI保留，不重写历史 |
| B4核心/健康/独立验收 | 后端/QA/主控 | DONE | 用户已原则通过，原229/253及任务/搜索/计数/健康事实证据保持；不等于本轮70全过或正式上线 |
| CN注释 / GIT-CN / PUBLIC | 主控/各Owner | DONE | 中文化/历史对照及公开授权有效；OPS-05由QA复核README现状与限定格式扫描后PASS，不将格式扫描当任意秘密完备保证 |
| 固定批准生产包 | 主控 | DONE | source d016d6e8bb431ff17ba81f8977d31a1b96f51f7f / JAR 64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50 / 原CI39838054946；旧68因真实Chrome失败禁止发布 |
| 既有253重跑+新增自动化 | QA/主控/前端 | PASS | Java104 unit+124 IT+43 Node=271，253+新增14 IT+4 Node分层去重；14含此前12/2，43含新4，不能重复相加 |
| 最新Linux/原包一致性 | QA/主控 | PASS | 测试源码230c3471b789e994c46346c06d3dd83989273f95、CI39859349901 success；原批准d016/64bc JAR实际运行/冷备/独立恢复通过；systemctl inactive仍是shim，不算实机unit |
| 本地新增70矩阵 | QA | DONE（矩阵产物） | 最新64 PASS、REC-07 FAIL1、5 NOT_RUN；70是需求映射维度，不和271工程项相加，原材料只读保护 |
| REC-07契约冲突 | QA/主控/用户 | FAIL / BLOCKED | 隔离R32成功库用R33启动health200、search200空hits，符合冻结B4过滤但违反原阻止版本不兼容就绪预期；原断言与wire保留。异步裁决待回，不默认豁免/改PASS/改核心 |
| 隔离PERF及队列观测 | 主控/QA/服务器 | DONE / PASS（有限范围） | 三轮实际实验结束，PERF01—04按原预期分层核证PASS；主1300请求、独立100文档队列、组件/HTTP/生命周期资源分别保留，不拼成同一轮或宣传SLA/语义准确率 |
| 原本地Chrome/F03/U12 | 主控/用户/QA | DONE | 28 PASS已核证并复用；原68 FAIL与补测过程保留，不无意义重跑 |
| F05 | 用户/QA | NOT_RUN / APPROVED_EXCEPTION | 用户正式接受有限例外，不伪造FAILED，不扩大到REC-07或其他未验项 |
| 新增本地Chrome差异 | 主控/前端/QA | PASS | FR-B01—03、受控400/429与真实键盘恢复/渠道错误文本DOM分层核证；UI-02/06/07已关闭，不重复算JUnit/Node；UI-08公网仍未执行 |
| 预部署/本地浏览器归档 | 主控/QA | DONE（本地） | 原件归档3a142e7及全部SHA保留本地隔离分支；自动审批拦截88文件公开推送后，公开仅提供聚合结果/原件摘要，不包含原始请求、截图或资源明细 |
| DEP2 / TLS基础 | 服务器/主控 | DONE | SSL、80/443沿用，不重做DEP2、不重索证书；TLS检查可达不等于认证业务上线 |
| D1预检/配置受限备份 | 服务器 | DONE | 04:44:19主机/Java/证书/端口/应用与目录核对、配置新备份完成；是配置副本，不是业务H2冷备 |
| D8冷副本观测工具 | 服务器 | DONE（本地准备） | ColdSnapshot通过本地真实H2封存副本/占用与安全拒绝分支；主控javac17复核通过，已纳入下一轮Linux编译，不代表正式D8备份恢复PASS |
| DEP3主控GO | 主控 | BLOCKED / WAIT_GO | D1—D9、reviewer安全生成/交互、应用/Nginx分别enable已正式授权；普通操作不重复请示。但REC-07未裁决，暂无GO证据 |
| 正式D2—D9与两项enable | 服务器/主控 | NOT_RUN | 尚未上传正式包/启动正式业务unit/切业务Nginx；D9尚未触发，不虚构回滚。隔离PERF进程不是正式业务部署 |
| UI-08、OPS-02/03/04/06 | QA/服务器/主控 | NOT_RUN（5项） | 正式公网Chrome、服务器单实例/重启持久化、非覆盖冷备新目录恢复、真实认证保护、实际发布回退记录均待实机；不能以本机/隔离Linux关闭 |
| A—F最终交付 | 各Owner/主控/PM | IN_PROGRESS / BLOCKED | B矩阵及工程/本地证据已交，正式C/D/E缺D2—D9实证，F最终PASS被REC-07与5未验项阻止；索引不是“正式已交付”声明 |

最新结果来源为`final-release-evidence-review.md`及矩阵ac02e5c；保留先前55/14、63/6等真实快照，当前不得倒退为70全NOT_RUN、F05待批准或D1仅计划。271项JUnit/Node绿色只证明该工程断言；REC-07诊断JUnit绿不能覆盖其原预期FAIL。

当前继续推进不依赖裁决的证据整理/脱敏与工具准备；解除冲突后由主控按已授权范围决定GO，不重复普通部署审批。若需改变生产源码/JAR，重新给用户完整SHA/hash/CI确认；算法/API/DDL/核心事务/资源参数改动须停止报告。首发失败正常停机回TLS检查保留现场，无旧版不虚构代码回滚，不覆盖生产或备份。

公开集成在主控专用`.worktrees/final-public`的main，起点19604d6；原项目树改分支`codex/final-release-local-evidence`保留未推送原件归档3a142e7，无其他角色依赖该未发布提交，未reset/rebase/amend或修改用户原件。公开推送只包含已审源码、测试及不含原载荷的摘要，不推送本地隔离分支。

本次PM仅只读主树/Owner证据与现有文件元信息，未运行测试/Chrome/服务器、未改根README/测试证据/业务，用户原材料未动；本轮提交只含本任务授权的三份任务文档。
