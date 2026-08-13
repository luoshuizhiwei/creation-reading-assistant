// @vitest-environment jsdom
import React, { useState } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CommandPalette } from "@/features/creation/command/CommandPalette";
import type { PaletteCommand } from "@/features/creation/command/command-palette";

function makeCommands(): PaletteCommand[] {
  return [
    { id: "view.writing", label: "正文写作台", group: "视图", keywords: ["写作"], run: vi.fn() },
    { id: "view.cards", label: "卡片管理", group: "视图", keywords: ["卡片"], run: vi.fn() },
    { id: "project.create", label: "新建项目", group: "项目", keywords: ["向导"], run: vi.fn() }
  ];
}

function renderPalette(commands: PaletteCommand[], onClose = vi.fn()) {
  render(<CommandPalette commands={commands} onClose={onClose} />);
  return { onClose, commands };
}

afterEach(() => cleanup());

describe("CommandPalette（真实渲染与键盘交互）", () => {
  it("打开后聚焦输入框，对话框与列表语义正确", () => {
    renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    expect(document.activeElement).toBe(input);
    expect(screen.getByRole("dialog", { name: "命令面板" })).toBeDefined();
    expect(input.getAttribute("aria-expanded")).toBe("true");
    expect(screen.getAllByRole("option")).toHaveLength(3);
  });

  it("输入过滤真实渲染的命令列表", () => {
    renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    fireEvent.change(input, { target: { value: "写作" } });
    expect(screen.getByText("正文写作台")).toBeDefined();
    expect(screen.queryByText("卡片管理")).toBeNull();
    expect(screen.queryByText("新建项目")).toBeNull();

    fireEvent.change(input, { target: { value: "不存在" } });
    expect(screen.getByRole("status").textContent).toContain("没有匹配的命令");
  });

  it("ArrowDown / ArrowUp 移动活动项并同步 aria-activedescendant 与 aria-selected", () => {
    renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    const options = screen.getAllByRole("option");

    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);
    expect(options[0]!.getAttribute("aria-selected")).toBe("true");

    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(input.getAttribute("aria-activedescendant")).toBe(options[1]!.id);
    expect(options[1]!.getAttribute("aria-selected")).toBe("true");
    expect(options[0]!.getAttribute("aria-selected")).toBe("false");

    fireEvent.keyDown(input, { key: "ArrowUp" });
    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);

    fireEvent.keyDown(input, { key: "ArrowUp" });
    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(input.getAttribute("aria-activedescendant")).toBe(options[2]!.id);
  });

  it("Enter 执行当前选中命令并关闭面板", () => {
    const { onClose, commands } = renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });

    fireEvent.keyDown(input, { key: "ArrowDown" });
    fireEvent.keyDown(input, { key: "Enter" });

    expect(commands[1]!.run).toHaveBeenCalledTimes(1);
    expect(commands[0]!.run).not.toHaveBeenCalled();
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("Escape 关闭面板", () => {
    const { onClose } = renderPalette(makeCommands());
    fireEvent.keyDown(screen.getByRole("combobox", { name: "筛选命令" }), { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("关闭按钮点击关闭面板", () => {
    const { onClose } = renderPalette(makeCommands());
    fireEvent.click(screen.getByRole("button", { name: "关闭命令面板" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("IME 组合期间 Enter / Escape / 方向键不得误触", () => {
    const { onClose, commands } = renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    const options = screen.getAllByRole("option");

    fireEvent.keyDown(input, { key: "Enter", isComposing: true });
    expect(commands[0]!.run).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.keyDown(input, { key: "ArrowDown", isComposing: true });
    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);

    fireEvent.keyDown(input, { key: "Escape", isComposing: true });
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.keyDown(input, { key: "Enter", keyCode: 229 });
    expect(commands[0]!.run).not.toHaveBeenCalled();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("Tab 焦点被限制在输入框与关闭按钮之间（选项不在键盘顺序中）", () => {
    renderPalette(makeCommands());
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    const close = screen.getByRole("button", { name: "关闭命令面板" });
    const dialog = screen.getByRole("dialog", { name: "命令面板" });

    for (const option of screen.getAllByRole("option")) {
      expect(option.tabIndex).toBe(-1);
    }
    const focusables = dialog.querySelectorAll<HTMLElement>("input, button, [tabindex]:not([tabindex='-1'])");
    expect(Array.from(focusables).map((el) => el.tagName)).toEqual(["INPUT", "BUTTON"]);

    fireEvent.keyDown(input, { key: "Tab" });
    expect(document.activeElement).toBe(close);
    fireEvent.keyDown(close, { key: "Tab" });
    expect(document.activeElement).toBe(input);
  });

  it("关闭后焦点恢复到打开前的元素", () => {
    const onClose = vi.fn();
    function Harness() {
      const [open, setOpen] = useState(false);
      return (
        <div>
          <button type="button" onClick={() => setOpen(true)}>打开面板</button>
          {open && <CommandPalette commands={makeCommands()} onClose={() => { onClose(); setOpen(false); }} />}
        </div>
      );
    }
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: "打开面板" });
    trigger.focus();
    fireEvent.click(trigger);

    const input = screen.getByRole("combobox", { name: "筛选命令" });
    expect(document.activeElement).toBe(input);

    fireEvent.keyDown(input, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(document.activeElement).toBe(trigger);
  });
});
