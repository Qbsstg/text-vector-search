#!/usr/bin/env python3
"""仅封装当前已验证构建的候选，不上传服务器、不生成凭据，也不覆盖已有候选。"""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
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


def version_static_resources(source, target, source_sha):
    """给入口和完整模块图分配不可变发布路径，避免新HTML命中旧脚本的本机缓存。"""
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("资源版本必须为完整源码SHA")
    if target.exists():
        raise FileExistsError("拒绝覆盖既有封装产物")
    static_root = "BOOT-INF/classes/static/"
    asset_prefix = f"/assets/{source_sha}/"
    with zipfile.ZipFile(source) as original:
        resources = {name[len(static_root):]: original.read(name) for name in original.namelist()
                     if name.startswith(static_root) and not name.endswith("/")}
        if not {"index.html", "app.js", "styles.css", "favicon.svg"}.issubset(resources):
            raise ValueError("缺少必须的前端入口资源")
        for name in resources:
            if ".." in Path(name).parts or re.match(r"assets/[0-9a-f]{40}/", name):
                raise ValueError("原始构建含异常或已经封装的资源路径")
        html = resources["index.html"].decode("utf-8")
        for name in ("app.js", "styles.css", "favicon.svg"):
            pattern = r"(?P<start>\b(?:src|href)=[\"'])/" + re.escape(name) + r"(?P<end>[\"'])"
            html, changed = re.subn(pattern,
                                   lambda match: match.group("start") + asset_prefix + name + match.group("end"), html)
            if changed != 1:
                raise ValueError("入口资源引用必须恰好出现一次：" + name)
        with zipfile.ZipFile(target, "x") as sealed:
            for info in original.infolist():
                data = html.encode("utf-8") if info.filename == static_root + "index.html" else original.read(info)
                # 保留原ZipInfo的压缩方式；Spring Boot嵌套依赖必须继续为原来的STORED格式。
                sealed.writestr(copy.copy(info), data)
                if info.filename.startswith(static_root) and not info.is_dir():
                    relative = info.filename[len(static_root):]
                    if relative != "index.html":
                        versioned = copy.copy(info)
                        versioned.filename = static_root + asset_prefix.lstrip("/") + relative
                        sealed.writestr(versioned, resources[relative])
    return asset_prefix


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
    if git("ls-files", "--others", "--exclude-standard", "--", "src", ".mvn"):
        raise SystemExit("存在未跟踪构建输入，不能把其产物归入指定源码SHA")
    jar = root / "target/vector-search.jar"
    if not jar.is_file() or not jar.resolve().is_relative_to(root / "target"):
        raise SystemExit("缺少当前构建的JAR，或JAR路径越过构建目录")
    with zipfile.ZipFile(jar) as archive:
        required = {"BOOT-INF/classes/application.yml", "BOOT-INF/classes/schema.sql",
                    "BOOT-INF/classes/dev/twelvetimes/vector/VectorSearchApplication.class"}
        if not required.issubset(archive.namelist()) or archive.testzip() is not None:
            raise SystemExit("产物不是完整的本项目Spring Boot JAR")
        application_class = archive.read("BOOT-INF/classes/dev/twelvetimes/vector/VectorSearchApplication.class")
        if application_class[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(application_class[6:8], "big") != 61:
            raise SystemExit("应用主类不是Java17目标字节码")
        if hashlib.sha256(archive.read("BOOT-INF/classes/schema.sql")).hexdigest() != sha256(root / "src/main/resources/schema.sql"):
            raise SystemExit("JAR内DDL与当前固定源码不一致")
    destination = root / "target/release-candidate" / source_sha
    if destination.exists() or not destination.resolve().is_relative_to(root / "target"):
        raise SystemExit("候选目录已存在或路径异常，拒绝覆盖")
    destination.mkdir(parents=True)
    artifact = destination / "app.jar"
    asset_prefix = version_static_resources(jar, artifact, source_sha)
    manifest = {
        "schemaVersion": 1, "sourceSha": source_sha, "jarFile": "app.jar",
        "jarSha256": sha256(artifact), "sizeBytes": artifact.stat().st_size,
        "javaTarget": 17, "embeddingVersion": "char12-sha256-r32-nfkc-root-ws-v1",
        "schemaSha256": sha256(root / "src/main/resources/schema.sql"),
        "createdAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "ciRunId": os.environ.get("GITHUB_RUN_ID"), "ciRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "staticAssetPrefix": asset_prefix,
    }
    manifest_path = destination / "release-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    # 清单覆盖JAR及其来源声明；下载后需同时核对审批的SHA和JAR摘要，不能只看文件名。
    (destination / "SHA256SUMS").write_text(
        "".join(f"{sha256(path)}  {path.name}\n" for path in (artifact, manifest_path)), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
