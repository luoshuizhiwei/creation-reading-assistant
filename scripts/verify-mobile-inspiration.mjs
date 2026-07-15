import { readWithCssImports } from "./lib/read-with-css-imports.mjs";
import { readFileSync } from "node:fs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-inspiration] ${message}`);
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

const page = "mobile/src/features/inspiration/InspirationPage.tsx";
const detail = "mobile/src/features/inspiration/InspirationDetailPanel.tsx";
const storage = "mobile/src/services/mobile-storage-inspirations.ts";
const styles = "mobile/src/styles/inspiration-center.css";

assertIncludes("mobile/src/App.tsx", "<InspirationPage", "Mobile app must expose inspiration as a first-class page.");
assertIncludes(page, "inspiration-topbar", "Inspiration list needs a compact mobile top bar.");
assertIncludes(page, "inspiration-search-mode", "Search must use a dedicated mobile input state.");
assertIncludes(page, "item.source?.excerpt", "Search must cover stored source excerpts.");
assertIncludes(page, "typeFilter", "Inspiration list must filter by the real inspiration type field.");
assertIncludes(page, "mobile-inspiration-sort", "Sort preference must remain local UI state.");
assertIncludes(page, ".sort(", "Inspiration list must sort a derived array.");
assertIncludes(page, "left.index - right.index", "Inspiration sorting must be stable.");
assertIncludes(page, "event.stopPropagation()", "Card action button must not accidentally open detail.");
assertIncludes(page, "mode === \"editor\"", "Create and edit must use a full-page editor state.");
assertIncludes(page, "放弃未保存修改", "Editor must protect unsaved changes.");
assertIncludes(page, "请至少填写标题、正文或来源摘录中的一项", "Empty records must not be created.");
assertIncludes(page, "sourceBookId", "Editor must preserve the source-book relationship.");
assertIncludes(page, "这本书在当前设备没有可读正文", "Unavailable source books must not open a blank reader.");
assertIncludes(page, "mobile-tab-back", "Android back must close inspiration layers before leaving the tab.");
assertIncludes(detail, "variantsExpanded", "AI variants must be collapsed by default and controlled by the detail view.");
assertIncludes(detail, "AI 只生成辅助候选", "AI must remain an explicit auxiliary action.");
assertNotIncludes(page, "quick-note-card", "The list page must not embed a large desktop-style quick editor.");
assertNotIncludes(page, "item.variants.length > 0", "The compact list must not promote AI variants into card content.");
assertIncludes(storage, "type: input.type ?? \"note\"", "Creation must persist the selected real inspiration type.");
assertIncludes(storage, "source: input.source === null ? undefined", "Editing must be able to clear an old source safely.");
assertIncludes(storage, "current.status === \"inbox\" || current.status === \"usable\"", "Adding an AI variant must not regress adopted or archived statuses.");
if (!readFileSync("mobile/src/styles.css", "utf8").includes("inspiration-center.css")) {
  fail("Inspiration styles must be centralized in the dedicated stylesheet.");
}
assertIncludes(styles, "width: 44px", "Card secondary actions must have a 44px touch target.");
assertNotIncludes(styles, "!important", "Dedicated inspiration styles must not add high-specificity emergency overrides.");

console.log("[verify-mobile-inspiration] Mobile inspiration list, detail, editor, source and back-navigation guards verified.");
