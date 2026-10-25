// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { GlobalCardLibraryPage } from "@/features/creation/cards/GlobalCardLibraryPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-190：附件「确认移除」的按钮随整行卸载。这一行的可聚焦件只有破坏性按钮，
 * 焦点交接给附件分组容器（tabIndex=-1），不得跌回 body（WCAG 2.4.3）。
 */

const loadCardTypes = vi.fn(async () => undefined);
const loadCards = vi.fn(async () => []);
const runStructure = vi.fn(async () => true);
const loadResources = vi.fn();
const attachResource = vi.fn(async () => ({ canceled: true, resource: null }));
const detachResource = vi.fn();
const loadTrash = vi.fn(async () => []);
const loadTrashImpact = vi.fn(async () => null);

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadCardTypes,
    loadCards,
    runStructure,
    loadResources,
    attachResource,
    detachResource,
    loadTrash,
    loadTrashImpact
  })
}));

const cardType = {
  id: "type-character",
  builtIn: true,
  projectId: null,
  kind: "character",
  name: "角色",
  fields: [{ key: "identity", label: "身份", kind: "text" as const, required: false }],
  sortOrder: 1,
  createdAt: "2026-09-11T00:00:00.000Z",
  updatedAt: "2026-09-11T00:00:00.000Z",
  revision: 1
};

const card = {
  id: "card-a",
  projectId: null,
  linkedProjectIds: [],
  usageCount: 0,
  kind: "character",
  title: "林墨",
  aliases: [],
  fields: { identity: "调查员" },
  tags: [],
  createdAt: "2026-09-11T00:00:00.000Z",
  updatedAt: "2026-09-11T00:00:00.000Z",
  revision: 1
};

function makeResource(id: string, name: string) {
  return {
    id,
    projectId: null,
    cardId: "card-a",
    ownerScope: "card",
    role: "attachment",
    relativePath: `resources/cards/card-a/${name}`,
    sha256: "a".repeat(64),
    size: 128,
    originalName: name,
    createdAt: "2026-09-12T00:00:00.000Z"
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  loadTrash.mockResolvedValue([]);
  loadResources.mockResolvedValueOnce([makeResource("res-a", "甲备注.txt")]).mockResolvedValue([]);
  detachResource.mockResolvedValue(true);
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
  useCreationStore.setState({
    cardTypes: [cardType],
    cards: [card],
    cardProjectId: null,
    selectedCardId: "card-a",
    cardsLoading: false,
    projects: []
  });
});

afterEach(() => cleanup());

function assetSection(): HTMLElement {
  return document.querySelector(".global-card-library-assets") as HTMLElement;
}

function rowOf(name: string): HTMLElement {
  return screen.getByText(name).closest("li") as HTMLElement;
}

describe("全局附件移除后的焦点交接（BD-190）", () => {
  it("确认移除后行消失，焦点落在附件分组容器而不是 body", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    const arm = within(rowOf("甲备注.txt")).getByRole("button", { name: "移除" });
    arm.focus();
    fireEvent.click(arm);
    const confirm = within(rowOf("甲备注.txt")).getByRole("button", { name: "确认移除" });
    confirm.focus();
    fireEvent.click(confirm);

    await waitFor(() => expect(screen.getByText("暂无附件。文件归这张全局卡片所有，不依附于任何单一项目。")).toBeTruthy());
    expect(document.activeElement).toBe(assetSection());
    expect(assetSection().getAttribute("tabindex")).toBe("-1");
    expect(assetSection().getAttribute("aria-label")).toBe("全局附件");
  });

  it("对照：移除失败时行还在、焦点原地不动", async () => {
    detachResource.mockResolvedValue(false);
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    const arm = within(rowOf("甲备注.txt")).getByRole("button", { name: "移除" });
    arm.focus();
    fireEvent.click(arm);
    const confirm = within(rowOf("甲备注.txt")).getByRole("button", { name: "确认移除" });
    confirm.focus();
    fireEvent.click(confirm);

    await waitFor(() => expect(detachResource).toHaveBeenCalledWith("res-a"));
    expect(screen.getByText("甲备注.txt")).toBeTruthy();
    expect(document.activeElement).toBe(confirm);
  });
});
