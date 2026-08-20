import { useState } from "react";
import { ArrowDown, ArrowUp, Pencil, Plus, Trash2, X } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { CardFieldKind, CardFieldSchema, CardSummary, CardType, RelationType } from "@/types/creation";

const FIELD_KINDS: { value: CardFieldKind; label: string }[] = [
  { value: "text", label: "单行文本" },
  { value: "multiline", label: "多行文本" },
  { value: "number", label: "数字" },
  { value: "date", label: "日期/相对时间" },
  { value: "select", label: "单选" },
  { value: "multiSelect", label: "多选" },
  { value: "boolean", label: "布尔" },
  { value: "cardRef", label: "卡片引用" },
  { value: "url", label: "URL" },
  { value: "attachment", label: "附件声明" }
];

const KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;

interface DraftField {
  key: string;
  label: string;
  kind: CardFieldKind;
  required: boolean;
  optionsText: string;
  defaultText: string;
  defaultBool: boolean;
  existing: boolean;
}

function emptyField(): DraftField {
  return {
    key: "",
    label: "",
    kind: "text",
    required: false,
    optionsText: "",
    defaultText: "",
    defaultBool: false,
    existing: false
  };
}

function fieldToDraft(field: CardFieldSchema): DraftField {
  return {
    key: field.key,
    label: field.label,
    kind: field.kind,
    required: field.required === true,
    optionsText: field.options?.join("\n") ?? "",
    defaultText: field.defaultValue === undefined || typeof field.defaultValue === "boolean" ? "" : String(field.defaultValue),
    defaultBool: field.defaultValue === true,
    existing: true
  };
}

function parseOptions(text: string): string[] {
  return text
    .split(/[\n,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function parseDefault(field: DraftField): { value?: unknown; error?: string } {
  if (field.kind === "boolean") {
    return field.defaultBool ? { value: true } : {};
  }
  if (field.kind === "multiSelect" || field.kind === "cardRef" || field.kind === "attachment") {
    return {};
  }
  const raw = field.defaultText.trim();
  if (raw === "") return {};
  if (field.kind === "number") {
    const numeric = Number(raw);
    if (Number.isNaN(numeric)) return { error: `字段「${field.label || field.key}」的默认值必须是数字。` };
    return { value: numeric };
  }
  if (field.kind === "select") {
    const options = parseOptions(field.optionsText);
    if (!options.includes(raw)) return { error: `字段「${field.label || field.key}」的默认值必须是选项之一。` };
  }
  return { value: raw };
}

interface CardTypeEditorProps {
  projectId: string;
  cardTypes?: CardType[];
  cards?: CardSummary[];
  relationTypes?: RelationType[];
  onClose: () => void;
}

export function CardTypeEditor({ projectId, cardTypes = [], cards = [], relationTypes = [], onClose }: CardTypeEditorProps) {
  const actions = useCreationActions();
  const { runStructure, loadCardTypes, loadCards } = actions;
  const showToast = useUIStore((state) => state.showToast);

  const [name, setName] = useState("");
  const [fields, setFields] = useState<DraftField[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [editingType, setEditingType] = useState<CardType | null>(null);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null);

  const updateField = (index: number, patch: Partial<DraftField>) => {
    setFields((current) => current.map((field, i) => (i === index ? { ...field, ...patch } : field)));
  };

  const addField = () => setFields((current) => [...current, emptyField()]);

  const removeField = (index: number) => setFields((current) => current.filter((_, i) => i !== index));

  const moveField = (index: number, direction: -1 | 1) => {
    setFields((current) => {
      const target = index + direction;
      if (target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const resetDraft = () => {
    setEditingType(null);
    setName("");
    setFields([]);
    setError(null);
    setConfirmingDeleteId(null);
  };

  const editType = (type: CardType) => {
    if (type.projectId === null) return;
    setEditingType(type);
    setName(type.name);
    setFields(type.fields.map(fieldToDraft));
    setError(null);
    setConfirmingDeleteId(null);
  };

  const impactFor = (type: CardType) => ({
    cards: cards.filter((card) => card.kind === type.kind).length,
    relationTypes: relationTypes.filter(
      (relationType) => relationType.fromKinds.includes(type.kind) || relationType.toKinds.includes(type.kind)
    ).length
  });

  const deleteType = async (type: CardType) => {
    const impact = impactFor(type);
    if (type.projectId === null || impact.cards > 0 || impact.relationTypes > 0) return;
    if (confirmingDeleteId !== type.id) {
      setConfirmingDeleteId(type.id);
      return;
    }
    setSubmitting(true);
    setError(null);
    const ok = await actions.deleteCardType({
      type: "cardType.delete",
      cardTypeId: type.id,
      baseRevision: type.revision
    });
    setSubmitting(false);
    if (!ok) {
      setError("删除卡片类型失败，请刷新后重试。");
      return;
    }
    showToast({ tone: "success", title: "卡片类型已删除" });
    resetDraft();
    void loadCardTypes(projectId);
    void loadCards({ projectId });
  };

  const submit = async () => {
    setError(null);
    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("请填写类型名称。");
      return;
    }
    const seenKeys = new Set<string>();
    const schema: CardFieldSchema[] = [];
    for (const field of fields) {
      const trimmedKey = field.key.trim();
      const trimmedLabel = field.label.trim();
      if (!KEY_PATTERN.test(trimmedKey)) {
        setError(`字段 key「${field.key}」必须为字母开头、仅含字母数字下划线的稳定标识。`);
        return;
      }
      if (!trimmedLabel) {
        setError("每个字段都需要填写名称（label）。");
        return;
      }
      if (seenKeys.has(trimmedKey)) {
        setError(`字段 key「${trimmedKey}」重复，请使用唯一标识。`);
        return;
      }
      seenKeys.add(trimmedKey);
      const entry: CardFieldSchema = { key: trimmedKey, label: trimmedLabel, kind: field.kind };
      if (field.required) entry.required = true;
      if (field.kind === "select" || field.kind === "multiSelect") {
        const options = parseOptions(field.optionsText);
        if (options.length === 0) {
          setError(`单选/多选字段「${trimmedLabel}」至少需要一个有效选项。`);
          return;
        }
        entry.options = options;
      }
      const parsed = parseDefault(field);
      if (parsed.error) {
        setError(parsed.error);
        return;
      }
      if (parsed.value !== undefined) entry.defaultValue = parsed.value;
      schema.push(entry);
    }

    setSubmitting(true);
    const ok = editingType
      ? await actions.updateCardType({
          type: "cardType.update",
          cardTypeId: editingType.id,
          name: trimmedName,
          fields: schema,
          baseRevision: editingType.revision
        })
      : await runStructure({
          type: "cardType.create",
          projectId,
          name: trimmedName,
          fields: schema
        });
    setSubmitting(false);
    if (!ok) {
      setError(editingType ? "保存卡片类型失败，请刷新后重试。" : "创建卡片类型失败，请重试。");
      return;
    }
    if (editingType) {
      showToast({ tone: "success", title: "卡片类型已更新" });
      void loadCardTypes(projectId);
      void loadCards({ projectId });
      resetDraft();
    } else {
      showToast({ tone: "success", title: "卡片类型已创建", body: "稳定标识由系统生成，可在卡片类型列表中查看。" });
      void loadCardTypes(projectId);
      void loadCards({ projectId });
      onClose();
    }
  };

  return (
    <div className="cards-modal-overlay" role="dialog" aria-label="管理卡片类型">
      <div className="cards-modal">
        <header className="cards-modal-head">
          <h3>管理卡片类型</h3>
          <button type="button" className="cards-close" onClick={onClose} title="关闭"><X size={15} /></button>
        </header>
        <div className="cards-modal-body">
          {cardTypes.length > 0 && (
            <section className="cards-type-list" aria-label="现有卡片类型">
              {cardTypes.map((type) => {
                const impact = impactFor(type);
                const builtin = type.projectId === null;
                const blocked = impact.cards > 0 || impact.relationTypes > 0;
                return (
                  <article key={type.id} className="cards-type-list-item">
                    <div>
                      <strong>{type.name}</strong>
                      <code>{type.kind}</code>
                      <small>{builtin ? "内置类型，不可编辑或删除" : `引用影响：${impact.cards} 张卡片，${impact.relationTypes} 个关系类型`}</small>
                    </div>
                    <div className="cards-type-list-actions">
                      <button type="button" className="cards-icon-btn" disabled={builtin} onClick={() => editType(type)} aria-label={`编辑${type.name}`}>
                        <Pencil size={13} />
                      </button>
                      <button
                        type="button"
                        className={`cards-icon-btn cards-danger ${confirmingDeleteId === type.id ? "confirming" : ""}`}
                        disabled={builtin || blocked || submitting}
                        title={blocked ? `仍被 ${impact.cards} 张卡片和 ${impact.relationTypes} 个关系类型引用` : builtin ? "内置类型不可删除" : "删除类型"}
                        aria-label={confirmingDeleteId === type.id ? `确认删除${type.name}` : `删除${type.name}`}
                        onClick={() => void deleteType(type)}
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </article>
                );
              })}
            </section>
          )}
          <h4 className="cards-type-editor-title">{editingType ? `编辑：${editingType.name}` : "新建卡片类型"}</h4>
          {editingType && (
            <div className="cards-field">
              <label className="cards-field-label">稳定 kind</label>
              <input className="cards-input" value={editingType.kind} disabled />
            </div>
          )}
          <div className="cards-field">
            <label className="cards-field-label">类型名称<span className="cards-required">*</span></label>
            <input
              className="cards-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="例如：功法、神兵"
            />
          </div>
          <p className="cards-hint">稳定标识（kind）由系统生成，创建后不再变化；下方每个字段的 key 也必须在类型内唯一且创建后保持稳定。</p>

          <div className="cards-fields-manager">
            <header className="cards-fields-manager-head">
              <h4>字段（{fields.length}）</h4>
              <button type="button" className="cards-add" onClick={addField}><Plus size={13} /> 添加字段</button>
            </header>
            {fields.length === 0 && <p className="cards-relations-empty">还没有字段。可添加单行文本、数字、日期、单选、附件声明等。</p>}
            {fields.map((field, index) => (
              <div key={index} className="cte-field-row">
                <div className="cte-field-row-head">
                  <span className="cte-field-index">#{index + 1}</span>
                  <button
                    type="button"
                    className="cards-icon-btn"
                    disabled={index === 0}
                    onClick={() => moveField(index, -1)}
                    title="上移"
                  >
                    <ArrowUp size={13} />
                  </button>
                  <button
                    type="button"
                    className="cards-icon-btn"
                    disabled={index === fields.length - 1}
                    onClick={() => moveField(index, 1)}
                    title="下移"
                  >
                    <ArrowDown size={13} />
                  </button>
                  <button type="button" className="cards-icon-btn cards-danger" disabled={field.existing} onClick={() => removeField(index)} title={field.existing ? "既有字段 key 必须保持稳定" : "删除字段"}>
                    <Trash2 size={13} />
                  </button>
                </div>
                <div className="cte-field-grid">
                  <div className="cards-field">
                    <label className="cards-field-label">名称<span className="cards-required">*</span></label>
                    <input className="cards-input" value={field.label} onChange={(e) => updateField(index, { label: e.target.value })} placeholder="字段显示名" />
                  </div>
                  <div className="cards-field">
                    <label className="cards-field-label">稳定 key<span className="cards-required">*</span></label>
                    <input className="cards-input" value={field.key} disabled={field.existing} onChange={(e) => updateField(index, { key: e.target.value })} placeholder="如 magic_name" />
                  </div>
                  <div className="cards-field">
                    <label className="cards-field-label">类型</label>
                    <select className="cards-input" value={field.kind} onChange={(e) => updateField(index, { kind: e.target.value as CardFieldKind })}>
                      {FIELD_KINDS.map((kind) => (
                        <option key={kind.value} value={kind.value}>{kind.label}</option>
                      ))}
                    </select>
                  </div>
                </div>
                {(field.kind === "select" || field.kind === "multiSelect") && (
                  <div className="cards-field">
                    <label className="cards-field-label">选项（每行或逗号分隔）</label>
                    <textarea
                      className="cards-input cards-textarea"
                      value={field.optionsText}
                      onChange={(e) => updateField(index, { optionsText: e.target.value })}
                      placeholder={"选项 A\n选项 B"}
                    />
                  </div>
                )}
                {(field.kind === "text" || field.kind === "multiline" || field.kind === "url" || field.kind === "number" || field.kind === "date" || field.kind === "select") && (
                  <div className="cards-field">
                    <label className="cards-field-label">默认值（可选）</label>
                    {field.kind === "number" ? (
                      <input className="cards-input" type="number" value={field.defaultText} onChange={(e) => updateField(index, { defaultText: e.target.value })} placeholder="留空表示无默认" />
                    ) : field.kind === "date" ? (
                      <input className="cards-input" type="date" value={field.defaultText} onChange={(e) => updateField(index, { defaultText: e.target.value })} />
                    ) : (
                      <input className="cards-input" value={field.defaultText} onChange={(e) => updateField(index, { defaultText: e.target.value })} placeholder="留空表示无默认" />
                    )}
                  </div>
                )}
                {field.kind === "boolean" && (
                  <label className="cards-field cards-field-inline">
                    <input type="checkbox" checked={field.defaultBool} onChange={(e) => updateField(index, { defaultBool: e.target.checked })} />
                    默认选中（true）
                  </label>
                )}
                <label className="cards-field cards-field-inline">
                  <input type="checkbox" checked={field.required} onChange={(e) => updateField(index, { required: e.target.checked })} />
                  必填
                </label>
              </div>
            ))}
          </div>
          {error && <p className="cards-form-error" role="alert">{error}</p>}
        </div>
        <footer className="cards-form-actions cards-modal-foot">
          <button type="button" className="cards-save" disabled={submitting} onClick={() => void submit()}>{editingType ? "保存修改" : "创建类型"}</button>
          <button type="button" className="cards-cancel" onClick={editingType ? resetDraft : onClose}>{editingType ? "取消编辑" : "取消"}</button>
        </footer>
      </div>
    </div>
  );
}
