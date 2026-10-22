#!/usr/bin/env bash
# 仅只读核对固定候选，不生成发布授权，不上传或启动应用。
set -euo pipefail
[[ $# == 5 ]] || { printf '用法：bash verify-release.sh 完整源码SHA 固定schema校验值 release-manifest.json app.jar SHA256SUMS\n' >&2; exit 2; }
python3 - "$@" <<'PY'
import hashlib
import datetime
import json
from pathlib import Path
import re
import sys

expected, expected_schema, manifest_name, jar_name, sums_name = sys.argv[1:]
if not re.fullmatch(r'[0-9a-f]{40}', expected):
    raise SystemExit('拒绝：必须提供已批准的完整40位源码SHA')
if not re.fullmatch(r'[0-9a-f]{64}', expected_schema):
    raise SystemExit('拒绝：必须提供冻结schema校验值')
for name in (manifest_name, jar_name, sums_name):
    path = Path(name)
    if any(part.is_symlink() for part in (path, *path.parents)) or not path.is_file() or '..' in path.parts:
        raise SystemExit('拒绝：候选文件不能为符号链接或含路径跳转')
if [Path(n).name for n in (manifest_name, jar_name, sums_name)] != ['release-manifest.json', 'app.jar', 'SHA256SUMS']:
    raise SystemExit('拒绝：候选必须使用扁平发布文件名')
if len({Path(n).absolute().parent for n in (manifest_name, jar_name, sums_name)}) != 1:
    raise SystemExit('拒绝：候选文件必须位于同一目录')
manifest = json.loads(Path(manifest_name).read_text())
if manifest.get('sourceSha') != expected:
    raise SystemExit('拒绝：候选源码SHA不匹配')
if not re.fullmatch(r'[0-9a-f]{64}', manifest.get('jarSha256', '')):
    raise SystemExit('拒绝：缺少有效JAR校验值')
if type(manifest.get('schemaVersion')) is not int or manifest['schemaVersion'] != 1:
    raise SystemExit('拒绝：manifest版本不符合契约')
if type(manifest.get('javaTarget')) is not int or manifest['javaTarget'] != 17 or manifest.get('jarFile') != 'app.jar':
    raise SystemExit('拒绝：Java或发布包名称不符合契约')
if manifest.get('embeddingVersion') != 'char12-sha256-r32-nfkc-root-ws-v1':
    raise SystemExit('拒绝：算法版本不匹配')
if manifest.get('schemaSha256') != expected_schema:
    raise SystemExit('拒绝：DDL校验不符合冻结schema')
created = manifest.get('createdAtUtc', '')
if not isinstance(created, str) or not created.endswith('Z'):
    raise SystemExit('拒绝：生成时间必须为UTC ISO-8601')
datetime.datetime.fromisoformat(created.replace('Z', '+00:00'))
jar = Path(jar_name)
if jar.is_symlink() or not jar.is_file() or jar.stat().st_size == 0:
    raise SystemExit('拒绝：JAR必须为非空普通文件，不能为符号链接')
digest = hashlib.sha256()
with jar.open('rb') as stream:
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(block)
if type(manifest.get('sizeBytes')) is not int or manifest['sizeBytes'] != jar.stat().st_size:
    raise SystemExit('拒绝：JAR大小不匹配')
if digest.hexdigest() != manifest['jarSha256']:
    raise SystemExit('拒绝：JAR SHA-256不匹配')
expected_sums = {'app.jar': digest.hexdigest(), 'release-manifest.json': hashlib.sha256(Path(manifest_name).read_bytes()).hexdigest()}
found = {}
for line in Path(sums_name).read_text().splitlines():
    match = re.fullmatch(r'([0-9a-f]{64}) [ *](app\.jar|release-manifest\.json)', line)
    if not match or match[2] in found:
        raise SystemExit('拒绝：SHA256SUMS含重复、路径或未知文件')
    found[match[2]] = match[1]
if found != expected_sums:
    raise SystemExit('拒绝：SHA256SUMS内容不匹配')
print('候选SHA与JAR校验一致；CI结果、UI和发布操作授权仍需人工核对')
PY
