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

for (const file of [
  ".github/workflows/release.yml",
  "CHANGELOG.md",
  "docs/GITHUB_RELEASE_PROCESS.md",
  "package.json",
  "package-lock.json",
  "mobile/package.json",
  "mobile/package-lock.json",
  "mobile/LICENSE",
  "mobile/NOTICE.md",
  "mobile/THIRD_PARTY_NOTICES.md",
  "mobile/android/legado-reader-core/LICENSE",
  "mobile/android/legado-reader-core/UPSTREAM.md",
  "mobile/android/legado-reader-core/PATCHES.md"
]) {
  assertFile(file);
}

assertIncludes(".gitignore", "release*/", "Generated desktop release directories must stay out of Git.");
assertIncludes(".gitignore", "mobile-release/", "Generated mobile APK directory must stay out of Git.");
assertIncludes(".gitignore", ".github-release/", "Local release staging directory must stay out of Git.");
assertIncludes("mobile/android/.gitignore", "*.jks", "Android keystores must stay out of Git.");
assertIncludes("mobile/android/.gitignore", "keystore.properties", "Android signing properties must stay out of Git.");

const workflow = read(".github/workflows/release.yml");
for (const required of [
  "assembleRelease",
  "app-release.apk",
  "apksigner.bat",
  "ANDROID_KEYSTORE_BASE64",
  "PUBLIC_RELEASE_TOKEN",
  "creation-reading-assistant-android-source.zip",
  "windows-x64-setup.exe",
  "windows-x64-portable.zip",
  "mobile-update.json",
  "latest-mobile.json",
  "SHA256SUMS.txt",
  "mobile/LICENSE",
  "mobile/NOTICE.md",
  "mobile/THIRD_PARTY_NOTICES.md",
  "npm run verify:beta"
]) {
  if (!workflow.includes(required)) fail(`Release workflow is missing ${required}.`);
}
if (workflow.includes("assembleDebug") || workflow.includes("mobile-debug.apk")) {
  fail("Release workflow must not publish a Debug APK.");
}

const rootPackage = JSON.parse(read("package.json"));
const mobilePackage = JSON.parse(read("mobile/package.json"));
const rootLock = JSON.parse(read("package-lock.json"));
const mobileLock = JSON.parse(read("mobile/package-lock.json"));
if (rootPackage.version !== mobilePackage.version) fail("Desktop and mobile package versions must stay aligned for a combined release.");
if (rootPackage.version !== rootLock.version || rootPackage.version !== rootLock.packages?.[""]?.version) {
  fail("Root package-lock version does not match package.json.");
}
if (mobilePackage.version !== mobileLock.version || mobilePackage.version !== mobileLock.packages?.[""]?.version) {
  fail("Mobile package-lock version does not match mobile/package.json.");
}

const gradle = read("mobile/android/app/build.gradle");
if (!gradle.includes("versionCode 27")) fail("Android versionCode must be 27 for v0.2.0.");
for (const required of [
  "CRA_ANDROID_KEYSTORE_FILE",
  "CRA_ANDROID_KEYSTORE_PASSWORD",
  "CRA_ANDROID_KEY_ALIAS",
  "CRA_ANDROID_KEY_PASSWORD",
  "拒绝生成未签名 Android Release 包"
]) {
  if (!gradle.includes(required)) fail(`Android release signing guard is missing ${required}.`);
}

assertIncludes("CHANGELOG.md", "## 未发布", "Changelog must keep a Chinese unpublished section.");
assertIncludes("CHANGELOG.md", `## v${mobilePackage.version}`, "Changelog must document the release version.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "私有源码仓库 + 公开下载仓库", "Release docs must describe repository separation.");
assertIncludes("docs/GITHUB_RELEASE_PROCESS.md", "完整对应源代码", "Release docs must describe GPL source distribution.");

for (const file of ["CHANGELOG.md", "docs/GITHUB_RELEASE_PROCESS.md", "mobile/NOTICE.md", "mobile/THIRD_PARTY_NOTICES.md"]) {
  const content = read(file);
  if (/sk-[A-Za-z0-9]/.test(content)) fail(`${file} appears to contain an API key-like secret.`);
  if (/gh[opusr]_[A-Za-z0-9]{20,}/.test(content)) fail(`${file} appears to contain a GitHub token.`);
}

console.log(`[verify-release-readiness] v${mobilePackage.version} signed release and GPL distribution guards verified.`);
