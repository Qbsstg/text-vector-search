# C0-FE 初始化入口交接

- 任务：C0-FE，仅初始化静态入口，不实现六业务 API。
- 分支：`agent/frontend`。
- 基线：`0f692387577be629d296d3a5fe0ba626072f93b4`。
- 提交：以本交接文档所在提交的 `git log -1 --format=%H` 为准；由角色回报实际 SHA。

## 修改范围

- `src/main/resources/static/index.html`：C0阶段说明、三个功能规划Tab、禁用的业务按钮、手动基础健康检查。
- `src/main/resources/static/styles.css`：本地样式、窄屏布局、可见焦点和状态文字。
- `src/main/resources/static/app.js`：Tab点击/方向键/Home/End切换，以及单次手动同源`GET /health`。
- 本交接文档。

没有修改Java、DDL、pom、公共DTO、API/运行契约或主工作区；没有改变依赖，没有业务mock、外部CDN、框架或持久化原文/凭据。

## 行为与诚实展示

页面明确列出六业务API尚未实现，业务按钮始终禁用并解释原因；没有假结果、假任务、假进度、虚构计数或硬编码服务正常。空文本、渠道缺省区别、任务真实状态和检索不自动重试作为后续规划说明，当前不发送业务请求。

基础健康初始为“尚未检查”，只由用户点击触发。5秒超时、请求期间按钮禁用，无自动重试/轮询；每次检查先隐藏旧响应，失败不继续展示旧值为当前结果。成功后按契约核对字段类型，使用`textContent`展示服务器实际返回的`status/stage/businessReady`；`businessReady=false`明确提示业务未就绪，不将基础连接等同于检索/任务/恢复可用。非2xx、格式异常、网络失败或超时均显示无法确认状态，业务按钮不因此启用。

## 真实检查

2026-10-22在本工作树执行：

- `node --check src/main/resources/static/app.js`：退出0，语法通过。
- Python标准库`HTMLParser`静态检查：三个Tab与panel一一对应；ID不重复；只有一个初始选中Tab；三个业务按钮禁用；本地CSS/JS引用文件存在；只有`/health`一个fetch入口，未使用`innerHTML`/`setInterval`。全部通过。
- `git diff --check`：提交前执行，检查空白错误。
- 首次尝试`JAVA_HOME=<JDK17> ./mvnw -B clean verify`：未进入编译，默认Wrapper缓存目录写入被沙箱限制。
- 尝试`JAVA_HOME=<JDK17> mvn -o -B clean verify`：失败，默认本地缓存缺少Spring Boot 4.0.3父POM。未将环境失败报告为构建通过。

主控随后提供可复用缓存环境并要求Java构建、JAR静态资源与浏览器统一由主控最终验证，本角色未重复运行Java构建。上述环境失败不涉及Java或依赖修改。

## 未执行与风险

- 未完成浏览器验证；尚未实际验证键盘焦点、窄屏布局和健康请求的浏览器行为。
- 未在本工作树启动Java服务、调用真实`/health`或检查最终JAR资源；交主控集成构建与浏览器验收。
- 未实现和验证六业务API、业务轮询/空文本/提交/搜索/失效流程；当前页面不能作为完整PRD或UI01—18通过证据。
- 未部署、未推送远程。

## 主控审查重点与下一依赖

审查C0未实现声明、禁用按钮、基础健康语义及`textContent`边界；集成后验证`/`、`/styles.css`、`/app.js`与真实`/health`同源可达。后续FE-C1必须由主控重新派发；待真实业务API和独立工作树基线明确后，才实现三功能区，不因C0入口完成自动扩展范围。
