import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-epub-restore] ${message}`);
  process.exit(1);
}

const reader = read("src/features/library/EpubReaderPage.tsx");
const betaCheck = read("scripts/beta-check.mjs");

const requiredSnippets = [
  [reader, "EPUB_INITIAL_DISPLAY_TIMEOUT_MS", "EPUB initial display should have a bounded timeout."],
  [reader, "displayWithTimeout", "EPUB reader should not let initial rendition.display hang forever."],
  [reader, "void book.locations.generate(1600)", "EPUB location generation should not block initial rendering."],
  [reader, "restoreHref", "EPUB restore should fall back to the saved href when CFI restore is unreliable."],
  [reader, "initialTargetHref || restoreHref || restoreCfi", "EPUB restore should prefer saved href before CFI for real-world EPUB compatibility."],
  [reader, "fallback.progressPercent > 0", "EPUB restore should not overwrite a known chapter progress with a zero percentage while locations are still generating."],
  [reader, "cfiProgressPercent === 0", "EPUB restore should not overwrite a known chapter progress when percentageFromCfi returns zero before location generation finishes."],
  [reader, "canSaveProgressRef.current = false", "EPUB reader should block progress writes during initial restore."],
  [reader, "pendingInitialLocationRef", "EPUB reader should quarantine initial relocated events before saving."],
  [reader, "hrefMatchesLocation", "EPUB reader should verify restored locations against the expected href."],
  [reader, "restoreTarget !== restoreHref", "EPUB reader should only retry href fallback when CFI was the first target."],
  [betaCheck, "npm run verify:epub-restore", "verify:beta must include npm run verify:epub-restore."]
];

const missing = requiredSnippets
  .filter(([source, snippet]) => !source.includes(snippet))
  .map(([, , message]) => message);

if (reader.includes("await book.locations.generate(1600)")) {
  missing.push("EPUB reader should not await book.locations.generate before rendering.");
}

if (reader.includes("displayWithTimeout(rendition, undefined)")) {
  missing.push("EPUB reader should not display an empty initial target before jumping to a restored location.");
}

if (missing.length > 0) {
  fail(`EPUB restore guard failed:\n${missing.map((message) => `  - ${message}`).join("\n")}`);
}

console.log("[verify-epub-restore] EPUB restore guards verified.");
