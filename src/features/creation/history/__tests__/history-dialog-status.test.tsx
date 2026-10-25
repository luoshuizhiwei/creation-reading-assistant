// @vitest-environment jsdom
import React from "react";
import { beforeAll, afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { PurgeTrashDialog } from "../PurgeTrashDialog";
import { RestoreSnapshotDialog } from "../RestoreSnapshotDialog";
import type { SnapshotInfo, TrashItem } from "@/types/creation";

beforeAll(() => {
  // jsdom 没有 rAF，Dialog 的首焦点在 rAF 回调里。
  globalThis.requestAnimationFrame = ((cb: FrameRequestCallback) =>
    setTimeout(() => cb(0), 0) as unknown as number) as typeof requestAnimationFrame;
  globalThis.cancelAnimationFrame = ((id: number) => clearTimeout(id)) as typeof cancelAnimationFrame;
});

afterEach(() => cleanup());

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

describe("历史对话框 · 异步进度与错误播报", () => {
  // BD-153：影响统计/恢复预览只是普通段落，读屏器不会播报「正在…」；
  // 统计失败、恢复失败也只是红字，同样不播报。
  it("永久删除影响统计中的提示以 status 暴露", () => {
    render(
      <PurgeTrashDialog
        item={trashItem}
        impact={null}
        impactBusy
        impactError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );

    expect(screen.getByRole("status").textContent).toContain("正在统计永久删除影响");
  });

  it("永久删除影响统计失败以 alert 暴露", () => {
    render(
      <PurgeTrashDialog
        item={trashItem}
        impact={null}
        impactBusy={false}
        impactError="无法读取影响统计"
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );

    expect(screen.getByRole("alert").textContent).toContain("无法读取影响统计");
  });

  it("恢复预览读取中的提示以 status 暴露", () => {
    render(
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

    expect(screen.getByRole("status").textContent).toContain("正在读取权威恢复预览");
  });

  it("恢复预览读取失败以 alert 暴露", () => {
    render(
      <RestoreSnapshotDialog
        snapshot={snapshot}
        subjectTitle="雨夜来客"
        preview={null}
        previewBusy={false}
        previewError="快照已失效"
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );

    expect(screen.getByRole("alert").textContent).toContain("快照已失效");
  });

  // BD-159：影响摘要与差异预览的 aria-label 挂在普通 div 上，没有角色，名字被丢弃。
  it("删除影响摘要与恢复差异预览都是带名字的 group", () => {
    render(
      <PurgeTrashDialog
        item={trashItem}
        impact={{
          title: "雨夜来客",
          childVolumeCount: 0,
          childChapterCount: 0,
          childSceneCount: 3,
          relatedCardCount: 2,
          resourceCount: 0,
          approxChars: 1200,
          warnings: []
        }}
        impactBusy={false}
        impactError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );

    expect(screen.getByRole("group", { name: "永久删除影响" })).toBeTruthy();

    cleanup();
    render(
      <RestoreSnapshotDialog
        snapshot={snapshot}
        subjectTitle="雨夜来客"
        preview={{
          snapshotId: "snap-1",
          subjectType: "scene",
          subjectId: "scene-a",
          title: "雨夜来客",
          rows: [{ label: "标题", before: "雨夜来客", after: "雨夜归人", changed: true }],
          warnings: [],
          canRestore: true
        }}
        previewBusy={false}
        previewError={null}
        onCancel={vi.fn()}
        onConfirm={vi.fn(async () => ({ ok: true }))}
      />
    );

    expect(screen.getByRole("group", { name: "恢复影响预览" })).toBeTruthy();
  });
});
