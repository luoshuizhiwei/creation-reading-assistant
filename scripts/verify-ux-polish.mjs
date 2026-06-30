import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-ux-polish] ${message}`);
  process.exit(1);
}

const app = read("src/app/App.tsx");
const start = read("src/pages/StartPage.tsx");
const reader = read("src/features/library/ReaderPage.tsx");
const search = read("src/features/search/SearchPanel.tsx");
const betaCheck = read("scripts/beta-check.mjs");

const requiredSnippets = [
  [app, "function RecoveryPrompt", "App should use an in-app recovery prompt."],
  [app, "info.recoveredSessionsCount > 0", "Recovery prompt should not show for zero recovered sessions."],
  [app, "setRecoveryInfo(info)", "Recovery info should be stored in component state."],
  [app, "window.confirm", "App should not use native window.confirm for startup recovery.", true],
  [reader, "MarkdownIt", "Markdown reader should use markdown-it for full Markdown rendering."],
  [reader, "function markdownToc", "Markdown reader should build a heading table of contents."],
  [reader, "activeBook.format === \"md\"", "Reader should branch Markdown rendering by book format."],
  [reader, "markdown-reader", "Rendered Markdown should use a readable class hook."],
  [reader, "reader-heading-scale", "Markdown heading sizes should scale with reader font size."],
  [start, "hover:-translate-y-1", "Start page module cards should have a smooth lifted hover state."],
  [start, "focus:ring-2", "Start page cards should keep visible keyboard focus states."],
  [start, "rounded-[2rem]", "Start page should use a softer dashboard shell."],
  [search, "function formatSourceLabel", "Search results should format technical source paths."],
  [search, "书库文件 ·", "Search source labels should hide AppLibrary internals."],
  [search, "text-paper-ink/70", "Search snippets/source labels should have stronger contrast."],
  [betaCheck, "npm run verify:ux-polish", "verify:beta must include npm run verify:ux-polish."]
];

const missing = [];
for (const [source, snippet, message, mustBeAbsent] of requiredSnippets) {
  const includes = source.includes(snippet);
  if (mustBeAbsent ? includes : !includes) missing.push(message);
}

if (missing.length > 0) {
  fail(`UX polish guard failed:\n${missing.map((message) => `  - ${message}`).join("\n")}`);
}

console.log("[verify-ux-polish] UX polish guards verified.");
