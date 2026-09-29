import { useMemo } from "react";
import { X } from "lucide-react";
import { Button, Select } from "@/components/ui";
import { CardDynamicFields } from "./CardDynamicFields";
import type { CardSummary, CardType } from "@/types/creation";

export function parseList(value: string): string[] {
  return value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export interface CardEditorFormProps {
  draft: CardSummary;
  cardTypes: CardType[];
  projectCards: CardSummary[];
  onChangeDraft: (draft: CardSummary) => void;
  onSave: () => void | Promise<void>;
  onCancel: () => void;
}

export function CardEditorForm({
  draft,
  cardTypes,
  projectCards,
  onChangeDraft,
  onSave,
  onCancel
}: CardEditorFormProps) {
  const typeById = useMemo(
    () => new Map(cardTypes.map((type) => [type.kind, type])),
    [cardTypes]
  );
  const draftFields = typeById.get(draft.kind)?.fields ?? [];

  return (
    <div className="cards-detail-card">
      <header className="cards-detail-head">
        <h3>{draft.id === "new" ? "新建卡片" : "编辑卡片"}</h3>
        <button type="button" className="cards-close" onClick={onCancel} title="关闭" aria-label="关闭卡片编辑">
          <X size={15} />
        </button>
      </header>
      <div className="cards-form">
        <div className="cards-field">
          <label className="cards-field-label">类型</label>
          <Select
            className="cards-input"
            value={draft.kind}
            disabled={draft.id !== "new"}
            onChange={(event) => onChangeDraft({ ...draft, kind: event.target.value, fields: {} })}
          >
            {cardTypes.map((type) => (
              <option key={type.id} value={type.kind}>{type.name}</option>
            ))}
          </Select>
        </div>
        <div className="cards-field">
          <label className="cards-field-label">名称<span className="cards-required">*</span></label>
          <input
            className="cards-input"
            value={draft.title}
            onChange={(event) => onChangeDraft({ ...draft, title: event.target.value })}
            placeholder="卡片主名称"
          />
        </div>
        <div className="cards-field">
          <label className="cards-field-label">别名</label>
          <input
            className="cards-input"
            value={draft.aliases.join("，")}
            onChange={(event) => onChangeDraft({ ...draft, aliases: parseList(event.target.value) })}
            placeholder="用逗号分隔多个别名"
          />
        </div>
        <CardDynamicFields
          fields={draftFields}
          values={draft.fields}
          onChange={(key, value) =>
            onChangeDraft({ ...draft, fields: { ...draft.fields, [key]: value } })
          }
          allCards={projectCards}
        />
        <div className="cards-field">
          <label className="cards-field-label">标签</label>
          <input
            className="cards-input"
            value={draft.tags.join("，")}
            onChange={(event) => onChangeDraft({ ...draft, tags: parseList(event.target.value) })}
            placeholder="用逗号分隔，如：主角，战斗"
          />
        </div>
        <div className="cards-form-actions">
          <Button variant="primary" onClick={() => void onSave()}>
            {draft.id === "new" ? "创建卡片" : "保存修改"}
          </Button>
          <Button variant="outline" onClick={onCancel}>
            取消
          </Button>
        </div>
      </div>
    </div>
  );
}
