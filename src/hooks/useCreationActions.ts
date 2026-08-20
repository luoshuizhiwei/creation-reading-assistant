import { useCallback } from "react";
import {
  cardRead,
  cardRelations,
  cardsList,
  cardTypesList,
  createProject as createProjectRequest,
  exportDraft as exportDraftRequest,
  listProjects,
  readProjectHome,
  readProjectNavigation,
  readProjectOutline,
  readSceneBody,
  relationTypesList,
  replaceApply as replaceApplyRequest,
  replacePreview as replacePreviewRequest,
  runStructure as runStructureRequest,
  structureApply as structureApplyRequest,
  structurePreview as structurePreviewRequest,
  structureRevert as structureRevertRequest,
  search as searchRequest,
  sessionDelete as sessionDeleteRequest,
  sessionList as sessionListRequest,
  sessionReport as sessionReportRequest,
  snapshotList,
  statsView as statsViewRequest,
  proofQuery as proofQueryRequest,
  projectExport as projectExportRequest,
  migrationStatus as migrationStatusRequest,
  migrationRun as migrationRunRequest,
  inboxList as inboxListRequest,
  inboxCount as inboxCountRequest,
  inboxUpdate as inboxUpdateRequest,
  inboxDelete as inboxDeleteRequest,
  inboxCreate as inboxCreateRequest,
  importDraftPreview as importDraftPreviewRequest,
  exportProjectBundle as exportProjectBundleRequest,
  importProjectBundle as importProjectBundleRequest,
  annotationList as annotationListRequest,
  annotationCreate as annotationCreateRequest,
  annotationUpdate as annotationUpdateRequest,
  annotationDelete as annotationDeleteRequest,
  annotationReanchor as annotationReanchorRequest,
  snapshotPreview as snapshotPreviewRequest,
  snapshotRestoreWithProtection as snapshotRestoreWithProtectionRequest,
  trashImpact as trashImpactRequest,
  resourceList as resourceListRequest,
  attachResource as attachResourceRequest,
  detachResource as detachResourceRequest,
  trashList,
  updateSceneBody,
  watchProject,
  snapshotRetentionRun,
  cardImportOpenAndParse,
  cardImportParse,
  cardImportSchema,
  cardImportPlan,
  cardImportApply,
  cardExportOpenAndWrite,
  replacePlanCreate,
  replacePlanApply,
  sessionUpdate as sessionUpdateRequest,
  projectUpdateGoal as projectUpdateGoalRequest
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CardRelation,
  CardRelationCreateCommand,
  CardSummary,
  CardsListQuery,
  CreateProjectInput,
  CreationDocument,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationRunCommand,
  CreationSearchQuery,
  CreationSearchView,
  CreationWorkspaceEvent,
  ReplaceApplyCommand,
  ReplacePreviewQuery,
  ReplacePreviewView,
  SceneSaveResponse,
  SessionDeleteCommand,
  SessionListQuery,
  SessionReportCommand,
  SnapshotListQuery,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxListQuery,
  InboxCountView,
  InboxUpdateCommand,
  InboxCreateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  DraftExportPreset,
  DraftImportPreview,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationReanchorCommand,
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
  ProjectHomeView,
  TrashEntityKind,
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
import { messageFromError } from "@/utils/format";

/**
 * loadCards 请求序列号：模块级共享，防止多个组件/项目并发加载时旧请求覆盖新结果。
 * 每个 loadCards 调用递增并捕获当前 seq；只有响应到达时 seq 仍匹配才写 store。
 */
let cardsLoadSeq = 0;
let cardsLoadLastProjectId = "";

/**
 * navigation 请求按 projectId 去重：同一项目同一时刻只允许一个 readProjectNavigation 请求。
 * 普通页面加载与导航请求共享同一 in-flight Promise，避免重复读取。
 */
const navigationInFlight = new Map<string, Promise<CreationProjectNavigation | null | undefined>>();

/**
 * 正在进行中的场景保存（场景 ID 集合）。
 * watch 事件到达时，若命中该集合则视为“自身保存事件”，不覆盖本地正文，
 * 避免事件与保存响应乱序到达时把用户未提交内容冲掉。
 */
const inFlightSceneSaves = new Set<string>();

export function useCreationActions() {
  const setProjects = useCreationStore((state) => state.setProjects);
  const setLoading = useCreationStore((state) => state.setLoading);
  const setError = useAppStore((state) => state.setError);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    try {
      setProjects(await listProjects());
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [setError, setLoading, setProjects]);

  const loadProjectHome = useCallback(async (): Promise<ProjectHomeView> => {
    try {
      return await readProjectHome();
    } catch (error) {
      setError(messageFromError(error));
      return { projects: [] };
    }
  }, [setError]);

  const createProject = useCallback(
    async (input: CreateProjectInput): Promise<CreationProjectNavigation | undefined> => {
      setLoading(true);
      try {
        const navigation = await createProjectRequest(input);
        useCreationStore.getState().upsertNavigation(navigation);
        return navigation;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        setLoading(false);
      }
    },
    [setError, setLoading]
  );

  const loadNavigation = useCallback(
    (projectId: string): Promise<CreationProjectNavigation | null | undefined> => {
      const existing = navigationInFlight.get(projectId);
      if (existing) return existing;
      const promise = (async () => {
        try {
          const navigation = await readProjectNavigation(projectId);
          if (navigation) useCreationStore.getState().setNavigation(projectId, navigation);
          return navigation;
        } catch (error) {
          setError(messageFromError(error));
          return undefined;
        } finally {
          navigationInFlight.delete(projectId);
        }
      })();
      navigationInFlight.set(projectId, promise);
      return promise;
    },
    [setError]
  );

  const loadOutline = useCallback(
    async (projectId: string): Promise<CreationProjectOutline | null | undefined> => {
      try {
        const outline = await readProjectOutline(projectId);
        if (outline) useCreationStore.getState().setOutline(projectId, outline);
        return outline;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      }
    },
    [setError]
  );

  const runStructure = useCallback(
    async (command: CreationRunCommand): Promise<boolean> => {
      try {
        await runStructureRequest(command);
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const previewStructure = useCallback(
    async (command: StructurePreviewCommand): Promise<StructurePreviewView | null> => {
      try {
        return await structurePreviewRequest(command);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const applyStructureWithProtection = useCallback(
    async (command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult | null> => {
      try {
        return await structureApplyRequest(command);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const revertStructure = useCallback(
    async (command: StructureRevertCommand): Promise<StructureRevertResult | null> => {
      try {
        return await structureRevertRequest(command);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadCardTypes = useCallback(
    async (projectId: string): Promise<void> => {
      useCreationStore.getState().activateCardProject(projectId);
      try {
        useCreationStore.getState().setCardTypes(projectId, await cardTypesList(projectId));
      } catch (error) {
        setError(messageFromError(error));
      }
    },
    [setError]
  );

  const loadRelationTypes = useCallback(
    async (projectId: string): Promise<void> => {
      useCreationStore.getState().activateCardProject(projectId);
      try {
        useCreationStore.getState().setRelationTypes(projectId, await relationTypesList(projectId));
      } catch (error) {
        setError(messageFromError(error));
      }
    },
    [setError]
  );

  const loadCards = useCallback(
    async (query: Omit<CardsListQuery, "kind">): Promise<CardSummary[] | undefined> => {
      useCreationStore.getState().activateCardProject(query.projectId);
      useCreationStore.getState().setCardsLoading(query.projectId, true);
      const seq = ++cardsLoadSeq;
      cardsLoadLastProjectId = query.projectId;
      try {
        const result = await cardsList({ kind: "cards.list", ...query });
        if (seq === cardsLoadSeq && query.projectId === cardsLoadLastProjectId) {
          useCreationStore.getState().setCards(query.projectId, result);
          return result;
        }
        // 请求已过期：旧项目响应不得覆盖新项目，也不得被当成当前结果消费。
        return undefined;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        if (seq === cardsLoadSeq && query.projectId === cardsLoadLastProjectId) {
          useCreationStore.getState().setCardsLoading(query.projectId, false);
        }
      }
    },
    [setError]
  );

  const loadCardRelations = useCallback(
    async (projectId: string, cardId: string): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }> => {
      try {
        const relations = await cardRelations(cardId);
        useCreationStore.getState().setCardRelations(projectId, cardId, relations);
        return relations;
      } catch (error) {
        setError(messageFromError(error));
        return { outgoing: [], incoming: [] };
      }
    },
    [setError]
  );

  const readCard = useCallback(
    async (cardId: string) => {
      try {
        return await cardRead(cardId);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadTrash = useCallback(
    async (projectId: string) => {
      try {
        return await trashList(projectId);
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const restoreTrash = useCallback(
    async (projectId: string, entity: TrashEntityKind, entityId: string): Promise<boolean> =>
      runStructure({ type: "trash.restore", projectId, entity, entityId }),
    [runStructure]
  );

  const purgeTrash = useCallback(
    async (projectId: string, entity: TrashEntityKind, entityId: string): Promise<boolean> =>
      runStructure({ type: "trash.purge", projectId, entity, entityId }),
    [runStructure]
  );

  const loadSnapshots = useCallback(
    async (query: SnapshotListQuery) => {
      try {
        return await snapshotList(query);
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const exportDraft = useCallback(
    async (projectId: string, preset: DraftExportPreset): Promise<{ canceled: boolean; filePath: string | null }> => {
      try {
        return await exportDraftRequest(projectId, preset);
      } catch (error) {
        setError(messageFromError(error));
        return { canceled: true, filePath: null };
      }
    },
    [setError]
  );

  const search = useCallback(
    async (query: Omit<CreationSearchQuery, "kind">): Promise<CreationSearchView> => {
      try {
        return await searchRequest({ kind: "search.query", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return { query: query.text, hits: [], total: 0 };
      }
    },
    [setError]
  );

  const replacePreview = useCallback(
    async (query: Omit<ReplacePreviewQuery, "kind">): Promise<ReplacePreviewView> => {
      try {
        return await replacePreviewRequest({ kind: "replace.preview", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return {
          projectId: query.projectId,
          find: query.find,
          replaceWith: query.replaceWith,
          scope: query.scope,
          sceneHits: [],
          totalHits: 0,
          matchedScenes: 0
        };
      }
    },
    [setError]
  );

  const replaceApply = useCallback(
    async (command: ReplaceApplyCommand): Promise<boolean> => {
      try {
        await replaceApplyRequest(command);
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const loadStats = useCallback(
    async (projectId: string) => {
      try {
        return await statsViewRequest(projectId);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadSessions = useCallback(
    async (query: Omit<SessionListQuery, "kind">) => {
      try {
        return await sessionListRequest({ kind: "session.list", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const reportSession = useCallback(
    async (command: Omit<SessionReportCommand, "type">): Promise<boolean> => {
      try {
        await sessionReportRequest({ type: "session.report", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const deleteSession = useCallback(
    async (command: Omit<SessionDeleteCommand, "type">): Promise<boolean> => {
      try {
        await sessionDeleteRequest({ type: "session.delete", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const runProof = useCallback(
    async (query: Omit<ProofQuery, "kind">): Promise<ProofView> => {
      try {
        return await proofQueryRequest({ kind: "proof.query", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return {
          projectId: query.projectId,
          issues: [],
          scannedScenes: 0,
          affectedScenes: 0,
          total: 0
        };
      }
    },
    [setError]
  );

  const loadMigrationStatus = useCallback(async (): Promise<LegacyMigrationStatus | null> => {
    try {
      return await migrationStatusRequest();
    } catch (error) {
      setError(messageFromError(error));
      return null;
    }
  }, [setError]);

  const runMigration = useCallback(async (): Promise<LegacyMigrationReport | null> => {
    try {
      return await migrationRunRequest();
    } catch (error) {
      setError(messageFromError(error));
      return null;
    }
  }, [setError]);

  const loadInbox = useCallback(
    async (query: Omit<InboxListQuery, "kind">): Promise<InboxItem[]> => {
      try {
        return await inboxListRequest({ kind: "inbox.list", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const loadInboxCount = useCallback(async (): Promise<InboxCountView> => {
    try {
      return await inboxCountRequest();
    } catch (error) {
      setError(messageFromError(error));
      return { total: 0, pending: 0 };
    }
  }, [setError]);

  const updateInbox = useCallback(
    async (command: Omit<InboxUpdateCommand, "type">): Promise<boolean> => {
      try {
        await inboxUpdateRequest({ type: "inbox.update", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const deleteInbox = useCallback(
    async (command: Omit<InboxDeleteCommand, "type">): Promise<boolean> => {
      try {
        await inboxDeleteRequest({ type: "inbox.delete", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const createInbox = useCallback(
    async (command: Omit<InboxCreateCommand, "type">): Promise<string | undefined> => {
      try {
        const result = await inboxCreateRequest({ type: "inbox.create", ...command });
        return result.itemId;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      }
    },
    [setError]
  );

  const previewDraftImport = useCallback(async (): Promise<DraftImportPreview | null> => {
    try {
      return await importDraftPreviewRequest();
    } catch (error) {
      setError(messageFromError(error));
      return null;
    }
  }, [setError]);

  const exportBundle = useCallback(
    async (projectId: string): Promise<{ canceled: boolean; directory: string | null }> => {
      try {
        return await exportProjectBundleRequest(projectId);
      } catch (error) {
        setError(messageFromError(error));
        return { canceled: true, directory: null };
      }
    },
    [setError]
  );

  const importBundle = useCallback(async (): Promise<{ canceled: boolean; result: ProjectBundleImportResult | null }> => {
    try {
      return await importProjectBundleRequest();
    } catch (error) {
      setError(messageFromError(error));
      return { canceled: true, result: null };
    }
  }, [setError]);

  const loadAnnotations = useCallback(
    async (query: Omit<AnnotationListQuery, "kind">): Promise<Annotation[]> => {
      try {
        return await annotationListRequest({ kind: "annotation.list", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const createAnnotation = useCallback(
    async (command: Omit<AnnotationCreateCommand, "type">): Promise<boolean> => {
      try {
        await annotationCreateRequest({ type: "annotation.create", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const updateAnnotation = useCallback(
    async (command: Omit<AnnotationUpdateCommand, "type">): Promise<boolean> => {
      try {
        await annotationUpdateRequest({ type: "annotation.update", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const deleteAnnotation = useCallback(
    async (command: Omit<AnnotationDeleteCommand, "type">): Promise<boolean> => {
      try {
        await annotationDeleteRequest({ type: "annotation.delete", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const reanchorAnnotation = useCallback(
    async (command: AnnotationReanchorCommand): Promise<boolean> => {
      try {
        await annotationReanchorRequest(command);
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const updateCardType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "cardType.update" }>): Promise<boolean> =>
      runStructure(command),
    [runStructure]
  );

  const deleteCardType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "cardType.delete" }>): Promise<boolean> =>
      runStructure(command),
    [runStructure]
  );

  const updateRelationType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "relationType.update" }>): Promise<boolean> =>
      runStructure(command),
    [runStructure]
  );

  const deleteRelationType = useCallback(
    async (command: Extract<CreationRunCommand, { type: "relationType.delete" }>): Promise<boolean> =>
      runStructure(command),
    [runStructure]
  );

  const previewSnapshot = useCallback(
    async (query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null> => {
      try {
        return await snapshotPreviewRequest(query);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const restoreSnapshotWithProtection = useCallback(
    async (command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult | null> => {
      try {
        return await snapshotRestoreWithProtectionRequest(command);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadTrashImpact = useCallback(
    async (query: TrashImpactQuery): Promise<TrashImpactView | null> => {
      try {
        return await trashImpactRequest(query);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadResources = useCallback(
    async (query: Omit<ResourceListQuery, "kind">): Promise<ResourceInfo[]> => {
      try {
        return await resourceListRequest({ kind: "resource.list", ...query });
      } catch (error) {
        setError(messageFromError(error));
        return [];
      }
    },
    [setError]
  );

  const attachResource = useCallback(
    async (projectId: string, cardId?: string): Promise<{ canceled: boolean; resource: ResourceResult | null }> => {
      try {
        return await attachResourceRequest(projectId, cardId);
      } catch (error) {
        setError(messageFromError(error));
        return { canceled: true, resource: null };
      }
    },
    [setError]
  );

  const detachResource = useCallback(
    async (resourceId: string): Promise<boolean> => {
      try {
        await detachResourceRequest(resourceId);
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const loadProjectExport = useCallback(
    async (projectId: string): Promise<ProjectExportView | null> => {
      try {
        return await projectExportRequest(projectId);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadScene = useCallback(
    async (sceneId: string) => {
      try {
        const view = await readSceneBody(sceneId);
        if (view) useCreationStore.getState().setSceneView(sceneId, view);
        return view;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      }
    },
    [setError]
  );

  const saveSceneBody = useCallback(
    async (sceneId: string, baseRevision: number, body: CreationDocument): Promise<SceneSaveResponse | undefined> => {
      inFlightSceneSaves.add(sceneId);
      try {
        const result = await updateSceneBody({ sceneId, baseRevision, body });
        if (result.ok) useCreationStore.getState().applySceneSaveResult(result, body);
        return result;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      } finally {
        inFlightSceneSaves.delete(sceneId);
      }
    },
    [setError]
  );

  const refreshProject = useCallback(
    async (projectId: string): Promise<CreationProjectNavigation | null | undefined> => {
      return loadNavigation(projectId);
    },
    [loadNavigation]
  );

  const subscribeProject = useCallback(
    (projectId: string): (() => void) => {
      let cancelled = false;
      let unsubscribe: (() => void) | undefined;
      const handleEvent = (event: CreationWorkspaceEvent) => {
        if (event.projectId !== projectId) return;
        const state = useCreationStore.getState();
        // 任何已提交变更都会刷新导航元数据（章节/场景标题、revision、项目 updatedAt）。
        void loadNavigation(projectId);
        // 若该项目已加载过大纲，结构命令（新建/改名/排序/拆并/状态）后同步刷新大纲树。
        if (state.outlines[projectId]) void loadOutline(projectId);
        // 卡片/关系变更时刷新卡片列表（跨视图同步，如看板拖拽改类型、其他视图外部修改）。
        if (
          (state.cardTypes.length > 0 || state.cards.length > 0) &&
          event.changes.some((change) => change.entity === "card" || change.entity === "cardRelation")
        ) {
          void loadCards({ projectId });
        }
        for (const change of event.changes) {
          if (change.entity !== "scene") continue;
          if (change.action === "deleted") continue;
          const current = state.sceneViews[change.id];
          const isOwnSave = inFlightSceneSaves.has(change.id);
          // 自身保存事件（保存响应到达前）不覆盖本地正文；已保存过的 revision 也不重取。
          if (!isOwnSave && (!current || change.revision > current.revision)) {
            void loadScene(change.id);
          }
        }
      };
      void watchProject(projectId, handleEvent)
        .then((unsub) => {
          if (cancelled) unsub();
          else {
            unsubscribe = unsub;
            useCreationStore.getState().setWatchConnected(true);
          }
        })
        .catch((error) => {
          useCreationStore.getState().setWatchConnected(false);
          setError(messageFromError(error));
        });
      return () => {
        cancelled = true;
        unsubscribe?.();
        useCreationStore.getState().setWatchConnected(false);
      };
    },
    [loadNavigation, loadOutline, loadCards, loadScene, setError]
  );

  // ---- Phase 1 P1 深模块 seam 接入 ----

  const runSnapshotRetention = useCallback(async (): Promise<SnapshotRetentionResult | null> => {
    try {
      return await snapshotRetentionRun();
    } catch (error) {
      setError(messageFromError(error));
      return null;
    }
  }, [setError]);

  const openCardImport = useCallback(async (): Promise<CardImportSource | null> => {
    try {
      return await cardImportOpenAndParse();
    } catch (error) {
      setError(messageFromError(error));
      return null;
    }
  }, [setError]);

  const parseCardImport = useCallback(
    async (input: { text: string; format: "csv" | "markdown" }): Promise<CardImportPreview | null> => {
      try {
        return await cardImportParse(input);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const loadCardImportSchema = useCallback(
    async (projectId: string): Promise<CardImportSchemaContext | null> => {
      try {
        return await cardImportSchema(projectId);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const planCardImport = useCallback(
    async (input: CardImportApplyInput): Promise<CardImportPlan | null> => {
      try {
        return await cardImportPlan(input);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const applyCardImport = useCallback(
    async (input: CardImportApplyInput): Promise<CardImportApplyResult | null> => {
      try {
        return await cardImportApply(input);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const exportCards = useCallback(
    async (input: {
      projectId: string;
      filter: CardExportFilter;
      format: "csv" | "markdown";
    }): Promise<CardExportResult | null> => {
      try {
        return await cardExportOpenAndWrite(input);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const createReplacePlan = useCallback(
    async (query: ReplacePlanQuery): Promise<ReplacePlan | null> => {
      try {
        return await replacePlanCreate(query);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const applyReplacePlan = useCallback(
    async (input: { planId: string; excludedHitIds: string[] }): Promise<ReplaceApplyOutcome | null> => {
      try {
        return await replacePlanApply(input);
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  const updateSession = useCallback(
    async (command: Omit<SessionUpdateCommand, "type">): Promise<boolean> => {
      try {
        await sessionUpdateRequest({ type: "session.update", ...command });
        return true;
      } catch (error) {
        setError(messageFromError(error));
        return false;
      }
    },
    [setError]
  );

  const updateProjectGoal = useCallback(
    async (command: Omit<ProjectUpdateGoalCommand, "type">): Promise<ProjectGoalResult | null> => {
      try {
        return await projectUpdateGoalRequest({ type: "project.updateGoal", ...command });
      } catch (error) {
        setError(messageFromError(error));
        return null;
      }
    },
    [setError]
  );

  return {
    loadProjects,
    loadProjectHome,
    createProject,
    loadNavigation,
    loadOutline,
    loadScene,
    saveSceneBody,
    refreshProject,
    runStructure,
    previewStructure,
    applyStructureWithProtection,
    revertStructure,
    loadCardTypes,
    loadRelationTypes,
    loadCards,
    loadCardRelations,
    readCard,
    loadTrash,
    restoreTrash,
    purgeTrash,
    loadSnapshots,
    exportDraft,
    search,
    replacePreview,
    replaceApply,
    loadStats,
    loadSessions,
    reportSession,
    deleteSession,
    runProof,
    loadMigrationStatus,
    runMigration,
    loadInbox,
    loadInboxCount,
    updateInbox,
    deleteInbox,
    createInbox,
    previewDraftImport,
    exportBundle,
    importBundle,
    loadAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
    reanchorAnnotation,
    updateCardType,
    deleteCardType,
    updateRelationType,
    deleteRelationType,
    previewSnapshot,
    restoreSnapshotWithProtection,
    loadTrashImpact,
    loadResources,
    attachResource,
    detachResource,
    loadProjectExport,
    subscribeProject,
    // ---- Phase 1 P1 深模块 seam 接入 ----
    runSnapshotRetention,
    openCardImport,
    parseCardImport,
    loadCardImportSchema,
    planCardImport,
    applyCardImport,
    exportCards,
    createReplacePlan,
    applyReplacePlan,
    updateSession,
    updateProjectGoal
  };
}
