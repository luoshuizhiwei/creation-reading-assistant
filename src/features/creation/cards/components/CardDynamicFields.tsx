import { Select } from "@/components/ui";
import type { CardFieldSchema, CardSummary } from "@/types/creation";

export function displayFieldValue(
  field: CardFieldSchema,
  value: unknown,
  projectCards: CardSummary[]
): string {
  if (value === undefined || value === null || value === "") return "—";
  if (field.kind === "cardRef" && typeof value === "string") {
    return projectCards.find((card) => card.id === value)?.title ?? value;
  }
  if (Array.isArray(value)) return value.join("、");
  return String(value);
}

export interface FieldEditorProps {
  schema: CardFieldSchema;
  value: unknown;
  onChange: (value: unknown) => void;
  allCards: CardSummary[];
}

export function FieldEditor({
  schema,
  value,
  onChange,
  allCards
}: FieldEditorProps) {
  const label = (
    <label className="cards-field-label">
      {schema.label}
      {schema.required && <span className="cards-required">*</span>}
    </label>
  );
  switch (schema.kind) {
    case "multiline":
      return (
        <div className="cards-field">
          {label}
          <textarea
            className="cards-input cards-textarea"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      );
    case "number":
      return (
        <div className="cards-field">
          {label}
          <input
            className="cards-input"
            type="number"
            value={typeof value === "number" ? value : value === "" ? "" : String(value ?? "")}
            onChange={(event) =>
              onChange(event.target.value === "" ? undefined : Number(event.target.value))
            }
          />
        </div>
      );
    case "date":
      return (
        <div className="cards-field">
          {label}
          <input
            className="cards-input"
            type="date"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      );
    case "boolean":
      return (
        <div className="cards-field cards-field-inline">
          {label}
          <input
            type="checkbox"
            checked={value === true}
            onChange={(event) => onChange(event.target.checked)}
          />
        </div>
      );
    case "select":
      return (
        <div className="cards-field">
          {label}
          <Select
            className="cards-input"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">（未选择）</option>
            {schema.options?.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </Select>
        </div>
      );
    case "multiSelect":
      return (
        <div className="cards-field">
          {label}
          <div className="cards-checkbox-group">
            {schema.options?.map((option) => {
              const current = Array.isArray(value) ? (value as string[]) : [];
              const checked = current.includes(option);
              return (
                <label key={option} className="cards-checkbox">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={(event) => {
                      const next = new Set(current);
                      if (event.target.checked) next.add(option);
                      else next.delete(option);
                      onChange([...next]);
                    }}
                  />
                  {option}
                </label>
              );
            })}
          </div>
        </div>
      );
    case "cardRef":
      return (
        <div className="cards-field">
          {label}
          <Select
            className="cards-input"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value || undefined)}
          >
            <option value="">（未引用）</option>
            {allCards.map((card) => (
              <option key={card.id} value={card.id}>{card.title}</option>
            ))}
          </Select>
        </div>
      );
    case "url":
      return (
        <div className="cards-field">
          {label}
          <input
            className="cards-input"
            type="text"
            value={typeof value === "string" ? value : ""}
            placeholder="https://"
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      );
    case "attachment":
      return (
        <div className="cards-field">
          {label}
          <p className="cards-attachment-note">
            附件请在卡片「附件」区添加：按内容哈希去重、保存在项目工作区内，不占用该字段，也不会把绝对路径写入字段。
          </p>
        </div>
      );
    default:
      return (
        <div className="cards-field">
          {label}
          <input
            className="cards-input"
            type="text"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      );
  }
}

export interface CardDynamicFieldsProps {
  fields: CardFieldSchema[];
  values: Record<string, unknown>;
  onChange: (key: string, value: unknown) => void;
  allCards: CardSummary[];
}

export function CardDynamicFields({
  fields,
  values,
  onChange,
  allCards
}: CardDynamicFieldsProps) {
  return (
    <>
      {fields.map((field) => (
        <FieldEditor
          key={field.key}
          schema={field}
          value={values[field.key]}
          onChange={(val) => onChange(field.key, val)}
          allCards={allCards}
        />
      ))}
    </>
  );
}
