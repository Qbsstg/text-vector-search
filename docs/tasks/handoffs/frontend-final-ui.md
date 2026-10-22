# FE1—FE3 最终 Chrome 人工验收步骤与证据模板

2026-10-23。文档基线`cd400f57406d362fb8b672e24b0ea32a74e7fc4d`，`agent/frontend`独立工作树。仅交付本文件，不改业务源码、服务器或历史验收。依据API v1.0与`docs/design/final-readiness-release-contract.md`。

**当前所有浏览器项目均为 NOT_RUN。** 主控本轮检查仍为Work浏览器禁用，本文供用户本人在Chrome手工操作；本角色没有开启、调用或绕过禁用浏览器。既有Node、HTTP和Linux CI结果不升级为浏览器PASS。以下Console代码均为待执行模板，没有在真人浏览器执行过。

## 1. 固定候选与专用测试库

最终健康实现和CI候选仍由主控收敛。开始前取得同一候选的**绝对JAR路径、完整源码SHA、SHA256和CI链接**填入记录；不把`target/`中未知来源JAR或纯文档HEAD当候选。候选变更必须重记版本并重跑相关UI，不写入不存在的候选SHA。

用户在本机Terminal执行下列模板，先替换三个占位值。本步骤不构建、不触服务器、不借用项目data目录。每轮创建一个全新临时目录；使用8083前先检查是否已有监听，若占用就换空闲端口并同步修改后文地址。不要杀掉未知监听进程。

```bash
CANDIDATE_JAR='/绝对路径/主控提供的固定候选/app.jar'
CANDIDATE_SOURCE_SHA='填写候选完整源码SHA'
CANDIDATE_SHA256='填写候选SHA256'
UI_JAVA='/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java'
UI_PORT=8083
lsof -nP -iTCP:"$UI_PORT" -sTCP:LISTEN
```

确认没有其他进程使用该端口后，再执行：

```bash
test -r "$CANDIDATE_JAR" || exit 1
test "$(shasum -a 256 "$CANDIDATE_JAR" | awk '{print $1}')" = "$CANDIDATE_SHA256" || exit 1
UI_RUN_DIR=$(mktemp -d /private/tmp/12times-final-ui.XXXXXX)
chmod 700 "$UI_RUN_DIR"
mkdir "$UI_RUN_DIR/db" "$UI_RUN_DIR/evidence" "$UI_RUN_DIR/candidate"
cp "$CANDIDATE_JAR" "$UI_RUN_DIR/candidate/app.jar"
shasum -a 256 "$UI_RUN_DIR/candidate/app.jar"
printf '%s\n' "$UI_RUN_DIR" "$CANDIDATE_SOURCE_SHA" "$CANDIDATE_SHA256" > "$UI_RUN_DIR/evidence/run.txt"
python3 - "$UI_RUN_DIR" "$UI_JAVA" "$UI_PORT" <<'PY'
import os, pathlib, subprocess, sys
root = pathlib.Path(sys.argv[1]).resolve()
env = dict(os.environ, APP_DATA_DIR=str(root / 'db'), PORT=sys.argv[3])
with (root / 'app.log').open('wb') as log:
    process = subprocess.Popen([sys.argv[2], '-jar', str(root / 'candidate/app.jar')], cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    (root / 'app.pid').write_text(str(process.pid))
    print(f'本次专用目录：{root}\n本次PID：{process.pid}\nChrome打开：http://127.0.0.1:{sys.argv[3]}/', flush=True)
    try:
        process.wait(timeout=7200)
    except (KeyboardInterrupt, subprocess.TimeoutExpired):
        if process.poll() is None:
            process.terminate()
        try:
            process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            print(f'PID {process.pid} 仍未退出；保留目录，交主控诊断，禁止启动另一实例或复制/删除活动数据库。', flush=True)
            sys.exit(2)
    print(f'应用退出码：{process.returncode}；数据与证据目录保留。', flush=True)
PY
```

人工完整验收可能超过180秒，所以上述用户手工启动最多2小时；正常完成可在**这个Terminal**按Ctrl+C，或另一个Terminal只对打印的**本次PID**执行`kill -TERM 本次PID`，等待正常退出。保留`app.pid/app.log/db/evidence`供核证；不提供`rm`、生产清理或数据库替换命令。若启动失败先看本次app.log，不更改算法/线程/队列/容量或轮询参数。

Chrome访问打印的回环地址。最终HEALTH-FINAL候选应点击“检查 /health”看到真实B4字段和业务就绪；若仍是旧B3/false，记录实际响应并交主控核对候选，不伪造健康，也不据此宣称上线。200/businessReady=true同样不代表浏览器或DEP3已批准。

## 2. 证据准备和合成数据

Chrome打开DevTools：Network勾选**Preserve log、Disable cache**，显示Method、Status、Initiator、Time；Console勾选保留日志。先记录Chrome版本、macOS、视口、时区、候选SHA/hash、URL和本次临时目录。每项清空Network显示或记起止行号，不能把手工Console请求算成页面自动请求。HAR/截图保存至本次`evidence/`，仅本地合成数据，不保存/提交认证头、Cookie、私钥或真实原文。

在Console初始化**内存中**的验收命名空间，不写localStorage：

```javascript
window.uiAcceptance = { prefix: `ui-${Date.now()}`, tasks: [], docs: {} };
uiAcceptance.channel = uiAcceptance.prefix;
uiAcceptance.docs = Object.fromEntries(['a', 'b', 'empty', 'xss', 'default'].map(key => [key, `${uiAcceptance.prefix}-${key}`]));
console.table(uiAcceptance);
```

把prefix和生成ID复制到本次记录。之后页面刷新会清空本页会话，重新粘贴保存的**同一对象**即可继续；不要不加判断地重复POST。提交结果未知先按ID查询，409不得自动换ID掩盖问题。

| 文档 | ID取值 | 原文 | 渠道 | 用途 |
| --- | --- | --- | --- | --- |
| A | uiAcceptance.docs.a | `字面向量检索人工验收，同文排序与真实计数。` | uiAcceptance.channel | 正常流程 |
| B | uiAcceptance.docs.b | 与A完全相同 | uiAcceptance.channel | 真实同分 |
| 空文 | uiAcceptance.docs.empty | 输入框保持空，不填`null`或引号 | uiAcceptance.channel | 零向量 |
| XSS | uiAcceptance.docs.xss | `<img src=x onerror="window.__uiXssFlag=1"><script>window.__uiXssFlag=2</script><a href="javascript:window.__uiXssFlag=3">仅文本</a>` | uiAcceptance.channel | 原文/摘要安全 |
| 缺省 | uiAcceptance.docs.default | `默认渠道专用合成文档` | UI留空 | 提交default/搜索全部区别 |

前两个核心文档必须经下节真实页面控件提交。可用Console**只填表，不发送请求**，然后本人点击“提交文档”：

```javascript
function fillUiDocument(id, text, channel = '') {
  document.getElementById('submit-id').value = id;
  document.getElementById('submit-text').value = text;
  document.getElementById('submit-channel').value = channel;
}
fillUiDocument(uiAcceptance.docs.a, '字面向量检索人工验收，同文排序与真实计数。', uiAcceptance.channel);
```

## 3. 真实页面主流程（全部待执行）

每项均保存**操作前后截图、对应Network请求/响应/次数、Console错误**。无异常Console也记录“本项观察窗口内无新增异常”，不能以脚本未报错替代看页面。表中“1”指一次明确点击后新增的请求；任务GET按轮询另计。

| 项目/PRD | 具体操作与预期 | 请求与证据 | 当前状态 |
| --- | --- | --- | --- |
| U01 / UI01、18 | 首次打开，默认“检索体验”，没有搜索结果/虚构进度；JS加载后按钮可用，三个Tab可切换 | `/`、app.js、api.mjs、tasks.mjs、search.mjs、styles.css均200，本地同源，JS媒体类型有效；首次无POST搜索 | NOT_RUN |
| U02 / UI02 | “文档管理”填A，点击“提交文档”；202显示真实ID/taskId，提交按钮HTTP结束后恢复，不等待任务结束 | POST documents仅1；保存202、taskId并手工加入uiAcceptance.tasks。点击“查看真实任务”看后端四态与时间 | NOT_RUN |
| U03 / UI03、12 | 提交空文；最终服务器SUCCEEDED、vectorReady=true、原文为空；终态后自动GET停止 | 空text正文为`""`，不被required阻止；任务响应和页面一致。终态后至少10秒无新增自动GET；不要求肉眼看到过快的全部中间态 | NOT_RUN |
| U04 / UI04 | 用相同A ID再明确点一次提交 | 新POST仅1，409提示不覆盖；不再自动POST/换ID，原文详情不变 | NOT_RUN |
| U05 / UI05 | 提交“缺省”文档且渠道空；详情channel=default。搜索渠道空后明确检索一次 | 两个POST Payload均省略channel；提交为default，搜索包含全部渠道。不要把省略与发送`""`混同 | NOT_RUN |
| U06 / UI06 | Top-K分别0、-1、1.5、空；每次点检索，字段提示并保留输入，再恢复5 | 非法UI输入新增搜索POST为0；默认5为JSON数值整数。后端非法JSON另按下节Console直调，不算页面发包 | NOT_RUN |
| U07 / UI07 | 提交B，等成功；查询A全文，渠道专用，Top-K100。A/B同分按ID升序，显示4位小数，不转百分比 | 保存原始hits顺序和完整score，对照页面逐行；若出现负分不得隐藏。负分没有自然出现时按受控分支单独验，不捏造真实负分 | NOT_RUN |
| U08 / UI08 | 查询文本留空，渠道专用，明确点击检索 | 新POST仅1；出现空查询零分提示，所有score=0、ID升序、K>N返回全部。该次搜索仍计数；保存hits | NOT_RUN |
| U09 / UI09 | 计算中失效真实文档需实际观察到RUNNING并成功PUT；结束可同时SUCCEEDED/已失效 | 来不及遇到则真实并发UI项保留NOT_RUN，不能靠sleep/伪状态宣布后端竞态通过；受控前端分支另记 | NOT_RUN |
| U10 / UI10 | 失效确认点取消：有效状态不变；断网/阻断PUT后确认：失败保留原状态，提示待刷新确认 | 取消时PUT0；网络失败时仅一次尝试、不自动再次PUT，无提前“已失效” | NOT_RUN |
| U11 / UI11 | “任务追踪”输入真实taskId，“查询并有限刷新”；阻断下一次GET | 显示“状态暂不可获取/上次已知状态”，不能因网络改FAILED。404 UUID与真实FAILED分支按下节分类 | NOT_RUN |
| U12 / UI12 | 正常终态停止；隐藏页面、恢复；单任务60秒预算、最多3任务自动刷新 | 正常真实终态用U03；任务过快的时间/并发分支使用明确受控响应，记录间隔从上一请求完成起≥2秒，总预算不重置 | NOT_RUN |
| U13 / UI13 | 已有成功结果后设Chrome Network Offline，再明确点击检索，连续观察15秒后恢复Online，不再点按钮 | 一次失败尝试、无自动第二次POST；提示可能已计数/新请求另计；保留旧结果并标历史。完全Offline不证明服务器已提交，未知提交另分层 | NOT_RUN |
| U14 / UI14 | Console先`window.__uiXssFlag=0`；提交XSS，打开“详情 / 计数快照”和搜索摘要 | 原文尖括号/链接按文字显示、无alert/脚本/自动跳转；`__uiXssFlag===0`，Elements中原文/摘要为文本，无img/script/a子元素，无由x引起的额外网络请求 | NOT_RUN |
| U15 / UI15 | 搜索前后分别“按文档 ID 查询详情”/“刷新详情”，列表“应用筛选 / 刷新”；统计只取响应字符串 | 记录基准hitCount=`c`；一次返回该文档的搜索后`c+1`，连续GET不变。Network Response中类型为字符串，不由页面推算次数 | NOT_RUN |
| U16 / UI16 | 成功结果中点“详情 / 计数快照”→“失效文档”→确认→关闭；旧结果仍在并有历史/已失效文字 | PUT1；失效操作新增搜索POST0；不删除/重排旧hits。本人再点检索后排除该ID，计数与首次失效时间保留 | NOT_RUN |
| U17 / UI17 | 仅键盘完成Tab切换、表单、表格按钮、详情和关闭；视口375×812、768×1024和桌面，200%缩放 | 方向键/Home/End切换Tab，Tab焦点可见；dialog焦点被约束、Escape关闭并回到合理触发控件；窄屏表单可读、表格自身横滚、无操作截断。截图+屏幕录制/步骤记录 | NOT_RUN |
| U18 / UI18 | 干净页Network检查真实同源、无CDN/mock；点“检查 /health”保存完整Response | 不能把UP/taskReady或businessReady=true代替浏览器/认证/发布验收。身份/HTTPS由DEP3单列，不在回环页伪报通过 | NOT_RUN |

列表补充：渠道/有效状态筛选后翻页不丢条件；改表单未应用时旧结果标历史并暂不可翻页。用专用合成数据达到21条以上后检查上一页/下一页、每页20/50/100及空结果；列表caption说明快照、UTC+8、向量就绪与有效状态分列。详情长ID可读、原文独立滚动、空原文无假占位内容；“刷新详情”更新字符串hitCount。

如需分页夹具，可在专用库Console**明确执行一次**以下有界准备；这不是经UI提交验收，不是自动重试：

```javascript
for (let i = 0; i < 21; i++) {
  const body = { documentId: `${uiAcceptance.prefix}-page-${String(i).padStart(2,'0')}`, text: `分页合成片段 ${i} 海洋山林与城市交通`, channel: uiAcceptance.channel };
  const r = await fetch('/api/documents', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) });
  const v = await r.json();
  console.log('SETUP_ONLY', r.status, v);
  if (r.status !== 202) throw new Error('准备中止；未知结果先按ID查询，不自动重提');
  uiAcceptance.tasks.push(v.taskId);
}
```

只用合成库，可以明确发起少量不同查询观察自然负分；每次都是新搜索、按hits计数并单独记录。没有真实负分响应时，“真实负分浏览器”保持NOT_RUN；不能引用Node/后端夹具冒充。四位显示相同但完整score不同也可能自然不出现，使用下节受控渲染分支另记来源。

## 4. 协议直调与受控浏览器分支

### 4.1 协议直调（本人明确执行，不属于页面自动请求）

专用回环页Console每条只执行一次，Network保存Payload和错误形状；每次合法搜索都会计数，以下非法请求应400：

```javascript
await fetch('/api/search', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({text:'',topK:5,channel:' '}) }).then(async r=>console.log('MANUAL_PROTOCOL_ONLY',r.status,await r.json()));
await fetch('/api/search', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({text:'',topK:'5'}) }).then(async r=>console.log('MANUAL_PROTOCOL_ONLY',r.status,await r.json()));
```

合法`channel:null`与省略均全渠道，可在单独计数账本中各明确执行一次并比较筛选语义；不要把再次检索当重试，也不要据累计次数判断曾经断网的那一笔是否提交。其他400/413/403、容量429等后端证据与UI错误展示分层，不在生产堆满任务。

### 4.2 安装仅本页内存的故障观察器

用于过快任务/迟到响应/错误反馈的**受控前端行为验收**。不是后端真实RUNNING、负分或未知commit证据；Network真实响应、Console中受控响应与截图必须同时注明`FAULT_INJECTION`。普通主流程先完成并留证，再做本节，结束刷新页面清除。不要修改仓库、JAR或服务参数。

关键：页面`createApi()`会捕获fetch，**页面已加载后直接覆盖window.fetch无效**。在Sources找到`app.js`的`const api = createApi();`行打断点，Reload；暂停在该行、尚未执行时，在Console粘贴保存的`uiAcceptance`对象（含真实taskId和合成prefix），再安装下面脚本，Resume。只加载一次页面，不重复import app.js，不改api.mjs。若无法按此方式暂停，分支记NOT_RUN，不借改业务代码凑结果。

```javascript
(() => {
  const a = window.uiAcceptance;
  if (location.origin !== 'http://127.0.0.1:8083' || !a?.prefix?.startsWith('ui-')) throw new Error('仅允许本次8083专用合成验收页；换端口需同步核对');
  if (window.uiNet) throw new Error('已经安装，不重复包装');
  const original = window.fetch.bind(window);
  const gates = new Map();
  const control = window.uiNet = { log:[], holdNext:new Set(), failNext:{}, pendingTasks:new Set(), rewriteScores:false };
  const kindOf = (url, method) => method==='POST' && url.pathname==='/api/search' ? 'search'
    : method==='POST' && url.pathname==='/api/documents' ? 'submit'
    : method==='GET' && url.pathname.startsWith('/api/tasks/') ? 'task'
    : method==='PUT' && url.pathname.endsWith('/invalidation') ? 'invalidate'
    : method==='GET' && /^\/api\/documents\/[^/]+$/.test(url.pathname) ? 'detail' : 'other';
  control.release = n => { gates.get(n)?.(); gates.delete(n); };
  control.restore = () => { window.fetch=original; for(const done of gates.values()) done(); gates.clear(); console.warn('故障观察器已解除；刷新页面清除本次受控UI状态'); };
  window.fetch = async (input, init={}) => {
    const url = new URL(typeof input==='string' ? input : input.url, location.href);
    if(url.origin!==location.origin || !url.pathname.startsWith('/api/')) return original(input,init);
    const method = String(init.method || input.method || 'GET').toUpperCase();
    const kind = kindOf(url,method);
    const e = { n:control.log.length+1, kind, method, path:url.pathname+url.search, start:performance.now(), mode:'REAL' };
    control.log.push(e);
    try {
      const fail = control.failNext[kind]; delete control.failNext[kind];
      if(fail) {
        e.mode='FAULT_INJECTION'; console.warn('FAULT_INJECTION',e.n,kind,fail);
        if(fail==='network') throw new TypeError('本地受控网络失败；没有发往服务器');
        e.status=503;
        return new Response(JSON.stringify({code:'RESULT_UNKNOWN',message:'本地受控未知结果展示测试，未产生真实commit',fieldErrors:[]}),{status:503,headers:{'Content-Type':'application/json'}});
      }
      let r = await original(input,init); e.status=r.status;
      if(r.ok && kind==='task' && control.pendingTasks.has(decodeURIComponent(url.pathname.split('/').at(-1)))) {
        const body=await r.json();
        r=new Response(JSON.stringify({...body,status:'RUNNING',finishedAt:null,errorCode:null,errorMessage:null,document:null}),{status:200,headers:{'Content-Type':'application/json'}});
        e.mode='FAULT_INJECTION'; console.warn('FAULT_INJECTION：任务只在浏览器被保持RUNNING',e.n);
      }
      if(r.ok && kind==='search' && control.rewriteScores) {
        const body=await r.json();
        if(body.hits.length<4) throw new Error('受控渲染需真实返回至少4个合成ID；本次已搜索，不能自动补发');
        const scores=[0.123449,0.123441,0,-0.3];
        r=new Response(JSON.stringify({hits:body.hits.slice(0,4).map((h,i)=>({...h,score:scores[i]}))}),{status:200,headers:{'Content-Type':'application/json'}});
        e.mode='FAULT_INJECTION'; console.warn('FAULT_INJECTION：只测试显示顺序/负分，分数不代表后端结果',e.n);
      }
      if(control.holdNext.delete(kind)) {
        e.mode='FAULT_INJECTION'; console.warn('受控延迟，使用uiNet.release(编号)释放',e.n);
        await new Promise(resolve=>gates.set(e.n,resolve));
      }
      return r;
    } catch(error) { e.error=error.name; throw error; }
    finally { e.end=performance.now(); console.table([e]); }
  };
  console.warn('已安装透明观察器；只有显式设定模式才注入。任务/分数受控分支不证明后端行为。');
})();
```

观察器日志是**页面fetch调用**，不是全部实际服务器请求：`failNext`在发包前拦截时Network新增0、Console意图1；真实Offline在Network可见失败尝试。不能混用两种计数。holdNext延迟的是浏览器接收处理，不证明服务器仍在计算；超过页面10秒请求预算可能超时，应在约3—6秒内释放。最长预算测试用pendingTasks，不用长期hold。

### 4.3 分支步骤（每行状态仍NOT_RUN）

每个失效分支使用新提交且真实有效的合成文档；不得复用U16已经失效的A，否则无法测试首次失效。下面的“A”指该分支本次选定的有效文档。F02可从保存的真实任务ID选择4项；不把未查询到的ID编造成任务。

| 分支 | 控制与本人操作 | 预期/证据 | 当前状态 |
| --- | --- | --- | --- |
| F01 负分/显示同分 | `uiNet.rewriteScores=true`；专用渠道Top-K100，明确点一次检索；之后设false | 页面顺序与注入数组相同，前两项均显示0.1234但不按ID重排，0和-0.3000仍显示。截图标FAULT_INJECTION；不作为真实后端分数/排序证据 | NOT_RUN |
| F02 60秒/3并发 | 把4个真实taskId放入`uiNet.pendingTasks`，依次用“taskId”框→“查询并有限刷新” | 前3个可自动轮询，第四个仅手动；Network真实GET响应可能SUCCEEDED，但Console明确受控RUNNING。GET完成后间隔≥2秒，同任务不重叠；60秒后停止 | NOT_RUN |
| F03 隐藏/恢复 | F02中10秒后切到另一Chrome Tab，20秒后回来；再隐藏跨过60秒回来 | 隐藏不发新自动GET；返回在预算内可再取，跨预算不恢复；预算不因隐藏/手动刷新重置。录屏/时间线，浏览器节流造成延后不误算提前 | NOT_RUN |
| F04 网络不改FAILED | F02已有RUNNING时`uiNet.failNext.task='network'`，点“手动刷新一次” | 状态暂不可获取，上次RUNNING保留；后续有限GET退避允许，但不能变FAILED或永久刷新。该注入无真实网络包，另用Chrome Request blocking验证真实失败 | NOT_RUN |
| F05 真实FAILED展示 | 仅当专用服务真实返回FAILED时核对errorCode/message、终态停止；没有真实失败不人为破坏库 | 无SQL/堆栈、无假百分比，失败与文档有效状态独立。没有数据则此真实分支NOT_RUN，不把F04当FAILED验收 | NOT_RUN |
| F06 RESULT_UNKNOWN | `uiNet.failNext.search='unknown'`，明确点检索；观察15秒，不再点击 | 一次页面fetch调用、真实POST0（注入在发包前）；提示可能已计数/不自动重试/累计值不能确认，旧结果标历史。只验前端反馈，真实commit未知仍引用QA后端分层证据 | NOT_RUN |
| F07 搜索防重/编辑 | `uiNet.holdNext.add('search')`；点检索后双击/Enter；修改查询文本但不提交，3—6秒内释放对应编号 | 真实POST1；返回结果的条件仍是原快照，当前表单不同明确标历史，不触发第二笔；无旧结果冒充新条件 | NOT_RUN |
| F08 在途失效 | 先有结果；hold下一笔search并明确提交；从旧结果打开A详情，确认失效；释放迟到搜索 | PUT1、明确搜索1，没有附加自动搜索；迟到hits若含A仍标历史/已失效，不删除重排。此为浏览器迟到响应，不冒称后端SQL快照竞态 | NOT_RUN |
| F09 详情旧响应 | `uiNet.holdNext.add('detail')`；用“按文档 ID 查询详情”查A，关闭后查B，B完成再释放A | A晚响应不覆盖B详情，关闭不复活dialog。Network/Console记录A/B及取消；Abort仅停止等待，不代表数据库回滚 | NOT_RUN |
| F10 失效失败 | 有效文档详情中`uiNet.failNext.invalidate='network'`，点“失效文档”并确认 | 仅一次页面调用、真实PUT0；仍显示上次有效状态，提示手动刷新确认，不乐观成功。另用Offline做一次真实网络失败，不能宣称已回滚 | NOT_RUN |
| F11 提交未知 | 新唯一合成ID，`uiNet.failNext.submit='unknown'`，点击“提交文档” | 页面调用1、真实POST0；提示可能已接收、按本次ID查询，不自动换ID/重提。更改输入后“按提交 ID 查询详情”仍查原ID，404诚实展示 | NOT_RUN |

结束执行`uiNet.restore()`并刷新页面。真实任务再次读取应恢复服务器原状态；不要把受控RUNNING保存在记录中当真实任务历史。清除Sources断点；保存带模式标注的截图/HAR/Console。每项只有本人实际操作且证据足够后才能改结果。

## 5. 可复制证据模板

先复制到本次临时`evidence/`填写，不直接覆盖本文件历史或以前报告。缺少某个罕见分支就继续NOT_RUN并说明；真实后端与受控显示分别编号。

```json
{
  "runId": "待填",
  "operator": "待填",
  "startedAt": "待填ISO时间",
  "candidateSourceSha": "待填完整SHA",
  "jarSha256": "待填",
  "ciUrl": "待填",
  "url": "http://127.0.0.1:8083/",
  "testDataDir": "待填本次mktemp路径/db",
  "syntheticPrefix": "待填ui-前缀",
  "chromeVersion": "待填",
  "os": "待填",
  "viewport": "待填",
  "cases": [{
    "id": "U01",
    "status": "NOT_RUN",
    "evidenceMode": "REAL",
    "stepsActuallyPerformed": [],
    "expected": "待填",
    "actual": null,
    "network": {"method":"待填","path":"待填","attempts":null,"actualServerRequests":null,"statusCodes":[],"initiator":"待填","payloadFile":null,"responseFile":null},
    "documentIds": [],
    "taskIds": [],
    "hitCountBefore": null,
    "hitCountAfter": null,
    "screenshots": [],
    "harFile": null,
    "consoleFile": null,
    "faultControl": null,
    "failureOrNotRunReason": "尚未执行真人浏览器",
    "finishedAt": null
  }]
}
```

```csv
case_id,status,evidence_mode,started_at,finished_at,candidate_sha,jar_sha256,viewport,steps,expected,actual,network_attempts,actual_server_requests,method,path,status_codes,hit_count_before,hit_count_after,screenshot,har,console,not_run_or_failure_reason
U01,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U02,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U03,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U04,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U05,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U06,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U07,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U08,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U09,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U10,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U11,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U12,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U13,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U14,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U15,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U16,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U17,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
U18,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
F01,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F02,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F03,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F04,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F05,NOT_RUN,REAL,,,,,,,,,,,,,,,,,,,尚未执行
F06,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F07,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F08,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F09,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F10,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
F11,NOT_RUN,FAULT_INJECTION,,,,,,,,,,,,,,,,,,,尚未执行
```

status只在实际核证后填PASS/FAIL/NOT_RUN；evidenceMode不可省略，注入项PASS不等于对应后端分支真实发生。计数填服务器原始十进制字符串；无法确定提交写unknown/null，不能据累计值推断。截图应包含候选runId/当前控件/状态文字，HAR能对应具体步骤；注入截图显式注明FAULT_INJECTION。完成后记录正常停止PID/退出时间，保留目录待主控核对，专用库清理仍另行明确确认。

## 6. 本次文档交付检查

本次只编写脚本和模板，未执行启动命令、Console、Chrome或服务器操作；没有新增业务测试通过数。提交前仅做文档空白、内嵌脚本语法与模板结构静态检查；这些检查不改变任何U/F项的NOT_RUN。待固定候选与用户实际执行证据齐备，才由主控/QA判定最终UI门禁。
