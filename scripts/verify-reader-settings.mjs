import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) {
    throw new Error(`[verify-reader-settings] ${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
  }
}

assertIncludes("src/types/library.ts", "appTheme", "ReaderSettings must split application theme from book background.");
assertIncludes("src/types/library.ts", "readerBackground", "ReaderSettings must expose book background choices.");
assertIncludes("src/types/library.ts", "epubStyleMode", "ReaderSettings must let EPUB keep publisher styles by default.");
assertIncludes("src/types/settings.ts", "dataDirectory", "StorageSettings must include configurable data directory.");
assertIncludes("src/types/settings.ts", "storageMode", "StorageSettings must record portable/custom/fallback mode.");
assertIncludes("electron/main/index.ts", "settings:resetReaderSettings", "Main process must expose a dedicated reader reset IPC.");
assertIncludes("electron/preload/index.ts", "resetReaderSettings", "Preload API must expose reader reset without renderer internals.");
assertIncludes("src/features/settings/SettingsPage.tsx", "书籍背景", "Settings page must name book background separately from app theme.");
assertIncludes("src/features/settings/SettingsPage.tsx", "恢复阅读默认", "Settings page must include a reader-default reset action.");
assertIncludes("src/features/settings/SettingsPage.tsx", "高级阅读记录", "Tracking settings must be grouped as advanced reading records.");
assertIncludes("src/features/settings/SettingsPage.tsx", "多久没有翻页", "Idle pause setting must explain what it means in user language.");
assertIncludes("src/features/library/ReaderSettingsPanel.tsx", "恢复默认", "Inline reader settings must include a reset button.");
assertIncludes("src/features/library/ReaderSettingsPanel.tsx", "白纸", "Reader background presets must include white paper.");
assertIncludes("src/features/library/ReaderSettingsPanel.tsx", "护眼", "Reader background presets must include eye-care background.");

console.log("[verify-reader-settings] Reader settings and theme guards verified.");
