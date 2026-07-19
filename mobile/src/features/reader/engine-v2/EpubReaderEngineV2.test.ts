import { describe, expect, it } from "vitest";
import type { NavItem } from "epubjs/types/navigation";
import {
  createEpubV2RenditionOptions,
  flattenEpubV2Navigation,
  isReadableEpubV2SpineItem
} from "./EpubReaderEngineV2";
import type { ReaderPreferences } from "./types";

const preferences: ReaderPreferences = {
  fontSize: 18,
  lineHeight: 1.8,
  pageMargin: 20,
  paragraphSpacing: 1,
  readerBackground: "warm",
  readerMode: "paged",
  fontWeight: "regular"
};

describe("EpubReaderEngineV2", () => {
  it("filters non-linear cover items from the reading order", () => {
    expect(isReadableEpubV2SpineItem({ href: "cover.xhtml", linear: false })).toBe(false);
    expect(isReadableEpubV2SpineItem({ href: "cover.xhtml", linear: "no" })).toBe(false);
    expect(isReadableEpubV2SpineItem({ href: "chapter.xhtml", linear: "yes" })).toBe(true);
    expect(isReadableEpubV2SpineItem({ linear: "yes" })).toBe(false);
  });

  it("preserves nested TOC levels", () => {
    const toc = [{
      id: "volume",
      label: "第一卷",
      href: "volume.xhtml",
      subitems: [{ id: "chapter", label: "第一章", href: "chapter.xhtml" }]
    }] as NavItem[];
    expect(flattenEpubV2Navigation(toc)).toEqual([
      expect.objectContaining({ id: "volume", level: 1, index: 0 }),
      expect.objectContaining({ id: "chapter", level: 2, index: 1 })
    ]);
  });

  it("always disables scripted EPUB content", () => {
    const options = createEpubV2RenditionOptions(preferences);
    expect(options.allowScriptedContent).toBe(false);
    expect(options.flow).toBe("paginated");
  });
});
