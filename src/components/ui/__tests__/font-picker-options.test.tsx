// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { FontPicker } from "@/components/ui/FontPicker";

vi.mock("@/features/settings/reader/font-import", () => ({
  useInstalledFonts: () => ({ fonts: ["思源宋体.ttf", "霞鹜文楷.otf"], refresh: vi.fn(async () => {}) }),
  importFontFile: vi.fn(async () => null)
}));

afterEach(() => cleanup());

// BD-166：选项曾经是「li[role=option] 里套 button」——option 是交互角色，里面再放可聚焦按钮
// 就是嵌套交互；读屏器把焦点停在按钮上，aria-selected 挂在不可聚焦的 li 上永远念不出来。
describe("FontPicker 列表语义与键盘焦点", () => {
  it("option 落在按钮自身，li 只是 presentation，没有嵌套交互", () => {
    render(<FontPicker value="" onChange={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: /系统默认/ }));

    const options = screen.getAllByRole("option");
    expect(options).toHaveLength(4); // 系统默认 + 两款字体 + 导入字体…
    for (const option of options) {
      expect(option.tagName).toBe("BUTTON");
      expect(option.querySelector("button")).toBeNull();
      expect(option.getAttribute("aria-selected")).toBeTruthy();
    }
    expect(options[0].getAttribute("aria-selected")).toBe("true");
  });

  it("方向键把焦点带到高亮项，回车提交的就是它", () => {
    const onChange = vi.fn();
    render(<FontPicker value="" onChange={onChange} />);
    const trigger = screen.getByRole("button", { name: /系统默认/ });

    fireEvent.click(trigger);
    fireEvent.keyDown(trigger, { key: "ArrowDown" });

    const options = screen.getAllByRole("option");
    expect(document.activeElement).toBe(options[1]);
    expect(options[1].getAttribute("aria-selected")).toBe("false");

    fireEvent.keyDown(document.activeElement as HTMLElement, { key: "Enter" });
    expect(onChange).toHaveBeenCalledWith("思源宋体");
    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(document.activeElement).toBe(trigger);
  });

  it("Esc 收起列表并把焦点还给触发钮", () => {
    render(<FontPicker value="" onChange={vi.fn()} />);
    const trigger = screen.getByRole("button", { name: /系统默认/ });
    fireEvent.click(trigger);
    fireEvent.keyDown(screen.getAllByRole("option")[0], { key: "Escape" });

    expect(screen.queryAllByRole("option")).toHaveLength(0);
    expect(document.activeElement).toBe(trigger);
  });
});
