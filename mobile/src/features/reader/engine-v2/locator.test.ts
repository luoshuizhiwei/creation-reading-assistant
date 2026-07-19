import { describe, expect, it } from "vitest";
import type { ReadingLocation } from "../../../../../src/types/library";
import {
  legacyLocationFromLocator,
  locatorFromLegacyLocation,
  normalizeReaderLocator,
  parseReaderLocator,
  serializeReaderLocator
} from "./locator";

describe("ReaderLocator", () => {
  it("migrates legacy percent and EPUB CFI without mutating the source", () => {
    const legacy: ReadingLocation = {
      format: "epub",
      mode: "epub-cfi",
      progressPercent: 42.5,
      precision: "exact",
      epub: { cfi: "epubcfi(/6/4!/4/2)", href: "Text/chapter.xhtml", spineIndex: 2, chapterRef: "chapter-3" },
      updatedAt: "2026-07-16T00:00:00.000Z"
    };
    const before = structuredClone(legacy);
    const locator = locatorFromLegacyLocation("book-1", legacy);
    expect(locator).toMatchObject({
      version: 2,
      bookId: "book-1",
      format: "epub",
      progression: 0.425,
      chapterId: "chapter-3",
      href: "Text/chapter.xhtml",
      epub: { cfi: "epubcfi(/6/4!/4/2)", position: 2, totalProgression: 0.425 }
    });
    expect(legacy).toEqual(before);
  });

  it("clamps invalid values and never serializes NaN", () => {
    const normalized = normalizeReaderLocator({
      version: 2,
      bookId: "book-2",
      format: "txt",
      progression: Number.NaN,
      textOffset: -9,
      paragraphIndex: 4.9,
      updatedAt: Number.NaN
    });
    expect(normalized.progression).toBeUndefined();
    expect(normalized.textOffset).toBe(0);
    expect(normalized.paragraphIndex).toBe(4);
    expect(Number.isFinite(normalized.updatedAt)).toBe(true);
    expect(serializeReaderLocator(normalized)).not.toContain("NaN");
  });

  it("round-trips TXT anchors through the legacy location shape", () => {
    const locator = normalizeReaderLocator({
      version: 2,
      bookId: "book-3",
      format: "markdown",
      progression: 0.75,
      chapterId: "heading-8",
      textOffset: 1_200,
      paragraphIndex: 24,
      updatedAt: Date.parse("2026-07-16T01:02:03.000Z")
    });
    const legacy = legacyLocationFromLocator(locator);
    expect(legacy).toMatchObject({
      format: "md",
      mode: "text-anchor",
      progressPercent: 75,
      text: { charOffset: 1_200, chapterRef: "heading-8" },
      paragraphIndex: 24
    });
    expect(locatorFromLegacyLocation("book-3", legacy)).toMatchObject({
      format: "markdown",
      progression: 0.75,
      textOffset: 1_200,
      paragraphIndex: 24
    });
  });

  it("rejects malformed serialized locators", () => {
    expect(parseReaderLocator("not-json")).toBeUndefined();
    expect(parseReaderLocator('{"version":1,"bookId":"x","format":"txt"}')).toBeUndefined();
    expect(parseReaderLocator(serializeReaderLocator({
      version: 2,
      bookId: "x",
      format: "txt",
      progression: 2,
      updatedAt: 1
    }))?.progression).toBe(1);
  });
});
