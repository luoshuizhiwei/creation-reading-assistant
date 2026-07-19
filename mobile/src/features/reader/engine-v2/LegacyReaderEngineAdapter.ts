import { ReaderEngineEvents } from "./events";
import type {
  ReaderBookmark,
  ReaderDecoration,
  ReaderEngine,
  ReaderEngineEvent,
  ReaderEngineListener,
  ReaderFormat,
  ReaderLink,
  ReaderLocator,
  ReaderOpenInput,
  ReaderPreferences,
  ReaderPublication,
  ReaderSelection
} from "./types";

export interface LegacyReaderBridge {
  open(input: ReaderOpenInput): Promise<ReaderPublication>;
  mount(container: HTMLElement): Promise<void>;
  restore(locator?: ReaderLocator): Promise<void>;
  getCurrentLocator(): Promise<ReaderLocator | null>;
  goTo(locator: ReaderLocator): Promise<void>;
  goToChapter(chapterId: string): Promise<void>;
  goForward(): Promise<boolean>;
  goBackward(): Promise<boolean>;
  getTableOfContents(): Promise<ReaderLink[]>;
  applyPreferences(preferences: ReaderPreferences): Promise<void>;
  addBookmark?(): Promise<ReaderBookmark>;
  getSelection?(): Promise<ReaderSelection | null>;
  addDecoration?(decoration: ReaderDecoration): Promise<void>;
  destroy(): Promise<void>;
}

/**
 * Legacy 适配器只做接口翻译，不拥有书架、同步、灵感或全局路由。
 * 正式接入前仍需为现有 TXT/Markdown/EPUB 组件各自提供 bridge。
 */
export class LegacyReaderEngineAdapter implements ReaderEngine {
  readonly id: string;
  readonly version = "legacy" as const;
  private readonly events = new ReaderEngineEvents();

  constructor(readonly format: ReaderFormat, private readonly bridge: LegacyReaderBridge, id?: string) {
    this.id = id ?? `legacy-${format}-${crypto.randomUUID()}`;
  }

  async open(input: ReaderOpenInput): Promise<ReaderPublication> {
    const publication = await this.bridge.open(input);
    this.events.emit("ready", publication);
    return publication;
  }

  mount(container: HTMLElement): Promise<void> { return this.bridge.mount(container); }
  restore(locator?: ReaderLocator): Promise<void> { return this.bridge.restore(locator); }
  getCurrentLocator(): Promise<ReaderLocator | null> { return this.bridge.getCurrentLocator(); }
  goTo(locator: ReaderLocator): Promise<void> { return this.bridge.goTo(locator); }
  goToChapter(chapterId: string): Promise<void> { return this.bridge.goToChapter(chapterId); }
  goForward(): Promise<boolean> { return this.bridge.goForward(); }
  goBackward(): Promise<boolean> { return this.bridge.goBackward(); }
  getTableOfContents(): Promise<ReaderLink[]> { return this.bridge.getTableOfContents(); }
  applyPreferences(preferences: ReaderPreferences): Promise<void> { return this.bridge.applyPreferences(preferences); }
  addBookmark(): Promise<ReaderBookmark> {
    if (!this.bridge.addBookmark) throw new Error("Legacy 阅读器未提供书签接口。");
    return this.bridge.addBookmark();
  }
  getSelection(): Promise<ReaderSelection | null> { return this.bridge.getSelection?.() ?? Promise.resolve(null); }
  addDecoration(decoration: ReaderDecoration): Promise<void> {
    if (!this.bridge.addDecoration) return Promise.resolve();
    return this.bridge.addDecoration(decoration);
  }
  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>): () => void {
    return this.events.on(event, listener);
  }
  async destroy(): Promise<void> {
    this.events.clear();
    await this.bridge.destroy();
  }
}
