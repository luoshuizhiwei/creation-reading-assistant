/**
 * 替换计划服务：通过 creation-service 调用 IPC（replacePlanCreate / replacePlanApply）。
 * 保持 ReplacePlanService 接口作为 ReplacePanel 的依赖注入契约，
 * 测试可注入 mock，生产自动使用 creation-service 实现。
 */
import { replacePlanCreate, replacePlanApply } from "@/services/creation-service";
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

export function createReplacePlanService(): ReplacePlanService {
  return {
    async createPlan(query: ReplacePlanQuery): Promise<ReplacePlanView> {
      return replacePlanCreate(query);
    },
    async applyPlan(planId: string, excludedHitIds: string[]): Promise<ReplaceApplyResultView> {
      return replacePlanApply({ planId, excludedHitIds });
    },
    cancel(): void {
      // 替换计划的运行时取消（通过操作控制器）为后续迭代能力；当前 IPC 不暴露取消。
    }
  };
}