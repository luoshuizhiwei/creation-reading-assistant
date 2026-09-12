import { randomUUID } from "node:crypto";
import path from "node:path";
import type Database from "better-sqlite3";
import { createBackupSnapshot, readBackupManifest, type BackupManifest } from "../backup";

export const GLOBAL_CARD_SCHEMA_VERSION = 10;
export const V9_MIGRATION_BACKUP_MARKER = ".v9-to-v10-backup-";
export const V10_GLOBAL_CARD_REPAIR_BACKUP_MARKER = ".v10-global-card-repair-backup-";

export interface GlobalCardMigrationHooks {
  /** 契约测试专用：验证备份失败时数据库保持 v9。 */
  failBackup?: boolean;
  /** 契约测试专用：验证事务中途失败时回滚全部 v10 DDL/DML。 */
  failAfterLinkBackfill?: boolean;
}

export interface V9MigrationBackupResult {
  backupRoot: string;
  manifest: BackupManifest;
}

export type GlobalCardSchemaRepairBackupResult = V9MigrationBackupResult;

export interface GlobalCardMigrationResult {
  migratedCards: number;
  migratedLinks: number;
  migratedRelations: number;
  preservedAnnotations: number;
  preservedResources: number;
}

function timestampForPath(date: Date): string {
  return date.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
}

/**
 * v10 升级前的目录级安全快照。
 *
 * 迁移调用方必须先关闭 SQLite；目标是 workspace 的同级目录，避免把备份递归复制进自身。
 * createBackupSnapshot 会为 SQLite、WAL/SHM（若存在）和 resources/** 建立逐文件哈希清单，
 * readBackupManifest 再次读取清单，确保成功返回前备份确实可发现、可校验。
 */
export async function createV9MigrationBackup(
  workspaceDirectory: string,
  hooks: GlobalCardMigrationHooks = {}
): Promise<V9MigrationBackupResult> {
  if (hooks.failBackup) throw new Error("v9→v10 升级前备份失败（测试注入）。");
  const resolvedWorkspace = path.resolve(workspaceDirectory);
  const backupRoot = path.join(
    path.dirname(resolvedWorkspace),
    `${path.basename(resolvedWorkspace)}${V9_MIGRATION_BACKUP_MARKER}${timestampForPath(new Date())}-${randomUUID().slice(0, 8)}`
  );
  const manifest = await createBackupSnapshot({
    backupRoot,
    appDataDirectory: resolvedWorkspace,
    appVersion: "workspace-schema-v9",
    platform: process.platform,
    arch: process.arch,
    createdAt: new Date().toISOString()
  });
  await readBackupManifest(backupRoot);
  return { backupRoot, manifest };
}

/**
 * M1-A 开发期曾短暂生成过 user_version=10、但仍带项目所有权约束的数据库。
 * 这些库不是正式发布版本，仍需在修正结构前做与正式迁移同等级的目录快照。
 */
export async function createGlobalCardSchemaRepairBackup(
  workspaceDirectory: string
): Promise<GlobalCardSchemaRepairBackupResult> {
  const resolvedWorkspace = path.resolve(workspaceDirectory);
  const backupRoot = path.join(
    path.dirname(resolvedWorkspace),
    `${path.basename(resolvedWorkspace)}${V10_GLOBAL_CARD_REPAIR_BACKUP_MARKER}${timestampForPath(new Date())}-${randomUUID().slice(0, 8)}`
  );
  const manifest = await createBackupSnapshot({
    backupRoot,
    appDataDirectory: resolvedWorkspace,
    appVersion: "workspace-schema-v10-global-card-repair",
    platform: process.platform,
    arch: process.arch,
    createdAt: new Date().toISOString()
  });
  await readBackupManifest(backupRoot);
  return { backupRoot, manifest };
}

function count(database: Database, table: string): number {
  return (database.prepare(`SELECT count(*) AS count FROM ${table}`).get() as { count: number }).count;
}

function validatePlanningReferences(database: Database): void {
  const existingCardIds = new Set(
    (database.prepare("SELECT id FROM cards").all() as Array<{ id: string }>).map((row) => row.id)
  );
  const scenes = database.prepare("SELECT id, planning_json FROM scenes").all() as Array<{
    id: string;
    planning_json: string;
  }>;
  for (const scene of scenes) {
    let planning: Record<string, unknown>;
    try {
      const parsed = JSON.parse(scene.planning_json) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("invalid planning payload");
      }
      planning = parsed as Record<string, unknown>;
    } catch {
      throw new Error(`场景 ${scene.id} 的 planning_json 损坏，已中止 v10 迁移。`);
    }
    const referenced = new Set<string>();
    for (const key of ["perspectiveCardId", "locationCardId"] as const) {
      const value = planning[key];
      if (typeof value === "string" && value) referenced.add(value);
    }
    if (Array.isArray(planning.castCardIds)) {
      for (const value of planning.castCardIds) {
        if (typeof value === "string" && value) referenced.add(value);
      }
    }
    for (const cardId of referenced) {
      if (!existingCardIds.has(cardId)) {
        throw new Error(`场景 ${scene.id} 引用了不存在的卡片 ${cardId}，已中止 v10 迁移。`);
      }
    }
  }
}

interface TableColumnInfo {
  name: string;
  notnull: 0 | 1;
}

interface ForeignKeyInfo {
  table: string;
  from: string;
}

function tableColumns(database: Database, table: string): TableColumnInfo[] {
  return database.prepare(`PRAGMA table_info(${table})`).all() as TableColumnInfo[];
}

function hasProjectOwnershipForeignKey(database: Database, table: string): boolean {
  return (database.prepare(`PRAGMA foreign_key_list(${table})`).all() as ForeignKeyInfo[]).some(
    (foreignKey) => foreignKey.table === "projects" && foreignKey.from === "project_id"
  );
}

function projectColumnIsRequired(database: Database, table: string): boolean {
  return tableColumns(database, table).find((column) => column.name === "project_id")?.notnull === 1;
}

function hasColumn(database: Database, table: string, column: string): boolean {
  return tableColumns(database, table).some((item) => item.name === column);
}

function hasTable(database: Database, table: string): boolean {
  return database
    .prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")
    .get(table) !== undefined;
}

/** 是否为 M1-A 开发期生成的、尚未完全解除项目所有权的 v10 结构。 */
export function needsGlobalCardSchemaRepair(database: Database): boolean {
  if (Number(database.pragma("user_version", { simple: true })) !== GLOBAL_CARD_SCHEMA_VERSION) return false;
  if (!hasTable(database, "project_card_links")) {
    throw new Error("schema v10 缺少 project_card_links，无法安全修复。");
  }
  return (
    !hasColumn(database, "card_types", "is_builtin") ||
    !hasColumn(database, "relation_types", "is_builtin") ||
    projectColumnIsRequired(database, "cards") ||
    projectColumnIsRequired(database, "card_relations") ||
    hasProjectOwnershipForeignKey(database, "card_types") ||
    hasProjectOwnershipForeignKey(database, "relation_types") ||
    hasProjectOwnershipForeignKey(database, "cards") ||
    hasProjectOwnershipForeignKey(database, "card_relations")
  );
}

/**
 * 在已开启的事务中把四张卡片核心表统一转换成“全局实体 + legacy origin”结构。
 * project_id 仅保留来源信息，不再表达所有权，也不参与项目删除级联。
 */
function applyGlobalCardStorageShape(database: Database): void {
  const cardTypesHaveBuiltin = hasColumn(database, "card_types", "is_builtin");
  const relationTypesHaveBuiltin = hasColumn(database, "relation_types", "is_builtin");

  database.exec(`
    ALTER TABLE card_types RENAME TO card_types_global_legacy;
    CREATE TABLE card_types (
      id TEXT PRIMARY KEY,
      project_id TEXT,
      kind TEXT NOT NULL,
      name TEXT NOT NULL,
      fields_json TEXT NOT NULL DEFAULT '[]',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      is_builtin INTEGER NOT NULL DEFAULT 0
    );
    INSERT INTO card_types
      (id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision, is_builtin)
    SELECT id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision,
      ${cardTypesHaveBuiltin ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"}
    FROM card_types_global_legacy;
    DROP TABLE card_types_global_legacy;
    CREATE INDEX idx_card_types_project ON card_types(project_id, sort_order);

    ALTER TABLE relation_types RENAME TO relation_types_global_legacy;
    CREATE TABLE relation_types (
      id TEXT PRIMARY KEY,
      project_id TEXT,
      name TEXT NOT NULL,
      forward_name TEXT NOT NULL,
      reverse_name TEXT NOT NULL,
      from_kinds_json TEXT NOT NULL DEFAULT '[]',
      to_kinds_json TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      is_builtin INTEGER NOT NULL DEFAULT 0
    );
    INSERT INTO relation_types
      (id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision, is_builtin)
    SELECT id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision,
      ${relationTypesHaveBuiltin ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"}
    FROM relation_types_global_legacy;
    DROP TABLE relation_types_global_legacy;
    CREATE INDEX idx_relation_types_project ON relation_types(project_id);

    ALTER TABLE card_relations RENAME TO card_relations_global_legacy;

    CREATE TABLE cards_global (
      id TEXT PRIMARY KEY,
      project_id TEXT,
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
    INSERT INTO cards_global
      (id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision, deleted_at)
    SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision, deleted_at
    FROM cards;
    DROP TABLE cards;
    ALTER TABLE cards_global RENAME TO cards;
    CREATE INDEX idx_cards_project_kind ON cards(project_id, kind);

    CREATE TABLE card_relations (
      id TEXT PRIMARY KEY,
      project_id TEXT,
      from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relation_type TEXT NOT NULL,
      note TEXT,
      created_at TEXT NOT NULL,
      UNIQUE(from_card_id, to_card_id, relation_type)
    );
    INSERT INTO card_relations
      (id, project_id, from_card_id, to_card_id, relation_type, note, created_at)
    SELECT id, project_id, from_card_id, to_card_id, relation_type, note, created_at
    FROM card_relations_global_legacy;
    DROP TABLE card_relations_global_legacy;
    CREATE INDEX idx_card_relations_from ON card_relations(from_card_id);
    CREATE INDEX idx_card_relations_to ON card_relations(to_card_id);
  `);
}

/** 修复开发期不完整 v10；调用前必须完成目录备份。 */
export function repairGlobalCardSchemaV10(database: Database, backupRoot: string): void {
  if (!needsGlobalCardSchemaRepair(database)) return;
  const countsBefore = {
    cardTypes: count(database, "card_types"),
    relationTypes: count(database, "relation_types"),
    cards: count(database, "cards"),
    relations: count(database, "card_relations")
  };
  database.pragma("foreign_keys = OFF");
  try {
    database.exec("BEGIN IMMEDIATE");
    applyGlobalCardStorageShape(database);
    const countsAfter = {
      cardTypes: count(database, "card_types"),
      relationTypes: count(database, "relation_types"),
      cards: count(database, "cards"),
      relations: count(database, "card_relations")
    };
    if (JSON.stringify(countsAfter) !== JSON.stringify(countsBefore)) {
      throw new Error("v10 全局卡片结构修复前后实体计数不一致。");
    }
    const foreignKeyProblems = database.pragma("foreign_key_check") as unknown[];
    if (foreignKeyProblems.length > 0) {
      throw new Error(`v10 全局卡片结构修复后发现 ${foreignKeyProblems.length} 条外键异常。`);
    }
    database
      .prepare("INSERT OR REPLACE INTO workspace_meta(key, value) VALUES ('v10_global_card_repair_backup_root', ?)")
      .run(backupRoot);
    database
      .prepare("INSERT OR REPLACE INTO workspace_meta(key, value) VALUES ('v10_global_card_repaired_at', ?)")
      .run(new Date().toISOString());
    database.exec("COMMIT");
  } catch (error) {
    try {
      database.exec("ROLLBACK");
    } catch {
      // BEGIN 之前失败或 SQLite 已自行回滚。
    }
    throw error;
  } finally {
    database.pragma("foreign_keys = ON");
  }
}

/**
 * v9→v10 的事务内迁移核心。
 *
 * 本切片先建立全局卡片的稳定实体 + project_card_links M:N seam，并保留旧列作为
 * M1-B renderer/IPC 兼容期的来源信息。所有旧卡片（含软删除项）都写入一条原项目链接；
 * 关系、批注、附件和场景规划引用在升版本前逐项校验，任何异常整体回滚。
 */
export function migrateGlobalCardsV9ToV10(
  database: Database,
  backupRoot: string | null,
  hooks: GlobalCardMigrationHooks = {}
): GlobalCardMigrationResult {
  const version = Number(database.pragma("user_version", { simple: true }));
  if (version !== 9) throw new Error(`v9→v10 迁移收到不支持的 schema 版本：${version}。`);

  const cardsBefore = count(database, "cards");
  const relationsBefore = count(database, "card_relations");
  const annotationsBefore = count(database, "annotations");
  const resourcesBefore = count(database, "resources");

  // SQLite 只能通过重建表移除旧的 project 级联所有权。迁移期暂时关闭 FK，
  // 在 COMMIT 前用 foreign_key_check 对最终 schema 做完整检查，任何异常仍可回滚。
  database.pragma("foreign_keys = OFF");
  try {
    database.exec("BEGIN IMMEDIATE");
    database.exec(`
      CREATE TABLE project_card_links (
        project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
        linked_at TEXT NOT NULL,
        PRIMARY KEY (project_id, card_id)
      );
      CREATE INDEX idx_project_card_links_card ON project_card_links(card_id, project_id);

      INSERT INTO project_card_links(project_id, card_id, linked_at)
      SELECT project_id, id, created_at FROM cards;
    `);

    if (hooks.failAfterLinkBackfill) {
      throw new Error("v9→v10 迁移中途失败（测试注入）。");
    }

    const linksAfter = count(database, "project_card_links");
    if (linksAfter !== cardsBefore) {
      throw new Error(`卡片关联回填数量不一致：卡片 ${cardsBefore}，关联 ${linksAfter}。`);
    }
    const orphanRelations = (
      database
        .prepare(`SELECT count(*) AS count FROM card_relations r
          LEFT JOIN cards f ON f.id = r.from_card_id
          LEFT JOIN cards t ON t.id = r.to_card_id
          WHERE f.id IS NULL OR t.id IS NULL`)
        .get() as { count: number }
    ).count;
    if (orphanRelations > 0) throw new Error(`发现 ${orphanRelations} 条端点缺失的卡片关系。`);

    const orphanAnnotations = (
      database
        .prepare(`SELECT count(*) AS count FROM annotations a
          LEFT JOIN cards c ON c.id = a.card_id
          WHERE a.card_id IS NOT NULL AND c.id IS NULL`)
        .get() as { count: number }
    ).count;
    if (orphanAnnotations > 0) throw new Error(`发现 ${orphanAnnotations} 条卡片引用失效的批注。`);

    const orphanResources = (
      database
        .prepare(`SELECT count(*) AS count FROM resources r
          LEFT JOIN cards c ON c.id = r.card_id
          WHERE r.card_id IS NOT NULL AND c.id IS NULL`)
        .get() as { count: number }
    ).count;
    if (orphanResources > 0) throw new Error(`发现 ${orphanResources} 条卡片引用失效的附件。`);

    validatePlanningReferences(database);

    applyGlobalCardStorageShape(database);

    if (
      count(database, "cards") !== cardsBefore ||
      count(database, "card_relations") !== relationsBefore ||
      count(database, "annotations") !== annotationsBefore ||
      count(database, "resources") !== resourcesBefore
    ) {
      throw new Error("v10 迁移前后实体计数不一致。");
    }

    const foreignKeyProblems = database.pragma("foreign_key_check") as unknown[];
    if (foreignKeyProblems.length > 0) {
      throw new Error(`v10 迁移后发现 ${foreignKeyProblems.length} 条外键异常。`);
    }

    database
      .prepare("INSERT OR REPLACE INTO workspace_meta(key, value) VALUES ('v9_to_v10_backup_root', ?)")
      .run(backupRoot ?? "new-workspace-no-backup-required");
    database
      .prepare("INSERT OR REPLACE INTO workspace_meta(key, value) VALUES ('v9_to_v10_migrated_at', ?)")
      .run(new Date().toISOString());
    database.pragma(`user_version = ${GLOBAL_CARD_SCHEMA_VERSION}`);
    database.exec("COMMIT");
    return {
      migratedCards: cardsBefore,
      migratedLinks: linksAfter,
      migratedRelations: relationsBefore,
      preservedAnnotations: annotationsBefore,
      preservedResources: resourcesBefore
    };
  } catch (error) {
    try {
      database.exec("ROLLBACK");
    } catch {
      // BEGIN 之前失败或 SQLite 已自行回滚。
    }
    throw error;
  } finally {
    database.pragma("foreign_keys = ON");
  }
}
