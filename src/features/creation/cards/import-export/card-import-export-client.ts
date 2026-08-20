/**
 * 卡片导入导出 —— 渲染端 IPC 客户端 + 纯函数助手。
 *
 * 端点已接入主进程 creation:* seam（openAndParse/parse/schema/plan/apply/exportOpenAndWrite），
 * 渲染端不持有任何文件路径能力，文件读取与写出全部由主进程 dialog 完成。
 */
import { getDesktopApi } from "@/services/ipc-client";
import {
  DEFAULT_TAG_DELIMITERS,
  type CardExportFilter,
  type CardExportResult,
  type CardImportApplyInput,
  type CardImportApplyResult,
  type CardImportMapping,
  type CardImportOptions,
  type CardImportPlan,
  type CardImportPreview,
  type CardImportSchemaContext,
  type CardImportSource
} from "./card-import-export-types";

/** SEAM 将要接入主进程的端点签名（渲染端视角）。 */
export interface CardImportExportApi {
  /** 打开系统文件对话框并读取源文本，用户取消则返回 null。 */
  cardImportOpenAndParse(): Promise<CardImportSource | null>;
  /** 把源文本解析为预览（不写库），供映射使用。 */
  cardImportParse(input: { text: string; format: "csv" | "markdown" }): Promise<CardImportPreview>;
  /** 读取当前项目的类型 / 关系类型 / 现有卡片标题，供映射与校验使用。 */
  cardImportSchema(projectId: string): Promise<CardImportSchemaContext>;
  /** 由预览 + 映射 + 模式 + 选项计算导入计划（apply 前校验）。 */
  cardImportPlan(input: CardImportApplyInput): Promise<CardImportPlan>;
  /** 在一次性 planId + 单事务内应用计划；失败时回滚零写入。 */
  cardImportApply(input: CardImportApplyInput): Promise<CardImportApplyResult>;
  /** 按筛选范围导出，由主进程弹出保存对话框并写出；用户取消则 canceled=true。 */
  cardExportOpenAndWrite(input: {
    projectId: string;
    filter: CardExportFilter;
    format: "csv" | "markdown";
  }): Promise<CardExportResult>;
}

function getCardImportExportApi(): CardImportExportApi {
  const api = getDesktopApi().creation as unknown as CardImportExportApi | undefined;
  if (!api || typeof api.cardImportPlan !== "function") {
    throw new Error("卡片导入导出接口尚未就绪（SEAM 未接入主进程）。");
  }
  return api;
}

export async function openAndParseImportSource(): Promise<CardImportSource | null> {
  return getCardImportExportApi().cardImportOpenAndParse();
}

export async function loadImportSchema(projectId: string): Promise<CardImportSchemaContext> {
  return getCardImportExportApi().cardImportSchema(projectId);
}

export async function parseImportSource(input: {
  text: string;
  format: "csv" | "markdown";
}): Promise<CardImportPreview> {
  return getCardImportExportApi().cardImportParse(input);
}

export async function planCardImport(input: CardImportApplyInput): Promise<CardImportPlan> {
  return getCardImportExportApi().cardImportPlan(input);
}

export async function applyCardImport(input: CardImportApplyInput): Promise<CardImportApplyResult> {
  return getCardImportExportApi().cardImportApply(input);
}

export async function exportCards(input: {
  projectId: string;
  filter: CardExportFilter;
  format: "csv" | "markdown";
}): Promise<{ canceled: boolean; written: number }> {
  return getCardImportExportApi().cardExportOpenAndWrite(input);
}

const TITLE_HINTS = ["标题", "名称", "title", "name"];
const TYPE_HINTS = ["类型", "type", "kind"];
const ALIAS_HINTS = ["别名", "alias", "aliases"];
const TAG_HINTS = ["标签", "tag", "tags"];

function headerLooksLike(header: string, hints: readonly string[]): boolean {
  const lower = header.trim().toLowerCase();
  return hints.some((hint) => lower === hint || lower.includes(hint));
}

/**
 * 依据预览表头与项目模式生成「尽力而为」的默认映射，减少用户手动配置。
 * CSV：按表头语义匹配标题/类型/别名/标签，并按字段名匹配自定义字段。
 * Markdown：标题固定来自 heading，字段键直接对应块内 key。
 */
export function buildDefaultMapping(
  preview: CardImportPreview,
  schema: CardImportSchemaContext
): CardImportMapping {
  const base: CardImportMapping = {
    format: preview.format,
    titleSource: preview.format === "markdown" ? "heading" : ""
  };

  if (preview.format === "csv") {
    const headers = preview.headers ?? [];
    const titleHeader = headers.find((h) => headerLooksLike(h, TITLE_HINTS));
    if (titleHeader !== undefined) base.titleSource = titleHeader;
    else if (headers.length > 0) base.titleSource = headers[0];

    const typeHeader = headers.find((h) => headerLooksLike(h, TYPE_HINTS));
    if (typeHeader !== undefined) base.typeSource = typeHeader;

    base.aliasSources = headers.filter((h) => headerLooksLike(h, ALIAS_HINTS));
    base.tagSources = headers.filter((h) => headerLooksLike(h, TAG_HINTS));

    const fieldSources: Record<string, string> = {};
    for (const type of schema.cardTypes) {
      for (const field of type.fields) {
        const match = headers.find(
          (h) => h.trim().toLowerCase() === field.label.toLowerCase() || h.trim().toLowerCase() === field.key.toLowerCase()
        );
        if (match !== undefined) fieldSources[field.key] = match;
      }
    }
    if (Object.keys(fieldSources).length > 0) base.fieldSources = fieldSources;
  } else {
    // Markdown：heading 已设为标题；别名/标签按块内约定字段名。
    base.aliasSources = ["别名"];
    base.tagSources = ["标签"];
    base.headingTypeDelimiter = "：";
    const fieldSources: Record<string, string> = {};
    for (const type of schema.cardTypes) {
      for (const field of type.fields) {
        if (field.key.toLowerCase() === "heading") continue;
        fieldSources[field.key] = field.key;
      }
    }
    if (Object.keys(fieldSources).length > 0) base.fieldSources = fieldSources;
  }

  base.tagDelimiters = [...DEFAULT_TAG_DELIMITERS];
  return base;
}

export interface PlanSummary {
  cardCount: number;
  relationCount: number;
  skipped: number;
  errorCount: number;
  errorLines: string[];
  warningCount: number;
}

/** 把计划压缩为 UI 友好的摘要。 */
export function summarizePlan(plan: CardImportPlan): PlanSummary {
  return {
    cardCount: plan.cards.length,
    relationCount: plan.relations.length,
    skipped: plan.skippedRowCount,
    errorCount: plan.errorCount,
    errorLines: plan.errors.map((error) => `第 ${error.rowIndex + 1} 行：${error.messages.join("；")}`),
    warningCount: plan.warnings.length
  };
}

/** 映射能否在 apply 前解析出类型：要么指定了 typeSource，要么有 typeFallback。 */
export function mappingCanResolveType(mapping: CardImportMapping): boolean {
  return Boolean(mapping.typeSource && mapping.typeSource.trim()) || Boolean(mapping.typeFallback && mapping.typeFallback.trim());
}

/** 映射是否足以生成计划（标题来源必填）。 */
export function mappingIsReady(mapping: CardImportMapping): boolean {
  return Boolean(mapping.titleSource && mapping.titleSource.trim());
}
