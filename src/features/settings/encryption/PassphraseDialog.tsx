import React, { useState } from "react";
import "./encryption.css";

export interface PassphraseDialogProps {
  open: boolean;
  title?: string;
  confirmLabel?: string;
  busy?: boolean;
  error?: string;
  onConfirm: (passphrase: string) => void;
  onCancel?: () => void;
}

/**
 * 口令设置/确认对话框（最小 UI）。
 * 要求 9: 明确提示“忘记口令无法恢复”，并通过“二次确认”——重复输入口令一致 +
 * 勾选已知晓——才允许提交。组件不保存口令，仅通过 onConfirm 把口令交给调用方。
 */
export function PassphraseDialog({
  open,
  title = "设置加密口令",
  confirmLabel = "加密",
  busy = false,
  error,
  onConfirm,
  onCancel
}: PassphraseDialogProps): React.ReactElement | null {
  const [passphrase, setPassphrase] = useState("");
  const [confirm, setConfirm] = useState("");
  const [ack, setAck] = useState(false);

  if (!open) return null;

  const mismatch = confirm.length > 0 && confirm !== passphrase;
  const canSubmit = passphrase.length > 0 && confirm === passphrase && ack && !busy;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    onConfirm(passphrase);
  };

  return (
    <div className="pe-modal-backdrop" role="dialog" aria-modal="true" data-testid="passphrase-dialog">
      <form className="pe-modal" onSubmit={handleSubmit}>
        <h2 className="pe-title">{title}</h2>
        <p className="pe-warning" data-testid="pe-warning">
          忘记口令无法恢复。请务必牢记口令；系统不会保存口令，也无法帮你找回。
        </p>

        <label className="pe-field">
          <span>口令</span>
          <input
            type="password"
            className="pe-input"
            data-testid="pe-passphrase"
            autoComplete="new-password"
            value={passphrase}
            disabled={busy}
            onChange={(e) => setPassphrase(e.target.value)}
          />
        </label>

        <label className="pe-field">
          <span>确认口令</span>
          <input
            type="password"
            className="pe-input"
            data-testid="pe-confirm"
            autoComplete="new-password"
            value={confirm}
            disabled={busy}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </label>

        {mismatch && (
          <p className="pe-error" data-testid="pe-mismatch">
            两次输入的口令不一致
          </p>
        )}

        <label className="pe-ack">
          <input
            type="checkbox"
            data-testid="pe-ack"
            checked={ack}
            disabled={busy}
            onChange={(e) => setAck(e.target.checked)}
          />
          <span>我已知晓：忘记口令后数据将无法恢复。</span>
        </label>

        {error && (
          <p className="pe-error" data-testid="pe-error">
            {error}
          </p>
        )}

        <div className="pe-actions">
          {onCancel && (
            <button
              type="button"
              className="pe-btn pe-btn-secondary"
              data-testid="pe-cancel"
              disabled={busy}
              onClick={onCancel}
            >
              取消
            </button>
          )}
          <button type="submit" className="pe-btn pe-btn-primary" data-testid="pe-submit" disabled={!canSubmit}>
            {confirmLabel}
          </button>
        </div>
      </form>
    </div>
  );
}
