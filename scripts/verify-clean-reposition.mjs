import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

function fail(message) {
  console.error(`[verify-clean-reposition] ${message}`);
  process.exit(1);
}

function walk(directory, results = []) {
  if (!existsSync(directory)) return results;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(fullPath, results);
    else results.push(fullPath);
  }
  return results;
}

const offenders = [];
const sourceFiles = walk(path.join(root, "src")).filter((file) => /\.(ts|tsx)$/.test(file));
const app = read("src/app/App.tsx");
const store = read("src/stores/app-store.ts");
const preload = read("electron/preload/index.ts");
const api = read("src/types/api.ts");
const betaCheck = read("scripts/beta-check.mjs");
const packageJson = read("package.json");
const indexHtml = read("index.html");

for (const snippet of ["legacy-workbench", "WorkbenchPage", "旧项目 / 资料库", "旧导出", "ExportProjectDialog"]) {
  for (const [label, source] of [
    ["src/app/App.tsx", app],
    ["src/stores/app-store.ts", store],
    ["electron/preload/index.ts", preload],
    ["src/types/api.ts", api],
    ["scripts/beta-check.mjs", betaCheck]
  ]) {
    if (source.includes(snippet)) offenders.push(`${label} still contains ${snippet}`);
  }
}

for (const removedPath of [
  "src/pages/WorkbenchPage.tsx",
  "src/features/ai/AiPage.tsx",
  "src/features/project",
  "src/features/export",
  "src/hooks/useProjectActions.ts",
  "src/hooks/useChapterActions.ts",
  "src/hooks/useCardActions.ts",
  "src/hooks/useAutosaveChapter.ts",
  "src/hooks/useAutosaveCard.ts",
  "src/hooks/useExportActions.ts",
  "src/services/project-service.ts",
  "src/services/chapter-service.ts",
  "src/services/card-service.ts",
  "src/services/volume-service.ts",
  "src/services/export-service.ts",
  "src/types/project.ts",
  "src/types/export.ts",
  "src/utils/project-tree.ts"
]) {
  if (existsSync(path.join(root, removedPath))) offenders.push(`${removedPath} should be removed.`);
}

for (const file of sourceFiles) {
  const relativePath = path.relative(root, file).replaceAll(path.sep, "/");
  const source = readFileSync(file, "utf8");
  if (/@\/(?:features\/project|features\/export|services\/project-service|services\/chapter-service|services\/card-service|services\/volume-service|services\/export-service|utils\/project-tree)/.test(source)) {
    offenders.push(`${relativePath} imports removed legacy writing code.`);
  }
}

for (const snippet of [
  "project:create",
  "project:open",
  "chapter:write",
  "volume:create",
  "export:projectMarkdown",
  "export:projectTxt"
]) {
  if (preload.includes(snippet)) offenders.push(`preload should not expose ${snippet}.`);
  if (api.includes(snippet)) offenders.push(`renderer api types should not expose ${snippet}.`);
  if (betaCheck.includes(snippet)) offenders.push(`beta check should not require ${snippet}.`);
}

if (!packageJson.includes("verify:clean-reposition")) offenders.push("package.json should expose verify:clean-reposition.");
if (!betaCheck.includes("npm run verify:clean-reposition")) offenders.push("verify:beta should include verify:clean-reposition.");
if (indexHtml.includes("小说作者工作台")) offenders.push("index.html title should not use the old writing-workbench product name.");
if (!indexHtml.includes("<title>创作阅读助手</title>")) offenders.push("index.html title should be 创作阅读助手.");

if (offenders.length > 0) {
  fail(`Old writing workbench cleanup guard failed:\n${offenders.map((item) => `  - ${item}`).join("\n")}`);
}

console.log("[verify-clean-reposition] Old writing workbench cleanup guards verified.");

