import type { ReadingLocation } from "@/types/library";

/**
 * Scroll metrics used for progress calculation.
 * Matches the subset of HTMLDivElement properties needed.
 */
export interface ScrollMetrics {
  scrollTop: number;
  scrollHeight: number;
  clientHeight: number;
}

/**
 * Book identity needed for building a reading location.
 */
export interface BookIdentity {
  format: "txt" | "md" | "epub";
  size: number;
}

/**
 * Build a ReadingLocation from scroll metrics and book identity.
 * Pure function — no side effects, no DOM/store dependency.
 */
export function computeScrollLocation(
  metrics: ScrollMetrics | null | undefined,
  book: BookIdentity | null | undefined
): ReadingLocation | undefined {
  if (!book || !metrics) return undefined;
  const maxScroll = Math.max(1, metrics.scrollHeight - metrics.clientHeight);
  const progressPercent = Math.min(1, Math.max(0, metrics.scrollTop / maxScroll));
  return {
    format: book.format,
    mode: "scroll",
    progressPercent,
    precision: "estimated",
    scroll: {
      scrollTop: metrics.scrollTop,
      scrollHeight: metrics.scrollHeight,
      containerHeight: metrics.clientHeight
    },
    sourceVersion: {
      fileSize: book.size
    },
    updatedAt: new Date().toISOString()
  };
}
