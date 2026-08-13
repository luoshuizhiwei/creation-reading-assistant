// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";

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

import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import * as creationService from "@/services/creation-service";
import * as searchService from "@/services/search-service";
import * as readerService from "@/services/reader-service";
import * as libraryService from "@/services/library-service";

beforeEach(() => {
  vi.clearAllMocks();
  useSearchStore.setState({
    open: false,
    keyword: "",
    filter: "all",
    projectContextId: undefined,
    allProjects: false,
    results: [],
    loading: false,
    error: null
  });
  vi.mocked(creationService.search).mockResolvedValue({ query: "", total: 0, hits: [] });
  vi.mocked(creationService.inboxList).mockResolvedValue([]);
  vi.mocked(searchService.globalSearch).mockResolvedValue([]);
});
afterEach(() => {
  vi.useRealTimers();
});

function createEntry(overrides: Record<string, unknown>) {
  return {
    id: "scene:s1",
    source: "scene",
    title: "场景A",
    snippet: "",
    originLabel: "项目 · 项目甲",
    intent: { kind: "scene", projectId: "p1", sceneId: "s1", chapterId: "ch1" },
    ...overrides
  };
}

describe("useSearchActions.runSearch", () => {
  it("250ms 防抖后聚合全部来源并写入结果", async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useSearchActions());
    vi.mocked(creationService.search).mockResolvedValue({
      query: "词",
      total: 1,
      hits: [{ kind: "scene", id: "s1", projectId: "p1", projectTitle: "项目甲", title: "场景A", snippet: "", updatedAt: "" }]
    });
    act(() => {
      result.current.runSearch("词");
    });
    expect(useSearchStore.getState().loading).toBe(false);
    await act(async () => {
      vi.advanceTimersByTime(300);
    });
    await act(async () => {});
    expect(useSearchStore.getState().results.some((entry) => entry.source === "scene")).toBe(true);
    expect(useSearchStore.getState().loading).toBe(false);
  });

  it("最后一次输入获胜：旧请求晚返回不覆盖新结果", async () => {
    vi.useFakeTimers();
    let resolveFirst: (value: unknown) => void = () => undefined;
    const first = new Promise((resolve) => {
      resolveFirst = resolve;
    });
    const { result } = renderHook(() => useSearchActions());

    vi.mocked(creationService.search).mockReturnValueOnce(first as never);
    act(() => result.current.runSearch("旧词"));
    await act(async () => vi.advanceTimersByTime(300));

    vi.mocked(creationService.search).mockResolvedValueOnce({
      query: "新词",
      total: 1,
      hits: [{ kind: "card", id: "c9", projectId: "p1", projectTitle: "项目甲", title: "新卡片", snippet: "", updatedAt: "" }]
    });
    act(() => result.current.runSearch("新词"));
    await act(async () => vi.advanceTimersByTime(300));
    await act(async () => {});
    expect(useSearchStore.getState().results.some((entry) => entry.id === "card:c9")).toBe(true);

    // 旧请求现在才返回：不得覆盖
    await act(async () => {
      resolveFirst({
        query: "旧词",
        total: 1,
        hits: [{ kind: "scene", id: "s-old", projectId: "p1", projectTitle: "项目甲", title: "旧场景", snippet: "", updatedAt: "" }]
      });
    });
    await act(async () => {});
    expect(useSearchStore.getState().results.some((entry) => entry.id === "scene:s-old")).toBe(false);
    expect(useSearchStore.getState().results.some((entry) => entry.id === "card:c9")).toBe(true);
  });

  it("单一来源失败时其他来源结果保留并给出错误提示", async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useSearchActions());
    vi.mocked(creationService.search).mockRejectedValue(new Error("创作工作区不可用"));
    vi.mocked(searchService.globalSearch).mockResolvedValue([
      {
        id: "r1",
        type: "book",
        title: "资料书",
        snippet: "片段",
        score: 1,
        target: { bookId: "b1" }
      }
    ]);
    act(() => result.current.runSearch("词"));
    await act(async () => vi.advanceTimersByTime(300));
    await act(async () => {});
    expect(useSearchStore.getState().results.length).toBe(1);
    expect(useSearchStore.getState().results[0]?.source).toBe("library");
    expect(useSearchStore.getState().error).toContain("创作项目");
  });

  it("当前项目上下文与全部项目切换影响 projectId 透传", async () => {
    vi.useFakeTimers();
    useSearchStore.setState({ projectContextId: "p7", allProjects: false });
    const { result } = renderHook(() => useSearchActions());
    act(() => result.current.runSearch("词"));
    await act(async () => vi.advanceTimersByTime(300));
    expect(vi.mocked(creationService.search)).toHaveBeenCalledWith(expect.objectContaining({ projectId: "p7" }));

    vi.clearAllMocks();
    vi.mocked(creationService.search).mockResolvedValue({ query: "", total: 0, hits: [] });
    useSearchStore.setState({ allProjects: true });
    act(() => result.current.runSearch("词"));
    await act(async () => vi.advanceTimersByTime(300));
    expect(vi.mocked(creationService.search)).toHaveBeenCalledWith(expect.objectContaining({ projectId: undefined }));
  });

  it("搜索取消：新搜索 abort 旧搜索的多页扫描，旧结果不覆盖新结果且无虚假失败提示", async () => {
    vi.useFakeTimers();
    useSearchStore.setState({ filter: "inbox" });
    const { result } = renderHook(() => useSearchActions());

    // 搜索 A：第一页挂起（模拟多页扫描进行中）
    let resolvePageA: (value: unknown) => void = () => undefined;
    const pageA = new Promise((resolve) => {
      resolvePageA = resolve;
    });
    let inboxCalls = 0;
    const inboxPage = (offset: number) => [
      {
        id: `item-${offset}`, title: `条目${offset}`, body: "", tags: [], status: "inbox", revision: 1,
        createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: []
      }
    ];
    vi.mocked(creationService.inboxList).mockImplementation(async ({ offset }) => {
      inboxCalls += 1;
      if (inboxCalls === 1) return pageA as never;
      return inboxPage(offset ?? 0);
    });

    act(() => result.current.runSearch("搜索A"));
    await act(async () => vi.advanceTimersByTime(300));
    expect(inboxCalls).toBe(1);

    // 输入搜索 B：应 abort A 的扫描
    act(() => result.current.runSearch("搜索B"));
    await act(async () => vi.advanceTimersByTime(300));
    // A 的第一页此时才返回：abort 后 A 不再请求后续分页
    await act(async () => {
      resolvePageA(inboxPage(0));
    });
    await act(async () => {});
    // A 的扫描只发生了一次 inboxList 调用（第一页挂起后不再有第二页）
    expect(inboxCalls).toBe(2); // A 第 1 页 + B 第 1 页
    // 结果只来自 B（A 的旧结果不能覆盖）
    const state = useSearchStore.getState();
    expect(state.results.every((entry) => entry.id.startsWith("inbox:item-200"))).toBe(true);
    // 取消不算来源失败：无虚假错误提示
    expect(state.error).toBeNull();
  });

  it("cancelSearch 清理防抖 timer 并取消进行中的请求", async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useSearchActions());
    vi.mocked(creationService.inboxList).mockResolvedValue([]);

    act(() => result.current.runSearch("词"));
    // 防抖期间取消：timer 被清理，300ms 后不应触发搜索
    act(() => result.current.cancelSearch());
    await act(async () => vi.advanceTimersByTime(400));
    expect(vi.mocked(creationService.inboxList)).not.toHaveBeenCalled();
    expect(useSearchStore.getState().loading).toBe(false);
  });
});

describe("useSearchActions 导航意图", () => {
  it("点击项目结果产生 project 意图并选中项目进入项目页", async () => {
    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "project", projectId: "p1" });
    });
    expect(useCreationStore.getState().selectedId).toBe("p1");
    expect(useAppStore.getState().screen).toBe("projects");
  });

  it("点击场景结果产生 scene 意图并落入项目导航 seam", async () => {
    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "scene", projectId: "p1", sceneId: "s1", chapterId: "ch1" });
    });
    expect(useCreationStore.getState().selectedId).toBe("p1");
    expect(useAppStore.getState().screen).toBe("projects");
    // openIntent 不再直接选中场景或读正文；它把请求落到 seam，
    // CreationProjectsPage 挂载/重渲染时消费后才执行 selectScene + loadScene。
    const req = useCreationStore.getState().projectNavigationRequests["p1"];
    expect(req?.target).toMatchObject({ view: "writing", sceneId: "s1" });
  });

  it("点击收件箱结果产生 inbox 意图", async () => {
    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "inbox" });
    });
    expect(useAppStore.getState().screen).toBe("inbox");
  });

  it("点击资料结果产生 book 意图并打开阅读器", async () => {
    vi.mocked(libraryService.listBooks).mockResolvedValue([
      {
        id: "b1",
        title: "资料书",
        filePath: "C:\\data\\b1.txt",
        format: "txt",
        importedAt: "",
        updatedAt: "",
        size: 10,
        revision: 1,
        deviceId: "d1"
      }
    ]);
    vi.mocked(readerService.openBook).mockResolvedValue({
      book: { id: "b1", title: "资料书", filePath: "C:\\data\\b1.txt", format: "txt", importedAt: "", updatedAt: "", size: 10, revision: 1, deviceId: "d1" },
      content: "正文",
      settings: {},
      progress: undefined
    });
    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "book", bookId: "b1" });
    });
    expect(useAppStore.getState().screen).toBe("reader");
    expect(vi.mocked(readerService.openBook)).toHaveBeenCalledWith("b1");
  });

  it("点击旧灵感结果产生 inspiration 意图", async () => {
    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "inspiration", inspirationId: "in1" });
    });
    expect(useAppStore.getState().screen).toBe("inspiration");
  });
});

describe("useSearchActions leave guard 与搜索关闭语义", () => {
  it("leaveGuard 返回 false 时：openIntent 返回 blocked，不切换页面，不产生部分导航状态", async () => {
    useAppStore.setState({ screen: "projects", errors: [] });
    useCreationStore.setState({
      selectedId: "p1",
      leaveGuard: async () => false, // 拒绝离开
      projects: [],
      navigations: {},
      projectNavigationRequests: {}
    });
    useSearchStore.setState({
      open: true,
      keyword: "测试",
      filter: "all",
      results: [
        {
          id: "scene:s1",
          source: "scene",
          title: "场景A",
          snippet: "",
          originLabel: "项目 · 项目甲",
          intent: { kind: "scene", projectId: "p1", sceneId: "s1", chapterId: "ch1" }
        }
      ],
      loading: false,
      error: null,
      projectContextId: undefined,
      allProjects: false
    });

    const { result } = renderHook(() => useSearchActions());
    let openResult: string | undefined;
    await act(async () => {
      openResult = await result.current.openResult(useSearchStore.getState().results[0]!);
    });

    // blocked：SearchPanel 必须保持打开
    expect(openResult).toBe("blocked");
    expect(useSearchStore.getState().open).toBe(true);

    // 不切换页面、不切换项目、不选中场景
    expect(useAppStore.getState().screen).toBe("projects");
    expect(useCreationStore.getState().selectedId).toBe("p1");
    expect(useCreationStore.getState().selectedSceneId).toBeUndefined();

    // 不产生部分导航请求
    expect(useCreationStore.getState().projectNavigationRequests["p1"]).toBeUndefined();

    // 搜索结果仍保留
    expect(useSearchStore.getState().results.length).toBe(1);
    expect(useSearchStore.getState().keyword).toBe("测试");
  });

  it("leaveGuard 返回 true 时：openIntent 返回 navigated，SearchPanel 关闭", async () => {
    useAppStore.setState({ screen: "projects", errors: [] });
    useCreationStore.setState({
      selectedId: "p1",
      leaveGuard: async () => true,
      projects: [],
      navigations: {},
      projectNavigationRequests: {}
    });
    useSearchStore.setState({
      open: true,
      keyword: "测试",
      filter: "all",
      results: [
        {
          id: "scene:s1",
          source: "scene",
          title: "场景A",
          snippet: "",
          originLabel: "项目 · 项目甲",
          intent: { kind: "scene", projectId: "p1", sceneId: "s1", chapterId: "ch1" }
        }
      ],
      loading: false,
      error: null,
      projectContextId: undefined,
      allProjects: false
    });

    const { result } = renderHook(() => useSearchActions());
    let openResult: string | undefined;
    await act(async () => {
      openResult = await result.current.openResult(useSearchStore.getState().results[0]!);
    });

    expect(openResult).toBe("navigated");
    expect(useSearchStore.getState().open).toBe(false);
  });

  it("非 projects 页面时 leaveGuard 不调用，直接导航", async () => {
    useAppStore.setState({ screen: "inbox", errors: [] });
    const guardMock = vi.fn(async () => false);
    useCreationStore.setState({
      selectedId: undefined,
      leaveGuard: guardMock,
      projects: [],
      navigations: {},
      projectNavigationRequests: {}
    });

    const { result } = renderHook(() => useSearchActions());
    await act(async () => {
      await result.current.openIntent({ kind: "inbox" });
    });

    expect(guardMock).not.toHaveBeenCalled();
    expect(useAppStore.getState().screen).toBe("inbox");
  });
});
