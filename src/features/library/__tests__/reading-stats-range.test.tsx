// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { ReadingStatsPage } from "@/features/library/ReadingStatsPage";

vi.mock("@/services/reader-service", () => ({
  getReadingStats: vi.fn()
}));

import * as readerService from "@/services/reader-service";

const summary = {
  todayDurationMs: 0,
  last7DaysDurationMs: 0,
  last30DaysDurationMs: 0,
  totalDurationMs: 0,
  byBook: [],
  recentBooks: [],
  recentSessions: [],
  readingDaysCount: 0,
  averageSessionDurationMs: 0,
  daily: []
};

beforeEach(() => {
  vi.mocked(readerService.getReadingStats).mockResolvedValue(summary);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("阅读统计 · 范围切换", () => {
  // BD-147：范围按钮只靠底色表达选中，读屏器不知道当前统计的是哪一段。
  it("用 aria-pressed 暴露当前统计范围", async () => {
    render(<ReadingStatsPage />);
    await screen.findByText("阅读与沉淀节律");

    const group = screen.getByRole("group", { name: "统计范围" });
    expect(group).toBeTruthy();
    expect(screen.getByRole("button", { name: "本周" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "本月" }).getAttribute("aria-pressed")).toBe("false");

    fireEvent.click(screen.getByRole("button", { name: "本月" }));
    expect(screen.getByRole("button", { name: "本月" }).getAttribute("aria-pressed")).toBe("true");
    expect(screen.getByRole("button", { name: "本周" }).getAttribute("aria-pressed")).toBe("false");
  });
});
