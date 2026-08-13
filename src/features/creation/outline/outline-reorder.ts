/**
 * 重排目标：显式区分「不能移动」与「移动到末尾」。
 *
 * 旧实现用 `undefined` 同时表达两种语义，导致：
 *  - 列表倒数第二项「下移」被误判为无变化（本应移到末位）；
 *  - 列表最后一项「下移」未禁用（本应禁用）。
 *
 * 新结构：`canMove: false` 表示按钮禁用；`canMove: true` 且 `beforeId` 为
 * `undefined` 表示移动到兄弟列表末尾（append），为具体 id 表示插到该元素之前。
 */
export type ReorderTarget =
  | { canMove: false }
  | { canMove: true; beforeId?: string };

export function reorderTarget(siblingIds: string[], id: string, direction: "up" | "down"): ReorderTarget {
  const at = siblingIds.indexOf(id);
  if (at < 0) return { canMove: false };
  if (direction === "up") {
    return at > 0 ? { canMove: true, beforeId: siblingIds[at - 1] } : { canMove: false };
  }
  // 下移：最后一项不可移动；倒数第二项下移到末位（beforeId 为 undefined = append）。
  if (at >= siblingIds.length - 1) return { canMove: false };
  return at === siblingIds.length - 2
    ? { canMove: true, beforeId: undefined }
    : { canMove: true, beforeId: siblingIds[at + 2] };
}
