/**
 * 创作工作区资源域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：附件（resources）登记列表/附加/移除。
 * 通过 Database + ResourceHost（requireProject/emitCommitted）与宿主解耦。
 */

import type Database from "better-sqlite3";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { CreationWorkspaceError } from "./types";
import { validateId } from "./workspace-utils";
import type {
  CreationWorkspaceEvent,
  ResourceAttachCommand,
  ResourceDetachCommand,
  ResourceInfo,
  ResourceListQuery,
  ResourceResult
} from "../../../src/types/creation";

/** 宿主提供的最小校验/副作用回调。 */
export interface ResourceHost {
  requireProject(projectId: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface ResourceModule {
  runResourceList(query: ResourceListQuery): ResourceInfo[];
  runResourceAttach(command: ResourceAttachCommand): ResourceResult;
  runResourceDetach(command: ResourceDetachCommand): ResourceResult;
}

export function createResourceModule(database: Database, host: ResourceHost): ResourceModule {
  const runResourceList = (query: ResourceListQuery): ResourceInfo[] => {
    const projectId = validateId(query.projectId, "作品");
    host.requireProject(projectId);
    const params: unknown[] = [projectId];
    let sql = "SELECT id, project_id, card_id, relative_path, sha256, size, original_name, created_at FROM resources WHERE project_id = ?";
    if (query.cardId) {
      sql += " AND card_id = ?";
      params.push(validateId(query.cardId, "卡片"));
    }
    sql += " ORDER BY created_at, id";
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string;
      card_id: string | null;
      relative_path: string;
      sha256: string;
      size: number;
      original_name: string | null;
      created_at: string;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      cardId: row.card_id,
      relativePath: row.relative_path,
      sha256: row.sha256,
      size: row.size,
      originalName: row.original_name,
      createdAt: row.created_at
    }));
  };

  const runResourceAttach = (command: ResourceAttachCommand): ResourceResult => {
    const projectId = validateId(command.projectId, "作品");
    const cardId = typeof command.cardId === "string" && command.cardId.trim() ? command.cardId.trim() : undefined;
    const relativePath = typeof command.relativePath === "string" ? command.relativePath.trim() : "";
    if (!relativePath || relativePath.length > 500 || relativePath.includes("..") || path.isAbsolute(relativePath)) {
      throw new CreationWorkspaceError("invalid-input", "附件相对路径无效（禁止路径穿越）。");
    }
    const sha256 = typeof command.sha256 === "string" && /^[0-9a-f]{64}$/i.test(command.sha256) ? command.sha256.toLowerCase() : "";
    if (!sha256) throw new CreationWorkspaceError("invalid-input", "附件校验和不合法。");
    const size = command.size;
    if (!Number.isInteger(size) || size < 0 || size > 500 * 1024 * 1024) {
      throw new CreationWorkspaceError("invalid-input", "附件大小超出允许范围。");
    }
    const originalName = typeof command.originalName === "string" && command.originalName.trim()
      ? command.originalName.trim().slice(0, 255)
      : null;
    const resourceId = `resource-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      host.requireProject(projectId);
      if (cardId) {
        const card = database
          .prepare("SELECT project_id FROM cards WHERE id = ? AND deleted_at IS NULL")
          .get(cardId) as { project_id: string } | undefined;
        if (!card) throw new CreationWorkspaceError("not-found", "卡片不存在。");
        if (card.project_id !== projectId) throw new CreationWorkspaceError("invalid-input", "卡片不属于该作品。");
      }
      const existing = database
        .prepare("SELECT id FROM resources WHERE project_id = ? AND relative_path = ?")
        .get(projectId, relativePath);
      if (existing) throw new CreationWorkspaceError("conflict", "相同路径的附件已存在。");
      database
        .prepare("INSERT INTO resources(id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
        .run(resourceId, projectId, cardId ?? null, relativePath, sha256, size, originalName, timestamp);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "resource.attach", JSON.stringify([{ entity: "resource", id: resourceId, action: "created", revision: 1 }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        commandType: "resource.attach",
        changes: [{ entity: "resource", id: resourceId, action: "created", revision: 1 }]
      });
      return { commandType: "resource.attach", sequence: Number(logged.lastInsertRowid), resourceId, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法登记附件。");
    }
  };

  const runResourceDetach = (command: ResourceDetachCommand): ResourceResult => {
    const resourceId = validateId(command.resourceId, "附件");
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT project_id, relative_path FROM resources WHERE id = ?")
        .get(resourceId) as { project_id: string; relative_path: string } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "附件不存在。");
      database.prepare("DELETE FROM resources WHERE id = ?").run(resourceId);
      database.exec("COMMIT");
      return { commandType: "resource.detach", sequence: 0, resourceId, relativePath: current.relative_path, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法移除附件。");
    }
  };

  return { runResourceList, runResourceAttach, runResourceDetach };
}