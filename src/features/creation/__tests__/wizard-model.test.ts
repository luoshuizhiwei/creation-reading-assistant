import { describe, expect, it } from "vitest";
import {
  buildCreateInput,
  DEFAULT_CHAPTER_WORKFLOW,
  INITIAL_WIZARD_DRAFT,
  templateLabel,
  validateWizardStep
} from "@/features/creation/wizard-model";
import type { WizardDraft } from "@/features/creation/wizard-model";

function draft(overrides: Partial<WizardDraft> = {}): WizardDraft {
  return { ...INITIAL_WIZARD_DRAFT, ...overrides };
}

describe("validateWizardStep", () => {
  it("accepts any draft on the template step", () => {
    expect(validateWizardStep(0, draft())).toEqual({ ok: true });
  });

  it("rejects a blank title", () => {
    expect(validateWizardStep(1, draft({ title: "   " }))).toEqual({ ok: false, message: "请输入作品标题。" });
  });

  it("rejects a title longer than 200 characters", () => {
    expect(validateWizardStep(1, draft({ title: "长".repeat(201) }))).toEqual({ ok: false, message: "标题不能超过 200 字。" });
  });

  it("accepts a trimmed title of exactly 200 characters", () => {
    expect(validateWizardStep(1, draft({ title: "长".repeat(200) }))).toEqual({ ok: true });
  });

  it("rejects an empty chapter workflow", () => {
    expect(validateWizardStep(2, draft({ chapterWorkflow: [] }))).toEqual({ ok: false, message: "章节工作流至少保留一个阶段。" });
  });

  it("rejects a workflow made only of whitespace", () => {
    expect(validateWizardStep(2, draft({ chapterWorkflow: ["  ", ""] }))).toEqual({ ok: false, message: "章节工作流至少保留一个阶段。" });
  });

  it("rejects a negative word goal", () => {
    expect(validateWizardStep(2, draft({ totalWordGoal: "-1" }))).toEqual({ ok: false, message: "目标字数需为正整数。" });
  });

  it("rejects a non-numeric word goal", () => {
    expect(validateWizardStep(2, draft({ dailyWordGoal: "abc" }))).toEqual({ ok: false, message: "目标字数需为正整数。" });
  });

  it("accepts empty and positive integer goals", () => {
    expect(validateWizardStep(2, draft({ totalWordGoal: "", dailyWordGoal: "1", weeklyWordGoal: "50000" }))).toEqual({ ok: true });
  });

  it("rejects zero and decimal word goals", () => {
    expect(validateWizardStep(2, draft({ dailyWordGoal: "0" }))).toEqual({ ok: false, message: "目标字数需为正整数。" });
    expect(validateWizardStep(2, draft({ weeklyWordGoal: "1.5" }))).toEqual({ ok: false, message: "目标字数需为正整数。" });
  });
});

describe("buildCreateInput", () => {
  it("trims title and drops blank optional fields", () => {
    const input = buildCreateInput(draft({ title: "  测试项目  ", description: "  ", genre: "" }));
    expect(input.title).toBe("测试项目");
    expect(input.description).toBeUndefined();
    expect(input.genre).toBeUndefined();
  });

  it("keeps positive integer goals and drops blank ones", () => {
    const input = buildCreateInput(draft({ totalWordGoal: " 120000 ", dailyWordGoal: "1500", weeklyWordGoal: "" }));
    expect(input.totalWordGoal).toBe(120000);
    expect(input.dailyWordGoal).toBe(1500);
    expect(input.weeklyWordGoal).toBeUndefined();
  });

  it("deduplicates and sorts weekly update days", () => {
    const input = buildCreateInput(draft({ weeklyUpdateDays: [3, 1, 3, 0, 7, -1] }));
    expect(input.weeklyUpdateDays).toEqual([0, 1, 3]);
  });

  it("filters blank workflow stages and preserves the default when untouched", () => {
    const input = buildCreateInput(draft());
    expect(input.chapterWorkflow).toEqual(DEFAULT_CHAPTER_WORKFLOW);
    const custom = buildCreateInput(draft({ chapterWorkflow: [" 规划 ", "", " 定稿"] }));
    expect(custom.chapterWorkflow).toEqual(["规划", "定稿"]);
  });

  it("keeps target date only when provided", () => {
    expect(buildCreateInput(draft()).targetDate).toBeUndefined();
    expect(buildCreateInput(draft({ targetDate: "2026-12-31" })).targetDate).toBe("2026-12-31");
  });
});

describe("templateLabel", () => {
  it("maps every built-in template to its label", () => {
    expect(templateLabel("blank")).toBe("空白项目");
    expect(templateLabel("long-form")).toBe("分卷长篇");
    expect(templateLabel("serial")).toBe("连续连载");
  });
});
