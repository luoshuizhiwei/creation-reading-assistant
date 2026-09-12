// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardRelation, CardSummary, CardType, CreationProjectSummary, RelationType } from "@/types/creation";

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

function makeProject(id: string, title: string): CreationProjectSummary {
  return {
    id,
    title,
    setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
    updatedAt: "",
    revision: 1,
    chapterCount: 1,
    sceneCount: 1
  };
}

function makeCard(projectId: string, id: string, kind: string, title: string): CardSummary {
  return {
    id,
    projectId,
    kind,
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

function makeType(id: string, kind: string, name: string): CardType {
  return { id, projectId: null, builtIn: true, kind, name, fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 };
}

function makeRelationType(id: string, forwardName: string, reverseName: string): RelationType {
  return {
    id,
    projectId: null,
    name: forwardName,
    forwardName,
    reverseName,
    fromKinds: [],
    toKinds: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

const p1 = makeProject("p1", "项目A");
const aCard = makeCard("p1", "card-a", "character", "卡片 A");
const bCard = makeCard("p1", "card-b", "character", "错误类型卡");
const cCard = makeCard("p1", "card-c", "location", "地点卡");
const p2Card = makeCard("p2", "card-p2", "location", "异项目卡");
const charType = makeType("t-char", "character", "角色");
const locType = makeType("t-loc", "location", "地点");
const relType = makeRelationType("rt-1", "位于", "包含");
relType.fromKinds = ["character"];
relType.toKinds = ["location"];
const relType2 = makeRelationType("rt-2", "收藏于", "被收藏于");
relType2.fromKinds = ["location"];
relType2.toKinds = [];
const existingRelation: CardRelation = {
  id: "rel-1",
  projectId: "p1",
  fromCardId: "card-a",
  toCardId: "card-c",
  relationTypeId: "rt-1",
  forwardName: "位于",
  note: null,
  createdAt: ""
};

function resetStores(): void {
  useCreationStore.setState({
    projects: [p1],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: "p1",
    cardTypes: [charType, locType],
    relationTypes: [relType, relType2],
    cards: [aCard, bCard, cCard, p2Card],
    cardRelations: { "card-a": { outgoing: [existingRelation], incoming: [] } },
    selectedCardId: "card-a",
    cardsLoading: false,
    selectedSceneId: undefined,
    sceneViews: {},
    abnormalExit: false,
    recoveryNoticeDismissed: false,
    loading: false,
    watchConnected: false,
    leaveGuard: undefined
  });
  useUIStore.setState({ toasts: [] });
}

beforeEach(() => {
  resetStores();
  vi.mocked(creationService.cardsList).mockReset();
  vi.mocked(creationService.cardTypesList).mockReset();
  vi.mocked(creationService.relationTypesList).mockReset();
  vi.mocked(creationService.cardRelations).mockReset();
  vi.mocked(creationService.resourceList).mockReset();
  vi.mocked(creationService.watchProject).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.watchProject).mockResolvedValue(() => {});
  // 这三个列表加载会把 store 中对应数组覆盖；不 mock 会写成 undefined 触发渲染崩溃。
  // 用与 resetStores 一致的样本值回填，保证 projectCards/类型/关系类型在挂载后仍可用。
  vi.mocked(creationService.cardsList).mockResolvedValue([aCard, bCard, cCard, p2Card]);
  vi.mocked(creationService.cardTypesList).mockResolvedValue([charType, locType]);
  vi.mocked(creationService.relationTypesList).mockResolvedValue([relType, relType2]);
  vi.mocked(creationService.cardRelations).mockResolvedValue({ outgoing: [existingRelation], incoming: [] });
  vi.mocked(creationService.resourceList).mockResolvedValue([]);
});

afterEach(() => cleanup());

describe("建立关系时目标过滤与语义", () => {
  it("目标列表按关系类型终点约束过滤，排除自身与异项目卡片", async () => {
    vi.mocked(creationService.runStructure).mockResolvedValue(true);
    const { container } = render(<CardsPage project={p1} />);

    fireEvent.click(await screen.findByText("建立关系"));
    const formSelects = () => container.querySelectorAll(".cards-relation-form select");
    // 选择关系类型
    fireEvent.change(formSelects()[0], { target: { value: "rt-1" } });

    const targetSelect = formSelects()[1] as HTMLSelectElement;
    const optionTexts = Array.from(targetSelect.options).map((option) => option.textContent ?? "");
    expect(optionTexts.some((text) => text.includes("地点卡"))).toBe(true);
    expect(optionTexts.some((text) => text.includes("错误类型卡"))).toBe(false);
    expect(optionTexts.some((text) => text.includes("异项目卡"))).toBe(false);
    // 自身也不在目标中
    expect(optionTexts.some((text) => text.includes("卡片 A"))).toBe(false);
  });

  it("显示正向/反向语义，起点类型不匹配时给出警告", async () => {
    vi.mocked(creationService.runStructure).mockResolvedValue(true);
    const { container } = render(<CardsPage project={p1} />);
    fireEvent.click(await screen.findByText("建立关系"));
    fireEvent.change(container.querySelectorAll(".cards-relation-form select")[0], { target: { value: "rt-1" } });
    // 语义段落专用 class，避免与关系类型下拉 <option>（同样含「反向：」）混淆。
    const semantic = container.querySelector(".cards-relation-semantic") as HTMLElement;
    expect(semantic).toBeDefined();
    expect(semantic.textContent).toContain("语义：");
    expect(semantic.textContent).toContain("位于");
    expect(semantic.textContent).toContain("反向：包含");
  });

  it("起点卡片类型不满足关系起点约束时显示警告且目标下拉禁用", async () => {
    vi.mocked(creationService.runStructure).mockResolvedValue(true);
    const { container } = render(<CardsPage project={p1} />);
    fireEvent.click(await screen.findByText("建立关系"));
    // rt-2 起点约束为 location，而当前卡片 card-a 是 character，应触发不匹配警告。
    fireEvent.change(container.querySelectorAll(".cards-relation-form select")[0], { target: { value: "rt-2" } });
    const warning = container.querySelector(".cards-form-error") as HTMLElement;
    expect(warning.textContent).toContain("不允许作为该关系的起点");
    const targetSelect = container.querySelectorAll(".cards-relation-form select")[1] as HTMLSelectElement;
    expect(targetSelect.disabled).toBe(true);
  });
});

describe("已有关系的删除", () => {
  it("删除关系需要二次确认", async () => {
    vi.mocked(creationService.runStructure).mockResolvedValue(true);
    render(<CardsPage project={p1} />);

    const deleteButtons = await screen.findAllByTitle("删除关系");
    expect(deleteButtons.length).toBeGreaterThan(0);
    fireEvent.click(deleteButtons[0]);
    // 第一次点击后变为「确认删除」，且尚未调用删除命令
    expect(screen.getByText("确认删除")).toBeDefined();
    expect(creationService.runStructure).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText("确认删除"));
    await waitFor(() =>
      expect(creationService.runStructure).toHaveBeenCalledWith(
        expect.objectContaining({ type: "cardRelation.delete", relationId: "rel-1" })
      )
    );
  });

  it("删除关系失败后 UI 仍保留该关系", async () => {
    vi.mocked(creationService.runStructure).mockImplementation(async (command: { type: string }) => {
      if (command.type === "cardRelation.delete") return false;
      return true;
    });
    const { container } = render(<CardsPage project={p1} />);

    const deleteButtons = await screen.findAllByTitle("删除关系");
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByText("确认删除"));

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledWith(
      expect.objectContaining({ type: "cardRelation.delete", relationId: "rel-1" })
    ));
    // 失败：关系条目仍在（地点卡是 rel-1 的终点标题），不先从本地消失。
    // 限定在关系列表内查询，避免与看板列中同名卡片标题混淆。
    const relationList = container.querySelector(".cards-relation-list") as HTMLElement;
    expect(relationList.textContent).toContain("地点卡");
  });
});
