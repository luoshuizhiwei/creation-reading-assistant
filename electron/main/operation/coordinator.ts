import { randomUUID } from "node:crypto";
import {
  OperationCancelledError,
  OperationError,
  OperationInvariantError,
  type OperationController,
  type OperationKind,
  type OperationPhase,
  type OperationProgress,
  type OperationResult,
  type OperationState,
  type OperationStatus
} from "./types";

/** 阶段顺序即允许前进的方向；索引越小越早。 */
const PHASE_ORDER: readonly OperationPhase[] = [
  "validating",
  "scanning",
  "hashing",
  "copying",
  "database",
  "committing",
  "cleanup",
  "done"
];
const PHASE_INDEX: ReadonlyMap<OperationPhase, number> = new Map(PHASE_ORDER.map((phase, index) => [phase, index]));

/**
 * 各阶段默认可中断性。database（SQLite 事务）与 committing（最终原子 rename / 数据库替换）
 * 不可中断：进入后不得中途强杀，必须完成安全提交或延迟到下一个安全点。
 */
const DEFAULT_INTERRUPTIBLE: ReadonlyMap<OperationPhase, boolean> = new Map<OperationPhase, boolean>([
  ["validating", true],
  ["scanning", true],
  ["hashing", true],
  ["copying", true],
  ["database", false],
  ["committing", false],
  ["cleanup", true],
  ["done", false]
]);

const FINISHED_STATUSES: ReadonlySet<OperationStatus> = new Set<OperationStatus>(["completed", "cancelled", "failed"]);

interface OperationEntry {
  state: OperationState;
  controller: OperationControllerImpl;
  aborter: AbortController;
}

class OperationControllerImpl implements OperationController {
  readonly operationId: string;
  readonly signal: AbortSignal;
  private readonly entry: OperationEntry;
  private interruptible: boolean;
  /** 由协调器注入：每次进度 / 阶段变化后推送快照给订阅者。 */
  notify: () => void = (): void => {};

  constructor(operationId: string, aborter: AbortController, entry: OperationEntry) {
    this.operationId = operationId;
    this.signal = aborter.signal;
    this.entry = entry;
    this.interruptible = DEFAULT_INTERRUPTIBLE.get("validating") ?? true;
  }

  isCancellationRequested(): boolean {
    return this.signal.aborted;
  }

  isInterruptible(): boolean {
    return this.interruptible;
  }

  throwIfCancelled(): void {
    if (this.isCancellationRequested() && this.interruptible) {
      throw new OperationCancelledError();
    }
  }

  setPhase(phase: OperationPhase, options?: { interruptible?: boolean }): void {
    const current = this.entry.state.progress.phase;
    const targetIndex = PHASE_INDEX.get(phase);
    const currentIndex = PHASE_INDEX.get(current);
    if (targetIndex === undefined || currentIndex === undefined) {
      throw new OperationInvariantError(`未知阶段：${phase}`);
    }
    if (targetIndex < currentIndex) {
      throw new OperationInvariantError(`阶段不允许回退：${current} → ${phase}`);
    }
    const interruptible = options?.interruptible ?? (DEFAULT_INTERRUPTIBLE.get(phase) ?? true);
    this.interruptible = interruptible;
    this.entry.state.progress.phase = phase;
    this.notify();
  }

  setProgress(progress: {
    completed?: number;
    total?: number | null;
    bytesCompleted?: number;
    bytesTotal?: number | null;
    indeterminate?: boolean;
  }): void {
    const current = this.entry.state.progress;

    if (progress.total !== undefined) {
      if (progress.total !== null && !Number.isInteger(progress.total)) {
        throw new OperationInvariantError("total 必须为整数或 null。");
      }
      if (progress.total !== null && progress.total < 0) {
        throw new OperationInvariantError("total 不能为负。");
      }
      if (current.total !== null && progress.total !== null && progress.total < current.total) {
        throw new OperationInvariantError("total 不允许减少。");
      }
      current.total = progress.total;
    }

    if (progress.completed !== undefined) {
      if (!Number.isInteger(progress.completed) || progress.completed < 0) {
        throw new OperationInvariantError("completed 必须为非负整数。");
      }
      if (progress.completed < current.completed) {
        throw new OperationInvariantError("completed 不允许倒退。");
      }
      if (current.total !== null && progress.completed > current.total) {
        throw new OperationInvariantError("completed 不得超过 total。");
      }
      current.completed = progress.completed;
    }

    if (progress.bytesTotal !== undefined) {
      if (progress.bytesTotal !== null && !Number.isInteger(progress.bytesTotal)) {
        throw new OperationInvariantError("bytesTotal 必须为整数或 null。");
      }
      if (progress.bytesTotal !== null && progress.bytesTotal < 0) {
        throw new OperationInvariantError("bytesTotal 不能为负。");
      }
      if (current.bytesTotal !== null && progress.bytesTotal !== null && progress.bytesTotal < current.bytesTotal) {
        throw new OperationInvariantError("bytesTotal 不允许减少。");
      }
      current.bytesTotal = progress.bytesTotal;
    }

    if (progress.bytesCompleted !== undefined) {
      if (!Number.isInteger(progress.bytesCompleted) || progress.bytesCompleted < 0) {
        throw new OperationInvariantError("bytesCompleted 必须为非负整数。");
      }
      if (progress.bytesCompleted < current.bytesCompleted) {
        throw new OperationInvariantError("bytesCompleted 不允许倒退。");
      }
      if (current.bytesTotal !== null && progress.bytesCompleted > current.bytesTotal) {
        throw new OperationInvariantError("bytesCompleted 不得超过 bytesTotal。");
      }
      current.bytesCompleted = progress.bytesCompleted;
    }

    if (progress.indeterminate !== undefined) {
      current.indeterminate = progress.indeterminate;
    }
    this.notify();
  }
}

export interface StartOperationOptions {
  now?: () => string;
}

export interface StartedOperation<T = unknown> {
  operationId: string;
  /** 完成时 resolve；结果含最终 status（completed / cancelled / failed）与产物或错误。 */
  done: Promise<OperationResult<T>>;
}

export class OperationCoordinator {
  private readonly entries = new Map<string, OperationEntry>();
  private readonly listeners = new Map<string, Set<(state: OperationState) => void>>();
  /**
   * 终态快照短时缓存：任务可能刚好在 renderer 的 start → subscribe 两次 IPC 之间完成，
   * 事件在 listener 注册前发出会丢失，导致 UI 永久显示“运行中”。缓存让晚到的
   * subscribe / getState 仍能取到终态；条目惰性过期清理，不长期驻留。
   */
  private readonly finished = new Map<string, { state: OperationState; until: number }>();
  private static readonly FINISHED_TTL_MS = 30_000;

  private pruneFinished(now = Date.now()): void {
    for (const [id, record] of this.finished) {
      if (record.until <= now) this.finished.delete(id);
    }
  }

  private pushFinished(state: OperationState): void {
    this.pruneFinished();
    this.finished.set(state.operationId, { state: cloneState(state), until: Date.now() + OperationCoordinator.FINISHED_TTL_MS });
  }

  /** 当前注册表中的 activity 快照；结束后条目即被移除，故正常不应包含 finished 状态。 */
  listStates(): OperationState[] {
    return Array.from(this.entries.values(), (entry) => cloneState(entry.state));
  }

  getState(operationId: string): OperationState | undefined {
    const entry = this.entries.get(operationId);
    if (entry) return cloneState(entry.state);
    this.pruneFinished();
    const finishedRecord = this.finished.get(operationId);
    return finishedRecord ? cloneState(finishedRecord.state) : undefined;
  }

  has(operationId: string): boolean {
    return this.entries.has(operationId);
  }

  /**
   * 订阅某任务的运行态快照；返回退订函数。UI 按 operationId 过滤，忽略迟到事件。
   * 注册后立即用当前状态（运行中或已终态）回调一次 listener，避免“事件先于
   * listener 注册发出”的丢失窗口；任务结束后 30 秒内晚到订阅仍能收到终态。
   */
  subscribe(operationId: string, listener: (state: OperationState) => void): () => void {
    let set = this.listeners.get(operationId);
    if (!set) {
      set = new Set();
      this.listeners.set(operationId, set);
    }
    set.add(listener);
    const current = this.getState(operationId);
    if (current) {
      try {
        listener(current);
      } catch {
        // 单个 listener 抛错不影响协调器与其他订阅者。
      }
    }
    return () => {
      set?.delete(listener);
      if (set && set.size === 0) this.listeners.delete(operationId);
    };
  }

  private notify(operationId: string): void {
    const set = this.listeners.get(operationId);
    if (!set || set.size === 0) return;
    const state = this.entries.get(operationId)?.state;
    if (!state) return;
    const snapshot = cloneState(state);
    for (const listener of set) listener(snapshot);
  }

  /**
   * 请求取消。返回 true 表示“针对一个仍在运行的操作发起了取消（含已处于 cancelling）”；
   * 返回 false 表示 operationId 不存在或已结束（幂等无副作用）。
   */
  requestCancel(operationId: string): boolean {
    const entry = this.entries.get(operationId);
    if (!entry) return false;
    if (FINISHED_STATUSES.has(entry.state.status)) return false;
    entry.aborter.abort();
    if (entry.state.status === "running") {
      entry.state.status = "cancelling";
    }
    this.notify(operationId);
    return true;
  }

  /** 退出前请求取消所有进行中的任务（不可中断阶段由深模块保证自身一致）。 */
  terminateAll(): void {
    for (const id of this.entries.keys()) {
      const entry = this.entries.get(id);
      if (entry && !FINISHED_STATUSES.has(entry.state.status)) this.requestCancel(id);
    }
  }

  /**
   * 启动一个 operation。run 在可中断阶段应周期性调用 controller.throwIfCancelled()；
   * 在不可中断阶段该方法为安全无操作，取消会被延迟到安全边界后以 deferredCancel 形式完成。
   */
  start<T>(
    kind: OperationKind,
    run: (controller: OperationController) => Promise<T>,
    options?: StartOperationOptions
  ): StartedOperation<T> {
    const operationId = `op-${randomUUID()}`;
    const now = options?.now ?? ((): string => new Date().toISOString());
    const aborter = new AbortController();
    const initialProgress: OperationProgress = {
      phase: "validating",
      completed: 0,
      total: null,
      bytesCompleted: 0,
      bytesTotal: null,
      indeterminate: true
    };
    const state: OperationState = {
      operationId,
      kind,
      status: "running",
      progress: initialProgress,
      startedAt: now(),
      deferredCancel: false
    };
    const entry: OperationEntry = {
      state,
      aborter,
      controller: undefined as unknown as OperationControllerImpl
    };
    entry.controller = new OperationControllerImpl(operationId, aborter, entry);
    entry.controller.notify = (): void => this.notify(operationId);
    this.entries.set(operationId, entry);

    const finalize = (status: OperationStatus, extra?: Partial<OperationState>): void => {
      state.status = status;
      state.finishedAt = now();
      state.progress.phase = "done";
      if (extra) Object.assign(state, extra);
      this.notify(operationId);
      this.entries.delete(operationId);
      this.pushFinished(state);
    };

    const done: Promise<OperationResult<T>> = (async (): Promise<OperationResult<T>> => {
      try {
        const result = await run(entry.controller);
        if (entry.controller.isCancellationRequested()) {
          // 取消恰好落在不可中断阶段：已安全提交，无法撤销，以 completed + deferredCancel 收尾。
          finalize("completed", { deferredCancel: true, result: { status: "completed", deferredCancel: true, result } });
          return { status: "completed", deferredCancel: true, result };
        }
        finalize("completed", { result: { status: "completed", result } });
        return { status: "completed", result };
      } catch (error) {
        if (error instanceof OperationCancelledError) {
          finalize("cancelled", { result: { status: "cancelled" } });
          return { status: "cancelled" };
        }
        const code = error instanceof OperationError ? error.code : "failed";
        const message = error instanceof Error ? error.message : String(error);
        finalize("failed", { error: { code, message }, result: { status: "failed", error: { code, message } } });
        return { status: "failed", error: { code, message } };
      }
    })();

    return { operationId, done };
  }
}

function cloneState(state: OperationState): OperationState {
  return {
    operationId: state.operationId,
    kind: state.kind,
    status: state.status,
    progress: { ...state.progress },
    startedAt: state.startedAt,
    finishedAt: state.finishedAt,
    error: state.error ? { ...state.error } : undefined,
    deferredCancel: state.deferredCancel,
    result: state.result
  };
}

export function createOperationCoordinator(): OperationCoordinator {
  return new OperationCoordinator();
}
