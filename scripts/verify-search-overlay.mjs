import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const searchPanel = readFileSync(path.join(root, "src/features/search/SearchPanel.tsx"), "utf8");

function fail(message) {
  console.error(`[verify-search-overlay] ${message}`);
  process.exit(1);
}

if (!searchPanel.includes("if (!open) return null;")) {
  fail("SearchPanel must not render the global search chrome while closed.");
}

if (searchPanel.includes('top-3 z-40 w-[min(760px,calc(100%-360px))]')) {
  fail("SearchPanel still uses the old always-visible top overlay positioning.");
}

if (!searchPanel.includes('aria-label="全局搜索"')) {
  fail("SearchPanel should expose the search dialog with an accessible label.");
}

console.log("[verify-search-overlay] Search overlay closed-state behavior verified.");
