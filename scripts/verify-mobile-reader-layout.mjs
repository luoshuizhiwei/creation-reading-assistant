import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-reader-layout] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/App.tsx", "MobileReaderView", "Reader must be split into a dedicated mobile reader component.");
assertIncludes("mobile/src/App.tsx", "reader-scroll-container", "Reader body must use a fixed scroll container.");
assertIncludes("mobile/src/App.tsx", "calculateReaderProgress", "Reader progress must be computed from the active reading mode.");
assertIncludes("mobile/src/App.tsx", "readerControlsVisible", "Reader controls must be hideable while reading.");
assertIncludes("mobile/src/App.tsx", "type ReaderPanel", "Reader non-body tools must use a unified secondary panel.");
assertIncludes("mobile/src/App.tsx", "readerPanel", "Reader must route TOC/search/bookmarks/notes/settings through secondary pages.");
assertIncludes("mobile/src/App.tsx", "mobile-reader-back", "Android back must close reader secondary pages before closing the book.");
assertIncludes("mobile/src/styles.css", ".reader-scroll-container", "Reader scroll container must have dedicated CSS.");
assertIncludes("mobile/src/styles.css", ".reader-chrome-hidden", "Reader chrome must support hidden/low-distraction state.");
assertIncludes("mobile/src/styles.css", ".reader-panel", "Reader secondary panels must be styled as native-like pages.");
assertIncludes("mobile/src/styles.css", ".reader-panel-body", "Reader secondary panels must have a dedicated scrollable body.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "renderEpubDocument", "EPUB rendering must parse chapters instead of showing a generic placeholder.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "JSZip", "EPUB rendering must inspect the EPUB zip structure.");

console.log("[verify-mobile-reader-layout] Mobile reader layout guards verified.");
