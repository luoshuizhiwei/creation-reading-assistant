import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button, Select } from "@/components/ui";
import type { CardRelation, CardSummary, RelationType } from "@/types/creation";

export interface CardRelationsManagerProps {
  selectedCard: CardSummary;
  projectCards: CardSummary[];
  relationTypes: RelationType[];
  relations?: {
    outgoing: CardRelation[];
    incoming: CardRelation[];
  };
  typeNameMap: Map<string, string>;
  onCreateRelation: (payload: {
    relationTypeId: string;
    targetCardId: string;
    note?: string;
  }) => Promise<boolean>;
  onDeleteRelation: (relationId: string) => Promise<boolean>;
}

export function CardRelationsManager({
  selectedCard,
  projectCards,
  relationTypes,
  relations,
  typeNameMap,
  onCreateRelation,
  onDeleteRelation
}: CardRelationsManagerProps) {
  const [showRelationForm, setShowRelationForm] = useState(false);
  const [relationTypeId, setRelationTypeId] = useState("");
  const [relationTargetId, setRelationTargetId] = useState("");
  const [relationNote, setRelationNote] = useState("");
  const [confirmingRelationId, setConfirmingRelationId] = useState<string | null>(null);

  // BD-199（BD-197 同族）：关系行删除成功后列表随父层刷新换血，握着焦点的
  // 删除钮整行卸载、焦点跌回 body。动作前记行序快照（出边在前、入边在后，
  // 与渲染序一致），换血后交接给原位置下一行（末尾退前一条）；删空时 ul 换成
  // 空态段落，退路是空态段（tabIndex=-1）；条目没走（失败/未刷新）不动焦点。
  const relationIds = (value: CardRelationsManagerProps["relations"]) =>
    value ? [...value.outgoing.map((item) => item.id), ...value.incoming.map((item) => item.id)] : [];
  const rowActionRefs = useRef(new Map<string, HTMLElement>());
  const pendingFocusRef = useRef<{ key: string; order: string[] } | null>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const emptyRef = useRef<HTMLParagraphElement>(null);
  useEffect(() => {
    const pending = pendingFocusRef.current;
    if (!pending) return;
    const current = relationIds(relations);
    if (current.includes(pending.key)) return; // 条目没走（失败/未刷新），不动焦点
    pendingFocusRef.current = null;
    const idx = pending.order.indexOf(pending.key);
    const survivors = current.filter((key) => key !== pending.key);
    const handoffKey = survivors[idx] ?? survivors[idx - 1] ?? null;
    const target = handoffKey ? rowActionRefs.current.get(handoffKey) : null;
    (target?.isConnected ? target : listRef.current ?? emptyRef.current)?.focus({ preventScroll: true });
  }, [relations]);

  useEffect(() => {
    setShowRelationForm(false);
    setRelationTypeId("");
    setRelationTargetId("");
    setRelationNote("");
    setConfirmingRelationId(null);
    pendingFocusRef.current = null; // BD-199：换卡即作废上一张卡的交接预约
  }, [selectedCard.id]);

  const relationTypeForForm = useMemo(
    () => relationTypes.find((type) => type.id === relationTypeId),
    [relationTypes, relationTypeId]
  );

  const fromKindAllowed = useMemo(() => {
    if (!relationTypeForForm) return true;
    const { fromKinds } = relationTypeForForm;
    return fromKinds.length === 0 || fromKinds.includes(selectedCard.kind);
  }, [selectedCard.kind, relationTypeForForm]);

  const allowedTargets = useMemo(() => {
    const toKinds = relationTypeForForm?.toKinds ?? [];
    return projectCards.filter(
      (card) =>
        card.id !== selectedCard.id && (toKinds.length === 0 || toKinds.includes(card.kind))
    );
  }, [projectCards, selectedCard.id, relationTypeForForm]);

  const handleCreate = async () => {
    if (!relationTypeId || !fromKindAllowed) return;
    if (!allowedTargets.some((card) => card.id === relationTargetId)) return;
    const ok = await onCreateRelation({
      relationTypeId,
      targetCardId: relationTargetId,
      note: relationNote || undefined
    });
    if (ok) {
      setShowRelationForm(false);
      setRelationTypeId("");
      setRelationTargetId("");
      setRelationNote("");
    }
  };

  const handleDelete = async (relationId: string) => {
    // BD-199：预约记在点击即、await 前——删除成功后列表换血才有交接依据。
    pendingFocusRef.current = { key: relationId, order: relationIds(relations) };
    const ok = await onDeleteRelation(relationId);
    if (ok) {
      setConfirmingRelationId(null);
    }
  };

  return (
    <section className="cards-relations">
      <header className="cards-relations-head">
        <h4>关系</h4>
        <button type="button" aria-expanded={showRelationForm} onClick={() => setShowRelationForm((value) => !value)}>
          <Plus size={13} /> 建立关系
        </button>
      </header>
      {showRelationForm && (
        <div className="cards-relation-form">
          <Select
            className="cards-input"
            aria-label="关系类型"
            value={relationTypeId}
            onChange={(event) => {
              setRelationTypeId(event.target.value);
              setRelationTargetId("");
            }}
          >
            <option value="">选择关系类型</option>
            {relationTypes.map((type) => (
              <option key={type.id} value={type.id}>
                {type.forwardName}（反向：{type.reverseName}）
              </option>
            ))}
          </Select>
          {relationTypeForForm && (
            <p className="cards-relation-semantic">
              语义：<strong>{selectedCard.title}</strong> <em>{relationTypeForForm.forwardName}</em> → 目标卡片
              <small>（反向：{relationTypeForForm.reverseName}）</small>
            </p>
          )}
          {relationTypeForForm && !fromKindAllowed && (
            <p className="cards-form-error" role="alert">
              当前卡片类型「{typeNameMap.get(selectedCard.kind) ?? selectedCard.kind}」不允许作为该关系的起点。
            </p>
          )}
          <Select
            className="cards-input"
            aria-label="目标卡片"
            value={relationTargetId}
            disabled={!fromKindAllowed}
            onChange={(event) => setRelationTargetId(event.target.value)}
          >
            <option value="">选择目标卡片</option>
            {allowedTargets.map((card) => (
              <option key={card.id} value={card.id}>
                {card.title}（{typeNameMap.get(card.kind) ?? card.kind}）
              </option>
            ))}
          </Select>
          {relationTypeForForm && fromKindAllowed && allowedTargets.length === 0 && (
            <p className="cards-relations-empty">没有符合该关系终点类型约束的卡片。</p>
          )}
          <input
            className="cards-input"
            aria-label="关系说明"
            value={relationNote}
            onChange={(event) => setRelationNote(event.target.value)}
            placeholder="关系说明（可选）"
          />
          <div className="cards-form-actions">
            <Button variant="primary" onClick={() => void handleCreate()}>
              建立
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setShowRelationForm(false);
                setRelationTypeId("");
                setRelationTargetId("");
              }}
            >
              取消
            </Button>
          </div>
        </div>
      )}
      {relations && (relations.outgoing.length > 0 || relations.incoming.length > 0) ? (
        <ul className="cards-relation-list" ref={listRef} tabIndex={-1}>
          {relations.outgoing.map((relation) => (
            <li key={relation.id}>
              <span>{selectedCard.title}</span>
              <em>{relation.forwardName}</em>
              <span>
                {projectCards.find((card) => card.id === relation.toCardId)?.title ?? relation.toCardId}
              </span>
              {relation.note && <small>（{relation.note}）</small>}
              <button
                type="button"
                className={`cards-relation-delete ${confirmingRelationId === relation.id ? "confirming" : ""}`}
                ref={(node) => {
                  // BD-199：行卸载后的焦点交接按 id 找回幸存行的删除钮。
                  if (node) rowActionRefs.current.set(relation.id, node);
                  else rowActionRefs.current.delete(relation.id);
                }}
                onClick={() => {
                  if (confirmingRelationId === relation.id) void handleDelete(relation.id);
                  else setConfirmingRelationId(relation.id);
                }}
                title="删除关系"
              >
                <Trash2 size={12} />
                {confirmingRelationId === relation.id ? "确认删除" : "删除"}
              </button>
            </li>
          ))}
          {relations.incoming.map((relation) => (
            <li key={relation.id}>
              <span>
                {projectCards.find((card) => card.id === relation.fromCardId)?.title ?? relation.fromCardId}
              </span>
              <em>{relation.forwardName}</em>
              <span>{selectedCard.title}</span>
              {relation.note && <small>（{relation.note}）</small>}
              <button
                type="button"
                className={`cards-relation-delete ${confirmingRelationId === relation.id ? "confirming" : ""}`}
                ref={(node) => {
                  // BD-199：行卸载后的焦点交接按 id 找回幸存行的删除钮。
                  if (node) rowActionRefs.current.set(relation.id, node);
                  else rowActionRefs.current.delete(relation.id);
                }}
                onClick={() => {
                  if (confirmingRelationId === relation.id) void handleDelete(relation.id);
                  else setConfirmingRelationId(relation.id);
                }}
                title="删除关系"
              >
                <Trash2 size={12} />
                {confirmingRelationId === relation.id ? "确认删除" : "删除"}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="cards-relations-empty" ref={emptyRef} tabIndex={-1}>暂无关系。</p>
      )}
    </section>
  );
}
