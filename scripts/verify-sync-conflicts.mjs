import { readMainProcess } from "./lib/main-process-sources.mjs";

function fail(message) {
  console.error(`[verify-sync-conflicts] ${message}`);
  process.exit(1);
}

const mainProcess = readMainProcess();

function assertMainIncludes(needle, message) {
  if (!mainProcess.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in main process sources.`);
}

assertMainIncludes("mergeIncomingInspirations", "Sync push must merge incoming inspirations.");
assertMainIncludes("冲突副本", "Inspiration text conflicts must create a conflict copy.");
assertMainIncludes("mergeByUpdatedAt", "Ordinary sync records must use updatedAt conflict resolution.");
assertMainIncludes("mergeReadingSessionsById", "Reading sessions must dedupe by session id.");
assertMainIncludes("deletedAt", "Sync merge must preserve tombstones.");
assertMainIncludes("applySyncPush", "LAN sync server must apply incoming sync pushes.");
assertMainIncludes("bookFiles", "Sync manifest must include book file manifests.");

console.log("[verify-sync-conflicts] Sync conflict guards verified.");
