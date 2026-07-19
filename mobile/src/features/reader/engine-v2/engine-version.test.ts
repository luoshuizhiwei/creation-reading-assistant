import { describe, expect, it } from "vitest";
import {
  loadReaderEngineVersion,
  loadReaderEngineVersionForBook,
  normalizeReaderEngineVersion,
  readerEngineSupportsNative,
  readerEngineSupportsV2,
  saveReaderEngineVersion,
  saveReaderEngineVersionForBook
} from "./engine-version";

describe("reader engine feature flag", () => {
  it("keeps Legacy as the safe default", () => {
    expect(normalizeReaderEngineVersion(undefined)).toBe("legacy");
    expect(normalizeReaderEngineVersion("future")).toBe("legacy");
  });

  it("stores only local supported values", () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, next: string) => { values.set(key, next); }
    };
    saveReaderEngineVersion("auto", storage);
    expect(loadReaderEngineVersion(storage)).toBe("auto");
  });

  it("defaults supported local books to the native Legado core", () => {
    const values = new Map<string, string>();
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, next: string) => { values.set(key, next); }
    };
    const txt = { id: "txt-1", format: "txt" as const };
    const markdown = { id: "md-1", format: "md" as const };
    const epub = { id: "epub-1", format: "epub" as const };

    expect(readerEngineSupportsV2(txt)).toBe(true);
    expect(readerEngineSupportsV2(markdown)).toBe(true);
    expect(readerEngineSupportsV2(epub)).toBe(false);
    expect(readerEngineSupportsNative(txt)).toBe(true);
    expect(readerEngineSupportsNative(markdown)).toBe(false);
    expect(readerEngineSupportsNative(epub)).toBe(true);
    expect(loadReaderEngineVersionForBook(txt, storage)).toBe("native-legado");

    saveReaderEngineVersionForBook(txt.id, "v2", storage);
    expect(loadReaderEngineVersionForBook(txt, storage)).toBe("v2");
    expect(loadReaderEngineVersionForBook(markdown, storage)).toBe("legacy");
    expect(loadReaderEngineVersionForBook(epub, storage)).toBe("native-legado");

    saveReaderEngineVersionForBook(epub.id, "native-legado", storage);
    expect(loadReaderEngineVersionForBook(epub, storage)).toBe("native-legado");
  });
});
