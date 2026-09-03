/**
 * 用户手动修正章节表（tocOverrides → chaptersFromOverrides）验证：
 * 行首规范化、行去重、非法条目丢弃、「正文」标题保留、序章/endIndex 契约与启发式一致。
 */
import { describe, it, expect } from "vitest";
import { chaptersFromOverrides, splitTxtChapters } from "../toc/txt-chapters";

describe("chaptersFromOverrides", () => {
  it("builds chapters from user-provided start offsets", () => {
    const content = "第一章 开端\n正文甲\n\n第二章 转折\n正文乙";
    const chapters = chaptersFromOverrides(content, [
      { title: "第一章 开端", startIndex: 0 },
      { title: "第二章 转折", startIndex: content.indexOf("第二章") }
    ]);
    expect(chapters.map((c) => c.title)).toEqual(["第一章 开端", "第二章 转折"]);
    // endIndex 裁掉章尾空行（与启发式同契约）
    expect(chapters[0].endIndex).toBe(content.indexOf("第二章") - 2);
    expect(content.slice(chapters[0].contentStart, chapters[0].endIndex)).toBe("正文甲");
    expect(content.slice(chapters[1].contentStart, chapters[1].endIndex)).toBe("正文乙");
  });

  it("normalizes mid-line offsets to line starts", () => {
    const content = "第一章 开端\n这是正文的一行\n尾行";
    const midLine = content.indexOf("这是正文的") + 2;
    const chapters = chaptersFromOverrides(content, [{ title: "新章", startIndex: midLine }]);
    // 行首之前有内容 → 自动补序章（与启发式同契约），新章在第二位
    expect(chapters).toHaveLength(2);
    expect(chapters[0].title).toBe("");
    const newChapter = chapters[1];
    expect(newChapter.startIndex).toBe(content.indexOf("这是正文的一行"));
    // 标题行不再进入正文
    expect(content.slice(newChapter.contentStart, newChapter.endIndex)).toBe("尾行");
  });

  it("drops duplicate-line and invalid entries", () => {
    const content = "甲行\n乙行\n丙行";
    const chapters = chaptersFromOverrides(content, [
      { title: "A", startIndex: content.indexOf("乙行") + 1 }, // 与下一行规范化后同行，先到先得
      { title: "B", startIndex: content.indexOf("乙行") },
      { title: "C", startIndex: -5 },
      { title: "D", startIndex: content.length + 3 },
      { title: "E", startIndex: content.indexOf("丙行") }
    ]);
    // 行首之前有内容（"甲行"）→ 自动补序章
    expect(chapters.map((c) => c.title)).toEqual(["", "A", "E"]);
  });

  it("keeps a chapter literally named 正文 (no builtin downgrading)", () => {
    const content = "正文\n内容一\n第一章 真\n内容二";
    const chapters = chaptersFromOverrides(content, [
      { title: "正文", startIndex: 0 },
      { title: "第一章 真", startIndex: content.indexOf("第一章") }
    ]);
    expect(chapters.map((c) => c.title)).toEqual(["正文", "第一章 真"]);
  });

  it("adds a prologue when content precedes the first chapter (same contract as heuristics)", () => {
    const content = "简介页\n\n第一章 开始\n正文";
    const chapters = chaptersFromOverrides(content, [{ title: "第一章 开始", startIndex: content.indexOf("第一章") }]);
    expect(chapters[0].title).toBe("");
    expect(chapters[0].startIndex).toBe(0);
    expect(chapters).toHaveLength(2);
  });

  it("empty/invalid overrides produce no chapters", () => {
    expect(chaptersFromOverrides("abc", [])).toEqual([]);
    expect(chaptersFromOverrides("", [{ title: "x", startIndex: 0 }])).toEqual([]);
  });

  it("supports rename semantics: original title line is consumed, custom title displayed", () => {
    const content = "旧标题\n正文内容";
    const chapters = chaptersFromOverrides(content, [{ title: "重命名后的章节", startIndex: 0 }]);
    expect(chapters[0].title).toBe("重命名后的章节");
    expect(content.slice(chapters[0].contentStart, chapters[0].endIndex)).toBe("正文内容");
  });

  it("heuristics still downgrade 正文 when used via splitTxtChapters", () => {
    const content = "引子\n楔子内容\n\n第一章 开始\n正文\n\n尾声\n结局";
    const chapters = splitTxtChapters(content);
    expect(chapters.map((c) => c.title)).toEqual(["引子", "第一章 开始", "尾声"]);
  });
});
