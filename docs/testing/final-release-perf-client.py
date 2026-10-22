#!/usr/bin/env python3
"""仅向明确确认的回环隔离实例发负载；不重试写请求，不访问正式域名。"""
import argparse
import collections
import concurrent.futures
import datetime
import hashlib
import json
import math
import pathlib
import platform
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', required=True)
parser.add_argument('--mode', choices=['ingest', 'search'], required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--confirm-isolated', action='store_true', required=True)
args = parser.parse_args()
url = urllib.parse.urlparse(args.base_url)
if url.scheme != 'http' or url.hostname != '127.0.0.1' or url.port in [80, 443, 8080]:
    parser.error('只允许非业务端口的127.0.0.1隔离目标')
out = pathlib.Path(args.output).resolve()
out.mkdir(parents=True, exist_ok=False)
lock = threading.Lock()
prefix = 'perf-' + args.mode + '-' + datetime.datetime.now(datetime.timezone.utc).strftime('%H%M%S')
started = time.monotonic()


def save(name, value):
    (out / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def request(method, path, body=None, label='read'):
    raw = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(args.base_url + path, data=raw, method=method,
                                 headers={'Content-Type': 'application/json'} if raw else {})
    begin = time.monotonic_ns()
    wall = time.time_ns()
    result = {'method': method, 'path': path, 'label': label, 'startedNs': wall,
              'requestSha256': None if raw is None else hashlib.sha256(raw).hexdigest()}
    try:
        try:
            response = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            result['status'] = response.status
            result['body'] = json.load(response)
    except Exception as error:
        result.update(status=None, error=type(error).__name__)
    result['elapsedNs'] = time.monotonic_ns() - begin
    with lock:
        with (out / 'requests.jsonl').open('a') as stream:
            stream.write(json.dumps(result, ensure_ascii=False) + '\n')
    return result


def documents(channel=None):
    rows = []
    page = 0
    while True:
        params = {'page': page, 'size': 100}
        if channel is not None:
            params['channel'] = channel
        response = request('GET', '/api/documents?' + urllib.parse.urlencode(params), label='monitor')
        assert response['status'] == 200, response
        body = response['body']
        rows.extend(body['items'])
        if len(rows) >= body['totalElements']:
            return rows
        page += 1


def wait_done(ids, channel=None, seconds=1800):
    deadline = time.monotonic() + seconds
    peaks = 0
    while time.monotonic() < deadline:
        rows = {row['documentId']: row for row in documents(channel)}
        selected = [rows.get(ident) for ident in ids]
        pending = sum(row is None or row['taskStatus'] in ['QUEUED', 'RUNNING'] for row in selected)
        peaks = max(peaks, pending)
        with (out / 'pending-observed.jsonl').open('a') as stream:
            stream.write(json.dumps({'wallNs': time.time_ns(), 'durablePendingObserved': pending,
                                     'accepted': len(ids), 'notJavaQueueSize': True}) + '\n')
        if pending == 0:
            assert all(row['taskStatus'] == 'SUCCEEDED' and row['vectorReady'] for row in selected)
            return selected, peaks
        # 只等待真实持久状态；延时不作为任何事务或性能断言。
        time.sleep(1)
    raise AssertionError('已接收任务未在实验保护期限内收尾')


def batch(items, workers, label):
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(lambda body: request('POST', '/api/documents', body, label), items))


def latency(results):
    accepted = sorted(r['elapsedNs'] / 1e6 for r in results if r['status'] in [200, 202])
    def percentile(p):
        return None if not accepted else accepted[max(0, math.ceil(len(accepted) * p) - 1)]
    return {'attempted': len(results), 'statusCounts': dict(collections.Counter(str(r['status']) for r in results)),
            'successfulLatencySamples': len(accepted), 'p50MsNearestRank': percentile(.50),
            'p95MsNearestRank': percentile(.95), 'maxMs': max(accepted) if accepted else None,
            'rejectionsExcludedFromSuccessfulLatency': True}


health = request('GET', '/health', label='preflight')
assert health['status'] == 200 and health['body']['businessReady']
assert documents() == [], '仅允许全新空隔离库，拒绝污染既有数据'
save('environment.json', {'prefix': prefix, 'mode': args.mode, 'baseUrl': args.base_url,
                         'clientPlatform': platform.platform(), 'clientHost': platform.node(),
                         'targetIsSeparateLinuxHostViaSsh': True,
                         'candidateSource': 'd016d6e8bb431ff17ba81f8977d31a1b96f51f7f',
                         'jarSha256': '64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50'})

if args.mode == 'ingest':
    all_results = []
    stages = []
    serial = 0
    for label, count, length, workers in [('short-single', 10, 16, 1), ('medium-eight', 40, 1024, 8),
                                           ('long-overload', 1250, 20000, 64)]:
        text = ('合成负载字面文本AB123456' * 2000)[:length]
        items = [{'documentId': prefix + '-' + str(serial + n), 'text': text, 'channel': prefix}
                 for n in range(count)]
        serial += count
        start = time.monotonic()
        result = batch(items, workers, label)
        all_results.extend(result)
        stages.append({'label': label, 'textCodePoints': len(text), 'workers': workers,
                       'wallSeconds': time.monotonic() - start, **latency(result)})
        print('STAGE', label, latency(result), flush=True)
    assert all(r['status'] in [202, 429] for r in all_results), '存在未知提交或非预期错误，不能按成功数归账'
    accepted = [r['body'] for r in all_results if r['status'] == 202]
    rows, peak = wait_done([r['documentId'] for r in accepted], prefix)
    task_results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        task_results = list(pool.map(lambda item: request('GET', '/api/tasks/' + item['taskId'], label='terminal'), accepted))
    assert all(r['status'] == 200 and r['body']['status'] == 'SUCCEEDED' for r in task_results)
    def instant(value):
        return datetime.datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()
    timing = [{'taskId': r['body']['taskId'],
               'queuedMs': (instant(r['body']['startedAt']) - instant(r['body']['submittedAt'])) * 1000,
               'processingMs': (instant(r['body']['finishedAt']) - instant(r['body']['startedAt'])) * 1000}
              for r in task_results]
    save('task-timings.json', timing)
    save('summary.json', {'mode': 'PERF-01', 'stages': stages, 'accepted': len(accepted),
                          'rejected429': sum(r['status'] == 429 for r in all_results),
                          'succeeded': len(rows), 'unknown': 0, 'observedPendingPeakAfterSubmission': peak,
                          'exactJavaQueuePeak': None, 'writeRetries': 0,
                          'seconds': time.monotonic() - started})
else:
    seeds = []
    texts = {}
    for index in range(5000):
        group = 'n100' if index < 100 else 'n1000' if index < 1100 else 'bulk'
        ident = prefix + '-' + str(index).zfill(4)
        text = '合成文档编号' + str(index) + '：分润结算、设备离线与订单退款处理 ' + str(index * 17)
        seeds.append({'documentId': ident, 'text': text, 'channel': prefix + '-' + group})
        texts[ident] = text
    accepted = []
    def prepare(items):
        for start in range(0, len(items), 100):
            result = batch(items[start:start + 100], 8, 'corpus-setup')
            assert all(r['status'] == 202 for r in result), '准备拒绝或未知时不自动重发'
            accepted.extend(r['body'] for r in result)
            wait_done([r['body']['documentId'] for r in result])
    prepare(seeds[:1100])
    wait_done([r['documentId'] for r in accepted])
    samples = []
    def search(channel, k, label, text='合成文档结算退款'):
        body = {'text': text, 'topK': k}
        if channel is not None:
            body['channel'] = channel
        r = request('POST', '/api/search', body, label)
        if r['status'] == 200:
            assert len(r['body']['hits']) == k
        return r
    # 混合阶段固定n1000候选集，后台只写其他渠道；同N的纯搜索随后单独记录。
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        filling = pool.submit(prepare, seeds[1100:])
        for _ in range(30):
            result = search(prefix + '-n1000', 10, 'mixed-n1000-k10')
            assert result['status'] == 200
            samples.append(result)
        filling.result()
    wait_done([r['documentId'] for r in accepted])
    assert len(documents()) == 5000
    overflow = request('POST', '/api/documents', {'documentId': prefix + '-overflow', 'text': '', 'channel': prefix}, 'capacity')
    assert overflow['status'] == 429
    for n, channel in [(100, prefix + '-n100'), (1000, prefix + '-n1000'), (5000, None)]:
        for k in [1, 10, 100]:
            assert search(channel, k, 'warmup')['status'] == 200
            result = [search(channel, k, 'pure-n' + str(n) + '-k' + str(k)) for _ in range(12)]
            assert all(r['status'] == 200 for r in result)
            samples.extend(result)
    before = {row['documentId']: int(row['hitCount']) for row in documents()}
    hotspot = prefix + '-0000'
    attempts = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
        attempts = list(pool.map(lambda _: search(None, 1, 'hotspot', ''), range(128)))
    assert all(r['status'] in [200, 429] for r in attempts), '未知结果不能强行以HTTP成功归账'
    ledger = collections.Counter(hit['documentId'] for r in attempts if r['status'] == 200 for hit in r['body']['hits'])
    after = {row['documentId']: int(row['hitCount']) for row in documents()}
    assert all(after[i] - before[i] == ledger[i] for i in before)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        scattered = list(pool.map(lambda index: search(None, 1, 'scattered', texts[prefix + '-' + str(index).zfill(4)]), range(64)))
    assert all(r['status'] in [200, 429] for r in scattered)
    probe = search(None, 1, 'after-overload-recovery')
    assert probe['status'] == 200
    scatter_ledger = collections.Counter(hit['documentId'] for result in scattered + [probe]
                                          if result['status'] == 200 for hit in result['body']['hits'])
    final_counts = {row['documentId']: int(row['hitCount']) for row in documents()}
    assert all(final_counts[i] - after[i] == scatter_ledger[i] for i in after)
    grouped = collections.defaultdict(list)
    for result in samples:
        grouped[result['label']].append(result)
    save('summary.json', {'mode': 'PERF-02/03', 'corpus': 5000,
                          'groups': {label: latency(values) for label, values in grouped.items()},
                          'hotspot': latency(attempts), 'hotspotCommittedLedger': dict(ledger),
                          'scattered': latency(scattered), 'scatteredAndRecoveryCommittedLedger': dict(scatter_ledger), 'recovered200': True, 'writeRetries': 0,
                          'unknown': 0, 'capacityRejected429': True, 'seconds': time.monotonic() - started})
print('PERF_COMPLETE', out, flush=True)
