import { readFileSync } from "node:fs";

/**
 * 阶段 7（资料摘录目的地接线）后改造的契约：
 * - 阅读端不再有"查看灵感"主入口；摘录走 ExcerptPicker，目的地为
 *   全局收件箱或项目资料卡，记为灵感流程已退役。
 * - 来源快照 ExcerptSourceSnapshot 必须保留 bookTitle / locationLabel /
 *   excerpt，便于迁移到收件箱 InboxCreateCommand.source 或卡片 content。
 * - InspirationPage 已降级为兼容跳转，不再承担灵感管理主入口职责，
 *   契约改为校验兼容跳转文本与迁移说明。
 */
function read(path) {
  return readFileSync(path, "utf-8");
}

function assertIncludes(file, needle, message) {
  const content = read(file);
  if (!content.includes(needle)) {
    throw new Error(`[verify-reading-inspiration] ${message}\nMissing ${JSON.stringify(needle)} in ${file}`);
  }
}

// 来源快照结构（阶段 5/7 的核心数据契约）。
assertIncludes("src/types/library.ts", "ExcerptSourceSnapshot", "Reader excerpt must define a structured source snapshot type.");
assertIncludes("src/types/library.ts", "bookTitle", "Source snapshot must keep a book title even if the book is removed later.");
assertIncludes("src/types/library.ts", "locationLabel", "Source snapshot must keep a human readable location label.");
assertIncludes("src/types/library.ts", "excerpt", "Source snapshot must keep selected text as source excerpt.");

// 摘录目的地 seam：必须同时提供"摘录到收件箱"和"摘录到项目资料卡"两条路径。
assertIncludes("src/types/library.ts", "saveToInbox", "Reader excerpt destination must expose saveToInbox (inbox.create).");
assertIncludes("src/types/library.ts", "saveToProjectCard", "Reader excerpt destination must expose saveToProjectCard (card.create).");

// 摘录 UI：ExcerptPicker 必须同时呈现两条摘录路径。
assertIncludes("src/features/library/ExcerptPicker.tsx", "全局收件箱", "ExcerptPicker must offer saving excerpts to the global inbox.");
assertIncludes("src/features/library/ExcerptPicker.tsx", "项目资料卡", "ExcerptPicker must offer saving excerpts to a project card.");

// 阅读端接入 ExcerptPicker（不再走"查看灵感"）。
assertIncludes("src/features/library/ReaderPage.tsx", "ExcerptPicker", "Text/Markdown reader must integrate ExcerptPicker for excerpt destination.");
assertIncludes("src/features/library/EpubReaderPage.tsx", "ExcerptPicker", "EPUB reader must integrate ExcerptPicker for excerpt destination.");

// 兼容跳转层：InspirationPage 已并入全局收件箱，不再是灵感管理主入口。
assertIncludes("src/features/inspiration/InspirationPage.tsx", "已并入全局收件箱", "InspirationPage must act as a compatibility redirect to the global inbox.");

console.log("[verify-reading-inspiration] Reading-to-excerpt destination guards verified.");
