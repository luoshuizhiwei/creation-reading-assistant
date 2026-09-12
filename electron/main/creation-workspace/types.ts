import type {
  CreationDocument,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationProjectTree,
  CreationStructureResult,
  CreateProjectInput,
  CreationWorkspaceErrorCode,
  CreationWorkspaceEvent,
  ReadProjectOutlineQuery,
  SceneBodyView,
  StructureCommand,
  UpdateSceneBodyResult,
  CardCommand,
  CardLinkResult,
  CardReadQuery,
  CardRelation,
  CardRelationsQuery,
  CardSummary,
  CardType,
  CardTypesListQuery,
  CardsListQuery,
  RelationGraphQuery,
  RelationGraphView,
  RelationType,
  RelationTypesListQuery,
  HistoryCommand,
  SnapshotInfo,
  SnapshotListQuery,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotRestoreWithProtectionCommand,
  SnapshotRestoreWithProtectionResult,
  TrashItem,
  TrashListQuery,
  TrashImpactQuery,
  TrashImpactView,
  ProjectExportQuery,
  ProjectExportView,
  CreationSearchQuery,
  CreationSearchView,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewQuery,
  ReplacePreviewView,
  ProjectStatsView,
  SessionDeleteCommand,
  SessionReportCommand,
  SessionReportResult,
  SessionEntry,
  SessionListQuery,
  StatsViewQuery,
  ProofQuery,
  ProofView,
  InboxCreateCommand,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxCountQuery,
  InboxCountView,
  InboxReadQuery,
  InboxUpdateCommand,
  ProjectImportDraftCommand,
  ProjectImportDraftResult,
  SceneUpdatePlanningCommand,
  SceneUpdatePlanningResult,
  SceneUpdateMetaCommand,
  SceneUpdateMetaResult,
  ProjectBundleData,
  ProjectBundleImportPreview,
  ProjectBundleExportQuery,
  ProjectBundleImportCommand,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationReanchorCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  ResourceAttachCommand,
  ResourceDetachCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  CreationRunCommand,
  CreationRunResultOf,
  InboxConvertToCardCommand,
  InboxConvertToCardResult,
  ProjectHomeQuery,
  ProjectHomeView,
  ProofIgnoreCommand,
  ProofIgnoreEntry,
  ProofIgnoreListQuery,
  ProofIgnoreResult,
  ProofUnignoreCommand
} from "../../../src/types/creation";

import type {
  ProjectGoalResult,
  ProjectUpdateGoalCommand,
  SessionUpdateCommand,
  SnapshotRetentionResult
} from "../../../src/types/creation";

import type {
  CardExportFilter,
  CardExportRow,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportSchemaContext,
} from "../../../src/types/card-io";

import type {
  ReplaceApplyOutcome,
  ReplacePlan,
  ReplacePlanController,
  ReplacePlanQuery
} from "./replace-plan";

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
  CardLinkResult,
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
  CardTypeUpdateCommand,
  CardTypeDeleteCommand,
  CardTypesListQuery,
  CardUpdateCommand,
  CardsListQuery,
  RelationGraphEdge,
  RelationGraphNode,
  RelationGraphQuery,
  RelationGraphView,
  RelationType,
  RelationTypeCreateCommand,
  RelationTypeUpdateCommand,
  RelationTypeDeleteCommand,
  RelationTypesListQuery,
  HistoryCommand,
  SnapshotCreateCommand,
  SnapshotInfo,
  SnapshotListQuery,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotDiffRow,
  SnapshotRestoreWithProtectionCommand,
  SnapshotRestoreWithProtectionResult,
  SnapshotSubjectType,
  TrashEntityKind,
  TrashItem,
  TrashListQuery,
  TrashPurgeCommand,
  TrashImpactQuery,
  TrashImpactView,
  TrashRestoreCommand,
  ProjectExportChapter,
  ProjectExportBlock,
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
  StatsViewQuery,
  ProofIssue,
  ProofQuery,
  ProofRule,
  ProofView,
  ProofLocation,
  ProofScanScope,
  ProofIgnoreCommand,
  ProofIgnoreEntry,
  ProofIgnoreListQuery,
  ProofIgnoreResult,
  ProofUnignoreCommand,
  InboxCreateCommand,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxCountQuery,
  InboxCountView,
  InboxReadQuery,
  InboxUpdateCommand,
  DraftImportChapterInput,
  DraftImportVolumeInput,
  ProjectImportDraftCommand,
  ProjectImportDraftResult,
  ScenePlanning,
  SceneUpdatePlanningCommand,
  SceneUpdatePlanningResult,
  SceneUpdateMetaCommand,
  SceneUpdateMetaResult,
  ProjectBundleData,
  ProjectBundleImportPreview,
  ProjectBundleExportQuery,
  ProjectBundleImportCommand,
  ProjectBundleImportResult,
  Annotation,
  AnnotationAnchor,
  AnnotationCreateCommand,
  AnnotationReanchorCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  ResourceAttachCommand,
  ResourceDetachCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  CreationRunCommand,
  CreationRunResultOf,
  InboxConvertToCardCommand,
  InboxConvertToCardResult,
  ProjectHomeEntry,
  ProjectHomeQuery,
  ProjectHomeView
} from "../../../src/types/creation";

export type { SceneStatus } from "../../../src/types/creation";

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
    inbox: number;
    annotations: number;
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
  | RelationGraphQuery
  | TrashListQuery
  | SnapshotListQuery
  | SnapshotPreviewQuery
  | TrashImpactQuery
  | ProjectExportQuery
  | CreationSearchQuery
  | ReplacePreviewQuery
  | StatsViewQuery
  | SessionListQuery
  | ProofQuery
  | InboxListQuery
  | InboxReadQuery
  | InboxCountQuery
  | ProjectBundleExportQuery
  | AnnotationListQuery
  | ResourceListQuery
  | ProjectHomeQuery
  | ProofIgnoreListQuery;
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
  | SnapshotPreviewView
  | TrashImpactView
  | ProjectExportView
  | CreationSearchView
  | ReplacePreviewView
  | ProjectStatsView
  | SessionEntry[]
  | ProofView
  | InboxItem[]
  | InboxItem
  | InboxCountView
  | ProjectBundleData
  | Annotation[]
  | ResourceInfo[]
  | ProjectHomeView
  | ProofIgnoreEntry[]
  | RelationGraphView
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

export type CreationCommand = CreateProjectCommand | UpdateSceneBodyCommand | StructureCommand | CardCommand | HistoryCommand | ReplaceApplyCommand | SessionReportCommand | SessionDeleteCommand | InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand | InboxConvertToCardCommand | ProjectImportDraftCommand | ProjectBundleImportCommand | AnnotationCreateCommand | AnnotationUpdateCommand | AnnotationDeleteCommand | AnnotationReanchorCommand | ResourceAttachCommand | ResourceDetachCommand | SceneUpdatePlanningCommand | SceneUpdateMetaCommand | StructurePlanCommand | ProofIgnoreCommand | ProofUnignoreCommand;

export interface CreateProjectResult {
  commandType: "project.create";
  sequence: number;
  projectId: string;
  volumeId: string;
  chapterId: string;
  sceneId: string;
}

export type CreationTransactionResult = CreateProjectResult | UpdateSceneBodyResult | CreationStructureResult | CardLinkResult | ReplaceApplyResult | SessionReportResult | InboxItemResult | InboxConvertToCardResult | ProjectImportDraftResult | ProjectBundleImportResult | AnnotationResult | ResourceResult | SceneUpdatePlanningResult | SceneUpdateMetaResult | StructurePlanResult | ProofIgnoreResult;

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
  read(query: RelationGraphQuery): Promise<RelationGraphView>;
  read(query: TrashListQuery): Promise<TrashItem[]>;
  read(query: SnapshotListQuery): Promise<SnapshotInfo[]>;
  read(query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null>;
  read(query: TrashImpactQuery): Promise<TrashImpactView | null>;
  read(query: ProjectExportQuery): Promise<ProjectExportView | null>;
  read(query: CreationSearchQuery): Promise<CreationSearchView>;
  read(query: ReplacePreviewQuery): Promise<ReplacePreviewView>;
  read(query: StatsViewQuery): Promise<ProjectStatsView | null>;
  read(query: SessionListQuery): Promise<SessionEntry[]>;
  read(query: ProofQuery): Promise<ProofView>;
  read(query: InboxListQuery): Promise<InboxItem[]>;
  read(query: InboxReadQuery): Promise<InboxItem | null>;
  read(query: InboxCountQuery): Promise<InboxCountView>;
  read(query: ProjectBundleExportQuery): Promise<ProjectBundleData | null>;
  read(query: AnnotationListQuery): Promise<Annotation[]>;
  read(query: ResourceListQuery): Promise<ResourceInfo[]>;
  read(query: ProjectHomeQuery): Promise<ProjectHomeView>;
  read(query: ProofIgnoreListQuery): Promise<ProofIgnoreEntry[]>;
  read(query: CreationReadQuery): Promise<CreationReadResult>;
  transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  transact(command: StructureCommand): Promise<CreationStructureResult>;
  transact(command: CardCommand): Promise<CreationStructureResult | CardLinkResult>;
  transact(command: HistoryCommand): Promise<CreationStructureResult>;
  transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  transact(command: InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand): Promise<InboxItemResult>;
  transact(command: ProjectImportDraftCommand): Promise<ProjectImportDraftResult>;
  transact(command: ProjectBundleImportCommand): Promise<ProjectBundleImportResult>;
  /** 只读比较项目包卡片稳定 ID；不创建项目、不写文件。 */
  previewProjectBundleImport(data: ProjectBundleData): Promise<ProjectBundleImportPreview>;
  transact(command: AnnotationCreateCommand | AnnotationUpdateCommand | AnnotationDeleteCommand | AnnotationReanchorCommand): Promise<AnnotationResult>;
  transact(command: ResourceAttachCommand | ResourceDetachCommand): Promise<ResourceResult>;
  transact(command: SceneUpdatePlanningCommand): Promise<SceneUpdatePlanningResult>;
  transact(command: SceneUpdateMetaCommand): Promise<SceneUpdateMetaResult>;
  transact(command: ProofIgnoreCommand | ProofUnignoreCommand): Promise<ProofIgnoreResult>;
  previewStructure(command: StructurePreviewCommand): Promise<StructurePreviewView>;
  applyStructure(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult>;
  revertStructure(command: StructureRevertCommand): Promise<StructureRevertResult>;
  restoreSnapshotWithProtection(command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult>;
  transact<Command extends CreationRunCommand>(command: Command): Promise<CreationRunResultOf<Command>>;
  // ---- Phase 1 P1 深模块 seam 接入（与 creation:* IPC / preload / service / hook 同一接口） ----
  /** 分层快照留存：按 system 受控分类计算 keep/delete 并在单事务内执行删除。 */
  runSnapshotRetention(): Promise<SnapshotRetentionResult>;
  /** 读取导入所需项目模式上下文（类型 / 关系类型 / 现有卡片）。 */
  cardImportSchema(projectId: string): Promise<CardImportSchemaContext>;
  /** 读模式上下文 -> 规划（纯，不写库），供 UI 预览可应用结果。 */
  cardImportPlan(input: CardImportApplyInput): Promise<CardImportPlan>;
  /** 读模式上下文 -> 规划 -> 单事务 apply；失败回滚零写入。 */
  cardImportApply(input: CardImportApplyInput): Promise<CardImportApplyResult>;
  /** 按筛选范围读取可导出卡片行（不携带内部 id）。 */
  cardExportRows(projectId: string, filter: CardExportFilter): Promise<CardExportRow[]>;
  /** 全项目替换计划：封存命中场景 revision/哈希后生成一次性 planId。 */
  createReplacePlan(query: ReplacePlanQuery, controller?: ReplacePlanController): Promise<ReplacePlan>;
  /** 应用替换计划（逐命中可排除）：单事务内建保护快照 + 改正文 + 写 change_log。 */
  applyReplacePlan(planId: string, excludedHitIds: string[], controller?: ReplacePlanController): Promise<ReplaceApplyOutcome>;
  /** 修正已有写作会话（仅 startedAt/activeSeconds/netChars 可改）。 */
  sessionUpdate(command: SessionUpdateCommand): Promise<SessionReportResult>;
  /** 更新项目目标（写入 setup_json，不新增表列）。 */
  projectUpdateGoal(command: ProjectUpdateGoalCommand): Promise<ProjectGoalResult>;
  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void;
  check(): Promise<CreationIntegrityReport>;
  close(): Promise<void>;
}

export interface OpenCreationWorkspaceOptions {
  directory: string;
  /** 契约夹具专用：创建/升级到 v9 后暂停，生产调用不得设置。 */
  testOnlyTargetSchemaVersion?: 9;
  /** 契约夹具专用：注入 v9→v10 备份失败。 */
  testOnlyFailV9ToV10Backup?: boolean;
  /** 契约夹具专用：注入 v9→v10 事务中途失败。 */
  testOnlyFailV9ToV10AfterLinkBackfill?: boolean;
}

export class CreationWorkspaceError extends Error {
  readonly code: CreationWorkspaceErrorCode;

  constructor(code: CreationWorkspaceErrorCode, message: string) {
    super(message);
    this.name = "CreationWorkspaceError";
    this.code = code;
  }
}

// 大纲安全重组 seam（Part 2）——复用 renderer 类型定义。
import type {
  ProtectedStructureCommand,
  StructureAffectedObject,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructurePlanCommand,
  StructurePlanResult,
  StructurePlanRow,
  StructurePreviewCommand,
  StructurePreviewView,
  StructureRevertCommand,
  StructureRevertResult
} from "../../../src/types/creation";

export type {
  ProtectedStructureCommand,
  StructureAffectedObject,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructurePlanCommand,
  StructurePlanResult,
  StructurePlanRow,
  StructurePreviewCommand,
  StructurePreviewView,
  StructureRevertCommand,
  StructureRevertResult
};
