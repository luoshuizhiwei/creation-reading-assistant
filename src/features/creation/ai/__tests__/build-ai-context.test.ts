import { describe, expect, it } from "vitest";
import { buildAiContextPack, estimateTokens } from "@/features/creation/ai/build-ai-context";
import type { Annotation, CardSummary, CardType } from "@/types/creation";

function card(id: string, title: string, kind = "character", fields: Record<string, unknown> = {}): CardSummary {
  return {
    id,
    projectId: "p1",
    kind,
    title,
    aliases: kind === "character" ? ["别名-" + title] : [],
    fields,
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

const cardTypes: CardType[] = [
  { id: "t1", projectId: null, builtIn: true, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 },
  { id: "t2", projectId: null, builtIn: true, kind: "foreshadow", name: "伏笔线索", fields: [], sortOrder: 6, createdAt: "", updatedAt: "", revision: 1 }
];

function annotation(partial: Partial<Annotation> & { id: string; note: string }): Annotation {
  return {
    projectId: "p1",
    sceneId: "s1",
    cardId: null,
    anchor: { blockIndex: 0, textOffset: 0, textLength: 4 },
    anchorInvalid: false,
    status: "open",
    revision: 1,
    createdAt: "",
    updatedAt: "",
    ...partial
  } as Annotation;
}

describe("estimateTokens", () => {
  it("CJK 约 1.6 字符/token，向上取整", () => {
    expect(estimateTokens(0)).toBe(0);
    expect(estimateTokens(1)).toBe(1);
    expect(estimateTokens(16)).toBe(10);
  });
});

describe("buildAiContextPack", () => {
  const base = {
    sceneTitle: "雨夜",
    sceneBodyText: "闭馆铃响过第三遍。\n她抬眼望向第七排。",
    cards: [
      card("k1", "林晚", "character", { note: "夜班管理员" }),
      card("k2", "无名的借书卡", "foreshadow", { status: "未回收" })
    ],
    cardTypes,
    annotations: [annotation({ id: "a1", note: "伏笔埋设", cardId: "k2" })]
  };

  it("按组构建：正文/任务卡/卡片/批注，空组省略", () => {
    const pack = buildAiContextPack({
      ...base,
      planning: {
        perspectiveCardId: "k1",
        time: "闭馆前的雨夜",
        goal: "查明借书卡的主人",
        castCardIds: ["k1", "k2"]
      }
    });
    const ids = pack.groups.map((group) => group.id);
    expect(ids).toEqual(["body", "planning", "cards", "annotations"]);
    const body = pack.groups.find((group) => group.id === "body")!;
    expect(body.content).toBe("闭馆铃响过第三遍。\n她抬眼望向第七排。");
    const planning = pack.groups.find((group) => group.id === "planning")!;
    expect(planning.content).toContain("视角：林晚");
    expect(planning.content).toContain("时间：闭馆前的雨夜");
    expect(planning.content).toContain("出场：林晚、无名的借书卡");
    const cardsGroup = pack.groups.find((group) => group.id === "cards")!;
    expect(cardsGroup.content).toContain("【角色】林晚（别名-林晚）");
    expect(cardsGroup.content).toContain("【伏笔线索】无名的借书卡");
    expect(cardsGroup.content).toContain("status: 未回收");
    const annotationsGroup = pack.groups.find((group) => group.id === "annotations")!;
    expect(annotationsGroup.content).toContain("待处理 · 关联「无名的借书卡」：伏笔埋设");
    expect(pack.totalChars).toBe(pack.groups.reduce((sum, group) => sum + group.chars, 0));
    expect(pack.totalTokens).toBe(pack.groups.reduce((sum, group) => sum + group.estTokens, 0));
  });

  it("无任务卡/卡片/批注时只剩正文组", () => {
    const pack = buildAiContextPack({ ...base, planning: null, cards: [], cardTypes: [], annotations: [] });
    expect(pack.groups.map((group) => group.id)).toEqual(["body"]);
  });

  it("compose 排除指定组；全部排除返回空串", () => {
    const pack = buildAiContextPack({
      ...base,
      planning: { goal: "查明" }
    });
    const withCards = pack.compose(new Set(["cards", "annotations"]));
    expect(withCards).toContain("【正文 · 雨夜】");
    expect(withCards).toContain("【任务卡】");
    expect(withCards).not.toContain("【关联卡片】");
    expect(withCards).not.toContain("【批注】");
    expect(pack.compose(new Set(["body", "planning", "cards", "annotations"]))).toBe("");
  });

  it("引用已删除卡片标注「已删除卡片」；未知类型回退 kind", () => {
    const pack = buildAiContextPack({
      ...base,
      planning: { perspectiveCardId: "gone", castCardIds: ["gone"] },
      cards: [card("k9", "道具", "item")],
      cardTypes: []
    });
    const planning = pack.groups.find((group) => group.id === "planning")!;
    expect(planning.content).toContain("视角：已删除卡片");
    const cardsGroup = pack.groups.find((group) => group.id === "cards")!;
    expect(cardsGroup.content).toContain("【item】道具");
  });

  it("速查中显式打开的卡片始终形成独立可排除组，并从普通关联组去重", () => {
    const pack = buildAiContextPack({
      ...base,
      quickReferenceCards: [base.cards[0], card("quick", "北关", "location")]
    });
    const quick = pack.groups.find((group) => group.id === "quick-reference")!;
    expect(quick.label).toBe("写作速查卡片");
    expect(quick.content).toContain("北关");
    expect(quick.content).toContain("林晚");
    expect(pack.groups.find((group) => group.id === "cards")!.content).not.toContain("林晚");
    expect(pack.compose(new Set(["quick-reference"]))).not.toContain("北关");
  });
});
