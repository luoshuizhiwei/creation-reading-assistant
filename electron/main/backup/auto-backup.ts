import { existsSync } from "node:fs";
import { realpath } from "node:fs/promises";
import path from "node:path";

/**
 * 自动备份深模块：每日 / 升级前自动完整备份的纯逻辑与调度器。
 *
 * 与备份深模块（backup/index.ts 的 createBackupSnapshot）协作：本模块只负责
 * “何时备份、备份到哪里、是否安全、防重入”，实际快照创建由调用方注入的
 * runBackup 完成（内部复用 createBackupSnapshot 的 v2 校验实现）。
 *
 * 错误约定：对外只抛 AutoBackupError（可读中文、不含内部堆栈与绝对路径），
 * 失败状态由 onFailure 持久化，renderer 可见但绝不周期性弹窗。
 */

export const AUTO_BACKUP_MIN_INTERVAL_MS = 24 * 60 * 60 * 1000;
export const AUTO_BACKUP_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;
export const AUTO_BACKUP_INITIAL_CHECK_DELAY_MS = 5_000;

export interface AutoBackupState {
  enabled: boolean;
  backupDirectory?: string;
  lastAutoBackupAt?: string;
}

export type AutoBackupEvalReason = "disabled" | "no-directory" | "not-due" | "due";

export interface AutoBackupEvaluation {
  shouldRun: boolean;
  reason: AutoBackupEvalReason;
}

export interface AutoBackupRunInput {
  backupRoot: string;
  createdAt: string;
}

export interface AutoBackupRunResult {
  backupRoot: string;
  createdAt: string;
}

/** 可读错误：不携带内部堆栈，避免 renderer 暴露实现细节。 */
export class AutoBackupError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AutoBackupError";
  }
}

export function readableBackupErrorMessage(error: unknown): string {
  if (error instanceof AutoBackupError) return error.message;
  const message = error instanceof Error ? error.message : String(error);
  return message.trim() ? message : "自动备份失败。";
}

/** 距上次成功 >= minIntervalMs（默认 24 小时）或从未成功过则到期。 */
export function isAutoBackupDue(
  lastAutoBackupAt: string | undefined,
  nowMs: number,
  minIntervalMs = AUTO_BACKUP_MIN_INTERVAL_MS
): boolean {
  if (!lastAutoBackupAt) return true;
  const lastMs = Date.parse(lastAutoBackupAt);
  if (Number.isNaN(lastMs)) return true;
  return nowMs - lastMs >= minIntervalMs;
}

/** 调度器周期性检查的完整判定：启用、有目录、到期三者缺一不可。 */
export function evaluateAutoBackup(
  state: AutoBackupState,
  nowMs: number,
  minIntervalMs = AUTO_BACKUP_MIN_INTERVAL_MS
): AutoBackupEvaluation {
  if (state.enabled !== true) return { shouldRun: false, reason: "disabled" };
  if (!state.backupDirectory || !state.backupDirectory.trim()) {
    return { shouldRun: false, reason: "no-directory" };
  }
  if (!isAutoBackupDue(state.lastAutoBackupAt, nowMs, minIntervalMs)) {
    return { shouldRun: false, reason: "not-due" };
  }
  return { shouldRun: true, reason: "due" };
}

/** 升级前备份触发条件：启用且已配置目录（不受 24 小时窗口限制）。 */
export function shouldBackupBeforeUpgrade(state: Pick<AutoBackupState, "enabled" | "backupDirectory">): boolean {
  return state.enabled === true && typeof state.backupDirectory === "string" && state.backupDirectory.trim() !== "";
}

function resolveInside(parentPath: string, childPath: string): boolean {
  const normalize = (value: string): string => {
    const resolved = path.resolve(value);
    return process.platform === "win32" ? resolved.toLowerCase() : resolved;
  };
  const parent = normalize(parentPath);
  const child = normalize(childPath);
  return child === parent || child.startsWith(`${parent}${path.sep}`);
}

/**
 * 目标目录安全校验：拒绝磁盘根、应用数据目录内/包含应用数据目录、
 * 书库目录内/包含书库目录等重叠目标。返回规范化后的目标目录。
 */
export function assertSafeBackupTarget(targetDirectory: string, appDataRoot: string, libraryRoot: string): string {
  if (!targetDirectory || !targetDirectory.trim()) {
    throw new AutoBackupError("备份目录不能为空。");
  }
  const resolved = path.resolve(targetDirectory);
  if (resolved === path.parse(resolved).root) {
    throw new AutoBackupError("备份目录不能选择磁盘根目录。");
  }
  if (resolveInside(appDataRoot, resolved)) {
    throw new AutoBackupError("备份目录不能位于应用数据目录内。");
  }
  if (resolveInside(resolved, appDataRoot)) {
    throw new AutoBackupError("备份目录不能包含应用数据目录。");
  }
  if (resolveInside(libraryRoot, resolved)) {
    throw new AutoBackupError("备份目录不能位于书库目录内。");
  }
  if (resolveInside(resolved, libraryRoot)) {
    throw new AutoBackupError("备份目录不能包含书库目录。");
  }
  return resolved;
}

/**
 * 执行前重新解析真实路径，防止目录被替换为 junction/symlink 后绕过文本路径校验。
 * 自动备份目录必须仍然存在；失效目录会留下可见失败状态而不会静默重建到意外位置。
 */
export async function assertSafeExistingBackupTarget(
  targetDirectory: string,
  appDataRoot: string,
  libraryRoot: string
): Promise<string> {
  assertSafeBackupTarget(targetDirectory, appDataRoot, libraryRoot);
  try {
    const [targetReal, appDataReal, libraryReal] = await Promise.all([
      realpath(targetDirectory),
      realpath(appDataRoot),
      realpath(libraryRoot)
    ]);
    return assertSafeBackupTarget(targetReal, appDataReal, libraryReal);
  } catch (error) {
    if (error instanceof AutoBackupError) throw error;
    throw new AutoBackupError("备份目录不存在或无法访问，请重新选择备份目录。");
  }
}

function timestampForFile(date: Date): string {
  const pad = (value: number): string => String(value).padStart(2, "0");
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

/** 生成唯一时间戳 v2 备份目录；同秒冲突时追加 -2/-3 后缀去重。 */
export function uniqueAutoBackupRoot(targetDirectory: string, now: Date): string {
  const base = path.join(targetDirectory, `CreationReadingAssistant-backup-${timestampForFile(now)}`);
  if (!existsSync(base)) return base;
  let index = 2;
  let candidate = `${base}-${index}`;
  while (existsSync(candidate)) {
    index += 1;
    candidate = `${base}-${index}`;
  }
  return candidate;
}

export interface AutoBackupSchedulerOptions {
  readState: () => Promise<AutoBackupState>;
  resolveRoots: () => { appDataRoot: string; libraryRoot: string };
  runBackup: (input: AutoBackupRunInput) => Promise<void>;
  validateTarget?: (targetDirectory: string, appDataRoot: string, libraryRoot: string) => Promise<string>;
  onSuccess: (at: string) => Promise<void>;
  onFailure: (at: string, errorMessage: string) => Promise<void>;
  now?: () => Date;
  initialDelayMs?: number;
  checkIntervalMs?: number;
  minIntervalMs?: number;
}

export interface AutoBackupScheduler {
  /** 启动后延时检查一次，之后低频周期检查。 */
  start(): void;
  /** 退出时清理定时器；进行中的备份由调用方维护锁队列自然收尾。 */
  dispose(): void;
  /** 立即执行一次（绕过 24 小时窗口）；进程内防重入，并发调用共享同一 Promise。 */
  runNow(): Promise<AutoBackupRunResult>;
  /** 升级下载前调用：启用且已配置目录则必须先成功备份，失败抛出可读错误阻断下载。 */
  backupBeforeUpgrade(): Promise<void>;
}

export function createAutoBackupScheduler(options: AutoBackupSchedulerOptions): AutoBackupScheduler {
  const nowFn = options.now ?? ((): Date => new Date());
  const initialDelayMs = options.initialDelayMs ?? AUTO_BACKUP_INITIAL_CHECK_DELAY_MS;
  const checkIntervalMs = options.checkIntervalMs ?? AUTO_BACKUP_CHECK_INTERVAL_MS;
  const minIntervalMs = options.minIntervalMs ?? AUTO_BACKUP_MIN_INTERVAL_MS;

  let running: Promise<AutoBackupRunResult> | null = null;
  let initialTimer: NodeJS.Timeout | null = null;
  let intervalTimer: NodeJS.Timeout | null = null;
  let disposed = false;

  const execute = async (): Promise<AutoBackupRunResult> => {
    const attempt = nowFn();
    const createdAt = attempt.toISOString();
    try {
      const state = await options.readState();
      if (state.enabled !== true) throw new AutoBackupError("自动备份未启用。");
      if (!state.backupDirectory || !state.backupDirectory.trim()) {
        throw new AutoBackupError("尚未配置自动备份目录。");
      }
      const roots = options.resolveRoots();
      const targetDirectory = options.validateTarget
        ? await options.validateTarget(state.backupDirectory, roots.appDataRoot, roots.libraryRoot)
        : assertSafeBackupTarget(state.backupDirectory, roots.appDataRoot, roots.libraryRoot);
      const backupRoot = uniqueAutoBackupRoot(targetDirectory, attempt);
      await options.runBackup({ backupRoot, createdAt });
      await options.onSuccess(createdAt);
      return { backupRoot, createdAt };
    } catch (error) {
      const message = readableBackupErrorMessage(error);
      try {
        await options.onFailure(createdAt, message);
      } catch {
        // 状态持久化失败不覆盖原始错误，也不向 renderer 暴露持久化细节。
      }
      throw new AutoBackupError(message);
    }
  };

  const runNow = (): Promise<AutoBackupRunResult> => {
    if (running) return running;
    running = execute().finally(() => {
      running = null;
    });
    return running;
  };

  const runCheck = async (): Promise<void> => {
    if (running || disposed) return;
    let state: AutoBackupState;
    try {
      state = await options.readState();
    } catch {
      try {
        await options.onFailure(nowFn().toISOString(), "无法读取自动备份设置。");
      } catch {
        // 周期任务不弹骚扰框，也不产生未处理拒绝。
      }
      return;
    }
    const evaluation = evaluateAutoBackup(state, nowFn().getTime(), minIntervalMs);
    if (!evaluation.shouldRun) return;
    try {
      await runNow();
    } catch {
      // execute 已持久化执行期失败。
    }
  };

  const start = (): void => {
    if (disposed || initialTimer || intervalTimer) return;
    initialTimer = setTimeout(() => {
      initialTimer = null;
      void runCheck();
      if (!disposed) {
        intervalTimer = setInterval(() => void runCheck(), checkIntervalMs);
      }
    }, initialDelayMs);
  };

  const dispose = (): void => {
    disposed = true;
    if (initialTimer) {
      clearTimeout(initialTimer);
      initialTimer = null;
    }
    if (intervalTimer) {
      clearInterval(intervalTimer);
      intervalTimer = null;
    }
  };

  const backupBeforeUpgrade = async (): Promise<void> => {
    const state = await options.readState();
    if (!shouldBackupBeforeUpgrade(state)) return;
    await runNow();
  };

  return { start, dispose, runNow, backupBeforeUpgrade };
}
