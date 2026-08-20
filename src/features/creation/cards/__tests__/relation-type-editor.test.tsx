// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { RelationTypeEditor } from "@/features/creation/cards/RelationTypeEditor";
import { useUIStore } from "@/stores/ui-store";
import type { CardRelation, CardType, RelationType } from "@/types/creation";

// Agent 1 提供的公共 Hook；在本测试中直接桩接，避免绕过 Hook 直接调用 IPC。
const actions = vi.hoisted(() => ({
  runStructure: vi.fn(),
  updateRelationType: vi.fn(),
  deleteRelationType: vi.fn(),
  loadRelationTypes: vi.fn(),
  loadCards: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    runStructure: actions.runStructure,
    updateRelationType: actions.updateRelationType,
    deleteRelationType: actions.deleteRelationType,
    loadRelationTypes: actions.loadRelationTypes,
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

function makeType(id: string, kind: string, name: string): CardType {
  return {
    id,
    projectId: null,
    kind,
    name,
    fields: [],
    sortOrder: 0,
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

const cardTypes: CardType[] = [makeType("t1", "character", "角色"), makeType("t2", "location", "地点")];

beforeEach(() => {
  actions.runStructure.mockReset();
  actions.runStructure.mockResolvedValue(true);
  actions.updateRelationType.mockReset();
  actions.updateRelationType.mockResolvedValue(true);
  actions.deleteRelationType.mockReset();
  actions.deleteRelationType.mockResolvedValue(true);
  actions.loadRelationTypes.mockResolvedValue(undefined);
  actions.loadCards.mockResolvedValue(undefined);
  useUIStore.setState({ toasts: [] });
});

afterEach(() => cleanup());

describe("RelationTypeEditor 自定义关系类型", () => {
  it("编辑自定义关系类型时锁定 name 并携带 baseRevision 提交", async () => {
    const customRelationType: RelationType = {
      id: "rt-custom",
      projectId: "p1",
      name: "rel-stable-key",
      forwardName: "师从",
      reverseName: "师父是",
      fromKinds: ["character"],
      toKinds: ["character"],
      createdAt: "",
      updatedAt: "",
      revision: 6
    };
    const { container } = render(
      <RelationTypeEditor projectId="p1" cardTypes={cardTypes} relationTypes={[customRelationType]} relations={[]} onClose={() => {}} />
    );

    fireEvent.click(screen.getByRole("button", { name: "编辑师从" }));
    expect(screen.getByDisplayValue("rel-stable-key").disabled).toBe(true);
    fireEvent.change(screen.getByDisplayValue("师从"), { target: { value: "传授" } });
    const groups = container.querySelectorAll(".cards-checkbox-group");
    fireEvent.click((groups[1] as HTMLElement).querySelectorAll("input")[0]);
    fireEvent.click((groups[1] as HTMLElement).querySelectorAll("input")[1]);
    fireEvent.click(screen.getByRole("button", { name: "保存修改" }));

    await waitFor(() => expect(actions.updateRelationType).toHaveBeenCalledTimes(1));
    expect(actions.updateRelationType.mock.calls[0][0]).toMatchObject({
      type: "relationType.update",
      relationTypeId: "rt-custom",
      name: "rel-stable-key",
      forwardName: "传授",
      reverseName: "师父是",
      fromKinds: ["character"],
      toKinds: ["location"],
      baseRevision: 6
    });
  });

  it("无关系实例引用时需二次确认并携带 revision 删除", async () => {
    const customRelationType: RelationType = {
      id: "rt-unused", projectId: "p1", name: "rel-unused", forwardName: "结识", reverseName: "被结识", fromKinds: [], toKinds: [], createdAt: "", updatedAt: "", revision: 5
    };
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} relationTypes={[customRelationType]} relations={[]} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "删除结识" }));
    expect(actions.deleteRelationType).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "确认删除结识" }));
    await waitFor(() =>
      expect(actions.deleteRelationType).toHaveBeenCalledWith({
        type: "relationType.delete",
        relationTypeId: "rt-unused",
        baseRevision: 5
      })
    );
  });

  it("有关系实例引用时显示数量并禁用删除，内置类型不可编辑删除", () => {
    const customRelationType: RelationType = {
      id: "rt-used", projectId: "p1", name: "rel-used", forwardName: "同行", reverseName: "同行", fromKinds: [], toKinds: [], createdAt: "", updatedAt: "", revision: 2
    };
    const builtinRelationType = { ...customRelationType, id: "rt-builtin", projectId: null, name: "knows", forwardName: "认识" };
    const relation = {
      id: "relation-1", projectId: "p1", fromCardId: "card-1", toCardId: "card-2", relationTypeId: "rt-used", forwardName: "同行", note: null, createdAt: ""
    } satisfies CardRelation;
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} relationTypes={[customRelationType, builtinRelationType]} relations={[relation]} onClose={() => {}} />);

    expect(screen.getByText("引用影响：1 条关系")).toBeDefined();
    expect(screen.getByRole("button", { name: "删除同行" }).disabled).toBe(true);
    expect(screen.getByRole("button", { name: "编辑认识" }).disabled).toBe(true);
    expect(screen.getByRole("button", { name: "删除认识" }).disabled).toBe(true);
    expect(screen.getByText("内置类型，不可编辑或删除")).toBeDefined();
  });

  it("更新失败时保留编辑器内容与错误", async () => {
    actions.updateRelationType.mockResolvedValue(false);
    const customRelationType: RelationType = {
      id: "rt-fail", projectId: "p1", name: "rel-fail", forwardName: "旧正向", reverseName: "旧反向", fromKinds: [], toKinds: [], createdAt: "", updatedAt: "", revision: 2
    };
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} relationTypes={[customRelationType]} relations={[]} onClose={() => {}} />);
    fireEvent.click(screen.getByRole("button", { name: "编辑旧正向" }));
    fireEvent.change(screen.getByDisplayValue("旧正向"), { target: { value: "未保存正向" } });
    fireEvent.click(screen.getByRole("button", { name: "保存修改" }));
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("保存关系类型失败"));
    expect(screen.getByDisplayValue("未保存正向")).toBeDefined();
    expect(actions.updateRelationType).toHaveBeenCalledTimes(1);
  });

  it("正反名称与起止类型约束正确提交并刷新", async () => {
    const { container } = render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} onClose={() => {}} />);

    fireEvent.change(screen.getByPlaceholderText("如：师徒"), { target: { value: "师徒" } });
    fireEvent.change(screen.getByPlaceholderText("如：师父"), { target: { value: "师父" } });
    // 起点类型约束与终点类型约束两个分组都渲染同名卡片类型，需按分组范围点击，避免歧义。
    const groups = container.querySelectorAll(".cards-checkbox-group");
    const fromGroup = groups[0] as HTMLElement;
    const toGroup = groups[1] as HTMLElement;
    const fromInputs = Array.from(fromGroup.querySelectorAll("input")) as HTMLInputElement[];
    const toInputs = Array.from(toGroup.querySelectorAll("input")) as HTMLInputElement[];
    // cardTypes 顺序为 [角色(character), 地点(location)]
    fireEvent.click(fromInputs[0]); // 起点：角色
    fireEvent.click(toInputs[1]); // 终点：地点

    fireEvent.click(screen.getByText("创建关系类型"));

    await waitFor(() => expect(actions.runStructure).toHaveBeenCalledTimes(1));
    const command = actions.runStructure.mock.calls[0][0] as Record<string, unknown>;
    expect(command.type).toBe("relationType.create");
    expect(command.forwardName).toBe("师徒");
    expect(command.reverseName).toBe("师父");
    expect(command.fromKinds).toEqual(["character"]);
    expect(command.toKinds).toEqual(["location"]);
    expect(actions.loadRelationTypes).toHaveBeenCalledWith("p1");
    expect(actions.loadCards).toHaveBeenCalledWith({ projectId: "p1" });
  });

  it("不勾选约束时 fromKinds/toKinds 为 undefined（不限）", async () => {
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("如：师徒"), { target: { value: "认识" } });
    fireEvent.change(screen.getByPlaceholderText("如：师父"), { target: { value: "被认识" } });
    fireEvent.click(screen.getByText("创建关系类型"));

    await waitFor(() => expect(actions.runStructure).toHaveBeenCalledTimes(1));
    const command = actions.runStructure.mock.calls[0][0] as Record<string, unknown>;
    expect(command.fromKinds).toBeUndefined();
    expect(command.toKinds).toBeUndefined();
  });

  it("缺少正反名称时不提交", async () => {
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("如：师徒"), { target: { value: "只有正向" } });
    fireEvent.click(screen.getByText("创建关系类型"));

    await waitFor(() => expect(screen.getByText(/请填写正向名称和反向名称/)).toBeDefined());
    expect(actions.runStructure).not.toHaveBeenCalled();
  });
});
