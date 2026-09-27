/**
 * 卡片域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：卡片与卡片类型、关系类型的读写，卡片关联（link/unlink），关系读写与关系图整图投影。
 * 结构写事务、项目时间戳与提交事件由宿主提供；卡片链接表探测、linkedProjectIds 等
 * 纯查询 helper 与卡片域一体搬入（仅 touchProject 经宿主回调）。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type CardCommand,
  type CardCreateCommand,
  type CardDeleteCommand,
  type CardFieldSchema,
  type CardLinkResult,
  type CardRelation,
  type CardRelationCreateCommand,
  type CardRelationDeleteCommand,
  type CardSummary,
  type CardType,
  type CardTypeCreateCommand,
  type CardTypeDeleteCommand,
  type CardTypeUpdateCommand,
  type CardUpdateCommand,
  type CreationStructureResult,
  type CreationWorkspaceEvent,
  type RelationGraphEdge,
  type RelationGraphNode,
  type RelationGraphQuery,
  type RelationGraphView,
  type RelationType,
  type RelationTypeCreateCommand,
  type RelationTypeDeleteCommand,
  type RelationTypeUpdateCommand,
  type ScenePlanning
} from "./types";
import {
  cardFieldSummary,
  clampRelationGraphLimit,
  isConstraintError,
  validateBaseRevision,
  validateId,
  validateTitle
} from "./workspace-utils";
import {
  validateCardFieldSchemaList,
  validateCardFieldValues,
  validateStringList
} from "./validate";

export interface CardsHost {
  requireProject(projectId: string): void;
  touchProject(projectId: string, timestamp: string): void;
  runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => { projectId: string | null; entityId: string; revision: number; changes: CreationWorkspaceEvent["changes"] }
  ): CreationStructureResult;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface CardsModule {
  hasProjectCardLinks(): boolean;
  hasGlobalCardResources(): boolean;
  listCardTypes(legacyProjectId?: string): CardType[];
  listRelationTypes(legacyProjectId?: string): RelationType[];
  listCards(query: { projectId?: string; cardKind?: string; search?: string }): CardSummary[];
  readCard(cardId: string): CardSummary | null;
  readCardRelations(cardId: string): { outgoing: CardRelation[]; incoming: CardRelation[] };
  runRelationGraph(query: RelationGraphQuery): RelationGraphView;
  linkedProjectIds(cardId: string): string[];
  executeCardCommand(command: CardCommand): CreationStructureResult | CardLinkResult;
}

export function createCardsModule(database: Database, host: CardsHost): CardsModule {

  function cardFromRow(row: {
    id: string;
    project_id: string | null;
    linked_project_ids_json?: string;
    usage_count?: number;
    kind: string;
    title: string;
    aliases_json: string;
    fields_json: string;
    tags_json: string;
    created_at: string;
    updated_at: string;
    revision: number;
    cover_resource_id?: string | null;
  }): CardSummary {
    return {
      id: row.id,
      projectId: row.project_id,
      linkedProjectIds: row.linked_project_ids_json
        ? (JSON.parse(row.linked_project_ids_json) as string[])
        : row.project_id
          ? [row.project_id]
          : [],
      usageCount: row.usage_count ?? (row.project_id ? 1 : 0),
      kind: row.kind,
      title: row.title,
      aliases: JSON.parse(row.aliases_json) as string[],
      fields: JSON.parse(row.fields_json) as Record<string, unknown>,
      tags: JSON.parse(row.tags_json) as string[],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision,
      coverResourceId: row.cover_resource_id ?? null
    };
  }

  /** v9 基线夹具兼容；生产 v10 始终为 true。 */
  function hasProjectCardLinks(): boolean {
    return database
      .prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'project_card_links'")
      .get() !== undefined;
  }

  /**
   * 是否存在全局卡片资源表（v10+）。旧库（v9 及更早）没有该表，
   * 此时 `CardSummary.coverResourceId` 恒为 null，列表缩略图降级为空态。
   */
  function hasGlobalCardResources(): boolean {
    return database
      .prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'global_card_resources'")
      .get() !== undefined;
  }

  function listCardTypes(legacyProjectId?: string): CardType[] {
    const builtInProjection = hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END";
    const legacyFilter = !hasProjectCardLinks() && legacyProjectId ? " WHERE project_id IS NULL OR project_id = ?" : "";
    const rows = database
      .prepare(
        `SELECT id, project_id, ${builtInProjection} AS is_builtin, kind, name, fields_json, sort_order, created_at, updated_at, revision
         FROM card_types${legacyFilter} ORDER BY sort_order, id`
      )
      .all(...(legacyFilter ? [legacyProjectId] : [])) as Array<{
      id: string;
      project_id: string | null;
      is_builtin: number;
      kind: string;
      name: string;
      fields_json: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      builtIn: row.is_builtin === 1,
      projectId: row.project_id,
      kind: row.kind,
      name: row.name,
      fields: JSON.parse(row.fields_json) as CardFieldSchema[],
      sortOrder: row.sort_order,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision
    }));
  }

  function listRelationTypes(legacyProjectId?: string): RelationType[] {
    const builtInProjection = hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END";
    const legacyFilter = !hasProjectCardLinks() && legacyProjectId ? " WHERE project_id IS NULL OR project_id = ?" : "";
    const rows = database
      .prepare(
        `SELECT id, project_id, ${builtInProjection} AS is_builtin, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision
         FROM relation_types${legacyFilter} ORDER BY created_at, id`
      )
      .all(...(legacyFilter ? [legacyProjectId] : [])) as Array<{
      id: string;
      project_id: string | null;
      is_builtin: number;
      name: string;
      forward_name: string;
      reverse_name: string;
      from_kinds_json: string;
      to_kinds_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      builtIn: row.is_builtin === 1,
      projectId: row.project_id,
      name: row.name,
      forwardName: row.forward_name,
      reverseName: row.reverse_name,
      fromKinds: JSON.parse(row.from_kinds_json) as string[],
      toKinds: JSON.parse(row.to_kinds_json) as string[],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision
    }));
  }

  function listCards(query: { projectId?: string; cardKind?: string; search?: string }): CardSummary[] {
    const params: unknown[] = [];
    let sql: string;
    // 封面资源 ID 随卡片一起查出，避免列表渲染时每张卡再查一次（N+1）。
    // 数据库层 `idx_global_card_resources_cover` 保证一张卡最多一个封面，故无需排序。
    const coverColumn = hasGlobalCardResources()
      ? `,
          (SELECT r.id FROM global_card_resources r WHERE r.card_id = c.id AND r.role = 'cover' LIMIT 1) AS cover_resource_id`
      : "";
    if (hasProjectCardLinks()) {
      const projection = query.projectId ? "?" : "NULL";
      if (query.projectId) params.push(query.projectId);
      sql = `SELECT c.id, ${projection} AS project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
          c.created_at, c.updated_at, c.revision,
          COALESCE((SELECT json_group_array(project_id) FROM (
            SELECT project_id FROM project_card_links WHERE card_id = c.id ORDER BY project_id
          )), '[]') AS linked_project_ids_json,
          (SELECT count(*) FROM project_card_links WHERE card_id = c.id) AS usage_count${coverColumn}
        FROM cards c WHERE c.deleted_at IS NULL`;
      if (query.projectId) {
        sql += " AND EXISTS (SELECT 1 FROM project_card_links pcl WHERE pcl.project_id = ? AND pcl.card_id = c.id)";
        params.push(query.projectId);
      }
    } else {
      sql = `SELECT c.id, c.project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
        c.created_at, c.updated_at, c.revision${coverColumn} FROM cards c WHERE c.deleted_at IS NULL`;
      if (query.projectId) {
        sql += " AND c.project_id = ?";
        params.push(query.projectId);
      }
    }
    if (query.cardKind) {
      sql += " AND c.kind = ?";
      params.push(query.cardKind);
    }
    if (query.search) {
      sql += " AND (c.title LIKE ? OR c.aliases_json LIKE ? OR c.fields_json LIKE ?)";
      params.push(`%${query.search}%`, `%${query.search}%`, `%${query.search}%`);
    }
    sql += " ORDER BY c.updated_at DESC, c.id DESC";
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string | null;
      linked_project_ids_json?: string;
      usage_count?: number;
      kind: string;
      title: string;
      aliases_json: string;
      fields_json: string;
      tags_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
      cover_resource_id?: string | null;
    }>;
    return rows.map((row) => cardFromRow(row));
  }

  function readCard(cardId: string): CardSummary | null {
    const sql = hasProjectCardLinks()
      ? `SELECT c.id, NULL AS project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
           c.created_at, c.updated_at, c.revision,
           COALESCE((SELECT json_group_array(project_id) FROM (
             SELECT project_id FROM project_card_links WHERE card_id = c.id ORDER BY project_id
           )), '[]') AS linked_project_ids_json,
           (SELECT count(*) FROM project_card_links WHERE card_id = c.id) AS usage_count
         FROM cards c WHERE c.id = ? AND c.deleted_at IS NULL`
      : `SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, created_at, updated_at, revision
         FROM cards WHERE id = ? AND deleted_at IS NULL`;
    const row = database
      .prepare(sql)
      .get(cardId) as
      | {
          id: string;
          project_id: string | null;
          linked_project_ids_json?: string;
          usage_count?: number;
          kind: string;
          title: string;
          aliases_json: string;
          fields_json: string;
          tags_json: string;
          created_at: string;
          updated_at: string;
          revision: number;
        }
      | undefined;
    if (!row) return null;
    return cardFromRow(row);
  }

  function readCardRelations(cardId: string): { outgoing: CardRelation[]; incoming: CardRelation[] } {
    const mapRelation = (row: {
      id: string;
      project_id: string | null;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      created_at: string;
      forward_name: string | null;
    }): CardRelation => ({
      id: row.id,
      projectId: row.project_id,
      fromCardId: row.from_card_id,
      toCardId: row.to_card_id,
      relationTypeId: row.relation_type,
      forwardName: row.forward_name ?? row.relation_type,
      note: row.note,
      createdAt: row.created_at
    });
    const base = `
      SELECT r.id, r.project_id, r.from_card_id, r.to_card_id, r.relation_type, r.note, r.created_at, rt.forward_name
      FROM card_relations r
      JOIN cards source_card ON source_card.id = r.from_card_id AND source_card.deleted_at IS NULL
      JOIN cards target_card ON target_card.id = r.to_card_id AND target_card.deleted_at IS NULL
      LEFT JOIN relation_types rt ON rt.id = r.relation_type`;
    const outgoing = (
      database.prepare(`${base} WHERE r.from_card_id = ? ORDER BY r.created_at, r.id`).all(cardId) as Array<{
        id: string;
        project_id: string | null;
        from_card_id: string;
        to_card_id: string;
        relation_type: string;
        note: string | null;
        created_at: string;
        forward_name: string | null;
      }>
    ).map(mapRelation);
    const incoming = (
      database.prepare(`${base} WHERE r.to_card_id = ? ORDER BY r.created_at, r.id`).all(cardId) as Array<{
        id: string;
        project_id: string | null;
        from_card_id: string;
        to_card_id: string;
        relation_type: string;
        note: string | null;
        created_at: string;
        forward_name: string | null;
      }>
    ).map(mapRelation);
    return { outgoing, incoming };
  }

  /**
   * 关系图整图（Stage 4-F）：一次性取回节点与连线，避免 UI 端按卡片逐个拉关系（N+1）。
   *
   * 边界：关系是**全局卡片资产**。给定 projectId 时不改变关系本身，只把节点收敛到
   * 「该项目已关联卡片」——即引用投影。一端在项目外的关系**不绘制**，
   * 但条数必须显式回传（hiddenRelationCount），避免用户以为关系丢了。
   */
  function runRelationGraph(query: RelationGraphQuery): RelationGraphView {
    const scope: "global" | "project" =
      typeof query.projectId === "string" && query.projectId.trim() !== "" ? "project" : "global";
    const projectId = scope === "project" ? (query.projectId as string).trim() : null;
    const limit = clampRelationGraphLimit(query.limit);

    const cards = listCards(projectId ? { projectId } : {});
    const candidateIds = new Set(cards.map((card) => card.id));

    const rows = database
      .prepare(
        `SELECT r.id, r.from_card_id, r.to_card_id, r.relation_type, r.note,
                rt.name AS relation_name, rt.forward_name, rt.reverse_name
         FROM card_relations r
         JOIN cards source_card ON source_card.id = r.from_card_id AND source_card.deleted_at IS NULL
         JOIN cards target_card ON target_card.id = r.to_card_id AND target_card.deleted_at IS NULL
         LEFT JOIN relation_types rt ON rt.id = r.relation_type
         ORDER BY r.created_at, r.id`
      )
      .all() as Array<{
      id: string;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      relation_name: string | null;
      forward_name: string | null;
      reverse_name: string | null;
    }>;

    const degree = new Map<string, number>();
    const bump = (cardId: string): void => {
      degree.set(cardId, (degree.get(cardId) ?? 0) + 1);
    };
    let hiddenRelationCount = 0;
    const scopedRows: typeof rows = [];
    for (const row of rows) {
      const fromInside = candidateIds.has(row.from_card_id);
      const toInside = candidateIds.has(row.to_card_id);
      if (!fromInside && !toInside) continue; // 与本次范围无关，既不画也不计入
      if (fromInside && toInside) {
        scopedRows.push(row);
        bump(row.from_card_id);
        bump(row.to_card_id);
      } else {
        hiddenRelationCount += 1; // 一端在范围外：不绘制，但显式计数
      }
    }

    // 节点上限：按度数降序保留（关系最密的卡片优先），保证截断可预期、可复现。
    const ordered = [...cards].sort((a, b) => {
      const delta = (degree.get(b.id) ?? 0) - (degree.get(a.id) ?? 0);
      if (delta !== 0) return delta;
      if (a.title !== b.title) return a.title < b.title ? -1 : 1;
      return a.id < b.id ? -1 : 1;
    });
    const kept = ordered.slice(0, limit);
    const keptIds = new Set(kept.map((card) => card.id));
    const truncatedNodeCount = Math.max(0, ordered.length - kept.length);

    // 指向被裁掉节点的关系同样不绘制：合并进 hiddenRelationCount（语义见类型注释）。
    const edges: RelationGraphEdge[] = [];
    for (const row of scopedRows) {
      if (!keptIds.has(row.from_card_id) || !keptIds.has(row.to_card_id)) {
        hiddenRelationCount += 1;
        continue;
      }
      edges.push({
        id: row.id,
        fromCardId: row.from_card_id,
        toCardId: row.to_card_id,
        relationTypeId: row.relation_type,
        relationName: row.relation_name ?? row.relation_type,
        forwardName: row.forward_name ?? row.relation_type,
        reverseName: row.reverse_name ?? row.forward_name ?? row.relation_type,
        note: row.note
      });
    }

    const typeNameOf = new Map(listCardTypes().map((type) => [type.kind, type.name]));
    const nodes: RelationGraphNode[] = kept.map((card) => ({
      cardId: card.id,
      title: card.title,
      kind: card.kind,
      kindName: typeNameOf.get(card.kind) ?? card.kind,
      aliases: [...card.aliases],
      summary: cardFieldSummary(card.fields),
      degree: degree.get(card.id) ?? 0
    }));

    const kindCounts = new Map<string, number>();
    const relationCounts = new Map<string, number>();
    for (const node of nodes) kindCounts.set(node.kind, (kindCounts.get(node.kind) ?? 0) + 1);
    for (const edge of edges) relationCounts.set(edge.relationTypeId, (relationCounts.get(edge.relationTypeId) ?? 0) + 1);
    const relationNameOf = new Map(
      listRelationTypes().map((type) => [type.id, type] as const)
    );

    return {
      scope,
      projectId,
      nodes,
      edges,
      kindFacets: [...kindCounts.entries()]
        .map(([kind, count]) => ({ kind, name: typeNameOf.get(kind) ?? kind, count }))
        .sort((a, b) => b.count - a.count || (a.kind < b.kind ? -1 : 1)),
      relationFacets: [...relationCounts.entries()]
        .map(([id, count]) => {
          const type = relationNameOf.get(id);
          return {
            id,
            name: type?.name ?? id,
            forwardName: type?.forwardName ?? id,
            reverseName: type?.reverseName ?? type?.forwardName ?? id,
            count
          };
        })
        .sort((a, b) => b.count - a.count || (a.id < b.id ? -1 : 1)),
      truncatedNodeCount,
      hiddenRelationCount,
      isolatedNodeCount: nodes.filter((node) => node.degree === 0).length
    };
  }

  function linkedProjectIds(cardId: string): string[] {
    if (!hasProjectCardLinks()) {
      const row = database.prepare("SELECT project_id FROM cards WHERE id = ?").get(cardId) as
        | { project_id: string | null }
        | undefined;
      return row?.project_id ? [row.project_id] : [];
    }
    return (
      database
        .prepare("SELECT project_id FROM project_card_links WHERE card_id = ? ORDER BY project_id")
        .all(cardId) as Array<{ project_id: string }>
    ).map((row) => row.project_id);
  }

  function touchLinkedProjects(cardId: string, timestamp: string): void {
    for (const projectId of linkedProjectIds(cardId)) host.touchProject(projectId, timestamp);
  }

  function executeCardCommand(command: CardCommand): CreationStructureResult | CardLinkResult { switch (command.type) {
    case "cardType.create": return createCardType(command);
    case "cardType.update": return updateCardType(command);
    case "cardType.delete": return deleteCardType(command);
    case "relationType.create": return createRelationType(command);
    case "relationType.update": return updateRelationType(command);
    case "relationType.delete": return deleteRelationType(command);
    case "card.create": return createCard(command);
    case "card.update": return updateCard(command);
    case "card.delete": return deleteCard(command);
    case "card.link": return mutateCardLink(command.projectId, command.cardId, true);
    case "card.unlink": return mutateCardLink(command.projectId, command.cardId, false);
    case "cardRelation.create": return createCardRelation(command);
    case "cardRelation.delete": return deleteCardRelation(command);
} throw new CreationWorkspaceError("invalid-input", "不支持的卡片命令。"); }

  function requireCard(cardId: string): {
    id: string;
    project_id: string | null;
    kind: string;
    title: string;
    aliases: string[];
    fields: Record<string, unknown>;
    tags: string[];
    revision: number;
  } {
    const card = database
      .prepare(
        "SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, revision FROM cards WHERE id = ? AND deleted_at IS NULL"
      )
      .get(cardId) as
      | {
          id: string;
          project_id: string | null;
          kind: string;
          title: string;
          aliases_json: string;
          fields_json: string;
          tags_json: string;
          revision: number;
        }
      | undefined;
    if (!card) throw new CreationWorkspaceError("not-found", "卡片不存在。");
    return {
      id: card.id,
      project_id: card.project_id,
      kind: card.kind,
      title: card.title,
      aliases: JSON.parse(card.aliases_json) as string[],
      fields: JSON.parse(card.fields_json) as Record<string, unknown>,
      tags: JSON.parse(card.tags_json) as string[],
      revision: card.revision
    };
  }

  function requireRelationType(relationTypeId: string): { id: string } {
    const type = database
      .prepare("SELECT id FROM relation_types WHERE id = ?")
      .get(relationTypeId) as { id: string } | undefined;
    if (!type) throw new CreationWorkspaceError("not-found", "关系类型不存在。");
    return type;
  }

  function resolveCardTypeFields(_projectId: string, kind: string): CardFieldSchema[] {
    const builtInOrder = hasProjectCardLinks() ? "is_builtin DESC" : "(project_id IS NULL) DESC";
    const type = database
      .prepare(
        `SELECT fields_json FROM card_types WHERE kind = ? ORDER BY ${builtInOrder}, id LIMIT 1`
      )
      .get(kind) as { fields_json: string } | undefined;
    if (!type) throw new CreationWorkspaceError("invalid-input", `卡片类型“${kind}”不存在。`);
    try {
      return JSON.parse(type.fields_json) as CardFieldSchema[];
    } catch {
      throw new CreationWorkspaceError("integrity", "卡片类型字段数据损坏。");
    }
  }

  function createCardType(command: CardTypeCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const name = validateTitle(command.name, "卡片类型名");
    const fields = validateCardFieldSchemaList(command.fields);
    const kind = `custom-${randomUUID().slice(0, 8)}`;
    const typeId = `card-type-${randomUUID()}`;
    return host.runStructureTransaction("cardType.create", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      const maxOrder = database
        .prepare("SELECT coalesce(max(sort_order), -1) AS m FROM card_types")
        .get() as { m: number };
      const insertSql = hasProjectCardLinks()
        ? "INSERT INTO card_types(id, project_id, is_builtin, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)"
        : "INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
      database
        .prepare(insertSql)
        .run(typeId, projectId, kind, name, JSON.stringify(fields), maxOrder.m + 1, timestamp, timestamp);
      if (projectId) host.touchProject(projectId, timestamp);
      return {
        projectId: null,
        entityId: typeId,
        revision: 1,
        changes: [{ entity: "cardType", id: typeId, action: "created", revision: 1 }]
      };
    });
  }

  function requireCardTypeRow(cardTypeId: string): {
    id: string;
    project_id: string | null;
    kind: string;
    name: string;
    fields_json: string;
    is_builtin: number;
    revision: number;
} { const builtInProjection = hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"; const type = database.prepare(`SELECT id, project_id, ${builtInProjection} AS is_builtin, kind, name, fields_json, revision FROM card_types WHERE id = ?`).get(cardTypeId) as {
    id: string;
    project_id: string | null;
    kind: string;
    name: string;
    fields_json: string;
    is_builtin: number;
    revision: number;
} | undefined; if (!type)
    throw new CreationWorkspaceError("not-found", "卡片类型不存在。"); return type; }

  function updateCardType(command: CardTypeUpdateCommand): CreationStructureResult {
    const cardTypeId = validateId(command.cardTypeId, "卡片类型");
    const name = validateTitle(command.name, "卡片类型名");
    const fields = validateCardFieldSchemaList(command.fields);
    const baseRevision = validateBaseRevision(command.baseRevision);
    return host.runStructureTransaction("cardType.update", (timestamp) => {
        const type = requireCardTypeRow(cardTypeId);
        if (type.is_builtin === 1) {
            throw new CreationWorkspaceError("conflict", "内置卡片类型为只读，不可修改。");
        }
        if (type.revision !== baseRevision) {
            throw new CreationWorkspaceError("revision-mismatch", "卡片类型已被更新，请重新读取后再操作。");
        } // 已存在字段 key 不允许重命名/删除：新 schema 必须覆盖旧 schema 的全部 key。
        let oldFields: CardFieldSchema[];
        try {
            oldFields = JSON.parse(type.fields_json) as CardFieldSchema[];
        }
        catch {
            throw new CreationWorkspaceError("integrity", "卡片类型字段数据损坏。");
        }
        const oldKeys = new Set(oldFields.map((field) => field.key));
        const newKeys = new Set(fields.map((field) => field.key));
        for (const key of oldKeys) {
            if (!newKeys.has(key)) {
                throw new CreationWorkspaceError("invalid-input", `字段 key「${key}」不允许重命名或删除。`);
            }
        } // 新 schema 必须能验证现有卡片数据，禁止静默丢字段。
        const cards = database.prepare("SELECT fields_json FROM cards WHERE kind = ? AND deleted_at IS NULL").all(type.kind) as Array<{
            fields_json: string;
        }>;
        for (const row of cards) {
            let existing: Record<string, unknown>;
            try {
                existing = JSON.parse(row.fields_json) as Record<string, unknown>;
            }
            catch {
                throw new CreationWorkspaceError("integrity", "卡片字段数据损坏。");
            }
            validateCardFieldValues(existing, fields);
        }
        const revision = type.revision + 1;
        database.prepare("UPDATE card_types SET name = ?, fields_json = ?, updated_at = ?, revision = ? WHERE id = ?").run(name, JSON.stringify(fields), timestamp, revision, cardTypeId);
        if (type.project_id) host.touchProject(type.project_id, timestamp);
        return { projectId: null, entityId: cardTypeId, revision, changes: [{ entity: "cardType", id: cardTypeId, action: "updated", revision }] };
    });
}

  function deleteCardType(command: CardTypeDeleteCommand): CreationStructureResult { const cardTypeId = validateId(command.cardTypeId, "卡片类型"); const baseRevision = validateBaseRevision(command.baseRevision); return host.runStructureTransaction("cardType.delete", (timestamp) => { const type = requireCardTypeRow(cardTypeId); if (type.is_builtin === 1) {
    throw new CreationWorkspaceError("conflict", "内置卡片类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "卡片类型已被更新，请重新读取后再操作。");
} const used = database.prepare("SELECT count(*) AS count FROM cards WHERE kind = ? AND deleted_at IS NULL").get(type.kind) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "卡片类型仍被卡片使用，不可删除。");
} database.prepare("DELETE FROM card_types WHERE id = ?").run(cardTypeId); if (type.project_id) host.touchProject(type.project_id, timestamp); return { projectId: null, entityId: cardTypeId, revision: type.revision + 1, changes: [{ entity: "cardType", id: cardTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

  function requireRelationTypeRow(relationTypeId: string): {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    is_builtin: number;
    revision: number;
} { const builtInProjection = hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"; const type = database.prepare(`SELECT id, project_id, ${builtInProjection} AS is_builtin, name, from_kinds_json, to_kinds_json, revision FROM relation_types WHERE id = ?`).get(relationTypeId) as {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    is_builtin: number;
    revision: number;
} | undefined; if (!type)
    throw new CreationWorkspaceError("not-found", "关系类型不存在。"); return type; }

  function updateRelationType(command: RelationTypeUpdateCommand): CreationStructureResult {
    const relationTypeId = validateId(command.relationTypeId, "关系类型");
    const stableName = validateId(command.name, "关系类型稳定标识");
    const forwardName = validateTitle(command.forwardName, "关系名称", 50);
    const reverseName = validateTitle(command.reverseName, "反向关系名称", 50);
    const baseRevision = validateBaseRevision(command.baseRevision);
    const fromKinds = command.fromKinds === undefined ? undefined : validateStringList(command.fromKinds, "起点卡片类型", 50);
    const toKinds = command.toKinds === undefined ? undefined : validateStringList(command.toKinds, "终点卡片类型", 50);
    return host.runStructureTransaction("relationType.update", (timestamp) => {
        const type = requireRelationTypeRow(relationTypeId);
        if (type.is_builtin === 1) {
            throw new CreationWorkspaceError("conflict", "内置关系类型为只读，不可修改。");
        }
        if (type.name !== stableName) {
            throw new CreationWorkspaceError("invalid-input", "关系类型的稳定标识不可修改。");
        }
        if (type.revision !== baseRevision) {
            throw new CreationWorkspaceError("revision-mismatch", "关系类型已被更新，请重新读取后再操作。");
        }
        let oldFrom: string[];
        let oldTo: string[];
        try {
            oldFrom = JSON.parse(type.from_kinds_json) as string[];
            oldTo = JSON.parse(type.to_kinds_json) as string[];
        }
        catch {
            throw new CreationWorkspaceError("integrity", "关系类型约束数据损坏。");
        }
        const nextFrom = fromKinds ?? oldFrom;
        const nextTo = toKinds ?? oldTo;
        const fromChanged = JSON.stringify(nextFrom) !== JSON.stringify(oldFrom);
        const toChanged = JSON.stringify(nextTo) !== JSON.stringify(oldTo);
        if (fromChanged || toChanged) { // 修改两端 kind 约束时，必须检查已有关系实例是否合法。
            const relations = database.prepare(`SELECT cr.id, f.kind AS from_kind, t.kind AS to_kind FROM card_relations cr             JOIN cards f ON f.id = cr.from_card_id             JOIN cards t ON t.id = cr.to_card_id             WHERE cr.relation_type = ?`).all(relationTypeId) as Array<{
                id: string;
                from_kind: string;
                to_kind: string;
            }>;
            for (const relation of relations) {
                const fromOk = nextFrom.length === 0 || nextFrom.includes(relation.from_kind);
                const toOk = nextTo.length === 0 || nextTo.includes(relation.to_kind);
                if (!fromOk || !toOk) {
                    throw new CreationWorkspaceError("conflict", `已有关系（${relation.from_kind}→${relation.to_kind}）不符合新约束，无法更新。`);
                }
            }
        }
        const revision = type.revision + 1;
        database.prepare("UPDATE relation_types SET forward_name = ?, reverse_name = ?, from_kinds_json = ?, to_kinds_json = ?, updated_at = ?, revision = ? WHERE id = ?").run(forwardName, reverseName, JSON.stringify(nextFrom), JSON.stringify(nextTo), timestamp, revision, relationTypeId);
        if (type.project_id) host.touchProject(type.project_id, timestamp);
        return { projectId: null, entityId: relationTypeId, revision, changes: [{ entity: "relationType", id: relationTypeId, action: "updated", revision }] };
    });
}

  function deleteRelationType(command: RelationTypeDeleteCommand): CreationStructureResult { const relationTypeId = validateId(command.relationTypeId, "关系类型"); const baseRevision = validateBaseRevision(command.baseRevision); return host.runStructureTransaction("relationType.delete", (timestamp) => { const type = requireRelationTypeRow(relationTypeId); if (type.is_builtin === 1) {
    throw new CreationWorkspaceError("conflict", "内置关系类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "关系类型已被更新，请重新读取后再操作。");
} const used = database.prepare("SELECT count(*) AS count FROM card_relations WHERE relation_type = ?").get(relationTypeId) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "关系类型仍被关系实例使用，不可删除。");
} database.prepare("DELETE FROM relation_types WHERE id = ?").run(relationTypeId); if (type.project_id) host.touchProject(type.project_id, timestamp); return { projectId: null, entityId: relationTypeId, revision: type.revision + 1, changes: [{ entity: "relationType", id: relationTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

  function createRelationType(command: RelationTypeCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const forwardName = validateTitle(command.forwardName, "关系名称", 50);
    const reverseName = validateTitle(command.reverseName, "反向关系名称", 50);
    const fromKinds = validateStringList(command.fromKinds, "起点卡片类型", 50);
    const toKinds = validateStringList(command.toKinds, "终点卡片类型", 50);
    const relationTypeId = `relation-type-${randomUUID()}`;
    return host.runStructureTransaction("relationType.create", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      const insertSql = hasProjectCardLinks()
        ? "INSERT INTO relation_types(id, project_id, is_builtin, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)"
        : "INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
      database
        .prepare(insertSql)
        .run(
          relationTypeId,
          projectId,
          `rel-${randomUUID().slice(0, 8)}`,
          forwardName,
          reverseName,
          JSON.stringify(fromKinds),
          JSON.stringify(toKinds),
          timestamp,
          timestamp
        );
      if (projectId) host.touchProject(projectId, timestamp);
      return {
        projectId: null,
        entityId: relationTypeId,
        revision: 1,
        changes: [{ entity: "relationType", id: relationTypeId, action: "created", revision: 1 }]
      };
    });
  }

  function createCard(command: CardCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const kind = validateId(command.kind, "卡片类型");
    const title = validateTitle(command.title, "卡片名称");
    const aliases = validateStringList(command.aliases, "别名");
    const tags = validateStringList(command.tags, "标签");
    let contentJson = "{}";
    if (command.content !== undefined && command.content !== null) {
      if (typeof command.content !== "object" || Array.isArray(command.content)) {
        throw new CreationWorkspaceError("invalid-input", "卡片内容必须为对象。");
      }
      contentJson = JSON.stringify(command.content);
      if (contentJson.length > 1_000_000) {
        throw new CreationWorkspaceError("invalid-input", "卡片内容不能超过 1MB。");
      }
    }
    const cardId = `card-${randomUUID()}`;
    return host.runStructureTransaction("card.create", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      const typeFields = resolveCardTypeFields(projectId ?? "", kind);
      const fields = validateCardFieldValues(command.fields ?? {}, typeFields);
      database
        .prepare(
          "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(cardId, projectId, kind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), contentJson, timestamp, timestamp);
      if (hasProjectCardLinks() && projectId) {
        database
          .prepare("INSERT INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, cardId, timestamp);
      }
      if (projectId) host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: cardId,
        revision: 1,
        changes: [{ entity: "card", id: cardId, action: "created", revision: 1 }]
      };
    });
  }

  function updateCard(command: CardUpdateCommand): CreationStructureResult {
    const cardId = validateId(command.cardId, "卡片");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return host.runStructureTransaction("card.update", (timestamp) => {
      const card = requireCard(cardId);
      if (card.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "卡片已被更新，请重新读取后再操作。");
      }
      const nextKind = command.kind === undefined ? card.kind : validateId(command.kind, "卡片类型");
      if (nextKind !== card.kind) {
        // 换类型：目标类型必须存在
        resolveCardTypeFields(card.project_id ?? "", nextKind);
      }
      const schemas = resolveCardTypeFields(card.project_id ?? "", nextKind);
      let fields = card.fields;
      if (command.fields !== undefined) {
        fields = validateCardFieldValues(command.fields, schemas);
      } else if (nextKind !== card.kind) {
        // 换类型且未提供新字段：保留能被新 schema 识别的旧字段，其余丢弃，再补默认并校验必填
        const allowed = new Set(schemas.map((schema) => schema.key));
        const filtered: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(card.fields)) {
          if (allowed.has(key)) filtered[key] = value;
        }
        fields = validateCardFieldValues(filtered, schemas);
      }
      const title = command.title === undefined ? card.title : validateTitle(command.title, "卡片名称");
      const aliases =
        command.aliases === undefined ? card.aliases : validateStringList(command.aliases, "别名");
      const tags = command.tags === undefined ? card.tags : validateStringList(command.tags, "标签");
      const revision = card.revision + 1;
      database
        .prepare(
          "UPDATE cards SET kind = ?, title = ?, aliases_json = ?, fields_json = ?, tags_json = ?, updated_at = ?, revision = ? WHERE id = ?"
        )
        .run(nextKind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), timestamp, revision, cardId);
      touchLinkedProjects(cardId, timestamp);
      return {
        projectId: null,
        entityId: cardId,
        revision,
        changes: [{ entity: "card", id: cardId, action: "updated", revision }]
      };
    });
  }

  function deleteCard(command: CardDeleteCommand): CreationStructureResult {
    const cardId = validateId(command.cardId, "卡片");
    return host.runStructureTransaction("card.delete", (timestamp) => {
      const card = requireCard(cardId);
      const linkedIds = linkedProjectIds(cardId);
      const revision = card.revision + 1;
      if (hasProjectCardLinks()) {
        database
          .prepare("INSERT OR REPLACE INTO global_card_trash_state(card_id, linked_project_ids_json, deleted_at) VALUES (?, ?, ?)")
          .run(cardId, JSON.stringify(linkedIds), timestamp);
        database.prepare("DELETE FROM project_card_links WHERE card_id = ?").run(cardId);
      } else {
        database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(cardId, cardId);
      }
      database
        .prepare("UPDATE cards SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, cardId);
      for (const projectId of linkedIds) host.touchProject(projectId, timestamp);
      return {
        projectId: null,
        entityId: cardId,
        revision,
        changes: [{ entity: "card", id: cardId, action: "deleted", revision }]
      };
    });
  }

  function assertCardCanUnlink(projectId: string, cardId: string): void {
    const annotationCount = (
      database
        .prepare("SELECT count(*) AS count FROM annotations WHERE project_id = ? AND card_id = ?")
        .get(projectId, cardId) as { count: number }
    ).count;
    const resourceCount = (
      database
        .prepare("SELECT count(*) AS count FROM resources WHERE project_id = ? AND card_id = ?")
        .get(projectId, cardId) as { count: number }
    ).count;
    const scenes = database
      .prepare(`SELECT s.id, s.planning_json FROM scenes s
        JOIN chapters c ON c.id = s.chapter_id
        WHERE c.project_id = ? AND s.deleted_at IS NULL`)
      .all(projectId) as Array<{ id: string; planning_json: string }>;
    let planningCount = 0;
    for (const scene of scenes) {
      let planning: ScenePlanning;
      try {
        planning = JSON.parse(scene.planning_json) as ScenePlanning;
      } catch {
        throw new CreationWorkspaceError("integrity", `场景 ${scene.id} 的任务卡数据损坏。`);
      }
      const castCardIds = Array.isArray(planning.castCardIds)
        ? planning.castCardIds.filter((value): value is string => typeof value === "string")
        : [];
      if (
        planning.perspectiveCardId === cardId ||
        planning.locationCardId === cardId ||
        castCardIds.includes(cardId)
      ) {
        planningCount += 1;
      }
    }
    if (annotationCount + resourceCount + planningCount > 0) {
      throw new CreationWorkspaceError(
        "conflict",
        `卡片仍被当前作品使用：场景 ${planningCount}、批注 ${annotationCount}、附件 ${resourceCount}。`
      );
    }
  }

  function mutateCardLink(projectIdInput: string, cardIdInput: string, link: boolean): CardLinkResult {
    const projectId = validateId(projectIdInput, "作品");
    const cardId = validateId(cardIdInput, "卡片");
    const commandType = link ? "card.link" : "card.unlink";
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      host.requireProject(projectId);
      requireCard(cardId);
      const exists =
        database
          .prepare("SELECT 1 FROM project_card_links WHERE project_id = ? AND card_id = ?")
          .get(projectId, cardId) !== undefined;
      let changed = false;
      if (link && !exists) {
        database
          .prepare("INSERT INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, cardId, timestamp);
        changed = true;
      } else if (!link && exists) {
        assertCardCanUnlink(projectId, cardId);
        database
          .prepare("DELETE FROM project_card_links WHERE project_id = ? AND card_id = ?")
          .run(projectId, cardId);
        changed = true;
      }
      const usageCount = (
        database.prepare("SELECT count(*) AS count FROM project_card_links WHERE card_id = ?").get(cardId) as {
          count: number;
        }
      ).count;
      let sequence = -1;
      if (changed) {
        host.touchProject(projectId, timestamp);
        const action = link ? "created" : "deleted";
        const changes: CreationWorkspaceEvent["changes"] = [
          { entity: "projectCardLink", id: `${projectId}:${cardId}`, action, revision: 1 }
        ];
        const logged = database
          .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
          .run(projectId, commandType, JSON.stringify(changes), timestamp);
        sequence = Number(logged.lastInsertRowid);
        database.exec("COMMIT");
        host.emitCommitted({ kind: "committed", sequence, projectId, commandType, changes });
      } else {
        database.exec("COMMIT");
      }
      return {
        commandType,
        sequence,
        projectId,
        cardId,
        entityId: cardId,
        revision: 1,
        updatedAt: timestamp,
        changed,
        linked: link ? true : exists && !changed,
        usageCount
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // SQLite may already have rolled back.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      if (isConstraintError(error)) throw new CreationWorkspaceError("conflict", "卡片关联违反完整性约束。");
      throw new CreationWorkspaceError("integrity", "无法更新项目卡片关联。");
    }
  }

  function createCardRelation(command: CardRelationCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const fromCardId = validateId(command.fromCardId, "起点卡片");
    const toCardId = validateId(command.toCardId, "终点卡片");
    const relationTypeId = validateId(command.relationTypeId, "关系类型");
    const note =
      command.note === undefined || command.note === null ? null : String(command.note).trim() || null;
    const relationId = `relation-${randomUUID()}`;
    return host.runStructureTransaction("cardRelation.create", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      requireCard(fromCardId);
      requireCard(toCardId);
      const linked = projectId && hasProjectCardLinks()
        ? (database
            .prepare("SELECT count(*) AS count FROM project_card_links WHERE project_id = ? AND card_id IN (?, ?)")
            .get(projectId, fromCardId, toCardId) as { count: number })
        : projectId
          ? (database
            .prepare("SELECT count(*) AS count FROM cards WHERE project_id = ? AND id IN (?, ?) AND deleted_at IS NULL")
            .get(projectId, fromCardId, toCardId) as { count: number })
          : { count: 2 };
      if (projectId && linked.count !== 2) {
        throw new CreationWorkspaceError("invalid-input", "关系卡片必须属于同一作品。");
      }
      if (fromCardId === toCardId) {
        throw new CreationWorkspaceError("invalid-input", "不能建立卡片到自身的关系。");
      }
      requireRelationType(relationTypeId);
      database
        .prepare(
          "INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(relationId, projectId, fromCardId, toCardId, relationTypeId, note, timestamp);
      if (projectId) host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: relationId,
        revision: 1,
        changes: [{ entity: "cardRelation", id: relationId, action: "created", revision: 1 }]
      };
    });
  }

  function deleteCardRelation(command: CardRelationDeleteCommand): CreationStructureResult {
    const relationId = validateId(command.relationId, "关系");
    return host.runStructureTransaction("cardRelation.delete", (timestamp) => {
      const relation = database
        .prepare("SELECT id, project_id FROM card_relations WHERE id = ?")
        .get(relationId) as { id: string; project_id: string | null } | undefined;
      if (!relation) throw new CreationWorkspaceError("not-found", "关系不存在。");
      database.prepare("DELETE FROM card_relations WHERE id = ?").run(relationId);
      if (relation.project_id) host.touchProject(relation.project_id, timestamp);
      return {
        projectId: relation.project_id,
        entityId: relationId,
        revision: 1,
        changes: [{ entity: "cardRelation", id: relationId, action: "deleted", revision: 1 }]
      };
    });
  }
  return {
    hasProjectCardLinks,
    hasGlobalCardResources,
    listCardTypes,
    listRelationTypes,
    listCards,
    readCard,
    readCardRelations,
    runRelationGraph,
    linkedProjectIds,
    executeCardCommand,
  };
}
