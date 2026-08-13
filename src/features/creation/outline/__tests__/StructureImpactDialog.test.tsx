// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { StructureImpactDialog } from "../StructureImpactDialog";

afterEach(cleanup);

const rows = [
  { label: "源章节", value: "第一章" },
  { label: "受影响场景数", value: "2" }
];

describe("StructureImpactDialog 影响预览对话框", () => {
  it("渲染影响行；取消只调用 onCancel，不调用 onConfirm", () => {
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    render(<StructureImpactDialog title="预览" rows={rows} onCancel={onCancel} onConfirm={onConfirm} />);
    expect(screen.getByText("第一章")).toBeTruthy();
    fireEvent.click(screen.getByText("取消"));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("确认调用 onConfirm", () => {
    const onConfirm = vi.fn();
    render(<StructureImpactDialog title="预览" rows={rows} onCancel={vi.fn()} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText("确认执行"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("confirmDisabled 时确认按钮禁用并展示提示", () => {
    render(
      <StructureImpactDialog title="预览" rows={rows} onCancel={vi.fn()} onConfirm={vi.fn()} confirmDisabled notice="不可执行" />
    );
    expect((screen.getByText("确认执行") as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText("不可执行")).toBeTruthy();
  });

  it("对话框包含影响行标签与值", () => {
    render(<StructureImpactDialog title="预览" rows={rows} onCancel={vi.fn()} onConfirm={vi.fn()} />);
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("源章节")).toBeTruthy();
    expect(within(dialog).getByText("受影响场景数")).toBeTruthy();
    expect(within(dialog).getByText("2")).toBeTruthy();
  });
});
