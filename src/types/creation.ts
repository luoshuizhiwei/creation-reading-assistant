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
