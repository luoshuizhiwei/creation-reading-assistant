// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardSummary, CardType, CreationProjectSummary } from "@/types/creation";

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

function makeProject(id: string, title: string): CreationProjectSummary {
  return {
    id,
    title,
    setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
    updatedAt: "",
    revision: 1,
    chapterCount: 1,
    sceneCount: 1
  };
}

function makeCard(projectId: string, id: string, kind: string, title: string): CardSummary {
  return { id, projectId, kind, title, aliases: [], fields: {}, tags: [], createdAt: "", updatedAt: "", revision: 1 };
}

function makeType(id: string, kind: string, name: string, fields: CardType["fields"]): CardType {
  return { id, projectId: null, kind, name, fields, sortOrder: 0, createdAt: "", updatedAt: "", revision: 1 };
}

const p1 = makeProject("p1", "项目A");
const attachmentType = makeType("t-char", "character", "角色", [
  { key: "portrait", label: "配图", kind: "attachment" }
]);
const aCard = makeCard("p1", "card-a", "character", "卡片 A");

function resetStores(): void {
  useCreationStore.setState({
    projects: [p1],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: "p1",
    cardTypes: [attachmentType],
    relationTypes: [],
    cards: [aCard],
    cardRelations: { "card-a": { outgoing: [], incoming: [] } },
    selectedCardId: "card-a",
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
  vi.mocked(creationService.cardTypesList).mockReset();
  vi.mocked(creationService.relationTypesList).mockReset();
  vi.mocked(creationService.cardRelations).mockReset();
  vi.mocked(creationService.resourceList).mockReset();
  vi.mocked(creationService.watchProject).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.cardsList).mockResolvedValue([aCard]);
  vi.mocked(creationService.cardTypesList).mockResolvedValue([attachmentType]);
  vi.mocked(creationService.relationTypesList).mockResolvedValue([]);
  vi.mocked(creationService.cardRelations).mockResolvedValue({ outgoing: [], incoming: [] });
  vi.mocked(creationService.resourceList).mockResolvedValue([]);
  vi.mocked(creationService.watchProject).mockResolvedValue(() => {});
  vi.mocked(creationService.runStructure).mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("附件字段不写入绝对路径", () => {
  it("attachment 字段不再显示「后续接入」占位，改为资源附件说明", async () => {
    render(<CardsPage project={p1} />);
    // 进入编辑态，触发 FieldEditor 渲染
    fireEvent.click(await screen.findByTitle("编辑"));

    expect(screen.queryByText(/后续接入/)).toBeNull();
    expect(screen.getByText(/附件请在卡片「附件」区添加/)).toBeDefined();
  });

  it("附件字段不渲染可写入绝对路径的输入框", async () => {
    const { container } = render(<CardsPage project={p1} />);
    fireEvent.click(await screen.findByTitle("编辑"));

    const inputs = Array.from(container.querySelectorAll(".cards-form input")) as HTMLInputElement[];
    const suspicious = inputs.filter((input) => /[\\/]|[A-Za-z]:\\/.test(input.value));
    expect(suspicious).toHaveLength(0);
  });
});
