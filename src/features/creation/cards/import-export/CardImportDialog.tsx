/**
 * 卡片 CSV/Markdown 映射导入对话框。
 *
 * 流程：选择文件（主进程 dialog + 解析）→ 预览 → 用户映射字段 → 计算计划（apply 前校验）
 * → 确认应用（一次性 planId + 单事务，失败回滚零写入）。渲染端不接触文件路径。
 */
import { useMemo, useState } from "react";
import { FileUp, Loader2, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useUIStore } from "@/stores/ui-store";
import type {
  CardImportMapping,
  CardImportOptions,
  CardImportPlan,
  CardImportPreview,
  CardImportSchemaContext
} from "./card-import-export-types";
import {
  applyCardImport,
  buildDefaultMapping,
  loadImportSchema,
  mappingCanResolveType,
  mappingIsReady,
  openAndParseImportSource,
  parseImportSource,
  planCardImport,
  summarizePlan,
  type PlanSummary
} from "./card-import-export-client";

interface CardImportDialogProps {
  projectId: string;
  onClose(): void;
  onImported(): void;
}

type Phase = "choose" | "mapping" | "plan" | "done";

export function CardImportDialog({ projectId, onClose, onImported }: CardImportDialogProps) {
  const showToast = useUIStore((state) => state.showToast);
  const [phase, setPhase] = useState<Phase>("choose");
  const [busy, setBusy] = useState(false);

  const [sourceText, setSourceText] = useState("");
  const [sourceFormat, setSourceFormat] = useState<"csv" | "markdown">("csv");
  const [preview, setPreview] = useState<CardImportPreview | null>(null);
  const [schema, setSchema] = useState<CardImportSchemaContext | null>(null);
  const [mapping, setMapping] = useState<CardImportMapping | null>(null);
  const [options, setOptions] = useState<CardImportOptions>({ duplicatePolicy: "add" });
  const [, setPlan] = useState<CardImportPlan | null>(null);
  const [summary, setSummary] = useState<PlanSummary | null>(null);
  const [appliedInfo, setAppliedInfo] = useState<{ cards: number; relations: number } | null>(null);

  const chooseFile = async () => {
    setBusy(true);
    try {
      const source = await openAndParseImportSource();
      if (!source) {
        onClose();
        return;
      }
      const ctx = await loadImportSchema(projectId);
      const parsed = await parseImportSource({ text: source.text, format: source.format });
      setSourceText(source.text);
      setSourceFormat(source.format);
      setSchema(ctx);
      setPreview(parsed);
      setMapping(buildDefaultMapping(parsed, ctx));
      setPhase("mapping");
    } catch (error) {
      showToast({ tone: "error", title: "无法准备导入", body: errorMessage(error) });
      onClose();
    } finally {
      setBusy(false);
    }
  };

  // 选择文件阶段直接触发。
  if (phase === "choose" && !preview) {
    void chooseFile();
  }

  const computePlan = async () => {
    if (!sourceText || !mapping) return;
    setBusy(true);
    try {
      const result = await planCardImport({ projectId, text: sourceText, format: sourceFormat, mapping, options });
      setPlan(result);
      setSummary(summarizePlan(result));
      setPhase("plan");
    } catch (error) {
      showToast({ tone: "error", title: "无法生成导入计划", body: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const runApply = async () => {
    if (!sourceText || !mapping) return;
    setBusy(true);
    try {
      const result = await applyCardImport({ projectId, text: sourceText, format: sourceFormat, mapping, options });
      setAppliedInfo({ cards: result.appliedCardCount, relations: result.appliedRelationCount });
      setPhase("done");
      onImported();
    } catch (error) {
      showToast({ tone: "error", title: "导入失败（已回滚）", body: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const headers = preview?.format === "csv" ? preview.headers ?? [] : [];
  const scalarFields = useMemo(
    () => (schema?.cardTypes ?? []).flatMap((type) => type.fields.filter((f) => f.kind !== "cardRef")),
    [schema]
  );
  const relationTypes = schema?.relationTypes ?? [];

  return (
    <div className="creation-search-overlay" role="dialog" aria-label="导入卡片" aria-modal="true">
      <div className="creation-search-shell migration-dialog" role="search">
        <div className="creation-search-head">
          <FileUp size={16} className="creation-search-head-icon" />
          <span className="creation-proof-title">导入卡片（CSV / Markdown）</span>
          <button type="button" className="creation-search-close" onClick={onClose} aria-label="关闭导入对话框">
            <X size={16} />
          </button>
        </div>

        <div className="migration-body">
          {phase === "choose" && <p className="migration-note">正在读取文件…</p>}

          {phase === "mapping" && preview && mapping && schema && (
            <MappingEditor
              preview={preview}
              schema={schema}
              mapping={mapping}
              headers={headers}
              scalarFields={scalarFields}
              relationTypes={relationTypes}
              options={options}
              canResolveType={mappingCanResolveType(mapping)}
              onChange={setMapping}
              onOptionsChange={setOptions}
            />
          )}

          {phase === "plan" && summary && (
            <div className="card-import-plan">
              <p className="migration-note">
                计划：{summary.cardCount} 张卡片、{summary.relationCount} 条关系
                {summary.skipped > 0 && `（跳过 ${summary.skipped} 行）`}
                {summary.warningCount > 0 && `；${summary.warningCount} 条提示`}。
              </p>
              {summary.errorCount > 0 ? (
                <div className="card-import-errors" role="alert">
                  <p className="cards-form-error">有 {summary.errorCount} 行无法通过校验，需调整映射或数据源：</p>
                  <ul>
                    {summary.errorLines.map((line) => (
                      <li key={line}>{line}</li>
                    ))}
                  </ul>
                </div>
              ) : (
                <p className="migration-note">校验通过，可安全导入（默认仅新增，不覆盖现有卡片）。</p>
              )}
            </div>
          )}

          {phase === "done" && appliedInfo && (
            <p className="migration-note">
              已导入 {appliedInfo.cards} 张卡片、{appliedInfo.relations} 条关系（单事务写入，并记入审计）。
            </p>
          )}

          <div className="migration-actions">
            <Button variant="secondary" onClick={onClose}>
              {phase === "done" ? "完成" : "取消"}
            </Button>
            {phase === "mapping" && (
              <Button onClick={() => void computePlan()} disabled={busy || !mapping || !mappingIsReady(mapping)}>
                {busy ? <><Loader2 size={14} className="spin" /> 生成计划中…</> : "预览计划"}
              </Button>
            )}
            {phase === "plan" && summary && summary.errorCount === 0 && (
              <Button onClick={() => void runApply()} disabled={busy}>
                {busy ? <><Loader2 size={14} className="spin" /> 导入中…</> : "确认导入"}
              </Button>
            )}
            {phase === "plan" && (
              <Button variant="quiet" onClick={() => setPhase("mapping")} disabled={busy}>
                返回调整映射
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

interface MappingEditorProps {
  preview: CardImportPreview;
  schema: CardImportSchemaContext;
  mapping: CardImportMapping;
  headers: string[];
  scalarFields: Array<{ key: string; label: string; kind: string }>;
  relationTypes: CardImportSchemaContext["relationTypes"];
  options: CardImportOptions;
  canResolveType: boolean;
  onChange(mapping: CardImportMapping): void;
  onOptionsChange(options: CardImportOptions): void;
}

function MappingEditor({
  preview,
  schema,
  mapping,
  headers,
  scalarFields,
  relationTypes,
  options,
  canResolveType,
  onChange,
  onOptionsChange
}: MappingEditorProps) {
  const isMarkdown = preview.format === "markdown";
  const set = (patch: Partial<CardImportMapping>) => onChange({ ...mapping, ...patch });

  const toggleAlias = (header: string) => {
    const current = new Set(mapping.aliasSources ?? []);
    if (current.has(header)) current.delete(header);
    else current.add(header);
    set({ aliasSources: [...current] });
  };
  const toggleTag = (header: string) => {
    const current = new Set(mapping.tagSources ?? []);
    if (current.has(header)) current.delete(header);
    else current.add(header);
    set({ tagSources: [...current] });
  };
  const setFieldSource = (fieldKey: string, header: string) => {
    const next = { ...(mapping.fieldSources ?? {}) };
    if (header === "") delete next[fieldKey];
    else next[fieldKey] = header;
    set({ fieldSources: next });
  };

  return (
    <div className="card-import-mapping">
      <p className="migration-note">
        已解析 {preview.rows.length} 行
        {preview.warnings.length > 0 && `（${preview.warnings.length} 条解析提示）`}。
        请确认字段映射后再生成计划。
      </p>

      {!isMarkdown && (
        <label className="creation-proof-banned">
          <span>标题列</span>
          <select className="cards-input" value={mapping.titleSource} onChange={(e) => set({ titleSource: e.target.value })}>
            {headers.map((h) => (
              <option key={h} value={h}>{h || "（空列）"}</option>
            ))}
          </select>
        </label>
      )}

      {isMarkdown && (
        <label className="creation-proof-banned">
          <span>标题来自</span>
          <span className="cards-static">heading（Markdown 标题）</span>
        </label>
      )}

      <label className="creation-proof-banned">
        <span>类型列</span>
        {isMarkdown ? (
          <input
            className="cards-input"
            value={mapping.headingTypeDelimiter ?? ""}
            placeholder="如「：」用于「类型：标题」"
            onChange={(e) => set({ headingTypeDelimiter: e.target.value || undefined })}
          />
        ) : (
          <select
            className="cards-input"
            value={mapping.typeSource ?? ""}
            onChange={(e) => set({ typeSource: e.target.value || undefined })}
          >
            <option value="">（不按列判断，使用下方固定类型）</option>
            {headers.map((h) => (
              <option key={h} value={h}>{h || "（空列）"}</option>
            ))}
          </select>
        )}
      </label>

      {!mapping.typeSource && (
        <label className="creation-proof-banned">
          <span>固定类型</span>
          <select
            className="cards-input"
            value={mapping.typeFallback ?? ""}
            onChange={(e) => set({ typeFallback: e.target.value || undefined })}
          >
            <option value="">（请选择类型）</option>
            {schema.cardTypes.map((t) => (
              <option key={t.kind} value={t.kind}>{t.name}</option>
            ))}
          </select>
        </label>
      )}

      {!isMarkdown && (
        <fieldset className="card-import-fieldset">
          <legend>别名列（可多选）</legend>
          <div className="cards-checkbox-group">
            {headers.map((h) => (
              <label key={h} className="cards-checkbox">
                <input type="checkbox" checked={(mapping.aliasSources ?? []).includes(h)} onChange={() => toggleAlias(h)} />
                {h || "（空列）"}
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {!isMarkdown && (
        <fieldset className="card-import-fieldset">
          <legend>标签列（可多选，按拆分符切分）</legend>
          <div className="cards-checkbox-group">
            {headers.map((h) => (
              <label key={h} className="cards-checkbox">
                <input type="checkbox" checked={(mapping.tagSources ?? []).includes(h)} onChange={() => toggleTag(h)} />
                {h || "（空列）"}
              </label>
            ))}
          </div>
        </fieldset>
      )}

      {scalarFields.length > 0 && (
        <fieldset className="card-import-fieldset">
          <legend>自定义字段映射</legend>
          {scalarFields.map((field) => (
            <label key={field.key} className="creation-proof-banned">
              <span>{field.label}</span>
              <select
                className="cards-input"
                value={mapping.fieldSources?.[field.key] ?? ""}
                onChange={(e) => setFieldSource(field.key, e.target.value)}
              >
                <option value="">（不导入）</option>
                {isMarkdown
                  ? Object.keys(preview.rows[0]?.fields ?? {}).map((k) => (
                      <option key={k} value={k}>{k}</option>
                    ))
                  : headers.map((h) => (
                      <option key={h} value={h}>{h || "（空列）"}</option>
                    ))}
              </select>
            </label>
          ))}
        </fieldset>
      )}

      {relationTypes.length > 0 && (
        <fieldset className="card-import-fieldset">
          <legend>关系列（按目标卡片标题解析，可指向现有卡或本批新增卡）</legend>
          <RelationMapping
            preview={preview}
            headers={headers}
            relationTypes={relationTypes}
            mapping={mapping}
            onChange={set}
          />
        </fieldset>
      )}

      <label className="creation-proof-banned">
        <span>重复策略</span>
        <select
          className="cards-input"
          value={options.duplicatePolicy ?? "add"}
          onChange={(e) => onOptionsChange({ ...options, duplicatePolicy: e.target.value as "add" | "skip" })}
        >
          <option value="add">仅新增（不覆盖现有卡片）</option>
          <option value="skip">跳过与现有卡片冲突的行</option>
        </select>
      </label>

      {!canResolveType && (
        <p className="cards-form-error" role="alert">
          还需指定「类型列」或「固定类型」，否则无法解析卡片类型。
        </p>
      )}
    </div>
  );
}

function RelationMapping({
  preview,
  headers,
  relationTypes,
  mapping,
  onChange
}: {
  preview: CardImportPreview;
  headers: string[];
  relationTypes: CardImportSchemaContext["relationTypes"];
  mapping: CardImportMapping;
  onChange(patch: Partial<CardImportMapping>): void;
}) {
  const isMarkdown = preview.format === "markdown";
  const columns = isMarkdown ? Object.keys(preview.rows[0]?.fields ?? {}) : headers;
  const relationSources = mapping.relationSources ?? {};

  const setColumn = (column: string, relationTypeId: string, direction: "from" | "to") => {
    const next = { ...relationSources };
    if (relationTypeId === "") delete next[column];
    else next[column] = { relationTypeId, direction };
    onChange({ relationSources: next });
  };

  return (
    <div className="card-import-relation-map">
      {columns.map((column) => {
        const current = relationSources[column];
        return (
          <div key={column} className="card-import-relation-row">
            <span className="card-import-relation-col">{column || "（空列）"}</span>
            <select
              className="cards-input"
              value={current?.relationTypeId ?? ""}
              onChange={(e) => setColumn(column, e.target.value, current?.direction ?? "from")}
            >
              <option value="">（不作为关系）</option>
              {relationTypes.map((r) => (
                <option key={r.id} value={r.id}>{r.forwardName}（反向：{r.reverseName}）</option>
              ))}
            </select>
            <select
              className="cards-input"
              value={current?.direction ?? "from"}
              disabled={!current}
              onChange={(e) => setColumn(column, current?.relationTypeId ?? "", e.target.value as "from" | "to")}
            >
              <option value="from">本行 → 目标</option>
              <option value="to">目标 → 本行</option>
            </select>
          </div>
        );
      })}
    </div>
  );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
