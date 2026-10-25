// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ReaderSidePanel } from "@/features/library/ReaderSidePanel";
import type { HighlightItem } from "@/types/library";

// BD-181：每条高亮下面都有 5 颗色板，此前可访问名只是 title 的颜色名。
// 一页 N 条高亮就是 5N 颗「黄色」「红色」——读屏器按钮列表里分不清改的是哪一条。

function makeHighlight(id: string, text: string, color: HighlightItem["color"]): HighlightItem {
  return {
    id,
    bookId: "book-1",
    charOffset: 0,
    charLength: text.length,
    text,
    color,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
}

function renderPanel(highlights: HighlightItem[]) {
  render(
    <ReaderSidePanel
      sidePanelTab="highlights"
      onTabChange={vi.fn()}
      onCollapse={vi.fn()}
      progressPercent={12}
      tocTitle="目录"
      tocEntries={[]}
      onTocJump={vi.fn()}
      tocEmptyText="暂无目录"
      highlights={highlights}
      onRemoveHighlight={vi.fn()}
      onJumpToHighlight={vi.fn()}
      onHighlightsChange={vi.fn()}
      bookmarks={[]}
      onAddBookmark={vi.fn()}
      onJumpToBookmark={vi.fn()}
      onRemoveBookmark={vi.fn()}
    />
  );
}

afterEach(() => cleanup());

describe("阅读侧栏 · 色板的可访问名带条目上下文", () => {
  it("每颗色板念出改哪一条、改成什么颜色", () => {
    renderPanel([makeHighlight("hl-1", "雨夜", "green")]);

    const yellow = screen.getByRole("button", { name: "将高亮「雨夜」改为黄色" });
    expect(yellow.getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "将高亮「雨夜」改为绿色" }).getAttribute("aria-pressed")).toBe("true");
  });

  it("两条高亮时十颗色板的名字两两不同", () => {
    renderPanel([
      makeHighlight("hl-1", "雨夜", "yellow"),
      makeHighlight("hl-2", "晴天", "blue")
    ]);

    const names = screen
      .getAllByRole("button")
      .map((button) => button.getAttribute("aria-label"))
      .filter((label): label is string => Boolean(label?.startsWith("将高亮")));

    expect(names).toHaveLength(10);
    expect(new Set(names).size).toBe(10);
    expect(names).toContain("将高亮「晴天」改为紫色");
  });

  it("长摘录按 12 字截断，名字里仍是可辨认的前缀", () => {
    renderPanel([makeHighlight("hl-1", "这一段高亮文字很长很长很长很长尾巴", "yellow")]);

    expect(
      screen.getByRole("button", { name: "将高亮「这一段高亮文字很长很长很…」改为红色" })
    ).toBeTruthy();
  });
});
