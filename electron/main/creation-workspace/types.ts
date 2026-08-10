import type {
  ChapterNumberingKind,
  ChapterCreateCommand,
  ChapterDeleteCommand,
  ChapterMoveCommand,
  ChapterRenameCommand,
  ChapterReorderCommand,
  ChapterSetNumberingCommand,
  ChapterSetStatusCommand,
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
  VolumeReorderCommand
} from "../../../src/types/creation";

export type {
  ChapterNumberingKind,
  ChapterCreateCommand,
  ChapterDeleteCommand,
  ChapterMoveCommand,
  ChapterRenameCommand,
  ChapterReorderCommand,
  ChapterSetNumberingCommand,
  ChapterSetStatusCommand,
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
  VolumeReorderCommand
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
  | ListProjectsQuery;
export type CreationReadResult =
  | CreationProjectTree
  | CreationProjectNavigation
  | CreationProjectOutline
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

export type CreationCommand = CreateProjectCommand | UpdateSceneBodyCommand | StructureCommand;

export interface CreateProjectResult {
  commandType: "project.create";
  sequence: number;
  projectId: string;
  volumeId: string;
  chapterId: string;
  sceneId: string;
}

export type CreationTransactionResult = CreateProjectResult | UpdateSceneBodyResult | CreationStructureResult;

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
  read(query: CreationReadQuery): Promise<CreationReadResult>;
  transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  transact(command: StructureCommand): Promise<CreationStructureResult>;
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
