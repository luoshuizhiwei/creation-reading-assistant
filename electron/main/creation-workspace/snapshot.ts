/**
 * 快照历史域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：快照列表/创建、对象载荷捕获、差异预览、载荷应用、带保护快照的安全恢复与分层留存。
 * 分类与留存策略仍由 ./snapshot-retention 纯函数模块提供；本模块负责取数、编排与事务。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import type {
  SnapshotRetentionResult
} from "../../../src/types/creation";
import {
  countSceneBodyStats
} from "./scene-stats";
import {
  classifySnapshotMeta,
  planSnapshotRetention
} from "./snapshot-retention";
import {
  CreationWorkspaceError,
  type CreationDocument,
  type CreationStructureResult,
  type CreationWorkspaceEvent,
  type SceneStatus,
  type SnapshotCreateCommand,
  type SnapshotDiffRow,
  type SnapshotInfo,
  type SnapshotPreviewView,
  type SnapshotRestoreWithProtectionCommand,
  type SnapshotRestoreWithProtectionResult,
  type SnapshotSubjectType
} from "./types";
import {
  SCENE_STATUSES,
  extractSceneText,
  isConstraintError,
  isRecord,
  validateId,
  validateTitle
} from "./workspace-utils";

export interface SnapshotHost {
  requireProject(projectId: string): void;
  runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => { projectId: string | null; entityId: string; revision: number; changes: CreationWorkspaceEvent["changes"] }
  ): CreationStructureResult;
  touchProject(projectId: string, timestamp: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
  assertOpen(): void;
}

export interface SnapshotModule {
  snapshotList(query: { projectId: string; subjectType?: SnapshotSubjectType; subjectId?: string }): SnapshotInfo[];
  snapshotCreate(command: SnapshotCreateCommand): CreationStructureResult;
  readSnapshotPreview(projectId: string, snapshotId: string): SnapshotPreviewView | null;
  restoreSnapshotWithProtection(command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult>;
  runSnapshotRetention(): Promise<SnapshotRetentionResult>;
}

export function createSnapshotModule(database: Database, host: SnapshotHost): SnapshotModule {

  function snapshotList(query: { projectId: string; subjectType?: SnapshotSubjectType; subjectId?: string }): SnapshotInfo[] {
    host.requireProject(query.projectId);
    let sql = "SELECT id, project_id, subject_type, subject_id, payload_json, created_at FROM snapshots WHERE project_id = ?";
    const params: unknown[] = [query.projectId];
    if (query.subjectType) {
      sql += " AND subject_type = ?";
      params.push(query.subjectType);
    }
    if (query.subjectId) {
      sql += " AND subject_id = ?";
      params.push(query.subjectId);
    }
    sql += " ORDER BY created_at DESC, id DESC";
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string;
      subject_type: string;
      subject_id: string;
      payload_json: string;
      created_at: string;
    }>;
    return rows.map((row) => {
      let reason = "";
      try {
        reason = (JSON.parse(row.payload_json) as { reason?: string }).reason ?? "";
      } catch {
        // 忽略损坏的 payload，仅 reason 缺失
      }
      return {
        id: row.id,
        projectId: row.project_id,
        subjectType: row.subject_type as SnapshotSubjectType,
        subjectId: row.subject_id,
        reason,
        createdAt: row.created_at
      };
    });
  }

  function snapshotCreate(command: SnapshotCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const supported: SnapshotSubjectType[] = ["scene", "card", "chapter", "volume"];
    if (!supported.includes(command.subjectType)) {
      throw new CreationWorkspaceError("invalid-input", "快照对象类型无效。");
    }
    const subjectId = validateId(command.subjectId, "对象");
    const reason = validateTitle(command.reason, "快照说明", 200);
    const snapshotId = `snapshot-${randomUUID()}`;
    return host.runStructureTransaction("snapshot.create", (timestamp) => {
      host.requireProject(projectId);
      assertSnapshotSubjectOwned(projectId, command.subjectType, subjectId);
      const payload = captureSubjectPayload(command.subjectType, subjectId, reason);
      if (!payload) {
        throw new CreationWorkspaceError("not-found", "快照对象不存在或已删除。");
      }
      database
        .prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run(snapshotId, projectId, command.subjectType, subjectId, JSON.stringify(payload), timestamp);
      host.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: snapshotId,
        revision: 1,
        changes: [{ entity: "snapshot", id: snapshotId, action: "created", revision: 1 }]
      };
    });
  }

  /** 校验快照对象属于指定项目：对象不存在（永久删除）抛 not-found，跨项目抛 invalid-input。 */
  function assertSnapshotSubjectOwned(projectId: string, subjectType: SnapshotSubjectType, subjectId: string): void {
    const ownerProject = findSnapshotSubjectProject(subjectType, subjectId);
    if (ownerProject === null) {
      throw new CreationWorkspaceError("not-found", "快照对象不存在或已删除。");
    }
    if (ownerProject !== projectId) {
      throw new CreationWorkspaceError("invalid-input", "快照对象不属于当前作品。");
    }
  }

  /** 返回快照对象所属项目 ID；对象已永久删除（行不存在）时返回 null。 */
  function findSnapshotSubjectProject(subjectType: SnapshotSubjectType, subjectId: string): string | null {
    if (subjectType === "scene") {
      const row = database
        .prepare("SELECT c.project_id AS project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    if (subjectType === "card") {
      const row = database
        .prepare("SELECT project_id FROM cards WHERE id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    if (subjectType === "chapter") {
      const row = database
        .prepare("SELECT project_id FROM chapters WHERE id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    const row = database
      .prepare("SELECT project_id FROM volumes WHERE id = ?")
      .get(subjectId) as { project_id: string } | undefined;
    return row?.project_id ?? null;
  }

  /** 读取某对象当前状态为快照 payload（快照创建与保护快照共用）。对象不存在返回 null。 */

  function captureSubjectPayload(subjectType: SnapshotSubjectType, subjectId: string, reason: string): Record<string, unknown> | null { if (subjectType === "scene") {
    const scene = database.prepare("SELECT body_json, planning_json, summary, scene_status, revision, deleted_at FROM scenes WHERE id = ?").get(subjectId) as {
        body_json: string;
        planning_json: string;
        summary: string;
        scene_status: string;
        revision: number;
        deleted_at: string | null;
    } | undefined;
    if (!scene || scene.deleted_at !== null)
        return null;
    let body: CreationDocument;
    try {
        body = JSON.parse(scene.body_json) as CreationDocument;
    }
    catch {
        throw new CreationWorkspaceError("integrity", "场景正文数据损坏。");
    }
    return {
        reason,
        revision: scene.revision,
        body,
        planningJson: scene.planning_json,
        summary: scene.summary,
        status: SCENE_STATUSES.has(scene.scene_status as SceneStatus) ? scene.scene_status : "planned"
    };
} if (subjectType === "card") {
    const card = database.prepare("SELECT title, aliases_json, fields_json, tags_json, revision, deleted_at FROM cards WHERE id = ?").get(subjectId) as {
        title: string;
        aliases_json: string;
        fields_json: string;
        tags_json: string;
        revision: number;
        deleted_at: string | null;
    } | undefined;
    if (!card || card.deleted_at !== null)
        return null;
    return { reason, revision: card.revision, card: { title: card.title, aliases: JSON.parse(card.aliases_json) as string[], fields: JSON.parse(card.fields_json) as Record<string, unknown>, tags: JSON.parse(card.tags_json) as string[] } };
} if (subjectType === "chapter") {
    const chapter = database.prepare("SELECT title, status, numbering_kind, custom_number, revision, deleted_at FROM chapters WHERE id = ?").get(subjectId) as {
        title: string;
        status: string;
        numbering_kind: string;
        custom_number: string | null;
        revision: number;
        deleted_at: string | null;
    } | undefined;
    if (!chapter || chapter.deleted_at !== null)
        return null;
    const scenes = database.prepare("SELECT id, title, sort_order, body_json, planning_json, summary, scene_status, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(subjectId) as Array<{
        id: string;
        title: string;
        sort_order: number;
        body_json: string;
        planning_json: string;
        summary: string;
        scene_status: string;
        revision: number;
    }>;
    return { reason, revision: chapter.revision, chapter: { title: chapter.title, status: chapter.status, numberingKind: chapter.numbering_kind, customNumber: chapter.custom_number, scenes } };
} const volume = database.prepare("SELECT title, revision, deleted_at FROM volumes WHERE id = ?").get(subjectId) as {
    title: string;
    revision: number;
    deleted_at: string | null;
} | undefined; if (!volume || volume.deleted_at !== null)
    return null; const chapters = database.prepare("SELECT id, title, status, numbering_kind, custom_number, revision FROM chapters WHERE volume_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(subjectId) as Array<{
    id: string;
    title: string;
    status: string;
    numbering_kind: string;
    custom_number: string | null;
    revision: number;
}>; const chaptersWithScenes = chapters.map((chapter) => { const scenes = database.prepare("SELECT id, title, sort_order, body_json, planning_json, summary, scene_status, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(chapter.id) as Array<{
    id: string;
    title: string;
    sort_order: number;
    body_json: string;
    planning_json: string;
    summary: string;
    scene_status: string;
    revision: number;
}>; return { ...chapter, scenes }; }); return { reason, revision: volume.revision, volume: { title: volume.title, chapters: chaptersWithScenes } }; }

  const SNAPSHOT_SUBJECT_TYPES = new Set<SnapshotSubjectType>(["scene", "card", "chapter", "volume"]);

  function requireSnapshot(projectId: string, snapshotId: string): {
    subject_type: string;
    subject_id: string;
    payload_json: string;
} { const snapshot = database.prepare("SELECT subject_type, subject_id, payload_json FROM snapshots WHERE id = ? AND project_id = ?").get(snapshotId, projectId) as {
    subject_type: string;
    subject_id: string;
    payload_json: string;
} | undefined; if (!snapshot)
    throw new CreationWorkspaceError("not-found", "快照不存在。"); return snapshot; }

  function parseSnapshotPayload(payloadJson: string): Record<string, unknown> { try {
    const parsed = JSON.parse(payloadJson) as unknown;
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
}
catch {
    throw new CreationWorkspaceError("integrity", "快照数据损坏。");
} }

  function clampDiffText(value: string, max = 400): string { if (value.length <= max)
    return value; return `${value.slice(0, max)}…（共 ${value.length} 字）`; } /** 快照差异预览：rows 中 before=当前值、after=快照值。数据缺失/损坏时给 warnings 并置 canRestore=false。 */

  function readSnapshotPreview(projectId: string, snapshotId: string): SnapshotPreviewView | null { host.requireProject(projectId); const snapshot = requireSnapshot(projectId, snapshotId); const warnings: string[] = []; let payload: Record<string, unknown>; try {
    payload = parseSnapshotPayload(snapshot.payload_json);
}
catch (error) {
    warnings.push(error instanceof Error ? error.message : String(error));
    return { snapshotId, subjectType: snapshot.subject_type as SnapshotSubjectType, subjectId: snapshot.subject_id, title: "", rows: [], warnings, canRestore: false };
} const rows: SnapshotDiffRow[] = []; const pushRow = (label: string, before: string, after: string) => { rows.push({ label, before: clampDiffText(before), after: clampDiffText(after), changed: before !== after }); }; let title = ""; let complete = true; if (SNAPSHOT_SUBJECT_TYPES.has(snapshot.subject_type as SnapshotSubjectType)) {
    const ownerProject = findSnapshotSubjectProject(snapshot.subject_type as SnapshotSubjectType, snapshot.subject_id);
    if (ownerProject === null) {
        warnings.push("目标对象已永久删除，无法恢复。");
        complete = false;
    }
    else if (ownerProject !== projectId) {
        throw new CreationWorkspaceError("invalid-input", "快照对象不属于当前作品。");
    }
} if (snapshot.subject_type === "scene") {
    const current = database.prepare("SELECT title, body_json FROM scenes WHERE id = ?").get(snapshot.subject_id) as {
        title: string;
        body_json: string;
    } | undefined;
    title = current?.title ?? "";
    if (payload.body === undefined) {
        warnings.push("快照缺少场景正文。");
        complete = false;
    }
    else {
        const beforeText = current ? extractSceneText(current.body_json) : "（对象不存在）";
        pushRow("正文", beforeText, extractSceneText(JSON.stringify(payload.body)));
    }
}
else if (snapshot.subject_type === "card") {
    const current = database.prepare("SELECT title, aliases_json, fields_json, tags_json FROM cards WHERE id = ?").get(snapshot.subject_id) as {
        title: string;
        aliases_json: string;
        fields_json: string;
        tags_json: string;
    } | undefined;
    title = current?.title ?? "";
    if (payload.card === undefined) {
        warnings.push("快照缺少卡片数据。");
        complete = false;
    }
    else {
        const card = payload.card as {
            title?: string;
            aliases?: string[];
            fields?: Record<string, unknown>;
            tags?: string[];
        };
        if (current) {
            pushRow("标题", current.title, card.title ?? "");
            pushRow("别名", (JSON.parse(current.aliases_json) as string[]).join("、"), (card.aliases ?? []).join("、"));
            pushRow("字段", JSON.stringify(JSON.parse(current.fields_json)), JSON.stringify(card.fields ?? {}));
            pushRow("标签", (JSON.parse(current.tags_json) as string[]).join("、"), (card.tags ?? []).join("、"));
        }
        else {
            pushRow("标题", "（对象不存在）", card.title ?? "");
            pushRow("别名", "（对象不存在）", (card.aliases ?? []).join("、"));
            pushRow("字段", "（对象不存在）", JSON.stringify(card.fields ?? {}));
            pushRow("标签", "（对象不存在）", (card.tags ?? []).join("、"));
        }
    }
}
else if (snapshot.subject_type === "chapter") {
    const current = database.prepare("SELECT title, status, numbering_kind, custom_number FROM chapters WHERE id = ?").get(snapshot.subject_id) as {
        title: string;
        status: string;
        numbering_kind: string;
        custom_number: string | null;
    } | undefined;
    title = current?.title ?? "";
    if (payload.chapter === undefined) {
        warnings.push("快照缺少章节数据。");
        complete = false;
    }
    else {
        const chapter = payload.chapter as {
            title?: string;
            status?: string;
            numberingKind?: string;
            customNumber?: string | null;
            scenes?: unknown[];
        };
        const sceneCount = Array.isArray(chapter.scenes) ? chapter.scenes.length : 0;
        // 已移出本章的快照场景：恢复时跳过，不覆盖其在其他章节的新内容。
        const movedSceneIds: string[] = [];
        if (Array.isArray(chapter.scenes)) {
            for (const item of chapter.scenes) {
                if (!isRecord(item) || typeof item.id !== "string") continue;
                const sceneRow = database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(item.id) as {
                    chapter_id: string;
                } | undefined;
                if (sceneRow && sceneRow.chapter_id !== snapshot.subject_id) movedSceneIds.push(item.id);
            }
        }
        if (current) {
            pushRow("标题", current.title, chapter.title ?? "");
            pushRow("状态", current.status, chapter.status ?? "");
            pushRow("编号方式", current.numbering_kind, chapter.numberingKind ?? "");
            pushRow("场景数", `${countChapterScenes(snapshot.subject_id)}`, `${sceneCount}`);
            pushRow("已移出本章的场景（跳过）", "0", `${movedSceneIds.length}`);
        }
        else {
            pushRow("标题", "（对象不存在）", chapter.title ?? "");
            pushRow("状态", "（对象不存在）", chapter.status ?? "");
            pushRow("场景数", "（对象不存在）", `${sceneCount}`);
        }
        if (movedSceneIds.length > 0) {
            warnings.push(`${movedSceneIds.length} 个快照场景已移出本章，恢复时将跳过，不会覆盖其在其他章节的新内容。`);
        }
    }
}
else if (snapshot.subject_type === "volume") {
    const current = database.prepare("SELECT title FROM volumes WHERE id = ?").get(snapshot.subject_id) as {
        title: string;
    } | undefined;
    title = current?.title ?? "";
    if (payload.volume === undefined) {
        warnings.push("快照缺少卷数据。");
        complete = false;
    }
    else {
        const volume = payload.volume as {
            title?: string;
            chapters?: unknown[];
        };
        const chapterCount = Array.isArray(volume.chapters) ? volume.chapters.length : 0;
        // 已移出本卷的快照章节：恢复时跳过，不覆盖其在其他卷的新修改；对保留章节统计已移出该章的场景。
        const movedChapterIds: string[] = [];
        let movedSceneCount = 0;
        if (Array.isArray(volume.chapters)) {
            for (const item of volume.chapters) {
                if (!isRecord(item) || typeof item.id !== "string") continue;
                const chapterRow = database.prepare("SELECT volume_id FROM chapters WHERE id = ?").get(item.id) as {
                    volume_id: string | null;
                } | undefined;
                if (chapterRow && chapterRow.volume_id !== snapshot.subject_id) {
                    movedChapterIds.push(item.id);
                    continue;
                }
                const chapterScenes = Array.isArray(item.scenes) ? item.scenes : [];
                for (const sceneItem of chapterScenes) {
                    if (!isRecord(sceneItem) || typeof sceneItem.id !== "string") continue;
                    const sceneRow = database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(sceneItem.id) as {
                        chapter_id: string;
                    } | undefined;
                    if (sceneRow && sceneRow.chapter_id !== item.id) movedSceneCount += 1;
                }
            }
        }
        if (current) {
            pushRow("标题", current.title, volume.title ?? "");
            pushRow("章节数", `${countVolumeChapters(snapshot.subject_id)}`, `${chapterCount}`);
            pushRow("已移出本卷的章节（跳过）", "0", `${movedChapterIds.length}`);
            pushRow("已移出所在章节的场景（跳过）", "0", `${movedSceneCount}`);
        }
        else {
            pushRow("标题", "（对象不存在）", volume.title ?? "");
            pushRow("章节数", "（对象不存在）", `${chapterCount}`);
        }
        if (movedChapterIds.length > 0) {
            warnings.push(`${movedChapterIds.length} 个快照章节已移出本卷，恢复时将跳过，不会覆盖其在其他卷的新修改。`);
        }
        if (movedSceneCount > 0) {
            warnings.push(`${movedSceneCount} 个快照场景已移出所在章节，恢复时将跳过，不会覆盖其在其他章节的新内容。`);
        }
    }
}
else {
    warnings.push("该快照类型不支持恢复。");
    complete = false;
} return { snapshotId, subjectType: snapshot.subject_type as SnapshotSubjectType, subjectId: snapshot.subject_id, title, rows, warnings, canRestore: complete && SNAPSHOT_SUBJECT_TYPES.has(snapshot.subject_type as SnapshotSubjectType) }; }

  function countChapterScenes(chapterId: string): number { const row = database.prepare("SELECT count(*) AS count FROM scenes WHERE chapter_id = ?").get(chapterId) as {
    count: number;
}; return row.count; }

  function countVolumeChapters(volumeId: string): number { const row = database.prepare("SELECT count(*) AS count FROM chapters WHERE volume_id = ?").get(volumeId) as {
    count: number;
}; return row.count; } /** 恢复目标快照内容（restoreWithProtection 事务内调用）。返回最新 revision 与事件 changes。 */

  function applySnapshotPayload(subjectType: string, subjectId: string, payload: Record<string, unknown>, timestamp: string): {
    revision: number;
    changes: CreationWorkspaceEvent["changes"];
} {
    if (subjectType === "scene") {
        const body = payload.body as CreationDocument | undefined;
        if (!body)
            throw new CreationWorkspaceError("integrity", "快照正文数据缺失。");
        const current = database.prepare("SELECT planning_json, summary, scene_status, revision FROM scenes WHERE id = ?").get(subjectId) as {
            planning_json: string;
            summary: string;
            scene_status: string;
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "场景已永久删除，无法恢复。");
        const revision = current.revision + 1;
        const bodyJson = JSON.stringify(body);
        const stats = countSceneBodyStats(bodyJson);
        const planningJson = typeof payload.planningJson === "string" ? payload.planningJson : current.planning_json;
        const summary = typeof payload.summary === "string" ? payload.summary.slice(0, 2000) : current.summary;
        const status = typeof payload.status === "string" && SCENE_STATUSES.has(payload.status as SceneStatus)
            ? payload.status
            : current.scene_status;
        database.prepare("UPDATE scenes SET body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(bodyJson, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, revision, subjectId);
        return { revision, changes: [{ entity: "scene", id: subjectId, action: "restored", revision }] };
    }
    if (subjectType === "card") {
        const card = payload.card as {
            title?: string;
            aliases?: string[];
            fields?: Record<string, unknown>;
            tags?: string[];
        } | undefined;
        if (!card)
            throw new CreationWorkspaceError("integrity", "快照卡片数据缺失。");
        const current = database.prepare("SELECT revision FROM cards WHERE id = ?").get(subjectId) as {
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "卡片已永久删除，无法恢复。");
        const revision = current.revision + 1;
        database.prepare("UPDATE cards SET title = ?, aliases_json = ?, fields_json = ?, tags_json = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(card.title ?? "", JSON.stringify(card.aliases ?? []), JSON.stringify(card.fields ?? {}), JSON.stringify(card.tags ?? []), timestamp, revision, subjectId);
        return { revision, changes: [{ entity: "card", id: subjectId, action: "restored", revision }] };
    }
    if (subjectType === "chapter") {
        const chapter = payload.chapter as {
            title?: string;
            status?: string;
            numberingKind?: string;
            customNumber?: string | null;
            scenes?: Array<{
                id: string;
                title: string;
                sort_order: number;
                body_json: string;
                planning_json?: string;
                summary?: string;
                scene_status?: string;
                revision: number;
            }>;
        } | undefined;
        if (!chapter)
            throw new CreationWorkspaceError("integrity", "快照章节数据缺失。");
        const current = database.prepare("SELECT revision FROM chapters WHERE id = ?").get(subjectId) as {
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "章节已永久删除，无法恢复。");
        const revision = current.revision + 1;
        database.prepare("UPDATE chapters SET title = ?, status = ?, numbering_kind = ?, custom_number = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(chapter.title ?? "", chapter.status ?? "", chapter.numberingKind ?? "auto", chapter.customNumber ?? null, timestamp, revision, subjectId);
        const changes: CreationWorkspaceEvent["changes"] = [{ entity: "chapter", id: subjectId, action: "restored", revision }];
        for (const scene of chapter.scenes ?? []) {
            // 场景已移出本章：跳过，不覆盖其在其他章节的新内容。
            const existing = database.prepare("SELECT chapter_id, planning_json, summary, scene_status FROM scenes WHERE id = ?").get(scene.id) as {
                chapter_id: string;
                planning_json: string;
                summary: string;
                scene_status: string;
            } | undefined;
            if (existing && existing.chapter_id !== subjectId)
                continue;
            const sceneRevision = (scene.revision ?? 0) + 1;
            const stats = countSceneBodyStats(scene.body_json);
            const planningJson = scene.planning_json ?? existing?.planning_json ?? "{}";
            const summary = typeof scene.summary === "string" ? scene.summary.slice(0, 2000) : existing?.summary ?? "";
            const status = typeof scene.scene_status === "string" && SCENE_STATUSES.has(scene.scene_status as SceneStatus)
                ? scene.scene_status
                : existing?.scene_status ?? "planned";
            if (existing) {
                database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, planning_json, summary, scene_status, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, subjectId, scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
            }
            changes.push({ entity: "scene", id: scene.id, action: "restored", revision: sceneRevision });
        }
        return { revision, changes };
    }
    const volume = payload.volume as {
        title?: string;
        chapters?: Array<{
            id: string;
            title: string;
            status: string;
            numbering_kind: string;
            custom_number: string | null;
            revision: number;
            scenes?: Array<{
                id: string;
                title: string;
                sort_order: number;
                body_json: string;
                planning_json?: string;
                summary?: string;
                scene_status?: string;
                revision: number;
            }>;
        }>;
    } | undefined;
    if (!volume)
        throw new CreationWorkspaceError("integrity", "快照卷数据缺失。");
    const current = database.prepare("SELECT revision FROM volumes WHERE id = ?").get(subjectId) as {
        revision: number;
    } | undefined;
    const volumeProject = database.prepare("SELECT project_id FROM volumes WHERE id = ?").get(subjectId) as {
        project_id: string;
    } | undefined;
    if (!volumeProject)
        throw new CreationWorkspaceError("not-found", "卷不存在，无法恢复。");
    const revision = (current?.revision ?? 0) + 1;
    database.prepare("UPDATE volumes SET title = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(volume.title ?? "", timestamp, revision, subjectId);
    const changes: CreationWorkspaceEvent["changes"] = [{ entity: "volume", id: subjectId, action: "restored", revision }];
    for (const chapter of volume.chapters ?? []) {
        const chapterRevision = (chapter.revision ?? 0) + 1;
        // 章节已移出本卷：跳过，不覆盖其在其他卷的新修改、不改归属。
        const existingChapter = database.prepare("SELECT volume_id FROM chapters WHERE id = ?").get(chapter.id) as {
            volume_id: string | null;
        } | undefined;
        if (existingChapter && existingChapter.volume_id !== subjectId)
            continue;
        if (existingChapter) {
            database.prepare("UPDATE chapters SET title = ?, status = ?, numbering_kind = ?, custom_number = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(chapter.title, chapter.status, chapter.numbering_kind, chapter.custom_number ?? null, timestamp, chapterRevision, chapter.id);
        }
        else {
            database.prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(chapter.id, volumeProject.project_id, subjectId, chapter.title, 0, chapter.status, chapter.numbering_kind, chapter.custom_number ?? null, timestamp, timestamp, chapterRevision);
        }
        changes.push({ entity: "chapter", id: chapter.id, action: "restored", revision: chapterRevision });
        for (const scene of chapter.scenes ?? []) {
            const sceneRevision = (scene.revision ?? 0) + 1;
            // 场景已移出本章：跳过，不覆盖其在其他章节的新内容、不改归属。
            const existingScene = database.prepare("SELECT chapter_id, planning_json, summary, scene_status FROM scenes WHERE id = ?").get(scene.id) as {
                chapter_id: string;
                planning_json: string;
                summary: string;
                scene_status: string;
            } | undefined;
            if (existingScene && existingScene.chapter_id !== chapter.id)
                continue;
            const stats = countSceneBodyStats(scene.body_json);
            const planningJson = scene.planning_json ?? existingScene?.planning_json ?? "{}";
            const summary = typeof scene.summary === "string" ? scene.summary.slice(0, 2000) : existingScene?.summary ?? "";
            const status = typeof scene.scene_status === "string" && SCENE_STATUSES.has(scene.scene_status as SceneStatus)
                ? scene.scene_status
                : existingScene?.scene_status ?? "planned";
            if (existingScene) {
                database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, planning_json, summary, scene_status, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, chapter.id, scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
            }
            changes.push({ entity: "scene", id: scene.id, action: "restored", revision: sceneRevision });
        }
    }
    return { revision, changes };
} /**   * 快照安全恢复：同一 BEGIN IMMEDIATE 事务内 保护快照 → 恢复目标 → change_log → COMMIT。   * 任一步失败整体回滚（保护快照不落库、对象不变）。   */

  async function restoreSnapshotWithProtection(command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult> {
    const projectId = validateId(command.projectId, "作品");
    const snapshotId = validateId(command.snapshotId, "快照");
    const protectionReason = validateTitle(command.protectionReason, "保护原因", 200);
    const timestamp = new Date().toISOString();
    const protectionSnapshotId = `protective-snapshot-${randomUUID()}`;
    try {
        database.exec("BEGIN IMMEDIATE");
        host.requireProject(projectId);
        const snapshot = requireSnapshot(projectId, snapshotId);
        const subjectType = snapshot.subject_type as SnapshotSubjectType;
        if (!SNAPSHOT_SUBJECT_TYPES.has(subjectType)) {
            throw new CreationWorkspaceError("invalid-input", "快照对象类型不支持安全恢复。");
        }
        // 归属校验：对象已永久删除或不属于当前项目时，在写入保护快照前稳定失败（整体回滚、零写入）。
        assertSnapshotSubjectOwned(projectId, subjectType, snapshot.subject_id);
        const payload = parseSnapshotPayload(snapshot.payload_json); // a. 捕获恢复前保护快照（对象不存在时记录 absent 标记，仍可用于回退）。
        const protectionPayload = captureSubjectPayload(subjectType, snapshot.subject_id, protectionReason);
        database.prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)").run(protectionSnapshotId, projectId, subjectType, snapshot.subject_id, JSON.stringify(protectionPayload ?? { reason: protectionReason, revision: 0, absent: true }), timestamp); // b. 恢复目标快照
        const restored = applySnapshotPayload(subjectType, snapshot.subject_id, payload, timestamp);
        host.touchProject(projectId, timestamp); // c. change_log
        const logged = database.prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)").run(projectId, "snapshot.restoreWithProtection", JSON.stringify(restored.changes), timestamp); // d. COMMIT
        database.exec("COMMIT");
        host.emitCommitted({ kind: "committed", sequence: Number(logged.lastInsertRowid), projectId, commandType: "snapshot.restoreWithProtection", changes: restored.changes });
        return { ok: true, protectionSnapshotId, restoredSubjectType: subjectType, restoredSubjectId: snapshot.subject_id, revision: restored.revision };
    }
    catch (error) {
        try {
            database.exec("ROLLBACK");
        }
        catch { // The transaction may already have been rolled back by SQLite.
        }
        if (error instanceof CreationWorkspaceError)
            throw error;
        if (isConstraintError(error))
            throw new CreationWorkspaceError("conflict", "快照恢复违反结构约束，未应用任何修改。");
        throw new CreationWorkspaceError("integrity", "无法安全恢复快照。");
    }
}

  /** 分层快照留存：system 受控分类 + 单事务删除；绝不读取 reason。 */
  async function runSnapshotRetention(): Promise<SnapshotRetentionResult> {
    host.assertOpen();
    const rows = database
      .prepare("SELECT id, project_id, subject_type, subject_id, created_at FROM snapshots")
      .all() as Array<{ id: string; project_id: string; subject_type: string; subject_id: string; created_at: string }>;
    const metas = rows.map((r) => ({
      id: r.id,
      projectId: r.project_id,
      subjectType: r.subject_type,
      subjectId: r.subject_id,
      kind: classifySnapshotMeta({ id: r.id, subjectType: r.subject_type, subjectId: r.subject_id }),
      createdAt: r.created_at
    }));
    const plan = planSnapshotRetention(metas, new Date());
    if (plan.deleteIds.length > 0) {
      database.exec("BEGIN IMMEDIATE");
      try {
        const del = database.prepare("DELETE FROM snapshots WHERE id = ?");
        for (const id of plan.deleteIds) del.run(id);
        database.exec("COMMIT");
      } catch (error) {
        try {
          database.exec("ROLLBACK");
        } catch {
          /* 已回滚 */
        }
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法执行分层快照留存删除。");
      }
    }
    return { keepIds: plan.keepIds, deleteIds: plan.deleteIds, deletedCount: plan.deleteIds.length };
  }

  return { snapshotList, snapshotCreate, readSnapshotPreview, restoreSnapshotWithProtection, runSnapshotRetention };
}
