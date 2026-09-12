// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor, fireEvent } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type {
  CardSummary,
  CardType,
  CreationProjectSummary,
  RelationGraphView,
  RelationType
} from "@/types/creation";

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
  cardLink: vi.fn(),
  cardUnlink: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  relationGraph: vi.fn(),
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
    revision: 1,
    linkedProjectIds: [projectId],
    usageCount: 1
  } as CardSummary;
}

const project = makeProject("p1", "项目A");
const cA = makeCard("p1", "card-a", "character", "苏青");
const cB = makeCard("p1", "card-b", "character", "顾淮");

const graph: RelationGraphView = {
  scope: "project",
  projectId: "p1",
  nodes: [
    { cardId: "card-a", title: "苏青", kind: "character", kindName: "角色", aliases: [], summary: "", degree: 1 },
    { cardId: "card-b", title: "顾淮", kind: "character", kindName: "角色", aliases: [], summary: "", degree: 1 }
  ],
  edges: [
    {
      id: "rel-1",
      fromCardId: "card-a",
      toCardId: "card-b",
      relationTypeId: "rt-a",
      relationName: "认识",
      forwardName: "认识",
      reverseName: "被认识",
      note: null
    }
  ],
  kindFacets: [{ kind: "character", name: "角色", count: 2 }],
  relationFacets: [{ id: "rt-a", name: "认识", forwardName: "认识", reverseName: "被认识", count: 1 }],
  truncatedNodeCount: 0,
  hiddenRelationCount: 2,
  isolatedNodeCount: 0
};

function resetStores(): void {
  useCreationStore.setState({
    projects: [project],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: "p1",
    cardTypes: [{ id: "t-a", projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 } as CardType],
    relationTypes: [
      {
        id: "rt-a",
        projectId: null,
        name: "认识",
        forwardName: "认识",
        reverseName: "被认识",
        fromKinds: [],
        toKinds: [],
        createdAt: "",
        updatedAt: "",
        revision: 1
      } as RelationType
    ],
    cards: [cA, cB],
    cardRelations: {},
    selectedCardId: undefined,
    cardsLoading: false,
    selectedSceneId: undefined,
    sceneViews: {},
    abnormalExit: false,
    recoveryNoticeDismissed: true,
    loading: false,
    watchConnected: false,
    leaveGuard: undefined
  });
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
}

beforeEach(() => {
  resetStores();
  vi.mocked(creationService.cardsList).mockReset();
  vi.mocked(creationService.cardTypesList).mockReset();
  vi.mocked(creationService.relationTypesList).mockReset();
  vi.mocked(creationService.cardRelations).mockReset();
  vi.mocked(creationService.resourceList).mockReset();
  vi.mocked(creationService.watchProject).mockReset();
  vi.mocked(creationService.relationGraph).mockReset();
  vi.mocked(creationService.cardsList).mockResolvedValue([cA, cB]);
  vi.mocked(creationService.cardTypesList).mockResolvedValue([{ id: "t-a", projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 } as CardType]);
  vi.mocked(creationService.relationTypesList).mockResolvedValue([]);
  vi.mocked(creationService.watchProject).mockResolvedValue(() => {});
  vi.mocked(creationService.cardRelations).mockResolvedValue({ outgoing: [], incoming: [] });
  vi.mocked(creationService.resourceList).mockResolvedValue([]);
});
afterEach(() => cleanup());

describe("设定卡页 · 关系图视图（Stage 4-F）", () => {
  it("默认不加载关系图，切到关系图页签才请求，且带项目作用域", async () => {
    vi.mocked(creationService.relationGraph).mockResolvedValue(graph);
    render(<CardsPage project={project} />);

    await waitFor(() => expect(creationService.cardsList).toHaveBeenCalled());
    expect(creationService.relationGraph).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("tab", { name: /关系图/ }));

    await waitFor(() => expect(creationService.relationGraph).toHaveBeenCalledTimes(1));
    expect(vi.mocked(creationService.relationGraph).mock.calls[0][0]).toEqual({
      kind: "relationGraph.list",
      projectId: "p1"
    });
    await waitFor(() => expect(screen.getByTestId("relation-graph")).toBeTruthy());
  });

  it("渲染项目引用投影，并显式报出指向范围外的关系条数", async () => {
    vi.mocked(creationService.relationGraph).mockResolvedValue(graph);
    render(<CardsPage project={project} />);
    fireEvent.click(screen.getByRole("tab", { name: /关系图/ }));

    await waitFor(() => expect(screen.getByTestId("relation-graph-node-card-a")).toBeTruthy());
    const scope = screen.getByTestId("relation-graph-scope").textContent ?? "";
    expect(scope).toContain("当前项目的引用投影");
    expect(scope).toContain("2 条关系指向范围外卡片，未绘制");
  });

  it("点击节点会选中该卡片（跳转），且不触发任何写入", async () => {
    vi.mocked(creationService.relationGraph).mockResolvedValue(graph);
    render(<CardsPage project={project} />);
    fireEvent.click(screen.getByRole("tab", { name: /关系图/ }));

    await waitFor(() => expect(screen.getByTestId("relation-graph-node-card-a")).toBeTruthy());
    fireEvent.click(screen.getByTestId("relation-graph-node-card-a"));

    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBe("card-a"));
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("读取失败时给出可重试的错误状态", async () => {
    vi.mocked(creationService.relationGraph).mockRejectedValueOnce(new Error("关系图读取失败。")).mockResolvedValueOnce(graph);
    render(<CardsPage project={project} />);
    fireEvent.click(screen.getByRole("tab", { name: /关系图/ }));

    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy());
    expect(screen.getByRole("alert").textContent).toContain("关系图读取失败");

    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    await waitFor(() => expect(screen.getByTestId("relation-graph-node-card-a")).toBeTruthy());
  });
});
