import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-reader-layout] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

function assertNotIncludes(file, needle, message) {
  const content = read(file);
  if (content.includes(needle)) fail(`${message}\nUnexpected ${JSON.stringify(needle)} in ${file}`);
}

const appPath = "mobile/src/App.tsx";
const readerViewPath = "mobile/src/features/reader/MobileReaderView.tsx";

assertIncludes(appPath, "MobileReaderView", "Reader must be split into a dedicated mobile reader component.");
assertIncludes(readerViewPath, "reader-scroll-container", "Reader body must use a fixed scroll container.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "calculateReaderProgress", "Reader progress must be computed from the active reading mode.");
assertIncludes(readerViewPath, "readerControlsVisible", "Reader controls must be hideable while reading.");
assertIncludes(readerViewPath, "type ReaderPanel", "Reader non-body tools must use a unified secondary panel.");
assertIncludes(readerViewPath, "readerPanel", "Reader must route TOC/search/bookmarks/notes/settings through secondary pages.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "mobile-reader-back", "Android back must close reader secondary pages before closing the book.");
assertIncludes("mobile/src/styles.css", ".reader-scroll-container", "Reader scroll container must have dedicated CSS.");
assertIncludes("mobile/src/styles.css", ".reader-chrome-hidden", "Reader chrome must support hidden/low-distraction state.");
assertIncludes("mobile/src/styles.css", ".reader-panel", "Reader secondary panels must be styled as native-like pages.");
assertIncludes("mobile/src/styles.css", ".reader-panel-body", "Reader secondary panels must have a dedicated scrollable body.");
assertIncludes("mobile/src/reader/mobile-reader-epubjs.ts", "renderEpubDocument", "EPUB rendering must parse chapters instead of showing a generic placeholder.");
assertIncludes("mobile/src/reader/mobile-reader-epubjs.ts", "ePub", "EPUB rendering must use epubjs to inspect the book structure.");

// P0: TXT paged mode page-width model consistency.
// JS pageStep must be derived from the actual CSS column stride, not from a hand-maintained duplicate formula.
const readerNavHook = "mobile/src/features/reader/hooks/useReaderNavigation.ts";
const readerNavSource = read(readerNavHook);
assertIncludes(readerNavHook, "window.getComputedStyle", "Paged page step must read the rendered CSS column metrics.");
assertIncludes(readerNavHook, "computed.columnWidth", "Paged page step must use CSS columnWidth.");
assertIncludes(readerNavHook, "computed.columnGap", "Paged page step must include CSS columnGap.");
assertNotIncludes(readerNavHook, "clientWidth - contentPadding", "Paged turn must not compute step as clientWidth minus padding; that no longer matches CSS column width.");
if (!/columnWidth\s*\+\s*\(\s*Number\.isFinite\(columnGap\)\s*\?\s*columnGap\s*:\s*0\s*\)/s.test(readerNavSource)) {
  fail("getPagedStep must return columnWidth + columnGap so JS turns by the actual CSS column stride.");
}

const readerCss = "mobile/src/styles/reader.css";
assertIncludes(readerViewPath, "reader-format-${book.format}", "Reader shell must expose the active format so TXT/Markdown pagination cannot leak into EPUB.");
assertIncludes(readerCss, ".reader-mode-paged.reader-format-txt .reader-scroll-container", "TXT paged mode must use format-scoped layout rules.");
assertIncludes(readerCss, ".reader-mode-paged.reader-format-md .reader-scroll-container", "Markdown paged mode must use format-scoped layout rules.");
assertIncludes(readerCss, "height: 100vh", "Paged reader must use a WebView-safe fixed viewport height.");
assertIncludes(readerCss, "column-width: calc(100vw - var(--reader-page-margin", "Paged columns must use a valid viewport length minus the reader margins.");
assertIncludes(readerCss, "column-gap: calc(var(--reader-page-margin", "Paged column stride must include both page margins.");
assertIncludes(readerCss, "touch-action: pan-y", "Paged content must preserve vertical gestures while horizontal turns are handled by the reader.");
assertNotIncludes(readerCss, "column-width: 100%", "CSS column-width does not accept percentages; use a length value.");

const releaseOverrides = "mobile/src/styles/release-overrides.css";
assertNotIncludes(releaseOverrides, ".reader-mode-paged .reader-scroll-container", "Release overrides must not replace the format-scoped pagination model.");
assertNotIncludes(releaseOverrides, ".reader-mode-paged .reader-content section", "Release overrides must not reintroduce section-padding pagination that clips Markdown pages.");

console.log("[verify-mobile-reader-layout] Mobile reader layout guards verified.");
