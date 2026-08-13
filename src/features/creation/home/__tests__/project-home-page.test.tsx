// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, waitFor } from "@testing-library/react";
import { ProjectHomePage } from "@/features/creation/home/ProjectHomePage";
import type { ProjectHomeView, InboxCountView } from "@/types/creation";

const { loadProjectHomeMock, loadInboxCountMock } = vi.hoisted(() => ({
  loadProjectHomeMock: vi.fn(),
  loadInboxCountMock: vi.fn()
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadProjectHome: loadProjectHomeMock,
    loadInboxCount: loadInboxCountMock
  })
}));

function entry(id: string, title: string, currentChars = 0) {
  return {
    id,
    title,
    setup: { template: "blank" as const, weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
    updatedAt: "",
    revision: 1,
    chapterCount: 1,
    sceneCount: 1,
    currentChars
  };
}

const noop = () => undefined;

beforeEach(() => {
  loadProjectHomeMock.mockReset();
  loadInboxCountMock.mockReset();
  loadProjectHomeMock.mockResolvedValue({ projects: [] } satisfies ProjectHomeView);
  loadInboxCountMock.mockResolvedValue({ total: 0, pending: 0 } satisfies InboxCountView);
});
afterEach(() => cleanup());

describe("ProjectHomePage 数据刷新", () => {
  it("挂载时读取 project.home 并渲染最近项目", async () => {
    loadProjectHomeMock.mockResolvedValue({ projects: [entry("p1", "项目A", 1200)] });
    render(
      <ProjectHomePage
        onOpenProject={noop}
        onContinueWriting={noop}
        onOpenInbox={noop}
        onCreateProject={noop}
        onImportBundle={noop}
        onImportDraft={noop}
      />
    );
    await waitFor(() => expect(loadProjectHomeMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByText("项目A")).toBeDefined();
    expect(screen.getByText(/1.2k 字/)).toBeDefined();
  });

  it("refreshKey 递增后重新读取 project.home 并显示新数据", async () => {
    loadProjectHomeMock.mockResolvedValue({ projects: [entry("p1", "项目A")] });
    const { rerender } = render(
      <ProjectHomePage
        onOpenProject={noop}
        onContinueWriting={noop}
        onOpenInbox={noop}
        onCreateProject={noop}
        onImportBundle={noop}
        onImportDraft={noop}
        refreshKey={0}
      />
    );
    await waitFor(() => expect(loadProjectHomeMock).toHaveBeenCalledTimes(1));

    loadProjectHomeMock.mockResolvedValue({ projects: [entry("p1", "项目A"), entry("p2", "新导入项目")] });
    rerender(
      <ProjectHomePage
        onOpenProject={noop}
        onContinueWriting={noop}
        onOpenInbox={noop}
        onCreateProject={noop}
        onImportBundle={noop}
        onImportDraft={noop}
        refreshKey={1}
      />
    );
    await waitFor(() => expect(loadProjectHomeMock).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("新导入项目")).toBeDefined();
  });

  it("refreshKey 不变时普通重渲染不重复拉取", async () => {
    const { rerender } = render(
      <ProjectHomePage
        onOpenProject={noop}
        onContinueWriting={noop}
        onOpenInbox={noop}
        onCreateProject={noop}
        onImportBundle={noop}
        onImportDraft={noop}
        refreshKey={0}
      />
    );
    await waitFor(() => expect(loadProjectHomeMock).toHaveBeenCalledTimes(1));
    rerender(
      <ProjectHomePage
        onOpenProject={noop}
        onContinueWriting={noop}
        onOpenInbox={noop}
        onCreateProject={noop}
        onImportBundle={noop}
        onImportDraft={noop}
        refreshKey={0}
      />
    );
    await waitFor(() => expect(loadProjectHomeMock).toHaveBeenCalledTimes(1));
  });
});
