import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-data-integrity] ${message}`);
  process.exit(1);
}

const mainProcess = read("electron/main/index.ts");
const betaCheck = read("scripts/beta-check.mjs");

const requiredMainSnippets = [
  "async function renameReplacingExistingFile",
  "await renameReplacingExistingFile(tempPath, filePath)",
  "code === \"EEXIST\" || code === \"EPERM\"",
  "function assertSafeRestoreSource",
  "Backup directory cannot be inside the current app data directory.",
  "Backup app-data source cannot overlap the current app data directory.",
  "async function unlinkManagedFileIfPresent",
  "await unlinkManagedFileIfPresent(appLibraryFilesRoot(), removedBook.filePath",
  "await unlinkManagedFileIfPresent(appLibraryCoversRoot(), removedBook.coverPath",
  "await writeSessionList(sessions)",
  "session.bookId !== bookId"
];

const missingMain = requiredMainSnippets.filter((snippet) => !mainProcess.includes(snippet));
if (missingMain.length > 0) {
  fail(`Missing main-process data integrity safeguards:\n${missingMain.map((snippet) => `  - ${snippet}`).join("\n")}`);
}

if (!betaCheck.includes("npm run verify:data-integrity")) {
  fail("verify:beta must include npm run verify:data-integrity.");
}

console.log("[verify-data-integrity] Data integrity guards verified.");
