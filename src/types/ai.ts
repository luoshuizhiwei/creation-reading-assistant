import type { InspirationVariantKind } from "./inspiration";

export type AIProvider = "openai-compatible";
/**
 * AI 运行动作：灵感侧沿用 InspirationVariantKind（收件箱候选版本）；
 * 场景级动作（polish / expand / consistency / continuation / condensing /
 * character-consistency）输出候选或报告，绝不直接改正文。
 *
 * 注意 polish / expand 同时属于灵感动作与场景动作（历史原因：收件箱先占用了这两个词）。
 * 因此**不能只凭 action 判断走哪套提示词**，必须结合是否携带 sceneContext，见
 * resolveAiPromptKind。
 */
export type AIRunAction =
  | InspirationVariantKind
  | "consistency"
  | "continuation"
  | "condensing"
  | "character-consistency";

/** 只在场景侧存在的动作：不带 sceneContext 时一定是非法调用。 */
export type SceneOnlyAiAction = "consistency" | "continuation" | "condensing" | "character-consistency";

/** 场景级 AI 动作的联合：写作台 UI 入口引用此处。 */
export type SceneAiAction = "polish" | "expand" | SceneOnlyAiAction;

export function isSceneOnlyAiAction(action: AIRunAction): action is SceneOnlyAiAction {
  return (
    action === "consistency" ||
    action === "continuation" ||
    action === "condensing" ||
    action === "character-consistency"
  );
}

export function isSceneAiAction(action: AIRunAction): action is SceneAiAction {
  return action === "polish" || action === "expand" || isSceneOnlyAiAction(action);
}

/** 全部合法动作：任何不在其中的值都是调用方 bug 或被篡改的 IPC 入参。 */
export function isKnownAiAction(action: string): boolean {
  return (
    action === "polish" ||
    action === "expand" ||
    action === "platform-style" ||
    action === "conflict" ||
    action === "humanize" ||
    isSceneOnlyAiAction(action as AIRunAction)
  );
}

/**
 * 提示词分派：决定一次 AI 调用走场景提示词还是灵感提示词。
 * - 场景专属动作（consistency / continuation / condensing / character-consistency）：必然走场景。
 * - polish / expand：携带 sceneContext 时视为场景调用，否则视为收件箱灵感调用。
 * 这样既不破坏收件箱既有行为，也不要求调用方为一个词造出第二个 action 名。
 */
export function resolveAiPromptKind(input: { action: AIRunAction; sceneContext?: AIRunSceneContext }): "scene" | "inspiration" {
  if (isSceneOnlyAiAction(input.action)) return "scene";
  if (isSceneAiAction(input.action) && input.sceneContext && input.sceneContext.sceneTitle.trim() !== "") {
    return "scene";
  }
  return "inspiration";
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

/** 场景上下文：仅场景级动作使用，灵感动作忽略。 */
export interface AIRunSceneContext {
  sceneTitle: string;
  /** 任务卡（视角/时间/地点/出场/目标/冲突/结果/情绪）文本化结果。 */
  planningText?: string;
  /** 场景关联卡片摘要（主名 + 别名 + 关键字段），含角色、地点、视角卡。 */
  cardsText?: string;
  /** 场景关联批注的纯文本。 */
  annotationsText?: string;
}

export interface AIRunInput {
  action: AIRunAction;
  title?: string;
  content: string;
  platform?: string;
  /** 场景上下文：仅当 action 属于 isSceneAiAction 时必填；其他动作忽略。 */
  sceneContext?: AIRunSceneContext;
}

export interface AIRunResult {
  kind: AIRunAction;
  content: string;
  prompt: string;
  model: string;
}
