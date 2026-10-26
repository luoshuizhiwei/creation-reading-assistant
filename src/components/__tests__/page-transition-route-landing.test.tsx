// @vitest-environment jsdom
/**
 * BD-202：路由切换的焦点着陆点。切屏入口按钮位于被 PageTransition 按 screen
 * 整体重挂的子树内，卸载后焦点跌回 body（WCAG 2.4.3）。容器 tabIndex={-1}
 * 接住焦点；换 key 重挂后焦点跟着新容器走。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PageTransition } from "@/components/interaction";

afterEach(() => cleanup());

function Host({ screen: key }: { screen: string }) {
  return (
    <PageTransition screenKey={key}>
      <h1>页面 {key}</h1>
    </PageTransition>
  );
}

describe("BD-202：PageTransition 的路由焦点着陆点", () => {
  it("挂载即由容器接住焦点，容器 tabIndex=-1 不进 Tab 序列", () => {
    render(<Host screen="library" />);
    const box = screen.getByRole("heading", { name: "页面 library" }).parentElement as HTMLElement;
    expect(document.activeElement).toBe(box);
    expect(box.getAttribute("tabindex")).toBe("-1");
  });

  it("换 key 重挂后焦点跟着新容器走，不留在旧子树或 body", () => {
    const view = render(<Host screen="library" />);
    view.rerender(<Host screen="stats" />);
    const box = screen.getByRole("heading", { name: "页面 stats" }).parentElement as HTMLElement;
    expect(document.activeElement).toBe(box);
    expect(screen.queryByRole("heading", { name: "页面 library" })).toBeNull();
  });

  it("聚焦带 preventScroll，路由切换不引发滚动跳位", () => {
    const calls: Array<{ options?: FocusOptions | undefined }> = [];
    const spy = vi.spyOn(HTMLElement.prototype, "focus").mockImplementation(function (this: HTMLElement, options?: FocusOptions) {
      calls.push({ options });
    });
    render(<Host screen="projects" />);
    spy.mockRestore();
    expect(calls.length, "着陆点必须抢在别处聚焦之前发生").toBe(1);
    expect(calls[0].options).toEqual({ preventScroll: true });
  });

  it("同 key 重渲染不再动焦点——一次切换只着陆一次", () => {
    const view = render(<Host screen="library" />);
    view.rerender(<Host screen="reader" />);
    const boxA = screen.getByRole("heading", { name: "页面 reader" }).parentElement as HTMLElement;
    expect(document.activeElement).toBe(boxA);
    // 同 key 重渲染不应再动焦点。
    view.rerender(<Host screen="reader" />);
    expect(document.activeElement).toBe(boxA);
  });
});
