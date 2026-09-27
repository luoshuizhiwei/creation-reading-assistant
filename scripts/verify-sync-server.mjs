import { readFileSync } from "node:fs";
import { readMainProcess, readSyncImplementation } from "./lib/main-process-sources.mjs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-sync-server] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const main = readMainProcess();

function assertMainIncludes(needle, message) {
  if (!main.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in main process sources.`);
}

for (const channel of [
  "sync:getStatus",
  "sync:startServer",
  "sync:stopServer",
  "sync:createPairingToken",
  "sync:listDevices",
  "sync:removeDevice"
]) {
  assertMainIncludes(channel, `Main process must register ${channel}.`);
  assertIncludes("electron/preload/index.ts", channel, `Preload must expose ${channel}.`);
  assertIncludes("src/types/api.ts", channel.replace("sync:", ""), `Renderer API must type ${channel}.`);
}

for (const route of [
  "/sync/manifest",
  "/sync/pull",
  "/sync/push",
  "/sync/pair",
  "/sync/books/"
]) {
  assertMainIncludes(route, `LAN sync server must handle ${route}.`);
}

for (const required of [
  "createServer",
  "syncServer.listen",
  "127.0.0.1",
  "listLanAddresses",
  "pairingAddresses",
  "createPairingToken",
  "pairingToken",
  "pairingUrls",
  "qrPayloads",
  "downloadBookFile",
  "downloadBookChunk",
  "uploadBookFile"
]) {
assertMainIncludes(required, `LAN sync server missing ${required}.`);
}

assertMainIncludes("PUT", "LAN sync server must accept a PUT upload for phone-imported book files.");
assertMainIncludes("writeUploadedBookFile", "LAN sync server must persist uploaded phone book files inside the desktop library directory.");
assertMainIncludes("X-Original-File-Name", "Book upload must preserve the original file name for library display.");
assertMainIncludes("x-sync-token", "Paired sync requests must include a per-device authorization token.");
assertMainIncludes("syncAuthTokenHash", "Desktop sync state must store only a hash of the device authorization token.");
assertMainIncludes("requirePairedSyncDevice(request, response)", "Manifest, pull, push and book file endpoints must reject unpaired devices.");

assertIncludes("src/features/settings/sections/StorageSection.tsx", "QRCode.toDataURL", "Settings page must render a real QR code for phone pairing.");
assertIncludes("src/features/settings/sections/StorageSection.tsx", "pairing.pairingUrls.map", "Settings page must expose alternate LAN pairing URLs for multi-network PCs.");
assertIncludes("src/features/settings/sections/StorageSection.tsx", "连接失败时试这些地址", "Settings page must explain alternate LAN pairing URLs to users.");
assertIncludes("src/types/sync.ts", "pairingUrls", "Pairing type must expose alternate LAN addresses.");
assertIncludes("src/types/sync.ts", "qrPayloads", "Pairing type must expose all QR payload options.");

const syncImpl = readSyncImplementation();
if (syncImpl.includes("aiSecretsPath") || syncImpl.includes("ai-secrets")) {
  fail("Sync HTTP handlers must never expose ai-secrets.json.");
}

console.log("[verify-sync-server] LAN sync server guards verified.");
