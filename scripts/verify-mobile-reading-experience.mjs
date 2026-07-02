import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-reading-experience] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/App.tsx", "ShelfViewMode", "Shelf must support reading-app style grid/list view switching.");
assertIncludes("mobile/src/App.tsx", "ShelfFilterMode", "Shelf must support quick filters for all/reading/downloaded/pending books.");
assertIncludes("mobile/src/App.tsx", "ShelfSortMode", "Shelf must support reader-oriented sorting.");
assertIncludes("mobile/src/App.tsx", "book-progress-line", "Shelf cards must expose compact reading progress instead of plain web text.");
assertIncludes("mobile/src/App.tsx", "BookDetailSheet", "Shelf must provide a native-reader style book detail sheet.");
assertIncludes("mobile/src/App.tsx", "下载后阅读", "Book detail sheet must guide users to download synced books before reading.");
assertIncludes("mobile/src/App.tsx", "book-detail-insights", "Book detail sheet must include reading records and note insights.");
assertIncludes("mobile/src/App.tsx", "progressFromSessionScroll", "Book detail reading timeline must derive progress from reading session locations.");
assertIncludes("mobile/src/App.tsx", "ReaderDrawerTab", "Reader drawer must support multiple reader tabs.");
assertIncludes("mobile/src/App.tsx", "createReaderSearchResults", "Reader must build in-book search results from the current document.");
assertIncludes("mobile/src/App.tsx", "jumpToReaderSearchResult", "Reader search results must jump to matched text.");
assertIncludes("mobile/src/App.tsx", "readerDrawerTab === \"search\"", "Reader drawer must expose an in-book search tab.");
assertIncludes("mobile/src/App.tsx", "addReaderBookmark", "Reader must allow adding bookmarks at the current location.");
assertIncludes("mobile/src/App.tsx", "addReaderNote", "Reader must allow saving reading notes.");
assertIncludes("mobile/src/App.tsx", "readerDrawerTab === \"bookmarks\"", "Reader drawer must expose a bookmarks tab.");
assertIncludes("mobile/src/App.tsx", "readerDrawerTab === \"notes\"", "Reader drawer must expose a notes tab.");
assertIncludes("mobile/src/App.tsx", "handleReaderTap", "Reader must use tap zones instead of only toolbar buttons.");
assertIncludes("mobile/src/App.tsx", "moveChapter", "Reader tap zones must support previous/next chapter movement.");
assertIncludes("mobile/src/App.tsx", "turnReaderPage", "Reader paged mode must support previous/next page movement.");
assertIncludes("mobile/src/App.tsx", "reader-mode-", "Reader shell must expose the active reading mode to CSS.");
assertIncludes("mobile/src/App.tsx", "tapZoneMode", "Reader must support configurable tap zones.");
assertIncludes("mobile/src/App.tsx", "showProgressBar", "Reader must allow the progress slider to be hidden.");
assertIncludes("mobile/src/App.tsx", "keepAwake", "Reader must expose a screen keep-awake setting.");
assertIncludes("mobile/src/App.tsx", "wakeLock", "Reader keep-awake must use the Screen Wake Lock API where available.");
assertIncludes("mobile/src/App.tsx", "brightness", "Reader must expose brightness control.");
assertIncludes("mobile/src/App.tsx", "paragraphSpacing", "Reader must expose paragraph spacing customization.");
assertIncludes("mobile/src/App.tsx", "reader-progress-chip", "Reader must expose a lightweight chapter/progress chip.");
assertIncludes("mobile/src/App.tsx", "reader-zone-guide", "Reader must show discoverable tap-zone hints when controls are visible.");
assertIncludes("mobile/src/App.tsx", "readerMode", "Reader settings must reserve mode switching for scroll/page style reading.");
assertIncludes("mobile/src/App.tsx", "fontWeight", "Reader settings must include text weight customization.");

assertIncludes("mobile/src/styles.css", ".segmented-control", "Shelf filters must be styled as native-like segmented controls.");
assertIncludes("mobile/src/styles.css", ".book-list", "Shelf must provide a compact list layout.");
assertIncludes("mobile/src/styles.css", ".book-detail-sheet", "Book detail sheet must be styled.");
assertIncludes("mobile/src/styles.css", ".book-detail-timeline-item", "Book detail reading timeline must be styled.");
assertIncludes("mobile/src/styles.css", ".book-detail-note-preview", "Book detail note preview must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-dim-layer", "Reader brightness dim layer must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-drawer-tabs", "Reader drawer tabs must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-search-panel", "Reader in-book search panel must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-search-results", "Reader search results must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-note-item", "Reader notes and bookmarks must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-actions::-webkit-scrollbar", "Reader action bar must be horizontally scrollable instead of cramped on small phones.");
assertIncludes("mobile/src/styles.css", ".reader-progress-chip", "Reader chapter/progress chip must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-zone-guide", "Reader tap-zone hints must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-mode-paged", "Paged reading mode must have dedicated styling.");
assertIncludes("mobile/src/styles.css", ".reader-mode-grid", "Reader mode controls must be styled in the settings drawer.");
assertIncludes("mobile/src/styles.css", "touch-action: pan-y", "Reader body must prioritize vertical reading gestures.");

console.log("[verify-mobile-reading-experience] Mobile reading-app experience guards verified.");
