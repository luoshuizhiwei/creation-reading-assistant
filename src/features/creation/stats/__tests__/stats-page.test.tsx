// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { StatsPage } from "@/features/creation/stats/StatsPage";
import type { ProjectDailyStat, ProjectStatsView } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadStats: vi.fn(async (): Promise<ProjectStatsView | null> => null),
  loadSessions: vi.fn(async () => []),
  loadProjectHome: vi.fn(async () => ({ projects: [] })),
  deleteSession: vi.fn(async () => true),
  updateProjectGoal: vi.fn(async () => null),
  updateSession: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => actions }));

/** 生成与主进程同口径的最近 N 天本地日历序列（仅今天有净增）。 */
function dailyOf(days = 30): ProjectDailyStat[] {
  const now = new Date();
  const list: ProjectDailyStat[] = [];
  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() - offset);
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    list.push({ date: key, netChars: offset === 0 ? 500 : 0, activeSeconds: offset === 0 ? 600 : 0 });
  }
  return list;
}

function statsOf(overrides: Partial<ProjectStatsView> = {}): ProjectStatsView {
  return {
    projectId: "p1",
    words: { han: 4000, nonWhitespace: 15000, withPunctuation: 7000 },
    sessionMinutes: { today: 25, week: 180, total: 540 },
    daily: dailyOf(),
    revisionCount: 3,
    chapterStatusCounts: [{ status: "写作中", count: 1 }],
    // 刻意省略 drafting：渲染层必须自行补 0，而不是漏掉这一行。
    sceneStatusCounts: [
      { status: "planned", count: 2 },
      { status: "revising", count: 3 },
      { status: "done", count: 5 }
    ],
    snapshotCount: 1,
    streakDays: 7,
    ...overrides
  } as ProjectStatsView;
}

function renderStats() {
  return render(<StatsPage projectId="p1" />);
}

beforeEach(() => {
  vi.clearAllMocks();
  actions.loadStats.mockResolvedValue(statsOf());
});

afterEach(() => cleanup());

describe("StatsPage 创作统计", () => {
  it("日趋势使用最近 30 天窗口", async () => {
    renderStats();
    expect(await screen.findByText("最近 30 天净增字数与写作时长")).toBeDefined();
    expect(screen.getByRole("img", { name: "最近三十天净增字数柱状图" })).toBeDefined();
    expect(screen.queryByText(/最近 14 天/)).toBeNull();
  });

  it("场景状态分布列出全部四种状态并为缺失状态补 0，另给出合计", async () => {
    renderStats();
    expect(await screen.findByText("场景状态")).toBeDefined();
    // 四个状态固定顺序铺满，drafting 在数据里不存在也要显示为 0。
    for (const label of ["待规划", "起草中", "修订中", "已完成"]) {
      expect(screen.getByText(label)).toBeDefined();
    }
    expect(screen.getByText("2 场")).toBeDefined();
    expect(screen.getByText("0 场")).toBeDefined();
    expect(screen.getByText("3 场")).toBeDefined();
    expect(screen.getByText("5 场")).toBeDefined();
    // 合计 = 2 + 0 + 3 + 5
    expect(screen.getByText("10 场")).toBeDefined();
  });

  it("场景状态与章节状态并列展示，并给出两者口径不同的说明", async () => {
    renderStats();
    // 章节状态仍走 chapters.status，与场景状态同时出现但不合并。
    expect(await screen.findByText("写作中 1")).toBeDefined();
    expect(screen.getByText("场景状态取自场景任务卡，独立于章节工作流状态。")).toBeDefined();
    expect(screen.getByText("章节状态是章节在项目工作流中的位置，与下面的场景状态是两种口径。")).toBeDefined();
  });

  it("没有任何场景时显示空态而不是四个 0", async () => {
    actions.loadStats.mockResolvedValue(statsOf({ sceneStatusCounts: [] }));
    renderStats();
    expect(await screen.findByText(/还没有场景/)).toBeDefined();
    expect(screen.queryByText("0 场")).toBeNull();
  });

  it("30 天柱状图抽稀日期标签，避免窄窗口下相互重叠", async () => {
    const { container } = renderStats();
    await screen.findByText("最近 30 天净增字数与写作时长");
    const labels = Array.from(container.querySelectorAll(".stats-daily-label"));
    expect(labels.length).toBe(30);
    const visible = labels.filter((node) => (node.textContent ?? "").trim().length > 0);
    // 每 5 天一处 + 今天：0/5/10/15/20/25/29，共 7 处。
    expect(visible.length).toBe(7);
  });

  it("未知场景状态值原样展示，不被静默归并", async () => {
    actions.loadStats.mockResolvedValue(
      statsOf({ sceneStatusCounts: [{ status: "planned", count: 1 }, { status: "archived", count: 4 }] })
    );
    renderStats();
    expect(await screen.findByText("archived")).toBeDefined();
    expect(screen.getByText("4 场")).toBeDefined();
  });
});
