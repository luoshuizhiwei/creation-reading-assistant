/**
 * 创作工作区搜索域（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：项目内全文搜索（场景/卡片/章节/项目四类 scope）。
 * 通过 Database 窄接口与宿主解耦；共享工具来自 workspace-utils。
 */

import type Database from "better-sqlite3";
import { CreationWorkspaceError } from "./types";
import { escapeLike, extractSceneText, isRecord, jsonStringValues, makeSnippet, DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT, SEARCH_SCOPES } from "./workspace-utils";
import type { CreationSearchHit, CreationSearchQuery, CreationSearchScope, CreationSearchView } from "../../../src/types/creation";

export interface SearchModule {
  runSearch(query: CreationSearchQuery): CreationSearchView;
}

export function createSearchModule(database: Database): SearchModule {
  const searchScenes = (
    keyword: string,
    projectId: string | undefined,
    chapterStatuses: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void => {
    const like = `%${escapeLike(keyword)}%`;
    const params: unknown[] = [like, like];
    let sql = `
      SELECT s.id AS scene_id, s.title AS scene_title, s.body_json, s.updated_at,
             c.id AS chapter_id, c.title AS chapter_title, c.status AS chapter_status,
             p.id AS project_id, p.title AS project_title
      FROM scenes s
      JOIN chapters c ON c.id = s.chapter_id
      JOIN projects p ON p.id = c.project_id
      WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL
        AND (s.title LIKE ? ESCAPE '\\' OR s.body_json LIKE ? ESCAPE '\\')`;
    if (projectId) {
      sql += " AND c.project_id = ?";
      params.push(projectId);
    }
    if (chapterStatuses.length > 0) {
      sql += ` AND c.status IN (${chapterStatuses.map(() => "?").join(", ")})`;
      params.push(...chapterStatuses);
    }
    sql += " ORDER BY s.updated_at DESC, s.id DESC LIMIT ?";
    params.push(limit);
    const rows = database.prepare(sql).all(...params) as Array<{
      scene_id: string;
      scene_title: string;
      body_json: string;
      updated_at: string;
      chapter_id: string;
      chapter_title: string;
      chapter_status: string;
      project_id: string;
      project_title: string;
    }>;
    for (const row of rows) {
      const plain = extractSceneText(row.body_json);
      hits.push({
        kind: "scene",
        id: row.scene_id,
        projectId: row.project_id,
        projectTitle: row.project_title,
        title: row.scene_title,
        snippet: makeSnippet(plain, keyword) ?? makeSnippet(row.scene_title, keyword),
        chapterId: row.chapter_id,
        chapterTitle: row.chapter_title,
        chapterStatus: row.chapter_status || undefined,
        updatedAt: row.updated_at
      });
    }
  };

  const searchCards = (
    keyword: string,
    projectId: string | undefined,
    cardKinds: string[],
    tags: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void => {
    const like = `%${escapeLike(keyword)}%`;
    const params: unknown[] = [like, like, like, like, like];
    let sql = `
      SELECT c.id, c.project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json, c.content_json, c.updated_at,
             p.title AS project_title
      FROM cards c
      JOIN projects p ON p.id = c.project_id
      WHERE c.deleted_at IS NULL
        AND (c.title LIKE ? ESCAPE '\\' OR c.aliases_json LIKE ? ESCAPE '\\'
             OR c.fields_json LIKE ? ESCAPE '\\' OR c.tags_json LIKE ? ESCAPE '\\'
             OR c.content_json LIKE ? ESCAPE '\\')`;
    if (projectId) {
      sql += " AND c.project_id = ?";
      params.push(projectId);
    }
    if (cardKinds.length > 0) {
      sql += ` AND c.kind IN (${cardKinds.map(() => "?").join(", ")})`;
      params.push(...cardKinds);
    }
    for (const tag of tags) {
      sql += " AND c.tags_json LIKE ?";
      params.push(`%"${escapeLike(tag)}"%`);
    }
    sql += " ORDER BY c.updated_at DESC, c.id DESC LIMIT ?";
    params.push(limit);
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string;
      kind: string;
      title: string;
      aliases_json: string;
      fields_json: string;
      tags_json: string;
      content_json: string;
      updated_at: string;
      project_title: string;
    }>;
    for (const row of rows) {
      const searchable = [
        row.title,
        ...jsonStringValues(row.aliases_json),
        ...jsonStringValues(row.fields_json),
        ...jsonStringValues(row.tags_json),
        ...jsonStringValues(row.content_json)
      ].join("");
      let tagsOut: string[] | undefined;
      try {
        const parsed = JSON.parse(row.tags_json) as unknown;
        if (Array.isArray(parsed)) tagsOut = parsed.filter((tag): tag is string => typeof tag === "string");
      } catch {
        tagsOut = undefined;
      }
      hits.push({
        kind: "card",
        id: row.id,
        projectId: row.project_id,
        projectTitle: row.project_title,
        title: row.title,
        snippet: makeSnippet(searchable, keyword),
        cardKind: row.kind,
        tags: tagsOut,
        updatedAt: row.updated_at
      });
    }
  };

  const searchChapters = (
    keyword: string,
    projectId: string | undefined,
    chapterStatuses: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void => {
    const like = `%${escapeLike(keyword)}%`;
    const params: unknown[] = [like];
    let sql = `
      SELECT c.id, c.title, c.status, c.updated_at, p.id AS project_id, p.title AS project_title
      FROM chapters c
      JOIN projects p ON p.id = c.project_id
      WHERE c.deleted_at IS NULL AND c.title LIKE ? ESCAPE '\\'`;
    if (projectId) {
      sql += " AND c.project_id = ?";
      params.push(projectId);
    }
    if (chapterStatuses.length > 0) {
      sql += ` AND c.status IN (${chapterStatuses.map(() => "?").join(", ")})`;
      params.push(...chapterStatuses);
    }
    sql += " ORDER BY c.updated_at DESC, c.id DESC LIMIT ?";
    params.push(limit);
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      title: string;
      status: string;
      updated_at: string;
      project_id: string;
      project_title: string;
    }>;
    for (const row of rows) {
      hits.push({
        kind: "chapter",
        id: row.id,
        projectId: row.project_id,
        projectTitle: row.project_title,
        title: row.title,
        snippet: null,
        chapterStatus: row.status || undefined,
        updatedAt: row.updated_at
      });
    }
  };

  const searchProjects = (keyword: string, projectId: string | undefined, limit: number, hits: CreationSearchHit[]): void => {
    const like = `%${escapeLike(keyword)}%`;
    const params: unknown[] = [like];
    let sql = "SELECT id, title, updated_at FROM projects WHERE title LIKE ? ESCAPE '\\'";
    if (projectId) {
      sql += " AND id = ?";
      params.push(projectId);
    }
    sql += " ORDER BY updated_at DESC, id DESC LIMIT ?";
    params.push(limit);
    const rows = database.prepare(sql).all(...params) as Array<{
      id: string;
      title: string;
      updated_at: string;
    }>;
    for (const row of rows) {
      hits.push({
        kind: "project",
        id: row.id,
        projectId: row.id,
        projectTitle: row.title,
        title: row.title,
        snippet: null,
        updatedAt: row.updated_at
      });
    }
  };

  const runSearch = (query: CreationSearchQuery): CreationSearchView => {
    const text = typeof query.text === "string" ? query.text.trim() : "";
    if (!text) throw new CreationWorkspaceError("invalid-input", "搜索关键词不能为空。");
    const limit = query.limit === undefined ? DEFAULT_SEARCH_LIMIT : query.limit;
    if (!Number.isInteger(limit) || limit < 1 || limit > MAX_SEARCH_LIMIT) {
      throw new CreationWorkspaceError("invalid-input", `搜索返回上限必须为 1..${MAX_SEARCH_LIMIT} 的整数。`);
    }
    const scopes = Array.isArray(query.scopes) && query.scopes.length > 0
      ? query.scopes.filter((scope): scope is CreationSearchScope => SEARCH_SCOPES.has(scope))
      : [...SEARCH_SCOPES];
    if (scopes.length === 0) return { query: text, hits: [], total: 0 };
    const projectId = typeof query.projectId === "string" && query.projectId.trim() ? query.projectId.trim() : undefined;
    const filters = isRecord(query.filters) ? query.filters : undefined;
    const cardKinds = Array.isArray(filters?.cardKinds)
      ? filters.cardKinds.filter((kind): kind is string => typeof kind === "string" && kind.length > 0)
      : [];
    const chapterStatuses = Array.isArray(filters?.chapterStatuses)
      ? filters.chapterStatuses.filter((status): status is string => typeof status === "string" && status.length > 0)
      : [];
    const tags = Array.isArray(filters?.tags)
      ? filters.tags.filter((tag): tag is string => typeof tag === "string" && tag.length > 0)
      : [];
    const hits: CreationSearchHit[] = [];
    const remaining = (): number => limit - hits.length;
    if (scopes.includes("scene")) searchScenes(text, projectId, chapterStatuses, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("card")) searchCards(text, projectId, cardKinds, tags, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("chapter")) searchChapters(text, projectId, chapterStatuses, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("project")) searchProjects(text, projectId, remaining(), hits);
    return { query: text, hits, total: hits.length };
  };

  return { runSearch };
}