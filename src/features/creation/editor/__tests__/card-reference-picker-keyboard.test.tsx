// @vitest-environment jsdom
import React, { useState } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardReferencePicker } from "../card-reference-picker";
import type { CardSummary } from "@/types/creation";

const card: CardSummary = {
  id: "card-a",
  projectId: "p1",
  linkedProjectIds: ["p1"],
  usageCount: 1,
  kind: "character",
  title: "林晚",
  aliases: [],
  fields: {},
  tags: [],
  createdAt: "",
  updatedAt: "",
  revision: 1
};

const secondCard: CardSummary = { ...card, id: "card-b", title: "顾淮" };

function Harness() {
  const [open, setOpen] = useState(false);
  return (
    <div>
      <button type="button" onClick={() => setOpen(true)}>
        打开引用
      </button>
      {open && (
        <CardReferencePicker cards={[card, secondCard]} onSelect={() => {}} onClose={() => setOpen(false)} />
      )}
    </div>
  );
}

afterEach(cleanup);

describe("卡片引用选择器 · 键盘", () => {
  // BD-156：Esc 此前只挂在输入框上；焦点移到关闭钮或卡片行后就关不掉了，
  // 关闭后焦点也丢在 body 上，键盘用户得从头再 Tab 一遍。
  it("焦点在卡片行时 Esc 也能关闭，并把焦点还给触发按钮", () => {
    render(<Harness />);
    const trigger = screen.getByRole("button", { name: "打开引用" });
    trigger.focus();
    fireEvent.click(trigger);

    const row = screen.getByRole("option", { name: /林晚/ });
    row.focus();
    fireEvent.keyDown(row, { key: "Escape" });

    expect(screen.queryByRole("dialog")).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  // BD-162：输入框没有组合框角色，方向键只在视觉上换高亮，
  // 读屏器不知道当前候选是哪一张，而 Enter 会直接引用它。
  it("候选卡片以 option 暴露，方向键同步 aria-activedescendant", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "打开引用" }));

    const input = screen.getByRole("combobox", { name: "搜索卡片（标题或别名）" });
    const options = screen.getAllByRole("option");
    expect(options).toHaveLength(2);
    expect(options[0]!.getAttribute("aria-selected")).toBe("true");
    expect(input.getAttribute("aria-activedescendant")).toBe(options[0]!.id);

    fireEvent.keyDown(input, { key: "ArrowDown" });
    expect(options[0]!.getAttribute("aria-selected")).toBe("false");
    expect(options[1]!.getAttribute("aria-selected")).toBe("true");
    expect(input.getAttribute("aria-activedescendant")).toBe(options[1]!.id);
  });

  it("输入法组合输入中的 Esc 不当作关闭", () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole("button", { name: "打开引用" }));

    const input = screen.getByRole("combobox", { name: "搜索卡片（标题或别名）" });
    fireEvent.keyDown(input, { key: "Escape", keyCode: 229 });

    expect(screen.getByRole("dialog")).toBeTruthy();
  });
});
