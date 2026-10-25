// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ReaderSidePanel } from "@/features/library/ReaderSidePanel";
import type { BookmarkItem, HighlightItem } from "@/types/library";

// BD-168：两个删除钮平时 opacity-0，只有 group-hover 时才显形。
// 它们一直在 Tab 序列里——键盘用户会停在一个完全看不见的按钮上（2.4.7）。
const highlight: HighlightItem = {
  id: "hl-1",
  bookId: "book-1",
  charOffset: 0,
  charLength: 2,
  text: "雨夜",
  color: "yellow",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:00.000Z"
};

const bookmark: BookmarkItem = {
  id: "bm-1",
  bookId: "book-1",
  label: "第一章",
  createdAt: "2026-01-01T00:00:00.000Z"
};

function renderPanel(tab: "highlights" | "bookmarks") {
  return render(
    <ReaderSidePanel
      sidePanelTab={tab}
      onTabChange={vi.fn()}
      onCollapse={vi.fn()}
      progressPercent={12}
      tocTitle="目录"
      tocEntries={[]}
      onTocJump={vi.fn()}
      tocEmptyText="暂无目录"
      highlights={[highlight]}
      onRemoveHighlight={vi.fn()}
      onJumpToHighlight={vi.fn()}
      onHighlightsChange={vi.fn()}
      bookmarks={[bookmark]}
      onAddBookmark={vi.fn()}
      onJumpToBookmark={vi.fn()}
      onRemoveBookmark={vi.fn()}
    />
  );
}

afterEach(() => cleanup());

describe("阅读侧栏 · 悬停才显形的删除钮", () => {
  it("高亮删除钮聚焦时同样显形，仍在 Tab 序列里", () => {
    renderPanel("highlights");
    const button = screen.getByTitle("删除高亮");
    expect(button.getAttribute("tabindex")).toBeNull();
    expect(button.className).toContain("focus-visible:opacity-100");

    button.focus();
    expect(document.activeElement).toBe(button);
  });

  it("书签删除钮聚焦时同样显形", () => {
    renderPanel("bookmarks");
    const button = screen.getByTitle("删除书签");
    expect(button.getAttribute("tabindex")).toBeNull();
    expect(button.className).toContain("focus-visible:opacity-100");
  });
});
