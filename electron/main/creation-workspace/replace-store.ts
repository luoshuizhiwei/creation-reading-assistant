/**
 * 替换计划存储域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 * 
 * 职责：为全项目替换装配 ReplacePlanStore —— 场景批量读取、正文写回与统计、
 * 快照与变更日志落库、计划 JSON 文件的读写与一次性消费标记。
 * 纯移动式切片：仅需 Database 与工作区目录参数，无宿主回调。
 */

import { mkdirSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import path from "node:path";
import type Database from "better-sqlite3";
import type { ReplacePlan, ReplacePlanStore } from "./replace-plan";
import { countSceneBodyStats } from "./scene-stats";

export interface ReplaceStoreModule {
  buildReplacePlanStore(): ReplacePlanStore;
}

export function createReplaceStoreModule(database: Database, workspaceDirectory: string): ReplaceStoreModule {

  /** 全项目替换计划 store：scenes/snapshots/change_log 走 DB，计划本体落 JSON 文件。 */
  function buildReplacePlanStore(): ReplacePlanStore {
    const listSql = `
      SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
      FROM scenes s JOIN chapters c ON c.id = s.chapter_id
      WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?`;
    const getSceneStmt = database.prepare(
      "SELECT body_json, revision, updated_at FROM scenes WHERE id = ? AND deleted_at IS NULL"
    );
    const updateScene = database.prepare(
      "UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?, revision = ? WHERE id = ?"
    );
    const insertSnapshot = database.prepare(
      "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
    );
    const insertChangeLog = database.prepare(
      "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
    );
    const plansDir = path.join(workspaceDirectory, "replace-plans");
    mkdirSync(plansDir, { recursive: true });
    const usedPlanIds = new Set<string>();
    const readPlanFile = (planId: string): ReplacePlan | undefined => {
      try {
        const raw = readFileSync(path.join(plansDir, `${planId}.json`), "utf8");
        return JSON.parse(raw) as ReplacePlan;
      } catch {
        return undefined;
      }
    };
    return {
      listScopeScenes: (projectId, scope, scopeId) => {
        let sql = listSql;
        const params: unknown[] = [projectId];
        if (scope === "chapter" && scopeId) {
          sql = `
            SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
            FROM scenes s JOIN chapters c ON c.id = s.chapter_id
            WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.chapter_id = ?`;
          params[0] = scopeId;
        } else if (scope === "scene" && scopeId) {
          sql = `
            SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
            FROM scenes s JOIN chapters c ON c.id = s.chapter_id
            WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.id = ?`;
          params[0] = scopeId;
        }
        const rows = database.prepare(sql).all(...params) as Array<{
          id: string;
          project_id: string;
          chapter_id: string;
          chapter_title: string;
          title: string;
          body_json: string;
          revision: number;
          updated_at: string;
        }>;
        return rows.map((r) => ({
          id: r.id,
          projectId: r.project_id,
          chapterId: r.chapter_id,
          chapterTitle: r.chapter_title,
          title: r.title,
          bodyJson: r.body_json,
          revision: r.revision,
          updatedAt: r.updated_at
        }));
      },
      getScene: (id) => {
        const row = getSceneStmt.get(id) as { body_json: string; revision: number; updated_at: string } | undefined;
        if (!row) return undefined;
        return { bodyJson: row.body_json, revision: row.revision, updatedAt: row.updated_at };
      },
      savePlan: (plan) => {
        writeFileSync(path.join(plansDir, `${plan.planId}.json`), JSON.stringify(plan), "utf8");
      },
      loadPlan: (planId) => readPlanFile(planId),
      deletePlan: (planId) => {
        try {
          unlinkSync(path.join(plansDir, `${planId}.json`));
        } catch {
          /* 已不存在 */
        }
      },
      isPlanUsed: (planId) => usedPlanIds.has(planId),
      markPlanUsed: (planId) => {
        usedPlanIds.add(planId);
        try {
          unlinkSync(path.join(plansDir, `${planId}.json`));
        } catch {
          /* 已不存在 */
        }
      },
      beginTransaction: () => database.exec("BEGIN IMMEDIATE"),
      commitTransaction: () => database.exec("COMMIT"),
      rollbackTransaction: () => database.exec("ROLLBACK"),
      updateSceneBody: (id, bodyJson, newRevision, timestamp) => {
        const stats = countSceneBodyStats(bodyJson);
        updateScene.run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, newRevision, id);
      },
      insertSnapshot: (id, projectId, subjectId, payloadJson, createdAt) =>
        insertSnapshot.run(id, projectId, "scene", subjectId, payloadJson, createdAt),
      insertChangeLog: (projectId, commandType, changesJson, committedAt) =>
        Number(insertChangeLog.run(projectId, commandType, changesJson, committedAt).lastInsertRowid)
    };
  }

  return {
    buildReplacePlanStore,
  };
}
