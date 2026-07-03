import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-profile] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const app = "mobile/src/App.tsx";
const css = "mobile/src/styles.css";
const html = "mobile/index.html";
const androidStyles = "mobile/android/app/src/main/res/values/styles.xml";

assertIncludes(app, "type ProfileSubPage = \"sync\" | \"webdav\" | \"tags\" | \"categories\" | \"shelves\" | \"reading\" | \"notes\" | \"ai\" | \"appearance\" | \"storage\" | \"privacy\" | \"about\"", "Profile must expose real second-level pages instead of dead menu items.");

for (const label of [
  "标签管理",
  "分类管理",
  "书单管理",
  "我的阅读",
  "我的书评 / 笔记",
  "AI 助手",
  "应用外观",
  "存储管理",
  "隐私安全",
  "关于"
]) {
  assertIncludes(app, label, `Profile second-level entry/page must include ${label}.`);
}

for (const marker of [
  "addTag",
  "addCategory",
  "addShelf",
  "toggleShelf",
  "removeNote",
  "exportSnapshot",
  "importSnapshotFile",
  "saveAiSettings",
  "onAppThemeChange"
]) {
  assertIncludes(app, marker, `Profile feature must wire ${marker}.`);
}

if (read(app).includes("会作为二级页面逐步补齐")) {
  fail("Profile menu must not use a generic coming-soon toast for primary entries.");
}

for (const staleText of [
  "后续会和书架筛选联动",
  "后续可以把书架里的书归入书单",
  "后续接入 EPUB 专用渲染器"
]) {
  if (read(app).includes(staleText) || read("mobile/src/reader/mobile-reader.ts").includes(staleText)) {
    fail(`Mobile UI must not keep stale unfinished wording: ${staleText}`);
  }
}

assertIncludes(app, "AI Key 不参与同步", "WebDAV/AI privacy boundary must be visible in Profile.");
assertIncludes(app, "AI Key 不跨设备同步", "Mobile AI settings must explain that keys are not synced.");
assertIncludes(app, "导出数据", "Storage page must provide data export.");
assertIncludes(app, "导入数据", "Storage page must provide data import.");
assertIncludes(app, "profileMoreOpen", "Profile more button must open a real shortcut panel.");
assertIncludes(app, "openProfileShortcut(\"storage\")", "Profile more panel must link to storage page.");
assertIncludes(app, "openProfileShortcut(\"privacy\")", "Profile more panel must link to privacy page.");
assertIncludes(app, "openProfileShortcut(\"about\")", "Profile more panel must link to about page.");
assertIncludes(app, "activePage === \"sync\"", "Sync must be a real second-level page, not a bottom sheet.");
assertIncludes(app, "activePage === \"webdav\"", "WebDAV must be a real second-level page, not a bottom sheet.");
assertIncludes(app, "settings-actions", "Profile subpage actions must use settings-page semantics instead of sheet actions.");
assertIncludes(app, "sync-status-summary", "Sync subpage must use settings-page summary styling instead of a sheet summary.");
assertIncludes(app, "window.setTimeout(() => setMessage(\"\")", "Global mobile messages must auto-dismiss instead of becoming a persistent top banner.");
assertIncludes(app, "所在书单", "Book detail must connect created shelves back to books.");
assertIncludes(app, "bookIds: inShelf", "Shelf membership must be toggled from book detail.");
assertIncludes(app, "selectedShelfId", "Shelf page must allow filtering by created shelves.");
assertIncludes(app, "selectedCategoryId", "Shelf page must allow filtering by categories.");
assertIncludes(app, "selectedTagName", "Shelf page must allow filtering by book tags.");
assertIncludes(app, "所属分类", "Book detail must connect categories back to books.");
assertIncludes(app, "categoryIds", "Books must store category membership.");
assertIncludes(app, "书籍标签", "Book detail must connect created book tags back to books.");
assertIncludes(app, "toggleBookTag", "Book detail must toggle book tag membership.");
assertIncludes(app, "tagNames", "Books must store tag membership.");
assertIncludes(app, "newTagType", "Tag management must distinguish book/inspiration/note tags.");
assertIncludes(app, "tag-inline-form", "Tag creation must include a compact typed form.");
assertIncludes(app, "targetTag?.type === \"book\"", "Deleting a book tag must clear it from books.");
assertIncludes(app, "targetTag?.type === \"inspiration\"", "Deleting an inspiration tag must clear it from inspirations.");

for (const selector of [
  ".management-row",
  ".option-row",
  ".profile-metric-strip",
  ".privacy-list",
  ".about-card",
  ".profile-more-panel",
  ".shelf-chip",
  ".filter-chip-rail",
  ".tag-inline-form",
  ".active-filter-note",
  ":root[data-mobile-theme=\"dark\"]"
]) {
  assertIncludes(css, selector, `Profile page styling must include ${selector}.`);
}

for (const staleSelector of ["sync-sheet-summary", "sheet-actions"]) {
  if (read(app).includes(staleSelector) || read(css).includes(staleSelector)) {
    fail(`Profile sync/WebDAV must not keep bottom-sheet naming or styling: ${staleSelector}`);
  }
}

for (const staleCopy of ["本地优先：没有网络也能阅读", "回到同一网络后再同步"]) {
  if (read(app).includes(staleCopy)) fail(`Profile top-level pages must not show the old black banner copy: ${staleCopy}`);
}

assertIncludes(html, "theme-color", "Mobile HTML must set theme-color so Android top chrome follows the app surface.");
assertIncludes(androidStyles, "android:statusBarColor", "Android theme must set status bar color to avoid black/gray top chrome.");
assertIncludes(androidStyles, "android:navigationBarColor", "Android theme must set navigation bar color to match the app surface.");
assertIncludes(androidStyles, "android:windowLightStatusBar", "Android theme must request dark status icons on the light paper surface.");

console.log("[verify-mobile-profile] Mobile profile feature guards verified.");
