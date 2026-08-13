import { useState } from "react";
import type { SnapshotInfo, SnapshotSubjectType } from "@/types/creation";

interface RestoreSnapshotDialogProps {
  snapshot: SnapshotInfo;
  subjectTitle: string;
  onCancel: () => void;
  onConfirm: () => Promise<{ ok: boolean; error?: string | null }>;
}

const ENTITY_LABEL: Record<SnapshotSubjectType, string> = {
  scene: "场景",
  card: "卡片"
};

export function RestoreSnapshotDialog({
  snapshot,
  subjectTitle,
  onCancel,
  onConfirm
}: RestoreSnapshotDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleConfirm = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await onConfirm();
      if (result.ok) {
        onCancel();
      } else {
        setError(result.error || "恢复失败，请稍后重试。");
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "恢复失败，请稍后重试。");
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
        aria-label="从快照恢复确认"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="history-modal-head">
          <h3>从快照恢复确认</h3>
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
              <dt>目标对象</dt>
              <dd>
                {ENTITY_LABEL[snapshot.subjectType]} · {subjectTitle}
              </dd>
            </div>
            <div className="history-impact-row">
              <dt>目标版本</dt>
              <dd>{snapshot.reason || "(未命名里程碑)"}</dd>
            </div>
            <div className="history-impact-row">
              <dt>版本时间</dt>
              <dd>{new Date(snapshot.createdAt).toLocaleString("zh-CN")}</dd>
            </div>
          </dl>

          <div className="history-impact-notice">
            恢复前会自动为该对象的<strong>当前状态</strong>创建「恢复前保护」快照。
            若保护快照创建失败，恢复将中止，不会修改任何内容。
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
            className="history-btn-confirm"
            onClick={() => void handleConfirm()}
            disabled={busy}
          >
            {busy ? (
              <>
                <span className="history-busy" /> &nbsp;执行中…
              </>
            ) : (
              "先保护再恢复"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
