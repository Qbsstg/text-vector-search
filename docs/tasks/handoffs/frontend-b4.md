# FE2/FE3：B4 搜索接入交接

- 日期：2026-10-23。
- 分支/工作树：`agent/frontend` / `.worktrees/frontend`。
- 基线：干净工作树从`dd96021a5dcc49acb31b4639eb4d1ae9ed6e4054`快进至主控指定`3f5764e43674ef5e8c3e5288f3967f5da4ad5e5c`后开始修改。
- 提交：实际SHA随角色交付回报；`git log -1 --format=%H`可核对本交接所在提交。

## 修改与所有权

| 文件 | 本轮变更 |
| --- | --- |
| `src/main/resources/static/api.mjs` | 搜索参数与响应校验、真实POST、null渠道按协议省略、搜索错误/未知计数说明 |
| `src/main/resources/static/search.mjs` | 新增单次搜索状态控制，参数快照、防重入、实际往返耗时、条件/失效历史标记 |
| `src/main/resources/static/app.js` | 搜索表单与纯文本结果、详情入口、真实任务跳转、失效与在途结果协调、B3健康字段显示 |
| `src/main/resources/static/index.html` | 开放搜索表单和结果区，Top-K默认5，更新真实B3流程说明 |
| `src/main/resources/static/styles.css` | 摘要和长ID换行、查询条件展开控件焦点 |
| `tests/frontend/api.test.mjs` | 删除FE1时期“API尚无搜索方法”的过期断言，保留失效只发一次PUT的原断言 |
| `tests/frontend/search.test.mjs` | 新增13项搜索边界与交互状态测试 |
| 本交接文档 | 真实证据和待集成范围 |

未改Java、B1、SQL、pom、公共DTO/API/运行契约或部署配置；没有新依赖、框架、CDN、生产mock、缓存计数或发布操作。新注释与提交说明使用简体中文。

## 接入行为

1. `POST /api/search`只在用户明确提交时发送，正文`{text,topK,channel?}`。空/纯空白文本合法、保留原文，Top-K必须1—100的数值整数；表单通过valueAsNumber读取，空值为NaN并提示。默认5。服务端仍校验规范化长度及字节限额。
2. UI空渠道省略字段；调用边界的null同样表示未指定，提交default、搜索/列表全渠道。非空渠道保持原冻结Java空白谓词（U+0085不是空白）与大小写，不改变后端显式空白400规则。
3. 响应只接受冻结hits形状、有限[-1,1]分数和200 code points以内的文本摘要。保留后端原顺序、零负分、同分顺序；四位小数仅显示，不换算匹配率/概率、不本地过滤或重排。
4. 初始未搜索、执行中、有结果、空命中、参数错误、401/403、404、429、503/未知、超时/网络失败各有真实反馈。成功展示实际hits.length和浏览器测得的请求往返耗时，不宣称纯后端耗时或全库统计。
5. 防重复提交覆盖按钮和隐式表单提交；不会为第二次提交取消/重放第一笔搜索。网络失败、超时、无效成功响应及RESULT_UNKNOWN均不自动重试，提示可能已计数；取消浏览器等待不能证明后台回滚，累计hitCount不能确认本请求提交情况。
6. 表单修改不自动搜索，旧结果标注原查询条件并可查看原始文本；新查询进行中或失败时也保留并明确上次成功快照。所有查询/摘要/文档字段通过textContent展示，不执行HTML或自动链接。
7. 失效仅在后端确认成功后标记；搜索已返回或仍在途时，同一文档结果都标历史状态，既不删除重排，也不自动重搜。详情读取发现已失效时同样更新历史提示。
8. 搜索结果详情按钮发真实GET，刷新详情始终读取服务器十进制字符串hitCount，不推算+1；列表也保持真实快照并手动刷新。任务完成不会触发搜索，提供“前往检索（手动查询）”；四态与终态停止仍以服务器为准。
9. 页面移除“B3未启用/搜索待B4”的旧运行提示，但不将页面接入等同于后端验收或正式发布。健康只显示实际字段，包含后端提供时的taskState/taskReady；businessReady=false继续明确完整业务未就绪。

## 真实验证

Node `v22.16.0`，在本工作树执行：

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/api.mjs
node --check src/main/resources/static/search.mjs
node --check src/main/resources/static/tasks.mjs
node --test tests/frontend/*.test.mjs
git diff --check
```

结果：四个模块语法检查通过；共38项Node测试（既有25+新增13），38通过、0失败/跳过/取消。新增覆盖空文/渠道省略与null、Top-K类型/范围、负零同分保序、空命中、HTML/emoji摘要、响应契约异常、搜索未知结果与超时不重放、SEARCH_BUSY/授权/字段错误、单在途防重入、编辑参数快照、在途失效历史、字符串计数读取。受控fetch和时钟仅存在于测试，不作为真实服务行为证据。

Python标准库HTMLParser及静态检查也通过：ID无重复，label/ARIA/Tab引用及app.js的byId目标均存在，CSS/JS与模块资源存在；未发现innerHTML/insertAdjacentHTML、原文/凭据持久化、外部CDN或setInterval。

## 未执行、限制与下一步

- **真实浏览器：NOT_RUN。** 主控本轮实测Work浏览器禁用（browsers为空、Local browser use is disabled in Work settings），本角色没有通过原生Chrome或其他路径绕过。历史FE1视觉/键盘/dialog焦点/真实DOM/XSS/浏览器请求竞争未验结论仍保留。
- **真实B4 HTTP与JAR：NOT_RUN。** 本角色与后端并行按冻结契约接入，当前分支未包含后端B4搜索实现；没有用受控fetch宣称真实搜索、事务或计数通过。
- **Java17完整verify：本角色未执行。** 未改Java，覆盖此次修改的Node测试已执行；主控合入后需完整verify及JAR模块媒体类型/真实同源请求验收。
- 后续主控应优先完整UI01—18：真实普通/空文提交→B3终态→手动搜索→详情字符串计数→GET不计数→失效确认→历史提示→手动重搜排除；再做网络/RESULT_UNKNOWN、负同分、隐藏/终态/预算、窄屏键盘及恶意原文测试。HTTP/Node证据不得替代这些浏览器结论。
- 当前只提供真实请求接入，不修改健康业务就绪策略，不发布远程应用/代理/认证或systemd。DEP3仍需独立授权。
