import { strict as assert } from "node:assert";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import Database from "better-sqlite3";
import { CreationWorkspaceError, openCreationWorkspace } from "./index";
import type { CreationProjectSummary } from "../../../src/types/creation";
import { createCreationCoordinator } from "../creation-coordinator/index";

const V1_SCHEMA_SQL = `
CREATE TABLE workspace_meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
CREATE TABLE projects (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE chapters (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_chapters_project_order ON chapters(project_id, sort_order);
CREATE TABLE scenes (
  id TEXT PRIMARY KEY,
  chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  sort_order INTEGER NOT NULL,
  body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_scenes_chapter_order ON scenes(chapter_id, sort_order);
CREATE TABLE cards (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  content_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  revision INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_cards_project_kind ON cards(project_id, kind);
CREATE TABLE card_relations (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
  relation_type TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(from_card_id, to_card_id, relation_type)
);
CREATE INDEX idx_card_relations_from ON card_relations(from_card_id);
CREATE INDEX idx_card_relations_to ON card_relations(to_card_id);
CREATE TABLE resources (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  relative_path TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_resources_project ON resources(project_id);
CREATE TABLE snapshots (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  subject_type TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX idx_snapshots_project_created ON snapshots(project_id, created_at);
CREATE TABLE change_log (
  sequence INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
  command_type TEXT NOT NULL,
  changes_json TEXT NOT NULL,
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

function createV1Workspace(directory: string): void {
  const db = new Database(path.join(directory, "workspace.sqlite"));
  db.exec(V1_SCHEMA_SQL);
  const timestamp = "2026-01-01T00:00:00.000Z";
  db.prepare("INSERT INTO projects(id, title, created_at, updated_at) VALUES (?, ?, ?, ?)").run(
    "project-v1",
    "旧项目",
    timestamp,
    timestamp
  );
  db.prepare("INSERT INTO chapters(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)").run(
    "chapter-v1",
    "project-v1",
    "第一章",
    0,
    timestamp,
    timestamp
  );
  db.prepare(
    "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
  ).run("scene-v1", "chapter-v1", "默认场景", 0, '{"type":"doc","content":[]}', timestamp, timestamp);
  db.close();
}

function createPartialV1Workspace(directory: string): void {
  const db = new Database(path.join(directory, "workspace.sqlite"));
  db.exec(
    V1_SCHEMA_SQL.replace(
      "title TEXT NOT NULL,\n  created_at TEXT NOT NULL,",
      "title TEXT NOT NULL,\n  setup_json TEXT NOT NULL DEFAULT '{}',\n  created_at TEXT NOT NULL,"
    )
  );
  db.close();
}

async function expectWorkspaceError(code: string, fn: () => Promise<unknown>): Promise<CreationWorkspaceError> {
  try {
    await fn();
  } catch (error) {
    assert.equal(error instanceof CreationWorkspaceError, true, "expected CreationWorkspaceError");
    assert.equal((error as CreationWorkspaceError).code, code);
    return error as CreationWorkspaceError;
  }
  assert.fail("expected workspace error to be thrown");
}

let passed = 0;
const failures: string[] = [];

async function test(name: string, fn: () => Promise<void> | void): Promise<void> {
  try {
    await fn();
    passed += 1;
  } catch (error) {
    failures.push(`${name}: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
  }
}

const minimalSetup = { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["起草"] };
const defaultSetup = {
  template: "blank" as const,
  weeklyUpdateDays: [],
  chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
};

async function run(): Promise<void> {
  const base = await mkdtemp(path.join(os.tmpdir(), "creation-project-shell-"));
  try {
    await test("v1 schema migrates atomically to v3 preserving data", async () => {
      const directory = path.join(base, "migrate-v1");
      await mkdir(directory, { recursive: true });
      createV1Workspace(directory);
      const workspace = await openCreationWorkspace({ directory });
      const report = await workspace.check();
      assert.equal(report.schemaVersion, 8);
      const list = (await workspace.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(list.length, 1);
      assert.equal(list[0].id, "project-v1");
      assert.equal(list[0].title, "旧项目");
      assert.deepEqual(list[0].setup, defaultSetup);
      assert.equal(list[0].chapterCount, 1);
      assert.equal(list[0].sceneCount, 1);
      assert.equal(list[0].revision, 1);
      assert.equal(typeof list[0].updatedAt, "string");
      const tree = await workspace.read({ kind: "project.tree", projectId: "project-v1" });
      assert.deepEqual(tree?.project.setup, defaultSetup);
      await workspace.close();
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(raw.pragma("user_version", { simple: true })), 8);
      const columns = raw.prepare("PRAGMA table_info(projects)").all() as Array<{ name: string }>;
      assert.equal(columns.some((column) => column.name === "setup_json"), true);
      raw.close();
    });

    await test("failed v1 migration rolls back without partial state", async () => {
      const directory = path.join(base, "migrate-fail");
      await mkdir(directory, { recursive: true });
      // Simulate a v1 database that already carries a setup_json column: the
      // migration's ALTER TABLE must fail and roll back without bumping user_version.
      createPartialV1Workspace(directory);
      await expectWorkspaceError("integrity", () => openCreationWorkspace({ directory }));
      const after = new Database(path.join(directory, "workspace.sqlite"));
      assert.equal(Number(after.pragma("user_version", { simple: true })), 1);
      after.close();
    });

    await test("project.create persists full setup and preserves created ids", async () => {
      const directory = path.join(base, "create-full");
      const workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({
        type: "project.create",
        title: "测试项目",
        setup: {
          description: " 桌面端项目壳契约 ",
          genre: "测试",
          template: "serial",
          totalWordGoal: 50000,
          dailyWordGoal: 1500,
          weeklyWordGoal: 10000,
          targetDate: "2026-12-31",
          weeklyUpdateDays: [6, 1, 1],
          chapterWorkflow: ["起草", "修订"]
        }
      });
      const tree = await workspace.read({ kind: "project.tree", projectId: created.projectId });
      assert.equal(tree?.project.id, created.projectId);
      assert.equal(tree?.project.title, "测试项目");
      assert.deepEqual(tree?.project.setup, {
        template: "serial",
        description: "桌面端项目壳契约",
        genre: "测试",
        totalWordGoal: 50000,
        dailyWordGoal: 1500,
        weeklyWordGoal: 10000,
        targetDate: "2026-12-31",
        weeklyUpdateDays: [1, 6],
        chapterWorkflow: ["起草", "修订"]
      });
      assert.equal(tree?.chapters.length, 1);
      assert.equal(tree?.chapters[0].id, created.chapterId);
      assert.equal(tree?.chapters[0].title, "第一章");
      assert.equal(tree?.chapters[0].scenes.length, 1);
      assert.equal(tree?.chapters[0].scenes[0].id, created.sceneId);
      assert.equal(tree?.chapters[0].scenes[0].title, "默认场景");
      assert.deepEqual(tree?.chapters[0].scenes[0].body, { type: "doc", content: [] });
      await workspace.close();
    });

    await test("legacy title-only create keeps working with default setup", async () => {
      const directory = path.join(base, "create-legacy");
      const workspace = await openCreationWorkspace({ directory });
      const created = await workspace.transact({ type: "project.create", title: "测试作品" });
      const tree = await workspace.read({ kind: "project.tree", projectId: created.projectId });
      assert.deepEqual(tree?.project.setup, defaultSetup);
      await workspace.close();
    });

    await test("invalid setup inputs are rejected without side effects", async () => {
      const directory = path.join(base, "invalid-setup");
      const workspace = await openCreationWorkspace({ directory });
      const invalidCommands = [
        { type: "project.create", title: "测试项目", setup: { template: "epic", weeklyUpdateDays: [], chapterWorkflow: ["起草"] } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["起草"], totalWordGoal: -1 } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: [7], chapterWorkflow: ["起草"] } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: "monday", chapterWorkflow: ["起草"] } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: [] } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: [""] } },
        { type: "project.create", title: "测试项目", setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["起草"], targetDate: "2026/12/31" } },
        { type: "project.create", title: "   ", setup: minimalSetup },
        { type: "project.create", title: "测试项目", setup: null }
      ];
      for (const command of invalidCommands) {
        await expectWorkspaceError("invalid-input", () => workspace.transact(command as never));
      }
      const list = (await workspace.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(list.length, 0);
      await workspace.close();
    });

    await test("projects.list orders by updatedAt desc with counts", async () => {
      const directory = path.join(base, "list-order");
      const workspace = await openCreationWorkspace({ directory });
      const first = await workspace.transact({ type: "project.create", title: "测试项目甲", setup: minimalSetup });
      const second = await workspace.transact({ type: "project.create", title: "测试项目乙", setup: minimalSetup });
      const third = await workspace.transact({ type: "project.create", title: "测试项目丙", setup: minimalSetup });
      await workspace.close();
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run("2026-01-01T00:00:00.000Z", first.projectId);
      raw.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run("2026-03-01T00:00:00.000Z", second.projectId);
      raw.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run("2026-02-01T00:00:00.000Z", third.projectId);
      raw.close();
      const reopened = await openCreationWorkspace({ directory });
      const list = (await reopened.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.deepEqual(list.map((item) => item.title), ["测试项目乙", "测试项目丙", "测试项目甲"]);
      for (const summary of list) {
        assert.equal(summary.chapterCount, 1);
        assert.equal(summary.sceneCount, 1);
        assert.equal(typeof summary.id, "string");
        assert.equal(typeof summary.updatedAt, "string");
        assert.equal(typeof summary.revision, "number");
        assert.equal(typeof summary.setup.template, "string");
      }
      await reopened.close();
    });

    await test("corrupt setup_json surfaces typed integrity errors", async () => {
      const directory = path.join(base, "corrupt-setup");
      const workspace = await openCreationWorkspace({ directory });
      await workspace.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
      await workspace.close();
      const raw = new Database(path.join(directory, "workspace.sqlite"));
      raw.prepare("UPDATE projects SET setup_json = ?").run("{broken");
      raw.close();
      const reopened = await openCreationWorkspace({ directory });
      await expectWorkspaceError("integrity", () => reopened.read({ kind: "projects.list" }));
      const report = await reopened.check();
      assert.equal(report.ok, false);
      assert.equal(report.schema.issues.some((issue) => issue.code === "project-setup-json"), true);
      await reopened.close();
    });

    await test("coordinator lazily opens and shares the first concurrent open", async () => {
      const directory = path.join(base, "coordinator-lazy");
      const coordinator = createCreationCoordinator({ resolveDirectory: () => directory });
      assert.equal(existsSync(directory), false);
      const [first, second, third] = await Promise.all([
        coordinator.getWorkspace(),
        coordinator.getWorkspace(),
        coordinator.getWorkspace()
      ]);
      assert.equal(first, second);
      assert.equal(second, third);
      assert.equal(existsSync(directory), true);
      await coordinator.close();
    });

    await test("coordinator rejects get during maintenance and reopens after", async () => {
      const directory = path.join(base, "coordinator-busy");
      const coordinator = createCreationCoordinator({ resolveDirectory: () => directory });
      const workspace = await coordinator.getWorkspace();
      let release: () => void = () => undefined;
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      const maintenance = coordinator.withWorkspaceClosed(async () => {
        await gate;
      });
      await expectWorkspaceError("closed", () => coordinator.getWorkspace());
      release();
      await maintenance;
      const reopened = await coordinator.getWorkspace();
      assert.notEqual(reopened, workspace);
      const created = await reopened.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
      assert.equal(typeof created.projectId, "string");
      await coordinator.close();
    });

    await test("coordinator dynamic root switch flushes and reopens in the new root", async () => {
      const baseDir = path.join(base, "coordinator-root");
      let root = path.join(baseDir, "root-a");
      const coordinator = createCreationCoordinator({ resolveDirectory: () => root });
      const workspaceA = await coordinator.getWorkspace();
      await workspaceA.transact({ type: "project.create", title: "测试项目", setup: minimalSetup });
      await coordinator.withWorkspaceClosed(async () => undefined);
      root = path.join(baseDir, "root-b");
      const workspaceB = await coordinator.getWorkspace();
      const listB = (await workspaceB.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(listB.length, 0);
      await coordinator.withWorkspaceClosed(async () => undefined);
      root = path.join(baseDir, "root-a");
      const workspaceA2 = await coordinator.getWorkspace();
      const listA = (await workspaceA2.read({ kind: "projects.list" })) as CreationProjectSummary[];
      assert.equal(listA.length, 1);
      assert.equal(listA[0].title, "测试项目");
      await coordinator.close();
      await coordinator.close();
      const afterClose = await coordinator.getWorkspace();
      assert.equal(typeof (await afterClose.read({ kind: "projects.list" })).length, "number");
      await coordinator.close();
    });

    await test("coordinator clears cached open failure and retries lazily", async () => {
      const baseDir = path.join(base, "coordinator-retry");
      await mkdir(baseDir, { recursive: true });
      const blockedRoot = path.join(baseDir, "blocked");
      await writeFile(blockedRoot, "file fixture", "utf8");
      let root = blockedRoot;
      const coordinator = createCreationCoordinator({ resolveDirectory: () => root });
      await expectWorkspaceError("integrity", () => coordinator.getWorkspace());
      root = path.join(baseDir, "open");
      const workspace = await coordinator.getWorkspace();
      assert.equal(existsSync(root), true);
      await coordinator.close();
    });
  } finally {
    await rm(base, { recursive: true, force: true });
  }

  if (failures.length > 0) {
    process.stderr.write(`${failures.join("\n\n")}\n`);
    process.exitCode = 1;
    return;
  }
  process.stdout.write(`${JSON.stringify({ allPass: true, tests: passed })}\n`);
}

run().catch((error) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
