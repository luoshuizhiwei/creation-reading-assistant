import type { UnifiedSearchFilter, UnifiedSearchSourceId } from "@/types/search";
import {
  creationSearchSource,
  inboxSearchSource,
  librarySearchSource,
  screenSearchSource,
  type SearchSource
} from "@/features/search/sources";

/**
 * 分组显示顺序：项目、章节、场景正文、卡片、收件箱、资料。
 * 旧灵感源已从"全部"结果中摘除（点击后无法定位目标条目，体验断链）；
 * 数据迁移并入收件箱后整个旧灵感链路会一并下线，届时再清理剩余代码。
 *
 * BD-239 的「页面导航」挂在末尾：跳转类结果排在内容结果之后，用户搜的是手上的稿子时
 * 不会被一排页面名插队；而搜「卡片库」这类页面词时它本来就在组里（组内按打分序）。
 */
export const SOURCE_ORDER: UnifiedSearchSourceId[] = [
  "project",
  "chapter",
  "scene",
  "card",
  "inbox",
  "library",
  "screen"
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
    // 页面导航只参与「全部」：这一源给的是去处（WCAG 2.4.5 的第二条途径），不是内容。
    case "all":
      return [creationSearchSource, inboxSearchSource, librarySearchSource, screenSearchSource];
  }
}

/** 按固定来源顺序稳定排序（各源内部顺序保持）。 */
export function sortEntries(entries: Array<{ source: UnifiedSearchSourceId }>): void {
  const rank = new Map(SOURCE_ORDER.map((source, index) => [source, index]));
  entries.sort((a, b) => (rank.get(a.source) ?? 99) - (rank.get(b.source) ?? 99));
}
