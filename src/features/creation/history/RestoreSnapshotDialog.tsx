import { useEffect, useState } from "react";
import { Button, Dialog } from "@/components/ui";
import type { SnapshotInfo, SnapshotPreviewView, SnapshotSubjectType } from "@/types/creation";
import { type RestoreSnapshotConfirmResult } from "./history-models";

interface RestoreSnapshotDialogProps {
  snapshot: SnapshotInfo;
  subjectTitle: string;
  preview: SnapshotPreviewView | null;
  previewBusy: boolean;
  previewError: string | null;
  onCancel: () => void;
  onConfirm: () => Promise<RestoreSnapshotConfirmResult>;
}

const ENTITY_LABEL: Record<SnapshotSubjectType, string> = {
  volume: "卷",
  chapter: "章",
  scene: "场景",
  card: "卡片"
};

function displayValue(value: unknown): string {
  if (value == null || value === "") return "（空）";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

export function RestoreSnapshotDialog({
  snapshot,
  subjectTitle,
  preview,
  previewBusy,
  previewError,
  onCancel,
  onConfirm
}: RestoreSnapshotDialogProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [protectedId, setProtectedId] = useState<string | null>(null);

  // 切换快照或重新打开时重置临时状态，保留已加载的差异与对话框。
  useEffect(() => {
    setBusy(false);
    setError(null);
    setProtectedId(null);
  }, [snapshot.id]);


  const handleConfirm = async () => {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = await onConfirm();
      if (result.ok) {
        // 成功后保留对话框，展示保护快照 ID，并保留差异供用户核对。
        setProtectedId(result.protectionSnapshotId ?? null);
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
    <Dialog
      open={true}
      title="从快照恢复确认"
      onClose={busy ? undefined : onCancel}
      width="max-w-2xl"
      className="history-modal"
      footer={
        protectedId ? (
          <Button variant="primary" onClick={onCancel}>
            关闭
          </Button>
        ) : (
          <>
            <Button variant="outline" onClick={onCancel} disabled={busy}>
              取消
            </Button>
            <Button
              variant="primary"
              onClick={() => void handleConfirm()}
              disabled={busy || previewBusy || !preview || !preview.canRestore}
            >
              {busy ? (
                <>
                  <span className="history-busy" /> &nbsp;执行中…
                </>
              ) : (
                "先保护再恢复"
              )}
            </Button>
          </>
        )
      }
    >
          {protectedId && (
            <div className="history-restore-success" role="status" aria-live="polite">
              <p className="history-restore-success-title">已恢复到目标版本</p>
              <p>恢复前的当前内容已自动保存为「恢复前保护」快照，可随时回退。</p>
              <p>
                保护快照 ID：<code className="history-protection-id">{protectedId}</code>
              </p>
            </div>
          )}
          <dl className="history-impact-list">
            <div className="history-impact-row">
              <dt>目标对象</dt>
              <dd>
                {ENTITY_LABEL[snapshot.subjectType]} · {preview?.title || subjectTitle}
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

          {previewBusy && <p className="history-impact-loading">正在读取权威恢复预览…</p>}
          {previewError && <p className="history-impact-error">{previewError}</p>}
          {preview && (
            <div className="history-authoritative-preview" aria-label="恢复影响预览">
              <h4>恢复后变化</h4>
              {preview.rows.length === 0 ? (
                <p className="history-impact-empty">目标版本与当前对象没有可见差异。</p>
              ) : (
                <div className="history-diff-table" role="table" aria-label="对象级差异">
                  {preview.rows.map((row, index) => (
                    <div
                      className={`history-diff-row${row.changed ? " changed" : ""}`}
                      role="row"
                      key={`${row.label}-${index}`}
                    >
                      <strong>{row.label}</strong>
                      <span><small>当前</small>{displayValue(row.before)}</span>
                      <span><small>恢复后</small>{displayValue(row.after)}</span>
                    </div>
                  ))}
                </div>
              )}
              {preview.warnings.length > 0 && (
                <ul className="history-impact-warnings" aria-label="关联影响">
                  {preview.warnings.map((warning, index) => <li key={`${warning}-${index}`}>{warning}</li>)}
                </ul>
              )}
            </div>
          )}

          <div className="history-impact-notice">
            恢复前会自动为该对象的<strong>当前状态</strong>创建「恢复前保护」快照。
            若保护快照创建失败，恢复将中止，不会修改任何内容。
          </div>
          {error && <p className="history-impact-error">{error}</p>}
    </Dialog>
  );
}
