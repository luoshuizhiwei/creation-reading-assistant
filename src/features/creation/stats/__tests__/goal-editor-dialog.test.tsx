// @vitest-environment jsdom
import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { GoalEditorDialog, type GoalUpdatePatch } from "../GoalEditorDialog";
import type { CreationProjectSetup } from "@/types/creation";

afterEach(cleanup);

function setupOf(partial: Partial<CreationProjectSetup> = {}): CreationProjectSetup {
  return {
    template: "blank",
    weeklyUpdateDays: [1, 5],
    chapterWorkflow: ["规划"],
    totalWordGoal: 50000,
    dailyWordGoal: 2000,
    weeklyWordGoal: 14000,
    targetDate: "2026-12-31",
    ...partial
  };
}

function renderDialog(overrides: { onSave?: (patch: GoalUpdatePatch) => Promise<boolean>; metricLocked?: boolean } = {}) {
  const onSave = overrides.onSave ?? vi.fn(async () => true);
  const onClose = vi.fn();
  render(
    <GoalEditorDialog
      open
      initial={setupOf()}
      onClose={onClose}
      onSave={onSave}
      metricLocked={overrides.metricLocked ?? false}
    />
  );
  return { onSave, onClose };
}

describe("GoalEditorDialog 目标编辑", () => {
  it("展示全部目标字段并预填初始值", () => {
    renderDialog();
    expect(screen.getByRole("dialog", { name: "编辑创作目标" })).toBeDefined();
    expect((screen.getByLabelText(/总字数/) as HTMLInputElement).value).toBe("50000");
    expect((screen.getByLabelText(/每日目标/) as HTMLInputElement).value).toBe("2000");
    expect((screen.getByLabelText(/每周目标/) as HTMLInputElement).value).toBe("14000");
    expect((screen.getByLabelText(/目标日期/) as HTMLInputElement).value).toBe("2026-12-31");
    expect(screen.getByRole("button", { name: "周一" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "周日" }).getAttribute("aria-pressed")).toBe("false");
    expect(screen.getByRole("button", { name: "字" }).getAttribute("aria-pressed")).toBe("true");
  });

  it("提交合法修改：onSave 收到局部更新载荷", async () => {
    const { onSave, onClose } = renderDialog();
    fireEvent.change(screen.getByLabelText(/总字数/), { target: { value: "60000" } });
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    await vi.waitFor(() => {
      expect(onSave).toHaveBeenCalledTimes(1);
    });
    const patch = onSave.mock.calls[0][0] as GoalUpdatePatch;
    expect(patch.totalWordGoal).toBe(60000);
    expect(patch.dailyWordGoal).toBe(2000);
    expect(patch.targetDate).toBe("2026-12-31");
    expect(patch.weeklyUpdateDays).toEqual([1, 5]);
    expect(onClose).toHaveBeenCalled();
  });

  it("清空字段表示清除目标：patch 对应字段为 undefined", async () => {
    const { onSave } = renderDialog();
    fireEvent.change(screen.getByLabelText(/总字数/), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText(/目标日期/), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    await vi.waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const patch = onSave.mock.calls[0][0] as GoalUpdatePatch;
    expect(patch.totalWordGoal).toBeUndefined();
    expect(patch.targetDate).toBeUndefined();
  });

  it("非法数字被拒绝并提示", async () => {
    const { onSave } = renderDialog();
    fireEvent.change(screen.getByLabelText(/总字数/), { target: { value: "-5" } });
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText(/总字数必须是 0 或正整数/)).toBeDefined();
  });

  it("metricLocked 时主指标按钮禁用，其余字段仍可编辑", () => {
    renderDialog({ metricLocked: true });
    expect((screen.getByRole("button", { name: "汉字" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByLabelText(/总字数/) as HTMLInputElement).disabled).toBe(false);
    expect(screen.getByText(/主指标选择为本地预览/)).toBeDefined();
  });

  it("主指标可切换并进入 patch", async () => {
    const { onSave } = renderDialog();
    fireEvent.click(screen.getByRole("button", { name: "字（含标点）" }));
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    await vi.waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect((onSave.mock.calls[0][0] as GoalUpdatePatch).mainMetric).toBe("withPunctuation");
  });

  it("onSave 失败（冲突）时不关闭，错误由调用方展示", async () => {
    const onSave = vi.fn(async () => false);
    const onClose = vi.fn();
    render(
      <GoalEditorDialog open initial={setupOf()} onClose={onClose} onSave={onSave} error="目标已被其他会话更新，请刷新后重试。" />
    );
    fireEvent.click(screen.getByRole("button", { name: "保存目标" }));
    await vi.waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByText(/目标已被其他会话更新/)).toBeDefined();
  });
});
