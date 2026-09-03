import {
  inboxList,
  search as creationSearch
} from "@/services/creation-service";
import { globalSearch } from "@/services/search-service";
import { listInspirations } from "@/services/inspiration-service";
import type {
  CreationSearchHit,
  InboxItem
} from "@/types/creation";
import type {
  SearchNavigateIntent,
  UnifiedSearchEntry,
  UnifiedSearchFilter,
  UnifiedSearchSourceId
} from "@/types/search";

/** 传给每个数据源的统一输入。 */
export interface SearchSourceInput {
  keyword: string;
  /** 当前项目上下文；undefined = 全部项目。 */
  projectId?: string;
  limit: number;
  filter: UnifiedSearchFilter;
  /** 可选取消信号：abort 后源应立即停止继续扫描（取消是正常结束，不是失败）。 */
  signal?: AbortSignal;
}

/** 统一搜索数据源：一个源负责一类数据，失败不影响其他源。 */
export interface SearchSource {
  id: UnifiedSearchSourceId;
  label: string;
  search(input: SearchSourceInput): Promise<UnifiedSearchEntry[]>;
}

/** 仅取文件名（绝不展示绝对路径）。 */
export function displayFileName(sourcePath?: string): string | undefined {
  if (!sourcePath) return undefined;
  return sourcePath.split(/[\\/]/).filter(Boolean).pop();
}

function intentForHit(hit: CreationSearchHit): SearchNavigateIntent {
  switch (hit.kind) {
    case "project":
      return { kind: "project", projectId: hit.id };
    case "chapter":
      return { kind: "chapter", projectId: hit.projectId, chapterId: hit.id };
    case "scene":
      return { kind: "scene", projectId: hit.projectId, sceneId: hit.id, chapterId: hit.chapterId };
    case "card":
      return { kind: "card", projectId: hit.projectId, cardId: hit.id };
  }
}

function entryFromHit(hit: CreationSearchHit): UnifiedSearchEntry {
  const projectTitle = hit.projectTitle || hit.projectId;
  const originLabel =
    hit.kind === "project"
      ? `项目 · ${hit.title}`
      : hit.kind === "card"
        ? `项目 · ${projectTitle} · 卡片`
        : `项目 · ${projectTitle}`;
  return {
    id: `${hit.kind}:${hit.id}`,
    source: hit.kind as UnifiedSearchSourceId,
    title: hit.kind === "project" ? hit.projectTitle || hit.title : hit.title,
    snippet: hit.snippet ?? "",
    projectId: hit.projectId,
    projectTitle: projectTitle,
    originLabel,
    intent: intentForHit(hit)
  };
}

/** 创作工作区源：项目/章节/场景正文/卡片（标题、别名、字段、标签）。 */
export const creationSearchSource: SearchSource = {
  id: "project",
  label: "创作项目",
  async search({ keyword, projectId, filter, limit }) {
    const scopes =
      filter === "card" ? (["card"] as const) : filter === "body" ? (["scene"] as const) : undefined;
    const view = await creationSearch({
      kind: "search.query",
      text: keyword,
      projectId,
      scopes: scopes ? [...scopes] : undefined,
      limit
    });
    return view.hits.map(entryFromHit);
  }
};

function entryFromInbox(item: InboxItem): UnifiedSearchEntry {
  return {
    id: `inbox:${item.id}`,
    source: "inbox",
    title: item.title,
    snippet: item.body,
    originLabel: "全局收件箱",
    intent: { kind: "inbox", inboxItemId: item.id }
  };
}

/**
 * 收件箱搜索分页大小。
 *
 * 不再使用固定上限（旧值 200/1000 会把后续条目变成永久不可搜索）。
 * 这里只是单次 IPC 的页大小，搜索源会按 offset 逐页扫描直到耗尽，
 * 确保第 N 条之后的条目仍能被命中。
 */
const INBOX_SEARCH_PAGE_SIZE = 200;

/** 收件箱搜索的上限条数：保护 UI 一次性渲染过多结果，但不影响是否命中。 */
const INBOX_SEARCH_RESULT_CAP = 200;

function inboxItemMatches(item: InboxItem, lower: string): boolean {
  if (item.status === "used") return false;
  return (
    item.title.toLowerCase().includes(lower) ||
    item.body.toLowerCase().includes(lower) ||
    item.tags.some((tag) => tag.toLowerCase().includes(lower))
  );
}

/**
 * 全局收件箱源：条目标题/正文/标签匹配。
 *
 * 实现：按 offset 分页逐页读取收件箱并在客户端过滤；
 * 不存在任何固定条数上限——第 N 条之后的条目仍可被搜索命中。
 * 单页读取（INBOX_SEARCH_PAGE_SIZE），避免一次无界加载全部数据。
 */
export const inboxSearchSource: SearchSource = {
  id: "inbox",
  label: "全局收件箱",
  async search({ keyword, limit, signal }) {
    const lower = keyword.toLowerCase();
    const max = Math.min(limit > 0 ? limit : INBOX_SEARCH_RESULT_CAP, INBOX_SEARCH_RESULT_CAP);
    const matched: InboxItem[] = [];
    let offset = 0;
    // 逐页扫描直到没有更多条目，或已收集到足够结果。
    // 每个条目都会被检查；offset 超过 1000、10000 同样覆盖。
    while (matched.length < max) {
      // 取消属于正常结束：立即停止后续分页，不把取消当成来源失败。
      if (signal?.aborted) return matched.map(entryFromInbox);
      const page = await inboxList({ kind: "inbox.list", limit: INBOX_SEARCH_PAGE_SIZE, offset });
      // 页间再检查一次：页面返回期间可能已被取消。
      if (signal?.aborted) return matched.map(entryFromInbox);
      if (page.length === 0) break;
      for (const item of page) {
        if (signal?.aborted) return matched.map(entryFromInbox);
        if (inboxItemMatches(item, lower)) {
          matched.push(item);
          if (matched.length >= max) break;
        }
      }
      // 不足一页 → 已到末尾
      if (page.length < INBOX_SEARCH_PAGE_SIZE) break;
      offset += page.length;
    }
    return matched.map(entryFromInbox);
  }
};

/** 资料书库源：本地书籍标题/作者/正文索引匹配（绝对路径不展示）。 */
export const librarySearchSource: SearchSource = {
  id: "library",
  label: "资料书库",
  async search({ keyword, limit }) {
    const results = await globalSearch({ keyword, scopes: ["library"], limit });
    return results.map((result) => ({
      id: `library:${result.target.bookId}:${result.id}`,
      source: "library" as const,
      title: result.title,
      snippet: result.snippet,
      originLabel: result.sourcePath
        ? `书库 · ${displayFileName(result.sourcePath) ?? "资料"}`
        : "书库资料",
      intent: {
        kind: "book" as const,
        bookId: result.target.bookId,
        epubHref: result.target.epubHref,
        charOffset: result.target.charOffset
      }
    }));
  }
};

function entryFromInspiration(item: { id: string; title: string; body: string; tags: string[] }): UnifiedSearchEntry {
  return {
    id: `inspiration:${item.id}`,
    source: "inspiration",
    title: item.title,
    snippet: item.body,
    originLabel: "旧灵感 · 待迁移",
    pendingMigration: true,
    intent: { kind: "inspiration", inspirationId: item.id }
  };
}

/**
 * 旧灵感源（兼容期）：尚未迁移到收件箱。
 * 已从 sourcesForFilter("all") 摘除——旧灵感页只能提示"去收件箱"，无法定位
 * 命中条目，出现在结果里是断链体验；保留定义供数据迁移前的回归测试。
 */
export const inspirationSearchSource: SearchSource = {
  id: "inspiration",
  label: "旧灵感（待迁移）",
  async search({ keyword, limit }) {
    const lower = keyword.toLowerCase();
    const items = await listInspirations();
    const matched = items.filter(
      (item) =>
        item.title.toLowerCase().includes(lower) ||
        item.body.toLowerCase().includes(lower) ||
        item.tags.some((tag) => tag.toLowerCase().includes(lower))
    );
    return matched.slice(0, limit).map(entryFromInspiration);
  }
};
