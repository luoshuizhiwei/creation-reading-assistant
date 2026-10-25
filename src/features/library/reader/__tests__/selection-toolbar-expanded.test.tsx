// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSelectionToolbar } from "@/features/library/reader/ReaderSelectionToolbar";
import { EpubSelectionToolbar } from "@/features/library/epub-reader/EpubSelectionToolbar";

// BD-163：两个阅读选区工具条的「高亮」钮都会展开调色板，但展开状态从未挂到 DOM 上，
// 读屏器听到的永远只是一个名叫「高亮」的普通按钮。
afterEach(() => cleanup());

describe("阅读选区工具条 · 高亮调色板展开态", () => {
  it("TXT 工具条用 aria-expanded 暴露调色板开合", () => {
    const { rerender } = render(
      <ReaderSelectionToolbar
        toolbar={{ visible: true, x: 10, y: 10, charOffset: 0, charLength: 2, text: "雨夜" }}
        showColorPicker={false}
        setShowColorPicker={vi.fn()}
        isTxt
        onHighlight={vi.fn(async () => {})}
        onCopy={vi.fn(async () => {})}
        onExcerpt={vi.fn()}
        onAddChapterStart={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByRole("button", { name: "高亮" }).getAttribute("aria-expanded")).toBe("false");

    rerender(
      <ReaderSelectionToolbar
        toolbar={{ visible: true, x: 10, y: 10, charOffset: 0, charLength: 2, text: "雨夜" }}
        showColorPicker
        setShowColorPicker={vi.fn()}
        isTxt
        onHighlight={vi.fn(async () => {})}
        onCopy={vi.fn(async () => {})}
        onExcerpt={vi.fn()}
        onAddChapterStart={vi.fn()}
        onClose={vi.fn()}
      />
    );
    expect(screen.getByRole("button", { name: "高亮" }).getAttribute("aria-expanded")).toBe("true");
  });

  it("EPUB 工具条同步开合，点击仍会回调切换", () => {
    const onToggleColorPicker = vi.fn();
    render(
      <EpubSelectionToolbar
        toolbar={{ visible: true, x: 10, y: 10, cfiRange: "epubcfi(/6/2)", text: "雨夜" }}
        toolbarOffset={{ x: 0, y: 0 }}
        showColorPicker={false}
        onToggleColorPicker={onToggleColorPicker}
        onDragStart={vi.fn()}
        onHighlight={vi.fn()}
        onCopy={vi.fn()}
        onExcerpt={vi.fn()}
        onClose={vi.fn()}
      />
    );
    const trigger = screen.getByRole("button", { name: "高亮" });
    expect(trigger.getAttribute("aria-expanded")).toBe("false");
    fireEvent.click(trigger);
    expect(onToggleColorPicker).toHaveBeenCalledTimes(1);
  });
});
