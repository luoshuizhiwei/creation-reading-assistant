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

export interface RelationTypeCreateCommand {
  type: "relationType.create";
  projectId: string;
  forwardName: string;
  reverseName: string;
  fromKinds?: string[];
  toKinds?: string[];
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
  | RelationTypeCreateCommand
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

export type SnapshotSubjectType = "scene" | "card";

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

export interface SnapshotRestoreCommand {
  type: "snapshot.restore";
  projectId: string;
  snapshotId: string;
}

export interface SnapshotListQuery {
  kind: "snapshot.list";
  projectId: string;
  subjectType?: SnapshotSubjectType;
  subjectId?: string;
}

export type HistoryCommand =
  | TrashRestoreCommand
  | TrashPurgeCommand
  | SnapshotCreateCommand
  | SnapshotRestoreCommand;

// ---------------------------------------------------------------------------
// 切片 9：成稿导出
// ---------------------------------------------------------------------------

export interface ProjectExportScene {
  id: string;
  title: string;
  /** 场景正文纯文本（块间空行、场景分隔换行）。 */
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

export type DraftImportFormat = "txt" | "markdown";

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
  formatVersion: 1;
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
  counts: {
    volumes: number;
    chapters: number;
    scenes: number;
    cards: number;
    relations: number;
    snapshots: number;
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
}

export interface ProjectBundleImportResult {
  commandType: "project.bundle.import";
  sequence: number;
  projectId: string;
  counts: ProjectBundleData["counts"];
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
  | "bannedWord";

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
  status?: string;
  tags?: string[];
  platformTags?: string[];
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
  commandType: "session.report" | "session.delete";
  sequence: number;
  projectId: string;
  sessionId: string;
  updatedAt: string;
}
