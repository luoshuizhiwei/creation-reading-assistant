/**
 * 场景写入域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 * 
 * 职责：场景正文落库与修订递增、场景规划（planning）与元信息（标题/摘要/状态）写入。
 * 结构写事务、项目时间戳与提交事件由宿主提供；卡片链接表探测同样经宿主回调转给卡片域。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type CreationWorkspaceEvent,
  type ScenePlanning,
  type SceneUpdateMetaCommand,
  type SceneUpdateMetaResult,
  type SceneUpdatePlanningCommand,
  type SceneUpdatePlanningResult,
  type UpdateSceneBodyCommand,
  type UpdateSceneBodyResult
} from "./types";
import { SCENE_STATUSES, validateBaseRevision, validateId } from "./workspace-utils";
import { parseScenePlanning } from "./bundle-merge";
import { countSceneBodyStats } from "./scene-stats";
import { isValidSceneDocument } from "./validate";

export interface SceneWriteHost {
  emitCommitted(event: CreationWorkspaceEvent): void;
  hasProjectCardLinks(): boolean;
}

export interface SceneWriteModule {
  updateSceneBody(command: UpdateSceneBodyCommand): UpdateSceneBodyResult;
  runSceneUpdatePlanning(command: SceneUpdatePlanningCommand): SceneUpdatePlanningResult;
  runSceneUpdateMeta(command: SceneUpdateMetaCommand): SceneUpdateMetaResult;
}

export function createSceneWriteModule(database: Database, host: SceneWriteHost): SceneWriteModule {

  function runSceneUpdatePlanning(command: SceneUpdatePlanningCommand): SceneUpdatePlanningResult {
    const sceneId = validateId(command.sceneId, "场景");
    const planning = command.planning as unknown as ScenePlanning | null;
    if (!planning || typeof planning !== "object") {
      throw new CreationWorkspaceError("invalid-input", "场景规划字段无效。");
    }
    // 语义：undefined = 保留旧值；null = 明确清空；字符串/数组/数字 = 写入新值。
    const cleaned: ScenePlanning = {};
    if (planning.perspectiveCardId === null) cleaned.perspectiveCardId = null;
    else if (planning.perspectiveCardId !== undefined) cleaned.perspectiveCardId = validateId(planning.perspectiveCardId, "视角卡片");
    if (planning.time === null) cleaned.time = null;
    else if (planning.time !== undefined) {
      if (typeof planning.time !== "string" || planning.time.length > 200) {
        throw new CreationWorkspaceError("invalid-input", "时间描述不能超过 200 个字符。");
      }
      const time = planning.time.trim();
      cleaned.time = time || null;
    }
    if (planning.locationCardId === null) cleaned.locationCardId = null;
    else if (planning.locationCardId !== undefined) cleaned.locationCardId = validateId(planning.locationCardId, "地点卡片");
    if (planning.castCardIds === null) cleaned.castCardIds = null;
    else if (planning.castCardIds !== undefined) {
      if (!Array.isArray(planning.castCardIds) || planning.castCardIds.length > 100) {
        throw new CreationWorkspaceError("invalid-input", "出场卡片数量超出允许范围。");
      }
      cleaned.castCardIds = planning.castCardIds.map((id) => validateId(id, "出场卡片"));
    }
    for (const key of ["goal", "conflict", "outcome", "emotion"] as const) {
      const value = planning[key];
      if (value === null) cleaned[key] = null;
      else if (value !== undefined) {
        if (typeof value !== "string" || value.length > 2000) {
          throw new CreationWorkspaceError("invalid-input", "场景规划文本不能超过 2000 个字符。");
        }
        const trimmed = value.trim();
        cleaned[key] = trimmed || null;
      }
    }
    if (planning.targetWords === null) cleaned.targetWords = null;
    else if (planning.targetWords !== undefined) {
      if (!Number.isInteger(planning.targetWords) || planning.targetWords < 1 || planning.targetWords > 1_000_000) {
        throw new CreationWorkspaceError("invalid-input", "目标字数必须为 1 至 1000000 的整数。");
      }
      cleaned.targetWords = planning.targetWords;
    }
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const scene = database
        .prepare("SELECT c.project_id, c.revision, s.planning_json FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ? AND s.deleted_at IS NULL")
        .get(sceneId) as { project_id: string; revision: number; planning_json: string } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
      const projectId = scene.project_id;
      // 合并语义：未提供的字段保留现有值；null 覆盖为清空。
      const merged: ScenePlanning = { ...parseScenePlanning(scene.planning_json), ...cleaned };
      // 校验卡片引用都属于同一项目（null / 空出场跳过）。
      const cardIds = new Set<string>();
      if (typeof merged.perspectiveCardId === "string") cardIds.add(merged.perspectiveCardId);
      if (typeof merged.locationCardId === "string") cardIds.add(merged.locationCardId);
      if (Array.isArray(merged.castCardIds)) {
        for (const id of merged.castCardIds) cardIds.add(id);
      }
      if (cardIds.size > 0) {
        const placeholders = [...cardIds].map(() => "?").join(", ");
        const existing = database
          .prepare(`SELECT id FROM cards WHERE id IN (${placeholders}) AND deleted_at IS NULL`)
          .all(...cardIds) as Array<{ id: string }>;
        if (existing.length !== cardIds.size) throw new CreationWorkspaceError("not-found", "引用的卡片不存在。");
        const ownershipSql = host.hasProjectCardLinks()
          ? `SELECT card_id AS id FROM project_card_links WHERE card_id IN (${placeholders}) AND project_id = ?`
          : `SELECT id FROM cards WHERE id IN (${placeholders}) AND project_id = ? AND deleted_at IS NULL`;
        const linked = database.prepare(ownershipSql).all(...cardIds, projectId) as Array<{ id: string }>;
        if (linked.length !== cardIds.size) {
          throw new CreationWorkspaceError("invalid-input", "引用的卡片不属于该作品。");
        }
      }
      database
        .prepare("UPDATE scenes SET planning_json = ?, updated_at = ? WHERE id = ?")
        .run(JSON.stringify(merged), timestamp, sceneId);
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "scene.updatePlanning", JSON.stringify([{ entity: "scene", id: sceneId, action: "updated", revision: scene.revision }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        commandType: "scene.updatePlanning",
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision: scene.revision }]
      });
      return { commandType: "scene.updatePlanning", sequence: Number(logged.lastInsertRowid), projectId, sceneId, updatedAt: timestamp };
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新场景规划。");
    }
  }

  function runSceneUpdateMeta(command: SceneUpdateMetaCommand): SceneUpdateMetaResult {
    const sceneId = validateId(command.sceneId, "场景");
    const baseRevision = validateBaseRevision(command.baseRevision);
    if (typeof command.summary !== "string" || command.summary.length > 2000) {
      throw new CreationWorkspaceError("invalid-input", "场景摘要不能超过 2000 个字符。");
    }
    if (!SCENE_STATUSES.has(command.status)) {
      throw new CreationWorkspaceError("invalid-input", "场景状态无效。");
    }
    const summary = command.summary.trim();
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const scene = database
        .prepare(`SELECT s.revision, c.project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id
          WHERE s.id = ? AND s.deleted_at IS NULL AND c.deleted_at IS NULL`)
        .get(sceneId) as { revision: number; project_id: string } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (scene.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再保存摘要与状态。");
      }
      const revision = scene.revision + 1;
      database.prepare(
        "UPDATE scenes SET summary = ?, scene_status = ?, updated_at = ?, revision = ? WHERE id = ?"
      ).run(summary, command.status, timestamp, revision, sceneId);
      const logged = database.prepare(
        "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
      ).run(scene.project_id, "scene.updateMeta", JSON.stringify([{ entity: "scene", id: sceneId, action: "updated", revision }]), timestamp);
      database.exec("COMMIT");
      host.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: scene.project_id,
        commandType: "scene.updateMeta",
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision }]
      });
      return {
        commandType: "scene.updateMeta",
        sequence: Number(logged.lastInsertRowid),
        projectId: scene.project_id,
        sceneId,
        revision,
        updatedAt: timestamp
      };
    } catch (error) {
      try { database.exec("ROLLBACK"); } catch { /* transaction may already be closed */ }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新场景摘要与状态。");
    }
  }

  function updateSceneBody(command: UpdateSceneBodyCommand): UpdateSceneBodyResult {
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
    if (bodyJson.length > 5_000_000) {
      throw new CreationWorkspaceError("invalid-input", "场景正文不能超过 5000000 个字符。");
    }
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = database
        .prepare(
          "SELECT s.revision, s.body_json, c.project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ?"
        )
        .get(command.sceneId) as { revision: number; body_json: string; project_id: string } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (current.revision !== command.baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再保存。");
      }
      const revision = current.revision + 1;
      const stats = countSceneBodyStats(bodyJson);
      // 每场景只保留一条 scene-autosave 快照：先删旧、再写提交前的旧正文与旧 revision。
      database
        .prepare("DELETE FROM snapshots WHERE subject_type = 'scene-autosave' AND subject_id = ?")
        .run(command.sceneId);
      database
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
      database
        .prepare("UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, revision, command.sceneId);
      database
        .prepare("UPDATE projects SET updated_at = ? WHERE id = ?")
        .run(timestamp, current.project_id);
      const changes = JSON.stringify([
        { entity: "scene", id: command.sceneId, action: "updated", revision }
      ]);
      const logged = database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(current.project_id, command.type, changes, timestamp);
      database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId: current.project_id,
        sceneId: command.sceneId,
        revision,
        updatedAt: timestamp
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: current.project_id,
        commandType: command.type,
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return result;
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法提交场景正文事务。");
    }
  }

  return {
    updateSceneBody,
    runSceneUpdatePlanning,
    runSceneUpdateMeta,
  };
}
