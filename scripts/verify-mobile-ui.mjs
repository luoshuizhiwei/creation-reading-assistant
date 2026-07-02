import { readFileSync } from "node:fs";

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
assertIncludes("mobile/src/App.tsx", "阅读目标不在本应用中启用", "Home page must explicitly exclude the crossed-out reading goal feature.");
assertIncludes("mobile/src/styles.css", ".bottom-nav", "Mobile UI must use a bottom navigation layout.");
assertIncludes("mobile/src/styles.css", ".book-grid", "Shelf page must use a book grid layout.");
assertIncludes("mobile/src/styles.css", ".inspiration-fab", "Inspiration page must keep quick capture prominent.");
assertIncludes("mobile/src/styles.css", "overflow-x: hidden", "Mobile app must prevent browser-like horizontal overflow.");
assertIncludes("mobile/src/styles.css", "width: min(100%, 560px)", "Mobile pages must be constrained to the viewport instead of overflowing like a web page.");
assertIncludes("mobile/src/styles.css", ".reader-bottom-sheet", "Mobile reader must use a reader-app style bottom sheet instead of a web-form toolbar.");
assertIncludes("mobile/src/App.tsx", "reader-stat-row", "Mobile reader must expose compact reading stats like native reading apps.");
assertIncludes("mobile/src/App.tsx", "保存进度", "Mobile reader must keep progress saving available from the reader controls.");
assertIncludes("mobile/src/services/mobile-stats.ts", "export type StatsPeriod", "Mobile stats logic must be extracted from App.tsx for maintainability.");
assertIncludes("mobile/src/services/mobile-stats.ts", "buildReadingTimeline", "Stats page must build a real reading timeline from sessions.");
assertIncludes("mobile/src/services/mobile-stats.ts", "buildBookRanking", "Stats page must expose book ranking instead of a placeholder trend card.");
assertIncludes("mobile/src/App.tsx", "reading-heat-strip", "Stats page must show a compact reading heat strip.");
assertIncludes("mobile/src/App.tsx", "reading-timeline", "Stats page must show reading timeline records.");
assertIncludes("mobile/src/App.tsx", "book-ranking-list", "Stats page must show book ranking.");
assertIncludes("mobile/src/App.tsx", "note-insight-list", "Stats page must show inspiration and note insights.");
assertIncludes("mobile/src/styles.css", ".stats-period-tabs", "Stats page must keep mobile period tabs polished.");
assertIncludes("mobile/src/styles.css", ".reading-timeline-item", "Stats timeline must use mobile list styling.");

console.log("[verify-mobile-ui] Mobile Reeden-style UI guards verified.");
