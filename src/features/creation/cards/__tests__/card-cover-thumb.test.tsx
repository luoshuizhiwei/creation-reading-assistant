// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardCoverThumb } from "@/features/creation/cards/components/CardCoverThumb";
import { CardListSidebar } from "@/features/creation/cards/components/CardListSidebar";
import type { CardSummary } from "@/types/creation";

function cardOf(overrides: Partial<CardSummary> = {}): CardSummary {
  return {
    id: "card-1",
    projectId: null,
    linkedProjectIds: [],
    usageCount: 0,
    kind: "character",
    title: "林墨",
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-09-12T00:00:00.000Z",
    updatedAt: "2026-09-12T00:00:00.000Z",
    revision: 1,
    coverResourceId: null,
    ...overrides
  } as CardSummary;
}

afterEach(() => cleanup());

describe("CardCoverThumb 列表封面缩略图（Stage 4-A 增强）", () => {
  it("无封面时渲染占位，不渲染 img（屏幕阅读器不会当成图片）", () => {
    render(<CardCoverThumb cardId="card-1" coverResourceId={null} title="林墨" />);
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByTitle("林墨 尚未设置封面")).toBeDefined();
  });

  it("coverResourceId 缺失时同样走占位", () => {
    render(<CardCoverThumb cardId="card-1" title="林墨" />);
    expect(screen.queryByRole("img")).toBeNull();
  });

  it("有封面时 img 指向 creation-asset 只读协议并带可读 alt", () => {
    render(<CardCoverThumb cardId="card-1" coverResourceId="resource-cover" title="林墨" />);
    const image = screen.getByRole("img", { name: "林墨 封面" }) as HTMLImageElement;
    expect(image.getAttribute("src")).toBe("creation-asset://card/card-1/resource-cover");
    // 列表缩略图固定 40x40，不撑开列表项。
    expect(image.getAttribute("width")).toBe("40");
    expect(image.getAttribute("height")).toBe("40");
  });

  it("读取失败时退化为占位而不是破图", () => {
    render(<CardCoverThumb cardId="card-1" coverResourceId="resource-cover" title="林墨" />);
    fireEvent.error(screen.getByRole("img", { name: "林墨 封面" }));
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByTitle("封面读取失败")).toBeDefined();
  });

  it("自定义 size 生效", () => {
    render(<CardCoverThumb cardId="card-1" coverResourceId="rc" title="林墨" size={64} />);
    const image = screen.getByRole("img", { name: "林墨 封面" }) as HTMLImageElement;
    expect(image.getAttribute("width")).toBe("64");
  });
});

describe("CardListSidebar 接入封面缩略图（Stage 4-A 增强）", () => {
  const typeNameMap = new Map<string, string>([["character", "角色"]]);

  it("无封面卡片在列表里显示占位，不出现 img", () => {
    render(
      <CardListSidebar
        cards={[cardOf({ id: "card-1", title: "林墨", coverResourceId: null })]}
        cardsLoading={false}
        onSelectCard={() => {}}
        typeNameMap={typeNameMap}
      />
    );
    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByText("林墨")).toBeDefined();
  });

  it("有封面卡片在列表里直接显示缩略图，无需点开详情", () => {
    render(
      <CardListSidebar
        cards={[cardOf({ id: "card-2", title: "苏青", coverResourceId: "res-cover-2" })]}
        cardsLoading={false}
        onSelectCard={() => {}}
        typeNameMap={typeNameMap}
      />
    );
    const image = screen.getByRole("img", { name: "苏青 封面" }) as HTMLImageElement;
    expect(image.getAttribute("src")).toBe("creation-asset://card/card-2/res-cover-2");
  });

  it("混合列表：只有设了封面的卡片才有 img", () => {
    render(
      <CardListSidebar
        cards={[
          cardOf({ id: "card-1", title: "林墨", coverResourceId: null }),
          cardOf({ id: "card-2", title: "苏青", coverResourceId: "res-2" }),
          cardOf({ id: "card-3", title: "顾淮", coverResourceId: null })
        ]}
        cardsLoading={false}
        onSelectCard={() => {}}
        typeNameMap={typeNameMap}
      />
    );
    expect(screen.getAllByRole("img")).toHaveLength(1);
    expect(screen.getByRole("img", { name: "苏青 封面" })).toBeDefined();
  });

  it("点击列表项仍能选中卡片（缩略图不拦截点击）", () => {
    let picked = "";
    render(
      <CardListSidebar
        cards={[cardOf({ id: "card-9", title: "旧书店", coverResourceId: "res-9" })]}
        cardsLoading={false}
        onSelectCard={(id) => {
          picked = id;
        }}
        typeNameMap={typeNameMap}
      />
    );
    fireEvent.click(screen.getByRole("img", { name: "旧书店 封面" }));
    expect(picked).toBe("card-9");
  });
});
