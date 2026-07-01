import { existsSync, readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-scan] ${message}`);
  process.exit(1);
}

function assertFile(path) {
  if (!existsSync(path)) fail(`${path} is required.`);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

for (const file of ["mobile/package.json", "mobile/src/App.tsx", "mobile/src/styles.css"]) assertFile(file);

assertIncludes("mobile/package.json", "jsqr", "Mobile QR scanning must decode WebView video frames instead of relying on an invisible native preview.");
assertIncludes("mobile/src/App.tsx", "QrScanOverlay", "Mobile app must render an in-app QR scan overlay.");
assertIncludes("mobile/src/App.tsx", "navigator.mediaDevices.getUserMedia", "QR scan overlay must request a visible WebView camera stream.");
assertIncludes("mobile/src/App.tsx", "qr-video", "QR scan overlay must include a visible video preview.");
assertIncludes("mobile/src/App.tsx", "粘贴配对 URL", "QR scan overlay must keep paste pairing as a fallback.");
assertIncludes("mobile/src/App.tsx", "扫码超时", "QR scan overlay must show a Chinese timeout message.");
assertIncludes("mobile/src/styles.css", ".qr-scan-overlay", "QR scan overlay must have dedicated full-screen mobile styling.");
assertIncludes("mobile/src/styles.css", ".qr-video", "QR scan video preview must be styled as a visible surface.");

console.log("[verify-mobile-scan] Mobile QR scan overlay guards verified.");
