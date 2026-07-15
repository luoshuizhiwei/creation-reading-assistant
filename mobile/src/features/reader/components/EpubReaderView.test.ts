import { describe, expect, it } from "vitest";
import { isReadableEpubSpineItem } from "./EpubReaderView";

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
