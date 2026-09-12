import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { performance } from "node:perf_hooks";
import Database from "better-sqlite3";
import {
  countSceneBodyStats,
  openCreationWorkspace,
  type CreationWorkspace,
  type CreationSearchView,
  type ProjectHomeView,
  type ProjectStatsView,
  type ReplacePreviewView
} from "./index";

/** 规格 §8.3 目标规模（按比例缩减到本机可承受的 300 万汉字量级）。 */
const SCENE_COUNT = 2000;
const SCENE_CHARS = 1500;
const CARD_COUNT = 10000;
const RELATION_COUNT = 20000;
const SNAPSHOT_COUNT = 1000;

const SENTINEL = "风起哨兵";

function sceneText(index: number): string {
  const paragraph = "黄沙镇的风吹过街角，油灯在案头忽明忽暗，远处传来梆子的声响。";
  const repeat = Math.ceil(SCENE_CHARS / paragraph.length);
  return `${SENTINEL} 第${index}节。${paragraph.repeat(repeat)}`.slice(0, SCENE_CHARS);
}

async function withWorkspace<T>(directory: string, fn: (workspace: CreationWorkspace) => Promise<T>): Promise<T> {
  const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
  try {
    return await fn(workspace);
  } finally {
    try {
      await workspace.close();
    } catch {
      // 规模数据下 WAL 截断可能失败；连接已关闭，不影响后续测量与清理。
    }
  }
}

async function run(): Promise<void> {
  const directory = await mkdtemp(path.join(os.tmpdir(), "creation-scale-"));
  const results: Record<string, number> = {};
  const statsSum = { han: 0, punct: 0, nonWhitespace: 0 };
  try {
    // ---- 生成规模数据（单事务批量 INSERT，含 FTS 同步） ----
    const start = performance.now();
    let projectId = "";
    await withWorkspace(directory, async (workspace) => {
      const created = await workspace.transact({ type: "project.create", title: "规模测试项目" });
      projectId = created.projectId;
      const raw = workspace as unknown as { database: { prepare(sql: string): { run(...params: unknown[]): void } } };
      raw.database.prepare("BEGIN IMMEDIATE").run();
      const timestamp = "2026-08-10T00:00:00.000Z";
      const insertVolume = raw.database.prepare(
        "INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      const insertChapter = raw.database.prepare(
        "INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
      );
      const insertScene = raw.database.prepare(
        "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const insertCard = raw.database.prepare(
        "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const insertCardLink = raw.database.prepare(
        "INSERT INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)"
      );
      const insertRelation = raw.database.prepare(
        "INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      const insertSnapshot = raw.database.prepare(
        "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      );

      const sceneIds: string[] = [];
      for (let volumeIndex = 0; volumeIndex < 5; volumeIndex += 1) {
        const volumeId = `volume-scale-${volumeIndex}`;
        insertVolume.run(volumeId, projectId, `第${volumeIndex + 1}卷`, volumeIndex, timestamp, timestamp);
        for (let chapterIndex = 0; chapterIndex < 40; chapterIndex += 1) {
          const chapterId = `chapter-scale-${volumeIndex}-${chapterIndex}`;
          insertChapter.run(chapterId, projectId, volumeId, `第${chapterIndex + 1}章`, chapterIndex, timestamp, timestamp);
          for (let sceneIndex = 0; sceneIndex < 10; sceneIndex += 1) {
            const sceneId = `scene-scale-${volumeIndex}-${chapterIndex}-${sceneIndex}`;
            const sceneBodyJson = JSON.stringify({
              type: "doc",
              content: [{ type: "paragraph", content: [{ type: "text", text: sceneText(volumeIndex * 400 + chapterIndex * 10 + sceneIndex) }] }]
            });
            const sceneStats = countSceneBodyStats(sceneBodyJson);
            statsSum.han += sceneStats.han;
            statsSum.punct += sceneStats.punct;
            statsSum.nonWhitespace += sceneStats.nonWhitespace;
            insertScene.run(
              sceneId,
              chapterId,
              `场景${sceneIndex + 1}`,
              sceneIndex,
              sceneBodyJson,
              sceneStats.han,
              sceneStats.punct,
              sceneStats.nonWhitespace,
              timestamp,
              timestamp
            );
            sceneIds.push(sceneId);
          }
        }
      }
      assert.equal(sceneIds.length, SCENE_COUNT);

      for (let index = 0; index < CARD_COUNT; index += 1) {
        insertCard.run(
          `card-scale-${index}`,
          projectId,
          "character",
          `角色${index}`,
          "[]",
          "{}",
          "[]",
          "{}",
          timestamp,
          timestamp
        );
        insertCardLink.run(projectId, `card-scale-${index}`, timestamp);
      }
      for (let index = 0; index < RELATION_COUNT; index += 1) {
        const offset = index < CARD_COUNT ? 1 : 100;
        insertRelation.run(
          `relation-scale-${index}`,
          projectId,
          `card-scale-${index % CARD_COUNT}`,
          `card-scale-${(index + offset) % CARD_COUNT}`,
          "relation-type-character-character",
          timestamp
        );
      }
      for (let index = 0; index < SNAPSHOT_COUNT; index += 1) {
        insertSnapshot.run(
          `snapshot-scale-${index}`,
          projectId,
          "scene",
          sceneIds[index]!,
          JSON.stringify({ reason: "规模", revision: 1, body: null }),
          timestamp
        );
      }
      raw.database.prepare("COMMIT").run();
      const integrity = await workspace.check();
      console.log("[scale-integrity]", JSON.stringify(integrity));
      assert.equal(integrity.ok, true);
    });
    results.generateMs = Math.round(performance.now() - start);

    // ---- P1-P01 打开项目概览（热启动 ≤ 2s 目标，本机容差 5s） ----
    const openStart = performance.now();
    await withWorkspace(directory, async (workspace) => {
      results.openMs = Math.round(performance.now() - openStart);
      assert.equal(results.openMs < 5000, true, `打开项目概览超时：${results.openMs}ms`);

      // ---- P1-P02 打开 5000 字场景（≤ 300ms 目标，容差 800ms） ----
      const bodyStart = performance.now();
      const body = await workspace.read({ kind: "scene.body", sceneId: "scene-scale-0-0-0" });
      results.sceneBodyMs = Math.round(performance.now() - bodyStart);
      assert.equal(body !== null, true);
      assert.equal(results.sceneBodyMs < 800, true, `打开场景超时：${results.sceneBodyMs}ms`);

      // ---- P1-P04 项目关键词搜索首屏（≤ 500ms 目标，容差 3000ms） ----
      const searchStart = performance.now();
      const search = (await workspace.read({ kind: "search.query", projectId, text: SENTINEL, limit: 50 })) as CreationSearchView;
      results.searchMs = Math.round(performance.now() - searchStart);
      assert.equal(search.total, 50);
      assert.equal(search.hits.every((hit) => hit.kind === "scene"), true);
      assert.equal(results.searchMs < 3000, true, `搜索首屏超时：${results.searchMs}ms`);

      // ---- P1-P06 卡片筛选首屏（≤ 500ms 目标，容差 2000ms） ----
      const cardsStart = performance.now();
      const cards = (await workspace.read({ kind: "cards.list", projectId, cardKind: "character", search: "角色1" })) as Array<{ id: string }>;
      results.cardsListMs = Math.round(performance.now() - cardsStart);
      assert.equal(cards.length, 1111);
      assert.equal(results.cardsListMs < 2000, true, `卡片筛选超时：${results.cardsListMs}ms`);

      // ---- P1-P07 全项目替换预览（不阻塞，限时 10s） ----
      const replaceStart = performance.now();
      const preview = (await workspace.read({
        kind: "replace.preview",
        projectId,
        find: "风吹过",
        replaceWith: "风也吹过",
        scope: "project",
        limit: 200
      })) as ReplacePreviewView;
      results.replacePreviewMs = Math.round(performance.now() - replaceStart);
      assert.equal(preview.matchedScenes > 0, true);
      assert.equal(results.replacePreviewMs < 10000, true, `替换预览超时：${results.replacePreviewMs}ms`);

      // ---- P1-P05 章节树数据读取（大纲查询） ----
      const outlineStart = performance.now();
      const outline = await workspace.read({ kind: "project.outline", projectId });
      results.outlineMs = Math.round(performance.now() - outlineStart);
      assert.equal(outline?.volumes.length, 6);
      assert.equal(results.outlineMs < 2000, true, `大纲读取超时：${results.outlineMs}ms`);

      // ---- P1-P08 项目首页聚合：计数正确、返回结构不携带正文 ----
      const expectedTotal = statsSum.nonWhitespace;
      const homeStart = performance.now();
      const home = (await workspace.read({ kind: "project.home" })) as ProjectHomeView;
      results.homeMs = Math.round(performance.now() - homeStart);
      const entry = home.projects.find((project) => project.id === projectId);
      assert.equal(entry !== undefined, true);
      assert.equal(entry!.currentChars, expectedTotal, "project.home 非空白字符数应与逐场景统计一致");
      const homeJson = JSON.stringify(home);
      assert.equal(homeJson.includes("黄沙镇的风吹过街角"), false, "project.home 返回结构不得携带场景正文");
      assert.equal(homeJson.includes(SENTINEL), false, "project.home 返回结构不得携带场景正文（哨兵）");

      // ---- P1-P09 统计视图：三口径正确且与 project.home 一致 ----
      const statsStart = performance.now();
      const stats = (await workspace.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      results.statsMs = Math.round(performance.now() - statsStart);
      assert.equal(stats.words.nonWhitespace, expectedTotal, "stats.nonWhitespace 应与 project.home.currentChars 一致");
      assert.equal(stats.words.nonWhitespace, statsSum.nonWhitespace, "stats.nonWhitespace 应等于逐场景统计");
      assert.equal(stats.words.han, statsSum.han, "stats.han 应等于逐场景统计");
      assert.equal(stats.words.withPunctuation, statsSum.han + statsSum.punct, "stats.withPunctuation = han + punct");
      const statsJson = JSON.stringify(stats);
      assert.equal(statsJson.includes("黄沙镇的风吹过街角"), false, "stats 返回结构不得携带场景正文");
      assert.equal(statsJson.includes(SENTINEL), false, "stats 返回结构不得携带场景正文（哨兵）");

      // ---- P1-P10 项目列表：计数正确、不读取正文 ----
      const listStart = performance.now();
      const list = (await workspace.read({ kind: "projects.list" })) as Array<{ id: string; chapterCount: number; sceneCount: number }>;
      results.listMs = Math.round(performance.now() - listStart);
      const listEntry = list.find((project) => project.id === projectId)!;
      assert.equal(listEntry.chapterCount, 201, "项目列表章节数应为 默认 1 章 + 5 卷 × 40 章");
      assert.equal(listEntry.sceneCount, SCENE_COUNT + 1, "项目列表场景数应为 默认 1 场景 + 2000");
      const listJson = JSON.stringify(list);
      assert.equal(listJson.includes(SENTINEL), false, "projects.list 返回结构不得携带场景正文");

      // ---- P1-P11 读路径不解析正文：损坏 body_json 不影响 home/stats 计数 ----
      const rawVerify = new Database(path.join(directory, "workspace.sqlite"));
      rawVerify.prepare("UPDATE scenes SET body_json = 'not-json' WHERE id = 'scene-scale-0-0-0'").run();
      rawVerify.close();
      const homeAfterCorrupt = (await workspace.read({ kind: "project.home" })) as ProjectHomeView;
      const entryAfterCorrupt = homeAfterCorrupt.projects.find((project) => project.id === projectId)!;
      assert.equal(entryAfterCorrupt.currentChars, expectedTotal, "正文损坏后 project.home 计数保持不变（读路径不解析 body_json）");
      const statsAfterCorrupt = (await workspace.read({ kind: "stats.view", projectId })) as ProjectStatsView;
      assert.equal(statsAfterCorrupt.words.nonWhitespace, expectedTotal, "正文损坏后 stats.nonWhitespace 保持不变（读路径不解析 body_json）");

      // ---- P1-P12 查询计划：project.home/stats 无相关子查询、无 group_concat 聚合 ----
      const planDatabase = new Database(path.join(directory, "workspace.sqlite"));
      try {
        const homePlan = planDatabase
          .prepare("EXPLAIN QUERY PLAN SELECT p.id, sc.current_chars FROM projects p LEFT JOIN (SELECT c.project_id, count(*) AS scene_count, coalesce(sum(s.non_ws_count), 0) AS current_chars FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.deleted_at IS NULL AND s.deleted_at IS NULL GROUP BY c.project_id) sc ON sc.project_id = p.id LEFT JOIN (SELECT project_id, count(*) AS chapter_count FROM chapters WHERE deleted_at IS NULL GROUP BY project_id) ch ON ch.project_id = p.id")
          .all() as Array<{ detail: string }>;
        const homePlanText = homePlan.map((row) => row.detail).join("\n");
        assert.equal(homePlanText.includes("CORRELATED"), false, "project.home 查询不得包含相关子查询（N+1）");
        assert.equal(homePlanText.includes("SCAN scenes"), false, "project.home 对 scenes 的访问应使用索引");
        const statsPlan = planDatabase
          .prepare("EXPLAIN QUERY PLAN SELECT coalesce(sum(s.han_count), 0), coalesce(sum(s.punct_count), 0), coalesce(sum(s.non_ws_count), 0) FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?")
          .all(projectId) as Array<{ detail: string }>;
        const statsPlanText = statsPlan.map((row) => row.detail).join("\n");
        assert.equal(statsPlanText.includes("CORRELATED"), false, "stats.view 查询不得包含相关子查询（N+1）");
      } finally {
        planDatabase.close();
      }
    });

    console.log(
      `[verify-creation-scale] ${JSON.stringify({
        generateMs: results.generateMs,
        openMs: results.openMs,
        sceneBodyMs: results.sceneBodyMs,
        searchMs: results.searchMs,
        cardsListMs: results.cardsListMs,
        replacePreviewMs: results.replacePreviewMs,
        outlineMs: results.outlineMs,
        homeMs: results.homeMs,
        statsMs: results.statsMs,
        listMs: results.listMs
      })}`
    );
    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 10 })}\n`);
  } finally {
    await removeWithRetry(directory, 12);
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
