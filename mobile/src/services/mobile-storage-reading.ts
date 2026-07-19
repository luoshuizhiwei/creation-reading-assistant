import type { BookFormat, LibraryBook, ReadingLocation, ReadingProgress, ReadingSession } from "../../../src/types/library";
import type { MobileSnapshot, SyncAccount } from "../types/mobile";
import {
  getMobileDeviceId,
  normalizeMobileSnapshot,
  nowIso,
  saveMobileSnapshot
} from "./mobile-storage-core";

const COMPLETION_THRESHOLD_PERCENT = 99.5;

function readingLocationPrecisionRank(location: ReadingLocation): number {
  const modeRank: Record<ReadingLocation["mode"], number> = {
    "epub-cfi": 4,
    "text-anchor": 3,
    page: 2,
    scroll: 1
  };
  const precisionRank = location.precision === "exact" ? 10 : 1;
  return precisionRank * 10 + (modeRank[location.mode] ?? 0);
}

/**
 * 冲突解决：优先选择更新且更精确的位置。
 * - 时间差 > 60s 时，updatedAt 更新的胜出；
 * - 时间差 <= 60s 时，按精度（exact > estimated）和模式（CFI > text-anchor > page > scroll）选择更精确的。
 */
export function compareReadingLocation(a: ReadingLocation, b: ReadingLocation): ReadingLocation {
  const aTime = new Date(a.updatedAt).getTime();
  const bTime = new Date(b.updatedAt).getTime();
  const TIME_THRESHOLD_MS = 60_000;
  if (Math.abs(aTime - bTime) > TIME_THRESHOLD_MS) {
    return aTime > bTime ? a : b;
  }
  return readingLocationPrecisionRank(a) >= readingLocationPrecisionRank(b) ? a : b;
}

export function createReadingLocation(format: BookFormat, progressPercent: number, scrollTop = 0, extras: Partial<ReadingLocation> = {}): ReadingLocation {
  return {
    format,
    mode: extras.mode ?? "scroll",
    progressPercent,
    precision: extras.precision ?? "estimated",
    scroll: extras.scroll ?? {
      scrollTop,
      scrollHeight: 100,
      containerHeight: 100
    },
    text: extras.text,
    epub: extras.epub,
    page: extras.page,
    sourceVersion: extras.sourceVersion,
    updatedAt: nowIso()
  };
}

export async function saveMobileReadingProgress(
  snapshot: MobileSnapshot,
  book: LibraryBook,
  progressPercent: number,
  activeDeltaMs = 0,
  locationExtras: Partial<ReadingLocation> = {}
): Promise<MobileSnapshot> {
  const current = snapshot.progress.find((item) => item.bookId === book.id);
  const updatedAt = nowIso();
  const progress: ReadingProgress = {
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    currentLocation: createReadingLocation(book.format, progressPercent, 0, locationExtras),
    progressPercent,
    lastReadAt: updatedAt,
    totalReadingTimeMs: (current?.totalReadingTimeMs ?? 0) + activeDeltaMs,
    completionState:
      progressPercent >= COMPLETION_THRESHOLD_PERCENT
        ? "completed"
        : progressPercent > 0
          ? "reading"
          : "unread",
    completedAt: progressPercent >= COMPLETION_THRESHOLD_PERCENT ? updatedAt : undefined,
    revision: (current?.revision ?? 0) + 1,
    deviceId: getMobileDeviceId(),
    updatedAt
  };
  const next = {
    ...snapshot,
    progress: [progress, ...snapshot.progress.filter((item) => item.bookId !== book.id)],
    updatedAt
  };
  await saveMobileSnapshot(next, ["progress"]);
  return next;
}

export async function addMobileReadingSession(
  snapshot: MobileSnapshot,
  book: LibraryBook,
  durationMs: number,
  progressPercent: number,
  locationExtras: Partial<ReadingLocation> = {},
  sessionId?: string
): Promise<MobileSnapshot> {
  if (sessionId && snapshot.sessions.some((item) => item.id === sessionId)) return snapshot;
  const createdAt = nowIso();
  const location = createReadingLocation(book.format, progressPercent, 0, locationExtras);
  const session: ReadingSession = {
    id: sessionId ?? `mobile-session-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    startAt: new Date(Date.now() - durationMs).toISOString(),
    endAt: createdAt,
    durationMs,
    activeDurationMs: durationMs,
    idleDurationMs: 0,
    wallDurationMs: durationMs,
    startLocation: location,
    endLocation: location,
    dateKey: createdAt.slice(0, 10),
    status: "ended",
    source: "manualOpen",
    endReason: "leave-reader",
    revision: 1,
    deviceId: getMobileDeviceId(),
    createdAt,
    updatedAt: createdAt,
    lastPersistAt: createdAt
  };
  const existingProgress = snapshot.progress.find((item) => item.bookId === book.id);
  const nextProgress = existingProgress
    ? {
        ...existingProgress,
        totalReadingTimeMs: Math.max(0, existingProgress.totalReadingTimeMs ?? 0) + Math.max(0, durationMs),
        updatedAt: createdAt,
        revision: (existingProgress.revision ?? 0) + 1
      }
    : undefined;
  const next = {
    ...snapshot,
    sessions: [session, ...snapshot.sessions],
    progress: nextProgress
      ? [nextProgress, ...snapshot.progress.filter((item) => item.bookId !== book.id)]
      : snapshot.progress,
    updatedAt: createdAt
  };
  await saveMobileSnapshot(next, ["sessions", "progress"]);
  return next;
}

export async function exportMobileSnapshot(snapshot: MobileSnapshot): Promise<string> {
  return JSON.stringify(normalizeMobileSnapshot(snapshot), null, 2);
}

export function mergeReadingProgress(local: ReadingProgress, remote: ReadingProgress): ReadingProgress {
  const winner = compareReadingLocation(local.currentLocation, remote.currentLocation);
  const isLocalWinner = winner === local.currentLocation;
  return {
    ...(isLocalWinner ? local : remote),
    totalReadingTimeMs: Math.max(local.totalReadingTimeMs ?? 0, remote.totalReadingTimeMs ?? 0),
    // 保留更精确的 currentLocation，其他元数据以胜者为准
    currentLocation: winner,
    // 若胜者未完成，则清除完成时间；若完成则保留胜者的时间
    completedAt: winner.progressPercent >= COMPLETION_THRESHOLD_PERCENT ? (isLocalWinner ? local.completedAt : remote.completedAt) : undefined,
    completionState: winner.progressPercent >= COMPLETION_THRESHOLD_PERCENT ? "completed" : "reading",
    lastReadAt: winner.updatedAt,
    updatedAt: nowIso(),
    revision: Math.max(local.revision ?? 0, remote.revision ?? 0) + 1
  };
}

export async function importMobileSnapshot(currentSnapshot: MobileSnapshot, json: string): Promise<MobileSnapshot> {
  try {
    const parsed = JSON.parse(json) as Partial<MobileSnapshot>;
    const imported = normalizeMobileSnapshot(parsed);
    const localProgressByBookId = new Map(currentSnapshot.progress.map((item) => [item.bookId, item]));
    const mergedProgress: ReadingProgress[] = [];
    for (const remote of imported.progress) {
      const local = localProgressByBookId.get(remote.bookId);
      if (local) {
        mergedProgress.push(mergeReadingProgress(local, remote));
        localProgressByBookId.delete(remote.bookId);
      } else {
        mergedProgress.push(remote);
      }
    }
    mergedProgress.push(...localProgressByBookId.values());

    const merged: MobileSnapshot = {
      inspirations: [...currentSnapshot.inspirations, ...imported.inspirations].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      books: [...currentSnapshot.books, ...imported.books].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      progress: mergedProgress,
      sessions: [...currentSnapshot.sessions, ...imported.sessions].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      notes: [...currentSnapshot.notes, ...imported.notes].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      highlights: [...currentSnapshot.highlights, ...imported.highlights].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      tags: [...currentSnapshot.tags, ...imported.tags].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      categories: [...currentSnapshot.categories, ...imported.categories].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      shelves: [...currentSnapshot.shelves, ...imported.shelves].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      syncAccounts: [...currentSnapshot.syncAccounts, ...imported.syncAccounts].filter(
        (item, index, array) => array.findIndex((dup) => dup.id === item.id) === index
      ),
      updatedAt: nowIso()
    };
    await saveMobileSnapshot(merged);
    return merged;
  } catch (error) {
    throw new Error(error instanceof SyntaxError ? "文件不是有效的 JSON 数据快照" : (error instanceof Error ? error.message : "数据快照导入失败"));
  }
}

export async function saveSyncAccount(snapshot: MobileSnapshot, account: Omit<SyncAccount, "id" | "createdAt" | "updatedAt" | "revision" | "deviceId">): Promise<MobileSnapshot> {
  const timestamp = nowIso();
  const nextAccount: SyncAccount = {
    ...account,
    id: `sync-${account.provider}-${Date.now().toString(36)}`,
    createdAt: timestamp,
    updatedAt: timestamp,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    syncAccounts: [nextAccount, ...snapshot.syncAccounts.filter((item) => item.provider !== account.provider)],
    updatedAt: timestamp
  };
  await saveMobileSnapshot(next);
  return next;
}
