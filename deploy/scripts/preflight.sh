#!/usr/bin/env bash
# 仅执行本地只读检查，不加载环境文件或打开H2。
set -euo pipefail
usage() {
  cat <<'EOF'
Usage: bash preflight.sh --java ABS_PATH --data-dir ABS_DIR --jar ABS_JAR --port PORT [--previous-pid PID]
Run as the intended service account. Requires existing directories/artifact.
Checks Java17, readability/access, port occupancy and an optional old PID.
No installation, directory creation, service control, network requests or data writes.
Success is a prerequisite check only; it does not prove business readiness or exclusive H2 ownership.
EOF
}
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
java_bin= data_dir= jar_file= port= previous_pid=
while (( $# )); do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --java|--data-dir|--jar|--port|--previous-pid)
      (( $# >= 2 )) || fail "Missing option value: $1"
      case "$1" in
        --java) java_bin=$2 ;; --data-dir) data_dir=$2 ;;
        --jar) jar_file=$2 ;; --port) port=$2 ;; --previous-pid) previous_pid=$2 ;;
      esac
      shift 2 ;;
    *) fail "Unknown argument; use --help" ;;
  esac
done
for value in "$java_bin" "$data_dir" "$jar_file"; do
  [[ "$value" == /* ]] || fail "Java, data directory and JAR must be absolute paths"
done
[[ "$port" =~ ^[0-9]{1,5}$ ]] || fail "Port must be an integer from 1 to 65535"
port=$((10#$port))
(( port >= 1 && port <= 65535 )) || fail "Port outside 1..65535"
[[ -x "$java_bin" && -f "$java_bin" ]] || fail "Java executable unavailable"
java_version=$("$java_bin" -version 2>&1) || fail "Java version command failed"
[[ "$java_version" =~ version[[:space:]]+\"17([.\"]|$) ]] || fail "Java major version must be 17"
printf 'PASS: Java17 executable\n'
[[ -d "$data_dir" && -r "$data_dir" && -w "$data_dir" && -x "$data_dir" ]] || fail "Existing data directory needs read/write/search access for this account"
[[ -f "$jar_file" && -r "$jar_file" && -s "$jar_file" ]] || fail "Readable nonempty JAR required"
printf 'PASS: existing data directory access and artifact readability (no write performed)\n'
if [[ -n "$previous_pid" ]]; then
  [[ "$previous_pid" =~ ^[1-9][0-9]{0,9}$ ]] || fail "Previous PID must be a positive process ID"
  # 进程检查被拒绝时不能误判为进程已经退出。
  process_ids=$(ps -e -o pid= 2>/dev/null) || fail "Cannot inspect process IDs; previous process exit remains unverified"
  if printf '%s\n' "$process_ids" | awk -v wanted="$previous_pid" '$1 == wanted { found=1 } END { exit !found }'; then
    fail "Previous PID still exists; verify process identity and wait for exit"
  fi
  printf 'PASS: supplied previous PID no longer exists\n'
fi
if command -v lsof >/dev/null 2>&1; then
  # 不打印进程参数或环境；检查可见性取决于运行账号。
  listener_status=0
  listener_output=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>&1) || listener_status=$?
  if (( listener_status == 0 )); then
    fail "Requested TCP port has a visible listener"
  fi
  # lsof退出1且无输出表示未选中文件；有诊断则视为UNKNOWN。
  if (( listener_status != 1 )) || [[ -n "$listener_output" ]]; then
    fail "UNKNOWN: lsof could not reliably inspect the requested TCP port"
  fi
elif command -v ss >/dev/null 2>&1; then
  listeners=$(ss -H -ltn "sport = :$port") || fail "Cannot inspect TCP listeners"
  [[ -z "$listeners" ]] || fail "Requested TCP port has a listener"
else
  fail "Need lsof or ss for a local listener check; this script will not install it"
fi
printf 'PASS: no visible TCP listener on requested port\n'
printf 'LIMIT: independently confirm no JVM owns this H2 path; an unused port cannot establish that.\n'
printf 'LIMIT: validate host permissions, resolved symlinks, disk, configuration, artifact SHA and readiness separately.\n'
