/**
 * 卡片导入执行与库读取桥接。
 *
 * 本文件是唯一接触 SQLite 的地方（applyCardImportPlan / 读取桥接）。解析、规划、导出均为纯函数。
 *
 * applyCardImportPlan：
 * - 拒绝包含未通过校验行的 plan（类型/字段/关系引用必须 apply 前已验证）。
 * - 使用「一次性 planId」标识本次导入批次，并写入 change_log 审计。
 * - 全程在单个 BEGIN IMMEDIATE … COMMIT 事务内；任何异常触发 ROLLBACK，保证零写入。
 * - 内容列固定写 "{}"，不导入卡片正文/批注（与「不覆盖现有卡片」一致，仅新增）。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import {
  type CardExportFilter,
  type CardExportRow,
  type CardImportApplyResult,
  type CardImportPlan,
  type CardImportRelationTypeInfo,
  type CardImportSchemaContext,
  type CardImportTypeInfo,
  type CardIoFieldSchema,
  type CardRef,
  type PlannedCard
} from "./card-io-types";
import { serializeExportField } from "./card-export";

type Row = Record<string, unknown>;

function resolveRef(ref: CardRef, rowToId: Map<number, string>): string {
  if (ref.source === "existing") return ref.id;
  const id = rowToId.get(ref.rowIndex);
  if (!id) throw new Error(`批量引用指向的行 ${ref.rowIndex} 未生成主键，导入中止。`);
  return id;
}

function buildFieldsJson(card: PlannedCard, rowToId: Map<number, string>): string {
  const fields: Record<string, unknown> = { ...card.fields };
  for (const [key, ref] of Object.entries(card.cardRefFields)) {
    fields[key] = resolveRef(ref, rowToId);
  }
  return JSON.stringify(fields);
}

export function applyCardImportPlan(
  database: Database,
  plan: CardImportPlan,
  planId: string,
  now: Date | string = new Date()
): CardImportApplyResult {
  if (plan.errorCount > 0) {
    throw new Error("存在未通过校验的行，禁止执行导入。请先修正映射或源数据。");
  }
  const iso = now instanceof Date ? now.toISOString() : new Date(now).toISOString();
  database.exec("BEGIN IMMEDIATE");
  try {
    const insertCard = database.prepare(
      "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    );
    const insertRelation = database.prepare(
      "INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
    );
    const rowToId = new Map<number, string>();
    for (const card of plan.cards) {
      const id = `card-${randomUUID()}`;
      insertCard.run(
        id,
        plan.projectId,
        card.kind,
        card.title,
        JSON.stringify(card.aliases),
        buildFieldsJson(card, rowToId),
        JSON.stringify(card.tags),
        "{}",
        iso,
        iso,
        1
      );
      rowToId.set(card.rowIndex, id);
    }
    let appliedRelations = 0;
    for (const rel of plan.relations) {
      const fromId = resolveRef(rel.from, rowToId);
      const toId = resolveRef(rel.to, rowToId);
      if (!fromId || !toId || fromId === toId) continue;
      insertRelation.run(`relation-${randomUUID()}`, plan.projectId, fromId, toId, rel.relationTypeId, null, iso);
      appliedRelations += 1;
    }
    database
      .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
      .run(plan.projectId, "card.import", JSON.stringify({ planId, cards: plan.cards.length, relations: appliedRelations }), iso);
    database.exec("COMMIT");
    return { planId, appliedCardCount: plan.cards.length, appliedRelationCount: appliedRelations };
  } catch (error) {
    database.exec("ROLLBACK");
    throw error;
  }
}

/** 从库读取导入所需的项目模式上下文（类型、关系类型、现有卡片引用）。 */
export function readCardImportSchemaContext(database: Database, projectId: string): CardImportSchemaContext {
  const typeRows = database
    .prepare("SELECT kind, name, fields_json FROM card_types WHERE project_id IS NULL OR project_id = ? ORDER BY (project_id IS NULL), sort_order, id")
    .all(projectId) as Array<{ kind: string; name: string; fields_json: string }>;
  const typeByKind = new Map<string, CardImportTypeInfo>();
  for (const row of typeRows) {
    let fields: CardIoFieldSchema[] = [];
    try {
      fields = JSON.parse(row.fields_json) as CardIoFieldSchema[];
    } catch {
      fields = [];
    }
    const info: CardImportTypeInfo = { kind: row.kind, name: row.name, fields };
    // 项目自定义类型覆盖内置同 kind。
    typeByKind.set(row.kind, info);
  }

  const relRows = database
    .prepare(
      "SELECT id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json FROM relation_types WHERE project_id IS NULL OR project_id = ?"
    )
    .all(projectId) as Array<{
      id: string;
      name: string;
      forward_name: string;
      reverse_name: string;
      from_kinds_json: string;
      to_kinds_json: string;
    }>;
  const relationTypes: CardImportRelationTypeInfo[] = relRows.map((r) => ({
    id: r.id,
    name: r.name,
    forwardName: r.forward_name,
    reverseName: r.reverse_name,
    fromKinds: safeJsonArray(r.from_kinds_json),
    toKinds: safeJsonArray(r.to_kinds_json)
  }));

  const cardRows = database
    .prepare("SELECT id, title, aliases_json, kind FROM cards WHERE project_id = ? AND deleted_at IS NULL")
    .all(projectId) as Array<{ id: string; title: string; aliases_json: string; kind: string }>;
  const existingCards = cardRows.map((c) => ({
    id: c.id,
    title: c.title,
    aliases: safeJsonArray(c.aliases_json),
    kind: c.kind
  }));

  return { projectId, cardTypes: [...typeByKind.values()], relationTypes, existingCards };
}

function safeJsonArray(value: string): string[] {
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? (parsed as unknown[]).filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** 读取当前筛选范围内的卡片，转换为可导出行（cardRef 解析为目标卡片标题，不出现内部 id）。 */
export function readCardsForExport(
  database: Database,
  projectId: string,
  filter: CardExportFilter = {}
): CardExportRow[] {
  const where: string[] = ["c.project_id = ?", "c.deleted_at IS NULL"];
  const params: unknown[] = [projectId];
  if (filter.cardKind) {
    where.push("c.kind = ?");
    params.push(filter.cardKind);
  }
  if (filter.search) {
    where.push("(c.title LIKE ? ESCAPE '\\' OR c.aliases_json LIKE ? ESCAPE '\\' OR c.tags_json LIKE ? ESCAPE '\\' OR c.fields_json LIKE ? ESCAPE '\\')");
    const like = `%${filter.search.replace(/[\\%_]/g, (m) => `\\${m}`)}%`;
    params.push(like, like, like, like);
  }
  let sql = `SELECT c.id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json FROM cards c WHERE ${where.join(" AND ")} ORDER BY c.updated_at, c.id`;
  if (filter.ids && filter.ids.length > 0) {
    const placeholders = filter.ids.map(() => "?").join(",");
    sql = `SELECT c.id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json FROM cards c WHERE c.id IN (${placeholders}) AND c.project_id = ? AND c.deleted_at IS NULL ORDER BY c.updated_at, c.id`;
    params.length = 0;
    params.push(...filter.ids, projectId);
  }

  // 类型 kind -> 显示名；用于 kindLabel。
  const typeRows = database
    .prepare("SELECT kind, name FROM card_types WHERE project_id IS NULL OR project_id = ? ORDER BY (project_id IS NULL), sort_order, id")
    .all(projectId) as Array<{ kind: string; name: string }>;
  const kindToName = new Map<string, string>();
  for (const t of typeRows) kindToName.set(t.kind, t.name);
  const typeFieldsRows = database
    .prepare("SELECT kind, fields_json FROM card_types WHERE project_id IS NULL OR project_id = ?")
    .all(projectId) as Array<{ kind: string; fields_json: string }>;
  const typeFields = new Map<string, CardIoFieldSchema[]>();
  for (const t of typeFieldsRows) {
    try {
      typeFields.set(t.kind, JSON.parse(t.fields_json) as CardIoFieldSchema[]);
    } catch {
      /* 忽略损坏 */
    }
  }

  // id -> 标题，用于 cardRef 反查。
  const allCards = database
    .prepare("SELECT id, title FROM cards WHERE project_id = ? AND deleted_at IS NULL")
    .all(projectId) as Array<{ id: string; title: string }>;
  const idToTitle = new Map<string, string>(allCards.map((c) => [c.id, c.title]));

  const rows = database.prepare(sql).all(...params) as Array<{
    id: string;
    kind: string;
    title: string;
    aliases_json: string;
    fields_json: string;
    tags_json: string;
  }>;
  const result: CardExportRow[] = [];
  for (const r of rows) {
    let fields: Record<string, unknown> = {};
    try {
      fields = JSON.parse(r.fields_json) as Record<string, unknown>;
    } catch {
      fields = {};
    }
    const schemaFields = typeFields.get(r.kind) ?? [];
    const outFields: Record<string, string> = {};
    for (const [k, v] of Object.entries(fields)) {
      const schema = schemaFields.find((f) => f.key === k);
      if (schema?.kind === "cardRef" && typeof v === "string") {
        outFields[k] = idToTitle.get(v) ?? v; // 不导出内部 id，转为标题
      } else {
        outFields[k] = serializeExportField(v);
      }
    }
    result.push({
      title: r.title,
      kindLabel: kindToName.get(r.kind) ?? r.kind,
      aliases: safeJsonArray(r.aliases_json),
      tags: safeJsonArray(r.tags_json),
      fields: outFields
    });
  }
  return result;
}

export type { Row };
