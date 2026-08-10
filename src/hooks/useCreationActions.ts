import { useCallback } from "react";
import {
  cardRead,
  cardRelations,
  cardsList,
  cardTypesList,
  createProject as createProjectRequest,
  exportDraft as exportDraftRequest,
  listProjects,
  readProjectNavigation,
  readProjectOutline,
  readSceneBody,
  relationTypesList,
  replaceApply as replaceApplyRequest,
  replacePreview as replacePreviewRequest,
  runStructure as runStructureRequest,
  search as searchRequest,
  sessionDelete as sessionDeleteRequest,
  sessionList as sessionListRequest,
  sessionReport as sessionReportRequest,
  snapshotList,
  statsView as statsViewRequest,
  proofQuery as proofQueryRequest,
  migrationStatus as migrationStatusRequest,
  migrationRun as migrationRunRequest,
  inboxList as inboxListRequest,
  inboxUpdate as inboxUpdateRequest,
  inboxDelete as inboxDeleteRequest,
  importDraftPreview as importDraftPreviewRequest,
  exportProjectBundle as exportProjectBundleRequest,
  importProjectBundle as importProjectBundleRequest,
  annotationList as annotationListRequest,
  annotationCreate as annotationCreateRequest,
  annotationUpdate as annotationUpdateRequest,
  annotationDelete as annotationDeleteRequest,
  trashList,
  updateSceneBody,
  watchProject
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CardCommand,
  CardRelationCreateCommand,
  CardsListQuery,
  CreateProjectInput,
  CreationDocument,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationSearchQuery,
  CreationSearchView,
  CreationWorkspaceEvent,
  HistoryCommand,
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
  InboxUpdateCommand,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  DraftImportPreview,
  ProjectImportDraftCommand,
  ProjectBundleImportResult,
  Annotation,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationUpdateCommand,
  StructureCommand,
  TrashEntityKind
} from "@/types/creation";
import { messageFromError } from "@/utils/format";

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
    async (projectId: string): Promise<CreationProjectNavigation | null | undefined> => {
      try {
        const navigation = await readProjectNavigation(projectId);
        if (navigation) useCreationStore.getState().setNavigation(projectId, navigation);
        return navigation;
      } catch (error) {
        setError(messageFromError(error));
        return undefined;
      }
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
    async (command: StructureCommand | CardCommand | HistoryCommand | ProjectImportDraftCommand): Promise<boolean> => {
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

  const loadCardTypes = useCallback(
    async (projectId: string): Promise<void> => {
      try {
        useCreationStore.getState().setCardTypes(await cardTypesList(projectId));
      } catch (error) {
        setError(messageFromError(error));
      }
    },
    [setError]
  );

  const loadRelationTypes = useCallback(
    async (projectId: string): Promise<void> => {
      try {
        useCreationStore.getState().setRelationTypes(await relationTypesList(projectId));
      } catch (error) {
        setError(messageFromError(error));
      }
    },
    [setError]
  );

  const loadCards = useCallback(
    async (query: Omit<CardsListQuery, "kind">): Promise<void> => {
      useCreationStore.getState().setCardsLoading(true);
      try {
        useCreationStore.getState().setCards(await cardsList({ kind: "cards.list", ...query }));
      } catch (error) {
        setError(messageFromError(error));
      } finally {
        useCreationStore.getState().setCardsLoading(false);
      }
    },
    [setError]
  );

  const loadCardRelations = useCallback(
    async (cardId: string): Promise<void> => {
      try {
        useCreationStore.getState().setCardRelations(cardId, await cardRelations(cardId));
      } catch (error) {
        setError(messageFromError(error));
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
    async (projectId: string): Promise<{ canceled: boolean; filePath: string | null }> => {
      try {
        return await exportDraftRequest(projectId);
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

  return {
    loadProjects,
    createProject,
    loadNavigation,
    loadOutline,
    loadScene,
    saveSceneBody,
    refreshProject,
    runStructure,
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
    updateInbox,
    deleteInbox,
    previewDraftImport,
    exportBundle,
    importBundle,
    loadAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
    subscribeProject
  };
}
