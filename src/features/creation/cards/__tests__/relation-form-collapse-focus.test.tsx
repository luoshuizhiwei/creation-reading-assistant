// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CardRelationsManager } from "@/features/creation/cards/components/CardRelationsManager";
import type { CardRelation, CardSummary, RelationType } from "@/types/creation";

/**
 * BD-200（焦点管理同族）：建关系表单的成功/取消收起会移除握着焦点的表单内
 * 按钮，焦点跌回 body（WCAG 2.4.3）。收起后焦点必须交回常驻的「建立关系」
 * 开合钮；失败不收起则焦点原地不动（建立钮还活着）。
 */

const selectedCard = { id: "c1", title: "主卡", kind: "角色" } as CardSummary;
const otherCard = { id: "c2", title: "客卡", kind: "角色" } as CardSummary;
const relType = {
  id: "rt",
  name: "kin",
  forwardName: "盟友",
  reverseName: "部属",
  fromKinds: [],
  toKinds: []
} as unknown as RelationType;
const r1 = { id: "x-1", relationTypeId: "rt", forwardName: "盟友", fromCardId: "c1", toCardId: "c2" } as unknown as CardRelation;

const toggle = () => screen.getByRole("button", { name: "建立关系" });

function openFilledForm() {
  fireEvent.click(toggle());
  fireEvent.change(screen.getByLabelText("关系类型"), { target: { value: "rt" } });
  fireEvent.change(screen.getByLabelText("目标卡片"), { target: { value: "c2" } });
}

afterEach(() => cleanup());

describe("BD-200：建关系表单收起焦点回交开合钮", () => {
  it("建立成功收起：焦点回到「建立关系」钮，不落 body", async () => {
    const onCreate = vi.fn(async () => true);
    render(
      <CardRelationsManager
        selectedCard={selectedCard}
        projectCards={[selectedCard, otherCard]}
        relationTypes={[relType]}
        relations={{ outgoing: [r1], incoming: [] }}
        typeNameMap={new Map()}
        onCreateRelation={onCreate}
        onDeleteRelation={vi.fn(async () => true)}
      />
    );
    openFilledForm();
    const submit = screen.getByRole("button", { name: "建立" });
    submit.focus();
    fireEvent.click(submit);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(onCreate).toHaveBeenCalled();
    expect(document.activeElement).toBe(toggle());
  });

  it("取消收起：焦点同样回到开合钮", () => {
    render(
      <CardRelationsManager
        selectedCard={selectedCard}
        projectCards={[selectedCard, otherCard]}
        relationTypes={[relType]}
        relations={{ outgoing: [r1], incoming: [] }}
        typeNameMap={new Map()}
        onCreateRelation={vi.fn(async () => true)}
        onDeleteRelation={vi.fn(async () => true)}
      />
    );
    openFilledForm();
    const cancel = screen.getByRole("button", { name: "取消" });
    cancel.focus();
    fireEvent.click(cancel);
    expect(document.activeElement).toBe(toggle());
  });

  it("建立失败不收起：焦点原地不动（建立钮还在表单里）", async () => {
    const onCreate = vi.fn(async () => false);
    render(
      <CardRelationsManager
        selectedCard={selectedCard}
        projectCards={[selectedCard, otherCard]}
        relationTypes={[relType]}
        relations={{ outgoing: [r1], incoming: [] }}
        typeNameMap={new Map()}
        onCreateRelation={onCreate}
        onDeleteRelation={vi.fn(async () => true)}
      />
    );
    openFilledForm();
    const submit = screen.getByRole("button", { name: "建立" });
    submit.focus();
    fireEvent.click(submit);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.activeElement).toBe(screen.getByRole("button", { name: "建立" }));
  });
});
