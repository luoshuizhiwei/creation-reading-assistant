import type {
  CreationProjectSetup,
  CreationDocument,
  ChapterNumberingKind,
  CardFieldSchema,
  TrashEntityKind,
  SnapshotSubjectType,
  ScenePlanning,
  SceneStatus,
  DraftImportFormat,
  DraftExportPreset
} from "./primitives";

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
  /** v11 场景摘要；可选仅用于兼容旧 renderer 测试夹具，数据库读取始终提供。 */
  summary?: string;
  /** v11 场景状态；不得与 CreationOutlineChapter.status 混用。 */
  status?: SceneStatus;
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
  /** 由未删除场景 non_ws_count 实时汇总，不持久化冗余总数。 */
  wordCount?: number;
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
  /** 由卷内章节实时汇总。 */
  wordCount?: number;
  chapters: CreationOutlineChapter[];
}

export interface CreationProjectOutline {
  project: CreationProject;
  volumes: CreationOutlineVolume[];
  /** 未分卷章节（无卷项目的兼容路径，正常迁移后为空）。 */
  looseChapters: CreationOutlineChapter[];
  /** 全书实时汇总字数。 */
  wordCount?: number;
}

export interface CardType {
  id: string;
  /** 内置类型全局只读；自定义类型同样属于全局卡片库。 */
  builtIn: boolean;
  /** 仅用于兼容 v9 来源追踪，不代表类型归项目所有。 */
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
  /** 内置关系类型全局只读；自定义关系类型同样属于全局卡片库。 */
  builtIn: boolean;
  /** 仅用于兼容 v9 来源追踪，不代表关系类型归项目所有。 */
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
  /** 当前项目投影；全局卡片库读取时为 null，不表示所有权。 */
  projectId: string | null;
  /** 当前关联此卡片的全部项目稳定 ID。 */
  linkedProjectIds: string[];
  usageCount: number;
  kind: string;
  title: string;
  aliases: string[];
  fields: Record<string, unknown>;
  tags: string[];
  createdAt: string;
  updatedAt: string;
  revision: number;
  /**
   * 封面资源 ID（`global_card_resources.id`，`role = 'cover'`）；无封面时为 null。
   *
   * 用于在卡片列表/网格里直接拼出 `creation-asset://card/<id>/<resourceId>` 缩略图，
   * 避免为每张卡片单独查一次资源（N+1）。数据库层已有
   * `idx_global_card_resources_cover` 唯一索引，一张卡最多一个封面。
   * 旧库（无 `global_card_resources` 表）恒为 null。
   */
  coverResourceId?: string | null;
}

export interface CardRelation {
  id: string;
  /** v9 来源项目，仅作兼容追踪；关系本体是全局资产。 */
  projectId: string | null;
  fromCardId: string;
  toCardId: string;
  relationTypeId: string;
  forwardName: string;
  note: string | null;
  createdAt: string;
}

export interface TrashItem {
  entity: TrashEntityKind;
  id: string;
  projectId: string | null;
  title: string;
  deletedAt: string;
  revision: number;
}

export interface SnapshotInfo {
  id: string;
  projectId: string;
  subjectType: SnapshotSubjectType;
  subjectId: string;
  reason: string;
  createdAt: string;
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

export interface ResourceInfo {
  id: string;
  /** 旧项目附件所属项目；全局卡片资产为 null。 */
  projectId: string | null;
  cardId: string | null;
  /** project 为兼容项目附件，card 为全局卡片资产。 */
  ownerScope?: "project" | "card";
  /** 全局卡片资产角色；旧项目资源默认为 attachment。 */
  role?: "attachment" | "cover";
  /** 工作区 resources 目录内的相对路径（项目包可移植）。 */
  relativePath: string;
  sha256: string;
  size: number;
  originalName: string | null;
  createdAt: string;
}

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

export interface ProjectBundleResourceFile {
  /** 项目包内资源相对路径（resources/**）。 */
  relativePath: string;
  /** 导入后工作区内的新相对路径（resources/<新项目ID>/<文件名>）。 */
  targetRelativePath: string;
  sha256: string;
  size: number;
  /** 复用本机全局卡片时不复制其包内全局资源。 */
  skip?: boolean;
}

export type ProjectBundleCardResolutionAction = "reuse" | "keep-local" | "import-copy";

export interface ProjectBundleCardResolution {
  cardId: string;
  action: ProjectBundleCardResolutionAction;
  /** import-copy 时由主进程预先生成，文件层和数据库层共用。 */
  targetCardId?: string;
}

export interface ProjectBundleCardConflict {
  cardId: string;
  localTitle: string;
  importedTitle: string;
  localDeleted: boolean;
  differingFields: string[];
}

export interface ProjectBundleImportPreview {
  projectTitle: string;
  cardCount: number;
  identicalCardIds: string[];
  conflicts: ProjectBundleCardConflict[];
}

export interface ProjectBundleCardMapping {
  sourceCardId: string;
  targetCardId: string;
  action: "created" | "reused" | "kept-local" | "copied";
}

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
    /** v11；可选以兼容 formatVersion=2 的既有项目包。 */
    summary?: string;
    /** v11；可选以兼容 formatVersion=2 的既有项目包。 */
    status?: SceneStatus;
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
    /** v2 兼容字段；card 表示来自全局卡片资产域。 */
    ownerScope?: "project" | "card";
    role?: "attachment" | "cover";
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

export interface ProjectExportScene {
  id: string;
  title: string;
  summary?: string;
  status?: SceneStatus;
  wordCount?: number;
  targetWords?: number | null;
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
  status?: string;
  wordCount?: number;
  scenes: ProjectExportScene[];
}

export interface ProjectExportVolume {
  id: string;
  title: string;
  wordCount?: number;
  chapters: ProjectExportChapter[];
}

export interface ProjectExportView {
  projectId: string;
  title: string;
  wordCount?: number;
  volumes: ProjectExportVolume[];
}

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

/**
 * 场景状态分布。`status` 一律取自 `scenes.scene_status`，与 `chapters.status` 的章节工作流状态
 * 是两种不同语义，不可互相复用或互相替代。
 */
export interface ProjectSceneStatusCount {
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
  /** 最近 30 天净增与活动时长（含今天）。 */
  daily: ProjectDailyStat[];
  /** 场景正文修订次数（保存 + 查找替换）。 */
  revisionCount: number;
  chapterStatusCounts: ProjectChapterStatusCount[];
  /** 场景状态分布，取自 `scenes.scene_status`；不含已删除场景与已删除章节下的场景。 */
  sceneStatusCounts: ProjectSceneStatusCount[];
  /** 命名快照数（里程碑）。 */
  snapshotCount: number;
  /** 连续写作天数（按有会话记录的天数，含今天）。 */
  streakDays: number;
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

export interface DraftImportPreviewChapter {
  title: string;
  /** 章节正文纯文本。 */
  body: string;
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

/** 分层快照留存执行结果（system 受控，不给渲染端任何 reason）。 */
export interface SnapshotRetentionResult {
  keepIds: string[];
  deleteIds: string[];
  deletedCount: number;
}

/** 项目目标更新后的结果（返回最新 setup 供 UI 直接刷新）。 */
export interface ProjectGoalResult {
  projectId: string;
  setup: CreationProjectSetup;
}

export interface DraftExportBuildResult {
  preset: DraftExportPreset;
  /** 目标文件扩展名（不带点）。 */
  extension: "txt" | "md";
  text: string;
}
