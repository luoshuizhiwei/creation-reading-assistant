import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { readMainProcess } from "./lib/main-process-sources.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

const files = {
  types: read("src/types/ai.ts"),
  settings: read("src/types/settings.ts"),
  api: read("src/types/api.ts"),
  preload: read("electron/preload/index.ts"),
  // AI 密钥与设置实现已按职责拆到 settings-store.ts，断言需覆盖整个主进程模块集合。
  main: readMainProcess(),
  settingsPage: read("src/features/settings/sections/AISection.tsx"),
  // 设置页和收件箱已拆成容器 + 子组件；静态门禁跟随真正承载文案/功能的叶组件。
  inboxPage: read("src/features/creation/inbox/InboxPage.tsx"),
  inboxDetail: read("src/features/creation/inbox/components/InboxItemDetail.tsx"),
  service: read("src/services/ai-service.ts")
};

const requiredSnippets = [
  [files.types, "export interface AISettings"],
  [files.types, "hasApiKey"],
  [files.types, "AIRunAction"],
  [files.settings, "ai: AISettings"],
  [files.api, "ai: {"],
  [files.preload, "ai:saveApiKey"],
  [files.main, "safeStorage"],
  [files.main, "aiSecretsPath"],
  [files.main, 'ipcMain.handle("ai:run"'],
  [files.settingsPage, "AI 助手"],
  [files.settingsPage, "API Key"],
  [files.inboxDetail, "AI 候选版本"],
  [files.inboxPage, "runAIAction"],
  [files.service, "runAIAction"]
];

const missing = requiredSnippets.filter(([content, snippet]) => !content.includes(snippet)).map(([, snippet]) => snippet);
const leakedSecretShape = files.api.includes("apiKey: string") || files.preload.includes("apiKey: string");
const secretUsesBackedUpWriter = files.main.includes("writeJson(aiSecretsPath()");

if (missing.length > 0 || leakedSecretShape || secretUsesBackedUpWriter) {
  console.error("[verify-ai-settings] Missing AI settings requirements:");
  for (const item of missing) console.error(`  - ${item}`);
  if (leakedSecretShape) console.error("  - renderer/preload type surface appears to expose API key as a settings field");
  if (secretUsesBackedUpWriter) console.error("  - AI secret storage must not use writeJson because it creates .bak copies");
  process.exit(1);
}

console.log("[verify-ai-settings] AI settings and secret boundary verified.");
