import { describe, expect, it } from "vitest";
import {
  EPUB_READER_BOTTOM_PADDING,
  isReadableEpubSpineItem,
  resolveEpubVisibleTapPosition,
  shouldHandleEpubClick,
  shouldHandleEpubZoneAction
} from "./EpubReaderView";

describe("EPUB page safe area", () => {
  it("keeps at least one full large-text line clear of the Android bottom edge", () => {
    expect(EPUB_READER_BOTTOM_PADDING).toContain("72px");
    expect(EPUB_READER_BOTTOM_PADDING).toContain("2.2em");
    expect(EPUB_READER_BOTTOM_PADDING).toContain("safe-area-inset-bottom");
  });
});

describe("resolveEpubVisibleTapPosition", () => {
  it("maps a tap from a multi-column chapter iframe back to the visible page", () => {
    const position = resolveEpubVisibleTapPosition(
      3_052,
      400,
      { left: 0, top: 48, width: 407, height: 793 },
      { left: -2_849, top: 48 }
    );

    expect(position).toEqual({ x: 203, y: 400, width: 407, height: 793 });
    expect(position.x / position.width).toBeCloseTo(0.5, 2);
  });
});

describe("isReadableEpubSpineItem", () => {
  it("excludes non-linear cover sections returned by epubjs as booleans", () => {
    expect(isReadableEpubSpineItem({ linear: false })).toBe(false);
    expect(isReadableEpubSpineItem({ linear: "no" })).toBe(false);
  });

  it("keeps ordinary readable spine sections", () => {
    expect(isReadableEpubSpineItem({ linear: true })).toBe(true);
    expect(isReadableEpubSpineItem({ linear: "yes" })).toBe(true);
    expect(isReadableEpubSpineItem({})).toBe(true);
  });
});

describe("shouldHandleEpubClick", () => {
  it("suppresses the synthetic click emitted after a touch tap", () => {
    expect(shouldHandleEpubClick(1_200, 1_000, 0, 350)).toBe(false);
  });

  it("deduplicates rendition and iframe click listeners for one physical tap", () => {
    expect(shouldHandleEpubClick(2_040, 0, 2_000, 350)).toBe(false);
    expect(shouldHandleEpubClick(2_120, 0, 2_000, 350)).toBe(true);
  });
});

describe("shouldHandleEpubZoneAction", () => {
  it("consumes only one zone action when iframe and rendition report the same tap", () => {
    expect(shouldHandleEpubZoneAction(2_100, 2_000)).toBe(false);
    expect(shouldHandleEpubZoneAction(2_200, 2_000)).toBe(true);
  });
});
