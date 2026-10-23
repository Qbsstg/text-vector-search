# FINAL-ACCEPTANCE 70条分层映射

## 当前独立核证快照（后续证据已收到）

**FINAL_ACCEPT仍BLOCKED**：生产仍2764/b8b且已自然复现H2缺陷；988/becb未获替换批准，完整正式浏览器还有NOT_RUN。根因、新283工程与Mac/Linux完整35分钟及正常重启已核证。本快照覆盖下方“首次54/6/10、ONLINE=0”历史结论，旧执行记录永久保留。

| 层次 | 当前精确结论 |
| --- | --- |
| 原基线独立工程 | QA实际执行46ebe7d：104 Unit+126 IT+52 Node=282；首轮7个bind启动errors另存 |
| 修复候选工程 | Owner执行9883400、QA独立核本地XML/CI：105 Unit+126 IT+52 Node=283；不能与282相加 |
| 新Mac35分钟 | 固定988/becb，2104.004秒、492业务事件=457个200+35个202、35篇、1正常重启、写重试0；ISOLATED_PASS |
| 新Linux35分钟 | 固定988/becb，2123.218秒、482业务+357启动健康=839请求、35篇/1正常重启/0写重试；conn0自然寿命回收已覆盖；Owner安全回执经QA核证为ISOLATED_PASS。MemoryMax1536M/CPUQuota100%/Xms256m/Xmx1024m与Mac不同，不冒称生产原资源的同机对照 |
| 后补冷副本raw验证 | 正常停机后新副本35×1024=35840bytes与固定CI包B1逐字节重算相等；独立ISOLATED_PASS，不是35分钟前后raw快照比较 |
| 旧2.4升级兼容 | 新副本22文档/22任务全字段及raw向量三快照一致，114请求/1重启/新2篇；ISOLATED_PASS，不证明降级 |
| 本轮HTTPS | Owner从原账本生成的白名单聚合109请求/6篇、unknown0/writeRetries0；QA核聚合/hash，不冒称QA读取109 raw账本 |
| Chrome与数据末检 | 主控真实浏览器2篇/真实RUNNING与SUCCEEDED；Owner用户新基线旧33摘要严格0额外差异；QA独立核脱敏网络/任务原响应/五hits/安全回执 |
| 当前70映射 | 70唯一ID全部列在下表。54个ID有本轮明确隔离子证据、26个ID有精确ONLINE_PASS子范围；层次相交，不是54+26个需求。UI8联合范围仍含NOT_RUN，不报70全绿 |

### 本轮70个ID的最新分层

ONLINE_PASS仅指本表所列精确子范围，未列出的原联合判据不能自动通过。所有线上实际应用均是2764/b8b，不能换称988修复候选。

| ID | 本轮隔离层 | ONLINE_PASS精确子范围 | 仍缺/历史复用界限 |
| --- | --- | --- | --- |
| ALG-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-02 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-03 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-05 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-06 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-07 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-08 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-09 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-10 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-11 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| ALG-12 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：109请求中6篇真实提交/任务成功/详情/检索/失效；Chrome另2篇闭环。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-02 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome128字符ID/重复409；非法ID本地阻止POST；不覆盖原文。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-03 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome16067码点Unicode原文保存；20001码点被本地阻止，无服务端边界外推。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：本轮专属/other渠道过滤；误操作省略渠道空查询真实5命中另列FAIL；不据UI留空推服务端显式空白合法。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-05 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome Top-K0/1.5本地无POST，列表41篇20条分页；严格JSON类型/分页溢出仅隔离。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-06 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：失效后新检索排除A、专属渠道过滤；数学低分资格/双版本只在隔离验证。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-07 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：109聚合与Chrome关联任务/详情/列表、GET不新增计数；8篇最终ledger一致。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-08 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：109有未找到404与2次失效、Chrome取消0PUT/确认1PUT；幂等字段以Owner聚合为据。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-09 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：真实同源JSON六接口闭环；非法JSON/媒体/实际chunked边界本轮仅隔离，未记在线。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-10 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：生产400以外的409/404可操作反馈；自然DB故障与脱敏失败分类在隔离，不造生产故障。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-11 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：实际5次HTTP搜索ledger+Chrome各次最终hits；五个误命中各+1可精确解释，原strict保持FAIL。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| API-12 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome空查询0分/HTTP渠道真实负分-0.0501；空库/数学向量只在隔离。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome202与首GET真实RUNNING→SUCCEEDED，异步表象实测；事务外CPU/领取锁只在隔离。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-02 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：真实RUNNING/SUCCEEDED及成功文档关联；生产FAILED未触发，失败可查/后续继续为隔离。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-03 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-05 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-06 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-07 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-08 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-09 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| TASK-10 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-02 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-03 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-05 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | ONLINE_PASS：Chrome确认失效后新空查询仅返回另一有效文档。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-06 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-07 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-08 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-09 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| CON-10 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 新副本升级兼容及Mac/Linux35分钟/重启另有实际子证据。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-02 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-03 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-05 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-06 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-07 | ISOLATED_PASS：REC-07-R1两方法；原判据历史FAIL保留。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| REC-08 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 新副本升级兼容及Mac/Linux35分钟/重启另有实际子证据。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| UI-01 | 无新增隔离联合结论。 | ONLINE_PASS：真实分数/顺序/状态/计数快照，未造语义概率。 | 联合范围不据几个截图全通过；向量内部字节只在隔离。 |
| UI-02 | 无新增隔离联合结论。 | ONLINE_PASS：128ID/16067原文/重复409/非法ID/20001/Top-K0/1.5/channel65；400/429正式UI不完整。 | 正式服务全部字段400/429/UI输入保留未完整重演；NOT_RUN范围保留，旧受控证据仅复用。 |
| UI-03 | 无新增隔离联合结论。 | ONLINE_PASS：真实RUNNING→SUCCEEDED；响应到下次GET实测2.004228秒；终态停止。 | 正式浏览器3任务/单在途/60秒墙钟/非终态隐藏恢复/手动不重置、QUEUED画面仍NOT_RUN；自然FAILED为F05原有限例外，非新例外。 |
| UI-04 | 无新增隔离联合结论。 | ONLINE_PASS：失效取消0PUT/确认1PUT，历史2行保留，任务/ready/invalidated正交。 | 失效服务端失败/未知的正式浏览器路径未重新故障注入，NOT_RUN。 |
| UI-05 | 无新增隔离联合结论。 | ONLINE_PASS：关闭迟到202不重开；详情A取消后B保留；客户端Fetch断网搜索不重试、旧结果保留。 | 被取消A未声称旧体送达；客户端网络失败不冒充数据库提交未知。 |
| UI-06 | 无新增隔离联合结论。 | ONLINE_PASS：原文/片段XSS纯文本；截图可见标签文字，主控DOM实测0img/script。 | 恶意服务端错误文案本轮正式浏览器NOT_RUN，历史受控错误DOM仅REUSED_EVIDENCE。 |
| UI-07 | 无新增隔离联合结论。 | ONLINE_PASS：主控1440/768/390/320、原生Chrome200%、Esc/焦点/Home/Right/End、128ID复制；QA只读截图抽核。 | 真读屏/物理手机NOT_RUN；视口与DPR不替代。 |
| UI-08 | 无新增隔离联合结论。 | ONLINE_PASS：旧2764/b8b正式HTTPS提交/任务/列表/详情/检索/失效闭环；尚非988新JAR线上。 | 988新候选未部署/重验；不以相同静态资源推定新JAR完整线上PASS。 |
| OPS-01 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| OPS-02 | 无新增隔离联合结论。 | ONLINE_PASS：17:26:52及17:42:39Owner回执active/enabled/NRestarts0/唯一旧生产持有/READY/锁inode；本轮未重启。 | 未把隔离/组件覆盖冒充生产内部断言；完整方法见下方历史执行映射。 |
| OPS-03 | 无新增隔离联合结论。 | NOT_RUN：未据本轮生产证明该联合范围。 | REUSED_EVIDENCE：旧生产冷备/恢复；本轮新副本升级属隔离，不是本轮生产备份演练。 |
| OPS-04 | 无新增隔离联合结论。 | ONLINE_PASS：109原聚合的未认证主页/列表401及health/H2 404；另11请求补证六业务无认证401×6、错误Basic401、跨源提交/搜索403×2、actuator404及loopback健康200。 | 补11请求不混入109；0新文档/0成功业务搜索/0重试；仅所列实际路由，不扩张到任意管理路径或其他认证组合。 |
| OPS-05 | 无新增隔离联合结论。 | NOT_RUN：未据本轮生产证明该联合范围。 | 本轮完整公开范围/五报告交叉审查正在收尾；不宣称无限秘密扫描完备。 |
| OPS-06 | 无新增隔离联合结论。 | ONLINE_PASS：Owner末次实读运行2764/b8b与新基线字段；988/becb尚未批准部署，不写实际新候选发布PASS。 | 实际新修复候选部署及发布后复验NOT_RUN；旧2.4不可自动打开2.5库作回退。 |
| PERF-01 | 无新增隔离联合结论。 | NOT_RUN：未据本轮生产证明该联合范围。 | REUSED_EVIDENCE：旧有限负载报告；本轮不施压，不报告新QPS/容量/Worker/SLA。 |
| PERF-02 | 无新增隔离联合结论。 | NOT_RUN：未据本轮生产证明该联合范围。 | REUSED_EVIDENCE：旧有限负载报告；本轮不施压，不报告新QPS/容量/Worker/SLA。 |
| PERF-03 | 无新增隔离联合结论。 | NOT_RUN：未据本轮生产证明该联合范围。 | REUSED_EVIDENCE：旧有限负载报告；本轮不施压，不报告新QPS/容量/Worker/SLA。 |
| PERF-04 | ISOLATED_PASS：本轮XML对应原表方法；新CI同法通过。 | NOT_RUN：未据本轮生产证明该联合范围。 | 无标签语言观察，不报语义准确率。 |

### 原FAIL、五命中与用户新基线分别核证

QA只读核对误请求为一次`text=""/topK=5`且省略channel，真实五hits的ID集合与Owner五行ledger完全相同、每行delta均1；旧3123为2→3、4123为1→2。原strict旧33不变确为FAIL，其他旧31篇摘要字段不变。完整请求/摘要原文不提交公开Git。

用户直接原话“允许新基线重验，永久保留原FAIL”单独存证。新baseline封存33个API摘要，实际hash与最终receipt引用完全匹配；17:26:52首次及全部补证后17:42:39最终Owner只读末检`differences=[]`、8篇计数/失效符合包含误操作的完整账本。QA核安全回执与hash链，未读取活动H2，不声称线上全量vector字节比较；原FAIL未回填，也未减计数。

| 安全原件逻辑名 | QA实际计算SHA-256 |
| --- | --- |
| Linux新包35分钟安全回执 | `759c08571749fdf1d7aa41115742f426cc7562e2574bc5aaf97fddbbe9b52bd3` |
| 原strict FAIL | `f9d331f2a27959b9ae7b440216822fa23c4d6b69072be19af9076ea37f7e8def` |
| 五命中ledger | `8ebb67b8453799f0ea553669f1112335703d8c5fef4f7921d15c5426344a25fd` |
| 用户裁决新baseline | `b61d6e99429ab35f4e89d2f2c24e03ef6ac231db374f6845ae3bd79c80bc8da0` |
| 新baseline首次receipt | `80658cb05d71dec84f760016d22ccb9cc32420dff4b85e8a8173b8aab4c3d105` |
| 全部测试后最终receipt | `4296db5cf1b0687c03b290b309d6340ea47132eda5a1528464aa15d24c0dc11e` |
| 109请求安全聚合 | `e0b167e2eaeea92cae1aa0e3ab96eef1b001a86d841810e10b3348132f958a82` |
| 入口拒绝11请求独立补证 | `3b2db13f203d6cfe43196dc018fbd531467f52a8dfc15e9daab74331652b7129` |
| 七资源与条件请求安全聚合 | `953da189cb250a606d7ec778f2b1c40b4a2c42824479c3739688c95d7faf5ac0` |

安全聚合的109状态分布200×99、401×2、404×2、202×6，方法GET96/POST11/PUT2；6个唯一成功提交、5search、2PUT。原109不能推出403或全部六接口无认证拒绝；随后单列11补证才有这些真实回执。补证安全聚合经QA逐条核expected=actual，六401、错误Basic401、两403、actuator404与一次loopback健康200；写入/搜索成功均0、无重试，额外预算当时28/32。额外资源10次只读、首次累计17/32、补11请求至28/32、最后1次list至29/32均单列，未混入109。最终receipt的8篇hitCount/invalidated还由QA与17:26:52回执逐ID对比完全相同；已停止生产HTTP测试，生产版本未变。

## 首次及中期独立核证历史快照（不作为当前状态）

下方保留首次与中期写成时的NOT_RUN/RUNNING及ONLINE=0；当前结论以上方最新快照为准，旧失败/原执行时间不回写。


日期：2026-10-23。QA独立分支`codex/final-acceptance-qa`，受测源码`46ebe7d9b29a616721a66a10c9ce65d89fc2e33a`。本轮生产候选仍为2764eddf34418fdcc8ed666e36937673c7feadb8 / b8b67460ee6af26ec5ea7854377b63919c18c37760b11e3fbf49f7672be7f45f / CI39915764822。没有改生产Java/API/DDL/算法/资源参数，没有发生产请求、浏览器或压力，也没有重启生产。

**总门禁：BLOCKED。** 已知本地1500.13秒提交503的真实FAIL仍开放；根因核证和实际候选连续35分钟稳定性没有新的真实PASS前，不给FINAL_ACCEPT。282项短程工程通过不能替代该门禁。

## 来源、状态及统计口径

原题PDF三页已实际只读提取；用户原始70条来自主项目`docs/测试工作/02_测试用例与验收方案.md`及`04_结构化测试用例.json`，原件只读保留，不复制入公开Git。公开追溯以`final-release-local-cases-matrix.md`70个唯一ID为索引，标题取`ONLINE-FUNCTIONAL-REPORT.md`。ALG12/API12/TASK10/CON10/REC8/UI8/OPS6/PERF4共70。

REC-07采用`docs/design/REC-07-验收判据裁决-20261023.md`的REC-07-R1；原预期/历史FAIL不改。F05原有限例外仅针对旧候选真实FAILED Chrome分支；本轮不造终态，不记PASS，也不扩展到新503。

- ONLINE_PASS：本轮实际生产/浏览器回执覆盖的精确子范围；尚未收到回执时不得先填。
- ISOLATED_PASS：本轮XML对应方法实际成功；只证明列出的本机隔离断言。一个方法可映射多个需求，执行数不重复累加。
- REUSED_EVIDENCE：仅复用历史版本和范围明确的证据，不是本轮ONLINE/ISOLATED执行。
- NOT_RUN：该联合范围尚缺本轮实际证据，即使已有子层成功也不当全项通过。
- FAIL：实际违反本轮断言；已知稳定性FAIL另列总门禁，不因70映射的历史复用而消失。

本快照状态计数：ISOLATED_PASS=54、NOT_RUN=10、REUSED_EVIDENCE=6；ONLINE_PASS=0。上述是证据层分布，不是70条全部最终验收通过。

工程执行明细见`API-FINAL-VERIFICATION.md`。首次沙箱回环绑定7个启动errors原件保留；允许随机本机回环端口后完整重跑104 Unit+126 IT成功，Node52成功；最终唯一执行282项。首轮未进入Failsafe，不将两轮或参数样本重复相加。

## 逐条执行与缺口

| ID | 需求摘要 | 本轮证据状态 | 实际方法/证据 | 细分边界与仍缺范围 |
| --- | --- | --- | --- | --- |
| ALG-01 | 256维、返回类型与有限数值 | ISOLATED_PASS | `B1AlgorithmIT#matchesIndependentlyGeneratedGoldenBytes`（XML匹配8项）；`CharNgramTextEmbedderTest#singleChineseAndSupplementaryCodePointsProduceFiniteUnitVectors`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-02 | 重复调用按位相同且结果不共享可变数组 | ISOLATED_PASS | `B1AlgorithmIT#sharedInstanceIsDeterministicUnderControlledConcurrencyAndReturnsFreshArrays`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-03 | 混合文本并发向量化无串扰 | ISOLATED_PASS | `B1AlgorithmIT#sharedInstanceIsDeterministicUnderControlledConcurrencyAndReturnsFreshArrays`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-04 | 独立JVM、默认Locale及固定环境的确定性 | ISOLATED_PASS | `B1AlgorithmIT#separateJvmReportsSameVersionDigestAndEveryEncodedByte`（XML匹配1项）；`B1AlgorithmIT#unicodeBoundariesExpansionAndDefaultLocaleFollowContract`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-05 | 空字符串和纯空白产生零向量 | ISOLATED_PASS | `CharNgramTextEmbedderTest#emptyAndUnicodeWhitespaceProduceFreshPositiveZeroVectors`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-06 | 规范化等价对与原文不改写 | ISOLATED_PASS | `CharNgramTextEmbedderTest#nfkcRootCaseAndWhitespaceRulesMatchCanonicalText`（XML匹配1项）；`LocalCasesIT#四组工程等价对按位一致且各自原文真实落库`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-07 | Unicode与n-gram边界 | ISOLATED_PASS | `B1AlgorithmIT#unicodeBoundariesExpansionAndDefaultLocaleFollowContract`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-08 | 向量二进制codec、字节序和损坏输入 | ISOLATED_PASS | `B1AlgorithmIT#codecPreservesRawFiniteBitsAndBigEndianWithIndependentReference`（XML匹配1项）；`LocalCasesIT#数学向量经真实H2往返保持每个原始位`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-09 | 余弦数学oracle含零、负值和非单位向量 | ISOLATED_PASS | `B1AlgorithmIT#cosineHandlesNonUnitExtremeZeroOppositeAndInvalidVectors`（XML匹配1项）；`LocalCasesIT#六组余弦与五组TopK原始数学夹具逐条断言`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-10 | Top-K固定oracle：负分、同分和K边界 | ISOLATED_PASS | `B1AlgorithmIT#topKMatchesIndependentFullSortAcrossShufflesTiesAndKBoundaries`（XML匹配1项）；`LocalCasesIT#六组余弦与五组TopK原始数学夹具逐条断言`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-11 | Top-K随机对照与近似同分 | ISOLATED_PASS | `B1AlgorithmIT#topKMatchesIndependentFullSortAcrossShufflesTiesAndKBoundaries`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| ALG-12 | 入库/查询同版本、增量稳定与CPU实现审查 | ISOLATED_PASS | `B1AlgorithmIT#productionSpringWiringUsesSingleEmbedderAndConfiguredRounds`（XML匹配1项）；`LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| API-01 | 完整业务闭环与元信息 | ISOLATED_PASS | `LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-02 | documentId校验和重复ID | ISOLATED_PASS | `B2HttpIT#JSON类型空白分页与来源错误保持统一结构且不落库`（XML匹配1项）；`B2AdmissionIT#十二个并发重复ID只接收一次并保留获胜原文`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-03 | text空值、长度与规范化膨胀边界 | ISOLATED_PASS | `B2HttpIT#规范化扩张上限与补充码点通过真实HTTP校验`（XML匹配1项）；`B4HttpIT#原始规范化长度和渠道码点边界在真实HTTP校验`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-04 | 提交与搜索的渠道缺省语义不同 | ISOLATED_PASS | `B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界`（XML匹配1项）；`LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-05 | topK与分页的数值边界 | ISOLATED_PASS | `B2HttpIT#JSON类型空白分页与来源错误保持统一结构且不落库`（XML匹配1项）；`B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-06 | 先过滤后Top-K，不遗漏低分合格候选 | ISOLATED_PASS | `B4SearchIT#状态双版本失效与渠道在TopK之前过滤`（XML匹配1项）；`LocalCasesIT#原始过滤数学夹具先过滤再返回零负分`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-07 | 文档列表、详情和任务关联不改变计数 | ISOLATED_PASS | `B4HttpIT#真HTTP提交真实B1后检索计数详情且snippet按码点`（XML匹配1项）；`B2HttpIT#四态查询字符串大计数排序与失效幂等保持独立`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-08 | 不存在对象与幂等失效 | ISOLATED_PASS | `B2HttpIT#四态查询字符串大计数排序与失效幂等保持独立`（XML匹配1项）；`LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-09 | 请求体、Content-Type与非法JSON | ISOLATED_PASS | `B2HttpIT#默认正文上限按实际chunked字节判断含边界且超限不落库`（XML匹配1项）；`B4HttpIT#搜索JSON参数渠道Origin与实际chunked字节边界`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-10 | 异常结构、安全信息与数据库故障 | ISOLATED_PASS | `B3TerminalIT#失败分类可靠持久化且不泄露异常原文`（XML匹配3项）；`B4SearchIT#坏候选和超容量失败不能计数且许可释放`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-11 | 只给最终结果计数，读取和被过滤候选不计数 | ISOLATED_PASS | `B4SearchIT#成功零负同分无阈值且仅最终TopK计数`（XML匹配1项）；`LocalCasesIT#十六篇合成文档真实HTTP闭环及增量向量不变`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| API-12 | 空库、空查询、零分和负分规则 | ISOLATED_PASS | `B4SearchIT#成功零负同分无阈值且仅最终TopK计数`（XML匹配1项）；`LocalCasesIT#十四条请求夹具按已冻结协议逐条真实HTTP验证`（XML匹配1项） | 真实本机HTTP/隔离持久层与边界已执行；本轮线上回执尚未收到。 |
| TASK-01 | 受控证明异步提交，不靠响应速度猜测 | ISOLATED_PASS | `B2DispatchIT#Worker调用处理器前已提交且无事务连接绑定`（XML匹配1项）；`LocalCasesIT#真实HTTP在计算暂停时已返回接收且不可搜索`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-02 | 四态、计算失败可查且不拖死后续任务 | ISOLATED_PASS | `LocalCasesIT#受控任务失败可查且后续任务继续成功`（XML匹配1项） | 受控处理器故障的真实隔离HTTP/持久层；不替代生产FAILED或F05浏览器。 |
| TASK-03 | 接收两表原子性：插入Document后任务写入失败 | ISOLATED_PASS | `B2AdmissionIT#两表实际写入后异常使整笔接收回滚`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-04 | 持久接收后未派发，扫描能补派 | ISOLATED_PASS | `B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-05 | 执行队列饱和与拒绝后的在途集合清理 | ISOLATED_PASS | `B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-06 | 持久积压与总文档容量分别限制 | ISOLATED_PASS | `B2AdmissionIT#文档总量包含已失败已失效数据而不只计算待处理任务`（XML匹配1项）；`B2AdmissionIT#待处理容量包含RUNNING而失效不释放名额`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-07 | 重复派发不造成重复执行，在途清理可继续工作 | ISOLATED_PASS | `B2DispatchIT#两个独立领取事务仅一个成功且令牌和尝试次数吻合`（XML匹配1项）；`B2DispatchIT#一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-08 | 终态两表写入中间故障不能半成功 | ISOLATED_PASS | `B3TerminalIT#成功两表写入任一步失败都不能半提交`（XML匹配3项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-09 | 失败回写带状态与token条件 | ISOLATED_PASS | `B3TerminalIT#旧令牌成功失败均拒写且本次终态重放不覆盖首次结果`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| TASK-10 | 调度/终态数据库异常必须显式处理 | ISOLATED_PASS | `B2DispatchIT#单次扫描数据库错误不阻止下一次真实扫描`（XML匹配1项）；`B3TerminalIT#明确回滚的暂时错误最多三次且耗尽不伪造FAILED`（XML匹配2项）；`B3LifecycleIT#恢复SQL失败绝不READY并保留RUNNING现场`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-01 | 两个独立事务竞争同一QUEUED领取权 | ISOLATED_PASS | `B2DispatchIT#两个独立领取事务仅一个成功且令牌和尝试次数吻合`（XML匹配1项）；`B2DispatchIT#领取物理提交后抛错经本次令牌确认只执行一次`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-02 | 旧token迟到结果与重复成功提交 | ISOLATED_PASS | `B3TerminalIT#旧令牌成功失败均拒写且本次终态重放不覆盖首次结果`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-03 | 计算中失效，完成不复活 | ISOLATED_PASS | `B3TerminalIT#计算暂停期间失效完成后成功回写不复活或覆盖计数`（XML匹配1项）；`B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-04 | 在途检索与失效的重叠语义 | ISOLATED_PASS | `B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-05 | 失效已经完成后开始的新检索必须排除 | ISOLATED_PASS | `B4SearchIT#快照之后失效仍返回并计数且CPU不占单连接池`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-06 | 多次并发命中同文档无丢失更新 | ISOLATED_PASS | `B4CountAndResourceIT#八路实际搜索多轮并发只对最终热点原子累加`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-07 | 多个结果的计数锁顺序与事务完整性 | ISOLATED_PASS | `B4CountAndResourceIT#八路实际搜索多轮并发只对最终热点原子累加`（XML匹配1项）；`SearchHitCountServiceTest#仅最终ID按ASCII顺序自增且不覆盖失效或向量字段`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-08 | 计数第二条写失败，整次统计回滚 | ISOLATED_PASS | `B4CountAndResourceIT#最终命中多行事务任何失败整体回滚`（XML匹配4项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-09 | 接收锁必须覆盖物理提交 | ISOLATED_PASS | `B2AdmissionIT#第二线程真实阻塞于接收锁且事务开启晚于首笔物理提交`（XML匹配1项）；`B2AdmissionIT#调用者外层事务被明确拒绝且不会借用其提交`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| CON-10 | CPU计算不持有长事务，检索门禁可释放 | ISOLATED_PASS | `B4CountAndResourceIT#CPU暂停时无事务连接关闭后迟到搜索零SQL且不计数`（XML匹配1项）；`B2DispatchIT#Worker调用处理器前已提交且无事务连接绑定`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-01 | 正常停止重启，全部业务数据保留 | ISOLATED_PASS | `BackupRestoreIT#真实B1检索失效正常关闭冷复制校验后独立目录恢复保持全部状态`（XML匹配1项）；`C0BootstrapIT#realHttpHealthDefaultsAndCompleteApplicationRestartPreserveBothTables`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-02 | 启动恢复四态与实际入口门禁 | ISOLATED_PASS | `B3LifecycleIT#恢复真实提交前拒接不派发且恢复后真实B1完成保持失效计数`（XML匹配1项）；`LocalCasesIT#四态文件库重启只恢复未完成任务`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-03 | 真实进程崩溃：接收提交后尚未入队 | ISOLATED_PASS | `B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做`（XML匹配4项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-04 | 真实进程崩溃：领取后计算中 | ISOLATED_PASS | `B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做`（XML匹配4项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-05 | 真实进程崩溃：终态提交前与提交后 | ISOLATED_PASS | `B3ProcessIT#四检查点真实强杀后同库恢复真实B1且已成功不重做`（XML匹配4项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-06 | 提交结果未知：先提交再抛错的三条路径 | ISOLATED_PASS | `B2DispatchIT#领取物理提交后抛错经本次令牌确认只执行一次`（XML匹配1项）；`B3TerminalIT#真提交后抛错必须核对完整终态向量且不能反写FAILED`（XML匹配3项）；`B4CountAndResourceIT#提交未知不读取累计数不重放且实际增量与真实提交一致`（XML匹配2项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| REC-07 | 算法版本不兼容不混算 | ISOLATED_PASS | `Rec07RevisedIT#R32真实旧库经R33顺序重启全字段不变且当前业务可用`（XML匹配1项）；`Rec07RevisedIT#文档任务双版本四组合在TopK前过滤且只计合格行`（XML匹配1项） | 采用已批准REC-07-R1；旧LocalCasesIT诊断也执行但原判据历史FAIL永久保留。 |
| REC-08 | 存储和初始化失败，以及损坏向量诊断 | ISOLATED_PASS | `C0BootstrapIT#invalidProductionConfigurationPreventsStartup`（XML匹配3项）；`B4SearchIT#坏候选和超容量失败不能计数且许可释放`（XML匹配1项）；`LocalCasesIT#无效存储路径启动失败且不偷偷换空库`（XML匹配1项）；`LocalCasesIT#只读临时目录真实拒绝启动且恢复权限后原路径可用`（XML匹配1项）；`LocalCasesIT#测试资源非法DDL真实初始化失败且恢复默认DDL后可用`（XML匹配1项） | 本轮实际执行上述隔离断言；不表示生产本轮已测。 |
| UI-01 | 展示真实状态、排序与分数，不制造AI指标 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-02 | 提交表单边界、缺省渠道和重复ID反馈 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-03 | 任务轮询停止、网络错误不伪造FAILED | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 F05真实FAILED分支仍NOT_RUN；旧有限例外不外推本轮。 |
| UI-04 | 失效确认、失败反馈及历史搜索结果 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-05 | 搜索不自动重试，旧响应不覆盖新结果 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-06 | 原文与错误文本不执行HTML/脚本 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-07 | 错误可操作、键盘、小屏和时间展示 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| UI-08 | 服务器上的真实页面端到端闭环 | NOT_RUN | 本轮52项Node已执行，仅为模块证据；本轮浏览器回执尚未收到 | 主控实际浏览器后逐子项更新；历史UI-RELEASE不充当本轮ONLINE_PASS。 |
| OPS-01 | 干净构建与测试实际执行 | ISOLATED_PASS | 本轮Java17完整clean verify、Node22全量；`BackupRestoreIT`及`C0BootstrapIT`实际执行 | 工程构建/隔离启动范围；不代表本轮Linux CI或生产重启。 |
| OPS-02 | 服务器数据目录、进程与正常重启 | REUSED_EVIDENCE | `UI-RELEASE-verification.md`的2764/b8b发布、独立恢复26篇及重启保持33篇/任务 | 历史复用；本轮未停启生产、未重新冷备恢复/回退；等待Owner只读新回执。 |
| OPS-03 | 备份恢复实际演练，不只生成文件 | REUSED_EVIDENCE | `UI-RELEASE-verification.md`的2764/b8b发布、独立恢复26篇及重启保持33篇/任务 | 历史复用；本轮未停启生产、未重新冷备恢复/回退；等待Owner只读新回执。 |
| OPS-04 | 公网访问保护覆盖页面和API | NOT_RUN | 隔离`ProxyOriginIT`3项及新工程回归已执行；本轮服务器入口回执未收到 | 127.0.0.2不可绑定，非可信来源socket未实测，使用真实RemoteIpValve组件；不算本轮公网认证。 |
| OPS-05 | Git、AI记录与交付材料的真实性和脱敏 | NOT_RUN | 已核对本树基线及与2764候选src/main/pom/Wrapper差异为空 | 本轮完整公开范围/历史/最终报告交叉审查尚未完成。 |
| OPS-06 | 发布与回滚对应同一可追溯版本 | REUSED_EVIDENCE | `UI-RELEASE-verification.md`的2764/b8b发布、独立恢复26篇及重启保持33篇/任务 | 历史复用；本轮未停启生产、未重新冷备恢复/回退；等待Owner只读新回执。 |
| PERF-01 | 纯入库与CPU模拟负载测量 | REUSED_EVIDENCE | `PERFORMANCE-BASELINE.md`、`CONCURRENCY-REPORT.md`及历史隔离性能报告 | 本轮不施压；旧有限规模不推广新容量/Worker/SLA。 |
| PERF-02 | 纯检索规模、并发与混合负载 | REUSED_EVIDENCE | `PERFORMANCE-BASELINE.md`、`CONCURRENCY-REPORT.md`及历史隔离性能报告 | 本轮不施压；旧有限规模不推广新容量/Worker/SLA。 |
| PERF-03 | 热点计数与资源耗尽后的恢复 | REUSED_EVIDENCE | `PERFORMANCE-BASELINE.md`、`CONCURRENCY-REPORT.md`及历史隔离性能报告 | 本轮不施压；旧有限规模不推广新容量/Worker/SLA。 本地1500.13秒503稳定性FAIL未结案；本行历史复用不能解除BLOCKED。 |
| PERF-04 | 相关性实验：与功能正确性验收分开 | ISOLATED_PASS | `LocalCasesIT#八组语言关系记录真实Java分数不预设语义高低`（XML匹配1项） | 按原允许的无标签观察范围，不报告准确率/Recall/NDCG或语义门槛。 |

## 新候选与用户新数据基线裁决追加

本表54/6/10的原快照对应QA实际46ebe7d执行，后续不把它改写成9883400实跑。新候选9883400/becb3b46经Owner执行283项、QA独立核本地XML/CI日志，详见API-FINAL-VERIFICATION追加。修复仅H2属性，生产仍2764/b8b，未批准替换。

新增旧2.4副本升级/正常重启兼容已Owner实跑、QA独立核原始快照，作为REC-01、REC-08与OPS-03的明确隔离子证据追加：22文档/22任务全部字段及vector原始bytes三快照完全一致，114请求、0写重试、1重启、新2文档持久化。不把该子范围推广为生产恢复或整项本轮ONLINE_PASS，不自动变更旧表分类。

原strict“旧33完全不变”因一次UI无渠道空查询命中五行而FAIL，永久保留；五hits+1真实ledger尚待QA原件复核。用户直接裁决“允许新基线重验，永久保留原FAIL”，允许封存新baseline后独立重验，不回改原断言、不扣计数、不自行增加例外。新baseline末次逐字段比对尚NOT_RUN；此后结果须与原FAIL并列，不把重新取基线本身当PASS。

Mac/Linux35分钟及其后重启尚RUNNING，当前FINAL_ACCEPT继续BLOCKED。
