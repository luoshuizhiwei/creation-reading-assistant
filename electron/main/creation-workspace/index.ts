import { mkdir } from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type ChapterCreateCommand,
  type ChapterDeleteCommand,
  type ChapterMergeCommand,
  type ChapterMoveCommand,
  type ChapterNumberingKind,
  type ChapterRenameCommand,
  type ChapterReorderCommand,
  type ChapterSetNumberingCommand,
  type ChapterSetStatusCommand,
  type ChaptersSetStatusCommand,
  type ChapterSplitCommand,
  type CreationCommand,
  type CreationDocument,
  type CreationIntegrityReport,
  type CreationOutlineChapter,
  type CreationProjectOutline,
  type CreationReadQuery,
  type CreationReadResult,
  type CreationStructureResult,
  type ListProjectsQuery,
  type CreationProjectSetup,
  type CreationProjectSummary,
  type CreationProjectTemplate,
  type CreationProjectNavigation,
  type CreationProjectTree,
  type CreationTransactionResult,
  type ReadProjectNavigationQuery,
  type ReadProjectOutlineQuery,
  type ReadProjectTreeQuery,
  type ReadSceneBodyQuery,
  type SceneBodyView,
  type SceneCreateCommand,
  type SceneDeleteCommand,
  type SceneMoveCommand,
  type SceneRenameCommand,
  type SceneReorderCommand,
  type CreateProjectCommand,
  type CreateProjectResult,
  type StructureCommand,
  type UpdateSceneBodyCommand,
  type UpdateSceneBodyResult,
  type VolumeCreateCommand,
  type VolumeDeleteCommand,
  type VolumeRenameCommand,
  type VolumeReorderCommand,
  type CardCommand,
  type CardCreateCommand,
  type CardDeleteCommand,
  type CardFieldKind,
  type CardFieldSchema,
  type CardReadQuery,
  type CardRelation,
  type CardRelationCreateCommand,
  type CardRelationDeleteCommand,
  type CardRelationsQuery,
  type CardSummary,
  type CardType,
  type CardTypeCreateCommand,
  type CardTypesListQuery,
  type CardUpdateCommand,
  type CardsListQuery,
  type RelationType,
  type RelationTypeCreateCommand,
  type RelationTypesListQuery,
  type HistoryCommand,
  type SnapshotCreateCommand,
  type SnapshotInfo,
  type SnapshotListQuery,
  type SnapshotRestoreCommand,
  type SnapshotSubjectType,
  type TrashEntityKind,
  type TrashItem,
  type TrashListQuery,
  type TrashPurgeCommand,
  type TrashRestoreCommand,
  type ProjectExportChapter,
  type ProjectExportQuery,
  type ProjectExportScene,
  type ProjectExportView,
  type ProjectExportVolume,
  type CreationSearchHit,
  type CreationSearchQuery,
  type CreationSearchScope,
  type CreationSearchView,
  type ReplaceApplyCommand,
  type ReplaceApplyResult,
  type ReplacePreviewHit,
  type ReplacePreviewQuery,
  type ReplacePreviewView,
  type ReplaceScope,
  type ProjectDailyStat,
  type ProjectStatsView,
  type SessionDeleteCommand,
  type SessionEntry,
  type SessionListQuery,
  type SessionReportCommand,
  type SessionReportResult,
  type StatsViewQuery,
  type ProofIssue,
  type ProofQuery,
  type ProofRule,
  type ProofView,
  type InboxCreateCommand,
  type InboxDeleteCommand,
  type InboxItem,
  type InboxItemResult,
  type InboxListQuery,
  type InboxReadQuery,
  type InboxUpdateCommand,
  type CreationWatchScope,
  type CreationWorkspace,
  type CreationWorkspaceEvent,
  type CreationWorkspaceListener,
  type OpenCreationWorkspaceOptions
} from "./types";

const SCHEMA_VERSION = 6;

const TARGET_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const SCENE_TEXT_BLOCKS = new Set(["paragraph", "quoteLetter", "centeredText", "authorNote"]);
const SCENE_MARKS = new Set(["bold", "italic"]);
const CHAPTER_NUMBERING_KINDS = new Set<ChapterNumberingKind>(["auto", "prologue", "extra", "custom"]);
const CARD_FIELD_KINDS = new Set<CardFieldKind>([
  "text",
  "multiline",
  "number",
  "date",
  "select",
  "multiSelect",
  "boolean",
  "cardRef",
  "url",
  "attachment"
]);

const STRUCTURE_COMMAND_TYPES = new Set<string>([
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

const CARD_COMMAND_TYPES = new Set<string>([
  "cardType.create",
  "relationType.create",
  "card.create",
  "card.update",
  "card.delete",
  "cardRelation.create",
  "cardRelation.delete"
]);

const HISTORY_COMMAND_TYPES = new Set<string>([
  "trash.restore",
  "trash.purge",
  "snapshot.create",
  "snapshot.restore"
]);

const TRASH_ENTITY_KINDS = new Set<TrashEntityKind>(["volume", "chapter", "scene", "card"]);

const SEARCH_SCOPES = new Set<CreationSearchScope>(["scene", "card", "chapter", "project"]);
const DEFAULT_SEARCH_LIMIT = 50;
const MAX_SEARCH_LIMIT = 200;

const REPLACE_SCOPES = new Set<ReplaceScope>(["project", "volume", "chapter", "scene"]);
const DEFAULT_REPLACE_PREVIEW_LIMIT = 200;
const MAX_REPLACE_PREVIEW_LIMIT = 1000;
const MAX_REGEX_LENGTH = 200;
const REGEX_FORBIDDEN_PATTERN = /\(\?[=<!]|\\[1-9]/;

const PROOF_RULES = new Set<ProofRule>([
  "repeatedChar",
  "unbalancedPunctuation",
  "abnormalSpacing",
  "longParagraph",
  "bannedWord"
]);
const DEFAULT_MAX_PARAGRAPH_CHARS = 500;
const PROOF_PAIR_PUNCTUATION: Array<[string, string]> = [
  ["「", "」"],
  ["『", "』"],
  ["（", "）"],
  ["《", "》"],
  ["【", "】"],
  ["“", "”"]
];

const REQUIRED_TABLES = [
  "workspace_meta",
  "projects",
  "volumes",
  "chapters",
  "scenes",
  "cards",
  "card_types",
  "relation_types",
  "card_relations",
  "resources",
  "snapshots",
  "change_log",
  "writing_sessions",
  "inbox_items",
  "scenes_fts"
] as const;

const REQUIRED_INDEXES = [
  "idx_volumes_project_order",
  "idx_chapters_volume_order",
  "idx_chapters_project_order",
  "idx_scenes_chapter_order",
  "idx_cards_project_kind",
  "idx_card_types_project",
  "idx_relation_types_project",
  "idx_card_relations_from",
  "idx_card_relations_to",
  "idx_resources_project",
  "idx_snapshots_project_created",
  "idx_writing_sessions_project_started",
  "idx_inbox_items_updated"
] as const;

function createSection(issues: Array<{ code: string; message: string }>) {
  return { ok: issues.length === 0, issues };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isValidSceneDocument(value: unknown): value is CreationDocument {
  if (!isRecord(value) || value.type !== "doc" || !Array.isArray(value.content)) return false;
  for (const block of value.content) {
    if (!isRecord(block) || typeof block.type !== "string") return false;
    if (block.type === "sceneBreak") {
      if (block.content !== undefined) return false;
      continue;
    }
    if (!SCENE_TEXT_BLOCKS.has(block.type)) return false;
    if (block.content === undefined) continue;
    if (!Array.isArray(block.content)) return false;
    for (const inline of block.content) {
      if (!isRecord(inline) || inline.type !== "text" || typeof inline.text !== "string") return false;
      if (inline.marks === undefined) continue;
      if (!Array.isArray(inline.marks)) return false;
      for (const mark of inline.marks) {
        if (!isRecord(mark) || typeof mark.type !== "string" || !SCENE_MARKS.has(mark.type)) return false;
      }
    }
  }
  return true;
}

/** 场景非空白正文字数（不含标点），供大纲与卡片板显示。 */
function countSceneWords(bodyJson: string): number {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return 0;
  }
  const parts: string[] = [];
  const collect = (nodes: unknown[]): void => {
    for (const node of nodes) {
      if (!isRecord(node)) continue;
      if (node.type === "text" && typeof node.text === "string") parts.push(node.text);
      else if (Array.isArray(node.content)) collect(node.content);
    }
  };
  collect(document.content ?? []);
  return parts.join("").replace(/[\p{P}\p{S}\p{Z}\s]/gu, "").length;
}

/** 场景正文纯文本：每个块一段，块间空行，场景分隔占位。 */
function extractSceneText(bodyJson: string): string {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return "";
  }
  const blocks: string[] = [];
  for (const block of document.content ?? []) {
    if (!isRecord(block)) continue;
    if (block.type === "sceneBreak") {
      blocks.push("　　");
      continue;
    }
    if (!Array.isArray(block.content)) continue;
    const parts: string[] = [];
    const collect = (nodes: unknown[]): void => {
      for (const node of nodes) {
        if (!isRecord(node)) continue;
        if (node.type === "text" && typeof node.text === "string") parts.push(node.text);
        else if (Array.isArray(node.content)) collect(node.content);
      }
    };
    collect(block.content);
    if (parts.length > 0) blocks.push(parts.join(""));
  }
  return blocks.join("\n\n");
}

/** 收件箱/卡片的 JSON 数组字段（tags 等）安全解析。 */
function parseJsonArray(json: string): string[] {
  try {
    const parsed = JSON.parse(json) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
  } catch {
    return [];
  }
}

/** 卡片/字段/标签 JSON 的纯文本值（用于搜索上下文片段）。 */
function jsonStringValues(json: string): string[] {
  const out: string[] = [];
  const collect = (value: unknown): void => {
    if (typeof value === "string") out.push(value);
    else if (Array.isArray(value)) value.forEach(collect);
    else if (isRecord(value)) Object.values(value).forEach(collect);
  };
  try {
    collect(JSON.parse(json));
  } catch {
    out.push(json);
  }
  return out;
}

/** 命中上下文片段：围绕首个命中位置截取，超出部分以省略号标注。 */
function makeSnippet(text: string, keyword: string, radius = 16): string | null {
  const index = text.toLowerCase().indexOf(keyword.toLowerCase());
  if (index < 0) return null;
  const start = Math.max(0, index - radius);
  const end = Math.min(text.length, index + keyword.length + radius * 2);
  const before = start > 0 ? "…" : "";
  const after = end < text.length ? "…" : "";
  return `${before}${text.slice(start, end)}${after}`;
}

/** LIKE 通配符转义，避免用户输入中的 %/_ 影响匹配。 */
function escapeLike(value: string): string {
  return value.replace(/[\\%_]/g, (ch) => `\\${ch}`);
}

/** 校验受限正则：长度限制、禁用 lookaround/后向引用，且必须可编译。 */
function compileReplaceRegex(source: string): RegExp {
  if (source.length > MAX_REGEX_LENGTH) {
    throw new CreationWorkspaceError("invalid-input", `正则长度不能超过 ${MAX_REGEX_LENGTH} 个字符。`);
  }
  if (REGEX_FORBIDDEN_PATTERN.test(source)) {
    throw new CreationWorkspaceError("invalid-input", "受限正则不支持环视断言或后向引用。");
  }
  try {
    return new RegExp(source, "g");
  } catch {
    throw new CreationWorkspaceError("invalid-input", "正则表达式无效。");
  }
}

interface TextMatcher {
  count(text: string): number;
  snippets(text: string, max: number): string[];
  apply(text: string): { text: string; hits: number };
}

function plainMatcher(find: string, replaceWith: string): TextMatcher {
  return {
    count: (text) => countPlainHits(text, find),
    snippets: (text, max) => {
      const out: string[] = [];
      let index = 0;
      while (out.length < max && (index = text.indexOf(find, index)) >= 0) {
        const radius = 14;
        const start = Math.max(0, index - radius);
        const end = Math.min(text.length, index + find.length + radius * 2);
        out.push(`${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`);
        index += find.length;
      }
      return out;
    },
    apply: (text) => {
      const hits = countPlainHits(text, find);
      return hits === 0 ? { text, hits: 0 } : { text: text.split(find).join(replaceWith), hits };
    }
  };
}

function regexMatcher(regex: RegExp, replaceWith: string): TextMatcher {
  return {
    count: (text) => {
      let hits = 0;
      regex.lastIndex = 0;
      while (regex.exec(text) !== null) hits += 1;
      return hits;
    },
    snippets: (text, max) => {
      const out: string[] = [];
      regex.lastIndex = 0;
      let match: RegExpExecArray | null;
      while (out.length < max && (match = regex.exec(text)) !== null) {
        if (match[0].length === 0) regex.lastIndex += 1;
        const radius = 14;
        const start = Math.max(0, match.index - radius);
        const end = Math.min(text.length, match.index + match[0].length + radius * 2);
        out.push(`${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`);
      }
      return out;
    },
    apply: (text) => {
      const replaced = text.replace(regex, replaceWith);
      return { text: replaced, hits: countRegexHits(regex, text) };
    }
  };
}

function countRegexHits(regex: RegExp, text: string): number {
  let hits = 0;
  regex.lastIndex = 0;
  while (regex.exec(text) !== null) {
    hits += 1;
    if (regex.lastIndex === 0) regex.lastIndex += 1;
  }
  return hits;
}

/** 返回普通文本在全文中的命中次数。 */
function countPlainHits(text: string, find: string): number {
  if (!find) return 0;
  let count = 0;
  let index = 0;
  while ((index = text.indexOf(find, index)) >= 0) {
    count += 1;
    index += find.length;
  }
  return count;
}

/** 段落文本：把场景正文按块拆成段落（含 sceneBreak 分隔符）。 */
function sceneParagraphs(bodyJson: string): string[] {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return [];
  }
  const blocks: string[] = [];
  for (const block of document.content ?? []) {
    if (!isRecord(block)) continue;
    if (block.type === "sceneBreak") {
      blocks.push("");
      continue;
    }
    if (!Array.isArray(block.content)) continue;
    const parts: string[] = [];
    const collect = (nodes: unknown[]): void => {
      for (const node of nodes) {
        if (!isRecord(node)) continue;
        if (node.type === "text" && typeof node.text === "string") parts.push(node.text);
        else if (Array.isArray(node.content)) collect(node.content);
      }
    };
    collect(block.content);
    blocks.push(parts.join(""));
  }
  return blocks;
}

/** 连续重复字：同一汉字连续出现 ≥3 次。 */
function findRepeatedChars(text: string, out: Array<{ rule: ProofRule; message: string; snippet: string | null }>): void {
  const repeated = /([\p{Script=Han}])\1{2,}/gu;
  let match: RegExpExecArray | null;
  while ((match = repeated.exec(text)) !== null) {
    const radius = 8;
    const start = Math.max(0, match.index - radius);
    const end = Math.min(text.length, match.index + match[0].length + radius * 2);
    out.push({
      rule: "repeatedChar",
      message: `连续重复字「${match[0].slice(0, 6)}」`,
      snippet: `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`
    });
  }
}

/** 成对标点：括号/引号开闭数量不等。 */
function findUnbalancedPunctuation(text: string, out: Array<{ rule: ProofRule; message: string; snippet: string | null }>): void {
  for (const [open, close] of PROOF_PAIR_PUNCTUATION) {
    let openCount = 0;
    let closeCount = 0;
    let firstIndex = -1;
    for (let index = 0; index < text.length; index += 1) {
      const character = text[index]!;
      if (character === open) {
        if (firstIndex < 0) firstIndex = index;
        openCount += 1;
      } else if (character === close) {
        if (firstIndex < 0) firstIndex = index;
        closeCount += 1;
      }
    }
    if (openCount === closeCount) continue;
    const radius = 8;
    const start = Math.max(0, firstIndex - radius);
    const end = Math.min(text.length, firstIndex + radius * 2);
    out.push({
      rule: "unbalancedPunctuation",
      message: `「${open}${close}」不配对（开 ${openCount} 个、闭 ${closeCount} 个）`,
      snippet: `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`
    });
  }
}

/** 异常空格：段首半角空格、连续 2+ 全角空格、半角与全角空格混用。 */
function findAbnormalSpacing(
  paragraphs: string[],
  out: Array<{ rule: ProofRule; message: string; snippet: string | null }>
): void {
  let leadingHalfWidth = 0;
  let repeatedFullWidth = 0;
  let mixedWidth = 0;
  let mixedSnippet = "";
  for (const paragraph of paragraphs) {
    if (/^[ ]/.test(paragraph)) leadingHalfWidth += 1;
    if (/　{2,}/u.test(paragraph)) repeatedFullWidth += 1;
    if (/[ ]/.test(paragraph) && /　/.test(paragraph)) {
      mixedWidth += 1;
      if (!mixedSnippet) {
        const index = Math.max(paragraph.indexOf(" "), paragraph.indexOf("　"));
        mixedSnippet = paragraph.slice(Math.max(0, index - 6), index + 14);
      }
    }
  }
  if (leadingHalfWidth > 0) {
    out.push({ rule: "abnormalSpacing", message: `${leadingHalfWidth} 个段落以半角空格开头`, snippet: null });
  }
  if (repeatedFullWidth > 0) {
    out.push({
      rule: "abnormalSpacing",
      message: `${repeatedFullWidth} 个段落含连续两个以上全角空格`,
      snippet: null
    });
  }
  if (mixedWidth > 0) {
    out.push({
      rule: "abnormalSpacing",
      message: `${mixedWidth} 个段落同时出现半角与全角空格`,
      snippet: mixedSnippet ? `…${mixedSnippet}…` : null
    });
  }
}

/** 超长段落：单段字符数超过阈值。 */
function findLongParagraphs(
  paragraphs: string[],
  maxChars: number,
  out: Array<{ rule: ProofRule; message: string; snippet: string | null }>
): void {
  let count = 0;
  let snippet: string | null = null;
  for (const paragraph of paragraphs) {
    if (paragraph.length > maxChars) {
      count += 1;
      if (!snippet) snippet = `…${paragraph.slice(0, 60)}…`;
    }
  }
  if (count > 0) {
    out.push({ rule: "longParagraph", message: `${count} 个段落超过 ${maxChars} 字符`, snippet });
  }
}

/** 禁用词：子串命中。 */
function findBannedWords(
  text: string,
  bannedWords: string[],
  out: Array<{ rule: ProofRule; message: string; snippet: string | null }>
): void {
  for (const word of bannedWords) {
    if (!word) continue;
    let found = false;
    let firstIndex = -1;
    for (let index = 0; index < text.length; index += 1) {
      if (text.startsWith(word, index)) {
        if (firstIndex < 0) firstIndex = index;
        found = true;
        break;
      }
    }
    if (!found) continue;
    const radius = 8;
    const start = Math.max(0, firstIndex - radius);
    const end = Math.min(text.length, firstIndex + word.length + radius * 2);
    out.push({
      rule: "bannedWord",
      message: `命中禁用词「${word}」`,
      snippet: `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`
    });
  }
}

function isConstraintError(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    String((error as { code: unknown }).code).startsWith("SQLITE_CONSTRAINT")
  );
}

/** 回收站到期清理：永久删除超过 30 天的软删除实体（卷级联章节/场景，卡片级联关系）。 */
function purgeExpiredTrash(database: Database): void {
  const cutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const volumeRows = database
    .prepare("SELECT id FROM volumes WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const volume of volumeRows) {
    const chapterRows = database
      .prepare("SELECT id FROM chapters WHERE volume_id = ?")
      .all(volume.id) as Array<{ id: string }>;
    for (const chapter of chapterRows) {
      database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
    }
    database.prepare("DELETE FROM chapters WHERE volume_id = ?").run(volume.id);
    database.prepare("DELETE FROM volumes WHERE id = ?").run(volume.id);
  }
  const chapterRows = database
    .prepare("SELECT id FROM chapters WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const chapter of chapterRows) {
    database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
    database.prepare("DELETE FROM chapters WHERE id = ?").run(chapter.id);
  }
  database.prepare("DELETE FROM scenes WHERE deleted_at IS NOT NULL AND deleted_at < ?").run(cutoff);
  const cardRows = database
    .prepare("SELECT id FROM cards WHERE deleted_at IS NOT NULL AND deleted_at < ?")
    .all(cutoff) as Array<{ id: string }>;
  for (const card of cardRows) {
    database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(card.id, card.id);
    database.prepare("DELETE FROM cards WHERE id = ?").run(card.id);
  }
}

function validateId(value: unknown, label: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new CreationWorkspaceError("invalid-input", `${label}不能为空。`);
  }
  return value;
}

/** beforeId：省略或 null 表示追加到末尾；其余必须为已存在的实体 id。 */
function resolveBeforeId(value: unknown, label: string): string | undefined {
  if (value === undefined || value === null) return undefined;
  return validateId(value, label);
}

function validateTitle(value: unknown, label: string, maxLength = 200): string {
  if (typeof value !== "string") {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为文本。`);
  }
  const title = value.trim();
  if (!title || title.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为 1 至 ${maxLength} 个字符。`);
  }
  return title;
}

function validateBaseRevision(value: unknown): number {
  if (!Number.isInteger(value) || Number(value) < 1) {
    throw new CreationWorkspaceError("invalid-input", "修订号必须为正整数。");
  }
  return value as number;
}

function validateStringList(value: unknown, label: string, maxLength = 20): string[] {
  if (value === undefined) return [];
  if (!Array.isArray(value)) throw new CreationWorkspaceError("invalid-input", `${label}必须为数组。`);
  const result: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") {
      throw new CreationWorkspaceError("invalid-input", `${label}项必须为文本。`);
    }
    const trimmed = item.trim();
    if (!trimmed || trimmed.length > 100) {
      throw new CreationWorkspaceError("invalid-input", `${label}项必须为 1 至 100 个字符。`);
    }
    if (!result.includes(trimmed)) result.push(trimmed);
  }
  if (result.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}最多 ${maxLength} 项。`);
  }
  return result;
}

function validateCardFieldSchemaList(value: unknown): CardFieldSchema[] {
  if (!Array.isArray(value)) throw new CreationWorkspaceError("invalid-input", "卡片字段定义必须为数组。");
  const fields: CardFieldSchema[] = [];
  const seenKeys = new Set<string>();
  for (const item of value) {
    if (!isRecord(item)) throw new CreationWorkspaceError("invalid-input", "卡片字段定义无效。");
    const key = typeof item.key === "string" ? item.key.trim() : "";
    const label = typeof item.label === "string" ? item.label.trim() : "";
    const kind = item.kind;
    if (!key || seenKeys.has(key) || !/^[a-zA-Z][a-zA-Z0-9_]*$/.test(key)) {
      throw new CreationWorkspaceError("invalid-input", "卡片字段 key 必须为唯一的字母数字标识。");
    }
    if (!label || label.length > 50) {
      throw new CreationWorkspaceError("invalid-input", "卡片字段标签必须为 1 至 50 个字符。");
    }
    if (typeof kind !== "string" || !CARD_FIELD_KINDS.has(kind as CardFieldKind)) {
      throw new CreationWorkspaceError("invalid-input", "卡片字段类型无效。");
    }
    seenKeys.add(key);
    const schema: CardFieldSchema = { key, label, kind: kind as CardFieldKind };
    if (item.required === true) schema.required = true;
    if (item.defaultValue !== undefined) schema.defaultValue = item.defaultValue;
    if (kind === "select" || kind === "multiSelect") {
      if (!Array.isArray(item.options) || item.options.length === 0) {
        throw new CreationWorkspaceError("invalid-input", "单选/多选字段必须提供选项。");
      }
      schema.options = item.options.map((option) => String(option));
    }
    fields.push(schema);
  }
  return fields;
}

function validateCardFieldValues(
  fields: Record<string, unknown>,
  schemas: CardFieldSchema[]
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  const byKey = new Map(schemas.map((schema) => [schema.key, schema]));
  for (const [key, value] of Object.entries(fields)) {
    if (!byKey.has(key)) {
      throw new CreationWorkspaceError("invalid-input", `卡片字段“${key}”不属于该类型。`);
    }
    result[key] = value;
  }
  for (const schema of schemas) {
    const hasValue = Object.prototype.hasOwnProperty.call(result, schema.key);
    if (schema.required && !hasValue) {
      throw new CreationWorkspaceError("invalid-input", `必填字段“${schema.label}”未填写。`);
    }
    if (!hasValue && schema.defaultValue !== undefined) {
      result[schema.key] = schema.defaultValue;
    }
  }
  return result;
}

function initializeSchema(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;

    CREATE TABLE IF NOT EXISTS workspace_meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      setup_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS volumes (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_volumes_project_order ON volumes(project_id, sort_order);

    CREATE TABLE IF NOT EXISTS chapters (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      volume_id TEXT REFERENCES volumes(id) ON DELETE SET NULL,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      status TEXT NOT NULL DEFAULT '',
      numbering_kind TEXT NOT NULL DEFAULT 'auto',
      custom_number TEXT,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_chapters_project_order ON chapters(project_id, sort_order);
    CREATE INDEX IF NOT EXISTS idx_chapters_volume_order ON chapters(volume_id, sort_order);

    CREATE TABLE IF NOT EXISTS scenes (
      id TEXT PRIMARY KEY,
      chapter_id TEXT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      body_json TEXT NOT NULL DEFAULT '{"type":"doc","content":[]}',
      planning_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_scenes_chapter_order ON scenes(chapter_id, sort_order);

    CREATE TABLE IF NOT EXISTS card_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      name TEXT NOT NULL,
      fields_json TEXT NOT NULL DEFAULT '[]',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_card_types_project ON card_types(project_id, sort_order);

    CREATE TABLE IF NOT EXISTS relation_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      forward_name TEXT NOT NULL,
      reverse_name TEXT NOT NULL,
      from_kinds_json TEXT NOT NULL DEFAULT '[]',
      to_kinds_json TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_relation_types_project ON relation_types(project_id);

    CREATE TABLE IF NOT EXISTS cards (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      title TEXT NOT NULL,
      aliases_json TEXT NOT NULL DEFAULT '[]',
      fields_json TEXT NOT NULL DEFAULT '{}',
      tags_json TEXT NOT NULL DEFAULT '[]',
      content_json TEXT NOT NULL DEFAULT '{}',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_cards_project_kind ON cards(project_id, kind);

    CREATE TABLE IF NOT EXISTS card_relations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      from_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      to_card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relation_type TEXT NOT NULL,
      note TEXT,
      created_at TEXT NOT NULL,
      UNIQUE(from_card_id, to_card_id, relation_type)
    );
    CREATE INDEX IF NOT EXISTS idx_card_relations_from ON card_relations(from_card_id);
    CREATE INDEX IF NOT EXISTS idx_card_relations_to ON card_relations(to_card_id);

    CREATE TABLE IF NOT EXISTS resources (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL,
      sha256 TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);

    CREATE TABLE IF NOT EXISTS snapshots (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      subject_type TEXT NOT NULL,
      subject_id TEXT NOT NULL,
      payload_json TEXT NOT NULL,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_snapshots_project_created ON snapshots(project_id, created_at);

    CREATE TABLE IF NOT EXISTS change_log (
      sequence INTEGER PRIMARY KEY AUTOINCREMENT,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      command_type TEXT NOT NULL,
      changes_json TEXT NOT NULL,
      committed_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS writing_sessions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT,
      started_at TEXT NOT NULL,
      active_seconds INTEGER NOT NULL DEFAULT 0,
      net_chars INTEGER NOT NULL DEFAULT 0,
      reported_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_writing_sessions_project_started ON writing_sessions(project_id, started_at);

    CREATE TABLE IF NOT EXISTS inbox_items (
      id TEXT PRIMARY KEY,
      legacy_id TEXT,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'note',
      status TEXT NOT NULL DEFAULT 'inbox',
      tags_json TEXT NOT NULL DEFAULT '[]',
      platform_tags_json TEXT NOT NULL DEFAULT '[]',
      source_json TEXT,
      variants_json TEXT NOT NULL DEFAULT '[]',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_inbox_items_updated ON inbox_items(updated_at);

    CREATE VIRTUAL TABLE IF NOT EXISTS scenes_fts USING fts5(
      title,
      body_json,
      content='scenes',
      content_rowid='rowid'
    );

    CREATE TRIGGER IF NOT EXISTS scenes_ai AFTER INSERT ON scenes BEGIN
      INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
    END;
    CREATE TRIGGER IF NOT EXISTS scenes_ad AFTER DELETE ON scenes BEGIN
      INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
    END;
    CREATE TRIGGER IF NOT EXISTS scenes_au AFTER UPDATE ON scenes BEGIN
      INSERT INTO scenes_fts(scenes_fts, rowid, title, body_json) VALUES ('delete', old.rowid, old.title, old.body_json);
      INSERT INTO scenes_fts(rowid, title, body_json) VALUES (new.rowid, new.title, new.body_json);
    END;

    PRAGMA user_version = ${SCHEMA_VERSION};
    COMMIT;
  `);
  seedBuiltinCardData(database);
}

function seedBuiltinCardData(database: Database): void {
  const seedTime = "2026-01-01T00:00:00.000Z";
  const insertType = database.prepare(
    "INSERT OR IGNORE INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
  );
  const builtinTypes: Array<[string, string]> = [
    ["character", "角色"],
    ["location", "地点"],
    ["organization", "组织"],
    ["item", "物品"],
    ["worldRule", "世界规则"],
    ["plotEvent", "情节事件"],
    ["foreshadow", "伏笔线索"],
    ["reference", "资料"]
  ];
  builtinTypes.forEach(([kind, name], index) => {
    insertType.run(
      `card-type-${kind}`,
      null,
      kind,
      name,
      '[{"key":"note","label":"备注","kind":"multiline"}]',
      index,
      seedTime,
      seedTime
    );
  });
  const insertRelation = database.prepare(
    "INSERT OR IGNORE INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
  );
  const builtinRelations: Array<[string, string, string, string, string[], string[]]> = [
    ["character-character", "knows", "认识", "认识", ["character"], ["character"]],
    ["character-location", "appearsAt", "登场于", "登场角色", ["character"], ["location"]],
    ["character-organization", "belongsTo", "隶属于", "成员", ["character"], ["organization"]],
    ["item-character", "ownedBy", "持有", "持有者", ["item"], ["character"]]
  ];
  builtinRelations.forEach(([suffix, name, forward, reverse, fromKinds, toKinds]) => {
    insertRelation.run(
      `relation-type-${suffix}`,
      null,
      name,
      forward,
      reverse,
      JSON.stringify(fromKinds),
      JSON.stringify(toKinds),
      seedTime,
      seedTime
    );
  });
}

function migrateSchemaV1ToV2(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    ALTER TABLE projects ADD COLUMN setup_json TEXT NOT NULL DEFAULT '{}';
    PRAGMA user_version = 2;
    COMMIT;
  `);
}

function migrateSchemaV2ToV3(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS volumes (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      sort_order INTEGER NOT NULL,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_volumes_project_order ON volumes(project_id, sort_order);
    ALTER TABLE chapters ADD COLUMN volume_id TEXT REFERENCES volumes(id) ON DELETE SET NULL;
    ALTER TABLE chapters ADD COLUMN status TEXT NOT NULL DEFAULT '';
    ALTER TABLE chapters ADD COLUMN numbering_kind TEXT NOT NULL DEFAULT 'auto';
    ALTER TABLE chapters ADD COLUMN custom_number TEXT;
    ALTER TABLE chapters ADD COLUMN deleted_at TEXT;
    ALTER TABLE scenes ADD COLUMN planning_json TEXT NOT NULL DEFAULT '{}';
    ALTER TABLE scenes ADD COLUMN deleted_at TEXT;
    INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at)
      SELECT 'volume-default-' || p.id, p.id, '正文', 0, p.created_at, p.updated_at
      FROM projects p
      WHERE EXISTS (SELECT 1 FROM chapters c WHERE c.project_id = p.id);
    UPDATE chapters SET volume_id = 'volume-default-' || project_id WHERE volume_id IS NULL;
    CREATE INDEX IF NOT EXISTS idx_chapters_volume_order ON chapters(volume_id, sort_order);
    PRAGMA user_version = 3;
    COMMIT;
  `);
}

function migrateSchemaV3ToV4(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS card_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      kind TEXT NOT NULL,
      name TEXT NOT NULL,
      fields_json TEXT NOT NULL DEFAULT '[]',
      sort_order INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_card_types_project ON card_types(project_id, sort_order);
    CREATE TABLE IF NOT EXISTS relation_types (
      id TEXT PRIMARY KEY,
      project_id TEXT REFERENCES projects(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      forward_name TEXT NOT NULL,
      reverse_name TEXT NOT NULL,
      from_kinds_json TEXT NOT NULL DEFAULT '[]',
      to_kinds_json TEXT NOT NULL DEFAULT '[]',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      revision INTEGER NOT NULL DEFAULT 1
    );
    CREATE INDEX IF NOT EXISTS idx_relation_types_project ON relation_types(project_id);
    ALTER TABLE cards ADD COLUMN aliases_json TEXT NOT NULL DEFAULT '[]';
    ALTER TABLE cards ADD COLUMN fields_json TEXT NOT NULL DEFAULT '{}';
    ALTER TABLE cards ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
    ALTER TABLE cards ADD COLUMN deleted_at TEXT;
    ALTER TABLE card_relations ADD COLUMN note TEXT;
    PRAGMA user_version = 4;
    COMMIT;
  `);
  seedBuiltinCardData(database);
}

function migrateSchemaV4ToV5(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS writing_sessions (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT,
      started_at TEXT NOT NULL,
      active_seconds INTEGER NOT NULL DEFAULT 0,
      net_chars INTEGER NOT NULL DEFAULT 0,
      reported_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_writing_sessions_project_started ON writing_sessions(project_id, started_at);
    PRAGMA user_version = 5;
    COMMIT;
  `);
}

function migrateSchemaV5ToV6(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS inbox_items (
      id TEXT PRIMARY KEY,
      legacy_id TEXT,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'note',
      status TEXT NOT NULL DEFAULT 'inbox',
      tags_json TEXT NOT NULL DEFAULT '[]',
      platform_tags_json TEXT NOT NULL DEFAULT '[]',
      source_json TEXT,
      variants_json TEXT NOT NULL DEFAULT '[]',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_inbox_items_updated ON inbox_items(updated_at);
    PRAGMA user_version = 6;
    COMMIT;
  `);
}

function defaultProjectSetup(): CreationProjectSetup {
  return {
    template: "blank",
    weeklyUpdateDays: [],
    chapterWorkflow: ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"]
  };
}

function setupString(value: unknown, maxLength: number, label: string): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string") throw new CreationWorkspaceError("invalid-input", `${label}必须为文本。`);
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  if (trimmed.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}不能超过 ${maxLength} 个字符。`);
  }
  return trimmed;
}

function setupPositiveInteger(value: unknown, label: string): number | undefined {
  if (value === undefined) return undefined;
  if (!Number.isInteger(value) || Number(value) < 1) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为正整数。`);
  }
  return value as number;
}

function setupTargetDate(value: unknown): string | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== "string" || !TARGET_DATE_PATTERN.test(value)) {
    throw new CreationWorkspaceError("invalid-input", "目标日期必须为 YYYY-MM-DD 格式。");
  }
  return value;
}

function setupWeeklyUpdateDays(value: unknown): number[] {
  if (!Array.isArray(value)) throw new CreationWorkspaceError("invalid-input", "每周更新日必须为数组。");
  const days: number[] = [];
  for (const item of value) {
    if (!Number.isInteger(item) || Number(item) < 0 || Number(item) > 6) {
      throw new CreationWorkspaceError("invalid-input", "每周更新日必须为 0 至 6 的整数。");
    }
    const day = item as number;
    if (!days.includes(day)) days.push(day);
  }
  return days.sort((left, right) => left - right);
}

function setupChapterWorkflow(value: unknown): string[] {
  if (!Array.isArray(value) || value.length === 0) {
    throw new CreationWorkspaceError("invalid-input", "章节工作流必须为非空数组。");
  }
  const steps: string[] = [];
  for (const item of value) {
    if (typeof item !== "string") throw new CreationWorkspaceError("invalid-input", "章节工作流步骤必须为文本。");
    const step = item.trim();
    if (!step) throw new CreationWorkspaceError("invalid-input", "章节工作流步骤不能为空。");
    steps.push(step);
  }
  return steps;
}

function normalizeProjectSetup(value: unknown): CreationProjectSetup {
  if (value === undefined) return defaultProjectSetup();
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new CreationWorkspaceError("invalid-input", "创作项目设置无效。");
  }
  const record = value as Record<string, unknown>;
  const template = record.template as CreationProjectTemplate;
  if (template !== "blank" && template !== "long-form" && template !== "serial") {
    throw new CreationWorkspaceError("invalid-input", "创作项目模板无效。");
  }
  const setup: CreationProjectSetup = {
    template,
    weeklyUpdateDays: setupWeeklyUpdateDays(record.weeklyUpdateDays),
    chapterWorkflow: setupChapterWorkflow(record.chapterWorkflow)
  };
  const description = setupString(record.description, 5000, "简介");
  if (description !== undefined) setup.description = description;
  const genre = setupString(record.genre, 200, "题材");
  if (genre !== undefined) setup.genre = genre;
  const totalWordGoal = setupPositiveInteger(record.totalWordGoal, "总字数目标");
  if (totalWordGoal !== undefined) setup.totalWordGoal = totalWordGoal;
  const dailyWordGoal = setupPositiveInteger(record.dailyWordGoal, "每日字数目标");
  if (dailyWordGoal !== undefined) setup.dailyWordGoal = dailyWordGoal;
  const weeklyWordGoal = setupPositiveInteger(record.weeklyWordGoal, "每周字数目标");
  if (weeklyWordGoal !== undefined) setup.weeklyWordGoal = weeklyWordGoal;
  const targetDate = setupTargetDate(record.targetDate);
  if (targetDate !== undefined) setup.targetDate = targetDate;
  return setup;
}

function parseStoredSetup(json: string): CreationProjectSetup {
  if (!json || json.trim() === "{}") return defaultProjectSetup();
  let value: unknown;
  try {
    value = JSON.parse(json);
  } catch {
    throw new CreationWorkspaceError("integrity", "创作项目设置数据损坏。");
  }
  try {
    return normalizeProjectSetup(value);
  } catch (error) {
    if (error instanceof CreationWorkspaceError && error.code === "invalid-input") {
      throw new CreationWorkspaceError("integrity", "创作项目设置数据损坏。");
    }
    throw error;
  }
}

class SqliteCreationWorkspace implements CreationWorkspace {
  private closed = false;
  private readonly watchers = new Map<symbol, { scope: CreationWatchScope; listener: CreationWorkspaceListener }>();

  constructor(private readonly database: Database) {}

  async read(query: ReadProjectTreeQuery): Promise<CreationProjectTree | null>;
  async read(query: ReadProjectNavigationQuery): Promise<CreationProjectNavigation | null>;
  async read(query: ReadProjectOutlineQuery): Promise<CreationProjectOutline | null>;
  async read(query: ReadSceneBodyQuery): Promise<SceneBodyView | null>;
  async read(query: ListProjectsQuery): Promise<CreationProjectSummary[]>;
  async read(query: CardsListQuery): Promise<CardSummary[]>;
  async read(query: CardReadQuery): Promise<CardSummary | null>;
  async read(query: CardTypesListQuery): Promise<CardType[]>;
  async read(query: RelationTypesListQuery): Promise<RelationType[]>;
  async read(query: CardRelationsQuery): Promise<{ outgoing: CardRelation[]; incoming: CardRelation[] }>;
  async read(query: TrashListQuery): Promise<TrashItem[]>;
  async read(query: SnapshotListQuery): Promise<SnapshotInfo[]>;
  async read(query: ProjectExportQuery): Promise<ProjectExportView | null>;
  async read(query: CreationSearchQuery): Promise<CreationSearchView>;
  async read(query: ReplacePreviewQuery): Promise<ReplacePreviewView>;
  async read(query: StatsViewQuery): Promise<ProjectStatsView | null>;
  async read(query: SessionListQuery): Promise<SessionEntry[]>;
  async read(query: ProofQuery): Promise<ProofView>;
  async read(query: InboxListQuery): Promise<InboxItem[]>;
  async read(query: InboxReadQuery): Promise<InboxItem | null>;
  async read(query: CreationReadQuery): Promise<CreationReadResult> {
    this.assertOpen();
    const runtimeQuery = query as unknown as {
      kind?: unknown;
      projectId?: unknown;
      sceneId?: unknown;
      cardId?: unknown;
      cardKind?: unknown;
      search?: unknown;
      subjectType?: unknown;
      subjectId?: unknown;
      text?: unknown;
      scopes?: unknown;
      filters?: unknown;
      limit?: unknown;
    } | null;
    if (
      runtimeQuery === null ||
      typeof runtimeQuery.kind !== "string"
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    if (runtimeQuery.kind === "projects.list") {
      try {
        return this.listProjects();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作工作区项目列表。");
      }
    }
    if (runtimeQuery.kind === "scene.body") {
      if (typeof runtimeQuery.sceneId !== "string" || !runtimeQuery.sceneId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "场景读取请求无效。");
      }
      try {
        return this.readSceneBody(runtimeQuery.sceneId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取场景正文。");
      }
    }
    if (runtimeQuery.kind === "search.query") {
      try {
        return this.runSearch(query as CreationSearchQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法搜索创作工作区数据。");
      }
    }
    if (runtimeQuery.kind === "replace.preview") {
      try {
        return this.runReplacePreview(query as ReplacePreviewQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法生成查找替换预览。");
      }
    }
    if (runtimeQuery.kind === "proof.query") {
      try {
        return this.runProofQuery(query as ProofQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法执行本地校对。");
      }
    }
    if (runtimeQuery.kind === "inbox.list") {
      const limit = (runtimeQuery as unknown as { limit?: unknown }).limit;
      const resolvedLimit = limit === undefined ? 100 : limit;
      if (!Number.isInteger(resolvedLimit) || (resolvedLimit as number) < 1 || (resolvedLimit as number) > 500) {
        throw new CreationWorkspaceError("invalid-input", "收件箱返回上限必须为 1..500 的整数。");
      }
      try {
        return this.runInboxList(resolvedLimit as number);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取收件箱。");
      }
    }
    if (runtimeQuery.kind === "inbox.read") {
      if (typeof (runtimeQuery as unknown as { itemId?: unknown }).itemId !== "string") {
        throw new CreationWorkspaceError("invalid-input", "收件箱读取请求无效。");
      }
      try {
        const itemId = (runtimeQuery as unknown as { itemId: string }).itemId;
        const row = this.database
          .prepare("SELECT id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
          .get(itemId) as
          | {
              id: string;
              legacy_id: string | null;
              title: string;
              body: string;
              type: string;
              status: string;
              tags_json: string;
              platform_tags_json: string;
              source_json: string | null;
              variants_json: string;
              revision: number;
              created_at: string;
              updated_at: string;
            }
          | undefined;
        return row ? this.inboxFromRow(row) : null;
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取收件箱条目。");
      }
    }
    if (runtimeQuery.kind === "stats.view") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "统计读取请求无效。");
      }
      try {
        return this.runStatsView(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作统计。");
      }
    }
    if (runtimeQuery.kind === "session.list") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "会话读取请求无效。");
      }
      const limit = (runtimeQuery as unknown as { limit?: unknown }).limit;
      const resolvedLimit = limit === undefined ? 100 : limit;
      if (!Number.isInteger(resolvedLimit) || (resolvedLimit as number) < 1 || (resolvedLimit as number) > 500) {
        throw new CreationWorkspaceError("invalid-input", "会话返回上限必须为 1..500 的整数。");
      }
      try {
        return this.runSessionList(runtimeQuery.projectId, resolvedLimit as number);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取写作会话。");
      }
    }
    if (
      runtimeQuery.kind === "cards.list" ||
      runtimeQuery.kind === "card.read" ||
      runtimeQuery.kind === "cardTypes.list" ||
      runtimeQuery.kind === "relationTypes.list" ||
      runtimeQuery.kind === "card.relations" ||
      runtimeQuery.kind === "trash.list" ||
      runtimeQuery.kind === "snapshot.list"
    ) {
      try {
        if (runtimeQuery.kind === "cards.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "卡片读取请求无效。");
          }
          return this.listCards({
            projectId: runtimeQuery.projectId,
            cardKind:
              typeof runtimeQuery.cardKind === "string" && runtimeQuery.cardKind ? runtimeQuery.cardKind : undefined,
            search:
              typeof runtimeQuery.search === "string" && runtimeQuery.search.trim()
                ? runtimeQuery.search.trim()
                : undefined
          });
        }
        if (runtimeQuery.kind === "cardTypes.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "卡片类型读取请求无效。");
          }
          return this.listCardTypes(runtimeQuery.projectId);
        }
        if (runtimeQuery.kind === "relationTypes.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "关系类型读取请求无效。");
          }
          return this.listRelationTypes(runtimeQuery.projectId);
        }
        if (runtimeQuery.kind === "trash.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "回收站读取请求无效。");
          }
          return this.trashList(runtimeQuery.projectId);
        }
        if (runtimeQuery.kind === "snapshot.list") {
          if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "快照读取请求无效。");
          }
          return this.snapshotList({
            projectId: runtimeQuery.projectId,
            subjectType:
              runtimeQuery.subjectType === "scene" || runtimeQuery.subjectType === "card"
                ? runtimeQuery.subjectType
                : undefined,
            subjectId:
              typeof runtimeQuery.subjectId === "string" && runtimeQuery.subjectId
                ? runtimeQuery.subjectId
                : undefined
          });
        }
        if (runtimeQuery.kind === "card.relations") {
          if (typeof runtimeQuery.cardId !== "string" || !runtimeQuery.cardId.trim()) {
            throw new CreationWorkspaceError("invalid-input", "卡片关系读取请求无效。");
          }
          return this.readCardRelations(runtimeQuery.cardId);
        }
        if (typeof runtimeQuery.cardId !== "string" || !runtimeQuery.cardId.trim()) {
          throw new CreationWorkspaceError("invalid-input", "卡片读取请求无效。");
        }
        return this.readCard(runtimeQuery.cardId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取创作工作区卡片数据。");
      }
    }
    if (
      (runtimeQuery.kind !== "project.tree" &&
        runtimeQuery.kind !== "project.navigation" &&
        runtimeQuery.kind !== "project.outline" &&
        runtimeQuery.kind !== "project.export") ||
      typeof runtimeQuery.projectId !== "string" ||
      !runtimeQuery.projectId.trim()
    ) {
      throw new CreationWorkspaceError("invalid-input", "项目读取请求无效。");
    }
    try {
      if (runtimeQuery.kind === "project.navigation") {
        return this.readProjectNavigation(runtimeQuery.projectId);
      }
      if (runtimeQuery.kind === "project.outline") {
        return this.readProjectOutline(runtimeQuery.projectId);
      }
      if (runtimeQuery.kind === "project.export") {
        return this.readProjectExport(runtimeQuery.projectId);
      }
      return this.readProjectTree(runtimeQuery.projectId);
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法读取创作工作区数据。");
    }
  }

  private readProjectTree(projectId: string): CreationProjectTree | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = this.database
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
    const sceneStatement = this.database.prepare(
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

  private readProjectNavigation(projectId: string): CreationProjectNavigation | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;

    const chapters = this.database
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
    const sceneStatement = this.database.prepare(
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

  private readProjectOutline(projectId: string): CreationProjectOutline | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;
    const setup = parseStoredSetup(project.setup_json);
    const workflow = setup.chapterWorkflow;

    const volumeRows = this.database
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

    const chapterRows = this.database
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

    const sceneRows = this.database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.sort_order, s.body_json, s.created_at, s.updated_at, s.revision
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id`
      )
      .all(projectId) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      sort_order: number;
      body_json: string;
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
      return {
        id: chapter.id,
        volumeId: chapter.volume_id,
        title: chapter.title,
        sortOrder: chapter.sort_order,
        status,
        numbering: chapter.numbering_kind,
        customNumber: chapter.custom_number,
        displayNumber,
        createdAt: chapter.created_at,
        updatedAt: chapter.updated_at,
        revision: chapter.revision,
        scenes: (scenesByChapter.get(chapter.id) ?? []).map((scene) => ({
          id: scene.id,
          chapterId: scene.chapter_id,
          title: scene.title,
          sortOrder: scene.sort_order,
          wordCount: countSceneWords(scene.body_json),
          createdAt: scene.created_at,
          updatedAt: scene.updated_at,
          revision: scene.revision
        }))
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
      volumes.push({
        id: volume.id,
        projectId: volume.project_id,
        title: volume.title,
        sortOrder: volume.sort_order,
        createdAt: volume.created_at,
        updatedAt: volume.updated_at,
        revision: volume.revision,
        chapters: chapterList.map((chapter) => {
          if (chapter.numbering_kind === "auto") autoIndex += 1;
          return buildChapter(chapter, autoIndex);
        })
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
      looseChapters
    };
  }

  private cardFromRow(row: {
    id: string;
    project_id: string;
    kind: string;
    title: string;
    aliases_json: string;
    fields_json: string;
    tags_json: string;
    created_at: string;
    updated_at: string;
    revision: number;
  }): CardSummary {
    return {
      id: row.id,
      projectId: row.project_id,
      kind: row.kind,
      title: row.title,
      aliases: JSON.parse(row.aliases_json) as string[],
      fields: JSON.parse(row.fields_json) as Record<string, unknown>,
      tags: JSON.parse(row.tags_json) as string[],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision
    };
  }

  private listCardTypes(projectId: string): CardType[] {
    const rows = this.database
      .prepare(
        `SELECT id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision
         FROM card_types WHERE project_id IS NULL OR project_id = ? ORDER BY sort_order, id`
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string | null;
      kind: string;
      name: string;
      fields_json: string;
      sort_order: number;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      kind: row.kind,
      name: row.name,
      fields: JSON.parse(row.fields_json) as CardFieldSchema[],
      sortOrder: row.sort_order,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision
    }));
  }

  private listRelationTypes(projectId: string): RelationType[] {
    const rows = this.database
      .prepare(
        `SELECT id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision
         FROM relation_types WHERE project_id IS NULL OR project_id = ? ORDER BY created_at, id`
      )
      .all(projectId) as Array<{
      id: string;
      project_id: string | null;
      name: string;
      forward_name: string;
      reverse_name: string;
      from_kinds_json: string;
      to_kinds_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      name: row.name,
      forwardName: row.forward_name,
      reverseName: row.reverse_name,
      fromKinds: JSON.parse(row.from_kinds_json) as string[],
      toKinds: JSON.parse(row.to_kinds_json) as string[],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision
    }));
  }

  private listCards(query: { projectId: string; cardKind?: string; search?: string }): CardSummary[] {
    const params: unknown[] = [query.projectId];
    let sql = `SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, created_at, updated_at, revision
               FROM cards WHERE project_id = ? AND deleted_at IS NULL`;
    if (query.cardKind) {
      sql += " AND kind = ?";
      params.push(query.cardKind);
    }
    if (query.search) {
      sql += " AND (title LIKE ? OR aliases_json LIKE ?)";
      params.push(`%${query.search}%`, `%${query.search}%`);
    }
    sql += " ORDER BY updated_at DESC, id DESC";
    const rows = this.database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string;
      kind: string;
      title: string;
      aliases_json: string;
      fields_json: string;
      tags_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    return rows.map((row) => this.cardFromRow(row));
  }

  private runSearch(query: CreationSearchQuery): CreationSearchView {
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
    if (scopes.includes("scene")) this.searchScenes(text, projectId, chapterStatuses, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("card")) this.searchCards(text, projectId, cardKinds, tags, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("chapter")) this.searchChapters(text, projectId, chapterStatuses, remaining(), hits);
    if (remaining() <= 0) return { query: text, hits, total: hits.length };
    if (scopes.includes("project")) this.searchProjects(text, projectId, remaining(), hits);
    return { query: text, hits, total: hits.length };
  }

  private searchScenes(
    keyword: string,
    projectId: string | undefined,
    chapterStatuses: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void {
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
    const rows = this.database.prepare(sql).all(...params) as Array<{
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
  }

  private searchCards(
    keyword: string,
    projectId: string | undefined,
    cardKinds: string[],
    tags: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void {
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
    const rows = this.database.prepare(sql).all(...params) as Array<{
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
  }

  private searchChapters(
    keyword: string,
    projectId: string | undefined,
    chapterStatuses: string[],
    limit: number,
    hits: CreationSearchHit[]
  ): void {
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
    const rows = this.database.prepare(sql).all(...params) as Array<{
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
  }

  private searchProjects(keyword: string, projectId: string | undefined, limit: number, hits: CreationSearchHit[]): void {
    const like = `%${escapeLike(keyword)}%`;
    const params: unknown[] = [like];
    let sql = "SELECT id, title, updated_at FROM projects WHERE title LIKE ? ESCAPE '\\'";
    if (projectId) {
      sql += " AND id = ?";
      params.push(projectId);
    }
    sql += " ORDER BY updated_at DESC, id DESC LIMIT ?";
    params.push(limit);
    const rows = this.database.prepare(sql).all(...params) as Array<{
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
  }

  private replaceScopeScenes(
    projectId: string,
    scope: ReplaceScope,
    scopeId: string | undefined
  ): Array<{ id: string; chapter_id: string; title: string; body_json: string; chapter_title: string }> {
    this.requireProject(projectId);
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
      const volume = this.requireVolume(volumeId);
      this.assertSameProject(projectId, volume.project_id, "卷");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.volume_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(volumeId);
    } else if (scope === "chapter") {
      const chapterId = validateId(scopeId, "章节");
      const chapter = this.requireChapter(chapterId);
      this.assertSameProject(projectId, chapter.project_id, "章节");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.chapter_id = ?
        ORDER BY s.sort_order, s.id`;
      params.push(chapterId);
    } else {
      const sceneId = validateId(scopeId, "场景");
      const scene = this.requireScene(sceneId);
      this.assertSameProject(projectId, scene.project_id, "场景");
      sql = `
        SELECT s.id, s.chapter_id, s.title, s.body_json, c.title AS chapter_title
        FROM scenes s JOIN chapters c ON c.id = s.chapter_id
        WHERE s.deleted_at IS NULL AND s.id = ?`;
      params.push(sceneId);
    }
    return this.database.prepare(sql).all(...params) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      body_json: string;
      chapter_title: string;
    }>;
  }

  private runReplacePreview(query: ReplacePreviewQuery): ReplacePreviewView {
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
    for (const scene of this.replaceScopeScenes(projectId, query.scope, query.scopeId)) {
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
  }

  private replaceApply(command: ReplaceApplyCommand): ReplaceApplyResult {
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
      this.database.exec("BEGIN IMMEDIATE");
      const scenes = this.replaceScopeScenes(projectId, command.scope, command.scopeId);
      const snapshotIds: string[] = [];
      const changes: CreationWorkspaceEvent["changes"] = [];
      let appliedScenes = 0;
      let appliedHits = 0;
      let skippedScenes = 0;
      const selectBody = this.database.prepare("SELECT body_json, revision FROM scenes WHERE id = ?");
      const updateScene = this.database.prepare(
        "UPDATE scenes SET body_json = ?, updated_at = ?, revision = ? WHERE id = ?"
      );
      const insertSnapshot = this.database.prepare(
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
        updateScene.run(JSON.stringify(document), timestamp, newRevision, scene.id);
        snapshotIds.push(snapshotId);
        appliedScenes += 1;
        appliedHits += sceneHits;
        changes.push({ entity: "scene", id: scene.id, action: "updated", revision: newRevision });
        changes.push({ entity: "snapshot", id: snapshotId, action: "created", revision: 1 });
      }
      if (appliedScenes === 0) {
        this.database.exec("ROLLBACK");
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
      this.touchProject(projectId, timestamp);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, "replace.apply", JSON.stringify(changes), timestamp);
      this.database.exec("COMMIT");
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
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "replace.apply",
        changes
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法提交查找替换事务。");
    }
  }

  private sessionReport(command: SessionReportCommand): SessionReportResult {
    const projectId = validateId(command.projectId, "作品");
    this.requireProject(projectId);
    const sceneId = typeof command.sceneId === "string" && command.sceneId.trim() ? command.sceneId.trim() : undefined;
    const startedAt = typeof command.startedAt === "string" && !Number.isNaN(Date.parse(command.startedAt))
      ? new Date(command.startedAt).toISOString()
      : new Date().toISOString();
    const activeSeconds = command.activeSeconds;
    if (!Number.isFinite(activeSeconds) || activeSeconds < 0 || activeSeconds > 86_400) {
      throw new CreationWorkspaceError("invalid-input", "活动时长必须在 0 至 86400 秒之间。");
    }
    const netChars = command.netChars;
    if (!Number.isFinite(netChars) || netChars < -1_000_000 || netChars > 1_000_000) {
      throw new CreationWorkspaceError("invalid-input", "净增字符数超出允许范围。");
    }
    const sessionId = `session-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      if (sceneId) {
        this.database.prepare("SELECT id FROM scenes WHERE id = ? AND deleted_at IS NULL").get(sceneId);
      }
      this.database
        .prepare(
          "INSERT INTO writing_sessions(id, project_id, scene_id, started_at, active_seconds, net_chars, reported_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(sessionId, projectId, sceneId, startedAt, Math.round(activeSeconds), Math.round(netChars), timestamp);
      this.touchProject(projectId, timestamp);
      this.database.exec("COMMIT");
      return { commandType: "session.report", sequence: 0, projectId, sessionId, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法写入写作会话。");
    }
  }

  private sessionDelete(command: SessionDeleteCommand): SessionReportResult {
    const projectId = validateId(command.projectId, "作品");
    const sessionId = validateId(command.sessionId, "会话");
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const row = this.database
        .prepare("SELECT id FROM writing_sessions WHERE id = ? AND project_id = ?")
        .get(sessionId, projectId) as { id: string } | undefined;
      if (!row) throw new CreationWorkspaceError("not-found", "会话不存在。");
      this.database.prepare("DELETE FROM writing_sessions WHERE id = ?").run(sessionId);
      this.touchProject(projectId, timestamp);
      this.database.exec("COMMIT");
      return { commandType: "session.delete", sequence: 0, projectId, sessionId, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法删除写作会话。");
    }
  }

  private runSessionList(projectId: string, limit: number): SessionEntry[] {
    this.requireProject(projectId);
    const rows = this.database
      .prepare(
        "SELECT id, project_id, scene_id, started_at, active_seconds, net_chars, reported_at FROM writing_sessions WHERE project_id = ? ORDER BY reported_at DESC, id DESC LIMIT ?"
      )
      .all(projectId, limit) as Array<{
      id: string;
      project_id: string;
      scene_id: string | null;
      started_at: string;
      active_seconds: number;
      net_chars: number;
      reported_at: string;
    }>;
    return rows.map((row) => ({
      id: row.id,
      projectId: row.project_id,
      sceneId: row.scene_id,
      startedAt: row.started_at,
      activeSeconds: row.active_seconds,
      netChars: row.net_chars,
      reportedAt: row.reported_at
    }));
  }

  private runProofQuery(query: ProofQuery): ProofView {
    const projectId = validateId(query.projectId, "作品");
    this.requireProject(projectId);
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
      const scene = this.requireScene(sceneId);
      this.assertSameProject(projectId, scene.project_id, "场景");
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

    const issues: ProofIssue[] = [];
    let scannedScenes = 0;
    const affectedScenes = new Set<string>();
    for (const row of this.database.prepare(sql).all(...params) as Array<{
      scene_id: string;
      chapter_id: string;
      scene_title: string;
      body_json: string;
      chapter_title: string;
    }>) {
      scannedScenes += 1;
      const found: Array<{ rule: ProofRule; message: string; snippet: string | null }> = [];
      const paragraphs = sceneParagraphs(row.body_json);
      const plain = paragraphs.join("\n");
      if (rules.includes("repeatedChar")) findRepeatedChars(plain, found);
      if (rules.includes("unbalancedPunctuation")) findUnbalancedPunctuation(plain, found);
      if (rules.includes("abnormalSpacing")) findAbnormalSpacing(paragraphs, found);
      if (rules.includes("longParagraph")) findLongParagraphs(paragraphs, maxParagraphChars, found);
      if (rules.includes("bannedWord") && bannedWords.length > 0) findBannedWords(plain, bannedWords, found);
      if (found.length === 0) continue;
      affectedScenes.add(row.scene_id);
      const byRule = new Map<ProofRule, { message: string; snippet: string | null; count: number }>();
      for (const item of found) {
        const entry = byRule.get(item.rule) ?? { message: item.message, snippet: item.snippet, count: 0 };
        entry.count += 1;
        if (!entry.snippet && item.snippet) entry.snippet = item.snippet;
        byRule.set(item.rule, entry);
      }
      for (const [rule, entry] of byRule) {
        issues.push({
          sceneId: row.scene_id,
          chapterId: row.chapter_id,
          chapterTitle: row.chapter_title,
          sceneTitle: row.scene_title,
          rule,
          message: entry.count > 1 ? `${entry.message}（共 ${entry.count} 处）` : entry.message,
          snippet: entry.snippet,
          count: entry.count
        });
      }
      if (issues.length >= limit) break;
    }
    return {
      projectId,
      issues,
      scannedScenes,
      affectedScenes: affectedScenes.size,
      total: issues.length
    };
  }

  private runInboxList(limit: number): InboxItem[] {
    const rows = this.database
      .prepare(
        "SELECT id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at FROM inbox_items WHERE deleted_at IS NULL ORDER BY updated_at DESC, id DESC LIMIT ?"
      )
      .all(limit) as Array<{
      id: string;
      legacy_id: string | null;
      title: string;
      body: string;
      type: string;
      status: string;
      tags_json: string;
      platform_tags_json: string;
      source_json: string | null;
      variants_json: string;
      revision: number;
      created_at: string;
      updated_at: string;
    }>;
    return rows.map((row) => this.inboxFromRow(row));
  }

  private inboxFromRow(row: {
    id: string;
    legacy_id: string | null;
    title: string;
    body: string;
    type: string;
    status: string;
    tags_json: string;
    platform_tags_json: string;
    source_json: string | null;
    variants_json: string;
    revision: number;
    created_at: string;
    updated_at: string;
  }): InboxItem {
    return {
      id: row.id,
      legacyId: row.legacy_id,
      title: row.title,
      body: row.body,
      type: row.type,
      status: row.status,
      tags: parseJsonArray(row.tags_json),
      platformTags: parseJsonArray(row.platform_tags_json),
      source: row.source_json ? (JSON.parse(row.source_json) as Record<string, unknown>) : null,
      variants: JSON.parse(row.variants_json) as Array<Record<string, unknown>>,
      revision: row.revision,
      createdAt: row.created_at,
      updatedAt: row.updated_at
    };
  }

  private runInboxCreate(command: InboxCreateCommand): InboxItemResult {
    const title = validateTitle(command.title, "标题", 200);
    const body = typeof command.body === "string" ? command.body : "";
    if (body.length > 1_000_000) throw new CreationWorkspaceError("invalid-input", "正文不能超过 1000000 个字符。");
    const kind = typeof command.kind === "string" && command.kind.trim() ? command.kind.trim() : "note";
    const status = typeof command.status === "string" && command.status.trim() ? command.status.trim() : "inbox";
    const tags = Array.isArray(command.tags) ? command.tags.filter((tag): tag is string => typeof tag === "string") : [];
    const platformTags = Array.isArray(command.platformTags)
      ? command.platformTags.filter((tag): tag is string => typeof tag === "string")
      : [];
    const legacyId = typeof command.legacyId === "string" && command.legacyId.trim() ? command.legacyId.trim() : undefined;
    const source = command.source === null || command.source === undefined ? null : command.source;
    const variants = Array.isArray(command.variants) ? command.variants : [];
    const itemId = `inbox-${randomUUID()}`;
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      if (legacyId) {
        const existing = this.database
          .prepare("SELECT id FROM inbox_items WHERE legacy_id = ? AND deleted_at IS NULL")
          .get(legacyId) as { id: string } | undefined;
        if (existing) throw new CreationWorkspaceError("conflict", `收件箱已存在旧灵感 ${legacyId} 的迁移条目。`);
      }
      this.database
        .prepare(
          "INSERT INTO inbox_items(id, legacy_id, title, body, type, status, tags_json, platform_tags_json, source_json, variants_json, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)"
        )
        .run(
          itemId,
          legacyId ?? null,
          title,
          body,
          kind,
          status,
          JSON.stringify(tags),
          JSON.stringify(platformTags),
          source ? JSON.stringify(source) : null,
          JSON.stringify(variants),
          timestamp,
          timestamp
        );
      const logged = this.database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (NULL, ?, ?, ?)")
        .run("inbox.create", JSON.stringify([{ entity: "inbox", id: itemId, action: "created", revision: 1 }]), timestamp);
      this.database.exec("COMMIT");
      this.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: "",
        commandType: "inbox.create",
        changes: [{ entity: "inbox", id: itemId, action: "created", revision: 1 }]
      });
      return { commandType: "inbox.create", sequence: Number(logged.lastInsertRowid), itemId, revision: 1, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法创建收件箱条目。");
    }
  }

  private runInboxUpdate(command: InboxUpdateCommand): InboxItemResult {
    const itemId = validateId(command.itemId, "条目");
    const baseRevision = validateBaseRevision(command.baseRevision);
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const current = this.database
        .prepare("SELECT revision FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
        .get(itemId) as { revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "收件箱条目不存在。");
      if (current.revision !== baseRevision) throw new CreationWorkspaceError("revision-mismatch", "收件箱条目已被其他修改更新。");
      const sets: string[] = [];
      const params: unknown[] = [];
      if (command.title !== undefined) {
        sets.push("title = ?");
        params.push(validateTitle(command.title, "标题", 200));
      }
      if (command.body !== undefined) {
        if (command.body.length > 1_000_000) throw new CreationWorkspaceError("invalid-input", "正文不能超过 1000000 个字符。");
        sets.push("body = ?");
        params.push(command.body);
      }
      if (command.status !== undefined) {
        const status = typeof command.status === "string" && command.status.trim() ? command.status.trim() : "inbox";
        sets.push("status = ?");
        params.push(status);
      }
      if (command.tags !== undefined) {
        sets.push("tags_json = ?");
        params.push(JSON.stringify(command.tags.filter((tag): tag is string => typeof tag === "string")));
      }
      if (command.platformTags !== undefined) {
        sets.push("platform_tags_json = ?");
        params.push(JSON.stringify(command.platformTags.filter((tag): tag is string => typeof tag === "string")));
      }
      if (sets.length === 0) throw new CreationWorkspaceError("invalid-input", "没有要更新的字段。");
      const revision = current.revision + 1;
      sets.push("revision = ?");
      params.push(revision);
      sets.push("updated_at = ?");
      params.push(timestamp);
      params.push(itemId);
      this.database.prepare(`UPDATE inbox_items SET ${sets.join(", ")} WHERE id = ?`).run(...params);
      const logged = this.database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (NULL, ?, ?, ?)")
        .run("inbox.update", JSON.stringify([{ entity: "inbox", id: itemId, action: "updated", revision }]), timestamp);
      this.database.exec("COMMIT");
      this.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: "",
        commandType: "inbox.update",
        changes: [{ entity: "inbox", id: itemId, action: "updated", revision }]
      });
      return { commandType: "inbox.update", sequence: Number(logged.lastInsertRowid), itemId, revision, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新收件箱条目。");
    }
  }

  private runInboxDelete(command: InboxDeleteCommand): InboxItemResult {
    const itemId = validateId(command.itemId, "条目");
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const current = this.database
        .prepare("SELECT revision FROM inbox_items WHERE id = ? AND deleted_at IS NULL")
        .get(itemId) as { revision: number } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "收件箱条目不存在。");
      this.database.prepare("UPDATE inbox_items SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?").run(timestamp, itemId);
      this.database.exec("COMMIT");
      return { commandType: "inbox.delete", sequence: 0, itemId, revision: current.revision + 1, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法删除收件箱条目。");
    }
  }

  private runStatsView(projectId: string): ProjectStatsView | null {
    this.requireProject(projectId);

    const sceneRows = this.database
      .prepare(
        `SELECT s.body_json
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?`
      )
      .all(projectId) as Array<{ body_json: string }>;
    let han = 0;
    let nonWhitespace = 0;
    let withPunctuation = 0;
    for (const row of sceneRows) {
      const plain = extractSceneText(row.body_json);
      for (const character of plain) {
        if (/\p{Script=Han}/u.test(character)) {
          han += 1;
          withPunctuation += 1;
        } else if (!/\s/u.test(character) && /[\p{P}\p{S}]/u.test(character)) {
          withPunctuation += 1;
        }
        if (!/\s/u.test(character)) nonWhitespace += 1;
      }
    }

    const sessionRows = this.database
      .prepare(
        "SELECT started_at, active_seconds, net_chars FROM writing_sessions WHERE project_id = ? ORDER BY started_at"
      )
      .all(projectId) as Array<{ started_at: string; active_seconds: number; net_chars: number }>;

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startOfWeek = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7));
    const dailyMap = new Map<string, { netChars: number; activeSeconds: number }>();
    const localDateKey = (date: Date): string => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, "0");
      const day = String(date.getDate()).padStart(2, "0");
      return `${year}-${month}-${day}`;
    };
    let todayMinutes = 0;
    let weekMinutes = 0;
    let totalMinutes = 0;
    const daySet = new Set<string>();
    for (const row of sessionRows) {
      const started = new Date(row.started_at);
      const key = localDateKey(started);
      daySet.add(key);
      const entry = dailyMap.get(key) ?? { netChars: 0, activeSeconds: 0 };
      entry.netChars += row.net_chars;
      entry.activeSeconds += row.active_seconds;
      dailyMap.set(key, entry);
      totalMinutes += row.active_seconds;
      if (started >= startOfToday) todayMinutes += row.active_seconds;
      if (started >= startOfWeek) weekMinutes += row.active_seconds;
    }
    const daily: ProjectDailyStat[] = [];
    for (let offset = 13; offset >= 0; offset -= 1) {
      const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset);
      const key = localDateKey(date);
      const entry = dailyMap.get(key);
      daily.push({
        date: key,
        netChars: entry?.netChars ?? 0,
        activeSeconds: entry?.activeSeconds ?? 0
      });
    }

    let streakDays = 0;
    const cursor = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    for (let offset = 0; offset < 3650; offset += 1) {
      if (!daySet.has(localDateKey(cursor))) break;
      streakDays += 1;
      cursor.setDate(cursor.getDate() - 1);
    }

    const revisionRow = this.database
      .prepare(
        "SELECT count(*) AS count FROM change_log WHERE project_id = ? AND command_type IN ('scene.updateBody', 'replace.apply')"
      )
      .get(projectId) as { count: number };
    const statusRows = this.database
      .prepare(
        "SELECT status, count(*) AS count FROM chapters WHERE project_id = ? AND deleted_at IS NULL GROUP BY status"
      )
      .all(projectId) as Array<{ status: string; count: number }>;
    const snapshotRow = this.database
      .prepare("SELECT count(*) AS count FROM snapshots WHERE project_id = ?")
      .get(projectId) as { count: number };

    return {
      projectId,
      words: { han, nonWhitespace, withPunctuation },
      sessionMinutes: {
        today: Math.round(todayMinutes / 60),
        week: Math.round(weekMinutes / 60),
        total: Math.round(totalMinutes / 60)
      },
      daily,
      revisionCount: revisionRow.count,
      chapterStatusCounts: statusRows.map((row) => ({ status: row.status, count: row.count })),
      snapshotCount: snapshotRow.count,
      streakDays
    };
  }

  private readCard(cardId: string): CardSummary | null {
    const row = this.database
      .prepare(
        `SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, created_at, updated_at, revision
         FROM cards WHERE id = ? AND deleted_at IS NULL`
      )
      .get(cardId) as
      | {
          id: string;
          project_id: string;
          kind: string;
          title: string;
          aliases_json: string;
          fields_json: string;
          tags_json: string;
          created_at: string;
          updated_at: string;
          revision: number;
        }
      | undefined;
    if (!row) return null;
    return this.cardFromRow(row);
  }

  private readCardRelations(cardId: string): { outgoing: CardRelation[]; incoming: CardRelation[] } {
    const mapRelation = (row: {
      id: string;
      project_id: string;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      created_at: string;
      forward_name: string | null;
    }): CardRelation => ({
      id: row.id,
      projectId: row.project_id,
      fromCardId: row.from_card_id,
      toCardId: row.to_card_id,
      relationTypeId: row.relation_type,
      forwardName: row.forward_name ?? row.relation_type,
      note: row.note,
      createdAt: row.created_at
    });
    const base = `
      SELECT r.id, r.project_id, r.from_card_id, r.to_card_id, r.relation_type, r.note, r.created_at, rt.forward_name
      FROM card_relations r LEFT JOIN relation_types rt ON rt.id = r.relation_type`;
    const outgoing = (
      this.database.prepare(`${base} WHERE r.from_card_id = ? ORDER BY r.created_at, r.id`).all(cardId) as Array<{
        id: string;
        project_id: string;
        from_card_id: string;
        to_card_id: string;
        relation_type: string;
        note: string | null;
        created_at: string;
        forward_name: string | null;
      }>
    ).map(mapRelation);
    const incoming = (
      this.database.prepare(`${base} WHERE r.to_card_id = ? ORDER BY r.created_at, r.id`).all(cardId) as Array<{
        id: string;
        project_id: string;
        from_card_id: string;
        to_card_id: string;
        relation_type: string;
        note: string | null;
        created_at: string;
        forward_name: string | null;
      }>
    ).map(mapRelation);
    return { outgoing, incoming };
  }

  private readProjectExport(projectId: string): ProjectExportView | null {
    const project = this.database
      .prepare("SELECT id, title FROM projects WHERE id = ?")
      .get(projectId) as { id: string; title: string } | undefined;
    if (!project) return null;

    const volumeRows = this.database
      .prepare("SELECT id, title FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
      .all(projectId) as Array<{ id: string; title: string }>;
    const chapterRows = this.database
      .prepare(
        `SELECT id, volume_id, title, numbering_kind, custom_number FROM chapters
         WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id`
      )
      .all(projectId) as Array<{
      id: string;
      volume_id: string | null;
      title: string;
      numbering_kind: ChapterNumberingKind;
      custom_number: string | null;
    }>;
    const sceneRows = this.database
      .prepare(
        `SELECT s.id, s.chapter_id, s.title, s.body_json FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id`
      )
      .all(projectId) as Array<{ id: string; chapter_id: string; title: string; body_json: string }>;

    const scenesByChapter = new Map<string, ProjectExportScene[]>();
    for (const scene of sceneRows) {
      const list = scenesByChapter.get(scene.chapter_id);
      const entry: ProjectExportScene = { id: scene.id, title: scene.title, text: extractSceneText(scene.body_json) };
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
      return { id: volume.id, title: volume.title, chapters };
    });
    if (looseChapters.length > 0) {
      volumes.push({ id: "loose", title: "未分卷", chapters: looseChapters });
    }
    return { projectId: project.id, title: project.title, volumes };
  }

  private readSceneBody(sceneId: string): SceneBodyView | null {
    const scene = this.database
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

  private listProjects(): CreationProjectSummary[] {
    const rows = this.database
      .prepare(
        `SELECT p.id, p.title, p.setup_json, p.updated_at, p.revision,
          (SELECT count(*) FROM chapters c WHERE c.project_id = p.id) AS chapter_count,
          (SELECT count(*) FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.project_id = p.id) AS scene_count
         FROM projects p
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

  async transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  async transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  async transact(command: StructureCommand): Promise<CreationStructureResult>;
  async transact(command: CardCommand): Promise<CreationStructureResult>;
  async transact(command: HistoryCommand): Promise<CreationStructureResult>;
  async transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  async transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  async transact(command: InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand): Promise<InboxItemResult>;
  async transact(command: CreationCommand): Promise<CreationTransactionResult> {
    this.assertOpen();
    if (command.type === "scene.updateBody") {
      return this.updateSceneBody(command);
    }
    if (command.type === "project.create") {
      return this.createProjectTransaction(command);
    }
    if (STRUCTURE_COMMAND_TYPES.has(command.type)) {
      return this.executeStructureCommand(command as StructureCommand);
    }
    if (CARD_COMMAND_TYPES.has(command.type)) {
      return this.executeCardCommand(command as CardCommand);
    }
    if (HISTORY_COMMAND_TYPES.has(command.type)) {
      return this.executeHistoryCommand(command as HistoryCommand);
    }
    if (command.type === "replace.apply") {
      return this.replaceApply(command as ReplaceApplyCommand);
    }
    if (command.type === "session.report") {
      return this.sessionReport(command as SessionReportCommand);
    }
    if (command.type === "session.delete") {
      return this.sessionDelete(command as SessionDeleteCommand);
    }
    if (command.type === "inbox.create") {
      return this.runInboxCreate(command as InboxCreateCommand);
    }
    if (command.type === "inbox.update") {
      return this.runInboxUpdate(command as InboxUpdateCommand);
    }
    if (command.type === "inbox.delete") {
      return this.runInboxDelete(command as InboxDeleteCommand);
    }
    throw new CreationWorkspaceError("invalid-input", "创作工作区命令无效。");
  }

  private createProjectTransaction(command: CreateProjectCommand): CreateProjectResult {
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
      this.database.exec("BEGIN IMMEDIATE");
      this.database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run(projectId, title, setupJson, timestamp, timestamp);
      this.database
        .prepare(
          "INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
        )
        .run(volumeId, projectId, "正文", 0, timestamp, timestamp);
      this.database
        .prepare(
          "INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(chapterId, projectId, volumeId, "第一章", 0, timestamp, timestamp);
      this.database
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
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, command.type, changes, timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId,
        volumeId,
        chapterId,
        sceneId
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: command.type,
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
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

  private updateSceneBody(command: UpdateSceneBodyCommand): UpdateSceneBodyResult {
    const runtimeCommand = command as unknown as {
      sceneId?: unknown;
      baseRevision?: unknown;
      body?: unknown;
    };
    if (
      typeof runtimeCommand.sceneId !== "string" ||
      !runtimeCommand.sceneId.trim() ||
      !Number.isInteger(runtimeCommand.baseRevision) ||
      Number(runtimeCommand.baseRevision) < 1
    ) {
      throw new CreationWorkspaceError("invalid-input", "场景写入请求无效。");
    }
    if (!isValidSceneDocument(runtimeCommand.body)) {
      throw new CreationWorkspaceError("invalid-input", "场景正文必须是有效的结构化文档。");
    }
    let bodyJson: string;
    try {
      bodyJson = JSON.stringify(runtimeCommand.body);
    } catch {
      throw new CreationWorkspaceError("invalid-input", "场景正文无法序列化。");
    }
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const current = this.database
        .prepare(
          "SELECT s.revision, s.body_json, c.project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ?"
        )
        .get(command.sceneId) as { revision: number; body_json: string; project_id: string } | undefined;
      if (!current) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (current.revision !== command.baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再保存。");
      }
      const revision = current.revision + 1;
      // 每场景只保留一条 scene-autosave 快照：先删旧、再写提交前的旧正文与旧 revision。
      this.database
        .prepare("DELETE FROM snapshots WHERE subject_type = 'scene-autosave' AND subject_id = ?")
        .run(command.sceneId);
      this.database
        .prepare(
          "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        )
        .run(
          `snapshot-${randomUUID()}`,
          current.project_id,
          "scene-autosave",
          command.sceneId,
          JSON.stringify({ body: JSON.parse(current.body_json), revision: current.revision }),
          timestamp
        );
      this.database
        .prepare("UPDATE scenes SET body_json = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(bodyJson, timestamp, revision, command.sceneId);
      this.database
        .prepare("UPDATE projects SET updated_at = ? WHERE id = ?")
        .run(timestamp, current.project_id);
      const changes = JSON.stringify([
        { entity: "scene", id: command.sceneId, action: "updated", revision }
      ]);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(current.project_id, command.type, changes, timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType: command.type,
        sequence: Number(logged.lastInsertRowid),
        projectId: current.project_id,
        sceneId: command.sceneId,
        revision,
        updatedAt: timestamp
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: current.project_id,
        commandType: command.type,
        changes: JSON.parse(changes) as CreationWorkspaceEvent["changes"]
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法提交场景正文事务。");
    }
  }

  private executeStructureCommand(command: StructureCommand): CreationStructureResult {
    switch (command.type) {
      case "volume.create":
        return this.createVolume(command);
      case "volume.rename":
        return this.renameVolume(command);
      case "volume.reorder":
        return this.reorderVolume(command);
      case "volume.delete":
        return this.deleteVolume(command);
      case "chapter.create":
        return this.createChapter(command);
      case "chapter.rename":
        return this.renameChapter(command);
      case "chapter.reorder":
        return this.reorderChapter(command);
      case "chapter.move":
        return this.moveChapter(command);
      case "chapter.delete":
        return this.deleteChapter(command);
      case "chapter.setStatus":
        return this.setChapterStatus(command);
      case "chapter.setNumbering":
        return this.setChapterNumbering(command);
      case "chapter.split":
        return this.splitChapter(command);
      case "chapter.merge":
        return this.mergeChapters(command);
      case "chapters.setStatus":
        return this.batchSetChapterStatus(command);
      case "scene.create":
        return this.createScene(command);
      case "scene.rename":
        return this.renameScene(command);
      case "scene.reorder":
        return this.reorderScene(command);
      case "scene.move":
        return this.moveScene(command);
      case "scene.delete":
        return this.deleteScene(command);
    }
  }

  private runStructureTransaction(
    commandType: string,
    op: (timestamp: string) => {
      projectId: string;
      entityId: string;
      revision: number;
      changes: CreationWorkspaceEvent["changes"];
    }
  ): CreationStructureResult {
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const outcome = op(timestamp);
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(outcome.projectId, commandType, JSON.stringify(outcome.changes), timestamp);
      this.database.exec("COMMIT");
      const result = {
        commandType,
        sequence: Number(logged.lastInsertRowid),
        projectId: outcome.projectId,
        entityId: outcome.entityId,
        revision: outcome.revision,
        updatedAt: timestamp
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId: outcome.projectId,
        commandType,
        changes: outcome.changes
      });
      return result;
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
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

  private requireProject(projectId: string): void {
    const project = this.database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId) as { id: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
  }

  private requireVolume(volumeId: string): { id: string; project_id: string; title: string; revision: number } {
    const volume = this.database
      .prepare("SELECT id, project_id, title, revision FROM volumes WHERE id = ? AND deleted_at IS NULL")
      .get(volumeId) as { id: string; project_id: string; title: string; revision: number } | undefined;
    if (!volume) throw new CreationWorkspaceError("not-found", "卷不存在。");
    return volume;
  }

  private requireChapter(chapterId: string): {
    id: string;
    project_id: string;
    volume_id: string | null;
    title: string;
    revision: number;
  } {
    const chapter = this.database
      .prepare("SELECT id, project_id, volume_id, title, revision FROM chapters WHERE id = ? AND deleted_at IS NULL")
      .get(chapterId) as
      | { id: string; project_id: string; volume_id: string | null; title: string; revision: number }
      | undefined;
    if (!chapter) throw new CreationWorkspaceError("not-found", "章节不存在。");
    return chapter;
  }

  private requireScene(sceneId: string): { id: string; chapter_id: string; project_id: string; revision: number } {
    const scene = this.database
      .prepare(
        `SELECT s.id, s.chapter_id, s.revision, c.project_id
         FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE s.id = ? AND s.deleted_at IS NULL`
      )
      .get(sceneId) as { id: string; chapter_id: string; project_id: string; revision: number } | undefined;
    if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
    return scene;
  }

  private assertSameProject(projectId: string, otherProjectId: string, label: string): void {
    if (projectId !== otherProjectId) {
      throw new CreationWorkspaceError("invalid-input", `${label}不属于同一作品。`);
    }
  }

  private touchProject(projectId: string, timestamp: string): void {
    this.database.prepare("UPDATE projects SET updated_at = ? WHERE id = ?").run(timestamp, projectId);
  }

  /** 按组重排序号；targetId 不在该组时只重写现有序号（用于跨组移动后的原组收敛）。 */
  private reorderEntityIds(
    table: string,
    whereSql: string,
    params: unknown[],
    targetId: string,
    beforeId: string | undefined
  ): void {
    const rows = this.database
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
      const update = this.database.prepare(`UPDATE ${table} SET sort_order = ? WHERE id = ?`);
      list.forEach((id, index) => update.run(index, id));
    }
  }

  private readWorkflow(projectId: string): string[] {
    const project = this.database
      .prepare("SELECT setup_json FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
    return parseStoredSetup(project.setup_json).chapterWorkflow;
  }

  private requireWorkflowStatus(projectId: string, status: string): void {
    if (!this.readWorkflow(projectId).includes(status)) {
      throw new CreationWorkspaceError("invalid-input", `章节状态“${status}”不在作品工作流中。`);
    }
  }

  private resolveOrCreateDefaultVolume(projectId: string, timestamp: string): string {
    const existing = this.database
      .prepare("SELECT id FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id LIMIT 1")
      .get(projectId) as { id: string } | undefined;
    if (existing) return existing.id;
    const volumeId = `volume-${randomUUID()}`;
    this.database
      .prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
      .run(volumeId, projectId, "正文", 0, timestamp, timestamp);
    return volumeId;
  }

  private softDeleteChapterTreeByVolume(volumeId: string, timestamp: string): void {
    const chapters = this.database
      .prepare("SELECT id FROM chapters WHERE volume_id = ? AND deleted_at IS NULL")
      .all(volumeId) as Array<{ id: string }>;
    for (const chapter of chapters) {
      this.database
        .prepare(
          "UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE chapter_id = ? AND deleted_at IS NULL"
        )
        .run(timestamp, timestamp, chapter.id);
      this.database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(timestamp, timestamp, chapter.id);
    }
  }

  private createVolume(command: VolumeCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const title = validateTitle(command.title, "卷名");
    const beforeId = resolveBeforeId(command.beforeVolumeId, "目标卷");
    const volumeId = `volume-${randomUUID()}`;
    return this.runStructureTransaction("volume.create", (timestamp) => {
      this.requireProject(projectId);
      if (beforeId !== undefined) {
        this.assertSameProject(projectId, this.requireVolume(beforeId).project_id, "目标卷");
      }
      this.database
        .prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run(volumeId, projectId, title, 0, timestamp, timestamp);
      this.reorderEntityIds("volumes", "project_id = ?", [projectId], volumeId, beforeId);
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: volumeId,
        revision: 1,
        changes: [{ entity: "volume", id: volumeId, action: "created", revision: 1 }]
      };
    });
  }

  private renameVolume(command: VolumeRenameCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    const title = validateTitle(command.title, "卷名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("volume.rename", (timestamp) => {
      const volume = this.requireVolume(volumeId);
      if (volume.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "卷已被更新，请重新读取后再操作。");
      }
      const revision = volume.revision + 1;
      this.database
        .prepare("UPDATE volumes SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, volumeId);
      this.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "updated", revision }]
      };
    });
  }

  private reorderVolume(command: VolumeReorderCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    const beforeId = resolveBeforeId(command.beforeVolumeId, "目标卷");
    return this.runStructureTransaction("volume.reorder", (timestamp) => {
      const volume = this.requireVolume(volumeId);
      if (beforeId !== undefined) {
        this.assertSameProject(volume.project_id, this.requireVolume(beforeId).project_id, "目标卷");
      }
      this.reorderEntityIds("volumes", "project_id = ?", [volume.project_id], volumeId, beforeId);
      const revision = volume.revision + 1;
      this.database
        .prepare("UPDATE volumes SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, volumeId);
      this.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "moved", revision }]
      };
    });
  }

  private deleteVolume(command: VolumeDeleteCommand): CreationStructureResult {
    const volumeId = validateId(command.volumeId, "卷");
    return this.runStructureTransaction("volume.delete", (timestamp) => {
      const volume = this.requireVolume(volumeId);
      this.softDeleteChapterTreeByVolume(volumeId, timestamp);
      const revision = volume.revision + 1;
      this.database
        .prepare("UPDATE volumes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, volumeId);
      this.touchProject(volume.project_id, timestamp);
      return {
        projectId: volume.project_id,
        entityId: volumeId,
        revision,
        changes: [{ entity: "volume", id: volumeId, action: "deleted", revision }]
      };
    });
  }

  private createChapter(command: ChapterCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const title = validateTitle(command.title, "章节名");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    const chapterId = `chapter-${randomUUID()}`;
    return this.runStructureTransaction("chapter.create", (timestamp) => {
      this.requireProject(projectId);
      let volumeId: string | null;
      if (command.volumeId !== undefined && command.volumeId !== null) {
        volumeId = validateId(command.volumeId, "卷");
        this.assertSameProject(projectId, this.requireVolume(volumeId).project_id, "卷");
      } else {
        volumeId = this.resolveOrCreateDefaultVolume(projectId, timestamp);
      }
      if (beforeId !== undefined) {
        const before = this.requireChapter(beforeId);
        if (before.volume_id !== volumeId) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于同一卷。");
        }
      }
      this.database
        .prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(chapterId, projectId, volumeId, title, 0, timestamp, timestamp);
      if (volumeId) {
        this.reorderEntityIds("chapters", "volume_id = ?", [volumeId], chapterId, beforeId);
      } else {
        this.reorderEntityIds("chapters", "project_id = ? AND volume_id IS NULL", [projectId], chapterId, beforeId);
      }
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: chapterId,
        revision: 1,
        changes: [{ entity: "chapter", id: chapterId, action: "created", revision: 1 }]
      };
    });
  }

  private renameChapter(command: ChapterRenameCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const title = validateTitle(command.title, "章节名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("chapter.rename", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      const revision = chapter.revision + 1;
      this.database
        .prepare("UPDATE chapters SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, chapterId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  private reorderChapter(command: ChapterReorderCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    return this.runStructureTransaction("chapter.reorder", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      const whereSql = chapter.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      const whereParam = chapter.volume_id ?? chapter.project_id;
      if (beforeId !== undefined) {
        const before = this.requireChapter(beforeId);
        if (before.volume_id !== chapter.volume_id) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于同一卷。");
        }
      }
      this.reorderEntityIds("chapters", whereSql, [whereParam], chapterId, beforeId);
      const revision = chapter.revision + 1;
      this.database
        .prepare("UPDATE chapters SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, chapterId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "moved", revision }]
      };
    });
  }

  private moveChapter(command: ChapterMoveCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const targetVolumeId = validateId(command.targetVolumeId, "目标卷");
    const beforeId = resolveBeforeId(command.beforeChapterId, "目标章节");
    return this.runStructureTransaction("chapter.move", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      const targetVolume = this.requireVolume(targetVolumeId);
      this.assertSameProject(chapter.project_id, targetVolume.project_id, "目标卷");
      const oldVolumeId = chapter.volume_id;
      if (beforeId !== undefined) {
        const before = this.requireChapter(beforeId);
        if (before.volume_id !== targetVolumeId) {
          throw new CreationWorkspaceError("invalid-input", "目标章节不属于目标卷。");
        }
      }
      this.database
        .prepare("UPDATE chapters SET volume_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(targetVolumeId, timestamp, chapterId);
      if (oldVolumeId) {
        this.reorderEntityIds("chapters", "volume_id = ?", [oldVolumeId], chapterId, undefined);
      } else {
        this.reorderEntityIds("chapters", "project_id = ? AND volume_id IS NULL", [chapter.project_id], chapterId, undefined);
      }
      this.reorderEntityIds("chapters", "volume_id = ?", [targetVolumeId], chapterId, beforeId);
      const revision = chapter.revision + 1;
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "moved", revision }]
      };
    });
  }

  private deleteChapter(command: ChapterDeleteCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    return this.runStructureTransaction("chapter.delete", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      this.database
        .prepare(
          "UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = revision + 1 WHERE chapter_id = ? AND deleted_at IS NULL"
        )
        .run(timestamp, timestamp, chapterId);
      const revision = chapter.revision + 1;
      this.database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, chapterId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "deleted", revision }]
      };
    });
  }

  private setChapterStatus(command: ChapterSetStatusCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const status = validateTitle(command.status, "章节状态", 50);
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("chapter.setStatus", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      this.requireWorkflowStatus(chapter.project_id, status);
      const revision = chapter.revision + 1;
      this.database
        .prepare("UPDATE chapters SET status = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(status, timestamp, revision, chapterId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  private setChapterNumbering(command: ChapterSetNumberingCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    if (!CHAPTER_NUMBERING_KINDS.has(command.numbering)) {
      throw new CreationWorkspaceError("invalid-input", "章节编号方式无效。");
    }
    const baseRevision = validateBaseRevision(command.baseRevision);
    const customNumber = command.numbering === "custom" ? validateTitle(command.customNumber, "自定义编号", 50) : null;
    return this.runStructureTransaction("chapter.setNumbering", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      if (chapter.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "章节已被更新，请重新读取后再操作。");
      }
      const revision = chapter.revision + 1;
      this.database
        .prepare("UPDATE chapters SET numbering_kind = ?, custom_number = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(command.numbering, customNumber, timestamp, revision, chapterId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: chapterId,
        revision,
        changes: [{ entity: "chapter", id: chapterId, action: "updated", revision }]
      };
    });
  }

  private createScene(command: SceneCreateCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const title = validateTitle(command.title, "场景名");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    const sceneId = `scene-${randomUUID()}`;
    return this.runStructureTransaction("scene.create", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      if (beforeId !== undefined) {
        const before = this.requireScene(beforeId);
        if (before.chapter_id !== chapterId) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于同一章节。");
        }
      }
      this.database
        .prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(sceneId, chapterId, title, 0, '{"type":"doc","content":[]}', timestamp, timestamp);
      this.reorderEntityIds("scenes", "chapter_id = ?", [chapterId], sceneId, beforeId);
      this.touchProject(chapter.project_id, timestamp);
      return {
        projectId: chapter.project_id,
        entityId: sceneId,
        revision: 1,
        changes: [{ entity: "scene", id: sceneId, action: "created", revision: 1 }]
      };
    });
  }

  private renameScene(command: SceneRenameCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const title = validateTitle(command.title, "场景名");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("scene.rename", (timestamp) => {
      const scene = this.requireScene(sceneId);
      if (scene.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再操作。");
      }
      const revision = scene.revision + 1;
      this.database
        .prepare("UPDATE scenes SET title = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(title, timestamp, revision, sceneId);
      this.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision }]
      };
    });
  }

  private reorderScene(command: SceneReorderCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    return this.runStructureTransaction("scene.reorder", (timestamp) => {
      const scene = this.requireScene(sceneId);
      if (beforeId !== undefined) {
        const before = this.requireScene(beforeId);
        if (before.chapter_id !== scene.chapter_id) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于同一章节。");
        }
      }
      this.reorderEntityIds("scenes", "chapter_id = ?", [scene.chapter_id], sceneId, beforeId);
      const revision = scene.revision + 1;
      this.database
        .prepare("UPDATE scenes SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, sceneId);
      this.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "moved", revision }]
      };
    });
  }

  private moveScene(command: SceneMoveCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    const targetChapterId = validateId(command.targetChapterId, "目标章节");
    const beforeId = resolveBeforeId(command.beforeSceneId, "目标场景");
    return this.runStructureTransaction("scene.move", (timestamp) => {
      const scene = this.requireScene(sceneId);
      const targetChapter = this.requireChapter(targetChapterId);
      this.assertSameProject(scene.project_id, targetChapter.project_id, "目标章节");
      const oldChapterId = scene.chapter_id;
      if (beforeId !== undefined) {
        const before = this.requireScene(beforeId);
        if (before.chapter_id !== targetChapterId) {
          throw new CreationWorkspaceError("invalid-input", "目标场景不属于目标章节。");
        }
      }
      this.database
        .prepare("UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?")
        .run(targetChapterId, timestamp, sceneId);
      this.reorderEntityIds("scenes", "chapter_id = ?", [oldChapterId], sceneId, undefined);
      this.reorderEntityIds("scenes", "chapter_id = ?", [targetChapterId], sceneId, beforeId);
      const revision = scene.revision + 1;
      this.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "moved", revision }]
      };
    });
  }

  private deleteScene(command: SceneDeleteCommand): CreationStructureResult {
    const sceneId = validateId(command.sceneId, "场景");
    return this.runStructureTransaction("scene.delete", (timestamp) => {
      const scene = this.requireScene(sceneId);
      const revision = scene.revision + 1;
      this.database
        .prepare("UPDATE scenes SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, sceneId);
      this.touchProject(scene.project_id, timestamp);
      return {
        projectId: scene.project_id,
        entityId: sceneId,
        revision,
        changes: [{ entity: "scene", id: sceneId, action: "deleted", revision }]
      };
    });
  }

  private splitChapter(command: ChapterSplitCommand): CreationStructureResult {
    const chapterId = validateId(command.chapterId, "章节");
    const splitSceneId = validateId(command.splitSceneId, "拆分场景");
    const newChapterTitle =
      command.newChapterTitle === undefined || command.newChapterTitle === null
        ? "新章节"
        : validateTitle(command.newChapterTitle, "新章节名");
    const newChapterId = `chapter-${randomUUID()}`;
    return this.runStructureTransaction("chapter.split", (timestamp) => {
      const chapter = this.requireChapter(chapterId);
      const splitScene = this.requireScene(splitSceneId);
      if (splitScene.chapter_id !== chapterId) {
        throw new CreationWorkspaceError("invalid-input", "拆分场景不属于该章节。");
      }
      const sceneRows = this.database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(chapterId) as Array<{ id: string }>;
      const splitIndex = sceneRows.findIndex((row) => row.id === splitSceneId);
      if (splitIndex <= 0) {
        throw new CreationWorkspaceError("invalid-input", "拆分点必须至少保留一个场景在当前章节。");
      }
      const keepScenes = sceneRows.slice(0, splitIndex);
      const movedScenes = sceneRows.slice(splitIndex);
      this.database
        .prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)")
        .run(newChapterId, chapter.project_id, chapter.volume_id, newChapterTitle, 0, timestamp, timestamp);
      const moveScene = this.database.prepare(
        "UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const row of movedScenes) moveScene.run(newChapterId, timestamp, row.id);
      const reorderScene = this.database.prepare("UPDATE scenes SET sort_order = ? WHERE id = ?");
      keepScenes.forEach((row, index) => reorderScene.run(index, row.id));
      movedScenes.forEach((row, index) => reorderScene.run(index, row.id));
      const whereSql = chapter.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      const whereParam = chapter.volume_id ?? chapter.project_id;
      const chapterRows = this.database
        .prepare(`SELECT id FROM chapters WHERE ${whereSql} AND deleted_at IS NULL ORDER BY sort_order, id`)
        .all(whereParam) as Array<{ id: string }>;
      const order = chapterRows.map((row) => row.id).filter((id) => id !== newChapterId);
      const at = order.indexOf(chapterId);
      order.splice(at + 1, 0, newChapterId);
      const reorderChapter = this.database.prepare("UPDATE chapters SET sort_order = ? WHERE id = ?");
      order.forEach((id, index) => reorderChapter.run(index, id));
      this.touchProject(chapter.project_id, timestamp);
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

  private mergeChapters(command: ChapterMergeCommand): CreationStructureResult {
    const sourceChapterId = validateId(command.sourceChapterId, "源章节");
    const targetChapterId = validateId(command.targetChapterId, "目标章节");
    return this.runStructureTransaction("chapter.merge", (timestamp) => {
      const source = this.requireChapter(sourceChapterId);
      const target = this.requireChapter(targetChapterId);
      if (source.id === target.id) {
        throw new CreationWorkspaceError("invalid-input", "不能把章节合并到自身。");
      }
      if (source.project_id !== target.project_id || source.volume_id !== target.volume_id) {
        throw new CreationWorkspaceError("invalid-input", "只能合并同一卷的章节。");
      }
      const sourceScenes = this.database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(sourceChapterId) as Array<{ id: string }>;
      const targetScenes = this.database
        .prepare("SELECT id FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
        .all(targetChapterId) as Array<{ id: string }>;
      const moveScene = this.database.prepare(
        "UPDATE scenes SET chapter_id = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const row of sourceScenes) moveScene.run(targetChapterId, timestamp, row.id);
      const reorderScene = this.database.prepare("UPDATE scenes SET sort_order = ? WHERE id = ?");
      [...targetScenes.map((row) => row.id), ...sourceScenes.map((row) => row.id)].forEach((id, index) =>
        reorderScene.run(index, id)
      );
      const revision = target.revision + 1;
      this.database
        .prepare("UPDATE chapters SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, source.revision + 1, sourceChapterId);
      this.database
        .prepare("UPDATE chapters SET updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, revision, targetChapterId);
      const whereSql = target.volume_id ? "volume_id = ?" : "project_id = ? AND volume_id IS NULL";
      this.reorderEntityIds("chapters", whereSql, [target.volume_id ?? target.project_id], sourceChapterId, undefined);
      this.touchProject(target.project_id, timestamp);
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

  private batchSetChapterStatus(command: ChaptersSetStatusCommand): CreationStructureResult {
    if (!Array.isArray(command.chapterIds) || command.chapterIds.length === 0) {
      throw new CreationWorkspaceError("invalid-input", "章节列表不能为空。");
    }
    const chapterIds = [...new Set(command.chapterIds.map((id) => validateId(id, "章节")))];
    const status = validateTitle(command.status, "章节状态", 50);
    return this.runStructureTransaction("chapters.setStatus", (timestamp) => {
      let projectId: string | undefined;
      const changes: CreationWorkspaceEvent["changes"] = [];
      const updateChapter = this.database.prepare(
        "UPDATE chapters SET status = ?, updated_at = ?, revision = revision + 1 WHERE id = ?"
      );
      for (const chapterId of chapterIds) {
        const chapter = this.requireChapter(chapterId);
        if (projectId === undefined) {
          projectId = chapter.project_id;
          if (!this.readWorkflow(projectId).includes(status)) {
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
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: chapterIds[0] ?? projectId,
        revision: changes[0]?.revision ?? 1,
        changes
      };
    });
  }

  private executeCardCommand(command: CardCommand): CreationStructureResult {
    switch (command.type) {
      case "cardType.create":
        return this.createCardType(command);
      case "relationType.create":
        return this.createRelationType(command);
      case "card.create":
        return this.createCard(command);
      case "card.update":
        return this.updateCard(command);
      case "card.delete":
        return this.deleteCard(command);
      case "cardRelation.create":
        return this.createCardRelation(command);
      case "cardRelation.delete":
        return this.deleteCardRelation(command);
    }
  }

  private requireCard(cardId: string): {
    id: string;
    project_id: string;
    kind: string;
    title: string;
    aliases: string[];
    fields: Record<string, unknown>;
    tags: string[];
    revision: number;
  } {
    const card = this.database
      .prepare(
        "SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, revision FROM cards WHERE id = ? AND deleted_at IS NULL"
      )
      .get(cardId) as
      | {
          id: string;
          project_id: string;
          kind: string;
          title: string;
          aliases_json: string;
          fields_json: string;
          tags_json: string;
          revision: number;
        }
      | undefined;
    if (!card) throw new CreationWorkspaceError("not-found", "卡片不存在。");
    return {
      id: card.id,
      project_id: card.project_id,
      kind: card.kind,
      title: card.title,
      aliases: JSON.parse(card.aliases_json) as string[],
      fields: JSON.parse(card.fields_json) as Record<string, unknown>,
      tags: JSON.parse(card.tags_json) as string[],
      revision: card.revision
    };
  }

  private requireRelationType(relationTypeId: string): { id: string } {
    const type = this.database
      .prepare("SELECT id FROM relation_types WHERE id = ?")
      .get(relationTypeId) as { id: string } | undefined;
    if (!type) throw new CreationWorkspaceError("not-found", "关系类型不存在。");
    return type;
  }

  private resolveCardTypeFields(projectId: string, kind: string): CardFieldSchema[] {
    const type = this.database
      .prepare(
        "SELECT fields_json FROM card_types WHERE (project_id IS NULL OR project_id = ?) AND kind = ? ORDER BY project_id DESC LIMIT 1"
      )
      .get(projectId, kind) as { fields_json: string } | undefined;
    if (!type) throw new CreationWorkspaceError("invalid-input", `卡片类型“${kind}”不存在。`);
    try {
      return JSON.parse(type.fields_json) as CardFieldSchema[];
    } catch {
      throw new CreationWorkspaceError("integrity", "卡片类型字段数据损坏。");
    }
  }

  private createCardType(command: CardTypeCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const name = validateTitle(command.name, "卡片类型名");
    const fields = validateCardFieldSchemaList(command.fields);
    const kind = `custom-${randomUUID().slice(0, 8)}`;
    const typeId = `card-type-${randomUUID()}`;
    return this.runStructureTransaction("cardType.create", (timestamp) => {
      this.requireProject(projectId);
      const maxOrder = this.database
        .prepare("SELECT coalesce(max(sort_order), -1) AS m FROM card_types WHERE project_id = ?")
        .get(projectId) as { m: number };
      this.database
        .prepare(
          "INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(typeId, projectId, kind, name, JSON.stringify(fields), maxOrder.m + 1, timestamp, timestamp);
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: typeId,
        revision: 1,
        changes: [{ entity: "cardType", id: typeId, action: "created", revision: 1 }]
      };
    });
  }

  private createRelationType(command: RelationTypeCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const forwardName = validateTitle(command.forwardName, "关系名称", 50);
    const reverseName = validateTitle(command.reverseName, "反向关系名称", 50);
    const fromKinds = validateStringList(command.fromKinds, "起点卡片类型", 50);
    const toKinds = validateStringList(command.toKinds, "终点卡片类型", 50);
    const relationTypeId = `relation-type-${randomUUID()}`;
    return this.runStructureTransaction("relationType.create", (timestamp) => {
      this.requireProject(projectId);
      this.database
        .prepare(
          "INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(
          relationTypeId,
          projectId,
          `rel-${randomUUID().slice(0, 8)}`,
          forwardName,
          reverseName,
          JSON.stringify(fromKinds),
          JSON.stringify(toKinds),
          timestamp,
          timestamp
        );
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: relationTypeId,
        revision: 1,
        changes: [{ entity: "relationType", id: relationTypeId, action: "created", revision: 1 }]
      };
    });
  }

  private createCard(command: CardCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const kind = validateId(command.kind, "卡片类型");
    const title = validateTitle(command.title, "卡片名称");
    const aliases = validateStringList(command.aliases, "别名");
    const tags = validateStringList(command.tags, "标签");
    const cardId = `card-${randomUUID()}`;
    return this.runStructureTransaction("card.create", (timestamp) => {
      this.requireProject(projectId);
      const typeFields = this.resolveCardTypeFields(projectId, kind);
      const fields = validateCardFieldValues(command.fields ?? {}, typeFields);
      this.database
        .prepare(
          "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(cardId, projectId, kind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), "{}", timestamp, timestamp);
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: cardId,
        revision: 1,
        changes: [{ entity: "card", id: cardId, action: "created", revision: 1 }]
      };
    });
  }

  private updateCard(command: CardUpdateCommand): CreationStructureResult {
    const cardId = validateId(command.cardId, "卡片");
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("card.update", (timestamp) => {
      const card = this.requireCard(cardId);
      if (card.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "卡片已被更新，请重新读取后再操作。");
      }
      const nextKind = command.kind === undefined ? card.kind : validateId(command.kind, "卡片类型");
      if (nextKind !== card.kind) {
        // 换类型：目标类型必须存在
        this.resolveCardTypeFields(card.project_id, nextKind);
      }
      const schemas = this.resolveCardTypeFields(card.project_id, nextKind);
      let fields = card.fields;
      if (command.fields !== undefined) {
        fields = validateCardFieldValues(command.fields, schemas);
      } else if (nextKind !== card.kind) {
        // 换类型且未提供新字段：保留能被新 schema 识别的旧字段，其余丢弃，再补默认并校验必填
        const allowed = new Set(schemas.map((schema) => schema.key));
        const filtered: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(card.fields)) {
          if (allowed.has(key)) filtered[key] = value;
        }
        fields = validateCardFieldValues(filtered, schemas);
      }
      const title = command.title === undefined ? card.title : validateTitle(command.title, "卡片名称");
      const aliases =
        command.aliases === undefined ? card.aliases : validateStringList(command.aliases, "别名");
      const tags = command.tags === undefined ? card.tags : validateStringList(command.tags, "标签");
      const revision = card.revision + 1;
      this.database
        .prepare(
          "UPDATE cards SET kind = ?, title = ?, aliases_json = ?, fields_json = ?, tags_json = ?, updated_at = ?, revision = ? WHERE id = ?"
        )
        .run(nextKind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), timestamp, revision, cardId);
      this.touchProject(card.project_id, timestamp);
      return {
        projectId: card.project_id,
        entityId: cardId,
        revision,
        changes: [{ entity: "card", id: cardId, action: "updated", revision }]
      };
    });
  }

  private deleteCard(command: CardDeleteCommand): CreationStructureResult {
    const cardId = validateId(command.cardId, "卡片");
    return this.runStructureTransaction("card.delete", (timestamp) => {
      const card = this.requireCard(cardId);
      const revision = card.revision + 1;
      this.database
        .prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?")
        .run(cardId, cardId);
      this.database
        .prepare("UPDATE cards SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, cardId);
      this.touchProject(card.project_id, timestamp);
      return {
        projectId: card.project_id,
        entityId: cardId,
        revision,
        changes: [{ entity: "card", id: cardId, action: "deleted", revision }]
      };
    });
  }

  private createCardRelation(command: CardRelationCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const fromCardId = validateId(command.fromCardId, "起点卡片");
    const toCardId = validateId(command.toCardId, "终点卡片");
    const relationTypeId = validateId(command.relationTypeId, "关系类型");
    const note =
      command.note === undefined || command.note === null ? null : String(command.note).trim() || null;
    const relationId = `relation-${randomUUID()}`;
    return this.runStructureTransaction("cardRelation.create", (timestamp) => {
      this.requireProject(projectId);
      const from = this.requireCard(fromCardId);
      const to = this.requireCard(toCardId);
      if (from.project_id !== projectId || to.project_id !== projectId) {
        throw new CreationWorkspaceError("invalid-input", "关系卡片必须属于同一作品。");
      }
      if (fromCardId === toCardId) {
        throw new CreationWorkspaceError("invalid-input", "不能建立卡片到自身的关系。");
      }
      this.requireRelationType(relationTypeId);
      this.database
        .prepare(
          "INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        .run(relationId, projectId, fromCardId, toCardId, relationTypeId, note, timestamp);
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: relationId,
        revision: 1,
        changes: [{ entity: "cardRelation", id: relationId, action: "created", revision: 1 }]
      };
    });
  }

  private deleteCardRelation(command: CardRelationDeleteCommand): CreationStructureResult {
    const relationId = validateId(command.relationId, "关系");
    return this.runStructureTransaction("cardRelation.delete", (timestamp) => {
      const relation = this.database
        .prepare("SELECT id, project_id FROM card_relations WHERE id = ?")
        .get(relationId) as { id: string; project_id: string } | undefined;
      if (!relation) throw new CreationWorkspaceError("not-found", "关系不存在。");
      this.database.prepare("DELETE FROM card_relations WHERE id = ?").run(relationId);
      this.touchProject(relation.project_id, timestamp);
      return {
        projectId: relation.project_id,
        entityId: relationId,
        revision: 1,
        changes: [{ entity: "cardRelation", id: relationId, action: "deleted", revision: 1 }]
      };
    });
  }

  private executeHistoryCommand(command: HistoryCommand): CreationStructureResult {
    switch (command.type) {
      case "trash.restore":
        return this.trashRestore(command);
      case "trash.purge":
        return this.trashPurge(command);
      case "snapshot.create":
        return this.snapshotCreate(command);
      case "snapshot.restore":
        return this.snapshotRestore(command);
    }
  }

  private trashList(projectId: string): TrashItem[] {
    const items: TrashItem[] = [];
    const volumes = this.database
      .prepare(
        "SELECT id, title, deleted_at, revision FROM volumes WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id"
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of volumes) {
      items.push({ entity: "volume", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const chapters = this.database
      .prepare(
        "SELECT id, title, deleted_at, revision FROM chapters WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id"
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of chapters) {
      items.push({ entity: "chapter", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const scenes = this.database
      .prepare(
        `SELECT s.id, s.title, s.deleted_at, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id
         WHERE c.project_id = ? AND s.deleted_at IS NOT NULL ORDER BY s.deleted_at DESC, s.id`
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of scenes) {
      items.push({ entity: "scene", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    const cards = this.database
      .prepare(
        "SELECT id, title, deleted_at, revision FROM cards WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id"
      )
      .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of cards) {
      items.push({ entity: "card", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    return items;
  }

  private findTrashEntity(
    entity: TrashEntityKind,
    projectId: string,
    entityId: string
  ): { title: string; revision: number } | undefined {
    if (entity === "volume") {
      const row = this.database
        .prepare("SELECT title, revision FROM volumes WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    if (entity === "chapter") {
      const row = this.database
        .prepare("SELECT title, revision FROM chapters WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    if (entity === "scene") {
      const row = this.database
        .prepare(
          `SELECT s.title, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id
           WHERE s.id = ? AND c.project_id = ? AND s.deleted_at IS NOT NULL`
        )
        .get(entityId, projectId) as { title: string; revision: number } | undefined;
      return row;
    }
    const row = this.database
      .prepare("SELECT title, revision FROM cards WHERE id = ? AND project_id = ? AND deleted_at IS NOT NULL")
      .get(entityId, projectId) as { title: string; revision: number } | undefined;
    return row;
  }

  private trashRestore(command: TrashRestoreCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return this.runStructureTransaction("trash.restore", (timestamp) => {
      this.requireProject(projectId);
      const found = this.findTrashEntity(command.entity, projectId, entityId);
      if (!found) throw new CreationWorkspaceError("not-found", "回收站中不存在该实体。");
      if (command.entity === "volume") {
        const chapters = this.database
          .prepare("SELECT id FROM chapters WHERE volume_id = ? AND deleted_at IS NOT NULL")
          .all(entityId) as Array<{ id: string }>;
        for (const chapter of chapters) {
          this.database
            .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE chapter_id = ?")
            .run(timestamp, chapter.id);
          this.database
            .prepare("UPDATE chapters SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
            .run(timestamp, chapter.id);
        }
        this.database
          .prepare("UPDATE volumes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else if (command.entity === "chapter") {
        this.database
          .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE chapter_id = ?")
          .run(timestamp, entityId);
        this.database
          .prepare("UPDATE chapters SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else if (command.entity === "scene") {
        this.database
          .prepare("UPDATE scenes SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      } else {
        this.database
          .prepare("UPDATE cards SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
      }
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId,
        revision: found.revision + 1,
        changes: [{ entity: command.entity, id: entityId, action: "restored", revision: found.revision + 1 }]
      };
    });
  }

  private trashPurge(command: TrashPurgeCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return this.runStructureTransaction("trash.purge", (timestamp) => {
      this.requireProject(projectId);
      const found = this.findTrashEntity(command.entity, projectId, entityId);
      if (!found) throw new CreationWorkspaceError("not-found", "回收站中不存在该实体。");
      if (command.entity === "volume") {
        const chapters = this.database.prepare("SELECT id FROM chapters WHERE volume_id = ?").all(entityId) as Array<{ id: string }>;
        for (const chapter of chapters) {
          this.database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(chapter.id);
        }
        this.database.prepare("DELETE FROM chapters WHERE volume_id = ?").run(entityId);
        this.database.prepare("DELETE FROM volumes WHERE id = ?").run(entityId);
      } else if (command.entity === "chapter") {
        this.database.prepare("DELETE FROM scenes WHERE chapter_id = ?").run(entityId);
        this.database.prepare("DELETE FROM chapters WHERE id = ?").run(entityId);
      } else if (command.entity === "scene") {
        this.database.prepare("DELETE FROM scenes WHERE id = ?").run(entityId);
      } else {
        this.database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(entityId, entityId);
        this.database.prepare("DELETE FROM cards WHERE id = ?").run(entityId);
      }
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId,
        revision: found.revision,
        changes: [{ entity: command.entity, id: entityId, action: "deleted", revision: found.revision }]
      };
    });
  }

  private snapshotList(query: { projectId: string; subjectType?: SnapshotSubjectType; subjectId?: string }): SnapshotInfo[] {
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
    const rows = this.database.prepare(sql).all(...params) as Array<{
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

  private snapshotCreate(command: SnapshotCreateCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    if (command.subjectType !== "scene" && command.subjectType !== "card") {
      throw new CreationWorkspaceError("invalid-input", "快照对象类型无效。");
    }
    const subjectId = validateId(command.subjectId, "对象");
    const reason = validateTitle(command.reason, "快照说明", 200);
    const snapshotId = `snapshot-${randomUUID()}`;
    return this.runStructureTransaction("snapshot.create", (timestamp) => {
      this.requireProject(projectId);
      let payload: { reason: string; revision: number; body?: CreationDocument; card?: unknown };
      if (command.subjectType === "scene") {
        const scene = this.database
          .prepare("SELECT body_json, revision FROM scenes WHERE id = ? AND deleted_at IS NULL")
          .get(subjectId) as { body_json: string; revision: number } | undefined;
        if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
        let body: CreationDocument;
        try {
          body = JSON.parse(scene.body_json) as CreationDocument;
        } catch {
          throw new CreationWorkspaceError("integrity", "场景正文数据损坏。");
        }
        payload = { reason, revision: scene.revision, body };
      } else {
        const card = this.requireCard(subjectId);
        payload = {
          reason,
          revision: card.revision,
          card: { title: card.title, aliases: card.aliases, fields: card.fields, tags: card.tags }
        };
      }
      this.database
        .prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)")
        .run(snapshotId, projectId, command.subjectType, subjectId, JSON.stringify(payload), timestamp);
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: snapshotId,
        revision: 1,
        changes: [{ entity: "snapshot", id: snapshotId, action: "created", revision: 1 }]
      };
    });
  }

  private snapshotRestore(command: SnapshotRestoreCommand): CreationStructureResult {
    const projectId = validateId(command.projectId, "作品");
    const snapshotId = validateId(command.snapshotId, "快照");
    return this.runStructureTransaction("snapshot.restore", (timestamp) => {
      const snapshot = this.database
        .prepare("SELECT id, project_id, subject_type, subject_id, payload_json FROM snapshots WHERE id = ? AND project_id = ?")
        .get(snapshotId, projectId) as
        | { id: string; project_id: string; subject_type: string; subject_id: string; payload_json: string }
        | undefined;
      if (!snapshot) throw new CreationWorkspaceError("not-found", "快照不存在。");
      let payload: {
        reason?: string;
        revision?: number;
        body?: CreationDocument;
        card?: { title?: string; aliases?: string[]; fields?: Record<string, unknown>; tags?: string[] };
      };
      try {
        payload = JSON.parse(snapshot.payload_json);
      } catch {
        throw new CreationWorkspaceError("integrity", "快照数据损坏。");
      }
      if (snapshot.subject_type === "scene") {
        const scene = this.database
          .prepare("SELECT revision FROM scenes WHERE id = ? AND deleted_at IS NULL")
          .get(snapshot.subject_id) as { revision: number } | undefined;
        if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在，无法恢复。");
        if (!payload.body) throw new CreationWorkspaceError("integrity", "快照正文数据缺失。");
        const revision = scene.revision + 1;
        this.database
          .prepare("UPDATE scenes SET body_json = ?, updated_at = ?, revision = ? WHERE id = ?")
          .run(JSON.stringify(payload.body), timestamp, revision, snapshot.subject_id);
        this.touchProject(projectId, timestamp);
        return {
          projectId,
          entityId: snapshot.subject_id,
          revision,
          changes: [{ entity: "scene", id: snapshot.subject_id, action: "restored", revision }]
        };
      }
      const card = this.requireCard(snapshot.subject_id);
      if (!payload.card) throw new CreationWorkspaceError("integrity", "快照卡片数据缺失。");
      const revision = card.revision + 1;
      this.database
        .prepare("UPDATE cards SET title = ?, aliases_json = ?, fields_json = ?, tags_json = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(
          payload.card.title ?? card.title,
          JSON.stringify(payload.card.aliases ?? card.aliases),
          JSON.stringify(payload.card.fields ?? card.fields),
          JSON.stringify(payload.card.tags ?? card.tags),
          timestamp,
          revision,
          snapshot.subject_id
        );
      this.touchProject(projectId, timestamp);
      return {
        projectId,
        entityId: snapshot.subject_id,
        revision,
        changes: [{ entity: "card", id: snapshot.subject_id, action: "restored", revision }]
      };
    });
  }

  watch(scope: CreationWatchScope, listener: CreationWorkspaceListener): () => void {
    this.assertOpen();
    const runtimeScope = scope as unknown as { projectId?: unknown } | null;
    if (
      runtimeScope === null ||
      typeof runtimeScope !== "object" ||
      typeof listener !== "function" ||
      (runtimeScope.projectId !== undefined &&
        (typeof runtimeScope.projectId !== "string" || !runtimeScope.projectId.trim()))
    ) {
      throw new CreationWorkspaceError("invalid-input", "创作工作区订阅请求无效。");
    }
    const token = Symbol("creation-workspace-watcher");
    this.watchers.set(token, {
      scope: runtimeScope.projectId === undefined ? {} : { projectId: runtimeScope.projectId as string },
      listener
    });
    return () => {
      this.watchers.delete(token);
    };
  }

  async check(): Promise<CreationIntegrityReport> {
    this.assertOpen();
    try {
      return this.runIntegrityCheck();
    } catch (error) {
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法检查创作工作区完整性。");
    }
  }

  private runIntegrityCheck(): CreationIntegrityReport {
    const checkedAt = new Date().toISOString();
    const schemaIssues: Array<{ code: string; message: string }> = [];
    const relationIssues: Array<{ code: string; message: string }> = [];
    const resourceIssues: Array<{ code: string; message: string }> = [];
    const indexIssues: Array<{ code: string; message: string }> = [];
    const snapshotIssues: Array<{ code: string; message: string }> = [];

    const schemaVersion = Number(this.database.pragma("user_version", { simple: true }));
    if (schemaVersion !== SCHEMA_VERSION) {
      schemaIssues.push({ code: "schema-version", message: `预期 schema ${SCHEMA_VERSION}，实际为 ${schemaVersion}。` });
    }
    const journalMode = String(this.database.pragma("journal_mode", { simple: true }));
    if (journalMode !== "wal") {
      schemaIssues.push({ code: "journal-mode", message: `预期 WAL，实际为 ${journalMode}。` });
    }
    const foreignKeysEnabled = Number(this.database.pragma("foreign_keys", { simple: true })) === 1;
    if (!foreignKeysEnabled) {
      schemaIssues.push({ code: "foreign-keys-disabled", message: "SQLite 外键约束未启用。" });
    }

    const objects = this.database
      .prepare("SELECT name, type FROM sqlite_master WHERE type IN ('table', 'index')")
      .all() as Array<{ name: string; type: "table" | "index" }>;
    const tables = new Set(objects.filter((item) => item.type === "table").map((item) => item.name));
    const indexes = new Set(objects.filter((item) => item.type === "index").map((item) => item.name));
    for (const table of REQUIRED_TABLES) {
      if (!tables.has(table)) schemaIssues.push({ code: "schema-table-missing", message: `缺少数据表 ${table}。` });
    }
    for (const index of REQUIRED_INDEXES) {
      if (!indexes.has(index)) indexIssues.push({ code: "index-missing", message: `缺少索引 ${index}。` });
    }

    const integrity = String(this.database.pragma("integrity_check", { simple: true }));
    if (integrity !== "ok") schemaIssues.push({ code: "sqlite-integrity", message: "SQLite 完整性检查失败。" });
    const foreignKeyRows = this.database.pragma("foreign_key_check") as unknown[];
    if (foreignKeyRows.length > 0) {
      relationIssues.push({ code: "foreign-key", message: `发现 ${foreignKeyRows.length} 个关系完整性问题。` });
    }

    if (tables.has("resources") && tables.has("projects")) {
      const missingResources = this.database
        .prepare("SELECT count(*) AS count FROM resources r LEFT JOIN projects p ON p.id = r.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingResources.count > 0) {
        resourceIssues.push({ code: "resource-project", message: `发现 ${missingResources.count} 个失去项目归属的资源。` });
      }
    }

    if (tables.has("snapshots") && tables.has("projects")) {
      const missingSnapshots = this.database
        .prepare("SELECT count(*) AS count FROM snapshots s LEFT JOIN projects p ON p.id = s.project_id WHERE p.id IS NULL")
        .get() as { count: number };
      if (missingSnapshots.count > 0) {
        snapshotIssues.push({ code: "snapshot-project", message: `发现 ${missingSnapshots.count} 个失去项目归属的快照。` });
      }
    }

    if (tables.has("projects")) {
      const setupRows = this.database.prepare("SELECT setup_json FROM projects").all() as Array<{ setup_json: string }>;
      for (const row of setupRows) {
        try {
          parseStoredSetup(row.setup_json);
        } catch {
          schemaIssues.push({ code: "project-setup-json", message: "发现损坏的创作项目设置数据。" });
          break;
        }
      }
    }

    const schema = createSection(schemaIssues);
    const relations = createSection(relationIssues);
    const resources = createSection(resourceIssues);
    const indexesSection = createSection(indexIssues);
    const snapshots = createSection(snapshotIssues);
    const count = (table: string): number => {
      if (!tables.has(table)) return 0;
      return (this.database.prepare(`SELECT count(*) AS count FROM ${table}`).get() as { count: number }).count;
    };
    const latestSequence = tables.has("change_log")
      ? (
          this.database.prepare("SELECT coalesce(max(sequence), 0) AS sequence FROM change_log").get() as {
            sequence: number;
          }
        ).sequence
      : 0;
    return {
      ok: schema.ok && relations.ok && resources.ok && indexesSection.ok && snapshots.ok,
      schemaVersion,
      latestSequence,
      checkedAt,
      counts: {
        projects: count("projects"),
        volumes: count("volumes"),
        chapters: count("chapters"),
        scenes: count("scenes"),
        cards: count("cards"),
        relations: count("card_relations"),
        resources: count("resources"),
        snapshots: count("snapshots"),
        sessions: count("writing_sessions"),
        inbox: count("inbox_items")
      },
      schema,
      relations,
      resources,
      indexes: indexesSection,
      snapshots
    };
  }

  async close(): Promise<void> {
    if (this.closed) return;
    this.watchers.clear();
    let checkpointFailed = false;
    try {
      this.database.pragma("wal_checkpoint(TRUNCATE)");
    } catch {
      checkpointFailed = true;
    }
    try {
      this.database.close();
      this.closed = true;
    } catch {
      throw new CreationWorkspaceError("integrity", "无法关闭创作工作区。");
    }
    if (checkpointFailed) {
      throw new CreationWorkspaceError("integrity", "创作工作区已关闭，但 WAL 检查点未完成。");
    }
  }

  private assertOpen(): void {
    if (this.closed) throw new CreationWorkspaceError("closed", "创作工作区已关闭。");
  }

  private emitCommitted(event: CreationWorkspaceEvent): void {
    for (const { scope, listener } of this.watchers.values()) {
      if (scope.projectId !== undefined && scope.projectId !== event.projectId) continue;
      try {
        listener(event);
      } catch {
        // A subscriber cannot roll back or break an already committed transaction.
      }
    }
  }
}

export async function openCreationWorkspace(options: OpenCreationWorkspaceOptions): Promise<CreationWorkspace> {
  if (!options || typeof options.directory !== "string" || !options.directory.trim()) {
    throw new CreationWorkspaceError("invalid-input", "创作工作区目录不能为空。");
  }
  let database: Database | undefined;
  try {
    await mkdir(options.directory, { recursive: true });
    database = new Database(path.join(options.directory, "workspace.sqlite"));
    database.pragma("journal_mode = WAL");
    database.pragma("foreign_keys = ON");
    database.pragma("synchronous = FULL");
    const existingVersion = Number(database.pragma("user_version", { simple: true }));
    if (existingVersion === 0) initializeSchema(database);
    else if (existingVersion === 1) {
      migrateSchemaV1ToV2(database);
      migrateSchemaV2ToV3(database);
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
    } else if (existingVersion === 2) {
      migrateSchemaV2ToV3(database);
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
    } else if (existingVersion === 3) {
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
    } else if (existingVersion === 4) {
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
    } else if (existingVersion === 5) migrateSchemaV5ToV6(database);
    else if (existingVersion !== SCHEMA_VERSION) {
      throw new CreationWorkspaceError("integrity", `不支持的创作工作区 schema 版本：${existingVersion}。`);
    }
    try {
      purgeExpiredTrash(database);
    } catch {
      // 到期清理失败不应阻止工作区打开
    }
    return new SqliteCreationWorkspace(database);
  } catch (error) {
    try {
      database?.close();
    } catch {
      // Preserve the stable workspace error instead of exposing a close failure.
    }
    if (error instanceof CreationWorkspaceError) throw error;
    throw new CreationWorkspaceError("integrity", "无法打开创作工作区。");
  }
}

export * from "./types";
