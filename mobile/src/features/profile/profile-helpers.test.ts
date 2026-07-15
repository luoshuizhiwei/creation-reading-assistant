import { describe, it, expect, beforeEach } from "vitest";

const mockStorage = new Map<string, string>();
const mockLocalStorage = {
  getItem: (key: string) => mockStorage.get(key) ?? null,
  setItem: (key: string, value: string) => mockStorage.set(key, value),
  removeItem: (key: string) => mockStorage.delete(key),
  clear: () => mockStorage.clear(),
  key: (index: number) => Array.from(mockStorage.keys())[index] ?? null,
  get length() { return mockStorage.size; }
};
Object.defineProperty(globalThis, "localStorage", { value: mockLocalStorage, writable: true });
import {
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  buildStorageBreakdown,
  clearReaderContentCache,
  clampFontSize,
  clampLineHeight,
  clampParagraphSpacing,
  isValidFontSize,
  isValidLineHeight,
  bookFormatLabel
} from "./profile-helpers";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";

function buildSnapshot(books: Partial<MobileBook>[]): MobileSnapshot {
  return {
    inspirations: [],
    books: books.map((book) => ({
      id: book.id ?? `id-${Math.random()}`,
      title: book.title ?? "Test",
      format: book.format ?? "txt",
      origin: book.origin ?? "local_import",
      contentStatus: book.contentStatus ?? "available",
      size: book.size ?? 0,
      ...book
    })) as MobileBook[],
    progress: [],
    sessions: [],
    notes: [],
    highlights: [],
    tags: [],
    categories: [],
    shelves: [],
    syncAccounts: [],
    updatedAt: new Date().toISOString()
  };
}

describe("buildStorageBreakdown", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("counts books by format and downloaded state", () => {
    const snapshot = buildSnapshot([
      { id: "b1", format: "txt", contentStatus: "available", size: 100, localContentPath: "books/b1.txt" },
      { id: "b2", format: "md", contentStatus: "missing", size: 200 },
      { id: "b3", format: "epub", contentStatus: "available", size: 300, localContentPath: "books/b3.epub" }
    ]);
    const result = buildStorageBreakdown(snapshot);
    expect(result.totalBooks).toBe(3);
    expect(result.downloadedBooks).toBe(2);
    expect(result.txtCount).toBe(1);
    expect(result.mdCount).toBe(1);
    expect(result.epubCount).toBe(1);
    expect(result.indexBytes).toBe(600);
  });

  it("returns zero when localStorage is empty", () => {
    const result = buildStorageBreakdown(buildSnapshot([]));
    expect(result.cacheEntryCount).toBe(0);
    expect(result.cacheBytes).toBe(0);
  });

  it("counts only reader content cache keys", () => {
    localStorage.setItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}b1`, "abc");
    localStorage.setItem("other-key", "xyz");
    const result = buildStorageBreakdown(buildSnapshot([]));
    expect(result.cacheEntryCount).toBe(1);
    expect(result.cacheBytes).toBe(3);
  });

  it("never returns negative values", () => {
    const result = buildStorageBreakdown(buildSnapshot([]));
    expect(result.totalBooks).toBeGreaterThanOrEqual(0);
    expect(result.indexBytes).toBeGreaterThanOrEqual(0);
  });
});

describe("clearReaderContentCache", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("removes only reader content keys", () => {
    localStorage.setItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}b1`, "abc");
    localStorage.setItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}b2`, "def");
    localStorage.setItem("keep-key", "xyz");
    const result = clearReaderContentCache();
    expect(result.removed).toBe(2);
    expect(result.freedBytes).toBe(6);
    expect(localStorage.getItem("keep-key")).toBe("xyz");
    expect(localStorage.getItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}b1`)).toBeNull();
  });

  it("returns zero when nothing to clear", () => {
    const result = clearReaderContentCache();
    expect(result.removed).toBe(0);
    expect(result.freedBytes).toBe(0);
  });
});

describe("setting validators", () => {
  it("clamps font size within valid range", () => {
    expect(clampFontSize(8)).toBe(12);
    expect(clampFontSize(18)).toBe(18);
    expect(clampFontSize(40)).toBe(32);
  });

  it("rejects invalid font sizes", () => {
    expect(isValidFontSize(NaN)).toBe(false);
    expect(isValidFontSize("18")).toBe(false);
    expect(isValidFontSize(18)).toBe(true);
  });

  it("clamps line height within valid range", () => {
    expect(clampLineHeight(0.5)).toBe(1);
    expect(clampLineHeight(1.85)).toBe(1.85);
    expect(clampLineHeight(5)).toBe(3);
  });

  it("clamps paragraph spacing independently from line height", () => {
    expect(clampParagraphSpacing(0.5)).toBe(0.5);
    expect(clampParagraphSpacing(1.15)).toBe(1.15);
    expect(clampParagraphSpacing(4)).toBe(2);
  });

  it("rejects invalid line heights", () => {
    expect(isValidLineHeight(NaN)).toBe(false);
    expect(isValidLineHeight("1.5")).toBe(false);
    expect(isValidLineHeight(1.5)).toBe(true);
  });
});

describe("bookFormatLabel", () => {
  it("returns readable labels", () => {
    expect(bookFormatLabel("txt")).toBe("TXT");
    expect(bookFormatLabel("md")).toBe("Markdown");
    expect(bookFormatLabel("epub")).toBe("EPUB");
  });
});
