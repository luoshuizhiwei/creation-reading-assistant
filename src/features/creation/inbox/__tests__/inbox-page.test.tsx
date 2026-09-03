// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { InboxPage } from "@/features/creation/inbox/InboxPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";

vi.mock("@/services/creation-service", () => ({
  inboxList: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  inboxCreate: vi.fn(),
  cardsList: vi.fn(),
  runStructure: vi.fn(),
  listProjects: vi.fn(),
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

const projects = [
  { id: "p1", title: "项目A", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 },
  { id: "p2", title: "项目B", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 }
];

const item = {
  id: "i1", legacyId: null, title: "测试灵感", body: "测试内容", type: "note", status: "inbox",
  tags: [], platformTags: [], source: null, variants: [], revision: 1,
  createdAt: "", updatedAt: ""
} as const;

function resetStores(): void {
  useCreationStore.setState({ projects, cards: [], selectedCardId: undefined });
  useUIStore.setState({ toasts: [] });
}

beforeEach(() => {
  resetStores();
  vi.mocked(creationService.inboxList).mockReset();
  vi.mocked(creationService.inboxList).mockResolvedValue([]);
  vi.mocked(creationService.inboxCount).mockResolvedValue({ total: 0, pending: 0 });
  vi.mocked(creationService.runStructure).mockReset();
});
afterEach(() => cleanup());

describe("InboxPage 目标项目同步", () => {
  it("无 projectId 且多个项目时，显示下拉选择器且默认为第一个", async () => {
    render(<InboxPage />);
    await waitFor(() => {
      const select = screen.getByRole("combobox") as HTMLSelectElement;
      expect(select.value).toBe("p1");
    });
  });

  it("指定 projectId 时不显示下拉（由父级固定目标）", async () => {
    render(<InboxPage projectId="p2" />);
    await waitFor(() => {
      expect(screen.queryByRole("combobox")).toBeNull();
    });
  });

  it("无项目时，点击转卡按钮不触发 runStructure", async () => {
    useCreationStore.setState({ projects: [], cards: [], selectedCardId: undefined });
    vi.mocked(creationService.inboxList).mockResolvedValue([item]);
    render(<InboxPage />);
    await waitFor(() => {
      expect(screen.getByText("测试灵感")).toBeDefined();
    });
    vi.mocked(creationService.runStructure).mockClear();
    const convertButton = screen.getByText("转为资料卡");
    fireEvent.click(convertButton);
    expect(vi.mocked(creationService.runStructure)).not.toHaveBeenCalled();
  });

  it("projectId 重渲染后使用新的固定目标项目", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([item]);
    vi.mocked(creationService.runStructure).mockResolvedValue({
      commandType: "inbox.convertToCard",
      sequence: 1,
      cardId: "c2",
      itemId: item.id,
      revision: 2,
      updatedAt: ""
    });
    const view = render(<InboxPage projectId="p1" />);
    await screen.findByText("测试灵感");
    view.rerender(<InboxPage projectId="p2" />);

    fireEvent.click(screen.getByText("转为资料卡"));

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledWith(expect.objectContaining({ projectId: "p2" })));
  });

  it("原子转卡成功后仅显示成功提示并刷新收件箱", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([item]);
    vi.mocked(creationService.runStructure).mockResolvedValue({
      commandType: "inbox.convertToCard",
      sequence: 1,
      cardId: "c1",
      itemId: item.id,
      revision: 2,
      updatedAt: ""
    });
    render(<InboxPage projectId="p1" />);
    await screen.findByText("测试灵感");

    fireEvent.click(screen.getByText("转为资料卡"));

    await waitFor(() => expect(useUIStore.getState().toasts.some((toast) => toast.title === "已转为资料卡")).toBe(true));
    expect(useUIStore.getState().toasts.some((toast) => toast.tone === "error")).toBe(false);
    expect(creationService.inboxList).toHaveBeenCalledTimes(2);
  });

  it("原子转卡失败时不显示成功提示，保留条目并给出上下文错误", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([item]);
    vi.mocked(creationService.runStructure).mockRejectedValue(new Error("条目版本冲突"));
    render(<InboxPage projectId="p1" />);
    await screen.findByText("测试灵感");

    fireEvent.click(screen.getByText("转为资料卡"));

    await waitFor(() => expect(useUIStore.getState().toasts.some((toast) => toast.title === "转卡失败")).toBe(true));
    expect(useUIStore.getState().toasts.some((toast) => toast.title === "已转为资料卡")).toBe(false);
    expect(screen.getByText("测试灵感")).toBeDefined();
    expect(creationService.inboxList).toHaveBeenCalledTimes(2);
  });

  it("同一条目转卡进行中时禁止重复提交", async () => {
    let resolve!: (value: Awaited<ReturnType<typeof creationService.runStructure>>) => void;
    vi.mocked(creationService.inboxList).mockResolvedValue([item]);
    vi.mocked(creationService.runStructure).mockReturnValue(new Promise((done) => { resolve = done; }));
    render(<InboxPage projectId="p1" />);
    await screen.findByText("测试灵感");
    const button = screen.getByText("转为资料卡").closest("button")!;

    fireEvent.click(button);
    fireEvent.click(button);

    expect(creationService.runStructure).toHaveBeenCalledTimes(1);
    expect(button).toHaveProperty("disabled", true);
    resolve({
      commandType: "inbox.convertToCard",
      sequence: 1,
      cardId: "c1",
      itemId: item.id,
      revision: 2,
      updatedAt: ""
    });
    await waitFor(() => expect(creationService.inboxList).toHaveBeenCalledTimes(2));
  });
});

describe("InboxPage 搜索结果选中消费（seam）", () => {
  it("外部搜索请求 InboxPage 选中指定条目并消费请求", async () => {
    const items = [
      { id: "i1", legacyId: null, title: "条目一", body: "内容一", type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [], revision: 1, createdAt: "", updatedAt: "" },
      { id: "i2", legacyId: null, title: "条目二", body: "内容二", type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [], revision: 1, createdAt: "", updatedAt: "" }
    ];
    vi.mocked(creationService.inboxList).mockResolvedValue(items);
    // 模拟统一搜索发出的选中请求
    useCreationStore.setState({ projects, inboxSelectionRequest: "i2" });

    render(<InboxPage />);
    // 条目二被选中（refresh 和 loadAndSelect 可能并发导致条目重复，用 getAllByText）
    await waitFor(() => {
      const elements = screen.getAllByText("条目二");
      const selectedLi = elements.find((el) => el.closest("li")?.className.includes("inbox-item--selected"));
      expect(selectedLi).toBeDefined();
    });
    // 请求被消费后清除
    expect(useCreationStore.getState().inboxSelectionRequest).toBeUndefined();
  });

  it("目标条目不存在时给出明确提示", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([
      { id: "i1", legacyId: null, title: "条目一", body: "内容", type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [], revision: 1, createdAt: "", updatedAt: "" }
    ]);
    useCreationStore.setState({ projects, inboxSelectionRequest: "i-missing" });

    render(<InboxPage />);
    await waitFor(() => expect(useUIStore.getState().toasts.some((t) => t.title === "目标条目不可用")).toBe(true));
    // 请求被消费后清除（即使没找到也清除，避免重复弹提示）
    expect(useCreationStore.getState().inboxSelectionRequest).toBeUndefined();
  });
});

describe("InboxPage 分页（移除 200 条上限）", () => {
  it("超过 200 条时提供加载更多按钮并分页加载", async () => {
    // 模拟 250 条数据，分两页返回（200 + 50）
    const firstPage = Array.from({ length: 200 }, (_, index) => ({
      id: `item-${index}`, legacyId: null, title: `条目${index}`, body: `内容${index}`,
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));
    const secondPage = Array.from({ length: 50 }, (_, index) => ({
      id: `item-${200 + index}`, legacyId: null, title: `条目${200 + index}`, body: `内容${200 + index}`,
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));

    vi.mocked(creationService.inboxList)
      .mockResolvedValueOnce(firstPage)   // offset=0, limit=200
      .mockResolvedValueOnce(secondPage); // offset=200, limit=200

    render(<InboxPage />);
    // 第一页加载
    await screen.findByText("条目0");
    await waitFor(() => expect(screen.getByText((content) => content.includes("200 条"))).toBeDefined());
    // 加载更多按钮可见
    const loadMoreButton = screen.getByText("加载更多");
    expect(loadMoreButton).toBeDefined();

    fireEvent.click(loadMoreButton);
    // 第二页加载后，第 201 条条目可见
    await screen.findByText("条目200");
    // hasMore 变为 false 后文案变为"已加载全部 250 条"
    await waitFor(() => expect(screen.getByText((content) => content.includes("250 条"))).toBeDefined());
    expect(screen.queryByText("加载更多")).toBeNull();
  });

  it("直接导航到第 201 条后的条目仍能加载并选中", async () => {
    // 场景：搜索结果指向 offset=200 之后的条目
    const firstPage = Array.from({ length: 200 }, (_, index) => ({
      id: `item-${index}`, legacyId: null, title: `前段条目${index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));
    const secondPage = Array.from({ length: 50 }, (_, index) => ({
      id: `item-${200 + index}`, legacyId: null, title: `后段条目${200 + index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));

    vi.mocked(creationService.inboxList)
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(secondPage);

    // 搜索请求选中第 220 条
    useCreationStore.setState({ projects, inboxSelectionRequest: "item-219" });

    render(<InboxPage />);
    // 第 220 条条目被选中（用 findByText 等待渲染完成）
    await waitFor(() => {
      const elements = screen.queryAllByText("后段条目219");
      const selectedLi = elements.find((el) => el.closest("li")?.className.includes("inbox-item--selected"));
      expect(selectedLi).toBeDefined();
    }, { timeout: 3000 });
    expect(useCreationStore.getState().inboxSelectionRequest).toBeUndefined();
  });

  it("深链分页 offset 序列必须为 0→200→400…，不允许 0→0→200（旧闭包重复读第一页）", async () => {
    // 目标在第 450 条之后：需要 3 页（0 / 200 / 400）才能命中
    const makePage = (start: number, count: number) =>
      Array.from({ length: count }, (_, index) => ({
        id: `item-${start + index}`, legacyId: null, title: `条目${start + index}`, body: "",
        type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
        revision: 1, createdAt: "", updatedAt: ""
      }));
    const offsets: number[] = [];
    vi.mocked(creationService.inboxList).mockImplementation(async ({ offset }) => {
      offsets.push(offset ?? 0);
      if ((offset ?? 0) === 0) return makePage(0, 200);
      if ((offset ?? 0) === 200) return makePage(200, 200);
      return makePage(400, 80); // 第 450 条在第三页
    });

    useCreationStore.setState({ projects, inboxSelectionRequest: "item-449" });
    render(<InboxPage />);

    await waitFor(() => {
      const elements = screen.queryAllByText("条目449");
      const selectedLi = elements.find((el) => el.closest("li")?.className.includes("inbox-item--selected"));
      expect(selectedLi).toBeDefined();
    }, { timeout: 3000 });

    // 关键断言：offset 序列严格为 0 → 200 → 400（第一页只读一次，深链从 200 继续）
    expect(offsets).toEqual([0, 200, 400]);
    expect(useCreationStore.getState().inboxSelectionRequest).toBeUndefined();
  });

  it("深链 offset 闭包回归：refresh 完成后 loadAndSelect 必须从实际 nextOffset 继续", async () => {
    // 旧 bug：mount effect 闭包捕获 nextOffset=0，refresh 完成后深链从 0 重读第一页 → 0→0→200
    const offsets: number[] = [];
    const firstPage = Array.from({ length: 200 }, (_, index) => ({
      id: `item-${index}`, legacyId: null, title: `前段${index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));
    const secondPage = Array.from({ length: 50 }, (_, index) => ({
      id: `item-${200 + index}`, legacyId: null, title: `后段${200 + index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));
    // 第一页延迟返回（真实延迟），确保 refresh 完成后深链才启动
    let resolveFirst!: (value: unknown) => void;
    const firstPromise = new Promise((resolve) => {
      resolveFirst = resolve;
    });
    vi.mocked(creationService.inboxList).mockImplementation(async ({ offset }) => {
      offsets.push(offset ?? 0);
      if ((offset ?? 0) === 0) return firstPromise as never;
      return secondPage;
    });

    useCreationStore.setState({ projects, inboxSelectionRequest: "item-219" });
    render(<InboxPage />);

    await act(async () => {
      resolveFirst(firstPage);
    });
    await waitFor(() => {
      const elements = screen.queryAllByText("后段219");
      const selectedLi = elements.find((el) => el.closest("li")?.className.includes("inbox-item--selected"));
      expect(selectedLi).toBeDefined();
    }, { timeout: 3000 });

    // offset 序列必须为 [0, 200]：第一页只读一次，深链从 200 继续（不得出现 [0, 0, 200]）
    expect(offsets).toEqual([0, 200]);
  });

  it("刷新和深链同时发生时不产生 duplicate-key（按 id 去重）", async () => {
    // 场景：mount 时 refresh 和 loadAndSelect 并发执行
    // 模拟同一个 offset 0 的响应被调用两次，但合并后只保留唯一条目
    const firstPage = Array.from({ length: 200 }, (_, index) => ({
      id: `item-${index}`, legacyId: null, title: `条目${index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));

    vi.mocked(creationService.inboxList).mockResolvedValue(firstPage);
    // 深链请求第一条（已在第一页内）
    useCreationStore.setState({ projects, inboxSelectionRequest: "item-0" });

    render(<InboxPage />);
    // 第一页命中 → 选中 item-0
    await waitFor(() => {
      const elements = screen.getAllByText("条目0");
      const selectedLi = elements.find((el) => el.closest("li")?.className.includes("inbox-item--selected"));
      expect(selectedLi).toBeDefined();
    });
    // 列表里每个 id 只出现一次（无 duplicate-key 警告）
    const listItems = screen.getAllByRole("listitem");
    const itemElements = listItems.filter((li) => li.textContent?.includes("条目"));
    // 第一页只有 200 条 item，不会因为并发深链重复
    expect(itemElements.length).toBeLessThanOrEqual(200);
  });

  it("真实乱序：深链分页与后续刷新相反顺序完成，旧响应不覆盖新状态且无重复 ID", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const firstPage = Array.from({ length: 200 }, (_, index) => ({
      id: `item-${index}`, legacyId: null, title: `前段条目${index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));
    const secondPage = Array.from({ length: 50 }, (_, index) => ({
      id: `item-${200 + index}`, legacyId: null, title: `后段条目${200 + index}`, body: "",
      type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [],
      revision: 1, createdAt: "", updatedAt: ""
    }));

    let resolveRefresh1!: (value: typeof firstPage) => void;
    let resolveDeep!: (value: typeof secondPage) => void;
    let resolveRefresh2!: (value: unknown) => void;
    let callIndex = 0;
    vi.mocked(creationService.inboxList).mockImplementation(async () => {
      callIndex += 1;
      if (callIndex === 1) {
        // 首屏刷新（offset 0）挂起
        return new Promise((resolve) => {
          resolveRefresh1 = resolve as never;
        });
      }
      if (callIndex === 2) {
        // 深链分页（offset 200）挂起
        return new Promise((resolve) => {
          resolveDeep = resolve as never;
        });
      }
      // 后续刷新挂起
      return new Promise((resolve) => {
        resolveRefresh2 = resolve as never;
      });
    });
    vi.mocked(creationService.inboxCreate).mockResolvedValue({
      commandType: "inbox.create",
      sequence: 1,
      itemId: "new-item-1",
      revision: 1,
      updatedAt: ""
    });

    useCreationStore.setState({ projects, inboxSelectionRequest: "item-219" });
    render(<InboxPage />);

    // 1. 首屏 refresh 挂起；释放第一页（不含目标）→ 深链定位开始（第二页挂起）
    await act(async () => {
      resolveRefresh1(firstPage);
    });
    await act(async () => {});
    await waitFor(() => expect(callIndex).toBeGreaterThanOrEqual(2));

    // 2. 深链第二页仍挂起时，用户触发新刷新（点击新建想法 → handleCreate → refresh）
    fireEvent.click(screen.getByText("新建想法"));
    await waitFor(() => expect(callIndex).toBeGreaterThanOrEqual(3));

    // 3. 新刷新先完成（新状态）
    const freshList = [{ id: "new-item-1", legacyId: null, title: "新想法", body: "", type: "note", status: "inbox", tags: [], platformTags: [], source: null, variants: [], revision: 1, createdAt: "", updatedAt: "" }];
    await act(async () => {
      resolveRefresh2(freshList);
    });
    await act(async () => {});
    expect(screen.getAllByText("新想法").length).toBe(1);

    // 4. 深链分页后完成：旧响应必须被丢弃（不能覆盖新列表、不能混入旧条目）
    await act(async () => {
      resolveDeep(secondPage);
    });
    await act(async () => {});
    await act(async () => {});

    // 最终列表只含新刷新内容：无后段条目、无重复 ID
    expect(screen.queryByText("后段条目219")).toBeNull();
    const listItems = screen.getAllByRole("listitem");
    const rendered = listItems.filter((li) => li.querySelector(".inbox-item-title"));
    const ids = rendered.map((li) => li.querySelector(".inbox-item-title")?.textContent);
    expect(new Set(ids).size).toBe(ids.length);
    // 旧深链响应被取消：选中的是新刷新创建的新条目，而不是旧深链目标 item-219
    const selected = listItems.find((li) => li.className.includes("inbox-item--selected"));
    expect(selected).toBeDefined();
    expect(selected?.querySelector(".inbox-item-title")?.textContent).toBe("新想法");
    // 无 duplicate-key 等 console.error
    const errorMessages = consoleError.mock.calls.map((call) => String(call[0]));
    expect(errorMessages.some((message) => message.includes("duplicate"))).toBe(false);
    consoleError.mockRestore();
  });
});

describe("InboxPage 状态分组筛选", () => {
  it("状态筛选 chip 显示各状态计数，点击后列表只显示对应状态条目", async () => {
    const items = [
      { ...item, id: "i-inbox-1", title: "未整理A", status: "inbox" },
      { ...item, id: "i-used-1", title: "已使用B", status: "used" },
      { ...item, id: "i-archived-1", title: "归档C", status: "archived" },
      { ...item, id: "i-inbox-2", title: "未整理D", status: "inbox" }
    ];
    vi.mocked(creationService.inboxList).mockResolvedValue(items as never);
    render(<InboxPage />);
    await waitFor(() => expect(screen.getByText("未整理A")).toBeDefined());

    // 全部 chip 显示总数 4
    const allChip = screen.getByRole("button", { name: /全部/ });
    expect(allChip.textContent).toMatch(/4/);
    expect(screen.getByText("已使用B")).toBeDefined();
    expect(screen.getByText("归档C")).toBeDefined();

    // 点击"待处理"：只剩两条
    fireEvent.click(screen.getByRole("button", { name: /待处理/ }));
    await waitFor(() => expect(screen.queryByText("已使用B")).toBeNull());
    expect(screen.queryByText("归档C")).toBeNull();
    expect(screen.getByText("未整理A")).toBeDefined();
    expect(screen.getByText("未整理D")).toBeDefined();

    // 点击"已转卡片"：只剩一条
    fireEvent.click(screen.getByRole("button", { name: /已转卡片/ }));
    await waitFor(() => expect(screen.queryByText("未整理A")).toBeNull());
    expect(screen.getByText("已使用B")).toBeDefined();

    // 切回全部
    fireEvent.click(screen.getByRole("button", { name: /全部/ }));
    await waitFor(() => expect(screen.getByText("未整理A")).toBeDefined());
    expect(screen.getByText("已使用B")).toBeDefined();
  });

  it("筛选下无条目时显示空提示，且不与收件箱为空混淆", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([{ ...item, id: "i1", status: "used" }] as never);
    render(<InboxPage />);
    await waitFor(() => expect(screen.getByText("测试灵感")).toBeDefined());
    fireEvent.click(screen.getByRole("button", { name: /归档/ }));
    expect(screen.getByText(/当前筛选下没有条目/)).toBeDefined();
  });
});
