import type { ID } from "./common";

/** 统一搜索的筛选维度（界面上的筛选按钮）。 */
export type UnifiedSearchFilter = "all" | "project" | "body" | "card" | "inbox" | "library";

/** 结果来源分组标识。 */
export type UnifiedSearchSourceId = "project" | "chapter" | "scene" | "card" | "inbox" | "library" | "inspiration";

/** 点击结果后产生的结构化导航意图：UI 不直接耦合具体页面，由解释器执行。 */
export interface SearchNavigateIntent {
  kind: "project" | "chapter" | "scene" | "card" | "inbox" | "book" | "inspiration";
  projectId?: ID;
  chapterId?: ID;
  sceneId?: ID;
  cardId?: ID;
  bookId?: ID;
  epubHref?: string;
  inspirationId?: ID;
  /** 收件箱结果携带的真实条目 ID；InboxPage 据此选中目标条目。 */
  inboxItemId?: ID;
}

/** 统一搜索的一条结果：来自任一数据源，附导航意图与来源标签。 */
export interface UnifiedSearchEntry {
  /** 稳定唯一 key（source:entityId）。 */
  id: string;
  source: UnifiedSearchSourceId;
  title: string;
  snippet: string;
  projectId?: ID;
  projectTitle?: string;
  /** 人类可读来源标签（只含文件名/书名等相对信息，绝不泄漏绝对路径）。 */
  originLabel: string;
  /** 旧灵感兼容期标记：尚未迁移到收件箱。 */
  pendingMigration?: boolean;
  intent: SearchNavigateIntent;
}

/** 兼容保留：旧全局搜索（library/inspiration）IPC 的查询与结果类型。 */
export type SearchScope = "library" | "inspiration";
export type SearchResultType = "book" | "inspiration";

export interface SearchQuery {
  keyword: string;
  scopes?: SearchScope[];
  limit?: number;
}

export interface SearchTarget {
  bookId?: ID;
  epubHref?: string;
  inspirationId?: ID;
}

export interface SearchResult {
  id: ID;
  type: SearchResultType;
  title: string;
  snippet: string;
  score: number;
  sourcePath?: string;
  target: SearchTarget;
}
