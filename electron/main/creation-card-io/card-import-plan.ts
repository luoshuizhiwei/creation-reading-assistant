/**
 * 卡片导入规划器（纯函数，无 DB / electron 依赖）。
 *
 * 输入：预览(preview) + 用户映射(mapping) + 项目模式上下文(schemaContext) + 选项(options)。
 * 输出：CardImportPlan（已校验、可直接 apply 的结构）。
 *
 * 关键不变量：
 * - 类型、字段、关系引用在 apply 之前全部在此校验；有错误的行不会进入 plan.cards。
 * - 默认只新增（duplicatePolicy 默认 "add"），绝不修改/覆盖现有卡片。
 * - 不读取任何「原因/备注」文本来推断类型，类型始终由系统受控字段或映射显式得出。
 */

import {
  type CardImportMapping,
  type CardImportOptions,
  type CardImportPlan,
  type CardImportPreview,
  type CardImportRowError,
  type CardImportSchemaContext,
  type CardIoFieldSchema,
  type CardRef,
  type PlannedCard,
  type PlannedRelation,
  DEFAULT_TAG_DELIMITERS
} from "./card-io-types";

interface BuiltRow {
  rowIndex: number;
  kind: string;
  title: string;
  aliases: string[];
  tags: string[];
  fields: Record<string, unknown>;
  cardRefFields: Record<string, CardRef>;
  cardRefRaw: Record<string, string>;
  errors: string[];
}

function splitTokens(value: string, delimiters: readonly string[]): string[] {
  let parts = [value];
  for (const d of delimiters) {
    const next: string[] = [];
    for (const p of parts) next.push(...p.split(d));
    parts = next;
  }
  return parts.map((p) => p.trim()).filter((p) => p !== "");
}

function parseBoolean(raw: string): boolean | undefined {
  const v = raw.trim().toLowerCase();
  if (["是", "true", "yes", "1", "对", "真", "y"].includes(v)) return true;
  if (["否", "false", "no", "0", "错", "假", "n"].includes(v)) return false;
  return undefined;
}

/** 构建单个字段值并校验；返回 { value?, error? }。cardRef 在 pass B 单独处理。 */
function buildScalarField(
  schema: CardIoFieldSchema,
  raw: string
): { value?: unknown; error?: string } {
  const text = raw.trim();
  const absent = text === "";
  switch (schema.kind) {
    case "text":
    case "multiline":
    case "url":
    case "attachment":
    case "date":
      return { value: text };
    case "number": {
      if (absent) return {};
      const num = Number(text);
      if (!Number.isFinite(num)) return { error: `字段「${schema.label}」必须为数字，收到「${text}」。` };
      return { value: num };
    }
    case "boolean": {
      if (absent) return {};
      const b = parseBoolean(text);
      if (b === undefined) return { error: `字段「${schema.label}」必须为布尔（是/否/true/false），收到「${text}」。` };
      return { value: b };
    }
    case "select": {
      if (absent) return {};
      const opts = schema.options ?? [];
      if (!opts.includes(text)) return { error: `字段「${schema.label}」取值「${text}」不在选项 ${opts.join("/")} 中。` };
      return { value: text };
    }
    case "multiSelect": {
      if (absent) return {};
      const opts = schema.options ?? [];
      const chosen = splitTokens(text, DEFAULT_TAG_DELIMITERS);
      for (const c of chosen) {
        if (!opts.includes(c)) return { error: `字段「${schema.label}」取值「${c}」不在选项 ${opts.join("/")} 中。` };
      }
      return { value: chosen };
    }
    case "cardRef":
      // 在 pass B 处理
      return {};
    default:
      return { error: `不支持的字段类型：${schema.kind}` };
  }
}

/** 在现有卡片与本次批量（按标题）中解析卡片引用。 */
function resolveCardRef(
  value: string,
  existingByTitle: Map<string, CardRef>,
  existingByAlias: Map<string, CardRef>,
  batchByTitle: Map<string, number>
): CardRef | null {
  const title = value.trim();
  if (title === "") return null;
  const existing = existingByTitle.get(title) ?? existingByAlias.get(title);
  if (existing) return existing;
  const rowIndex = batchByTitle.get(title);
  if (rowIndex !== undefined) return { source: "batch", rowIndex };
  return null;
}

export function buildCardImportPlan(
  preview: CardImportPreview,
  mapping: CardImportMapping,
  schema: CardImportSchemaContext,
  options: CardImportOptions = {}
): CardImportPlan {
  const warnings = [...preview.warnings];
  const duplicatePolicy = options.duplicatePolicy ?? "add";
  const tagDelimiters = mapping.tagDelimiters && mapping.tagDelimiters.length > 0 ? mapping.tagDelimiters : DEFAULT_TAG_DELIMITERS;

  // 类型名/ kind -> kind
  const typeByKey = new Map<string, CardImportSchemaContext["cardTypes"][number]>();
  for (const t of schema.cardTypes) {
    typeByKey.set(t.kind, t);
    typeByKey.set(t.name, t);
  }
  // 现有卡片引用表（标题 + 别名 -> existing ref）
  const existingByTitle = new Map<string, CardRef>();
  const existingByAlias = new Map<string, CardRef>();
  for (const c of schema.existingCards) {
    existingByTitle.set(c.title, { source: "existing", id: c.id });
    for (const a of c.aliases) existingByAlias.set(a, { source: "existing", id: c.id });
  }

  const built: BuiltRow[] = [];
  const rowErrors: CardImportRowError[] = [];

  // Pass A：标题、类型、别名、标签、标量字段、必填（不含 cardRef 解析）。
  for (const row of preview.rows) {
    const fields = row.fields;
    const errs: string[] = [...row.notes];
    let title = (mapping.titleSource ? fields[mapping.titleSource] ?? "" : "").trim();
    let typeName = mapping.typeSource ? (fields[mapping.typeSource] ?? "").trim() : "";

    // 标题来自 heading 且配置了分隔符：按「类型<分隔符>标题」拆分。
    if (mapping.headingTypeDelimiter && mapping.titleSource === "heading" && title.includes(mapping.headingTypeDelimiter)) {
      const idx = title.indexOf(mapping.headingTypeDelimiter);
      const maybeType = title.slice(0, idx).trim();
      const rest = title.slice(idx + mapping.headingTypeDelimiter.length).trim();
      if (maybeType !== "") {
        typeName = typeName || maybeType;
        title = rest;
      }
    }

    if (title === "") errs.push("缺少标题（映射字段为空）。");
    if (typeName === "" && !mapping.typeFallback) {
      errs.push("缺少卡片类型（未映射类型列且无默认类型）。");
    }
    const typeInfo = typeName !== "" ? typeByKey.get(typeName) : undefined;
    const resolvedType = typeInfo ?? (mapping.typeFallback ? typeByKey.get(mapping.typeFallback) : undefined);
    if (!resolvedType) {
      errs.push(`未知卡片类型「${typeName || mapping.typeFallback}」。`);
    }

    const aliases = (mapping.aliasSources ?? [])
      .flatMap((src) => splitTokens(fields[src] ?? "", tagDelimiters))
      .filter((a) => a !== title);
    const tags = (mapping.tagSources ?? [])
      .flatMap((src) => splitTokens(fields[src] ?? "", tagDelimiters));

    const fields_out: Record<string, unknown> = {};
    const cardRefFields: Record<string, CardRef> = {};
    const cardRefRaw: Record<string, string> = {};
    if (resolvedType) {
      const fieldByKey = new Map(resolvedType.fields.map((f) => [f.key, f]));
      for (const [fieldKey, srcKey] of Object.entries(mapping.fieldSources ?? {})) {
        const fSchema = fieldByKey.get(fieldKey);
        if (!fSchema) {
          errs.push(`字段「${fieldKey}」不属于卡片类型「${resolvedType.name}」。`);
          continue;
        }
        const raw = (fields[srcKey] ?? "").trim();
        if (fSchema.kind === "cardRef") {
          // 留待 pass B 解析为真实引用
          if (raw !== "") cardRefRaw[fieldKey] = raw;
          continue;
        }
        const builtField = buildScalarField(fSchema, raw);
        if (builtField.error) {
          errs.push(builtField.error);
          continue;
        }
        if (builtField.value !== undefined) fields_out[fieldKey] = builtField.value;
      }
      // 必填校验（标量 + cardRef）
      for (const fSchema of resolvedType.fields) {
        const present =
          Object.prototype.hasOwnProperty.call(fields_out, fSchema.key) ||
          (fSchema.kind === "cardRef" && Object.prototype.hasOwnProperty.call(cardRefRaw, fSchema.key));
        if (fSchema.required && !present) {
          errs.push(`必填字段「${fSchema.label}」未填写。`);
        }
      }
    }

    built.push({
      rowIndex: row.index,
      kind: resolvedType?.kind ?? "",
      title,
      aliases,
      tags,
      fields: fields_out,
      cardRefFields,
      cardRefRaw,
      errors: errs
    });
  }

  // 批量内按标题解析（仅用通过 pass A 且标题非空的行）。
  const batchByTitle = new Map<string, number>();
  for (const b of built) {
    if (b.errors.length === 0 && b.title !== "") batchByTitle.set(b.title, b.rowIndex);
  }

  // Pass B：解析 cardRef 字段与关系引用（现有 + 批量）。
  const relationAcc: Array<{ relationTypeId: string; from: CardRef; to: CardRef }> = [];
  for (const b of built) {
    // cardRef 字段
    for (const [fieldKey, rawTitle] of Object.entries(b.cardRefRaw)) {
      const resolved = resolveCardRef(rawTitle, existingByTitle, existingByAlias, batchByTitle);
      if (!resolved) {
        b.errors.push(`卡片引用字段无法解析为已存在或本次导入的卡片：「${rawTitle}」。`);
      } else {
        b.cardRefFields[fieldKey] = resolved;
      }
    }
    // 关系
    for (const [srcKey, rel] of Object.entries(mapping.relationSources ?? {})) {
      const raw = (preview.rows.find((r) => r.index === b.rowIndex)?.fields[srcKey] ?? "").trim();
      if (raw === "") continue;
      const targets = splitTokens(raw, tagDelimiters);
      for (const target of targets) {
        const ref = resolveCardRef(target, existingByTitle, existingByAlias, batchByTitle);
        if (!ref) {
          b.errors.push(`关系「${rel.relationTypeId}」的目标卡片未找到：「${target}」。`);
          continue;
        }
        const from: CardRef = rel.direction === "from" ? { source: "batch", rowIndex: b.rowIndex } : ref;
        const to: CardRef = rel.direction === "to" ? { source: "batch", rowIndex: b.rowIndex } : ref;
        relationAcc.push({ relationTypeId: rel.relationTypeId, from, to });
      }
    }
  }

  // 组装 plan：排除有错误的行，按 skip 策略跳过重复。
  const validRowIndices = new Set<number>();
  const cards: PlannedCard[] = [];
  let skippedRowCount = 0;
  const existingTitleSet = new Set(schema.existingCards.map((c) => c.title));
  const existingAliasSet = new Set(schema.existingCards.flatMap((c) => c.aliases));
  for (const b of built) {
    if (b.errors.length > 0) {
      rowErrors.push({ rowIndex: b.rowIndex, messages: b.errors });
      continue;
    }
    if (duplicatePolicy === "skip") {
      const conflicts = existingTitleSet.has(b.title) || b.aliases.some((a) => existingAliasSet.has(a));
      if (conflicts) {
        skippedRowCount += 1;
        warnings.push(`第 ${b.rowIndex + 1} 行「${b.title}」与现有卡片重复，已跳过（skip 策略）。`);
        continue;
      }
    }
    validRowIndices.add(b.rowIndex);
    cards.push({
      rowIndex: b.rowIndex,
      kind: b.kind,
      title: b.title,
      aliases: b.aliases,
      tags: b.tags,
      fields: b.fields,
      cardRefFields: b.cardRefFields
    });
  }

  // 关系只保留两端都可解析且两端行均有效的。
  const relations: PlannedRelation[] = [];
  for (const rel of relationAcc) {
    const fromOk = rel.from.source === "existing" || validRowIndices.has((rel.from as { rowIndex: number }).rowIndex);
    const toOk = rel.to.source === "existing" || validRowIndices.has((rel.to as { rowIndex: number }).rowIndex);
    if (fromOk && toOk) relations.push(rel);
  }

  return {
    projectId: schema.projectId,
    cards,
    relations,
    skippedRowCount,
    errorCount: rowErrors.length,
    errors: rowErrors,
    warnings
  };
}
