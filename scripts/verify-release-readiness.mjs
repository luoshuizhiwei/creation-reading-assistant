import { existsSync, readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-release-readiness] ${message}`);
  process.exit(1);
}

function assertFile(path) {
  if (!existsSync(path)) fail(`${path} is required for release publishing.`);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

// --- Required files ---
// Desktop release files + license tracking files (GPL compliance; mobile/ deleted,
// license and upstream provenance archived under archives/frozen-mobile/).
for (const file of [
  ".github/workflows/release.yml",
  "CHANGELOG.md",
  "docs/GITHUB_RELEASE_PROCESS.md",
  "package.json",
  "package-lock.json",
  // License/notice/upstream files retained for GPL tracking of the deleted mobile/
  // line, archived under archives/frozen-mobile/.
  "archives/frozen-mobile/LICENSE",
  "archives/frozen-mobile/NOTICE.md",
  "archives/frozen-mobile/THIRD_PARTY_NOTICES.md",
  "archives/frozen-mobile/FROZEN.md",
  "archives/frozen-mobile/legado-reader-core/LICENSE",
  "archives/frozen-mobile/legado-reader-core/UPSTREAM.md",
  "archives/frozen-mobile/legado-reader-core/PATCHES.md",
  "scripts/dist-beta-offline.mjs"
]) {
  assertFile(file);
}

// --- .gitignore guards ---
assertIncludes(".gitignore", "release*/", "Generated desktop release directories must stay out of Git.");
assertIncludes(".gitignore", "mobile-release/", "Generated mobile APK directory must stay out of Git.");
assertIncludes(".gitignore", ".github-release/", "Local release staging directory must stay out of Git.");
// mobile/ is deleted; native android/ signing file guards apply until P0-A3
// restores Android Release (no keystore.properties signing config exists yet).
assertIncludes("android/.gitignore", "*.jks", "Android keystores must stay out of Git.");

// --- Release workflow guards (desktop-only until P0-A3) ---
const workflow = read(".github/workflows/release.yml");
for (const required of [
  "PUBLIC_RELEASE_TOKEN",
  "windows-x64-setup.exe",
  "windows-x64-portable.zip",
  "SHA256SUMS.txt",
  "npm run verify:beta"
]) {
  if (!workflow.includes(required)) fail(`Release workflow is missing ${required}.`);
}
if (workflow.includes("assembleDebug") || workflow.includes("mobile-debug.apk")) {
  fail("Release workflow must not publish a Debug APK.");
}
// P0-A3 TODO: the workflow must document that native android/ Release is pending.
if (!workflow.includes("P0-A3")) {
  fail("Release workflow must reference P0-A3 TODO for native Android Release migration.");
}

const offlineBuilder = read("scripts/dist-beta-offline.mjs");
if (!offlineBuilder.includes('"--publish"') || !offlineBuilder.includes('"never"')) {
  fail("Offline desktop packaging must disable electron-builder implicit tag publishing.");
}

// --- Version alignment (root only; mobile/ is deleted, no longer aligned) ---
const rootPackage = JSON.parse(read("package.json"));
const rootLock = JSON.parse(read("package-lock.json"));
if (rootPackage.version !== rootLock.version || rootPackage.version !== rootLock.packages?.[""]?.version) {
  fail("Root package-lock version does not match package.json.");
}

// --- Changelog & docs ---
assertIncludes("CHANGELOG.md", "## 未发布", "Changelog must keep a Chinese unpublished section.");
assertIncludes("CHANGELOG.md", `## v${rootPackage.version}`, "Changelog must document the release version.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "私有源码仓库 + 公开下载仓库", "Release docs must describe repository separation.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "完整对应源代码", "Release docs must describe GPL source distribution.");

// --- Secret scan ---
for (const file of ["CHANGELOG.md", "docs/GITHUB_RELEASE_PROCESS.md", "archives/frozen-mobile/NOTICE.md", "archives/frozen-mobile/THIRD_PARTY_NOTICES.md"]) {
  const content = read(file);
  if (/sk-[A-Za-z0-9]/.test(content)) fail(`${file} appears to contain an API key-like secret.`);
  if (/gh[opusr]_[A-Za-z0-9]{20,}/.test(content)) fail(`${file} appears to contain a GitHub token.`);
}

console.log(`[verify-release-readiness] v${rootPackage.version} desktop release and GPL license-tracking guards verified. Native Android Release pending P0-A3.`);
