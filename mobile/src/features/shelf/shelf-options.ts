import type { ShelfSortMode } from "./shelf-types";

export const shelfSortOptions: Array<{ value: ShelfSortMode; label: string }> = [
  { value: "recent", label: "最近阅读" },
  { value: "imported", label: "导入时间" },
  { value: "title", label: "书名" },
  { value: "progress", label: "进度" }
];
