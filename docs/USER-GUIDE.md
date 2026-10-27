# 项目使用说明

[返回项目交付资料](DELIVERY.md) · [系统设计与技术选型](DESIGN.md) · [AI协作与关键对话](AI-COLLABORATION.md) · [项目文档目录](README.md)

本文介绍环境配置、服务启动、页面操作和API调用，按当前已发布前端与API契约编写。以下命令及响应示例用于说明操作流程，实际验证记录见[测试与质量验证](testing/FINAL-ACCEPTANCE-REPORT.md)。

## 1. 准备环境

- JDK17：本项目构建明确校验Java主版本17。确认`java -version`和`./mvnw -version`都使用17。
- Git、可联网下载Maven依赖的环境。仓库自带Maven Wrapper，无须另装H2服务或手工创建表。
- Node.js 22.16.0仅用于可选前端测试，运行网页和JAR不需要Node服务或前端打包。
- 本机可写的数据目录及空闲回环端口，默认8080。同一H2数据目录只运行一个实例。

macOS若装有多个JDK，可先选择17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

## 2. 克隆、构建和启动

macOS / Linux的Bash命令：

```bash
git clone https://github.com/Qbsstg/text-vector-search.git
cd text-vector-search
java -version
./mvnw -version
./mvnw -B -ntp clean verify
mkdir -p .local-data
APP_DATA_DIR="$PWD/.local-data" java -jar target/vector-search.jar
```

保持启动终端运行，在浏览器打开 [本地演示台](http://127.0.0.1:8080/)。应用绑定回环，前端和API同源。

这里从main构建源码；main可能包含后续文档和模板更新，不承诺本地新构建与固定生产JAR具有同一字节摘要。已发布版本及其CI见[项目交付资料](DELIVERY.md)，本地构建不会替换生产包。

Windows PowerShell可使用：

```powershell
git clone https://github.com/Qbsstg/text-vector-search.git
cd text-vector-search
java -version
.\mvnw.cmd -B -ntp clean verify
New-Item -ItemType Directory -Force .local-data | Out-Null
$env:APP_DATA_DIR = (Resolve-Path .local-data).Path
java -jar target/vector-search.jar
```

如8080已占用，Bash启动时可加`PORT=18080`，并相应打开`http://127.0.0.1:18080/`；不要对同一数据目录重复启动实例。

### 首次启动如何自动建表

[application.yml](../src/main/resources/application.yml)指定H2文件模式，并配置`spring.sql.init.mode=always`及`classpath:schema.sql`。启动会执行随项目提供的[建表DDL](../src/main/resources/schema.sql)，创建`documents`、`embedding_tasks`和索引，不需要手工导入SQL。

上述命令生成`.local-data/knowledge.mv.db`；如果未设置`APP_DATA_DIR`，默认使用当前工作目录下的`data/knowledge.mv.db`。DDL使用`IF NOT EXISTS`，重复启动保留已有表与数据；它不负责任意旧结构的自动迁移。数据目录须可写，不能使用生产库或其他工作树的数据。

可在另一个终端只读检查本地就绪状态：

```bash
curl -sS http://127.0.0.1:8080/health
```

健康在真实业务就绪时返回200，否则503；`businessReady`并不保证所有未来写入一定成功，也不代表所有UI/发布门禁通过。公网演示的健康、H2及管理路径受保护，不应照搬本地健康URL到公网判断部署失败。

## 3. 五分钟页面操作

每次演示使用新的ASCII文档ID，例如`review-demo-01`；若重复使用已存在ID会409，不会覆盖原文。以下使用合成文本，渠道取一个本地专用值，如`review-demo`。

### 提交并查看任务

1. 打开“文档管理”，点击“新增文档”。
2. 填写文档ID、原文`设备离线处理：先检查供电，再检查网络。`和专用渠道，点击“提交文档”。
3. 接收成功返回taskId和QUEUED，只表示持久化接收；点击“查看真实任务”，或到“任务追踪”填写真实taskId并点击“查询任务”。
4. 查看服务器实际状态。任务可能很快完成，未观察到RUNNING并不意味着同步执行。

QUEUED为等待领取，RUNNING为处理中，SUCCEEDED为向量完成，FAILED为已持久化业务失败。读取网络错误不能被解释成任务FAILED；不应为了展示四态故意破坏数据库。

自动跟踪每次响应后至少间隔2秒，最多同时3个任务，单任务累计预算60秒；页面隐藏暂停，隐藏时间计入预算，返回可见或手动刷新不重置截止时间。终态停止自动刷新。预算耗尽后可以“刷新一次”，不代表后台任务被取消。

### 列表、详情与检索

1. “文档管理”中填写同一渠道，点“应用筛选 / 刷新”；翻页保留已应用条件，改变筛选后先重新应用。
2. 按文档ID查看详情，核对原文、SUCCEEDED、向量就绪、有效状态和命中次数；GET不会计数。已发布详情还会展示实际版本、维度、字节数、是否零向量和SHA-256指纹，固定版本和证据见[本轮核证](testing/VECTOR-INFO-RELEASE.md)。
3. 切到“检索体验”，输入同一原文、Top-K=1及专用渠道，点“开始检索”。成功且有效的同文应进入结果；相似度是字面特征余弦分数，不是概率或语义准确率。
4. 再读取详情，核对这次成功检索对最终命中的计数增量。不要用刷新详情的次数解释hitCount。

零分、负分和空查询合法；空查询结果按零分/ID顺序返回，也会计数。若渠道内有其他文档，K>1的所有最终命中都会各加一，不能假设只增加第一名。

### 查看向量与核对同文一致性

在支持API v1.1的详情页中，向量信息来自数据库已保存结果，查看、展开和复制都不重新向量化。默认预览前8维、六位小数；可展开全部256维，复制完整向量保留float32可往返精度和负零，不能以舍入预览代替完整值。

同一原文使用两个不同ID提交并完成后，可以对照实际算法版本与完整向量指纹；两篇仍有独立任务、失效状态和命中计数。相同ID再次提交409。这是按ID管理文档，不是按原文或向量指纹去重；相同指纹也不作为原文唯一性的证明。

空文完成后显示合法零向量和就绪状态。未完成显示尚未生成；兼容旧服务未返回详情字段时会明确提示未提供信息，不伪造向量。已失效但已完成的文档仍可查看历史向量。

### 主动失效

在详情中点击“标记失效”，在“确认标记失效”弹窗中确认。取消不发失效请求。成功后文档、任务和历史计数仍保留，当前没有恢复有效操作。

失效不会自动重搜；原搜索结果会标明历史快照。手动发起新搜索才应排除该文档。失效与SUCCEEDED、vectorReady相互独立，失效不把任务改成失败。

## 4. 本地六接口示例

下面的Bash示例额外使用curl与Python3读取taskId；它们是可选客户端工具，不是应用运行依赖。逐段执行、检查响应后再继续。命令只指向本机，不带写入自动重试；任何提交错误、网络结果未知或解析不到taskId时先停止，不继续后续写入，也不换ID盲目重提。

### 一次提交

在另一个终端中执行：

```bash
DEMO_BASE_URL='http://127.0.0.1:8080'
DEMO_DOCUMENT_ID="review-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
DEMO_CHANNEL="$DEMO_DOCUMENT_ID"
DEMO_ACCEPTED=$(curl --fail --silent --show-error \
  -X POST "$DEMO_BASE_URL/api/documents" \
  -H 'Content-Type: application/json' \
  --data "{\"documentId\":\"$DEMO_DOCUMENT_ID\",\"text\":\"设备离线处理：先检查供电，再检查网络。\",\"channel\":\"$DEMO_CHANNEL\"}")
printf '%s\n' "$DEMO_ACCEPTED"
DEMO_TASK_ID=$(printf '%s' "$DEMO_ACCEPTED" | python3 -c 'import json,sys; print(json.load(sys.stdin)["taskId"])')
```

成功接收按契约为HTTP202、响应含真实taskId、documentId与`status:"QUEUED"`。随机ID也作为独立渠道，便于只观察本次样例，避免影响其他文档计数。

### 查询任务、列表和详情

```bash
curl -sS "$DEMO_BASE_URL/api/tasks/$DEMO_TASK_ID"
curl -sS --get "$DEMO_BASE_URL/api/documents" \
  --data-urlencode "channel=$DEMO_CHANNEL" \
  --data-urlencode 'page=0' --data-urlencode 'size=20'
curl -sS "$DEMO_BASE_URL/api/documents/$DEMO_DOCUMENT_ID"
```

若尚为QUEUED/RUNNING，稍后只读查询同一taskId；确认SUCCEEDED和vectorReady后再检索。列表返回`items/page/size/totalElements`，分页从0开始；成功任务的`document`包含文档信息。失败时查看实际errorCode/errorMessage，不伪造成功。

### 一次同步搜索及计数

```bash
curl --fail --silent --show-error -X POST "$DEMO_BASE_URL/api/search" \
  -H 'Content-Type: application/json' \
  --data "{\"text\":\"设备离线处理：先检查供电，再检查网络。\",\"topK\":1,\"channel\":\"$DEMO_CHANNEL\"}"
curl -sS "$DEMO_BASE_URL/api/documents/$DEMO_DOCUMENT_ID"
```

在没有其他写入/搜索的独立渠道中，预期返回本次文档，详情hitCount从字符串`"0"`变为`"1"`。这是预期说明，实际结果以响应为准；重复执行搜索会继续加一。时间字段为UTC ISO-8601，页面展示为北京时间；hitCount保持字符串，避免大整数精度丢失。

### 失效并再次检索

```bash
curl --fail --silent --show-error -X PUT \
  "$DEMO_BASE_URL/api/documents/$DEMO_DOCUMENT_ID/invalidation"
curl -sS "$DEMO_BASE_URL/api/documents/$DEMO_DOCUMENT_ID"
curl --fail --silent --show-error -X POST "$DEMO_BASE_URL/api/search" \
  -H 'Content-Type: application/json' \
  --data "{\"text\":\"设备离线处理：先检查供电，再检查网络。\",\"topK\":1,\"channel\":\"$DEMO_CHANNEL\"}"
```

PUT不发送请求体，不附加`{}`。预期文档invalidated=true、任务仍SUCCEEDED、向量仍就绪；新搜索hits为空且不再增加该文档计数。重复失效保持首次时间。

## 5. 输入规则与异常处理

| 情况 | 使用时应如何理解 |
| --- | --- |
| ID、文本、渠道 | ID为1—128位约定ASCII字符，不自动裁剪；空文本允许，null不允许。提交省略/null渠道为default，搜索/列表省略/null渠道为全部；显式空白渠道400。 |
| Top-K与文本长度 | Top-K必须为JSON整数1—100；文本原始及规范化长度分别最多20000 code points，JSON正文另有字节上限。 |
| 400 / 404 / 409 | 修正参数；核对文档ID/taskId；重复ID不覆盖。详情可按已知ID查询。 |
| 429 | 容量或搜索许可保护，不是成功；不自动重放写请求。失效不会释放文档容量。 |
| 503 / RESULT_UNKNOWN / 网络失败 | 先保留原ID与响应，暂停写入；提交可按原documentId只读核对。搜索可能已计数，不能凭累计计数猜测是否提交，不自动重试。 |

完整字段、错误结构及来源规则见[API契约](api/api-contract.md)。本手册不提供绕过认证、故障注入、清库或数据库直接修改步骤。

## 6. 正常停止、重启与可选测试

在启动终端按Ctrl+C，等待正常关闭后，再以同一`APP_DATA_DIR`执行启动命令。数据库文件继续保留，已提交数据应可读取；未完成任务依启动恢复处理。不要同时打开同一库的第二个应用或在活动库上直接复制备份。

可选前端测试命令：

```bash
node --test tests/frontend/*.test.mjs
```

`clean verify`执行Surefire的`*Test`和Failsafe的`*IT`，报告在`target/surefire-reports/`与`target/failsafe-reports/`。Node测试、浏览器案例、HTTP请求和历史70条需求映射分开统计；已有结果见[测试与质量验证](testing/FINAL-ACCEPTANCE-REPORT.md)。

## 7. 在线演示访问

[正式HTTPS演示站点](https://text-vector-search.lemoncharger.com)使用入口认证。通过项目负责人授权渠道获取访问方式，公开仓库不提供密码；不要把凭据写入curl命令参数、Git、日志或截图。

页面操作顺序与第3节一致。正式站点每次提交永久占用文档容量，失效不释放，搜索真实计数；演示使用少量合成内容与专用渠道，不把上面的本地API示例随意改成公网批量执行。生产健康/H2/管理路径不公开。本轮文档没有验证此刻服务可用性或执行新的线上操作。
