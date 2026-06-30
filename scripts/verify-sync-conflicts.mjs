import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-sync-conflicts] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("electron/main/index.ts", "mergeIncomingInspirations", "Sync push must merge incoming inspirations.");
assertIncludes("electron/main/index.ts", "冲突副本", "Inspiration text conflicts must create a conflict copy.");
assertIncludes("electron/main/index.ts", "mergeByUpdatedAt", "Ordinary sync records must use updatedAt conflict resolution.");
assertIncludes("electron/main/index.ts", "mergeReadingSessionsById", "Reading sessions must dedupe by session id.");
assertIncludes("electron/main/index.ts", "deletedAt", "Sync merge must preserve tombstones.");
assertIncludes("electron/main/index.ts", "applySyncPush", "LAN sync server must apply incoming sync pushes.");
assertIncludes("electron/main/index.ts", "bookFiles", "Sync manifest must include book file manifests.");

console.log("[verify-sync-conflicts] Sync conflict guards verified.");
