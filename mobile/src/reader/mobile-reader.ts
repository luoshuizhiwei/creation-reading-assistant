import type { BookFormat } from "../../../src/types/library";

export interface MobileReaderDocument {
  title: string;
  format: BookFormat;
  html: string;
  plainText: string;
  toc: Array<{ id: string; title: string; level: number }>;
  wordCount: number;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function inlineMarkdown(value: string): string {
  return escapeHtml(value)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}

export function renderMarkdown(markdown: string): MobileReaderDocument {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const toc: MobileReaderDocument["toc"] = [];
  const html: string[] = [];
  let inCode = false;
  let inTable = false;

  lines.forEach((line, index) => {
    if (line.trim().startsWith("```")) {
      inCode = !inCode;
      html.push(inCode ? "<pre><code>" : "</code></pre>");
      return;
    }
    if (inCode) {
      html.push(`${escapeHtml(line)}\n`);
      return;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(line);
    if (heading) {
      const level = heading[1].length;
      const title = heading[2].trim();
      const id = `heading-${index}`;
      toc.push({ id, title, level });
      html.push(`<h${level} id="${id}">${inlineMarkdown(title)}</h${level}>`);
      return;
    }

    if (/^\s*\|.+\|\s*$/.test(line)) {
      if (!inTable) {
        html.push("<table>");
        inTable = true;
      }
      const cells = line
        .trim()
        .slice(1, -1)
        .split("|")
        .map((cell) => `<td>${inlineMarkdown(cell.trim())}</td>`)
        .join("");
      if (!/^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line)) html.push(`<tr>${cells}</tr>`);
      return;
    }
    if (inTable) {
      html.push("</table>");
      inTable = false;
    }

    const quote = /^>\s?(.+)$/.exec(line);
    if (quote) {
      html.push(`<blockquote>${inlineMarkdown(quote[1])}</blockquote>`);
      return;
    }

    const listItem = /^[-*+]\s+(.+)$/.exec(line);
    if (listItem) {
      html.push(`<ul><li>${inlineMarkdown(listItem[1])}</li></ul>`);
      return;
    }

    if (line.trim()) html.push(`<p>${inlineMarkdown(line.trim())}</p>`);
  });
  if (inTable) html.push("</table>");

  return {
    title: toc[0]?.title ?? "Markdown 书籍",
    format: "md",
    html: html.join("\n"),
    plainText: markdown,
    toc,
    wordCount: markdown.replace(/\s/g, "").length
  };
}

export function extractEpubText(content: string, title = "EPUB 书籍"): MobileReaderDocument {
  const clean = content
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<[^>]+>/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
  const fallback = clean || "EPUB 文件已保存到手机端。完整保留原书样式的渲染会在后续接入 EPUB 专用渲染器继续加强。";
  return {
    title,
    format: "epub",
    html: `<p>${escapeHtml(fallback).replace(/\n{2,}/g, "</p><p>").replace(/\n/g, "<br />")}</p>`,
    plainText: fallback,
    toc: [{ id: "epub-start", title: "开始阅读", level: 1 }],
    wordCount: fallback.replace(/\s/g, "").length
  };
}

export function renderPlainText(content: string, title = "TXT 书籍"): MobileReaderDocument {
  const chapters = content.match(/^第.{1,12}[章节回卷部集].*$/gm) ?? [];
  const toc = chapters.slice(0, 80).map((chapter, index) => ({
    id: `txt-chapter-${index}`,
    title: chapter.trim(),
    level: 1
  }));
  return {
    title,
    format: "txt",
    html: content
      .split(/\n{2,}/)
      .map((paragraph) => `<p>${escapeHtml(paragraph.trim()).replace(/\n/g, "<br />")}</p>`)
      .join("\n"),
    plainText: content,
    toc,
    wordCount: content.replace(/\s/g, "").length
  };
}

export function renderMobileDocument(format: BookFormat, content: string, title: string): MobileReaderDocument {
  if (format === "md") return renderMarkdown(content);
  if (format === "epub") return extractEpubText(content, title);
  return renderPlainText(content, title);
}
