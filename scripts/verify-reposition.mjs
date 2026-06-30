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
  start: read("src/pages/StartPage.tsx"),
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
  ["App renders InspirationPage", files.app.includes("<InspirationPage />")],
  ["App does not render standalone AiPage", !files.app.includes("AiPage")],
  ["App does not render legacy WorkbenchPage", !files.app.includes("WorkbenchPage")],
  ["Start page leads with inspiration and library", files.start.includes("灵感中心") && files.start.includes("本地书库") && !files.start.includes('screen: "ai"')],
  ["Start page exposes visible search", files.start.includes("首页搜索") && files.start.includes("搜索书名、作者、灵感、阅读来源")],
  ["Start page explains new product focus", files.start.includes("沉淀灵感") && files.start.includes("阅读本地小说取材")],
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

