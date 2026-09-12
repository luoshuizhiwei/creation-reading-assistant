// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { GlobalCardLibraryPage } from "@/features/creation/cards/GlobalCardLibraryPage";
import { useCreationStore } from "@/stores/creation-store";

const loadCardTypes = vi.fn(async () => undefined);
const loadCards = vi.fn(async () => []);
const runStructure = vi.fn(async () => true);
const loadResources = vi.fn(async () => []);
const attachResource = vi.fn(async () => ({ canceled: true, resource: null }));
const detachResource = vi.fn(async () => true);
const loadTrash = vi.fn(async () => []);
const loadTrashImpact = vi.fn(async () => ({
  entity: "card" as const,
  entityId: "card-global",
  title: "林墨",
  relatedVolumeCount: 0,
  relatedChapterCount: 0,
  relatedSceneCount: 0,
  relatedCardCount: 3,
  resourceCount: 2,
  linkedProjectCount: 2,
  sceneReferenceCount: 1,
  annotationCount: 4,
  warnings: []
}));

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

const globalCard = {
  id: "card-global",
  projectId: null,
  linkedProjectIds: ["project-a", "project-b"],
  usageCount: 2,
  kind: "character",
  title: "林墨",
  aliases: ["阿墨"],
  fields: { identity: "调查员" },
  tags: ["主角"],
  createdAt: "2026-09-11T00:00:00.000Z",
  updatedAt: "2026-09-11T00:00:00.000Z",
  revision: 3
};

beforeEach(() => {
  vi.clearAllMocks();
  loadTrash.mockResolvedValue([]);
  loadTrashImpact.mockResolvedValue({
    entity: "card",
    entityId: "card-global",
    title: "林墨",
    relatedVolumeCount: 0,
    relatedChapterCount: 0,
    relatedSceneCount: 0,
    relatedCardCount: 3,
    resourceCount: 2,
    linkedProjectCount: 2,
    sceneReferenceCount: 1,
    annotationCount: 4,
    warnings: []
  });
  useCreationStore.setState({
    cardTypes: [cardType],
    cards: [globalCard],
    cardProjectId: null,
    selectedCardId: "card-global",
    cardsLoading: false,
    projects: [
      { id: "project-a", title: "长夜航线", setup: {}, createdAt: "", updatedAt: "", revision: 1 },
      { id: "project-b", title: "雾港手记", setup: {}, createdAt: "", updatedAt: "", revision: 1 }
    ]
  });
});

afterEach(() => cleanup());

describe("GlobalCardLibraryPage", () => {
  it("读取全局作用域，并展示字段检索入口和项目使用账册", async () => {
    render(<GlobalCardLibraryPage />);

    await waitFor(() => expect(loadCards).toHaveBeenCalledWith({ cardKind: undefined, search: undefined }));
    expect(screen.getByRole("region", { name: "全局卡片库" })).toBeTruthy();
    expect(screen.getAllByText("林墨")).toHaveLength(2);
    expect(screen.getByText("调查员")).toBeTruthy();
    expect(screen.getByText("使用项目")).toBeTruthy();
    expect(screen.getByText("2")).toBeTruthy();
    expect(screen.getByText("长夜航线")).toBeTruthy();
    expect(screen.getByText("雾港手记")).toBeTruthy();

    fireEvent.change(screen.getByPlaceholderText("搜索名称、别名或字段"), { target: { value: "调查员" } });
    await waitFor(() => expect(loadCards).toHaveBeenLastCalledWith({ cardKind: undefined, search: "调查员" }));
  });

  it("新建时发送全局 card.create，不携带 projectId", async () => {
    render(<GlobalCardLibraryPage />);

    fireEvent.click(screen.getByRole("button", { name: "新建全局卡片" }));
    fireEvent.change(screen.getByPlaceholderText("卡片主名称"), { target: { value: "北岸灯塔" } });
    fireEvent.click(screen.getByRole("button", { name: "创建卡片" }));

    await waitFor(() => expect(runStructure).toHaveBeenCalledTimes(1));
    expect(runStructure).toHaveBeenCalledWith({
      type: "card.create",
      kind: "character",
      title: "北岸灯塔",
      aliases: [],
      fields: {},
      tags: []
    });
    expect(runStructure.mock.calls[0]?.[0]).not.toHaveProperty("projectId");
  });

  it("全局附件查询与添加都不携带 projectId", async () => {
    attachResource.mockResolvedValueOnce({
      canceled: false,
      resource: { commandType: "resource.attach", sequence: 1, resourceId: "resource-global", updatedAt: "" }
    });
    render(<GlobalCardLibraryPage />);
    await waitFor(() => expect(loadResources).toHaveBeenCalledWith({ cardId: "card-global" }));
    fireEvent.click(screen.getByRole("button", { name: "添加附件" }));
    await waitFor(() => expect(attachResource).toHaveBeenCalledWith(undefined, "card-global", "attachment"));
  });

  it("删除前展示跨项目影响，并将卡片移入全局回收站", async () => {
    render(<GlobalCardLibraryPage />);

    fireEvent.click(screen.getByRole("button", { name: "删除全局卡片" }));
    await waitFor(() => expect(loadTrashImpact).toHaveBeenCalledWith({
      kind: "trash.impact",
      entity: "card",
      entityId: "card-global"
    }));
    expect(screen.getByText("2 个关联项目")).toBeTruthy();
    expect(screen.getByText("3 条卡片关系")).toBeTruthy();
    expect(screen.getByText("1 个场景引用")).toBeTruthy();
    expect(screen.getByText("4 条批注")).toBeTruthy();
    expect(screen.getByText("2 个附件/封面")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "移入回收站" }));
    await waitFor(() => expect(runStructure).toHaveBeenCalledWith({ type: "card.delete", cardId: "card-global" }));
  });

  it("从全局回收站恢复卡片时不要求 projectId", async () => {
    loadTrash.mockResolvedValue([{
      entity: "card",
      id: "card-deleted",
      projectId: null,
      title: "旧城地图",
      deletedAt: "2026-09-10T00:00:00.000Z"
    }]);
    render(<GlobalCardLibraryPage />);

    await waitFor(() => expect(screen.getByRole("button", { name: "回收站 1" })).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "回收站 1" }));
    fireEvent.click(screen.getByRole("button", { name: "恢复" }));

    await waitFor(() => expect(runStructure).toHaveBeenCalledWith({
      type: "trash.restore",
      entity: "card",
      entityId: "card-deleted"
    }));
  });
});
