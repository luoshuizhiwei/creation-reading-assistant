import type {
  CreationChapter,
  CreationDocument,
  CreationNavigationChapter,
  CreationNavigationScene,
  CreationProject,
  CreationProjectNavigation,
  CreationProjectSetup,
  CreationProjectSummary,
  CreationProjectTemplate,
  CreationProjectTree,
  CreateProjectInput,
  CreationWorkspaceErrorCode,
  CreationWorkspaceEvent,
  SceneBodyView,
  UpdateSceneBodyInput,
  UpdateSceneBodyResult
} from "../../../src/types/creation";

export type {
  CreationChapter,
  CreationDocument,
  CreationNavigationChapter,
  CreationNavigationScene,
  CreationProject,
  CreationProjectNavigation,
  CreationProjectSetup,
  CreationProjectSummary,
  CreationProjectTemplate,
  CreationProjectTree,
  CreateProjectInput,
  CreationWorkspaceErrorCode,
  CreationWorkspaceEvent,
  SceneBodyView,
  UpdateSceneBodyInput,
  UpdateSceneBodyResult
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
    chapters: number;
    scenes: number;
    cards: number;
    relations: number;
    resources: number;
    snapshots: number;
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
  | ReadSceneBodyQuery
  | ListProjectsQuery;
export type CreationReadResult =
  | CreationProjectTree
  | CreationProjectNavigation
  | SceneBodyView
  | CreationProjectSummary[]
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

export type CreationCommand = CreateProjectCommand | UpdateSceneBodyCommand;

export interface CreateProjectResult {
  commandType: "project.create";
  sequence: number;
  projectId: string;
  chapterId: string;
  sceneId: string;
}

export type CreationTransactionResult = CreateProjectResult | UpdateSceneBodyResult;

export interface CreationWatchScope {
  projectId?: string;
}

export type CreationWorkspaceListener = (event: CreationWorkspaceEvent) => void;

export interface CreationWorkspace {
  read(query: ReadProjectTreeQuery): Promise<CreationProjectTree | null>;
  read(query: ReadProjectNavigationQuery): Promise<CreationProjectNavigation | null>;
  read(query: ReadSceneBodyQuery): Promise<SceneBodyView | null>;
  read(query: ListProjectsQuery): Promise<CreationProjectSummary[]>;
  read(query: CreationReadQuery): Promise<CreationReadResult>;
  transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
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
