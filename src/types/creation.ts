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
  | SceneCreateCommand
  | SceneRenameCommand
  | SceneReorderCommand
  | SceneMoveCommand
  | SceneDeleteCommand;
