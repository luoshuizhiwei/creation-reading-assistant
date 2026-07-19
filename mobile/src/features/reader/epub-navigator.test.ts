import { describe, expect, it, vi } from "vitest";
import type { MobileReaderDocument } from "../../reader/mobile-reader";
import { EpubNavigatorAdapter, type EpubViewBridge } from "./epub-navigator";

function documentFixture(): MobileReaderDocument {
  return {
    title: "测试 EPUB",
    format: "epub",
    html: "",
    plainText: "",
    toc: [
      { id: "chapter-1", title: "第一章", href: "chapter-1.xhtml", level: 1, index: 0 },
      { id: "chapter-2", title: "第二章", href: "chapter-2.xhtml", level: 1, index: 1 }
    ],
    wordCount: 0,
    currentTocIndex: 0,
    totalChapters: 2
  };
}

function bridgeFixture(): EpubViewBridge {
  return {
    display: vi.fn(async () => undefined),
    next: vi.fn(async () => undefined),
    prev: vi.fn(async () => undefined)
  };
}

describe("EpubNavigatorAdapter", () => {
  it("keeps epub.js details behind a stable navigation boundary", async () => {
    const bridge = bridgeFixture();
    const navigator = new EpubNavigatorAdapter("book-1");
    navigator.bind(bridge);
    navigator.updateDocument(documentFixture());
    navigator.markReady();

    expect(navigator.isReady()).toBe(true);
    await navigator.goToChapter(documentFixture().toc[1]);
    await navigator.goBackward();
    await navigator.goForward();

    expect(bridge.display).toHaveBeenCalledWith("chapter-2.xhtml");
    expect(bridge.prev).toHaveBeenCalledTimes(1);
    expect(bridge.next).toHaveBeenCalledTimes(1);
  });

  it("converts stable-reader locations to the shared locator model", () => {
    const navigator = new EpubNavigatorAdapter("book-1");
    navigator.updateDocument(documentFixture());
    navigator.updateLocation({
      cfi: "epubcfi(/6/4)",
      href: "chapter-2.xhtml",
      progressPercent: 62.5,
      pageIndex: 2,
      pageCount: 8
    });

    expect(navigator.getCurrentLocator()).toMatchObject({
      version: 2,
      bookId: "book-1",
      format: "epub",
      progression: 0.625,
      chapterId: "chapter-2",
      href: "chapter-2.xhtml",
      epub: { cfi: "epubcfi(/6/4)", position: 1, totalProgression: 0.625 }
    });
  });

  it("maps percentage jumps through the readable table of contents", async () => {
    const bridge = bridgeFixture();
    const navigator = new EpubNavigatorAdapter("book-1");
    navigator.bind(bridge);
    navigator.updateDocument(documentFixture());

    expect(await navigator.goToProgress(75)).toBe(true);
    expect(bridge.display).toHaveBeenCalledWith("chapter-2.xhtml");
    expect(navigator.getTableOfContents()).toHaveLength(2);
  });

  it("detaches without touching the view-owned epub.js lifecycle", async () => {
    const bridge = bridgeFixture();
    const navigator = new EpubNavigatorAdapter("book-1");
    navigator.bind(bridge);
    navigator.markReady();
    navigator.destroy();

    expect(navigator.isReady()).toBe(false);
    expect(await navigator.goForward()).toBe(false);
    expect(bridge.next).not.toHaveBeenCalled();
  });
});
