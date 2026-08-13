// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardFieldKind } from "@/types/creation";

vi.mock("@/services/creation-service", () => ({
  inboxList: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  cardsList: vi.fn(),
  runStructure: vi.fn(),
  listProjects: vi.fn(),
  readProjectHome: vi.fn(),
  readProjectNavigation: vi.fn(),
  readProjectOutline: vi.fn(),
  readSceneBody: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  search: vi.fn(),
  replacePreview: vi.fn(),
  replaceApply: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  sessionReport: vi.fn(),
  sessionDelete: vi.fn(),
  proofQuery: vi.fn(),
  trashList: vi.fn(),
  snapshotList: vi.fn(),
  cardRead: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  exportDraft: vi.fn(),
  importDraftPreview: vi.fn(),
  exportProjectBundle: vi.fn(),
  importProjectBundle: vi.fn(),
  annotationList: vi.fn(),
  annotationCreate: vi.fn(),
  annotationUpdate: vi.fn(),
  annotationDelete: vi.fn(),
  resourceList: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  projectExport: vi.fn(),
  migrationStatus: vi.fn(),
  migrationRun: vi.fn(),
  createProject: vi.fn()
}));

const ALL_KINDS: CardFieldKind[] = [
  "text",
  "multiline",
  "number",
  "date",
  "select",
  "multiSelect",
  "boolean",
  "cardRef",
  "url",
  "attachment"
];

function rowControls(container: HTMLElement, rowIndex: number) {
  const rows = container.querySelectorAll(".cte-field-row");
  const row = rows[rowIndex] as HTMLElement;
  const inputs = Array.from(row.querySelectorAll("input")) as HTMLInputElement[];
  const selects = Array.from(row.querySelectorAll("select")) as HTMLSelectElement[];
  const textareas = Array.from(row.querySelectorAll("textarea")) as HTMLTextAreaElement[];
  return { row, inputs, selects, textareas };
}

function addFieldAndConfigure(
  container: HTMLElement,
  index: number,
  config: { label: string; key: string; kind: CardFieldKind; options?: string }
): void {
  fireEvent.click(screen.getByText("添加字段"));
  const { inputs, selects } = rowControls(container, index);
  fireEvent.change(inputs[0], { target: { value: config.label } });
  fireEvent.change(inputs[1], { target: { value: config.key } });
  fireEvent.change(selects[0], { target: { value: config.kind } });
  // 选项 textarea 只有把类型切换成 select/multiSelect 之后才出现，需在改类型后重新查询。
  if (config.options !== undefined) {
    const row = container.querySelectorAll(".cte-field-row")[index] as HTMLElement;
    const textareas = Array.from(row.querySelectorAll("textarea")) as HTMLTextAreaElement[];
    fireEvent.change(textareas[0], { target: { value: config.options } });
  }
}

beforeEach(() => {
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.runStructure).mockResolvedValue(true);
  vi.mocked(creationService.cardTypesList).mockResolvedValue([]);
  useUIStore.setState({ toasts: [] });
});

afterEach(() => cleanup());

describe("CardTypeEditor 自定义卡片类型", () => {
  it("可创建包含全部 10 种字段声明类型的自定义 CardType", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);

    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "测试类型" } });
    ALL_KINDS.forEach((kind, index) => {
      addFieldAndConfigure(container, index, {
        label: `字段${index}`,
        key: `key_${kind}`,
        kind,
        options: kind === "select" || kind === "multiSelect" ? "A\nB" : undefined
      });
    });

    fireEvent.click(screen.getByText("创建类型"));

    await waitFor(() =>
      expect(creationService.runStructure).toHaveBeenCalledTimes(1)
    );
    const command = vi.mocked(creationService.runStructure).mock.calls[0][0] as Record<string, unknown>;
    expect(command.type).toBe("cardType.create");
    expect(command.name).toBe("测试类型");
    const fields = command.fields as Array<{ key: string; kind: string; options?: string[] }>;
    expect(fields).toHaveLength(10);
    const kinds = fields.map((field) => field.kind).sort();
    expect(kinds).toEqual([...ALL_KINDS].sort());
    // 选项字段带 options，非选项字段不带。
    const selectField = fields.find((field) => field.kind === "select");
    expect(selectField?.options).toEqual(["A", "B"]);
    const textField = fields.find((field) => field.kind === "text");
    expect(textField?.options).toBeUndefined();
  });

  it("同一类型内重复 key 被拦截且不调用命令", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);

    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "重复类型" } });
    addFieldAndConfigure(container, 0, { label: "字段甲", key: "dup", kind: "text" });
    addFieldAndConfigure(container, 1, { label: "字段乙", key: "dup", kind: "number" });

    fireEvent.click(screen.getByText("创建类型"));

    await waitFor(() => expect(screen.getByText(/重复/)).toBeDefined());
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("空名称或空 key 不提交", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);

    // 只填字段、不填类型名称
    addFieldAndConfigure(container, 0, { label: "字段甲", key: "k1", kind: "text" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/请填写类型名称/)).toBeDefined());
    expect(creationService.runStructure).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "有名称" } });
    // 把字段 key 清空
    const { inputs } = rowControls(container, 0);
    fireEvent.change(inputs[1], { target: { value: "" } });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/字段 key/)).toBeDefined());
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("选项字段没有有效选项不提交", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "选项类型" } });
    addFieldAndConfigure(container, 0, { label: "单选字段", key: "sel", kind: "select", options: "" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/至少需要一个有效选项/)).toBeDefined());
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("创建成功后回调 onClose 并刷新卡片类型列表", async () => {
    const onClose = vi.fn();
    const { container } = render(<CardTypeEditor projectId="p1" onClose={onClose} />);
    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "关闭测试" } });
    addFieldAndConfigure(container, 0, { label: "字段甲", key: "k1", kind: "text" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(creationService.cardTypesList).toHaveBeenCalledWith("p1");
  });
});
