/**
 * 写作会话跟踪器（独立 module，不依赖 React / IPC / 主进程）。
 *
 * 设计约束（来自 P1 目标/统计/会话修正规格）：
 * - 只在「输入 / 有意义的选择 / 结构操作」三种显式信号发生时计时；
 *   鼠标移动、焦点切换等无意义事件由模块 API 面天然排除（模块不提供这些信号）；
 * - 绝不记录按键内容、选中文本或正文内容：所有 API 只接受「字符数（数字）」
 *   与场景 ID，净增字数 = 最新字符数 - 段起始字符数；
 * - 空闲超过 idleTimeoutMs（默认 5 分钟）自动结算当前段并上报；
 *   空闲后再活动时创建新段（重新活动创建新段）；
 * - 段（segment）以场景为归属：场景切换先结算旧段再开新段；
 * - settle() 显式结算（切项目 / 窗口隐藏 / 卸载 / 异常关闭），结算后段清空；
 * - 不足 minActiveMs 的段不上报（丢弃噪声段）。
 *
 * 口径说明：段活动时长 = 最后活动时刻 - 段开始时刻（与主进程
 * `writing_sessions.active_seconds` 及统计口径一致），空闲结算保证间隙不超过
 * 空闲阈值。
 */

export type SessionActivityKind = "input" | "selection" | "structure";

/** 结算原因：idle=空闲超时；scene-switch=场景切换；settle=显式结算（切项目/隐藏/卸载等）。 */
export type SessionSettleReason = "idle" | "scene-switch" | "settle";

export interface SessionSettleReport {
  sceneId: string | null;
  startedAt: number;
  activeMs: number;
  netChars: number;
  reason: SessionSettleReason;
  /** 本段内发生过的活动种类（去重）。 */
  activityKinds: SessionActivityKind[];
}

export interface WritingSessionTrackerOptions {
  /** 空闲多久结算当前段（毫秒），默认 5 分钟。 */
  idleTimeoutMs?: number;
  /** 不足该时长的段不结算（毫秒），默认 1000。 */
  minActiveMs?: number;
  now?: () => number;
  /** 段结算时回调（不足最短时长的段不回调）。 */
  onSettle: (report: SessionSettleReport) => void;
}

export interface ActiveSegmentInfo {
  sceneId: string | null;
  startedAt: number;
  lastActivity: number;
  netChars: number;
}

export interface WritingSessionTracker {
  /** 上报一次写作活动。charCount 为当前场景（或连续模式合计）的字符数，不含任何内容。 */
  signalActivity(kind: SessionActivityKind, sceneId: string | null, charCount: number): void;
  /** 空闲检查（由调用方定时触发，如 30 秒间隔）；超时自动结算。 */
  checkIdle(): void;
  /** 显式结算当前段（切项目 / 窗口隐藏 / 卸载 / 异常关闭）。 */
  settle(reason?: SessionSettleReason): void;
  hasActiveSegment(): boolean;
  getActiveSegment(): ActiveSegmentInfo | null;
}

interface Segment {
  sceneId: string | null;
  startedAt: number;
  lastActivity: number;
  startChars: number;
  lastChars: number;
  kinds: Set<SessionActivityKind>;
}

export const SESSION_DEFAULT_IDLE_TIMEOUT_MS = 5 * 60 * 1000;
export const SESSION_DEFAULT_MIN_ACTIVE_MS = 1000;

export function createWritingSessionTracker(options: WritingSessionTrackerOptions): WritingSessionTracker {
  const idleTimeoutMs = options.idleTimeoutMs ?? SESSION_DEFAULT_IDLE_TIMEOUT_MS;
  const minActiveMs = options.minActiveMs ?? SESSION_DEFAULT_MIN_ACTIVE_MS;
  const nowFn = options.now ?? ((): number => Date.now());
  let segment: Segment | null = null;

  const flush = (reason: SessionSettleReason): void => {
    if (!segment) return;
    const current = segment;
    segment = null;
    const activeMs = Math.max(0, current.lastActivity - current.startedAt);
    if (activeMs < minActiveMs) return;
    options.onSettle({
      sceneId: current.sceneId,
      startedAt: current.startedAt,
      activeMs,
      netChars: current.lastChars - current.startChars,
      reason,
      activityKinds: [...current.kinds]
    });
  };

  return {
    signalActivity(kind, sceneId, charCount) {
      const now = nowFn();
      if (!segment) {
        segment = {
          sceneId,
          startedAt: now,
          lastActivity: now,
          startChars: charCount,
          lastChars: charCount,
          kinds: new Set([kind])
        };
        return;
      }
      if (segment.sceneId !== sceneId) {
        flush("scene-switch");
        segment = {
          sceneId,
          startedAt: now,
          lastActivity: now,
          startChars: charCount,
          lastChars: charCount,
          kinds: new Set([kind])
        };
        return;
      }
      segment.lastActivity = now;
      segment.lastChars = charCount;
      segment.kinds.add(kind);
    },

    checkIdle() {
      if (!segment) return;
      if (nowFn() - segment.lastActivity >= idleTimeoutMs) flush("idle");
    },

    settle(reason = "settle") {
      flush(reason);
    },

    hasActiveSegment() {
      return segment !== null;
    },

    getActiveSegment() {
      if (!segment) return null;
      return {
        sceneId: segment.sceneId,
        startedAt: segment.startedAt,
        lastActivity: segment.lastActivity,
        netChars: segment.lastChars - segment.startChars
      };
    }
  };
}
