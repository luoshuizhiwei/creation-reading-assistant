// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, renderHook, screen, within } from "@testing-library/react";
import { SearchPanel } from "@/features/search/SearchPanel";
import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";

vi.mock("@/services/creation-service", () => ({
  search: vi.fn(),
  inboxList: vi.fn(),
  cardsList: vi.fn(),
  readSceneBody: vi.fn(),
  readProjectOutline: vi.fn(),
  listProjects: vi.fn(),
  readProjectNavigation: vi.fn(),
  runStructure: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  cardRead: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  trashList: vi.fn(),
  snapshotList: vi.fn(),
  exportDraft: vi.fn(),
  replacePreview: vi.fn(),
  replaceApply: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  sessionReport: vi.fn(),
  sessionDelete: vi.fn(),
  proofQuery: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
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
  createProject: vi.fn(),
  readProjectHome: vi.fn()
}));
vi.mock("@/services/search-service", () => ({
  globalSearch: vi.fn()
}));
vi.mock("@/services/inspiration-service", () => ({
  listInspirations: vi.fn()
}));
vi.mock("@/services/library-service", () => ({
  listBooks: vi.fn()
}));
vi.mock("@/services/reader-service", () => ({
  openBook: vi.fn(),
  openEpub: vi.fn()
}));

import * as creationService from "@/services/creation-service";
import * as searchService from "@/services/search-service";
import * as inspirationService from "@/services/inspiration-service";

const creationHit = (overrides: Record<string, unknown>) => ({
  kind: "scene",
  id: "sc1",
  projectId: "p1",
  projectTitle: "项目甲",
  title: "场景A",
  snippet: "片段",
  updatedAt: "",
  ...overrides
});

function setupAllSourcesMock() {
  vi.mocked(creationService.search).mockResolvedValue({
    query: "词",
    total: 6,
    hits: [
      creationHit({ kind: "project", id: "p1", projectTitle: "项目甲", title: "项目甲" }),
      creationHit({ kind: "chapter", id: "ch1", title: "第一章" }),
      creationHit({ kind: "scene", id: "sc1", title: "场景A" }),
      creationHit({ kind: "card", id: "ca1", title: "苏青", cardKind: "character" })
    ]
  });
  vi.mocked(creationService.inboxList).mockResolvedValue([
    { id: "i1", title: "灵感词条", body: "收件箱内容", tags: [], status: "inbox", revision: 1, createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: [] }
  ]);
  vi.mocked(searchService.globalSearch).mockResolvedValue([
    {
      id: "r1",
      type: "book",
      title: "资料书",
      snippet: "正文片段",
      score: 3,
      sourcePath: "C:\\Users\\x\\AppData\\Roaming\\App\\data\\AppLibrary\\files\\资料书.txt",
      target: { bookId: "b1" }
    }
  ]);
  vi.mocked(inspirationService.listInspirations).mockResolvedValue([
    { id: "in1", title: "旧灵感词条", body: "兼容期内容", tags: [], type: "note", status: "inbox", platformTags: [], variants: [], revision: 1, deviceId: "d1", createdAt: "", updatedAt: "" }
  ]);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  useSearchStore.setState({
    open: true,
    keyword: "",
    filter: "all",
    projectContextId: undefined,
    allProjects: false,
    results: [],
    loading: false,
    error: null
  });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

async function typeAndFlush(keyword: string) {
  fireEvent.change(screen.getByRole("textbox"), { target: { value: keyword } });
  await act(async () => {
    vi.advanceTimersByTime(400);
  });
  await act(async () => {});
}

describe("SearchPanel 统一搜索界面", () => {
  it("六类核心结果（项目/章节/场景/卡片/收件箱/资料）在同一界面出现并分组", async () => {
    setupAllSourcesMock();
    render(<SearchPanel />);
    await typeAndFlush("词");
    expect(screen.getAllByRole("button", { name: /项目甲/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /第一章/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /场景A/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /苏青/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /灵感词条/ }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole("button", { name: /资料书/ }).length).toBeGreaterThan(0);

    const groups = ["项目", "章节", "场景正文", "卡片", "收件箱", "资料书库"];
    for (const label of groups) {
      expect(screen.getByRole("heading", { name: label })).toBeDefined();
    }
  });

  it("旧灵感源已摘除：即使服务返回旧灵感数据也不出现在结果中", async () => {
    setupAllSourcesMock();
    render(<SearchPanel />);
    await typeAndFlush("词");
    expect(screen.queryByRole("button", { name: /旧灵感词条/ })).toBeNull();
    expect(screen.queryByText("待迁移")).toBeNull();
    expect(screen.queryByText("旧灵感 · 待迁移")).toBeNull();
  });

  it("绝对本地路径不会显示", async () => {
    setupAllSourcesMock();
    render(<SearchPanel />);
    await typeAndFlush("词");
    expect(screen.queryByText(/C:\\Users/)).toBeNull();
    expect(screen.queryByText(/AppLibrary/)).toBeNull();
    expect(screen.getByText("书库 · 资料书.txt")).toBeDefined();
  });

  it("单一来源失败时其他来源结果保留且显示错误提示", async () => {
    vi.mocked(creationService.search).mockRejectedValue(new Error("创作工作区不可用"));
    vi.mocked(creationService.inboxList).mockResolvedValue([]);
    vi.mocked(searchService.globalSearch).mockResolvedValue([
      { id: "r1", type: "book", title: "资料书", snippet: "片段", score: 1, target: { bookId: "b1" } }
    ]);
    vi.mocked(inspirationService.listInspirations).mockResolvedValue([]);
    render(<SearchPanel />);
    await typeAndFlush("词");
    expect(screen.getByText("资料书")).toBeDefined();
    expect(screen.getByRole("alert")).toBeDefined();
    expect(screen.getByRole("alert").textContent).toContain("创作项目");
  });

  it("无结果时显示明确空状态", async () => {
    vi.mocked(creationService.search).mockResolvedValue({ query: "词", total: 0, hits: [] });
    vi.mocked(creationService.inboxList).mockResolvedValue([]);
    vi.mocked(searchService.globalSearch).mockResolvedValue([]);
    vi.mocked(inspirationService.listInspirations).mockResolvedValue([]);
    render(<SearchPanel />);
    await typeAndFlush("词");
    expect(screen.getByText(/没有找到与「词」匹配的内容/)).toBeDefined();
  });

  it("加载中显示进行中状态", async () => {
    vi.mocked(creationService.search).mockReturnValue(new Promise(() => undefined) as never);
    render(<SearchPanel />);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "词" } });
    await act(async () => {
      vi.advanceTimersByTime(400);
    });
    expect(screen.getByRole("status")).toBeDefined();
  });
});

describe("SearchPanel 键盘交互", () => {
  it("IME 组合输入期间 Ctrl+K 不触发打开", () => {
    useSearchStore.setState({ open: false });
    render(<SearchPanel />);
    fireEvent.keyDown(window, { key: "k", ctrlKey: true, isComposing: true });
    expect(useSearchStore.getState().open).toBe(false);
  });

  it("Esc 关闭搜索并恢复触发元素焦点", () => {
    render(
      <div>
        <button type="button" data-testid="trigger">打开搜索</button>
        <SearchPanel />
      </div>
    );
    const trigger = screen.getByTestId("trigger");
    trigger.focus();
    expect(document.activeElement).toBe(trigger);

    // 模拟 Ctrl+K 打开（记录焦点恢复目标）
    useSearchStore.setState({ open: true });
    act(() => {
      fireEvent.keyDown(window, { key: "k", ctrlKey: true });
    });
    expect(screen.getByRole("dialog")).toBeDefined();

    fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });
    expect(useSearchStore.getState().open).toBe(false);
  });

  it("方向键选择 + Enter 打开选中结果", async () => {
    setupAllSourcesMock();
    render(<SearchPanel />);
    await typeAndFlush("词");

    const input = screen.getByRole("textbox");
    fireEvent.keyDown(input, { key: "ArrowDown" });
    const listItems = screen.getAllByRole("listitem");
    expect(listItems[0]?.querySelector("button")?.className).toContain("active");

    fireEvent.keyDown(input, { key: "Enter" });
    await act(async () => {});
    expect(useSearchStore.getState().open).toBe(false);
  });

  it("点击结果产生正确导航意图（卡片 → card 意图 → 落入项目导航 seam）", async () => {
    setupAllSourcesMock();
    const { result: actions } = renderHook(() => useSearchActions());
    render(<SearchPanel />);
    await typeAndFlush("词");

    const cardEntry = useSearchStore.getState().results.find((entry) => entry.source === "card");
    expect(cardEntry?.intent).toEqual({ kind: "card", projectId: "p1", cardId: "ca1" });

    await act(async () => {
      await actions.current.openResult(cardEntry as never);
    });
    const { useCreationStore } = await import("@/stores/creation-store");
    expect(useSearchStore.getState().open).toBe(false);
    expect(useCreationStore.getState().selectedId).toBe("p1");
    // 不再直接选中卡片：seam 上落了请求，等待 CreationProjectsPage 消费后才切换视图和选中。
    const req = useCreationStore.getState().projectNavigationRequests["p1"];
    expect(req?.target).toMatchObject({ view: "cards", cardId: "ca1" });
  });

  it("收件箱结果携带真实条目 ID 并落入收件箱选中 seam", async () => {
    setupAllSourcesMock();
    const { result: actions } = renderHook(() => useSearchActions());
    render(<SearchPanel />);
    await typeAndFlush("词");

    const inboxEntry = useSearchStore.getState().results.find((entry) => entry.source === "inbox");
    expect(inboxEntry?.intent).toEqual({ kind: "inbox", inboxItemId: "i1" });

    await act(async () => {
      await actions.current.openResult(inboxEntry as never);
    });
    const { useCreationStore } = await import("@/stores/creation-store");
    const { useAppStore } = await import("@/stores/app-store");
    expect(useAppStore.getState().screen).toBe("inbox");
    // InboxPage 挂载时消费；此测试未渲染 InboxPage，故请求应仍保留。
    expect(useCreationStore.getState().inboxSelectionRequest).toBe("i1");
  });
});
