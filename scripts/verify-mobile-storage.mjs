import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
}

function fail(message) {
  console.error(`[verify-mobile-storage] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/package.json", "@capacitor-community/sqlite", "Mobile app must include Capacitor SQLite.");
assertIncludes("mobile/package.json", "@capacitor/filesystem", "Mobile app must include Capacitor Filesystem.");
assertIncludes("mobile/src/storage/mobile-schema.ts", "CREATE TABLE IF NOT EXISTS books", "SQLite schema must define books.");
assertIncludes("mobile/src/storage/mobile-schema.ts", "CREATE TABLE IF NOT EXISTS inspirations", "SQLite schema must define inspirations.");
assertIncludes("mobile/src/storage/mobile-schema.ts", "CREATE TABLE IF NOT EXISTS sync_accounts", "SQLite schema must define sync accounts.");
assertIncludes("mobile/src/storage/mobile-database.ts", "SQLiteConnection", "Mobile database must initialize SQLite.");
assertIncludes("mobile/src/storage/mobile-files.ts", "Filesystem", "Book files must use Capacitor Filesystem.");
assertIncludes("mobile/src/storage/mobile-files.ts", "localFilePath", "Mobile file storage must return a stable local file path for reopening synced/imported books.");
assertIncludes("mobile/src/storage/mobile-files.ts", "readMobileBookFile", "Mobile app must read saved book files back from Capacitor Filesystem.");
assertIncludes("mobile/src/storage/mobile-files.ts", "verifySavedBookFile", "Mobile imports must verify that saved book files exist and are non-empty before writing metadata.");
assertIncludes("mobile/src/storage/mobile-files.ts", "statMobileBookFile", "Mobile reader must re-check saved files before opening metadata-backed books.");
assertIncludes("mobile/src/storage/mobile-files.ts", "deleteMobileBookFile", "Mobile imports must be able to roll back saved files when metadata persistence fails.");
const STORAGE_CORE = "mobile/src/services/mobile-storage-core.ts";
const STORAGE_BOOKS = "mobile/src/services/mobile-storage-books.ts";
assertIncludes(STORAGE_CORE, "migrateLegacySnapshot", "Storage must migrate the old localStorage snapshot.");
assertIncludes(STORAGE_BOOKS, "saveSyncedMobileBookFile", "Mobile storage must persist book files downloaded from desktop sync.");
assertIncludes(STORAGE_BOOKS, "deleteMobileBook", "Mobile storage must expose one unified book deletion path.");
assertIncludes(STORAGE_BOOKS, "shelves: snapshot.shelves.map", "Deleting a book must also remove it from shelf membership.");
assertIncludes(STORAGE_BOOKS, "localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`)", "Deleting a book must clean old hot-cache content.");
assertIncludes(STORAGE_CORE, "markMissingRowsDeleted", "SQLite mirror must mark rows missing from the snapshot as deleted so removed books do not reappear.");
assertIncludes(STORAGE_CORE, "UPDATE ${table} SET deleted_at = ?", "SQLite deletion reconciliation must use deleted_at tombstones.");
assertIncludes(STORAGE_CORE, "markMissingRowsDeleted(\"books\", \"id\"", "SQLite books table must reconcile hard-deleted shelf records.");
assertIncludes(STORAGE_CORE, "markMissingRowsDeleted(\"book_files\", \"book_id\"", "SQLite book_files table must reconcile deleted book files.");
assertIncludes(STORAGE_CORE, "markMissingRowsDeleted(\"inspirations\", \"id\"", "SQLite inspirations table must tombstone deleted ideas so they do not reappear after restart.");
assertIncludes(STORAGE_CORE, "snapshotWriteQueue", "SQLite snapshot writes must be serialized instead of racing on one connection.");
assertIncludes(STORAGE_CORE, "sqliteSnapshot.tags.length", "Manager-only SQLite data must still be treated as authoritative.");
assertIncludes(STORAGE_CORE, "BOOK_CONTENT_STORAGE_KEY_PREFIX", "Storage should keep old localStorage content only as migration fallback.");
assertIncludes(STORAGE_CORE, "SUPPORTED_MOBILE_BOOK_EXTENSIONS", "Mobile imports must use an explicit book extension allowlist.");
assertIncludes(STORAGE_CORE, "isSupportedMobileBookFileName", "Mobile imports must reject hidden/system files such as .nomedia.");
assertIncludes(STORAGE_CORE, "baseName.startsWith(\".\")", "Mobile storage must reject dotfiles instead of treating them as TXT books.");
assertIncludes(STORAGE_CORE, "validBookIds", "Mobile snapshot normalization must remove progress/session records for books filtered out of the shelf.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "跳过 ${skippedCount} 个非书籍或系统文件", "Mobile import flow must tell users when hidden/system files are skipped.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "没有找到可导入的 TXT / Markdown / EPUB 文件", "Mobile import flow must not import unsupported selections.");
assertIncludes("mobile/src/utils/mobile-helpers.ts", "MAX_MOBILE_IMPORT_BYTES", "Mobile import flow must have a clear per-book size guard instead of letting huge files freeze the WebView.");
assertIncludes("mobile/src/utils/mobile-helpers.ts", "HOT_READER_CACHE_BYTES", "Mobile import flow must bound warm text caching and must not put huge novels into localStorage.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "canCacheReaderText", "Mobile reader must cache only bounded non-EPUB text content.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "writeReaderTextCache", "Mobile reader must centralize hot-cache writes and cleanup.");
assertIncludes("mobile/src/utils/mobile-helpers.ts", "readFileAsBase64", "EPUB imports should use async FileReader base64 reads instead of blocking manual ArrayBuffer conversion in the import path.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "waitForBrowserPaint", "Mobile import flow must yield between heavy import steps so the app can show progress instead of looking frozen.");
assertIncludes("mobile/src/hooks/useMobileImport.ts", "failedCount", "Mobile import flow must handle per-file failures and continue importing the remaining selected books.");
assertIncludes("mobile/src/utils/mobile-helpers.ts", "localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${bookId}`)", "Mobile import flow must clean failed hot-cache writes instead of leaving partial localStorage book content.");
assertIncludes(STORAGE_BOOKS, "正文保存到手机本地失败", "Imported books must not be added to the shelf if the actual book file failed to persist.");
assertIncludes(STORAGE_BOOKS, "正文为空，未导入", "Mobile imports must reject empty text/Markdown files instead of creating ghost books.");
assertIncludes(STORAGE_CORE, "contentStatus", "Mobile books must distinguish available local files from missing sync placeholders.");
assertIncludes(STORAGE_CORE, "origin", "Mobile books must track whether content came from local import or sync.");
assertIncludes(STORAGE_CORE, "sync_placeholder", "Synced metadata without a local file must be represented as a placeholder.");
assertIncludes(STORAGE_BOOKS, "sync_downloaded", "Downloaded synced books must be marked as available local content.");
assertIncludes(STORAGE_CORE, "sanitizeMobileBookForSync", "Mobile sync payloads must remove local-only runtime fields.");
assertIncludes(STORAGE_CORE, "MOBILE_READER_PREVIEW_CHARS = 64 * 1024", "Mobile reader previews must be capped so localStorage snapshots stay small.");
assertIncludes(STORAGE_CORE, "readerPreview: _readerPreview", "Reader previews must not be uploaded in sync metadata payloads.");
assertIncludes(STORAGE_CORE, "localContentPath: _localContentPath", "Local content paths must not be uploaded in sync metadata payloads.");
assertIncludes(STORAGE_BOOKS, "author: metadata.author", "EPUB imports must read author metadata from the EPUB package instead of scanning base64 text.");
assertIncludes(STORAGE_BOOKS, "author: detectAuthor(content)", "TXT and Markdown imports should retain local author-line detection.");
assertIncludes("mobile/src/sync/webdav-sync.ts", "sanitizeMobileBookForSync", "WebDAV book metadata uploads must use the same sync sanitizer.");
if (read("mobile/src/sync/webdav-sync.ts").includes('records/books.json", snapshot.books')) {
  fail("WebDAV uploads must not write raw snapshot.books because they can contain local paths and readerPreview.");
}
if (read(STORAGE_CORE).includes("localStorage.setItem(`creation-reading-assistant-mobile-book-content")) {
  fail("Mobile storage must not write full book content into localStorage; use Capacitor Filesystem to avoid WebView crashes after sync.");
}
assertIncludes("mobile/src/types/mobile.ts", "MobileBook", "Mobile storage types must define MobileBook.");
assertIncludes("mobile/src/types/mobile.ts", "localFilePath", "MobileBook must remember the relative local file path, not only a display URI.");
assertIncludes("mobile/src/types/mobile.ts", "BookOrigin", "MobileBook must expose origin metadata for local imports and sync placeholders.");
assertIncludes("mobile/src/types/mobile.ts", "BookContentStatus", "MobileBook must expose content status metadata.");
assertIncludes("mobile/src/types/mobile.ts", "localContentPath", "MobileBook must remember the local content path separate from sync metadata.");
assertIncludes("mobile/src/types/mobile.ts", "SyncAccount", "Mobile storage types must define SyncAccount.");

console.log("[verify-mobile-storage] Mobile SQLite and filesystem guards verified.");
