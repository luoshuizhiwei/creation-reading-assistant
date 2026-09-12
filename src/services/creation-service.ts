import { getDesktopApi } from "@/services/ipc-client";
import type {
  CardRelation,
  CardSummary,
  CardLinkResult,
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
  RelationGraphQuery,
  RelationGraphView,
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
  ProofIgnoreCommand,
  ProofIgnoreEntry,
  ProofIgnoreListQuery,
  ProofIgnoreResult,
  ProofUnignoreCommand,
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
  DraftExportPreset,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationReanchorCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotRestoreWithProtectionCommand,
  SnapshotRestoreWithProtectionResult,
  TrashImpactQuery,
  TrashImpactView,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  ProjectExportView,
  ProjectPrintMode,
  ProjectPrintResult,
  ProjectHomeView,
  StructurePreviewCommand,
  StructureApplyWithProtectionCommand,
  StructureRevertCommand,
  StructurePreviewView,
  StructureApplyResult,
  StructureRevertResult,
  SessionUpdateCommand,
  ProjectUpdateGoalCommand,
  ProjectGoalResult,
  SnapshotRetentionResult,
  ReplacePlanQuery,
  ReplacePlan,
  ReplaceApplyOutcome
} from "@/types/creation";
import type {
  CardExportFilter,
  CardExportResult,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext,
  CardImportSource
} from "@/types/card-io";

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

export async function trashList(projectId?: string): Promise<TrashItem[]> {
  return getCreationApi().trashList(projectId);
}

export async function snapshotList(query: SnapshotListQuery): Promise<SnapshotInfo[]> {
  return getCreationApi().snapshotList(query);
}

export async function exportDraft(
  projectId: string,
  preset: DraftExportPreset
): Promise<{ canceled: boolean; filePath: string | null }> {
  return getCreationApi().exportDraft(projectId, preset);
}

export async function cardsList(query: CardsListQuery): Promise<CardSummary[]> {
  return getCreationApi().cardsList(query);
}

export async function cardRead(cardId: string): Promise<CardSummary | null> {
  return getCreationApi().cardRead(cardId);
}

export async function cardTypesList(): Promise<CardType[]> {
  return getCreationApi().cardTypesList();
}

export async function relationTypesList(): Promise<RelationType[]> {
  return getCreationApi().relationTypesList();
}

export async function cardLink(projectId: string, cardId: string): Promise<CardLinkResult> {
  return getCreationApi().cardLink(projectId, cardId);
}

export async function cardUnlink(projectId: string, cardId: string): Promise<CardLinkResult> {
  return getCreationApi().cardUnlink(projectId, cardId);
}

export async function relationGraph(query: RelationGraphQuery): Promise<RelationGraphView> {
  return getCreationApi().relationGraph(query);
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

export async function proofIgnoreList(query: ProofIgnoreListQuery): Promise<ProofIgnoreEntry[]> {
  return getCreationApi().proofIgnoreList(query);
}

export async function proofIgnore(
  command: Omit<ProofIgnoreCommand, "type">
): Promise<ProofIgnoreResult> {
  return getCreationApi().runStructure({ type: "proof.ignore", ...command });
}

export async function proofUnignore(
  command: Omit<ProofUnignoreCommand, "type">
): Promise<ProofIgnoreResult> {
  return getCreationApi().runStructure({ type: "proof.unignore", ...command });
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

export async function annotationReanchor(command: AnnotationReanchorCommand): Promise<AnnotationResult> {
  return getCreationApi().annotationReanchor(command);
}

export async function snapshotPreview(query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null> {
  return getCreationApi().snapshotPreview(query);
}

export async function snapshotRestoreWithProtection(
  command: SnapshotRestoreWithProtectionCommand
): Promise<SnapshotRestoreWithProtectionResult> {
  return getCreationApi().snapshotRestoreWithProtection(command);
}

export async function trashImpact(query: TrashImpactQuery): Promise<TrashImpactView | null> {
  return getCreationApi().trashImpact(query);
}

export async function resourceList(query: ResourceListQuery): Promise<ResourceInfo[]> {
  return getCreationApi().resourceList(query);
}

export async function attachResource(
  projectId: string | undefined,
  cardId?: string,
  role?: "attachment" | "cover"
): Promise<{ canceled: boolean; resource: ResourceResult | null }> {
  return getCreationApi().attachResource(projectId, cardId, role);
}

export async function detachResource(resourceId: string): Promise<ResourceResult> {
  return getCreationApi().detachResource(resourceId);
}

export async function projectExport(projectId: string): Promise<ProjectExportView | null> {
  return getCreationApi().readProjectExport(projectId);
}

/** 全书只读预览：与成稿导出同源，但固定要求块级视图以便按块类型排版。 */
export async function projectPreview(projectId: string): Promise<ProjectExportView | null> {
  return getCreationApi().readProjectPreview(projectId);
}

export async function printProject(projectId: string, mode: ProjectPrintMode): Promise<ProjectPrintResult> {
  return getCreationApi().printProject(projectId, mode);
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

// ---- Phase 1 P1 深模块 seam 接入 ----

export async function snapshotRetentionRun(): Promise<SnapshotRetentionResult> {
  return getCreationApi().snapshotRetentionRun();
}

export async function cardImportOpenAndParse(): Promise<CardImportSource | null> {
  return getCreationApi().cardImportOpenAndParse();
}

export async function cardImportParse(input: {
  text: string;
  format: "csv" | "markdown";
}): Promise<CardImportPreview> {
  return getCreationApi().cardImportParse(input);
}

export async function cardImportSchema(projectId: string): Promise<CardImportSchemaContext> {
  return getCreationApi().cardImportSchema(projectId);
}

export async function cardImportPlan(input: CardImportApplyInput): Promise<CardImportPlan> {
  return getCreationApi().cardImportPlan(input);
}

export async function cardImportApply(input: CardImportApplyInput): Promise<CardImportApplyResult> {
  return getCreationApi().cardImportApply(input);
}

export async function cardExportOpenAndWrite(input: {
  projectId: string;
  filter: CardExportFilter;
  format: "csv" | "markdown";
}): Promise<CardExportResult> {
  return getCreationApi().cardExportOpenAndWrite(input);
}

export async function replacePlanCreate(query: ReplacePlanQuery): Promise<ReplacePlan> {
  return getCreationApi().replacePlanCreate(query);
}

export async function replacePlanApply(input: {
  planId: string;
  excludedHitIds: string[];
}): Promise<ReplaceApplyOutcome> {
  return getCreationApi().replacePlanApply(input);
}

export async function sessionUpdate(command: SessionUpdateCommand): Promise<SessionReportResult> {
  return getCreationApi().sessionUpdate(command);
}

export async function projectUpdateGoal(command: ProjectUpdateGoalCommand): Promise<ProjectGoalResult> {
  return getCreationApi().projectUpdateGoal(command);
}
