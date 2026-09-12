import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) {
    throw new Error(`[verify-portable-storage] ${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
  }
}

assertIncludes("electron/main/index.ts", "portableDataRoot", "Main process must prefer an install-adjacent portable data directory.");
assertIncludes("electron/main/index.ts", "isDirectoryWritable", "Main process must verify install/data directory writability before use.");
assertIncludes("electron/main/index.ts", "settings:chooseDataDirectory", "Main process must expose data directory picker IPC.");
assertIncludes("electron/main/index.ts", "settings:chooseLibraryDirectory", "Main process must expose library directory picker IPC.");
assertIncludes("electron/main/index.ts", "settings:migrateDataDirectory", "Main process must expose data directory migration IPC.");
assertIncludes("electron/main/index.ts", "settings:migrateLibraryDirectory", "Main process must expose library directory migration IPC.");
assertIncludes("electron/main/index.ts", "storage:getLocations", "Main process must expose current storage locations.");
assertIncludes("src/types/api.ts", "chooseDataDirectory", "Renderer typed API must include data directory picker.");
assertIncludes("src/types/api.ts", "chooseLibraryDirectory", "Renderer typed API must include library directory picker.");
assertIncludes("src/services/settings-service.ts", "migrateDataDirectory", "Settings service must wrap data directory migration.");
assertIncludes("src/features/settings/sections/StorageSection.tsx", "选择数据目录", "Settings UI must let users choose a data directory.");
assertIncludes("src/features/settings/sections/StorageSection.tsx", "选择书籍目录", "Settings UI must let users choose a library directory.");
assertIncludes("src/features/settings/sections/StorageSection.tsx", "旧目录不会自动删除", "Settings UI must explain migration is copy-only.");

console.log("[verify-portable-storage] Portable storage guards verified.");
