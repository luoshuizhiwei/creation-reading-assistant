import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import { isBookDisplayable } from "../shelf/book-status";

export type StatsPeriod = "day" | "week" | "month" | "year" | "total";

export const statsPeriodLabels: Record<Exclude<StatsPeriod, "day">, string> = {
  week: "本周",
  month: "本月",
  year: "本年",
  total: "累计"
};

export interface DateRange {
  start: Date;
  end: Date;
  /** 左闭右开 */
  includes(date: Date): boolean;
}

export interface MobileStatsSummary {
  totalReadingMs: number;
  readingDays: number;
  completed: number;
  readingBooks: number;
  readBooks: number;
  unreadBooks: number;
  unreadableBooks: number;
  totalDisplayableBooks: number;
  words: number;
  speed: number;
  sessionCount: number;
  noteCount: number;
  inspirationCount: number;
}

export interface TrendItem {
  dateKey: string;
  label: string;
  durationMs: number;
  sessionCount: number;
}

export interface BookStatusCount {
  reading: number;
  completed: number;
  unread: number;
  unreadable: number;
  total: number;
}

const MAX_SESSION_MS = 24 * 60 * 60 * 1000;

function isValidNumber(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value);
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function toDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseDateLike(value?: string): Date | undefined {
  if (!value) return undefined;
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

function sessionDateKey(session: MobileSnapshot["sessions"][number]): string {
  if (session.dateKey) return session.dateKey;
  const date = parseDateLike(session.startAt);
  return date ? toDateKey(date) : toDateKey(new Date());
}

function sessionDurationMs(session: MobileSnapshot["sessions"][number]): number {
  const active = isValidNumber(session.activeDurationMs) ? session.activeDurationMs : undefined;
  const fallback = isValidNumber(session.durationMs) ? session.durationMs : 0;
  if (active !== undefined && active < 0) return 0;
  const value = active && active > 0 ? active : fallback;
  if (!isValidNumber(value) || value <= 0) return 0;
  if (value > MAX_SESSION_MS) return 0;
  return value;
}

export function buildDateRange(period: StatsPeriod, now = new Date()): DateRange {
  const today = startOfDay(now);
  let start: Date;
  let end: Date;
  if (period === "day") {
    start = new Date(today);
    end = new Date(today);
    end.setDate(end.getDate() + 1);
  } else if (period === "week") {
    const day = today.getDay() || 7;
    start = new Date(today);
    start.setDate(today.getDate() - (day - 1));
    end = new Date(start);
    end.setDate(end.getDate() + 7);
  } else if (period === "month") {
    start = new Date(today.getFullYear(), today.getMonth(), 1);
    end = new Date(today.getFullYear(), today.getMonth() + 1, 1);
  } else if (period === "year") {
    start = new Date(today.getFullYear(), 0, 1);
    end = new Date(today.getFullYear() + 1, 0, 1);
  } else {
    start = new Date(0);
    end = new Date(today);
    end.setDate(end.getDate() + 1);
  }
  return {
    start,
    end,
    includes(date: Date) {
      const t = startOfDay(date).getTime();
      return t >= start.getTime() && t < end.getTime();
    }
  };
}

export function shiftStatsPeriodAnchor(date: Date, period: StatsPeriod, direction: -1 | 1): Date {
  const next = new Date(date);
  if (period === "day") {
    next.setDate(next.getDate() + direction);
  } else if (period === "week") {
    next.setDate(next.getDate() + direction * 7);
  } else if (period === "month") {
    next.setFullYear(date.getFullYear(), date.getMonth() + direction, 1);
  } else if (period === "year") {
    next.setFullYear(next.getFullYear() + direction, 0, 1);
  }
  return next;
}

export function getStatsPeriodTitle(period: StatsPeriod, anchor: Date): string {
  if (period === "total") return "全部记录";
  if (period === "day") return `${anchor.getFullYear()}年${anchor.getMonth() + 1}月${anchor.getDate()}日`;
  const range = buildDateRange(period, anchor);
  const start = range.start;
  const end = new Date(range.end.getTime() - 24 * 60 * 60 * 1000);
  if (period === "week") {
    return `${start.getMonth() + 1}月${start.getDate()}日 - ${end.getMonth() + 1}月${end.getDate()}日`;
  }
  if (period === "month") return `${anchor.getFullYear()}年${anchor.getMonth() + 1}月`;
  if (period === "year") return `${anchor.getFullYear()}年`;
  return "";
}

export function isCurrentStatsPeriod(period: StatsPeriod, anchor: Date, now = new Date()): boolean {
  if (period === "total") return true;
  return buildDateRange(period, anchor).includes(now);
}

export function getValidSessions(snapshot: MobileSnapshot): MobileSnapshot["sessions"][number][] {
  return dedupeSessions(snapshot.sessions.filter((session) => sessionDurationMs(session) > 0));
}

function dedupeSessions(sessions: MobileSnapshot["sessions"][number][]): MobileSnapshot["sessions"][number][] {
  const map = new Map<string, MobileSnapshot["sessions"][number]>();
  for (const session of sessions) {
    const existing = map.get(session.id);
    if (!existing) {
      map.set(session.id, session);
      continue;
    }
    if (sessionDurationMs(session) > sessionDurationMs(existing)) {
      map.set(session.id, session);
    }
  }
  return Array.from(map.values());
}

export function getPeriodSessions(snapshot: MobileSnapshot, period: StatsPeriod, now = new Date()) {
  const range = buildDateRange(period, now);
  return getValidSessions(snapshot).filter((session) => {
    const date = parseDateLike(session.dateKey || session.startAt);
    return date && range.includes(date);
  });
}

export function getDisplayableBooks(snapshot: MobileSnapshot): MobileBook[] {
  return snapshot.books.filter(isBookDisplayable);
}

export function hasBookBeenRead(snapshot: MobileSnapshot, bookId: string): boolean {
  const progress = snapshot.progress.find((p) => p.bookId === bookId);
  if (progress && isValidNumber(progress.progressPercent) && progress.progressPercent > 0) return true;
  return snapshot.sessions.some((s) => s.bookId === bookId && sessionDurationMs(s) > 0);
}

export function isBookCompleted(snapshot: MobileSnapshot, bookId: string): boolean {
  const progress = snapshot.progress.find((p) => p.bookId === bookId);
  if (!progress) return false;
  if (progress.completionState === "completed") return true;
  return isValidNumber(progress.progressPercent) && progress.progressPercent >= 99.5;
}

export function isBookReading(snapshot: MobileSnapshot, bookId: string): boolean {
  if (isBookCompleted(snapshot, bookId)) return false;
  return hasBookBeenRead(snapshot, bookId);
}

export function buildBookStatusCount(snapshot: MobileSnapshot): BookStatusCount {
  const displayable = getDisplayableBooks(snapshot);
  let reading = 0;
  let completed = 0;
  let unread = 0;
  for (const book of displayable) {
    if (isBookCompleted(snapshot, book.id)) {
      completed++;
    } else if (isBookReading(snapshot, book.id)) {
      reading++;
    } else {
      unread++;
    }
  }
  return {
    reading,
    completed,
    unread,
    unreadable: snapshot.books.length - displayable.length,
    total: snapshot.books.length
  };
}

export function buildMobileStatsSummary(
  snapshot: MobileSnapshot,
  period: StatsPeriod = "total",
  now = new Date()
): MobileStatsSummary {
  const periodSessions = getPeriodSessions(snapshot, period, now);
  const totalReadingMs = periodSessions.reduce((sum, s) => sum + sessionDurationMs(s), 0);

  const readingDaysSet = new Set<string>();
  for (const session of periodSessions) {
    readingDaysSet.add(sessionDateKey(session));
  }

  const status = buildBookStatusCount(snapshot);
  const readBooks = getDisplayableBooks(snapshot).filter((b) => hasBookBeenRead(snapshot, b.id)).length;

  const words = estimateReadingWords(snapshot, periodSessions, totalReadingMs);
  const speed = totalReadingMs > 0 ? Math.round(words / Math.max(1, totalReadingMs / 60_000)) : 0;

  const range = buildDateRange(period, now);
  const noteCount = snapshot.notes.filter((item) => {
    const date = parseDateLike(item.createdAt ?? item.updatedAt);
    return date && range.includes(date);
  }).length;
  const inspirationCount = snapshot.inspirations.filter((item) => {
    const date = parseDateLike(item.createdAt ?? item.updatedAt);
    return date && range.includes(date);
  }).length;

  return {
    totalReadingMs,
    readingDays: readingDaysSet.size,
    completed: status.completed,
    readingBooks: status.reading,
    readBooks,
    unreadBooks: status.unread,
    unreadableBooks: status.unreadable,
    totalDisplayableBooks: status.total,
    words,
    speed,
    sessionCount: periodSessions.length,
    noteCount,
    inspirationCount
  };
}

function estimateReadingWords(
  snapshot: MobileSnapshot,
  sessions: MobileSnapshot["sessions"][number][],
  totalReadingMs: number
): number {
  const bookMap = new Map(snapshot.books.map((b) => [b.id, b]));
  let totalWords = 0;
  for (const session of sessions) {
    const book = bookMap.get(session.bookId);
    if (!book || !isValidNumber(book.size) || book.size <= 0) continue;
    const start = session.startLocation?.progressPercent ?? 0;
    const end = session.endLocation?.progressPercent ?? session.startLocation?.progressPercent ?? 0;
    const delta = Math.max(0, Math.min(100, end) - Math.max(0, Math.min(100, start)));
    const bookWords = Math.max(1, Math.round(book.size / 3));
    totalWords += Math.round((bookWords * delta) / 100);
  }
  if (totalWords > 0) return totalWords;
  // 无会话进度时，用当前进度做保守估算，但不上浮
  for (const progress of snapshot.progress) {
    const book = bookMap.get(progress.bookId);
    if (!book || !isValidNumber(book.size) || book.size <= 0) continue;
    const pct = isValidNumber(progress.progressPercent) ? progress.progressPercent : 0;
    const bookWords = Math.max(1, Math.round(book.size / 3));
    totalWords += Math.round((bookWords * Math.min(100, Math.max(0, pct))) / 100);
  }
  return totalWords;
}

export function buildReadingTrend(
  snapshot: MobileSnapshot,
  period: StatsPeriod,
  now = new Date()
): TrendItem[] {
  const range = buildDateRange(period, now);
  const groups = new Map<string, TrendItem>();
  for (const session of getValidSessions(snapshot)) {
    const date = parseDateLike(session.dateKey || session.startAt);
    if (!date || !range.includes(date)) continue;
    const dateKey = sessionDateKey(session);
    const existing = groups.get(dateKey);
    if (existing) {
      existing.durationMs += sessionDurationMs(session);
      existing.sessionCount += 1;
    } else {
      const label = `${date.getMonth() + 1}/${date.getDate()}`;
      groups.set(dateKey, {
        dateKey,
        label,
        durationMs: sessionDurationMs(session),
        sessionCount: 1
      });
    }
  }

  if (period === "day") {
    // 按小时聚合（UI 当前未展示“日”，保留语义一致性）
    return Array.from(groups.values()).sort((a, b) => a.dateKey.localeCompare(b.dateKey));
  }

  if (period === "week") {
    return fillDailyTrend(range, groups);
  }

  if (period === "month") {
    return fillWeeklyTrend(range, groups);
  }

  if (period === "year") {
    return fillMonthlyTrend(range, groups);
  }

  // total：按月份聚合；若跨度超过 2 年则按年聚合。
  // 显示范围应从最早有数据的日期开始，而不是从 1970 年开始，避免大量空白柱形。
  if (groups.size === 0) return [];
  const firstDate = getEarliestTrendDate(groups, range.start);
  const lastDate = getLatestTrendDate(groups, range.end);
  const spanDays = Math.max(0, (lastDate.getTime() - firstDate.getTime()) / (24 * 60 * 60 * 1000));
  const displayStart = startOfDay(firstDate);
  const displayEnd = startOfDay(lastDate);
  if (spanDays > 730) {
    displayStart.setMonth(0, 1);
    displayEnd.setFullYear(lastDate.getFullYear() + 1, 0, 1);
  } else {
    displayStart.setDate(1);
    displayEnd.setFullYear(lastDate.getFullYear(), lastDate.getMonth() + 1, 1);
  }
  const displayRange: DateRange = {
    start: displayStart,
    end: displayEnd,
    includes: range.includes
  };
  if (spanDays > 730) {
    return fillYearlyTrend(displayRange, groups);
  }
  return fillMonthlyTrend(displayRange, groups);
}

function fillDailyTrend(range: DateRange, groups: Map<string, TrendItem>): TrendItem[] {
  const items: TrendItem[] = [];
  for (let d = new Date(range.start); d < range.end; d.setDate(d.getDate() + 1)) {
    const dateKey = toDateKey(d);
    const existing = groups.get(dateKey);
    items.push(
      existing ?? {
        dateKey,
        label: `${d.getMonth() + 1}/${d.getDate()}`,
        durationMs: 0,
        sessionCount: 0
      }
    );
  }
  return items;
}

function fillWeeklyTrend(range: DateRange, groups: Map<string, TrendItem>): TrendItem[] {
  const items: TrendItem[] = [];
  let weekIndex = 1;
  for (let start = new Date(range.start); start < range.end; ) {
    const end = new Date(start);
    end.setDate(end.getDate() + 7);
    if (end > range.end) end.setTime(range.end.getTime());
    let durationMs = 0;
    let sessionCount = 0;
    for (let d = new Date(start); d < end; d.setDate(d.getDate() + 1)) {
      const key = toDateKey(d);
      const existing = groups.get(key);
      if (existing) {
        durationMs += existing.durationMs;
        sessionCount += existing.sessionCount;
      }
    }
    items.push({
      dateKey: toDateKey(start),
      label: `第${weekIndex}周`,
      durationMs,
      sessionCount
    });
    start = new Date(end);
    weekIndex++;
  }
  return items;
}

function fillMonthlyTrend(range: DateRange, groups: Map<string, TrendItem>): TrendItem[] {
  const items: TrendItem[] = [];
  for (let d = new Date(range.start); d < range.end; ) {
    const year = d.getFullYear();
    const month = d.getMonth();
    const next = new Date(year, month + 1, 1);
    const monthEnd = next > range.end ? new Date(range.end) : new Date(next);
    let durationMs = 0;
    let sessionCount = 0;
    for (let cursor = new Date(d); cursor < monthEnd; cursor.setDate(cursor.getDate() + 1)) {
      const key = toDateKey(cursor);
      const existing = groups.get(key);
      if (existing) {
        durationMs += existing.durationMs;
        sessionCount += existing.sessionCount;
      }
    }
    items.push({
      dateKey: `${year}-${String(month + 1).padStart(2, "0")}`,
      label: `${month + 1}月`,
      durationMs,
      sessionCount
    });
    d = new Date(next);
  }
  return items;
}

function fillYearlyTrend(range: DateRange, groups: Map<string, TrendItem>): TrendItem[] {
  const items: TrendItem[] = [];
  for (let year = range.start.getFullYear(); year < range.end.getFullYear(); year++) {
    const yearStart = new Date(year, 0, 1);
    const yearEnd = new Date(year + 1, 0, 1);
    let durationMs = 0;
    let sessionCount = 0;
    for (let d = new Date(yearStart); d < yearEnd && d < range.end; d.setDate(d.getDate() + 1)) {
      const key = toDateKey(d);
      const existing = groups.get(key);
      if (existing) {
        durationMs += existing.durationMs;
        sessionCount += existing.sessionCount;
      }
    }
    items.push({
      dateKey: String(year),
      label: `${year}年`,
      durationMs,
      sessionCount
    });
  }
  return items;
}

function getEarliestTrendDate(groups: Map<string, TrendItem>, fallback: Date): Date {
  const keys = Array.from(groups.keys()).sort();
  if (keys.length === 0) return new Date(fallback);
  const parsed = parseDateLike(keys[0]);
  return parsed ? parsed : new Date(fallback);
}

function getLatestTrendDate(groups: Map<string, TrendItem>, fallback: Date): Date {
  const keys = Array.from(groups.keys()).sort();
  if (keys.length === 0) return new Date(fallback);
  const parsed = parseDateLike(keys[keys.length - 1]);
  return parsed ? parsed : new Date(fallback);
}

export function formatCompactDuration(ms: number): string {
  if (!isValidNumber(ms) || ms <= 0) return "0 分钟";
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const remainMinutes = minutes % 60;
  if (remainMinutes === 0) return `${hours} 小时`;
  const decimalTenths = Math.round((remainMinutes / 60) * 10);
  if (decimalTenths >= 10) return `${hours + 1} 小时`;
  return `${hours}.${decimalTenths} 小时`;
}

export function formatFullDuration(ms: number): string {
  if (!isValidNumber(ms) || ms <= 0) return "0 分钟";
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const remainMinutes = minutes % 60;
  if (remainMinutes === 0) return `${hours} 小时`;
  return `${hours} 小时 ${remainMinutes} 分钟`;
}

export function buildReadingStreak(snapshot: MobileSnapshot, now = new Date()): { current: number; longest: number } {
  const dateKeys = new Set<string>();
  for (const session of getValidSessions(snapshot)) {
    dateKeys.add(sessionDateKey(session));
  }
  if (dateKeys.size === 0) return { current: 0, longest: 0 };
  const sorted = Array.from(dateKeys).sort();

  let longest = 1;
  let temp = 1;
  for (let i = 1; i < sorted.length; i++) {
    const prev = parseDateLike(sorted[i - 1]);
    const curr = parseDateLike(sorted[i]);
    if (!prev || !curr) continue;
    const diff = Math.round((startOfDay(curr).getTime() - startOfDay(prev).getTime()) / (24 * 60 * 60 * 1000));
    if (diff === 1) {
      temp++;
      longest = Math.max(longest, temp);
    } else {
      temp = 1;
    }
  }

  const today = toDateKey(startOfDay(now));
  const yesterdayDate = new Date(now);
  yesterdayDate.setDate(yesterdayDate.getDate() - 1);
  const yesterday = toDateKey(startOfDay(yesterdayDate));
  let current = 0;
  let cursor = new Date(now);
  if (!dateKeys.has(today)) {
    cursor.setDate(cursor.getDate() - 1);
  }
  while (dateKeys.has(toDateKey(startOfDay(cursor)))) {
    current++;
    cursor.setDate(cursor.getDate() - 1);
  }

  return { current, longest };
}
