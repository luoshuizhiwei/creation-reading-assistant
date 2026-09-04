import { useEffect, useState } from "react";
import { Edit3 } from "lucide-react";
import { Button, Dialog, TextInput } from "@/components/ui";
import type { SessionEntry } from "@/types/creation";

/** 会话修正载荷：局部更新，缺省字段保持不变。 */
export interface SessionUpdatePatch {
  startedAt?: string;
  activeSeconds?: number;
  netChars?: number;
}

export interface SessionEditDialogProps {
  open: boolean;
  session: SessionEntry;
  onClose: () => void;
  /** 提交会话修正；返回 false 表示失败（冲突等），错误由调用方展示。 */
  onSave: (patch: SessionUpdatePatch) => Promise<boolean>;
  busy?: boolean;
  error?: string | null;
}

function toLocalInput(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toDraft(session: SessionEntry): { startedAt: string; activeSeconds: string; netChars: string } {
  return {
    startedAt: toLocalInput(session.startedAt),
    activeSeconds: String(session.activeSeconds),
    netChars: String(session.netChars)
  };
}

function parseNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) return undefined;
  return parsed;
}

export function SessionEditDialog({ open, session, onClose, onSave, busy, error }: SessionEditDialogProps) {
  const [draft, setDraft] = useState(() => toDraft(session));
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) setDraft(toDraft(session));
  }, [open, session]);

  if (!open) return null;

  const submitting = busy ?? saving;

  const handleSave = async () => {
    const activeSeconds = parseNumber(draft.activeSeconds);
    if (draft.activeSeconds.trim() && activeSeconds === undefined) {
      setValidationMessage("活动时长必须是数字。");
      return;
    }
    const netChars = parseNumber(draft.netChars);
    if (draft.netChars.trim() && netChars === undefined) {
      setValidationMessage("净增字数必须是数字。");
      return;
    }
    if (draft.startedAt.trim() && Number.isNaN(Date.parse(draft.startedAt))) {
      setValidationMessage("开始时间格式无效。");
      return;
    }
    setValidationMessage(null);
    setSaving(true);
    try {
      const patch: SessionUpdatePatch = {};
      if (draft.startedAt.trim()) patch.startedAt = new Date(draft.startedAt).toISOString();
      if (activeSeconds !== undefined) patch.activeSeconds = Math.round(activeSeconds);
      if (netChars !== undefined) patch.netChars = Math.round(netChars);
      const ok = await onSave(patch);
      if (ok) onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open={open}
      title={<span className="flex items-center gap-1.5"><Edit3 size={15} /> 修正写作会话</span>}
      ariaLabel="修正写作会话"
      onClose={submitting ? undefined : onClose}
      width="max-w-lg"
      className="session-edit-dialog"
      footer={
        <>
          <Button variant="quiet" onClick={onClose} disabled={submitting}>取消</Button>
          <Button onClick={() => void handleSave()} disabled={submitting}>
            {submitting ? "保存中…" : "保存修正"}
          </Button>
        </>
      }
    >
          <div className="creation-goals-grid">
            <label className="creation-field">
              <span>开始时间</span>
              <TextInput type="datetime-local" value={draft.startedAt} onChange={(event) => setDraft((current) => ({ ...current, startedAt: event.target.value }))} />
            </label>
            <label className="creation-field">
              <span>活动时长（秒）</span>
              <TextInput type="number" min={0} max={86400} step={1} inputMode="numeric" value={draft.activeSeconds} onChange={(event) => setDraft((current) => ({ ...current, activeSeconds: event.target.value }))} />
            </label>
            <label className="creation-field">
              <span>净增字数</span>
              <TextInput type="number" step={1} inputMode="numeric" value={draft.netChars} onChange={(event) => setDraft((current) => ({ ...current, netChars: event.target.value }))} />
            </label>
          </div>
          {validationMessage && <span className="creation-field-error">{validationMessage}</span>}
          {error && <span className="creation-field-error">{error}</span>}
    </Dialog>
  );
}
