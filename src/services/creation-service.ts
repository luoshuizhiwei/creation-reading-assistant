import { getDesktopApi } from "@/services/ipc-client";
import type {
  CardRelation,
  CardSummary,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectListener,
  CreationProjectSummary,
  CreationRunCommand,
  CreationRunResultOf,
  CreationSearchQuery,
  CreationSearchView,
  RelationType,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewQuery,
  ReplacePreviewView,
  SceneBodyView,
  SceneSaveResponse,
  SessionDeleteCommand,
  SessionEntry,
  SessionListQuery,
  SessionReportCommand,
  SessionReportResult,
  SnapshotInfo,
  SnapshotListQuery,
  ProjectStatsView,
  TrashItem,
  UpdateSceneBodyInput,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxCountView,
  InboxUpdateCommand,
  InboxCreateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  DraftImportPreview,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  ProjectExportView,
  ProjectHomeView,
  StructurePreviewCommand,
  StructureApplyWithProtectionCommand,
  StructureRevertCommand,
  StructurePreviewView,
  StructureApplyResult,
  StructureRevertResult
} from "@/types/creation";

function getCreationApi() {
  const api = getDesktopApi().creation;
  if (!api) {
    throw new Error("创作项目数据接口尚未就绪。");
  }
  return api;
}

export async function listProjects(): Promise<CreationProjectSummary[]> {
  return getCreationApi().listProjects();
}

export async function readProjectHome(): Promise<ProjectHomeView> {
  return getCreationApi().readProjectHome();
}

export async function readProjectNavigation(projectId: string): Promise<CreationProjectNavigation | null> {
  return getCreationApi().readProjectNavigation(projectId);
}

export async function readProjectOutline(projectId: string): Promise<CreationProjectOutline | null> {
  return getCreationApi().readProjectOutline(projectId);
}

export async function createProject(input: CreateProjectInput): Promise<CreationProjectNavigation> {
  return getCreationApi().createProject(input);
}

export async function runStructure<Command extends CreationRunCommand>(
  command: Command
): Promise<CreationRunResultOf<Command>> {
  return getCreationApi().runStructure(command);
}

export async function structurePreview(command: StructurePreviewCommand): Promise<StructurePreviewView> {
  return getCreationApi().structurePreview(command);
}

export async function structureApply(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult> {
  return getCreationApi().structureApply(command);
}

export async function structureRevert(command: StructureRevertCommand): Promise<StructureRevertResult> {
  return getCreationApi().structureRevert(command);
}

export async function trashList(projectId: string): Promise<TrashItem[]> {
  return getCreationApi().trashList(projectId);
}

export async function snapshotList(query: SnapshotListQuery): Promise<SnapshotInfo[]> {
  return getCreationApi().snapshotList(query);
}

export async function exportDraft(
  projectId: string
): Promise<{ canceled: boolean; filePath: string | null }> {
  return getCreationApi().exportDraft(projectId);
}

export async function cardsList(query: CardsListQuery): Promise<CardSummary[]> {
  return getCreationApi().cardsList(query);
}

export async function cardRead(cardId: string): Promise<CardSummary | null> {
  return getCreationApi().cardRead(cardId);
}

export async function cardTypesList(projectId: string): Promise<CardType[]> {
  return getCreationApi().cardTypesList(projectId);
}

export async function relationTypesList(projectId: string): Promise<RelationType[]> {
  return getCreationApi().relationTypesList(projectId);
}

export async function cardRelations(
  cardId: string
): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }> {
  return getCreationApi().cardRelations(cardId);
}

export async function readSceneBody(sceneId: string): Promise<SceneBodyView | null> {
  return getCreationApi().readSceneBody(sceneId);
}

export async function updateSceneBody(input: UpdateSceneBodyInput): Promise<SceneSaveResponse> {
  return getCreationApi().updateSceneBody(input);
}

export async function watchProject(
  projectId: string,
  onEvent: CreationProjectListener
): Promise<() => void> {
  return getCreationApi().watchProject(projectId, onEvent);
}

export async function search(query: CreationSearchQuery): Promise<CreationSearchView> {
  return getCreationApi().search(query);
}

export async function replacePreview(query: ReplacePreviewQuery): Promise<ReplacePreviewView> {
  return getCreationApi().replacePreview(query);
}

export async function replaceApply(command: ReplaceApplyCommand): Promise<ReplaceApplyResult> {
  return getCreationApi().replaceApply(command);
}

export async function statsView(projectId: string): Promise<ProjectStatsView | null> {
  return getCreationApi().statsView(projectId);
}

export async function sessionList(query: SessionListQuery): Promise<SessionEntry[]> {
  return getCreationApi().sessionList(query);
}

export async function sessionReport(command: SessionReportCommand): Promise<SessionReportResult> {
  return getCreationApi().sessionReport(command);
}

export async function sessionDelete(command: SessionDeleteCommand): Promise<SessionReportResult> {
  return getCreationApi().sessionDelete(command);
}

export async function proofQuery(query: ProofQuery): Promise<ProofView> {
  return getCreationApi().proofQuery(query);
}

export async function importDraftPreview(): Promise<DraftImportPreview | null> {
  return getCreationApi().importDraftPreview();
}

export async function exportProjectBundle(
  projectId: string
): Promise<{ canceled: boolean; directory: string | null }> {
  return getCreationApi().exportProjectBundle(projectId);
}

export async function importProjectBundle(): Promise<{ canceled: boolean; result: ProjectBundleImportResult | null }> {
  return getCreationApi().importProjectBundle();
}

export async function annotationList(query: AnnotationListQuery): Promise<Annotation[]> {
  return getCreationApi().annotationList(query);
}

export async function annotationCreate(command: AnnotationCreateCommand): Promise<AnnotationResult> {
  return getCreationApi().annotationCreate(command);
}

export async function annotationUpdate(command: AnnotationUpdateCommand): Promise<AnnotationResult> {
  return getCreationApi().annotationUpdate(command);
}

export async function annotationDelete(command: AnnotationDeleteCommand): Promise<AnnotationResult> {
  return getCreationApi().annotationDelete(command);
}

export async function resourceList(query: ResourceListQuery): Promise<ResourceInfo[]> {
  return getCreationApi().resourceList(query);
}

export async function attachResource(
  projectId: string,
  cardId?: string
): Promise<{ canceled: boolean; resource: ResourceResult | null }> {
  return getCreationApi().attachResource(projectId, cardId);
}

export async function detachResource(resourceId: string): Promise<ResourceResult> {
  return getCreationApi().detachResource(resourceId);
}

export async function projectExport(projectId: string): Promise<ProjectExportView | null> {
  return getCreationApi().readProjectExport(projectId);
}

export async function migrationStatus(): Promise<LegacyMigrationStatus | null> {
  return getCreationApi().migrationStatus();
}

export async function migrationRun(): Promise<LegacyMigrationReport> {
  return getCreationApi().migrationRun();
}

export async function inboxList(query: InboxListQuery): Promise<InboxItem[]> {
  return getCreationApi().inboxList(query);
}

export async function inboxCount(): Promise<InboxCountView> {
  return getCreationApi().inboxCount();
}

export async function inboxUpdate(command: InboxUpdateCommand): Promise<InboxItemResult> {
  return getCreationApi().inboxUpdate(command);
}

export async function inboxDelete(command: InboxDeleteCommand): Promise<InboxItemResult> {
  return getCreationApi().inboxDelete(command);
}

export async function inboxCreate(command: InboxCreateCommand): Promise<InboxItemResult> {
  return getCreationApi().inboxCreate(command);
}
