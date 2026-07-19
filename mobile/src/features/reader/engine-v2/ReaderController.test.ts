import { describe, expect, it, vi } from "vitest";
import type { MobileBook, MobileReaderSettings } from "../../../types/mobile";
import { ReaderController } from "./ReaderController";
import { ReaderEngineEvents } from "./events";
import { ReaderEngineFactory } from "./ReaderEngineFactory";
import { ReaderProgressRepository } from "./ReaderProgressRepository";
import type {
  ReaderEngine,
  ReaderEngineEvent,
  ReaderEngineListener,
  ReaderFormat,
  ReaderLocator,
  ReaderOpenInput,
  ReaderPreferences,
  ReaderPublication
} from "./types";

const preferences: ReaderPreferences = {
  fontSize: 18,
  lineHeight: 1.8,
  pageMargin: 20,
  paragraphSpacing: 1,
  readerBackground: "warm",
  readerMode: "paged",
  fontWeight: "regular"
};

const book = (id: string, format: MobileBook["format"] = "txt"): MobileBook => ({
  id,
  title: id,
  filePath: `books/${id}`,
  format,
  importedAt: "2026-07-16T00:00:00.000Z",
  updatedAt: "2026-07-16T00:00:00.000Z",
  size: 100,
  revision: 1,
  deviceId: "test"
});

class FakeEngine implements ReaderEngine {
  readonly events = new ReaderEngineEvents();
  readonly destroy = vi.fn(async () => { this.events.clear(); });
  readonly mount = vi.fn(async () => undefined);
  readonly restore = vi.fn(async (value?: ReaderLocator) => { this.locator = value ?? this.locator; });
  readonly applyPreferences = vi.fn(async (_value: ReaderPreferences) => undefined);
  readonly id: string;
  readonly format: ReaderFormat;
  readonly version: "legacy" | "v2";
  locator: ReaderLocator | null = null;
  publication: ReaderPublication;
  openImpl?: (input: ReaderOpenInput) => Promise<ReaderPublication>;

  constructor(id: string, format: ReaderFormat, version: "legacy" | "v2") {
    this.id = id;
    this.format = format;
    this.version = version;
    this.publication = {
      bookId: id,
      title: id,
      format,
      readingOrder: [{ id: "chapter-1", title: "第一章", level: 1, index: 0 }],
      tableOfContents: [{ id: "chapter-1", title: "第一章", level: 1, index: 0 }]
    };
  }

  open(input: ReaderOpenInput): Promise<ReaderPublication> {
    return this.openImpl?.(input) ?? Promise.resolve({ ...this.publication, bookId: input.book.id });
  }
  getCurrentLocator(): Promise<ReaderLocator | null> { return Promise.resolve(this.locator); }
  goTo(value: ReaderLocator): Promise<void> { this.locator = value; return Promise.resolve(); }
  goToChapter(_chapterId: string): Promise<void> { return Promise.resolve(); }
  goForward(): Promise<boolean> { return Promise.resolve(true); }
  goBackward(): Promise<boolean> { return Promise.resolve(true); }
  getTableOfContents() { return Promise.resolve(this.publication.tableOfContents); }
  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>) { return this.events.on(event, listener); }
}

function createController(options: {
  legacy?: () => FakeEngine;
  v2?: () => FakeEngine;
  save?: ReturnType<typeof vi.fn>;
  load?: () => Promise<never>;
} = {}) {
  const legacy = options.legacy ?? (() => new FakeEngine("legacy", "txt", "legacy"));
  const providers = { txt: legacy, markdown: legacy, epub: legacy };
  const v2Providers = options.v2 ? { txt: options.v2 } : {};
  const save = options.save ?? vi.fn(async () => undefined);
  const repository = new ReaderProgressRepository({ load: options.load ?? (async () => undefined), save }, 60_000);
  return {
    controller: new ReaderController(new ReaderEngineFactory(providers, v2Providers), repository),
    save
  };
}

const openInput = (id: string, content = "正文") => ({
  book: book(id),
  format: "txt" as const,
  content,
  preferences
});

describe("ReaderController", () => {
  it("never enters ready for empty content", async () => {
    const { controller } = createController();
    const state = await controller.open(openInput("empty", "   "), {} as HTMLElement, "legacy");
    expect(state).toMatchObject({ status: "error", code: "empty-content" });
  });

  it("rejects a publication without readable units", async () => {
    const engine = new FakeEngine("invalid", "txt", "legacy");
    engine.publication.readingOrder = [];
    const { controller } = createController({ legacy: () => engine });
    const state = await controller.open(openInput("invalid"), {} as HTMLElement, "legacy");
    expect(state).toMatchObject({ status: "error", code: "invalid-publication" });
    expect(engine.destroy).toHaveBeenCalledTimes(1);
  });

  it("prevents an old open task from overwriting a newer book", async () => {
    let resolveFirst!: (publication: ReaderPublication) => void;
    let markFirstStarted!: () => void;
    const firstStarted = new Promise<void>((resolve) => { markFirstStarted = resolve; });
    const first = new FakeEngine("first-engine", "txt", "legacy");
    first.openImpl = () => new Promise((resolve) => {
      resolveFirst = resolve;
      markFirstStarted();
    });
    const second = new FakeEngine("second-engine", "txt", "legacy");
    let count = 0;
    const { controller } = createController({ legacy: () => (count++ === 0 ? first : second) });
    const firstOpen = controller.open(openInput("book-a"), {} as HTMLElement, "legacy");
    await firstStarted;
    const secondOpen = controller.open(openInput("book-b"), {} as HTMLElement, "legacy");
    resolveFirst({ ...first.publication, bookId: "book-a" });
    await Promise.all([firstOpen, secondOpen]);
    expect(controller.getState()).toMatchObject({ status: "ready", bookId: "book-b" });
    expect(first.destroy).toHaveBeenCalled();
  });

  it("falls back from V2 to Legacy without two progress writers", async () => {
    const v2 = new FakeEngine("v2", "txt", "v2");
    v2.openImpl = async () => { throw new Error("POC failed"); };
    const legacy = new FakeEngine("legacy", "txt", "legacy");
    const save = vi.fn(async () => undefined);
    const { controller } = createController({ v2: () => v2, legacy: () => legacy, save });
    const state = await controller.open(openInput("book-a"), {} as HTMLElement, "auto");
    expect(state).toMatchObject({ status: "ready", engineVersion: "legacy" });
    expect(v2.destroy).toHaveBeenCalledTimes(1);
    legacy.locator = {
      version: 2,
      bookId: "book-a",
      format: "txt",
      progression: 0.4,
      updatedAt: Date.now()
    };
    await controller.flush();
    expect(save).toHaveBeenCalledTimes(1);
    await controller.close();
  });

  it("captures a stable locator before applying reflow preferences", async () => {
    const engine = new FakeEngine("legacy", "txt", "legacy");
    engine.locator = { version: 2, bookId: "book-a", format: "txt", progression: 0.6, textOffset: 600, updatedAt: 1 };
    const { controller } = createController({ legacy: () => engine });
    await controller.open(openInput("book-a"), {} as HTMLElement, "legacy");
    await controller.applyPreferences({ ...preferences, fontSize: 24 });
    expect(engine.applyPreferences).toHaveBeenCalled();
    expect(engine.restore).toHaveBeenLastCalledWith(expect.objectContaining({ textOffset: 600 }));
  });

  it("converts an unavailable V2 provider into a visible error state", async () => {
    const { controller } = createController();
    const state = await controller.open(openInput("book-a"), {} as HTMLElement, "v2");

    expect(state).toMatchObject({ status: "error", code: "unsupported-format" });
  });

  it("converts progress loading failures into a visible error state", async () => {
    const { controller } = createController({ load: async () => { throw new Error("storage unavailable"); } });
    const state = await controller.open(openInput("book-a"), {} as HTMLElement, "legacy");

    expect(state).toMatchObject({ status: "error", code: "open-failed" });
    if (state.status === "error") expect(state.message).toContain("读取阅读进度失败");
  });
});
