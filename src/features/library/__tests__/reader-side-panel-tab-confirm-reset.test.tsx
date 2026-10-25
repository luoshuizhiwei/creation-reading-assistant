// @vitest-environment jsdom
import React, { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSidePanel } from "@/features/library/ReaderSidePanel";
import type { BookmarkItem, HighlightItem } from "@/types/library";

// BD-186：两步确认是「就着眼前这一行」的临时契约。侧栏切页签（视图上下文变了）
// 之后，处于确认态的删除钮必须退回一步态，否则回来再点一下就是直接删除。

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

function Harness({ onRemoveHighlight }: { onRemoveHighlight: (id: string) => void }) {
  const [tab, setTab] = useState<"toc" | "highlights" | "bookmarks">("highlights");
  return (
    <ReaderSidePanel
      sidePanelTab={tab}
      onTabChange={setTab}
      onCollapse={vi.fn()}
      progressPercent={12}
      tocTitle="目录"
      tocEntries={[]}
      onTocJump={vi.fn()}
      tocEmptyText="暂无目录"
      highlights={[makeHighlight("hl-1", "雨夜"), makeHighlight("hl-2", "晴天")]}
      onRemoveHighlight={onRemoveHighlight}
      onJumpToHighlight={vi.fn()}
      onHighlightsChange={vi.fn()}
      bookmarks={[makeBookmark("bm-1", "第一章")]}
      onAddBookmark={vi.fn()}
      onJumpToBookmark={vi.fn()}
      onRemoveBookmark={vi.fn()}
    />
  );
}

afterEach(() => cleanup());

describe("阅读侧栏 · 切页签作废两步确认（BD-186）", () => {
  it("高亮页签武装删除后切到书签再切回，按钮退回一步态", () => {
    render(<Harness onRemoveHighlight={vi.fn()} />);

    fireEvent.click(screen.getByLabelText("删除高亮「雨夜」"));
    expect(screen.getByLabelText("再次点击确认删除高亮「雨夜」")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: /^书签/ }));
    fireEvent.click(screen.getByRole("button", { name: /^高亮/ }));

    expect(screen.queryByLabelText("再次点击确认删除高亮「雨夜」")).toBeNull();
    expect(screen.getByLabelText("删除高亮「雨夜」")).toBeTruthy();
  });

  it("作废后第一次点击只是重新武装，不会直接删除", () => {
    const onRemoveHighlight = vi.fn();
    render(<Harness onRemoveHighlight={onRemoveHighlight} />);

    fireEvent.click(screen.getByLabelText("删除高亮「雨夜」"));
    fireEvent.click(screen.getByRole("button", { name: /^书签/ }));
    fireEvent.click(screen.getByRole("button", { name: /^高亮/ }));

    fireEvent.click(screen.getByLabelText("删除高亮「雨夜」"));
    expect(onRemoveHighlight).not.toHaveBeenCalled();
    expect(screen.getByLabelText("再次点击确认删除高亮「雨夜」")).toBeTruthy();

    fireEvent.click(screen.getByLabelText("再次点击确认删除高亮「雨夜」"));
    expect(onRemoveHighlight).toHaveBeenCalledWith("hl-1");
  });

  it("书签页签武装后切去目录再切回，确认态同样作废", () => {
    const onRemoveHighlight = vi.fn();
    render(<Harness onRemoveHighlight={onRemoveHighlight} />);

    fireEvent.click(screen.getByRole("button", { name: /^书签/ }));
    fireEvent.click(screen.getByLabelText("删除书签「第一章」"));
    expect(screen.getByLabelText("再次点击确认删除书签「第一章」")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: /目录/ }));
    fireEvent.click(screen.getByRole("button", { name: /^书签/ }));

    expect(screen.queryByLabelText("再次点击确认删除书签「第一章」")).toBeNull();
    expect(screen.getByLabelText("删除书签「第一章」")).toBeTruthy();
  });

  it("不切页签时两步确认保持原样：一次点击只武装、不删除", () => {
    const onRemoveHighlight = vi.fn();
    render(<Harness onRemoveHighlight={onRemoveHighlight} />);

    fireEvent.click(screen.getByLabelText("删除高亮「晴天」"));
    expect(onRemoveHighlight).not.toHaveBeenCalled();
    expect(screen.getByLabelText("再次点击确认删除高亮「晴天」")).toBeTruthy();
  });
});
