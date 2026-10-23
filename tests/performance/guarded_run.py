#!/usr/bin/env python3
"""用连续只读采样守护有界客户端；只停止自己启动的客户端和监控，不操作应用。"""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import threading
import time


def save(path, value):
    with path.open('w') as stream:
        os.chmod(path, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)


def stop_client(process):
    """只回收本监督器创建的进程组；分级终止均有上界，不信号生产应用。"""
    if process is None or process.poll() is not None:
        return
    for sig, seconds in [(signal.SIGINT, 4), (signal.SIGTERM, 2), (signal.SIGKILL, 2)]:
        if process.poll() is not None:
            return
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            process.wait(timeout=seconds)
            return
        try:
            process.wait(timeout=seconds)
            return
        except subprocess.TimeoutExpired:
            continue
    raise RuntimeError('OWN_CLIENT_REAP_FAILED')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--monitor-command-file', required=True)
    parser.add_argument('--client-command-file', required=True)
    parser.add_argument('--output-dir', required=True)
    parser.add_argument('--max-seconds', required=True, type=int)
    parser.add_argument('--authorized-live', action='store_true')
    args = parser.parse_args()
    if not args.authorized_live or not 1 <= args.max_seconds <= 300:
        raise SystemExit('LIVE_AUTHORIZATION_OR_BOUND_REQUIRED')
    output = Path(args.output_dir)
    output.mkdir(mode=0o700, exist_ok=False)
    os.chmod(output, 0o700)
    monitor_command = json.loads(Path(args.monitor_command_file).read_text())
    client = json.loads(Path(args.client_command_file).read_text())
    if not isinstance(monitor_command, list) or not all(isinstance(x, str) for x in monitor_command):
        raise SystemExit('MONITOR_COMMAND_INVALID')
    if not isinstance(client.get('command'), list) or not all(isinstance(x, str) for x in client['command']):
        raise SystemExit('CLIENT_COMMAND_INVALID')
    lock = threading.Lock()
    state = {'lastSample': None, 'safeSample': False, 'reasons': [], 'summary': None}
    monitor_error = (output / 'monitor-stderr.log').open('x')
    os.chmod(monitor_error.name, 0o600)
    monitor = subprocess.Popen(monitor_command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=monitor_error, text=True, start_new_session=True)

    def observe():
        with (output / 'monitor.jsonl').open('x') as stream:
            os.chmod(stream.name, 0o600)
            for line in monitor.stdout:
                try:
                    sample = json.loads(line)
                except ValueError:
                    with lock:
                        state['reasons'].append('MONITOR_NON_JSON')
                    continue
                stream.write(json.dumps({'receivedEpoch': time.time(), 'sample': sample}) + '\n')
                stream.flush()
                with lock:
                    if sample.get('type') == 'SAMPLE':
                        state['lastSample'] = time.monotonic()
                        state['safeSample'] = sample.get('businessReady') is True and not sample.get('abortReasons')
                        state['reasons'].extend(sample.get('abortReasons', []))
                    if sample.get('type') == 'SUMMARY':
                        state['summary'] = sample

    reader = threading.Thread(target=observe, daemon=True)
    reader.start()
    ready_deadline = time.monotonic() + 15
    process = None
    stopped = False
    began = None
    ended = None
    reasons = []
    try:
        while time.monotonic() < ready_deadline:
            with lock:
                safe = state['safeSample']
                reasons = list(state['reasons'])
            if safe or reasons or monitor.poll() is not None:
                break
            time.sleep(0.1)
        if not safe or reasons:
            raise RuntimeError('SAFE_MONITOR_SAMPLE_REQUIRED')
        environment = {key: value for key, value in os.environ.items() if not key.startswith('K6_')}
        environment.update(client.get('environment', {}))
        with (output / 'client-stdout.log').open('x') as stdout, (output / 'client-stderr.log').open('x') as stderr:
            os.chmod(stdout.name, 0o600)
            os.chmod(stderr.name, 0o600)
            began = time.monotonic()
            process = subprocess.Popen(client['command'], stdout=stdout, stderr=stderr, env=environment,
                                       start_new_session=True)
            with (output / 'client-resources.jsonl').open('x') as metrics:
                os.chmod(metrics.name, 0o600)
                while process.poll() is None:
                    now = time.monotonic()
                    with lock:
                        reasons = list(state['reasons'])
                        last = state['lastSample']
                    if last is None or now - last > 4:
                        reasons.append('MONITOR_STALE_OVER_FOUR_SECONDS')
                    if monitor.poll() is not None:
                        reasons.append('MONITOR_ENDED_BEFORE_CLIENT')
                    if now - began > args.max_seconds:
                        reasons.append('CLIENT_WALL_BUDGET_EXCEEDED')
                    observed = subprocess.run(['/bin/ps', '-p', str(process.pid), '-o', 'pcpu=,rss='],
                                              capture_output=True, text=True, timeout=2)
                    fields = observed.stdout.split()
                    if len(fields) == 2:
                        metrics.write(json.dumps({'epoch': time.time(), 'elapsedSeconds': now - began,
                                                  'cpuPercentPsSample': float(fields[0]),
                                                  'rssBytes': int(fields[1]) * 1024}) + '\n')
                        metrics.flush()
                    if reasons:
                        stopped = True
                        stop_client(process)
                        break
                    time.sleep(0.5)
    except Exception as error:
        stopped = True
        reasons.append(str(error) if isinstance(error, RuntimeError) else type(error).__name__)
        if process is not None and process.poll() is None:
            stop_client(process)
    finally:
        if process is not None and process.poll() is None:
            stop_client(process)
        ended = time.monotonic()
        if monitor.poll() is None:
            try:
                monitor.stdin.write('STOP\n')
                monitor.stdin.flush()
                monitor.stdin.close()
                monitor.wait(timeout=5)
            except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
                if monitor.poll() is None:
                    os.killpg(monitor.pid, signal.SIGTERM)
                    monitor.wait(timeout=3)
        reader.join(timeout=2)
        monitor_error.close()
    with lock:
        final_summary = state['summary']
        reasons.extend(state['reasons'])
    if process is not None and process.returncode != 0 and not reasons:
        reasons.append('CLIENT_REPORTED_FAILURE')
    result = {'status': 'PASS' if not stopped and process is not None and process.returncode == 0 else 'STOPPED',
              'clientReturnCode': None if process is None else process.returncode,
              'controllerObservedClientWallSeconds': None if began is None else ended - began,
              'stopReasons': sorted(set(reasons)), 'monitorSummary': final_summary,
              'unknownInFlightPolicy': '停止后不重试；若客户端被中断，保留可能未知的业务结果，单独诊断'}
    save(output / 'controller-result.json', result)
    print(json.dumps({'status': result['status'], 'stopReasons': result['stopReasons'],
                      'clientReturnCode': result['clientReturnCode']}))
    return 0 if result['status'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
