#!/usr/bin/env bash
# 仅用于明确授权主机上的DEP2一次性准备，不承担应用发布。
# 已存在的项目资源一律拒绝，避免误认所有权或覆盖配置。
set -euo pipefail
if [[ "${1:-}" == --help && $# == 1 ]]; then
    printf 'Usage: bash prepare-host-dep2.sh --apply-on-authorized-host\nCreates project account/directories/env and an inactive unit. No application deployment.\n'
    exit 0
fi
[[ $# == 1 && "$1" == --apply-on-authorized-host ]] || { printf 'Explicit authorized-host flag required\n' >&2; exit 2; }
[[ $(id -u) == 0 ]] || { printf 'Requires authorized root session\n' >&2; exit 1; }
for path in /opt/vector-search /var/lib/vector-search /etc/vector-search /var/backups/vector-search /etc/systemd/system/vector-search.service /usr/lib/systemd/system/vector-search.service /lib/systemd/system/vector-search.service; do
    if [[ -e "$path" || -L "$path" ]]; then
        printf 'CONFLICT: %s\n' "$path" >&2
        exit 1
    fi
done
if getent passwd vector-search >/dev/null || getent group vector-search >/dev/null; then
    printf 'CONFLICT: project account/group exists\n' >&2
    exit 1
fi
# 此运行时路径已在授权主机核实，不安装或切换Java。
[[ $(readlink -f /usr/bin/java) == /usr/lib/jvm/jdk-17.0.15+6/bin/java ]]
java_version=$(/usr/bin/java -version 2>&1)
[[ "$java_version" =~ version[[:space:]]+\"17([.\"]|$) ]]
command -v python3 >/dev/null
command -v systemd-analyze >/dev/null
[[ -x /usr/sbin/nologin ]]
groupadd --system vector-search
useradd --system --gid vector-search --home-dir /var/lib/vector-search --no-create-home --shell /usr/sbin/nologin vector-search
install -d -o root -g root -m 0755 /opt/vector-search /opt/vector-search/releases
install -d -o root -g vector-search -m 0750 /var/lib/vector-search /etc/vector-search
install -d -o vector-search -g vector-search -m 0700 /var/lib/vector-search/db
install -d -o root -g root -m 0700 /var/backups/vector-search
python3 - <<'PY'
import grp
import os
import secrets

# 真实凭据仅在主机生成，不打印或返回。
settings = [
    'APP_DATA_DIR=/var/lib/vector-search/db', 'PORT=8080',
    'DB_PASSWORD=' + secrets.token_hex(32),
    'VECTOR_WORKER_COUNT=1', 'VECTOR_QUEUE_CAPACITY=64',
    'VECTOR_MAX_PENDING_TASKS=1000', 'VECTOR_MAX_DOCUMENTS=5000',
    'VECTOR_SEARCH_PERMITS=1', 'VECTOR_MAX_TEXT_CODE_POINTS=20000',
    'VECTOR_MAX_REQUEST_BYTES=262144', 'VECTOR_MAX_TOP_K=100',
    'VECTOR_MAX_PAGE_SIZE=100', 'VECTOR_CPU_ROUNDS=32',
    'VECTOR_SCAN_INTERVAL_MS=500', 'VECTOR_SCAN_BATCH_SIZE=32',
    'VECTOR_SHUTDOWN_WAIT_SECONDS=30',
    'SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=45s',
]
path = '/etc/vector-search/app.env'
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write('\n'.join(settings) + '\n')
os.chown(path, 0, grp.getgrnam('vector-search').gr_gid)
os.chmod(path, 0o640)
print('Created restricted app.env; contents withheld')
PY
# noclobber再次防止覆盖文件；不启用或启动服务。
(
    umask 022
    set -o noclobber
    cat > /etc/systemd/system/vector-search.service <<'UNIT'
[Unit]
Description=Vector search single-instance application
After=network.target
StartLimitIntervalSec=120
StartLimitBurst=3

[Service]
Type=simple
User=vector-search
Group=vector-search
WorkingDirectory=/var/lib/vector-search
EnvironmentFile=/etc/vector-search/app.env
ExecStart=/usr/lib/jvm/jdk-17.0.15+6/bin/java -Xms256m -Xmx1024m -jar /opt/vector-search/current/app.jar
Restart=on-failure
RestartSec=10
KillSignal=SIGTERM
# 临时外层等待预算，须按实际生命周期阶段校准。
TimeoutStopSec=120s
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=/var/lib/vector-search/db
StandardOutput=journal
StandardError=journal
SyslogIdentifier=vector-search

[Install]
WantedBy=multi-user.target
UNIT
)
systemd-analyze verify /etc/systemd/system/vector-search.service
systemctl daemon-reload
printf '\n[PROJECT RESOURCE METADATA]\n'
stat -c '%F %a %U:%G %n' /opt/vector-search /opt/vector-search/releases /var/lib/vector-search /var/lib/vector-search/db /etc/vector-search /etc/vector-search/app.env /var/backups/vector-search /etc/systemd/system/vector-search.service
getent passwd vector-search
printf '\n[SERVICE STATE]\n'
systemctl is-enabled vector-search.service || [[ $? == 1 ]]
systemctl is-active vector-search.service || [[ $? == 3 ]]
