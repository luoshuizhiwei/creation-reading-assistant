import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

function read(relativePath) {
  return readFileSync(path.join(root, relativePath), "utf8");
}

const files = {
  types: read("src/types/inspiration.ts"),
  api: read("src/types/api.ts"),
  preload: read("electron/preload/index.ts"),
  main: read("electron/main/index.ts"),
  page: read("src/features/inspiration/InspirationPage.tsx"),
  service: read("src/services/inspiration-service.ts"),
  store: read("src/stores/inspiration-store.ts")
};

const requiredSnippets = [
  [files.types, "export interface InspirationItem"],
  [files.types, "export interface InspirationVariant"],
  [files.types, "sourceBookId"],
  [files.types, "sourceLocation"],
  [files.api, "inspiration: {"],
  [files.preload, "inspiration:create"],
  [files.main, "inspirationsPath"],
  [files.main, 'ipcMain.handle("inspiration:addVariant"'],
  [files.page, "灵感中心"],
  [files.page, "平台标签"],
  [files.service, "createInspiration"],
  [files.store, "selectedId"]
];

const missing = requiredSnippets.filter(([content, snippet]) => !content.includes(snippet)).map(([, snippet]) => snippet);
if (missing.length > 0) {
  console.error("[verify-inspiration] Missing inspiration requirements:");
  for (const item of missing) console.error(`  - ${item}`);
  process.exit(1);
}

console.log("[verify-inspiration] Inspiration MVP surface verified.");
