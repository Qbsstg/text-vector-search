#!/usr/bin/env bash
# 仅在另获具体操作授权后执行。标志不等于授权；脚本不停止服务、不打开H2。
# 只冷复制到全新目录；恢复校验不会替换生产库，也不删除失败时的现场。
set -euo pipefail
[[ $# == 5 && "$1" == --authorized-cold-copy ]] || {
    printf '用法：bash cold-copy.sh --authorized-cold-copy backup|restore-check 源绝对目录 新目标绝对目录 完整源码SHA\n' >&2
    exit 2
}
python3 - "${@:2}" <<'PY'
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

def reject(message):
    raise SystemExit('拒绝：' + message)

mode, source_name, destination_name, source_sha = sys.argv[1:]
if mode not in ('backup', 'restore-check'):
    reject('仅支持backup或restore-check')
if not re.fullmatch(r'[0-9a-f]{40}', source_sha):
    reject('必须记录固定源码SHA')
source, destination = Path(source_name), Path(destination_name)
if not source.is_absolute() or not destination.is_absolute():
    reject('必须使用绝对路径')
for path in (source, destination):
    if '..' in path.parts or any(part.is_symlink() for part in (path, *path.parents)):
        reject('路径不能包含上级跳转或任何符号链接')
    if str(path) != os.path.normpath(str(path)):
        reject('路径必须规范化')
if mode == 'backup':
    if source != Path('/var/lib/vector-search/db'):
        reject('备份源只能是本项目固定生产数据目录')
    if destination.parent != Path('/var/backups/vector-search'):
        reject('备份只能进入本项目备份根下全新目录')
else:
    if source.parent != Path('/var/backups/vector-search'):
        reject('恢复源只能是本项目独立备份目录')
    if destination.parent != Path('/var/lib/vector-search') or not destination.name.startswith('restore-check-'):
        reject('恢复只能进入本项目全新restore-check目录，禁止生产替换')
if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', destination.name):
    reject('目标目录名称不安全')
if destination.exists() or not destination.parent.is_dir():
    reject('目标已存在或父目录不存在，绝不覆盖或递归创建')
if not source.is_dir():
    reject('源目录不存在')
if sys.platform != 'linux' or os.geteuid() != 0:
    reject('必须在已授权Linux主机以root核验完整进程可见性')
if not shutil.which('lsof') or not shutil.which('systemctl'):
    reject('需要systemctl和lsof，脚本不会安装工具')
# 已启用的单实例unit与备份共享同一文件锁；检查期间持有，不只测一次锁状态。
lock = Path('/var/lib/vector-search/db/.instance.lock')
if not lock.is_file() or lock.is_symlink() or lock.parent.is_symlink():
    reject('缺少已受管的单实例锁，不能创建替代锁绕过检查')
descriptor = os.open(lock, os.O_RDONLY | os.O_NOFOLLOW)
try:
    fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
except BlockingIOError:
    reject('单实例锁被占用')
state = subprocess.run(['systemctl', 'show', 'vector-search.service', '--property=ActiveState', '--value'], capture_output=True, text=True)
if state.returncode or state.stdout.strip() != 'inactive':
    reject('服务未明确inactive；failed/超时必须先诊断，不能当作干净停止')
def check_open_files(inspection, own_pid, lock_fd, lock_path):
    # lsof退出1可以同时有有效匹配，不能据此忽略占用或误认输出矛盾。
    # 仅接受所请求的p/f/n机器字段，每个文件必须同时有PID、FD和完整路径。
    if inspection.stderr or inspection.returncode not in (0, 1):
        reject('lsof检查不可靠')
    if not inspection.stdout:
        if inspection.returncode == 0:
            reject('lsof格式异常：成功状态却没有记录')
        return
    pid, file_fd, name = None, None, None
    process_has_file = False
    def finish_file():
        if file_fd is None or name is None:
            reject('lsof格式异常：文件字段不完整')
        # 自身也仅允许实际持有的永久锁FD，不能放过自身打开的数据库。
        if pid != own_pid or file_fd != str(lock_fd) or name != lock_path:
            reject('存在其他进程文件占用或自身非锁文件占用')
    for line in inspection.stdout.splitlines():
        if not line:
            reject('lsof格式异常：空字段')
        field, value = line[0], line[1:]
        if field == 'p':
            if pid is not None:
                if not process_has_file:
                    reject('lsof格式异常：进程缺少文件')
                finish_file()
            if not value.isascii() or not value.isdecimal() or int(value) <= 0:
                reject('lsof格式异常：PID非法')
            pid = int(value)
            if pid != own_pid:
                reject('存在其他进程文件占用')
            file_fd, name, process_has_file = None, None, False
        elif field == 'f':
            if pid is None or not value:
                reject('lsof格式异常：FD缺少进程')
            if file_fd is not None:
                finish_file()
            file_fd, name, process_has_file = value, None, True
        elif field == 'n':
            if file_fd is None or name is not None or not value:
                reject('lsof格式异常：路径重复或缺少FD')
            name = value
        else:
            reject('lsof格式异常：未知字段')
    if pid is None or not process_has_file:
        reject('lsof格式异常：缺少文件记录')
    finish_file()

# restore-check也要求生产当前停止；保守避免把活动现场误认为冷恢复演练。
for directory in {source, Path('/var/lib/vector-search/db')}:
    inspection = subprocess.run(['lsof', '-nP', '-Fpfn', '+D', str(directory)], capture_output=True, text=True)
    check_open_files(inspection, os.getpid(), descriptor, str(lock))
database = source / 'knowledge.mv.db'
if database.is_symlink() or not database.is_file():
    reject('源必须包含普通knowledge.mv.db文件')
# 首版只支持H2单一主文件；出现未知附件或锁文件时交由操作者诊断。
allowed = {'knowledge.mv.db', 'knowledge.trace.db', '.instance.lock'} if mode == 'backup' else {'knowledge.mv.db', 'backup-manifest.json'}
if any(p.name not in allowed or p.is_symlink() or not p.is_file() for p in source.iterdir()):
    reject('源有未知附件、链接或目录，不能保证完整冷复制')
def checksum(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()
before = checksum(database)
if mode == 'restore-check':
    metadata = json.loads((source / 'backup-manifest.json').read_text())
    if metadata.get('source_sha') != source_sha or metadata.get('database_sha256') != before:
        reject('备份版本或数据库校验和不匹配')
destination.mkdir(mode=0o700)
copy = destination / database.name
# 只创建新文件；异常保留目标现场，不做自动清理或生产恢复。
with database.open('rb') as origin, copy.open('xb') as target:
    os.chmod(copy, 0o600)
    shutil.copyfileobj(origin, target)
    target.flush()
    os.fsync(target.fileno())
if checksum(copy) != before or checksum(database) != before:
    reject('复制前后校验不一致，保留现场且不得使用此备份')
metadata = {'source_sha': source_sha, 'database_sha256': before, 'created_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'mode': mode}
with (destination / 'backup-manifest.json').open('x') as stream:
    os.chmod(stream.name, 0o600)
    json.dump(metadata, stream, ensure_ascii=False, indent=2)
    stream.flush()
    os.fsync(stream.fileno())
print('全新独立目录冷复制和校验成功；未打开H2，未证明业务可读，未替换生产库')
os.close(descriptor)
PY
