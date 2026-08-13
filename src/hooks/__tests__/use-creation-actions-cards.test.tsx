// @vitest-environment jsdom
import React from "react";
import { act, cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useCreationActions } from "@/hooks/useCreationActions";
import * as creationService from "@/services/creation-service";
import { useCreationStore } from "@/stores/creation-store";
import type { CardSummary } from "@/types/creation";

vi.mock("@/services/creation-service", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/services/creation-service")>();
  return { ...actual, cardsList: vi.fn() };
});

function makeCard(projectId: string, id?: string): CardSummary {
  return {
    id: id ?? `card-${projectId}`,
    projectId,
    kind: "character",
    title: `卡片 ${projectId}`,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "2026-08-11T00:00:00.000Z",
    updatedAt: "2026-08-11T00:00:00.000Z",
    revision: 1
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

let actions: ReturnType<typeof useCreationActions>;

function Harness() {
  actions = useCreationActions();
  return null;
}

beforeEach(() => {
  useCreationStore.setState({
    cardProjectId: "a",
    cards: [makeCard("a")],
    cardTypes: [],
    relationTypes: [],
    cardRelations: {},
    selectedCardId: "card-a",
    cardsLoading: false
  });
  vi.mocked(creationService.cardsList).mockReset();
});

afterEach(() => cleanup());

describe("useCreationActions card project isolation", () => {
  it("切换项目立即清空旧卡片，且迟到响应不能覆盖新项目", async () => {
    const responseA = deferred<CardSummary[]>();
    const responseB = deferred<CardSummary[]>();
    vi.mocked(creationService.cardsList)
      .mockReturnValueOnce(responseA.promise)
      .mockReturnValueOnce(responseB.promise);
    render(<Harness />);

    let requestA!: Promise<CardSummary[] | undefined>;
    let requestB!: Promise<CardSummary[] | undefined>;
    act(() => {
      requestA = actions.loadCards({ projectId: "a" });
      requestB = actions.loadCards({ projectId: "b" });
    });

    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [],
      selectedCardId: undefined,
      cardsLoading: true
    });

    await act(async () => {
      responseA.resolve([makeCard("a")]);
      await requestA;
    });
    expect(useCreationStore.getState().cards).toEqual([]);

    await act(async () => {
      responseB.resolve([makeCard("b")]);
      await requestB;
    });
    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [makeCard("b")],
      cardsLoading: false
    });
  });

  it("成功时返回本次实际加载的卡片数组，并写入对应项目 store", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([makeCard("b"), makeCard("b", "card-b2")]);
    render(<Harness />);

    let result!: CardSummary[] | undefined;
    await act(async () => {
      result = await actions.loadCards({ projectId: "b" });
    });

    expect(result).toEqual([makeCard("b"), makeCard("b", "card-b2")]);
    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [makeCard("b"), makeCard("b", "card-b2")],
      cardsLoading: false
    });
  });

  it("IPC 失败时返回 undefined，且不写入 store", async () => {
    vi.mocked(creationService.cardsList).mockRejectedValue(new Error("工作区不可用"));
    render(<Harness />);

    let result!: CardSummary[] | undefined;
    await act(async () => {
      result = await actions.loadCards({ projectId: "b" });
    });

    expect(result).toBeUndefined();
    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [],
      cardsLoading: false
    });
  });

  it("空列表返回 []（成功加载但项目没有卡片），与失败 undefined 区分", async () => {
    vi.mocked(creationService.cardsList).mockResolvedValue([]);
    render(<Harness />);

    let result: CardSummary[] | undefined | null = null;
    await act(async () => {
      result = await actions.loadCards({ projectId: "b" });
    });

    expect(result).toEqual([]);
    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [],
      cardsLoading: false
    });
  });

  it("请求过期（被更新请求取代）时返回 undefined，不把旧结果当当前结果", async () => {
    const responseA = deferred<CardSummary[]>();
    vi.mocked(creationService.cardsList)
      .mockReturnValueOnce(responseA.promise)
      .mockResolvedValueOnce([makeCard("b")]);
    render(<Harness />);

    let requestA!: Promise<CardSummary[] | undefined>;
    act(() => {
      requestA = actions.loadCards({ projectId: "a" });
    });
    // 新请求取代旧请求
    await act(async () => {
      await actions.loadCards({ projectId: "b" });
    });

    let resultA: CardSummary[] | undefined | null = null;
    await act(async () => {
      responseA.resolve([makeCard("a")]);
      resultA = await requestA;
    });
    // 旧请求已过期：结果 undefined，且不污染 store（store 保持项目 b 的卡片）
    expect(resultA).toBeUndefined();
    expect(useCreationStore.getState()).toMatchObject({
      cardProjectId: "b",
      cards: [makeCard("b")]
    });
  });
});
