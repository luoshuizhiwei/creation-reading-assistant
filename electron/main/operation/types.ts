/**
 * 长任务操作协调器类型定义。
 *
 * 本模块是备份 / 项目包等“重 IO + 多阶段”主进程任务的统一类型安全骨架。
 * 设计约束（来自 P1 收尾规格）：
 * - 每个 operation 拥有唯一 operationId、kind、明确状态与阶段；
 * - 阶段、计数、字节数只能单调前进（违反即抛 OperationInvariantError）；
 * - 每个 operation 拥有独立的 AbortController 作为取消令牌，绝不使用全局 cancelled 布尔；
 * - scanning / hashing / copying / validating / cleanup 为可中断阶段，database / committing 为不可中断阶段；
 * - 不引入 setTimeout / sleep 伪造进度，不把整文件读入内存计算进度，不在 SQLite 事务内同步发事件，
 *   不依赖 BrowserWindow / ipcMain 等 UI 设施（进度通过状态机轮询暴露，由 IPC 层另行接线）。
 */

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

/** 阶段顺序即单调前进的允许方向；不可反向。 */
export type OperationPhase =
  | "validating"
  | "scanning"
  | "hashing"
  | "copying"
  | "database"
  | "committing"
  | "cleanup"
  | "done";

export type OperationStatus = "running" | "cancelling" | "completed" | "cancelled" | "failed";

export interface OperationProgress {
  phase: OperationPhase;
  /** 已完成的条目计数；null total 时为不确定计数。 */
  completed: number;
  /** 总条目计数；null 表示不可计算（indeterminate）。 */
  total: number | null;
  /** 已复制/哈希的字节数。 */
  bytesCompleted: number;
  /** 总字节数；null 表示不可计算。 */
  bytesTotal: number | null;
  indeterminate: boolean;
}

export interface OperationState {
  operationId: string;
  kind: OperationKind;
  status: OperationStatus;
  progress: OperationProgress;
  startedAt: string;
  finishedAt?: string;
  error?: { code: string; message: string };
  /**
   * 若取消请求恰好落在不可中断阶段（database / committing），操作会先完成安全提交，
   * 再在结束时标记 deferredCancel=true 并以 completed 收尾（不产生半成品，但无法撤销已提交结果）。
   */
  deferredCancel: boolean;
  /** 终态时携带 OperationResult，便于 UI 展示结果（如资源扫描报告）。 */
  result?: OperationResult<unknown>;
}

export interface OperationResult<T = unknown> {
  status: OperationStatus;
  deferredCancel?: boolean;
  result?: T;
  error?: { code: string; message: string };
}

export class OperationError extends Error {
  readonly code: string;
  constructor(code: string, message: string) {
    super(message);
    this.name = "OperationError";
    this.code = code;
  }
}

/** 可中断阶段内被取消时抛出；协调器据此将状态置为 cancelled。 */
export class OperationCancelledError extends OperationError {
  constructor(message = "操作已取消。") {
    super("cancelled", message);
    this.name = "OperationCancelledError";
  }
}

/** 状态机不变量被破坏（阶段回退、计数/字节倒退或越界）时抛出，便于尽早暴露 bug。 */
export class OperationInvariantError extends OperationError {
  constructor(message: string) {
    super("invariant", message);
    this.name = "OperationInvariantError";
  }
}

/** 运行期交给任务函数的控制器：仅用于上报进度与查询/触发取消。 */
export interface OperationController {
  readonly operationId: string;
  readonly signal: AbortSignal;
  /** 是否已请求取消（不论当前阶段是否可中断）。 */
  isCancellationRequested(): boolean;
  /** 仅当“已请求取消且当前阶段可中断”时抛出 OperationCancelledError；不可中断阶段为安全无操作。 */
  throwIfCancelled(): void;
  /** 切换阶段：必须单调前进；可覆盖该阶段的默认可中断性。 */
  setPhase(phase: OperationPhase, options?: { interruptible?: boolean }): void;
  /** 单调上报进度；completed/bytesCompleted 不得倒退，且不得超过已知 total/bytesTotal。 */
  setProgress(progress: {
    completed?: number;
    total?: number | null;
    bytesCompleted?: number;
    bytesTotal?: number | null;
    indeterminate?: boolean;
  }): void;
  /** 当前阶段是否可中断（供深模块在关键边界自行决策）。 */
  isInterruptible(): boolean;
}
