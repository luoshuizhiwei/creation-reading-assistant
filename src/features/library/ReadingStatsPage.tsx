import { useEffect, useMemo, useState } from "react";
import { BookOpen, CalendarDays, CalendarRange, ChevronLeft, ChevronRight, Clock3, Library, Minus, Sunrise, TrendingDown, TrendingUp } from "lucide-react";
import { Button } from "@/components/ui";
import { getReadingStats } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import { formatDuration, formatDate } from "@/utils/format";

type StatsRange = "day" | "week" | "month" | "year" | "all";

const rangeLabels: Record<StatsRange, string> = {
  day: "今日",
  week: "本周",
  month: "本月",
  year: "本年",
  all: "全部"
};

function calcPeriodDurations(
  stats: NonNullable<Awaited<ReturnType<typeof getReadingStats>>>,
  range: StatsRange,
  anchor: Date
): { totalMs: number; days: number } {
  if (range === "all") return { totalMs: stats.totalDurationMs, days: stats.readingDaysCount };

  const start = new Date(anchor);
  const end = new Date(anchor);
  start.setHours(0, 0, 0, 0);
  end.setHours(23, 59, 59, 999);
  if (range === "week") {
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
    end.setTime(start.getTime());
    end.setDate(start.getDate() + 6);
    end.setHours(23, 59, 59, 999);
  } else if (range === "month") {
    start.setDate(1);
    end.setFullYear(start.getFullYear(), start.getMonth() + 1, 0);
    end.setHours(23, 59, 59, 999);
  } else if (range === "year") {
    start.setMonth(0, 1);
    end.setFullYear(start.getFullYear(), 11, 31);
    end.setHours(23, 59, 59, 999);
  }

  const startKey = formatLocalDateKey(start);
  const endKey = formatLocalDateKey(end);
  let total = 0;
  let days = 0;
  for (const d of stats.daily) {
    if (d.dateKey >= startKey && d.dateKey <= endKey) {
      total += d.durationMs;
      if (d.durationMs > 0) days++;
    }
  }
  return { totalMs: total, days };
}

function formatLocalDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function shiftStatsAnchor(anchor: Date, range: StatsRange, direction: -1 | 1): Date {
  const next = new Date(anchor);
  if (range === "all") return next;
  if (range === "day") next.setDate(next.getDate() + direction);
  else if (range === "week") next.setDate(next.getDate() + direction * 7);
  else if (range === "month") next.setMonth(next.getMonth() + direction);
  else next.setFullYear(next.getFullYear() + direction);
  return next;
}

function isFutureStatsAnchor(anchor: Date, range: Exclude<StatsRange, "all">): boolean {
  const next = shiftStatsAnchor(anchor, range, 1);
  const today = new Date();
  today.setHours(23, 59, 59, 999);
  return next > today;
}

/** 会话状态/结束原因的用户措辞（内部枚举不直接暴露）。 */
const SESSION_STATUS_LABELS: Record<string, string> = {
  active: "正在阅读",
  paused: "已暂停",
  ended: "已结束",
  recovered: "已恢复"
};

const SESSION_END_REASON_LABELS: Record<string, string> = {
  "leave-reader": "离开阅读器",
  "switch-book": "切换书籍",
  "window-close": "关闭窗口",
  "idle-timeout": "空闲超时",
  "crash-recovered": "异常恢复"
};

export function ReadingStatsPage() {
  const stats = useLibraryStore((state) => state.stats);
  const activeBook = useLibraryStore((state) => state.activeBook);
  const activeProgress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const setStats = useLibraryStore((state) => state.setStats);
  const setError = useAppStore((state) => state.setError);
  const setScreen = useAppStore((state) => state.setScreen);
  const [selectedRange, setSelectedRange] = useState<StatsRange>("week");
  const [anchorDate, setAnchorDate] = useState(() => new Date());

  useEffect(() => {
    getReadingStats()
      .then(setStats)
      .catch((error) => setError(error instanceof Error ? error.message : String(error)));
  }, [setError, setStats]);

  const maxDailyDuration = useMemo(
    () => Math.max(1, ...(stats?.daily.map((item) => item.durationMs) ?? [1])),
    [stats]
  );

  const periodInfo = useMemo(
    () => {
      if (!stats) return undefined;
      const previousAnchor = selectedRange === "all" ? anchorDate : shiftStatsAnchor(anchorDate, selectedRange, -1);
      return {
        current: calcPeriodDurations(stats, selectedRange, anchorDate),
        previous: calcPeriodDurations(stats, selectedRange, previousAnchor)
      };
    },
    [stats, selectedRange, anchorDate]
  );

  const changeRange = (range: StatsRange) => {
    setSelectedRange(range);
    setAnchorDate(new Date());
  };

  const nextPeriodIsFuture = selectedRange !== "all" && isFutureStatsAnchor(anchorDate, selectedRange);

  const pctChange = (current: number, previous: number) => {
    if (previous <= 0) return null;
    return Math.round(((current - previous) / previous) * 100);
  };

  const renderComparisonRow = () => {
    if (!periodInfo) return null;
    if (!periodInfo.previous.totalMs && !periodInfo.previous.days) return null;
    const metrics: Array<[string, number, number]> = [
      ["时长", periodInfo.current.totalMs, periodInfo.previous.totalMs],
      ["天数", periodInfo.current.days, periodInfo.previous.days],
    ];
    return (
      <div className="motion-panel rounded-md bg-paper-panel p-3 [box-shadow:var(--shadow-1)]">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 overflow-x-auto">
          <span className="text-xs font-semibold text-paper-muted">较上一周期</span>
          {metrics.map(([label, currentVal, prevVal]) => {
            const diff = currentVal - prevVal;
            const absDiff = diff < 0 ? -diff : diff;
            const pct = label === "天数" ? null : pctChange(currentVal, prevVal);
            if (prevVal <= 0 && currentVal <= 0) return null;
            const trendClass = diff > 0 ? "text-moss" : diff < 0 ? "text-[color:var(--proof-mark)]" : "text-paper-muted opacity-50";
            const TrendIcon = diff > 0 ? TrendingUp : diff < 0 ? TrendingDown : Minus;
            const valueText =
              label === "天数"
                ? `${diff > 0 ? "多" : diff < 0 ? "少" : ""}${absDiff} 天`
                : `${diff > 0 ? "+" : diff < 0 ? "-" : ""}${formatDuration(absDiff)}`;
            return (
              <span key={label} className={`flex items-center gap-1.5 text-sm ${trendClass}`}>
                <TrendIcon size={13} className="shrink-0" />
                <span className="text-xs text-paper-muted">{label}</span>
                <span>{valueText}</span>
                {pct !== null && <span className="text-xs opacity-70">({diff > 0 ? "+" : ""}{pct}%)</span>}
              </span>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div className="desktop-page-scroll paper-shell">
      <div className="desktop-page-stack">
        <section className="desktop-page-hero motion-panel">
          <div>
            <div className="desktop-card-label">Reading rhythm</div>
            <h2>阅读统计仪表板</h2>
            <p>基于有效阅读会话聚合，不按单纯打开时长计算；桌面端用于复盘节奏、书籍推进和素材沉淀。</p>
          </div>
          <div className="desktop-page-actions">
            <Button variant="quiet" onClick={() => setScreen("library")}>
              <BookOpen size={16} />
              打开书库
            </Button>
          </div>
        </section>

        {!stats ? (
          <ReadingStatsSkeleton />
        ) : (
          <div className="grid gap-5">
            {/* Period selector + nav */}
            <div className="desktop-panel-card flex items-end justify-between gap-4 p-4">
              <div>
                <h1 className="paper-title text-2xl font-semibold">阅读与沉淀节律</h1>
                <p className="mt-1 text-sm text-paper-muted">统计只计算有效阅读会话，后台挂起和空闲时间不会膨胀数据。</p>
              </div>
              <div className="flex items-center gap-3">
                <div className="range-tabs inline-flex rounded-md border border-paper-line bg-paper-soft/50 p-1">
                  {(Object.keys(rangeLabels) as StatsRange[]).map((r) => (
                    <button
                      key={r}
                      className={`rounded-md px-3 py-1 text-xs font-medium transition ${
                        selectedRange === r ? "bg-copper text-[color:var(--fg-on-solid)]" : "text-paper-muted hover:text-paper-ink"
                      }`}
                      onClick={() => changeRange(r)}
                    >
                      {rangeLabels[r]}
                    </button>
                  ))}
                </div>
                {selectedRange !== "all" && (
                  <div className="flex items-center gap-1">
                    <Button
                      variant="quiet"
                      className="px-2"
                      aria-label="上一周期"
                      onClick={() => setAnchorDate((d) => {
                        return shiftStatsAnchor(d, selectedRange, -1);
                      })}
                    >
                      <ChevronLeft size={15} />
                    </Button>
                    <span className="text-sm font-medium text-paper-ink min-w-[100px] text-center">
                      {new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long", day: "numeric" }).format(anchorDate)}
                    </span>
                    <Button
                      variant="quiet"
                      className="px-2"
                      aria-label="下一周期"
                      disabled={nextPeriodIsFuture}
                      onClick={() => setAnchorDate((d) => {
                        return shiftStatsAnchor(d, selectedRange, 1);
                      })}
                    >
                      <ChevronRight size={15} />
                    </Button>
                  </div>
                )}
              </div>
            </div>

            <section className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
              {[
                ["今日", stats.todayDurationMs, <Sunrise size={14} key="i" />],
                ["近 7 天", stats.last7DaysDurationMs, <CalendarDays size={14} key="i" />],
                ["近 30 天", stats.last30DaysDurationMs, <CalendarRange size={14} key="i" />],
                ["全部", stats.totalDurationMs, <Library size={14} key="i" />],
                [activeBook ? `当前书：${activeBook.title}` : "当前书", activeProgress?.totalReadingTimeMs ?? 0, <BookOpen size={14} key="i" />]
              ].map(([label, value, icon]) => (
                <div key={label as string} className="stats-card reading-stat-tile motion-panel">
                  <div className="reading-stat-head">
                    {icon}
                    <span className="truncate">{label}</span>
                  </div>
                  <div className="reading-stat-value font-mono">{formatDuration(Number(value))}</div>
                </div>
              ))}
            </section>

            {/* Period comparison row */}
            {renderComparisonRow()}

            <section className="desktop-stats-grid">
              <div className="grid gap-5">
                <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
                  <div className="mb-3 flex items-center justify-between">
                    <h2 className="paper-title text-base font-semibold">按书统计</h2>
                    <span className="text-xs text-paper-muted">平均单次 {formatDuration(stats.averageSessionDurationMs)}</span>
                  </div>
                  {stats.byBook.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">还没有可统计的阅读时长。</div>
                  ) : (
                    <div className="grid gap-2">
                      {stats.byBook.map((book) => {
                        const pct = Math.round((book.progressPercent ?? 0) * 100);
                        return (
                          <div key={book.bookId} className="grid grid-cols-[1fr_110px_100px] items-center gap-3 rounded-md border border-paper-line bg-paper-soft/60 px-3 py-2 text-sm">
                            <div className="min-w-0">
                              <div className="truncate font-medium text-paper-ink">{book.title}</div>
                              <div className="text-xs uppercase text-paper-muted">{book.format}</div>
                            </div>
                            <div className="text-right text-paper-muted">{formatDuration(book.totalDurationMs)}</div>
                            <div className="flex items-center gap-2">
                              <div className="stats-mini-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}>
                                <div className="stats-mini-bar-fill" style={{ width: `${pct}%` }} />
                              </div>
                              <span className="w-8 shrink-0 text-right text-xs text-paper-muted">{pct}%</span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
                  <h2 className="paper-title mb-3 text-base font-semibold">最近阅读书籍</h2>
                  {stats.recentBooks.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">打开并阅读书籍后会出现在这里。</div>
                  ) : (
                    <div className="grid gap-2">
                      {stats.recentBooks.map((book) => {
                        const pct = Math.round(book.progressPercent * 100);
                        return (
                          <div key={book.bookId} className="grid grid-cols-[1fr_100px_100px] items-center gap-3 rounded-md border border-paper-line bg-paper-soft/60 px-3 py-2 text-sm">
                            <div className="min-w-0">
                              <div className="truncate font-medium text-paper-ink">{book.title}</div>
                              <div className="text-xs text-paper-muted">{formatDate(book.lastReadAt)}</div>
                            </div>
                            <div className="text-right text-paper-muted">{formatDuration(book.totalDurationMs)}</div>
                            <div className="flex items-center gap-2">
                              <div className="stats-mini-bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}>
                                <div className="stats-mini-bar-fill" style={{ width: `${pct}%` }} />
                              </div>
                              <span className="w-8 shrink-0 text-right text-xs text-paper-muted">{pct}%</span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
                  <h2 className="paper-title mb-3 text-base font-semibold">近 30 天节律</h2>
                  <div className="stats-rhythm-chart">
                    {stats.daily.slice(-30).map((day) => (
                      <div key={day.dateKey} className="stats-rhythm-col" title={`${day.dateKey} · ${formatDuration(day.durationMs)}`}>
                        <div
                          className="stats-rhythm-bar"
                          style={{ height: `${Math.max(4, (day.durationMs / maxDailyDuration) * 120)}px` }}
                        />
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
                <div className="mb-3 flex items-center gap-2">
                  <Clock3 size={16} />
                  <h2 className="paper-title text-base font-semibold">最近会话</h2>
                </div>
                <div className="grid gap-2">
                  {stats.recentSessions.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">阅读器产生有效时长后会出现在这里。</div>
                  ) : (
                    stats.recentSessions.map((session) => (
                      <div key={session.id} className="rounded-md border border-paper-line bg-paper-soft/60 px-3 py-2 text-sm">
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-paper-muted">{formatDate(session.startAt)}</span>
                          <span className="font-medium text-paper-ink">{formatDuration(session.activeDurationMs)}</span>
                        </div>
                        <div className="mt-1 text-xs text-paper-muted">
                          {SESSION_STATUS_LABELS[session.status]}
                          {session.status === "active" ? " · 进行中" : session.endReason ? ` · ${SESSION_END_REASON_LABELS[session.endReason]}` : ""}
                        </div>
                      </div>
                    ))
                  )}
                </div>
                <div className="mt-4 rounded-md bg-paper-soft/60 px-3 py-2 text-xs leading-5 text-paper-muted">
                  平均单次阅读：{formatDuration(stats.averageSessionDurationMs)}
                </div>
              </div>
            </section>
          </div>
        )}
      </div>
    </div>
  );
}

function ReadingStatsSkeleton() {
  return (
    <div className="grid gap-5" aria-busy="true">
      <span className="sr-only">正在读取统计…</span>
      {/* Period selector skeleton */}
      <div className="desktop-panel-card flex items-end justify-between gap-4 p-4">
        <div>
          <div className="h-7 w-48 animate-pulse rounded-md bg-paper-soft" />
          <div className="mt-2 h-4 w-80 animate-pulse rounded-md bg-paper-soft/60" />
        </div>
        <div className="h-8 w-44 animate-pulse rounded-md bg-paper-soft/60" />
      </div>

      {/* 5 stat cards skeleton */}
      <section className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="stats-card reading-stat-tile motion-panel animate-pulse">
            <div className="flex items-center gap-2">
              <div className="h-3.5 w-3.5 rounded bg-paper-soft" />
              <div className="h-3 w-16 rounded bg-paper-soft/60" />
            </div>
            <div className="mt-2 h-6 w-24 rounded bg-paper-soft" />
          </div>
        ))}
      </section>

      {/* Grid columns skeleton */}
      <section className="desktop-stats-grid">
        <div className="grid gap-5">
          {/* By book card */}
          <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
            <div className="mb-4 flex items-center justify-between">
              <div className="h-5 w-24 animate-pulse rounded bg-paper-soft" />
              <div className="h-3.5 w-20 animate-pulse rounded bg-paper-soft/60" />
            </div>
            <div className="grid gap-2">
              {[0, 1, 2].map((i) => (
                <div key={i} className="flex h-12 items-center justify-between rounded-md border border-paper-line bg-paper-soft/40 px-3 animate-pulse">
                  <div className="h-4 w-36 rounded bg-paper-soft" />
                  <div className="h-4 w-16 rounded bg-paper-soft/60" />
                </div>
              ))}
            </div>
          </div>

          {/* Rhythm chart card */}
          <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
            <div className="mb-4 h-5 w-28 animate-pulse rounded bg-paper-soft" />
            <div className="flex h-28 items-end gap-1.5 pt-4">
              {[40, 65, 25, 80, 50, 95, 30, 60, 45, 70, 85, 35, 55, 90, 60].map((h, i) => (
                <div
                  key={i}
                  className="flex-1 animate-pulse rounded-t bg-paper-soft/50"
                  style={{ height: `${h}%` }}
                />
              ))}
            </div>
          </div>
        </div>

        {/* Sessions card */}
        <div className="motion-panel rounded-md bg-paper-panel p-4 [box-shadow:var(--shadow-1)]">
          <div className="mb-4 flex items-center gap-2">
            <div className="h-4 w-4 rounded bg-paper-soft animate-pulse" />
            <div className="h-5 w-24 rounded bg-paper-soft animate-pulse" />
          </div>
          <div className="grid gap-2">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="rounded-md border border-paper-line bg-paper-soft/40 p-3 animate-pulse">
                <div className="flex justify-between">
                  <div className="h-3.5 w-24 rounded bg-paper-soft/60" />
                  <div className="h-3.5 w-14 rounded bg-paper-soft" />
                </div>
                <div className="mt-2 h-3 w-16 rounded bg-paper-soft/40" />
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
