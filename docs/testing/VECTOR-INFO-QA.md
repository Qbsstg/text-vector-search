# VECTOR-INFO-QA 独立验收

任务ID：VECTOR-INFO-QA。基线：`7303b9b5c408e8eee90b0f34867b4caff091c367`；分支：`codex/vector-info-qa`。本次仅新增本报告及 `src/test/java/dev/twelvetimes/vector/acceptance/VectorInformationIT.java`，未修改生产实现、公共契约、DDL、参数或其他工作树；未部署、未推送。

## 执行与结论

2026-10-27，Java17离线执行以下同一命令（HTTP绑定经执行工具授权）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw -o -B -Dtest=VectorInfoContractTest,DocumentVectorInfoTest -Dit.test=VectorInformationIT verify
```

最终 `BUILD SUCCESS`，耗时8.548秒；相关单元测试11项、新增独立IT 9项，共20项，0 failure、0 error、0 skipped。该命令仅运行指定测试，**不是全量verify**。主控另跑完整工程回归。

| 用例 | 实测结果与边界 |
| --- | --- |
| 真实同文不同ID | PASS：真实HTTP提交/B1处理，两任务两记录；原始存储字节、SHA和HTTP全256维f32原始位一致，不按指纹去重 |
| 空文和纯空白 | PASS：真实生产组成得到1024零字节，256值全零，成功且就绪 |
| 三种非成功状态 | PASS：QUEUED/RUNNING/FAILED人工异常状态即使已植入字节，也不返回假向量；任务document为null |
| 失效历史 | PASS：真实失效后向量、SHA、原文保留，仍就绪；重复GET不改变失效时间、状态、计数或字节 |
| 极值/负零/实际版本 | PASS：人工合法f32含最大有限数、最小次正规数、最小正规数与负零，HTTP解码再BIG_ENDIAN重编码逐位相等；取文档保存版本而非不同的任务版本；大hitCount仍为字符串 |
| 非有限坏向量 | PASS：临时库分别注入NaN及正负Infinity，真实HTTP统一500 INTERNAL_ERROR；无向量占位、堆栈或NaN泄露，存储未改 |
| 单SQL与跨文档竞态 | PASS：真实MyBatis查询完成后latch暂停A响应，B独立读取且A同时失效/计数提交；旧响应仍为旧一致快照，新GET显示新状态；三次GET各一次专用查询 |
| 终态提交竞态 | PASS：无后台处理器的隔离组成，真实领取后暂停RUNNING快照读取，再用生产TaskTerminalService及真实Mapper/事务/写屏障提交成功；旧响应无新向量，新响应完整SUCCEEDED/256维。向量为人工零向量夹具，不宣称此用例执行B1 |
| 文件重启 | PASS：真实生产组成正常关闭，再打开同一独立文件库；文档JSON、字节、任务元数据完全一致，重启未调用embed |

只读兼容断言包含：详情/列表/任务GET不增计数、不改时间/状态/字节；计算监视实际观测两次真实提交的B1执行，随后读取无新增调用；成功任务document保留原文且vectorInfo为null，列表不返回vectorInfo。坏长度受DDL保护，已有相关unit覆盖，不为造坏数据修改DDL。

## 首次失败及修复轨迹

首次执行真实失败，永久保留，不以最终通过覆盖：11 unit中1 failure、1 error，IT未执行。原因是新增QA嵌套@TestConfiguration被现有显式ComponentScan发现，Control尚未初始化，钩子空引用污染单元测试并造成503。仅给本IT钩子添加默认关闭的专用条件与显式启动参数，未改生产实现或断言。第二轮11 unit、8 IT全部通过。

补强第9项终态竞态后，第三轮11 unit通过，9 IT中1 error：B2隔离组成按设计没有TaskTerminalService bean。仅在本IT显式装配生产TaskTerminalService，使用真实Mapper、事务管理器和写屏障（与既有终态IT方式一致），未降低断言；第四轮11 unit、9 IT全部通过。

原始本地证据：首轮日志 `/private/tmp/vector-info-qa-first.log` 与XML目录 `/private/tmp/vector-info-qa-first-reports/`；第二轮日志 `/private/tmp/vector-info-qa-second.log` 与XML目录 `/private/tmp/vector-info-qa-second-reports/`；第三轮日志 `/private/tmp/vector-info-qa-final.log` 与XML目录 `/private/tmp/vector-info-qa-third-reports/`；第四轮日志 `/private/tmp/vector-info-qa-fourth.log` 与XML目录 `/private/tmp/vector-info-qa-fourth-reports/`。原始材料不作为公开提交内容。

## 最终原始XML核账

| XML | tests | failures | errors | skipped | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| TEST-dev.twelvetimes.vector.unit.DocumentVectorInfoTest.xml | 9 | 0 | 0 | 0 | `7d4ce6aeb586fe4508b837f660ab045f0e41db9e258408d2a431451b03d5a6d6` |
| TEST-dev.twelvetimes.vector.unit.VectorInfoContractTest.xml | 2 | 0 | 0 | 0 | `1edfea1b410f950ea8d48754262e27e2c1cbc7b5f5ceea1635742d00414c0f08` |
| TEST-dev.twelvetimes.vector.acceptance.VectorInformationIT.xml | 9 | 0 | 0 | 0 | `12a221c60f1edf517db921a748d4e9d3b4a98386dcb31c1bbf0a0b7e22979bbf` |

## 未执行与风险

本QA未执行全量回归、真实浏览器、窄屏、键盘、XSS、剪贴板负零/完整精度、服务器或公网验收。真实浏览器归主控；HTTP负零往返PASS不替代浏览器复制PASS。此处文件重启是正常关闭重启，不代表进程崩溃或断电恢复验证。未发现本功能实现缺陷；两次失败均为新增QA测试隔离/装配问题，原始失败保留。

一次SQL快照经实测与Mapper审查成立，详情专用查询读取`d.embedding_version`及原始字节，列表/任务保持轻量。API属于已批准的nullable只读响应扩展，原路径/请求规则/旧字段类型保持；严格拒绝未知字段的外部客户端仍须适配新增vectorInfo，不将其描述为响应字段完全不变。
