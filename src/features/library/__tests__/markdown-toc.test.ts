/**
 * Markdown 目录提取与标题锚点验证：
 * - ATX / Setext 两类标题均识别，id 注入与目录严格对齐；
 * - 围栏代码块内的伪标题不进目录、也不打断 id 对齐；
 * - 重复标题 slug 追加序号保证唯一（跳转不再落错位置）。
 */
import { describe, it, expect } from "vitest";
import { renderMarkdownWithToc } from "../toc/markdown-toc";

describe("renderMarkdownWithToc", () => {
  it("extracts ATX headings and injects matching ids", () => {
    const { html, toc } = renderMarkdownWithToc("# 开端\n\n正文\n\n## 第二节\n\n内容");
    expect(toc).toEqual([
      { id: "开端", title: "开端", level: 1 },
      { id: "第二节", title: "第二节", level: 2 }
    ]);
    expect(html).toContain('<h1 id="开端">');
    expect(html).toContain('<h2 id="第二节">');
  });

  it("extracts Setext headings (underline style)", () => {
    const { html, toc } = renderMarkdownWithToc("卷一 少年游\n========\n\n正文\n\n尾声\n----\n\n完");
    expect(toc).toEqual([
      { id: "卷一-少年游", title: "卷一 少年游", level: 1 },
      { id: "尾声", title: "尾声", level: 2 }
    ]);
    expect(html).toContain('<h1 id="卷一-少年游">');
    expect(html).toContain('<h2 id="尾声">');
  });

  it("ignores pseudo headings inside fenced code blocks", () => {
    const { html, toc } = renderMarkdownWithToc("# 真标题\n\n```md\n# 不是标题\n```\n\n正文");
    expect(toc).toEqual([{ id: "真标题", title: "真标题", level: 1 }]);
    expect(html).toContain("# 不是标题");
    expect(html).not.toContain('<h1 id="不是标题">');
  });

  it("does not confuse a thematic break after a blank line with a Setext underline", () => {
    const { toc } = renderMarkdownWithToc("# 标题\n\n正文一段\n\n---\n\n正文二段");
    expect(toc).toEqual([{ id: "标题", title: "标题", level: 1 }]);
  });

  it("makes duplicate slugs unique with ordinal suffixes", () => {
    const { html, toc } = renderMarkdownWithToc("## 讨论\n\n内容\n\n## 讨论\n\n更多内容");
    expect(toc.map((t) => t.id)).toEqual(["讨论", "讨论-2"]);
    expect(html).toContain('<h2 id="讨论">');
    expect(html).toContain('<h2 id="讨论-2">');
  });

  it("strips inline markup from toc titles", () => {
    const { toc } = renderMarkdownWithToc("## **加粗** 与 `代码`");
    expect(toc[0].title).toBe("加粗 与 代码");
  });

  it("falls back to ordinal heading id for empty/symbol-only titles", () => {
    const { toc } = renderMarkdownWithToc("## ***\n\n内容");
    expect(toc).toEqual([{ id: "heading-1", title: "标题 1", level: 2 }]);
  });
});
