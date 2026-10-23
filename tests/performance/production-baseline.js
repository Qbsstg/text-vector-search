import http from 'k6/http';
import { sleep } from 'k6';
import execution from 'k6/execution';
import encoding from 'k6/encoding';
import { Counter, Trend } from 'k6/metrics';

// 配置和凭据只在初始化时从受限本地文件读取，不输出认证头或响应原文。
if (__ENV.QUALITY_AUTHORIZED_LIVE !== 'YES') throw new Error('LIVE_AUTHORIZATION_REQUIRED');
const config = JSON.parse(open(__ENV.QUALITY_CONFIG_FILE));
const credentials = JSON.parse(open(__ENV.QUALITY_CREDENTIALS_FILE));
if (!/^https:\/\/[^/@?#]+$/.test(config.baseUrl)) throw new Error('HTTPS_ORIGIN_REQUIRED');
if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,90}$/.test(config.runPrefix)) throw new Error('PREFIX_INVALID');
if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,48}$/.test(config.channel)) throw new Error('CHANNEL_INVALID');
if (config.approvedJarSha256 !== '64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50') {
  throw new Error('APPROVED_JAR_ATTESTATION_REQUIRED');
}
const authorization = 'Basic ' + encoding.b64encode(credentials.username + ':' + credentials.password);
const profile = [1, 2, 4, 8];
const scenarios = {};
for (let i = 0; i < profile.length; i += 1) {
  scenarios['step' + profile[i]] = {
    executor: 'constant-vus', vus: profile[i], duration: '27s', gracefulStop: '3s',
    startTime: (i * 30) + 's', exec: 'mixed', tags: { phase: 'step' + profile[i] },
  };
}

export const options = {
  scenarios, maxRedirects: 0, insecureSkipTLSVerify: false,
  discardResponseBodies: false, noConnectionReuse: false,
  systemTags: ['status', 'method', 'name', 'scenario', 'error_code'],
  summaryTrendStats: ['avg', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const attempts = new Counter('quality_attempts');
const successes = new Counter('quality_business_success');
const rejections = new Counter('quality_rejections');
const exceptions = new Counter('quality_exceptions');
const elapsed = new Trend('quality_elapsed_ms', true);
const successfulElapsed = new Trend('quality_success_elapsed_ms', true);
const budgets = {};
const ingestChannel = config.channel + '-ingest';
const text = '并发质量测试abcdef'.repeat(1600).slice(0, 12000);
const aliases = {};
for (const name of ['empty', 'whitespace', 'unicode', 'canonical', 'normalized', 'ordinary', 'duplicate', 'snippet', 'boundary']) {
  aliases[config.runPrefix + '-' + name] = name;
}

function stop(reason) {
  console.log(JSON.stringify({ type: 'STOP', phase: execution.scenario.name, reason }));
  execution.test.abort(reason);
}

function request(method, path, endpoint, body) {
  const phase = execution.scenario.name;
  const tags = { phase, endpoint, name: endpoint };
  const headers = { Authorization: authorization, Accept: 'application/json' };
  if (method !== 'GET') {
    headers['Content-Type'] = 'application/json';
    headers.Origin = config.baseUrl;
  }
  const begin = Date.now();
  const response = http.request(method, config.baseUrl + path, body === null ? null : JSON.stringify(body),
    { headers, timeout: '3s', redirects: 0, tags });
  const ms = Date.now() - begin;
  attempts.add(1, tags);
  elapsed.add(ms, tags);
  let value = null;
  try { value = response.json(); } catch (_) { /* 非 JSON 响应单独归类，绝不输出正文。 */ }
  const record = { type: 'REQUEST', phase, endpoint, method, startEpochMs: begin,
    scenarioStartEpochMs: execution.scenario.startTime,
    endEpochMs: Date.now(), elapsedMs: ms, status: response.status,
    httpDurationMs: response.timings.duration, blockedMs: response.timings.blocked,
    connectingMs: response.timings.connecting, tlsMs: response.timings.tls_handshaking,
    outcome: 'EXCEPTION', code: value && typeof value.code === 'string' ? value.code : null };
  if (response.status === 0 || response.status >= 500 || record.code === 'RESULT_UNKNOWN') {
    exceptions.add(1, tags); console.log(JSON.stringify(record)); stop('UNKNOWN_OR_SERVER_FAILURE'); return null;
  }
  if (response.status === 429 && endpoint === 'search' && record.code === 'SEARCH_BUSY') {
    record.outcome = 'REJECTED'; rejections.add(1, tags);
  } else if ((response.status === 200 && endpoint !== 'submit') || (response.status === 202 && endpoint === 'submit')) {
    if (!value || typeof value !== 'object') { console.log(JSON.stringify(record)); stop('SUCCESS_NOT_JSON'); return null; }
    if (endpoint === 'search') {
      if (!Array.isArray(value.hits) || value.hits.length !== 1 || value.hits.length > body.topK) {
        console.log(JSON.stringify(record)); stop('HITS_MISSING_OR_TOPK'); return null;
      }
      const ids = new Set();
      record.hits = value.hits.map((hit) => {
        if (!aliases[hit.documentId] || ids.has(hit.documentId) || hit.channel !== config.channel
            || !Number.isFinite(hit.score) || hit.score < -1 || hit.score > 1) {
          stop('HIT_SCOPE_OR_SCORE_INVALID');
        }
        ids.add(hit.documentId);
        return { alias: aliases[hit.documentId], score: hit.score };
      });
    }
    if (endpoint === 'submit') {
      if (value.status !== 'QUEUED' || value.documentId !== body.documentId
          || !/^[a-f0-9-]{36}$/i.test(value.taskId || '')) {
        console.log(JSON.stringify(record)); stop('ACCEPTANCE_SHAPE'); return null;
      }
      record.taskId = value.taskId;
    }
    if (endpoint === 'detail' && (value.documentId !== config.runPrefix + '-ordinary'
        || value.channel !== config.channel || value.taskStatus !== 'SUCCEEDED')) {
      console.log(JSON.stringify(record)); stop('DETAIL_SHAPE'); return null;
    }
    if (endpoint === 'list' && (!Array.isArray(value.items) || !Number.isInteger(value.totalElements)
        || value.page !== 0 || value.size !== 20)) {
      console.log(JSON.stringify(record)); stop('LIST_SHAPE'); return null;
    }
    record.outcome = 'SUCCESS'; successes.add(1, tags); successfulElapsed.add(ms, tags);
  } else {
    exceptions.add(1, tags); console.log(JSON.stringify(record)); stop('UNEXPECTED_HTTP_STATUS'); return null;
  }
  console.log(JSON.stringify(record));
  return { response, value, record };
}

export function mixed() {
  const phase = execution.scenario.name;
  const stage = profile.indexOf(Number(phase.slice(4)));
  if (!budgets[phase]) budgets[phase] = { gets: 0, searches: 0, rejected: 0, slow: 0, reported: false };
  const budget = budgets[phase];
  // 每场景只有全局第零次迭代提交一篇；四阶段合计最多四篇，不自动重发。
  if (execution.scenario.iterationInTest === 0) {
    request('POST', '/api/documents', 'submit', {
      documentId: config.runPrefix + '-perf-' + (stage + 1), text, channel: ingestChannel,
    });
  }
  // 总预算为15个VU份额乘以160次搜索/320次GET；达到后停止发包，不改变混合比例。
  if (budget.searches >= 160 || budget.gets >= 320) {
    if (!budget.reported) {
      console.log(JSON.stringify({ type: 'BUDGET_LIMIT', phase, vu: execution.vu.idInTest }));
      budget.reported = true;
    }
    sleep(0.5); return;
  }
  const index = execution.vu.iterationInScenario % 3;
  let result;
  if (index === 0) {
    budget.gets += 1;
    result = request('GET', '/api/documents/' + encodeURIComponent(config.runPrefix + '-ordinary'), 'detail', null);
  } else if (index === 1) {
    budget.gets += 1;
    result = request('GET', '/api/documents?channel=' + encodeURIComponent(config.channel) + '&page=0&size=20', 'list', null);
  } else {
    budget.searches += 1;
    // 新提交走另一个渠道，搜索固定九篇有效候选，避免把候选增长误归因为并发影响。
    result = request('POST', '/api/search', 'search', { text, topK: 1, channel: config.channel });
    if (result && result.record.outcome === 'REJECTED') budget.rejected += 1;
    if (budget.searches >= 20 && budget.rejected / budget.searches > 0.95) stop('PROTECTIVE_REJECTIONS_OVER_95_PERCENT_LOCAL_SAMPLE');
  }
  if (result) {
    budget.slow = result.record.elapsedMs > 2000 ? budget.slow + 1 : 0;
    if (budget.slow >= 3) stop('THREE_CONSECUTIVE_LATENCIES_OVER_TWO_SECONDS');
  }
}

export function handleSummary(data) {
  return { [__ENV.QUALITY_SUMMARY_FILE]: JSON.stringify(data, null, 2) };
}
