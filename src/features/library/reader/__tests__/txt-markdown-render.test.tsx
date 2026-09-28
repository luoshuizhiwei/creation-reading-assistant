/**
 * reader/txt-markdown-render 单测：覆盖从 TxtMarkdownReader 纯移动出来的渲染层——
 * Markdown HTML 白名单净化、外链安全属性补全、段落切分与当前标题锚点探测。
 */
// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import {
  findCurrentHeadingAnchor,
  renderChapterParagraphs,
  renderPlainText,
  sanitizeMarkdownHtml
} from "../txt-markdown-render";
import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";

afterEach(() => {
  document.body.replaceChildren();
});

describe("sanitizeMarkdownHtml", () => {
  it("移除行内 style 与脚本，保留结构化标签", () => {
    const out = sanitizeMarkdownHtml(
      '<p style="color:red">正文<strong>加粗</strong><script>alert(1)</script><img src="x" onerror="bad()"/></p>'
    );
    expect(out).toContain("<strong>加粗</strong>");
    expect(out).not.toContain("style=");
    expect(out).not.toContain("<script");
    expect(out).not.toContain("onerror");
    expect(out).not.toContain("<img");
  });

  it("保留站内锚点与安全协议，剥离 javascript: 链接", () => {
    const out = sanitizeMarkdownHtml(
      '<a href="#txt-chapter-1">站内</a><a href="https://example.com">外链</a><a href="javascript:alert(1)">危险</a>'
    );
    expect(out).toContain('href="#txt-chapter-1"');
    expect(out).toContain('href="https://example.com"');
    expect(out).not.toContain("javascript:");
  });

  it("外链补齐安全属性，且原有属性不被重复注入", () => {
    const out = sanitizeMarkdownHtml('<a href="https://example.com">A</a><a href="https://b.com" target="_blank" rel="noreferrer">B</a>');
    const [first, second] = out.split("</a>").slice(0, 2);
    expect(first).toContain('target="_blank"');
    expect(first).toContain('rel="noopener noreferrer"');
    // DOMPurify 自身会把 target=_blank 的 rel 规范化为包含 noopener；此处只要求不重复挂属性。
    expect((second.match(/target=/g) ?? []).length).toBe(1);
    expect((second.match(/rel=/g) ?? []).length).toBe(1);
    expect(second).toContain("noopener");
  });

  it("保留 id 与 class（目录锚点跳转依赖 id）", () => {
    const out = sanitizeMarkdownHtml('<h2 id="md-h1" class="reader-heading">标题</h2>');
    expect(out).toContain('id="md-h1"');
    expect(out).toContain('class="reader-heading"');
  });

  it("剥离 data-* 属性", () => {
    const out = sanitizeMarkdownHtml('<p data-evil="1">正文</p>');
    expect(out).not.toContain("data-evil");
  });
});

describe("renderPlainText / renderChapterParagraphs", () => {
  const toHtml = (nodes: ReturnType<typeof renderPlainText>) => renderToStaticMarkup(createElement("div", null, nodes as never));

  it("按连续两个及以上换行切段，单换行保留在段内", () => {
    const html = toHtml(renderPlainText("第一段\n续行\n\n第二段"));
    expect(html).toContain("第一段\n续行");
    expect((html.match(/<p/g) ?? []).length).toBe(2);
  });

  it("段落带 whitespace-pre-wrap，保留原始换行观感", () => {
    const html = toHtml(renderChapterParagraphs("甲\n乙\n\n丙"));
    expect(html).toContain("whitespace-pre-wrap");
    expect((html.match(/<p/g) ?? []).length).toBe(2);
  });

  it("空内容仍渲染单个空段落（split 语义），不抛错", () => {
    expect(toHtml(renderPlainText(""))).toBe("<div><p class=\"mb-5 whitespace-pre-wrap\"></p></div>");
  });
});

describe("findCurrentHeadingAnchor", () => {
  function fixture(tops: number[]) {
    const host = document.createElement("div");
    host.innerHTML = tops.map((_, i) => `<h2 id="h${i}">h${i}</h2>`).join("");
    document.body.replaceChildren(host);
    // jsdom 无布局，手动伪造 getBoundingClientRect 的相对位置
    host.getBoundingClientRect = () => ({ top: 0 } as DOMRect);
    tops.forEach((top, i) => {
      const el = host.querySelector(`#h${i}`) as HTMLElement;
      el.getBoundingClientRect = () => ({ top } as DOMRect);
    });
    return host as unknown as HTMLDivElement;
  }

  it("取最后一个已越过容器顶（<=20px）的标题，遇到未越过的即停止", () => {
    expect(findCurrentHeadingAnchor(fixture([10, 15, 100]))?.id).toBe("h1");
  });

  it("越过阈值的标题之后不再采信（即使位置也在阈值内也不会回头）", () => {
    // 首个标题就在视口下方时返回 undefined（文档尚未滚动到任何章节）
    const scroller = fixture([500, 600]);
    expect(findCurrentHeadingAnchor(scroller)).toBeUndefined();
  });

  it("容器为空时返回 undefined", () => {
    expect(findCurrentHeadingAnchor(null)).toBeUndefined();
  });
});
