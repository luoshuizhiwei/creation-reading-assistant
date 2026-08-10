import { useCallback } from "react";
import {
  createProject as createProjectRequest,
  listProjects,
  readProjectNavigation,
  readSceneBody,
  updateSceneBody,
  watchProject
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CreateProjectInput,
  CreationDocument,
  CreationProjectNavigation,
  CreationWorkspaceEvent,
  SceneSaveResponse
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
    [loadNavigation, loadScene, setError]
  );

  return { loadProjects, createProject, loadNavigation, loadScene, saveSceneBody, refreshProject, subscribeProject };
}
