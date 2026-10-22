# FE2/FE3 真实临时 JAR HTTP 联调

日期：2026-10-23（北京时间）。前端分支`agent/frontend`，代码提交`8a1ea67b1dab9ba21028c7d5a19c96c52a5dd81b`；本次只新增证据文档，不修改代码。

## 服务与边界

- 主控提供限时本地JAR：`http://127.0.0.1:49490`，独立测试数据目录`/private/tmp/12times-b4-live-cl4yi4i4/db`。主控报告main为`e9ed996`、受测代码`820d1ff`；本角色未独立校验JAR哈希。
- 仅写入`fe-http-1792690934489-*`合成文档及同名专用渠道，没有使用其他文档或生产服务；未启动/停止应用，服务由主控的180秒预算自动退出。
- **浏览器 NOT_RUN**。以下是Node真实HTTP证据，不代表视觉、键盘、dialog焦点、真实DOM/XSS或浏览器请求竞争验收。没有绕过Work浏览器禁用设置。
- 首次默认沙箱中的fetch及curl无法连接；沙箱升级后的只读curl确认200/READY，随后直接在同一升级权限下运行完整HTTP脚本。首次失败原始记录保留，不能归因为JAR业务失败。

## 真实命令与断言

在`.worktrees/frontend`执行`node --input-type=module` heredoc脚本，使用已提交封装注入真实fetch：

```javascript
import { createApi } from './src/main/resources/static/api.mjs';
const origin = 'http://127.0.0.1:49490';
const api = createApi((path, options) => fetch(origin + path, options), { timeoutMs: 12000 });
```

脚本用`api.submit`分别提交普通/空文，循环`api.task`（单任务最长45秒，查询间隔500ms，仅本次HTTP验收脚本使用，不改页面2秒轮询默认），断言真实SUCCEEDED。随后明确调用：

```javascript
const before = await api.detail(normal);
const found = await api.search({ text: '文档向量相似检索', topK: 5, channel });
const after = await api.detail(normal), again = await api.detail(normal);
const page = await api.list({ channel, page: 0, size: 20 });
const afterList = await api.detail(normal);
const zero = await api.search({ text: '', topK: 5, channel });
const invalidated = await api.invalidate(normal);
const final = await api.search({ text: '文档向量相似检索', topK: 5, channel });
```

`normal/empty/channel`均由本次唯一前缀生成，完整值见原始JSON。真实HTTP脚本退出0，所有断言通过：

- 两次202接收、两个真实SUCCEEDED且vectorReady=true；空文原文仍为`""`。
- 首次搜索命中两条，普通文档真实score为`0.6367942239095025`，空文为0；没有注入响应或模拟分数。
- 普通文档hitCount为字符串`"0"→"1"`，两次详情读取与列表读取后仍`"1"`。断言用BigInt比较服务器字符串，没有修改页面计数。
- 单独明确发起的空查询返回两个零分结果，按ID顺序；这是第二笔搜索，正常再计一次。
- 失效成功后，第三笔明确搜索仅命中空文；普通文档仍为SUCCEEDED且invalidated=true，hitCount=`"2"`，未被失效后新查询命中。
- GET `/`、`/app.js`、`/search.mjs`均200；HTML为text/html，两个脚本均为text/javascript。

以下原始JSON包含服务器时间、真实任务ID、结果、计数与资源媒体类型；只含合成原文，无数据库文件、日志文件、凭据或密钥。本文档提交前执行`git diff --check`；文档提交没有重新运行未变更代码的测试。此前38个Node通过记录仍见frontend-b4.md，Java完整verify由主控负责。

## 首次沙箱失败原始记录

```json
{
  "checkedAt": "2026-10-22T17:41:29.192Z",
  "origin": "http://127.0.0.1:49490",
  "frontendCommit": "8a1ea67b1dab9ba21028c7d5a19c96c52a5dd81b",
  "browser": "NOT_RUN",
  "steps": [
    {
      "step": "错误",
      "value": {
        "name": "TypeError",
        "message": "fetch failed"
      }
    }
  ],
  "result": "FAIL"
}
```

## 沙箱升级后真实HTTP原始记录

```json
{
  "checkedAt": "2026-10-22T17:42:14.442Z",
  "origin": "http://127.0.0.1:49490",
  "frontendCommit": "8a1ea67b1dab9ba21028c7d5a19c96c52a5dd81b",
  "browser": "NOT_RUN",
  "steps": [
    {
      "step": "静态资源",
      "value": {
        "path": "/",
        "status": 200,
        "contentType": "text/html",
        "bytes": 12542
      }
    },
    {
      "step": "静态资源",
      "value": {
        "path": "/app.js",
        "status": 200,
        "contentType": "text/javascript",
        "bytes": 22132
      }
    },
    {
      "step": "静态资源",
      "value": {
        "path": "/search.mjs",
        "status": 200,
        "contentType": "text/javascript",
        "bytes": 2377
      }
    },
    {
      "step": "真实202接收",
      "value": {
        "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
        "documentId": "fe-http-1792690934489-normal",
        "status": "QUEUED"
      }
    },
    {
      "step": "真实任务终态",
      "value": {
        "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
        "documentId": "fe-http-1792690934489-normal",
        "status": "SUCCEEDED",
        "submittedAt": "2026-10-22T17:42:14.506202Z",
        "startedAt": "2026-10-22T17:42:14.827952Z",
        "finishedAt": "2026-10-22T17:42:14.836952Z",
        "errorCode": null,
        "errorMessage": null,
        "document": {
          "documentId": "fe-http-1792690934489-normal",
          "channel": "fe-http-1792690934489",
          "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
          "taskStatus": "SUCCEEDED",
          "vectorReady": true,
          "invalidated": false,
          "submittedAt": "2026-10-22T17:42:14.506202Z",
          "vectorCompletedAt": "2026-10-22T17:42:14.836952Z",
          "invalidatedAt": null,
          "hitCount": "0",
          "text": "前端真实HTTP联调：文档向量相似检索与计数。"
        }
      }
    },
    {
      "step": "真实202接收",
      "value": {
        "taskId": "8f1386d4-157a-4300-9b82-1db3fca96b54",
        "documentId": "fe-http-1792690934489-empty",
        "status": "QUEUED"
      }
    },
    {
      "step": "真实任务终态",
      "value": {
        "taskId": "8f1386d4-157a-4300-9b82-1db3fca96b54",
        "documentId": "fe-http-1792690934489-empty",
        "status": "SUCCEEDED",
        "submittedAt": "2026-10-22T17:42:15.058979Z",
        "startedAt": "2026-10-22T17:42:15.330395Z",
        "finishedAt": "2026-10-22T17:42:15.332290Z",
        "errorCode": null,
        "errorMessage": null,
        "document": {
          "documentId": "fe-http-1792690934489-empty",
          "channel": "fe-http-1792690934489",
          "taskId": "8f1386d4-157a-4300-9b82-1db3fca96b54",
          "taskStatus": "SUCCEEDED",
          "vectorReady": true,
          "invalidated": false,
          "submittedAt": "2026-10-22T17:42:15.058979Z",
          "vectorCompletedAt": "2026-10-22T17:42:15.332290Z",
          "invalidatedAt": null,
          "hitCount": "0",
          "text": ""
        }
      }
    },
    {
      "step": "检索前详情",
      "value": {
        "documentId": "fe-http-1792690934489-normal",
        "channel": "fe-http-1792690934489",
        "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
        "taskStatus": "SUCCEEDED",
        "vectorReady": true,
        "invalidated": false,
        "submittedAt": "2026-10-22T17:42:14.506202Z",
        "vectorCompletedAt": "2026-10-22T17:42:14.836952Z",
        "invalidatedAt": null,
        "hitCount": "0",
        "text": "前端真实HTTP联调：文档向量相似检索与计数。"
      }
    },
    {
      "step": "手动真实检索",
      "value": {
        "hits": [
          {
            "documentId": "fe-http-1792690934489-normal",
            "channel": "fe-http-1792690934489",
            "score": 0.6367942239095025,
            "snippet": "前端真实HTTP联调：文档向量相似检索与计数。"
          },
          {
            "documentId": "fe-http-1792690934489-empty",
            "channel": "fe-http-1792690934489",
            "score": 0,
            "snippet": ""
          }
        ]
      }
    },
    {
      "step": "检索后详情与GET不计数",
      "value": {
        "before": "0",
        "after": "1",
        "again": "1",
        "afterList": "1",
        "page": {
          "items": [
            {
              "documentId": "fe-http-1792690934489-empty",
              "channel": "fe-http-1792690934489",
              "taskId": "8f1386d4-157a-4300-9b82-1db3fca96b54",
              "taskStatus": "SUCCEEDED",
              "vectorReady": true,
              "invalidated": false,
              "submittedAt": "2026-10-22T17:42:15.058979Z",
              "vectorCompletedAt": "2026-10-22T17:42:15.332290Z",
              "invalidatedAt": null,
              "hitCount": "1"
            },
            {
              "documentId": "fe-http-1792690934489-normal",
              "channel": "fe-http-1792690934489",
              "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
              "taskStatus": "SUCCEEDED",
              "vectorReady": true,
              "invalidated": false,
              "submittedAt": "2026-10-22T17:42:14.506202Z",
              "vectorCompletedAt": "2026-10-22T17:42:14.836952Z",
              "invalidatedAt": null,
              "hitCount": "1"
            }
          ],
          "page": 0,
          "size": 20,
          "totalElements": 2
        }
      }
    },
    {
      "step": "空查询真实零分",
      "value": {
        "hits": [
          {
            "documentId": "fe-http-1792690934489-empty",
            "channel": "fe-http-1792690934489",
            "score": 0,
            "snippet": ""
          },
          {
            "documentId": "fe-http-1792690934489-normal",
            "channel": "fe-http-1792690934489",
            "score": 0,
            "snippet": "前端真实HTTP联调：文档向量相似检索与计数。"
          }
        ]
      }
    },
    {
      "step": "失效真实响应",
      "value": {
        "documentId": "fe-http-1792690934489-normal",
        "invalidated": true,
        "invalidatedAt": "2026-10-22T17:42:15.653884Z"
      }
    },
    {
      "step": "失效后明确新检索",
      "value": {
        "hits": [
          {
            "documentId": "fe-http-1792690934489-empty",
            "channel": "fe-http-1792690934489",
            "score": 0,
            "snippet": ""
          }
        ]
      }
    },
    {
      "step": "最终详情",
      "value": {
        "documentId": "fe-http-1792690934489-normal",
        "channel": "fe-http-1792690934489",
        "taskId": "ef3aa330-c641-4cc2-8ee1-b03c05c96bba",
        "taskStatus": "SUCCEEDED",
        "vectorReady": true,
        "invalidated": true,
        "submittedAt": "2026-10-22T17:42:14.506202Z",
        "vectorCompletedAt": "2026-10-22T17:42:14.836952Z",
        "invalidatedAt": "2026-10-22T17:42:15.653884Z",
        "hitCount": "2",
        "text": "前端真实HTTP联调：文档向量相似检索与计数。"
      }
    }
  ],
  "result": "PASS"
}
```
