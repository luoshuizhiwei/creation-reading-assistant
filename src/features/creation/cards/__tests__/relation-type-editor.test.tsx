// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { RelationTypeEditor } from "@/features/creation/cards/RelationTypeEditor";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardType, RelationType } from "@/types/creation";

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
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.runStructure).mockResolvedValue(true);
  vi.mocked(creationService.relationTypesList).mockResolvedValue([] as RelationType[]);
  useUIStore.setState({ toasts: [] });
});

afterEach(() => cleanup());

describe("RelationTypeEditor 自定义关系类型", () => {
  it("正反名称与起止类型约束正确提交", async () => {
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

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledTimes(1));
    const command = vi.mocked(creationService.runStructure).mock.calls[0][0] as Record<string, unknown>;
    expect(command.type).toBe("relationType.create");
    expect(command.forwardName).toBe("师徒");
    expect(command.reverseName).toBe("师父");
    expect(command.fromKinds).toEqual(["character"]);
    expect(command.toKinds).toEqual(["location"]);
  });

  it("不勾选约束时 fromKinds/toKinds 为 undefined（不限）", async () => {
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("如：师徒"), { target: { value: "认识" } });
    fireEvent.change(screen.getByPlaceholderText("如：师父"), { target: { value: "被认识" } });
    fireEvent.click(screen.getByText("创建关系类型"));

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledTimes(1));
    const command = vi.mocked(creationService.runStructure).mock.calls[0][0] as Record<string, unknown>;
    expect(command.fromKinds).toBeUndefined();
    expect(command.toKinds).toBeUndefined();
  });

  it("缺少正反名称时不提交", async () => {
    render(<RelationTypeEditor projectId="p1" cardTypes={cardTypes} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText("如：师徒"), { target: { value: "只有正向" } });
    fireEvent.click(screen.getByText("创建关系类型"));

    await waitFor(() => expect(screen.getByText(/请填写正向名称和反向名称/)).toBeDefined());
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });
});
