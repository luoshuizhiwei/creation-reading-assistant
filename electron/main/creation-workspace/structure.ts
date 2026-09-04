/**
 * 大纲与结构域模块（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：卷/章/场景的基础增删改排、状态变更、分卷拆合，以及结构重组的权威预览、受保护应用与原子撤回。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { CreationWorkspaceError } from "./types";
import type {
  ChapterCreateCommand,
  ChapterDeleteCommand,
  ChapterMergeCommand,
  ChapterMoveCommand,
  ChapterNumberingKind,
  ChapterRenameCommand,
  ChapterReorderCommand,
  ChapterSetNumberingCommand,
  ChapterSetStatusCommand,
  ChaptersSetStatusCommand,
  ChapterSplitCommand,
  CreationProjectOutline,
  CreationStructureResult,
  CreationWorkspaceEvent,
  ProtectedStructureCommand,
  SceneCreateCommand,
  SceneDeleteCommand,
  SceneMoveCommand,
  SceneRenameCommand,
  SceneReorderCommand,
  StructureAffectedObject,
  StructureApplyResult,
  StructureApplyWithProtectionCommand,
  StructureCommand,
  StructurePlanRow,
  StructurePreviewCommand,
  StructurePreviewView,
  StructureRevertCommand,
  StructureRevertResult,
  VolumeCreateCommand,
  VolumeDeleteCommand,
  VolumeRenameCommand,
  VolumeReorderCommand
} from "../../../src/types/creation";
import {
  isConstraintError,
  resolveBeforeId,
  validateBaseRevision,
  validateId,
  validateTitle
} from "./workspace-utils";

export const STRUCTURE_COMMAND_TYPES = new Set<string>([
  "volume.create",
  "volume.rename",
  "volume.reorder",
  "volume.delete",
  "chapter.create",
  "chapter.rename",
  "chapter.reorder",
  "chapter.move",
  "chapter.delete",
  "chapter.setStatus",
  "chapter.setNumbering",
  "chapter.split",
  "chapter.merge",
  "chapters.setStatus",
  "scene.create",
  "scene.rename",
  "scene.reorder",
  "scene.move",
  "scene.delete"
]);

const CHAPTER_NUMBERING_KINDS = new Set<ChapterNumberingKind>(["auto", "prologue", "extra", "custom"]);

type StructuralEntityType = "volume" | "chapter" | "scene";

type StructuralRow = {
  type: StructuralEntityType;
  id: string;
  revision: number;
  data: Record<string, string | number | null>;
};

type StructureState = StructuralRow[];

type StructureStateChange = {
  type: StructuralEntityType;
  id: string;
  before: StructuralRow | null;
  after: StructuralRow | null;
};

type StructurePlanRecord = {
  id: string;
  projectId: string;
  command: ProtectedStructureCommand;
  beforeState: StructureState;
  stale: boolean;
  createdAt: number;
};

type StructureProtectionPayload = {
  kind: "structure-operation";
  reason: string;
  command: ProtectedStructureCommand;
  changes: StructureStateChange[];
  appliedAt: string;
  revertedAt?: string;
};

export interface StructureHost {
  requireProject(projectId: string): void;
  requireVolume(volumeId: string): { id: string; project_id: string; title: string; revision: number };
  requireChapter(chapterId: string): { id: string; project_id: string; volume_id: string | null; title: string; revision: number };
  requireScene(sceneId: string): { id: string; chapter_id: string; project_id: string; revision: number };
  assertSameProject(projectId: string, otherProjectId: string, label: string): void;
  touchProject(projectId: string, timestamp: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
  readProjectOutline(projectId: string): CreationProjectOutline | null;
  readWorkflow(projectId: string): string[];
  projectRevision(projectId: string): number;
}

export interface StructureModule {
  executeStructureCommand(command: StructureCommand): CreationStructureResult;
  previewStructure(command: StructurePreviewCommand): Promise<StructurePreviewView>;
  applyStructure(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult>;
  revertStructure(command: StructureRevertCommand): Promise<StructureRevertResult>;
}

export function createStructureModule(database: Database, host: StructureHost): StructureModule {
  const structurePlans = new Map<string, StructurePlanRecord>();
  let protectedStructureTransaction:
    | {
        timestamp: string;
        outcomes: Array<{
          projectId: string;
          entityId: string;
          revision: number;
          changes: CreationWorkspaceEvent["changes"];
        }>;
      }
    | undefined;

  /** 按组重排序号；targetId 不在该组时只重写现有序号（用于跨组移动后的原组收敛）。 */
  function reorderEntityIds(
    table: string,
    whereSql: string,
    params: unknown[],
    targetId: string,
    beforeId: string | undefined
  ): void {
    const rows = database
      .prepare(`SELECT id FROM ${table} WHERE ${whereSql} AND deleted_at IS NULL ORDER BY sort_order, id`)
      .all(...params) as Array<{ id: string }>;
    const ids = rows.map((row) => row.id);
    if (ids.includes(targetId)) {
      const list = ids.filter((id) => id !== targetId);
      if (beforeId !== undefined) {
        const at = list.indexOf(beforeId);
        if (at < 0) throw new CreationWorkspaceError("not-found", "目标位置不存在。");
        list.splice(at, 0, targetId);
      } else {
        list.push(targetId);
      }
      const update = database.prepare(`UPDATE ${table} SET sort_order = ? WHERE id = ?`);
      list.forEach((id, index) => update.run(index, id));
    }
  }

  function requireWorkflowStatus(projectId: string, status: string): void {
    if (!host.readWorkflow(projectId).includes(status)) {
      throw new CreationWorkspaceError("invalid-input", `章节状态“${status}”不在作品工作流中。`);
    }
  }

  function resolveOrCreateDefaultVolume(projectId: string, timestamp: string): string {
    const existing = database
      .prepare("SELECT id FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id LIMIT 1")
      .get(projectId) as { id: string } | undefined;
    if (existing) return existing.id;
    const volumeId = `volume-${randomUUID()}`;
    database
      .prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
      .run(volumeId, projectId, "正文", 0, timestamp, timestamp);
    return volumeId;
  }

  function softDeleteChapterTreeByVolume(volumeId: string, timestamp: string): void {
    const chapters = database
      .prepare("SELECT id FROM chapters WHERE volume_id = ? AND deleted_at IS NULL")
      .all(volumeId) as Array<{ id: string }>;
    for (const chapter of chapters) {
      database
        .prepare(
          "UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE chapter_id = ? AND deleted_at IS NULL"
        )
        .run(timestamp, timestamp, chapter.id);
      database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(timestamp, timestamp, chapter.id);
    }
  }

  function locateOutlineChapter(outline: CreationProjectOutline, chapterId: string) {
    for (const volume of outline.volumes) {
      const chapter = volume.chapters.find((c) => c.id === chapterId);
      if (chapter) return { volume, chapter };
    }
    return undefined;
  }

  function locateOutlineScene(outline: CreationProjectOutline, sceneId: string) {
    for (const volume of outline.volumes) {
      for (const chapter of volume.chapters) {
        const scene = chapter.scenes.find((s) => s.id === sceneId);
        if (scene) return { volume, chapter, scene };
      }
    }
    return undefined;
  }

  function computeStructurePlan(
    outline: CreationProjectOutline,
    command: ProtectedStructureCommand
  ): {
    affected: Array<{ type: "volume" | "chapter" | "scene"; id: string; before: unknown }>;
    rows: StructurePlanRow[];
    softDeletedChapter?: string;
    numberingChange?: string;
    affectedSceneCount: number;
  } {
    const rows: StructurePlanRow[] = [];
    const affected: Array<{ type: "volume" | "chapter" | "scene"; id: string; before: unknown }> = [];
    let softDeletedChapter: string | undefined;
    let numberingChange: string | undefined;
    let affectedSceneCount = 0;

    if (command.type === "chapter.split") {
      const found = locateOutlineScene(outline, command.splitSceneId);
      if (!found) throw new CreationWorkspaceError("not-found", "拆章场景不存在。");
      const { chapter } = found;
      if (chapter.id !== command.chapterId) {
        throw new CreationWorkspaceError("invalid-input", "拆章场景不属于指定章节。");
      }
      const idx = chapter.scenes.findIndex((s) => s.id === command.splitSceneId);
      if (idx <= 0) throw new CreationWorkspaceError("invalid-input", "拆分点必须至少保留一个场景在当前章节。");
      affectedSceneCount = chapter.scenes.slice(idx).length;
      affected.push({ type: "chapter", id: chapter.id, before: chapter });
      rows.push({ label: "源章节", value: chapter.title });
      rows.push({ label: "新章节", value: "（拆分后生成）" });
      rows.push({ label: "受影响场景数", value: String(affectedSceneCount) });
    } else if (command.type === "chapter.merge") {
      const found = locateOutlineChapter(outline, command.sourceChapterId);
      if (!found) throw new CreationWorkspaceError("not-found", "合并源章节不存在。");
      const { chapter } = found;
      const target = locateOutlineChapter(outline, command.targetChapterId);
      if (!target) throw new CreationWorkspaceError("not-found", "合并目标章节不存在。");
      if (chapter.id === target.chapter.id || chapter.volumeId !== target.chapter.volumeId) {
        throw new CreationWorkspaceError("invalid-input", "只能把章节合并到同一卷的其他章节。");
      }
      affectedSceneCount = chapter.scenes.length;
      affected.push({ type: "chapter", id: chapter.id, before: chapter });
      affected.push({ type: "chapter", id: target.chapter.id, before: target.chapter });
      rows.push({ label: "源章节（将被并入）", value: chapter.title });
      rows.push({ label: "目标章节", value: target.chapter.title });
      rows.push({ label: "受影响场景数", value: String(affectedSceneCount) });
      softDeletedChapter = chapter.title;
      rows.push({ label: "软删除章节", value: chapter.title });
    } else if (command.type === "chapter.move") {
      const found = locateOutlineChapter(outline, command.chapterId);
      if (!found) throw new CreationWorkspaceError("not-found", "移动章节不存在。");
      const { chapter } = found;
      const targetVolume = outline.volumes.find((volume) => volume.id === command.targetVolumeId);
      if (!targetVolume) throw new CreationWorkspaceError("not-found", "目标卷不存在。");
      affected.push({ type: "chapter", id: chapter.id, before: chapter });
      for (const s of chapter.scenes) affected.push({ type: "scene", id: s.id, before: s });
      affectedSceneCount = chapter.scenes.length;
      rows.push({ label: "源章节", value: chapter.title });
      rows.push({ label: "目标卷", value: targetVolume.title });
      rows.push({ label: "受影响场景数", value: String(affectedSceneCount) });
    } else if (command.type === "scene.move") {
      const found = locateOutlineScene(outline, command.sceneId);
      if (!found) throw new CreationWorkspaceError("not-found", "移动场景不存在。");
      const { chapter, scene } = found;
      const target = locateOutlineChapter(outline, command.targetChapterId);
      if (!target) throw new CreationWorkspaceError("not-found", "目标章节不存在。");
      affected.push({ type: "scene", id: scene.id, before: scene });
      affected.push({ type: "chapter", id: chapter.id, before: chapter });
      affectedSceneCount = 1;
      rows.push({ label: "源场景", value: scene.title });
      rows.push({ label: "目标章节", value: target.chapter.title });
      rows.push({ label: "受影响场景数", value: "1" });
    } else if (command.type === "chapters.setStatus") {
      for (const id of command.chapterIds) {
        const found = locateOutlineChapter(outline, id);
        if (found) affected.push({ type: "chapter", id, before: found.chapter });
      }
      if (affected.length !== new Set(command.chapterIds).size) {
        throw new CreationWorkspaceError("not-found", "批量状态中的章节不存在。");
      }
      affectedSceneCount = command.chapterIds.reduce(
        (total, id) => total + (locateOutlineChapter(outline, id)?.chapter.scenes.length ?? 0),
        0
      );
      rows.push({ label: "目标章节数", value: String(command.chapterIds.length) });
      rows.push({ label: "新状态", value: command.status });
    } else if (command.type === "chapter.setNumbering") {
      const found = locateOutlineChapter(outline, command.chapterId);
      if (!found) throw new CreationWorkspaceError("not-found", "编号章节不存在。");
      const { chapter } = found;
      affected.push({ type: "chapter", id: chapter.id, before: chapter });
      numberingChange = `${chapter.displayNumber} → ${command.numbering}`;
      rows.push({ label: "章节", value: chapter.title });
      rows.push({ label: "编号变化", value: numberingChange });
    }
    return { affected, rows, softDeletedChapter, numberingChange, affectedSceneCount };
  }

  function readStructureState(projectId: string): StructureState {
    const volumes = database
      .prepare(
        `SELECT id, title, sort_order, revision, deleted_at
         FROM volumes WHERE project_id = ? ORDER BY id`
      )
      .all(projectId) as Array<{ id: string; title: string; sort_order: number; revision: number; deleted_at: string | null }>;
    const chapters = database
      .prepare(
        `SELECT id, volume_id, title, sort_order, status, numbering_kind, custom_number, revision, deleted_at
         FROM chapters WHERE project_id = ? ORDER BY id`
      )
      .all(projectId) as Array<{
        id: string;
        volume_id: string | null;
        title: string;
        sort_order: number;
        status: string;
        numbering_kind: string;
        custom_number: string | null;
        revision: number;
        deleted_at: string | null;
      }>;
    const scenes = database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.sort_order, s.revision, s.deleted_at
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? ORDER BY s.id`
      )
      .all(projectId) as Array<{
        id: string;
        chapter_id: string;
        title: string;
        sort_order: number;
        revision: number;
        deleted_at: string | null;
      }>;
    return [
      ...volumes.map((row): StructuralRow => ({
        type: "volume",
        id: row.id,
        revision: row.revision,
        data: { title: row.title, sortOrder: row.sort_order, deletedAt: row.deleted_at }
      })),
      ...chapters.map((row): StructuralRow => ({
        type: "chapter",
        id: row.id,
        revision: row.revision,
        data: {
          volumeId: row.volume_id,
          title: row.title,
          sortOrder: row.sort_order,
          status: row.status,
          numberingKind: row.numbering_kind,
          customNumber: row.custom_number,
          deletedAt: row.deleted_at
        }
      })),
      ...scenes.map((row): StructuralRow => ({
        type: "scene",
        id: row.id,
        revision: row.revision,
        data: {
          chapterId: row.chapter_id,
          title: row.title,
          sortOrder: row.sort_order,
          deletedAt: row.deleted_at
        }
      }))
    ].sort((left, right) => `${left.type}:${left.id}`.localeCompare(`${right.type}:${right.id}`));
  }

  function structureStateEquals(left: StructureState, right: StructureState): boolean {
    return JSON.stringify(left) === JSON.stringify(right);
  }

  function diffStructureState(before: StructureState, after: StructureState): StructureStateChange[] {
    const beforeByKey = new Map(before.map((row) => [`${row.type}:${row.id}`, row]));
    const afterByKey = new Map(after.map((row) => [`${row.type}:${row.id}`, row]));
    const keys = [...new Set([...beforeByKey.keys(), ...afterByKey.keys()])].sort();
    return keys.flatMap((key) => {
      const oldRow = beforeByKey.get(key) ?? null;
      const newRow = afterByKey.get(key) ?? null;
      if (JSON.stringify(oldRow) === JSON.stringify(newRow)) return [];
      const sample = newRow ?? oldRow;
      return sample ? [{ type: sample.type, id: sample.id, before: oldRow, after: newRow }] : [];
    });
  }

  function affectedRevisions(changes: StructureStateChange[]): StructureAffectedObject[] {
    return changes.map((change) => {
      const row = change.after ?? change.before;
      if (!row) throw new CreationWorkspaceError("integrity", "结构变更缺少对象状态。");
      return { type: change.type, id: change.id, revision: row.revision };
    });
  }

  function structureEventChanges(changes: StructureStateChange[]): CreationWorkspaceEvent["changes"] {
    return changes.map((change) => {
      const beforeDeleted = change.before?.data.deletedAt ?? null;
      const afterDeleted = change.after?.data.deletedAt ?? null;
      const action: CreationWorkspaceEvent["changes"][number]["action"] =
        change.before === null
          ? "created"
          : change.after === null || (beforeDeleted === null && afterDeleted !== null)
            ? "deleted"
            : beforeDeleted !== null && afterDeleted === null
              ? "restored"
              : change.before.data.sortOrder !== change.after.data.sortOrder ||
                  change.before.data.volumeId !== change.after.data.volumeId ||
                  change.before.data.chapterId !== change.after.data.chapterId
                ? "moved"
                : "updated";
      const row = change.after ?? change.before;
      if (!row) throw new CreationWorkspaceError("integrity", "结构事件缺少对象状态。");
      return { entity: change.type, id: change.id, action, revision: row.revision };
    });
  }

  function assertExpectedAffected(
    expected: StructureAffectedObject[],
    changes: StructureStateChange[]
  ): void {
    const normalized = (items: StructureAffectedObject[]) =>
      [...items]
        .map((item) => `${item.type}:${item.id}:${item.revision}`)
        .sort();
    const actual = affectedRevisions(changes);
    if (JSON.stringify(normalized(expected)) !== JSON.stringify(normalized(actual))) {
      throw new CreationWorkspaceError("conflict", "结构对象版本已变化，无法安全撤回。");
    }
  }

  function restoreStructureChanges(changes: StructureStateChange[], timestamp: string): CreationWorkspaceEvent["changes"] {
    const restored: CreationWorkspaceEvent["changes"] = [];
    for (const change of changes) {
      const current = change.after;
      if (!current) throw new CreationWorkspaceError("integrity", "保护快照缺少应用后状态。");
      const nextRevision = current.revision + 1;
      if (change.type === "volume") {
        const before = change.before;
        if (!before) {
          database.prepare("UPDATE volumes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
            .run(timestamp, timestamp, nextRevision, change.id);
        } else {
          database.prepare(
            "UPDATE volumes SET title = ?, sort_order = ?, deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?"
          ).run(before.data.title, before.data.sortOrder, before.data.deletedAt, timestamp, nextRevision, change.id);
        }
      } else if (change.type === "chapter") {
        const before = change.before;
        if (!before) {
          database.prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
            .run(timestamp, timestamp, nextRevision, change.id);
        } else {
          database.prepare(
            `UPDATE chapters SET volume_id = ?, title = ?, sort_order = ?, status = ?, numbering_kind = ?,
             custom_number = ?, deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?`
          ).run(
            before.data.volumeId,
            before.data.title,
            before.data.sortOrder,
            before.data.status,
            before.data.numberingKind,
            before.data.customNumber,
            before.data.deletedAt,
            timestamp,
            nextRevision,
            change.id
          );
        }
      } else {
        const before = change.before;
        if (!before) {
          database.prepare("UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
            .run(timestamp, timestamp, nextRevision, change.id);
        } else {
          database.prepare(
            "UPDATE scenes SET chapter_id = ?, title = ?, sort_order = ?, deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?"
          ).run(
            before.data.chapterId,
            before.data.title,
            before.data.sortOrder,
            before.data.deletedAt,
            timestamp,
            nextRevision,
            change.id
          );
        }
      }
      restored.push({ entity: change.type, id: change.id, action: "restored", revision: nextRevision });
    }
    return restored;
  }

  function runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => {
      projectId: string;
      entityId: string;
      revision: number;
      changes: CreationWorkspaceEvent["changes"];
    }
  ): CreationStructureResult {
    if (protectedStructureTransaction) {
      const outcome = op(protectedStructureTransaction.timestamp);
      protectedStructureTransaction.outcomes.push(outcome);
      return {
        commandType,
        sequence: 0,
        projectId: outcome.projectId,
        entityId: outcome.entityId,
        revision: outcome.revision,
        updatedAt: protectedStructureTransaction.timestamp
      };
    }
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const outcome = op(timestamp);
      const logged = database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(outcome.projectId, commandType, JSON.stringify(outcome.changes), timestamp);
      database.exec("COMMIT");
      const result = {
        commandType,
        sequence: Number(logged.lastInsertRowid),
        projectId: outcome.projectId,
        entityId: outcome.entityId,
        revision: outcome.revision,
        updatedAt: timestamp
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: outcome.projectId,
        commandType,
        changes: outcome.changes
      });
      return result;
    } catch (error) {
      try {
        database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      if (isConstraintError(error)) {
        throw new CreationWorkspaceError("conflict", "创作结构命令违反稳定标识约束。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交创作结构命令事务。");
    }
  }

  function createVolume(command: VolumeCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const title = validateTitle(command.title, "卷名");
    const beforeId = resolveBeforeId(command.beforeVolumeId, "目标卷");
    const volumeId = `volume-${randomUUID()}`;
    return runStructureTransaction("volume.create", (timestamp) => {
      host.requireProject(projectId);
      if (beforeId !== undefined) {
        host.assertSameProject(projectId, host.requireVolume(beforeId).project_id, "目标卷");
      }
      database
        .prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run(volumeId, projectId, title, 0, timestamp, timestamp);
      reorderEntityIds("volumes", "project_id = ?", [projectId], volumeId, beforeId);
      host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: volumeId,
        revision: 1,
        changes: [{ entity: "volume", id: volumeId, action: "created", revision: 1 }]
      };
    });
  }

  function renameVolume(command: VolumeRenameCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    const title = validateTitle(command.title, "卷名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return runStructureTransaction("volume.rename", (timestamp) => {
      const volume = host.requireVolume(volumeId);
      if (volume.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "卷已被更新，请重新读取后再操作。");
      }
      const revision = volume.revision + 1;
      database
        .prepare("UPDATE volumes SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, volumeId);
      host.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "updated", revision }]
      };
    });
  }

  function reorderVolume(command: VolumeReorderCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    const beforeId = resolveBeforeId(command.beforeVolumeId, "目标卷");
    return runStructureTransaction("volume.reorder", (timestamp) => {
      const volume = host.requireVolume(volumeId);
      if (beforeId !== undefined) {
        host.assertSameProject(volume.project_id, host.requireVolume(beforeId).project_id, "目标卷");
      }
      reorderEntityIds("volumes", "project_id = ?", [volume.project_id], volumeId, beforeId);
      const revision = volume.revision + 1;
      database
        .prepare("UPDATE volumes SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, volumeId);
      host.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "moved", revision }]
      };
    });
  }

  function deleteVolume(command: VolumeDeleteCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    return runStructureTransaction("volume.delete", (timestamp) => {
      const volume = host.requireVolume(volumeId);
      softDeleteChapterTreeByVolume(volumeId, timestamp);
      const revision = volume.revision + 1;
      database
        .prepare("UPDATE volumes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, volumeId);
      host.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "deleted", revision }]
      };
    });
  }

  function createChapter(command: ChapterCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const title = validateTitle(command.title, "章节名");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    const chapterId = `chapter-${randomUUID()}`;
    return runStructureTransaction("chapter.create", (timestamp) => {
      host.requireProject(projectId);
      let volumeId: string | null;
      if (command.volumeId !== undefined && command.volumeId !== null) {
        volumeId = validateId(command.volumeId, "卷");
        host.assertSameProject(projectId, host.requireVolume(volumeId).project_id, "卷");
      } else {
        volumeId = resolveOrCreateDefaultVolume(projectId, timestamp);
      }
      if (beforeId !== undefined) {
        const before = host.requireChapter(beforeId);
        if (before.volume_id !== volumeId) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于同一卷。");
        }
      }
      database
        .prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(chapterId, projectId, volumeId, title, 0, timestamp, timestamp);
      if (volumeId) {
        reorderEntityIds("chapters", "volume_id = ?", [volumeId], chapterId, beforeId);
      } else {
        reorderEntityIds("chapters", "project_id = ? AND volume_id IS NULL", [projectId], chapterId, beforeId);
      }
      host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: chapterId,
        revision: 1,
        changes: [{ entity: "chapter", id: chapterId, action: "created", revision: 1 }]
      };
    });
  }

  function renameChapter(command: ChapterRenameCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const title = validateTitle(command.title, "章节名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return runStructureTransaction("chapter.rename", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      const revision = chapter.revision + 1;
      database
        .prepare("UPDATE chapters SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, chapterId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  function reorderChapter(command: ChapterReorderCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    return runStructureTransaction("chapter.reorder", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      const whereSql = chapter.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      const whereParam = chapter.volume_id ?? chapter.project_id;
      if (beforeId !== undefined) {
        const before = host.requireChapter(beforeId);
        if (before.volume_id !== chapter.volume_id) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于同一卷。");
        }
      }
      reorderEntityIds("chapters", whereSql, [whereParam], chapterId, beforeId);
      const revision = chapter.revision + 1;
      database
        .prepare("UPDATE chapters SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, chapterId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "moved", revision }]
      };
    });
  }

  function moveChapter(command: ChapterMoveCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const targetVolumeId = validateId(command.targetVolumeId, "目标卷");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    return runStructureTransaction("chapter.move", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      const targetVolume = host.requireVolume(targetVolumeId);
      host.assertSameProject(chapter.project_id, targetVolume.project_id, "目标卷");
      const oldVolumeId = chapter.volume_id;
      if (beforeId !== undefined) {
        const before = host.requireChapter(beforeId);
        if (before.volume_id !== targetVolumeId) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于目标卷。");
        }
      }
      database
        .prepare("UPDATE chapters SET volume_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(targetVolumeId, timestamp, chapterId);
      if (oldVolumeId) {
        reorderEntityIds("chapters", "volume_id = ?", [oldVolumeId], chapterId, undefined);
      } else {
        reorderEntityIds("chapters", "project_id = ? AND volume_id IS NULL", [chapter.project_id], chapterId, undefined);
      }
      reorderEntityIds("chapters", "volume_id = ?", [targetVolumeId], chapterId, beforeId);
      const revision = chapter.revision + 1;
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "moved", revision }]
      };
    });
  }

  function deleteChapter(command: ChapterDeleteCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    return runStructureTransaction("chapter.delete", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      database
        .prepare(
          "UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE chapter_id = ? AND deleted_at IS NULL"
        )
        .run(timestamp, timestamp, chapter.id);
      const revision = chapter.revision + 1;
      database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, chapterId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "deleted", revision }]
      };
    });
  }

  function setChapterStatus(command: ChapterSetStatusCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const status = validateTitle(command.status, "章节状态", 50);
    const baseRevision = validateBaseRevision(command.baseRevision);
    return runStructureTransaction("chapter.setStatus", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      requireWorkflowStatus(chapter.project_id, status);
      const revision = chapter.revision + 1;
      database
        .prepare("UPDATE chapters SET status = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(status, timestamp, revision, chapterId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  function setChapterNumbering(command: ChapterSetNumberingCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    if (!CHAPTER_NUMBERING_KINDS.has(command.numbering)) {
      throw new CreationWorkspaceError("invalid-input", "章节编号方式无效。");
    }
    const baseRevision = validateBaseRevision(command.baseRevision);
    const customNumber = command.numbering === "custom" ? validateTitle(command.customNumber, "自定义编号", 50) : null;
    return runStructureTransaction("chapter.setNumbering", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      const revision = chapter.revision + 1;
      database
        .prepare("UPDATE chapters SET numbering_kind = ?, custom_number = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(command.numbering, customNumber, timestamp, revision, chapterId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  function createScene(command: SceneCreateCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const title = validateTitle(command.title, "场景名");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    const sceneId = `scene-${randomUUID()}`;
    return runStructureTransaction("scene.create", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      if (beforeId !== undefined) {
        const before = host.requireScene(beforeId);
        if (before.chapter_id !== chapterId) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于同一章节。");
        }
      }
      database
        .prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(sceneId, chapterId, title, 0, '{"type":"doc","content":[]}', timestamp, timestamp);
      reorderEntityIds("scenes", "chapter_id = ?", [chapterId], sceneId, beforeId);
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: sceneId,
        revision: 1,
        changes: [{ entity: "scene", id: sceneId, action: "created", revision: 1 }]
      };
    });
  }

  function renameScene(command: SceneRenameCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const title = validateTitle(command.title, "场景名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return runStructureTransaction("scene.rename", (timestamp) => {
      const scene = host.requireScene(sceneId);
      if (scene.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再操作。");
      }
      const revision = scene.revision + 1;
      database
        .prepare("UPDATE scenes SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, sceneId);
      host.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision }]
      };
    });
  }

  function reorderScene(command: SceneReorderCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    return runStructureTransaction("scene.reorder", (timestamp) => {
      const scene = host.requireScene(sceneId);
      if (beforeId !== undefined) {
        const before = host.requireScene(beforeId);
        if (before.chapter_id !== scene.chapter_id) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于同一章节。");
        }
      }
      reorderEntityIds("scenes", "chapter_id = ?", [scene.chapter_id], sceneId, beforeId);
      const revision = scene.revision + 1;
      database
        .prepare("UPDATE scenes SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, sceneId);
      host.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "moved", revision }]
      };
    });
  }

  function moveScene(command: SceneMoveCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const targetChapterId = validateId(command.targetChapterId, "目标章节");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    return runStructureTransaction("scene.move", (timestamp) => {
      const scene = host.requireScene(sceneId);
      const targetChapter = host.requireChapter(targetChapterId);
      host.assertSameProject(scene.project_id, targetChapter.project_id, "目标章节");
      const oldChapterId = scene.chapter_id;
      if (beforeId !== undefined) {
        const before = host.requireScene(beforeId);
        if (before.chapter_id !== targetChapterId) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于目标章节。");
        }
      }
      database
        .prepare("UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(targetChapterId, timestamp, sceneId);
      reorderEntityIds("scenes", "chapter_id = ?", [oldChapterId], sceneId, undefined);
      reorderEntityIds("scenes", "chapter_id = ?", [targetChapterId], sceneId, beforeId);
      const revision = scene.revision + 1;
      host.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "moved", revision }]
      };
    });
  }

  function deleteScene(command: SceneDeleteCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    return runStructureTransaction("scene.delete", (timestamp) => {
      const scene = host.requireScene(sceneId);
      const revision = scene.revision + 1;
      database
        .prepare("UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, sceneId);
      host.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "deleted", revision }]
      };
    });
  }

  function splitChapter(command: ChapterSplitCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const splitSceneId = validateId(command.splitSceneId, "拆分场景");
    const newChapterTitle =
      command.newChapterTitle === undefined || command.newChapterTitle === null
        ? "新章节"
        : validateTitle(command.newChapterTitle, "新章节名");
    const newChapterId = `chapter-${randomUUID()}`;
    return runStructureTransaction("chapter.split", (timestamp) => {
      const chapter = host.requireChapter(chapterId);
      const splitScene = host.requireScene(splitSceneId);
      if (splitScene.chapter_id !== chapterId) {
        throw new CreationWorkspaceError("invalid-input", "拆分场景不属于该章节。");
      }
      const sceneRows = database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(chapterId) as Array<{ id: string }>;
      const splitIndex = sceneRows.findIndex((row) => row.id === splitSceneId);
      if (splitIndex <= 0) {
        throw new CreationWorkspaceError("invalid-input", "拆分点必须至少保留一个场景在当前章节。");
      }
      const keepScenes = sceneRows.slice(0, splitIndex);
      const movedScenes = sceneRows.slice(splitIndex);
      database
        .prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(newChapterId, chapter.project_id, chapter.volume_id, newChapterTitle, 0, timestamp, timestamp);
      const moveScene = database.prepare(
        "UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const row of movedScenes) moveScene.run(newChapterId, timestamp, row.id);
      const reorderScene = database.prepare("UPDATE scenes SET sort_order = ? WHERE id = ?");
      keepScenes.forEach((row, index) => reorderScene.run(index, row.id));
      movedScenes.forEach((row, index) => reorderScene.run(index, row.id));
      const whereSql = chapter.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      const whereParam = chapter.volume_id ?? chapter.project_id;
      const chapterRows = database
        .prepare(`SELECT id FROM chapters WHERE ${whereSql} AND deleted_at IS NULL ORDER BY sort_order, id`)
        .all(whereParam) as Array<{ id: string }>;
      const order = chapterRows.map((row) => row.id).filter((id) => id !== newChapterId);
      const at = order.indexOf(chapterId);
      order.splice(at + 1, 0, newChapterId);
      const reorderChapter = database.prepare("UPDATE chapters SET sort_order = ? WHERE id = ?");
      order.forEach((id, index) => reorderChapter.run(index, id));
      host.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: newChapterId,
        revision: 1,
        changes: [
          { entity: "chapter", id: newChapterId, action: "created", revision: 1 },
          ...movedScenes.map((row) => ({ entity: "scene", id: row.id, action: "moved" as const, revision: 1 }))
        ]
      };
    });
  }

  function mergeChapters(command: ChapterMergeCommand): CreationStructureResult {
    const sourceChapterId = validateId(command.sourceChapterId, "源章节");
    const targetChapterId = validateId(command.targetChapterId, "目标章节");
    return runStructureTransaction("chapter.merge", (timestamp) => {
      const source = host.requireChapter(sourceChapterId);
      const target = host.requireChapter(targetChapterId);
      if (source.id === target.id) {
        throw new CreationWorkspaceError("invalid-input", "不能把章节合并到自身。");
      }
      if (source.project_id !== target.project_id || source.volume_id !== target.volume_id) {
        throw new CreationWorkspaceError("invalid-input", "只能合并同一卷的章节。");
      }
      const sourceScenes = database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(sourceChapterId) as Array<{ id: string }>;
      const targetScenes = database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(targetChapterId) as Array<{ id: string }>;
      const moveScene = database.prepare(
        "UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const row of sourceScenes) moveScene.run(targetChapterId, timestamp, row.id);
      const reorderScene = database.prepare("UPDATE scenes SET sort_order = ? WHERE id = ?");
      [...targetScenes.map((row) => row.id), ...sourceScenes.map((row) => row.id)].forEach((id, index) =>
        reorderScene.run(index, id)
      );
      const revision = target.revision + 1;
      database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, source.revision + 1, sourceChapterId);
      database
        .prepare("UPDATE chapters SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, targetChapterId);
      const whereSql = target.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      reorderEntityIds("chapters", whereSql, [target.volume_id ?? target.project_id], sourceChapterId, undefined);
      host.touchProject(target.project_id, timestamp);
      return {
        projectId: target.project_id,
        entityId: targetChapterId,
        revision,
        changes: [
          { entity: "chapter", id: sourceChapterId, action: "deleted", revision: source.revision + 1 },
          { entity: "chapter", id: targetChapterId, action: "updated", revision }
        ]
      };
    });
  }

  function batchSetChapterStatus(command: ChaptersSetStatusCommand): CreationStructureResult {
    if (!Array.isArray(command.chapterIds) || command.chapterIds.length === 0) {
      throw new CreationWorkspaceError("invalid-input", "章节列表不能为空。");
    }
    const chapterIds = [...new Set(command.chapterIds.map((id) => validateId(id, "章节")))];
    const status = validateTitle(command.status, "章节状态", 50);
    return runStructureTransaction("chapters.setStatus", (timestamp) => {
      let projectId: string | undefined;
      const changes: CreationWorkspaceEvent["changes"] = [];
      const updateChapter = database.prepare(
        "UPDATE chapters SET status = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const chapterId of chapterIds) {
        const chapter = host.requireChapter(chapterId);
        if (projectId === undefined) {
          projectId = chapter.project_id;
          if (!host.readWorkflow(projectId).includes(status)) {
            throw new CreationWorkspaceError("invalid-input", `章节状态“${status}”不在作品工作流中。`);
          }
        } else if (projectId !== chapter.project_id) {
          throw new CreationWorkspaceError("invalid-input", "批量状态只能作用于同一作品的章节。");
        }
        updateChapter.run(status, timestamp, chapterId);
        changes.push({ entity: "chapter", id: chapterId, action: "updated", revision: chapter.revision + 1 });
      }
      if (projectId === undefined) {
        throw new CreationWorkspaceError("invalid-input", "章节列表不能为空。");
      }
      host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: chapterIds[0] ?? projectId,
        revision: changes[0]?.revision ?? 1,
        changes
      };
    });
  }

  function executeStructureCommand(command: StructureCommand): CreationStructureResult {
    switch (command.type) {
      case "volume.create":
        return createVolume(command);
      case "volume.rename":
        return renameVolume(command);
      case "volume.reorder":
        return reorderVolume(command);
      case "volume.delete":
        return deleteVolume(command);
      case "chapter.create":
        return createChapter(command);
      case "chapter.rename":
        return renameChapter(command);
      case "chapter.reorder":
        return reorderChapter(command);
      case "chapter.move":
        return moveChapter(command);
      case "chapter.delete":
        return deleteChapter(command);
      case "chapter.setStatus":
        return setChapterStatus(command);
      case "chapter.setNumbering":
        return setChapterNumbering(command);
      case "chapter.split":
        return splitChapter(command);
      case "chapter.merge":
        return mergeChapters(command);
      case "chapters.setStatus":
        return batchSetChapterStatus(command);
      case "scene.create":
        return createScene(command);
      case "scene.rename":
        return renameScene(command);
      case "scene.reorder":
        return reorderScene(command);
      case "scene.move":
        return moveScene(command);
      case "scene.delete":
        return deleteScene(command);
    }
  }

  async function previewStructure(command: StructurePreviewCommand): Promise<StructurePreviewView> {
    const projectId = validateId(command.projectId, "作品");
    const outline = host.readProjectOutline(projectId);
    if (!outline) throw new CreationWorkspaceError("not-found", "作品大纲不存在。");
    const plan = computeStructurePlan(outline, command.command);
    let stale = false;
    const baseRev = (command.command as { baseRevision?: number }).baseRevision;
    if (command.command.type === "chapter.setNumbering" && baseRev !== undefined) {
      stale = host.requireChapter(command.command.chapterId).revision !== baseRev;
    }
    const planId = `structure-plan-${randomUUID()}`;
    const beforeState = readStructureState(projectId);
    structurePlans.set(planId, {
      id: planId,
      projectId,
      command: command.command,
      beforeState,
      stale,
      createdAt: Date.now()
    });
    for (const [id, existing] of structurePlans) {
      if (Date.now() - existing.createdAt > 5 * 60_000) structurePlans.delete(id);
    }
    return {
      ok: true,
      planId,
      command: command.command,
      rows: plan.rows,
      stale,
      affectedSceneCount: plan.affectedSceneCount,
      softDeletedChapter: plan.softDeletedChapter,
      numberingChange: plan.numberingChange
    };
  }

  async function applyStructure(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult> {
    const projectId = validateId(command.projectId, "作品");
    const planId = validateId(command.planId, "重组计划");
    const reason = validateTitle(command.protectionReason, "保护原因", 200);
    const plan = structurePlans.get(planId);
    if (!plan || plan.projectId !== projectId || Date.now() - plan.createdAt > 5 * 60_000) {
      structurePlans.delete(planId);
      throw new CreationWorkspaceError("conflict", "重组计划不存在或已过期，请重新预览。");
    }
    if (plan.stale || !structureStateEquals(readStructureState(projectId), plan.beforeState)) {
      structurePlans.delete(planId);
      throw new CreationWorkspaceError("conflict", "结构已变更（stale-plan），请重新预览后再应用。");
    }
    // planId 是一次性能力凭证：无论事务最终成功或失败，一次 apply 尝试后都必须重新预览。
    structurePlans.delete(planId);
    const timestamp = new Date().toISOString();
    const snapshotId = `protective-${randomUUID()}`;
    try {
      database.exec("BEGIN IMMEDIATE");
      if (!structureStateEquals(readStructureState(projectId), plan.beforeState)) {
        throw new CreationWorkspaceError("conflict", "结构已变更（stale-plan），请重新预览后再应用。");
      }
      const initialPayload: StructureProtectionPayload = {
        kind: "structure-operation",
        reason,
        command: plan.command,
        changes: [],
        appliedAt: timestamp
      };
      database.prepare(
        "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
      ).run(snapshotId, projectId, "structure-operation", projectId, JSON.stringify(initialPayload), timestamp);
      protectedStructureTransaction = { timestamp, outcomes: [] };
      executeStructureCommand(plan.command as StructureCommand);
      protectedStructureTransaction = undefined;
      const afterState = readStructureState(projectId);
      const changes = diffStructureState(plan.beforeState, afterState);
      if (changes.length === 0) throw new CreationWorkspaceError("integrity", "重组未产生可保护的结构变更。");
      const payload: StructureProtectionPayload = { ...initialPayload, changes };
      database.prepare("UPDATE snapshots SET payload_json = ? WHERE id = ?")
        .run(JSON.stringify(payload), snapshotId);
      const eventChanges = structureEventChanges(changes);
      const logged = database.prepare(
        "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
      ).run(projectId, "structure.applyWithProtection", JSON.stringify(eventChanges), timestamp);
      database.exec("COMMIT");
      const sequence = Number(logged.lastInsertRowid);
      host.emitCommitted({
        kind: "committed",
        sequence,
        projectId,
        commandType: "structure.applyWithProtection",
        changes: eventChanges
      });
      return {
        ok: true,
        protectionSnapshotId: snapshotId,
        affected: affectedRevisions(changes),
        newRevision: host.projectRevision(projectId)
      };
    } catch (error) {
      protectedStructureTransaction = undefined;
      try { database.exec("ROLLBACK"); } catch { /* transaction may already be closed */ }
      if (error instanceof CreationWorkspaceError) throw error;
      if (isConstraintError(error)) throw new CreationWorkspaceError("conflict", "重组违反结构约束，未应用任何修改。");
      throw new CreationWorkspaceError("integrity", "无法原子应用结构重组。");
    }
  }

  async function revertStructure(command: StructureRevertCommand): Promise<StructureRevertResult> {
    const projectId = validateId(command.projectId, "作品");
    if (!Array.isArray(command.expectedAppliedRevisions) || command.expectedAppliedRevisions.length === 0) {
      throw new CreationWorkspaceError("invalid-input", "撤回需要应用后的对象版本集合。");
    }
    const row = database
      .prepare("SELECT payload_json FROM snapshots WHERE id = ? AND project_id = ? AND subject_type = 'structure-operation'")
      .get(command.protectionSnapshotId, projectId) as { payload_json: string } | undefined;
    if (!row) throw new CreationWorkspaceError("not-found", "保护快照不存在或已被清理。");
    let payload: StructureProtectionPayload;
    try {
      payload = JSON.parse(row.payload_json) as StructureProtectionPayload;
    } catch {
      throw new CreationWorkspaceError("integrity", "保护快照数据损坏。");
    }
    if (payload.kind !== "structure-operation" || !Array.isArray(payload.changes) || payload.changes.length === 0) {
      throw new CreationWorkspaceError("integrity", "保护快照不包含可恢复结构。");
    }
    if (payload.revertedAt) throw new CreationWorkspaceError("conflict", "该保护快照已经撤回。");
    assertExpectedAffected(command.expectedAppliedRevisions, payload.changes);
    const timestamp = new Date().toISOString();
    try {
      database.exec("BEGIN IMMEDIATE");
      const current = new Map(readStructureState(projectId).map((item) => [`${item.type}:${item.id}`, item]));
      for (const change of payload.changes) {
        const actual = current.get(`${change.type}:${change.id}`) ?? null;
        if (JSON.stringify(actual) !== JSON.stringify(change.after)) {
          throw new CreationWorkspaceError("conflict", "应用后的结构已被继续修改，无法安全撤回。");
        }
      }
      const eventChanges = restoreStructureChanges(payload.changes, timestamp);
      host.touchProject(projectId, timestamp);
      payload.revertedAt = timestamp;
      database.prepare("UPDATE snapshots SET payload_json = ? WHERE id = ?")
        .run(JSON.stringify(payload), command.protectionSnapshotId);
      const logged = database.prepare(
        "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
      ).run(projectId, "structure.revert", JSON.stringify(eventChanges), timestamp);
      database.exec("COMMIT");
      const sequence = Number(logged.lastInsertRowid);
      host.emitCommitted({ kind: "committed", sequence, projectId, commandType: "structure.revert", changes: eventChanges });
      return { ok: true, restoredRevision: host.projectRevision(projectId) };
    } catch (error) {
      try { database.exec("ROLLBACK"); } catch { /* transaction may already be closed */ }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法原子撤回结构重组。");
    }
  }

  return {
    executeStructureCommand,
    previewStructure,
    applyStructure,
    revertStructure
  };
}
