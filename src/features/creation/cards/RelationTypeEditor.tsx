import { useState } from "react";
import { Pencil, Trash2 } from "lucide-react";
import { Dialog } from "@/components/ui";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { CardRelation, CardType, RelationType } from "@/types/creation";

interface RelationTypeEditorProps {
  projectId: string;
  cardTypes: CardType[];
  relationTypes?: RelationType[];
  relations?: CardRelation[];
  onClose: () => void;
}

export function RelationTypeEditor({ projectId, cardTypes, relationTypes = [], relations = [], onClose }: RelationTypeEditorProps) {
  const actions = useCreationActions();
  const { runStructure, loadRelationTypes, loadCards } = actions;
  const showToast = useUIStore((state) => state.showToast);

  const [forwardName, setForwardName] = useState("");
  const [reverseName, setReverseName] = useState("");
  const [fromKinds, setFromKinds] = useState<string[]>([]);
  const [toKinds, setToKinds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [editingType, setEditingType] = useState<RelationType | null>(null);
  const [confirmingDeleteId, setConfirmingDeleteId] = useState<string | null>(null);

  const toggleKind = (list: string[], setList: (next: string[]) => void, kind: string) => {
    setList(list.includes(kind) ? list.filter((item) => item !== kind) : [...list, kind]);
  };

  const resetDraft = () => {
    setEditingType(null);
    setForwardName("");
    setReverseName("");
    setFromKinds([]);
    setToKinds([]);
    setError(null);
    setConfirmingDeleteId(null);
  };

  const editType = (type: RelationType) => {
    if (type.projectId === null) return;
    setEditingType(type);
    setForwardName(type.forwardName);
    setReverseName(type.reverseName);
    setFromKinds(type.fromKinds);
    setToKinds(type.toKinds);
    setError(null);
    setConfirmingDeleteId(null);
  };

  const impactFor = (type: RelationType) => relations.filter((relation) => relation.relationTypeId === type.id).length;

  const deleteType = async (type: RelationType) => {
    if (type.projectId === null || impactFor(type) > 0) return;
    if (confirmingDeleteId !== type.id) {
      setConfirmingDeleteId(type.id);
      return;
    }
    setSubmitting(true);
    setError(null);
    const ok = await actions.deleteRelationType({
      type: "relationType.delete",
      relationTypeId: type.id,
      baseRevision: type.revision
    });
    setSubmitting(false);
    if (!ok) {
      setError("删除关系类型失败，请刷新后重试。");
      return;
    }
    showToast({ tone: "success", title: "关系类型已删除" });
    resetDraft();
    void loadRelationTypes(projectId);
    void loadCards({ projectId });
  };

  const submit = async () => {
    setError(null);
    const trimmedForward = forwardName.trim();
    const trimmedReverse = reverseName.trim();
    if (!trimmedForward || !trimmedReverse) {
      setError("请填写正向名称和反向名称。");
      return;
    }
    setSubmitting(true);
    const ok = editingType
      ? await actions.updateRelationType({
          type: "relationType.update",
          relationTypeId: editingType.id,
          name: editingType.name,
          forwardName: trimmedForward,
          reverseName: trimmedReverse,
          fromKinds: fromKinds.length > 0 ? fromKinds : undefined,
          toKinds: toKinds.length > 0 ? toKinds : undefined,
          baseRevision: editingType.revision
        })
      : await runStructure({
          type: "relationType.create",
          projectId,
          forwardName: trimmedForward,
          reverseName: trimmedReverse,
          fromKinds: fromKinds.length > 0 ? fromKinds : undefined,
          toKinds: toKinds.length > 0 ? toKinds : undefined
        });
    setSubmitting(false);
    if (!ok) {
      setError(editingType ? "保存关系类型失败，请刷新后重试。" : "创建关系类型失败，请重试。");
      return;
    }
    if (editingType) {
      showToast({ tone: "success", title: "关系类型已更新" });
      void loadRelationTypes(projectId);
      void loadCards({ projectId });
      resetDraft();
    } else {
      showToast({ tone: "success", title: "关系类型已创建" });
      void loadRelationTypes(projectId);
      void loadCards({ projectId });
      onClose();
    }
  };

  return (
    <Dialog
      open
      title="管理关系类型"
      onClose={submitting ? undefined : onClose}
      width="max-w-2xl"
      footer={
        <div className="cards-form-actions">
          <button type="button" className="cards-save" disabled={submitting} onClick={() => void submit()}>{editingType ? "保存修改" : "创建关系类型"}</button>
          <button type="button" className="cards-cancel" onClick={editingType ? resetDraft : onClose}>{editingType ? "取消编辑" : "取消"}</button>
        </div>
      }
    >
      {relationTypes.length > 0 && (
            <section className="cards-type-list" aria-label="现有关系类型">
              {relationTypes.map((type) => {
                const impact = impactFor(type);
                const builtin = type.projectId === null;
                return (
                  <article key={type.id} className="cards-type-list-item">
                    <div>
                      <strong>{type.forwardName}</strong>
                      <code>{type.name}</code>
                      <small>{builtin ? "内置类型，不可编辑或删除" : `引用影响：${impact} 条关系`}</small>
                    </div>
                    <div className="cards-type-list-actions">
                      <button type="button" className="cards-icon-btn" disabled={builtin} onClick={() => editType(type)} aria-label={`编辑${type.forwardName}`}>
                        <Pencil size={13} />
                      </button>
                      <button
                        type="button"
                        className={`cards-icon-btn cards-danger ${confirmingDeleteId === type.id ? "confirming" : ""}`}
                        disabled={builtin || impact > 0 || submitting}
                        title={impact > 0 ? `仍被 ${impact} 条关系引用` : builtin ? "内置类型不可删除" : "删除类型"}
                        aria-label={confirmingDeleteId === type.id ? `确认删除${type.forwardName}` : `删除${type.forwardName}`}
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
          <h4 className="cards-type-editor-title">{editingType ? `编辑：${editingType.forwardName}` : "新建关系类型"}</h4>
          {editingType && (
            <div className="cards-field">
              <label className="cards-field-label">稳定 key</label>
              <input className="cards-input" value={editingType.name} disabled />
            </div>
          )}
          <div className="cards-field">
            <label className="cards-field-label">正向名称<span className="cards-required">*</span></label>
            <input
              className="cards-input"
              value={forwardName}
              onChange={(event) => setForwardName(event.target.value)}
              placeholder="如：师徒"
            />
          </div>
          <div className="cards-field">
            <label className="cards-field-label">反向名称<span className="cards-required">*</span></label>
            <input
              className="cards-input"
              value={reverseName}
              onChange={(event) => setReverseName(event.target.value)}
              placeholder="如：师父"
            />
          </div>

          <div className="cards-field">
            <label className="cards-field-label">起点类型约束（可多选，留空表示不限）</label>
            <div className="cards-checkbox-group">
              {cardTypes.map((type) => (
                <label key={type.id} className="cards-checkbox">
                  <input
                    type="checkbox"
                    checked={fromKinds.includes(type.kind)}
                    onChange={() => toggleKind(fromKinds, setFromKinds, type.kind)}
                  />
                  {type.name}
                </label>
              ))}
              {cardTypes.length === 0 && <span className="cards-relations-empty">当前项目还没有卡片类型。</span>}
            </div>
          </div>

          <div className="cards-field">
            <label className="cards-field-label">终点类型约束（可多选，留空表示不限）</label>
            <div className="cards-checkbox-group">
              {cardTypes.map((type) => (
                <label key={type.id} className="cards-checkbox">
                  <input
                    type="checkbox"
                    checked={toKinds.includes(type.kind)}
                    onChange={() => toggleKind(toKinds, setToKinds, type.kind)}
                  />
                  {type.name}
                </label>
              ))}
              {cardTypes.length === 0 && <span className="cards-relations-empty">当前项目还没有卡片类型。</span>}
            </div>
          </div>
          {error && <p className="cards-form-error" role="alert">{error}</p>}
    </Dialog>
  );
}
