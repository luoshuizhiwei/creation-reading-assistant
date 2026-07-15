import type { MobileBook } from "../../types/mobile";
import type { MobileSnapshot } from "../../services/mobile-storage";
import { isBookReadableOnDevice } from "./book-status";
import {
  clearContinueRemoval,
  getRemovedContinueBookIds,
  isRemovedFromContinue,
  removeBookFromContinue
} from "./continue-removal";

export {
  clearContinueRemoval,
  getRemovedContinueBookIds,
  isRemovedFromContinue,
  removeBookFromContinue
} from "./continue-removal";

export function progressFor(snapshot: MobileSnapshot, bookId: string): number {
  const value = snapshot.progress.find((item) => item.bookId === bookId)?.progressPercent ?? 0;
  return Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
}

export interface ReaderPositionLabel {
  chapterTitle?: string;
  pageIndex?: number;
  pageCount?: number;
  progressPercent: number;
}

export function readerPositionFor(snapshot: MobileSnapshot, bookId: string): ReaderPositionLabel {
  const progress = snapshot.progress.find((item) => item.bookId === bookId);
  const location = progress?.currentLocation;
  return {
    chapterTitle: location?.text?.headingPath?.[0],
    pageIndex: location?.page?.pageIndex,
    pageCount: location?.page?.pageCount,
    progressPercent: progressFor(snapshot, bookId)
  };
}

export function formatReaderPositionLabel(position: ReaderPositionLabel): string {
  const parts: string[] = [];
  if (position.chapterTitle) parts.push(position.chapterTitle);
  if (typeof position.pageIndex === "number" && Number.isFinite(position.pageIndex) && position.pageIndex >= 0) {
    parts.push(`第 ${position.pageIndex + 1} 页`);
  }
  parts.push(`${position.progressPercent.toFixed(1)}%`);
  return parts.join(" · ");
}

const MIN_SESSION_MS = 10_000;

/** 判断一本书是否有真实阅读记录（进度>0 或存在阅读会话）。 */
export function hasBookBeenRead(snapshot: MobileSnapshot, bookId: string): boolean {
  const progress = snapshot.progress.find((p) => p.bookId === bookId);
  if (progress && progress.progressPercent > 0) return true;
  return snapshot.sessions.some((session) => session.bookId === bookId);
}

/** 返回真实阅读记录中的最近时间；没有记录时不使用导入时间伪装成最近阅读。 */
export function lastReadAtFor(snapshot: MobileSnapshot, bookId: string): string | undefined {
  const progress = snapshot.progress.find((item) => item.bookId === bookId);
  if (progress?.lastReadAt) return progress.lastReadAt;
  return snapshot.sessions
    .filter((session) => session.bookId === bookId)
    .reduce<string | undefined>((latest, session) => {
      const candidate = session.endAt ?? session.startAt;
      return !latest || candidate > latest ? candidate : latest;
    }, undefined);
}

function sessionProgressDeltaWords(session: MobileSnapshot["sessions"][number], bookWordCount: number): number {
  const start = session.startLocation?.progressPercent ?? 0;
  const end = session.endLocation?.progressPercent ?? session.startLocation?.progressPercent ?? 0;
  const delta = Math.max(0, end - start);
  return Math.max(0, Math.round((bookWordCount * delta) / 100));
}

/**
 * 估算当前阅读速度（字/分钟）。
 * 优先使用该书历史会话（含当前未结束会话）计算，其次使用全局历史会话，
 * 最后返回一个合理的默认值（300 字/分）。
 */
export function estimateBookReadingSpeed(
  snapshot: MobileSnapshot,
  book: MobileBook,
  documentWordCount: number,
  currentSessionActiveMs: number,
  currentSessionProgressDelta: number
): number {
  const effectiveWordCount = documentWordCount > 0 ? documentWordCount : Math.max(1, Math.round(book.size / 3));
  const bookSessions = snapshot.sessions.filter(
    (session) =>
      session.bookId === book.id &&
      session.status === "ended" &&
      (session.activeDurationMs ?? session.durationMs ?? 0) >= MIN_SESSION_MS
  );
  let historicalWords = 0;
  let historicalMs = 0;
  for (const session of bookSessions) {
    historicalWords += sessionProgressDeltaWords(session, effectiveWordCount);
    historicalMs += session.activeDurationMs ?? session.durationMs ?? 0;
  }
  const currentWords = Math.max(0, Math.round((effectiveWordCount * currentSessionProgressDelta) / 100));
  const currentMs = Math.max(0, currentSessionActiveMs);
  const totalWords = historicalWords + currentWords;
  const totalMs = historicalMs + currentMs;
  if (totalMs >= MIN_SESSION_MS && totalWords > 0) {
    return Math.round(totalWords / (totalMs / 60_000));
  }
  const globalSessions = snapshot.sessions.filter(
    (session) => session.status === "ended" && (session.activeDurationMs ?? session.durationMs ?? 0) >= MIN_SESSION_MS
  );
  let globalWords = 0;
  let globalMs = 0;
  for (const session of globalSessions) {
    const otherBook = snapshot.books.find((b) => b.id === session.bookId);
    if (!otherBook) continue;
    const otherWordCount = Math.max(1, Math.round(otherBook.size / 3));
    globalWords += sessionProgressDeltaWords(session, otherWordCount);
    globalMs += session.activeDurationMs ?? session.durationMs ?? 0;
  }
  if (globalMs >= MIN_SESSION_MS && globalWords > 0) {
    return Math.round(globalWords / (globalMs / 60_000));
  }
  return 300;
}

export function getContinueBooks(snapshot: MobileSnapshot): MobileBook[] {
  return getAllContinueBooks(snapshot).slice(0, 8);
}

/** 获取全部继续阅读书籍（不限 8 本），用于管理面板。 */
export function getAllContinueBooks(snapshot: MobileSnapshot): MobileBook[] {
  const now = Date.now();
  const msPerDay = 24 * 60 * 60 * 1000;
  const recencyDecayMs = 7 * msPerDay;
  const monthAgo = now - 30 * msPerDay;

  const scoreBook = (book: MobileBook): number => {
    const progress = snapshot.progress.find((item) => item.bookId === book.id);
    const lastReadAt = lastReadAtFor(snapshot, book.id);
    const lastReadTime = lastReadAt ? new Date(lastReadAt).getTime() : 0;
    const recencyScore = Math.exp((lastReadTime - now) / recencyDecayMs);
    const recentSessions = snapshot.sessions.filter(
      (session) => session.bookId === book.id && new Date(session.startAt).getTime() > monthAgo
    ).length;
    const frequencyScore = Math.min(recentSessions, 10) / 10;
    return recencyScore * 0.6 + frequencyScore * 0.4;
  };

  const scored = snapshot.books
    .filter((book) => {
      const progress = progressFor(snapshot, book.id);
      return (
        isBookReadableOnDevice(book) &&
        hasBookBeenRead(snapshot, book.id) &&
        progress < 99.5 &&
        !isRemovedFromContinue(snapshot, book.id)
      );
    })
    .map((book) => ({
      book,
      score: scoreBook(book),
      lastReadAt: lastReadAtFor(snapshot, book.id) ?? ""
    }))
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      return b.lastReadAt.localeCompare(a.lastReadAt);
    });

  return scored.map((item) => item.book);
}
