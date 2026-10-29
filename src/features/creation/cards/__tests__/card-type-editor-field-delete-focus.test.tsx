// @vitest-environment jsdom
/**
 * BD-219（BD-179/199 同族）：卡片类型编辑器删除字段后焦点交接。
 *
 * 删除字段钮随行卸载，焦点跌回 body（WCAG 2.4.3）。交接判据：原位置下一行优先，
 * 末尾退前一行；字段删空时退「添加字段」钮。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";

const mocks = vi.hoisted(() => ({
  deleteCardType: vi.fn(async () => true),
  loadCardTypes: vi.fn(async () => {}),
  loadCards: vi.fn(async () => {}),
  runStructure: vi.fn(async () => true),
  updateCardType: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => mocks }));

function renderEditor() {
  return render(
    <CardTypeEditor projectId="p1" cardTypes={[]} cards={[]} relationTypes={[]} onClose={() => undefined} />
  );
}

/** Dialog 打开帧的 rAF 焦点可能迟到，先冲刷两帧再布防。 */
const flushFrames = () =>
  new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));

function addField() {
  fireEvent.click(screen.getByRole("button", { name: /添加字段/ }));
}

function deleteField(index: number) {
  const button = screen.getAllByTitle("删除字段")[index];
  button.focus();
  fireEvent.click(button);
}

beforeEach(() => vi.clearAllMocks());
afterEach(() => cleanup());

describe("BD-219：字段行卸载焦点交接", () => {
  it("删中间字段：焦点交给原位置下一字段的删除钮", async () => {
    renderEditor();
    await flushFrames();
    addField();
    addField();
    addField();
    deleteField(1);
    const buttons = screen.getAllByTitle("删除字段");
    expect(buttons).toHaveLength(2);
    expect(document.activeElement).toBe(buttons[1]);
  });

  it("删末尾字段：末尾退前一字段", async () => {
    renderEditor();
    await flushFrames();
    addField();
    addField();
    deleteField(1);
    expect(document.activeElement).toBe(screen.getAllByTitle("删除字段")[0]);
  });

  it("字段删空：焦点退到「添加字段」钮", async () => {
    renderEditor();
    await flushFrames();
    addField();
    deleteField(0);
    const addButton = screen.getByRole("button", { name: /添加字段/ });
    expect(document.activeElement).toBe(addButton);
  });
});
