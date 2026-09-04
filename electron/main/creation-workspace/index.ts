import { mkdir } from "node:fs/promises";
import { mkdirSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import path from "node:path";
import { randomUUID } from "node:crypto";
import Database from "better-sqlite3";
import {
  CreationWorkspaceError,
  type ChapterNumberingKind,
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
  type CreateProjectCommand,
  type CreateProjectResult,
  type StructureCommand,
  type StructurePreviewCommand,
  type StructureApplyWithProtectionCommand,
  type StructureRevertCommand,
  type StructurePreviewView,
  type StructureApplyResult,
  type StructureRevertResult,
  type UpdateSceneBodyCommand,
  type UpdateSceneBodyResult,
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
   type CardTypeUpdateCommand,
   type CardTypeDeleteCommand,
  type CardTypesListQuery,
  type CardUpdateCommand,
  type CardsListQuery,
  type RelationType,
   type RelationTypeCreateCommand,
   type RelationTypeUpdateCommand,
   type RelationTypeDeleteCommand,
  type RelationTypesListQuery,
  type HistoryCommand,
  type SnapshotCreateCommand,
  type SnapshotInfo,
  type SnapshotListQuery,
   type SnapshotPreviewQuery,
   type SnapshotPreviewView,
   type SnapshotDiffRow,
   type SnapshotRestoreWithProtectionCommand,
   type SnapshotRestoreWithProtectionResult,
  type SnapshotSubjectType,
  type TrashEntityKind,
  type TrashItem,
  type TrashListQuery,
  type TrashPurgeCommand,
   type TrashRestoreCommand,
   type TrashImpactQuery,
   type TrashImpactView,
  type ProjectExportChapter,
  type ProjectExportBlock,
  type ProjectExportQuery,
  type ProjectExportScene,
  type ProjectExportView,
  type ProjectExportVolume,
  type CreationSearchQuery,
  type CreationSearchView,
  type ReplaceApplyCommand,
  type ReplaceApplyResult,
  type ReplacePreviewQuery,
  type ReplacePreviewView,
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
  type InboxCountQuery,
  type InboxCountView,
  type InboxReadQuery,
  type InboxUpdateCommand,
  type ProjectImportDraftCommand,
  type ProjectImportDraftResult,
  type ProjectBundleData,
  type ProjectBundleExportQuery,
  type ProjectBundleImportCommand,
  type ProjectBundleImportResult,
  type Annotation,
  type AnnotationAnchor,
  type AnnotationCreateCommand,
  type AnnotationDeleteCommand,
  type AnnotationListQuery,
  type AnnotationResult,
   type AnnotationUpdateCommand,
   type AnnotationReanchorCommand,
  type ResourceAttachCommand,
  type ResourceDetachCommand,
  type ResourceInfo,
  type ResourceListQuery,
   type ResourceResult,
   type ScenePlanning,
   type SceneUpdatePlanningCommand,
   type SceneUpdatePlanningResult,
   type CreationRunCommand,
   type CreationRunResultOf,
   type InboxConvertToCardCommand,
   type InboxConvertToCardResult,
   type ProjectHomeEntry,
   type ProjectHomeQuery,
   type ProjectHomeView,
   type CreationWatchScope,
  type CreationWorkspace,
  type CreationWorkspaceEvent,
  type CreationWorkspaceListener,
  type OpenCreationWorkspaceOptions
} from "./types";
import { countSceneBodyStats } from "./scene-stats";
import { createStatsSessionsModule, type StatsSessionsModule } from "./stats-sessions";
import { createSearchModule, type SearchModule } from "./search";
import { createInboxModule, type InboxModule } from "./inbox";
import { createResourceModule, type ResourceModule } from "./resource";
import { createAnnotationModule, type AnnotationModule } from "./annotation";
import { createReplaceModule, type ReplaceModule } from "./replace";
import { createStructureModule, type StructureModule, STRUCTURE_COMMAND_TYPES } from "./structure";
import {
  extractSceneText,
  isConstraintError,
  isRecord,
  validateBaseRevision,
  validateId,
  validateTitle
} from "./workspace-utils";
import { classifySnapshotMeta, planSnapshotRetention } from "./snapshot-retention";
import { applyReplacePlan as runApplyReplacePlan, createReplacePlan as runCreateReplacePlan } from "./replace-plan";
import {
  applyCardImportPlan,
  generateCardImportPlanId,
  planCardImport,
  readCardImportSchemaContext,
  readCardsForExport
} from "../creation-card-io/card-io-index";
import type {
  CardExportFilter,
  CardExportRow,
  CardImportApplyInput,
  CardImportApplyResult,
  CardImportPlan,
  CardImportSchemaContext
} from "../../../src/types/card-io";
import type {
  ProjectGoalResult,
  ProjectUpdateGoalCommand,
  SessionUpdateCommand,
  SnapshotRetentionResult
} from "../../../src/types/creation";
import type {
  ReplaceApplyOutcome,
  ReplacePlan,
  ReplacePlanController,
  ReplacePlanQuery,
  ReplacePlanStore
} from "./replace-plan";

const SCHEMA_VERSION = 9;

const TARGET_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const SCENE_TEXT_BLOCKS = new Set(["paragraph", "quoteLetter", "centeredText", "authorNote"]);
const SCENE_MARKS = new Set(["bold", "italic"]);
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

const CARD_COMMAND_TYPES = new Set<string>([
  "cardType.create",
  "cardType.update",
  "cardType.delete",
  "relationType.create",
  "relationType.update",
  "relationType.delete",
  "card.create",
  "card.update",
  "card.delete",
  "cardRelation.create",
  "cardRelation.delete"
]);

const HISTORY_COMMAND_TYPES = new Set<string>([
  "trash.restore",
  "trash.purge",
  "snapshot.create"
]);

const TRASH_ENTITY_KINDS = new Set<TrashEntityKind>(["volume", "chapter", "scene", "card"]);

const PROOF_RULES = new Set<ProofRule>([
  "repeatedChar",
  "unbalancedPunctuation",
  "abnormalSpacing",
  "longParagraph",
  "bannedWord",
  "mixedPunctuation",
  "crutchWord",
  "paragraphStartRepeat"
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
  "annotations",
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
  "idx_inbox_items_updated",
  "idx_annotations_scene",
  "idx_annotations_project",
  "idx_resources_card"
] as const;

function createSection(issues: Array<{ code: string; message: string }>) {
  return { ok: issues.length === 0, issues };
}

/** 项目包/附件相对路径安全检查：仅接受 POSIX 风格相对路径，禁止绝对路径、穿越、盘符与反斜杠。 */
function isSafeBundleRelativePath(value: unknown): value is string {
  if (typeof value !== "string" || !value.trim()) return false;
  if (value.length > 500) return false;
  if (path.isAbsolute(value)) return false;
  if (value.includes("..") || value.includes("\\") || value.includes(":")) return false;
  if (value.includes("\0")) return false;
  return true;
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

/**
 * 场景正文三口径统计的权威实现在 ./scene-stats（workspace 与视觉 seed 共用，避免口径漂移）。
 * 这里 re-export 保持历史导入（scale-contract 等）兼容。
 */
export { countSceneBodyStats } from "./scene-stats";
export {
  scanResourceConsistencyCore,
  readResourceRecords,
  type ResourceIssue,
  type ResourceIssueType,
  type ResourceRecord,
  type ResourceScanResult,
  type ScanResourceOptions
} from "./resource-scan";

/** 场景正文最小块视图（kind + 文本，不携带 marks/完整结构），供审阅稿导出使用。 */
function extractSceneBlocks(bodyJson: string): ProjectExportBlock[] {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return [];
  }
  const result: ProjectExportBlock[] = [];
  for (const block of document.content ?? []) {
    if (!isRecord(block)) continue;
    if (block.type === "sceneBreak") {
      result.push({ kind: "sceneBreak", text: "" });
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
    result.push({ kind: typeof block.type === "string" ? block.type : "paragraph", text: parts.join("") });
  }
  return result;
}

/** 收件箱/卡片的 JSON 数组字段（tags 等）安全解析。 */

/**
 * 场景任务卡字段解析（planning_json），非法结构返回空对象。
 * 保留明确的 null（表示已清空）与空数组（出场为空），以便清空后重新读取仍为空。
 */
function parseScenePlanning(json: string): ScenePlanning | undefined {
  try {
    const parsed = JSON.parse(json) as unknown;
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return undefined;
    const planning = parsed as Record<string, unknown>;
    const result: ScenePlanning = {};
    if (planning.perspectiveCardId === null) result.perspectiveCardId = null;
    else if (typeof planning.perspectiveCardId === "string" && planning.perspectiveCardId) result.perspectiveCardId = planning.perspectiveCardId;
    if (planning.time === null) result.time = null;
    else if (typeof planning.time === "string" && planning.time.trim()) result.time = planning.time.trim();
    if (planning.locationCardId === null) result.locationCardId = null;
    else if (typeof planning.locationCardId === "string" && planning.locationCardId) result.locationCardId = planning.locationCardId;
    if (planning.castCardIds === null) result.castCardIds = null;
    else if (Array.isArray(planning.castCardIds)) {
      result.castCardIds = planning.castCardIds.filter((item): item is string => typeof item === "string" && item.length > 0);
    }
    if (planning.goal === null) result.goal = null;
    else if (typeof planning.goal === "string" && planning.goal.trim()) result.goal = planning.goal.trim();
    if (planning.conflict === null) result.conflict = null;
    else if (typeof planning.conflict === "string" && planning.conflict.trim()) result.conflict = planning.conflict.trim();
    if (planning.outcome === null) result.outcome = null;
    else if (typeof planning.outcome === "string" && planning.outcome.trim()) result.outcome = planning.outcome.trim();
    if (planning.emotion === null) result.emotion = null;
    else if (typeof planning.emotion === "string" && planning.emotion.trim()) result.emotion = planning.emotion.trim();
    if (planning.targetWords === null) result.targetWords = null;
    else if (typeof planning.targetWords === "number" && Number.isInteger(planning.targetWords) && planning.targetWords > 0) {
      result.targetWords = planning.targetWords;
    }
    return Object.keys(result).length > 0 ? result : undefined;
  } catch {
    return undefined;
  }
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

/** 中英混用标点：汉字紧邻半角标点（网页粘贴/输入法残留的高频问题）。 */
function findMixedPunctuation(text: string, out: Array<{ rule: ProofRule; message: string; snippet: string | null }>): void {
  // 数字间的半角点（3.5、1,000）不算；只抓汉字直接贴半角标点。
  const mixed = /[\p{Script=Han}][,.!?;:]|[,.!?;:][\p{Script=Han}]/gu;
  let count = 0;
  let firstSnippet: string | null = null;
  let match: RegExpExecArray | null;
  while ((match = mixed.exec(text)) !== null) {
    count += 1;
    if (!firstSnippet) {
      const radius = 8;
      const start = Math.max(0, match.index - radius);
      const end = Math.min(text.length, match.index + match[0].length + radius * 2);
      firstSnippet = `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`;
    }
  }
  if (count > 0) {
    out.push({
      rule: "mixedPunctuation",
      message: `${count} 处汉字紧邻半角标点（,.!?;:），疑似中英标点混用`,
      snippet: firstSnippet
    });
  }
}

/** 口头禅：叙述类高频副词在单个场景内出现过多（每词 ≥3 次才提示，避免噪声）。 */
const PROOF_CRUTCH_WORDS = ["突然", "顿时", "瞬间", "竟然", "居然", "仿佛", "似乎", "显然", "几乎", "一阵"];
function findCrutchWords(text: string, out: Array<{ rule: ProofRule; message: string; snippet: string | null }>): void {
  for (const word of PROOF_CRUTCH_WORDS) {
    let count = 0;
    let index = text.indexOf(word);
    while (index >= 0) {
      count += 1;
      index = text.indexOf(word, index + word.length);
    }
    if (count >= 3) {
      out.push({
        rule: "crutchWord",
        message: `「${word}」出现 ${count} 次，注意口头禅化`,
        snippet: null
      });
    }
  }
}

/** 连续段落同字开头：≥3 个连续非空段落首字相同（刻意排比可忽略）。 */
function findParagraphStartRepeat(paragraphs: string[], out: Array<{ rule: ProofRule; message: string; snippet: string | null }>): void {
  const meaningful = paragraphs
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0);
  let runStart = 0;
  let count = 0;
  let snippet: string | null = null;
  for (let index = 1; index <= meaningful.length; index += 1) {
    const sameHead =
      index < meaningful.length &&
      meaningful[index]![0] === meaningful[runStart]![0];
    if (sameHead) continue;
    const runLength = index - runStart;
    if (runLength >= 3) {
      count += 1;
      if (!snippet) {
        snippet = meaningful.slice(runStart, runStart + 2).map((paragraph) => paragraph.slice(0, 16)).join(" / ");
      }
    }
    runStart = index;
  }
  if (count > 0) {
    out.push({
      rule: "paragraphStartRepeat",
      message: `${count} 处连续段落以同一字开头（如为刻意排比可忽略）`,
      snippet
    });
  }
}

/** 纯文本 → 场景 doc：空行分段，无空行时按行分段。 */
function plainTextToSceneDocument(text: string): CreationDocument {
  const cleaned = text.replace(/\r\n/g, "\n").trim();
  if (!cleaned) return { type: "doc", content: [] };
  const blocks = cleaned.split(/\n{2,}/);
  const content: CreationDocument["content"] = [];
  for (const block of blocks) {
    const lines = block.split("\n");
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      content.push({
        type: "paragraph",
        content: [{ type: "text", text: trimmed }]
      });
    }
  }
  return { type: "doc", content };
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
      han_count INTEGER NOT NULL DEFAULT 0,
      punct_count INTEGER NOT NULL DEFAULT 0,
      non_ws_count INTEGER NOT NULL DEFAULT 0,
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
      card_id TEXT REFERENCES cards(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL,
      sha256 TEXT NOT NULL,
      size INTEGER NOT NULL DEFAULT 0,
      original_name TEXT,
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);
    CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id);

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

    CREATE TABLE IF NOT EXISTS annotations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      card_id TEXT REFERENCES cards(id) ON DELETE SET NULL,
      anchor_json TEXT NOT NULL,
      note TEXT,
      status TEXT NOT NULL DEFAULT 'open',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_annotations_scene ON annotations(scene_id);
    CREATE INDEX IF NOT EXISTS idx_annotations_project ON annotations(project_id);

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
  const builtinTypes: Array<[string, string, string]> = [
    ["character", "角色", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["location", "地点", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["organization", "组织", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["item", "物品", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["worldRule", "世界规则", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    ["plotEvent", "情节事件", '[{"key":"note","label":"备注","kind":"multiline"}]'],
    [
      "foreshadow",
      "伏笔线索",
      // D-C3 伏笔生命周期 v1：status 由「未标记 = 未回收」兜底，无需数据迁移。
      JSON.stringify([
        { key: "note", label: "伏笔内容", kind: "multiline" },
        { key: "status", label: "状态", kind: "select", options: ["未回收", "已回收"], defaultValue: "未回收" },
        { key: "plantedIn", label: "埋设位置", kind: "text" },
        { key: "resolution", label: "回收说明", kind: "multiline" }
      ])
    ],
    ["reference", "资料", '[{"key":"note","label":"备注","kind":"multiline"}]']
  ];
  builtinTypes.forEach(([kind, name, fieldsJson], index) => {
    insertType.run(
      `card-type-${kind}`,
      null,
      kind,
      name,
      fieldsJson,
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

function migrateSchemaV6ToV7(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    CREATE TABLE IF NOT EXISTS annotations (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
      scene_id TEXT NOT NULL REFERENCES scenes(id) ON DELETE CASCADE,
      card_id TEXT REFERENCES cards(id) ON DELETE SET NULL,
      anchor_json TEXT NOT NULL,
      note TEXT,
      status TEXT NOT NULL DEFAULT 'open',
      revision INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      deleted_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_annotations_scene ON annotations(scene_id);
    CREATE INDEX IF NOT EXISTS idx_annotations_project ON annotations(project_id);
    PRAGMA user_version = 7;
    COMMIT;
  `);
}

function migrateSchemaV7ToV8(database: Database): void {
  database.exec("BEGIN IMMEDIATE");
  const tableExists = database
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'resources'")
    .get();
  if (!tableExists) {
    database.exec(`
      CREATE TABLE resources (
        id TEXT PRIMARY KEY,
        project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
        card_id TEXT REFERENCES cards(id) ON DELETE CASCADE,
        relative_path TEXT NOT NULL,
        sha256 TEXT NOT NULL,
        size INTEGER NOT NULL DEFAULT 0,
        original_name TEXT,
        created_at TEXT NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_resources_project ON resources(project_id);
      CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id);
    `);
  } else {
    const columns = new Set(
      (database.prepare("PRAGMA table_info(resources)").all() as Array<{ name: string }>).map((column) => column.name)
    );
    if (!columns.has("card_id")) {
      database.exec("ALTER TABLE resources ADD COLUMN card_id TEXT REFERENCES cards(id) ON DELETE CASCADE");
    }
    if (!columns.has("size")) {
      database.exec("ALTER TABLE resources ADD COLUMN size INTEGER NOT NULL DEFAULT 0");
    }
    if (!columns.has("original_name")) {
      database.exec("ALTER TABLE resources ADD COLUMN original_name TEXT");
    }
    database.exec("CREATE INDEX IF NOT EXISTS idx_resources_card ON resources(card_id)");
  }
  database.exec("PRAGMA user_version = 8; COMMIT;");
}

/** v8→v9：scenes 增加持久化计数列 han_count/punct_count/non_ws_count，并对既有正文一次性回填。 */
function migrateSchemaV8ToV9(database: Database): void {
  database.exec("BEGIN IMMEDIATE");
  const tableExists = database
    .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'scenes'")
    .get();
  if (!tableExists) {
    // 残缺库（缺 scenes 表）：不补列，由 check() 的 schema-table-missing 暴露；只升版本。
    database.exec("PRAGMA user_version = 9; COMMIT;");
    return;
  }
  const columns = new Set(
    (database.prepare("PRAGMA table_info(scenes)").all() as Array<{ name: string }>).map((column) => column.name)
  );
  if (!columns.has("han_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN han_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columns.has("punct_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN punct_count INTEGER NOT NULL DEFAULT 0");
  }
  if (!columns.has("non_ws_count")) {
    database.exec("ALTER TABLE scenes ADD COLUMN non_ws_count INTEGER NOT NULL DEFAULT 0");
  }
  const rows = database
    .prepare("SELECT id, body_json FROM scenes WHERE han_count = 0 AND punct_count = 0 AND non_ws_count = 0")
    .all() as Array<{ id: string; body_json: string }>;
  const update = database.prepare("UPDATE scenes SET han_count = ?, punct_count = ?, non_ws_count = ? WHERE id = ?");
  for (const row of rows) {
    const stats = countSceneBodyStats(row.body_json);
    update.run(stats.han, stats.punct, stats.nonWhitespace, row.id);
  }
  database.exec("PRAGMA user_version = 9; COMMIT;");
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
  private readonly statsSessions: StatsSessionsModule;
  private readonly search: SearchModule;
  private readonly inbox: InboxModule;
  private readonly resource: ResourceModule;
  private readonly annotation: AnnotationModule;
  private readonly replace: ReplaceModule;
  private readonly structure: StructureModule;

  constructor(
    private readonly database: Database,
    private readonly workspaceDirectory: string
  ) {
    // 统计与会话域独立模块（纯移动式切片，逻辑与旧内联实现一致）。
    this.statsSessions = createStatsSessionsModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp)
    });
    // 搜索域独立模块。
    this.search = createSearchModule(database);
    // 收件箱域独立模块。
    this.inbox = createInboxModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 资源域独立模块。
    this.resource = createResourceModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 批注域独立模块。
    this.annotation = createAnnotationModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 查找替换域独立模块。
    this.replace = createReplaceModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      requireVolume: (volumeId) => this.requireVolume(volumeId),
      requireChapter: (chapterId) => this.requireChapter(chapterId),
      requireScene: (sceneId) => this.requireScene(sceneId),
      assertSameProject: (projectId, otherProjectId, label) => this.assertSameProject(projectId, otherProjectId, label),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event)
    });
    // 大纲与结构域独立模块。
    this.structure = createStructureModule(database, {
      requireProject: (projectId) => this.requireProject(projectId),
      requireVolume: (volumeId) => this.requireVolume(volumeId),
      requireChapter: (chapterId) => this.requireChapter(chapterId),
      requireScene: (sceneId) => this.requireScene(sceneId),
      assertSameProject: (projectId, otherProjectId, label) => this.assertSameProject(projectId, otherProjectId, label),
      touchProject: (projectId, timestamp) => this.touchProject(projectId, timestamp),
      emitCommitted: (event) => this.emitCommitted(event),
      readProjectOutline: (projectId) => this.readProjectOutline(projectId),
      readWorkflow: (projectId) => this.readWorkflow(projectId),
      projectRevision: (projectId) => this.projectRevision(projectId)
    });
  }

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
  async read(query: SnapshotPreviewQuery): Promise<SnapshotPreviewView | null>;
  async read(query: TrashImpactQuery): Promise<TrashImpactView | null>;
  async read(query: ProjectExportQuery): Promise<ProjectExportView | null>;
  async read(query: CreationSearchQuery): Promise<CreationSearchView>;
  async read(query: ReplacePreviewQuery): Promise<ReplacePreviewView>;
  async read(query: StatsViewQuery): Promise<ProjectStatsView | null>;
  async read(query: SessionListQuery): Promise<SessionEntry[]>;
  async read(query: ProofQuery): Promise<ProofView>;
  async read(query: InboxListQuery): Promise<InboxItem[]>;
  async read(query: InboxReadQuery): Promise<InboxItem | null>;
  async read(query: InboxCountQuery): Promise<InboxCountView>;
  async read(query: ProjectBundleExportQuery): Promise<ProjectBundleData | null>;
  async read(query: AnnotationListQuery): Promise<Annotation[]>;
  async read(query: ResourceListQuery): Promise<ResourceInfo[]>;
  async read(query: ProjectHomeQuery): Promise<ProjectHomeView>;
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
      snapshotId?: unknown;
      entity?: unknown;
      entityId?: unknown;
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
    if (runtimeQuery.kind === "project.home") {
      try {
        return this.readProjectHome();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取项目首页聚合视图。");
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
    if (runtimeQuery.kind === "resource.list") {
      try {
        return this.runResourceList(query as ResourceListQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取附件列表。");
      }
    }
    if (runtimeQuery.kind === "annotation.list") {
      try {
        return this.runAnnotationList(query as AnnotationListQuery);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取批注。");
      }
    }
    if (runtimeQuery.kind === "snapshot.preview") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "快照预览请求无效。");
      }
      if (typeof runtimeQuery.snapshotId !== "string" || !runtimeQuery.snapshotId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "快照预览请求无效。");
      }
      try {
        return this.readSnapshotPreview(runtimeQuery.projectId, runtimeQuery.snapshotId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法预览快照差异。");
      }
    }
    if (runtimeQuery.kind === "trash.impact") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      const entity = runtimeQuery.entity;
      if (entity !== "volume" && entity !== "chapter" && entity !== "scene" && entity !== "card") {
        throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
      }
      if (typeof runtimeQuery.entityId !== "string" || !runtimeQuery.entityId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      try {
        return this.readTrashImpact(runtimeQuery.projectId, entity, runtimeQuery.entityId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取回收站影响。");
      }
    }
    if (runtimeQuery.kind === "project.bundle.export") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "项目包读取请求无效。");
      }
      try {
        return this.runProjectBundleExport(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取项目包数据。");
      }
    }
    if (runtimeQuery.kind === "inbox.list") {
      const limit = (runtimeQuery as unknown as { limit?: unknown }).limit;
      const offset = (runtimeQuery as unknown as { offset?: unknown }).offset;
      const resolvedLimit = limit === undefined ? 100 : limit;
      if (!Number.isInteger(resolvedLimit) || (resolvedLimit as number) < 1 || (resolvedLimit as number) > 500) {
        throw new CreationWorkspaceError("invalid-input", "收件箱返回上限必须为 1..500 的整数。");
      }
      const resolvedOffset = offset === undefined ? 0 : offset;
      if (!Number.isInteger(resolvedOffset) || (resolvedOffset as number) < 0) {
        throw new CreationWorkspaceError("invalid-input", "收件箱偏移量必须为非负整数。");
      }
      try {
        return this.runInboxList(resolvedLimit as number, resolvedOffset as number);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取收件箱。");
      }
    }
    if (runtimeQuery.kind === "inbox.count") {
      try {
        return this.runInboxCount();
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法统计收件箱。");
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
        return row ? this.inbox.inboxFromRow(row) : null;
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
        return this.readProjectExport(runtimeQuery.projectId, (runtimeQuery as { includeBlocks?: boolean }).includeBlocks === true);
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
        `SELECT s.id, s.chapter_id, s.title, s.sort_order, s.non_ws_count, s.planning_json, s.created_at, s.updated_at, s.revision
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
          wordCount: scene.non_ws_count,
          planning: parseScenePlanning(scene.planning_json),
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
    return this.search.runSearch(query);
  }

  private runReplacePreview(query: ReplacePreviewQuery): ReplacePreviewView {
    return this.replace.runReplacePreview(query);
  }

  private replaceApply(command: ReplaceApplyCommand): ReplaceApplyResult {
    return this.replace.replaceApply(command);
  }

  private sessionReport(command: SessionReportCommand): SessionReportResult {
    return this.statsSessions.sessionReport(command);
  }

  private sessionDelete(command: SessionDeleteCommand): SessionReportResult {
    return this.statsSessions.sessionDelete(command);
  }

  private runSessionList(projectId: string, limit: number): SessionEntry[] {
    return this.statsSessions.runSessionList(projectId, limit);
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
      if (rules.includes("mixedPunctuation")) findMixedPunctuation(plain, found);
      if (rules.includes("crutchWord")) findCrutchWords(plain, found);
      if (rules.includes("paragraphStartRepeat")) findParagraphStartRepeat(paragraphs, found);
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

  private runInboxList(limit: number, offset: number): InboxItem[] {
    return this.inbox.runInboxList(limit, offset);
  }

  private runInboxCount(): InboxCountView {
    return this.inbox.runInboxCount();
  }

  private runInboxCreate(command: InboxCreateCommand): InboxItemResult {
    return this.inbox.runInboxCreate(command);
  }

  private runInboxUpdate(command: InboxUpdateCommand): InboxItemResult {
    return this.inbox.runInboxUpdate(command);
  }

  private runInboxDelete(command: InboxDeleteCommand): InboxItemResult {
    return this.inbox.runInboxDelete(command);
  }

  private runInboxConvertToCard(command: InboxConvertToCardCommand): InboxConvertToCardResult {
    return this.inbox.runInboxConvertToCard(command);
  }

  private runAnnotationList(query: AnnotationListQuery): Annotation[] {
    return this.annotation.runAnnotationList(query);
  }

  private runAnnotationCreate(command: AnnotationCreateCommand): AnnotationResult {
    return this.annotation.runAnnotationCreate(command);
  }

  private runAnnotationUpdate(command: AnnotationUpdateCommand): AnnotationResult {
    return this.annotation.runAnnotationUpdate(command);
  }

  private runAnnotationDelete(command: AnnotationDeleteCommand): AnnotationResult {
    return this.annotation.runAnnotationDelete(command);
  }

  private runAnnotationReanchor(command: AnnotationReanchorCommand): AnnotationResult {
    return this.annotation.runAnnotationReanchor(command);
  }

  private runResourceList(query: ResourceListQuery): ResourceInfo[] {
    return this.resource.runResourceList(query);
  }

  private runResourceAttach(command: ResourceAttachCommand): ResourceResult {
    return this.resource.runResourceAttach(command);
  }

  private runResourceDetach(command: ResourceDetachCommand): ResourceResult {
    return this.resource.runResourceDetach(command);
  }

  private runSceneUpdatePlanning(command: SceneUpdatePlanningCommand): SceneUpdatePlanningResult {
    const sceneId = validateId(command.sceneId, "场景");
    const planning = command.planning as unknown as ScenePlanning | null;
    if (!planning || typeof planning !== "object") {
      throw new CreationWorkspaceError("invalid-input", "场景规划字段无效。");
    }
    // 语义：undefined = 保留旧值；null = 明确清空；字符串/数组/数字 = 写入新值。
    const cleaned: ScenePlanning = {};
    if (planning.perspectiveCardId === null) cleaned.perspectiveCardId = null;
    else if (planning.perspectiveCardId !== undefined) cleaned.perspectiveCardId = validateId(planning.perspectiveCardId, "视角卡片");
    if (planning.time === null) cleaned.time = null;
    else if (planning.time !== undefined) {
      if (typeof planning.time !== "string" || planning.time.length > 200) {
        throw new CreationWorkspaceError("invalid-input", "时间描述不能超过 200 个字符。");
      }
      const time = planning.time.trim();
      cleaned.time = time || null;
    }
    if (planning.locationCardId === null) cleaned.locationCardId = null;
    else if (planning.locationCardId !== undefined) cleaned.locationCardId = validateId(planning.locationCardId, "地点卡片");
    if (planning.castCardIds === null) cleaned.castCardIds = null;
    else if (planning.castCardIds !== undefined) {
      if (!Array.isArray(planning.castCardIds) || planning.castCardIds.length > 100) {
        throw new CreationWorkspaceError("invalid-input", "出场卡片数量超出允许范围。");
      }
      cleaned.castCardIds = planning.castCardIds.map((id) => validateId(id, "出场卡片"));
    }
    for (const key of ["goal", "conflict", "outcome", "emotion"] as const) {
      const value = planning[key];
      if (value === null) cleaned[key] = null;
      else if (value !== undefined) {
        if (typeof value !== "string" || value.length > 2000) {
          throw new CreationWorkspaceError("invalid-input", "场景规划文本不能超过 2000 个字符。");
        }
        const trimmed = value.trim();
        cleaned[key] = trimmed || null;
      }
    }
    if (planning.targetWords === null) cleaned.targetWords = null;
    else if (planning.targetWords !== undefined) {
      if (!Number.isInteger(planning.targetWords) || planning.targetWords < 1 || planning.targetWords > 1_000_000) {
        throw new CreationWorkspaceError("invalid-input", "目标字数必须为 1 至 1000000 的整数。");
      }
      cleaned.targetWords = planning.targetWords;
    }
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const scene = this.database
        .prepare("SELECT c.project_id, c.revision, s.planning_json FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ? AND s.deleted_at IS NULL")
        .get(sceneId) as { project_id: string; revision: number; planning_json: string } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
      const projectId = scene.project_id;
      // 合并语义：未提供的字段保留现有值；null 覆盖为清空。
      const merged: ScenePlanning = { ...parseScenePlanning(scene.planning_json), ...cleaned };
      // 校验卡片引用都属于同一项目（null / 空出场跳过）。
      const cardIds = new Set<string>();
      if (typeof merged.perspectiveCardId === "string") cardIds.add(merged.perspectiveCardId);
      if (typeof merged.locationCardId === "string") cardIds.add(merged.locationCardId);
      if (Array.isArray(merged.castCardIds)) {
        for (const id of merged.castCardIds) cardIds.add(id);
      }
      if (cardIds.size > 0) {
        const placeholders = [...cardIds].map(() => "?").join(", ");
        const rows = this.database
          .prepare(`SELECT id, project_id FROM cards WHERE id IN (${placeholders}) AND deleted_at IS NULL`)
          .all(...cardIds) as Array<{ id: string; project_id: string }>;
        if (rows.length !== cardIds.size) throw new CreationWorkspaceError("not-found", "引用的卡片不存在。");
        for (const row of rows) {
          if (row.project_id !== projectId) throw new CreationWorkspaceError("invalid-input", "引用的卡片不属于该作品。");
        }
      }
      this.database
        .prepare("UPDATE scenes SET planning_json = ?, updated_at = ? WHERE id = ?")
        .run(JSON.stringify(merged), timestamp, sceneId);
      const logged = this.database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "scene.updatePlanning", JSON.stringify([{ entity: "scene", id: sceneId, action: "updated", revision: scene.revision }]), timestamp);
      this.database.exec("COMMIT");
      this.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        commandType: "scene.updatePlanning",
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision: scene.revision }]
      });
      return { commandType: "scene.updatePlanning", sequence: Number(logged.lastInsertRowid), projectId, sceneId, updatedAt: timestamp };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // The transaction may already have been rolled back by SQLite.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新场景规划。");
    }
  }

  private runStatsView(projectId: string): ProjectStatsView | null {
    return this.statsSessions.runStatsView(projectId);
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

  private readProjectExport(projectId: string, includeBlocks = false): ProjectExportView | null {
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
      const entry: ProjectExportScene = {
        id: scene.id,
        title: scene.title,
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
  private readProjectHome(): ProjectHomeView {
    const rows = this.database
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

  async transact(command: CreateProjectCommand): Promise<CreateProjectResult>;
  async transact(command: UpdateSceneBodyCommand): Promise<UpdateSceneBodyResult>;
  async transact(command: StructureCommand): Promise<CreationStructureResult>;
  async transact(command: CardCommand): Promise<CreationStructureResult>;
  async transact(command: HistoryCommand): Promise<CreationStructureResult>;
  async transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  async transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  async transact(command: InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand): Promise<InboxItemResult>;
  async transact(command: ProjectImportDraftCommand): Promise<ProjectImportDraftResult>;
  async transact(command: ProjectBundleImportCommand): Promise<ProjectBundleImportResult>;
  async transact(command: AnnotationCreateCommand | AnnotationUpdateCommand | AnnotationDeleteCommand): Promise<AnnotationResult>;
  async transact(command: ResourceAttachCommand | ResourceDetachCommand): Promise<ResourceResult>;
  async transact(command: SceneUpdatePlanningCommand): Promise<SceneUpdatePlanningResult>;
  async transact<Command extends CreationRunCommand>(command: Command): Promise<CreationRunResultOf<Command>>;
  async transact(command: CreationCommand): Promise<CreationTransactionResult> {
    this.assertOpen();
    if (command.type === "scene.updateBody") {
      return this.updateSceneBody(command);
    }
    if (command.type === "project.create") {
      return this.createProjectTransaction(command);
    }
    if (command.type === "structure.preview") return this.previewStructure(command);
    if (command.type === "structure.applyWithProtection") return this.applyStructure(command);
    if (command.type === "structure.revert") return this.revertStructure(command);
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
    if (command.type === "project.importDraft") {
      return this.importDraftTransaction(command as ProjectImportDraftCommand);
    }
    if (command.type === "project.bundle.import") {
      return this.importProjectBundle(command as ProjectBundleImportCommand);
    }
    if (command.type === "annotation.create") {
      return this.runAnnotationCreate(command as AnnotationCreateCommand);
    }
    if (command.type === "annotation.update") {
      return this.runAnnotationUpdate(command as AnnotationUpdateCommand);
    }
    if (command.type === "annotation.delete") {
      return this.runAnnotationDelete(command as AnnotationDeleteCommand);
    }
    if (command.type === "annotation.reanchor") {
      return this.runAnnotationReanchor(command as AnnotationReanchorCommand);
    }
    if (command.type === "resource.attach") {
      return this.runResourceAttach(command as ResourceAttachCommand);
    }
    if (command.type === "resource.detach") {
      return this.runResourceDetach(command as ResourceDetachCommand);
    }
    if (command.type === "scene.updatePlanning") {
      return this.runSceneUpdatePlanning(command as SceneUpdatePlanningCommand);
    }
    if (command.type === "inbox.convertToCard") {
      return this.runInboxConvertToCard(command as InboxConvertToCardCommand);
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

  private importDraftTransaction(command: ProjectImportDraftCommand): ProjectImportDraftResult {
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
      this.database.exec("BEGIN IMMEDIATE");
      this.database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")
        .run(projectId, title, JSON.stringify(setup), timestamp, timestamp);
      const insertVolume = this.database.prepare(
        "INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)"
      );
      const insertChapter = this.database.prepare(
        "INSERT INTO chapters(id, project_id, volume_id, title, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
      );
      const insertScene = this.database.prepare(
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
      const logged = this.database
        .prepare(
          "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
        )
        .run(projectId, command.type, JSON.stringify(changes), timestamp);
      this.database.exec("COMMIT");
      const result: ProjectImportDraftResult = {
        commandType: "project.importDraft",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        volumeCount,
        chapterCount,
        sceneCount: totalScenes
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "project.importDraft",
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
      if (typeof error === "object" && error !== null && "code" in error && String(error.code).startsWith("SQLITE_CONSTRAINT")) {
        throw new CreationWorkspaceError("conflict", "无法创建作品，稳定标识发生冲突。");
      }
      throw new CreationWorkspaceError("integrity", "无法提交旧稿导入事务。");
    }
  }

  private runProjectBundleExport(projectId: string): ProjectBundleData | null {
    const project = this.database
      .prepare("SELECT id, title, setup_json, created_at, updated_at, revision FROM projects WHERE id = ?")
      .get(projectId) as
      | { id: string; title: string; setup_json: string; created_at: string; updated_at: string; revision: number }
      | undefined;
    if (!project) return null;
    const volumes = this.database
      .prepare("SELECT id, title, sort_order, created_at, updated_at, revision FROM volumes WHERE project_id = ? AND deleted_at IS NULL ORDER BY sort_order, id")
      .all(projectId) as Array<{ id: string; title: string; sort_order: number; created_at: string; updated_at: string; revision: number }>;
    const chapters = this.database
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
    const scenes = this.database
      .prepare(
        "SELECT s.id, s.chapter_id, s.title, s.sort_order, s.body_json, s.planning_json, s.created_at, s.updated_at, s.revision FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.project_id = ? AND s.deleted_at IS NULL ORDER BY s.sort_order, s.id"
      )
      .all(projectId) as Array<{
      id: string;
      chapter_id: string;
      title: string;
      sort_order: number;
      body_json: string;
      planning_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
    }>;
    const cardTypes = this.database
      .prepare("SELECT id, kind, name, fields_json, sort_order, created_at, updated_at, revision FROM card_types WHERE project_id = ? ORDER BY sort_order, id")
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
    const relationTypes = this.database
      .prepare("SELECT id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision FROM relation_types WHERE project_id = ? ORDER BY id")
      .all(projectId) as Array<{
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
    const cards = this.database
      .prepare("SELECT id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision FROM cards WHERE project_id = ? AND deleted_at IS NULL ORDER BY updated_at, id")
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
    const relations = this.database
      .prepare("SELECT id, from_card_id, to_card_id, relation_type, note, created_at FROM card_relations WHERE project_id = ? ORDER BY id")
      .all(projectId) as Array<{
      id: string;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      created_at: string;
    }>;
    const snapshots = this.database
      .prepare("SELECT id, subject_type, subject_id, payload_json, created_at FROM snapshots WHERE project_id = ? ORDER BY created_at, id")
      .all(projectId) as Array<{
      id: string;
      subject_type: string;
      subject_id: string;
      payload_json: string;
      created_at: string;
    }>;
    const resources = this.database
      .prepare("SELECT id, card_id, relative_path, sha256, size, original_name, created_at FROM resources WHERE project_id = ? ORDER BY relative_path, id")
      .all(projectId) as Array<{
      id: string;
      card_id: string | null;
      relative_path: string;
      sha256: string;
      size: number;
      original_name: string | null;
      created_at: string;
    }>;
    const annotations = this.database
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

  private importProjectBundle(command: ProjectBundleImportCommand): ProjectBundleImportResult {
    const runtimeCommand = command as unknown as {
      data?: unknown;
      targetProjectId?: unknown;
      resourceFiles?: unknown;
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
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const existing = this.database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId);
      if (existing) throw new CreationWorkspaceError("conflict", `项目 ${projectId} 已存在，无法导入。`);
      this.database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?)")
        .run(projectId, title, JSON.stringify(data.project.setup ?? defaultProjectSetup()), data.project.createdAt ?? timestamp, data.project.updatedAt ?? timestamp, data.project.revision ?? 1);

      const insertVolume = this.database.prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?)");
      const volumeIds = new Set<string>();
      for (const volume of data.volumes ?? []) {
        const id = typeof volume.id === "string" && volume.id.startsWith("volume-") ? volume.id : `volume-${randomUUID()}`;
        if (volumeIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复卷 ID。");
        volumeIds.add(id);
        insertVolume.run(id, projectId, volume.title, volume.sortOrder ?? 0, volume.createdAt ?? timestamp, volume.updatedAt ?? timestamp, volume.revision ?? 1);
      }
      if (volumeIds.size === 0) throw new CreationWorkspaceError("invalid-input", "项目包不包含任何卷。");

      const insertChapter = this.database.prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const chapterIds = new Set<string>();
      for (const chapter of data.chapters ?? []) {
        const id = typeof chapter.id === "string" && chapter.id.startsWith("chapter-") ? chapter.id : `chapter-${randomUUID()}`;
        if (chapterIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复章节 ID。");
        chapterIds.add(id);
        if (!volumeIds.has(chapter.volumeId ?? "")) throw new CreationWorkspaceError("invalid-input", "项目包章节引用了不存在的卷。");
        insertChapter.run(
          id,
          projectId,
          chapter.volumeId,
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

      const insertScene = this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, planning_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const sceneIds = new Set<string>();
      for (const scene of data.scenes ?? []) {
        const id = typeof scene.id === "string" && scene.id.startsWith("scene-") ? scene.id : `scene-${randomUUID()}`;
        if (sceneIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复场景 ID。");
        sceneIds.add(id);
        if (!chapterIds.has(scene.chapterId)) throw new CreationWorkspaceError("invalid-input", "项目包场景引用了不存在的章节。");
        let bodyJson = scene.bodyJson ?? '{"type":"doc","content":[]}';
        try {
          JSON.parse(bodyJson);
        } catch {
          bodyJson = '{"type":"doc","content":[]}';
        }
        const stats = countSceneBodyStats(bodyJson);
        insertScene.run(id, scene.chapterId, scene.title, scene.sortOrder ?? 0, bodyJson, stats.han, stats.punct, stats.nonWhitespace, scene.planningJson ?? "{}", scene.createdAt ?? timestamp, scene.updatedAt ?? timestamp, scene.revision ?? 1);
      }

      const insertCardType = this.database.prepare("INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const cardType of data.cardTypes ?? []) {
        const id = typeof cardType.id === "string" && cardType.id.startsWith("card-type-") ? cardType.id : `card-type-${randomUUID()}`;
        insertCardType.run(id, projectId, cardType.kind, cardType.name, cardType.fieldsJson ?? "[]", cardType.sortOrder ?? 0, cardType.createdAt ?? timestamp, cardType.updatedAt ?? timestamp, cardType.revision ?? 1);
      }

      const insertRelationType = this.database.prepare("INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const relationType of data.relationTypes ?? []) {
        const id = typeof relationType.id === "string" && relationType.id.startsWith("relation-type-") ? relationType.id : `relation-type-${randomUUID()}`;
        insertRelationType.run(id, projectId, relationType.name, relationType.forwardName, relationType.reverseName, relationType.fromKindsJson ?? "[]", relationType.toKindsJson ?? "[]", relationType.createdAt ?? timestamp, relationType.updatedAt ?? timestamp, relationType.revision ?? 1);
      }

      const insertCard = this.database.prepare("INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const cardIds = new Set<string>();
      for (const card of data.cards ?? []) {
        const id = typeof card.id === "string" && card.id.startsWith("card-") ? card.id : `card-${randomUUID()}`;
        if (cardIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复卡片 ID。");
        cardIds.add(id);
        insertCard.run(id, projectId, card.kind, card.title, card.aliasesJson ?? "[]", card.fieldsJson ?? "{}", card.tagsJson ?? "[]", card.contentJson ?? "{}", card.createdAt ?? timestamp, card.updatedAt ?? timestamp, card.revision ?? 1);
      }

      const insertRelation = this.database.prepare("INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
      for (const relation of data.relations ?? []) {
        const id = typeof relation.id === "string" && relation.id.startsWith("relation-") ? relation.id : `relation-${randomUUID()}`;
        if (!cardIds.has(relation.fromCardId) || !cardIds.has(relation.toCardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包关系引用了不存在的卡片。");
        }
        insertRelation.run(id, projectId, relation.fromCardId, relation.toCardId, relation.relationType, relation.note ?? null, relation.createdAt ?? timestamp);
      }

      const insertSnapshot = this.database.prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)");
      for (const snapshot of data.snapshots ?? []) {
        const id = typeof snapshot.id === "string" && snapshot.id.startsWith("snapshot-") ? snapshot.id : `snapshot-${randomUUID()}`;
        insertSnapshot.run(id, projectId, snapshot.subjectType, snapshot.subjectId, snapshot.payloadJson ?? "{}", snapshot.createdAt ?? timestamp);
      }

      // 附件元数据：校验路径/哈希/大小/引用，并按资源文件映射写入新路径。
      const resourceFilesRaw = Array.isArray(runtimeCommand.resourceFiles) ? runtimeCommand.resourceFiles : undefined;
      const resourceFileMap = new Map<string, { targetRelativePath: string; sha256: string; size: number }>();
      if (resourceFilesRaw) {
        const seenTargets = new Set<string>();
        for (const entry of resourceFilesRaw) {
          const file = isRecord(entry)
            ? {
                relativePath: entry.relativePath,
                targetRelativePath: entry.targetRelativePath,
                sha256: entry.sha256,
                size: entry.size
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
            Number(file.size) > 500 * 1024 * 1024
          ) {
            throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射无效。");
          }
          if (seenTargets.has(file.targetRelativePath)) {
            throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射包含重复目标路径。");
          }
          seenTargets.add(file.targetRelativePath);
          resourceFileMap.set(file.relativePath, {
            targetRelativePath: file.targetRelativePath,
            sha256: file.sha256.toLowerCase(),
            size: Number(file.size)
          });
        }
        if (resourceFileMap.size !== (data.resources?.length ?? 0)) {
          throw new CreationWorkspaceError("invalid-input", "项目包资源文件映射与附件元数据不一致。");
        }
      }

      const insertResource = this.database.prepare(
        "INSERT INTO resources(id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const resourceIds = new Set<string>();
      const resourceTargets = new Set<string>();
      for (const resource of data.resources ?? []) {
        const id = typeof resource.id === "string" && resource.id.startsWith("resource-") ? resource.id : `resource-${randomUUID()}`;
        if (resourceIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复附件 ID。");
        resourceIds.add(id);
        if (!isSafeBundleRelativePath(resource.relativePath)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件相对路径无效（禁止绝对路径/穿越）。");
        }
        if (typeof resource.sha256 !== "string" || !/^[0-9a-f]{64}$/i.test(resource.sha256)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件校验和不合法。");
        }
        if (!Number.isInteger(resource.size) || Number(resource.size) < 0 || Number(resource.size) > 500 * 1024 * 1024) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件大小超出允许范围。");
        }
        const cardId = resource.cardId === null || resource.cardId === undefined ? null : resource.cardId;
        if (cardId !== null && !cardIds.has(cardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件引用了不存在的卡片。");
        }
        const mapped = resourceFileMap.get(resource.relativePath);
        if (resourceFilesRaw) {
          if (!mapped) throw new CreationWorkspaceError("invalid-input", "项目包附件缺少文件落盘映射。");
          if (mapped.sha256 !== resource.sha256.toLowerCase() || mapped.size !== Number(resource.size)) {
            throw new CreationWorkspaceError("invalid-input", "项目包附件文件映射与元数据不一致。");
          }
        }
        const targetRelativePath = mapped?.targetRelativePath ?? resource.relativePath;
        if (resourceTargets.has(targetRelativePath)) {
          throw new CreationWorkspaceError("invalid-input", "项目包附件目标路径重复。");
        }
        resourceTargets.add(targetRelativePath);
        insertResource.run(
          id,
          projectId,
          cardId,
          targetRelativePath,
          resource.sha256.toLowerCase(),
          Number(resource.size),
          typeof resource.originalName === "string" && resource.originalName.trim()
            ? resource.originalName.trim().slice(0, 255)
            : null,
          typeof resource.createdAt === "string" && resource.createdAt ? resource.createdAt : timestamp
        );
      }

      // 批注（v2）：scene/card 引用必须落在本包导入的实体上；锚点形状/状态/版本非法或重复 ID 一律拒绝（单事务回滚，零写入）。
      // 锚点与当前正文不再匹配时仍须保留；annotation.list 会将其标记为待重新定位。
      const insertAnnotation = this.database.prepare(
        "INSERT INTO annotations(id, project_id, scene_id, card_id, anchor_json, note, status, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const annotationIds = new Set<string>();
      for (const annotation of annotationsToImport) {
        const id = typeof annotation.id === "string" && annotation.id.startsWith("annotation-") ? annotation.id : `annotation-${randomUUID()}`;
        if (annotationIds.has(id)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复批注 ID。");
        annotationIds.add(id);
        if (!sceneIds.has(annotation.sceneId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注引用了不存在的场景。");
        }
        const cardId = annotation.cardId === null || annotation.cardId === undefined ? null : annotation.cardId;
        if (cardId !== null && !cardIds.has(cardId)) {
          throw new CreationWorkspaceError("invalid-input", "项目包批注引用了不存在的卡片。");
        }
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
          annotation.sceneId,
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
        ...[...cardIds].map((id) => ({ entity: "card" as const, id, action: "created" as const, revision: 1 })),
        ...[...resourceIds].map((id) => ({ entity: "resource" as const, id, action: "created" as const, revision: 1 })),
        ...[...annotationIds].map((id) => ({ entity: "annotation" as const, id, action: "created" as const, revision: 1 }))
      ];
      const logged = this.database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "project.bundle.import", JSON.stringify(changes), timestamp);
      this.database.exec("COMMIT");
      const counts: ProjectBundleData["counts"] = {
        volumes: volumeIds.size,
        chapters: chapterIds.size,
        scenes: sceneIds.size,
        cards: cardIds.size,
        relations: data.relations?.length ?? 0,
        snapshots: data.snapshots?.length ?? 0,
        resources: resourceIds.size,
        annotations: annotationIds.size
      };
      const result: ProjectBundleImportResult = {
        commandType: "project.bundle.import",
        sequence: Number(logged.lastInsertRowid),
        projectId,
        counts
      };
      this.emitCommitted({
        kind: "committed",
        sequence: result.sequence,
        projectId,
        commandType: "project.bundle.import",
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
      throw new CreationWorkspaceError("integrity", "无法导入项目包。");
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
    if (bodyJson.length > 5_000_000) {
      throw new CreationWorkspaceError("invalid-input", "场景正文不能超过 5000000 个字符。");
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
      const stats = countSceneBodyStats(bodyJson);
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
        .prepare("UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, revision, command.sceneId);
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
    return this.structure.executeStructureCommand(command);
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

  previewStructure(command: StructurePreviewCommand): Promise<StructurePreviewView> {
    return this.structure.previewStructure(command);
  }

  applyStructure(command: StructureApplyWithProtectionCommand): Promise<StructureApplyResult> {
    return this.structure.applyStructure(command);
  }

  revertStructure(command: StructureRevertCommand): Promise<StructureRevertResult> {
    return this.structure.revertStructure(command);
  }

  private requireProject(projectId: string): void {
    const project = this.database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId) as { id: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
  }

  private projectRevision(projectId: string): number {
    const row = this.database
      .prepare(
        `SELECT COALESCE(MAX(r), 0) AS rev FROM (
           SELECT revision AS r FROM volumes WHERE project_id = ? AND deleted_at IS NULL
           UNION ALL
           SELECT revision AS r FROM chapters WHERE project_id = ? AND deleted_at IS NULL
           UNION ALL
           SELECT s.revision AS r FROM scenes s JOIN chapters c ON c.id = s.chapter_id
           WHERE c.project_id = ? AND s.deleted_at IS NULL
         )`
      )
      .get(projectId, projectId, projectId) as { rev: number };
    return row.rev;
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

  private readWorkflow(projectId: string): string[] {
    const project = this.database
      .prepare("SELECT setup_json FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
    return parseStoredSetup(project.setup_json).chapterWorkflow;
  }

  private executeCardCommand(command: CardCommand): CreationStructureResult { switch (command.type) {
    case "cardType.create": return this.createCardType(command);
    case "cardType.update": return this.updateCardType(command);
    case "cardType.delete": return this.deleteCardType(command);
    case "relationType.create": return this.createRelationType(command);
    case "relationType.update": return this.updateRelationType(command);
    case "relationType.delete": return this.deleteRelationType(command);
    case "card.create": return this.createCard(command);
    case "card.update": return this.updateCard(command);
    case "card.delete": return this.deleteCard(command);
    case "cardRelation.create": return this.createCardRelation(command);
    case "cardRelation.delete": return this.deleteCardRelation(command);
} throw new CreationWorkspaceError("invalid-input", "不支持的卡片命令。"); }
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

  private requireCardTypeRow(cardTypeId: string): {
    id: string;
    project_id: string | null;
    kind: string;
    name: string;
    fields_json: string;
    revision: number;
} { const type = this.database.prepare("SELECT id, project_id, kind, name, fields_json, revision FROM card_types WHERE id = ?").get(cardTypeId) as {
    id: string;
    project_id: string | null;
    kind: string;
    name: string;
    fields_json: string;
    revision: number;
} | undefined; if (!type)
    throw new CreationWorkspaceError("not-found", "卡片类型不存在。"); return type; }

  private updateCardType(command: CardTypeUpdateCommand): CreationStructureResult {
    const cardTypeId = validateId(command.cardTypeId, "卡片类型");
    const name = validateTitle(command.name, "卡片类型名");
    const fields = validateCardFieldSchemaList(command.fields);
    const baseRevision = validateBaseRevision(command.baseRevision);
    return this.runStructureTransaction("cardType.update", (timestamp) => {
        const type = this.requireCardTypeRow(cardTypeId);
        if (type.project_id === null) {
            throw new CreationWorkspaceError("conflict", "内置卡片类型为只读，不可修改。");
        }
        if (type.revision !== baseRevision) {
            throw new CreationWorkspaceError("revision-mismatch", "卡片类型已被更新，请重新读取后再操作。");
        } // 已存在字段 key 不允许重命名/删除：新 schema 必须覆盖旧 schema 的全部 key。
        let oldFields: CardFieldSchema[];
        try {
            oldFields = JSON.parse(type.fields_json) as CardFieldSchema[];
        }
        catch {
            throw new CreationWorkspaceError("integrity", "卡片类型字段数据损坏。");
        }
        const oldKeys = new Set(oldFields.map((field) => field.key));
        const newKeys = new Set(fields.map((field) => field.key));
        for (const key of oldKeys) {
            if (!newKeys.has(key)) {
                throw new CreationWorkspaceError("invalid-input", `字段 key「${key}」不允许重命名或删除。`);
            }
        } // 新 schema 必须能验证现有卡片数据，禁止静默丢字段。
        const cards = this.database.prepare("SELECT fields_json FROM cards WHERE project_id = ? AND kind = ? AND deleted_at IS NULL").all(type.project_id, type.kind) as Array<{
            fields_json: string;
        }>;
        for (const row of cards) {
            let existing: Record<string, unknown>;
            try {
                existing = JSON.parse(row.fields_json) as Record<string, unknown>;
            }
            catch {
                throw new CreationWorkspaceError("integrity", "卡片字段数据损坏。");
            }
            validateCardFieldValues(existing, fields);
        }
        const revision = type.revision + 1;
        this.database.prepare("UPDATE card_types SET name = ?, fields_json = ?, updated_at = ?, revision = ? WHERE id = ?").run(name, JSON.stringify(fields), timestamp, revision, cardTypeId);
        this.touchProject(type.project_id, timestamp);
        return { projectId: type.project_id, entityId: cardTypeId, revision, changes: [{ entity: "cardType", id: cardTypeId, action: "updated", revision }] };
    });
}

  private deleteCardType(command: CardTypeDeleteCommand): CreationStructureResult { const cardTypeId = validateId(command.cardTypeId, "卡片类型"); const baseRevision = validateBaseRevision(command.baseRevision); return this.runStructureTransaction("cardType.delete", (timestamp) => { const type = this.requireCardTypeRow(cardTypeId); if (type.project_id === null) {
    throw new CreationWorkspaceError("conflict", "内置卡片类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "卡片类型已被更新，请重新读取后再操作。");
} const used = this.database.prepare("SELECT count(*) AS count FROM cards WHERE project_id = ? AND kind = ? AND deleted_at IS NULL").get(type.project_id, type.kind) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "卡片类型仍被卡片使用，不可删除。");
} this.database.prepare("DELETE FROM card_types WHERE id = ?").run(cardTypeId); this.touchProject(type.project_id, timestamp); return { projectId: type.project_id, entityId: cardTypeId, revision: type.revision + 1, changes: [{ entity: "cardType", id: cardTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

  private requireRelationTypeRow(relationTypeId: string): {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    revision: number;
} { const type = this.database.prepare("SELECT id, project_id, name, from_kinds_json, to_kinds_json, revision FROM relation_types WHERE id = ?").get(relationTypeId) as {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    revision: number;
} | undefined; if (!type)
    throw new CreationWorkspaceError("not-found", "关系类型不存在。"); return type; }

  private updateRelationType(command: RelationTypeUpdateCommand): CreationStructureResult {
    const relationTypeId = validateId(command.relationTypeId, "关系类型");
    const stableName = validateId(command.name, "关系类型稳定标识");
    const forwardName = validateTitle(command.forwardName, "关系名称", 50);
    const reverseName = validateTitle(command.reverseName, "反向关系名称", 50);
    const baseRevision = validateBaseRevision(command.baseRevision);
    const fromKinds = command.fromKinds === undefined ? undefined : validateStringList(command.fromKinds, "起点卡片类型", 50);
    const toKinds = command.toKinds === undefined ? undefined : validateStringList(command.toKinds, "终点卡片类型", 50);
    return this.runStructureTransaction("relationType.update", (timestamp) => {
        const type = this.requireRelationTypeRow(relationTypeId);
        if (type.project_id === null) {
            throw new CreationWorkspaceError("conflict", "内置关系类型为只读，不可修改。");
        }
        if (type.name !== stableName) {
            throw new CreationWorkspaceError("invalid-input", "关系类型的稳定标识不可修改。");
        }
        if (type.revision !== baseRevision) {
            throw new CreationWorkspaceError("revision-mismatch", "关系类型已被更新，请重新读取后再操作。");
        }
        let oldFrom: string[];
        let oldTo: string[];
        try {
            oldFrom = JSON.parse(type.from_kinds_json) as string[];
            oldTo = JSON.parse(type.to_kinds_json) as string[];
        }
        catch {
            throw new CreationWorkspaceError("integrity", "关系类型约束数据损坏。");
        }
        const nextFrom = fromKinds ?? oldFrom;
        const nextTo = toKinds ?? oldTo;
        const fromChanged = JSON.stringify(nextFrom) !== JSON.stringify(oldFrom);
        const toChanged = JSON.stringify(nextTo) !== JSON.stringify(oldTo);
        if (fromChanged || toChanged) { // 修改两端 kind 约束时，必须检查已有关系实例是否合法。
            const relations = this.database.prepare(`SELECT cr.id, f.kind AS from_kind, t.kind AS to_kind FROM card_relations cr             JOIN cards f ON f.id = cr.from_card_id             JOIN cards t ON t.id = cr.to_card_id             WHERE cr.relation_type = ?`).all(relationTypeId) as Array<{
                id: string;
                from_kind: string;
                to_kind: string;
            }>;
            for (const relation of relations) {
                const fromOk = nextFrom.length === 0 || nextFrom.includes(relation.from_kind);
                const toOk = nextTo.length === 0 || nextTo.includes(relation.to_kind);
                if (!fromOk || !toOk) {
                    throw new CreationWorkspaceError("conflict", `已有关系（${relation.from_kind}→${relation.to_kind}）不符合新约束，无法更新。`);
                }
            }
        }
        const revision = type.revision + 1;
        this.database.prepare("UPDATE relation_types SET forward_name = ?, reverse_name = ?, from_kinds_json = ?, to_kinds_json = ?, updated_at = ?, revision = ? WHERE id = ?").run(forwardName, reverseName, JSON.stringify(nextFrom), JSON.stringify(nextTo), timestamp, revision, relationTypeId);
        this.touchProject(type.project_id, timestamp);
        return { projectId: type.project_id, entityId: relationTypeId, revision, changes: [{ entity: "relationType", id: relationTypeId, action: "updated", revision }] };
    });
}

  private deleteRelationType(command: RelationTypeDeleteCommand): CreationStructureResult { const relationTypeId = validateId(command.relationTypeId, "关系类型"); const baseRevision = validateBaseRevision(command.baseRevision); return this.runStructureTransaction("relationType.delete", (timestamp) => { const type = this.requireRelationTypeRow(relationTypeId); if (type.project_id === null) {
    throw new CreationWorkspaceError("conflict", "内置关系类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "关系类型已被更新，请重新读取后再操作。");
} const used = this.database.prepare("SELECT count(*) AS count FROM card_relations WHERE relation_type = ?").get(relationTypeId) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "关系类型仍被关系实例使用，不可删除。");
} this.database.prepare("DELETE FROM relation_types WHERE id = ?").run(relationTypeId); this.touchProject(type.project_id, timestamp); return { projectId: type.project_id, entityId: relationTypeId, revision: type.revision + 1, changes: [{ entity: "relationType", id: relationTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

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
    let contentJson = "{}";
    if (command.content !== undefined && command.content !== null) {
      if (typeof command.content !== "object" || Array.isArray(command.content)) {
        throw new CreationWorkspaceError("invalid-input", "卡片内容必须为对象。");
      }
      contentJson = JSON.stringify(command.content);
      if (contentJson.length > 1_000_000) {
        throw new CreationWorkspaceError("invalid-input", "卡片内容不能超过 1MB。");
      }
    }
    const cardId = `card-${randomUUID()}`;
    return this.runStructureTransaction("card.create", (timestamp) => {
      this.requireProject(projectId);
      const typeFields = this.resolveCardTypeFields(projectId, kind);
      const fields = validateCardFieldValues(command.fields ?? {}, typeFields);
      this.database
        .prepare(
          "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(cardId, projectId, kind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), contentJson, timestamp, timestamp);
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
    }
    throw new CreationWorkspaceError("invalid-input", "不支持的历史命令。");
  }

  private trashList(projectId: string): TrashItem[] {
    this.requireProject(projectId);
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
    this.requireProject(query.projectId);
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
    const supported: SnapshotSubjectType[] = ["scene", "card", "chapter", "volume"];
    if (!supported.includes(command.subjectType)) {
      throw new CreationWorkspaceError("invalid-input", "快照对象类型无效。");
    }
    const subjectId = validateId(command.subjectId, "对象");
    const reason = validateTitle(command.reason, "快照说明", 200);
    const snapshotId = `snapshot-${randomUUID()}`;
    return this.runStructureTransaction("snapshot.create", (timestamp) => {
      this.requireProject(projectId);
      this.assertSnapshotSubjectOwned(projectId, command.subjectType, subjectId);
      const payload = this.captureSubjectPayload(command.subjectType, subjectId, reason);
      if (!payload) {
        throw new CreationWorkspaceError("not-found", "快照对象不存在或已删除。");
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

  /** 校验快照对象属于指定项目：对象不存在（永久删除）抛 not-found，跨项目抛 invalid-input。 */
  private assertSnapshotSubjectOwned(projectId: string, subjectType: SnapshotSubjectType, subjectId: string): void {
    const ownerProject = this.findSnapshotSubjectProject(subjectType, subjectId);
    if (ownerProject === null) {
      throw new CreationWorkspaceError("not-found", "快照对象不存在或已删除。");
    }
    if (ownerProject !== projectId) {
      throw new CreationWorkspaceError("invalid-input", "快照对象不属于当前作品。");
    }
  }

  /** 返回快照对象所属项目 ID；对象已永久删除（行不存在）时返回 null。 */
  private findSnapshotSubjectProject(subjectType: SnapshotSubjectType, subjectId: string): string | null {
    if (subjectType === "scene") {
      const row = this.database
        .prepare("SELECT c.project_id AS project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE s.id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    if (subjectType === "card") {
      const row = this.database
        .prepare("SELECT project_id FROM cards WHERE id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    if (subjectType === "chapter") {
      const row = this.database
        .prepare("SELECT project_id FROM chapters WHERE id = ?")
        .get(subjectId) as { project_id: string } | undefined;
      return row?.project_id ?? null;
    }
    const row = this.database
      .prepare("SELECT project_id FROM volumes WHERE id = ?")
      .get(subjectId) as { project_id: string } | undefined;
    return row?.project_id ?? null;
  }

  /** 读取某对象当前状态为快照 payload（快照创建与保护快照共用）。对象不存在返回 null。 */

  private captureSubjectPayload(subjectType: SnapshotSubjectType, subjectId: string, reason: string): Record<string, unknown> | null { if (subjectType === "scene") {
    const scene = this.database.prepare("SELECT body_json, revision, deleted_at FROM scenes WHERE id = ?").get(subjectId) as {
        body_json: string;
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
    return { reason, revision: scene.revision, body };
} if (subjectType === "card") {
    const card = this.database.prepare("SELECT title, aliases_json, fields_json, tags_json, revision, deleted_at FROM cards WHERE id = ?").get(subjectId) as {
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
    const chapter = this.database.prepare("SELECT title, status, numbering_kind, custom_number, revision, deleted_at FROM chapters WHERE id = ?").get(subjectId) as {
        title: string;
        status: string;
        numbering_kind: string;
        custom_number: string | null;
        revision: number;
        deleted_at: string | null;
    } | undefined;
    if (!chapter || chapter.deleted_at !== null)
        return null;
    const scenes = this.database.prepare("SELECT id, title, sort_order, body_json, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(subjectId) as Array<{
        id: string;
        title: string;
        sort_order: number;
        body_json: string;
        revision: number;
    }>;
    return { reason, revision: chapter.revision, chapter: { title: chapter.title, status: chapter.status, numberingKind: chapter.numbering_kind, customNumber: chapter.custom_number, scenes } };
} const volume = this.database.prepare("SELECT title, revision, deleted_at FROM volumes WHERE id = ?").get(subjectId) as {
    title: string;
    revision: number;
    deleted_at: string | null;
} | undefined; if (!volume || volume.deleted_at !== null)
    return null; const chapters = this.database.prepare("SELECT id, title, status, numbering_kind, custom_number, revision FROM chapters WHERE volume_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(subjectId) as Array<{
    id: string;
    title: string;
    status: string;
    numbering_kind: string;
    custom_number: string | null;
    revision: number;
}>; const chaptersWithScenes = chapters.map((chapter) => { const scenes = this.database.prepare("SELECT id, title, sort_order, body_json, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(chapter.id) as Array<{
    id: string;
    title: string;
    sort_order: number;
    body_json: string;
    revision: number;
}>; return { ...chapter, scenes }; }); return { reason, revision: volume.revision, volume: { title: volume.title, chapters: chaptersWithScenes } }; }

  private static readonly SNAPSHOT_SUBJECT_TYPES = new Set<SnapshotSubjectType>(["scene", "card", "chapter", "volume"]);

  private requireSnapshot(projectId: string, snapshotId: string): {
    subject_type: string;
    subject_id: string;
    payload_json: string;
} { const snapshot = this.database.prepare("SELECT subject_type, subject_id, payload_json FROM snapshots WHERE id = ? AND project_id = ?").get(snapshotId, projectId) as {
    subject_type: string;
    subject_id: string;
    payload_json: string;
} | undefined; if (!snapshot)
    throw new CreationWorkspaceError("not-found", "快照不存在。"); return snapshot; }

  private parseSnapshotPayload(payloadJson: string): Record<string, unknown> { try {
    const parsed = JSON.parse(payloadJson) as unknown;
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        throw new Error("not an object");
    }
    return parsed as Record<string, unknown>;
}
catch {
    throw new CreationWorkspaceError("integrity", "快照数据损坏。");
} }

  private clampDiffText(value: string, max = 400): string { if (value.length <= max)
    return value; return `${value.slice(0, max)}…（共 ${value.length} 字）`; } /** 快照差异预览：rows 中 before=当前值、after=快照值。数据缺失/损坏时给 warnings 并置 canRestore=false。 */

  private readSnapshotPreview(projectId: string, snapshotId: string): SnapshotPreviewView | null { this.requireProject(projectId); const snapshot = this.requireSnapshot(projectId, snapshotId); const warnings: string[] = []; let payload: Record<string, unknown>; try {
    payload = this.parseSnapshotPayload(snapshot.payload_json);
}
catch (error) {
    warnings.push(error instanceof Error ? error.message : String(error));
    return { snapshotId, subjectType: snapshot.subject_type as SnapshotSubjectType, subjectId: snapshot.subject_id, title: "", rows: [], warnings, canRestore: false };
} const rows: SnapshotDiffRow[] = []; const pushRow = (label: string, before: string, after: string) => { rows.push({ label, before: this.clampDiffText(before), after: this.clampDiffText(after), changed: before !== after }); }; let title = ""; let complete = true; if (SqliteCreationWorkspace.SNAPSHOT_SUBJECT_TYPES.has(snapshot.subject_type as SnapshotSubjectType)) {
    const ownerProject = this.findSnapshotSubjectProject(snapshot.subject_type as SnapshotSubjectType, snapshot.subject_id);
    if (ownerProject === null) {
        warnings.push("目标对象已永久删除，无法恢复。");
        complete = false;
    }
    else if (ownerProject !== projectId) {
        throw new CreationWorkspaceError("invalid-input", "快照对象不属于当前作品。");
    }
} if (snapshot.subject_type === "scene") {
    const current = this.database.prepare("SELECT title, body_json FROM scenes WHERE id = ?").get(snapshot.subject_id) as {
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
    const current = this.database.prepare("SELECT title, aliases_json, fields_json, tags_json FROM cards WHERE id = ?").get(snapshot.subject_id) as {
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
    const current = this.database.prepare("SELECT title, status, numbering_kind, custom_number FROM chapters WHERE id = ?").get(snapshot.subject_id) as {
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
                const sceneRow = this.database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(item.id) as {
                    chapter_id: string;
                } | undefined;
                if (sceneRow && sceneRow.chapter_id !== snapshot.subject_id) movedSceneIds.push(item.id);
            }
        }
        if (current) {
            pushRow("标题", current.title, chapter.title ?? "");
            pushRow("状态", current.status, chapter.status ?? "");
            pushRow("编号方式", current.numbering_kind, chapter.numberingKind ?? "");
            pushRow("场景数", `${this.countChapterScenes(snapshot.subject_id)}`, `${sceneCount}`);
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
    const current = this.database.prepare("SELECT title FROM volumes WHERE id = ?").get(snapshot.subject_id) as {
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
                const chapterRow = this.database.prepare("SELECT volume_id FROM chapters WHERE id = ?").get(item.id) as {
                    volume_id: string | null;
                } | undefined;
                if (chapterRow && chapterRow.volume_id !== snapshot.subject_id) {
                    movedChapterIds.push(item.id);
                    continue;
                }
                const chapterScenes = Array.isArray(item.scenes) ? item.scenes : [];
                for (const sceneItem of chapterScenes) {
                    if (!isRecord(sceneItem) || typeof sceneItem.id !== "string") continue;
                    const sceneRow = this.database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(sceneItem.id) as {
                        chapter_id: string;
                    } | undefined;
                    if (sceneRow && sceneRow.chapter_id !== item.id) movedSceneCount += 1;
                }
            }
        }
        if (current) {
            pushRow("标题", current.title, volume.title ?? "");
            pushRow("章节数", `${this.countVolumeChapters(snapshot.subject_id)}`, `${chapterCount}`);
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
} return { snapshotId, subjectType: snapshot.subject_type as SnapshotSubjectType, subjectId: snapshot.subject_id, title, rows, warnings, canRestore: complete && SqliteCreationWorkspace.SNAPSHOT_SUBJECT_TYPES.has(snapshot.subject_type as SnapshotSubjectType) }; }

  private countChapterScenes(chapterId: string): number { const row = this.database.prepare("SELECT count(*) AS count FROM scenes WHERE chapter_id = ?").get(chapterId) as {
    count: number;
}; return row.count; }

  private countVolumeChapters(volumeId: string): number { const row = this.database.prepare("SELECT count(*) AS count FROM chapters WHERE volume_id = ?").get(volumeId) as {
    count: number;
}; return row.count; } /** 恢复目标快照内容（restoreWithProtection 事务内调用）。返回最新 revision 与事件 changes。 */

  private applySnapshotPayload(subjectType: string, subjectId: string, payload: Record<string, unknown>, timestamp: string): {
    revision: number;
    changes: CreationWorkspaceEvent["changes"];
} {
    if (subjectType === "scene") {
        const body = payload.body as CreationDocument | undefined;
        if (!body)
            throw new CreationWorkspaceError("integrity", "快照正文数据缺失。");
        const current = this.database.prepare("SELECT revision FROM scenes WHERE id = ?").get(subjectId) as {
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "场景已永久删除，无法恢复。");
        const revision = current.revision + 1;
        const bodyJson = JSON.stringify(body);
        const stats = countSceneBodyStats(bodyJson);
        this.database.prepare("UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, revision, subjectId);
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
        const current = this.database.prepare("SELECT revision FROM cards WHERE id = ?").get(subjectId) as {
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "卡片已永久删除，无法恢复。");
        const revision = current.revision + 1;
        this.database.prepare("UPDATE cards SET title = ?, aliases_json = ?, fields_json = ?, tags_json = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(card.title ?? "", JSON.stringify(card.aliases ?? []), JSON.stringify(card.fields ?? {}), JSON.stringify(card.tags ?? []), timestamp, revision, subjectId);
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
                revision: number;
            }>;
        } | undefined;
        if (!chapter)
            throw new CreationWorkspaceError("integrity", "快照章节数据缺失。");
        const current = this.database.prepare("SELECT revision FROM chapters WHERE id = ?").get(subjectId) as {
            revision: number;
        } | undefined;
        if (!current)
            throw new CreationWorkspaceError("not-found", "章节已永久删除，无法恢复。");
        const revision = current.revision + 1;
        this.database.prepare("UPDATE chapters SET title = ?, status = ?, numbering_kind = ?, custom_number = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(chapter.title ?? "", chapter.status ?? "", chapter.numberingKind ?? "auto", chapter.customNumber ?? null, timestamp, revision, subjectId);
        const changes: CreationWorkspaceEvent["changes"] = [{ entity: "chapter", id: subjectId, action: "restored", revision }];
        for (const scene of chapter.scenes ?? []) {
            // 场景已移出本章：跳过，不覆盖其在其他章节的新内容。
            const existing = this.database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(scene.id) as {
                chapter_id: string;
            } | undefined;
            if (existing && existing.chapter_id !== subjectId)
                continue;
            const sceneRevision = (scene.revision ?? 0) + 1;
            const stats = countSceneBodyStats(scene.body_json);
            if (existing) {
                this.database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, subjectId, scene.title, scene.sort_order, scene.body_json, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
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
                revision: number;
            }>;
        }>;
    } | undefined;
    if (!volume)
        throw new CreationWorkspaceError("integrity", "快照卷数据缺失。");
    const current = this.database.prepare("SELECT revision FROM volumes WHERE id = ?").get(subjectId) as {
        revision: number;
    } | undefined;
    const volumeProject = this.database.prepare("SELECT project_id FROM volumes WHERE id = ?").get(subjectId) as {
        project_id: string;
    } | undefined;
    if (!volumeProject)
        throw new CreationWorkspaceError("not-found", "卷不存在，无法恢复。");
    const revision = (current?.revision ?? 0) + 1;
    this.database.prepare("UPDATE volumes SET title = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(volume.title ?? "", timestamp, revision, subjectId);
    const changes: CreationWorkspaceEvent["changes"] = [{ entity: "volume", id: subjectId, action: "restored", revision }];
    for (const chapter of volume.chapters ?? []) {
        const chapterRevision = (chapter.revision ?? 0) + 1;
        // 章节已移出本卷：跳过，不覆盖其在其他卷的新修改、不改归属。
        const existingChapter = this.database.prepare("SELECT volume_id FROM chapters WHERE id = ?").get(chapter.id) as {
            volume_id: string | null;
        } | undefined;
        if (existingChapter && existingChapter.volume_id !== subjectId)
            continue;
        if (existingChapter) {
            this.database.prepare("UPDATE chapters SET title = ?, status = ?, numbering_kind = ?, custom_number = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(chapter.title, chapter.status, chapter.numbering_kind, chapter.custom_number ?? null, timestamp, chapterRevision, chapter.id);
        }
        else {
            this.database.prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(chapter.id, volumeProject.project_id, subjectId, chapter.title, 0, chapter.status, chapter.numbering_kind, chapter.custom_number ?? null, timestamp, timestamp, chapterRevision);
        }
        changes.push({ entity: "chapter", id: chapter.id, action: "restored", revision: chapterRevision });
        for (const scene of chapter.scenes ?? []) {
            const sceneRevision = (scene.revision ?? 0) + 1;
            // 场景已移出本章：跳过，不覆盖其在其他章节的新内容、不改归属。
            const existingScene = this.database.prepare("SELECT chapter_id FROM scenes WHERE id = ?").get(scene.id) as {
                chapter_id: string;
            } | undefined;
            if (existingScene && existingScene.chapter_id !== chapter.id)
                continue;
            const stats = countSceneBodyStats(scene.body_json);
            if (existingScene) {
                this.database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, chapter.id, scene.title, scene.sort_order, scene.body_json, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
            }
            changes.push({ entity: "scene", id: scene.id, action: "restored", revision: sceneRevision });
        }
    }
    return { revision, changes };
} /**   * 快照安全恢复：同一 BEGIN IMMEDIATE 事务内 保护快照 → 恢复目标 → change_log → COMMIT。   * 任一步失败整体回滚（保护快照不落库、对象不变）。   */

  async restoreSnapshotWithProtection(command: SnapshotRestoreWithProtectionCommand): Promise<SnapshotRestoreWithProtectionResult> {
    const projectId = validateId(command.projectId, "作品");
    const snapshotId = validateId(command.snapshotId, "快照");
    const protectionReason = validateTitle(command.protectionReason, "保护原因", 200);
    const timestamp = new Date().toISOString();
    const protectionSnapshotId = `protective-snapshot-${randomUUID()}`;
    try {
        this.database.exec("BEGIN IMMEDIATE");
        this.requireProject(projectId);
        const snapshot = this.requireSnapshot(projectId, snapshotId);
        const subjectType = snapshot.subject_type as SnapshotSubjectType;
        if (!SqliteCreationWorkspace.SNAPSHOT_SUBJECT_TYPES.has(subjectType)) {
            throw new CreationWorkspaceError("invalid-input", "快照对象类型不支持安全恢复。");
        }
        // 归属校验：对象已永久删除或不属于当前项目时，在写入保护快照前稳定失败（整体回滚、零写入）。
        this.assertSnapshotSubjectOwned(projectId, subjectType, snapshot.subject_id);
        const payload = this.parseSnapshotPayload(snapshot.payload_json); // a. 捕获恢复前保护快照（对象不存在时记录 absent 标记，仍可用于回退）。
        const protectionPayload = this.captureSubjectPayload(subjectType, snapshot.subject_id, protectionReason);
        this.database.prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)").run(protectionSnapshotId, projectId, subjectType, snapshot.subject_id, JSON.stringify(protectionPayload ?? { reason: protectionReason, revision: 0, absent: true }), timestamp); // b. 恢复目标快照
        const restored = this.applySnapshotPayload(subjectType, snapshot.subject_id, payload, timestamp);
        this.touchProject(projectId, timestamp); // c. change_log
        const logged = this.database.prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)").run(projectId, "snapshot.restoreWithProtection", JSON.stringify(restored.changes), timestamp); // d. COMMIT
        this.database.exec("COMMIT");
        this.emitCommitted({ kind: "committed", sequence: Number(logged.lastInsertRowid), projectId, commandType: "snapshot.restoreWithProtection", changes: restored.changes });
        return { ok: true, protectionSnapshotId, restoredSubjectType: subjectType, restoredSubjectId: snapshot.subject_id, revision: restored.revision };
    }
    catch (error) {
        try {
            this.database.exec("ROLLBACK");
        }
        catch { // The transaction may already have been rolled back by SQLite.
        }
        if (error instanceof CreationWorkspaceError)
            throw error;
        if (isConstraintError(error))
            throw new CreationWorkspaceError("conflict", "快照恢复违反结构约束，未应用任何修改。");
        throw new CreationWorkspaceError("integrity", "无法安全恢复快照。");
    }
} /** 回收站影响预览：对象不存在或不在回收站时返回稳定 not-found 错误。 */

  private readTrashImpact(projectId: string, entity: TrashEntityKind, entityId: string): TrashImpactView { this.requireProject(projectId); const warnings: string[] = []; if (entity === "volume") {
    const volume = this.database.prepare("SELECT title, deleted_at FROM volumes WHERE id = ? AND project_id = ?").get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
    } | undefined;
    if (!volume)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (volume.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    const chapters = this.database.prepare("SELECT count(*) AS count FROM chapters WHERE volume_id = ?").get(entityId) as {
        count: number;
    };
    const scenes = this.database.prepare("SELECT coalesce(sum(s.non_ws_count), 0) AS chars, count(*) AS count FROM scenes s JOIN chapters c ON c.id = s.chapter_id WHERE c.volume_id = ?").get(entityId) as {
        chars: number;
        count: number;
    };
    return { title: volume.title, childVolumeCount: 0, childChapterCount: chapters.count, childSceneCount: scenes.count, relatedCardCount: 0, resourceCount: 0, approxChars: scenes.chars, warnings };
} if (entity === "chapter") {
    const chapter = this.database.prepare("SELECT title, deleted_at FROM chapters WHERE id = ? AND project_id = ?").get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
    } | undefined;
    if (!chapter)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (chapter.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    const scenes = this.database.prepare("SELECT coalesce(sum(non_ws_count), 0) AS chars, count(*) AS count FROM scenes WHERE chapter_id = ?").get(entityId) as {
        chars: number;
        count: number;
    };
    return { title: chapter.title, childVolumeCount: 0, childChapterCount: 0, childSceneCount: scenes.count, relatedCardCount: 0, resourceCount: 0, approxChars: scenes.chars, warnings };
} if (entity === "scene") {
    const scene = this.database.prepare(`SELECT s.title, s.deleted_at, s.non_ws_count FROM scenes s JOIN chapters c ON c.id = s.chapter_id           WHERE s.id = ? AND c.project_id = ?`).get(entityId, projectId) as {
        title: string;
        deleted_at: string | null;
        non_ws_count: number;
    } | undefined;
    if (!scene)
        throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (scene.deleted_at === null)
        throw new CreationWorkspaceError("conflict", "对象不在回收站。");
    return { title: scene.title, childVolumeCount: 0, childChapterCount: 0, childSceneCount: 0, relatedCardCount: 0, resourceCount: 0, approxChars: scene.non_ws_count, warnings };
} const card = this.database.prepare("SELECT title, deleted_at FROM cards WHERE id = ? AND project_id = ?").get(entityId, projectId) as {
    title: string;
    deleted_at: string | null;
} | undefined; if (!card)
    throw new CreationWorkspaceError("not-found", "对象不存在。"); if (card.deleted_at === null)
    throw new CreationWorkspaceError("conflict", "对象不在回收站。"); const relations = this.database.prepare("SELECT count(*) AS count FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").get(entityId, entityId) as {
    count: number;
}; const resources = this.database.prepare("SELECT count(*) AS count FROM resources WHERE card_id = ?").get(entityId) as {
    count: number;
}; return { title: card.title, childVolumeCount: 0, childChapterCount: 0, childSceneCount: 0, relatedCardCount: relations.count, resourceCount: resources.count, approxChars: 0, warnings }; }
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
        inbox: count("inbox_items"),
        annotations: count("annotations")
      },
      schema,
      relations,
      resources,
      indexes: indexesSection,
      snapshots
    };
  }

  // ---- Phase 1 P1 深模块 seam 接入实现 ----

  /** 分层快照留存：system 受控分类 + 单事务删除；绝不读取 reason。 */
  async runSnapshotRetention(): Promise<SnapshotRetentionResult> {
    this.assertOpen();
    const rows = this.database
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
      this.database.exec("BEGIN IMMEDIATE");
      try {
        const del = this.database.prepare("DELETE FROM snapshots WHERE id = ?");
        for (const id of plan.deleteIds) del.run(id);
        this.database.exec("COMMIT");
      } catch (error) {
        try {
          this.database.exec("ROLLBACK");
        } catch {
          /* 已回滚 */
        }
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法执行分层快照留存删除。");
      }
    }
    return { keepIds: plan.keepIds, deleteIds: plan.deleteIds, deletedCount: plan.deleteIds.length };
  }

  /** 读取导入所需的项目模式上下文（类型 / 关系类型 / 现有卡片标题）。 */
  async cardImportSchema(projectId: string): Promise<CardImportSchemaContext> {
    this.assertOpen();
    validateId(projectId, "作品");
    return readCardImportSchemaContext(this.database, projectId);
  }

  /** 读模式上下文 -> 规划（纯，不写库），供 UI 预览可应用结果。 */
  async cardImportPlan(input: CardImportApplyInput): Promise<CardImportPlan> {
    this.assertOpen();
    validateId(input.projectId, "作品");
    const schema = readCardImportSchemaContext(this.database, input.projectId);
    return planCardImport(input.text, input.format, input.mapping, schema, input.options);
  }

  /** 读模式 -> 规划 -> 单事务 apply；失败回滚零写入。 */
  async cardImportApply(input: CardImportApplyInput): Promise<CardImportApplyResult> {
    this.assertOpen();
    validateId(input.projectId, "作品");
    const schema = readCardImportSchemaContext(this.database, input.projectId);
    const plan = planCardImport(input.text, input.format, input.mapping, schema, input.options);
    const planId = generateCardImportPlanId();
    return applyCardImportPlan(this.database, plan, planId);
  }

  /** 按筛选范围读取可导出卡片行（不携带内部 id；cardRef 已转为标题）。 */
  async cardExportRows(projectId: string, filter: CardExportFilter): Promise<CardExportRow[]> {
    this.assertOpen();
    validateId(projectId, "作品");
    return readCardsForExport(this.database, projectId, filter);
  }

  /** 全项目替换计划 store：scenes/snapshots/change_log 走 DB，计划本体落 JSON 文件。 */
  private buildReplacePlanStore(): ReplacePlanStore {
    const listSql = `
      SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
      FROM scenes s JOIN chapters c ON c.id = s.chapter_id
      WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND c.project_id = ?`;
    const getSceneStmt = this.database.prepare(
      "SELECT body_json, revision, updated_at FROM scenes WHERE id = ? AND deleted_at IS NULL"
    );
    const updateScene = this.database.prepare(
      "UPDATE scenes SET body_json = ?, han_count = ?, punct_count = ?, non_ws_count = ?, updated_at = ?, revision = ? WHERE id = ?"
    );
    const insertSnapshot = this.database.prepare(
      "INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)"
    );
    const insertChangeLog = this.database.prepare(
      "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
    );
    const plansDir = path.join(this.workspaceDirectory, "replace-plans");
    mkdirSync(plansDir, { recursive: true });
    const usedPlanIds = new Set<string>();
    const readPlanFile = (planId: string): ReplacePlan | undefined => {
      try {
        const raw = readFileSync(path.join(plansDir, `${planId}.json`), "utf8");
        return JSON.parse(raw) as ReplacePlan;
      } catch {
        return undefined;
      }
    };
    return {
      listScopeScenes: (projectId, scope, scopeId) => {
        let sql = listSql;
        const params: unknown[] = [projectId];
        if (scope === "chapter" && scopeId) {
          sql = `
            SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
            FROM scenes s JOIN chapters c ON c.id = s.chapter_id
            WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.chapter_id = ?`;
          params[0] = scopeId;
        } else if (scope === "scene" && scopeId) {
          sql = `
            SELECT s.id, s.project_id, s.chapter_id, c.title AS chapter_title, s.title, s.body_json, s.revision, s.updated_at
            FROM scenes s JOIN chapters c ON c.id = s.chapter_id
            WHERE s.deleted_at IS NULL AND c.deleted_at IS NULL AND s.id = ?`;
          params[0] = scopeId;
        }
        const rows = this.database.prepare(sql).all(...params) as Array<{
          id: string;
          project_id: string;
          chapter_id: string;
          chapter_title: string;
          title: string;
          body_json: string;
          revision: number;
          updated_at: string;
        }>;
        return rows.map((r) => ({
          id: r.id,
          projectId: r.project_id,
          chapterId: r.chapter_id,
          chapterTitle: r.chapter_title,
          title: r.title,
          bodyJson: r.body_json,
          revision: r.revision,
          updatedAt: r.updated_at
        }));
      },
      getScene: (id) => {
        const row = getSceneStmt.get(id) as { body_json: string; revision: number; updated_at: string } | undefined;
        if (!row) return undefined;
        return { bodyJson: row.body_json, revision: row.revision, updatedAt: row.updated_at };
      },
      savePlan: (plan) => {
        writeFileSync(path.join(plansDir, `${plan.planId}.json`), JSON.stringify(plan), "utf8");
      },
      loadPlan: (planId) => readPlanFile(planId),
      deletePlan: (planId) => {
        try {
          unlinkSync(path.join(plansDir, `${planId}.json`));
        } catch {
          /* 已不存在 */
        }
      },
      isPlanUsed: (planId) => usedPlanIds.has(planId),
      markPlanUsed: (planId) => {
        usedPlanIds.add(planId);
        try {
          unlinkSync(path.join(plansDir, `${planId}.json`));
        } catch {
          /* 已不存在 */
        }
      },
      beginTransaction: () => this.database.exec("BEGIN IMMEDIATE"),
      commitTransaction: () => this.database.exec("COMMIT"),
      rollbackTransaction: () => this.database.exec("ROLLBACK"),
      updateSceneBody: (id, bodyJson, newRevision, timestamp) => {
        const stats = countSceneBodyStats(bodyJson);
        updateScene.run(bodyJson, stats.han, stats.punct, stats.nonWhitespace, timestamp, newRevision, id);
      },
      insertSnapshot: (id, projectId, subjectId, payloadJson, createdAt) =>
        insertSnapshot.run(id, projectId, "scene", subjectId, payloadJson, createdAt),
      insertChangeLog: (projectId, commandType, changesJson, committedAt) =>
        Number(insertChangeLog.run(projectId, commandType, changesJson, committedAt).lastInsertRowid)
    };
  }

  async createReplacePlan(query: ReplacePlanQuery, controller?: ReplacePlanController): Promise<ReplacePlan> {
    this.assertOpen();
    return runCreateReplacePlan(this.buildReplacePlanStore(), query, controller);
  }

  async applyReplacePlan(
    planId: string,
    excludedHitIds: string[],
    controller?: ReplacePlanController
  ): Promise<ReplaceApplyOutcome> {
    this.assertOpen();
    const store = this.buildReplacePlanStore();
    const plan = store.loadPlan(planId);
    const projectId = plan?.projectId ?? "";
    const outcome = await runApplyReplacePlan(store, planId, excludedHitIds, controller);
    if (projectId && outcome.modifiedSceneIds.length > 0) {
      const timestamp = outcome.committedAt;
      this.touchProject(projectId, timestamp);
      const changes = outcome.modifiedSceneIds.flatMap((sceneId, index) => [
        { entity: "scene" as const, id: sceneId, action: "updated" as const, revision: 0 },
        {
          entity: "snapshot" as const,
          id: outcome.snapshotIds[index] ?? "",
          action: "created" as const,
          revision: 1
        }
      ]);
      this.emitCommitted({
        kind: "committed",
        sequence: outcome.sequence,
        projectId,
        commandType: "replace.applyPlan",
        changes
      });
    }
    return outcome;
  }

  /** 修正已有写作会话（仅 startedAt/activeSeconds/netChars 可改）。 */
  async sessionUpdate(command: SessionUpdateCommand): Promise<SessionReportResult> {
    this.assertOpen();
    const projectId = validateId(command.projectId, "作品");
    this.requireProject(projectId);
    const sessionId = validateId(command.sessionId, "会话");
    const row = this.database
      .prepare("SELECT id, started_at, active_seconds, net_chars FROM writing_sessions WHERE id = ? AND project_id = ?")
      .get(sessionId, projectId) as { id: string; started_at: string; active_seconds: number; net_chars: number } | undefined;
    if (!row) throw new CreationWorkspaceError("not-found", "会话不存在或不属于该项目。");

    const startedAt =
      typeof command.startedAt === "string" && !Number.isNaN(Date.parse(command.startedAt))
        ? new Date(command.startedAt).toISOString()
        : row.started_at;
    const activeSeconds =
      command.activeSeconds === undefined ? row.active_seconds : command.activeSeconds;
    if (!Number.isFinite(activeSeconds) || activeSeconds < 0 || activeSeconds > 86_400) {
      throw new CreationWorkspaceError("invalid-input", "活动时长必须在 0 至 86400 秒之间。");
    }
    const netChars = command.netChars === undefined ? row.net_chars : command.netChars;
    if (!Number.isFinite(netChars) || netChars < -1_000_000 || netChars > 1_000_000) {
      throw new CreationWorkspaceError("invalid-input", "净增字符数超出允许范围。");
    }
    const timestamp = new Date().toISOString();
    this.database
      .prepare("UPDATE writing_sessions SET started_at = ?, active_seconds = ?, net_chars = ? WHERE id = ?")
      .run(startedAt, Math.round(activeSeconds), Math.round(netChars), sessionId);
    this.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "session.update",
      changes: [{ entity: "session", id: sessionId, action: "updated", revision: 0 }]
    });
    return { commandType: "session.update", sequence: -1, projectId, sessionId, updatedAt: timestamp };
  }

  /** 更新项目目标（写入 setup_json，不新增表列）。 */
  async projectUpdateGoal(command: ProjectUpdateGoalCommand): Promise<ProjectGoalResult> {
    this.assertOpen();
    const projectId = validateId(command.projectId, "作品");
    const row = this.database
      .prepare("SELECT setup_json, revision FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string; revision: number } | undefined;
    if (!row) throw new CreationWorkspaceError("not-found", "作品不存在。");
    const setup = parseStoredSetup(row.setup_json);
    if (command.dailyWordGoal !== undefined) setup.dailyWordGoal = command.dailyWordGoal ?? undefined;
    if (command.weeklyWordGoal !== undefined) setup.weeklyWordGoal = command.weeklyWordGoal ?? undefined;
    if (command.totalWordGoal !== undefined) setup.totalWordGoal = command.totalWordGoal ?? undefined;
    if (command.targetDate !== undefined) setup.targetDate = command.targetDate ?? undefined;
    if (command.description !== undefined) setup.description = command.description ?? undefined;
    if (command.genre !== undefined) setup.genre = command.genre ?? undefined;
    if (command.weeklyUpdateDays !== undefined) {
      setup.weeklyUpdateDays = command.weeklyUpdateDays.filter(
        (day) => Number.isInteger(day) && day >= 1 && day <= 7
      );
    }
    const newRevision = row.revision + 1;
    const timestamp = new Date().toISOString();
    this.database
      .prepare("UPDATE projects SET setup_json = ?, revision = ?, updated_at = ? WHERE id = ?")
      .run(JSON.stringify(setup), newRevision, timestamp, projectId);
    this.touchProject(projectId, timestamp);
    this.emitCommitted({
      kind: "committed",
      sequence: -1,
      projectId,
      commandType: "project.updateGoal",
      changes: [{ entity: "project", id: projectId, action: "updated", revision: newRevision }]
    });
    return { projectId, setup };
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
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 2) {
      migrateSchemaV2ToV3(database);
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 3) {
      migrateSchemaV3ToV4(database);
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 4) {
      migrateSchemaV4ToV5(database);
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 5) {
      migrateSchemaV5ToV6(database);
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 6) {
      migrateSchemaV6ToV7(database);
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 7) {
      migrateSchemaV7ToV8(database);
      migrateSchemaV8ToV9(database);
    } else if (existingVersion === 8) migrateSchemaV8ToV9(database);
    else if (existingVersion !== SCHEMA_VERSION) {
      throw new CreationWorkspaceError("integrity", `不支持的创作工作区 schema 版本：${existingVersion}。`);
    }
    try {
      purgeExpiredTrash(database);
    } catch {
      // 到期清理失败不应阻止工作区打开
    }
    return new SqliteCreationWorkspace(database, options.directory);
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
