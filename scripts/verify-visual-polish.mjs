import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-visual-polish] ${message}`);
  process.exit(1);
}

const styles = read("src/styles.css");
const readerPage = read("src/features/library/ReaderPage.tsx");
const libraryPage = read("src/features/library/LibraryPage.tsx");
const mainProcess = read("electron/main/index.ts");

if (!styles.includes(".reader-bg-warm") || !styles.includes(".reader-bg-night")) {
  fail("Missing multi-background reading surface classes.");
}

if (readerPage.includes("manuscript-paper")) {
  fail("Text reader still uses manuscript-paper; long-form reading should use the quieter reader-paper surface.");
}

if (!readerPage.includes("readerPaperClass(settings.readerBackground)")) {
  fail("Text reader does not use configurable reader background classes.");
}

if (!libraryPage.includes("library-path")) {
  fail("Library source paths should use the clearer library-path style.");
}

if (!styles.includes(".library-path")) {
  fail("Missing .library-path readability style.");
}

if (!mainProcess.includes("autoHideMenuBar: true")) {
  fail("BrowserWindow should hide the default Electron menu bar.");
}

if (!mainProcess.includes("mainWindow.setMenuBarVisibility(false)")) {
  fail("Main window should explicitly keep the menu bar hidden.");
}

console.log("[verify-visual-polish] Visual polish guards verified.");
