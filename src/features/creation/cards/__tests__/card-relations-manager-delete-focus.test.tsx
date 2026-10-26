// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardRelationsManager } from "@/features/creation/cards/components/CardRelationsManager";
import type { CardRelation, CardSummary, RelationType } from "@/types/creation";

/**
 * BD-199（BD-197 同族）：卡详情关系行删除后随父层刷新整行卸载，握着焦点的
 * 删除钮被移除、焦点跌回 body。行序快照 = 出边在前、入边在后（与渲染序一致）；
 * 删空时 ul 换空态段落，退路是空态段（tabIndex=-1）；条目没走不动焦点。
 */

const selectedCard = { id: "c1", title: "主卡", kind: "角色" } as CardSummary;
const otherCard = { id: "c2", title: "客卡", kind: "角色" } as CardSummary;
const thirdCard = { id: "c3", title: "别卡", kind: "角色" } as CardSummary;
const relType = { id: "rt", name: "kin", forwardName: "盟友", reverseName: "部属" } as RelationType;

function relation(id: string, fromCardId: string, toCardId: string): CardRelation {
  return { id, relationTypeId: "rt", forwardName: "盟友", fromCardId, toCardId } as unknown as CardRelation;
}

type Relations = { outgoing: CardRelation[]; incoming: CardRelation[] } | undefined;

function Manager({ relations, onDeleteRelation }: { relations: Relations; onDeleteRelation: (id: string) => Promise<boolean> }) {
  return (
    <CardRelationsManager
      selectedCard={selectedCard}
      projectCards={[selectedCard, otherCard, thirdCard]}
      relationTypes={[relType]}
      relations={relations}
      typeNameMap={new Map([["角色", "角色"]])}
      onCreateRelation={vi.fn(async () => true)}
      onDeleteRelation={onDeleteRelation}
    />
  );
}

const deleteButtons = () => screen.getAllByTitle("删除关系");

function clickDelete(index: number) {
  const btn = deleteButtons()[index];
  btn.focus();
  fireEvent.click(btn); // 进入确认态
  fireEvent.click(deleteButtons()[index]); // 确认删除
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => cleanup());

describe("BD-199：关系行卸载焦点交接", () => {
  it("删出边首行：焦点交给原位置下一行", async () => {
    const onDelete = vi.fn(async () => true);
    const r1 = relation("x-1", "c1", "c2");
    const r2 = relation("x-2", "c1", "c3");
    const view = render(<Manager relations={{ outgoing: [r1, r2], incoming: [] }} onDeleteRelation={onDelete} />);
    clickDelete(0);
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith("x-1"));
    view.rerender(<Manager relations={{ outgoing: [r2], incoming: [] }} onDeleteRelation={onDelete} />);
    expect(document.activeElement).toBe(deleteButtons()[0]);
    expect(document.activeElement?.closest("li")?.textContent).toContain("别卡"); // 交接的是 r2 行
  });

  it("行序跨出边/入边拼接：删出边末行交接给入边首行", async () => {
    const onDelete = vi.fn(async () => true);
    const r1 = relation("x-1", "c1", "c2");
    const r2 = relation("x-2", "c1", "c3");
    const r3 = relation("x-3", "c3", "c1");
    const view = render(<Manager relations={{ outgoing: [r1, r2], incoming: [r3] }} onDeleteRelation={onDelete} />);
    clickDelete(1);
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith("x-2"));
    view.rerender(<Manager relations={{ outgoing: [r1], incoming: [r3] }} onDeleteRelation={onDelete} />);
    expect(document.activeElement).toBe(deleteButtons()[1]); // survivors[1] = x-3（入边首行）
    expect(document.activeElement?.closest("li")?.textContent).toContain("别卡");
  });

  it("删空：ul 换成空态段落，焦点退到空态段", async () => {
    const onDelete = vi.fn(async () => true);
    const r1 = relation("x-1", "c1", "c2");
    const view = render(<Manager relations={{ outgoing: [r1], incoming: [] }} onDeleteRelation={onDelete} />);
    clickDelete(0);
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith("x-1"));
    view.rerender(<Manager relations={{ outgoing: [], incoming: [] }} onDeleteRelation={onDelete} />);
    expect(document.activeElement?.textContent).toBe("暂无关系。");
  });

  it("删除失败（条目没走）：不动焦点", async () => {
    const onDelete = vi.fn(async () => false);
    const r1 = relation("x-1", "c1", "c2");
    const r2 = relation("x-2", "c1", "c3");
    const view = render(<Manager relations={{ outgoing: [r1, r2], incoming: [] }} onDeleteRelation={onDelete} />);
    clickDelete(0);
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith("x-1"));
    view.rerender(<Manager relations={{ outgoing: [r1, r2], incoming: [] }} onDeleteRelation={onDelete} />);
    expect(document.activeElement).toBe(deleteButtons()[0]);
    expect(document.activeElement?.textContent).toContain("确认删除");
  });
});
