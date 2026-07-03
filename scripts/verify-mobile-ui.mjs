import { readFileSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import { join } from "node:path";

function read(path) {
  return readFileSync(path, "utf-8");
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
for (const label of ["累计阅读", "阅读时长", "继续阅读", "同步状态"]) {
  assertIncludes("mobile/src/App.tsx", label, `Home page must include ${label}.`);
}
assertIncludes("mobile/src/App.tsx", "openShelfSearch", "Home search action must open the shelf search flow, not only navigate vaguely.");
assertIncludes("mobile/src/App.tsx", "shelfSearchFocusToken", "Shelf search must receive a focus token from the home search action.");
assertIncludes("mobile/src/App.tsx", "searchInputRef.current?.focus()", "Home search action must focus the shelf search input.");
assertIncludes("mobile/src/App.tsx", "打开书架搜索", "Home search button must describe the direct search behavior.");
assertIncludes("mobile/src/styles.css", ".bottom-nav", "Mobile UI must use a bottom navigation layout.");
assertIncludes("mobile/src/styles.css", ".book-grid", "Shelf page must use a book grid layout.");
assertIncludes("mobile/src/styles.css", ".book-list .book-cover", "Shelf list mode must override cover size and behave like a real compact list.");
assertIncludes("mobile/src/styles.css", "width: 46px", "Shelf list mode cover should be compact, not reuse the large grid cover.");
assertIncludes("mobile/src/App.tsx", "bookStorageLabel", "Shelf should explain local book availability with a user-facing helper.");
assertIncludes("mobile/src/App.tsx", "本机可读", "Downloaded/imported books should be labelled as locally readable.");
assertIncludes("mobile/src/App.tsx", "需下载正文", "Synced metadata without file content should be labelled as needing body download.");
assertIncludes("mobile/src/App.tsx", "Boolean(snapshot.shelves.length || snapshot.categories.length || bookTagNames.length)", "Shelf optional filter rails must not render a stray 0 when no rails exist.");
assertIncludes("mobile/src/styles.css", ".inspiration-fab", "Inspiration page must keep quick capture prominent.");
assertIncludes("mobile/src/styles.css", ".compact-inspiration-card", "Inspiration list cards must be compact enough for many ideas.");
assertIncludes("mobile/src/App.tsx", "inspiration-detail-screen", "Inspiration AI/editing actions should live in a detail screen, not on oversized list cards.");
assertIncludes("mobile/src/App.tsx", "inspiration-ai-panel", "Inspiration AI candidates must be moved into the detail panel.");
assertIncludes("mobile/src/styles.css", "overflow-x: hidden", "Mobile app must prevent browser-like horizontal overflow.");
assertIncludes("mobile/src/styles.css", "width: min(100%, 560px)", "Mobile pages must be constrained to the viewport instead of overflowing like a web page.");
assertIncludes("mobile/src/styles.css", ".reader-bottom-sheet", "Mobile reader must use a reader-app style bottom sheet instead of a web-form toolbar.");
assertIncludes("mobile/src/App.tsx", "reader-chapter-control-row", "Reader menu must group previous chapter, progress, and next chapter like a native reader.");
assertIncludes("mobile/src/App.tsx", "reader-actions reader-primary-actions", "Reader menu must expose compact primary entries instead of large floating controls.");
for (const label of ["灵感", "目录", "搜索", "书签", "设置"]) {
  assertIncludes("mobile/src/App.tsx", `<span>${label}</span>`, `Reader compact toolbar must expose ${label}.`);
}
assertIncludes("mobile/src/App.tsx", "reader-stat-row", "Mobile reader must expose compact reading stats like native reading apps.");
assertIncludes("mobile/src/App.tsx", "showLoadingHint", "Reader loading hint must be delayed so fast local books do not flash a loading card.");
assertIncludes("mobile/src/App.tsx", "saveProgress(Number(event.target.value))", "Mobile reader progress slider must still save progress from the reader controls.");
assertIncludes("mobile/src/services/mobile-stats.ts", "export type StatsPeriod", "Mobile stats logic must be extracted from App.tsx for maintainability.");
assertIncludes("mobile/src/services/mobile-stats.ts", "shiftStatsPeriodAnchor", "Stats period navigation must be backed by real date shifting.");
assertIncludes("mobile/src/services/mobile-stats.ts", "isCurrentStatsPeriod", "Stats page must know when the displayed period is current.");
assertIncludes("mobile/src/services/mobile-stats.ts", "buildReadingTimeline", "Stats page must build a real reading timeline from sessions.");
assertIncludes("mobile/src/services/mobile-stats.ts", "buildBookRanking", "Stats page must expose book ranking instead of a placeholder trend card.");
assertIncludes("mobile/src/App.tsx", "reading-heat-strip", "Stats page must show a compact reading heat strip.");
assertIncludes("mobile/src/App.tsx", "statsAnchor", "Stats page must keep a real date anchor for period navigation.");
assertIncludes("mobile/src/App.tsx", "shiftStatsPeriod(-1)", "Stats previous-period button must navigate instead of being decorative.");
assertIncludes("mobile/src/App.tsx", "shiftStatsPeriod(1)", "Stats next-period button must navigate instead of being decorative.");
assertIncludes("mobile/src/App.tsx", "回到当前周期", "Stats top action must reset to the current period.");
assertIncludes("mobile/src/App.tsx", "reading-timeline", "Stats page must show reading timeline records.");
assertIncludes("mobile/src/App.tsx", "book-ranking-list", "Stats page must show book ranking.");
assertIncludes("mobile/src/App.tsx", "note-insight-list", "Stats page must show inspiration and note insights.");
assertIncludes("mobile/src/styles.css", ".stats-period-tabs", "Stats page must keep mobile period tabs polished.");
assertIncludes("mobile/src/styles.css", ".reading-timeline-item", "Stats timeline must use mobile list styling.");
assertIncludes("mobile/src/App.tsx", "CapacitorApp.addListener(\"backButton\"", "Android hardware back must be handled by the app instead of exiting immediately.");
assertIncludes("mobile/src/App.tsx", "再按一次返回键退出应用", "Home page must require a second back press before exiting.");
assertIncludes("mobile/src/App.tsx", "CapacitorApp.exitApp()", "Second back press on home should exit explicitly.");

const appSource = read("mobile/src/App.tsx");
for (const forbidden of ["正在打开书籍", "shelf.name} ·", "category.name} ·", "${tag} ·"]) {
  if (appSource.includes(forbidden)) {
    fail(`Mobile UI should not show stale opening text or noisy zero-count chips: ${forbidden}`);
  }
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
