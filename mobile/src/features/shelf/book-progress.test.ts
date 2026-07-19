import { describe, expect, it } from "vitest";
import type { MobileSnapshot } from "../../types/mobile";
import { bookReadingTimeMs, totalReadingTimeMs } from "./book-progress";

function snapshotWithTimes(progressMs: number, sessionMs: number): MobileSnapshot {
  return {
    books: [{ id: "book-1" }],
    progress: [{ bookId: "book-1", totalReadingTimeMs: progressMs }],
    sessions: [{ bookId: "book-1", activeDurationMs: sessionMs }],
    inspirations: [],
    notes: [],
    highlights: [],
    tags: [],
    categories: [],
    shelves: [],
    syncAccounts: [],
    updatedAt: "2026-07-18T00:00:00.000Z"
  } as unknown as MobileSnapshot;
}

describe("bookReadingTimeMs", () => {
  it("recovers old data where sessions exist but progress time stayed at zero", () => {
    expect(bookReadingTimeMs(snapshotWithTimes(0, 89_000), "book-1")).toBe(89_000);
  });

  it("does not double count when new data persists both progress and sessions", () => {
    const snapshot = snapshotWithTimes(89_000, 89_000);
    expect(bookReadingTimeMs(snapshot, "book-1")).toBe(89_000);
    expect(totalReadingTimeMs(snapshot)).toBe(89_000);
  });
});
