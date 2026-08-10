import { mkdir } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type CreationCommand,
  type CreationDocument,
  type CreationIntegrityReport,
  type CreationReadQuery,
  type CreationReadResult,
  type ListProjectsQuery,
  type CreationProjectSetup,
  type CreationProjectSummary,
  type CreationProjectTemplate,
  type CreationProjectNavigation,
  type CreationProjectTree,
  type CreationTransactionResult,
  type ReadProjectNavigationQuery,
  type ReadProjectTreeQuery,
  type ReadSceneBodyQuery,
  type SceneBodyView,
  type CreateProjectCommand,
  type CreateProjectResult,
  type UpdateSceneBodyCommand,
  type UpdateSceneBodyResult,
  type CreationWatchScope,
  type CreationWorkspace,
  type CreationWorkspaceEvent,
  type CreationWorkspaceListener,
  type OpenCreationWorkspaceOptions
} from "./types";

const SCHEMA_VERSION = 2;

const TARGET_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const SCENE_TEXT_BLOCKS = new Set(["paragraph", "quoteLetter", "centeredText", "authorNote"]);
const SCENE_MARKS = new Set(["bold", "italic"]);

const REQUIRED_TABLES = [
  "workspace_meta",
  "projects",
  "chapters",
  "scenes",
  "cards",
  "card_relations",
  "resources",
  "snapshots",
  "change_log",
  "scenes_fts"
] as const;

const REQUIRED_INDEXES = [
  "idx_chapters_project_order",
  "idx_scenes_chapter_order",
  "idx_cards_project_kind",
  "idx_card_relations_from",
  "idx_card_relations_to",
  "idx_resources_project",
  "idx_snapshots_project_created"
] as const;

function createSection(issues: Array<{ code: string; message: string }>) {
  return { ok: issues.length === 0, issues };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isValidSceneDocument(value: unknown): value is CreationDocument {
  if (!isRecord(value) || value.type !== "doc" || !Array.isArray(value.content)) return false;
  for (const block of value.content) {
    if (!isRecord(block) || typeof block.type !== "string") return false;
    if (block.type === "sceneBreak") {
      if (block.content !== undefined) return false;
      continue;
    }
    if (!SCENE_TEXT_BLOCKS.has(block.type)) return false;
    if (block.content === undefined) continue;
    if (!Array.isArray(block.content)) return false;
    for (const inline of block.content) {
      if (!isRecord(inline) || inline.type !== "text" || typeof inline.text !== "string") return false;
      if (inline.marks === undefined) continue;
      if (!Array.isArray(inline.marks)) return false;
      for (const mark of inline.marks) {
        if (!isRecord(mark) || typeof mark.type !== "string" || !SCENE_MARKS.has(mark.type)) return false;
      }
    }
  }
  return true;
}

function initializeSchema(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;

    CREATE TABLE IF NOT EXISTS workspace_meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      setup_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS chapters (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_chapters_project_order ON chapters(project_id, sort_order);

    CREATE TABLE IF NOT EXISTS scenes (
      id TEXT PRIMARY KEY,
      chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_scenes_chapter_order ON scenes(chapter_id, sort_order);

    CREATE TABLE IF NOT EXISTS cards (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      title TEXT NOT NULL,
      content_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_cards_project_kind ON cards(project_id, kind);

    CREATE TABLE IF NOT EXISTS card_relations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relation_type TEXT NOT NULL,
      created_at TEXT NOT NULL,
      UNIQUE(from_card_id, to_card_id, relation_type)
    );
    CREATE INDEX IF NOT EXISTS idx_card_relations_from ON card_relations(from_card_id);
    CREATE INDEX IF NOT EXISTS idx_card_relations_to ON card_relations(to_card_id);

    CREATE TABLE IF NOT EXISTS resources (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL,
      sha256 TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);

    CREATE TABLE IF NOT EXISTS snapshots (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      subject_type TEXT NOT NULL,
      subject_id TEXT NOT NULL,
      payload_json TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_snapshots_project_created ON snapshots(project_id, created_at);

    CREATE TABLE IF NOT EXISTS change_log (
      sequence INTEGER PRIMARY KEY AUTOINCREMENT,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      command_type TEXT NOT NULL,
      changes_json TEXT NOT NULL,
      committed_at TEXT NOT NULL
    );

    CREATE VIRTUAL TABLE IF NOT EXISTS scenes_fts USING fts5(
      title,
      body_json,
      content='scenes',
      content_rowid='rowid'
    );

    CREATE TRIGGER IF NOT EXISTS scenes_ai AFTER INSERT ON scenes BEGIN
      INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
    END;
    CREATE TRIGGER IF NOT EXISTS scenes_ad AFTER DELETE ON scenes BEGIN
      INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
    END;
    CREATE TRIGGER IF NOT EXISTS scenes_au AFTER UPDATE ON scenes BEGIN
      INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
      INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
    END;

    PRAGMA user_version = ${SCHEMA_VERSION};
    COMMIT;
  `);
}

function migrateSchemaV1ToV2(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    ALTER TABLE projects ADD COLUMN setup_json TEXT NOT NULL DEFAULT '{}';
    PRAGMA user_version = 2;
    COMMIT;
  `);
}

function defaultProjectSetup(): CreationProjectSetup {
  return { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["起草"] };
}

function setupString(value: unknown, maxLength: number, label: string): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string") throw new CreationWorkspaceError("invalid-input", `${label}必须为文本。`);
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  if (trimmed.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}不能超过 ${maxLength} 个字符。`);
  }
  return trimmed;
}

function setupPositiveInteger(value: unknown, label: string): number | undefined {
  if (value === undefined) return undefined;
  if (!Number.isInteger(value) || Number(value) < 1) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为正整数。`);
  }
  return value as number;
}

function setupTargetDate(value: unknown): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || !TARGET_DATE_PATTERN.test(value)) {
    throw new CreationWorkspaceError("invalid-input", "目标日期必须为 YYYY-MM-DD 格式。");
  }
  return value;
}

function setupWeeklyUpdateDays(value: unknown): number[] {
  if (!Array.isArray(value)) throw new CreationWorkspaceError("invalid-input", "每周更新日必须为数组。");
  const days: number[] = [];
  for (const item of value) {
    if (!Number.isInteger(item) || Number(item) < 0 || Number(item) > 6) {
      throw new CreationWorkspaceError("invalid-input", "每周更新日必须为 0 至 6 的整数。");
    }
    const day = item as number;
    if (!days.includes(day)) days.push(day);
  }
  return days.sort((left, right) => left - right);
}

function setupChapterWorkflow(value: unknown): string[] {
  if (!Array.isArray(value) || value.length === 0) {
    throw new CreationWorkspaceError("invalid-input", "章节工作流必须为非空数组。");
  }
  const steps: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") throw new CreationWorkspaceError("invalid-input", "章节工作流步骤必须为文本。");
    const step = item.trim();
    if (!step) throw new CreationWorkspaceError("invalid-input", "章节工作流步骤不能为空。");
    steps.push(step);
  }
  return steps;
}

function normalizeProjectSetup(value: unknown): CreationProjectSetup {
  if (value === undefined) return defaultProjectSetup();
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CreationWorkspaceError("invalid-input", "创作项目设置无效。");
  }
  const record = value as Record<string, unknown>;
  const template = record.template as CreationProjectTemplate;
  if (template !== "blank" && template !== "long-form" && template !== "serial") {
    throw new CreationWorkspaceError("invalid-input", "创作项目模板无效。");
  }
  const setup: CreationProjectSetup = {
    template,
    weeklyUpdateDays: setupWeeklyUpdateDays(record.weeklyUpdateDays),
    chapterWorkflow: setupChapterWorkflow(record.chapterWorkflow)
  };
  const description = setupString(record.description, 5000, "简介");
  if (description !== undefined) setup.description = description;
  const genre = setupString(record.genre, 200, "题材");
  if (genre !== undefined) setup.genre = genre;
  const totalWordGoal = setupPositiveInteger(record.totalWordGoal, "总字数目标");
  if (totalWordGoal !== undefined) setup.totalWordGoal = totalWordGoal;
  const dailyWordGoal = setupPositiveInteger(record.dailyWordGoal, "每日字数目标");
  if (dailyWordGoal !== undefined) setup.dailyWordGoal = dailyWordGoal;
  const weeklyWordGoal = setupPositiveInteger(record.weeklyWordGoal, "每周字数目标");
  if (weeklyWordGoal !== undefined) setup.weeklyWordGoal = weeklyWordGoal;
  const targetDate = setupTargetDate(record.targetDate);
  if (targetDate !== undefined) setup.targetDate = targetDate;
  return setup;
}

function parseStoredSetup(json: string): CreationProjectSetup {
  if (!json || json.trim() === "{}") return defaultProjectSetup();
  let value: unknown;
  try {
    value = JSON.parse(json);
  } catch {
    throw new CreationWorkspaceError("integrity", "创作项目设置数据损坏。");
  }
  try {
    return normalizeProjectSetup(value);
  } catch (error) {
    if (error instanceof CreationWorkspaceError && error.code === "invalid-input") {
      throw new CreationWorkspaceError("integrity", "创作项目设置数据损坏。");
    }
    throw error;
  }
}

class SqliteCreationWorkspace implements CreationWorkspace {
  private closed = false;
  private readonly watchers = new Map<symbol, { scope: CreationWatchScope; listener: CreationWorkspaceListener }>();

  constructor(private readonly database: Database) {}

  async read(query: ReadProjectTreeQuery): Promise<CreationProjectTree | null>;
  async read(query: ReadProjectNavigationQuery): Promise<CreationProjectNavigation | null>;
  async read(query: ReadSceneBodyQuery): Promise<SceneBodyView | null>;
  async read(query: ListProjectsQuery): Promise<CreationProjectSummary[]>;
  async read(query: CreationReadQuery): Promise<CreationReadResult> {
    this.assertOpen();
    const runtimeQuery = query as unknown as { kind?: unknown; projectId?: unknown; sceneId?: unknown } | null;
    if (
      runtimeQuery === null ||
      typeof runtimeQuery.kind !== "string"
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    if (runtimeQuery.kind === "projects.list") {
      try {
        return this.listProjects();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作工作区项目列表。");
      }
    }
    if (runtimeQuery.kind === "scene.body") {
      if (typeof runtimeQuery.sceneId !== "string" || !runtimeQuery.sceneId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "场景读取请求无效。");
      }
      try {
        return this.readSceneBody(runtimeQuery.sceneId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取场景正文。");
      }
    }
    if (
      (runtimeQuery.kind !== "project.tree" && runtimeQuery.kind !== "project.navigation") ||
      typeof runtimeQuery.projectId !== "string" ||
      !runtimeQuery.projectId.trim()
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    try {
      if (runtimeQuery.kind === "project.navigation") {
        return this.readProjectNavigation(runtimeQuery.projectId);
      }
      return this.readProjectTree(runtimeQuery.projectId);
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法读取创作工作区数据。");
    }
  }

  private readProjectTree(projectId: string): CreationProjectTree | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = this.database
      .prepare(
        "SELECT id, project_id, title, sort_order, created_at, updated_at, revision FROM chapters WHERE project_id = ? ORDER BY sort_order, id"
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      title: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const sceneStatement = this.database.prepare(
      "SELECT id, chapter_id, title, sort_order, body_json, created_at, updated_at, revision FROM scenes WHERE chapter_id = ? ORDER BY sort_order, id"
    );

    return {
      project: {
        id: project.id,
        title: project.title,
        setup: parseStoredSetup(project.setup_json),
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      chapters: chapters.map((chapter) => ({
        id: chapter.id,
        projectId: chapter.project_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes: (sceneStatement.all(chapter.id) as Array<{
          id: string;
          chapter_id: string;
          title: string;
          sort_order: number;
          body_json: string;
          created_at: string;
          updated_at: string;
          revision: number;
        }>).map((scene) => ({
          id: scene.id,
          chapterId: scene.chapter_id,
          title: scene.title,
          sortOrder: scene.sort_order,
          body: JSON.parse(scene.body_json) as CreationProjectTree["chapters"][number]["scenes"][number]["body"],
          createdAt: scene.created_at,
          updatedAt: scene.updated_at,
          revision: scene.revision
        }))
      }))
    };
  }

  private readProjectNavigation(projectId: string): CreationProjectNavigation | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = this.database
      .prepare(
        "SELECT id, project_id, title, sort_order, created_at, updated_at, revision FROM chapters WHERE project_id = ? ORDER BY sort_order, id"
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      title: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const sceneStatement = this.database.prepare(
      "SELECT id, chapter_id, title, sort_order, created_at, updated_at, revision FROM scenes WHERE chapter_id = ? ORDER BY sort_order, id"
    );

    return {
      project: {
        id: project.id,
        title: project.title,
        setup: parseStoredSetup(project.setup_json),
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      chapters: chapters.map((chapter) => ({
        id: chapter.id,
        projectId: chapter.project_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes: (sceneStatement.all(chapter.id) as Array<{
          id: string;
          chapter_id: string;
          title: string;
          sort_order: number;
          created_at: string;
          updated_at: string;
          revision: number;
        }>).map((scene) => ({
          id: scene.id,
          chapterId: scene.chapter_id,
          title: scene.title,
          sortOrder: scene.sort_order,
          createdAt: scene.created_at,
          updatedAt: scene.updated_at,
          revision: scene.revision
        }))
      }))
    };
  }

  private readSceneBody(sceneId: string): SceneBodyView | null {
    const scene = this.database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.body_json, s.updated_at, s.revision, c.project_id
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.id = ?`
      )
      .get(sceneId) as
      | {
          id: string;
          chapter_id: string;
          title: string;
          body_json: string;
          updated_at: string;
          revision: number;
          project_id: string;
        }
      | undefined;
    if (!scene) return null;
    let body: CreationDocument;
    try {
      body = JSON.parse(scene.body_json) as CreationDocument;
    } catch {
      throw new CreationWorkspaceError("integrity", "场景正文数据损坏。");
    }
    return {
      sceneId: scene.id,
      projectId: scene.project_id,
      chapterId: scene.chapter_id,
      title: scene.title,
      body,
      revision: scene.revision,
      updatedAt: scene.updated_at
    };
  }

  private listProjects(): CreationProjectSummary[] {
    const rows = this.database
      .prepare(
        `SELECT p.id, p.title, p.setup_json, p.updated_at, p.revision,
          (SELECT count(*) FROM chapters c WHERE c.project_id = p.id) AS chapter_count,
          (SELECT count(*) FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.project_id = p.id) AS scene_count
         FROM projects p
         ORDER BY p.updated_at DESC, p.id DESC`
      )
      .all() as Array<{
      id: string;
      title: string;
      setup_json: string;
      updated_at: string;
      revision: number;
      chapter_count: number;
      scene_count: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      title: row.title,
      setup: parseStoredSetup(row.setup_json),
      updatedAt: row.updated_at,
      revision: row.revision,
      chapterCount: row.chapter_count,
      sceneCount: row.scene_count
    }));
  }

  async transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  async transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  async transact(command: CreationCommand): Promise<CreationTransactionResult> {
    this.assertOpen();
    const runtimeType = (command as unknown as { type?: unknown }).type;
    if (runtimeType !== "project.create" && runtimeType !== "scene.updateBody") {
      throw new CreationWorkspaceError("invalid-input", "创作工作区命令无效。");
    }
    if (command.type === "scene.updateBody") {
      return this.updateSceneBody(command);
    }
    const runtimeTitle = (command as unknown as { title?: unknown }).title;
    if (typeof runtimeTitle !== "string") {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为文本。");
    }
    const title = runtimeTitle.trim();
    if (!title || title.length > 200) {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为 1 至 200 个字符。");
    }
    const setup = normalizeProjectSetup((command as unknown as { setup?: unknown }).setup);
    const setupJson = JSON.stringify(setup);

    const projectId = `project-${randomUUID()}`;
    const chapterId = `chapter-${randomUUID()}`;
    const sceneId = `scene-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      this.database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run(projectId, title, setupJson, timestamp, timestamp);
      this.database
        .prepare(
          "INSERT INTO chapters(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
        )
        .run(chapterId, projectId, "第一章", 0, timestamp, timestamp);
      this.database
        .prepare(
          "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(sceneId, chapterId, "默认场景", 0, '{"type":"doc","content":[]}', timestamp, timestamp);
      const changes = JSON.stringify([
        { entity: "project", id: projectId, action: "created", revision: 1 },
        { entity: "chapter", id: chapterId, action: "created", revision: 1 },
        { entity: "scene", id: sceneId, action: "created", revision: 1 }
      ]);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, command.type, changes, timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId,
        chapterId,
        sceneId
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: command.type,
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      if (typeof error === "object" && error !== null && "code" in error && String(error.code).startsWith("SQLITE_CONSTRAINT")) {
        throw new CreationWorkspaceError("conflict", "无法创建作品，稳定标识发生冲突。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交创作工作区事务。");
    }
  }

  private updateSceneBody(command: UpdateSceneBodyCommand): UpdateSceneBodyResult {
    const runtimeCommand = command as unknown as {
      sceneId?: unknown;
      baseRevision?: unknown;
      body?: unknown;
    };
    if (
      typeof runtimeCommand.sceneId !== "string" ||
      !runtimeCommand.sceneId.trim() ||
      !Number.isInteger(runtimeCommand.baseRevision) ||
      Number(runtimeCommand.baseRevision) < 1
    ) {
      throw new CreationWorkspaceError("invalid-input", "场景写入请求无效。");
    }
    if (!isValidSceneDocument(runtimeCommand.body)) {
      throw new CreationWorkspaceError("invalid-input", "场景正文必须是有效的结构化文档。");
    }
    let bodyJson: string;
    try {
      bodyJson = JSON.stringify(runtimeCommand.body);
    } catch {
      throw new CreationWorkspaceError("invalid-input", "场景正文无法序列化。");
    }
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const current = this.database
        .prepare(
          "SELECT s.revision, s.body_json, c.project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ?"
        )
        .get(command.sceneId) as { revision: number; body_json: string; project_id: string } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (current.revision !== command.baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再保存。");
      }
      const revision = current.revision + 1;
      // 每场景只保留一条 scene-autosave 快照：先删旧、再写提交前的旧正文与旧 revision。
      this.database
        .prepare("DELETE FROM snapshots WHERE subject_type = 'scene-autosave' AND subject_id = ?")
        .run(command.sceneId);
      this.database
        .prepare(
          "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        )
        .run(
          `snapshot-${randomUUID()}`,
          current.project_id,
          "scene-autosave",
          command.sceneId,
          JSON.stringify({ body: JSON.parse(current.body_json), revision: current.revision }),
          timestamp
        );
      this.database
        .prepare("UPDATE scenes SET body_json = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(bodyJson, timestamp, revision, command.sceneId);
      this.database
        .prepare("UPDATE projects SET updated_at = ? WHERE id = ?")
        .run(timestamp, current.project_id);
      const changes = JSON.stringify([
        { entity: "scene", id: command.sceneId, action: "updated", revision }
      ]);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(current.project_id, command.type, changes, timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId: current.project_id,
        sceneId: command.sceneId,
        revision,
        updatedAt: timestamp
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: current.project_id,
        commandType: command.type,
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法提交场景正文事务。");
    }
  }

  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void {
    this.assertOpen();
    const runtimeScope = scope as unknown as { projectId?: unknown } | null;
    if (
      runtimeScope === null ||
      typeof runtimeScope !== "object" ||
      typeof listener !== "function" ||
      (runtimeScope.projectId !== undefined &&
        (typeof runtimeScope.projectId !== "string" || !runtimeScope.projectId.trim()))
    ) {
      throw new CreationWorkspaceError("invalid-input", "创作工作区订阅请求无效。");
    }
    const token = Symbol("creation-workspace-watcher");
    this.watchers.set(token, {
      scope: runtimeScope.projectId === undefined ? {} : { projectId: runtimeScope.projectId as string },
      listener
    });
    return () => {
      this.watchers.delete(token);
    };
  }

  async check(): Promise<CreationIntegrityReport> {
    this.assertOpen();
    try {
      return this.runIntegrityCheck();
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法检查创作工作区完整性。");
    }
  }

  private runIntegrityCheck(): CreationIntegrityReport {
    const checkedAt = new Date().toISOString();
    const schemaIssues: Array<{ code: string; message: string }> = [];
    const relationIssues: Array<{ code: string; message: string }> = [];
    const resourceIssues: Array<{ code: string; message: string }> = [];
    const indexIssues: Array<{ code: string; message: string }> = [];
    const snapshotIssues: Array<{ code: string; message: string }> = [];

    const schemaVersion = Number(this.database.pragma("user_version", { simple: true }));
    if (schemaVersion !== SCHEMA_VERSION) {
      schemaIssues.push({ code: "schema-version", message: `预期 schema ${SCHEMA_VERSION}，实际为 ${schemaVersion}。` });
    }
    const journalMode = String(this.database.pragma("journal_mode", { simple: true }));
    if (journalMode !== "wal") {
      schemaIssues.push({ code: "journal-mode", message: `预期 WAL，实际为 ${journalMode}。` });
    }
    const foreignKeysEnabled = Number(this.database.pragma("foreign_keys", { simple: true })) === 1;
    if (!foreignKeysEnabled) {
      schemaIssues.push({ code: "foreign-keys-disabled", message: "SQLite 外键约束未启用。" });
    }

    const objects = this.database
      .prepare("SELECT name, type FROM sqlite_master WHERE type IN ('table', 'index')")
      .all() as Array<{ name: string; type: "table" | "index" }>;
    const tables = new Set(objects.filter((item) => item.type === "table").map((item) => item.name));
    const indexes = new Set(objects.filter((item) => item.type === "index").map((item) => item.name));
    for (const table of REQUIRED_TABLES) {
      if (!tables.has(table)) schemaIssues.push({ code: "schema-table-missing", message: `缺少数据表 ${table}。` });
    }
    for (const index of REQUIRED_INDEXES) {
      if (!indexes.has(index)) indexIssues.push({ code: "index-missing", message: `缺少索引 ${index}。` });
    }

    const integrity = String(this.database.pragma("integrity_check", { simple: true }));
    if (integrity !== "ok") schemaIssues.push({ code: "sqlite-integrity", message: "SQLite 完整性检查失败。" });
    const foreignKeyRows = this.database.pragma("foreign_key_check") as unknown[];
    if (foreignKeyRows.length > 0) {
      relationIssues.push({ code: "foreign-key", message: `发现 ${foreignKeyRows.length} 个关系完整性问题。` });
    }

    if (tables.has("resources") && tables.has("projects")) {
      const missingResources = this.database
        .prepare("SELECT count(*) AS count FROM resources r LEFT JOIN projects p ON p.id = r.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingResources.count > 0) {
        resourceIssues.push({ code: "resource-project", message: `发现 ${missingResources.count} 个失去项目归属的资源。` });
      }
    }

    if (tables.has("snapshots") && tables.has("projects")) {
      const missingSnapshots = this.database
        .prepare("SELECT count(*) AS count FROM snapshots s LEFT JOIN projects p ON p.id = s.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingSnapshots.count > 0) {
        snapshotIssues.push({ code: "snapshot-project", message: `发现 ${missingSnapshots.count} 个失去项目归属的快照。` });
      }
    }

    if (tables.has("projects")) {
      const setupRows = this.database.prepare("SELECT setup_json FROM projects").all() as Array<{ setup_json: string }>;
      for (const row of setupRows) {
        try {
          parseStoredSetup(row.setup_json);
        } catch {
          schemaIssues.push({ code: "project-setup-json", message: "发现损坏的创作项目设置数据。" });
          break;
        }
      }
    }

    const schema = createSection(schemaIssues);
    const relations = createSection(relationIssues);
    const resources = createSection(resourceIssues);
    const indexesSection = createSection(indexIssues);
    const snapshots = createSection(snapshotIssues);
    const count = (table: string): number => {
      if (!tables.has(table)) return 0;
      return (this.database.prepare(`SELECT count(*) AS count FROM ${table}`).get() as { count: number }).count;
    };
    const latestSequence = tables.has("change_log")
      ? (
          this.database.prepare("SELECT coalesce(max(sequence), 0) AS sequence FROM change_log").get() as {
            sequence: number;
          }
        ).sequence
      : 0;
    return {
      ok: schema.ok && relations.ok && resources.ok && indexesSection.ok && snapshots.ok,
      schemaVersion,
      latestSequence,
      checkedAt,
      counts: {
        projects: count("projects"),
        chapters: count("chapters"),
        scenes: count("scenes"),
        cards: count("cards"),
        relations: count("card_relations"),
        resources: count("resources"),
        snapshots: count("snapshots")
      },
      schema,
      relations,
      resources,
      indexes: indexesSection,
      snapshots
    };
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.watchers.clear();
    let checkpointFailed = false;
    try {
      this.database.pragma("wal_checkpoint(TRUNCATE)");
    } catch {
      checkpointFailed = true;
    }
    try {
      this.database.close();
      this.closed = true;
    } catch {
      throw new CreationWorkspaceError("integrity", "无法关闭创作工作区。");
    }
    if (checkpointFailed) {
      throw new CreationWorkspaceError("integrity", "创作工作区已关闭，但 WAL 检查点未完成。");
    }
  }

  private assertOpen(): void {
    if (this.closed) throw new CreationWorkspaceError("closed", "创作工作区已关闭。");
  }

  private emitCommitted(event: CreationWorkspaceEvent): void {
    for (const { scope, listener } of this.watchers.values()) {
      if (scope.projectId !== undefined && scope.projectId !== event.projectId) continue;
      try {
        listener(event);
      } catch {
        // A subscriber cannot roll back or break an already committed transaction.
      }
    }
  }
}

export async function openCreationWorkspace(options: OpenCreationWorkspaceOptions): Promise<CreationWorkspace> {
  if (!options || typeof options.directory !== "string" || !options.directory.trim()) {
    throw new CreationWorkspaceError("invalid-input", "创作工作区目录不能为空。");
  }
  let database: Database | undefined;
  try {
    await mkdir(options.directory, { recursive: true });
    database = new Database(path.join(options.directory, "workspace.sqlite"));
    database.pragma("journal_mode = WAL");
    database.pragma("foreign_keys = ON");
    database.pragma("synchronous = FULL");
    const existingVersion = Number(database.pragma("user_version", { simple: true }));
    if (existingVersion === 0) initializeSchema(database);
    else if (existingVersion === 1) migrateSchemaV1ToV2(database);
    else if (existingVersion !== SCHEMA_VERSION) {
      throw new CreationWorkspaceError("integrity", `不支持的创作工作区 schema 版本：${existingVersion}。`);
    }
    return new SqliteCreationWorkspace(database);
  } catch (error) {
    try {
      database?.close();
    } catch {
      // Preserve the stable workspace error instead of exposing a close failure.
    }
    if (error instanceof CreationWorkspaceError) throw error;
    throw new CreationWorkspaceError("integrity", "无法打开创作工作区。");
  }
}

export * from "./types";
