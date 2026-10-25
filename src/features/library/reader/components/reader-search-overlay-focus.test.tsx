// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReaderSearchOverlay } from "@/features/library/reader/components/ReaderSearchOverlay";

// BD-183：查找浮层抢焦点进输入框，关闭后节点直接消失，焦点掉回 body。
// 出口契约（同 BD-176 ReplacePanel）：卸载时把焦点还给开它的那个控件。

const CONTENT = "雨夜来信。雨夜很冷。";

function renderOverlay() {
  return render(
    <ReaderSearchOverlay content={CONTENT} onJumpToOffset={vi.fn()} onClose={vi.fn()} />
  );
}

afterEach(() => cleanup());

describe("阅读器查找浮层 · 焦点归还", () => {
  it("打开时焦点进输入框，卸载后回到开手按钮", () => {
    const opener = document.createElement("button");
    opener.textContent = "全文搜索";
    document.body.appendChild(opener);
    opener.focus();

    const { unmount } = renderOverlay();
    expect(document.activeElement).toBe(screen.getByLabelText("搜索正文关键词"));

    unmount();
    expect(document.activeElement).toBe(opener);

    opener.remove();
  });

  it("按 Esc 走关闭路径后卸载，焦点同样回到开手", () => {
    const opener = document.createElement("button");
    document.body.appendChild(opener);
    opener.focus();

    const onClose = vi.fn();
    const { unmount } = render(
      <ReaderSearchOverlay content={CONTENT} onJumpToOffset={vi.fn()} onClose={onClose} />
    );

    fireEvent.keyDown(screen.getByLabelText("搜索正文关键词"), { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);

    unmount();
    expect(document.activeElement).toBe(opener);

    opener.remove();
  });

  it("开手是 body（Ctrl+F 从正文里按出）时不抢焦点、也不报错", () => {
    expect(document.activeElement).toBe(document.body);

    const { unmount } = renderOverlay();
    expect(document.activeElement).toBe(screen.getByLabelText("搜索正文关键词"));

    unmount();
    expect(document.activeElement).toBe(document.body);
  });

  it("开手已被卸载时保持静默，焦点不落到陈旧节点", () => {
    const holder = document.createElement("div");
    document.body.appendChild(holder);
    const opener = document.createElement("button");
    holder.appendChild(opener);
    opener.focus();

    const { unmount } = renderOverlay();
    holder.remove();

    unmount();
    expect(document.activeElement).toBe(document.body);
  });
});
