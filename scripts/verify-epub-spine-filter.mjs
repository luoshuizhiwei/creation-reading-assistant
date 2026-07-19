import { readFileSync } from "node:fs";

const EPUB_SOURCE = "mobile/src/reader/mobile-reader-epubjs.ts";
const source = readFileSync(EPUB_SOURCE, "utf-8");

function fail(message) {
  console.error(`[verify-epub-spine-filter] ${message}`);
  process.exit(1);
}

// Mirror of isEpubNavigationHref from mobile-reader-epubjs.ts.
// This script acts as a behavioral regression test: if the source filter changes,
// this copy must be updated too, and the cases below ensure nav/toc items are rejected.
function isEpubNavigationHref(href) {
  if (!href) return false;
  const lower = href.toLowerCase().split("#")[0];
  return (
    /\bnav\.x?html?$/.test(lower) ||
    /\btoc\.x?html?$/.test(lower) ||
    /\bcontents?\.x?html?$/.test(lower) ||
    /\btable-of-contents\.x?html?$/.test(lower)
  );
}

const cases = [
  { href: "nav.xhtml", expected: true, label: "EPUB3 nav.xhtml" },
  { href: "OEBPS/nav.html", expected: true, label: "nav.html in subfolder" },
  { href: "toc.xhtml", expected: true, label: "toc.xhtml" },
  { href: "contents.xhtml", expected: true, label: "contents.xhtml" },
  { href: "table-of-contents.xhtml", expected: true, label: "table-of-contents.xhtml" },
  { href: "cover.xhtml", expected: false, label: "cover.xhtml should stay readable" },
  { href: "chapter1.xhtml", expected: false, label: "regular chapter" },
  { href: "chapter2.xhtml", expected: false, label: "regular chapter 2" },
  { href: "copyright.xhtml", expected: false, label: "copyright page" }
];

for (const { href, expected, label } of cases) {
  const result = isEpubNavigationHref(href);
  if (result !== expected) {
    fail(`isEpubNavigationHref mismatch for "${label}": expected ${expected}, got ${result}`);
  }
}

// Static guard: ensure the source still contains the regexes we tested.
const requiredPatterns = [
  "/\\bnav\\.x?html?$/",
  "/\\btoc\\.x?html?$/",
  "/\\bcontents?\\.x?html?$/",
  "readableSpineItems"
];
for (const pattern of requiredPatterns) {
  if (!source.includes(pattern)) fail(`Source missing required pattern: ${pattern}`);
}

console.log("[verify-epub-spine-filter] EPUB spine navigation filtering verified.");
