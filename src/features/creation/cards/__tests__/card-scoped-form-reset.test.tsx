// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { CardsPage } from "@/features/creation/cards/CardsPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";
import type { CardSummary, CardType, CreationProjectSummary, ResourceInfo } from "@/types/creation";

// BD-185：卡片绑定的编辑/确认态必须随选中卡片复位。
// 里程碑表单内联非模态（开着时左侧列表照样能点），而 createMilestone 的靶子是
// selectedCard.id —— 改前给 A 填好说明再点到 B，保存就把 A 的说明写成 B 的里程碑。
// 附件的两步确认同理：第一击留着，换卡再换回来，按钮还停在「确认移除」上。

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
  cardLink: vi.fn(),
  cardUnlink: vi.fn(),
  cardTypesList: vi.fn(),
  relationTypesList: vi.fn(),
  cardRelations: vi.fn(),
  relationGraph: vi.fn(),
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

function makeCard(id: string, title: string): CardSummary {
  return {
    id,
    projectId: "p1",
    kind: "character",
    title,
    aliases: [],
    fields: {},
    tags: [],
    createdAt: "",
    updatedAt: "",
    revision: 1
  };
}

function makeResource(id: string, name: string): ResourceInfo {
  return {
    id,
    projectId: "p1",
    cardId: null,
    ownerScope: "project",
    relativePath: `resources/${id}`,
    sha256: `${id}${"0".repeat(56)}`.slice(0, 64),
    size: 2048,
    originalName: name,
    createdAt: ""
  };
}

const p1 = makeProject("p1", "项目A");
const cardA = makeCard("card-a", "卡片 A");
const cardB = makeCard("card-b", "卡片 B");
const charType: CardType = {
  id: "t-char",
  projectId: null,
  builtIn: true,
  kind: "character",
  name: "角色",
  fields: [],
  sortOrder: 0,
  createdAt: "",
  updatedAt: "",
  revision: 1
};
const resourceA = makeResource("res-a", "A 的附件.png");

beforeEach(() => {
  useCreationStore.setState({
    projects: [p1],
    selectedId: "p1",
    navigations: {},
    outlines: {},
    cardProjectId: "p1",
    cardTypes: [charType],
    relationTypes: [],
    cards: [cardA, cardB],
    cardRelations: { "card-a": { outgoing: [], incoming: [] }, "card-b": { outgoing: [], incoming: [] } },
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

  vi.mocked(creationService.cardsList).mockReset().mockResolvedValue([cardA, cardB]);
  vi.mocked(creationService.cardTypesList).mockReset().mockResolvedValue([charType]);
  vi.mocked(creationService.relationTypesList).mockReset().mockResolvedValue([]);
  vi.mocked(creationService.cardRelations).mockReset().mockResolvedValue({ outgoing: [], incoming: [] });
  vi.mocked(creationService.watchProject).mockReset().mockResolvedValue(() => {});
  vi.mocked(creationService.runStructure).mockReset().mockResolvedValue(true);
  vi.mocked(creationService.detachResource).mockReset().mockResolvedValue(true as never);
  vi.mocked(creationService.resourceList)
    .mockReset()
    .mockImplementation(async (query) => (query?.cardId === "card-a" ? [resourceA] : []));
});

afterEach(() => cleanup());

function selectCard(title: "卡片 A" | "卡片 B") {
  fireEvent.click(screen.getByRole("button", { name: new RegExp(title) }));
}

describe("卡片页 · 编辑/确认态随选中卡片复位", () => {
  it("给 A 填好里程碑说明后切到 B，表单关掉、说明清空，不会把 A 的说明写成 B 的里程碑", async () => {
    render(<CardsPage project={p1} />);

    const trigger = await screen.findByRole("button", { name: "创建命名里程碑" });
    fireEvent.click(trigger);
    fireEvent.change(screen.getByRole("textbox", { name: "里程碑说明" }), {
      target: { value: "角色设定定稿 v1" }
    });

    selectCard("卡片 B");

    expect(screen.queryByRole("textbox", { name: "里程碑说明" })).toBeNull();
    expect(
      screen.getAllByRole("button", { name: "创建命名里程碑" })[0].getAttribute("aria-expanded")
    ).toBe("false");
    expect(creationService.runStructure).not.toHaveBeenCalled();
  });

  it("切到 B 后重开里程碑表单，是空白说明而不是 A 残留的草稿", async () => {
    render(<CardsPage project={p1} />);

    const trigger = await screen.findByRole("button", { name: "创建命名里程碑" });
    fireEvent.click(trigger);
    fireEvent.change(screen.getByRole("textbox", { name: "里程碑说明" }), {
      target: { value: "角色设定定稿 v1" }
    });

    selectCard("卡片 B");
    fireEvent.click(screen.getByRole("button", { name: "创建命名里程碑" }));

    expect((screen.getByRole("textbox", { name: "里程碑说明" }) as HTMLInputElement).value).toBe("");

    fireEvent.change(screen.getByRole("textbox", { name: "里程碑说明" }), { target: { value: "B 的定稿" } });
    fireEvent.click(screen.getByText("保存里程碑"));

    await waitFor(() => expect(creationService.runStructure).toHaveBeenCalledTimes(1));
    const command = vi.mocked(creationService.runStructure).mock.calls[0][0] as Record<string, unknown>;
    expect(command.subjectId).toBe("card-b");
    expect(command.reason).toBe("B 的定稿");
  });

  it("附件的两步确认不跨卡片残留：换卡再回来，按钮退回「移除」", async () => {
    render(<CardsPage project={p1} />);

    const remove = await screen.findByRole("button", { name: /移除/ });
    fireEvent.click(remove);
    expect(await screen.findByRole("button", { name: /确认移除/ })).toBeTruthy();

    selectCard("卡片 B");
    selectCard("卡片 A");

    const again = await screen.findByRole("button", { name: /移除/ });
    expect(again.textContent).toContain("移除");
    expect(screen.queryByRole("button", { name: /确认移除/ })).toBeNull();

    fireEvent.click(again);
    expect(creationService.detachResource).not.toHaveBeenCalled();
  });
});
