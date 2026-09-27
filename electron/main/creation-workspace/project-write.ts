/**
 * 项目写入域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 * 
 * 职责：新建作品的单事务落库，以及导入草稿（章节/场景树）的批量写入事务。
 * 提交事件由宿主发出；其余全部在本域内完成。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type CreateProjectCommand,
  type CreateProjectResult,
  type CreationWorkspaceEvent,
  type ProjectImportDraftCommand,
  type ProjectImportDraftResult
} from "./types";
import { normalizeProjectSetup } from "./project-setup";
import { plainTextToSceneDocument } from "./workspace-utils";
import { countSceneBodyStats } from "./scene-stats";

export interface ProjectWriteHost {
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface ProjectWriteModule {
  createProjectTransaction(command: CreateProjectCommand): CreateProjectResult;
  importDraftTransaction(command: ProjectImportDraftCommand): ProjectImportDraftResult;
}

export function createProjectWriteModule(database: Database, host: ProjectWriteHost): ProjectWriteModule {

  function createProjectTransaction(command: CreateProjectCommand): CreateProjectResult {
    const runtimeTitle = (command as unknown as { title?: unknown }).title;
    if (typeof runtimeTitle !== "string") {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为文本。");
    }
    const title = runtimeTitle.trim();
    if (!title || title.length > 200) {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为 1 至 200 个字符。");
    }
    const setup = normalizeProjectSetup((command as unknown as { setup?: unknown }).setup);
    const setupJson = JSON.stringify(setup);

    const projectId = `project-${randomUUID()}`;
    const volumeId = `volume-${randomUUID()}`;
    const chapterId = `chapter-${randomUUID()}`;
    const sceneId = `scene-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run(projectId, title, setupJson, timestamp, timestamp);
      database
        .prepare(
          "INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
        )
        .run(volumeId, projectId, "正文", 0, timestamp, timestamp);
      database
        .prepare(
          "INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(chapterId, projectId, volumeId, "第一章", 0, timestamp, timestamp);
      database
        .prepare(
          "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(sceneId, chapterId, "默认场景", 0, '{"type":"doc","content":[]}', timestamp, timestamp);
      const changes = JSON.stringify([
        { entity: "project", id: projectId, action: "created", revision: 1 },
        { entity: "volume", id: volumeId, action: "created", revision: 1 },
        { entity: "chapter", id: chapterId, action: "created", revision: 1 },
        { entity: "scene", id: sceneId, action: "created", revision: 1 }
      ]);
      const logged = database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, command.type, changes, timestamp);
      database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId,
        volumeId,
        chapterId,
        sceneId
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
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
      if (typeof error === "object" && error !== null && "code" in error && String(error.code).startsWith("SQLITE_CONSTRAINT")) {
        throw new CreationWorkspaceError("conflict", "无法创建作品，稳定标识发生冲突。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交创作工作区事务。");
    }
  }

  function importDraftTransaction(command: ProjectImportDraftCommand): ProjectImportDraftResult {
    const runtimeTitle = (command as unknown as { title?: unknown }).title;
    if (typeof runtimeTitle !== "string") {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为文本。");
    }
    const title = runtimeTitle.trim();
    if (!title || title.length > 200) {
      throw new CreationWorkspaceError("invalid-input", "作品名称必须为 1 至 200 个字符。");
    }
    const setup = normalizeProjectSetup((command as unknown as { setup?: unknown }).setup);
    const volumes = Array.isArray(command.volumes) ? command.volumes : [];
    if (volumes.length === 0) {
      throw new CreationWorkspaceError("invalid-input", "导入内容不能为空。");
    }
    if (volumes.length > 100) {
      throw new CreationWorkspaceError("invalid-input", "导入卷数不能超过 100。");
    }
    let totalChapters = 0;
    let totalScenes = 0;
    let totalBytes = 0;
    for (const volume of volumes) {
      if (typeof volume?.title !== "string" || !volume.title.trim()) {
        throw new CreationWorkspaceError("invalid-input", "卷标题不能为空。");
      }
      if (volume.title.trim().length > 200) {
        throw new CreationWorkspaceError("invalid-input", "卷标题不能超过 200 个字符。");
      }
      if (!Array.isArray(volume.chapters)) {
        throw new CreationWorkspaceError("invalid-input", "卷内容无效。");
      }
      if (volume.chapters.length > 2000) {
        throw new CreationWorkspaceError("invalid-input", "单卷章节数不能超过 2000。");
      }
      for (const chapter of volume.chapters) {
        if (typeof chapter?.title !== "string" || !chapter.title.trim()) {
          throw new CreationWorkspaceError("invalid-input", "章节标题不能为空。");
        }
        if (typeof chapter.body !== "string") {
          throw new CreationWorkspaceError("invalid-input", "章节正文必须为文本。");
        }
        if (chapter.body.length > 5_000_000) {
          throw new CreationWorkspaceError("invalid-input", "章节正文不能超过 5000000 个字符。");
        }
        totalChapters += 1;
        totalScenes += 1;
        totalBytes += chapter.body.length;
        if (totalBytes > 50_000_000) {
          throw new CreationWorkspaceError("invalid-input", "导入内容总量不能超过 5000 万字符。");
        }
      }
    }

    const projectId = `project-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run(projectId, title, JSON.stringify(setup), timestamp, timestamp);
      const insertVolume = database.prepare(
        "INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      const insertChapter = database.prepare(
        "INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
      );
      const insertScene = database.prepare(
        "INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const changes: CreationWorkspaceEvent["changes"] = [
        { entity: "project", id: projectId, action: "created", revision: 1 }
      ];
      let volumeCount = 0;
      let chapterCount = 0;
      volumes.forEach((volume, volumeIndex) => {
        const volumeId = `volume-${randomUUID()}`;
        const volumeTitle = volume.title.trim();
        insertVolume.run(volumeId, projectId, volumeTitle, volumeIndex, timestamp, timestamp);
        volumeCount += 1;
        changes.push({ entity: "volume", id: volumeId, action: "created", revision: 1 });
        volume.chapters.forEach((chapter, chapterIndex) => {
          const chapterId = `chapter-${randomUUID()}`;
          const chapterTitle = chapter.title.trim();
          insertChapter.run(chapterId, projectId, volumeId, chapterTitle, chapterIndex, timestamp, timestamp);
          chapterCount += 1;
          changes.push({ entity: "chapter", id: chapterId, action: "created", revision: 1 });
          const sceneId = `scene-${randomUUID()}`;
          const bodyJson = JSON.stringify(plainTextToSceneDocument(chapter.body));
          const stats = countSceneBodyStats(bodyJson);
          insertScene.run(sceneId, chapterId, "正文", 0, bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp);
          changes.push({ entity: "scene", id: sceneId, action: "created", revision: 1 });
        });
      });
      const logged = database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, command.type, JSON.stringify(changes), timestamp);
      database.exec("COMMIT");
      const result: ProjectImportDraftResult = {
        commandType: "project.importDraft",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        volumeCount,
        chapterCount,
        sceneCount: totalScenes
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "project.importDraft",
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
      if (typeof error === "object" && error !== null && "code" in error && String(error.code).startsWith("SQLITE_CONSTRAINT")) {
        throw new CreationWorkspaceError("conflict", "无法创建作品，稳定标识发生冲突。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交旧稿导入事务。");
    }
  }

  return {
    createProjectTransaction,
    importDraftTransaction,
  };
}
