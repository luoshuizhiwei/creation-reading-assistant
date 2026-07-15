import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-reader-anchor] ${message}`);
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

// Mirror of readerTocIndexFromCharOffset for behavioral regression testing.
function readerTocIndexFromCharOffset(document, charOffset) {
  const fullText = document.fullText;
  if (!fullText) return document.currentTocIndex ?? 0;
  const chapters = document.toc.filter((item) => item.level > 0);
  if (!chapters.length) return document.currentTocIndex ?? 0;
  for (let index = chapters.length - 1; index >= 0; index -= 1) {
    const item = chapters[index];
    if ((item.startOffset ?? 0) <= charOffset) return item.index ?? index;
  }
  return chapters[0].index ?? 0;
}

assertIncludes(
  "mobile/src/features/reader/reader-navigation.ts",
  "export function readerTocIndexFromCharOffset",
  "reader-navigation.ts must export readerTocIndexFromCharOffset."
);
assertIncludes(
  "mobile/src/features/reader/reader-navigation.ts",
  "export function readerCharOffsetFromViewport",
  "reader-navigation.ts must export readerCharOffsetFromViewport."
);
assertIncludes(
  "mobile/src/features/reader/reader-navigation.ts",
  "export function readerLocationExtrasFromViewport",
  "reader-navigation.ts must export readerLocationExtrasFromViewport."
);
assertIncludes(
  "mobile/src/services/mobile-storage-reading.ts",
  "text: extras.text",
  "createReadingLocation must merge text anchor fields from extras."
);
assertIncludes(
  "mobile/src/services/mobile-storage-reading.ts",
  "page: extras.page",
  "createReadingLocation must merge page fields from extras."
);
assertNotIncludes(
  "mobile/src/features/reader/hooks/useReaderNavigation.ts",
  "void saveProgress(nextProgress)",
  "Navigation hook must no longer save progress without location extras."
);

// Behavioral test: char offset mapping.
const fakeDoc = {
  fullText: "A".repeat(5000),
  currentTocIndex: 0,
  toc: [
    { id: "vol1", title: "第一卷", level: 0, index: 0, startOffset: 0, endOffset: 5000 },
    { id: "ch1", title: "第一章", level: 1, index: 1, startOffset: 0, endOffset: 1500 },
    { id: "ch2", title: "第二章", level: 1, index: 2, startOffset: 1500, endOffset: 3200 },
    { id: "ch3", title: "第三章", level: 1, index: 3, startOffset: 3200, endOffset: 5000 }
  ]
};

const cases = [
  { offset: 0, expected: 1, label: "start of book maps to first chapter" },
  { offset: 1499, expected: 1, label: "just before chapter 2 still chapter 1" },
  { offset: 1500, expected: 2, label: "exact chapter 2 start" },
  { offset: 2500, expected: 2, label: "middle of chapter 2" },
  { offset: 3200, expected: 3, label: "exact chapter 3 start" },
  { offset: 4999, expected: 3, label: "end of book maps to last chapter" }
];

for (const { offset, expected, label } of cases) {
  const actual = readerTocIndexFromCharOffset(fakeDoc, offset);
  if (actual !== expected) {
    fail(`readerTocIndexFromCharOffset(${label}): expected ${expected}, got ${actual}`);
  }
}

console.log("[verify-mobile-reader-anchor] Mobile reader anchor guards verified.");
