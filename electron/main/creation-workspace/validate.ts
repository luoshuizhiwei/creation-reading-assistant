/**
 * 输入校验簇：卡片字段 schema/取值、字符串列表、场景文档结构的强校验。
 *
 * 从 creation-workspace/index.ts 外迁而来（拆分切片 1），行为逐字保持：
 * 全部为纯校验函数，非法输入抛 CreationWorkspaceError("invalid-input")。
 */
import type { CardFieldKind, CardFieldSchema, CreationDocument } from "./types";
import { CreationWorkspaceError } from "./types";
import { isRecord } from "./workspace-utils";

const SCENE_TEXT_BLOCKS = new Set(["paragraph", "quoteLetter", "centeredText", "authorNote"]);
const SCENE_MARKS = new Set(["bold", "italic"]);

export function validateStringList(value: unknown, label: string, maxLength = 20): string[] {
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

export function validateCardFieldSchemaList(value: unknown): CardFieldSchema[] {
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

export function validateCardFieldValues(
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

export function isValidSceneDocument(value: unknown): value is CreationDocument {
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
