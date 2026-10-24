// @vitest-environment jsdom
/**
 * BD-131：阅读器「摘录到资料」弹层的键盘契约。
 *
 * 此前该弹层没有任何键盘处理：Esc 不关闭、打开后焦点仍留在正文、Tab 可直接穿到弹层后的
 * 阅读器控件。接入 useModalKeyboard 后要求：Esc 关闭并归还焦点、Tab 在弹层内成环。
 */
import React from "react";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import type { ExcerptSourceSnapshot } from "@/types/library";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

const source: ExcerptSourceSnapshot = {
  bookId: "b1",
  bookTitle: "夜雨书",
  format: "txt",
  locationLabel: "第三章 · 42% 附近",
  excerpt: "窗外正下着雨。"
};

function Harness({ onClose }: { onClose: () => void }) {
  const [open, setOpen] = React.useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        摘录
      </button>
      {open && (
        <ExcerptPicker
          source={source}
          projects={[{ id: "p1", title: "项目甲" }]}
          isSubmitting={false}
          onClose={() => {
            onClose();
            setOpen(false);
          }}
          onSubmit={async () => ({ success: true })}
          onResult={() => {}}
        />
      )}
    </div>
  );
}

describe("BD-131 摘录弹层键盘契约", () => {
  it("Esc 关闭并把焦点还给触发按钮", async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const trigger = screen.getByRole("button", { name: "摘录" });
    trigger.focus();
    fireEvent.click(trigger);

    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    fireEvent.keyDown(document.activeElement as Element, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(trigger);
  });

  it("Tab 在弹层内首尾成环", async () => {
    render(<Harness onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "摘录" }));
    const dialog = await screen.findByRole("dialog");
    const close = screen.getByRole("button", { name: "关闭" });
    const save = screen.getByRole("button", { name: "保存摘录" });

    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));

    save.focus();
    fireEvent.keyDown(save, { key: "Tab" });
    expect(document.activeElement).toBe(close);

    fireEvent.keyDown(close, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(save);
  });
});
