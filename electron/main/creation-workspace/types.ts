import type {
  ChapterNumberingKind,
  ChapterCreateCommand,
  ChapterDeleteCommand,
  ChapterMergeCommand,
  ChapterMoveCommand,
  ChapterRenameCommand,
  ChapterReorderCommand,
  ChapterSetNumberingCommand,
  ChapterSetStatusCommand,
  ChaptersSetStatusCommand,
  ChapterSplitCommand,
  CreationChapter,
  CreationDocument,
  CreationNavigationChapter,
  CreationNavigationScene,
  CreationOutlineChapter,
  CreationOutlineScene,
  CreationOutlineVolume,
  CreationProject,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSetup,
  CreationProjectSummary,
  CreationProjectTemplate,
  CreationProjectTree,
  CreationStructureResult,
  CreationVolume,
  CreateProjectInput,
  CreationWorkspaceErrorCode,
  CreationWorkspaceEvent,
  ReadProjectOutlineQuery,
  SceneBodyView,
  SceneCreateCommand,
  SceneDeleteCommand,
  SceneMoveCommand,
  SceneRenameCommand,
  SceneReorderCommand,
  StructureCommand,
  UpdateSceneBodyInput,
  UpdateSceneBodyResult,
  VolumeCreateCommand,
  VolumeDeleteCommand,
  VolumeRenameCommand,
  VolumeReorderCommand,
  CardCommand,
  CardCreateCommand,
  CardDeleteCommand,
  CardFieldKind,
  CardFieldSchema,
  CardReadQuery,
  CardRelation,
  CardRelationCreateCommand,
  CardRelationDeleteCommand,
  CardRelationsQuery,
  CardSummary,
  CardType,
  CardTypeCreateCommand,
  CardTypesListQuery,
  CardUpdateCommand,
  CardsListQuery,
  RelationType,
  RelationTypeCreateCommand,
  RelationTypesListQuery,
  HistoryCommand,
  SnapshotCreateCommand,
  SnapshotInfo,
  SnapshotListQuery,
  SnapshotRestoreCommand,
  SnapshotSubjectType,
  TrashEntityKind,
  TrashItem,
  TrashListQuery,
  TrashPurgeCommand,
  TrashRestoreCommand,
  ProjectExportChapter,
  ProjectExportQuery,
  ProjectExportScene,
  ProjectExportView,
  ProjectExportVolume,
  CreationSearchHit,
  CreationSearchQuery,
  CreationSearchScope,
  CreationSearchView,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewHit,
  ReplacePreviewQuery,
  ReplacePreviewView,
  ReplaceScope,
  ProjectDailyStat,
  ProjectStatsView,
  SessionDeleteCommand,
  SessionReportCommand,
  SessionReportResult,
  SessionEntry,
  SessionListQuery,
  StatsViewQuery
} from "../../../src/types/creation";

export type {
  ChapterNumberingKind,
  ChapterCreateCommand,
  ChapterDeleteCommand,
  ChapterMergeCommand,
  ChapterMoveCommand,
  ChapterRenameCommand,
  ChapterReorderCommand,
  ChapterSetNumberingCommand,
  ChapterSetStatusCommand,
  ChaptersSetStatusCommand,
  ChapterSplitCommand,
  CreationChapter,
  CreationDocument,
  CreationNavigationChapter,
  CreationNavigationScene,
  CreationOutlineChapter,
  CreationOutlineScene,
  CreationOutlineVolume,
  CreationProject,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSetup,
  CreationProjectSummary,
  CreationProjectTemplate,
  CreationProjectTree,
  CreationVolume,
  CreateProjectInput,
  CreationWorkspaceErrorCode,
  CreationWorkspaceEvent,
  ReadProjectOutlineQuery,
  SceneBodyView,
  SceneCreateCommand,
  SceneDeleteCommand,
  SceneMoveCommand,
  SceneRenameCommand,
  SceneReorderCommand,
  StructureCommand,
  CreationStructureResult,
  UpdateSceneBodyInput,
  UpdateSceneBodyResult,
  VolumeCreateCommand,
  VolumeDeleteCommand,
  VolumeRenameCommand,
  VolumeReorderCommand,
  CardCommand,
  CardCreateCommand,
  CardDeleteCommand,
  CardFieldKind,
  CardFieldSchema,
  CardReadQuery,
  CardRelation,
  CardRelationCreateCommand,
  CardRelationDeleteCommand,
  CardRelationsQuery,
  CardSummary,
  CardType,
  CardTypeCreateCommand,
  CardTypesListQuery,
  CardUpdateCommand,
  CardsListQuery,
  RelationType,
  RelationTypeCreateCommand,
  RelationTypesListQuery,
  HistoryCommand,
  SnapshotCreateCommand,
  SnapshotInfo,
  SnapshotListQuery,
  SnapshotRestoreCommand,
  SnapshotSubjectType,
  TrashEntityKind,
  TrashItem,
  TrashListQuery,
  TrashPurgeCommand,
  TrashRestoreCommand,
  ProjectExportChapter,
  ProjectExportQuery,
  ProjectExportScene,
  ProjectExportView,
  ProjectExportVolume,
  CreationSearchHit,
  CreationSearchQuery,
  CreationSearchScope,
  CreationSearchView,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewHit,
  ReplacePreviewQuery,
  ReplacePreviewView,
  ReplaceScope,
  ProjectDailyStat,
  ProjectStatsView,
  SessionDeleteCommand,
  SessionEntry,
  SessionListQuery,
  SessionReportCommand,
  SessionReportResult,
  StatsViewQuery
} from "../../../src/types/creation";

export type IntegritySectionName = "schema" | "relations" | "resources" | "indexes" | "snapshots";

export interface IntegrityIssue {
  code: string;
  message: string;
}

export interface IntegritySection {
  ok: boolean;
  issues: IntegrityIssue[];
}

export interface CreationIntegrityReport {
  ok: boolean;
  schemaVersion: number;
  latestSequence: number;
  checkedAt: string;
  counts: {
    projects: number;
    volumes: number;
    chapters: number;
    scenes: number;
    cards: number;
    relations: number;
    resources: number;
    snapshots: number;
    sessions: number;
  };
  schema: IntegritySection;
  relations: IntegritySection;
  resources: IntegritySection;
  indexes: IntegritySection;
  snapshots: IntegritySection;
}

export interface ReadProjectTreeQuery {
  kind: "project.tree";
  projectId: string;
}

export interface ReadProjectNavigationQuery {
  kind: "project.navigation";
  projectId: string;
}

export interface ReadSceneBodyQuery {
  kind: "scene.body";
  sceneId: string;
}

export interface ListProjectsQuery {
  kind: "projects.list";
}

export type CreationReadQuery =
  | ReadProjectTreeQuery
  | ReadProjectNavigationQuery
  | ReadProjectOutlineQuery
  | ReadSceneBodyQuery
  | ListProjectsQuery
  | CardsListQuery
  | CardReadQuery
  | CardTypesListQuery
  | RelationTypesListQuery
  | CardRelationsQuery
  | TrashListQuery
  | SnapshotListQuery
  | ProjectExportQuery
  | CreationSearchQuery
  | ReplacePreviewQuery
  | StatsViewQuery
  | SessionListQuery;
export type CreationReadResult =
  | CreationProjectTree
  | CreationProjectNavigation
  | CreationProjectOutline
  | SceneBodyView
  | CreationProjectSummary[]
  | CardSummary[]
  | CardSummary
  | CardType[]
  | RelationType[]
  | { outgoing: CardRelation[]; incoming: CardRelation[] }
  | TrashItem[]
  | SnapshotInfo[]
  | ProjectExportView
  | CreationSearchView
  | ReplacePreviewView
  | ProjectStatsView
  | SessionEntry[]
  | null;

export type CreateProjectSetupInput = Omit<CreateProjectInput, "title">;

export interface CreateProjectCommand {
  type: "project.create";
  title: string;
  setup?: CreateProjectSetupInput;
}

export interface UpdateSceneBodyCommand {
  type: "scene.updateBody";
  sceneId: string;
  baseRevision: number;
  body: CreationDocument;
}

export type CreationCommand = CreateProjectCommand | UpdateSceneBodyCommand | StructureCommand | CardCommand | HistoryCommand | ReplaceApplyCommand | SessionReportCommand | SessionDeleteCommand;

export interface CreateProjectResult {
  commandType: "project.create";
  sequence: number;
  projectId: string;
  volumeId: string;
  chapterId: string;
  sceneId: string;
}

export type CreationTransactionResult = CreateProjectResult | UpdateSceneBodyResult | CreationStructureResult | ReplaceApplyResult | SessionReportResult;

export interface CreationWatchScope {
  projectId?: string;
}

export type CreationWorkspaceListener = (event: CreationWorkspaceEvent) => void;

export interface CreationWorkspace {
  read(query: ReadProjectTreeQuery): Promise<CreationProjectTree | null>;
  read(query: ReadProjectNavigationQuery): Promise<CreationProjectNavigation | null>;
  read(query: ReadProjectOutlineQuery): Promise<CreationProjectOutline | null>;
  read(query: ReadSceneBodyQuery): Promise<SceneBodyView | null>;
  read(query: ListProjectsQuery): Promise<CreationProjectSummary[]>;
  read(query: CardsListQuery): Promise<CardSummary[]>;
  read(query: CardReadQuery): Promise<CardSummary | null>;
  read(query: CardTypesListQuery): Promise<CardType[]>;
  read(query: RelationTypesListQuery): Promise<RelationType[]>;
  read(query: CardRelationsQuery): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>;
  read(query: TrashListQuery): Promise<TrashItem[]>;
  read(query: SnapshotListQuery): Promise<SnapshotInfo[]>;
  read(query: ProjectExportQuery): Promise<ProjectExportView | null>;
  read(query: CreationSearchQuery): Promise<CreationSearchView>;
  read(query: ReplacePreviewQuery): Promise<ReplacePreviewView>;
  read(query: StatsViewQuery): Promise<ProjectStatsView | null>;
  read(query: SessionListQuery): Promise<SessionEntry[]>;
  read(query: CreationReadQuery): Promise<CreationReadResult>;
  transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  transact(command: StructureCommand): Promise<CreationStructureResult>;
  transact(command: CardCommand): Promise<CreationStructureResult>;
  transact(command: HistoryCommand): Promise<CreationStructureResult>;
  transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void;
  check(): Promise<CreationIntegrityReport>;
  close(): Promise<void>;
}

export interface OpenCreationWorkspaceOptions {
  directory: string;
}

export class CreationWorkspaceError extends Error {
  readonly code: CreationWorkspaceErrorCode;

  constructor(code: CreationWorkspaceErrorCode, message: string) {
    super(message);
    this.name = "CreationWorkspaceError";
    this.code = code;
  }
}
