// @vitest-environment jsdom
/**
 * BD-128：aria-modal 弹窗的 Tab 环。
 *
 * 实测（Electron + Playwright）在「新建项目」向导里连按 3 次 Tab，焦点就跑到弹窗后的
 * 窗口控制按钮（.desktop-window-control）上——aria-modal 承诺弹窗外惰性，浏览器并不会
 * 自动兑现。此测试钉住首尾成环：末控件 Tab 回首个，首控件 Shift+Tab 到末个。
 */
import React from "react";
import { render, fireEvent, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, beforeAll } from "vitest";
import { Dialog } from "../Dialog";

beforeAll(() => {
  if (typeof requestAnimationFrame !== "function") {
    (globalThis as unknown as { requestAnimationFrame: (cb: FrameRequestCallback) => number }).requestAnimationFrame =
      (cb) => setTimeout(() => cb(0), 0) as unknown as number;
    (globalThis as unknown as { cancelAnimationFrame: (id: number) => void }).cancelAnimationFrame = (id) =>
      clearTimeout(id as unknown as ReturnType<typeof setTimeout>);
  }
});

function TrapHarness() {
  return (
    <Dialog open title="焦点环" hideCloseButton onClose={() => {}}>
      <button type="button">甲</button>
      <button type="button">乙</button>
    </Dialog>
  );
}

describe("BD-128 Dialog Tab 环", () => {
  it("末控件按 Tab 回到首个控件", async () => {
    render(<TrapHarness />);
    await screen.findByRole("dialog");
    const first = screen.getByRole("button", { name: "甲" });
    const last = screen.getByRole("button", { name: "乙" });
    await waitFor(() => expect(document.activeElement).toBe(first));

    last.focus();
    fireEvent.keyDown(last, { key: "Tab" });
    expect(document.activeElement).toBe(first);
  });

  it("首控件按 Shift+Tab 到末个控件", async () => {
    render(<TrapHarness />);
    await screen.findByRole("dialog");
    const first = screen.getByRole("button", { name: "甲" });
    const last = screen.getByRole("button", { name: "乙" });
    await waitFor(() => expect(document.activeElement).toBe(first));

    fireEvent.keyDown(first, { key: "Tab", shiftKey: true });
    expect(document.activeElement).toBe(last);
  });
});
