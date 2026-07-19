import {
  clampReaderIndex,
  escapeHtml,
  RENDER_ALL_MAX_CHAPTERS,
  RENDER_ALL_MAX_HTML_BYTES,
  TXT_MAX_RENDER_CHARS,
  TXT_TOC_LIMIT,
  TXT_VIRTUAL_CHAPTER_CHARS,
  renderPreformattedTextBlock,
  type MobileReaderDocument,
  type MobileReaderRenderOptions,
  type MobileReaderTocItem
} from "./mobile-reader-types";

const chapterRegex = /^\s*(第\s*[一二三四五六七八九十百千万零\d]{1,12}\s*[章节回部集篇话节]|Chapter\s+\d+[^\n]{0,60}|CHAPTER\s+\d+[^\n]{0,60}|\d+[\.、]\s*[^\n]{1,40}|序章|序言|前言|楔子|尾声|番外|后记)[^\n]{0,60}$/gim;
const volumeRegex = /^\s*(第\s*[一二三四五六七八九十百千万零\d]{1,12}\s*[卷部册])[^\n]{0,40}$/gim;

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function findNearestBoundary(text: string, target: number, min: number): number {
  // 1. 空行
  let boundary = text.lastIndexOf("\n\n", target);
  if (boundary > min) return boundary;
  // 2. 换行
  boundary = text.lastIndexOf("\n", target);
  if (boundary > min) return boundary;
  // 3. 句子结束标点（中文 + 英文）
  boundary = text.lastIndexOf("。", target);
  if (boundary > min) return boundary + 1;
  boundary = text.lastIndexOf("！", target);
  if (boundary > min) return boundary + 1;
  boundary = text.lastIndexOf("？", target);
  if (boundary > min) return boundary + 1;
  boundary = text.lastIndexOf("…", target);
  if (boundary > min) return boundary + 1;
  boundary = text.lastIndexOf(".", target);
  if (boundary > min && !text.slice(boundary - 1, boundary).match(/\d/)) return boundary + 1;
  boundary = text.lastIndexOf("!", target);
  if (boundary > min) return boundary + 1;
  boundary = text.lastIndexOf("?", target);
  if (boundary > min) return boundary + 1;
  // 4. 空格
  boundary = text.lastIndexOf(" ", target);
  if (boundary > min) return boundary;
  // 5. 硬切
  return target;
}

function splitByParagraphs(text: string, maxChars: number): Array<{ start: number; end: number }> {
  const ranges: Array<{ start: number; end: number }> = [];
  let start = 0;
  while (start < text.length) {
    if (text.length - start <= maxChars) {
      ranges.push({ start, end: text.length });
      break;
    }
    const end = findNearestBoundary(text, start + maxChars, start);
    ranges.push({ start, end: Math.max(end, start + 1) });
    start = end;
    while (start < text.length && (text[start] === "\n" || text[start] === "\r" || text[start] === " ")) start += 1;
  }
  return ranges;
}

function buildPlainTextToc(content: string, title: string): MobileReaderTocItem[] {
  const normalized = content.replace(/\r\n/g, "\n");
  const matches = Array.from(normalized.matchAll(chapterRegex)).filter((match) => match.index !== undefined);
  const volumeMatches = Array.from(normalized.matchAll(volumeRegex)).filter((match) => match.index !== undefined);
  const ranges: Array<{ title: string; start: number; end: number; volume?: string }> = [];

  if (matches.length) {
    const firstIndex = matches[0].index ?? 0;
    if (firstIndex > 120) ranges.push({ title: "开篇", start: 0, end: firstIndex });
    matches.forEach((match, index) => {
      const start = match.index ?? 0;
      const nextStart = matches[index + 1]?.index ?? normalized.length;
      const activeVolume = volumeMatches
        .filter((v) => (v.index ?? 0) <= start)
        .sort((a, b) => (b.index ?? 0) - (a.index ?? 0))[0];
      ranges.push({
        title: match[0].trim() || `章节 ${index + 1}`,
        start,
        end: nextStart,
        volume: activeVolume?.[0].trim()
      });
    });
  } else {
    const paraRanges = splitByParagraphs(normalized, TXT_VIRTUAL_CHAPTER_CHARS);
    paraRanges.forEach((range, index) => {
      ranges.push({
        title: normalized.length > TXT_VIRTUAL_CHAPTER_CHARS ? `片段 ${index + 1}` : title,
        start: range.start,
        end: range.end
      });
    });
  }

  const toc: MobileReaderTocItem[] = [];
  let lastVolume: string | undefined;
  for (const range of ranges.length ? ranges : [{ title, start: 0, end: normalized.length }]) {
    const span = Math.max(0, range.end - range.start);
    const chunkSize = span > TXT_MAX_RENDER_CHARS ? TXT_VIRTUAL_CHAPTER_CHARS : TXT_MAX_RENDER_CHARS;
    for (let start = range.start, part = 0; start < range.end || (range.end === 0 && part === 0); start += chunkSize, part += 1) {
      if (toc.length >= TXT_TOC_LIMIT) break;
      const end = Math.min(range.end || normalized.length, start + chunkSize);
      if (part === 0 && range.volume && range.volume !== lastVolume) {
        toc.push({
          id: `txt-volume-${toc.length}`,
          title: range.volume,
          level: 0,
          index: toc.length,
          startOffset: start,
          endOffset: end
        });
        lastVolume = range.volume;
      }
      toc.push({
        id: `txt-chapter-${toc.length}`,
        title: part === 0 ? range.title : `${range.title} · ${part + 1}`,
        level: part === 0 ? 1 : 2,
        index: toc.length,
        startOffset: start,
        endOffset: end
      });
      if (range.end === 0) break;
    }
    if (toc.length >= TXT_TOC_LIMIT) break;
  }

  return toc.length ? toc : [{ id: "txt-start", title, level: 1, index: 0, startOffset: 0, endOffset: normalized.length }];
}

export interface PreparedPlainTextSource {
  title: string;
  normalized: string;
  toc: MobileReaderTocItem[];
  wordCount: number;
}

/**
 * TXT 的整书预处理结果。目录扫描是大文件阅读中最昂贵的步骤之一，
 * 因此应在打开书籍时执行一次，翻章时只切片并排版当前章节。
 */
export function preparePlainTextSource(content: string, title = "TXT 书籍"): PreparedPlainTextSource {
  const normalized = content.replace(/\r\n/g, "\n");
  return {
    title,
    normalized,
    toc: buildPlainTextToc(normalized, title),
    wordCount: normalized.replace(/\s/g, "").length
  };
}

function stripLeadingChapterTitle(text: string, title: string): string {
  if (!title) return text;
  const trimmed = text.trimStart();
  const pattern = new RegExp(`^${escapeRegExp(title)}\\s*[\\n\\r]*`);
  return trimmed.replace(pattern, "").trimStart();
}

export function renderPreparedPlainText(source: PreparedPlainTextSource, options: MobileReaderRenderOptions = {}): MobileReaderDocument {
  const { normalized, toc, title, wordCount } = source;
  const renderAll = options.renderAllChapters ?? false;
  if (renderAll) {
    const chapterCount = toc.filter((item) => item.level > 0).length;
    const useRenderAll = chapterCount <= RENDER_ALL_MAX_CHAPTERS;
    if (useRenderAll) {
      const htmlParts = toc.map((item, index) => {
        if (item.level === 0) return "";
        const rawText = normalized.slice(item.startOffset ?? 0, item.endOffset ?? normalized.length).trim();
        if (!rawText) return "";
        const chapterText = stripLeadingChapterTitle(rawText, item.title);
        return `<section id="${item.id}" data-chapter-index="${index + 1}"><h2>${escapeHtml(item.title)}</h2>${renderPreformattedTextBlock(chapterText || "正文为空。")}</section>`;
      }).filter(Boolean);
      const allHtml = htmlParts.join("\n");
      if (allHtml.length <= RENDER_ALL_MAX_HTML_BYTES) {
        return {
          title,
          format: "txt",
          html: allHtml,
          plainText: normalized,
          fullText: normalized,
          toc,
          wordCount,
          currentTocIndex: 0,
          totalChapters: toc.length,
          renderAllChapters: true
        };
      }
    }
  }
  const currentIndex = clampReaderIndex(options.chapterIndex, toc.length);
  const current = toc[currentIndex] ?? toc[0];
  const rawText = normalized.slice(current.startOffset ?? 0, current.endOffset ?? normalized.length).trim() || "正文为空。";
  const chapterText = current.level === 0 ? rawText : stripLeadingChapterTitle(rawText, current.title ?? "");
  const html = current.level === 0
    ? `<section id="${current.id}" data-chapter-index="${currentIndex + 1}">${renderPreformattedTextBlock(chapterText)}</section>`
    : `<section id="${current.id}" data-chapter-index="${currentIndex + 1}"><h2>${escapeHtml(current.title ?? title)}</h2>${renderPreformattedTextBlock(chapterText)}</section>`;
  return {
    title,
    format: "txt",
    html,
    plainText: chapterText,
    fullText: normalized,
    toc,
    wordCount,
    currentTocIndex: currentIndex,
    totalChapters: toc.length
  };
}

export function renderPlainText(content: string, title = "TXT 书籍", options: MobileReaderRenderOptions = {}): MobileReaderDocument {
  return renderPreparedPlainText(preparePlainTextSource(content, title), options);
}
