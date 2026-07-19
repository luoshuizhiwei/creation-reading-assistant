import ePub from "epubjs";
import type Book from "epubjs/types/book";
import type Contents from "epubjs/types/contents";
import type { NavItem } from "epubjs/types/navigation";
import type { DisplayedLocation, Location, RenditionOptions } from "epubjs/types/rendition";
import { ReaderEngineEvents } from "./events";
import { clampProgression, normalizeReaderLocator } from "./locator";
import {
  ReaderEngineError,
  type ReaderDecoration,
  type ReaderEngine,
  type ReaderEngineEvent,
  type ReaderEngineListener,
  type ReaderLink,
  type ReaderLocator,
  type ReaderOpenInput,
  type ReaderPreferences,
  type ReaderPublication,
  type ReaderSelection
} from "./types";

const EPUB_OPEN_TIMEOUT_MS = 12_000;
const ENGINE_THEME_NAME = "creation-reader-engine-v2";

type EpubRendition = ReturnType<Book["renderTo"]>;

interface SpineEntry {
  id?: string;
  href?: string;
  index?: number;
  linear?: boolean | string;
}

export function isReadableEpubV2SpineItem(item: SpineEntry): boolean {
  return item.linear !== false && item.linear !== "no" && Boolean(item.href);
}

function stripFragment(value = ""): string {
  return value.split("#")[0].replace(/^\.\//, "");
}

export function flattenEpubV2Navigation(items: NavItem[], level = 1): ReaderLink[] {
  const links: ReaderLink[] = [];
  for (const item of items) {
    if (item.href) {
      links.push({
        id: item.id || `toc-${links.length}`,
        title: item.label?.trim() || "未命名章节",
        href: item.href,
        level,
        index: links.length
      });
    }
    if (item.subitems?.length) links.push(...flattenEpubV2Navigation(item.subitems, level + 1));
  }
  return links.map((link, index) => ({ ...link, index }));
}

function decodeBase64ToArrayBuffer(value: string): ArrayBuffer {
  let normalized = value.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
  normalized = normalized.replace(/-/g, "+").replace(/_/g, "/");
  while (normalized.length % 4 !== 0) normalized += "=";
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
}

function waitForBookReady(book: Book, signal: AbortSignal): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    let settled = false;
    const finish = (callback: () => void) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      signal.removeEventListener("abort", onAbort);
      callback();
    };
    const onAbort = () => finish(() => reject(new ReaderEngineError("aborted", "EPUB 打开任务已取消。", { retryable: false })));
    const timer = setTimeout(
      () => finish(() => reject(new ReaderEngineError("open-failed", "EPUB 容器解析超时。"))),
      EPUB_OPEN_TIMEOUT_MS
    );
    signal.addEventListener("abort", onAbort, { once: true });
    book.ready.then(
      () => finish(resolve),
      (error) => finish(() => reject(error))
    );
  });
}

function buildReadingOrder(book: Book): ReaderLink[] {
  const items = ((book.spine as unknown as { spineItems?: SpineEntry[] }).spineItems ?? []).filter(isReadableEpubV2SpineItem);
  return items.map((item, index) => ({
    id: item.id || `spine-${index}`,
    title: item.href?.split("/").pop()?.replace(/\.[^.]+$/, "") || `章节 ${index + 1}`,
    href: item.href,
    level: 1,
    index
  }));
}

function mergeTocWithReadingOrder(toc: ReaderLink[], readingOrder: ReaderLink[]): ReaderLink[] {
  const byHref = new Map(readingOrder.map((item) => [stripFragment(item.href), item]));
  const mapped = toc
    .map((item) => {
      const direct = byHref.get(stripFragment(item.href));
      if (direct) return { ...item, index: direct.index };
      const fileName = stripFragment(item.href).split("/").pop();
      const fallback = readingOrder.find((entry) => stripFragment(entry.href).split("/").pop() === fileName);
      return fallback ? { ...item, index: fallback.index } : undefined;
    })
    .filter((item): item is ReaderLink => Boolean(item));
  return mapped.length ? mapped : readingOrder;
}

function readerTheme(preferences: ReaderPreferences): Record<string, Record<string, string>> {
  const palette: Record<string, { background: string; color: string }> = {
    white: { background: "#fafaf8", color: "#1a1a1a" },
    warm: { background: "#f3e8d8", color: "#2b2118" },
    green: { background: "#e8f0df", color: "#1f291a" },
    night: { background: "#1a1614", color: "#e8ddd0" },
    "warm-yellow": { background: "#f7f0d8", color: "#3d2b1f" },
    "green-bean": { background: "#e8f0e0", color: "#2d332b" },
    "oled-black": { background: "#000", color: "#b8b0a8" }
  };
  const selected = palette[preferences.readerBackground] ?? palette.warm;
  return {
    "*": { "box-sizing": "border-box" },
    html: { margin: "0", padding: "0", "min-height": "100%", "background-color": selected.background },
    body: {
      margin: "0",
      padding: `14px ${preferences.pageMargin}px max(48px, env(safe-area-inset-bottom))`,
      "min-height": "100%",
      "font-size": `${preferences.fontSize}px`,
      "font-weight": preferences.fontWeight === "bold" ? "600" : "400",
      "line-height": String(preferences.lineHeight),
      color: selected.color,
      "background-color": selected.background
    },
    p: { "margin-bottom": `${preferences.paragraphSpacing}em` }
  };
}

export function createEpubV2RenditionOptions(preferences: ReaderPreferences): RenditionOptions {
  return {
    flow: preferences.readerMode === "paged" ? "paginated" : "scrolled-doc",
    spread: "none",
    width: "100%",
    height: "100%",
    resizeOnOrientationChange: true,
    snap: preferences.readerMode === "paged",
    allowScriptedContent: false
  };
}

/** 禁止 EPUB 自动加载不可信远程资源，也不允许内容脚本进入 App 上下文。 */
export function secureEpubContents(contents: Contents): void {
  const document = contents.document;
  const frame = document.defaultView?.frameElement;
  if (frame instanceof HTMLIFrameElement) frame.setAttribute("sandbox", "allow-same-origin");
  document.querySelectorAll("script, iframe, object, embed").forEach((node) => node.remove());
  document.querySelectorAll<HTMLElement>("*").forEach((element) => {
    for (const attribute of Array.from(element.attributes)) {
      if (/^on/i.test(attribute.name)) element.removeAttribute(attribute.name);
    }
  });
  document.querySelectorAll<HTMLImageElement | HTMLSourceElement | HTMLVideoElement | HTMLAudioElement>("img[src],source[src],video[src],audio[src]")
    .forEach((element) => {
      if (/^https?:/i.test(element.getAttribute("src") ?? "")) element.removeAttribute("src");
    });
  document.querySelectorAll<HTMLLinkElement>("link[href]").forEach((element) => {
    if (/^https?:/i.test(element.href)) element.remove();
  });
  document.querySelectorAll<HTMLAnchorElement>("a[href]").forEach((anchor) => {
    if (/^https?:/i.test(anchor.href)) {
      anchor.rel = "noreferrer noopener";
      anchor.target = "_blank";
    }
  });
}

export class EpubReaderEngineV2 implements ReaderEngine {
  readonly id = `epub-v2-${crypto.randomUUID()}`;
  readonly format = "epub" as const;
  readonly version = "v2" as const;
  private readonly events = new ReaderEngineEvents();
  private book?: Book;
  private rendition?: EpubRendition;
  private publication?: ReaderPublication;
  private preferences?: ReaderPreferences;
  private currentLocator: ReaderLocator | null = null;
  private root?: HTMLDivElement;
  private destroyed = false;
  private readonly contentHook = (contents: Contents) => secureEpubContents(contents);
  private readonly relocatedHandler = (location: Location) => this.handleRelocated(location);
  private readonly displayErrorHandler = (error: Error) => {
    this.events.emit("error", new ReaderEngineError("mount-failed", error?.message || "EPUB 渲染失败。", { cause: error }));
  };

  async open(input: ReaderOpenInput): Promise<ReaderPublication> {
    if (this.destroyed) throw new ReaderEngineError("aborted", "EPUB V2 已销毁。", { retryable: false });
    if (input.format !== "epub") throw new ReaderEngineError("unsupported-format", "EPUB 引擎只能打开 EPUB。", { retryable: false });
    const normalized = input.content.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
    if (!normalized) throw new ReaderEngineError("empty-content", "EPUB 内容为空。", { retryable: false });
    this.preferences = input.preferences;
    let book = ePub(normalized, { openAs: "base64" });
    try {
      await waitForBookReady(book, input.signal);
    } catch (firstError) {
      book.destroy();
      if (input.signal.aborted) throw firstError;
      try {
        book = ePub(decodeBase64ToArrayBuffer(input.content), { openAs: "binary" });
        await waitForBookReady(book, input.signal);
      } catch (secondError) {
        book.destroy();
        throw new ReaderEngineError("open-failed", "EPUB 容器无法解析。", { cause: secondError });
      }
    }
    if (input.signal.aborted) {
      book.destroy();
      throw new ReaderEngineError("aborted", "EPUB 打开任务已取消。", { retryable: false });
    }
    const readingOrder = buildReadingOrder(book);
    if (!readingOrder.length) {
      book.destroy();
      throw new ReaderEngineError("invalid-publication", "EPUB 没有可读取的 spine。", { retryable: false });
    }
    const toc = mergeTocWithReadingOrder(flattenEpubV2Navigation(book.navigation?.toc ?? []), readingOrder);
    const metadata = book.packaging.metadata as typeof book.packaging.metadata & {
      creator?: string;
      language?: string;
      publisher?: string;
    };
    this.book = book;
    this.publication = {
      bookId: input.book.id,
      title: metadata.title?.trim() || input.book.title,
      format: "epub",
      readingOrder,
      tableOfContents: toc,
      metadata: {
        author: metadata.creator?.trim() || input.book.author,
        language: metadata.language?.trim() || input.book.language,
        publisher: metadata.publisher?.trim() || input.book.publisher
      }
    };
    return this.publication;
  }

  async mount(container: HTMLElement): Promise<void> {
    if (!this.book || !this.publication || !this.preferences) {
      throw new ReaderEngineError("mount-failed", "必须先打开 EPUB，再挂载阅读器。", { retryable: false });
    }
    if (this.destroyed) throw new ReaderEngineError("aborted", "EPUB V2 已销毁。", { retryable: false });
    this.root = document.createElement("div");
    this.root.className = "reader-engine-v2-epub-root";
    this.root.style.width = "100%";
    this.root.style.height = "100%";
    this.root.style.overflow = "hidden";
    container.replaceChildren(this.root);
    const options = createEpubV2RenditionOptions(this.preferences);
    const rendition = this.book.renderTo(this.root, options);
    this.rendition = rendition;
    rendition.hooks.content.register(this.contentHook);
    rendition.on("relocated", this.relocatedHandler);
    rendition.on("displayError", this.displayErrorHandler);
    await this.applyPreferences(this.preferences);
  }

  async restore(locator?: ReaderLocator): Promise<void> {
    if (!this.rendition || !this.publication) throw new ReaderEngineError("restore-failed", "EPUB 阅读器尚未挂载。");
    const target = locator?.epub?.cfi
      || (locator?.href ? `${locator.href}${locator.fragment ? `#${locator.fragment}` : ""}` : undefined)
      || (locator?.epub?.position !== undefined ? this.publication.readingOrder[locator.epub.position]?.href : undefined)
      || this.publication.readingOrder[0]?.href;
    try {
      await this.rendition.display(target);
    } catch (error) {
      const fallback = this.publication.readingOrder[0]?.href;
      if (!fallback || fallback === target) throw new ReaderEngineError("restore-failed", "EPUB 阅读位置恢复失败。", { cause: error });
      await this.rendition.display(fallback);
    }
  }

  getCurrentLocator(): Promise<ReaderLocator | null> {
    return Promise.resolve(this.currentLocator ? normalizeReaderLocator(this.currentLocator) : null);
  }

  async goTo(locator: ReaderLocator): Promise<void> {
    await this.restore(locator);
  }

  async goToChapter(chapterId: string): Promise<void> {
    if (!this.rendition || !this.publication) throw new ReaderEngineError("navigation-failed", "EPUB 阅读器尚未就绪。");
    const target = this.publication.tableOfContents.find((item) => item.id === chapterId)
      ?? this.publication.readingOrder.find((item) => item.id === chapterId);
    if (!target?.href) throw new ReaderEngineError("navigation-failed", "没有找到目标章节。", { retryable: false });
    await this.rendition.display(target.href);
  }

  async goForward(): Promise<boolean> {
    if (!this.rendition) return false;
    const before = this.currentLocator?.epub?.cfi;
    await this.rendition.next();
    return before !== this.currentLocator?.epub?.cfi;
  }

  async goBackward(): Promise<boolean> {
    if (!this.rendition) return false;
    const before = this.currentLocator?.epub?.cfi;
    await this.rendition.prev();
    return before !== this.currentLocator?.epub?.cfi;
  }

  getTableOfContents(): Promise<ReaderLink[]> {
    return Promise.resolve(this.publication?.tableOfContents.map((item) => ({ ...item })) ?? []);
  }

  async applyPreferences(preferences: ReaderPreferences): Promise<void> {
    this.preferences = preferences;
    const rendition = this.rendition;
    if (!rendition) return;
    rendition.themes.register(ENGINE_THEME_NAME, readerTheme(preferences));
    rendition.themes.select(ENGINE_THEME_NAME);
    if (preferences.readerMode === "paged") rendition.flow("paginated");
    else rendition.flow("scrolled-doc");
  }

  getSelection(): Promise<ReaderSelection | null> {
    const selection = this.rendition?.getContents().map((contents: Contents) => contents.window.getSelection()?.toString().trim() ?? "").find(Boolean);
    return Promise.resolve(selection ? { text: selection, locator: this.currentLocator ?? undefined } : null);
  }

  addDecoration(_decoration: ReaderDecoration): Promise<void> {
    // POC 先保留接口边界；正式接入时使用 epub.js annotations + CFI，不在这里伪造高亮。
    return Promise.resolve();
  }

  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>): () => void {
    return this.events.on(event, listener);
  }

  private handleRelocated(location: Location): void {
    if (!this.publication || this.destroyed) return;
    const start: DisplayedLocation = location.start;
    const href = stripFragment(start.href);
    const position = this.publication.readingOrder.findIndex((item) => stripFragment(item.href) === href);
    const percentage = clampProgression(Number(start.percentage));
    const displayedPage = Math.max(1, Number(start.displayed?.page) || 1);
    const displayedTotal = Math.max(1, Number(start.displayed?.total) || 1);
    const withinChapter = Math.min(1, Math.max(0, (displayedPage - 1) / displayedTotal));
    const progression = percentage ?? ((Math.max(0, position) + withinChapter) / Math.max(1, this.publication.readingOrder.length));
    const toc = this.publication.tableOfContents.find((item) => stripFragment(item.href) === href);
    this.currentLocator = normalizeReaderLocator({
      version: 2,
      bookId: this.publication.bookId,
      format: "epub",
      progression,
      chapterId: toc?.id ?? this.publication.readingOrder[position]?.id,
      href: start.href,
      epub: { cfi: start.cfi, position: Math.max(0, position), totalProgression: progression },
      updatedAt: Date.now()
    });
    this.events.emit("location", this.currentLocator);
  }

  async destroy(): Promise<void> {
    if (this.destroyed) return;
    this.destroyed = true;
    const rendition = this.rendition;
    const book = this.book;
    this.rendition = undefined;
    this.book = undefined;
    this.publication = undefined;
    this.currentLocator = null;
    try {
      rendition?.off("relocated", this.relocatedHandler);
      rendition?.off("displayError", this.displayErrorHandler);
      rendition?.hooks.content.deregister(this.contentHook);
      rendition?.destroy();
    } finally {
      book?.destroy();
      this.root?.remove();
      this.root = undefined;
      this.events.clear();
    }
  }
}
