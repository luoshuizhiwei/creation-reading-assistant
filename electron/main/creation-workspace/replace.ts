/**
 * 创作工作区查找替换域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：范围场景查询、全文替换预览、查找替换事务执行与版本快照自动保存。
 * 通过 Database + ReplaceHost（requireProject/Volume/Chapter/Scene/assertSameProject/touchProject/emitCommitted）与宿主解耦。
 */

import type Database from "better-sqlite3";
import { randomUUID } from "node:crypto";
import { CreationWorkspaceError } from "./types";
import { countSceneBodyStats } from "./scene-stats";
import {
  compileReplaceRegex,
  DEFAULT_REPLACE_PREVIEW_LIMIT,
  extractSceneText,
  isRecord,
  MAX_REPLACE_PREVIEW_LIMIT,
  plainMatcher,
  regexMatcher,
  REPLACE_SCOPES,
  type TextMatcher,
  validateId
} from "./workspace-utils";
import type {
  CreationDocument,
  CreationWorkspaceEvent,
  ReplaceApplyCommand,
  ReplaceApplyResult,
  ReplacePreviewHit,
  ReplacePreviewQuery,
  ReplacePreviewView,
  ReplaceScope
} from "../../../src/types/creation";

/** 宿主提供的最小校验/副作用回调。 */
export interface ReplaceHost {
  requireProject(projectId: string): void;
  requireVolume(volumeId: string): { id: string; project_id: string; title: string; revision: number };
  requireChapter(chapterId: string): {
    id: string;
    project_id: string;
    volume_id: string | null;
    title: string;
    revision: number;
  };
  requireScene(sceneId: string): { id: string; chapter_id: string; project_id: string; revision: number };
  assertSameProject(projectId: string, otherProjectId: string, label: string): void;
  touchProject(projectId: string, timestamp: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface ReplaceModule {
  replaceScopeScenes(
    projectId: string,
    scope: ReplaceScope,
    scopeId: string | undefined
  ): Array<{ id: string; chapter_id: string; title: string; body_json: string; chapter_title: string }>;
  runReplacePreview(query: ReplacePreviewQuery): ReplacePreviewView;
  replaceApply(command: ReplaceApplyCommand): ReplaceApplyResult;
}

export function createReplaceModule(database: Database, host: ReplaceHost): ReplaceModule {
  const replaceScopeScenes = (
    projectId: string,
    scope: ReplaceScope,
    scopeId: string | undefined
  ): Array<{ id: string; chapter_id: string; title: string; body_json: string; chapter_title: string }> => {
    host.requireProject(projectId);
    let sql: string;
    const params: unknown[] = [];
    if (scope === "project") {
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(projectId);
    } else if (scope === "volume") {
      const volumeId = validateId(scopeId, "卷");
      const volume = host.requireVolume(volumeId);
      host.assertSameProject(projectId, volume.project_id, "卷");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.volume_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(volumeId);
    } else if (scope === "chapter") {
      const chapterId = validateId(scopeId, "章节");
      const chapter = host.requireChapter(chapterId);
      host.assertSameProject(projectId, chapter.project_id, "章节");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.chapter_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(chapterId);
    } else {
      const sceneId = validateId(scopeId, "场景");
      const scene = host.requireScene(sceneId);
      host.assertSameProject(projectId, scene.project_id, "场景");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND s.id = ?`;
      params.push(sceneId);
    }
    return database.prepare(sql).all(...params) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      body_json: string;
      chapter_title: string;
    }>;
  };

  const runReplacePreview = (query: ReplacePreviewQuery): ReplacePreviewView => {
    const projectId = validateId(query.projectId, "作品");
    const find = typeof query.find === "string" ? query.find.trim() : "";
    const replaceWith = typeof query.replaceWith === "string" ? query.replaceWith : "";
    if (!find) throw new CreationWorkspaceError("invalid-input", "查找文本不能为空。");
    if (find.length > 500) throw new CreationWorkspaceError("invalid-input", "查找文本不能超过 500 个字符。");
    if (replaceWith.length > 2000) throw new CreationWorkspaceError("invalid-input", "替换文本不能超过 2000 个字符。");
    if (!REPLACE_SCOPES.has(query.scope)) throw new CreationWorkspaceError("invalid-input", "替换范围无效。");
    const limit = query.limit === undefined ? DEFAULT_REPLACE_PREVIEW_LIMIT : query.limit;
    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_REPLACE_PREVIEW_LIMIT) {
      throw new CreationWorkspaceError("invalid-input", `预览场景上限必须为 1..${MAX_REPLACE_PREVIEW_LIMIT} 的整数。`);
    }
    const matcher: TextMatcher = query.regex
      ? regexMatcher(compileReplaceRegex(find), replaceWith)
      : plainMatcher(find, replaceWith);

    const sceneHits: ReplacePreviewHit[] = [];
    let totalHits = 0;
    for (const scene of replaceScopeScenes(projectId, query.scope, query.scopeId)) {
      const plain = extractSceneText(scene.body_json);
      const count = matcher.count(plain);
      if (count === 0) continue;
      totalHits += count;
      sceneHits.push({
        sceneId: scene.id,
        chapterId: scene.chapter_id,
        chapterTitle: scene.chapter_title,
        sceneTitle: scene.title,
        count,
        snippets: matcher.snippets(plain, 5)
      });
      if (sceneHits.length >= limit) break;
    }
    return {
      projectId,
      find,
      replaceWith,
      scope: query.scope,
      sceneHits,
      totalHits,
      matchedScenes: sceneHits.length
    };
  };

  const replaceApply = (command: ReplaceApplyCommand): ReplaceApplyResult => {
    const projectId = validateId(command.projectId, "作品");
    const find = typeof command.find === "string" ? command.find.trim() : "";
    const replaceWith = typeof command.replaceWith === "string" ? command.replaceWith : "";
    if (!find) throw new CreationWorkspaceError("invalid-input", "查找文本不能为空。");
    if (find.length > 500) throw new CreationWorkspaceError("invalid-input", "查找文本不能超过 500 个字符。");
    if (replaceWith.length > 2000) throw new CreationWorkspaceError("invalid-input", "替换文本不能超过 2000 个字符。");
    if (!REPLACE_SCOPES.has(command.scope)) throw new CreationWorkspaceError("invalid-input", "替换范围无效。");
    const matcher: TextMatcher = command.regex
      ? regexMatcher(compileReplaceRegex(command.find!), replaceWith)
      : plainMatcher(find, replaceWith);
    const excludeSet = new Set(
      Array.isArray(command.excludeSceneIds)
        ? command.excludeSceneIds.filter((id): id is string => typeof id === "string" && id.length > 0)
        : []
    );

    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const scenes = replaceScopeScenes(projectId, command.scope, command.scopeId);
      const snapshotIds: string[] = [];
      const changes: CreationWorkspaceEvent["changes"] = [];
      let appliedScenes = 0;
      let appliedHits = 0;
      let skippedScenes = 0;
      const selectBody = database.prepare("SELECT body_json, revision FROM scenes WHERE id = ?");
      const updateScene = database.prepare(
        "UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?, revision = ? WHERE id = ?"
      );
      const insertSnapshot = database.prepare(
        "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      for (const scene of scenes) {
        if (excludeSet.has(scene.id)) {
          skippedScenes += 1;
          continue;
        }
        let document: CreationDocument;
        try {
          document = JSON.parse(scene.body_json) as CreationDocument;
        } catch {
          throw new CreationWorkspaceError("integrity", "场景正文数据损坏。");
        }
        let sceneHits = 0;
        const collect = (nodes: unknown[]): void => {
          for (const node of nodes) {
            if (!isRecord(node)) continue;
            if (node.type === "text" && typeof node.text === "string") {
              const result = matcher.apply(node.text);
              if (result.hits > 0) {
                node.text = result.text;
                sceneHits += result.hits;
              }
            } else if (Array.isArray(node.content)) {
              collect(node.content);
            }
          }
        };
        collect(document.content ?? []);
        if (sceneHits === 0) continue;
        const current = selectBody.get(scene.id) as { body_json: string; revision: number };
        const newRevision = current.revision + 1;
        const snapshotId = `snapshot-${randomUUID()}`;
        insertSnapshot.run(
          snapshotId,
          projectId,
          "scene",
          scene.id,
          JSON.stringify({ reason: "查找替换自动备份", revision: current.revision, body: JSON.parse(current.body_json) }),
          timestamp
        );
        const stats = countSceneBodyStats(JSON.stringify(document));
        updateScene.run(JSON.stringify(document), stats.han, stats.punct, stats.nonWhitespace, timestamp, newRevision, scene.id);
        snapshotIds.push(snapshotId);
        appliedScenes += 1;
        appliedHits += sceneHits;
        changes.push({ entity: "scene", id: scene.id, action: "updated", revision: newRevision });
        changes.push({ entity: "snapshot", id: snapshotId, action: "created", revision: 1 });
      }
      if (appliedScenes === 0) {
        database.exec("ROLLBACK");
        return {
          commandType: "replace.apply",
          sequence: 0,
          projectId,
          appliedScenes: 0,
          appliedHits: 0,
          skippedScenes,
          snapshotIds,
          updatedAt: timestamp
        };
      }
      host.touchProject(projectId, timestamp);
      const logged = database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, "replace.apply", JSON.stringify(changes), timestamp);
      database.exec("COMMIT");
      const result: ReplaceApplyResult = {
        commandType: "replace.apply",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        appliedScenes,
        appliedHits,
        skippedScenes,
        snapshotIds,
        updatedAt: timestamp
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "replace.apply",
        changes
      });
      return result;
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法提交查找替换事务。");
    }
  };

  return { replaceScopeScenes, runReplacePreview, replaceApply };
}
