import { useEffect, useMemo, useState } from "react";
import { Columns, Layers, LayoutGrid, Pencil, Plus, Search, Trash2, X } from "lucide-react";
import { BoardView } from "@/features/creation/cards/BoardView";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import type {
  CardFieldSchema,
  CardSummary,
  CreationProjectSummary
} from "@/types/creation";

interface CardsPageProps {
  project: CreationProjectSummary;
}

function parseList(value: string): string[] {
  return value
    .split(/[,，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function FieldEditor({
  schema,
  value,
  onChange,
  allCards
}: {
  schema: CardFieldSchema;
  value: unknown;
  onChange: (value: unknown) => void;
  allCards: CardSummary[];
}) {
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
          <select
            className="cards-input"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">（未选择）</option>
            {schema.options?.map((option) => (
              <option key={option} value={option}>{option}</option>
            ))}
          </select>
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
          <select
            className="cards-input"
            value={typeof value === "string" ? value : ""}
            onChange={(event) => onChange(event.target.value || undefined)}
          >
            <option value="">（未引用）</option>
            {allCards.map((card) => (
              <option key={card.id} value={card.id}>{card.title}</option>
            ))}
          </select>
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
          <input
            className="cards-input"
            type="text"
            value={typeof value === "string" ? value : ""}
            placeholder="附件标识（文件上传后续切片接入）"
            disabled
          />
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

export function CardsPage({ project }: CardsPageProps) {
  const cardTypes = useCreationStore((state) => state.cardTypes);
  const relationTypes = useCreationStore((state) => state.relationTypes);
  const cards = useCreationStore((state) => state.cards);
  const cardRelations = useCreationStore((state) => state.cardRelations);
  const selectedCardId = useCreationStore((state) => state.selectedCardId);
  const cardsLoading = useCreationStore((state) => state.cardsLoading);
  const selectCard = useCreationStore((state) => state.selectCard);
  const {
    loadCardTypes,
    loadRelationTypes,
    loadCards,
    loadCardRelations,
    runStructure,
    subscribeProject
  } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);

  const [view, setView] = useState<"board" | "list">("board");
  const [filterKind, setFilterKind] = useState("");
  const [search, setSearch] = useState("");
  const [draft, setDraft] = useState<CardSummary | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [showRelationForm, setShowRelationForm] = useState(false);
  const [relationTypeId, setRelationTypeId] = useState("");
  const [relationTargetId, setRelationTargetId] = useState("");
  const [relationNote, setRelationNote] = useState("");

  useEffect(() => {
    void loadCardTypes(project.id);
    void loadRelationTypes(project.id);
  }, [loadCardTypes, loadRelationTypes, project.id]);

  // 订阅项目已提交事件：卡片/关系变更（含看板拖拽改类型）实时刷新列表，跨视图保持一致。
  useEffect(() => subscribeProject(project.id), [project.id, subscribeProject]);

  useEffect(() => {
    void loadCards({ projectId: project.id, cardKind: filterKind || undefined, search: search || undefined });
  }, [loadCards, project.id, filterKind, search]);

  const selectedCard = useMemo(
    () => cards.find((card) => card.id === selectedCardId),
    [cards, selectedCardId]
  );
  const selectedType = useMemo(
    () => cardTypes.find((type) => type.kind === selectedCard?.kind),
    [cardTypes, selectedCard]
  );
  const typeNameMap = useMemo(
    () => new Map(cardTypes.map((type) => [type.kind, type.name])),
    [cardTypes]
  );
  const relations = selectedCardId ? cardRelations[selectedCardId] : undefined;

  useEffect(() => {
    if (selectedCardId) void loadCardRelations(selectedCardId);
  }, [loadCardRelations, selectedCardId]);

  const typeById = useMemo(() => new Map(cardTypes.map((type) => [type.kind, type])), [cardTypes]);

  const refreshCards = () =>
    void loadCards({ projectId: project.id, cardKind: filterKind || undefined, search: search || undefined });

  const newCardInKind = (kind: string) => {
    setDraft({
      id: "new",
      projectId: project.id,
      kind: kind || cardTypes[0]?.kind || "character",
      title: "",
      aliases: [],
      fields: {},
      tags: [],
      createdAt: "",
      updatedAt: "",
      revision: 0
    });
  };

  const saveDraft = async () => {
    if (!draft) return;
    const fields = { ...draft.fields };
    for (const field of typeById.get(draft.kind)?.fields ?? []) {
      if (fields[field.key] === undefined && field.defaultValue !== undefined) {
        fields[field.key] = field.defaultValue;
      }
    }
    if (draft.id === "new") {
      const ok = await runStructure({
        type: "card.create",
        projectId: project.id,
        kind: draft.kind,
        title: draft.title.trim(),
        aliases: draft.aliases,
        fields,
        tags: draft.tags
      });
      if (ok) {
        showToast({ tone: "success", title: "卡片已创建" });
        setDraft(null);
        refreshCards();
      }
    } else {
      const ok = await runStructure({
        type: "card.update",
        cardId: draft.id,
        title: draft.title.trim(),
        aliases: draft.aliases,
        fields,
        tags: draft.tags,
        baseRevision: draft.revision
      });
      if (ok) {
        showToast({ tone: "success", title: "卡片已保存" });
        setDraft(null);
        refreshCards();
      }
    }
  };

  const moveCardKind = async (cardId: string, kind: string) => {
    const card = cards.find((item) => item.id === cardId);
    if (!card || card.kind === kind) return;
    const ok = await runStructure({
      type: "card.update",
      cardId,
      kind,
      baseRevision: card.revision
    });
    if (ok) {
      showToast({ tone: "success", title: `已移动到「${typeNameMap.get(kind) ?? kind}」` });
      refreshCards();
    }
  };

  const removeCard = async () => {
    if (!selectedCard || !confirmDelete) return;
    const ok = await runStructure({ type: "card.delete", cardId: selectedCard.id });
    if (ok) {
      showToast({ tone: "success", title: "卡片已删除" });
      selectCard(undefined);
      setConfirmDelete(false);
      refreshCards();
    }
  };

  const createRelation = async () => {
    if (!selectedCard || !relationTypeId || !relationTargetId) return;
    const ok = await runStructure({
      type: "cardRelation.create",
      projectId: project.id,
      fromCardId: selectedCard.id,
      toCardId: relationTargetId,
      relationTypeId,
      note: relationNote || undefined
    });
    if (ok) {
      showToast({ tone: "success", title: "关系已建立" });
      setShowRelationForm(false);
      setRelationTypeId("");
      setRelationTargetId("");
      setRelationNote("");
      void loadCardRelations(selectedCard.id);
    }
  };

  const displayFieldValue = (field: CardFieldSchema, value: unknown): string => {
    if (value === undefined || value === null || value === "") return "—";
    if (field.kind === "cardRef" && typeof value === "string") {
      return cards.find((card) => card.id === value)?.title ?? value;
    }
    if (Array.isArray(value)) return value.join("、");
    return String(value);
  };

  const draftFields = draft ? typeById.get(draft.kind)?.fields ?? [] : [];

  const detailPane = draft ? (
    <div className="cards-detail-card">
      <header className="cards-detail-head">
        <h3>{draft.id === "new" ? "新建卡片" : "编辑卡片"}</h3>
        <button type="button" className="cards-close" onClick={() => setDraft(null)} title="关闭"><X size={15} /></button>
      </header>
      <div className="cards-form">
        <div className="cards-field">
          <label className="cards-field-label">类型</label>
          <select
            className="cards-input"
            value={draft.kind}
            disabled={draft.id !== "new"}
            onChange={(event) => setDraft({ ...draft, kind: event.target.value, fields: {} })}
          >
            {cardTypes.map((type) => (
              <option key={type.id} value={type.kind}>{type.name}</option>
            ))}
          </select>
        </div>
        <div className="cards-field">
          <label className="cards-field-label">名称<span className="cards-required">*</span></label>
          <input
            className="cards-input"
            value={draft.title}
            onChange={(event) => setDraft({ ...draft, title: event.target.value })}
            placeholder="卡片主名称"
          />
        </div>
        <div className="cards-field">
          <label className="cards-field-label">别名</label>
          <input
            className="cards-input"
            value={draft.aliases.join("，")}
            onChange={(event) => setDraft({ ...draft, aliases: parseList(event.target.value) })}
            placeholder="用逗号分隔多个别名"
          />
        </div>
        {draftFields.map((field) => (
          <FieldEditor
            key={field.key}
            schema={field}
            value={draft.fields[field.key]}
            onChange={(value) => setDraft({ ...draft, fields: { ...draft.fields, [field.key]: value } })}
            allCards={cards}
          />
        ))}
        <div className="cards-field">
          <label className="cards-field-label">标签</label>
          <input
            className="cards-input"
            value={draft.tags.join("，")}
            onChange={(event) => setDraft({ ...draft, tags: parseList(event.target.value) })}
            placeholder="用逗号分隔，如：主角，战斗"
          />
        </div>
        <div className="cards-form-actions">
          <button type="button" className="cards-save" onClick={() => void saveDraft()}>
            {draft.id === "new" ? "创建卡片" : "保存修改"}
          </button>
          <button type="button" className="cards-cancel" onClick={() => setDraft(null)}>取消</button>
        </div>
      </div>
    </div>
  ) : selectedCard ? (
    <div className="cards-detail-card">
      <header className="cards-detail-head">
        <div>
          <span className="cards-detail-kind">{typeNameMap.get(selectedCard.kind) ?? selectedCard.kind}</span>
          <h3>{selectedCard.title}</h3>
        </div>
        <div className="cards-detail-actions">
          <button type="button" onClick={() => setDraft(selectedCard)} title="编辑"><Pencil size={15} /></button>
          <button
            type="button"
            className={confirmDelete ? "confirming" : ""}
            onClick={() => {
              if (confirmDelete) void removeCard();
              else setConfirmDelete(true);
            }}
            title="删除卡片"
          >
            <Trash2 size={15} />
          </button>
        </div>
      </header>

      {selectedCard.aliases.length > 0 && (
        <p className="cards-aliases">别名：{selectedCard.aliases.join("、")}</p>
      )}
      {selectedCard.tags.length > 0 && (
        <p className="cards-tags">{selectedCard.tags.map((tag) => `#${tag}`).join(" ")}</p>
      )}

      {selectedType && selectedType.fields.length > 0 && (
        <dl className="cards-fields-list">
          {selectedType.fields.map((field) => (
            <div key={field.key} className="cards-field-row">
              <dt>{field.label}</dt>
              <dd>{displayFieldValue(field, selectedCard.fields[field.key])}</dd>
            </div>
          ))}
        </dl>
      )}

      <section className="cards-relations">
        <header className="cards-relations-head">
          <h4>关系</h4>
          <button type="button" onClick={() => setShowRelationForm((value) => !value)}>
            <Plus size={13} /> 建立关系
          </button>
        </header>
        {showRelationForm && (
          <div className="cards-relation-form">
            <select
              className="cards-input"
              value={relationTypeId}
              onChange={(event) => setRelationTypeId(event.target.value)}
            >
              <option value="">选择关系类型</option>
              {relationTypes.map((type) => (
                <option key={type.id} value={type.id}>{type.forwardName}</option>
              ))}
            </select>
            <select
              className="cards-input"
              value={relationTargetId}
              onChange={(event) => setRelationTargetId(event.target.value)}
            >
              <option value="">选择目标卡片</option>
              {cards.filter((card) => card.id !== selectedCard.id).map((card) => (
                <option key={card.id} value={card.id}>{card.title}</option>
              ))}
            </select>
            <input
              className="cards-input"
              value={relationNote}
              onChange={(event) => setRelationNote(event.target.value)}
              placeholder="关系说明（可选）"
            />
            <div className="cards-form-actions">
              <button type="button" className="cards-save" onClick={() => void createRelation()}>建立</button>
              <button type="button" className="cards-cancel" onClick={() => setShowRelationForm(false)}>取消</button>
            </div>
          </div>
        )}
        {relations && (relations.outgoing.length > 0 || relations.incoming.length > 0) ? (
          <ul className="cards-relation-list">
            {relations.outgoing.map((relation) => (
              <li key={relation.id}>
                <span>{selectedCard.title}</span>
                <em>{relation.forwardName}</em>
                <span>{cards.find((card) => card.id === relation.toCardId)?.title ?? relation.toCardId}</span>
                {relation.note && <small>（{relation.note}）</small>}
              </li>
            ))}
            {relations.incoming.map((relation) => (
              <li key={relation.id}>
                <span>{cards.find((card) => card.id === relation.fromCardId)?.title ?? relation.fromCardId}</span>
                <em>{relation.forwardName}</em>
                <span>{selectedCard.title}</span>
                {relation.note && <small>（{relation.note}）</small>}
              </li>
            ))}
          </ul>
        ) : (
          <p className="cards-relations-empty">暂无关系。</p>
        )}
      </section>
    </div>
  ) : (
    <div className="cards-detail-empty">
      <Layers size={28} />
      <p>在左侧选择一张卡片查看详情，或新建一张。</p>
    </div>
  );

  return (
    <section className="cards-page" aria-label="卡片管理">
      <header className="cards-toolbar">
        <div className="cards-toolbar-left">
          <span className="desktop-card-label">Cards</span>
          <div className="cards-view-switch" role="group" aria-label="卡片视图">
            <button type="button" className={view === "board" ? "active" : ""} onClick={() => setView("board")}>
              <LayoutGrid size={13} /> 看板
            </button>
            <button type="button" className={view === "list" ? "active" : ""} onClick={() => setView("list")}>
              <Columns size={13} /> 列表
            </button>
          </div>
          {view === "list" && (
            <select
              className="cards-input cards-kind-filter"
              value={filterKind}
              onChange={(event) => setFilterKind(event.target.value)}
              aria-label="按类型筛选"
            >
              <option value="">全部类型</option>
              {cardTypes.map((type) => (
                <option key={type.id} value={type.kind}>{type.name}</option>
              ))}
            </select>
          )}
        </div>
        <div className="cards-toolbar-right">
          <span className="cards-search">
            <Search size={14} />
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索名称或别名"
            />
          </span>
          <button type="button" className="cards-add" onClick={() => newCardInKind(filterKind)}>
            <Plus size={15} /> 新建卡片
          </button>
        </div>
      </header>

      {cardsLoading && view === "board" ? (
        <p className="cards-empty" role="status">正在读取卡片…</p>
      ) : view === "board" ? (
        <div className="cards-board-layout">
          <div className="cards-board-pane">
            <BoardView
              cardTypes={cardTypes}
              cards={cards}
              selectedCardId={selectedCardId}
              onSelectCard={(cardId) => selectCard(cardId)}
              onMoveKind={(cardId, kind) => void moveCardKind(cardId, kind)}
              onNewCard={(kind) => newCardInKind(kind)}
            />
          </div>
          <aside className="cards-detail-pane">{detailPane}</aside>
        </div>
      ) : (
        <div className="cards-layout">
          <aside className="cards-list-pane">
            {cardsLoading ? (
              <p className="cards-empty" role="status">正在读取卡片…</p>
            ) : cards.length === 0 ? (
              <p className="cards-empty">还没有卡片。点右上角「新建卡片」创建第一张。</p>
            ) : (
              cards.map((card) => (
                <button
                  type="button"
                  key={card.id}
                  className={`cards-list-item ${card.id === selectedCardId ? "active" : ""}`}
                  onClick={() => selectCard(card.id)}
                >
                  <span className="cards-list-kind">{typeNameMap.get(card.kind) ?? card.kind}</span>
                  <strong>{card.title}</strong>
                  {card.tags.length > 0 && (
                    <span className="cards-list-tags">{card.tags.map((tag) => `#${tag}`).join(" ")}</span>
                  )}
                </button>
              ))
            )}
          </aside>
          <main className="cards-detail-pane">{detailPane}</main>
        </div>
      )}
    </section>
  );
}
