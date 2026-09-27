/**
 * 回收站域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：回收站列表与实体定位、还原、彻底清除（含全局卡片排队删除），以及删除影响预览。
 * 通过 Database 窄接口与宿主回调与类主体解耦；结构写事务、项目时间戳与链接表探测由宿主提供。
 */

import type Database from "better-sqlite3";
import {
  queueAndDeleteGlobalCard
} from "./schema";
import {
  CreationWorkspaceError,
  type CreationStructureResult,
  type CreationWorkspaceEvent,
  type ScenePlanning,
  type TrashEntityKind,
  type TrashImpactView,
  type TrashItem,
  type TrashPurgeCommand,
  type TrashRestoreCommand
} from "./types";
import {
  validateId
} from "./workspace-utils";

export interface TrashHost {
  hasProjectCardLinks(): boolean;
  requireProject(projectId: string): void;
  linkedProjectIds(cardId: string): string[];
  runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => { projectId: string | null; entityId: string; revision: number; changes: CreationWorkspaceEvent["changes"] }
  ): CreationStructureResult;
  touchProject(projectId: string, timestamp: string): void;
}

export interface TrashModule {
  trashList(projectId?: string): TrashItem[];
  trashRestore(command: TrashRestoreCommand): CreationStructureResult;
  trashPurge(command: TrashPurgeCommand): CreationStructureResult;
  readTrashImpact(projectId: string | undefined, entity: TrashEntityKind, entityId: string): TrashImpactView;
}

export function createTrashModule(database: Database, host: TrashHost): TrashModule {

  function trashList(projectId?: string): TrashItem[] {
    if (!projectId) {
      if (!host.hasProjectCardLinks()) return [];
      return (database
        .prepare("SELECT id, title, deleted_at, revision FROM cards WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC, id")
        .all() as Array<{ id: string; title: string; deleted_at: string; revision: number }>).map((row) => ({
          entity: "card" as const,
          id: row.id,
          projectId: null,
          title: row.title,
          deletedAt: row.deleted_at,
          revision: row.revision
        }));
    }
    host.requireProject(projectId);
    const items: TrashItem[] = [];
    const volumes = database
      .prepare(
        "SELECT id, title, deleted_at, revision FROM volumes WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id"
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of volumes) {
      items.push({ entity: "volume", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const chapters = database
      .prepare(
        "SELECT id, title, deleted_at, revision FROM chapters WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id"
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of chapters) {
      items.push({ entity: "chapter", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const scenes = database
      .prepare(
        `SELECT s.id, s.title, s.deleted_at, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NOT NULL ORDER BY s.deleted_at DESC, s.id`
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of scenes) {
      items.push({ entity: "scene", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const cards = host.hasProjectCardLinks()
      ? (database.prepare(`SELECT c.id, c.title, c.deleted_at, c.revision, s.linked_project_ids_json
          FROM cards c LEFT JOIN global_card_trash_state s ON s.card_id = c.id
          WHERE c.deleted_at IS NOT NULL ORDER BY c.deleted_at DESC, c.id`).all() as Array<{
            id: string; title: string; deleted_at: string; revision: number; linked_project_ids_json: string | null;
          }>).filter((row) => {
            try {
              return row.linked_project_ids_json !== null && (JSON.parse(row.linked_project_ids_json) as unknown[]).includes(projectId);
            } catch {
              return false;
            }
          })
      : database
        .prepare("SELECT id, title, deleted_at, revision FROM cards WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id")
        .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of cards) {
      items.push({ entity: "card", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    return items;
  }

  function findTrashEntity(
    entity: TrashEntityKind,
    projectId: string | undefined,
    entityId: string
  ): { title: string; revision: number } | undefined {
    if (entity === "card" && host.hasProjectCardLinks()) {
      return database
        .prepare("SELECT title, revision FROM cards WHERE id = ? AND deleted_at IS NOT NULL")
        .get(entityId) as { title: string; revision: number } | undefined;
    }
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "项目回收站操作必须提供作品 ID。");
    if (entity === "volume") {
      const row = database
        .prepare("SELECT title, revision FROM volumes WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    if (entity === "chapter") {
      const row = database
        .prepare("SELECT title, revision FROM chapters WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    if (entity === "scene") {
      const row = database
        .prepare(
          `SELECT s.title, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id
           WHERE s.id = ? AND c.project_id = ? AND s.deleted_at IS NOT NULL`
        )
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    const row = database
      .prepare("SELECT title, revision FROM cards WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
      .get(entityId, projectId) as { title: string; revision: number } | undefined;
    return row;
  }

  function trashRestore(command: TrashRestoreCommand): CreationStructureResult {
    const globalCard = command.entity === "card" && host.hasProjectCardLinks();
    const projectId = globalCard
      ? (command.projectId === undefined ? undefined : validateId(command.projectId, "作品"))
      : validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return host.runStructureTransaction("trash.restore", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      const found = findTrashEntity(command.entity, projectId, entityId);
      if (!found) throw new CreationWorkspaceError("not-found", "回收站中不存在该实体。");
      if (command.entity === "volume") {
        const chapters = database
          .prepare("SELECT id FROM chapters WHERE volume_id = ? AND deleted_at IS NOT NULL")
          .all(entityId) as Array<{ id: string }>;
        for (const chapter of chapters) {
          database
            .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE chapter_id = ?")
            .run(timestamp, chapter.id);
          database
            .prepare("UPDATE chapters SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
            .run(timestamp, chapter.id);
        }
        database
          .prepare("UPDATE volumes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else if (command.entity === "chapter") {
        database
          .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE chapter_id = ?")
          .run(timestamp, entityId);
        database
          .prepare("UPDATE chapters SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else if (command.entity === "scene") {
        database
          .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else {
        const state = host.hasProjectCardLinks()
          ? database.prepare("SELECT linked_project_ids_json FROM global_card_trash_state WHERE card_id = ?")
            .get(entityId) as { linked_project_ids_json: string } | undefined
          : undefined;
        const linkedProjectIds = state ? (JSON.parse(state.linked_project_ids_json) as string[]) : host.linkedProjectIds(entityId);
        database
          .prepare("UPDATE cards SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
        if (host.hasProjectCardLinks()) {
          const insertLink = database.prepare("INSERT OR IGNORE INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)");
          for (const linkedProjectId of linkedProjectIds) {
            const exists = database.prepare("SELECT 1 FROM projects WHERE id = ?").get(linkedProjectId);
            if (exists) {
              insertLink.run(linkedProjectId, entityId, timestamp);
              host.touchProject(linkedProjectId, timestamp);
            }
          }
          database.prepare("DELETE FROM global_card_trash_state WHERE card_id = ?").run(entityId);
        }
      }
      if (projectId && !globalCard) host.touchProject(projectId, timestamp);
      return {
        projectId: globalCard ? null : projectId!,
        entityId,
        revision: found.revision + 1,
        changes: [{ entity: command.entity, id: entityId, action: "restored", revision: found.revision + 1 }]
      };
    });
  }

  function trashPurge(command: TrashPurgeCommand): CreationStructureResult {
    const globalCard = command.entity === "card" && host.hasProjectCardLinks();
    const projectId = globalCard
      ? (command.projectId === undefined ? undefined : validateId(command.projectId, "作品"))
      : validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return host.runStructureTransaction("trash.purge", (timestamp) => {
      if (projectId) host.requireProject(projectId);
      const found = findTrashEntity(command.entity, projectId, entityId);
      if (!found) throw new CreationWorkspaceError("not-found", "回收站中不存在该实体。");
      if (command.entity === "volume") {
        const chapters = database.prepare("SELECT id FROM chapters WHERE volume_id = ?").all(entityId) as Array<{ id: string }>;
        for (const chapter of chapters) {
          database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
        }
        database.prepare("DELETE FROM chapters WHERE volume_id = ?").run(entityId);
        database.prepare("DELETE FROM volumes WHERE id = ?").run(entityId);
      } else if (command.entity === "chapter") {
        database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(entityId);
        database.prepare("DELETE FROM chapters WHERE id = ?").run(entityId);
      } else if (command.entity === "scene") {
        database.prepare("DELETE FROM scenes WHERE id = ?").run(entityId);
      } else {
        if (globalCard) queueAndDeleteGlobalCard(database, entityId, timestamp);
        else {
          database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(entityId, entityId);
          database.prepare("DELETE FROM cards WHERE id = ?").run(entityId);
        }
      }
      if (projectId && !globalCard) host.touchProject(projectId, timestamp);
      return {
        projectId: globalCard ? null : projectId!,
        entityId,
        revision: found.revision,
        changes: [{ entity: command.entity, id: entityId, action: "deleted", revision: found.revision }]
      };
    });
  }

/** 回收站影响预览；无 projectId 的 card 查询也可用于删除前影响确认。 */
  function readTrashImpact(projectId: string | undefined, entity: TrashEntityKind, entityId: string): TrashImpactView {
    if (projectId) host.requireProject(projectId);
    if (!projectId && entity !== "card") throw new CreationWorkspaceError("invalid-input", "项目回收站影响请求必须提供作品 ID。");
    const warnings: string[] = [];
    if (entity === "volume") {
    const volume = database.prepare("SELECT title, deleted_at FROM volumes WHERE id = ? AND project_id = ?").get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
    } | undefined;
    if (!volume)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (volume.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    const chapters = database.prepare("SELECT count(*) AS count FROM chapters WHERE volume_id = ?").get(entityId) as {
        count: number;
    };
    const scenes = database.prepare("SELECT coalesce(sum(s.non_ws_count), 0) AS chars, count(*) AS count FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.volume_id = ?").get(entityId) as {
        chars: number;
        count: number;
    };
    return { title: volume.title, childVolumeCount: 0, childChapterCount: chapters.count, childSceneCount: scenes.count, relatedCardCount: 0, resourceCount: 0, approxChars: scenes.chars, warnings };
} if (entity === "chapter") {
    const chapter = database.prepare("SELECT title, deleted_at FROM chapters WHERE id = ? AND project_id = ?").get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
    } | undefined;
    if (!chapter)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (chapter.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    const scenes = database.prepare("SELECT coalesce(sum(non_ws_count), 0) AS chars, count(*) AS count FROM scenes WHERE chapter_id = ?").get(entityId) as {
        chars: number;
        count: number;
    };
    return { title: chapter.title, childVolumeCount: 0, childChapterCount: 0, childSceneCount: scenes.count, relatedCardCount: 0, resourceCount: 0, approxChars: scenes.chars, warnings };
} if (entity === "scene") {
    const scene = database.prepare(`SELECT s.title, s.deleted_at, s.non_ws_count FROM scenes s JOIN chapters c ON c.id = s.chapter_id           WHERE s.id = ? AND c.project_id = ?`).get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
        non_ws_count: number;
    } | undefined;
    if (!scene)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (scene.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    return { title: scene.title, childVolumeCount: 0, childChapterCount: 0, childSceneCount: 0, relatedCardCount: 0, resourceCount: 0, approxChars: scene.non_ws_count, warnings };
    }
    const cardSql = host.hasProjectCardLinks()
      ? "SELECT title, deleted_at FROM cards WHERE id = ?"
      : "SELECT title, deleted_at FROM cards WHERE id = ? AND project_id = ?";
    const card = database.prepare(cardSql).get(...(host.hasProjectCardLinks() ? [entityId] : [entityId, projectId])) as {
      title: string;
      deleted_at: string | null;
    } | undefined;
    if (!card) throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (projectId && card.deleted_at === null) throw new CreationWorkspaceError("conflict", "对象不在回收站。");

    const relations = database.prepare("SELECT count(*) AS count FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").get(entityId, entityId) as { count: number };
    const projectResources = database.prepare("SELECT count(*) AS count FROM resources WHERE card_id = ?").get(entityId) as { count: number };
    const globalResources = host.hasProjectCardLinks()
      ? database.prepare("SELECT count(*) AS count FROM global_card_resources WHERE card_id = ?").get(entityId) as { count: number }
      : { count: 0 };
    const annotations = database.prepare("SELECT count(*) AS count FROM annotations WHERE card_id = ? AND deleted_at IS NULL").get(entityId) as { count: number };
    const state = host.hasProjectCardLinks()
      ? database.prepare("SELECT linked_project_ids_json FROM global_card_trash_state WHERE card_id = ?").get(entityId) as { linked_project_ids_json: string } | undefined
      : undefined;
    const linkedProjectIds = state ? (JSON.parse(state.linked_project_ids_json) as string[]) : host.linkedProjectIds(entityId);
    const scenes = database.prepare("SELECT id, planning_json FROM scenes WHERE deleted_at IS NULL").all() as Array<{ id: string; planning_json: string }>;
    let sceneReferenceCount = 0;
    for (const scene of scenes) {
      let planning: ScenePlanning;
      try {
        planning = JSON.parse(scene.planning_json) as ScenePlanning;
      } catch {
        throw new CreationWorkspaceError("integrity", `场景 ${scene.id} 的任务卡数据损坏。`);
      }
      if (
        planning.perspectiveCardId === entityId ||
        planning.locationCardId === entityId ||
        (Array.isArray(planning.castCardIds) && planning.castCardIds.includes(entityId))
      ) sceneReferenceCount += 1;
    }
    if (linkedProjectIds.length > 0) warnings.push(`将从 ${linkedProjectIds.length} 个项目移除关联。`);
    if (relations.count > 0 || sceneReferenceCount > 0 || annotations.count > 0) warnings.push("关系、场景引用和批注会保留 30 天，恢复后继续可用。");
    if (projectResources.count + globalResources.count > 0) warnings.push("附件会保留 30 天，到期后才永久删除。");
    return {
      title: card.title,
      childVolumeCount: 0,
      childChapterCount: 0,
      childSceneCount: 0,
      relatedCardCount: relations.count,
      resourceCount: projectResources.count + globalResources.count,
      linkedProjectCount: linkedProjectIds.length,
      sceneReferenceCount,
      annotationCount: annotations.count,
      approxChars: 0,
      warnings
    };
  }
  const TRASH_ENTITY_KINDS = new Set<TrashEntityKind>(["volume", "chapter", "scene", "card"]);

  return { trashList, trashRestore, trashPurge, readTrashImpact };
}
