#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在明确授权后执行有界HTTPS功能验收；默认不连接网络。

只使用标准库。凭据、原文及响应正文仅在内存中使用，落盘只保留
白名单字段和摘要。并发屏障只同步客户端，不声称证明服务端内部交错。
"""
import argparse
import base64
import concurrent.futures
import hashlib
import json
import math
import os
from pathlib import Path
import re
import ssl
import stat
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone


class SafetyStop(Exception):
    """仅带固定原因标签，禁止把网络异常原文或响应正文输出。"""


def require(condition, reason):
    if not condition:
        raise SafetyStop(reason)


def utc():
    return datetime.now(timezone.utc).isoformat()


def digest(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True).encode('utf-8')
    return hashlib.sha256(raw).hexdigest()


def known_gateway_413(case_id, status, content_type, server):
    """只识别明确的Nginx HTML 413；不把网关拒绝冒充应用错误码。"""
    mime = content_type.split(';', 1)[0].strip().lower()
    return (case_id == 'V-bytes' and status == 413 and mime == 'text/html'
            and re.fullmatch(r'nginx(?:/[0-9.]+)?', server.strip().lower()) is not None)


def validate_known_gateway_resume(result, budget, statuses, events, baseline):
    """唯一续跑入口：首次零成功写入、已知413拒绝；不接受未知结果。"""
    require(result.get('status') == 'STOPPED' and result.get('reason') == 'ERROR_NOT_JSON', 'RESUME_RESULT_NOT_KNOWN_GATEWAY')
    require(result.get('cases') == {'PASS': 9, 'FAIL': 1, 'NOT_RUN': 29}, 'RESUME_CASE_COUNTS')
    require(budget.get('stopped') is True and budget.get('successful_new_documents') == 0, 'RESUME_ACCEPTED_OR_NOT_STOPPED')
    require(budget.get('used') == {'total': 14, 'GET': 4, 'submit': 10, 'search': 0, 'PUT': 0}, 'RESUME_BUDGET_NOT_EXACT')
    require(len(events) == 14, 'RESUME_EVENT_COUNT')
    ordered = sorted(events, key=lambda event: event.get('request_ordinal', -1))
    require([event.get('request_ordinal') for event in ordered] == list(range(1, 15)), 'RESUME_ORDINALS')
    for event in ordered:
        status = event.get('http_status')
        require(type(status) is int and status in (200, 400, 413), 'RESUME_UNKNOWN_OR_SERVER_STATUS')
        require(event.get('actual_code') != 'RESULT_UNKNOWN', 'RESUME_UNKNOWN_CODE')
    last = ordered[-1]
    require(last.get('case_id') == 'V-bytes' and last.get('http_status') == 413
            and last.get('outcome') == 'ERROR_NOT_JSON' and last.get('actual_code') is None, 'RESUME_LAST_NOT_GATEWAY_413')
    expected_pass = {'V-submit-missing_text', 'V-submit-null_text', 'V-submit-bad_id',
                     'V-submit-blank_channel', 'V-submit-long_text', 'V-submit-expansion',
                     'V-submit-channel_length', 'V-json', 'V-media'}
    require(len(statuses) == 39 and {key for key, value in statuses.items() if value.get('status') == 'PASS'} == expected_pass,
            'RESUME_PASS_SET')
    require({key for key, value in statuses.items() if value.get('status') == 'FAIL'} == {'V-bytes'}
            and statuses['V-bytes'].get('reason') == 'ERROR_NOT_JSON', 'RESUME_FAIL_SET')
    require(all(value.get('status') == 'NOT_RUN' for key, value in statuses.items()
                if key not in expected_pass and key != 'V-bytes'), 'RESUME_OTHER_CASE_STATE')
    for event in ordered[:-1]:
        require(event.get('expected_match') is True and event.get('outcome') in ('SUCCESS', 'REJECTED'), 'RESUME_PRIOR_FAILURE')
    require(all(sum(event.get('case_id') == case_id for event in ordered) == 1 for case_id in expected_pass), 'RESUME_PRIOR_CASE_EVENTS')
    require(sum(event.get('method') == 'GET' for event in ordered) == 4
            and sum(event.get('method') == 'POST' for event in ordered) == 10, 'RESUME_METHOD_COUNTS')
    require(type(baseline.get('existing_documents')) is int and baseline['existing_documents'] == 9
            and isinstance(baseline.get('metadata_sha256'), str)
            and re.fullmatch(r'[0-9a-f]{64}', baseline['metadata_sha256']), 'RESUME_BASELINE')
    return expected_pass


def load_known_gateway_resume(directory):
    """只读原件，保留文件散列；新轮始终写独立输出目录。"""
    names = ('result.json', 'budget.json', 'case-status.json', 'events.jsonl', 'baseline.json', 'run.json')
    values, hashes = {}, {}
    root = Path(directory)
    require(root.is_dir() and not root.is_symlink(), 'RESUME_DIRECTORY_INVALID')
    for name in names:
        path = root / name
        require(path.is_file() and not path.is_symlink(), 'RESUME_FILE_INVALID')
        raw = path.read_bytes()
        hashes[name] = hashlib.sha256(raw).hexdigest()
        values[name] = [json.loads(line) for line in raw.decode('utf-8').splitlines()] if name.endswith('jsonl') else json.loads(raw)
    passed = validate_known_gateway_resume(values['result.json'], values['budget.json'], values['case-status.json'],
                                           values['events.jsonl'], values['baseline.json'])
    return {'values': values, 'sha256': hashes, 'passed': passed}


class Budget:
    """请求在发出前扣账；未知结果也消耗额度，不能因失败退还。"""
    LIMITS = {'total': 240, 'GET': 180, 'submit': 32, 'search': 16, 'PUT': 4}

    def __init__(self):
        self.lock = threading.Lock()
        self.used = dict.fromkeys(self.LIMITS, 0)
        self.next_get = 0.0
        self.stopped = False
        self.successes = 0

    def reserve(self, method, path):
        kind = 'search' if path == '/api/search' else 'submit' if method == 'POST' else method
        with self.lock:
            require(not self.stopped, 'STOP_ALREADY_SET')
            for key in ('total', kind):
                require(key in self.LIMITS and self.used[key] < self.LIMITS[key], 'BUDGET_EXHAUSTED')
            # 锁覆盖GET节奏预约及等待，所有线程共享一个发出时钟。
            if method == 'GET':
                delay = self.next_get - time.monotonic()
                if delay > 0:
                    time.sleep(delay)
                self.next_get = time.monotonic() + 0.25
            self.used['total'] += 1
            self.used[kind] += 1
            return self.used['total'], self.remaining_locked()

    def accepted(self):
        with self.lock:
            self.successes += 1
            require(self.successes <= 12, 'DOCUMENT_BUDGET_EXCEEDED')

    def stop(self):
        with self.lock:
            self.stopped = True

    def remaining_locked(self):
        return {key: limit - self.used[key] for key, limit in self.LIMITS.items()}

    def snapshot(self):
        with self.lock:
            return {'used': dict(self.used), 'remaining': self.remaining_locked(),
                    'successful_new_documents': self.successes, 'stopped': self.stopped}


class HitLedger:
    """只按已确认200的真实hits累计；未知提交不由累计值反推。"""
    def __init__(self, aliases):
        self.aliases = aliases
        self.counts = dict.fromkeys(aliases, 0)
        self.lock = threading.Lock()

    def add(self, hits, channel):
        require(isinstance(hits, list), 'HITS_TYPE')
        seen = set()
        for hit in hits:
            require(isinstance(hit, dict), 'HIT_TYPE')
            document_id = hit.get('documentId')
            require(document_id in self.aliases and document_id not in seen, 'HIT_OUTSIDE_RUN_OR_DUPLICATE')
            require(hit.get('channel') == channel, 'HIT_CHANNEL_MISMATCH')
            score = hit.get('score')
            require(type(score) in (int, float) and math.isfinite(score) and -1 <= score <= 1, 'SCORE_RANGE')
            seen.add(document_id)
        ordered = sorted(hits, key=lambda hit: (-hit['score'], hit['documentId']))
        require(hits == ordered, 'HIT_ORDER')
        with self.lock:
            for document_id in seen:
                self.counts[document_id] += 1

    def verify(self, initial, final):
        with self.lock:
            for document_id, count in self.counts.items():
                value = final[document_id]
                require(isinstance(value, str) and re.fullmatch(r'\d+', value), 'COUNT_NOT_DECIMAL_STRING')
                require(int(value) - int(initial[document_id]) == count, 'COUNT_DELTA_MISMATCH')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Runner:
    def __init__(self, args, manifest, authorization, resume=None):
        self.args = args
        self.manifest = manifest
        self.authorization = authorization
        self.budget = Budget()
        self.resume = resume
        if resume:
            self.budget.used = dict(resume['values']['budget.json']['used'])
        self.io_lock = threading.Lock()
        self.state_lock = threading.Lock()
        self.output = Path(args.output_dir)
        self.output.mkdir(mode=0o700, parents=False, exist_ok=False)
        os.chmod(self.output, 0o700)
        self.events = self.output / 'events.jsonl'
        self.status = {case['id']: {'status': 'NOT_RUN', 'reason': 'NOT_STARTED'} for case in manifest['cases']}
        if resume:
            for case_id in resume['passed']:
                self.status[case_id] = {'status': 'PASS', 'reason': 'PREVIOUS_ATTEMPT_REAL_PASS',
                                        'source_case_status_sha256': resume['sha256']['case-status.json'],
                                        'source_events_sha256': resume['sha256']['events.jsonl']}
            self.write_json('resume-source.json', {'sha256': resume['sha256'], 'inherited_requests': 14,
                                                    'inherited_real_pass_cases': sorted(resume['passed']),
                                                    'old_failure_preserved': True})
            require(resume['values']['run.json'].get('run_prefix_sha256') == digest(args.run_prefix)
                    and resume['values']['run.json'].get('channel_sha256') == digest(args.channel), 'RESUME_RUN_ID_MISMATCH')
        self.cases = {case['id']: self.expand(case) for case in manifest['cases']}
        self.fixtures = {}
        for source in manifest['fixtures']:
            fixture = self.expand(source)
            if 'text_generator' in fixture:
                fixture['text'] = self.generate(fixture['text_generator'])
            self.fixtures[fixture['alias']] = fixture
        self.aliases = {f['documentId']: alias for alias, f in self.fixtures.items()}
        self.ledger = HitLedger(self.aliases)
        self.tasks = {}
        self.task_observations = {}
        self.layer_coverage = {}
        self.initial_counts = {}
        self.baseline = {}
        self.final_metadata = {}
        self.write_json('run.json', {'started_utc': utc(), 'run_prefix_sha256': digest(args.run_prefix),
                                    'channel_sha256': digest(args.channel), 'manifest_sha256': digest(manifest),
                                    'status': 'RUNNING', 'production_preflight_attested': True})

    def expand(self, value):
        if isinstance(value, str):
            return value.replace('${RUN_PREFIX}', self.args.run_prefix).replace('${CHANNEL}', self.args.channel)
        if isinstance(value, list):
            return [self.expand(item) for item in value]
        if isinstance(value, dict):
            return {key: self.expand(item) for key, item in value.items()}
        return value

    @staticmethod
    def generate(spec):
        return spec['character'] * spec['repeat'] + spec.get('suffix', '')

    def write_json(self, name, data):
        path = self.output / name
        with path.open('w', encoding='utf-8') as stream:
            os.chmod(path, 0o600)
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write('\n')

    def event(self, value):
        # 调用方不能传入完整URL、正文、message或认证材料。
        allowed = {'run_id', 'case_id', 'request_ordinal', 'method', 'route_template', 'start_utc',
                   'duration_ms', 'http_status', 'actual_code', 'outcome', 'budget_remaining',
                   'response_body_sha256', 'request_body_sha256', 'safe_document_alias',
                   'task_state', 'task_times', 'hit_aliases', 'hit_scores', 'expected_match', 'layer', 'contentType'}
        require(set(value) <= allowed, 'EVENT_FIELD_NOT_ALLOWED')
        with self.io_lock:
            with self.events.open('a', encoding='utf-8') as stream:
                os.chmod(self.events, 0o600)
                stream.write(json.dumps(value, ensure_ascii=False) + '\n')

    def request(self, case_id, method, path, expected, body=None, raw=None, query=None,
                origin=None, auth=True, headers=None, code=None, timeout=10):
        ordinal, remaining = self.budget.reserve(method, path)
        started = utc()
        clock = time.monotonic()
        template = re.sub(r'/(?:' + re.escape(self.args.run_prefix) + r'[^/?]*|[0-9a-f-]{36})(?=/|$)', '/{id}', path)
        event = {'run_id': 'functional', 'case_id': case_id, 'request_ordinal': ordinal,
                 'method': method, 'route_template': template, 'start_utc': started,
                 'http_status': None, 'actual_code': None, 'outcome': 'UNKNOWN',
                 'budget_remaining': remaining}
        payload = raw if raw is not None else json.dumps(body, ensure_ascii=False).encode('utf-8') if body is not None else None
        if payload is not None:
            event['request_body_sha256'] = hashlib.sha256(payload).hexdigest()
        request_headers = {'Accept': 'application/json'}
        if method == 'POST':
            request_headers['Content-Type'] = 'application/json'
        if auth:
            request_headers['Authorization'] = self.authorization
        if origin:
            request_headers['Origin'] = self.args.base_url if origin == 'same_origin' else origin
        request_headers.update(headers or {})
        url = self.args.base_url + path
        if query:
            url += '?' + urllib.parse.urlencode(query)
        try:
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                                urllib.request.HTTPSHandler(context=ssl.create_default_context()))
            req = urllib.request.Request(url, data=payload, method=method, headers=request_headers)
            try:
                response = opener.open(req, timeout=min(10, timeout))
            except urllib.error.HTTPError as error:
                response = error
            with response:
                status = response.code
                response_raw = response.read(2_000_001)
                content_type = response.headers.get('Content-Type', '')
                server = response.headers.get('Server', '')
            event['http_status'] = status
            event['response_body_sha256'] = hashlib.sha256(response_raw).hexdigest()
            mime = content_type.split(';', 1)[0].strip().lower()
            event['contentType'] = mime if mime in ('application/json', 'text/html', 'text/plain') else 'OTHER'
            gateway = known_gateway_413(case_id, status, content_type, server)
            event['layer'] = 'GATEWAY_413' if gateway else 'APPLICATION_JSON' if mime == 'application/json' else 'UNCLASSIFIED'
            require(len(response_raw) <= 2_000_000, 'RESPONSE_TOO_LARGE')
            try:
                data = json.loads(response_raw)
            except (ValueError, UnicodeError):
                data = None
            if isinstance(data, dict) and isinstance(data.get('code'), str):
                # code限协议标识符，拒绝把任意message伪装成code落盘。
                require(re.fullmatch(r'[A-Z_]{1,64}', data['code']), 'ERROR_CODE_UNSAFE')
                event['actual_code'] = data['code']
            if status == 202 and method == 'POST' and path == '/api/documents':
                self.budget.accepted()
            require(status < 500 and event['actual_code'] != 'RESULT_UNKNOWN', 'SERVER_OR_UNKNOWN_RESULT')
            require(status not in range(300, 400), 'REDIRECT_REFUSED')
            wanted = expected if isinstance(expected, list) else [expected]
            require(status in wanted, 'HTTP_STATUS_MISMATCH')
            if status >= 400 and status != 401 and not gateway:
                require(isinstance(data, dict), 'ERROR_NOT_JSON')
                require(isinstance(data.get('code'), str) and isinstance(data.get('message'), str)
                        and isinstance(data.get('fieldErrors'), list), 'ERROR_SHAPE')
                for field in data['fieldErrors']:
                    require(isinstance(field, dict) and isinstance(field.get('field'), str)
                            and isinstance(field.get('message'), str), 'FIELD_ERROR_SHAPE')
                if code:
                    require(data['code'] == code, 'ERROR_CODE_MISMATCH')
                if status == 429:
                    require(path == '/api/search' and data['code'] == 'SEARCH_BUSY', 'UNEXPECTED_REJECTION')
            if status < 400:
                require(isinstance(data, dict), 'SUCCESS_NOT_OBJECT')
            if status == 200 and path == '/api/search':
                self.ledger.add(data.get('hits'), body['channel'])
                event['hit_aliases'] = [self.aliases[hit['documentId']] for hit in data['hits']]
                event['hit_scores'] = [hit['score'] for hit in data['hits']]
                require(len(data['hits']) <= body['topK'], 'TOPK_BOUND')
                for hit in data['hits']:
                    fixture = self.fixtures[self.aliases[hit['documentId']]]
                    require(hit.get('snippet') == fixture['text'][:200], 'SNIPPET_MISMATCH')
            if path.startswith('/api/tasks/') and status == 200:
                task_state = data.get('status')
                require(task_state in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'), 'TASK_STATUS')
                task_times = {key: data.get(key) for key in ('submittedAt', 'startedAt', 'finishedAt')}
                for value in task_times.values():
                    require(value is None or isinstance(value, str) and re.fullmatch(r'[0-9T:Z.+-]{10,40}', value), 'TASK_TIME_SHAPE')
                    if value is not None:
                        require(datetime.fromisoformat(value.replace('Z', '+00:00')).tzinfo is not None, 'TASK_TIME_ZONE')
                with self.state_lock:
                    alias = next((a for a, task_id in self.tasks.items() if path.endswith('/' + task_id)), None)
                    if alias:
                        event['safe_document_alias'] = alias
                        self.task_observations[alias] = dict(task_times, status=task_state)
                event['task_state'] = task_state
                event['task_times'] = task_times
            if gateway:
                require(data is None and event['actual_code'] is None, 'GATEWAY_413_CODE_MUST_BE_NULL')
                with self.state_lock:
                    self.layer_coverage[case_id] = {'layer': 'GATEWAY_413', 'gateway_rejection': 'PASS',
                                                     'application_json_branch': 'NOT_RUN'}
            event['expected_match'] = True
            event['outcome'] = 'REJECTED' if status >= 400 else 'SUCCESS'
            return status, data
        except Exception as error:
            self.budget.stop()
            event['expected_match'] = False
            event['outcome'] = error.args[0] if isinstance(error, SafetyStop) else 'TRANSPORT_OR_INTERNAL_FAILURE'
            raise SafetyStop(event['outcome']) from None
        finally:
            event['duration_ms'] = round((time.monotonic() - clock) * 1000, 3)
            self.event(event)

    def mark(self, case_id, action):
        try:
            result = action()
            with self.state_lock:
                self.status[case_id] = {'status': 'PASS', 'reason': 'ACTUAL_ASSERTIONS_COMPLETED'}
                if case_id in self.layer_coverage:
                    self.status[case_id].update(self.layer_coverage[case_id])
            return result
        except Exception as error:
            self.budget.stop()
            with self.state_lock:
                self.status[case_id] = {'status': 'FAIL', 'reason': error.args[0] if isinstance(error, SafetyStop) else 'INTERNAL_FAILURE'}
            raise

    def concurrent(self, actions):
        barrier = threading.Barrier(len(actions), timeout=10)
        def invoke(action):
            barrier.wait()
            return action()
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(actions)) as pool:
            futures = [pool.submit(invoke, action) for action in actions]
            results, failures = [], []
            for future in futures:
                try:
                    results.append(future.result())
                except Exception as error:
                    failures.append(error)
            if failures:
                raise failures[0]
            return results

    def list_all(self, case_id, channel=None, size=100):
        result = []
        page = 0
        total = None
        while True:
            query = {'page': page, 'size': size}
            if channel is not None:
                query['channel'] = channel
            _, data = self.request(case_id, 'GET', '/api/documents', 200, query=query)
            require(isinstance(data.get('items'), list) and data.get('page') == page and data.get('size') == size,
                    'PAGE_SHAPE')
            require(type(data.get('totalElements')) is int, 'PAGE_TOTAL_TYPE')
            if total is None:
                total = data['totalElements']
            require(total == data['totalElements'], 'PAGE_TOTAL_CHANGED')
            result.extend(data['items'])
            if len(result) >= total:
                break
            require(data['items'], 'PAGE_NO_PROGRESS')
            page += 1
        require(len(result) == total and len({x['documentId'] for x in result}) == total, 'PAGE_DUPLICATE_OR_LOST')
        return result

    def preflight(self):
        rows = self.list_all('PREFLIGHT')
        self.baseline = {row['documentId']: row for row in rows}
        require(not any(key.startswith(self.args.run_prefix) for key in self.baseline), 'PREFIX_EXISTS')
        require(len(rows) + 12 <= 5000, 'CAPACITY_INSUFFICIENT')
        for suffix in ('', '-neighbor', '-absent'):
            require(not self.list_all('PREFLIGHT', self.args.channel + suffix), 'CHANNEL_EXISTS')
        # 预留全部既有行首尾分页、12篇三次详情、任务轮询和验证；不足时先拒绝写入。
        pages = (len(rows) + 99) // 100 or 1
        # 首轮最坏120轮询；续跑只继承POST校验PASS，没有节省必要GET。
        require(self.budget.snapshot()['remaining']['GET'] >= 171 + pages, 'GET_BUDGET_PREFLIGHT_INSUFFICIENT')
        if self.resume:
            require(digest(rows) == self.resume['values']['baseline.json']['metadata_sha256'], 'RESUME_BASELINE_CHANGED')
        self.write_json('baseline.json', {'existing_documents': len(rows), 'metadata_sha256': digest(rows),
                                         'test_aliases': sorted(self.fixtures)})

    def generic(self, case_id):
        c = self.cases[case_id]
        body = c.get('body')
        if 'text_generator' in c:
            body = dict(body)
            body['text'] = self.generate(c['text_generator'])
        path = c['path'].replace('${RUN_PREFIX}', self.args.run_prefix)
        raw = c.get('raw_body')
        if raw is not None:
            raw = raw.encode('utf-8')
        if c.get('operation') == 'generated_oversize_body':
            raw = b' ' * c['body_bytes']
        return self.request(case_id, c['method'], path, c['expect_http'], body=body, raw=raw,
                            query=c.get('query'), origin=c.get('origin'), auth=c.get('auth') != 'none',
                            headers=c.get('headers'), code=c.get('expect_code'))

    def submit(self, alias, case_id, expected=202, origin=None):
        f = self.fixtures[alias]
        body = {key: f[key] for key in ('documentId', 'text')}
        if f['channel_mode'] != 'omit':
            body['channel'] = f['channel']
        status, data = self.request(case_id, 'POST', '/api/documents', expected, body=body, origin=origin)
        if status == 202:
            require(data.get('documentId') == f['documentId'] and data.get('status') == 'QUEUED', 'ACCEPTANCE_SHAPE')
            task_id = data.get('taskId')
            require(isinstance(task_id, str) and re.fullmatch(r'[0-9a-fA-F-]{36}', task_id), 'TASK_ID_SHAPE')
            with self.state_lock:
                require(alias not in self.tasks and task_id not in self.tasks.values(), 'DUPLICATE_TASK')
                self.tasks[alias] = task_id
        else:
            require(data['code'] == 'DOCUMENT_EXISTS', 'DUPLICATE_CODE')
        return status

    def details(self, case_id):
        results = {}
        for alias, f in self.fixtures.items():
            _, d = self.request(case_id, 'GET', '/api/documents/' + urllib.parse.quote(f['documentId'], safe=''), 200)
            require(d.get('documentId') == f['documentId'] and d.get('taskId') == self.tasks[alias], 'DETAIL_ASSOCIATION')
            require(d.get('text') == f['text'], 'ORIGINAL_TEXT_CHANGED')
            require(d.get('channel') == (f['channel'] if f['channel_mode'] == 'explicit' else 'default'), 'DETAIL_CHANNEL')
            require(d.get('taskStatus') == 'SUCCEEDED' and d.get('vectorReady') is True, 'DETAIL_NOT_READY')
            require(isinstance(d.get('hitCount'), str) and re.fullmatch(r'\d+', d['hitCount']), 'COUNT_SHAPE')
            results[f['documentId']] = d
        return results

    def poll(self):
        poll_total = 0
        for alias, task_id in self.tasks.items():
            start = time.monotonic()
            for attempt in range(10):
                require(poll_total < 120 and time.monotonic() - start < 30, 'POLL_BUDGET')
                _, task = self.request('F02', 'GET', '/api/tasks/' + task_id, 200,
                                       timeout=max(0.1, 30 - (time.monotonic() - start)))
                poll_total += 1
                require(task.get('taskId') == task_id and task.get('documentId') == self.fixtures[alias]['documentId'], 'TASK_ASSOCIATION')
                require(task.get('status') in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'), 'TASK_STATUS')
                require(task['status'] != 'FAILED', 'TASK_FAILED')
                if task['status'] == 'SUCCEEDED':
                    require(isinstance(task.get('document'), dict), 'TASK_DOCUMENT_MISSING')
                    require(all(task.get(k) is not None for k in ('submittedAt', 'startedAt', 'finishedAt')), 'TASK_TIMES_MISSING')
                    break
                require(task.get('document') is None, 'EARLY_TASK_DOCUMENT')
                if attempt < 9:
                    time.sleep(2)
            else:
                raise SafetyStop('POLL_NOT_TERMINAL')

    def search(self, case_id, expected=None):
        c = self.cases[case_id]
        return self.request(case_id, 'POST', '/api/search', expected or c['expect_http'], body=c['body'])

    def invalidate(self, case_id):
        path = '/api/documents/' + self.fixtures['race']['documentId'] + '/invalidation'
        _, data = self.request(case_id, 'PUT', path, 200)
        require(data.get('documentId') == self.fixtures['race']['documentId'] and data.get('invalidated') is True,
                'INVALIDATION_SHAPE')
        require(isinstance(data.get('invalidatedAt'), str), 'INVALIDATION_TIME')
        return data

    def execute(self):
        self.preflight()
        # 拒绝性提交在12篇成功前执行，意外202立即停写，避免第13篇永久占位。
        for case_id in self.cases:
            if self.resume and case_id in self.resume['passed']:
                continue
            if case_id.startswith('V-') or case_id in ('A01', 'A02', 'R03', 'R04', 'R05', 'R06'):
                if case_id == 'R03':
                    def invalid_pages():
                        c = self.cases['R03']
                        for query in c['query_variants']:
                            self.request('R03', 'GET', '/api/documents', 400, query=query, code=c['expect_code'])
                    self.mark(case_id, invalid_pages)
                else:
                    self.mark(case_id, lambda cid=case_id: self.generic(cid))
        def fixtures():
            for alias in self.fixtures:
                if alias == 'duplicate':
                    def duplicate():
                        statuses = self.concurrent([lambda: self.submit(alias, 'F04', [202, 409]),
                                                    lambda: self.submit(alias, 'F04', [202, 409])])
                        require(sorted(statuses) == [202, 409], 'DUPLICATE_RACE_WINNERS')
                    self.mark('F04', duplicate)
                elif alias == 'ordinary':
                    self.mark('A03', lambda: self.submit(alias, 'A03', origin='same_origin'))
                else:
                    self.submit(alias, 'F01')
        self.mark('F01', fixtures)
        self.mark('F02', self.poll)
        before = self.details('R02')
        self.initial_counts = {key: d['hitCount'] for key, d in before.items()}
        require(all(int(value) == 0 for value in self.initial_counts.values()), 'INITIAL_COUNT_NONZERO')
        self.mark('F03', lambda: self.submit('ordinary', 'F03', 409))
        def pages():
            rows = self.list_all('R01', self.args.channel, size=3)
            expected_ids = {f['documentId'] for f in self.fixtures.values() if f['channel'] == self.args.channel}
            require({row['documentId'] for row in rows} == expected_ids, 'CHANNEL_PAGE_MEMBERS')
            # ISO时间按真实时间解析，避免小数秒位数不同造成字符串排序错误。
            def timestamp(value):
                return datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()
            require(rows == sorted(rows, key=lambda row: (-timestamp(row['submittedAt']), row['documentId'])), 'PAGE_ORDER')
            _, data = self.request('R01', 'GET', '/api/documents', 200,
                                   query={'channel': self.args.channel, 'page': 100000, 'size': 3})
            require(data['items'] == [], 'EMPTY_PAGE')
        self.mark('R01', pages)
        after_get = self.details('R02')
        require(before == after_get, 'GET_OR_DUPLICATE_CHANGED_DOCUMENT')
        def zero_search():
            _, data = self.search('S01')
            require(len(data['hits']) == 10 and all(hit['score'] == 0 for hit in data['hits']), 'ZERO_SEARCH_MEMBERS')
        self.mark('S01', zero_search)
        def normalized():
            _, data = self.search('S02')
            require({self.aliases[h['documentId']] for h in data['hits'][:2]} == {'canonical', 'normalized'}, 'NORMALIZED_HEAD')
            require(all(abs(h['score'] - 1) <= 1e-6 for h in data['hits'][:2]), 'NORMALIZED_SCORE')
        self.mark('S02', normalized)
        self.mark('S03', lambda: require(self.search('S03')[1]['hits'] == [], 'EMPTY_CHANNEL_HITS'))
        def concurrent_search():
            results = self.concurrent([lambda: self.search('S04'), lambda: self.search('S04')])
            require(any(status == 200 for status, _ in results), 'NO_SUCCESSFUL_CONCURRENT_SEARCH')
        self.mark('S04', concurrent_search)
        invalidation = {}
        def race():
            c = self.cases['C01']
            def put():
                invalidation.update(self.invalidate('C01'))
            self.concurrent([put, lambda: self.request('C01', 'POST', '/api/search', [200, 429], body=c['search_body'])])
        self.mark('C01', race)
        def idempotent():
            again = self.invalidate('C02')
            require(again == invalidation, 'INVALIDATION_NOT_IDEMPOTENT')
        self.mark('C02', idempotent)
        def excluded():
            _, data = self.search('S05')
            expected = {f['documentId'] for f in self.fixtures.values() if f['channel'] == self.args.channel and f['alias'] != 'race'}
            require({hit['documentId'] for hit in data['hits']} == expected, 'POST_INVALIDATION_MEMBERS')
        self.mark('S05', excluded)
        def final_details():
            final = self.details('R02')
            self.ledger.verify(self.initial_counts, {key: d['hitCount'] for key, d in final.items()})
            for document_id, d in final.items():
                original = dict(before[document_id])
                updated = dict(d)
                for key in ('hitCount', 'invalidated', 'invalidatedAt'):
                    original.pop(key, None)
                    updated.pop(key, None)
                require(original == updated, 'UNRELATED_METADATA_CHANGED')
                require(d['invalidated'] == (self.aliases[document_id] == 'race'), 'INVALIDATED_FLAG')
            self.final_metadata = final
        self.mark('R02', final_details)
        all_final = self.list_all('FINAL_CHECK')
        current = {d['documentId']: d for d in all_final}
        require(set(current) == set(self.baseline) | set(self.aliases), 'UNEXPECTED_DOCUMENT_SET')
        require(all(current[key] == value for key, value in self.baseline.items()), 'HISTORICAL_METADATA_CHANGED')
        for document_id in self.aliases:
            detail = self.final_metadata[document_id]
            require(current[document_id] == {key: detail[key] for key in current[document_id]}, 'FINAL_GET_CHANGED_NEW_METADATA')
        self.write_json('consistency.json', {'existing_documents': len(self.baseline), 'historical_metadata_unchanged': True,
                                           'new_documents': 12, 'count_deltas': {self.aliases[key]: value for key, value in self.ledger.counts.items()},
                                           'final_metadata_sha256': digest(all_final),
                                           'race_evidence': 'CLIENT_BARRIER_ONLY_NOT_INTERNAL_INTERLEAVING'})

    def finish(self, success, reason):
        self.write_json('case-status.json', self.status)
        self.write_json('task-timing.json', {'observations': self.task_observations,
                                             'derived': task_metrics(self.task_observations) if success else None,
                                             'meaning': '服务器任务时间白名单；不是Worker线程活动或内部并行证明'})
        self.write_json('budget.json', self.budget.snapshot())
        self.write_json('result.json', {'finished_utc': utc(), 'status': 'PASS' if success else 'STOPPED',
                                        'reason': reason, 'cases': {state: sum(v['status'] == state for v in self.status.values())
                                                                   for state in ('PASS', 'FAIL', 'NOT_RUN')}})


def task_metrics(observations):
    """按服务器时间计算等待和处理区间；不从HTTP并发推断Worker并行。"""
    intervals = {}
    metrics = {}
    for alias, values in observations.items():
        if values['status'] != 'SUCCEEDED':
            continue
        parsed = {key: datetime.fromisoformat(values[key].replace('Z', '+00:00')).timestamp()
                  for key in ('submittedAt', 'startedAt', 'finishedAt')}
        require(parsed['submittedAt'] <= parsed['startedAt'] <= parsed['finishedAt'], 'TASK_TIME_ORDER')
        intervals[alias] = (parsed['startedAt'], parsed['finishedAt'])
        metrics[alias] = {'queue_wait_ms': round((parsed['startedAt'] - parsed['submittedAt']) * 1000, 3),
                          'processing_interval_ms': round((parsed['finishedAt'] - parsed['startedAt']) * 1000, 3)}
    overlaps = []
    names = sorted(intervals)
    for i, left in enumerate(names):
        for right in names[i + 1:]:
            overlap = min(intervals[left][1], intervals[right][1]) - max(intervals[left][0], intervals[right][0])
            if overlap > 0:
                overlaps.append({'left': left, 'right': right, 'overlap_ms': round(overlap * 1000, 3)})
    return {'per_alias': metrics, 'timestamp_interval_overlaps': overlaps}


def self_test():
    """不构造网络客户端；校验边界扣账、停止屏障、排序与计数账本。"""
    budget = Budget()
    for _ in range(16):
        budget.reserve('POST', '/api/search')
    try:
        budget.reserve('POST', '/api/search')
        raise AssertionError('预算未生效')
    except SafetyStop:
        pass
    assert budget.snapshot()['used']['search'] == 16
    # 并发扣账也不能突破上限，失败不退还额度。
    raced = Budget()
    def attempt(_):
        try:
            raced.reserve('POST', '/api/search')
            return 1
        except SafetyStop:
            return 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        assert sum(pool.map(attempt, range(64))) == 16
    for method, path, limit in [('POST', '/api/documents', 32), ('PUT', '/api/documents/x/invalidation', 4)]:
        scoped = Budget()
        for _ in range(limit):
            scoped.reserve(method, path)
        try:
            scoped.reserve(method, path)
            raise AssertionError('分类预算未生效')
        except SafetyStop:
            pass
    accepted = Budget()
    for _ in range(12):
        accepted.accepted()
    try:
        accepted.accepted()
        raise AssertionError('文档额度未生效')
    except SafetyStop:
        pass
    # 虚拟单调时钟验证4RPS调度，无网络、无真实等待。
    from unittest.mock import patch
    fake_clock = [0.0]
    def advance(delay):
        fake_clock[0] += delay
    with patch.object(time, 'monotonic', lambda: fake_clock[0]), patch.object(time, 'sleep', advance):
        reads = Budget()
        for _ in range(180):
            reads.reserve('GET', '/api/documents')
        assert fake_clock[0] == 44.75
        try:
            reads.reserve('GET', '/api/documents')
            raise AssertionError('GET预算未生效')
        except SafetyStop:
            pass
    budget.stop()
    try:
        budget.reserve('POST', '/api/documents')
        raise AssertionError('停止屏障未生效')
    except SafetyStop:
        pass
    aliases = {'run-a': 'a', 'run-b': 'b'}
    ledger = HitLedger(aliases)
    hits = [{'documentId': 'run-a', 'score': 0, 'channel': 'test'},
            {'documentId': 'run-b', 'score': -0.5, 'channel': 'test'}]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda _: ledger.add(hits, 'test'), range(40)))
    ledger.verify({'run-a': '0', 'run-b': '0'}, {'run-a': '40', 'run-b': '40'})
    for invalid in (list(reversed(hits)), [hits[0], hits[0]], [{'documentId': 'old', 'score': 0, 'channel': 'test'}]):
        try:
            ledger.add(invalid, 'test')
            raise AssertionError('账本非法输入未拒绝')
        except SafetyStop:
            pass
    timing = task_metrics({'a': {'status': 'SUCCEEDED', 'submittedAt': '2026-10-23T00:00:00Z',
                                   'startedAt': '2026-10-23T00:00:01Z', 'finishedAt': '2026-10-23T00:00:03Z'},
                           'b': {'status': 'SUCCEEDED', 'submittedAt': '2026-10-23T00:00:00Z',
                                   'startedAt': '2026-10-23T00:00:02Z', 'finishedAt': '2026-10-23T00:00:04Z'}})
    assert timing['per_alias']['a'] == {'queue_wait_ms': 1000.0, 'processing_interval_ms': 2000.0}
    assert timing['timestamp_interval_overlaps'][0]['overlap_ms'] == 1000.0
    global_limit = Budget()
    global_limit.used['total'] = 240
    try:
        global_limit.reserve('POST', '/api/search')
        raise AssertionError('全局预算未生效')
    except SafetyStop:
        pass
    assert known_gateway_413('V-bytes', 413, 'text/html; charset=utf-8', 'nginx/1.24.0')
    assert known_gateway_413('V-bytes', 413, 'text/html', 'nginx')
    for values in [('other', 413, 'text/html', 'nginx'), ('V-bytes', 400, 'text/html', 'nginx'),
                   ('V-bytes', 413, 'application/json', 'nginx'), ('V-bytes', 413, 'text/html', 'apache'),
                   ('V-bytes', 413, 'text/html', ''), ('V-bytes', 413, 'text/html-other', 'nginx')]:
        assert not known_gateway_413(*values)
    passed = ['V-submit-missing_text', 'V-submit-null_text', 'V-submit-bad_id', 'V-submit-blank_channel',
              'V-submit-long_text', 'V-submit-expansion', 'V-submit-channel_length', 'V-json', 'V-media']
    old_result = {'status': 'STOPPED', 'reason': 'ERROR_NOT_JSON', 'cases': {'PASS': 9, 'FAIL': 1, 'NOT_RUN': 29}}
    old_budget = {'used': {'total': 14, 'GET': 4, 'submit': 10, 'search': 0, 'PUT': 0},
                  'stopped': True, 'successful_new_documents': 0}
    old_status = {case_id: {'status': 'PASS'} for case_id in passed}
    old_status['V-bytes'] = {'status': 'FAIL', 'reason': 'ERROR_NOT_JSON'}
    old_status.update({'unstarted-' + str(i): {'status': 'NOT_RUN'} for i in range(29)})
    old_events = [{'request_ordinal': i + 1, 'case_id': 'PREFLIGHT', 'method': 'GET', 'http_status': 200,
                   'expected_match': True, 'outcome': 'SUCCESS', 'actual_code': None} for i in range(4)]
    old_events.extend({'request_ordinal': i + 5, 'case_id': case_id, 'method': 'POST', 'http_status': 400,
                       'expected_match': True, 'outcome': 'REJECTED', 'actual_code': 'VALIDATION_ERROR'}
                      for i, case_id in enumerate(passed))
    old_events.append({'request_ordinal': 14, 'case_id': 'V-bytes', 'method': 'POST', 'http_status': 413,
                       'expected_match': False, 'outcome': 'ERROR_NOT_JSON', 'actual_code': None})
    old_baseline = {'existing_documents': 9, 'metadata_sha256': 'a' * 64}
    assert len(validate_known_gateway_resume(old_result, old_budget, old_status, old_events, old_baseline)) == 9
    import copy
    for mutate in ('accepted', 'unknown', 'status_zero', 'status_null', 'server_5xx', 'wrong_failure', 'budget_reset'):
        b, events, result = copy.deepcopy(old_budget), copy.deepcopy(old_events), copy.deepcopy(old_result)
        if mutate == 'accepted':
            b['successful_new_documents'] = 1
        elif mutate == 'unknown':
            events[0]['actual_code'] = 'RESULT_UNKNOWN'
        elif mutate == 'status_zero':
            events[0]['http_status'] = 0
        elif mutate == 'status_null':
            events[0]['http_status'] = None
        elif mutate == 'server_5xx':
            events[0]['http_status'] = 503
        elif mutate == 'wrong_failure':
            result['reason'] = 'TRANSPORT_OR_INTERNAL_FAILURE'
        else:
            b['used']['total'] = 0
        try:
            validate_known_gateway_resume(result, b, old_status, events, old_baseline)
            raise AssertionError('不安全续跑未拒绝')
        except SafetyStop:
            pass
    print('离线自测通过：预算边界、停止屏障、并发账本、排序、重复及越界命中；网络请求0。')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default=os.environ.get('QUALITY_BASE_URL'))
    parser.add_argument('--credentials-file', default=os.environ.get('QUALITY_CREDENTIALS_FILE'))
    parser.add_argument('--run-prefix', default=os.environ.get('QUALITY_RUN_PREFIX'))
    parser.add_argument('--channel', default=os.environ.get('QUALITY_CHANNEL'))
    parser.add_argument('--output-dir')
    parser.add_argument('--authorized-live', action='store_true', help='主控已完成运行版本/就绪/资源/全轮预算门禁并授权执行')
    parser.add_argument('--resume-known-gateway-dir', help='仅续跑首次已知Nginx 413且零成功写入的停止记录')
    parser.add_argument('--self-test', action='store_true', help='只执行离线helper测试')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    runner = None
    try:
        require(args.authorized_live, 'LIVE_AUTHORIZATION_REQUIRED')
        require(all((args.base_url, args.credentials_file, args.run_prefix, args.channel, args.output_dir)), 'ARGUMENT_MISSING')
        url = urllib.parse.urlsplit(args.base_url)
        require(url.scheme == 'https' and url.hostname and '@' not in url.netloc and not url.username and not url.password
                and url.path in ('', '/') and not url.query and not url.fragment, 'BASE_URL_INVALID')
        args.base_url = args.base_url.rstrip('/')
        require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,99}', args.run_prefix), 'PREFIX_INVALID')
        require(re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,54}', args.channel), 'CHANNEL_INVALID')
        path = Path(args.credentials_file)
        info = path.lstat()
        require(stat.S_ISREG(info.st_mode) and not path.is_symlink() and stat.S_IMODE(info.st_mode) == 0o600
                and info.st_uid == os.getuid(), 'CREDENTIALS_FILE_PERMISSIONS')
        with path.open(encoding='utf-8') as stream:
            credentials = json.load(stream)
        require(isinstance(credentials, dict) and all(isinstance(credentials.get(k), str) and credentials[k]
                                                    for k in ('username', 'password')), 'CREDENTIALS_FORMAT')
        require(':' not in credentials['username'] and '\n' not in credentials['username']
                and '\r' not in credentials['username'], 'CREDENTIALS_USERNAME_INVALID')
        token = base64.b64encode((credentials['username'] + ':' + credentials['password']).encode('utf-8')).decode('ascii')
        manifest_path = Path(__file__).with_name('functional-cases.json')
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        require(len(manifest['cases']) == 39 and len(manifest['fixtures']) == 12, 'MANIFEST_COUNT')
        resume = load_known_gateway_resume(args.resume_known_gateway_dir) if args.resume_known_gateway_dir else None
        if resume:
            require(Path(args.output_dir).resolve() != Path(args.resume_known_gateway_dir).resolve()
                    and Path(args.output_dir).name == 'functional-results-attempt2', 'RESUME_OUTPUT_MUST_BE_ATTEMPT2')
        runner = Runner(args, manifest, 'Basic ' + token, resume=resume)
        runner.execute()
        runner.finish(True, 'ALL_DECLARED_CASES_COMPLETED')
        print('功能执行结束；请核对受限输出目录中的白名单事件、case-status、预算与一致性结果。')
        return 0
    except Exception as error:
        reason = error.args[0] if isinstance(error, SafetyStop) else 'LOCAL_OR_NETWORK_FAILURE'
        if runner:
            runner.budget.stop()
            runner.finish(False, reason)
        print('执行停止：' + reason, file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
