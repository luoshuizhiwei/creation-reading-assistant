import { countSignificantChars, estimateTokens } from "@/features/creation/ai/context-pack-format";
import type { Annotation, CardSummary, CardType, ScenePlanning } from "@/types/creation";

/**
 * AI 上下文包（调研 D-C2 全量版第一片）：
 * 把一次 AI 任务将发送的内容按「组」建模（正文/任务卡/关联卡片/批注），
 * 每组带字符数与 token 估算，支持按组排除后合成最终发送文本。
 * 纯函数、无 IPC、无 AI 调用——调用方（确认对话框 / 写作台 AI 入口）只消费预览与合成结果。
 */

export type AiContextGroupId = "body" | "planning" | "cards" | "annotations";

export interface AiContextGroup {
  id: AiContextGroupId;
  label: string;
  /** 该组将发送的文本（已合成，可直接拼接）。 */
  content: string;
  /** 非空白字符数（与写作台口径一致）。 */
  chars: number;
  /** token 估算：中文约 1.6 字符/token，仅作量级参考。 */
  estTokens: number;
}

export interface AiContextPack {
  groups: AiContextGroup[];
  totalChars: number;
  totalTokens: number;
  /** 按排除集合合成最终发送文本；全部排除时返回空串（调用方应阻止发送）。 */
  compose(excluded: ReadonlySet<string>): string;
}

export interface AiContextPackInput {
  sceneTitle: string;
  /** 场景正文纯文本（段落以换行连接）。 */
  sceneBodyText: string;
  planning?: ScenePlanning | null;
  /** 与场景相关的卡片（任务卡引用 + 批注关联）。 */
  cards: CardSummary[];
  cardTypes?: CardType[];
  annotations: Annotation[];
}

export { estimateTokens };
function planningText(planning: ScenePlanning, cardTitleOf: (cardId: string | null | undefined) => string | null): string[] {
  const lines: string[] = [];
  const perspective = cardTitleOf(planning.perspectiveCardId);
  if (perspective) lines.push(`视角：${perspective}`);
  if (planning.time) lines.push(`时间：${planning.time}`);
  const location = cardTitleOf(planning.locationCardId);
  if (location) lines.push(`地点：${location}`);
  const cast = (planning.castCardIds ?? [])
    .map((id) => cardTitleOf(id))
    .filter((title): title is string => title !== null);
  if (cast.length > 0) lines.push(`出场：${cast.join("、")}`);
  if (planning.goal) lines.push(`目标：${planning.goal}`);
  if (planning.conflict) lines.push(`冲突：${planning.conflict}`);
  if (planning.outcome) lines.push(`结果：${planning.outcome}`);
  if (planning.emotion) lines.push(`情绪：${planning.emotion}`);
  return lines;
}

function cardsText(cards: CardSummary[], typeNameOf: (kind: string) => string): string[] {
  return cards.map((card) => {
    const head = `【${typeNameOf(card.kind)}】${card.title}${card.aliases.length > 0 ? `（${card.aliases.join("、")}）` : ""}`;
    const notes = Object.entries(card.fields)
      .filter(([, value]) => typeof value === "string" && String(value).trim() !== "")
      .map(([key, value]) => `${key}: ${String(value).trim()}`);
    return notes.length > 0 ? `${head}\n${notes.join("\n")}` : head;
  });
}

function annotationsText(annotations: Annotation[], cardTitleOf: (cardId: string | null | undefined) => string | null): string[] {
  return annotations.map((annotation) => {
    const card = cardTitleOf(annotation.cardId);
    const statusLabel = annotation.status === "resolved" ? "已解决" : "待处理";
    return `${statusLabel}${card ? ` · 关联「${card}」` : ""}：${annotation.note}（锚点：${annotation.anchoredText || "（失效）"}）`;
  });
}

export function buildAiContextPack(input: AiContextPackInput): AiContextPack {
  const { sceneTitle, sceneBodyText, planning, cards, cardTypes = [], annotations } = input;
  const titleOf = (cardId: string | null | undefined): string | null =>
    cardId ? cards.find((card) => card.id === cardId)?.title ?? "已删除卡片" : null;
  const typeNameOf = (kind: string): string => cardTypes.find((type) => type.kind === kind)?.name ?? kind;

  const groups: AiContextGroup[] = [];
  const push = (id: AiContextGroupId, label: string, lines: string[]): void => {
    const content = lines.join("\n");
    if (content.trim() === "") return;
    const chars = countSignificantChars(content);
    groups.push({ id, label, content, chars, estTokens: estimateTokens(chars) });
  };

  const bodyLines = sceneBodyText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
  push("body", `正文 · ${sceneTitle}`, bodyLines);
  if (planning) push("planning", "任务卡", planningText(planning, titleOf));
  if (cards.length > 0) push("cards", "关联卡片", cardsText(cards, typeNameOf));
  if (annotations.length > 0) push("annotations", "批注", annotationsText(annotations, titleOf));

  return {
    groups,
    totalChars: groups.reduce((sum, group) => sum + group.chars, 0),
    totalTokens: groups.reduce((sum, group) => sum + group.estTokens, 0),
    compose: (excluded) =>
      groups
        .filter((group) => !excluded.has(group.id))
        .map((group) => `【${group.label}】\n${group.content}`)
        .join("\n\n")
  };
}
