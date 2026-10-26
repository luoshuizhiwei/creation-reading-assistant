// @vitest-environment jsdom
/**
 * BD-204：设置搜索 combobox 的出口契约。家族里 FontPicker（选项点回交 trigger）
 * 与 CardReferencePicker / CommandPalette（BD-183 式卸载回交）都兑现了「选中后
 * 焦点归输入框」，SettingsSearch 指针路径漏：真实浏览器里点击把焦点落到 option
 * 按钮，choose 收 listbox 即卸载焦点所在节点，跌回 body。本文件显式把焦点放上
 * option 模拟浏览器点击落焦（jsdom 的 fireEvent.click 不带焦点，BD-199 同款注意
 * 事项），前提断言先验「焦点确实在将被卸载的 option 上」。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { SettingsSearch } from "@/features/settings/SettingsSearch";

afterEach(() => cleanup());

function typeQuery(value: string) {
  const input = screen.getByRole("combobox", { name: "搜索设置项" });
  fireEvent.change(input, { target: { value } });
  return input;
}

describe("BD-204：设置搜索选中后焦点归输入框", () => {
  it("指针路径：点击 option 前焦点在其上，choose 卸载 listbox 后焦点归输入框", () => {
    const onSelect = vi.fn();
    render(<SettingsSearch onSelect={onSelect} />);
    const input = typeQuery("字号");
    const listbox = screen.getByRole("listbox", { name: "设置搜索结果" });
    const first = within(listbox).getAllByRole("option")[0] as HTMLElement;
    first.focus();
    expect(document.activeElement).toBe(first); // 前提：浏览器点击会先落焦 option
    fireEvent.click(first);
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(document.activeElement).toBe(input);
  });

  it("键盘路径：Enter 选中后焦点仍停输入框，候选清空", () => {
    const onSelect = vi.fn();
    render(<SettingsSearch onSelect={onSelect} />);
    const input = typeQuery("字号");
    input.focus();
    fireEvent.keyDown(input, { key: "Enter" });
    expect(onSelect).toHaveBeenCalledTimes(1);
    expect(input.value).toBe("");
    expect(document.activeElement).toBe(input);
  });

  it("Escape 收 list 焦点不旁落；外点宿主树内按钮后焦点可离开搜索框", () => {
    const onSelect = vi.fn();
    render(<SettingsSearch onSelect={onSelect} />);
    const input = typeQuery("字号");
    input.focus();
    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(document.activeElement).toBe(input);
    // 外点关闭：pointerdown 落在别处并聚焦那里，搜索框不抢回（preventScroll
    // 回交会吞掉用户正在点的目标）。
    const external = document.createElement("button");
    document.body.appendChild(external);
    fireEvent.change(input, { target: { value: "" } }); // 同值 change 会被 React 的 value tracker 吞掉
    fireEvent.change(input, { target: { value: "字号" } }); // 重新开 list
    expect(screen.getByRole("listbox")).toBeTruthy();
    external.focus();
    fireEvent.pointerDown(external);
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(document.activeElement).toBe(external);
    external.remove();
  });
});
