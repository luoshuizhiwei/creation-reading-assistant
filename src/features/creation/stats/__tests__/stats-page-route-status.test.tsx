// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, waitFor } from "@testing-library/react";
import { StatsPage } from "@/features/creation/stats/StatsPage";
import { useUIStore } from "@/stores/ui-store";
import { STATS_LOADING_STATUS } from "@/hooks/useRouteStatus";
import type { ProjectStatsView } from "@/types/creation";

/**
 * BD-198 行为账：统计页到达播报经由根级常驻区的槽位。
 *
 * BD-193 摘掉 StatsPage 加载根的死装饰后，「正在读取统计…」一直是哑的
 * （页面自己挂载即插入带内容）。这一批把忙态经 useRouteStatus 喂给 App
 * 根级常驻区：进入即有声，stats 落定即清账，中途退出卸载也不留残播。
 */

const actions = vi.hoisted(() => ({
  loadStats: vi.fn(async (): Promise<ProjectStatsView | null> => null),
  loadSessions: vi.fn(async () => []),
  loadProjectHome: vi.fn(async () => ({ projects: [] })),
  deleteSession: vi.fn(async () => true),
  updateProjectGoal: vi.fn(async () => null),
  updateSession: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => actions }));

const slot = () => useUIStore.getState().routeStatus;

function statsOf(): ProjectStatsView {
  return {
    projectId: "p1",
    words: { han: 4000, nonWhitespace: 15000, withPunctuation: 7000 },
    sessionMinutes: { today: 25, week: 180, total: 540 },
    daily: [{ date: "2026-10-26", netChars: 500, activeSeconds: 600 }],
    revisionCount: 3,
    chapterStatusCounts: [{ status: "写作中", count: 1 }],
    sceneStatusCounts: [{ status: "planned", count: 2 }],
    snapshotCount: 1,
    streakDays: 7
  } as unknown as ProjectStatsView;
}

beforeEach(() => {
  vi.clearAllMocks();
  useUIStore.setState({ routeStatus: "" });
});

afterEach(() => cleanup());

describe("BD-198：StatsPage 到达播报接上根级常驻区", () => {
  it("进页即有声，stats 落定即清账", async () => {
    let release!: (value: ProjectStatsView | null) => void;
    actions.loadStats.mockReturnValueOnce(
      new Promise<ProjectStatsView | null>((resolve) => {
        release = resolve;
      })
    );
    render(<StatsPage projectId="p1" />);
    // 挂载 effect 跑完、IPC 未回：播报必须在槽里
    expect(slot()).toBe(STATS_LOADING_STATUS);
    release(statsOf());
    await waitFor(() => expect(slot()).toBe(""));
  });

  it("stats 落定时槽已被他人改写：只清自己的文本", async () => {
    let release!: (value: ProjectStatsView | null) => void;
    actions.loadStats.mockReturnValueOnce(
      new Promise<ProjectStatsView | null>((resolve) => {
        release = resolve;
      })
    );
    render(<StatsPage projectId="p1" />);
    expect(slot()).toBe(STATS_LOADING_STATUS);
    // IPC 未回时他人接管槽位（例如用户切去开 EPUB）——本页就绪只能清自己的文本
    useUIStore.getState().setRouteStatus("正在打开 EPUB…");
    release(statsOf());
    await waitFor(() => expect(actions.loadSessions).toHaveBeenCalled());
    expect(slot()).toBe("正在打开 EPUB…");
  });

  it("忙碌中卸载：清掉本页写的播报，不留残声", async () => {
    actions.loadStats.mockReturnValue(new Promise<ProjectStatsView | null>(() => undefined));
    const view = render(<StatsPage projectId="p1" />);
    expect(slot()).toBe(STATS_LOADING_STATUS);
    view.unmount();
    expect(slot()).toBe("");
  });
});
