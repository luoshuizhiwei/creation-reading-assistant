// @vitest-environment node
/**
 * BD-184 划词工具栏关闭契约：取色面板（调色盘）的开关状态住在阅读器页面这一层，
 * 不在 EpubSelectionToolbar / ReaderSelectionToolbar 组件里。因此**每一条**清空划词
 * 工具栏的路径都必须连着把它收起，否则下一次划词弹出的工具栏自带一块已经展开的
 * 调色盘。改前 EPUB 漏三条（iframe 内 click——它不冒泡到外层 document，所以点空白
 * 那条兜不住、Esc、复制），TXT/MD 漏两条（复制、设章节起点）。
 *
 * 这两个文件都要主进程 / epub.js iframe 才挂得起来，jsdom 里渲染不了，按本仓源码账
 * 惯例（shortcut-chord-contract.test.ts、txt-chapter-split-pressed-source.test.ts）
 * 走源码级判据：台账比的是「清空工具栏的次数 == 成对复位取色面板的次数」，
 * 新增一条关闭路径却忘了复位，当场红。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");

const epubSrc = read("../../EpubReaderPage.tsx");
const txtSrc = read("../TxtMarkdownReader.tsx");

/** 中间只允许空行与整行 `//` 注释——复位必须紧跟在清空后面，不许隔一条别的语句。 */
const CLOSE_RE = /setSelectionToolbar\(null\);/g;
const PAIR_RE = /^\s*(?:\/\/[^\n]*\n\s*)*setShowColorPicker\(false\);/;

function auditLedger(label: string, src: string, expectedSites: number) {
  const unpaired: string[] = [];
  let sites = 0;
  for (const match of src.matchAll(CLOSE_RE)) {
    sites += 1;
    const rest = src.slice((match.index ?? 0) + match[0].length);
    if (!PAIR_RE.test(rest)) {
      const line = src.slice(0, match.index).split("\n").length;
      unpaired.push(`${label} 第 ${line} 行`);
    }
  }
  expect(unpaired, `未复位的关闭路径：${unpaired.join("、")}`).toEqual([]);
  // 台账根数：关闭路径增删都要显式改这里，避免「成对删除」把这条契约悄悄削薄。
  expect(sites).toBe(expectedSites);
}

describe("划词工具栏关闭路径必须连取色面板一起复位", () => {
  it("EPUB：每一条清空工具栏的路径都成对复位（共 7 条）", () => {
    auditLedger("EpubReaderPage", epubSrc, 7);
  });

  it("TXT/MD：每一条清空工具栏的路径都成对复位（共 8 条）", () => {
    auditLedger("TxtMarkdownReader", txtSrc, 8);
  });

  it("EPUB：iframe 内的 click 复位（它不冒泡到外层 document，点空白那条兜不住）", () => {
    expect(epubSrc).toMatch(
      /rendition\.on\("click", \(\) => \{[\s\S]*?setSelectionToolbar\(null\);[\s\S]*?setShowColorPicker\(false\);[\s\S]*?\}\);/
    );
  });

  it("EPUB：Esc 关掉工具栏时同样收起取色面板", () => {
    expect(epubSrc).toMatch(
      /if \(event\.key === "Escape"\) \{\s*setSelectionToolbar\(null\);\s*setShowColorPicker\(false\);\s*\}/
    );
  });

  it("TXT/MD：复制与设章节起点这两条关闭路径也补齐了复位", () => {
    expect(txtSrc).toMatch(
      /onCopy=\{async \(\) => \{[\s\S]*?setSelectionToolbar\(null\);[\s\S]*?setShowColorPicker\(false\);/
    );
    expect(txtSrc).toMatch(
      /addChapterStartFromSelection\(offset\);\s*setSelectionToolbar\(null\);\s*setShowColorPicker\(false\);/
    );
  });
});
