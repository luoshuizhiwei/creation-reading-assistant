import type { ReaderLocator } from "./engine-v2/types";

/**
 * 判断两个跨内核定位点是否指向同一阅读位置。
 * 文本偏移优先；旧数据缺少精确偏移时才回退到章节与全书进度。
 */
export function isSameReaderPosition(
  left: ReaderLocator | null | undefined,
  right: ReaderLocator | null | undefined
): boolean {
  if (!left || !right || left.bookId !== right.bookId || left.format !== right.format) return false;
  if (left.chapterId && right.chapterId && left.chapterId !== right.chapterId) return false;
  if (left.textOffset !== undefined && right.textOffset !== undefined) {
    return Math.abs(left.textOffset - right.textOffset) <= 8;
  }
  if (left.progression === undefined || right.progression === undefined) return false;
  return Math.abs(left.progression - right.progression) <= 0.0015;
}

