import { readFileSync } from "node:fs";

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

const main = read("electron/main/index.ts");

for (const channel of [
  "sync:getStatus",
  "sync:startServer",
  "sync:stopServer",
  "sync:createPairingToken",
  "sync:listDevices",
  "sync:removeDevice"
]) {
  assertIncludes("electron/main/index.ts", channel, `Main process must register ${channel}.`);
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
  assertIncludes("electron/main/index.ts", route, `LAN sync server must handle ${route}.`);
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
assertIncludes("electron/main/index.ts", required, `LAN sync server missing ${required}.`);
}

assertIncludes("electron/main/index.ts", "PUT", "LAN sync server must accept a PUT upload for phone-imported book files.");
assertIncludes("electron/main/index.ts", "writeUploadedBookFile", "LAN sync server must persist uploaded phone book files inside the desktop library directory.");
assertIncludes("electron/main/index.ts", "X-Original-File-Name", "Book upload must preserve the original file name for library display.");

assertIncludes("src/features/settings/SettingsPage.tsx", "QRCode.toDataURL", "Settings page must render a real QR code for phone pairing.");
assertIncludes("src/features/settings/SettingsPage.tsx", "pairing.pairingUrls.map", "Settings page must expose alternate LAN pairing URLs for multi-network PCs.");
assertIncludes("src/features/settings/SettingsPage.tsx", "连接失败时试这些地址", "Settings page must explain alternate LAN pairing URLs to users.");
assertIncludes("src/types/sync.ts", "pairingUrls", "Pairing type must expose alternate LAN addresses.");
assertIncludes("src/types/sync.ts", "qrPayloads", "Pairing type must expose all QR payload options.");

if (main.includes("ai-secrets.json") && main.slice(main.indexOf("/sync/"), main.lastIndexOf("/sync/")).includes("ai-secrets")) {
  fail("Sync HTTP handlers must never expose ai-secrets.json.");
}

console.log("[verify-sync-server] LAN sync server guards verified.");
