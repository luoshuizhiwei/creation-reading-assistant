// @vitest-environment jsdom
import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { ReaderTopNav } from "./ReaderTopNav";
import { ReaderBottomBar } from "./ReaderBottomBar";
import { ReaderSearchOverlay } from "./ReaderSearchOverlay";

describe("ReaderTopNav", () => {
  it("renders title, author, chapter title, and progress", () => {
    const onBack = vi.fn();
    const onToggleSearch = vi.fn();
    const onToggleToc = vi.fn();
    const onOpenSettings = vi.fn();

    render(
      <ReaderTopNav
        title="测试作品"
        author="测试作者"
        currentChapterTitle="第一章 序幕"
        progressPercent={42}
        totalReadingTimeMs={60000}
        isTocOpen={true}
        isSearchOpen={false}
        onBack={onBack}
        onToggleSearch={onToggleSearch}
        onToggleToc={onToggleToc}
        onOpenSettings={onOpenSettings}
      />
    );

    expect(screen.getByText("测试作品")).toBeDefined();
    expect(screen.getByText("测试作者")).toBeDefined();
    expect(screen.getByText(/第一章 序幕/)).toBeDefined();
    expect(screen.getByText("42%")).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "返回书库" }));
    expect(onBack).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "全文搜索" }));
    expect(onToggleSearch).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "收起目录" }));
    expect(onToggleToc).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "阅读设置" }));
    expect(onOpenSettings).toHaveBeenCalledTimes(1);
  });
});

describe("ReaderBottomBar", () => {
  it("renders pages, word count, reading time and handles seek", () => {
    const onSeekPercent = vi.fn();

    render(
      <ReaderBottomBar
        currentPage={3}
        totalPages={10}
        totalWords={15200}
        progressPercent={30}
        totalReadingTimeMs={120000}
        onSeekPercent={onSeekPercent}
      />
    );

    expect(screen.getByText("第 3 / 10 页")).toBeDefined();
    expect(screen.getByText("15,200 字")).toBeDefined();
    expect(screen.getByText("30%")).toBeDefined();

    const slider = screen.getByRole("slider", { name: "阅读进度跳转" });
    fireEvent.change(slider, { target: { value: "55" } });
    expect(onSeekPercent).toHaveBeenCalledWith(55);
  });
});

describe("ReaderSearchOverlay", () => {
  it("searches text, reports match counts and handles next/prev navigation", () => {
    const onJumpToOffset = vi.fn();
    const onClose = vi.fn();
    const sampleContent = "山不在高，有仙则名。水不在深，有龙则灵。斯是陋室，惟吾德馨。";

    render(
      <ReaderSearchOverlay
        content={sampleContent}
        onJumpToOffset={onJumpToOffset}
        onClose={onClose}
      />
    );

    const input = screen.getByRole("textbox", { name: "搜索正文关键词" });
    fireEvent.change(input, { target: { value: "不在" } });

    // "不在" occurs 2 times in sampleContent
    expect(screen.getByText("1 / 2")).toBeDefined();
    expect(onJumpToOffset).toHaveBeenCalledWith(1); // "山不在高..." offset of first "不在" is 1

    const nextBtn = screen.getByRole("button", { name: "下一处" });
    fireEvent.click(nextBtn);
    expect(screen.getByText("2 / 2")).toBeDefined();

    const prevBtn = screen.getByRole("button", { name: "上一处" });
    fireEvent.click(prevBtn);
    expect(screen.getByText("1 / 2")).toBeDefined();

    fireEvent.keyDown(input, { key: "Enter" });
    expect(screen.getByText("2 / 2")).toBeDefined();

    fireEvent.keyDown(input, { key: "Enter", shiftKey: true });
    expect(screen.getByText("1 / 2")).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "关闭搜索" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
