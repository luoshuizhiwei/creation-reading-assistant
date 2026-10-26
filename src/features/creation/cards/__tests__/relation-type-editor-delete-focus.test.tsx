// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { RelationTypeEditor } from "@/features/creation/cards/RelationTypeEditor";
import type { CardType, RelationType } from "@/types/creation";

/**
 * BD-199（BD-197/CardTypeEditor 同族）：关系类型行删除后随刷新整行卸载，
 * 握着焦点的删除钮被移除、焦点跌回 body（WCAG 2.4.3）。同一条交接判据。
 */

const mocks = vi.hoisted(() => ({
  deleteRelationType: vi.fn(async () => true),
  loadRelationTypes: vi.fn(async () => {}),
  loadCards: vi.fn(async () => {}),
  runStructure: vi.fn(async () => true),
  updateRelationType: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => mocks }));

function relType(id: string, forward: string, builtIn = false): RelationType {
  return { id, name: `n-${id}`, forwardName: forward, reverseName: `${forward}反`, fromKinds: [], toKinds: [], revision: 1, builtIn } as unknown as RelationType;
}

const cardTypes: CardType[] = [];

function Editor({ relationTypes }: { relationTypes: RelationType[] }) {
  return (
    <RelationTypeEditor
      projectId="p1"
      cardTypes={cardTypes}
      relationTypes={relationTypes}
      relations={[]}
      onClose={() => undefined}
    />
  );
}

function clickDelete(forward: string) {
  const btn = screen.getByLabelText(`删除${forward}`);
  btn.focus();
  fireEvent.click(btn);
  fireEvent.click(screen.getByLabelText(`确认删除${forward}`));
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.deleteRelationType.mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("BD-199：关系类型行卸载焦点交接", () => {
  it("删中间行：焦点交给原位置下一行", async () => {
    const [a, b, c] = [relType("r-a", "师徒"), relType("r-b", "盟友"), relType("r-c", "仇敌")];
    const view = render(<Editor relationTypes={[a, b, c]} />);
    clickDelete("盟友");
    await waitFor(() => expect(mocks.loadRelationTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor relationTypes={[a, c]} />);
    expect(document.activeElement).toBe(screen.getByLabelText("删除仇敌"));
  });

  it("删空：列表 section 卸载，焦点退到编辑器标题", async () => {
    const a = relType("r-a", "师徒");
    const view = render(<Editor relationTypes={[a]} />);
    clickDelete("师徒");
    await waitFor(() => expect(mocks.loadRelationTypes).toHaveBeenCalledWith("p1"));
    view.rerender(<Editor relationTypes={[]} />);
    expect((document.activeElement as HTMLElement | null)?.className ?? "").toContain("cards-type-editor-title");
  });

  it("删除失败（条目没走）：不动焦点", async () => {
    mocks.deleteRelationType.mockResolvedValue(false);
    const [a, b] = [relType("r-a", "师徒"), relType("r-b", "盟友")];
    const view = render(<Editor relationTypes={[a, b]} />);
    clickDelete("盟友");
    await waitFor(() => expect(mocks.deleteRelationType).toHaveBeenCalled());
    view.rerender(<Editor relationTypes={[a, b]} />);
    expect(document.activeElement).toBe(screen.getByLabelText("确认删除盟友"));
  });
});
