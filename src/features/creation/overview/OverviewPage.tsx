import { useCallback, useEffect, useState } from "react";
import { BookOpen, Clock3, Flame, PenLine, Target } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import type { ProjectStatsView } from "@/types/creation";

interface OverviewPageProps {
  projectId: string;
  onContinueWriting(): void;
  onOpenOutline(): void;
  onOpenStats(): void;
  onOpenInbox(): void;
}

export function OverviewPage({ projectId, onContinueWriting, onOpenOutline, onOpenStats, onOpenInbox }: OverviewPageProps) {
  const { loadStats } = useCreationActions();
  const projects = useCreationStore((state) => state.projects);
  const navigations = useCreationStore((state) => state.navigations);
  const [stats, setStats] = useState<ProjectStatsView | null>(null);
  const [inboxCount, setInboxCount] = useState(0);
  const { loadInbox } = useCreationActions();

  const refresh = useCallback(async () => {
    setStats(await loadStats(projectId));
    setInboxCount((await loadInbox({ limit: 1 })).length);
  }, [loadInbox, loadStats, projectId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const project = projects.find((item) => item.id === projectId);
  const navigation = navigations[projectId];
  const sceneCount = navigation?.chapters.reduce((sum, chapter) => sum + chapter.scenes.length, 0) ?? 0;
  const targetWords = project?.setup?.totalWordGoal ?? 0;
  const progress = stats && targetWords > 0 ? Math.min(100, Math.round((stats.words.nonWhitespace / targetWords) * 100)) : 0;
  const targetDate = project?.setup?.targetDate;

  return (
    <section className="stats-page overview-page" aria-label="项目概览">
      <div className="stats-grid">
        <div className="stats-card">
          <h3><BookOpen size={15} /> 项目</h3>
          <p className="overview-project-title">{project?.title ?? "—"}</p>
          <p className="stats-note">
            {navigation?.chapters.length ?? 0} 章 · {sceneCount} 场景
            {project?.setup?.description ? ` · ${project.setup.description}` : ""}
          </p>
        </div>
        <div className="stats-card">
          <h3><Target size={15} /> 字数目标</h3>
          {targetWords > 0 && stats ? (
            <>
              <p className="stats-streak">{progress}%</p>
              <p className="stats-note">
                已写 {stats.words.nonWhitespace.toLocaleString("zh-CN")} / {targetWords.toLocaleString("zh-CN")} 字
              </p>
            </>
          ) : (
            <p className="stats-note">未设置字数目标。</p>
          )}
          {targetDate && <p className="stats-note">目标日期：{String(targetDate).slice(0, 10)}</p>}
        </div>
        <div className="stats-card">
          <h3><Clock3 size={15} /> 今日写作</h3>
          <p className="stats-streak">{stats?.sessionMinutes.today ?? 0} 分钟</p>
          <p className="stats-note">
            连续写作 {stats?.streakDays ?? 0} 天 · 本周 {stats?.sessionMinutes.week ?? 0} 分钟
          </p>
        </div>
        <div className="stats-card">
          <h3><Flame size={15} /> 待处理</h3>
          <p className="stats-streak">{inboxCount}</p>
          <p className="stats-note">收件箱未处理条目</p>
        </div>
      </div>

      <div className="stats-card">
        <h3>继续写作</h3>
        <div className="overview-actions">
          <button type="button" className="overview-action" onClick={onContinueWriting}>
            <PenLine size={15} /> 继续写作
          </button>
          <button type="button" className="overview-action" onClick={onOpenOutline}>
            打开大纲
          </button>
          <button type="button" className="overview-action" onClick={onOpenStats}>
            查看统计
          </button>
          <button type="button" className="overview-action" onClick={onOpenInbox}>
            收件箱
          </button>
        </div>
      </div>
    </section>
  );
}
