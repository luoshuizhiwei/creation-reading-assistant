import React, { useEffect, useState } from "react";
import { Button, Dialog } from "@/components/ui";
import "./encryption.css";

export type PassphraseDialogMode = "create" | "confirm";

export interface PassphraseDialogProps {
  open: boolean;
  title?: string;
  confirmLabel?: string;
  busy?: boolean;
  error?: string;
  /**
   * create：设置新口令（二次确认 + 勾选知晓）；
   * confirm：仅输入口令解锁（如导入解密），无确认框与勾选。
   */
  mode?: PassphraseDialogMode;
  /** create 模式下口令最短长度；0 或不传表示不设下限。confirm 模式不强制。 */
  minLength?: number;
  /** 覆盖默认提示文案（confirm 模式用来说明口令来源）。 */
  description?: string;
  onConfirm: (passphrase: string) => void;
  onCancel?: () => void;
}

/**
 * 口令设置/确认对话框。
 * create 模式：明确提示“忘记口令无法恢复”，二次输入一致 + 勾选已知晓（并满足最短长度）
 * 才允许提交。confirm 模式：单次输入即可提交，由调用方负责危险确认。
 * 组件不保存口令，仅通过 onConfirm 把口令交给调用方。
 */
export function PassphraseDialog({
  open,
  title = "设置加密口令",
  confirmLabel = "加密",
  busy = false,
  error,
  mode = "create",
  minLength = 0,
  description,
  onConfirm,
  onCancel
}: PassphraseDialogProps): React.ReactElement | null {
  const [passphrase, setPassphrase] = useState("");
  const [confirm, setConfirm] = useState("");
  const [ack, setAck] = useState(false);

  // 关闭或由外部切换用途（不同标题/模式）时重置所有输入，避免口令残留或旧勾选直接可提交。
  useEffect(() => {
    if (!open) return;
    setPassphrase("");
    setConfirm("");
    setAck(false);
  }, [open, mode, title]);

  if (!open) return null;

  const isCreate = mode === "create";
  const tooShort = isCreate && minLength > 0 && passphrase.length > 0 && passphrase.length < minLength;
  const mismatch = isCreate && confirm.length > 0 && confirm !== passphrase;
  const canSubmit = isCreate
    ? passphrase.length > 0 && !tooShort && confirm === passphrase && ack && !busy
    : passphrase.length > 0 && !busy;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    onConfirm(passphrase);
  };

  return (
    <Dialog
      open={open}
      title={title}
      dataTestId="passphrase-dialog"
      onClose={busy ? undefined : onCancel}
      width="max-w-md"
      footer={
        <>
          {onCancel && (
            <Button
              type="button"
              variant="secondary"
              data-testid="pe-cancel"
              disabled={busy}
              onClick={onCancel}
            >
              取消
            </Button>
          )}
          <Button
            type="submit"
            form="passphrase-form"
            data-testid="pe-submit"
            disabled={!canSubmit}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <form id="passphrase-form" onSubmit={handleSubmit}>
        {description ? (
          <p className="pe-description" data-testid="pe-description">
            {description}
          </p>
        ) : (
          <p className="pe-warning" data-testid="pe-warning">
            忘记口令无法恢复。请务必牢记口令；系统不会保存口令，也无法帮你找回。
          </p>
        )}

        <label className="pe-field mt-3 block">
          <span>{isCreate ? "口令" : "解锁口令"}</span>
          <input
            type="password"
            className="pe-input"
            data-testid="pe-passphrase"
            autoComplete={isCreate ? "new-password" : "off"}
            value={passphrase}
            disabled={busy}
            onChange={(e) => setPassphrase(e.target.value)}
          />
        </label>

        {tooShort && (
          <p className="pe-error mt-2" data-testid="pe-too-short">
            口令至少需要 {minLength} 个字符
          </p>
        )}

        {isCreate && (
          <label className="pe-field mt-3 block">
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
        )}

        {mismatch && (
          <p className="pe-error mt-2" data-testid="pe-mismatch">
            两次输入的口令不一致
          </p>
        )}

        {isCreate && (
          <label className="pe-ack mt-4 flex items-center gap-2">
            <input
              type="checkbox"
              data-testid="pe-ack"
              checked={ack}
              disabled={busy}
              onChange={(e) => setAck(e.target.checked)}
            />
            <span>我已知晓：忘记口令后数据将无法恢复。</span>
          </label>
        )}

        {error && (
          <p className="pe-error mt-2" data-testid="pe-error">
            {error}
          </p>
        )}
      </form>
    </Dialog>
  );
}
