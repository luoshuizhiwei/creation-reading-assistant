import type { InspirationVariantKind } from "./inspiration";

export type AIProvider = "openai-compatible";
/**
 * AI 运行动作：灵感侧沿用 InspirationVariantKind（收件箱候选版本）；
 * "consistency" 为场景一致性检查（输出问题报告，不产出候选，不落库）。
 */
export type AIRunAction = InspirationVariantKind | "consistency";
export function isSceneAiAction(action: AIRunAction): action is "polish" | "expand" | "consistency" {
  return action === "polish" || action === "expand" || action === "consistency";
}

export interface AISettings {
  provider: AIProvider;
  baseUrl: string;
  model: string;
  temperature: number;
  hasApiKey: boolean;
  /**
   * AI 打磨是否启用。默认 false：即使已配置 Key，未显式开启也不允许任何 AI 操作，
   * 也不会向用户配置的服务发送任何内容。
   */
  enabled: boolean;
}

export interface AISettingsPatch {
  provider?: AIProvider;
  baseUrl?: string;
  model?: string;
  temperature?: number;
  enabled?: boolean;
}

/**
 * AI 是否真正可用：必须显式启用且已配置 API Key。
 * 仅当两者都满足时，UI 才显示/允许 AI 操作，主进程 runAIAction 才会真正发起网络请求。
 */
export function isAIAvailable(settings: AISettings | undefined | null): boolean {
  return Boolean(settings) && settings!.enabled === true && settings!.hasApiKey === true;
}

export interface SaveAIApiKeyInput {
  apiKey: string;
}

export interface AIRunInput {
  action: AIRunAction;
  title?: string;
  content: string;
  platform?: string;
}

export interface AIRunResult {
  kind: AIRunAction;
  content: string;
  prompt: string;
  model: string;
}
