// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GlobalCardLibraryPage } from "@/features/creation/cards/GlobalCardLibraryPage";

/**
 * BD-197：全局卡库回收站「恢复」成功后该行随 refreshTrash 卸载，握着焦点的
 * 按钮跌回 body（WCAG 2.4.3，BD-190/HistoryPage 同型）。交接判据同回收站：
 * 原位置下一条优先、末尾退前一条；关窗（取消路径）作废预约。
 */

const fns = vi.hoisted(() => ({
  loadCardTypes: vi.fn(),
  loadCards: vi.fn(),
  runStructure: vi.fn(),
  loadResources: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  loadTrash: vi.fn(),
  loadTrashImpact: vi.fn(),
  showToast: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadCardTypes: fns.loadCardTypes,
    loadCards: fns.loadCards,
    runStructure: fns.runStructure,
    loadResources: fns.loadResources,
    attachResource: fns.attachResource,
    detachResource: fns.detachResource,
    loadTrash: fns.loadTrash,
    loadTrashImpact: fns.loadTrashImpact
  })
}));

vi.mock("@/stores/ui-store", () => ({
  useUIStore: (selector: (state: { showToast: typeof fns.showToast }) => unknown) =>
    selector({ showToast: fns.showToast })
}));

vi.mock("@/stores/creation-store", () => {
  const state = {
    cardTypes: [],
    cards: [],
    projects: [],
    cardsLoading: false,
    cardProjectId: null,
    selectedCardId: undefined,
    selectCard: vi.fn(),
    resources: [],
    draft: null
  };
  return {
    useCreationStore: (selector: (s: Record<string, unknown>) => unknown) => selector(state),
    __store: state
  };
});

function trashRow(id: string, title: string) {
  return {
    id,
    projectId: null,
    entity: "card",
    title,
    deletedAt: "2026-09-10T00:00:00.000Z",
    revision: 1
  };
}

const rows = [trashRow("card-a", "旧城地图"), trashRow("card-b", "灯塔管理员")];

beforeEach(() => {
  vi.clearAllMocks();
  fns.loadCardTypes.mockResolvedValue(undefined);
  fns.loadCards.mockResolvedValue(undefined);
  fns.loadResources.mockResolvedValue(undefined);
  fns.loadTrash.mockResolvedValue(rows);
  fns.runStructure.mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("全局卡库回收站焦点交接（BD-197）", () => {
  it("恢复第一条：焦点交给下一条的「恢复」按钮，不跌回 body", async () => {
    fns.runStructure.mockImplementation(async (command: { type: string; entityId?: string }) => {
      if (command.type === "trash.restore" && command.entityId) {
        fns.loadTrash.mockResolvedValue(rows.filter((r) => r.id !== command.entityId));
      }
      return true;
    });
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(screen.getByRole("button", { name: "回收站 2" })).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "回收站 2" }));

    const first = await screen.findByRole("button", { name: "恢复：旧城地图" });
    const second = screen.getByRole("button", { name: "恢复：灯塔管理员" });
    first.focus();
    fireEvent.click(first);
    await waitFor(() =>
      expect(fns.runStructure).toHaveBeenCalledWith({ type: "trash.restore", entity: "card", entityId: "card-a" })
    );
    await waitFor(() => expect(document.activeElement).toBe(second));
  });
});
