import type {
  CreationDocument,
  CreationProjectSetup,
  ChapterNumberingKind,
  CardFieldSchema,
  TrashEntityKind,
  SnapshotSubjectType,
  ScenePlanning,
  SceneStatus,
  CreationWorkspaceErrorCode,
  ReplacePlanScope,
  ReplacePlanMode,
  ReplaceScope
} from "./primitives";
import type {
  AnnotationAnchor,
  ProjectBundleCardMapping,
  ProjectBundleCardResolution,
  ProjectBundleData,
  ProjectBundleResourceFile
} from "./model";

// ---------------------------------------------------------------------------
// 场景正文保存
// ---------------------------------------------------------------------------

export interface UpdateSceneBodyInput {
  sceneId: string;
  baseRevision: number;
  body: CreationDocument;
}

export interface UpdateSceneBodyResult {
  commandType: "scene.updateBody";
  sequence: number;
  projectId: string;
  sceneId: string;
  revision: number;
  updatedAt: string;
}

export interface SceneSaveFailure {
  code: CreationWorkspaceErrorCode;
  message: string;
  currentRevision?: number;
}

/** IPC 保存信封：成功携带事务结果；失败携带类型化错误（revision 冲突时附当前 revision）。 */
export type SceneSaveResponse =
  | { ok: true; result: UpdateSceneBodyResult }
  | { ok: false; error: SceneSaveFailure };

// ---------------------------------------------------------------------------
// 结构命令（卷 / 章 / 场景）
// ---------------------------------------------------------------------------

export interface VolumeCreateCommand {
  type: "volume.create";
  projectId: string;
  title: string;
  beforeVolumeId?: string;
}

export interface VolumeRenameCommand {
  type: "volume.rename";
  volumeId: string;
  title: string;
  baseRevision: number;
}

export interface VolumeReorderCommand {
  type: "volume.reorder";
  volumeId: string;
  beforeVolumeId?: string;
}

export interface VolumeDeleteCommand {
  type: "volume.delete";
  volumeId: string;
}

export interface ChapterCreateCommand {
  type: "chapter.create";
  projectId: string;
  volumeId?: string;
  title: string;
  beforeChapterId?: string;
}

export interface ChapterRenameCommand {
  type: "chapter.rename";
  chapterId: string;
  title: string;
  baseRevision: number;
}

export interface ChapterReorderCommand {
  type: "chapter.reorder";
  chapterId: string;
  beforeChapterId?: string;
}

export interface ChapterMoveCommand {
  type: "chapter.move";
  chapterId: string;
  targetVolumeId: string;
  beforeChapterId?: string;
}

export interface ChapterDeleteCommand {
  type: "chapter.delete";
  chapterId: string;
}

export interface ChapterSetStatusCommand {
  type: "chapter.setStatus";
  chapterId: string;
  status: string;
  baseRevision: number;
}

export interface ChapterSetNumberingCommand {
  type: "chapter.setNumbering";
  chapterId: string;
  numbering: ChapterNumberingKind;
  customNumber?: string;
  baseRevision: number;
}

export interface SceneCreateCommand {
  type: "scene.create";
  chapterId: string;
  title: string;
  beforeSceneId?: string;
}

export interface SceneRenameCommand {
  type: "scene.rename";
  sceneId: string;
  title: string;
  baseRevision: number;
}

export interface SceneReorderCommand {
  type: "scene.reorder";
  sceneId: string;
  beforeSceneId?: string;
}

export interface SceneMoveCommand {
  type: "scene.move";
  sceneId: string;
  targetChapterId: string;
  beforeSceneId?: string;
}

export interface SceneDeleteCommand {
  type: "scene.delete";
  sceneId: string;
}

/** 按场景边界拆章：splitSceneId（含）及之后场景移入新章；原章必须至少保留一个场景。 */
export interface ChapterSplitCommand {
  type: "chapter.split";
  chapterId: string;
  splitSceneId: string;
  newChapterTitle?: string;
}

/** 合并同卷相邻章节：源章节场景并入目标章节末尾，源章节软删除。 */
export interface ChapterMergeCommand {
  type: "chapter.merge";
  sourceChapterId: string;
  targetChapterId: string;
}

/** 批量修改章节工作流状态（必须属于同一作品）。 */
export interface ChaptersSetStatusCommand {
  type: "chapters.setStatus";
  chapterIds: string[];
  status: string;
}

/** 结构命令统一返回：entityId 为被操作的目标实体 id。 */
export interface CreationStructureResult {
  commandType: string;
  sequence: number;
  /** 全局卡片库命令没有单一项目时为 null。 */
  projectId: string | null;
  entityId: string;
  revision: number;
  updatedAt: string;
}

export type StructureCommand =
  | VolumeCreateCommand
  | VolumeRenameCommand
  | VolumeReorderCommand
  | VolumeDeleteCommand
  | ChapterCreateCommand
  | ChapterRenameCommand
  | ChapterReorderCommand
  | ChapterMoveCommand
  | ChapterDeleteCommand
  | ChapterSetStatusCommand
  | ChapterSetNumberingCommand
  | ChapterSplitCommand
  | ChapterMergeCommand
  | ChaptersSetStatusCommand
  | SceneCreateCommand
  | SceneRenameCommand
  | SceneReorderCommand
  | SceneMoveCommand
  | SceneDeleteCommand;

// ---------------------------------------------------------------------------
// 卡片命令
// ---------------------------------------------------------------------------

export interface CardTypeCreateCommand {
  type: "cardType.create";
  /** 可选来源项目，仅用于刷新兼容 UI；类型本体始终是全局资产。 */
  projectId?: string;
  name: string;
  fields: CardFieldSchema[];
}

export interface CardTypeUpdateCommand {
  type: "cardType.update";
  cardTypeId: string;
  name: string;
  fields: CardFieldSchema[];
  baseRevision: number;
}

export interface CardTypeDeleteCommand {
  type: "cardType.delete";
  cardTypeId: string;
  baseRevision: number;
}

export interface RelationTypeCreateCommand {
  type: "relationType.create";
  /** 可选来源项目，仅用于刷新兼容 UI；关系类型本体始终是全局资产。 */
  projectId?: string;
  forwardName: string;
  reverseName: string;
  fromKinds?: string[];
  toKinds?: string[];
}

export interface RelationTypeUpdateCommand {
  type: "relationType.update";
  relationTypeId: string;
  /** 稳定内部 key；更新时必须与当前值一致。 */
  name: string;
  forwardName: string;
  reverseName: string;
  fromKinds?: string[];
  toKinds?: string[];
  baseRevision: number;
}

export interface RelationTypeDeleteCommand {
  type: "relationType.delete";
  relationTypeId: string;
  baseRevision: number;
}

export interface CardCreateCommand {
  type: "card.create";
  /** 提供时创建后立即关联项目；省略时只创建全局卡片。 */
  projectId?: string;
  kind: string;
  title: string;
  aliases?: string[];
  fields?: Record<string, unknown>;
  tags?: string[];
  /** 自由结构内容（如摘录来源快照），最大 1MB。 */
  content?: Record<string, unknown>;
}

export interface CardUpdateCommand {
  type: "card.update";
  cardId: string;
  /** 换卡片类型（跨列移动）；省略表示保持原类型。 */
  kind?: string;
  title?: string;
  aliases?: string[];
  fields?: Record<string, unknown>;
  tags?: string[];
  baseRevision: number;
}

export interface CardDeleteCommand {
  type: "card.delete";
  cardId: string;
}

export interface CardRelationCreateCommand {
  type: "cardRelation.create";
  /** 可选操作上下文；关系本体始终是全局资产。 */
  projectId?: string;
  fromCardId: string;
  toCardId: string;
  relationTypeId: string;
  note?: string;
}

export interface CardRelationDeleteCommand {
  type: "cardRelation.delete";
  relationId: string;
}

export interface CardLinkCommand {
  type: "card.link";
  projectId: string;
  cardId: string;
}

export interface CardUnlinkCommand {
  type: "card.unlink";
  projectId: string;
  cardId: string;
}

export interface CardLinkResult {
  commandType: "card.link" | "card.unlink";
  sequence: number;
  projectId: string;
  cardId: string;
  /** 兼容结构命令结果；等于 cardId。 */
  entityId: string;
  revision: number;
  updatedAt: string;
  /** 本次是否改变了关联记录；重复 link/unlink 为 false。 */
  changed: boolean;
  linked: boolean;
  usageCount: number;
}

export type CardCommand =
  | CardTypeCreateCommand
  | CardTypeUpdateCommand
  | CardTypeDeleteCommand
  | RelationTypeCreateCommand
  | RelationTypeUpdateCommand
  | RelationTypeDeleteCommand
  | CardCreateCommand
  | CardUpdateCommand
  | CardDeleteCommand
  | CardLinkCommand
  | CardUnlinkCommand
  | CardRelationCreateCommand
  | CardRelationDeleteCommand;

// ---------------------------------------------------------------------------
// 回收站 / 快照命令
// ---------------------------------------------------------------------------

export interface TrashRestoreCommand {
  type: "trash.restore";
  /** 全局卡片恢复不带 projectId。 */
  projectId?: string;
  entity: TrashEntityKind;
  entityId: string;
}

export interface TrashPurgeCommand {
  type: "trash.purge";
  /** 全局卡片永久清理不带 projectId。 */
  projectId?: string;
  entity: TrashEntityKind;
  entityId: string;
}

export interface SnapshotCreateCommand {
  type: "snapshot.create";
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
}

export interface SnapshotRestoreWithProtectionCommand {
  type: "snapshot.restoreWithProtection";
  projectId: string;
  snapshotId: string;
  protectionReason: string;
}

export interface SnapshotRestoreWithProtectionResult {
  ok: true;
  protectionSnapshotId: string;
  restoredSubjectType: SnapshotSubjectType;
  restoredSubjectId: string;
  revision: number;
}

export type HistoryCommand = TrashRestoreCommand | TrashPurgeCommand | SnapshotCreateCommand;

// ---------------------------------------------------------------------------
// 旧稿导入
// ---------------------------------------------------------------------------

export interface DraftImportChapterInput {
  title: string;
  /** 章节正文纯文本。 */
  body: string;
}

export interface DraftImportVolumeInput {
  title: string;
  chapters: DraftImportChapterInput[];
}

export interface ProjectImportDraftCommand {
  type: "project.importDraft";
  title: string;
  setup?: CreationProjectSetup;
  volumes: DraftImportVolumeInput[];
}

export interface ProjectImportDraftResult {
  commandType: "project.importDraft";
  sequence: number;
  projectId: string;
  volumeCount: number;
  chapterCount: number;
  sceneCount: number;
}

// ---------------------------------------------------------------------------
// 场景任务卡
// ---------------------------------------------------------------------------

export interface SceneUpdatePlanningCommand {
  type: "scene.updatePlanning";
  sceneId: string;
  planning: ScenePlanning;
}

export interface SceneUpdatePlanningResult {
  commandType: "scene.updatePlanning";
  sequence: number;
  projectId: string;
  sceneId: string;
  updatedAt: string;
}

/** 更新场景摘要与场景状态；独立于章节工作流状态。 */
export interface SceneUpdateMetaCommand {
  type: "scene.updateMeta";
  sceneId: string;
  baseRevision: number;
  summary: string;
  status: SceneStatus;
}

export interface SceneUpdateMetaResult {
  commandType: "scene.updateMeta";
  sequence: number;
  projectId: string;
  sceneId: string;
  revision: number;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// 项目包导入
// ---------------------------------------------------------------------------

export interface ProjectBundleImportCommand {
  type: "project.bundle.import";
  data: ProjectBundleData;
  /** 可选：目标项目 ID（文件层预先生成，保证不与现有项目冲突；缺省时沿用包内 ID 或自动生成）。 */
  targetProjectId?: string;
  /** 可选：资源文件落盘映射。提供时必须与 data.resources 一一对应（路径/哈希/大小均匹配）；缺省时按原 relativePath 登记（仅 DB 层导入）。 */
  resourceFiles?: ProjectBundleResourceFile[];
  /** 对已存在的全局卡片稳定 ID 的显式处理；同内容可 reuse，异内容必须 keep-local 或 import-copy。 */
  cardResolutions?: ProjectBundleCardResolution[];
}

export interface ProjectBundleImportResult {
  commandType: "project.bundle.import";
  sequence: number;
  projectId: string;
  counts: ProjectBundleData["counts"];
  cardMappings: ProjectBundleCardMapping[];
}

// ---------------------------------------------------------------------------
// 批注命令
// ---------------------------------------------------------------------------

export interface AnnotationCreateCommand {
  type: "annotation.create";
  projectId: string;
  sceneId: string;
  cardId?: string;
  anchor: AnnotationAnchor;
  note: string;
  status?: "open" | "resolved";
}

export interface AnnotationUpdateCommand {
  type: "annotation.update";
  annotationId: string;
  baseRevision: number;
  note?: string;
  status?: "open" | "resolved";
  cardId?: string | null;
}

export interface AnnotationDeleteCommand {
  type: "annotation.delete";
  annotationId: string;
}

export interface AnnotationReanchorCommand {
  type: "annotation.reanchor";
  annotationId: string;
  baseRevision: number;
  anchor: AnnotationAnchor;
}

export interface AnnotationResult {
  commandType: "annotation.create" | "annotation.update" | "annotation.delete" | "annotation.reanchor";
  sequence: number;
  annotationId: string;
  revision: number;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// 附件命令
// ---------------------------------------------------------------------------

export interface ResourceAttachCommand {
  type: "resource.attach";
  /** 省略时 cardId 必填，并登记为全局卡片资产。 */
  projectId?: string;
  cardId?: string;
  /** 仅全局卡片资产支持 cover；省略为 attachment。 */
  role?: "attachment" | "cover";
  relativePath: string;
  sha256: string;
  size: number;
  originalName?: string;
}

export interface ResourceDetachCommand {
  type: "resource.detach";
  resourceId: string;
}

export interface ResourceResult {
  commandType: "resource.attach" | "resource.detach";
  sequence: number;
  resourceId: string;
  /** detach 时返回相对路径（供主进程删除文件）。 */
  relativePath?: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// 收件箱命令
// ---------------------------------------------------------------------------

export interface InboxCreateCommand {
  type: "inbox.create";
  title: string;
  body: string;
  /** 灵感类型（plot/character/world 等，缺省 note）。 */
  kind?: string;
  status?: string;
  tags?: string[];
  platformTags?: string[];
  source?: Record<string, unknown> | null;
  variants?: Array<Record<string, unknown>>;
  /** 旧数据迁移时保留的原始灵感 ID；重复的 legacyId 拒绝创建。 */
  legacyId?: string;
}

export interface InboxUpdateCommand {
  type: "inbox.update";
  itemId: string;
  baseRevision: number;
  title?: string;
  body?: string;
  /** 灵感类型（plot/character/world 等）。 */
  kind?: string;
  status?: string;
  tags?: string[];
  platformTags?: string[];
  /** 全部 AI 候选版本（整体覆盖；新增候选需携带既有候选）。 */
  variants?: Array<Record<string, unknown>>;
}

export interface InboxDeleteCommand {
  type: "inbox.delete";
  itemId: string;
}

export interface InboxItemResult {
  commandType: "inbox.create" | "inbox.update" | "inbox.delete";
  sequence: number;
  itemId: string;
  revision: number;
  updatedAt: string;
}

/**
 * 收件箱条目原子转资料卡命令：同一事务内完成校验 → 建卡 → 标 used → 写 change_log。
 * 任何一步失败整体回滚，不产生重复卡片或半更新条目。
 */
export interface InboxConvertToCardCommand {
  type: "inbox.convertToCard";
  itemId: string;
  baseRevision: number;
  projectId: string;
}

export interface InboxConvertToCardResult {
  commandType: "inbox.convertToCard";
  sequence: number;
  itemId: string;
  cardId: string;
  revision: number;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// 查找替换命令（旧版）
// ---------------------------------------------------------------------------

export interface ReplaceApplyCommand {
  type: "replace.apply";
  projectId: string;
  find: string;
  replaceWith: string;
  scope: ReplaceScope;
  scopeId?: string;
  regex?: boolean;
  /** 逐条排除的场景 ID（预览后由用户勾选）。 */
  excludeSceneIds?: string[];
}

export interface ReplaceApplyResult {
  commandType: "replace.apply";
  sequence: number;
  projectId: string;
  /** 实际修改的场景数。 */
  appliedScenes: number;
  /** 实际替换的命中总数。 */
  appliedHits: number;
  /** 因排除而跳过的场景数。 */
  skippedScenes: number;
  /** 自动保护快照 ID（每修改场景一个）。 */
  snapshotIds: string[];
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// 全项目替换计划（ReplacePlan）
// ---------------------------------------------------------------------------

export interface ReplacePlanQuery {
  projectId: string;
  scope: ReplacePlanScope;
  scopeId?: string;
  find: string;
  replaceWith: string;
  mode: ReplacePlanMode;
  limit?: number;
  createdBy?: string;
}

export interface ReplaceTextRange {
  start: number;
  end: number;
}

export interface ReplaceHit {
  hitId: string;
  sceneId: string;
  blockIndex: number;
  range: ReplaceTextRange;
  before: string;
  after: string;
  context: string;
}

export interface ReplacePlanSceneSummary {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  title: string;
  hitCount: number;
}

export interface ReplacePlanSceneSeal {
  sceneId: string;
  revision: number;
  hash: string;
}

export interface ReplacePlan {
  planId: string;
  projectId: string;
  scope: ReplacePlanScope;
  scopeId?: string;
  find: string;
  replaceWith: string;
  mode: ReplacePlanMode;
  scenes: ReplacePlanSceneSummary[];
  hits: ReplaceHit[];
  totalHits: number;
  limit: number;
  truncated: boolean;
  seals: Record<string, ReplacePlanSceneSeal>;
  sealedAt: string;
  expiresAt: string;
  contentHash: string;
}

export interface ReplaceApplyOutcome {
  planId: string;
  sequence: number;
  appliedHitCount: number;
  modifiedSceneIds: string[];
  snapshotIds: string[];
  committedAt: string;
}

// ---------------------------------------------------------------------------
// 会话命令
// ---------------------------------------------------------------------------

export interface SessionReportCommand {
  type: "session.report";
  projectId: string;
  sceneId?: string;
  /** 会话段开始时间（ISO）。 */
  startedAt: string;
  /** 活动秒数（空闲暂停不计入）。 */
  activeSeconds: number;
  /** 净增字符（非空白，输入减删除，可为负）。 */
  netChars: number;
}

export interface SessionDeleteCommand {
  type: "session.delete";
  projectId: string;
  sessionId: string;
}

/**
 * 修正已存在的写作会话（编辑开始时间 / 活动秒数 / 净增字符）。
 * 仅允许更新这三个可修正字段；其余元数据（id、projectId、reportedAt）不可变。
 */
export interface SessionUpdateCommand {
  type: "session.update";
  projectId: string;
  sessionId: string;
  startedAt?: string;
  activeSeconds?: number;
  netChars?: number;
}

export interface SessionReportResult {
  commandType: "session.report" | "session.delete" | "session.update";
  sequence: number;
  projectId: string;
  sessionId: string;
  updatedAt: string;
}

/** 项目目标设置更新命令（写入 setup_json，不新增表列）。 */
export interface ProjectUpdateGoalCommand {
  type: "project.updateGoal";
  projectId: string;
  dailyWordGoal?: number | null;
  weeklyWordGoal?: number | null;
  totalWordGoal?: number | null;
  targetDate?: string | null;
  description?: string | null;
  genre?: string | null;
  weeklyUpdateDays?: number[];
}
