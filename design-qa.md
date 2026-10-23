# UI-REDESIGN-LOCAL 设计还原核证

final result: passed

日期：2026-10-23。主控实际操作IAB本地预览，前端Owner修复，独立QA核对模块测试和源码。此结论仅覆盖本地前端设计还原，不是线上发布或全部非功能验收结论。

## 源稿、实现和归一方式

- 源稿目录：`/Users/qbss/code/12times/.cache/product-design/20261023/full-page-design/`，01检索、02文档、03提交、04任务、05详情、06手机检索六图。
- 实现工作树：`/Users/qbss/.codex/worktrees/frontend-redesign/12times`；代码SHA：`0b0f18b39384b0ccd68d4dc7e15deca7afe60d1f`。
- 浏览器URL：`http://127.0.0.1:18183/`，本轮全新临时H2，仅合成数据。前半段冻结后端JAR+外置静态目录，后半段新JAR内嵌静态资源；两层证据分开。
- 证据目录：`/Users/qbss/code/12times/.cache/product-design/20261023/ui-implementation-evidence/`。以下截图/联合比较文件名均相对该目录。
- 桌面源图1487×1058归一为1440×1024。运行DOM视口1440×1024、devicePixelRatio=1；IAB截图常为1425×986或1440×996。保留运行截图原始像素，仅从归一后的源稿裁切相同区域，不拉伸实现。差异含15px滚动条及浏览器可见采集裁切，不能将该差异判为页面缺陷。
- 手机源图853×1844按近似相同纵横比归一为390×844。运行DOM390×844、devicePixelRatio=1，截图375×812；源稿同区域裁切。另实测320×720，未使用设备外框。
- 原始CDP截图`02-search-results-exact.png`存在物理像素裁切问题，已排除，不用于最终比较。`25-final-local-preview.png`处于异步结果更新窗口，以`26-user-preview-current.png`为当前预览证据。
- 生成图数据与真实B1输出不同：保留真实分数、顺序、计数、时间和状态，不为视觉还原写入假数据。详情比较使用有效且成功的06替代源稿01；任务正常态均为真实SUCCEEDED。列表源稿混合状态，运行截图是当时真实快照，数量不同不作像素同一断言。

## 全视图及局部证据

| 页面 | 源图 | 浏览器截图 | 联合对比 |
| --- | --- | --- | --- |
| 桌面检索 | 01-search-desktop.png | 09-search-after-density-fix.png | compare-search-after.png |
| 文档管理 | 02-documents-desktop.png | 03-documents.png | compare-documents.png |
| 提交弹窗 | 03-submit-dialog.png | 04-submit-dialog.png | compare-submit.png |
| 任务页 | 04-tasks-desktop.png | 20-task-after-fix.png | compare-tasks-after.png |
| 详情弹窗 | 05-detail-dialog.png | 21-detail-after-fix.png | compare-detail-after.png |
| 手机检索 | 06-search-mobile.png | 16-mobile-search-after-fix.png | compare-mobile-after.png |

已实际打开上述联合图片后判断。检索字体/表格/摘要另用`compare-search-after-focus.png`，列表密集信息用`compare-documents-focus.png`，详情状态条和字体用`compare-detail-after-focus.png`；没有把分别打开的两图冒称联合比较。

## P0—P2 发现与修复历史

| 编号 | 初次证据与影响 | 修复 | 复核结果 |
| --- | --- | --- | --- |
| UI-V01 / P2 | compare-search-before.png、02-search-results-browser.png：重复成功反馈/快照说明令表头接近700px，五条短结果首屏只见约3.5条；results top569.98 | Owner3543e0d：成功通知保留读屏但视觉收起、正常历史空行删除，条件和耗时合并，结果和行间距收紧；错误/零分/忙碌/历史仍可见 | 09截图与compare-search-after：results top518.18，五条末行bottom965.74；1440×1024可见，关闭 |
| UI-V02 / P2 | compare-tasks.png、05-task-completed.png：正常任务仍有两条空错误行，主要操作落到首屏以下 | Owner f1ecc09：按真实错误内容显示字段，收紧时间和关键信息；详情各状态独立使用徽章 | 20截图与compare-tasks-after：主要按钮top854.96/bottom896.96；无错误不占行，有错误不按状态隐去，关闭 |
| UI-V03 / P2 | compare-mobile.png、13-mobile-search.png：两行导航、105px文本区和大间距让390屏只见约一条结果，偏离源稿密度 | Owner0fde491：44px单行导航、24px标题、64px可扩容查询区、紧凑摘要和行距 | 16截图与compare-mobile-after：结果top404.57；390与320均无页面横向溢出；320三Tab同一行，主操作可达，关闭 |

无剩余可行动P0/P1/P2。手机结果动作保留32px可点区域、完整真实片段及复制标签，首屏约2条多，第三条随内容长度出现；较生成图更密集的小链接保留可读/可点击取舍，作为P3记录，不继续压缩。

## 五项必查表面

| 项目 | 结论 |
| --- | --- |
| 字体与层级 | 使用已有本地系统无衬线字体栈，无外部字体依赖。桌面h1 32px、正文14px、表格13px；手机h1 24px、结果摘要12px。源图无字体元数据，不能声称精确同字体。联合和局部对比显示层级一致；ID用等宽、长ID可完整查看/复制，无截断请求值。 |
| 间距与结构 | 白底、1280主宽、顶部三Tab、原文优先详情、任务/会话分区与目标保持。三处密度问题均已修复。提交弹窗正文可滚动、底部操作保留；320弹窗主要提交按钮在653—695px范围。 |
| 颜色与令牌 | 真实computed默认主色rgb(37,99,235)，即#2563EB；文字#0F172A、次色#64748B、浅灰背景和细线。鼠标悬停#1D4ED8是预期，不能把悬停截图或色彩空间差异当普通态缺陷。成功、待处理、失败、失效分别映射真实字段。 |
| 资产与图像 | 目标是纯产品界面，无照片、插画或背景位图资产；已有favicon保留。品牌为文字。操作使用有名称的文字按钮和原生details，不以emoji、自绘SVG或图片假装实际控件，也没有把整张设计图贴为网页。 |
| 产品文案 | 真实字面匹配、202仅接收、零/负分、计数快照、未知不重放、失效不可恢复均保留。固定示例日期改UTC+8说明；查询正文折叠可查。任务采用真实状态+三时间字段，未复制示意时间线或伪造进度。 |

## 浏览器交互与控制台

实测提交202→真实任务SUCCEEDED→详情；搜索、列表跨页、筛选草稿、失效取消/确认、计数读取不变、空查询、负分、键盘Home/End/Escape、焦点恢复、XSS纯文本、长ID/窄屏、复制完整ID。受控测试单列：暂停真实202响应，关闭后释放不复活弹窗/不抢焦点；暂停详情A再关闭打开B，A请求被取消，B未被覆盖；请求阶段注入ConnectionReset后搜索仅一个POST，不自动重试且保留历史快照。没有声称已向关闭窗口交付被取消的A响应。

最终新JAR页面控制台error/warn为空；network摘要仅本机127.0.0.1:18183，读取无truncated/hasMore遗漏。最终包reload初始0个/api请求。测试后已清除Fetch拦截、恢复浏览器缓存和视口覆盖，保留本地预览页。

## 遗留与适用范围

P3：可进一步统一表格次级标签和标准图标，或讨论任务时间线；不扩大本轮业务范围。实际读屏软件、真实手机触控/软键盘及其他浏览器引擎未执行；本机HTTPS认证/反代及公网环境未执行。临时旧预览的503和补充HTTP脚本错误详见验收报告，保留原事实，不作为视觉通过的替代证据。

## 最终核对

- 已核对六张源图和真实渲染，具备联合及局部证据。
- 已修复并重拍全部P2；字体、间距、颜色、资产、文案均逐项检查。
- 关键新增交互已本地浏览器核验；自动化和HTTP结果未冒充浏览器结果。
- 发布仍延期；此结论不改变线上版本或历史验收记录。
