import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

const files = {
  app: read("src/app/App.tsx"),
  store: read("src/stores/app-store.ts"),
  home: read("src/features/creation/home/ProjectHomePage.tsx"),
  api: read("src/types/api.ts"),
  preload: read("electron/preload/index.ts"),
  main: read("electron/main/index.ts"),
  packageJson: read("package.json")
};

const checks = [
  ["AppScreen includes inspiration", files.store.includes('"inspiration"')],
  ["AppScreen does not include standalone ai screen", !files.store.includes('"ai"')],
  ["AppScreen includes library", files.store.includes('"library"')],
  ["AppScreen does not include legacy-workbench", !files.store.includes('"legacy-workbench"')],
  ["App renders InspirationPage", files.app.includes("InspirationPage")],
  ["App does not render standalone AiPage", !files.app.includes("AiPage")],
  ["App does not render legacy WorkbenchPage", !files.app.includes("WorkbenchPage")],
  ["App renders project home page", files.app.includes("CreationProjectsPage")],
  ["Project home leads with recent projects and pending inbox", files.home.includes("最近项目") && files.home.includes("待处理")],
  ["Project home exposes global search", files.home.includes("全局搜索")],
  ["Project home leads with creation focus (continue writing)", files.home.includes("继续写作")],
  ["DesktopApi exposes inspiration namespace", files.api.includes("inspiration: {")],
  ["DesktopApi exposes ai namespace", files.api.includes("ai: {")],
  ["DesktopApi does not expose old project/export namespaces", !/\b(project|chapter|idea|character|world|volume|export):\s*\{/.test(files.api)],
  ["Preload exposes inspiration IPC", files.preload.includes("inspiration:list")],
  ["Preload exposes ai IPC", files.preload.includes("ai:getSettings")],
  ["Preload does not expose old project/export IPC", !/(project:create|chapter:write|export:projectMarkdown)/.test(files.preload)],
  ["Main registers inspiration IPC", files.main.includes('ipcMain.handle("inspiration:list"')],
  ["Main registers ai IPC", files.main.includes('ipcMain.handle("ai:getSettings"')],
  ["Main does not register old project/export IPC", !/(ipcMain\.handle\("project:create"|ipcMain\.handle\("chapter:write"|ipcMain\.handle\("export:projectMarkdown")/.test(files.main)],
  ["Renderer/preload do not mention ai-secrets storage path", !files.preload.includes("ai-secrets") && !files.api.includes("ai-secrets")],
  ["Package productName is repositioned", files.packageJson.includes('"productName": "创作阅读助手"')]
];

const missing = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (missing.length > 0) {
  console.error("[verify-reposition] Missing repositioning requirements:");
  for (const item of missing) console.error(`  - ${item}`);
  process.exit(1);
}

console.log("[verify-reposition] Product repositioning surface verified.");

