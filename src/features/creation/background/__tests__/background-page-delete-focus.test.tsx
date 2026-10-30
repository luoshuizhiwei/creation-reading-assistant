// @vitest-environment jsdom
/**
 * BD-221（BD-219/220 同族）：背景卡删除后的焦点交接。
 *
 * 确认删除后详情面板与卡片一起换血，焦点跌回 body（WCAG 2.4.3）。交接判据：
 * 原位置下一张卡片优先，末尾退前一张；删空退空态文案。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

const loc = makeCard("p1", "loc-1", "location", "黄沙镇");
const rule = makeCard("p1", "rule-1", "worldRule", "灵气复苏");

function resetStores(cards: CardSummary[]): void {
  useCreationStore.setState({
    cards,
    selectedCardId: loc.id,
    cardProjectId: "p1",
    loading: false
  } as never);
  useUIStore.setState({ toasts: [] });
}

beforeEach(() => {
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.runStructure).mockResolvedValue({ commandType: "card.delete" } as never);
  vi.mocked(creationService.cardsList).mockReset();
});

afterEach(() => cleanup());

async function confirmDelete(cardName: RegExp) {
  fireEvent.click(screen.getByRole("button", { name: cardName }));
  const del = await screen.findByRole("button", { name: "删除" });
  del.focus();
  fireEvent.click(del);
  fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
}

describe("BD-221：背景卡删除后的焦点交接", () => {
  it("删中间卡片：焦点交给原位置下一张卡片", async () => {
    resetStores([loc, rule]);
    vi.mocked(creationService.cardsList)
      .mockResolvedValueOnce([loc, rule] as never)
      .mockResolvedValue([rule] as never);
    render(<BackgroundPage projectId="p1" />);

    await confirmDelete(/黄沙镇/);
    await waitFor(() => expect(screen.queryAllByText("黄沙镇")).toHaveLength(0));

    await waitFor(() => expect(document.activeElement).toBe(screen.getByRole("button", { name: /灵气复苏/ })));
  });

  it("删空：焦点退到空态文案", async () => {
    resetStores([loc]);
    vi.mocked(creationService.cardsList)
      .mockResolvedValueOnce([loc] as never)
      .mockResolvedValue([] as never);
    render(<BackgroundPage projectId="p1" />);

    await confirmDelete(/黄沙镇/);
    await waitFor(() => expect(screen.queryAllByText("黄沙镇")).toHaveLength(0));

    await waitFor(() => {
      // 判据落在元素本身：body 的 textContent 也含这句，只读文本会把「跌回 body」误判成 PASS。
      expect(document.activeElement?.classList.contains("cards-relations-empty")).toBe(true);
      expect((document.activeElement?.textContent ?? "")).toContain("还没有背景设定卡");
    });
  });
});
