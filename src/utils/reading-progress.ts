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
 * `textAnchor` 由 TXT/MD 阅读器提供（章节 + 章内比例的字符坐标），随滚动位置
 * 双写保存，恢复时用于抵消排版参数变化带来的像素漂移。
 * Pure function — no side effects, no DOM/store dependency.
 */
export function computeScrollLocation(
  metrics: ScrollMetrics | null | undefined,
  book: BookIdentity | null | undefined,
  textAnchor?: { chapterRef?: string; headingPath?: string[]; charOffset?: number }
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
    ...(textAnchor
      ? {
          text: {
            charOffset: textAnchor.charOffset,
            chapterRef: textAnchor.chapterRef,
            headingPath: textAnchor.headingPath
          }
        }
      : {}),
    sourceVersion: {
      fileSize: book.size
    },
    updatedAt: new Date().toISOString()
  };
}
