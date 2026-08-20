import { useState } from "react";
import type { TrashImpactView, TrashItem } from "@/types/creation";

const ENTITY_LABEL: Record<string, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片"
};

interface PurgeTrashDialogProps {
  item: TrashItem;
  impact: TrashImpactView | null;
  impactBusy: boolean;
  impactError: string | null;
  onCancel: () => void;
  onConfirm: () => Promise<{ ok: boolean; error?: string | null }>;
}

export function PurgeTrashDialog({
  item,
  impact,
  impactBusy,
  impactError,
  onCancel,
  onConfirm
}: PurgeTrashDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmation, setConfirmation] = useState("");

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
            <div className="history-impact-row">
              <dt>删除时间</dt>
              <dd>{new Date(item.deletedAt).toLocaleString("zh-CN")}</dd>
            </div>
          </dl>

          {impactBusy && <p className="history-impact-loading">正在统计永久删除影响…</p>}
          {impactError && <p className="history-impact-error">{impactError}</p>}
          {impact && (
            <div className="history-authoritative-preview" aria-label="永久删除影响">
              <h4>将被永久移除的内容</h4>
              <ul className="history-impact-summary">
                {impact.childVolumeCount > 0 && <li>{impact.childVolumeCount} 个子卷</li>}
                {impact.childChapterCount > 0 && <li>{impact.childChapterCount} 个章节</li>}
                {impact.childSceneCount > 0 && <li>{impact.childSceneCount} 个场景</li>}
                {impact.approxChars > 0 && <li>约 {impact.approxChars.toLocaleString("zh-CN")} 字</li>}
                {impact.relatedCardCount > 0 && <li>{impact.relatedCardCount} 张关联卡片</li>}
                {impact.resourceCount > 0 && <li>{impact.resourceCount} 个资源</li>}
              </ul>
              {impact.warnings.length > 0 && (
                <ul className="history-impact-warnings">
                  {impact.warnings.map((warning, index) => <li key={`${warning}-${index}`}>{warning}</li>)}
                </ul>
              )}
            </div>
          )}

          <div className="history-impact-danger">
            <strong>警告：</strong>此操作<strong>永久不可恢复</strong>。
            该{entityLabel}及其所有内容（正文、字段、关系等）将从数据库中彻底移除，
            无法通过回收站或其他方式找回。
          </div>

          <label className="history-confirm-name">
            输入对象名称确认
            <input
              aria-label="输入对象名称确认"
              value={confirmation}
              onChange={(event) => setConfirmation(event.target.value)}
              disabled={busy}
              placeholder={item.title}
              autoComplete="off"
            />
            <small>请输入“{item.title}”后才能永久删除。</small>
          </label>

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
            disabled={busy || impactBusy || !impact || confirmation !== item.title}
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
