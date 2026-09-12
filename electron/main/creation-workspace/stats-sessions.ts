/**
 * 创作统计与会话域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：写作会话上报/删除/列表 + 项目统计视图。
 * 通过窄接口（Database + 校验回调）与宿主解耦，不依赖 CreationWorkspace 类内部状态。
 */

import type Database from "better-sqlite3";
import { randomUUID } from "node:crypto";
import { CreationWorkspaceError } from "./types";
import type {
  ProjectDailyStat,
  ProjectStatsView,
  SessionDeleteCommand,
  SessionEntry,
  SessionReportCommand,
  SessionReportResult
} from "../../../src/types/creation";

/** 宿主提供的最小校验/副作用回调（避免把 requireProject / touchProject 复制两份）。 */
export interface StatsSessionsHost {
  requireProject(projectId: string): void;
  touchProject(projectId: string, timestamp: string): void;
}

export interface StatsSessionsModule {
  runStatsView(projectId: string): ProjectStatsView | null;
  sessionReport(command: SessionReportCommand): SessionReportResult;
  sessionDelete(command: SessionDeleteCommand): SessionReportResult;
  runSessionList(projectId: string, limit: number): SessionEntry[];
}

/** 统计页每日趋势窗口（天，含今天）。需求为最近 30 天。 */
export const STATS_DAILY_TREND_DAYS = 30;

function validateId(value: unknown, label: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new CreationWorkspaceError("invalid-input", `${label}标识无效。`);
  }
  return value.trim();
}

export function createStatsSessionsModule(database: Database, host: StatsSessionsHost): StatsSessionsModule {
  const sessionReport = (command: SessionReportCommand): SessionReportResult => {
    const projectId = validateId(command.projectId, "作品");
    host.requireProject(projectId);
    const sceneId = typeof command.sceneId === "string" && command.sceneId.trim() ? command.sceneId.trim() : undefined;
    const startedAt = typeof command.startedAt === "string" && !Number.isNaN(Date.parse(command.startedAt))
      ? new Date(command.startedAt).toISOString()
      : new Date().toISOString();
    const activeSeconds = command.activeSeconds;
    if (!Number.isFinite(activeSeconds) || activeSeconds < 0 || activeSeconds > 86_400) {
      throw new CreationWorkspaceError("invalid-input", "活动时长必须在 0 至 86400 秒之间。");
    }
    const netChars = command.netChars;
    if (!Number.isFinite(netChars) || netChars < -1_000_000 || netChars > 1_000_000) {
      throw new CreationWorkspaceError("invalid-input", "净增字符数超出允许范围。");
    }
    const sessionId = `session-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      if (sceneId) {
        database.prepare("SELECT id FROM scenes WHERE id = ? AND deleted_at IS NULL").get(sceneId);
      }
      database
        .prepare(
          "INSERT INTO writing_sessions(id, project_id, scene_id, started_at, active_seconds, net_chars, reported_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(sessionId, projectId, sceneId, startedAt, Math.round(activeSeconds), Math.round(netChars), timestamp);
      host.touchProject(projectId, timestamp);
      database.exec("COMMIT");
      return { commandType: "session.report", sequence: 0, projectId, sessionId, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法写入写作会话。");
    }
  };

  const sessionDelete = (command: SessionDeleteCommand): SessionReportResult => {
    const projectId = validateId(command.projectId, "作品");
    const sessionId = validateId(command.sessionId, "会话");
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const row = database
        .prepare("SELECT id FROM writing_sessions WHERE id = ? AND project_id = ?")
        .get(sessionId, projectId) as { id: string } | undefined;
      if (!row) throw new CreationWorkspaceError("not-found", "会话不存在。");
      database.prepare("DELETE FROM writing_sessions WHERE id = ?").run(sessionId);
      host.touchProject(projectId, timestamp);
      database.exec("COMMIT");
      return { commandType: "session.delete", sequence: 0, projectId, sessionId, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法删除写作会话。");
    }
  };

  const runSessionList = (projectId: string, limit: number): SessionEntry[] => {
    host.requireProject(projectId);
    const rows = database
      .prepare(
        "SELECT id, project_id, scene_id, started_at, active_seconds, net_chars, reported_at FROM writing_sessions WHERE project_id = ? ORDER BY reported_at DESC, id DESC LIMIT ?"
      )
      .all(projectId, limit) as Array<{
      id: string;
      project_id: string;
      scene_id: string | null;
      started_at: string;
      active_seconds: number;
      net_chars: number;
      reported_at: string;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      sceneId: row.scene_id,
      startedAt: row.started_at,
      activeSeconds: row.active_seconds,
      netChars: row.net_chars,
      reportedAt: row.reported_at
    }));
  };

  const runStatsView = (projectId: string): ProjectStatsView | null => {
    host.requireProject(projectId);

    const wordRow = database
      .prepare(
        `SELECT coalesce(sum(s.han_count), 0) AS han,
                coalesce(sum(s.punct_count), 0) AS punct,
                coalesce(sum(s.non_ws_count), 0) AS non_ws
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?`
      )
      .get(projectId) as { han: number; punct: number; non_ws: number };
    const han = Number(wordRow.han);
    const nonWhitespace = Number(wordRow.non_ws);
    const withPunctuation = han + Number(wordRow.punct);

    const sessionRows = database
      .prepare(
        "SELECT started_at, active_seconds, net_chars FROM writing_sessions WHERE project_id = ? ORDER BY started_at"
      )
      .all(projectId) as Array<{ started_at: string; active_seconds: number; net_chars: number }>;

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7));
    const dailyMap = new Map<string, { netChars: number; activeSeconds: number }>();
    const localDateKey = (date: Date): string => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    };
    let todayMinutes = 0;
    let weekMinutes = 0;
    let totalMinutes = 0;
    const daySet = new Set<string>();
    for (const row of sessionRows) {
      const started = new Date(row.started_at);
      const key = localDateKey(started);
      daySet.add(key);
      const entry = dailyMap.get(key) ?? { netChars: 0, activeSeconds: 0 };
      entry.netChars += row.net_chars;
      entry.activeSeconds += row.active_seconds;
      dailyMap.set(key, entry);
      totalMinutes += row.active_seconds;
      if (started >= startOfToday) todayMinutes += row.active_seconds;
      if (started >= startOfWeek) weekMinutes += row.active_seconds;
    }
    const daily: ProjectDailyStat[] = [];
    for (let offset = STATS_DAILY_TREND_DAYS - 1; offset >= 0; offset -= 1) {
      const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset);
      const key = localDateKey(date);
      const entry = dailyMap.get(key);
      daily.push({
        date: key,
        netChars: entry?.netChars ?? 0,
        activeSeconds: entry?.activeSeconds ?? 0
      });
    }

    let streakDays = 0;
    const cursor = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    for (let offset = 0; offset < 3650; offset += 1) {
      if (!daySet.has(localDateKey(cursor))) break;
      streakDays += 1;
      cursor.setDate(cursor.getDate() - 1);
    }

    const revisionRow = database
      .prepare(
        "SELECT count(*) AS count FROM change_log WHERE project_id = ? AND command_type IN ('scene.updateBody', 'replace.apply')"
      )
      .get(projectId) as { count: number };
    const statusRows = database
      .prepare(
        "SELECT status, count(*) AS count FROM chapters WHERE project_id = ? AND deleted_at IS NULL GROUP BY status"
      )
      .all(projectId) as Array<{ status: string; count: number }>;
    // 场景状态分布必须取自 scenes.scene_status。它与 chapters.status 的章节工作流状态是两种
    // 不同语义，不得复用章节状态，也不得由章节状态推导。
    const sceneStatusRows = database
      .prepare(
        `SELECT s.scene_status AS status, count(*) AS count
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?
         GROUP BY s.scene_status`
      )
      .all(projectId) as Array<{ status: string; count: number }>;
    const snapshotRow = database
      .prepare("SELECT count(*) AS count FROM snapshots WHERE project_id = ?")
      .get(projectId) as { count: number };

    return {
      projectId,
      words: { han, nonWhitespace, withPunctuation },
      sessionMinutes: {
        today: Math.round(todayMinutes / 60),
        week: Math.round(weekMinutes / 60),
        total: Math.round(totalMinutes / 60)
      },
      daily,
      revisionCount: revisionRow.count,
      chapterStatusCounts: statusRows.map((row) => ({ status: row.status, count: row.count })),
      sceneStatusCounts: sceneStatusRows.map((row) => ({ status: row.status, count: row.count })),
      snapshotCount: snapshotRow.count,
      streakDays
    };
  };

  return { runStatsView, sessionReport, sessionDelete, runSessionList };
}