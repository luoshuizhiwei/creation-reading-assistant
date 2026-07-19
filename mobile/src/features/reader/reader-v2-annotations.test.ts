import { describe, expect, it } from "vitest";
import type { ReaderLocator } from "./engine-v2/types";
import { isSameReaderPosition } from "./reader-v2-annotations";

const locator = (patch: Partial<ReaderLocator> = {}): ReaderLocator => ({
  version: 2,
  bookId: "book-1",
  format: "txt",
  progression: 0.25,
  chapterId: "chapter-2",
  textOffset: 120,
  updatedAt: 1,
  ...patch
});

describe("isSameReaderPosition", () => {
  it("uses precise text offsets when available", () => {
    expect(isSameReaderPosition(locator(), locator({ textOffset: 126 }))).toBe(true);
    expect(isSameReaderPosition(locator(), locator({ textOffset: 140 }))).toBe(false);
  });

  it("does not merge different books or chapters", () => {
    expect(isSameReaderPosition(locator(), locator({ bookId: "book-2" }))).toBe(false);
    expect(isSameReaderPosition(locator(), locator({ chapterId: "chapter-3" }))).toBe(false);
  });

  it("falls back to progression for legacy annotations", () => {
    expect(isSameReaderPosition(locator({ textOffset: undefined }), locator({ textOffset: undefined, progression: 0.251 }))).toBe(true);
    expect(isSameReaderPosition(locator({ textOffset: undefined }), locator({ textOffset: undefined, progression: 0.26 }))).toBe(false);
  });
});

