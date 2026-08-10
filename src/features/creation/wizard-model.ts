import type { CreateProjectInput, CreationProjectTemplate } from "@/types/creation";

/** 章节工作流默认阶段：规划、待写、写作中、初稿、修订、定稿、已发布。 */
export const DEFAULT_CHAPTER_WORKFLOW = ["规划", "待写", "写作中", "初稿", "修订", "定稿", "已发布"];

/** 每周更新日标签，索引即数字值：0=周日、1=周一 … 6=周六（与 Date.getDay() 一致）。 */
export const WEEKDAY_LABELS = ["日", "一", "二", "三", "四", "五", "六"] as const;

export interface TemplateOption {
  value: CreationProjectTemplate;
  label: string;
  description: string;
}

export const TEMPLATE_OPTIONS: TemplateOption[] = [
  {
    value: "blank",
    label: "空白项目",
    description: "不预设结构偏好，从零开始组织卷、章与场景。"
  },
  {
    value: "long-form",
    label: "分卷长篇",
    description: "以卷为骨架组织长篇：先规划卷，再在卷内排布章节与场景。"
  },
  {
    value: "serial",
    label: "连续连载",
    description: "按章节连续更新，章节平铺、不强制分卷。"
  }
];

export function templateLabel(template: CreationProjectTemplate): string {
  return TEMPLATE_OPTIONS.find((option) => option.value === template)?.label ?? template;
}

export interface WizardDraft {
  template: CreationProjectTemplate;
  title: string;
  description: string;
  genre: string;
  totalWordGoal: string;
  dailyWordGoal: string;
  weeklyWordGoal: string;
  targetDate: string;
  weeklyUpdateDays: number[];
  chapterWorkflow: string[];
}

export const INITIAL_WIZARD_DRAFT: WizardDraft = {
  template: "blank",
  title: "",
  description: "",
  genre: "",
  totalWordGoal: "",
  dailyWordGoal: "",
  weeklyWordGoal: "",
  targetDate: "",
  weeklyUpdateDays: [],
  chapterWorkflow: [...DEFAULT_CHAPTER_WORKFLOW]
};

export type WizardValidation = { ok: true } | { ok: false; message: string };

/**
 * 校验向导指定步骤（0=模板、1=信息、2=目标与工作流）。
 * 模板步骤始终有效；信息步骤要求标题 1..200 字；目标步骤要求工作流非空且数字目标合法。
 */
export function validateWizardStep(step: number, draft: WizardDraft): WizardValidation {
  if (step === 1) {
    const title = draft.title.trim();
    if (!title) {
      return { ok: false, message: "请输入作品标题。" };
    }
    if (title.length > 200) {
      return { ok: false, message: "标题不能超过 200 字。" };
    }
    return { ok: true };
  }
  if (step === 2) {
    if (draft.chapterWorkflow.filter((stage) => stage.trim()).length === 0) {
      return { ok: false, message: "章节工作流至少保留一个阶段。" };
    }
    for (const raw of [draft.totalWordGoal, draft.dailyWordGoal, draft.weeklyWordGoal]) {
      if (raw.trim() === "") continue;
      const value = Number(raw);
      if (!Number.isInteger(value) || value < 1) {
        return { ok: false, message: "目标字数需为正整数。" };
      }
    }
    return { ok: true };
  }
  return { ok: true };
}

function optionalNumber(raw: string): number | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  const value = Number(trimmed);
  return Number.isInteger(value) && value >= 1 ? value : undefined;
}

/** 把表单草稿整理成提交契约：去空白、去重排序更新日、过滤空工作流阶段。 */
export function buildCreateInput(draft: WizardDraft): CreateProjectInput {
  const workflow = draft.chapterWorkflow.map((stage) => stage.trim()).filter(Boolean);
  const weeklyUpdateDays = [...new Set(draft.weeklyUpdateDays.filter((day) => day >= 0 && day <= 6))].sort((a, b) => a - b);
  return {
    title: draft.title.trim(),
    description: draft.description.trim() || undefined,
    genre: draft.genre.trim() || undefined,
    template: draft.template,
    totalWordGoal: optionalNumber(draft.totalWordGoal),
    dailyWordGoal: optionalNumber(draft.dailyWordGoal),
    weeklyWordGoal: optionalNumber(draft.weeklyWordGoal),
    targetDate: draft.targetDate || undefined,
    weeklyUpdateDays,
    chapterWorkflow: workflow
  };
}
