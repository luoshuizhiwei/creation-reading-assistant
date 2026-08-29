import type { Annotation, CardSummary, CardType, CreationProjectOutline, ScenePlanning } from "@/types/creation";

/** 引用卡片的雷达视图：带类型名；伏笔卡片带生命周期状态（D-C3 v1）。 */
export interface SceneRadarCardRef {
  title: string;
  /** 卡片类型显示名（如「角色」「伏笔线索」）；类型定义缺失时回退 kind。 */
  kindName: string;
  isForeshadow: boolean;
  /** 仅伏笔卡片有意义：true = 已回收；false/undefined = 未回收（未标记按未回收兜底）。 */
  foreshadowResolved?: boolean;
}

/** 创作雷达 v1（调研 D-C1）：只聚合已有数据（大纲任务卡/卡片/批注/正文字数），不新增表、不调 AI。 */
export interface SceneRadarData {
  sceneTitle: string | null;
  planning: ScenePlanning | null;
  /** 任务卡是否填过任一字段（决定空态引导）。 */
  hasPlanning: boolean;
  /** 视角/地点/出场解析后的卡片标题；引用已删除卡片时为「已删除卡片」。 */
  perspective: string | null;
  location: string | null;
  cast: string[];
  unresolvedAnnotations: number;
  resolvedAnnotations: number;
  invalidAnchors: number;
  /** 批注里关联的卡片（去重，保持首次出现顺序），带类型与伏笔状态。 */
  referencedCards: SceneRadarCardRef[];
  /** 当前场景批注引用的未回收伏笔数。 */
  foreshadowOpenInScene: number;
  /** 全书未回收伏笔数（kind=foreshadow 且未标记已回收）。 */
  foreshadowOpenInProject: number;
  targetWords: number | null;
  characterCount: number;
  /** 预计阅读分钟数（约 400 字/分钟）；无正文时为 null。 */
  estimatedMinutes: number | null;
  revision: number;
}

export interface SceneRadarInput {
  outline?: CreationProjectOutline;
  selectedSceneId?: string;
  cards: CardSummary[];
  /** 卡片类型定义（解析类型显示名与伏笔识别）。 */
  cardTypes?: CardType[];
  annotations: Annotation[];
  characterCount: number;
  revision: number;
}

function findOutlineScene(outline: CreationProjectOutline | undefined, sceneId?: string) {
  if (!outline || !sceneId) return null;
  // 防御不完整的 outline 形状（测试 mock / 迁移中间态）：缺数组视为空。
  const chapters = [
    ...(outline.volumes ?? []).flatMap((volume) => volume.chapters ?? []),
    ...(outline.looseChapters ?? [])
  ];
  for (const chapter of chapters) {
    const scene = (chapter.scenes ?? []).find((item) => item.id === sceneId);
    if (scene) return scene;
  }
  return null;
}

function planningFields(planning: ScenePlanning): boolean {
  return Boolean(
    planning.perspectiveCardId ||
    planning.time ||
    planning.locationCardId ||
    (planning.castCardIds && planning.castCardIds.length > 0) ||
    planning.goal ||
    planning.conflict ||
    planning.outcome ||
    planning.emotion ||
    planning.targetWords != null
  );
}

/** 卡片标题解析：删除后的卡片不静默消失，标注「已删除卡片」。 */
export function resolveCardTitle(cards: CardSummary[], cardId: string | null | undefined): string | null {
  if (!cardId) return null;
  const card = cards.find((item) => item.id === cardId);
  return card ? card.title : "已删除卡片";
}

/** 伏笔生命周期（D-C3 v1）：status 字段严格「已回收」才算回收；未标记/缺失/自定义值一律按未回收兜底。 */
export function isForeshadowResolved(card: CardSummary): boolean {
  return card.fields?.status === "已回收";
}

export function deriveSceneRadar(input: SceneRadarInput): SceneRadarData {
  const { outline, selectedSceneId, cards, cardTypes = [], annotations, characterCount, revision } = input;
  const scene = findOutlineScene(outline, selectedSceneId);
  const planning = scene?.planning ?? null;

  const cast = (planning?.castCardIds ?? [])
    .map((id) => resolveCardTitle(cards, id))
    .filter((title): title is string => title !== null);

  const kindNameOf = (kind: string): string => cardTypes.find((type) => type.kind === kind)?.name ?? kind;
  const referencedCards: SceneRadarCardRef[] = [];
  for (const annotation of annotations) {
    if (!annotation.cardId) continue;
    const card = cards.find((item) => item.id === annotation.cardId);
    const title = card ? card.title : "已删除卡片";
    if (referencedCards.some((item) => item.title === title && item.kindName === (card ? kindNameOf(card.kind) : ""))) continue;
    referencedCards.push(
      card
        ? {
            title,
            kindName: kindNameOf(card.kind),
            isForeshadow: card.kind === "foreshadow",
            foreshadowResolved: card.kind === "foreshadow" ? isForeshadowResolved(card) : undefined
          }
        : { title, kindName: "", isForeshadow: false }
    );
  }

  const projectForeshadows = cards.filter((card) => card.kind === "foreshadow");

  return {
    sceneTitle: scene?.title ?? null,
    planning,
    hasPlanning: planning ? planningFields(planning) : false,
    perspective: resolveCardTitle(cards, planning?.perspectiveCardId),
    location: resolveCardTitle(cards, planning?.locationCardId),
    cast,
    unresolvedAnnotations: annotations.filter((item) => item.status !== "resolved").length,
    resolvedAnnotations: annotations.filter((item) => item.status === "resolved").length,
    invalidAnchors: annotations.filter((item) => item.anchorInvalid).length,
    referencedCards,
    foreshadowOpenInScene: referencedCards.filter((item) => item.isForeshadow && !item.foreshadowResolved).length,
    foreshadowOpenInProject: projectForeshadows.filter((card) => !isForeshadowResolved(card)).length,
    targetWords: planning?.targetWords ?? null,
    characterCount,
    estimatedMinutes: characterCount > 0 ? Math.max(1, Math.round(characterCount / 400)) : null,
    revision
  };
}
