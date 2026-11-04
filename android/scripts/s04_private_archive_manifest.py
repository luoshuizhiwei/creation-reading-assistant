#!/usr/bin/env python3
"""S04 私有归档内容基线工具（纯离线，输入只读）。

两个模式：

  manifest  对 adb 捕获的应用私有数据 tar（仅允许 databases/、files/、shared_prefs/、
            no_backup/ 四个根）逐成员流式计算 SHA-256，生成内容级私有基线清单 JSON。
            可选 --expect-sha256/--expect-size 在生成基线前强制校验归档本体。
            输出文件已存在时默认拒绝（基线一经建立不得被重新运行悄悄替换），
            本工具不提供覆盖既有基线的参数。拒绝覆盖在扫描前先快速失败，且最终落盘
            采用"O_CREAT|O_EXCL 独占占位 + 原子替换"，因此即便目标在扫描期间被另一
            进程创建，也绝不覆盖：命令 BLOCKED，既有文件逐字节不变。

  compare   把"测试后归档"与既有基线清单严格比较：完全一致才 PASS；任何差异返回
            非零，且标准输出只给差异数量与类别，不输出私有文件名或文件内容。
            必须传 --expect-baseline-sha256：在解析、信任基线 JSON 之前，先对基线
            文件的原始字节计算 SHA-256 并与该预期比对；缺参、格式错误或哈希不符
            一律 BLOCKED，不执行任何 PASS 判定。

隐私约定：

  - 标准输出绝不包含成员/文件路径或任何文件内容；差异数量与类别是唯一输出。
  - 基线清单与 --evidence-json 证据文件属仓库外私有证据，工具拒绝把这两类文件
    写进任何 git 仓库内部，也拒绝从 git 仓库内部读取基线。

安全性约定（任一命中即 BLOCKED，不产出任何"通过"结论）：

  - 成员名必须是四个允许根之下的相对路径；拒绝绝对路径、盘符、反斜杠、``..``、
    空段/``.`` 段、控制字符、非 UTF-8 名。
  - 拒绝重复成员名；只接受普通文件与目录成员，符号链接/硬链接/设备/FIFO 一律
    按不能安全解释处理。
  - 归档级 SHA-256/字节数校验不符、tar 解析失败、成员读出字节数与头部声明不符，
    均 BLOCKED。tarfile 在流模式下会把"成员边界处被截断"的归档静默当作正常结束，
    因此工具额外做两项端部校验：结束标记之后的残留字节必须全为零填充；文件末尾
    1024 字节必须全为零（任何标准 tar 都以两个零块 + 可选零填充结尾）。

退出码：0=成功或 PASS；1=发现差异；2=BLOCKED；3=内部/IO 意外错误。
"""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import re
import sys
import tarfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = "s04-private-archive-manifest.v1"
EVIDENCE_SCHEMA = "s04-private-compare-evidence.v1"
ALLOWED_ROOTS = ("databases", "files", "shared_prefs", "no_backup")
WORKDB_BASENAME = "androidx.work.workdb"
WORKDB_REQUIRED_ROOT = "no_backup"
CHUNK = 1024 * 1024
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

EXIT_OK = 0
EXIT_DIFF = 1
EXIT_BLOCKED = 2
EXIT_INTERNAL = 3

UNSAFE_CATEGORY_KEYS = (
    "absolute_path",
    "dotdot_path",
    "duplicate_member",
    "symlink_member",
    "hardlink_member",
    "unsafe_member_type",
    "disallowed_root",
    "backslash_path",
    "drive_path",
    "colon_in_name",
    "non_utf8_name",
    "unsafe_chars",
    "malformed_name",
    "file_at_root_level",
    "size_mismatch",
    "unreadable_member",
)


class ToolBlocked(Exception):
    """BLOCKED：证据级失败，工具不得给出任何通过结论。"""

    def __init__(self, reason: str, fields: dict | None = None):
        super().__init__(reason)
        self.reason = reason
        self.fields = fields or {}


@dataclass
class FileRecord:
    path: str
    size: int
    sha256: str


@dataclass
class ScanResult:
    ok: bool
    archive_path: str
    blocked_reason: str = ""
    blocked_fields: dict = field(default_factory=dict)
    unsafe: collections.Counter = field(default_factory=collections.Counter)
    archive_size: int = 0
    archive_sha256: str = ""
    files: list = field(default_factory=list)  # list[FileRecord]，按路径排序
    dirs: list = field(default_factory=list)  # 归一化目录成员名，排序
    member_count: int = 0
    file_count: int = 0
    dir_count: int = 0
    file_bytes_total: int = 0
    files_by_root: dict = field(default_factory=dict)

    @property
    def roots_present(self) -> set:
        roots = {p.split("/", 1)[0] for p in self.dirs}
        roots |= {r.path.split("/", 1)[0] for r in self.files}
        return roots


class _HashingReader:
    """包装只读文件对象：经过它的每个字节都被计入归档级 SHA-256。"""

    def __init__(self, fileobj, hasher):
        self._fileobj = fileobj
        self._hasher = hasher
        self.bytes_read = 0

    def read(self, n: int = -1) -> bytes:
        data = self._fileobj.read(n)
        if data:
            self._hasher.update(data)
            self.bytes_read += len(data)
        return data

    def tell(self) -> int:
        return self._fileobj.tell()


def normalized_member_name(raw: str, is_dir: bool) -> str:
    """校验并归一化成员名；不安全即抛 ToolBlocked 内部使用的 UnsafeMember。"""
    if "\x00" in raw or any(ord(c) < 0x20 for c in raw):
        raise _UnsafeName("unsafe_chars")
    try:
        raw.encode("utf-8", "strict")
    except UnicodeEncodeError:
        raise _UnsafeName("non_utf8_name")
    if "\\" in raw:
        raise _UnsafeName("backslash_path")
    if raw.startswith("/"):
        raise _UnsafeName("absolute_path")
    if re.match(r"^[A-Za-z]:", raw):
        raise _UnsafeName("drive_path")
    if ":" in raw:
        raise _UnsafeName("colon_in_name")
    name = raw
    while name.startswith("./"):
        name = name[2:]
    if is_dir and name.endswith("/"):
        name = name[:-1]
    elif name.endswith("/"):
        raise _UnsafeName("malformed_name")
    if name in ("", "."):
        raise _UnsafeName("malformed_name")
    parts = name.split("/")
    if any(p == ".." for p in parts):
        raise _UnsafeName("dotdot_path")
    if any(p in ("", ".") for p in parts):
        raise _UnsafeName("malformed_name")
    if parts[0] not in ALLOWED_ROOTS:
        raise _UnsafeName("disallowed_root")
    if not is_dir and len(parts) < 2:
        raise _UnsafeName("file_at_root_level")
    return name


class _UnsafeName(Exception):
    def __init__(self, category: str):
        super().__init__(category)
        self.category = category


def scan_archive(
    archive: Path,
    expect_sha256: str | None = None,
    expect_size: int | None = None,
) -> ScanResult:
    """单遍流式扫描：归档级 SHA-256 + 逐成员安全校验 + 逐文件内容 SHA-256。"""
    result = ScanResult(ok=False, archive_path=str(archive))
    try:
        st = archive.stat()
    except OSError as exc:
        result.blocked_reason = "IO_ERROR"
        result.blocked_fields = {"detail": f"cannot stat archive: {exc}"}
        return result
    if not archive.is_file():
        result.blocked_reason = "IO_ERROR"
        result.blocked_fields = {"detail": "archive is not a regular file"}
        return result

    result.archive_size = st.st_size
    if expect_size is not None and st.st_size != expect_size:
        result.blocked_reason = "ARCHIVE_SIZE_MISMATCH"
        result.blocked_fields = {"EXPECTED_SIZE_BYTES": expect_size, "ACTUAL_SIZE_BYTES": st.st_size}
        return result

    hasher = hashlib.sha256()
    unsafe: collections.Counter = collections.Counter()
    files: list = []
    dirs: list = []
    seen: set = set()
    files_by_root: collections.Counter = collections.Counter()
    tar_error: str = ""
    trailing_nonzero = False
    end_marker_ok = False

    try:
        with open(archive, "rb") as raw:
            reader = _HashingReader(raw, hasher)
            with tarfile.open(fileobj=reader, mode="r|", encoding="utf-8", errors="surrogateescape") as tf:
                for member in tf:
                    result.member_count += 1
                    try:
                        norm = normalized_member_name(member.name, member.isdir())
                    except _UnsafeName as exc:
                        unsafe[exc.category] += 1
                        continue
                    if member.isdir():
                        if norm in seen:
                            unsafe["duplicate_member"] += 1
                            continue
                        seen.add(norm)
                        dirs.append(norm)
                        result.dir_count += 1
                    elif member.isreg():
                        if norm in seen:
                            unsafe["duplicate_member"] += 1
                            continue
                        digest = hashlib.sha256()
                        consumed = 0
                        extracted = tf.extractfile(member)
                        if extracted is None:
                            unsafe["unreadable_member"] += 1
                            continue
                        with extracted:
                            while True:
                                chunk = extracted.read(CHUNK)
                                if not chunk:
                                    break
                                digest.update(chunk)
                                consumed += len(chunk)
                        if consumed != member.size:
                            unsafe["size_mismatch"] += 1
                            continue
                        seen.add(norm)
                        files.append(FileRecord(norm, member.size, digest.hexdigest()))
                        result.file_count += 1
                        result.file_bytes_total += member.size
                        files_by_root[norm.split("/", 1)[0]] += 1
                    elif member.issym():
                        unsafe["symlink_member"] += 1
                    elif member.islnk():
                        unsafe["hardlink_member"] += 1
                    else:
                        unsafe["unsafe_member_type"] += 1
            # tar 流在归档结束标记处停止；排空剩余字节并验证全为零填充——
            # 若存在非零残留，说明 tarfile 在归档中途提前终止（或归档被篡改）。
            while True:
                tail = reader.read(CHUNK)
                if not tail:
                    break
                if any(tail):
                    trailing_nonzero = True
                    break
            # 补充端部校验：tarfile 流模式会把成员边界处的截断静默当作正常结束，
            # 因此直接检查文件末尾 1024 字节（两个零块 + 可选零填充必为零）。
            if st.st_size >= 1024:
                raw.seek(-1024, os.SEEK_END)
                end_marker_ok = not any(raw.read(1024))
    except tarfile.TarError as exc:
        tar_error = f"{type(exc).__name__}"
    except OSError as exc:
        result.blocked_reason = "IO_ERROR"
        result.blocked_fields = {"detail": f"read failure: {exc}"}
        return result

    if tar_error:
        result.blocked_reason = "TAR_READ_ERROR"
        result.blocked_fields = {"detail": tar_error}
        return result
    if trailing_nonzero:
        result.blocked_reason = "TAR_TRAILING_NONZERO"
        result.blocked_fields = {"detail": "nonzero bytes after tar end-of-archive marker"}
        return result
    if not end_marker_ok:
        result.blocked_reason = "TAR_END_MARKER_MISSING"
        result.blocked_fields = {"detail": "archive does not end with zero end-of-archive blocks"}
        return result
    if unsafe:
        result.blocked_reason = "UNSAFE_MEMBERS"
        result.unsafe = unsafe
        return result
    if reader.bytes_read != st.st_size:
        result.blocked_reason = "ARCHIVE_READ_INCOMPLETE"
        result.blocked_fields = {"stat_size_bytes": st.st_size, "hashed_bytes": reader.bytes_read}
        return result

    result.archive_sha256 = hasher.hexdigest()
    if expect_sha256 is not None and result.archive_sha256 != expect_sha256.strip().lower():
        result.blocked_reason = "ARCHIVE_SHA256_MISMATCH"
        result.blocked_fields = {
            "EXPECTED_SHA256": expect_sha256.strip().lower(),
            "ACTUAL_SHA256": result.archive_sha256,
        }
        return result

    files.sort(key=lambda r: r.path)
    dirs.sort()
    result.ok = True
    result.files = files
    result.dirs = dirs
    result.file_bytes_total = sum(r.size for r in files)
    result.files_by_root = {root: files_by_root.get(root, 0) for root in ALLOWED_ROOTS}
    return result


def refuse_if_inside_git_repo(target: Path, role: str) -> None:
    base = target.resolve().parent
    for cand in (base, *base.parents):
        if (cand / ".git").exists():
            raise ToolBlocked(
                f"{role}_INSIDE_GIT_REPO",
                {"path": str(target), "hint": "私有基线/证据必须保存在仓库之外"},
            )


def load_baseline(baseline: Path, expect_sha256: str | None) -> dict:
    """读取基线：先对文件原始字节做 SHA-256 锚定，通过后才解析与信任 JSON。

    哈希校验先于任何解析：基线内容被改动（即便仍是合法 JSON）都会在这里被拒绝，
    比较逻辑永远不会接触未经锚定的基线内容，也不会把其中任何路径送进输出。
    """
    refuse_if_inside_git_repo(baseline, "BASELINE")
    if not expect_sha256 or not expect_sha256.strip():
        raise ToolBlocked("EXPECTED_BASELINE_SHA256_MISSING")
    expected = expect_sha256.strip().lower()
    if not SHA256_RE.fullmatch(expected):
        raise ToolBlocked("EXPECTED_BASELINE_SHA256_INVALID_FORMAT")
    try:
        raw = baseline.read_bytes()
    except OSError as exc:
        raise ToolBlocked("BASELINE_UNREADABLE", {"detail": str(exc)})
    actual = hashlib.sha256(raw).hexdigest()
    if actual != expected:
        raise ToolBlocked(
            "EXPECTED_BASELINE_SHA256_MISMATCH",
            {
                "EXPECTED_BASELINE_SHA256": expected,
                "ACTUAL_BASELINE_SHA256": actual,
            },
        )
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ToolBlocked("BASELINE_INVALID_JSON", {"detail": str(exc)})
    if not isinstance(data, dict) or data.get("schema") != SCHEMA:
        raise ToolBlocked("BASELINE_SCHEMA_MISMATCH", {"expected_schema": SCHEMA})
    policy = data.get("policy") or {}
    if policy.get("hash_algorithm") != "sha256":
        raise ToolBlocked("BASELINE_HASH_ALGORITHM_UNSUPPORTED")
    files = data.get("files")
    if not isinstance(files, list):
        raise ToolBlocked("BASELINE_FILES_MISSING")
    parsed = []
    for entry in files:
        if not isinstance(entry, dict):
            raise ToolBlocked("BASELINE_FILE_ENTRY_INVALID")
        path = entry.get("path")
        size = entry.get("size_bytes")
        digest = entry.get("sha256")
        if not isinstance(path, str) or not isinstance(size, int) or size < 0:
            raise ToolBlocked("BASELINE_FILE_ENTRY_INVALID")
        if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest.lower()):
            raise ToolBlocked("BASELINE_FILE_ENTRY_INVALID")
        parsed.append({"path": path, "size_bytes": size, "sha256": digest.lower()})
    dirs = data.get("directories")
    if not isinstance(dirs, list) or not all(isinstance(d, str) for d in dirs):
        raise ToolBlocked("BASELINE_DIRECTORIES_INVALID")
    counts = data.get("counts") or {}
    if isinstance(counts.get("regular_files"), int) and counts["regular_files"] != len(parsed):
        raise ToolBlocked("BASELINE_COUNTS_INCONSISTENT")
    data["files"] = parsed
    return data


def workdb_check(scan: ScanResult) -> dict:
    """WorkManager workdb 位置核对：主库与 WAL/SHM 必须位于 no_backup/。"""
    main = [r for r in scan.files if r.path.rsplit("/", 1)[-1] == WORKDB_BASENAME]
    wal = [r for r in scan.files if r.path.rsplit("/", 1)[-1] == WORKDB_BASENAME + "-wal"]
    shm = [r for r in scan.files if r.path.rsplit("/", 1)[-1] == WORKDB_BASENAME + "-shm"]
    violations = []
    if not main:
        violations.append("WORKDB_NOT_FOUND")
    for record in main:
        if record.path.split("/", 1)[0] != WORKDB_REQUIRED_ROOT:
            violations.append("WORKDB_OUTSIDE_NO_BACKUP")
    for record in wal + shm:
        if record.path.split("/", 1)[0] != WORKDB_REQUIRED_ROOT:
            violations.append("WORKDB_WAL_SHM_OUTSIDE_NO_BACKUP")
    return {
        "required_root": WORKDB_REQUIRED_ROOT,
        "main_found": len(main),
        "wal_found": len(wal),
        "shm_found": len(shm),
        "violations": violations,
    }


def serialize_json_bytes(payload: dict) -> bytes:
    return (json.dumps(payload, indent=2, ensure_ascii=True, sort_keys=False) + "\n").encode("utf-8")


def write_private_json(target: Path, payload: dict, role: str) -> str:
    """原子写出私有 JSON，返回其 SHA-256（hex）。写入位置禁止位于 git 仓库内。

    竞态安全（不覆盖既有目标）：目标名先用 ``O_CREAT|O_EXCL`` 独占占位——
    目标若在调用前或调用瞬间已存在（含"扫描期间另一进程恰好创建同名文件"），
    立即抛 ``<ROLE>_OUTPUT_EXISTS``，既有文件逐字节不变，且不产生任何临时文件；
    占位成功后再把完整内容写入临时文件并以 ``os.replace`` 原子发布。
    因此本函数是"先占用名字、后原子落盘"，写入期间不存在可被外部读取的半成品，
    也不会覆盖任何既有文件。目标与临时文件均以 ``0o600`` 创建（POSIX 生效；
    Windows 忽略 mode，依赖目录 ACL），使最终发布的私有文件不再是宽松默认权限。
    """
    refuse_if_inside_git_repo(target, role)
    target.parent.mkdir(parents=True, exist_ok=True)
    data = serialize_json_bytes(payload)
    tmp = target.with_name(target.name + ".tmp")
    try:
        fd = os.open(
            target,
            os.O_CREAT | os.O_EXCL | os.O_WRONLY | getattr(os, "O_BINARY", 0),
            0o600,
        )
    except FileExistsError:
        raise ToolBlocked(
            f"{role}_OUTPUT_EXISTS",
            {
                "PATH": str(target),
                "HINT": "目标已存在（可能由另一进程在本次运行期间创建）；本工具绝不覆盖既有文件",
            },
        )
    try:
        os.close(fd)
        # 临时文件同样以 0o600 创建：os.replace 保留 inode，使发布后的文件权限
        # 不受默认 umask 影响（否则占位时的 0o600 会被替换为 tmp 的宽松权限）。
        tmp_fd = os.open(
            tmp,
            os.O_CREAT | os.O_TRUNC | os.O_WRONLY | getattr(os, "O_BINARY", 0),
            0o600,
        )
        with os.fdopen(tmp_fd, "wb") as handle:
            handle.write(data)
        os.replace(tmp, target)
    except BaseException:
        # 回滚：清除本次占位目标与临时文件，避免留下半成品。
        for leftover in (tmp, target):
            try:
                leftover.unlink()
            except OSError:
                pass
        raise
    return hashlib.sha256(data).hexdigest()


def print_blocked(reason: str, fields: dict | None = None) -> None:
    print("RESULT: BLOCKED")
    print(f"REASON: {reason}")
    for key, value in (fields or {}).items():
        print(f"{key}: {value}")
    print("EXIT_CODE: 2")


def cmd_manifest(args) -> int:
    archive = Path(args.archive)
    output = Path(args.output)
    try:
        refuse_if_inside_git_repo(output, "MANIFEST")
        # 快速失败：扫描（可能数 GB）之前先拒绝明显已存在的输出。
        # 这不是唯一的防线——扫描后落盘时 write_private_json 会再做一次
        # O_CREAT|O_EXCL 独占占位，覆盖"扫描期间另一进程创建同名文件"的竞态。
        if output.exists():
            raise ToolBlocked(
                "MANIFEST_OUTPUT_EXISTS",
                {
                    "PATH": str(output),
                    "HINT": "基线一经建立不得被重新运行悄悄替换；如需新基线请使用新路径并另行登记",
                },
            )
        scan = scan_archive(
            archive,
            expect_sha256=args.expect_sha256,
            expect_size=args.expect_size,
        )
        if not scan.ok:
            if scan.blocked_reason == "UNSAFE_MEMBERS":
                fields = {f"UNSAFE_{k.upper()}": scan.unsafe.get(k, 0) for k in UNSAFE_CATEGORY_KEYS}
                print_blocked(scan.blocked_reason, fields)
            else:
                print_blocked(scan.blocked_reason, scan.blocked_fields)
            return EXIT_BLOCKED

        check = workdb_check(scan) if args.expect_workdb_in_no_backup else None
        if check is not None and check["violations"]:
            fields = {
                "WORKDB_MAIN_FOUND": check["main_found"],
                "WORKDB_WAL_FOUND": check["wal_found"],
                "WORKDB_SHM_FOUND": check["shm_found"],
                "WORKDB_VIOLATIONS": ",".join(check["violations"]),
            }
            print_blocked("WORKDB_LOCATION_VIOLATION", fields)
            return EXIT_BLOCKED

        manifest = {
            "schema": SCHEMA,
            "created_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "tool": "android/scripts/s04_private_archive_manifest.py",
            "archive": {
                "file_name": archive.name,
                "size_bytes": scan.archive_size,
                "sha256": scan.archive_sha256,
            },
            "expectations_enforced": {
                "sha256": args.expect_sha256.strip().lower() if args.expect_sha256 else None,
                "size_bytes": args.expect_size,
            },
            "policy": {
                "hash_algorithm": "sha256",
                "allowed_roots": list(ALLOWED_ROOTS),
                "content_level": "regular files: relative path + byte size + sha256",
                "ignored_tar_metadata": ["mtime", "mode", "uid", "gid", "uname", "gname"],
            },
            "counts": {
                "tar_members": scan.member_count,
                "directories": scan.dir_count,
                "regular_files": scan.file_count,
                "file_bytes_total": scan.file_bytes_total,
                "files_by_root": scan.files_by_root,
                "roots_present": sorted(scan.roots_present),
            },
            "files": [
                {"path": r.path, "size_bytes": r.size, "sha256": r.sha256} for r in scan.files
            ],
            "directories": scan.dirs,
            "workdb_check": check,
        }
        manifest_sha256 = write_private_json(output, manifest, "MANIFEST")

        print("RESULT: OK")
        print(f"ARCHIVE_FILE: {archive.name}")
        print(f"ARCHIVE_SIZE_BYTES: {scan.archive_size}")
        print(f"ARCHIVE_SHA256: {scan.archive_sha256}")
        print(f"TAR_MEMBERS: {scan.member_count}")
        print(f"DIRECTORIES: {scan.dir_count}")
        print(f"REGULAR_FILES: {scan.file_count}")
        print(f"FILE_BYTES_TOTAL: {scan.file_bytes_total}")
        for root in ALLOWED_ROOTS:
            print(f"FILES_BY_ROOT_{root.upper()}: {scan.files_by_root.get(root, 0)}")
        if check is None:
            print("WORKDB_CHECK: skipped")
        else:
            status = "ok" if not check["violations"] else "violations"
            print(
                f"WORKDB_CHECK: {status} (main={check['main_found']} "
                f"wal={check['wal_found']} shm={check['shm_found']})"
            )
        print(f"MANIFEST_PATH: {output}")
        print(f"MANIFEST_SHA256: {manifest_sha256}")
        print("EXIT_CODE: 0")
        return EXIT_OK
    except ToolBlocked as blocked:
        print_blocked(blocked.reason, blocked.fields)
        return EXIT_BLOCKED


def _diff_areas(path: str) -> list:
    areas = []
    root = path.split("/", 1)[0]
    if root in ALLOWED_ROOTS:
        areas.append(f"AREA_{root.upper()}")
    base = path.rsplit("/", 1)[-1]
    if base.endswith("-wal") or base.endswith("-shm"):
        areas.append("AREA_WAL_SHM")
    return areas


def cmd_compare(args) -> int:
    archive = Path(args.archive)
    baseline_path = Path(args.baseline)
    try:
        baseline = load_baseline(baseline_path, expect_sha256=args.expect_baseline_sha256)
        scan = scan_archive(archive, expect_sha256=args.expect_sha256, expect_size=args.expect_size)
        if not scan.ok:
            if scan.blocked_reason == "UNSAFE_MEMBERS":
                fields = {f"UNSAFE_{k.upper()}": scan.unsafe.get(k, 0) for k in UNSAFE_CATEGORY_KEYS}
                print_blocked(scan.blocked_reason, fields)
            else:
                print_blocked(scan.blocked_reason, scan.blocked_fields)
            return EXIT_BLOCKED

        base_map = {entry["path"]: (entry["size_bytes"], entry["sha256"]) for entry in baseline["files"]}
        new_map = {r.path: (r.size, r.sha256) for r in scan.files}
        base_roots = {p.split("/", 1)[0] for p in baseline.get("directories", [])}
        base_roots |= {p.split("/", 1)[0] for p in base_map}
        new_roots = scan.roots_present

        added = sorted(set(new_map) - set(base_map))
        removed = sorted(set(base_map) - set(new_map))
        common = set(base_map) & set(new_map)
        same_size_changed = sorted(
            p for p in common if base_map[p][0] == new_map[p][0] and base_map[p][1] != new_map[p][1]
        )
        size_changed = sorted(p for p in common if base_map[p][0] != new_map[p][0])
        roots_missing = sorted(base_roots - new_roots)

        diff_paths = added + removed + same_size_changed + size_changed
        areas: collections.Counter = collections.Counter()
        for path in diff_paths:
            for area in _diff_areas(path):
                areas[area] += 1

        total = len(added) + len(removed) + len(same_size_changed) + len(size_changed) + len(roots_missing)

        print(f"RESULT: {'PASS' if total == 0 else 'DIFF'}")
        print(f"BASELINE_ARCHIVE_FILE: {baseline.get('archive', {}).get('file_name', '')}")
        print(f"BASELINE_ARCHIVE_SHA256: {baseline.get('archive', {}).get('sha256', '')}")
        print(f"NEW_ARCHIVE_FILE: {archive.name}")
        print(f"NEW_ARCHIVE_SIZE_BYTES: {scan.archive_size}")
        print(f"NEW_ARCHIVE_SHA256: {scan.archive_sha256}")
        print(f"NEW_ARCHIVE_TAR_MEMBERS: {scan.member_count}")
        print(f"NEW_ARCHIVE_REGULAR_FILES: {scan.file_count}")
        print(f"TOTAL_DIFFERENCES: {total}")
        print(f"FILE_ADDED: {len(added)}")
        print(f"FILE_REMOVED: {len(removed)}")
        print(f"CONTENT_CHANGED_SAME_SIZE: {len(same_size_changed)}")
        print(f"CONTENT_CHANGED_SIZE_CHANGED: {len(size_changed)}")
        print(f"ROOT_MISSING: {len(roots_missing)}")
        print(f"ROOT_MISSING_ROOTS: {','.join(roots_missing) if roots_missing else 'none'}")
        for area in ("AREA_DATABASES", "AREA_FILES", "AREA_SHARED_PREFS", "AREA_NO_BACKUP", "AREA_WAL_SHM"):
            print(f"{area}: {areas.get(area, 0)}")
        if total == 0:
            print("NOTE: byte-level content identical to baseline at every compared path.")
            print("EXIT_CODE: 0")
            return EXIT_OK
        print(
            "NOTE: byte-level differences are pending attribution (UNATTRIBUTED) until human review; "
            "no file names or contents are printed by design."
        )
        if args.evidence_json:
            evidence = {
                "schema": EVIDENCE_SCHEMA,
                "created_utc": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "baseline": {
                    "file_name": baseline_path.name,
                    "archive": baseline.get("archive", {}),
                },
                "new_archive": {
                    "file_name": archive.name,
                    "size_bytes": scan.archive_size,
                    "sha256": scan.archive_sha256,
                },
                "roots_missing": roots_missing,
                "added": added,
                "removed": removed,
                "content_changed_same_size": [
                    {
                        "path": p,
                        "size_bytes": base_map[p][0],
                        "baseline_sha256": base_map[p][1],
                        "new_sha256": new_map[p][1],
                    }
                    for p in same_size_changed
                ],
                "content_changed_size_changed": [
                    {
                        "path": p,
                        "baseline_size_bytes": base_map[p][0],
                        "new_size_bytes": new_map[p][0],
                        "baseline_sha256": base_map[p][1],
                        "new_sha256": new_map[p][1],
                    }
                    for p in size_changed
                ],
            }
            evidence_path = Path(args.evidence_json)
            evidence_sha256 = write_private_json(evidence_path, evidence, "EVIDENCE")
            print(f"EVIDENCE_PATH: {evidence_path}")
            print(f"EVIDENCE_SHA256: {evidence_sha256}")
        print("EXIT_CODE: 1")
        return EXIT_DIFF
    except ToolBlocked as blocked:
        print_blocked(blocked.reason, blocked.fields)
        return EXIT_BLOCKED


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="s04_private_archive_manifest.py",
        description="S04 私有归档内容基线：manifest 生成 / compare 严格比较（离线，输入只读）。",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_manifest = sub.add_parser("manifest", help="生成内容级私有基线清单 JSON")
    p_manifest.add_argument("--archive", required=True, help="输入 tar（只读）")
    p_manifest.add_argument("--output", required=True, help="基线清单输出路径（仓库外私有位置）")
    p_manifest.add_argument("--expect-sha256", help="强制校验归档 SHA-256（大小写不敏感）")
    p_manifest.add_argument("--expect-size", type=int, help="强制校验归档字节数")
    p_manifest.add_argument(
        "--expect-workdb-in-no-backup",
        action="store_true",
        help="强制核对 WorkManager workdb 主库与 WAL/SHM 位于 no_backup/（旧文档的 databases/ 写法会被拒绝）",
    )
    p_manifest.set_defaults(func=cmd_manifest)

    p_compare = sub.add_parser("compare", help="把归档与基线严格比较（PASS 仅当逐字节一致）")
    p_compare.add_argument("--archive", required=True, help="待比较的归档（如测试后捕获）")
    p_compare.add_argument("--baseline", required=True, help="manifest 模式生成的基线清单 JSON")
    p_compare.add_argument(
        "--expect-baseline-sha256",
        help="必填：基线清单文件原始字节的 SHA-256（信任锚，先于 JSON 解析校验）",
    )
    p_compare.add_argument("--expect-sha256", help="可选：强制校验待比较归档 SHA-256")
    p_compare.add_argument("--expect-size", type=int, help="可选：强制校验待比较归档字节数")
    p_compare.add_argument(
        "--evidence-json",
        help="可选：把带文件路径的差异明细写入该私有文件（必须仓库外；stdout 仍只输出数量与类别）",
    )
    p_compare.set_defaults(func=cmd_compare)
    return parser


def main(argv=None) -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, OSError):
        pass
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001 — 任何意外都不得伪装成通过
        print("RESULT: BLOCKED")
        print(f"REASON: INTERNAL_ERROR")
        print(f"DETAIL: {type(exc).__name__}")
        print("EXIT_CODE: 3")
        return EXIT_INTERNAL


if __name__ == "__main__":
    sys.exit(main())
