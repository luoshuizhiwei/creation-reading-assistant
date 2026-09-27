import { existsSync, readFileSync } from "node:fs";
import { readMainProcess, readSyncImplementation } from "./lib/main-process-sources.mjs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-sync-schema] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const mainProcess = readMainProcess();

function assertMainIncludes(needle, message) {
  if (!mainProcess.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in main process sources.`);
}

if (!existsSync("src/types/sync.ts")) fail("src/types/sync.ts must define shared sync types.");

for (const needle of ["DeviceInfo", "SyncEnvelope", "SyncManifest", "BookFileManifest", "SyncStatus", "PairingTokenResult"]) {
  assertIncludes("src/types/sync.ts", needle, `Sync type ${needle} is required.`);
}

for (const file of ["src/types/inspiration.ts", "src/types/library.ts"]) {
  assertIncludes(file, "revision", `${file} must carry sync revision metadata.`);
  assertIncludes(file, "deviceId", `${file} must carry sync device metadata.`);
  assertIncludes(file, "deletedAt", `${file} must carry tombstone metadata.`);
}

assertMainIncludes("getOrCreateDeviceId", "Main process must have a stable desktop device id.");
assertMainIncludes("withSyncMetadata", "Main process must normalize legacy JSON with sync metadata.");
assertMainIncludes("revision:", "Main process must write revision values.");
assertMainIncludes("deviceId:", "Main process must write device ids.");
assertMainIncludes("deletedAt", "Main process must preserve tombstones.");
assertMainIncludes("buildSyncManifest", "Main process must build sync manifests.");

const syncSection = readSyncImplementation();
if (syncSection.includes("aiSecretsPath") || syncSection.includes("apiKeyEncrypted")) {
  fail("AI secrets must not be included in sync manifest or sync payload builders.");
}

console.log("[verify-sync-schema] Sync schema guards verified.");
