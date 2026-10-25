// @vitest-environment jsdom
/**
 * BD-124 渲染级护栏：目录（TOC）的行不再是 role=button 容器内套 <button>（折叠钮），
 * 跳章动作改由行内一个原生 <button> 承担，折叠钮是它的兄弟并带 aria-expanded。
 * 断言：行内不存在 role=button 容器、不存在 button 套 button、折叠钮有 aria-expanded、
 * 点标题按钮触发 onJump。
 */
import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, fireEvent, screen } from "@testing-library/react";
import { TocList } from "../TocList";

const entries = [
  { id: "p", label: "第一章 序幕", level: 1 },
  { id: "c", label: "1.1 小节", level: 2 },
];

describe("BD-124 目录行不再嵌套交互控件", () => {
  it("行容器无 role=button，且没有任何 button 套 button", () => {
    const { container } = render(<TocList entries={entries} onJump={() => {}} emptyText="无" />);
    expect(container.querySelector('[role="button"]')).toBeNull();
    expect(container.querySelectorAll("button button").length).toBe(0);
  });

  it("折叠钮独立且带 aria-expanded；标题本身是按钮，点击触发跳章", () => {
    const onJump = vi.fn();
    const { container, getByRole } = render(<TocList entries={entries} onJump={onJump} emptyText="无" />);
    expect(container.querySelectorAll('button[aria-expanded]').length).toBeGreaterThan(0);
    fireEvent.click(getByRole("button", { name: /第一章 序幕/ }));
    expect(onJump).toHaveBeenCalledWith("p");
  });

  // BD-175：过滤后匹配数/空态只在屏幕上变化，焦点还在输入框里，读屏器听不到。
  it("过滤结果数与空态通过常驻 status 播报（BD-175）", () => {
    const many = Array.from({ length: 15 }, (_, index) => ({ id: `e${index}`, label: `第${index + 1}章 标题`, level: 1 }));
    const { container } = render(<TocList entries={many} onJump={() => {}} emptyText="无" />);
    const status = container.querySelector('span.sr-only[role="status"]') as HTMLElement;
    expect(status).toBeTruthy();
    expect(status.textContent).toBe("");

    const input = screen.getByLabelText("搜索章节目录");
    fireEvent.change(input, { target: { value: "第1章" } });
    expect(status.textContent).toBe("共 1 个匹配");

    fireEvent.change(input, { target: { value: "查无此章" } });
    expect(status.textContent).toContain("没有匹配");
  });
});
