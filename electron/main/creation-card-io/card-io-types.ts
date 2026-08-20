/**
 * 卡片 CSV/Markdown 映射导入与筛选导出的共享类型与最小常量。
 *
 * 本文件为「独立深模块」：不依赖 electron、better-sqlite3 或 creation-workspace/index，
 * 仅声明纯数据类型与解析/规划所需的常量，供 parser / plan / export / apply 共用。
 */

/** 字段类型，与主进程 card_types.fields_json 的 kind 对齐。 */
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

/**
 * 卡片引用：既可能指向已存在于库中的卡片（existing），
 * 也可能指向本次批量导入中（尚未落库）的另一行（batch）。
 * apply 阶段再把 batch 引用解析为真实生成的主键。
 */
export type CardRef =
  | { source: "existing"; id: string }
  | { source: "batch"; rowIndex: number };

/** 导入所需的项目级模式上下文，由主进程在 apply 前从库读取后注入（纯数据，无 DB 依赖）。 */
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

/** 解析后的预览行（尚未映射/校验），直接来自源文本。 */
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

/** 用户的字段映射配置。 */
export interface CardImportMapping {
  format: "csv" | "markdown";
  /** 标题来源字段键（CSV 表头或 Markdown 块字段键）。 */
  titleSource: string;
  /** 类型来源字段键：其值为卡片类型「名称」或「kind」；为空时回退 typeFallback。 */
  typeSource?: string;
  /** 当 typeSource 为空时使用的卡片类型 kind（必须存在于项目）。 */
  typeFallback?: string;
  /** 若标题来自 heading 且设置此分隔符，则按「类型<分隔符>标题」拆分（如「角色：林晚」）。 */
  headingTypeDelimiter?: string;
  aliasSources?: string[];
  tagSources?: string[];
  /** 标签/多选值拆分符（默认见 DEFAULT_TAG_DELIMITERS）。 */
  tagDelimiters?: string[];
  /** 自定义字段：fieldKey -> 源字段键。 */
  fieldSources?: Record<string, string>;
  /** 关系：源字段键 -> 关系类型与方向。源字段值为目标卡片标题。 */
  relationSources?: Record<string, { relationTypeId: string; direction: "from" | "to" }>;
}

export interface CardImportOptions {
  /** 重复策略：默认 "add" 仅新增；"skip" 跳过标题/别名与现有卡片冲突的行。 */
  duplicatePolicy?: "add" | "skip";
}

export interface PlannedCard {
  rowIndex: number;
  kind: string;
  title: string;
  aliases: string[];
  tags: string[];
  fields: Record<string, unknown>;
  /** cardRef 类型字段解析后的引用，apply 阶段写为目标卡片 id。 */
  cardRefFields: Record<string, CardRef>;
}
export interface PlannedRelation {
  relationTypeId: string;
  from: CardRef;
  to: CardRef;
}
export interface CardImportRowError {
  rowIndex: number;
  messages: string[];
}
export interface CardImportPlan {
  projectId: string;
  cards: PlannedCard[];
  relations: PlannedRelation[];
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

/** 导出行（已解析，不携带内部 id/批注/元数据）。 */
export interface CardExportRow {
  title: string;
  kindLabel: string;
  aliases: string[];
  tags: string[];
  /** 自定义字段：fieldKey -> 已序列化为可读字符串的值（cardRef 已转为目标卡片标题）。 */
  fields: Record<string, string>;
}
export interface CardExportFilter {
  cardKind?: string;
  search?: string;
  ids?: string[];
}

/** 默认标签/多选值拆分符。 */
export const DEFAULT_TAG_DELIMITERS: readonly string[] = [",", "、", "|", "\n", "；", ";"];

/** Markdown 导出用的标题层级。 */
export const MARKDOWN_CARD_HEADING_LEVEL = 2;
