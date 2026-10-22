# FE1 管理入口与受控任务轮询交接

- 任务：FE1；分支 `agent/frontend`；基线 `540bb4592fbedaa6a850ebf929a02a6529cfd885`。
- 提交：以本交接文件所在提交的 `git log -1 --format=%H` 为准，实际 SHA 随角色结果回报。
- 范围：原生静态页面、原生 Node 测试；没有改 Java、B1、SQL、pom、公共DTO、API/运行契约或部署配置，没有引入依赖。

## 修改文件与交付

| 文件 | 用途 |
| --- | --- |
| `src/main/resources/static/index.html` | 三Tab表单、文档筛选与分页列表、详情dialog、任务与本页会话区域、阶段说明 |
| `src/main/resources/static/styles.css` | 延续C0视觉，补表单、错误、滚动表格、详情与窄屏布局 |
| `src/main/resources/static/app.js` | 真实请求与安全DOM渲染、旧读响应保护、失效确认、有限任务跟踪、健康检查 |
| `src/main/resources/static/api.mjs` | 输入校验、渠道省略、同源请求、响应边界、错误/未知结果、UTC+8展示、读请求代次 |
| `src/main/resources/static/tasks.mjs` | 可注入时钟的任务轮询控制，不包含写请求重试 |
| `tests/frontend/api.test.mjs` | 请求/参数/未知结果/响应结构与显示边界的原生Node测试 |
| `tests/frontend/tasks.test.mjs` | 受控时钟与受控Promise验证轮询、停止、退避和迟到响应 |
| 本交接文档 | 范围、证据和待集成项 |

## 已实现行为

- 管理操作仅调用真实同源五个文档/任务API，不预填结果，不制造任务、时间、进度或成功状态。接口未合入当前服务时如实显示404/网络等错误。
- 明确B3未启用时接收记录保持QUEUED；不因等待60秒或网络失败把任务改成FAILED。只有服务端返回的四态才用于业务状态。
- 搜索表单默认展示，按钮禁用并解释B4边界；隐式提交也被阻止。没有搜索请求函数，不声称已完成查询/命中计数闭环。
- 空字符串原文合法，不裁剪或规范化。ID严格按冻结字符规则且不去空白；原始文本以code point计数并拒绝不完整代理字符，规范化长度仍由后端校验。
- UI空渠道省略：提交default、列表全部；非空渠道按Java冻结空白集合去首尾空白，保留U+0085和U+FEFF。后端显式空白400规则没有被改写。分页输入上限与Integer.MAX_VALUE一致。
- 提交只认真实202及合法taskId，不自动重试、不自动换ID。网络/超时/格式异常提示可能已接收，使用本次提交时捕获的文档ID手动查询，不误用之后改动的表单值。
- 列表显示读取快照，改变筛选需重新应用；翻页沿用已应用筛选；不自动刷新统计。详情原文使用textContent，空文显示为空；hitCount全程保留十进制字符串，不经过Number。
- 所有时间按Asia/Shanghai显示，页面标UTC+8；缺失时间显示“—”。详情与列表没有搜索调用。
- 失效前确认，等待成功响应后才更新详情有效状态；失败保留原状态，未知结果提示刷新确认。旧列表保留并标历史快照，不本地删除/重排伪装新查询，不自动检索。
- 任务自动刷新最多3个，响应完成后2秒再安排下一次；首次启用起60秒墙钟预算，不因手动刷新或隐藏/恢复重置。终态停止，隐藏暂停，返回可见后才重新读取；预算耗尽仍允许单次手动查询。
- GET网络/5xx采用有限退避，连续3次读取失败后暂停；404等不重试。同任务只有一个在途请求；取消及迟到响应不能伪造服务端回滚或覆盖新选择。
- 本页会话最多20个真实返回/查询ID；刷新页面清空，不使用localStorage/sessionStorage保存文档或凭据。淘汰记录会停止自动刷新。
- 所有新维护注释均为简体中文；原C0 static未发现需独立整改的英文注释，没有制造无内容的翻译提交。

## 实际验证

环境：Node `v22.16.0`。在独立frontend工作树执行：

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/api.mjs
node --check src/main/resources/static/tasks.mjs
node --test tests/frontend/*.test.mjs
git diff --check
```

结果：三个语法检查退出0；Node测试25个，25通过、0失败/跳过/取消；差异空白检查通过。

另外用Python标准库HTMLParser检查：HTML ID无重复，Tab/label/ARIA引用都存在，三个Tab仅一个初始激活，搜索按钮禁用，本地资源存在；静态检索未发现innerHTML、insertAdjacentHTML、外部CDN、存储原文/凭据或setInterval。检查通过。测试仅使用注入的受控fetch/时钟，不向正式页面加入mock。

## 未执行与待集成

- **未完成浏览器验证**。未宣称UI01—18全部通过；DOM布局、窄屏、键盘、dialog焦点/关闭和恶意原文实际执行边界，待主控浏览器验证。
- 未在此分支启动Java服务或调用真实管理API。后端B2尚在实施，五接口合入后需按真实HTTP验证空文、渠道、409/429、分页、详情、任务和幂等失效。
- Java17完整`./mvnw -B clean verify`、最终JAR资源和浏览器同源联调由主控阶段合入统一执行，本次仅执行覆盖修改的前端测试；请确认`.mjs`由Spring静态资源正确返回JavaScript媒体类型。
- B3终态/恢复未集成，真实QUEUED→RUNNING→终态及任务/失效竞态仍待后续，不把Node受控响应当后端实现证据。
- B4未启用；真实搜索、排序、负分/同分、计数与失效后手动重搜尚未实现/验证。
- 未部署或远程推送；健康响应只能说明基础状态，不能代替业务就绪或发布验收。

## 主控审查重点

按API契约核对api.mjs字段/空白规则、tasks.mjs的预算与取消边界、app.js提交未知结果与失效后的历史快照。集成后先验证模块加载与管理API实际响应，再做失败注入/真实浏览器回归；禁止为了演示完成而在生产启用受控任务处理器或搜索mock。
