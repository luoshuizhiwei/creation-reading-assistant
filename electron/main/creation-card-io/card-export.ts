/**
 * 卡片导出序列化（纯函数，无 DB / electron 依赖）。
 *
 * 导出「当前筛选范围」内的卡片为 CSV 或 Markdown，两者字段对称（相同 fieldKeys）。
 * 不导出内部 id、批注或不必要元数据：content_json 不含、revision/deleted_at 不含、
 * cardRef 字段已在上游解析为目标卡片标题（不出现 card- 主键）。
 */

import { type CardExportRow, MARKDOWN_CARD_HEADING_LEVEL } from "./card-io-types";

/** 把任意字段值序列化为可读字符串（与导入解析互逆）。 */
export function serializeExportField(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "是" : "否";
  if (Array.isArray(value)) return value.map((v) => serializeExportField(v)).join("、");
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

/** 从导出行的并集推导对称字段键列表（用于 CSV 表头 / Markdown 元数据键）。 */
export function collectExportFieldKeys(rows: CardExportRow[]): string[] {
  const keys = new Set<string>();
  for (const r of rows) for (const k of Object.keys(r.fields)) keys.add(k);
  return [...keys];
}

function csvEscape(s: string): string {
  if (/[",\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

export function exportCardsToCsv(rows: CardExportRow[], fieldKeys: string[]): string {
  const header = ["标题", "类型", "别名", "标签", ...fieldKeys].map(csvEscape);
  const lines = [header.join(",")];
  for (const r of rows) {
    const cells = [
      r.title,
      r.kindLabel,
      r.aliases.join("、"),
      r.tags.join("、"),
      ...fieldKeys.map((k) => r.fields[k] ?? "")
    ].map(csvEscape);
    lines.push(cells.join(","));
  }
  return `${lines.join("\r\n")}\r\n`;
}

export function exportCardsToMarkdown(rows: CardExportRow[], fieldKeys: string[]): string {
  const out: string[] = [];
  for (const r of rows) {
    out.push(`${"#".repeat(MARKDOWN_CARD_HEADING_LEVEL)} ${r.kindLabel}：${r.title}`);
    if (r.aliases.length > 0) out.push(`别名：${r.aliases.join("、")}`);
    if (r.tags.length > 0) out.push(`标签：${r.tags.join("、")}`);
    for (const k of fieldKeys) {
      const v = r.fields[k];
      if (v !== undefined && v !== "") out.push(`${k}：${v}`);
    }
    out.push("");
  }
  return out.join("\n");
}
