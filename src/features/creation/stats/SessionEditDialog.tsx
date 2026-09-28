import { Edit3 } from "lucide-react";
import { Dialog } from "@/components/ui";
import type { SessionEntry } from "@/types/creation";
import { SessionEditForm } from "./SessionEditForm";
import type { SessionCorrectionDraft } from "./session-policy";

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

/** datetime-local 只有分钟精度：按同一口径归一化基线，避免保存时误改秒数。 */
function normalizeToMinutePrecision(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes(), 0, 0).toISOString();
}

/**
 * 会话修正对话框：只是 SessionEditForm 的弹窗外壳。
 *
 * 校验、字段与提交流程全部由 SessionEditForm 承担（原先两处各写了一遍，
 * 弹窗版还漏掉了 session-policy 的日期/时长一致性校验），这里仅负责
 * Dialog 容器与把规范化草稿收敛成局部更新补丁。
 */
export function SessionEditDialog({ open, session, onClose, onSave, busy, error }: SessionEditDialogProps) {
  if (!open) return null;

  const handleSubmit = async (draft: SessionCorrectionDraft): Promise<boolean> => {
    const patch: SessionUpdatePatch = {};
    if (draft.startedAt !== normalizeToMinutePrecision(session.startedAt)) patch.startedAt = draft.startedAt;
    if (draft.activeSeconds !== session.activeSeconds) patch.activeSeconds = draft.activeSeconds;
    if (draft.netChars !== session.netChars) patch.netChars = draft.netChars;
    return onSave(patch);
  };

  return (
    <Dialog
      open={open}
      title={<span className="flex items-center gap-1.5"><Edit3 size={15} /> 修正写作会话</span>}
      ariaLabel="修正写作会话"
      onClose={busy ? undefined : onClose}
      width="max-w-lg"
      className="session-edit-dialog"
    >
      <SessionEditForm
        session={session}
        projectId={session.projectId}
        onCancel={onClose}
        onSave={handleSubmit}
        busy={busy}
        error={error}
      />
    </Dialog>
  );
}
