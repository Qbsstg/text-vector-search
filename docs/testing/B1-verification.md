# B1集成验收记录

> **历史阶段说明**：本文件记录B1 算法与基础组件的历史阶段验收。正文、原始 FAIL、测试数量和时间线原样保留，不代表项目当前仍未交付。最终结论请查阅[《最终交付聚合结果》](最终交付聚合结果.md)。

2026-10-22，Owner主控。范围为确定性向量、codec、余弦和Top-K；未进入B2-B4六业务接口与调度。

## 真实代码与提交

- 基线契约：`224bc791b114b1e3a788585dad0ee6baa8c86ad2`。
- 后端`fafcbd51e95e202305439299ec0c5f1805a9dbac`：算法、codec、Spring统一实例；提交前14个对应unit通过。
- 后端`847210d2b9620939455527ad8d8f650f3dde2dc8`：余弦及Top-K；提交前12个对应unit通过。
- 独立QA`a82b4258f07d63e57f79ad51a496cfbfbda3c133`：先目标16项B1验收，再完整29个unit+22个IT通过后提交。
- 主控集成实测SHA：`27b850ca5051c6024734a9ff28fd5dba781a1791`。随后README、运行说明、任务/验收等仅改文档；如源代码/构建配置变化需重新验证。

所有代码提交保留真实时间线，未重写历史。核心实现有详细Javadoc及原因注释，使用Java17、四空格、显式import，不新增依赖。

## 主控最终回归

2026-10-22 22:24:05 +08:00，BUILD SUCCESS，命令退出0，**29个单元测试 + 22个验收测试，失败/错误/跳过均0**。其中B1独立验收16项，C0回归6项。

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
-Dmaven.repo.local=/private/tmp/12times-m2 clean verify
```

临时settings/cache只适配本机受限环境；交付命令仍是JDK17下`./mvnw -B clean verify`。Surefire/Failsafe报告在target内，不提交。

| 验收 | 已验证内容 |
| --- | --- |
| 确定性 | 256维、空/空白零向量、重复按位一致、版本含R、NFKC/ROOT/空白/边界、中文/单字/emoji、非法UTF16、原始/规范化后超限 |
| 黄金参考 | 8组固定SHA由独立Python标准库/人工规范化预期生成；明确核对全角逗号NFKC规则，不抄生产输出 |
| 独立进程/并发 | 8次独立JVM逐字节对照；7线程barrier并发、多轮重复及返回数组修改后不串扰 |
| codec | BIG_ENDIAN、恰好1024字节、负零/最小/最大有限值原始位往返、非法长度及非有限值拒绝 |
| 余弦 | 非单位向量、零范数、反向、极值、NaN/Infinity拒绝、范围钳制与规范零分 |
| Top-K | 独立全量排序参考、打乱候选、同分ID顺序、零负分、K>N/极大K、不可变输出及不改输入 |
| Spring | 生产扫描只生成一个TextEmbedder，按现有VectorProperties绑定轮数与限额 |
| C0回归 | 文件库正常重启、真实HTTP、配置绑定与非法值启动失败、DTO类型保持 |

主控逐类检查了hash摘要轮数/位映射、固定归一化顺序、局部MessageDigest、字节序、余弦验证与堆比较器，未发现阻塞B1的问题。

## 外部交付状态与边界

私有仓库`Qbsstg/text-vector-search`已创建并推送完整B1及真实Git历史，private经连接器与CLI核对。远程main已核对为`de576934167049ca4104567b8773b69a942f411a`，随后仅补充文档记录。

最小CI固定官方Action完整SHA。[B1 CI运行39796113841](https://github.com/Qbsstg/text-vector-search/actions/runs/39796113841)对应该提交，verify任务`119883663996`于22:30:23 +08:00完成，状态completed、结论success，全部步骤成功。主控已下载其`test-reports-de576934167049ca4104567b8773b69a942f411a`产物逐份解析：Surefire29、Failsafe22，失败/错误/跳过均0。XML实测环境为Eclipse Adoptium17.0.15、Linux/amd64，证实本轮固定黄金在本机macOS/arm64及CI Linux/amd64均通过。核对时工作流汇总元数据尚在收尾，不将任务状态和汇总状态混称；没有只根据本地结果判断远程通过。

服务器基础准备见`docs/tasks/handoffs/server-dep2.md`。主控另用严格主机校验SSH复核：unit loaded/disabled/inactive/dead、NRestarts=0；db目录0700、env0640、backup0700、unit0644；无current发布链接，8080无监听。未读取env内容。

未验证上述两环境之外的JDK/平台、算法相关性准确率或性能；未执行B2-B4事务/任务/命中/强杀恢复、全量UI或应用服务器发布。正常进程向量一致性不等于业务崩溃恢复。

执行期间用户新放入`docs/测试工作/`资料包。已快速核对其算法验收边界，原文件保留未提交；其中70条用例并未由本轮逐项执行，不能与51项实际JUnit测试数量混算。
