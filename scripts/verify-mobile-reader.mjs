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
assertIncludes("mobile/src/reader/mobile-reader.ts", "extractEpubText", "Mobile reader must provide EPUB text extraction/rendering fallback.");
assertIncludes("mobile/src/App.tsx", "ReaderView", "Mobile app must expose a reader view.");
assertIncludes("mobile/src/App.tsx", "readMobileBookContent", "Opening a book must read the real saved local file before falling back to placeholders.");
assertIncludes("mobile/src/App.tsx", "downloadDesktopBookFiles", "Desktop sync must download book files for offline mobile reading.");
assertIncludes("mobile/src/App.tsx", "downloadBookFile", "Mobile sync must call the desktop book file download endpoint.");
assertIncludes("mobile/src/App.tsx", "记为灵感", "Reader must support capturing selected text as inspiration.");
assertIncludes("mobile/src/App.tsx", "readerNotice", "Reader must show local feedback after capturing an inspiration.");
assertIncludes("mobile/src/App.tsx", "查看灵感", "Reader capture feedback must offer a direct way to inspect the saved inspiration.");
assertIncludes("mobile/src/App.tsx", "继续阅读", "Reader capture feedback must let the user continue reading without guessing what happened.");
assertIncludes("mobile/src/App.tsx", "saveMobileReadingProgress", "Reader must persist reading progress.");
assertIncludes("mobile/src/App.tsx", "fontSize", "Reader settings must include font size.");
assertIncludes("mobile/src/App.tsx", "lineHeight", "Reader settings must include line height.");
assertIncludes("mobile/src/App.tsx", "readerBackground", "Reader settings must include background.");

console.log("[verify-mobile-reader] Mobile reader guards verified.");
