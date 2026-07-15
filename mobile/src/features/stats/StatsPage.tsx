import { useMemo, useState } from "react";
import {
  BarChart3,
  Book,
  BookOpen,
  Calendar,
  CheckCircle,
  Clock,
  MessageSquare,
  Sparkles,
  Type,
  Gauge,
  Library,
  AlertCircle
} from "lucide-react";
import type { MobileSnapshot } from "../../services/mobile-storage";
import {
  buildBookStatusCount,
  buildMobileStatsSummary,
  buildReadingStreak,
  buildReadingTrend,
  formatCompactDuration,
  formatFullDuration,
  getStatsPeriodTitle,
  isCurrentStatsPeriod,
  shiftStatsPeriodAnchor,
  statsPeriodLabels,
  type StatsPeriod
} from "./statistics-helpers";

function SummaryCard({
  icon: Icon,
  value,
  label
}: {
  icon: React.ComponentType<{ size?: number | string; className?: string }>;
  value: string;
  label: string;
}) {
  return (
    <article className="stats-summary-card">
      <span className="stats-summary-icon" aria-hidden="true">
        <Icon size={18} />
      </span>
      <div className="stats-summary-text">
        <strong className="stats-summary-value">{value}</strong>
        <span className="stats-summary-label">{label}</span>
      </div>
    </article>
  );
}

function TrendChart({ items }: { items: ReturnType<typeof buildReadingTrend> }) {
  const maxMs = useMemo(() => Math.max(1, ...items.map((item) => item.durationMs)), [items]);
  if (items.length === 0) {
    return <p className="stats-empty">本期还没有阅读记录</p>;
  }
  return (
    <div className="stats-trend-chart" role="img" aria-label="阅读时长趋势">
      {items.map((item) => {
        const height = Math.max(4, (item.durationMs / maxMs) * 100);
        return (
          <div key={item.dateKey} className="stats-trend-bar-wrap">
            <div
              className="stats-trend-bar"
              style={{ height: `${height}%` }}
              title={`${item.label} · ${formatFullDuration(item.durationMs)} · ${item.sessionCount} 次`}
            />
            <small>{item.label}</small>
          </div>
        );
      })}
    </div>
  );
}

function StatusBar({
  label,
  count,
  total,
  variant
}: {
  label: string;
  count: number;
  total: number;
  variant: "reading" | "completed" | "unread" | "unreadable";
}) {
  const percent = total > 0 ? Math.round((count / total) * 100) : 0;
  return (
    <div className="stats-status-row">
      <span className="stats-status-dot" data-variant={variant} aria-hidden="true" />
      <span className="stats-status-label">{label}</span>
      <div className="stats-status-track">
        <div className="stats-status-fill" data-variant={variant} style={{ width: `${percent}%` }} />
      </div>
      <span className="stats-status-count">{count}</span>
    </div>
  );
}

export function StatsPage({ snapshot, onGoToShelf }: { snapshot: MobileSnapshot; onGoToShelf?: () => void }) {
  const [statsPeriod, setStatsPeriod] = useState<Exclude<StatsPeriod, "day">>("week");
  const [statsAnchor, setStatsAnchor] = useState(() => new Date());

  const periodStats = useMemo(
    () => buildMobileStatsSummary(snapshot, statsPeriod, statsAnchor),
    [snapshot, statsPeriod, statsAnchor]
  );
  const trend = useMemo(
    () => buildReadingTrend(snapshot, statsPeriod, statsAnchor),
    [snapshot, statsPeriod, statsAnchor]
  );
  const statusCount = useMemo(() => buildBookStatusCount(snapshot), [snapshot]);
  const streak = useMemo(() => buildReadingStreak(snapshot), [snapshot]);
  const currentPeriod = isCurrentStatsPeriod(statsPeriod, statsAnchor);

  const changeStatsPeriod = (period: Exclude<StatsPeriod, "day">) => {
    setStatsPeriod(period);
    setStatsAnchor(new Date());
  };

  const shiftStatsPeriod = (direction: -1 | 1) => {
    if (statsPeriod === "total") return;
    setStatsAnchor((current) => shiftStatsPeriodAnchor(current, statsPeriod, direction));
  };

  const hasAnyData = periodStats.totalReadingMs > 0 || periodStats.sessionCount > 0;
  const showGlobalEmpty = snapshot.books.length === 0 && !hasAnyData;

  return (
    <div className="screen-stack stats-screen">
      <header className="mobile-header row-header stats-header">
        <h1>统计</h1>
      </header>

      <div className="stats-period-tabs range-tabs">
        {(Object.keys(statsPeriodLabels) as Exclude<StatsPeriod, "day">[]).map((item) => (
          <button
            key={item}
            className={statsPeriod === item ? "active" : ""}
            onClick={() => changeStatsPeriod(item)}
            aria-pressed={statsPeriod === item}
          >
            {statsPeriodLabels[item]}
          </button>
        ))}
      </div>

      <div className="stats-period-title">
        <button
          className="round-action"
          aria-label="上一周期"
          disabled={statsPeriod === "total"}
          onClick={() => shiftStatsPeriod(-1)}
        >
          ‹
        </button>
        <strong>{getStatsPeriodTitle(statsPeriod, statsAnchor)}</strong>
        <button
          className="round-action"
          aria-label="下一周期"
          disabled={statsPeriod === "total" || currentPeriod}
          onClick={() => shiftStatsPeriod(1)}
        >
          ›
        </button>
      </div>

      {showGlobalEmpty ? (
        <section className="stats-empty-state">
          <BookOpen size={40} strokeWidth={1.5} />
          <strong>还没有阅读记录</strong>
          <p>开始阅读后，这里会展示你的阅读时长、书籍和天数统计。</p>
          {onGoToShelf && (
            <button className="stats-empty-action" onClick={onGoToShelf}>
              前往书架
            </button>
          )}
        </section>
      ) : (
        <>
          <section className="stats-summary-grid" aria-label="阅读概要">
            <SummaryCard
              icon={Clock}
              value={formatCompactDuration(periodStats.totalReadingMs)}
              label="阅读时长"
            />
            <SummaryCard
              icon={Calendar}
              value={`${periodStats.readingDays} 天`}
              label="阅读天数"
            />
            <SummaryCard
              icon={Book}
              value={`${periodStats.readBooks} 本`}
              label="读过书籍"
            />
            <SummaryCard
              icon={CheckCircle}
              value={`${periodStats.completed} 本`}
              label="已读完"
            />
          </section>

          <section className="stats-streak-card">
            <div className={`stats-streak-item ${streak.current > 0 ? "active" : ""}`}>
              <BarChart3 size={18} />
              <div>
                <strong>{streak.current}</strong>
                <span>当前连续（天）</span>
              </div>
            </div>
            <div className="stats-streak-divider" />
            <div className="stats-streak-item">
              <Calendar size={18} />
              <div>
                <strong>{streak.longest}</strong>
                <span>最长连续（天）</span>
              </div>
            </div>
          </section>

          <section className="stats-card" aria-label="阅读趋势">
            <div className="stats-card-header">
              <h2>阅读趋势</h2>
              <span>{periodStats.sessionCount ? `${periodStats.sessionCount} 次阅读` : "暂无数据"}</span>
            </div>
            <TrendChart items={trend} />
          </section>

          <section className="stats-card" aria-label="书籍状态">
            <div className="stats-card-header">
              <h2>书籍状态</h2>
              <span>共 {statusCount.total} 本</span>
            </div>
            <div className="stats-status-list">
              <StatusBar label="在读" count={statusCount.reading} total={statusCount.total} variant="reading" />
              <StatusBar label="已读完" count={statusCount.completed} total={statusCount.total} variant="completed" />
              <StatusBar label="未开始" count={statusCount.unread} total={statusCount.total} variant="unread" />
              {statusCount.unreadable > 0 && (
                <StatusBar label="不可读" count={statusCount.unreadable} total={statusCount.total} variant="unreadable" />
              )}
            </div>
            {statusCount.unreadable > 0 && (
              <p className="stats-status-hint">
                <AlertCircle size={14} />
                不可读包括同步占位、导入失败或文件缺失的书籍。
              </p>
            )}
          </section>

          <section className="stats-card" aria-label="阅读与创作">
            <div className="stats-card-header">
              <h2>阅读与创作</h2>
            </div>
            <div className="stats-creation-grid">
              <article>
                <Type size={16} />
                <strong>{periodStats.words.toLocaleString()}</strong>
                <span>阅读字数</span>
              </article>
              <article>
                <Gauge size={16} />
                <strong>{periodStats.speed}</strong>
                <span>字/分钟</span>
              </article>
              <article>
                <MessageSquare size={16} />
                <strong>{periodStats.noteCount}</strong>
                <span>笔记</span>
              </article>
              <article>
                <Sparkles size={16} />
                <strong>{periodStats.inspirationCount}</strong>
                <span>灵感</span>
              </article>
            </div>
          </section>

          {!hasAnyData && (
            <section className="stats-period-empty">
              <Library size={32} strokeWidth={1.5} />
              <strong>本期还没有阅读记录</strong>
              <p>切换其他时间范围，或开始阅读以生成统计。</p>
            </section>
          )}
        </>
      )}
    </div>
  );
}
