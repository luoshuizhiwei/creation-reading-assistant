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
  const hasProjectCardLinks = (): boolean =>
    database.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'project_card_links'").get() !== undefined;

  const runResourceList = (query: ResourceListQuery): ResourceInfo[] => {
    const projectId = query.projectId === undefined ? undefined : validateId(query.projectId, "作品");
    const cardId = query.cardId === undefined ? undefined : validateId(query.cardId, "卡片");
    if (!projectId) {
      if (!cardId) throw new CreationWorkspaceError("invalid-input", "全局卡片资源查询必须提供卡片 ID。");
      const card = database.prepare("SELECT 1 FROM cards WHERE id = ? AND deleted_at IS NULL").get(cardId);
      if (!card) throw new CreationWorkspaceError("not-found", "卡片不存在。");
      const rows = database.prepare(`SELECT id, NULL AS project_id, card_id, relative_path, sha256, size, original_name, role, created_at
        FROM global_card_resources WHERE card_id = ? ORDER BY created_at, id`).all(cardId) as Array<{
        id: string; project_id: null; card_id: string; relative_path: string; sha256: string;
        size: number; original_name: string | null; role: "attachment" | "cover"; created_at: string;
      }>;
      return rows.map((row) => ({
        id: row.id, projectId: null, cardId: row.card_id, ownerScope: "card",
        role: row.role,
        relativePath: row.relative_path, sha256: row.sha256, size: row.size,
        originalName: row.original_name, createdAt: row.created_at
      }));
    }
    host.requireProject(projectId);
    const params: unknown[] = [projectId];
    let sql = "SELECT id, project_id, card_id, relative_path, sha256, size, original_name, created_at FROM resources WHERE project_id = ?";
    if (cardId) {
      sql += " AND card_id = ?";
      params.push(cardId);
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
    const projectResources = rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      cardId: row.card_id,
      ownerScope: "project" as const,
      relativePath: row.relative_path,
      sha256: row.sha256,
      size: row.size,
      originalName: row.original_name,
      createdAt: row.created_at
    }));
    if (!hasProjectCardLinks()) return projectResources;
    const globalRows = database.prepare(`SELECT g.id, g.card_id, g.relative_path, g.sha256, g.size, g.original_name, g.role, g.created_at
      FROM global_card_resources g
      JOIN project_card_links pcl ON pcl.card_id = g.card_id
      WHERE pcl.project_id = ?${cardId ? " AND g.card_id = ?" : ""}
      ORDER BY g.created_at, g.id`).all(...(cardId ? [projectId, cardId] : [projectId])) as Array<{
      id: string; card_id: string; relative_path: string; sha256: string; size: number;
      original_name: string | null; role: "attachment" | "cover"; created_at: string;
    }>;
    const globalResources: ResourceInfo[] = globalRows.map((row) => ({
      id: row.id, projectId: null, cardId: row.card_id, ownerScope: "card", role: row.role,
      relativePath: row.relative_path, sha256: row.sha256, size: row.size,
      originalName: row.original_name, createdAt: row.created_at
    }));
    return [...projectResources, ...globalResources].sort((left, right) =>
      left.createdAt.localeCompare(right.createdAt) || left.id.localeCompare(right.id));
  };

  const runResourceAttach = (command: ResourceAttachCommand): ResourceResult => {
    const projectId = command.projectId === undefined ? undefined : validateId(command.projectId, "作品");
    const cardId = typeof command.cardId === "string" && command.cardId.trim() ? command.cardId.trim() : undefined;
    if (!projectId && !cardId) throw new CreationWorkspaceError("invalid-input", "全局卡片资源必须提供卡片 ID。");
    const role = command.role === "cover" ? "cover" : "attachment";
    if (projectId && role !== "attachment") throw new CreationWorkspaceError("invalid-input", "项目附件不支持全局封面角色。");
    const relativePath = typeof command.relativePath === "string" ? command.relativePath.trim() : "";
    if (!relativePath || relativePath.length > 500 || relativePath.includes("..") || path.isAbsolute(relativePath)) {
      throw new CreationWorkspaceError("invalid-input", "附件相对路径无效（禁止路径穿越）。");
    }
    if (!projectId && !relativePath.replaceAll("\\", "/").startsWith(`resources/cards/${cardId}/`)) {
      throw new CreationWorkspaceError("invalid-input", "全局卡片附件必须保存在该卡片的资源目录内。");
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
      if (projectId) host.requireProject(projectId);
      if (cardId) {
        const card = database
          .prepare("SELECT id FROM cards WHERE id = ? AND deleted_at IS NULL")
          .get(cardId) as { id: string } | undefined;
        if (!card) throw new CreationWorkspaceError("not-found", "卡片不存在。");
        if (projectId) {
          const linked = hasProjectCardLinks()
            ? database.prepare("SELECT 1 FROM project_card_links WHERE project_id = ? AND card_id = ?").get(projectId, cardId)
            : database.prepare("SELECT 1 FROM cards WHERE id = ? AND project_id = ?").get(cardId, projectId);
          if (!linked) throw new CreationWorkspaceError("invalid-input", "卡片不属于该作品。");
        }
      }
      const table = projectId ? "resources" : "global_card_resources";
      const existing = database.prepare(`SELECT id FROM ${table} WHERE relative_path = ?`).get(relativePath);
      if (existing) throw new CreationWorkspaceError("conflict", "相同路径的附件已存在。");
      if (!projectId && role === "cover" && database.prepare("SELECT 1 FROM global_card_resources WHERE card_id = ? AND role = 'cover'").get(cardId)) {
        throw new CreationWorkspaceError("conflict", "该卡片已设置封面，请先移除原封面。");
      }
      if (projectId) {
        database.prepare("INSERT INTO resources(id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
          .run(resourceId, projectId, cardId ?? null, relativePath, sha256, size, originalName, timestamp);
      } else {
        database.prepare("INSERT INTO global_card_resources(id, card_id, relative_path, sha256, size, original_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
          .run(resourceId, cardId!, relativePath, sha256, size, originalName, role, timestamp);
      }
      const logged = database.prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId ?? null, "resource.attach", JSON.stringify([{ entity: "resource", id: resourceId, action: "created", revision: 1 }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: projectId ?? null,
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
      const current = database.prepare(`SELECT project_id, relative_path, 'project' AS owner_scope FROM resources WHERE id = ?
        UNION ALL SELECT NULL AS project_id, relative_path, 'card' AS owner_scope FROM global_card_resources WHERE id = ?`)
        .get(resourceId, resourceId) as { project_id: string | null; relative_path: string; owner_scope: "project" | "card" } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "附件不存在。");
      database.prepare(`DELETE FROM ${current.owner_scope === "card" ? "global_card_resources" : "resources"} WHERE id = ?`).run(resourceId);
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
