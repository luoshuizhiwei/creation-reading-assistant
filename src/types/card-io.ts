/**
 * 卡片 CSV/Markdown 导入导出 —— 跨进程 seam 共享类型。
 *
 * 该文件位于 src/types 下，对主进程（electron/main）、预加载（electron/preload）
 * 与渲染端（src）三套 tsc 工程均可见，是 IPC 边界上 creation:* 卡片导入导出端点的
 * 唯一类型来源。深模块（electron/main/creation-card-io）保留其内部专用类型；
 * 渲染端 card-import-export-types 复用本文件（保持单一事实来源）。
 * 运行期数据结构需与本文件保持一致（深模块 card-io-types 为同形状镜像）。
 */

export type CardIoFieldKind =
  | "text"
  | "multiline"
  | "number"
  | "date"
  | "select"
  | "multiSelect"
  | "boolean"
  | "cardRef"
  | "url"
  | "attachment";

export interface CardIoFieldSchema {
  key: string;
  label: string;
  kind: CardIoFieldKind;
  required?: boolean;
  options?: string[];
}

export type CardRef =
  | { source: "existing"; id: string }
  | { source: "batch"; rowIndex: number };

export interface CardImportTypeInfo {
  kind: string;
  name: string;
  fields: CardIoFieldSchema[];
}

export interface CardImportRelationTypeInfo {
  id: string;
  name: string;
  forwardName: string;
  reverseName: string;
  fromKinds: string[];
  toKinds: string[];
}

export interface CardExistingRef {
  id: string;
  title: string;
  aliases: string[];
  kind: string;
}

export interface CardImportSchemaContext {
  projectId: string;
  cardTypes: CardImportTypeInfo[];
  relationTypes: CardImportRelationTypeInfo[];
  existingCards: CardExistingRef[];
}

export interface CardImportRowPreview {
  index: number;
  fields: Record<string, string>;
  notes: string[];
}

export interface CardImportPreview {
  format: "csv" | "markdown";
  rows: CardImportRowPreview[];
  headers?: string[];
  duplicateHeaders?: string[];
  warnings: string[];
}

export interface CardImportMapping {
  format: "csv" | "markdown";
  titleSource: string;
  typeSource?: string;
  typeFallback?: string;
  headingTypeDelimiter?: string;
  aliasSources?: string[];
  tagSources?: string[];
  tagDelimiters?: string[];
  fieldSources?: Record<string, string>;
  relationSources?: Record<string, { relationTypeId: string; direction: "from" | "to" }>;
}

export interface CardImportOptions {
  duplicatePolicy?: "add" | "skip";
}

export interface CardImportRowError {
  rowIndex: number;
  messages: string[];
}

export interface CardImportPlan {
  projectId: string;
  cards: Array<{
    rowIndex: number;
    kind: string;
    title: string;
    aliases: string[];
    tags: string[];
    fields: Record<string, unknown>;
    cardRefFields: Record<string, CardRef>;
  }>;
  relations: Array<{
    relationTypeId: string;
    from: CardRef;
    to: CardRef;
  }>;
  skippedRowCount: number;
  errorCount: number;
  errors: CardImportRowError[];
  warnings: string[];
}

export interface CardImportApplyResult {
  planId: string;
  appliedCardCount: number;
  appliedRelationCount: number;
}

export interface CardExportFilter {
  cardKind?: string;
  search?: string;
  ids?: string[];
}

/** 导出行（已解析，不携带内部 id/批注/元数据）。 */
export interface CardExportRow {
  title: string;
  kindLabel: string;
  aliases: string[];
  tags: string[];
  fields: Record<string, string>;
}

export const DEFAULT_TAG_DELIMITERS: readonly string[] = [",", "、", "|", "\n", "；", ";"];

export const MARKDOWN_CARD_HEADING_LEVEL = 2;

/** 由主进程 dialog 打开并读取源文件后返回的待解析文本。 */
export interface CardImportSource {
  format: "csv" | "markdown";
  text: string;
}

/** 导出写出的结果（主进程弹出保存对话框并写出；用户取消则 canceled=true）。 */
export interface CardExportResult {
  canceled: boolean;
  written: number;
}

/** 卡片导入 apply 入参（主进程按 projectId 读模式上下文后单事务写入）。 */
export interface CardImportApplyInput {
  projectId: string;
  text: string;
  format: "csv" | "markdown";
  mapping: CardImportMapping;
  options?: CardImportOptions;
}
