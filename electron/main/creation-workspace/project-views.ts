/**
 * 项目视图域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：只读聚合视图的组装——项目树、导航、大纲、导出视图、场景正文、项目列表与首页聚合。
 * 全部为纯查询，不写库、不发事件，因此仅需 Database 窄接口，无宿主回调。
 */

import type Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type ChapterNumberingKind,
  type CreationDocument,
  type CreationOutlineChapter,
  type CreationProjectNavigation,
  type CreationProjectOutline,
  type CreationProjectSummary,
  type CreationProjectTree,
  type ProjectExportChapter,
  type ProjectExportScene,
  type ProjectExportView,
  type ProjectExportVolume,
  type ProjectHomeEntry,
  type ProjectHomeView,
  type SceneBodyView,
  type SceneStatus
} from "./types";
import { SCENE_STATUSES, extractSceneText } from "./workspace-utils";
import { parseStoredSetup } from "./project-setup";
import { extractSceneBlocks, parseScenePlanning } from "./bundle-merge";

export interface ProjectViewsModule {
  readProjectTree(projectId: string): CreationProjectTree | null;
  readProjectNavigation(projectId: string): CreationProjectNavigation | null;
  readProjectOutline(projectId: string): CreationProjectOutline | null;
  readProjectExport(projectId: string, includeBlocks?: boolean): ProjectExportView | null;
  readSceneBody(sceneId: string): SceneBodyView | null;
  listProjects(): CreationProjectSummary[];
  readProjectHome(): ProjectHomeView;
}

export function createProjectViewsModule(database: Database): ProjectViewsModule {

  function readProjectTree(projectId: string): CreationProjectTree | null {
    const project = database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = database
      .prepare(
        "SELECT id, project_id, title, sort_order, created_at, updated_at, revision FROM chapters WHERE project_id = ? ORDER BY sort_order, id"
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      title: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const sceneStatement = database.prepare(
      "SELECT id, chapter_id, title, sort_order, body_json, created_at, updated_at, revision FROM scenes WHERE chapter_id = ? ORDER BY sort_order, id"
    );

    return {
      project: {
        id: project.id,
        title: project.title,
        setup: parseStoredSetup(project.setup_json),
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      chapters: chapters.map((chapter) => ({
        id: chapter.id,
        projectId: chapter.project_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes: (sceneStatement.all(chapter.id) as Array<{
          id: string;
          chapter_id: string;
          title: string;
          sort_order: number;
          body_json: string;
          created_at: string;
          updated_at: string;
          revision: number;
        }>).map((scene) => ({
          id: scene.id,
          chapterId: scene.chapter_id,
          title: scene.title,
          sortOrder: scene.sort_order,
          body: JSON.parse(scene.body_json) as CreationProjectTree["chapters"][number]["scenes"][number]["body"],
          createdAt: scene.created_at,
          updatedAt: scene.updated_at,
          revision: scene.revision
        }))
      }))
    };
  }

  function readProjectNavigation(projectId: string): CreationProjectNavigation | null {
    const project = database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = database
      .prepare(
        "SELECT id, project_id, title, sort_order, created_at, updated_at, revision FROM chapters WHERE project_id = ? ORDER BY sort_order, id"
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      title: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const sceneStatement = database.prepare(
      "SELECT id, chapter_id, title, sort_order, created_at, updated_at, revision FROM scenes WHERE chapter_id = ? ORDER BY sort_order, id"
    );

    return {
      project: {
        id: project.id,
        title: project.title,
        setup: parseStoredSetup(project.setup_json),
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      chapters: chapters.map((chapter) => ({
        id: chapter.id,
        projectId: chapter.project_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes: (sceneStatement.all(chapter.id) as Array<{
          id: string;
          chapter_id: string;
          title: string;
          sort_order: number;
          created_at: string;
          updated_at: string;
          revision: number;
        }>).map((scene) => ({
          id: scene.id,
          chapterId: scene.chapter_id,
          title: scene.title,
          sortOrder: scene.sort_order,
          createdAt: scene.created_at,
          updatedAt: scene.updated_at,
          revision: scene.revision
        }))
      }))
    };
  }

  function readProjectOutline(projectId: string): CreationProjectOutline | null {
    const project = database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;
    const setup = parseStoredSetup(project.setup_json);
    const workflow = setup.chapterWorkflow;

    const volumeRows = database
      .prepare(
        "SELECT id, project_id, title, sort_order, created_at, updated_at, revision FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id"
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      title: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;

    const chapterRows = database
      .prepare(
        `SELECT id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number,
                created_at, updated_at, revision
         FROM chapters WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id`
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string;
      volume_id: string | null;
      title: string;
      sort_order: number;
      status: string;
      numbering_kind: ChapterNumberingKind;
      custom_number: string | null;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;

    const hasSceneMeta = Number(database.pragma("user_version", { simple: true })) >= 11;
    const sceneRows = database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.sort_order, s.non_ws_count, s.planning_json,
                ${hasSceneMeta ? "s.summary, s.scene_status" : "'' AS summary, 'planned' AS scene_status"},
                s.created_at, s.updated_at, s.revision
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id`
      )
      .all(projectId) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      sort_order: number;
      non_ws_count: number;
      planning_json: string;
      summary: string;
      scene_status: SceneStatus;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;

    const scenesByChapter = new Map<string, Array<(typeof sceneRows)[number]>>();
    for (const scene of sceneRows) {
      const list = scenesByChapter.get(scene.chapter_id);
      if (list) list.push(scene);
      else scenesByChapter.set(scene.chapter_id, [scene]);
    }

    const buildChapter = (chapter: (typeof chapterRows)[number], autoIndex: number): CreationOutlineChapter => {
      const status = chapter.status || workflow[0] || "起草";
      let displayNumber: string | null = null;
      if (chapter.numbering_kind === "auto") displayNumber = `第${autoIndex}章`;
      else if (chapter.numbering_kind === "prologue") displayNumber = "序章";
      else if (chapter.numbering_kind === "extra") displayNumber = "番外";
      else if (chapter.numbering_kind === "custom") displayNumber = chapter.custom_number || null;
      const scenes = (scenesByChapter.get(chapter.id) ?? []).map((scene) => ({
        id: scene.id,
        chapterId: scene.chapter_id,
        title: scene.title,
        sortOrder: scene.sort_order,
        wordCount: scene.non_ws_count,
        summary: scene.summary,
        status: SCENE_STATUSES.has(scene.scene_status) ? scene.scene_status : "planned" as const,
        planning: parseScenePlanning(scene.planning_json),
        createdAt: scene.created_at,
        updatedAt: scene.updated_at,
        revision: scene.revision
      }));
      return {
        id: chapter.id,
        volumeId: chapter.volume_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        status,
        numbering: chapter.numbering_kind,
        customNumber: chapter.custom_number,
        displayNumber,
        wordCount: scenes.reduce((sum, scene) => sum + scene.wordCount, 0),
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes
      };
    };

    const volumes: CreationProjectOutline["volumes"] = [];
    const volumeById = new Map(volumeRows.map((volume) => [volume.id, volume]));
    const chaptersByVolume = new Map<string, Array<(typeof chapterRows)[number]>>();
    const looseChapters: CreationOutlineChapter[] = [];
    for (const chapter of chapterRows) {
      if (chapter.volume_id && volumeById.has(chapter.volume_id)) {
        const list = chaptersByVolume.get(chapter.volume_id);
        if (list) list.push(chapter);
        else chaptersByVolume.set(chapter.volume_id, [chapter]);
      } else {
        // 无卷章节：按项目内 auto 序编号
        const autoIndex = looseChapters.filter((item) => item.numbering === "auto").length + 1;
        looseChapters.push(buildChapter(chapter, autoIndex));
      }
    }
    for (const volume of volumeRows) {
      const chapterList = chaptersByVolume.get(volume.id) ?? [];
      let autoIndex = 0;
      const chapters = chapterList.map((chapter) => {
        if (chapter.numbering_kind === "auto") autoIndex += 1;
        return buildChapter(chapter, autoIndex);
      });
      volumes.push({
        id: volume.id,
        projectId: volume.project_id,
        title: volume.title,
        sortOrder: volume.sort_order,
        createdAt: volume.created_at,
        updatedAt: volume.updated_at,
        revision: volume.revision,
        wordCount: chapters.reduce((sum, chapter) => sum + (chapter.wordCount ?? 0), 0),
        chapters
      });
    }

    return {
      project: {
        id: project.id,
        title: project.title,
        setup,
        createdAt: project.created_at,
        updatedAt: project.updated_at,
        revision: project.revision
      },
      volumes,
      looseChapters,
      wordCount: volumes.reduce((sum, volume) => sum + (volume.wordCount ?? 0), 0) +
        looseChapters.reduce((sum, chapter) => sum + (chapter.wordCount ?? 0), 0)
    };
  }

  function readProjectExport(projectId: string, includeBlocks = false): ProjectExportView | null {
    const project = database
      .prepare("SELECT id, title FROM projects WHERE id = ?")
      .get(projectId) as { id: string; title: string } | undefined;
    if (!project) return null;

    const volumeRows = database
      .prepare("SELECT id, title FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
      .all(projectId) as Array<{ id: string; title: string }>;
    const chapterRows = database
      .prepare(
        `SELECT id, volume_id, title, status, numbering_kind, custom_number FROM chapters
         WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id`
      )
      .all(projectId) as Array<{
      id: string;
      volume_id: string | null;
      title: string;
      status: string;
      numbering_kind: ChapterNumberingKind;
      custom_number: string | null;
    }>;
    const sceneRows = database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.body_json, s.non_ws_count, s.summary, s.scene_status, s.planning_json
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id`
      )
      .all(projectId) as Array<{
        id: string;
        chapter_id: string;
        title: string;
        body_json: string;
        non_ws_count: number;
        summary: string;
        scene_status: SceneStatus;
        planning_json: string;
      }>;

    const scenesByChapter = new Map<string, ProjectExportScene[]>();
    for (const scene of sceneRows) {
      const list = scenesByChapter.get(scene.chapter_id);
      const entry: ProjectExportScene = {
        id: scene.id,
        title: scene.title,
        summary: scene.summary,
        status: SCENE_STATUSES.has(scene.scene_status) ? scene.scene_status : "planned",
        wordCount: scene.non_ws_count,
        targetWords: parseScenePlanning(scene.planning_json)?.targetWords,
        text: extractSceneText(scene.body_json),
        ...(includeBlocks ? { blocks: extractSceneBlocks(scene.body_json) } : {})
      };
      if (list) list.push(entry);
      else scenesByChapter.set(scene.chapter_id, [entry]);
    }

    const buildChapter = (chapter: (typeof chapterRows)[number], autoIndex: number): ProjectExportChapter => {
      let displayNumber: string | null = null;
      if (chapter.numbering_kind === "auto") displayNumber = `第${autoIndex}章`;
      else if (chapter.numbering_kind === "prologue") displayNumber = "序章";
      else if (chapter.numbering_kind === "extra") displayNumber = "番外";
      else if (chapter.numbering_kind === "custom") displayNumber = chapter.custom_number;
      return {
        id: chapter.id,
        title: chapter.title,
        displayNumber,
        status: chapter.status,
        wordCount: (scenesByChapter.get(chapter.id) ?? []).reduce((sum, scene) => sum + (scene.wordCount ?? 0), 0),
        scenes: scenesByChapter.get(chapter.id) ?? []
      };
    };

    const chaptersByVolume = new Map<string, Array<(typeof chapterRows)[number]>>();
    const looseChapters: ProjectExportChapter[] = [];
    const volumeIds = new Set(volumeRows.map((volume) => volume.id));
    for (const chapter of chapterRows) {
      if (chapter.volume_id && volumeIds.has(chapter.volume_id)) {
        const list = chaptersByVolume.get(chapter.volume_id);
        if (list) list.push(chapter);
        else chaptersByVolume.set(chapter.volume_id, [chapter]);
      } else {
        const autoIndex = looseChapters.filter((item) => item.displayNumber === null).length + 1;
        looseChapters.push(buildChapter(chapter, autoIndex));
      }
    }
    const volumes: ProjectExportVolume[] = volumeRows.map((volume) => {
      let autoIndex = 0;
      const chapters = (chaptersByVolume.get(volume.id) ?? []).map((chapter) => {
        if (chapter.numbering_kind === "auto") autoIndex += 1;
        return buildChapter(chapter, autoIndex);
      });
      return {
        id: volume.id,
        title: volume.title,
        wordCount: chapters.reduce((sum, chapter) => sum + (chapter.wordCount ?? 0), 0),
        chapters
      };
    });
    if (looseChapters.length > 0) {
      volumes.push({
        id: "loose",
        title: "未分卷",
        wordCount: looseChapters.reduce((sum, chapter) => sum + (chapter.wordCount ?? 0), 0),
        chapters: looseChapters
      });
    }
    return {
      projectId: project.id,
      title: project.title,
      wordCount: volumes.reduce((sum, volume) => sum + (volume.wordCount ?? 0), 0),
      volumes
    };
  }

  function readSceneBody(sceneId: string): SceneBodyView | null {
    const scene = database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.body_json, s.updated_at, s.revision, c.project_id
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.id = ?`
      )
      .get(sceneId) as
      | {
          id: string;
          chapter_id: string;
          title: string;
          body_json: string;
          updated_at: string;
          revision: number;
          project_id: string;
        }
      | undefined;
    if (!scene) return null;
    let body: CreationDocument;
    try {
      body = JSON.parse(scene.body_json) as CreationDocument;
    } catch {
      throw new CreationWorkspaceError("integrity", "场景正文数据损坏。");
    }
    return {
      sceneId: scene.id,
      projectId: scene.project_id,
      chapterId: scene.chapter_id,
      title: scene.title,
      body,
      revision: scene.revision,
      updatedAt: scene.updated_at
    };
  }

  function listProjects(): CreationProjectSummary[] {
    const rows = database
      .prepare(
        `WITH chapter_totals AS (
           SELECT project_id, count(*) AS chapter_count
           FROM chapters
           WHERE deleted_at IS NULL
           GROUP BY project_id
         ), scene_totals AS (
           SELECT c.project_id, count(*) AS scene_count
           FROM scenes s
           JOIN chapters c ON c.id = s.chapter_id
           WHERE c.deleted_at IS NULL AND s.deleted_at IS NULL
           GROUP BY c.project_id
         )
         SELECT p.id, p.title, p.setup_json, p.updated_at, p.revision,
                coalesce(chapter_totals.chapter_count, 0) AS chapter_count,
                coalesce(scene_totals.scene_count, 0) AS scene_count
         FROM projects p
         LEFT JOIN chapter_totals ON chapter_totals.project_id = p.id
         LEFT JOIN scene_totals ON scene_totals.project_id = p.id
         ORDER BY p.updated_at DESC, p.id DESC`
      )
      .all() as Array<{
      id: string;
      title: string;
      setup_json: string;
      updated_at: string;
      revision: number;
      chapter_count: number;
      scene_count: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      title: row.title,
      setup: parseStoredSetup(row.setup_json),
      updatedAt: row.updated_at,
      revision: row.revision,
      chapterCount: row.chapter_count,
      sceneCount: row.scene_count
    }));
  }

  /** 项目首页聚合视图：数据库只返回按项目聚合后的计数，不把场景正文加载到调用方。 */
  function readProjectHome(): ProjectHomeView {
    const rows = database
      .prepare(
        `WITH chapter_totals AS (
           SELECT project_id, count(*) AS chapter_count
           FROM chapters
           WHERE deleted_at IS NULL
           GROUP BY project_id
         ), scene_totals AS (
           SELECT c.project_id,
                  count(*) AS scene_count,
                  coalesce(sum(s.non_ws_count), 0) AS current_chars
           FROM scenes s
           JOIN chapters c ON c.id = s.chapter_id
           WHERE c.deleted_at IS NULL AND s.deleted_at IS NULL
           GROUP BY c.project_id
         )
         SELECT p.id, p.title, p.setup_json, p.updated_at, p.revision,
                coalesce(chapter_totals.chapter_count, 0) AS chapter_count,
                coalesce(scene_totals.scene_count, 0) AS scene_count,
                coalesce(scene_totals.current_chars, 0) AS current_chars
         FROM projects p
         LEFT JOIN chapter_totals ON chapter_totals.project_id = p.id
         LEFT JOIN scene_totals ON scene_totals.project_id = p.id
         ORDER BY p.updated_at DESC, p.id DESC`
      )
      .all() as Array<{
      id: string;
      title: string;
      setup_json: string;
      updated_at: string;
      revision: number;
      chapter_count: number;
      scene_count: number;
      current_chars: number;
    }>;
    const projects: ProjectHomeEntry[] = rows.map((row) => ({
      id: row.id,
      title: row.title,
      setup: parseStoredSetup(row.setup_json),
      updatedAt: row.updated_at,
      revision: row.revision,
      chapterCount: row.chapter_count,
      sceneCount: row.scene_count,
      currentChars: row.current_chars
    }));
    return { projects };
  }
  return {
    readProjectTree,
    readProjectNavigation,
    readProjectOutline,
    readProjectExport,
    readSceneBody,
    listProjects,
    readProjectHome
  };
}
