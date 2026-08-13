// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { OutlineTree } from "../OutlineTree";
import { makeOutline } from "./fixtures";

afterEach(cleanup);

const workflow = ["草稿", "已完成"];

function renderTree(
  outline = makeOutline(),
  runStructure = vi.fn().mockResolvedValue(true),
  extra: Record<string, unknown> = {}
) {
  const onSelectScene = vi.fn();
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
      outline={outline}
      workflow={workflow}
      selectedSceneId={undefined}
      onSelectScene={onSelectScene}
      runStructure={runStructure}
      previewStructure={previewStructure}
      applyStructureWithProtection={applyStructureWithProtection}
      {...extra}
    />
  );
  return { runStructure, onSelectScene, previewStructure, applyStructureWithProtection };
}

describe("OutlineTree 改名与影响预览", () => {
  it("跨卷移动先展示工作区权威预览，确认时只提交 planId", async () => {
    const runStructure = vi.fn().mockResolvedValue(true);
    const { previewStructure, applyStructureWithProtection } = renderTree(makeOutline(), runStructure);

    fireEvent.change(screen.getAllByLabelText("移动到卷")[0], { target: { value: "v2" } });

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("权威影响")).toBeTruthy();
    expect(within(dialog).getByText("由工作区计算")).toBeTruthy();
    expect(previewStructure).toHaveBeenCalledWith({
      type: "structure.preview",
      projectId: "p1",
      command: { type: "chapter.move", chapterId: "c1", targetVolumeId: "v2" }
    });

    fireEvent.click(within(dialog).getByText("确认移动"));
    await waitFor(() => expect(applyStructureWithProtection).toHaveBeenCalled());
    expect(applyStructureWithProtection).toHaveBeenCalledWith({
      type: "structure.applyWithProtection",
      projectId: "p1",
      planId: "plan-authoritative",
      protectionReason: "大纲安全重组：chapterMove"
    });
    expect(runStructure).not.toHaveBeenCalled();
  });

  it("场景跨章移动也必须先权威预览再按 planId 应用", async () => {
    const { previewStructure, applyStructureWithProtection } = renderTree();
    const scene = screen.getByText("场景A").closest(".outline-scene")!;
    fireEvent.change(within(scene as HTMLElement).getByLabelText("移动到章节"), { target: { value: "c2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    expect(previewStructure).toHaveBeenCalledWith({
      type: "structure.preview",
      projectId: "p1",
      command: { type: "scene.move", sceneId: "s1", targetChapterId: "c2" }
    });
    fireEvent.click(within(dialog).getByText("确认移动"));
    await waitFor(() => expect(applyStructureWithProtection).toHaveBeenCalledWith(expect.objectContaining({ planId: "plan-authoritative" })));
  });

  it("章节编号变更也必须先权威预览再应用", async () => {
    const { previewStructure, applyStructureWithProtection } = renderTree();
    fireEvent.change(screen.getAllByLabelText("章节编号模式")[0], { target: { value: "prologue" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认修改") as HTMLButtonElement).disabled).toBe(false));
    expect(previewStructure).toHaveBeenCalledWith({
      type: "structure.preview",
      projectId: "p1",
      command: {
        type: "chapter.setNumbering",
        chapterId: "c1",
        numbering: "prologue",
        baseRevision: 1
      }
    });
    fireEvent.click(within(dialog).getByText("确认修改"));
    await waitFor(() => expect(applyStructureWithProtection).toHaveBeenCalledWith(expect.objectContaining({ planId: "plan-authoritative" })));
  });

  it("卷改名提交对象当前真实 revision（非常量 1）", async () => {
    const o = makeOutline();
    o.volumes[0].revision = 5;
    const { runStructure } = renderTree(o);
    fireEvent.click(screen.getAllByTitle("改名")[0]);
    const input = screen.getByDisplayValue("卷一");
    fireEvent.change(input, { target: { value: "卷一新" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(
      expect.objectContaining({ type: "volume.rename", volumeId: "v1", baseRevision: 5, title: "卷一新" })
    );
  });

  it("章改名提交对象当前真实 revision（非常量 1）", async () => {
    const o = makeOutline();
    o.volumes[0].chapters[0].revision = 7;
    const { runStructure } = renderTree(o);
    fireEvent.click(screen.getAllByTitle("改名")[1]);
    const input = screen.getByDisplayValue("第一章");
    fireEvent.change(input, { target: { value: "第一章新" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(
      expect.objectContaining({ type: "chapter.rename", chapterId: "c1", baseRevision: 7 })
    );
  });

  it("场景改名提交对象当前真实 revision（非常量 1）", async () => {
    const o = makeOutline();
    o.volumes[0].chapters[0].scenes[0].revision = 3;
    const { runStructure } = renderTree(o);
    fireEvent.click(screen.getAllByTitle("改名")[2]);
    const input = screen.getByDisplayValue("场景A");
    fireEvent.change(input, { target: { value: "场景A新" } });
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(
      expect.objectContaining({ type: "scene.rename", sceneId: "s1", baseRevision: 3 })
    );
  });

  it("取消拆章预览不调用命令", async () => {
    const runStructure = vi.fn().mockResolvedValue(true);
    renderTree(makeOutline(), runStructure);
    fireEvent.click(screen.getAllByTitle("从该场景拆为新章")[1]); // 场景B（非首场景）
    const input = await screen.findByRole("textbox");
    fireEvent.change(input, { target: { value: "新章名" } });
    fireEvent.click(screen.getByText("确定"));
    expect(await screen.findByText("拆章影响预览")).toBeTruthy();
    fireEvent.click(screen.getByText("取消"));
    expect(runStructure).not.toHaveBeenCalled();
  });

  it("首个场景作为拆分点显示无效并禁用确认", async () => {
    const runStructure = vi.fn().mockResolvedValue(true);
    renderTree(makeOutline(), runStructure);
    fireEvent.click(screen.getAllByTitle("从该场景拆为新章")[0]); // 场景A（首场景）
    const input = await screen.findByRole("textbox");
    fireEvent.change(input, { target: { value: "新章名" } });
    fireEvent.click(screen.getByText("确定"));
    expect(await screen.findByText(/不能从第一个场景拆分/)).toBeTruthy();
    expect((screen.getByText("确认拆章") as HTMLButtonElement).disabled).toBe(true);
    expect(runStructure).not.toHaveBeenCalled();
  });

  it("章节跨卷移动：确认前零写入", async () => {
    const runStructure = vi.fn().mockResolvedValue(true);
    const { applyStructureWithProtection } = renderTree(makeOutline(), runStructure);
    fireEvent.change(screen.getAllByLabelText("移动到卷")[0], { target: { value: "v2" } });
    expect(await screen.findByText("跨卷移动影响预览")).toBeTruthy();
    expect(runStructure).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("确认移动"));
    await waitFor(() => expect(applyStructureWithProtection).toHaveBeenCalled());
    expect(runStructure).not.toHaveBeenCalled();
  });

  it("并入上一章显示工作区返回的权威影响行", async () => {
    const runStructure = vi.fn().mockResolvedValue(true);
    renderTree(makeOutline(), runStructure);
    fireEvent.click(screen.getAllByTitle("并入上一章")[0]); // 第二章（前一章为第一章）
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("并入上一章影响预览")).toBeTruthy();
    expect(within(dialog).getByText("权威影响")).toBeTruthy();
    expect(within(dialog).getByText("由工作区计算")).toBeTruthy();
  });

  it("保护应用失败时保留对话框并显示错误", async () => {
    const applyStructureWithProtection = vi.fn().mockResolvedValue(null);
    renderTree(makeOutline(), vi.fn().mockResolvedValue(true), { applyStructureWithProtection });
    fireEvent.change(screen.getAllByLabelText("移动到卷")[0], { target: { value: "v2" } });
    const dialog = await screen.findByRole("dialog");
    await waitFor(() => expect((within(dialog).getByText("确认移动") as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(within(dialog).getByText("确认移动"));
    expect(await within(dialog).findByText(/操作未成功/)).toBeTruthy();
    expect(screen.getByRole("dialog")).toBeTruthy();
  });

  it("命令失败不产生本地假顺序", async () => {
    const runStructure = vi.fn().mockResolvedValue(false);
    renderTree(makeOutline(), runStructure);
    const sceneEl = screen.getByText("场景B").closest(".outline-scene")!;
    fireEvent.click(within(sceneEl as HTMLElement).getByTitle("上移")); // 有效 beforeId=s1
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    const titles = Array.from(document.querySelectorAll(".outline-scene-title")).map((e) => e.textContent);
    expect(titles).toEqual(["场景A", "场景B", "场景C", "场景D"]);
  });
});

describe("OutlineTree 重排边界（ReorderTarget）", () => {
  function makeThreeSceneOutline(): CreationProjectOutline {
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
    return o;
  }

  it("三场景列表：最后一项下移按钮禁用", () => {
    renderTree(makeThreeSceneOutline());
    const sceneEl = screen.getByText("场景E").closest(".outline-scene")!;
    expect((within(sceneEl as HTMLElement).getByTitle("下移") as HTMLButtonElement).disabled).toBe(true);
  });

  it("三场景列表：第一项上移按钮禁用", () => {
    renderTree(makeThreeSceneOutline());
    const sceneEl = screen.getByText("场景A").closest(".outline-scene")!;
    expect((within(sceneEl as HTMLElement).getByTitle("上移") as HTMLButtonElement).disabled).toBe(true);
  });

  it("三场景列表：倒数第二项下移 → 移到末位（beforeId=undefined）", async () => {
    const { runStructure } = renderTree(makeThreeSceneOutline());
    const sceneEl = screen.getByText("场景B").closest(".outline-scene")!;
    fireEvent.click(within(sceneEl as HTMLElement).getByTitle("下移"));
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(expect.objectContaining({ type: "scene.reorder", sceneId: "s2", beforeSceneId: undefined }));
  });

  it("三场景列表：第一项下移 → beforeId=第三项", async () => {
    const { runStructure } = renderTree(makeThreeSceneOutline());
    const sceneEl = screen.getByText("场景A").closest(".outline-scene")!;
    fireEvent.click(within(sceneEl as HTMLElement).getByTitle("下移"));
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(expect.objectContaining({ type: "scene.reorder", sceneId: "s1", beforeSceneId: "s1b" }));
  });

  it("三场景列表：第二项上移 → beforeId=第一项", async () => {
    const { runStructure } = renderTree(makeThreeSceneOutline());
    const sceneEl = screen.getByText("场景B").closest(".outline-scene")!;
    fireEvent.click(within(sceneEl as HTMLElement).getByTitle("上移"));
    await waitFor(() => expect(runStructure).toHaveBeenCalled());
    expect(runStructure).toHaveBeenCalledWith(expect.objectContaining({ type: "scene.reorder", sceneId: "s2", beforeSceneId: "s1" }));
  });

  it("单项章节：上移与下移均禁用", () => {
    renderTree(makeOutline());
    const sceneEl = screen.getByText("场景C").closest(".outline-scene")!; // c2 仅一个场景
    expect((within(sceneEl as HTMLElement).getByTitle("上移") as HTMLButtonElement).disabled).toBe(true);
    expect((within(sceneEl as HTMLElement).getByTitle("下移") as HTMLButtonElement).disabled).toBe(true);
  });
});
