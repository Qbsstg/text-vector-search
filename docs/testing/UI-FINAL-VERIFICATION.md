# 新版前端真实浏览器最终验收

2026-10-23，主控实际操作正式 HTTPS 页面，使用本机 Google Chrome。**已执行范围通过，完整 UI 门禁尚未关闭**；下方未执行的非终态轮询范围不算浏览器 PASS。F05 保持 `NOT_RUN / APPROVED_EXCEPTION`。

本轮线上应用源码 `2764eddf34418fdcc8ed666e36937673c7feadb8`，JAR SHA-256 `b8b67460ee6af26ec5ea7854377b63919c18c37760b11e3fbf49f7672be7f45f`，发布 CI39915764822。修复候选9883400的前端资源与此版本逐字节一致，但尚未部署，以下不是新JAR线上验收。

## 执行边界与证据

HTTP Owner新增6篇，Chrome新增2篇，合计严格预算8篇，无删除、覆盖或生产重启。Chrome两篇同16067 Unicode码点原文，包含HTML/script、补充字符、全角字符和换行；第二篇ID为128字符。合法检索限定本轮专用渠道；下方误操作单列保留。

截图、脱敏网络事件、真实任务响应、误操作原请求和响应仅保留授权本地，逻辑目录 `final-acceptance-20261023/browser`，不自动复制到公开Git。网络封存去除Authorization/Cookie；旧标签与续测标签按来源区分，不把跨标签sequence当全局唯一号。工具选择器超时和被取消请求的拦截ID失效不当作应用失败，也不因此重发写请求。

## 本轮正式Chrome实测

| 编号 | 实际操作及断言 | 结果 |
| --- | --- | --- |
| UFA-01 | 首次无缓存加载，同源HTML/JS/CSS/模块正常，初始不自动调用业务API | ONLINE_PASS |
| UFA-02 | Top-K=0、1.5，渠道65码点，本地错误阻止POST | ONLINE_PASS |
| UFA-03 | 非法ID、20001码点原文拒绝，焦点进入错误字段，不裁剪输入 | ONLINE_PASS |
| UFA-04 | 两篇各一笔POST返回202及真实taskId；重复A为409，不换ID或重提 | ONLINE_PASS |
| UFA-05 | 浏览器侧延迟202，关闭提交弹窗并切页后释放，不重开或抢焦点 | ONLINE_PASS |
| UFA-06 | B真实GET首次RUNNING，后SUCCEEDED；前响应后约2.004秒再GET，显示服务器时间 | ONLINE_PASS |
| UFA-07 | 成功任务停止自动刷新，当前任务与会话展示真实终态 | ONLINE_PASS |
| UFA-08 | 长原文详情逐字核对，img/script节点0，无JS弹窗，可滚动阅读 | ONLINE_PASS |
| UFA-09 | 128字符ID真实复制到剪贴板，逐字核对相同 | ONLINE_PASS |
| UFA-10 | 同原文搜索两项1.0000按ID排序，片段纯文本 | ONLINE_PASS |
| UFA-11 | 合法空查询保留两个0.0000，并各计一次 | ONLINE_PASS |
| UFA-12 | HTTP专用渠道真实分数1.0000/0.0000/-0.0501，B/Z/C顺序，各计一次 | ONLINE_PASS |
| UFA-13 | 点击/刷新详情不POST搜索，GET前后计数相同 | ONLINE_PASS |
| UFA-14 | 延迟A详情，Esc后立即开B，A被取消，B不被旧内容覆盖 | ONLINE_PASS |
| UFA-15 | 失效取消0PUT，再次确认1PUT，成功/就绪/失效正交 | ONLINE_PASS |
| UFA-16 | 失效后原搜索两行保留且标历史，不自动重搜；新空查询仅B | ONLINE_PASS |
| UFA-17 | 渠道/失效筛选返回A；全列表41篇，20条分页前后切换正常 | ONLINE_PASS |
| UFA-18 | 搜索浏览器侧ConnectionReset后提示未知、不重试；守卫拒绝无渠道出网 | ONLINE_PASS（受控客户端网络故障） |
| UFA-19 | 已成功任务手动GET被浏览器侧断网，保留SUCCEEDED，不误报FAILED | ONLINE_PASS（受控客户端网络故障） |
| UFA-20 | 1440/768/390/320 CSS像素无全局横溢，长文和表格局部滚动 | ONLINE_PASS |
| UFA-21 | 320像素新增弹窗目视核对，提交/取消在视口内可达，正文可滚动 | ONLINE_PASS |
| UFA-22 | 原生Chrome控件200%，DPR2→4、布局2560→1280，弹窗可操作；结束恢复100% | ONLINE_PASS |
| UFA-23 | Tab、3px可见焦点、Home/右箭头/End页签、Esc及恢复触发按钮焦点 | ONLINE_PASS |
| UFA-24 | 启用缓存普通刷新，同源七主资源及favicon均200，模块成功、0API，弹窗/页签正常 | ONLINE_PASS |
| UFA-25 | 本轮Console实读error/warn均0，客户端断网有明确提示 | ONLINE_PASS |
| UFA-26 | 普通刷新后会话任务清空，页面明确本页范围，不保存原文或凭据 | ONLINE_PASS |

任务迁移与请求计数来自真实CDP事件及响应；不把请求未完成时的旧DOM当最终结果。普通缓存刷新未用强制刷新掩盖问题。服务器另核七资源与运行JAR字节相同，三个条件请求均200返回当前app.js；这是独立HTTPS证据。

## 未执行范围与历史复用

| 范围 | 状态与边界 |
| --- | --- |
| QUEUED实时画面 | NOT_RUN；首次B GET已RUNNING，不伪造排队画面 |
| 最多3自动任务、单在途、60秒墙钟、手动不重置 | NOT_RUN（正式浏览器）；Node对应调度断言仅隔离通过，不冒充正式浏览器 |
| 非终态期间隐藏/恢复 | NOT_RUN；未在生产暂停后台或伪造状态制造场景 |
| 自然FAILED浏览器分支 | NOT_RUN / APPROVED_EXCEPTION（F05），不扩展到其他缺口 |
| 恶意错误字符串的正式服务响应 | NOT_RUN；原文/片段XSS已实测，历史受控错误DOM仅REUSED_EVIDENCE |
| 旧版缓存预置完整复现 | REUSED_EVIDENCE；旧混用FAIL及修复保留，本轮未植入旧资源，普通缓存和条件请求已重验 |
| 真读屏、物理手机 | NOT_RUN；视口模拟不能替代 |

## 永久保留误操作FAIL与新基线重验

曾误发一次无渠道空查询，五个命中各+1；旧3123计数2→3、4123为1→2，其余旧31篇摘要字段不变。原“旧33全部不变”断言永久FAIL，不回写数据库减计数，也不以合法业务规则将该断言改PASS。

用户明确允许“新基线重验，永久保留原FAIL”。17:26:52只读回执按新快照核旧33零额外差异；后续安全拒绝补证结束后，17:42:39再次list核旧33仍零额外差异，本轮8篇计数和失效均符合真实操作账本。原FAIL、五命中账本、新快照与两次新PASS分别保存；裁决不批准新生产版本，也不增加UI验收例外。
