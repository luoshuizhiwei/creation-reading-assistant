// @vitest-environment jsdom
/**
 * BD-244（WCAG 3.3.1 错误识别）：卡片类型编辑器的校验必须「点名出错项」，
 * 并且把错误程序化挂到那一格控件上（aria-invalid + aria-describedby → 错误元素）。
 *
 * 运行时实测（/tmp/bd244-probe.log，设定卡→卡片类型，两行字段）——旧写法：
 *   A1 第 2 行名称留空 → 「每个字段都需要填写名称（label）。」
 *   A2 第 1 行名称留空 → 「每个字段都需要填写名称（label）。」  ← 一字不差
 *   [换位测试] 失败项从第 2 行换到第 1 行，文案没变 → 文案定位不到出错项
 *   两档都是「带 aria-invalid=true 的控件：0 ｜ 有 aria-describedby 且目标存在的：0」，
 *   全部 6 种失败态（A0～A5）无一例外。
 * 本文件把修好的三件事钉住：① 文案带「第 N 个字段」（N == 行首那个可见的 #N 编号）；
 * ② 换位测试必须换字；③ 错误只挂在真正出错的那一格上。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";
import { useUIStore } from "@/stores/ui-store";
import type { CardFieldKind } from "@/types/creation";

const actions = vi.hoisted(() => ({
  runStructure: vi.fn(),
  updateCardType: vi.fn(),
  deleteCardType: vi.fn(),
  loadCardTypes: vi.fn(),
  loadCards: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    runStructure: actions.runStructure,
    updateCardType: actions.updateCardType,
    deleteCardType: actions.deleteCardType,
    loadCardTypes: actions.loadCardTypes,
    loadCards: actions.loadCards
  })
}));

vi.mock("@/services/creation-service", () => {
  const noop = vi.fn();
  return new Proxy({} as Record<string, typeof noop>, {
    get: (target, prop: string) => {
      if (!target[prop]) target[prop] = noop;
      return target[prop];
    }
  });
});

const ERROR_ID = "card-type-form-error";

function byName(name: string) {
  return screen.getByRole("textbox", { name }) as HTMLInputElement;
}

function addRow(index: number, config: { label: string; key: string; kind?: CardFieldKind; options?: string }) {
  fireEvent.click(screen.getByText("添加字段"));
  fireEvent.change(byName(`字段 ${index + 1} 名称`), { target: { value: config.label } });
  fireEvent.change(byName(`字段 ${index + 1} 稳定 key`), { target: { value: config.key } });
  if (config.kind) fireEvent.change(screen.getByRole("combobox", { name: `字段 ${index + 1} 类型` }), { target: { value: config.kind } });
  if (config.options !== undefined) {
    fireEvent.change(screen.getByRole("textbox", { name: `字段 ${index + 1} 选项` }), { target: { value: config.options } });
  }
}

function submitType() {
  fireEvent.click(screen.getByText("创建类型"));
}

function alertText() {
  return screen.getByRole("alert").textContent ?? "";
}

beforeEach(() => {
  actions.runStructure.mockReset();
  actions.runStructure.mockResolvedValue(true);
  actions.loadCardTypes.mockResolvedValue(undefined);
  actions.loadCards.mockResolvedValue(undefined);
  useUIStore.setState({ toasts: [] });
});

afterEach(cleanup);

describe("BD-244 卡片类型编辑器：出错项要点名（3.3.1）", () => {
  it("换位测试：第 1 行留空与第 2 行留空，文案必须不同、各自点出自己的行号", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    addRow(1, { label: "字段乙", key: "k2" });

    fireEvent.change(byName("字段 2 名称"), { target: { value: "" } });
    submitType();
    await waitFor(() => expect(alertText()).toContain("第 2 个字段"));
    const second = alertText();

    fireEvent.change(byName("字段 2 名称"), { target: { value: "字段乙" } });
    fireEvent.change(byName("字段 1 名称"), { target: { value: "" } });
    submitType();
    await waitFor(() => expect(alertText()).toContain("第 1 个字段"));
    const first = alertText();

    expect(first).not.toBe(second);
    expect(actions.runStructure).not.toHaveBeenCalled();
  });

  it("名称留空：错误只挂在这一格的名称输入框上", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    addRow(1, { label: "字段乙", key: "k2" });
    fireEvent.change(byName("字段 2 名称"), { target: { value: "" } });
    submitType();

    const alert = await waitFor(() => screen.getByRole("alert"));
    expect(alert.id).toBe(ERROR_ID);
    expect(byName("字段 2 名称").getAttribute("aria-invalid")).toBe("true");
    expect(byName("字段 2 名称").getAttribute("aria-describedby")).toBe(ERROR_ID);
    // 没出错的那格不能被牵连。
    expect(byName("字段 1 名称").getAttribute("aria-invalid")).toBeNull();
    expect(byName("字段 2 稳定 key").getAttribute("aria-invalid")).toBeNull();
    expect(byName("类型名称").getAttribute("aria-invalid")).toBeNull();
  });

  it("key 不合法：文案带行号，错误挂在该行 key 框", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    addRow(1, { label: "字段乙", key: "2bad" });
    submitType();

    await waitFor(() => expect(alertText()).toContain("第 2 个字段"));
    expect(alertText()).toContain("字段 key");
    expect(byName("字段 2 稳定 key").getAttribute("aria-invalid")).toBe("true");
    expect(byName("字段 2 稳定 key").getAttribute("aria-describedby")).toBe(ERROR_ID);
    expect(byName("字段 2 名称").getAttribute("aria-invalid")).toBeNull();
  });

  it("key 重复：错误挂在重复的那一行（第 2 行），不是第 1 行", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "dup" });
    addRow(1, { label: "字段乙", key: "dup" });
    submitType();

    await waitFor(() => expect(alertText()).toContain("第 2 个字段"));
    expect(byName("字段 2 稳定 key").getAttribute("aria-invalid")).toBe("true");
    expect(byName("字段 1 稳定 key").getAttribute("aria-invalid")).toBeNull();
  });

  it("单选没选项：错误挂在该行的选项文本域", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    addRow(1, { label: "字段乙", key: "k2", kind: "select", options: "" });
    submitType();

    await waitFor(() => expect(alertText()).toContain("至少需要一个有效选项"));
    expect(alertText()).toContain("第 2 个字段");
    const optionsBox = screen.getByRole("textbox", { name: "字段 2 选项" });
    expect(optionsBox.getAttribute("aria-invalid")).toBe("true");
    expect(optionsBox.getAttribute("aria-describedby")).toBe(ERROR_ID);
  });

  it("默认值不合法：错误挂在该行的默认值框", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    // 用单选字段：number 那格的 DOM 会把非数字输入直接吞掉（jsdom 同浏览器行为），
    // 换成「默认值必须是选项之一」这条同样走 parseDefault 的路径。
    addRow(1, { label: "字段乙", key: "k2", kind: "select", options: "A\nB" });
    const defaultBox = document.getElementById("cte-field-default-1") as HTMLInputElement;
    fireEvent.change(defaultBox, { target: { value: "C" } });
    submitType();

    await waitFor(() => expect(alertText()).toContain("默认值必须是选项之一"));
    expect(alertText()).toContain("字段乙");
    expect(defaultBox.getAttribute("aria-invalid")).toBe("true");
    expect(defaultBox.getAttribute("aria-describedby")).toBe(ERROR_ID);
  });

  it("类型名称留空：错误挂在类型名称框", async () => {
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    submitType();
    await waitFor(() => expect(alertText()).toContain("请填写类型名称"));
    expect(byName("类型名称").getAttribute("aria-invalid")).toBe("true");
    expect(byName("类型名称").getAttribute("aria-describedby")).toBe(ERROR_ID);
  });

  it("保存层面的失败（不是某一格的问题）不牵连任何控件", async () => {
    actions.runStructure.mockResolvedValue(false);
    render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(byName("类型名称"), { target: { value: "探针类型" } });
    addRow(0, { label: "字段甲", key: "k1" });
    submitType();

    await waitFor(() => expect(alertText()).toContain("创建卡片类型失败"));
    const flagged = document.querySelectorAll('[aria-invalid="true"]');
    expect(flagged.length).toBe(0);
  });
});
