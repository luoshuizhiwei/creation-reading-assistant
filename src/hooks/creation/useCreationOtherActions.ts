import { useCallback } from "react";
import {
  attachResource as attachResourceRequest,
  detachResource as detachResourceRequest,
  replaceApply as replaceApplyRequest,
  replacePlanApply,
  replacePlanCreate,
  replacePreview as replacePreviewRequest,
  resourceList as resourceListRequest,
  runStructure as runStructureRequest,
  search as searchRequest,
  sessionDelete as sessionDeleteRequest,
  sessionList as sessionListRequest,
  sessionReport as sessionReportRequest,
  sessionUpdate as sessionUpdateRequest,
  statsView as statsViewRequest,
  trashImpact as trashImpactRequest,
  trashList
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import type {
  CreationSearchQuery,
  CreationSearchView,
  ReplaceApplyCommand,
  ReplaceApplyOutcome,
  ReplacePlan,
  ReplacePlanQuery,
  ReplacePreviewQuery,
  ReplacePreviewView,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  SessionDeleteCommand,
  SessionListQuery,
  SessionReportCommand,
  SessionUpdateCommand,
  TrashEntityKind,
  TrashImpactQuery,
  TrashImpactView
} from "@/types/creation";
import { executeAction, executeBoolAction } from "@/utils/async-action";

export function useCreationOtherActions() {
  const setError = useAppStore((state) => state.setError);

  const loadTrash = useCallback(
    async (projectId?: string) => {
      const res = await executeAction(() => trashList(projectId), { setError });
      return res ?? [];
    },
    [setError]
  );

  const restoreTrash = useCallback(
    async (projectId: string | undefined, entity: TrashEntityKind, entityId: string): Promise<boolean> => {
      return executeBoolAction(
        () => runStructureRequest({ type: "trash.restore", projectId, entity, entityId }),
        { setError }
      );
    },
    [setError]
  );

  const purgeTrash = useCallback(
    async (projectId: string | undefined, entity: TrashEntityKind, entityId: string): Promise<boolean> => {
      return executeBoolAction(
        () => runStructureRequest({ type: "trash.purge", projectId, entity, entityId }),
        { setError }
      );
    },
    [setError]
  );

  const loadTrashImpact = useCallback(
    async (query: TrashImpactQuery): Promise<TrashImpactView | null> => {
      const res = await executeAction(() => trashImpactRequest(query), { setError });
      return res ?? null;
    },
    [setError]
  );

  const loadResources = useCallback(
    async (query: Omit<ResourceListQuery, "kind">): Promise<ResourceInfo[]> => {
      const res = await executeAction(
        () => resourceListRequest({ kind: "resource.list", ...query }),
        { setError }
      );
      return res ?? [];
    },
    [setError]
  );

  const attachResource = useCallback(
    async (projectId: string | undefined, cardId?: string, role?: "attachment" | "cover"): Promise<{ canceled: boolean; resource: ResourceResult | null }> => {
      const res = await executeAction(() => attachResourceRequest(projectId, cardId, role), { setError });
      return res ?? { canceled: true, resource: null };
    },
    [setError]
  );

  const detachResource = useCallback(
    async (resourceId: string): Promise<boolean> => {
      return executeBoolAction(() => detachResourceRequest(resourceId), { setError });
    },
    [setError]
  );

  const search = useCallback(
    async (query: Omit<CreationSearchQuery, "kind">): Promise<CreationSearchView> => {
      const res = await executeAction(
        () => searchRequest({ kind: "search.query", ...query }),
        { setError }
      );
      return res ?? { query: query.text, hits: [], total: 0 };
    },
    [setError]
  );

  const replacePreview = useCallback(
    async (query: Omit<ReplacePreviewQuery, "kind">): Promise<ReplacePreviewView> => {
      const res = await executeAction(
        () => replacePreviewRequest({ kind: "replace.preview", ...query }),
        { setError }
      );
      return (
        res ?? {
          projectId: query.projectId,
          find: query.find,
          replaceWith: query.replaceWith,
          scope: query.scope,
          sceneHits: [],
          totalHits: 0,
          matchedScenes: 0
        }
      );
    },
    [setError]
  );

  const replaceApply = useCallback(
    async (command: ReplaceApplyCommand): Promise<boolean> => {
      return executeBoolAction(() => replaceApplyRequest(command), { setError });
    },
    [setError]
  );

  const createReplacePlan = useCallback(
    async (query: ReplacePlanQuery): Promise<ReplacePlan | null> => {
      const res = await executeAction(() => replacePlanCreate(query), { setError });
      return res ?? null;
    },
    [setError]
  );

  const applyReplacePlan = useCallback(
    async (input: { planId: string; excludedHitIds: string[] }): Promise<ReplaceApplyOutcome | null> => {
      const res = await executeAction(() => replacePlanApply(input), { setError });
      return res ?? null;
    },
    [setError]
  );

  const loadStats = useCallback(
    async (projectId: string) => {
      const res = await executeAction(() => statsViewRequest(projectId), { setError });
      return res ?? null;
    },
    [setError]
  );

  const loadSessions = useCallback(
    async (query: Omit<SessionListQuery, "kind">) => {
      const res = await executeAction(
        () => sessionListRequest({ kind: "session.list", ...query }),
        { setError }
      );
      return res ?? [];
    },
    [setError]
  );

  const reportSession = useCallback(
    async (command: Omit<SessionReportCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => sessionReportRequest({ type: "session.report", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const deleteSession = useCallback(
    async (command: Omit<SessionDeleteCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => sessionDeleteRequest({ type: "session.delete", ...command }),
        { setError }
      );
    },
    [setError]
  );

  const updateSession = useCallback(
    async (command: Omit<SessionUpdateCommand, "type">): Promise<boolean> => {
      return executeBoolAction(
        () => sessionUpdateRequest({ type: "session.update", ...command }),
        { setError }
      );
    },
    [setError]
  );

  return {
    loadTrash,
    restoreTrash,
    purgeTrash,
    loadTrashImpact,
    loadResources,
    attachResource,
    detachResource,
    search,
    replacePreview,
    replaceApply,
    createReplacePlan,
    applyReplacePlan,
    loadStats,
    loadSessions,
    reportSession,
    deleteSession,
    updateSession
  };
}
