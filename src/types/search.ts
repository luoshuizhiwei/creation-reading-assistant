import type { ID } from "./common";

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
