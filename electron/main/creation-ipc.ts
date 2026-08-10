import { ipcMain, dialog, BrowserWindow, type WebContents } from "electron";
import { randomUUID } from "node:crypto";
import { writeFile, mkdir, rm, readFile, stat } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";
import type {
  CardRelation,
  CardSummary,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectListener,
  CreationProjectNavigation,
  CreationProjectOutline,
  CreationProjectSummary,
  CreationSearchQuery,
  CreationSearchView,
  CreationStructureResult,
  ProjectExportView,
  RelationType,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewQuery,
  ReplacePreviewView,
  SceneBodyView,
  SceneSaveResponse,
  SessionEntry,
  SessionListQuery,
  SessionReportCommand,
  SessionReportResult,
  SnapshotInfo,
  SnapshotListQuery,
  StructureCommand,
  TrashItem,
  UpdateSceneBodyInput,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxListQuery,
  InboxUpdateCommand,
  ProjectBundleData,
  ProjectBundleImportCommand,
  ProjectBundleImportResult,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  ResourceAttachCommand,
  ResourceDetachCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult
} from "../../src/types/creation";
import { CreationWorkspaceError } from "./creation-workspace";
import type { CreationCoordinator } from "./creation-coordinator";
import { getLegacyMigrationStatus, runLegacyMigration } from "./creation-migration";
import { previewLegacyDraft } from "./creation-import";

export interface CreationIpcContext {
  resolveDataRoot: () => string;
  resolveLibraryRoot: () => string;
}

/** 把导出视图组装成平台发布净文本：卷/章标题 + 场景正文，空行分隔。 */
function buildExportText(view: ProjectExportView): string {
  const lines: string[] = [];
  for (const volume of view.volumes) {
    if (view.volumes.length > 1) {
      lines.push(volume.title, "");
    }
    for (const chapter of volume.chapters) {
      const heading = [chapter.displayNumber, chapter.title].filter(Boolean).join(" ");
      lines.push(heading, "");
      for (const scene of chapter.scenes) {
        if (scene.title && scene.title !== "默认场景") lines.push(scene.title, "");
        if (scene.text) lines.push(scene.text, "");
      }
      lines.push("");
    }
  }
  return `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trim()}\n`;
}

interface WatchEntry {
  subscriptionId: string;
  sender: WebContents;
  unsubscribe: () => void;
}

/**
 * creation 域的全部 IPC 通道。renderer 只拿到 invoke 面；watch 推送由主进程
 * 按 subscriptionId 转发 `creation:event`，订阅随 sender 销毁自动退订。
 */
export function registerCreationIpc(coordinator: CreationCoordinator, context: CreationIpcContext): void {
  const watchEntries = new Map<string, WatchEntry>();

  ipcMain.handle("creation:migrationStatus", async () => {
    const dataRoot = context.resolveDataRoot();
    if (!dataRoot) return null;
    return getLegacyMigrationStatus({ dataRoot, libraryRoot: context.resolveLibraryRoot() });
  });

  ipcMain.handle("creation:migrationRun", async () => {
    const dataRoot = context.resolveDataRoot();
    if (!dataRoot) throw new CreationWorkspaceError("invalid-input", "旧数据目录不可用。");
    return coordinator.withWorkspaceClosed(() =>
      runLegacyMigration({ dataRoot, libraryRoot: context.resolveLibraryRoot() })
    );
  });

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

  ipcMain.handle("creation:cardsList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as CardsListQuery) as Promise<CardSummary[]>)
  );

  ipcMain.handle("creation:cardRead", (_event, cardId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "card.read", cardId }))
  );

  ipcMain.handle("creation:cardTypesList", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "cardTypes.list", projectId }) as Promise<CardType[]>)
  );

  ipcMain.handle("creation:relationTypesList", (_event, projectId: string) =>
    coordinator.withWorkspace(
      (workspace) => workspace.read({ kind: "relationTypes.list", projectId }) as Promise<RelationType[]>
    )
  );

  ipcMain.handle("creation:cardRelations", (_event, cardId: string) =>
    coordinator.withWorkspace(
      (workspace) =>
        workspace.read({ kind: "card.relations", cardId }) as Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>
    )
  );

  ipcMain.handle("creation:trashList", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "trash.list", projectId }))
  );

  ipcMain.handle("creation:snapshotList", (_event, query: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.read(query as SnapshotListQuery) as Promise<SnapshotInfo[]>
    )
  );

  ipcMain.handle("creation:search", (_event, query: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.read(query as CreationSearchQuery) as Promise<CreationSearchView>
    )
  );

  ipcMain.handle("creation:replacePreview", (_event, query: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.read(query as ReplacePreviewQuery) as Promise<ReplacePreviewView>
    )
  );

  ipcMain.handle("creation:replaceApply", (_event, command: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.transact(command as ReplaceApplyCommand) as Promise<ReplaceApplyResult>
    )
  );

  ipcMain.handle("creation:statsView", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "stats.view", projectId }))
  );

  ipcMain.handle("creation:sessionList", (_event, query: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.read(query as SessionListQuery) as Promise<SessionEntry[]>
    )
  );

  ipcMain.handle("creation:sessionReport", (_event, command: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.transact(command as SessionReportCommand) as Promise<SessionReportResult>
    )
  );

  ipcMain.handle("creation:sessionDelete", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as SessionReportCommand))
  );

  ipcMain.handle("creation:proofQuery", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as ProofQuery) as Promise<ProofView>)
  );

  ipcMain.handle("creation:inboxList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as InboxListQuery) as Promise<InboxItem[]>)
  );

  ipcMain.handle("creation:inboxUpdate", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as InboxUpdateCommand))
  );

  ipcMain.handle("creation:inboxDelete", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as InboxDeleteCommand))
  );

  ipcMain.handle("creation:annotationList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as AnnotationListQuery) as Promise<unknown>)
  );

  ipcMain.handle("creation:annotationCreate", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as AnnotationCreateCommand) as Promise<AnnotationResult>)
  );

  ipcMain.handle("creation:annotationUpdate", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as AnnotationUpdateCommand) as Promise<AnnotationResult>)
  );

  ipcMain.handle("creation:annotationDelete", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as AnnotationDeleteCommand) as Promise<AnnotationResult>)
  );

  ipcMain.handle("creation:resourceList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as ResourceListQuery) as Promise<ResourceInfo[]>)
  );

  ipcMain.handle("creation:attachResource", async (event, input: { projectId?: unknown; cardId?: unknown }) => {
    const projectId = typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    const cardId = typeof input?.cardId === "string" && input.cardId.trim() ? input.cardId : undefined;
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "作品读取请求无效。");
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "选择附件",
      properties: ["openFile" as const]
    };
    const { canceled, filePaths } = parent ? await dialog.showOpenDialog(parent, options) : await dialog.showOpenDialog(options);
    if (canceled || filePaths.length === 0) return { canceled: true, resource: null };
    const sourcePath = filePaths[0]!;
    const sourceInfo = await stat(sourcePath);
    if (!sourceInfo.isFile() || sourceInfo.size > 500 * 1024 * 1024) {
      throw new CreationWorkspaceError("invalid-input", "附件大小超出允许范围（最大 500MB）。");
    }
    const buffer = await readFile(sourcePath);
    const sha256 = createHash("sha256").update(buffer).digest("hex");
    const fileName = path.basename(sourcePath);
    const relativePath = `resources/${projectId}/${randomUUID()}-${fileName}`;
    const targetPath = path.join(context.resolveDataRoot(), "CreationWorkspace", relativePath);
    await mkdir(path.dirname(targetPath), { recursive: true });
    await writeFile(targetPath, buffer);
    try {
      const result = await coordinator.withWorkspace((workspace) =>
        workspace.transact({
          type: "resource.attach",
          projectId,
          cardId,
          relativePath,
          sha256,
          size: buffer.length,
          originalName: fileName
        } as ResourceAttachCommand) as Promise<ResourceResult>
      );
      return { canceled: false, resource: result };
    } catch (error) {
      await rm(targetPath, { force: true });
      throw error;
    }
  });

  ipcMain.handle("creation:detachResource", async (_event, command: unknown) => {
    const resourceId = typeof (command as { resourceId?: unknown }).resourceId === "string"
      ? (command as { resourceId: string }).resourceId
      : "";
    if (!resourceId) throw new CreationWorkspaceError("invalid-input", "附件读取请求无效。");
    const result = await coordinator.withWorkspace((workspace) =>
      workspace.transact({ type: "resource.detach", resourceId } as ResourceDetachCommand) as Promise<ResourceResult>
    );
    if (result.relativePath) {
      await rm(path.join(context.resolveDataRoot(), "CreationWorkspace", result.relativePath), { force: true });
    }
    return result;
  });

  ipcMain.handle("creation:importDraftPreview", async (event) => {
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "导入旧稿",
      properties: ["openFile" as const],
      filters: [
        { name: "文本与 Markdown", extensions: ["txt", "md", "markdown"] },
        { name: "所有文件", extensions: ["*"] }
      ]
    };
    const { canceled, filePaths } = parent ? await dialog.showOpenDialog(parent, options) : await dialog.showOpenDialog(options);
    if (canceled || filePaths.length === 0) return null;
    return previewLegacyDraft({ filePath: filePaths[0]! });
  });

  ipcMain.handle("creation:exportProjectBundle", async (event, input: { projectId?: unknown }) => {
    const projectId = typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "作品读取请求无效。");
    const data = (await coordinator.withWorkspace((workspace) =>
      workspace.read({ kind: "project.bundle.export", projectId })
    )) as ProjectBundleData | null;
    if (!data) throw new CreationWorkspaceError("not-found", "作品不存在。");
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "导出项目包",
      properties: ["openDirectory" as const, "createDirectory" as const]
    };
    const { canceled, filePaths } = parent ? await dialog.showOpenDialog(parent, options) : await dialog.showOpenDialog(options);
    if (canceled || filePaths.length === 0) return { canceled: true, directory: null };
    const directory = path.join(filePaths[0]!, `项目包-${data.project.title}`);
    await mkdir(directory, { recursive: true });
    await writeFile(path.join(directory, "manifest.json"), `${JSON.stringify({
      formatVersion: 1,
      projectTitle: data.project.title,
      exportedAt: data.exportedAt,
      counts: data.counts
    }, null, 2)}\n`, "utf8");
    await writeFile(path.join(directory, "project.json"), `${JSON.stringify(data, null, 2)}\n`, "utf8");
    return { canceled: false, directory };
  });

  ipcMain.handle("creation:importProjectBundle", async (event) => {
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "导入项目包",
      properties: ["openDirectory" as const]
    };
    const { canceled, filePaths } = parent ? await dialog.showOpenDialog(parent, options) : await dialog.showOpenDialog(options);
    if (canceled || filePaths.length === 0) return { canceled: true, result: null };
    const directory = filePaths[0]!;
    const { readFile } = await import("node:fs/promises");
    const data = JSON.parse(await readFile(path.join(directory, "project.json"), "utf8")) as ProjectBundleData;
    if (data.formatVersion !== 1) throw new CreationWorkspaceError("invalid-input", "项目包格式版本不受支持。");
    const result = await coordinator.withWorkspace((workspace) =>
      workspace.transact({ type: "project.bundle.import", data } as ProjectBundleImportCommand) as Promise<ProjectBundleImportResult>
    );
    return { canceled: false, result };
  });

  ipcMain.handle("creation:exportDraft", async (event, input: { projectId?: unknown }) => {
    const projectId =
      typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "作品读取请求无效。");
    const view = (await coordinator.withWorkspace((workspace) =>
      workspace.read({ kind: "project.export", projectId })
    )) as ProjectExportView | null;
    if (!view) throw new CreationWorkspaceError("not-found", "作品不存在。");
    const text = buildExportText(view);
    const options = {
      title: "导出成稿",
      defaultPath: `${view.title}.txt`,
      filters: [{ name: "文本文件", extensions: ["txt"] }]
    };
    const parent = BrowserWindow.fromWebContents(event.sender);
    const { canceled, filePath } = parent
      ? await dialog.showSaveDialog(parent, options)
      : await dialog.showSaveDialog(options);
    if (canceled || !filePath) return { canceled: true, filePath: null };
    await writeFile(filePath, text, "utf8");
    return { canceled: false, filePath };
  });

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
