import fs from "node:fs";

function read(path) {
  return fs.readFileSync(path, "utf8");
}

function fail(message) {
  console.error(`[verify-mobile-import-chain] ${message}`);
  process.exit(1);
}

function includes(file, needle, message) {
  if (!read(file).includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const importer = "mobile/src/hooks/useMobileImport.ts";
const books = "mobile/src/services/mobile-storage-books.ts";
const files = "mobile/src/storage/mobile-files.ts";
const epub = "mobile/src/reader/mobile-reader-epubjs.ts";
const core = "mobile/src/services/mobile-storage-core.ts";
const shelf = "mobile/src/features/shelf/ShelfPage.tsx";

includes(importer, "ImportTaskPhase", "Import flow must expose explicit phases instead of contradictory booleans.");
includes(importer, "runningTaskIdsRef", "The same import task must not run concurrently.");
includes(importer, "if (importingRef.current)", "Rapid import or repair actions must be rejected while a foreground task is active.");
includes(importer, "existingBook = snapshotRef.current.books.find((book) => book.contentHash === imported.contentHash)", "Exact content duplicates must be detected before committing a new record.");
includes(importer, "return \"duplicate\"", "Exact duplicates must reuse the existing record instead of silently creating another copy.");
includes(importer, "EMPTY_FILE", "Empty files must fail explicitly and remain out of the shelf.");
includes(importer, "repairMobileBookFromImport", "Missing local files must have a safe re-import path.");

const booksSource = read(books);
const inspectIndex = booksSource.indexOf("inspectEpubForImport(content");
const saveIndex = booksSource.indexOf("export async function saveMobileBook");
if (inspectIndex < 0 || saveIndex < 0 || inspectIndex > saveIndex) {
  fail("EPUB structure and a readable spine chapter must be validated before saveMobileBook can create a formal record.");
}
includes(books, "signature.startsWith(\"PK\")", "Renamed non-ZIP files must not be accepted as EPUB.");
includes(books, "committed.books.some", "Import commit must be read back and verified before success is reported.");
includes(books, "deleteMobileBookFile(localContentPath).catch", "Failed commits must roll back the private copied file.");
includes(books, "repairMobileBookFromImport", "Storage must preserve bookId and related reading data during safe repair.");
includes(books, "current.contentHash === imported.contentHash", "Repair must require stable content matching when an old hash exists.");

const deleteStart = booksSource.indexOf("export async function deleteMobileBook");
const deleteSource = booksSource.slice(deleteStart);
if (deleteSource.indexOf("await saveMobileSnapshot(next)") > deleteSource.indexOf("await deleteMobileBookFile(localContentPath)")) {
  fail("Single-book deletion must persist record removal before deleting the only private正文 file.");
}
includes(deleteSource ? books : books, "已恢复书架记录", "A private-file deletion failure must restore the shelf record.");

includes(files, "getMobileBookStoragePath", "Private file paths must be deterministic so partial writes can be rolled back.");
includes(files, "Filesystem.deleteFile({ path, directory: Directory.Data }).catch", "Failed private writes must clean their known target path.");
includes(files, "beginPendingMobileBookWrite", "A private file write must be journaled before the app can be killed mid-import.");
includes(files, "recoverPendingMobileBookWrites", "Interrupted imports must clean only journaled files that lack formal records.");
includes(books, "recoverInterruptedMobileImports", "Storage must expose interrupted-import recovery during app startup.");
includes("mobile/src/App.tsx", "recoverInterruptedMobileImports(loaded)", "App startup must reconcile interrupted import files after loading formal records.");
includes(epub, "inspectEpubForImport", "EPUB import must have a dedicated pre-commit inspector.");
includes(epub, "section.render()", "EPUB import must actually read a spine chapter, not stop at metadata.");
includes(epub, "book.archive.getText(path)", "EPUB import and reading must fall back to the underlying XHTML when epub.js returns an empty render result.");
includes(epub, "Pick<SpineItem, \"index\" | \"href\" | \"linear\">", "EPUB structure caches must not retain Section objects owned by a destroyed Book instance.");
includes(epub, "URL.revokeObjectURL", "Temporary EPUB cover blob URLs must be released.");
includes(epub, "EPUB_IMPORT_COVER_MAX_BYTES", "Imported covers must be bounded before persistence.");
includes(core, "coverDataUrl: _coverDataUrl", "Device-local cover data URLs must not enter sync metadata payloads.");
includes(core, "localUri: _localUri", "Android private URIs must not enter sync payloads.");
includes(shelf, "重新选择文件修复正文", "Missing files must expose a user-facing repair action.");
includes(shelf, "is-indeterminate", "Import UI must not show fabricated percentage progress.");

console.log("[verify-mobile-import-chain] Mobile import transaction, EPUB validation, repair and sync-boundary guards verified.");
