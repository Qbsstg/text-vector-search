# C0集成验收记录

日期：2026-10-22，北京时间。范围仅为项目初始化，不代表六业务接口、算法、任务调度或部署完成。

## 基线与环境

- 初始统一基线：`0f692387577be629d296d3a5fe0ba626072f93b4`。
- 本次集成构建代码SHA：`2e29030c91d834a24773fee29a6295038cf39e2f`。后续角色交接/验收记录提交仅改文档与属性文件；若后续改运行代码须重新验证。
- Temurin17.0.15，Maven3.9.9 / Wrapper3.3.2，Spring Boot4.0.3、MyBatis Starter4.0.1、H2 2.4.240（Boot BOM解析）。
- 五角色均实际按指定模型与推理运行，五个工作树从统一基线创建；首轮只读审查与C0任务分别交接。
- 评审资料包5份文件按原SHA256SUMS核对全部OK；原题PDF本地保留且未加入Git。

## 构建与测试

可移植命令：JDK17下 `./mvnw -B clean verify`。

本机真实执行（临时缓存/settings只用于受限环境，不是交付依赖）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
-Dmaven.repo.local=/private/tmp/12times-m2 clean verify
```

2026-10-22 21:21:20 +08:00结束，退出0，BUILD SUCCESS。Surefire **3项**、Failsafe **6项**，失败/错误/跳过均0。报告位于target下，不加入Git。

| 范围 | 已通过的证据 |
| --- | --- |
| DDL与文件 | 重复DDL、连接关闭再打开保留文档/任务；外键与向量长度检查 |
| Unicode存储 | 64个补充平面code point的渠道可保存，列预留128个UTF-16单元 |
| 完整应用重启 | 生产SpringApplication关闭及连接池关闭后同文件重启；原文、任务、版本、大整数计数保留 |
| 真实HTTP | 随机本地端口返回UP/C0/businessReady=false |
| 生产配置 | 13项默认/非默认覆盖；3种非法值经真实生产扫描和校验导致启动失败 |
| DTO | 生产Jackson的UTC时间、字符串hitCount、枚举、null字段及往返；不声称业务校验已实现 |

负向配置用例会输出预期启动校验失败日志，最终测试断言通过。C0暂无Mapper的扫描警告符合尚未实现业务的阶段。

## 可执行JAR与浏览器

实际执行`APP_DATA_DIR=/private/tmp/12times-c0-runtime/db PORT=18080 <Java17>/bin/java -jar target/vector-search.jar`，日志确认启动完成、H2文件连接成功、静态欢迎页生效。该临时目录仅供本轮验证，不是后续演示/服务器数据。

JAR SHA-256：`71f2bee0de288fe7e311e28fa121566d8763b1092ac2a2c4f7d0b2ce451fdbfb`。

在真实内置浏览器访问JAR托管页面，逐一切换检索/文档/任务Tab，均呈现初始化说明与禁用业务按钮；手动读取真实`/health`得到UP/C0/false，页面明确区分基础健康与业务就绪。截图见`evidence/c0-browser.png`。

验证后停止专用测试实例，日志确认Web graceful shutdown及Hikari shutdown completed。测试端口不继续驻留；没有服务器部署或公网访问。

额外检查：`node --check`前端脚本、`bash -n`部署预检、`git diff --check`通过；服务器角色的本地只读preflight及受控lsof分支检查见其交接，不当作实机systemd/Nginx通过。

## 明确未完成的后续内容

六业务接口、确定性向量器、调度和run_token、资源限额实际执行、业务恢复/降级/CSRF门禁、并发/未知提交/强杀测试、全量UI真接口联调、负载测试、服务器发布与GitHub远程CI均未完成。

现有`CREATE TABLE IF NOT EXISTS`只适合初始化，不自动升级旧列；C0新库通过不代表旧schema迁移通过。未来若保留早期库，需另加显式迁移与测试。

