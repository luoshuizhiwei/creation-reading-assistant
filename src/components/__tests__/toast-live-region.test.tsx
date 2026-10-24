// @vitest-environment jsdom
/**
 * BD-137：全局提示（toast）必须是可被辅助技术感知的状态消息。
 *
 * 此前 ToastCenter 只是一个普通 div + article：保存失败、导入失败这类 error 提示
 * 在屏幕上出现、读屏器却完全不知道（WCAG 4.1.3 Status Messages）。修复要求：
 * - 常驻的 live region（先于消息存在，插入时才可能被播报）；
 * - 普通提示 polite，error 走 alert 断言播报；
 * - 每条消息做原子播报，标题与正文不被拆散。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import { ToastCenter } from "@/components/interaction";
import { useUIStore } from "@/stores/ui-store";
import type { ToastItem } from "@/stores/ui-store";

function toast(overrides: Partial<ToastItem> = {}): ToastItem {
  return {
    id: "t1",
    tone: "success",
    title: "已保存",
    body: "场景正文已保存",
    createdAt: 0,
    ...overrides
  };
}

beforeEach(() => {
  useUIStore.setState({ toasts: [] });
});
afterEach(() => cleanup());

describe("ToastCenter 辅助技术可达性", () => {
  it("空态也有常驻 live region", () => {
    render(<ToastCenter />);
    const region = screen.getByRole("status", { name: "通知" });
    expect(region.getAttribute("aria-live")).toBe("polite");
    expect(region.textContent).toBe("");
  });

  it("普通提示进入 polite region，并原子播报标题与正文", () => {
    const view = render(<ToastCenter />);
    act(() => useUIStore.setState({ toasts: [toast()] }));
    view.rerender(<ToastCenter />);

    const region = screen.getByRole("status", { name: "通知" });
    const item = screen.getByText("已保存").closest("article")!;
    expect(region.contains(item)).toBe(true);
    expect(item.getAttribute("aria-atomic")).toBe("true");
    expect(item.getAttribute("role")).toBeNull();
    expect(item.textContent).toContain("场景正文已保存");
  });

  it("错误提示走 alert 断言播报", () => {
    const view = render(<ToastCenter />);
    act(() => useUIStore.setState({ toasts: [toast({ tone: "error", title: "保存失败", body: "磁盘只读" })] }));
    view.rerender(<ToastCenter />);

    const alert = screen.getByRole("alert");
    expect(alert.textContent).toContain("保存失败");
    expect(alert.textContent).toContain("磁盘只读");
    expect(alert.getAttribute("aria-atomic")).toBe("true");
  });
});
