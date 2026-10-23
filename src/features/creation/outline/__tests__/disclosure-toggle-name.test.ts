// @vitest-environment node
/**
 * BD-125 护栏：大纲树的章节/卷折叠钮是纯图标（Chevron）+ aria-expanded，若不给 aria-label，
 * 屏幕阅读器只会念"按钮，已折叠/已展开"、说不出展开的是哪一章/哪一卷（违反 WCAG 4.1.2 Name）。
 * 断言每个 `aria-expanded={expanded}` 的折叠钮后都紧跟 aria-label。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, it, expect } from "vitest";

const src = readFileSync(fileURLToPath(new URL("../OutlineTree.tsx", import.meta.url)), "utf8");

describe("BD-125 大纲折叠钮必须有可访问名", () => {
  it("每个 aria-expanded={expanded} 的折叠钮都紧跟着 aria-label", () => {
    const expandedCount = (src.match(/aria-expanded=\{expanded\}/g) ?? []).length;
    const labeledCount = (src.match(/aria-expanded=\{expanded\}\s*\n?\s*aria-label=/g) ?? []).length;
    expect(expandedCount).toBeGreaterThanOrEqual(2); // 至少章节 + 卷两处
    expect(labeledCount, "有折叠钮缺 aria-label").toBe(expandedCount);
  });
});
