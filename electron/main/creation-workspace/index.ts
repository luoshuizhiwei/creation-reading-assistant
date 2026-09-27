import { mkdir } from "node:fs/promises";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type CreationCommand,
  type CreationIntegrityReport,
  type CreationProjectOutline,
  type CreationReadQuery,
  type CreationReadResult,
  type CreationStructureResult,
  type ListProjectsQuery,
  type CreationProjectSummary,
  type CreationProjectNavigation,
  type CreationProjectTree,
  type CreationTransactionResult,
  type ReadProjectNavigationQuery,
  type ReadProjectOutlineQuery,
  type ReadProjectTreeQuery,
  type ReadSceneBodyQuery,
  type SceneBodyView,
  type CreateProjectCommand,
  type CreateProjectResult,
  type StructureCommand,
  type StructurePreviewCommand,
  type StructureApplyWithProtectionCommand,
  type StructureRevertCommand,
  type StructurePreviewView,
  type StructureApplyResult,
  type StructureRevertResult,
  type UpdateSceneBodyCommand,
  type UpdateSceneBodyResult,
  type CardCommand,
  type CardLinkResult,
  type CardReadQuery,
  type CardRelation,
  type CardRelationsQuery,
  type CardSummary,
  type CardType,
  type CardTypesListQuery,
  type CardsListQuery,
  type RelationType,
  type RelationTypesListQuery,
  type HistoryCommand,
  type SnapshotInfo,
  type SnapshotListQuery,
   type SnapshotPreviewQuery,
   type SnapshotPreviewView,
   type SnapshotRestoreWithProtectionCommand,
   type SnapshotRestoreWithProtectionResult,
  type TrashItem,
  type TrashListQuery,
   type TrashImpactQuery,
   type TrashImpactView,
  type ProjectExportQuery,
  type ProjectExportView,
  type CreationSearchQuery,
  type CreationSearchView,
  type ReplaceApplyCommand,
  type ReplaceApplyResult,
  type ReplacePreviewQuery,
  type ReplacePreviewView,
  type ProjectStatsView,
  type SessionDeleteCommand,
  type SessionEntry,
  type SessionListQuery,
  type SessionReportCommand,
  type SessionReportResult,
  type StatsViewQuery,
  type ProofQuery,
  type ProofView,
  type ProofIgnoreCommand,
  type ProofIgnoreEntry,
  type ProofIgnoreListQuery,
  type ProofIgnoreResult,
  type ProofUnignoreCommand,
  type RelationGraphQuery,
  type RelationGraphView,
  type InboxCreateCommand,
  type InboxDeleteCommand,
  type InboxItem,
  type InboxItemResult,
  type InboxListQuery,
  type InboxCountQuery,
  type InboxCountView,
  type InboxReadQuery,
  type InboxUpdateCommand,
  type ProjectImportDraftCommand,
  type ProjectImportDraftResult,
  type ProjectBundleData,
  type ProjectBundleImportPreview,
  type ProjectBundleExportQuery,
  type ProjectBundleImportCommand,
  type ProjectBundleImportResult,
  type Annotation,
  type AnnotationCreateCommand,
  type AnnotationDeleteCommand,
  type AnnotationListQuery,
  type AnnotationResult,
   type AnnotationUpdateCommand,
   type AnnotationReanchorCommand,
  type ResourceAttachCommand,
  type ResourceDetachCommand,
  type ResourceInfo,
  type ResourceListQuery,
   type ResourceResult,
   type SceneUpdatePlanningCommand,
   type SceneUpdatePlanningResult,
   type SceneUpdateMetaCommand,
   type SceneUpdateMetaResult,
   type CreationRunCommand,
   type CreationRunResultOf,
   type InboxConvertToCardCommand,
   type InboxConvertToCardResult,
   type ProjectHomeQuery,
   type ProjectHomeView,
   type CreationWatchScope,
  type CreationWorkspace,
  type CreationWorkspaceEvent,
  type CreationWorkspaceListener,
  type OpenCreationWorkspaceOptions
} from "./types";
import { createStatsSessionsModule, type StatsSessionsModule } from "./stats-sessions";
import { createSearchModule, type SearchModule } from "./search";
import { createInboxModule, type InboxModule } from "./inbox";
import { createResourceModule, type ResourceModule } from "./resource";
import { createAnnotationModule, type AnnotationModule } from "./annotation";
import { createReplaceModule, type ReplaceModule } from "./replace";
import {
  createGlobalCardSchemaRepairBackup,
  createV9MigrationBackup,
  GLOBAL_CARD_SCHEMA_VERSION,
  migrateGlobalCardsV9ToV10,
  needsGlobalCardSchemaRepair,
  repairGlobalCardSchemaV10
} from "./global-card-migration";
import { createStructureModule, type StructureModule, STRUCTURE_COMMAND_TYPES } from "./structure";
import { createBundleIoModule, type BundleIoModule } from "./bundle-io";
import { createProofModule, type ProofModule } from "./proof";
import { createTrashModule, type TrashModule } from "./trash";
import { createSnapshotModule, type SnapshotModule } from "./snapshot";
import { createProjectViewsModule, type ProjectViewsModule } from "./project-views";
import { createCardsModule, type CardsModule } from "./cards";
import { createSceneWriteModule, type SceneWriteModule } from "./scene-write";
import { createProjectWriteModule, type ProjectWriteModule } from "./project-write";
import { createIntegrityModule, type IntegrityModule } from "./integrity";
import { createReplaceStoreModule, type ReplaceStoreModule } from "./replace-store";
import {
  isConstraintError,
  validateId,
} from "./workspace-utils";
import {
  parseStoredSetup
} from "./project-setup";
import {
  drainGlobalCardResourceGc,
  ensureGlobalCardResourceSchema,
  ensureProofIgnoreSchema,
  initializeSchema,
  migrateSchemaV1ToV2,
  migrateSchemaV10ToV11,
  migrateSchemaV11ToV12,
  migrateSchemaV2ToV3,
  migrateSchemaV3ToV4,
  migrateSchemaV4ToV5,
  migrateSchemaV5ToV6,
  migrateSchemaV6ToV7,
  migrateSchemaV7ToV8,
  migrateSchemaV8ToV9,
  purgeExpiredTrash,
  SCHEMA_VERSION
} from "./schema";
import {
} from "./validate";
import { applyReplacePlan as runApplyReplacePlan, createReplacePlan as runCreateReplacePlan } from "./replace-plan";
import {
  applyCardImportPlan,
  generateCardImportPlanId,
  planCardImport,
  readCardImportSchemaContext,
  readCardsForExport
} from "../creation-card-io/card-io-index";
import type {
  CardExportFilter,
  CardExportRow,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportSchemaContext
} from "../../../src/types/card-io";
import type {
  ProjectGoalResult,
  ProjectUpdateGoalCommand,
  SessionUpdateCommand,
  SnapshotRetentionResult
} from "../../../src/types/creation";
import type {
  ReplaceApplyOutcome,
  ReplacePlan,
  ReplacePlanController,
  ReplacePlanQuery,
} from "./replace-plan";

/**
 * schema 版本常量的权威实现在 ./schema（完整性检查与迁移链共用）。
 * 这里 re-export 保持历史导入路径（各 *-contract.ts 从 ./index 取值）不变。
 */
export { SCHEMA_VERSION } from "./schema";
/** v11：场景摘要与场景状态独立持久化。保留为独立常量，避免迁移链出现「魔术版本号」。 */
const SCENE_META_SCHEMA_VERSION = 11;


const CARD_COMMAND_TYPES = new Set<string>([
  "cardType.create",
  "cardType.update",
  "cardType.delete",
  "relationType.create",
  "relationType.update",
  "relationType.delete",
  "card.create",
  "card.update",
  "card.delete",
  "card.link",
  "card.unlink",
  "cardRelation.create",
  "cardRelation.delete"
]);

const HISTORY_COMMAND_TYPES = new Set<string>([
  "trash.restore",
  "trash.purge",
  "snapshot.create"
]);

/**
 * 场景正文三口径统计的权威实现在 ./scene-stats（workspace 与视觉 seed 共用，避免口径漂移）。
 * 这里 re-export 保持历史导入（scale-contract 等）兼容。
 */
export { countSceneBodyStats } from "./scene-stats";
export {
  scanResourceConsistencyCore,
  readResourceRecords,
  type ResourceIssue,
  type ResourceIssueType,
  type ResourceRecord,
  type ResourceScanResult,
  type ScanResourceOptions
} from "./resource-scan";

class SqliteCreationWorkspace implements CreationWorkspace {
  private closed = false;
  private readonly watchers = new Map<symbol, { scope: CreationWatchScope; listener: CreationWorkspaceListener }>();
  private readonly statsSessions: StatsSessionsModule;
  private readonly search: SearchModule;
  private readonly inbox: InboxModule;
  private readonly resource: ResourceModule;
  private readonly annotation: AnnotationModule;
  private readonly replace: ReplaceModule;
  private readonly structure: StructureModule;
  private readonly bundleIo: BundleIoModule;
  private readonly proof: ProofModule;
  private readonly trash: TrashModule;
  private readonly snapshot: SnapshotModule;
  private readonly views: ProjectViewsModule;
  private readonly cards: CardsModule;
  private readonly sceneWrite: SceneWriteModule;
  private readonly projectWrite: ProjectWriteModule;
  private readonly integrity: IntegrityModule;
  private readonly replaceStore: ReplaceStoreModule;

  constructor(
    private readonly database: Database,
    private readonly workspaceDirectory: string
  ) {
    // 统计与会话域独立模块（纯移动式切片，逻辑与旧内联实现一致）。
    this.statsSessions = createStatsSessionsModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp)
    });
    // 搜索域独立模块。
    this.search = createSearchModule(database);
    // 收件箱域独立模块。
    this.inbox = createInboxModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 资源域独立模块。
    this.resource = createResourceModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 批注域独立模块。
    this.annotation = createAnnotationModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 查找替换域独立模块。
    this.replace = createReplaceModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      requireVolume: (volumeId) => this.requireVolume(volumeId),
      requireChapter: (chapterId) => this.requireChapter(chapterId),
      requireScene: (sceneId) => this.requireScene(sceneId),
      assertSameProject: (projectId, otherProjectId, label) => this.assertSameProject(projectId, otherProjectId, label),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 项目包域独立模块。
    this.bundleIo = createBundleIoModule(database, {
      assertOpen: () => this.assertOpen(),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 替换计划存储域（计划文件与库内写入装配，无宿主回调）。
    this.replaceStore = createReplaceStoreModule(database, this.workspaceDirectory);
    // 完整性检查域（纯只读体检，无宿主回调）。
    this.integrity = createIntegrityModule(database);
    // 项目写入域独立模块（新建作品与导入草稿）。
    this.projectWrite = createProjectWriteModule(database, {
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 场景写入域独立模块。
    this.sceneWrite = createSceneWriteModule(database, {
      emitCommitted: (event) => this.emitCommitted(event),
      hasProjectCardLinks: () => this.cards.hasProjectCardLinks()
    });
    // 卡片域独立模块（卡片/类型/关系读写与关系图）。
    this.cards = createCardsModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      runStructureTransaction: (commandType, op) => this.runStructureTransaction(commandType, op),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 回收站域独立模块。
    this.trash = createTrashModule(database, {
      hasProjectCardLinks: () => this.cards.hasProjectCardLinks(),
      requireProject: (projectId) => this.requireProject(projectId),
      linkedProjectIds: (cardId) => this.cards.linkedProjectIds(cardId),
      runStructureTransaction: (commandType, op) => this.runStructureTransaction(commandType, op),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp)
    });
    // 快照历史域独立模块。
    this.snapshot = createSnapshotModule(database, {
      assertOpen: () => this.assertOpen(),
      requireProject: (projectId) => this.requireProject(projectId),
      runStructureTransaction: (commandType, op) => this.runStructureTransaction(commandType, op),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 项目视图域（纯只读聚合视图，无宿主回调）。
    this.views = createProjectViewsModule(database);
    // 校对域独立模块（含表存在性惰性缓存）。
    this.proof = createProofModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      requireScene: (sceneId) => this.requireScene(sceneId),
      assertSameProject: (projectId, otherProjectId, label) => this.assertSameProject(projectId, otherProjectId, label),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 大纲与结构域独立模块。
    this.structure = createStructureModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      requireVolume: (volumeId) => this.requireVolume(volumeId),
      requireChapter: (chapterId) => this.requireChapter(chapterId),
      requireScene: (sceneId) => this.requireScene(sceneId),
      assertSameProject: (projectId, otherProjectId, label) => this.assertSameProject(projectId, otherProjectId, label),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event),
      readProjectOutline: (projectId) => this.views.readProjectOutline(projectId),
      readWorkflow: (projectId) => this.readWorkflow(projectId),
      projectRevision: (projectId) => this.projectRevision(projectId)
    });
  }

  async read(query: ReadProjectTreeQuery): Promise<CreationProjectTree | null>;
  async read(query: ReadProjectNavigationQuery): Promise<CreationProjectNavigation | null>;
  async read(query: ReadProjectOutlineQuery): Promise<CreationProjectOutline | null>;
  async read(query: ReadSceneBodyQuery): Promise<SceneBodyView | null>;
  async read(query: ListProjectsQuery): Promise<CreationProjectSummary[]>;
  async read(query: CardsListQuery): Promise<CardSummary[]>;
  async read(query: CardReadQuery): Promise<CardSummary | null>;
  async read(query: CardTypesListQuery): Promise<CardType[]>;
  async read(query: RelationTypesListQuery): Promise<RelationType[]>;
  async read(query: CardRelationsQuery): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>;
  async read(query: TrashListQuery): Promise<TrashItem[]>;
  async read(query: SnapshotListQuery): Promise<SnapshotInfo[]>;
  async read(query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null>;
  async read(query: TrashImpactQuery): Promise<TrashImpactView | null>;
  async read(query: ProjectExportQuery): Promise<ProjectExportView | null>;
  async read(query: CreationSearchQuery): Promise<CreationSearchView>;
  async read(query: ReplacePreviewQuery): Promise<ReplacePreviewView>;
  async read(query: StatsViewQuery): Promise<ProjectStatsView | null>;
  async read(query: SessionListQuery): Promise<SessionEntry[]>;
  async read(query: ProofQuery): Promise<ProofView>;
  async read(query: ProofIgnoreListQuery): Promise<ProofIgnoreEntry[]>;
  async read(query: RelationGraphQuery): Promise<RelationGraphView>;
  async read(query: InboxListQuery): Promise<InboxItem[]>;
  async read(query: InboxReadQuery): Promise<InboxItem | null>;
  async read(query: InboxCountQuery): Promise<InboxCountView>;
  async read(query: ProjectBundleExportQuery): Promise<ProjectBundleData | null>;
  async read(query: AnnotationListQuery): Promise<Annotation[]>;
  async read(query: ResourceListQuery): Promise<ResourceInfo[]>;
  async read(query: ProjectHomeQuery): Promise<ProjectHomeView>;
  async read(query: CreationReadQuery): Promise<CreationReadResult> {
    this.assertOpen();
    const runtimeQuery = query as unknown as {
      kind?: unknown;
      projectId?: unknown;
      sceneId?: unknown;
      cardId?: unknown;
      cardKind?: unknown;
      search?: unknown;
      subjectType?: unknown;
      subjectId?: unknown;
      text?: unknown;
      scopes?: unknown;
      filters?: unknown;
      limit?: unknown;
      snapshotId?: unknown;
      entity?: unknown;
      entityId?: unknown;
    } | null;
    if (
      runtimeQuery === null ||
      typeof runtimeQuery.kind !== "string"
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    if (runtimeQuery.kind === "projects.list") {
      try {
        return this.views.listProjects();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作工作区项目列表。");
      }
    }
    if (runtimeQuery.kind === "project.home") {
      try {
        return this.views.readProjectHome();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取项目首页聚合视图。");
      }
    }
    if (runtimeQuery.kind === "scene.body") {
      if (typeof runtimeQuery.sceneId !== "string" || !runtimeQuery.sceneId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "场景读取请求无效。");
      }
      try {
        return this.views.readSceneBody(runtimeQuery.sceneId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取场景正文。");
      }
    }
    if (runtimeQuery.kind === "search.query") {
      try {
        return this.runSearch(query as CreationSearchQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法搜索创作工作区数据。");
      }
    }
    if (runtimeQuery.kind === "replace.preview") {
      try {
        return this.runReplacePreview(query as ReplacePreviewQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法生成查找替换预览。");
      }
    }
    if (runtimeQuery.kind === "proof.query") {
      try {
        return this.proof.runProofQuery(query as ProofQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法执行本地校对。");
      }
    }
    if (runtimeQuery.kind === "proof.ignores") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "校对忽略记录请求无效。");
      }
      try {
        return this.proof.runProofIgnoreList(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取校对忽略记录。");
      }
    }
    if (runtimeQuery.kind === "resource.list") {
      try {
        return this.runResourceList(query as ResourceListQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取附件列表。");
      }
    }
    if (runtimeQuery.kind === "annotation.list") {
      try {
        return this.runAnnotationList(query as AnnotationListQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取批注。");
      }
    }
    if (runtimeQuery.kind === "snapshot.preview") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "快照预览请求无效。");
      }
      if (typeof runtimeQuery.snapshotId !== "string" || !runtimeQuery.snapshotId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "快照预览请求无效。");
      }
      try {
        return this.snapshot.readSnapshotPreview(runtimeQuery.projectId, runtimeQuery.snapshotId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法预览快照差异。");
      }
    }
    if (runtimeQuery.kind === "trash.impact") {
      if (runtimeQuery.projectId !== undefined && (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      const entity = runtimeQuery.entity;
      if (entity !== "volume" && entity !== "chapter" && entity !== "scene" && entity !== "card") {
        throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
      }
      if (typeof runtimeQuery.entityId !== "string" || !runtimeQuery.entityId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      if (runtimeQuery.projectId === undefined && entity !== "card") {
        throw new CreationWorkspaceError("invalid-input", "项目回收站影响请求必须提供作品 ID。");
      }
      try {
        return this.trash.readTrashImpact(
          typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined,
          entity,
          runtimeQuery.entityId
        );
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取回收站影响。");
      }
    }
    if (runtimeQuery.kind === "project.bundle.export") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "项目包读取请求无效。");
      }
      try {
        return this.bundleIo.runProjectBundleExport(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取项目包数据。");
      }
    }
    if (runtimeQuery.kind === "inbox.list") {
      const limit = (runtimeQuery as unknown as { limit?: unknown }).limit;
      const offset = (runtimeQuery as unknown as { offset?: unknown }).offset;
      const resolvedLimit = limit === undefined ? 100 : limit;
      if (!Number.isInteger(resolvedLimit) || (resolvedLimit as number) < 1 || (resolvedLimit as number) > 500) {
        throw new CreationWorkspaceError("invalid-input", "收件箱返回上限必须为 1..500 的整数。");
      }
      const resolvedOffset = offset === undefined ? 0 : offset;
      if (!Number.isInteger(resolvedOffset) || (resolvedOffset as number) < 0) {
        throw new CreationWorkspaceError("invalid-input", "收件箱偏移量必须为非负整数。");
      }
      try {
        return this.runInboxList(resolvedLimit as number, resolvedOffset as number);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取收件箱。");
      }
    }
    if (runtimeQuery.kind === "inbox.count") {
      try {
        return this.runInboxCount();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法统计收件箱。");
      }
    }
    if (runtimeQuery.kind === "inbox.read") {
      if (typeof (runtimeQuery as unknown as { itemId?: unknown }).itemId !== "string") {
        throw new CreationWorkspaceError("invalid-input", "收件箱读取请求无效。");
      }
      try {
        const itemId = (runtimeQuery as unknown as { itemId: string }).itemId;
        const row = this.database
          .prepare("SELECT id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
          .get(itemId) as
          | {
              id: string;
              legacy_id: string | null;
              title: string;
              body: string;
              type: string;
              status: string;
              tags_json: string;
              platform_tags_json: string;
              source_json: string | null;
              variants_json: string;
              revision: number;
              created_at: string;
              updated_at: string;
            }
          | undefined;
        return row ? this.inbox.inboxFromRow(row) : null;
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取收件箱条目。");
      }
    }
    if (runtimeQuery.kind === "stats.view") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "统计读取请求无效。");
      }
      try {
        return this.runStatsView(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作统计。");
      }
    }
    if (runtimeQuery.kind === "session.list") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "会话读取请求无效。");
      }
      const limit = (runtimeQuery as unknown as { limit?: unknown }).limit;
      const resolvedLimit = limit === undefined ? 100 : limit;
      if (!Number.isInteger(resolvedLimit) || (resolvedLimit as number) < 1 || (resolvedLimit as number) > 500) {
        throw new CreationWorkspaceError("invalid-input", "会话返回上限必须为 1..500 的整数。");
      }
      try {
        return this.runSessionList(runtimeQuery.projectId, resolvedLimit as number);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取写作会话。");
      }
    }
    if (
      runtimeQuery.kind === "cards.list" ||
      runtimeQuery.kind === "card.read" ||
      runtimeQuery.kind === "cardTypes.list" ||
      runtimeQuery.kind === "relationTypes.list" ||
      runtimeQuery.kind === "card.relations" ||
      runtimeQuery.kind === "relationGraph.list" ||
      runtimeQuery.kind === "trash.list" ||
      runtimeQuery.kind === "snapshot.list"
    ) {
      try {
        if (runtimeQuery.kind === "cards.list") {
          if (
            runtimeQuery.projectId !== undefined &&
            (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())
          ) {
            throw new CreationWorkspaceError("invalid-input", "卡片读取请求无效。");
          }
          return this.cards.listCards({
            projectId: typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined,
            cardKind:
              typeof runtimeQuery.cardKind === "string" && runtimeQuery.cardKind ? runtimeQuery.cardKind : undefined,
            search:
              typeof runtimeQuery.search === "string" && runtimeQuery.search.trim()
                ? runtimeQuery.search.trim()
                : undefined
          });
        }
        if (runtimeQuery.kind === "cardTypes.list") {
          return this.cards.listCardTypes(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
        }
        if (runtimeQuery.kind === "relationTypes.list") {
          return this.cards.listRelationTypes(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
        }
        if (runtimeQuery.kind === "trash.list") {
          if (runtimeQuery.projectId !== undefined && (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())) {
            throw new CreationWorkspaceError("invalid-input", "回收站读取请求无效。");
          }
          return this.trash.trashList(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
        }
        if (runtimeQuery.kind === "snapshot.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "快照读取请求无效。");
          }
          return this.snapshot.snapshotList({
            projectId: runtimeQuery.projectId,
            subjectType:
              runtimeQuery.subjectType === "scene" || runtimeQuery.subjectType === "card"
                ? runtimeQuery.subjectType
                : undefined,
            subjectId:
              typeof runtimeQuery.subjectId === "string" && runtimeQuery.subjectId
                ? runtimeQuery.subjectId
                : undefined
          });
        }
        if (runtimeQuery.kind === "card.relations") {
          if (typeof runtimeQuery.cardId !== "string" || !runtimeQuery.cardId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "卡片关系读取请求无效。");
          }
          return this.cards.readCardRelations(runtimeQuery.cardId);
        }
        if (runtimeQuery.kind === "relationGraph.list") {
          if (
            runtimeQuery.projectId !== undefined &&
            (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())
          ) {
            throw new CreationWorkspaceError("invalid-input", "关系图读取请求无效。");
          }
          return this.cards.runRelationGraph(runtimeQuery as RelationGraphQuery);
        }
        if (typeof runtimeQuery.cardId !== "string" || !runtimeQuery.cardId.trim()) {
          throw new CreationWorkspaceError("invalid-input", "卡片读取请求无效。");
        }
        return this.cards.readCard(runtimeQuery.cardId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作工作区卡片数据。");
      }
    }
    if (
      (runtimeQuery.kind !== "project.tree" &&
        runtimeQuery.kind !== "project.navigation" &&
        runtimeQuery.kind !== "project.outline" &&
        runtimeQuery.kind !== "project.export") ||
      typeof runtimeQuery.projectId !== "string" ||
      !runtimeQuery.projectId.trim()
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    try {
      if (runtimeQuery.kind === "project.navigation") {
        return this.views.readProjectNavigation(runtimeQuery.projectId);
      }
      if (runtimeQuery.kind === "project.outline") {
        return this.views.readProjectOutline(runtimeQuery.projectId);
      }
      if (runtimeQuery.kind === "project.export") {
        return this.views.readProjectExport(runtimeQuery.projectId, (runtimeQuery as { includeBlocks?: boolean }).includeBlocks === true);
      }
      return this.views.readProjectTree(runtimeQuery.projectId);
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法读取创作工作区数据。");
    }
  }

  private runSearch(query: CreationSearchQuery): CreationSearchView {
    return this.search.runSearch(query);
  }

  private runReplacePreview(query: ReplacePreviewQuery): ReplacePreviewView {
    return this.replace.runReplacePreview(query);
  }

  private replaceApply(command: ReplaceApplyCommand): ReplaceApplyResult {
    return this.replace.replaceApply(command);
  }

  private sessionReport(command: SessionReportCommand): SessionReportResult {
    return this.statsSessions.sessionReport(command);
  }

  private sessionDelete(command: SessionDeleteCommand): SessionReportResult {
    return this.statsSessions.sessionDelete(command);
  }

  private runSessionList(projectId: string, limit: number): SessionEntry[] {
    return this.statsSessions.runSessionList(projectId, limit);
  }


  private runInboxList(limit: number, offset: number): InboxItem[] {
    return this.inbox.runInboxList(limit, offset);
  }

  private runInboxCount(): InboxCountView {
    return this.inbox.runInboxCount();
  }

  private runInboxCreate(command: InboxCreateCommand): InboxItemResult {
    return this.inbox.runInboxCreate(command);
  }

  private runInboxUpdate(command: InboxUpdateCommand): InboxItemResult {
    return this.inbox.runInboxUpdate(command);
  }

  private runInboxDelete(command: InboxDeleteCommand): InboxItemResult {
    return this.inbox.runInboxDelete(command);
  }

  private runInboxConvertToCard(command: InboxConvertToCardCommand): InboxConvertToCardResult {
    return this.inbox.runInboxConvertToCard(command);
  }

  private runAnnotationList(query: AnnotationListQuery): Annotation[] {
    return this.annotation.runAnnotationList(query);
  }

  private runAnnotationCreate(command: AnnotationCreateCommand): AnnotationResult {
    return this.annotation.runAnnotationCreate(command);
  }

  private runAnnotationUpdate(command: AnnotationUpdateCommand): AnnotationResult {
    return this.annotation.runAnnotationUpdate(command);
  }

  private runAnnotationDelete(command: AnnotationDeleteCommand): AnnotationResult {
    return this.annotation.runAnnotationDelete(command);
  }

  private runAnnotationReanchor(command: AnnotationReanchorCommand): AnnotationResult {
    return this.annotation.runAnnotationReanchor(command);
  }

  private runResourceList(query: ResourceListQuery): ResourceInfo[] {
    return this.resource.runResourceList(query);
  }

  private runResourceAttach(command: ResourceAttachCommand): ResourceResult {
    return this.resource.runResourceAttach(command);
  }

  private runResourceDetach(command: ResourceDetachCommand): ResourceResult {
    return this.resource.runResourceDetach(command);
  }

  private runStatsView(projectId: string): ProjectStatsView | null {
    return this.statsSessions.runStatsView(projectId);
  }

  async transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  async transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  async transact(command: StructureCommand): Promise<CreationStructureResult>;
  async transact(command: CardCommand): Promise<CreationStructureResult | CardLinkResult>;
  async transact(command: HistoryCommand): Promise<CreationStructureResult>;
  async transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  async transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  async transact(command: InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand): Promise<InboxItemResult>;
  async transact(command: ProjectImportDraftCommand): Promise<ProjectImportDraftResult>;
  async transact(command: ProjectBundleImportCommand): Promise<ProjectBundleImportResult>;
  async transact(command: AnnotationCreateCommand | AnnotationUpdateCommand | AnnotationDeleteCommand): Promise<AnnotationResult>;
  async transact(command: ResourceAttachCommand | ResourceDetachCommand): Promise<ResourceResult>;
  async transact(command: SceneUpdatePlanningCommand): Promise<SceneUpdatePlanningResult>;
  async transact(command: SceneUpdateMetaCommand): Promise<SceneUpdateMetaResult>;
  async transact(command: ProofIgnoreCommand | ProofUnignoreCommand): Promise<ProofIgnoreResult>;
  async transact<Command extends CreationRunCommand>(command: Command): Promise<CreationRunResultOf<Command>>;
  async transact(command: CreationCommand): Promise<CreationTransactionResult> {
    this.assertOpen();
    if (command.type === "scene.updateBody") {
      return this.sceneWrite.updateSceneBody(command);
    }
    if (command.type === "project.create") {
      return this.projectWrite.createProjectTransaction(command);
    }
    if (command.type === "structure.preview") return this.previewStructure(command);
    if (command.type === "structure.applyWithProtection") return this.applyStructure(command);
    if (command.type === "structure.revert") return this.revertStructure(command);
    if (STRUCTURE_COMMAND_TYPES.has(command.type)) {
      return this.executeStructureCommand(command as StructureCommand);
    }
    if (CARD_COMMAND_TYPES.has(command.type)) {
      return this.cards.executeCardCommand(command as CardCommand);
    }
    if (HISTORY_COMMAND_TYPES.has(command.type)) {
      return this.executeHistoryCommand(command as HistoryCommand);
    }
    if (command.type === "replace.apply") {
      return this.replaceApply(command as ReplaceApplyCommand);
    }
    if (command.type === "session.report") {
      return this.sessionReport(command as SessionReportCommand);
    }
    if (command.type === "session.delete") {
      return this.sessionDelete(command as SessionDeleteCommand);
    }
    if (command.type === "inbox.create") {
      return this.runInboxCreate(command as InboxCreateCommand);
    }
    if (command.type === "inbox.update") {
      return this.runInboxUpdate(command as InboxUpdateCommand);
    }
    if (command.type === "inbox.delete") {
      return this.runInboxDelete(command as InboxDeleteCommand);
    }
    if (command.type === "project.importDraft") {
      return this.projectWrite.importDraftTransaction(command as ProjectImportDraftCommand);
    }
    if (command.type === "project.bundle.import") {
      return this.bundleIo.importProjectBundle(command as ProjectBundleImportCommand);
    }
    if (command.type === "annotation.create") {
      return this.runAnnotationCreate(command as AnnotationCreateCommand);
    }
    if (command.type === "annotation.update") {
      return this.runAnnotationUpdate(command as AnnotationUpdateCommand);
    }
    if (command.type === "annotation.delete") {
      return this.runAnnotationDelete(command as AnnotationDeleteCommand);
    }
    if (command.type === "annotation.reanchor") {
      return this.runAnnotationReanchor(command as AnnotationReanchorCommand);
    }
    if (command.type === "resource.attach") {
      return this.runResourceAttach(command as ResourceAttachCommand);
    }
    if (command.type === "resource.detach") {
      return this.runResourceDetach(command as ResourceDetachCommand);
    }
    if (command.type === "scene.updatePlanning") {
      return this.sceneWrite.runSceneUpdatePlanning(command as SceneUpdatePlanningCommand);
    }
    if (command.type === "scene.updateMeta") {
      return this.sceneWrite.runSceneUpdateMeta(command as SceneUpdateMetaCommand);
    }
    if (command.type === "proof.ignore") {
      return this.proof.proofIgnore(command as ProofIgnoreCommand);
    }
    if (command.type === "proof.unignore") {
      return this.proof.proofUnignore(command as ProofUnignoreCommand);
    }
    if (command.type === "inbox.convertToCard") {
      return this.runInboxConvertToCard(command as InboxConvertToCardCommand);
    }
    throw new CreationWorkspaceError("invalid-input", "创作工作区命令无效。");
  }

  private executeStructureCommand(command: StructureCommand): CreationStructureResult {
    return this.structure.executeStructureCommand(command);
  }

  private runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => {
      projectId: string | null;
      entityId: string;
      revision: number;
      changes: CreationWorkspaceEvent["changes"];
    }
  ): CreationStructureResult {
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const outcome = op(timestamp);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(outcome.projectId, commandType, JSON.stringify(outcome.changes), timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType,
        sequence: Number(logged.lastInsertRowid),
        projectId: outcome.projectId,
        entityId: outcome.entityId,
        revision: outcome.revision,
        updatedAt: timestamp
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: outcome.projectId,
        commandType,
        changes: outcome.changes
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      if (isConstraintError(error)) {
        throw new CreationWorkspaceError("conflict", "创作结构命令违反稳定标识约束。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交创作结构命令事务。");
    }
  }

  previewStructure(command: StructurePreviewCommand): Promise<StructurePreviewView> {
    return this.structure.previewStructure(command);
  }

  applyStructure(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult> {
    return this.structure.applyStructure(command);
  }

  revertStructure(command: StructureRevertCommand): Promise<StructureRevertResult> {
    return this.structure.revertStructure(command);
  }

  private requireProject(projectId: string): void {
    const project = this.database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId) as { id: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
  }

  private projectRevision(projectId: string): number {
    const row = this.database
      .prepare(
        `SELECT COALESCE(MAX(r), 0) AS rev FROM (
           SELECT revision AS r FROM volumes WHERE project_id = ? AND deleted_at IS NULL
           UNION ALL
           SELECT revision AS r FROM chapters WHERE project_id = ? AND deleted_at IS NULL
           UNION ALL
           SELECT s.revision AS r FROM scenes s JOIN chapters c ON c.id = s.chapter_id
           WHERE c.project_id = ? AND s.deleted_at IS NULL
         )`
      )
      .get(projectId, projectId, projectId) as { rev: number };
    return row.rev;
  }

  private requireVolume(volumeId: string): { id: string; project_id: string; title: string; revision: number } {
    const volume = this.database
      .prepare("SELECT id, project_id, title, revision FROM volumes WHERE id = ? AND deleted_at IS NULL")
      .get(volumeId) as { id: string; project_id: string; title: string; revision: number } | undefined;
    if (!volume) throw new CreationWorkspaceError("not-found", "卷不存在。");
    return volume;
  }

  private requireChapter(chapterId: string): {
    id: string;
    project_id: string;
    volume_id: string | null;
    title: string;
    revision: number;
  } {
    const chapter = this.database
      .prepare("SELECT id, project_id, volume_id, title, revision FROM chapters WHERE id = ? AND deleted_at IS NULL")
      .get(chapterId) as
      | { id: string; project_id: string; volume_id: string | null; title: string; revision: number }
      | undefined;
    if (!chapter) throw new CreationWorkspaceError("not-found", "章节不存在。");
    return chapter;
  }

  private requireScene(sceneId: string): { id: string; chapter_id: string; project_id: string; revision: number } {
    const scene = this.database
      .prepare(
        `SELECT s.id, s.chapter_id, s.revision, c.project_id
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.id = ? AND s.deleted_at IS NULL`
      )
      .get(sceneId) as { id: string; chapter_id: string; project_id: string; revision: number } | undefined;
    if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
    return scene;
  }

  private assertSameProject(projectId: string, otherProjectId: string, label: string): void {
    if (projectId !== otherProjectId) {
      throw new CreationWorkspaceError("invalid-input", `${label}不属于同一作品。`);
    }
  }

  private touchProject(projectId: string, timestamp: string): void {
    this.database.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run(timestamp, projectId);
  }

  private readWorkflow(projectId: string): string[] {
    const project = this.database
      .prepare("SELECT setup_json FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
    return parseStoredSetup(project.setup_json).chapterWorkflow;
  }

  private executeHistoryCommand(command: HistoryCommand): CreationStructureResult {
    switch (command.type) {
      case "trash.restore":
        return this.trash.trashRestore(command);
      case "trash.purge":
        return this.trash.trashPurge(command);
      case "snapshot.create":
        return this.snapshot.snapshotCreate(command);
    }
    throw new CreationWorkspaceError("invalid-input", "不支持的历史命令。");
  }

  async restoreSnapshotWithProtection(command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult> {
    return this.snapshot.restoreSnapshotWithProtection(command);
  }

  async runSnapshotRetention(): Promise<SnapshotRetentionResult> {
    return this.snapshot.runSnapshotRetention();
  }

  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void {
    this.assertOpen();
    const runtimeScope = scope as unknown as { projectId?: unknown } | null;
    if (
      runtimeScope === null ||
      typeof runtimeScope !== "object" ||
      typeof listener !== "function" ||
      (runtimeScope.projectId !== undefined &&
        (typeof runtimeScope.projectId !== "string" || !runtimeScope.projectId.trim()))
    ) {
      throw new CreationWorkspaceError("invalid-input", "创作工作区订阅请求无效。");
    }
    const token = Symbol("creation-workspace-watcher");
    this.watchers.set(token, {
      scope: runtimeScope.projectId === undefined ? {} : { projectId: runtimeScope.projectId as string },
      listener
    });
    return () => {
      this.watchers.delete(token);
    };
  }

  async check(): Promise<CreationIntegrityReport> {
    this.assertOpen();
    try {
      return this.integrity.runIntegrityCheck();
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法检查创作工作区完整性。");
    }
  }

  // ---- Phase 1 P1 深模块 seam 接入实现 ----

  /** 读取导入所需的项目模式上下文（类型 / 关系类型 / 现有卡片标题）。 */
  async cardImportSchema(projectId: string): Promise<CardImportSchemaContext> {
    this.assertOpen();
    validateId(projectId, "作品");
    return readCardImportSchemaContext(this.database, projectId);
  }

  /** 读模式上下文 -> 规划（纯，不写库），供 UI 预览可应用结果。 */
  async cardImportPlan(input: CardImportApplyInput): Promise<CardImportPlan> {
    this.assertOpen();
    validateId(input.projectId, "作品");
    const schema = readCardImportSchemaContext(this.database, input.projectId);
    return planCardImport(input.text, input.format, input.mapping, schema, input.options);
  }

  /** 读模式 -> 规划 -> 单事务 apply；失败回滚零写入。 */
  async cardImportApply(input: CardImportApplyInput): Promise<CardImportApplyResult> {
    this.assertOpen();
    validateId(input.projectId, "作品");
    const schema = readCardImportSchemaContext(this.database, input.projectId);
    const plan = planCardImport(input.text, input.format, input.mapping, schema, input.options);
    const planId = generateCardImportPlanId();
    return applyCardImportPlan(this.database, plan, planId);
  }

  /** 按筛选范围读取可导出卡片行（不携带内部 id；cardRef 已转为标题）。 */
  async cardExportRows(projectId: string, filter: CardExportFilter): Promise<CardExportRow[]> {
    this.assertOpen();
    validateId(projectId, "作品");
    return readCardsForExport(this.database, projectId, filter);
  }

  async createReplacePlan(query: ReplacePlanQuery, controller?: ReplacePlanController): Promise<ReplacePlan> {
    this.assertOpen();
    return runCreateReplacePlan(this.replaceStore.buildReplacePlanStore(), query, controller);
  }

  async applyReplacePlan(
    planId: string,
    excludedHitIds: string[],
    controller?: ReplacePlanController
  ): Promise<ReplaceApplyOutcome> {
    this.assertOpen();
    const store = this.replaceStore.buildReplacePlanStore();
    const plan = store.loadPlan(planId);
    const projectId = plan?.projectId ?? "";
    const outcome = await runApplyReplacePlan(store, planId, excludedHitIds, controller);
    if (projectId && outcome.modifiedSceneIds.length > 0) {
      const timestamp = outcome.committedAt;
      this.touchProject(projectId, timestamp);
      const changes = outcome.modifiedSceneIds.flatMap((sceneId, index) => [
        { entity: "scene" as const, id: sceneId, action: "updated" as const, revision: 0 },
        {
          entity: "snapshot" as const,
          id: outcome.snapshotIds[index] ?? "",
          action: "created" as const,
          revision: 1
        }
      ]);
      this.emitCommitted({
        kind: "committed",
        sequence: outcome.sequence,
        projectId,
        commandType: "replace.applyPlan",
        changes
      });
    }
    return outcome;
  }

  /** 修正已有写作会话（仅 startedAt/activeSeconds/netChars 可改）。 */
  async sessionUpdate(command: SessionUpdateCommand): Promise<SessionReportResult> {
    this.assertOpen();
    const projectId = validateId(command.projectId, "作品");
    this.requireProject(projectId);
    const sessionId = validateId(command.sessionId, "会话");
    const row = this.database
      .prepare("SELECT id, started_at, active_seconds, net_chars FROM writing_sessions WHERE id = ? AND project_id = ?")
      .get(sessionId, projectId) as { id: string; started_at: string; active_seconds: number; net_chars: number } | undefined;
    if (!row) throw new CreationWorkspaceError("not-found", "会话不存在或不属于该项目。");

    const startedAt =
      typeof command.startedAt === "string" && !Number.isNaN(Date.parse(command.startedAt))
        ? new Date(command.startedAt).toISOString()
        : row.started_at;
    const activeSeconds =
      command.activeSeconds === undefined ? row.active_seconds : command.activeSeconds;
    if (!Number.isFinite(activeSeconds) || activeSeconds < 0 || activeSeconds > 86_400) {
      throw new CreationWorkspaceError("invalid-input", "活动时长必须在 0 至 86400 秒之间。");
    }
    const netChars = command.netChars === undefined ? row.net_chars : command.netChars;
    if (!Number.isFinite(netChars) || netChars < -1_000_000 || netChars > 1_000_000) {
      throw new CreationWorkspaceError("invalid-input", "净增字符数超出允许范围。");
    }
    const timestamp = new Date().toISOString();
    this.database
      .prepare("UPDATE writing_sessions SET started_at = ?, active_seconds = ?, net_chars = ? WHERE id = ?")
      .run(startedAt, Math.round(activeSeconds), Math.round(netChars), sessionId);
    this.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "session.update",
      changes: [{ entity: "session", id: sessionId, action: "updated", revision: 0 }]
    });
    return { commandType: "session.update", sequence: -1, projectId, sessionId, updatedAt: timestamp };
  }

  /** 更新项目目标（写入 setup_json，不新增表列）。 */
  async projectUpdateGoal(command: ProjectUpdateGoalCommand): Promise<ProjectGoalResult> {
    this.assertOpen();
    const projectId = validateId(command.projectId, "作品");
    const row = this.database
      .prepare("SELECT setup_json, revision FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string; revision: number } | undefined;
    if (!row) throw new CreationWorkspaceError("not-found", "作品不存在。");
    const setup = parseStoredSetup(row.setup_json);
    if (command.dailyWordGoal !== undefined) setup.dailyWordGoal = command.dailyWordGoal ?? undefined;
    if (command.weeklyWordGoal !== undefined) setup.weeklyWordGoal = command.weeklyWordGoal ?? undefined;
    if (command.totalWordGoal !== undefined) setup.totalWordGoal = command.totalWordGoal ?? undefined;
    if (command.targetDate !== undefined) setup.targetDate = command.targetDate ?? undefined;
    if (command.description !== undefined) setup.description = command.description ?? undefined;
    if (command.genre !== undefined) setup.genre = command.genre ?? undefined;
    if (command.weeklyUpdateDays !== undefined) {
      setup.weeklyUpdateDays = command.weeklyUpdateDays.filter(
        (day) => Number.isInteger(day) && day >= 1 && day <= 7
      );
    }
    const newRevision = row.revision + 1;
    const timestamp = new Date().toISOString();
    this.database
      .prepare("UPDATE projects SET setup_json = ?, revision = ?, updated_at = ? WHERE id = ?")
      .run(JSON.stringify(setup), newRevision, timestamp, projectId);
    this.touchProject(projectId, timestamp);
    this.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "project.updateGoal",
      changes: [{ entity: "project", id: projectId, action: "updated", revision: newRevision }]
    });
    return { projectId, setup };
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.watchers.clear();
    let checkpointFailed = false;
    try {
      this.database.pragma("wal_checkpoint(TRUNCATE)");
    } catch {
      checkpointFailed = true;
    }
    try {
      this.database.close();
      this.closed = true;
    } catch {
      throw new CreationWorkspaceError("integrity", "无法关闭创作工作区。");
    }
    if (checkpointFailed) {
      throw new CreationWorkspaceError("integrity", "创作工作区已关闭，但 WAL 检查点未完成。");
    }
  }

  private assertOpen(): void {
    if (this.closed) throw new CreationWorkspaceError("closed", "创作工作区已关闭。");
  }

  previewProjectBundleImport(data: ProjectBundleData): Promise<ProjectBundleImportPreview> {
    return this.bundleIo.previewProjectBundleImport(data);
  }

  private emitCommitted(event: CreationWorkspaceEvent): void {
    for (const { scope, listener } of this.watchers.values()) {
      // projectId=null 表示全局实体发生变化，所有项目作用域都必须立即失效刷新。
      if (scope.projectId !== undefined && event.projectId !== null && scope.projectId !== event.projectId) continue;
      try {
        listener(event);
      } catch {
        // A subscriber cannot roll back or break an already committed transaction.
      }
    }
  }
}

export async function openCreationWorkspace(options: OpenCreationWorkspaceOptions): Promise<CreationWorkspace> {
  if (!options || typeof options.directory !== "string" || !options.directory.trim()) {
    throw new CreationWorkspaceError("invalid-input", "创作工作区目录不能为空。");
  }
  let database: Database | undefined;
  try {
    await mkdir(options.directory, { recursive: true });
    const databasePath = path.join(options.directory, "workspace.sqlite");
    const configureDatabase = (target: Database): void => {
      target.pragma("journal_mode = WAL");
      target.pragma("foreign_keys = ON");
      target.pragma("synchronous = FULL");
    };
    database = new Database(databasePath);
    configureDatabase(database);
    const existingVersion = Number(database.pragma("user_version", { simple: true }));
    if (existingVersion === 0) initializeSchema(database);
    else if (existingVersion === 1) {
      migrateSchemaV1ToV2(database);
      migrateSchemaV2ToV3(database);
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 2) {
      migrateSchemaV2ToV3(database);
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 3) {
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 4) {
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 5) {
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 6) {
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 7) {
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 8) migrateSchemaV8ToV9(database);
    else if (
      existingVersion !== 9 &&
      existingVersion !== GLOBAL_CARD_SCHEMA_VERSION &&
      existingVersion !== SCENE_META_SCHEMA_VERSION &&
      existingVersion !== SCHEMA_VERSION
    ) {
      throw new CreationWorkspaceError("integrity", `不支持的创作工作区 schema 版本：${existingVersion}。`);
    }

    const versionAfterLegacyMigrations = Number(database.pragma("user_version", { simple: true }));
    if (options.testOnlyTargetSchemaVersion === 9) {
      if (versionAfterLegacyMigrations !== 9) {
        throw new CreationWorkspaceError("integrity", "测试夹具只能停留在 schema v9。");
      }
    } else if (versionAfterLegacyMigrations === 9) {
      // 新建空库不需要可恢复备份；任何既有库（含从 v1..v8 逐级升到 v9 的库）
      // 都先 checkpoint/close，再创建带哈希清单的目录级快照，成功后才进入 v10 事务。
      let backupRoot: string | null = null;
      if (existingVersion !== 0) {
        database.pragma("wal_checkpoint(TRUNCATE)");
        database.close();
        database = undefined;
        const backup = await createV9MigrationBackup(options.directory, {
          failBackup: options.testOnlyFailV9ToV10Backup === true
        });
        backupRoot = backup.backupRoot;
        database = new Database(databasePath);
        configureDatabase(database);
      }
      migrateGlobalCardsV9ToV10(database, backupRoot, {
        failAfterLinkBackfill: options.testOnlyFailV9ToV10AfterLinkBackfill === true
      });
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === GLOBAL_CARD_SCHEMA_VERSION &&
      needsGlobalCardSchemaRepair(database)
    ) {
      database.pragma("wal_checkpoint(TRUNCATE)");
      database.close();
      database = undefined;
      const backup = await createGlobalCardSchemaRepairBackup(options.directory);
      database = new Database(databasePath);
      configureDatabase(database);
      repairGlobalCardSchemaV10(database, backup.backupRoot);
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === GLOBAL_CARD_SCHEMA_VERSION
    ) {
      migrateSchemaV10ToV11(database);
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === SCENE_META_SCHEMA_VERSION
    ) {
      migrateSchemaV11ToV12(database);
    }
    if (options.testOnlyTargetSchemaVersion !== 9) ensureGlobalCardResourceSchema(database);
    if (options.testOnlyTargetSchemaVersion !== 9) ensureProofIgnoreSchema(database);
    try {
      purgeExpiredTrash(database);
      await drainGlobalCardResourceGc(database, options.directory);
    } catch {
      // 到期清理失败不应阻止工作区打开
    }
    return new SqliteCreationWorkspace(database, options.directory);
  } catch (error) {
    try {
      database?.close();
    } catch {
      // Preserve the stable workspace error instead of exposing a close failure.
    }
    if (error instanceof CreationWorkspaceError) throw error;
    throw new CreationWorkspaceError("integrity", "无法打开创作工作区。");
  }
}

export * from "./types";
