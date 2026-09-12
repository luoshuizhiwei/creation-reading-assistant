import type { AIRunInput, AIRunSceneContext } from "../../src/types/ai";
import { isKnownAiAction, resolveAiPromptKind } from "../../src/types/ai";

/**
 * 提示词构造（Stage 4-D）：
 * - 灵感侧沿用收件箱既有提示词，不读取任何场景上下文；
 * - 场景侧（润色/扩写/一致性/续写/精简/角色一致性）消费 sceneContext + 正文，
 *   绝不读取项目边界外的字段。
 * 与 IPC、网络、Key 完全解耦，便于纯函数测试。
 */

/** 灵感侧提示词：用于 polish / humanize / platform-style / conflict / expand（灵感用法）。 */
export function buildInspirationPrompt(input: AIRunInput): string {
  const title = input.title?.trim() ? `标题：${input.title.trim()}\n` : "";
  const platform = input.platform?.trim() ? `目标平台/风格：${input.platform.trim()}\n` : "";
  const content = requireString(input.content, "灵感内容");
  const instruction =
    input.action === "platform-style"
      ? "按目标平台读者口味重写这条灵感，让它更像可直接拿去写正文前的桥段设计。"
      : input.action === "conflict"
        ? "基于这条灵感生成 5 个可写冲突点，每个包含触发条件、升级方式和可用爽点。"
        : input.action === "humanize"
          ? "去掉机械总结感和 AI 腔，把这条灵感润成更自然、更像作者自己随手写下但清楚可用的素材。"
          : input.action === "expand"
            ? "把这条灵感扩展为可执行的写作方案：拆镜头、写冲突点、列出关键对白与节奏建议；不要直接写正文。"
            : "润色这条小说灵感，让它更清晰、更有画面感，同时不要替作者写成长篇正文。";
  return `${instruction}\n\n${platform}${title}原始灵感：\n${content}`;
}

/** 场景级动作提示词：消费 sceneContext 与场景正文。 */
export function buildScenePrompt(input: AIRunInput): string {
  const scene = input.sceneContext;
  if (!scene || scene.sceneTitle.trim() === "") {
    throw new Error("场景 AI 动作必须提供非空 sceneContext.sceneTitle。");
  }
  const blocks: string[] = [
    scene.planningText?.trim() ? `【任务卡】\n${scene.planningText.trim()}` : "",
    scene.cardsText?.trim() ? `【关联卡片】\n${scene.cardsText.trim()}` : "",
    scene.annotationsText?.trim() ? `【批注】\n${scene.annotationsText.trim()}` : ""
  ].filter((block) => block !== "");
  const ctxBlock = blocks.length > 0 ? `【上下文】\n${blocks.join("\n\n")}\n\n` : "";
  const body = requireString(input.content, "场景正文");
  const instruction = sceneInstruction(input.action);
  return `${instruction}\n\n${ctxBlock}【场景标题】${scene.sceneTitle.trim()}\n【场景正文】\n${body}`;
}

/** 场景动作指令文本；非法动作直接抛错，由 runAIAction 上层拦截。 */
export function sceneInstruction(action: AIRunInput["action"]): string {
  switch (action) {
    case "polish":
      return "润色下方场景正文：保留作者风格与情节，只改不通顺、可读性差之处；不增删情节、不替换专有名词、不改称谓；输出纯正文，不要解释、不要 Markdown 标题。";
    case "expand":
      return "把下方场景扩展为可执行的写作方案：拆镜头、写冲突点、列出关键对白与节奏建议；不要直接写正文、不要给出完整段落。";
    case "consistency":
      return "对下方场景做一致性检查：只输出问题报告，不要改写正文。每条问题按「【类型】严重度(高/中/低) 位置/证据 → 建议」一行列出，类型限：事实矛盾、时间线冲突、人物设定冲突、称谓/地名不一致、伏笔未回收、逻辑漏洞；没有问题的方面不要罗列，结尾给一行「总体结论」。";
    case "continuation":
      return "在保持原文人物动机、语气与称谓的前提下自然续写下方场景正文；输出约 300–600 字新内容；只输出续写部分，不要解释、不要 Markdown 标题、不要重复原文。";
    case "condensing":
      return "在保留关键情节、人物动机与必要伏笔的前提下精简下方场景正文；输出字数不超过原文 70%；不删关键事件、不替换专有名词、不改称谓；只输出精简后的正文。";
    case "character-consistency":
      return "对下方场景做角色人格/动机一致性检查：只输出报告，不改正文。按角色分组输出【人物名 | 状态(一致/偏离/冲突) | 证据 | 建议】；无问题者给出「一致」；结尾给一行【总体结论】。";
    default:
      throw new Error(`不支持的 AI 动作：${String(action)}`);
  }
}

/**
 * 主进程统一入口：按 action + 是否携带 sceneContext 分派提示词构造器。
 * polish / expand 无 sceneContext 时走灵感提示词（收件箱沿用行为），
 * 有 sceneContext 时走场景提示词；场景专属动作缺 sceneContext 直接抛错。
 */
export function buildAIPrompt(input: AIRunInput): string {
  // 非法 action 一律拒绝：宁可报错也不把它静默当成「润色」发给外部服务。
  if (!isKnownAiAction(String(input.action))) {
    throw new Error(`不支持的 AI 动作：${String(input.action)}`);
  }
  const kind = resolveAiPromptKind(input);
  if (kind === "scene") {
    if (!input.sceneContext || input.sceneContext.sceneTitle.trim() === "") {
      throw new Error(`场景 AI 动作「${input.action}」必须提供非空 sceneContext.sceneTitle。`);
    }
    return buildScenePrompt(input);
  }
  return buildInspirationPrompt(input);
}

/** 供调用方/测试复用：把可能为 undefined 的上下文片段收敛成可选字符串。 */
export function optionalText(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

/** 供调用方复用：判断一次输入是否需要场景上下文。 */
export function requiresSceneContext(input: AIRunInput): input is AIRunInput & { sceneContext: AIRunSceneContext } {
  return resolveAiPromptKind(input) === "scene";
}

function requireString(value: unknown, label: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${label}不能为空。`);
  }
  return value;
}
