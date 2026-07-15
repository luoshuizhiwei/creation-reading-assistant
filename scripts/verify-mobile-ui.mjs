import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-ui] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/App.tsx", "MainTab = \"home\" | \"shelf\" | \"inspiration\" | \"stats\" | \"profile\"", "Android app must use the five Reeden-style top-level tabs.");
for (const label of ["首页", "书架", "灵感", "统计", "我的"]) {
  assertIncludes("mobile/src/App.tsx", label, `Bottom navigation must expose ${label}.`);
}
for (const label of ["累计阅读", "阅读时长", "继续阅读", "阅读统计", "最近灵感", "已阅读完成"]) {
  assertIncludes("mobile/src/features/home/HomePage.tsx", label, `Home page must include ${label}.`);
}
if (read("mobile/src/features/home/HomePage.tsx").includes("同步状态")) {
  fail("Home page must keep sync status in Profile instead of rendering a sync status card.");
}
assertIncludes("mobile/src/features/home/HomePage.tsx", "全局搜索", "Home search action must clearly open global search.");
assertIncludes("mobile/src/App.tsx", "GlobalSearchOverlay", "Mobile app must expose the global search overlay.");
assertIncludes("mobile/src/App.tsx", "setFocusedInspirationId(inspirationId)", "Global search must open the selected inspiration instead of only switching tabs.");
assertIncludes("mobile/src/App.tsx", "setActiveProfilePage(\"notes\")", "Global search must open the notes page for a selected note.");
assertIncludes("mobile/src/features/search/GlobalSearchOverlay.tsx", "renderHighlightedText", "Search highlighting must be rendered as React nodes.");
if (read("mobile/src/features/search/GlobalSearchOverlay.tsx").includes("dangerouslySetInnerHTML")) {
  fail("Global search must not inject user content through dangerouslySetInnerHTML.");
}
assertIncludes("mobile/src/features/home/HomePage.tsx", "home-continue-strip", "Home continue-reading UI should live in the home feature module.");
if (read("mobile/src/features/shelf/book-progress.ts").includes("snapshot.progress.sort(")) {
  fail("Shelf/home progress derivation must not mutate snapshot.progress while sorting.");
}
assertIncludes("mobile/src/styles.css", ".bottom-nav", "Mobile UI must use a bottom navigation layout.");
assertIncludes("mobile/src/styles.css", ".book-grid", "Shelf page must use a book grid layout.");
assertIncludes("mobile/src/styles.css", ".book-list .book-cover", "Shelf list mode must override cover size and behave like a real compact list.");
assertIncludes("mobile/src/styles.css", "width: 46px", "Shelf list mode cover should be compact, not reuse the large grid cover.");
assertIncludes("mobile/src/features/shelf/BookTile.tsx", "bookStorageLabel", "Shelf should explain local book availability with a user-facing helper.");
assertIncludes("mobile/src/features/shelf/book-status.ts", "本机可读", "Downloaded/imported books should be labelled as locally readable.");
assertIncludes("mobile/src/features/shelf/book-status.ts", "需下载正文", "Synced metadata without file content should be labelled as needing body download.");
assertIncludes("mobile/src/features/shelf/BookTile.tsx", "React.memo", "Shelf book tiles must stay split into a memoized component outside App.tsx.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "shelf-book-action-sheet", "Shelf book cards must open a mobile action sheet instead of an inline web menu.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "删除书籍", "Shelf action sheet must expose direct deletion.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "requestDeleteBook", "Shelf page must be able to delete books directly from the shelf management menu.");
assertIncludes("mobile/src/styles.css", ".shelf-book-action-sheet", "Shelf book management sheet must have mobile-native styling.");
assertIncludes("mobile/src/features/shelf/shelf-options.ts", "shelfSortOptions", "Shelf sort options should live beside shelf feature code instead of App.tsx.");
assertIncludes("mobile/src/features/shelf/shelf-selectors.ts", "filterAndSortShelfBooks", "Shelf filtering and sorting should live beside shelf feature code instead of App.tsx.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "export function ShelfPage", "Shelf page should live in the shelf feature module instead of App.tsx.");
assertIncludes("mobile/src/features/shelf/BookDetailSheet.tsx", "function BookDetailSheet", "Book detail page should live beside the shelf feature module instead of App.tsx.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "filterAndSortShelfBooks", "Shelf page should call the extracted shelf selector instead of re-embedding filter and sort logic.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "Boolean(snapshot.shelves.length || snapshot.categories.length || bookTagNames.length)", "Shelf optional filter rails must not render a stray 0 when no rails exist.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "shelf-sort-select", "Shelf sorting must use one native-like dropdown field instead of uneven chips.");
assertIncludes("mobile/src/features/shelf/ShelfPage.tsx", "setSortMode(event.target.value as ShelfSortMode)", "Shelf sorting dropdown must update the active sort mode.");
assertIncludes("mobile/src/styles.css", ".shelf-sort-select", "Shelf sorting dropdown must be styled in the same toolbar position.");
assertIncludes("mobile/src/styles.css", "align-items: stretch", "Shelf list mode must stretch every row to the same width.");
assertIncludes("mobile/src/styles.css", "max-width: none", "Shelf list rows must not shrink shorter than neighbouring rows.");
assertIncludes("mobile/src/styles.css", ".inspiration-topbar", "Inspiration page must keep search and creation prominent in a mobile top bar.");
assertIncludes("mobile/src/styles.css", ".inspiration-record", "Inspiration list cards must be compact enough for many ideas.");
assertIncludes("mobile/src/features/inspiration/InspirationPage.tsx", "export function InspirationPage", "Inspiration page should live in the inspiration feature module instead of App.tsx.");
assertIncludes("mobile/src/features/inspiration/InspirationDetailPanel.tsx", "inspiration-detail-v2", "Inspiration AI/editing actions should live in a detail screen, not on oversized list cards.");
assertIncludes("mobile/src/features/inspiration/InspirationDetailPanel.tsx", "inspiration-ai-v2", "Inspiration AI candidates must be moved into the detail panel.");
assertIncludes("mobile/src/styles.css", "overflow-x: hidden", "Mobile app must prevent browser-like horizontal overflow.");
assertIncludes("mobile/src/styles.css", "width: min(100%, 560px)", "Mobile pages must be constrained to the viewport instead of overflowing like a web page.");
assertIncludes("mobile/src/styles.css", ".reader-bottom-sheet", "Mobile reader must use a reader-app style bottom sheet instead of a web-form toolbar.");
const READER_FILE = "mobile/src/features/reader/MobileReaderView.tsx";
const READER_SHEETS_FILE = "mobile/src/features/reader/components/ReaderSheets.tsx";
assertIncludes(READER_SHEETS_FILE, "reader-progress-panel", "Reader menu must provide a progress panel with previous/next chapter.");
assertIncludes(READER_FILE, "reader-actions reader-primary-actions", "Reader menu must expose compact primary entries instead of large floating controls.");
for (const label of ["灵感", "目录", "进度", "主题", "设置"]) {
  assertIncludes(READER_FILE, `<span>${label}</span>`, `Reader compact toolbar must expose ${label}.`);
}
assertIncludes(READER_SHEETS_FILE, "reader-stat-row", "Mobile reader must expose compact reading stats like native reading apps.");
assertIncludes(READER_FILE, "showLoadingHint", "Reader loading hint must be delayed so fast local books do not flash a loading card.");
assertIncludes(READER_SHEETS_FILE, "void saveProgress(next)", "Mobile reader progress slider must still save progress from the reader controls.");
const mobileStatsSource = read("mobile/src/services/mobile-stats.ts");
if (!mobileStatsSource.includes("StatsPeriod")) {
  fail("Mobile stats logic must be extracted from App.tsx for maintainability.");
}
assertIncludes("mobile/src/services/mobile-stats.ts", "shiftStatsPeriodAnchor", "Stats period navigation must be backed by real date shifting.");
assertIncludes("mobile/src/services/mobile-stats.ts", "isCurrentStatsPeriod", "Stats page must know when the displayed period is current.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "export function StatsPage", "Stats page should live in the stats feature module instead of App.tsx.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "statsAnchor", "Stats page must keep a real date anchor for period navigation.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "shiftStatsPeriod(-1)", "Stats previous-period button must navigate instead of being decorative.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "shiftStatsPeriod(1)", "Stats next-period button must navigate instead of being decorative.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "stats-trend-chart", "Stats page must show a reading trend chart.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "stats-status-list", "Stats page must show book status breakdown.");
assertIncludes("mobile/src/features/stats/StatsPage.tsx", "stats-creation-grid", "Stats page must show inspiration and note counts.");
assertIncludes("mobile/src/styles.css", ".stats-period-tabs", "Stats page must keep mobile period tabs polished.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "CapacitorApp.addListener(\"backButton\"", "Android hardware back must be handled by the app instead of exiting immediately.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "再按一次返回键退出应用", "Home page must require a second back press before exiting.");
assertIncludes("mobile/src/hooks/useAppBackHandler.ts", "CapacitorApp.exitApp()", "Second back press on home should exit explicitly.");

const appSource = read("mobile/src/App.tsx");
const styleSource = read("mobile/src/styles.css");
for (const forbiddenLocalModule of ["function HomePage(", "function ShelfPage(", "function BookDetailSheet(", "function StatsPage(", "function InspirationPage(", "const BookTile", "function getContinueBooks(", "function progressFor(", "function formatDuration("]) {
  if (appSource.includes(forbiddenLocalModule)) {
    fail(`Mobile App.tsx should stay as an orchestrator and not re-embed extracted modules: ${forbiddenLocalModule}`);
  }
}
for (const forbidden of ["正在打开书籍", "shelf.name} ·", "category.name} ·", "${tag} ·", "shelf-sort-chips", "option.value === \"recent\" && sortMode === \"recent\" ? \" ▾\" : \"\""]) {
  if (appSource.includes(forbidden)) {
    fail(`Mobile UI should not show stale opening text or noisy zero-count chips: ${forbidden}`);
  }
}
if (styleSource.includes("shelf-sort-chips")) {
  fail("Mobile shelf sorting must not keep stale shelf-sort-chips CSS after switching to a native dropdown.");
}

function walkFiles(dir) {
  return readdirSync(dir)
    .flatMap((entry) => {
      const path = join(dir, entry);
      if (statSync(path).isDirectory()) return walkFiles(path);
      return path;
    })
    .filter((path) => /\.(ts|tsx|css|mjs)$/.test(path));
}

for (const file of [...walkFiles("mobile/src"), ...walkFiles("scripts")]) {
  const content = read(file);
  if (/readingGoals|MobileReadingGoal|addMobileReadingGoal|deleteMobileReadingGoal|阅读目标/.test(content) && file !== "scripts\\verify-mobile-ui.mjs" && file !== "scripts/verify-mobile-ui.mjs") {
    fail(`Mobile app must not keep the crossed-out reading-goal feature in code: ${file}`);
  }
}

console.log("[verify-mobile-ui] Mobile Reeden-style UI guards verified.");
