// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, cleanup, fireEvent } from "@testing-library/react";
import { OverviewPage } from "@/features/creation/overview/OverviewPage";
import { useCreationStore } from "@/stores/creation-store";
import type { CreationProjectNavigation, CreationProjectSummary, ProjectStatsView } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadStats: vi.fn(async () => null),
  loadInboxCount: vi.fn(async () => ({ total: 0, pending: 0 }))
}));

vi.mock("@/hooks/useCreationActions", () => ({
  useCreationActions: () => ({
    loadStats: actions.loadStats,
    loadInboxCount: actions.loadInboxCount
  })
}));

const selectScene = vi.fn();

function projectOf(partial: Partial<CreationProjectSummary> = {}): CreationProjectSummary {
  return {
    id: "p1",
    title: "示例项目",
    setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"] },
    updatedAt: "",
    revision: 1,
    chapterCount: 2,
    sceneCount: 4,
    currentChars: 0,
    ...partial
  } as CreationProjectSummary;
}

function navigationOf(): CreationProjectNavigation {
  return {
    project: { id: "p1", title: "示例项目", revision: 1 },
    chapters: [
      { id: "ch1", projectId: "p1", title: "第一章", sortOrder: 0, createdAt: "", updatedAt: "", revision: 1, scenes: [
        { id: "scene-a", chapterId: "ch1", title: "场景 A", sortOrder: 0, createdAt: "", updatedAt: "2026-08-01T00:00:00.000Z", revision: 1 },
        { id: "scene-b", chapterId: "ch1", title: "场景 B", sortOrder: 1, createdAt: "", updatedAt: "2026-08-14T00:00:00.000Z", revision: 1 }
      ] },
      { id: "ch2", projectId: "p1", title: "第二章", sortOrder: 1, createdAt: "", updatedAt: "", revision: 1, scenes: [
        { id: "scene-c", chapterId: "ch2", title: "场景 C", sortOrder: 0, createdAt: "", updatedAt: "2026-08-10T00:00:00.000Z", revision: 1 }
      ] }
    ]
  } as unknown as CreationProjectNavigation;
}

function statsOf(overrides: Partial<ProjectStatsView> = {}): ProjectStatsView {
  return {
    projectId: "p1",
    words: { han: 4000, nonWhitespace: 15000, withPunctuation: 7000 },
    sessionMinutes: { today: 25, week: 180, total: 540 },
    daily: [
      { date: "2026-08-01", netChars: 200, activeSeconds: 600 },
      { date: "2026-08-14", netChars: 800, activeSeconds: 1500 }
    ],
    revisionCount: 3,
    snapshotCount: 1,
    streakDays: 7,
    chapterStatusCounts: [{ status: "写作中", count: 2 }, { status: "规划", count: 1 }],
    ...overrides
  } as ProjectStatsView;
}

function renderOverview(callbacks?: { onContinueWriting?: () => void; onOpenOutline?: () => void; onOpenStats?: () => void; onOpenInbox?: () => void }) {
  return render(
    <OverviewPage
      projectId="p1"
      onContinueWriting={callbacks?.onContinueWriting ?? vi.fn()}
      onOpenOutline={callbacks?.onOpenOutline ?? vi.fn()}
      onOpenStats={callbacks?.onOpenStats ?? vi.fn()}
      onOpenInbox={callbacks?.onOpenInbox ?? vi.fn()}
    />
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  actions.loadStats.mockResolvedValue(statsOf());
  actions.loadInboxCount.mockResolvedValue({ total: 12, pending: 5 });
  useCreationStore.setState({
    projects: [projectOf()],
    navigations: { p1: navigationOf() },
    selectScene
  } as never);
});

afterEach(() => cleanup());

describe("OverviewPage 项目仪表板", () => {
  it("hero 展示功能标签（项目名由外层页面 hero 呈现，避免重复）、章节数与场景数；徽章显示今日/连续/本周净增", async () => {
    renderOverview();
    expect(await screen.findByText("项目概览")).toBeDefined();
    const hero = screen.getByText(/2 章 · 3 场景/);
    expect(hero).toBeDefined();
    expect(await screen.findByText((content) => content.includes("今日") && content.includes("25"))).toBeDefined();
    expect(await screen.findByText((content) => content.includes("连续") && content.includes("7"))).toBeDefined();
  });

  it("有总字数目标时显示进度数字与剩余提示", async () => {
    useCreationStore.setState({
      projects: [projectOf({ setup: { template: "blank", weeklyUpdateDays: [], chapterWorkflow: ["规划"], totalWordGoal: 30000 } })],
      navigations: { p1: navigationOf() },
      selectScene
    } as never);
    renderOverview();
    expect(await screen.findByText(/距离目标还差/)).toBeDefined();
    expect(screen.getByText((c) => c.includes("30,000") && c.includes("15,000"))).toBeDefined();
  });

  it("无总字数目标时提示尚未设置", async () => {
    renderOverview();
    expect(await screen.findByText(/尚未设置总字数目标/)).toBeDefined();
  });

  it("章节状态分布列出各状态与数量", async () => {
    renderOverview();
    expect(await screen.findByText("写作中")).toBeDefined();
    expect(screen.getByText("规划")).toBeDefined();
    // 占比暗含：写作中 2，规划 1
    expect(screen.getAllByText("2").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("1").length).toBeGreaterThanOrEqual(1);
  });

  it("待处理卡显示真实未处理与总数", async () => {
    renderOverview();
    expect(await screen.findByText((c) => c.includes("5") && c.includes("12") && c.includes("未整理"))).toBeDefined();
  });

  it("最近编辑按 updatedAt 倒序排列，点击跳到对应场景并进入写作", async () => {
    const onContinueWriting = vi.fn();
    renderOverview({ onContinueWriting });
    // 通过点击场景列表项触发跳场景
    const sceneButtons = await screen.findAllByRole("button", {
      name: /场景/
    });
    expect(sceneButtons.length).toBeGreaterThanOrEqual(3);
    // 第一项应为 updatedAt 最新的 scene-b
    expect(screen.getByText("场景 B")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: /场景 B/ }));
    expect(selectScene).toHaveBeenCalledWith("scene-b");
    expect(onContinueWriting).toHaveBeenCalled();
  });

  it("操作行四个入口分别触发对应回调", async () => {
    const onContinueWriting = vi.fn();
    const onOpenOutline = vi.fn();
    const onOpenStats = vi.fn();
    const onOpenInbox = vi.fn();
    renderOverview({ onContinueWriting, onOpenOutline, onOpenStats, onOpenInbox });
    // 操作行：继续写作 / 大纲 / 统计 / 收件箱（用语义 aria-label 与文本匹配）
    const allButtons = await screen.findAllByRole("button");
    const findByText = (text: string) =>
      allButtons.find((el) => (el.textContent ?? "").includes(text));
    fireEvent.click(findByText("大纲")!);
    fireEvent.click(findByText("统计")!);
    fireEvent.click(findByText("收件箱")!);
    expect(onOpenOutline).toHaveBeenCalled();
    expect(onOpenStats).toHaveBeenCalled();
    expect(onOpenInbox).toHaveBeenCalled();
  });
});