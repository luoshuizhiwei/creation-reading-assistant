import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
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
assertIncludes("mobile/src/services/mobile-storage.ts", "migrateLegacySnapshot", "Storage must migrate the old localStorage snapshot.");
assertIncludes("mobile/src/services/mobile-storage.ts", "saveSyncedMobileBookFile", "Mobile storage must persist book files downloaded from desktop sync.");
assertIncludes("mobile/src/services/mobile-storage.ts", "BOOK_CONTENT_STORAGE_KEY_PREFIX", "Storage should keep old localStorage content only as migration fallback.");
if (read("mobile/src/services/mobile-storage.ts").includes("localStorage.setItem(`creation-reading-assistant-mobile-book-content")) {
  fail("Mobile storage must not write full book content into localStorage; use Capacitor Filesystem to avoid WebView crashes after sync.");
}
assertIncludes("mobile/src/types/mobile.ts", "MobileBook", "Mobile storage types must define MobileBook.");
assertIncludes("mobile/src/types/mobile.ts", "localFilePath", "MobileBook must remember the relative local file path, not only a display URI.");
assertIncludes("mobile/src/types/mobile.ts", "SyncAccount", "Mobile storage types must define SyncAccount.");

console.log("[verify-mobile-storage] Mobile SQLite and filesystem guards verified.");
