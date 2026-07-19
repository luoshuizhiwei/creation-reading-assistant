import type { MobileBook } from "../../../types/mobile";
import type { ReaderEngineVersion } from "./types";

export const READER_ENGINE_VERSION_STORAGE_KEY = "creation-reading-assistant-reader-engine-version";
export const READER_ENGINE_VERSION_BOOK_STORAGE_KEY_PREFIX = `${READER_ENGINE_VERSION_STORAGE_KEY}:book:`;

export type ResolvedReaderEngineVersion = Exclude<ReaderEngineVersion, "auto"> | "native-legado";

export function normalizeReaderEngineVersion(value: unknown): ReaderEngineVersion {
  return value === "v2" || value === "auto" ? value : "legacy";
}

export function loadReaderEngineVersion(storage: Pick<Storage, "getItem"> = localStorage): ReaderEngineVersion {
  try {
    return normalizeReaderEngineVersion(storage.getItem(READER_ENGINE_VERSION_STORAGE_KEY));
  } catch {
    return "legacy";
  }
}

export function saveReaderEngineVersion(
  version: ReaderEngineVersion,
  storage: Pick<Storage, "setItem"> = localStorage
): void {
  try {
    storage.setItem(READER_ENGINE_VERSION_STORAGE_KEY, normalizeReaderEngineVersion(version));
  } catch {
    // 功能开关仅为本机灰度设置，存储不可用时保持 Legacy 默认。
  }
}

export function readerEngineSupportsV2(book: Pick<MobileBook, "format">): boolean {
  return book.format === "txt" || book.format === "md";
}

export function readerEngineSupportsNative(book: Pick<MobileBook, "format">): boolean {
  // Native Canvas pagination is production-ready for plain text first.
  // Markdown keeps the HTML renderer until native Markdown parity is complete.
  return book.format === "txt" || book.format === "epub";
}

function bookStorageKey(bookId: string): string {
  return `${READER_ENGINE_VERSION_BOOK_STORAGE_KEY_PREFIX}${bookId}`;
}

export function loadReaderEngineVersionForBook(
  book: Pick<MobileBook, "id" | "format">,
  storage: Pick<Storage, "getItem"> = localStorage
): ResolvedReaderEngineVersion {
  if (!readerEngineSupportsV2(book) && !readerEngineSupportsNative(book)) return "legacy";
  try {
    const override = storage.getItem(bookStorageKey(book.id));
    if (override === "native-legado" && readerEngineSupportsNative(book)) return override;
    if (override === "v2" || override === "legacy") return override;
    const globalVersion = loadReaderEngineVersion(storage);
    if (globalVersion === "v2") return "v2";
    // TXT and EPUB default to the isolated Android-native Legado core.
    // Non-Android environments and per-book parser failures transparently
    // fall back inside useMobileReaderBook without changing other books.
    return readerEngineSupportsNative(book) ? "native-legado" : "legacy";
  } catch {
    return "legacy";
  }
}

export function saveReaderEngineVersionForBook(
  bookId: string,
  version: ResolvedReaderEngineVersion,
  storage: Pick<Storage, "setItem"> = localStorage
): void {
  try {
    storage.setItem(bookStorageKey(bookId), version);
  } catch {
    // 单书灰度选择仅保存在本机，写入失败时继续使用安全的 Legacy 默认值。
  }
}
