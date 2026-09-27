/**
 * 阅读进度与会话数据层（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：reading-progress.json / reading-sessions.json 的读写、记录规范化、
 * 会话生命周期（开始 / 心跳 / 结束 / 异常恢复）与阅读统计汇总。
 * 行为逐字保持，仅把顶层声明导出供主进程入口与同步层使用。
 */
import path from "node:path";
import type {
  BookFormat,
  EndReadingSessionInput,
  GetReadingSessionsInput,
  ReadingLocation,
  ReadingProgress,
  ReadingSession,
  ReadingStatsSummary,
  RecoverReadingSessionsResult,
  SaveProgressInput,
  StartReadingSessionInput,
  UpdateReadingSessionInput
} from "../../src/types/library";
import {
  currentDeviceId,
  isRecord,
  makeId,
  nextSyncMetadata,
  now,
  optionalString,
  readJson,
  readingProgressPath,
  readingSessionsPath,
  withFileLock,
  withSyncMetadata,
  writeJson
} from "./storage";
import { normalizeBookFormat, readLibraryIndex } from "./library-store";

export interface ReadingProgressStore {
  version: 2;
  updatedAt: string;
  items: ReadingProgress[];
}

export interface ReadingSessionStore {
  version: 1;
  updatedAt: string;
  sessions: ReadingSession[];
}

export function clamp01(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
}

export function clampDuration(value: unknown, maxMs = 30 * 60_000): number {
  if (typeof value !== "number" || !Number.isFinite(value)) return 0;
  if (value > maxMs) {
    console.warn(`[clampDuration] unusually large delta ${value}ms exceeds ${maxMs}ms cap, clamping.`);
  }
  return Math.max(0, Math.min(maxMs, value));
}

export function dateKeyFromDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function dateKeyFromIso(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? dateKeyFromDate() : dateKeyFromDate(parsed);
}

export function dayStartMs(dateKey: string): number {
  return new Date(`${dateKey}T00:00:00`).getTime();
}

export function defaultLocation(format: BookFormat, progressPercent = 0): ReadingLocation {
  const progress = clamp01(progressPercent);
  if (format === "epub") {
    return {
      format,
      mode: "epub-cfi",
      progressPercent: progress,
      precision: "estimated",
      epub: {},
      updatedAt: now()
    };
  }
  return {
    format,
    mode: "scroll",
    progressPercent: progress,
    precision: "estimated",
    scroll: {
      scrollTop: 0,
      scrollHeight: 1,
      containerHeight: 1
    },
    updatedAt: now()
  };
}

export function normalizeLocation(value: unknown, fallbackFormat: BookFormat): ReadingLocation | null {
  if (!isRecord(value)) return null;
  const format = normalizeBookFormat(value.format, undefined) || fallbackFormat;
  const mode =
    value.mode === "text-anchor" || value.mode === "epub-cfi" || value.mode === "page" || value.mode === "scroll" ? value.mode : "scroll";
  const progressPercent = clamp01(value.progressPercent);
  const location: ReadingLocation = {
    format,
    mode,
    progressPercent,
    precision: value.precision === "exact" ? "exact" : "estimated",
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now()
  };
  if (isRecord(value.scroll)) {
    location.scroll = {
      scrollTop: typeof value.scroll.scrollTop === "number" ? Math.max(0, value.scroll.scrollTop) : 0,
      scrollHeight: typeof value.scroll.scrollHeight === "number" ? Math.max(1, value.scroll.scrollHeight) : 1,
      containerHeight: typeof value.scroll.containerHeight === "number" ? Math.max(1, value.scroll.containerHeight) : 1
    };
  }
  if (isRecord(value.text)) {
    location.text = {
      charOffset: typeof value.text.charOffset === "number" ? Math.max(0, value.text.charOffset) : undefined,
      chapterRef: optionalString(value.text.chapterRef),
      headingPath: Array.isArray(value.text.headingPath) ? value.text.headingPath.filter((item): item is string => typeof item === "string") : undefined,
      anchorText: optionalString(value.text.anchorText)
    };
  }
  if (isRecord(value.epub)) {
    location.epub = {
      cfi: optionalString(value.epub.cfi),
      href: optionalString(value.epub.href),
      spineIndex: typeof value.epub.spineIndex === "number" ? Math.max(0, value.epub.spineIndex) : undefined,
      chapterRef: optionalString(value.epub.chapterRef)
    };
  }
  if (isRecord(value.page)) {
    location.page = {
      pageIndex: typeof value.page.pageIndex === "number" ? Math.max(0, value.page.pageIndex) : 0,
      pageCount: typeof value.page.pageCount === "number" ? Math.max(1, value.page.pageCount) : undefined
    };
  }
  if (isRecord(value.sourceVersion)) {
    location.sourceVersion = {
      fileSize: typeof value.sourceVersion.fileSize === "number" ? Math.max(0, value.sourceVersion.fileSize) : undefined,
      modifiedAt: typeof value.sourceVersion.modifiedAt === "string" ? value.sourceVersion.modifiedAt : undefined,
      contentHash: optionalString(value.sourceVersion.contentHash)
    };
  }
  return location;
}

export function normalizeProgressItem(value: unknown): ReadingProgress | null {
  if (!isRecord(value)) return null;
  const bookId = typeof value.bookId === "string" ? value.bookId : "";
  const filePath = typeof value.filePath === "string" ? value.filePath : "";
  if (!bookId || !filePath) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const legacyProgress = clamp01(typeof value.progress === "number" ? value.progress : value.progressPercent);
  const legacyLocation: ReadingLocation = {
    ...defaultLocation(format, legacyProgress),
    scroll: {
      scrollTop: typeof value.scrollTop === "number" ? Math.max(0, value.scrollTop) : 0,
      scrollHeight: typeof value.scrollHeight === "number" ? Math.max(1, value.scrollHeight) : 1,
      containerHeight: typeof value.clientHeight === "number" ? Math.max(1, value.clientHeight) : 1
    },
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : typeof value.lastReadAt === "string" ? value.lastReadAt : now()
  };
  const currentLocation = normalizeLocation(value.currentLocation, format) ?? legacyLocation;
  const progressPercent = clamp01(typeof value.progressPercent === "number" ? value.progressPercent : currentLocation.progressPercent);
  const completionState: ReadingProgress["completionState"] =
    value.completionState === "completed" || value.completionState === "unread" ? value.completionState : "reading";
  return withSyncMetadata({
    bookId,
    filePath,
    format,
    currentLocation: { ...currentLocation, progressPercent },
    progressPercent,
    lastReadAt: typeof value.lastReadAt === "string" ? value.lastReadAt : now(),
    totalReadingTimeMs: typeof value.totalReadingTimeMs === "number" ? Math.max(0, value.totalReadingTimeMs) : 0,
    lastSessionId: typeof value.lastSessionId === "string" ? value.lastSessionId : undefined,
    completionState,
    completedAt: typeof value.completedAt === "string" ? value.completedAt : undefined,
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now(),
    revision: typeof value.revision === "number" ? value.revision : undefined,
    deviceId: optionalString(value.deviceId),
    deletedAt: optionalString(value.deletedAt)
  });
}

export async function readProgressList(options: { includeDeleted?: boolean } = {}): Promise<ReadingProgress[]> {
  const raw = await readJson<unknown>(readingProgressPath(), { version: 2, updatedAt: now(), items: [] });
  const items = Array.isArray(raw) ? raw : isRecord(raw) && Array.isArray(raw.items) ? raw.items : [];
  return items
    .map(normalizeProgressItem)
    .filter((item): item is ReadingProgress => item !== null)
    .filter((item) => options.includeDeleted || !item.deletedAt);
}

export async function writeProgressList(items: ReadingProgress[]): Promise<void> {
  await writeJson<ReadingProgressStore>(readingProgressPath(), { version: 2, updatedAt: now(), items });
}

export async function getProgress(bookId: string): Promise<ReadingProgress | undefined> {
  return (await readProgressList()).find((item) => item.bookId === bookId);
}

export async function getBatchProgress(bookIds: string[]): Promise<ReadingProgress[]> {
  const idSet = new Set(bookIds);
  const list = await readProgressList();
  return list.filter((item) => idSet.has(item.bookId));
}

export async function totalReadingTimeForBook(bookId: string, sessions?: ReadingSession[]): Promise<number> {
  const source = sessions ?? (await readSessionList());
  return source.filter((session) => session.bookId === bookId).reduce((sum, session) => sum + Math.max(0, session.activeDurationMs), 0);
}

export async function saveProgress(input: SaveProgressInput): Promise<ReadingProgress> {
  const book = (await readLibraryIndex()).find((item) => item.id === input.bookId);
  if (!book) throw new Error("Book not found.");
  const list = await readProgressList();
  const current = list.find((item) => item.bookId === input.bookId);
  const location = normalizeLocation(input.location, book.format) ?? defaultLocation(book.format);
  const timestamp = now();
  const next: ReadingProgress = {
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    ...nextSyncMetadata(current),
    currentLocation: { ...location, updatedAt: timestamp },
    progressPercent: location.progressPercent,
    lastReadAt: timestamp,
    totalReadingTimeMs: current?.totalReadingTimeMs ?? 0,
    lastSessionId: current?.lastSessionId,
    completionState: location.progressPercent >= 0.995 ? "completed" : "reading",
    completedAt: location.progressPercent >= 0.995 ? current?.completedAt ?? timestamp : undefined,
    updatedAt: timestamp
  };
  await writeProgressList([next, ...list.filter((item) => item.bookId !== input.bookId)]);
  return next;
}

export function normalizeSessionItem(value: unknown): ReadingSession | null {
  if (!isRecord(value)) return null;
  const id = typeof value.id === "string" ? value.id : "";
  const bookId = typeof value.bookId === "string" ? value.bookId : "";
  const filePath = typeof value.filePath === "string" ? value.filePath : "";
  if (!id || !bookId || !filePath) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const startAt = typeof value.startAt === "string" ? value.startAt : typeof value.startedAt === "string" ? value.startedAt : now();
  const activeDurationMs =
    typeof value.activeDurationMs === "number" ? Math.max(0, value.activeDurationMs) : typeof value.durationMs === "number" ? Math.max(0, value.durationMs) : 0;
  const idleDurationMs = typeof value.idleDurationMs === "number" ? Math.max(0, value.idleDurationMs) : 0;
  const status: ReadingSession["status"] =
    value.status === "active" || value.status === "paused" || value.status === "ended" || value.status === "recovered" ? value.status : value.endAt ? "ended" : "active";
  const source: ReadingSession["source"] = value.source === "restore" || value.source === "switchBook" ? value.source : "manualOpen";
  const pauseReason: ReadingSession["pauseReason"] =
    value.pauseReason === "idle" || value.pauseReason === "window-blur" || value.pauseReason === "leave-reader" ? value.pauseReason : undefined;
  const endReason: ReadingSession["endReason"] =
    value.endReason === "leave-reader" ||
    value.endReason === "switch-book" ||
    value.endReason === "window-close" ||
    value.endReason === "idle-timeout" ||
    value.endReason === "crash-recovered"
      ? value.endReason
      : undefined;
  return withSyncMetadata({
    id,
    bookId,
    filePath,
    format,
    startAt,
    endAt: typeof value.endAt === "string" ? value.endAt : undefined,
    durationMs: activeDurationMs + idleDurationMs,
    activeDurationMs,
    idleDurationMs,
    wallDurationMs: typeof value.wallDurationMs === "number" ? Math.max(0, value.wallDurationMs) : Math.max(0, Date.now() - new Date(startAt).getTime()),
    startLocation: normalizeLocation(value.startLocation, format) ?? defaultLocation(format, clamp01(value.startProgress)),
    endLocation: normalizeLocation(value.endLocation, format) ?? undefined,
    dateKey: typeof value.dateKey === "string" ? value.dateKey : dateKeyFromIso(startAt),
    dailyActiveMs: isRecord(value.dailyActiveMs)
      ? Object.fromEntries(Object.entries(value.dailyActiveMs).filter((entry): entry is [string, number] => typeof entry[1] === "number" && entry[1] > 0))
      : undefined,
    status,
    source,
    pauseReason,
    endReason,
    createdAt: typeof value.createdAt === "string" ? value.createdAt : startAt,
    updatedAt: typeof value.updatedAt === "string" ? value.updatedAt : now(),
    lastPersistAt: typeof value.lastPersistAt === "string" ? value.lastPersistAt : typeof value.updatedAt === "string" ? value.updatedAt : now(),
    revision: typeof value.revision === "number" ? value.revision : undefined,
    deviceId: optionalString(value.deviceId),
    deletedAt: optionalString(value.deletedAt)
  });
}

export async function readSessionList(options: { includeDeleted?: boolean } = {}): Promise<ReadingSession[]> {
  const raw = await readJson<unknown>(readingSessionsPath(), { version: 1, updatedAt: now(), sessions: [] });
  const sessions = Array.isArray(raw) ? raw : isRecord(raw) && Array.isArray(raw.sessions) ? raw.sessions : [];
  return sessions
    .map(normalizeSessionItem)
    .filter((item): item is ReadingSession => item !== null)
    .filter((item) => options.includeDeleted || !item.deletedAt);
}

export async function writeSessionList(sessions: ReadingSession[]): Promise<void> {
  await writeJson<ReadingSessionStore>(readingSessionsPath(), { version: 1, updatedAt: now(), sessions });
}

export function applySessionDelta(session: ReadingSession, input: UpdateReadingSessionInput | EndReadingSessionInput, timestamp: string): ReadingSession {
  const activeDeltaMs = clampDuration(input.activeDeltaMs);
  const idleDeltaMs = clampDuration(input.idleDeltaMs);
  const dayKey = dateKeyFromIso(timestamp);
  const dailyActiveMs = { ...(session.dailyActiveMs ?? {}) };
  if (activeDeltaMs > 0) dailyActiveMs[dayKey] = (dailyActiveMs[dayKey] ?? 0) + activeDeltaMs;
  const activeDurationMs = session.activeDurationMs + activeDeltaMs;
  const idleDurationMs = session.idleDurationMs + idleDeltaMs;
  return {
    ...session,
    durationMs: activeDurationMs + idleDurationMs,
    activeDurationMs,
    idleDurationMs,
    wallDurationMs: Math.max(0, new Date(timestamp).getTime() - new Date(session.startAt).getTime()),
    endLocation: input.location ?? session.endLocation,
    dailyActiveMs,
    updatedAt: timestamp,
    lastPersistAt: timestamp
  };
}

export async function syncProgressTotalForBook(bookId: string, sessionId?: string): Promise<void> {
  const list = await readProgressList();
  const current = list.find((item) => item.bookId === bookId);
  if (!current) return;
  const next: ReadingProgress = {
    ...current,
    ...nextSyncMetadata(current),
    totalReadingTimeMs: await totalReadingTimeForBook(bookId),
    lastSessionId: sessionId ?? current.lastSessionId,
    updatedAt: now()
  };
  await writeProgressList([next, ...list.filter((item) => item.bookId !== bookId)]);
}

export async function syncAllProgressTotals(): Promise<void> {
  const sessions = await readSessionList();
  const list = await readProgressList();
  const next = await Promise.all(
    list.map(async (progress) => ({
      ...progress,
      totalReadingTimeMs: await totalReadingTimeForBook(progress.bookId, sessions)
    }))
  );
  await writeProgressList(next);
}

export async function startReadingSession(input: StartReadingSessionInput): Promise<ReadingSession> {
  const book = (await readLibraryIndex()).find((item) => item.id === input.bookId);
  if (!book) throw new Error("Book not found.");
  const timestamp = now();
  const location = normalizeLocation(input.location, book.format) ?? defaultLocation(book.format);
  const session: ReadingSession = {
    id: makeId("session"),
    bookId: book.id,
    filePath: book.filePath,
    format: book.format,
    startAt: timestamp,
    durationMs: 0,
    activeDurationMs: 0,
    idleDurationMs: 0,
    wallDurationMs: 0,
    startLocation: location,
    endLocation: location,
    dateKey: dateKeyFromIso(timestamp),
    dailyActiveMs: {},
    revision: 1,
    deviceId: currentDeviceId(),
    status: "active",
    source: input.source === "restore" || input.source === "switchBook" ? input.source : "manualOpen",
    createdAt: timestamp,
    updatedAt: timestamp,
    lastPersistAt: timestamp
  };
  // Acquire file-level lock to prevent read-write race with concurrent IPC handlers
  await withFileLock(readingSessionsPath(), async () => {
    await writeSessionList([session, ...(await readSessionList())]);
  });
  return session;
}

export async function updateReadingSession(input: UpdateReadingSessionInput): Promise<ReadingSession> {
  const sessions = await readSessionList();
  const session = sessions.find((item) => item.id === input.sessionId);
  if (!session) throw new Error("Reading session not found.");
  if (session.status === "ended" || session.status === "recovered") return session;
  const timestamp = now();
  const updated: ReadingSession = {
    ...applySessionDelta(session, input, timestamp),
    ...nextSyncMetadata(session),
    status: input.status === "paused" ? "paused" : "active",
    pauseReason: input.status === "paused" ? input.pauseReason : undefined
  };
  await writeSessionList([updated, ...sessions.filter((item) => item.id !== input.sessionId)]);
  return updated;
}

export async function endReadingSession(input: EndReadingSessionInput): Promise<ReadingSession> {
  const sessions = await readSessionList();
  const session = sessions.find((item) => item.id === input.sessionId);
  if (!session) throw new Error("Reading session not found.");
  if (session.status === "ended" || session.status === "recovered") return session;
  const timestamp = now();
  const ended: ReadingSession = {
    ...applySessionDelta(session, input, timestamp),
    ...nextSyncMetadata(session),
    status: "ended",
    endAt: timestamp,
    endReason: input.endReason,
    pauseReason: undefined
  };
  await writeSessionList([ended, ...sessions.filter((item) => item.id !== input.sessionId)]);
  await syncProgressTotalForBook(ended.bookId, ended.id);
  return ended;
}

export async function recoverUnfinishedSessions(): Promise<RecoverReadingSessionsResult> {
  const sessions = await readSessionList();
  let changed = false;
  let recoveredCount = 0;
  const recovered = sessions.map((session) => {
    if ((session.status !== "active" && session.status !== "paused") || session.endAt) return session;
    changed = true;
    recoveredCount += 1;
    const endAt = session.lastPersistAt || session.updatedAt || session.startAt;
    return {
      ...session,
      ...nextSyncMetadata(session),
      status: "recovered" as const,
      endAt,
      endReason: "crash-recovered" as const,
      wallDurationMs: Math.max(0, new Date(endAt).getTime() - new Date(session.startAt).getTime()),
      updatedAt: now(),
      lastPersistAt: endAt
    };
  });
  if (changed) {
    await writeSessionList(recovered);
    await syncAllProgressTotals();
  }
  return { recoveredCount, sessions: recovered };
}

export async function getReadingSessions(input?: GetReadingSessionsInput): Promise<ReadingSession[]> {
  const limit = typeof input?.limit === "number" ? Math.min(500, Math.max(1, Math.round(input.limit))) : 100;
  const includeActive = input?.includeActive !== false;
  return (await readSessionList())
    .filter((session) => (!input?.bookId || session.bookId === input.bookId) && (includeActive || (session.status !== "active" && session.status !== "paused")))
    .sort((a, b) => new Date(b.startAt).getTime() - new Date(a.startAt).getTime())
    .slice(0, limit);
}

export async function getReadingStats(): Promise<ReadingStatsSummary> {
  const [sessions, books, progressList] = await Promise.all([readSessionList(), readLibraryIndex(), readProgressList()]);
  const bookById = new Map(books.map((book) => [book.id, book]));
  const progressById = new Map(progressList.map((progress) => [progress.bookId, progress]));
  const dailyTotals = new Map<string, number>();
  const bookTotals = new Map<string, number>();
  let totalDurationMs = 0;
  let countedSessions = 0;

  for (const session of sessions) {
    const activeDuration = Math.max(0, session.activeDurationMs);
    if (activeDuration <= 0) continue;
    countedSessions += 1;
    totalDurationMs += activeDuration;
    bookTotals.set(session.bookId, (bookTotals.get(session.bookId) ?? 0) + activeDuration);
    const daily = session.dailyActiveMs && Object.keys(session.dailyActiveMs).length > 0 ? session.dailyActiveMs : { [session.dateKey]: activeDuration };
    for (const [dateKey, duration] of Object.entries(daily)) {
      dailyTotals.set(dateKey, (dailyTotals.get(dateKey) ?? 0) + Math.max(0, duration));
    }
  }

  const todayKey = dateKeyFromDate();
  const todayStart = dayStartMs(todayKey);
  const durationForRange = (days: number): number => {
    let total = 0;
    for (const [dateKey, duration] of dailyTotals) {
      const diff = Math.floor((todayStart - dayStartMs(dateKey)) / 86_400_000);
      if (diff >= 0 && diff < days) total += duration;
    }
    return total;
  };

  const byBook = [...bookTotals.entries()]
    .map(([bookId, duration]) => {
      const book = bookById.get(bookId);
      const progress = progressById.get(bookId);
      return {
        bookId,
        title: book?.title ?? path.basename(progress?.filePath ?? bookId),
        format: book?.format ?? progress?.format ?? "txt",
        totalDurationMs: duration,
        lastReadAt: progress?.lastReadAt,
        progressPercent: progress?.progressPercent
      };
    })
    .sort((a, b) => b.totalDurationMs - a.totalDurationMs);

  const recentBooks = progressList
    .filter((progress) => progress.lastReadAt)
    .sort((a, b) => new Date(b.lastReadAt).getTime() - new Date(a.lastReadAt).getTime())
    .slice(0, 10)
    .map((progress) => {
      const book = bookById.get(progress.bookId);
      return {
        bookId: progress.bookId,
        title: book?.title ?? path.basename(progress.filePath),
        format: book?.format ?? progress.format,
        lastReadAt: progress.lastReadAt,
        progressPercent: progress.progressPercent,
        totalDurationMs: progress.totalReadingTimeMs
      };
    });

  return {
    todayDurationMs: durationForRange(1),
    last7DaysDurationMs: durationForRange(7),
    last30DaysDurationMs: durationForRange(30),
    totalDurationMs,
    byBook,
    recentBooks,
    recentSessions: sessions
      .filter((session) => session.activeDurationMs >= 1000)
      .sort((a, b) => new Date(b.startAt).getTime() - new Date(a.startAt).getTime())
      .slice(0, 20),
    readingDaysCount: [...dailyTotals.values()].filter((duration) => duration > 0).length,
    averageSessionDurationMs: countedSessions > 0 ? Math.round(totalDurationMs / countedSessions) : 0,
    daily: [...dailyTotals.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([dateKey, durationMs]) => ({ dateKey, durationMs }))
  };
}
