import { describe, expect, it } from "vitest";
import { creationDocumentToPlainText, diffParagraphs, diffStats, splitParagraphs } from "@/features/creation/ai/diff-paragraphs";

describe("splitParagraphs", () => {
  it("空行分段、无空行按行、空白裁剪", () => {
    expect(splitParagraphs("第一段。\n\n第二段。")).toEqual(["第一段。", "第二段。"]);
    expect(splitParagraphs("一行。\n二行。")).toEqual(["一行。", "二行。"]);
    expect(splitParagraphs("  空白。 \n\n")).toEqual(["空白。"]);
    expect(splitParagraphs("")).toEqual([]);
  });
});

describe("diffParagraphs", () => {
  it("全同 → 全 same", () => {
    const entries = diffParagraphs("A。\n\nB。", "A。\n\nB。");
    expect(entries).toEqual([{ type: "same", text: "A。\nB。" }]);
  });

  it("段落替换 → remove + add", () => {
    const entries = diffParagraphs("开头。\n旧中段。\n结尾。", "开头。\n新中段。\n结尾。");
    expect(entries).toEqual([
      { type: "same", text: "开头。" },
      { type: "remove", text: "旧中段。" },
      { type: "add", text: "新中段。" },
      { type: "same", text: "结尾。" }
    ]);
  });

  it("纯新增与纯删除", () => {
    expect(diffParagraphs("", "新1。\n新2。")).toEqual([{ type: "add", text: "新1。\n新2。" }]);
    expect(diffParagraphs("旧1。\n旧2。", "")).toEqual([{ type: "remove", text: "旧1。\n旧2。" }]);
  });

  it("相邻同类型合并为一个条目", () => {
    const entries = diffParagraphs("A。\n删1。\n删2。\nD。", "A。\n增1。\n增2。\n增3。\nD。");
    const types = entries.map((entry) => entry.type);
    expect(types).toEqual(["same", "remove", "add", "same"]);
    expect(entries[1]!.text).toBe("删1。\n删2。");
    expect(entries[2]!.text).toBe("增1。\n增2。\n增3。");
  });

  it("diffStats 按段落计数", () => {
    const entries = diffParagraphs("A。\n删1。\n删2。\nD。", "A。\n增1。\nD。");
    expect(diffStats(entries)).toEqual({ added: 1, removed: 2, same: 2 });
  });
});

describe("creationDocumentToPlainText", () => {
  it("提取段落文本；非段落块与空段忽略", () => {
    const doc = {
      type: "doc",
      content: [
        { type: "paragraph", content: [{ type: "text", text: "第一段" }] },
        { type: "heading", content: [{ type: "text", text: "标题" }] },
        { type: "paragraph", content: [] },
        { type: "paragraph", content: [{ type: "text", text: "第二段" }, { type: "text", text: "续" }] }
      ]
    };
    expect(creationDocumentToPlainText(doc)).toBe("第一段\n第二段续");
    expect(creationDocumentToPlainText(null)).toBe("");
    expect(creationDocumentToPlainText({ type: "doc", content: [] })).toBe("");
  });
});
