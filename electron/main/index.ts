import { app, BrowserWindow, dialog, ipcMain, Notification, protocol, session, shell } from "electron";
import type { OpenDialogOptions, SaveDialogOptions } from "electron";
import { copyFile, mkdir, readFile, readdir, rm, stat, unlink } from "node:fs/promises";
import { existsSync, writeFileSync } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { parseEpubFile } from "./epub-metadata";
import { createCreationCoordinator } from "./creation-coordinator";
import { registerCreationIpc } from "./creation-ipc";
import { registerCreationAssetProtocol } from "./creation-asset-protocol";
import { createOperationCoordinator } from "./operation/coordinator";
import { registerOperationIpc, disposeAllOperations } from "./operation/ipc";
import { BackupError, createBackupSnapshot, readBackupManifest, restoreBackupFromDirectory } from "./backup";
import {
  openCreationWorkspace,
  type ProjectBundleData
} from "./creation-workspace";
import type { ProjectBundleCardResolution, ProjectBundleImportPreview } from "../../src/types/creation";
import { CREATION_ASSET_SCHEME } from "../../src/types/creation";
import {
  AutoBackupError,
  assertSafeExistingBackupTarget,
  createAutoBackupScheduler,
  uniqueAutoBackupRoot,
  type AutoBackupScheduler
} from "./backup/auto-backup";
import type {
  BookmarkItem,
  EndReadingSessionInput,
  GetReadingSessionsInput,
  HighlightItem,
  LibraryBook,
  ReaderSettings,
  SaveProgressInput,
  StartReadingSessionInput,
  TxtTocOverrides,
  UpdateReadingSessionInput
} from "../../src/types/library";
import type { AppSettings, AppSettingsPatch, SettingsSection } from "../../src/types/settings";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  UpdateInspirationInput
} from "../../src/types/inspiration";
import type { AISettingsPatch, AIRunInput, SaveAIApiKeyInput } from "../../src/types/ai";
import type { BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "../../src/types/maintenance";
import type { AppUpdateInfo } from "../../src/types/updates";
import type { SearchQuery } from "../../src/types/search";
import {
  EPUB_PROTOCOL_SCHEME,
  appDataRoot,
  appLibraryCoversRoot,
  appLibraryFilesRoot,
  appLibraryRoot,
  appSettingsPath,
  assertSafeMigrationTarget,
  captureProfileDir,
  copyDirectory,
  currentDeviceId,
  ensureDir,
  epubSearchIndexPath,
  isDirectoryWritable,
  isInsidePath,
  isRecord,
  logsRoot,
  makeId,
  nextSyncMetadata,
  now,
  optionalString,
  readJson,
  readerSettingsPath,
  relocatePathInsideRoot,
  requireString,
  resolveInitialDataRoot,
  runtimeStatePath,
  setActiveDataRoot,
  setActiveStorageFromSettings,
  storageModeForDataRoot,
  timestampForFile,
  unlinkManagedFileIfPresent,
  writeJson,
  writeLog,
  writeStoragePointer
} from "./storage";
import {
  addInspirationVariant,
  createInspiration,
  deleteInspiration,
  readBookmarks,
  readHighlights,
  readInspiration,
  readInspirations,
  updateInspiration,
  writeBookmarks,
  writeHighlights
} from "./inspiration-store";
import {
  contentHash,
  duplicateImportInfo,
  extractTextBookMetadata,
  normalizeTxtTocOverrides,
  readLibraryIndex,
  writeEpubSearchIndex,
  writeLibraryIndex
} from "./library-store";
import {
  endReadingSession,
  getBatchProgress,
  getProgress,
  getReadingSessions,
  getReadingStats,
  readProgressList,
  readSessionList,
  recoverUnfinishedSessions,
  saveProgress,
  startReadingSession,
  updateReadingSession,
  writeProgressList,
  writeSessionList
} from "./progress-store";
import {
  createPairingToken,
  getSyncStatusWithRebind,
  listPairedDevices,
  removePairedDevice,
  startSyncServer,
  stopSyncServer
} from "./sync-server";
import {
  clearAIApiKey,
  defaultAppSettings,
  ensureAppStorage,
  getAISettings,
  getAppSettings,
  getStorageLocations,
  normalizeAppSettings,
  persistAutoBackupState,
  readLegacyReaderSettings,
  resetReaderSettingsOnly,
  resetSettingsSection,
  runAIAction,
  saveAIApiKey,
  testAIConnection,
  updateAISettings,
  updateAppSettings
} from "./settings-store";
import {
  getEpubLocation,
  getReaderSettings,
  openBook,
  openEpub,
  readTextFileIfWithinLimit,
  registerEpubProtocol,
  saveEpubLocation,
  updateReaderSettings
} from "./reader-io";
import { searchGlobal } from "./library-search";

let mainWindow: BrowserWindow | null = null;
let startupRecoveryInfo: StartupRecoveryInfo = {
  abnormalExit: false,
  recoveredSessionsCount: 0,
  checkedAt: new Date().toISOString()
};
let startupRecoverySeen = false;

const creationCoordinator = createCreationCoordinator({
  resolveDirectory: () => path.join(appDataRoot(), "CreationWorkspace")
});

// 长任务协调器：备份 / 项目包 / 资源扫描的统一进度、取消与结果载体。
const operationCoordinator = createOperationCoordinator();

const RELEASE_API_URL = "https://api.github.com/repos/luoshuizhiwei/creation-reading-assistant/releases/latest";

interface GitHubReleaseAsset {
  name?: string;
  browser_download_url?: string;
}

interface GitHubRelease {
  tag_name?: string;
  html_url?: string;
  body?: string;
  assets?: GitHubReleaseAsset[];
}

function normalizeVersionParts(value: string): number[] {
  return value
    .replace(/^v/i, "")
    .split(".")
    .map((part) => Number.parseInt(part.replace(/[^\d].*$/, ""), 10))
    .map((part) => (Number.isFinite(part) ? part : 0));
}

function isNewerVersion(latest: string, current: string): boolean {
  const left = normalizeVersionParts(latest);
  const right = normalizeVersionParts(current);
  const length = Math.max(left.length, right.length, 3);
  for (let index = 0; index < length; index += 1) {
    const latestPart = left[index] ?? 0;
    const currentPart = right[index] ?? 0;
    if (latestPart > currentPart) return true;
    if (latestPart < currentPart) return false;
  }
  return false;
}

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
  },
  {
    // 创作工作区只读资源（全局卡片封面/附件）。只做 `<img>` 展示，因此不开放
    // supportFetchAPI / corsEnabled / stream：渲染进程无法用 fetch 读字节，
    // 也无法把这个来源当作可跨域读取的资源。
    scheme: CREATION_ASSET_SCHEME,
    privileges: {
      standard: true,
      secure: true
    }
  }
]);

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

const autoBackupScheduler: AutoBackupScheduler = createAutoBackupScheduler({
  readState: async () => {
    const raw = await readJson<unknown>(appSettingsPath(), {});
    const settings = normalizeAppSettings(raw);
    return {
      enabled: settings.storage.autoBackupEnabled === true,
      backupDirectory: settings.storage.backupDirectory,
      lastAutoBackupAt: settings.storage.lastAutoBackupAt
    };
  },
  resolveRoots: () => ({ appDataRoot: appDataRoot(), libraryRoot: appLibraryRoot() }),
  validateTarget: assertSafeExistingBackupTarget,
  runBackup: async ({ backupRoot, createdAt }) => {
    try {
      await ensureDir(backupRoot);
      await writeLog("info", "Auto backup started.", { backupRoot });
      const shouldCopyExternalLibrary = !isInsidePath(appDataRoot(), appLibraryRoot()) && existsSync(appLibraryRoot());
      await creationCoordinator.withWorkspaceClosed(async () => {
        await createBackupSnapshot({
          backupRoot,
          appDataDirectory: appDataRoot(),
          libraryDirectory: shouldCopyExternalLibrary ? appLibraryRoot() : undefined,
          appVersion: app.getVersion(),
          platform: process.platform,
          arch: process.arch,
          createdAt,
          logger: (level, message, meta) => writeLog(level, message, meta)
        });
      });
      await writeLog("info", "Auto backup completed.", { backupRoot });
    } catch (error) {
      await rm(backupRoot, { recursive: true, force: true });
      await writeLog("error", "Auto backup failed.", error);
      if (error instanceof BackupError) throw new AutoBackupError(error.message);
      throw new AutoBackupError("自动备份失败，请检查备份目录是否可写并重试。");
    }
  },
  onSuccess: (at) =>
    persistAutoBackupState({
      lastAutoBackupAt: at,
      lastAutoBackupFailedAt: undefined,
      lastAutoBackupError: undefined
    }),
  onFailure: async (at, errorMessage) => {
    const previous = await getAppSettings();
    const shouldNotify = !previous.storage.lastAutoBackupError;
    await writeLog("error", "Auto backup status recorded as failed.", { at, errorMessage });
    await persistAutoBackupState({ lastAutoBackupFailedAt: at, lastAutoBackupError: errorMessage });
    if (shouldNotify && Notification.isSupported()) {
      new Notification({
        title: "自动备份失败",
        body: "数据尚未完成自动备份，请打开设置检查备份目录并重试。"
      }).show();
    }
  }
});

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
  await creationCoordinator.withWorkspaceClosed(async () => {
    await copyDirectory(appDataRoot(), target);
    setActiveDataRoot(target);
    await writeStoragePointer(target);
  });
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
  const oldLibraryRoot = appLibraryRoot();
  assertSafeMigrationTarget(oldLibraryRoot, target, "书籍目录");
  if (!(await isDirectoryWritable(target))) throw new Error("选择的书籍目录不可写，请换一个位置。");
  const books = await readLibraryIndex({ includeDeleted: true });
  await copyDirectory(oldLibraryRoot, target);
  const relocatedBooks = books.map((book) => ({
    ...book,
    filePath: relocatePathInsideRoot(book.filePath, oldLibraryRoot, target) ?? book.filePath,
    coverPath: relocatePathInsideRoot(book.coverPath, oldLibraryRoot, target),
    epub: book.epub
      ? {
          ...book.epub,
          coverPath: relocatePathInsideRoot(book.epub.coverPath, oldLibraryRoot, target),
          searchIndexPath: relocatePathInsideRoot(book.epub.searchIndexPath, oldLibraryRoot, target)
        }
      : undefined
  }));
  await writeJson(path.join(target, "library.json"), { books: relocatedBooks });
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

async function checkForUpdates(): Promise<AppUpdateInfo> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);
  try {
    const response = await fetch(RELEASE_API_URL, {
      headers: { Accept: "application/vnd.github+json" },
      signal: controller.signal
    });
    if (!response.ok) throw new Error(`检查更新失败：GitHub 返回 ${response.status}`);
    const release = (await response.json()) as GitHubRelease;
    const currentVersion = app.getVersion();
    const latestVersion = release.tag_name?.replace(/^v/i, "") || currentVersion;
    const desktopAsset = release.assets?.find((asset) => {
      const name = asset.name?.toLowerCase() ?? "";
      return name.endsWith(".exe") || name.includes("setup") || name.includes("windows");
    });
    return {
      currentVersion,
      latestVersion,
      hasUpdate: isNewerVersion(latestVersion, currentVersion),
      releaseUrl: release.html_url || "https://github.com/luoshuizhiwei/creation-reading-assistant/releases",
      notes: release.body || "暂无更新说明。",
      desktopAssetName: desktopAsset?.name,
      desktopAssetUrl: desktopAsset?.browser_download_url
    };
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw new Error("检查更新超时，请检查网络后稍后重试。");
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function openUpdateDownload(url: string): Promise<void> {
  if (!/^https:\/\/github\.com\/luoshuizhiwei\/creation-reading-assistant\/releases\//i.test(url)) {
    throw new Error("只能打开本项目 GitHub Release 下载地址。");
  }
  // 升级前自动备份：启用且已配置目录时必须先成功创建备份，失败则阻止打开下载。
  await autoBackupScheduler.backupBeforeUpgrade();
  await shell.openExternal(url);
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
  const MAX_LOG_LEN = 4096;
  const message = (input.message || "Renderer log").slice(0, MAX_LOG_LEN);
  const detail = typeof input.detail === "string" ? input.detail.slice(0, MAX_LOG_LEN) : input.detail;
  await writeLog(level, message, {
    source: input.source,
    detail
  });
}

async function chooseDirectory(title: string): Promise<string | null> {
  const options: OpenDialogOptions = { title, properties: ["openDirectory", "createDirectory"] };
  const result = mainWindow ? await dialog.showOpenDialog(mainWindow, options) : await dialog.showOpenDialog(options);
  return result.canceled ? null : result.filePaths[0] ?? null;
}

/** 选择加密容器保存路径（单文件）。取消返回 null。 */
async function chooseSaveFile(title: string, defaultName: string, extensions: string[]): Promise<string | null> {
  let defaultPath: string;
  try {
    defaultPath = path.join(app.getPath("downloads"), defaultName);
  } catch {
    defaultPath = path.join(appDataRoot(), defaultName);
  }
  const options: SaveDialogOptions = {
    title,
    defaultPath,
    filters: [{ name: "加密容器", extensions }]
  };
  const result = mainWindow ? await dialog.showSaveDialog(mainWindow, options) : await dialog.showSaveDialog(options);
  return result.canceled ? null : result.filePath ?? null;
}

/** 选择加密容器文件（单文件，打开对话框）。取消返回 null。 */
async function chooseEncryptedContainer(title: string, extensions: string[]): Promise<string | null> {
  const options: OpenDialogOptions = {
    title,
    properties: ["openFile"],
    filters: [{ name: "加密容器", extensions }]
  };
  const result = mainWindow ? await dialog.showOpenDialog(mainWindow, options) : await dialog.showOpenDialog(options);
  return result.canceled ? null : result.filePaths[0] ?? null;
}

/** 生成文件名安全的本地时间戳（不含冒号等非法字符）。 */
function fileTimestamp(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

async function createBackup(): Promise<BackupResult | null> {
  const selectedDir = await chooseDirectory("选择备份保存目录");
  if (!selectedDir) return null;
  const safeSelectedDir = await assertSafeExistingBackupTarget(selectedDir, appDataRoot(), appLibraryRoot());
  const createdAt = now();
  const backupRoot = uniqueAutoBackupRoot(safeSelectedDir, new Date(createdAt));
  const shouldCopyExternalLibrary = !isInsidePath(appDataRoot(), appLibraryRoot()) && existsSync(appLibraryRoot());
  await persistAutoBackupState({ backupDirectory: safeSelectedDir });
  try {
    await ensureDir(backupRoot);
    await writeLog("info", "Backup started.", { backupRoot });
    await creationCoordinator.withWorkspaceClosed(async () => {
      await createBackupSnapshot({
        backupRoot,
        appDataDirectory: appDataRoot(),
        libraryDirectory: shouldCopyExternalLibrary ? appLibraryRoot() : undefined,
        appVersion: app.getVersion(),
        platform: process.platform,
        arch: process.arch,
        createdAt,
        logger: (level, message, meta) => writeLog(level, message, meta)
      });
    });
  } catch (error) {
    await rm(backupRoot, { recursive: true, force: true });
    throw error;
  }
  await writeLog("info", "Backup completed.", { backupRoot });
  return {
    backupRoot,
    manifestPath: path.join(backupRoot, "backup-manifest.json"),
    createdAt,
    appDataCopied: true
  };
}

async function restoreBackup(): Promise<RestoreResult | null> {
  const backupRoot = await chooseDirectory("选择备份目录");
  if (!backupRoot) return null;
  try {
    const manifest = await readBackupManifest(backupRoot);
    const currentLibraryRoot = appLibraryRoot();
    if (manifest.libraryFiles) {
      const confirmation = mainWindow
        ? await dialog.showMessageBox(mainWindow, {
            type: "warning",
            buttons: ["取消", "确认恢复"],
            defaultId: 0,
            cancelId: 0,
            title: "确认恢复外置资料库",
            message: "备份包含外置资料库文件。",
            detail: `恢复将替换当前资料库目录中的内容：\n${currentLibraryRoot}`
          })
        : await dialog.showMessageBox({
            type: "warning",
            buttons: ["取消", "确认恢复"],
            defaultId: 0,
            cancelId: 0,
            title: "确认恢复外置资料库",
            message: "备份包含外置资料库文件。",
            detail: `恢复将替换当前资料库目录中的内容：\n${currentLibraryRoot}`
          });
      if (confirmation.response !== 1) return null;
    }
    const restored = await creationCoordinator.withWorkspaceClosed(() =>
      restoreBackupFromDirectory({
        backupRoot,
        currentAppDataRoot: appDataRoot(),
        resolveLibraryTarget: async () => currentLibraryRoot,
        logger: (level, message, meta) => writeLog(level, message, meta)
      })
    );
    await ensureDir(logsRoot());
    await writeLog("warn", "Restore completed.", { backupRoot, checkpointPath: restored.checkpointPath });
    const restoredSettings = normalizeAppSettings(await readJson<unknown>(appSettingsPath(), defaultAppSettings()), await readLegacyReaderSettings());
    restoredSettings.storage = {
      ...restoredSettings.storage,
      dataDirectory: appDataRoot(),
      libraryDirectory: manifest.libraryFiles ? currentLibraryRoot : path.join(appDataRoot(), "AppLibrary"),
      storageMode: storageModeForDataRoot(appDataRoot())
    };
    await writeJson<AppSettings>(appSettingsPath(), restoredSettings);
    await setActiveStorageFromSettings(restoredSettings);
    writeRuntimeStateSync(true);
    return {
      backupRoot,
      restoredAt: restored.restoredAt,
      checkpointPath: restored.checkpointPath,
      restartRecommended: true
    };
  } catch (error) {
    if (error instanceof BackupError) {
      await writeLog("error", "Restore aborted.", { backupRoot, code: error.code, message: error.message, detail: error.detail });
      throw new Error(error.message);
    }
    await writeLog("error", "Restore aborted.", { backupRoot, detail: error instanceof Error ? error.message : String(error) });
    throw new Error("恢复失败。");
  }
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



function contentSecurityPolicy(): string {
  const scriptPolicy = app.isPackaged
    ? "script-src 'self'"
    : "script-src 'self' 'unsafe-inline'";
  // 生产环境渲染进程仅经 IPC 与主进程通信，从不直连 localhost；localhost 通配仅 dev 模式（Vite/HMR）需要。
  // 收窄生产 CSP 可降低渲染进程被 XSS 后向内网/本机服务发起横向请求的面。
  const connectPolicy = app.isPackaged
    ? "connect-src 'self' novel-workbench-epub:"
    : "connect-src 'self' novel-workbench-epub: http://localhost:* ws://localhost:*";
  return [
    "default-src 'self'",
    scriptPolicy,
    "style-src 'self' 'unsafe-inline'",
    // 注意：`img-src` 必须与 index.html 的 meta CSP 保持一致。两处 CSP 同时生效
    // （CSP 取交集），只改一处会让 creation-asset 封面被静默拦截。
    "img-src 'self' data: blob: novel-workbench-epub: creation-asset:",
    "font-src 'self' data: file:",
    connectPolicy,
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

function installPermissionHandler(): void {
  session.defaultSession.setPermissionRequestHandler((_webContents, permission, callback) => {
    // 仅允许桌面通知；地理/媒体/剪贴板等权限默认拒绝，降低渲染进程被 XSS 滥用的攻击面。
    callback(permission === "notifications");
  });
}

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1360,
    height: 860,
    ...(captureProfileDir ? {} : { minWidth: 1080, minHeight: 720 }),
    title: "创作阅读助手",
    backgroundColor: "#f1f0eb",
    // 打包后任务栏/资源管理器图标来自 exe（electron-builder 用 build/icon.ico）；
    // 开发模式 exe 是 electron.exe，需要显式给窗口挂上品牌图标。
    icon: app.isPackaged ? undefined : path.join(app.getAppPath(), "build", "icon.ico"),
    // 截图巡检模式（CREATION_READER_CAPTURE_PROFILE）：窗口放到屏幕外 + 不进任务栏，
    // showInactive 不抢焦点；正常绘制保证 CDP 截图可用，且不打扰用户正在做的事。
    ...(captureProfileDir ? { skipTaskbar: true } : {}),
    frame: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, "../preload/index.js"),
      contextIsolation: true,
      nodeIntegration: false,
      // Electron 20+ 的 sandbox:true 会启用 OS 级渲染沙箱。在装有 HIPS 类主动防御软件
      // 的 Windows 上，该沙箱进程会被外部终止，表现为启动即白屏（应用日志
      // "Renderer process gone" reason:killed exitCode:1）。禁用 OS 级沙箱后仍保留
      // contextIsolation + nodeIntegration:false + preload 白名单 IPC 的安全模型。
      sandbox: false,
      devTools: !app.isPackaged
    }
  });
  mainWindow.setMenuBarVisibility(false);
  // 渲染进程禁止自行打开新窗口：外链统一走 shell.openExternal 白名单（src 全仓无 window.open 用法）。
  // 缺失此处理器时 Electron 默认会弹一个无限制的 BrowserWindow，构成安全缺口。
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void writeLog("warn", "Blocked renderer window.open; external links must use shell.openExternal whitelist.", { url });
    return { action: "deny" };
  });
  if (captureProfileDir) {
    mainWindow.webContents.setBackgroundThrottling(false);
    mainWindow.setPosition(-20000, -20000);
    mainWindow.showInactive();
  }
  const broadcastMaximized = () => {
    mainWindow?.webContents.send("window:maximized-changed", mainWindow.isMaximized());
  };
  mainWindow.on("maximize", broadcastMaximized);
  mainWindow.on("unmaximize", broadcastMaximized);
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

async function resolveBundleCardConflicts(
  preview: ProjectBundleImportPreview
): Promise<ProjectBundleCardResolution[] | null> {
  const cardResolutions: ProjectBundleCardResolution[] = preview.identicalCardIds.map((cardId) => ({
    cardId,
    action: "reuse"
  }));
  for (const conflict of preview.conflicts) {
    const buttons = conflict.localDeleted
      ? ["取消导入", "导入副本"]
      : ["取消导入", "保留本机", "导入副本"];
    const detail = [
      `稳定 ID：${conflict.cardId}`,
      `本机：${conflict.localTitle}${conflict.localDeleted ? "（在回收站）" : ""}`,
      `项目包：${conflict.importedTitle}`,
      `差异：${conflict.differingFields.join("、") || "未知"}`,
      "保留本机会让新项目关联现有卡片；导入副本会创建新稳定 ID 并重映射场景、关系、批注和附件。"
    ].join("\n");
    const options = {
      type: "warning" as const,
      buttons,
      defaultId: 0,
      cancelId: 0,
      title: "全局卡片冲突",
      message: `卡片“${conflict.importedTitle}”与本机内容不同。`,
      detail
    };
    const choice = mainWindow
      ? await dialog.showMessageBox(mainWindow, options)
      : await dialog.showMessageBox(options);
    if (choice.response === 0) return null;
    if (!conflict.localDeleted && choice.response === 1) {
      cardResolutions.push({ cardId: conflict.cardId, action: "keep-local" });
    } else {
      cardResolutions.push({
        cardId: conflict.cardId,
        action: "import-copy",
        targetCardId: `card-${crypto.randomUUID()}`
      });
    }
  }
  return cardResolutions;
}

function registerIpc(): void {
  ipcMain.handle("window:minimize", () => {
    mainWindow?.minimize();
  });
  ipcMain.handle("window:toggleMaximize", () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.handle("window:close", () => {
    mainWindow?.close();
  });
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
  ipcMain.handle("updates:check", async () => checkForUpdates());
  ipcMain.handle("updates:openDownload", async (_event, url: string) => openUpdateDownload(url));
  ipcMain.handle("library:importBook", async () => importBook());
  ipcMain.handle("library:importEpub", async () => importEpub());
  ipcMain.handle("library:listBooks", async () => readLibraryIndex());
  ipcMain.handle("library:removeBook", async (_event, bookId: string) => removeBook(bookId));
  ipcMain.handle("reader:openBook", async (_event, bookId: string) => openBook(bookId));
  // TXT 目录手动修正：overrides=null 表示清除修正、恢复自动识别。
  ipcMain.handle("reader:saveTxtTocOverrides", async (_event, input: { bookId: string; overrides: TxtTocOverrides | null }) => {
    const bookId = optionalString(input?.bookId);
    if (!bookId) throw new Error("缺少书籍 ID。");
    const books = await readLibraryIndex({ includeDeleted: true });
    const book = books.find((item) => item.id === bookId);
    if (!book) throw new Error("书籍不存在或已删除。");
    if (book.format !== "txt") throw new Error("只有 TXT 支持手动修正章节目录。");
    const overrides = input.overrides === null ? undefined : normalizeTxtTocOverrides(input.overrides);
    const next: LibraryBook = {
      ...book,
      text: overrides ? { ...book.text, tocOverrides: overrides } : undefined,
      updatedAt: now()
    };
    await writeLibraryIndex(books.map((item) => (item.id === bookId ? next : item)));
    return next;
  });
  ipcMain.handle("reader:openEpub", async (_event, bookId: string) => openEpub(bookId));
  ipcMain.handle("reader:saveProgress", async (_event, input: SaveProgressInput) => saveProgress(input));
  ipcMain.handle("reader:getProgress", async (_event, bookId: string) => getProgress(bookId));
  ipcMain.handle("reader:getBatchProgress", async (_event, bookIds: string[]) => getBatchProgress(bookIds));
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
  ipcMain.handle("reader:savePreset", async (_e, preset: any) => {
    const current = (await getAppSettings()).reader;
    const presets = Array.isArray(current.presets) ? [...current.presets] : [];
    const idx = presets.findIndex((p: any) => p.id === preset.id);
    if (idx >= 0) presets[idx] = preset;
    else presets.push(preset);
    await updateAppSettings({ reader: { ...current, presets } });
    return preset;
  });
  ipcMain.handle("reader:deletePreset", async (_e, presetId: string) => {
    const current = (await getAppSettings()).reader;
    if (Array.isArray(current.presets)) {
      const presets = current.presets.filter((p: any) => p.id !== presetId);
      await updateAppSettings({ reader: { ...current, presets } });
    }
  });
  ipcMain.handle("reader:chooseFont", async () => {
    const result = await dialog.showOpenDialog({
      properties: ["openFile"],
      filters: [{ name: "字体文件", extensions: ["ttf", "otf", "woff", "woff2"] }],
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    const srcPath = result.filePaths[0];
    const fontFileName = path.basename(srcPath);
    const fontsDir = path.join(appDataRoot(), "fonts");
    await mkdir(fontsDir, { recursive: true });
    const destPath = path.join(fontsDir, fontFileName);
    await copyFile(srcPath, destPath);
    return { fileName: fontFileName, filePath: destPath };
  });
  ipcMain.handle("reader:getInstalledFonts", async () => {
    const fontsDir = path.join(appDataRoot(), "fonts");
    try {
      const files = await readdir(fontsDir);
      return files.filter((f: string) => /\.(ttf|otf|woff|woff2)$/i.test(f));
    } catch { return []; }
  });
  ipcMain.handle("reader:deleteFont", async (_e, fileName: string) => {
    if (typeof fileName !== "string" || fileName.length === 0) return;
    const fontsDir = path.resolve(appDataRoot(), "fonts");
    const targetPath = path.resolve(fontsDir, path.basename(fileName));
    if (!isInsidePath(fontsDir, targetPath)) {
      await writeLog("warn", "Font deletion skipped: target escapes fonts directory.", { fileName, targetPath });
      return;
    }
    try { await unlink(targetPath); } catch {}
  });
  ipcMain.handle("settings:get", async () => getAppSettings());
  ipcMain.handle("settings:update", async (_event, patch: AppSettingsPatch) => updateAppSettings(patch));
  ipcMain.handle("settings:chooseBackupDirectory", async () => chooseDirectory("选择自动备份目录"));
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
  ipcMain.handle("highlights:getByBook", async (_event, bookId: string) => {
    const all = await readHighlights();
    return all.filter(h => h.bookId === bookId);
  });
  ipcMain.handle("highlights:save", async (_event, item: HighlightItem) => {
    const all = await readHighlights();
    const idx = all.findIndex(h => h.id === item.id);
    if (idx >= 0) all[idx] = item; else all.push(item);
    await writeHighlights(all);
    return item;
  });
  ipcMain.handle("highlights:delete", async (_event, id: string) => {
    const all = await readHighlights();
    await writeHighlights(all.filter(h => h.id !== id));
  });
  ipcMain.handle("bookmarks:getByBook", async (_event, bookId: string) => {
    const all = await readBookmarks();
    return all.filter(b => b.bookId === bookId);
  });
  ipcMain.handle("bookmarks:save", async (_event, item: BookmarkItem) => {
    const all = await readBookmarks();
    const idx = all.findIndex(b => b.id === item.id);
    if (idx >= 0) all[idx] = item; else all.push(item);
    await writeBookmarks(all);
    return item;
  });
  ipcMain.handle("bookmarks:delete", async (_event, id: string) => {
    const all = await readBookmarks();
    await writeBookmarks(all.filter(b => b.id !== id));
  });
  ipcMain.handle("ai:getSettings", async () => getAISettings());
  ipcMain.handle("ai:updateSettings", async (_event, patch: AISettingsPatch) => updateAISettings(patch));
  ipcMain.handle("ai:saveApiKey", async (_event, input: SaveAIApiKeyInput) => saveAIApiKey(input));
  ipcMain.handle("ai:clearApiKey", async () => clearAIApiKey());
  ipcMain.handle("ai:test", async () => testAIConnection());
  ipcMain.handle("ai:run", async (_event, input: AIRunInput) => runAIAction(input));
  ipcMain.handle("search:global", async (_event, query: SearchQuery) => searchGlobal(query));
  ipcMain.handle("sync:getStatus", async () => getSyncStatusWithRebind());
  ipcMain.handle("sync:startServer", async () => startSyncServer());
  ipcMain.handle("sync:stopServer", async () => stopSyncServer());
  ipcMain.handle("sync:createPairingToken", async () => createPairingToken());
  ipcMain.handle("sync:listDevices", async () => listPairedDevices());
  ipcMain.handle("sync:removeDevice", async (_event, deviceId: string) => removePairedDevice(deviceId));
  ipcMain.handle("backup:create", async () => createBackup());
  ipcMain.handle("backup:restore", async () => restoreBackup());
  ipcMain.handle("backup:runAuto", async () => {
    const result = await autoBackupScheduler.runNow();
    return {
      backupRoot: result.backupRoot,
      manifestPath: path.join(result.backupRoot, "backup-manifest.json"),
      createdAt: result.createdAt
    };
  });
  ipcMain.handle("diagnostics:exportDebugInfo", async () => exportDebugInfo());
  registerCreationIpc(creationCoordinator, {
    resolveDataRoot: () => appDataRoot(),
    resolveLibraryRoot: () => appLibraryRoot()
  });
  registerCreationAssetProtocol(creationCoordinator, {
    resolveDataRoot: () => appDataRoot()
  });

  // 长任务（备份 / 项目包 / 资源扫描）的编排：把目录选择、校验、外置资料库确认、
  // 恢复后设置回写等主进程副作用收口到 index.ts，operation IPC 只负责调度 runner。
  registerOperationIpc(operationCoordinator, {
    resolveDataRoot: () => appDataRoot(),
    resolveCreationWorkspaceRoot: () => path.join(appDataRoot(), "CreationWorkspace"),
    resolveLibraryRoot: () => appLibraryRoot(),
    appVersion: app.getVersion(),
    platform: process.platform,
    arch: process.arch,
    now: () => Date.now(),
    prepareBackupCreate: async () => {
      const selectedDir = await chooseDirectory("选择备份保存目录");
      if (!selectedDir) return null;
      await assertSafeExistingBackupTarget(selectedDir, appDataRoot(), appLibraryRoot());
      const backupRoot = uniqueAutoBackupRoot(selectedDir, new Date());
      await persistAutoBackupState({ backupDirectory: selectedDir });
      const shouldCopyExternalLibrary = !isInsidePath(appDataRoot(), appLibraryRoot()) && existsSync(appLibraryRoot());
      return {
        backupRoot,
        libraryDirectory: shouldCopyExternalLibrary ? appLibraryRoot() : undefined,
        createdAt: new Date().toISOString()
      };
    },
    prepareBackupRestore: async () => {
      const backupRoot = await chooseDirectory("选择备份目录");
      if (!backupRoot) return null;
      const manifest = await readBackupManifest(backupRoot);
      const currentLibraryRoot = appLibraryRoot();
      if (manifest.libraryFiles) {
        const detail = `恢复将替换当前资料库目录中的内容：\n${currentLibraryRoot}`;
        const confirmation = mainWindow
          ? await dialog.showMessageBox(mainWindow, {
              type: "warning",
              buttons: ["取消", "确认恢复"],
              defaultId: 0,
              cancelId: 0,
              title: "确认恢复外置资料库",
              message: "备份包含外置资料库文件。",
              detail
            })
          : await dialog.showMessageBox({
              type: "warning",
              buttons: ["取消", "确认恢复"],
              defaultId: 0,
              cancelId: 0,
              title: "确认恢复外置资料库",
              message: "备份包含外置资料库文件。",
              detail
            });
        if (confirmation.response !== 1) return null;
      }
      return { backupRoot, manifest };
    },
    finalizeBackupRestore: async (manifest, backupRoot) => {
      const restoredSettings = normalizeAppSettings(
        await readJson<unknown>(appSettingsPath(), defaultAppSettings()),
        await readLegacyReaderSettings()
      );
      restoredSettings.storage = {
        ...restoredSettings.storage,
        dataDirectory: appDataRoot(),
        libraryDirectory: manifest.libraryFiles ? appLibraryRoot() : path.join(appDataRoot(), "AppLibrary"),
        storageMode: storageModeForDataRoot(appDataRoot())
      };
      await writeJson<AppSettings>(appSettingsPath(), restoredSettings);
      await setActiveStorageFromSettings(restoredSettings);
      writeRuntimeStateSync(true);
      await writeLog("warn", "Restore completed via operation.", { backupRoot });
    },
    prepareBundleImport: async () => {
      const bundleDirectory = await chooseDirectory("选择项目包导入目录");
      if (!bundleDirectory) return null;
      let data: ProjectBundleData;
      try {
        data = JSON.parse(await readFile(path.join(bundleDirectory, "project.json"), "utf8")) as ProjectBundleData;
      } catch {
        throw new Error("项目包 project.json 无法解析。");
      }
      const workspaceDirectory = path.join(appDataRoot(), "CreationWorkspace");
      const workspace = await openCreationWorkspace({ directory: workspaceDirectory });
      let preview;
      try {
        preview = await workspace.previewProjectBundleImport(data);
      } finally {
        await workspace.close();
      }
      const cardResolutions = await resolveBundleCardConflicts(preview);
      if (cardResolutions === null) return null;
      return { workspaceDirectory, bundleDirectory, cardResolutions };
    },
    prepareBackupExportEncrypted: async () => {
      const targetFile = await chooseSaveFile(
        "保存加密备份",
        `creation-backup-${fileTimestamp(new Date())}.crbackup`,
        ["crbackup"]
      );
      if (!targetFile) return null;
      const shouldCopyExternalLibrary = !isInsidePath(appDataRoot(), appLibraryRoot()) && existsSync(appLibraryRoot());
      return {
        targetFile,
        libraryDirectory: shouldCopyExternalLibrary ? appLibraryRoot() : undefined,
        createdAt: new Date().toISOString()
      };
    },
    prepareBackupImportEncrypted: async () => {
      const containerFile = await chooseEncryptedContainer("选择加密备份容器", ["crbackup"]);
      return containerFile ? { containerFile } : null;
    },
    prepareBundleExportEncrypted: async (projectId: string) => {
      const targetFile = await chooseSaveFile(
        "保存加密项目包",
        `creation-bundle-${fileTimestamp(new Date())}.crbundle`,
        ["crbundle"]
      );
      if (!targetFile) return null;
      // 读取项目包导出数据需要打开工作区；读取完成后关闭，避免与后续文件复制冲突。
      const workspaceDirectory = path.join(appDataRoot(), "CreationWorkspace");
      const workspace = await openCreationWorkspace({ directory: workspaceDirectory });
      try {
        const data = await workspace.read({ kind: "project.bundle.export", projectId });
        if (!data) return null;
        return { workspaceDirectory, data, targetFile };
      } finally {
        await workspace.close();
      }
    },
    prepareBundleImportEncrypted: async () => {
      const containerFile = await chooseEncryptedContainer("选择加密项目包容器", ["crbundle"]);
      return containerFile ? { containerFile } : null;
    },
    resolveBundleCardConflicts,
    finalizeEncryptedBackupRestore: async () => {
      const restoredSettings = normalizeAppSettings(
        await readJson<unknown>(appSettingsPath(), defaultAppSettings()),
        await readLegacyReaderSettings()
      );
      restoredSettings.storage = {
        ...restoredSettings.storage,
        dataDirectory: appDataRoot(),
        libraryDirectory: appLibraryRoot(),
        storageMode: storageModeForDataRoot(appDataRoot())
      };
      await writeJson<AppSettings>(appSettingsPath(), restoredSettings);
      await setActiveStorageFromSettings(restoredSettings);
      writeRuntimeStateSync(true);
      await writeLog("warn", "Encrypted restore completed via operation.", {});
    },
    prepareBundleExport: async (projectId: string) => {
      const targetDirectory = await chooseDirectory("选择项目包导出目录");
      if (!targetDirectory) return null;
      // 读取项目包导出数据需要打开工作区；读取完成后关闭，避免与后续文件复制冲突。
      const workspaceDirectory = path.join(appDataRoot(), "CreationWorkspace");
      const workspace = await openCreationWorkspace({ directory: workspaceDirectory });
      try {
        const data = await workspace.read({ kind: "project.bundle.export", projectId });
        if (!data) return null;
        return { workspaceDirectory, data, targetDirectory };
      } finally {
        await workspace.close();
      }
    },
    withWorkspaceClosed: (fn) => creationCoordinator.withWorkspaceClosed(fn)
  });
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
  installPermissionHandler();
  registerEpubProtocol();
  await resolveInitialDataRoot();
  await ensureAppStorage();
  await getAppSettings();
  await prepareStartupRecovery();
  registerIpc();
  autoBackupScheduler.start();
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

let creationWorkspaceReadyToQuit = false;
let creationWorkspaceQuitPending = false;

app.on("before-quit", (event) => {
  writeRuntimeStateSync(true);
  autoBackupScheduler.dispose();
  disposeAllOperations(operationCoordinator);
  if (creationWorkspaceReadyToQuit) return;
  event.preventDefault();
  if (creationWorkspaceQuitPending) return;
  creationWorkspaceQuitPending = true;
  void creationCoordinator
    .close()
    .catch((error) => writeLog("error", "Failed to close creation workspace before quit.", error))
    .finally(() => {
      creationWorkspaceReadyToQuit = true;
      app.quit();
    });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
