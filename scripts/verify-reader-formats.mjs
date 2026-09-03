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
assertIncludes("src/features/library/toc/markdown-toc.ts", "MarkdownIt", "Markdown reader must instantiate markdown-it.");
assertIncludes("src/features/library/toc/markdown-toc.ts", "renderMarkdownWithToc", "Markdown reader must generate a table of contents from headings (token-stream aligned with rendered ids).");
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
assertIncludes("src/features/search/SearchPanel.tsx", "全局搜索", "Global search must be exposed as a visible primary action.");
assertIncludes("src/components/layout/DesktopFrame.tsx", "资料阅读", "App navigation must keep the library as a primary entry.");
assertIncludes("src/features/inspiration/InspirationPage.tsx", "灵感", "Inspiration center must remain reachable.");

// TXT smart chapter splitting
assertIncludes("src/features/library/ReaderPage.tsx", "splitTxtChapters", "TXT reader must provide smart chapter splitting by heading regex.");
assertIncludes("src/features/library/ReaderPage.tsx", "txtChapters", "TXT reader must compute chapter list from content.");
assertIncludes("src/features/library/ReaderPage.tsx", "txtToc", "TXT reader must generate a table of contents from split chapters.");
assertIncludes("src/features/library/ReaderPage.tsx", "txt-chapter-", "TXT chapter headings must carry stable anchor ids for TOC jump.");
assertIncludes("src/features/library/ReaderPage.tsx", "renderChapterParagraphs", "TXT chapter body must be rendered paragraph-by-paragraph.");
assertIncludes("src/features/library/ReaderPage.tsx", "readerTextColor(settings.readerBackground)", "TXT chapter headings must follow reader text color setting.");
assertIncludes("src/features/library/ReaderPage.tsx", "renderPlainText(convertedContent)", "TXT without detected chapters must fall back to plain paragraph rendering.");
assertIncludes("src/features/library/toc/txt-chapters.ts", "P_REVERSED_VOLUME", "TXT chapter regex must match reversed volume format like 卷一/卷二.");
assertIncludes("src/features/library/toc/txt-chapters.ts", "P_PLATFORM", "TXT chapter regex must cover platform-specific chapters (感言/间章/最终话).");
assertIncludes("src/features/library/toc/txt-chapters.ts", "SNIFF_RULE_ORDER", "TXT chapter detection must auto-sniff numbered-style tocs (1、/一、/【1】).");
assertIncludes("src/features/library/ReaderPage.tsx", "txtChapters.length <= 1", "TXT single-chapter detection must not trigger chapter split rendering.");

// TOC upgrade 2026-09-03: TOC/settings separation + shared TOC interactions
assertIncludes("src/features/library/ReaderPage.tsx", "ReaderSettingsDrawer", "TXT/MD reader settings must live in a drawer, not stacked with the TOC panel.");
assertIncludes("src/features/library/ReaderPage.tsx", "tocCollapsed", "TXT/MD TOC sidebar must be collapsible like the EPUB one.");
assertIncludes("src/features/library/ReaderPage.tsx", "TocList", "TXT/MD TOC must use the shared TocList (highlight/search/collapse).");
assertIncludes("src/features/library/epub-reader/EpubSidePanel.tsx", "TocList", "EPUB TOC tab must use the shared TocList (highlight/search/collapse).");
assertIncludes("src/features/library/toc/current.ts", "findCurrentTocItem", "EPUB current TOC item must prefer exact href-with-fragment matching.");

console.log("[verify-reader-formats] Reader format and search guards verified.");
