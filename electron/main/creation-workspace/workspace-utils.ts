/**
 * 创作工作区共享工具（从 creation-workspace/index.ts 拆出，纯移动式重构）。
 *
 * 存放被多个域模块共同使用的纯工具：JSON 判定、LIKE 转义、正文纯文本提取、
 * 命中上下文片段、卡片 JSON 纯文本收集，以及搜索域常量。
 */

import type { CreationDocument, CreationSearchScope, ReplaceScope } from "../../../src/types/creation";
import { CreationWorkspaceError } from "./types";

export const SEARCH_SCOPES = new Set<CreationSearchScope>(["scene", "card", "chapter", "project"]);
export const DEFAULT_SEARCH_LIMIT = 50;
export const MAX_SEARCH_LIMIT = 200;

export const REPLACE_SCOPES = new Set<ReplaceScope>(["project", "volume", "chapter", "scene"]);
export const DEFAULT_REPLACE_PREVIEW_LIMIT = 200;
export const MAX_REPLACE_PREVIEW_LIMIT = 1000;
export const MAX_REGEX_LENGTH = 200;
export const REGEX_FORBIDDEN_PATTERN = /\(\?[=<!]|\\[1-9]/;

/** 校验受限正则：长度限制、禁用 lookaround/后向引用，且必须可编译。 */
export function compileReplaceRegex(source: string): RegExp {
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

export interface TextMatcher {
  count(text: string): number;
  snippets(text: string, max: number): string[];
  apply(text: string): { text: string; hits: number };
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

function countRegexHits(regex: RegExp, text: string): number {
  let hits = 0;
  regex.lastIndex = 0;
  while (regex.exec(text) !== null) {
    hits += 1;
    if (regex.lastIndex === 0) regex.lastIndex += 1;
  }
  return hits;
}

export function plainMatcher(find: string, replaceWith: string): TextMatcher {
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

export function regexMatcher(regex: RegExp, replaceWith: string): TextMatcher {
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

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function validateId(value: unknown, label: string): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new CreationWorkspaceError("invalid-input", `${label}不能为空。`);
  }
  return value;
}

export function validateTitle(value: unknown, label: string, maxLength = 200): string {
  if (typeof value !== "string") {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为文本。`);
  }
  const title = value.trim();
  if (!title || title.length > maxLength) {
    throw new CreationWorkspaceError("invalid-input", `${label}必须为 1 至 ${maxLength} 个字符。`);
  }
  return title;
}

export function validateBaseRevision(value: unknown): number {
  if (!Number.isInteger(value) || Number(value) < 1) {
    throw new CreationWorkspaceError("invalid-input", "修订号必须为正整数。");
  }
  return value as number;
}

/** 收件箱/卡片的 JSON 数组字段（tags 等）安全解析。 */
export function parseJsonArray(json: string): string[] {
  try {
    const parsed = JSON.parse(json) as unknown;
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
  } catch {
    return [];
  }
}

/** 场景正文纯文本：每个块一段，块间空行，场景分隔占位。 */
export function extractSceneText(bodyJson: string): string {
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

/** 卡片/字段/标签 JSON 的纯文本值（用于搜索上下文片段）。 */
export function jsonStringValues(json: string): string[] {
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
export function makeSnippet(text: string, keyword: string, radius = 16): string | null {
  const index = text.toLowerCase().indexOf(keyword.toLowerCase());
  if (index < 0) return null;
  const start = Math.max(0, index - radius);
  const end = Math.min(text.length, index + keyword.length + radius * 2);
  const before = start > 0 ? "…" : "";
  const after = end < text.length ? "…" : "";
  return `${before}${text.slice(start, end)}${after}`;
}

/** LIKE 通配符转义，避免用户输入中的 %/_ 影响匹配。 */
export function escapeLike(value: string): string {
  return value.replace(/[\\%_]/g, (ch) => `\\${ch}`);
}

export function isConstraintError(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    String((error as { code: unknown }).code).startsWith("SQLITE_CONSTRAINT")
  );
}

/** beforeId：省略或 null 表示追加到末尾；其余必须为已存在的实体 id。 */
export function resolveBeforeId(value: unknown, label: string): string | undefined {
  if (value === undefined || value === null) return undefined;
  return validateId(value, label);
}