// @vitest-environment jsdom
/**
 * BD-177：看板的每一列都有一个「+」按钮，旧名只有 title「在此类型下新建卡片」——
 * 一行按下来全是同一个名字，用户不知道会新建哪种卡片。把类型名带进名字。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { BoardView } from "@/features/creation/cards/BoardView";
import type { CardType } from "@/types/creation";

afterEach(() => cleanup());

function type(id: string, kind: string, name: string): CardType {
  return {
    id,
    builtIn: false,
    projectId: "p1",
    kind,
    name,
    fields: [],
    sortOrder: 0,
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

describe("BoardView 列内新建按钮", () => {
  it("按列带上类型名，点击仍回传对应 kind（BD-177）", () => {
    const onNewCard = vi.fn();
    render(
      <BoardView
        cardTypes={[type("t1", "character", "角色"), type("t2", "location", "地点")]}
        cards={[]}
        onSelectCard={vi.fn()}
        onMoveKind={vi.fn()}
        onNewCard={onNewCard}
      />
    );

    const character = screen.getByRole("button", { name: "在「角色」下新建卡片" });
    const location = screen.getByRole("button", { name: "在「地点」下新建卡片" });
    expect(character.getAttribute("title")).toBe("在此类型下新建卡片");

    fireEvent.click(location);
    expect(onNewCard).toHaveBeenCalledWith("location");
    fireEvent.click(character);
    expect(onNewCard).toHaveBeenCalledWith("character");
  });
});
