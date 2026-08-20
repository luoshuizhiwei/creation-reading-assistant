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
      activeSeconds: Number(activeSeconds),
      netChars: Number(netChars)
    });
  }, [activeSeconds, netChars, projectId, session, startedAt]);

  const issues = validation.ok ? [] : validation.issues;
  const canSubmit = !busy && !submitting;

  const handleSave = async () => {
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
      onSubmit={(event) => {
        event.preventDefault();
        void handleSave();
      }}
    >
      <h4><PencilLine size={14} /> 修正会话</h4>
      <div className="creation-goals-grid">
        <label className="creation-field">
          <span>开始时间</span>
          <input className="paper-input h-9" type="datetime-local" value={startedAt} onChange={(event) => setStartedAt(event.target.value)} />
        </label>
        <label className="creation-field">
          <span>活动时长（秒）</span>
          <input className="paper-input h-9" type="number" min={0} max={86400} step={1} inputMode="numeric" value={activeSeconds} onChange={(event) => setActiveSeconds(event.target.value)} />
        </label>
        <label className="creation-field">
          <span>净增字数</span>
          <input className="paper-input h-9" type="number" step={1} inputMode="numeric" value={netChars} onChange={(event) => setNetChars(event.target.value)} />
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
        <Button variant="quiet" type="button" onClick={onCancel} disabled={submitting}>取消</Button>
        <Button type="button" onClick={() => void handleSave()} disabled={submitting}>
          {submitting ? "保存中…" : "保存修正"}
        </Button>
      </div>
    </form>
  );
}
