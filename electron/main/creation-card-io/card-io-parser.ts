/**
 * 卡片 CSV / Markdown 解析器（纯函数，无 DB / electron 依赖）。
 *
 * 解析只产出「预览」（preview），绝不写库。预览是映射与校验的输入。
 *
 * CSV 处理：BOM、CRLF/CR/LF、引号包裹、字段内逗号、双引号转义（""）、空列、重复表头。
 * Markdown 处理：标题层级（#..######）、空块、正文中的 key:value 元数据行与散文说明。
 */

import type { CardImportPreview, CardImportRowPreview } from "./card-io-types";

/** 去除 UTF-8 BOM（字节序标记）。 */
function stripBom(input: string): string {
  return input.charCodeAt(0) === 0xfeff ? input.slice(1) : input;
}

/** 状态机解析 CSV 为字符串二维数组，正确处理引号、逗号、换行与转义引号。 */
function parseCsvRecords(text: string): string[][] {
  if (text === "") return [];
  const records: string[][] = [];
  let field = "";
  let row: string[] = [];
  let inQuotes = false;
  let i = 0;
  const n = text.length;
  const endRow = (): void => {
    row.push(field);
    records.push(row);
    row = [];
    field = "";
  };
  while (i < n) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i += 2;
          continue;
        }
        inQuotes = false;
        i += 1;
        continue;
      }
      field += ch;
      i += 1;
      continue;
    }
    if (ch === '"') {
      inQuotes = true;
      i += 1;
      continue;
    }
    if (ch === ",") {
      row.push(field);
      field = "";
      i += 1;
      continue;
    }
    if (ch === "\r") {
      endRow();
      if (text[i + 1] === "\n") i += 2;
      else i += 1;
      continue;
    }
    if (ch === "\n") {
      endRow();
      i += 1;
      continue;
    }
    field += ch;
    i += 1;
  }
  // 收尾：仅当仍有内容（避免把纯空行变成多余记录）。
  if (field !== "" || row.length > 0) endRow();
  // 丢弃仅含单个空单元格的「空白行」（导出常见尾随空行）。
  return records.filter((rec) => !(rec.length === 1 && rec[0] === ""));
}

/** 表头去重：重复项追加 __2 / __3 后缀，并记录原始重名。 */
function dedupeHeaders(raw: string[]): { headers: string[]; duplicateHeaders: string[] } {
  const seen = new Map<string, number>();
  const headers: string[] = [];
  const duplicateHeaders: string[] = [];
  for (const h of raw) {
    const key = h.trim();
    const prev = seen.get(key) ?? 0;
    if (prev > 0) {
      const count = prev + 1;
      seen.set(key, count);
      headers.push(`${key}__${count}`);
      if (count === 2) duplicateHeaders.push(key);
    } else {
      seen.set(key, 1);
      headers.push(key);
    }
  }
  return { headers, duplicateHeaders };
}

export function parseCardCsv(input: string): CardImportPreview {
  const text = stripBom(input);
  const records = parseCsvRecords(text);
  if (records.length === 0) {
    return {
      format: "csv",
      rows: [],
      headers: [],
      duplicateHeaders: [],
      warnings: ["CSV 未解析到任何表头或数据行。"]
    };
  }
  const rawHeaders = records[0];
  const { headers, duplicateHeaders } = dedupeHeaders(rawHeaders);
  const warnings: string[] = [];
  if (duplicateHeaders.length > 0) {
    warnings.push(`CSV 存在重复表头：${duplicateHeaders.join("、")}，已自动重命名（如「${duplicateHeaders[0]}__2」）以避免冲突。`);
  }
  const emptyCols = rawHeaders.filter((h) => h.trim() === "").length;
  if (emptyCols > 0) {
    warnings.push(`CSV 存在 ${emptyCols} 个空表头列，已忽略其映射。`);
  }
  const rows: CardImportRowPreview[] = [];
  for (let r = 1; r < records.length; r += 1) {
    const rec = records[r];
    const fields: Record<string, string> = {};
    for (let c = 0; c < headers.length; c += 1) {
      fields[headers[c]] = rec[c] ?? "";
    }
    const notes: string[] = [];
    if (rec.length > headers.length) notes.push("该行字段数超出表头，多余列已忽略。");
    rows.push({ index: r - 1, fields, notes });
  }
  return { format: "csv", rows, headers, duplicateHeaders, warnings };
}

interface RawMarkdownBlock {
  index: number;
  level: number;
  headingText: string;
  bodyLines: string[];
}

interface FinalizedMarkdownBlock extends RawMarkdownBlock {
  metaFields: Record<string, string>;
  note: string;
  isEmpty: boolean;
}

const HEADING_RE = /^(#{1,6})\s+(.*)$/;
const META_RE = /^\s*([^:：]+?)\s*[:：]\s*(.*)$/;

function finalizeBlock(index: number, block: { level: number; headingText: string; bodyLines: string[] }): FinalizedMarkdownBlock {
  const metaFields: Record<string, string> = {};
  const noteLines: string[] = [];
  for (const line of block.bodyLines) {
    const meta = META_RE.exec(line);
    if (meta && meta[1].trim() !== "") {
      metaFields[meta[1].trim()] = meta[2].trim();
    } else if (line.trim() !== "") {
      noteLines.push(line.trim());
    }
  }
  const isEmpty = block.bodyLines.every((l) => l.trim() === "") && Object.keys(metaFields).length === 0;
  return {
    index,
    level: block.level,
    headingText: block.headingText,
    bodyLines: block.bodyLines,
    metaFields,
    note: noteLines.join("\n"),
    isEmpty
  };
}

export function parseCardMarkdown(input: string): CardImportPreview {
  const lines = stripBom(input).split(/\r\n|\r|\n/);
  const blocks: FinalizedMarkdownBlock[] = [];
  let current: { level: number; headingText: string; bodyLines: string[] } | null = null;
  const flush = (): void => {
    if (current) blocks.push(finalizeBlock(blocks.length, current));
  };
  for (const line of lines) {
    const m = HEADING_RE.exec(line);
    if (m) {
      flush();
      current = { level: m[1].length, headingText: m[2].trim(), bodyLines: [] };
    } else if (current) {
      current.bodyLines.push(line);
    }
  }
  flush();

  const rows: CardImportRowPreview[] = blocks.map((b) => {
    const fields: Record<string, string> = { heading: b.headingText, level: String(b.level), body: b.note };
    for (const [k, v] of Object.entries(b.metaFields)) fields[k] = v;
    return { index: b.index, fields, notes: b.isEmpty ? ["空块：该标题下没有任何内容。"] : [] };
  });
  const warnings: string[] = [];
  if (blocks.length === 0) warnings.push("Markdown 未解析到任何标题块（#..######）。");
  return { format: "markdown", rows, warnings };
}
