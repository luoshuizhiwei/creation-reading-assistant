import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdir, mkdtemp } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { openCreationWorkspace, SCHEMA_VERSION, type CreationWorkspace } from "./index";

const V1_SCHEMA_SQL = `
CREATE TABLE workspace_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE projects (
  id TEXT PRIMARY KEY, title TEXT NOT NULL,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE chapters (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title TEXT NOT NULL, sort_order INTEGER NOT NULL,
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_chapters_project_order ON chapters(project_id, sort_order);
CREATE TABLE scenes (
  id TEXT PRIMARY KEY,
  chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  title TEXT NOT NULL, sort_order INTEGER NOT NULL,
  body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_scenes_chapter_order ON scenes(chapter_id, sort_order);
CREATE TABLE cards (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  kind TEXT NOT NULL, title TEXT NOT NULL,
  content_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_cards_project_kind ON cards(project_id, kind);
CREATE TABLE card_relations (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  relation_type TEXT NOT NULL, created_at TEXT NOT NULL,
  UNIQUE(from_card_id, to_card_id, relation_type)
);
CREATE INDEX idx_card_relations_from ON card_relations(from_card_id);
CREATE INDEX idx_card_relations_to ON card_relations(to_card_id);
CREATE TABLE resources (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  relative_path TEXT NOT NULL, sha256 TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_resources_project ON resources(project_id);
CREATE TABLE snapshots (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  subject_type TEXT NOT NULL, subject_id TEXT NOT NULL,
  payload_json TEXT NOT NULL, created_at TEXT NOT NULL
);
CREATE INDEX idx_snapshots_project_created ON snapshots(project_id, created_at);
CREATE TABLE change_log (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
  command_type TEXT NOT NULL, changes_json TEXT NOT NULL,
  committed_at TEXT NOT NULL
);
CREATE VIRTUAL TABLE scenes_fts USING fts5(title, body_json, content='scenes', content_rowid='rowid');
CREATE TRIGGER scenes_ai AFTER INSERT ON scenes BEGIN
  INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
END;
CREATE TRIGGER scenes_ad AFTER DELETE ON scenes BEGIN
  INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
END;
CREATE TRIGGER scenes_au AFTER UPDATE ON scenes BEGIN
  INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
  INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
END;
PRAGMA user_version = 1;
`;

/** 断言 v1 完整旧库可逐级迁移到最新 schema 且数据保留、check 通过、表清单完整、未知版本拒绝。 */
async function run(): Promise<void> {
  const parent = await mkdtemp(path.join(os.tmpdir(), "creation-schema-"));
  let tests = 0;
  try {
    const scenario = async (name: string, fn: () => Promise<void>): Promise<void> => {
      try {
        await fn();
        tests += 1;
      } catch (error) {
        throw new Error(`场景「${name}」失败：${error instanceof Error ? error.message : String(error)}`);
      }
    };

    await scenario("v1 完整旧库逐级迁移到最新：数据保留 + check 通过 + 表清单完整", async () => {
      const directory = path.join(parent, "v1");
      await mkdir(directory, { recursive: true });
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.exec(V1_SCHEMA_SQL);
      raw
        .prepare("INSERT INTO projects(id, title, created_at, updated_at) VALUES (?, ?, ?, ?)")
        .run("project-v1", "旧项目v1", "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z");
      raw
        .prepare("INSERT INTO chapters(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run("chapter-v1", "project-v1", "第一章", 0, "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z");
      raw
        .prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(
          "scene-v1",
          "chapter-v1",
          "默认场景",
          0,
          JSON.stringify({ type: "doc", content: [{ type: "paragraph", content: [{ type: "text", text: "旧稿正文，含标点与空白。  " }] }] }),
          "2026-01-01T00:00:00.000Z",
          "2026-01-01T00:00:00.000Z"
        );
      raw.close();

      const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
      try {
        const report = await workspace.check();
        assert.equal(report.ok, true);
        assert.equal(report.schemaVersion, SCHEMA_VERSION);
        const list = (await workspace.read({ kind: "projects.list" })) as Array<{ title: string }>;
        assert.equal(list.some((project) => project.title === "旧项目v1"), true);
        const tree = await workspace.read({ kind: "project.tree", projectId: "project-v1" });
        assert.equal(tree?.chapters[0]?.scenes[0]?.id, "scene-v1");
        // v8→v9 迁移回填：旧正文非空白字符数（10 汉字 + 2 标点 = 12，空白不计）必须进入 project.home。
        const home = (await workspace.read({ kind: "project.home" })) as { projects: Array<{ id: string; currentChars: number }> };
        const entry = home.projects.find((project) => project.id === "project-v1");
        assert.equal(entry?.currentChars, 12);
        const stats = (await workspace.read({ kind: "stats.view", projectId: "project-v1" })) as { words: { han: number; nonWhitespace: number } };
        assert.equal(stats.words.han, 10);
        assert.equal(stats.words.nonWhitespace, 12);
      } finally {
        await workspace.close();
      }
      const reopened = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(reopened.pragma("user_version", { simple: true })), SCHEMA_VERSION);
      const sceneColumns = new Set((reopened.prepare("PRAGMA table_info(scenes)").all() as Array<{ name: string }>).map((column) => column.name));
      assert.equal(sceneColumns.has("summary"), true);
      assert.equal(sceneColumns.has("scene_status"), true);
      const requiredTables = [
        "projects", "volumes", "chapters", "scenes", "cards", "card_types",
        "relation_types", "card_relations", "project_card_links", "resources", "snapshots", "change_log",
        "writing_sessions", "inbox_items", "annotations", "scenes_fts"
      ];
      for (const table of requiredTables) {
        const found = reopened.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").get(table);
        assert.equal(found !== undefined, true, `迁移后缺少表 ${table}`);
      }
      const cardTypes = reopened.prepare("SELECT count(*) AS count FROM card_types").get() as { count: number };
      assert.equal(cardTypes.count, 8);
      reopened.close();
    });

    await scenario("v10 增量升级到 v11：既有场景获得安全默认摘要与场景状态", async () => {
      const directory = path.join(parent, "v10-to-v11");
      await mkdir(directory, { recursive: true });
      const prepared = await openCreationWorkspace({ directory });
      const created = await prepared.transact({ type: "project.create", title: "v10 项目" });
      await prepared.close();
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.exec("ALTER TABLE scenes DROP COLUMN summary; ALTER TABLE scenes DROP COLUMN scene_status; PRAGMA user_version = 10;");
      raw.close();

      const migrated = await openCreationWorkspace({ directory });
      const report = await migrated.check();
      assert.equal(report.schemaVersion, SCHEMA_VERSION);
      const outline = await migrated.read({ kind: "project.outline", projectId: created.projectId });
      const scene = outline?.volumes[0]?.chapters[0]?.scenes[0];
      assert.equal(scene?.summary, "");
      assert.equal(scene?.status, "planned");
      await migrated.close();
    });

    await scenario("v11 增量升级到 v12：建校对忽略表且可重复打开（幂等）", async () => {
      const directory = path.join(parent, "v11-to-v12");
      await mkdir(directory, { recursive: true });
      const prepared = await openCreationWorkspace({ directory });
      const created = await prepared.transact({ type: "project.create", title: "v11 项目" });
      await prepared.close();
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.exec("DROP TABLE IF EXISTS proof_ignores; PRAGMA user_version = 11;");
      raw.close();

      const migrated = await openCreationWorkspace({ directory });
      const report = await migrated.check();
      assert.equal(report.schemaVersion, SCHEMA_VERSION);
      const created2 = await migrated.transact({
        type: "proof.ignore",
        projectId: created.projectId,
        sceneId: created.sceneId,
        rule: "repeatedChar",
        locationKey: "repeatedChar#0#deadbeef",
        matchedText: "他他他"
      });
      assert.equal(created2.ignoreIds.length, 1);
      await migrated.close();

      // 重复打开必须自愈到同一版本，且忽略记录不丢。
      const reopened = await openCreationWorkspace({ directory });
      const repopenedReport = await reopened.check();
      assert.equal(repopenedReport.schemaVersion, 12);
      const ignores = (await reopened.read({
        kind: "proof.ignores",
        projectId: created.projectId
      })) as Array<{ locationKey: string; matchedText: string }>;
      assert.equal(ignores.length, 1);
      assert.equal(ignores[0]!.locationKey, "repeatedChar#0#deadbeef");
      assert.equal(ignores[0]!.matchedText, "他他他");
      await reopened.close();

      const after = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(after.pragma("user_version", { simple: true })), SCHEMA_VERSION);
      after.close();
    });

    await scenario("未知更高版本拒绝打开（禁止 destructive fallback）", async () => {
      const directory = path.join(parent, "future");
      await mkdir(directory, { recursive: true });
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.exec("CREATE TABLE projects (id TEXT PRIMARY KEY); PRAGMA user_version = 99;");
      raw.close();
      let error: unknown;
      try {
        await openCreationWorkspace({ directory });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
    });

    await scenario("损坏的 schema 版本（0 之外的未知中间值）不产生半迁移状态", async () => {
      const directory = path.join(parent, "half");
      await mkdir(directory, { recursive: true });
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.exec("CREATE TABLE projects (id TEXT PRIMARY KEY); PRAGMA user_version = 4;");
      raw.close();
      // v4 库缺 volumes/chapters/scenes/cards 等表：v10 激活前必须直接拒绝，
      // 不能把残缺库标成最新版本或留下半张 project_card_links 表。
      let error: unknown;
      try {
        await openCreationWorkspace({ directory });
      } catch (caught) {
        error = caught;
      }
      assert.equal((error as { code?: string }).code, "integrity");
      const after = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(after.pragma("user_version", { simple: true })), 9);
      assert.equal(
        after.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'project_card_links'").get(),
        undefined
      );
      after.close();
    });

    process.stdout.write(`${JSON.stringify({ allPass: true, tests })}\n`);
  } finally {
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await removeWithRetry(parent);
        break;
      } catch {
        await new Promise((resolve) => setTimeout(resolve, 300));
      }
    }
  }
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
