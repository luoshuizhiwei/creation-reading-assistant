import { formatBytes } from "../../utils/mobile-helpers";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import { isBookDownloaded } from "../shelf/book-status";

export const BOOK_CONTENT_STORAGE_KEY_PREFIX = "creation-reading-assistant-mobile-book-content:";

export interface StorageBreakdown {
  totalBooks: number;
  downloadedBooks: number;
  txtCount: number;
  mdCount: number;
  epubCount: number;
  indexBytes: number;
  cacheEntryCount: number;
  cacheBytes: number;
}

export function buildStorageBreakdown(snapshot: MobileSnapshot): StorageBreakdown {
  const totalBooks = snapshot.books.length;
  const downloadedBooks = snapshot.books.filter(isBookDownloaded).length;
  const txtCount = snapshot.books.filter((book) => book.format === "txt").length;
  const mdCount = snapshot.books.filter((book) => book.format === "md").length;
  const epubCount = snapshot.books.filter((book) => book.format === "epub").length;
  const indexBytes = snapshot.books.reduce((sum, book) => sum + (book.size ?? 0), 0);

  let cacheEntryCount = 0;
  let cacheBytes = 0;
  try {
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (key?.startsWith(BOOK_CONTENT_STORAGE_KEY_PREFIX)) {
        cacheEntryCount += 1;
        cacheBytes += localStorage.getItem(key)?.length ?? 0;
      }
    }
  } catch {
    // localStorage 不可用时保持 0
  }

  return {
    totalBooks,
    downloadedBooks,
    txtCount,
    mdCount,
    epubCount,
    indexBytes,
    cacheEntryCount,
    cacheBytes
  };
}

export function formatCacheBytes(bytes: number): string {
  return formatBytes(bytes);
}

export function clearReaderContentCache(): { removed: number; freedBytes: number } {
  let removed = 0;
  let freedBytes = 0;
  try {
    const keys: string[] = [];
    for (let index = 0; index < localStorage.length; index += 1) {
      const key = localStorage.key(index);
      if (key?.startsWith(BOOK_CONTENT_STORAGE_KEY_PREFIX)) {
        keys.push(key);
      }
    }
    for (const key of keys) {
      const value = localStorage.getItem(key);
      if (value !== null) {
        removed += 1;
        freedBytes += value.length;
        localStorage.removeItem(key);
      }
    }
  } catch {
    // 清理失败时返回已统计部分，不抛错
  }
  return { removed, freedBytes };
}

export function isValidFontSize(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 12 && value <= 32;
}

export function clampFontSize(value: number): number {
  return Math.min(32, Math.max(12, Math.round(value)));
}

export function isValidLineHeight(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= 1 && value <= 3;
}

export function clampLineHeight(value: number): number {
  return Math.min(3, Math.max(1, Math.round(value * 20) / 20));
}

export function clampParagraphSpacing(value: number): number {
  if (!Number.isFinite(value)) return 1.15;
  return Math.min(2, Math.max(0.5, Math.round(value * 20) / 20));
}

export function bookFormatLabel(format: MobileBook["format"]): string {
  if (format === "txt") return "TXT";
  if (format === "md") return "Markdown";
  if (format === "epub") return "EPUB";
  return format;
}
