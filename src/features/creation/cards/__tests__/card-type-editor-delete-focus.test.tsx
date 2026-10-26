// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";
import type { CardType } from "@/types/creation";

/**
 * BD-199（BD-197 同族）：卡片类型行删除后随刷新整行卸载，握着焦点的删除钮
 * 被移除、焦点跌回 body（WCAG 2.4.3）。交接判据：原位置下一行优先，末尾退
 * 前一条；幸存行删除钮 disabled（内置/被引用）退列表容器；列表清空退编辑器
 * 标题；条目没走（失败/未刷新）不动焦点。
 */

const mocks = vi.hoisted(() => ({
  deleteCardType: vi.fn(async () => true),
  loadCardTypes: vi.fn(async () => {}),
  loadCards: vi.fn(async () => {}),
  runStructure: vi.fn(async () => true),
  updateCardType: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => mocks }));

function type(id: string, name: string, builtIn = false): CardType {
  return { id, name, kind: `k-${id}`, revision: 1, fields: [], builtIn } as unknown as CardType;
}

function Editor({ cardTypes }: { cardTypes: CardType[] }) {
  return (
    <CardTypeEditor
      projectId="p1"
      cardTypes={cardTypes}
      cards={[]}
      relationTypes={[]}
      onClose={() => undefined}
    />
  );
}

function clickDelete(name: string) {
  const btn = screen.getByLabelText(`删除${name}`);
  btn.focus(); // 模拟键盘用户：焦点已握在删除钮上
  fireEvent.click(btn); // 第一次点击 = 进入确认态
  fireEvent.click(screen.getByLabelText(`确认删除${name}`)); // 第二次 = 真删
  return btn;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.deleteCardType.mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("BD-199：卡片类型行卸载焦点交接", () => {
  it("删中间行：焦点交给原位置下一行", async () => {
    const [a, b, c] = [type("t-a", "甲"), type("t-b", "乙"), type("t-c", "丙")];
    const view = render(<Editor cardTypes={[a, b, c]} />);
    clickDelete("乙");
    await waitFor(() => expect(mocks.loadCardTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor cardTypes={[a, c]} />);
    expect(document.activeElement).toBe(screen.getByLabelText("删除丙"));
  });

  it("删末行：末尾退前一条", async () => {
    const [a, b, c] = [type("t-a", "甲"), type("t-b", "乙"), type("t-c", "丙")];
    const view = render(<Editor cardTypes={[a, b, c]} />);
    clickDelete("丙");
    await waitFor(() => expect(mocks.loadCardTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor cardTypes={[a, b]} />);
    expect(document.activeElement).toBe(screen.getByLabelText("删除乙"));
  });

  it("删空：列表 section 卸载，焦点退到编辑器标题", async () => {
    const a = type("t-a", "甲");
    const view = render(<Editor cardTypes={[a]} />);
    clickDelete("甲");
    await waitFor(() => expect(mocks.loadCardTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor cardTypes={[]} />);
    expect((document.activeElement as HTMLElement | null)?.className ?? "").toContain("cards-type-editor-title");
  });

  it("幸存行删除钮 disabled（内置）：退列表容器，焦点不落 body", async () => {
    const a = type("t-a", "乙");
    const b = type("t-b", "内置", true);
    const view = render(<Editor cardTypes={[a, b]} />);
    clickDelete("乙");
    await waitFor(() => expect(mocks.loadCardTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor cardTypes={[b]} />);
    expect((document.activeElement as HTMLElement | null)?.className ?? "").toContain("cards-type-list");
  });

  it("删除失败（条目没走）：不动焦点", async () => {
    mocks.deleteCardType.mockResolvedValue(false);
    const [a, b, c] = [type("t-a", "甲"), type("t-b", "乙"), type("t-c", "丙")];
    const view = render(<Editor cardTypes={[a, b, c]} />);
    clickDelete("乙");
    await waitFor(() => expect(mocks.deleteCardType).toHaveBeenCalled());
    // 父层照常刷新但条目还在——预约保持，焦点不搬（此时钮仍在确认态文案上）
    view.rerender(<Editor cardTypes={[a, b, c]} />);
    expect(document.activeElement).toBe(screen.getByLabelText("确认删除乙"));
  });
});
