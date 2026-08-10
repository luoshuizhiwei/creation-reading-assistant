import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

function fail(message) {
  console.error(`[verify-creation-adapter-spike] ${message}`);
  process.exit(1);
}

function requireFile(relativePath) {
  const absolutePath = path.join(root, relativePath);
  if (!existsSync(absolutePath)) fail(`Missing required spike artifact: ${relativePath}`);
  return absolutePath;
}

function parseEvidence(stdout, label) {
  const lines = stdout.trim().split(/\r?\n/).filter(Boolean);
  try {
    return JSON.parse(lines.at(-1));
  } catch {
    fail(`${label} did not emit valid JSON evidence.\n${stdout}`);
  }
}

const packageJson = JSON.parse(readFileSync(path.join(root, "package.json"), "utf8"));
const requiredDependencies = {
  "better-sqlite3": "12.4.1",
  "@tiptap/core": "3.29.2",
  "@tiptap/pm": "3.29.2",
  "@tiptap/react": "3.29.2"
};

for (const [name, version] of Object.entries(requiredDependencies)) {
  if (packageJson.dependencies?.[name] !== version) {
    fail(`Expected dependency ${name}@${version}.`);
  }
}

if (packageJson.devDependencies?.electron !== "33.4.11") {
  fail("Expected exact Electron runtime electron@33.4.11.");
}

if (packageJson.scripts?.["verify:creation-adapter-spike"] !== "node scripts/verify-creation-adapter-spike.mjs") {
  fail("package.json must expose verify:creation-adapter-spike.");
}

if (packageJson.scripts?.postinstall !== "electron-builder install-app-deps") {
  fail("Native dependencies must be rebuilt for the pinned Electron runtime after install.");
}

const asarUnpack = packageJson.build?.asarUnpack;
if (!Array.isArray(asarUnpack) || !asarUnpack.includes("node_modules/better-sqlite3/**")) {
  fail("better-sqlite3 native files must be explicitly unpacked from ASAR.");
}

const electronBinary = requireFile("node_modules/electron/dist/electron.exe");
const sqliteSpike = requireFile("scripts/spikes/creation-sqlite-spike.cjs");
const editorSpike = requireFile("scripts/spikes/creation-editor-spike.mjs");
const runtimeSpike = requireFile("scripts/spikes/electron-runtime-info.cjs");
requireFile("scripts/spikes/electron-native-module-info.cjs");
const decisionDocument = requireFile("docs/architecture/desktop-creation-adapter-decision.md");

const betaCheck = readFileSync(path.join(root, "scripts/beta-check.mjs"), "utf8");
if (!betaCheck.includes('runScoped("verify:creation-adapter-spike")')) {
  fail("verify:beta must run verify:creation-adapter-spike for desktop scope.");
}

const runtime = spawnSync(electronBinary, [runtimeSpike], {
  cwd: root,
  encoding: "utf8",
  timeout: 30_000,
  windowsHide: true
});
if (runtime.status !== 0) {
  fail(`Electron runtime probe failed.\n${runtime.stdout}\n${runtime.stderr}`);
}
const runtimeEvidence = parseEvidence(runtime.stdout, "Electron runtime probe");
if (
  runtimeEvidence.electron !== "33.4.11" ||
  runtimeEvidence.node !== "20.18.3" ||
  runtimeEvidence.modules !== "130"
) {
  fail(`Unexpected Electron runtime evidence: ${JSON.stringify(runtimeEvidence)}`);
}

const sqlite = spawnSync(electronBinary, [sqliteSpike], {
  cwd: root,
  encoding: "utf8",
  timeout: 120_000,
  windowsHide: true
});
if (sqlite.status !== 0) {
  fail(`SQLite Electron spike failed.\n${sqlite.stdout}\n${sqlite.stderr}`);
}
const sqliteEvidence = parseEvidence(sqlite.stdout, "SQLite Electron spike");
if (
  sqliteEvidence.integrity !== "ok" ||
  sqliteEvidence.journalMode !== "wal" ||
  sqliteEvidence.foreignKeysEnabled !== true ||
  sqliteEvidence.rollbackVerified !== true ||
  sqliteEvidence.rollbackFtsClean !== true ||
  sqliteEvidence.ftsVerified !== true ||
  sqliteEvidence.counts?.scenes !== 2000 ||
  sqliteEvidence.counts?.cards !== 10000 ||
  sqliteEvidence.counts?.relations !== 20000
) {
  fail(`SQLite spike evidence is incomplete: ${JSON.stringify(sqliteEvidence)}`);
}

const editor = spawnSync(process.execPath, [editorSpike], {
  cwd: root,
  encoding: "utf8",
  timeout: 30_000,
  windowsHide: true
});
if (editor.status !== 0) {
  fail(`Editor schema spike failed.\n${editor.stdout}\n${editor.stderr}`);
}
const editorEvidence = parseEvidence(editor.stdout, "Editor schema spike");
if (
  editorEvidence.roundTrip !== true ||
  editorEvidence.unsupportedContentRejected !== true ||
  editorEvidence.chineseTextPreserved !== true ||
  editorEvidence.sceneBreakAtom !== true ||
  editorEvidence.schemaIsStrict !== true
) {
  fail(`Editor spike evidence is incomplete: ${JSON.stringify(editorEvidence)}`);
}

const decision = readFileSync(decisionDocument, "utf8");
for (const requiredText of [
  "better-sqlite3 12.4.1",
  "Tiptap 3.29.2",
  "Electron 33.4.11",
  "Node 20.18.3",
  "ABI 130",
  "不进入业务实现"
]) {
  if (!decision.includes(requiredText)) fail(`Decision document missing: ${requiredText}`);
}

console.log("[verify-creation-adapter-spike] Storage and editor adapter evidence verified.");
