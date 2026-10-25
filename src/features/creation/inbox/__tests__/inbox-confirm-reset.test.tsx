// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { InboxPage } from "@/features/creation/inbox/InboxPage";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import * as creationService from "@/services/creation-service";

// BD-186：「移出」的第一步只把该行按钮送进「确认移出」态。之后换选中条目或切
// 状态筛选，这一行的上下文已经变了，确认态必须作废——否则在新上下文里再点
// 一下就是直接软删除。

vi.mock("@/services/creation-service", () => ({
  inboxList: vi.fn(),
  inboxCount: vi.fn(),
  inboxUpdate: vi.fn(),
  inboxDelete: vi.fn(),
  inboxCreate: vi.fn(),
  cardsList: vi.fn(),
  runStructure: vi.fn(),
  listProjects: vi.fn(),
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

const projects = [
  { id: "p1", title: "项目A", setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] }, updatedAt: "", revision: 1, chapterCount: 1, sceneCount: 1 }
];

function makeItem(id: string, title: string, status = "inbox") {
  return {
    id, legacyId: null, title, body: `${title}的内容`, type: "note", status,
    tags: [], platformTags: [], source: null, variants: [], revision: 1,
    createdAt: "", updatedAt: ""
  };
}

function rowOf(title: string): HTMLElement {
  return screen.getByText(title).closest("li")!;
}

beforeEach(() => {
  useCreationStore.setState({ projects, cards: [], selectedCardId: undefined });
  useUIStore.setState({ toasts: [] });
  vi.mocked(creationService.inboxList).mockReset();
  vi.mocked(creationService.inboxList).mockResolvedValue([
    makeItem("i1", "灵感一"),
    makeItem("i2", "灵感二"),
    makeItem("i3", "归档件", "archived")
  ] as never);
  vi.mocked(creationService.inboxCount).mockResolvedValue({ total: 3, pending: 2 });
  vi.mocked(creationService.inboxDelete).mockReset();
  vi.mocked(creationService.inboxDelete).mockResolvedValue(true);
  vi.mocked(creationService.runStructure).mockReset();
});

afterEach(() => cleanup());

describe("收件箱 · 上下文切换作废两步确认（BD-186）", () => {
  it("换选中条目后，旧行的「确认移出」退回一步态", async () => {
    render(<InboxPage projectId="p1" />);
    await screen.findByText("灵感一");

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "移出" }));
    expect(within(rowOf("灵感一")).getByRole("button", { name: "确认移出" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "打开想法：灵感二" }));

    expect(screen.queryByRole("button", { name: "确认移出" })).toBeNull();
    expect(within(rowOf("灵感一")).getByRole("button", { name: "移出" })).toBeTruthy();
  });

  it("作废后第一次点击只是重新武装，第二次才真的移出", async () => {
    render(<InboxPage projectId="p1" />);
    await screen.findByText("灵感一");

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "移出" }));
    fireEvent.click(screen.getByRole("button", { name: "打开想法：灵感二" }));

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "移出" }));
    expect(creationService.inboxDelete).not.toHaveBeenCalled();
    expect(within(rowOf("灵感一")).getByRole("button", { name: "确认移出" })).toBeTruthy();

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "确认移出" }));
    await waitFor(() =>
      expect(creationService.inboxDelete).toHaveBeenCalledWith({ type: "inbox.delete", itemId: "i1" })
    );
  });

  it("切状态筛选再切回，确认态同样作废", async () => {
    render(<InboxPage projectId="p1" />);
    await screen.findByText("灵感一");

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "移出" }));
    expect(within(rowOf("灵感一")).getByRole("button", { name: "确认移出" })).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: /^归档/ }));
    fireEvent.click(screen.getByRole("button", { name: /^全部/ }));
    await screen.findByText("灵感一");

    expect(screen.queryByRole("button", { name: "确认移出" })).toBeNull();
    expect(within(rowOf("灵感一")).getByRole("button", { name: "移出" })).toBeTruthy();
  });

  it("不切上下文时两步确认保持原样：一次点击只武装、不删除", async () => {
    render(<InboxPage projectId="p1" />);
    await screen.findByText("灵感一");

    fireEvent.click(within(rowOf("灵感一")).getByRole("button", { name: "移出" }));

    expect(creationService.inboxDelete).not.toHaveBeenCalled();
    expect(within(rowOf("灵感一")).getByRole("button", { name: "确认移出" })).toBeTruthy();
  });
});
