// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { SettingsSearch } from "../SettingsSearch";

afterEach(cleanup);

describe("设置搜索 · 组合框", () => {
  // BD-161：输入框声明了 role="combobox"，方向键只在视觉上换高亮，
  // 既没有 listbox/option，也没有 aria-activedescendant——读屏器不知道当前候选是哪一条，
  // Enter 却会直接选中它。
  it("候选项以 option 暴露，方向键同步 aria-activedescendant", () => {
    const onSelect = vi.fn();
    render(<SettingsSearch onSelect={onSelect} />);

    const input = screen.getByRole("combobox", { name: "搜索设置项" });
    fireEvent.change(input, { target: { value: "字" } });

    const listbox = screen.getByRole("listbox", { name: "设置搜索结果" });
    const options = within(listbox).getAllByRole("option");
    expect(options.length).toBeGreaterThan(1);
    expect(options[0]!.getAttribute("aria-selected")).toBe("true");
    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);

    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(options[0]!.getAttribute("aria-selected")).toBe("false");
    expect(options[1]!.getAttribute("aria-selected")).toBe("true");
    expect(input.getAttribute("aria-activedescendant")).toBe(options[1]!.id);

    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSelect).toHaveBeenCalledTimes(1);
  });
});
