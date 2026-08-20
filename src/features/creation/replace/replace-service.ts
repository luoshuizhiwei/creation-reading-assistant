/**
 * 替换计划服务：调用已接入公共 seam 的 IPC 通道
 * （window.api.creation.replacePlanCreate / replacePlanApply）。
 * 进度上报与运行时取消为后续迭代（通过操作控制器接线）的能力，当前 MVP 仅做基础接线。
 */
import type { DesktopApi } from "@/types/api";
import type {
  ReplaceApplyResultView,
  ReplaceErrorView,
  ReplacePlanQuery,
  ReplacePlanService,
  ReplacePlanView
} from "./types";

export class ReplaceServiceError extends Error {
  readonly code: ReplaceErrorView["code"];

  constructor(code: ReplaceErrorView["code"], message: string) {
    super(message);
    this.name = "ReplaceServiceError";
    this.code = code;
  }
}

export function createReplacePlanService(api: DesktopApi = window.api): ReplacePlanService {
  const creation = api.creation;
  return {
    async createPlan(query: ReplacePlanQuery): Promise<ReplacePlanView> {
      return creation.replacePlanCreate(query);
    },
    async applyPlan(planId: string, excludedHitIds: string[]): Promise<ReplaceApplyResultView> {
      return creation.replacePlanApply({ planId, excludedHitIds });
    },
    cancel(): void {
      // 替换计划的运行时取消（通过操作控制器）为后续迭代能力；当前 IPC 不暴露取消。
    }
  };
}
