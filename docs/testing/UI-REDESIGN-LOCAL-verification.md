# UI-REDESIGN-LOCAL 本地交付核证

2026-10-23。结论：前端改版在独立工作树完成，本地自动化、所列真实浏览器与新JAR冒烟通过，可供本地审阅；不代表线上发布完成。旧临时预览曾发生提交503，原因未定，保留为发布前跟踪风险。

## 版本、所有权与范围

- 基线：5f1740d2141473a7595eeb41eb6efb037a847815（本轮取得的origin/main）。
- 代码/测试SHA：0b0f18b39384b0ccd68d4dc7e15deca7afe60d1f；分支codex/frontend-redesign。
- 主工作树：/Users/qbss/.codex/worktrees/frontend-redesign/12times；前端角色树：/Users/qbss/code/12times/.worktrees/ui-redesign-frontend。
- 实施提交：011439d（改版和8项用例）、3543e0d（桌面结果密度）、f1ecc09（任务/详情与第9项用例）、0fde491（手机密度），均中文真实提交；主控使用保留历史的合并提交。
- 生产差异仅static：index.html/styles.css/app.js及新增presentation.mjs；api.mjs/search.mjs/tasks.mjs未改。新增tests/frontend/redesign.test.mjs。AGENTS和任务/契约/验收文档另列；Java业务、API/DDL、算法、application配置、依赖、CI与deploy无修改。
- 没有push、main合并、SSH、线上业务请求、线上JAR/配置/数据库变更；原归档树和用户docs/测试工作/未改。

## 真实自动化命令

Java17：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
-Dmaven.repo.local=/private/tmp/12times-m2 clean verify
```

最后一次代码修复后完整运行：BUILD SUCCESS，2026-10-23 12:55:43 UTC+8结束，58.440秒。XML实计Surefire19组104项、Failsafe16组126项；失败/错误/跳过均0。最终日志：/private/tmp/12times-ui-redesign-release-verify.log。

Node：`/opt/homebrew/opt/node@22/bin/node --test tests/frontend/*.test.mjs`。Owner及独立QA实测Node v22.16.0、52/52 PASS，无失败/取消/跳过。原43+新增9项，不重复相加历史运行；QA最终日志：/private/tmp/12times-ui-qa-mobile-node.log。Java230+Node52共282项自动化，浏览器案例和HTTP请求不再混入该数量。另有真实app语法与git diff --check核对。

## 新包和运行

- JAR：.cache/ui-redesign-preview/0b0f18b39384b0ccd68d4dc7e15deca7afe60d1f/vector-search.jar（本工作树内，本地不可变副本）。
- SHA-256：9091619016d93a2ecd4e195c41d8d738ac12c66b3bf8c3208b1b284d9a0e4066。
- ZIP逐字节核对7份内嵌静态资源与工作树一致；最终启动无外置static override，不沿用旧正式包摘要。
- 本地URL：http://127.0.0.1:18183/；仅回环监听；H2：/private/tmp/12times-ui-local-wdkte6mb/db/knowledge。
- 当前预览PID18141、supervisor18140，13:02:54启动，15:02:54（北京时间）自动SIGTERM，最多等待90秒，不强杀；正常停止可核对进程后`kill -TERM 18141`。会话元数据.cache/ui-redesign-preview/final-session.json，日志/private/tmp/12times-ui-local-wdkte6mb/preview-default.log。
- 开发库显式空密码仅限本轮新建回环临时库；没有使用生产凭据、库、其他角色端口或H2 Console。数据、日志和离线诊断副本均保留，不清理。
- 先前预览12843与诊断17231均正常exit143，实际Tomcat优雅关闭/Hikari关闭日志齐备；新包默认配置重启后已有失效、成功状态、向量和计数仍可读。

## 真实浏览器矩阵

主控实际用IAB，不以HTTP/Node代替。证据目录：/Users/qbss/code/12times/.cache/product-design/20261023/ui-implementation-evidence/。

| 组别 | 实测断言 | 结果与证据 |
| --- | --- | --- |
| NAV | reload初始0 API；三Tab切换、键盘End/Home；导航不自动搜索 | PASS；网络记录和DOM |
| INPUT | Top-K=0显示字段错误、0 POST；空查询合法 | PASS；08截图；请求游标未截断 |
| SEARCH | 单击一次POST，真实负分-0.0581保留，零查询全0按ID排序；显示四位不本地重排 | PASS；02/08/09截图 |
| SUBMIT | 单次POST202，真实任务SUCCEEDED；新包手机空原文真实提交成功 | PASS；04/05/18截图 |
| LIST | 实际21条，第一页20、第二页1；修改渠道后禁翻页，应用后回第一页10条 | PASS；22截图；夹具HTTP_SETUP_ONLY不算浏览器提交 |
| DETAIL | 真实原文、长ID完整显示/复制；重复GET命中计数不变 | PASS；06/19/21；复制剪贴板实际refund-policy-06 |
| INVALIDATE | 取消0 PUT，确认1 PUT/200；任务SUCCEEDED、向量就绪仍保留，历史结果不重搜 | PASS；07/23截图与网络 |
| DIALOG | Esc关闭，焦点回触发按钮；确认取消回失效按钮；320主按钮可达 | PASS；17/DOM |
| RACE | 响应阶段暂停真实202，关闭并切Tab，释放后不复活/不抢焦点；详情A取消后B保留 | PASS于所列受控范围；10/24截图；A已取消，不声称旧体送达 |
| FAIL | 请求阶段ConnectionReset，错误明确结果可能未知，不重试，保留上次快照；观察窗口仅1 POST | PASS；12截图；不是数据库提交未知的真实故障注入 |
| SAFE | img/script输入真实入库；详情与片段无img/script节点，无JS弹窗 | PASS；11截图与DOM |
| VISUAL | 六图联合/局部对比；1440、390、320；三项P2复拍关闭 | PASS；根design-qa.md与联合图 |

最终页面error/warn日志为空；Fetch拦截清空、缓存恢复、viewport重置；实际当前默认564×670也无横向溢出。原始截图和网络摘要保留本地，evidence-manifest.json记录文件hash（清单SHA：9d4725c01ea5ec009671d0111da108b26f734a1c9bedbb9a03b3e8533b7f233c），不推送原始载荷或截图。

## 六接口与HTTP补充

新JAR默认配置独立合成文档ui-ready-smoke-01真实202和SUCCEEDED；搜索后计数0→1，重复详情仍1；正确无body PUT后失效为真，任务仍成功且向量就绪。六路由跨分段核验完成。真实证据：/private/tmp/12times-ui-local-wdkte6mb/final-jar-http-smoke.json；HTTP_SETUP_ONLY与浏览器按上表分层。

补充验收脚本有两处自身错误，保留事实：首段误用total与page=1，列表断言退出；续跑给PUT发{}收到400。冻结契约是totalElements、page=0及无body PUT，前端原本正确。修正后未重放文档提交或搜索，仅继续未成功的失效并GET核对；不把错误脚本的运行报告为整段一次通过。

## 旧临时预览503：开放跟踪项

旧正式冻结JAR+外置静态资源预览12843先完成浏览器提交/搜索，随后空文/长ID/普通新提交均返回503 DATABASE_UNAVAILABLE，health仍UP/READY、已存数据可读。未观察到原因，未更改后端。

正常停止旧进程后，对同一临时H2的独立离线副本执行两表INSERT+rollback成功；新JAR同库、诊断日志与随后默认日志正常重启均接收新空文、长ID、普通文档及10条分页夹具。未复现不等于已修复。旧日志、原库、副本及新日志保留。候选含相同Java源码；发布前需追踪该现象及完成所选环境稳定性回归，不能用UI通过覆盖。

## 未执行与后续发布门禁

- 新分支远程push/CI、main集成、线上部署/HTTPS认证反代/线上浏览器和新版本压测：NOT_RUN，按用户安排延期。
- 实际读屏软件、真实手机触控和软键盘、Safari/Firefox：NOT_RUN。本地IAB键盘、DOM和视口测试不能替代。
- 真实数据库提交未知/故障恢复未在UI轮重做；Java完整回归包含已有测试，但不等于新线上故障演练。
- 本轮不改生产后端以排查503。若后续确认需要Java/算法/API/DDL/核心参数改变，单独报告和版本决策。
- 用户确认线上功能/压测结束后，先审阅本改版和503跟踪结果，再选择版本集成、push并取得新CI，固定实际SHA与JAR摘要、检查备份/回滚，依既有门禁发布。禁止沿用d016旧批准值或自动切线上。

## 并行主线最终只读复核

交付复核时共享origin/main为`b1b54f458dec7ddaa53539203d0e2d1aac76da26`，本地main引用仍为`661ed08c1f524358111881e9c4e1fe1936a7a4a5`。相对本工作树创建基线5f1740d，新增为线上测试执行器、性能统计/图表、报告及README；`git diff --exit-code 基线 origin/main -- src/main pom.xml .mvn mvnw mvnw.cmd`实际为0，生产树无差异。本轮未把它们合并或更改，不继承其线上结果作为新UI验收；后续集成需保留这些并行成果。受测代码SHA仍为0b0f18b，随后提交仅文档。
