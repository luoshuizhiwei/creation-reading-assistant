import { useCallback } from "react";
import {
  createProject as createProjectRequest,
  exportDraft as exportDraftRequest,
  exportProjectBundle as exportProjectBundleRequest,
  importDraftPreview as importDraftPreviewRequest,
  importProjectBundle as importProjectBundleRequest,
  listProjects,
  migrationRun as migrationRunRequest,
  migrationStatus as migrationStatusRequest,
  projectExport as projectExportRequest,
  projectUpdateGoal as projectUpdateGoalRequest,
  readProjectHome
} from "@/services/creation-service";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import type {
  CreateProjectInput,
  CreationProjectNavigation,
  DraftExportPreset,
  DraftImportPreview,
  LegacyMigrationReport,
  LegacyMigrationStatus,
  ProjectBundleImportResult,
  ProjectExportView,
  ProjectGoalResult,
  ProjectHomeView,
  ProjectUpdateGoalCommand
} from "@/types/creation";
import { executeAction } from "@/utils/async-action";

export function useProjectActions() {
  const setProjects = useCreationStore((state) => state.setProjects);
  const setLoading = useCreationStore((state) => state.setLoading);
  const setError = useAppStore((state) => state.setError);

  const loadProjects = useCallback(async () => {
    return executeAction(
      () => listProjects(),
      { setLoading, setError, onSuccess: (projects) => setProjects(projects) }
    );
  }, [setError, setLoading, setProjects]);

  const loadProjectHome = useCallback(async (): Promise<ProjectHomeView> => {
    const res = await executeAction(() => readProjectHome(), { setError });
    return res ?? { projects: [] };
  }, [setError]);

  const createProject = useCallback(
    async (input: CreateProjectInput): Promise<CreationProjectNavigation | undefined> => {
      return executeAction(
        async () => {
          const navigation = await createProjectRequest(input);
          useCreationStore.getState().upsertNavigation(navigation);
          return navigation;
        },
        { setLoading, setError }
      );
    },
    [setError, setLoading]
  );

  const loadProjectExport = useCallback(
    async (projectId: string): Promise<ProjectExportView | null> => {
      const res = await executeAction(() => projectExportRequest(projectId), { setError });
      return res ?? null;
    },
    [setError]
  );

  const exportBundle = useCallback(
    async (projectId: string): Promise<{ canceled: boolean; directory: string | null }> => {
      const res = await executeAction(() => exportProjectBundleRequest(projectId), { setError });
      return res ?? { canceled: true, directory: null };
    },
    [setError]
  );

  const importBundle = useCallback(
    async (): Promise<{ canceled: boolean; result: ProjectBundleImportResult | null }> => {
      const res = await executeAction(() => importProjectBundleRequest(), { setError });
      return res ?? { canceled: true, result: null };
    },
    [setError]
  );

  const loadMigrationStatus = useCallback(async (): Promise<LegacyMigrationStatus | null> => {
    const res = await executeAction(() => migrationStatusRequest(), { setError });
    return res ?? null;
  }, [setError]);

  const runMigration = useCallback(async (): Promise<LegacyMigrationReport | null> => {
    const res = await executeAction(() => migrationRunRequest(), { setError });
    return res ?? null;
  }, [setError]);

  const previewDraftImport = useCallback(async (): Promise<DraftImportPreview | null> => {
    const res = await executeAction(() => importDraftPreviewRequest(), { setError });
    return res ?? null;
  }, [setError]);

  const exportDraft = useCallback(
    async (projectId: string, preset: DraftExportPreset): Promise<{ canceled: boolean; filePath: string | null }> => {
      const res = await executeAction(() => exportDraftRequest(projectId, preset), { setError });
      return res ?? { canceled: true, filePath: null };
    },
    [setError]
  );

  const updateProjectGoal = useCallback(
    async (command: Omit<ProjectUpdateGoalCommand, "type">): Promise<ProjectGoalResult | null> => {
      const res = await executeAction(
        () => projectUpdateGoalRequest({ type: "project.updateGoal", ...command }),
        { setError }
      );
      return res ?? null;
    },
    [setError]
  );

  return {
    loadProjects,
    loadProjectHome,
    createProject,
    loadProjectExport,
    exportBundle,
    importBundle,
    loadMigrationStatus,
    runMigration,
    previewDraftImport,
    exportDraft,
    updateProjectGoal
  };
}
