import type { ReadingLocation } from "../../../../../src/types/library";
import type { ReaderFormat, ReaderLocator } from "./types";

function finiteNumber(value: unknown): number | undefined {
  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export function clampProgression(value: unknown): number | undefined {
  const numberValue = finiteNumber(value);
  if (numberValue === undefined) return undefined;
  return Math.min(1, Math.max(0, numberValue));
}

function optionalNonNegativeInteger(value: unknown): number | undefined {
  const numberValue = finiteNumber(value);
  if (numberValue === undefined) return undefined;
  return Math.max(0, Math.floor(numberValue));
}

export function normalizeReaderLocator(locator: ReaderLocator): ReaderLocator {
  const progression = clampProgression(locator.progression);
  const textOffset = optionalNonNegativeInteger(locator.textOffset);
  const paragraphIndex = optionalNonNegativeInteger(locator.paragraphIndex);
  const position = optionalNonNegativeInteger(locator.epub?.position);
  const totalProgression = clampProgression(locator.epub?.totalProgression);
  const epub = locator.epub && (locator.epub.cfi || position !== undefined || totalProgression !== undefined)
    ? {
        cfi: locator.epub.cfi?.trim() || undefined,
        position,
        totalProgression
      }
    : undefined;
  return {
    version: 2,
    bookId: locator.bookId,
    format: locator.format,
    progression,
    chapterId: locator.chapterId?.trim() || undefined,
    href: locator.href?.trim() || undefined,
    fragment: locator.fragment?.trim() || undefined,
    textOffset,
    paragraphIndex,
    epub,
    updatedAt: Number.isFinite(locator.updatedAt) ? locator.updatedAt : Date.now()
  };
}

export function readerFormatFromLegacy(format: ReadingLocation["format"]): ReaderFormat {
  return format === "md" ? "markdown" : format;
}

export function legacyFormatFromReader(format: ReaderFormat): ReadingLocation["format"] {
  return format === "markdown" ? "md" : format;
}

export function locatorFromLegacyLocation(bookId: string, location?: ReadingLocation): ReaderLocator | undefined {
  if (!location) return undefined;
  const format = readerFormatFromLegacy(location.format);
  const progressPercent = finiteNumber(location.progressPercent);
  return normalizeReaderLocator({
    version: 2,
    bookId,
    format,
    progression: progressPercent === undefined ? undefined : progressPercent / 100,
    chapterId: location.text?.chapterRef ?? location.epub?.chapterRef,
    href: location.epub?.href,
    textOffset: location.text?.charOffset,
    paragraphIndex: location.paragraphIndex,
    epub: format === "epub"
      ? {
          cfi: location.epub?.cfi,
          position: location.epub?.spineIndex,
          totalProgression: progressPercent === undefined ? undefined : progressPercent / 100
        }
      : undefined,
    updatedAt: Date.parse(location.updatedAt) || Date.now()
  });
}

export function legacyLocationFromLocator(locator: ReaderLocator, previous?: ReadingLocation): ReadingLocation {
  const normalized = normalizeReaderLocator(locator);
  const progressPercent = (normalized.progression ?? normalized.epub?.totalProgression ?? 0) * 100;
  const format = legacyFormatFromReader(normalized.format);
  const updatedAt = new Date(normalized.updatedAt).toISOString();
  const mode: ReadingLocation["mode"] = normalized.format === "epub"
    ? "epub-cfi"
    : normalized.textOffset !== undefined
      ? "text-anchor"
      : previous?.mode ?? "page";
  return {
    format,
    mode,
    progressPercent,
    precision: normalized.epub?.cfi || normalized.textOffset !== undefined ? "exact" : "estimated",
    scroll: previous?.scroll,
    text: normalized.format === "epub"
      ? previous?.text
      : {
          ...previous?.text,
          charOffset: normalized.textOffset,
          chapterRef: normalized.chapterId
        },
    epub: normalized.format === "epub"
      ? {
          ...previous?.epub,
          cfi: normalized.epub?.cfi,
          href: normalized.href,
          spineIndex: normalized.epub?.position,
          chapterRef: normalized.chapterId
        }
      : previous?.epub,
    page: previous?.page,
    sourceVersion: previous?.sourceVersion,
    paragraphIndex: normalized.paragraphIndex,
    updatedAt
  };
}

export function serializeReaderLocator(locator: ReaderLocator): string {
  return JSON.stringify(normalizeReaderLocator(locator));
}

export function parseReaderLocator(value: string): ReaderLocator | undefined {
  try {
    const parsed = JSON.parse(value) as Partial<ReaderLocator>;
    if (parsed.version !== 2 || !parsed.bookId || !parsed.format || !["txt", "markdown", "epub"].includes(parsed.format)) {
      return undefined;
    }
    return normalizeReaderLocator(parsed as ReaderLocator);
  } catch {
    return undefined;
  }
}
