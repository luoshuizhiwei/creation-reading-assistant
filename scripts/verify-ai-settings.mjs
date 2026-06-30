import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

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
  main: read("electron/main/index.ts"),
  settingsPage: read("src/features/settings/SettingsPage.tsx"),
  inspirationPage: read("src/features/inspiration/InspirationPage.tsx"),
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
  [files.inspirationPage, "AI 候选版本"],
  [files.inspirationPage, "runAIAction"],
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
