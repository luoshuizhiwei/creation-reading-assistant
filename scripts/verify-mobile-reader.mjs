import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-reader] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/reader/mobile-reader.ts", "renderMarkdown", "Mobile reader must render Markdown.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "renderEpubDocument", "Mobile reader must provide EPUB zip rendering.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "inlineEpubImages", "EPUB renderer must inline packaged images for offline mobile reading.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "normalizeEpubBodyMarkup", "EPUB renderer must sanitize body markup while preserving reading semantics.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "mediaTypeFromPath", "EPUB renderer must preserve image MIME types.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "epub-publisher-flow", "EPUB renderer must mark publisher-style chapter flow.");
assertIncludes("mobile/src/reader/mobile-reader.ts", "loading=\"lazy\"", "EPUB images must be lazy-loaded.");
assertIncludes("mobile/src/App.tsx", "MobileReaderView", "Mobile app must expose a dedicated reader view.");
assertIncludes("mobile/src/App.tsx", "readMobileBookContent", "Opening a book must read the real saved local file before falling back to placeholders.");
assertIncludes("mobile/src/App.tsx", "openBookRequestRef", "Opening a book must be guarded against stale async reads.");
assertIncludes("mobile/src/App.tsx", "readerContentCacheRef", "Opening the same local book again should use an in-memory reader content cache.");
assertIncludes("mobile/src/App.tsx", "window.requestAnimationFrame", "Book content reading should be deferred until after the reader view can paint.");
assertIncludes("mobile/src/App.tsx", "readerLoadingState", "Reader must distinguish instant navigation from background content loading.");
assertIncludes("mobile/src/App.tsx", "reader-loading-state", "Reader must show loading inside the reader page, not block on the shelf.");
assertIncludes("mobile/src/App.tsx", "downloadBookToMobile", "Desktop book files must be downloaded explicitly for offline mobile reading.");
assertIncludes("mobile/src/App.tsx", "downloadBookFile", "Mobile sync must call the desktop book file download endpoint.");
assertIncludes("mobile/src/App.tsx", "记为灵感", "Reader must support capturing selected text as inspiration.");
assertIncludes("mobile/src/App.tsx", "readerNotice", "Reader must show local feedback after capturing an inspiration.");
assertIncludes("mobile/src/App.tsx", "查看灵感", "Reader capture feedback must offer a direct way to inspect the saved inspiration.");
assertIncludes("mobile/src/App.tsx", "继续阅读", "Reader capture feedback must let the user continue reading without guessing what happened.");
assertIncludes("mobile/src/App.tsx", "saveMobileReadingProgress", "Reader must persist reading progress.");
assertIncludes("mobile/src/App.tsx", "fontSize", "Reader settings must include font size.");
assertIncludes("mobile/src/App.tsx", "lineHeight", "Reader settings must include line height.");
assertIncludes("mobile/src/App.tsx", "readerBackground", "Reader settings must include background.");
assertIncludes("mobile/src/styles.css", ".reader-content .epub-publisher-flow", "EPUB publisher flow must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-content img", "Reader must style EPUB images.");
assertIncludes("mobile/src/styles.css", ".reader-content figure", "Reader must style EPUB figures.");
assertIncludes("mobile/src/styles.css", ".reader-content a", "Reader must style EPUB links.");
assertIncludes("mobile/src/styles.css", ".reader-content thead", "Reader must style EPUB tables.");
assertIncludes("mobile/src/styles.css", ".reader-bg-night .reader-content img", "Night reading background must keep EPUB images readable.");
assertIncludes("mobile/src/styles.css", ".reader-loading-state", "Reader loading state must be styled inside the reader shell.");

const appSource = read("mobile/src/App.tsx");
if (appSource.includes("const openBook = async")) {
  fail("openBook must not be async because awaiting local file reads blocks the shelf tap response.");
}
const openBookStart = appSource.indexOf("const openBook = (book: MobileBook)");
const setReaderBookIndex = appSource.indexOf("setReaderBook(book);", openBookStart);
const readContentIndex = appSource.indexOf("readMobileBookContent(book)", openBookStart);
if (openBookStart < 0 || setReaderBookIndex < 0 || readContentIndex < 0 || setReaderBookIndex > readContentIndex) {
  fail("openBook must set readerBook before starting readMobileBookContent so local books open immediately.");
}

console.log("[verify-mobile-reader] Mobile reader guards verified.");
