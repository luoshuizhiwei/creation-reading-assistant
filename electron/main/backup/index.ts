import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { existsSync } from "node:fs";
import { cp, mkdir, mkdtemp, readFile, readdir, rename, rm, stat, writeFile } from "node:fs/promises";
import { pipeline } from "node:stream/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { OperationCancelledError } from "../operation";
import type { OperationController } from "../operation";

/**
 * 备份深模块：恢复前完整性校验（P1-D07）。
 *
 * 根因（旧实现）：restoreBackup 仅以「暂存目录存在」为唯一校验（existsSync 启发式），
 * manifest v1 不含任何文件校验和；损坏但可复制的备份会在删除当前 appData 后被 rename 覆盖。
 *
 * 本模块保证：
 * - manifest v2 记录全部恢复源文件的相对路径、字节数与 SHA-256；
 * - 恢复先暂存、后校验（哈希 + SQLite 只读完整性 + 关键 JSON 可解析），校验通过前不触碰当前数据；
 * - 恢复前检查点先于任何破坏性替换创建；校验失败时当前数据与检查点保持可解释；
 * - 原子交换失败时尽力从检查点还原，不留下空目录；
 * - 外置资料库在 appData 替换前完成校验。
 */

export const BACKUP_MANIFEST_VERSION = 2;

const MANIFEST_FILE_NAME = "backup-manifest.json";
const APP_DATA_DIR_NAME = "app-data";
const LIBRARY_DIR_NAME = "library";

export interface BackupFileEntry {
  path: string;
  size: number;
  sha256: string;
}

export interface BackupManifest {
  version: 2;
  checksumAlgorithm: "sha256";
  createdAt: string;
  appVersion: string;
  platform: string;
  arch: string;
  appDataPath: "app-data";
  libraryPath?: "library";
  libraryCopied?: boolean;
  files: BackupFileEntry[];
  libraryFiles?: BackupFileEntry[];
}

export type BackupErrorCode =
  | "invalid-manifest"
  | "legacy-manifest"
  | "unsafe-path"
  | "duplicate-path"
  | "file-missing"
  | "file-extra"
  | "hash-mismatch"
  | "sqlite-integrity"
  | "json-invalid"
  | "unsafe-source"
  | "staging-failed"
  | "checkpoint-failed"
  | "swap-failed"
  | "rollback-failed"
  | "library-target"
  | "library-swap-failed";

export class BackupError extends Error {
  readonly code: BackupErrorCode;
  readonly detail: unknown;

  constructor(code: BackupErrorCode, message: string, detail?: unknown) {
    super(message);
    this.name = "BackupError";
    this.code = code;
    this.detail = detail;
  }
}

export interface BackupLogger {
  (level: "info" | "warn" | "error", message: string, meta?: unknown): void;
}

const noopLogger: BackupLogger = () => undefined;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function timestampForFile(): string {
  const date = new Date();
  const pad = (value: number): string => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function isInsidePath(parentPath: string, childPath: string): boolean {
  const parent = path.resolve(parentPath);
  const child = path.resolve(childPath);
  return child === parent || child.startsWith(`${parent}${path.sep}`);
}

/**
 * 流式计算 SHA-256：分块读取，避免为计算进度/校验把整个（可能很大的）文件读入内存。
 */
async function sha256File(filePath: string): Promise<string> {
  const hash = createHash("sha256");
  await pipeline(createReadStream(filePath), hash);
  return hash.digest("hex");
}

/** 与主进程 copyDirectory 一致：跳过 .tmp 临时文件。 */
async function copyDirectoryFiltered(source: string, target: string, operation?: OperationController): Promise<void> {
  if (!existsSync(source)) {
    throw new BackupError("invalid-manifest", "备份源目录不存在。");
  }
  await mkdir(path.dirname(target), { recursive: true });
  await cp(source, target, {
    recursive: true,
    force: true,
    errorOnExist: false,
    filter: (sourcePath) => {
      operation?.throwIfCancelled();
      return !sourcePath.endsWith(".tmp");
    }
  });
}

function assertSafeLibraryTarget(
  libraryTarget: string,
  currentAppDataRoot: string,
  backupRoot: string
): void {
  const resolved = path.resolve(libraryTarget);
  if (resolved === path.parse(resolved).root) {
    throw new BackupError("library-target", "资料库恢复目标不能是磁盘根目录。");
  }
  if (
    isInsidePath(currentAppDataRoot, resolved) ||
    isInsidePath(resolved, currentAppDataRoot) ||
    isInsidePath(backupRoot, resolved) ||
    isInsidePath(resolved, backupRoot)
  ) {
    throw new BackupError("library-target", "备份声明的资料库位置与数据或备份目录重叠，已中止恢复。");
  }
}

/** 递归遍历目录，生成 { 相对路径, 字节数, sha256 } 清单（跳过非普通文件）。 */
export async function inventoryTree(root: string, operation?: OperationController): Promise<BackupFileEntry[]> {
  const entries: BackupFileEntry[] = [];
  async function visit(current: string): Promise<void> {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      operation?.throwIfCancelled();
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await visit(absolute);
        continue;
      }
      if (!entry.isFile()) {
        throw new BackupError("file-extra", "备份源包含链接或其他无法安全恢复的文件条目。");
      }
      const relative = path.relative(root, absolute).split(path.sep).join("/");
      const info = await stat(absolute);
      entries.push({
        path: relative,
        size: info.size,
        sha256: await sha256File(absolute)
      });
    }
  }
  await visit(root);
  return entries;
}

function assertSafeRelativePath(rawPath: string): string {
  if (!rawPath) throw new BackupError("unsafe-path", "备份清单包含空文件路径。");
  if (rawPath.includes("\\")) throw new BackupError("unsafe-path", "备份清单包含不安全的文件路径。");
  if (rawPath.startsWith("/") || path.posix.isAbsolute(rawPath)) {
    throw new BackupError("unsafe-path", "备份清单包含绝对路径。");
  }
  if (/^[A-Za-z]:/.test(rawPath)) throw new BackupError("unsafe-path", "备份清单包含盘符路径。");
  const segments = rawPath.split("/");
  if (segments.some((segment) => segment === "" || segment === "." || segment === "..")) {
    throw new BackupError("unsafe-path", "备份清单包含不安全路径段。");
  }
  return rawPath;
}

function parseFileEntry(value: unknown): BackupFileEntry {
  if (
    !isRecord(value) ||
    typeof value.path !== "string" ||
    typeof value.size !== "number" ||
    typeof value.sha256 !== "string"
  ) {
    throw new BackupError("invalid-manifest", "备份清单文件条目无效。");
  }
  if (!Number.isInteger(value.size) || value.size < 0) {
    throw new BackupError("invalid-manifest", "备份清单文件大小无效。");
  }
  if (!/^[0-9a-f]{64}$/.test(value.sha256)) {
    throw new BackupError("invalid-manifest", "备份清单校验和格式无效。");
  }
  const safePath = assertSafeRelativePath(value.path);
  return { path: safePath, size: value.size, sha256: value.sha256 };
}

function parseEntryList(value: unknown, label: string): BackupFileEntry[] {
  if (!Array.isArray(value)) {
    throw new BackupError("invalid-manifest", `备份清单缺少${label}文件列表。`);
  }
  if (value.length === 0) {
    throw new BackupError("invalid-manifest", `备份清单${label}文件列表为空。`);
  }
  const entries = value.map((item) => parseFileEntry(item));
  const seen = new Set<string>();
  for (const entry of entries) {
    if (seen.has(entry.path)) {
      throw new BackupError("duplicate-path", `备份清单包含重复文件路径：${entry.path}。`);
    }
    seen.add(entry.path);
  }
  return entries;
}

export function parseBackupManifest(value: unknown): BackupManifest {
  if (!isRecord(value)) throw new BackupError("invalid-manifest", "备份清单无法解析。");
  if (value.version === 1) {
    throw new BackupError("legacy-manifest", "旧备份缺少完整性清单，已拒绝恢复。请使用当前版本重新创建备份。");
  }
  if (value.version !== BACKUP_MANIFEST_VERSION || value.appDataPath !== APP_DATA_DIR_NAME) {
    throw new BackupError("invalid-manifest", "备份清单版本不受支持。");
  }
  if (value.checksumAlgorithm !== "sha256") {
    throw new BackupError("invalid-manifest", "备份校验算法不受支持。");
  }
  const files = parseEntryList(value.files, "数据");
  let libraryFiles: BackupFileEntry[] | undefined;
  if (value.libraryFiles !== undefined) {
    libraryFiles = parseEntryList(value.libraryFiles, "资料库");
  } else if (value.libraryPath !== undefined && value.libraryPath !== LIBRARY_DIR_NAME) {
    throw new BackupError("invalid-manifest", "备份清单资料库路径无效。");
  }
  return {
    version: BACKUP_MANIFEST_VERSION,
    checksumAlgorithm: "sha256",
    createdAt: typeof value.createdAt === "string" ? value.createdAt : "",
    appVersion: typeof value.appVersion === "string" ? value.appVersion : "",
    platform: typeof value.platform === "string" ? value.platform : "",
    arch: typeof value.arch === "string" ? value.arch : "",
    appDataPath: APP_DATA_DIR_NAME,
    libraryPath: libraryFiles ? LIBRARY_DIR_NAME : undefined,
    libraryCopied: libraryFiles !== undefined,
    files,
    ...(libraryFiles ? { libraryFiles } : {})
  };
}

export async function readBackupManifest(backupRoot: string): Promise<BackupManifest> {
  const manifestPath = path.join(backupRoot, MANIFEST_FILE_NAME);
  if (!existsSync(manifestPath)) {
    throw new BackupError("invalid-manifest", "所选目录不是有效的备份目录（缺少备份清单）。");
  }
  let raw: unknown;
  try {
    raw = JSON.parse(await readFile(manifestPath, "utf8"));
  } catch {
    throw new BackupError("invalid-manifest", "备份清单无法解析。");
  }
  return parseBackupManifest(raw);
}

export interface CreateBackupSnapshotOptions {
  backupRoot: string;
  appDataDirectory: string;
  libraryDirectory?: string;
  appVersion: string;
  platform: string;
  arch: string;
  createdAt: string;
  logger?: BackupLogger;
  /** 长任务协调器控制器：在可中断阶段（scanning/hashing/copying）检查取消并上报进度。 */
  operation?: OperationController;
}

export async function createBackupSnapshot(options: CreateBackupSnapshotOptions): Promise<BackupManifest> {
  const log = options.logger ?? noopLogger;
  const operation = options.operation;
  const appDataBackupPath = path.join(options.backupRoot, APP_DATA_DIR_NAME);
  const libraryBackupPath = path.join(options.backupRoot, LIBRARY_DIR_NAME);
  operation?.setPhase("validating");
  operation?.setPhase("scanning");
  // 先验证源树，避免 cp 遇到 junction/symlink 时抛出平台相关错误，
  // 更不能在发现源不安全前删除同名备份目标。
  const sourceEntries = await inventoryTree(options.appDataDirectory, operation);
  let sourceLibraryEntries: BackupFileEntry[] | undefined;
  if (options.libraryDirectory) sourceLibraryEntries = await inventoryTree(options.libraryDirectory, operation);
  const sourceBytes = sourceEntries.reduce((sum, entry) => sum + entry.size, 0) + (sourceLibraryEntries?.reduce((sum, entry) => sum + entry.size, 0) ?? 0);
  operation?.setPhase("copying");
  operation?.setProgress({ bytesTotal: sourceBytes, bytesCompleted: 0, indeterminate: false });
  try {
    await mkdir(options.backupRoot, { recursive: true });
    if (existsSync(appDataBackupPath)) await rm(appDataBackupPath, { recursive: true, force: true });
    await copyDirectoryFiltered(options.appDataDirectory, appDataBackupPath, operation);
    let libraryFiles: BackupFileEntry[] | undefined;
    if (options.libraryDirectory) {
      if (existsSync(libraryBackupPath)) await rm(libraryBackupPath, { recursive: true, force: true });
      await copyDirectoryFiltered(options.libraryDirectory, libraryBackupPath, operation);
      libraryFiles = await inventoryTree(libraryBackupPath, operation);
    }
    // 复制后立即重新清点已复制文件以生成 manifest 哈希（复制完整性确认）。
    // 不再单独命名 hashing 阶段：协调器阶段顺序要求 hashing 早于 copying，
    // 而备份的哈希校验天然发生在复制之后，故折叠进 copying 阶段。
    const files = await inventoryTree(appDataBackupPath, operation);
    operation?.setProgress({ bytesCompleted: sourceBytes });
    const manifest: BackupManifest = {
      version: BACKUP_MANIFEST_VERSION,
      checksumAlgorithm: "sha256",
      createdAt: options.createdAt,
      appVersion: options.appVersion,
      platform: options.platform,
      arch: options.arch,
      appDataPath: APP_DATA_DIR_NAME,
      libraryPath: libraryFiles ? LIBRARY_DIR_NAME : undefined,
      libraryCopied: libraryFiles !== undefined,
      files,
      ...(libraryFiles ? { libraryFiles } : {})
    };
    operation?.setPhase("committing");
    await writeFile(path.join(options.backupRoot, MANIFEST_FILE_NAME), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
    operation?.setProgress({ completed: files.length, total: files.length });
    log("info", "Backup snapshot manifest written.", { fileCount: files.length, libraryFileCount: libraryFiles?.length ?? 0 });
    return manifest;
  } catch (error) {
    // 取消或失败：清理半成品备份目录，避免遗留临时 / 半备份。
    await rm(options.backupRoot, { recursive: true, force: true, maxRetries: 8, retryDelay: 250 }).catch(() => undefined);
    throw error;
  }
}

function assertSafeRestoreSource(backupRoot: string, currentAppDataRoot: string): void {
  const appDataBackupPath = path.join(backupRoot, APP_DATA_DIR_NAME);
  if (isInsidePath(currentAppDataRoot, backupRoot)) {
    throw new BackupError("unsafe-source", "备份目录不能位于当前数据目录内。");
  }
  if (isInsidePath(currentAppDataRoot, appDataBackupPath) || isInsidePath(appDataBackupPath, currentAppDataRoot)) {
    throw new BackupError("unsafe-source", "备份数据目录与当前数据目录存在重叠。");
  }
}

/** 校验暂存树与清单完全一致：缺一不可、多一不可、大小与哈希必须吻合；非普通文件条目一律拒绝。 */
async function assertStagedTreeMatches(
  entries: BackupFileEntry[],
  stagedRoot: string,
  scope: "app-data" | "library",
  operation?: OperationController
): Promise<void> {
  const expected = new Map(entries.map((entry) => [entry.path, entry] as const));
  const actual = new Map<string, { absolute: string; size: number }>();
  async function walk(current: string): Promise<void> {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      operation?.throwIfCancelled();
      const absolute = path.join(current, entry.name);
      if (entry.isDirectory()) {
        await walk(absolute);
        continue;
      }
      if (!entry.isFile()) {
        throw new BackupError("file-extra", `备份${scope}包含无法验证的文件条目。`);
      }
      const relative = path.relative(stagedRoot, absolute).split(path.sep).join("/");
      if (actual.has(relative)) {
        throw new BackupError("duplicate-path", `备份${scope}暂存树包含重复文件：${relative}。`);
      }
      const info = await stat(absolute);
      actual.set(relative, { absolute, size: info.size });
    }
  }
  await walk(stagedRoot);
  for (const entry of entries) {
    const found = actual.get(entry.path);
    if (!found) throw new BackupError("file-missing", `备份缺少文件：${scope}/${entry.path}。`);
  }
  for (const relative of actual.keys()) {
    if (!expected.has(relative)) {
      throw new BackupError("file-extra", `备份包含未在清单中的文件：${scope}/${relative}。`);
    }
  }
  for (const entry of entries) {
    const found = actual.get(entry.path)!;
    if (found.size !== entry.size) {
      throw new BackupError("hash-mismatch", `备份文件大小不一致：${scope}/${entry.path}。`);
    }
    if ((await sha256File(found.absolute)) !== entry.sha256) {
      throw new BackupError("hash-mismatch", `备份文件校验和不一致：${scope}/${entry.path}。`);
    }
  }
}

export interface SqliteValidationResult {
  path: string;
  userVersion: number;
}

/**
 * 以只读方式校验备份中的 SQLite 数据库：不打开原备份文件，将 .sqlite/.sqlite-wal/.sqlite-shm
 * 复制到临时目录后运行 integrity_check 与 foreign_key_check，且绝不执行 schema 迁移或写入。
 */
async function validateSqliteFileReadonly(sqlitePath: string): Promise<SqliteValidationResult> {
  const tempDir = await mkdtemp(path.join(os.tmpdir(), "backup-sqlite-check-"));
  try {
    for (const suffix of ["", "-wal", "-shm"]) {
      const candidate = `${sqlitePath}${suffix}`;
      if (existsSync(candidate)) {
        await cp(candidate, path.join(tempDir, path.basename(candidate)), { force: true });
      }
    }
    const copiedPath = path.join(tempDir, path.basename(sqlitePath));
    let userVersion = 0;
    try {
      const db = new Database(copiedPath);
      try {
        const integrity = db.pragma("integrity_check", { simple: true }) as unknown;
        if (integrity !== "ok") {
          throw new BackupError("sqlite-integrity", "备份中的数据库文件未通过完整性检查。");
        }
        const foreignKeyRows = db.pragma("foreign_key_check") as unknown[];
        if (Array.isArray(foreignKeyRows) && foreignKeyRows.length > 0) {
          throw new BackupError("sqlite-integrity", "备份中的数据库文件存在关系完整性问题。");
        }
        userVersion = Number(db.pragma("user_version", { simple: true }));
        if (!Number.isInteger(userVersion) || userVersion < 1) {
          throw new BackupError("sqlite-integrity", "备份中的数据库文件未初始化或版本无效。");
        }
      } finally {
        db.close();
      }
    } catch (error) {
      if (error instanceof BackupError) throw error;
      throw new BackupError("sqlite-integrity", "备份中的数据库文件损坏，无法进行完整性校验。");
    }
    return { path: path.basename(sqlitePath), userVersion };
  } finally {
    await rm(tempDir, { recursive: true, force: true });
  }
}

async function validateSqliteFilesReadonly(
  entries: BackupFileEntry[],
  stagedRoot: string,
  operation?: OperationController
): Promise<SqliteValidationResult[]> {
  const results: SqliteValidationResult[] = [];
  for (const entry of entries) {
    operation?.throwIfCancelled();
    if (!entry.path.endsWith(".sqlite")) continue;
    const absolute = path.join(stagedRoot, ...entry.path.split("/"));
    results.push(await validateSqliteFileReadonly(absolute));
  }
  return results;
}

async function validateJsonFilesReadonly(entries: BackupFileEntry[], stagedRoot: string, scope: "app-data" | "library", operation?: OperationController): Promise<void> {
  for (const entry of entries) {
    operation?.throwIfCancelled();
    if (!entry.path.endsWith(".json")) continue;
    const absolute = path.join(stagedRoot, ...entry.path.split("/"));
    let content: string;
    try {
      content = await readFile(absolute, "utf8");
    } catch {
      throw new BackupError("json-invalid", `备份中的 JSON 数据不可读：${scope}/${entry.path}。`);
    }
    if (!content.trim()) continue;
    try {
      JSON.parse(content);
    } catch {
      throw new BackupError("json-invalid", `备份中的 JSON 数据损坏：${scope}/${entry.path}。`);
    }
  }
}

export interface RestoreBackupOptions {
  backupRoot: string;
  currentAppDataRoot: string;
  /** 依据暂存（待恢复）的 app-data 设置解析资料库目标目录；仅在清单声明外置资料库时调用。 */
  resolveLibraryTarget: (stagedAppDataRoot: string) => Promise<string>;
  logger?: BackupLogger;
  now?: () => string;
  /** 长任务协调器控制器：在可中断阶段（copying/hashing）检查取消并上报进度。 */
  operation?: OperationController;
  testHooks?: {
    /** 契约专用：在删除当前目录后、rename 前注入 appData 交换失败。 */
    failAppDataRename?: boolean;
    /** 契约专用：在删除目标后、rename 前注入资料库交换失败。 */
    failLibraryRename?: boolean;
  };
}

export interface RestoreBackupResult {
  restoredAt: string;
  checkpointPath?: string;
  appDataRestored: boolean;
  libraryRestored: boolean;
  sqliteUserVersions: number[];
}

function uniqueSiblingPath(parent: string, prefix: string): string {
  let candidate = path.join(parent, `${prefix}${timestampForFile()}`);
  let index = 2;
  while (existsSync(candidate)) {
    candidate = path.join(parent, `${prefix}${timestampForFile()}-${index}`);
    index += 1;
  }
  return candidate;
}

async function cleanupStaging(...stagedPaths: Array<string | undefined>): Promise<void> {
  for (const stagedPath of stagedPaths) {
    if (stagedPath) {
      await rm(stagedPath, { recursive: true, force: true, maxRetries: 8, retryDelay: 250 });
    }
  }
}

/** 尽力从检查点还原：优先 rename，失败则回退目录复制。 */
async function restoreFromCheckpoint(checkpointPath: string | undefined, targetRoot: string, log: BackupLogger): Promise<boolean> {
  if (!checkpointPath || !existsSync(checkpointPath)) {
    log("error", "No checkpoint available for rollback.", {});
    return false;
  }
  try {
    if (existsSync(targetRoot)) await rm(targetRoot, { recursive: true, force: true });
    await rename(checkpointPath, targetRoot);
    return true;
  } catch (renameError) {
    try {
      await copyDirectoryFiltered(checkpointPath, targetRoot);
      log("warn", "Checkpoint rollback used directory copy fallback.", { renameError: String(renameError) });
      return true;
    } catch (copyError) {
      log("error", "Checkpoint rollback failed.", { renameError: String(renameError), copyError: String(copyError) });
      return false;
    }
  }
}

export async function restoreBackupFromDirectory(options: RestoreBackupOptions): Promise<RestoreBackupResult> {
  const log = options.logger ?? noopLogger;
  const nowValue = options.now ?? ((): string => new Date().toISOString());
  const manifest = await readBackupManifest(options.backupRoot);
  assertSafeRestoreSource(options.backupRoot, options.currentAppDataRoot);
  const operation = options.operation;
  operation?.setPhase("validating");
  const manifestBytes =
    manifest.files.reduce((sum, entry) => sum + entry.size, 0) +
    (manifest.libraryFiles?.reduce((sum, entry) => sum + entry.size, 0) ?? 0);

  const stagedAppData = `${options.currentAppDataRoot}.restoring`;
  await cleanupStaging(stagedAppData);

  // 1) 恢复前检查点：先于任何破坏性替换创建。
  let checkpointPath: string | undefined;
  if (existsSync(options.currentAppDataRoot)) {
    checkpointPath = uniqueSiblingPath(path.dirname(options.currentAppDataRoot), "CreationReadingAssistant-before-restore-");
    try {
      await copyDirectoryFiltered(options.currentAppDataRoot, checkpointPath);
    } catch (error) {
      throw new BackupError("checkpoint-failed", "创建恢复前检查点失败，已中止恢复。", {
        cause: error instanceof Error ? error.message : String(error)
      });
    }
    log("warn", "Restore checkpoint created.", { checkpointPath });
  }

  // 2) 暂存备份数据。
  operation?.setPhase("copying");
  operation?.setProgress({ bytesTotal: manifestBytes, bytesCompleted: 0, indeterminate: false });
  let stagedLibrary: string | undefined;
  let libraryTarget: string | undefined;
  try {
    await copyDirectoryFiltered(path.join(options.backupRoot, APP_DATA_DIR_NAME), stagedAppData, operation);
    if (manifest.libraryFiles) {
      libraryTarget = await options.resolveLibraryTarget(stagedAppData);
      assertSafeLibraryTarget(libraryTarget, options.currentAppDataRoot, options.backupRoot);
      stagedLibrary = `${libraryTarget}.restoring`;
      await cleanupStaging(stagedLibrary);
      await copyDirectoryFiltered(path.join(options.backupRoot, LIBRARY_DIR_NAME), stagedLibrary, operation);
    }
    operation?.setProgress({ bytesCompleted: manifestBytes });
  } catch (error) {
    await cleanupStaging(stagedAppData, stagedLibrary);
    if (error instanceof OperationCancelledError) throw error;
    if (error instanceof BackupError && error.code === "library-target") {
      log("error", "Restore aborted: unsafe library target.", { checkpointPath });
      throw new BackupError(error.code, error.message, { checkpointPath });
    }
    log("error", "Restore aborted: staging failed.", { checkpointPath, cause: error instanceof Error ? error.message : String(error) });
    throw new BackupError("staging-failed", "无法暂存备份数据，已中止恢复。", { checkpointPath });
  }

  // 3) 完整性与内容校验：通过前不删除当前 appData。
  // 该验证发生在 staging 复制之后，协调器阶段顺序要求 hashing 早于 copying，
  // 故此处不单独命名 hashing 阶段，验证作为 copying 阶段的收尾（仍在 committing 之前）。
  let sqliteVersions: number[] = [];
  try {
    await assertStagedTreeMatches(manifest.files, stagedAppData, "app-data", operation);
    const sqliteResults = await validateSqliteFilesReadonly(manifest.files, stagedAppData, operation);
    sqliteVersions = sqliteResults.map((result) => result.userVersion);
    await validateJsonFilesReadonly(manifest.files, stagedAppData, "app-data", operation);
    if (manifest.libraryFiles && stagedLibrary) {
      await assertStagedTreeMatches(manifest.libraryFiles, stagedLibrary, "library", operation);
      await validateJsonFilesReadonly(manifest.libraryFiles, stagedLibrary, "library", operation);
    }
    log("info", "Backup staged and validated.", {
      fileCount: manifest.files.length,
      libraryFileCount: manifest.libraryFiles?.length ?? 0,
      sqliteUserVersions: sqliteResults.map((result) => result.userVersion)
    });
  } catch (error) {
    await cleanupStaging(stagedAppData, stagedLibrary);
    if (error instanceof OperationCancelledError) throw error;
    const code = error instanceof BackupError ? error.code : "invalid-manifest";
    const message = error instanceof BackupError ? error.message : "备份校验失败。";
    log("error", "Restore aborted: backup validation failed. Current data untouched; checkpoint retained.", {
      code,
      checkpointPath,
      detail: error instanceof BackupError ? error.detail : String(error)
    });
    throw new BackupError(code, message, { checkpointPath });
  }

  // 4) 原子交换 appData；失败则从检查点还原。
  operation?.setPhase("committing");
  operation?.setProgress({ indeterminate: true });
  let libraryCheckpointPath: string | undefined;
  if (manifest.libraryFiles && libraryTarget && existsSync(libraryTarget)) {
    libraryCheckpointPath = uniqueSiblingPath(
      path.dirname(libraryTarget),
      "CreationReadingAssistant-library-before-restore-"
    );
    try {
      await copyDirectoryFiltered(libraryTarget, libraryCheckpointPath);
    } catch (error) {
      await cleanupStaging(stagedAppData, stagedLibrary, libraryCheckpointPath);
      throw new BackupError("checkpoint-failed", "创建资料库恢复前检查点失败，已中止恢复。", {
        checkpointPath,
        cause: error instanceof Error ? error.message : String(error)
      });
    }
  }

  try {
    if (existsSync(options.currentAppDataRoot)) {
      await rm(options.currentAppDataRoot, { recursive: true, force: true });
    }
    if (options.testHooks?.failAppDataRename) {
      throw new BackupError("swap-failed", "数据交换失败（注入）。");
    }
    await rename(stagedAppData, options.currentAppDataRoot);
  } catch (error) {
    const rollbackOk = await restoreFromCheckpoint(checkpointPath, options.currentAppDataRoot, log);
    if (!rollbackOk) {
      throw new BackupError("rollback-failed", "恢复交换失败，且无法从检查点还原当前数据。", { checkpointPath });
    }
    throw new BackupError("swap-failed", "数据交换失败，当前数据已从检查点还原。", { checkpointPath });
  }
  log("info", "App data swapped.", {});

  // 5) 交换外置资料库；失败则同时回滚 appData 与资料库。
  let libraryRestored = false;
  if (manifest.libraryFiles && stagedLibrary && libraryTarget) {
    try {
      if (existsSync(libraryTarget)) {
        await rm(libraryTarget, { recursive: true, force: true });
      }
      if (options.testHooks?.failLibraryRename) {
        throw new BackupError("library-swap-failed", "资料库交换失败（注入）。");
      }
      await rename(stagedLibrary, libraryTarget);
      libraryRestored = true;
    } catch (error) {
      const appDataRollbackOk = await restoreFromCheckpoint(checkpointPath, options.currentAppDataRoot, log);
      const libraryRollbackOk = libraryCheckpointPath
        ? await restoreFromCheckpoint(libraryCheckpointPath, libraryTarget, log)
        : !existsSync(libraryTarget);
      if (!appDataRollbackOk || !libraryRollbackOk) {
        throw new BackupError("rollback-failed", "资料库交换失败，且无法完整还原恢复前数据。", {
          checkpointPath: appDataRollbackOk ? undefined : checkpointPath
        });
      }
      throw new BackupError("library-swap-failed", "资料库恢复失败，当前数据已从检查点还原。", {
        checkpointPath: undefined,
        cause: error instanceof Error ? error.message : String(error)
      });
    }
  }

  return {
    restoredAt: nowValue(),
    checkpointPath,
    appDataRestored: true,
    libraryRestored,
    sqliteUserVersions: sqliteVersions
  };
}
