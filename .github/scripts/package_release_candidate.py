#!/usr/bin/env python3
"""仅封装当前已验证构建的候选，不上传服务器、不生成凭据，也不覆盖已有候选。"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from datetime import datetime, timezone
import zipfile


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description="生成固定源码与JAR校验和的发布候选")
    parser.add_argument("--expected-sha", required=True)
    options = parser.parse_args()
    root = Path(git("rev-parse", "--show-toplevel")).resolve()
    source_sha = git("rev-parse", "HEAD")
    if not re.fullmatch(r"[0-9a-f]{40}", options.expected_sha) or source_sha != options.expected_sha:
        raise SystemExit("源码HEAD与指定完整SHA不一致")
    if git("status", "--porcelain", "--untracked-files=no"):
        raise SystemExit("存在已跟踪文件改动，不能给候选标注为干净源码SHA")
    jar = root / "target/vector-search.jar"
    if not jar.is_file() or not jar.resolve().is_relative_to(root / "target"):
        raise SystemExit("缺少当前构建的JAR，或JAR路径越过构建目录")
    with zipfile.ZipFile(jar) as archive:
        required = {"BOOT-INF/classes/application.yml", "BOOT-INF/classes/schema.sql",
                    "BOOT-INF/classes/dev/twelvetimes/vector/VectorSearchApplication.class"}
        if not required.issubset(archive.namelist()) or archive.testzip() is not None:
            raise SystemExit("产物不是完整的本项目Spring Boot JAR")
    destination = root / "target/release-candidate" / source_sha
    if destination.exists() or not destination.resolve().is_relative_to(root / "target"):
        raise SystemExit("候选目录已存在或路径异常，拒绝覆盖")
    destination.mkdir(parents=True)
    artifact = destination / "app.jar"
    shutil.copyfile(jar, artifact)
    manifest = {
        "schemaVersion": 1, "sourceSha": source_sha, "jarFile": "app.jar",
        "jarSha256": sha256(artifact), "sizeBytes": artifact.stat().st_size,
        "javaTarget": 17, "embeddingVersion": "char12-sha256-r32-nfkc-root-ws-v1",
        "schemaSha256": sha256(root / "src/main/resources/schema.sql"),
        "createdAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "ciRunId": os.environ.get("GITHUB_RUN_ID"), "ciRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
    }
    manifest_path = destination / "release-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # 清单覆盖JAR及其来源声明；下载后需同时核对审批的SHA和JAR摘要，不能只看文件名。
    (destination / "SHA256SUMS").write_text(
        "".join(f"{sha256(path)}  {path.name}\n" for path in (artifact, manifest_path)), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
