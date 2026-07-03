import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-ai] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

const app = "mobile/src/App.tsx";
const ai = "mobile/src/services/mobile-ai.ts";
const secret = "mobile/src/services/mobile-secret-store.ts";
const storage = "mobile/src/services/mobile-storage.ts";
const sync = "mobile/src/services/sync-client.ts";
const webdav = "mobile/src/sync/webdav-sync.ts";
const css = "mobile/src/styles.css";

assertIncludes(ai, "runMobileAIAction", "Mobile must implement a real OpenAI-compatible AI request path.");
assertIncludes(ai, "/chat/completions", "Mobile AI must use OpenAI-compatible chat completions.");
assertIncludes(secret, "MOBILE_SECRET_DB_NAME", "Mobile secrets must be isolated in a dedicated secret store.");
assertIncludes(secret, "indexedDB.open(MOBILE_SECRET_DB_NAME", "Mobile secrets must use IndexedDB instead of localStorage plaintext.");
assertIncludes(ai, "writeMobileSecret(AI_SECRET_RECORD_ID, apiKey)", "Mobile AI key must be saved through the secret store.");
assertIncludes(ai, "readMobileSecret(AI_SECRET_RECORD_ID)", "Mobile AI key must be read through the secret store.");
assertIncludes(ai, "LEGACY_AI_API_KEY_KEY", "Mobile AI key storage must migrate old localStorage keys safely.");
assertIncludes(ai, "clearMobileAIApiKey", "Mobile AI settings must allow clearing the local key.");

if (/localStorage\.setItem\(\s*LEGACY_AI_API_KEY_KEY/.test(read(ai))) {
  fail("Mobile AI API key must not be written to legacy localStorage plaintext.");
}

assertIncludes(storage, "addMobileInspirationVariant", "Mobile storage must be able to save AI variants.");
assertIncludes(storage, "variants: [variant, ...current.variants]", "AI output must be appended as a variant instead of overwriting inspiration body.");

for (const marker of [
  "runInspirationAI",
  "runMobileAIAction",
  "addMobileInspirationVariant",
  "copyInspirationVariant",
  "adoptInspirationVariant",
  "润色",
  "扩写",
  "平台风格化",
  "生成冲突",
  "去 AI 味",
  "复制候选",
  "采用为正文",
  "候选记录仍保留",
  "原文没有被覆盖",
  "测试",
  "清除 Key"
]) {
  assertIncludes(app, marker, `Mobile inspiration/AI UI must include ${marker}.`);
}

for (const selector of [".ai-action-row", ".ai-variant-card", ".ai-variant-actions", ".range-setting-row"]) {
  assertIncludes(css, selector, `Mobile AI UI must include ${selector}.`);
}

const syncText = read(sync);
const webdavText = read(webdav);
if (/AI_API_KEY_KEY|creation-reading-assistant-mobile-ai-api-key/i.test(syncText + webdavText)) {
  fail("Mobile AI API key must not be referenced by LAN sync or WebDAV sync.");
}

console.log("[verify-mobile-ai] Mobile AI workflow guards verified.");
