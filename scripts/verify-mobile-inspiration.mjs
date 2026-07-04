import { readFileSync } from "node:fs";

function read(path) {
  return readFileSync(path, "utf-8");
}

function fail(message) {
  console.error(`[verify-mobile-inspiration] ${message}`);
  process.exit(1);
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) fail(`${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
}

assertIncludes("mobile/src/App.tsx", "InspirationPage", "Mobile app must expose inspiration as a first-class page.");
assertIncludes("mobile/src/App.tsx", "快速记录", "Inspiration page must support quick capture.");
assertIncludes("mobile/src/App.tsx", "来源摘录", "Inspiration page must show source excerpts.");
assertIncludes("mobile/src/App.tsx", "compact-inspiration-card", "Inspiration list must use compact cards for high-volume idea browsing.");
assertIncludes("mobile/src/App.tsx", "selectedItem", "Inspiration cards must open a detail view before editing or AI actions.");
assertIncludes("mobile/src/App.tsx", "inspiration-ai-panel", "AI variants and actions must live in the inspiration detail panel.");
assertIncludes("mobile/src/App.tsx", "AI 打磨", "Inspiration detail must expose AI polishing actions.");
assertIncludes("mobile/src/App.tsx", "个候选", "Inspiration detail must show AI candidate count.");
assertIncludes("mobile/src/App.tsx", "按书籍", "Inspiration page must filter or group by book.");
assertIncludes("mobile/src/App.tsx", "parseTagInput", "Inspiration editor must allow users to type and save tags.");
assertIncludes("mobile/src/App.tsx", "未命名灵感", "New inspirations must not blindly use the first body line as the title.");
assertIncludes("mobile/src/App.tsx", "setEditTags", "Inspiration detail editing must include editable tags.");
assertIncludes("mobile/src/App.tsx", "lastSavedInspirationId", "Reader-to-inspiration flow must remember the saved inspiration for feedback navigation.");
assertIncludes("mobile/src/App.tsx", "createdFrom: sourceExcerpt ? \"reader-selection\" : \"reader-note\"", "Reader inspiration source must distinguish selected text from a general reading note.");
assertIncludes("mobile/src/services/mobile-storage.ts", "addMobileInspiration", "Mobile storage must support adding inspirations.");
assertIncludes("mobile/src/types/mobile.ts", "MobileInspiration", "Mobile types must define MobileInspiration.");
assertIncludes("mobile/src/types/mobile.ts", "MobileNote", "Mobile types must define MobileNote for reading notes.");

const appSource = read("mobile/src/App.tsx");
const detailIndex = appSource.indexOf("inspiration-ai-panel");
const listIndex = appSource.indexOf("compact-inspiration-card");
if (detailIndex < 0 || listIndex < 0 || detailIndex > listIndex) {
  fail("AI panel should appear in the detail branch before the compact list branch, keeping list cards lightweight.");
}

console.log("[verify-mobile-inspiration] Mobile inspiration guards verified.");
