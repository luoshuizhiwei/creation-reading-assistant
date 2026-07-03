import type { MobileBook, MobileInspiration, MobileNote, MobileSnapshot } from "../types/mobile";

export type StatsPeriod = "day" | "week" | "month" | "year" | "total";

export const statsPeriodLabels: Record<StatsPeriod, string> = {
  day: "日",
  week: "周",
  month: "月",
  year: "年",
  total: "总"
};

export interface MobileStatsSummary {
  totalReadingMs: number;
  readingDays: number;
  completed: number;
  readingBooks: number;
  words: number;
  speed: number;
  sessionCount: number;
  noteCount: number;
  inspirationCount: number;
}

export interface ReadingTimelineItem {
  dateKey: string;
  label: string;
  durationMs: number;
  sessionCount: number;
  bookTitles: string[];
  noteCount: number;
}

export interface BookRankingItem {
  bookId: string;
  title: string;
  author?: string;
  durationMs: number;
  progressPercent: number;
  format: string;
}

export interface NoteInsightItem {
  id: string;
  kind: "灵感" | "笔记";
  title: string;
  excerpt: string;
  bookTitle?: string;
  createdAt: string;
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

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, Math.min(date.getDate(), 28));
}

function periodStart(period: StatsPeriod, now = new Date()): Date | undefined {
  const today = startOfDay(now);
  if (period === "day") return today;
  if (period === "week") {
    const day = today.getDay() || 7;
    return addDays(today, 1 - day);
  }
  if (period === "month") return new Date(today.getFullYear(), today.getMonth(), 1);
  if (period === "year") return new Date(today.getFullYear(), 0, 1);
  return undefined;
}

function periodEnd(period: StatsPeriod, now = new Date()): Date | undefined {
  const start = periodStart(period, now);
  if (!start) return undefined;
  if (period === "day") return addDays(start, 1);
  if (period === "week") return addDays(start, 7);
  if (period === "month") return addMonths(start, 1);
  if (period === "year") return new Date(start.getFullYear() + 1, 0, 1);
  return undefined;
}

function withinPeriod(value: string | undefined, period: StatsPeriod, now = new Date()): boolean {
  if (period === "total") return true;
  const date = parseDateLike(value);
  const start = periodStart(period, now);
  const end = periodEnd(period, now);
  if (!date || !start || !end) return false;
  const time = startOfDay(date).getTime();
  return time >= start.getTime() && time < end.getTime();
}

function durationOfSession(session: MobileSnapshot["sessions"][number]): number {
  return Math.max(0, session.activeDurationMs || session.durationMs || 0);
}

function bookById(snapshot: MobileSnapshot): Map<string, MobileBook> {
  return new Map(snapshot.books.map((book) => [book.id, book]));
}

function estimatedReadWords(snapshot: MobileSnapshot): number {
  const books = bookById(snapshot);
  return snapshot.progress.reduce((sum, item) => {
    const book = books.get(item.bookId);
    const estimatedBookWords = Math.max(0, Math.round((book?.size ?? 0) / 2));
    return sum + Math.round(estimatedBookWords * Math.min(100, Math.max(0, item.progressPercent)) / 100);
  }, 0);
}

function formatMonthDay(date: Date): string {
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

function createdInPeriod(item: MobileNote | MobileInspiration, period: StatsPeriod, now = new Date()): boolean {
  return withinPeriod(item.createdAt ?? item.updatedAt, period, now);
}

export function getStatsPeriodTitle(period: StatsPeriod, now = new Date()): string {
  const start = periodStart(period, now);
  const end = periodEnd(period, now);
  if (period === "day") return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`;
  if (period === "week" && start && end) return `${formatMonthDay(start)} - ${formatMonthDay(addDays(end, -1))}`;
  if (period === "month") return `${now.getFullYear()}年${now.getMonth() + 1}月`;
  if (period === "year") return `${now.getFullYear()}年`;
  return "全部记录";
}

export function shiftStatsPeriodAnchor(date: Date, period: StatsPeriod, direction: -1 | 1): Date {
  if (period === "day") return addDays(date, direction);
  if (period === "week") return addDays(date, direction * 7);
  if (period === "month") return addMonths(date, direction);
  if (period === "year") return new Date(date.getFullYear() + direction, date.getMonth(), Math.min(date.getDate(), 28));
  return date;
}

export function isCurrentStatsPeriod(period: StatsPeriod, anchor: Date, now = new Date()): boolean {
  if (period === "total") return true;
  return periodStart(period, anchor)?.getTime() === periodStart(period, now)?.getTime();
}

export function buildMobileStatsSummary(snapshot: MobileSnapshot, period: StatsPeriod = "total", now = new Date()): MobileStatsSummary {
  const periodSessions = snapshot.sessions.filter((session) => withinPeriod(session.dateKey || session.startAt, period, now));
  const overallReadingMs = snapshot.progress.reduce((sum, item) => sum + Math.max(0, item.totalReadingTimeMs || 0), 0);
  const sessionReadingMs = periodSessions.reduce((sum, session) => sum + durationOfSession(session), 0);
  const totalReadingMs = period === "total" ? Math.max(overallReadingMs, sessionReadingMs) : sessionReadingMs;
  const allReadWords = estimatedReadWords(snapshot);
  const words = overallReadingMs > 0 && period !== "total" ? Math.round(allReadWords * (totalReadingMs / overallReadingMs)) : allReadWords;
  const readingDays = new Set(periodSessions.map((session) => session.dateKey || toDateKey(parseDateLike(session.startAt) ?? new Date()))).size;
  return {
    totalReadingMs,
    readingDays,
    completed: snapshot.progress.filter((item) => item.completionState === "completed").length,
    readingBooks: snapshot.progress.filter((item) => item.completionState === "reading").length,
    words,
    speed: totalReadingMs > 0 ? Math.round(words / Math.max(1, totalReadingMs / 60_000)) : 0,
    sessionCount: periodSessions.length,
    noteCount: snapshot.notes.filter((item) => createdInPeriod(item, period, now)).length,
    inspirationCount: snapshot.inspirations.filter((item) => createdInPeriod(item, period, now)).length
  };
}

export function buildReadingTimeline(snapshot: MobileSnapshot, period: StatsPeriod, limit = 14, now = new Date()): ReadingTimelineItem[] {
  const books = bookById(snapshot);
  const groups = new Map<string, ReadingTimelineItem>();
  for (const session of snapshot.sessions.filter((item) => withinPeriod(item.dateKey || item.startAt, period, now))) {
    const dateKey = session.dateKey || toDateKey(parseDateLike(session.startAt) ?? new Date());
    const group =
      groups.get(dateKey) ??
      {
        dateKey,
        label: dateKey.slice(5),
        durationMs: 0,
        sessionCount: 0,
        bookTitles: [],
        noteCount: 0
      };
    group.durationMs += durationOfSession(session);
    group.sessionCount += 1;
    const title = books.get(session.bookId)?.title;
    if (title && !group.bookTitles.includes(title)) group.bookTitles.push(title);
    groups.set(dateKey, group);
  }
  for (const note of snapshot.notes) {
    const date = parseDateLike(note.createdAt);
    if (!date || !withinPeriod(note.createdAt, period, now)) continue;
    const dateKey = toDateKey(date);
    const group = groups.get(dateKey);
    if (group) group.noteCount += 1;
  }
  return Array.from(groups.values())
    .sort((left, right) => right.dateKey.localeCompare(left.dateKey))
    .slice(0, limit);
}

export function buildBookRanking(snapshot: MobileSnapshot, period: StatsPeriod, limit = 5, now = new Date()): BookRankingItem[] {
  const books = bookById(snapshot);
  const durationByBook = new Map<string, number>();
  if (period === "total") {
    for (const progress of snapshot.progress) {
      durationByBook.set(progress.bookId, Math.max(durationByBook.get(progress.bookId) ?? 0, progress.totalReadingTimeMs || 0));
    }
  }
  for (const session of snapshot.sessions.filter((item) => withinPeriod(item.dateKey || item.startAt, period, now))) {
    durationByBook.set(session.bookId, (durationByBook.get(session.bookId) ?? 0) + durationOfSession(session));
  }
  return Array.from(durationByBook.entries())
    .map(([bookId, durationMs]) => {
      const book = books.get(bookId);
      const progress = snapshot.progress.find((item) => item.bookId === bookId);
      return {
        bookId,
        title: book?.title ?? "未知书籍",
        author: book?.author,
        durationMs,
        progressPercent: progress?.progressPercent ?? 0,
        format: book?.format?.toUpperCase() ?? "BOOK"
      };
    })
    .filter((item) => item.durationMs > 0 || item.progressPercent > 0)
    .sort((left, right) => right.durationMs - left.durationMs || right.progressPercent - left.progressPercent)
    .slice(0, limit);
}

export function buildNoteInsights(snapshot: MobileSnapshot, period: StatsPeriod, limit = 5, now = new Date()): NoteInsightItem[] {
  const books = bookById(snapshot);
  const notes: NoteInsightItem[] = snapshot.notes
    .filter((item) => createdInPeriod(item, period, now))
    .map((item) => ({
      id: item.id,
      kind: "笔记",
      title: item.title || "阅读笔记",
      excerpt: item.excerpt || item.body || "暂无摘录",
      bookTitle: item.bookId ? books.get(item.bookId)?.title : undefined,
      createdAt: item.createdAt
    }));
  const inspirations: NoteInsightItem[] = snapshot.inspirations
    .filter((item) => createdInPeriod(item, period, now))
    .map((item) => ({
      id: item.id,
      kind: "灵感",
      title: item.title || "阅读灵感",
      excerpt: item.source?.excerpt || item.body || "暂无正文",
      bookTitle: item.source?.bookTitle,
      createdAt: item.createdAt
    }));
  return [...notes, ...inspirations]
    .sort((left, right) => (right.createdAt ?? "").localeCompare(left.createdAt ?? ""))
    .slice(0, limit);
}
