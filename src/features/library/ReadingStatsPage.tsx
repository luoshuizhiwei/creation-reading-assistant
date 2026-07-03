import { useEffect, useMemo, useState } from "react";
import { BookOpen, Clock3 } from "lucide-react";
import { Button, EmptyState } from "@/components/ui";
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

function shiftStatsAnchor(anchor: Date, range: Exclude<StatsRange, "all">, direction: -1 | 1): Date {
  const next = new Date(anchor);
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
      <div className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-3 shadow-lift">
        <div className="flex items-center gap-2 text-xs font-semibold text-paper-muted mb-2">
          <span>较上一周期</span>
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-2 overflow-x-auto">
          {metrics.map(([label, currentVal, prevVal]) => {
            const diff = currentVal - prevVal;
            const absDiff = diff < 0 ? -diff : diff;
            const pct = pctChange(currentVal, prevVal);
            if (prevVal <= 0 && currentVal <= 0) return null;
            const trendClass = diff > 0 ? "text-moss" : diff < 0 ? "text-red-600" : "text-paper-muted opacity-50";
            return (
              <span key={label} className={`flex items-center gap-1 text-sm ${trendClass}`}>
                <span className="text-xs text-paper-muted">{label}</span>
                <span>{diff > 0 ? "+" : ""}{absDiff}</span>
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
          <div className="desktop-panel-card desktop-empty-wrap">
            <EmptyState title="正在读取统计" body="阅读会话保存在本地 JSON 中，统计会从会话日志即时聚合。" />
          </div>
        ) : (
          <div className="grid gap-5">
            {/* Period selector + nav */}
            <div className="desktop-panel-card flex items-end justify-between gap-4 p-4">
              <div>
                <h1 className="paper-title text-2xl font-semibold">阅读与沉淀节律</h1>
                <p className="mt-1 text-sm text-paper-muted">统计只计算有效阅读会话，后台挂起和空闲时间不会膨胀数据。</p>
              </div>
              <div className="flex items-center gap-3">
                <div className="range-tabs inline-flex rounded-lg border border-paper-line bg-paper-soft/50 p-1">
                  {(Object.keys(rangeLabels) as StatsRange[]).map((r) => (
                    <button
                      key={r}
                      className={`rounded-md px-3 py-1 text-xs font-medium transition ${
                        selectedRange === r ? "bg-copper text-white shadow-lift" : "text-paper-muted hover:text-paper-ink"
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
                      onClick={() => setAnchorDate((d) => {
                        return shiftStatsAnchor(d, selectedRange, -1);
                      })}
                    >
                      ◀
                    </Button>
                    <span className="text-sm font-medium text-paper-ink min-w-[100px] text-center">
                      {new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long", day: "numeric" }).format(anchorDate)}
                    </span>
                    <Button
                      variant="quiet"
                      className="px-2"
                      disabled={nextPeriodIsFuture}
                      onClick={() => setAnchorDate((d) => {
                        return shiftStatsAnchor(d, selectedRange, 1);
                      })}
                    >
                      ▶
                    </Button>
                  </div>
                )}
              </div>
            </div>

            <section className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
              {[
                ["今日", stats.todayDurationMs],
                ["近 7 天", stats.last7DaysDurationMs],
                ["近 30 天", stats.last30DaysDurationMs],
                ["全部", stats.totalDurationMs],
                [activeBook ? `当前书：${activeBook.title}` : "当前书", activeProgress?.totalReadingTimeMs ?? 0]
              ].map(([label, value]) => (
                <div key={label} className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
                  <div className="truncate text-xs font-medium text-paper-muted">{label}</div>
                  <div className="mt-2 font-mono text-2xl font-semibold text-copper">{formatDuration(Number(value))}</div>
                </div>
              ))}
            </section>

            {/* Period comparison row */}
            {renderComparisonRow()}

            <section className="desktop-stats-grid">
              <div className="grid gap-5">
                <div className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
                  <div className="mb-3 flex items-center justify-between">
                    <h2 className="paper-title text-base font-semibold">按书统计</h2>
                    <span className="text-xs text-paper-muted">平均单次 {formatDuration(stats.averageSessionDurationMs)}</span>
                  </div>
                  {stats.byBook.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">还没有可统计的阅读时长。</div>
                  ) : (
                    <div className="grid gap-2">
                      {stats.byBook.map((book) => (
                        <div key={book.bookId} className="grid grid-cols-[1fr_120px_70px] items-center gap-3 rounded-lg border border-paper-line bg-white/50 px-3 py-2 text-sm">
                          <div className="min-w-0">
                            <div className="truncate font-medium text-paper-ink">{book.title}</div>
                            <div className="text-xs uppercase text-paper-muted">{book.format}</div>
                          </div>
                          <div className="text-right text-paper-muted">{formatDuration(book.totalDurationMs)}</div>
                          <div className="text-right text-xs text-paper-muted">{Math.round((book.progressPercent ?? 0) * 100)}%</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
                  <h2 className="paper-title mb-3 text-base font-semibold">最近阅读书籍</h2>
                  {stats.recentBooks.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">打开并阅读书籍后会出现在这里。</div>
                  ) : (
                    <div className="grid gap-2">
                      {stats.recentBooks.map((book) => (
                        <div key={book.bookId} className="grid grid-cols-[1fr_100px_80px] items-center gap-3 rounded-lg border border-paper-line bg-white/50 px-3 py-2 text-sm">
                          <div className="min-w-0">
                            <div className="truncate font-medium text-paper-ink">{book.title}</div>
                            <div className="text-xs text-paper-muted">{formatDate(book.lastReadAt)}</div>
                          </div>
                          <div className="text-right text-paper-muted">{formatDuration(book.totalDurationMs)}</div>
                          <div className="text-right text-xs text-paper-muted">{Math.round(book.progressPercent * 100)}%</div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
                  <h2 className="paper-title mb-3 text-base font-semibold">近 30 天节律</h2>
                  <div className="flex h-36 items-end justify-start gap-2 overflow-hidden rounded-lg border-b border-paper-line bg-paper-soft/25 px-3 pb-2">
                    {stats.daily.slice(-30).map((day) => (
                      <div key={day.dateKey} className="flex h-full w-4 shrink-0 flex-col items-center justify-end gap-1">
                        <div
                          className="w-3 rounded-t bg-copper/75 shadow-[0_-6px_18px_rgba(138,90,43,0.12)]"
                          title={`${day.dateKey} · ${formatDuration(day.durationMs)}`}
                          style={{ height: `${Math.max(4, (day.durationMs / maxDailyDuration) * 120)}px` }}
                        />
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              <div className="motion-panel rounded-xl border border-paper-line bg-paper-panel p-4 shadow-lift">
                <div className="mb-3 flex items-center gap-2">
                  <Clock3 size={16} />
                  <h2 className="paper-title text-base font-semibold">最近会话</h2>
                </div>
                <div className="grid gap-2">
                  {stats.recentSessions.length === 0 ? (
                    <div className="rounded-md bg-paper-soft/60 p-5 text-sm text-paper-muted">阅读器产生有效时长后会出现在这里。</div>
                  ) : (
                    stats.recentSessions.map((session) => (
                      <div key={session.id} className="rounded-lg border border-paper-line bg-white/50 px-3 py-2 text-sm">
                        <div className="flex items-center justify-between gap-3">
                          <span className="text-paper-muted">{formatDate(session.startAt)}</span>
                          <span className="font-medium text-paper-ink">{formatDuration(session.activeDurationMs)}</span>
                        </div>
                        <div className="mt-1 text-xs text-paper-muted">
                          {session.status} · {session.endReason ?? "进行中"}
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
