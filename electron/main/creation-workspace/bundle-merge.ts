/**
 * 项目包（bundle）导入比对辅助：路径安全、JSON 规范化签名、卡片差异与稳定 ID 重映射。
 *
 * 从 creation-workspace/index.ts 外迁而来（拆分切片 1），行为逐字保持：
 * 均为纯函数，不触库；导入应用（写库）仍由工作区类负责。
 */
import path from "node:path";
import type { CreationDocument, ProjectBundleData, ScenePlanning } from "./types";
import { CreationWorkspaceError } from "./types";
import { isRecord } from "./workspace-utils";

/** 项目包/附件相对路径安全检查：仅接受 POSIX 风格相对路径，禁止绝对路径、穿越、盘符与反斜杠。 */
export function isSafeBundleRelativePath(value: unknown): value is string {
  if (typeof value !== "string" || !value.trim()) return false;
  if (value.length > 500) return false;
  if (path.isAbsolute(value)) return false;
  if (value.includes("..") || value.includes("\\") || value.includes(":")) return false;
  if (value.includes("\0")) return false;
  return true;
}

export function canonicalJsonValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonicalJsonValue);
  if (!isRecord(value)) return value;
  return Object.fromEntries(
    Object.keys(value).sort().map((key) => [key, canonicalJsonValue(value[key])])
  );
}

export function canonicalStoredJson(value: string): string {
  try {
    return JSON.stringify(canonicalJsonValue(JSON.parse(value)));
  } catch {
    return `!invalid:${value}`;
  }
}

export interface StoredBundleCardComparable {
  id: string;
  kind: string;
  title: string;
  aliases_json: string;
  fields_json: string;
  tags_json: string;
  content_json: string;
  deleted_at: string | null;
}

export interface ComparableCardResource {
  role: string;
  sha256: string;
  size: number;
  originalName: string | null;
}

export function cardResourceSignature(resources: ComparableCardResource[]): string {
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

export function incomingCardResourceSignature(data: ProjectBundleData, cardId: string): string {
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

export function bundleCardDifferences(
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

export function remapPlanningCardIds(planningJson: string, cardIdMap: ReadonlyMap<string, string>): string {
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

export function remapJsonStableIds(value: string, idMap: ReadonlyMap<string, string>): string {
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

/**
 * 场景正文最小块视图（kind + 文本，不携带 marks/完整结构），供审阅稿导出使用。
 */
export function extractSceneBlocks(bodyJson: string): ProjectExportBlockLite[] {
  let document: CreationDocument;
  try {
    document = JSON.parse(bodyJson) as CreationDocument;
  } catch {
    return [];
  }
  const result: ProjectExportBlockLite[] = [];
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

/** 与 types.ProjectExportBlock 对齐的最小局部别名，避免在此重复 import 整个导出视图类型。 */
type ProjectExportBlockLite = { kind: string; text: string };

/**
 * 场景任务卡字段解析（planning_json），非法结构返回空对象。
 * 保留明确的 null（表示已清空）与空数组（出场为空），以便清空后重新读取仍为空。
 */
export function parseScenePlanning(json: string): ScenePlanning | undefined {
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
