/**
 * EPUB 引擎层：从 EpubReaderPage 纯移动而来的位置换算、恢复超时与主题注入逻辑。
 *
 * 页面组件只保留 React 状态与事件编排；本模块承载 epubjs 相关的纯函数，
 * 便于单测覆盖（位置换算与恢复超时是历史上回归最多的两处）。
 * 除导出可见性外，函数体与移动前逐字节一致。
 */
import type { Book, Location as EpubLocation, Rendition } from "epubjs";
import { normalizeEpubHref } from "@/features/library/toc/current";
import { readerTextColor } from "@/utils/format";
import type { LibraryBook, ReaderSettings, ReadingLocation } from "@/types/library";

export const EPUB_INITIAL_DISPLAY_TIMEOUT_MS = 8_000;
const renditionRegisteredThemes = new WeakMap<Rendition, Set<string>>();

export type SidePanelTab = "toc" | "highlights" | "bookmarks";

export function initialEpubLocation(book: LibraryBook, existing?: ReadingLocation): ReadingLocation {
  if (existing?.format === "epub" && existing.mode === "epub-cfi") return existing;
  return {
    format: "epub",
    mode: "epub-cfi",
    progressPercent: existing?.progressPercent ?? 0,
    precision: "estimated",
    epub: {},
    sourceVersion: {
      fileSize: book.size
    },
    updatedAt: new Date().toISOString()
  };
}

function safePercent(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
}

export function hrefMatchesLocation(location: ReadingLocation | undefined, expectedHref?: string): boolean {
  if (!expectedHref) return true;
  const actual = normalizeEpubHref(location?.epub?.href);
  const expected = normalizeEpubHref(expectedHref);
  return Boolean(actual && expected && actual === expected);
}

export async function displayWithTimeout(rendition: Rendition, target?: string): Promise<"displayed" | "timeout"> {
  let timeoutId: number | undefined;
  try {
    return await Promise.race([
      Promise.resolve(rendition.display(target)).then(() => "displayed" as const),
      new Promise<"timeout">((resolve) => {
        timeoutId = window.setTimeout(() => resolve("timeout"), EPUB_INITIAL_DISPLAY_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timeoutId) window.clearTimeout(timeoutId);
  }
}

export async function getDisplayedEpubLocation(rendition: Rendition): Promise<EpubLocation | undefined> {
  const currentLocation = await Promise.resolve(rendition.currentLocation?.());
  if (!currentLocation) return undefined;
  return ("start" in currentLocation ? currentLocation : { start: currentLocation }) as EpubLocation;
}

export function locationFromEpub(book: Book | undefined, libraryBook: LibraryBook, value: EpubLocation | undefined, fallback: ReadingLocation): ReadingLocation {
  const start = value?.start;
  const cfi = typeof start?.cfi === "string" ? start.cfi : fallback.epub?.cfi;
  const href = typeof start?.href === "string" ? start.href : fallback.epub?.href;
  let progressPercent = safePercent(start?.percentage, fallback.progressPercent);
  if (
    progressPercent === 0 &&
    fallback.progressPercent > 0 &&
    normalizeEpubHref(href) === normalizeEpubHref(fallback.epub?.href)
  ) {
    progressPercent = fallback.progressPercent;
  }
  if (book && cfi) {
    try {
      const cfiProgressPercent = safePercent(book.locations.percentageFromCfi(cfi), progressPercent);
      progressPercent =
        cfiProgressPercent === 0 && progressPercent > 0 && normalizeEpubHref(href) === normalizeEpubHref(fallback.epub?.href)
          ? progressPercent
          : cfiProgressPercent;
    } catch {
      progressPercent = safePercent(start?.percentage, progressPercent);
    }
  }
  return {
    format: "epub",
    mode: "epub-cfi",
    progressPercent,
    precision: cfi ? "exact" : "estimated",
    epub: {
      cfi,
      href,
      spineIndex: typeof start?.index === "number" ? start.index : fallback.epub?.spineIndex
    },
    page: start?.displayed
      ? {
          pageIndex: Math.max(0, start.displayed.page - 1),
          pageCount: Math.max(1, start.displayed.total)
        }
      : fallback.page,
    sourceVersion: {
      fileSize: libraryBook.size
    },
    updatedAt: new Date().toISOString()
  };
}

export function applyReaderTheme(rendition: Rendition, settings: ReaderSettings): void {
  const isPublisher = settings.epubStyleMode === "publisher";
  const themeName = isPublisher
    ? settings.readerBackground === "night"
      ? "publisher-preserve-night"
      : "publisher-preserve"
    : `novel-workbench-${settings.readerBackground}`;

  let registered = renditionRegisteredThemes.get(rendition);
  if (!registered) {
    registered = new Set();
    renditionRegisteredThemes.set(rendition, registered);
  }

  const textColor = readerTextColor(settings.readerBackground);
  if (!registered.has(themeName)) {
    const rules: Record<string, Record<string, string>> = {
      body: { background: "transparent !important" }
    };
    if (isPublisher && settings.readerBackground === "night") {
      rules["body, p, div, span, section, article, h1, h2, h3, h4, h5, h6, li, td, th, blockquote"] = {
        color: `${textColor} !important`
      };
      rules.a = { color: "#e0b07b !important" };
    } else if (!isPublisher) {
      rules.body = { color: `${textColor} !important`, background: "transparent !important" };
      rules.a = { color: `${textColor} !important` };
    }
    rendition.themes.register(themeName, rules);
    registered.add(themeName);
  }

  // Register paragraph spacing theme (needs to be re-registered when value changes)
  const paraSpacingThemeName = `para-spacing-${settings.paragraphSpacing ?? 1.0}`;
  if (!registered.has(paraSpacingThemeName)) {
    rendition.themes.register(paraSpacingThemeName, {
      "p, div": { "margin-bottom": `${settings.paragraphSpacing ?? 1.0}em !important` }
    });
    registered.add(paraSpacingThemeName);
  }

  rendition.themes.select(themeName);
  rendition.themes.select(paraSpacingThemeName);
  if (!isPublisher) {
    rendition.themes.override("font-size", `${settings.fontSize}px`, true);
    rendition.themes.override("line-height", `${settings.lineHeight}`, true);
    rendition.themes.override("letter-spacing", `${settings.letterSpacing ?? 0}em`, true);
  }
  if (settings.fontFamily) {
    rendition.themes.override("font-family", `'${settings.fontFamily}', serif`);
  }
}

export function shouldIgnoreKeydown(event: KeyboardEvent): boolean {
  const target = event.target;
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement;
}
