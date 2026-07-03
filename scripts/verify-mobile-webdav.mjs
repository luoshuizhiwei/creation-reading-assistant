import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-webdav] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/sync/webdav-sync.ts", ".creation-reading-assistant/manifest.json", "WebDAV sync must use the agreed app manifest path.");
assertIncludes("mobile/src/sync/webdav-sync.ts", ".creation-reading-assistant/records/", "WebDAV sync must use records directory.");
assertIncludes("mobile/src/sync/webdav-sync.ts", ".creation-reading-assistant/books/", "WebDAV sync must use books directory.");
assertIncludes("mobile/src/sync/webdav-sync.ts", "testWebDavConnection", "WebDAV sync must support connection testing.");
assertIncludes("mobile/src/sync/webdav-sync.ts", "uploadWebDavSnapshot", "WebDAV sync must support upload.");
assertIncludes("mobile/src/sync/webdav-sync.ts", "downloadWebDavSnapshot", "WebDAV sync must support download.");
assertIncludes("mobile/src/App.tsx", "WebDAV", "Profile page must expose WebDAV settings.");
assertIncludes("mobile/src/App.tsx", "local-desktop-lan", "Mobile app must preserve desktop LAN sync provider.");
assertIncludes("mobile/src/App.tsx", "webdav", "Mobile app must expose WebDAV sync provider.");
assertIncludes("mobile/src/services/mobile-secret-store.ts", "indexedDB.open(MOBILE_SECRET_DB_NAME", "WebDAV credentials must use the shared mobile secret store.");
assertIncludes("mobile/src/services/mobile-webdav-secrets.ts", "loadWebDavPasswordSecret", "WebDAV password/token must be loadable from the local secret store.");
assertIncludes("mobile/src/services/mobile-webdav-secrets.ts", "saveWebDavPasswordSecret", "WebDAV password/token must be saved through the local secret store.");
assertIncludes("mobile/src/services/mobile-webdav-secrets.ts", "clearWebDavPasswordSecret", "WebDAV password/token must be clearable.");
assertIncludes("mobile/src/App.tsx", "getWebDavCredentials", "WebDAV sync actions must combine visible settings with saved secret credentials.");
assertIncludes("mobile/src/App.tsx", "本机密钥库保存 WebDAV 密码 / token", "WebDAV UI must explain local-only credential storage.");
assertIncludes("mobile/src/services/mobile-storage.ts", "sanitizeSyncAccount", "Mobile snapshot storage must sanitize sync accounts.");

const types = read("mobile/src/types/mobile.ts");
const storage = read("mobile/src/services/mobile-storage.ts");
if (/passwordToken/.test(types)) {
  fail("SyncAccount type must not expose passwordToken.");
}
if (!/passwordToken: _passwordToken/.test(storage) || !/password: _password/.test(storage)) {
  fail("Storage must strip legacy password/passwordToken fields from sync accounts.");
}
if (/passwordToken/.test(read("mobile/src/sync/webdav-sync.ts"))) {
  fail("WebDAV sync transport must not read passwordToken from snapshot metadata.");
}

console.log("[verify-mobile-webdav] Mobile WebDAV sync guards verified.");
