/**
 * 设置中心与 AI 配置域（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：AppSettings 默认值与规范化、设置读写与分节重置、存储位置查询、
 * AI 参数与安全存储的 API Key 读写、AI 请求执行。
 * 行为逐字保持；仅把顶层声明导出供主进程入口与 IPC 层使用。
 * 目录迁移与自动备份调度器留在入口，因为它们需要协调创作工作区的关闭。
 */
import { app, safeStorage } from "electron";
import { existsSync } from "node:fs";
import { readFile, rm } from "node:fs/promises";
import type { LibraryBook, ReaderSettings } from "../../src/types/library";
import type { InspirationItem } from "../../src/types/inspiration";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations, StorageSettings } from "../../src/types/settings";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, SaveAIApiKeyInput } from "../../src/types/ai";
import { buildAIPrompt as buildAIPromptFromModule } from "./ai-prompt";
import { assertSafeExistingBackupTarget } from "./backup/auto-backup";
import {
  aiSecretsPath,
  appDataRoot,
  appLibraryCoversRoot,
  appLibraryFilesRoot,
  appLibraryRoot,
  appLibrarySearchIndexRoot,
  appSettingsPath,
  bookmarksPath,
  ensureDir,
  fallbackDataRoot,
  getOrCreateDeviceId,
  highlightsPath,
  inspirationsPath,
  isDirectoryWritable,
  isRecord,
  libraryPath,
  logsRoot,
  now,
  portableDataRoot,
  readJson,
  readerSettingsPath,
  readingProgressPath,
  readingSessionsPath,
  setActiveStorageFromSettings,
  storageModeForDataRoot,
  withFileLock,
  writeAtomic,
  writeJson,
  writeLog
} from "./storage";

export function defaultReaderSettings(): ReaderSettings {
  return {
    fontSize: 18,
    lineHeight: 1.8,
    paragraphSpacing: 1.0,
    letterSpacing: 0,
    pageMargin: 56,
    appTheme: "system",
    readerBackground: "warm",
    epubStyleMode: "publisher",
    textConversion: "none",
    restoreLastPosition: true,
    readingMode: "scroll",
    presets: [
      {
        id: "preset-comfortable",
        name: "舒适阅读",
        fontSize: 18, lineHeight: 1.8, pageMargin: 24,
        paragraphSpacing: 1.0, letterSpacing: 0,
        readerBackground: "warm" as const,
      },
      {
        id: "preset-compact",
        name: "紧凑模式",
        fontSize: 15, lineHeight: 1.4, pageMargin: 16,
        paragraphSpacing: 0.6, letterSpacing: 0,
        readerBackground: "white" as const,
      },
      {
        id: "preset-large",
        name: "大字体",
        fontSize: 24, lineHeight: 2.0, pageMargin: 32,
        paragraphSpacing: 1.5, letterSpacing: 0.02,
        readerBackground: "parchment" as const,
      },
    ],
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

export function defaultAISettings(): AISettings {
  return {
    provider: "openai-compatible",
    baseUrl: "https://api.openai.com/v1",
    model: "gpt-4.1-mini",
    temperature: 0.7,
    hasApiKey: false,
    enabled: false
  };
}

export function defaultAppSettings(): AppSettings {
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

export async function ensureAppStorage(): Promise<void> {
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
  if (!existsSync(highlightsPath())) await writeJson(highlightsPath(), { version: 1, updatedAt: now(), items: [] });
  if (!existsSync(bookmarksPath())) await writeJson(bookmarksPath(), { version: 1, updatedAt: now(), items: [] });
  if (!existsSync(readerSettingsPath())) await writeJson<ReaderSettings>(readerSettingsPath(), defaultReaderSettings());
  if (!existsSync(appSettingsPath())) await writeJson<AppSettings>(appSettingsPath(), normalizeAppSettings(defaultAppSettings(), await readLegacyReaderSettings()));
}

export async function readLegacyReaderSettings(): Promise<Partial<ReaderSettings> | undefined> {
  if (!existsSync(readerSettingsPath())) return undefined;
  return readJson<Partial<ReaderSettings>>(readerSettingsPath(), {});
}

export function normalizeReaderSettings(value: unknown): ReaderSettings {
  const defaults = defaultReaderSettings();
  const raw = isRecord(value) ? value : {};
  const tracking = isRecord(raw.tracking) ? raw.tracking : {};
  const legacyTheme = raw.theme === "dark" || raw.theme === "light" ? raw.theme : undefined;
  const readerBackground =
    raw.readerBackground === "white" || raw.readerBackground === "warm" || raw.readerBackground === "green" || raw.readerBackground === "night" ||
    raw.readerBackground === "amber" || raw.readerBackground === "parchment" || raw.readerBackground === "beans"
      ? raw.readerBackground
      : legacyTheme === "dark"
        ? "night"
        : defaults.readerBackground;
  return {
    fontSize: typeof raw.fontSize === "number" ? raw.fontSize : defaults.fontSize,
    lineHeight: typeof raw.lineHeight === "number" ? raw.lineHeight : defaults.lineHeight,
    paragraphSpacing: typeof raw.paragraphSpacing === "number" && !isNaN(raw.paragraphSpacing)
      ? Math.max(0.5, Math.min(3.0, raw.paragraphSpacing))
      : defaults.paragraphSpacing,
    letterSpacing: typeof raw.letterSpacing === "number" && !isNaN(raw.letterSpacing)
      ? Math.max(0, Math.min(0.5, raw.letterSpacing))
      : defaults.letterSpacing,
    pageMargin: typeof raw.pageMargin === "number" ? raw.pageMargin : defaults.pageMargin,
    appTheme: raw.appTheme === "light" || raw.appTheme === "dark" || raw.appTheme === "system" ? raw.appTheme : defaults.appTheme,
    readerBackground,
    epubStyleMode: raw.epubStyleMode === "publisher" || raw.epubStyleMode === "unified" ? raw.epubStyleMode : defaults.epubStyleMode,
    textConversion: raw.textConversion === "none" || raw.textConversion === "s2t" || raw.textConversion === "t2s" ? raw.textConversion : defaults.textConversion,
    theme: legacyTheme,
    restoreLastPosition: typeof raw.restoreLastPosition === "boolean" ? raw.restoreLastPosition : defaults.restoreLastPosition,
    readingMode: "scroll",
    presets: Array.isArray(raw.presets) ? raw.presets : defaults.presets,
    fontFamily: typeof raw.fontFamily === "string" ? raw.fontFamily : undefined,
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

export function normalizeAISettings(value: unknown, hasApiKey = false): AISettings {
  const defaults = defaultAISettings();
  const raw = isRecord(value) ? value : {};
  return {
    provider: raw.provider === "openai-compatible" ? raw.provider : defaults.provider,
    baseUrl: typeof raw.baseUrl === "string" && raw.baseUrl.trim() ? raw.baseUrl.trim().replace(/\/+$/, "") : defaults.baseUrl,
    model: typeof raw.model === "string" && raw.model.trim() ? raw.model.trim() : defaults.model,
    temperature: typeof raw.temperature === "number" ? Math.min(1.5, Math.max(0, raw.temperature)) : defaults.temperature,
    hasApiKey,
    enabled: raw.enabled === true
  };
}

export function normalizeAppSettings(value: unknown, legacyReader?: Partial<ReaderSettings>): AppSettings {
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
      lastMigratedAt: typeof storage.lastMigratedAt === "string" ? storage.lastMigratedAt : undefined,
      backupDirectory:
        typeof storage.backupDirectory === "string" && storage.backupDirectory.trim() ? storage.backupDirectory : undefined,
      autoBackupEnabled: storage.autoBackupEnabled === true,
      lastAutoBackupAt: typeof storage.lastAutoBackupAt === "string" ? storage.lastAutoBackupAt : undefined,
      lastAutoBackupFailedAt: typeof storage.lastAutoBackupFailedAt === "string" ? storage.lastAutoBackupFailedAt : undefined,
      lastAutoBackupError: typeof storage.lastAutoBackupError === "string" ? storage.lastAutoBackupError : undefined
    },
    debug: {
      appVersion: app.getVersion(),
      dataRoot: appDataRoot(),
      showStatsCards: typeof debug.showStatsCards === "boolean" ? debug.showStatsCards : defaults.debug.showStatsCards
    },
    updatedAt: typeof raw.updatedAt === "string" ? raw.updatedAt : now()
  };
}

export function mergeSettings(current: AppSettings, patch: AppSettingsPatch): AppSettings {
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

export async function readAISecretStore(): Promise<unknown> {
  if (!existsSync(aiSecretsPath())) return {};
  try {
    const content = await readFile(aiSecretsPath(), "utf-8");
    return content.trim() ? JSON.parse(content) : {};
  } catch (error) {
    await writeLog("warn", "AI secret store read failed.", { error: error instanceof Error ? error.message : String(error) });
    return {};
  }
}

export async function hasAIApiKey(): Promise<boolean> {
  const raw = await readAISecretStore();
  return isRecord(raw) && typeof raw.apiKeyEncrypted === "string" && raw.apiKeyEncrypted.length > 0;
}

export async function readAIApiKey(): Promise<string | undefined> {
  const raw = await readAISecretStore();
  if (!isRecord(raw) || typeof raw.apiKeyEncrypted !== "string" || !raw.apiKeyEncrypted) return undefined;
  try {
    return safeStorage.decryptString(Buffer.from(raw.apiKeyEncrypted, "base64"));
  } catch (error) {
    await writeLog("warn", "AI API key decrypt failed.", { error: error instanceof Error ? error.message : String(error) });
    return undefined;
  }
}

export async function saveAIApiKey(input: SaveAIApiKeyInput): Promise<AISettings> {
  const apiKey = typeof input.apiKey === "string" ? input.apiKey.trim() : "";
  if (!apiKey) throw new Error("API Key 不能为空。");
  if (!safeStorage.isEncryptionAvailable()) throw new Error("当前系统不可用安全加密存储，无法保存 API Key。");
  const encrypted = safeStorage.encryptString(apiKey).toString("base64");
  await writeAtomic(aiSecretsPath(), `${JSON.stringify({ version: 1, apiKeyEncrypted: encrypted, updatedAt: now() }, null, 2)}\n`);
  return getAISettings();
}

export async function clearAIApiKey(): Promise<AISettings> {
  await rm(aiSecretsPath(), { force: true });
  await rm(`${aiSecretsPath()}.bak`, { force: true });
  return getAISettings();
}

export async function getAppSettings(): Promise<AppSettings> {
  const raw = await readJson<unknown>(appSettingsPath(), defaultAppSettings());
  const settings = normalizeAppSettings(raw, await readLegacyReaderSettings());
  settings.ai = normalizeAISettings(settings.ai, await hasAIApiKey());
  if (!existsSync(appSettingsPath())) await writeJson<AppSettings>(appSettingsPath(), settings);
  await setActiveStorageFromSettings(settings);
  return settings;
}

export async function updateAppSettings(patch: AppSettingsPatch): Promise<AppSettings> {
  if (isRecord(patch.storage) && typeof patch.storage.backupDirectory === "string") {
    await assertSafeExistingBackupTarget(patch.storage.backupDirectory, appDataRoot(), appLibraryRoot());
  }
  return withFileLock(appSettingsPath(), async () => {
    const next = mergeSettings(await getAppSettings(), isRecord(patch) ? patch : {});
    if (next.storage.autoBackupEnabled && !next.storage.backupDirectory) {
      throw new Error("请先选择自动备份目录，再启用自动备份。");
    }
    next.ai = normalizeAISettings(next.ai, await hasAIApiKey());
    await setActiveStorageFromSettings(next);
    await writeJson<AppSettings>(appSettingsPath(), next);
    await writeJson<ReaderSettings>(readerSettingsPath(), next.reader);
    return next;
  });
}

export async function persistAutoBackupState(patch: Partial<StorageSettings>): Promise<void> {
  await withFileLock(appSettingsPath(), async () => {
    const current = await getAppSettings();
    const next = mergeSettings(current, { storage: patch });
    await writeJson<AppSettings>(appSettingsPath(), next);
  });
}

export async function resetSettingsSection(section: SettingsSection): Promise<AppSettings> {
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

export async function resetReaderSettingsOnly(): Promise<AppSettings> {
  return updateAppSettings({ reader: defaultReaderSettings() });
}

export async function getStorageLocations(): Promise<StorageLocations> {
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

export async function getAISettings(): Promise<AISettings> {
  return (await getAppSettings()).ai;
}

export async function updateAISettings(patch: AISettingsPatch): Promise<AISettings> {
  if (Object.keys(patch).length === 0) return getAISettings();
  const settings = await updateAppSettings({ ai: patch });
  return settings.ai;
}

export function buildAIPrompt(input: AIRunInput): string {
  // 委托给独立模块，便于纯函数测试；按 action 分派灵感 / 场景两套提示词。
  return buildAIPromptFromModule(input);
}

export async function runAIAction(input: AIRunInput): Promise<AIRunResult> {
  const settings = await getAISettings();
  // 隐私边界：未显式启用时绝不发起任何网络请求，也不读取 Key。
  if (!settings.enabled) throw new Error("AI 助手未启用。请在设置中心开启「启用 AI 助手」后再使用 AI 打磨。");
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

export async function testAIConnection(): Promise<{ ok: boolean; message: string }> {
  try {
    const result = await runAIAction({ action: "polish", content: "测试连接：一个角色在雨夜想起旧约定。" });
    return { ok: true, message: result.content ? "AI 连接可用。" : "AI 返回为空。" };
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : String(error) };
  }
}
