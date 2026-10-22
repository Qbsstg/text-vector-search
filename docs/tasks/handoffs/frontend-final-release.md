# FINAL-RELEASE 前端新增用例交接

- Owner：前端工程师；工作树 `/Users/qbss/code/12times/.worktrees/frontend`；分支 `agent/frontend`。
- 经主控明确批准，从干净的 `bf7aeac2a5ba349486d7cc4319be9d59e18890bf` 快进 main；本次基线 `da5867a5ecdb5ae4705e3643e3e96298b9bd7574`。
- 已读取用户本轮完整授权附件；当前前端任务仅盘点、独立 Node 补测及浏览器差异步骤。主控独占真实 Chrome、正式 HTTPS 闭环和候选/部署。
- 已批准生产候选仍为 `d016d6e8bb431ff17ba81f8977d31a1b96f51f7f`，JAR SHA256 `64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50`，CI `39838054946`。本次只加测试和交接，不改变生产源码或候选 JAR；后续测试源码 CI 由主控集成。
- UI 历史保持 28 PASS；F05 保持 `NOT_RUN / 用户批准例外`。现有报告中“例外待批”属于此前快照，以用户本轮明确授权为最新决定；不回改原始证据、不升级为 PASS。

## 来源与去重映射

已只读检查主树本地 `docs/测试工作/02_测试用例与验收方案.md` 前端部分、`04_结构化测试用例.json` 的 UI-01～08，以及四个 `fixtures/*.json`；原始文件未改、未复制进入本分支。02 与 04 是同一组用例的两种表达，按同一 ID 逐项映射，不算十六条。以下仅记录本项目的执行映射与差异，不原样公开用户材料。

| 原编号 | 已有证据/自动化 | 本轮真正差异与安排 |
| --- | --- | --- |
| UI-01 | Chrome U01/U07/U08、受控 F01；Node 搜索结果顺序、零负分、同显示分与空结果 | 无需重复；HTTPS 闭环核对真实展示即可 |
| UI-02 | U02/U04/U05/U06/F11；Node 原文保留、渠道省略、参数与未知提交 | 补非法路径发包前拒绝；错误表单的键盘恢复见 FR-B02/B03 |
| UI-03 | U03/U11/U12/F02/F03/F04；Node 有限轮询、隐藏、预算、并发、读取失败 | 复用；真实 FAILED 组合仍按 F05 已批准例外，不制造故障 |
| UI-04 | U09/U10/U16/F08/F10；Node 失效不自动搜索及历史快照 | 复用，正式 HTTPS 闭环再次走实际失效路径 |
| UI-05 | U13/F06/F07/F09；Node 单请求门禁、未知不重发、旧读取消 | 复用，受控分支与真实网络证据继续分层 |
| UI-06 | U14 已验证原文/摘要 XSS；Node 已保留恶意原文字符串 | 渠道展示和错误 code/message/fieldErrors 的 DOM 安全缺直接证据；新增 FR-N04 和 FR-B01/B02 |
| UI-07 | U17 的键盘、三视口、200%、长 ID/原文、dialog；Node 时间与部分参数校验 | 新增合法 `._-`/128 位、非法路径、特殊渠道编码回归；FR-B03 验证错误后的键盘焦点/输入保留/恢复。已有 F02 DOM 包含 UTC+8 标签及缺失结束时间“—”，不重复补测 |
| UI-08 | 本地 U18 仅同源健康，不能替代服务器 | 正式认证 HTTPS + 重启前后业务闭环仍须主控执行，见 FR-B04 |

fixtures 的适用边界：文档夹具中的 D-013 对应原文 XSS，语义已由 U14 覆盖，新增渠道/错误文本使用独立无害标记；数学向量与文本关系夹具由后端/QA处理，不从中编造前端真实分数。请求夹具的 REQ-13/14 保留旧待定标记，当前 null 渠道和 JSON 类型以冻结 API 契约及对应工程测试判定，不回写原件或照搬旧待定结论。

## 新增 Node：四项，生产代码零修改

文件：`tests/frontend/final-release-boundaries.test.mjs`。全部使用独立合成常量，不依赖本地用户材料、真实网络、凭据、运行数据库或浏览器。

| 测试 ID | 来源 | 精确断言 | 本机结果 |
| --- | --- | --- | --- |
| FR-N01 | UI-07 | 合法 `A_1.x-y`、128 位 ID 的详情/失效路径原样保留；大小写 UUID 查询与响应匹配；无裁剪或改变大小写 | PASS |
| FR-N02 | UI-02/UI-07 | 路径分隔、百分号、问号、片段、空白、中文、129 位及空 ID、非法任务 ID 在 fetch 前产生对应 InputError；总网络调用 0 | PASS |
| FR-N03 | UI-07 | Unicode/空格/`&/+?%#=_` 渠道往返查询参数不变；路径、origin、hash、分页/有效筛选保持正确，只有一次 GET | PASS |
| FR-N04 | UI-06 | HTTP 400 的错误 code/message/fieldErrors 保留原字符串；过滤非对象/非字符串字段结构；一次调用且不重发。只证明解析边界，不宣称 DOM 无执行 | PASS |

## 主控真实 Chrome 差异步骤

以下四项当前均为 `NOT_RUN`，不是新增通过数。沿用已有 28 项证据，不重跑相同场景。每项记录完整生产 SHA/JAR SHA、页面 URL、模式、截图/DOM、请求与响应、Console、marker/请求数断言。认证头、Cookie、凭据不得写入公开证据。

### FR-B01：渠道纯文本展示（UI-06，REAL）

1. 在正式或主控独立验收页面，将 `window.__frontend_channel_probe__` 置为 0。新建独立合成 ID `FR_ui.PATH-<唯一后缀>`，原文使用普通合成短句，渠道使用不超过 64 code points 的无害字符串 `<svg onload="window.__frontend_channel_probe__=1">`。
2. 通过页面真实提交一次，取得真实 taskId 并等待真实成功；在列表、详情和同渠道搜索结果核对渠道与 Network 原字符串一致。此新 ID 同时覆盖合法 `._-` 路径。
3. 保存各渠道容器的 `textContent`、子节点结构及截图：只允许原字符串文本，不能出现由该渠道生成的 SVG/事件属性；marker 仍为 0、Console 无新应用错误、无额外外部资源请求。此项仅新渠道边界，不重跑 U14 已验的原文脚本流程。

### FR-B02：错误文本与错误后的键盘恢复（UI-02/UI-06/UI-07，FAULT_INJECTION）

1. 仅在本页短暂单次包装 fetch：匹配 `/api/documents` 的下一次 POST，在发包前返回结构合法的 HTTP 400 JSON。`message`、`code`、`fieldErrors[channel].message` 各使用独立无害 HTML/脚本 marker 字符串；额外给一个含引号的未知 field，确认不会被当 CSS 选择器执行。保存注入结构和 wrapper 计数；原 fetch 必须在 finally 恢复。
2. 保留文档 ID、text、channel 三个输入，通过键盘聚焦提交按钮并 Enter。预期包装层一次、真实 POST 增量 0；HTTP 错误与 channel 字段错误均为纯文本，无脚本/SVG/img 注入、marker 不变。
3. 核对焦点落在 channel、`aria-invalid=true`、三个输入原样保留、提交按钮恢复、没有成功记录或自动换 ID/重提。通过键盘修正渠道后恢复原 fetch，再主动提交一次，确认错误标记被清除且仅一个真实 POST 成功；记录该次真实新 taskId。此受控 400 不计真实服务错误或 FAILED 验收。

### FR-B03：非法路径与错误键盘操作（UI-02/UI-07，REAL）

1. 在详情查询表单分别输入 `fr/a`、`fr%2Fa`，每次以键盘 Enter 提交。保存输入、焦点、`aria-invalid`、字段错误、Network 增量；预期详情 GET/失效 PUT 增量均 0，错误不裁剪、修补或替换 ID。
2. 在任务查询输入带 `?x=1` 后缀的 UUID，以键盘提交；预期任务 GET 增量 0，焦点在任务 ID、原输入保留。
3. 用键盘将详情 ID 改为 FR-B01 的合法 `._-` ID，再 Enter；预期一个同源 GET、正确文档详情、错误标记清除、dialog 可读和 Escape 返回。128 位/其余非法字符由 FR-N01/02覆盖，不重复原 U17 长 ID/全视口测试。
4. 时间直接复用原 F02 缺失结束时间 DOM；在本次成功详情顺带对照原始 UTC 时间与北京时间 UTC+8 展示，不凭系统时区推断结果。

### FR-B04：正式 HTTPS 闭环（UI-08，REAL / SERVER）

1. 主控先完成认证/保护验收，在无包装 fetch 的正式域名登录；记录受保护页面与静态资源、同源 HTTPS、Console。凭据由安全方式处理，不留认证明文。
2. 使用独立 `FR_https-<唯一后缀>` 经页面提交，记录 202/QUEUED 和真实 task GET/成功/向量就绪；查询列表/详情，主动搜索核对渠道、顺序、分数与字符串计数。GET 不增加计数，搜索计数只能按明确完成的请求核账。
3. 明确失效后旧结果保持历史，再主动搜索排除该 ID；无未经许可的自动 POST/PUT。时间与空值展示可在此顺带保存，不重跑原 28 项。
4. 由服务器 Owner 在已授权流程正常重启，主控刷新正式页面，对照同一 ID 的成功任务、向量就绪、命中计数与失效状态保留；保存重启前后真实响应和截图。未捕获完成前搜索排除时，仅该子项引用后端/HTTP相应证据，不拼造浏览器时序。

## 真实执行与原始证据

- 环境：Node `v22.16.0`；新增测试运行于上述前端独立工作树和基线。
- `node --test tests/frontend/final-release-boundaries.test.mjs`：新增 4 tests / 4 pass / 0 fail / 0 skipped。
- `node --test tests/frontend/*.test.mjs`：全量 43 tests / 43 pass / 0 fail / 0 skipped；按旧 39 + 新 4 统计，独立运行的新 4 不与全量重复相加。
- 原始 TAP：`/private/tmp/12times-frontend-final-release.zTiD3W/new-node.tap`，SHA256 `140839497fd1224204a508896dfb160c6d77c2ef43d880d6831e4ae07c32b3b7`；`all-node.tap`，SHA256 `7ca3987b4c2931afe37bbd276b04249ca0b211318211ed14e6c7befb857064bb`。
- `git diff --check`：通过。通过 Python 对两个新增文件扫描私钥头、常见令牌、凭据赋值和认证头；对测试代码另检查无本地原始材料路径、文件读取、环境变量或真实网络调用依赖，扫描通过。
- 未执行：真实 Chrome、HTTPS/服务器、Java全量、Linux CI、发布候选构建；本任务不新增这些通过数。四项浏览器差异全保持 NOT_RUN 交主控。
- 修改仅上述独立测试文件及本交接；现有 39 项测试不改，生产源码不改。提交 SHA 由提交后的交付消息给出，可用本文件的最后提交核验。
