"""发布封装的回归测试：版本化整个模块图，不靠用户强刷或关闭浏览器缓存。"""

import importlib.util
from pathlib import Path
import re
import tempfile
import unittest
from urllib.parse import urljoin
import zipfile

spec = importlib.util.spec_from_file_location("release_packager", Path(__file__).with_name("package_release_candidate.py"))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class VersionedStaticResourcesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / "source.jar"
        self.static = "BOOT-INF/classes/static/"
        self.resources = {
            "index.html": b'<link rel="icon" href="/favicon.svg"><link rel="stylesheet" href="/styles.css"><script src="/app.js" type="module"></script>',
            "favicon.svg": b"unchanged-icon",
            "styles.css": b"body { color: black; }",
            "app.js": b'import { value } from "./api.mjs"; import "./tasks.mjs";',
            "api.mjs": b'import "./vector-info.mjs"; export const value = 1;',
            "tasks.mjs": b'import { value } from "./api.mjs";',
            "vector-info.mjs": b"export const dimensions = 256;",
        }
        with zipfile.ZipFile(self.source, "x") as archive:
            for name, data in self.resources.items():
                archive.writestr(self.static + name, data)
            archive.writestr("BOOT-INF/classes/schema.sql", b"unchanged-schema")
            archive.writestr("BOOT-INF/classes/algorithm.class", b"unchanged-algorithm")
            archive.writestr("BOOT-INF/lib/dependency.jar", b"unchanged-nested-library", compress_type=zipfile.ZIP_STORED)

    def test_入口及所有相对模块引用属于同一源码版本(self):
        sha = "a" * 40
        output = self.root / "sealed.jar"
        prefix = packager.version_static_resources(self.source, output, sha)
        self.assertEqual(f"/assets/{sha}/", prefix)
        with zipfile.ZipFile(output) as archive:
            html = archive.read(self.static + "index.html").decode()
            for name in ("favicon.svg", "styles.css", "app.js"):
                self.assertIn(prefix + name, html)
                self.assertNotRegex(html, r'["\']/' + re.escape(name) + r'["\']')
            for name, data in self.resources.items():
                if name == "index.html":
                    continue
                self.assertEqual(data, archive.read(self.static + prefix.lstrip("/") + name))
                for dependency in re.findall(r'["\'](\./[^"\']+\.mjs)["\']', data.decode()):
                    resolved = urljoin(prefix + name, dependency)
                    self.assertTrue(resolved.startswith(prefix))
                    self.assertIn(self.static + resolved.lstrip("/"), archive.namelist())

    def test_业务字节与嵌套依赖存储方式保持且不覆盖原产物(self):
        before = self.source.read_bytes()
        output = self.root / "sealed.jar"
        packager.version_static_resources(self.source, output, "a" * 40)
        with zipfile.ZipFile(self.source) as original, zipfile.ZipFile(output) as sealed:
            for info in original.infolist():
                if info.filename != self.static + "index.html":
                    self.assertEqual(original.read(info), sealed.read(info.filename))
                    self.assertEqual(info.compress_type, sealed.getinfo(info.filename).compress_type)
            self.assertEqual(len(sealed.namelist()), len(set(sealed.namelist())))
            self.assertIsNone(sealed.testzip())
        self.assertEqual(before, self.source.read_bytes())
        preserved = output.read_bytes()
        with self.assertRaises(FileExistsError):
            packager.version_static_resources(self.source, output, "b" * 40)
        self.assertEqual(preserved, output.read_bytes())

    def test_两次发布使用互不混用的完整模块路径(self):
        for sha in ("a" * 40, "b" * 40):
            output = self.root / (sha + ".jar")
            packager.version_static_resources(self.source, output, sha)
            with zipfile.ZipFile(output) as archive:
                html = archive.read(self.static + "index.html").decode()
                self.assertIn("/assets/" + sha + "/app.js", html)
                other = "b" if sha.startswith("a") else "a"
                self.assertNotIn("/assets/" + other * 40 + "/", html)

    def test_不完整入口及非法源码版本拒绝封装(self):
        with self.assertRaises(ValueError):
            packager.version_static_resources(self.source, self.root / "invalid.jar", "not-a-sha")
        broken = self.root / "broken.jar"
        with zipfile.ZipFile(broken, "x") as archive:
            archive.writestr(self.static + "index.html", b"missing-entrypoints")
        with self.assertRaises(ValueError):
            packager.version_static_resources(broken, self.root / "broken-sealed.jar", "a" * 40)

    def test_实际前端的全部本地模块依赖仍落入同一版本目录(self):
        folder = Path(__file__).resolve().parents[2] / "src/main/resources/static"
        actual = {str(path.relative_to(folder)): path.read_bytes() for path in folder.rglob("*") if path.is_file()}
        source = self.root / "actual-source.jar"
        output = self.root / "actual-sealed.jar"
        with zipfile.ZipFile(source, "x") as archive:
            for name, data in actual.items():
                archive.writestr(self.static + name, data)
        prefix = packager.version_static_resources(source, output, "c" * 40)
        with zipfile.ZipFile(output) as sealed:
            for name, data in actual.items():
                if not name.endswith((".js", ".mjs")):
                    continue
                for dependency in re.findall(r'from\s+["\']([^"\']+)["\']', data.decode("utf-8")):
                    self.assertTrue(dependency.startswith("./"), (name, dependency))
                    resolved = urljoin(prefix + name, dependency)
                    self.assertTrue(resolved.startswith(prefix))
                    self.assertEqual(actual[dependency[2:]], sealed.read(self.static + resolved.lstrip("/")))


if __name__ == "__main__":
    unittest.main()
