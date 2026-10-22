#!/usr/bin/env bash
# 仅供一次性GitHub-hosted Linux runner；不在工作站、自托管runner或服务器执行。
# systemctl的inactive仅为脚本层前置模拟，真实Java/H2/lsof/flock和文件校验不模拟。
set -euo pipefail
[[ $# == 2 && "$1" == --github-hosted-test-only ]] || { echo '用法：final-linux-check.sh --github-hosted-test-only target/vector-search.jar' >&2; exit 2; }
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted && "$(uname -s)" == Linux ]] || {
    echo 'NOT_RUN：只允许一次性GitHub-hosted Linux runner，不把非Linux拒绝算通过' >&2; exit 2;
}
python3 - "$2" <<'PY'
import base64
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import select
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

jar = Path(sys.argv[1]).resolve(strict=True)
if not jar.is_file():
    raise SystemExit('候选JAR必须存在')
project = Path.cwd()
script = project / 'deploy/scripts/cold-copy.sh'
roots = [Path('/var/lib/vector-search'), Path('/var/backups/vector-search')]
if any(p.exists() or p.is_symlink() for p in roots):
    raise SystemExit('拒绝：固定测试目录已存在，不能触及任何已有数据')
for command in ('java', 'python3', 'lsof', 'sudo'):
    if not shutil.which(command):
        raise SystemExit('缺少前置工具：' + command)
version = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
if not re.search(r'version "17\.', version.stderr + version.stdout):
    raise SystemExit('仅使用Java17')
subprocess.run(['sudo', '-n', 'true'], check=True, timeout=5)
sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
if not re.fullmatch('[0-9a-f]{40}', sha):
    raise SystemExit('无法记录完整源码SHA')
run_id = uuid.uuid4().hex
created = []
process = None
holder = None
evidence = project / 'target/final-linux-evidence'
evidence.mkdir(parents=True, exist_ok=True)
summary = evidence / 'summary.txt'

def note(message):
    with summary.open('a') as stream:
        stream.write(message + '\n')
    print(message, flush=True)

note('sourceSHA=' + sha + ';jarSHA256=' + hashlib.sha256(jar.read_bytes()).hexdigest() + ';runId=' + run_id)
note('systemctl为仅此进程PATH内的inactive测试shim；不证明真实systemd停止/启动或服务器部署')

def sudo(*args):
    return subprocess.run(['sudo', '-n', *map(str, args)], check=True, capture_output=True, text=True, timeout=15)

def request(port, method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('http://127.0.0.1:' + str(port) + path, data=data, method=method,
                                 headers={'Content-Type': 'application/json'} if data else {})
    with urllib.request.urlopen(req, timeout=5) as response:
        return json.load(response)

def wait_until(check, seconds=35):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result is not None:
            return result
        # 这里只等待明确健康/状态条件，不能用固定延时当作恢复/事务成功证据。
        time.sleep(0.05)
    raise AssertionError('等待真实条件超时')

def start_app(data, name):
    global process
    log = evidence / (name + '-jar.txt')
    output = log.open('wb')
    process = subprocess.Popen(['java', '-jar', str(jar), '--APP_DATA_DIR=' + str(data), '--server.address=127.0.0.1',
                                '--server.port=0', '--spring.datasource.username=sa', '--spring.datasource.password='],
                               stdout=output, stderr=subprocess.STDOUT)
    output.close()
    def ready():
        if process.poll() is not None:
            raise AssertionError('真实JAR提前退出：' + str(log))
        match = re.search(r'Tomcat started on port (\d+)', log.read_text(errors='replace'))
        if not match:
            return None
        port = int(match.group(1))
        try:
            health = request(port, 'GET', '/health')
            return port if health['businessReady'] is True else None
        except (urllib.error.URLError, TimeoutError):
            return None
    port = wait_until(ready)
    note('真实JAR已就绪：pid=' + str(process.pid) + ';port=' + str(port) + ';mode=' + name)
    return port

def stop_app():
    global process
    if process is not None and process.poll() is None:
        process.send_signal(signal.SIGTERM)
        code = process.wait(timeout=45)
        note('真实JAR SIGTERM退出：pid=' + str(process.pid) + ';exit=' + str(code))
        if code not in (143, -signal.SIGTERM, 0):
            raise AssertionError('JAR停止异常')
    process = None

try:
    for root in roots:
        # mkdir独占创建：即使检查后出现并发目录也拒绝，不使用可接管已有目录的install -d。
        sudo('mkdir', '-m', '0700', root)
        sudo('chown', str(os.getuid()) + ':' + str(os.getgid()), root)
        (root / '.qa-final-marker').write_text(run_id)
        created.append(root)
    db = roots[0] / 'db'
    db.mkdir(mode=0o700)
    (db / '.instance.lock').touch(mode=0o600)
    with tempfile.TemporaryDirectory(prefix='qa-final-linux-') as temp:
        shims = Path(temp)
        shim = shims / 'systemctl'
        shim.write_text('#!/bin/sh\n[ "$1" = show ] && [ "$2" = vector-search.service ] || exit 2\nprintf "inactive\\n"\n')
        shim.chmod(0o700)
        env_path = str(shims) + ':' + os.environ['PATH']
        cold_sequence = 0
        def diagnose_lsof(source, prefix):
            # 在相同sudo/PATH/cwd和既有锁FD上下文执行真实lsof；只读诊断先释放自身锁，再运行正式脚本。
            diagnostic_code = r"""
import base64, fcntl, json, os, shutil, subprocess, sys
from pathlib import Path
lock = Path('/var/lib/vector-search/db/.instance.lock')
fd = os.open(lock, os.O_RDONLY | os.O_NOFOLLOW)
owned = False
try:
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        owned = True
    except BlockingIOError:
        pass
    output = {'pid': os.getpid(), 'euid': os.geteuid(), 'cwd': os.getcwd(),
              'path': os.environ.get('PATH'), 'lsof_executable': shutil.which('lsof'),
              'lock_acquired': owned, 'inspections': []}
    version = subprocess.run(['lsof', '-v'], capture_output=True, timeout=5)
    output['lsof_version'] = {'returncode': version.returncode,
                              'stdout_base64': base64.b64encode(version.stdout).decode(),
                              'stderr_base64': base64.b64encode(version.stderr).decode()}
    for directory in sorted({sys.argv[1], '/var/lib/vector-search/db'}):
        command = ['lsof', '-nP', '+D', directory]
        try:
            result = subprocess.run(command, capture_output=True, timeout=5)
            item = {'command': command, 'returncode': result.returncode, 'timeout': False,
                    'stdout_base64': base64.b64encode(result.stdout).decode(),
                    'stderr_base64': base64.b64encode(result.stderr).decode()}
        except subprocess.TimeoutExpired as failure:
            item = {'command': command, 'returncode': None, 'timeout': True,
                    'stdout_base64': base64.b64encode(failure.stdout or b'').decode(),
                    'stderr_base64': base64.b64encode(failure.stderr or b'').decode()}
        output['inspections'].append(item)
    print(json.dumps(output, ensure_ascii=False))
finally:
    os.close(fd)
"""
            diagnostic = subprocess.run(['sudo', '-n', 'env', 'PATH=' + env_path, 'python3', '-c', diagnostic_code, str(source)],
                                        capture_output=True, timeout=20)
            # 保留原始字节，不用strip/合并流掩盖退出码与输出矛盾。
            (evidence / (prefix + '-diagnostic.stdout.txt')).write_bytes(diagnostic.stdout)
            (evidence / (prefix + '-diagnostic.stderr.txt')).write_bytes(diagnostic.stderr)
            (evidence / (prefix + '-diagnostic-exit.txt')).write_text(str(diagnostic.returncode) + '\n')
            if diagnostic.returncode != 0:
                raise AssertionError('lsof诊断程序失败，查看' + prefix + '原始stdout/stderr')
            details = json.loads(diagnostic.stdout)
            version = details['lsof_version']
            (evidence / (prefix + '-lsof-version.stdout.txt')).write_bytes(base64.b64decode(version['stdout_base64']))
            (evidence / (prefix + '-lsof-version.stderr.txt')).write_bytes(base64.b64decode(version['stderr_base64']))
            (evidence / (prefix + '-lsof-version-exit.txt')).write_text(str(version['returncode']) + '\n')
            for index, inspection in enumerate(details['inspections']):
                name = prefix + '-lsof-' + str(index)
                (evidence / (name + '.stdout.txt')).write_bytes(base64.b64decode(inspection['stdout_base64']))
                (evidence / (name + '.stderr.txt')).write_bytes(base64.b64decode(inspection['stderr_base64']))
                (evidence / (name + '.metadata.json')).write_text(json.dumps({
                    key: value for key, value in inspection.items() if not key.endswith('_base64')}, ensure_ascii=False, indent=2))
                note('DIAGNOSTIC ' + name + ': returncode=' + str(inspection['returncode'])
                     + ';timeout=' + str(inspection['timeout']) + ';euid=' + str(details['euid'])
                     + ';lock_acquired=' + str(details['lock_acquired']) + ';directory=' + inspection['command'][-1])

        def cold(mode, source, destination, version=sha, reject=None):
            global cold_sequence
            cold_sequence += 1
            prefix = 'cold-' + str(cold_sequence).zfill(2) + '-' + destination.name
            diagnose_lsof(source, prefix)
            result = subprocess.run(['sudo', '-n', 'env', 'PATH=' + env_path, 'bash', str(script),
                                     '--authorized-cold-copy', mode, str(source), str(destination), version],
                                    capture_output=True, text=True, timeout=20)
            (evidence / (prefix + '-cold.stdout.txt')).write_text(result.stdout)
            (evidence / (prefix + '-cold.stderr.txt')).write_text(result.stderr)
            (evidence / (prefix + '-cold-exit.txt')).write_text(str(result.returncode) + '\n')
            if reject:
                if result.returncode == 0 or reject not in result.stdout + result.stderr:
                    raise AssertionError('拒绝原因不符合：' + result.stdout + result.stderr)
                note('PASS 拒绝' + reject)
            else:
                if result.returncode != 0:
                    raise AssertionError(result.stdout + result.stderr)
                note('PASS ' + mode + '：' + str(destination))
        port = start_app(db, 'original')
        task = request(port, 'POST', '/api/documents', {'documentId': 'linux-fixture', 'text': 'ababa'})['taskId']
        wait_until(lambda: True if request(port, 'GET', '/api/tasks/' + task)['status'] == 'SUCCEEDED' else None)
        request(port, 'POST', '/api/search', {'text': 'ababa', 'topK': 1})
        request(port, 'PUT', '/api/documents/linux-fixture/invalidation')
        original = request(port, 'GET', '/api/documents/linux-fixture')
        assert original['hitCount'] == '1' and original['invalidated'] is True
        # 直接JAR不持unit的外部flock；这里必须由真实lsof发现活动H2而拒绝。
        cold('backup', db, roots[1] / 'active-rejected', reject='存在其他进程文件占用')
        stop_app()
        holder = subprocess.Popen(['python3', '-u', '-c',
            'import fcntl,sys; f=open(sys.argv[1]); fcntl.flock(f,fcntl.LOCK_EX); print("LOCKED",flush=True); sys.stdin.readline()',
            str(db / '.instance.lock')], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        if not select.select([holder.stdout], [], [], 5)[0] or holder.stdout.readline().strip() != 'LOCKED':
            raise AssertionError('未确认真实flock持锁')
        cold('backup', db, roots[1] / 'lock-rejected', reject='单实例锁被占用')
        holder.communicate('\n', timeout=5)
        assert holder.returncode == 0
        holder = None
        backup = roots[1] / 'backup-case'
        cold('backup', db, backup)
        manifest = json.loads(sudo('cat', backup / 'backup-manifest.json').stdout)
        database_hash = sudo('sha256sum', backup / 'knowledge.mv.db').stdout.split()[0]
        assert manifest['source_sha'] == sha and manifest['database_sha256'] == database_hash
        assert sudo('stat', '-c', '%a', backup).stdout.strip() == '700'
        assert sudo('stat', '-c', '%a', backup / 'knowledge.mv.db').stdout.strip() == '600'
        note('PASS 冷备manifest/SHA256/0700目录/0600文件：' + database_hash)
        cold('backup', db, backup, reject='目标已存在')
        cold('restore-check', backup, roots[0] / 'restore-check-wrong-sha', version='0' * 40, reject='备份版本或数据库校验和不匹配')
        restored = roots[0] / 'restore-check-case'
        cold('restore-check', backup, restored)
        assert sudo('sha256sum', restored / 'knowledge.mv.db').stdout.split()[0] == database_hash
        cold('restore-check', backup, restored, reject='目标已存在')
        # 仅为当前自建恢复目录授权测试用户打开；不改变备份原件或生产目录。
        sudo('chown', '-R', str(os.getuid()) + ':' + str(os.getgid()), restored)
        restored_port = start_app(restored, 'restored')
        reopened = request(restored_port, 'GET', '/api/documents/linux-fixture')
        for field in ('documentId', 'taskId', 'taskStatus', 'vectorReady', 'hitCount', 'invalidated', 'invalidatedAt', 'vectorCompletedAt', 'text'):
            assert reopened[field] == original[field], field
        assert request(restored_port, 'POST', '/api/search', {'text': 'ababa', 'topK': 1})['hits'] == []
        stop_app()
        assert sudo('sha256sum', backup / 'knowledge.mv.db').stdout.split()[0] == database_hash
        note('PASS 真实JAR恢复状态/向量就绪/首次时间/计数/失效保持且备份原件未改')
        note('COMPLETE：仅一次性Linux runner本地验收；真实systemd/服务器数据恢复/发布仍NOT_RUN')
finally:
    if holder is not None and holder.poll() is None:
        holder.kill()
        holder.wait(timeout=5)
    if process is not None and process.poll() is None:
        process.send_signal(signal.SIGTERM)
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    for root in reversed(created):
        # 只清理由本次从不存在状态创建并带精确runId标记的固定测试目录。
        if root.is_symlink() or any(parent.is_symlink() for parent in root.parents) or (root / '.qa-final-marker').read_text() != run_id:
            raise RuntimeError('拒绝清理标记异常目录：' + str(root))
        sudo('rm', '-rf', '--', root)
PY
