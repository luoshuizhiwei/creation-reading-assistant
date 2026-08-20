/**
 * 卡片 CSV/Markdown 映射导入与筛选导出的编排入口（独立深模块）。
 *
 * 该模块只聚合纯函数与 DB 桥接，不自行接触公共 seam（IPC/preload/types）。
 * 真正接入 UI 的 IPC 通道由最终集成者按 SEAM REQUEST 注册。
 */

import { randomUUID } from "node:crypto";
import { parseCardCsv, parseCardMarkdown } from "./card-io-parser";
import { buildCardImportPlan } from "./card-import-plan";
import {
  applyCardImportPlan,
  readCardImportSchemaContext,
  readCardsForExport
} from "./card-import-apply";
import {
  collectExportFieldKeys,
  exportCardsToCsv,
  exportCardsToMarkdown,
  serializeExportField
} from "./card-export";
import type {
  CardExportFilter,
  CardExportRow,
  CardImportMapping,
  CardImportOptions,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext
} from "./card-io-types";

export * from "./card-io-types";

export {
  parseCardCsv,
  parseCardMarkdown,
  buildCardImportPlan,
  applyCardImportPlan,
  readCardImportSchemaContext,
  readCardsForExport,
  collectExportFieldKeys,
  exportCardsToCsv,
  exportCardsToMarkdown,
  serializeExportField
};

/** 生成一次性导入批次 id（标识本次 apply 的事务）。 */
export function generateCardImportPlanId(): string {
  return `card-import-${randomUUID()}`;
}

/** 解析源文本为预览（不直接写库）。 */
export function parseCardSource(text: string, format: "csv" | "markdown"): CardImportPreview {
  return format === "csv" ? parseCardCsv(text) : parseCardMarkdown(text);
}

/** 解析 + 映射 + 校验，产出可 apply 的 plan（纯）。 */
export function planCardImport(
  text: string,
  format: "csv" | "markdown",
  mapping: CardImportMapping,
  schema: CardImportSchemaContext,
  options: CardImportOptions = {}
): CardImportPlan {
  const preview = parseCardSource(text, format);
  return buildCardImportPlan(preview, mapping, schema, options);
}

export type {
  CardExportFilter,
  CardExportRow,
  CardImportMapping,
  CardImportOptions,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext
};
