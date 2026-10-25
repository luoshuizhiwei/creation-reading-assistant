// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GlobalCardLibraryPage } from "@/features/creation/cards/GlobalCardLibraryPage";
import { useCreationStore } from "@/stores/creation-store";

// BD-186：「移除」附件同样是两步确认。确认态绑的是当前卡片的上下文，切卡再切回
// 之后必须作废，否则一次点击就把（在新旧语境下都不该被顺手删掉的）附件摘了。

const loadCardTypes = vi.fn(async () => undefined);
const loadCards = vi.fn(async () => []);
const runStructure = vi.fn(async () => true);
const loadResources = vi.fn(async (_query?: { cardId?: string }): Promise<unknown[]> => []);
const attachResource = vi.fn(async () => ({ canceled: true, resource: null }));
const detachResource = vi.fn(async () => true);
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

function makeCard(id: string, title: string) {
  return {
    id,
    projectId: null,
    linkedProjectIds: [],
    usageCount: 0,
    kind: "character",
    title,
    aliases: [],
    fields: { identity: "调查员" },
    tags: [],
    createdAt: "2026-09-11T00:00:00.000Z",
    updatedAt: "2026-09-11T00:00:00.000Z",
    revision: 1
  };
}

function makeResource(id: string, cardId: string, name: string) {
  return {
    id,
    projectId: null,
    cardId,
    ownerScope: "card",
    role: "attachment",
    relativePath: `resources/cards/${cardId}/${name}`,
    sha256: "a".repeat(64),
    size: 128,
    originalName: name,
    createdAt: "2026-09-12T00:00:00.000Z"
  };
}

function selectCard(cardId: string | undefined): void {
  act(() => {
    useCreationStore.getState().selectCard(cardId);
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  loadTrash.mockResolvedValue([]);
  loadResources.mockImplementation(async (query) =>
    query?.cardId === "card-a" ? [makeResource("res-a", "card-a", "甲备注.txt")] : [makeResource("res-b", "card-b", "乙附件.txt")]
  );
  detachResource.mockResolvedValue(true);
  useCreationStore.setState({
    cardTypes: [cardType],
    cards: [makeCard("card-a", "林墨"), makeCard("card-b", "北岸灯塔")],
    cardProjectId: null,
    selectedCardId: "card-a",
    cardsLoading: false,
    projects: []
  });
});

afterEach(() => cleanup());

describe("全局卡片库 · 切卡作废附件移除的两步确认（BD-186）", () => {
  it("切到另一张卡再切回，旧卡的「确认移除」退回一步态", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    fireEvent.click(screen.getByRole("button", { name: "移除" }));
    expect(screen.getByRole("button", { name: "确认移除" })).toBeTruthy();

    selectCard("card-b");
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-b" }));
    selectCard("card-a");
    await waitFor(() => expect(screen.getByText("甲备注.txt")).toBeTruthy());

    expect(screen.queryByRole("button", { name: "确认移除" })).toBeNull();
    expect(screen.getByRole("button", { name: "移除" })).toBeTruthy();
  });

  it("作废后第一次点击只是重新武装，第二次才真的摘除", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    fireEvent.click(screen.getByRole("button", { name: "移除" }));
    selectCard("card-b");
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-b" }));
    selectCard("card-a");
    await waitFor(() => expect(screen.getByText("甲备注.txt")).toBeTruthy());

    fireEvent.click(screen.getByRole("button", { name: "移除" }));
    expect(detachResource).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "确认移除" }));
    await waitFor(() => expect(detachResource).toHaveBeenCalledWith("res-a"));
  });

  it("不切卡时两步确认保持原样：一次点击只武装、不摘除", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    fireEvent.click(screen.getByRole("button", { name: "移除" }));

    expect(detachResource).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "确认移除" })).toBeTruthy();
  });
});
