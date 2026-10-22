# 最终真实 Chrome 验收核证

任务：UI-FINAL / QA。QA文档基线：`8a6713c`，分支`agent/qa`，独立工作树`.worktrees/qa`。本文件仅记录已有真实浏览器证据及待执行矩阵；不启动应用、不操作Chrome、不操作服务器、不修改业务与既有证据。执行步骤沿用`docs/tasks/handoffs/frontend-final-ui.md`的U01—U18、F01—F11，共29项。

## 判定规则

- `REAL`：真实Chrome页面、真实同源接口及浏览器真实断网/阻断；需要页面、Network与响应相互对应。
- `FAULT_INJECTION`：明确包装响应、延迟页面处理或发包前抛错，只能证明前端分支；不能证明真实RUNNING、FAILED、负分或后端未知提交。
- 状态只有`PASS`、`FAIL`、`NOT_RUN`。只执行某项的一部分时保持`NOT_RUN`并列出已观察事实。截图名不是状态证据；Node、HTTP与CI不能升级为浏览器PASS。
- 新候选必须重记完整源码SHA、JAR SHA256、CI与独立临时库；旧候选FAIL永久保留。真实RUNNING失效竞态、真实FAILED及自然负分未发生时不得填PASS。
- 所有计数以响应中的十进制字符串记账；失败/未知提交不依据累计值猜测、不自动重放。正式服务器、认证与HTTPS门禁另验。

## 旧候选：真实阻断失败

- 源码SHA：`68d80cb967c6d321047672811dea45b555ba5cbc`。
- JAR SHA256：`61bbb02df6742ee66e25e0d93dd13a18a8fb6caca69008761a80961707a9d42f`。
- 本机真实Chrome，Network User-Agent记录`Chrome/155.0.0.0`；URL：`http://127.0.0.1:8083/`。未据User-Agent推断精确安装版本。
- 原始目录：`/private/tmp/12times-chrome-ui-skafk2b5/`，证据在`evidence/`；QA只读核证，未复制/修改原始材料。`run.json`记录开始`2026-10-22T19:04:35.878187+00:00`、停止`2026-10-22T19:08:34.802402+00:00`、退出143。
- 结论：**该候选UI门禁FAIL**。真实提交已返回202，但自动任务追踪和两种手动入口均被计时器异常阻断；不能称完整业务UI通过。

独立证据核对：

| 证据 | 已核事实 |
| --- | --- |
| `chrome-events.json`请求`56882.30`、`chrome-response-bodies.json` | 一次`POST /api/documents`返回202；真实documentId=`ui-1792695973737-a`，taskId=`71295722-9e09-4579-a5b0-5a150c4045e4`，响应状态QUEUED。该事件文件内没有任务GET，不证明后端最终终态。 |
| `console-failure.json` | 三次`TypeError: Illegal invocation`均在`TaskTracker.enable`的`tasks.mjs:65:14`；调用链分别经提交表单、`查看真实任务`按钮、任务查询表单。时间为19:06:14、19:06:19、19:06:32 UTC。 |
| `U02-real-task.png`及同名DOM | 输入真实taskId后仍显示“尚未查询任务”和“尚无本页会话任务”，与计时器阻断一致。`U02-task-succeeded`仅为文件名，不能据此宣称SUCCEEDED已验。 |
| 源码`src/main/resources/static/tasks.mjs`第8—14、65行 | 构造器把宿主`setTimeout`直接存入`this.setTimer`，再以成员调用方式执行，receiver成为TaskTracker；与Chrome异常位置吻合。修复应保留正确宿主receiver，并覆盖`clearTimeout`同类边界，由前端Owner实施；QA不改业务。 |
| `chrome-response-bodies.json`请求`56882.29`与U18截图/DOM | `/health`真实200，B4、READY、taskReady/businessReady及六项checks均true；只证明该快照，不等于UI或发布许可。 |
| `chrome-events.json`静态资源 | 首页200；五个静态文件304且媒体类型有效，非模板要求的全200冷加载；另有`favicon.ico`500及Chrome扩展请求。扩展请求不等于应用引入外部依赖，favicon500保留为待复核发现。 |
| `cases.json` | 原记录U01、U18为PASS、U02为FAIL。QA保留原件；U01完整条件尚缺冷加载全200证据，下面按NOT_RUN记录部分已观察事实。U18按本机同源/健康范围核证PASS，不包含服务器项。 |

## 精简29项矩阵

“新轮”尚无固定候选与证据，全部NOT_RUN；旧轮结果不得迁移。U09、F05虽然位置不同，均必须取真实后端状态。U12含真实终态与受控时限两层，分别记录。

| 编号 | 核证要点 | 证据模式 | 旧轮状态与实际范围 | 新轮 |
| --- | --- | --- | --- | --- |
| U01 | 首屏、三Tab、同源静态、初始无搜索 | REAL | NOT_RUN；已有首屏/Tab观察，冷加载全200待验 | NOT_RUN |
| U02 | 单次提交202、真实任务入口与按钮恢复 | REAL | FAIL；202后计时器Illegal invocation，任务入口阻断 | NOT_RUN |
| U03 | 空文成功、ready、终态后10秒停GET | REAL | NOT_RUN | NOT_RUN |
| U04 | 重复ID409、无重提/覆盖 | REAL | NOT_RUN | NOT_RUN |
| U05 | 省略渠道：提交default、搜索全部 | REAL | NOT_RUN | NOT_RUN |
| U06 | Top-K 0/-1/小数/空拦截、恢复整数 | REAL | NOT_RUN；仅有0的页面记录，不代表完整四种已验 | NOT_RUN |
| U07 | 同文同分ID顺序、四位分数、自然负分 | REAL | NOT_RUN；自然负分未发生时单列未执行 | NOT_RUN |
| U08 | 空查询零分、ID序、K>N及计数 | REAL | NOT_RUN | NOT_RUN |
| U09 | 真RUNNING中PUT失效、终态/有效性正交 | REAL | NOT_RUN；未捕获真实竞态不得PASS | NOT_RUN |
| U10 | 取消PUT0、真实网络失败不假成功 | REAL | NOT_RUN | NOT_RUN |
| U11 | 真taskId查询、网络失败不改FAILED、404 | REAL | NOT_RUN；任务入口已有阻断，其他分支未验 | NOT_RUN |
| U12 | 真终态停GET；预算/隐藏/并发另见F02/F03 | REAL / FAULT_INJECTION分层 | NOT_RUN | NOT_RUN |
| U13 | 真Offline搜索仅一次、旧结果历史提示 | REAL | NOT_RUN；Offline不证明服务端已提交 | NOT_RUN |
| U14 | 原文/摘要XSS纯文本、无额外脚本网络 | REAL | NOT_RUN | NOT_RUN |
| U15 | 字符串计数、搜索+1、GET不变 | REAL | NOT_RUN | NOT_RUN |
| U16 | 确认失效、旧结果保留、新搜索排除 | REAL | NOT_RUN | NOT_RUN |
| U17 | 键盘、dialog焦点、三视口与200% | REAL | NOT_RUN | NOT_RUN |
| U18 | 同源请求、真实B4健康；独立发布边界 | REAL | PASS；限本机同源和健康快照，不含DEP3 | NOT_RUN |
| F01 | 注入负分/同显示分不重排 | FAULT_INJECTION | NOT_RUN | NOT_RUN |
| F02 | 受控RUNNING、60秒/最多3个、不重叠 | FAULT_INJECTION | NOT_RUN | NOT_RUN |
| F03 | 受控任务隐藏/恢复不重置预算 | FAULT_INJECTION | NOT_RUN | NOT_RUN |
| F04 | 受控网络失败保留上次状态 | FAULT_INJECTION | NOT_RUN；真实Request blocking另记 | NOT_RUN |
| F05 | 真实FAILED错误展示、终态停止 | REAL | NOT_RUN；无真实失败则继续未执行 | NOT_RUN |
| F06 | 注入RESULT_UNKNOWN无重发、旧结果历史 | FAULT_INJECTION | NOT_RUN；无真实POST，不能证明未知commit | NOT_RUN |
| F07 | 延迟搜索防重、编辑保留原快照 | FAULT_INJECTION | NOT_RUN | NOT_RUN |
| F08 | 浏览器迟到搜索+真实PUT、历史标记 | FAULT_INJECTION | NOT_RUN；不证明后端SQL快照竞态 | NOT_RUN |
| F09 | 详情A迟到不覆盖B/不复活dialog | FAULT_INJECTION | NOT_RUN | NOT_RUN |
| F10 | 发包前失效失败不乐观成功 | FAULT_INJECTION | NOT_RUN；真实Offline另记 | NOT_RUN |
| F11 | 提交未知按原ID确认、不换ID重提 | FAULT_INJECTION | NOT_RUN | NOT_RUN |

列表分页、筛选条件、20/50/100、21条以上及空结果、详情长ID/空原文/独立滚动是上述脚本的补充范围，目前均未执行，不能因29行填完而遗漏。

## 本次交付与未执行项

实际命令：`git status --short`、`git branch --show-current`、`git rev-parse HEAD`确认QA树干净；`git merge --ff-only main`快进至`8a6713c`；只读`cat`/`sed`/`rg`与Python解析旧轮JSON/事件；查看`U02-real-task.png`进行视觉核证。提交前执行`git diff --check`及矩阵结构静态检查。首次Git元数据写入受沙箱拒绝后以已授权提升重试成功，不影响文件历史。

本次仅新增本文档，无代码测试新增通过数；Java/Node测试未执行，Chrome操作未执行，应用启动未执行，服务器未执行。新轮候选SHA/摘要/CI、完整真实Chrome矩阵及故障注入分支待主控提供后再核证；最终UI门禁仍未通过。证据位于本机临时目录，尚未归档进仓库，需主控保留或另行归档后再作长期交付。
