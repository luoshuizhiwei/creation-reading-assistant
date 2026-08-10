import { contextBridge, ipcRenderer, type IpcRendererEvent } from "electron";
import type { DesktopApi } from "../../src/types/api";
import type {
  EndReadingSessionInput,
  GetReadingSessionsInput,
  BookmarkItem,
  HighlightItem,
  LibraryBook,
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderPreset,
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
import type {
  CardCommand,
  CardRelation,
  CardSummary,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectListener,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationSearchQuery,
  CreationSearchView,
  CreationStructureResult,
  CreationWorkspaceEvent,
  HistoryCommand,
  ProjectStatsView,
  RelationType,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewQuery,
  ReplacePreviewView,
  SceneBodyView,
  SceneSaveResponse,
  SessionDeleteCommand,
  SessionEntry,
  SessionListQuery,
  SessionReportCommand,
  SessionReportResult,
  SnapshotInfo,
  SnapshotListQuery,
  StructureCommand,
  TrashItem,
  TrashListQuery,
  UpdateSceneBodyInput,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxUpdateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus
} from "../../src/types/creation";
import type { SearchQuery, SearchResult } from "../../src/types/search";
import type { BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "../../src/types/maintenance";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "../../src/types/sync";
import type { AppUpdateInfo } from "../../src/types/updates";

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
  updates: {
    check: () => invoke<AppUpdateInfo>("updates:check"),
    openDownload: (url: string) => invoke<void>("updates:openDownload", url)
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
    updateSettings: (settings: Partial<ReaderSettings>) => invoke<ReaderSettings>("reader:updateSettings", settings),
    savePreset: (preset: ReaderPreset) => invoke<ReaderPreset>("reader:savePreset", preset),
    deletePreset: (presetId: string) => invoke<void>("reader:deletePreset", presetId),
    chooseFont: () => invoke<{ fileName: string; filePath: string } | null>("reader:chooseFont"),
    getInstalledFonts: () => invoke<string[]>("reader:getInstalledFonts"),
    deleteFont: (fileName: string) => invoke<void>("reader:deleteFont", fileName)
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
  creation: {
    listProjects: () => invoke<CreationProjectSummary[]>("creation:listProjects"),
    readProjectNavigation: (projectId: string) =>
      invoke<CreationProjectNavigation | null>("creation:readProjectNavigation", projectId),
    createProject: (input: CreateProjectInput) => invoke<CreationProjectNavigation>("creation:createProject", input),
    readProjectOutline: (projectId: string) =>
      invoke<CreationProjectOutline | null>("creation:readProjectOutline", projectId),
    runStructure: (command: StructureCommand | CardCommand | HistoryCommand) =>
      invoke<CreationStructureResult>("creation:runStructure", command),
    trashList: (projectId: string) => invoke<TrashItem[]>("creation:trashList", projectId),
    snapshotList: (query: SnapshotListQuery) => invoke<SnapshotInfo[]>("creation:snapshotList", query),
    search: (query: CreationSearchQuery) => invoke<CreationSearchView>("creation:search", query),
    replacePreview: (query: ReplacePreviewQuery) => invoke<ReplacePreviewView>("creation:replacePreview", query),
    replaceApply: (command: ReplaceApplyCommand) => invoke<ReplaceApplyResult>("creation:replaceApply", command),
    statsView: (projectId: string) => invoke<ProjectStatsView | null>("creation:statsView", projectId),
    sessionList: (query: SessionListQuery) => invoke<SessionEntry[]>("creation:sessionList", query),
    sessionReport: (command: SessionReportCommand) => invoke<SessionReportResult>("creation:sessionReport", command),
    sessionDelete: (command: SessionDeleteCommand) => invoke<SessionReportResult>("creation:sessionDelete", command),
    proofQuery: (query: ProofQuery) => invoke<ProofView>("creation:proofQuery", query),
    migrationStatus: () => invoke<LegacyMigrationStatus | null>("creation:migrationStatus"),
    migrationRun: () => invoke<LegacyMigrationReport>("creation:migrationRun"),
    inboxList: (query: InboxListQuery) => invoke<InboxItem[]>("creation:inboxList", query),
    inboxUpdate: (command: InboxUpdateCommand) => invoke<InboxItemResult>("creation:inboxUpdate", command),
    inboxDelete: (command: InboxDeleteCommand) => invoke<InboxItemResult>("creation:inboxDelete", command),
    exportDraft: (projectId: string) =>
      invoke<{ canceled: boolean; filePath: string | null }>("creation:exportDraft", { projectId }),
    cardsList: (query: CardsListQuery) => invoke<CardSummary[]>("creation:cardsList", query),
    cardRead: (cardId: string) => invoke<CardSummary | null>("creation:cardRead", cardId),
    cardTypesList: (projectId: string) => invoke<CardType[]>("creation:cardTypesList", projectId),
    relationTypesList: (projectId: string) => invoke<RelationType[]>("creation:relationTypesList", projectId),
    cardRelations: (cardId: string) =>
      invoke<{ outgoing: CardRelation[]; incoming: CardRelation[] }>("creation:cardRelations", cardId),
    readSceneBody: (sceneId: string) => invoke<SceneBodyView | null>("creation:readSceneBody", sceneId),
    updateSceneBody: (input: UpdateSceneBodyInput) => invoke<SceneSaveResponse>("creation:updateSceneBody", input),
    watchProject: async (projectId: string, listener: CreationProjectListener) => {
      const { subscriptionId } = await invoke<{ subscriptionId: string }>("creation:watchProject", projectId);
      const onEvent = (_event: IpcRendererEvent, payload: { subscriptionId: string; event: CreationWorkspaceEvent }) => {
        if (payload.subscriptionId === subscriptionId) listener(payload.event);
      };
      ipcRenderer.on("creation:event", onEvent);
      return () => {
        ipcRenderer.removeListener("creation:event", onEvent);
        void invoke<void>("creation:unwatchProject", { subscriptionId });
      };
    }
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
  },
  annotations: {
    getHighlightsByBook: (bookId: string) => invoke<HighlightItem[]>("highlights:getByBook", bookId),
    saveHighlight: (item: HighlightItem) => invoke<HighlightItem>("highlights:save", item),
    deleteHighlight: (id: string) => invoke<void>("highlights:delete", id),
    getBookmarksByBook: (bookId: string) => invoke<BookmarkItem[]>("bookmarks:getByBook", bookId),
    saveBookmark: (item: BookmarkItem) => invoke<BookmarkItem>("bookmarks:save", item),
    deleteBookmark: (id: string) => invoke<void>("bookmarks:delete", id)
  }
};

contextBridge.exposeInMainWorld("api", api);
