import { getDesktopApi } from "@/services/ipc-client";
import type { SearchQuery, SearchResult } from "@/types/search";

export async function globalSearch(query: SearchQuery): Promise<SearchResult[]> {
  return getDesktopApi().search.global(query);
}
