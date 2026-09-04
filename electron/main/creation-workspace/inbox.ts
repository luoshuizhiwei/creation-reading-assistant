/**
 * 创作工作区收件箱域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：收件箱条目列表/计数/创建/更新/软删除/转资料卡。
 * 通过 Database + InboxHost（requireProject/touchProject/emitCommitted）与宿主解耦。
 */

import type Database from "better-sqlite3";
import { randomUUID } from "node:crypto";
import { CreationWorkspaceError } from "./types";
import { parseJsonArray, validateBaseRevision, validateId, validateTitle } from "./workspace-utils";
import type {
  CreationWorkspaceEvent,
  InboxConvertToCardCommand,
  InboxConvertToCardResult,
  InboxCountView,
  InboxCreateCommand,
  InboxDeleteCommand,
  InboxItem,
  InboxItemResult,
  InboxUpdateCommand
} from "../../../src/types/creation";

/** 宿主提供的最小校验/副作用回调（避免把 requireProject / touchProject / emitCommitted 复制两份）。 */
export interface InboxHost {
  requireProject(projectId: string): void;
  touchProject(projectId: string, timestamp: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface InboxModule {
  runInboxList(limit: number, offset: number): InboxItem[];
  runInboxCount(): InboxCountView;
  runInboxCreate(command: InboxCreateCommand): InboxItemResult;
  runInboxUpdate(command: InboxUpdateCommand): InboxItemResult;
  runInboxDelete(command: InboxDeleteCommand): InboxItemResult;
  runInboxConvertToCard(command: InboxConvertToCardCommand): InboxConvertToCardResult;
  /** 单条原始行 → InboxItem 映射（read 分派用）。 */
  inboxFromRow(row: {
    id: string;
    legacy_id: string | null;
    title: string;
    body: string;
    type: string;
    status: string;
    tags_json: string;
    platform_tags_json: string;
    source_json: string | null;
    variants_json: string;
    revision: number;
    created_at: string;
    updated_at: string;
  }): InboxItem;
}

interface InboxRow {
  id: string;
  legacy_id: string | null;
  title: string;
  body: string;
  type: string;
  status: string;
  tags_json: string;
  platform_tags_json: string;
  source_json: string | null;
  variants_json: string;
  revision: number;
  created_at: string;
  updated_at: string;
}

export function createInboxModule(database: Database, host: InboxHost): InboxModule {
  const inboxFromRow = (row: InboxRow): InboxItem => ({
    id: row.id,
    legacyId: row.legacy_id,
    title: row.title,
    body: row.body,
    type: row.type,
    status: row.status,
    tags: parseJsonArray(row.tags_json),
    platformTags: parseJsonArray(row.platform_tags_json),
    source: row.source_json ? (JSON.parse(row.source_json) as Record<string, unknown>) : null,
    variants: JSON.parse(row.variants_json) as Array<Record<string, unknown>>,
    revision: row.revision,
    createdAt: row.created_at,
    updatedAt: row.updated_at
  });

  const runInboxList = (limit: number, offset: number): InboxItem[] => {
    const rows = database
      .prepare(
        "SELECT id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at FROM inbox_items WHERE deleted_at IS NULL ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?"
      )
      .all(limit, offset) as InboxRow[];
    return rows.map((row) => inboxFromRow(row));
  };

  const runInboxCount = (): InboxCountView => {
    const totalRow = database
      .prepare("SELECT count(*) AS count FROM inbox_items WHERE deleted_at IS NULL")
      .get() as { count: number };
    const pendingRow = database
      .prepare("SELECT count(*) AS count FROM inbox_items WHERE deleted_at IS NULL AND status != 'used'")
      .get() as { count: number };
    return { total: totalRow.count, pending: pendingRow.count };
  };

  const runInboxCreate = (command: InboxCreateCommand): InboxItemResult => {
    const title = validateTitle(command.title, "标题", 200);
    const body = typeof command.body === "string" ? command.body : "";
    if (body.length > 1_000_000) throw new CreationWorkspaceError("invalid-input", "正文不能超过 1000000 个字符。");
    const kind = typeof command.kind === "string" && command.kind.trim() ? command.kind.trim() : "note";
    const status = typeof command.status === "string" && command.status.trim() ? command.status.trim() : "inbox";
    const tags = Array.isArray(command.tags) ? command.tags.filter((tag): tag is string => typeof tag === "string") : [];
    const platformTags = Array.isArray(command.platformTags)
      ? command.platformTags.filter((tag): tag is string => typeof tag === "string")
      : [];
    const legacyId = typeof command.legacyId === "string" && command.legacyId.trim() ? command.legacyId.trim() : undefined;
    const source = command.source === null || command.source === undefined ? null : command.source;
    const variants = Array.isArray(command.variants) ? command.variants : [];
    const itemId = `inbox-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      if (legacyId) {
        const existing = database
          .prepare("SELECT id FROM inbox_items WHERE legacy_id = ? AND deleted_at IS NULL")
          .get(legacyId) as { id: string } | undefined;
        if (existing) throw new CreationWorkspaceError("conflict", `收件箱已存在旧灵感 ${legacyId} 的迁移条目。`);
      }
      database
        .prepare(
          "INSERT INTO inbox_items(id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)"
        )
        .run(
          itemId,
          legacyId ?? null,
          title,
          body,
          kind,
          status,
          JSON.stringify(tags),
          JSON.stringify(platformTags),
          source ? JSON.stringify(source) : null,
          JSON.stringify(variants),
          timestamp,
          timestamp
        );
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (NULL, ?, ?, ?)")
        .run("inbox.create", JSON.stringify([{ entity: "inbox", id: itemId, action: "created", revision: 1 }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: "",
        commandType: "inbox.create",
        changes: [{ entity: "inbox", id: itemId, action: "created", revision: 1 }]
      });
      return { commandType: "inbox.create", sequence: Number(logged.lastInsertRowid), itemId, revision: 1, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法创建收件箱条目。");
    }
  };

  const runInboxUpdate = (command: InboxUpdateCommand): InboxItemResult => {
    const itemId = validateId(command.itemId, "条目");
    const baseRevision = validateBaseRevision(command.baseRevision);
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT revision FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
        .get(itemId) as { revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "收件箱条目不存在。");
      if (current.revision !== baseRevision) throw new CreationWorkspaceError("revision-mismatch", "收件箱条目已被其他修改更新。");
      const sets: string[] = [];
      const params: unknown[] = [];
      if (command.title !== undefined) {
        sets.push("title = ?");
        params.push(validateTitle(command.title, "标题", 200));
      }
      if (command.body !== undefined) {
        if (command.body.length > 1_000_000) throw new CreationWorkspaceError("invalid-input", "正文不能超过 1000000 个字符。");
        sets.push("body = ?");
        params.push(command.body);
      }
      if (command.status !== undefined) {
        const status = typeof command.status === "string" && command.status.trim() ? command.status.trim() : "inbox";
        sets.push("status = ?");
        params.push(status);
      }
      if (command.kind !== undefined) {
        const kind = typeof command.kind === "string" && command.kind.trim() ? command.kind.trim() : "note";
        sets.push("type = ?");
        params.push(kind);
      }
      if (command.tags !== undefined) {
        sets.push("tags_json = ?");
        params.push(JSON.stringify(command.tags.filter((tag): tag is string => typeof tag === "string")));
      }
      if (command.platformTags !== undefined) {
        sets.push("platform_tags_json = ?");
        params.push(JSON.stringify(command.platformTags.filter((tag): tag is string => typeof tag === "string")));
      }
      if (command.variants !== undefined) {
        // 整体覆盖候选列表：新增候选时由调用方携带既有候选，避免覆盖已有候选。
        sets.push("variants_json = ?");
        params.push(JSON.stringify(Array.isArray(command.variants) ? command.variants : []));
      }
      if (sets.length === 0) throw new CreationWorkspaceError("invalid-input", "没有要更新的字段。");
      const revision = current.revision + 1;
      sets.push("revision = ?");
      params.push(revision);
      sets.push("updated_at = ?");
      params.push(timestamp);
      params.push(itemId);
      database.prepare(`UPDATE inbox_items SET ${sets.join(", ")} WHERE id = ?`).run(...params);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (NULL, ?, ?, ?)")
        .run("inbox.update", JSON.stringify([{ entity: "inbox", id: itemId, action: "updated", revision }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: "",
        commandType: "inbox.update",
        changes: [{ entity: "inbox", id: itemId, action: "updated", revision }]
      });
      return { commandType: "inbox.update", sequence: Number(logged.lastInsertRowid), itemId, revision, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新收件箱条目。");
    }
  };

  const runInboxDelete = (command: InboxDeleteCommand): InboxItemResult => {
    const itemId = validateId(command.itemId, "条目");
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT revision FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
        .get(itemId) as { revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "收件箱条目不存在。");
      database
        .prepare("UPDATE inbox_items SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(timestamp, timestamp, itemId);
      database.exec("COMMIT");
      return { commandType: "inbox.delete", sequence: 0, itemId, revision: current.revision + 1, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法删除收件箱条目。");
    }
  };

  const runInboxConvertToCard = (command: InboxConvertToCardCommand): InboxConvertToCardResult => {
    const itemId = validateId(command.itemId, "条目");
    const baseRevision = validateBaseRevision(command.baseRevision);
    const projectId = validateId(command.projectId, "作品");
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      host.requireProject(projectId);
      const inboxRow = database
        .prepare("SELECT title, body, tags_json, revision, status FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
        .get(itemId) as { title: string; body: string; tags_json: string; revision: number; status: string } | undefined;
      if (!inboxRow) throw new CreationWorkspaceError("not-found", "收件箱条目不存在。");
      if (inboxRow.revision !== baseRevision) throw new CreationWorkspaceError("revision-mismatch", "收件箱条目已被其他修改更新。");
      if (inboxRow.status === "used") throw new CreationWorkspaceError("conflict", "收件箱条目已转为资料卡。");

      const title = validateTitle(inboxRow.title, "标题", 200);
      const tags = parseJsonArray(inboxRow.tags_json);
      const fieldsJson = JSON.stringify({ note: inboxRow.body.slice(0, 2000) });
      const cardId = `card-${randomUUID()}`;
      database
        .prepare(
          "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, 'reference', ?, '[]', ?, ?, '{}', ?, ?)"
        )
        .run(cardId, projectId, title, fieldsJson, JSON.stringify(tags), timestamp, timestamp);
      host.touchProject(projectId, timestamp);

      const nextRevision = inboxRow.revision + 1;
      database
        .prepare("UPDATE inbox_items SET status = 'used', revision = ?, updated_at = ? WHERE id = ?")
        .run(nextRevision, timestamp, itemId);

      const changes = JSON.stringify([
        { entity: "card", id: cardId, action: "created", revision: 1 },
        { entity: "inbox", id: itemId, action: "updated", revision: nextRevision }
      ]);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "inbox.convertToCard", changes, timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        commandType: "inbox.convertToCard",
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return {
        commandType: "inbox.convertToCard",
        sequence: Number(logged.lastInsertRowid),
        itemId,
        cardId,
        revision: nextRevision,
        updatedAt: timestamp
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法将收件箱条目转为资料卡。");
    }
  };

  return { runInboxList, runInboxCount, runInboxCreate, runInboxUpdate, runInboxDelete, runInboxConvertToCard, inboxFromRow };
}