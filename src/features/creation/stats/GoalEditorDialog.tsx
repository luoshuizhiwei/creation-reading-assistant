import { useEffect, useState } from "react";
import { Target } from "lucide-react";
import { Button, Dialog, TextInput } from "@/components/ui";
import type { CreationProjectSetup } from "@/types/creation";
import { DEFAULT_WORD_METRIC, WORD_METRIC_LABELS, type WordMetric } from "./stats-calculator";

/**
 * 目标编辑对话框。
 *
 * 编辑项目创作目标：总字数 / 每日字数 / 每周字数 / 目标日期 / 每周更新日 / 主指标。
 * 表单为受控组件：所有字段与校验逻辑完整（可独立测试）；保存通过 onSave 回调
 * 提交。主指标写入项目属于公共 seam（project.updateGoal 命令），提交与冲突
 * 提示由集成方接线后启用；本组件不直接依赖任何未就绪接口。
 */

const WEEKDAY_LABELS = ["一", "二", "三", "四", "五", "六", "日"];

/** 目标更新载荷：局部更新，缺省字段保持不变。 */
export interface GoalUpdatePatch {
  totalWordGoal?: number;
  dailyWordGoal?: number;
  weeklyWordGoal?: number;
  targetDate?: string;
  weeklyUpdateDays?: number[];
  mainMetric?: WordMetric;
}

export interface GoalEditorDialogProps {
  open: boolean;
  initial: CreationProjectSetup;
  onClose: () => void;
  /** 提交目标更新；返回 false 表示失败（冲突等），错误由调用方展示。 */
  onSave: (patch: GoalUpdatePatch) => Promise<boolean>;
  busy?: boolean;
  error?: string | null;
  /** 主指标写入当前不可用（seam 未接线）时置为 true，禁止保存主指标变更。 */
  metricLocked?: boolean;
}

interface DraftState {
  totalWordGoal: string;
  dailyWordGoal: string;
  weeklyWordGoal: string;
  targetDate: string;
  weeklyUpdateDays: number[];
  mainMetric: WordMetric;
}

function toDraft(setup: CreationProjectSetup): DraftState {
  return {
    totalWordGoal: setup.totalWordGoal === undefined ? "" : String(setup.totalWordGoal),
    dailyWordGoal: setup.dailyWordGoal === undefined ? "" : String(setup.dailyWordGoal),
    weeklyWordGoal: setup.weeklyWordGoal === undefined ? "" : String(setup.weeklyWordGoal),
    targetDate: setup.targetDate ?? "",
    weeklyUpdateDays: setup.weeklyUpdateDays.filter((day) => day >= 1 && day <= 7),
    mainMetric: DEFAULT_WORD_METRIC
  };
}

function parseGoalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || parsed < 0) return undefined;
  return parsed;
}

function validateDraft(draft: DraftState): { ok: true; patch: GoalUpdatePatch } | { ok: false; message: string } {
  const totalWordGoal = parseGoalNumber(draft.totalWordGoal);
  if (draft.totalWordGoal.trim() && totalWordGoal === undefined) {
    return { ok: false, message: "总字数必须是 0 或正整数。" };
  }
  const dailyWordGoal = parseGoalNumber(draft.dailyWordGoal);
  if (draft.dailyWordGoal.trim() && dailyWordGoal === undefined) {
    return { ok: false, message: "每日目标必须是 0 或正整数。" };
  }
  const weeklyWordGoal = parseGoalNumber(draft.weeklyWordGoal);
  if (draft.weeklyWordGoal.trim() && weeklyWordGoal === undefined) {
    return { ok: false, message: "每周目标必须是 0 或正整数。" };
  }
  if (draft.targetDate.trim() && Number.isNaN(Date.parse(draft.targetDate))) {
    return { ok: false, message: "目标日期格式无效。" };
  }
  const patch: GoalUpdatePatch = {
    ...(draft.totalWordGoal.trim() ? { totalWordGoal } : { totalWordGoal: undefined }),
    ...(draft.dailyWordGoal.trim() ? { dailyWordGoal } : { dailyWordGoal: undefined }),
    ...(draft.weeklyWordGoal.trim() ? { weeklyWordGoal } : { weeklyWordGoal: undefined }),
    ...(draft.targetDate.trim() ? { targetDate: draft.targetDate } : { targetDate: undefined }),
    weeklyUpdateDays: draft.weeklyUpdateDays,
    mainMetric: draft.mainMetric
  };
  return { ok: true, patch };
}

export function GoalEditorDialog({ open, initial, onClose, onSave, busy, error, metricLocked }: GoalEditorDialogProps) {
  const [draft, setDraft] = useState<DraftState>(() => toDraft(initial));
  const [validationMessage, setValidationMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) setDraft(toDraft(initial));
  }, [open, initial]);

  if (!open) return null;

  const submitting = busy ?? saving;

  const handleSave = async () => {
    const validation = validateDraft(draft);
    if (!validation.ok) {
      setValidationMessage(validation.message);
      return;
    }
    setValidationMessage(null);
    setSaving(true);
    try {
      const ok = await onSave(validation.patch);
      if (ok) onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open={open}
      title={<span className="flex items-center gap-1.5"><Target size={15} /> 编辑创作目标</span>}
      ariaLabel="编辑创作目标"
      onClose={submitting ? undefined : onClose}
      width="max-w-xl"
      className="goal-editor-dialog"
      footer={
        <>
          <Button variant="quiet" onClick={onClose} disabled={submitting}>取消</Button>
          <Button onClick={() => void handleSave()} disabled={submitting}>
            {submitting ? "保存中…" : "保存目标"}
          </Button>
        </>
      }
    >
          <div className="creation-goals-grid">
            <label className="creation-field">
              <span>总字数 <em className="optional">可选</em></span>
              <input className="paper-input h-9" type="number" min={0} step={1} inputMode="numeric" value={draft.totalWordGoal} onChange={(event) => setDraft((current) => ({ ...current, totalWordGoal: event.target.value }))} />
            </label>
            <label className="creation-field">
              <span>每日目标 <em className="optional">可选</em></span>
              <TextInput type="number" min={0} step={1} inputMode="numeric" value={draft.dailyWordGoal} onChange={(event) => setDraft((current) => ({ ...current, dailyWordGoal: event.target.value }))} />
            </label>
            <label className="creation-field">
              <span>每周目标 <em className="optional">可选</em></span>
              <TextInput type="number" min={0} step={1} inputMode="numeric" value={draft.weeklyWordGoal} onChange={(event) => setDraft((current) => ({ ...current, weeklyWordGoal: event.target.value }))} />
            </label>
            <label className="creation-field">
              <span>目标日期 <em className="optional">可选</em></span>
              <TextInput type="date" value={draft.targetDate} onChange={(event) => setDraft((current) => ({ ...current, targetDate: event.target.value }))} />
            </label>
          </div>

          <div className="creation-field">
            <span className="creation-field-title">每周更新日 <em className="optional">可选</em></span>
            <div className="creation-chip-row" role="group" aria-label="每周更新日">
              {WEEKDAY_LABELS.map((label, day) => {
                const selected = draft.weeklyUpdateDays.includes(day + 1);
                return (
                  <button
                    key={day}
                    type="button"
                    className={`creation-chip ${selected ? "selected" : ""}`}
                    aria-pressed={selected}
                    onClick={() =>
                      setDraft((current) => ({
                        ...current,
                        weeklyUpdateDays: selected ? current.weeklyUpdateDays.filter((item) => item !== day + 1) : [...current.weeklyUpdateDays, day + 1]
                      }))
                    }
                  >
                    周{label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="creation-field">
            <span className="creation-field-title">主指标</span>
            <div className="creation-chip-row" role="group" aria-label="主指标">
              {(Object.keys(WORD_METRIC_LABELS) as WordMetric[]).map((metric) => (
                <button
                  key={metric}
                  type="button"
                  className={`creation-chip ${draft.mainMetric === metric ? "selected" : ""}`}
                  aria-pressed={draft.mainMetric === metric}
                  disabled={metricLocked}
                  title={metricLocked ? "主指标写入接口待集成（SEAM REQUEST）" : WORD_METRIC_LABELS[metric]}
                  onClick={() => setDraft((current) => ({ ...current, mainMetric: metric }))}
                >
                  {WORD_METRIC_LABELS[metric]}
                </button>
              ))}
            </div>
            {metricLocked ? (
              <p className="stats-note">主指标选择为本地预览，保存到项目需等待目标更新接口接入。</p>
            ) : null}
          </div>

          {validationMessage && <span className="creation-field-error">{validationMessage}</span>}
          {error && <span className="creation-field-error">{error}</span>}
    </Dialog>
  );
}
