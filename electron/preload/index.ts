import { contextBridge, ipcRenderer, type IpcRendererEvent } from "electron";
import type { DesktopApi } from "../../src/types/api";
import type { OperationState, OperationStartRequest } from "../../src/types/operation";
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
  TxtTocOverrides,
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
  CardRelation,
  CardSummary,
  CardLinkResult,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectListener,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationRunCommand,
  CreationRunResultOf,
  CreationSearchQuery,
  CreationSearchView,
  CreationWorkspaceEvent,
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
  TrashItem,
  UpdateSceneBodyInput,
  ProofQuery,
  ProofView,
  ProofIgnoreListQuery,
  ProofIgnoreEntry,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxCountView,
  InboxUpdateCommand,
  InboxCreateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  DraftImportPreview,
  DraftExportPreset,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationReanchorCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotRestoreWithProtectionCommand,
  SnapshotRestoreWithProtectionResult,
  TrashImpactQuery,
  TrashImpactView,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  ProjectExportView,
  ProjectHomeView,
  ProjectPrintMode,
  ProjectPrintResult,
  StructurePreviewCommand,
  StructureApplyWithProtectionCommand,
  StructureRevertCommand,
  StructurePreviewView,
  StructureApplyResult,
  StructureRevertResult,
  SessionUpdateCommand,
  ProjectUpdateGoalCommand,
  ProjectGoalResult,
  SnapshotRetentionResult,
  ReplacePlanQuery,
  ReplacePlan,
  ReplaceApplyOutcome,
  RelationGraphQuery,
  RelationGraphView
} from "../../src/types/creation";
import type {
  CardExportFilter,
  CardExportResult,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext,
  CardImportSource
} from "../../src/types/card-io";
import type { SearchQuery, SearchResult } from "../../src/types/search";
import type { AutoBackupRunResult, BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "../../src/types/maintenance";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "../../src/types/sync";
import type { AppUpdateInfo } from "../../src/types/updates";

const invoke = <T>(channel: string, ...args: unknown[]): Promise<T> => ipcRenderer.invoke(channel, ...args);

const api: DesktopApi = {
  window: {
    minimize: () => invoke<void>("window:minimize"),
    toggleMaximize: () => invoke<void>("window:toggleMaximize"),
    close: () => invoke<void>("window:close"),
    onMaximizedChange: (callback: (maximized: boolean) => void) => {
      const onEvent = (_event: IpcRendererEvent, maximized: boolean) => callback(maximized);
      ipcRenderer.on("window:maximized-changed", onEvent);
      return () => {
        ipcRenderer.removeListener("window:maximized-changed", onEvent);
      };
    }
  },
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
    getBatchProgress: (bookIds: string[]) => invoke<ReadingProgress[]>("reader:getBatchProgress", bookIds),
    saveEpubLocation: (input: SaveProgressInput) => invoke<ReadingProgress>("reader:saveEpubLocation", input),
    getEpubLocation: (bookId: string) => invoke<ReadingLocation | undefined>("reader:getEpubLocation", bookId),
    saveTxtTocOverrides: (input: { bookId: string; overrides: TxtTocOverrides | null }) =>
      invoke<LibraryBook>("reader:saveTxtTocOverrides", input),
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
    chooseBackupDirectory: () => invoke<string | null>("settings:chooseBackupDirectory"),
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
    readProjectHome: () => invoke<ProjectHomeView>("creation:readProjectHome"),
    readProjectNavigation: (projectId: string) =>
      invoke<CreationProjectNavigation | null>("creation:readProjectNavigation", projectId),
    createProject: (input: CreateProjectInput) => invoke<CreationProjectNavigation>("creation:createProject", input),
    readProjectOutline: (projectId: string) =>
      invoke<CreationProjectOutline | null>("creation:readProjectOutline", projectId),
    runStructure: <Command extends CreationRunCommand>(command: Command) =>
      invoke<CreationRunResultOf<Command>>("creation:runStructure", command),
    structurePreview: (command: StructurePreviewCommand) => invoke<StructurePreviewView>("creation:structurePreview", command),
    structureApply: (command: StructureApplyWithProtectionCommand) => invoke<StructureApplyResult>("creation:structureApply", command),
    structureRevert: (command: StructureRevertCommand) => invoke<StructureRevertResult>("creation:structureRevert", command),
    trashList: (projectId?: string) => invoke<TrashItem[]>("creation:trashList", projectId),
    snapshotList: (query: SnapshotListQuery) => invoke<SnapshotInfo[]>("creation:snapshotList", query),
    search: (query: CreationSearchQuery) => invoke<CreationSearchView>("creation:search", query),
    replacePreview: (query: ReplacePreviewQuery) => invoke<ReplacePreviewView>("creation:replacePreview", query),
    replaceApply: (command: ReplaceApplyCommand) => invoke<ReplaceApplyResult>("creation:replaceApply", command),
    statsView: (projectId: string) => invoke<ProjectStatsView | null>("creation:statsView", projectId),
    sessionList: (query: SessionListQuery) => invoke<SessionEntry[]>("creation:sessionList", query),
    sessionReport: (command: SessionReportCommand) => invoke<SessionReportResult>("creation:sessionReport", command),
    sessionDelete: (command: SessionDeleteCommand) => invoke<SessionReportResult>("creation:sessionDelete", command),
    proofQuery: (query: ProofQuery) => invoke<ProofView>("creation:proofQuery", query),
    proofIgnoreList: (query: ProofIgnoreListQuery) =>
      invoke<ProofIgnoreEntry[]>("creation:proofIgnoreList", query),
    importDraftPreview: () => invoke<DraftImportPreview | null>("creation:importDraftPreview"),
    exportProjectBundle: (projectId: string) =>
      invoke<{ canceled: boolean; directory: string | null }>("creation:exportProjectBundle", { projectId }),
    importProjectBundle: () =>
      invoke<{ canceled: boolean; result: ProjectBundleImportResult | null }>("creation:importProjectBundle"),
    annotationList: (query: AnnotationListQuery) => invoke<Annotation[]>("creation:annotationList", query),
    annotationCreate: (command: AnnotationCreateCommand) => invoke<AnnotationResult>("creation:annotationCreate", command),
    annotationUpdate: (command: AnnotationUpdateCommand) => invoke<AnnotationResult>("creation:annotationUpdate", command),
    annotationDelete: (command: AnnotationDeleteCommand) => invoke<AnnotationResult>("creation:annotationDelete", command),
    annotationReanchor: (command: AnnotationReanchorCommand) => invoke<AnnotationResult>("creation:annotationReanchor", command),
    snapshotPreview: (query: SnapshotPreviewQuery) => invoke<SnapshotPreviewView | null>("creation:snapshotPreview", query),
    snapshotRestoreWithProtection: (command: SnapshotRestoreWithProtectionCommand) =>
      invoke<SnapshotRestoreWithProtectionResult>("creation:snapshotRestoreWithProtection", command),
    trashImpact: (query: TrashImpactQuery) => invoke<TrashImpactView | null>("creation:trashImpact", query),
    resourceList: (query: ResourceListQuery) => invoke<ResourceInfo[]>("creation:resourceList", query),
    attachResource: (projectId: string | undefined, cardId?: string, role?: "attachment" | "cover") =>
      invoke<{ canceled: boolean; resource: ResourceResult | null }>("creation:attachResource", { projectId, cardId, role }),
    detachResource: (resourceId: string) => invoke<ResourceResult>("creation:detachResource", { resourceId }),
    readProjectExport: (projectId: string) => invoke<ProjectExportView | null>("creation:readProjectExport", projectId),
    readProjectPreview: (projectId: string) => invoke<ProjectExportView | null>("creation:readProjectPreview", projectId),
    printProject: (projectId: string, mode: ProjectPrintMode) =>
      invoke<ProjectPrintResult>("creation:printProject", { projectId, mode }),
    migrationStatus: () => invoke<LegacyMigrationStatus | null>("creation:migrationStatus"),
    migrationRun: () => invoke<LegacyMigrationReport>("creation:migrationRun"),
    inboxList: (query: InboxListQuery) => invoke<InboxItem[]>("creation:inboxList", query),
    inboxCount: () => invoke<InboxCountView>("creation:inboxCount"),
    inboxUpdate: (command: InboxUpdateCommand) => invoke<InboxItemResult>("creation:inboxUpdate", command),
    inboxDelete: (command: InboxDeleteCommand) => invoke<InboxItemResult>("creation:inboxDelete", command),
    inboxCreate: (command: InboxCreateCommand) => invoke<InboxItemResult>("creation:inboxCreate", command),
    exportDraft: (projectId: string, preset: DraftExportPreset) =>
      invoke<{ canceled: boolean; filePath: string | null }>("creation:exportDraft", { projectId, preset }),
    cardsList: (query: CardsListQuery) => invoke<CardSummary[]>("creation:cardsList", query),
    cardRead: (cardId: string) => invoke<CardSummary | null>("creation:cardRead", cardId),
    cardTypesList: () => invoke<CardType[]>("creation:cardTypesList"),
    relationTypesList: () => invoke<RelationType[]>("creation:relationTypesList"),
    cardLink: (projectId: string, cardId: string) =>
      invoke<CardLinkResult>("creation:cardLink", { projectId, cardId }),
    cardUnlink: (projectId: string, cardId: string) =>
      invoke<CardLinkResult>("creation:cardUnlink", { projectId, cardId }),
    cardRelations: (cardId: string) =>
      invoke<{ outgoing: CardRelation[]; incoming: CardRelation[] }>("creation:cardRelations", cardId),
    relationGraph: (query: RelationGraphQuery) => invoke<RelationGraphView>("creation:relationGraph", query),
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
    },
    // ---- Phase 1 P1 深模块 seam 接入 ----
    snapshotRetentionRun: () => invoke<SnapshotRetentionResult>("creation:snapshotRetentionRun"),
    cardImportOpenAndParse: () => invoke<CardImportSource | null>("creation:cardImportOpenAndParse"),
    cardImportParse: (input: { text: string; format: "csv" | "markdown" }) =>
      invoke<CardImportPreview>("creation:cardImportParse", input),
    cardImportSchema: (projectId: string) => invoke<CardImportSchemaContext>("creation:cardImportSchema", projectId),
    cardImportPlan: (input: CardImportApplyInput) => invoke<CardImportPlan>("creation:cardImportPlan", input),
    cardImportApply: (input: CardImportApplyInput) => invoke<CardImportApplyResult>("creation:cardImportApply", input),
    cardExportOpenAndWrite: (input: { projectId: string; filter: CardExportFilter; format: "csv" | "markdown" }) =>
      invoke<CardExportResult>("creation:cardExportOpenAndWrite", input),
    replacePlanCreate: (query: ReplacePlanQuery) => invoke<ReplacePlan>("creation:replacePlanCreate", query),
    replacePlanApply: (input: { planId: string; excludedHitIds: string[] }) =>
      invoke<ReplaceApplyOutcome>("creation:replacePlanApply", input),
    sessionUpdate: (command: SessionUpdateCommand) => invoke<SessionReportResult>("creation:sessionUpdate", command),
    projectUpdateGoal: (command: ProjectUpdateGoalCommand) => invoke<ProjectGoalResult>("creation:projectUpdateGoal", command)
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
    restore: () => invoke<RestoreResult | null>("backup:restore"),
    runAuto: () => invoke<AutoBackupRunResult>("backup:runAuto")
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
  },
  operation: {
    start: (request: OperationStartRequest) => invoke<OperationState | null>("operation:start", request),
    getState: (operationId: string) => invoke<OperationState | null>("operation:getState", operationId),
    cancel: (operationId: string) => invoke<void>("operation:cancel", operationId),
    subscribe: async (
      operationId: string,
      listener: (state: OperationState) => void
    ): Promise<() => void> => {
      const { subscriptionId } = await invoke<{ subscriptionId: string }>("operation:subscribe", operationId);
      const onEvent = (
        _event: IpcRendererEvent,
        payload: { subscriptionId: string; state: OperationState }
      ): void => {
        if (payload.subscriptionId === subscriptionId) listener(payload.state);
      };
      ipcRenderer.on("operation:event", onEvent);
      // 返回退订函数：移除事件监听并向主进程注销 subscription（避免泄漏）。
      return () => {
        ipcRenderer.removeListener("operation:event", onEvent);
        void invoke<void>("operation:unsubscribe", { subscriptionId });
      };
    }
  }
};

contextBridge.exposeInMainWorld("api", api);
