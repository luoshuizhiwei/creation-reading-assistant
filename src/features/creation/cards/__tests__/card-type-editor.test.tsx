// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardTypeEditor } from "@/features/creation/cards/CardTypeEditor";
import { useUIStore } from "@/stores/ui-store";
import type { CardFieldKind, CardSummary, CardType, RelationType } from "@/types/creation";

// Agent 1 提供的公共 Hook；在本测试中直接桩接，避免绕过 Hook 直接调用 IPC。
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

// 保留 creationService 桩以免 store 引入真实主进程模块；编辑器经 Hook 调用，不会触达真实服务。
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
  actions.runStructure.mockReset();
  actions.runStructure.mockResolvedValue(true);
  actions.updateCardType.mockReset();
  actions.updateCardType.mockResolvedValue(true);
  actions.deleteCardType.mockReset();
  actions.deleteCardType.mockResolvedValue(true);
  actions.loadCardTypes.mockResolvedValue(undefined);
  actions.loadCards.mockResolvedValue(undefined);
  useUIStore.setState({ toasts: [] });
});

afterEach(() => cleanup());

describe("CardTypeEditor 自定义卡片类型", () => {
  it("编辑自定义类型时锁定 kind 与既有字段 key，并携带 baseRevision 提交", async () => {
    const customType: CardType = {
      id: "type-magic",
      projectId: "p1",
      builtIn: false,
      kind: "custom_magic",
      name: "功法",
      fields: [{ key: "grade", label: "品阶", kind: "text" }],
      sortOrder: 8,
      createdAt: "",
      updatedAt: "",
      revision: 7
    };
    const { container } = render(
      <CardTypeEditor
        projectId="p1"
        cardTypes={[customType]}
        cards={[] as CardSummary[]}
        relationTypes={[] as RelationType[]}
        onClose={() => {}}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "编辑功法" }));
    expect(screen.getByDisplayValue("custom_magic").disabled).toBe(true);
    expect(screen.getByDisplayValue("grade").disabled).toBe(true);
    fireEvent.change(screen.getByDisplayValue("功法"), { target: { value: "绝学" } });
    fireEvent.change(screen.getByDisplayValue("品阶"), { target: { value: "境界" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修改" }));

    await waitFor(() => expect(actions.updateCardType).toHaveBeenCalledTimes(1));
    expect(actions.updateCardType.mock.calls[0][0]).toMatchObject({
      type: "cardType.update",
      cardTypeId: "type-magic",
      name: "绝学",
      fields: [{ key: "grade", label: "境界", kind: "text" }],
      baseRevision: 7
    });
  });

  it("删除自定义类型需二次确认，无引用时携带 revision 删除", async () => {
    const customType: CardType = {
      id: "type-unused",
      projectId: "p1",
      builtIn: false,
      kind: "custom_unused",
      name: "未使用类型",
      fields: [],
      sortOrder: 8,
      createdAt: "",
      updatedAt: "",
      revision: 4
    };
    render(<CardTypeEditor projectId="p1" cardTypes={[customType]} cards={[]} relationTypes={[]} onClose={() => {}} />);

    fireEvent.click(screen.getByRole("button", { name: "删除未使用类型" }));
    expect(actions.deleteCardType).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认删除未使用类型" }));

    await waitFor(() =>
      expect(actions.deleteCardType).toHaveBeenCalledWith({
        type: "cardType.delete",
        cardTypeId: "type-unused",
        baseRevision: 4
      })
    );
  });

  it("卡片或关系类型仍引用时说明影响并禁用删除，内置类型不可编辑删除", () => {
    const customType: CardType = {
      id: "type-used",
      projectId: "p1",
      builtIn: false,
      kind: "custom_used",
      name: "被引用类型",
      fields: [],
      sortOrder: 8,
      createdAt: "",
      updatedAt: "",
      revision: 2
    };
    const builtinType = { ...customType, id: "type-builtin", projectId: null, builtIn: true, kind: "character", name: "角色" };
    const card = {
      id: "card-1", projectId: "p1", kind: "custom_used", title: "卡片", aliases: [], fields: {}, tags: [], createdAt: "", updatedAt: "", revision: 1
    } satisfies CardSummary;
    const relationType = {
      id: "rel-type", projectId: "p1", builtIn: false, name: "rel", forwardName: "关联", reverseName: "被关联", fromKinds: ["custom_used"], toKinds: [], createdAt: "", updatedAt: "", revision: 1
    } satisfies RelationType;
    render(<CardTypeEditor projectId="p1" cardTypes={[customType, builtinType]} cards={[card]} relationTypes={[relationType]} onClose={() => {}} />);

    expect(screen.getByText("引用影响：1 张卡片，1 个关系类型")).toBeDefined();
    expect(screen.getByRole("button", { name: "删除被引用类型" }).disabled).toBe(true);
    expect(screen.getByRole("button", { name: "编辑角色" }).disabled).toBe(true);
    expect(screen.getByRole("button", { name: "删除角色" }).disabled).toBe(true);
    expect(screen.getByText("内置类型，不可编辑或删除")).toBeDefined();
  });

  it("更新失败时保留编辑内容与错误提示", async () => {
    actions.updateCardType.mockResolvedValue(false);
    const customType: CardType = {
      id: "type-fail", projectId: "p1", builtIn: false, kind: "custom_fail", name: "旧名称", fields: [], sortOrder: 8, createdAt: "", updatedAt: "", revision: 3
    };
    render(<CardTypeEditor projectId="p1" cardTypes={[customType]} cards={[]} relationTypes={[]} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "编辑旧名称" }));
    fireEvent.change(screen.getByDisplayValue("旧名称"), { target: { value: "未保存名称" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修改" }));

    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("保存卡片类型失败"));
    expect(screen.getByDisplayValue("未保存名称")).toBeDefined();
    expect(screen.getByRole("button", { name: "保存修改" })).toBeDefined();
    expect(actions.updateCardType).toHaveBeenCalledTimes(1);
  });

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

    await waitFor(() => expect(actions.runStructure).toHaveBeenCalledTimes(1));
    const command = actions.runStructure.mock.calls[0][0] as Record<string, unknown>;
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
    expect(actions.runStructure).not.toHaveBeenCalled();
  });

  it("空名称或空 key 不提交", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);

    // 只填字段、不填类型名称
    addFieldAndConfigure(container, 0, { label: "字段甲", key: "k1", kind: "text" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/请填写类型名称/)).toBeDefined());
    expect(actions.runStructure).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "有名称" } });
    // 把字段 key 清空
    const { inputs } = rowControls(container, 0);
    fireEvent.change(inputs[1], { target: { value: "" } });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/字段 key/)).toBeDefined());
    expect(actions.runStructure).not.toHaveBeenCalled();
  });

  it("选项字段没有有效选项不提交", async () => {
    const { container } = render(<CardTypeEditor projectId="p1" onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "选项类型" } });
    addFieldAndConfigure(container, 0, { label: "单选字段", key: "sel", kind: "select", options: "" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(screen.getByText(/至少需要一个有效选项/)).toBeDefined());
    expect(actions.runStructure).not.toHaveBeenCalled();
  });

  it("创建成功后回调 onClose 并刷新卡片类型与卡片列表", async () => {
    const onClose = vi.fn();
    const { container } = render(<CardTypeEditor projectId="p1" onClose={onClose} />);
    fireEvent.change(screen.getByPlaceholderText("例如：功法、神兵"), { target: { value: "关闭测试" } });
    addFieldAndConfigure(container, 0, { label: "字段甲", key: "k1", kind: "text" });
    fireEvent.click(screen.getByText("创建类型"));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(actions.loadCardTypes).toHaveBeenCalledWith("p1");
    expect(actions.loadCards).toHaveBeenCalledWith({ projectId: "p1" });
  });
});
