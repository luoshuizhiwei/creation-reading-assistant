// @vitest-environment jsdom
import React, { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSidePanel } from "@/features/library/ReaderSidePanel";
import type { BookmarkItem, HighlightItem } from "@/types/library";

// BD-179：删除一条高亮/书签后，握焦点的删除钮随条目消失，焦点掉回 body，
// 键盘用户要从文档开头重新 Tab。删除应把焦点交接给相邻条目，删空则退回 Tab 钮。

function makeHighlight(id: string, text: string): HighlightItem {
  return {
    id,
    bookId: "book-1",
    charOffset: 0,
    charLength: text.length,
    text,
    color: "yellow",
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z"
  };
}

function makeBookmark(id: string, label: string): BookmarkItem {
  return {
    id,
    bookId: "book-1",
    label,
    createdAt: "2026-01-01T00:00:00.000Z"
  };
}

function Harness({
  tab,
  initialHighlights,
  initialBookmarks
}: {
  tab: "highlights" | "bookmarks";
  initialHighlights: HighlightItem[];
  initialBookmarks: BookmarkItem[];
}) {
  const [highlights, setHighlights] = useState(initialHighlights);
  const [bookmarks, setBookmarks] = useState(initialBookmarks);
  return (
    <ReaderSidePanel
      sidePanelTab={tab}
      onTabChange={vi.fn()}
      onCollapse={vi.fn()}
      progressPercent={12}
      tocTitle="目录"
      tocEntries={[]}
      onTocJump={vi.fn()}
      tocEmptyText="暂无目录"
      highlights={highlights}
      onRemoveHighlight={(id) => setHighlights((list) => list.filter((item) => item.id !== id))}
      onJumpToHighlight={vi.fn()}
      onHighlightsChange={vi.fn()}
      bookmarks={bookmarks}
      onAddBookmark={vi.fn()}
      onJumpToBookmark={vi.fn()}
      onRemoveBookmark={(id) => setBookmarks((list) => list.filter((item) => item.id !== id))}
    />
  );
}

function deleteByLabel(label: string) {
  const button = screen.getByLabelText(label);
  fireEvent.click(button);
  // 第一次点击只是进入确认态，第二次才真删。
  fireEvent.click(button);
}

afterEach(() => cleanup());

describe("阅读侧栏 · 删除条目后的焦点交接", () => {
  it("删掉第一条高亮后焦点落到下一条的正文按钮", () => {
    render(
      <Harness
        tab="highlights"
        initialHighlights={[makeHighlight("hl-1", "雨夜"), makeHighlight("hl-2", "晴天")]}
        initialBookmarks={[]}
      />
    );

    deleteByLabel("删除高亮「雨夜」");

    expect(document.activeElement).toBe(screen.getByRole("button", { name: "晴天" }));
  });

  it("删掉最后一条高亮后焦点退回上一条", () => {
    render(
      <Harness
        tab="highlights"
        initialHighlights={[makeHighlight("hl-1", "雨夜"), makeHighlight("hl-2", "晴天")]}
        initialBookmarks={[]}
      />
    );

    deleteByLabel("删除高亮「晴天」");

    expect(document.activeElement).toBe(screen.getByRole("button", { name: "雨夜" }));
  });

  it("删空高亮列表后焦点退回高亮页签，仍留在侧栏内", () => {
    render(
      <Harness
        tab="highlights"
        initialHighlights={[makeHighlight("hl-1", "雨夜")]}
        initialBookmarks={[]}
      />
    );

    deleteByLabel("删除高亮「雨夜」");

    const tabButton = screen.getByRole("button", { name: /^高亮/ });
    expect(document.activeElement).toBe(tabButton);
  });

  it("删掉书签后焦点落到剩余书签上，删空则退回书签页签", () => {
    render(
      <Harness
        tab="bookmarks"
        initialHighlights={[]}
        initialBookmarks={[makeBookmark("bm-1", "第一章"), makeBookmark("bm-2", "第二章")]}
      />
    );

    deleteByLabel("删除书签「第一章」");
    expect(document.activeElement).toBe(screen.getByRole("button", { name: /^第二章/ }));

    deleteByLabel("删除书签「第二章」");
    expect(document.activeElement).toBe(screen.getByRole("button", { name: /^书签/ }));
  });
});
