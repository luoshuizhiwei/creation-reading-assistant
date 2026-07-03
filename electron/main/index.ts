import { app, BrowserWindow, dialog, ipcMain, protocol, safeStorage, session, shell } from "electron";
import type { OpenDialogOptions } from "electron";
import { appendFile, copyFile, cp, mkdir, readFile, rename, rm, stat, unlink, writeFile } from "node:fs/promises";
import { existsSync, writeFileSync } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { createServer } from "node:http";
import type { IncomingMessage, Server, ServerResponse } from "node:http";
import { networkInterfaces } from "node:os";
import { parseEpubFile } from "./epub-metadata";
import type {
  BookFormat,
  EndReadingSessionInput,
  EpubSearchIndex,
  EpubSearchIndexItem,
  GetReadingSessionsInput,
  LibraryBook,
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderSettings,
  ReadingLocation,
  ReadingProgress,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  UpdateReadingSessionInput
} from "../../src/types/library";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations, StorageSettings } from "../../src/types/settings";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, AIRunAction, SaveAIApiKeyInput } from "../../src/types/ai";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  InspirationItem,
  InspirationSourceSnapshot,
  InspirationSourceLocation,
  InspirationStatus,
  InspirationType,
  InspirationVariant,
  UpdateInspirationInput
} from "../../src/types/inspiration";
import type { SearchQuery, SearchResult, SearchResultType } from "../../src/types/search";
import type { BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "../../src/types/maintenance";
import type {
  BookFileManifest,
  DeviceInfo,
  PairingTokenResult,
  SyncRecordType,
  SyncEnvelope,
  SyncManifest,
  SyncPullResponse,
  SyncPushPayload,
  SyncPushResult,
  SyncStatus
} from "../../src/types/sync";

let mainWindow: BrowserWindow | null = null;
let startupRecoveryInfo: StartupRecoveryInfo = {
  abnormalExit: false,
  recoveredSessionsCount: 0,
  checkedAt: new Date().toISOString()
};
let startupRecoverySeen = false;
let activeDataRoot: string | undefined;
let activeLibraryRoot: string | undefined;
let desktopDeviceId: string | undefined;
let syncServer: Server | undefined;
let syncServerPort: number | undefined;
let pairingToken: PairingTokenResult | undefined;

const EPUB_PROTOCOL_SCHEME = "novel-workbench-epub";
const MAX_SEARCH_TEXT_FILE_BYTES = 5 * 1024 * 1024;
const SYNC_CHUNK_SIZE = 1024 * 1024;
const now = (): string => new Date().toISOString();
const makeId = (prefix: string): string => `${prefix}-${Date.now().toString(36)}-${crypto.randomBytes(4).toString("hex")}`;

protocol.registerSchemesAsPrivileged([
  {
    scheme: EPUB_PROTOCOL_SCHEME,
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
      stream: true
    }
  }
]);

function fallbackDataRoot(): string {
  return path.join(app.getPath("userData"), "NovelWorkbench");
}

function portableDataRoot(): string {
  const base = app.isPackaged ? path.dirname(app.getPath("exe")) : process.cwd();
  return path.join(base, "data");
}

function storagePointerPath(): string {
  return path.join(app.getPath("userData"), "CreationReadingAssistant-storage.json");
}

function appDataRoot(): string {
  return activeDataRoot ?? fallbackDataRoot();
}

function appLibraryRoot(): string {
  return activeLibraryRoot ?? path.join(appDataRoot(), "AppLibrary");
}

function appLibraryFilesRoot(): string {
  return path.join(appLibraryRoot(), "files");
}

function appLibraryCoversRoot(): string {
  return path.join(appLibraryRoot(), "covers");
}

function appLibrarySearchIndexRoot(): string {
  return path.join(appLibraryRoot(), "search-index");
}

function safeBookIdForFile(bookId: string): string {
  if (!/^[A-Za-z0-9_-]+$/.test(bookId)) throw new Error("Invalid book id.");
  return bookId;
}

function epubSearchIndexPath(bookId: string): string {
  return path.join(appLibrarySearchIndexRoot(), `${safeBookIdForFile(bookId)}.json`);
}

function epubUrlForBook(bookId: string): string {
  return `${EPUB_PROTOCOL_SCHEME}://book/${encodeURIComponent(bookId)}.epub`;
}

function bookIdFromEpubProtocolUrl(rawUrl: string): string | null {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== `${EPUB_PROTOCOL_SCHEME}:` || url.hostname !== "book") return null;
    const rawName = path.posix.basename(url.pathname).replace(/\.epub$/i, "");
    return rawName ? decodeURIComponent(rawName) : null;
  } catch {
    return null;
  }
}

function appSettingsPath(): string {
  return path.join(appDataRoot(), "app-settings.json");
}

function inspirationsPath(): string {
  return path.join(appDataRoot(), "inspirations.json");
}

function aiSecretsPath(): string {
  return path.join(appDataRoot(), "ai-secrets.json");
}

function libraryPath(): string {
  return path.join(appLibraryRoot(), "library.json");
}

function readingProgressPath(): string {
  return path.join(appLibraryRoot(), "reading-progress.json");
}

function readingSessionsPath(): string {
  return path.join(appLibraryRoot(), "reading-sessions.json");
}

function readerSettingsPath(): string {
  return path.join(appLibraryRoot(), "settings.json");
}

function logsRoot(): string {
  return path.join(appDataRoot(), "logs");
}

function runtimeStatePath(): string {
  return path.join(appDataRoot(), "runtime-state.json");
}

function syncStatePath(): string {
  return path.join(appDataRoot(), "sync-state.json");
}

async function ensureDir(dirPath: string): Promise<void> {
  await mkdir(dirPath, { recursive: true });
}

async function renameReplacingExistingFile(sourcePath: string, targetPath: string): Promise<void> {
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

async function writeAtomic(filePath: string, content: string | Buffer): Promise<void> {
  await ensureDir(path.dirname(filePath));
  const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
  await writeFile(tempPath, content);
  await renameReplacingExistingFile(tempPath, filePath);
}

async function readJson<T>(filePath: string, fallback: T): Promise<T> {
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

async function writeJson<T>(filePath: string, data: T): Promise<void> {
  if (existsSync(filePath)) {
    await copyFile(filePath, `${filePath}.bak`);
  }
  await writeAtomic(filePath, `${JSON.stringify(data, null, 2)}\n`);
}

interface SyncStateFile {
  version: 1;
  deviceId: string;
  devices: DeviceInfo[];
  updatedAt: string;
}

function fallbackDeviceId(): string {
  const seed = `${app.getPath("userData")}:${app.getName()}`;
  return `desktop-${crypto.createHash("sha256").update(seed).digest("hex").slice(0, 12)}`;
}

function currentDeviceId(): string {
  desktopDeviceId ??= fallbackDeviceId();
  return desktopDeviceId;
}

async function readSyncState(): Promise<SyncStateFile> {
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
            lastSeenAt: optionalString(item.lastSeenAt) ?? now()
          }))
      : [],
    updatedAt: optionalString(raw.updatedAt) ?? now()
  };
}

async function writeSyncState(state: SyncStateFile): Promise<void> {
  desktopDeviceId = state.deviceId;
  await writeJson(syncStatePath(), { ...state, updatedAt: now() });
}

async function getOrCreateDeviceId(): Promise<string> {
  const state = await readSyncState();
  if (!state.deviceId) {
    state.deviceId = fallbackDeviceId();
    await writeSyncState(state);
  }
  desktopDeviceId = state.deviceId;
  return state.deviceId;
}

function withSyncMetadata<T extends { updatedAt?: string; revision?: number; deviceId?: string; deletedAt?: string }>(
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

function nextSyncMetadata<T extends { revision?: number }>(value?: T): { revision: number; deviceId: string } {
  return {
    revision: Math.max(1, value?.revision ?? 0) + 1,
    deviceId: currentDeviceId()
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function logFilePath(date = new Date()): string {
  const day = date.toISOString().slice(0, 10);
  return path.join(logsRoot(), `app-${day}.log`);
}

function sanitizeLogMeta(meta?: unknown): unknown {
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

async function writeLog(level: "info" | "warn" | "error", message: string, meta?: unknown): Promise<void> {
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

function writeRuntimeStateSync(cleanShutdown: boolean): void {
  try {
    const payload = {
      version: 1,
      pid: process.pid,
      startedAt: startupRecoveryInfo.checkedAt,
      cleanShutdown,
      lastShutdownAt: cleanShutdown ? now() : undefined,
      updatedAt: now()
    };
    writeFileSync(runtimeStatePath(), `${JSON.stringify(payload, null, 2)}\n`, "utf-8");
  } catch {
    // Best-effort crash marker.
  }
}

function requireString(value: unknown, fieldName: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${fieldName} is required.`);
  }
  return value.trim();
}

function optionalString(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function normalizeTags(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string").map((item) => item.trim()).filter(Boolean);
}

function timestampForFile(): string {
  const date = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function isInsidePath(parentPath: string, childPath: string): boolean {
  const parent = path.resolve(parentPath).toLowerCase();
  const child = path.resolve(childPath).toLowerCase();
  return child === parent || child.startsWith(`${parent}${path.sep}`);
}

async function copyDirectory(source: string, target: string): Promise<void> {
  if (!existsSync(source)) return;
  await ensureDir(path.dirname(target));
  await cp(source, target, {
    recursive: true,
    force: true,
    errorOnExist: false,
    filter: (sourcePath) => !sourcePath.endsWith(".tmp")
  });
}

async function isDirectoryWritable(dirPath: string): Promise<boolean> {
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

async function readStoragePointer(): Promise<string | undefined> {
  const pointer = await readJson<unknown>(storagePointerPath(), {});
  if (!isRecord(pointer)) return undefined;
  return optionalString(pointer.dataDirectory);
}

async function writeStoragePointer(dataDirectory: string): Promise<void> {
  await writeJson(storagePointerPath(), {
    version: 1,
    dataDirectory,
    updatedAt: now()
  });
}

async function resolveInitialDataRoot(): Promise<void> {
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

async function setActiveStorageFromSettings(settings: AppSettings): Promise<void> {
  activeDataRoot = settings.storage.dataDirectory || appDataRoot();
  activeLibraryRoot = settings.storage.libraryDirectory || path.join(appDataRoot(), "AppLibrary");
  await ensureDir(appLibraryFilesRoot());
  await ensureDir(appLibraryCoversRoot());
  await ensureDir(appLibrarySearchIndexRoot());
}

function storageModeForDataRoot(dataDirectory: string): StorageSettings["storageMode"] {
  if (path.resolve(dataDirectory).toLowerCase() === path.resolve(portableDataRoot()).toLowerCase()) return "portable";
  if (path.resolve(dataDirectory).toLowerCase() === path.resolve(fallbackDataRoot()).toLowerCase()) return "fallback";
  return "custom";
}

function assertSafeMigrationTarget(currentRoot: string, targetRoot: string, label: string): void {
  const current = path.resolve(currentRoot);
  const target = path.resolve(targetRoot);
  if (current === target) throw new Error(`${label}已经在这个位置。`);
  if (isInsidePath(current, target) || isInsidePath(target, current)) {
    throw new Error(`${label}不能迁移到当前目录内部或父目录，请选择一个独立目录。`);
  }
}

async function unlinkManagedFileIfPresent(managedRoot: string, filePath: string | undefined, context: Record<string, unknown>): Promise<void> {
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

function defaultReaderSettings(): ReaderSettings {
  return {
    fontSize: 18,
    lineHeight: 1.8,
    pageMargin: 56,
    appTheme: "system",
    readerBackground: "warm",
    epubStyleMode: "publisher",
    restoreLastPosition: true,
    readingMode: "scroll",
    tracking: {
      trackReadingSessions: true,
      idleTimeoutMs: 90_000,
      progressSaveIntervalMs: 2_000,
      sessionHeartbeatMs: 10_000,
      sessionPersistIntervalMs: 30_000,
      maxPausedBeforeNewSessionMs: 1_800_000,
      endSessionOnBookSwitch: true,
      recordRecentReads: true,
      showReadingStatsCards: true
    }
  };
}

function defaultAISettings(): AISettings {
  return {
    provider: "openai-compatible",
    baseUrl: "https://api.openai.com/v1",
    model: "gpt-4.1-mini",
    temperature: 0.7,
    hasApiKey: false
  };
}

function defaultAppSettings(): AppSettings {
  return {
    version: 1,
    appearance: {
      theme: "system",
      appFontScale: 1,
      showRightPanel: true
    },
    reader: defaultReaderSettings(),
    ai: defaultAISettings(),
    storage: {
      dataDirectory: appDataRoot(),
      libraryDirectory: appLibraryRoot(),
      storageMode: storageModeForDataRoot(appDataRoot())
    },
    debug: {
      appVersion: app.getVersion(),
      dataRoot: appDataRoot(),
      showStatsCards: true
    },
    updatedAt: now()
  };
}

async function ensureAppStorage(): Promise<void> {
  await getOrCreateDeviceId();
  await ensureDir(appDataRoot());
  await ensureDir(appLibraryFilesRoot());
  await ensureDir(appLibraryCoversRoot());
  await ensureDir(appLibrarySearchIndexRoot());
  await ensureDir(logsRoot());
  if (!existsSync(inspirationsPath())) await writeJson<{ version: 1; updatedAt: string; items: InspirationItem[] }>(inspirationsPath(), { version: 1, updatedAt: now(), items: [] });
  if (!existsSync(libraryPath())) await writeJson<{ books: LibraryBook[] }>(libraryPath(), { books: [] });
  if (!existsSync(readingProgressPath())) await writeJson(readingProgressPath(), { version: 2, updatedAt: now(), items: [] });
  if (!existsSync(readingSessionsPath())) await writeJson(readingSessionsPath(), { version: 1, updatedAt: now(), sessions: [] });
  if (!existsSync(readerSettingsPath())) await writeJson<ReaderSettings>(readerSettingsPath(), defaultReaderSettings());
  if (!existsSync(appSettingsPath())) await writeJson<AppSettings>(appSettingsPath(), normalizeAppSettings(defaultAppSettings(), await readLegacyReaderSettings()));
}

async function readLegacyReaderSettings(): Promise<Partial<ReaderSettings> | undefined> {
  if (!existsSync(readerSettingsPath())) return undefined;
  return readJson<Partial<ReaderSettings>>(readerSettingsPath(), {});
}

function normalizeReaderSettings(value: unknown): ReaderSettings {
  const defaults = defaultReaderSettings();
  const raw = isRecord(value) ? value : {};
  const tracking = isRecord(raw.tracking) ? raw.tracking : {};
  const legacyTheme = raw.theme === "dark" || raw.theme === "light" ? raw.theme : undefined;
  const readerBackground =
    raw.readerBackground === "white" || raw.readerBackground === "warm" || raw.readerBackground === "green" || raw.readerBackground === "night"
      ? raw.readerBackground
      : legacyTheme === "dark"
        ? "night"
        : defaults.readerBackground;
  return {
    fontSize: typeof raw.fontSize === "number" ? raw.fontSize : defaults.fontSize,
    lineHeight: typeof raw.lineHeight === "number" ? raw.lineHeight : defaults.lineHeight,
    pageMargin: typeof raw.pageMargin === "number" ? raw.pageMargin : defaults.pageMargin,
    appTheme: raw.appTheme === "light" || raw.appTheme === "dark" || raw.appTheme === "system" ? raw.appTheme : defaults.appTheme,
    readerBackground,
    epubStyleMode: raw.epubStyleMode === "publisher" || raw.epubStyleMode === "unified" ? raw.epubStyleMode : defaults.epubStyleMode,
    theme: legacyTheme,
    restoreLastPosition: typeof raw.restoreLastPosition === "boolean" ? raw.restoreLastPosition : defaults.restoreLastPosition,
    readingMode: "scroll",
    tracking: {
      trackReadingSessions:
        typeof tracking.trackReadingSessions === "boolean" ? tracking.trackReadingSessions : defaults.tracking.trackReadingSessions,
      idleTimeoutMs: typeof tracking.idleTimeoutMs === "number" ? Math.max(15_000, tracking.idleTimeoutMs) : defaults.tracking.idleTimeoutMs,
      progressSaveIntervalMs:
        typeof tracking.progressSaveIntervalMs === "number" ? Math.max(500, tracking.progressSaveIntervalMs) : defaults.tracking.progressSaveIntervalMs,
      sessionHeartbeatMs:
        typeof tracking.sessionHeartbeatMs === "number" ? Math.max(2_000, tracking.sessionHeartbeatMs) : defaults.tracking.sessionHeartbeatMs,
      sessionPersistIntervalMs:
        typeof tracking.sessionPersistIntervalMs === "number" ? Math.max(5_000, tracking.sessionPersistIntervalMs) : defaults.tracking.sessionPersistIntervalMs,
      maxPausedBeforeNewSessionMs:
        typeof tracking.maxPausedBeforeNewSessionMs === "number"
          ? Math.max(60_000, tracking.maxPausedBeforeNewSessionMs)
          : defaults.tracking.maxPausedBeforeNewSessionMs,
      endSessionOnBookSwitch:
        typeof tracking.endSessionOnBookSwitch === "boolean" ? tracking.endSessionOnBookSwitch : defaults.tracking.endSessionOnBookSwitch,
      recordRecentReads: typeof tracking.recordRecentReads === "boolean" ? tracking.recordRecentReads : defaults.tracking.recordRecentReads,
      showReadingStatsCards:
        typeof tracking.showReadingStatsCards === "boolean" ? tracking.showReadingStatsCards : defaults.tracking.showReadingStatsCards
    }
  };
}

function normalizeAISettings(value: unknown, hasApiKey = false): AISettings {
  const defaults = defaultAISettings();
  const raw = isRecord(value) ? value : {};
  return {
    provider: raw.provider === "openai-compatible" ? raw.provider : defaults.provider,
    baseUrl: typeof raw.baseUrl === "string" && raw.baseUrl.trim() ? raw.baseUrl.trim().replace(/\/+$/, "") : defaults.baseUrl,
    model: typeof raw.model === "string" && raw.model.trim() ? raw.model.trim() : defaults.model,
    temperature: typeof raw.temperature === "number" ? Math.min(1.5, Math.max(0, raw.temperature)) : defaults.temperature,
    hasApiKey
  };
}

function normalizeAppSettings(value: unknown, legacyReader?: Partial<ReaderSettings>): AppSettings {
  const defaults = defaultAppSettings();
  const raw = isRecord(value) ? value : {};
  const appearance = isRecord(raw.appearance) ? raw.appearance : {};
  const storage = isRecord(raw.storage) ? raw.storage : {};
  const debug = isRecord(raw.debug) ? raw.debug : {};
  return {
    version: 1,
    appearance: {
      theme: appearance.theme === "light" || appearance.theme === "dark" || appearance.theme === "system" ? appearance.theme : defaults.appearance.theme,
      appFontScale: typeof appearance.appFontScale === "number" ? Math.min(1.4, Math.max(0.85, appearance.appFontScale)) : defaults.appearance.appFontScale,
      showRightPanel: typeof appearance.showRightPanel === "boolean" ? appearance.showRightPanel : defaults.appearance.showRightPanel
    },
    reader: normalizeReaderSettings(raw.reader ?? legacyReader ?? defaults.reader),
    ai: normalizeAISettings(raw.ai ?? defaults.ai, isRecord(raw.ai) && raw.ai.hasApiKey === true),
    storage: {
      dataDirectory: typeof storage.dataDirectory === "string" ? storage.dataDirectory : defaults.storage.dataDirectory,
      libraryDirectory: typeof storage.libraryDirectory === "string" ? storage.libraryDirectory : defaults.storage.libraryDirectory,
      storageMode:
        storage.storageMode === "portable" || storage.storageMode === "custom" || storage.storageMode === "fallback"
          ? storage.storageMode
          : storageModeForDataRoot(typeof storage.dataDirectory === "string" ? storage.dataDirectory : defaults.storage.dataDirectory),
      lastMigratedAt: typeof storage.lastMigratedAt === "string" ? storage.lastMigratedAt : undefined
    },
    debug: {
      appVersion: app.getVersion(),
      dataRoot: appDataRoot(),
      showStatsCards: typeof debug.showStatsCards === "boolean" ? debug.showStatsCards : defaults.debug.showStatsCards
    },
    updatedAt: typeof raw.updatedAt === "string" ? raw.updatedAt : now()
  };
}

function mergeSettings(current: AppSettings, patch: AppSettingsPatch): AppSettings {
  return normalizeAppSettings({
    ...current,
    appearance: { ...current.appearance, ...(isRecord(patch.appearance) ? patch.appearance : {}) },
    reader: {
      ...current.reader,
      ...(isRecord(patch.reader) ? patch.reader : {}),
      tracking: {
        ...current.reader.tracking,
        ...(isRecord(patch.reader?.tracking) ? patch.reader.tracking : {})
      }
    },
    ai: { ...current.ai, ...(isRecord(patch.ai) ? patch.ai : {}), hasApiKey: current.ai.hasApiKey },
    storage: { ...current.storage, ...(isRecord(patch.storage) ? patch.storage : {}) },
    debug: { ...current.debug, ...(isRecord(patch.debug) ? patch.debug : {}) },
    updatedAt: now()
  });
}

async function readAISecretStore(): Promise<unknown> {
  if (!existsSync(aiSecretsPath())) return {};
  try {
    const content = await readFile(aiSecretsPath(), "utf-8");
    return content.trim() ? JSON.parse(content) : {};
  } catch (error) {
    await writeLog("warn", "AI secret store read failed.", { error: error instanceof Error ? error.message : String(error) });
    return {};
  }
}

async function hasAIApiKey(): Promise<boolean> {
  const raw = await readAISecretStore();
  return isRecord(raw) && typeof raw.apiKeyEncrypted === "string" && raw.apiKeyEncrypted.length > 0;
}

async function readAIApiKey(): Promise<string | undefined> {
  const raw = await readAISecretStore();
  if (!isRecord(raw) || typeof raw.apiKeyEncrypted !== "string" || !raw.apiKeyEncrypted) return undefined;
  try {
    return safeStorage.decryptString(Buffer.from(raw.apiKeyEncrypted, "base64"));
  } catch (error) {
    await writeLog("warn", "AI API key decrypt failed.", { error: error instanceof Error ? error.message : String(error) });
    return undefined;
  }
}

async function saveAIApiKey(input: SaveAIApiKeyInput): Promise<AISettings> {
  const apiKey = typeof input.apiKey === "string" ? input.apiKey.trim() : "";
  if (!apiKey) throw new Error("API Key 不能为空。");
  if (!safeStorage.isEncryptionAvailable()) throw new Error("当前系统不可用安全加密存储，无法保存 API Key。");
  const encrypted = safeStorage.encryptString(apiKey).toString("base64");
  await writeAtomic(aiSecretsPath(), `${JSON.stringify({ version: 1, apiKeyEncrypted: encrypted, updatedAt: now() }, null, 2)}\n`);
  return getAISettings();
}

async function clearAIApiKey(): Promise<AISettings> {
  await rm(aiSecretsPath(), { force: true });
  await rm(`${aiSecretsPath()}.bak`, { force: true });
  return getAISettings();
}

async function getAppSettings(): Promise<AppSettings> {
  const raw = await readJson<unknown>(appSettingsPath(), defaultAppSettings());
  const settings = normalizeAppSettings(raw, await readLegacyReaderSettings());
  settings.ai = normalizeAISettings(settings.ai, await hasAIApiKey());
  if (!existsSync(appSettingsPath())) await writeJson<AppSettings>(appSettingsPath(), settings);
  await setActiveStorageFromSettings(settings);
  return settings;
}

async function updateAppSettings(patch: AppSettingsPatch): Promise<AppSettings> {
  const next = mergeSettings(await getAppSettings(), isRecord(patch) ? patch : {});
  next.ai = normalizeAISettings(next.ai, await hasAIApiKey());
  await setActiveStorageFromSettings(next);
  await writeJson<AppSettings>(appSettingsPath(), next);
  await writeJson<ReaderSettings>(readerSettingsPath(), next.reader);
  return next;
}

async function resetSettingsSection(section: SettingsSection): Promise<AppSettings> {
  const current = await getAppSettings();
  const defaults = defaultAppSettings();
  const next = normalizeAppSettings({
    ...current,
    [section]: defaults[section],
    updatedAt: now()
  });
  next.ai = normalizeAISettings(next.ai, await hasAIApiKey());
  await setActiveStorageFromSettings(next);
  await writeJson<AppSettings>(appSettingsPath(), next);
  await writeJson<ReaderSettings>(readerSettingsPath(), next.reader);
  return next;
}

async function resetReaderSettingsOnly(): Promise<AppSettings> {
  return updateAppSettings({ reader: defaultReaderSettings() });
}

async function getStorageLocations(): Promise<StorageLocations> {
  const settings = await getAppSettings();
  return {
    dataDirectory: appDataRoot(),
    libraryDirectory: appLibraryRoot(),
    portableDataDirectory: portableDataRoot(),
    fallbackDataDirectory: fallbackDataRoot(),
    storageMode: settings.storage.storageMode,
    dataDirectoryWritable: await isDirectoryWritable(appDataRoot()),
    libraryDirectoryWritable: await isDirectoryWritable(appLibraryRoot())
  };
}

async function chooseDataDirectory(): Promise<string | null> {
  return chooseDirectory("选择数据目录");
}

async function chooseLibraryDirectory(): Promise<string | null> {
  return chooseDirectory("选择书籍目录");
}

async function migrateDataDirectory(targetDirectory: string): Promise<AppSettings> {
  const target = requireString(targetDirectory, "数据目录");
  assertSafeMigrationTarget(appDataRoot(), target, "数据目录");
  if (!(await isDirectoryWritable(target))) throw new Error("选择的数据目录不可写，请换一个位置。");
  await copyDirectory(appDataRoot(), target);
  activeDataRoot = target;
  await writeStoragePointer(target);
  const copiedSettings = normalizeAppSettings(await readJson<unknown>(appSettingsPath(), defaultAppSettings()), await readLegacyReaderSettings());
  const next = normalizeAppSettings({
    ...copiedSettings,
    storage: {
      ...copiedSettings.storage,
      dataDirectory: target,
      libraryDirectory: path.join(target, "AppLibrary"),
      storageMode: storageModeForDataRoot(target),
      lastMigratedAt: now()
    },
    debug: {
      ...copiedSettings.debug,
      dataRoot: target
    },
    updatedAt: now()
  });
  await setActiveStorageFromSettings(next);
  await writeJson<AppSettings>(appSettingsPath(), next);
  await writeJson<ReaderSettings>(readerSettingsPath(), next.reader);
  await writeLog("warn", "Data directory migrated by user request.", { target });
  return next;
}

async function migrateLibraryDirectory(targetDirectory: string): Promise<AppSettings> {
  const target = requireString(targetDirectory, "书籍目录");
  assertSafeMigrationTarget(appLibraryRoot(), target, "书籍目录");
  if (!(await isDirectoryWritable(target))) throw new Error("选择的书籍目录不可写，请换一个位置。");
  await copyDirectory(appLibraryRoot(), target);
  const current = await getAppSettings();
  const next = normalizeAppSettings({
    ...current,
    storage: {
      ...current.storage,
      libraryDirectory: target,
      storageMode: current.storage.storageMode,
      lastMigratedAt: now()
    },
    updatedAt: now()
  });
  await setActiveStorageFromSettings(next);
  await writeJson<AppSettings>(appSettingsPath(), next);
  await writeJson<ReaderSettings>(readerSettingsPath(), next.reader);
  await writeLog("warn", "Library directory migrated by user request.", { target });
  return next;
}

async function getAISettings(): Promise<AISettings> {
  return (await getAppSettings()).ai;
}

async function updateAISettings(patch: AISettingsPatch): Promise<AISettings> {
  const settings = await updateAppSettings({ ai: patch });
  return settings.ai;
}

function aiActionLabel(action: AIRunAction): string {
  switch (action) {
    case "expand":
      return "扩写";
    case "platform-style":
      return "平台风格化";
    case "conflict":
      return "生成冲突点";
    case "humanize":
      return "去 AI 味润色";
    case "polish":
    default:
      return "润色";
  }
}

function buildAIPrompt(input: AIRunInput): string {
  const title = input.title?.trim() ? `标题：${input.title.trim()}\n` : "";
  const platform = input.platform?.trim() ? `目标平台/风格：${input.platform.trim()}\n` : "";
  const content = requireString(input.content, "灵感内容");
  const instruction =
    input.action === "expand"
      ? "把这条小说灵感扩展成可执行的剧情方案，保留钩子、冲突、角色动机和下一步写法。"
      : input.action === "platform-style"
        ? "按目标平台读者口味重写这条灵感，让它更像可直接拿去写正文前的桥段设计。"
        : input.action === "conflict"
          ? "基于这条灵感生成 5 个可写冲突点，每个包含触发条件、升级方式和可用爽点。"
          : input.action === "humanize"
            ? "去掉机械总结感和 AI 腔，把这条灵感润成更自然、更像作者自己随手写下但清楚可用的素材。"
            : "润色这条小说灵感，让它更清晰、更有画面感，同时不要替作者写成长篇正文。";
  return `${instruction}\n\n${platform}${title}原始灵感：\n${content}`;
}

async function runAIAction(input: AIRunInput): Promise<AIRunResult> {
  const settings = await getAISettings();
  const apiKey = await readAIApiKey();
  if (!apiKey) throw new Error("请先在设置中心配置 AI API Key。");
  const prompt = buildAIPrompt(input);
  const response = await fetch(`${settings.baseUrl.replace(/\/+$/, "")}/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: settings.model,
      temperature: settings.temperature,
      messages: [
        {
          role: "system",
          content:
            "你是小说创作辅助工具，只加工灵感、桥段、人设和阅读札记。输出中文，具体、可写、少套话，不要声称可以发布到任何平台。"
        },
        { role: "user", content: prompt }
      ]
    })
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    await writeLog("warn", "AI request failed.", { status: response.status, statusText: response.statusText });
    throw new Error(`AI 请求失败：${response.status} ${response.statusText}${detail ? "。请检查 Base URL、模型名或额度。" : ""}`);
  }
  const data = (await response.json()) as unknown;
  const content =
    isRecord(data) &&
    Array.isArray(data.choices) &&
    isRecord(data.choices[0]) &&
    isRecord(data.choices[0].message) &&
    typeof data.choices[0].message.content === "string"
      ? data.choices[0].message.content.trim()
      : "";
  if (!content) throw new Error("AI 返回为空。");
  return { kind: input.action, content, prompt, model: settings.model };
}

async function testAIConnection(): Promise<{ ok: boolean; message: string }> {
  try {
    const result = await runAIAction({ action: "polish", content: "测试连接：一个角色在雨夜想起旧约定。" });
    return { ok: true, message: result.content ? "AI 连接可用。" : "AI 返回为空。" };
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : String(error) };
  }
}

function getBuildInfo(): BuildInfo {
  return {
    appName: app.getName(),
    appVersion: app.getVersion(),
    isPackaged: app.isPackaged,
    platform: process.platform,
    arch: process.arch,
    electronVersion: process.versions.electron ?? "",
    chromeVersion: process.versions.chrome ?? "",
    nodeVersion: process.versions.node,
    appPath: app.getAppPath(),
    dataRoot: appDataRoot(),
    logsRoot: logsRoot(),
    buildMode: app.isPackaged ? "production" : "development"
  };
}

async function prepareStartupRecovery(): Promise<void> {
  const previous = await readJson<unknown>(runtimeStatePath(), {});
  const previousRecord = isRecord(previous) ? previous : {};
  const abnormalExit = previousRecord.cleanShutdown === false;
  const recovered = await recoverUnfinishedSessions();
  startupRecoveryInfo = {
    abnormalExit,
    previousStartedAt: typeof previousRecord.startedAt === "string" ? previousRecord.startedAt : undefined,
    previousShutdownAt: typeof previousRecord.lastShutdownAt === "string" ? previousRecord.lastShutdownAt : undefined,
    recoveredSessionsCount: recovered.recoveredCount,
    checkedAt: now()
  };
  startupRecoverySeen = false;
  await writeLog("info", "Application startup recovery check completed.", startupRecoveryInfo);
  writeRuntimeStateSync(false);
}

async function openPathOrThrow(targetPath: string): Promise<void> {
  await ensureDir(targetPath);
  const result = await shell.openPath(targetPath);
  if (result) throw new Error(result);
}

async function writeRendererLog(input: RendererLogInput): Promise<void> {
  const level = input.level === "warn" || input.level === "error" ? input.level : "info";
  await writeLog(level, input.message || "Renderer log", {
    source: input.source,
    detail: input.detail
  });
}

interface BackupManifest {
  version: 1;
  createdAt: string;
  appVersion: string;
  platform: string;
  arch: string;
  dataRoot: string;
  appDataPath: "app-data";
}

async function chooseDirectory(title: string): Promise<string | null> {
  const options: OpenDialogOptions = { title, properties: ["openDirectory", "createDirectory"] };
  const result = mainWindow ? await dialog.showOpenDialog(mainWindow, options) : await dialog.showOpenDialog(options);
  return result.canceled ? null : result.filePaths[0] ?? null;
}

async function createBackup(): Promise<BackupResult | null> {
  const selectedDir = await chooseDirectory("选择备份保存目录");
  if (!selectedDir) return null;
  if (isInsidePath(appDataRoot(), selectedDir)) {
    throw new Error("Backup directory cannot be inside the app data directory.");
  }
  const createdAt = now();
  const backupRoot = path.join(selectedDir, `CreationReadingAssistant-backup-${timestampForFile()}`);
  const appDataBackupPath = path.join(backupRoot, "app-data");
  await ensureDir(backupRoot);
  await writeLog("info", "Backup started.", { backupRoot });
  await copyDirectory(appDataRoot(), appDataBackupPath);

  const manifest: BackupManifest = {
    version: 1,
    createdAt,
    appVersion: app.getVersion(),
    platform: process.platform,
    arch: process.arch,
    dataRoot: appDataRoot(),
    appDataPath: "app-data"
  };
  const manifestPath = path.join(backupRoot, "backup-manifest.json");
  await writeJson<BackupManifest>(manifestPath, manifest);
  await writeLog("info", "Backup completed.", { backupRoot });
  return {
    backupRoot,
    manifestPath,
    createdAt,
    appDataCopied: true
  };
}

function normalizeBackupManifest(value: unknown): BackupManifest {
  if (!isRecord(value) || value.version !== 1 || value.appDataPath !== "app-data") {
    throw new Error("Invalid backup manifest.");
  }
  return {
    version: 1,
    createdAt: typeof value.createdAt === "string" ? value.createdAt : now(),
    appVersion: typeof value.appVersion === "string" ? value.appVersion : "",
    platform: typeof value.platform === "string" ? value.platform : "",
    arch: typeof value.arch === "string" ? value.arch : "",
    dataRoot: typeof value.dataRoot === "string" ? value.dataRoot : "",
    appDataPath: "app-data"
  };
}

function assertSafeRestoreSource(backupRoot: string, appDataBackupPath: string): void {
  const currentAppData = appDataRoot();
  if (isInsidePath(currentAppData, backupRoot)) {
    throw new Error("Backup directory cannot be inside the current app data directory.");
  }
  if (isInsidePath(currentAppData, appDataBackupPath) || isInsidePath(appDataBackupPath, currentAppData)) {
    throw new Error("Backup app-data source cannot overlap the current app data directory.");
  }
}

async function restoreBackup(): Promise<RestoreResult | null> {
  const backupRoot = await chooseDirectory("选择备份目录");
  if (!backupRoot) return null;
  const manifestPath = path.join(backupRoot, "backup-manifest.json");
  const appDataBackupPath = path.join(backupRoot, "app-data");
  if (!existsSync(manifestPath) || !existsSync(appDataBackupPath)) throw new Error("Selected directory is not a valid CreationReadingAssistant backup.");
  assertSafeRestoreSource(backupRoot, appDataBackupPath);
  const manifest = normalizeBackupManifest(await readJson<unknown>(manifestPath, {}));
  await writeLog("warn", "Restore started.", { backupRoot });

  const restoredAt = now();
  const checkpointPath = existsSync(appDataRoot()) ? path.join(path.dirname(appDataRoot()), `CreationReadingAssistant-before-restore-${timestampForFile()}`) : undefined;
  if (checkpointPath) await copyDirectory(appDataRoot(), checkpointPath);
  if (existsSync(appDataRoot())) await rm(appDataRoot(), { recursive: true, force: true });
  await copyDirectory(appDataBackupPath, appDataRoot());
  await ensureDir(logsRoot());

  await writeLog("warn", "Restore completed.", { backupRoot, checkpointPath });
  writeRuntimeStateSync(false);
  return {
    backupRoot,
    restoredAt,
    checkpointPath,
    restartRecommended: true
  };
}

async function exportDebugInfo(): Promise<DebugExportResult | null> {
  const selectedDir = await chooseDirectory("选择调试信息导出目录");
  if (!selectedDir) return null;
  const createdAt = now();
  const outputRoot = path.join(selectedDir, `CreationReadingAssistant-debug-${timestampForFile()}`);
  await ensureDir(outputRoot);
  const [settings, books, progress, sessions] = await Promise.all([
    getAppSettings(),
    readLibraryIndex(),
    readProgressList(),
    readSessionList()
  ]);
  const info = {
    createdAt,
    build: getBuildInfo(),
    startupRecovery: startupRecoveryInfo,
    summary: {
      libraryBooksCount: books.length,
      readingProgressCount: progress.length,
      readingSessionsCount: sessions.length
    },
    settings: {
      appearance: settings.appearance,
      reader: {
        ...settings.reader,
        tracking: settings.reader.tracking
      },
      storage: settings.storage,
      debug: settings.debug
    }
  };
  const infoPath = path.join(outputRoot, "debug-info.json");
  await writeJson(infoPath, info);
  let logsCopied = false;
  if (existsSync(logsRoot())) {
    await copyDirectory(logsRoot(), path.join(outputRoot, "logs"));
    logsCopied = true;
  }
  await writeLog("info", "Debug info exported.", { outputRoot });
  return { outputRoot, infoPath, logsCopied, createdAt };
}

async function readTextFile(filePath: string): Promise<string> {
  return (await readFile(filePath, "utf-8")).replace(/^\uFEFF/, "");
}

async function readTextFileIfWithinLimit(filePath: string, maxBytes = MAX_SEARCH_TEXT_FILE_BYTES): Promise<string> {
  try {
    const info = await stat(filePath);
    if (info.size > maxBytes) {
      await writeLog("warn", "Search skipped large text file.", { filePath, size: info.size, maxBytes });
      return "";
    }
    return await readTextFile(filePath);
  } catch {
    return "";
  }
}

function normalizeInspirationType(value: unknown): InspirationType {
  return value === "plot" || value === "character" || value === "world" || value === "scene" || value === "line" || value === "trope" || value === "note"
    ? value
    : "note";
}

function normalizeInspirationStatus(value: unknown): InspirationStatus {
  return value === "inbox" || value === "usable" || value === "polished" || value === "used" || value === "archived" ? value : "inbox";
}

function normalizeSourceLocation(value: unknown): InspirationSourceLocation | undefined {
  if (!isRecord(value)) return undefined;
  return {
    format: value.format === "txt" || value.format === "md" || value.format === "epub" ? value.format : undefined,
    progressPercent: typeof value.progressPercent === "number" ? Math.min(100, Math.max(0, value.progressPercent)) : undefined,
    excerpt: optionalString(value.excerpt),
    href: optionalString(value.href),
    cfi: optionalString(value.cfi),
    scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : undefined,
    createdFrom:
      value.createdFrom === "reader-selection" || value.createdFrom === "reader-note" || value.createdFrom === "manual" ? value.createdFrom : undefined
  };
}

function normalizeInspirationSource(value: unknown): InspirationSourceSnapshot | undefined {
  if (!isRecord(value)) return undefined;
  const createdAt = optionalString(value.createdAt) ?? now();
  return {
    bookId: optionalString(value.bookId),
    bookTitle: optionalString(value.bookTitle),
    bookAuthor: optionalString(value.bookAuthor),
    format: value.format === "txt" || value.format === "md" || value.format === "epub" ? value.format : undefined,
    chapterTitle: optionalString(value.chapterTitle),
    locationLabel: optionalString(value.locationLabel),
    progressPercent: typeof value.progressPercent === "number" ? Math.min(100, Math.max(0, value.progressPercent)) : undefined,
    excerpt: optionalString(value.excerpt),
    href: optionalString(value.href),
    cfi: optionalString(value.cfi),
    scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : undefined,
    createdFrom:
      value.createdFrom === "reader-selection" || value.createdFrom === "reader-note" || value.createdFrom === "manual" ? value.createdFrom : undefined,
    createdAt
  };
}

function normalizeInspirationVariant(value: unknown): InspirationVariant | undefined {
  if (!isRecord(value)) return undefined;
  const content = optionalString(value.content);
  if (!content) return undefined;
  const kind =
    value.kind === "polish" || value.kind === "expand" || value.kind === "platform-style" || value.kind === "conflict" || value.kind === "humanize"
      ? value.kind
      : "polish";
  return {
    id: optionalString(value.id) ?? makeId("variant"),
    kind,
    content,
    prompt: optionalString(value.prompt) ?? "",
    model: optionalString(value.model) ?? "",
    createdAt: optionalString(value.createdAt) ?? now()
  };
}

function normalizeInspirationItem(value: unknown): InspirationItem | undefined {
  if (!isRecord(value)) return undefined;
  const title = optionalString(value.title);
  if (!title) return undefined;
  const createdAt = optionalString(value.createdAt) ?? now();
  return withSyncMetadata(
    {
      id: optionalString(value.id) ?? makeId("insp"),
      title,
      body: typeof value.body === "string" ? value.body : "",
      type: normalizeInspirationType(value.type),
      status: normalizeInspirationStatus(value.status),
      tags: normalizeTags(value.tags),
      platformTags: normalizeTags(value.platformTags),
      source: normalizeInspirationSource(value.source),
      sourceBookId: optionalString(value.sourceBookId),
      sourceLocation: normalizeSourceLocation(value.sourceLocation),
      variants: Array.isArray(value.variants) ? value.variants.map(normalizeInspirationVariant).filter((item): item is InspirationVariant => Boolean(item)) : [],
      createdAt,
      updatedAt: optionalString(value.updatedAt) ?? createdAt,
      revision: typeof value.revision === "number" ? value.revision : undefined,
      deviceId: optionalString(value.deviceId),
      deletedAt: optionalString(value.deletedAt)
    },
    createdAt
  );
}

async function readInspirations(options: { includeDeleted?: boolean } = {}): Promise<InspirationItem[]> {
  const raw = await readJson<unknown>(inspirationsPath(), { version: 1, updatedAt: now(), items: [] });
  const items = isRecord(raw) && Array.isArray(raw.items) ? raw.items : Array.isArray(raw) ? raw : [];
  return items
    .map(normalizeInspirationItem)
    .filter((item): item is InspirationItem => Boolean(item))
    .filter((item) => options.includeDeleted || !item.deletedAt)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

async function writeInspirations(items: InspirationItem[]): Promise<InspirationItem[]> {
  const sorted = [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  await writeJson(inspirationsPath(), { version: 1, updatedAt: now(), items: sorted });
  return sorted;
}

async function createInspiration(input: CreateInspirationInput): Promise<InspirationItem> {
  const title = requireString(input.title, "灵感标题");
  const item: InspirationItem = {
    id: makeId("insp"),
    title,
    body: typeof input.body === "string" ? input.body : "",
    type: normalizeInspirationType(input.type),
    status: normalizeInspirationStatus(input.status),
    tags: normalizeTags(input.tags),
    platformTags: normalizeTags(input.platformTags),
    source: normalizeInspirationSource(input.source),
    sourceBookId: optionalString(input.sourceBookId),
    sourceLocation: normalizeSourceLocation(input.sourceLocation),
    variants: [],
    revision: 1,
    deviceId: currentDeviceId(),
    createdAt: now(),
    updatedAt: now()
  };
  await writeInspirations([item, ...(await readInspirations())]);
  return item;
}

async function readInspiration(id: string): Promise<InspirationItem | undefined> {
  return (await readInspirations()).find((item) => item.id === id);
}

async function updateInspiration(id: string, input: UpdateInspirationInput): Promise<InspirationItem> {
  const items = await readInspirations();
  const current = items.find((item) => item.id === id);
  if (!current) throw new Error("未找到灵感。");
  const next: InspirationItem = {
    ...current,
    title: typeof input.title === "string" && input.title.trim() ? input.title.trim() : current.title,
    body: typeof input.body === "string" ? input.body : current.body,
    type: input.type ? normalizeInspirationType(input.type) : current.type,
    status: input.status ? normalizeInspirationStatus(input.status) : current.status,
    tags: Array.isArray(input.tags) ? normalizeTags(input.tags) : current.tags,
    platformTags: Array.isArray(input.platformTags) ? normalizeTags(input.platformTags) : current.platformTags,
    source: input.source === undefined ? current.source : normalizeInspirationSource(input.source),
    sourceBookId: input.sourceBookId === undefined ? current.sourceBookId : optionalString(input.sourceBookId),
    sourceLocation: input.sourceLocation === undefined ? current.sourceLocation : normalizeSourceLocation(input.sourceLocation),
    variants: Array.isArray(input.variants) ? input.variants as InspirationVariant[] : current.variants,
    ...nextSyncMetadata(current),
    updatedAt: now()
  };
  await writeInspirations(items.map((item) => (item.id === id ? next : item)));
  return next;
}

async function deleteInspiration(id: string): Promise<InspirationItem[]> {
  const timestamp = now();
  const items = await readInspirations({ includeDeleted: true });
  await writeInspirations(items.map((item) => (item.id === id ? { ...item, ...nextSyncMetadata(item), deletedAt: timestamp, updatedAt: timestamp } : item)));
  return readInspirations();
}

async function addInspirationVariant(id: string, input: AddInspirationVariantInput): Promise<InspirationItem> {
  const items = await readInspirations();
  const current = items.find((item) => item.id === id);
  if (!current) throw new Error("未找到灵感。");
  const content = requireString(input.content, "AI 候选内容");
  const variant: InspirationVariant = {
    id: makeId("variant"),
    kind:
      input.kind === "polish" || input.kind === "expand" || input.kind === "platform-style" || input.kind === "conflict" || input.kind === "humanize"
        ? input.kind
        : "polish",
    content,
    prompt: typeof input.prompt === "string" ? input.prompt : "",
    model: typeof input.model === "string" ? input.model : "",
    createdAt: now()
  };
  const next = { ...current, status: "polished" as InspirationStatus, variants: [variant, ...current.variants], ...nextSyncMetadata(current), updatedAt: now() };
  await writeInspirations(items.map((item) => (item.id === id ? next : item)));
  return next;
}

function normalizeLibraryBook(value: unknown): LibraryBook | null {
  if (!isRecord(value)) return null;
  const id = optionalString(value.id);
  const filePath = optionalString(value.filePath);
  const title = optionalString(value.title);
  if (!id || !filePath || !title) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const importedAt = optionalString(value.importedAt) ?? now();
  return withSyncMetadata(
    {
      id,
      title,
      filePath,
      originalPath: optionalString(value.originalPath),
      originalFileName: optionalString(value.originalFileName),
      originalFilePath: optionalString(value.originalFilePath),
      format,
      importedAt,
      updatedAt: optionalString(value.updatedAt) ?? importedAt,
      size: typeof value.size === "number" ? Math.max(0, value.size) : 0,
      contentHash: optionalString(value.contentHash),
      duplicateIndex: typeof value.duplicateIndex === "number" ? Math.max(1, value.duplicateIndex) : undefined,
      importLabel: optionalString(value.importLabel),
      author: optionalString(value.author),
      description: optionalString(value.description),
      language: optionalString(value.language),
      publisher: optionalString(value.publisher),
      coverPath: optionalString(value.coverPath),
      epub: isRecord(value.epub) ? (value.epub as LibraryBook["epub"]) : undefined,
      revision: typeof value.revision === "number" ? value.revision : undefined,
      deviceId: optionalString(value.deviceId),
      deletedAt: optionalString(value.deletedAt)
    },
    importedAt
  );
}

async function readLibraryIndex(options: { includeDeleted?: boolean } = {}): Promise<LibraryBook[]> {
  const data = await readJson<{ books: unknown[] }>(libraryPath(), { books: [] });
  const books = Array.isArray(data.books) ? data.books : [];
  return books.map(normalizeLibraryBook).filter((book): book is LibraryBook => Boolean(book)).filter((book) => options.includeDeleted || !book.deletedAt);
}

async function writeEpubSearchIndex(bookId: string, items: EpubSearchIndexItem[]): Promise<string> {
  const targetPath = epubSearchIndexPath(bookId);
  const index: EpubSearchIndex = {
    version: 1,
    bookId,
    updatedAt: now(),
    items
  };
  await writeJson(targetPath, index);
  return targetPath;
}

async function readEpubSearchIndex(bookId: string): Promise<EpubSearchIndex | undefined> {
  try {
    const index = await readJson<unknown>(epubSearchIndexPath(bookId), undefined);
    if (index === undefined) return undefined;
    if (!isRecord(index)) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "not-an-object" });
      return undefined;
    }
    if (index.version !== 1) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "unsupported-version" });
      return undefined;
    }
    if (index.bookId !== bookId) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "book-id-mismatch" });
      return undefined;
    }
    if (!Array.isArray(index.items)) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "items-not-array" });
      return undefined;
    }

    const items = index.items;
    const hasInvalidItem = items.some(
      (item) =>
        !isRecord(item) ||
        typeof item.id !== "string" ||
        typeof item.title !== "string" ||
        typeof item.href !== "string" ||
        typeof item.text !== "string"
    );
    if (hasInvalidItem) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "invalid-item-shape" });
      return undefined;
    }

    return {
      version: 1,
      bookId,
      updatedAt: typeof index.updatedAt === "string" ? index.updatedAt : now(),
      items: items as EpubSearchIndexItem[]
    };
  } catch (error) {
    await writeLog("warn", "EPUB search index ignored.", {
      bookId,
      reason: "read-failed",
      error: error instanceof Error ? error.message : String(error)
    });
    return undefined;
  }
}

async function writeLibraryIndex(books: LibraryBook[]): Promise<void> {
  await writeJson(libraryPath(), { books });
}

function importDateLabel(date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${month}-${day}`;
}

async function contentHash(filePath: string): Promise<string> {
  const buffer = await readFile(filePath);
  return crypto.createHash("sha256").update(buffer).digest("hex");
}

function duplicateImportInfo(existingBooks: LibraryBook[], hash: string): Pick<LibraryBook, "contentHash" | "duplicateIndex" | "importLabel"> {
  const duplicates = existingBooks.filter((book) => book.contentHash === hash);
  const duplicateIndex = duplicates.length + 1;
  return {
    contentHash: hash,
    duplicateIndex,
    importLabel: duplicateIndex > 1 ? `重复导入 #${duplicateIndex}` : `导入于 ${importDateLabel()}`
  };
}

function extractTextBookMetadata(content: string, fallbackTitle: string): { title: string; author?: string } {
  const lines = content
    .replace(/^\uFEFF/, "")
    .split(/\r?\n/)
    .slice(0, 80)
    .map((line) => line.trim())
    .filter(Boolean);
  let title = fallbackTitle;
  let author: string | undefined;

  for (const line of lines) {
    const titleMatch = line.match(/^(?:书名|标题|Title)\s*[:：]\s*(.+)$/i);
    if (titleMatch?.[1]?.trim()) title = titleMatch[1].trim();
    const authorMatch = line.match(/^(?:作者|Author)\s*[:：]\s*(.+)$/i) ?? line.match(/^by\s+(.+)$/i);
    if (authorMatch?.[1]?.trim()) {
      author = authorMatch[1].replace(/^[:：]\s*/, "").trim();
      break;
    }
  }

  return { title, author };
}

async function importBook(): Promise<LibraryBook[]> {
  const options: OpenDialogOptions = {
    title: "导入本地小说",
    properties: ["openFile", "multiSelections"],
    filters: [{ name: "Text", extensions: ["txt", "md", "markdown"] }]
  };
  const result = mainWindow ? await dialog.showOpenDialog(mainWindow, options) : await dialog.showOpenDialog(options);
  if (result.canceled) return readLibraryIndex();
  const books = await readLibraryIndex();
  for (const sourcePath of result.filePaths) {
    const ext = path.extname(sourcePath).toLowerCase();
    const format = ext === ".md" || ext === ".markdown" ? "md" : "txt";
    const id = makeId("book");
    const targetPath = path.join(appLibraryFilesRoot(), `${id}.${format}`);
    await copyFile(sourcePath, targetPath);
    const info = await stat(targetPath);
    const hash = await contentHash(targetPath);
    const metadata = extractTextBookMetadata(await readTextFileIfWithinLimit(targetPath), path.basename(sourcePath, ext));
    books.unshift({
      id,
      title: metadata.title,
      filePath: targetPath,
      originalPath: sourcePath,
      originalFilePath: sourcePath,
      originalFileName: path.basename(sourcePath),
      format,
      importedAt: now(),
      updatedAt: now(),
      size: info.size,
      revision: 1,
      deviceId: currentDeviceId(),
      author: metadata.author,
      ...duplicateImportInfo(books, hash)
    });
  }
  await writeLibraryIndex(books);
  await writeLog("info", "Text/Markdown books imported.", { importedCount: result.filePaths.length });
  return books;
}

async function importEpub(): Promise<LibraryBook[]> {
  const options: OpenDialogOptions = {
    title: "导入 EPUB 书籍",
    properties: ["openFile", "multiSelections"],
    filters: [{ name: "EPUB", extensions: ["epub"] }]
  };
  const result = mainWindow ? await dialog.showOpenDialog(mainWindow, options) : await dialog.showOpenDialog(options);
  if (result.canceled) return readLibraryIndex();
  const books = await readLibraryIndex();
  for (const sourcePath of result.filePaths) {
    const ext = path.extname(sourcePath).toLowerCase();
    if (ext !== ".epub") continue;
    const id = makeId("book");
    const targetPath = path.join(appLibraryFilesRoot(), `${id}.epub`);
    await copyFile(sourcePath, targetPath);
    const info = await stat(targetPath);
    const hash = await contentHash(targetPath);
    let parsed: Awaited<ReturnType<typeof parseEpubFile>> | undefined;
    try {
      parsed = await parseEpubFile(targetPath, id, appLibraryCoversRoot());
    } catch (error) {
      await writeLog("warn", "EPUB metadata parsing failed.", {
        bookId: id,
        error: error instanceof Error ? error.message : String(error)
      });
    }
    let searchIndexPathValue: string | undefined;
    const indexedAt = parsed?.searchItems.length ? now() : undefined;
    if (parsed?.searchItems.length) {
      try {
        searchIndexPathValue = await writeEpubSearchIndex(id, parsed.searchItems);
      } catch (error) {
        await writeLog("warn", "EPUB search index creation failed.", {
          bookId: id,
          error: error instanceof Error ? error.message : String(error)
        });
      }
    }
    books.unshift({
      id,
      title: parsed?.title || path.basename(sourcePath, ext),
      filePath: targetPath,
      originalPath: sourcePath,
      originalFilePath: sourcePath,
      originalFileName: path.basename(sourcePath),
      format: "epub",
      importedAt: now(),
      updatedAt: now(),
      size: info.size,
      revision: 1,
      deviceId: currentDeviceId(),
      ...duplicateImportInfo(books, hash),
      author: parsed?.author,
      description: parsed?.description,
      language: parsed?.language,
      publisher: parsed?.publisher,
      coverPath: parsed?.coverPath,
      epub: {
        author: parsed?.author,
        description: parsed?.description,
        language: parsed?.language,
        publisher: parsed?.publisher,
        coverPath: parsed?.coverPath,
        toc: parsed?.toc ?? [],
        searchIndexedAt: searchIndexPathValue ? indexedAt : undefined,
        searchIndexPath: searchIndexPathValue
      }
    });
  }
  await writeLibraryIndex(books);
  await writeLog("info", "EPUB books imported.", { importedCount: result.filePaths.length });
  return books;
}

async function removeBook(bookId: string): Promise<LibraryBook[]> {
  const existingBooks = await readLibraryIndex({ includeDeleted: true });
  const removedBook = existingBooks.find((book) => book.id === bookId);
  const timestamp = now();
  const books = existingBooks.map((book) => (book.id === bookId ? { ...book, ...nextSyncMetadata(book), deletedAt: timestamp, updatedAt: timestamp } : book));
  await writeLibraryIndex(books);
  const progress = (await readProgressList()).filter((item) => item.bookId !== bookId);
  await writeProgressList(progress);
  const sessions = (await readSessionList()).filter((session) => session.bookId !== bookId);
  await writeSessionList(sessions);
  if (removedBook) {
    await unlinkManagedFileIfPresent(appLibraryFilesRoot(), removedBook.filePath, { bookId, kind: "library-file" });
    await unlinkManagedFileIfPresent(appLibraryCoversRoot(), removedBook.coverPath ?? removedBook.epub?.coverPath, { bookId, kind: "epub-cover" });
  }
  if (removedBook?.format === "epub") {
    try {
      await unlink(epubSearchIndexPath(bookId));
    } catch (error) {
      const code = isRecord(error) && typeof error.code === "string" ? error.code : undefined;
      if (code !== "ENOENT") {
        await writeLog("warn", "EPUB search index cleanup failed.", {
          bookId,
          error: error instanceof Error ? error.message : String(error)
        });
      }
    }
  }
  await writeLog("info", "Book removed from library.", { bookId });
  return readLibraryIndex();
}

function registerEpubProtocol(): void {
  protocol.registerFileProtocol(EPUB_PROTOCOL_SCHEME, (request, callback) => {
    void (async () => {
      const bookId = bookIdFromEpubProtocolUrl(request.url);
      if (!bookId) {
        callback({ error: -6 });
        return;
      }
      const book = (await readLibraryIndex()).find((item) => item.id === bookId);
      if (!book || book.format !== "epub") {
        callback({ error: -6 });
        return;
      }
      const libraryRoot = path.resolve(appLibraryFilesRoot()).toLowerCase();
      const filePath = path.resolve(book.filePath);
      const filePathKey = filePath.toLowerCase();
      if (!filePathKey.startsWith(`${libraryRoot}${path.sep}`) || !existsSync(filePath)) {
        callback({ error: -6 });
        return;
      }
      callback({ path: filePath });
    })().catch(() => callback({ error: -2 }));
  });
}

interface ReadingProgressStore {
  version: 2;
  updatedAt: string;
  items: ReadingProgress[];
}

interface ReadingSessionStore {
  version: 1;
  updatedAt: string;
  sessions: ReadingSession[];
}

function clamp01(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
}

function clampDuration(value: unknown, maxMs = 5 * 60_000): number {
  if (typeof value !== "number" || !Number.isFinite(value)) return 0;
  return Math.max(0, Math.min(maxMs, value));
}

function dateKeyFromDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dateKeyFromIso(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? dateKeyFromDate() : dateKeyFromDate(parsed);
}

function dayStartMs(dateKey: string): number {
  return new Date(`${dateKey}T00:00:00`).getTime();
}

function normalizeBookFormat(value: unknown, filePath?: string): BookFormat {
  if (value === "txt" || value === "md" || value === "epub") return value;
  const ext = filePath ? path.extname(filePath).toLowerCase() : "";
  if (ext === ".epub") return "epub";
  if (ext === ".md" || ext === ".markdown") return "md";
  return "txt";
}

function defaultLocation(format: BookFormat, progressPercent = 0): ReadingLocation {
  const progress = clamp01(progressPercent);
  if (format === "epub") {
    return {
      format,
      mode: "epub-cfi",
      progressPercent: progress,
      precision: "estimated",
      epub: {},
      updatedAt: now()
    };
  }
  return {
    format,
    mode: "scroll",
    progressPercent: progress,
    precision: "estimated",
    scroll: {
      scrollTop: 0,
      scrollHeight: 1,
      containerHeight: 1
    },
    updatedAt: now()
  };
}

function normalizeLocation(value: unknown, fallbackFormat: BookFormat): ReadingLocation | null {
  if (!isRecord(value)) return null;
  const format = normalizeBookFormat(value.format, undefined) || fallbackFormat;
  const mode =
    value.mode === "text-anchor" || value.mode === "epub-cfi" || value.mode === "page" || value.mode === "scroll" ? value.mode : "scroll";
  const progressPercent = clamp01(value.progressPercent);
  const location: ReadingLocation = {
    format,
    mode,
    progressPercent,
    precision: value.precision === "exact" ? "exact" : "estimated",
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now()
  };
  if (isRecord(value.scroll)) {
    location.scroll = {
      scrollTop: typeof value.scroll.scrollTop === "number" ? Math.max(0, value.scroll.scrollTop) : 0,
      scrollHeight: typeof value.scroll.scrollHeight === "number" ? Math.max(1, value.scroll.scrollHeight) : 1,
      containerHeight: typeof value.scroll.containerHeight === "number" ? Math.max(1, value.scroll.containerHeight) : 1
    };
  }
  if (isRecord(value.text)) {
    location.text = {
      charOffset: typeof value.text.charOffset === "number" ? Math.max(0, value.text.charOffset) : undefined,
      chapterRef: optionalString(value.text.chapterRef),
      headingPath: Array.isArray(value.text.headingPath) ? value.text.headingPath.filter((item): item is string => typeof item === "string") : undefined,
      anchorText: optionalString(value.text.anchorText)
    };
  }
  if (isRecord(value.epub)) {
    location.epub = {
      cfi: optionalString(value.epub.cfi),
      href: optionalString(value.epub.href),
      spineIndex: typeof value.epub.spineIndex === "number" ? Math.max(0, value.epub.spineIndex) : undefined,
      chapterRef: optionalString(value.epub.chapterRef)
    };
  }
  if (isRecord(value.page)) {
    location.page = {
      pageIndex: typeof value.page.pageIndex === "number" ? Math.max(0, value.page.pageIndex) : 0,
      pageCount: typeof value.page.pageCount === "number" ? Math.max(1, value.page.pageCount) : undefined
    };
  }
  if (isRecord(value.sourceVersion)) {
    location.sourceVersion = {
      fileSize: typeof value.sourceVersion.fileSize === "number" ? Math.max(0, value.sourceVersion.fileSize) : undefined,
      modifiedAt: typeof value.sourceVersion.modifiedAt === "string" ? value.sourceVersion.modifiedAt : undefined,
      contentHash: optionalString(value.sourceVersion.contentHash)
    };
  }
  return location;
}

function normalizeProgressItem(value: unknown): ReadingProgress | null {
  if (!isRecord(value)) return null;
  const bookId = typeof value.bookId === "string" ? value.bookId : "";
  const filePath = typeof value.filePath === "string" ? value.filePath : "";
  if (!bookId || !filePath) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const legacyProgress = clamp01(typeof value.progress === "number" ? value.progress : value.progressPercent);
  const legacyLocation: ReadingLocation = {
    ...defaultLocation(format, legacyProgress),
    scroll: {
      scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : 0,
      scrollHeight: typeof value.scrollHeight === "number" ? Math.max(1, value.scrollHeight) : 1,
      containerHeight: typeof value.clientHeight === "number" ? Math.max(1, value.clientHeight) : 1
    },
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : typeof value.lastReadAt === "string" ? value.lastReadAt : now()
  };
  const currentLocation = normalizeLocation(value.currentLocation, format) ?? legacyLocation;
  const progressPercent = clamp01(typeof value.progressPercent === "number" ? value.progressPercent : currentLocation.progressPercent);
  const completionState: ReadingProgress["completionState"] =
    value.completionState === "completed" || value.completionState === "unread" ? value.completionState : "reading";
  return withSyncMetadata({
    bookId,
    filePath,
    format,
    currentLocation: { ...currentLocation, progressPercent },
    progressPercent,
    lastReadAt: typeof value.lastReadAt === "string" ? value.lastReadAt : now(),
    totalReadingTimeMs: typeof value.totalReadingTimeMs === "number" ? Math.max(0, value.totalReadingTimeMs) : 0,
    lastSessionId: typeof value.lastSessionId === "string" ? value.lastSessionId : undefined,
    completionState,
    completedAt: typeof value.completedAt === "string" ? value.completedAt : undefined,
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now(),
    revision: typeof value.revision === "number" ? value.revision : undefined,
    deviceId: optionalString(value.deviceId),
    deletedAt: optionalString(value.deletedAt)
  });
}

async function readProgressList(options: { includeDeleted?: boolean } = {}): Promise<ReadingProgress[]> {
  const raw = await readJson<unknown>(readingProgressPath(), { version: 2, updatedAt: now(), items: [] });
  const items = Array.isArray(raw) ? raw : isRecord(raw) && Array.isArray(raw.items) ? raw.items : [];
  return items
    .map(normalizeProgressItem)
    .filter((item): item is ReadingProgress => item !== null)
    .filter((item) => options.includeDeleted || !item.deletedAt);
}

async function writeProgressList(items: ReadingProgress[]): Promise<void> {
  await writeJson<ReadingProgressStore>(readingProgressPath(), { version: 2, updatedAt: now(), items });
}

async function getProgress(bookId: string): Promise<ReadingProgress | undefined> {
  return (await readProgressList()).find((item) => item.bookId === bookId);
}

async function totalReadingTimeForBook(bookId: string, sessions?: ReadingSession[]): Promise<number> {
  const source = sessions ?? (await readSessionList());
  return source.filter((session) => session.bookId === bookId).reduce((sum, session) => sum + Math.max(0, session.activeDurationMs), 0);
}

async function saveProgress(input: SaveProgressInput): Promise<ReadingProgress> {
  const book = (await readLibraryIndex()).find((item) => item.id === input.bookId);
  if (!book) throw new Error("Book not found.");
  const list = await readProgressList();
  const current = list.find((item) => item.bookId === input.bookId);
  const location = normalizeLocation(input.location, book.format) ?? defaultLocation(book.format);
  const timestamp = now();
  const totalReadingTimeMs = await totalReadingTimeForBook(book.id);
  const next: ReadingProgress = {
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    ...nextSyncMetadata(current),
    currentLocation: { ...location, updatedAt: timestamp },
    progressPercent: location.progressPercent,
    lastReadAt: timestamp,
    totalReadingTimeMs,
    lastSessionId: current?.lastSessionId,
    completionState: location.progressPercent >= 0.995 ? "completed" : "reading",
    completedAt: location.progressPercent >= 0.995 ? current?.completedAt ?? timestamp : undefined,
    updatedAt: timestamp
  };
  await writeProgressList([next, ...list.filter((item) => item.bookId !== input.bookId)]);
  return next;
}

function normalizeSessionItem(value: unknown): ReadingSession | null {
  if (!isRecord(value)) return null;
  const id = typeof value.id === "string" ? value.id : "";
  const bookId = typeof value.bookId === "string" ? value.bookId : "";
  const filePath = typeof value.filePath === "string" ? value.filePath : "";
  if (!id || !bookId || !filePath) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const startAt = typeof value.startAt === "string" ? value.startAt : typeof value.startedAt === "string" ? value.startedAt : now();
  const activeDurationMs =
    typeof value.activeDurationMs === "number" ? Math.max(0, value.activeDurationMs) : typeof value.durationMs === "number" ? Math.max(0, value.durationMs) : 0;
  const status: ReadingSession["status"] =
    value.status === "active" || value.status === "paused" || value.status === "ended" || value.status === "recovered" ? value.status : value.endAt ? "ended" : "active";
  const source: ReadingSession["source"] = value.source === "restore" || value.source === "switchBook" ? value.source : "manualOpen";
  const pauseReason: ReadingSession["pauseReason"] =
    value.pauseReason === "idle" || value.pauseReason === "window-blur" || value.pauseReason === "leave-reader" ? value.pauseReason : undefined;
  const endReason: ReadingSession["endReason"] =
    value.endReason === "leave-reader" ||
    value.endReason === "switch-book" ||
    value.endReason === "window-close" ||
    value.endReason === "idle-timeout" ||
    value.endReason === "crash-recovered"
      ? value.endReason
      : undefined;
  return withSyncMetadata({
    id,
    bookId,
    filePath,
    format,
    startAt,
    endAt: typeof value.endAt === "string" ? value.endAt : undefined,
    durationMs: activeDurationMs,
    activeDurationMs,
    idleDurationMs: typeof value.idleDurationMs === "number" ? Math.max(0, value.idleDurationMs) : 0,
    wallDurationMs: typeof value.wallDurationMs === "number" ? Math.max(0, value.wallDurationMs) : Math.max(0, Date.now() - new Date(startAt).getTime()),
    startLocation: normalizeLocation(value.startLocation, format) ?? defaultLocation(format, clamp01(value.startProgress)),
    endLocation: normalizeLocation(value.endLocation, format) ?? undefined,
    dateKey: typeof value.dateKey === "string" ? value.dateKey : dateKeyFromIso(startAt),
    dailyActiveMs: isRecord(value.dailyActiveMs)
      ? Object.fromEntries(Object.entries(value.dailyActiveMs).filter((entry): entry is [string, number] => typeof entry[1] === "number" && entry[1] > 0))
      : undefined,
    status,
    source,
    pauseReason,
    endReason,
    createdAt: typeof value.createdAt === "string" ? value.createdAt : startAt,
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now(),
    lastPersistAt: typeof value.lastPersistAt === "string" ? value.lastPersistAt : typeof value.updatedAt === "string" ? value.updatedAt : now(),
    revision: typeof value.revision === "number" ? value.revision : undefined,
    deviceId: optionalString(value.deviceId),
    deletedAt: optionalString(value.deletedAt)
  });
}

async function readSessionList(options: { includeDeleted?: boolean } = {}): Promise<ReadingSession[]> {
  const raw = await readJson<unknown>(readingSessionsPath(), { version: 1, updatedAt: now(), sessions: [] });
  const sessions = Array.isArray(raw) ? raw : isRecord(raw) && Array.isArray(raw.sessions) ? raw.sessions : [];
  return sessions
    .map(normalizeSessionItem)
    .filter((item): item is ReadingSession => item !== null)
    .filter((item) => options.includeDeleted || !item.deletedAt);
}

async function writeSessionList(sessions: ReadingSession[]): Promise<void> {
  await writeJson<ReadingSessionStore>(readingSessionsPath(), { version: 1, updatedAt: now(), sessions });
}

function applySessionDelta(session: ReadingSession, input: UpdateReadingSessionInput | EndReadingSessionInput, timestamp: string): ReadingSession {
  const activeDeltaMs = clampDuration(input.activeDeltaMs);
  const idleDeltaMs = clampDuration(input.idleDeltaMs);
  const dayKey = dateKeyFromIso(timestamp);
  const dailyActiveMs = { ...(session.dailyActiveMs ?? {}) };
  if (activeDeltaMs > 0) dailyActiveMs[dayKey] = (dailyActiveMs[dayKey] ?? 0) + activeDeltaMs;
  const activeDurationMs = session.activeDurationMs + activeDeltaMs;
  const idleDurationMs = session.idleDurationMs + idleDeltaMs;
  return {
    ...session,
    durationMs: activeDurationMs,
    activeDurationMs,
    idleDurationMs,
    wallDurationMs: Math.max(0, new Date(timestamp).getTime() - new Date(session.startAt).getTime()),
    endLocation: input.location ?? session.endLocation,
    dailyActiveMs,
    updatedAt: timestamp,
    lastPersistAt: timestamp
  };
}

async function syncProgressTotalForBook(bookId: string, sessionId?: string): Promise<void> {
  const list = await readProgressList();
  const current = list.find((item) => item.bookId === bookId);
  if (!current) return;
  const next: ReadingProgress = {
    ...current,
    ...nextSyncMetadata(current),
    totalReadingTimeMs: await totalReadingTimeForBook(bookId),
    lastSessionId: sessionId ?? current.lastSessionId,
    updatedAt: now()
  };
  await writeProgressList([next, ...list.filter((item) => item.bookId !== bookId)]);
}

async function syncAllProgressTotals(): Promise<void> {
  const sessions = await readSessionList();
  const list = await readProgressList();
  const next = await Promise.all(
    list.map(async (progress) => ({
      ...progress,
      totalReadingTimeMs: await totalReadingTimeForBook(progress.bookId, sessions)
    }))
  );
  await writeProgressList(next);
}

async function startReadingSession(input: StartReadingSessionInput): Promise<ReadingSession> {
  const book = (await readLibraryIndex()).find((item) => item.id === input.bookId);
  if (!book) throw new Error("Book not found.");
  const timestamp = now();
  const location = normalizeLocation(input.location, book.format) ?? defaultLocation(book.format);
  const session: ReadingSession = {
    id: makeId("session"),
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    startAt: timestamp,
    durationMs: 0,
    activeDurationMs: 0,
    idleDurationMs: 0,
    wallDurationMs: 0,
    startLocation: location,
    endLocation: location,
    dateKey: dateKeyFromIso(timestamp),
    dailyActiveMs: {},
    revision: 1,
    deviceId: currentDeviceId(),
    status: "active",
    source: input.source === "restore" || input.source === "switchBook" ? input.source : "manualOpen",
    createdAt: timestamp,
    updatedAt: timestamp,
    lastPersistAt: timestamp
  };
  await writeSessionList([session, ...(await readSessionList())]);
  return session;
}

async function updateReadingSession(input: UpdateReadingSessionInput): Promise<ReadingSession> {
  const sessions = await readSessionList();
  const session = sessions.find((item) => item.id === input.sessionId);
  if (!session) throw new Error("Reading session not found.");
  if (session.status === "ended" || session.status === "recovered") return session;
  const timestamp = now();
  const updated: ReadingSession = {
    ...applySessionDelta(session, input, timestamp),
    ...nextSyncMetadata(session),
    status: input.status === "paused" ? "paused" : "active",
    pauseReason: input.status === "paused" ? input.pauseReason : undefined
  };
  await writeSessionList([updated, ...sessions.filter((item) => item.id !== input.sessionId)]);
  return updated;
}

async function endReadingSession(input: EndReadingSessionInput): Promise<ReadingSession> {
  const sessions = await readSessionList();
  const session = sessions.find((item) => item.id === input.sessionId);
  if (!session) throw new Error("Reading session not found.");
  if (session.status === "ended" || session.status === "recovered") return session;
  const timestamp = now();
  const ended: ReadingSession = {
    ...applySessionDelta(session, input, timestamp),
    ...nextSyncMetadata(session),
    status: "ended",
    endAt: timestamp,
    endReason: input.endReason,
    pauseReason: undefined
  };
  await writeSessionList([ended, ...sessions.filter((item) => item.id !== input.sessionId)]);
  await syncProgressTotalForBook(ended.bookId, ended.id);
  return ended;
}

async function recoverUnfinishedSessions(): Promise<RecoverReadingSessionsResult> {
  const sessions = await readSessionList();
  let changed = false;
  let recoveredCount = 0;
  const recovered = sessions.map((session) => {
    if ((session.status !== "active" && session.status !== "paused") || session.endAt) return session;
    changed = true;
    recoveredCount += 1;
    const endAt = session.lastPersistAt || session.updatedAt || session.startAt;
    return {
      ...session,
      ...nextSyncMetadata(session),
      status: "recovered" as const,
      endAt,
      endReason: "crash-recovered" as const,
      wallDurationMs: Math.max(0, new Date(endAt).getTime() - new Date(session.startAt).getTime()),
      updatedAt: now(),
      lastPersistAt: endAt
    };
  });
  if (changed) {
    await writeSessionList(recovered);
    await syncAllProgressTotals();
  }
  return { recoveredCount, sessions: recovered };
}

async function getReadingSessions(input?: GetReadingSessionsInput): Promise<ReadingSession[]> {
  const limit = typeof input?.limit === "number" ? Math.min(500, Math.max(1, Math.round(input.limit))) : 100;
  const includeActive = input?.includeActive !== false;
  return (await readSessionList())
    .filter((session) => (!input?.bookId || session.bookId === input.bookId) && (includeActive || (session.status !== "active" && session.status !== "paused")))
    .sort((a, b) => new Date(b.startAt).getTime() - new Date(a.startAt).getTime())
    .slice(0, limit);
}

async function getReadingStats(): Promise<ReadingStatsSummary> {
  const [sessions, books, progressList] = await Promise.all([readSessionList(), readLibraryIndex(), readProgressList()]);
  const bookById = new Map(books.map((book) => [book.id, book]));
  const progressById = new Map(progressList.map((progress) => [progress.bookId, progress]));
  const dailyTotals = new Map<string, number>();
  const bookTotals = new Map<string, number>();
  let totalDurationMs = 0;
  let countedSessions = 0;

  for (const session of sessions) {
    const activeDuration = Math.max(0, session.activeDurationMs);
    if (activeDuration <= 0) continue;
    countedSessions += 1;
    totalDurationMs += activeDuration;
    bookTotals.set(session.bookId, (bookTotals.get(session.bookId) ?? 0) + activeDuration);
    const daily = session.dailyActiveMs && Object.keys(session.dailyActiveMs).length > 0 ? session.dailyActiveMs : { [session.dateKey]: activeDuration };
    for (const [dateKey, duration] of Object.entries(daily)) {
      dailyTotals.set(dateKey, (dailyTotals.get(dateKey) ?? 0) + Math.max(0, duration));
    }
  }

  const todayKey = dateKeyFromDate();
  const todayStart = dayStartMs(todayKey);
  const durationForRange = (days: number): number => {
    let total = 0;
    for (const [dateKey, duration] of dailyTotals) {
      const diff = Math.floor((todayStart - dayStartMs(dateKey)) / 86_400_000);
      if (diff >= 0 && diff < days) total += duration;
    }
    return total;
  };

  const byBook = [...bookTotals.entries()]
    .map(([bookId, duration]) => {
      const book = bookById.get(bookId);
      const progress = progressById.get(bookId);
      return {
        bookId,
        title: book?.title ?? path.basename(progress?.filePath ?? bookId),
        format: book?.format ?? progress?.format ?? "txt",
        totalDurationMs: duration,
        lastReadAt: progress?.lastReadAt,
        progressPercent: progress?.progressPercent
      };
    })
    .sort((a, b) => b.totalDurationMs - a.totalDurationMs);

  const recentBooks = progressList
    .filter((progress) => progress.lastReadAt)
    .sort((a, b) => new Date(b.lastReadAt).getTime() - new Date(a.lastReadAt).getTime())
    .slice(0, 10)
    .map((progress) => {
      const book = bookById.get(progress.bookId);
      return {
        bookId: progress.bookId,
        title: book?.title ?? path.basename(progress.filePath),
        format: book?.format ?? progress.format,
        lastReadAt: progress.lastReadAt,
        progressPercent: progress.progressPercent,
        totalDurationMs: progress.totalReadingTimeMs
      };
    });

  return {
    todayDurationMs: durationForRange(1),
    last7DaysDurationMs: durationForRange(7),
    last30DaysDurationMs: durationForRange(30),
    totalDurationMs,
    byBook,
    recentBooks,
    recentSessions: sessions
      .filter((session) => session.activeDurationMs >= 1000)
      .sort((a, b) => new Date(b.startAt).getTime() - new Date(a.startAt).getTime())
      .slice(0, 20),
    readingDaysCount: [...dailyTotals.values()].filter((duration) => duration > 0).length,
    averageSessionDurationMs: countedSessions > 0 ? Math.round(totalDurationMs / countedSessions) : 0,
    daily: [...dailyTotals.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([dateKey, durationMs]) => ({ dateKey, durationMs }))
  };
}

async function getReaderSettings(): Promise<ReaderSettings> {
  return (await getAppSettings()).reader;
}

async function updateReaderSettings(patch: Partial<ReaderSettings>): Promise<ReaderSettings> {
  return (await updateAppSettings({ reader: patch })).reader;
}

async function openBook(bookId: string): Promise<ReaderBookPayload> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) throw new Error("Book not found.");
  if (book.format === "epub") throw new Error("Use reader:openEpub for EPUB books.");
  await writeLog("info", "Text reader opened.", { bookId, format: book.format });
  return {
    book,
    content: await readTextFile(book.filePath),
    progress: await getProgress(bookId),
    settings: await getReaderSettings()
  };
}

async function openEpub(bookId: string): Promise<ReaderEpubPayload> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) throw new Error("Book not found.");
  if (book.format !== "epub") throw new Error("Book is not EPUB.");
  await writeLog("info", "EPUB reader opened.", { bookId });
  return {
    book,
    epubUrl: epubUrlForBook(book.id),
    progress: await getProgress(bookId),
    settings: await getReaderSettings(),
    toc: book.epub?.toc ?? []
  };
}

async function saveEpubLocation(input: SaveProgressInput): Promise<ReadingProgress> {
  return saveProgress({
    bookId: input.bookId,
    location: {
      ...input.location,
      format: "epub",
      mode: "epub-cfi"
    }
  });
}

async function getEpubLocation(bookId: string): Promise<ReadingLocation | undefined> {
  return (await getProgress(bookId))?.currentLocation;
}

function scoreFor(keyword: string, title: string, metaText: string, content: string): number {
  const needle = keyword.toLowerCase();
  let score = 0;
  if (title.toLowerCase().includes(needle)) score += 20;
  if (metaText.toLowerCase().includes(needle)) score += 8;
  if (content.toLowerCase().includes(needle)) score += 4;
  return score;
}

function snippetFor(keyword: string, text: string, fallback: string): string {
  const clean = text.replace(/\s+/g, " ").trim();
  if (!clean) return fallback;
  const index = clean.toLowerCase().indexOf(keyword.toLowerCase());
  if (index < 0) return clean.slice(0, 140);
  const start = Math.max(0, index - 55);
  const end = Math.min(clean.length, index + keyword.length + 85);
  return `${start > 0 ? "..." : ""}${clean.slice(start, end)}${end < clean.length ? "..." : ""}`;
}

async function searchGlobal(query: SearchQuery): Promise<SearchResult[]> {
  const keyword = typeof query.keyword === "string" ? query.keyword.trim() : "";
  if (!keyword) return [];
  const scopes = Array.isArray(query.scopes) && query.scopes.length > 0 ? query.scopes : ["library", "inspiration"];
  const limit = typeof query.limit === "number" ? Math.min(100, Math.max(1, query.limit)) : 50;
  const results: SearchResult[] = [];
  const pushResult = (type: SearchResultType, title: string, metaText: string, content: string, sourcePath: string | undefined, target: SearchResult["target"]) => {
    const score = scoreFor(keyword, title, metaText, content);
    if (score <= 0) return;
    results.push({
      id: makeId("result"),
      type,
      title,
      snippet: snippetFor(keyword, `${metaText}\n${content}`, title),
      score,
      sourcePath,
      target
    });
  };
  const pushScoredResult = (
    type: SearchResultType,
    displayTitle: string,
    scoreTitle: string,
    metaText: string,
    content: string,
    sourcePath: string | undefined,
    target: SearchResult["target"]
  ) => {
    const score = scoreFor(keyword, scoreTitle, metaText, content);
    if (score <= 0) return;
    results.push({
      id: makeId("result"),
      type,
      title: displayTitle,
      snippet: snippetFor(keyword, `${metaText}\n${content}`, displayTitle),
      score,
      sourcePath,
      target
    });
  };

  if (scopes.includes("library")) {
    for (const book of await readLibraryIndex()) {
      if (book.format === "epub") {
        const metaText = `${book.format}\n${book.author ?? ""}\n${book.description ?? ""}\n${book.originalPath ?? ""}\n${book.filePath}`;
        const index = await readEpubSearchIndex(book.id);
        if (index) {
          for (const item of index.items) {
            const itemMetaText = item.href;
            pushScoredResult("book", `${book.title} · ${item.title}`, item.title, itemMetaText, item.text, book.originalPath ?? book.filePath, {
              bookId: book.id,
              epubHref: item.href
            });
          }
        }
        pushResult("book", book.title, metaText, "", book.originalPath ?? book.filePath, { bookId: book.id });
        continue;
      }
      const canReadBody = book.format === "txt" || book.format === "md";
      let content = "";
      if (canReadBody) {
        content = await readTextFileIfWithinLimit(book.filePath);
      }
      pushResult("book", book.title, `${book.format}\n${book.originalPath ?? ""}\n${book.filePath}`, content, book.originalPath ?? book.filePath, { bookId: book.id });
    }
  }

  if (scopes.includes("inspiration")) {
    for (const item of await readInspirations()) {
      pushResult(
        "inspiration",
        item.title,
        `${item.type}\n${item.status}\n${item.tags.join(" ")}\n${item.platformTags.join(" ")}\n${item.source?.bookTitle ?? ""}\n${item.source?.bookAuthor ?? ""}\n${item.source?.locationLabel ?? ""}`,
        `${item.body}\n${item.source?.excerpt ?? ""}\n${item.variants.map((variant) => variant.content).join("\n")}`,
        inspirationsPath(),
        { inspirationId: item.id }
      );
    }
  }

  return results.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title)).slice(0, limit);
}

function syncTimestamp(value?: string): number {
  const parsed = Date.parse(value ?? "");
  return Number.isFinite(parsed) ? parsed : 0;
}

function listLanAddresses(): string[] {
  const addresses = new Set<string>(["127.0.0.1"]);
  const interfaces = networkInterfaces();
  for (const items of Object.values(interfaces)) {
    for (const item of items ?? []) {
      if (item.family === "IPv4" && !item.internal) addresses.add(item.address);
    }
  }
  return [...addresses];
}

function scorePairingAddress(address: string): number {
  if (address === "127.0.0.1") return -1000;
  let score = 0;
  if (!address.startsWith("169.254.")) score += 50;
  if (!address.startsWith("192.168.137.")) score += 30;
  if (!address.endsWith(".1")) score += 10;
  if (address.startsWith("192.168.") || address.startsWith("10.") || address.startsWith("172.")) score += 5;
  return score;
}

function pairingAddresses(addresses: string[]): string[] {
  const candidates = addresses.filter((address) => address !== "127.0.0.1");
  return (candidates.length > 0 ? candidates : ["127.0.0.1"]).sort((a, b) => scorePairingAddress(b) - scorePairingAddress(a));
}

async function desktopDeviceInfo(): Promise<DeviceInfo> {
  const state = await readSyncState();
  return {
    deviceId: state.deviceId,
    name: "电脑端 · 创作阅读助手",
    platform: "desktop",
    pairedAt: state.updatedAt,
    lastSeenAt: now()
  };
}

function normalizeDeviceInfo(value: unknown): DeviceInfo | undefined {
  if (!isRecord(value)) return undefined;
  const deviceId = optionalString(value.deviceId);
  if (!deviceId) return undefined;
  return {
    deviceId,
    name: optionalString(value.name) ?? "手机端",
    platform: value.platform === "android" || value.platform === "ios" || value.platform === "web" || value.platform === "desktop" ? value.platform : "android",
    pairedAt: optionalString(value.pairedAt) ?? now(),
    lastSeenAt: optionalString(value.lastSeenAt) ?? now()
  };
}

async function upsertPairedDevice(device: DeviceInfo): Promise<void> {
  const state = await readSyncState();
  const existing = state.devices.find((item) => item.deviceId === device.deviceId);
  const nextDevice: DeviceInfo = {
    ...device,
    pairedAt: existing?.pairedAt ?? device.pairedAt ?? now(),
    lastSeenAt: now()
  };
  state.devices = [nextDevice, ...state.devices.filter((item) => item.deviceId !== device.deviceId)];
  await writeSyncState(state);
}

async function listPairedDevices(): Promise<DeviceInfo[]> {
  return (await readSyncState()).devices;
}

async function removePairedDevice(deviceId: string): Promise<DeviceInfo[]> {
  const state = await readSyncState();
  state.devices = state.devices.filter((item) => item.deviceId !== deviceId);
  await writeSyncState(state);
  return state.devices;
}

function toManifestEnvelope<TPayload>(
  type: SyncRecordType,
  id: string,
  item: { revision: number; deviceId: string; updatedAt: string; deletedAt?: string },
  payload: TPayload
): SyncEnvelope<TPayload> {
  return {
    id,
    type,
    revision: item.revision,
    deviceId: item.deviceId,
    updatedAt: item.updatedAt,
    deletedAt: item.deletedAt,
    payload
  };
}

function toFullEnvelope<TPayload extends { revision: number; deviceId: string; updatedAt: string; deletedAt?: string }>(
  type: SyncRecordType,
  id: string,
  payload: TPayload
): SyncEnvelope<TPayload> {
  return toManifestEnvelope(type, id, payload, payload);
}

async function buildBookFileManifest(books?: LibraryBook[]): Promise<BookFileManifest[]> {
  const source = books ?? (await readLibraryIndex({ includeDeleted: true }));
  const manifests: BookFileManifest[] = [];
  for (const book of source) {
    if (book.deletedAt) continue;
    try {
      const fileStats = await stat(book.filePath);
      manifests.push({
        bookId: book.id,
        fileName: book.originalFileName ?? path.basename(book.filePath),
        format: book.format,
        contentHash: book.contentHash,
        size: fileStats.size,
        chunkSize: SYNC_CHUNK_SIZE
      });
    } catch {
      await writeLog("warn", "Sync book file manifest skipped missing file.", { bookId: book.id, filePath: book.filePath });
    }
  }
  return manifests;
}

async function buildSyncManifest(): Promise<SyncManifest> {
  const [inspirations, books, progress, sessions] = await Promise.all([
    readInspirations({ includeDeleted: true }),
    readLibraryIndex({ includeDeleted: true }),
    readProgressList({ includeDeleted: true }),
    readSessionList({ includeDeleted: true })
  ]);
  return {
    device: await desktopDeviceInfo(),
    generatedAt: now(),
    inspirations: inspirations.map((item) => toManifestEnvelope("inspiration", item.id, item, { id: item.id })),
    books: books.map((item) => toManifestEnvelope("book", item.id, item, { id: item.id })),
    progress: progress.map((item) => toManifestEnvelope("progress", item.bookId, item, { bookId: item.bookId })),
    sessions: sessions.map((item) => toManifestEnvelope("session", item.id, item, { id: item.id })),
    bookFiles: await buildBookFileManifest(books)
  };
}

async function buildSyncPullResponse(): Promise<SyncPullResponse> {
  const [inspirations, books, progress, sessions] = await Promise.all([
    readInspirations({ includeDeleted: true }),
    readLibraryIndex({ includeDeleted: true }),
    readProgressList({ includeDeleted: true }),
    readSessionList({ includeDeleted: true })
  ]);
  return {
    manifest: await buildSyncManifest(),
    inspirations: inspirations.map((item) => toFullEnvelope("inspiration", item.id, item)),
    books: books.map((item) => toFullEnvelope("book", item.id, item)),
    progress: progress.map((item) => toFullEnvelope("progress", item.bookId, item)),
    sessions: sessions.map((item) => toFullEnvelope("session", item.id, item))
  };
}

function mergeByUpdatedAt<T extends { updatedAt: string; revision: number; deviceId: string; deletedAt?: string }>(current: T | undefined, incoming: T): T {
  if (!current) return incoming;
  const currentTime = syncTimestamp(current.updatedAt);
  const incomingTime = syncTimestamp(incoming.updatedAt);
  if (incomingTime > currentTime) return incoming;
  if (incomingTime === currentTime && incoming.revision > current.revision) return incoming;
  return current;
}

function mergeIncomingInspirations(
  currentItems: InspirationItem[],
  incomingEnvelopes: Array<SyncEnvelope<InspirationItem>>
): { items: InspirationItem[]; conflicts: Array<SyncEnvelope<InspirationItem>>; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  const conflicts: Array<SyncEnvelope<InspirationItem>> = [];
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeInspirationItem(envelope.payload);
    if (!incoming) continue;
    const current = byId.get(incoming.id);
    if (current && !current.deletedAt && !incoming.deletedAt && current.deviceId !== incoming.deviceId && current.body !== incoming.body) {
      const winner = mergeByUpdatedAt(current, incoming);
      const loser = winner === current ? incoming : current;
      const timestamp = now();
      const conflict = withSyncMetadata<InspirationItem>({
        ...loser,
        id: makeId("insp-conflict"),
        title: `${loser.title}（冲突副本）`,
        createdAt: timestamp,
        updatedAt: timestamp,
        revision: 1,
        deviceId: currentDeviceId(),
        deletedAt: undefined
      });
      byId.set(incoming.id, winner);
      byId.set(conflict.id, conflict);
      conflicts.push(toFullEnvelope("inspiration", conflict.id, conflict));
      applied += 1;
      continue;
    }
    byId.set(incoming.id, mergeByUpdatedAt(current, incoming));
    applied += 1;
  }
  return { items: [...byId.values()], conflicts, applied };
}

function mergeLibraryBooksById(currentItems: LibraryBook[], incomingEnvelopes: Array<SyncEnvelope<LibraryBook>>): { items: LibraryBook[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeLibraryBook(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.id, mergeByUpdatedAt(byId.get(incoming.id), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

function mergeReadingProgressByBookId(
  currentItems: ReadingProgress[],
  incomingEnvelopes: Array<SyncEnvelope<ReadingProgress>>
): { items: ReadingProgress[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.bookId, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeProgressItem(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.bookId, mergeByUpdatedAt(byId.get(incoming.bookId), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

function mergeReadingSessionsById(
  currentItems: ReadingSession[],
  incomingEnvelopes: Array<SyncEnvelope<ReadingSession>>
): { items: ReadingSession[]; applied: number } {
  const byId = new Map(currentItems.map((item) => [item.id, item]));
  let applied = 0;
  for (const envelope of incomingEnvelopes) {
    const incoming = normalizeSessionItem(envelope.payload);
    if (!incoming) continue;
    byId.set(incoming.id, mergeByUpdatedAt(byId.get(incoming.id), incoming));
    applied += 1;
  }
  return { items: [...byId.values()], applied };
}

async function applySyncPush(payload: SyncPushPayload): Promise<SyncPushResult> {
  if (payload.device) await upsertPairedDevice(payload.device);

  const incomingInspirations = Array.isArray(payload.inspirations) ? payload.inspirations : [];
  const incomingBooks = Array.isArray(payload.books) ? payload.books : [];
  const incomingProgress = Array.isArray(payload.progress) ? payload.progress : [];
  const incomingSessions = Array.isArray(payload.sessions) ? payload.sessions : [];

  const inspirationMerge = mergeIncomingInspirations(await readInspirations({ includeDeleted: true }), incomingInspirations);
  if (incomingInspirations.length > 0) await writeInspirations(inspirationMerge.items);

  const bookMerge = mergeLibraryBooksById(await readLibraryIndex({ includeDeleted: true }), incomingBooks);
  if (incomingBooks.length > 0) await writeLibraryIndex(bookMerge.items);

  const progressMerge = mergeReadingProgressByBookId(await readProgressList({ includeDeleted: true }), incomingProgress);
  if (incomingProgress.length > 0) await writeProgressList(progressMerge.items);

  const sessionMerge = mergeReadingSessionsById(await readSessionList({ includeDeleted: true }), incomingSessions);
  if (incomingSessions.length > 0) await writeSessionList(sessionMerge.items);

  await syncAllProgressTotals();

  return {
    ok: true,
    applied: {
      inspirations: inspirationMerge.applied,
      books: bookMerge.applied,
      progress: progressMerge.applied,
      sessions: sessionMerge.applied
    },
    conflicts: inspirationMerge.conflicts,
    manifest: await buildSyncManifest()
  };
}

function isPairingTokenValid(token: string | undefined): boolean {
  return Boolean(pairingToken && token && pairingToken.token === token && syncTimestamp(pairingToken.expiresAt) > Date.now());
}

async function getSyncStatus(): Promise<SyncStatus> {
  if (pairingToken && syncTimestamp(pairingToken.expiresAt) <= Date.now()) pairingToken = undefined;
  return {
    running: Boolean(syncServer && syncServerPort),
    port: syncServerPort,
    addresses: listLanAddresses(),
    device: await desktopDeviceInfo(),
    pairingToken
  };
}

async function startSyncServer(): Promise<SyncStatus> {
  await getOrCreateDeviceId();
  if (syncServer && syncServerPort) return getSyncStatus();
  syncServer = createServer((request, response) => {
    void handleSyncRequest(request, response).catch((error) => {
      void writeLog("error", "Sync server request failed.", error);
      sendJson(response, 500, { ok: false, message: error instanceof Error ? error.message : "同步服务内部错误。" });
    });
  });
  await new Promise<void>((resolve, reject) => {
    const onError = (error: Error) => reject(error);
    if (!syncServer) {
      reject(new Error("同步服务创建失败。"));
      return;
    }
    syncServer.once("error", onError);
    syncServer.listen(0, "0.0.0.0", () => {
      syncServer?.off("error", onError);
      const address = syncServer?.address();
      syncServerPort = typeof address === "object" && address ? address.port : undefined;
      resolve();
    });
  });
  return getSyncStatus();
}

async function stopSyncServer(): Promise<SyncStatus> {
  if (!syncServer) {
    syncServerPort = undefined;
    pairingToken = undefined;
    return getSyncStatus();
  }
  const server = syncServer;
  await new Promise<void>((resolve) => server.close(() => resolve()));
  syncServer = undefined;
  syncServerPort = undefined;
  pairingToken = undefined;
  return getSyncStatus();
}

async function createPairingToken(): Promise<PairingTokenResult> {
  const status = await startSyncServer();
  if (!status.port) throw new Error("同步服务未能启动。");
  const token = crypto.randomBytes(18).toString("hex");
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
  const options = pairingAddresses(status.addresses).map((address) => {
    const pairingUrl = `http://${address}:${status.port}/sync/pair?token=${encodeURIComponent(token)}`;
    const qrPayload = JSON.stringify({
      app: "创作阅读助手",
      version: 1,
      host: address,
      port: status.port,
      token,
      pairingUrl
    });
    return { address, pairingUrl, qrPayload };
  });
  const primary = options[0];
  pairingToken = {
    token,
    pairingUrl: primary.pairingUrl,
    qrPayload: primary.qrPayload,
    pairingUrls: options.map((item) => item.pairingUrl),
    qrPayloads: options,
    expiresAt
  };
  return pairingToken;
}

async function readRequestJson<T>(request: IncomingMessage): Promise<T> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.byteLength;
    if (total > 25 * 1024 * 1024) throw new Error("同步请求过大。");
    chunks.push(buffer);
  }
  const content = Buffer.concat(chunks).toString("utf-8").trim();
  return (content ? JSON.parse(content) : {}) as T;
}

async function readRequestBuffer(request: IncomingMessage, maxBytes = 256 * 1024 * 1024): Promise<Buffer> {
  const chunks: Buffer[] = [];
  let total = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    total += buffer.byteLength;
    if (total > maxBytes) throw new Error("上传的书籍文件过大。");
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}

function sendCorsHeaders(response: ServerResponse): void {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS");
  response.setHeader("Access-Control-Allow-Headers", "content-type,x-device-id,x-original-file-name,X-Original-File-Name");
}

function sendJson(response: ServerResponse, statusCode: number, payload: unknown): void {
  sendCorsHeaders(response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.end(JSON.stringify(payload));
}

function sendText(response: ServerResponse, statusCode: number, payload: string): void {
  sendCorsHeaders(response);
  response.statusCode = statusCode;
  response.setHeader("Content-Type", "text/plain; charset=utf-8");
  response.end(payload);
}

async function downloadBookFile(bookId: string, response: ServerResponse): Promise<void> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "没有找到这本书。" });
    return;
  }
  const fileBuffer = await readFile(book.filePath);
  sendCorsHeaders(response);
  response.statusCode = 200;
  response.setHeader("Content-Type", "application/octet-stream");
  response.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(book.originalFileName ?? path.basename(book.filePath))}`);
  response.setHeader("X-Content-Hash", book.contentHash ?? "");
  response.end(fileBuffer);
}

function requestHeaderString(request: IncomingMessage, headerName: string): string | undefined {
  const value = request.headers[headerName.toLowerCase()];
  return Array.isArray(value) ? value[0] : value;
}

async function writeUploadedBookFile(book: LibraryBook, request: IncomingMessage): Promise<LibraryBook> {
  const rawOriginalFileName = requestHeaderString(request, "X-Original-File-Name");
  const originalFileName = rawOriginalFileName ? decodeURIComponent(rawOriginalFileName) : book.originalFileName ?? `${book.id}.${book.format}`;
  const format = normalizeBookFormat(book.format, originalFileName);
  const targetPath = path.join(appLibraryFilesRoot(), `${book.id}.${format}`);
  await ensureDir(appLibraryFilesRoot());
  const fileBuffer = await readRequestBuffer(request);
  await writeFile(targetPath, fileBuffer);
  const info = await stat(targetPath);
  const hash = crypto.createHash("sha256").update(fileBuffer).digest("hex");
  return {
    ...book,
    filePath: targetPath,
    originalFileName: book.originalFileName ?? originalFileName,
    originalFilePath: book.originalFilePath ?? originalFileName,
    originalPath: book.originalPath ?? originalFileName,
    format,
    size: info.size,
    contentHash: hash,
    updatedAt: now(),
    revision: book.revision + 1,
    deviceId: currentDeviceId()
  };
}

async function uploadBookFile(bookId: string, request: IncomingMessage, response: ServerResponse): Promise<void> {
  const books = await readLibraryIndex({ includeDeleted: true });
  const book = books.find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "请先同步书籍元数据，再上传书籍文件。" });
    return;
  }
  if (book.deletedAt) {
    sendJson(response, 410, { ok: false, message: "这本书已删除，不能继续上传文件。" });
    return;
  }
  const nextBook = await writeUploadedBookFile(book, request);
  await writeLibraryIndex([nextBook, ...books.filter((item) => item.id !== bookId)]);
  sendJson(response, 200, { ok: true, bookId, contentHash: nextBook.contentHash, size: nextBook.size });
}

async function downloadBookChunk(bookId: string, index: number, response: ServerResponse): Promise<void> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) {
    sendJson(response, 404, { ok: false, message: "没有找到这本书。" });
    return;
  }
  const fileStats = await stat(book.filePath);
  const start = index * SYNC_CHUNK_SIZE;
  if (start >= fileStats.size || index < 0) {
    sendJson(response, 416, { ok: false, message: "分块序号超出范围。" });
    return;
  }
  const buffer = await readFile(book.filePath);
  const chunk = buffer.subarray(start, Math.min(start + SYNC_CHUNK_SIZE, buffer.byteLength));
  sendCorsHeaders(response);
  response.statusCode = 200;
  response.setHeader("Content-Type", "application/octet-stream");
  response.setHeader("Content-Range", `bytes ${start}-${start + chunk.byteLength - 1}/${fileStats.size}`);
  response.setHeader("X-Chunk-Index", String(index));
  response.end(chunk);
}

async function handlePairingRequest(request: IncomingMessage, response: ServerResponse, url: URL): Promise<void> {
  const body = request.method === "POST" ? await readRequestJson<{ token?: string; device?: DeviceInfo }>(request) : {};
  const token = body.token ?? url.searchParams.get("token") ?? undefined;
  if (!isPairingTokenValid(token)) {
    sendJson(response, 401, { ok: false, message: "配对码无效或已过期，请在电脑端重新生成。" });
    return;
  }
  const device =
    normalizeDeviceInfo(body.device) ??
    ({
      deviceId: `android-${crypto.createHash("sha256").update(token ?? "").digest("hex").slice(0, 12)}`,
      name: "Android 手机端",
      platform: "android",
      pairedAt: now(),
      lastSeenAt: now()
    } satisfies DeviceInfo);
  await upsertPairedDevice(device);
  pairingToken = undefined;
  sendJson(response, 200, { ok: true, device: await desktopDeviceInfo(), manifest: await buildSyncManifest() });
}

async function handleSyncRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  sendCorsHeaders(response);
  if (request.method === "OPTIONS") {
    response.statusCode = 204;
    response.end();
    return;
  }
  const url = new URL(request.url ?? "/", "http://127.0.0.1");
  if (request.method === "GET" && url.pathname === "/sync/manifest") {
    sendJson(response, 200, await buildSyncManifest());
    return;
  }
  if (request.method === "POST" && url.pathname === "/sync/pull") {
    sendJson(response, 200, await buildSyncPullResponse());
    return;
  }
  if (request.method === "POST" && url.pathname === "/sync/push") {
    const payload = await readRequestJson<SyncPushPayload>(request);
    sendJson(response, 200, await applySyncPush(payload));
    return;
  }
  if (url.pathname === "/sync/pair") {
    await handlePairingRequest(request, response, url);
    return;
  }
  if (url.pathname.startsWith("/sync/books/")) {
    const parts = url.pathname.split("/").map(decodeURIComponent);
    const bookId = parts[3];
    if (request.method === "GET" && parts[4] === "file" && bookId) {
      await downloadBookFile(bookId, response);
      return;
    }
    if (request.method === "PUT" && parts[4] === "file" && bookId) {
      await uploadBookFile(bookId, request, response);
      return;
    }
    if (request.method === "GET" && parts[4] === "chunks" && bookId) {
      await downloadBookChunk(bookId, Number(parts[5] ?? "-1"), response);
      return;
    }
  }
  sendText(response, 404, "Not found");
}

function contentSecurityPolicy(): string {
  return [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob: file: novel-workbench-epub:",
    "font-src 'self' data:",
    "connect-src 'self' novel-workbench-epub: http://localhost:* ws://localhost:*",
    "frame-src 'self' novel-workbench-epub:",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'"
  ].join("; ");
}

function installContentSecurityPolicy(): void {
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    callback({
      responseHeaders: {
        ...details.responseHeaders,
        "Content-Security-Policy": [contentSecurityPolicy()]
      }
    });
  });
}

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    minWidth: 1080,
    minHeight: 720,
    title: "创作阅读助手",
    backgroundColor: "#f5f5f4",
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "../preload/index.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.setMenuBarVisibility(false);
  const rendererUrl = process.env.ELECTRON_RENDERER_URL;
  if (rendererUrl) mainWindow.loadURL(rendererUrl);
  else mainWindow.loadFile(path.join(__dirname, "../renderer/index.html"));
  mainWindow.webContents.on("render-process-gone", (_event, details) => {
    void writeLog("error", "Renderer process gone.", details);
  });
  mainWindow.webContents.on("did-fail-load", (_event, errorCode, errorDescription, validatedURL) => {
    void writeLog("error", "Renderer failed to load.", { errorCode, errorDescription, validatedURL });
  });
}

function registerIpc(): void {
  ipcMain.handle("app:getBuildInfo", async () => getBuildInfo());
  ipcMain.handle("app:getStartupRecovery", async () =>
    startupRecoverySeen ? { ...startupRecoveryInfo, abnormalExit: false, recoveredSessionsCount: 0 } : startupRecoveryInfo
  );
  ipcMain.handle("app:markStartupRecoverySeen", async () => {
    startupRecoverySeen = true;
  });
  ipcMain.handle("app:openDataDirectory", async () => openPathOrThrow(appDataRoot()));
  ipcMain.handle("app:openLogDirectory", async () => openPathOrThrow(logsRoot()));
  ipcMain.handle("app:writeRendererLog", async (_event, input: RendererLogInput) => writeRendererLog(input));
  ipcMain.handle("library:importBook", async () => importBook());
  ipcMain.handle("library:importEpub", async () => importEpub());
  ipcMain.handle("library:listBooks", async () => readLibraryIndex());
  ipcMain.handle("library:removeBook", async (_event, bookId: string) => removeBook(bookId));
  ipcMain.handle("reader:openBook", async (_event, bookId: string) => openBook(bookId));
  ipcMain.handle("reader:openEpub", async (_event, bookId: string) => openEpub(bookId));
  ipcMain.handle("reader:saveProgress", async (_event, input: SaveProgressInput) => saveProgress(input));
  ipcMain.handle("reader:getProgress", async (_event, bookId: string) => getProgress(bookId));
  ipcMain.handle("reader:saveEpubLocation", async (_event, input: SaveProgressInput) => saveEpubLocation(input));
  ipcMain.handle("reader:getEpubLocation", async (_event, bookId: string) => getEpubLocation(bookId));
  ipcMain.handle("reader:startSession", async (_event, input: StartReadingSessionInput) => startReadingSession(input));
  ipcMain.handle("reader:updateSession", async (_event, input: UpdateReadingSessionInput) => updateReadingSession(input));
  ipcMain.handle("reader:endSession", async (_event, input: EndReadingSessionInput) => endReadingSession(input));
  ipcMain.handle("reader:recoverActiveSession", async () => recoverUnfinishedSessions());
  ipcMain.handle("reader:getSessions", async (_event, input?: GetReadingSessionsInput) => getReadingSessions(input));
  ipcMain.handle("reader:getStats", async () => getReadingStats());
  ipcMain.handle("reader:getSettings", async () => getReaderSettings());
  ipcMain.handle("reader:updateSettings", async (_event, settings: Partial<ReaderSettings>) => updateReaderSettings(settings));
  ipcMain.handle("settings:get", async () => getAppSettings());
  ipcMain.handle("settings:update", async (_event, patch: AppSettingsPatch) => updateAppSettings(patch));
  ipcMain.handle("settings:resetSection", async (_event, section: SettingsSection) => resetSettingsSection(section));
  ipcMain.handle("settings:resetReaderSettings", async () => resetReaderSettingsOnly());
  ipcMain.handle("settings:chooseDataDirectory", async () => chooseDataDirectory());
  ipcMain.handle("settings:chooseLibraryDirectory", async () => chooseLibraryDirectory());
  ipcMain.handle("settings:migrateDataDirectory", async (_event, targetDirectory: string) => migrateDataDirectory(targetDirectory));
  ipcMain.handle("settings:migrateLibraryDirectory", async (_event, targetDirectory: string) => migrateLibraryDirectory(targetDirectory));
  ipcMain.handle("storage:getLocations", async () => getStorageLocations());
  ipcMain.handle("inspiration:list", async () => readInspirations());
  ipcMain.handle("inspiration:create", async (_event, input: CreateInspirationInput) => createInspiration(input));
  ipcMain.handle("inspiration:read", async (_event, id: string) => readInspiration(id));
  ipcMain.handle("inspiration:update", async (_event, id: string, input: UpdateInspirationInput) => updateInspiration(id, input));
  ipcMain.handle("inspiration:delete", async (_event, id: string) => deleteInspiration(id));
  ipcMain.handle("inspiration:addVariant", async (_event, id: string, input: AddInspirationVariantInput) => addInspirationVariant(id, input));
  ipcMain.handle("ai:getSettings", async () => getAISettings());
  ipcMain.handle("ai:updateSettings", async (_event, patch: AISettingsPatch) => updateAISettings(patch));
  ipcMain.handle("ai:saveApiKey", async (_event, input: SaveAIApiKeyInput) => saveAIApiKey(input));
  ipcMain.handle("ai:clearApiKey", async () => clearAIApiKey());
  ipcMain.handle("ai:test", async () => testAIConnection());
  ipcMain.handle("ai:run", async (_event, input: AIRunInput) => runAIAction(input));
  ipcMain.handle("search:global", async (_event, query: SearchQuery) => searchGlobal(query));
  ipcMain.handle("sync:getStatus", async () => getSyncStatus());
  ipcMain.handle("sync:startServer", async () => startSyncServer());
  ipcMain.handle("sync:stopServer", async () => stopSyncServer());
  ipcMain.handle("sync:createPairingToken", async () => createPairingToken());
  ipcMain.handle("sync:listDevices", async () => listPairedDevices());
  ipcMain.handle("sync:removeDevice", async (_event, deviceId: string) => removePairedDevice(deviceId));
  ipcMain.handle("backup:create", async () => createBackup());
  ipcMain.handle("backup:restore", async () => restoreBackup());
  ipcMain.handle("diagnostics:exportDebugInfo", async () => exportDebugInfo());
}

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
  process.exit(1);
}

app.on("second-instance", () => {
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.focus();
});

app.whenReady().then(async () => {
  installContentSecurityPolicy();
  registerEpubProtocol();
  await resolveInitialDataRoot();
  await ensureAppStorage();
  await getAppSettings();
  await prepareStartupRecovery();
  registerIpc();
  createWindow();
  await writeLog("info", "Application ready.", getBuildInfo());
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

process.on("uncaughtException", (error) => {
  void writeLog("error", "Uncaught exception.", error).finally(() => {
    process.exit(1);
  });
});

process.on("unhandledRejection", (reason) => {
  void writeLog("error", "Unhandled rejection.", reason);
});

app.on("before-quit", () => {
  writeRuntimeStateSync(true);
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
