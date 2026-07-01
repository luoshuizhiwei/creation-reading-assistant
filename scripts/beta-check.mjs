import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const args = new Set(process.argv.slice(2));
const requireArtifact = args.has("--require-artifact");
const skipBuild = args.has("--skip-build");
const skipAudit = args.has("--skip-audit");
const expectedProductName = "\u521b\u4f5c\u9605\u8bfb\u52a9\u624b";
const expectedExeName = `${expectedProductName}.exe`;

const requiredIpcChannels = [
  "library:importBook",
  "library:importEpub",
  "reader:openBook",
  "reader:openEpub",
  "reader:saveProgress",
  "reader:startSession",
  "reader:updateSession",
  "reader:endSession",
  "reader:getStats",
  "settings:get",
  "settings:resetReaderSettings",
  "settings:chooseDataDirectory",
  "settings:chooseLibraryDirectory",
  "settings:migrateDataDirectory",
  "settings:migrateLibraryDirectory",
  "storage:getLocations",
  "sync:getStatus",
  "sync:startServer",
  "sync:stopServer",
  "sync:createPairingToken",
  "sync:listDevices",
  "sync:removeDevice",
  "inspiration:list",
  "inspiration:create",
  "inspiration:update",
  "inspiration:addVariant",
  "ai:getSettings",
  "ai:saveApiKey",
  "ai:run",
  "search:global",
  "backup:create",
  "backup:restore",
  "diagnostics:exportDebugInfo"
];

const suspiciousMojibakeCodePoints = new Set([
  0x704f, 0x7f02, 0x95c3, 0x9351, 0x9428, 0x93c2, 0x6d93, 0x59ab, 0x5a08, 0x93ba,
  0x93c8, 0x6d63, 0x5f6c, 0x4f34, 0x4f6a, 0x6b22, 0x5a09, 0x934f, 0x93c4, 0x7481,
  0x6828, 0x9366, 0x9286
]);

function logStep(message) {
  console.log(`\n[beta-check] ${message}`);
}

function fail(message) {
  console.error(`\n[beta-check] FAILED: ${message}`);
  process.exit(1);
}

function run(command, options = {}) {
  logStep(command);
  const result = spawnSync(command, {
    cwd: root,
    shell: true,
    stdio: "inherit",
    env: { ...process.env, ...options.env }
  });
  if (result.status !== 0) fail(`Command failed: ${command}`);
}

function readText(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function walkFiles(directory, predicate, results = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".git") continue;
      walkFiles(fullPath, predicate, results);
    } else if (predicate(fullPath)) {
      results.push(fullPath);
    }
  }
  return results;
}

function assertPackageMetadata() {
  logStep("Checking package metadata");
  const pkg = JSON.parse(readText("package.json"));
  if (pkg.build?.productName !== expectedProductName) {
    fail(`build.productName must be ${expectedProductName}, got ${pkg.build?.productName}`);
  }
  if (!pkg.author || typeof pkg.author !== "string") {
    fail("package.json author is required for Beta packaging metadata.");
  }
  console.log(`[ok] productName=${pkg.build.productName}`);
  console.log(`[ok] author=${pkg.author}`);
}

function assertRendererSecurity() {
  logStep("Scanning renderer for direct privileged imports");
  const files = walkFiles(path.join(root, "src"), (file) => /\.(ts|tsx)$/.test(file));
  const forbidden = /\bfrom\s+["'](?:electron|fs|path|node:fs|node:path)["']|\brequire\(["'](?:electron|fs|path|node:fs|node:path)["']\)/;
  const offenders = files.filter((file) => forbidden.test(readFileSync(file, "utf8")));
  if (offenders.length > 0) {
    fail(`Renderer imports privileged modules:\n${offenders.map((file) => `  - ${path.relative(root, file)}`).join("\n")}`);
  }
  console.log(`[ok] scanned ${files.length} renderer TypeScript files`);
}

function assertIpcSurface() {
  logStep("Checking critical IPC surface");
  const main = readText("electron/main/index.ts");
  const preload = readText("electron/preload/index.ts");
  const missing = requiredIpcChannels.filter((channel) => !main.includes(`"${channel}"`) || !preload.includes(`"${channel}"`));
  if (missing.length > 0) fail(`Missing IPC channels in main/preload:\n${missing.map((channel) => `  - ${channel}`).join("\n")}`);
  console.log(`[ok] ${requiredIpcChannels.length} critical IPC channels found in main and preload`);
}

function assertReadableUtf8() {
  logStep("Checking common UTF-8 mojibake markers");
  const files = [
    "package.json",
    "BETA_CHECKLIST.md",
    "src/app/App.tsx",
    "src/features/settings/SettingsPage.tsx",
    "src/pages/StartPage.tsx",
    "src/features/inspiration/InspirationPage.tsx"
  ].filter((file) => existsSync(path.join(root, file)));
  const offenders = [];
  for (const file of files) {
    const text = readText(file);
    const hasReplacement = text.includes("\uFFFD");
    const hasCommonMojibake = [...text].some((char) => suspiciousMojibakeCodePoints.has(char.codePointAt(0)));
    if (hasReplacement || hasCommonMojibake) offenders.push(file);
  }
  if (offenders.length > 0) fail(`Possible mojibake found:\n${offenders.map((file) => `  - ${file}`).join("\n")}`);
  console.log(`[ok] checked ${files.length} human-facing files`);
}

function candidateArtifacts() {
  const explicitArtifact = process.env.BETA_ARTIFACT;
  const candidates = explicitArtifact
    ? [explicitArtifact]
    : [path.join("release-beta", "win-unpacked", expectedExeName)];
  return candidates.map((candidate) => path.resolve(root, candidate));
}

function assertArtifactIfRequested() {
  logStep("Checking packaged artifact");
  const candidates = candidateArtifacts();
  const existing = candidates.find((candidate) => existsSync(candidate));
  if (!existing) {
    const message = `No packaged exe found. Checked:\n${candidates.map((candidate) => `  - ${candidate}`).join("\n")}`;
    if (requireArtifact) fail(message);
    console.log(`[warn] ${message}`);
    return;
  }
  const size = statSync(existing).size;
  if (size < 50_000_000) fail(`Packaged exe looks too small: ${existing} (${size} bytes)`);
  const asarPath = path.join(path.dirname(existing), "resources", "app.asar");
  if (!existsSync(asarPath)) fail(`Missing app.asar next to artifact: ${asarPath}`);
  console.log(`[ok] artifact=${existing}`);
  console.log(`[ok] exeSize=${size}`);
}

assertPackageMetadata();
assertReadableUtf8();
assertRendererSecurity();
assertIpcSurface();
run("npm run verify:data-integrity");
run("npm run verify:hardening");
run("npm run verify:reposition");
run("npm run verify:inspiration");
run("npm run verify:ai-settings");
run("npm run verify:search-overlay");
run("npm run verify:stats-ui");
run("npm run verify:epub-restore");
run("npm run verify:reader-settings");
run("npm run verify:portable-storage");
run("npm run verify:reading-inspiration");
run("npm run verify:reader-formats");
run("npm run verify:interaction-polish");
run("npm run verify:clean-reposition");
run("npm run verify:sync-schema");
run("npm run verify:sync-server");
run("npm run verify:mobile-adapter");
run("npm run verify:mobile-ui");
run("npm run verify:mobile-storage");
run("npm run verify:mobile-reader");
run("npm run verify:mobile-scan");
run("npm run verify:mobile-sync-stability");
run("npm run verify:mobile-reader-layout");
run("npm run verify:mobile-reading-experience");
run("npm run verify:mobile-webdav");
run("npm run verify:mobile-inspiration");
run("npm run verify:sync-conflicts");
run("npm run verify:release-readiness");
run("npm run verify:installer-release");
run("npm run verify:visual-polish");
run("npm run verify:ux-polish");
if (!skipBuild) run("npm run build");
if (!skipAudit) run("npm audit --omit=dev");
assertArtifactIfRequested();

console.log("\n[beta-check] All checks passed.");
