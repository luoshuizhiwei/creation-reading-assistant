import { existsSync, readFileSync } from "node:fs";

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

if (!existsSync("src/types/sync.ts")) fail("src/types/sync.ts must define shared sync types.");

for (const needle of ["DeviceInfo", "SyncEnvelope", "SyncManifest", "BookFileManifest", "SyncStatus", "PairingTokenResult"]) {
  assertIncludes("src/types/sync.ts", needle, `Sync type ${needle} is required.`);
}

for (const file of ["src/types/inspiration.ts", "src/types/library.ts"]) {
  assertIncludes(file, "revision", `${file} must carry sync revision metadata.`);
  assertIncludes(file, "deviceId", `${file} must carry sync device metadata.`);
  assertIncludes(file, "deletedAt", `${file} must carry tombstone metadata.`);
}

assertIncludes("electron/main/index.ts", "getOrCreateDeviceId", "Main process must have a stable desktop device id.");
assertIncludes("electron/main/index.ts", "withSyncMetadata", "Main process must normalize legacy JSON with sync metadata.");
assertIncludes("electron/main/index.ts", "revision:", "Main process must write revision values.");
assertIncludes("electron/main/index.ts", "deviceId:", "Main process must write device ids.");
assertIncludes("electron/main/index.ts", "deletedAt", "Main process must preserve tombstones.");
assertIncludes("electron/main/index.ts", "buildSyncManifest", "Main process must build sync manifests.");

const main = read("electron/main/index.ts");
const syncSection = main.slice(main.indexOf("buildSyncManifest"), main.indexOf("registerIpc"));
if (syncSection.includes("aiSecretsPath") || syncSection.includes("apiKeyEncrypted")) {
  fail("AI secrets must not be included in sync manifest or sync payload builders.");
}

console.log("[verify-sync-schema] Sync schema guards verified.");
