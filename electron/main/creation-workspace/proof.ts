/**
 * 校对域模块（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 职责：全书/单场景校对扫描与分组汇总、持久化忽略记录的读写，以及表存在性惰性缓存。
 * 规则本身（纯文本命中判定）仍由 proof-engine 提供；本模块只负责取数、编排与忽略台账。
 * 通过 Database 窄接口与宿主回调与类主体解耦。
 */

import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { CreationWorkspaceError } from "./types";
import type {
  CreationWorkspaceEvent,
  ProofIgnoreCommand,
  ProofIgnoreEntry,
  ProofIgnoreResult,
  ProofIssue,
  ProofQuery,
  ProofRule,
  ProofScanScope,
  ProofUnignoreCommand,
  ProofView
} from "./types";
import {
  DEFAULT_MAX_PARAGRAPH_CHARS,
  PROOF_RULES,
  PROOF_RULE_BASE_MESSAGE,
  PROOF_TYPO_MAX_TERM_LENGTH,
  buildTypoMaskIndex,
  countOccurrences,
  findAbnormalSpacing,
  findAliasInconsistency,
  findBannedWords,
  findCrutchWords,
  findLongParagraphs,
  findMixedPunctuation,
  findParagraphStartRepeat,
  findRepeatedChars,
  findSuspectedTypos,
  findUnbalancedPunctuation,
  proofLocationKey,
  sceneParagraphs,
  validateProofRule,
  type ProofDictionaryEntry,
  type ProofHits
} from "./proof-engine";
import { parseJsonArray, validateId } from "./workspace-utils";

export interface ProofHost {
  requireProject(projectId: string): void;
  requireScene(sceneId: string): { id: string; chapter_id: string; project_id: string; revision: number };
  assertSameProject(projectId: string, otherProjectId: string, label: string): void;
  emitCommitted(event: CreationWorkspaceEvent): void;
}

export interface ProofModule {
  runProofQuery(query: ProofQuery): ProofView;
  runProofIgnoreList(projectId: string): ProofIgnoreEntry[];
  proofIgnore(command: ProofIgnoreCommand): ProofIgnoreResult;
  proofUnignore(command: ProofUnignoreCommand): ProofIgnoreResult;
}

export function createProofModule(database: Database, host: ProofHost): ProofModule {
  /** 表存在性的惰性缓存：同一个连接内 sqlite_master 不会变化，避免逐次查询。 */
  const tablePresence = new Map<string, boolean>();

function runProofQuery(query: ProofQuery): ProofView {
    const projectId = validateId(query.projectId, "作品");
    host.requireProject(projectId);
    const rules = Array.isArray(query.rules) && query.rules.length > 0
      ? query.rules.filter((rule): rule is ProofRule => PROOF_RULES.has(rule))
      : [...PROOF_RULES];
    const bannedWords = Array.isArray(query.bannedWords)
      ? query.bannedWords
          .map((word) => (typeof word === "string" ? word.trim() : ""))
          .filter((word) => word.length > 0)
      : [];
    const maxParagraphChars = query.maxParagraphChars === undefined ? DEFAULT_MAX_PARAGRAPH_CHARS : query.maxParagraphChars;
    if (!Number.isInteger(maxParagraphChars) || maxParagraphChars < 100 || maxParagraphChars > 5000) {
      throw new CreationWorkspaceError("invalid-input", "超长段落阈值必须为 100..5000 的整数。");
    }
    const limit = query.limit === undefined ? 200 : query.limit;
    if (!Number.isInteger(limit) || limit < 1 || limit > 2000) {
      throw new CreationWorkspaceError("invalid-input", "校对问题上限必须为 1..2000 的整数。");
    }

    let sql: string;
    const params: unknown[] = [];
    if (query.sceneId) {
      const sceneId = validateId(query.sceneId, "场景");
      const scene = host.requireScene(sceneId);
      host.assertSameProject(projectId, scene.project_id, "场景");
      sql = `
        SELECT s.id AS scene_id, s.chapter_id, s.title AS scene_title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND s.id = ?`;
      params.push(sceneId);
    } else {
      sql = `
        SELECT s.id AS scene_id, s.chapter_id, s.title AS scene_title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(projectId);
    }

    const rows = database.prepare(sql).all(...params) as Array<{
      scene_id: string;
      chapter_id: string;
      scene_title: string;
      body_json: string;
      chapter_title: string;
    }>;
    const scannedScenes = rows.length;
    const sceneParagraphsList = rows.map((row) => sceneParagraphs(row.body_json));
    const bookText = sceneParagraphsList.map((paragraphs) => paragraphs.join("\n")).join("\n");

    // 词表（主名 + 别名）与全书词频：别名一致性与疑似错拼共用。
    const dictionary = rules.includes("aliasInconsistency") || rules.includes("suspectedTypo")
      ? loadProofDictionary(projectId)
      : [];
    const variantFrequency = new Map<string, number>();
    for (const entry of dictionary) {
      for (const variant of entry.variants) {
        if (variantFrequency.has(variant)) continue;
        variantFrequency.set(variant, countOccurrences(bookText, variant));
      }
    }
    const vocabulary = new Set<string>();
    /** 词表自身的任意子串都是合法出现，「洛水之蔚」里的「水之」不算错拼。 */
    const protectedSubstrings = new Set<string>();
    let maskIndex = new Map<string, string[]>();
    if (rules.includes("suspectedTypo")) {
      for (const entry of dictionary) {
        for (const variant of entry.variants) {
          vocabulary.add(variant);
          const maxLength = Math.min(variant.length, PROOF_TYPO_MAX_TERM_LENGTH);
          for (let length = 2; length <= maxLength; length += 1) {
            for (let start = 0; start + length <= variant.length; start += 1) {
              protectedSubstrings.add(variant.slice(start, start + length));
            }
          }
        }
      }
      maskIndex = buildTypoMaskIndex(dictionary);
    }

    // 持久化忽略：按「场景 + 规则 + 位置键」精确匹配，与其它位置/项目互不牵连。
    const ignoreRows = loadProofIgnores(projectId);
    const ignoreKeys = new Set(
      ignoreRows.map((entry) => `${entry.sceneId}\u0000${entry.rule}\u0000${entry.locationKey}`)
    );

    const groups = new Map<string, ProofIssue>();
    const order: string[] = [];
    for (let index = 0; index < rows.length; index += 1) {
      const row = rows[index]!;
      const paragraphs = sceneParagraphsList[index]!;
      const hits: ProofHits = [];
      if (rules.includes("repeatedChar")) findRepeatedChars(paragraphs, hits);
      if (rules.includes("unbalancedPunctuation")) findUnbalancedPunctuation(paragraphs, hits);
      if (rules.includes("abnormalSpacing")) findAbnormalSpacing(paragraphs, hits);
      if (rules.includes("longParagraph")) findLongParagraphs(paragraphs, maxParagraphChars, hits);
      if (rules.includes("bannedWord") && bannedWords.length > 0) findBannedWords(paragraphs, bannedWords, hits);
      if (rules.includes("mixedPunctuation")) findMixedPunctuation(paragraphs, hits);
      if (rules.includes("crutchWord")) findCrutchWords(paragraphs, hits);
      if (rules.includes("paragraphStartRepeat")) findParagraphStartRepeat(paragraphs, hits);
      if (rules.includes("aliasInconsistency") && dictionary.length > 0) {
        findAliasInconsistency(paragraphs, dictionary, variantFrequency, hits);
      }
      if (rules.includes("suspectedTypo") && vocabulary.size > 0) {
        findSuspectedTypos(paragraphs, vocabulary, protectedSubstrings, maskIndex, variantFrequency, hits);
      }
      if (hits.length === 0) continue;
      for (const hit of hits) {
        const locationKey = proofLocationKey(hit.rule, hit.paragraphIndex, hit.matchedText);
        const ignored = ignoreKeys.has(`${row.scene_id}\u0000${hit.rule}\u0000${locationKey}`);
        const groupKey = `${row.scene_id}\u0000${hit.rule}`;
        let group = groups.get(groupKey);
        if (!group) {
          group = {
            sceneId: row.scene_id,
            chapterId: row.chapter_id,
            chapterTitle: row.chapter_title,
            sceneTitle: row.scene_title,
            rule: hit.rule,
            message: "",
            snippet: null,
            count: 0,
            ignoredCount: 0,
            locations: []
          };
          groups.set(groupKey, group);
          order.push(groupKey);
        }
        group.locations.push({
          locationKey,
          paragraphIndex: hit.paragraphIndex,
          matchedText: hit.matchedText,
          snippet: hit.snippet,
          detail: hit.detail,
          ignored
        });
        if (ignored) group.ignoredCount += 1;
        else group.count += 1;
      }
    }

    // 汇总：分组内的位置按段落顺序稳定排序，消息基干取首个未忽略位置的说明。
    const active: ProofIssue[] = [];
    const ignoredOnly: ProofIssue[] = [];
    const affectedScenes = new Set<string>();
    let activeTotal = 0;
    let ignoredTotal = 0;
    for (const key of order) {
      const group = groups.get(key)!;
      group.locations.sort(
        (left, right) =>
          left.paragraphIndex - right.paragraphIndex ||
          (left.matchedText < right.matchedText ? -1 : left.matchedText > right.matchedText ? 1 : 0) ||
          (left.locationKey < right.locationKey ? -1 : left.locationKey > right.locationKey ? 1 : 0)
      );
      const activeLocations = group.locations.filter((location) => !location.ignored);
      const baseDetail =
        activeLocations[0]?.detail ?? group.locations[0]?.detail ?? PROOF_RULE_BASE_MESSAGE[group.rule];
      group.message =
        activeLocations.length > 1 ? `${baseDetail}（共 ${activeLocations.length} 处）` : baseDetail;
      group.snippet = (activeLocations.find((location) => location.snippet) ?? group.locations[0])?.snippet ?? null;
      ignoredTotal += group.ignoredCount;
      if (activeLocations.length === 0) {
        ignoredOnly.push(group);
        continue;
      }
      active.push(group);
      activeTotal += activeLocations.length;
      affectedScenes.add(group.sceneId);
    }

    let volumeCount = 0;
    let chapterCount = 1;
    if (!query.sceneId) {
      const counts = database
        .prepare(
          `SELECT
             (SELECT count(*) FROM volumes WHERE project_id = ? AND deleted_at IS NULL) AS volume_count,
             (SELECT count(*) FROM chapters WHERE project_id = ? AND deleted_at IS NULL) AS chapter_count`
        )
        .get(projectId, projectId) as { volume_count: number; chapter_count: number } | undefined;
      volumeCount = counts?.volume_count ?? 0;
      chapterCount = counts?.chapter_count ?? 0;
    }
    const scanScope: ProofScanScope = {
      kind: query.sceneId ? "scene" : "project",
      label: query.sceneId
        ? `单场景扫描：${rows[0]?.chapter_title ?? "未命名章节"} · ${rows[0]?.scene_title ?? "未命名场景"}`
        : `全书扫描：${volumeCount} 卷 / ${chapterCount} 章 / ${scannedScenes} 场景`,
      volumeCount,
      chapterCount,
      sceneCount: scannedScenes,
      rules,
      bannedWords,
      maxParagraphChars
    };

    return {
      projectId,
      scanScope,
      issues: active.slice(0, limit),
      ignoredIssues: query.includeIgnored === true ? ignoredOnly.slice(0, limit) : [],
      scannedScenes,
      affectedScenes: affectedScenes.size,
      total: activeTotal,
      ignoredCount: ignoredTotal,
      rawTotal: activeTotal + ignoredTotal,
      truncated: active.length > limit,
      ignoredTruncated: query.includeIgnored === true && ignoredOnly.length > limit,
      ignoreRecordCount: ignoreRows.length
    };
  }

function hasTable(name: string): boolean {
    const cached = tablePresence.get(name);
    if (cached !== undefined) return cached;
    const found =
      database.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").get(name) !== undefined;
    tablePresence.set(name, found);
    return found;
  }

  /** 项目内卡片词表（主名 + 别名），用于别名一致性与疑似错拼。 */
function loadProofDictionary(projectId: string): ProofDictionaryEntry[] {
    const linked = hasTable("project_card_links");
    const sql = linked
      ? `SELECT c.id AS card_id, c.title, c.aliases_json
         FROM cards c
         WHERE c.deleted_at IS NULL AND (
           c.project_id = ? OR
           EXISTS (SELECT 1 FROM project_card_links l WHERE l.card_id = c.id AND l.project_id = ?)
         )
         ORDER BY c.id`
      : `SELECT c.id AS card_id, c.title, c.aliases_json
         FROM cards c
         WHERE c.deleted_at IS NULL AND c.project_id = ?
         ORDER BY c.id`;
    const rows = (linked
      ? database.prepare(sql).all(projectId, projectId)
      : database.prepare(sql).all(projectId)) as Array<{
      card_id: string;
      title: string;
      aliases_json: string;
    }>;
    const entries: ProofDictionaryEntry[] = [];
    for (const row of rows) {
      const variants: string[] = [];
      const push = (value: unknown): void => {
        if (typeof value !== "string") return;
        const trimmed = value.trim();
        if (trimmed.length < 2 || variants.includes(trimmed)) return;
        variants.push(trimmed);
      };
      push(row.title);
      for (const alias of parseJsonArray(row.aliases_json)) push(alias);
      if (variants.length === 0) continue;
      entries.push({ cardId: row.card_id, title: row.title, variants });
    }
    return entries;
  }

  /** 读取项目的持久化忽略记录（键为 scene + rule + locationKey）。 */
function loadProofIgnores(projectId: string): Array<{
    id: string;
    sceneId: string;
    rule: string;
    locationKey: string;
  }> {
    if (!hasTable("proof_ignores")) return [];
    const rows = database
      .prepare(
        "SELECT id, scene_id, rule, location_key FROM proof_ignores WHERE project_id = ? ORDER BY created_at, id"
      )
      .all(projectId) as Array<{ id: string; scene_id: string; rule: string; location_key: string }>;
    return rows.map((row) => ({
      id: row.id,
      sceneId: row.scene_id,
      rule: row.rule,
      locationKey: row.location_key
    }));
  }

function runProofIgnoreList(projectId: string): ProofIgnoreEntry[] {
    const id = validateId(projectId, "作品");
    host.requireProject(id);
    if (!hasTable("proof_ignores")) return [];
    const rows = database
      .prepare(
        `SELECT i.id, i.scene_id, i.rule, i.location_key, i.matched_text, i.note, i.created_at,
                COALESCE(s.title, '') AS scene_title,
                COALESCE(s.chapter_id, '') AS chapter_id,
                COALESCE(c.title, '') AS chapter_title
         FROM proof_ignores i
         LEFT JOIN scenes s ON s.id = i.scene_id
         LEFT JOIN chapters c ON c.id = s.chapter_id
         WHERE i.project_id = ?
         ORDER BY i.created_at, i.id`
      )
      .all(id) as Array<{
      id: string;
      scene_id: string;
      rule: string;
      location_key: string;
      matched_text: string;
      note: string;
      created_at: string;
      scene_title: string;
      chapter_id: string;
      chapter_title: string;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: id,
      sceneId: row.scene_id,
      sceneTitle: row.scene_title,
      chapterId: row.chapter_id,
      chapterTitle: row.chapter_title,
      rule: PROOF_RULES.has(row.rule as ProofRule) ? (row.rule as ProofRule) : "repeatedChar",
      locationKey: row.location_key,
      matchedText: row.matched_text,
      note: row.note,
      createdAt: row.created_at
    }));
  }

  /**
   * 忽略一个具体位置的校对命中。
   * 只写忽略记录：不修改正文、不推进场景 revision、不产生快照，
   * 因此即使误操作也不会污染稿件历史。
   */
function proofIgnore(command: ProofIgnoreCommand): ProofIgnoreResult {
    const projectId = validateId(command.projectId, "作品");
    host.requireProject(projectId);
    const sceneId = validateId(command.sceneId, "场景");
    const scene = host.requireScene(sceneId);
    host.assertSameProject(projectId, scene.project_id, "场景");
    const rule = validateProofRule(command.rule);
    const locationKey = typeof command.locationKey === "string" ? command.locationKey.trim() : "";
    if (!locationKey || locationKey.length > 200) {
      throw new CreationWorkspaceError("invalid-input", "校对忽略位置无效。");
    }
    const matchedText = typeof command.matchedText === "string" ? command.matchedText.slice(0, 200) : "";
    const note = typeof command.note === "string" ? command.note.slice(0, 200) : "";
    ensureProofIgnoreTable();
    const timestamp = new Date().toISOString();
    const existing = database
      .prepare(
        "SELECT id FROM proof_ignores WHERE project_id = ? AND scene_id = ? AND rule = ? AND location_key = ?"
      )
      .get(projectId, sceneId, rule, locationKey) as { id: string } | undefined;
    if (existing) {
      // 幂等：重复忽略同一位置不产生新记录，也不刷新既有记录时间。
      return {
        commandType: "proof.ignore",
        sequence: -1,
        projectId,
        ignoreIds: [existing.id],
        removed: 0,
        updatedAt: timestamp
      };
    }
    const id = `proofIgnore-${randomUUID()}`;
    database
      .prepare(
        `INSERT INTO proof_ignores (id, project_id, scene_id, rule, location_key, matched_text, note, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(id, projectId, sceneId, rule, locationKey, matchedText, note, timestamp);
    host.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "proof.ignore",
      changes: [{ entity: "proofIgnore", id, action: "created", revision: 0 }]
    });
    return {
      commandType: "proof.ignore",
      sequence: -1,
      projectId,
      ignoreIds: [id],
      removed: 0,
      updatedAt: timestamp
    };
  }

  /** 取消忽略：按记录 ID，或按（场景 + 规则 + 位置键）定位。 */
function proofUnignore(command: ProofUnignoreCommand): ProofIgnoreResult {
    const projectId = validateId(command.projectId, "作品");
    host.requireProject(projectId);
    let ids: string[] = [];
    if (command.ignoreId !== undefined) {
      const ignoreId = validateId(command.ignoreId, "忽略记录");
      const row = database
        .prepare("SELECT id FROM proof_ignores WHERE id = ? AND project_id = ?")
        .get(ignoreId, projectId) as { id: string } | undefined;
      if (!row) throw new CreationWorkspaceError("not-found", "忽略记录不存在。");
      ids = [row.id];
    } else {
      if (command.sceneId === undefined || command.rule === undefined || command.locationKey === undefined) {
        throw new CreationWorkspaceError("invalid-input", "取消忽略需要记录 ID，或场景 + 规则 + 位置键。");
      }
      const sceneId = validateId(command.sceneId, "场景");
      const rule = validateProofRule(command.rule);
      const locationKey = typeof command.locationKey === "string" ? command.locationKey.trim() : "";
      if (!locationKey) throw new CreationWorkspaceError("invalid-input", "校对忽略位置无效。");
      ids = (
        database
          .prepare(
            "SELECT id FROM proof_ignores WHERE project_id = ? AND scene_id = ? AND rule = ? AND location_key = ?"
          )
          .all(projectId, sceneId, rule, locationKey) as Array<{ id: string }>
      ).map((row) => row.id);
    }
    const remove = database.prepare("DELETE FROM proof_ignores WHERE id = ? AND project_id = ?");
    let removed = 0;
    for (const id of ids) removed += remove.run(id, projectId).changes;
    const timestamp = new Date().toISOString();
    host.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "proof.unignore",
      changes: ids.map((id) => ({ entity: "proofIgnore", id, action: "deleted" as const, revision: 0 }))
    });
    return {
      commandType: "proof.unignore",
      sequence: -1,
      projectId,
      ignoreIds: ids,
      removed,
      updatedAt: timestamp
    };
  }

  /** 兜底建表：让 v9 测试夹具等未走迁移链的连接也能安全读写忽略记录。 */
function ensureProofIgnoreTable(): void {
    if (hasTable("proof_ignores")) return;
    database.exec(`
      CREATE TABLE IF NOT EXISTS proof_ignores (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL,
        scene_id TEXT NOT NULL,
        rule TEXT NOT NULL,
        location_key TEXT NOT NULL,
        matched_text TEXT NOT NULL DEFAULT '',
        note TEXT NOT NULL DEFAULT '',
        created_at TEXT NOT NULL
      );
      CREATE UNIQUE INDEX IF NOT EXISTS idx_proof_ignores_location
        ON proof_ignores(project_id, scene_id, rule, location_key);
      CREATE INDEX IF NOT EXISTS idx_proof_ignores_project ON proof_ignores(project_id);
    `);
    tablePresence.set("proof_ignores", true);
  }

  return { runProofQuery, runProofIgnoreList, proofIgnore, proofUnignore };
}
