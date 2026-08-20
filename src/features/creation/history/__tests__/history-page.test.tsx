// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { HistoryPage } from "@/features/creation/history/HistoryPage";
import type { CreationProjectSummary, SnapshotInfo, TrashItem } from "@/types/creation";

const mocks = vi.hoisted(() => ({
  loadTrash: vi.fn(),
  restoreTrash: vi.fn(),
  purgeTrash: vi.fn(),
  loadSnapshots: vi.fn(),
  previewSnapshot: vi.fn(),
  restoreSnapshotWithProtection: vi.fn(),
  loadTrashImpact: vi.fn(),
  runStructure: vi.fn(),
  loadNavigation: vi.fn(),
  loadCards: vi.fn(),
  loadOutline: vi.fn(),
  showToast: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadTrash: mocks.loadTrash,
    restoreTrash: mocks.restoreTrash,
    purgeTrash: mocks.purgeTrash,
    loadSnapshots: mocks.loadSnapshots,
    previewSnapshot: mocks.previewSnapshot,
    restoreSnapshotWithProtection: mocks.restoreSnapshotWithProtection,
    loadTrashImpact: mocks.loadTrashImpact,
    runStructure: mocks.runStructure,
    loadNavigation: mocks.loadNavigation,
    loadCards: mocks.loadCards,
    loadOutline: mocks.loadOutline
  })
}));

vi.mock("@/stores/creation-store", () => ({
  useCreationStore: (selector: (state: Record<string, unknown>) => unknown) =>
    selector({ navigations: {}, cardProjectId: null, cards: [] })
}));

vi.mock("@/stores/ui-store", () => ({
  useUIStore: (selector: (state: { showToast: typeof mocks.showToast }) => unknown) =>
    selector({ showToast: mocks.showToast })
}));

const project: CreationProjectSummary = {
  id: "p1",
  title: "项目",
  setup: { weeklyUpdateDays: [], chapterWorkflow: ["草稿"] },
  createdAt: "2026-08-01T00:00:00.000Z",
  updatedAt: "2026-08-01T00:00:00.000Z",
  revision: 1
};

const snapshot: SnapshotInfo = {
  id: "snap-1",
  projectId: "p1",
  subjectType: "scene",
  subjectId: "scene-1",
  reason: "初稿",
  createdAt: "2026-08-10T10:00:00.000Z"
};

const trashItem: TrashItem = {
  id: "chapter-1",
  projectId: "p1",
  entity: "chapter",
  title: "旧章节",
  deletedAt: "2026-08-12T09:00:00.000Z",
  revision: 2
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.loadTrash.mockResolvedValue([]);
  mocks.loadSnapshots.mockResolvedValue([]);
  mocks.loadNavigation.mockResolvedValue(null);
  mocks.loadCards.mockResolvedValue([]);
  mocks.loadOutline.mockResolvedValue(null);
  mocks.purgeTrash.mockResolvedValue(true);
});

afterEach(() => cleanup());

async function openSnapshotRestore(snap: SnapshotInfo = snapshot) {
  mocks.loadSnapshots.mockResolvedValue([snap]);
  render(<HistoryPage project={project} />);
  await waitFor(() => expect(mocks.loadNavigation).toHaveBeenCalledWith("p1"));
  fireEvent.click(screen.getByRole("tab", { name: "版本快照" }));
  expect(await screen.findByText(snap.reason)).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "恢复" }));
}

describe("HistoryPage 权威快照恢复", () => {
  it("先展示 snapshot.preview 的对象级差异，再原子创建保护快照并恢复，成功后展示保护快照 ID 并刷新项目视图", async () => {
    mocks.previewSnapshot.mockResolvedValue({
      snapshotId: "snap-1",
      subjectType: "scene",
      subjectId: "scene-1",
      title: "开场",
      rows: [
        { label: "标题", before: "当前标题", after: "开场", changed: true },
        { label: "正文", before: "当前正文", after: "历史正文", changed: true }
      ],
      warnings: ["恢复会覆盖当前正文"],
      canRestore: true
    });
    mocks.restoreSnapshotWithProtection.mockResolvedValue({
      ok: true,
      protectionSnapshotId: "protect-1",
      restoredSubjectType: "scene",
      restoredSubjectId: "scene-1",
      revision: 3
    });

    await openSnapshotRestore();

    await waitFor(() =>
      expect(mocks.previewSnapshot).toHaveBeenCalledWith({
        kind: "snapshot.preview",
        projectId: "p1",
        snapshotId: "snap-1"
      })
    );
    expect(await screen.findByText("当前标题")).toBeDefined();
    expect(screen.getByText("历史正文")).toBeDefined();
    expect(screen.getByText("恢复会覆盖当前正文")).toBeDefined();
    expect(mocks.runStructure).not.toHaveBeenCalledWith(
      expect.objectContaining({ type: "snapshot.restore" })
    );

    fireEvent.click(screen.getByRole("button", { name: "先保护再恢复" }));
    await waitFor(() =>
      expect(mocks.restoreSnapshotWithProtection).toHaveBeenCalledWith({
        type: "snapshot.restoreWithProtection",
        projectId: "p1",
        snapshotId: "snap-1",
        protectionReason: expect.stringContaining("恢复前保护")
      })
    );
    // 成功后保留对话框并展示保护快照 ID，同时刷新快照与项目视图。
    expect(await screen.findByText("protect-1")).toBeDefined();
    expect(screen.getByRole("dialog", { name: "从快照恢复确认" })).toBeDefined();
    await waitFor(() => expect(mocks.loadSnapshots).toHaveBeenCalledTimes(2));
    expect(mocks.loadNavigation).toHaveBeenCalledWith("p1");
    expect(mocks.loadCards).toHaveBeenCalledWith({ projectId: "p1" });
    expect(mocks.loadOutline).toHaveBeenCalledWith("p1");
  });

  it("snapshot.preview 加载失败时禁止恢复", async () => {
    mocks.previewSnapshot.mockResolvedValue(null);

    await openSnapshotRestore();
    expect(await screen.findByText(/无法读取权威恢复预览/)).toBeDefined();
    expect(
      (screen.getByRole("button", { name: "先保护再恢复" }) as HTMLButtonElement).disabled
    ).toBe(true);
    expect(mocks.restoreSnapshotWithProtection).not.toHaveBeenCalled();
  });

  it("canRestore=false 时禁止提交恢复", async () => {
    mocks.previewSnapshot.mockResolvedValue({
      snapshotId: "snap-1",
      subjectType: "scene",
      subjectId: "scene-1",
      title: "开场",
      rows: [{ label: "正文", before: "当前正文", after: "历史正文", changed: true }],
      warnings: ["该快照已被保护引用，无法覆盖当前内容"],
      canRestore: false
    });

    await openSnapshotRestore();
    expect(await screen.findByText("历史正文")).toBeDefined();
    expect(
      (screen.getByRole("button", { name: "先保护再恢复" }) as HTMLButtonElement).disabled
    ).toBe(true);
    expect(mocks.restoreSnapshotWithProtection).not.toHaveBeenCalled();
  });

  it("原子恢复失败时保留对话框与权威预览", async () => {
    mocks.previewSnapshot.mockResolvedValue({
      snapshotId: "snap-1",
      subjectType: "scene",
      subjectId: "scene-1",
      title: "开场",
      rows: [{ label: "正文", before: "当前正文", after: "历史正文", changed: true }],
      warnings: [],
      canRestore: true
    });
    mocks.restoreSnapshotWithProtection.mockResolvedValue(null);

    await openSnapshotRestore();
    expect(await screen.findByText("当前正文")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "先保护再恢复" }));

    expect(await screen.findByText(/恢复失败/)).toBeDefined();
    expect(screen.getByRole("dialog", { name: "从快照恢复确认" })).toBeDefined();
    expect(screen.getByText("历史正文")).toBeDefined();
  });

  it("章快照以权威预览名称作为标题展示", async () => {
    const chapterSnap: SnapshotInfo = {
      id: "snap-ch",
      projectId: "p1",
      subjectType: "chapter",
      subjectId: "chapter-x",
      reason: "章快照",
      createdAt: "2026-08-10T10:00:00.000Z"
    };
    mocks.previewSnapshot.mockResolvedValue({
      snapshotId: "snap-ch",
      subjectType: "chapter",
      subjectId: "chapter-x",
      title: "目标章",
      rows: [{ label: "标题", before: "当前", after: "历史", changed: true }],
      warnings: [],
      canRestore: true
    });

    await openSnapshotRestore(chapterSnap);
    expect(await screen.findByText(/目标章/)).toBeDefined();
    expect(screen.getByText("章")).toBeDefined();
  });

  it("卷快照以权威预览名称作为标题展示", async () => {
    const volumeSnap: SnapshotInfo = {
      id: "snap-vol",
      projectId: "p1",
      subjectType: "volume",
      subjectId: "volume-x",
      reason: "卷快照",
      createdAt: "2026-08-10T10:00:00.000Z"
    };
    mocks.previewSnapshot.mockResolvedValue({
      snapshotId: "snap-vol",
      subjectType: "volume",
      subjectId: "volume-x",
      title: "目标卷",
      rows: [{ label: "标题", before: "当前", after: "历史", changed: true }],
      warnings: [],
      canRestore: true
    });

    await openSnapshotRestore(volumeSnap);
    expect(await screen.findByText(/目标卷/)).toBeDefined();
    expect(screen.getByText("卷")).toBeDefined();
  });

});

describe("HistoryPage 永久删除影响确认", () => {
  it("展示 trash.impact，并仅在输入完整对象名称后允许永久删除", async () => {
    mocks.loadTrash.mockResolvedValue([trashItem]);
    mocks.loadTrashImpact.mockResolvedValue({
      title: "旧章节",
      childVolumeCount: 0,
      childChapterCount: 0,
      childSceneCount: 4,
      relatedCardCount: 2,
      resourceCount: 3,
      approxChars: 12500,
      warnings: ["关联卡片不会删除，但相关关系会移除"]
    });
    render(<HistoryPage project={project} />);

    expect(await screen.findByText("旧章节")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "永久删除" }));
    await waitFor(() =>
      expect(mocks.loadTrashImpact).toHaveBeenCalledWith({
        kind: "trash.impact",
        projectId: "p1",
        entity: "chapter",
        entityId: "chapter-1"
      })
    );

    expect(await screen.findByText(/4 个场景/)).toBeDefined();
    expect(screen.getByText(/约 12,500 字/)).toBeDefined();
    expect(screen.getByText(/2 张关联卡片/)).toBeDefined();
    expect(screen.getByText(/3 个资源/)).toBeDefined();
    expect(screen.getByText("关联卡片不会删除，但相关关系会移除")).toBeDefined();

    const confirm = screen.getByRole("button", { name: "确认永久删除" });
    expect((confirm as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("输入对象名称确认"), { target: { value: "旧章" } });
    expect((confirm as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByLabelText("输入对象名称确认"), { target: { value: "旧章节" } });
    expect((confirm as HTMLButtonElement).disabled).toBe(false);
    fireEvent.click(confirm);
    await waitFor(() =>
      expect(mocks.purgeTrash).toHaveBeenCalledWith("p1", "chapter", "chapter-1")
    );
  });

  it("trash.impact 查询失败时禁止永久删除", async () => {
    mocks.loadTrash.mockResolvedValue([trashItem]);
    mocks.loadTrashImpact.mockResolvedValue(null);

    render(<HistoryPage project={project} />);
    expect(await screen.findByText("旧章节")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "永久删除" }));
    await waitFor(() =>
      expect(mocks.loadTrashImpact).toHaveBeenCalledWith({
        kind: "trash.impact",
        projectId: "p1",
        entity: "chapter",
        entityId: "chapter-1"
      })
    );
    expect(await screen.findByText(/无法读取永久删除影响/)).toBeDefined();
    expect(
      (screen.getByRole("button", { name: "确认永久删除" }) as HTMLButtonElement).disabled
    ).toBe(true);
    expect(mocks.purgeTrash).not.toHaveBeenCalled();
  });
});
