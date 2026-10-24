// @vitest-environment jsdom
/**
 * BD-127：Dialog 的两个键盘行为。
 *
 * 1) Esc：旧实现把 Escape 监听挂在 document，而弹窗内层 div 又 stopPropagation()，
 *    焦点在弹窗里时（打开后必然如此）事件到不了 document，Esc 完全关不掉弹窗。
 * 2) 焦点归还：关闭后焦点必须回到打开前的元素；旧实现落到 body。
 */
import React from "react";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeAll } from "vitest";
import { Dialog } from "../Dialog";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    // jsdom 未开 pretendToBeVisual 时同步补一个
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

function Harness() {
  const [open, setOpen] = React.useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        打开弹窗
      </button>
      <Dialog open={open} title="演示弹窗" onClose={() => setOpen(false)}>
        <input aria-label="名称" />
      </Dialog>
    </div>
  );
}

describe("BD-127 Dialog 键盘行为", () => {
  it("焦点在弹窗内时按 Esc 会关闭弹窗", async () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "打开弹窗" }));
    const dialogEl = await screen.findByRole("dialog");
    await waitFor(() => expect(dialogEl.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document.activeElement as Element, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
  });

  it("关闭弹窗后焦点回到打开前的按钮", async () => {
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: "打开弹窗" });
    trigger.focus();
    fireEvent.click(trigger);
    await screen.findByRole("dialog");
    await waitFor(() => expect(document.activeElement).not.toBe(trigger));

    fireEvent.keyDown(document.activeElement as Element, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(document.activeElement).toBe(trigger);
  });
});
