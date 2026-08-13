// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { InspirationPage } from "@/features/inspiration/InspirationPage";
import { useAppStore } from "@/stores/app-store";

describe("InspirationPage 兼容跳转", () => {
  beforeEach(() => {
    useAppStore.setState({ setScreen: vi.fn() });
  });
  afterEach(() => cleanup());

  it("渲染兼容说明，不再承担独立灵感管理", () => {
    render(<InspirationPage />);
    expect(screen.getByText(/旧灵感中心已并入全局收件箱/)).toBeDefined();
  });

  it("提供「前往全局收件箱」按钮并切换到 inbox 屏幕", () => {
    render(<InspirationPage />);
    const button = screen.getByText("前往全局收件箱");
    fireEvent.click(button);
    expect(useAppStore.getState().setScreen).toHaveBeenCalledWith("inbox");
  });
});
