import { renderMarkdown } from "../../../reader/mobile-reader-markdown";
import { preparePlainTextSourceAsync } from "../../../reader/mobile-reader-preparation";
import { renderPreparedPlainText, type PreparedPlainTextSource } from "../../../reader/mobile-reader-txt";
import type { MobileReaderDocument } from "../../../reader/mobile-reader-types";
import { ReaderEngineEvents } from "./events";
import { clampProgression, normalizeReaderLocator } from "./locator";
import {
  ReaderEngineError,
  type ReaderEngine,
  type ReaderEngineEvent,
  type ReaderEngineListener,
  type ReaderFormat,
  type ReaderLink,
  type ReaderLocator,
  type ReaderOpenInput,
  type ReaderPreferences,
  type ReaderPublication,
  type ReaderSelection
} from "./types";

type TextFormat = Extract<ReaderFormat, "txt" | "markdown">;

function nextPaint(): Promise<void> {
  return new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
}

function assertNotAborted(signal: AbortSignal): void {
  if (signal.aborted) {
    throw new ReaderEngineError("aborted", "文本阅读任务已取消。", { retryable: false });
  }
}

function tocLinks(document: MobileReaderDocument): ReaderLink[] {
  return document.toc.map((item, index) => ({
    id: item.id,
    title: item.title,
    href: item.href,
    level: item.level,
    index: item.index ?? index
  }));
}

/**
 * TXT/Markdown 的独立 V2 导航器。
 *
 * 约束：
 * - 打开阶段只生成 Publication，不读取书架/同步/灵感状态；
 * - 位置使用全书 progression + textOffset + chapterId，不持久化像素；
 * - mount 后才接触 DOM，destroy 必须释放 listener、RAF 和根节点；
 * - 不使用 transform 翻页，避免 Android WebView 大文本图层残影。
 */
export class TextReaderEngineV2 implements ReaderEngine {
  readonly id = `text-v2-${crypto.randomUUID()}`;
  readonly version = "v2" as const;
  readonly events = new ReaderEngineEvents();

  private input?: ReaderOpenInput;
  private publication?: ReaderPublication;
  private source?: PreparedPlainTextSource;
  private markdownDocument?: MobileReaderDocument;
  private root?: HTMLDivElement;
  private article?: HTMLElement;
  private preferences?: ReaderPreferences;
  private chapterIndex = 0;
  private locationFrame?: number;
  private destroyed = false;
  private readonly disposers: Array<() => void> = [];

  constructor(readonly format: TextFormat) {}

  async open(input: ReaderOpenInput): Promise<ReaderPublication> {
    if (this.destroyed) throw new ReaderEngineError("aborted", "文本阅读器已经销毁。", { retryable: false });
    if (input.format !== this.format) {
      throw new ReaderEngineError("unsupported-format", `文本引擎不能打开 ${input.format}。`, { retryable: false });
    }
    assertNotAborted(input.signal);
    if (!input.content.trim()) {
      throw new ReaderEngineError("empty-content", "书籍正文为空。", { retryable: false });
    }

    this.input = input;
    this.preferences = input.preferences;
    let document: MobileReaderDocument;
    if (this.format === "txt") {
      this.source = await preparePlainTextSourceAsync(input.content, input.book.title, input.signal);
      assertNotAborted(input.signal);
      document = renderPreparedPlainText(this.source, { chapterIndex: 0 });
    } else {
      document = renderMarkdown(input.content);
      this.markdownDocument = document;
    }

    const tableOfContents = tocLinks(document);
    const fallback: ReaderLink = { id: "start", title: input.book.title, level: 1, index: 0 };
    const readingOrder = tableOfContents.filter((item) => item.level > 0);
    this.publication = {
      bookId: input.book.id,
      title: input.book.title,
      format: this.format,
      readingOrder: readingOrder.length ? readingOrder : [fallback],
      tableOfContents: tableOfContents.length ? tableOfContents : [fallback],
      metadata: {
        author: input.book.author
      }
    };
    return this.publication;
  }

  async mount(container: HTMLElement): Promise<void> {
    this.assertOpened();
    if (this.destroyed) throw new ReaderEngineError("aborted", "文本阅读器已经销毁。", { retryable: false });
    this.unmountDom();
    const root = document.createElement("div");
    root.className = "reader-engine-v2-text-root";
    root.tabIndex = 0;
    const article = document.createElement("article");
    article.className = "reader-engine-v2-text-content reader-content";
    root.append(article);
    container.replaceChildren(root);
    this.root = root;
    this.article = article;

    const onScroll = () => this.scheduleLocation();
    const onSelection = () => this.emitSelection();
    root.addEventListener("scroll", onScroll, { passive: true });
    article.addEventListener("mouseup", onSelection);
    article.addEventListener("touchend", onSelection, { passive: true });
    this.disposers.push(
      () => root.removeEventListener("scroll", onScroll),
      () => article.removeEventListener("mouseup", onSelection),
      () => article.removeEventListener("touchend", onSelection)
    );

    this.renderCurrentUnit();
    await this.applyPreferences(this.preferences as ReaderPreferences);
  }

  async restore(locator?: ReaderLocator): Promise<void> {
    this.assertMounted();
    if (!locator) {
      this.root?.scrollTo({ left: 0, top: 0, behavior: "auto" });
      this.emitLocation();
      return;
    }
    const normalized = normalizeReaderLocator(locator);
    if (normalized.bookId !== this.input?.book.id) {
      throw new ReaderEngineError("restore-failed", "恢复位置不属于当前书籍。", { retryable: false });
    }

    const nextChapter = this.chapterIndexForLocator(normalized);
    if (nextChapter !== this.chapterIndex) {
      this.chapterIndex = nextChapter;
      this.renderCurrentUnit();
    }
    await nextPaint();
    const root = this.root as HTMLDivElement;
    if (this.format === "markdown" && normalized.chapterId) {
      const target = this.markdownHeading(normalized.chapterId);
      if (target) {
        this.scrollToMarkdownHeading(target);
        this.emitLocation();
        return;
      }
    }
    const localProgression = this.localProgressionFromLocator(normalized);
    if (this.preferences?.readerMode === "paged") {
      root.scrollTo({ left: Math.round(Math.max(0, root.scrollWidth - root.clientWidth) * localProgression), top: 0, behavior: "auto" });
    } else {
      root.scrollTo({ top: Math.round(Math.max(0, root.scrollHeight - root.clientHeight) * localProgression), left: 0, behavior: "auto" });
    }
    this.emitLocation();
  }

  async getCurrentLocator(): Promise<ReaderLocator | null> {
    if (!this.input || !this.root) return null;
    if (this.format === "markdown") this.syncMarkdownChapterFromViewport();
    const root = this.root;
    const paged = this.preferences?.readerMode === "paged";
    const max = paged ? root.scrollWidth - root.clientWidth : root.scrollHeight - root.clientHeight;
    const offset = paged ? root.scrollLeft : root.scrollTop;
    const localProgression = max > 0 ? Math.min(1, Math.max(0, offset / max)) : 0;
    const range = this.currentTextRange();
    const textLength = Math.max(1, this.input.content.length);
    const textOffset = Math.round(range.start + (range.end - range.start) * localProgression);
    return normalizeReaderLocator({
      version: 2,
      bookId: this.input.book.id,
      format: this.format,
      progression: textOffset / textLength,
      chapterId: this.currentLink()?.id,
      textOffset,
      updatedAt: Date.now()
    });
  }

  goTo(locator: ReaderLocator): Promise<void> {
    return this.restore(locator);
  }

  async goToChapter(chapterId: string): Promise<void> {
    this.assertMounted();
    const index = this.readingLinks().findIndex((item) => item.id === chapterId);
    if (index < 0) throw new ReaderEngineError("navigation-failed", "目录目标不存在。", { retryable: false });
    this.chapterIndex = index;
    this.renderCurrentUnit();
    await nextPaint();
    if (this.format === "markdown") {
      const target = this.markdownHeading(chapterId);
      if (target) this.scrollToMarkdownHeading(target);
    }
    this.emitLocation();
  }

  async goForward(): Promise<boolean> {
    return this.move(1);
  }

  async goBackward(): Promise<boolean> {
    return this.move(-1);
  }

  async getTableOfContents(): Promise<ReaderLink[]> {
    return this.publication?.tableOfContents ?? [];
  }

  async applyPreferences(preferences: ReaderPreferences): Promise<void> {
    this.preferences = preferences;
    if (!this.root || !this.article) return;
    const root = this.root;
    const article = this.article;
    root.classList.toggle("reader-engine-v2-paged", preferences.readerMode === "paged");
    root.classList.toggle("reader-engine-v2-scroll", preferences.readerMode === "scroll");
    root.style.overflowX = preferences.readerMode === "paged" ? "auto" : "hidden";
    root.style.overflowY = preferences.readerMode === "scroll" ? "auto" : "hidden";
    article.style.fontSize = `${preferences.fontSize}px`;
    article.style.lineHeight = String(preferences.lineHeight);
    article.style.fontWeight = preferences.fontWeight === "bold" ? "600" : "400";
    article.style.paddingLeft = `${preferences.pageMargin}px`;
    article.style.paddingRight = `${preferences.pageMargin}px`;
    article.style.setProperty("--reader-paragraph-spacing", `${preferences.paragraphSpacing}em`);
    article.style.columnWidth = preferences.readerMode === "paged" ? `calc(100vw - ${preferences.pageMargin * 2}px)` : "auto";
    article.style.columnGap = preferences.readerMode === "paged" ? `${preferences.pageMargin * 2}px` : "normal";
    article.style.height = preferences.readerMode === "paged" ? "100%" : "auto";
  }

  async getSelection(): Promise<ReaderSelection | null> {
    if (!this.root) return null;
    const selection = document.getSelection();
    const text = selection?.toString().trim() ?? "";
    const node = selection?.anchorNode;
    if (!text || !node || !this.root.contains(node)) return null;
    return { text, locator: (await this.getCurrentLocator()) ?? undefined };
  }

  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>): () => void {
    return this.events.on(event, listener);
  }

  async destroy(): Promise<void> {
    if (this.destroyed) return;
    this.destroyed = true;
    if (this.locationFrame !== undefined) cancelAnimationFrame(this.locationFrame);
    this.locationFrame = undefined;
    this.unmountDom();
    this.events.clear();
    this.input = undefined;
    this.publication = undefined;
    this.source = undefined;
    this.markdownDocument = undefined;
  }

  private assertOpened(): void {
    if (!this.input || !this.publication) {
      throw new ReaderEngineError("open-failed", "必须先打开书籍。", { retryable: false });
    }
  }

  private assertMounted(): void {
    this.assertOpened();
    if (!this.root || !this.article) {
      throw new ReaderEngineError("mount-failed", "阅读视图尚未挂载。", { retryable: false });
    }
  }

  private readingLinks(): ReaderLink[] {
    return this.publication?.readingOrder ?? [];
  }

  private currentLink(): ReaderLink | undefined {
    return this.readingLinks()[this.chapterIndex];
  }

  private markdownHeading(chapterId: string): HTMLElement | null {
    if (this.format !== "markdown" || !this.article) return null;
    return this.article.querySelector<HTMLElement>(`#${chapterId}`);
  }

  private scrollToMarkdownHeading(heading: HTMLElement): void {
    if (!this.root) return;
    if (this.preferences?.readerMode === "paged") {
      const pageWidth = Math.max(1, this.root.clientWidth);
      const pageLeft = Math.floor(Math.max(0, heading.offsetLeft) / pageWidth) * pageWidth;
      this.root.scrollTo({ left: pageLeft, top: 0, behavior: "auto" });
      return;
    }
    this.root.scrollTo({ left: 0, top: Math.max(0, heading.offsetTop), behavior: "auto" });
  }

  private syncMarkdownChapterFromViewport(): void {
    if (this.format !== "markdown" || !this.root || !this.article) return;
    const paged = this.preferences?.readerMode === "paged";
    const threshold = (paged ? this.root.scrollLeft : this.root.scrollTop) + 32;
    let activeIndex = 0;
    this.readingLinks().forEach((link, index) => {
      const heading = this.markdownHeading(link.id);
      const headingOffset = paged ? heading?.offsetLeft : heading?.offsetTop;
      if (headingOffset !== undefined && headingOffset <= threshold) activeIndex = index;
    });
    this.chapterIndex = activeIndex;
  }

  private renderCurrentUnit(): void {
    if (!this.article || !this.input) return;
    if (this.format === "txt" && this.source) {
      const link = this.currentLink();
      const tocIndex = link ? this.source.toc.findIndex((item) => item.id === link.id) : this.chapterIndex;
      const document = renderPreparedPlainText(this.source, { chapterIndex: Math.max(0, tocIndex) });
      this.article.innerHTML = document.html;
    } else {
      this.article.innerHTML = this.markdownDocument?.html ?? "";
    }
    this.root?.scrollTo({ left: 0, top: 0, behavior: "auto" });
  }

  private currentTextRange(): { start: number; end: number } {
    if (this.format !== "txt" || !this.source) {
      const length = this.input?.content.length ?? 0;
      return { start: 0, end: length };
    }
    const link = this.currentLink();
    const item = link ? this.source.toc.find((entry) => entry.id === link.id) : undefined;
    return {
      start: item?.startOffset ?? 0,
      end: item?.endOffset ?? this.source.normalized.length
    };
  }

  private chapterIndexForLocator(locator: ReaderLocator): number {
    const links = this.readingLinks();
    if (locator.chapterId) {
      const byId = links.findIndex((item) => item.id === locator.chapterId);
      if (byId >= 0) return byId;
    }
    if (this.format === "txt" && this.source && locator.textOffset !== undefined) {
      const byOffset = links.findIndex((link) => {
        const item = this.source?.toc.find((entry) => entry.id === link.id);
        return Boolean(item && locator.textOffset! >= (item.startOffset ?? 0) && locator.textOffset! < (item.endOffset ?? this.source!.normalized.length));
      });
      if (byOffset >= 0) return byOffset;
    }
    return Math.min(links.length - 1, Math.max(0, Math.floor((clampProgression(locator.progression) ?? 0) * links.length)));
  }

  private localProgressionFromLocator(locator: ReaderLocator): number {
    const range = this.currentTextRange();
    if (locator.textOffset !== undefined && range.end > range.start) {
      return Math.min(1, Math.max(0, (locator.textOffset - range.start) / (range.end - range.start)));
    }
    if (this.format === "txt" && this.input && range.end > range.start) {
      const globalOffset = (clampProgression(locator.progression) ?? 0) * this.input.content.length;
      return Math.min(1, Math.max(0, (globalOffset - range.start) / (range.end - range.start)));
    }
    return clampProgression(locator.progression) ?? 0;
  }

  private async move(direction: -1 | 1): Promise<boolean> {
    this.assertMounted();
    const root = this.root as HTMLDivElement;
    const paged = this.preferences?.readerMode === "paged";
    const viewport = paged ? root.clientWidth : root.clientHeight;
    const max = paged ? root.scrollWidth - root.clientWidth : root.scrollHeight - root.clientHeight;
    const current = paged ? root.scrollLeft : root.scrollTop;
    const target = Math.min(max, Math.max(0, current + direction * Math.max(1, viewport)));
    if (Math.abs(target - current) > 1) {
      root.scrollTo(paged ? { left: target, behavior: "auto" } : { top: target, behavior: "auto" });
      this.emitLocation();
      return true;
    }

    const nextChapter = this.chapterIndex + direction;
    if (this.format !== "txt" || nextChapter < 0 || nextChapter >= this.readingLinks().length) return false;
    this.chapterIndex = nextChapter;
    this.renderCurrentUnit();
    await nextPaint();
    if (direction < 0) {
      if (paged) root.scrollTo({ left: Math.max(0, root.scrollWidth - root.clientWidth), behavior: "auto" });
      else root.scrollTo({ top: Math.max(0, root.scrollHeight - root.clientHeight), behavior: "auto" });
    }
    this.emitLocation();
    return true;
  }

  private scheduleLocation(): void {
    if (this.locationFrame !== undefined) return;
    this.locationFrame = requestAnimationFrame(() => {
      this.locationFrame = undefined;
      this.emitLocation();
    });
  }

  private voidLocationPromise(): void {
    void this.getCurrentLocator().then((locator) => {
      if (locator && !this.destroyed) this.events.emit("location", locator);
    });
  }

  private emitLocation(): void {
    this.voidLocationPromise();
  }

  private emitSelection(): void {
    void this.getSelection().then((selection) => {
      if (!this.destroyed) this.events.emit("selection", selection);
    });
  }

  private unmountDom(): void {
    for (const dispose of this.disposers.splice(0)) dispose();
    this.root?.remove();
    this.root = undefined;
    this.article = undefined;
  }
}
