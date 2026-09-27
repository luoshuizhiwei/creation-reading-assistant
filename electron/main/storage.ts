/**
 * 主进程存储底座（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：数据根/库根解析（指针·便携·回退三级策略）与生效根状态、全部数据文件路径、
 * JSON 读写（原子写 + .bak 回退）、文件级写锁、运行日志、设备身份与同步元数据、
 * 目录迁移安全校验。行为逐字保持；生效数据根的跨迁移改写经 setActiveDataRoot 提交。
 */
import { app } from "electron";
import { appendFile, copyFile, cp, mkdir, readFile, rename, rm, unlink, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import type { AppSettings, StorageSettings } from "../../src/types/settings";
import type { DeviceInfo } from "../../src/types/sync";

/** 当前生效的数据根/库根（启动解析、存储设置与目录迁移改写）。 */
let activeDataRoot: string | undefined;
let activeLibraryRoot: string | undefined;

/** 迁移提交点：目录复制完成后改写生效数据根。 */
export function setActiveDataRoot(root: string): void {
  activeDataRoot = root;
}

/** 桌面设备 ID 的模块级缓存（readSyncState/writeSyncState 维护）。 */
let desktopDeviceId: string | undefined;

export const EPUB_PROTOCOL_SCHEME = "novel-workbench-epub";

export const now = (): string => new Date().toISOString();

export const makeId = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${crypto.randomBytes(4).toString("hex")}`;

// Simple Promise-chain mutex for file-level write serialization (no external deps)
export const fileWriteChains = new Map<string, Promise<unknown>>();
export function withFileLock<T>(filePath: string, fn: () => Promise<T>): Promise<T> {
  const previous = fileWriteChains.get(filePath) ?? Promise.resolve();
  const next = previous.then(fn, fn);
  fileWriteChains.set(filePath, next);
  return next.then(
    (result) => {
      if (fileWriteChains.get(filePath) === next) fileWriteChains.delete(filePath);
      return result;
    },
    (error) => {
      if (fileWriteChains.get(filePath) === next) fileWriteChains.delete(filePath);
      throw error;
    }
  );
}


export function fallbackDataRoot(): string {
  return path.join(app.getPath("userData"), "NovelWorkbench");
}


/**
 * 视觉捕获隔离钩子：仅开发态（未打包）且显式设置 CREATION_READER_CAPTURE_PROFILE 时生效
 * （自动化截图验收用）。打包环境即使继承该环境变量也必须继续使用正常 userData。
 */
export const captureProfileDir = !app.isPackaged ? process.env.CREATION_READER_CAPTURE_PROFILE : undefined;
if (captureProfileDir) {
  app.setPath("userData", captureProfileDir);
}


export function portableDataRoot(): string {
  const base = app.isPackaged ? path.dirname(app.getPath("exe")) : process.cwd();
  return path.join(base, "data");
}


export function storagePointerPath(): string {
  return path.join(app.getPath("userData"), "CreationReadingAssistant-storage.json");
}


export function appDataRoot(): string {
  return activeDataRoot ?? fallbackDataRoot();
}


export function appLibraryRoot(): string {
  return activeLibraryRoot ?? path.join(appDataRoot(), "AppLibrary");
}


export function appLibraryFilesRoot(): string {
  return path.join(appLibraryRoot(), "files");
}


export function appLibraryCoversRoot(): string {
  return path.join(appLibraryRoot(), "covers");
}


export function appLibrarySearchIndexRoot(): string {
  return path.join(appLibraryRoot(), "search-index");
}


export function safeBookIdForFile(bookId: string): string {
  if (!/^[A-Za-z0-9_-]+$/.test(bookId)) throw new Error("Invalid book id.");
  return bookId;
}


export function epubSearchIndexPath(bookId: string): string {
  return path.join(appLibrarySearchIndexRoot(), `${safeBookIdForFile(bookId)}.json`);
}


export function epubUrlForBook(bookId: string): string {
  return `${EPUB_PROTOCOL_SCHEME}://book/${encodeURIComponent(bookId)}.epub`;
}


export function bookIdFromEpubProtocolUrl(rawUrl: string): string | null {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== `${EPUB_PROTOCOL_SCHEME}:` || url.hostname !== "book") return null;
    const rawName = path.posix.basename(url.pathname).replace(/\.epub$/i, "");
    return rawName ? decodeURIComponent(rawName) : null;
  } catch {
    return null;
  }
}


export function appSettingsPath(): string {
  return path.join(appDataRoot(), "app-settings.json");
}


export function inspirationsPath(): string {
  return path.join(appDataRoot(), "inspirations.json");
}


export function aiSecretsPath(): string {
  return path.join(appDataRoot(), "ai-secrets.json");
}


export function libraryPath(): string {
  return path.join(appLibraryRoot(), "library.json");
}


export function readingProgressPath(): string {
  return path.join(appLibraryRoot(), "reading-progress.json");
}


export function readingSessionsPath(): string {
  return path.join(appLibraryRoot(), "reading-sessions.json");
}


export function readerSettingsPath(): string {
  return path.join(appLibraryRoot(), "settings.json");
}


export function highlightsPath(): string {
  return path.join(appLibraryRoot(), "highlights.json");
}


export function bookmarksPath(): string {
  return path.join(appLibraryRoot(), "bookmarks.json");
}


export function logsRoot(): string {
  return path.join(appDataRoot(), "logs");
}


export function runtimeStatePath(): string {
  return path.join(appDataRoot(), "runtime-state.json");
}


export function syncStatePath(): string {
  return path.join(appDataRoot(), "sync-state.json");
}


export async function ensureDir(dirPath: string): Promise<void> {
  await mkdir(dirPath, { recursive: true });
}


export async function renameReplacingExistingFile(sourcePath: string, targetPath: string): Promise<void> {
  try {
    await rename(sourcePath, targetPath);
  } catch (error) {
    const code = isRecord(error) && typeof error.code === "string" ? error.code : undefined;
    if (code === "EEXIST" || code === "EPERM") {
      await rm(targetPath, { force: true });
      await rename(sourcePath, targetPath);
      return;
    }
    throw error;
  }
}


export async function writeAtomic(filePath: string, content: string | Buffer): Promise<void> {
  await ensureDir(path.dirname(filePath));
  const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
  await writeFile(tempPath, content);
  await renameReplacingExistingFile(tempPath, filePath);
}


export async function readJson<T>(filePath: string, fallback: T): Promise<T> {
  if (!existsSync(filePath)) return fallback;
  try {
    const content = await readFile(filePath, "utf-8");
    if (!content.trim()) return fallback;
    return JSON.parse(content) as T;
  } catch {
    const backupPath = `${filePath}.bak`;
    if (!existsSync(backupPath)) return fallback;
    try {
      const backupContent = await readFile(backupPath, "utf-8");
      if (!backupContent.trim()) return fallback;
      return JSON.parse(backupContent) as T;
    } catch (backupError) {
      await writeLog("warn", "readJson backup parse failed.", {
        filePath,
        backupPath,
        error: backupError instanceof Error ? backupError.message : String(backupError)
      });
      return fallback;
    }
  }
}


export async function writeJson<T>(filePath: string, data: T): Promise<void> {
  if (existsSync(filePath)) {
    await copyFile(filePath, `${filePath}.bak`);
  }
  await writeAtomic(filePath, `${JSON.stringify(data, null, 2)}\n`);
}


export interface SyncStateFile {
  version: 1;
  deviceId: string;
  devices: Array<DeviceInfo & { authTokenHash?: string }>;
  updatedAt: string;
}


export function fallbackDeviceId(): string {
  const seed = `${app.getPath("userData")}:${app.getName()}`;
  return `desktop-${crypto.createHash("sha256").update(seed).digest("hex").slice(0, 12)}`;
}


export function currentDeviceId(): string {
  desktopDeviceId ??= fallbackDeviceId();
  return desktopDeviceId;
}


export async function readSyncState(): Promise<SyncStateFile> {
  const fallback: SyncStateFile = {
    version: 1,
    deviceId: currentDeviceId(),
    devices: [],
    updatedAt: now()
  };
  const raw = await readJson<unknown>(syncStatePath(), fallback);
  if (!isRecord(raw)) return fallback;
  const deviceId = optionalString(raw.deviceId) ?? fallback.deviceId;
  desktopDeviceId = deviceId;
  return {
    version: 1,
    deviceId,
    devices: Array.isArray(raw.devices)
      ? raw.devices
          .filter(isRecord)
          .map((item) => ({
            deviceId: optionalString(item.deviceId) ?? makeId("device"),
            name: optionalString(item.name) ?? "未知设备",
            platform:
              item.platform === "android" || item.platform === "ios" || item.platform === "web" || item.platform === "desktop"
                ? item.platform
                : "android",
            pairedAt: optionalString(item.pairedAt) ?? now(),
            lastSeenAt: optionalString(item.lastSeenAt) ?? now(),
            authTokenHash: optionalString(item.authTokenHash)
          }))
      : [],
    updatedAt: optionalString(raw.updatedAt) ?? now()
  };
}


export async function writeSyncState(state: SyncStateFile): Promise<void> {
  desktopDeviceId = state.deviceId;
  await writeJson(syncStatePath(), { ...state, updatedAt: now() });
}


export async function getOrCreateDeviceId(): Promise<string> {
  const state = await readSyncState();
  if (!state.deviceId) {
    state.deviceId = fallbackDeviceId();
    await writeSyncState(state);
  }
  desktopDeviceId = state.deviceId;
  return state.deviceId;
}


export function withSyncMetadata<T extends { updatedAt?: string; revision?: number; deviceId?: string; deletedAt?: string }>(
  value: T,
  fallbackUpdatedAt = now()
): T & { revision: number; deviceId: string; deletedAt?: string } {
  return {
    ...value,
    revision: typeof value.revision === "number" && value.revision > 0 ? Math.floor(value.revision) : 1,
    deviceId: typeof value.deviceId === "string" && value.deviceId ? value.deviceId : currentDeviceId(),
    deletedAt: typeof value.deletedAt === "string" ? value.deletedAt : undefined,
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : fallbackUpdatedAt
  };
}


export function nextSyncMetadata<T extends { revision?: number }>(value?: T): { revision: number; deviceId: string } {
  return {
    revision: Math.max(1, value?.revision ?? 0) + 1,
    deviceId: currentDeviceId()
  };
}


export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}


export function logFilePath(date = new Date()): string {
  const day = date.toISOString().slice(0, 10);
  return path.join(logsRoot(), `app-${day}.log`);
}


export function sanitizeLogMeta(meta?: unknown): unknown {
  if (!meta) return undefined;
  if (meta instanceof Error) return { name: meta.name, message: meta.message, stack: meta.stack };
  if (typeof meta === "string") return meta;
  if (isRecord(meta)) {
    return Object.fromEntries(
      Object.entries(meta).map(([key, value]) => {
        if (value === undefined) return [key, undefined];
        if (/token|secret|password|credential/i.test(key)) return [key, "[redacted]"];
        if (value instanceof Error) return [key, sanitizeLogMeta(value)];
        if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return [key, value];
        return [key, String(value)];
      })
    );
  }
  return String(meta);
}


export async function writeLog(level: "info" | "warn" | "error", message: string, meta?: unknown): Promise<void> {
  try {
    await ensureDir(logsRoot());
    const entry = {
      at: now(),
      level,
      message,
      meta: sanitizeLogMeta(meta)
    };
    await appendFile(logFilePath(), `${JSON.stringify(entry)}\n`, "utf-8");
  } catch {
    // Logging must never break core writing or reading workflows.
  }
}


export function requireString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${fieldName} is required.`);
  }
  return value.trim();
}


export function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}


export function normalizeTags(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string").map((item) => item.trim()).filter(Boolean);
}


export function timestampForFile(): string {
  const date = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}


export function isInsidePath(parentPath: string, childPath: string): boolean {
  const parent = path.resolve(parentPath);
  const child = path.resolve(childPath);
  // On Windows, path.resolve already normalizes drive letter casing;
  // we compare directly without toLowerCase to preserve Linux case-sensitivity.
  return child === parent || child.startsWith(`${parent}${path.sep}`);
}


export async function copyDirectory(source: string, target: string): Promise<void> {
  if (!existsSync(source)) return;
  await ensureDir(path.dirname(target));
  await cp(source, target, {
    recursive: true,
    force: true,
    errorOnExist: false,
    filter: (sourcePath) => !sourcePath.endsWith(".tmp")
  });
}


export async function isDirectoryWritable(dirPath: string): Promise<boolean> {
  try {
    await ensureDir(dirPath);
    const probePath = path.join(dirPath, `.write-test-${process.pid}-${Date.now()}`);
    await writeFile(probePath, "ok", "utf-8");
    await rm(probePath, { force: true });
    return true;
  } catch {
    return false;
  }
}


export async function readStoragePointer(): Promise<string | undefined> {
  const pointer = await readJson<unknown>(storagePointerPath(), {});
  if (!isRecord(pointer)) return undefined;
  return optionalString(pointer.dataDirectory);
}


export async function writeStoragePointer(dataDirectory: string): Promise<void> {
  await writeJson(storagePointerPath(), {
    version: 1,
    dataDirectory,
    updatedAt: now()
  });
}


export async function resolveInitialDataRoot(): Promise<void> {
  if (captureProfileDir) {
    activeDataRoot = fallbackDataRoot();
    return;
  }
  const pointedRoot = await readStoragePointer();
  if (pointedRoot && (await isDirectoryWritable(pointedRoot))) {
    activeDataRoot = pointedRoot;
    return;
  }

  const portableRoot = portableDataRoot();
  const fallbackRoot = fallbackDataRoot();
  const hasLegacyData = existsSync(fallbackRoot);
  const hasPortableData = existsSync(portableRoot);
  if (hasLegacyData && !hasPortableData) {
    activeDataRoot = fallbackRoot;
    return;
  }
  activeDataRoot = (await isDirectoryWritable(portableRoot)) ? portableRoot : fallbackRoot;
}


export async function setActiveStorageFromSettings(settings: AppSettings): Promise<void> {
  activeDataRoot = settings.storage.dataDirectory || appDataRoot();
  activeLibraryRoot = settings.storage.libraryDirectory || path.join(appDataRoot(), "AppLibrary");
  await ensureDir(appLibraryFilesRoot());
  await ensureDir(appLibraryCoversRoot());
  await ensureDir(appLibrarySearchIndexRoot());
}


export function storageModeForDataRoot(dataDirectory: string): StorageSettings["storageMode"] {
  if (path.resolve(dataDirectory).toLowerCase() === path.resolve(portableDataRoot()).toLowerCase()) return "portable";
  if (path.resolve(dataDirectory).toLowerCase() === path.resolve(fallbackDataRoot()).toLowerCase()) return "fallback";
  return "custom";
}


export function isSystemProtectedDirectory(target: string): boolean {
  if (process.platform !== "win32") return false;
  const t = path.resolve(target).toLowerCase();
  const sysRoots = [
    (process.env.WINDIR || "c:\\windows").toLowerCase(),
    (process.env.ProgramFiles || "c:\\program files").toLowerCase(),
    (process.env.ProgramFilesX86 || "c:\\program files (x86)").toLowerCase(),
    (process.env.ProgramData || "c:\\programdata").toLowerCase()
  ];
  return sysRoots.some((root) => t === root || isInsidePath(root, t));
}


export function assertSafeMigrationTarget(currentRoot: string, targetRoot: string, label: string): void {
  const current = path.resolve(currentRoot);
  const target = path.resolve(targetRoot);
  if (current === target) throw new Error(`${label}已经在这个位置。`);
  if (isInsidePath(current, target) || isInsidePath(target, current)) {
    throw new Error(`${label}不能迁移到当前目录内部或父目录，请选择一个独立目录。`);
  }
  if (isSystemProtectedDirectory(target)) {
    throw new Error(`${label}不能迁移到系统受保护目录，请选择用户数据目录。`);
  }
}


export function relocatePathInsideRoot(filePath: string | undefined, oldRoot: string, newRoot: string): string | undefined {
  if (!filePath) return filePath;
  if (!isInsidePath(oldRoot, filePath)) return filePath;
  const relative = path.relative(path.resolve(oldRoot), path.resolve(filePath));
  return path.join(newRoot, relative);
}


export async function unlinkManagedFileIfPresent(managedRoot: string, filePath: string | undefined, context: Record<string, unknown>): Promise<void> {
  if (!filePath) return;
  const managedRootPath = path.resolve(managedRoot);
  const targetPath = path.resolve(filePath);
  if (!isInsidePath(managedRootPath, targetPath)) {
    await writeLog("warn", "Managed file cleanup skipped because target escapes managed root.", {
      ...context,
      managedRoot: managedRootPath,
      targetPath
    });
    return;
  }
  try {
    await unlink(targetPath);
  } catch (error) {
    const code = isRecord(error) && typeof error.code === "string" ? error.code : undefined;
    if (code !== "ENOENT") {
      await writeLog("warn", "Managed file cleanup failed.", {
        ...context,
        managedRoot: managedRootPath,
        targetPath,
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }
}

