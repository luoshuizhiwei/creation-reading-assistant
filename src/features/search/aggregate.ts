import type { UnifiedSearchFilter, UnifiedSearchSourceId } from "@/types/search";
import {
  creationSearchSource,
  inboxSearchSource,
  librarySearchSource,
  type SearchSource
} from "@/features/search/sources";

/**
 * 分组显示顺序：项目、章节、场景正文、卡片、收件箱、资料。
 * 旧灵感源已从"全部"结果中摘除（点击后无法定位目标条目，体验断链）；
 * 数据迁移并入收件箱后整个旧灵感链路会一并下线，届时再清理剩余代码。
 */
export const SOURCE_ORDER: UnifiedSearchSourceId[] = [
  "project",
  "chapter",
  "scene",
  "card",
  "inbox",
  "library"
];

/** 筛选 → 参与搜索的数据源列表。 */
export function sourcesForFilter(filter: UnifiedSearchFilter): SearchSource[] {
  switch (filter) {
    case "project":
      return [creationSearchSource];
    case "body":
      return [creationSearchSource, librarySearchSource];
    case "card":
      return [creationSearchSource];
    case "inbox":
      return [inboxSearchSource];
    case "library":
      return [librarySearchSource];
    case "all":
      return [creationSearchSource, inboxSearchSource, librarySearchSource];
  }
}

/** 按固定来源顺序稳定排序（各源内部顺序保持）。 */
export function sortEntries(entries: Array<{ source: UnifiedSearchSourceId }>): void {
  const rank = new Map(SOURCE_ORDER.map((source, index) => [source, index]));
  entries.sort((a, b) => (rank.get(a.source) ?? 99) - (rank.get(b.source) ?? 99));
}
