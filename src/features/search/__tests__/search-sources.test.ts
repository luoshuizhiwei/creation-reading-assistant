import { describe, expect, it, vi, beforeEach } from "vitest";

vi.mock("@/services/creation-service", () => ({
  search: vi.fn(),
  inboxList: vi.fn()
}));
vi.mock("@/services/search-service", () => ({
  globalSearch: vi.fn()
}));
vi.mock("@/services/inspiration-service", () => ({
  listInspirations: vi.fn()
}));

import { creationSearchSource, inboxSearchSource, librarySearchSource, inspirationSearchSource, displayFileName } from "@/features/search/sources";
import { sourcesForFilter, sortEntries, SOURCE_ORDER } from "@/features/search/aggregate";
import * as creationService from "@/services/creation-service";
import * as searchService from "@/services/search-service";
import * as inspirationService from "@/services/inspiration-service";
import type { UnifiedSearchFilter } from "@/types/search";

function creationHit(overrides: Record<string, unknown>) {
  return {
    kind: "scene",
    id: "scene-1",
    projectId: "p1",
    projectTitle: "项目甲",
    title: "默认场景",
    snippet: "命中片段",
    updatedAt: "",
    ...overrides
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("统一搜索数据源", () => {
  it("创作源返回项目/章节/场景/卡片四类结果并携带正确导航意图", async () => {
    vi.mocked(creationService.search).mockResolvedValue({
      query: "关键词",
      total: 4,
      hits: [
        creationHit({ kind: "project", id: "p1", projectTitle: "项目甲", title: "项目甲" }),
        creationHit({ kind: "chapter", id: "ch1", title: "第一章" }),
        creationHit({ kind: "scene", id: "sc1", chapterId: "ch1", title: "场景A" }),
        creationHit({ kind: "card", id: "ca1", title: "苏青", cardKind: "character" })
      ]
    });
    const entries = await creationSearchSource.search({ keyword: "关键词", filter: "all", limit: 50 });
    expect(entries.map((entry) => entry.source)).toEqual(["project", "chapter", "scene", "card"]);
    expect(entries.find((entry) => entry.source === "scene")?.intent).toEqual({
      kind: "scene",
      projectId: "p1",
      sceneId: "sc1",
      chapterId: "ch1"
    });
    expect(entries.find((entry) => entry.source === "card")?.intent).toEqual({ kind: "card", projectId: "p1", cardId: "ca1" });
    expect(entries.find((entry) => entry.source === "project")?.intent).toEqual({ kind: "project", projectId: "p1" });
  });

  it("卡片筛选只请求 card scope，正文筛选只请求 scene scope", async () => {
    vi.mocked(creationService.search).mockResolvedValue({ query: "x", total: 0, hits: [] });
    await creationSearchSource.search({ keyword: "x", filter: "card", limit: 50 });
    expect(vi.mocked(creationService.search)).toHaveBeenCalledWith(expect.objectContaining({ scopes: ["card"] }));
    await creationSearchSource.search({ keyword: "x", filter: "body", limit: 50 });
    expect(vi.mocked(creationService.search)).toHaveBeenLastCalledWith(expect.objectContaining({ scopes: ["scene"] }));
    await creationSearchSource.search({ keyword: "x", filter: "all", limit: 50 });
    expect(vi.mocked(creationService.search)).toHaveBeenLastCalledWith(expect.objectContaining({ scopes: undefined }));
  });

  it("项目上下文透传到创作搜索，且限定当前项目", async () => {
    vi.mocked(creationService.search).mockResolvedValue({ query: "x", total: 0, hits: [] });
    await creationSearchSource.search({ keyword: "x", projectId: "p7", filter: "all", limit: 50 });
    expect(vi.mocked(creationService.search)).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: "p7", text: "x" })
    );
  });

  it("收件箱源匹配标题/正文/标签，used 条目排除，intent 指向收件箱", async () => {
    vi.mocked(creationService.inboxList).mockResolvedValue([
      { id: "i1", title: "灵感A", body: "正文", tags: [], status: "inbox", revision: 1, createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: [] },
      { id: "i2", title: "灵感B", body: "内容", tags: ["主角"], status: "used", revision: 1, createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: [] },
      { id: "i3", title: "无关", body: "别的", tags: [], status: "inbox", revision: 1, createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: [] }
    ]);
    const entries = await inboxSearchSource.search({ keyword: "灵感", filter: "inbox", limit: 50 });
    expect(entries.map((entry) => entry.id)).toEqual(["inbox:i1"]);
    // 收件箱结果必须携带真实条目 ID，供 InboxPage 精确选中。
    expect(entries[0]?.intent).toEqual({ kind: "inbox", inboxItemId: "i1" });
  });

  it("收件箱源逐页扫描，第 1001 条之后的条目仍可被命中", async () => {
    // 模拟 1200 条数据，分 6 页返回（每页 200 条）
    // 第 1001 条之后的条目必须可被搜索命中——这是修复硬上限的核心验证点。
    vi.mocked(creationService.inboxList).mockImplementation(async (query) => {
      const offset = query.offset ?? 0;
      const limit = query.limit ?? 200;
      const page: Array<{ id: string; title: string; body: string; tags: string[]; status: string; revision: number; createdAt: string; updatedAt: string; legacyId: string | null; type: string; platformTags: string[]; source: unknown; variants: unknown[] }> = [];
      for (let i = 0; i < limit; i++) {
        const idx = offset + i;
        if (idx >= 1200) break;
        // 只有第 1050 条命中关键词 "独特关键词"
        const title = idx === 1049 ? "独特关键词的条目" : `普通条目${idx}`;
        page.push({
          id: `item-${idx}`,
          title,
          body: `内容${idx}`,
          tags: [],
          status: "inbox",
          revision: 1,
          createdAt: "",
          updatedAt: "",
          legacyId: null,
          type: "note",
          platformTags: [],
          source: null,
          variants: []
        });
      }
      return page;
    });

    const entries = await inboxSearchSource.search({ keyword: "独特关键词", filter: "inbox", limit: 50 });
    // 必须命中第 1050 条（offset 1049，在 1000 之后）
    expect(entries.length).toBe(1);
    expect(entries[0]?.id).toBe("inbox:item-1049");
    expect(entries[0]?.intent).toEqual({ kind: "inbox", inboxItemId: "item-1049" });
    // 验证确实进行了多页扫描（至少 6 次 inboxList 调用）
    expect(vi.mocked(creationService.inboxList).mock.calls.length).toBeGreaterThanOrEqual(6);
  });

  it("资料书库源结果来源标签只含文件名，绝不泄漏绝对路径", async () => {
    vi.mocked(searchService.globalSearch).mockResolvedValue([
      {
        id: "r1",
        type: "book",
        title: "测试书籍",
        snippet: "片段",
        score: 5,
        sourcePath: "C:\\Users\\someone\\AppData\\Roaming\\App\\data\\AppLibrary\\files\\测试书籍.txt",
        target: { bookId: "b1" }
      }
    ]);
    const entries = await librarySearchSource.search({ keyword: "书籍", filter: "library", limit: 50 });
    expect(entries[0]?.originLabel).toBe("书库 · 测试书籍.txt");
    expect(entries[0]?.originLabel).not.toContain("C:\\");
    expect(entries[0]?.intent).toEqual({ kind: "book", bookId: "b1" });
  });

  it("displayFileName 对任意分隔符路径只返回文件名", () => {
    expect(displayFileName("C:\\a\\b\\c.txt")).toBe("c.txt");
    expect(displayFileName("/home/user/dir/书.md")).toBe("书.md");
    expect(displayFileName(undefined)).toBeUndefined();
  });

  it("旧灵感源标记待迁移且 intent 指向灵感中心", async () => {
    vi.mocked(inspirationService.listInspirations).mockResolvedValue([
      { id: "in1", title: "旧笔记", body: "内容", tags: ["灵感"], type: "note", status: "inbox", platformTags: [], variants: [], revision: 1, deviceId: "d1", createdAt: "", updatedAt: "" }
    ]);
    const entries = await inspirationSearchSource.search({ keyword: "旧笔记", filter: "all", limit: 50 });
    expect(entries[0]?.pendingMigration).toBe(true);
    expect(entries[0]?.originLabel).toBe("旧灵感 · 待迁移");
    expect(entries[0]?.intent).toEqual({ kind: "inspiration", inspirationId: "in1" });
  });

  it("取消后收件箱源停止后续分页扫描，且取消不算失败", async () => {
    const controller = new AbortController();
    let calls = 0;
    // 第一页满 200 条（继续翻页）；第二页请求发出时 abort（模拟多页扫描中途取消）
    vi.mocked(creationService.inboxList).mockImplementation(async () => {
      calls += 1;
      if (calls === 2) controller.abort();
      const page: InboxItem[] = [];
      for (let i = 0; i < 200; i += 1) {
        page.push({
          id: `item-${calls}-${i}`, title: `普通条目${calls}-${i}`, body: "", tags: [], status: "inbox",
          revision: 1, createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: []
        });
      }
      return page;
    });

    const entries = await inboxSearchSource.search({ keyword: "命中", filter: "inbox", limit: 50, signal: controller.signal });

    // 第二页返回后 abort：第三页不再请求
    expect(calls).toBe(2);
    // 取消是正常结束：返回数组（可能为空），而不是抛错
    expect(Array.isArray(entries)).toBe(true);
  });

  it("搜索进行中收到 abort 信号时（页间挂起期间）同样停止且不抛错", async () => {
    const controller = new AbortController();
    let resolvePage: (value: unknown) => void = () => undefined;
    const firstPage = new Promise((resolve) => {
      resolvePage = resolve;
    });
    vi.mocked(creationService.inboxList).mockReturnValueOnce(firstPage as never);

    const searchPromise = inboxSearchSource.search({ keyword: "x", filter: "inbox", limit: 50, signal: controller.signal });
    // 第一页请求挂起期间取消
    controller.abort();
    await act0();
    resolvePage([{
      id: "i1", title: "条目", body: "", tags: [], status: "inbox", revision: 1,
      createdAt: "", updatedAt: "", legacyId: null, type: "note", platformTags: [], source: null, variants: []
    }]);
    const entries = await searchPromise;
    // 页间检查发现 aborted：不再请求第二页，正常返回
    expect(vi.mocked(creationService.inboxList)).toHaveBeenCalledTimes(1);
    expect(Array.isArray(entries)).toBe(true);
  });
});

async function act0(): Promise<void> {
  await Promise.resolve();
}

describe("统一搜索聚合", () => {
  it("筛选 → 数据源映射完整", () => {
    const cases: Array<[UnifiedSearchFilter, string[]]> = [
      ["all", ["创作项目", "全局收件箱", "资料书库", "旧灵感（待迁移）"]],
      ["project", ["创作项目"]],
      ["body", ["创作项目", "资料书库"]],
      ["card", ["创作项目"]],
      ["inbox", ["全局收件箱"]],
      ["library", ["资料书库"]]
    ];
    for (const [filter, labels] of cases) {
      expect(sourcesForFilter(filter).map((source) => source.label)).toEqual(labels);
    }
  });

  it("排序按固定来源顺序：项目、章节、场景、卡片、收件箱、资料、旧灵感", () => {
    const entries = [
      { source: "inbox" as const },
      { source: "scene" as const },
      { source: "project" as const },
      { source: "inspiration" as const },
      { source: "card" as const },
      { source: "chapter" as const },
      { source: "library" as const }
    ];
    sortEntries(entries);
    expect(entries.map((entry) => entry.source)).toEqual(SOURCE_ORDER);
  });
});
