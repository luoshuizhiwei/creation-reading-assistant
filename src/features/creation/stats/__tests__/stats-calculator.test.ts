import { describe, expect, it, vi } from "vitest";
import type { CreationProjectSetup, ProjectStatsView } from "@/types/creation";
import {
  computeDailyActiveSeconds,
  computeDailyNetChars,
  computeGoalDeadline,
  computeGoalProgress,
  computeWeekSummary,
  computeWeeklyUpdateDayTarget,
  localDateKey,
  startOfLocalWeek,
  wordsForMetric
} from "../stats-calculator";

function statsOf(words: { han: number; nonWhitespace: number; withPunctuation: number }, daily: ProjectStatsView["daily"] = []): ProjectStatsView {
  return {
    projectId: "p1",
    words,
    sessionMinutes: { today: 0, week: 0, total: 0 },
    daily,
    revisionCount: 3,
    chapterStatusCounts: [],
    snapshotCount: 1,
    streakDays: 1
  } as ProjectStatsView;
}

function setupOf(partial: Partial<CreationProjectSetup> = {}): CreationProjectSetup {
  return {
    template: "blank",
    weeklyUpdateDays: [],
    chapterWorkflow: ["规划"],
    ...partial
  };
}

const now = new Date(2026, 7, 14, 15, 30, 0); // 2026-08-14 周五 15:30 本地

describe("stats-calculator 字数指标切换", () => {
  it("三口径互不干扰：按指标取对应字数", () => {
    const stats = statsOf({ han: 4, nonWhitespace: 15, withPunctuation: 7 });
    expect(wordsForMetric(stats, "han")).toBe(4);
    expect(wordsForMetric(stats, "nonWhitespace")).toBe(15);
    expect(wordsForMetric(stats, "withPunctuation")).toBe(7);
  });

  it("目标进度按指标展示：切换指标改变当前值与百分比", () => {
    const stats = statsOf({ han: 400, nonWhitespace: 1500, withPunctuation: 700 });
    const setup = setupOf({ totalWordGoal: 2000 });
    const han = computeGoalProgress(stats, setup, "han");
    const ws = computeGoalProgress(stats, setup, "nonWhitespace");
    expect(han?.current).toBe(400);
    expect(han?.percent).toBe(20);
    expect(ws?.current).toBe(1500);
    expect(ws?.percent).toBe(75);
    expect(ws?.remaining).toBe(500);
    expect(ws?.reached).toBe(false);
  });

  it("达到目标：reached=true 且剩余为 0", () => {
    const stats = statsOf({ han: 0, nonWhitespace: 2500, withPunctuation: 0 });
    const progress = computeGoalProgress(stats, setupOf({ totalWordGoal: 2000 }));
    expect(progress?.reached).toBe(true);
    expect(progress?.remaining).toBe(0);
    expect(progress?.percent).toBe(100);
  });

  it("无总字数目标：返回 null", () => {
    expect(computeGoalProgress(statsOf({ han: 1, nonWhitespace: 1, withPunctuation: 1 }), setupOf())).toBeNull();
  });
});

describe("stats-calculator 本地日/周边界", () => {
  it("localDateKey 使用本地日历而非 UTC 截断", () => {
    // 固定 UTC 时间，再模拟东八区的本地日历 getter，避免测试依赖 runner 时区。
    const local = new Date("2026-08-13T16:30:00.000Z");
    vi.spyOn(local, "getFullYear").mockReturnValue(2026);
    vi.spyOn(local, "getMonth").mockReturnValue(7);
    vi.spyOn(local, "getDate").mockReturnValue(14);
    expect(localDateKey(local)).toBe("2026-08-14");
    expect(local.toISOString().slice(0, 10)).toBe("2026-08-13");
  });

  it("startOfLocalWeek 返回本地周一 0 点（周五 → 本周一）", () => {
    const monday = startOfLocalWeek(now);
    expect(monday.getDay()).toBe(1);
    expect(monday.getHours()).toBe(0);
    expect(localDateKey(monday)).toBe("2026-08-10");
  });

  it("今日净增：取 daily 中本地今日键", () => {
    const daily = [
      { date: "2026-08-13", netChars: 100, activeSeconds: 600 },
      { date: "2026-08-14", netChars: 250, activeSeconds: 1800 }
    ];
    const stats = statsOf({ han: 0, nonWhitespace: 0, withPunctuation: 0 }, daily);
    expect(computeDailyNetChars(stats, now)).toBe(250);
    expect(computeDailyActiveSeconds(stats, now)).toBe(1800);
  });

  it("本周净增：本地周一边界聚合，不含上周数据", () => {
    const daily = [
      { date: "2026-08-09", netChars: 999, activeSeconds: 999 }, // 上周日（周六？08-09 是周日）
      { date: "2026-08-10", netChars: 50, activeSeconds: 600 }, // 周一
      { date: "2026-08-12", netChars: -20, activeSeconds: 300 },
      { date: "2026-08-14", netChars: 70, activeSeconds: 900 }
    ];
    const stats = statsOf({ han: 0, nonWhitespace: 0, withPunctuation: 0 }, daily);
    const week = computeWeekSummary(stats, now);
    expect(week.mondayKey).toBe("2026-08-10");
    expect(week.todayKey).toBe("2026-08-14");
    expect(week.netChars).toBe(100);
    expect(week.activeSeconds).toBe(1800);
  });

  it("周跨月边界正确（周一在月末）", () => {
    // 2026-09-01 是周二；本周一为 2026-08-31。
    const tuesday = new Date(2026, 8, 1, 12, 0, 0);
    const monday = startOfLocalWeek(tuesday);
    expect(localDateKey(monday)).toBe("2026-08-31");
  });
});

describe("stats-calculator 目标日期与更新日", () => {
  it("目标日期倒计时：剩余字数与每日所需速度", () => {
    const stats = statsOf({ han: 0, nonWhitespace: 1500, withPunctuation: 0 });
    const setup = setupOf({ totalWordGoal: 2500, targetDate: "2026-08-17" });
    // now=08-14，目标 08-17：daysLeft=3（含今天）。
    const deadline = computeGoalDeadline(stats, setup, "nonWhitespace", now);
    expect(deadline?.remaining).toBe(1000);
    expect(deadline?.daysLeft).toBe(3);
    expect(deadline?.perDayNeeded).toBe(334);
  });

  it("目标日期已过：daysLeft=0，所需速度为剩余字数", () => {
    const stats = statsOf({ han: 0, nonWhitespace: 500, withPunctuation: 0 });
    const setup = setupOf({ totalWordGoal: 1000, targetDate: "2026-08-01" });
    const deadline = computeGoalDeadline(stats, setup, "nonWhitespace", now);
    expect(deadline?.daysLeft).toBe(0);
    expect(deadline?.perDayNeeded).toBe(500);
  });

  it("无目标日期 / 已达成 / 无目标：返回 null", () => {
    expect(computeGoalDeadline(statsOf({ han: 0, nonWhitespace: 100, withPunctuation: 0 }), setupOf({ totalWordGoal: 200 }), "nonWhitespace", now)).toBeNull();
    expect(computeGoalDeadline(statsOf({ han: 0, nonWhitespace: 500, withPunctuation: 0 }), setupOf({ totalWordGoal: 1000, targetDate: "not-a-date" }), "nonWhitespace", now)).toBeNull();
    expect(computeGoalDeadline(statsOf({ han: 0, nonWhitespace: 500, withPunctuation: 0 }), setupOf({ targetDate: "2026-08-17" }), "nonWhitespace", now)).toBeNull();
  });

  it("每周更新日平均目标：周目标 ÷ 更新日数", () => {
    const setup = setupOf({ weeklyWordGoal: 7000, weeklyUpdateDays: [1, 4, 6] });
    const target = computeWeeklyUpdateDayTarget(setup);
    expect(target).toEqual({ weeklyGoal: 7000, days: 3, perUpdateDay: 2334 });
  });

  it("无周目标或更新日：返回 null", () => {
    expect(computeWeeklyUpdateDayTarget(setupOf({ weeklyWordGoal: 100 }))).toBeNull();
    expect(computeWeeklyUpdateDayTarget(setupOf({ weeklyUpdateDays: [1] }))).toBeNull();
  });
});
