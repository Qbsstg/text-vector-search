# C0-DEP 交接

- 任务：C0-DEP；分支 `agent/server`；基线 `0f692387577be629d296d3a5fe0ba626072f93b4`。
- 修改文件：`deploy/README.md`、`deploy/env/app.env.example`、`deploy/systemd/vector-search.service.example`、`deploy/scripts/preflight.sh`、本交接文件。
- 契约/依赖：没有修改。遵循JAR `target/vector-search.jar`、回环8080、显式APP_DATA_DIR、C0健康businessReady=false；systemd暂留120s并要求后续实测，Spring45s是每阶段而非总时间。
- 产物：非root/systemd与环境模板、只读预检、Nginx设计说明、后续验收/备份/回滚门禁。所有账号/主机/证书均待确认；没有生成复杂发布脚本或CI。

## 真实验证

- `bash -n deploy/scripts/preflight.sh`：通过。
- `bash deploy/scripts/preflight.sh --help`：通过，输出参数与只读边界。
- 本地以Temurin17绝对路径、`--data-dir /private/tmp`、主工作区已有JAR `/Users/qbss/code/12times/target/vector-search.jar`、端口8084运行预检：退出0；Java17、目录访问、非空可读JAR、无可见监听检查通过。只读取主工作区JAR，不启动应用，不打开H2，不在/tmp创建数据。
- Python subprocess参数拒绝验证：缺参数、缺值、未知参数、65536端口、相对数据路径、不存在JAR均正确退出1。未增加测试文件。
- 旧PID检查首次暴露沙箱拒绝ps时误判退出的问题，已修正为进程列表读取失败就退出1。复验正确报告 `Cannot inspect process IDs; previous process exit remains unverified`；实际进程列表不可读，不声称真实旧PID检查通过。
- `git diff --check`：通过。
- lsof分支区分退出1且无输出（无可见监听）与其他异常/诊断；检查错误以UNKNOWN退出1，不再视为端口空闲。

## 未执行与风险

- 未执行Maven构建（本任务仅文档/模板/脚本），没有修改Java、依赖或基础配置。
- 未进行远程登录、部署、push、系统配置写入、服务控制、端口开放或数据恢复/删除。
- 未进行实机systemd校验、Nginx/TLS/认证验证、业务就绪/关闭/恢复/备份验证；需要真实主机与部署授权。
- lsof可见性受运行账号限制；无可见监听不证明无JVM占H2。访问位检查不写入探针，因此不证明实际写入、剩余容量和配额；需操作者另行核对。
- 当前业务30s等待尚待Owner实现；unit的120s预算需按实际阶段和关闭演练调整。配置值存在不表示业务容量门禁已实现。
- 建议主控审查：systemd外层超时及硬化路径、env与runtime契约一致性、预检失败保守处理、C0健康不得用于最终上线门禁。
- 下一任务：主控审查合入；业务关闭/就绪完成且获得服务器信息和授权后再收敛真实模板与部署验收。
- 提交SHA：以本交接所在提交为准，由回报提供，避免自引用提交哈希。
