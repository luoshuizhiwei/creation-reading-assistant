import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function walk(directory, results = []) {
  if (!existsSync(directory)) return results;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(fullPath, results);
    else if (/\.(ts|tsx|css)$/.test(entry.name)) results.push(fullPath);
  }
  return results;
}

function fail(message) {
  console.error(`[verify-interaction-polish] ${message}`);
  process.exit(1);
}

const sourceFiles = walk(path.join(root, "src"));
const nativeDialogOffenders = sourceFiles
  .filter((file) => !file.endsWith(path.join("stores", "ui-store.ts")))
  .filter((file) => /\bwindow\.(confirm|alert|prompt)\b|\b(confirm|alert|prompt)\(/.test(readFileSync(file, "utf8")));

if (nativeDialogOffenders.length > 0) {
  fail(`Native browser dialogs are not allowed:\n${nativeDialogOffenders.map((file) => `  - ${path.relative(root, file)}`).join("\n")}`);
}

const requiredFiles = [
  "src/stores/ui-store.ts",
  "src/components/interaction.tsx"
];

for (const file of requiredFiles) {
  if (!existsSync(path.join(root, file))) fail(`Missing ${file}.`);
}

const interaction = read("src/components/interaction.tsx");
const app = read("src/app/App.tsx");
const styles = read("src/styles.css");
const search = read("src/features/search/SearchPanel.tsx");
const searchActions = read("src/hooks/useSearchActions.ts");
const epub = read("src/features/library/EpubReaderPage.tsx");
const reader = read("src/features/library/ReaderPage.tsx");
const homePage = read("src/features/creation/home/ProjectHomePage.tsx");
const libraryPage = read("src/features/library/LibraryPage.tsx");
const statsPage = read("src/features/library/ReadingStatsPage.tsx");
const settings = read("src/features/settings/SettingsPage.tsx");
const libraryActions = read("src/hooks/useLibraryActions.ts");

/** 防抖 timer 必须在空关键词处理之前被清理（支持直接 clearTimeout 或集中式 cancelSearch 两种实现）。 */
function debounceClearedBeforeEmpty(source) {
  const emptyIndex = source.indexOf("if (!keyword.trim())");
  if (emptyIndex < 0) return false;
  const directClear = source.lastIndexOf("window.clearTimeout(timerRef.current)", emptyIndex);
  if (directClear >= 0) return true;
  const cancelSearch = source.lastIndexOf("cancelSearch()", emptyIndex);
  return cancelSearch >= 0;
}

const checks = [
  [interaction.includes("ConfirmDialog"), "Interaction layer must provide ConfirmDialog."],
  [interaction.includes("ToastCenter"), "Interaction layer must provide ToastCenter."],
  [interaction.includes("PageTransition"), "Interaction layer must provide PageTransition."],
  [interaction.includes("AnimatedPanel"), "Interaction layer must provide AnimatedPanel."],
  [interaction.includes("InlineNotice"), "Interaction layer must provide InlineNotice."],
  [app.includes("<ToastCenter />"), "App must render ToastCenter globally."],
  [app.includes("<ConfirmDialog />"), "App must render ConfirmDialog globally."],
  [app.includes("<PageTransition"), "App must wrap screens in PageTransition."],
  [styles.includes("@keyframes paper-rise"), "Styles must define page/card rise animation."],
  [styles.includes("@keyframes toast-slide"), "Styles must define toast slide animation."],
  [styles.includes(".motion-card"), "Styles must expose reusable motion-card class."],
  [styles.includes(".motion-panel"), "Styles must expose reusable motion-panel class for non-clickable panels."],
  [styles.includes(".motion-dialog"), "Styles must expose reusable motion-dialog class."],
  [styles.includes('input[type="checkbox"]') && styles.includes("accent-color: var(--copper)"), "Checkboxes must use the copper theme accent instead of the browser default blue."],
  [interaction.includes("motion-panel"), "AnimatedPanel must use non-hover motion-panel styling."],
  [homePage.includes('className="project-home-hero motion-panel"'), "Homepage non-clickable hero panel must use motion-panel instead of hover card motion."],
  [libraryPage.includes("motion-panel overflow-hidden"), "Library table container must use motion-panel instead of hover card motion."],
  [statsPage.includes("motion-panel"), "Reading stats non-clickable panels must use motion-panel."],
  [search.includes("motion-dialog"), "Search dialog must use animated dialog styling."],
  [debounceClearedBeforeEmpty(searchActions), "Search debounce timer must be cleared before handling an empty keyword."],
  [searchActions.includes("setLoading(false)") && searchActions.indexOf("setLoading(false)") < searchActions.indexOf("setResults([])"), "Clearing a search must immediately clear loading state before showing empty results."],
  [epub.includes("motion-drawer"), "EPUB settings drawer must slide in with motion-drawer."],
  [reader.includes("showToast"), "Text/Markdown reader must use toast feedback for inspiration capture."],
  [epub.includes("showToast"), "EPUB reader must use toast feedback for inspiration capture."],
  [settings.includes("confirmAction"), "Settings page must use custom confirmation flow."],
  [libraryActions.includes("confirmAction"), "Library delete must use custom confirmation flow."]
];

const missing = checks.filter(([ok]) => !ok).map(([, message]) => message);
if (missing.length > 0) {
  fail(`Interaction polish guard failed:\n${missing.map((message) => `  - ${message}`).join("\n")}`);
}

console.log("[verify-interaction-polish] Interaction polish guards verified.");
