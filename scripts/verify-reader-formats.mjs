import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) {
    throw new Error(`[verify-reader-formats] ${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
  }
}

assertIncludes("package.json", "markdown-it", "Markdown reader must use a full Markdown renderer dependency.");
assertIncludes("src/features/library/ReaderPage.tsx", "MarkdownIt", "Markdown reader must instantiate markdown-it.");
assertIncludes("src/features/library/ReaderPage.tsx", "markdownToc", "Markdown reader must generate a table of contents from headings.");
assertIncludes("src/features/library/ReaderPage.tsx", "reader-heading-scale", "Markdown headings must scale with reader font settings.");
assertIncludes("electron/main/index.ts", "extractTextBookMetadata", "TXT/Markdown import must parse metadata such as author.");
assertIncludes("electron/main/index.ts", "contentHash", "Book imports must calculate a content hash for duplicate detection.");
assertIncludes("electron/main/index.ts", "duplicateIndex", "Book imports must label duplicate imports.");
assertIncludes("src/features/library/LibraryPage.tsx", "importLabel", "Library UI must display duplicate/import labels.");
assertIncludes("src/features/library/LibraryPage.tsx", "role=\"button\"", "Library row/card must be directly clickable to read.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "onWheel", "EPUB reader must support mouse wheel page turning.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "tocCollapsed", "EPUB TOC sidebar must be collapsible.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "settingsDrawerOpen", "EPUB settings must move into a drawer/popup instead of TOC bottom.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "epubStyleMode === \"publisher\"", "EPUB reader must preserve publisher styles by default.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "publisher-preserve-night", "EPUB publisher-style night mode must inject a readable text-color fallback.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "readerTextColor(settings.readerBackground)", "EPUB night fallback must use the configured reader text color.");
assertIncludes("src/features/search/SearchPanel.tsx", "HighlightedText", "Search results must visually highlight matching text.");
assertIncludes("src/pages/StartPage.tsx", "首页搜索", "Start page must expose search as a visible primary action.");
assertIncludes("src/pages/StartPage.tsx", "本地书库", "Start page must keep local library as a primary entry.");
assertIncludes("src/pages/StartPage.tsx", "灵感中心", "Start page must keep inspiration center as a primary entry.");

console.log("[verify-reader-formats] Reader format and search guards verified.");
