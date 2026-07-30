import { describe, it, expect } from "vitest";
import { computeScrollLocation } from "@/utils/reading-progress";
import type { ScrollMetrics, BookIdentity } from "@/utils/reading-progress";

describe("computeScrollLocation", () => {
  const book: BookIdentity = { format: "txt", size: 102400 };

  it("returns undefined when metrics is null", () => {
    expect(computeScrollLocation(null, book)).toBeUndefined();
  });

  it("returns undefined when metrics is undefined", () => {
    expect(computeScrollLocation(undefined, book)).toBeUndefined();
  });

  it("returns undefined when book is null", () => {
    const metrics: ScrollMetrics = { scrollTop: 100, scrollHeight: 2000, clientHeight: 800 };
    expect(computeScrollLocation(metrics, null)).toBeUndefined();
  });

  it("returns undefined when book is undefined", () => {
    const metrics: ScrollMetrics = { scrollTop: 100, scrollHeight: 2000, clientHeight: 800 };
    expect(computeScrollLocation(metrics, undefined)).toBeUndefined();
  });

  it("computes 0% progress at top", () => {
    const metrics: ScrollMetrics = { scrollTop: 0, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(0);
    expect(result.format).toBe("txt");
    expect(result.mode).toBe("scroll");
    expect(result.precision).toBe("estimated");
  });

  it("computes 100% progress at bottom", () => {
    const metrics: ScrollMetrics = { scrollTop: 1200, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(1);
  });

  it("computes 50% progress at midpoint", () => {
    const metrics: ScrollMetrics = { scrollTop: 600, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(0.5);
  });

  it("clamps progress to 0 when scrollTop is negative", () => {
    const metrics: ScrollMetrics = { scrollTop: -50, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(0);
  });

  it("clamps progress to 1 when scrollTop exceeds max", () => {
    const metrics: ScrollMetrics = { scrollTop: 5000, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(1);
  });

  it("handles edge case where content fits container (scrollHeight == clientHeight)", () => {
    // maxScroll = max(1, 0) = 1, so scrollTop 0 => 0%
    const metrics: ScrollMetrics = { scrollTop: 0, scrollHeight: 800, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(0);
  });

  it("handles edge case where content smaller than container", () => {
    // scrollHeight < clientHeight: maxScroll = max(1, negative) = 1
    const metrics: ScrollMetrics = { scrollTop: 0, scrollHeight: 400, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.progressPercent).toBe(0);
  });

  it("preserves scroll snapshot in output", () => {
    const metrics: ScrollMetrics = { scrollTop: 300, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.scroll).toEqual({
      scrollTop: 300,
      scrollHeight: 2000,
      containerHeight: 800
    });
  });

  it("preserves book size in sourceVersion", () => {
    const metrics: ScrollMetrics = { scrollTop: 0, scrollHeight: 2000, clientHeight: 800 };
    const result = computeScrollLocation(metrics, book)!;
    expect(result.sourceVersion).toEqual({ fileSize: 102400 });
  });

  it("includes a valid ISO timestamp in updatedAt", () => {
    const metrics: ScrollMetrics = { scrollTop: 0, scrollHeight: 2000, clientHeight: 800 };
    const before = new Date().toISOString();
    const result = computeScrollLocation(metrics, book)!;
    const after = new Date().toISOString();
    expect(result.updatedAt >= before).toBe(true);
    expect(result.updatedAt <= after).toBe(true);
  });

  it("supports epub format", () => {
    const epubBook: BookIdentity = { format: "epub", size: 5242880 };
    const metrics: ScrollMetrics = { scrollTop: 500, scrollHeight: 3000, clientHeight: 1000 };
    const result = computeScrollLocation(metrics, epubBook)!;
    expect(result.format).toBe("epub");
    expect(result.progressPercent).toBe(0.25);
    expect(result.sourceVersion).toEqual({ fileSize: 5242880 });
  });

  it("supports md format", () => {
    const mdBook: BookIdentity = { format: "md", size: 8192 };
    const metrics: ScrollMetrics = { scrollTop: 200, scrollHeight: 1200, clientHeight: 800 };
    const result = computeScrollLocation(metrics, mdBook)!;
    expect(result.format).toBe("md");
    expect(result.progressPercent).toBe(0.5);
  });
});
