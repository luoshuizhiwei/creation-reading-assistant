/**
 * 替换计划 UI 视图类型与服务契约。
 * 核心数据形状复用 @/types/creation，消除类型双存与维护漂移。
 */
import type {
  ReplaceApplyOutcome,
  ReplaceHit,
  ReplacePlan,
  ReplacePlanMode,
  ReplacePlanQuery as CanonicalReplacePlanQuery,
  ReplacePlanSceneSummary,
  ReplacePlanScope,
  ReplaceTextRange
} from "@/types/creation";

export type {
  ReplaceHit,
  ReplacePlanMode,
  ReplacePlanSceneSummary,
  ReplaceTextRange
};

export type ReplaceScope = ReplacePlanScope;
export type ReplacePlanQuery = CanonicalReplacePlanQuery;
export type ReplacePlanView = Omit<ReplacePlan, "seals">;
export type ReplaceApplyResultView = ReplaceApplyOutcome;

export type ReplacePlanPhase = "scanning" | "planning";

export interface ReplacePlanProgress {
  phase: ReplacePlanPhase;
  completedScenes: number;
  totalScenes: number;
  completedHits: number;
  totalHits: number;
}

export type ReplaceErrorCode =
  | "invalid-input"
  | "not-found"
  | "stale"
  | "overlap"
  | "plan-used"
  | "plan-forbidden"
  | "plan-expired"
  | "cancelled"
  | "transaction-failed"
  | "seam-not-wired";

export interface ReplaceErrorView {
  code: ReplaceErrorCode;
  message: string;
}

export interface ReplaceCreateHandlers {
  onProgress(progress: ReplacePlanProgress): void;
  signal?: AbortSignal;
}

export interface ReplacePlanService {
  createPlan(query: ReplacePlanQuery, handlers: ReplaceCreateHandlers): Promise<ReplacePlanView>;
  applyPlan(planId: string, excludedHitIds: string[], handlers?: { signal?: AbortSignal }): Promise<ReplaceApplyResultView>;
  cancel(): void;
}

const ERROR_MESSAGES: Record<ReplaceErrorCode, string> = {
  "invalid-input": "输入无效：查找内容不能为空，或受限正则不被支持。",
  "not-found": "指定的范围不存在。",
  stale: "场景正文自预览后已变更，计划已失效，请重新预览。",
  overlap: "存在重叠的替换命中，已拒绝应用以避免重复替换。",
  "plan-used": "该替换计划已使用，不能重复应用。",
  "plan-forbidden": "替换计划不存在或已失效（planId 无效）。",
  "plan-expired": "替换计划已过期，请重新预览。",
  cancelled: "操作已取消，未留下任何修改。",
  "transaction-failed": "应用替换失败，已全部回滚，未留下任何修改。",
  "seam-not-wired": "替换计划接口尚未接入（待公共 seam 接线）。"
};

export function describeReplaceError(error: ReplaceErrorView | undefined): string {
  if (!error) return "未知错误。";
  return ERROR_MESSAGES[error.code] ?? error.message ?? "未知错误。";
}
