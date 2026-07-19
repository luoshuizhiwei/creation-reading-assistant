import { describe, expect, it } from "vitest";
import {
  preparePlainTextSource,
  renderPlainText,
  renderPreparedPlainText
} from "./mobile-reader-txt";

describe("TXT reader preparation", () => {
  const content = [
    "内容简介",
    "这是简介。",
    "",
    "第1章 开始",
    "第一章正文。",
    "",
    "第2章 继续",
    "第二章正文。"
  ].join("\n");

  it("reuses one prepared TOC while rendering different chapters", () => {
    const prepared = preparePlainTextSource(content, "测试书");
    const first = renderPreparedPlainText(prepared, { chapterIndex: 0 });
    const second = renderPreparedPlainText(prepared, { chapterIndex: 1 });

    expect(first.toc).toBe(prepared.toc);
    expect(second.toc).toBe(prepared.toc);
    expect(first.currentTocIndex).toBe(0);
    expect(second.currentTocIndex).toBe(1);
    expect(first.html).not.toBe(second.html);
  });

  it("keeps the public one-shot renderer compatible", () => {
    const rendered = renderPlainText(content, "测试书", { chapterIndex: 1 });
    expect(rendered.format).toBe("txt");
    expect(rendered.currentTocIndex).toBe(1);
    expect(rendered.toc.length).toBeGreaterThan(1);
  });
});
