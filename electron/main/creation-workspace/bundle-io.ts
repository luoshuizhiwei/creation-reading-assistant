/**
 * 项目包域模块（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：项目包（ProjectBundle）的导出载荷组装、导入预览与导入事务。
 * 通过 Database 窄接口与宿主回调与类主体解耦；共享工具来自 bundle-merge / workspace-utils。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { CreationWorkspaceError } from "./types";
import type {
  AnnotationAnchor,
  CreationWorkspaceEvent,
  ProjectBundleData,
  ProjectBundleImportCommand,
  ProjectBundleImportPreview,
  ProjectBundleImportResult,
  SceneStatus
} from "./types";
import type { ProjectBundleCardMapping, ProjectBundleCardResolution, ChapterNumberingKind } from "../../../src/types/creation";
import { countSceneBodyStats } from "./scene-stats";
import { parseStoredSetup, defaultProjectSetup } from "./project-setup";
import { isRecord, SCENE_STATUSES } from "./workspace-utils";
import {
  isSafeBundleRelativePath,
  canonicalStoredJson,
  cardResourceSignature,
  incomingCardResourceSignature,
  bundleCardDifferences,
  remapPlanningCardIds,
  remapJsonStableIds,
  type StoredBundleCardComparable
} from "./bundle-merge";

export interface BundleIoHost {
  assertOpen(): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface BundleIoModule {
  runProjectBundleExport(projectId: string): ProjectBundleData | null;
  previewProjectBundleImport(data: ProjectBundleData): Promise<ProjectBundleImportPreview>;
  importProjectBundle(command: ProjectBundleImportCommand): ProjectBundleImportResult;
}

export function createBundleIoModule(database: Database, host: BundleIoHost): BundleIoModule {

const runProjectBundleExport = (projectId: string): ProjectBundleData | null => {
    const project = database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;
    const volumes = database
      .prepare("SELECT id, title, sort_order, created_at, updated_at, revision FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
      .all(projectId) as Array<{ id: string; title: string; sort_order: number; created_at: string; updated_at: string; revision: number }>;
    const chapters = database
      .prepare("SELECT id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision FROM chapters WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
      .all(projectId) as Array<{
      id: string;
      volume_id: string | null;
      title: string;
      sort_order: number;
      status: string;
      numbering_kind: string;
      custom_number: string | null;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const scenes = database
      .prepare(
        "SELECT s.id, s.chapter_id, s.title, s.sort_order, s.body_json, s.planning_json, s.summary, s.scene_status, s.created_at, s.updated_at, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id"
      )
      .all(projectId) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      sort_order: number;
      body_json: string;
      planning_json: string;
      summary: string;
      scene_status: SceneStatus;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const cardTypes = database
      .prepare(`SELECT id, kind, name, fields_json, sort_order, created_at, updated_at, revision
        FROM card_types
        WHERE is_builtin = 0 AND kind IN (
          SELECT c.kind FROM cards c JOIN project_card_links pcl ON pcl.card_id = c.id
          WHERE pcl.project_id = ? AND c.deleted_at IS NULL
        ) ORDER BY sort_order, id`)
      .all(projectId) as Array<{
      id: string;
      kind: string;
      name: string;
      fields_json: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const relationTypes = database
      .prepare(`SELECT id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision
        FROM relation_types WHERE is_builtin = 0 AND id IN (
          SELECT r.relation_type FROM card_relations r
          JOIN project_card_links pf ON pf.card_id = r.from_card_id AND pf.project_id = ?
          JOIN project_card_links pt ON pt.card_id = r.to_card_id AND pt.project_id = ?
        ) ORDER BY id`)
      .all(projectId, projectId) as Array<{
      id: string;
      name: string;
      forward_name: string;
      reverse_name: string;
      from_kinds_json: string;
      to_kinds_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const cards = database
      .prepare(`SELECT c.id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json, c.content_json, c.created_at, c.updated_at, c.revision
        FROM cards c JOIN project_card_links pcl ON pcl.card_id = c.id
        WHERE pcl.project_id = ? AND c.deleted_at IS NULL ORDER BY c.updated_at, c.id`)
      .all(projectId) as Array<{
      id: string;
      kind: string;
      title: string;
      aliases_json: string;
      fields_json: string;
      tags_json: string;
      content_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const relations = database
      .prepare(`SELECT r.id, r.from_card_id, r.to_card_id, r.relation_type, r.note, r.created_at
        FROM card_relations r
        JOIN project_card_links pf ON pf.card_id = r.from_card_id AND pf.project_id = ?
        JOIN project_card_links pt ON pt.card_id = r.to_card_id AND pt.project_id = ?
        ORDER BY r.id`)
      .all(projectId, projectId) as Array<{
      id: string;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      created_at: string;
    }>;
    const snapshots = database
      .prepare("SELECT id, subject_type, subject_id, payload_json, created_at FROM snapshots WHERE project_id = ? ORDER BY created_at, id")
      .all(projectId) as Array<{
      id: string;
      subject_type: string;
      subject_id: string;
      payload_json: string;
      created_at: string;
    }>;
    const resources = database
      .prepare(`SELECT r.id, r.card_id, 'project' AS owner_scope, r.relative_path, r.sha256, r.size, r.original_name, 'attachment' AS role, r.created_at
        FROM resources r
        WHERE r.project_id = ? OR (r.card_id IS NOT NULL AND EXISTS (
          SELECT 1 FROM project_card_links pcl WHERE pcl.project_id = ? AND pcl.card_id = r.card_id
        ))
        UNION ALL
        SELECT g.id, g.card_id, 'card' AS owner_scope, g.relative_path, g.sha256, g.size, g.original_name, g.role, g.created_at
        FROM global_card_resources g JOIN project_card_links pcl ON pcl.card_id = g.card_id
        WHERE pcl.project_id = ?
        ORDER BY relative_path, id`)
      .all(projectId, projectId, projectId) as Array<{
      id: string;
      card_id: string | null;
      owner_scope: "project" | "card";
      role: "attachment" | "cover";
      relative_path: string;
      sha256: string;
      size: number;
      original_name: string | null;
      created_at: string;
    }>;
    const annotations = database
      .prepare("SELECT id, scene_id, card_id, anchor_json, note, status, revision, created_at, updated_at FROM annotations WHERE project_id = ? AND deleted_at IS NULL ORDER BY created_at, id")
      .all(projectId) as Array<{
      id: string;
      scene_id: string;
      card_id: string | null;
      anchor_json: string;
      note: string | null;
      status: string;
      revision: number;
      created_at: string;
      updated_at: string;
    }>;
    return {
      formatVersion: 2,
      project: {
        id: project.id,
        title: project.title,
        setup: parseStoredSetup(project.setup_json),
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      volumes: volumes.map((row) => ({ id: row.id, title: row.title, sortOrder: row.sort_order, createdAt: row.created_at, updatedAt: row.updated_at, revision: row.revision })),
      chapters: chapters.map((row) => ({
        id: row.id,
        volumeId: row.volume_id,
        title: row.title,
        sortOrder: row.sort_order,
        status: row.status,
        numberingKind: (row.numbering_kind as ChapterNumberingKind) ?? "auto",
        customNumber: row.custom_number,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        revision: row.revision
      })),
      scenes: scenes.map((row) => ({
        id: row.id,
        chapterId: row.chapter_id,
        title: row.title,
        sortOrder: row.sort_order,
        bodyJson: row.body_json,
        planningJson: row.planning_json,
        summary: row.summary,
        status: SCENE_STATUSES.has(row.scene_status) ? row.scene_status : "planned",
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        revision: row.revision
      })),
      cardTypes: cardTypes.map((row) => ({
        id: row.id,
        kind: row.kind,
        name: row.name,
        fieldsJson: row.fields_json,
        sortOrder: row.sort_order,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        revision: row.revision
      })),
      relationTypes: relationTypes.map((row) => ({
        id: row.id,
        name: row.name,
        forwardName: row.forward_name,
        reverseName: row.reverse_name,
        fromKindsJson: row.from_kinds_json,
        toKindsJson: row.to_kinds_json,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        revision: row.revision
      })),
      cards: cards.map((row) => ({
        id: row.id,
        kind: row.kind,
        title: row.title,
        aliasesJson: row.aliases_json,
        fieldsJson: row.fields_json,
        tagsJson: row.tags_json,
        contentJson: row.content_json,
        createdAt: row.created_at,
        updatedAt: row.updated_at,
        revision: row.revision
      })),
      relations: relations.map((row) => ({
        id: row.id,
        fromCardId: row.from_card_id,
        toCardId: row.to_card_id,
        relationType: row.relation_type,
        note: row.note,
        createdAt: row.created_at
      })),
      snapshots: snapshots.map((row) => ({
        id: row.id,
        subjectType: row.subject_type,
        subjectId: row.subject_id,
        payloadJson: row.payload_json,
        createdAt: row.created_at
      })),
      resources: resources.map((row) => ({
        id: row.id,
        cardId: row.card_id,
        ownerScope: row.owner_scope,
        role: row.role,
        relativePath: row.relative_path,
        sha256: row.sha256,
        size: row.size,
        originalName: row.original_name,
        createdAt: row.created_at
      })),
      annotations: annotations.map((row) => {
        let anchor: AnnotationAnchor;
        try {
          anchor = JSON.parse(row.anchor_json) as AnnotationAnchor;
        } catch {
          throw new CreationWorkspaceError("integrity", "批注锚点数据损坏。");
        }
        if (
          !anchor ||
          typeof anchor !== "object" ||
          !Number.isInteger(anchor.blockIndex) ||
          anchor.blockIndex < 0 ||
          !Number.isInteger(anchor.textOffset) ||
          anchor.textOffset < 0 ||
          !Number.isInteger(anchor.textLength) ||
          anchor.textLength < 1 ||
          (anchor.text !== undefined && typeof anchor.text !== "string") ||
          (row.status !== "open" && row.status !== "resolved") ||
          !Number.isInteger(row.revision) ||
          row.revision < 1
        ) {
          throw new CreationWorkspaceError("integrity", "批注锚点数据损坏。");
        }
        return {
          id: row.id,
          sceneId: row.scene_id,
          cardId: row.card_id,
          anchor,
          note: row.note ?? "",
          status: row.status,
          revision: row.revision,
          createdAt: row.created_at,
          updatedAt: row.updated_at
        };
      }),
      counts: {
        volumes: volumes.length,
        chapters: chapters.length,
        scenes: scenes.length,
        cards: cards.length,
        relations: relations.length,
        snapshots: snapshots.length,
        resources: resources.length,
        annotations: annotations.length
      },
      exportedAt: new Date().toISOString()
    };
  }

const previewProjectBundleImport = async (data: ProjectBundleData): Promise<ProjectBundleImportPreview> => {
    host.assertOpen();
    if (!data || typeof data !== "object" || (data.formatVersion !== 1 && data.formatVersion !== 2)) {
      throw new CreationWorkspaceError("invalid-input", "项目包格式无效或版本不受支持。");
    }
    const projectTitle = typeof data.project?.title === "string" ? data.project.title.trim() : "";
    if (!projectTitle) throw new CreationWorkspaceError("invalid-input", "项目包缺少作品名称。");
    const incomingIds = new Set<string>();
    const identicalCardIds: string[] = [];
    const conflicts: ProjectBundleImportPreview["conflicts"] = [];
    const findCard = database.prepare(
      "SELECT id, kind, title, aliases_json, fields_json, tags_json, content_json, deleted_at FROM cards WHERE id = ?"
    );
    const findCardResources = database.prepare(
      "SELECT role, sha256, size, original_name FROM global_card_resources WHERE card_id = ? ORDER BY role, sha256, size, original_name"
    );
    for (const card of data.cards ?? []) {
      if (typeof card.id !== "string" || !card.id.startsWith("card-") || incomingIds.has(card.id)) {
        throw new CreationWorkspaceError("invalid-input", "项目包包含无效或重复卡片 ID。");
      }
      incomingIds.add(card.id);
      const local = findCard.get(card.id) as StoredBundleCardComparable | undefined;
      if (!local) continue;
      const localResources = findCardResources.all(card.id) as Array<{
        role: string;
        sha256: string;
        size: number;
        original_name: string | null;
      }>;
      const differingFields = bundleCardDifferences(
        card,
        local,
        incomingCardResourceSignature(data, card.id),
        cardResourceSignature(localResources.map((resource) => ({
          role: resource.role,
          sha256: resource.sha256,
          size: resource.size,
          originalName: resource.original_name
        })))
      );
      if (local.deleted_at === null && differingFields.length === 0) {
        identicalCardIds.push(card.id);
      } else {
        conflicts.push({
          cardId: card.id,
          localTitle: local.title,
          importedTitle: card.title,
          localDeleted: local.deleted_at !== null,
          differingFields: local.deleted_at !== null ? ["回收站状态", ...differingFields] : differingFields
        });
      }
    }
    return { projectTitle, cardCount: data.cards?.length ?? 0, identicalCardIds, conflicts };
  }

const importProjectBundle = (command: ProjectBundleImportCommand): ProjectBundleImportResult => {
    const runtimeCommand = command as unknown as {
      data?: unknown;
      targetProjectId?: unknown;
      resourceFiles?: unknown;
      cardResolutions?: unknown;
    };
    const data = runtimeCommand.data as unknown as ProjectBundleData | null;
    if (!data || typeof data !== "object" || (data.formatVersion !== 1 && data.formatVersion !== 2)) {
      throw new CreationWorkspaceError("invalid-input", "项目包格式无效或版本不受支持。");
    }
    const title = typeof data.project?.title === "string" && data.project.title.trim() ? data.project.title.trim() : "";
    if (!title) throw new CreationWorkspaceError("invalid-input", "项目包缺少作品名称。");
    const targetProjectId =
      typeof runtimeCommand.targetProjectId === "string" && runtimeCommand.targetProjectId.startsWith("project-") && runtimeCommand.targetProjectId.length <= 128
        ? runtimeCommand.targetProjectId
        : undefined;
    const projectId =
      targetProjectId ??
      (typeof data.project?.id === "string" && data.project.id.startsWith("project-") ? data.project.id : `project-${randomUUID()}`);
    const timestamp = new Date().toISOString();
    const annotationEntries = (data as unknown as Record<string, unknown>).annotations;
    if (data.formatVersion === 2 && !Array.isArray(annotationEntries)) {
      throw new CreationWorkspaceError("invalid-input", "v2 项目包缺少批注数据。");
    }
    const annotationsToImport = Array.isArray(annotationEntries)
      ? annotationEntries as ProjectBundleData["annotations"]
      : [];
    const resolutionMap = new Map<string, ProjectBundleCardResolution>();
    if (runtimeCommand.cardResolutions !== undefined) {
      if (!Array.isArray(runtimeCommand.cardResolutions)) {
        throw new CreationWorkspaceError("invalid-input", "项目包卡片冲突选择无效。");
      }
      for (const value of runtimeCommand.cardResolutions) {
        if (!isRecord(value) || typeof value.cardId !== "string" || !value.cardId.startsWith("card-") ||
          (value.action !== "reuse" && value.action !== "keep-local" && value.action !== "import-copy")) {
          throw new CreationWorkspaceError("invalid-input", "项目包卡片冲突选择无效。");
        }
        if (resolutionMap.has(value.cardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包卡片冲突选择包含重复卡片。");
        }
        const targetCardId = value.targetCardId;
        if (value.action === "import-copy" &&
          (typeof targetCardId !== "string" || !targetCardId.startsWith("card-") || targetCardId.length > 128)) {
          throw new CreationWorkspaceError("invalid-input", "导入副本缺少有效目标卡片 ID。");
        }
        resolutionMap.set(value.cardId, {
          cardId: value.cardId,
          action: value.action,
          ...(typeof targetCardId === "string" ? { targetCardId } : {})
        });
      }
    }
    try {
      database.exec("BEGIN IMMEDIATE");
      const existing = database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId);
      if (existing) throw new CreationWorkspaceError("conflict", `项目 ${projectId} 已存在，无法导入。`);
      database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?)")
        .run(projectId, title, JSON.stringify(data.project.setup ?? defaultProjectSetup()), data.project.createdAt ?? timestamp, data.project.updatedAt ?? timestamp, data.project.revision ?? 1);

      const incomingCardIds = new Set<string>();
      const targetCardIds = new Set<string>();
      const cardIdMap = new Map<string, string>();
      const cardMappings: ProjectBundleCardMapping[] = [];
      const cardsToInsert: Array<{ card: ProjectBundleData["cards"][number]; id: string }> = [];
      const findStoredCard = database.prepare(
        "SELECT id, kind, title, aliases_json, fields_json, tags_json, content_json, deleted_at FROM cards WHERE id = ?"
      );
      const findStoredCardResources = database.prepare(
        "SELECT role, sha256, size, original_name FROM global_card_resources WHERE card_id = ? ORDER BY role, sha256, size, original_name"
      );
      for (const card of data.cards ?? []) {
        if (typeof card.id !== "string" || !card.id.startsWith("card-") || incomingCardIds.has(card.id)) {
          throw new CreationWorkspaceError("invalid-input", "项目包包含无效或重复卡片 ID。");
        }
        incomingCardIds.add(card.id);
        const local = findStoredCard.get(card.id) as StoredBundleCardComparable | undefined;
        const resolution = resolutionMap.get(card.id);
        if (!local) {
          if (resolution) throw new CreationWorkspaceError("invalid-input", `卡片 ${card.id} 不存在冲突，不应提供处理选择。`);
          cardIdMap.set(card.id, card.id);
          targetCardIds.add(card.id);
          cardsToInsert.push({ card, id: card.id });
          cardMappings.push({ sourceCardId: card.id, targetCardId: card.id, action: "created" });
          continue;
        }
        const localResources = findStoredCardResources.all(card.id) as Array<{
          role: string;
          sha256: string;
          size: number;
          original_name: string | null;
        }>;
        const differences = bundleCardDifferences(
          card,
          local,
          incomingCardResourceSignature(data, card.id),
          cardResourceSignature(localResources.map((resource) => ({
            role: resource.role,
            sha256: resource.sha256,
            size: resource.size,
            originalName: resource.original_name
          })))
        );
        if (local.deleted_at === null && differences.length === 0) {
          if (resolution && resolution.action !== "reuse") {
            throw new CreationWorkspaceError("invalid-input", `卡片 ${card.id} 与本机内容相同，只能直接复用。`);
          }
          cardIdMap.set(card.id, card.id);
          targetCardIds.add(card.id);
          cardMappings.push({ sourceCardId: card.id, targetCardId: card.id, action: "reused" });
          continue;
        }
        if (!resolution || resolution.action === "reuse") {
          throw new CreationWorkspaceError("conflict", `全局卡片 ${card.title}（${card.id}）与本机内容不同，必须明确选择保留本机或导入副本。`);
        }
        if (resolution.action === "keep-local") {
          if (local.deleted_at !== null) {
            throw new CreationWorkspaceError("conflict", `全局卡片 ${card.title} 在本机回收站中，只能导入副本或取消。`);
          }
          cardIdMap.set(card.id, card.id);
          targetCardIds.add(card.id);
          cardMappings.push({ sourceCardId: card.id, targetCardId: card.id, action: "kept-local" });
          continue;
        }
        const copyId = resolution.targetCardId!;
        if (targetCardIds.has(copyId) || database.prepare("SELECT 1 FROM cards WHERE id = ?").get(copyId)) {
          throw new CreationWorkspaceError("conflict", `导入副本目标卡片 ID ${copyId} 已存在。`);
        }
        cardIdMap.set(card.id, copyId);
        targetCardIds.add(copyId);
        cardsToInsert.push({ card, id: copyId });
        cardMappings.push({ sourceCardId: card.id, targetCardId: copyId, action: "copied" });
      }
      for (const cardId of resolutionMap.keys()) {
        if (!incomingCardIds.has(cardId)) {
          throw new CreationWorkspaceError("invalid-input", `冲突选择引用了项目包中不存在的卡片 ${cardId}。`);
        }
      }

      const insertVolume = database.prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?)");
      const volumeSourceIds = new Set<string>();
      const volumeIds = new Set<string>();
      const volumeIdMap = new Map<string, string>();
      for (const volume of data.volumes ?? []) {
        const sourceId = typeof volume.id === "string" && volume.id.startsWith("volume-") ? volume.id : `volume-${randomUUID()}`;
        if (volumeSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复卷 ID。");
        volumeSourceIds.add(sourceId);
        const id = database.prepare("SELECT 1 FROM volumes WHERE id = ?").get(sourceId) ? `volume-${randomUUID()}` : sourceId;
        volumeIds.add(id);
        volumeIdMap.set(sourceId, id);
        insertVolume.run(id, projectId, volume.title, volume.sortOrder ?? 0, volume.createdAt ?? timestamp, volume.updatedAt ?? timestamp, volume.revision ?? 1);
      }
      if (volumeIds.size === 0) throw new CreationWorkspaceError("invalid-input", "项目包不包含任何卷。");

      const insertChapter = database.prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const chapterSourceIds = new Set<string>();
      const chapterIds = new Set<string>();
      const chapterIdMap = new Map<string, string>();
      for (const chapter of data.chapters ?? []) {
        const sourceId = typeof chapter.id === "string" && chapter.id.startsWith("chapter-") ? chapter.id : `chapter-${randomUUID()}`;
        if (chapterSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复章节 ID。");
        chapterSourceIds.add(sourceId);
        const id = database.prepare("SELECT 1 FROM chapters WHERE id = ?").get(sourceId) ? `chapter-${randomUUID()}` : sourceId;
        chapterIds.add(id);
        chapterIdMap.set(sourceId, id);
        const volumeId = chapter.volumeId ? volumeIdMap.get(chapter.volumeId) : undefined;
        if (!volumeId) throw new CreationWorkspaceError("invalid-input", "项目包章节引用了不存在的卷。");
        insertChapter.run(
          id,
          projectId,
          volumeId,
          chapter.title,
          chapter.sortOrder ?? 0,
          chapter.status ?? "",
          chapter.numberingKind ?? "auto",
          chapter.customNumber ?? null,
          chapter.createdAt ?? timestamp,
          chapter.updatedAt ?? timestamp,
          chapter.revision ?? 1
        );
      }

      const insertScene = database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, planning_json, summary, scene_status, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const sceneSourceIds = new Set<string>();
      const sceneIds = new Set<string>();
      const sceneIdMap = new Map<string, string>();
      for (const scene of data.scenes ?? []) {
        const sourceId = typeof scene.id === "string" && scene.id.startsWith("scene-") ? scene.id : `scene-${randomUUID()}`;
        if (sceneSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复场景 ID。");
        sceneSourceIds.add(sourceId);
        const id = database.prepare("SELECT 1 FROM scenes WHERE id = ?").get(sourceId) ? `scene-${randomUUID()}` : sourceId;
        sceneIds.add(id);
        sceneIdMap.set(sourceId, id);
        const chapterId = chapterIdMap.get(scene.chapterId);
        if (!chapterId) throw new CreationWorkspaceError("invalid-input", "项目包场景引用了不存在的章节。");
        let bodyJson = scene.bodyJson ?? '{"type":"doc","content":[]}';
        try {
          JSON.parse(bodyJson);
        } catch {
          bodyJson = '{"type":"doc","content":[]}';
        }
        const stats = countSceneBodyStats(bodyJson);
        const summary = typeof scene.summary === "string" ? scene.summary.trim() : "";
        if (summary.length > 2000) throw new CreationWorkspaceError("invalid-input", "项目包场景摘要超过 2000 个字符。");
        const sceneStatus = scene.status && SCENE_STATUSES.has(scene.status) ? scene.status : "planned";
        insertScene.run(id, chapterId, scene.title, scene.sortOrder ?? 0, bodyJson, stats.han, stats.punct, stats.nonWhitespace, remapPlanningCardIds(scene.planningJson ?? "{}", cardIdMap), summary, sceneStatus, scene.createdAt ?? timestamp, scene.updatedAt ?? timestamp, scene.revision ?? 1);
      }

      const insertCardType = database.prepare("INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const cardType of data.cardTypes ?? []) {
        const id = typeof cardType.id === "string" && cardType.id.startsWith("card-type-") ? cardType.id : `card-type-${randomUUID()}`;
        const existingType = database.prepare(
          "SELECT id, kind, name, fields_json, sort_order FROM card_types WHERE id = ? OR kind = ? LIMIT 1"
        ).get(id, cardType.kind) as { id: string; kind: string; name: string; fields_json: string; sort_order: number } | undefined;
        if (existingType) {
          const identical = existingType.kind === cardType.kind && existingType.name === cardType.name &&
            canonicalStoredJson(existingType.fields_json) === canonicalStoredJson(cardType.fieldsJson ?? "[]") &&
            existingType.sort_order === (cardType.sortOrder ?? 0);
          if (!identical) throw new CreationWorkspaceError("conflict", `自定义卡片类型 ${cardType.name} 与本机定义不同，拒绝静默覆盖。`);
          continue;
        }
        insertCardType.run(id, projectId, cardType.kind, cardType.name, cardType.fieldsJson ?? "[]", cardType.sortOrder ?? 0, cardType.createdAt ?? timestamp, cardType.updatedAt ?? timestamp, cardType.revision ?? 1);
      }

      const insertRelationType = database.prepare("INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const relationTypeIdMap = new Map<string, string>();
      for (const relationType of data.relationTypes ?? []) {
        const id = typeof relationType.id === "string" && relationType.id.startsWith("relation-type-") ? relationType.id : `relation-type-${randomUUID()}`;
        const existingType = database.prepare(
          "SELECT id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json FROM relation_types WHERE id = ? OR name = ? LIMIT 1"
        ).get(id, relationType.name) as {
          id: string; name: string; forward_name: string; reverse_name: string; from_kinds_json: string; to_kinds_json: string;
        } | undefined;
        if (existingType) {
          const identical = existingType.name === relationType.name && existingType.forward_name === relationType.forwardName &&
            existingType.reverse_name === relationType.reverseName &&
            canonicalStoredJson(existingType.from_kinds_json) === canonicalStoredJson(relationType.fromKindsJson ?? "[]") &&
            canonicalStoredJson(existingType.to_kinds_json) === canonicalStoredJson(relationType.toKindsJson ?? "[]");
          if (!identical) throw new CreationWorkspaceError("conflict", `自定义关系类型 ${relationType.name} 与本机定义不同，拒绝静默覆盖。`);
          relationTypeIdMap.set(id, existingType.id);
          continue;
        }
        insertRelationType.run(id, projectId, relationType.name, relationType.forwardName, relationType.reverseName, relationType.fromKindsJson ?? "[]", relationType.toKindsJson ?? "[]", relationType.createdAt ?? timestamp, relationType.updatedAt ?? timestamp, relationType.revision ?? 1);
        relationTypeIdMap.set(id, id);
      }

      const insertCard = database.prepare("INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const { card, id } of cardsToInsert) {
        insertCard.run(id, projectId, card.kind, card.title, card.aliasesJson ?? "[]", card.fieldsJson ?? "{}", card.tagsJson ?? "[]", card.contentJson ?? "{}", card.createdAt ?? timestamp, card.updatedAt ?? timestamp, card.revision ?? 1);
      }
      for (const card of data.cards ?? []) {
        const id = cardIdMap.get(card.id)!;
        database
          .prepare("INSERT OR IGNORE INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, id, card.createdAt ?? timestamp);
      }

      const insertRelation = database.prepare("INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
      for (const relation of data.relations ?? []) {
        const sourceId = typeof relation.id === "string" && relation.id.startsWith("relation-") ? relation.id : `relation-${randomUUID()}`;
        const fromCardId = cardIdMap.get(relation.fromCardId);
        const toCardId = cardIdMap.get(relation.toCardId);
        if (!fromCardId || !toCardId) {
          throw new CreationWorkspaceError("invalid-input", "项目包关系引用了不存在的卡片。");
        }
        const relationTypeId = relationTypeIdMap.get(relation.relationType) ?? relation.relationType;
        const existingRelation = database.prepare(
          "SELECT from_card_id, to_card_id, relation_type, note FROM card_relations WHERE id = ?"
        ).get(sourceId) as { from_card_id: string; to_card_id: string; relation_type: string; note: string | null } | undefined;
        if (existingRelation && existingRelation.from_card_id === fromCardId && existingRelation.to_card_id === toCardId &&
          existingRelation.relation_type === relationTypeId && existingRelation.note === (relation.note ?? null)) {
          continue;
        }
        const id = existingRelation ? `relation-${randomUUID()}` : sourceId;
        insertRelation.run(id, projectId, fromCardId, toCardId, relationTypeId, relation.note ?? null, relation.createdAt ?? timestamp);
      }

      const insertSnapshot = database.prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)");
      const allIdMap = new Map<string, string>([
        ...volumeIdMap,
        ...chapterIdMap,
        ...sceneIdMap,
        ...cardIdMap
      ]);
      for (const snapshot of data.snapshots ?? []) {
        const sourceId = typeof snapshot.id === "string" && snapshot.id.startsWith("snapshot-") ? snapshot.id : `snapshot-${randomUUID()}`;
        const id = database.prepare("SELECT 1 FROM snapshots WHERE id = ?").get(sourceId) ? `snapshot-${randomUUID()}` : sourceId;
        insertSnapshot.run(
          id,
          projectId,
          snapshot.subjectType,
          allIdMap.get(snapshot.subjectId) ?? snapshot.subjectId,
          remapJsonStableIds(snapshot.payloadJson ?? "{}", allIdMap),
          snapshot.createdAt ?? timestamp
        );
      }

      // 附件元数据：校验路径/哈希/大小/引用，并按资源文件映射写入新路径。
      const resourceFilesRaw = Array.isArray(runtimeCommand.resourceFiles) ? runtimeCommand.resourceFiles : undefined;
      const resourceFileMap = new Map<string, { targetRelativePath: string; sha256: string; size: number; skip: boolean }>();
      if (resourceFilesRaw) {
        const seenTargets = new Set<string>();
        for (const entry of resourceFilesRaw) {
          const file = isRecord(entry)
            ? {
                relativePath: entry.relativePath,
                targetRelativePath: entry.targetRelativePath,
                sha256: entry.sha256,
                size: entry.size,
                skip: entry.skip
              }
            : null;
          if (
            !file ||
            !isSafeBundleRelativePath(file.relativePath) ||
            !isSafeBundleRelativePath(file.targetRelativePath) ||
            typeof file.sha256 !== "string" ||
            !/^[0-9a-f]{64}$/i.test(file.sha256) ||
            !Number.isInteger(file.size) ||
            Number(file.size) < 0 ||
            Number(file.size) > 500 * 1024 * 1024 ||
            (file.skip !== undefined && typeof file.skip !== "boolean")
          ) {
            throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射无效。");
          }
          if (file.skip !== true && seenTargets.has(file.targetRelativePath)) {
            throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射包含重复目标路径。");
          }
          if (file.skip !== true) seenTargets.add(file.targetRelativePath);
          resourceFileMap.set(file.relativePath, {
            targetRelativePath: file.targetRelativePath,
            sha256: file.sha256.toLowerCase(),
            size: Number(file.size),
            skip: file.skip === true
          });
        }
        if (resourceFileMap.size !== (data.resources?.length ?? 0)) {
          throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射与附件元数据不一致。");
        }
      }

      const insertResource = database.prepare(
        "INSERT INTO resources(id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const insertGlobalResource = database.prepare(
        "INSERT INTO global_card_resources(id, card_id, relative_path, sha256, size, original_name, role, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const resourceSourceIds = new Set<string>();
      const resourceIds = new Set<string>();
      const resourceTargets = new Set<string>();
      for (const resource of data.resources ?? []) {
        const sourceId = typeof resource.id === "string" && resource.id.startsWith("resource-") ? resource.id : `resource-${randomUUID()}`;
        if (resourceSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复附件 ID。");
        resourceSourceIds.add(sourceId);
        if (!isSafeBundleRelativePath(resource.relativePath)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件相对路径无效（禁止绝对路径/穿越）。");
        }
        if (typeof resource.sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(resource.sha256)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件校验和不合法。");
        }
        if (!Number.isInteger(resource.size) || Number(resource.size) < 0 || Number(resource.size) > 500 * 1024 * 1024) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件大小超出允许范围。");
        }
        const sourceCardId = resource.cardId === null || resource.cardId === undefined ? null : resource.cardId;
        if (sourceCardId !== null && !incomingCardIds.has(sourceCardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件引用了不存在的卡片。");
        }
        const cardId = sourceCardId === null ? null : cardIdMap.get(sourceCardId) ?? null;
        const mapped = resourceFileMap.get(resource.relativePath);
        if (resourceFilesRaw) {
          if (!mapped) throw new CreationWorkspaceError("invalid-input", "项目包附件缺少文件落盘映射。");
          if (mapped.sha256 !== resource.sha256.toLowerCase() || mapped.size !== Number(resource.size)) {
            throw new CreationWorkspaceError("invalid-input", "项目包附件文件映射与元数据不一致。");
          }
        }
        const cardMapping = sourceCardId ? cardMappings.find((item) => item.sourceCardId === sourceCardId) : undefined;
        const skipGlobalResource = resource.ownerScope === "card" &&
          (cardMapping?.action === "reused" || cardMapping?.action === "kept-local");
        if (resourceFilesRaw && mapped!.skip !== skipGlobalResource) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件跳过映射与卡片冲突选择不一致。");
        }
        if (skipGlobalResource) continue;
        const existingResourceId = database.prepare(
          "SELECT id FROM resources WHERE id = ? UNION ALL SELECT id FROM global_card_resources WHERE id = ? LIMIT 1"
        ).get(sourceId, sourceId);
        const id = existingResourceId ? `resource-${randomUUID()}` : sourceId;
        resourceIds.add(id);
        const targetRelativePath = mapped?.targetRelativePath ?? resource.relativePath;
        if (resourceTargets.has(targetRelativePath)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件目标路径重复。");
        }
        resourceTargets.add(targetRelativePath);
        const normalizedOriginalName = typeof resource.originalName === "string" && resource.originalName.trim()
          ? resource.originalName.trim().slice(0, 255)
          : null;
        const createdAt = typeof resource.createdAt === "string" && resource.createdAt ? resource.createdAt : timestamp;
        if (resource.ownerScope === "card") {
          if (!cardId) throw new CreationWorkspaceError("invalid-input", "全局卡片附件缺少卡片引用。");
          const role = resource.role === "cover" ? "cover" : "attachment";
          insertGlobalResource.run(id, cardId, targetRelativePath, resource.sha256.toLowerCase(), Number(resource.size), normalizedOriginalName, role, createdAt);
        } else {
          insertResource.run(id, projectId, cardId, targetRelativePath, resource.sha256.toLowerCase(), Number(resource.size), normalizedOriginalName, createdAt);
        }
      }

      // 批注（v2）：scene/card 引用必须落在本包导入的实体上；锚点形状/状态/版本非法或重复 ID 一律拒绝（单事务回滚，零写入）。
      // 锚点与当前正文不再匹配时仍须保留；annotation.list 会将其标记为待重新定位。
      const insertAnnotation = database.prepare(
        "INSERT INTO annotations(id, project_id, scene_id, card_id, anchor_json, note, status, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const annotationSourceIds = new Set<string>();
      const annotationIds = new Set<string>();
      for (const annotation of annotationsToImport) {
        const sourceId = typeof annotation.id === "string" && annotation.id.startsWith("annotation-") ? annotation.id : `annotation-${randomUUID()}`;
        if (annotationSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复批注 ID。");
        annotationSourceIds.add(sourceId);
        const id = database.prepare("SELECT 1 FROM annotations WHERE id = ?").get(sourceId) ? `annotation-${randomUUID()}` : sourceId;
        annotationIds.add(id);
        const sceneId = sceneIdMap.get(annotation.sceneId);
        if (!sceneId) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注引用了不存在的场景。");
        }
        const sourceCardId = annotation.cardId === null || annotation.cardId === undefined ? null : annotation.cardId;
        if (sourceCardId !== null && !incomingCardIds.has(sourceCardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注引用了不存在的卡片。");
        }
        const cardId = sourceCardId === null ? null : cardIdMap.get(sourceCardId) ?? null;
        const anchor = annotation.anchor;
        if (
          !anchor ||
          typeof anchor !== "object" ||
          !Number.isInteger(anchor.blockIndex) ||
          anchor.blockIndex < 0 ||
          !Number.isInteger(anchor.textOffset) ||
          anchor.textOffset < 0 ||
          !Number.isInteger(anchor.textLength) ||
          anchor.textLength < 1 ||
          (anchor.text !== undefined && anchor.text !== null && typeof anchor.text !== "string")
        ) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注锚点无效。");
        }
        const status = annotation.status === "resolved" ? "resolved" : annotation.status === "open" ? "open" : "";
        if (!status) throw new CreationWorkspaceError("invalid-input", "项目包批注状态无效。");
        if (!Number.isInteger(annotation.revision) || Number(annotation.revision) < 1) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注版本无效。");
        }
        const storedAnchor: AnnotationAnchor = {
          blockIndex: Number(anchor.blockIndex),
          textOffset: Number(anchor.textOffset),
          textLength: Number(anchor.textLength),
          ...(typeof anchor.text === "string" && anchor.text ? { text: anchor.text } : {})
        };
        insertAnnotation.run(
          id,
          projectId,
          sceneId,
          cardId,
          JSON.stringify(storedAnchor),
          typeof annotation.note === "string" && annotation.note ? annotation.note.slice(0, 20_000) : "",
          status,
          Number(annotation.revision),
          typeof annotation.createdAt === "string" && annotation.createdAt ? annotation.createdAt : timestamp,
          typeof annotation.updatedAt === "string" && annotation.updatedAt ? annotation.updatedAt : timestamp
        );
      }

      const changes: CreationWorkspaceEvent["changes"] = [
        { entity: "project", id: projectId, action: "created", revision: 1 },
        ...[...volumeIds].map((id) => ({ entity: "volume" as const, id, action: "created" as const, revision: 1 })),
        ...[...chapterIds].map((id) => ({ entity: "chapter" as const, id, action: "created" as const, revision: 1 })),
        ...[...sceneIds].map((id) => ({ entity: "scene" as const, id, action: "created" as const, revision: 1 })),
        ...cardsToInsert.map(({ id }) => ({ entity: "card" as const, id, action: "created" as const, revision: 1 })),
        ...[...resourceIds].map((id) => ({ entity: "resource" as const, id, action: "created" as const, revision: 1 })),
        ...[...annotationIds].map((id) => ({ entity: "annotation" as const, id, action: "created" as const, revision: 1 }))
      ];
      const logged = database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "project.bundle.import", JSON.stringify(changes), timestamp);
      database.exec("COMMIT");
      const counts: ProjectBundleData["counts"] = {
        volumes: volumeIds.size,
        chapters: chapterIds.size,
        scenes: sceneIds.size,
        cards: incomingCardIds.size,
        relations: data.relations?.length ?? 0,
        snapshots: data.snapshots?.length ?? 0,
        resources: resourceIds.size,
        annotations: annotationIds.size
      };
      const result: ProjectBundleImportResult = {
        commandType: "project.bundle.import",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        counts,
        cardMappings
      };
      host.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "project.bundle.import",
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
      throw new CreationWorkspaceError("integrity", "无法导入项目包。");
    }
  }

  return { runProjectBundleExport, previewProjectBundleImport, importProjectBundle };
}
