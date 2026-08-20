/**
 * 会话修正策略（纯 module，可独立测试）。
 *
 * 会话修正（编辑 startedAt / activeSeconds / netChars）在提交到主进程前，
 * 由本模块完成 renderer 侧一致性校验，规则与主进程写入侧保持一致：
 * - startedAt 必须是可解析的 ISO 时间，且不得是明显未来的时间；
 * - activeSeconds 必须是非负整数，且不超过单段上限（86400 秒 = 24 小时）；
 * - netChars 必须是有限数，且落在合理区间内；
 * - projectId / sessionId 必须非空（项目归属由主进程事务校验，这里先做形状校验）。
 *
 * 校验失败返回按字段分类的问题清单，UI 据此逐项提示；校验通过返回
 * 规范化后的修正值（含整型化 activeSeconds / netChars）。
 */

export const SESSION_DURATION_MAX_SECONDS = 86_400;
export const SESSION_NET_CHARS_LIMIT = 1_000_000;

export interface SessionCorrectionDraft {
  projectId: string;
  sessionId: string;
  sceneId?: string;
  startedAt: string;
  activeSeconds: number;
  netChars: number;
}

export type SessionCorrectionIssue =
  | "missing-project"
  | "missing-session"
  | "invalid-start"
  | "future-start"
  | "duration-type"
  | "duration-range"
  | "net-chars-type"
  | "net-chars-range";

export type SessionCorrectionValidation =
  | { ok: true; value: { startedAt: string; activeSeconds: number; netChars: number; sceneId?: string } }
  | { ok: false; issues: SessionCorrectionIssue[] };

export function validateSessionCorrection(draft: SessionCorrectionDraft, nowMs = Date.now()): SessionCorrectionValidation {
  const issues: SessionCorrectionIssue[] = [];

  if (!draft.projectId || !draft.projectId.trim()) issues.push("missing-project");
  if (!draft.sessionId || !draft.sessionId.trim()) issues.push("missing-session");

  const parsed = Date.parse(draft.startedAt);
  if (Number.isNaN(parsed)) {
    issues.push("invalid-start");
  } else if (parsed > nowMs + 5 * 60 * 1000) {
    issues.push("future-start");
  }

  if (!Number.isFinite(draft.activeSeconds)) {
    issues.push("duration-type");
  } else if (!Number.isInteger(draft.activeSeconds) || draft.activeSeconds < 0 || draft.activeSeconds > SESSION_DURATION_MAX_SECONDS) {
    issues.push("duration-range");
  }

  if (!Number.isFinite(draft.netChars)) {
    issues.push("net-chars-type");
  } else if (draft.netChars < -SESSION_NET_CHARS_LIMIT || draft.netChars > SESSION_NET_CHARS_LIMIT) {
    issues.push("net-chars-range");
  }

  if (issues.length > 0) return { ok: false, issues };

  const sceneId = draft.sceneId && draft.sceneId.trim() ? draft.sceneId.trim() : undefined;
  return {
    ok: true,
    value: {
      startedAt: new Date(parsed).toISOString(),
      activeSeconds: Math.round(draft.activeSeconds),
      netChars: Math.round(draft.netChars),
      ...(sceneId ? { sceneId } : {})
    }
  };
}

export const SESSION_CORRECTION_ISSUE_LABELS: Record<SessionCorrectionIssue, string> = {
  "missing-project": "缺少项目归属。",
  "missing-session": "缺少会话标识。",
  "invalid-start": "开始时间无法解析。",
  "future-start": "开始时间不能明显晚于当前时间。",
  "duration-type": "活动时长必须是数字。",
  "duration-range": `活动时长必须在 0 至 ${SESSION_DURATION_MAX_SECONDS} 秒之间。`,
  "net-chars-type": "净增字符数必须是数字。",
  "net-chars-range": `净增字符数必须在 ±${SESSION_NET_CHARS_LIMIT.toLocaleString("zh-CN")} 之间。`
};
