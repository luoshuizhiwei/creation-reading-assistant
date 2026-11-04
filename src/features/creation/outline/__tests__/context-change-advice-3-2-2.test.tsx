// @vitest-environment jsdom
/**
 * BD-246（WCAG 3.2.2 On Input）·「改值就弹层」这一族必须事先说明白
 *
 * 运行时普查（scripts/audit-context-change.mjs）量的是「真按一下方向键，上下文会不会被
 * 搬走、那句说明在不在屏上真渲染」；这本账守的是形状与因果：
 *   ① 说明必须挂在那几颗**真的会弹层**的 select 上，且指到同一段可见提示；
 *   ② 说明不许吃掉安全确认——改了编号、移了章仍然要先弹影响预览，不确认就一个写命令都不发。
 * 两条都要求非空命中：找不到形状就红，不然账本自己变哑牙。
 */
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { CardBoard } from "../CardBoard";
import { OutlineTree } from "../OutlineTree";
import { makeOutline } from "./fixtures";

afterEach(cleanup);

const workflow = ["草稿", "已完成"];

function renderTree() {
  const runStructure = vi.fn().mockResolvedValue(true);
  const previewStructure = vi.fn().mockImplementation(async ({ command }) => ({
    ok: true,
    planId: "plan-authoritative",
    command,
    rows: [{ label: "权威影响", value: "由工作区计算" }],
    stale: false,
    affectedSceneCount: 1
  }));
  const applyStructureWithProtection = vi.fn().mockResolvedValue({
    ok: true,
    protectionSnapshotId: "snapshot-1",
    affected: [{ type: "chapter", id: "c1", revision: 8 }],
    newRevision: 8
  });
  render(
    <OutlineTree
      outline={makeOutline()}
      workflow={workflow}
      selectedSceneId={undefined}
      onSelectScene={vi.fn()}
      runStructure={runStructure}
      previewStructure={previewStructure}
      applyStructureWithProtection={applyStructureWithProtection}
    />
  );
  return { runStructure, previewStructure, applyStructureWithProtection };
}

function renderBoard() {
  const onSceneMove = vi.fn().mockResolvedValue(true);
  render(
    <CardBoard
      outline={makeOutline()}
      workflow={workflow}
      onSelectScene={vi.fn()}
      onSceneReorder={vi.fn().mockResolvedValue(true)}
      onSceneMove={onSceneMove}
      onChapterSetStatus={vi.fn().mockResolvedValue(true)}
      onChaptersSetStatus={vi.fn().mockResolvedValue(true)}
      selectedChapterIds={new Set(["c1", "c3"])}
      onToggleChapterSelected={vi.fn()}
    />
  );
  return { onSceneMove };
}

describe("BD-246 大纲树：会弹影响预览的下拉框，动之前就说明白", () => {
  it("三颗 select（移卷 / 移章 / 编号模式）都 aria-describedby 指向同一段可见提示", () => {
    renderTree();
    const advice = screen.getByText(/会先弹影响预览/);
    expect(advice.id, "提示没有 id，select 无从 aria-describedby").toBeTruthy();
    // 那句话必须把后果说全：弹的是预览、要确认才写入
    expect(advice.textContent).toContain("预览");
    expect(advice.textContent).toContain("确认");
    expect(advice.textContent).toContain("写入");

    // 三颗都必须真在场（少一颗就是这一族被拆走了一颗，测试要红，不许默默少测）
    expect(screen.getAllByLabelText(/移动到卷/).length, "双卷 fixture 里该有可移卷的下拉框").toBeGreaterThan(0);
    expect(screen.getAllByLabelText(/移动到章节/).length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText(/编号模式/).length).toBeGreaterThan(0);
    const selects = [
      screen.getAllByLabelText(/移动到卷/)[0],
      screen.getAllByLabelText(/移动到章节/)[0],
      screen.getAllByLabelText(/编号模式/)[0]
    ];
    for (const sel of selects) {
      expect(sel.getAttribute("aria-describedby"), `<select aria-label="${sel.getAttribute("aria-label")}"> 没挂事先说明`).toBe(advice.id);
      expect(document.getElementById(sel.getAttribute("aria-describedby") || "")).toBeTruthy();
    }
  });

  it("说明不吃掉安全确认：改编号仍先弹影响预览，取消就一个写命令都不发", async () => {
    const { runStructure, applyStructureWithProtection } = renderTree();
    const numbering = screen.getAllByLabelText(/编号模式/)[0] as HTMLSelectElement;
    fireEvent.change(numbering, { target: { value: "prologue" } });
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("章节编号变更影响预览")).toBeTruthy();
    fireEvent.click(within(dialog).getByText("取消"));
    await waitFor(() => expect(document.querySelector('[role="dialog"][aria-modal="true"]')).toBe(null));
    expect(runStructure).not.toHaveBeenCalled();
    expect(applyStructureWithProtection).not.toHaveBeenCalled();
  });

  it("说明只有一段：提示的 id 在整棵树里唯一，不会给每行复制出一条重复的 id", () => {
    renderTree();
    const advice = screen.getByText(/会先弹影响预览/);
    expect(document.querySelectorAll(`[id="${advice.id}"]`).length).toBe(1);
  });
});

describe("BD-246 场景卡板：移章下拉框同族同修", () => {
  it("卡板那颗「移动到章节」挂着可见的事先说明", () => {
    renderBoard();
    const advice = screen.getByText(/移章会先弹影响预览/);
    expect(advice.id).toBeTruthy();
    expect(advice.textContent).toContain("确认");
    const card = screen.getByText("场景A").closest(".card-board-card") as HTMLElement;
    const move = within(card).getByLabelText(/移动到章节/) as HTMLSelectElement;
    expect(move.getAttribute("aria-describedby")).toBe(advice.id);
    expect(document.querySelectorAll(`[id="${advice.id}"]`).length).toBe(1);
  });

  it("说明挂着，弹层也照常挂着：移章仍然先预览、确认才写入", async () => {
    const onSceneMove = vi.fn().mockResolvedValue(true);
    render(
      <CardBoard
        outline={makeOutline()}
        workflow={workflow}
        onSelectScene={vi.fn()}
        onSceneReorder={vi.fn().mockResolvedValue(true)}
        onSceneMove={onSceneMove}
        onChapterSetStatus={vi.fn().mockResolvedValue(true)}
        onChaptersSetStatus={vi.fn().mockResolvedValue(true)}
        selectedChapterIds={new Set(["c1"])}
        onToggleChapterSelected={vi.fn()}
      />
    );
    const card = screen.getByText("场景A").closest(".card-board-card") as HTMLElement;
    fireEvent.change(within(card).getByLabelText(/移动到章节/), { target: { value: "c2" } });
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("场景跨章移动影响预览")).toBeTruthy();
    fireEvent.click(within(dialog).getByText("取消"));
    await waitFor(() => expect(document.querySelector('[role="dialog"][aria-modal="true"]')).toBe(null));
    expect(onSceneMove).not.toHaveBeenCalled();
  });
});
