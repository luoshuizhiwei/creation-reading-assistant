/**
 * 替换计划 UI 的本地视图类型与服务接口（独立副本，不依赖主进程模块）。
 * 主进程 replace-plan.ts 的字段形状与此保持一致；公共 seam 接线后由 preload 返回同样结构。
 */

export type ReplaceScope = "all" | "chapter" | "scene";
export type ReplacePlanMode = "plain" | "regex";

export interface ReplaceTextRange {
  start: number;
  end: number;
}

export interface ReplaceHit {
  hitId: string;
  sceneId: string;
  blockIndex: number;
  range: ReplaceTextRange;
  before: string;
  after: string;
  context: string;
}

export interface ReplacePlanSceneSummary {
  sceneId: string;
  chapterId: string;
  chapterTitle: string;
  title: string;
  hitCount: number;
}

export interface ReplacePlanView {
  planId: string;
  projectId: string;
  scope: ReplaceScope;
  scopeId?: string;
  find: string;
  replaceWith: string;
  mode: ReplacePlanMode;
  scenes: ReplacePlanSceneSummary[];
  hits: ReplaceHit[];
  totalHits: number;
  limit: number;
  truncated: boolean;
  sealedAt: string;
  expiresAt: string;
}

export type ReplacePlanPhase = "scanning" | "planning";

export interface ReplacePlanProgress {
  phase: ReplacePlanPhase;
  completedScenes: number;
  totalScenes: number;
  completedHits: number;
  totalHits: number;
}

export interface ReplaceApplyResultView {
  planId: string;
  sequence: number;
  appliedHitCount: number;
  modifiedSceneIds: string[];
  snapshotIds: string[];
  committedAt: string;
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

export interface ReplacePlanQuery {
  projectId: string;
  scope: ReplaceScope;
  scopeId?: string;
  find: string;
  replaceWith: string;
  mode: ReplacePlanMode;
  limit?: number;
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
