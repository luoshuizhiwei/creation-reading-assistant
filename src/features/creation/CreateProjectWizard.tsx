import { useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, FilePlus2, X } from "lucide-react";
import { Button, Dialog, TextArea, TextInput } from "@/components/ui";
import {
  buildCreateInput,
  INITIAL_WIZARD_DRAFT,
  TEMPLATE_OPTIONS,
  validateWizardStep,
  WEEKDAY_LABELS,
  type WizardDraft
} from "@/features/creation/wizard-model";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useUIStore } from "@/stores/ui-store";

const STEP_LABELS = ["来源与模板", "作品信息", "目标与工作流"];

export function CreateProjectWizard({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<WizardDraft>(INITIAL_WIZARD_DRAFT);
  const [submitting, setSubmitting] = useState(false);
  const submitLockRef = useRef(false);
  const [titleTouched, setTitleTouched] = useState(false);
  const { createProject } = useCreationActions();
  const showToast = useUIStore((state) => state.showToast);

  const step0Ref = useRef<HTMLInputElement>(null);
  const step1Ref = useRef<HTMLInputElement>(null);
  const step2Ref = useRef<HTMLInputElement>(null);
  const workflowInputRef = useRef<HTMLInputElement>(null);
  const [workflowInput, setWorkflowInput] = useState("");

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setDraft(INITIAL_WIZARD_DRAFT);
    setSubmitting(false);
    submitLockRef.current = false;
    setTitleTouched(false);
    setWorkflowInput("");
    window.setTimeout(() => step0Ref.current?.focus(), 0);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    if (step === 0) step0Ref.current?.focus();
    if (step === 1) step1Ref.current?.focus();
    if (step === 2) step2Ref.current?.focus();
  }, [open, step]);


  if (!open) return null;

  const validation = validateWizardStep(step, draft);
  const nextDisabled = !validation.ok;

  const goNext = () => {
    if (nextDisabled) return;
    setStep((current) => Math.min(2, current + 1));
  };

  const goBack = () => setStep((current) => Math.max(0, current - 1));

  const addWorkflowStage = () => {
    const stage = workflowInput.trim();
    if (!stage) return;
    if (!draft.chapterWorkflow.some((existing) => existing.trim() === stage)) {
      setDraft((current) => ({ ...current, chapterWorkflow: [...current.chapterWorkflow, stage] }));
    }
    setWorkflowInput("");
    workflowInputRef.current?.focus();
  };

  const submit = async () => {
    if (submitLockRef.current || !validateWizardStep(2, draft).ok) return;
    submitLockRef.current = true;
    setSubmitting(true);
    try {
      const tree = await createProject(buildCreateInput(draft));
      if (tree) {
        showToast({ tone: "success", title: "项目已创建", body: `《${tree.project.title}》已加入项目书架。` });
        onClose();
      }
    } finally {
      submitLockRef.current = false;
      setSubmitting(false);
    }
  };

  const titleError = step === 1 && titleTouched && !validation.ok ? validation.message : undefined;

  return (
    <Dialog
      open={open}
      title={
        <div className="min-w-0">
          <p className="desktop-card-label">New project</p>
          <span id="creation-wizard-title">新建作品</span>
        </div>
      }
      ariaLabel="新建作品"
      onClose={submitting ? undefined : onClose}
      width="max-w-3xl"
      className="creation-wizard-dialog"
      footer={
        <>
          <Button variant="quiet" onClick={onClose} disabled={submitting}>
            取消
          </Button>
          {step > 0 && (
            <Button variant="secondary" onClick={goBack} disabled={submitting}>
              <ChevronLeft size={15} />
              上一步
            </Button>
          )}
          {step < 2 ? (
            <Button onClick={goNext} disabled={nextDisabled}>
              下一步
              <ChevronRight size={15} />
            </Button>
          ) : (
            <Button onClick={() => void submit()} disabled={nextDisabled || submitting}>
              <FilePlus2 size={15} />
              {submitting ? "正在创建…" : "创建项目"}
            </Button>
          )}
        </>
      }
    >
      <ol className="creation-wizard-steps" aria-label="新建作品步骤">
          {STEP_LABELS.map((label, index) => (
            <li key={label} className={index === step ? "active" : index < step ? "done" : ""} aria-current={index === step ? "step" : undefined}>
              <span>{index + 1}</span>
              {label}
            </li>
          ))}
        </ol>

        <div className="creation-wizard-body">
          {step === 0 && (
            <fieldset className="creation-step">
              <legend>选择结构模板</legend>
              <p className="creation-step-note">模板只保存结构偏好，不会预填题材套路；创建后会自动生成第一章与默认场景。</p>
              <div className="creation-template-grid" role="radiogroup" aria-label="结构模板">
                {TEMPLATE_OPTIONS.map((option, index) => {
                  const selected = draft.template === option.value;
                  return (
                    <label key={option.value} className={`creation-template-card ${selected ? "selected" : ""}`}>
                      <input
                        ref={index === 0 ? step0Ref : undefined}
                        type="radio"
                        name="creation-template"
                        value={option.value}
                        checked={selected}
                        onChange={() => setDraft((current) => ({ ...current, template: option.value }))}
                        className="sr-only"
                      />
                      <strong>{option.label}</strong>
                      <span>{option.description}</span>
                    </label>
                  );
                })}
                <div className="creation-template-card disabled" aria-disabled="true">
                  <strong>旧稿导入</strong>
                  <span>后续开放：从 TXT / Markdown / DOCX 预览并导入旧稿。</span>
                </div>
              </div>
            </fieldset>
          )}

          {step === 1 && (
            <fieldset className="creation-step">
              <legend>作品信息</legend>
              <div className="creation-form-grid">
                <label className="creation-field">
                  <span>
                    标题 <em>必填</em>
                  </span>
                  <input
                    ref={step1Ref}
                    className="paper-input h-9"
                    value={draft.title}
                    maxLength={201}
                    placeholder="例如：测试项目"
                    onChange={(event) => {
                      setDraft((current) => ({ ...current, title: event.target.value }));
                      setTitleTouched(true);
                    }}
                    aria-invalid={Boolean(titleError)}
                  />
                  <span className="creation-field-hint">1–200 字</span>
                  {titleError && <span className="creation-field-error">{titleError}</span>}
                </label>
                <label className="creation-field">
                  <span>
                    简介 <em className="optional">可选</em>
                  </span>
                  <TextArea
                    rows={4}
                    value={draft.description}
                    maxLength={2000}
                    placeholder="一句话介绍作品方向、主题或故事梗概。"
                    onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
                  />
                </label>
                <label className="creation-field">
                  <span>
                    题材 <em className="optional">可选</em>
                  </span>
                  <TextInput
                    value={draft.genre}
                    maxLength={40}
                    placeholder="例如：悬疑、都市、历史"
                    onChange={(event) => setDraft((current) => ({ ...current, genre: event.target.value }))}
                  />
                </label>
              </div>
            </fieldset>
          )}

          {step === 2 && (
            <fieldset className="creation-step">
              <legend>创作目标与章节工作流</legend>
              <div className="creation-goals-grid">
                <label className="creation-field">
                  <span>
                    总字数 <em className="optional">可选</em>
                  </span>
                  <input ref={step2Ref} className="paper-input h-9" type="number" min={1} step={1} inputMode="numeric" value={draft.totalWordGoal} placeholder="例如：500000" onChange={(event) => setDraft((current) => ({ ...current, totalWordGoal: event.target.value }))} />
                </label>
                <label className="creation-field">
                  <span>
                    每日目标 <em className="optional">可选</em>
                  </span>
                  <TextInput type="number" min={1} step={1} inputMode="numeric" value={draft.dailyWordGoal} placeholder="例如：2000" onChange={(event) => setDraft((current) => ({ ...current, dailyWordGoal: event.target.value }))} />
                </label>
                <label className="creation-field">
                  <span>
                    每周目标 <em className="optional">可选</em>
                  </span>
                  <TextInput type="number" min={1} step={1} inputMode="numeric" value={draft.weeklyWordGoal} placeholder="例如：14000" onChange={(event) => setDraft((current) => ({ ...current, weeklyWordGoal: event.target.value }))} />
                </label>
                <label className="creation-field">
                  <span>
                    目标日期 <em className="optional">可选</em>
                  </span>
                  <TextInput type="date" value={draft.targetDate} onChange={(event) => setDraft((current) => ({ ...current, targetDate: event.target.value }))} />
                </label>
              </div>

              <div className="creation-field">
                <span className="creation-field-title">
                  每周更新日 <em className="optional">可选</em>
                </span>
                <div className="creation-chip-row" role="group" aria-label="每周更新日">
                  {WEEKDAY_LABELS.map((label, day) => {
                    const selected = draft.weeklyUpdateDays.includes(day);
                    return (
                      <button
                        key={day}
                        type="button"
                        className={`creation-chip ${selected ? "selected" : ""}`}
                        aria-pressed={selected}
                        onClick={() =>
                          setDraft((current) => ({
                            ...current,
                            weeklyUpdateDays: selected ? current.weeklyUpdateDays.filter((item) => item !== day) : [...current.weeklyUpdateDays, day]
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
                <span className="creation-field-title">章节工作流</span>
                <div className="creation-chip-row">
                  {draft.chapterWorkflow.map((stage, index) => (
                    <span key={`${stage}-${index}`} className="creation-chip selected workflow-stage">
                      {stage}
                      <button
                        type="button"
                        aria-label={`删除阶段 ${stage}`}
                        onClick={() => setDraft((current) => ({ ...current, chapterWorkflow: current.chapterWorkflow.filter((_, itemIndex) => itemIndex !== index) }))}
                      >
                        <X size={11} />
                      </button>
                    </span>
                  ))}
                </div>
                <div className="creation-workflow-add">
                  <input
                    ref={workflowInputRef}
                    className="paper-input h-9"
                    value={workflowInput}
                    maxLength={20}
                    placeholder="添加自定义阶段，回车确认"
                    onChange={(event) => setWorkflowInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        addWorkflowStage();
                      }
                    }}
                  />
                  <Button variant="secondary" type="button" onClick={addWorkflowStage}>
                    添加
                  </Button>
                </div>
                {step === 2 && !validation.ok && <span className="creation-field-error">{validation.message}</span>}
              </div>
            </fieldset>
          )}
        </div>
    </Dialog>
  );
}
