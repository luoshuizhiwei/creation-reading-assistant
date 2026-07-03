import type { BookFormat } from "../../../src/types/library";
import JSZip from "jszip";

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
  const fallback = clean || "这本 EPUB 已保存到手机端，但当前章节没有可提取的正文。请尝试重新下载正文，或回到书架重新导入原文件。";
  return {
    title,
    format: "epub",
    html: `<p>${escapeHtml(fallback).replace(/\n{2,}/g, "</p><p>").replace(/\n/g, "<br />")}</p>`,
    plainText: fallback,
    toc: [{ id: "epub-start", title: "开始阅读", level: 1 }],
    wordCount: fallback.replace(/\s/g, "").length
  };
}

function decodeBase64ToBytes(value: string): Uint8Array {
  const binary = atob(value.replace(/^data:.*?;base64,/, ""));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function dirname(path: string): string {
  const index = path.lastIndexOf("/");
  return index >= 0 ? path.slice(0, index + 1) : "";
}

function resolvePath(base: string, href: string): string {
  if (/^(https?:|data:|#)/i.test(href)) return href;
  const parts = `${base}${href}`.replace(/\\/g, "/").split("/");
  const normalized: string[] = [];
  for (const part of parts) {
    if (!part || part === ".") continue;
    if (part === "..") normalized.pop();
    else normalized.push(part);
  }
  return normalized.join("/");
}

function textFromXml(xml: string, tagName: string): string | undefined {
  return new RegExp(`<[^:>]*:?${tagName}[^>]*>([\\s\\S]*?)<\\/[^:>]*:?${tagName}>`, "i").exec(xml)?.[1]?.replace(/<[^>]+>/g, "").trim();
}

function manifestFromOpf(opf: string, opfDir: string): Map<string, { href: string; mediaType: string }> {
  const manifest = new Map<string, { href: string; mediaType: string }>();
  for (const match of opf.matchAll(/<item\b([^>]+)>/gi)) {
    const attrs = match[1];
    const id = /\bid=["']([^"']+)["']/i.exec(attrs)?.[1];
    const href = /\bhref=["']([^"']+)["']/i.exec(attrs)?.[1];
    const mediaType = /\bmedia-type=["']([^"']+)["']/i.exec(attrs)?.[1] ?? "";
    if (id && href) manifest.set(id, { href: resolvePath(opfDir, href), mediaType });
  }
  return manifest;
}

function spineIdsFromOpf(opf: string): string[] {
  const spine = /<spine\b[\s\S]*?<\/spine>/i.exec(opf)?.[0] ?? "";
  return Array.from(spine.matchAll(/<itemref\b[^>]*idref=["']([^"']+)["'][^>]*>/gi)).map((match) => match[1]);
}

function mediaTypeFromPath(path: string): string {
  const extension = path.toLowerCase().split(".").pop() ?? "";
  if (extension === "jpg" || extension === "jpeg") return "image/jpeg";
  if (extension === "png") return "image/png";
  if (extension === "gif") return "image/gif";
  if (extension === "webp") return "image/webp";
  if (extension === "svg") return "image/svg+xml";
  return "application/octet-stream";
}

function normalizeEpubBodyMarkup(body: string): string {
  return body
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
    .replace(/\s(on\w+|style)=["'][\s\S]*?["']/gi, "")
    .replace(/\s(xml:)?lang=["']([^"']+)["']/gi, ' lang="$2"')
    .replace(/<a\b([^>]*?)href=["']javascript:[^"']*["']([^>]*)>/gi, "<a$1$2>")
    .replace(/<a\b([^>]*?)href=["'](https?:[^"']+)["']([^>]*)>/gi, '<a$1href="$2"$3 target="_blank" rel="noreferrer">')
    .replace(/<image\b/gi, "<img")
    .replace(/<\/image>/gi, "");
}

async function inlineEpubImages(zip: JSZip, html: string, chapterPath: string): Promise<string> {
  const chapterDir = dirname(chapterPath);
  const matches = Array.from(html.matchAll(/<img\b[^>]*(?:src|href|xlink:href)=["']([^"']+)["'][^>]*>/gi));
  let nextHtml = html;
  for (const match of matches) {
    const originalTag = match[0];
    const source = match[1];
    if (/^(https?:|data:|#)/i.test(source)) continue;
    const resourcePath = resolvePath(chapterDir, source);
    const file = zip.file(resourcePath);
    if (!file) continue;
    const data = await file.async("base64");
    const dataUrl = `data:${mediaTypeFromPath(resourcePath)};base64,${data}`;
    const safeTag = originalTag
      .replace(/\s(?:src|href|xlink:href)=["'][^"']+["']/i, ` src="${dataUrl}"`)
      .replace(/<img\b(?![^>]*\salt=)/i, '<img alt=""')
      .replace(/<img\b(?![^>]*\sloading=)/i, '<img loading="lazy"');
    nextHtml = nextHtml.replace(originalTag, safeTag);
  }
  return nextHtml;
}

async function xhtmlBodyToHtml(zip: JSZip, xhtml: string, chapterPath: string, fallbackTitle: string, index: number): Promise<{ html: string; title: string }> {
  const title = textFromXml(xhtml, "title") || textFromXml(xhtml, "h1") || `${fallbackTitle} · ${index + 1}`;
  const body = /<body\b[^>]*>([\s\S]*?)<\/body>/i.exec(xhtml)?.[1] ?? xhtml;
  const clean = await inlineEpubImages(zip, normalizeEpubBodyMarkup(body), chapterPath);
  return {
    title,
    html: `<section class="epub-chapter epub-publisher-flow" id="epub-chapter-${index}" data-chapter-index="${index + 1}"><h2>${escapeHtml(title)}</h2>${clean}</section>`
  };
}

export async function renderEpubDocument(content: string, title = "EPUB 书籍"): Promise<MobileReaderDocument> {
  try {
    const zip = await JSZip.loadAsync(decodeBase64ToBytes(content));
    const containerXml = await zip.file("META-INF/container.xml")?.async("string");
    const opfPath = /full-path=["']([^"']+)["']/i.exec(containerXml ?? "")?.[1];
    if (!opfPath) throw new Error("EPUB 缺少 container.xml 或 OPF 路径。");
    const opf = await zip.file(opfPath)?.async("string");
    if (!opf) throw new Error("EPUB 缺少 OPF 文件。");
    const opfDir = dirname(opfPath);
    const bookTitle = textFromXml(opf, "title") || title;
    const manifest = manifestFromOpf(opf, opfDir);
    const spineIds = spineIdsFromOpf(opf);
    const html: string[] = [];
    const toc: MobileReaderDocument["toc"] = [];
    for (const [index, id] of spineIds.entries()) {
      const item = manifest.get(id);
      if (!item || !/xhtml|html/i.test(item.mediaType)) continue;
      const xhtml = await zip.file(item.href)?.async("string");
      if (!xhtml) continue;
      const chapter = await xhtmlBodyToHtml(zip, xhtml, item.href, bookTitle, index);
      html.push(chapter.html);
      toc.push({ id: `epub-chapter-${index}`, title: chapter.title, level: 1 });
    }
    if (!html.length) throw new Error("EPUB 没有可读取的章节正文。");
    const plainText = html.join("\n").replace(/<[^>]+>/g, "\n").replace(/\n{3,}/g, "\n\n").trim();
    return {
      title: bookTitle,
      format: "epub",
      html: html.join("\n"),
      plainText,
      toc,
      wordCount: plainText.replace(/\s/g, "").length
    };
  } catch {
    return extractEpubText(content, title);
  }
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

export async function renderMobileDocument(format: BookFormat, content: string, title: string): Promise<MobileReaderDocument> {
  if (format === "md") return renderMarkdown(content);
  if (format === "epub") return renderEpubDocument(content, title);
  return renderPlainText(content, title);
}
