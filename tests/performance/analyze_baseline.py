#!/usr/bin/env python3
"""从本地原始回执与监控计算口径明确的聚合和图表，不连接网络。"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import statistics
import sys
import tempfile

from verify_counters import console_events


def percentile(values, q):
    """使用排序样本的(n-1)线性插值；无样本返回null，不能用0代替。"""
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * q
    low = math.floor(position)
    high = math.ceil(position)
    return ordered[low] + (ordered[high] - ordered[low]) * (position - low)


def latency(values):
    return {'samples': len(values), 'averageMs': statistics.mean(values) if values else None,
            **{'p' + str(q) + 'Ms': percentile(values, q / 100) for q in [50, 90, 95, 99]},
            'maximumMs': max(values) if values else None}


def parse_instant(text):
    return datetime.fromisoformat(text.replace('Z', '+00:00')).timestamp()


def window_counts(rows, start, stop):
    """固定半开窗口统计完成吞吐；宽限期回执单列，不混入窗口内拒绝数。"""
    cohort = [r for r in rows if start <= r['startEpochMs'] < stop]
    done = [r for r in cohort if r['endEpochMs'] < stop]
    return cohort, done, {
        'rejected429CompletedWithinWindow': sum(r['status'] == 429 for r in done),
        'rejected429CohortIncludingGrace': sum(r['status'] == 429 for r in cohort),
        'successCompletedWithinWindow': sum(r['outcome'] == 'SUCCESS' for r in done),
        'successCohortIncludingGrace': sum(r['outcome'] == 'SUCCESS' for r in cohort),
    }


def validate_completed_run(controller, audit, events, requests):
    """只有四阶完整且真实核账通过才允许发布完整窗口图，拒绝异常收尾样本。"""
    if controller.get('status') != 'PASS' or audit.get('status') != 'PASS':
        raise ValueError('控制器或持久计数核账未通过，不能标为完整测量')
    if any(e['type'] in ('STOP', 'BUDGET_LIMIT') for e in events):
        raise ValueError('提前中止或预算耗尽，不能标为完整阶梯')
    if {r['phase'] for r in requests} != {'step1', 'step2', 'step4', 'step8'}:
        raise ValueError('四个阶段不完整')
    starts = []
    for phase in ['step1', 'step2', 'step4', 'step8']:
        rows = [r for r in requests if r['phase'] == phase]
        phase_starts = {r['scenarioStartEpochMs'] for r in rows}
        if len(phase_starts) != 1:
            raise ValueError('阶段测量起点不唯一')
        start = phase_starts.pop()
        starts.append(start)
        if any(not (start <= r['startEpochMs'] < start + 27000
                    and r['startEpochMs'] <= r['endEpochMs'] <= start + 30000)
               for r in rows):
            raise ValueError('请求越过27秒发起或3秒收尾边界')
        if any(r['outcome'] not in ('SUCCESS', 'REJECTED') or r['status'] not in (200, 202, 429)
               for r in rows):
            raise ValueError('存在异常或未知结果，不能确认完整成功测量')
    if starts != [starts[0] + i * 30000 for i in range(4)]:
        raise ValueError('阶段起点必须严格相隔30秒')


def self_test():
    """用窗口边界和尾部拒绝样本防止不同统计口径再次混淆，不访问网络。"""
    rows = [
        {'startEpochMs': 100, 'endEpochMs': 199, 'status': 200, 'outcome': 'SUCCESS'},
        {'startEpochMs': 150, 'endEpochMs': 200, 'status': 429, 'outcome': 'REJECTED'},
        {'startEpochMs': 199, 'endEpochMs': 205, 'status': 200, 'outcome': 'SUCCESS'},
        {'startEpochMs': 200, 'endEpochMs': 201, 'status': 200, 'outcome': 'SUCCESS'},
    ]
    cohort, done, counts = window_counts(rows, 100, 200)
    assert len(cohort) == 3 and len(done) == 1
    assert counts == {'rejected429CompletedWithinWindow': 0, 'rejected429CohortIncludingGrace': 1,
                      'successCompletedWithinWindow': 1, 'successCohortIncludingGrace': 2}
    assert percentile([], .99) is None and percentile([1, 5], .5) == 3
    assert math.isclose(percentile([1, 5], .95), 4.8)
    complete = [{'type': 'REQUEST', 'phase': phase, 'scenarioStartEpochMs': i * 30000,
                 'startEpochMs': i * 30000 + 1, 'endEpochMs': i * 30000 + 29000,
                 'status': 200, 'outcome': 'SUCCESS'}
                for i, phase in enumerate(['step1', 'step2', 'step4', 'step8'])]
    validate_completed_run({'status': 'PASS'}, {'status': 'PASS'}, complete, complete)
    late = [dict(r) for r in complete]
    late[0]['endEpochMs'] = 31000
    for controller, audit, events, requests in [
            ({'status': 'STOPPED'}, {'status': 'PASS'}, complete, complete),
            ({'status': 'PASS'}, {'status': 'FAIL'}, complete, complete),
            ({'status': 'PASS'}, {'status': 'PASS'}, late, late),
            ({'status': 'PASS'}, {'status': 'PASS'}, complete[:3], complete[:3]),
            ({'status': 'PASS'}, {'status': 'PASS'}, complete + [{'type': 'STOP'}], complete)]:
        try:
            validate_completed_run(controller, audit, events, requests)
        except ValueError:
            continue
        raise AssertionError('不完整或超时实验不应通过发布门禁')
    print('PASS：半开窗口、尾部拒绝、百分位及中止/核账失败/超时/缺阶拒绝')


def main():
    if sys.argv[1:] == ['--self-test']:
        self_test()
        return
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--console', required=True)
    parser.add_argument('--metrics', required=True)
    parser.add_argument('--monitor', required=True)
    parser.add_argument('--controller-result', required=True)
    parser.add_argument('--counter-audit', required=True)
    parser.add_argument('--client-resources', required=True)
    parser.add_argument('--font-file', help='支持中文的字体文件；缺少时尝试系统常见字体')
    parser.add_argument('--output-dir', required=True)
    args = parser.parse_args()
    output = Path(args.output_dir)
    output.mkdir(parents=True, exist_ok=False)
    events = console_events(Path(args.console))
    requests = [event for event in events if event['type'] == 'REQUEST']
    controller = json.loads(Path(args.controller_result).read_text())
    audit = json.loads(Path(args.counter_audit).read_text())
    validate_completed_run(controller, audit, events, requests)
    totals = Counter()
    for line in Path(args.metrics).read_text().splitlines():
        event = json.loads(line)
        if event['type'] == 'Point' and event['metric'] in ['http_reqs', 'quality_attempts', 'quality_business_success', 'quality_rejections', 'quality_exceptions']:
            totals[event['metric']] += event['data']['value']
    if totals['http_reqs'] != len(requests) or totals['quality_attempts'] != len(requests):
        raise ValueError('HTTP原始计数与已知回执不一致，不能确认完整提交结果')
    success = sum(r['outcome'] == 'SUCCESS' for r in requests)
    rejected = sum(r['outcome'] == 'REJECTED' for r in requests)
    if totals['quality_business_success'] != success or totals['quality_rejections'] != rejected:
        raise ValueError('成功或拒绝计数与回执不一致')
    phase_rows = []
    endpoint_rows = []
    budget_phases = {e['phase'] for e in events if e['type'] == 'BUDGET_LIMIT'}
    for vus in [1, 2, 4, 8]:
        phase = 'step' + str(vus)
        rows = [r for r in requests if r['phase'] == phase]
        if not rows:
            phase_rows.append({'phase': phase, 'vus': vus, 'status': 'NOT_RUN'})
            continue
        starts = {r['scenarioStartEpochMs'] for r in rows}
        if len(starts) != 1:
            raise ValueError('同一阶段出现多个测量起点')
        start = starts.pop()
        stop = start + 27000
        began_in_window, completed_in_window, counts = window_counts(rows, start, stop)
        row = {'phase': phase, 'vus': vus, 'status': 'MEASURED', 'measurementSeconds': 27,
               'startUtc': datetime.fromtimestamp(start / 1000, timezone.utc).isoformat(),
               'stopUtc': datetime.fromtimestamp(stop / 1000, timezone.utc).isoformat(),
               'requestsStarted': len(began_in_window), 'completedWithinWindow': len(completed_in_window),
               **counts,
               'rejected429': sum(r['status'] == 429 for r in began_in_window),
               'server5xx': sum(r['status'] >= 500 for r in began_in_window),
               'graceCompleted': len(began_in_window) - len(completed_in_window),
               'actualSentRps': len(began_in_window) / 27,
               'businessSuccessQps': sum(r['outcome'] == 'SUCCESS' for r in completed_in_window) / 27,
               'clientBudgetLimited': phase in budget_phases,
               'successfulLatency': latency([r['elapsedMs'] for r in began_in_window if r['outcome'] == 'SUCCESS']),
               'allResponseLatency': latency([r['elapsedMs'] for r in began_in_window])}
        row['rejectionRate'] = row['rejected429'] / len(began_in_window)
        row['server5xxRate'] = row['server5xx'] / len(began_in_window)
        phase_rows.append(row)
        for endpoint in ['detail', 'list', 'search', 'submit']:
            subset = [r for r in began_in_window if r['endpoint'] == endpoint]
            done = [r for r in subset if r['endEpochMs'] < stop]
            endpoint_rows.append({'phase': phase, 'vus': vus, 'endpoint': endpoint,
                                  'requests': len(subset), 'successes': sum(r['outcome'] == 'SUCCESS' for r in subset),
                                  'rejected429': sum(r['status'] == 429 for r in subset),
                                  'sentRps': len(subset) / 27,
                                  'successQps': sum(r['outcome'] == 'SUCCESS' for r in done) / 27,
                                  'successfulLatency': latency([r['elapsedMs'] for r in subset if r['outcome'] == 'SUCCESS']),
                                  'allResponseLatency': latency([r['elapsedMs'] for r in subset])})
    wrapped = [json.loads(line) for line in Path(args.monitor).read_text().splitlines()]
    samples = [x for x in wrapped if x['sample'].get('type') == 'SAMPLE']
    gc = [x['sample']['heapAndGc'] for x in samples if 'heapUsedBytes' in x['sample']['heapAndGc']]
    resource = {'samples': len(samples), 'firstUtc': samples[0]['sample']['timeUtc'],
                'lastUtc': samples[-1]['sample']['timeUtc'],
                'hostCpuPeakPercent': max(x['sample']['hostCpuPercent'] or 0 for x in samples),
                'processCpuPeakOneCorePercent': max(x['sample']['processCpuPercentOneCore'] or 0 for x in samples),
                'rssPeakBytes': max(x['sample']['rssBytes'] for x in samples),
                'heapUsedPeakBytes': max(x['heapUsedBytes'] for x in gc),
                'minimumAvailableMemoryBytes': min(x['sample']['memAvailableBytes'] for x in samples),
                'minimumDiskFreeBytes': min(x['sample']['diskFreeBytes'] for x in samples),
                'workerThreadsObserved': sorted({len(x['sample']['workers']) for x in samples}),
                'gcCounterDeltas': {key: gc[-1]['jstatCounters'][key] - gc[0]['jstatCounters'][key]
                                    for key in gc[0]['jstatCounters'] if gc[0]['jstatCounters'][key] is not None
                                    and gc[-1]['jstatCounters'][key] is not None},
                'exactGcPauseEvents': 'NOT_MEASURED', 'hikariGauge': 'NOT_MEASURED',
                'exactExecutorQueueGauge': 'NOT_MEASURED',
                'samplingScope': '整个只读观测窗，含少量客户端启动和收尾；峰值是采样峰，不是连续极值'}
    intervals = [(parse_instant(t['startedAt']), parse_instant(t['finishedAt'])) for t in audit['taskTimes']]
    worker_overlap = max(sum(a <= point < b for a, b in intervals) for point, _ in intervals)
    client_samples = [json.loads(line) for line in Path(args.client_resources).read_text().splitlines()]
    client_resource = {'samples': len(client_samples),
                       'cpuPercentPsSamplePeak': max(x['cpuPercentPsSample'] for x in client_samples),
                       'rssPeakBytes': max(x['rssBytes'] for x in client_samples),
                       'scope': '本机k6进程ps观测值；不是全机CPU或精密区间CPU，不包含Chrome等其他客户端进程'}
    report = {'status': 'MEASURED_WITH_LIMITATIONS', 'controllerPassed': controller['status'] == 'PASS',
              'allRequests': len(requests), 'businessSuccessResponses': success, 'rejected429': rejected,
              'server5xx': sum(r['status'] >= 500 for r in requests),
              'statusCounts': dict(Counter(str(r['status']) for r in requests)),
              'unknownTransportResults': sum(r['status'] == 0 for r in requests),
              'firstRequestEpochMs': min(r['startEpochMs'] for r in requests),
              'lastResponseEpochMs': max(r['endEpochMs'] for r in requests),
              'fullKnownRequestSpanSeconds': (max(r['endEpochMs'] for r in requests) - min(r['startEpochMs'] for r in requests)) / 1000,
              'submittedDocuments': sum(r['status'] == 202 for r in requests),
              'queryCodePoints': 12000, 'filteredCandidateCount': 9, 'topK': 1,
              'phases': phase_rows, 'endpoints': endpoint_rows, 'resources': resource,
              'clientResources': client_resource,
              'worker': {'configured': 1, 'observedThreadCounts': resource['workerThreadsObserved'],
                         'taskIntervalMaxOverlap': worker_overlap, 'completedDocuments': len(intervals),
                         'documentsPer120SecondLoadWindow': len(intervals) / 120,
                         'worker2': 'NOT_RUN', 'worker4': 'NOT_RUN',
                         'meaning': '只在各阶段首个迭代提交一篇，这是稀疏混合负载完成率，不是Worker最大吞吐'},
              'convention': 'QPS按27秒内完成的业务成功响应计；RPS按窗口内发起计。延迟和rejected429保留测量窗发起且宽限期完成的尾请求；窗口内429另列。429不算业务成功，202不算向量化完成。'}
    (output / 'aggregate.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))

    # 仅把聚合值制图，不把原始请求、认证、私有路径或生产数据绘入公开图表。
    os.environ.setdefault('MPLCONFIGDIR', str(Path(tempfile.gettempdir()) / 'quality-matplotlib-cache'))
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    font_options = [Path(args.font_file)] if args.font_file else [
        Path('/System/Library/Fonts/PingFang.ttc'), Path('/System/Library/Fonts/STHeiti Medium.ttc'),
        Path('/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc')]
    font = next((p for p in font_options if p.is_file()), None)
    if font is None:
        raise ValueError('需要通过--font-file指定支持中文的字体，不能交付缺字图表')
    font_manager.fontManager.addfont(str(font))
    plt.rcParams['font.family'] = font_manager.FontProperties(fname=str(font)).get_name()
    plt.rcParams['axes.unicode_minus'] = False
    caption = '生产 Worker=1 / SearchPermit=1；N=9，查询12000码点；每阶测量27秒；起点UTC ' + phase_rows[0]['startUtc']
    measured = [r for r in phase_rows if r['status'] == 'MEASURED']
    xs = [r['vus'] for r in measured]
    fig, ax = plt.subplots(figsize=(9, 4.8))
    ax.plot(xs, [r['businessSuccessQps'] for r in measured], 'o-', label='混合负载业务成功QPS')
    for endpoint, label in [('detail', '详情'), ('list', '列表'), ('search', '搜索')]:
        ax.plot(xs, [next(e['successQps'] for e in endpoint_rows if e['phase'] == r['phase'] and e['endpoint'] == endpoint) for r in measured], 'o--', label=label)
    ax.set(xlabel='HTTP客户端并发数（不是后台Worker数）', ylabel='成功响应/秒', title='受控混合负载的成功QPS')
    ax.set_xticks(xs); ax.legend(); ax.grid(alpha=.2); fig.text(.02,.01,caption,fontsize=7)
    fig.tight_layout(rect=[0,.045,1,1]); fig.savefig(output/'concurrency-qps.svg'); fig.savefig(output/'concurrency-qps.png',dpi=140); plt.close(fig)
    fig, ax = plt.subplots(figsize=(9,4.8))
    ax.plot(xs,[r['successfulLatency']['p95Ms'] for r in measured],'o-',label='成功响应p95')
    ax.plot(xs,[r['successfulLatency']['p99Ms'] for r in measured],'o-',label='成功响应p99')
    ax.set(xlabel='HTTP客户端并发数',ylabel='端到端客户端耗时/ms',title='成功响应延迟（包含宽限期完成的尾请求）')
    ax.set_xticks(xs);ax.legend();ax.grid(alpha=.2);fig.text(.02,.01,caption,fontsize=7)
    fig.tight_layout(rect=[0,.045,1,1]);fig.savefig(output/'concurrency-latency.svg');fig.savefig(output/'concurrency-latency.png',dpi=140);plt.close(fig)
    fig, ax = plt.subplots(figsize=(9,4.8))
    throughput = len(intervals)/120
    ax.bar([1],[throughput],width=.5,label='生产实测：四篇稀疏提交/120秒')
    ax.set_xticks([1,2,4]);ax.set(xlabel='后台Worker配置',ylabel='完成文档/秒',title='Worker=1实测；Worker=2/4未执行')
    ax.set_xlim(.5,4.5)
    ax.set_ylim(0,max(.05,throughput*1.7))
    for x in [2,4]:ax.text(x,.002,'NOT_RUN',ha='center')
    ax.legend();fig.text(.02,.01,caption+'；不是处理能力上限，不把未测画成零吞吐。',fontsize=7)
    fig.tight_layout(rect=[0,.045,1,1]);fig.savefig(output/'worker-throughput.svg');fig.savefig(output/'worker-throughput.png',dpi=140);plt.close(fig)
    fig,axes=plt.subplots(2,1,figsize=(10,6.4),sharex=True)
    start=samples[0]['receivedEpoch'];ts=[x['receivedEpoch']-start for x in samples]
    axes[0].plot(ts,[x['sample']['hostCpuPercent'] for x in samples],label='主机CPU（全核100%）')
    axes[0].set(ylabel='CPU/%',title='同一生产观测窗口的CPU与内存采样');axes[0].legend();axes[0].grid(alpha=.2)
    axes[1].plot(ts,[x['sample']['rssBytes']/1048576 for x in samples],label='进程RSS')
    axes[1].plot(ts,[x['sample']['heapAndGc'].get('heapUsedBytes',float('nan'))/1048576 for x in samples],label='JVM Heap已使用')
    axes[1].set(xlabel='客户端收到采样后的相对秒数（1秒采样，含SSH传输延迟）',ylabel='MiB');axes[1].legend();axes[1].grid(alpha=.2)
    fig.text(.02,.01,'生产；'+resource['firstUtc']+' — '+resource['lastUtc']+'；完整观测窗，不拼接其他实验。',fontsize=7)
    fig.tight_layout(rect=[0,.045,1,1]);fig.savefig(output/'resources.svg');fig.savefig(output/'resources.png',dpi=140);plt.close(fig)
    print(json.dumps({'requests':len(requests),'success':success,'rejected429':rejected,'workerMaxIntervalOverlap':worker_overlap}))


if __name__ == '__main__':
    main()
