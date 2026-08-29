export type CreationProjectTemplate = "blank" | "long-form" | "serial";

export interface CreationProjectSetup {
  template: CreationProjectTemplate;
  description?: string;
  genre?: string;
  totalWordGoal?: number;
  dailyWordGoal?: number;
  weeklyWordGoal?: number;
  targetDate?: string;
  weeklyUpdateDays: number[];
  chapterWorkflow: string[];
}

export type CreateProjectInput = { title: string } & CreationProjectSetup;

export interface CreationDocument {
  type: "doc";
  content: unknown[];
}

export interface CreationProject {
  id: string;
  title: string;
  setup: CreationProjectSetup;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CreationScene {
  id: string;
  chapterId: string;
  title: string;
  sortOrder: number;
  /** project.tree 的内部兼容正文；renderer 编辑路径使用 readSceneBody 懒读取。 */
  body: CreationDocument;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CreationChapter {
  id: string;
  projectId: string;
  title: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
  scenes: CreationScene[];
}

export interface CreationProjectTree {
  project: CreationProject;
  chapters: CreationChapter[];
}

export interface CreationProjectSummary {
  id: string;
  title: string;
  setup: CreationProjectSetup;
  updatedAt: string;
  revision: number;
  chapterCount: number;
  sceneCount: number;
}

/** 项目首页行项目：含当前非空白字符数（单次聚合查询，不加载场景正文到渲染进程）。 */
export interface ProjectHomeEntry {
  id: string;
  title: string;
  setup: CreationProjectSetup;
  updatedAt: string;
  revision: number;
  chapterCount: number;
  sceneCount: number;
  currentChars: number;
}

export interface ProjectHomeView {
  projects: ProjectHomeEntry[];
}

export interface ProjectHomeQuery {
  kind: "project.home";
}

/** 导航场景：仅元数据，不携带正文（正文通过 scene.body 查询懒读取）。 */
export interface CreationNavigationScene {
  id: string;
  chapterId: string;
  title: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CreationNavigationChapter {
  id: string;
  projectId: string;
  title: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
  scenes: CreationNavigationScene[];
}

export interface CreationProjectNavigation {
  project: CreationProject;
  chapters: CreationNavigationChapter[];
}

export interface SceneBodyView {
  sceneId: string;
  projectId: string;
  chapterId: string;
  title: string;
  body: CreationDocument;
  revision: number;
  updatedAt: string;
}

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

export type CreationWorkspaceErrorCode =
  | "closed"
  | "invalid-input"
  | "not-found"
  | "conflict"
  | "revision-mismatch"
  | "integrity";

export interface SceneSaveFailure {
  code: CreationWorkspaceErrorCode;
  message: string;
  currentRevision?: number;
}

/** IPC 保存信封：成功携带事务结果；失败携带类型化错误（revision 冲突时附当前 revision）。 */
export type SceneSaveResponse =
  | { ok: true; result: UpdateSceneBodyResult }
  | { ok: false; error: SceneSaveFailure };

export interface CreationWorkspaceEvent {
  kind: "committed";
  sequence: number;
  projectId: string | null;
  commandType: string;
  changes: Array<{
    entity: string;
    id: string;
    action: "created" | "updated" | "deleted" | "restored" | "moved";
    revision: number;
  }>;
}

export type CreationProjectListener = (event: CreationWorkspaceEvent) => void;

// ---------------------------------------------------------------------------
// 切片 6：卷章与大纲 —— 结构实体、大纲查询与结构命令
// ---------------------------------------------------------------------------

export type ChapterNumberingKind = "auto" | "prologue" | "extra" | "custom";

export interface CreationVolume {
  id: string;
  projectId: string;
  title: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CreationOutlineScene {
  id: string;
  chapterId: string;
  title: string;
  sortOrder: number;
  /** 场景非空白正文字数（不含标点），供大纲与卡片板显示。 */
  wordCount: number;
  /** 场景任务卡字段（视角/时间/地点/出场/目标/冲突/结果/情绪/目标字数）。 */
  planning?: ScenePlanning;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CreationOutlineChapter {
  id: string;
  volumeId: string | null;
  title: string;
  sortOrder: number;
  status: string;
  numbering: ChapterNumberingKind;
  customNumber: string | null;
  /** 派生显示编号：auto 时按卷内顺序生成「第N章」，prologue/extra 为「序章」「番外」，custom 用 customNumber。 */
  displayNumber: string | null;
  createdAt: string;
  updatedAt: string;
  revision: number;
  scenes: CreationOutlineScene[];
}

export interface CreationOutlineVolume {
  id: string;
  projectId: string;
  title: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
  chapters: CreationOutlineChapter[];
}

export interface CreationProjectOutline {
  project: CreationProject;
  volumes: CreationOutlineVolume[];
  /** 未分卷章节（无卷项目的兼容路径，正常迁移后为空）。 */
  looseChapters: CreationOutlineChapter[];
}

export interface ReadProjectOutlineQuery {
  kind: "project.outline";
  projectId: string;
}

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
  projectId: string;
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
// 切片 7：卡片与关系
// ---------------------------------------------------------------------------

export type CardFieldKind =
  | "text"
  | "multiline"
  | "number"
  | "date"
  | "select"
  | "multiSelect"
  | "boolean"
  | "cardRef"
  | "url"
  | "attachment";

export interface CardFieldSchema {
  key: string;
  label: string;
  kind: CardFieldKind;
  required?: boolean;
  options?: string[];
  defaultValue?: unknown;
}

export interface CardType {
  id: string;
  /** null = 全局内置类型；非 null = 项目自定义。 */
  projectId: string | null;
  kind: string;
  name: string;
  fields: CardFieldSchema[];
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface RelationType {
  id: string;
  projectId: string | null;
  name: string;
  forwardName: string;
  reverseName: string;
  fromKinds: string[];
  toKinds: string[];
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CardSummary {
  id: string;
  projectId: string;
  kind: string;
  title: string;
  aliases: string[];
  fields: Record<string, unknown>;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  revision: number;
}

export interface CardRelation {
  id: string;
  projectId: string;
  fromCardId: string;
  toCardId: string;
  relationTypeId: string;
  forwardName: string;
  note: string | null;
  createdAt: string;
}

export interface CardTypeCreateCommand {
  type: "cardType.create";
  projectId: string;
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
  projectId: string;
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
  projectId: string;
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
  projectId: string;
  fromCardId: string;
  toCardId: string;
  relationTypeId: string;
  note?: string;
}

export interface CardRelationDeleteCommand {
  type: "cardRelation.delete";
  relationId: string;
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
  | CardRelationCreateCommand
  | CardRelationDeleteCommand;

export interface CardsListQuery {
  kind: "cards.list";
  projectId: string;
  /** 按卡片类型 kind 筛选（内置或自定义）。 */
  cardKind?: string;
  /** 标题/别名子串搜索。 */
  search?: string;
}

export interface CardReadQuery {
  kind: "card.read";
  cardId: string;
}

export interface CardTypesListQuery {
  kind: "cardTypes.list";
  projectId: string;
}

export interface RelationTypesListQuery {
  kind: "relationTypes.list";
  projectId: string;
}

export interface CardRelationsQuery {
  kind: "card.relations";
  cardId: string;
}

// ---------------------------------------------------------------------------
// 切片 8：回收站与快照
// ---------------------------------------------------------------------------

export type TrashEntityKind = "volume" | "chapter" | "scene" | "card";

export interface TrashItem {
  entity: TrashEntityKind;
  id: string;
  projectId: string;
  title: string;
  deletedAt: string;
  revision: number;
}

export interface TrashListQuery {
  kind: "trash.list";
  projectId: string;
}

export interface TrashRestoreCommand {
  type: "trash.restore";
  projectId: string;
  entity: TrashEntityKind;
  entityId: string;
}

export interface TrashPurgeCommand {
  type: "trash.purge";
  projectId: string;
  entity: TrashEntityKind;
  entityId: string;
}

export type SnapshotSubjectType = "scene" | "card" | "chapter" | "volume";

export interface SnapshotInfo {
  id: string;
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
  createdAt: string;
}

export interface SnapshotCreateCommand {
  type: "snapshot.create";
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
}

export interface SnapshotListQuery {
  kind: "snapshot.list";
  projectId: string;
  subjectType?: SnapshotSubjectType;
  subjectId?: string;
}

export interface SnapshotPreviewQuery {
  kind: "snapshot.preview";
  projectId: string;
  snapshotId: string;
}

export interface SnapshotDiffRow {
  label: string;
  before: string;
  after: string;
  changed: boolean;
}

export interface SnapshotPreviewView {
  snapshotId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  title: string;
  rows: SnapshotDiffRow[];
  warnings: string[];
  canRestore: boolean;
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

export interface TrashImpactQuery {
  kind: "trash.impact";
  projectId: string;
  entity: TrashEntityKind;
  entityId: string;
}

export interface TrashImpactView {
  title: string;
  childVolumeCount: number;
  childChapterCount: number;
  childSceneCount: number;
  relatedCardCount: number;
  resourceCount: number;
  approxChars: number;
  warnings: string[];
}

export type HistoryCommand = TrashRestoreCommand | TrashPurgeCommand | SnapshotCreateCommand;

// ---------------------------------------------------------------------------
// 切片 9：成稿导出
// ---------------------------------------------------------------------------

export interface ProjectExportScene {
  id: string;
  title: string;
  /** 场景正文纯文本（块间空行、场景分隔换行）。 */
  text: string;
  /** 最小块级视图（仅 kind + 文本，不包含 marks/完整 bodyJson），供审阅稿等需要区分块类型的导出使用。 */
  blocks?: ProjectExportBlock[];
}

/** 场景导出最小块：kind 为块类型（paragraph/quoteLetter/centeredText/authorNote/sceneBreak）。 */
export interface ProjectExportBlock {
  kind: string;
  text: string;
}

export interface ProjectExportChapter {
  id: string;
  title: string;
  displayNumber: string | null;
  scenes: ProjectExportScene[];
}

export interface ProjectExportVolume {
  id: string;
  title: string;
  chapters: ProjectExportChapter[];
}

export interface ProjectExportView {
  projectId: string;
  title: string;
  volumes: ProjectExportVolume[];
}

export interface ProjectExportQuery {
  kind: "project.export";
  projectId: string;
  /** 为 true 时场景附带 blocks（kind + 文本）；默认只返回 text。 */
  includeBlocks?: boolean;
}

/** 成稿导出预设。 */
export type DraftExportPreset = "platform-plain" | "standard-review";

export interface DraftExportBuildResult {
  preset: DraftExportPreset;
  /** 目标文件扩展名（不带点）。 */
  extension: "txt" | "md";
  text: string;
}

// ---------------------------------------------------------------------------
// 切片 9：旧稿导入（TXT/Markdown）
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
// 场景任务卡（蓝图 §5.3）：视角/时间/地点/出场/目标/冲突/结果/情绪/目标字数
// ---------------------------------------------------------------------------

/**
 * 场景任务卡字段（蓝图 §5.3）。
 * 语义：字段未提供（undefined）= 保留旧值；字段为 null = 明确清空。
 * JSON/IPC 会丢失 undefined，因此显式清空一律使用 null。
 */
export interface ScenePlanning {
  /** 视角角色卡片 ID。 */
  perspectiveCardId?: string | null;
  /** 时间或相对时间描述。 */
  time?: string | null;
  /** 地点卡片 ID。 */
  locationCardId?: string | null;
  /** 出场卡片 ID 列表（清空用 [] 或 null）。 */
  castCardIds?: string[] | null;
  goal?: string | null;
  conflict?: string | null;
  outcome?: string | null;
  emotion?: string | null;
  /** 目标字数（非空白字符）。 */
  targetWords?: number | null;
}

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

/**
 * runStructure 通道接受的命令联合。结果类型按命令 type 推导：
 * scene.updatePlanning → SceneUpdatePlanningResult；
 * project.importDraft → ProjectImportDraftResult；
 * 其余结构/卡片/回收站命令 → CreationStructureResult。
 */
export type CreationRunCommand =
  | StructureCommand
  | CardCommand
  | HistoryCommand
  | ProjectImportDraftCommand
  | SceneUpdatePlanningCommand
  | InboxConvertToCardCommand;

/** runStructure 的唯一运行时命令目录；Record 保证新增联合成员时必须同步白名单。 */
export const CREATION_RUN_COMMAND_TYPES: Readonly<Record<CreationRunCommand["type"], true>> = {
  "volume.create": true,
  "volume.rename": true,
  "volume.reorder": true,
  "volume.delete": true,
  "chapter.create": true,
  "chapter.rename": true,
  "chapter.reorder": true,
  "chapter.move": true,
  "chapter.delete": true,
  "chapter.setStatus": true,
  "chapter.setNumbering": true,
  "chapter.split": true,
  "chapter.merge": true,
  "chapters.setStatus": true,
  "scene.create": true,
  "scene.rename": true,
  "scene.reorder": true,
  "scene.move": true,
  "scene.delete": true,
  "cardType.create": true,
  "cardType.update": true,
  "cardType.delete": true,
  "relationType.create": true,
  "relationType.update": true,
  "relationType.delete": true,
  "card.create": true,
  "card.update": true,
  "card.delete": true,
  "cardRelation.create": true,
  "cardRelation.delete": true,
  "trash.restore": true,
  "trash.purge": true,
  "snapshot.create": true,
  "project.importDraft": true,
  "scene.updatePlanning": true,
  "inbox.convertToCard": true
};

export function isCreationRunCommandType(value: unknown): value is CreationRunCommand["type"] {
  return typeof value === "string" && Object.prototype.hasOwnProperty.call(CREATION_RUN_COMMAND_TYPES, value);
}

type _RunStructureCommandTypes = StructureCommand["type"] | CardCommand["type"] | HistoryCommand["type"];

export type CreationRunResultOf<Command extends CreationRunCommand> =
  Command extends { type: "scene.updatePlanning" } ? SceneUpdatePlanningResult :
  Command extends { type: "project.importDraft" } ? ProjectImportDraftResult :
  Command extends { type: "inbox.convertToCard" } ? InboxConvertToCardResult :
  Command extends { type: _RunStructureCommandTypes } ? CreationStructureResult :
  never;

export type CreationRunResult = CreationRunResultOf<CreationRunCommand>;

// ---------------------------------------------------------------------------
// 切片：大纲安全重组 workspace seam（Part 2）
// 本地 compute*Impact 仅作即时 UI 预览；权威预览 / 保护快照 / 单次事务应用 / 撤回
// 必须经由 workspace 的真实 SQLite 事务，不得仅依赖 renderer 本地计算。
// ---------------------------------------------------------------------------

/** 受保护重组覆盖的结构命令。 */
export type ProtectedStructureCommand = Extract<
  CreationRunCommand,
  | { type: "chapter.split" }
  | { type: "chapter.merge" }
  | { type: "chapter.move" }
  | { type: "scene.move" }
  | { type: "chapters.setStatus" }
  | { type: "chapter.setNumbering" }
>;

export type StructurePreviewCommand = {
  type: "structure.preview";
  projectId: string;
  command: ProtectedStructureCommand;
};

export type StructureApplyWithProtectionCommand = {
  type: "structure.applyWithProtection";
  projectId: string;
  /** 一次性权威预览计划；主进程只执行该计划中封存的命令。 */
  planId: string;
  protectionReason: string;
};

export type StructureRevertCommand = {
  type: "structure.revert";
  projectId: string;
  protectionSnapshotId: string;
  /** 应用完成时返回的精确版本集合；任一对象后来被改动即拒绝撤回。 */
  expectedAppliedRevisions: StructureAffectedObject[];
};

export type StructurePlanCommand = StructurePreviewCommand | StructureApplyWithProtectionCommand | StructureRevertCommand;

export type StructurePlanRow = { label: string; value: string };

export type StructurePreviewView = {
  ok: true;
  planId: string;
  command: ProtectedStructureCommand;
  rows: StructurePlanRow[];
  stale: boolean;
  affectedSceneCount: number;
  numberingChange?: string;
  softDeletedChapter?: string;
};

export type StructureAffectedObject = {
  type: "volume" | "chapter" | "scene";
  id: string;
  /** 应用后的精确对象版本；用于撤回前冲突检测，不是项目最大版本。 */
  revision: number;
};

export type StructureApplyResult = {
  ok: true;
  protectionSnapshotId: string;
  affected: StructureAffectedObject[];
  newRevision: number;
};

export type StructureRevertResult = {
  ok: true;
  restoredRevision: number;
};

export type StructurePlanResult = StructurePreviewView | StructureApplyResult | StructureRevertResult;

export type DraftImportFormat = "txt" | "markdown" | "docx";

export interface DraftImportPreviewChapter extends DraftImportChapterInput {
  wordCount: number;
}

export interface DraftImportPreviewVolume {
  title: string;
  chapters: DraftImportPreviewChapter[];
}

export interface DraftImportPreview {
  format: DraftImportFormat;
  fileName: string;
  projectTitle: string;
  volumes: DraftImportPreviewVolume[];
  totalChapters: number;
  totalWords: number;
  warnings: string[];
}

// ---------------------------------------------------------------------------
// 切片 9：完整项目包（可校验重导入）
// ---------------------------------------------------------------------------

export interface ProjectBundleData {
  /** v1 不含批注；v2 增加 annotations 数组（导入端同时接受 v1 与 v2）。 */
  formatVersion: 1 | 2;
  project: {
    id: string;
    title: string;
    setup: CreationProjectSetup;
    createdAt: string;
    updatedAt: string;
    revision: number;
  };
  volumes: Array<{
    id: string;
    title: string;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  chapters: Array<{
    id: string;
    volumeId: string | null;
    title: string;
    sortOrder: number;
    status: string;
    numberingKind: ChapterNumberingKind;
    customNumber: string | null;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  scenes: Array<{
    id: string;
    chapterId: string;
    title: string;
    sortOrder: number;
    bodyJson: string;
    planningJson: string;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  cardTypes: Array<{
    id: string;
    kind: string;
    name: string;
    fieldsJson: string;
    sortOrder: number;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  relationTypes: Array<{
    id: string;
    name: string;
    forwardName: string;
    reverseName: string;
    fromKindsJson: string;
    toKindsJson: string;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  cards: Array<{
    id: string;
    kind: string;
    title: string;
    aliasesJson: string;
    fieldsJson: string;
    tagsJson: string;
    contentJson: string;
    createdAt: string;
    updatedAt: string;
    revision: number;
  }>;
  relations: Array<{
    id: string;
    fromCardId: string;
    toCardId: string;
    relationType: string;
    note: string | null;
    createdAt: string;
  }>;
  snapshots: Array<{
    id: string;
    subjectType: string;
    subjectId: string;
    payloadJson: string;
    createdAt: string;
  }>;
  /** 附件元数据（文件实体位于项目包 resources/** 目录，由文件层负责校验与落盘）。 */
  resources: Array<{
    id: string;
    cardId: string | null;
    /** 工作区 resources 目录内的相对路径（项目包内对应 resources/<去前缀路径>）。 */
    relativePath: string;
    sha256: string;
    size: number;
    originalName: string | null;
    createdAt: string;
  }>;
  /** v2 起导出全部未删除批注（scene/card 引用保持包内 ID，导入时随目标 ID 映射重映射）。 */
  annotations: Array<{
    id: string;
    sceneId: string;
    cardId: string | null;
    anchor: AnnotationAnchor;
    note: string;
    status: "open" | "resolved";
    revision: number;
    createdAt: string;
    updatedAt: string;
  }>;
  counts: {
    volumes: number;
    chapters: number;
    scenes: number;
    cards: number;
    relations: number;
    snapshots: number;
    resources: number;
    annotations: number;
  };
  exportedAt: string;
}

export interface ProjectBundleExportQuery {
  kind: "project.bundle.export";
  projectId: string;
}

export interface ProjectBundleImportCommand {
  type: "project.bundle.import";
  data: ProjectBundleData;
  /** 可选：目标项目 ID（文件层预先生成，保证不与现有项目冲突；缺省时沿用包内 ID 或自动生成）。 */
  targetProjectId?: string;
  /** 可选：资源文件落盘映射。提供时必须与 data.resources 一一对应（路径/哈希/大小均匹配）；缺省时按原 relativePath 登记（仅 DB 层导入）。 */
  resourceFiles?: ProjectBundleResourceFile[];
}

export interface ProjectBundleResourceFile {
  /** 项目包内资源相对路径（resources/**）。 */
  relativePath: string;
  /** 导入后工作区内的新相对路径（resources/<新项目ID>/<文件名>）。 */
  targetRelativePath: string;
  sha256: string;
  size: number;
}

export interface ProjectBundleImportResult {
  commandType: "project.bundle.import";
  sequence: number;
  projectId: string;
  counts: ProjectBundleData["counts"];
}

// ---------------------------------------------------------------------------
// 切片 7 补：批注与引用（§5.7）
// ---------------------------------------------------------------------------

/** 批注锚点：段落索引 + 段内文本偏移（编辑后可能失效，进入待重新定位）。 */
export interface AnnotationAnchor {
  /** 场景正文中的块索引（text 所在块）。 */
  blockIndex: number;
  /** 块内文本起始偏移。 */
  textOffset: number;
  /** 锚定文本长度。 */
  textLength: number;
  /** 创建时锚定文本快照（校验内容是否仍一致）。 */
  text?: string;
}

export interface Annotation {
  id: string;
  projectId: string;
  sceneId: string;
  cardId: string | null;
  /** 锚点；编辑导致失效时 anchorInvalid=true（不静默删除）。 */
  anchor: AnnotationAnchor;
  anchorInvalid: boolean;
  note: string;
  status: "open" | "resolved";
  /** 锚定文本当前内容（失效时可能为空）。 */
  anchoredText: string;
  /** 当前行的乐观并发 revision；更新批注时必须作为 baseRevision 提交，避免静默覆盖。 */
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface AnnotationListQuery {
  kind: "annotation.list";
  projectId: string;
  sceneId?: string;
  /** 返回上限，默认 200，最大 2000。 */
  limit?: number;
}

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
// 切片 7 补：卡片附件（§5.6）
// ---------------------------------------------------------------------------

export interface ResourceInfo {
  id: string;
  projectId: string;
  cardId: string | null;
  /** 工作区 resources 目录内的相对路径（项目包可移植）。 */
  relativePath: string;
  sha256: string;
  size: number;
  originalName: string | null;
  createdAt: string;
}

export interface ResourceListQuery {
  kind: "resource.list";
  projectId: string;
  cardId?: string;
}

export interface ResourceAttachCommand {
  type: "resource.attach";
  projectId: string;
  cardId?: string;
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
// 切片 10：搜索与查找替换
// ---------------------------------------------------------------------------

export type CreationSearchScope = "scene" | "card" | "chapter" | "project";

export interface CreationSearchFilters {
  /** 仅搜索指定卡片类型（kind）。 */
  cardKinds?: string[];
  /** 仅搜索指定章节工作流状态。 */
  chapterStatuses?: string[];
  /** 仅搜索带指定标签的卡片（标签全命中）。 */
  tags?: string[];
}

export interface CreationSearchQuery {
  kind: "search.query";
  /** 搜索关键词（普通文本子串匹配，不做正则）。 */
  text: string;
  /** 限定单个项目；缺省时全局搜索所有项目。 */
  projectId?: string;
  /** 搜索范围；缺省覆盖全部四种。 */
  scopes?: CreationSearchScope[];
  filters?: CreationSearchFilters;
  /** 返回上限，默认 50，最大 200。 */
  limit?: number;
}

export interface CreationSearchHit {
  kind: CreationSearchScope;
  id: string;
  projectId: string;
  projectTitle: string;
  title: string;
  /** 命中上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 场景/章节命中时给出父章节信息。 */
  chapterId?: string;
  chapterTitle?: string;
  chapterStatus?: string;
  /** 卡片命中时的卡片类型与标签。 */
  cardKind?: string;
  tags?: string[];
  updatedAt: string;
}

export interface CreationSearchView {
  query: string;
  hits: CreationSearchHit[];
  total: number;
}

export type ReplaceScope = "project" | "volume" | "chapter" | "scene";

export interface ReplacePreviewHit {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  sceneTitle: string;
  /** 该场景命中次数。 */
  count: number;
  /** 命中上下文示例（最多 5 条，按出现顺序）。 */
  snippets: string[];
}

export interface ReplacePreviewQuery {
  kind: "replace.preview";
  projectId: string;
  /** 查找文本（普通文本或受限正则，见 regex）。 */
  find: string;
  /** 替换文本（普通字符串；regex 模式下支持 $1 引用捕获组）。 */
  replaceWith: string;
  /** 替换范围：项目 / 卷 / 章 / 场景。 */
  scope: ReplaceScope;
  /** scope 为 volume/chapter/scene 时对应的实体 ID。 */
  scopeId?: string;
  /** 高级模式：把 find 视为受限正则（禁用 lookaround/backreference，长度 ≤ 200）。 */
  regex?: boolean;
  /** 预览场景数上限，默认 200，最大 1000。 */
  limit?: number;
}

export interface ReplacePreviewView {
  projectId: string;
  find: string;
  replaceWith: string;
  scope: ReplaceScope;
  sceneHits: ReplacePreviewHit[];
  /** 全部命中总数。 */
  totalHits: number;
  /** 命中场景数。 */
  matchedScenes: number;
}

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
// 切片：全项目替换计划（P1-F08 / P1-P07）seam 类型
// 跨越 IPC 边界（主进程 / 预加载 / 渲染端三套 tsc 均可见），故定义于本文件；
// 深层模块 replace-plan.ts 复用并 re-export。注意 scope 与既有 ReplaceScope
// （"project" | "volume" | "chapter" | "scene"）不同，这里独立为 ReplacePlanScope。
// ---------------------------------------------------------------------------

export type ReplacePlanScope = "all" | "chapter" | "scene";
export type ReplacePlanMode = "plain" | "regex";

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
// 切片 10：统计与创作目标
// ---------------------------------------------------------------------------

export interface ProjectWordCounts {
  /** 汉字数。 */
  han: number;
  /** 非空白字符数（含标点、字母、数字）。 */
  nonWhitespace: number;
  /** 含标点字符数（汉字 + 标点符号，不含字母数字）。 */
  withPunctuation: number;
}

export interface ProjectDailyStat {
  /** 本地日期 YYYY-MM-DD。 */
  date: string;
  /** 当日净增字符（非空白，可为负）。 */
  netChars: number;
  /** 当日活动秒数。 */
  activeSeconds: number;
}

export interface ProjectChapterStatusCount {
  status: string;
  count: number;
}

export interface ProjectStatsView {
  projectId: string;
  words: ProjectWordCounts;
  /** 活动会话时长（写入 sessions 的活动秒数）。 */
  sessionMinutes: {
    today: number;
    week: number;
    total: number;
  };
  /** 最近 14 天净增与活动时长（含今天）。 */
  daily: ProjectDailyStat[];
  /** 场景正文修订次数（保存 + 查找替换）。 */
  revisionCount: number;
  chapterStatusCounts: ProjectChapterStatusCount[];
  /** 命名快照数（里程碑）。 */
  snapshotCount: number;
  /** 连续写作天数（按有会话记录的天数，含今天）。 */
  streakDays: number;
}

export interface StatsViewQuery {
  kind: "stats.view";
  projectId: string;
}

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

/** 项目目标更新后的结果（返回最新 setup 供 UI 直接刷新）。 */
export interface ProjectGoalResult {
  projectId: string;
  setup: CreationProjectSetup;
}

/** 分层快照留存执行结果（system 受控，不给渲染端任何 reason）。 */
export interface SnapshotRetentionResult {
  keepIds: string[];
  deleteIds: string[];
  deletedCount: number;
}

export interface SessionEntry {
  id: string;
  projectId: string;
  sceneId: string | null;
  startedAt: string;
  activeSeconds: number;
  netChars: number;
  reportedAt: string;
}

export interface SessionListQuery {
  kind: "session.list";
  projectId: string;
  /** 返回上限，默认 100，最大 500。 */
  limit?: number;
}

// ---------------------------------------------------------------------------
// 切片 10：本地校对
// ---------------------------------------------------------------------------

export type ProofRule =
  | "repeatedChar"
  | "unbalancedPunctuation"
  | "abnormalSpacing"
  | "longParagraph"
  | "bannedWord"
  | "mixedPunctuation"
  | "crutchWord"
  | "paragraphStartRepeat";

export interface ProofIssue {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  sceneTitle: string;
  rule: ProofRule;
  /** 人类可读说明。 */
  message: string;
  /** 上下文片段（可能为 null）。 */
  snippet: string | null;
  /** 该场景该规则下的问题数。 */
  count: number;
}

export interface ProofQuery {
  kind: "proof.query";
  projectId: string;
  /** 限定单个场景；缺省检查项目全部场景。 */
  sceneId?: string;
  /** 启用的规则；缺省全部启用。 */
  rules?: ProofRule[];
  /** 用户自定义禁用词（子串匹配）。 */
  bannedWords?: string[];
  /** 超长段落阈值（字符数），默认 500，范围 100..5000。 */
  maxParagraphChars?: number;
  /** 问题数上限（场景×规则聚合后），默认 200，最大 2000。 */
  limit?: number;
}

export interface ProofView {
  projectId: string;
  issues: ProofIssue[];
  /** 检查的场景数。 */
  scannedScenes: number;
  /** 命中问题的场景数。 */
  affectedScenes: number;
  /** 问题总数（等于 issues 条数）。 */
  total: number;
}

// ---------------------------------------------------------------------------
// 切片 11：全局收件箱（旧灵感迁移目标）
// ---------------------------------------------------------------------------

export interface InboxItem {
  id: string;
  /** 旧数据迁移时保留的原始灵感 ID；手工创建的条目为 null。 */
  legacyId: string | null;
  title: string;
  body: string;
  type: string;
  status: string;
  tags: string[];
  platformTags: string[];
  /** 来源快照（原灵感 source 字段的松散结构）。 */
  source: Record<string, unknown> | null;
  /** 全部 AI 候选版本。 */
  variants: Array<Record<string, unknown>>;
  revision: number;
  createdAt: string;
  updatedAt: string;
}

export interface InboxListQuery {
  kind: "inbox.list";
  /** 返回上限，默认 100，最大 500。 */
  limit?: number;
  /** 分页偏移量，默认 0。迁移全量校验使用分页读取超过 500 条的收件箱。 */
  offset?: number;
}

/** 收件箱计数（不加载条目正文，供概览与项目首页共用）。 */
export interface InboxCountQuery {
  kind: "inbox.count";
}

export interface InboxCountView {
  /** 未删除条目总数。 */
  total: number;
  /** 未删除且状态不是 used（已转卡片）的待处理条目数。 */
  pending: number;
}

export interface InboxReadQuery {
  kind: "inbox.read";
  itemId: string;
}

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
// 切片 11：旧数据激活迁移
// ---------------------------------------------------------------------------

export interface LegacyMigrationActivation {
  formatVersion: 1;
  activatedAt: string;
  backupDirectory: string;
  reportPath: string;
  idMapPath: string;
  discovered: number;
  migrated: number;
  skipped: number;
  failed: number;
}

export interface LegacyMigrationReport {
  reportVersion: 1;
  generatedAt: string;
  activated: boolean;
  backup: {
    directory: string;
    manifestVersion: number;
    manifestPath: string;
    files: number;
    bytes: number;
    checksumVerified: boolean;
  };
  sources: {
    discovered: number;
    migrated: number;
    skipped: number;
    failed: number;
  };
  failures: Array<{ legacyId: string; reason: string; retryable: boolean }>;
  idMap: {
    path: string;
    entries: number;
  };
  targetStore: {
    directory: string;
    integrityOk: boolean;
    schemaVersion: number;
    wasFresh: boolean;
  };
  rollback: {
    how: string;
    backupDirectory: string;
  };
}

export interface LegacyMigrationStatus {
  activated: boolean;
  activation: LegacyMigrationActivation | null;
  report: LegacyMigrationReport | null;
  canProceed: boolean;
  /** 审计阻断原因（canProceed=false 时的可读说明）。 */
  blockingReasons: string[];
}

export interface SessionReportResult {
  commandType: "session.report" | "session.delete" | "session.update";
  sequence: number;
  projectId: string;
  sessionId: string;
  updatedAt: string;
}
