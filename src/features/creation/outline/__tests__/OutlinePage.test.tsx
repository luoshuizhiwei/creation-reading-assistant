// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { OutlinePage } from "../OutlinePage";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { makeOutline } from "./fixtures";

afterEach(cleanup);

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: vi.fn()
}));

const project = { id: "p1", title: "P1", setup: { chapterWorkflow: ["草稿", "已完成"] } };

function treeOrder(): string[] {
  return Array.from(document.querySelectorAll(".outline-scene-title")).map((e) => e.textContent ?? "");
}

function boardOrder(): string[] {
  return Array.from(document.querySelectorAll(".card-board-card-title")).map((e) => e.textContent ?? "");
}

function setup(outline = makeOutline()) {
  const runStructure = vi.fn().mockResolvedValue(true);
  const previewStructure = vi.fn().mockImplementation(async ({ command }) => ({
    ok: true,
    planId: "page-plan",
    command,
    rows: [{ label: "权威影响", value: "工作区已核对" }],
    stale: false,
    affectedSceneCount: 1
  }));
  const applyStructureWithProtection = vi.fn().mockResolvedValue({
    ok: true,
    protectionSnapshotId: "page-snapshot",
    affected: [{ type: "chapter", id: "c1", revision: 9 }],
    newRevision: 9
  });
  const revertStructure = vi.fn().mockResolvedValue({ ok: true, restoredRevision: 10 });
  const loadOutline = vi.fn().mockImplementation(async (id: string) => {
    useCreationStore.setState((s) => ({ outlines: { ...s.outlines, [id]: outline } }));
    return outline;
  });
  const loadCards = vi.fn().mockResolvedValue(undefined);
  (useCreationActions as unknown as vi.Mock).mockReturnValue({
    runStructure,
    loadOutline,
    loadCards,
    previewStructure,
    applyStructureWithProtection,
    revertStructure
  });
  useCreationStore.setState({ outlines: {}, selectedSceneId: "", cards: [] });
  render(<OutlinePage project={project} />);
  return { runStructure, loadOutline, previewStructure, applyStructureWithProtection, revertStructure };
}

describe("OutlinePage 树与卡板同源闭环", () => {
  beforeEach(() => {
    useCreationStore.setState({ outlines: {}, selectedSceneId: "", cards: [] });
  });

  it("树与卡板切换后排序一致（共享同一 outline）", async () => {
    setup();
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());
    const tree = treeOrder();
    expect(tree).toEqual(["场景A", "场景B", "场景C", "场景D"]);

    fireEvent.click(screen.getByText("场景卡板"));
    await waitFor(() => expect(screen.getByText("场景A")).toBeTruthy());
    expect(boardOrder()).toEqual(tree);
  });

  it("CardBoard 操作成功后重新读取 outline", async () => {
    const { loadOutline } = setup();
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());
    expect(loadOutline).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText("场景卡板"));
    await waitFor(() => expect(screen.getByText("场景A")).toBeTruthy());

    fireEvent.click(screen.getByLabelText("选择 第一章"));
    fireEvent.change(screen.getByLabelText("批量状态"), { target: { value: "已完成" } });
    fireEvent.click(screen.getByText("应用"));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("确认修改"));

    await waitFor(() => expect(loadOutline).toHaveBeenCalledTimes(2));
  });

  it("保护应用后显示一次性撤回，并传回精确 affected revisions", async () => {
    const { revertStructure } = setup();
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());
    fireEvent.change(screen.getAllByLabelText("移动到卷")[0], { target: { value: "v2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(within(dialog).getByText("确认移动"));

    const undo = await screen.findByRole("button", { name: "撤回本次重组" });
    fireEvent.click(undo);
    await waitFor(() => expect(revertStructure).toHaveBeenCalledWith({
      type: "structure.revert",
      projectId: "p1",
      protectionSnapshotId: "page-snapshot",
      expectedAppliedRevisions: [{ type: "chapter", id: "c1", revision: 9 }]
    }));
    await waitFor(() => expect(screen.queryByRole("button", { name: "撤回本次重组" })).toBeNull());
  });

  it("撤回冲突时保留入口与错误提示", async () => {
    const { revertStructure } = setup();
    revertStructure.mockResolvedValueOnce(null);
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());
    fireEvent.change(screen.getAllByLabelText("移动到卷")[0], { target: { value: "v2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(within(dialog).getByText("确认移动"));
    fireEvent.click(await screen.findByRole("button", { name: "撤回本次重组" }));
    expect(await screen.findByText(/无法撤回/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "撤回本次重组" })).toBeTruthy();
  });
});
