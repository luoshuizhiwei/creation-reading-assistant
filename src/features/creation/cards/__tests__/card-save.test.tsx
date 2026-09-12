// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardType, CreationProjectSummary } from "@/types/creation";

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

const magicType: CardType = {
  id: "t-magic",
  projectId: null,
  builtIn: true,
  kind: "magic",
  name: "功法",
  fields: [
    { key: "name", label: "功法名", kind: "text", required: true },
    { key: "level", label: "等级", kind: "number", defaultValue: 1 }
  ],
  sortOrder: 0,
  createdAt: "",
  updatedAt: "",
  revision: 1
};

const p1 = makeProject("p1", "项目A");

function resetStores(): void {
  useCreationStore.setState({
    projects: [p1],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: "p1",
    cardTypes: [magicType],
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
  vi.mocked(creationService.cardTypesList).mockReset();
  vi.mocked(creationService.relationTypesList).mockReset();
  vi.mocked(creationService.cardRelations).mockReset();
  vi.mocked(creationService.resourceList).mockReset();
  vi.mocked(creationService.watchProject).mockReset();
  vi.mocked(creationService.runStructure).mockReset();
  vi.mocked(creationService.cardsList).mockResolvedValue([]);
  vi.mocked(creationService.cardTypesList).mockResolvedValue([magicType]);
  vi.mocked(creationService.relationTypesList).mockResolvedValue([]);
  vi.mocked(creationService.cardRelations).mockResolvedValue({ outgoing: [], incoming: [] });
  vi.mocked(creationService.resourceList).mockResolvedValue([]);
  vi.mocked(creationService.watchProject).mockResolvedValue(() => {});
  vi.mocked(creationService.runStructure).mockResolvedValue(true);
});

afterEach(() => cleanup());

describe("新建卡片的必填与默认值", () => {
  function openNewMagicDraft(container: HTMLElement): void {
    const columns = container.querySelectorAll(".cards-board-column");
    let target: HTMLElement | null = null;
    columns.forEach((column) => {
      if (column.textContent?.includes("功法")) target = column as HTMLElement;
    });
    const button = target?.querySelector('button[title="在此类型下新建卡片"]') as HTMLButtonElement | undefined;
    fireEvent.click(button as HTMLButtonElement);
  }

  it("必填字段没有值时卡片不能保存", async () => {
    const { container } = render(<CardsPage project={p1} />);
    await waitFor(() => expect(screen.getByText("功法")).toBeDefined());
    openNewMagicDraft(container);

    // 填写标题，但必填的「功法名」留空
    const formInputs = container.querySelectorAll(".cards-form input") as NodeListOf<HTMLInputElement>;
    fireEvent.change(formInputs[0], { target: { value: "新功法" } });

    fireEvent.click(screen.getByText("创建卡片"));
    await waitFor(() =>
      expect(useUIStore.getState().toasts.some((toast) => toast.title === "必填字段未完成")).toBe(true)
    );
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("默认值按 schema 填入新卡片", async () => {
    const { container } = render(<CardsPage project={p1} />);
    await waitFor(() => expect(screen.getByText("功法")).toBeDefined());
    openNewMagicDraft(container);

    const formInputs = container.querySelectorAll(".cards-form input") as NodeListOf<HTMLInputElement>;
    fireEvent.change(formInputs[0], { target: { value: "新功法" } });
    // 必填字段「功法名」在 inputs[2]（title, aliases, name, level, tags）
    fireEvent.change(formInputs[2], { target: { value: "九阳" } });

    fireEvent.click(screen.getByText("创建卡片"));

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledTimes(1));
    const command = vi.mocked(creationService.runStructure).mock.calls[0][0] as Record<string, unknown>;
    expect(command.type).toBe("card.create");
    const fields = command.fields as Record<string, unknown>;
    expect(fields.name).toBe("九阳");
    expect(fields.level).toBe(1);
  });
});
