/**
 * 完整性检查域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 * 
 * 职责：一次性的全库体检——表/列/索引齐备性、结构外键一致性、资源文件可达性与写作统计口径。
 * 纯只读，不写库、不发事件，因此仅需 Database 窄接口，无宿主回调。
 */

import type Database from "better-sqlite3";
import { type CreationIntegrityReport } from "./types";
import { createSection } from "./workspace-utils";
import { parseStoredSetup } from "./project-setup";
import { REQUIRED_INDEXES, REQUIRED_TABLES, SCHEMA_VERSION } from "./schema";

export interface IntegrityModule {
  runIntegrityCheck(): CreationIntegrityReport;
}

export function createIntegrityModule(database: Database): IntegrityModule {

  function runIntegrityCheck(): CreationIntegrityReport {
    const checkedAt = new Date().toISOString();
    const schemaIssues: Array<{ code: string; message: string }> = [];
    const relationIssues: Array<{ code: string; message: string }> = [];
    const resourceIssues: Array<{ code: string; message: string }> = [];
    const indexIssues: Array<{ code: string; message: string }> = [];
    const snapshotIssues: Array<{ code: string; message: string }> = [];

    const schemaVersion = Number(database.pragma("user_version", { simple: true }));
    if (schemaVersion !== SCHEMA_VERSION) {
      schemaIssues.push({ code: "schema-version", message: `预期 schema ${SCHEMA_VERSION}，实际为 ${schemaVersion}。` });
    }
    const journalMode = String(database.pragma("journal_mode", { simple: true }));
    if (journalMode !== "wal") {
      schemaIssues.push({ code: "journal-mode", message: `预期 WAL，实际为 ${journalMode}。` });
    }
    const foreignKeysEnabled = Number(database.pragma("foreign_keys", { simple: true })) === 1;
    if (!foreignKeysEnabled) {
      schemaIssues.push({ code: "foreign-keys-disabled", message: "SQLite 外键约束未启用。" });
    }

    const objects = database
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

    const integrity = String(database.pragma("integrity_check", { simple: true }));
    if (integrity !== "ok") schemaIssues.push({ code: "sqlite-integrity", message: "SQLite 完整性检查失败。" });
    const foreignKeyRows = database.pragma("foreign_key_check") as unknown[];
    if (foreignKeyRows.length > 0) {
      relationIssues.push({ code: "foreign-key", message: `发现 ${foreignKeyRows.length} 个关系完整性问题。` });
    }

    if (tables.has("resources") && tables.has("projects")) {
      const missingResources = database
        .prepare("SELECT count(*) AS count FROM resources r LEFT JOIN projects p ON p.id = r.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingResources.count > 0) {
        resourceIssues.push({ code: "resource-project", message: `发现 ${missingResources.count} 个失去项目归属的资源。` });
      }
    }

    if (tables.has("snapshots") && tables.has("projects")) {
      const missingSnapshots = database
        .prepare("SELECT count(*) AS count FROM snapshots s LEFT JOIN projects p ON p.id = s.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingSnapshots.count > 0) {
        snapshotIssues.push({ code: "snapshot-project", message: `发现 ${missingSnapshots.count} 个失去项目归属的快照。` });
      }
    }

    if (tables.has("projects")) {
      const setupRows = database.prepare("SELECT setup_json FROM projects").all() as Array<{ setup_json: string }>;
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
      return (database.prepare(`SELECT count(*) AS count FROM ${table}`).get() as { count: number }).count;
    };
    const latestSequence = tables.has("change_log")
      ? (
          database.prepare("SELECT coalesce(max(sequence), 0) AS sequence FROM change_log").get() as {
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
        volumes: count("volumes"),
        chapters: count("chapters"),
        scenes: count("scenes"),
        cards: count("cards"),
        relations: count("card_relations"),
        resources: count("resources"),
        snapshots: count("snapshots"),
        sessions: count("writing_sessions"),
        inbox: count("inbox_items"),
        annotations: count("annotations")
      },
      schema,
      relations,
      resources,
      indexes: indexesSection,
      snapshots
    };
  }

  return {
    runIntegrityCheck,
  };
}
