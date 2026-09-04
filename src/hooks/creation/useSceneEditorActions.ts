import { useCallback } from "react";
import {
  annotationCreate as annotationCreateRequest,
  annotationDelete as annotationDeleteRequest,
  annotationList as annotationListRequest,
  annotationReanchor as annotationReanchorRequest,
  annotationUpdate as annotationUpdateRequest,
  proofQuery as proofQueryRequest,
  readSceneBody,
  snapshotList,
  snapshotPreview as snapshotPreviewRequest,
  snapshotRestoreWithProtection as snapshotRestoreWithProtectionRequest,
  snapshotRetentionRun,
  updateSceneBody,
  watchProject
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  Annotation,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationReanchorCommand,
  AnnotationUpdateCommand,
  CreationDocument,
  CreationWorkspaceEvent,
  ProofQuery,
  ProofView,
  SceneSaveResponse,
  SnapshotListQuery,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotRestoreWithProtectionCommand,
  SnapshotRestoreWithProtectionResult,
  SnapshotRetentionResult
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";
import { messageFromError } from "@/utils/format";

/**
 * 正在进行中的场景保存（场景 ID 集合）。
 * watch 事件到达时，若命中该集合则视为“自身保存事件”，不覆盖本地正文，
 * 避免事件与保存响应乱序到达时把用户未提交内容冲掉。
 */
const inFlightSceneSaves = new Set<string>();

export interface UseSceneEditorActionsOptions {
  loadNavigation?: (projectId: string) => Promise<unknown>;
  loadOutline?: (projectId: string) => Promise<unknown>;
  loadCards?: (query: { projectId: string }) => Promise<unknown>;
}

export function useSceneEditorActions(options: UseSceneEditorActionsOptions = {}) {
  const { loadNavigation, loadOutline, loadCards } = options;
  const setError = useAppStore((state) => state.setError);

  const loadScene = useCallback(
    async (sceneId: string) => {
      const res = await executeAction(
        async () => {
          const view = await readSceneBody(sceneId);
          if (view) useCreationStore.getState().setSceneView(sceneId, view);
          return view;
        },
        { setError }
      );
      return res ?? undefined;
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

  const subscribeProject = useCallback(
    (projectId: string): (() => void) => {
      let cancelled = false;
      let unsubscribe: (() => void) | undefined;
      const handleEvent = (event: CreationWorkspaceEvent) => {
        if (event.projectId !== projectId) return;
        const state = useCreationStore.getState();
        // 任何已提交变更都会刷新导航元数据（章节/场景标题、revision、项目 updatedAt）。
        void loadNavigation?.(projectId);
        // 若该项目已加载过大纲，结构命令（新建/改名/排序/拆并/状态）后同步刷新大纲树。
        if (state.outlines[projectId]) void loadOutline?.(projectId);
        // 卡片/关系变更时刷新卡片列表（跨视图同步，如看板拖拽改类型、其他视图外部修改）。
        if (
          (state.cardTypes.length > 0 || state.cards.length > 0) &&
          event.changes.some((change) => change.entity === "card" || change.entity === "cardRelation")
        ) {
          void loadCards?.({ projectId });
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

  const loadSnapshots = useCallback(
    async (query: SnapshotListQuery) => {
      const res = await executeAction(() => snapshotList(query), { setError });
      return res ?? [];
    },
    [setError]
  );

  const previewSnapshot = useCallback(
    async (query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null> => {
      const res = await executeAction(() => snapshotPreviewRequest(query), { setError });
      return res ?? null;
    },
    [setError]
  );

  const restoreSnapshotWithProtection = useCallback(
    async (command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult | null> => {
      const res = await executeAction(() => snapshotRestoreWithProtectionRequest(command), { setError });
      return res ?? null;
    },
    [setError]
  );

  const runSnapshotRetention = useCallback(async (): Promise<SnapshotRetentionResult | null> => {
    const res = await executeAction(() => snapshotRetentionRun(), { setError });
    return res ?? null;
  }, [setError]);

  const runProof = useCallback(
    async (query: Omit<ProofQuery, "kind">): Promise<ProofView> => {
      const res = await executeAction(
        () => proofQueryRequest({ kind: "proof.query", ...query }),
        { setError }
      );
      return (
        res ?? {
          projectId: query.projectId,
          issues: [],
          scannedScenes: 0,
          affectedScenes: 0,
          total: 0
        }
      );
    },
    [setError]
  );

  const loadAnnotations = useCallback(
    async (query: Omit<AnnotationListQuery, "kind">): Promise<Annotation[]> => {
      const res = await executeAction(
        () => annotationListRequest({ kind: "annotation.list", ...query }),
        { setError }
      );
      return res ?? [];
    },
    [setError]
  );

  const createAnnotation = useCallback(
    async (command: Omit<AnnotationCreateCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => annotationCreateRequest({ type: "annotation.create", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const updateAnnotation = useCallback(
    async (command: Omit<AnnotationUpdateCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => annotationUpdateRequest({ type: "annotation.update", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const deleteAnnotation = useCallback(
    async (command: Omit<AnnotationDeleteCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => annotationDeleteRequest({ type: "annotation.delete", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const reanchorAnnotation = useCallback(
    async (command: AnnotationReanchorCommand): Promise<boolean> => {
      return executeBoolAction(
        () => annotationReanchorRequest(command),
        { setError }
      );
    },
    [setError]
  );

  return {
    loadScene,
    saveSceneBody,
    subscribeProject,
    loadSnapshots,
    previewSnapshot,
    restoreSnapshotWithProtection,
    runSnapshotRetention,
    runProof,
    loadAnnotations,
    createAnnotation,
    updateAnnotation,
    deleteAnnotation,
    reanchorAnnotation
  };
}
