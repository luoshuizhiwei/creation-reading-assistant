import type { CreationDocument } from "@/types/creation";
import { plainTextToCreationDocument } from "@/features/creation/editor/paste-clean";
import type { SceneAiAction } from "@/types/ai";

/**
 * 场景 AI 动作的展示语义（Stage 4-D）：
 * 标签、排序、输出形态（候选 / 报告）与采纳语义（替换 / 追加）集中在一处，
 * 写作台按钮、发送确认、结果分发三处共用，避免「按钮叫续写、结果却整篇替换」。
 * 纯数据 + 纯函数，无 React、无 IPC。
 */

export const SCENE_AI_ACTION_LABELS: Record<SceneAiAction, string> = {
  polish: "润色场景",
  expand: "扩写场景",
  consistency: "一致性检查",
  continuation: "续写场景",
  condensing: "精简场景",
  "character-consistency": "角色一致性"
};

/** 按钮顺序：先改写类（产出可采纳候选），后检查类（产出只读报告）。 */
export const SCENE_AI_ACTION_ORDER: readonly SceneAiAction[] = [
  "polish",
  "expand",
  "continuation",
  "condensing",
  "consistency",
  "character-consistency"
];

/** 输出为只读报告、不提供「采纳为正文」入口的动作。 */
const REPORT_ACTIONS: ReadonlySet<string> = new Set(["consistency", "character-consistency"]);

/** 候选采纳语义为「追加到正文末尾」而非「替换整篇」的动作。 */
const APPEND_ACTIONS: ReadonlySet<string> = new Set(["continuation"]);

export type SceneAiOutputKind = "candidate" | "report";
export type SceneAiAdoptMode = "replace" | "append";

export function sceneAiActionLabel(action: string): string {
  return SCENE_AI_ACTION_LABELS[action as SceneAiAction] ?? action;
}

export function sceneAiOutputKind(action: string): SceneAiOutputKind {
  return REPORT_ACTIONS.has(action) ? "report" : "candidate";
}

export function sceneAiAdoptMode(action: string): SceneAiAdoptMode {
  return APPEND_ACTIONS.has(action) ? "append" : "replace";
}

/**
 * 追加模式下的**预览**文本：空正文不加多余空行，两者都非空时用一个空行分隔。
 * 只用于 diff 展示，不用于落库——落库走 appendTextToSceneBody。
 */
export function mergeSceneBody(currentBodyText: string, candidateText: string): string {
  const head = currentBodyText.trim();
  const tail = candidateText.trim();
  if (head === "") return tail;
  if (tail === "") return head;
  return `${head}\n\n${tail}`;
}

/**
 * 追加模式下的**落库**文档：在原文档末尾拼接候选段落，不重排既有正文。
 *
 * 为什么不直接把合并后的纯文本转回文档：正文→纯文本按单换行序列化，
 * 而纯文本→文档按空行分段，往返一次会把多个段落并成一段（真实数据损失）。
 * 在文档层追加则原块原样保留。
 */
export function appendTextToSceneBody(body: unknown, candidateText: string): CreationDocument {
  const existing: unknown[] = Array.isArray((body as { content?: unknown } | null)?.content)
    ? ((body as { content: unknown[] }).content ?? [])
    : [];
  if (candidateText.trim() === "") {
    return { type: "doc", content: existing.length > 0 ? existing : [{ type: "paragraph" }] };
  }
  const additions = plainTextToCreationDocument(candidateText);
  return { type: "doc", content: [...existing, ...additions.content] };
}
