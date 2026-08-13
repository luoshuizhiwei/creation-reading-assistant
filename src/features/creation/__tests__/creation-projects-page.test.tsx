// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
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

vi.mock("@/services/creation-service", () => service);

vi.mock("@/features/creation/import/ImportDraftDialog", () => ({
  ImportDraftDialog: ({ onImported, onClose }: { onImported: () => void; onClose: () => void }) => (
    <div data-testid="import-draft-dialog">
      <button data-testid="fake-imported" onClick={() => onImported()} />
      <button data-testid="fake-close" onClick={() => onClose()} />
    </div>
  )
}));

// WritingDesk 和 CardsPage 依赖 window.matchMedia 等浏览器 API，jsdom 不提供；
// 导航 seam 测试只关注视图切换和请求消费，不需要渲染完整子页面。
vi.mock("@/features/creation/editor/WritingDesk", () => ({
  WritingDesk: () => <div data-testid="writing-desk-stub">写作台</div>
}));
vi.mock("@/features/creation/cards/CardsPage", () => ({
  CardsPage: () => <div data-testid="cards-page-stub">卡片管理</div>
}));
vi.mock("@/features/creation/outline/OutlinePage", () => ({
  OutlinePage: () => <div data-testid="outline-page-stub">大纲</div>
}));
vi.mock("@/features/creation/overview/OverviewPage", () => ({
  OverviewPage: () => <div data-testid="overview-page-stub">概览</div>
}));

vi.mock("@/hooks/useCreationActions", () => {
  const hookApi = {
    loadProjects: () => service.listProjects().then(() => undefined),
    loadProjectHome: () => service.readProjectHome(),
    loadInboxCount: () => service.inboxCount(),
    loadMigrationStatus: () => service.migrationStatus(),
    runMigration: () => service.migrationRun(),
    runStructure: (cmd: unknown) => service.runStructure(cmd),
    loadNavigation: (projectId: string) => service.readProjectNavigation(projectId).then((nav: unknown) => {
      // 把结果同步到 store，模拟真实 loadNavigation 行为
      if (nav) {
        useCreationStore.getState().setNavigation(projectId, nav as never);
      }
      return nav;
    }),
    loadOutline: (projectId: string) => service.readProjectOutline(projectId),
    loadScene: (sceneId: string) => service.readSceneBody(sceneId),
    loadCards: (query: unknown) => service.cardsList(query).then((cards: unknown) => {
      // 把结果同步到 store，模拟真实 loadCards 行为
      const q = query as { projectId?: string };
      if (q?.projectId && Array.isArray(cards)) {
        useCreationStore.getState().setCards(q.projectId, cards as never);
      }
      return cards;
    }),
    loadCardTypes: () => service.cardTypesList(),
    loadRelationTypes: () => service.relationTypesList(),
    loadCardRelations: () => service.cardRelations(),
    readCard: () => service.cardRead(),
    loadTrash: () => service.trashList(),
    restoreTrash: () => service.runStructure(),
    purgeTrash: () => service.runStructure(),
    loadSnapshots: () => service.snapshotList(),
    exportDraft: () => service.exportDraft(),
    exportBundle: () => service.exportProjectBundle(),
    importBundle: () => service.importProjectBundle(),
    previewDraftImport: () => service.importDraftPreview(),
    search: () => service.search(),
    replacePreview: () => service.replacePreview(),
    replaceApply: () => service.replaceApply(),
    loadStats: () => service.statsView(),
    loadSessions: () => service.sessionList(),
    reportSession: () => service.sessionReport(),
    deleteSession: () => service.sessionDelete(),
    runProof: () => service.proofQuery(),
    loadInbox: () => service.inboxList(),
    updateInbox: () => service.inboxUpdate(),
    deleteInbox: () => service.inboxDelete(),
    createProject: () => service.createProject(),
    loadProjectExport: () => service.projectExport(),
    saveSceneBody: () => service.updateSceneBody(),
    subscribeProject: () => () => undefined
  };
  return { useCreationActions: () => hookApi };
});

function resetStores(): void {
  useCreationStore.setState({
    projects: [],
    selectedId: undefined,
    navigations: {},
    outlines: {},
    cards: [],
    loading: false,
    watchConnected: false,
    projectNavigationRequests: {},
    inboxSelectionRequest: undefined,
    selectedSceneId: undefined,
    selectedCardId: undefined
  });
  useUIStore.setState({ toasts: [] });
}

beforeEach(() => {
  vi.clearAllMocks();
  resetStores();
  service.listProjects.mockResolvedValue([]);
  service.readProjectHome.mockResolvedValue({ projects: [] });
  service.inboxCount.mockResolvedValue({ total: 0, pending: 0 });
  service.migrationStatus.mockResolvedValue(null);
  service.readProjectNavigation.mockResolvedValue(null);
});
afterEach(() => cleanup());

describe("CreationProjectsPage 导入后首页刷新", () => {
  it("项目包导入成功后重新读取 project.home", async () => {
    service.importProjectBundle.mockResolvedValue({
      canceled: false,
      result: { commandType: "project.bundle.import", sequence: 1, projectId: "p9", counts: { volumes: 1, chapters: 1, scenes: 1, cards: 0, relations: 0, snapshots: 0 } }
    });
    service.readProjectHome.mockResolvedValue({
      projects: [{
        id: "p9", title: "导入包", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
        updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1, currentChars: 0
      }]
    });

    render(<CreationProjectsPage />);
    await waitFor(() => expect(service.readProjectHome.mock.calls.length).toBeGreaterThanOrEqual(1));
    const beforeImport = service.readProjectHome.mock.calls.length;

    fireEvent.click(screen.getByRole("button", { name: /导入项目包/ }));
    await waitFor(() => expect(service.readProjectHome.mock.calls.length).toBeGreaterThan(beforeImport));
    expect(await screen.findByText("导入包")).toBeDefined();
  });

  it("项目包导入取消时不得重新读取 project.home", async () => {
    service.importProjectBundle.mockResolvedValue({ canceled: true, result: null });
    render(<CreationProjectsPage />);
    await waitFor(() => expect(service.readProjectHome.mock.calls.length).toBeGreaterThanOrEqual(1));
    await new Promise((resolve) => setTimeout(resolve, 50));
    const beforeImport = service.readProjectHome.mock.calls.length;
    fireEvent.click(screen.getByRole("button", { name: /导入项目包/ }));
    await waitFor(() => expect(service.importProjectBundle).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(service.readProjectHome.mock.calls.length).toBe(beforeImport);
  });

  it("旧稿导入成功后（onImported）重新读取 project.home", async () => {
    render(<CreationProjectsPage />);
    await waitFor(() => expect(service.readProjectHome.mock.calls.length).toBeGreaterThanOrEqual(1));
    const beforeImport = service.readProjectHome.mock.calls.length;

    fireEvent.click(screen.getByRole("button", { name: /导入旧稿/ }));
    expect(await screen.findByTestId("import-draft-dialog")).toBeDefined();

    service.readProjectHome.mockResolvedValue({
      projects: [{
        id: "p7", title: "旧稿项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
        updatedAt: "", revision: 1, chapterCount: 2, sceneCount: 5, currentChars: 800
      }]
    });
    fireEvent.click(screen.getByTestId("fake-imported"));
    await waitFor(() => expect(service.readProjectHome.mock.calls.length).toBeGreaterThan(beforeImport));
    expect(await screen.findByText("旧稿项目")).toBeDefined();
  });
});

describe("CreationProjectsPage 项目导航 seam 消费", () => {
  const project = { id: "p1", title: "项目甲", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };
  const navigation = {
    project: { id: "p1", title: "项目甲", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "", updatedAt: "", revision: 1 },
    chapters: [
      { id: "ch1", projectId: "p1", title: "第一章", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1, scenes: [
        { id: "sc1", chapterId: "ch1", title: "场景A", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1 },
        { id: "sc2", chapterId: "ch1", title: "场景B", sortOrder: 2, createdAt: "", updatedAt: "", revision: 1 }
      ]},
      { id: "ch2", projectId: "p1", title: "第二章", sortOrder: 2, createdAt: "", updatedAt: "", revision: 1, scenes: [
        { id: "sc3", chapterId: "ch2", title: "场景C", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1 }
      ]}
    ]
  };

  beforeEach(() => {
    service.readProjectNavigation.mockResolvedValue(navigation);
  });

  it("scene 导航请求：切换到 writing 视图并定位目标场景", async () => {
    useCreationStore.setState({
      projects: [project],
      selectedId: "p1",
      navigations: { p1: navigation },
      projectNavigationRequests: {
        p1: { target: { projectId: "p1", view: "writing", sceneId: "sc2" }, createdAt: Date.now() }
      }
    });

    render(<CreationProjectsPage />);
    await waitFor(() => expect(useCreationStore.getState().selectedSceneId).toBe("sc2"));
    // 请求被消费后清除，避免重渲染重复跳转
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();
    // 调用了 readSceneBody 加载目标场景正文
    expect(service.readSceneBody).toHaveBeenCalledWith("sc2");
    // 视图切到 writing（通过视图描述文本验证）
    expect(screen.getByText(/在场景中连续写作/)).toBeDefined();
  });

  it("card 导航请求：切换到 cards 视图并选中卡片", async () => {
    service.cardsList.mockResolvedValue([
      { id: "ca1", projectId: "p1", kind: "character", title: "角色A", aliases: [], tags: [], updatedAt: "", revision: 1, fields: {}, attachments: [] }
    ]);
    useCreationStore.setState({
      projects: [project],
      selectedId: "p1",
      navigations: { p1: navigation },
      projectNavigationRequests: {
        p1: { target: { projectId: "p1", view: "cards", cardId: "ca1" }, createdAt: Date.now() }
      }
    });

    render(<CreationProjectsPage />);
    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBe("ca1"));
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();
    expect(service.cardsList).toHaveBeenCalled();
  });

  it("chapter 导航请求：定位到目标章节的首个场景", async () => {
    useCreationStore.setState({
      projects: [project],
      selectedId: "p1",
      navigations: { p1: navigation },
      projectNavigationRequests: {
        p1: { target: { projectId: "p1", view: "writing", chapterId: "ch2" }, createdAt: Date.now() }
      }
    });

    render(<CreationProjectsPage />);
    // chapter 请求应定位到 ch2 的第一个场景 sc3，而不是只打开项目
    await waitFor(() => expect(useCreationStore.getState().selectedSceneId).toBe("sc3"));
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();
    expect(service.readSceneBody).toHaveBeenCalledWith("sc3");
  });

  it("无导航请求时不消费、不切换视图", async () => {
    useCreationStore.setState({
      projects: [project],
      selectedId: "p1",
      navigations: { p1: navigation },
      projectNavigationRequests: {}
    });

    render(<CreationProjectsPage />);
    // 项目 hero 标题可见即说明页面已渲染
    await waitFor(() => expect(screen.getAllByText("项目甲").length).toBeGreaterThan(0));
    // 没有请求时默认停留在 overview，不选中场景
    expect(useCreationStore.getState().selectedSceneId).toBeUndefined();
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();
  });

  it("未预载项目的 chapter 导航：先等待 loadNavigation 再定位目标", async () => {
    // 模拟跨项目场景：当前 selectedId=p1，但导航请求指向 p2，p2 的 navigation 未加载
    const project2 = { id: "p2", title: "项目乙", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };
    const navigation2 = {
      project: { id: "p2", title: "项目乙", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, createdAt: "", updatedAt: "", revision: 1 },
      chapters: [
        { id: "ch-p2-1", projectId: "p2", title: "项目乙第一章", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1, scenes: [
          { id: "sc-p2-1", chapterId: "ch-p2-1", title: "项目乙场景A", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1 }
        ]}
      ]
    };
    useCreationStore.setState({
      projects: [project, project2],
      selectedId: "p2",
      navigations: {}, // p2 navigation 未预载
      projectNavigationRequests: {
        p2: { target: { projectId: "p2", view: "writing", chapterId: "ch-p2-1" }, createdAt: Date.now() }
      }
    });
    service.readProjectNavigation.mockResolvedValue(navigation2);

    render(<CreationProjectsPage />);
    // 等待 navigation 加载后，定位到 ch-p2-1 的首场景 sc-p2-1
    await waitFor(() => expect(useCreationStore.getState().selectedSceneId).toBe("sc-p2-1"), { timeout: 3000 });
    // 请求被消费
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
    // 调用了 readSceneBody 加载目标场景正文
    expect(service.readSceneBody).toHaveBeenCalledWith("sc-p2-1");
  });

  it("chapter 导航目标章节不存在时给出明确提示并清除无效请求", async () => {
    useCreationStore.setState({
      projects: [project],
      selectedId: "p1",
      navigations: { p1: navigation },
      projectNavigationRequests: {
        p1: { target: { projectId: "p1", view: "writing", chapterId: "ch-nonexistent" }, createdAt: Date.now() }
      }
    });

    render(<CreationProjectsPage />);
    // 目标章节不存在 → 显示明确提示
    await waitFor(() => expect(useUIStore.getState().toasts.some((t) => t.title === "目标章节不存在")).toBe(true));
    // 请求被清除
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();
    // 不切换视图（保持 overview）
    expect(useCreationStore.getState().selectedSceneId).toBeUndefined();
  });

  it("跨项目卡片导航：先激活项目→加载卡片→确认存在→selectCard→切视图", async () => {
    // 模拟跨项目：当前 selectedId=p1，但卡片请求指向 p2 的卡 ca-p2-1
    const project2 = { id: "p2", title: "项目乙", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };
    useCreationStore.setState({
      projects: [project, project2],
      selectedId: "p2",
      navigations: { p1: navigation }, // p2 无 navigation（卡片不需要 navigation）
      projectNavigationRequests: {
        p2: { target: { projectId: "p2", view: "cards", cardId: "ca-p2-1" }, createdAt: Date.now() }
      }
    });
    service.cardsList.mockResolvedValue([
      { id: "ca-p2-1", projectId: "p2", kind: "character", title: "项目乙角色", aliases: [], tags: [], updatedAt: "", revision: 1, fields: {}, attachments: [] }
    ]);

    render(<CreationProjectsPage />);
    // 卡片存在 → 选中目标卡片
    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBe("ca-p2-1"), { timeout: 3000 });
    // 请求被消费
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
    // 调用了 cardsList 加载目标项目卡片
    expect(service.cardsList).toHaveBeenCalledWith(expect.objectContaining({ projectId: "p2" }));
  });

  it("跨项目卡片导航：目标卡片不存在时给出明确提示", async () => {
    const project2 = { id: "p2", title: "项目乙", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 };
    useCreationStore.setState({
      projects: [project, project2],
      selectedId: "p2",
      navigations: { p1: navigation },
      projectNavigationRequests: {
        p2: { target: { projectId: "p2", view: "cards", cardId: "ca-nonexistent" }, createdAt: Date.now() }
      }
    });
    service.cardsList.mockResolvedValue([
      { id: "ca-p2-other", projectId: "p2", kind: "character", title: "另一张卡", aliases: [], tags: [], updatedAt: "", revision: 1, fields: {}, attachments: [] }
    ]);

    render(<CreationProjectsPage />);
    await waitFor(() => expect(useUIStore.getState().toasts.some((t) => t.title === "目标卡片不存在")).toBe(true), { timeout: 3000 });
    expect(useCreationStore.getState().projectNavigationRequests["p2"]).toBeUndefined();
  });
});
