// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { CardBoard } from "../CardBoard";
import { makeOutline } from "./fixtures";

afterEach(cleanup);

const workflow = ["草稿", "已完成"];

function renderBoard(extra: Record<string, unknown> = {}) {
  const onSelectScene = vi.fn();
  const onSceneReorder = vi.fn().mockResolvedValue(true);
  const onSceneMove = vi.fn().mockResolvedValue(true);
  const onChapterSetStatus = vi.fn().mockResolvedValue(true);
  const onChaptersSetStatus = vi.fn().mockResolvedValue(true);
  render(
    <CardBoard
      outline={makeOutline()}
      workflow={workflow}
      onSelectScene={onSelectScene}
      onSceneReorder={onSceneReorder}
      onSceneMove={onSceneMove}
      onChapterSetStatus={onChapterSetStatus}
      onChaptersSetStatus={onChaptersSetStatus}
      selectedChapterIds={new Set(["c1", "c3"])}
      onToggleChapterSelected={vi.fn()}
      {...extra}
    />
  );
  return { onSelectScene, onSceneReorder, onSceneMove, onChapterSetStatus, onChaptersSetStatus };
}

describe("CardBoard 编辑能力", () => {
  it("高风险命令通过权威预览 planId 应用，不调用旧直写回调", async () => {
    const previewStructure = vi.fn().mockImplementation(async ({ command }) => ({
      ok: true,
      planId: "board-plan",
      command,
      rows: [{ label: "工作区影响", value: "1 个场景" }],
      stale: false,
      affectedSceneCount: 1
    }));
    const applyStructureWithProtection = vi.fn().mockResolvedValue({
      ok: true,
      protectionSnapshotId: "snapshot-board",
      affected: [{ type: "scene", id: "s1", revision: 2 }],
      newRevision: 2
    });
    const { onSceneMove } = renderBoard({ previewStructure, applyStructureWithProtection });
    const card = screen.getByText("场景A").closest(".card-board-card")!;
    fireEvent.change(within(card as HTMLElement).getByLabelText("移动到章节"), { target: { value: "c2" } });
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText("工作区影响")).toBeTruthy();
    fireEvent.click(within(dialog).getByText("确认移动"));
    await waitFor(() => expect(applyStructureWithProtection).toHaveBeenCalledWith({
      type: "structure.applyWithProtection",
      projectId: "p1",
      planId: "board-plan",
      protectionReason: "卡板安全重组：sceneMove"
    }));
    expect(onSceneMove).not.toHaveBeenCalled();
  });

  it("批量状态只作用于勾选章节", async () => {
    const { onChaptersSetStatus } = renderBoard();
    expect(screen.getByText(/已选 2 章/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText("批量状态"), { target: { value: "已完成" } });
    fireEvent.click(screen.getByText("应用"));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("确认修改"));
    await waitFor(() => expect(onChaptersSetStatus).toHaveBeenCalled());
    expect(onChaptersSetStatus).toHaveBeenCalledWith(["c1", "c3"], "已完成");
  });

  it("场景跨章移动：取消不调用命令", async () => {
    const { onSceneMove } = renderBoard();
    const card = screen.getByText("场景A").closest(".card-board-card")!;
    fireEvent.change(within(card as HTMLElement).getByLabelText("移动到章节"), { target: { value: "c2" } });
    expect(await screen.findByText("场景跨章移动影响预览")).toBeTruthy();
    fireEvent.click(screen.getByText("取消"));
    expect(onSceneMove).not.toHaveBeenCalled();
  });

  it("场景跨章移动：确认调用 onSceneMove", async () => {
    const { onSceneMove } = renderBoard();
    const card = screen.getByText("场景A").closest(".card-board-card")!;
    fireEvent.change(within(card as HTMLElement).getByLabelText("移动到章节"), { target: { value: "c2" } });
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("确认移动"));
    await waitFor(() => expect(onSceneMove).toHaveBeenCalled());
    expect(onSceneMove).toHaveBeenCalledWith("s1", "c2");
  });

  it("缺省时卡板保持只读（无移章 / 批量控件）", () => {
    render(<CardBoard outline={makeOutline()} workflow={workflow} onSelectScene={vi.fn()} />);
    expect(screen.queryByLabelText("移动到章节")).toBeNull();
    expect(screen.queryByLabelText("批量状态")).toBeNull();
  });

  it("场景下移调用 onSceneReorder（两项列表 → 移到末位 beforeId=undefined）", async () => {
    const { onSceneReorder } = renderBoard();
    const card = screen.getByText("场景A").closest(".card-board-card")!;
    fireEvent.click(within(card as HTMLElement).getByTitle("下移"));
    await waitFor(() => expect(onSceneReorder).toHaveBeenCalled());
    expect(onSceneReorder).toHaveBeenCalledWith("s1", undefined);
  });

  it("场景上移调用 onSceneReorder", async () => {
    const { onSceneReorder } = renderBoard();
    const card = screen.getByText("场景B").closest(".card-board-card")!;
    fireEvent.click(within(card as HTMLElement).getByTitle("上移"));
    await waitFor(() => expect(onSceneReorder).toHaveBeenCalled());
    expect(onSceneReorder).toHaveBeenCalledWith("s2", "s1");
  });

  it("首项上移按钮禁用、末项下移按钮禁用（ReorderTarget 区分）", () => {
    renderBoard();
    const first = screen.getByText("场景A").closest(".card-board-card")!;
    const last = screen.getByText("场景B").closest(".card-board-card")!;
    expect((within(first as HTMLElement).getByTitle("上移") as HTMLButtonElement).disabled).toBe(true);
    expect((within(last as HTMLElement).getByTitle("下移") as HTMLButtonElement).disabled).toBe(true);
  });

  it("三场景列表：倒数第二项下移 → 移到末位 beforeId=undefined", async () => {
    const o = makeOutline();
    o.volumes[0].chapters[0].scenes.push({
      id: "s1b",
      chapterId: "c1",
      title: "场景E",
      sortOrder: 2,
      createdAt: "",
      updatedAt: "",
      revision: 1,
      wordCount: 1,
      planning: {}
    });
    const { onSceneReorder } = renderBoard({ outline: o });
    const card = screen.getByText("场景B").closest(".card-board-card")!; // 倒数第二
    fireEvent.click(within(card as HTMLElement).getByTitle("下移"));
    await waitFor(() => expect(onSceneReorder).toHaveBeenCalled());
    expect(onSceneReorder).toHaveBeenCalledWith("s2", undefined);
  });

  it("批量状态：命令返回 false 时对话框保留并显示错误", async () => {
    const onChaptersSetStatus = vi.fn().mockResolvedValue(false);
    renderBoard({ onChaptersSetStatus });
    fireEvent.change(screen.getByLabelText("批量状态"), { target: { value: "已完成" } });
    fireEvent.click(screen.getByText("应用"));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("确认修改"));
    await waitFor(() => expect(screen.getAllByText(/操作未成功/).length).toBeGreaterThan(0));
    expect(screen.getByRole("dialog")).toBeTruthy();
    expect(onChaptersSetStatus).toHaveBeenCalled();
  });

  it("批量状态：命令抛错时对话框保留并显示错误", async () => {
    const onChaptersSetStatus = vi.fn().mockRejectedValue(new Error("网络异常"));
    renderBoard({ onChaptersSetStatus });
    fireEvent.change(screen.getByLabelText("批量状态"), { target: { value: "已完成" } });
    fireEvent.click(screen.getByText("应用"));
    const dialog = await screen.findByRole("dialog");
    fireEvent.click(within(dialog).getByText("确认修改"));
    await waitFor(() => expect(screen.getAllByText(/网络异常/).length).toBeGreaterThan(0));
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("批量状态：连续点击确认只提交一次", async () => {
    const onChaptersSetStatus = vi.fn().mockResolvedValue(true);
    renderBoard({ onChaptersSetStatus });
    fireEvent.change(screen.getByLabelText("批量状态"), { target: { value: "已完成" } });
    fireEvent.click(screen.getByText("应用"));
    const dialog = await screen.findByRole("dialog");
    const confirm = within(dialog).getByText("确认修改");
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    await waitFor(() => expect(onChaptersSetStatus).toHaveBeenCalledTimes(1));
  });
});
