/** 目录搜索过滤：大小写不敏感的包含匹配。空查询返回原列表。 */
export function filterTocEntries(entries: readonly { id: string; label: string; level: number }[], query: string): { id: string; label: string; level: number }[] {
  const q = query.trim().toLowerCase();
  if (!q) return [...entries];
  return entries.filter((entry) => entry.label.toLowerCase().includes(q));
}
