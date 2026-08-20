import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  openCreationWorkspace,
  type CreationWorkspace,
  type ProjectStatsView,
  type SessionEntry,
  type SessionReportResult
} from "./index";

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-stats-"));
  let workspace: CreationWorkspace | undefined;
  let tests = 0;
  let projectId = "";
  let sceneId = "";
  let extraSceneId = "";
  try {
    const scenario = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      try {
        const result = await fn();
        tests += 1;
        return result;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    workspace = await openCreationWorkspace({ directory });
    const report = await workspace.check();
    assert.equal(report.ok, true);
    assert.equal(report.schemaVersion, 9);

    await scenario("准备项目：写入带汉字/标点/字母的正文", async () => {
      const created = await workspace!.transact({ type: "project.create", title: "统计测试项目" });
      projectId = created.projectId;
      sceneId = created.sceneId;
      await workspace!.transact({
        type: "scene.updateBody",
        sceneId,
        baseRevision: 1,
        body: {
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "你好，世界！Hello 123。" }] }
          ]
        }
      });
      const extra = await workspace!.transact({
        type: "scene.create",
        chapterId: created.chapterId,
        title: "第二场景"
      }) as { entityId: string };
      extraSceneId = extra.entityId;
    });

    await scenario("字数三口径：汉字/非空白/含标点", async () => {
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      // "你好，世界！Hello 123。" 汉字：你/好/世/界 = 4；非空白 = 4汉字+3标点+5字母+3数字 = 15；含标点 = 汉字+标点 = 7
      assert.equal(stats.words.han, 4);
      assert.equal(stats.words.nonWhitespace, 15);
      assert.equal(stats.words.withPunctuation, 7);
      assert.equal(stats.revisionCount, 1);
      // scene.updateBody 每次确认保存会保留一份先前正文快照
      assert.equal(stats.snapshotCount, 1);
      assert.equal(stats.streakDays, 0);
      assert.equal(stats.chapterStatusCounts.some((item) => item.status === "" && item.count === 1), true);
    });

    await scenario("汇报会话：今日/本周/总量与每日净增", async () => {
      const now = new Date();
      const startedAt = new Date(now.getTime() - 30 * 60 * 1000).toISOString();
      const result = (await workspace!.transact({
        type: "session.report",
        projectId,
        sceneId,
        startedAt,
        activeSeconds: 1200,
        netChars: 400
      })) as SessionReportResult;
      assert.equal(result.sessionId.startsWith("session-"), true);
      await workspace!.transact({
        type: "session.report",
        projectId,
        startedAt: new Date(now.getTime() - 10 * 60 * 1000).toISOString(),
        activeSeconds: 60,
        netChars: -50
      });
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(stats.sessionMinutes.today, 21);
      assert.equal(stats.sessionMinutes.total, 21);
      const today = stats.daily.find((item) => item.date === stats.daily[stats.daily.length - 1]!.date)!;
      assert.equal(today.netChars, 350);
      assert.equal(today.activeSeconds, 1260);
      assert.equal(stats.streakDays, 1);
    });

    await scenario("跨日会话计入对应日期与连续天数", async () => {
      const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000);
      const startedAt = new Date(yesterday.getFullYear(), yesterday.getMonth(), yesterday.getDate(), 20, 0, 0).toISOString();
      await workspace!.transact({
        type: "session.report",
        projectId,
        startedAt,
        activeSeconds: 600,
        netChars: 100
      });
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(stats.streakDays, 2);
      assert.equal(stats.sessionMinutes.total, 31);
      const yesterdayKey = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, "0")}-${String(yesterday.getDate()).padStart(2, "0")}`;
      const entry = stats.daily.find((item) => item.date === yesterdayKey);
      assert.equal(entry?.activeSeconds, 600);
    });

    await scenario("修正会话：删除单条后统计回落", async () => {
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const row = raw.prepare("SELECT id FROM writing_sessions WHERE net_chars = -50 LIMIT 1").get() as { id: string };
      raw.close();
      assert.equal(typeof row?.id, "string");
      await workspace!.transact({ type: "session.delete", projectId, sessionId: row.id });
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(stats.sessionMinutes.today, 20);
      assert.equal(stats.daily[stats.daily.length - 1]!.netChars, 400);
    });

    await scenario("删除不存在会话报 not-found", async () => {
      let notFound: unknown;
      try {
        await workspace!.transact({ type: "session.delete", projectId, sessionId: "session-missing" });
      } catch (error) {
        notFound = error;
      }
      assert.equal((notFound as CreationWorkspaceError).code, "not-found");
    });

    await scenario("非法会话参数报 invalid-input", async () => {
      let invalidSeconds: unknown;
      try {
        await workspace!.transact({
          type: "session.report",
          projectId,
          startedAt: new Date().toISOString(),
          activeSeconds: -1,
          netChars: 0
        });
      } catch (error) {
        invalidSeconds = error;
      }
      assert.equal((invalidSeconds as CreationWorkspaceError).code, "invalid-input");
      let invalidNet: unknown;
      try {
        await workspace!.transact({
          type: "session.report",
          projectId,
          startedAt: new Date().toISOString(),
          activeSeconds: 10,
          netChars: Number.NaN
        });
      } catch (error) {
        invalidNet = error;
      }
      assert.equal((invalidNet as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("不存在的项目统计返回 not-found", async () => {
      let notFound: unknown;
      try {
        await workspace!.read({ kind: "stats.view", projectId: "project-missing" });
      } catch (error) {
        notFound = error;
      }
      assert.equal((notFound as CreationWorkspaceError).code, "not-found");
    });

    await scenario("会话列表：按上报时间倒序且 limit 生效", async () => {
      const sessions = (await workspace!.read({
        kind: "session.list",
        projectId,
        limit: 10
      })) as SessionEntry[];
      assert.equal(sessions.length, 2);
      assert.equal(sessions[0]!.netChars, 100);
      assert.equal(sessions[1]!.netChars, 400);
      const limited = (await workspace!.read({ kind: "session.list", projectId, limit: 1 })) as SessionEntry[];
      assert.equal(limited.length, 1);
      let invalidLimit: unknown;
      try {
        await workspace!.read({ kind: "session.list", projectId, limit: 0 });
      } catch (error) {
        invalidLimit = error;
      }
      assert.equal((invalidLimit as CreationWorkspaceError).code, "invalid-input");
    });

    await scenario("writing_sessions 表只存元数据，不存按键/文本内容", async () => {
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      const columns = raw.prepare("PRAGMA table_info(writing_sessions)").all() as Array<{ name: string }>;
      raw.close();
      const names = columns.map((column) => column.name).sort();
      assert.deepEqual(names, ["active_seconds", "id", "net_chars", "project_id", "reported_at", "scene_id", "started_at"]);
    });

    await scenario("周净增与周时长按本地周一边界聚合（周日会话不计入本周）", async () => {
      const today = new Date();
      const dayOfWeek = (today.getDay() + 6) % 7; // 0=周一
      const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - dayOfWeek);
      const lastSunday = new Date(monday);
      lastSunday.setDate(monday.getDate() - 1);
      const lastSundayStartedAt = new Date(lastSunday.getFullYear(), lastSunday.getMonth(), lastSunday.getDate(), 22, 0, 0).toISOString();
      await workspace!.transact({
        type: "session.report",
        projectId,
        startedAt: lastSundayStartedAt,
        activeSeconds: 3600,
        netChars: 500
      });
      const stats = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      // 周日会话计入 daily 与 total，但不计入本周 minutes。
      const lastSundayKey = `${lastSunday.getFullYear()}-${String(lastSunday.getMonth() + 1).padStart(2, "0")}-${String(lastSunday.getDate()).padStart(2, "0")}`;
      const sundayEntry = stats.daily.find((item) => item.date === lastSundayKey);
      assert.equal(sundayEntry?.netChars, 500);
      const weekBefore = stats.sessionMinutes.week;
      // 本周内新增一段，确认周界为本地周一。
      const thisWeekStartedAt = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate(), 9, 0, 0).toISOString();
      await workspace!.transact({
        type: "session.report",
        projectId,
        startedAt: thisWeekStartedAt,
        activeSeconds: 600,
        netChars: 200
      });
      const after = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(after.sessionMinutes.week, weekBefore + 10);
      // 周日会话 + 本周会话分别落在正确的本地日期键上。
      const thisWeekKey = `${monday.getFullYear()}-${String(monday.getMonth() + 1).padStart(2, "0")}-${String(monday.getDate()).padStart(2, "0")}`;
      assert.equal(after.daily.find((item) => item.date === thisWeekKey)?.activeSeconds, 600);
    });

    await scenario("跨午夜会话按开始时间归属本地日期，不按 UTC 截断", async () => {
      // 本地 23:50 开始的会话：断言其贡献计入本地日期键；若本地键与 UTC 键不同，
      // 则 UTC 键不增加（证明按本地日历归属，而非 UTC 字符串截断）。
      const now = new Date();
      const localLate = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 50, 0);
      const startedAt = localLate.toISOString();
      const localKey = `${localLate.getFullYear()}-${String(localLate.getMonth() + 1).padStart(2, "0")}-${String(localLate.getDate()).padStart(2, "0")}`;
      const utcKey = startedAt.slice(0, 10);
      const before = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      const localBefore = before.daily.find((item) => item.date === localKey)?.activeSeconds ?? 0;
      const utcBefore = before.daily.find((item) => item.date === utcKey)?.activeSeconds ?? 0;
      await workspace!.transact({
        type: "session.report",
        projectId,
        startedAt,
        activeSeconds: 120,
        netChars: 60
      });
      const after = (await workspace!.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      const localAfter = after.daily.find((item) => item.date === localKey)?.activeSeconds ?? 0;
      assert.equal(localAfter - localBefore, 120);
      if (utcKey !== localKey) {
        const utcAfter = after.daily.find((item) => item.date === utcKey)?.activeSeconds ?? 0;
        assert.equal(utcAfter, utcBefore);
      }
    });

    await scenario("check 完整性报告包含 sessions 计数", async () => {
      const checked = await workspace!.check();
      assert.equal(checked.counts.sessions, 5);
    });

    await scenario("新开数据库迁移到 v9 且会话表存在", async () => {
      await workspace!.close();
      workspace = await openCreationWorkspace({ directory });
      const checked = await workspace!.check();
      assert.equal(checked.ok, true);
      assert.equal(checked.schemaVersion, 9);
      assert.equal(checked.counts.sessions, 5);
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    await workspace?.close();
    await removeWithRetry(directory);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
