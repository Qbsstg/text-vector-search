# B1 后端实施交接

日期：2026-10-22。角色：后端工程师，gpt-6.1-sol / high。

## 任务与所有权

- 任务：B1纯Java向量器、codec、余弦、Top-K及Spring统一实例绑定。
- 工作树：`/Users/qbss/code/12times/.worktrees/backend`；分支：`agent/backend`。
- 基线：`224bc791b114b1e3a788585dad0ee6baa8c86ad2`。
- 已读最新AGENTS、B1算法契约v1、运行/API契约。类名、方法和版本已先同步主控与测试角色，实际接口未偏离。
- 仅改显式授权的embedding/search、新EmbeddingConfiguration、unit及本交接；未改变pom、DDL、公共DTO、VectorProperties、application.yml或公共契约，未实施B2—B4。

## 实现与接口

| 文件/类 | 责任与接口 |
| --- | --- |
| `embedding/TextEmbedder.java` | 窄接口，DIMENSIONS=256、embed(String)、version()；入库/查询后续应共用此接口实例 |
| `embedding/CharNgramTextEmbedder.java` | 构造参数依次cpuRounds、maxTextCodePoints；原始UTF-16/长度校验、固定规范化、code point 1+2 gram、逐特征SHA-256、符号桶、固定顺序L2归一化 |
| `embedding/VectorCodec.java` | 静态encode(float[])/decode(byte[])，ENCODED_LENGTH=1024；显式BIG_ENDIAN、有限值/尺寸检查、保留负零原始位、独立结果存储 |
| `config/EmbeddingConfiguration.java` | 通过VectorProperties绑定一个不可变TextEmbedder Bean；算法本体不依赖Spring |
| `search/CosineSimilarity.java` | 静态score(float[],float[])，验证两个完整输入，double累加真实范数、零范数正零、结果钳制[-1,1] |
| `search/ScoredDocument.java` | 不可变record；验证ASCII ID、有限[-1,1]分数、将负零规范为正零 |
| `search/TopKSelector.java` | 静态select(Iterable<ScoredDocument>,int)，单遍有界堆、只排序保留项、不可变结果；过滤和ID唯一由调用方保证 |

关键逻辑已附Javadoc和原因注释：不跨空白bigram、NBSP空白谓词、digest重置与本地可变状态、固定浮点累计、codec原始位和字节序、完整向量校验、堆头为何必须是最差项及同分ID反向。显式imports、Java17、四空格缩进，无新依赖。

默认版本：`char12-sha256-r32-nfkc-root-ws-v1`。轮数R改变则版本为`char12-sha256-r<R>-nfkc-root-ws-v1`。maxTextCodePoints是接收约束，不改变同一合法文本的向量。

Top-K初始堆容量为min(k,64)，实际保留min(K,N)，不会因Integer.MAX_VALUE分配巨大数组；不增加O(N)去重集合，不截断候选，不加分数阈值。

## 有意义的代码提交与提交前测试

1. `fafcbd51e95e202305439299ec0c5f1805a9dbac` — `feat: add deterministic character vectors and binary codec`。
   包含算法、codec、Spring绑定及三个unit类。提交前对应14个unit通过（CharNgramTextEmbedderTest 9、VectorCodecTest 4、EmbeddingConfigurationTest 1），0失败/错误/跳过。
2. `847210d2b9620939455527ad8d8f650f3dde2dc8` — `feat: add exact cosine scoring and bounded top-k selection`。
   包含余弦、候选record、Top-K及两个unit类。提交前对应12个unit通过（CosineSimilarityTest 5、TopKSelectorTest 7），0失败/错误/跳过。

本文件是随后独立文档提交，提交SHA在交接消息给出；不编造文件内自引用SHA。

## 实际运行命令与结果

本机每次Maven命令都显式使用：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
MAVEN_USER_HOME=/private/tmp/12times-maven-user-home \
./mvnw -B -ntp -s /private/tmp/12times-maven-settings.xml \
  -Dmaven.repo.local=/private/tmp/12times-m2 <goals>
```

`<goals>`按先后为：

- `-Dtest=CharNgramTextEmbedderTest,VectorCodecTest,EmbeddingConfigurationTest test`：14通过；显式import整理后再执行一次仍14通过，之后才第一次代码commit。
- `-Dtest=CosineSimilarityTest,TopKSelectorTest test`：12通过，之后才第二次代码commit。
- `clean verify`：首次29个unit通过，6个C0 IT中HTTP项因沙箱禁止socket监听报Operation not permitted，其余5项通过；未修改测试或弱化断言。
- 获本机socket执行权限后原样重跑`clean verify`：2026-10-22 22:20:54 +08:00完成，BUILD SUCCESS；Surefire **29通过**（B1新增26、既有schema 3），Failsafe **6通过**（既有C0），均0失败/错误/跳过。

最终代码验证SHA为第二个代码提交。完整日志本机临时路径：`/private/tmp/12times-backend-b1-verify.log`；报告在工作树`target/surefire-reports/`和`target/failsafe-reports/`，不提交。临时settings/cache只是本机依赖适配，不成为项目部署要求。C0 HTTP测试绑定127.0.0.1随机端口、H2使用JUnit临时绝对目录，没有共享主数据库。

提交均显式git add授权文件；提交前cached diff --check通过。交付前另检查提交差异和工作树状态。

## 验收覆盖与黄金摘要

- 输入：空/Unicode空白、NFKC、ROOT大小写、空白边界、标点保留、中文/单字/emoji、原始code point上限、NFKC及小写扩张上限、孤立代理单元拒绝。
- 确定性：重复、24个并发调用、独立返回数组；黄金字节摘要由单独Python hashlib/struct参考计算后固定，在Java17实际通过。未执行旧探索脚本，未使用预研相关性分数。
- codec：固定BIG_ENDIAN字节、正负零、最小subnormal/normal、最大有限正负数、非法长度及NaN/Infinity拒绝、输入输出存储隔离。
- 余弦：未单位化、同向/反向/正交、零范数、有限极值、完整校验、对称性及不改输入。
- Top-K：独立手写全排序参考，250候选、15次固定seed打乱、7种K（含Integer.MAX_VALUE）；同分淘汰、ASCII ID、零负分、非法输入、单遍Iterable、不改输入、不可变结果。

以下为VectorCodec.encode输出1024字节的SHA-256，不是业务准确率或性能：

| 输入 | R | SHA-256 |
| --- | --- | --- |
| 空字符串 | 32 | `5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef` |
| `a` | 1 | `d2987869c01cdc5afa71fd78034ccee55022ab11b288d7c3da9eccadcc0aa207` |
| `Ａb 你🙂` | 32 | `e5d2ef07b4dbefb919a4eb325c8d3d8e216dd3584d86613a8d2a816b4a8aaf85` |
| `重复字符AAAA` | 32 | `f56126b4d52753bf7286c72a0c9f486b121f2197e2d08049e663daf15f761ca7` |

## 未执行、限制与下一步

- 独立跨JVM及更完整参考验收由测试角色执行；本角色未把该部分记为已通过。测试角色已收到完整代码SHA与接口。
- 尚未测量吞吐、相关性、内存或服务器性能；Java17以外的JDK/Unicode版本不承诺按位兼容。
- Top-K要求调用方先过滤并保证ID唯一；后续B4需履行，B1不引入数据库/HTTP逻辑。
- B2—B4任务、事务、恢复、API、真实前端联调、发布均未执行；当前/health仍为C0且businessReady=false，不能称业务已就绪。
- 主控需审查精确位映射、规范化、StrictMath/固定顺序、codec、比较器和测试，再合入并执行集成树完整verify。收到下一工作包前停止扩展实现。
