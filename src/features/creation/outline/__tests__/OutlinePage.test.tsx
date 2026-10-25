// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { OutlinePage } from "../OutlinePage";
import { useCreationActions } from "@/hooks/useCreationActions";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
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
  const loadScene = vi.fn().mockResolvedValue(undefined);
  const exportDraft = vi.fn().mockResolvedValue({ canceled: false, filePath: "D:\\exports\\outline.md" });
  (useCreationActions as unknown as vi.Mock).mockReturnValue({
    runStructure,
    loadOutline,
    loadCards,
    previewStructure,
    applyStructureWithProtection,
    revertStructure,
    loadScene,
    exportDraft
  });
  useUIStore.setState({ toasts: [], showToast: vi.fn() });
  useCreationStore.setState({ outlines: {}, selectedSceneId: "", cards: [] });
  render(<OutlinePage project={project} />);
  return { runStructure, loadOutline, previewStructure, applyStructureWithProtection, revertStructure, loadScene, exportDraft };
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
    fireEvent.change(screen.getAllByLabelText(/移动到卷/)[0], { target: { value: "v2" } });
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
    fireEvent.change(screen.getAllByLabelText(/移动到卷/)[0], { target: { value: "v2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(within(dialog).getByText("确认移动"));
    fireEvent.click(await screen.findByRole("button", { name: "撤回本次重组" }));
    expect(await screen.findByText(/无法撤回/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "撤回本次重组" })).toBeTruthy();
  });

  it("场景摘要和场景状态通过独立 scene.updateMeta 保存，不改写章节状态", async () => {
    const outline = makeOutline();
    outline.volumes[0]!.chapters[0]!.scenes[0]!.summary = "旧摘要";
    outline.volumes[0]!.chapters[0]!.scenes[0]!.status = "planned";
    const { runStructure, loadScene } = setup(outline);
    await waitFor(() => expect(screen.getByText("场景A", { selector: ".outline-scene-title" })).toBeTruthy());
    fireEvent.click(screen.getByText("场景A", { selector: ".outline-scene-title" }).closest("button")!);
    const summary = await screen.findByPlaceholderText("这一场发生什么、推动了什么变化？");
    fireEvent.change(summary, { target: { value: "新摘要" } });
    fireEvent.change(screen.getByText("场景状态").parentElement!.querySelector("select")!, { target: { value: "drafting" } });
    fireEvent.click(screen.getByRole("button", { name: /保存场景卡/ }));
    await waitFor(() => expect(runStructure).toHaveBeenCalledWith({
      type: "scene.updateMeta",
      sceneId: "s1",
      baseRevision: 1,
      summary: "新摘要",
      status: "drafting"
    }));
    expect(runStructure).not.toHaveBeenCalledWith(expect.objectContaining({ type: "chapter.setStatus" }));
    expect(loadScene).toHaveBeenCalledWith("s1");
  });

  it("大纲页可直接导出 Markdown 大纲", async () => {
    const { exportDraft } = setup();
    const button = await screen.findByRole("button", { name: "导出 Markdown 大纲" });
    fireEvent.click(button);
    await waitFor(() => expect(exportDraft).toHaveBeenCalledWith("p1", "outline-markdown"));
  });

  // BD-191：撤回横幅此前整块（连 role="status"）在 apply 落定后才挂载——「插入即带
  // 内容」的 polite 区是哑的。播报交给页面级常驻区，可见横幅退回纯视觉。
  it("重组落定由挂载前就存在的常驻区播报，撤回横幅不再自带 status", async () => {
    setup();
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());
    const region = document.querySelector('span.sr-only[role="status"]') as HTMLSpanElement;
    expect(region, "播报区必须在结果出现前就存在").toBeTruthy();
    expect(region.textContent).toBe("");

    fireEvent.change(screen.getAllByLabelText(/移动到卷/)[0], { target: { value: "v2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(within(dialog).getByText("确认移动"));
    await screen.findByRole("button", { name: "撤回本次重组" });

    expect(document.querySelector('span.sr-only[role="status"]'), "回退：区域随横幅重挂载").toBe(region);
    expect(region.textContent).toBe("大纲安全重组已应用，并已创建保护快照。");
    const bar = document.querySelector(".outline-revert-bar") as HTMLElement;
    expect(bar, "可见横幅（含撤回按钮）仍要在界面上").toBeTruthy();
    expect(bar.getAttribute("role"), "回退：横幅又整体挂回 role=status——落定即插入，播不出来").toBeNull();
  });
});

/**
 * BD-192：页面级「正在读取大纲…」此前挂在 {!outline && <p role="status">} 上——
 * 每次重载都是连内容一起插入的新节点，polite 播报是哑的（BD-175/180/187 判据）。
 * 播报交给常驻区换文本，可见 p 退回纯视觉。
 */
describe("OutlinePage 读大纲忙态常驻播报（BD-192）", () => {
  it("大纲落定前常驻区已带忙态，落定后同一节点清空、可见 p 不带 status", async () => {
    let resolveLoad: ((o: unknown) => void) | null = null;
    (useCreationActions as unknown as vi.Mock).mockReturnValue({
      runStructure: vi.fn().mockResolvedValue(true),
      loadOutline: vi.fn().mockImplementation(async (id: string) => {
        const o = await new Promise((r) => {
          resolveLoad = r;
        });
        useCreationStore.setState((s) => ({ outlines: { ...s.outlines, [id]: o } }));
        return o;
      }),
      loadCards: vi.fn().mockResolvedValue(undefined),
      previewStructure: vi.fn(),
      applyStructureWithProtection: vi.fn(),
      revertStructure: vi.fn(),
      loadScene: vi.fn().mockResolvedValue(undefined),
      exportDraft: vi.fn()
    });
    useUIStore.setState({ toasts: [], showToast: vi.fn() });
    useCreationStore.setState({ outlines: {}, selectedSceneId: "", cards: [] });
    render(<OutlinePage project={project} />);

    const busy = Array.from(document.querySelectorAll('span.sr-only[role="status"]')).find(
      (el) => el.textContent === "正在读取大纲…"
    );
    expect(busy, "回退：忙态又回到条件挂载的 p 上，常驻区没有接手").toBeTruthy();

    const visible = document.querySelector("p.outline-loading") as HTMLElement;
    expect(visible, "可见读数仍要在界面上").toBeTruthy();
    expect(visible.getAttribute("role"), "回退：可见 p 又自带 status，插入即哑").toBeNull();

    await act(async () => {
      resolveLoad?.(makeOutline());
    });
    await waitFor(() => expect(screen.getByText("卷一", { selector: ".outline-volume-title" })).toBeTruthy());

    expect(
      Array.from(document.querySelectorAll('span.sr-only[role="status"]')),
      "回退：区域随忙态结束被卸载"
    ).toContain(busy);
    expect(busy!.textContent).toBe("");
    expect(document.querySelector("p.outline-loading")).toBeNull();
  });
});
