// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CommandPalette } from "@/features/creation/command/CommandPalette";
import type { PaletteCommand } from "@/features/creation/command/command-palette";

// BD-187：零结果提示此前只在 flat.length===0 那一刻连 role=status 一起挂载，
// polite 区插入即带内容不会被播报。播报必须来自一条自打开就存在、文本可变的常驻区。

function makeCommands(): PaletteCommand[] {
  return [
    { id: "view.writing", label: "正文写作台", group: "视图", keywords: ["写作"], run: vi.fn() },
    { id: "view.cards", label: "卡片管理", group: "视图", keywords: ["卡片"], run: vi.fn() }
  ];
}

afterEach(() => cleanup());

describe("命令面板 · 零结果播报来自常驻区（BD-187）", () => {
  it("打开瞬间播报区就在（空文本），零结果后是同一个节点换文本", () => {
    render(<CommandPalette commands={makeCommands()} onClose={vi.fn()} />);
    const input = screen.getByRole("combobox", { name: "筛选命令" });

    // 有匹配时：播报区存在且为空。
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    fireEvent.change(input, { target: { value: "查无此令" } });
    // 关键：不是新挂载的节点，而是同一条常驻区更新文本。
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("没有匹配的命令。");

    // 可见提示仍在（纯视觉），但不再自带 status 角色。
    expect(screen.getByText("没有匹配的命令。", { selector: "p" })).toBeTruthy();

    fireEvent.change(input, { target: { value: "写作" } });
    expect(region.textContent).toBe("");
  });

  it("未输入关键词时不作播报", () => {
    render(<CommandPalette commands={makeCommands()} onClose={vi.fn()} />);
    const input = screen.getByRole("combobox", { name: "筛选命令" });
    fireEvent.change(input, { target: { value: "   " } });
    expect(screen.getByRole("status").textContent).toBe("");
  });
});
