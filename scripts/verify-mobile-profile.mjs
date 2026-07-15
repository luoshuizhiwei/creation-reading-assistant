import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
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
const shelf = "mobile/src/features/shelf/ShelfPage.tsx";
const bookDetail = "mobile/src/features/shelf/BookDetailSheet.tsx";
const mobileTypes = "mobile/src/types/mobile.ts";
const css = "mobile/src/styles.css";
const html = "mobile/index.html";
const androidStyles = "mobile/android/app/src/main/res/values/styles.xml";

const profilePage = "mobile/src/features/profile/ProfilePage.tsx";
const profileHome = "mobile/src/features/profile/ProfileHome.tsx";
const syncPage = "mobile/src/features/profile/pages/SyncPage.tsx";
const webDavPage = "mobile/src/features/profile/pages/WebDavPage.tsx";
const libraryManagersPage = "mobile/src/features/profile/pages/LibraryManagersPage.tsx";
const readingAndNotesPage = "mobile/src/features/profile/pages/ReadingAndNotesPage.tsx";
const aiSettingsPage = "mobile/src/features/profile/pages/AISettingsPage.tsx";
const aboutAndPrivacyPage = "mobile/src/features/profile/pages/AboutAndPrivacyPage.tsx";
const useLibraryManagersHook = "mobile/src/features/profile/hooks/useLibraryManagers.ts";
const useMobileAISettingsHook = "mobile/src/features/profile/hooks/useMobileAISettings.ts";

const profilePageSource = read(profilePage);
if (!profilePageSource.includes("export type ProfileSubPage")) {
  fail("Profile must expose a ProfileSubPage type for second-level pages instead of dead menu items.");
}
for (const requiredSubPage of ["sync", "webdav", "tags", "categories", "shelves", "reading", "notes", "ai", "appearance", "storage", "privacy", "about"]) {
  if (!profilePageSource.includes(`"${requiredSubPage}"`)) {
    fail(`Profile sub-page type must include "${requiredSubPage}".`);
  }
}

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
  assertIncludes(profileHome, label, `Profile second-level entry/page must include ${label}.`);
}

assertIncludes(useLibraryManagersHook, "addTag", "Profile feature must wire addTag.");
assertIncludes(useLibraryManagersHook, "addCategory", "Profile feature must wire addCategory.");
assertIncludes(useLibraryManagersHook, "addShelf", "Profile feature must wire addShelf.");
assertIncludes(readingAndNotesPage, "removeNote", "Profile feature must wire removeNote.");
assertIncludes(aboutAndPrivacyPage, "exportSnapshot", "Profile feature must wire exportSnapshot.");
assertIncludes(aboutAndPrivacyPage, "importSnapshotFile", "Profile feature must wire importSnapshotFile.");
assertIncludes(useMobileAISettingsHook, "saveAiSettings", "Profile feature must wire saveAiSettings.");
assertIncludes(app, "onAppThemeChange", "Profile feature must wire onAppThemeChange.");

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

assertIncludes(webDavPage, "AI Key 不参与同步", "WebDAV/AI privacy boundary must be visible in Profile.");
assertIncludes(aiSettingsPage, "AI Key 不跨设备同步", "Mobile AI settings must explain that keys are not synced.");
assertIncludes(aboutAndPrivacyPage, "导出数据", "Storage page must provide data export.");
assertIncludes(aboutAndPrivacyPage, "导入数据", "Storage page must provide data import.");
assertIncludes(profileHome, "profileMoreOpen", "Profile more button must open a real shortcut panel.");
assertIncludes(profileHome, "openProfileShortcut(\"storage\")", "Profile more panel must link to storage page.");
assertIncludes(profileHome, "openProfileShortcut(\"privacy\")", "Profile more panel must link to privacy page.");
assertIncludes(profileHome, "openProfileShortcut(\"about\")", "Profile more panel must link to about page.");
assertIncludes(profilePage, "activePage === \"sync\"", "Sync must be a real second-level page, not a bottom sheet.");
assertIncludes(profilePage, "activePage === \"webdav\"", "WebDAV must be a real second-level page, not a bottom sheet.");
assertIncludes(syncPage, "settings-actions", "Profile subpage actions must use settings-page semantics instead of sheet actions.");
assertIncludes(syncPage, "sync-status-summary", "Sync subpage must use settings-page summary styling instead of a sheet summary.");
assertIncludes("mobile/src/hooks/useAppToast.ts", "window.setTimeout(() => setMessage(\"\")", "Global mobile messages must auto-dismiss instead of becoming a persistent top banner.");
assertIncludes(bookDetail, "toggleShelf", "Book detail must wire shelf membership from the shelf feature module.");
assertIncludes(bookDetail, "所在书单", "Book detail must connect created shelves back to books.");
assertIncludes(bookDetail, "bookIds: inShelf", "Shelf membership must be toggled from book detail.");
assertIncludes(shelf, "selectedShelfId", "Shelf page must allow filtering by created shelves.");
assertIncludes(shelf, "selectedCategoryId", "Shelf page must allow filtering by categories.");
assertIncludes(shelf, "selectedTagName", "Shelf page must allow filtering by book tags.");
assertIncludes(bookDetail, "所属分类", "Book detail must connect categories back to books.");
assertIncludes(bookDetail, "categoryIds", "Book detail must read category membership.");
assertIncludes(mobileTypes, "categoryIds", "Books must store category membership.");
assertIncludes(bookDetail, "书籍标签", "Book detail must connect created book tags back to books.");
assertIncludes(bookDetail, "toggleBookTag", "Book detail must toggle book tag membership.");
assertIncludes(bookDetail, "tagNames", "Book detail must read tag membership.");
assertIncludes(mobileTypes, "tagNames", "Books must store tag membership.");
assertIncludes(useLibraryManagersHook, "newTagType", "Tag management must distinguish book/inspiration/note tags.");
assertIncludes(libraryManagersPage, "tag-inline-form", "Tag creation must include a compact typed form.");
assertIncludes(useLibraryManagersHook, "targetTag?.type === \"book\"", "Deleting a book tag must clear it from books.");
assertIncludes(useLibraryManagersHook, "targetTag?.type === \"inspiration\"", "Deleting an inspiration tag must clear it from inspirations.");

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

if (read(app).includes("sync-sheet-summary") || read(css).includes("sync-sheet-summary")) {
  fail("Profile sync/WebDAV must not keep bottom-sheet naming or styling: sync-sheet-summary");
}
if (/(^|[\s,{])\.sheet-actions(?=[\s,{.:#>+~])/m.test(read(css))) {
  fail("Profile sync/WebDAV must not keep the legacy standalone .sheet-actions selector.");
}

for (const staleCopy of ["本地优先：没有网络也能阅读", "回到同一网络后再同步"]) {
  if (read(app).includes(staleCopy)) fail(`Profile top-level pages must not show the old black banner copy: ${staleCopy}`);
}

assertIncludes(html, "theme-color", "Mobile HTML must set theme-color so Android top chrome follows the app surface.");
assertIncludes(androidStyles, "android:statusBarColor", "Android theme must set status bar color to avoid black/gray top chrome.");
assertIncludes(androidStyles, "android:navigationBarColor", "Android theme must set navigation bar color to match the app surface.");
assertIncludes(androidStyles, "android:windowLightStatusBar", "Android theme must request dark status icons on the light paper surface.");

console.log("[verify-mobile-profile] Mobile profile feature guards verified.");
