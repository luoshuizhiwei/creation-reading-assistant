import { readFileSync } from "node:fs";

const read = (path) => readFileSync(path, "utf8");
const shelf = read("mobile/src/features/shelf/ShelfPage.tsx");
const tile = read("mobile/src/features/shelf/BookTile.tsx");
const selectors = read("mobile/src/features/shelf/shelf-selectors.ts");
const status = read("mobile/src/features/shelf/book-status.ts");
const importer = read("mobile/src/hooks/useMobileImport.ts");
const styles = read("mobile/src/styles/shelf-library.css");
const styleEntry = read("mobile/src/styles.css");
const interactionStyles = read("mobile/src/styles/interaction-polish.css");
const releaseStyles = read("mobile/src/styles/release-overrides.css");

function requireText(source, value, message) {
  if (!source.includes(value)) throw new Error(`[verify-mobile-shelf] ${message}: missing ${JSON.stringify(value)}`);
}

for (const label of ["全部", "在读", "已完成", "未开始", "本机可读"]) {
  requireText(shelf, `label: "${label}"`, `status filter must expose ${label}`);
}
requireText(selectors, "matchesStatusFilter", "status filters must use shared metadata/progress logic");
requireText(selectors, "originalIndex", "shelf sorting must stay stable");
requireText(selectors, "originalFileName", "search must include the original filename without exposing paths");
requireText(selectors, "compareOptionalDates", "missing sort dates must be handled explicitly");
requireText(status, "book.origin === \"sync_placeholder\"", "sync placeholders must never be considered downloaded");
requireText(status, "book.contentStatus === \"downloading\"", "downloading books must not be opened as ready");
requireText(shelf, "statMobileBookFile", "shelf must verify the local file before entering the reader");
requireText(shelf, "shelf-book-action-sheet", "book actions must use a mobile action sheet");
requireText(shelf, "createPortal", "book action sheet must escape the scrolling shelf container");
requireText(shelf, "requestDeleteBook", "book deletion must keep the existing confirmation path");
requireText(importer, "importingRef.current", "rapid repeated imports must be guarded");
requireText(importer, "finally", "the import guard must be released after success or failure");
requireText(tile, "onTouchCancel", "long-press timers must be cleared when Android cancels a touch");
requireText(tile, "grid-book-author", "grid cards must expose a compact author line");
requireText(styles, "grid-template-columns: repeat(3, minmax(0, 112px))", "normal phone widths must use a compact three-column grid");
requireText(styles, "@media (max-width: 339px)", "two-column fallback must be reserved for genuinely narrow screens");
requireText(styles, ".grid-book-author", "the canonical stylesheet must keep the author visible");
requireText(styles, "display: block", "the canonical stylesheet must restore metadata hidden by historical CSS");
requireText(styles, ".shelf-action-mask", "action sheet must have an overlay above the bottom navigation");
requireText(styleEntry, "@import './styles/shelf-library.css';", "shelf styles must use the dedicated stylesheet");

if (shelf.includes("snapshot.progress.length} 在读")) {
  throw new Error("[verify-mobile-shelf] completed progress records must not be counted as currently reading");
}
if (tile.includes("book-tile-menu")) {
  throw new Error("[verify-mobile-shelf] inline web-style book menus must not return");
}
if (/!important/.test(styles)) {
  throw new Error("[verify-mobile-shelf] canonical shelf styles must not depend on !important");
}
if (interactionStyles.includes(".book-grid:not(.book-list) .book-tile p")) {
  throw new Error("[verify-mobile-shelf] historical global styles must not hide shelf metadata");
}
if (releaseStyles.includes(".book-grid:not(.book-list) .reading-book-tile .tile-more")) {
  throw new Error("[verify-mobile-shelf] a historical high-specificity rule must not shrink the shelf action target");
}
if (!/\.book-grid:not\(\.book-list\) \.tile-more\s*\{[\s\S]*?width:\s*36px;[\s\S]*?height:\s*36px;/.test(styles)) {
  throw new Error("[verify-mobile-shelf] the canonical shelf action target must override historical release sizing");
}

console.log("[verify-mobile-shelf] Shelf data, interaction, import and layout guards verified.");
