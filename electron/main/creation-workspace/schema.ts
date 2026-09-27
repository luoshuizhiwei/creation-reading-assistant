/**
 * SQLite schema 定义与 v1→v12 迁移链（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：建表建索引、内置卡片数据 seed、逐版本迁移、幂等 ensure*Schema、
 * 全局卡片资源删除入队与 GC 队列排空，以及健康检查所需的必需表/索引清单。
 * 全部只依赖传入的 Database 句柄与文件系统，行为逐字保持。
 */
import { rm } from "node:fs/promises";
import path from "node:path";
import type Database from "better-sqlite3";
import type { ScenePlanning } from "./types";
import { countSceneBodyStats } from "./scene-stats";

export const REQUIRED_TABLES = [
  "workspace_meta",
  "projects",
  "volumes",
  "chapters",
  "scenes",
  "cards",
  "card_types",
  "relation_types",
  "card_relations",
  "project_card_links",
  "resources",
  "snapshots",
  "change_log",
  "writing_sessions",
  "inbox_items",
  "annotations",
  "scenes_fts"
] as const;

export const REQUIRED_INDEXES = [
  "idx_volumes_project_order",
  "idx_chapters_volume_order",
  "idx_chapters_project_order",
  "idx_scenes_chapter_order",
  "idx_cards_project_kind",
  "idx_card_types_project",
  "idx_relation_types_project",
  "idx_card_relations_from",
  "idx_card_relations_to",
  "idx_project_card_links_card",
  "idx_resources_project",
  "idx_snapshots_project_created",
  "idx_writing_sessions_project_started",
  "idx_inbox_items_updated",
  "idx_annotations_scene",
  "idx_annotations_project",
  "idx_resources_card"
] as const;

export function initializeSchema(database: Database): void {
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

    CREATE TABLE IF NOT EXISTS volumes (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_volumes_project_order ON volumes(project_id, sort_order);

    CREATE TABLE IF NOT EXISTS chapters (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      volume_id TEXT REFERENCES volumes(id) ON DELETE SET NULL,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      status TEXT NOT NULL DEFAULT '',
      numbering_kind TEXT NOT NULL DEFAULT 'auto',
      custom_number TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_chapters_project_order ON chapters(project_id, sort_order);
    CREATE INDEX IF NOT EXISTS idx_chapters_volume_order ON chapters(volume_id, sort_order);

    CREATE TABLE IF NOT EXISTS scenes (
      id TEXT PRIMARY KEY,
      chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
      han_count INTEGER NOT NULL DEFAULT 0,
      punct_count INTEGER NOT NULL DEFAULT 0,
      non_ws_count INTEGER NOT NULL DEFAULT 0,
      planning_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_scenes_chapter_order ON scenes(chapter_id, sort_order);

    CREATE TABLE IF NOT EXISTS card_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      name TEXT NOT NULL,
      fields_json TEXT NOT NULL DEFAULT '[]',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_card_types_project ON card_types(project_id, sort_order);

    CREATE TABLE IF NOT EXISTS relation_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      forward_name TEXT NOT NULL,
      reverse_name TEXT NOT NULL,
      from_kinds_json TEXT NOT NULL DEFAULT '[]',
      to_kinds_json TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_relation_types_project ON relation_types(project_id);

    CREATE TABLE IF NOT EXISTS cards (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      title TEXT NOT NULL,
      aliases_json TEXT NOT NULL DEFAULT '[]',
      fields_json TEXT NOT NULL DEFAULT '{}',
      tags_json TEXT NOT NULL DEFAULT '[]',
      content_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_cards_project_kind ON cards(project_id, kind);

    CREATE TABLE IF NOT EXISTS card_relations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relation_type TEXT NOT NULL,
      note TEXT,
      created_at TEXT NOT NULL,
      UNIQUE(from_card_id, to_card_id, relation_type)
    );
    CREATE INDEX IF NOT EXISTS idx_card_relations_from ON card_relations(from_card_id);
    CREATE INDEX IF NOT EXISTS idx_card_relations_to ON card_relations(to_card_id);

    CREATE TABLE IF NOT EXISTS resources (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      card_id TEXT REFERENCES cards(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL,
      sha256 TEXT NOT NULL,
      size INTEGER NOT NULL DEFAULT 0,
      original_name TEXT,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);
    CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id);

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

    CREATE TABLE IF NOT EXISTS writing_sessions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT,
      started_at TEXT NOT NULL,
      active_seconds INTEGER NOT NULL DEFAULT 0,
      net_chars INTEGER NOT NULL DEFAULT 0,
      reported_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_writing_sessions_project_started ON writing_sessions(project_id, started_at);

    CREATE TABLE IF NOT EXISTS inbox_items (
      id TEXT PRIMARY KEY,
      legacy_id TEXT,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'note',
      status TEXT NOT NULL DEFAULT 'inbox',
      tags_json TEXT NOT NULL DEFAULT '[]',
      platform_tags_json TEXT NOT NULL DEFAULT '[]',
      source_json TEXT,
      variants_json TEXT NOT NULL DEFAULT '[]',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_inbox_items_updated ON inbox_items(updated_at);

    CREATE TABLE IF NOT EXISTS annotations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      card_id TEXT REFERENCES cards(id) ON DELETE SET NULL,
      anchor_json TEXT NOT NULL,
      note TEXT,
      status TEXT NOT NULL DEFAULT 'open',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_annotations_scene ON annotations(scene_id);
    CREATE INDEX IF NOT EXISTS idx_annotations_project ON annotations(project_id);

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

    PRAGMA user_version = 9;
    COMMIT;
  `);
  seedBuiltinCardData(database);
}

export function seedBuiltinCardData(database: Database): void {
  const seedTime = "2026-01-01T00:00:00.000Z";
  const insertType = database.prepare(
    "INSERT OR IGNORE INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
  );
  const builtinTypes: Array<[string, string, string]> = [
    ["character", "角色", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["location", "地点", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["organization", "组织", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["item", "物品", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["worldRule", "世界规则", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["plotEvent", "情节事件", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    [
      "foreshadow",
      "伏笔线索",
      // D-C3 伏笔生命周期 v1：status 由「未标记 = 未回收」兜底，无需数据迁移。
      JSON.stringify([
        { key: "note", label: "伏笔内容", kind: "multiline" },
        { key: "status", label: "状态", kind: "select", options: ["未回收", "已回收"], defaultValue: "未回收" },
        { key: "plantedIn", label: "埋设位置", kind: "text" },
        { key: "resolution", label: "回收说明", kind: "multiline" }
      ])
    ],
    ["reference", "资料", '[{"key":"note","label":"备注","kind":"multiline"}]']
  ];
  builtinTypes.forEach(([kind, name, fieldsJson], index) => {
    insertType.run(
      `card-type-${kind}`,
      null,
      kind,
      name,
      fieldsJson,
      index,
      seedTime,
      seedTime
    );
  });
  const insertRelation = database.prepare(
    "INSERT OR IGNORE INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
  );
  const builtinRelations: Array<[string, string, string, string, string[], string[]]> = [
    ["character-character", "knows", "认识", "认识", ["character"], ["character"]],
    ["character-location", "appearsAt", "登场于", "登场角色", ["character"], ["location"]],
    ["character-organization", "belongsTo", "隶属于", "成员", ["character"], ["organization"]],
    ["item-character", "ownedBy", "持有", "持有者", ["item"], ["character"]]
  ];
  builtinRelations.forEach(([suffix, name, forward, reverse, fromKinds, toKinds]) => {
    insertRelation.run(
      `relation-type-${suffix}`,
      null,
      name,
      forward,
      reverse,
      JSON.stringify(fromKinds),
      JSON.stringify(toKinds),
      seedTime,
      seedTime
    );
  });
}

export function migrateSchemaV1ToV2(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    ALTER TABLE projects ADD COLUMN setup_json TEXT NOT NULL DEFAULT '{}';
    PRAGMA user_version = 2;
    COMMIT;
  `);
}

export function migrateSchemaV2ToV3(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS volumes (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_volumes_project_order ON volumes(project_id, sort_order);
    ALTER TABLE chapters ADD COLUMN volume_id TEXT REFERENCES volumes(id) ON DELETE SET NULL;
    ALTER TABLE chapters ADD COLUMN status TEXT NOT NULL DEFAULT '';
    ALTER TABLE chapters ADD COLUMN numbering_kind TEXT NOT NULL DEFAULT 'auto';
    ALTER TABLE chapters ADD COLUMN custom_number TEXT;
    ALTER TABLE chapters ADD COLUMN deleted_at TEXT;
    ALTER TABLE scenes ADD COLUMN planning_json TEXT NOT NULL DEFAULT '{}';
    ALTER TABLE scenes ADD COLUMN deleted_at TEXT;
    INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at)
      SELECT 'volume-default-' || p.id, p.id, '正文', 0, p.created_at, p.updated_at
      FROM projects p
      WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.project_id = p.id);
    UPDATE chapters SET volume_id = 'volume-default-' || project_id WHERE volume_id IS NULL;
    CREATE INDEX IF NOT EXISTS idx_chapters_volume_order ON chapters(volume_id, sort_order);
    PRAGMA user_version = 3;
    COMMIT;
  `);
}

export function migrateSchemaV3ToV4(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS card_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      name TEXT NOT NULL,
      fields_json TEXT NOT NULL DEFAULT '[]',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_card_types_project ON card_types(project_id, sort_order);
    CREATE TABLE IF NOT EXISTS relation_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      forward_name TEXT NOT NULL,
      reverse_name TEXT NOT NULL,
      from_kinds_json TEXT NOT NULL DEFAULT '[]',
      to_kinds_json TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_relation_types_project ON relation_types(project_id);
    ALTER TABLE cards ADD COLUMN aliases_json TEXT NOT NULL DEFAULT '[]';
    ALTER TABLE cards ADD COLUMN fields_json TEXT NOT NULL DEFAULT '{}';
    ALTER TABLE cards ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
    ALTER TABLE cards ADD COLUMN deleted_at TEXT;
    ALTER TABLE card_relations ADD COLUMN note TEXT;
    PRAGMA user_version = 4;
    COMMIT;
  `);
  seedBuiltinCardData(database);
}

export function migrateSchemaV4ToV5(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS writing_sessions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT,
      started_at TEXT NOT NULL,
      active_seconds INTEGER NOT NULL DEFAULT 0,
      net_chars INTEGER NOT NULL DEFAULT 0,
      reported_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_writing_sessions_project_started ON writing_sessions(project_id, started_at);
    PRAGMA user_version = 5;
    COMMIT;
  `);
}

export function migrateSchemaV5ToV6(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS inbox_items (
      id TEXT PRIMARY KEY,
      legacy_id TEXT,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'note',
      status TEXT NOT NULL DEFAULT 'inbox',
      tags_json TEXT NOT NULL DEFAULT '[]',
      platform_tags_json TEXT NOT NULL DEFAULT '[]',
      source_json TEXT,
      variants_json TEXT NOT NULL DEFAULT '[]',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_inbox_items_updated ON inbox_items(updated_at);
    PRAGMA user_version = 6;
    COMMIT;
  `);
}

export function migrateSchemaV6ToV7(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS annotations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      card_id TEXT REFERENCES cards(id) ON DELETE SET NULL,
      anchor_json TEXT NOT NULL,
      note TEXT,
      status TEXT NOT NULL DEFAULT 'open',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_annotations_scene ON annotations(scene_id);
    CREATE INDEX IF NOT EXISTS idx_annotations_project ON annotations(project_id);
    PRAGMA user_version = 7;
    COMMIT;
  `);
}

export function migrateSchemaV7ToV8(database: Database): void {
  database.exec("BEGIN IMMEDIATE");
  const tableExists = database
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'resources'")
    .get();
  if (!tableExists) {
    database.exec(`
      CREATE TABLE resources (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        card_id TEXT REFERENCES cards(id) ON DELETE CASCADE,
        relative_path TEXT NOT NULL,
        sha256 TEXT NOT NULL,
        size INTEGER NOT NULL DEFAULT 0,
        original_name TEXT,
        created_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);
      CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id);
    `);
  } else {
    const columns = new Set(
      (database.prepare("PRAGMA table_info(resources)").all() as Array<{ name: string }>).map((column) => column.name)
    );
    if (!columns.has("card_id")) {
      database.exec("ALTER TABLE resources ADD COLUMN card_id TEXT REFERENCES cards(id) ON DELETE CASCADE");
    }
    if (!columns.has("size")) {
      database.exec("ALTER TABLE resources ADD COLUMN size INTEGER NOT NULL DEFAULT 0");
    }
    if (!columns.has("original_name")) {
      database.exec("ALTER TABLE resources ADD COLUMN original_name TEXT");
    }
    database.exec("CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id)");
  }
  database.exec("PRAGMA user_version = 8; COMMIT;");
}

/** v8→v9：scenes 增加持久化计数列 han_count/punct_count/non_ws_count，并对既有正文一次性回填。 */
export function migrateSchemaV8ToV9(database: Database): void {
  database.exec("BEGIN IMMEDIATE");
  const tableExists = database
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scenes'")
    .get();
  if (!tableExists) {
    // 残缺库（缺 scenes 表）：不补列，由 check() 的 schema-table-missing 暴露；只升版本。
    database.exec("PRAGMA user_version = 9; COMMIT;");
    return;
  }
  const columns = new Set(
    (database.prepare("PRAGMA table_info(scenes)").all() as Array<{ name: string }>).map((column) => column.name)
  );
  if (!columns.has("han_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN han_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columns.has("punct_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN punct_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columns.has("non_ws_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN non_ws_count INTEGER NOT NULL DEFAULT 0");
  }
  const rows = database
    .prepare("SELECT id, body_json FROM scenes WHERE han_count = 0 AND punct_count = 0 AND non_ws_count = 0")
    .all() as Array<{ id: string; body_json: string }>;
  const update = database.prepare("UPDATE scenes SET han_count = ?, punct_count = ?, non_ws_count = ? WHERE id = ?");
  for (const row of rows) {
    const stats = countSceneBodyStats(row.body_json);
    update.run(stats.han, stats.punct, stats.nonWhitespace, row.id);
  }
  database.exec("PRAGMA user_version = 9; COMMIT;");
}

/** v10→v11：场景摘要与场景状态独立持久化；章节 status 保持原工作流语义。 */
export function migrateSchemaV10ToV11(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    ALTER TABLE scenes ADD COLUMN summary TEXT NOT NULL DEFAULT '';
    ALTER TABLE scenes ADD COLUMN scene_status TEXT NOT NULL DEFAULT 'planned';
    PRAGMA user_version = 11;
    COMMIT;
  `);
}

/**
 * v11→v12：校对忽略记录持久化。
 * 唯一键（project_id, scene_id, rule, location_key）保证「按位置忽略」，
 * 同文本在其它段落 / 其它场景 / 其它项目的出现不受影响；不触碰任何正文表。
 */
export function migrateSchemaV11ToV12(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS proof_ignores (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      scene_id TEXT NOT NULL,
      rule TEXT NOT NULL,
      location_key TEXT NOT NULL,
      matched_text TEXT NOT NULL DEFAULT '',
      note TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL
    );
    CREATE UNIQUE INDEX IF NOT EXISTS idx_proof_ignores_location
      ON proof_ignores(project_id, scene_id, rule, location_key);
    CREATE INDEX IF NOT EXISTS idx_proof_ignores_project ON proof_ignores(project_id);
    PRAGMA user_version = 12;
    COMMIT;
  `);
}

/**
 * 校对忽略表的幂等兜底。
 * 与 global_card_resources 同样属于「向后兼容扩展表」：旧库迁移链已建表，
 * 这里只负责让重复打开、异常中断后的重开都能自愈，不写 user_version。
 */
export function ensureProofIgnoreSchema(database: Database): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS proof_ignores (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      scene_id TEXT NOT NULL,
      rule TEXT NOT NULL,
      location_key TEXT NOT NULL,
      matched_text TEXT NOT NULL DEFAULT '',
      note TEXT NOT NULL DEFAULT '',
      created_at TEXT NOT NULL
    );
    CREATE UNIQUE INDEX IF NOT EXISTS idx_proof_ignores_location
      ON proof_ignores(project_id, scene_id, rule, location_key);
    CREATE INDEX IF NOT EXISTS idx_proof_ignores_project ON proof_ignores(project_id);
  `);
}

/**
 * M1-E：全局卡片资产是 v10 的向后兼容扩展。
 * 旧 resources 表继续承载项目附件；新表不带 project 外键，删除项目不会级联删除共享资产。
 */
export function ensureGlobalCardResourceSchema(database: Database): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS global_card_resources (
      id TEXT PRIMARY KEY,
      card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL UNIQUE,
      sha256 TEXT NOT NULL,
      size INTEGER NOT NULL DEFAULT 0,
      original_name TEXT,
      role TEXT NOT NULL DEFAULT 'attachment' CHECK(role IN ('attachment', 'cover')),
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_global_card_resources_card ON global_card_resources(card_id);
    CREATE TABLE IF NOT EXISTS global_card_trash_state (
      card_id TEXT PRIMARY KEY REFERENCES cards(id) ON DELETE CASCADE,
      linked_project_ids_json TEXT NOT NULL DEFAULT '[]',
      deleted_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS global_card_resource_gc (
      relative_path TEXT PRIMARY KEY,
      queued_at TEXT NOT NULL
    );
  `);
  const columns = database.prepare("PRAGMA table_info(global_card_resources)").all() as Array<{ name: string }>;
  if (!columns.some((column) => column.name === "role")) {
    database.exec("ALTER TABLE global_card_resources ADD COLUMN role TEXT NOT NULL DEFAULT 'attachment' CHECK(role IN ('attachment', 'cover'));");
  }
  database.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_global_card_resources_cover ON global_card_resources(card_id) WHERE role = 'cover';");
}

export function removeCardFromPlanningJson(planningJson: string, cardId: string): string | null {
  const planning = JSON.parse(planningJson) as ScenePlanning;
  let changed = false;
  if (planning.perspectiveCardId === cardId) {
    delete planning.perspectiveCardId;
    changed = true;
  }
  if (planning.locationCardId === cardId) {
    delete planning.locationCardId;
    changed = true;
  }
  if (Array.isArray(planning.castCardIds) && planning.castCardIds.includes(cardId)) {
    planning.castCardIds = planning.castCardIds.filter((item) => item !== cardId);
    changed = true;
  }
  return changed ? JSON.stringify(planning) : null;
}

export function queueAndDeleteGlobalCard(database: Database, cardId: string, timestamp: string): void {
  const resources = database
    .prepare("SELECT relative_path FROM global_card_resources WHERE card_id = ?")
    .all(cardId) as Array<{ relative_path: string }>;
  const queue = database.prepare("INSERT OR IGNORE INTO global_card_resource_gc(relative_path, queued_at) VALUES (?, ?)");
  for (const resource of resources) queue.run(resource.relative_path, timestamp);

  const scenes = database.prepare("SELECT id, planning_json FROM scenes").all() as Array<{ id: string; planning_json: string }>;
  const updateScene = database.prepare("UPDATE scenes SET planning_json = ?, updated_at = ?, revision = revision + 1 WHERE id = ?");
  for (const scene of scenes) {
    const updated = removeCardFromPlanningJson(scene.planning_json, cardId);
    if (updated !== null) updateScene.run(updated, timestamp, scene.id);
  }
  database.prepare("UPDATE annotations SET card_id = NULL, updated_at = ?, revision = revision + 1 WHERE card_id = ?").run(timestamp, cardId);
  database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(cardId, cardId);
  database.prepare("DELETE FROM cards WHERE id = ?").run(cardId);
}

export async function drainGlobalCardResourceGc(database: Database, workspaceDirectory: string): Promise<void> {
  const rows = database.prepare("SELECT relative_path FROM global_card_resource_gc ORDER BY queued_at, relative_path").all() as Array<{ relative_path: string }>;
  const workspaceRoot = path.resolve(workspaceDirectory);
  for (const row of rows) {
    const normalized = row.relative_path.replaceAll("\\", "/");
    const target = path.resolve(workspaceRoot, normalized);
    if (!normalized.startsWith("resources/cards/") || !target.startsWith(`${workspaceRoot}${path.sep}`)) continue;
    try {
      await rm(target, { force: true });
      database.prepare("DELETE FROM global_card_resource_gc WHERE relative_path = ?").run(row.relative_path);
    } catch {
      // 保留队列项，下次打开工作区继续重试。
    }
  }
}

/** 回收站到期清理：永久删除超过 30 天的软删除实体（卷级联章节/场景，卡片级联关系）。 */
export function purgeExpiredTrash(database: Database): void {
  const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const volumeRows = database
    .prepare("SELECT id FROM volumes WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const volume of volumeRows) {
    const chapterRows = database
      .prepare("SELECT id FROM chapters WHERE volume_id = ?")
      .all(volume.id) as Array<{ id: string }>;
    for (const chapter of chapterRows) {
      database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
    }
    database.prepare("DELETE FROM chapters WHERE volume_id = ?").run(volume.id);
    database.prepare("DELETE FROM volumes WHERE id = ?").run(volume.id);
  }
  const chapterRows = database
    .prepare("SELECT id FROM chapters WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const chapter of chapterRows) {
    database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
    database.prepare("DELETE FROM chapters WHERE id = ?").run(chapter.id);
  }
  database.prepare("DELETE FROM scenes WHERE deleted_at IS NOT NULL AND deleted_at < ?").run(cutoff);
  const cardRows = database
    .prepare("SELECT id FROM cards WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const card of cardRows) {
    database.exec("BEGIN IMMEDIATE");
    try {
      queueAndDeleteGlobalCard(database, card.id, new Date().toISOString());
      database.exec("COMMIT");
    } catch (error) {
      database.exec("ROLLBACK");
      throw error;
    }
  }
}

/** 当前 schema 版本；契约断言一律引用此常量，避免升版时漏改硬编码数字。 */
export const SCHEMA_VERSION = 12;
