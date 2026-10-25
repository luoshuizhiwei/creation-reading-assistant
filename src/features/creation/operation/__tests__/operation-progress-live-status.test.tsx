// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import type { OperationState } from "../../../types/operation";
import { OperationProgressDialog } from "../OperationProgressDialog";

// BD-187：长操作对话框在 running 时就已打开，「操作已完成/已取消」结果块是之后
// 挂载的——插入即带内容的 role=status 不会被播报。终态必须由常驻区在同一节点上报出。

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

afterEach(() => cleanup());

describe("操作进度对话框 · 终态播报走常驻区（BD-187）", () => {
  it("running→completed / running→cancelled 都在同一条常驻区上播报", () => {
    const view = render(
      <OperationProgressDialog state={makeState()} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />
    );

    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    view.rerender(
      <OperationProgressDialog
        state={makeState({ status: "completed" })}
        isCommitting={false}
        onCancel={vi.fn()}
        onClose={vi.fn()}
        onReset={vi.fn()}
      />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("操作已完成。");

    view.rerender(
      <OperationProgressDialog
        state={makeState({ status: "cancelled" })}
        isCommitting={false}
        onCancel={vi.fn()}
        onClose={vi.fn()}
        onReset={vi.fn()}
      />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toContain("操作已取消");

    // 可见结果块仍在，但不再自带 status 角色（避免双区）。
    expect(screen.getByText(/操作已取消/, { selector: "p" })).toBeTruthy();
    expect(document.querySelector('.operation-result[role="status"]')).toBeNull();
  });

  it("失败态仍是 alert，播报区保持静默", () => {
    render(
      <OperationProgressDialog
        state={makeState({ status: "failed", error: { message: "磁盘空间不足" } as OperationState["error"] })}
        isCommitting={false}
        onCancel={vi.fn()}
        onClose={vi.fn()}
        onReset={vi.fn()}
      />
    );
    expect(screen.getByRole("alert").textContent).toContain("磁盘空间不足");
    expect(screen.getByRole("status").textContent).toBe("");
  });
});
