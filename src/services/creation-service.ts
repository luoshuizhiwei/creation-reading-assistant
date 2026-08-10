import { getDesktopApi } from "@/services/ipc-client";
import type {
  CardCommand,
  CardRelation,
  CardSummary,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectListener,
  CreationProjectSummary,
  CreationSearchQuery,
  CreationSearchView,
  CreationStructureResult,
  HistoryCommand,
  ProjectStatsView,
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
  StructureCommand,
  TrashItem,
  UpdateSceneBodyInput,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxListQuery,
  InboxUpdateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  DraftImportPreview,
  ProjectImportDraftCommand,
  ProjectBundleImportResult
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

export async function readProjectNavigation(projectId: string): Promise<CreationProjectNavigation | null> {
  return getCreationApi().readProjectNavigation(projectId);
}

export async function readProjectOutline(projectId: string): Promise<CreationProjectOutline | null> {
  return getCreationApi().readProjectOutline(projectId);
}

export async function createProject(input: CreateProjectInput): Promise<CreationProjectNavigation> {
  return getCreationApi().createProject(input);
}

export async function runStructure(
  command: StructureCommand | CardCommand | HistoryCommand | ProjectImportDraftCommand
): Promise<CreationStructureResult> {
  return getCreationApi().runStructure(command);
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

export async function migrationStatus(): Promise<LegacyMigrationStatus | null> {
  return getCreationApi().migrationStatus();
}

export async function migrationRun(): Promise<LegacyMigrationReport> {
  return getCreationApi().migrationRun();
}

export async function inboxList(query: InboxListQuery): Promise<InboxItem[]> {
  return getCreationApi().inboxList(query);
}

export async function inboxUpdate(command: InboxUpdateCommand): Promise<InboxItemResult> {
  return getCreationApi().inboxUpdate(command);
}

export async function inboxDelete(command: InboxDeleteCommand): Promise<InboxItemResult> {
  return getCreationApi().inboxDelete(command);
}
