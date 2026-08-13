import { useState } from "react";
import type { TrashItem } from "@/types/creation";

const ENTITY_LABEL: Record<string, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片"
};

interface PurgeTrashDialogProps {
  item: TrashItem;
  childCount?: number;
  onCancel: () => void;
  onConfirm: () => Promise<{ ok: boolean; error?: string | null }>;
}

export function PurgeTrashDialog({
  item,
  childCount,
  onCancel,
  onConfirm
}: PurgeTrashDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const entityLabel = ENTITY_LABEL[item.entity] ?? item.entity;

  const handleConfirm = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await onConfirm();
      if (result.ok) {
        onCancel();
      } else {
        setError(result.error || "删除失败，请稍后重试。");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "删除失败，请稍后重试。");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="history-modal-overlay"
      onClick={busy ? undefined : onCancel}
    >
      <div
        className="history-modal"
        role="dialog"
        aria-modal="true"
        aria-label="永久删除确认"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="history-modal-head">
          <h3>永久删除确认</h3>
          <button
            type="button"
            className="history-modal-close"
            onClick={onCancel}
            disabled={busy}
            aria-label="关闭"
          >
            ×
          </button>
        </div>
        <div className="history-modal-body">
          <dl className="history-impact-list">
            <div className="history-impact-row">
              <dt>实体类型</dt>
              <dd>{entityLabel}</dd>
            </div>
            <div className="history-impact-row">
              <dt>实体名称</dt>
              <dd>{item.title}</dd>
            </div>
            {typeof childCount === "number" && childCount > 0 && (
              <div className="history-impact-row">
                <dt>下级条目</dt>
                <dd>共 {childCount} 项（已从回收站移除后将一并永久删除）</dd>
              </div>
            )}
            <div className="history-impact-row">
              <dt>删除时间</dt>
              <dd>{new Date(item.deletedAt).toLocaleString("zh-CN")}</dd>
            </div>
          </dl>

          <div className="history-impact-danger">
            <strong>警告：</strong>此操作<strong>永久不可恢复</strong>。
            该{entityLabel}及其所有内容（正文、字段、关系等）将从数据库中彻底移除，
            无法通过回收站或其他方式找回。
          </div>

          {error && <p className="history-impact-error">{error}</p>}
        </div>
        <div className="history-modal-foot">
          <button
            type="button"
            className="history-btn-cancel"
            onClick={onCancel}
            disabled={busy}
          >
            取消
          </button>
          <button
            type="button"
            className="history-btn-danger"
            onClick={() => void handleConfirm()}
            disabled={busy}
          >
            {busy ? (
              <>
                <span className="history-busy" /> &nbsp;删除中…
              </>
            ) : (
              "确认永久删除"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
