// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { BackgroundPage } from "@/features/creation/background/BackgroundPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardSummary } from "@/types/creation";

vi.mock("@/services/creation-service", () => ({
  inboxList: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  cardsList: vi.fn(),
  runStructure: vi.fn(),
  listProjects: vi.fn(),
  readProjectHome: vi.fn(),
  readProjectNavigation: vi.fn(),
  readProjectOutline: vi.fn(),
  readSceneBody: vi.fn(),
  updateSceneBody: vi.fn(),
  watchProject: vi.fn(),
  search: vi.fn(),
  replacePreview: vi.fn(),
  replaceApply: vi.fn(),
  statsView: vi.fn(),
  sessionList: vi.fn(),
  sessionReport: vi.fn(),
  sessionDelete: vi.fn(),
  proofQuery: vi.fn(),
  trashList: vi.fn(),
  snapshotList: vi.fn(),
  cardRead: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  exportDraft: vi.fn(),
  importDraftPreview: vi.fn(),
  exportProjectBundle: vi.fn(),
  importProjectBundle: vi.fn(),
  annotationList: vi.fn(),
  annotationCreate: vi.fn(),
  annotationUpdate: vi.fn(),
  annotationDelete: vi.fn(),
  resourceList: vi.fn(),
  attachResource: vi.fn(),
  detachResource: vi.fn(),
  projectExport: vi.fn(),
  migrationStatus: vi.fn(),
  migrationRun: vi.fn(),
  createProject: vi.fn()
}));

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function makeCard(projectId: string, id: string, kind: string, title: string): CardSummary {
  return {
    id,
    projectId,
    kind,
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-08-09T10:00:00.000Z",
    updatedAt: "2026-08-09T10:00:00.000Z",
    revision: 1
  };
}

const p1Loc = makeCard("p1", "loc-1", "location", "黄沙镇");
const p1Rule = makeCard("p1", "rule-1", "worldRule", "灵气复苏");
const p2Loc = makeCard("p2", "loc-2", "location", "别处");
const p1Char = makeCard("p1", "char-1", "character", "苏青");

function resetStores(): void {
  useCreationStore.setState({
    projects: [],
    selectedId: undefined,
    navigations: {},
    outlines: {},
    cardProjectId: undefined,
    cardTypes: [],
    relationTypes: [],
    cards: [],
    cardRelations: {},
    selectedCardId: undefined,
    cardsLoading: false,
    selectedSceneId: undefined,
    sceneViews: {},
    abnormalExit: false,
    recoveryNoticeDismissed: false,
    loading: false,
    watchConnected: false,
    leaveGuard: undefined
  });
  useUIStore.setState({ toasts: [] });
}

beforeEach(() => {
  resetStores();
  vi.mocked(creationService.cardsList).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.runStructure).mockResolvedValue({ commandType: "card.delete" } as never);
});
afterEach(() => cleanup());

describe("BackgroundPage 跨项目隔离", () => {
  it("只展示当前项目的背景卡；其他项目的背景卡与非背景卡不渲染", async () => {
    // 服务端返回跨项目脏数据时，组件必须仍按当前项目过滤。
    vi.mocked(creationService.cardsList).mockResolvedValue([p1Loc, p1Rule, p2Loc, p1Char]);

    render(<BackgroundPage projectId="p1" />);

    expect(await screen.findByText("黄沙镇")).toBeDefined();
    expect(screen.getByText("灵气复苏")).toBeDefined();
    expect(screen.queryByText("别处")).toBeNull();
    expect(screen.queryByText("苏青")).toBeNull();
  });

  it("选中其他项目的背景卡时：不渲染详情并清除选择", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([p1Loc, p2Loc]);
    useCreationStore.setState({ selectedCardId: "loc-2" });

    render(<BackgroundPage projectId="p1" />);

    await waitFor(() => expect(screen.queryByText("别处")).toBeNull());
    await waitFor(() => expect(useCreationStore.getState().selectedCardId).toBeUndefined());
  });

  it("只能删除当前项目的背景卡", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([p1Loc, p2Loc, p1Char]);

    render(<BackgroundPage projectId="p1" />);
    fireEvent.click(await screen.findByText("黄沙镇"));

    await waitFor(() => expect(screen.getByText("删除")).toBeDefined());
    fireEvent.click(screen.getByText("删除"));
    fireEvent.click(screen.getByText("确认删除"));

    await waitFor(() =>
      expect(creationService.runStructure).toHaveBeenCalledWith({ type: "card.delete", cardId: "loc-1" })
    );
    expect(vi.mocked(creationService.runStructure)).toHaveBeenCalledTimes(1);
  });

  it("项目 A 迟到响应不覆盖 B：切到 B 后 A 的卡片列表不得写入", async () => {
    const dA = deferred<CardSummary[]>();
    vi.mocked(creationService.cardsList)
      .mockImplementationOnce(() => dA.promise)
      .mockResolvedValueOnce([p2Loc]);

    const view = render(<BackgroundPage projectId="p1" />);
    await waitFor(() => expect(creationService.cardsList).toHaveBeenCalledTimes(1));

    view.rerender(<BackgroundPage projectId="p2" />);
    expect(await screen.findByText("别处")).toBeDefined();

    await act(async () => {
      dA.resolve([p1Loc]);
    });

    expect(useCreationStore.getState().cardProjectId).toBe("p2");
    expect(useCreationStore.getState().cards).toEqual([p2Loc]);
    expect(screen.queryByText("黄沙镇")).toBeNull();
  });
});
