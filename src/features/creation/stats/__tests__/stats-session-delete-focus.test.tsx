// @vitest-environment jsdom
/**
 * BD-220（BD-219 同族）：统计页写作会话删除后的焦点交接。
 *
 * 两步确认删除会话后行随刷新卸载，焦点跌回 body（WCAG 2.4.3）。交接判据：
 * 原位置下一行优先，末尾退前一行；会话删空退会话卡片容器。
 */
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { StatsPage } from "@/features/creation/stats/StatsPage";
import type { SessionEntry } from "@/types/creation";
import type { ProjectStatsView } from "@/types/creation";

const actions = vi.hoisted(() => ({
  loadStats: vi.fn(async (): Promise<ProjectStatsView | null> => null),
  loadSessions: vi.fn(async (): Promise<SessionEntry[]> => []),
  loadProjectHome: vi.fn(async () => ({ projects: [] })),
  deleteSession: vi.fn(async () => true),
  updateProjectGoal: vi.fn(async () => null),
  updateSession: vi.fn(async () => true)
}));

vi.mock("@/hooks/useCreationActions", () => ({ useCreationActions: () => actions }));

function sessionOf(id: string): SessionEntry {
  return {
    id,
    projectId: "p1",
    sceneId: "scene-a",
    startedAt: "2026-08-14T08:30:00.000Z",
    activeSeconds: 1200,
    netChars: 400,
    reportedAt: "2026-08-14T08:50:00.000Z"
  } as SessionEntry;
}

const stats = {
  projectId: "p1",
  words: { han: 0, nonWhitespace: 0, withPunctuation: 0 },
  sessionMinutes: { today: 0, week: 0, total: 0 },
  daily: [],
  revisionCount: 0,
  chapterStatusCounts: [],
  sceneStatusCounts: [],
  snapshotCount: 0,
  streakDays: 0
} as unknown as ProjectStatsView;

beforeEach(() => {
  vi.clearAllMocks();
  actions.loadStats.mockResolvedValue(stats);
  actions.deleteSession.mockResolvedValue(true);
});

afterEach(() => cleanup());

async function deleteRow(button: HTMLElement) {
  button.focus();
  fireEvent.click(button);
  fireEvent.click(screen.getByRole("button", { name: "确认删除" }));
}

describe("BD-220：写作会话行卸载焦点交接", () => {
  it("删中间会话：焦点交给原位置下一行", async () => {
    actions.loadSessions
      .mockResolvedValueOnce([sessionOf("s1"), sessionOf("s2"), sessionOf("s3")])
      .mockResolvedValue([sessionOf("s1"), sessionOf("s3")]);

    render(<StatsPage projectId="p1" />);
    const before = await screen.findAllByRole("button", { name: "删除" });
    expect(before).toHaveLength(3);
    await deleteRow(before[1]);

    await waitFor(() => expect(actions.deleteSession).toHaveBeenCalledWith({ projectId: "p1", sessionId: "s2" }));
    const after = await screen.findAllByRole("button", { name: "删除" });
    expect(after).toHaveLength(2);
    expect(document.activeElement).toBe(after[1]);
  });

  it("会话删空：焦点退到会话卡片容器", async () => {
    actions.loadSessions.mockResolvedValueOnce([sessionOf("s1")]).mockResolvedValue([]);

    render(<StatsPage projectId="p1" />);
    const before = await screen.findAllByRole("button", { name: "删除" });
    await deleteRow(before[0]);

    await waitFor(() => expect(actions.deleteSession).toHaveBeenCalled());
    const card = document.querySelector(".stats-sessions-card");
    expect(document.activeElement).toBe(card);
  });
});
