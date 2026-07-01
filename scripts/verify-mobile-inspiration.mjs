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
assertIncludes("mobile/src/App.tsx", "AI 候选", "Inspiration page must show AI variants.");
assertIncludes("mobile/src/App.tsx", "按书籍", "Inspiration page must filter or group by book.");
assertIncludes("mobile/src/App.tsx", "lastSavedInspirationId", "Reader-to-inspiration flow must remember the saved inspiration for feedback navigation.");
assertIncludes("mobile/src/App.tsx", "createdFrom: sourceExcerpt ? \"reader-selection\" : \"reader-note\"", "Reader inspiration source must distinguish selected text from a general reading note.");
assertIncludes("mobile/src/services/mobile-storage.ts", "addMobileInspiration", "Mobile storage must support adding inspirations.");
assertIncludes("mobile/src/types/mobile.ts", "MobileInspiration", "Mobile types must define MobileInspiration.");
assertIncludes("mobile/src/types/mobile.ts", "MobileNote", "Mobile types must define MobileNote for reading notes.");

console.log("[verify-mobile-inspiration] Mobile inspiration guards verified.");
