// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HistoryPage } from "@/features/creation/history/HistoryPage";
import type { CreationProjectSummary, TrashItem } from "@/types/creation";

/**
 * BD-197：回收站行内「恢复/永久删除」成功后条目随列表刷新整行卸载，握着焦点的
 * 按钮被移除、焦点跌回 body（WCAG 2.4.3，BD-190 收件箱删行的同型缺陷）。
 * 交接判据：原位置下一条优先，末尾退前一条，删空退到列表容器；
 * 条目没走（失败/未刷新）不动焦点，取消删除作废预约。
 */

const mocks = vi.hoisted(() => ({
  loadTrash: vi.fn(),
  restoreTrash: vi.fn(),
  purgeTrash: vi.fn(),
  loadSnapshots: vi.fn(),
  previewSnapshot: vi.fn(),
  restoreSnapshotWithProtection: vi.fn(),
  loadTrashImpact: vi.fn(),
  runStructure: vi.fn(),
  loadNavigation: vi.fn(),
  loadCards: vi.fn(),
  loadOutline: vi.fn(),
  showToast: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadTrash: mocks.loadTrash,
    restoreTrash: mocks.restoreTrash,
    purgeTrash: mocks.purgeTrash,
    loadSnapshots: mocks.loadSnapshots,
    previewSnapshot: mocks.previewSnapshot,
    restoreSnapshotWithProtection: mocks.restoreSnapshotWithProtection,
    loadTrashImpact: mocks.loadTrashImpact,
    runStructure: mocks.runStructure,
    loadNavigation: mocks.loadNavigation,
    loadCards: mocks.loadCards,
    loadOutline: mocks.loadOutline
  })
}));

vi.mock("@/stores/creation-store", () => ({
  useCreationStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ navigations: {}, cardProjectId: null, cards: [] })
}));

vi.mock("@/stores/ui-store", () => ({
  useUIStore: (selector: (state: { showToast: typeof mocks.showToast }) => unknown) =>
    selector({ showToast: mocks.showToast })
}));

const project: CreationProjectSummary = {
  id: "p1",
  title: "项目",
  setup: { weeklyUpdateDays: [], chapterWorkflow: ["草稿"] },
  createdAt: "2026-08-01T00:00:00.000Z",
  updatedAt: "2026-08-01T00:00:00.000Z",
  revision: 1
};

function trashRow(id: string, title: string): TrashItem {
  return { id, projectId: "p1", entity: "chapter", title, deletedAt: "2026-08-12T09:00:00.000Z", revision: 2 };
}

const rows = [trashRow("c-1", "第一章"), trashRow("c-2", "第二章"), trashRow("c-3", "第三章")];

beforeEach(() => {
  vi.clearAllMocks();
  mocks.loadSnapshots.mockResolvedValue([]);
  mocks.loadNavigation.mockResolvedValue(null);
  mocks.loadCards.mockResolvedValue([]);
  mocks.loadOutline.mockResolvedValue(null);
  mocks.loadTrashImpact.mockResolvedValue({
    title: "", childVolumeCount: 0, childChapterCount: 0, childSceneCount: 0,
    relatedCardCount: 0, resourceCount: 0, approxChars: 0, warnings: []
  });
});

afterEach(() => cleanup());

describe("HistoryPage 回收站焦点交接（BD-197）", () => {
  it("恢复中间条目：焦点交给原位置下一条的「恢复」按钮", async () => {
    mocks.loadTrash.mockResolvedValue(rows);
    mocks.restoreTrash.mockImplementation(async (_p: string, _e: string, id: string) => {
      mocks.loadTrash.mockResolvedValue(rows.filter((r) => r.id !== id));
      return true;
    });
    render(<HistoryPage project={project} />);
    const second = await screen.findByRole("button", { name: "恢复：第二章" });
    fireEvent.click(screen.getByRole("button", { name: "恢复：第一章" }));
    await waitFor(() => expect(mocks.restoreTrash).toHaveBeenCalledWith("p1", "chapter", "c-1"));
    await waitFor(() => expect(document.activeElement).toBe(second));
  });

  it("恢复末尾条目：退交接给前一条", async () => {
    mocks.loadTrash.mockResolvedValue(rows);
    mocks.restoreTrash.mockImplementation(async (_p: string, _e: string, id: string) => {
      mocks.loadTrash.mockResolvedValue(rows.filter((r) => r.id !== id));
      return true;
    });
    render(<HistoryPage project={project} />);
    const second = await screen.findByRole("button", { name: "恢复：第二章" });
    fireEvent.click(screen.getByRole("button", { name: "恢复：第三章" }));
    await waitFor(() => expect(document.activeElement).toBe(second));
  });

  it("恢复最后一条清空列表：焦点退到空态段落，不跌回 body", async () => {
    mocks.loadTrash.mockResolvedValue([rows[1]]);
    mocks.restoreTrash.mockImplementation(async () => {
      mocks.loadTrash.mockResolvedValue([]);
      return true;
    });
    render(<HistoryPage project={project} />);
    const only = await screen.findByRole("button", { name: "恢复：第二章" });
    fireEvent.click(only);
    await waitFor(() => expect(mocks.restoreTrash).toHaveBeenCalled());
    // 删空后 ul 随空态换掉，退路是 history-empty 段落（tabIndex=-1）——
    // 焦点留在原地区域，键盘用户不必从文档开头重走（WCAG 2.4.3）。
    const empty = await waitFor(() => {
      const el = document.querySelector("p.history-empty");
      expect(el).toBeTruthy();
      return el as HTMLElement;
    });
    await waitFor(() => expect(document.activeElement).toBe(empty));
  });

  it("永久删除确认成功：焦点交给原位置下一条", async () => {
    mocks.loadTrash.mockResolvedValue(rows);
    mocks.purgeTrash.mockImplementation(async (_p: string, _e: string, id: string) => {
      mocks.loadTrash.mockResolvedValue(rows.filter((r) => r.id !== id));
      return true;
    });
    render(<HistoryPage project={project} />);
    await screen.findByRole("button", { name: "恢复：第二章" });
    fireEvent.click(screen.getByRole("button", { name: "永久删除：第一章" }));
    fireEvent.change(await screen.findByLabelText("输入对象名称确认"), { target: { value: "第一章" } });
    fireEvent.click(screen.getByRole("button", { name: "确认永久删除" }));
    await waitFor(() => expect(mocks.purgeTrash).toHaveBeenCalledWith("p1", "chapter", "c-1"));
    const second = await screen.findByRole("button", { name: "恢复：第二章" });
    await waitFor(() => expect(document.activeElement).toBe(second));
  });

  it("取消删除作废预约：之后的恢复刷新按恢复自己的判据走", async () => {
    mocks.loadTrash.mockResolvedValue(rows);
    mocks.restoreTrash.mockImplementation(async (_p: string, _e: string, id: string) => {
      mocks.loadTrash.mockResolvedValue(rows.filter((r) => r.id !== id));
      return true;
    });
    render(<HistoryPage project={project} />);
    await screen.findByRole("button", { name: "恢复：第二章" });
    fireEvent.click(screen.getByRole("button", { name: "永久删除：第一章" }));
    fireEvent.click(await screen.findByRole("button", { name: "取消" }));
    // 作废后点「恢复：第二章」——若第一章的旧预约未作废，交接线会错乱；
    // 正确行为是按第二章自己的判据交给第三章。
    fireEvent.click(screen.getByRole("button", { name: "恢复：第二章" }));
    await waitFor(() => expect(mocks.restoreTrash).toHaveBeenCalledWith("p1", "chapter", "c-2"));
    const third = await screen.findByRole("button", { name: "恢复：第三章" });
    await waitFor(() => expect(document.activeElement).toBe(third));
  });
});
