import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) {
    throw new Error(`[verify-reading-inspiration] ${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
  }
}

assertIncludes("src/types/inspiration.ts", "InspirationSourceSnapshot", "Inspiration must have a structured source snapshot type.");
assertIncludes("src/types/inspiration.ts", "bookTitle", "Source snapshot must keep a book title even if the book is removed later.");
assertIncludes("src/types/inspiration.ts", "locationLabel", "Source snapshot must keep a human readable location label.");
assertIncludes("src/types/inspiration.ts", "excerpt", "Source snapshot must keep selected text as source excerpt.");
assertIncludes("electron/main/index.ts", "normalizeInspirationSource", "Main process must normalize structured inspiration source.");
assertIncludes("src/stores/app-store.ts", "readerReturn", "App state must remember how to return from inspiration to reading.");
assertIncludes("src/features/library/ReaderPage.tsx", "查看灵感", "Text/Markdown reader must not jump away without offering continue/read inspiration choices.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "查看灵感", "EPUB reader must not jump away without offering continue/read inspiration choices.");
assertIncludes("src/features/inspiration/InspirationPage.tsx", "返回阅读", "Inspiration center must expose a return-to-reading action.");
assertIncludes("src/features/inspiration/InspirationPage.tsx", "来源卡片", "Inspiration details must show source in a dedicated card.");
assertIncludes("src/features/inspiration/InspirationPage.tsx", "来源摘录", "Inspiration details must show selected source excerpt separately from body.");
assertIncludes("src/features/inspiration/InspirationPage.tsx", "source?.bookTitle", "Inspiration UI must prefer source book title over raw book id.");

console.log("[verify-reading-inspiration] Reading-to-inspiration guards verified.");

