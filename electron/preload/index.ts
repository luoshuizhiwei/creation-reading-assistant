import { contextBridge, ipcRenderer } from "electron";
import type { DesktopApi } from "../../src/types/api";
import type {
  EndReadingSessionInput,
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
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations } from "../../src/types/settings";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, SaveAIApiKeyInput } from "../../src/types/ai";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  InspirationItem,
  UpdateInspirationInput
} from "../../src/types/inspiration";
import type { SearchQuery, SearchResult } from "../../src/types/search";
import type { BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "../../src/types/maintenance";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "../../src/types/sync";

const invoke = <T>(channel: string, ...args: unknown[]): Promise<T> => ipcRenderer.invoke(channel, ...args);

const api: DesktopApi = {
  app: {
    getBuildInfo: () => invoke<BuildInfo>("app:getBuildInfo"),
    getStartupRecovery: () => invoke<StartupRecoveryInfo>("app:getStartupRecovery"),
    markStartupRecoverySeen: () => invoke<void>("app:markStartupRecoverySeen"),
    openDataDirectory: () => invoke<void>("app:openDataDirectory"),
    openLogDirectory: () => invoke<void>("app:openLogDirectory"),
    writeRendererLog: (input: RendererLogInput) => invoke<void>("app:writeRendererLog", input)
  },
  library: {
    importBook: () => invoke<LibraryBook[]>("library:importBook"),
    importEpub: () => invoke<LibraryBook[]>("library:importEpub"),
    listBooks: () => invoke<LibraryBook[]>("library:listBooks"),
    removeBook: (bookId: string) => invoke<LibraryBook[]>("library:removeBook", bookId)
  },
  reader: {
    openBook: (bookId: string) => invoke<ReaderBookPayload>("reader:openBook", bookId),
    openEpub: (bookId: string) => invoke<ReaderEpubPayload>("reader:openEpub", bookId),
    saveProgress: (input: SaveProgressInput) => invoke<ReadingProgress>("reader:saveProgress", input),
    getProgress: (bookId: string) => invoke<ReadingProgress | undefined>("reader:getProgress", bookId),
    saveEpubLocation: (input: SaveProgressInput) => invoke<ReadingProgress>("reader:saveEpubLocation", input),
    getEpubLocation: (bookId: string) => invoke<ReadingLocation | undefined>("reader:getEpubLocation", bookId),
    startSession: (input: StartReadingSessionInput) => invoke<ReadingSession>("reader:startSession", input),
    updateSession: (input: UpdateReadingSessionInput) => invoke<ReadingSession>("reader:updateSession", input),
    endSession: (input: EndReadingSessionInput) => invoke<ReadingSession>("reader:endSession", input),
    recoverActiveSession: () => invoke<RecoverReadingSessionsResult>("reader:recoverActiveSession"),
    getSessions: (input?: GetReadingSessionsInput) => invoke<ReadingSession[]>("reader:getSessions", input),
    getStats: () => invoke<ReadingStatsSummary>("reader:getStats"),
    getSettings: () => invoke<ReaderSettings>("reader:getSettings"),
    updateSettings: (settings: Partial<ReaderSettings>) => invoke<ReaderSettings>("reader:updateSettings", settings)
  },
  settings: {
    get: () => invoke<AppSettings>("settings:get"),
    update: (patch: AppSettingsPatch) => invoke<AppSettings>("settings:update", patch),
    resetSection: (section: SettingsSection) => invoke<AppSettings>("settings:resetSection", section),
    resetReaderSettings: () => invoke<AppSettings>("settings:resetReaderSettings"),
    chooseDataDirectory: () => invoke<string | null>("settings:chooseDataDirectory"),
    chooseLibraryDirectory: () => invoke<string | null>("settings:chooseLibraryDirectory"),
    migrateDataDirectory: (targetDirectory: string) => invoke<AppSettings>("settings:migrateDataDirectory", targetDirectory),
    migrateLibraryDirectory: (targetDirectory: string) => invoke<AppSettings>("settings:migrateLibraryDirectory", targetDirectory)
  },
  storage: {
    getLocations: () => invoke<StorageLocations>("storage:getLocations")
  },
  inspiration: {
    list: () => invoke<InspirationItem[]>("inspiration:list"),
    create: (input: CreateInspirationInput) => invoke<InspirationItem>("inspiration:create", input),
    read: (id: string) => invoke<InspirationItem | undefined>("inspiration:read", id),
    update: (id: string, input: UpdateInspirationInput) => invoke<InspirationItem>("inspiration:update", id, input),
    delete: (id: string) => invoke<InspirationItem[]>("inspiration:delete", id),
    addVariant: (id: string, input: AddInspirationVariantInput) => invoke<InspirationItem>("inspiration:addVariant", id, input)
  },
  ai: {
    getSettings: () => invoke<AISettings>("ai:getSettings"),
    updateSettings: (patch: AISettingsPatch) => invoke<AISettings>("ai:updateSettings", patch),
    saveApiKey: (input: SaveAIApiKeyInput) => invoke<AISettings>("ai:saveApiKey", input),
    clearApiKey: () => invoke<AISettings>("ai:clearApiKey"),
    test: () => invoke<{ ok: boolean; message: string }>("ai:test"),
    run: (input: AIRunInput) => invoke<AIRunResult>("ai:run", input)
  },
  search: {
    global: (query: SearchQuery) => invoke<SearchResult[]>("search:global", query)
  },
  sync: {
    getStatus: () => invoke<SyncStatus>("sync:getStatus"),
    startServer: () => invoke<SyncStatus>("sync:startServer"),
    stopServer: () => invoke<SyncStatus>("sync:stopServer"),
    createPairingToken: () => invoke<PairingTokenResult>("sync:createPairingToken"),
    listDevices: () => invoke<DeviceInfo[]>("sync:listDevices"),
    removeDevice: (deviceId: string) => invoke<DeviceInfo[]>("sync:removeDevice", deviceId)
  },
  backup: {
    create: () => invoke<BackupResult | null>("backup:create"),
    restore: () => invoke<RestoreResult | null>("backup:restore")
  },
  diagnostics: {
    exportDebugInfo: () => invoke<DebugExportResult | null>("diagnostics:exportDebugInfo")
  }
};

contextBridge.exposeInMainWorld("api", api);
