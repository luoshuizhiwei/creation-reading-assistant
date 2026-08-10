import { ipcMain, type WebContents } from "electron";
import { randomUUID } from "node:crypto";
import type {
  CreateProjectInput,
  CreationProjectListener,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationStructureResult,
  SceneBodyView,
  SceneSaveResponse,
  StructureCommand,
  UpdateSceneBodyInput
} from "../../src/types/creation";
import { CreationWorkspaceError } from "./creation-workspace";
import type { CreationCoordinator } from "./creation-coordinator";

interface WatchEntry {
  subscriptionId: string;
  sender: WebContents;
  unsubscribe: () => void;
}

/**
 * creation 域的全部 IPC 通道。renderer 只拿到 invoke 面；watch 推送由主进程
 * 按 subscriptionId 转发 `creation:event`，订阅随 sender 销毁自动退订。
 */
export function registerCreationIpc(coordinator: CreationCoordinator): void {
  const watchEntries = new Map<string, WatchEntry>();

  ipcMain.handle("creation:listProjects", () =>
    coordinator.withWorkspace(
      (workspace) => workspace.read({ kind: "projects.list" }) as Promise<CreationProjectSummary[]>
    )
  );

  ipcMain.handle("creation:readProjectNavigation", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "project.navigation", projectId }))
  );

  ipcMain.handle("creation:createProject", (_event, input: CreateProjectInput) =>
    coordinator.withWorkspace(async (workspace) => {
      const { title, ...setup } = (input ?? {}) as CreateProjectInput;
      const created = await workspace.transact({ type: "project.create", title, setup });
      const navigation = await workspace.read({ kind: "project.navigation", projectId: created.projectId });
      return navigation as CreationProjectNavigation;
    })
  );

  ipcMain.handle("creation:readSceneBody", (_event, sceneId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "scene.body", sceneId }))
  );

  ipcMain.handle("creation:readProjectOutline", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "project.outline", projectId }))
  );

  ipcMain.handle("creation:runStructure", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as StructureCommand) as Promise<CreationStructureResult>)
  );

  ipcMain.handle("creation:updateSceneBody", (_event, input: UpdateSceneBodyInput) =>
    coordinator.withWorkspace(async (workspace): Promise<SceneSaveResponse> => {
      const { sceneId, baseRevision, body } = (input ?? {}) as UpdateSceneBodyInput;
      try {
        const result = await workspace.transact({ type: "scene.updateBody", sceneId, baseRevision, body });
        return { ok: true, result };
      } catch (error) {
        if (error instanceof CreationWorkspaceError) {
          let currentRevision: number | undefined;
          if (error.code === "revision-mismatch") {
            const view = await workspace.read({ kind: "scene.body", sceneId });
            currentRevision = view?.revision;
          }
          return { ok: false, error: { code: error.code, message: error.message, currentRevision } };
        }
        return {
          ok: false,
          error: { code: "integrity", message: "无法保存场景正文。" }
        };
      }
    })
  );

  ipcMain.handle("creation:watchProject", async (event, projectId: string) => {
    const subscriptionId = `creation-watch-${randomUUID()}`;
    const sender = event.sender;
    const listener: CreationProjectListener = (workspaceEvent) => {
      if (sender.isDestroyed()) return;
      sender.send("creation:event", { subscriptionId, event: workspaceEvent });
    };
    const unsubscribe = await coordinator.watch({ projectId }, listener);
    watchEntries.set(subscriptionId, { subscriptionId, sender, unsubscribe });
    sender.once("destroyed", () => {
      const entry = watchEntries.get(subscriptionId);
      if (entry) {
        watchEntries.delete(subscriptionId);
        entry.unsubscribe();
      }
    });
    return { subscriptionId };
  });

  ipcMain.handle("creation:unwatchProject", (_event, input: { subscriptionId?: unknown }) => {
    const subscriptionId = typeof input?.subscriptionId === "string" ? input.subscriptionId : "";
    const entry = watchEntries.get(subscriptionId);
    if (!entry) return;
    watchEntries.delete(subscriptionId);
    entry.unsubscribe();
  });
}
