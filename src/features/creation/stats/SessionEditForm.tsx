import { useEffect, useMemo, useState } from "react";
import { PencilLine } from "lucide-react";
import { Button } from "@/components/ui";
import type { SessionEntry } from "@/types/creation";
import {
  SESSION_CORRECTION_ISSUE_LABELS,
  validateSessionCorrection,
  type SessionCorrectionDraft
} from "./session-policy";

/**
 * 会话修正表单。
 *
 * 编辑写作会话的 startedAt / activeSeconds / netChars；提交前用
 * session-policy 完成日期、时长与净增的一致性校验，问题逐项提示。
 * 保存通过 onSave 回调提交（携带 projectId / sessionId 归属），
 * 主进程侧的项目归属与 revision 冲突校验由集成方接线后生效。
 */

function toLocalDateTimeInput(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (value: number): string => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export interface SessionEditFormProps {
  session: SessionEntry;
  projectId: string;
  onCancel: () => void;
  /** 提交修正；返回 false 表示失败（冲突等），错误由调用方展示。 */
  onSave: (draft: SessionCorrectionDraft) => Promise<boolean>;
  busy?: boolean;
  error?: string | null;
}

export function SessionEditForm({ session, projectId, onCancel, onSave, busy, error }: SessionEditFormProps) {
  const [startedAt, setStartedAt] = useState(() => toLocalDateTimeInput(session.startedAt));
  const [activeSeconds, setActiveSeconds] = useState(() => String(session.activeSeconds));
  const [netChars, setNetChars] = useState(() => String(session.netChars));
  const [submitting, setSubmitting] = useState(false);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    setStartedAt(toLocalDateTimeInput(session.startedAt));
    setActiveSeconds(String(session.activeSeconds));
    setNetChars(String(session.netChars));
    setTouched(false);
  }, [session]);

  const validation = useMemo(() => {
    const iso = startedAt ? new Date(startedAt).toISOString() : "";
    return validateSessionCorrection({
      projectId,
      sessionId: session.id,
      sceneId: session.sceneId ?? undefined,
      startedAt: iso,
      // 清空输入框表示“不修改该项”，回落到会话原值；
      // 若按 Number("")===0 处理，会把时长/字数误改为 0。
      activeSeconds: activeSeconds.trim() ? Number(activeSeconds) : session.activeSeconds,
      netChars: netChars.trim() ? Number(netChars) : session.netChars
    });
  }, [activeSeconds, netChars, projectId, session, startedAt]);

  const issues = validation.ok ? [] : validation.issues;
  // 外部 busy（调用方正在写库）与内部 submitting 都要锁定表单，
  // 否则保存期间仍可编辑/重复提交，revision 会被撞掉。
  const blocked = submitting || busy === true;

  const handleSave = async () => {
    if (blocked) return;
    if (!validation.ok) {
      // 校验失败：展示问题清单，不提交。
      setTouched(true);
      return;
    }
    setTouched(true);
    setSubmitting(true);
    try {
      const ok = await onSave({ ...validation.value, projectId, sessionId: session.id, sceneId: session.sceneId ?? undefined });
      if (ok) onCancel();
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form
      className="session-edit-form"
      data-testid="session-edit-form"
      onSubmit={(event) => {
        event.preventDefault();
        void handleSave();
      }}
    >
      <h4><PencilLine size={14} /> 修正会话</h4>
      <div className="creation-goals-grid">
        <label className="creation-field">
          <span>开始时间</span>
          <input className="paper-input h-9" data-testid="session-edit-started-at" type="datetime-local" value={startedAt} disabled={blocked} onChange={(event) => setStartedAt(event.target.value)} />
        </label>
        <label className="creation-field">
          <span>活动时长（秒）</span>
          <input className="paper-input h-9" data-testid="session-edit-active-seconds" type="number" min={0} max={86400} step={1} inputMode="numeric" value={activeSeconds} disabled={blocked} onChange={(event) => setActiveSeconds(event.target.value)} />
        </label>
        <label className="creation-field">
          <span>净增字数</span>
          <input className="paper-input h-9" data-testid="session-edit-net-chars" type="number" step={1} inputMode="numeric" value={netChars} disabled={blocked} onChange={(event) => setNetChars(event.target.value)} />
        </label>
      </div>

      {touched && issues.length > 0 ? (
        <ul className="session-edit-issues" role="alert">
          {issues.map((issue) => (
            <li key={issue}>{SESSION_CORRECTION_ISSUE_LABELS[issue]}</li>
          ))}
        </ul>
      ) : null}
      {error ? <p className="session-edit-issues" role="alert">{error}</p> : null}

      <div className="session-edit-actions">
        <Button variant="quiet" type="button" data-testid="session-edit-cancel" onClick={onCancel} disabled={blocked}>取消</Button>
        <Button type="button" data-testid="session-edit-save" onClick={() => void handleSave()} disabled={blocked}>
          {blocked ? "保存中…" : "保存修正"}
        </Button>
      </div>
    </form>
  );
}
