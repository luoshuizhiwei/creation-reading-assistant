// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardImportDialog } from "@/features/creation/cards/import-export/CardImportDialog";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-189：导入对话框的相位反馈（读取中→读取完成→计划就绪→导入完成）此前挂在
 * 随内容挂载的节点上，读屏全程无声；改由一条常驻区按相位换文本播报。
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

function region(): HTMLElement {
  return document.querySelector('span.sr-only[role="status"]') as HTMLElement;
}

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

describe("卡片导入对话框 · 相位播报常驻区（BD-189）", () => {
  it("读取中→映射→计划→导入完成，全程一条常驻区换文本、节点不换", async () => {
    const sourceGate = deferred<{ text: string; format: "csv" }>();
    const planGate = deferred<unknown>();
    const applyGate = deferred<{ appliedCardCount: number; appliedRelationCount: number }>();
    services.openAndParseImportSource.mockReturnValue(sourceGate.promise);
    services.planCardImport.mockReturnValue(planGate.promise);
    services.applyCardImport.mockReturnValue(applyGate.promise);
    const onImported = vi.fn();

    render(<CardImportDialog projectId="p1" onClose={() => {}} onImported={onImported} />);

    const node = region();
    expect(node.textContent).toBe("正在读取文件…");
    expect(document.querySelector('.migration-note[role="status"]'), "回退：可见横幅又挂回 role=status").toBeNull();
    expect(document.querySelectorAll('[role="status"]').length, "只允许一条 status 区域").toBe(1);

    sourceGate.resolve({ text: "标题,别名\n林墨,阿墨", format: "csv" });
    await waitFor(() => expect(region().textContent).toBe("文件读取完成，请设置列映射。"));
    expect(region()).toBe(node);

    fireEvent.click(screen.getByRole("button", { name: "预览计划" }));
    planGate.resolve({ id: "plan-1" });
    await waitFor(() => expect(region().textContent).toBe("计划生成完成：2 张卡片、1 条关系，校验通过。"));
    expect(region()).toBe(node);

    fireEvent.click(screen.getByRole("button", { name: "确认导入" }));
    applyGate.resolve({ appliedCardCount: 2, appliedRelationCount: 1 });
    await waitFor(() => expect(region().textContent).toBe("已导入 2 张卡片、1 条关系。"));
    expect(region()).toBe(node);
    await waitFor(() => expect(onImported).toHaveBeenCalled());
  });

  it("校验失败走 alert：常驻区保持静音，不抢述错误细节", async () => {
    const sourceGate = deferred<{ text: string; format: "csv" }>();
    const planGate = deferred<unknown>();
    services.openAndParseImportSource.mockReturnValue(sourceGate.promise);
    services.planCardImport.mockReturnValue(planGate.promise);
    services.summarizePlan.mockReturnValue({ ...summary, errorCount: 2, errorLines: [3, 7] });

    render(<CardImportDialog projectId="p1" onClose={() => {}} onImported={() => {}} />);
    sourceGate.resolve({ text: "标题,别名\n坏行", format: "csv" });
    await waitFor(() => expect(region().textContent).toBe("文件读取完成，请设置列映射。"));

    fireEvent.click(screen.getByRole("button", { name: "预览计划" }));
    planGate.resolve({ id: "plan-2" });
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("行无法通过校验"));
    expect(region().textContent).toBe("");
    expect(document.querySelectorAll('[role="status"]').length).toBe(1);
  });
});
