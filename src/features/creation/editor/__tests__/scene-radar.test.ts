import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { deriveSceneRadar, isForeshadowResolved, resolveCardTitle } from "@/features/creation/editor/scene-radar";
import type { Annotation, CardSummary, CardType, CreationProjectOutline } from "@/types/creation";

function card(id: string, title: string, extra?: Partial<CardSummary>): CardSummary {
  return {
    id,
    projectId: "p1",
    kind: "character",
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-08-29T00:00:00Z",
    updatedAt: "2026-08-29T00:00:00Z",
    revision: 1,
    ...extra
  };
}

const cardTypes: CardType[] = [
  { id: "t-character", projectId: null, builtIn: true, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 },
  { id: "t-foreshadow", projectId: null, builtIn: true, kind: "foreshadow", name: "伏笔线索", fields: [], sortOrder: 6, createdAt: "", updatedAt: "", revision: 1 }
];

function annotation(partial: Partial<Annotation> & { id: string }): Annotation {
  return {
    projectId: "p1",
    sceneId: "s1",
    cardId: null,
    anchor: { blockIndex: 0, textOffset: 0, textLength: 4 },
    anchorInvalid: false,
    note: "n",
    status: "open",
    revision: 1,
    createdAt: "2026-08-29T00:00:00Z",
    updatedAt: "2026-08-29T00:00:00Z",
    ...partial
  } as Annotation;
}

function outlineWithScene(planning?: Record<string, unknown>): CreationProjectOutline {
  return {
    project: { id: "p1" } as CreationProjectOutline["project"],
    volumes: [
      {
        id: "v1",
        projectId: "p1",
        title: "第一卷",
        sortOrder: 0,
        createdAt: "",
        updatedAt: "",
        revision: 1,
        chapters: [
          {
            id: "c1",
            volumeId: "v1",
            title: "第一章",
            sortOrder: 0,
            status: "draft",
            numbering: "auto",
            customNumber: null,
            displayNumber: "第1章",
            createdAt: "",
            updatedAt: "",
            revision: 1,
            scenes: [
              {
                id: "s1",
                chapterId: "c1",
                title: "开场",
                sortOrder: 0,
                wordCount: 0,
                planning: planning as never,
                createdAt: "",
                updatedAt: "",
                revision: 3
              }
            ]
          }
        ]
      }
    ],
    looseChapters: []
  };
}

const base = {
  cards: [card("k1", "林晚"), card("k2", "旧图书馆")],
  annotations: [],
  characterCount: 800,
  revision: 5
};

describe("deriveSceneRadar", () => {
  it("无大纲/未选场景时返回空聚合，不抛错", () => {
    const radar = deriveSceneRadar({ ...base, selectedSceneId: "s1" });
    expect(radar.hasPlanning).toBe(false);
    expect(radar.sceneTitle).toBeNull();
    expect(radar.estimatedMinutes).toBe(2);
    expect(radar.revision).toBe(5);
  });

  it("解析任务卡字段：视角/地点/出场解析为卡片标题", () => {
    const radar = deriveSceneRadar({
      ...base,
      outline: outlineWithScene({
        perspectiveCardId: "k1",
        locationCardId: "k2",
        castCardIds: ["k1", "k2"],
        time: "入夜后",
        goal: "拿到钥匙",
        conflict: "守夜人在巡逻",
        outcome: null,
        emotion: "紧张",
        targetWords: 2000
      }),
      selectedSceneId: "s1"
    });
    expect(radar.hasPlanning).toBe(true);
    expect(radar.perspective).toBe("林晚");
    expect(radar.location).toBe("旧图书馆");
    expect(radar.cast).toEqual(["林晚", "旧图书馆"]);
    expect(radar.targetWords).toBe(2000);
  });

  it("引用已删除卡片标注「已删除卡片」，不静默消失", () => {
    const radar = deriveSceneRadar({
      ...base,
      outline: outlineWithScene({ perspectiveCardId: "gone", castCardIds: ["gone"] }),
      selectedSceneId: "s1"
    });
    expect(radar.perspective).toBe("已删除卡片");
    expect(radar.cast).toEqual(["已删除卡片"]);
  });

  it("批注概览：待处理/已解决/失效锚点计数与引用卡片去重", () => {
    const radar = deriveSceneRadar({
      ...base,
      cardTypes,
      outline: outlineWithScene(),
      selectedSceneId: "s1",
      annotations: [
        annotation({ id: "a1", cardId: "k1", status: "open" }),
        annotation({ id: "a2", cardId: "k1", status: "resolved" }),
        annotation({ id: "a3", cardId: "k2", status: "open", anchorInvalid: true }),
        annotation({ id: "a4", cardId: null, status: "open" })
      ]
    });
    expect(radar.unresolvedAnnotations).toBe(3);
    expect(radar.resolvedAnnotations).toBe(1);
    expect(radar.invalidAnchors).toBe(1);
    expect(radar.referencedCards.map((ref) => ref.title)).toEqual(["林晚", "旧图书馆"]);
    expect(radar.referencedCards[0].kindName).toBe("角色");
    expect(radar.referencedCards[0].isForeshadow).toBe(false);
  });

  it("伏笔生命周期：status 字段严格判定，未标记按未回收兜底（D-C3 v1）", () => {
    expect(isForeshadowResolved(card("f1", "古剑来历", { kind: "foreshadow", fields: { status: "已回收" } }))).toBe(true);
    expect(isForeshadowResolved(card("f2", "神秘信件", { kind: "foreshadow", fields: {} }))).toBe(false);
    expect(isForeshadowResolved(card("f3", "陌生脚印", { kind: "foreshadow", fields: { status: "未回收" } }))).toBe(false);

    const radar = deriveSceneRadar({
      ...base,
      cardTypes,
      outline: outlineWithScene(),
      selectedSceneId: "s1",
      cards: [
        ...base.cards,
        card("f1", "古剑来历", { kind: "foreshadow", fields: { status: "已回收" } }),
        card("f2", "神秘信件", { kind: "foreshadow", fields: {} }),
        card("f3", "陌生脚印", { kind: "foreshadow", fields: { status: "未回收" } })
      ],
      annotations: [
        annotation({ id: "a1", cardId: "f2", status: "open" }),
        annotation({ id: "a2", cardId: "f1", status: "open" })
      ]
    });
    expect(radar.foreshadowOpenInScene).toBe(1);
    expect(radar.foreshadowOpenInProject).toBe(2);
    const open = radar.referencedCards.find((ref) => ref.title === "神秘信件");
    const resolved = radar.referencedCards.find((ref) => ref.title === "古剑来历");
    expect(open?.isForeshadow).toBe(true);
    expect(open?.foreshadowResolved).toBe(false);
    expect(resolved?.foreshadowResolved).toBe(true);
    expect(open?.kindName).toBe("伏笔线索");
  });

  it("目标字数进度与预计阅读时长边界", () => {
    const radar = deriveSceneRadar({
      ...base,
      outline: outlineWithScene({ targetWords: 2000 }),
      selectedSceneId: "s1",
      characterCount: 500
    });
    expect(radar.characterCount).toBe(500);
    expect(radar.targetWords).toBe(2000);

    const empty = deriveSceneRadar({ ...base, characterCount: 0 });
    expect(empty.estimatedMinutes).toBeNull();
  });
});

describe("resolveCardTitle", () => {
  it("空 ID 返回 null，命中返回标题，未命中返回已删除卡片", () => {
    const cards = [card("k1", "林晚")];
    expect(resolveCardTitle(cards, null)).toBeNull();
    expect(resolveCardTitle(cards, "k1")).toBe("林晚");
    expect(resolveCardTitle(cards, "nope")).toBe("已删除卡片");
  });
});

// BD-117：场景雷达「未设置」值与「已回收伏笔」标签原来各挂一条 fractional opacity，把走
// --text-meta（三级文字）的正文压到晨校 <3.0 底线。这类「靠调暗正文表达状态」是本仓第四次撞的
// 雷（BD-114 收件箱 used 行 / ai-send-confirm 排除组）。运行时普查的 opacity 折叠只能抓到
// 「扫的那一刻该状态恰好在 DOM 里」的情形；这两个态平时不渲染，故用源码级断言把这条债钉死。
describe("scene-radar.css 不许用 opacity 压暗三级文字", () => {
  // 去掉块注释再判：注释里会原样引用被禁的 `opacity` 字样，不去掉会自己咬自己。
  const css = readFileSync(fileURLToPath(new URL("../scene-radar.css", import.meta.url)), "utf8").replace(/\/\*[\s\S]*?\*\//g, "");

  it("dd.unset 规则块不含 opacity", () => {
    const block = /\.scene-radar-item dd\.unset \{([^}]*)\}/.exec(css);
    expect(block, "找不到 .scene-radar-item dd.unset 规则").toBeTruthy();
    expect(block![1]).not.toMatch(/opacity/);
  });

  it("foreshadow-resolved 裸标签规则块不含 opacity", () => {
    // 只针对裸 `.scene-radar-tag.foreshadow-resolved {`（不带 ` b` 子选择器）
    const bare = /\.scene-radar-tag\.foreshadow-resolved \{([^}]*)\}/.exec(css);
    if (bare) expect(bare[1]).not.toMatch(/opacity/);
    // 允许该裸规则整条删除：无论留删，都不得再出现压暗正文的 opacity。
  });
});
