import type { CardSummary } from "@/types/creation";

/** 背景聚合的卡片类型（地点/世界规则/组织/资料）。 */
export const BACKGROUND_KINDS = ["location", "worldRule", "organization", "reference"] as const;

export function isBackgroundCard(card: CardSummary): boolean {
  return (BACKGROUND_KINDS as readonly string[]).includes(card.kind);
}

/** 只保留当前项目的背景卡，再按搜索词过滤并解析选中卡。 */
export function resolveBackgroundSelection(
  cards: CardSummary[],
  projectId: string,
  search: string,
  selectedCardId: string | undefined
): CardSummary | undefined {
  const keyword = search.trim();
  const filtered = cards.filter(
    (card) =>
      card.projectId === projectId &&
      isBackgroundCard(card) &&
      (keyword
        ? card.title.includes(keyword) || card.aliases.some((alias) => alias.includes(keyword))
        : true)
  );
  if (!selectedCardId) return undefined;
  return filtered.find((card) => card.id === selectedCardId);
}

/** 删除前再次验证：卡片属于当前项目且类型属于背景集合。 */
export function canDeleteBackgroundCard(card: CardSummary, projectId: string): boolean {
  return card.projectId === projectId && isBackgroundCard(card);
}
