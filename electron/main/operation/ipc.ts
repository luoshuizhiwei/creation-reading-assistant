/**
 * operation 的 IPC 接线。
 *
 * 暴露类型安全的长任务接口，把 start / getState / cancel / subscribe 串起来，
 * 并保证 operationId 贯穿 renderer → IPC → 主进程：
 * - start 返回带 operationId 的运行态；
 * - cancel(operationId) 只取消对应任务；
 * - subscribe(operationId) 返回 subscriptionId，主进程用 operation:event 推送快照，
 *   renderer 端按 subscriptionId 过滤；unsubscribe 或 sender 销毁时清理 listener，不泄漏。
 *
 * 前置编排（目录选择、合法性校验、外置资料库确认、恢复后设置回写）由传入的
 * Orchestrator 负责——这部分逻辑本就属于 index.ts，避免在此重写已通过合同的深模块。
 */

import { ipcMain, type IpcMainInvokeEvent, type WebContents } from "electron";
import type { OperationCoordinator } from "./coordinator";
import type { OperationState as CoordinatorOperationState } from "./types";
import type { ProjectBundleData } from "../../../src/types/creation";
import type { OperationState, OperationStartRequest } from "../../../src/types/operation";
import {
  runBackupCreate,
  runBackupRestore,
  runBackupExportEncrypted,
  runBackupImportEncrypted,
  runBundleExport,
  runBundleImport,
  runBundleExportEncrypted,
  runBundleImportEncrypted
} from "./operations";
import { scanResourceConsistencyCore } from "../creation-workspace/resource-scan";
import type { BackupManifest } from "../backup";

/** index.ts 提供的编排原语，封装对话框 / 路径 / 设置回写等主进程副作用。 */
export interface OperationOrchestrator {
  resolveDataRoot: () => string;
  resolveLibraryRoot: () => string;
  appVersion: string;
  platform: string;
  arch: string;
  now: () => number;
  /** 选择备份保存目录并校验 / 计算唯一根；取消返回 null。 */
  prepareBackupCreate: () => Promise<{ backupRoot: string; libraryDirectory?: string; createdAt: string } | null>;
  /** 选择备份目录、读取 manifest、确认外置资料库；取消返回 null。 */
  prepareBackupRestore: () => Promise<{ backupRoot: string; manifest: BackupManifest } | null>;
  /** 恢复成功后回写存储设置并标记需重启。 */
  finalizeBackupRestore: (manifest: BackupManifest, backupRoot: string) => Promise<void>;
  /** 选择项目包导入目录；取消返回 null。 */
  pickBundleImportDirectory: () => Promise<string | null>;
  /** 打开工作区读取导出数据并选择目标目录；取消返回 null。 */
  prepareBundleExport: (projectId: string) => Promise<{ workspaceDirectory: string; data: ProjectBundleData; targetDirectory: string } | null>;
  /** 选择加密备份容器保存路径（.crbackup）；返回目标文件、外置资料库标记与时间戳；取消返回 null。 */
  prepareBackupExportEncrypted: () => Promise<{ targetFile: string; libraryDirectory?: string; createdAt: string } | null>;
  /** 选择加密备份容器文件（.crbackup）；取消返回 null。 */
  prepareBackupImportEncrypted: () => Promise<{ containerFile: string } | null>;
  /** 读取项目包数据并选择加密容器保存路径（.crbundle）；取消返回 null。 */
  prepareBundleExportEncrypted: (projectId: string) => Promise<{ workspaceDirectory: string; data: ProjectBundleData; targetFile: string } | null>;
  /** 选择加密项目包容器文件（.crbundle）；取消返回 null。 */
  prepareBundleImportEncrypted: () => Promise<{ containerFile: string } | null>;
  /**
   * 关闭创作工作区连接后执行（保护正在使用的 appData / SQLite 不被破坏），
   * 由 index.ts 桥接到 creationCoordinator.withWorkspaceClosed。
   */
  withWorkspaceClosed: <R>(fn: () => Promise<R>) => Promise<R>;
  /** 加密备份恢复成功后回写存储设置并标记需重启（无明文 manifest，按本机数据根回指）。 */
  finalizeEncryptedBackupRestore: () => Promise<void>;
}

interface Subscription {
  webContents: WebContents;
  unsubscribe: () => void;
  onDestroyed: () => void;
}

function normalizeState(state: CoordinatorOperationState | null | undefined): OperationState | null {
  if (!state) return null;
  return {
    ...state,
    progress: {
      ...state.progress,
      indeterminate: state.progress.indeterminate ?? false
    }
  };
}

export function registerOperationIpc(coordinator: OperationCoordinator, orchestrator: OperationOrchestrator): void {
  const subscriptions = new Map<string, Subscription>();
  let subscriptionSeq = 0;

  function disposeSubscription(id: string): void {
    const sub = subscriptions.get(id);
    if (!sub) return;
    sub.webContents.removeListener("destroyed", sub.onDestroyed);
    try {
      sub.unsubscribe();
    } catch {
      // 协调器已在任务结束时清理订阅，忽略重复释放。
    }
    subscriptions.delete(id);
  }

  ipcMain.handle("operation:start", async (_event: IpcMainInvokeEvent, request: OperationStartRequest) => {
    let started: { operationId: string; done: Promise<unknown> };
    switch (request.kind) {
      case "backup.create": {
        const prepared = await orchestrator.prepareBackupCreate();
        if (!prepared) return null;
        started = runBackupCreate(coordinator, {
          backupRoot: prepared.backupRoot,
          appDataDirectory: orchestrator.resolveDataRoot(),
          libraryDirectory: prepared.libraryDirectory,
          appVersion: orchestrator.appVersion,
          platform: orchestrator.platform,
          arch: orchestrator.arch,
          createdAt: prepared.createdAt
        });
        break;
      }
      case "backup.restore": {
        const prepared = await orchestrator.prepareBackupRestore();
        if (!prepared) return null;
        started = runBackupRestore(coordinator, {
          backupRoot: prepared.backupRoot,
          currentAppDataRoot: orchestrator.resolveDataRoot(),
          resolveLibraryTarget: async () => orchestrator.resolveLibraryRoot()
        }, orchestrator.withWorkspaceClosed);
        // 恢复成功后的设置回写与重启标记在主进程侧完成，与 renderer 是否仍在无关。
        void (started.done as Promise<{ status: string }>).then((result) => {
          if (result && result.status === "completed") {
            void orchestrator.finalizeBackupRestore(prepared.manifest, prepared.backupRoot);
          }
        });
        break;
      }
      case "bundle.export": {
        const prepared = await orchestrator.prepareBundleExport(request.projectId);
        if (!prepared) return null;
        started = runBundleExport(coordinator, {
          workspaceDirectory: prepared.workspaceDirectory,
          data: prepared.data,
          targetDirectory: prepared.targetDirectory
        });
        break;
      }
      case "bundle.import": {
        const bundleDirectory = await orchestrator.pickBundleImportDirectory();
        if (!bundleDirectory) return null;
        started = runBundleImport(coordinator, {
          workspaceDirectory: orchestrator.resolveDataRoot(),
          bundleDirectory
        }, orchestrator.withWorkspaceClosed);
        break;
      }
      case "backup.export-encrypted": {
        const prepared = await orchestrator.prepareBackupExportEncrypted();
        if (!prepared) return null;
        started = runBackupExportEncrypted(coordinator, {
          appDataDirectory: orchestrator.resolveDataRoot(),
          libraryDirectory: prepared.libraryDirectory,
          appVersion: orchestrator.appVersion,
          platform: orchestrator.platform,
          arch: orchestrator.arch,
          createdAt: prepared.createdAt,
          targetFile: prepared.targetFile,
          passphrase: request.passphrase
        });
        break;
      }
      case "backup.import-encrypted": {
        const prepared = await orchestrator.prepareBackupImportEncrypted();
        if (!prepared) return null;
        started = runBackupImportEncrypted(coordinator, {
          containerFile: prepared.containerFile,
          passphrase: request.passphrase,
          currentAppDataRoot: orchestrator.resolveDataRoot(),
          resolveLibraryTarget: async () => orchestrator.resolveLibraryRoot()
        }, orchestrator.withWorkspaceClosed);
        // 恢复成功后的设置回写与重启标记在主进程侧完成，与 renderer 是否仍在无关。
        void (started.done as Promise<{ status: string }>).then((result) => {
          if (result && result.status === "completed") {
            void orchestrator.finalizeEncryptedBackupRestore();
          }
        });
        break;
      }
      case "bundle.export-encrypted": {
        const prepared = await orchestrator.prepareBundleExportEncrypted(request.projectId);
        if (!prepared) return null;
        started = runBundleExportEncrypted(coordinator, {
          workspaceDirectory: prepared.workspaceDirectory,
          data: prepared.data,
          targetFile: prepared.targetFile,
          passphrase: request.passphrase
        });
        break;
      }
      case "bundle.import-encrypted": {
        const prepared = await orchestrator.prepareBundleImportEncrypted();
        if (!prepared) return null;
        started = runBundleImportEncrypted(coordinator, {
          workspaceDirectory: orchestrator.resolveDataRoot(),
          containerFile: prepared.containerFile,
          passphrase: request.passphrase
        }, orchestrator.withWorkspaceClosed);
        break;
      }
      case "resource.scan": {
        const result = coordinator.start("resource.scan", (controller) =>
          scanResourceConsistencyCore({
            workspaceDirectory: orchestrator.resolveDataRoot(),
            operation: controller
          })
        );
        started = { operationId: result.operationId, done: result.done };
        break;
      }
      default: {
        const exhaustive: never = request;
        throw new Error(`未知 operation 种类：${JSON.stringify(exhaustive)}`);
      }
    }
    return normalizeState(coordinator.getState(started.operationId));
  });

  ipcMain.handle("operation:getState", (_event: IpcMainInvokeEvent, operationId: string) => {
    return normalizeState(coordinator.getState(operationId));
  });

  ipcMain.handle("operation:cancel", (_event: IpcMainInvokeEvent, operationId: string) => {
    coordinator.requestCancel(operationId);
  });

  ipcMain.handle("operation:subscribe", (event: IpcMainInvokeEvent, operationId: string) => {
    const subscriptionId = `op-sub-${++subscriptionSeq}`;
    const sender = event.sender;
    const unsubscribe = coordinator.subscribe(operationId, (state) => {
      if (sender.isDestroyed()) return;
      sender.send("operation:event", { subscriptionId, state: normalizeState(state) });
    });
    const onDestroyed = () => disposeSubscription(subscriptionId);
    sender.once("destroyed", onDestroyed);
    subscriptions.set(subscriptionId, { webContents: sender, unsubscribe, onDestroyed });
    return { subscriptionId };
  });

  ipcMain.handle("operation:unsubscribe", (_event: IpcMainInvokeEvent, payload: { subscriptionId: string }) => {
    disposeSubscription(payload.subscriptionId);
  });
}

/** 应用退出前终止所有进行中的 operation（不可中断阶段由深模块保证自身一致）。 */
export function disposeAllOperations(coordinator: OperationCoordinator): void {
  coordinator.terminateAll();
}
