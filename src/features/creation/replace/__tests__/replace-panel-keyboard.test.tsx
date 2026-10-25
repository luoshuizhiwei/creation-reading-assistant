// @vitest-environment jsdom
/**
 * BD-176：查找替换浮层是键盘可打开的（工具栏按钮），但打开后焦点留在触发钮上、
 * Esc 也不管用——键盘用户得把整页 Tab 一遍才能到"查找"，收起还得摸到关闭钮。
 * 这里钉三件事：打开即聚焦查找框、Esc 关闭、关闭后焦点回到原处。
 */
import React from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { ReplacePanel } from "../ReplacePanel";
import type { ReplacePlanService } from "../types";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

afterEach(() => cleanup());

function stubService(): ReplacePlanService {
  return {
    createPlan: vi.fn(),
    applyPlan: vi.fn(),
    cancel: vi.fn()
  } as unknown as ReplacePlanService;
}

describe("ReplacePanel 键盘进出", () => {
  it("打开后焦点落在查找框（BD-176）", async () => {
    render(<ReplacePanel projectId="p1" chapterId="c1" onClose={vi.fn()} service={stubService()} />);

    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("replace-find")));
  });

  it("Esc 关闭面板（BD-176）", () => {
    const onClose = vi.fn();
    render(<ReplacePanel projectId="p1" chapterId="c1" onClose={onClose} service={stubService()} />);

    fireEvent.keyDown(screen.getByTestId("replace-find"), { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("输入法组合中的 Esc 不关闭（BD-176）", () => {
    const onClose = vi.fn();
    render(<ReplacePanel projectId="p1" chapterId="c1" onClose={onClose} service={stubService()} />);

    fireEvent.keyDown(screen.getByTestId("replace-find"), { key: "Escape", keyCode: 229 });
    expect(onClose).not.toHaveBeenCalled();
  });

  it("关闭后焦点回到打开前的元素（BD-176）", async () => {
    const trigger = document.createElement("button");
    trigger.textContent = "查找替换";
    document.body.appendChild(trigger);
    trigger.focus();

    const { unmount } = render(<ReplacePanel projectId="p1" chapterId="c1" onClose={vi.fn()} service={stubService()} />);
    await waitFor(() => expect(document.activeElement).toBe(screen.getByTestId("replace-find")));

    unmount();
    expect(document.activeElement).toBe(trigger);
    trigger.remove();
  });
});
