// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PurgeTrashDialog } from "../PurgeTrashDialog";
import { RestoreSnapshotDialog } from "../RestoreSnapshotDialog";
import type { SnapshotInfo, StructurePreviewView, TrashImpactView, TrashItem } from "@/types/creation";

// BD-187：两个历史对话框在打开后仍会继续变化（统计中→出结果、确认→恢复成功）。
// 播报此前挂在随内容出现的节点上＝不会被读出；必须常驻一条、只换文本。

beforeAll(() => {
  globalThis.requestAnimationFrame = ((cb: FrameRequestCallback) =>
    setTimeout(() => cb(0), 0) as unknown as number) as typeof requestAnimationFrame;
  globalThis.cancelAnimationFrame = ((id: number) => clearTimeout(id)) as typeof cancelAnimationFrame;
});

const trashItem: TrashItem = {
  entity: "scene",
  id: "scene-a",
  projectId: "p1",
  title: "雨夜来客",
  deletedAt: "2026-01-01T00:00:00.000Z",
  revision: 1
};

const snapshot: SnapshotInfo = {
  id: "snap-1",
  projectId: "p1",
  subjectType: "scene",
  subjectId: "scene-a",
  reason: "改稿前",
  createdAt: "2026-01-01T00:00:00.000Z"
};

const impact: TrashImpactView = {
  title: "雨夜来客",
  childVolumeCount: 0,
  childChapterCount: 0,
  childSceneCount: 3,
  relatedCardCount: 2,
  resourceCount: 0,
  approxChars: 1200,
  warnings: []
};

const preview: StructurePreviewView = {
  snapshotId: "snap-1",
  subjectType: "scene",
  subjectId: "scene-a",
  title: "雨夜来客",
  rows: [],
  warnings: [],
  canRestore: true
};

afterEach(() => cleanup());

describe("历史对话框 · 播报走常驻区（BD-187）", () => {
  it("永久删除：统计中/就绪在同一条常驻区上切换", () => {
    const view = render(
      <PurgeTrashDialog
        item={trashItem}
        impact={null}
        impactBusy={false}
        impactError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    view.rerender(
      <PurgeTrashDialog
        item={trashItem}
        impact={null}
        impactBusy
        impactError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("正在统计永久删除影响…");

    view.rerender(
      <PurgeTrashDialog
        item={trashItem}
        impact={impact}
        impactBusy={false}
        impactError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );
    expect(region.textContent).toBe("");
    expect(screen.getByRole("group", { name: "永久删除影响" })).toBeTruthy();
  });

  it("恢复快照：预览读取中走同一条常驻区", () => {
    const view = render(
      <RestoreSnapshotDialog
        snapshot={snapshot}
        subjectTitle="雨夜来客"
        preview={null}
        previewBusy={false}
        previewError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    view.rerender(
      <RestoreSnapshotDialog
        snapshot={snapshot}
        subjectTitle="雨夜来客"
        preview={null}
        previewBusy
        previewError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );
    expect(screen.getByRole("status")).toBe(region);
    expect(region.textContent).toBe("正在读取权威恢复预览…");
  });

  it("恢复成功后由常驻区播报，对话框保持打开", async () => {
    const onConfirm = vi.fn(async () => ({ ok: true, protectionSnapshotId: "prot-1" }));
    render(
      <RestoreSnapshotDialog
        snapshot={snapshot}
        subjectTitle="雨夜来客"
        preview={preview}
        previewBusy={false}
        previewError={null}
        onCancel={vi.fn()}
        onConfirm={onConfirm}
      />
    );
    const region = screen.getByRole("status");
    expect(region.textContent).toBe("");

    fireEvent.click(screen.getByRole("button", { name: "先保护再恢复" }));

    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1));
    expect(screen.getByRole("status")).toBe(region);
    await waitFor(() => expect(region.textContent).toContain("已恢复到目标版本"));
    // 成功面板仍可见，但不再自带 status 角色。
    expect(screen.getByText("已恢复到目标版本")).toBeTruthy();
    expect(document.querySelector('.history-restore-success[role="status"]')).toBeNull();
  });
});
