import { describe, expect, it } from "vitest";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import {
  buildBookStatusCount,
  buildDateRange,
  buildMobileStatsSummary,
  buildReadingStreak,
  buildReadingTrend,
  formatCompactDuration,
  formatFullDuration,
  getStatsPeriodTitle,
  getValidSessions,
  hasBookBeenRead,
  isBookCompleted,
  shiftStatsPeriodAnchor,
  statsPeriodLabels
} from "./statistics-helpers";

function makeBook(overrides: Partial<MobileBook> = {}): MobileBook {
  return {
    id: "book-1",
    title: "测试书",
    filePath: "books/test.txt",
    format: "txt",
    importedAt: "2024-01-01T00:00:00",
    updatedAt: "2024-01-01T00:00:00",
    size: 12000,
    revision: 1,
    deviceId: "d1",
    ...overrides
  } as MobileBook;
}

function makeSession(overrides: Partial<MobileSnapshot["sessions"][number]> = {}): MobileSnapshot["sessions"][number] {
  return {
    id: `session-${overrides.id ?? Math.random().toString(36).slice(2)}`,
    bookId: "book-1",
    filePath: "books/test.txt",
    format: "txt" as const,
    startAt: "2024-06-15T10:00:00",
    activeDurationMs: 600_000,
    durationMs: 600_000,
    idleDurationMs: 0,
    wallDurationMs: 600_000,
    startLocation: {
      format: "txt" as const,
      mode: "scroll" as const,
      progressPercent: 10,
      precision: "estimated" as const,
      updatedAt: "2024-06-15T10:00:00"
    },
    dateKey: "2024-06-15",
    status: "ended" as const,
    source: "manualOpen" as const,
    revision: 1,
    deviceId: "d1",
    createdAt: "2024-06-15T10:00:00",
    updatedAt: "2024-06-15T10:00:00",
    lastPersistAt: "2024-06-15T10:00:00",
    ...overrides
  };
}

function makeSnapshot(overrides: Partial<MobileSnapshot> = {}): MobileSnapshot {
  return {
    books: [],
    progress: [],
    sessions: [],
    notes: [],
    inspirations: [],
    highlights: [],
    tags: [],
    categories: [],
    shelves: [],
    syncAccounts: [],
    updatedAt: "2024-06-15T00:00:00",
    ...overrides
  };
}

describe("buildDateRange", () => {
  it("本周：周一至下周一", () => {
    const now = new Date(2024, 5, 19); // 2024-06-19 周三
    const range = buildDateRange("week", now);
    expect(range.start.getFullYear()).toBe(2024);
    expect(range.start.getMonth()).toBe(5);
    expect(range.start.getDate()).toBe(17); // 周一
    expect(range.end.getDate()).toBe(24); // 下周一
    expect(range.includes(new Date(2024, 5, 17))).toBe(true);
    expect(range.includes(new Date(2024, 5, 23))).toBe(true);
    expect(range.includes(new Date(2024, 5, 24))).toBe(false);
  });

  it("本月：1号到下月1号", () => {
    const now = new Date(2024, 5, 19);
    const range = buildDateRange("month", now);
    expect(range.start.getDate()).toBe(1);
    expect(range.end.getFullYear()).toBe(2024);
    expect(range.end.getMonth()).toBe(6);
    expect(range.end.getDate()).toBe(1);
  });

  it("本年：1月1日到明年1月1日", () => {
    const now = new Date(2024, 5, 19);
    const range = buildDateRange("year", now);
    expect(range.start.getMonth()).toBe(0);
    expect(range.start.getDate()).toBe(1);
    expect(range.end.getFullYear()).toBe(2025);
    expect(range.end.getMonth()).toBe(0);
    expect(range.end.getDate()).toBe(1);
  });

  it("累计：从 epoch 到今天结束", () => {
    const now = new Date(2024, 5, 19);
    const range = buildDateRange("total", now);
    expect(range.start.getTime()).toBe(new Date(0).getTime());
    expect(range.end.getDate()).toBe(20);
  });

  it("跨月月份边界正确", () => {
    const now = new Date(2024, 0, 31); // 1月31日
    const range = buildDateRange("month", now);
    expect(range.start.getDate()).toBe(1);
    expect(range.end.getMonth()).toBe(1);
    expect(range.end.getDate()).toBe(1);
  });

  it("闰年2月包含29天", () => {
    const now = new Date(2024, 1, 15);
    const range = buildDateRange("month", now);
    expect(range.includes(new Date(2024, 1, 29))).toBe(true);
    expect(range.end.getMonth()).toBe(2);
    expect(range.end.getDate()).toBe(1);
  });
});

describe("session duration filtering", () => {
  it("负数、NaN、异常大会话被过滤", () => {
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: -100, durationMs: 100 }),
        makeSession({ activeDurationMs: NaN, durationMs: NaN }),
        makeSession({ activeDurationMs: 25 * 60 * 60 * 1000 }), // >24h
        makeSession({ activeDurationMs: 0, durationMs: 0 }),
        makeSession({ activeDurationMs: 300_000 })
      ]
    });
    const valid = getValidSessions(snapshot);
    expect(valid.length).toBe(1);
    expect(valid[0].activeDurationMs).toBe(300_000);
  });

  it("优先使用 activeDurationMs", () => {
    const snapshot = makeSnapshot({
      sessions: [makeSession({ activeDurationMs: 300_000, durationMs: 600_000 })]
    });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.totalReadingMs).toBe(300_000);
  });

  it("activeDurationMs 为 0 时回退到 durationMs", () => {
    const snapshot = makeSnapshot({
      sessions: [makeSession({ activeDurationMs: 0, durationMs: 600_000 })]
    });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.totalReadingMs).toBe(600_000);
  });

  it("重复 session id 只计算一次", () => {
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ id: "dup", activeDurationMs: 600_000 }),
        makeSession({ id: "dup", activeDurationMs: 300_000 })
      ]
    });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.totalReadingMs).toBe(600_000);
    expect(summary.sessionCount).toBe(1);
  });
});

describe("reading days and trend", () => {
  it("同一天多次阅读只算 1 天", () => {
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 300_000, startAt: "2024-06-15T10:00:00", dateKey: "2024-06-15" }),
        makeSession({ activeDurationMs: 400_000, startAt: "2024-06-15T20:00:00", dateKey: "2024-06-15" }),
        makeSession({ activeDurationMs: 500_000, startAt: "2024-06-16T10:00:00", dateKey: "2024-06-16" })
      ]
    });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.readingDays).toBe(2);
    expect(summary.totalReadingMs).toBe(1_200_000);
  });

  it("本周趋势包含 7 天", () => {
    const now = new Date(2024, 5, 19);
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 600_000, startAt: "2024-06-17T10:00:00", dateKey: "2024-06-17" }),
        makeSession({ activeDurationMs: 300_000, startAt: "2024-06-18T10:00:00", dateKey: "2024-06-18" })
      ]
    });
    const trend = buildReadingTrend(snapshot, "week", now);
    expect(trend.length).toBe(7);
    const mon = trend.find((i) => i.dateKey === "2024-06-17");
    const tue = trend.find((i) => i.dateKey === "2024-06-18");
    expect(mon?.durationMs).toBe(600_000);
    expect(tue?.durationMs).toBe(300_000);
    expect(trend.reduce((sum, i) => sum + i.durationMs, 0)).toBe(900_000);
  });

  it("本月趋势按周聚合", () => {
    const now = new Date(2024, 5, 19);
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 600_000, startAt: "2024-06-03T10:00:00", dateKey: "2024-06-03" }),
        makeSession({ activeDurationMs: 300_000, startAt: "2024-06-10T10:00:00", dateKey: "2024-06-10" })
      ]
    });
    const trend = buildReadingTrend(snapshot, "month", now);
    expect(trend.length).toBeGreaterThanOrEqual(2);
    expect(trend.reduce((sum, i) => sum + i.durationMs, 0)).toBe(900_000);
  });

  it("本年趋势包含 12 个月", () => {
    const now = new Date(2024, 5, 19);
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 600_000, startAt: "2024-01-15T10:00:00", dateKey: "2024-01-15" }),
        makeSession({ activeDurationMs: 300_000, startAt: "2024-03-15T10:00:00", dateKey: "2024-03-15" })
      ]
    });
    const trend = buildReadingTrend(snapshot, "year", now);
    expect(trend.length).toBe(12);
    expect(trend.reduce((sum, i) => sum + i.durationMs, 0)).toBe(900_000);
  });

  it("累计趋势不从 1970 年开始", () => {
    const now = new Date(2024, 5, 19);
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 600_000, startAt: "2024-01-15T10:00:00", dateKey: "2024-01-15" }),
        makeSession({ activeDurationMs: 300_000, startAt: "2024-03-15T10:00:00", dateKey: "2024-03-15" })
      ]
    });
    const trend = buildReadingTrend(snapshot, "total", now);
    const firstLabel = trend[0]?.label;
    expect(firstLabel).not.toContain("1970");
    expect(trend.length).toBeLessThanOrEqual(6);
    expect(trend.reduce((sum, i) => sum + i.durationMs, 0)).toBe(900_000);
  });
});

describe("book status counting", () => {
  it("排除同步占位、导入失败、正文缺失书籍", () => {
    const snapshot = makeSnapshot({
      books: [
        makeBook({ id: "b1", size: 12000, contentHash: "abc" }),
        makeBook({ id: "b2", origin: "sync_placeholder" }),
        makeBook({ id: "b3", contentStatus: "failed" }),
        makeBook({ id: "b4", contentStatus: "missing" }),
        makeBook({ id: "b5", contentStatus: "downloading" })
      ]
    });
    const status = buildBookStatusCount(snapshot);
    expect(status.total).toBe(5);
    expect(status.unreadable).toBe(4);
    expect(status.unread).toBe(1);
  });

  it("进度大于 0 才算读过", () => {
    const snapshot = makeSnapshot({
      books: [makeBook({ id: "b1" }), makeBook({ id: "b2" })],
      progress: [
        {
          bookId: "b1",
          filePath: "books/test.txt",
          format: "txt",
          currentLocation: { format: "txt", mode: "scroll", progressPercent: 20, precision: "estimated", updatedAt: "2024-06-15T00:00:00" },
          progressPercent: 20,
          lastReadAt: "2024-06-15T00:00:00",
          totalReadingTimeMs: 0,
          completionState: "reading",
          revision: 1,
          deviceId: "d1",
          updatedAt: "2024-06-15T00:00:00"
        }
      ]
    });
    expect(hasBookBeenRead(snapshot, "b1")).toBe(true);
    expect(hasBookBeenRead(snapshot, "b2")).toBe(false);
  });

  it("explicit completed 字段优先", () => {
    const snapshot = makeSnapshot({
      books: [makeBook({ id: "b1" }), makeBook({ id: "b2" })],
      progress: [
        {
          bookId: "b1",
          filePath: "books/test.txt",
          format: "txt",
          currentLocation: { format: "txt", mode: "scroll", progressPercent: 50, precision: "estimated", updatedAt: "2024-06-15T00:00:00" },
          progressPercent: 50,
          lastReadAt: "2024-06-15T00:00:00",
          totalReadingTimeMs: 0,
          completionState: "completed",
          revision: 1,
          deviceId: "d1",
          updatedAt: "2024-06-15T00:00:00"
        },
        {
          bookId: "b2",
          filePath: "books/test.txt",
          format: "txt",
          currentLocation: { format: "txt", mode: "scroll", progressPercent: 100, precision: "estimated", updatedAt: "2024-06-15T00:00:00" },
          progressPercent: 100,
          lastReadAt: "2024-06-15T00:00:00",
          totalReadingTimeMs: 0,
          completionState: "reading",
          revision: 1,
          deviceId: "d1",
          updatedAt: "2024-06-15T00:00:00"
        }
      ]
    });
    expect(isBookCompleted(snapshot, "b1")).toBe(true);
    expect(isBookCompleted(snapshot, "b2")).toBe(true);
    const status = buildBookStatusCount(snapshot);
    expect(status.completed).toBe(2);
    expect(status.reading).toBe(0);
  });
});

describe("summary aggregation", () => {
  it("无数据时返回 0 结构", () => {
    const summary = buildMobileStatsSummary(makeSnapshot(), "total");
    expect(summary.totalReadingMs).toBe(0);
    expect(summary.readingDays).toBe(0);
    expect(summary.readBooks).toBe(0);
    expect(summary.completed).toBe(0);
    expect(summary.words).toBe(0);
    expect(summary.speed).toBe(0);
    expect(summary.sessionCount).toBe(0);
    expect(summary.noteCount).toBe(0);
    expect(summary.inspirationCount).toBe(0);
  });

  it("有书籍但未阅读时 readBooks 为 0", () => {
    const snapshot = makeSnapshot({ books: [makeBook()] });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.totalDisplayableBooks).toBe(1);
    expect(summary.readBooks).toBe(0);
  });

  it("按时间范围过滤", () => {
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 600_000, startAt: "2024-06-10T10:00:00", dateKey: "2024-06-10" }),
        makeSession({ activeDurationMs: 300_000, startAt: "2024-06-17T10:00:00", dateKey: "2024-06-17" })
      ]
    });
    const weekSummary = buildMobileStatsSummary(snapshot, "week", new Date(2024, 5, 19));
    expect(weekSummary.totalReadingMs).toBe(300_000);
    expect(weekSummary.sessionCount).toBe(1);
    const totalSummary = buildMobileStatsSummary(snapshot, "total", new Date(2024, 5, 19));
    expect(totalSummary.totalReadingMs).toBe(900_000);
  });

  it("笔记和灵感按创建时间过滤", () => {
    const snapshot = makeSnapshot({
      notes: [
        { id: "n1", title: "", body: "", createdAt: "2024-06-17T10:00:00", updatedAt: "2024-06-17T10:00:00", revision: 1, deviceId: "d1" },
        { id: "n2", title: "", body: "", createdAt: "2024-05-01T10:00:00", updatedAt: "2024-05-01T10:00:00", revision: 1, deviceId: "d1" }
      ],
      inspirations: [
        { id: "i1", title: "", body: "", createdAt: "2024-06-18T10:00:00", updatedAt: "2024-06-18T10:00:00", revision: 1, deviceId: "d1" }
      ] as MobileSnapshot["inspirations"]
    });
    const summary = buildMobileStatsSummary(snapshot, "week", new Date(2024, 5, 19));
    expect(summary.noteCount).toBe(1);
    expect(summary.inspirationCount).toBe(1);
  });
});

describe("duration formatting", () => {
  it("formatCompactDuration 边界", () => {
    expect(formatCompactDuration(0)).toBe("0 分钟");
    expect(formatCompactDuration(-100)).toBe("0 分钟");
    expect(formatCompactDuration(NaN)).toBe("0 分钟");
    expect(formatCompactDuration(30_000)).toBe("1 分钟");
    expect(formatCompactDuration(90_000)).toBe("2 分钟");
    expect(formatCompactDuration(60 * 60_000)).toBe("1 小时");
    expect(formatCompactDuration(90 * 60_000)).toBe("1.5 小时");
    expect(formatCompactDuration(119 * 60_000)).toBe("2 小时");
  });

  it("formatFullDuration 完整格式", () => {
    expect(formatFullDuration(90 * 60_000)).toBe("1 小时 30 分钟");
    expect(formatFullDuration(119 * 60_000)).toBe("1 小时 59 分钟");
  });
});

describe("reading streak", () => {
  it("当前连续与最长连续", () => {
    const snapshot = makeSnapshot({
      sessions: [
        makeSession({ activeDurationMs: 100_000, startAt: "2024-06-19T10:00:00", dateKey: "2024-06-19" }),
        makeSession({ activeDurationMs: 100_000, startAt: "2024-06-18T10:00:00", dateKey: "2024-06-18" }),
        makeSession({ activeDurationMs: 100_000, startAt: "2024-06-16T10:00:00", dateKey: "2024-06-16" }),
        makeSession({ activeDurationMs: 100_000, startAt: "2024-06-15T10:00:00", dateKey: "2024-06-15" })
      ]
    });
    const streak = buildReadingStreak(snapshot, new Date(2024, 5, 19));
    expect(streak.current).toBe(2);
    expect(streak.longest).toBe(2);
  });
});

describe("period navigation", () => {
  it("shiftStatsPeriodAnchor 不溢出", () => {
    const anchor = new Date(2024, 0, 31);
    const prev = shiftStatsPeriodAnchor(anchor, "month", -1);
    expect(prev.getMonth()).toBe(11);
    expect(prev.getFullYear()).toBe(2023);
  });

  it("getStatsPeriodTitle 显示周范围", () => {
    const title = getStatsPeriodTitle("week", new Date(2024, 5, 19));
    expect(title).toContain("6月17日");
    expect(title).toContain("6月23日");
  });
});

describe("old data compatibility", () => {
  it("progress 为字符串或 NaN 时不崩溃", () => {
    const snapshot = makeSnapshot({
      books: [makeBook({ id: "b1" })],
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      progress: [{ bookId: "b1", progressPercent: "100" as any, completionState: "reading" } as MobileSnapshot["progress"][number]]
    });
    expect(() => buildBookStatusCount(snapshot)).not.toThrow();
  });

  it("缺少 dateKey 时使用 startAt", () => {
    const snapshot = makeSnapshot({
      sessions: [makeSession({ dateKey: undefined, startAt: "2024-06-15T10:00:00" })]
    });
    const summary = buildMobileStatsSummary(snapshot, "total");
    expect(summary.readingDays).toBe(1);
  });
});

describe("performance guardrails", () => {
  it("200 本书、数千条会话聚合不慢", () => {
    const books: MobileBook[] = [];
    for (let i = 0; i < 200; i++) {
      books.push(makeBook({ id: `b${i}`, size: 10000 + i }));
    }
    const sessions = [];
    const base = new Date(2024, 5, 1);
    for (let i = 0; i < 3000; i++) {
      const day = new Date(base);
      day.setDate(day.getDate() + (i % 30));
      sessions.push(
        makeSession({
          id: `s${i}`,
          bookId: `b${i % 200}`,
          activeDurationMs: 60_000,
          startAt: day.toISOString(),
          dateKey: `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`
        })
      );
    }
    const snapshot = makeSnapshot({ books, sessions });
    const start = performance.now();
    buildMobileStatsSummary(snapshot, "month", new Date(2024, 5, 15));
    buildReadingTrend(snapshot, "month", new Date(2024, 5, 15));
    const elapsed = performance.now() - start;
    expect(elapsed).toBeLessThan(500);
  });
});

describe("statsPeriodLabels", () => {
  it("包含四个可切换范围", () => {
    expect(statsPeriodLabels.week).toBe("本周");
    expect(statsPeriodLabels.month).toBe("本月");
    expect(statsPeriodLabels.year).toBe("本年");
    expect(statsPeriodLabels.total).toBe("累计");
  });
});
