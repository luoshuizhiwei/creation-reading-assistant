import { useCallback, useEffect, useState } from "react";
import { Clock3, Flame, Hash, History, Library, Trash2 } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { ProjectStatsView, SessionEntry } from "@/types/creation";

interface StatsPageProps {
  projectId: string;
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest > 0 ? `${hours} 小时 ${rest} 分钟` : `${hours} 小时`;
}

export function StatsPage({ projectId }: StatsPageProps) {
  const { loadStats, loadSessions, deleteSession } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);
  const [stats, setStats] = useState<ProjectStatsView | null>(null);
  const [sessions, setSessions] = useState<SessionEntry[]>([]);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setStats(await loadStats(projectId));
    setSessions(await loadSessions({ projectId, limit: 100 }));
  }, [loadSessions, loadStats, projectId]);

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

  if (!stats) {
    return <section className="creation-writing-loading" role="status"><History size={24} /><span>正在读取统计…</span></section>;
  }

  const maxDaily = Math.max(...stats.daily.map((item) => Math.abs(item.netChars)), 1);
  const { words, sessionMinutes, revisionCount, snapshotCount, streakDays, chapterStatusCounts, daily } = stats;

  return (
    <section className="stats-page" aria-label="创作统计">
      <div className="stats-grid">
        <div className="stats-card">
          <h3><Hash size={15} /> 字数（当前全稿）</h3>
          <dl className="stats-words">
            <div><dt>汉字</dt><dd>{words.han.toLocaleString("zh-CN")}</dd></div>
            <div><dt>含标点</dt><dd>{words.withPunctuation.toLocaleString("zh-CN")}</dd></div>
            <div><dt>非空白字符</dt><dd>{words.nonWhitespace.toLocaleString("zh-CN")}</dd></div>
          </dl>
        </div>
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
          <h3><Library size={15} /> 进度</h3>
          <dl className="stats-words">
            <div><dt>正文修订</dt><dd>{revisionCount.toLocaleString("zh-CN")} 次</dd></div>
            <div><dt>命名快照</dt><dd>{snapshotCount.toLocaleString("zh-CN")} 个</dd></div>
            <div><dt>章节状态</dt><dd>{chapterStatusCounts.map((item) => `${item.status || "未设置"} ${item.count}`).join("，") || "—"}</dd></div>
          </dl>
        </div>
      </div>

      <div className="stats-card stats-daily-card">
        <h3>最近 14 天净增字数与写作时长</h3>
        <div className="stats-daily" role="img" aria-label="最近十四天净增字数柱状图">
          {daily.map((item) => {
            const height = Math.max(2, Math.round((Math.abs(item.netChars) / maxDaily) * 100));
            return (
              <div key={item.date} className="stats-daily-col" title={`${item.date}：净增 ${item.netChars} 字 · ${Math.round(item.activeSeconds / 60)} 分钟`}>
                <span className="stats-daily-bar" style={{ height: `${height}%` }} />
                <span className="stats-daily-label">{item.date.slice(5)}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="stats-card stats-sessions-card">
        <h3>写作会话（可修正）</h3>
        {sessions.length === 0 ? (
          <p className="stats-note">在写作台输入内容时才会计时；空闲超过 5 分钟自动暂停，不记录具体按键内容。</p>
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
    </section>
  );
}
