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

console.log("[verify-mobile-webdav] Mobile WebDAV sync guards verified.");
