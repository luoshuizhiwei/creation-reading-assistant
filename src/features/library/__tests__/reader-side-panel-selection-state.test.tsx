// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ReaderSidePanel } from "@/features/library/ReaderSidePanel";
import type { BookmarkItem, HighlightItem } from "@/types/library";

// BD-170：三 Tab 与高亮配色此前只用 bg-copper/10、ring 这类视觉类表达选中，
// 读屏器读到的是一排没有任何状态的按钮——不知道当前在哪个 Tab、这条高亮是什么颜色。
const highlight: HighlightItem = {
  id: "hl-1",
  bookId: "book-1",
  charOffset: 0,
  charLength: 2,
  text: "雨夜",
  color: "green",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:00.000Z"
};

const bookmark: BookmarkItem = {
  id: "bm-1",
  bookId: "book-1",
  label: "第一章",
  createdAt: "2026-01-01T00:00:00.000Z"
};

function renderPanel(tab: "toc" | "highlights" | "bookmarks") {
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

describe("阅读侧栏 · 选中态暴露给辅助技术（BD-170）", () => {
  it("三个视图 Tab 用 aria-pressed 表达当前项", () => {
    renderPanel("highlights");
    expect(screen.getByRole("button", { name: /^目录/ }).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: /^高亮/ }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: /^书签/ }).getAttribute("aria-pressed")).toBe("false");
  });

  it("高亮配色选项按当前颜色给出 aria-pressed", () => {
    renderPanel("highlights");
    expect(screen.getByRole("button", { name: "绿色" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "黄色" }).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "紫色" }).getAttribute("aria-pressed")).toBe("false");
  });
});
