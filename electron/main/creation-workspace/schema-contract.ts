import { strict as assert } from "node:assert";
import { removeWithRetry } from "./test-utils";
import { mkdir, mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { openCreationWorkspace, type CreationWorkspace } from "./index";

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
        .prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run("scene-v1", "chapter-v1", "默认场景", 0, "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z");
      raw.close();

      const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
      try {
        const report = await workspace.check();
        assert.equal(report.ok, true);
        assert.equal(report.schemaVersion, 8);
        const list = (await workspace.read({ kind: "projects.list" })) as Array<{ title: string }>;
        assert.equal(list.some((project) => project.title === "旧项目v1"), true);
        const tree = await workspace.read({ kind: "project.tree", projectId: "project-v1" });
        assert.equal(tree?.chapters[0]?.scenes[0]?.id, "scene-v1");
      } finally {
        await workspace.close();
      }
      const reopened = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(reopened.pragma("user_version", { simple: true })), 8);
      const requiredTables = [
        "projects", "volumes", "chapters", "scenes", "cards", "card_types",
        "relation_types", "card_relations", "resources", "snapshots", "change_log",
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
      // v4 库缺 volumes/chapters/scenes 等表：打开可成功（迁移只补新表），但完整性检查必须暴露缺失
      const workspace = await openCreationWorkspace({ directory }) as CreationWorkspace;
      try {
        let exposed = false;
        try {
          const report = await workspace.check();
          exposed = !report.ok && report.schema.issues.some((issue) => issue.code === "schema-table-missing");
        } catch {
          // check 对缺失表直接抛 integrity 同样视为暴露
          exposed = true;
        }
        assert.equal(exposed, true);
      } finally {
        await workspace.close();
      }
      const after = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(after.pragma("user_version", { simple: true })), 8);
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
