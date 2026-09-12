import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarClock, Clock3, Edit3, Flame, Layers, Library, PencilLine, Target, Trash2 } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { CreationProjectSetup, ProjectHomeEntry, ProjectStatsView, ProjectUpdateGoalCommand, SessionEntry } from "@/types/creation";
import { SCENE_STATUS_LABELS, SCENE_STATUS_ORDER, sceneStatusLabel } from "@/features/creation/scene-status";
import { GoalEditorDialog, type GoalUpdatePatch } from "./GoalEditorDialog";
import { SessionEditDialog, type SessionUpdatePatch } from "./SessionEditDialog";
import {
  computeDailyNetChars,
  computeGoalDeadline,
  computeGoalProgress,
  computeWeekSummary,
  DEFAULT_WORD_METRIC,
  shouldShowDailyLabel,
  WORD_METRIC_LABELS,
  type WordMetric
} from "./stats-calculator";

interface StatsPageProps {
  projectId: string;
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest > 0 ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}

/**
 * 目标进度卡：按项目主指标（seam 接入前默认非空白字符）展示总字数目标进度、
 * 今日/本周净增、目标日期倒计时与每周更新日平均。指标可本地预览切换（不写项目）。
 */
function GoalProgressCard({
  setup,
  stats,
  projectId,
  onUpdateGoal
}: {
  setup: CreationProjectSetup | null;
  stats: ProjectStatsView;
  projectId: string;
  onUpdateGoal: (command: Omit<ProjectUpdateGoalCommand, "type">) => Promise<boolean>;
}) {
  const [metric, setMetric] = useState<WordMetric>(DEFAULT_WORD_METRIC);
  const progress = useMemo(() => computeGoalProgress(stats, setup ?? { template: "blank", weeklyUpdateDays: [], chapterWorkflow: [] }, metric), [metric, setup, stats]);
  const week = useMemo(() => computeWeekSummary(stats), [stats]);
  const deadline = useMemo(() => computeGoalDeadline(stats, setup ?? { template: "blank", weeklyUpdateDays: [], chapterWorkflow: [] }, metric), [metric, setup, stats]);
  const [goalOpen, setGoalOpen] = useState(false);
  const [goalBusy, setGoalBusy] = useState(false);
  const [goalError, setGoalError] = useState<string | null>(null);

  const handleSaveGoal = useCallback(
    async (patch: GoalUpdatePatch): Promise<boolean> => {
      setGoalBusy(true);
      setGoalError(null);
      try {
        const ok = await onUpdateGoal({
          projectId,
          totalWordGoal: patch.totalWordGoal ?? null,
          dailyWordGoal: patch.dailyWordGoal ?? null,
          weeklyWordGoal: patch.weeklyWordGoal ?? null,
          targetDate: patch.targetDate ?? null,
          weeklyUpdateDays: patch.weeklyUpdateDays
        });
        if (!ok) setGoalError("目标已被其他会话更新，请刷新后重试。");
        return ok;
      } catch (error) {
        setGoalError(error instanceof Error ? error.message : String(error));
        return false;
      } finally {
        setGoalBusy(false);
      }
    },
    [onUpdateGoal, projectId]
  );

  return (
    <div className="stats-card stats-goal-card">
      <h3>
        <Target size={15} /> 目标进度
        {setup ? (
          <button
            type="button"
            className="stats-goal-edit"
            onClick={() => {
              setGoalError(null);
              setGoalOpen(true);
            }}
          >
            <Edit3 size={13} /> 编辑目标
          </button>
        ) : null}
      </h3>
      <div className="stats-metric-switch" role="group" aria-label="字数指标预览">
        {(Object.keys(WORD_METRIC_LABELS) as WordMetric[]).map((item) => (
          <button
            key={item}
            type="button"
            className={metric === item ? "active" : ""}
            aria-pressed={metric === item}
            title="指标预览（保存为项目主指标需目标更新接口）"
            onClick={() => setMetric(item)}
          >
            {WORD_METRIC_LABELS[item]}
          </button>
        ))}
      </div>

      {progress ? (
        <div className="stats-goal-progress">
          <div className="stats-goal-bar" role="img" aria-label={`总字数目标进度 ${progress.percent}%`}>
            <div className="stats-goal-bar-fill" style={{ width: `${progress.percent}%` }} />
          </div>
          <p className="stats-goal-numbers">
            {progress.current.toLocaleString("zh-CN")} / {progress.target.toLocaleString("zh-CN")} {WORD_METRIC_LABELS[metric]}（{progress.percent}%）
          </p>
          <p className="stats-note">
            {progress.reached
              ? "已达到总字数目标。"
              : `距离目标还差 ${progress.remaining.toLocaleString("zh-CN")} ${WORD_METRIC_LABELS[metric]}。`}
          </p>
        </div>
      ) : (
        <p className="stats-note">尚未设置总字数目标。</p>
      )}

      <dl className="stats-words">
        <div><dt>今日净增</dt><dd>{computeDailyNetChars(stats).toLocaleString("zh-CN")} 字</dd></div>
        <div><dt>本周净增</dt><dd>{week.netChars.toLocaleString("zh-CN")} 字</dd></div>
        <div><dt>本周时长</dt><dd>{formatMinutes(Math.round(week.activeSeconds / 60))}</dd></div>
      </dl>

      {deadline ? (
        <p className="stats-note">
          <CalendarClock size={13} /> 距目标日期还有 {deadline.daysLeft} 天，日均需 {deadline.perDayNeeded.toLocaleString("zh-CN")} 字。
        </p>
      ) : null}
      {setup?.weeklyWordGoal !== undefined && setup.weeklyWordGoal > 0 && setup.weeklyUpdateDays.length > 0 ? (
        <p className="stats-note">
          每周目标 {setup.weeklyWordGoal.toLocaleString("zh-CN")} 字，更新日 {setup.weeklyUpdateDays.length} 天，平均每日{" "}
          {Math.ceil(setup.weeklyWordGoal / setup.weeklyUpdateDays.length).toLocaleString("zh-CN")} 字。
        </p>
      ) : null}
      {setup && goalOpen ? (
        <GoalEditorDialog
          open={goalOpen}
          initial={setup}
          onClose={() => setGoalOpen(false)}
          onSave={handleSaveGoal}
          busy={goalBusy}
          error={goalError}
          metricLocked
        />
      ) : null}
    </div>
  );
}

export function StatsPage({ projectId }: StatsPageProps) {
  const { loadProjectHome, loadStats, loadSessions, deleteSession, updateProjectGoal, updateSession } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [stats, setStats] = useState<ProjectStatsView | null>(null);
  const [sessions, setSessions] = useState<SessionEntry[]>([]);
  const [setup, setSetup] = useState<CreationProjectSetup | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [editingSession, setEditingSession] = useState<SessionEntry | null>(null);
  const [sessionBusy, setSessionBusy] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);

    const refresh = useCallback(async () => {
    setStats(await loadStats(projectId));
    setSessions(await loadSessions({ projectId, limit: 100 }));
    const home = await loadProjectHome();
    const entry: ProjectHomeEntry | undefined = home.projects.find((item) => item.id === projectId);
    setSetup(entry?.setup ?? null);
  }, [loadProjectHome, loadSessions, loadStats, projectId]);

  const handleUpdateGoal = useCallback(
    async (command: Omit<ProjectUpdateGoalCommand, "type">): Promise<boolean> => {
      const result = await updateProjectGoal(command);
      return result !== null;
    },
    [updateProjectGoal]
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleDeleteSession = async (sessionId: string) => {
    const ok = await deleteSession({ projectId, sessionId });
    if (ok) {
      showToast({ tone: "success", title: "已删除该写作会话", body: "统计会按剩余会话重新计算。" });
      await refresh();
    }
    setConfirmingId(null);
  };

  const handleSaveSession = async (patch: SessionUpdatePatch): Promise<boolean> => {
    if (!editingSession) return false;
    setSessionBusy(true);
    setSessionError(null);
    try {
      const ok = await updateSession({ projectId, sessionId: editingSession.id, ...patch });
      if (ok) {
        setEditingSession(null);
        await refresh();
      } else {
        setSessionError("会话已被其他会话更新，请刷新后重试。");
      }
      return ok;
    } catch (error) {
      setSessionError(error instanceof Error ? error.message : String(error));
      return false;
    } finally {
      setSessionBusy(false);
    }
  };

  if (!stats) {
    return <section className="creation-writing-loading" role="status"><Library size={24} /><span>正在读取统计…</span></section>;
  }

  const maxDaily = Math.max(...stats.daily.map((item) => Math.abs(item.netChars)), 1);
  const { sessionMinutes, revisionCount, snapshotCount, streakDays, chapterStatusCounts, sceneStatusCounts, daily } = stats;

  // 场景状态分布：按固定顺序铺满四个状态（计数为 0 也保留，避免同一项目在不同时间点
  // 出现行数跳变）；未知状态值追加在末尾并原样展示，使数据异常可见。
  const sceneStatusRows = SCENE_STATUS_ORDER.map((value) => ({
    key: value as string,
    label: SCENE_STATUS_LABELS[value],
    count: sceneStatusCounts.find((item) => item.status === value)?.count ?? 0
  }));
  for (const item of sceneStatusCounts) {
    if (!(SCENE_STATUS_ORDER as string[]).includes(item.status)) {
      sceneStatusRows.push({ key: item.status, label: sceneStatusLabel(item.status), count: item.count });
    }
  }
  const sceneStatusTotal = sceneStatusCounts.reduce((sum, item) => sum + item.count, 0);

  return (
    <section className="stats-page" aria-label="创作统计">
      <div className="stats-grid">
        <GoalProgressCard setup={setup} stats={stats} projectId={projectId} onUpdateGoal={handleUpdateGoal} />
        <div className="stats-card">
          <h3><Clock3 size={15} /> 写作时长</h3>
          <dl className="stats-words">
            <div><dt>今日</dt><dd>{formatMinutes(sessionMinutes.today)}</dd></div>
            <div><dt>本周</dt><dd>{formatMinutes(sessionMinutes.week)}</dd></div>
            <div><dt>累计</dt><dd>{formatMinutes(sessionMinutes.total)}</dd></div>
          </dl>
        </div>
        <div className="stats-card">
          <h3><Flame size={15} /> 连续写作</h3>
          <p className="stats-streak">{streakDays} 天</p>
          <p className="stats-note">按有写作会话的连续天数计算（含今天）。</p>
        </div>
        <div className="stats-card">
          <h3><Library size={15} /> 修订与结构</h3>
          <dl className="stats-words">
            <div><dt>正文修订</dt><dd>{revisionCount.toLocaleString("zh-CN")} 次</dd></div>
            <div><dt>命名快照</dt><dd>{snapshotCount.toLocaleString("zh-CN")} 个</dd></div>
            <div><dt>章节状态</dt><dd>{chapterStatusCounts.map((item) => `${item.status || "未设置"} ${item.count}`).join("，") || "—"}</dd></div>
          </dl>
          <p className="stats-note">章节状态是章节在项目工作流中的位置，与下面的场景状态是两种口径。</p>
        </div>
        <div className="stats-card stats-scene-status-card">
          <h3><Layers size={15} /> 场景状态</h3>
          {sceneStatusTotal > 0 ? (
            <dl className="stats-words">
              {sceneStatusRows.map((row) => (
                <div key={row.key}>
                  <dt>{row.label}</dt>
                  <dd>{row.count.toLocaleString("zh-CN")} 场</dd>
                </div>
              ))}
              <div><dt>合计</dt><dd>{sceneStatusTotal.toLocaleString("zh-CN")} 场</dd></div>
            </dl>
          ) : (
            <p className="stats-note">还没有场景。建立大纲并填写场景任务卡后，这里会显示场景状态分布。</p>
          )}
          <p className="stats-note">场景状态取自场景任务卡，独立于章节工作流状态。</p>
        </div>
      </div>

      <div className="stats-card stats-daily-card">
        <h3>最近 30 天净增字数与写作时长</h3>
        {daily.every((item) => item.netChars === 0) ? (
          <p className="stats-note">最近 30 天还没有净增字数。在写作台输入或调整结构后，这里会按天记录变化。</p>
        ) : (
          <div className="stats-daily" role="img" aria-label="最近三十天净增字数柱状图">
            {daily.map((item, index) => {
              const height = Math.max(2, Math.round((Math.abs(item.netChars) / maxDaily) * 100));
              return (
                <div key={item.date} className="stats-daily-col" title={`${item.date}：净增 ${item.netChars} 字 · ${Math.round(item.activeSeconds / 60)} 分钟`}>
                  <span className="stats-daily-bar" style={{ height: `${height}%` }} />
                  <span className="stats-daily-label">{shouldShowDailyLabel(index, daily.length) ? item.date.slice(5) : ""}</span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <div className="stats-card stats-sessions-card">
        <h3><PencilLine size={15} /> 写作会话（可修正）</h3>
        {sessions.length === 0 ? (
          <p className="stats-note">在写作台输入、选择或结构操作时才会计时；空闲超过 5 分钟自动暂停，不记录具体按键内容。</p>
        ) : (
          <ul className="stats-sessions">
            {sessions.map((session) => (
              <li key={session.id} className="stats-session">
                <span className="stats-session-when">{new Date(session.startedAt).toLocaleString("zh-CN")}</span>
                <span className="stats-session-main">
                  {formatMinutes(Math.round(session.activeSeconds / 60))}
                  <em className={session.netChars < 0 ? "negative" : ""}>
                    {session.netChars >= 0 ? "+" : ""}{session.netChars.toLocaleString("zh-CN")} 字
                  </em>
                </span>
                <span className="history-item-actions">
                  <button
                    type="button"
                    className="stats-session-edit"
                    onClick={() => {
                      setSessionError(null);
                      setEditingSession(session);
                    }}
                  >
                    <Edit3 size={13} />
                    修正
                  </button>
                  <button
                    type="button"
                    className={confirmingId === session.id ? "confirming" : ""}
                    onClick={() => {
                      if (confirmingId === session.id) void handleDeleteSession(session.id);
                      else setConfirmingId(session.id);
                    }}
                  >
                    <Trash2 size={13} />
                    {confirmingId === session.id ? "确认删除" : "删除"}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {editingSession ? (
        <SessionEditDialog
          open={editingSession !== null}
          session={editingSession}
          onClose={() => setEditingSession(null)}
          onSave={handleSaveSession}
          busy={sessionBusy}
          error={sessionError}
        />
      ) : null}
    </section>
  );
}
