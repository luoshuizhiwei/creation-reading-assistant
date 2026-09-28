/**
 * TXT/Markdown 渲染层：从 TxtMarkdownReader 纯移动而来的净化与段落渲染逻辑。
 *
 * 页面组件保留滚动、锚点与目录编辑的状态编排；HTML 白名单净化与段落切分是
 * 可独立测试的纯函数，故移出。除导出可见性外，函数体与移动前逐字节一致。
 */
import DOMPurify from "dompurify";

export function sanitizeMarkdownHtml(html: string): string {
  const clean = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "a",
      "blockquote",
      "br",
      "code",
      "div",
      "em",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "hr",
      "li",
      "ol",
      "p",
      "pre",
      "s",
      "span",
      "strong",
      "table",
      "tbody",
      "td",
      "th",
      "thead",
      "tr",
      "ul"
    ],
    ALLOWED_ATTR: ["href", "title", "target", "rel", "id", "class"],
    ALLOW_DATA_ATTR: false,
    FORBID_ATTR: ["style"],
    ALLOWED_URI_REGEXP: /^(?:(?:(?:https?|mailto|file|ftp):)|#|\/)/i
  });
  return clean.replace(/<a\b([^>]*)>/gi, (match, attrs: string) => {
    if (/\btarget=/i.test(attrs) && /\brel=/i.test(attrs)) return match;
    const target = /\btarget=/i.test(attrs) ? "" : ' target="_blank"';
    const rel = /\brel=/i.test(attrs) ? "" : ' rel="noopener noreferrer"';
    return `<a${attrs}${target}${rel}>`;
  });
}

export function renderPlainText(content: string) {
  return content.split(/\n{2,}/).map((paragraph, index) => (
    <p key={index} className="mb-5 whitespace-pre-wrap">
      {paragraph}
    </p>
  ));
}

export function renderChapterParagraphs(text: string) {
  return text.split(/\n{2,}/).map((paragraph, index) => (
    <p key={index} className="mb-5 whitespace-pre-wrap">
      {paragraph}
    </p>
  ));
}

export function findCurrentHeadingAnchor(scroller: HTMLDivElement | null): { id: string; title: string } | undefined {
  if (!scroller) return undefined;
  const headings = Array.from(scroller.querySelectorAll<HTMLElement>("h1, h2, h3, h4, h5, h6"));
  const containerTop = scroller.getBoundingClientRect().top;
  let best: HTMLElement | null = null;
  for (const h of headings) {
    const offset = h.getBoundingClientRect().top - containerTop;
    if (offset <= 20) best = h;
    else break;
  }
  if (!best?.id) return undefined;
  return { id: best.id, title: best.textContent?.trim() ?? "" };
}
