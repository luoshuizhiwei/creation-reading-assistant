import type { MobileReaderDocument } from "../../reader/mobile-reader";
import { normalizeReaderLocator } from "./engine-v2/locator";
import type { ReaderLink, ReaderLocator } from "./engine-v2/types";

export type EpubNavigationTarget = string | number;

/**
 * EPUB 渲染视图提供给导航层的最小能力。
 *
 * 这里刻意不暴露 epub.js 的 Book/Rendition，避免阅读页 UI 与具体内核耦合。
 */
export interface EpubViewBridge {
  display(target: EpubNavigationTarget): Promise<void>;
  next(): Promise<void>;
  prev(): Promise<void>;
}

export interface EpubLocationInfo {
  cfi: string;
  href: string;
  progressPercent: number;
  chapterTitle?: string;
  pageIndex?: number;
  pageCount?: number;
}

function tocLinks(document?: MobileReaderDocument): ReaderLink[] {
  return document?.toc.map((item, index) => ({
    id: item.id,
    title: item.title,
    href: item.href,
    level: item.level,
    index: typeof item.index === "number" ? item.index : index
  })) ?? [];
}

/**
 * 稳定版 EpubReaderView 与统一阅读导航协议之间的适配器。
 *
 * 适配器不创建第二个 epub.js 实例，只转发已验证过的稳定版翻页实现，
 * 同时把位置与目录转换成 ReaderLocator / ReaderLink。
 */
export class EpubNavigatorAdapter {
  private bridge: EpubViewBridge | null = null;
  private location: EpubLocationInfo | null = null;
  private document?: MobileReaderDocument;
  private ready = false;

  constructor(readonly bookId: string) {}

  bind(bridge: EpubViewBridge | null): void {
    this.bridge = bridge;
    if (!bridge) this.ready = false;
  }

  markReady(): void {
    this.ready = Boolean(this.bridge);
  }

  isReady(): boolean {
    return this.ready && Boolean(this.bridge);
  }

  updateDocument(document: MobileReaderDocument): void {
    this.document = document;
  }

  updateLocation(location: EpubLocationInfo): void {
    this.location = { ...location };
  }

  clearLocation(): void {
    this.location = null;
  }

  getLegacyLocationInfo(): EpubLocationInfo | null {
    return this.location ? { ...this.location } : null;
  }

  getCurrentLocator(): ReaderLocator | null {
    if (!this.location) return null;
    const chapter = this.document?.toc.find((item) => {
      if (!item.href || !this.location?.href) return false;
      return item.href.split("#")[0] === this.location.href.split("#")[0];
    });
    return normalizeReaderLocator({
      version: 2,
      bookId: this.bookId,
      format: "epub",
      progression: Math.min(1, Math.max(0, this.location.progressPercent / 100)),
      chapterId: chapter?.id,
      href: this.location.href,
      epub: {
        cfi: this.location.cfi,
        position: chapter?.index,
        totalProgression: Math.min(1, Math.max(0, this.location.progressPercent / 100))
      },
      updatedAt: Date.now()
    });
  }

  getTableOfContents(): ReaderLink[] {
    return tocLinks(this.document).map((item) => ({ ...item }));
  }

  async goTo(target: EpubNavigationTarget): Promise<boolean> {
    const bridge = this.bridge;
    if (!bridge) return false;
    await bridge.display(target);
    return true;
  }

  async goToChapter(chapter: MobileReaderDocument["toc"][number] | undefined): Promise<boolean> {
    if (!chapter) return false;
    if (chapter.href) return this.goTo(chapter.href);
    if (typeof chapter.index !== "number") return false;
    return this.goTo(chapter.index);
  }

  async goToProgress(progressPercent: number): Promise<boolean> {
    const toc = this.document?.toc ?? [];
    const total = Math.max(1, this.document?.totalChapters ?? toc.length);
    const targetIndex = Math.min(total - 1, Math.max(0, Math.floor((progressPercent / 100) * total)));
    const item = toc.find((entry) => entry.index === targetIndex) ?? toc[targetIndex];
    return item ? this.goToChapter(item) : this.goTo(targetIndex);
  }

  async goForward(): Promise<boolean> {
    const bridge = this.bridge;
    if (!bridge) return false;
    await bridge.next();
    return true;
  }

  async goBackward(): Promise<boolean> {
    const bridge = this.bridge;
    if (!bridge) return false;
    await bridge.prev();
    return true;
  }

  destroy(): void {
    this.bridge = null;
    this.location = null;
    this.document = undefined;
    this.ready = false;
  }
}
