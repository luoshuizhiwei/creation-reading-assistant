import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-home] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  if (!read(file).includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const home = read("mobile/src/features/home/HomePage.tsx");
const sheet = read("mobile/src/features/home/HomeContinueSheet.tsx");
const progress = read("mobile/src/features/shelf/book-progress.ts");
const styles = read("mobile/src/styles.css");
const releaseOverrides = read("mobile/src/styles/release-overrides.css");

for (const label of ["首页", "累计阅读", "阅读时长", "继续阅读", "阅读统计", "最近灵感", "已阅读完成"]) {
  assertIncludes("mobile/src/features/home/HomePage.tsx", label, `Home must render ${label}.`);
}
assertIncludes("mobile/src/features/home/HomePage.tsx", "onOpenInspiration(item.id)", "Recent inspiration rows must open the selected item.");
if (home.includes("今天") || home.includes("同步状态")) {
  fail("Home header must only show 首页 and must not restore 今天 or a sync status card.");
}

const orderedSections = ["继续阅读", "阅读统计", "最近灵感", "已阅读完成"].map((label) => home.indexOf(label));
if (orderedSections.some((index) => index < 0) || orderedSections.some((index, position) => position > 0 && index <= orderedSections[position - 1])) {
  fail("Home sections must stay ordered as continue, stats, inspirations, completed.");
}

for (const guard of ["isBookReadableOnDevice(book)", "hasBookBeenRead(snapshot, book.id)", "progress < 99.5", "!isRemovedFromContinue(snapshot, book.id)"]) {
  if (!progress.includes(guard)) fail(`Continue-reading selector is missing guard: ${guard}`);
}

for (const contract of ["createPortal(sheetContent, document.body)", "mobile-tab-back", "menuView === \"sort\"", "menuView === \"more\"", "actionBookId", "originalIndex"]) {
  if (!sheet.includes(contract)) fail(`Continue sheet is missing interaction contract: ${contract}`);
}

const removeHandler = sheet.match(/const handleRemoveFromContinue[\s\S]*?\n  };/)?.[0] ?? "";
if (!removeHandler.includes("removeBookFromContinue(book.id)") || /saveMobileReadingProgress|deleteMobileBook/.test(removeHandler)) {
  fail("Removing a book from continue-reading must only update local hidden state.");
}

for (const cssContract of [".home-summary-row", "grid-template-columns: repeat(2, minmax(0, 1fr))", ".home-continue-card", "width: min(68vw, 248px)", ".home-completed-card", "width: 116px", ".home-sheet", "position: fixed", "inset: 0", ".home-sheet-body", "overflow-y: auto"]) {
  if (!styles.includes(cssContract)) fail(`Home CSS is missing: ${cssContract}`);
}
for (const darkThemeContract of ["--md3-surface: #17120f", "--md3-on-surface: #f8efe4", "--md3-on-surface-variant: #c8b8a7"]) {
  if (!styles.includes(darkThemeContract)) fail(`System dark mode is missing a complete surface contract: ${darkThemeContract}`);
}
if (!releaseOverrides.includes(':root[data-mobile-theme="system"] .bottom-nav') || !releaseOverrides.includes(':root[data-mobile-theme="dark"] .bottom-nav')) {
  fail("Dark app themes must keep the fixed bottom navigation on the same surface as Home.");
}

if (/\.home-summary-row\s*\{\s*grid-template-columns:\s*1fr;/s.test(styles)) {
  fail("Narrow screens must keep the two summary cards in one row.");
}
if (/\.home-summary-card\s*\{|\.home-section\s*\{/s.test(releaseOverrides)) {
  fail("Home card sizing must not be redefined in release-overrides.css.");
}

console.log("[verify-mobile-home] Mobile home guards verified.");
