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
const backupModule = read("electron/main/backup/index.ts");
const betaCheck = read("scripts/beta-check.mjs");

const requiredMainSnippets = [
  "async function renameReplacingExistingFile",
  "await renameReplacingExistingFile(tempPath, filePath)",
  "code === \"EEXIST\" || code === \"EPERM\"",
  "async function unlinkManagedFileIfPresent",
  "await unlinkManagedFileIfPresent(appLibraryFilesRoot(), removedBook.filePath",
  "await unlinkManagedFileIfPresent(appLibraryCoversRoot(), removedBook.coverPath",
  "await writeSessionList(sessions)",
  "session.bookId !== bookId"
];

const requiredBackupSnippets = [
  "function assertSafeRestoreSource",
  'throw new BackupError("unsafe-source"',
  "备份数据目录与当前数据目录存在重叠",
  "function assertSafeLibraryTarget",
  "资料库恢复目标不能是磁盘根目录"
];

const missingMain = requiredMainSnippets.filter((snippet) => !mainProcess.includes(snippet));
if (missingMain.length > 0) {
  fail(`Missing main-process data integrity safeguards:\n${missingMain.map((snippet) => `  - ${snippet}`).join("\n")}`);
}
const missingBackup = requiredBackupSnippets.filter((snippet) => !backupModule.includes(snippet));
if (missingBackup.length > 0) {
  fail(`Missing backup integrity safeguards:\n${missingBackup.map((snippet) => `  - ${snippet}`).join("\n")}`);
}

if (!betaCheck.includes("npm run verify:data-integrity")) {
  fail("verify:beta must include npm run verify:data-integrity.");
}

console.log("[verify-data-integrity] Data integrity guards verified.");
