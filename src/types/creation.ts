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
