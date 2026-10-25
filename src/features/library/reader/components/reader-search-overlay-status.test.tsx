// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSearchOverlay } from "@/features/library/reader/components/ReaderSearchOverlay";

// BD-180：命中位次原来只是视觉文本，焦点一直在输入框里，Enter 换一处、改字改计数
// 都不播报。现在挂一条常驻 role=status，读中文位次。

const CONTENT = "雨夜来信。雨夜很冷。又是雨夜。最后是晴天。";

function renderOverlay() {
  const onJumpToOffset = vi.fn();
  render(
    <ReaderSearchOverlay
      content={CONTENT}
      onJumpToOffset={onJumpToOffset}
      onClose={vi.fn()}
    />
  );
  return { onJumpToOffset, input: screen.getByLabelText("搜索正文关键词") };
}

afterEach(() => cleanup());

describe("阅读器查找浮层 · 命中位次播报", () => {
  it("还没输入时状态区为空，视觉位次仍显示待输入", () => {
    renderOverlay();
    expect(screen.getByRole("status").textContent).toBe("");
    expect(screen.getByText("待输入")).toBeTruthy();
  });

  it("输入后播报第一处与总数，视觉串保持原样", () => {
    const { input, onJumpToOffset } = renderOverlay();
    fireEvent.change(input, { target: { value: "雨夜" } });

    expect(screen.getByRole("status").textContent).toBe("第 1 处，共 3 处");
    expect(input.closest('[role="search"]')?.textContent).toContain("1 / 3");
    expect(onJumpToOffset).toHaveBeenLastCalledWith(0);
  });

  it("Enter 换下一处时位次跟着播报，Shift+Enter 回到上一处", () => {
    const { input, onJumpToOffset } = renderOverlay();
    fireEvent.change(input, { target: { value: "雨夜" } });

    fireEvent.keyDown(input, { key: "Enter" });
    expect(screen.getByRole("status").textContent).toBe("第 2 处，共 3 处");
    expect(onJumpToOffset).toHaveBeenLastCalledWith(CONTENT.indexOf("雨夜", 1));

    fireEvent.keyDown(input, { key: "Enter", shiftKey: true });
    expect(screen.getByRole("status").textContent).toBe("第 1 处，共 3 处");
  });

  it("没有命中时播报没有匹配结果", () => {
    const { input } = renderOverlay();
    fireEvent.change(input, { target: { value: "查无此词" } });

    expect(screen.getByRole("status").textContent).toBe("没有匹配结果");
    expect(screen.getByText("无结果")).toBeTruthy();
  });

  it("命中数触到 1000 上限时播报「共 1000 处以上」", () => {
    const longContent = "雨".repeat(1200);
    render(
      <ReaderSearchOverlay content={longContent} onJumpToOffset={vi.fn()} onClose={vi.fn()} />
    );
    fireEvent.change(screen.getByLabelText("搜索正文关键词"), { target: { value: "雨" } });

    expect(screen.getByRole("status").textContent).toBe("第 1 处，共 1000 处以上");
  });
});
