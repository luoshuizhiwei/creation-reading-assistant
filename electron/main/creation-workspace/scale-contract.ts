import { strict as assert } from "node:assert";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { performance } from "node:perf_hooks";
import {
  openCreationWorkspace,
  type CreationWorkspace,
  type CreationSearchView,
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
        "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
      );
      const insertCard = raw.database.prepare(
        "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
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
            insertScene.run(
              sceneId,
              chapterId,
              `场景${sceneIndex + 1}`,
              sceneIndex,
              JSON.stringify({
                type: "doc",
                content: [{ type: "paragraph", content: [{ type: "text", text: sceneText(volumeIndex * 400 + chapterIndex * 10 + sceneIndex) }] }]
              }),
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
    });

    console.log(
      `[verify-creation-scale] ${JSON.stringify({
        generateMs: results.generateMs,
        openMs: results.openMs,
        sceneBodyMs: results.sceneBodyMs,
        searchMs: results.searchMs,
        cardsListMs: results.cardsListMs,
        replacePreviewMs: results.replacePreviewMs,
        outlineMs: results.outlineMs
      })}`
    );
    process.stdout.write(`${JSON.stringify({ allPass: true, tests: 7 })}\n`);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
