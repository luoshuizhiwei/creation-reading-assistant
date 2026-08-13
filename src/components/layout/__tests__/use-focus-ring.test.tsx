// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { RingButton } from "@/components/interaction";

afterEach(() => cleanup());

describe("RingButton 键盘焦点环", () => {
  it("点击嵌套 span/svg 后代节点不显示键盘焦点环", () => {
    render(
      <RingButton>
        <span>带图标的文字</span>
        <svg data-testid="icon" />
      </RingButton>
    );
    const button = screen.getByRole("button");
    const span = button.querySelector("span");
    expect(span).not.toBeNull();

    fireEvent.pointerDown(span as Element);
    fireEvent.focus(button);
    expect(button.style.outline).toBe("");
  });

  it("直接点击按钮自身也不显示键盘焦点环", () => {
    render(<RingButton>按钮</RingButton>);
    const button = screen.getByRole("button");
    fireEvent.pointerDown(button);
    fireEvent.focus(button);
    expect(button.style.outline).toBe("");
  });

  it("Tab 键盘聚焦（无指针操作）显示焦点环", () => {
    render(<RingButton>按钮</RingButton>);
    const button = screen.getByRole("button");
    fireEvent.focus(button);
    expect(button.style.outline).toContain("2px solid var(--copper)");
  });

  it("blur 复位后指针状态不卡住：再次键盘聚焦仍显示焦点环", () => {
    render(<RingButton>按钮</RingButton>);
    const button = screen.getByRole("button");

    fireEvent.pointerDown(button);
    fireEvent.focus(button);
    expect(button.style.outline).toBe("");

    fireEvent.blur(button);
    fireEvent.focus(button);
    expect(button.style.outline).toContain("2px solid var(--copper)");
  });
});
