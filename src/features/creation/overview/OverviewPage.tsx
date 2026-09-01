import { useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, CalendarClock, Clock3, FileText, Flame, Inbox, Layers, PenLine, Target } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import type { CreationProjectSetup, InboxCountView, ProjectStatsView } from "@/types/creation";
import {
  computeDailyNetChars,
  computeGoalDeadline,
  computeGoalProgress,
  computeWeekSummary,
  DEFAULT_WORD_METRIC,
  WORD_METRIC_LABELS
} from "@/features/creation/stats/stats-calculator";
import "./overview-local.css";

interface OverviewPageProps {
  projectId: string;
  onContinueWriting(): void;
  onOpenOutline(): void;
  onOpenStats(): void;
  onOpenInbox(): void;
}

interface RecentScene {
  sceneId: string;
  sceneTitle: string;
  chapterTitle: string;
  updatedAt: string;
}

function relativeTime(iso: string): string {
  const delta = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(delta) || delta < 0) return "—";
  const minutes = Math.round(delta / 60_000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days} 天前`;
  return new Date(iso).toLocaleDateString("zh-CN");
}

export function OverviewPage({ projectId, onContinueWriting, onOpenOutline, onOpenStats, onOpenInbox }: OverviewPageProps) {
  const { loadStats, loadInboxCount } = useCreationActions();
  const selectScene = useCreationStore((state) => state.selectScene);
  const projects = useCreationStore((state) => state.projects);
  const navigations = useCreationStore((state) => state.navigations);
  const [stats, setStats] = useState<ProjectStatsView | null>(null);
  const [inbox, setInbox] = useState<InboxCountView>({ total: 0, pending: 0 });

  const refresh = useCallback(async () => {
    setStats(await loadStats(projectId));
    setInbox(await loadInboxCount());
  }, [loadInboxCount, loadStats, projectId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const project = projects.find((item) => item.id === projectId);
  const setup: CreationProjectSetup | undefined = project?.setup;
  const navigation = navigations[projectId];
  const chapters = navigation?.chapters ?? [];
  const sceneCount = chapters.reduce((sum, chapter) => sum + chapter.scenes.length, 0);

  const progress = useMemo(
    () => (stats && setup ? computeGoalProgress(stats, setup, DEFAULT_WORD_METRIC) : null),
    [stats, setup]
  );
  const deadline = useMemo(
    () => (stats && setup ? computeGoalDeadline(stats, setup, DEFAULT_WORD_METRIC) : null),
    [stats, setup]
  );
  const dailyNetChars = useMemo(() => (stats ? computeDailyNetChars(stats) : 0), [stats]);
  const week = useMemo(() => (stats ? computeWeekSummary(stats) : { netChars: 0, activeSeconds: 0 }), [stats]);

  const recentScenes = useMemo<RecentScene[]>(() => {
    const list: RecentScene[] = [];
    for (const chapter of chapters) {
      for (const scene of chapter.scenes) {
        list.push({ sceneId: scene.id, sceneTitle: scene.title, chapterTitle: chapter.title, updatedAt: scene.updatedAt });
      }
    }
    list.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
    return list.slice(0, 6);
  }, [chapters]);

  const maxDaily = stats ? Math.max(...stats.daily.map((item) => Math.abs(item.netChars)), 1) : 1;
  const statusTotal = stats ? stats.chapterStatusCounts.reduce((sum, item) => sum + item.count, 0) : 0;

  const handleJumpScene = (sceneId: string) => {
    selectScene(sceneId);
    onContinueWriting();
  };

  return (
    <section className="overview-page" aria-label="项目概览">
      <section className="desktop-page-hero motion-panel overview-hero">
        <div className="overview-hero-title">
          <h2>项目概览</h2>
          <p>
            {chapters.length} 章 · {sceneCount} 场景
            {setup?.description ? ` · ${setup.description}` : ""}
          </p>
        </div>
        <div className="overview-hero-badges">
          <span className="overview-chip"><Clock3 size={13} /> 今日 {stats?.sessionMinutes.today ?? 0} 分钟</span>
          <span className="overview-chip"><Flame size={13} /> 连续 {stats?.streakDays ?? 0} 天</span>
          <span className="overview-chip"><PenLine size={13} /> 本周净增 {week.netChars.toLocaleString("zh-CN")} 字</span>
        </div>
      </section>

      <div className="stats-grid overview-grid">
        <div className="stats-card overview-goal-card">
          <h3><Target size={15} /> 字数目标</h3>
          {progress ? (
            <>
              <div className="overview-goal-bar" role="img" aria-label={`总字数目标进度 ${progress.percent}%`}>
                <div className="overview-goal-bar-fill" style={{ width: `${progress.percent}%` }} />
              </div>
              <p className="overview-goal-numbers">
                {progress.current.toLocaleString("zh-CN")} / {progress.target.toLocaleString("zh-CN")} {WORD_METRIC_LABELS[progress.metric]}
                <em>（{progress.percent}%）</em>
              </p>
              <p className="stats-note">
                {progress.reached
                  ? "已达到总字数目标。"
                  : `距离目标还差 ${progress.remaining.toLocaleString("zh-CN")} 字。`}
              </p>
              {deadline ? (
                <p className="stats-note">
                  <CalendarClock size={13} /> 距目标日期 {deadline.daysLeft} 天，日均需 {deadline.perDayNeeded.toLocaleString("zh-CN")} 字。
                </p>
              ) : null}
            </>
          ) : (
            <p className="stats-note">尚未设置总字数目标。</p>
          )}
        </div>

        <div className="stats-card">
          <h3><Clock3 size={15} /> 写作时长</h3>
          <dl className="stats-words">
            <div><dt>今日</dt><dd>{stats?.sessionMinutes.today ?? 0} 分钟</dd></div>
            <div><dt>本周</dt><dd>{Math.round(week.activeSeconds / 60)} 分钟</dd></div>
            <div><dt>累计</dt><dd>{stats?.sessionMinutes.total ?? 0} 分钟</dd></div>
            <div><dt>今日净增</dt><dd>{dailyNetChars.toLocaleString("zh-CN")} 字</dd></div>
          </dl>
        </div>

        <div className="stats-card">
          <h3><Layers size={15} /> 章节状态</h3>
          {stats && statusTotal > 0 ? (
            stats.chapterStatusCounts.length === 1 && !stats.chapterStatusCounts[0].status ? (
              <div className="overview-status-empty">全部 {statusTotal} 章尚未设置状态。在大纲页或章节列表中标记章节状态后，这里会显示分布。</div>
            ) : (
            <ul className="overview-status-list">
              {stats.chapterStatusCounts.map((item) => {
                const ratio = Math.round((item.count / statusTotal) * 100);
                return (
                  <li key={item.status || "未设置"} title={`${item.status || "未设置"}：${item.count} 章`}>
                    <span className="overview-status-name">{item.status || "未设置"}</span>
                    <span className="overview-status-bar" role="img" aria-label={`${item.status || "未设置"} 占比 ${ratio}%`}>
                      <span className={`overview-status-bar-fill ${item.status ? "" : "overview-status-bar-fill--unset"}`} style={{ width: `${ratio}%` }} />
                    </span>
                    <span className="overview-status-count">{item.count}</span>
                  </li>
                );
              })}
            </ul>
            )
          ) : (
            <p className="stats-note">暂无章节状态数据。</p>
          )}
        </div>

        <div className="stats-card overview-pending-card">
          <h3><Inbox size={15} /> 待处理</h3>
          <p className="stats-streak">{inbox.pending}</p>
          <p className="stats-note">收件箱中 {inbox.pending.toLocaleString("zh-CN")} 条未整理（共 {inbox.total.toLocaleString("zh-CN")}）</p>
          <button type="button" className="overview-action" onClick={onOpenInbox}>打开收件箱</button>
        </div>
      </div>

      <div className="stats-card overview-daily-card">
        <h3><Flame size={15} /> 最近 14 天净增字数</h3>
        {stats && stats.daily.some((item) => item.netChars !== 0) ? (
          <div className="stats-daily overview-daily" role="img" aria-label="最近十四天净增字数柱状图">
            {stats.daily.map((item) => {
              const height = Math.max(2, Math.round((Math.abs(item.netChars) / maxDaily) * 100));
              const netClass = item.netChars >= 0 ? "gain" : "loss";
              return (
                <div
                  key={item.date}
                  className={`stats-daily-col overview-daily-col ${netClass}`}
                  title={`${item.date}：净增 ${item.netChars} 字 · ${Math.round(item.activeSeconds / 60)} 分钟`}
                >
                  <span className="stats-daily-bar overview-daily-bar" style={{ height: `${height}%` }} />
                  <span className="stats-daily-label overview-daily-label">{item.date.slice(5)}</span>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="stats-note">最近 14 天还没有净增字数。在写作台输入或调整结构后，这里会按天记录变化。</p>
        )}
      </div>

      <div className="stats-card overview-recent-card">
        <h3>
          <FileText size={15} /> 最近编辑
          <button type="button" className="overview-recent-all" onClick={onContinueWriting}>继续写作</button>
        </h3>
        {recentScenes.length === 0 ? (
          <p className="stats-note">还没有场景。先在大纲里创建章节与场景。</p>
        ) : (
          <ul className="overview-recent-list">
            {recentScenes.map((scene) => (
              <li
                key={scene.sceneId}
                className="overview-recent-item"
                tabIndex={0}
                role="button"
                onClick={() => handleJumpScene(scene.sceneId)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    handleJumpScene(scene.sceneId);
                  }
                }}
                title={`跳到场景「${scene.sceneTitle}」并进入写作`}
              >
                <span className="overview-recent-scene">{scene.sceneTitle}</span>
                <span className="overview-recent-chapter">{scene.chapterTitle}</span>
                <span className="overview-recent-when">{relativeTime(scene.updatedAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="overview-actions-row">
        <button type="button" className="overview-action" onClick={onContinueWriting}>
          <PenLine size={15} /> 继续写作
        </button>
        <button type="button" className="overview-action" onClick={onOpenOutline}>
          <BookOpen size={15} /> 大纲
        </button>
        <button type="button" className="overview-action" onClick={onOpenStats}>
          <Target size={15} /> 统计
        </button>
        <button type="button" className="overview-action" onClick={onOpenInbox}>
          <Inbox size={15} /> 收件箱
        </button>
      </div>
    </section>
  );
}