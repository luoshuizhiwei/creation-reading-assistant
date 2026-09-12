/**
 * operation 集成入口。
 *
 * 把"长任务协调器"与既有深模块（backup / creation-bundle）接起来：
 * 每个 run* 函数创建一个 operation，把控制器（OperationController）透传给深模块，
 * 深模块在可中断阶段周期性调用 throwIfCancelled / setPhase / setProgress。
 *
 * 设计约束（来自 P1 收尾规格）：
 * - 不在此处引入 setTimeout / sleep 伪造进度；
 * - 不依赖 BrowserWindow / ipcMain 等 UI 设施；
 * - 临时目录 / 半复制资源 / 半导入项目的清理由深模块负责（取消或失败时）；
 *   此处仅负责深模块之外的资源生命周期（如 import 时按需打开 / 关闭工作区）。
 */

import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import type { OperationCoordinator } from "./coordinator";
import type { OperationController, OperationResult } from "./types";
import { OperationCancelledError, OperationError } from "./types";
import {
  createBackupSnapshot,
  restoreBackupFromDirectory,
  type BackupManifest,
  type CreateBackupSnapshotOptions,
  type RestoreBackupOptions,
  type RestoreBackupResult
} from "../backup";
import {
  encryptBackupSnapshotToContainer,
  withDecryptedBackupStaging
} from "../backup/encrypted-backup";
import {
  exportProjectBundleDirectory,
  importProjectBundleDirectory,
  type ExportProjectBundleOptions,
  type ProjectBundleManifest
} from "../creation-bundle";
import {
  encryptBundleToContainer,
  withDecryptedBundleStaging
} from "../creation-bundle/encrypted-bundle";
import { openCreationWorkspace, type CreationWorkspace } from "../creation-workspace";
import type {
  ProjectBundleData,
  ProjectBundleCardResolution,
  ProjectBundleImportCommand,
  ProjectBundleImportPreview,
  ProjectBundleImportResult
} from "../../../src/types/creation";
import { EncryptionError } from "../portable-encryption";

/** 与 coordinator.start 返回结构一致的最小句柄：仅暴露 operationId 与完成时结果。 */
export interface StartedOperationHandle<T> {
  operationId: string;
  done: Promise<OperationResult<T>>;
}

/** 备份创建。深模块在出错或取消时会清理半成品备份目录。 */
export function runBackupCreate(
  coordinator: OperationCoordinator,
  options: Omit<CreateBackupSnapshotOptions, "operation">
): StartedOperationHandle<BackupManifest> {
  const started = coordinator.start("backup.create", (controller) =>
    createBackupSnapshot({ ...options, operation: controller })
  );
  return { operationId: started.operationId, done: started.done };
}

/** 备份恢复。深模块在可中断阶段取消时清理暂存目录，当前数据保持未触碰。 */
export function runBackupRestore(
  coordinator: OperationCoordinator,
  options: Omit<RestoreBackupOptions, "operation">,
  withWorkspaceClosed?: <R>(fn: () => Promise<R>) => Promise<R>
): StartedOperationHandle<RestoreBackupResult> {
  const started = coordinator.start("backup.restore", (controller) => {
    const run = () => restoreBackupFromDirectory({ ...options, operation: controller });
    // 恢复会重命名 / 覆盖正在使用的 appData 文件，必须确保创作工作区连接已关闭，
    // 否则会破坏当前打开的 SQLite 连接。生产路径通过 creationCoordinator 关闭工作区。
    return withWorkspaceClosed ? withWorkspaceClosed(run) : run();
  });
  return { operationId: started.operationId, done: started.done };
}

/** 项目包导出。深模块在出错或取消时清理 staging 目录。 */
export function runBundleExport(
  coordinator: OperationCoordinator,
  options: Omit<ExportProjectBundleOptions, "operation">
): StartedOperationHandle<{ directory: string; manifest: ProjectBundleManifest }> {
  const started = coordinator.start("bundle.export", (controller) =>
    exportProjectBundleDirectory({ ...options, operation: controller })
  );
  return { operationId: started.operationId, done: started.done };
}

export interface BundleImportRunOptions {
  workspaceDirectory: string;
  bundleDirectory: string;
  cardResolutions?: ProjectBundleCardResolution[];
  /**
   * 真正的 DB 导入命令执行器（由已打开的工作区提供）。
   * 不传时本函数会按需打开并关闭工作区（finally 保证关闭，不泄漏连接）。
   */
  transact?: (command: ProjectBundleImportCommand) => Promise<ProjectBundleImportResult>;
}

/** 项目包导入。深模块负责清理 staging / 已落盘文件；本函数额外保证工作区连接关闭。 */
export function runBundleImport(
  coordinator: OperationCoordinator,
  options: BundleImportRunOptions,
  withWorkspaceClosed?: <R>(fn: () => Promise<R>) => Promise<R>
): StartedOperationHandle<ProjectBundleImportResult> {
  const started = coordinator.start("bundle.import", async (controller) => {
    const run = async () => {
      if (options.transact) {
        return importProjectBundleDirectory({
          workspaceDirectory: options.workspaceDirectory,
          bundleDirectory: options.bundleDirectory,
          cardResolutions: options.cardResolutions,
          operation: controller,
          transact: options.transact
        });
      }
      const workspace: CreationWorkspace = await openCreationWorkspace({ directory: options.workspaceDirectory });
      try {
        return await importProjectBundleDirectory({
          workspaceDirectory: options.workspaceDirectory,
          bundleDirectory: options.bundleDirectory,
          cardResolutions: options.cardResolutions,
          operation: controller,
          transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
        });
      } finally {
        await workspace.close();
      }
    };
    // 导入会重写工作区数据库；若创作工作区当前处于打开状态，关闭它以避免双连接冲突。
    return withWorkspaceClosed ? withWorkspaceClosed(run) : run();
  });
  return { operationId: started.operationId, done: started.done };
}

/* --------------------------- 加密长任务的公共辅助 --------------------------- */

/**
 * 代理 operation 控制器，记录进度中观察到的“最大字节总数”。
 * 多阶段长任务（明文快照 → 加密容器）需把后续阶段的字节计数整体平移到前一阶段
 * 之后，以满足协调器“字节单调不降”的不变量；此代理用于捕获前一阶段的字节上限。
 */
function recordMaxBytes(operation: OperationController): { op: OperationController; maxBytes: () => number } {
  let maxBytes = 0;
  const op: OperationController = {
    get operationId() {
      return operation.operationId;
    },
    get signal() {
      return operation.signal;
    },
    isCancellationRequested: () => operation.isCancellationRequested(),
    throwIfCancelled: () => operation.throwIfCancelled(),
    setPhase: (phase, opts) => operation.setPhase(phase, opts),
    setProgress: (progress) => {
      if (progress.bytesTotal !== null && progress.bytesTotal !== undefined && progress.bytesTotal > maxBytes) {
        maxBytes = progress.bytesTotal;
      }
      operation.setProgress(progress);
    },
    isInterruptible: () => operation.isInterruptible()
  };
  return { op, maxBytes: () => maxBytes };
}

/** 把某阶段的字节计数整体平移 base，使其在前一阶段字节上限之后单调递增。 */
function offsetBytesBy(operation: OperationController, base: number): OperationController {
  return {
    get operationId() {
      return operation.operationId;
    },
    get signal() {
      return operation.signal;
    },
    isCancellationRequested: () => operation.isCancellationRequested(),
    throwIfCancelled: () => operation.throwIfCancelled(),
    setPhase: (phase, opts) => operation.setPhase(phase, opts),
    setProgress: (progress) => {
      operation.setProgress({
        bytesCompleted: progress.bytesCompleted !== undefined ? base + progress.bytesCompleted : undefined,
        bytesTotal: progress.bytesTotal === null ? null : progress.bytesTotal !== undefined ? base + progress.bytesTotal : undefined,
        completed: progress.completed,
        total: progress.total,
        indeterminate: progress.indeterminate
      });
    },
    isInterruptible: () => operation.isInterruptible()
  };
}

/**
 * 把加密深模块的错误映射为 operation 错误：
 * - 保留 auth-failed / truncated / invalid-input / unsupported-format 等码；
 * - 取消统一为 OperationCancelledError（协调器据此置 cancelled 而非 failed）；
 * - 绝不透出口令、派生密钥或明文（深模块本身已保证，此处仅转发 message）。
 * 非加密错误（如恢复/导入深模块抛出的 OperationError）原样上抛，由协调器处理。
 */
function rethrowAsOperationError(error: unknown): never {
  if (error instanceof OperationCancelledError) throw error;
  if (error instanceof EncryptionError) {
    if (error.code === "cancelled") throw new OperationCancelledError(error.message);
    throw new OperationError(error.code, error.message);
  }
  throw error;
}

/* ------------------------------- 加密备份导出 ------------------------------- */

export interface EncryptedBackupExportOptions {
  appDataDirectory: string;
  libraryDirectory?: string;
  appVersion: string;
  platform: string;
  arch: string;
  createdAt: string;
  /** 输出的加密容器文件路径（建议扩展名 .crbackup）。 */
  targetFile: string;
  passphrase: string;
}

/** 加密备份导出：明文快照落到临时目录 → 加密为容器 → 清理临时目录。 */
export function runBackupExportEncrypted(
  coordinator: OperationCoordinator,
  options: EncryptedBackupExportOptions
): StartedOperationHandle<{ targetFile: string; fileCount: number; bytesTotal: number }> {
  const started = coordinator.start("backup.export-encrypted", async (controller) => {
    const plaintextRoot = await mkdtemp(path.join(os.tmpdir(), "cr-bk-enc-"));
    try {
      const recorder = recordMaxBytes(controller);
      await createBackupSnapshot({
        backupRoot: plaintextRoot,
        appDataDirectory: options.appDataDirectory,
        libraryDirectory: options.libraryDirectory,
        appVersion: options.appVersion,
        platform: options.platform,
        arch: options.arch,
        createdAt: options.createdAt,
        operation: recorder.op
      });
      const base = recorder.maxBytes();
      // 加密阶段：在快照提交之后推进到可中断的 cleanup 阶段，避免阶段回退。
      controller.setPhase("cleanup", { interruptible: true });
      const result = await encryptBackupSnapshotToContainer({
        backupRoot: plaintextRoot,
        targetFile: options.targetFile,
        passphrase: options.passphrase,
        operation: offsetBytesBy(controller, base)
      }).catch((error) => rethrowAsOperationError(error));
      return { targetFile: result.targetFile, fileCount: result.fileCount, bytesTotal: result.bytesTotal };
    } finally {
      await rm(plaintextRoot, { recursive: true, force: true });
    }
  });
  return { operationId: started.operationId, done: started.done };
}

/* ------------------------------- 加密备份导入 ------------------------------- */

export interface EncryptedBackupImportOptions {
  containerFile: string;
  passphrase: string;
  currentAppDataRoot: string;
  resolveLibraryTarget: () => Promise<string>;
}

/** 加密备份导入：解密到受控临时目录（完整性不通过则失败且不留半成品）→ 恢复。 */
export function runBackupImportEncrypted(
  coordinator: OperationCoordinator,
  options: EncryptedBackupImportOptions,
  withWorkspaceClosed?: <R>(fn: () => Promise<R>) => Promise<R>
): StartedOperationHandle<RestoreBackupResult> {
  const started = coordinator.start("backup.import-encrypted", async (controller) => {
    const recorder = recordMaxBytes(controller);
    const run = () =>
      withDecryptedBackupStaging(
        { containerFile: options.containerFile, passphrase: options.passphrase, operation: recorder.op },
        async (stagingDir) => {
          const base = recorder.maxBytes();
          return restoreBackupFromDirectory({
            backupRoot: stagingDir,
            currentAppDataRoot: options.currentAppDataRoot,
            resolveLibraryTarget: options.resolveLibraryTarget,
            operation: offsetBytesBy(controller, base)
          });
        }
      ).catch((error) => rethrowAsOperationError(error));
    return withWorkspaceClosed ? withWorkspaceClosed(run) : run();
  });
  return { operationId: started.operationId, done: started.done };
}

/* ------------------------------- 加密项目包导出 ----------------------------- */

export interface EncryptedBundleExportOptions {
  workspaceDirectory: string;
  data: ProjectBundleData;
  /** 输出的加密容器文件路径（建议扩展名 .crbundle）。 */
  targetFile: string;
  passphrase: string;
}

/** 加密项目包导出：明文项目包落到临时目录 → 加密为容器 → 清理临时目录。 */
export function runBundleExportEncrypted(
  coordinator: OperationCoordinator,
  options: EncryptedBundleExportOptions
): StartedOperationHandle<{ targetFile: string; fileCount: number; bytesTotal: number }> {
  const started = coordinator.start("bundle.export-encrypted", async (controller) => {
    const plaintextDir = await mkdtemp(path.join(os.tmpdir(), "cr-bundle-enc-"));
    try {
      const recorder = recordMaxBytes(controller);
      const bundle = await exportProjectBundleDirectory({
        workspaceDirectory: options.workspaceDirectory,
        data: options.data,
        targetDirectory: plaintextDir,
        operation: recorder.op
      });
      const base = recorder.maxBytes();
      controller.setPhase("cleanup", { interruptible: true });
      const result = await encryptBundleToContainer({
        bundleDirectory: bundle.directory,
        targetFile: options.targetFile,
        passphrase: options.passphrase,
        operation: offsetBytesBy(controller, base)
      }).catch((error) => rethrowAsOperationError(error));
      return { targetFile: result.targetFile, fileCount: result.fileCount, bytesTotal: result.bytesTotal };
    } finally {
      await rm(plaintextDir, { recursive: true, force: true });
    }
  });
  return { operationId: started.operationId, done: started.done };
}

/* ------------------------------- 加密项目包导入 ----------------------------- */

export interface EncryptedBundleImportOptions {
  workspaceDirectory: string;
  containerFile: string;
  passphrase: string;
  /** 解密后基于只读预检收集冲突决策；返回 null 表示用户取消且必须零写入。 */
  resolveCardResolutions?: (preview: ProjectBundleImportPreview) => Promise<ProjectBundleCardResolution[] | null>;
}

/** 加密项目包导入：解密到受控临时目录（完整性不通过则失败且不留半成品）→ 导入。 */
export function runBundleImportEncrypted(
  coordinator: OperationCoordinator,
  options: EncryptedBundleImportOptions,
  withWorkspaceClosed?: <R>(fn: () => Promise<R>) => Promise<R>
): StartedOperationHandle<ProjectBundleImportResult> {
  const started = coordinator.start("bundle.import-encrypted", async (controller) => {
    const recorder = recordMaxBytes(controller);
    const run = () =>
      withDecryptedBundleStaging(
        { containerFile: options.containerFile, passphrase: options.passphrase, operation: recorder.op },
        async (stagingDir) => {
          const base = recorder.maxBytes();
          const workspace = await openCreationWorkspace({ directory: options.workspaceDirectory });
          try {
            let cardResolutions: ProjectBundleCardResolution[] | undefined;
            if (options.resolveCardResolutions) {
              let data: ProjectBundleData;
              try {
                data = JSON.parse(await readFile(path.join(stagingDir, "project.json"), "utf8")) as ProjectBundleData;
              } catch {
                throw new OperationError("invalid-input", "项目包 project.json 无法解析。");
              }
              const preview = await workspace.previewProjectBundleImport(data);
              const resolved = await options.resolveCardResolutions(preview);
              if (resolved === null) throw new OperationCancelledError("已取消项目包导入。");
              cardResolutions = resolved;
            }
            return await importProjectBundleDirectory({
              workspaceDirectory: options.workspaceDirectory,
              bundleDirectory: stagingDir,
              cardResolutions,
              operation: offsetBytesBy(controller, base),
              transact: (command) => workspace.transact(command) as Promise<ProjectBundleImportResult>
            });
          } finally {
            await workspace.close();
          }
        }
      ).catch((error) => rethrowAsOperationError(error));
    return withWorkspaceClosed ? withWorkspaceClosed(run) : run();
  });
  return { operationId: started.operationId, done: started.done };
}
