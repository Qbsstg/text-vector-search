#!/usr/bin/env python3
"""对本轮独立渠道进行前后计数核账；不删除数据，不重试未知写入。"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import ssl
import stat
import time
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


def write(path, value):
    with path.open('w') as stream:
        os.chmod(path, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)


def console_events(path):
    values = []
    for line in path.read_text().splitlines():
        outer = json.loads(line)
        value = json.loads(outer['msg']) if isinstance(outer.get('msg'), str) else outer
        if value.get('type') in ('REQUEST', 'STOP', 'BUDGET_LIMIT'):
            values.append(value)
    return values


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['before', 'after'])
    parser.add_argument('--run-config', required=True)
    parser.add_argument('--credentials-file', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--negative-oracle-file')
    parser.add_argument('--baseline-dir')
    parser.add_argument('--k6-console')
    parser.add_argument('--authorized-live', action='store_true')
    args = parser.parse_args()
    if not args.authorized_live:
        raise SystemExit('LIVE_AUTHORIZATION_REQUIRED')
    config = json.loads(Path(args.run_config).read_text())
    target = urllib.parse.urlsplit(config['baseUrl'])
    if target.scheme != 'https' or target.username or target.password or target.path or target.query or target.fragment:
        raise SystemExit('HTTPS_ORIGIN_REQUIRED')
    file = Path(args.credentials_file)
    info = file.lstat()
    if not stat.S_ISREG(info.st_mode) or file.is_symlink() or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.getuid():
        raise SystemExit('CREDENTIALS_PERMISSIONS')
    credentials = json.loads(file.read_text())
    token = 'Basic ' + base64.b64encode((credentials['username'] + ':' + credentials['password']).encode()).decode()
    output = Path(args.output_dir)
    output.mkdir(mode=0o700, exist_ok=False)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                        urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    calls = []
    last_get = 0.0

    def request(method, path, body=None):
        nonlocal last_get
        if len(calls) >= 10 or method == 'POST' and any(c['method'] == 'POST' for c in calls):
            raise RuntimeError('AUDIT_BUDGET_EXHAUSTED')
        if method == 'GET':
            time.sleep(max(0, 0.25 - (time.monotonic() - last_get)))
            last_get = time.monotonic()
        headers = {'Authorization': token, 'Accept': 'application/json'}
        raw = None
        if body is not None:
            headers.update({'Content-Type': 'application/json', 'Origin': config['baseUrl']})
            raw = json.dumps(body, ensure_ascii=False).encode()
        record = {'method': method, 'startedUtc': datetime.now(timezone.utc).isoformat(), 'status': None}
        calls.append(record)
        try:
            try:
                response = opener.open(urllib.request.Request(config['baseUrl'] + path, data=raw,
                                                              method=method, headers=headers), timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                record['status'] = response.status
                data = response.read(2_000_001)
            record['responseSha256'] = hashlib.sha256(data).hexdigest()
            if record['status'] != 200 or len(data) > 2_000_000:
                raise RuntimeError('AUDIT_HTTP_STATUS_OR_SIZE')
            return json.loads(data)
        finally:
            write(output / 'requests.json', calls)

    def all_documents():
        data = request('GET', '/api/documents?page=0&size=100')
        if data['totalElements'] != len(data['items']) or len(data['items']) > 100:
            raise RuntimeError('AUDIT_SINGLE_PAGE_BOUND')
        return {row['documentId']: row for row in data['items']}

    fixtures = json.loads(Path(__file__).with_name('functional-cases.json').read_text())['fixtures']
    aliases = {config['runPrefix'] + '-' + f['alias']: f['alias'] for f in fixtures}
    rows = all_documents()
    if args.stage == 'before':
        negative = None
        if args.negative_oracle_file:
            oracle = json.loads(Path(args.negative_oracle_file).read_text())
            result = request('POST', '/api/search', {'text': oracle['query'], 'topK': 100, 'channel': config['channel']})
            hits = result['hits']
            expected = {key for key, row in rows.items() if key in aliases and row['channel'] == config['channel']
                        and not row['invalidated'] and row['vectorReady'] and row['taskStatus'] == 'SUCCEEDED'}
            if len(hits) != 9 or {h['documentId'] for h in hits} != expected:
                raise RuntimeError('NEGATIVE_HIT_SCOPE')
            if hits != sorted(hits, key=lambda h: (-h['score'], h['documentId'])):
                raise RuntimeError('NEGATIVE_SORT')
            minimum = min(h['score'] for h in hits)
            if not minimum < 0 or not math.isclose(minimum, oracle['minimum'], abs_tol=1e-9):
                raise RuntimeError('NEGATIVE_ORACLE_MISMATCH')
            negative = {'status': 'PASS', 'minimum': minimum,
                        'hits': [{'alias': aliases[h['documentId']], 'score': h['score']} for h in hits]}
            write(output / 'negative-probe.json', negative)
            after = all_documents()
            for key, old in rows.items():
                expected_row = dict(old)
                if key in expected:
                    expected_row['hitCount'] = str(int(old['hitCount']) + 1)
                if expected_row != after[key]:
                    raise RuntimeError('NEGATIVE_COUNT_OR_METADATA_MISMATCH')
            rows = after
        write(output / 'baseline.json', {'rows': rows, 'newFixtureAliases': aliases, 'negativeProbe': negative})
        print(json.dumps({'status': 'PASS', 'baselineDocuments': len(rows), 'negativeProbe': bool(negative)}))
    else:
        baseline = json.loads((Path(args.baseline_dir) / 'baseline.json').read_text())
        events = console_events(Path(args.k6_console))
        if any(e['type'] == 'STOP' for e in events):
            raise RuntimeError('LOAD_STOPPED_NO_AUTOMATIC_COUNTER_CONFIRMATION')
        deltas = dict.fromkeys(aliases, 0)
        submissions = []
        for event in events:
            if event['type'] != 'REQUEST':
                continue
            if event['endpoint'] == 'search' and event['outcome'] == 'SUCCESS':
                for hit in event['hits']:
                    key = config['runPrefix'] + '-' + hit['alias']
                    if key not in aliases or hit['alias'] in ('race', 'neighbor', 'default'):
                        raise RuntimeError('LOAD_HIT_SCOPE')
                    deltas[key] += 1
            if event['endpoint'] == 'submit' and event['outcome'] == 'SUCCESS':
                submissions.append(event)
        if len(submissions) != 4 or len(rows) != len(baseline['rows']) + 4:
            raise RuntimeError('LOAD_DOCUMENT_BUDGET_OR_COMPLETENESS')
        for key, old in baseline['rows'].items():
            expected_row = dict(old)
            if key in deltas:
                expected_row['hitCount'] = str(int(old['hitCount']) + deltas[key])
            if rows.get(key) != expected_row:
                raise RuntimeError('LOAD_COUNT_OR_METADATA_MISMATCH')
        task_times = []
        for event in submissions:
            task = request('GET', '/api/tasks/' + urllib.parse.quote(event['taskId'], safe=''))
            if task['status'] != 'SUCCEEDED' or not task.get('document') or not task['document']['vectorReady']:
                raise RuntimeError('LOAD_TASK_NOT_SUCCEEDED')
            if task['document']['channel'] != config['channel'] + '-ingest':
                raise RuntimeError('INGEST_CHANNEL_MISMATCH')
            task_times.append({key: task[key] for key in ['submittedAt', 'startedAt', 'finishedAt', 'status']})
        result = {'status': 'PASS', 'historicalAndNonHitMetadataUnchanged': True,
                  'newDocuments': len(submissions), 'allNewTasksSucceeded': True,
                  'countDeltas': {aliases[key]: value for key, value in deltas.items()},
                  'taskTimes': task_times, 'getRequests': len(calls)}
        write(output / 'counter-audit.json', result)
        print(json.dumps({k: v for k, v in result.items() if k != 'taskTimes'}))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('核账停止：' + (str(error) if isinstance(error, RuntimeError) else type(error).__name__))
        raise SystemExit(1)
