import type { MobileBook, MobileReaderSettings } from "../../../types/mobile";
import type { ReaderLocatorV2 } from "../../../../../src/types/library";

export type ReaderFormat = "txt" | "markdown" | "epub";
export type ReaderEngineVersion = "legacy" | "v2" | "auto";

export type ReaderLoadPhase =
  | "validating"
  | "opening"
  | "parsing"
  | "mounting"
  | "restoring-location"
  | "ready";

export type ReaderErrorCode =
  | "aborted"
  | "missing-content"
  | "empty-content"
  | "unsupported-format"
  | "invalid-publication"
  | "open-failed"
  | "mount-failed"
  | "restore-failed"
  | "navigation-failed"
  | "destroy-failed"
  | "stale-task";

export type ReaderLocator = ReaderLocatorV2;

export interface ReaderLink {
  id: string;
  title: string;
  href?: string;
  level: number;
  index: number;
}

export interface ReaderPublication {
  bookId: string;
  title: string;
  format: ReaderFormat;
  readingOrder: ReaderLink[];
  tableOfContents: ReaderLink[];
  metadata?: {
    author?: string;
    language?: string;
    publisher?: string;
  };
}

export interface ReaderPreferences {
  fontSize: number;
  lineHeight: number;
  pageMargin: number;
  paragraphSpacing: number;
  readerBackground: MobileReaderSettings["readerBackground"];
  readerMode: MobileReaderSettings["readerMode"];
  fontWeight: MobileReaderSettings["fontWeight"];
}

export interface ReaderOpenInput {
  book: MobileBook;
  format: ReaderFormat;
  /** TXT/Markdown 为原文；EPUB 为项目现有的 base64 归档内容。 */
  content: string;
  preferences: ReaderPreferences;
  signal: AbortSignal;
}

export interface ReaderSelection {
  text: string;
  locator?: ReaderLocator;
}

export interface ReaderDecoration {
  id: string;
  locator: ReaderLocator;
  style: "highlight" | "underline";
  color?: string;
}

export type ReaderEngineEvent = "ready" | "location" | "selection" | "error";
export type ReaderEngineEventPayload = {
  ready: ReaderPublication;
  location: ReaderLocator;
  selection: ReaderSelection | null;
  error: ReaderEngineError;
};
export type ReaderEngineListener<T extends ReaderEngineEvent = ReaderEngineEvent> = (
  payload: ReaderEngineEventPayload[T]
) => void;

export interface ReaderBookmark {
  id: string;
  locator: ReaderLocator;
  createdAt: number;
}

export interface ReaderEngine {
  readonly id: string;
  readonly format: ReaderFormat;
  readonly version: Exclude<ReaderEngineVersion, "auto">;

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
  on<T extends ReaderEngineEvent>(event: T, listener: ReaderEngineListener<T>): () => void;
  destroy(): Promise<void>;
}

export type ReaderState =
  | { status: "idle" }
  | { status: "loading"; bookId: string; taskId: string; phase: ReaderLoadPhase; engineVersion: ReaderEngineVersion }
  | {
      status: "ready";
      bookId: string;
      taskId: string;
      engineVersion: Exclude<ReaderEngineVersion, "auto">;
      engine: ReaderEngine;
      publication: ReaderPublication;
    }
  | {
      status: "error";
      bookId?: string;
      taskId?: string;
      code: ReaderErrorCode;
      message: string;
      retryable: boolean;
    };

export class ReaderEngineError extends Error {
  readonly code: ReaderErrorCode;
  readonly retryable: boolean;
  readonly cause?: unknown;

  constructor(code: ReaderErrorCode, message: string, options: { retryable?: boolean; cause?: unknown } = {}) {
    super(message);
    this.name = "ReaderEngineError";
    this.code = code;
    this.retryable = options.retryable ?? true;
    this.cause = options.cause;
  }
}

export function readerFormatFromBook(book: MobileBook): ReaderFormat {
  if (book.format === "md") return "markdown";
  return book.format;
}

export function readerPreferencesFromSettings(settings: MobileReaderSettings): ReaderPreferences {
  return {
    fontSize: settings.fontSize,
    lineHeight: settings.lineHeight,
    pageMargin: settings.pageMargin,
    paragraphSpacing: settings.paragraphSpacing,
    readerBackground: settings.readerBackground,
    readerMode: settings.readerMode,
    fontWeight: settings.fontWeight
  };
}
