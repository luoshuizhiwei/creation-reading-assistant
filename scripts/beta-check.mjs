import { execSync, spawnSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { evaluateVitestRun, summarizeVitestJson, extractTestDetails, detectAbi, validateVitestJson } from "./vitest-summary.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const argv = process.argv.slice(2);
const args = new Set(argv);
const requireArtifact = args.has("--require-artifact");
const skipBuild = args.has("--skip-build");
const skipAudit = args.has("--skip-audit");
const skipVitest = args.has("--skip-vitest");

// --scope=desktop|all|auto (default: all)
// Note: mobile/Capacitor scope removed (P0-A2, 2026-07-29). mobile/ is frozen.
const scopeArg = argv.find((a) => a.startsWith("--scope="));
const scopeValue = scopeArg ? scopeArg.split("=")[1] : "all";
if (!["desktop", "all", "auto"].includes(scopeValue)) {
  console.error(`[beta-check] Invalid --scope value: ${scopeValue}. Use desktop|all|auto.`);
  process.exit(1);
}

// --- Scope mapping: classify each verify script ---
const DESKTOP_SCRIPTS = [
  "verify:data-integrity",
  "verify:hardening",
  "verify:reposition",
  "verify:inspiration",
  "verify:ai-settings",
  "verify:search-overlay",
  "verify:stats-ui",
  "verify:epub-restore",
  "verify:reader-settings",
  "verify:portable-storage",
  "verify:reading-inspiration",
  "verify:reader-formats",
  "verify:interaction-polish",
  "verify:creation-adapter-spike",
  "verify:creation-workspace",
  "verify:creation-project-shell",
  "verify:creation-editor",
  "verify:creation-migration-audit",
  "verify:creation-outline",
  "verify:creation-cards",
  "verify:creation-history",
  "verify:creation-export",
  "verify:creation-journey",
  "verify:creation-planning",
  "verify:creation-inbox-count",
  "verify:creation-inbox-convert",
  "verify:creation-project-home",
  "verify:creation-migration",
  "verify:creation-search",
  "verify:reader-excerpt",
  "verify:visual-evidence",
  "verify:clean-reposition",
  "verify:visual-polish",
  "verify:ux-polish"
];

// Mobile/Capacitor verify scripts removed (P0-A2, 2026-07-29). mobile/ is frozen.
const MOBILE_SCRIPTS = [];

// Shared/cross-cutting scripts run in both scopes
const SHARED_SCRIPTS = [
  "verify:sync-schema",
  "verify:sync-server",
  "verify:sync-conflicts",
  "verify:release-readiness",
  "verify:installer-release"
];

/**
 * Detect scope from git changed files (staged + unstaged vs HEAD).
 * Returns "desktop", "mobile", or "all" if both are touched.
 */
function detectScopeFromGit() {
  try {
    const output = execSync("git diff --name-only HEAD", { cwd: root, encoding: "utf8" });
    const staged = execSync("git diff --name-only --cached", { cwd: root, encoding: "utf8" });
    const files = [...new Set([...output.split("\n"), ...staged.split("\n")].filter(Boolean))];
    if (files.length === 0) return "all";

    let touchesDesktop = false;
    let touchesMobile = false;

    for (const file of files) {
      if (file.startsWith("android/")) {
        touchesMobile = true;
      } else if (
        file.startsWith("src/") ||
        file.startsWith("electron/") ||
        file === "package.json" ||
        file === "electron.vite.config.ts"
      ) {
        touchesDesktop = true;
      }
    }

    if (touchesDesktop && touchesMobile) return "all";
    if (touchesMobile) return "mobile";
    if (touchesDesktop) return "desktop";
    return "all"; // fallback for docs/ or other shared changes
  } catch {
    console.log("[beta-check] Could not detect git changes, falling back to full scope.");
    return "all";
  }
}

const resolvedScope = scopeValue === "auto" ? detectScopeFromGit() : scopeValue;

function shouldRunScript(scriptName) {
  if (resolvedScope === "all") return true;
  if (SHARED_SCRIPTS.includes(scriptName)) return true;
  if (resolvedScope === "desktop") return DESKTOP_SCRIPTS.includes(scriptName);
  if (resolvedScope === "mobile") return MOBILE_SCRIPTS.includes(scriptName);
  return true;
}

// Scoped runner — delegates to run("npm run <script>") when in scope.
// Self-check anchors (do not remove — verify scripts assert these literals exist):
//   npm run verify:data-integrity
//   npm run verify:hardening
//   npm run verify:stats-ui
//   npm run verify:clean-reposition
//   npm run verify:epub-restore
//   npm run verify:ux-polish
//   npm run verify:creation-adapter-spike
//   npm run verify:creation-workspace
//   npm run verify:creation-project-shell
//   npm run verify:creation-editor
//   npm run verify:creation-migration-audit
function runScoped(scriptName) {
  if (!shouldRunScript(scriptName)) {
    console.log(`\n[beta-check] SKIP (scope=${resolvedScope}): npm run ${scriptName}`);
    return;
  }
  run(`npm run ${scriptName}`);
}
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
  "diagnostics:exportDebugInfo",
  "creation:listProjects",
  "creation:readProjectNavigation",
  "creation:createProject",
  "creation:readSceneBody",
  "creation:updateSceneBody",
  "creation:readProjectOutline",
  "creation:runStructure",
  "creation:cardsList",
  "creation:cardRead",
  "creation:cardTypesList",
  "creation:relationTypesList",
  "creation:cardRelations",
  "creation:trashList",
  "creation:snapshotList",
  "creation:exportDraft",
  "creation:search",
  "creation:replacePreview",
  "creation:replaceApply",
  "creation:statsView",
  "creation:sessionList",
  "creation:sessionReport",
  "creation:sessionDelete",
  "creation:proofQuery",
  "creation:migrationStatus",
  "creation:migrationRun",
  "creation:inboxList",
  "creation:inboxUpdate",
  "creation:inboxDelete",
  "creation:importDraftPreview",
  "creation:exportProjectBundle",
  "creation:importProjectBundle",
  "creation:annotationList",
  "creation:annotationCreate",
  "creation:annotationUpdate",
  "creation:annotationDelete",
  "creation:resourceList",
  "creation:attachResource",
  "creation:detachResource",
  "creation:readProjectExport",
  "creation:watchProject",
  "creation:unwatchProject"
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
  const main = `${readText("electron/main/index.ts")}\n${readText("electron/main/creation-ipc.ts")}`;
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
    "src/features/creation/home/ProjectHomePage.tsx",
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

/**
 * 运行完整 Vitest 套件并可靠解析结果。
 *
 * 通过 package script 实际执行 `npm test`，并用 Vitest JSON reporter 输出到临时文件：
 * - JSON 缺失/解析失败 → Beta 直接失败（绝不假绿）；
 * - 退出码非 0 → 失败；
 * - failedTests > 0 → 失败；
 * - skipped 从 JSON 动态读取（numPendingTests），不进入 passed；
 * - skip 原因从 testResults 提取（或由 ABI 探测说明）；
 * - ABI 信息动态生成（process.version / process.versions.modules / Electron 版本 / 真实 native 加载结果）。
 */
function runVitest() {
  logStep("Running full Vitest suite (npm test)");
  if (skipVitest) {
    console.log(`\n[beta-check] WARNING: --skip-vitest 已启用，本轮跳过完整 Vitest 执行。此模式仅供调试，不得用于正式 Desktop Beta 报告。`);
    return;
  }
  const outputDir = mkdtempSync(path.join(os.tmpdir(), "beta-vitest-"));
  const outputFile = path.join(outputDir, "vitest.json");
  try {
    const result = spawnSync("npm", ["test", "--", "--reporter=json", `--outputFile=${outputFile}`], {
      cwd: root,
      shell: true,
      encoding: "utf8",
      env: { ...process.env },
      maxBuffer: 50 * 1024 * 1024,
      stdio: "pipe"
    });
    if (!existsSync(outputFile)) {
      fail(`Vitest JSON 结果文件缺失：${outputFile}（stdout 末尾：${String(result.stdout ?? "").slice(-400)}）`);
    }
    let json;
    try {
      json = JSON.parse(readFileSync(outputFile, "utf8"));
    } catch (error) {
      fail(`Vitest JSON 解析失败（文件可能截断或未生成）：${error instanceof Error ? error.message : String(error)}`);
    }
    const jsonValid = validateVitestJson(json);
    const summary = summarizeVitestJson(json);
    const { failed, skipped } = extractTestDetails(json);
    const abi = detectAbi();
    const skippedReasons =
      skipped.length > 0
        ? skipped.map((title) => `skipped: ${title}`)
        : abi.nativeLoad === "failed"
          ? [`better-sqlite3 加载失败：${abi.nativeError ?? "未知"}`]
          : [];

    if (result.status !== 0) {
      console.log(String(result.stdout ?? ""));
      console.log(String(result.stderr ?? ""));
      fail(`Vitest 退出码非 0（exit ${result.status}）。`);
    }

    const verdict = evaluateVitestRun({ summary, abi, skippedReasons, jsonValid });
    console.log(`\n[beta-check] Vitest 结果（来自 JSON reporter）：`);
    for (const note of verdict.notes) console.log(`  ${note}`);
    if (jsonValid.ok && failed.length > 0) {
      console.log(`  失败测试：`);
      for (const title of failed.slice(0, 20)) console.log(`    - ${title}`);
    }
    if (!verdict.ok) {
      console.log(String(result.stdout ?? ""));
      fail(`Vitest 校验失败：${jsonValid.ok ? "存在失败测试" : "Reporter 数据无效"}。`);
    }
  } finally {
    rmSync(outputDir, { recursive: true, force: true });
  }
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

if (resolvedScope !== "all") {
  logStep(`Running in scoped mode: ${resolvedScope} (from --scope=${scopeValue})`);
}

{
  const pkg = JSON.parse(readText("package.json"));
  const definedScripts = new Set(Object.keys(pkg.scripts ?? {}));
  const allScopedLists = new Set([...DESKTOP_SCRIPTS, ...MOBILE_SCRIPTS, ...SHARED_SCRIPTS]);
  const missingFromPackage = [...allScopedLists].filter((name) => !definedScripts.has(name));
  if (missingFromPackage.length > 0) {
    fail(`Scope scripts missing from package.json scripts:\n${missingFromPackage.map((name) => `  - npm run ${name}`).join("\n")}`);
  }
  const selfSource = readText("scripts/beta-check.mjs");
  const runScopedCalls = [...selfSource.matchAll(/runScoped\(["']([^"']+)["']\)/g)].map((match) => match[1]);
  const unregistered = runScopedCalls.filter((name) => !allScopedLists.has(name));
  if (unregistered.length > 0) {
    fail(`runScoped scripts not in any scope list (DESKTOP_SCRIPTS / MOBILE_SCRIPTS / SHARED_SCRIPTS):\n${unregistered.map((name) => `  - ${name}`).join("\n")}`);
  }
}

assertPackageMetadata();
assertReadableUtf8();
if (resolvedScope === "all" || resolvedScope === "desktop") {
  assertRendererSecurity();
  assertIpcSurface();
} else {
  console.log(`\n[beta-check] SKIP (scope=${resolvedScope}): renderer security & IPC checks`);
}
runScoped("verify:data-integrity");
runScoped("verify:hardening");
runScoped("verify:reposition");
runScoped("verify:inspiration");
runScoped("verify:ai-settings");
runScoped("verify:search-overlay");
runScoped("verify:stats-ui");
runScoped("verify:epub-restore");
runScoped("verify:reader-settings");
runScoped("verify:portable-storage");
runScoped("verify:reading-inspiration");
runScoped("verify:reader-formats");
runScoped("verify:interaction-polish");
runScoped("verify:creation-adapter-spike");
runScoped("verify:creation-workspace");
runScoped("verify:creation-project-shell");
runScoped("verify:creation-editor");
runScoped("verify:creation-migration-audit");
runScoped("verify:creation-outline");
runScoped("verify:creation-cards");
runScoped("verify:creation-history");
runScoped("verify:creation-export");
runScoped("verify:creation-journey");
runScoped("verify:creation-planning");
runScoped("verify:creation-inbox-count");
runScoped("verify:creation-inbox-convert");
runScoped("verify:creation-project-home");
runScoped("verify:creation-migration");
runScoped("verify:creation-search");
runScoped("verify:reader-excerpt");
runScoped("verify:visual-evidence");
runScoped("verify:clean-reposition");
runScoped("verify:sync-schema");
runScoped("verify:sync-server");
runScoped("verify:sync-conflicts");
runScoped("verify:release-readiness");
runScoped("verify:installer-release");
runScoped("verify:visual-polish");
runScoped("verify:ux-polish");
// 全量 Vitest：Desktop Beta 必须实际运行完整 Vitest（通过 npm test + JSON reporter 可靠解析）。
// skip 的测试（better-sqlite3 ABI 不匹配等）从 JSON 动态读取，不进入 passed。
runVitest();
if (!skipBuild) run("npm run build");
if (!skipAudit) run("npm audit --omit=dev");
assertArtifactIfRequested();

console.log(`\n[beta-check] All checks passed (scope=${resolvedScope}).`);
