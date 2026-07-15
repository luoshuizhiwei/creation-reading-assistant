import { nowIso } from "../../services/mobile-storage-core";
import type { MobileSnapshot } from "../../types/mobile";

const CONTINUE_REMOVED_KEY = "creation-reading-assistant-mobile-continue-removed";

/** 获取“已从继续阅读移除”的本地隐藏状态（key=bookId, value=removedAt ISO）。纯本地，不参与同步。 */
export function getRemovedContinueBookIds(): Record<string, string> {
  try {
    const raw = localStorage.getItem(CONTINUE_REMOVED_KEY);
    return raw ? (JSON.parse(raw) as Record<string, string>) : {};
  } catch {
    return {};
  }
}

/** 判断某本书是否被用户从继续阅读中移除且尚未重新阅读。 */
export function isRemovedFromContinue(snapshot: MobileSnapshot, bookId: string): boolean {
  const removed = getRemovedContinueBookIds();
  const removedAt = removed[bookId];
  if (!removedAt) return false;
  const progress = snapshot.progress.find((p) => p.bookId === bookId);
  const lastReadAt = progress?.lastReadAt;
  // 如果移除后用户又读了这本书（lastReadAt 更新），允许它重新出现
  if (lastReadAt && lastReadAt > removedAt) return false;
  return true;
}

/** 将书籍从继续阅读列表中移除，不修改阅读进度，不写入同步数据。 */
export function removeBookFromContinue(bookId: string): void {
  const removed = getRemovedContinueBookIds();
  removed[bookId] = nowIso();
  try {
    localStorage.setItem(CONTINUE_REMOVED_KEY, JSON.stringify(removed));
  } catch {
    // 本地存储失败时静默降级
  }
}

/** 清除某本书的移除记录，使其可以重新出现在继续阅读列表。 */
export function clearContinueRemoval(bookId: string): void {
  const removed = getRemovedContinueBookIds();
  if (!removed[bookId]) return;
  delete removed[bookId];
  try {
    localStorage.setItem(CONTINUE_REMOVED_KEY, JSON.stringify(removed));
  } catch {
    // ignore
  }
}
