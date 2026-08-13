import { describe, expect, it } from "vitest";
import {
  canDeleteBackgroundCard,
  isBackgroundCard,
  resolveBackgroundSelection
} from "@/features/creation/background/background-guard";
import type { CardSummary } from "@/types/creation";

function makeCard(overrides: Partial<CardSummary> & { id: string }): CardSummary {
  return {
    projectId: "p1",
    kind: "location",
    title: "黄沙镇",
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-08-09T10:00:00.000Z",
    updatedAt: "2026-08-09T10:00:00.000Z",
    revision: 1,
    ...overrides
  };
}

describe("背景页选择/删除防护", () => {
  it("角色卡不属于背景集合", () => {
    expect(isBackgroundCard(makeCard({ id: "char-1", kind: "character", title: "苏青" }))).toBe(false);
    expect(isBackgroundCard(makeCard({ id: "loc-1", kind: "location" }))).toBe(true);
  });

  it("先选择角色卡再进入背景页：详情解析不到角色卡，角色卡不显示", () => {
    const cards = [
      makeCard({ id: "char-1", kind: "character", title: "苏青" }),
      makeCard({ id: "loc-1", kind: "location", title: "黄沙镇" })
    ];
    const selected = resolveBackgroundSelection(cards, "p1", "", "char-1");
    expect(selected).toBeUndefined();
  });

  it("角色卡不可在背景页删除；同项目背景卡可删除", () => {
    const character = makeCard({ id: "char-1", kind: "character", title: "苏青" });
    const location = makeCard({ id: "loc-1", kind: "location", title: "黄沙镇" });
    expect(canDeleteBackgroundCard(character, "p1")).toBe(false);
    expect(canDeleteBackgroundCard(location, "p1")).toBe(true);
  });

  it("其他项目的背景卡不可删除（先校验项目归属）", () => {
    const foreign = makeCard({ id: "loc-2", projectId: "p2", kind: "location", title: "别处" });
    expect(canDeleteBackgroundCard(foreign, "p1")).toBe(false);
  });

  it("搜索筛选后，选中卡被过滤掉时详情解析不到", () => {
    const cards = [
      makeCard({ id: "loc-1", kind: "location", title: "黄沙镇" }),
      makeCard({ id: "rule-1", kind: "worldRule", title: "灵气复苏" })
    ];
    expect(resolveBackgroundSelection(cards, "p1", "黄沙", "rule-1")?.id).toBeUndefined();
    expect(resolveBackgroundSelection(cards, "p1", "黄沙", "loc-1")?.id).toBe("loc-1");
  });

  it("跨项目卡片隔离：p2 的背景卡不会在 p1 页面被选中或渲染", () => {
    const cards = [
      makeCard({ id: "loc-1", projectId: "p1", kind: "location", title: "黄沙镇" }),
      makeCard({ id: "loc-2", projectId: "p2", kind: "location", title: "别处" })
    ];
    expect(resolveBackgroundSelection(cards, "p1", "", "loc-2")).toBeUndefined();
    expect(resolveBackgroundSelection(cards, "p1", "", "loc-1")?.id).toBe("loc-1");
    expect(resolveBackgroundSelection(cards, "p2", "", "loc-2")?.id).toBe("loc-2");
  });

  it("异步乱序：A 项目请求晚到，不应让 p1 页面看到 p2 的卡片", () => {
    const p2Cards: CardSummary[] = [
      makeCard({ id: "loc-2", projectId: "p2", kind: "location", title: "别处" })
    ];
    const p1Cards: CardSummary[] = [
      makeCard({ id: "loc-1", projectId: "p1", kind: "location", title: "黄沙镇" })
    ];
    const p1Selected = resolveBackgroundSelection(p2Cards, "p1", "", "loc-2");
    expect(p1Selected).toBeUndefined();
    const p1SelectedOwn = resolveBackgroundSelection(p1Cards, "p1", "", "loc-1");
    expect(p1SelectedOwn?.id).toBe("loc-1");
  });
});
