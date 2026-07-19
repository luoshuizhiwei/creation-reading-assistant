import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

function read(filePath) {
  return readFileSync(filePath, "utf-8");
}

function fail(message) {
  console.error(`[verify-installer-release] ${message}`);
  process.exit(1);
}

function assertIncludes(filePath, needle, message) {
  const content = read(filePath);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${filePath}`);
}

assertIncludes("package.json", "dist:beta:installer", "package.json must expose an installer build command.");
assertIncludes("scripts/dist-beta-offline.mjs", "--installer", "Offline desktop packaging must support installer mode.");
assertIncludes(".github/workflows/release.yml", "dist:beta:installer", "GitHub release workflow must build the installer package.");
assertIncludes(".github/workflows/release.yml", "windows-x64-setup.exe", "GitHub release workflow must upload a versioned Windows installer.");
assertIncludes("scripts/verify-release-readiness.mjs", "windows-x64-setup.exe", "Release readiness must guard the versioned Windows installer asset.");

if (process.argv.includes("--require-artifact")) {
  const releaseRoot = path.resolve("release-beta");
  const version = JSON.parse(read("package.json")).version;
  const candidates = existsSync(releaseRoot) ? [path.join(releaseRoot, `创作阅读助手-${version}-win-x64.exe`)] : [];
  const found = candidates.find((candidate) => existsSync(candidate));
  if (!found) fail(`No Windows installer exe found in ${releaseRoot}.`);
  const size = statSync(found).size;
  if (size < 50_000_000) fail(`Installer looks too small: ${found} (${size} bytes)`);
  console.log(`[verify-installer-release] installer=${found}`);
}

console.log("[verify-installer-release] Windows installer release guards verified.");
