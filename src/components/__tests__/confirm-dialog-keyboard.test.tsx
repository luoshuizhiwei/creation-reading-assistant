// @vitest-environment jsdom
/**
 * BD-130：全局确认弹窗（ConfirmDialog）的键盘契约。
 *
 * 运行实测两处缺陷：打开后按 1 次 Tab 焦点就穿到弹窗后的窗口控制钮（aria-modal 不自动
 * 生效）；Esc 关闭后焦点落到 body（不归还触发元素）。修复走 useModalKeyboard 共用 hook。
 */
import React from "react";
import { render, fireEvent, screen, waitFor, act } from "@testing-library/react";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "@/components/interaction";
import { useUIStore } from "@/stores/ui-store";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

beforeEach(() => {
  useUIStore.setState({ confirmRequest: undefined });
});
afterEach(() => {
  useUIStore.setState({ confirmRequest: undefined });
});

function openConfirm(resolve: (value: boolean) => void) {
  useUIStore.setState({
    confirmRequest: {
      id: "c1",
      title: "删除项目？",
      body: "此操作不可撤销。",
      confirmLabel: "删除",
      cancelLabel: "取消",
      tone: "danger",
      resolve
    }
  });
}

describe("BD-130 ConfirmDialog 键盘契约", () => {
  it("Tab 在弹窗内首尾成环（末个确认钮 Tab 回取消钮）", async () => {
    render(<ConfirmDialog />);
    act(() => openConfirm(vi.fn()));
    const dialog = await screen.findByRole("dialog");
    const cancel = screen.getByRole("button", { name: "取消" });
    const confirm = screen.getByRole("button", { name: "删除" });

    await waitFor(() => expect(document.activeElement).toBe(confirm));
    expect(dialog.contains(document.activeElement)).toBe(true);

    fireEvent.keyDown(confirm, { key: "Tab" });
    expect(document.activeElement).toBe(cancel);

    fireEvent.keyDown(cancel, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(confirm);
  });

  it("Esc 关闭后焦点回到打开前的元素", async () => {
    const trigger = document.createElement("button");
    trigger.textContent = "触发";
    document.body.appendChild(trigger);
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    render(<ConfirmDialog />);
    const resolve = vi.fn();
    act(() => openConfirm(resolve));
    await screen.findByRole("dialog");
    await waitFor(() => expect(document.activeElement).not.toBe(trigger));

    const confirm = screen.getByRole("button", { name: "删除" });
    fireEvent.keyDown(confirm, { key: "Escape" });
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());

    expect(resolve).toHaveBeenCalledWith(false);
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });
});
