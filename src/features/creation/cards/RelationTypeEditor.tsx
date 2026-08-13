import { useState } from "react";
import { X } from "lucide-react";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";
import type { CardType } from "@/types/creation";

interface RelationTypeEditorProps {
  projectId: string;
  cardTypes: CardType[];
  onClose: () => void;
}

export function RelationTypeEditor({ projectId, cardTypes, onClose }: RelationTypeEditorProps) {
  const { runStructure, loadRelationTypes } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);

  const [forwardName, setForwardName] = useState("");
  const [reverseName, setReverseName] = useState("");
  const [fromKinds, setFromKinds] = useState<string[]>([]);
  const [toKinds, setToKinds] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const toggleKind = (list: string[], setList: (next: string[]) => void, kind: string) => {
    setList(list.includes(kind) ? list.filter((item) => item !== kind) : [...list, kind]);
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
    const ok = await runStructure({
      type: "relationType.create",
      projectId,
      forwardName: trimmedForward,
      reverseName: trimmedReverse,
      fromKinds: fromKinds.length > 0 ? fromKinds : undefined,
      toKinds: toKinds.length > 0 ? toKinds : undefined
    });
    setSubmitting(false);
    if (ok) {
      showToast({ tone: "success", title: "关系类型已创建" });
      void loadRelationTypes(projectId);
      onClose();
    }
  };

  return (
    <div className="cards-modal-overlay" role="dialog" aria-label="新建关系类型">
      <div className="cards-modal">
        <header className="cards-modal-head">
          <h3>新建关系类型</h3>
          <button type="button" className="cards-close" onClick={onClose} title="关闭"><X size={15} /></button>
        </header>
        <div className="cards-modal-body">
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
        </div>
        <footer className="cards-form-actions cards-modal-foot">
          <button type="button" className="cards-save" disabled={submitting} onClick={() => void submit()}>创建关系类型</button>
          <button type="button" className="cards-cancel" onClick={onClose}>取消</button>
        </footer>
      </div>
    </div>
  );
}
