import { mkdir, rm } from "node:fs/promises";
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
  type CardLinkResult,
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
  type ProofIgnoreCommand,
  type ProofIgnoreEntry,
  type ProofIgnoreListQuery,
  type ProofIgnoreResult,
  type ProofUnignoreCommand,
  type ProofScanScope,
  type RelationGraphEdge,
  type RelationGraphNode,
  type RelationGraphQuery,
  type RelationGraphView,
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
  type ProjectBundleImportPreview,
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
   type SceneStatus,
   type SceneUpdatePlanningCommand,
   type SceneUpdatePlanningResult,
   type SceneUpdateMetaCommand,
   type SceneUpdateMetaResult,
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
import type { ProjectBundleCardMapping, ProjectBundleCardResolution } from "../../../src/types/creation";
import { countSceneBodyStats } from "./scene-stats";
import { createStatsSessionsModule, type StatsSessionsModule } from "./stats-sessions";
import { createSearchModule, type SearchModule } from "./search";
import { createInboxModule, type InboxModule } from "./inbox";
import { createResourceModule, type ResourceModule } from "./resource";
import { createAnnotationModule, type AnnotationModule } from "./annotation";
import { createReplaceModule, type ReplaceModule } from "./replace";
import {
  createGlobalCardSchemaRepairBackup,
  createV9MigrationBackup,
  GLOBAL_CARD_SCHEMA_VERSION,
  migrateGlobalCardsV9ToV10,
  needsGlobalCardSchemaRepair,
  repairGlobalCardSchemaV10
} from "./global-card-migration";
import { createStructureModule, type StructureModule, STRUCTURE_COMMAND_TYPES } from "./structure";
import {
  extractSceneText,
  isConstraintError,
  isRecord,
  parseJsonArray,
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

/** 当前 schema 版本；契约断言一律引用此常量，避免升版时漏改硬编码数字。 */
export const SCHEMA_VERSION = 12;
/** v11：场景摘要与场景状态独立持久化。保留为独立常量，避免迁移链出现「魔术版本号」。 */
const SCENE_META_SCHEMA_VERSION = 11;

const TARGET_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const SCENE_TEXT_BLOCKS = new Set(["paragraph", "quoteLetter", "centeredText", "authorNote"]);
const SCENE_MARKS = new Set(["bold", "italic"]);
const SCENE_STATUSES = new Set<SceneStatus>(["planned", "drafting", "revising", "done"]);
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
  "card.link",
  "card.unlink",
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
  "paragraphStartRepeat",
  "aliasInconsistency",
  "suspectedTypo"
]);

/** 规则级兜底说明：命中没有 detail 时用于分组消息。 */
const PROOF_RULE_BASE_MESSAGE: Record<ProofRule, string> = {
  repeatedChar: "存在连续重复字",
  unbalancedPunctuation: "成对标点数量不等",
  abnormalSpacing: "存在异常空格",
  longParagraph: "存在超长段落",
  bannedWord: "命中禁用词",
  mixedPunctuation: "疑似中英标点混用",
  crutchWord: "叙述词重复过多",
  paragraphStartRepeat: "连续段落以同一字开头",
  aliasInconsistency: "同一卡片出现多种称呼",
  suspectedTypo: "疑似错拼"
};

function validateProofRule(value: unknown): ProofRule {
  if (typeof value !== "string" || !PROOF_RULES.has(value as ProofRule)) {
    throw new CreationWorkspaceError("invalid-input", "不支持的校对规则。");
  }
  return value as ProofRule;
}
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
  "project_card_links",
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
  "idx_project_card_links_card",
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

function canonicalJsonValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalJsonValue);
  if (!isRecord(value)) return value;
  return Object.fromEntries(
    Object.keys(value).sort().map((key) => [key, canonicalJsonValue(value[key])])
  );
}

function canonicalStoredJson(value: string): string {
  try {
    return JSON.stringify(canonicalJsonValue(JSON.parse(value)));
  } catch {
    return `!invalid:${value}`;
  }
}

interface StoredBundleCardComparable {
  id: string;
  kind: string;
  title: string;
  aliases_json: string;
  fields_json: string;
  tags_json: string;
  content_json: string;
  deleted_at: string | null;
}

interface ComparableCardResource {
  role: string;
  sha256: string;
  size: number;
  originalName: string | null;
}

function cardResourceSignature(resources: ComparableCardResource[]): string {
  return JSON.stringify(
    resources
      .map((resource) => ({
        role: resource.role,
        sha256: resource.sha256.toLowerCase(),
        size: resource.size,
        originalName: resource.originalName
      }))
      .sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right)))
  );
}

function incomingCardResourceSignature(data: ProjectBundleData, cardId: string): string {
  return cardResourceSignature(
    (data.resources ?? [])
      .filter((resource) => resource.ownerScope === "card" && resource.cardId === cardId)
      .map((resource) => ({
        role: resource.role ?? "attachment",
        sha256: resource.sha256,
        size: resource.size,
        originalName: resource.originalName
      }))
  );
}

function bundleCardDifferences(
  card: ProjectBundleData["cards"][number],
  local: StoredBundleCardComparable,
  incomingResourceSignature?: string,
  localResourceSignature?: string
): string[] {
  const differences: string[] = [];
  if (card.kind !== local.kind) differences.push("类型");
  if (card.title !== local.title) differences.push("名称");
  if (canonicalStoredJson(card.aliasesJson ?? "[]") !== canonicalStoredJson(local.aliases_json)) differences.push("别名");
  if (canonicalStoredJson(card.fieldsJson ?? "{}") !== canonicalStoredJson(local.fields_json)) differences.push("字段");
  if (canonicalStoredJson(card.tagsJson ?? "[]") !== canonicalStoredJson(local.tags_json)) differences.push("标签");
  if (canonicalStoredJson(card.contentJson ?? "{}") !== canonicalStoredJson(local.content_json)) differences.push("内容快照");
  if (incomingResourceSignature !== undefined && localResourceSignature !== undefined && incomingResourceSignature !== localResourceSignature) {
    differences.push("全局附件");
  }
  return differences;
}

function remapPlanningCardIds(planningJson: string, cardIdMap: ReadonlyMap<string, string>): string {
  let planning: ScenePlanning;
  try {
    planning = JSON.parse(planningJson || "{}") as ScenePlanning;
  } catch {
    throw new CreationWorkspaceError("invalid-input", "项目包场景任务卡数据无效。");
  }
  if (planning.perspectiveCardId) planning.perspectiveCardId = cardIdMap.get(planning.perspectiveCardId) ?? planning.perspectiveCardId;
  if (planning.locationCardId) planning.locationCardId = cardIdMap.get(planning.locationCardId) ?? planning.locationCardId;
  if (Array.isArray(planning.castCardIds)) {
    planning.castCardIds = planning.castCardIds.map((cardId) => cardIdMap.get(cardId) ?? cardId);
  }
  return JSON.stringify(planning);
}

function remapJsonStableIds(value: string, idMap: ReadonlyMap<string, string>): string {
  try {
    const walk = (current: unknown): unknown => {
      if (typeof current === "string") return idMap.get(current) ?? current;
      if (Array.isArray(current)) return current.map(walk);
      if (!isRecord(current)) return current;
      return Object.fromEntries(Object.entries(current).map(([key, item]) => [key, walk(item)]));
    };
    return JSON.stringify(walk(JSON.parse(value || "{}")));
  } catch {
    return "{}";
  }
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

/**
 * 校对单条命中。
 * `detail` 是该位置的说明，同时作为「场景 × 规则」分组的消息基干：
 * 同一分组内多条命中只保留首条的基干并追加「（共 N 处）」。
 */
interface ProofHit {
  rule: ProofRule;
  /** 段落序号（0 起）；-1 表示该规则以整场为粒度。 */
  paragraphIndex: number;
  /** 命中文本（规范化前）。 */
  matchedText: string;
  snippet: string | null;
  detail: string;
}

type ProofHits = ProofHit[];

/** 位置键使用的 32 位 FNV-1a 哈希；仅用于生成稳定短键，不承担安全职责。 */
function fnv1a32(text: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

/** 位置键的文本部分：去掉所有空白，避免排版微调导致键漂移。 */
function normalizeProofText(text: string): string {
  return text.replace(/\s+/g, "");
}

/** 关系图节点上限：默认 150，允许 20..400；越界或非法值回落到默认。 */
function clampRelationGraphLimit(value: unknown): number {
  const DEFAULT = 150;
  const MIN = 20;
  const MAX = 400;
  if (typeof value !== "number" || !Number.isFinite(value)) return DEFAULT;
  return Math.min(MAX, Math.max(MIN, Math.floor(value)));
}

/**
 * 卡片字段摘要：最多 3 项「键: 值」，供关系图节点悬浮/选中时快速辨认。
 * 只取字符串类字段，避免把长文本或二进制字段塞进图。
 */
function cardFieldSummary(fields: Record<string, unknown> | undefined): string {
  if (!fields) return "";
  const entries = Object.entries(fields)
    .filter(([, value]) => typeof value === "string" && value.trim() !== "")
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${String(value).trim()}`);
  return entries.join("；");
}

/**
 * 稳定位置键：`规则#段落序号#命中文本哈希`。
 * 段落序号让同一文本在场景内的不同段落互不牵连；
 * 忽略记录再叠加 project / scene 维度，因此跨项目、跨场景同样隔离。
 */
function proofLocationKey(rule: ProofRule, paragraphIndex: number, matchedText: string): string {
  return `${rule}#${paragraphIndex}#${fnv1a32(normalizeProofText(matchedText))}`;
}

/** 生成上下文片段：命中前后各取若干字符。 */
function proofSnippet(text: string, start: number, end: number): string {
  const radius = 8;
  const from = Math.max(0, start - radius);
  const to = Math.min(text.length, end + radius * 2);
  return `${from > 0 ? "…" : ""}${text.slice(from, to)}${to < text.length ? "…" : ""}`;
}

/** 非空子串出现次数（不重叠）。 */
function countOccurrences(haystack: string, needle: string): number {
  if (!needle) return 0;
  let count = 0;
  let index = haystack.indexOf(needle);
  while (index >= 0) {
    count += 1;
    index = haystack.indexOf(needle, index + needle.length);
  }
  return count;
}

/** 把「段落用 \n 拼接后的偏移」映射回段落序号。 */
function paragraphIndexAt(paragraphs: string[], offset: number): number {
  let cursor = 0;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const length = paragraphs[index]!.length;
    if (offset <= cursor + length) return index;
    cursor += length + 1;
  }
  return paragraphs.length - 1;
}

const HAN_CHARACTER_PATTERN = /\p{Script=Han}/u;
const HAN_ONLY_PATTERN = /^[\p{Script=Han}]+$/u;
/** 疑似错拼只比较 2..6 字的词条，避免长句掩码爆炸。 */
const PROOF_TYPO_MAX_TERM_LENGTH = 6;
/** 词条在全书出现次数低于该值时不作为错拼基准，抑制一次性噪声。 */
const PROOF_TYPO_MIN_TERM_FREQUENCY = 2;

/** 校对词表条目：一张卡片的主名与别名。 */
interface ProofDictionaryEntry {
  cardId: string;
  title: string;
  /** 主名 + 别名（去重、去空白、长度 ≥2）。 */
  variants: string[];
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
function findRepeatedChars(paragraphs: string[], out: ProofHits): void {
  const repeated = /([\p{Script=Han}])\1{2,}/gu;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    repeated.lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = repeated.exec(paragraph)) !== null) {
      out.push({
        rule: "repeatedChar",
        paragraphIndex: index,
        matchedText: match[0],
        snippet: proofSnippet(paragraph, match.index, match.index + match[0].length),
        detail: `连续重复字「${match[0].slice(0, 6)}」`
      });
    }
  }
}

/** 成对标点：括号/引号开闭数量不等（按场景统计，定位到首次出现的段落）。 */
function findUnbalancedPunctuation(paragraphs: string[], out: ProofHits): void {
  const joined = paragraphs.join("\n");
  for (const [open, close] of PROOF_PAIR_PUNCTUATION) {
    let openCount = 0;
    let closeCount = 0;
    let firstIndex = -1;
    for (let index = 0; index < joined.length; index += 1) {
      const character = joined[index]!;
      if (character === open) {
        if (firstIndex < 0) firstIndex = index;
        openCount += 1;
      } else if (character === close) {
        if (firstIndex < 0) firstIndex = index;
        closeCount += 1;
      }
    }
    if (openCount === closeCount || firstIndex < 0) continue;
    out.push({
      rule: "unbalancedPunctuation",
      paragraphIndex: paragraphIndexAt(paragraphs, firstIndex),
      matchedText: `${open}${close}`,
      snippet: proofSnippet(joined, firstIndex, firstIndex + 1),
      detail: `「${open}${close}」不配对（开 ${openCount} 个、闭 ${closeCount} 个）`
    });
  }
}

/** 异常空格：段首半角空格、连续 2+ 全角空格、半角与全角空格混用（逐段定位）。 */
function findAbnormalSpacing(paragraphs: string[], out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    if (/^[ ]/.test(paragraph)) {
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: " ",
        snippet: proofSnippet(paragraph, 0, 1),
        detail: "段落以半角空格开头"
      });
    }
    if (/　{2,}/u.test(paragraph)) {
      const position = paragraph.search(/　{2,}/u);
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: "　　",
        snippet: proofSnippet(paragraph, position, position + 2),
        detail: "段落含连续两个以上全角空格"
      });
    }
    if (/[ ]/.test(paragraph) && /　/.test(paragraph)) {
      const position = Math.min(
        paragraph.indexOf(" ") >= 0 ? paragraph.indexOf(" ") : Number.MAX_SAFE_INTEGER,
        paragraph.indexOf("　") >= 0 ? paragraph.indexOf("　") : Number.MAX_SAFE_INTEGER
      );
      out.push({
        rule: "abnormalSpacing",
        paragraphIndex: index,
        matchedText: "　",
        snippet: proofSnippet(paragraph, position, position + 1),
        detail: "段落同时出现半角与全角空格"
      });
    }
  }
}

/** 超长段落：单段字符数超过阈值。 */
function findLongParagraphs(paragraphs: string[], maxChars: number, out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (paragraph.length <= maxChars) continue;
    out.push({
      rule: "longParagraph",
      paragraphIndex: index,
      matchedText: paragraph.slice(0, 40),
      snippet: `…${paragraph.slice(0, 60)}…`,
      detail: `段落 ${paragraph.length} 字符，超过 ${maxChars} 字符`
    });
  }
}

/** 禁用词：子串命中。 */
function findBannedWords(paragraphs: string[], bannedWords: string[], out: ProofHits): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    for (const word of bannedWords) {
      if (!word) continue;
      const position = paragraph.indexOf(word);
      if (position < 0) continue;
      out.push({
        rule: "bannedWord",
        paragraphIndex: index,
        matchedText: word,
        snippet: proofSnippet(paragraph, position, position + word.length),
        detail: `命中禁用词「${word}」`
      });
    }
  }
}

/** 中英混用标点：汉字紧邻半角标点（网页粘贴/输入法残留的高频问题）。 */
function findMixedPunctuation(paragraphs: string[], out: ProofHits): void {
  // 数字间的半角点（3.5、1,000）不算；只抓汉字直接贴半角标点。
  const mixed = /[\p{Script=Han}][,.!?;:]|[,.!?;:][\p{Script=Han}]/gu;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    mixed.lastIndex = 0;
    const seen = new Set<string>();
    let match: RegExpExecArray | null;
    while ((match = mixed.exec(paragraph)) !== null) {
      if (seen.has(match[0])) continue;
      seen.add(match[0]);
      out.push({
        rule: "mixedPunctuation",
        paragraphIndex: index,
        matchedText: match[0],
        snippet: proofSnippet(paragraph, match.index, match.index + match[0].length),
        detail: "汉字紧邻半角标点（,.!?;:），疑似中英标点混用"
      });
    }
  }
}

/** 口头禅：叙述类高频副词在单个场景内出现过多（每词 ≥3 次才提示，避免噪声）。 */
const PROOF_CRUTCH_WORDS = ["突然", "顿时", "瞬间", "竟然", "居然", "仿佛", "似乎", "显然", "几乎", "一阵"];
function findCrutchWords(paragraphs: string[], out: ProofHits): void {
  const joined = paragraphs.join("\n");
  for (const word of PROOF_CRUTCH_WORDS) {
    const count = countOccurrences(joined, word);
    if (count < 3) continue;
    const firstIndex = joined.indexOf(word);
    out.push({
      rule: "crutchWord",
      // 口头禅是场景级判断，位置粒度定为「整场 + 该词」。
      paragraphIndex: -1,
      matchedText: word,
      snippet: firstIndex >= 0 ? proofSnippet(joined, firstIndex, firstIndex + word.length) : null,
      detail: `「${word}」出现 ${count} 次，注意口头禅化`
    });
  }
}

/** 连续段落同字开头：≥3 个连续非空段落首字相同（刻意排比可忽略）。 */
function findParagraphStartRepeat(paragraphs: string[], out: ProofHits): void {
  const meaningful = paragraphs
    .map((paragraph) => paragraph.trim())
    .filter((paragraph) => paragraph.length > 0);
  let runStart = 0;
  for (let index = 1; index <= meaningful.length; index += 1) {
    const sameHead =
      index < meaningful.length &&
      meaningful[index]![0] === meaningful[runStart]![0];
    if (sameHead) continue;
    const runLength = index - runStart;
    if (runLength >= 3) {
      out.push({
        rule: "paragraphStartRepeat",
        paragraphIndex: runStart,
        matchedText: meaningful[runStart]![0] ?? "",
        snippet: meaningful.slice(runStart, runStart + 2).map((paragraph) => paragraph.slice(0, 16)).join(" / "),
        detail: `${runLength} 处连续段落以同一字开头（如为刻意排比可忽略）`
      });
    }
    runStart = index;
  }
}

/**
 * 别名一致性：同一张卡片在全书被多种称呼指代时，逐个提示「少数派称呼」所在位置。
 * 主导称呼按全书出现次数决定（并列时优先卡片主名），因此不会因为一次「全名 + 简称」
 * 的正常写法就报警——只有相对罕见的称呼才需要作者确认。
 */
function findAliasInconsistency(
  paragraphs: string[],
  dictionary: ProofDictionaryEntry[],
  variantFrequency: Map<string, number>,
  out: ProofHits
): void {
  for (const entry of dictionary) {
    if (entry.variants.length < 2) continue;
    let dominant = entry.variants[0]!;
    let dominantCount = -1;
    for (const variant of entry.variants) {
      const count = variantFrequency.get(variant) ?? 0;
      if (count > dominantCount || (count === dominantCount && variant === entry.title)) {
        dominant = variant;
        dominantCount = count;
      }
    }
    if (dominantCount <= 0) continue;
    const minority = entry.variants.filter(
      (variant) => variant !== dominant && (variantFrequency.get(variant) ?? 0) > 0
    );
    if (minority.length === 0) continue;
    for (let index = 0; index < paragraphs.length; index += 1) {
      const paragraph = paragraphs[index]!;
      if (!paragraph) continue;
      for (const variant of minority) {
        const position = paragraph.indexOf(variant);
        if (position < 0) continue;
        out.push({
          rule: "aliasInconsistency",
          paragraphIndex: index,
          matchedText: variant,
          snippet: proofSnippet(paragraph, position, position + variant.length),
          detail: `「${entry.title}」全书以「${dominant}」为主，此处用了「${variant}」`
        });
      }
    }
  }
}

/**
 * 词表掩码索引：把每个词条的每个位置替换为通配符。
 * 文本侧对每个窗口生成同构掩码键即可 O(1) 找到「只差一个字」的词条，
 * 复杂度与词表规模无关（只与窗口长度相关）。
 */
function buildTypoMaskIndex(dictionary: ProofDictionaryEntry[]): Map<string, string[]> {
  const index = new Map<string, string[]>();
  for (const entry of dictionary) {
    for (const variant of entry.variants) {
      if (variant.length < 2 || variant.length > PROOF_TYPO_MAX_TERM_LENGTH) continue;
      for (let position = 0; position < variant.length; position += 1) {
        const key = `${variant.length}:${variant.slice(0, position)}*${variant.slice(position + 1)}`;
        const bucket = index.get(key);
        if (bucket) {
          if (!bucket.includes(variant)) bucket.push(variant);
        } else {
          index.set(key, [variant]);
        }
      }
    }
  }
  return index;
}

/**
 * 疑似错拼：与项目词表（卡片主名/别名）仅差一个字的词，按位置提示。
 * 词表自身的任意子串都视为合法（「洛水之蔚」里的「水之」不算错），
 * 且基准词需在全书出现 ≥2 次，避免一次性噪声。
 */
function findSuspectedTypos(
  paragraphs: string[],
  vocabulary: Set<string>,
  protectedSubstrings: Set<string>,
  maskIndex: Map<string, string[]>,
  variantFrequency: Map<string, number>,
  out: ProofHits
): void {
  for (let index = 0; index < paragraphs.length; index += 1) {
    const paragraph = paragraphs[index]!;
    if (!paragraph) continue;
    const reported = new Set<string>();
    for (let start = 0; start < paragraph.length; start += 1) {
      if (!HAN_CHARACTER_PATTERN.test(paragraph[start]!)) continue;
      for (let length = 2; length <= PROOF_TYPO_MAX_TERM_LENGTH; length += 1) {
        const end = start + length;
        if (end > paragraph.length) break;
        const candidate = paragraph.slice(start, end);
        if (!HAN_ONLY_PATTERN.test(candidate)) break;
        if (vocabulary.has(candidate) || protectedSubstrings.has(candidate)) continue;
        let best = "";
        let bestCount = 0;
        for (let position = 0; position < length; position += 1) {
          const key = `${length}:${candidate.slice(0, position)}*${candidate.slice(position + 1)}`;
          const bucket = maskIndex.get(key);
          if (!bucket) continue;
          for (const term of bucket) {
            if (term === candidate) continue;
            const count = variantFrequency.get(term) ?? 0;
            if (count > bestCount) {
              best = term;
              bestCount = count;
            }
          }
        }
        if (bestCount < PROOF_TYPO_MIN_TERM_FREQUENCY) continue;
        if (reported.has(candidate)) continue;
        reported.add(candidate);
        out.push({
          rule: "suspectedTypo",
          paragraphIndex: index,
          matchedText: candidate,
          snippet: proofSnippet(paragraph, start, end),
          detail: `疑似「${best}」的错拼（全书出现 ${bestCount} 次）`
        });
      }
    }
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
    database.exec("BEGIN IMMEDIATE");
    try {
      queueAndDeleteGlobalCard(database, card.id, new Date().toISOString());
      database.exec("COMMIT");
    } catch (error) {
      database.exec("ROLLBACK");
      throw error;
    }
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

    PRAGMA user_version = 9;
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

/** v10→v11：场景摘要与场景状态独立持久化；章节 status 保持原工作流语义。 */
function migrateSchemaV10ToV11(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
    ALTER TABLE scenes ADD COLUMN summary TEXT NOT NULL DEFAULT '';
    ALTER TABLE scenes ADD COLUMN scene_status TEXT NOT NULL DEFAULT 'planned';
    PRAGMA user_version = 11;
    COMMIT;
  `);
}

/**
 * v11→v12：校对忽略记录持久化。
 * 唯一键（project_id, scene_id, rule, location_key）保证「按位置忽略」，
 * 同文本在其它段落 / 其它场景 / 其它项目的出现不受影响；不触碰任何正文表。
 */
function migrateSchemaV11ToV12(database: Database): void {
  database.exec(`
    BEGIN IMMEDIATE;
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
    PRAGMA user_version = 12;
    COMMIT;
  `);
}

/**
 * 校对忽略表的幂等兜底。
 * 与 global_card_resources 同样属于「向后兼容扩展表」：旧库迁移链已建表，
 * 这里只负责让重复打开、异常中断后的重开都能自愈，不写 user_version。
 */
function ensureProofIgnoreSchema(database: Database): void {
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
}

/**
 * M1-E：全局卡片资产是 v10 的向后兼容扩展。
 * 旧 resources 表继续承载项目附件；新表不带 project 外键，删除项目不会级联删除共享资产。
 */
function ensureGlobalCardResourceSchema(database: Database): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS global_card_resources (
      id TEXT PRIMARY KEY,
      card_id TEXT NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
      relative_path TEXT NOT NULL UNIQUE,
      sha256 TEXT NOT NULL,
      size INTEGER NOT NULL DEFAULT 0,
      original_name TEXT,
      role TEXT NOT NULL DEFAULT 'attachment' CHECK(role IN ('attachment', 'cover')),
      created_at TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_global_card_resources_card ON global_card_resources(card_id);
    CREATE TABLE IF NOT EXISTS global_card_trash_state (
      card_id TEXT PRIMARY KEY REFERENCES cards(id) ON DELETE CASCADE,
      linked_project_ids_json TEXT NOT NULL DEFAULT '[]',
      deleted_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS global_card_resource_gc (
      relative_path TEXT PRIMARY KEY,
      queued_at TEXT NOT NULL
    );
  `);
  const columns = database.prepare("PRAGMA table_info(global_card_resources)").all() as Array<{ name: string }>;
  if (!columns.some((column) => column.name === "role")) {
    database.exec("ALTER TABLE global_card_resources ADD COLUMN role TEXT NOT NULL DEFAULT 'attachment' CHECK(role IN ('attachment', 'cover'));");
  }
  database.exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_global_card_resources_cover ON global_card_resources(card_id) WHERE role = 'cover';");
}

function removeCardFromPlanningJson(planningJson: string, cardId: string): string | null {
  const planning = JSON.parse(planningJson) as ScenePlanning;
  let changed = false;
  if (planning.perspectiveCardId === cardId) {
    delete planning.perspectiveCardId;
    changed = true;
  }
  if (planning.locationCardId === cardId) {
    delete planning.locationCardId;
    changed = true;
  }
  if (Array.isArray(planning.castCardIds) && planning.castCardIds.includes(cardId)) {
    planning.castCardIds = planning.castCardIds.filter((item) => item !== cardId);
    changed = true;
  }
  return changed ? JSON.stringify(planning) : null;
}

function queueAndDeleteGlobalCard(database: Database, cardId: string, timestamp: string): void {
  const resources = database
    .prepare("SELECT relative_path FROM global_card_resources WHERE card_id = ?")
    .all(cardId) as Array<{ relative_path: string }>;
  const queue = database.prepare("INSERT OR IGNORE INTO global_card_resource_gc(relative_path, queued_at) VALUES (?, ?)");
  for (const resource of resources) queue.run(resource.relative_path, timestamp);

  const scenes = database.prepare("SELECT id, planning_json FROM scenes").all() as Array<{ id: string; planning_json: string }>;
  const updateScene = database.prepare("UPDATE scenes SET planning_json = ?, updated_at = ?, revision = revision + 1 WHERE id = ?");
  for (const scene of scenes) {
    const updated = removeCardFromPlanningJson(scene.planning_json, cardId);
    if (updated !== null) updateScene.run(updated, timestamp, scene.id);
  }
  database.prepare("UPDATE annotations SET card_id = NULL, updated_at = ?, revision = revision + 1 WHERE card_id = ?").run(timestamp, cardId);
  database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(cardId, cardId);
  database.prepare("DELETE FROM cards WHERE id = ?").run(cardId);
}

async function drainGlobalCardResourceGc(database: Database, workspaceDirectory: string): Promise<void> {
  const rows = database.prepare("SELECT relative_path FROM global_card_resource_gc ORDER BY queued_at, relative_path").all() as Array<{ relative_path: string }>;
  const workspaceRoot = path.resolve(workspaceDirectory);
  for (const row of rows) {
    const normalized = row.relative_path.replaceAll("\\", "/");
    const target = path.resolve(workspaceRoot, normalized);
    if (!normalized.startsWith("resources/cards/") || !target.startsWith(`${workspaceRoot}${path.sep}`)) continue;
    try {
      await rm(target, { force: true });
      database.prepare("DELETE FROM global_card_resource_gc WHERE relative_path = ?").run(row.relative_path);
    } catch {
      // 保留队列项，下次打开工作区继续重试。
    }
  }
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
  /** 表存在性的惰性缓存：同一个连接内 sqlite_master 不会变化，避免逐次查询。 */
  private readonly tablePresence = new Map<string, boolean>();

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
  async read(query: ProofIgnoreListQuery): Promise<ProofIgnoreEntry[]>;
  async read(query: RelationGraphQuery): Promise<RelationGraphView>;
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
    if (runtimeQuery.kind === "proof.ignores") {
      if (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "校对忽略记录请求无效。");
      }
      try {
        return this.runProofIgnoreList(runtimeQuery.projectId);
      } catch (error) {
        if (error instanceof CreationWorkspaceError) throw error;
        throw new CreationWorkspaceError("integrity", "无法读取校对忽略记录。");
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
      if (runtimeQuery.projectId !== undefined && (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      const entity = runtimeQuery.entity;
      if (entity !== "volume" && entity !== "chapter" && entity !== "scene" && entity !== "card") {
        throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
      }
      if (typeof runtimeQuery.entityId !== "string" || !runtimeQuery.entityId.trim()) {
        throw new CreationWorkspaceError("invalid-input", "回收站影响请求无效。");
      }
      if (runtimeQuery.projectId === undefined && entity !== "card") {
        throw new CreationWorkspaceError("invalid-input", "项目回收站影响请求必须提供作品 ID。");
      }
      try {
        return this.readTrashImpact(
          typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined,
          entity,
          runtimeQuery.entityId
        );
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
      runtimeQuery.kind === "relationGraph.list" ||
      runtimeQuery.kind === "trash.list" ||
      runtimeQuery.kind === "snapshot.list"
    ) {
      try {
        if (runtimeQuery.kind === "cards.list") {
          if (
            runtimeQuery.projectId !== undefined &&
            (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())
          ) {
            throw new CreationWorkspaceError("invalid-input", "卡片读取请求无效。");
          }
          return this.listCards({
            projectId: typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined,
            cardKind:
              typeof runtimeQuery.cardKind === "string" && runtimeQuery.cardKind ? runtimeQuery.cardKind : undefined,
            search:
              typeof runtimeQuery.search === "string" && runtimeQuery.search.trim()
                ? runtimeQuery.search.trim()
                : undefined
          });
        }
        if (runtimeQuery.kind === "cardTypes.list") {
          return this.listCardTypes(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
        }
        if (runtimeQuery.kind === "relationTypes.list") {
          return this.listRelationTypes(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
        }
        if (runtimeQuery.kind === "trash.list") {
          if (runtimeQuery.projectId !== undefined && (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())) {
            throw new CreationWorkspaceError("invalid-input", "回收站读取请求无效。");
          }
          return this.trashList(typeof runtimeQuery.projectId === "string" ? runtimeQuery.projectId : undefined);
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
        if (runtimeQuery.kind === "relationGraph.list") {
          if (
            runtimeQuery.projectId !== undefined &&
            (typeof runtimeQuery.projectId !== "string" || !runtimeQuery.projectId.trim())
          ) {
            throw new CreationWorkspaceError("invalid-input", "关系图读取请求无效。");
          }
          return this.runRelationGraph(runtimeQuery as RelationGraphQuery);
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

    const hasSceneMeta = Number(this.database.pragma("user_version", { simple: true })) >= 11;
    const sceneRows = this.database
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

  private cardFromRow(row: {
    id: string;
    project_id: string | null;
    linked_project_ids_json?: string;
    usage_count?: number;
    kind: string;
    title: string;
    aliases_json: string;
    fields_json: string;
    tags_json: string;
    created_at: string;
    updated_at: string;
    revision: number;
    cover_resource_id?: string | null;
  }): CardSummary {
    return {
      id: row.id,
      projectId: row.project_id,
      linkedProjectIds: row.linked_project_ids_json
        ? (JSON.parse(row.linked_project_ids_json) as string[])
        : row.project_id
          ? [row.project_id]
          : [],
      usageCount: row.usage_count ?? (row.project_id ? 1 : 0),
      kind: row.kind,
      title: row.title,
      aliases: JSON.parse(row.aliases_json) as string[],
      fields: JSON.parse(row.fields_json) as Record<string, unknown>,
      tags: JSON.parse(row.tags_json) as string[],
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      revision: row.revision,
      coverResourceId: row.cover_resource_id ?? null
    };
  }

  /** v9 基线夹具兼容；生产 v10 始终为 true。 */
  private hasProjectCardLinks(): boolean {
    return this.database
      .prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'project_card_links'")
      .get() !== undefined;
  }

  /**
   * 是否存在全局卡片资源表（v10+）。旧库（v9 及更早）没有该表，
   * 此时 `CardSummary.coverResourceId` 恒为 null，列表缩略图降级为空态。
   */
  private hasGlobalCardResources(): boolean {
    return this.database
      .prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'global_card_resources'")
      .get() !== undefined;
  }

  private listCardTypes(legacyProjectId?: string): CardType[] {
    const builtInProjection = this.hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END";
    const legacyFilter = !this.hasProjectCardLinks() && legacyProjectId ? " WHERE project_id IS NULL OR project_id = ?" : "";
    const rows = this.database
      .prepare(
        `SELECT id, project_id, ${builtInProjection} AS is_builtin, kind, name, fields_json, sort_order, created_at, updated_at, revision
         FROM card_types${legacyFilter} ORDER BY sort_order, id`
      )
      .all(...(legacyFilter ? [legacyProjectId] : [])) as Array<{
      id: string;
      project_id: string | null;
      is_builtin: number;
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
      builtIn: row.is_builtin === 1,
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

  private listRelationTypes(legacyProjectId?: string): RelationType[] {
    const builtInProjection = this.hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END";
    const legacyFilter = !this.hasProjectCardLinks() && legacyProjectId ? " WHERE project_id IS NULL OR project_id = ?" : "";
    const rows = this.database
      .prepare(
        `SELECT id, project_id, ${builtInProjection} AS is_builtin, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision
         FROM relation_types${legacyFilter} ORDER BY created_at, id`
      )
      .all(...(legacyFilter ? [legacyProjectId] : [])) as Array<{
      id: string;
      project_id: string | null;
      is_builtin: number;
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
      builtIn: row.is_builtin === 1,
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

  private listCards(query: { projectId?: string; cardKind?: string; search?: string }): CardSummary[] {
    const params: unknown[] = [];
    let sql: string;
    // 封面资源 ID 随卡片一起查出，避免列表渲染时每张卡再查一次（N+1）。
    // 数据库层 `idx_global_card_resources_cover` 保证一张卡最多一个封面，故无需排序。
    const coverColumn = this.hasGlobalCardResources()
      ? `,
          (SELECT r.id FROM global_card_resources r WHERE r.card_id = c.id AND r.role = 'cover' LIMIT 1) AS cover_resource_id`
      : "";
    if (this.hasProjectCardLinks()) {
      const projection = query.projectId ? "?" : "NULL";
      if (query.projectId) params.push(query.projectId);
      sql = `SELECT c.id, ${projection} AS project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
          c.created_at, c.updated_at, c.revision,
          COALESCE((SELECT json_group_array(project_id) FROM (
            SELECT project_id FROM project_card_links WHERE card_id = c.id ORDER BY project_id
          )), '[]') AS linked_project_ids_json,
          (SELECT count(*) FROM project_card_links WHERE card_id = c.id) AS usage_count${coverColumn}
        FROM cards c WHERE c.deleted_at IS NULL`;
      if (query.projectId) {
        sql += " AND EXISTS (SELECT 1 FROM project_card_links pcl WHERE pcl.project_id = ? AND pcl.card_id = c.id)";
        params.push(query.projectId);
      }
    } else {
      sql = `SELECT c.id, c.project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
        c.created_at, c.updated_at, c.revision${coverColumn} FROM cards c WHERE c.deleted_at IS NULL`;
      if (query.projectId) {
        sql += " AND c.project_id = ?";
        params.push(query.projectId);
      }
    }
    if (query.cardKind) {
      sql += " AND c.kind = ?";
      params.push(query.cardKind);
    }
    if (query.search) {
      sql += " AND (c.title LIKE ? OR c.aliases_json LIKE ? OR c.fields_json LIKE ?)";
      params.push(`%${query.search}%`, `%${query.search}%`, `%${query.search}%`);
    }
    sql += " ORDER BY c.updated_at DESC, c.id DESC";
    const rows = this.database.prepare(sql).all(...params) as Array<{
      id: string;
      project_id: string | null;
      linked_project_ids_json?: string;
      usage_count?: number;
      kind: string;
      title: string;
      aliases_json: string;
      fields_json: string;
      tags_json: string;
      created_at: string;
      updated_at: string;
      revision: number;
      cover_resource_id?: string | null;
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

    const rows = this.database.prepare(sql).all(...params) as Array<{
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
      ? this.loadProofDictionary(projectId)
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
    const ignoreRows = this.loadProofIgnores(projectId);
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
      const counts = this.database
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

  private hasTable(name: string): boolean {
    const cached = this.tablePresence.get(name);
    if (cached !== undefined) return cached;
    const found =
      this.database.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").get(name) !== undefined;
    this.tablePresence.set(name, found);
    return found;
  }

  /** 项目内卡片词表（主名 + 别名），用于别名一致性与疑似错拼。 */
  private loadProofDictionary(projectId: string): ProofDictionaryEntry[] {
    const linked = this.hasTable("project_card_links");
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
      ? this.database.prepare(sql).all(projectId, projectId)
      : this.database.prepare(sql).all(projectId)) as Array<{
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
  private loadProofIgnores(projectId: string): Array<{
    id: string;
    sceneId: string;
    rule: string;
    locationKey: string;
  }> {
    if (!this.hasTable("proof_ignores")) return [];
    const rows = this.database
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

  private runProofIgnoreList(projectId: string): ProofIgnoreEntry[] {
    const id = validateId(projectId, "作品");
    this.requireProject(id);
    if (!this.hasTable("proof_ignores")) return [];
    const rows = this.database
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
  private proofIgnore(command: ProofIgnoreCommand): ProofIgnoreResult {
    const projectId = validateId(command.projectId, "作品");
    this.requireProject(projectId);
    const sceneId = validateId(command.sceneId, "场景");
    const scene = this.requireScene(sceneId);
    this.assertSameProject(projectId, scene.project_id, "场景");
    const rule = validateProofRule(command.rule);
    const locationKey = typeof command.locationKey === "string" ? command.locationKey.trim() : "";
    if (!locationKey || locationKey.length > 200) {
      throw new CreationWorkspaceError("invalid-input", "校对忽略位置无效。");
    }
    const matchedText = typeof command.matchedText === "string" ? command.matchedText.slice(0, 200) : "";
    const note = typeof command.note === "string" ? command.note.slice(0, 200) : "";
    this.ensureProofIgnoreTable();
    const timestamp = new Date().toISOString();
    const existing = this.database
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
    this.database
      .prepare(
        `INSERT INTO proof_ignores (id, project_id, scene_id, rule, location_key, matched_text, note, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .run(id, projectId, sceneId, rule, locationKey, matchedText, note, timestamp);
    this.emitCommitted({
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
  private proofUnignore(command: ProofUnignoreCommand): ProofIgnoreResult {
    const projectId = validateId(command.projectId, "作品");
    this.requireProject(projectId);
    let ids: string[] = [];
    if (command.ignoreId !== undefined) {
      const ignoreId = validateId(command.ignoreId, "忽略记录");
      const row = this.database
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
        this.database
          .prepare(
            "SELECT id FROM proof_ignores WHERE project_id = ? AND scene_id = ? AND rule = ? AND location_key = ?"
          )
          .all(projectId, sceneId, rule, locationKey) as Array<{ id: string }>
      ).map((row) => row.id);
    }
    const remove = this.database.prepare("DELETE FROM proof_ignores WHERE id = ? AND project_id = ?");
    let removed = 0;
    for (const id of ids) removed += remove.run(id, projectId).changes;
    const timestamp = new Date().toISOString();
    this.emitCommitted({
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
  private ensureProofIgnoreTable(): void {
    if (this.hasTable("proof_ignores")) return;
    this.database.exec(`
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
    this.tablePresence.set("proof_ignores", true);
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
        const existing = this.database
          .prepare(`SELECT id FROM cards WHERE id IN (${placeholders}) AND deleted_at IS NULL`)
          .all(...cardIds) as Array<{ id: string }>;
        if (existing.length !== cardIds.size) throw new CreationWorkspaceError("not-found", "引用的卡片不存在。");
        const ownershipSql = this.hasProjectCardLinks()
          ? `SELECT card_id AS id FROM project_card_links WHERE card_id IN (${placeholders}) AND project_id = ?`
          : `SELECT id FROM cards WHERE id IN (${placeholders}) AND project_id = ? AND deleted_at IS NULL`;
        const linked = this.database.prepare(ownershipSql).all(...cardIds, projectId) as Array<{ id: string }>;
        if (linked.length !== cardIds.size) {
          throw new CreationWorkspaceError("invalid-input", "引用的卡片不属于该作品。");
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

  private runSceneUpdateMeta(command: SceneUpdateMetaCommand): SceneUpdateMetaResult {
    const sceneId = validateId(command.sceneId, "场景");
    const baseRevision = validateBaseRevision(command.baseRevision);
    if (typeof command.summary !== "string" || command.summary.length > 2000) {
      throw new CreationWorkspaceError("invalid-input", "场景摘要不能超过 2000 个字符。");
    }
    if (!SCENE_STATUSES.has(command.status)) {
      throw new CreationWorkspaceError("invalid-input", "场景状态无效。");
    }
    const summary = command.summary.trim();
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      const scene = this.database
        .prepare(`SELECT s.revision, c.project_id FROM scenes s JOIN chapters c ON c.id = s.chapter_id
          WHERE s.id = ? AND s.deleted_at IS NULL AND c.deleted_at IS NULL`)
        .get(sceneId) as { revision: number; project_id: string } | undefined;
      if (!scene) throw new CreationWorkspaceError("not-found", "场景不存在。");
      if (scene.revision !== baseRevision) {
        throw new CreationWorkspaceError("revision-mismatch", "场景已被更新，请重新读取后再保存摘要与状态。");
      }
      const revision = scene.revision + 1;
      this.database.prepare(
        "UPDATE scenes SET summary = ?, scene_status = ?, updated_at = ?, revision = ? WHERE id = ?"
      ).run(summary, command.status, timestamp, revision, sceneId);
      const logged = this.database.prepare(
        "INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)"
      ).run(scene.project_id, "scene.updateMeta", JSON.stringify([{ entity: "scene", id: sceneId, action: "updated", revision }]), timestamp);
      this.database.exec("COMMIT");
      this.emitCommitted({
        kind: "committed",
        sequence: Number(logged.lastInsertRowid),
        projectId: scene.project_id,
        commandType: "scene.updateMeta",
        changes: [{ entity: "scene", id: sceneId, action: "updated", revision }]
      });
      return {
        commandType: "scene.updateMeta",
        sequence: Number(logged.lastInsertRowid),
        projectId: scene.project_id,
        sceneId,
        revision,
        updatedAt: timestamp
      };
    } catch (error) {
      try { this.database.exec("ROLLBACK"); } catch { /* transaction may already be closed */ }
      if (error instanceof CreationWorkspaceError) throw error;
      throw new CreationWorkspaceError("integrity", "无法更新场景摘要与状态。");
    }
  }

  private runStatsView(projectId: string): ProjectStatsView | null {
    return this.statsSessions.runStatsView(projectId);
  }

  private readCard(cardId: string): CardSummary | null {
    const sql = this.hasProjectCardLinks()
      ? `SELECT c.id, NULL AS project_id, c.kind, c.title, c.aliases_json, c.fields_json, c.tags_json,
           c.created_at, c.updated_at, c.revision,
           COALESCE((SELECT json_group_array(project_id) FROM (
             SELECT project_id FROM project_card_links WHERE card_id = c.id ORDER BY project_id
           )), '[]') AS linked_project_ids_json,
           (SELECT count(*) FROM project_card_links WHERE card_id = c.id) AS usage_count
         FROM cards c WHERE c.id = ? AND c.deleted_at IS NULL`
      : `SELECT id, project_id, kind, title, aliases_json, fields_json, tags_json, created_at, updated_at, revision
         FROM cards WHERE id = ? AND deleted_at IS NULL`;
    const row = this.database
      .prepare(sql)
      .get(cardId) as
      | {
          id: string;
          project_id: string | null;
          linked_project_ids_json?: string;
          usage_count?: number;
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
      project_id: string | null;
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
      FROM card_relations r
      JOIN cards source_card ON source_card.id = r.from_card_id AND source_card.deleted_at IS NULL
      JOIN cards target_card ON target_card.id = r.to_card_id AND target_card.deleted_at IS NULL
      LEFT JOIN relation_types rt ON rt.id = r.relation_type`;
    const outgoing = (
      this.database.prepare(`${base} WHERE r.from_card_id = ? ORDER BY r.created_at, r.id`).all(cardId) as Array<{
        id: string;
        project_id: string | null;
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
        project_id: string | null;
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

  /**
   * 关系图整图（Stage 4-F）：一次性取回节点与连线，避免 UI 端按卡片逐个拉关系（N+1）。
   *
   * 边界：关系是**全局卡片资产**。给定 projectId 时不改变关系本身，只把节点收敛到
   * 「该项目已关联卡片」——即引用投影。一端在项目外的关系**不绘制**，
   * 但条数必须显式回传（hiddenRelationCount），避免用户以为关系丢了。
   */
  private runRelationGraph(query: RelationGraphQuery): RelationGraphView {
    const scope: "global" | "project" =
      typeof query.projectId === "string" && query.projectId.trim() !== "" ? "project" : "global";
    const projectId = scope === "project" ? (query.projectId as string).trim() : null;
    const limit = clampRelationGraphLimit(query.limit);

    const cards = this.listCards(projectId ? { projectId } : {});
    const candidateIds = new Set(cards.map((card) => card.id));

    const rows = this.database
      .prepare(
        `SELECT r.id, r.from_card_id, r.to_card_id, r.relation_type, r.note,
                rt.name AS relation_name, rt.forward_name, rt.reverse_name
         FROM card_relations r
         JOIN cards source_card ON source_card.id = r.from_card_id AND source_card.deleted_at IS NULL
         JOIN cards target_card ON target_card.id = r.to_card_id AND target_card.deleted_at IS NULL
         LEFT JOIN relation_types rt ON rt.id = r.relation_type
         ORDER BY r.created_at, r.id`
      )
      .all() as Array<{
      id: string;
      from_card_id: string;
      to_card_id: string;
      relation_type: string;
      note: string | null;
      relation_name: string | null;
      forward_name: string | null;
      reverse_name: string | null;
    }>;

    const degree = new Map<string, number>();
    const bump = (cardId: string): void => {
      degree.set(cardId, (degree.get(cardId) ?? 0) + 1);
    };
    let hiddenRelationCount = 0;
    const scopedRows: typeof rows = [];
    for (const row of rows) {
      const fromInside = candidateIds.has(row.from_card_id);
      const toInside = candidateIds.has(row.to_card_id);
      if (!fromInside && !toInside) continue; // 与本次范围无关，既不画也不计入
      if (fromInside && toInside) {
        scopedRows.push(row);
        bump(row.from_card_id);
        bump(row.to_card_id);
      } else {
        hiddenRelationCount += 1; // 一端在范围外：不绘制，但显式计数
      }
    }

    // 节点上限：按度数降序保留（关系最密的卡片优先），保证截断可预期、可复现。
    const ordered = [...cards].sort((a, b) => {
      const delta = (degree.get(b.id) ?? 0) - (degree.get(a.id) ?? 0);
      if (delta !== 0) return delta;
      if (a.title !== b.title) return a.title < b.title ? -1 : 1;
      return a.id < b.id ? -1 : 1;
    });
    const kept = ordered.slice(0, limit);
    const keptIds = new Set(kept.map((card) => card.id));
    const truncatedNodeCount = Math.max(0, ordered.length - kept.length);

    // 指向被裁掉节点的关系同样不绘制：合并进 hiddenRelationCount（语义见类型注释）。
    const edges: RelationGraphEdge[] = [];
    for (const row of scopedRows) {
      if (!keptIds.has(row.from_card_id) || !keptIds.has(row.to_card_id)) {
        hiddenRelationCount += 1;
        continue;
      }
      edges.push({
        id: row.id,
        fromCardId: row.from_card_id,
        toCardId: row.to_card_id,
        relationTypeId: row.relation_type,
        relationName: row.relation_name ?? row.relation_type,
        forwardName: row.forward_name ?? row.relation_type,
        reverseName: row.reverse_name ?? row.forward_name ?? row.relation_type,
        note: row.note
      });
    }

    const typeNameOf = new Map(this.listCardTypes().map((type) => [type.kind, type.name]));
    const nodes: RelationGraphNode[] = kept.map((card) => ({
      cardId: card.id,
      title: card.title,
      kind: card.kind,
      kindName: typeNameOf.get(card.kind) ?? card.kind,
      aliases: [...card.aliases],
      summary: cardFieldSummary(card.fields),
      degree: degree.get(card.id) ?? 0
    }));

    const kindCounts = new Map<string, number>();
    const relationCounts = new Map<string, number>();
    for (const node of nodes) kindCounts.set(node.kind, (kindCounts.get(node.kind) ?? 0) + 1);
    for (const edge of edges) relationCounts.set(edge.relationTypeId, (relationCounts.get(edge.relationTypeId) ?? 0) + 1);
    const relationNameOf = new Map(
      this.listRelationTypes().map((type) => [type.id, type] as const)
    );

    return {
      scope,
      projectId,
      nodes,
      edges,
      kindFacets: [...kindCounts.entries()]
        .map(([kind, count]) => ({ kind, name: typeNameOf.get(kind) ?? kind, count }))
        .sort((a, b) => b.count - a.count || (a.kind < b.kind ? -1 : 1)),
      relationFacets: [...relationCounts.entries()]
        .map(([id, count]) => {
          const type = relationNameOf.get(id);
          return {
            id,
            name: type?.name ?? id,
            forwardName: type?.forwardName ?? id,
            reverseName: type?.reverseName ?? type?.forwardName ?? id,
            count
          };
        })
        .sort((a, b) => b.count - a.count || (a.id < b.id ? -1 : 1)),
      truncatedNodeCount,
      hiddenRelationCount,
      isolatedNodeCount: nodes.filter((node) => node.degree === 0).length
    };
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
    const sceneRows = this.database
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
  async transact(command: CardCommand): Promise<CreationStructureResult | CardLinkResult>;
  async transact(command: HistoryCommand): Promise<CreationStructureResult>;
  async transact(command: ReplaceApplyCommand): Promise<ReplaceApplyResult>;
  async transact(command: SessionReportCommand | SessionDeleteCommand): Promise<SessionReportResult>;
  async transact(command: InboxCreateCommand | InboxUpdateCommand | InboxDeleteCommand): Promise<InboxItemResult>;
  async transact(command: ProjectImportDraftCommand): Promise<ProjectImportDraftResult>;
  async transact(command: ProjectBundleImportCommand): Promise<ProjectBundleImportResult>;
  async transact(command: AnnotationCreateCommand | AnnotationUpdateCommand | AnnotationDeleteCommand): Promise<AnnotationResult>;
  async transact(command: ResourceAttachCommand | ResourceDetachCommand): Promise<ResourceResult>;
  async transact(command: SceneUpdatePlanningCommand): Promise<SceneUpdatePlanningResult>;
  async transact(command: SceneUpdateMetaCommand): Promise<SceneUpdateMetaResult>;
  async transact(command: ProofIgnoreCommand | ProofUnignoreCommand): Promise<ProofIgnoreResult>;
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
    if (command.type === "scene.updateMeta") {
      return this.runSceneUpdateMeta(command as SceneUpdateMetaCommand);
    }
    if (command.type === "proof.ignore") {
      return this.proofIgnore(command as ProofIgnoreCommand);
    }
    if (command.type === "proof.unignore") {
      return this.proofUnignore(command as ProofUnignoreCommand);
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
    const cardTypes = this.database
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
    const relationTypes = this.database
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
    const cards = this.database
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
    const relations = this.database
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

  async previewProjectBundleImport(data: ProjectBundleData): Promise<ProjectBundleImportPreview> {
    this.assertOpen();
    if (!data || typeof data !== "object" || (data.formatVersion !== 1 && data.formatVersion !== 2)) {
      throw new CreationWorkspaceError("invalid-input", "项目包格式无效或版本不受支持。");
    }
    const projectTitle = typeof data.project?.title === "string" ? data.project.title.trim() : "";
    if (!projectTitle) throw new CreationWorkspaceError("invalid-input", "项目包缺少作品名称。");
    const incomingIds = new Set<string>();
    const identicalCardIds: string[] = [];
    const conflicts: ProjectBundleImportPreview["conflicts"] = [];
    const findCard = this.database.prepare(
      "SELECT id, kind, title, aliases_json, fields_json, tags_json, content_json, deleted_at FROM cards WHERE id = ?"
    );
    const findCardResources = this.database.prepare(
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

  private importProjectBundle(command: ProjectBundleImportCommand): ProjectBundleImportResult {
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
      this.database.exec("BEGIN IMMEDIATE");
      const existing = this.database.prepare("SELECT id FROM projects WHERE id = ?").get(projectId);
      if (existing) throw new CreationWorkspaceError("conflict", `项目 ${projectId} 已存在，无法导入。`);
      this.database
        .prepare("INSERT INTO projects(id, title, setup_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?)")
        .run(projectId, title, JSON.stringify(data.project.setup ?? defaultProjectSetup()), data.project.createdAt ?? timestamp, data.project.updatedAt ?? timestamp, data.project.revision ?? 1);

      const incomingCardIds = new Set<string>();
      const targetCardIds = new Set<string>();
      const cardIdMap = new Map<string, string>();
      const cardMappings: ProjectBundleCardMapping[] = [];
      const cardsToInsert: Array<{ card: ProjectBundleData["cards"][number]; id: string }> = [];
      const findStoredCard = this.database.prepare(
        "SELECT id, kind, title, aliases_json, fields_json, tags_json, content_json, deleted_at FROM cards WHERE id = ?"
      );
      const findStoredCardResources = this.database.prepare(
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
        if (targetCardIds.has(copyId) || this.database.prepare("SELECT 1 FROM cards WHERE id = ?").get(copyId)) {
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

      const insertVolume = this.database.prepare("INSERT INTO volumes(id, project_id, title, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?)");
      const volumeSourceIds = new Set<string>();
      const volumeIds = new Set<string>();
      const volumeIdMap = new Map<string, string>();
      for (const volume of data.volumes ?? []) {
        const sourceId = typeof volume.id === "string" && volume.id.startsWith("volume-") ? volume.id : `volume-${randomUUID()}`;
        if (volumeSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复卷 ID。");
        volumeSourceIds.add(sourceId);
        const id = this.database.prepare("SELECT 1 FROM volumes WHERE id = ?").get(sourceId) ? `volume-${randomUUID()}` : sourceId;
        volumeIds.add(id);
        volumeIdMap.set(sourceId, id);
        insertVolume.run(id, projectId, volume.title, volume.sortOrder ?? 0, volume.createdAt ?? timestamp, volume.updatedAt ?? timestamp, volume.revision ?? 1);
      }
      if (volumeIds.size === 0) throw new CreationWorkspaceError("invalid-input", "项目包不包含任何卷。");

      const insertChapter = this.database.prepare("INSERT INTO chapters(id, project_id, volume_id, title, sort_order, status, numbering_kind, custom_number, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const chapterSourceIds = new Set<string>();
      const chapterIds = new Set<string>();
      const chapterIdMap = new Map<string, string>();
      for (const chapter of data.chapters ?? []) {
        const sourceId = typeof chapter.id === "string" && chapter.id.startsWith("chapter-") ? chapter.id : `chapter-${randomUUID()}`;
        if (chapterSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复章节 ID。");
        chapterSourceIds.add(sourceId);
        const id = this.database.prepare("SELECT 1 FROM chapters WHERE id = ?").get(sourceId) ? `chapter-${randomUUID()}` : sourceId;
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

      const insertScene = this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, han_count, punct_count, non_ws_count, planning_json, summary, scene_status, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const sceneSourceIds = new Set<string>();
      const sceneIds = new Set<string>();
      const sceneIdMap = new Map<string, string>();
      for (const scene of data.scenes ?? []) {
        const sourceId = typeof scene.id === "string" && scene.id.startsWith("scene-") ? scene.id : `scene-${randomUUID()}`;
        if (sceneSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复场景 ID。");
        sceneSourceIds.add(sourceId);
        const id = this.database.prepare("SELECT 1 FROM scenes WHERE id = ?").get(sourceId) ? `scene-${randomUUID()}` : sourceId;
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

      const insertCardType = this.database.prepare("INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const cardType of data.cardTypes ?? []) {
        const id = typeof cardType.id === "string" && cardType.id.startsWith("card-type-") ? cardType.id : `card-type-${randomUUID()}`;
        const existingType = this.database.prepare(
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

      const insertRelationType = this.database.prepare("INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      const relationTypeIdMap = new Map<string, string>();
      for (const relationType of data.relationTypes ?? []) {
        const id = typeof relationType.id === "string" && relationType.id.startsWith("relation-type-") ? relationType.id : `relation-type-${randomUUID()}`;
        const existingType = this.database.prepare(
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

      const insertCard = this.database.prepare("INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
      for (const { card, id } of cardsToInsert) {
        insertCard.run(id, projectId, card.kind, card.title, card.aliasesJson ?? "[]", card.fieldsJson ?? "{}", card.tagsJson ?? "[]", card.contentJson ?? "{}", card.createdAt ?? timestamp, card.updatedAt ?? timestamp, card.revision ?? 1);
      }
      for (const card of data.cards ?? []) {
        const id = cardIdMap.get(card.id)!;
        this.database
          .prepare("INSERT OR IGNORE INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, id, card.createdAt ?? timestamp);
      }

      const insertRelation = this.database.prepare("INSERT INTO card_relations(id, project_id, from_card_id, to_card_id, relation_type, note, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
      for (const relation of data.relations ?? []) {
        const sourceId = typeof relation.id === "string" && relation.id.startsWith("relation-") ? relation.id : `relation-${randomUUID()}`;
        const fromCardId = cardIdMap.get(relation.fromCardId);
        const toCardId = cardIdMap.get(relation.toCardId);
        if (!fromCardId || !toCardId) {
          throw new CreationWorkspaceError("invalid-input", "项目包关系引用了不存在的卡片。");
        }
        const relationTypeId = relationTypeIdMap.get(relation.relationType) ?? relation.relationType;
        const existingRelation = this.database.prepare(
          "SELECT from_card_id, to_card_id, relation_type, note FROM card_relations WHERE id = ?"
        ).get(sourceId) as { from_card_id: string; to_card_id: string; relation_type: string; note: string | null } | undefined;
        if (existingRelation && existingRelation.from_card_id === fromCardId && existingRelation.to_card_id === toCardId &&
          existingRelation.relation_type === relationTypeId && existingRelation.note === (relation.note ?? null)) {
          continue;
        }
        const id = existingRelation ? `relation-${randomUUID()}` : sourceId;
        insertRelation.run(id, projectId, fromCardId, toCardId, relationTypeId, relation.note ?? null, relation.createdAt ?? timestamp);
      }

      const insertSnapshot = this.database.prepare("INSERT INTO snapshots(id, project_id, subject_type, subject_id, payload_json, created_at) VALUES (?, ?, ?, ?, ?, ?)");
      const allIdMap = new Map<string, string>([
        ...volumeIdMap,
        ...chapterIdMap,
        ...sceneIdMap,
        ...cardIdMap
      ]);
      for (const snapshot of data.snapshots ?? []) {
        const sourceId = typeof snapshot.id === "string" && snapshot.id.startsWith("snapshot-") ? snapshot.id : `snapshot-${randomUUID()}`;
        const id = this.database.prepare("SELECT 1 FROM snapshots WHERE id = ?").get(sourceId) ? `snapshot-${randomUUID()}` : sourceId;
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

      const insertResource = this.database.prepare(
        "INSERT INTO resources(id, project_id, card_id, relative_path, sha256, size, original_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const insertGlobalResource = this.database.prepare(
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
        const existingResourceId = this.database.prepare(
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
      const insertAnnotation = this.database.prepare(
        "INSERT INTO annotations(id, project_id, scene_id, card_id, anchor_json, note, status, revision, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
      );
      const annotationSourceIds = new Set<string>();
      const annotationIds = new Set<string>();
      for (const annotation of annotationsToImport) {
        const sourceId = typeof annotation.id === "string" && annotation.id.startsWith("annotation-") ? annotation.id : `annotation-${randomUUID()}`;
        if (annotationSourceIds.has(sourceId)) throw new CreationWorkspaceError("invalid-input", "项目包包含重复批注 ID。");
        annotationSourceIds.add(sourceId);
        const id = this.database.prepare("SELECT 1 FROM annotations WHERE id = ?").get(sourceId) ? `annotation-${randomUUID()}` : sourceId;
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
      const logged = this.database
        .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
        .run(projectId, "project.bundle.import", JSON.stringify(changes), timestamp);
      this.database.exec("COMMIT");
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
      projectId: string | null;
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

  private linkedProjectIds(cardId: string): string[] {
    if (!this.hasProjectCardLinks()) {
      const row = this.database.prepare("SELECT project_id FROM cards WHERE id = ?").get(cardId) as
        | { project_id: string | null }
        | undefined;
      return row?.project_id ? [row.project_id] : [];
    }
    return (
      this.database
        .prepare("SELECT project_id FROM project_card_links WHERE card_id = ? ORDER BY project_id")
        .all(cardId) as Array<{ project_id: string }>
    ).map((row) => row.project_id);
  }

  private touchLinkedProjects(cardId: string, timestamp: string): void {
    for (const projectId of this.linkedProjectIds(cardId)) this.touchProject(projectId, timestamp);
  }

  private readWorkflow(projectId: string): string[] {
    const project = this.database
      .prepare("SELECT setup_json FROM projects WHERE id = ?")
      .get(projectId) as { setup_json: string } | undefined;
    if (!project) throw new CreationWorkspaceError("not-found", "作品不存在。");
    return parseStoredSetup(project.setup_json).chapterWorkflow;
  }

  private executeCardCommand(command: CardCommand): CreationStructureResult | CardLinkResult { switch (command.type) {
    case "cardType.create": return this.createCardType(command);
    case "cardType.update": return this.updateCardType(command);
    case "cardType.delete": return this.deleteCardType(command);
    case "relationType.create": return this.createRelationType(command);
    case "relationType.update": return this.updateRelationType(command);
    case "relationType.delete": return this.deleteRelationType(command);
    case "card.create": return this.createCard(command);
    case "card.update": return this.updateCard(command);
    case "card.delete": return this.deleteCard(command);
    case "card.link": return this.mutateCardLink(command.projectId, command.cardId, true);
    case "card.unlink": return this.mutateCardLink(command.projectId, command.cardId, false);
    case "cardRelation.create": return this.createCardRelation(command);
    case "cardRelation.delete": return this.deleteCardRelation(command);
} throw new CreationWorkspaceError("invalid-input", "不支持的卡片命令。"); }
  private requireCard(cardId: string): {
    id: string;
    project_id: string | null;
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
          project_id: string | null;
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

  private resolveCardTypeFields(_projectId: string, kind: string): CardFieldSchema[] {
    const builtInOrder = this.hasProjectCardLinks() ? "is_builtin DESC" : "(project_id IS NULL) DESC";
    const type = this.database
      .prepare(
        `SELECT fields_json FROM card_types WHERE kind = ? ORDER BY ${builtInOrder}, id LIMIT 1`
      )
      .get(kind) as { fields_json: string } | undefined;
    if (!type) throw new CreationWorkspaceError("invalid-input", `卡片类型“${kind}”不存在。`);
    try {
      return JSON.parse(type.fields_json) as CardFieldSchema[];
    } catch {
      throw new CreationWorkspaceError("integrity", "卡片类型字段数据损坏。");
    }
  }

  private createCardType(command: CardTypeCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const name = validateTitle(command.name, "卡片类型名");
    const fields = validateCardFieldSchemaList(command.fields);
    const kind = `custom-${randomUUID().slice(0, 8)}`;
    const typeId = `card-type-${randomUUID()}`;
    return this.runStructureTransaction("cardType.create", (timestamp) => {
      if (projectId) this.requireProject(projectId);
      const maxOrder = this.database
        .prepare("SELECT coalesce(max(sort_order), -1) AS m FROM card_types")
        .get() as { m: number };
      const insertSql = this.hasProjectCardLinks()
        ? "INSERT INTO card_types(id, project_id, is_builtin, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)"
        : "INSERT INTO card_types(id, project_id, kind, name, fields_json, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
      this.database
        .prepare(insertSql)
        .run(typeId, projectId, kind, name, JSON.stringify(fields), maxOrder.m + 1, timestamp, timestamp);
      if (projectId) this.touchProject(projectId, timestamp);
      return {
        projectId: null,
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
    is_builtin: number;
    revision: number;
} { const builtInProjection = this.hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"; const type = this.database.prepare(`SELECT id, project_id, ${builtInProjection} AS is_builtin, kind, name, fields_json, revision FROM card_types WHERE id = ?`).get(cardTypeId) as {
    id: string;
    project_id: string | null;
    kind: string;
    name: string;
    fields_json: string;
    is_builtin: number;
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
        if (type.is_builtin === 1) {
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
        const cards = this.database.prepare("SELECT fields_json FROM cards WHERE kind = ? AND deleted_at IS NULL").all(type.kind) as Array<{
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
        if (type.project_id) this.touchProject(type.project_id, timestamp);
        return { projectId: null, entityId: cardTypeId, revision, changes: [{ entity: "cardType", id: cardTypeId, action: "updated", revision }] };
    });
}

  private deleteCardType(command: CardTypeDeleteCommand): CreationStructureResult { const cardTypeId = validateId(command.cardTypeId, "卡片类型"); const baseRevision = validateBaseRevision(command.baseRevision); return this.runStructureTransaction("cardType.delete", (timestamp) => { const type = this.requireCardTypeRow(cardTypeId); if (type.is_builtin === 1) {
    throw new CreationWorkspaceError("conflict", "内置卡片类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "卡片类型已被更新，请重新读取后再操作。");
} const used = this.database.prepare("SELECT count(*) AS count FROM cards WHERE kind = ? AND deleted_at IS NULL").get(type.kind) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "卡片类型仍被卡片使用，不可删除。");
} this.database.prepare("DELETE FROM card_types WHERE id = ?").run(cardTypeId); if (type.project_id) this.touchProject(type.project_id, timestamp); return { projectId: null, entityId: cardTypeId, revision: type.revision + 1, changes: [{ entity: "cardType", id: cardTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

  private requireRelationTypeRow(relationTypeId: string): {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    is_builtin: number;
    revision: number;
} { const builtInProjection = this.hasProjectCardLinks() ? "is_builtin" : "CASE WHEN project_id IS NULL THEN 1 ELSE 0 END"; const type = this.database.prepare(`SELECT id, project_id, ${builtInProjection} AS is_builtin, name, from_kinds_json, to_kinds_json, revision FROM relation_types WHERE id = ?`).get(relationTypeId) as {
    id: string;
    project_id: string | null;
    name: string;
    from_kinds_json: string;
    to_kinds_json: string;
    is_builtin: number;
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
        if (type.is_builtin === 1) {
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
        if (type.project_id) this.touchProject(type.project_id, timestamp);
        return { projectId: null, entityId: relationTypeId, revision, changes: [{ entity: "relationType", id: relationTypeId, action: "updated", revision }] };
    });
}

  private deleteRelationType(command: RelationTypeDeleteCommand): CreationStructureResult { const relationTypeId = validateId(command.relationTypeId, "关系类型"); const baseRevision = validateBaseRevision(command.baseRevision); return this.runStructureTransaction("relationType.delete", (timestamp) => { const type = this.requireRelationTypeRow(relationTypeId); if (type.is_builtin === 1) {
    throw new CreationWorkspaceError("conflict", "内置关系类型为只读，不可删除。");
} if (type.revision !== baseRevision) {
    throw new CreationWorkspaceError("revision-mismatch", "关系类型已被更新，请重新读取后再操作。");
} const used = this.database.prepare("SELECT count(*) AS count FROM card_relations WHERE relation_type = ?").get(relationTypeId) as {
    count: number;
}; if (used.count > 0) {
    throw new CreationWorkspaceError("conflict", "关系类型仍被关系实例使用，不可删除。");
} this.database.prepare("DELETE FROM relation_types WHERE id = ?").run(relationTypeId); if (type.project_id) this.touchProject(type.project_id, timestamp); return { projectId: null, entityId: relationTypeId, revision: type.revision + 1, changes: [{ entity: "relationType", id: relationTypeId, action: "deleted", revision: type.revision + 1 }] }; }); }

  private createRelationType(command: RelationTypeCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const forwardName = validateTitle(command.forwardName, "关系名称", 50);
    const reverseName = validateTitle(command.reverseName, "反向关系名称", 50);
    const fromKinds = validateStringList(command.fromKinds, "起点卡片类型", 50);
    const toKinds = validateStringList(command.toKinds, "终点卡片类型", 50);
    const relationTypeId = `relation-type-${randomUUID()}`;
    return this.runStructureTransaction("relationType.create", (timestamp) => {
      if (projectId) this.requireProject(projectId);
      const insertSql = this.hasProjectCardLinks()
        ? "INSERT INTO relation_types(id, project_id, is_builtin, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?, ?)"
        : "INSERT INTO relation_types(id, project_id, name, forward_name, reverse_name, from_kinds_json, to_kinds_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
      this.database
        .prepare(insertSql)
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
      if (projectId) this.touchProject(projectId, timestamp);
      return {
        projectId: null,
        entityId: relationTypeId,
        revision: 1,
        changes: [{ entity: "relationType", id: relationTypeId, action: "created", revision: 1 }]
      };
    });
  }

  private createCard(command: CardCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
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
      if (projectId) this.requireProject(projectId);
      const typeFields = this.resolveCardTypeFields(projectId ?? "", kind);
      const fields = validateCardFieldValues(command.fields ?? {}, typeFields);
      this.database
        .prepare(
          "INSERT INTO cards(id, project_id, kind, title, aliases_json, fields_json, tags_json, content_json, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        .run(cardId, projectId, kind, title, JSON.stringify(aliases), JSON.stringify(fields), JSON.stringify(tags), contentJson, timestamp, timestamp);
      if (this.hasProjectCardLinks() && projectId) {
        this.database
          .prepare("INSERT INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, cardId, timestamp);
      }
      if (projectId) this.touchProject(projectId, timestamp);
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
        this.resolveCardTypeFields(card.project_id ?? "", nextKind);
      }
      const schemas = this.resolveCardTypeFields(card.project_id ?? "", nextKind);
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
      this.touchLinkedProjects(cardId, timestamp);
      return {
        projectId: null,
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
      const linkedProjectIds = this.linkedProjectIds(cardId);
      const revision = card.revision + 1;
      if (this.hasProjectCardLinks()) {
        this.database
          .prepare("INSERT OR REPLACE INTO global_card_trash_state(card_id, linked_project_ids_json, deleted_at) VALUES (?, ?, ?)")
          .run(cardId, JSON.stringify(linkedProjectIds), timestamp);
        this.database.prepare("DELETE FROM project_card_links WHERE card_id = ?").run(cardId);
      } else {
        this.database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(cardId, cardId);
      }
      this.database
        .prepare("UPDATE cards SET deleted_at = ?, updated_at = ?, revision = ? WHERE id = ?")
        .run(timestamp, timestamp, revision, cardId);
      for (const projectId of linkedProjectIds) this.touchProject(projectId, timestamp);
      return {
        projectId: null,
        entityId: cardId,
        revision,
        changes: [{ entity: "card", id: cardId, action: "deleted", revision }]
      };
    });
  }

  private assertCardCanUnlink(projectId: string, cardId: string): void {
    const annotationCount = (
      this.database
        .prepare("SELECT count(*) AS count FROM annotations WHERE project_id = ? AND card_id = ?")
        .get(projectId, cardId) as { count: number }
    ).count;
    const resourceCount = (
      this.database
        .prepare("SELECT count(*) AS count FROM resources WHERE project_id = ? AND card_id = ?")
        .get(projectId, cardId) as { count: number }
    ).count;
    const scenes = this.database
      .prepare(`SELECT s.id, s.planning_json FROM scenes s
        JOIN chapters c ON c.id = s.chapter_id
        WHERE c.project_id = ? AND s.deleted_at IS NULL`)
      .all(projectId) as Array<{ id: string; planning_json: string }>;
    let planningCount = 0;
    for (const scene of scenes) {
      let planning: ScenePlanning;
      try {
        planning = JSON.parse(scene.planning_json) as ScenePlanning;
      } catch {
        throw new CreationWorkspaceError("integrity", `场景 ${scene.id} 的任务卡数据损坏。`);
      }
      const castCardIds = Array.isArray(planning.castCardIds)
        ? planning.castCardIds.filter((value): value is string => typeof value === "string")
        : [];
      if (
        planning.perspectiveCardId === cardId ||
        planning.locationCardId === cardId ||
        castCardIds.includes(cardId)
      ) {
        planningCount += 1;
      }
    }
    if (annotationCount + resourceCount + planningCount > 0) {
      throw new CreationWorkspaceError(
        "conflict",
        `卡片仍被当前作品使用：场景 ${planningCount}、批注 ${annotationCount}、附件 ${resourceCount}。`
      );
    }
  }

  private mutateCardLink(projectIdInput: string, cardIdInput: string, link: boolean): CardLinkResult {
    const projectId = validateId(projectIdInput, "作品");
    const cardId = validateId(cardIdInput, "卡片");
    const commandType = link ? "card.link" : "card.unlink";
    const timestamp = new Date().toISOString();
    try {
      this.database.exec("BEGIN IMMEDIATE");
      this.requireProject(projectId);
      this.requireCard(cardId);
      const exists =
        this.database
          .prepare("SELECT 1 FROM project_card_links WHERE project_id = ? AND card_id = ?")
          .get(projectId, cardId) !== undefined;
      let changed = false;
      if (link && !exists) {
        this.database
          .prepare("INSERT INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)")
          .run(projectId, cardId, timestamp);
        changed = true;
      } else if (!link && exists) {
        this.assertCardCanUnlink(projectId, cardId);
        this.database
          .prepare("DELETE FROM project_card_links WHERE project_id = ? AND card_id = ?")
          .run(projectId, cardId);
        changed = true;
      }
      const usageCount = (
        this.database.prepare("SELECT count(*) AS count FROM project_card_links WHERE card_id = ?").get(cardId) as {
          count: number;
        }
      ).count;
      let sequence = -1;
      if (changed) {
        this.touchProject(projectId, timestamp);
        const action = link ? "created" : "deleted";
        const changes: CreationWorkspaceEvent["changes"] = [
          { entity: "projectCardLink", id: `${projectId}:${cardId}`, action, revision: 1 }
        ];
        const logged = this.database
          .prepare("INSERT INTO change_log(project_id, command_type, changes_json, committed_at) VALUES (?, ?, ?, ?)")
          .run(projectId, commandType, JSON.stringify(changes), timestamp);
        sequence = Number(logged.lastInsertRowid);
        this.database.exec("COMMIT");
        this.emitCommitted({ kind: "committed", sequence, projectId, commandType, changes });
      } else {
        this.database.exec("COMMIT");
      }
      return {
        commandType,
        sequence,
        projectId,
        cardId,
        entityId: cardId,
        revision: 1,
        updatedAt: timestamp,
        changed,
        linked: link ? true : exists && !changed,
        usageCount
      };
    } catch (error) {
      try {
        this.database.exec("ROLLBACK");
      } catch {
        // SQLite may already have rolled back.
      }
      if (error instanceof CreationWorkspaceError) throw error;
      if (isConstraintError(error)) throw new CreationWorkspaceError("conflict", "卡片关联违反完整性约束。");
      throw new CreationWorkspaceError("integrity", "无法更新项目卡片关联。");
    }
  }

  private createCardRelation(command: CardRelationCreateCommand): CreationStructureResult {
    const projectId = command.projectId === undefined ? null : validateId(command.projectId, "作品");
    const fromCardId = validateId(command.fromCardId, "起点卡片");
    const toCardId = validateId(command.toCardId, "终点卡片");
    const relationTypeId = validateId(command.relationTypeId, "关系类型");
    const note =
      command.note === undefined || command.note === null ? null : String(command.note).trim() || null;
    const relationId = `relation-${randomUUID()}`;
    return this.runStructureTransaction("cardRelation.create", (timestamp) => {
      if (projectId) this.requireProject(projectId);
      this.requireCard(fromCardId);
      this.requireCard(toCardId);
      const linked = projectId && this.hasProjectCardLinks()
        ? (this.database
            .prepare("SELECT count(*) AS count FROM project_card_links WHERE project_id = ? AND card_id IN (?, ?)")
            .get(projectId, fromCardId, toCardId) as { count: number })
        : projectId
          ? (this.database
            .prepare("SELECT count(*) AS count FROM cards WHERE project_id = ? AND id IN (?, ?) AND deleted_at IS NULL")
            .get(projectId, fromCardId, toCardId) as { count: number })
          : { count: 2 };
      if (projectId && linked.count !== 2) {
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
      if (projectId) this.touchProject(projectId, timestamp);
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
        .get(relationId) as { id: string; project_id: string | null } | undefined;
      if (!relation) throw new CreationWorkspaceError("not-found", "关系不存在。");
      this.database.prepare("DELETE FROM card_relations WHERE id = ?").run(relationId);
      if (relation.project_id) this.touchProject(relation.project_id, timestamp);
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

  private trashList(projectId?: string): TrashItem[] {
    if (!projectId) {
      if (!this.hasProjectCardLinks()) return [];
      return (this.database
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
    const cards = this.hasProjectCardLinks()
      ? (this.database.prepare(`SELECT c.id, c.title, c.deleted_at, c.revision, s.linked_project_ids_json
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
      : this.database
        .prepare("SELECT id, title, deleted_at, revision FROM cards WHERE project_id = ? AND deleted_at IS NOT NULL ORDER BY deleted_at DESC, id")
        .all(projectId) as Array<{ id: string; title: string; deleted_at: string; revision: number }>;
    for (const row of cards) {
      items.push({ entity: "card", id: row.id, projectId, title: row.title, deletedAt: row.deleted_at, revision: row.revision });
    }
    return items;
  }

  private findTrashEntity(
    entity: TrashEntityKind,
    projectId: string | undefined,
    entityId: string
  ): { title: string; revision: number } | undefined {
    if (entity === "card" && this.hasProjectCardLinks()) {
      return this.database
        .prepare("SELECT title, revision FROM cards WHERE id = ? AND deleted_at IS NOT NULL")
        .get(entityId) as { title: string; revision: number } | undefined;
    }
    if (!projectId) throw new CreationWorkspaceError("invalid-input", "项目回收站操作必须提供作品 ID。");
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
    const globalCard = command.entity === "card" && this.hasProjectCardLinks();
    const projectId = globalCard
      ? (command.projectId === undefined ? undefined : validateId(command.projectId, "作品"))
      : validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return this.runStructureTransaction("trash.restore", (timestamp) => {
      if (projectId) this.requireProject(projectId);
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
        const state = this.hasProjectCardLinks()
          ? this.database.prepare("SELECT linked_project_ids_json FROM global_card_trash_state WHERE card_id = ?")
            .get(entityId) as { linked_project_ids_json: string } | undefined
          : undefined;
        const linkedProjectIds = state ? (JSON.parse(state.linked_project_ids_json) as string[]) : this.linkedProjectIds(entityId);
        this.database
          .prepare("UPDATE cards SET deleted_at = NULL, updated_at = ?, revision = revision + 1 WHERE id = ?")
          .run(timestamp, entityId);
        if (this.hasProjectCardLinks()) {
          const insertLink = this.database.prepare("INSERT OR IGNORE INTO project_card_links(project_id, card_id, linked_at) VALUES (?, ?, ?)");
          for (const linkedProjectId of linkedProjectIds) {
            const exists = this.database.prepare("SELECT 1 FROM projects WHERE id = ?").get(linkedProjectId);
            if (exists) {
              insertLink.run(linkedProjectId, entityId, timestamp);
              this.touchProject(linkedProjectId, timestamp);
            }
          }
          this.database.prepare("DELETE FROM global_card_trash_state WHERE card_id = ?").run(entityId);
        }
      }
      if (projectId && !globalCard) this.touchProject(projectId, timestamp);
      return {
        projectId: globalCard ? null : projectId!,
        entityId,
        revision: found.revision + 1,
        changes: [{ entity: command.entity, id: entityId, action: "restored", revision: found.revision + 1 }]
      };
    });
  }

  private trashPurge(command: TrashPurgeCommand): CreationStructureResult {
    const globalCard = command.entity === "card" && this.hasProjectCardLinks();
    const projectId = globalCard
      ? (command.projectId === undefined ? undefined : validateId(command.projectId, "作品"))
      : validateId(command.projectId, "作品");
    if (!TRASH_ENTITY_KINDS.has(command.entity)) {
      throw new CreationWorkspaceError("invalid-input", "回收站实体类型无效。");
    }
    const entityId = validateId(command.entityId, "实体");
    return this.runStructureTransaction("trash.purge", (timestamp) => {
      if (projectId) this.requireProject(projectId);
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
        if (globalCard) queueAndDeleteGlobalCard(this.database, entityId, timestamp);
        else {
          this.database.prepare("DELETE FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").run(entityId, entityId);
          this.database.prepare("DELETE FROM cards WHERE id = ?").run(entityId);
        }
      }
      if (projectId && !globalCard) this.touchProject(projectId, timestamp);
      return {
        projectId: globalCard ? null : projectId!,
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
    const scene = this.database.prepare("SELECT body_json, planning_json, summary, scene_status, revision, deleted_at FROM scenes WHERE id = ?").get(subjectId) as {
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
    const scenes = this.database.prepare("SELECT id, title, sort_order, body_json, planning_json, summary, scene_status, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(subjectId) as Array<{
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
}>; const chaptersWithScenes = chapters.map((chapter) => { const scenes = this.database.prepare("SELECT id, title, sort_order, body_json, planning_json, summary, scene_status, revision FROM scenes WHERE chapter_id = ? AND deleted_at IS NULL ORDER BY sort_order, id").all(chapter.id) as Array<{
    id: string;
    title: string;
    sort_order: number;
    body_json: string;
    planning_json: string;
    summary: string;
    scene_status: string;
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
        const current = this.database.prepare("SELECT planning_json, summary, scene_status, revision FROM scenes WHERE id = ?").get(subjectId) as {
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
        this.database.prepare("UPDATE scenes SET body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(bodyJson, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, revision, subjectId);
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
                planning_json?: string;
                summary?: string;
                scene_status?: string;
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
            const existing = this.database.prepare("SELECT chapter_id, planning_json, summary, scene_status FROM scenes WHERE id = ?").get(scene.id) as {
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
                this.database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, planning_json, summary, scene_status, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, subjectId, scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
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
            const existingScene = this.database.prepare("SELECT chapter_id, planning_json, summary, scene_status FROM scenes WHERE id = ?").get(scene.id) as {
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
                this.database.prepare("UPDATE scenes SET title = ?, sort_order = ?, body_json = ?, planning_json = ?, summary = ?, scene_status = ?, han_count = ?, punct_count = ?, non_ws_count = ?, deleted_at = NULL, updated_at = ?, revision = ? WHERE id = ?").run(scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, sceneRevision, scene.id);
            }
            else {
                this.database.prepare("INSERT INTO scenes(id, chapter_id, title, sort_order, body_json, planning_json, summary, scene_status, han_count, punct_count, non_ws_count, created_at, updated_at, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").run(scene.id, chapter.id, scene.title, scene.sort_order, scene.body_json, planningJson, summary, status, stats.han, stats.punct, stats.nonWhitespace, timestamp, timestamp, sceneRevision);
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
} /** 回收站影响预览；无 projectId 的 card 查询也可用于删除前影响确认。 */

  private readTrashImpact(projectId: string | undefined, entity: TrashEntityKind, entityId: string): TrashImpactView {
    if (projectId) this.requireProject(projectId);
    if (!projectId && entity !== "card") throw new CreationWorkspaceError("invalid-input", "项目回收站影响请求必须提供作品 ID。");
    const warnings: string[] = [];
    if (entity === "volume") {
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
    }
    const cardSql = this.hasProjectCardLinks()
      ? "SELECT title, deleted_at FROM cards WHERE id = ?"
      : "SELECT title, deleted_at FROM cards WHERE id = ? AND project_id = ?";
    const card = this.database.prepare(cardSql).get(...(this.hasProjectCardLinks() ? [entityId] : [entityId, projectId])) as {
      title: string;
      deleted_at: string | null;
    } | undefined;
    if (!card) throw new CreationWorkspaceError("not-found", "对象不存在。");
    if (projectId && card.deleted_at === null) throw new CreationWorkspaceError("conflict", "对象不在回收站。");

    const relations = this.database.prepare("SELECT count(*) AS count FROM card_relations WHERE from_card_id = ? OR to_card_id = ?").get(entityId, entityId) as { count: number };
    const projectResources = this.database.prepare("SELECT count(*) AS count FROM resources WHERE card_id = ?").get(entityId) as { count: number };
    const globalResources = this.hasProjectCardLinks()
      ? this.database.prepare("SELECT count(*) AS count FROM global_card_resources WHERE card_id = ?").get(entityId) as { count: number }
      : { count: 0 };
    const annotations = this.database.prepare("SELECT count(*) AS count FROM annotations WHERE card_id = ? AND deleted_at IS NULL").get(entityId) as { count: number };
    const state = this.hasProjectCardLinks()
      ? this.database.prepare("SELECT linked_project_ids_json FROM global_card_trash_state WHERE card_id = ?").get(entityId) as { linked_project_ids_json: string } | undefined
      : undefined;
    const linkedProjectIds = state ? (JSON.parse(state.linked_project_ids_json) as string[]) : this.linkedProjectIds(entityId);
    const scenes = this.database.prepare("SELECT id, planning_json FROM scenes WHERE deleted_at IS NULL").all() as Array<{ id: string; planning_json: string }>;
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
      // projectId=null 表示全局实体发生变化，所有项目作用域都必须立即失效刷新。
      if (scope.projectId !== undefined && event.projectId !== null && scope.projectId !== event.projectId) continue;
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
    const databasePath = path.join(options.directory, "workspace.sqlite");
    const configureDatabase = (target: Database): void => {
      target.pragma("journal_mode = WAL");
      target.pragma("foreign_keys = ON");
      target.pragma("synchronous = FULL");
    };
    database = new Database(databasePath);
    configureDatabase(database);
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
    else if (
      existingVersion !== 9 &&
      existingVersion !== GLOBAL_CARD_SCHEMA_VERSION &&
      existingVersion !== SCENE_META_SCHEMA_VERSION &&
      existingVersion !== SCHEMA_VERSION
    ) {
      throw new CreationWorkspaceError("integrity", `不支持的创作工作区 schema 版本：${existingVersion}。`);
    }

    const versionAfterLegacyMigrations = Number(database.pragma("user_version", { simple: true }));
    if (options.testOnlyTargetSchemaVersion === 9) {
      if (versionAfterLegacyMigrations !== 9) {
        throw new CreationWorkspaceError("integrity", "测试夹具只能停留在 schema v9。");
      }
    } else if (versionAfterLegacyMigrations === 9) {
      // 新建空库不需要可恢复备份；任何既有库（含从 v1..v8 逐级升到 v9 的库）
      // 都先 checkpoint/close，再创建带哈希清单的目录级快照，成功后才进入 v10 事务。
      let backupRoot: string | null = null;
      if (existingVersion !== 0) {
        database.pragma("wal_checkpoint(TRUNCATE)");
        database.close();
        database = undefined;
        const backup = await createV9MigrationBackup(options.directory, {
          failBackup: options.testOnlyFailV9ToV10Backup === true
        });
        backupRoot = backup.backupRoot;
        database = new Database(databasePath);
        configureDatabase(database);
      }
      migrateGlobalCardsV9ToV10(database, backupRoot, {
        failAfterLinkBackfill: options.testOnlyFailV9ToV10AfterLinkBackfill === true
      });
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === GLOBAL_CARD_SCHEMA_VERSION &&
      needsGlobalCardSchemaRepair(database)
    ) {
      database.pragma("wal_checkpoint(TRUNCATE)");
      database.close();
      database = undefined;
      const backup = await createGlobalCardSchemaRepairBackup(options.directory);
      database = new Database(databasePath);
      configureDatabase(database);
      repairGlobalCardSchemaV10(database, backup.backupRoot);
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === GLOBAL_CARD_SCHEMA_VERSION
    ) {
      migrateSchemaV10ToV11(database);
    }
    if (
      options.testOnlyTargetSchemaVersion !== 9 &&
      Number(database.pragma("user_version", { simple: true })) === SCENE_META_SCHEMA_VERSION
    ) {
      migrateSchemaV11ToV12(database);
    }
    if (options.testOnlyTargetSchemaVersion !== 9) ensureGlobalCardResourceSchema(database);
    if (options.testOnlyTargetSchemaVersion !== 9) ensureProofIgnoreSchema(database);
    try {
      purgeExpiredTrash(database);
      await drainGlobalCardResourceGc(database, options.directory);
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
