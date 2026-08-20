/**
 * 长任务操作协调器统一出口。
 *
 * 仅暴露纯类型与状态机，不依赖 BrowserWindow / ipcMain / 任何 UI 设施；
 * renderer 进度接线由后续 IPC 层在调用方完成（轮询 getState / listStates）。
 */
export {
  OperationError,
  OperationCancelledError,
  OperationInvariantError
} from "./types";
export type {
  OperationKind,
  OperationPhase,
  OperationStatus,
  OperationProgress,
  OperationState,
  OperationResult,
  OperationController
} from "./types";
import { createOperationCoordinator } from "./coordinator";
export {
  OperationCoordinator,
  createOperationCoordinator,
  type StartOperationOptions,
  type StartedOperation
} from "./coordinator";
export {
  runBackupCreate,
  runBackupRestore,
  runBackupExportEncrypted,
  runBackupImportEncrypted,
  runBundleExport,
  runBundleImport,
  runBundleExportEncrypted,
  runBundleImportEncrypted,
  type BundleImportRunOptions,
  type EncryptedBackupExportOptions,
  type EncryptedBackupImportOptions,
  type EncryptedBundleExportOptions,
  type EncryptedBundleImportOptions,
  type StartedOperationHandle
} from "./operations";

/** 主进程级单例：备份 / 项目包集成入口共用。测试可改用 createOperationCoordinator() 隔离。 */
export const operationCoordinator = createOperationCoordinator();
