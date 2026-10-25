// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GlobalCardLibraryPage } from "@/features/creation/cards/GlobalCardLibraryPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";

/**
 * BD-189：点「删除全局卡片」后才发起影响统计；「正在统计…」随忙碌对话框一起
 * 挂载＝不播报。播报交给页面级常驻区——它跨 busy/结果两个对话框都活着。
 */

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const loadCardTypes = vi.fn(async () => undefined);
const loadCards = vi.fn(async () => []);
const runStructure = vi.fn(async () => true);
const loadResources = vi.fn(async (_query?: { cardId?: string }): Promise<unknown[]> => []);
const attachResource = vi.fn(async () => ({ canceled: true, resource: null }));
const detachResource = vi.fn(async () => true);
const loadTrash = vi.fn(async () => []);
const loadTrashImpact = vi.fn();

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

const impact = {
  title: "林墨",
  childVolumeCount: 0,
  childChapterCount: 0,
  childSceneCount: 0,
  relatedCardCount: 2,
  resourceCount: 1,
  approxChars: 0,
  warnings: []
};

function region(): HTMLElement {
  return document.querySelector('span.sr-only[role="status"]') as HTMLElement;
}

beforeEach(() => {
  vi.clearAllMocks();
  loadTrash.mockResolvedValue([]);
  loadResources.mockResolvedValue([]);
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

describe("全局卡片库 · 删除影响统计的页面级常驻播报（BD-189）", () => {
  it("点删除后 busy 换文本、可见横幅无 role；结果出现后区域静音且节点不换", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));

    const node = region();
    expect(node.textContent).toBe("");

    const gate = deferred<typeof impact>();
    loadTrashImpact.mockReturnValue(gate.promise);
    fireEvent.click(screen.getByRole("button", { name: "删除全局卡片" }));

    await waitFor(() => expect(region().textContent).toBe("正在统计关联项目和引用影响…"));
    expect(document.querySelector('p[role="status"]'), "回退：忙碌对话框的横幅又自带 role=status").toBeNull();
    expect(screen.getAllByText("正在统计关联项目和引用影响…").length, "可见横幅 + 常驻区各一份").toBe(2);

    gate.resolve(impact);
    await waitFor(() => expect(region().textContent).toBe(""));
    expect(region()).toBe(node);
    expect(screen.getByText("2 条卡片关系")).toBeTruthy();
  });

  it("阳性对照：未触发删除流程时不产生任何播报文本", async () => {
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-a" }));
    expect(region().textContent).toBe("");
    expect(loadTrashImpact).not.toHaveBeenCalled();
  });
});
