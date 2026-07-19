import { describe, expect, it } from "vitest";
import { preparePlainTextSourceAsync } from "./mobile-reader-preparation";

describe("async TXT preparation", () => {
  it("builds chapter metadata without changing the source text", async () => {
    const content = "前言\n\n第一章 开始\n\n正文一。\n\n第二章 继续\n\n正文二。";
    const source = await preparePlainTextSourceAsync(content, "测试书");

    expect(source.normalized).toBe(content);
    expect(source.toc.some((item) => item.title.includes("第一章"))).toBe(true);
    expect(source.wordCount).toBeGreaterThan(0);
  });

  it("rejects an already aborted preparation task", async () => {
    const abort = new AbortController();
    abort.abort();

    await expect(preparePlainTextSourceAsync("正文", "测试书", abort.signal)).rejects.toMatchObject({
      name: "AbortError"
    });
  });
});
