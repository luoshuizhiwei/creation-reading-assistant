/**
 * 创作工作区批注域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：批注列表/创建/更新/软删除/重新定位，以及正文锚点解析。
 * 通过 Database + AnnotationHost（requireProject/emitCommitted）与宿主解耦。
 */

import type Database from "better-sqlite3";
import { randomUUID } from "node:crypto";
import { CreationWorkspaceError } from "./types";
import { isRecord, validateBaseRevision, validateId } from "./workspace-utils";
import type {
  Annotation,
  AnnotationAnchor,
  AnnotationCreateCommand,
  AnnotationDeleteCommand,
  AnnotationListQuery,
  AnnotationReanchorCommand,
  AnnotationResult,
  AnnotationUpdateCommand,
  CreationDocument,
  CreationWorkspaceEvent
} from "../../../src/types/creation";

/** 宿主提供的最小校验/副作用回调。 */
export interface AnnotationHost {
  requireProject(projectId: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface AnnotationModule {
  runAnnotationList(query: AnnotationListQuery): Annotation[];
  runAnnotationCreate(command: AnnotationCreateCommand): AnnotationResult;
  runAnnotationUpdate(command: AnnotationUpdateCommand): AnnotationResult;
  runAnnotationDelete(command: AnnotationDeleteCommand): AnnotationResult;
  runAnnotationReanchor(command: AnnotationReanchorCommand): AnnotationResult;
  resolveAnnotationAnchor(bodyJson: string, anchor: AnnotationAnchor): { anchoredText: string; anchorInvalid: boolean };
}

/** 解析批注锚点对应的当前文本；锚点越界或锚定文本已变化时标记失效（不静默删除）。 */
export function resolveAnnotationAnchor(
  bodyJson: string,
  anchor: AnnotationAnchor
): { anchoredText: string; anchorInvalid: boolean } {
  if (!bodyJson) return { anchoredText: "", anchorInvalid: true };
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return { anchoredText: "", anchorInvalid: true };
  }
  const block = document.content?.[anchor.blockIndex];
  if (!isRecord(block) || !Array.isArray(block.content)) return { anchoredText: "", anchorInvalid: true };
  const parts: string[] = [];
  const collect = (nodes: unknown[]): void => {
    for (const node of nodes) {
      if (!isRecord(node)) continue;
      if (node.type === "text" && typeof node.text === "string") parts.push(node.text);
      else if (Array.isArray(node.content)) collect(node.content);
    }
  };
  collect(block.content);
  const text = parts.join("");
  if (anchor.textOffset < 0 || anchor.textOffset + anchor.textLength > text.length) {
    return { anchoredText: "", anchorInvalid: true };
  }
  const anchoredText = text.slice(anchor.textOffset, anchor.textOffset + anchor.textLength);
  if (anchor.text !== undefined && anchor.text !== anchoredText) {
    return { anchoredText: "", anchorInvalid: true };
  }
  return { anchoredText, anchorInvalid: false };
}

export function createAnnotationModule(database: Database, host: AnnotationHost): AnnotationModule {
  const hasProjectCardLinks = (): boolean =>
    database.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'project_card_links'").get() !== undefined;

  const runAnnotationList = (query: AnnotationListQuery): Annotation[] => {
    const projectId = validateId(query.projectId, "作品");
    host.requireProject(projectId);
    const limit = query.limit === undefined ? 200 : query.limit;
    if (!Number.isInteger(limit) || limit < 1 || limit > 2000) {
      throw new CreationWorkspaceError("invalid-input", "批注返回上限必须为 1..2000 的整数。");
    }
    const params: unknown[] = [projectId];
    let sql =
      "SELECT id, scene_id, card_id, anchor_json, note, status, revision, created_at, updated_at FROM annotations WHERE project_id = ? AND deleted_at IS NULL";
    if (query.sceneId) {
      sql += " AND scene_id = ?";
      params.push(validateId(query.sceneId, "场景"));
    }
    sql += " ORDER BY updated_at DESC, id DESC LIMIT ?";
    params.push(limit);
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      scene_id: string;
      card_id: string | null;
      anchor_json: string;
      note: string;
      status: string;
      revision: number;
      created_at: string;
      updated_at: string;
    }>;
    const sceneBodies = new Map<string, string>();
    return rows.map((row) => {
      let anchor: AnnotationAnchor;
      try {
        anchor = JSON.parse(row.anchor_json) as AnnotationAnchor;
      } catch {
        anchor = { blockIndex: -1, textOffset: 0, textLength: 0 };
      }
      let bodyJson = sceneBodies.get(row.scene_id);
      if (bodyJson === undefined) {
        bodyJson =
          (
            database
              .prepare("SELECT body_json FROM scenes WHERE id = ? AND deleted_at IS NULL")
              .get(row.scene_id) as { body_json: string } | undefined
          )?.body_json ?? "";
        sceneBodies.set(row.scene_id, bodyJson);
      }
      const { anchoredText, anchorInvalid } = resolveAnnotationAnchor(bodyJson, anchor);
      return {
        id: row.id,
        projectId,
        sceneId: row.scene_id,
        cardId: row.card_id,
        anchor,
        anchorInvalid,
        note: row.note,
        status: row.status === "resolved" ? "resolved" : "open",
        anchoredText,
        revision: row.revision,
        createdAt: row.created_at,
        updatedAt: row.updated_at
      };
    });
  };

  const runAnnotationCreate = (command: AnnotationCreateCommand): AnnotationResult => {
    const projectId = validateId(command.projectId, "作品");
    const sceneId = validateId(command.sceneId, "场景");
    const note = typeof command.note === "string" ? command.note : "";
    if (note.length > 2000) throw new CreationWorkspaceError("invalid-input", "批注内容不能超过 2000 个字符。");
    const cardId = typeof command.cardId === "string" && command.cardId.trim() ? command.cardId.trim() : undefined;
    const anchor = command.anchor as unknown as AnnotationAnchor | null;
    if (
      !anchor ||
      typeof anchor.blockIndex !== "number" ||
      !Number.isInteger(anchor.blockIndex) ||
      anchor.blockIndex < 0 ||
      typeof anchor.textOffset !== "number" ||
      anchor.textOffset < 0 ||
      typeof anchor.textLength !== "number" ||
      anchor.textLength < 1
    ) {
      throw new CreationWorkspaceError("invalid-input", "批注锚点无效。");
    }
    const status = command.status === "resolved" ? "resolved" : "open";
    const annotationId = `annotation-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const scene = database
        .prepare(
          "SELECT c.project_id, s.body_json FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ? AND s.deleted_at IS NULL"
        )
        .get(sceneId) as { project_id: string; body_json: string } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (scene.project_id !== projectId) throw new CreationWorkspaceError("invalid-input", "场景不属于该作品。");
      if (cardId) {
        const card = database
          .prepare("SELECT id FROM cards WHERE id = ? AND deleted_at IS NULL")
          .get(cardId) as { id: string } | undefined;
        if (!card) throw new CreationWorkspaceError("not-found", "关联卡片不存在。");
        const linked = hasProjectCardLinks()
          ? database.prepare("SELECT 1 FROM project_card_links WHERE project_id = ? AND card_id = ?").get(projectId, cardId)
          : database.prepare("SELECT 1 FROM cards WHERE id = ? AND project_id = ?").get(cardId, projectId);
        if (!linked) throw new CreationWorkspaceError("invalid-input", "关联卡片不属于该作品。");
      }
      const anchored = resolveAnnotationAnchor(scene.body_json, anchor);
      if (anchored.anchorInvalid) {
        throw new CreationWorkspaceError("invalid-input", "批注锚点未命中正文文本。");
      }
      const storedAnchor: AnnotationAnchor = { ...anchor, text: anchored.anchoredText };
      database
        .prepare(
          "INSERT INTO annotations(id, project_id, scene_id, card_id, anchor_json, note, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(
          annotationId,
          projectId,
          sceneId,
          cardId ?? null,
          JSON.stringify(storedAnchor),
          note,
          status,
          timestamp,
          timestamp
        );
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(
          projectId,
          "annotation.create",
          JSON.stringify([{ entity: "annotation", id: annotationId, action: "created", revision: 1 }]),
          timestamp
        );
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        commandType: "annotation.create",
        changes: [{ entity: "annotation", id: annotationId, action: "created", revision: 1 }]
      });
      return {
        commandType: "annotation.create",
        sequence: Number(logged.lastInsertRowid),
        annotationId,
        revision: 1,
        updatedAt: timestamp
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法创建批注。");
    }
  };

  const runAnnotationUpdate = (command: AnnotationUpdateCommand): AnnotationResult => {
    const annotationId = validateId(command.annotationId, "批注");
    const baseRevision = validateBaseRevision(command.baseRevision);
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT project_id, revision FROM annotations WHERE id = ? AND deleted_at IS NULL")
        .get(annotationId) as { project_id: string; revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "批注不存在。");
      if (current.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "批注已被其他修改更新。");
      }
      const sets: string[] = [];
      const params: unknown[] = [];
      if (command.note !== undefined) {
        if (command.note.length > 2000) throw new CreationWorkspaceError("invalid-input", "批注内容不能超过 2000 个字符。");
        sets.push("note = ?");
        params.push(command.note);
      }
      if (command.status !== undefined) {
        if (command.status !== "open" && command.status !== "resolved") {
          throw new CreationWorkspaceError("invalid-input", "批注状态无效。");
        }
        sets.push("status = ?");
        params.push(command.status);
      }
      if (command.cardId !== undefined) {
        if (command.cardId !== null) {
          const card = database
            .prepare("SELECT id FROM cards WHERE id = ? AND deleted_at IS NULL")
            .get(command.cardId) as { id: string } | undefined;
          if (!card) throw new CreationWorkspaceError("not-found", "关联卡片不存在。");
          const linked = hasProjectCardLinks()
            ? database
                .prepare("SELECT 1 FROM project_card_links WHERE project_id = ? AND card_id = ?")
                .get(current.project_id, command.cardId)
            : database
                .prepare("SELECT 1 FROM cards WHERE id = ? AND project_id = ?")
                .get(command.cardId, current.project_id);
          if (!linked) throw new CreationWorkspaceError("invalid-input", "关联卡片不属于该作品。");
        }
        sets.push("card_id = ?");
        params.push(command.cardId);
      }
      if (sets.length === 0) throw new CreationWorkspaceError("invalid-input", "没有要更新的字段。");
      const revision = current.revision + 1;
      sets.push("revision = ?");
      params.push(revision);
      sets.push("updated_at = ?");
      params.push(timestamp);
      params.push(annotationId);
      database.prepare(`UPDATE annotations SET ${sets.join(", ")} WHERE id = ?`).run(...params);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(
          current.project_id,
          "annotation.update",
          JSON.stringify([{ entity: "annotation", id: annotationId, action: "updated", revision }]),
          timestamp
        );
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: current.project_id,
        commandType: "annotation.update",
        changes: [{ entity: "annotation", id: annotationId, action: "updated", revision }]
      });
      return {
        commandType: "annotation.update",
        sequence: Number(logged.lastInsertRowid),
        annotationId,
        revision,
        updatedAt: timestamp
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新批注。");
    }
  };

  const runAnnotationDelete = (command: AnnotationDeleteCommand): AnnotationResult => {
    const annotationId = validateId(command.annotationId, "批注");
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT project_id, revision FROM annotations WHERE id = ? AND deleted_at IS NULL")
        .get(annotationId) as { project_id: string; revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "批注不存在。");
      database
        .prepare("UPDATE annotations SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(timestamp, timestamp, annotationId);
      database.exec("COMMIT");
      return {
        commandType: "annotation.delete",
        sequence: 0,
        annotationId,
        revision: current.revision + 1,
        updatedAt: timestamp
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法删除批注。");
    }
  };

  const runAnnotationReanchor = (command: AnnotationReanchorCommand): AnnotationResult => {
    const annotationId = validateId(command.annotationId, "批注");
    const baseRevision = validateBaseRevision(command.baseRevision);
    const anchor = command.anchor as unknown as AnnotationAnchor | null;
    if (
      !anchor ||
      typeof anchor.blockIndex !== "number" ||
      !Number.isInteger(anchor.blockIndex) ||
      anchor.blockIndex < 0 ||
      typeof anchor.textOffset !== "number" ||
      anchor.textOffset < 0 ||
      typeof anchor.textLength !== "number" ||
      anchor.textLength < 1
    ) {
      throw new CreationWorkspaceError("invalid-input", "批注锚点无效。");
    }
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare("SELECT project_id, scene_id, revision FROM annotations WHERE id = ? AND deleted_at IS NULL")
        .get(annotationId) as {
        project_id: string;
        scene_id: string;
        revision: number;
      } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "批注不存在。");
      if (current.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "批注已被其他修改更新。");
      }
      const scene = database
        .prepare("SELECT body_json FROM scenes WHERE id = ? AND deleted_at IS NULL")
        .get(current.scene_id) as {
        body_json: string;
      } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "批注所属场景不存在。");
      const anchored = resolveAnnotationAnchor(scene.body_json, anchor);
      if (anchored.anchorInvalid) {
        throw new CreationWorkspaceError("invalid-input", "新锚点未命中当前正文文本。");
      }
      const storedAnchor: AnnotationAnchor = { ...anchor, text: anchored.anchoredText };
      const revision = current.revision + 1;
      database
        .prepare("UPDATE annotations SET anchor_json = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(JSON.stringify(storedAnchor), timestamp, revision, annotationId);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(
          current.project_id,
          "annotation.reanchor",
          JSON.stringify([{ entity: "annotation", id: annotationId, action: "updated", revision }]),
          timestamp
        );
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: current.project_id,
        commandType: "annotation.reanchor",
        changes: [{ entity: "annotation", id: annotationId, action: "updated", revision }]
      });
      return {
        commandType: "annotation.reanchor",
        sequence: Number(logged.lastInsertRowid),
        annotationId,
        revision,
        updatedAt: timestamp
      };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法重新定位批注。");
    }
  };

  return {
    runAnnotationList,
    runAnnotationCreate,
    runAnnotationUpdate,
    runAnnotationDelete,
    runAnnotationReanchor,
    resolveAnnotationAnchor
  };
}
