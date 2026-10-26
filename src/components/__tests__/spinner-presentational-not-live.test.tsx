// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardImportDialog } from "@/features/creation/cards/import-export/CardImportDialog";
import { OperationProgressDialog } from "@/features/creation/operation/OperationProgressDialog";
import { Spinner } from "@/components/ui";
import { useUIStore } from "@/stores/ui-store";
import type { OperationState } from "@/types/operation";

/**
 * BD-195：Spinner 原语此前自带 role="status" + aria-label（默认「加载中…」）。
 * 十个使用点全是「插入即带内容」的死播报，且默认 label 混进宿主可及名
 * （按钮忙态名变成「加载中… 导出中…」）。本文件锁两件事：
 * ① Spinner 退回纯视觉（无角色、无名字，图标 aria-hidden）；
 * ② 摘除后播报不欠账——不确定进度提示并入操作对话框的 BD-187 常驻区换文本，
 * 导入对话框的常驻区计数不因 Spinner 破功（旧形态忙态会多出一条隐形 status）。
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const services = vi.hoisted(() => ({
  openAndParseImportSource: vi.fn(),
  loadImportSchema: vi.fn(),
  parseImportSource: vi.fn(),
  buildDefaultMapping: vi.fn(),
  mappingCanResolveType: vi.fn(),
  mappingIsReady: vi.fn(),
  planCardImport: vi.fn(),
  summarizePlan: vi.fn(),
  applyCardImport: vi.fn()
}));

vi.mock("@/features/creation/cards/import-export/card-import-export-client", () => services);

const preview = {
  format: "csv",
  headers: ["标题", "别名"],
  rows: [{ 标题: "林墨", 别名: "阿墨" }, { 标题: "北岸灯塔", 别名: "" }],
  warnings: []
};

const mapping = {
  titleSource: "标题",
  aliasSources: ["别名"],
  tagSources: [],
  fieldSources: {}
};

const summary = {
  cardCount: 2,
  relationCount: 1,
  skipped: 0,
  warningCount: 0,
  errorCount: 0,
  errorLines: [] as number[]
};

beforeEach(() => {
  vi.clearAllMocks();
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
  services.loadImportSchema.mockResolvedValue({
    relationTypes: [],
    cardTypes: [{ kind: "character", name: "角色", fields: [{ key: "role", label: "定位", kind: "text" }] }]
  });
  services.parseImportSource.mockResolvedValue(preview);
  services.buildDefaultMapping.mockReturnValue(mapping);
  services.mappingCanResolveType.mockReturnValue(false);
  services.mappingIsReady.mockReturnValue(true);
  services.summarizePlan.mockReturnValue(summary);
});

afterEach(() => cleanup());

describe("Spinner 原语是纯视觉装饰（BD-195）", () => {
  it("不带 role=status/aria-live/aria-label，图标 aria-hidden", () => {
    const { container } = render(<Spinner />);
    const root = container.firstElementChild as HTMLElement;
    expect(root.getAttribute("role")).toBeNull();
    expect(root.getAttribute("aria-live")).toBeNull();
    expect(root.getAttribute("aria-label")).toBeNull();
    expect(container.querySelectorAll('[role="status"], [aria-live]').length).toBe(0);
    const icon = root.firstElementChild as HTMLElement;
    expect(icon.getAttribute("aria-hidden")).toBe("true");
  });

  it("放进按钮不污染可及名：忙态名字就是可见文本", () => {
    render(
      <button type="button">
        <Spinner size={14} /> 导出中…
      </button>
    );
    expect(screen.getByRole("button", { name: "导出中…" })).toBeTruthy();
  });
});

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

function indeterminateState(): OperationState {
  return makeState({
    progress: { phase: "scanning", completed: 0, total: null, bytesCompleted: null, bytesTotal: null, indeterminate: true }
  });
}

describe("操作对话框·不确定进度由常驻区换文本播报（BD-195）", () => {
  it("确定进度常驻区静音；换到不确定分支在同一节点上播「正在处理，请稍候…」", () => {
    const view = render(
      <OperationProgressDialog state={makeState()} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />
    );
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");
    // 可见不确定提示块未挂载时，全页只允许这一条 status。
    expect(document.querySelectorAll('[role="status"]').length).toBe(1);

    view.rerender(
      <OperationProgressDialog state={indeterminateState()} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("正在处理，请稍候…");
    expect(document.querySelectorAll('[role="status"]').length, "回退：可见提示块又长出第二条 status").toBe(1);
    expect(document.querySelector('p.operation-indeterminate[aria-live]'), "回退：可见提示块又挂回自带 aria-live").toBeNull();
    expect(screen.getByText(/正在处理，请稍候/, { selector: "p.operation-indeterminate" })).toBeTruthy();
  });

  it("不确定→完成在同一节点上收尾，不留双区", () => {
    const view = render(
      <OperationProgressDialog state={indeterminateState()} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />
    );
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("正在处理，请稍候…");

    view.rerender(
      <OperationProgressDialog state={makeState({ status: "completed" })} isCommitting={false} onCancel={vi.fn()} onClose={vi.fn()} onReset={vi.fn()} />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("操作已完成。");
    expect(document.querySelectorAll('[role="status"]').length).toBe(1);
  });
});

describe("导入对话框·忙态计数不被 Spinner 破功（BD-195）", () => {
  it("点「预览计划」进入忙态：全页仍只有一条 status，按钮名不含「加载中…」", async () => {
    const sourceGate = deferred<{ text: string; format: "csv" }>();
    const planGate = deferred<unknown>();
    services.openAndParseImportSource.mockReturnValue(sourceGate.promise);
    services.planCardImport.mockReturnValue(planGate.promise);

    render(<CardImportDialog projectId="p1" onClose={vi.fn()} onImported={vi.fn()} />);
    sourceGate.resolve({ text: "标题,别名\n林墨,阿墨", format: "csv" });
    await waitFor(() => expect(screen.getByRole("status").textContent).toBe("文件读取完成，请设置列映射。"));

    fireEvent.click(screen.getByRole("button", { name: "预览计划" }));
    // 忙态：Spinner 挂在按钮里，旧形态会多出一条隐形 status 并污染按钮名。
    expect(document.querySelectorAll('[role="status"]').length).toBe(1);
    expect(screen.getByRole("button", { name: "生成计划中…" })).toBeTruthy();
    planGate.resolve({ id: "plan-1" });
    await waitFor(() => expect(screen.getByRole("status").textContent).toContain("计划生成完成"));
  });
});
