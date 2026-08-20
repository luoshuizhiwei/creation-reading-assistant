/**
 * 长任务（operation）在 renderer / IPC / 主进程之间的统一、可序列化类型。
 *
 * 这些类型镜像主进程 `electron/main/operation` 中的运行态，但只保留可跨进程
 * 传输的字段，且不包含任何函数 / 句柄。operationId 必须贯穿三层。
 *
 * 注意：进度字段以主进程协调器为准（phase / completed / total / bytesCompleted
 * / bytesTotal / indeterminate），不使用 stage / percent / count 等前端臆造字段，
 * 以免伪造进度。
 */

/** 受支持的长任务种类。新增种类需同步主进程 runner 与 IPC 分发。 */
export type OperationKind =
  | "backup.create"
  | "backup.restore"
  | "backup.export-encrypted"
  | "backup.import-encrypted"
  | "bundle.export"
  | "bundle.import"
  | "bundle.export-encrypted"
  | "bundle.import-encrypted"
  | "resource.scan";

/** 任务生命周期状态。cancelling 表示已请求取消，正在安全边界收尾。 */
export type OperationStatus = "running" | "cancelling" | "completed" | "cancelled" | "failed";

/**
 * 阶段。进入不可中断阶段（database / committing）后取消被禁用，关闭对话框也不会
 * 杀死任务，取消会被延迟到安全边界后以 deferredCancel 形式完成。
 */
export type OperationPhase =
  | "validating"
  | "scanning"
  | "hashing"
  | "copying"
  | "database"
  | "committing"
  | "cleanup"
  | "done";

/** 不可中断的提交阶段：进入后取消被禁用。 */
export const NON_INTERRUPTIBLE_PHASES: ReadonlyArray<OperationPhase> = ["database", "committing"];

/** 进度信息（镜像主进程协调器）。indeterminate 为真时不得显示伪造百分比。 */
export interface OperationProgress {
  phase: OperationPhase;
  completed: number;
  total: number | null;
  bytesCompleted: number;
  bytesTotal: number | null;
  indeterminate: boolean;
}

export interface OperationError {
  code: string;
  message: string;
}

/** operation 的最终结果包（镜像主进程 OperationResult）。result 为深模块产物。 */
export interface OperationResult<T = unknown> {
  status: OperationStatus;
  deferredCancel?: boolean;
  result?: T;
  error?: OperationError;
}

/** 跨进程运行态。result 的 result 值类型取决于 kind，UI 按需断言。 */
export interface OperationState<T = unknown> {
  operationId: string;
  kind: OperationKind;
  status: OperationStatus;
  progress: OperationProgress;
  startedAt: string;
  finishedAt?: string;
  deferredCancel: boolean;
  result?: OperationResult<T> | null;
  error?: OperationError | null;
}

/** renderer 发起任务的标准化请求（全部字段可序列化）。 */
export type OperationStartRequest =
  | { kind: "backup.create" }
  | { kind: "backup.restore" }
  | { kind: "backup.export-encrypted"; passphrase: string }
  | { kind: "backup.import-encrypted"; passphrase: string }
  | { kind: "bundle.export"; projectId: string }
  | { kind: "bundle.import" }
  | { kind: "bundle.export-encrypted"; projectId: string; passphrase: string }
  | { kind: "bundle.import-encrypted"; passphrase: string }
  | { kind: "resource.scan" };

/* ----------------------------- 资源完整性扫描 ----------------------------- */

/** 资源完整性问题分类（只读扫描，绝不涉及删除 / 修复）。 */
export type ResourceIntegrityCategory =
  | "file-missing"
  | "file-unreferenced"
  | "size-mismatch"
  | "hash-mismatch"
  | "unsafe-relative-path"
  | "symlink-escape"
  | "duplicate-path"
  | "record-conflict";

/** 单条资源完整性问题。relativePath 始终为工作区相对路径，不含绝对前缀。 */
export interface ResourceIntegrityIssue {
  type: ResourceIntegrityCategory;
  resourceId?: string;
  relativePath: string;
  message: string;
}

/** 资源完整性扫描报告（只读清单，不含任何绝对路径）。 */
export interface ResourceIntegrityReport {
  issues: ResourceIntegrityIssue[];
  scannedRecordCount: number;
  scannedFileCount: number;
}

export const RESOURCE_INTEGRITY_CATEGORY_LABEL: Record<ResourceIntegrityCategory, string> = {
  "file-missing": "文件缺失",
  "file-unreferenced": "未引用文件",
  "size-mismatch": "大小不符",
  "hash-mismatch": "哈希不符",
  "unsafe-relative-path": "路径越界",
  "symlink-escape": "符号链接越界",
  "duplicate-path": "重复路径",
  "record-conflict": "记录冲突"
};

export const RESOURCE_INTEGRITY_CATEGORY_ORDER: ReadonlyArray<ResourceIntegrityCategory> = [
  "file-missing",
  "unsafe-relative-path",
  "symlink-escape",
  "size-mismatch",
  "hash-mismatch",
  "duplicate-path",
  "record-conflict",
  "file-unreferenced"
];
