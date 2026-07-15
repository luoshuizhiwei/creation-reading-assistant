import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-reading-experience] ${message}`);
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

assertIncludes("mobile/src/features/shelf/shelf-types.ts", "ShelfViewMode", "Shelf must support reading-app style grid/list view switching.");
assertNotIncludes("mobile/src/App.tsx", "ShelfFilterMode", "Shelf must not expose confusing all/reading/downloaded/pending quick filters on the primary shelf.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "shelf-filter-toggle", "Shelf must expose a compact category/tag/shelf filter entry.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "shelf-filter-panel", "Shelf filters must open in a dedicated mobile panel.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "shelf-stats-note", "Shelf should expose compact reading-state summary instead of noisy status filters.");
assertIncludes("mobile/src/features/shelf/shelf-types.ts", "ShelfSortMode", "Shelf must support reader-oriented sorting.");
assertIncludes("mobile/src/features/shelf/BookTile.tsx", "book-progress-line", "Shelf cards must expose compact reading progress instead of plain web text.");
assertIncludes("mobile/src/features/shelf/BookTile.tsx", "book-readiness-pill", "Shelf/home/detail must use one compact readability label for local/synced books.");
assertIncludes("mobile/src/features/shelf/book-status.ts", "getBookReadiness", "Reading surfaces must share one readiness helper instead of scattered status copy.");
assertIncludes("mobile/src/features/shelf/book-status.ts", "formatBookProgress", "Reading surfaces must share compact book progress labels.");
assertIncludes("mobile/src/features/home/HomePage.tsx", "home-continue-card", "Home continue-reading cards must use the compact mobile reader-card component.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "BookDetailSheet", "Shelf must provide a native-reader style book detail sheet.");
assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "reading-book-profile", "Book detail must act as a book profile page, not a generic file detail panel.");
assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "下载后阅读", "Book detail sheet must guide users to download synced books before reading.");
assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "book-detail-insights", "Book detail sheet must include reading records and note insights.");
assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "progressFromSessionScroll", "Book detail reading timeline must derive progress from reading session locations.");
const READER_FILE = "mobile/src/features/reader/MobileReaderView.tsx";
const READER_NAV_HOOK = "mobile/src/features/reader/hooks/useReaderNavigation.ts";
const READER_WAKE_LOCK_HOOK = "mobile/src/features/reader/hooks/useReaderWakeLock.ts";
const READER_PANEL_FILE = "mobile/src/features/reader/components/ReaderPanel.tsx";
const READER_SHEETS_FILE = "mobile/src/features/reader/components/ReaderSheets.tsx";
assertIncludes(READER_FILE, "ReaderDrawerTab", "Reader drawer must support multiple reader tabs.");
assertIncludes(READER_FILE, "createReaderSearchResults", "Reader must build in-book search results from the current document.");
assertIncludes(READER_NAV_HOOK, "jumpToReaderSearchResult", "Reader search results must jump to matched text.");
assertIncludes(READER_FILE, "jumpToReaderProgress", "Reader bookmarks and notes must share a unified progress jump helper.");
assertIncludes(READER_FILE, "finishReaderJump", "Reader jumps must close the drawer and show feedback.");
assertIncludes(READER_NAV_HOOK, "triggerReaderPageTurn", "Reader navigation must trigger lightweight page-turn feedback.");
assertIncludes(READER_FILE, "data-page-turn", "Reader shell must expose page-turn direction to CSS.");
assertIncludes(READER_PANEL_FILE, "readerDrawerTab === \"search\"", "Reader drawer must expose an in-book search tab.");
assertIncludes(READER_FILE, "addReaderBookmark", "Reader must allow adding bookmarks at the current location.");
assertIncludes(READER_FILE, "addReaderNote", "Reader must allow saving reading notes.");
assertIncludes(READER_FILE, "reader-selection-toolbar", "Reader must expose quick actions after text selection.");
assertIncludes(READER_FILE, "searchSelectedText", "Selected text must be reusable for in-book search.");
assertIncludes(READER_FILE, "copySelectedText", "Selected text quick toolbar must support copy feedback.");
assertIncludes(READER_FILE, "readerSessionStartRef", "Reader must track real session duration instead of writing fake fixed sessions.");
assertIncludes(READER_FILE, "activeReadingMs", "Reader bottom sheet must show live reading time.");
assertIncludes(READER_FILE, "readerSpeed", "Reader bottom sheet must estimate live reading speed from progress movement.");
assertIncludes(READER_PANEL_FILE, "readerDrawerTab === \"bookmarks\"", "Reader drawer must expose a bookmarks tab.");
assertIncludes(READER_PANEL_FILE, "readerDrawerTab === \"notes\"", "Reader drawer must expose a notes tab.");
assertIncludes(READER_FILE, "handleReaderTap", "Reader must use tap zones instead of only toolbar buttons.");
assertIncludes(READER_FILE, "moveChapter", "Reader tap zones must support previous/next chapter movement.");
assertIncludes(READER_FILE, "useState(false)", "Reader controls should default to hidden so text is primary.");
assertIncludes(READER_SHEETS_FILE, "reader-progress-panel", "Reader must provide a progress panel with previous/next chapter.");
assertIncludes(READER_SHEETS_FILE, "reader-menu-chip", "Reader progress chip must live in the reader menu instead of floating over text.");
assertIncludes(READER_FILE, "reader-actions reader-primary-actions", "Reader quick actions must be compact in the bottom toolbar.");
assertIncludes(READER_FILE, "showLoadingHint", "Reader loading hint must be delayed so instant local opens do not feel blocked.");
assertIncludes(READER_FILE, "turnReaderPage", "Reader paged mode must support previous/next page movement.");
assertIncludes(READER_FILE, "reader-mode-", "Reader shell must expose the active reading mode to CSS.");
assertIncludes(READER_FILE, "tapZoneMode", "Reader must support configurable tap zones.");
assertIncludes(READER_SHEETS_FILE, "showProgressBar", "Reader must allow the progress slider to be hidden.");
assertIncludes(READER_FILE, "keepAwake", "Reader must expose a screen keep-awake setting.");
assertIncludes(READER_WAKE_LOCK_HOOK, "wakeLock", "Reader keep-awake must use the Screen Wake Lock API where available.");
assertIncludes(READER_FILE, "brightness", "Reader must expose brightness control.");
assertIncludes(READER_FILE, "paragraphSpacing", "Reader must expose paragraph spacing customization.");
assertIncludes(READER_SHEETS_FILE, "reader-progress-chip", "Reader must expose a lightweight chapter/progress chip.");
assertIncludes(READER_FILE, "reader-zone-guide", "Reader must show discoverable tap-zone hints when controls are visible.");
assertIncludes(READER_FILE, "readerMode", "Reader settings must reserve mode switching for scroll/page style reading.");
assertIncludes(READER_FILE, "fontWeight", "Reader settings must include text weight customization.");

assertIncludes("mobile/src/styles.css", ".segmented-control", "Shelf filters must be styled as native-like segmented controls.");
assertIncludes("mobile/src/styles.css", ".home-continue-card", "Home continue-reading cards must have compact mobile reader-card styling.");
assertIncludes("mobile/src/styles.css", ".shelf-reading-summary", "Shelf reading summary must be styled.");
assertIncludes("mobile/src/styles.css", ".book-readiness-pill", "Book readiness labels must be styled consistently.");
assertIncludes("mobile/src/styles.css", ".reading-book-profile", "Book profile detail page must be styled.");
assertIncludes("mobile/src/styles.css", ".book-list", "Shelf must provide a compact list layout.");
assertIncludes("mobile/src/styles.css", ".book-detail-sheet", "Book detail sheet must be styled.");
assertIncludes("mobile/src/styles.css", ".book-detail-timeline-item", "Book detail reading timeline must be styled.");
assertIncludes("mobile/src/styles.css", ".book-detail-note-preview", "Book detail note preview must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-dim-layer", "Reader brightness dim layer must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-drawer-tabs", "Reader drawer tabs must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-search-panel", "Reader in-book search panel must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-search-results", "Reader search results must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-note-item", "Reader notes and bookmarks must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-selection-toolbar", "Reader selected-text quick toolbar must be styled.");
assertIncludes("mobile/src/styles.css", "grid-template-columns: repeat(5, minmax(0, 1fr))", "Reader action bar must use five compact native-like actions.");
assertIncludes("mobile/src/styles.css", ".reader-progress-chip", "Reader chapter/progress chip must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-progress-panel", "Reader progress panel must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-primary-actions", "Reader primary menu row must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-zone-guide", "Reader tap-zone hints must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-mode-paged", "Paged reading mode must have dedicated styling.");
assertIncludes("mobile/src/styles.css", "reader-page-forward", "Reader forward page-turn feedback must be styled.");
assertIncludes("mobile/src/styles.css", "reader-page-backward", "Reader backward page-turn feedback must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-mode-grid", "Reader mode controls must be styled in the settings drawer.");
assertIncludes("mobile/src/styles.css", "touch-action: pan-y", "Reader body must prioritize vertical reading gestures.");

console.log("[verify-mobile-reading-experience] Mobile reading-app experience guards verified.");
