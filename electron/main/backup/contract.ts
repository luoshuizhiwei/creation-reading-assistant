import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { existsSync } from "node:fs";
import { cp, mkdir, mkdtemp, readFile, readdir, rm, symlink, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { openCreationWorkspace } from "../creation-workspace";
import {
  BackupError,
  createBackupSnapshot,
  inventoryTree,
  readBackupManifest,
  restoreBackupFromDirectory,
  type BackupManifest
} from "./index";

async function writeJson(filePath: string, value: unknown): Promise<void> {
  await mkdir(path.dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function readJson(filePath: string): Promise<unknown> {
  return JSON.parse(await readFile(filePath, "utf8")) as unknown;
}

async function hashFile(filePath: string): Promise<string> {
  return createHash("sha256").update(await readFile(filePath)).digest("hex");
}

/** 递归哈希目录：按相对路径排序拼接，用于整树相等断言。 */
async function hashDir(root: string): Promise<string> {
  const entries: Array<{ relative: string; hash: string }> = [];
  async function visit(current: string): Promise<void> {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await visit(absolute);
      } else if (entry.isFile()) {
        const relative = path.relative(root, absolute).split(path.sep).join("/");
        entries.push({ relative, hash: await hashFile(absolute) });
      }
    }
  }
  await visit(root);
  entries.sort((a, b) => (a.relative < b.relative ? -1 : a.relative > b.relative ? 1 : 0));
  return entries.map((entry) => `${entry.relative}:${entry.hash}`).join("|");
}

/** 复刻旧 restoreBackup 的唯一校验逻辑（index.ts 旧实现）：暂存目录存在即通过，无校验和、无 SQLite 检查。 */
async function legacyRestoreHeuristic(backupRoot: string): Promise<boolean> {
  const appDataBackupPath = path.join(backupRoot, "app-data");
  const tempRestorePath = `${backupRoot}.legacy-restoring`;
  if (existsSync(tempRestorePath)) await rm(tempRestorePath, { recursive: true, force: true });
  await cp(appDataBackupPath, tempRestorePath, { recursive: true, force: true });
  const accepted = existsSync(tempRestorePath);
  await rm(tempRestorePath, { recursive: true, force: true });
  return accepted;
}

async function makeAppDataFixture(root: string, withWorkspace = true): Promise<void> {
  await mkdir(path.join(root, "CreationWorkspace"), { recursive: true });
  await mkdir(path.join(root, "logs"), { recursive: true });
  await writeJson(path.join(root, "app-settings.json"), { version: 1, appearance: {}, ai: {}, storage: {} });
  await writeJson(path.join(root, "inspirations.json"), { version: 1, items: [{ id: "insp-1", title: "测试灵感", body: "正文" }] });
  await writeJson(path.join(root, "sync-state.json"), { version: 1, deviceId: "device-1", devices: [], updatedAt: "2026-01-01T00:00:00.000Z" });
  await writeJson(path.join(root, "runtime-state.json"), { version: 1, cleanShutdown: true });
  await writeFile(path.join(root, "logs", "app-test.log"), "sentinel log line\n", "utf8");
  if (withWorkspace) {
    const workspace = await openCreationWorkspace({ directory: path.join(root, "CreationWorkspace") });
    await workspace.close();
  }
}

async function makeLibraryFixture(root: string): Promise<void> {
  await mkdir(path.join(root, "files"), { recursive: true });
  await writeJson(path.join(root, "library.json"), { version: 1, books: [] });
  await writeFile(path.join(root, "files", "fixture-book.epub"), "EPUB_BYTES_SENTINEL", "utf8");
}

/** 篡改备份后按当前磁盘内容重建 manifest（模拟“备份时即损坏”的一致性清单）。 */
async function resyncManifest(backupRoot: string): Promise<void> {
  const manifestPath = path.join(backupRoot, "backup-manifest.json");
  const manifest = (await readJson(manifestPath)) as BackupManifest;
  manifest.files = await inventoryTree(path.join(backupRoot, "app-data"));
  if (manifest.libraryFiles) {
    manifest.libraryFiles = await inventoryTree(path.join(backupRoot, "library"));
  }
  await writeJson(manifestPath, manifest);
}

function assertSafeMessage(message: string, roots: string[]): void {
  for (const root of roots) {
    assert.equal(message.includes(root), false, `错误信息泄露了绝对路径：${message}`);
  }
  assert.equal(/\b[A-Za-z]:[\\/]/.test(message), false, `错误信息包含盘符路径：${message}`);
  assert.equal(message.includes("\\"), false, `错误信息包含反斜杠路径分隔符：${message}`);
}

interface RestoreCall {
  backupRoot: string;
  currentAppDataRoot: string;
  currentLibraryRoot: string;
  libraryTarget?: string;
  failAppDataRename?: boolean;
  failLibraryRename?: boolean;
}

async function callRestore(options: RestoreCall): Promise<unknown> {
  return restoreBackupFromDirectory({
    backupRoot: options.backupRoot,
    currentAppDataRoot: options.currentAppDataRoot,
    resolveLibraryTarget: async () => options.libraryTarget ?? path.join(options.currentAppDataRoot, "AppLibrary"),
    logger: () => undefined,
    testHooks: {
      failAppDataRename: options.failAppDataRename === true,
      failLibraryRename: options.failLibraryRename === true
    }
  });
}

async function expectReject(
  name: string,
  options: RestoreCall,
  expectedCode: string
): Promise<{ code: string; message: string; checkpointPath?: string }> {
  try {
    await callRestore(options);
    assert.fail(`${name}: 期望拒绝，但恢复成功。`);
  } catch (error) {
    assert.ok(error instanceof BackupError, `${name}: 期望 BackupError，实际 ${String(error)}`);
    assert.equal(error.code, expectedCode, `${name}: 期望 code=${expectedCode}，实际 ${error.code}（${error.message}）`);
    assertSafeMessage(error.message, [options.backupRoot, options.currentAppDataRoot, options.libraryTarget ?? options.currentLibraryRoot]);
    return { code: error.code, message: error.message, checkpointPath: (error.detail as { checkpointPath?: string } | undefined)?.checkpointPath };
  }
  return { code: "", message: "" };
}

async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "backup-contract-"));
  const progress = (label: string): void => {
    console.error(`[backup-contract] ${label}`);
  };
  const failures: string[] = [];
  let passed = 0;
  const pendingChecks: Array<Promise<void>> = [];
  const check = (name: string, fn: () => void | Promise<void>): void => {
    pendingChecks.push(
      Promise.resolve()
        .then(fn)
        .then(
          () => {
            passed += 1;
          },
          (error) => {
            failures.push(`${name}: ${error instanceof Error ? error.message : String(error)}`);
          }
        )
    );
  };

  try {
    // ============ 场景 1：旧启发式接受损坏备份，新校验拒绝 ============
    {
      progress("S1");
      const backupRoot = path.join(parent, "s1-backup");
      const source = path.join(parent, "s1-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      // 破坏 SQLite 内容并重建清单（备份时即损坏，哈希一致）
      const sqlitePath = path.join(backupRoot, "app-data", "CreationWorkspace", "workspace.sqlite");
      await writeFile(sqlitePath, "CORRUPTED_SQLITE_BYTES", "utf8");
      await resyncManifest(backupRoot);
      const legacyAccepted = await legacyRestoreHeuristic(backupRoot);
      const currentRoot = path.join(parent, "s1-current");
      await makeAppDataFixture(currentRoot);
      await writeFile(path.join(currentRoot, "inspirations.json"), JSON.stringify({ version: 1, items: [{ id: "current", title: "当前数据", body: "必须保留" }] }), "utf8");
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S1 损坏备份被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "sqlite-integrity");
      check("S1 旧启发式接受损坏备份", () => assert.equal(legacyAccepted, true));
      check("S1 校验失败后当前数据未动", async () => {
        assert.equal(await hashDir(currentRoot), beforeHash);
      });
      check("S1 staging 已清理", async () => {
        assert.equal(existsSync(`${currentRoot}.restoring`), false);
      });
      check("S1 检查点保留且可解释", async () => {
        const checkpoint = rejection.checkpointPath;
        assert.ok(checkpoint, "期望存在检查点路径");
        assert.equal(existsSync(checkpoint!), true);
        assert.equal(await hashDir(checkpoint!), beforeHash);
      });
    }

    // ============ 场景 2：有效备份完整往返（无外置资料库） ============
    {
      progress("S2");
      const backupRoot = path.join(parent, "s2-backup");
      const source = path.join(parent, "s2-source");
      await makeAppDataFixture(source);
      const manifest = await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      check("S2 manifest v2 含全部文件与 sha256", () => {
        assert.equal(manifest.version, 2);
        assert.equal(manifest.checksumAlgorithm, "sha256");
        assert.ok(manifest.files.length >= 6);
        for (const entry of manifest.files) {
          assert.match(entry.sha256, /^[0-9a-f]{64}$/);
          assert.ok(!entry.path.startsWith("/") && !entry.path.includes("..") && !entry.path.includes("\\"));
        }
        assert.ok(manifest.files.some((entry) => entry.path === "CreationWorkspace/workspace.sqlite"));
      });
      const currentRoot = path.join(parent, "s2-current");
      await makeAppDataFixture(currentRoot);
      await writeFile(path.join(currentRoot, "inspirations.json"), JSON.stringify({ version: 1, items: [{ id: "old", title: "旧数据", body: "将被替换" }] }), "utf8");
      const restored = (await callRestore({
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: path.join(currentRoot, "AppLibrary")
      })) as { restoredAt: string; checkpointPath?: string; appDataRestored: boolean };
      check("S2 恢复成功且内容一致", async () => {
        assert.equal(restored.appDataRestored, true);
        assert.equal(await hashDir(currentRoot), await hashDir(path.join(backupRoot, "app-data")));
      });
      check("S2 检查点已创建", () => assert.ok(restored.checkpointPath && existsSync(restored.checkpointPath!)));
      check("S2 恢复后的数据库可打开", async () => {
        const workspace = await openCreationWorkspace({ directory: path.join(currentRoot, "CreationWorkspace") });
        await workspace.close();
      });
      check("S2 清单可再次解析", async () => {
        const reread = await readBackupManifest(backupRoot);
        assert.equal(reread.files.length, manifest.files.length);
      });
    }

    // ============ 场景 3：有效备份完整往返（外置资料库） ============
    {
      progress("S3");
      const backupRoot = path.join(parent, "s3-backup");
      const source = path.join(parent, "s3-source");
      const sourceLibrary = path.join(parent, "s3-library");
      await makeAppDataFixture(source);
      await makeLibraryFixture(sourceLibrary);
      await writeJson(path.join(source, "app-settings.json"), {
        version: 1,
        storage: { dataDirectory: source, libraryDirectory: sourceLibrary }
      });
      const manifest = await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        libraryDirectory: sourceLibrary,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      check("S3 外置资料库已入清单", () => {
        assert.equal(manifest.libraryFiles !== undefined, true);
        assert.ok(manifest.libraryFiles!.some((entry) => entry.path === "library.json"));
      });
      const currentRoot = path.join(parent, "s3-current");
      const currentLibrary = path.join(parent, "s3-current-library");
      await makeAppDataFixture(currentRoot);
      await makeLibraryFixture(currentLibrary);
      await writeFile(path.join(currentLibrary, "library.json"), JSON.stringify({ version: 1, books: [{ id: "old" }] }), "utf8");
      const restored = (await callRestore({
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: currentLibrary,
        libraryTarget: currentLibrary
      })) as { libraryRestored: boolean };
      check("S3 资料库恢复成功且内容一致", async () => {
        assert.equal(restored.libraryRestored, true);
        assert.equal(await hashDir(currentLibrary), await hashDir(path.join(backupRoot, "library")));
      });
    }

    // ============ 场景 4：单个文件哈希不一致 ============
    {
      progress("S4");
      const backupRoot = path.join(parent, "s4-backup");
      const source = path.join(parent, "s4-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeFile(path.join(backupRoot, "app-data", "inspirations.json"), JSON.stringify({ version: 1, items: [{ id: "tampered" }] }), "utf8");
      const currentRoot = path.join(parent, "s4-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S4 哈希不一致被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "hash-mismatch");
      check("S4 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
      check("S4 staging 已清理", () => assert.equal(existsSync(`${currentRoot}.restoring`), false));
      check("S4 检查点保留", async () => {
        assert.ok(rejection.checkpointPath && existsSync(rejection.checkpointPath));
        assert.equal(await hashDir(rejection.checkpointPath!), beforeHash);
      });
    }

    // ============ 场景 5：清单文件缺失 ============
    {
      progress("S5");
      const backupRoot = path.join(parent, "s5-backup");
      const source = path.join(parent, "s5-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await rm(path.join(backupRoot, "app-data", "inspirations.json"), { force: true });
      const currentRoot = path.join(parent, "s5-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S5 缺失文件被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "file-missing");
      check("S5 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
      check("S5 错误信息可读且无绝对路径", () => assert.ok(rejection.message.includes("备份缺少文件")));
    }

    // ============ 场景 6：未列出的额外文件 ============
    {
      progress("S6");
      const backupRoot = path.join(parent, "s6-backup");
      const source = path.join(parent, "s6-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeFile(path.join(backupRoot, "app-data", "rogue-extra.json"), "{}", "utf8");
      const currentRoot = path.join(parent, "s6-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S6 额外文件被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "file-extra");
      check("S6 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
      check("S6 错误信息可读", () => assert.ok(rejection.message.includes("未在清单")));
    }

    // ============ 场景 7：SQLite 损坏（哈希一致但 integrity_check 失败） ============
    {
      progress("S7");
      const backupRoot = path.join(parent, "s7-backup");
      const source = path.join(parent, "s7-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeFile(path.join(backupRoot, "app-data", "CreationWorkspace", "workspace.sqlite"), "NOT_A_DATABASE", "utf8");
      await resyncManifest(backupRoot);
      const currentRoot = path.join(parent, "s7-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S7 损坏 SQLite 被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "sqlite-integrity");
      check("S7 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
      check("S7 备份原件未被改写", async () => {
        assert.equal(await readFile(path.join(backupRoot, "app-data", "CreationWorkspace", "workspace.sqlite"), "utf8"), "NOT_A_DATABASE");
      });
    }

    // ============ 场景 8：SQLite 未初始化（user_version=0） ============
    {
      progress("S8");
      const backupRoot = path.join(parent, "s8-backup");
      const source = path.join(parent, "s8-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const sqlitePath = path.join(backupRoot, "app-data", "CreationWorkspace", "workspace.sqlite");
      const db = new Database(sqlitePath);
      db.pragma("user_version = 0");
      db.close();
      await resyncManifest(backupRoot);
      const currentRoot = path.join(parent, "s8-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S8 未初始化数据库被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "sqlite-integrity");
      check("S8 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 9：SQLite 外键违约 ============
    {
      progress("S9");
      const backupRoot = path.join(parent, "s9-backup");
      const source = path.join(parent, "s9-source");
      await makeAppDataFixture(source);
      const sourceSqlitePath = path.join(source, "CreationWorkspace", "workspace.sqlite");
      const db = new Database(sourceSqlitePath);
      db.pragma("foreign_keys = OFF");
      db.prepare("INSERT INTO card_relations (id, project_id, from_card_id, to_card_id, relation_type, created_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run("rel-orphan", "project-missing", "card-missing-a", "card-missing-b", "rel", "2026-01-01T00:00:00.000Z");
      db.close();
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const currentRoot = path.join(parent, "s9-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S9 外键违约被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "sqlite-integrity");
      check("S9 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 10：关键 JSON 损坏（哈希一致但解析失败） ============
    {
      progress("S10");
      const backupRoot = path.join(parent, "s10-backup");
      const source = path.join(parent, "s10-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeFile(path.join(backupRoot, "app-data", "app-settings.json"), "{ truncated json", "utf8");
      await resyncManifest(backupRoot);
      const currentRoot = path.join(parent, "s10-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S10 损坏 JSON 被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "json-invalid");
      check("S10 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 11：legacy v1 清单显式拒绝 ============
    {
      progress("S11");
      const backupRoot = path.join(parent, "s11-backup");
      const source = path.join(parent, "s11-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeJson(path.join(backupRoot, "backup-manifest.json"), {
        version: 1,
        createdAt: "2025-01-01T00:00:00.000Z",
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        dataRoot: source,
        appDataPath: "app-data"
      });
      const currentRoot = path.join(parent, "s11-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S11 旧清单被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "legacy-manifest");
      check("S11 提示缺少完整性清单", () => assert.ok(rejection.message.includes("旧备份缺少完整性清单")));
      check("S11 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 12：清单路径不安全 / 重复 ============
    {
      progress("S12");
      const backupRoot = path.join(parent, "s12-backup");
      const source = path.join(parent, "s12-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const manifestPath = path.join(backupRoot, "backup-manifest.json");
      const manifest = (await readJson(manifestPath)) as BackupManifest;
      manifest.files = [...manifest.files, { path: "../evil", size: 1, sha256: "a".repeat(64) }];
      await writeJson(manifestPath, manifest);
      const currentRoot = path.join(parent, "s12-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S12 上级路径被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "unsafe-path");
      const manifest2 = (await readJson(manifestPath)) as BackupManifest;
      manifest2.files = [...manifest.files.filter((entry) => entry.path !== "../evil"), { path: "C:\\evil", size: 1, sha256: "a".repeat(64) }];
      await writeJson(manifestPath, manifest2);
      await expectReject("S12 绝对路径被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "unsafe-path");
      const manifest3 = (await readJson(manifestPath)) as BackupManifest;
      const safeFiles = manifest.files.filter((entry) => entry.path !== "../evil" && entry.path !== "C:\\evil");
      manifest3.files = [...safeFiles, safeFiles[0]!];
      await writeJson(manifestPath, manifest3);
      await expectReject("S12 重复路径被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "duplicate-path");
      check("S12 当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 13：原子交换失败 → 从检查点还原 ============
    {
      progress("S13");
      const backupRoot = path.join(parent, "s13-backup");
      const source = path.join(parent, "s13-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const currentRoot = path.join(parent, "s13-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      const rejection = await expectReject("S13 交换失败回滚", {
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: path.join(currentRoot, "AppLibrary"),
        failAppDataRename: true
      }, "swap-failed");
      check("S13 当前目录非空且内容等于检查点", async () => {
        assert.equal(existsSync(currentRoot), true);
        assert.equal(await hashDir(currentRoot), beforeHash);
      });
      check("S13 错误信息说明已还原", () => assert.ok(rejection.message.includes("检查点还原") || rejection.message.includes("还原")));
    }

    // ============ 场景 14：外置资料库损坏 → appData 替换前整体中止 ============
    {
      progress("S14");
      const backupRoot = path.join(parent, "s14-backup");
      const source = path.join(parent, "s14-source");
      const sourceLibrary = path.join(parent, "s14-library");
      await makeAppDataFixture(source);
      await makeLibraryFixture(sourceLibrary);
      await writeJson(path.join(source, "app-settings.json"), {
        version: 1,
        storage: { dataDirectory: source, libraryDirectory: sourceLibrary }
      });
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        libraryDirectory: sourceLibrary,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      await writeFile(path.join(backupRoot, "library", "library.json"), "{ broken", "utf8");
      const currentRoot = path.join(parent, "s14-current");
      const currentLibrary = path.join(parent, "s14-current-library");
      await makeAppDataFixture(currentRoot);
      await makeLibraryFixture(currentLibrary);
      const beforeAppHash = await hashDir(currentRoot);
      const beforeLibHash = await hashDir(currentLibrary);
      await expectReject("S14 资料库损坏在 appData 替换前拒绝", {
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: currentLibrary,
        libraryTarget: currentLibrary
      }, "hash-mismatch");
      check("S14 appData 未被替换", async () => assert.equal(await hashDir(currentRoot), beforeAppHash));
      check("S14 资料库未被替换", async () => assert.equal(await hashDir(currentLibrary), beforeLibHash));
      check("S14 资料库 staging 已清理", () => assert.equal(existsSync(`${currentLibrary}.restoring`), false));
    }

    // ============ 场景 15：资料库交换失败 → appData 回滚 ============
    {
      progress("S15");
      const backupRoot = path.join(parent, "s15-backup");
      const source = path.join(parent, "s15-source");
      const sourceLibrary = path.join(parent, "s15-library");
      await makeAppDataFixture(source);
      await makeLibraryFixture(sourceLibrary);
      await writeJson(path.join(source, "app-settings.json"), {
        version: 1,
        storage: { dataDirectory: source, libraryDirectory: sourceLibrary }
      });
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        libraryDirectory: sourceLibrary,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const currentRoot = path.join(parent, "s15-current");
      const currentLibrary = path.join(parent, "s15-current-library");
      await makeAppDataFixture(currentRoot);
      await makeLibraryFixture(currentLibrary);
      const beforeAppHash = await hashDir(currentRoot);
      const beforeLibHash = await hashDir(currentLibrary);
      const rejection = await expectReject("S15 资料库交换失败回滚", {
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: currentLibrary,
        libraryTarget: currentLibrary,
        failLibraryRename: true
      }, "library-swap-failed");
      check("S15 appData 回滚为原数据", async () => assert.equal(await hashDir(currentRoot), beforeAppHash));
      check("S15 资料库保持原数据", async () => assert.equal(await hashDir(currentLibrary), beforeLibHash));
      check("S15 错误信息说明已回滚", () => assert.ok(rejection.message.includes("回滚") || rejection.message.includes("还原")));
    }

    // ============ 场景 16：无创作工作区数据库的备份可恢复 ============
    {
      progress("S16");
      const backupRoot = path.join(parent, "s16-backup");
      const source = path.join(parent, "s16-source");
      await makeAppDataFixture(source, false);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const currentRoot = path.join(parent, "s16-current");
      await makeAppDataFixture(currentRoot);
      const restored = (await callRestore({
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: path.join(currentRoot, "AppLibrary")
      })) as { appDataRestored: boolean };
      check("S16 无数据库备份可恢复且内容一致", async () => {
        assert.equal(restored.appDataRestored, true);
        assert.equal(await hashDir(currentRoot), await hashDir(path.join(backupRoot, "app-data")));
      });
    }

    // ============ 场景 17：manifest v2 大小/哈希格式防御 ============
    {
      progress("S17");
      const backupRoot = path.join(parent, "s17-backup");
      const source = path.join(parent, "s17-source");
      await makeAppDataFixture(source);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const manifestPath = path.join(backupRoot, "backup-manifest.json");
      const manifest = (await readJson(manifestPath)) as BackupManifest;
      manifest.files[0] = { ...manifest.files[0]!, sha256: "zzz" };
      await writeJson(manifestPath, manifest);
      const currentRoot = path.join(parent, "s17-current");
      await makeAppDataFixture(currentRoot);
      await expectReject("S17 非法哈希格式被拒绝", { backupRoot, currentAppDataRoot: currentRoot, currentLibraryRoot: path.join(currentRoot, "AppLibrary") }, "invalid-manifest");
    }

    // ============ 场景 18：资料库危险目标在任何删除前拒绝 ============
    {
      progress("S18");
      const backupRoot = path.join(parent, "s18-backup");
      const source = path.join(parent, "s18-source");
      const sourceLibrary = path.join(parent, "s18-library");
      await makeAppDataFixture(source);
      await makeLibraryFixture(sourceLibrary);
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: source,
        libraryDirectory: sourceLibrary,
        appVersion: "0.0.0",
        platform: "test",
        arch: "test",
        createdAt: "2026-01-01T00:00:00.000Z"
      });
      const currentRoot = path.join(parent, "s18-current");
      await makeAppDataFixture(currentRoot);
      const beforeHash = await hashDir(currentRoot);
      await expectReject("S18 磁盘根目录目标被拒绝", {
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: path.parse(parent).root,
        libraryTarget: path.parse(parent).root
      }, "library-target");
      await expectReject("S18 备份目录重叠目标被拒绝", {
        backupRoot,
        currentAppDataRoot: currentRoot,
        currentLibraryRoot: backupRoot,
        libraryTarget: backupRoot
      }, "library-target");
      check("S18 危险目标拒绝后当前数据未动", async () => assert.equal(await hashDir(currentRoot), beforeHash));
    }

    // ============ 场景 19：链接条目在创建备份时明确拒绝 ============
    {
      progress("S19");
      const backupRoot = path.join(parent, "s19-backup");
      const source = path.join(parent, "s19-source");
      const linkedTarget = path.join(parent, "s19-linked-target");
      await makeAppDataFixture(source);
      await mkdir(linkedTarget, { recursive: true });
      await writeFile(path.join(linkedTarget, "sentinel.txt"), "linked", "utf8");
      await symlink(linkedTarget, path.join(source, "linked-directory"), "junction");
      let caught: unknown;
      try {
        await createBackupSnapshot({
          backupRoot,
          appDataDirectory: source,
          appVersion: "0.0.0",
          platform: "test",
          arch: "test",
          createdAt: "2026-01-01T00:00:00.000Z"
        });
      } catch (error) {
        caught = error;
      }
      check("S19 链接条目不会生成假成功备份", () => {
        assert.ok(caught instanceof BackupError);
        assert.equal(caught.code, "file-extra");
      });
    }
    await Promise.all(pendingChecks);
  } finally {
    await rm(parent, { recursive: true, force: true, maxRetries: 8, retryDelay: 250 });
  }

  const evidence = {
    tests: passed,
    allPass: failures.length === 0,
    failures
  };
  console.log(JSON.stringify(evidence));
  if (failures.length > 0) {
    console.error(failures.join("\n"));
    process.exitCode = 1;
  }
}

run().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : String(error));
  process.exitCode = 1;
});
