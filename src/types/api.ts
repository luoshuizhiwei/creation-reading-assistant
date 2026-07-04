import type {
  EndReadingSessionInput,
  GetReadingSessionsInput,
  LibraryBook,
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderSettings,
  ReadingProgress,
  ReadingLocation,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  UpdateReadingSessionInput
} from "./library";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations } from "./settings";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, SaveAIApiKeyInput } from "./ai";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  InspirationItem,
  UpdateInspirationInput
} from "./inspiration";
import type { SearchQuery, SearchResult } from "./search";
import type { BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "./maintenance";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "./sync";
import type { AppUpdateInfo } from "./updates";

export interface DesktopApi {
  app: {
    getBuildInfo: () => Promise<BuildInfo>;
    getStartupRecovery: () => Promise<StartupRecoveryInfo>;
    markStartupRecoverySeen: () => Promise<void>;
    openDataDirectory: () => Promise<void>;
    openLogDirectory: () => Promise<void>;
    writeRendererLog: (input: RendererLogInput) => Promise<void>;
  };
  updates: {
    check: () => Promise<AppUpdateInfo>;
    openDownload: (url: string) => Promise<void>;
  };
  library: {
    importBook: () => Promise<LibraryBook[]>;
    importEpub: () => Promise<LibraryBook[]>;
    listBooks: () => Promise<LibraryBook[]>;
    removeBook: (bookId: string) => Promise<LibraryBook[]>;
  };
  reader: {
    openBook: (bookId: string) => Promise<ReaderBookPayload>;
    openEpub: (bookId: string) => Promise<ReaderEpubPayload>;
    saveProgress: (input: SaveProgressInput) => Promise<ReadingProgress>;
    getProgress: (bookId: string) => Promise<ReadingProgress | undefined>;
    saveEpubLocation: (input: SaveProgressInput) => Promise<ReadingProgress>;
    getEpubLocation: (bookId: string) => Promise<ReadingLocation | undefined>;
    startSession: (input: StartReadingSessionInput) => Promise<ReadingSession>;
    updateSession: (input: UpdateReadingSessionInput) => Promise<ReadingSession>;
    endSession: (input: EndReadingSessionInput) => Promise<ReadingSession>;
    recoverActiveSession: () => Promise<RecoverReadingSessionsResult>;
    getSessions: (input?: GetReadingSessionsInput) => Promise<ReadingSession[]>;
    getStats: () => Promise<ReadingStatsSummary>;
    getSettings: () => Promise<ReaderSettings>;
    updateSettings: (settings: Partial<ReaderSettings>) => Promise<ReaderSettings>;
  };
  settings: {
    get: () => Promise<AppSettings>;
    update: (patch: AppSettingsPatch) => Promise<AppSettings>;
    resetSection: (section: SettingsSection) => Promise<AppSettings>;
    resetReaderSettings: () => Promise<AppSettings>;
    chooseDataDirectory: () => Promise<string | null>;
    chooseLibraryDirectory: () => Promise<string | null>;
    migrateDataDirectory: (targetDirectory: string) => Promise<AppSettings>;
    migrateLibraryDirectory: (targetDirectory: string) => Promise<AppSettings>;
  };
  storage: {
    getLocations: () => Promise<StorageLocations>;
  };
  inspiration: {
    list: () => Promise<InspirationItem[]>;
    create: (input: CreateInspirationInput) => Promise<InspirationItem>;
    read: (id: string) => Promise<InspirationItem | undefined>;
    update: (id: string, input: UpdateInspirationInput) => Promise<InspirationItem>;
    delete: (id: string) => Promise<InspirationItem[]>;
    addVariant: (id: string, input: AddInspirationVariantInput) => Promise<InspirationItem>;
  };
  ai: {
    getSettings: () => Promise<AISettings>;
    updateSettings: (patch: AISettingsPatch) => Promise<AISettings>;
    saveApiKey: (input: SaveAIApiKeyInput) => Promise<AISettings>;
    clearApiKey: () => Promise<AISettings>;
    test: () => Promise<{ ok: boolean; message: string }>;
    run: (input: AIRunInput) => Promise<AIRunResult>;
  };
  search: {
    global: (query: SearchQuery) => Promise<SearchResult[]>;
  };
  sync: {
    getStatus: () => Promise<SyncStatus>;
    startServer: () => Promise<SyncStatus>;
    stopServer: () => Promise<SyncStatus>;
    createPairingToken: () => Promise<PairingTokenResult>;
    listDevices: () => Promise<DeviceInfo[]>;
    removeDevice: (deviceId: string) => Promise<DeviceInfo[]>;
  };
  backup: {
    create: () => Promise<BackupResult | null>;
    restore: () => Promise<RestoreResult | null>;
  };
  diagnostics: {
    exportDebugInfo: () => Promise<DebugExportResult | null>;
  };
}
