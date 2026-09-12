// @vitest-environment jsdom
import React from "react";
/**
 * OperationProgressDialog 行为测试：
 *  - 有总数时显示真实百分比，绝不编造百分比
 *  - 不确定进度时只显示处理中提示，不显示任何百分比
 *  - 不可中断的提交阶段：取消按钮禁用 + “正在完成安全提交”提示
 *  - 终态区分 完成 / 取消 / 失败，且错误信息可见（不只在 toast）
 *  - 资源扫描完成态渲染只读扫描面板
 */
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { OperationState, ResourceIntegrityReport } from "../../../types/operation";
import { OperationProgressDialog } from "../OperationProgressDialog";

function makeState(overrides: Partial<OperationState> = {}): OperationState {
  return {
    operationId: "op-1",
    kind: "backup.create",
    status: "running",
    progress: { phase: "copying", completed: 42, total: 100, bytesCompleted: null, bytesTotal: null, indeterminate: false },
    startedAt: 0,
    deferredCancel: false,
    ...overrides
  } as OperationState;
}

function scanReport(issues: ResourceIntegrityReport["issues"]): ResourceIntegrityReport {
  return { issues, scannedRecordCount: issues.length, scannedFileCount: issues.length };
}

describe("OperationProgressDialog", () => {
  it("有总数时显示真实百分比，不显示伪百分比", () => {
    render(<OperationProgressDialog state={makeState()} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText("42%")).toBeTruthy();
    expect(screen.queryByText(/正在处理/)).toBeNull();
  });

  it("不确定进度时只显示处理中提示，不显示任何百分比", () => {
    const state = makeState({
      progress: { phase: "scanning", completed: 0, total: null, bytesCompleted: null, bytesTotal: null, indeterminate: true }
    });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText(/正在处理，请稍候/)).toBeTruthy();
    expect(screen.queryByText(/%/)).toBeNull();
  });

  it("不可中断的提交阶段禁用取消并显示安全提示", () => {
    const state = makeState({ progress: { phase: "committing", completed: 0, total: 1, bytesCompleted: null, bytesTotal: null, indeterminate: false } });
    const onCancel = vi.fn();
    render(<OperationProgressDialog state={state} isCommitting onCancel={onCancel} onClose={vi.fn()} onReset={vi.fn()} />);
    const cancel = screen.getByRole("button", { name: "取消" }) as HTMLButtonElement;
    expect(cancel.disabled).toBe(true);
    expect(screen.getByText(/正在完成安全提交/)).toBeTruthy();
    fireEvent.click(cancel);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it("运行中的安全阶段取消按钮可用", () => {
    const onCancel = vi.fn();
    render(<OperationProgressDialog state={makeState()} isCommitting={false} onCancel={onCancel} onClose={vi.fn()} onReset={vi.fn()} />);
    const cancel = screen.getByRole("button", { name: "取消" }) as HTMLButtonElement;
    expect(cancel.disabled).toBe(false);
    fireEvent.click(cancel);
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("完成态显示成功文案", () => {
    const state = makeState({ status: "completed" });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText(/操作已完成/)).toBeTruthy();
  });

  it("项目包导入完成态展示稳定 ID 映射（含加密项目包）", () => {
    const state = makeState({
      kind: "bundle.import-encrypted",
      status: "completed",
      result: {
        status: "completed",
        result: {
          projectId: "project-imported",
          counts: { volumes: 1, chapters: 1, scenes: 1, cards: 2, relations: 0, snapshots: 0, resources: 0, annotations: 0 },
          cardMappings: [
            { sourceCardId: "card-reused", targetCardId: "card-reused", action: "reused" },
            { sourceCardId: "card-source", targetCardId: "card-copy", action: "copied" }
          ]
        }
      }
    });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText(/卡片稳定 ID 处理结果/)).toBeTruthy();
    expect(screen.getByText("同内容复用")).toBeTruthy();
    expect(screen.getByText("导入副本")).toBeTruthy();
    expect(screen.getByText(/card-copy/)).toBeTruthy();
  });

  it("取消态显示已取消文案且无取消按钮（仅保留关闭）", () => {
    const state = makeState({ status: "cancelled" });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText(/操作已取消/)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "取消" })).toBeNull();
    expect(screen.getAllByRole("button", { name: "关闭" }).length).toBeGreaterThan(0);
  });

  it("失败态完整展示错误信息（标题/消息/代码），不只在 toast", () => {
    const state = makeState({
      status: "failed",
      error: { code: "E_BACKUP_WRITE", message: "写入备份文件失败" }
    });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText("操作失败")).toBeTruthy();
    expect(screen.getByText("写入备份文件失败")).toBeTruthy();
    expect(screen.getByText(/E_BACKUP_WRITE/)).toBeTruthy();
  });

  it("重试按钮以 onReset 重新执行，且取消按钮禁用", () => {
    const onReset = vi.fn();
    const state = makeState({ status: "failed", error: { message: "boom" } });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={onReset} />);
    const retry = screen.getByRole("button", { name: "重试" });
    fireEvent.click(retry);
    expect(onReset).toHaveBeenCalledTimes(1);
  });

  it("资源扫描完成态渲染只读扫描面板（含空结果提示）", () => {
    const state = makeState({ operationId: "scan-1", kind: "resource.scan", status: "completed", result: { status: "completed", result: scanReport([]) } });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getByText(/未发现资源完整性问题/)).toBeTruthy();
  });

  it("资源扫描完成态渲染分类问题（相对路径、无删除入口）", () => {
    const state = makeState({
      operationId: "scan-1",
      kind: "resource.scan",
      status: "completed",
      result: {
        status: "completed",
        result: scanReport([
          { type: "file-missing", relativePath: "volumes/01/intro.txt", message: "文件不存在" },
          { type: "size-mismatch", relativePath: "assets/cover.png", message: "尺寸不一致" }
        ])
      }
    });
    render(<OperationProgressDialog state={state} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />);
    expect(screen.getAllByText("文件缺失").length).toBeGreaterThan(0);
    expect(screen.getAllByText("大小不符").length).toBeGreaterThan(0);
    expect(screen.getByText("volumes/01/intro.txt")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /删除|修复|清理/ })).toBeNull();
  });
});
