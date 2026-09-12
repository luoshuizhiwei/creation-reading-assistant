import { ipcMain, dialog, BrowserWindow, type WebContents } from "electron";
import { randomUUID } from "node:crypto";
import { writeFile, mkdir, rm, readFile, stat } from "node:fs/promises";
import { createHash } from "node:crypto";
import path from "node:path";
import { isCreationRunCommandType } from "../../src/types/creation";
import type {
  CardRelation,
  CardSummary,
  CardLinkResult,
  CardType,
  CardsListQuery,
  CreateProjectInput,
  CreationProjectListener,
  CreationProjectNavigation,
  CreationProjectSummary,
  CreationRunCommand,
  CreationRunResult,
  ProjectHomeView,
  CreationSearchQuery,
  CreationSearchView,
  ProjectExportView,
  RelationType,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewQuery,
  ReplacePreviewView,
  SceneSaveResponse,
  SessionEntry,
  SessionListQuery,
  SessionReportCommand,
  SessionReportResult,
  SnapshotInfo,
  SnapshotListQuery,
  UpdateSceneBodyInput,
  StructurePreviewCommand,
  StructureApplyWithProtectionCommand,
  StructureRevertCommand,
  ProtectedStructureCommand,
  ProofQuery,
  ProofView,
  InboxDeleteCommand,
  InboxItem,
  InboxListQuery,
  InboxUpdateCommand,
  InboxCreateCommand,
  ProjectBundleData,
  ProjectBundleImportResult,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationResult,
  AnnotationUpdateCommand,
  AnnotationReanchorCommand,
  SnapshotPreviewQuery,
  SnapshotPreviewView,
  SnapshotRestoreWithProtectionCommand,
  TrashImpactQuery,
  TrashImpactView,
  ResourceAttachCommand,
  ResourceDetachCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult,
  SessionUpdateCommand,
  ProjectUpdateGoalCommand,
  ProjectGoalResult,
  ReplacePlanQuery,
} from "../../src/types/creation";
import type {
  CardExportFilter,
  CardExportResult,
  CardExportRow,
  CardImportApplyInput,
  CardImportPreview,
  CardImportSource
} from "../../src/types/card-io";
import {
  collectExportFieldKeys,
  exportCardsToCsv,
  exportCardsToMarkdown,
  parseCardSource
} from "./creation-card-io/card-io-index";
import { CreationWorkspaceError } from "./creation-workspace";
import { exportProjectBundleDirectory, importProjectBundleDirectory } from "./creation-bundle";
import type { CreationCoordinator } from "./creation-coordinator";
import { getLegacyMigrationStatus, runLegacyMigration } from "./creation-migration";
import { previewLegacyDraft } from "./creation-import";
import { buildDraftExport, DRAFT_EXPORT_PRESET_META, isDraftExportPreset } from "./creation-export";
import type { DraftExportPreset } from "../../src/types/creation";

function assertRunCommand(value: unknown): CreationRunCommand {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CreationWorkspaceError("invalid-input", "创作命令必须为对象。");
  }
  const type = (value as { type?: unknown }).type;
  if (!isCreationRunCommandType(type)) {
    throw new CreationWorkspaceError("invalid-input", `不支持的运行命令类型：${String(type)}。`);
  }
  return value as CreationRunCommand;
}

type RuntimeRecord = Record<string, unknown>;

function assertRecord(value: unknown, label: string): RuntimeRecord {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为对象。`);
  }
  return value as RuntimeRecord;
}

function assertRequiredText(value: unknown, label: string, maxLength = 256): string {
  if (typeof value !== "string" || !value.trim() || value.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为非空字符串，且不得超过 ${maxLength} 个字符。`);
  }
  return value;
}

function assertOptionalText(value: unknown, label: string, maxLength = 256): string | undefined {
  if (value === undefined) return undefined;
  return assertRequiredText(value, label, maxLength);
}

function assertPositiveRevision(value: unknown, label: string): number {
  if (!Number.isInteger(value) || (value as number) <= 0) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为正整数。`);
  }
  return value as number;
}

function assertProtectedStructureCommand(value: unknown): ProtectedStructureCommand {
  const command = assertRecord(value, "受保护的结构命令");
  const type = command.type;
  switch (type) {
    case "chapter.split":
      return {
        type,
        chapterId: assertRequiredText(command.chapterId, "章节 ID"),
        splitSceneId: assertRequiredText(command.splitSceneId, "拆分场景 ID"),
        newChapterTitle: assertOptionalText(command.newChapterTitle, "新章节标题", 500)
      };
    case "chapter.merge":
      return {
        type,
        sourceChapterId: assertRequiredText(command.sourceChapterId, "源章节 ID"),
        targetChapterId: assertRequiredText(command.targetChapterId, "目标章节 ID")
      };
    case "chapter.move":
      return {
        type,
        chapterId: assertRequiredText(command.chapterId, "章节 ID"),
        targetVolumeId: assertRequiredText(command.targetVolumeId, "目标卷 ID"),
        beforeChapterId: assertOptionalText(command.beforeChapterId, "前置章节 ID")
      };
    case "scene.move":
      return {
        type,
        sceneId: assertRequiredText(command.sceneId, "场景 ID"),
        targetChapterId: assertRequiredText(command.targetChapterId, "目标章节 ID"),
        beforeSceneId: assertOptionalText(command.beforeSceneId, "前置场景 ID")
      };
    case "chapters.setStatus": {
      if (!Array.isArray(command.chapterIds) || command.chapterIds.length === 0) {
        throw new CreationWorkspaceError("invalid-input", "章节 ID 列表必须为非空数组。");
      }
      const chapterIds = command.chapterIds.map((id) => assertRequiredText(id, "章节 ID"));
      if (new Set(chapterIds).size !== chapterIds.length) {
        throw new CreationWorkspaceError("invalid-input", "章节 ID 列表不得包含重复项。");
      }
      return {
        type,
        chapterIds,
        status: assertRequiredText(command.status, "章节状态", 100)
      };
    }
    case "chapter.setNumbering": {
      const numbering = command.numbering;
      if (numbering !== "auto" && numbering !== "prologue" && numbering !== "extra" && numbering !== "custom") {
        throw new CreationWorkspaceError("invalid-input", "章节编号类型无效。");
      }
      const customNumber = assertOptionalText(command.customNumber, "自定义章节编号", 100);
      if (numbering === "custom" && !customNumber) {
        throw new CreationWorkspaceError("invalid-input", "自定义章节编号不能为空。");
      }
      return {
        type,
        chapterId: assertRequiredText(command.chapterId, "章节 ID"),
        numbering,
        customNumber,
        baseRevision: assertPositiveRevision(command.baseRevision, "章节基础版本")
      };
    }
    default:
      throw new CreationWorkspaceError("invalid-input", `命令 ${String(type)} 不属于受保护的结构操作。`);
  }
}

function assertStructurePreviewCommand(value: unknown): StructurePreviewCommand {
  const command = assertRecord(value, "结构预览请求");
  if (command.type !== "structure.preview") {
    throw new CreationWorkspaceError("invalid-input", "结构预览请求类型必须为 structure.preview。");
  }
  return {
    type: "structure.preview",
    projectId: assertRequiredText(command.projectId, "作品 ID"),
    command: assertProtectedStructureCommand(command.command)
  };
}

function assertStructureApplyCommand(value: unknown): StructureApplyWithProtectionCommand {
  const command = assertRecord(value, "结构应用请求");
  if (command.type !== "structure.applyWithProtection") {
    throw new CreationWorkspaceError("invalid-input", "结构应用请求类型必须为 structure.applyWithProtection。");
  }
  return {
    type: "structure.applyWithProtection",
    projectId: assertRequiredText(command.projectId, "作品 ID"),
    planId: assertRequiredText(command.planId, "预览计划 ID"),
    protectionReason: assertRequiredText(command.protectionReason, "保护原因", 200)
  };
}

function assertAnnotationReanchorCommand(value: unknown): AnnotationReanchorCommand {
  const command = assertRecord(value, "批注重新定位请求");
  if (command.type !== "annotation.reanchor") {
    throw new CreationWorkspaceError("invalid-input", "批注重新定位请求类型必须为 annotation.reanchor。");
  }
  const anchor = assertRecord(command.anchor, "批注锚点");
  if (
    !Number.isInteger(anchor.blockIndex) ||
    (anchor.blockIndex as number) < 0 ||
    !Number.isInteger(anchor.textOffset) ||
    (anchor.textOffset as number) < 0 ||
    !Number.isInteger(anchor.textLength) ||
    (anchor.textLength as number) < 1
  ) {
    throw new CreationWorkspaceError("invalid-input", "批注锚点无效。");
  }
  return {
    type: "annotation.reanchor",
    annotationId: assertRequiredText(command.annotationId, "批注 ID"),
    baseRevision: assertPositiveRevision(command.baseRevision, "批注基础版本"),
    anchor: {
      blockIndex: anchor.blockIndex as number,
      textOffset: anchor.textOffset as number,
      textLength: anchor.textLength as number,
      text: typeof anchor.text === "string" ? anchor.text : undefined
    }
  };
}

function assertSnapshotPreviewQuery(value: unknown): SnapshotPreviewQuery {
  const query = assertRecord(value, "快照预览请求");
  if (query.kind !== "snapshot.preview") {
    throw new CreationWorkspaceError("invalid-input", "快照预览请求类型必须为 snapshot.preview。");
  }
  return {
    kind: "snapshot.preview",
    projectId: assertRequiredText(query.projectId, "作品 ID"),
    snapshotId: assertRequiredText(query.snapshotId, "快照 ID")
  };
}

function assertSnapshotRestoreWithProtectionCommand(value: unknown): SnapshotRestoreWithProtectionCommand {
  const command = assertRecord(value, "快照安全恢复请求");
  if (command.type !== "snapshot.restoreWithProtection") {
    throw new CreationWorkspaceError("invalid-input", "快照安全恢复请求类型必须为 snapshot.restoreWithProtection。");
  }
  return {
    type: "snapshot.restoreWithProtection",
    projectId: assertRequiredText(command.projectId, "作品 ID"),
    snapshotId: assertRequiredText(command.snapshotId, "快照 ID"),
    protectionReason: assertRequiredText(command.protectionReason, "保护原因", 200)
  };
}

function assertTrashImpactQuery(value: unknown): TrashImpactQuery {
  const query = assertRecord(value, "回收站影响请求");
  if (query.kind !== "trash.impact") {
    throw new CreationWorkspaceError("invalid-input", "回收站影响请求类型必须为 trash.impact。");
  }
  const entity = query.entity;
  if (entity !== "volume" && entity !== "chapter" && entity !== "scene" && entity !== "card") {
    throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
  }
  const projectId = query.projectId === undefined ? undefined : assertRequiredText(query.projectId, "作品 ID");
  if (!projectId && entity !== "card") {
    throw new CreationWorkspaceError("invalid-input", "项目回收站影响请求必须提供作品 ID。");
  }
  return {
    kind: "trash.impact",
    projectId,
    entity,
    entityId: assertRequiredText(query.entityId, "实体 ID")
  };
}

function assertStructureRevertCommand(value: unknown): StructureRevertCommand {  const command = assertRecord(value, "结构撤回请求");
  if (command.type !== "structure.revert") {
    throw new CreationWorkspaceError("invalid-input", "结构撤回请求类型必须为 structure.revert。");
  }
  if (!Array.isArray(command.expectedAppliedRevisions)) {
    throw new CreationWorkspaceError("invalid-input", "应用版本集合必须为数组。");
  }
  const expectedAppliedRevisions: StructureRevertCommand["expectedAppliedRevisions"] = command.expectedAppliedRevisions.map((entry, index) => {
    const revision = assertRecord(entry, `应用版本集合第 ${index + 1} 项`);
    if (revision.type !== "volume" && revision.type !== "chapter" && revision.type !== "scene") {
      throw new CreationWorkspaceError("invalid-input", `应用版本集合第 ${index + 1} 项的实体类型无效。`);
    }
    return {
      type: revision.type,
      id: assertRequiredText(revision.id, `应用版本集合第 ${index + 1} 项的实体 ID`),
      revision: assertPositiveRevision(revision.revision, `应用版本集合第 ${index + 1} 项的版本`)
    };
  });
  const keys = expectedAppliedRevisions.map((entry) => `${entry.type}:${entry.id}`);
  if (new Set(keys).size !== keys.length) {
    throw new CreationWorkspaceError("invalid-input", "应用版本集合不得包含重复实体。");
  }
  return {
    type: "structure.revert",
    projectId: assertRequiredText(command.projectId, "作品 ID"),
    protectionSnapshotId: assertRequiredText(command.protectionSnapshotId, "保护快照 ID"),
    expectedAppliedRevisions
  };
}

export interface CreationIpcContext {
  resolveDataRoot: () => string;
  resolveLibraryRoot: () => string;
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

  ipcMain.handle("creation:readProjectHome", () =>
    coordinator.withWorkspace(
      (workspace) => workspace.read({ kind: "project.home" }) as Promise<ProjectHomeView>
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
    coordinator.withWorkspace((workspace) => workspace.transact(assertRunCommand(command)) as Promise<CreationRunResult>)
  );

  ipcMain.handle("creation:structurePreview", (_event, command: unknown) => {
    const validated = assertStructurePreviewCommand(command);
    return coordinator.withWorkspace((workspace) => workspace.previewStructure(validated));
  });
  ipcMain.handle("creation:structureApply", (_event, command: unknown) => {
    const validated = assertStructureApplyCommand(command);
    return coordinator.withWorkspace((workspace) => workspace.applyStructure(validated));
  });
  ipcMain.handle("creation:structureRevert", (_event, command: unknown) => {
    const validated = assertStructureRevertCommand(command);
    return coordinator.withWorkspace((workspace) => workspace.revertStructure(validated));
  });

  ipcMain.handle("creation:cardsList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as CardsListQuery) as Promise<CardSummary[]>)
  );

  ipcMain.handle("creation:cardRead", (_event, cardId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "card.read", cardId }))
  );

  ipcMain.handle("creation:cardTypesList", () =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "cardTypes.list" }) as Promise<CardType[]>)
  );

  ipcMain.handle("creation:relationTypesList", () =>
    coordinator.withWorkspace(
      (workspace) => workspace.read({ kind: "relationTypes.list" }) as Promise<RelationType[]>
    )
  );

  const runCardLink = (link: boolean, input: unknown): Promise<CardLinkResult> => {
    const request = assertRecord(input, "卡片关联请求");
    const projectId = assertRequiredText(request.projectId, "作品 ID");
    const cardId = assertRequiredText(request.cardId, "卡片 ID");
    return coordinator.withWorkspace(
      (workspace) => workspace.transact({ type: link ? "card.link" : "card.unlink", projectId, cardId }) as Promise<CardLinkResult>
    );
  };
  ipcMain.handle("creation:cardLink", (_event, input: unknown) => runCardLink(true, input));
  ipcMain.handle("creation:cardUnlink", (_event, input: unknown) => runCardLink(false, input));

  ipcMain.handle("creation:cardRelations", (_event, cardId: string) =>
    coordinator.withWorkspace(
      (workspace) =>
        workspace.read({ kind: "card.relations", cardId }) as Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>
    )
  );

  ipcMain.handle("creation:trashList", (_event, projectId?: string) =>
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

  ipcMain.handle("creation:inboxCount", () =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "inbox.count" }))
  );

  ipcMain.handle("creation:inboxUpdate", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as InboxUpdateCommand))
  );

  ipcMain.handle("creation:inboxDelete", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as InboxDeleteCommand))
  );

  ipcMain.handle("creation:inboxCreate", (_event, command: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.transact(command as InboxCreateCommand))
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

  ipcMain.handle("creation:annotationReanchor", (_event, command: unknown) => {
    const validated = assertAnnotationReanchorCommand(command);
    return coordinator.withWorkspace((workspace) => workspace.transact(validated) as Promise<AnnotationResult>);
  });

  ipcMain.handle("creation:snapshotPreview", (_event, query: unknown) => {
    const validated = assertSnapshotPreviewQuery(query);
    return coordinator.withWorkspace((workspace) => workspace.read(validated) as Promise<SnapshotPreviewView | null>);
  });

  ipcMain.handle("creation:snapshotRestoreWithProtection", (_event, command: unknown) => {
    const validated = assertSnapshotRestoreWithProtectionCommand(command);
    return coordinator.withWorkspace((workspace) => workspace.restoreSnapshotWithProtection(validated));
  });

  ipcMain.handle("creation:trashImpact", (_event, query: unknown) => {
    const validated = assertTrashImpactQuery(query);
    return coordinator.withWorkspace((workspace) => workspace.read(validated) as Promise<TrashImpactView | null>);
  });

  ipcMain.handle("creation:resourceList", (_event, query: unknown) =>
    coordinator.withWorkspace((workspace) => workspace.read(query as ResourceListQuery) as Promise<ResourceInfo[]>)
  );

  ipcMain.handle("creation:attachResource", async (event, input: { projectId?: unknown; cardId?: unknown; role?: unknown }) => {
    const projectId = typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    const cardId = typeof input?.cardId === "string" && input.cardId.trim() ? input.cardId : undefined;
    const role = input?.role === "cover" ? "cover" : "attachment";
    if (!projectId && !cardId) throw new CreationWorkspaceError("invalid-input", "附件必须归属作品或全局卡片。");
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: role === "cover" ? "选择卡片封面" : "选择附件",
      properties: ["openFile" as const],
      ...(role === "cover" ? { filters: [{ name: "图片", extensions: ["png", "jpg", "jpeg", "webp", "gif"] }] } : {})
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
    const ownerPath = projectId ? projectId : `cards/${cardId}`;
    const relativePath = `resources/${ownerPath}/${randomUUID()}-${fileName}`;
    const targetPath = path.join(context.resolveDataRoot(), "CreationWorkspace", relativePath);
    await mkdir(path.dirname(targetPath), { recursive: true });
    await writeFile(targetPath, buffer);
    try {
      const result = await coordinator.withWorkspace((workspace) =>
        workspace.transact({
          type: "resource.attach",
          projectId: projectId || undefined,
          cardId,
          role,
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
        { name: "旧稿文件（TXT / Markdown / DOCX）", extensions: ["txt", "md", "markdown", "docx"] },
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
    const workspaceDirectory = path.join(context.resolveDataRoot(), "CreationWorkspace");
    const { directory } = await exportProjectBundleDirectory({
      workspaceDirectory,
      data,
      targetDirectory: filePaths[0]!
    });
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
    const workspaceDirectory = path.join(context.resolveDataRoot(), "CreationWorkspace");
    const result = await importProjectBundleDirectory({
      workspaceDirectory,
      bundleDirectory: directory,
      transact: async (command) =>
        coordinator.withWorkspace((workspace) => workspace.transact(command) as Promise<ProjectBundleImportResult>)
    });
    return { canceled: false, result };
  });

  ipcMain.handle("creation:readProjectExport", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.read({ kind: "project.export", projectId }) as Promise<ProjectExportView | null>)
  );

  ipcMain.handle("creation:exportDraft", async (event, input: { projectId?: unknown; preset?: unknown }) => {
    const projectId =
      typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "作品读取请求无效。");
    if (!isDraftExportPreset(input?.preset)) {
      throw new CreationWorkspaceError("invalid-input", "导出预设无效。");
    }
    const preset = input.preset as DraftExportPreset;
    const view = (await coordinator.withWorkspace((workspace) =>
      workspace.read({ kind: "project.export", projectId, includeBlocks: true })
    )) as ProjectExportView | null;
    if (!view) throw new CreationWorkspaceError("not-found", "作品不存在。");
    const built = buildDraftExport(view, preset);
    if (!built) throw new CreationWorkspaceError("invalid-input", "导出预设无效。");
    const meta = DRAFT_EXPORT_PRESET_META[built.preset];
    const isOutline = built.preset === "outline-markdown";
    const options = {
      title: isOutline ? "导出 Markdown 大纲" : `导出成稿（${meta.label}）`,
      defaultPath: `${view.title}${isOutline ? "-大纲" : ""}.${built.extension}`,
      filters: [{ name: meta.extension === "md" ? "Markdown 文档" : "文本文件", extensions: [built.extension] }]
    };
    const parent = BrowserWindow.fromWebContents(event.sender);
    const { canceled, filePath } = parent
      ? await dialog.showSaveDialog(parent, options)
      : await dialog.showSaveDialog(options);
    if (canceled || !filePath) return { canceled: true, filePath: null };
    await writeFile(filePath, built.text, "utf8");
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

  // ---- Phase 1 P1 深模块 seam 接入 ----

  ipcMain.handle("creation:snapshotRetentionRun", () =>
    coordinator.withWorkspace((workspace) => workspace.runSnapshotRetention())
  );

  ipcMain.handle("creation:cardImportOpenAndParse", async (event) => {
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "导入卡片",
      properties: ["openFile" as const],
      filters: [
        { name: "卡片文件（CSV / Markdown）", extensions: ["csv", "md", "markdown"] },
        { name: "所有文件", extensions: ["*"] }
      ]
    };
    const { canceled, filePaths } = parent ? await dialog.showOpenDialog(parent, options) : await dialog.showOpenDialog(options);
    if (canceled || filePaths.length === 0) return null;
    const filePath = filePaths[0]!;
    const ext = path.extname(filePath).toLowerCase();
    const format: CardImportSource["format"] = ext === ".csv" ? "csv" : "markdown";
    const text = await readFile(filePath, "utf8");
    return { format, text } as CardImportSource;
  });

  ipcMain.handle("creation:cardImportParse", (_event, input: { text: string; format: "csv" | "markdown" }) => {
    if (typeof input?.text !== "string" || (input.format !== "csv" && input.format !== "markdown")) {
      throw new CreationWorkspaceError("invalid-input", "卡片解析请求无效。");
    }
    return parseCardSource(input.text, input.format) as CardImportPreview;
  });

  ipcMain.handle("creation:cardImportSchema", (_event, projectId: string) =>
    coordinator.withWorkspace((workspace) => workspace.cardImportSchema(projectId))
  );

  ipcMain.handle("creation:cardImportPlan", (_event, input: CardImportApplyInput) =>
    coordinator.withWorkspace((workspace) => workspace.cardImportPlan(input))
  );

  ipcMain.handle("creation:cardImportApply", (_event, input: CardImportApplyInput) =>
    coordinator.withWorkspace((workspace) => workspace.cardImportApply(input))
  );

  ipcMain.handle("creation:cardExportOpenAndWrite", async (event, input: { projectId?: unknown; filter?: unknown; format?: unknown }) => {
    const projectId = typeof input?.projectId === "string" && input.projectId.trim() ? input.projectId : "";
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "作品读取请求无效。");
    const filter = (input?.filter as CardExportFilter) ?? {};
    const format = input?.format === "csv" ? "csv" : "markdown";
    const rows = await coordinator.withWorkspace((workspace) =>
      workspace.cardExportRows(projectId, filter) as Promise<CardExportRow[]>
    );
    const fieldKeys = collectExportFieldKeys(rows);
    const text = format === "csv" ? exportCardsToCsv(rows, fieldKeys) : exportCardsToMarkdown(rows, fieldKeys);
    const parent = BrowserWindow.fromWebContents(event.sender);
    const options = {
      title: "导出卡片",
      defaultPath: `cards.${format}`,
      filters: [{ name: format === "csv" ? "CSV 文档" : "Markdown 文档", extensions: [format] }]
    };
    const { canceled, filePath } = parent
      ? await dialog.showSaveDialog(parent, options)
      : await dialog.showSaveDialog(options);
    if (canceled || !filePath) return { canceled: true, written: 0 } as CardExportResult;
    await writeFile(filePath, text, "utf8");
    return { canceled: false, written: rows.length } as CardExportResult;
  });

  ipcMain.handle("creation:replacePlanCreate", (_event, query: ReplacePlanQuery) =>
    coordinator.withWorkspace((workspace) => workspace.createReplacePlan(query))
  );

  ipcMain.handle("creation:replacePlanApply", (_event, input: { planId?: unknown; excludedHitIds?: unknown }) => {
    const planId = typeof input?.planId === "string" && input.planId.trim() ? input.planId : "";
    if (!planId) throw new CreationWorkspaceError("invalid-input", "替换计划读取请求无效。");
    const excludedHitIds = Array.isArray(input?.excludedHitIds)
      ? (input!.excludedHitIds as unknown[]).filter((id): id is string => typeof id === "string")
      : [];
    return coordinator.withWorkspace((workspace) => workspace.applyReplacePlan(planId, excludedHitIds));
  });

  ipcMain.handle("creation:sessionUpdate", (_event, command: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.sessionUpdate(command as SessionUpdateCommand) as Promise<SessionReportResult>
    )
  );

  ipcMain.handle("creation:projectUpdateGoal", (_event, command: unknown) =>
    coordinator.withWorkspace(
      (workspace) => workspace.projectUpdateGoal(command as ProjectUpdateGoalCommand) as Promise<ProjectGoalResult>
    )
  );
}
