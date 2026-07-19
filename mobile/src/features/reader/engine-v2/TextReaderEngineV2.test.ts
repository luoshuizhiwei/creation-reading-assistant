import { describe, expect, it } from "vitest";
import type { MobileBook } from "../../../types/mobile";
import { TextReaderEngineV2 } from "./TextReaderEngineV2";
import type { ReaderOpenInput, ReaderPreferences } from "./types";

const preferences: ReaderPreferences = {
  fontSize: 18,
  lineHeight: 1.8,
  pageMargin: 22,
  paragraphSpacing: 1.1,
  readerBackground: "warm",
  readerMode: "paged",
  fontWeight: "regular"
};

function book(format: MobileBook["format"]): MobileBook {
  return {
    id: `book-${format}`,
    title: "测试书",
    author: "测试作者",
    filePath: `books/test.${format}`,
    format,
    importedAt: "2026-07-19T00:00:00.000Z",
    updatedAt: "2026-07-19T00:00:00.000Z",
    size: 1024,
    revision: 1,
    deviceId: "test"
  };
}

function input(format: "txt" | "markdown", content: string, signal = new AbortController().signal): ReaderOpenInput {
  return {
    book: book(format === "markdown" ? "md" : "txt"),
    format,
    content,
    preferences,
    signal
  };
}

describe("TextReaderEngineV2", () => {
  it("builds a TXT publication with stable chapter ids", async () => {
    const engine = new TextReaderEngineV2("txt");
    const publication = await engine.open(input("txt", "第一章 开始\n\n正文。\n\n第二章 继续\n\n正文。"));

    expect(publication.readingOrder.length).toBeGreaterThanOrEqual(2);
    expect(publication.readingOrder[0].id).toMatch(/^txt-chapter-/);
    expect(publication.metadata?.author).toBe("测试作者");
    await engine.destroy();
  });

  it("normalizes Markdown headings into the publication TOC", async () => {
    const engine = new TextReaderEngineV2("markdown");
    const publication = await engine.open(input("markdown", "# 第一章\n正文\n## 小节\n内容"));

    expect(publication.tableOfContents.map((item) => item.title)).toEqual(["第一章", "小节"]);
    await engine.destroy();
  });

  it("does not commit an aborted open task", async () => {
    const engine = new TextReaderEngineV2("txt");
    const abort = new AbortController();
    abort.abort();

    await expect(engine.open(input("txt", "正文", abort.signal))).rejects.toMatchObject({ code: "aborted" });
    await engine.destroy();
  });
});
