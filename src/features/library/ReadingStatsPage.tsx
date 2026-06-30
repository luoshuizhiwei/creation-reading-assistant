import { useEffect } from "react";
import { ArrowLeft, BarChart3, BookOpen, Clock3 } from "lucide-react";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { getReadingStats } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import { formatDuration, formatDate } from "@/utils/format";

export function ReadingStatsPage() {
  const stats = useLibraryStore((state) => state.stats);
  const activeBook = useLibraryStore((state) => state.activeBook);
  const activeProgress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const setStats = useLibraryStore((state) => state.setStats);
  const setError = useAppStore((state) => state.setError);
  const setScreen = useAppStore((state) => state.setScreen);

  useEffect(() => {
    getReadingStats()
      .then(setStats)
      .catch((error) => setError(error instanceof Error ? error.message : String(error)));
  }, [setError, setStats]);

  const maxDailyDuration = Math.max(1, ...(stats?.daily.map((item) => item.durationMs) ?? [1]));

  return (
    <div className="grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <header className="paper-topbar flex items-center gap-3 px-5">
        <BarChart3 size={18} />
        <div className="paper-title text-xl font-semibold text-copper">阅读统计</div>
        <div className="min-w-0 flex-1 text-sm text-paper-muted">基于有效阅读会话聚合，不按单纯打开时长计算</div>
        <Button variant="quiet" onClick={() => setScreen("library")}>
          <BookOpen size={16} />
          书库
        </Button>
        <Button variant="quiet" onClick={() => setScreen("start")}>
          <ArrowLeft size={16} />
          返回首页
        </Button>
      </header>

      <ShellPanel className="min-h-0 overflow-auto border-0 bg-transparent p-6 shadow-none">
        {!stats ? (
          <EmptyState title="正在读取统计" body="阅读会话保存在本地 JSON 中，统计会从会话日志即时聚合。" />
        ) : (
          <div className="mx-auto grid max-w-7xl gap-6">
            <div className="flex items-end justify-between border-b border-paper-line pb-4">
              <div>
                <h1 className="paper-title text-2xl font-semibold">阅读与沉淀节律</h1>
                <p className="mt-1 text-sm text-paper-muted">统计只计算有效阅读会话，后台挂起和空闲时间不会膨胀数据。</p>
              </div>
              <div className="moss-chip">阅读天数 {stats.readingDaysCount} 天</div>
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

            <section className="grid grid-cols-[1fr_380px] gap-5">
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
      </ShellPanel>
    </div>
  );
}

