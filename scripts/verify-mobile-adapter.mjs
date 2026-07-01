import { existsSync, readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-adapter] ${message}`);
  process.exit(1);
}

function assertFile(path) {
  if (!existsSync(path)) fail(`${path} is required for the Android mobile app scaffold.`);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

for (const file of [
  "mobile/package.json",
  "mobile/capacitor.config.ts",
  "mobile/index.html",
  "mobile/src/App.tsx",
  "mobile/src/services/mobile-storage.ts",
  "mobile/src/services/sync-client.ts",
  "mobile/src/styles.css"
]) {
  assertFile(file);
}

assertIncludes("mobile/package.json", "@capacitor/core", "Mobile package must use Capacitor.");
assertIncludes("mobile/package.json", "@capacitor/android", "Mobile package must target Android.");
assertIncludes("mobile/package.json", "@capacitor-mlkit/barcode-scanning", "Mobile app must include a real native QR scanner dependency.");
assertIncludes("mobile/src/App.tsx", "灵感中心", "Mobile app must keep inspiration as a primary entry.");
assertIncludes("mobile/src/App.tsx", "本地书库", "Mobile app must keep local library as a primary entry.");
assertIncludes("mobile/src/App.tsx", "扫码连接电脑", "Mobile app must expose pairing flow.");
assertIncludes("mobile/src/App.tsx", "scanPairingQrCode", "Mobile pairing flow must expose a camera QR scan action.");
assertIncludes("mobile/src/App.tsx", "正在尝试第", "Mobile LAN pairing should show which candidate address is being tried.");
assertIncludes("mobile/src/services/sync-client.ts", "parsePairingCandidates", "Mobile sync client must parse every alternate LAN pairing URL.");
assertIncludes("mobile/src/services/sync-client.ts", "pairingUrls", "Mobile sync client must preserve alternate pairing URLs from desktop payloads.");
assertIncludes("mobile/src/services/sync-client.ts", "lastError", "Mobile sync client should return useful diagnostics when all pairing candidates fail.");
assertIncludes("mobile/src/services/mobile-storage.ts", "localStorage", "First mobile adapter must support offline local storage.");
assertIncludes("mobile/src/services/sync-client.ts", "sync/manifest", "Mobile sync client must pull desktop manifest.");
assertIncludes("mobile/src/services/sync-client.ts", "sync/push", "Mobile sync client must push local changes.");
assertIncludes("mobile/src/services/sync-client.ts", "sync/books", "Mobile sync client must support book file download.");
assertIncludes("mobile/src/services/sync-client.ts", "PairingTokenResult", "Mobile sync client must understand pairing payloads.");
assertIncludes("mobile/android/app/src/main/AndroidManifest.xml", "usesCleartextTraffic=\"true\"", "Android app must allow LAN HTTP sync in the MVP.");
assertIncludes("mobile/android/app/src/main/AndroidManifest.xml", "networkSecurityConfig", "Android app must opt into LAN cleartext network policy.");
assertIncludes("mobile/android/app/src/main/AndroidManifest.xml", "android.permission.CAMERA", "Android app must request camera permission for QR scanning.");
assertIncludes("mobile/android/app/src/main/res/xml/network_security_config.xml", "cleartextTrafficPermitted=\"true\"", "Android network security config must permit local HTTP sync.");

console.log("[verify-mobile-adapter] Mobile adapter guards verified.");
