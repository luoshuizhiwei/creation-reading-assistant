import type { ReadingLocation } from "../../../../src/types/library";
import type { MobileReaderDocument } from "../../reader/mobile-reader";
import type { MobileReaderSettings } from "../../types/mobile";

export function getPagedStep(element: HTMLElement): number {
  const contentEl = element.querySelector(".reader-content") as HTMLElement | null;
  const computed = contentEl ? window.getComputedStyle(contentEl) : null;
  const columnWidth = computed ? parseFloat(computed.columnWidth || "0") : 0;
  const columnGap = computed ? parseFloat(computed.columnGap || "0") : 0;
  if (Number.isFinite(columnWidth) && columnWidth > 0) {
    return Math.max(1, columnWidth + (Number.isFinite(columnGap) ? columnGap : 0));
  }
  return Math.max(1, element.clientWidth);
}

export const READER_PARAGRAPH_SELECTOR = "p, li, blockquote, td, pre, h1, h2, h3, h4, h5, h6, .reader-preformatted";

function getReaderParagraphElements(element: HTMLElement): HTMLElement[] {
  const contentEl = element.querySelector(".reader-content") as HTMLElement | null;
  if (!contentEl) return [];
  return Array.from(contentEl.querySelectorAll(READER_PARAGRAPH_SELECTOR)) as HTMLElement[];
}

export interface ReaderParagraphPosition {
  paragraphIndex: number;
  scrollRatioInParagraph: number;
}

export function getCurrentParagraphPosition(
  element: HTMLElement,
  mode: MobileReaderSettings["readerMode"]
): ReaderParagraphPosition | undefined {
  if (mode !== "scroll") return undefined;
  const paragraphs = getReaderParagraphElements(element);
  if (!paragraphs.length) return undefined;
  const viewportTop = element.scrollTop;
  for (let index = 0; index < paragraphs.length; index += 1) {
    const p = paragraphs[index];
    const bottom = p.offsetTop + p.offsetHeight;
    if (bottom > viewportTop + 1) {
      const ratio = p.offsetHeight > 0 ? Math.min(1, Math.max(0, (viewportTop - p.offsetTop) / p.offsetHeight)) : 0;
      return { paragraphIndex: index, scrollRatioInParagraph: ratio };
    }
  }
  return { paragraphIndex: paragraphs.length - 1, scrollRatioInParagraph: 0 };
}

export function getReaderParagraphIndexFromElement(element: HTMLElement, target: HTMLElement): number {
  const paragraphs = getReaderParagraphElements(element);
  return paragraphs.findIndex((p) => p === target);
}

export function scrollReaderToParagraph(
  element: HTMLElement,
  paragraphIndex: number,
  mode: MobileReaderSettings["readerMode"]
): void {
  const paragraphs = getReaderParagraphElements(element);
  const p = paragraphs[paragraphIndex];
  if (!p) return;
  if (mode === "scroll") {
    element.scrollTo({ top: Math.max(0, p.offsetTop - 20), behavior: "auto" });
    return;
  }
  const pageStep = getPagedStep(element);
  const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
  const rawLeft = Math.max(0, p.offsetLeft - 12);
  const page = Math.round(rawLeft / pageStep);
  element.scrollTo({ left: Math.min(maxScroll, Math.max(0, page * pageStep)), behavior: "auto" });
}

export function getCurrentPageRatio(element: HTMLElement): number {
  const pageStep = getPagedStep(element);
  const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
  if (maxScroll <= 0 || pageStep <= 0) return 0;
  const page = Math.max(0, Math.round(element.scrollLeft / pageStep));
  const pageStart = page * pageStep;
  const ratio = (element.scrollLeft - pageStart) / pageStep;
  return Math.min(1, Math.max(0, ratio));
}

export function applyReaderExactLocationOffsets(
  element: HTMLElement,
  mode: MobileReaderSettings["readerMode"],
  location?: ReadingLocation
): boolean {
  if (!location) return false;
  if (mode === "scroll") {
    const paragraphIndex = location.paragraphIndex;
    const scrollRatio = location.scrollRatioInParagraph;
    if (typeof paragraphIndex !== "number" || typeof scrollRatio !== "number") return false;
    const paragraphs = getReaderParagraphElements(element);
    const p = paragraphs[paragraphIndex];
    if (!p) return false;
    const contentEl = element.querySelector(".reader-content") as HTMLElement | null;
    const contentRect = contentEl ? contentEl.getBoundingClientRect() : element.getBoundingClientRect();
    const pRect = p.getBoundingClientRect();
    const relativeTop = pRect.top - contentRect.top;
    const targetTop = relativeTop + pRect.height * scrollRatio;
    element.scrollTop = Math.max(0, Math.min(targetTop, element.scrollHeight - element.clientHeight));
    return true;
  }
  if (mode === "paged") {
    const pageIndex = location.page?.pageIndex;
    const pageRatio = location.pageRatio;
    if (typeof pageIndex !== "number" || typeof pageRatio !== "number") return false;
    const pageStep = getPagedStep(element);
    const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
    const targetLeft = Math.min(maxScroll, Math.max(0, pageIndex * pageStep + pageStep * pageRatio));
    element.scrollLeft = targetLeft;
    return true;
  }
  return false;
}

export function calculateReaderProgress(element: HTMLElement, mode: MobileReaderSettings["readerMode"]): number {
  // paged 模式基于整页计算进度，避免保存半页/夹缝位置
  if (mode === "paged") {
    const pageStep = getPagedStep(element);
    const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
    if (maxScroll <= 0) return 0;
    const page = Math.round(element.scrollLeft / pageStep);
    return Math.min(100, Math.max(0, (page * pageStep / maxScroll) * 100));
  }
  const scrollable = Math.max(1, element.scrollHeight - element.clientHeight);
  const current = element.scrollTop;
  return Math.min(100, Math.max(0, (current / scrollable) * 100));
}

export function readerBookProgressFromLocal(document: MobileReaderDocument, localProgress: number): number {
  // renderAll 模式：所有章节已拼成连续 HTML，localProgress 就是全书进度
  if (document.renderAllChapters) {
    return Math.min(100, Math.max(0, localProgress));
  }
  const total = document.totalChapters ?? document.toc.length;
  if (!total || total <= 1) return Math.min(100, Math.max(0, localProgress));
  const index = Math.min(total - 1, Math.max(0, document.currentTocIndex ?? 0));
  return Math.min(100, Math.max(0, ((index + Math.min(100, Math.max(0, localProgress)) / 100) / total) * 100));
}

export function readerLocalProgressFromBook(document: MobileReaderDocument, bookProgress: number): number {
  // renderAll 模式：直接用全书进度定位连续 HTML
  if (document.renderAllChapters) {
    return Math.min(100, Math.max(0, bookProgress));
  }
  const total = document.totalChapters ?? document.toc.length;
  if (!total || total <= 1) return Math.min(100, Math.max(0, bookProgress));
  const index = Math.min(total - 1, Math.max(0, document.currentTocIndex ?? 0));
  const raw = (Math.min(100, Math.max(0, bookProgress)) / 100) * total - index;
  return Math.min(100, Math.max(0, raw * 100));
}

function snapPagedScrollLeft(element: HTMLElement, rawLeft: number): number {
  const pageStep = getPagedStep(element);
  const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
  const page = Math.round(rawLeft / pageStep);
  return Math.min(maxScroll, Math.max(0, page * pageStep));
}

export function scrollReaderToPercent(
  element: HTMLElement,
  progressPercent: number,
  mode: MobileReaderSettings["readerMode"],
  behavior: ScrollBehavior = "smooth"
): void {
  const bounded = Math.min(100, Math.max(0, progressPercent));
  if (mode === "paged") {
    const scrollable = Math.max(0, element.scrollWidth - element.clientWidth);
    const targetLeft = snapPagedScrollLeft(element, (scrollable * bounded) / 100);
    if (behavior === "auto") {
      element.scrollLeft = targetLeft;
      element.scrollTop = 0;
    } else {
      element.scrollTo({ left: targetLeft, behavior });
    }
    return;
  }
  const scrollable = Math.max(0, element.scrollHeight - element.clientHeight);
  const targetTop = (scrollable * bounded) / 100;
  if (behavior === "auto") {
    element.scrollTop = targetTop;
    element.scrollLeft = 0;
  } else {
    element.scrollTo({ top: targetTop, behavior });
  }
}

export function restoreReaderViewportAfterLayout(
  element: HTMLElement,
  progressPercent: number,
  mode: MobileReaderSettings["readerMode"],
  afterRestore: () => void
): () => void {
  let cancelled = false;
  const bounded = Math.min(100, Math.max(0, progressPercent));
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      if (cancelled) return;
      scrollReaderToPercent(element, bounded, mode, "auto");
      if (mode === "paged" && bounded <= 0.05) {
        element.scrollLeft = 0;
        element.scrollTop = 0;
      }
      afterRestore();
    });
  });
  return () => {
    cancelled = true;
  };
}

export function findCurrentChapter(
  document: MobileReaderDocument,
  root?: HTMLElement | null,
  mode: MobileReaderSettings["readerMode"] = "scroll"
): MobileReaderDocument["toc"][number] | undefined {
  if (!root || !document.toc.length) return document.toc[0];
  const marker = (mode === "paged" ? root.scrollLeft : root.scrollTop) + 96;
  let current = document.toc[0];
  for (const item of document.toc) {
    const element = root.querySelector<HTMLElement>(`#${item.id}`);
    const offset = mode === "paged" ? element?.offsetLeft : element?.offsetTop;
    if (element && offset !== undefined && offset <= marker) current = item;
  }
  return current;
}

/** 根据全局字符偏移找到对应章节的索引（跳过卷标等 level 0 的条目）。 */
export function readerTocIndexFromCharOffset(document: MobileReaderDocument, charOffset: number): number {
  const fullText = document.fullText;
  if (!fullText) return document.currentTocIndex ?? 0;
  const chapters = document.toc.filter((item) => item.level > 0);
  if (!chapters.length) return document.currentTocIndex ?? 0;
  for (let index = chapters.length - 1; index >= 0; index -= 1) {
    const item = chapters[index];
    if ((item.startOffset ?? 0) <= charOffset) return item.index ?? index;
  }
  return chapters[0].index ?? 0;
}

/** 计算全局字符偏移在当前章节内的进度百分比（0-100），用于精确恢复阅读位置。 */
export function readerChapterProgressFromCharOffset(document: MobileReaderDocument, charOffset: number): number {
  const fullText = document.fullText;
  if (!fullText) return 0;
  const chapterIndex = readerTocIndexFromCharOffset(document, charOffset);
  const chapter = document.toc[chapterIndex];
  if (!chapter) return 0;
  const chapterStart = chapter.startOffset ?? 0;
  const chapterEnd = Math.min(fullText.length, chapter.endOffset ?? fullText.length);
  const chapterLength = Math.max(1, chapterEnd - chapterStart);
  const offsetInChapter = Math.max(0, Math.min(chapterLength, charOffset - chapterStart));
  return (offsetInChapter / chapterLength) * 100;
}

/** 估算当前视口在当前章节内的字符偏移（相对全文）。 */
export function readerCharOffsetFromViewport(
  element: HTMLElement,
  document: MobileReaderDocument,
  mode: MobileReaderSettings["readerMode"],
  currentChapter?: MobileReaderDocument["toc"][number]
): number {
  const fullText = document.fullText;
  if (!fullText) return 0;
  const chapter = currentChapter ?? document.toc[document.currentTocIndex ?? 0] ?? document.toc[0];
  if (!chapter) return 0;
  const chapterStart = chapter.startOffset ?? 0;
  const chapterEnd = Math.min(fullText.length, chapter.endOffset ?? fullText.length);
  const chapterLength = Math.max(1, chapterEnd - chapterStart);
  let chapterRatio = 0;
  if (mode === "paged") {
    const pageStep = getPagedStep(element);
    const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
    const currentPage = maxScroll <= 0 ? 0 : Math.round(element.scrollLeft / pageStep);
    const totalPages = Math.max(1, Math.round(maxScroll / pageStep) + 1);
    chapterRatio = totalPages <= 1 ? 0 : currentPage / (totalPages - 1);
  } else {
    const maxScroll = Math.max(0, element.scrollHeight - element.clientHeight);
    chapterRatio = maxScroll <= 0 ? 0 : element.scrollTop / maxScroll;
  }
  return Math.min(fullText.length - 1, Math.max(0, Math.floor(chapterStart + chapterRatio * chapterLength)));
}

/** 为进度保存构建 ReadingLocation 附加字段（TXT/Markdown 内容锚点 + 页码）。 */
export function readerLocationExtrasFromViewport(
  element: HTMLElement,
  document: MobileReaderDocument,
  mode: MobileReaderSettings["readerMode"],
  currentChapter?: MobileReaderDocument["toc"][number]
): Partial<ReadingLocation> {
  const fullText = document.fullText;
  const chapter = currentChapter ?? document.toc[document.currentTocIndex ?? 0] ?? document.toc[0];
  const extras: Partial<ReadingLocation> = { mode: mode === "paged" ? "page" : "scroll", precision: "estimated" };
  const paragraphPosition = getCurrentParagraphPosition(element, mode);
  const pageRatio = mode === "paged" ? getCurrentPageRatio(element) : undefined;
  if (paragraphPosition) {
    extras.paragraphIndex = paragraphPosition.paragraphIndex;
    extras.scrollRatioInParagraph = paragraphPosition.scrollRatioInParagraph;
  }
  if (typeof pageRatio === "number") extras.pageRatio = pageRatio;
  if (fullText && chapter) {
    const charOffset = readerCharOffsetFromViewport(element, document, mode, chapter);
    extras.text = {
      charOffset,
      chapterRef: chapter.id,
      headingPath: [chapter.title],
      anchorText: fullText.slice(charOffset, charOffset + 80).replace(/\s+/g, " ").trim()
    };
  }
  if (mode === "paged") {
    const pageStep = getPagedStep(element);
    const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
    const currentPage = maxScroll <= 0 ? 0 : Math.round(element.scrollLeft / pageStep);
    const totalPages = Math.max(1, Math.round(maxScroll / pageStep) + 1);
    extras.page = { pageIndex: currentPage, pageCount: totalPages };
  } else {
    extras.scroll = {
      scrollTop: element.scrollTop,
      scrollHeight: element.scrollHeight,
      containerHeight: element.clientHeight
    };
  }
  return extras;
}
