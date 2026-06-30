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

console.log("[verify-mobile-ui] Mobile Reeden-style UI guards verified.");
