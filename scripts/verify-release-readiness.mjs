import { existsSync, readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-release-readiness] ${message}`);
  process.exit(1);
}

function assertFile(path) {
  if (!existsSync(path)) fail(`${path} is required for GitHub release publishing.`);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

for (const file of [
  ".github/workflows/release.yml",
  "CHANGELOG.md",
  "docs/GITHUB_RELEASE_PROCESS.md",
  "package-lock.json",
  "mobile/package-lock.json"
]) {
  assertFile(file);
}

assertIncludes(".gitignore", "release*/", "Generated desktop release directories must stay out of Git.");
assertIncludes(".gitignore", "mobile-release/", "Generated mobile APK directory must stay out of Git.");
assertIncludes(".gitignore", ".github-release/", "Local release staging directory must stay out of Git.");
assertIncludes(".gitignore", ".claude/", "Claude local workspace state must stay out of Git.");

assertIncludes(".github/workflows/release.yml", "softprops/action-gh-release", "GitHub workflow must publish release assets.");
assertIncludes(".github/workflows/release.yml", "creation-reading-assistant-windows-win-unpacked.zip", "GitHub workflow must upload Windows desktop artifact.");
assertIncludes(".github/workflows/release.yml", "creation-reading-assistant-mobile-debug.apk", "GitHub workflow must upload Android APK artifact.");
assertIncludes(".github/workflows/release.yml", "SHA256SUMS.txt", "GitHub workflow must publish checksums.");
assertIncludes(".github/workflows/release.yml", "npm run verify:beta", "GitHub workflow must run the beta gate.");
assertIncludes(".github/workflows/release.yml", "npm run verify:beta:release", "GitHub workflow must verify packaged desktop artifact.");
assertIncludes(".github/workflows/release.yml", "gradlew.bat assembleDebug", "GitHub workflow must build Android APK.");

assertIncludes("CHANGELOG.md", "## 未发布", "Changelog must keep a Chinese unpublished section for major updates.");
assertIncludes("CHANGELOG.md", "## v0.1.0", "Changelog must document the current baseline release.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "gh repo create creation-reading-assistant --private", "Release docs must document private repository setup.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "git tag -a", "Release docs must document tag-based publishing.");

for (const file of ["CHANGELOG.md", "docs/GITHUB_RELEASE_PROCESS.md"]) {
  const content = read(file);
  if (/sk-[A-Za-z0-9]/.test(content)) fail(`${file} appears to contain an API key-like secret.`);
  if (/\bTODO\b|\bTBD\b/.test(content)) fail(`${file} still contains TODO/TBD placeholders.`);
}

console.log("[verify-release-readiness] GitHub release pipeline guards verified.");
