import ePub from "epubjs";
import type Book from "epubjs/types/book";
import type { NavItem } from "epubjs/types/navigation";
import type { SpineItem } from "epubjs/types/section";
import {
  clampReaderIndex,
  EPUB_INLINE_IMAGES_BY_DEFAULT,
  EPUB_INLINE_IMAGE_MAX_BYTES,
  escapeHtml,
  plainTextFromHtml,
  RENDER_ALL_MAX_CHAPTERS,
  RENDER_ALL_MAX_HTML_BYTES,
  TXT_TOC_LIMIT,
  type MobileReaderDocument,
  type MobileReaderRenderOptions,
  type MobileReaderTocItem
} from "./mobile-reader-types";

interface ReadableSpineItem {
  /**
   * 这里只保存可跨 Book 实例复用的稳定字段。不要缓存 epub.js 的 Section
   * 对象：Book.destroy() 后，旧 Section 持有的 archive/request 已经失效。
   */
  item: Pick<SpineItem, "index" | "href" | "linear">;
  readableIndex: number;
  title: string;
}

interface ParsedEpubStructure {
  bookTitle: string;
  readableSpineItems: ReadableSpineItem[];
  toc: MobileReaderDocument["toc"];
}

export interface EpubImportInspection {
  title: string;
  author?: string;
  description?: string;
  language?: string;
  publisher?: string;
  coverDataUrl?: string;
  toc: MobileReaderDocument["toc"];
  totalChapters: number;
}

const EPUB_STRUCTURE_CACHE_MAX = 2;
const EPUB_OPEN_TIMEOUT_MS = 12_000;
const EPUB_IMPORT_COVER_MAX_BYTES = 3 * 1024 * 1024;
const epubStructureCache = new Map<string, ParsedEpubStructure>();

type EpubSection = ReturnType<Book["section"]>;

/**
 * 不同 EPUB 以及不同 epub.js 版本对 spine index 的处理并不完全一致。
 * 优先用 index，失败后用 href，最后直接从当前 Book 的 spine 中查找，
 * 保证导入校验和实际阅读使用同一套章节定位规则。
 */
function resolveEpubSection(book: Book, item: ReadableSpineItem["item"]): EpubSection | undefined {
  const byIndex = Number.isFinite(item.index) ? book.section(item.index) : undefined;
  if (byIndex) return byIndex;

  if (item.href) {
    const byHref = book.section(item.href);
    if (byHref) return byHref;
  }

  const spineItems = (book.spine as unknown as { spineItems?: EpubSection[] }).spineItems ?? [];
  return spineItems.find((section) => section.index === item.index || (!!item.href && section.href === item.href));
}

/**
 * epub.js 在少数 Android WebView / EPUB 组合中会成功 resolve render()，
 * 但返回空字符串。此时直接从 archive 读取对应 XHTML，既能验证真实正文，
 * 也能让阅读器继续工作，而不是把有效 EPUB 判成“只有元数据”。
 */
async function readEpubSectionMarkup(book: Book, item: ReadableSpineItem["item"]): Promise<string> {
  const section = resolveEpubSection(book, item);
  let renderError: unknown;
  if (section) {
    try {
      const rendered = await (section.render() as unknown as Promise<string>);
      if (typeof rendered === "string" && rendered.trim()) return rendered;
    } catch (error) {
      renderError = error;
    }
  }

  const candidates = Array.from(new Set([item.href, section?.href, section?.url].filter((value): value is string => !!value && !value.startsWith("blob:"))));
  for (const path of candidates) {
    try {
      const raw = await book.archive.getText(path);
      if (typeof raw === "string" && raw.trim()) return raw;
    } catch {
      // 某些 EPUB 的 href 带有包根目录，继续尝试下一个候选路径。
    }
  }

  if (renderError instanceof Error) throw renderError;
  throw new Error(`EPUB 章节内容为空：${item.href || item.index}`);
}

async function withEpubStepTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  let timer = 0;
  try {
    return await Promise.race([
      promise,
      new Promise<never>((_resolve, reject) => {
        timer = window.setTimeout(() => reject(new Error(message)), timeoutMs);
      })
    ]);
  } finally {
    if (timer) window.clearTimeout(timer);
  }
}

function epubCacheKey(content: string): string {
  const normalizedLength = content.length;
  return `${normalizedLength}:${content.slice(0, 96)}:${content.slice(Math.max(0, normalizedLength - 96))}`;
}

function rememberParsedEpubStructure(key: string, structure: ParsedEpubStructure): ParsedEpubStructure {
  epubStructureCache.delete(key);
  epubStructureCache.set(key, structure);
  if (epubStructureCache.size > EPUB_STRUCTURE_CACHE_MAX) {
    const oldest = epubStructureCache.keys().next().value;
    if (oldest !== undefined) epubStructureCache.delete(oldest);
  }
  return structure;
}

function decodeBase64ToArrayBuffer(value: string): ArrayBuffer {
  let normalized = value.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
  // 兼容部分 bridge / 归档工具输出的 URL-safe base64
  normalized = normalized.replace(/-/g, "+").replace(/_/g, "/");
  while (normalized.length % 4 !== 0) normalized += "=";
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
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

function isEpubNavigationHref(href?: string): boolean {
  if (!href) return false;
  const lower = href.toLowerCase().split("#")[0];
  return /\bnav\.x?html?$/.test(lower)
    || /\btoc\.x?html?$/.test(lower)
    || /\bcontents?\.x?html?$/.test(lower)
    || /\btable-of-contents\.x?html?$/.test(lower);
}

function isGenericEpubChapterTitle(value?: string): boolean {
  const title = value?.trim();
  if (!title) return true;
  return /^(chapter|section|page)\s*[\d\s_.-]+$/i.test(title) || /^untitled$/i.test(title);
}

function readableTitleFromXhtml(xhtml: string, fallbackTitle: string, index: number): string {
  const titleMatch = /<title[^>]*>([\s\S]*?)<\/title>/i.exec(xhtml);
  const documentTitle = titleMatch?.[1]?.replace(/<[^>]+>/g, "").trim();
  if (documentTitle && !isGenericEpubChapterTitle(documentTitle)) return documentTitle;
  const headingTitle = (/<h1[^>]*>([\s\S]*?)<\/h1>/i.exec(xhtml)
    || /<h2[^>]*>([\s\S]*?)<\/h2>/i.exec(xhtml)
    || /<h3[^>]*>([\s\S]*?)<\/h3>/i.exec(xhtml))?.[1]?.replace(/<[^>]+>/g, "").trim();
  if (headingTitle) return headingTitle;
  const fallback = fallbackTitle.trim();
  if (fallback && !isGenericEpubChapterTitle(fallback)) return fallback;
  return `章节 ${index + 1}`;
}

function escapeAttribute(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function normalizeEpubBodyMarkup(body: string): string {
  return body
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<iframe[\s\S]*?<\/iframe>/gi, "")
    .replace(/\s(on\w+|style|class)=["'][\s\S]*?["']/gi, "")
    .replace(/\s(xml:)?lang=["']([^"']+)["']/gi, ' lang="$2"')
    .replace(/<a\b([^>]*?)href=["']javascript:[^"']*["']([^>]*)>/gi, "<a$1$2>")
    .replace(/<a\b([^>]*?)href=["'](https?:[^"']+)["']([^>]*)>/gi, '<a$1href="$2"$3 target="_blank" rel="noreferrer">')
    .replace(/<a\b([^>]*?)href=["'](?!https?:|data:|mailto:|tel:)([^"']+)["']([^>]*)>/gi, (_match, before, href, after) => {
      return `<a${before}data-reader-href="${escapeAttribute(href)}"${after}>`;
    })
    .replace(/<image\b/gi, "<img")
    .replace(/<\/image>/gi, "");
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

async function inlineEpubImages(book: Book, html: string, chapterPath: string): Promise<string> {
  const chapterDir = dirname(chapterPath);
  const matches = Array.from(html.matchAll(/<img\b[^>]*(?:src|href|xlink:href)=["']([^"']+)["'][^>]*>/gi));
  let nextHtml = html;
  for (const match of matches) {
    const originalTag = match[0];
    const source = match[1];
    if (/^(https?:|data:|#)/i.test(source)) continue;
    const resourcePath = resolvePath(chapterDir, source);
    try {
      const blob = await book.archive.getBlob(resourcePath);
      if (!blob || blob.size > EPUB_INLINE_IMAGE_MAX_BYTES) {
        const safeTag = originalTag
          .replace(/\s(?:src|href|xlink:href)=["'][^"']+["']/i, "")
          .replace(/<img\b/i, '<img alt="图片过大，已跳过"');
        nextHtml = nextHtml.replace(originalTag, safeTag);
        continue;
      }
      const data = await blobToBase64(blob);
      const dataUrl = `data:${mediaTypeFromPath(resourcePath)};base64,${data}`;
      const safeTag = originalTag
        .replace(/\s(?:src|href|xlink:href)=["'][^"']+["']/i, ` src="${dataUrl}"`)
        .replace(/<img\b(?![^>]*\salt=)/i, '<img alt=""')
        .replace(/<img\b(?![^>]*\sloading=)/i, '<img loading="lazy"');
      nextHtml = nextHtml.replace(originalTag, safeTag);
    } catch {
      // 忽略单张图片内联失败
    }
  }
  return nextHtml;
}

function skipEpubImagesForStability(html: string): string {
  return html.replace(/<img\b[^>]*>/gi, "");
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = reader.result as string;
      const base64 = result.split(",")[1] ?? "";
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

function extractBodyFromRenderedXhtml(xhtml: string): string {
  const bodyMatch = /<body[^>]*>([\s\S]*?)<\/body>/i.exec(xhtml);
  return bodyMatch?.[1]?.trim() ?? xhtml;
}

function flattenNavItems(items: NavItem[], level = 0): Array<{ href: string; title: string; level: number }> {
  const result: Array<{ href: string; title: string; level: number }> = [];
  for (const item of items) {
    if (!item.href) continue;
    result.push({ href: item.href.split("#")[0], title: item.label || item.id || "未命名", level });
    if (item.subitems?.length) result.push(...flattenNavItems(item.subitems, level + 1));
  }
  return result;
}

function stripBase64DataPrefix(value: string): string {
  return value.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
}

async function openEpubBook(content: string): Promise<Book> {
  const normalized = stripBase64DataPrefix(content);
  if (!normalized) throw new Error("EPUB 内容为空。");
  // 优先用 epubjs 原生 base64 解码，避免自定义 atob 在大文件或特殊字符上出错
  let book = ePub(normalized, { openAs: "base64" });
  try {
    await waitForEpubReady(book);
    return book;
  } catch (base64Error) {
    book.destroy();
    // 兜底：转成 ArrayBuffer 再按二进制打开
    const buffer = decodeBase64ToArrayBuffer(content);
    book = ePub(buffer, { openAs: "binary" });
    await waitForEpubReady(book);
    return book;
  }
}

async function waitForEpubReady(book: Book): Promise<void> {
  let timer = 0;
  try {
    await Promise.race([
      book.ready.then(() => undefined),
      new Promise<never>((_resolve, reject) => {
        timer = window.setTimeout(() => reject(new Error("EPUB 容器解析超时。")), EPUB_OPEN_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timer) window.clearTimeout(timer);
  }
}

async function readEpubCoverDataUrl(book: Book): Promise<string | undefined> {
  let coverUrl: string | undefined;
  try {
    coverUrl = await withEpubStepTimeout(book.coverUrl(), 4_000, "EPUB 封面读取超时。") ?? undefined;
    if (!coverUrl) return undefined;
    const response = await fetch(coverUrl);
    if (!response.ok) return undefined;
    const blob = await response.blob();
    if (!blob.size || blob.size > EPUB_IMPORT_COVER_MAX_BYTES || !blob.type.startsWith("image/")) return undefined;
    const base64 = await blobToBase64(blob);
    return base64 ? `data:${blob.type};base64,${base64}` : undefined;
  } catch {
    return undefined;
  } finally {
    if (coverUrl?.startsWith("blob:")) URL.revokeObjectURL(coverUrl);
  }
}

/**
 * 导入提交前的 EPUB 事务校验：不仅解析元数据，还实际读取一个 spine 章节。
 * 封面失败只降级，不影响正文导入；容器、spine 或章节读取失败则拒绝创建正式书籍记录。
 */
export async function inspectEpubForImport(content: string, fallbackTitle = "EPUB 书籍"): Promise<EpubImportInspection> {
  let book: Book | undefined;
  try {
    book = await openEpubBook(content);
    const structure = parseEpubStructureFromBook(book, fallbackTitle);
    let readableChapterFound = false;
    let lastChapterError: unknown;
    for (const entry of structure.readableSpineItems.slice(0, 5)) {
      try {
        const rendered = await withEpubStepTimeout(readEpubSectionMarkup(book, entry.item), 6_000, "EPUB 章节读取超时。");
        const body = normalizeEpubBodyMarkup(extractBodyFromRenderedXhtml(rendered));
        if (body.trim()) {
          readableChapterFound = true;
          break;
        }
      } catch (error) {
        lastChapterError = error;
      }
    }
    if (!readableChapterFound) {
      const detail = lastChapterError instanceof Error ? lastChapterError.message : "前几个 spine 章节均为空或不可读取";
      throw new Error(`EPUB 没有可验证的正文：${detail}`);
    }

    const metadata = book.packaging.metadata as typeof book.packaging.metadata & {
      creator?: string;
      description?: string;
      language?: string;
      publisher?: string;
    };
    const coverDataUrl = await readEpubCoverDataUrl(book);
    return {
      title: metadata.title?.trim() || structure.bookTitle || fallbackTitle,
      author: metadata.creator?.trim() || undefined,
      description: metadata.description?.trim() || undefined,
      language: metadata.language?.trim() || undefined,
      publisher: metadata.publisher?.trim() || undefined,
      coverDataUrl,
      toc: structure.toc.slice(0, TXT_TOC_LIMIT),
      totalChapters: structure.readableSpineItems.length
    };
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`EPUB 导入校验失败：${detail || "未知错误"}`);
  } finally {
    book?.destroy();
  }
}

function readableTitleForSection(section: ReturnType<Book["section"]>, index: number, navTitle = ""): string {
  const fallback = navTitle || section.href?.split("/").pop() || "";
  return readableTitleFromXhtml(section.output || "", fallback, index);
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
    toc: [{ id: "epub-start", title: "开始阅读", level: 1, index: 0 }],
    wordCount: fallback.replace(/\s/g, "").length,
    currentTocIndex: 0,
    totalChapters: 1
  };
}

function parseEpubStructureFromBook(book: Book, title: string): ParsedEpubStructure {
  const bookTitle = book.packaging.metadata?.title || title;
  const spineItems = (book.spine as unknown as { spineItems?: SpineItem[] }).spineItems ?? [];

  // 过滤非线性章节（如封面、版权页通常 linear="no"）和导航文件
  const readableSpineItems: ReadableSpineItem[] = [];
  for (let index = 0; index < spineItems.length; index += 1) {
    const item = spineItems[index];
    if (item.linear === "no") continue;
    if (isEpubNavigationHref(item.href)) continue;
    readableSpineItems.push({
      item: { index: item.index, href: item.href, linear: item.linear },
      readableIndex: readableSpineItems.length,
      title: ""
    });
  }

  if (!readableSpineItems.length) throw new Error("EPUB 没有可读取的章节正文。");

  // 目录优先从 epubjs 解析的 navigation 获取
  const navToc = book.navigation?.toc ?? [];
  const flatNavItems = flattenNavItems(navToc);
  const navTitleMap = new Map<string, string>();
  for (const item of flatNavItems) navTitleMap.set(item.href, item.title);

  // 更新 readableSpineItems 标题
  for (const entry of readableSpineItems) {
    const href = entry.item.href || "";
    const fileName = href.split("/").pop() ?? "";
    const navTitle = navTitleMap.get(href) ?? navTitleMap.get(fileName);
    entry.title = navTitle || `章节 ${entry.readableIndex + 1}`;
  }

  // 建立 toc，映射到 readableIndex，按 readableIndex 去重
  const toc: MobileReaderDocument["toc"] = [];
  const seenIndexes = new Set<number>();
  const hrefToReadableIndex = new Map<string, number>();
  for (const entry of readableSpineItems) {
    const href = entry.item.href || "";
    hrefToReadableIndex.set(href, entry.readableIndex);
    const fileName = href.split("/").pop();
    if (fileName) hrefToReadableIndex.set(fileName, entry.readableIndex);
  }

  for (const item of flatNavItems) {
    const readableIndex = hrefToReadableIndex.get(item.href);
    if (readableIndex === undefined || seenIndexes.has(readableIndex)) continue;
    seenIndexes.add(readableIndex);
    toc.push({
      id: `epub-chapter-${readableIndex}`,
      title: item.title,
      level: item.level + 1,
      index: readableIndex,
      href: item.href
    });
  }

  // 没有 nav 时回退到 spine 列表
  if (!toc.length) {
    for (const entry of readableSpineItems) {
      toc.push({
        id: `epub-chapter-${entry.readableIndex}`,
        title: entry.title,
        level: 1,
        index: entry.readableIndex,
        href: entry.item.href
      });
    }
  }

  return { bookTitle, readableSpineItems, toc };
}

export async function parseEpubDocumentStructure(content: string, title = "EPUB 书籍"): Promise<MobileReaderDocument> {
  let book: Book | undefined;
  try {
    const cacheKey = epubCacheKey(content);
    let structure = epubStructureCache.get(cacheKey);
    book = await openEpubBook(content);
    if (!structure) {
      structure = parseEpubStructureFromBook(book, title);
      rememberParsedEpubStructure(cacheKey, structure);
    }
    const { bookTitle, readableSpineItems, toc } = structure;
    const tocLimit = Math.min(toc.length, TXT_TOC_LIMIT);
    book.destroy();
    return {
      title: bookTitle,
      format: "epub",
      html: "",
      plainText: "",
      toc: toc.slice(0, tocLimit),
      wordCount: 0,
      currentTocIndex: 0,
      totalChapters: readableSpineItems.length
    };
  } catch (error) {
    book?.destroy();
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`EPUB 解析失败：${detail || "未知错误"}`);
  }
}

export async function renderEpubDocument(content: string, title = "EPUB 书籍", options: MobileReaderRenderOptions = {}): Promise<MobileReaderDocument> {
  let book: Book | undefined;
  try {
    const cacheKey = epubCacheKey(content);
    let structure = epubStructureCache.get(cacheKey);
    book = await openEpubBook(content);
    if (!structure) {
      structure = parseEpubStructureFromBook(book, title);
      rememberParsedEpubStructure(cacheKey, structure);
    }
    const { bookTitle, readableSpineItems, toc } = structure;
    const tocLimit = Math.min(toc.length, TXT_TOC_LIMIT);
    const limitedToc = toc.slice(0, tocLimit);

    const renderChapter = async (entry: ReadableSpineItem): Promise<{ html: string; title: string; plainText: string }> => {
      const section = resolveEpubSection(book!, entry.item);
      if (!section) throw new Error(`EPUB 章节文件缺失：${entry.item.href || "unknown"}`);
      const rendered = await readEpubSectionMarkup(book!, entry.item);
      const body = extractBodyFromRenderedXhtml(rendered);
      const normalizedBody = normalizeEpubBodyMarkup(body);
      const inlineImages = options.inlineEpubImages === true || (options.inlineEpubImages === undefined && EPUB_INLINE_IMAGES_BY_DEFAULT);
      const chapterHref = entry.item.href || "";
      const processedBody = inlineImages
        ? await inlineEpubImages(book!, normalizedBody, chapterHref)
        : skipEpubImagesForStability(normalizedBody);
      const bodyHtml = processedBody.trim()
        ? processedBody
        : '<p class="epub-empty-chapter">本章无正文内容（可能是版权页或扉页）。</p>';
      const chapterTitle = readableTitleForSection(section, entry.readableIndex, entry.title);
      const fullHtml = `<section class="epub-chapter epub-publisher-flow" id="epub-chapter-${entry.readableIndex}" data-chapter-index="${entry.readableIndex + 1}"><h2>${escapeHtml(chapterTitle)}</h2>${bodyHtml}</section>`;
      const plainText = plainTextFromHtml(fullHtml);
      return { html: fullHtml, title: chapterTitle, plainText };
    };

    const renderAll = options.renderAllChapters ?? false;
    if (renderAll && tocLimit <= RENDER_ALL_MAX_CHAPTERS) {
      const chapterHtmls: string[] = [];
      let totalWordCount = 0;
      let totalHtmlBytes = 0;
      for (const entry of readableSpineItems.slice(0, tocLimit)) {
        const chapter = await renderChapter(entry);
        totalHtmlBytes += chapter.html.length;
        if (totalHtmlBytes > RENDER_ALL_MAX_HTML_BYTES) {
          chapterHtmls.length = 0;
          break;
        }
        chapterHtmls.push(chapter.html);
        totalWordCount += chapter.plainText.replace(/\s/g, "").length;
      }
      if (chapterHtmls.length === tocLimit) {
        const allHtml = chapterHtmls.join("\n");
        book.destroy();
        return {
          title: bookTitle,
          format: "epub",
          html: allHtml,
          plainText: plainTextFromHtml(allHtml),
          toc: limitedToc,
          wordCount: totalWordCount,
          currentTocIndex: 0,
          totalChapters: readableSpineItems.length,
          renderAllChapters: true
        };
      }
    }

    const currentIndex = clampReaderIndex(options.chapterIndex, readableSpineItems.length);
    const currentItem = readableSpineItems[currentIndex];
    const chapter = await renderChapter(currentItem);
    const tocIndex = limitedToc.findIndex((item) => item.index === currentItem.readableIndex);
    if (tocIndex >= 0) limitedToc[tocIndex] = { ...limitedToc[tocIndex], title: chapter.title };
    book.destroy();
    return {
      title: bookTitle,
      format: "epub",
      html: chapter.html,
      plainText: chapter.plainText,
      toc: limitedToc,
      wordCount: chapter.plainText.replace(/\s/g, "").length,
      currentTocIndex: currentIndex,
      totalChapters: readableSpineItems.length
    };
  } catch (error) {
    book?.destroy();
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`EPUB 解析失败：${detail || "未知错误"}`);
  }
}
