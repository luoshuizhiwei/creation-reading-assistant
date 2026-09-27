import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readMainProcess } from "./lib/main-process-sources.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-hardening] ${message}`);
  process.exit(1);
}

const missing = [];

if (!existsSync(path.join(root, ".gitignore"))) {
  missing.push("Repository should include a .gitignore for generated artifacts.");
} else {
  const gitignore = read(".gitignore");
  for (const snippet of ["node_modules/", "out/", "release*/", "visual-qa-sample/", "*.log"]) {
    if (!gitignore.includes(snippet)) missing.push(`.gitignore should include ${snippet}`);
  }
}

const indexHtml = read("index.html");
if (!indexHtml.includes("Content-Security-Policy")) {
  missing.push("Renderer index.html should define a Content Security Policy.");
}

const main = readMainProcess();
for (const snippet of [
  "session.defaultSession.webRequest.onHeadersReceived",
  "function contentSecurityPolicy",
  "app.requestSingleInstanceLock()",
  "mainWindow.focus()",
  "MAX_SEARCH_TEXT_FILE_BYTES",
  "readTextFileIfWithinLimit",
  "readJson backup parse failed",
  "process.exit(1)"
]) {
  if (!main.includes(snippet)) missing.push(`main process sources should include ${snippet}`);
}

const betaCheck = read("scripts/beta-check.mjs");
for (const snippet of ["npm run verify:hardening", "npm run verify:reader-formats"]) {
  if (!betaCheck.includes(snippet)) missing.push(`verify:beta should run ${snippet}.`);
}

if (!existsSync(path.join(root, "tsconfig.main.json"))) {
  missing.push("Main process should have tsconfig.main.json.");
}
if (!existsSync(path.join(root, "tsconfig.renderer.json"))) {
  missing.push("Renderer should have tsconfig.renderer.json.");
}
if (!existsSync(path.join(root, "tsconfig.node.json"))) {
  missing.push("Node tooling should have tsconfig.node.json.");
}

const tsconfig = read("tsconfig.json");
if (!tsconfig.includes('"references"') || !tsconfig.includes("tsconfig.renderer.json")) {
  missing.push("Root tsconfig should reference split configs.");
}

if (missing.length > 0) {
  fail(`Hardening guard failed:\n${missing.map((item) => `  - ${item}`).join("\n")}`);
}

console.log("[verify-hardening] Hardening guards verified.");
