// @vitest-environment jsdom
/**
 * BD-139：卡片编辑表单的可见标签必须与控件关联。
 *
 * 这些字段的 <label class="cards-field-label"> 一直是控件前面的独立节点，既没有
 * htmlFor，控件也没有被包进 label——视觉上有字，程序上零关联，读屏器只报「编辑框」。
 * 这里把「可见文案 = 可访问名」钉住，含动态字段（schema.label 为名）。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { CardEditorForm } from "@/features/creation/cards/components/CardEditorForm";
import type { CardFieldSchema, CardSummary, CardType } from "@/types/creation";

afterEach(() => cleanup());

const fields: CardFieldSchema[] = [
  { key: "alias", label: "道号", kind: "text", required: false },
  { key: "age", label: "年岁", kind: "number", required: false },
  { key: "since", label: "入门日", kind: "date", required: false },
  { key: "active", label: "在世", kind: "boolean", required: false },
  { key: "tier", label: "境界", kind: "select", required: false, options: ["炼气", "筑基"] },
  { key: "note", label: "小传", kind: "multiline", required: false }
];

const cardType: CardType = {
  id: "t1",
  projectId: "p1",
  kind: "cultivator",
  name: "修士",
  fields,
  sortOrder: 0,
  createdAt: "",
  updatedAt: "",
  revision: 1
};

const draft: CardSummary = {
  id: "new",
  projectId: "p1",
  kind: "cultivator",
  title: "",
  aliases: [],
  fields: {},
  tags: [],
  createdAt: "",
  updatedAt: "",
  revision: 1
};

function renderForm() {
  render(
    <CardEditorForm
      draft={draft}
      cardTypes={[cardType]}
      projectCards={[]}
      onChangeDraft={vi.fn()}
      onSave={vi.fn()}
      onCancel={vi.fn()}
    />
  );
}

describe("CardEditorForm 可访问名", () => {
  it("类型/名称/别名/标签四个基础控件都有可访问名", () => {
    renderForm();
    expect(screen.getByRole("combobox", { name: "类型" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "名称" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "别名" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "标签" })).toBeTruthy();
  });

  it("动态字段以 schema 的可见标签作可访问名（含数字/日期/开关/下拉/多行）", () => {
    renderForm();
    expect(screen.getByRole("textbox", { name: "道号" })).toBeTruthy();
    expect(screen.getByRole("spinbutton", { name: "年岁" })).toBeTruthy();
    expect(screen.getByRole("textbox", { name: "小传" })).toBeTruthy();
    expect(screen.getByRole("combobox", { name: "境界" })).toBeTruthy();
    expect(screen.getByRole("checkbox", { name: "在世" })).toBeTruthy();
    // jsdom 里 type=date 没有稳定的 role，按可访问名反查。
    expect(screen.getByLabelText("入门日")).toBeTruthy();
  });
});
