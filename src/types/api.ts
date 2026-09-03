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
  ReadingProgress,
  ReadingLocation,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  TxtTocOverrides,
  UpdateReadingSessionInput
} from "./library";
import type { AppSettings, AppSettingsPatch, SettingsSection, StorageLocations } from "./settings";
import type { OperationState, OperationStartRequest } from "./operation";
import type { AISettings, AISettingsPatch, AIRunInput, AIRunResult, SaveAIApiKeyInput } from "./ai";
import type {
  AddInspirationVariantInput,
  CreateInspirationInput,
  InspirationItem,
  UpdateInspirationInput
} from "./inspiration";
import type {
  CardRelation,
  CardSummary,
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
  ReplaceApplyOutcome
} from "./creation";
import type {
  CardExportFilter,
  CardExportResult,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext,
  CardImportSource
} from "./card-io";
import type { SearchQuery, SearchResult } from "./search";
import type { AutoBackupRunResult, BackupResult, BuildInfo, DebugExportResult, RendererLogInput, RestoreResult, StartupRecoveryInfo } from "./maintenance";
import type { DeviceInfo, PairingTokenResult, SyncStatus } from "./sync";
import type { AppUpdateInfo } from "./updates";

export interface DesktopApi {
  window: {
    minimize: () => void;
    toggleMaximize: () => void;
    close: () => void;
    onMaximizedChange: (callback: (maximized: boolean) => void) => () => void;
  };
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
    saveTxtTocOverrides: (input: { bookId: string; overrides: TxtTocOverrides | null }) => Promise<LibraryBook>;
    startSession: (input: StartReadingSessionInput) => Promise<ReadingSession>;
    updateSession: (input: UpdateReadingSessionInput) => Promise<ReadingSession>;
    endSession: (input: EndReadingSessionInput) => Promise<ReadingSession>;
    recoverActiveSession: () => Promise<RecoverReadingSessionsResult>;
    getSessions: (input?: GetReadingSessionsInput) => Promise<ReadingSession[]>;
    getStats: () => Promise<ReadingStatsSummary>;
    getSettings: () => Promise<ReaderSettings>;
    updateSettings: (settings: Partial<ReaderSettings>) => Promise<ReaderSettings>;
    savePreset: (preset: ReaderPreset) => Promise<ReaderPreset>;
    deletePreset: (presetId: string) => Promise<void>;
    chooseFont: () => Promise<{ fileName: string; filePath: string } | null>;
    getInstalledFonts: () => Promise<string[]>;
    deleteFont: (fileName: string) => Promise<void>;
  };
  settings: {
    get: () => Promise<AppSettings>;
    update: (patch: AppSettingsPatch) => Promise<AppSettings>;
    resetSection: (section: SettingsSection) => Promise<AppSettings>;
    resetReaderSettings: () => Promise<AppSettings>;
    chooseDataDirectory: () => Promise<string | null>;
    chooseLibraryDirectory: () => Promise<string | null>;
    chooseBackupDirectory: () => Promise<string | null>;
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
  creation: {
    listProjects: () => Promise<CreationProjectSummary[]>;
    readProjectHome: () => Promise<ProjectHomeView>;
    readProjectNavigation: (projectId: string) => Promise<CreationProjectNavigation | null>;
    createProject: (input: CreateProjectInput) => Promise<CreationProjectNavigation>;
    readProjectOutline: (projectId: string) => Promise<CreationProjectOutline | null>;
    runStructure: <Command extends CreationRunCommand>(command: Command) => Promise<CreationRunResultOf<Command>>;
    structurePreview: (command: StructurePreviewCommand) => Promise<StructurePreviewView>;
    structureApply: (command: StructureApplyWithProtectionCommand) => Promise<StructureApplyResult>;
    structureRevert: (command: StructureRevertCommand) => Promise<StructureRevertResult>;
    trashList: (projectId: string) => Promise<TrashItem[]>;
    snapshotList: (query: SnapshotListQuery) => Promise<SnapshotInfo[]>;
    search: (query: CreationSearchQuery) => Promise<CreationSearchView>;
    replacePreview: (query: ReplacePreviewQuery) => Promise<ReplacePreviewView>;
    replaceApply: (command: ReplaceApplyCommand) => Promise<ReplaceApplyResult>;
    statsView: (projectId: string) => Promise<ProjectStatsView | null>;
    sessionList: (query: SessionListQuery) => Promise<SessionEntry[]>;
    sessionReport: (command: SessionReportCommand) => Promise<SessionReportResult>;
    sessionDelete: (command: SessionDeleteCommand) => Promise<SessionReportResult>;
    proofQuery: (query: ProofQuery) => Promise<ProofView>;
    importDraftPreview: () => Promise<DraftImportPreview | null>;
    exportProjectBundle: (projectId: string) => Promise<{ canceled: boolean; directory: string | null }>;
    importProjectBundle: () => Promise<{ canceled: boolean; result: ProjectBundleImportResult | null }>;
    annotationList: (query: AnnotationListQuery) => Promise<Annotation[]>;
    annotationCreate: (command: AnnotationCreateCommand) => Promise<AnnotationResult>;
    annotationUpdate: (command: AnnotationUpdateCommand) => Promise<AnnotationResult>;
    annotationDelete: (command: AnnotationDeleteCommand) => Promise<AnnotationResult>;
    annotationReanchor: (command: AnnotationReanchorCommand) => Promise<AnnotationResult>;
    snapshotPreview: (query: SnapshotPreviewQuery) => Promise<SnapshotPreviewView | null>;
    snapshotRestoreWithProtection: (command: SnapshotRestoreWithProtectionCommand) => Promise<SnapshotRestoreWithProtectionResult>;
    trashImpact: (query: TrashImpactQuery) => Promise<TrashImpactView | null>;
    resourceList: (query: ResourceListQuery) => Promise<ResourceInfo[]>;
    attachResource: (projectId: string, cardId?: string) => Promise<{ canceled: boolean; resource: ResourceResult | null }>;
    detachResource: (resourceId: string) => Promise<ResourceResult>;
    readProjectExport: (projectId: string) => Promise<ProjectExportView | null>;
    migrationStatus: () => Promise<LegacyMigrationStatus | null>;
    migrationRun: () => Promise<LegacyMigrationReport>;
    inboxList: (query: InboxListQuery) => Promise<InboxItem[]>;
    inboxCount: () => Promise<InboxCountView>;
    inboxUpdate: (command: InboxUpdateCommand) => Promise<InboxItemResult>;
    inboxDelete: (command: InboxDeleteCommand) => Promise<InboxItemResult>;
    inboxCreate: (command: InboxCreateCommand) => Promise<InboxItemResult>;
    exportDraft: (projectId: string, preset: DraftExportPreset) => Promise<{ canceled: boolean; filePath: string | null }>;
    cardsList: (query: CardsListQuery) => Promise<CardSummary[]>;
    cardRead: (cardId: string) => Promise<CardSummary | null>;
    cardTypesList: (projectId: string) => Promise<CardType[]>;
    relationTypesList: (projectId: string) => Promise<RelationType[]>;
    cardRelations: (cardId: string) => Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>;
    readSceneBody: (sceneId: string) => Promise<SceneBodyView | null>;
    updateSceneBody: (input: UpdateSceneBodyInput) => Promise<SceneSaveResponse>;
    watchProject: (projectId: string, listener: CreationProjectListener) => Promise<() => void>;
    // ---- Phase 1 P1 深模块 seam 接入 ----
    snapshotRetentionRun: () => Promise<SnapshotRetentionResult>;
    cardImportOpenAndParse: () => Promise<CardImportSource | null>;
    cardImportParse: (input: { text: string; format: "csv" | "markdown" }) => Promise<CardImportPreview>;
    cardImportSchema: (projectId: string) => Promise<CardImportSchemaContext>;
    cardImportPlan: (input: CardImportApplyInput) => Promise<CardImportPlan>;
    cardImportApply: (input: CardImportApplyInput) => Promise<CardImportApplyResult>;
    cardExportOpenAndWrite: (input: { projectId: string; filter: CardExportFilter; format: "csv" | "markdown" }) => Promise<CardExportResult>;
    replacePlanCreate: (query: ReplacePlanQuery) => Promise<ReplacePlan>;
    replacePlanApply: (input: { planId: string; excludedHitIds: string[] }) => Promise<ReplaceApplyOutcome>;
    sessionUpdate: (command: SessionUpdateCommand) => Promise<SessionReportResult>;
    projectUpdateGoal: (command: ProjectUpdateGoalCommand) => Promise<ProjectGoalResult>;
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
    runAuto: () => Promise<AutoBackupRunResult>;
  };
  diagnostics: {
    exportDebugInfo: () => Promise<DebugExportResult | null>;
  };
  annotations: {
    getHighlightsByBook: (bookId: string) => Promise<HighlightItem[]>;
    saveHighlight: (item: HighlightItem) => Promise<HighlightItem>;
    deleteHighlight: (id: string) => Promise<void>;
    getBookmarksByBook: (bookId: string) => Promise<BookmarkItem[]>;
    saveBookmark: (item: BookmarkItem) => Promise<BookmarkItem>;
    deleteBookmark: (id: string) => Promise<void>;
  };
  operation: {
    start: (request: OperationStartRequest) => Promise<OperationState | null>;
    getState: (operationId: string) => Promise<OperationState | null>;
    cancel: (operationId: string) => Promise<void>;
    subscribe: (operationId: string, listener: (state: OperationState) => void) => Promise<() => void>;
  };
}
