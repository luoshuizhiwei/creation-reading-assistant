/**
 * epub-reader/epub-engine 单测：覆盖从 EpubReaderPage 纯移动出来的引擎层——
 * 初始位置构造、href 匹配、位置换算（含进度回退）与主题注册去重。
 */
// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import {
  applyReaderTheme,
  hrefMatchesLocation,
  initialEpubLocation,
  locationFromEpub,
  shouldIgnoreKeydown
} from "../epub-engine";
import type { LibraryBook, ReadingLocation } from "@/types/library";

function bookOf(size: number): LibraryBook {
  return { id: "book-1", title: "测试书", author: "作者", format: "epub", size, addedAt: "2026-01-01T00:00:00.000Z" } as LibraryBook;
}

function locationOf(epub: Partial<ReadingLocation["epub"]>, progressPercent = 0): ReadingLocation {
  return { format: "epub", mode: "epub-cfi", progressPercent, precision: "estimated", epub, updatedAt: "2026-01-01T00:00:00.000Z", sourceVersion: { fileSize: 1 } };
}

describe("initialEpubLocation", () => {
  it("保留已有的 epub-cfi 定位，不重建", () => {
    const existing = locationOf({ cfi: "epubcfi(/6/2)" }, 0.4);
    existing.mode = "epub-cfi";
    const next = initialEpubLocation(bookOf(1000), existing);
    expect(next).toBe(existing);
  });

  it("无已有定位时生成本地格式的默认位置并沿用旧进度", () => {
    const next = initialEpubLocation(bookOf(1000), { ...locationOf(undefined, 0.3), format: "txt" } as ReadingLocation);
    expect(next.mode).toBe("epub-cfi");
    expect(next.progressPercent).toBe(0.3);
    expect(next.sourceVersion).toEqual({ fileSize: 1000 });
  });
});

describe("hrefMatchesLocation", () => {
  it("缺省 expectedHref 时视为匹配", () => {
    expect(hrefMatchesLocation(locationOf({ href: "OEBPS/c1.xhtml" }))).toBe(true);
  });

  it("按去 fragment 后的 href 精确比较", () => {
    const loc = locationOf({ href: "OEBPS/c1.xhtml" });
    expect(hrefMatchesLocation(loc, "OEBPS/c1.xhtml#p1")).toBe(true);
    expect(hrefMatchesLocation(loc, "OEBPS/c2.xhtml")).toBe(false);
  });

  it("定位缺失 href 时不匹配", () => {
    expect(hrefMatchesLocation(locationOf(undefined), "OEBPS/c1.xhtml")).toBe(false);
  });
});

describe("locationFromEpub", () => {
  it("start 携带完整信息时输出 exact 定位与页码", () => {
    const value = { start: { cfi: "epubcfi(/6/4)", href: "c2.xhtml", percentage: 0.5, index: 3, displayed: { page: 2, total: 10 } } };
    const next = locationFromEpub(undefined, bookOf(2000), value as never, locationOf({ href: "c1.xhtml" }, 0.1));
    expect(next.precision).toBe("exact");
    expect(next.epub).toEqual({ cfi: "epubcfi(/6/4)", href: "c2.xhtml", spineIndex: 3 });
    expect(next.page).toEqual({ pageIndex: 1, pageCount: 10 });
    expect(next.sourceVersion).toEqual({ fileSize: 2000 });
  });

  it("locations 未生成（percentageFromCfi 返回 0）且同章节时保留旧的已知进度", () => {
    const book = { locations: { percentageFromCfi: vi.fn(() => 0) } };
    const fallback = locationOf({ cfi: "epubcfi(/6/4)", href: "c2.xhtml" }, 0.42);
    const value = { start: { cfi: "epubcfi(/6/4)", href: "c2.xhtml", percentage: 0 } };
    const next = locationFromEpub(book as never, bookOf(2000), value as never, fallback);
    expect(next.progressPercent).toBe(0.42);
  });

  it("跨章节且 CFI 进度为 0 时接受 0（旧进度属于别的章节不能沿用）", () => {
    const book = { locations: { percentageFromCfi: vi.fn(() => 0) } };
    const fallback = locationOf({ href: "c1.xhtml" }, 0.9);
    const value = { start: { cfi: "epubcfi(/6/8)", href: "c2.xhtml", percentage: 0 } };
    const next = locationFromEpub(book as never, bookOf(2000), value as never, fallback);
    expect(next.progressPercent).toBe(0);
  });

  it("percentageFromCfi 抛错时回退到 start.percentage", () => {
    const book = { locations: { percentageFromCfi: vi.fn(() => { throw new Error("not ready"); }) } };
    const value = { start: { cfi: "epubcfi(/6/4)", href: "c2.xhtml", percentage: 0.25 } };
    const next = locationFromEpub(book as never, bookOf(2000), value as never, locationOf({ href: "c1.xhtml" }, 0.1));
    expect(next.progressPercent).toBe(0.25);
  });

  it("无 start 时沿用 fallback 的 epub 字段，precision 取决于是否已知 cfi", () => {
    const withCfi = locationOf({ cfi: "epubcfi(/6/2)", href: "c1.xhtml", spineIndex: 1 }, 0.3);
    const exact = locationFromEpub(undefined, bookOf(2000), undefined, withCfi);
    expect(exact.precision).toBe("exact");
    expect(exact.epub?.spineIndex).toBe(1);
    expect(exact.progressPercent).toBe(0.3);
    const noCfi = locationOf({ href: "c1.xhtml" }, 0.3);
    expect(locationFromEpub(undefined, bookOf(2000), undefined, noCfi).precision).toBe("estimated");
  });

  it("钳制越界的 percentage 到 [0,1]", () => {
    const value = { start: { cfi: "epubcfi(/6/4)", href: "c2.xhtml", percentage: 1.7 } };
    const next = locationFromEpub(undefined, bookOf(2000), value as never, locationOf({ href: "c1.xhtml" }, 0.1));
    expect(next.progressPercent).toBe(1);
  });
});

describe("applyReaderTheme", () => {
  interface RenditionStub {
    themes: { register: (name: string, rules: unknown) => void; select: (name: string) => void; override: (key: string, value: unknown, silent?: boolean) => void };
  }
  function stub() {
    const registered: string[] = [];
    const selected: string[] = [];
    const overrides: Array<[string, unknown]> = [];
    const rendition = {
      themes: {
        register: (name: string) => registered.push(name),
        select: (name: string) => selected.push(name),
        override: (key: string, value: unknown) => overrides.push([key, value])
      }
    } as unknown as RenditionStub;
    return { rendition, registered, selected, overrides };
  }
  const settings = {
    fontSize: 18, lineHeight: 1.8, letterSpacing: 0, paragraphSpacing: 1,
    readerBackground: "day", epubStyleMode: "novel", fontFamily: "", restoreLastPosition: true
  } as never;

  it("同一 rendition 重复应用同名主题只注册一次", () => {
    const { rendition, registered } = stub();
    applyReaderTheme(rendition as never, settings);
    applyReaderTheme(rendition as never, settings);
    expect(registered.filter((n) => n === "novel-workbench-day")).toHaveLength(1);
  });

  it("出版商样式 + 夜间时注册 night 主题并注入可读文字色", () => {
    const { rendition, registered, selected } = stub();
    applyReaderTheme(rendition as never, { ...settings, epubStyleMode: "publisher", readerBackground: "night" });
    expect(registered).toContain("publisher-preserve-night");
    expect(selected).toContain("publisher-preserve-night");
  });

  it("非出版商模式覆盖字号/行距/字距", () => {
    const { rendition, overrides } = stub();
    applyReaderTheme(rendition as never, settings);
    expect(overrides.some(([k, v]) => k === "font-size" && v === "18px")).toBe(true);
  });

  it("出版商模式不做排版覆盖", () => {
    const { rendition, overrides } = stub();
    applyReaderTheme(rendition as never, { ...settings, epubStyleMode: "publisher" });
    expect(overrides.filter(([k]) => k === "font-size" || k === "line-height")).toHaveLength(0);
  });
});

describe("shouldIgnoreKeydown", () => {
  it("输入控件内的按键被忽略，正文按键不忽略", () => {
    const el = document.createElement("input");
    expect(shouldIgnoreKeydown({ target: el } as unknown as KeyboardEvent)).toBe(true);
    expect(shouldIgnoreKeydown({ target: document.createElement("div") } as unknown as KeyboardEvent)).toBe(false);
  });
});
