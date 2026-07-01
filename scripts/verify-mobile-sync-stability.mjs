import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-sync-stability] ${message}`);
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

assertIncludes("mobile/src/App.tsx", "syncLogs", "Mobile sync must persist visible sync logs for troubleshooting.");
assertIncludes("mobile/src/App.tsx", "pendingDownloadCount", "Metadata sync must report books whose content is not downloaded yet.");
assertIncludes("mobile/src/App.tsx", "downloadBookToMobile", "Book content download must be an explicit single-book action.");
assertIncludes("mobile/src/App.tsx", "下载正文", "Shelf must expose a manual book-content download action.");
assertIncludes("mobile/src/App.tsx", "取消下载", "Large book downloads must expose a cancel action.");
assertIncludes("mobile/src/App.tsx", "AbortController", "Large book downloads must be cancellable.");
assertIncludes("mobile/src/services/mobile-storage.ts", "saveSyncedMobileBookBlob", "Synced book files must be saved as format-aware blobs.");
assertIncludes("mobile/src/storage/mobile-files.ts", "Encoding.UTF8", "TXT and Markdown files must still be saved as UTF-8.");
assertIncludes("mobile/src/storage/mobile-files.ts", "writeFile", "Book file persistence must use Capacitor Filesystem.");
assertNotIncludes("mobile/src/App.tsx", "const withFiles = await downloadDesktopBookFiles(next)", "Immediate metadata sync must not download all desktop book files.");
assertNotIncludes("mobile/src/App.tsx", "await blob.text()", "Sync must not decode EPUB book files with blob.text().");

console.log("[verify-mobile-sync-stability] Mobile sync stability guards verified.");
