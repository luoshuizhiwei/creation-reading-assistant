import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-reader] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const READER_MAIN = "mobile/src/reader/mobile-reader.ts";
const READER_EPUB = "mobile/src/reader/mobile-reader-epubjs.ts";
const READER_TXT = "mobile/src/reader/mobile-reader-txt.ts";
assertIncludes(READER_MAIN, "renderMarkdown", "Mobile reader must render Markdown.");
assertIncludes(READER_EPUB, "renderEpubDocument", "Mobile reader must provide EPUB zip rendering.");
assertIncludes(READER_EPUB, "inlineEpubImages", "EPUB renderer must inline packaged images for offline mobile reading.");
assertIncludes(READER_EPUB, "normalizeEpubBodyMarkup", "EPUB renderer must sanitize body markup while preserving reading semantics.");
assertIncludes(READER_EPUB, "mediaTypeFromPath", "EPUB renderer must preserve image MIME types.");
assertIncludes(READER_EPUB, "epub-publisher-flow", "EPUB renderer must mark publisher-style chapter flow.");
assertIncludes(READER_EPUB, "loading=\"lazy\"", "EPUB images must be lazy-loaded.");
assertIncludes("mobile/src/App.tsx", "MobileReaderView", "Mobile app must expose a dedicated reader view.");
assertIncludes("mobile/src/App.tsx", "readMobileBookContent", "Opening a book must read the real saved local file before falling back to placeholders.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "openBookRequestRef", "Opening a book must be guarded against stale async reads.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "readerLoadSeqRef", "Reader background loads must use a sequence guard so timed-out promises cannot overwrite newer books.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "setReaderStateIfCurrent", "Reader state writes from background loads must re-check current book and sequence.");
assertIncludes("mobile/src/App.tsx", "readerContentCacheRef", "Opening the same local book again should use an in-memory reader content cache.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "window.requestAnimationFrame", "Book content reading should be deferred until after the reader view can paint.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "export type ReaderPhase", "Reader must use an explicit phase state instead of treating errors as body text.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "\"opening\"", "Reader phase must include opening.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "\"preview\"", "Reader phase must include preview.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "\"loadingFullContent\"", "Reader phase must include background full-content loading.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "\"ready\"", "Reader phase must include ready.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "\"error\"", "Reader phase must include error.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "withTimeout(readMobileBookContent(book), 12_000", "Reader full-content load must have a 12 second timeout.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "statMobileBookFile", "Reader must verify the saved book file still exists before trusting metadata or hot cache.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "\"read_timeout\"", "Reader timeout must enter an error state.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "errorCode: \"not_downloaded\"", "Sync placeholder books must enter a not-downloaded error instead of a blank reader.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "\"missing_content\"", "Reader must distinguish missing local files from empty content.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "errorCode: \"empty_content\"", "Empty content must enter an error state instead of ready.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "lastBackHandledAtRef", "Android back handling must dedupe native and Capacitor events.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "handleMobileBack", "Native and Capacitor back events must share one handler.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "reader-loading-state", "Reader must show loading inside the reader page, not block on the shelf.");
assertIncludes("mobile/src/features/reader/reader-model.ts", "html: \"\"", "Empty reader documents must not render fake empty paragraphs that hide loading/error states.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "content?.trim()", "Reader must treat empty saved file content as missing instead of opening a blank ready state.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerEmptyForBack", "Reader error/empty states must let the Android back button return to the shelf.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerBlockingErrorForBack", "Reader back handling must close the reader when loading fails or content is empty.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "pushMobileReaderHistory", "Reader must push a WebView history entry so Android back can close the reader reliably.");
assertIncludes("mobile/src/hooks/useMobileReaderBook.ts", "handleReaderHistoryBack", "Reader must close on WebView popstate when Android back uses browser history.");
assertIncludes("mobile/src/App.tsx", "appBackStateRef", "Android back handling must use one stable listener with latest app state.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "mobile-native-back", "Mobile app must listen for the native Android back bridge event.");
assertIncludes("mobile/android/app/src/main/java/local/creationReadingAssistant/mobile/MainActivity.java", "OnBackPressedCallback", "Android MainActivity must bridge native back presses into the WebView.");
assertIncludes("mobile/android/app/src/main/java/local/creationReadingAssistant/mobile/MainActivity.java", "mobile-native-back", "Android MainActivity must dispatch the native back event to JavaScript.");
assertIncludes("mobile/src/App.tsx", "downloadBookToMobile", "Desktop book files must be downloaded explicitly for offline mobile reading.");
assertIncludes("mobile/src/hooks/useMobileSync.ts", "downloadBookFile", "Mobile sync must call the desktop book file download endpoint.");
assertIncludes(READER_TXT, "TXT_VIRTUAL_CHAPTER_CHARS", "Large TXT books must be split into virtual chapters instead of one full-book DOM node.");
assertIncludes(READER_TXT, "TXT_MAX_RENDER_CHARS", "TXT renderer must cap the amount of text rendered into the WebView at one time.");
assertIncludes(READER_TXT, "startOffset", "TXT virtual chapters must preserve source offsets for stable navigation.");
assertIncludes("mobile/src/reader/mobile-reader-types.ts", "currentTocIndex", "Reader documents must expose the currently rendered chapter index.");
assertIncludes(READER_MAIN, "renderMobileDocument(format: BookFormat, content: string, title: string, options", "Mobile document renderer must accept chapter render options.");
assertIncludes("mobile/src/reader/mobile-reader-types.ts", "EPUB_INLINE_IMAGES_BY_DEFAULT = false", "EPUB renderer must avoid inlining packaged images by default so first open stays responsive.");
assertIncludes("mobile/src/reader/mobile-reader-types.ts", "EPUB_INLINE_IMAGE_MAX_BYTES", "EPUB renderer must cap individual image size to avoid WebView memory pressure.");
assertIncludes(READER_EPUB, "skipEpubImagesForStability", "EPUB renderer must have a fallback to skip images when inlining is disabled.");
assertIncludes(READER_EPUB, "epubStructureCache", "EPUB renderer must cache parsed structure for faster chapter switches and reopens.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerChapterIndex", "Reader view must track which virtual chapter is currently rendered.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "setReaderChapterIndex", "Reader chapter navigation must re-render the requested chapter.");

// P0: EPUB navigation files must not be rendered as body chapters, and internal links must be intercepted.
assertIncludes(READER_EPUB, "isEpubNavigationHref", "EPUB renderer must filter navigation files from readable spine.");
assertIncludes(READER_EPUB, "readableSpineItems", "EPUB renderer must build a readable spine after filtering navigation files.");
assertIncludes(READER_EPUB, "nav\\.x?html?$", "EPUB navigation filter must match nav.xhtml files.");
assertIncludes(READER_EPUB, "toc\\.x?html?$", "EPUB navigation filter must match toc.xhtml files.");
assertIncludes(READER_EPUB, "totalChapters: readableSpineItems.length", "EPUB total chapters must count readable spine items, not raw spine ids.");
assertIncludes(READER_EPUB, "data-reader-href", "EPUB renderer must convert internal links to data-reader-href for app interception.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "data-reader-href", "Reader view must intercept EPUB internal links via data-reader-href.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "jumpToChapter(tocItem)", "Reader view must jump to the chapter mapped from an EPUB internal link.");
assertIncludes("mobile/src/reader/mobile-reader-types.ts", "href?: string", "Reader TOC items must support href for EPUB link mapping.");
assertIncludes("mobile/src/features/reader/reader-navigation.ts", "readerBookProgressFromLocal", "Reader must convert current-chapter progress into full-book progress.");
assertIncludes("mobile/src/features/reader/reader-navigation.ts", "readerLocalProgressFromBook", "Reader must restore full-book progress inside the current rendered chapter.");
assertIncludes(READER_MAIN, "LARGE_MARKDOWN_PLAIN_TEXT_THRESHOLD", "Large Markdown books must degrade to plain text instead of blocking the WebView.");
assertIncludes("mobile/src/styles.css", ".reader-preformatted", "Reader must style lightweight TXT blocks for native reading.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "记为灵感", "Reader must support capturing selected text as inspiration.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerNotice", "Reader must show local feedback after capturing an inspiration.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "查看灵感", "Reader capture feedback must offer a direct way to inspect the saved inspiration.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "继续阅读", "Reader capture feedback must let the user continue reading without guessing what happened.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "loadMobileSnapshot", "Progress saves must merge into the latest snapshot instead of overwriting concurrent notes or inspirations.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "progressWriteQueueRef", "Progress writes must be serialized to prevent out-of-order persistence.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "saveMobileReadingProgress", "Reader navigation must persist reading progress.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "flushProgress", "Reader must expose an immediate progress flush for lifecycle transitions.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "flushProgress", "Reader close and background transitions must flush pending progress.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "appStateChange", "Reader must save progress when the Android app enters the background.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "EPUB_OPEN_TIMEOUT_MS", "EPUB opening must have a hard timeout instead of hanging indefinitely.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "lastTouchTapAtRef", "EPUB tap handling must prevent synthetic click and touch events from firing twice.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "hooks.content.register(bindContentInteractions)", "EPUB gestures must bind directly to each iframe document on Android WebView.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "item.linear !== false", "EPUB reader must exclude non-linear cover sections represented as booleans by epubjs.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "restoredSection && isReadableEpubSpineItem(restoredSection)", "EPUB reader must not restore users into a non-linear cover section.");
assertIncludes("mobile/src/features/reader/components/EpubReaderView.tsx", "contentDocument.addEventListener(\"touchend\"", "EPUB iframe content must receive direct swipe/tap handling.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "fontSize", "Reader settings must include font size.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "lineHeight", "Reader settings must include line height.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerBackground", "Reader settings must include background.");
assertIncludes("mobile/src/styles.css", ".reader-content .epub-publisher-flow", "EPUB publisher flow must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-content img", "Reader must style EPUB images.");
assertIncludes("mobile/src/styles.css", ".reader-content figure", "Reader must style EPUB figures.");
assertIncludes("mobile/src/styles.css", ".reader-content a", "Reader must style EPUB links.");
assertIncludes("mobile/src/styles.css", ".reader-content thead", "Reader must style EPUB tables.");
assertIncludes("mobile/src/styles.css", ".reader-bg-night .reader-content img", "Night reading background must keep EPUB images readable.");
assertIncludes("mobile/src/styles.css", ".reader-loading-state", "Reader loading state must be styled inside the reader shell.");

const appSource = read("mobile/src/App.tsx");
for (const forbiddenLocalReaderHelper of [
  "type ReaderPhase",
  "interface ReaderState",
  "const defaultReaderSettings",
  "function createReaderSearchResults(",
  "function jumpToReaderSearchResult(",
  "function calculateReaderProgress(",
  "function scrollReaderToPercent(",
  "function findCurrentChapter("
]) {
  if (appSource.includes(forbiddenLocalReaderHelper)) {
    fail(`App.tsx should not re-embed extracted reader helpers: ${forbiddenLocalReaderHelper}`);
  }
}
if (appSource.includes("这本书的正文没有读到内容。请返回书架重新导入")) {
  fail("Reader must not put missing-content error copy into reader body and mark it ready.");
}
if (appSource.includes("const openBook = async")) {
  fail("openBook must not be async because awaiting local file reads blocks the shelf tap response.");
}
const readerSource = read("mobile/src/reader/mobile-reader.ts");
if (readerSource.includes("html.push(chapter.html)") || readerSource.includes("for (const [index, id] of spineIds.entries())")) {
  fail("EPUB renderer must not parse and append every spine chapter during initial open.");
}
const readerBookHookSource = read("mobile/src/hooks/useMobileReaderBook.ts");
const openBookStart = readerBookHookSource.indexOf("const openBook = (book: MobileBook)");
const setReaderBookIndex = readerBookHookSource.indexOf("setReaderBook(book);", openBookStart);
const readContentIndex = readerBookHookSource.indexOf("readMobileBookContent(book)", openBookStart);
if (openBookStart < 0 || setReaderBookIndex < 0 || readContentIndex < 0 || setReaderBookIndex > readContentIndex) {
  fail("openBook must set readerBook before starting readMobileBookContent so local books open immediately.");
}

// Stage 1: TXT/Markdown content anchor (charOffset) support.
assertIncludes("mobile/src/reader/mobile-reader-types.ts", "fullText?: string", "MobileReaderDocument must carry fullText for content-anchor positioning.");
assertIncludes("mobile/src/reader/mobile-reader-txt.ts", "fullText: normalized", "TXT renderer must expose full original text for anchor calculation.");
assertIncludes("mobile/src/services/mobile-storage-reading.ts", "extras: Partial<ReadingLocation>", "Reading location factory must accept extra fields for content anchors.");
assertIncludes("mobile/src/services/mobile-storage-reading.ts", "locationExtras: Partial<ReadingLocation>", "saveMobileReadingProgress must accept location extras.");
assertIncludes("mobile/src/features/reader/reader-navigation.ts", "readerTocIndexFromCharOffset", "Reader navigation must map charOffset back to chapter index.");
assertIncludes("mobile/src/features/reader/reader-navigation.ts", "readerCharOffsetFromViewport", "Reader navigation must compute global char offset from viewport.");
assertIncludes("mobile/src/features/reader/reader-navigation.ts", "readerLocationExtrasFromViewport", "Reader navigation must build location extras from viewport.");
assertIncludes("mobile/src/features/reader/hooks/useReaderNavigation.ts", "readerLocationExtrasFromViewport", "Navigation hook must use location extras helper.");
const navigationHookSource = read("mobile/src/features/reader/hooks/useReaderNavigation.ts");
if (!navigationHookSource.includes("saveProgress(nextProgress, extras)") && !navigationHookSource.includes("saveProgressRef.current(nextProgress, extras)")) {
  fail("Navigation hook must pass location extras when saving progress.");
}
assertIncludes("mobile/src/features/reader/hooks/useReaderDocument.ts", "initialLocation?: ReadingLocation", "Document hook must accept initial location for chapter restore.");
assertIncludes("mobile/src/features/reader/hooks/useReaderDocument.ts", "readerTocIndexFromCharOffset", "Document hook must restore chapter from charOffset.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "savedReadingLocation", "Reader view must read saved location for restore.");
assertIncludes("mobile/src/features/reader/MobileReaderView.tsx", "readerLocationExtrasFromViewport", "Reader view must compute location extras on close.");

console.log("[verify-mobile-reader] Mobile reader guards verified.");
