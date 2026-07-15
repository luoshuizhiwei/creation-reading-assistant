import type { BookFormat } from "../../../src/types/library";

// 渲染保护阈值
export const LARGE_MARKDOWN_PLAIN_TEXT_THRESHOLD = 1024 * 1024;
export const TXT_VIRTUAL_CHAPTER_CHARS = 14 * 1024;
export const TXT_MAX_RENDER_CHARS = 64 * 1024;
export const TXT_TOC_LIMIT = 800;
export const EPUB_INLINE_IMAGES_BY_DEFAULT = false;
export const EPUB_INLINE_IMAGE_MAX_BYTES = 2 * 1024 * 1024; // 单张图片 2MB 以上跳过，避免 WebView 内存压力
// renderAll 模式（滚动连续渲染）保护阈值：章节过多或 HTML 过大时回退单章渲染，避免 WebView OOM
export const RENDER_ALL_MAX_CHAPTERS = 300;
export const RENDER_ALL_MAX_HTML_BYTES = 5 * 1024 * 1024; // 拼接后 HTML 超过 5MB 回退单章

export interface MobileReaderTocItem {
  id: string;
  title: string;
  level: number;
  index?: number;
  href?: string;
  startOffset?: number;
  endOffset?: number;
}

export interface MobileReaderRenderOptions {
  chapterIndex?: number;
  inlineEpubImages?: boolean;
  renderAllChapters?: boolean;
}

export interface MobileReaderDocument {
  title: string;
  format: BookFormat;
  html: string;
  plainText: string;
  toc: MobileReaderTocItem[];
  wordCount: number;
  currentTocIndex?: number;
  totalChapters?: number;
  /** renderAll 模式：所有章节已拼成连续 HTML，currentTocIndex 恒为 0，进度按全书整体计算 */
  renderAllChapters?: boolean;
  /**
   * 全文原始文本（不含 HTML 标签）。
   * 用于 TXT/Markdown 等内容锚点定位：保存阅读位置时按全局字符偏移计算，
   * 恢复时根据偏移反查章节，避免虚拟章节切分策略变化后位置错乱。
   */
  fullText?: string;
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

export function renderPreformattedTextBlock(content: string): string {
  const trimmed = content.trim();
  const paragraphs = trimmed.split(/\n\s*\n/).filter((p) => p.trim().length > 0);
  if (paragraphs.length <= 1) {
    return `<div class="reader-preformatted">${escapeHtml(trimmed)}</div>`;
  }
  return `<div class="reader-txt-body">${paragraphs.map((p) => `<p>${escapeHtml(p).replace(/\n/g, "<br>")}</p>`).join("")}</div>`;
}

export function clampReaderIndex(index: number | undefined, total: number): number {
  if (total <= 0) return 0;
  if (index === undefined || Number.isNaN(index)) return 0;
  return Math.min(total - 1, Math.max(0, Math.floor(index)));
}

export function plainTextFromHtml(html: string): string {
  return html.replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<[^>]+>/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function inlineMarkdown(value: string): string {
  return escapeHtml(value)
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}
