import { existsSync } from "node:fs";
import { readWithCssImports } from "./lib/read-with-css-imports.mjs";

function read(path) {
  return readWithCssImports(path);
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

const appPath = "mobile/src/App.tsx";
const qrScanOverlayPath = "mobile/src/components/QrScanOverlay.tsx";

assertIncludes("mobile/package.json", "jsqr", "Mobile QR scanning must decode WebView video frames instead of relying on an invisible native preview.");
assertIncludes(appPath, "QrScanOverlay", "Mobile app must render an in-app QR scan overlay.");
assertIncludes(qrScanOverlayPath, "navigator.mediaDevices.getUserMedia", "QR scan overlay must request a visible WebView camera stream.");
assertIncludes(qrScanOverlayPath, "qr-video", "QR scan overlay must include a visible video preview.");
assertIncludes(qrScanOverlayPath, "粘贴配对 URL", "QR scan overlay must keep paste pairing as a fallback.");
assertIncludes(qrScanOverlayPath, "扫码超时", "QR scan overlay must show a Chinese timeout message.");
assertIncludes("mobile/src/styles.css", ".qr-scan-overlay", "QR scan overlay must have dedicated full-screen mobile styling.");
assertIncludes("mobile/src/styles.css", ".qr-video", "QR scan video preview must be styled as a visible surface.");

console.log("[verify-mobile-scan] Mobile QR scan overlay guards verified.");
