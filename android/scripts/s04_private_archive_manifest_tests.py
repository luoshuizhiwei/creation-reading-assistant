#!/usr/bin/env python3
"""s04_private_archive_manifest.py 的定向测试（纯离线，全部使用小型合成 tar）。

运行（在仓库根或任意目录均可，测试与 CWD 无关）：

    python android/scripts/s04_private_archive_manifest_tests.py

或在 android/scripts/ 下：

    python s04_private_archive_manifest_tests.py

覆盖面：

  manifest 模式 —— 正常生成、逐文件哈希已知答案、期望 SHA-256/字节数强制、
  可重复性（两次运行内容一致）、绝对路径 / .. / 重复成员 / 符号链接 / 硬链接 /
  非法根 / 反斜杠 / 盘符 / 冒号 / 非 UTF-8 / 控制字符 / 根级普通文件 / 截断归档
  全部 BLOCKED、输出位置禁止落在 git 仓库内、WorkManager workdb 位置核对
  （no_backup/ 通过；databases/ —— 旧审查文档写法 —— 被拒绝）。

  compare 模式 —— 基线原始字节哈希锚（正确哈希可比较；缺参、格式错误、错误哈希、
  "JSON 仍合法但内容被改动" 均 BLOCKED 且不执行 PASS 判定、不泄露私有文件名）、
  自比较 PASS、同大小内容改动、新增/删除、WAL 改动、prefs 改动、no_backup 根缺失、
  被篡改基线（哈希锚与深层结构校验两条防线）、不安全新归档、期望哈希不符，以及
  隐私约束：任何差异场景下 stdout/stderr 都不得包含私有文件名。

  manifest 模式追加 —— 输出路径已存在时默认拒绝，且既有文件逐字节不变、无临时文件残留；
  以及确定性合成竞态：目标在扫描期间被另一进程创建时，最终落盘绝不覆盖，命令 BLOCKED、
  原文件逐字节不变、无临时文件残留（共 24 例）。
"""

from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import json
import subprocess
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPT_PATH = Path(__file__).with_name("s04_private_archive_manifest.py")

_spec = importlib.util.spec_from_file_location("s04_pam_under_test", SCRIPT_PATH)
MOD = importlib.util.module_from_spec(_spec)
sys.modules["s04_pam_under_test"] = MOD
_spec.loader.exec_module(MOD)

HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

# 合成内容（无任何真实数据）。
FILE_APP_DB = (b"synthetic-row-01\x00" * 8)
FILE_WAL = b"synthetic-wal-page-0001"
FILE_PREFS = b"<map><boolean name='flag' value='true'/></map>"
FILE_WORKDB = b"synthetic-workdb-page-0001"


def make_tar(path: Path, entries) -> None:
    """entries: list[('dir', name) | ('file', name, data) | ('sym', name, linkname) | ('lnk', name, linkname)]"""
    with tarfile.open(path, "w", format=tarfile.PAX_FORMAT) as tf:
        for entry in entries:
            kind = entry[0]
            ti = tarfile.TarInfo(entry[1])
            ti.mtime = 0
            ti.uid = 0
            ti.gid = 0
            ti.uname = ""
            ti.gname = ""
            if kind == "dir":
                ti.type = tarfile.DIRTYPE
                ti.mode = 0o755
                tf.addfile(ti)
            elif kind == "file":
                data = entry[2]
                ti.type = tarfile.REGTYPE
                ti.mode = 0o644
                ti.size = len(data)
                tf.addfile(ti, __import__("io").BytesIO(data))
            elif kind == "sym":
                ti.type = tarfile.SYMTYPE
                ti.linkname = entry[2]
                tf.addfile(ti)
            elif kind == "lnk":
                ti.type = tarfile.LNKTYPE
                ti.linkname = entry[2]
                tf.addfile(ti)
            else:
                raise AssertionError(f"unknown entry kind: {kind}")


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_cli(args):
    return subprocess.run(
        [sys.executable, str(SCRIPT_PATH), *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def parse_kv(stdout: str) -> dict:
    out = {}
    for line in stdout.splitlines():
        if ": " in line:
            key, value = line.split(": ", 1)
            out[key] = value
    return out


def std_output(proc) -> str:
    return (proc.stdout or "") + (proc.stderr or "")


STANDARD_ENTRIES = [
    ("dir", "databases"),
    ("dir", "databases/inner"),
    ("file", "databases/inner/app.db", FILE_APP_DB),
    ("file", "databases/inner/app.db-wal", FILE_WAL),
    ("dir", "files"),
    ("dir", "files/books/epub"),
    ("file", "files/books/epub/testbook-1/book.epub", b"hello"),
    ("dir", "shared_prefs"),
    ("file", "shared_prefs/app_prefs.xml", FILE_PREFS),
    ("dir", "no_backup"),
    ("file", "no_backup/androidx.work.workdb", FILE_WORKDB),
    ("file", "no_backup/androidx.work.workdb-wal", FILE_WAL),
    ("file", "no_backup/androidx.work.workdb-shm", b"synthetic-shm-0001"),
]


class ManifestModeTests(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory(prefix="s04-pam-test-")
        self.tmp = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def test_manifest_happy_path_and_known_answer_hash(self):
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        out_path = self.tmp / "baseline.json"
        proc = run_cli(["manifest", "--archive", str(tar_path), "--output", str(out_path)])
        self.assertEqual(proc.returncode, 0, std_output(proc))
        kv = parse_kv(proc.stdout)
        self.assertEqual(kv["RESULT"], "OK")
        self.assertEqual(int(kv["REGULAR_FILES"]), 7)
        self.assertEqual(int(kv["TAR_MEMBERS"]), 13)
        self.assertEqual(int(kv["DIRECTORIES"]), 6)
        self.assertEqual(
            int(kv["FILE_BYTES_TOTAL"]),
            sum(len(e[2]) for e in STANDARD_ENTRIES if e[0] == "file"),
        )
        self.assertEqual(int(kv["FILES_BY_ROOT_DATABASES"]), 2)
        self.assertEqual(int(kv["FILES_BY_ROOT_FILES"]), 1)
        self.assertEqual(int(kv["FILES_BY_ROOT_SHARED_PREFS"]), 1)
        self.assertEqual(int(kv["FILES_BY_ROOT_NO_BACKUP"]), 3)
        # 已知答案：b"hello" 的 SHA-256，锚定逐文件哈希路径。
        manifest = json.loads(out_path.read_text(encoding="utf-8"))
        by_path = {f["path"]: f for f in manifest["files"]}
        self.assertEqual(by_path["files/books/epub/testbook-1/book.epub"]["sha256"], HELLO_SHA256)
        self.assertEqual(by_path["files/books/epub/testbook-1/book.epub"]["size_bytes"], 5)
        # 归档级哈希与字节数。
        self.assertEqual(manifest["archive"]["sha256"], sha256_file(tar_path))
        self.assertEqual(manifest["archive"]["size_bytes"], tar_path.stat().st_size)
        self.assertEqual(kv["MANIFEST_SHA256"], hashlib.sha256(out_path.read_bytes()).hexdigest())
        # 文件清单按路径排序（可重复性基础）。
        paths = [f["path"] for f in manifest["files"]]
        self.assertEqual(paths, sorted(paths))

    def test_manifest_expectations_enforced(self):
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        out_path = self.tmp / "baseline.json"
        good_sha = sha256_file(tar_path)
        good_size = tar_path.stat().st_size

        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(out_path),
            "--expect-sha256", good_sha.upper(), "--expect-size", str(good_size),
        ])
        self.assertEqual(proc.returncode, 0, std_output(proc))
        self.assertTrue(out_path.exists())

        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(self.tmp / "b2.json"),
            "--expect-sha256", "0" * 64, "--expect-size", str(good_size),
        ])
        self.assertEqual(proc.returncode, 2)
        kv = parse_kv(proc.stdout)
        self.assertEqual(kv["REASON"], "ARCHIVE_SHA256_MISMATCH")
        self.assertEqual(kv["EXPECTED_SHA256"], "0" * 64)
        self.assertEqual(kv["ACTUAL_SHA256"], good_sha)
        self.assertFalse((self.tmp / "b2.json").exists())

        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(self.tmp / "b3.json"),
            "--expect-size", str(good_size + 1),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "ARCHIVE_SIZE_MISMATCH")
        self.assertFalse((self.tmp / "b3.json").exists())

    def test_manifest_repeatability_across_runs(self):
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        first = self.tmp / "r1.json"
        second = self.tmp / "r2.json"
        for out in (first, second):
            proc = run_cli(["manifest", "--archive", str(tar_path), "--output", str(out)])
            self.assertEqual(proc.returncode, 0, std_output(proc))
        a = json.loads(first.read_text(encoding="utf-8"))
        b = json.loads(second.read_text(encoding="utf-8"))
        for key in ("schema", "archive", "counts", "files", "directories"):
            self.assertEqual(a[key], b[key], f"non-repeatable field: {key}")

    def test_manifest_rejects_unsafe_members_without_leaking_names(self):
        cases = [
            ("absolute", [("file", "/etc/evil.txt", b"x")], "UNSAFE_ABSOLUTE_PATH"),
            ("dotdot", [("file", "databases/../evil.txt", b"x")], "UNSAFE_DOTDOT_PATH"),
            (
                "duplicate",
                [("file", "databases/a.db", b"one"), ("file", "databases/a.db", b"two")],
                "UNSAFE_DUPLICATE_MEMBER",
            ),
            ("symlink", [("sym", "files/evil-link", "databases/secret.db")], "UNSAFE_SYMLINK_MEMBER"),
            ("hardlink", [("lnk", "files/evil-link", "databases/secret.db")], "UNSAFE_HARDLINK_MEMBER"),
            ("bad_root", [("file", "cache/evil.jpg", b"x")], "UNSAFE_DISALLOWED_ROOT"),
            ("backslash", [("file", "databases\\evil.txt", b"x")], "UNSAFE_BACKSLASH_PATH"),
            ("drive", [("file", "C:/evil.txt", b"x")], "UNSAFE_DRIVE_PATH"),
            ("colon", [("file", "databases/evil:stream", b"x")], "UNSAFE_COLON_IN_NAME"),
            ("root_file", [("file", "databases", b"x")], "UNSAFE_FILE_AT_ROOT_LEVEL"),
            ("control_char", [("file", "databases/ev\til.txt", b"x")], "UNSAFE_UNSAFE_CHARS"),
            (
                "non_utf8",
                [("file", "databases/ev\udcffil.txt", b"x")],
                "UNSAFE_NON_UTF8_NAME",
            ),
        ]
        for label, entries, expected_token in cases:
            with self.subTest(case=label):
                tar_path = self.tmp / f"unsafe-{label}.tar"
                out_path = self.tmp / f"unsafe-{label}.json"
                make_tar(tar_path, entries)
                proc = run_cli(["manifest", "--archive", str(tar_path), "--output", str(out_path)])
                self.assertEqual(proc.returncode, 2, std_output(proc))
                self.assertIn("RESULT: BLOCKED", proc.stdout)
                self.assertIn(expected_token, proc.stdout)
                # 私有（此处为合成）成员名不得出现在任何输出里。
                for entry in entries:
                    self.assertNotIn(entry[1], std_output(proc))
                self.assertFalse(out_path.exists())

    def test_manifest_rejects_truncated_tar(self):
        full = self.tmp / "full.tar"
        make_tar(full, STANDARD_ENTRIES)
        # 在成员边界处截断（恰好少掉尾部，tarfile 流模式会把这种截断静默当正常结束）。
        boundary_cut = self.tmp / "truncated-boundary.tar"
        boundary_cut.write_bytes(full.read_bytes()[:1024])
        proc = run_cli([
            "manifest", "--archive", str(boundary_cut), "--output", str(self.tmp / "t1.json"),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertIn("RESULT: BLOCKED", proc.stdout)
        # 在成员数据中部截断。
        mid_cut = self.tmp / "truncated-mid.tar"
        mid_cut.write_bytes(full.read_bytes()[:6000])
        proc = run_cli([
            "manifest", "--archive", str(mid_cut), "--output", str(self.tmp / "t2.json"),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertIn("RESULT: BLOCKED", proc.stdout)

    def test_manifest_rejects_trailing_garbage_after_end_marker(self):
        tar_path = self.tmp / "garbage.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        with_garbage = self.tmp / "garbage-appended.tar"
        with_garbage.write_bytes(tar_path.read_bytes() + b"JUNKJUNK")
        proc = run_cli([
            "manifest", "--archive", str(with_garbage), "--output", str(self.tmp / "g.json"),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertIn("RESULT: BLOCKED", proc.stdout)
        self.assertFalse((self.tmp / "g.json").exists())

    def test_manifest_refuses_output_inside_git_repo(self):
        repo_dir = self.tmp / "fake-repo"
        (repo_dir / ".git").mkdir(parents=True)
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(repo_dir / "baseline.json"),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "MANIFEST_INSIDE_GIT_REPO")

    def test_manifest_workdb_check_accepts_no_backup(self):
        tar_path = self.tmp / "workdb-ok.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(self.tmp / "w1.json"),
            "--expect-workdb-in-no-backup",
        ])
        self.assertEqual(proc.returncode, 0, std_output(proc))
        self.assertIn("WORKDB_CHECK: ok (main=1 wal=1 shm=1)", proc.stdout)
        manifest = json.loads((self.tmp / "w1.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["workdb_check"]["violations"], [])

    def test_manifest_workdb_check_rejects_databases_location(self):
        # 旧审查文档把 workdb 写在 databases/ —— 该位置必须被显式拒绝。
        tar_path = self.tmp / "workdb-wrong.tar"
        make_tar(tar_path, [
            ("dir", "databases"),
            ("file", "databases/androidx.work.workdb", FILE_WORKDB),
            ("file", "databases/androidx.work.workdb-wal", FILE_WAL),
            ("file", "databases/androidx.work.workdb-shm", b"shm"),
        ])
        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(self.tmp / "w2.json"),
            "--expect-workdb-in-no-backup",
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "WORKDB_LOCATION_VIOLATION")
        self.assertIn("WORKDB_OUTSIDE_NO_BACKUP", proc.stdout)
        self.assertFalse((self.tmp / "w2.json").exists())

    def test_manifest_workdb_check_blocks_missing_workdb(self):
        tar_path = self.tmp / "workdb-absent.tar"
        make_tar(tar_path, [
            ("dir", "databases"),
            ("file", "databases/other.db", b"x"),
        ])
        proc = run_cli([
            "manifest", "--archive", str(tar_path), "--output", str(self.tmp / "w3.json"),
            "--expect-workdb-in-no-backup",
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertIn("WORKDB_NOT_FOUND", proc.stdout)


    def test_manifest_refuses_existing_output(self):
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        out_path = self.tmp / "existing.json"
        first = run_cli(["manifest", "--archive", str(tar_path), "--output", str(out_path)])
        self.assertEqual(first.returncode, 0, std_output(first))
        before = hashlib.sha256(out_path.read_bytes()).hexdigest()

        second = run_cli(["manifest", "--archive", str(tar_path), "--output", str(out_path)])
        self.assertEqual(second.returncode, 2)
        kv = parse_kv(second.stdout)
        self.assertEqual(kv["REASON"], "MANIFEST_OUTPUT_EXISTS")
        self.assertNotIn("RESULT: OK", second.stdout)
        # 既有基线必须逐字节不变，且无临时文件残留。
        self.assertEqual(hashlib.sha256(out_path.read_bytes()).hexdigest(), before)
        self.assertFalse(out_path.with_name(out_path.name + ".tmp").exists())

    def test_manifest_blocks_when_output_appears_during_scan(self):
        """确定性合成竞态：目标在扫描期间出现时，原文件字节不变、命令 BLOCKED。

        精确复现 TOCTOU 窗口：目标在扫描前的 exists() 检查时不存在，但在扫描完成、
        落盘之前被"另一进程"创建。通过包装模块的 scan_archive，在扫描返回前注入同名
        目标文件，从而不依赖真实并发与时序，确定性地命中该窗口。
        """
        tar_path = self.tmp / "synthetic.tar"
        make_tar(tar_path, STANDARD_ENTRIES)
        out_path = self.tmp / "race-baseline.json"
        # "另一进程"在扫描期间写入的同名基线，内容与本次将生成的清单完全不同。
        foreign_bytes = (
            b'{"schema":"foreign-baseline-created-during-scan","marker":"do-not-overwrite"}\n'
        )
        self.assertFalse(out_path.exists())
        real_scan = MOD.scan_archive

        def scan_then_create_foreign_target(archive, expect_sha256=None, expect_size=None):
            result = real_scan(archive, expect_sha256=expect_sha256, expect_size=expect_size)
            # 扫描已发生；模拟另一进程此刻创建同名基线（早于最终落盘）。
            out_path.write_bytes(foreign_bytes)
            return result

        buf = io.StringIO()
        with mock.patch.object(MOD, "scan_archive", scan_then_create_foreign_target):
            with contextlib.redirect_stdout(buf):
                rc = MOD.main(
                    ["manifest", "--archive", str(tar_path), "--output", str(out_path)]
                )
        output = buf.getvalue()
        self.assertEqual(rc, 2, output)
        self.assertIn("RESULT: BLOCKED", output)
        self.assertEqual(parse_kv(output)["REASON"], "MANIFEST_OUTPUT_EXISTS")
        self.assertNotIn("RESULT: OK", output)
        # 竞态期间出现的原文件必须逐字节不变；不得残留临时文件；不得泄露其内容。
        self.assertEqual(out_path.read_bytes(), foreign_bytes)
        self.assertFalse(out_path.with_name(out_path.name + ".tmp").exists())
        self.assertNotIn("foreign-baseline-created-during-scan", output)


class CompareModeTests(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory(prefix="s04-pam-test-")
        self.tmp = Path(self._tmp.name)
        self.baseline_tar = self.tmp / "baseline.tar"
        make_tar(self.baseline_tar, STANDARD_ENTRIES)
        self.baseline_json = self.tmp / "baseline.json"
        proc = run_cli(["manifest", "--archive", str(self.baseline_tar), "--output", str(self.baseline_json)])
        self.assertEqual(proc.returncode, 0, std_output(proc))
        self.baseline_sha256 = sha256_file(self.baseline_json)

    def tearDown(self):
        self._tmp.cleanup()

    def _rebuild(self, name: str, entries):
        tar_path = self.tmp / name
        make_tar(tar_path, entries)
        return tar_path

    def _compare(self, tar_path, **extra):
        args = [
            "compare", "--archive", str(tar_path), "--baseline", str(self.baseline_json),
            "--expect-baseline-sha256", self.baseline_sha256,
        ]
        args.extend(extra.pop("args", []))
        return run_cli(args)

    def test_compare_identical_archive_passes(self):
        same_tar = self.tmp / "identical.tar"
        make_tar(same_tar, STANDARD_ENTRIES)
        proc = self._compare(same_tar)
        self.assertEqual(proc.returncode, 0, std_output(proc))
        kv = parse_kv(proc.stdout)
        self.assertEqual(kv["RESULT"], "PASS")
        self.assertEqual(int(kv["TOTAL_DIFFERENCES"]), 0)
        self.assertEqual(kv["BASELINE_ARCHIVE_SHA256"], sha256_file(self.baseline_tar))

    def test_compare_detects_same_size_content_change_without_leaking_names(self):
        changed = [
            e if e[1] != "databases/inner/app.db" else ("file", e[1], b"synthetic-row-02\x00" * 8)
            for e in STANDARD_ENTRIES
        ]
        tar_path = self._rebuild("same-size-change.tar", changed)
        proc = self._compare(tar_path)
        self.assertEqual(proc.returncode, 1, std_output(proc))
        kv = parse_kv(proc.stdout)
        self.assertEqual(kv["RESULT"], "DIFF")
        self.assertEqual(int(kv["TOTAL_DIFFERENCES"]), 1)
        self.assertEqual(int(kv["CONTENT_CHANGED_SAME_SIZE"]), 1)
        self.assertEqual(int(kv["CONTENT_CHANGED_SIZE_CHANGED"]), 0)
        self.assertEqual(int(kv["AREA_DATABASES"]), 1)
        # 隐私约束：差异文件名不得出现在任何输出。
        self.assertNotIn("app.db", std_output(proc))

    def test_compare_detects_added_and_removed_files(self):
        added_tar = self._rebuild("added.tar", STANDARD_ENTRIES + [
            ("file", "files/books/epub/testbook-2/new-book.epub", b"new-bytes"),
        ])
        proc = self._compare(added_tar)
        self.assertEqual(proc.returncode, 1)
        kv = parse_kv(proc.stdout)
        self.assertEqual(int(kv["FILE_ADDED"]), 1)
        self.assertEqual(int(kv["TOTAL_DIFFERENCES"]), 1)
        self.assertNotIn("new-book.epub", std_output(proc))

        removed_tar = self._rebuild("removed.tar", [
            e for e in STANDARD_ENTRIES if e[1] != "files/books/epub/testbook-1/book.epub"
        ])
        proc = self._compare(removed_tar)
        self.assertEqual(proc.returncode, 1)
        kv = parse_kv(proc.stdout)
        self.assertEqual(int(kv["FILE_REMOVED"]), 1)
        self.assertEqual(int(kv["AREA_FILES"]), 1)
        self.assertNotIn("testbook-1", std_output(proc))

    def test_compare_detects_wal_change(self):
        changed = [
            e if e[1] != "databases/inner/app.db-wal" else ("file", e[1], b"totally-different-wal-bytes")
            for e in STANDARD_ENTRIES
        ]
        tar_path = self._rebuild("wal-change.tar", changed)
        proc = self._compare(tar_path)
        self.assertEqual(proc.returncode, 1)
        kv = parse_kv(proc.stdout)
        self.assertEqual(int(kv["AREA_WAL_SHM"]), 1)
        self.assertEqual(int(kv["CONTENT_CHANGED_SIZE_CHANGED"]), 1)
        self.assertNotIn("app.db-wal", std_output(proc))

    def test_compare_detects_prefs_change(self):
        # 同大小内容改动：属性名 flag→glag，长度不变，仅字节不同。
        changed = [
            e if e[1] != "shared_prefs/app_prefs.xml"
            else ("file", e[1], b"<map><boolean name='glag' value='true'/></map>")
            for e in STANDARD_ENTRIES
        ]
        tar_path = self._rebuild("prefs-change.tar", changed)
        proc = self._compare(tar_path)
        self.assertEqual(proc.returncode, 1)
        kv = parse_kv(proc.stdout)
        self.assertEqual(int(kv["AREA_SHARED_PREFS"]), 1)
        self.assertEqual(int(kv["CONTENT_CHANGED_SAME_SIZE"]), 1)
        self.assertNotIn("app_prefs", std_output(proc))

    def test_compare_detects_no_backup_root_missing(self):
        without_no_backup = [
            e for e in STANDARD_ENTRIES
            if not e[1].startswith("no_backup")
        ]
        tar_path = self._rebuild("no-no_backup.tar", without_no_backup)
        proc = self._compare(tar_path)
        self.assertEqual(proc.returncode, 1)
        kv = parse_kv(proc.stdout)
        self.assertEqual(int(kv["ROOT_MISSING"]), 1)
        self.assertEqual(kv["ROOT_MISSING_ROOTS"], "no_backup")
        self.assertEqual(int(kv["AREA_NO_BACKUP"]), 3)
        self.assertEqual(int(kv["FILE_REMOVED"]), 3)
        self.assertEqual(int(kv["TOTAL_DIFFERENCES"]), 4)

    def test_compare_requires_baseline_hash_anchor(self):
        tar_path = self._rebuild("anchor.tar", STANDARD_ENTRIES)
        base_args = ["compare", "--archive", str(tar_path), "--baseline", str(self.baseline_json)]

        # 缺参：必须 BLOCKED，不执行 PASS 判定。
        proc = run_cli(base_args)
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "EXPECTED_BASELINE_SHA256_MISSING")
        self.assertNotIn("RESULT: PASS", proc.stdout)

        # 格式错误：BLOCKED。
        proc = run_cli([*base_args, "--expect-baseline-sha256", "xyz-not-a-hash"])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "EXPECTED_BASELINE_SHA256_INVALID_FORMAT")

        # 错误哈希：BLOCKED，只输出预期/实际哈希，不泄露基线内任何路径或内容。
        proc = run_cli([*base_args, "--expect-baseline-sha256", "0" * 64])
        self.assertEqual(proc.returncode, 2)
        kv = parse_kv(proc.stdout)
        self.assertEqual(kv["REASON"], "EXPECTED_BASELINE_SHA256_MISMATCH")
        self.assertEqual(kv["EXPECTED_BASELINE_SHA256"], "0" * 64)
        self.assertEqual(kv["ACTUAL_BASELINE_SHA256"], self.baseline_sha256)
        self.assertNotIn("RESULT: PASS", proc.stdout)
        for private_marker in ("app.db", "app_prefs", "testbook", "workdb"):
            self.assertNotIn(private_marker, std_output(proc))

    def test_compare_rejects_tampered_baseline(self):
        tar_path = self._rebuild("tampered.tar", STANDARD_ENTRIES)

        # 内容被改动但 JSON 仍合法：旧哈希锚必须拒绝，且不得泄露基线内容。
        tampered = self.tmp / "tampered-content.json"
        payload = json.loads(self.baseline_json.read_text(encoding="utf-8"))
        payload["files"][0]["sha256"] = "f" * 64
        tampered.write_text(json.dumps(payload), encoding="utf-8")
        proc = run_cli([
            "compare", "--archive", str(tar_path), "--baseline", str(tampered),
            "--expect-baseline-sha256", self.baseline_sha256,
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "EXPECTED_BASELINE_SHA256_MISMATCH")
        self.assertNotIn("RESULT: PASS", proc.stdout)
        for private_marker in ("app.db", "app_prefs", "testbook", "workdb"):
            self.assertNotIn(private_marker, std_output(proc))

        # 深层结构校验仍然有效：用被篡改文件自身的正确哈希 → 结构非法被拒。
        bad_schema = self.tmp / "bad-schema.json"
        bad_schema.write_text(json.dumps({"schema": "something-else", "files": []}), encoding="utf-8")
        proc = run_cli([
            "compare", "--archive", str(tar_path), "--baseline", str(bad_schema),
            "--expect-baseline-sha256", sha256_file(bad_schema),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "BASELINE_SCHEMA_MISMATCH")

        entry_missing_hash = self.tmp / "entry-bad.json"
        entry_payload = json.loads(self.baseline_json.read_text(encoding="utf-8"))
        entry_payload["files"][0].pop("sha256")
        entry_missing_hash.write_text(json.dumps(entry_payload), encoding="utf-8")
        proc = run_cli([
            "compare", "--archive", str(tar_path), "--baseline", str(entry_missing_hash),
            "--expect-baseline-sha256", sha256_file(entry_missing_hash),
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "BASELINE_FILE_ENTRY_INVALID")

    def test_compare_rejects_unsafe_new_archive(self):
        tar_path = self._rebuild("unsafe-new.tar", STANDARD_ENTRIES + [
            ("file", "../escape.txt", b"x"),
        ])
        proc = self._compare(tar_path)
        self.assertEqual(proc.returncode, 2)
        self.assertIn("UNSAFE_DOTDOT_PATH", proc.stdout)

    def test_compare_expectation_mismatch_blocks_before_diff(self):
        tar_path = self._rebuild("expect.tar", STANDARD_ENTRIES)
        proc = self._compare(tar_path, args=["--expect-sha256", "0" * 64])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "ARCHIVE_SHA256_MISMATCH")

    def test_compare_writes_private_evidence_json_on_diff(self):
        # 同大小内容改动：属性名 flag→glag，长度不变，仅字节不同。
        changed = [
            e if e[1] != "shared_prefs/app_prefs.xml"
            else ("file", e[1], b"<map><boolean name='glag' value='true'/></map>")
            for e in STANDARD_ENTRIES
        ]
        tar_path = self._rebuild("evidence.tar", changed)
        evidence_path = self.tmp / "evidence" / "diff-evidence.json"
        proc = self._compare(tar_path, args=["--evidence-json", str(evidence_path)])
        self.assertEqual(proc.returncode, 1)
        self.assertTrue(evidence_path.exists())
        evidence = json.loads(evidence_path.read_text(encoding="utf-8"))
        self.assertEqual(len(evidence["content_changed_same_size"]), 1)
        self.assertEqual(
            evidence["content_changed_same_size"][0]["path"], "shared_prefs/app_prefs.xml"
        )
        self.assertNotIn("app_prefs", proc.stdout)

    def test_compare_refuses_baseline_inside_git_repo(self):
        repo_dir = self.tmp / "fake-repo2"
        repo_dir.mkdir()
        (repo_dir / ".git").write_bytes(b"gitdir: elsewhere\n")
        tar_path = self._rebuild("git-baseline.tar", STANDARD_ENTRIES)
        proc = run_cli([
            "compare", "--archive", str(tar_path), "--baseline", str(repo_dir / "baseline.json"),
            "--expect-baseline-sha256", self.baseline_sha256,
        ])
        self.assertEqual(proc.returncode, 2)
        self.assertEqual(parse_kv(proc.stdout)["REASON"], "BASELINE_INSIDE_GIT_REPO")


if __name__ == "__main__":
    unittest.main(verbosity=2)
