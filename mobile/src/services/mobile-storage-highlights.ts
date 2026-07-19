import type { HighlightColor, LibraryBook, ReaderLocatorV2 } from "../../../src/types/library";
import type { MobileHighlight, MobileSnapshot } from "../types/mobile";
import { getMobileDeviceId, nowIso, saveMobileSnapshot } from "./mobile-storage-core";

export interface AddMobileHighlightInput {
  book: LibraryBook;
  text: string;
  color?: HighlightColor;
  note?: string;
  chapterTitle?: string;
  cfiRange?: string;
  charOffset?: number;
  charLength?: number;
  progressPercent?: number;
  locator?: ReaderLocatorV2;
}

export async function addMobileHighlight(
  snapshot: MobileSnapshot,
  input: AddMobileHighlightInput
): Promise<{ snapshot: MobileSnapshot; highlight: MobileHighlight }> {
  const createdAt = nowIso();
  const highlight: MobileHighlight = {
    id: `mobile-hl-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
    bookId: input.book.id,
    cfiRange: input.cfiRange,
    charOffset: input.charOffset,
    charLength: input.charLength,
    text: input.text.slice(0, 1200),
    color: input.color ?? "yellow",
    note: input.note,
    chapterTitle: input.chapterTitle,
    progressPercent: input.progressPercent,
    locator: input.locator,
    createdAt,
    updatedAt: createdAt
  };
  const next: MobileSnapshot = {
    ...snapshot,
    highlights: [highlight, ...snapshot.highlights],
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return { snapshot: next, highlight };
}

export async function updateMobileHighlight(
  snapshot: MobileSnapshot,
  highlightId: string,
  patch: Partial<Pick<MobileHighlight, "color" | "note" | "text">>
): Promise<MobileSnapshot> {
  const next: MobileSnapshot = {
    ...snapshot,
    highlights: snapshot.highlights.map((item) =>
      item.id === highlightId
        ? { ...item, ...patch, updatedAt: nowIso() }
        : item
    ),
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

export async function deleteMobileHighlight(
  snapshot: MobileSnapshot,
  highlightId: string
): Promise<MobileSnapshot> {
  const next: MobileSnapshot = {
    ...snapshot,
    highlights: snapshot.highlights.filter((item) => item.id !== highlightId),
    updatedAt: nowIso()
  };
  await saveMobileSnapshot(next);
  return next;
}

/** 导出指定书籍的高亮/笔记/书签为纯文本书摘 */
export function exportBookExcerpts(
  snapshot: MobileSnapshot,
  bookId: string
): string {
  const book = snapshot.books.find((item) => item.id === bookId);
  const highlights = snapshot.highlights
    .filter((item) => item.bookId === bookId)
    .sort((a, b) => (a.charOffset ?? 0) - (b.charOffset ?? 0));
  const notes = snapshot.notes
    .filter((item) => item.bookId === bookId && item.kind === "note")
    .sort((a, b) => (a.progressPercent ?? 0) - (b.progressPercent ?? 0));
  const bookmarks = snapshot.notes
    .filter((item) => item.bookId === bookId && item.kind === "bookmark")
    .sort((a, b) => (a.progressPercent ?? 0) - (b.progressPercent ?? 0));

  const lines: string[] = [];
  lines.push(`《${book?.title ?? "未知书籍"}》书摘`);
  lines.push(`作者：${book?.author || "未知"}`);
  lines.push(`导出时间：${new Date().toLocaleString("zh-CN")}`);
  lines.push("");

  if (highlights.length) {
    lines.push("【高亮】");
    highlights.forEach((item, index) => {
      lines.push(`${index + 1}. [${item.color}] ${item.text}`);
      if (item.chapterTitle) lines.push(`   章节：${item.chapterTitle}`);
      if (item.note) lines.push(`   批注：${item.note}`);
      lines.push("");
    });
  }

  if (notes.length) {
    lines.push("【笔记】");
    notes.forEach((item, index) => {
      lines.push(`${index + 1}. ${item.body}`);
      if (item.chapterTitle) lines.push(`   章节：${item.chapterTitle}`);
      if (item.excerpt) lines.push(`   摘录：${item.excerpt}`);
      lines.push("");
    });
  }

  if (bookmarks.length) {
    lines.push("【书签】");
    bookmarks.forEach((item, index) => {
      lines.push(`${index + 1}. ${item.title}${item.progressPercent ? ` (${item.progressPercent.toFixed(1)}%)` : ""}`);
      lines.push("");
    });
  }

  if (!highlights.length && !notes.length && !bookmarks.length) {
    lines.push("暂无高亮、笔记或书签。");
  }

  return lines.join("\n");
}
