// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardSummary, CardType, CreationProjectSummary, RelationType } from "@/types/creation";

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

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

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
    createdAt: "2026-08-09T10:00:00.000Z",
    updatedAt: "2026-08-09T10:00:00.000Z",
    revision: 1
  };
}

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

function makeRelationType(id: string, forwardName: string): RelationType {
  return {
    id,
    projectId: null,
    name: forwardName,
    forwardName,
    reverseName: `被${forwardName}`,
    fromKinds: [],
    toKinds: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

const p1 = makeProject("p1", "项目A");
const p2 = makeProject("p2", "项目B");
const aCard = makeCard("p1", "card-a", "character", "卡片 A");
const bCard = makeCard("p2", "card-b", "character", "卡片 B");
const aType = makeType("t-a", "character", "角色A");
const bType = makeType("t-b", "location", "地点B");
const aRelType = makeRelationType("rt-a", "认识A");
const bRelType = makeRelationType("rt-b", "认识B");

function resetStores(): void {
  useCreationStore.setState({
    projects: [p1, p2],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: undefined,
    cardTypes: [],
    relationTypes: [],
    cards: [],
    cardRelations: {},
    selectedCardId: undefined,
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
  vi.mocked(creationService.cardLink).mockReset();
  vi.mocked(creationService.cardUnlink).mockReset();
  vi.mocked(creationService.resourceList).mockReset();
  vi.mocked(creationService.watchProject).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.watchProject).mockResolvedValue(() => {});
  vi.mocked(creationService.cardRelations).mockResolvedValue({ outgoing: [], incoming: [] });
  vi.mocked(creationService.resourceList).mockResolvedValue([]);
});
afterEach(() => cleanup());

describe("CardsPage 跨项目竞态与状态清空", () => {
  it("搜索深链预选当前项目卡片时，挂载后保持选中并显示详情", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([bCard]);
    vi.mocked(creationService.cardTypesList).mockResolvedValue([makeType("t-b", "character", "角色")]);
    vi.mocked(creationService.relationTypesList).mockResolvedValue([]);
    useCreationStore.setState({
      selectedId: "p2",
      cardProjectId: "p2",
      cards: [bCard],
      selectedCardId: "card-b"
    });

    const view = render(<CardsPage project={p2} />);

    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBe("card-b"));
    expect(view.container.querySelector(".cards-board-card.active")?.textContent).toContain("卡片 B");
    expect(view.container.querySelector(".cards-detail-card h3")?.textContent).toBe("卡片 B");
  });

  it("预选卡片不属于当前项目时，挂载后清除旧选择", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([bCard]);
    vi.mocked(creationService.cardTypesList).mockResolvedValue([makeType("t-b", "character", "角色")]);
    vi.mocked(creationService.relationTypesList).mockResolvedValue([]);
    useCreationStore.setState({
      selectedId: "p2",
      cardProjectId: "p2",
      cards: [bCard],
      selectedCardId: "card-a"
    });

    const view = render(<CardsPage project={p2} />);

    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBeUndefined());
    expect(view.container.querySelector(".cards-board-card.active")).toBeNull();
    expect(view.container.querySelector(".cards-detail-card")).toBeNull();
  });

  it("项目 A 请求未完成时切到 B：A 的迟到卡片/类型/关系类型响应不得覆盖 B", async () => {
    const dCardsA = deferred<CardSummary[]>();
    const dTypesA = deferred<CardType[]>();
    const dRelsA = deferred<RelationType[]>();
    vi.mocked(creationService.cardsList)
      .mockImplementationOnce(() => dCardsA.promise)
      .mockResolvedValueOnce([bCard]);
    vi.mocked(creationService.cardTypesList)
      .mockImplementationOnce(() => dTypesA.promise)
      .mockResolvedValueOnce([bType]);
    vi.mocked(creationService.relationTypesList)
      .mockImplementationOnce(() => dRelsA.promise)
      .mockResolvedValueOnce([bRelType]);

    const view = render(<CardsPage project={p1} />);
    await waitFor(() => expect(creationService.cardsList).toHaveBeenCalledTimes(1));

    act(() => {
      useCreationStore.getState().setSelectedId("p2");
    });
    view.rerender(<CardsPage project={p2} />);

    await waitFor(() => {
      expect(useCreationStore.getState().cards).toEqual([bCard]);
      expect(useCreationStore.getState().cardTypes).toEqual([bType]);
      expect(useCreationStore.getState().relationTypes).toEqual([bRelType]);
    });

    await act(async () => {
      dCardsA.resolve([aCard]);
      dTypesA.resolve([aType]);
      dRelsA.resolve([aRelType]);
    });

    const state = useCreationStore.getState();
    expect(state.cardProjectId).toBe("p2");
    expect(state.cards).toEqual([bCard]);
    expect(state.cardTypes).toEqual([bType]);
    expect(state.relationTypes).toEqual([bRelType]);
  });

  it("切换项目后旧 cards/relations/selection/draft/loading 立即清空", async () => {
    const dCardsB = deferred<CardSummary[]>();
    vi.mocked(creationService.cardsList)
      .mockResolvedValueOnce([aCard])
      .mockImplementationOnce(() => dCardsB.promise);
    vi.mocked(creationService.cardTypesList).mockResolvedValue([aType]);
    vi.mocked(creationService.relationTypesList).mockResolvedValue([]);

    act(() => {
      useCreationStore.getState().setSelectedId("p1");
    });
    const view = render(<CardsPage project={p1} />);
    await waitFor(() => expect(screen.getByText("卡片 A")).toBeDefined());

    // 选中 A 的卡片进入详情，并打开编辑草稿
    fireEvent.click(screen.getByText("卡片 A"));
    await waitFor(() => expect(screen.getByTitle("编辑")).toBeDefined());
    fireEvent.click(screen.getByTitle("编辑"));
    expect(screen.getByText("编辑卡片")).toBeDefined();
    await waitFor(() =>
      expect(useCreationStore.getState().cardRelations["card-a"]).toEqual({ outgoing: [], incoming: [] })
    );

    // 切到项目 B：store 作用域立即清空（同步断言，不等任何异步）
    act(() => {
      useCreationStore.getState().setSelectedId("p2");
    });
    expect(useCreationStore.getState().selectedCardId).toBeUndefined();
    expect(useCreationStore.getState().cards).toEqual([]);
    expect(useCreationStore.getState().cardRelations).toEqual({});
    expect(useCreationStore.getState().cardsLoading).toBe(false);

    view.rerender(<CardsPage project={p2} />);
    // 编辑草稿随项目切换消失，回到详情空态
    await waitFor(() => expect(screen.queryByText("编辑卡片")).toBeNull());
    expect(screen.queryByText("卡片 A")).toBeNull();

    // B 加载完成：loading 恢复，且不残留 A 的数据
    await act(async () => {
      dCardsB.resolve([bCard]);
    });
    await waitFor(() => expect(useCreationStore.getState().cardsLoading).toBe(false));
    expect(useCreationStore.getState().cards).toEqual([bCard]);
  });
});
