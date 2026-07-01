import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-reading-experience] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/App.tsx", "ShelfViewMode", "Shelf must support reading-app style grid/list view switching.");
assertIncludes("mobile/src/App.tsx", "ShelfFilterMode", "Shelf must support quick filters for all/reading/downloaded/pending books.");
assertIncludes("mobile/src/App.tsx", "ShelfSortMode", "Shelf must support reader-oriented sorting.");
assertIncludes("mobile/src/App.tsx", "book-progress-line", "Shelf cards must expose compact reading progress instead of plain web text.");
assertIncludes("mobile/src/App.tsx", "handleReaderTap", "Reader must use tap zones instead of only toolbar buttons.");
assertIncludes("mobile/src/App.tsx", "moveChapter", "Reader tap zones must support previous/next chapter movement.");
assertIncludes("mobile/src/App.tsx", "reader-progress-chip", "Reader must expose a lightweight chapter/progress chip.");
assertIncludes("mobile/src/App.tsx", "reader-zone-guide", "Reader must show discoverable tap-zone hints when controls are visible.");
assertIncludes("mobile/src/App.tsx", "readerMode", "Reader settings must reserve mode switching for scroll/page style reading.");
assertIncludes("mobile/src/App.tsx", "fontWeight", "Reader settings must include text weight customization.");

assertIncludes("mobile/src/styles.css", ".segmented-control", "Shelf filters must be styled as native-like segmented controls.");
assertIncludes("mobile/src/styles.css", ".book-list", "Shelf must provide a compact list layout.");
assertIncludes("mobile/src/styles.css", ".reader-progress-chip", "Reader chapter/progress chip must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-zone-guide", "Reader tap-zone hints must be styled.");
assertIncludes("mobile/src/styles.css", ".reader-mode-grid", "Reader mode controls must be styled in the settings drawer.");
assertIncludes("mobile/src/styles.css", "touch-action: pan-y", "Reader body must prioritize vertical reading gestures.");

console.log("[verify-mobile-reading-experience] Mobile reading-app experience guards verified.");
