// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup } from "@testing-library/react";
import { CreationProjectsPage } from "@/features/creation/CreationProjectsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";

const service = vi.hoisted(() => ({
  listProjects: vi.fn(),
  readProjectHome: vi.fn(),
  inboxCount: vi.fn(),
  importProjectBundle: vi.fn(),
  importDraftPreview: vi.fn(),
  migrationStatus: vi.fn(),
  migrationRun: vi.fn(),
  runStructure: vi.fn(),
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
  cardsList: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  exportDraft: vi.fn(),
  exportProjectBundle: vi.fn(),
  annotationList: vi.fn(),
  annotationCreate: vi.fn(),
  annotationUpdate: vi.fn(),
  annotationDelete: vi.fn(),
  resourceList: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  projectExport: vi.fn(),
  inboxList: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  createProject: vi.fn()
}));

// 只 mock service 层；useCreationActions 使用真实实现（验证真实 loadCards/loadNavigation 契约）。
vi.mock("@/services/creation-service", () => service);
vi.mock("@/features/creation/import/ImportDraftDialog", () => ({
  ImportDraftDialog: () => <div />
}));
vi.mock("@/features/creation/editor/WritingDesk", () => ({
  WritingDesk: () => <div data-testid="writing-desk-stub">写作台</div>
}));
vi.mock("@/features/creation/outline/OutlinePage", () => ({
  OutlinePage: () => <div data-testid="outline-page-stub">大纲</div>
}));
vi.mock("@/features/creation/overview/OverviewPage", () => ({
  OverviewPage: () => <div data-testid="overview-page-stub">概览</div>
}));

const projectA = { id: "p1", title: "项目甲", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };
const projectB = { id: "p2", title: "项目乙", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };

function navigationOf(projectId: string, chapterId: string, sceneId: string) {
  return {
    project: { id: projectId, title: `项目${projectId}`, setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "", updatedAt: "", revision: 1 },
    chapters: [
      { id: chapterId, projectId, title: "第一章", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1, scenes: [
        { id: sceneId, chapterId, title: "场景A", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1 }
      ]}
    ]
  };
}

function makeCard(projectId: string, cardId: string) {
  return { id: cardId, projectId, kind: "character", title: `角色 ${cardId}`, aliases: [], tags: [], updatedAt: "", revision: 1, fields: {}, attachments: [] };
}

/** 真实 hook 的异步链路需要多次微任务 flush 才能完成 store 写入与重渲染。 */
async function flushMicrotasks(times = 12): Promise<void> {
  await act(async () => {
    for (let index = 0; index < times; index += 1) {
      await Promise.resolve();
    }
  });
}

function resetStore(): void {
  useCreationStore.setState({
    projects: [projectA, projectB],
    selectedId: undefined,
    navigations: {},
    outlines: {},
    cards: [],
    cardProjectId: undefined,
    cardsLoading: false,
    loading: false,
    watchConnected: false,
    projectNavigationRequests: {},
    inboxSelectionRequest: undefined,
    selectedSceneId: undefined,
    selectedCardId: undefined
  });
  useUIStore.setState({ toasts: [] });
}

/** 每个测试显式重置全部 Mock 实现（不依赖测试执行顺序）。 */
beforeEach(() => {
  vi.resetAllMocks();
  resetStore();
  service.listProjects.mockResolvedValue([projectA, projectB]);
  service.readProjectHome.mockResolvedValue({ projects: [] });
  service.inboxCount.mockResolvedValue({ total: 0, pending: 0 });
  service.migrationStatus.mockResolvedValue(null);
  service.readProjectNavigation.mockResolvedValue(null);
  service.cardsList.mockResolvedValue([]);
  service.cardTypesList.mockResolvedValue([
    { id: "type-character", projectId: null, kind: "character", name: "角色", fields: [], sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 }
  ]);
  service.relationTypesList.mockResolvedValue([]);
  service.cardRelations.mockResolvedValue({ outgoing: [], incoming: [] });
  service.resourceList.mockResolvedValue([]);
  service.watchProject.mockResolvedValue(() => {});
  service.readProjectOutline.mockResolvedValue(null);
  service.readSceneBody.mockResolvedValue(null);
});
afterEach(() => cleanup());

describe("真实 useCreationActions 契约：跨项目导航", () => {
  it("未预载项目只调用一次 readProjectNavigation，成功后定位章节首场景并消费请求", async () => {
    const navigationB = navigationOf("p2", "ch-p2-1", "sc-p2-1");
    service.readProjectNavigation.mockResolvedValue(navigationB);
    useCreationStore.setState({
      selectedId: "p2",
      projectNavigationRequests: {
        p2: { target: { projectId: "p2", view: "writing", chapterId: "ch-p2-1" }, createdAt: Date.now() }
      }
    });

    render(<CreationProjectsPage />);
    await flushMicrotasks();

    expect(useCreationStore.getState().selectedSceneId).toBe("sc-p2-1");
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
    expect(service.readProjectNavigation).toHaveBeenCalledTimes(1);
    expect(service.readProjectNavigation).toHaveBeenCalledWith("p2");
    expect(service.readSceneBody).toHaveBeenCalledWith("sc-p2-1");
  });
});

describe("跨项目卡片导航（当前项目 A，搜索目标属于项目 B）", () => {
  /** 模拟统一搜索 openIntent 的真实动作序列：先切到目标项目，再提交导航请求。 */
  function dispatchCardNavigation(cardId: string, createdAt: number) {
    act(() => {
      useCreationStore.getState().setSelectedId("p2");
      useCreationStore.getState().requestProjectNavigation({
        target: { projectId: "p2", view: "cards", cardId },
        createdAt
      });
    });
  }

  it("navigation 缺失时卡片请求独立执行：加载 B 卡片、选中目标、切 cards 视图、消费请求", async () => {
    service.cardsList.mockResolvedValue([makeCard("p2", "ca-p2-1")]);
    // 当前项目 A（工作台已渲染，navigations 只有 A）
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-p2-1", 1000);
    await flushMicrotasks();

    // 请求被消费
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
    // 加载的是 B 的卡片（真正的 A→B）
    expect(service.cardsList).toHaveBeenCalledWith(expect.objectContaining({ projectId: "p2" }));
    // 结果属于 B：selectedId 切换为 B、选中 B 的卡片、store 卡片归属 B
    expect(useCreationStore.getState().selectedId).toBe("p2");
    expect(useCreationStore.getState().selectedCardId).toBe("ca-p2-1");
    expect(useCreationStore.getState().cardProjectId).toBe("p2");
    // 真实 CardsPage 保持目标卡片 active，并显示对应详情。
    expect(document.querySelector(".cards-board-card.active")?.textContent).toContain("角色 ca-p2-1");
    expect(document.querySelector(".cards-detail-card h3")?.textContent).toBe("角色 ca-p2-1");
  });

  it("目标卡片不存在时提示且不选中（不伪装成功），请求被消费", async () => {
    service.cardsList.mockResolvedValue([makeCard("p2", "ca-other")]);
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-missing", 1000);
    await flushMicrotasks();

    expect(useUIStore.getState().toasts.some((t) => t.title === "目标卡片不存在")).toBe(true);
    expect(useCreationStore.getState().selectedCardId).toBeUndefined();
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
  });

  it("loadCards 失败返回 undefined 时提示且不选中（不伪装成功）", async () => {
    service.cardsList.mockRejectedValue(new Error("工作区不可用"));
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-p2-1", 1000);
    await flushMicrotasks();

    expect(useUIStore.getState().toasts.some((t) => t.title === "目标卡片不存在")).toBe(true);
    expect(useCreationStore.getState().selectedCardId).toBeUndefined();
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
  });

  it("B 的 navigation 加载失败时卡片请求不受影响，仍独立完成", async () => {
    // B 的 navigation 加载失败（通用 effect 会触发，但卡片不依赖它）
    service.readProjectNavigation.mockRejectedValue(new Error("结构加载失败"));
    service.cardsList.mockResolvedValue([makeCard("p2", "ca-p2-1")]);
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-p2-1", 1000);
    await flushMicrotasks();

    // 卡片导航完成（card 分支不经过 navigation 加载，且不会被失败消费）
    expect(useCreationStore.getState().selectedId).toBe("p2");
    expect(useCreationStore.getState().selectedCardId).toBe("ca-p2-1");
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
    expect(service.cardsList).toHaveBeenCalledWith(expect.objectContaining({ projectId: "p2" }));
    expect(document.querySelector(".cards-board-card.active")?.textContent).toContain("角色 ca-p2-1");
    expect(document.querySelector(".cards-detail-card h3")?.textContent).toBe("角色 ca-p2-1");
  });

  it("竞态：卡片加载挂起期间请求被替换，旧请求不得选择旧卡片", async () => {
    let resolveCards!: (value: unknown) => void;
    const pendingCards = new Promise((resolve) => {
      resolveCards = resolve;
    });
    service.cardsList.mockReturnValueOnce(pendingCards as never);
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-p2-1", 1000);
    await flushMicrotasks(4);

    // 请求被替换（新 createdAt）
    act(() => {
      useCreationStore.getState().requestProjectNavigation({
        target: { projectId: "p2", view: "cards", cardId: "ca-p2-new" },
        createdAt: 2000
      });
    });
    await flushMicrotasks(4);

    // 旧请求的卡片响应才返回：竞态守卫应放弃，不得选择旧卡片
    await act(async () => {
      resolveCards([makeCard("p2", "ca-p2-1")]);
    });
    await flushMicrotasks();

    // 旧请求被竞态守卫放弃：不得选择旧卡片
    expect(useCreationStore.getState().selectedCardId).not.toBe("ca-p2-1");
    // 新请求被独立处理（第二次 loadCards 返回空 → 目标不存在 → 消费），同样未选中
    expect(useCreationStore.getState().selectedCardId).toBeUndefined();
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
  });

  it("竞态：卡片加载挂起期间项目切换，不得选择旧项目的卡片", async () => {
    let resolveCards!: (value: unknown) => void;
    const pendingCards = new Promise((resolve) => {
      resolveCards = resolve;
    });
    service.cardsList.mockReturnValueOnce(pendingCards as never);
    useCreationStore.setState({
      selectedId: "p1",
      navigations: { p1: navigationOf("p1", "ch1", "sc1") },
      projectNavigationRequests: {}
    });
    render(<CreationProjectsPage />);
    await flushMicrotasks(4);

    dispatchCardNavigation("ca-p2-1", 1000);
    await flushMicrotasks(4);

    // 卡片加载期间用户切回项目 A 并消费请求
    act(() => {
      useCreationStore.getState().setSelectedId("p1");
      useCreationStore.getState().consumeProjectNavigation("p2");
    });
    await flushMicrotasks(4);

    await act(async () => {
      resolveCards([makeCard("p2", "ca-p2-1")]);
    });
    await flushMicrotasks();

    // 不得选择 B 的卡片（当前已切回 A）
    expect(useCreationStore.getState().selectedId).toBe("p1");
    expect(useCreationStore.getState().selectedCardId).toBeUndefined();
  });
});
