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

    await scenario("check 完整性报告包含 sessions 计数", async () => {
      const checked = await workspace!.check();
      assert.equal(checked.counts.sessions, 2);
    });

    await scenario("新开数据库迁移到 v5 且会话表存在", async () => {
      await workspace!.close();
      workspace = await openCreationWorkspace({ directory });
      const checked = await workspace!.check();
      assert.equal(checked.ok, true);
      assert.equal(checked.schemaVersion, 9);
      assert.equal(checked.counts.sessions, 2);
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
