import { useCallback } from "react";
import {
  readProjectNavigation,
  readProjectOutline,
  runStructure as runStructureRequest,
  structureApply as structureApplyRequest,
  structurePreview as structurePreviewRequest,
  structureRevert as structureRevertRequest
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationRunCommand,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructurePreviewCommand,
  StructurePreviewView,
  StructureRevertCommand,
  StructureRevertResult
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";
import { messageFromError } from "@/utils/format";

/**
 * navigation 请求按 projectId 去重：同一项目同一时刻只允许一个 readProjectNavigation 请求。
 * 普通页面加载与导航请求共享同一 in-flight Promise，避免重复读取。
 */
const navigationInFlight = new Map<string, Promise<CreationProjectNavigation | null | undefined>>();

export function useOutlineActions() {
  const setError = useAppStore((state) => state.setError);

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
      return executeAction(
        async () => {
          const outline = await readProjectOutline(projectId);
          if (outline) useCreationStore.getState().setOutline(projectId, outline);
          return outline;
        },
        { setError }
      );
    },
    [setError]
  );

  const runStructure = useCallback(
    async (command: CreationRunCommand): Promise<boolean> => {
      return executeBoolAction(() => runStructureRequest(command), { setError });
    },
    [setError]
  );

  const previewStructure = useCallback(
    async (command: StructurePreviewCommand): Promise<StructurePreviewView | null> => {
      const res = await executeAction(() => structurePreviewRequest(command), { setError });
      return res ?? null;
    },
    [setError]
  );

  const applyStructureWithProtection = useCallback(
    async (command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult | null> => {
      const res = await executeAction(() => structureApplyRequest(command), { setError });
      return res ?? null;
    },
    [setError]
  );

  const revertStructure = useCallback(
    async (command: StructureRevertCommand): Promise<StructureRevertResult | null> => {
      const res = await executeAction(() => structureRevertRequest(command), { setError });
      return res ?? null;
    },
    [setError]
  );

  const refreshProject = useCallback(
    async (projectId: string): Promise<CreationProjectNavigation | null | undefined> => {
      return loadNavigation(projectId);
    },
    [loadNavigation]
  );

  return {
    loadNavigation,
    loadOutline,
    runStructure,
    previewStructure,
    applyStructureWithProtection,
    revertStructure,
    refreshProject
  };
}
