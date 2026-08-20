import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import jschardet from "jschardet";
import * as iconv from "iconv-lite";
import mammoth from "mammoth";
import { DocxPreflightError, preflightDocxArchive } from "./docx-preflight";
import type {
  DraftImportChapterInput,
  DraftImportFormat,
  DraftImportPreview,
  DraftImportPreviewChapter,
  DraftImportPreviewVolume,
  DraftImportVolumeInput
} from "../../../src/types/creation";

export type { DraftImportFormat, DraftImportPreview, DraftImportPreviewChapter, DraftImportPreviewVolume } from "../../../src/types/creation";

/** 章节标题识别（TXT）：第X章/节/回/卷，Chapter N，序章/楔子/尾声/番外。 */
const CHAPTER_HEADING = /^\s*(第[一二三四五六七八九十百千万零〇两0-9０-９]+[章节回]|序章|楔子|尾声|番外|Chapter\s+\d+|CHAPTER\s+\d+)\s*[:：]?\s*(.*)$/i;

/** DOCX 输入文件大小上限（20MB）。 */
const MAX_DOCX_INPUT_BYTES = 20 * 1024 * 1024;
/** DOCX 解析后文本总量上限（与导入事务的 5000 万字符总量一致）。 */
const MAX_DOCX_TEXT_CHARS = 50_000_000;

function countWords(text: string): number {
  return text.replace(/\s/g, "").length;
}

/** HTML 实体解码（先数字实体，再命名实体，&amp; 最后处理避免二次解码）。 */
function decodeHtmlEntities(input: string): string {
  return input
    .replace(/&#x([0-9a-f]+);/gi, (_match, hex: string) => {
      try {
        return String.fromCodePoint(parseInt(hex, 16));
      } catch {
        return "";
      }
    })
    .replace(/&#(\d+);/g, (_match, dec: string) => {
      try {
        return String.fromCodePoint(Number(dec));
      } catch {
        return "";
      }
    })
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&");
}

interface DocxBlock {
  kind: "h1" | "h2" | "p";
  text: string;
}

/** 从 mammoth 输出的 HTML 中提取块级内容：h1/h2 为标题，其余块并入段落；跳过表格/图片。 */
function extractDocxBlocks(html: string): { blocks: DocxBlock[]; skippedRich: boolean } {
  const blocks: DocxBlock[] = [];
  const kindStack: Array<"h1" | "h2" | "p"> = [];
  let currentText = "";
  let inTable = 0;
  let skippedRich = false;

  const flush = (): void => {
    const text = decodeHtmlEntities(currentText)
      .replace(/\u00a0/g, " ")
      .replace(/[ \t]+/g, " ")
      .replace(/\s*\n\s*/g, " ")
      .trim()
      .replace(/\u2028/g, "\n");
    if (text) blocks.push({ kind: kindStack[kindStack.length - 1] ?? "p", text });
    currentText = "";
  };

  const tokenPattern = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>"']|"[^"]*"|'[^']*')*?)\s*\/?>|([^<]+)/g;
  const BLOCK_TAGS = new Set(["h1", "h2", "h3", "h4", "h5", "h6", "p", "blockquote", "li", "pre", "section", "div"]);
  let match: RegExpExecArray | null;
  while ((match = tokenPattern.exec(html)) !== null) {
    if (match[4] !== undefined) {
      if (inTable === 0) currentText += match[4];
      continue;
    }
    const closing = match[1] === "/";
    const tag = (match[2] ?? "").toLowerCase();
    if (!closing && tag === "table") {
      inTable += 1;
      skippedRich = true;
      continue;
    }
    if (closing && tag === "table") {
      if (inTable > 0) inTable -= 1;
      continue;
    }
    if (!closing && tag === "img") {
      skippedRich = true;
      continue;
    }
    if (!closing && tag === "br" && inTable === 0) {
      currentText += "\u2028";
      continue;
    }
    if (BLOCK_TAGS.has(tag)) {
      if (closing) {
        flush();
        kindStack.pop();
      } else {
        flush();
        const kind = tag === "h1" ? "h1" : tag === "h2" ? "h2" : "p";
        kindStack.push(kind);
      }
    }
  }
  flush();
  return { blocks, skippedRich };
}

/** 按 Heading 规则组装卷章：H1+H2 → 卷/章；只有 H1 或 H2 → 全部为章（统一「正文」卷）；无标题回退 TXT 识别。 */
function parseDocxBlocks(
  blocks: DocxBlock[],
  warnings: string[]
): { volumes: DraftImportPreviewVolume[] } {
  const hasH1 = blocks.some((block) => block.kind === "h1");
  const hasH2 = blocks.some((block) => block.kind === "h2");

  if (!hasH1 && !hasH2) {
    warnings.push("未识别到 Word 标题样式，已按「第X章」文本识别章节；无结构时整篇作为单章。");
    const text = blocks.map((block) => block.text).join("\n");
    return parseTxt(text);
  }

  interface ChapterBuilder {
    title: string;
    bodyLines: string[];
  }
  interface VolumeBuilder {
    title: string;
    chapters: ChapterBuilder[];
  }
  const volumes: VolumeBuilder[] = [];
  let currentVolume: VolumeBuilder | undefined;
  let currentChapter: ChapterBuilder | undefined;

  const ensureVolume = (title: string): void => {
    currentVolume = { title, chapters: [] };
    volumes.push(currentVolume);
  };
  const ensureChapter = (title: string): void => {
    currentChapter = { title, bodyLines: [] };
    currentVolume!.chapters.push(currentChapter);
  };
  const pushParagraph = (text: string): void => {
    if (!currentVolume) ensureVolume("正文");
    if (!currentChapter) ensureChapter("未命名章节");
    currentChapter!.bodyLines.push(text);
  };

  if (hasH1 && hasH2) {
    warnings.push("识别到一级与二级标题：一级标题作为卷，二级标题作为章。");
    for (const block of blocks) {
      if (block.kind === "h1") {
        ensureVolume(block.text);
      } else if (block.kind === "h2") {
        if (!currentVolume) ensureVolume("正文");
        ensureChapter(block.text);
      } else {
        pushParagraph(block.text);
      }
    }
  } else {
    warnings.push("仅识别到单级标题，全部作为章并归入「正文」卷。");
    ensureVolume("正文");
    for (const block of blocks) {
      if (block.kind === "h1" || block.kind === "h2") {
        ensureChapter(block.text);
      } else {
        pushParagraph(block.text);
      }
    }
  }

  return {
    volumes: volumes.map((volume) => ({
      title: volume.title,
      chapters: volume.chapters.map((chapter) => {
        const body = chapter.bodyLines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
        return { title: chapter.title, body, wordCount: countWords(body) };
      })
    }))
  };
}

/** 解析 .docx（mammoth）：损坏/加密给中文可读错误；限制输入大小与文本规模。 */
async function parseDocx(filePath: string): Promise<{ volumes: DraftImportPreviewVolume[]; warnings: string[] }> {
  const info = await stat(filePath);
  if (info.size > MAX_DOCX_INPUT_BYTES) {
    throw new Error("DOCX 文件过大，无法导入（最大 20MB）。");
  }
  const buffer = await readFile(filePath);
  try {
    await preflightDocxArchive(buffer);
  } catch (error) {
    if (error instanceof DocxPreflightError) throw error;
    throw new Error("文件损坏或不是有效的 .docx 文档。");
  }
  let html: string;
  let mammothWarnings: string[] = [];
  try {
    const result = await mammoth.convertToHtml({ buffer });
    html = result.value;
    mammothWarnings = result.messages
      .filter((message) => message.type === "warning")
      .map((message) => message.message)
      .slice(0, 5);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (/password|encrypted/i.test(message)) {
      throw new Error("文档已加密，无法导入。请先解除密码保护。");
    }
    throw new Error("文件损坏或不是有效的 .docx 文档。");
  }
  const { blocks, skippedRich } = extractDocxBlocks(html);
  const totalChars = blocks.reduce((sum, block) => sum + block.text.length, 0);
  if (totalChars === 0) {
    throw new Error("DOCX 文档没有可导入的正文文本。");
  }
  if (totalChars > MAX_DOCX_TEXT_CHARS) {
    throw new Error("文档文本量过大，无法导入（超过 5000 万字符）。");
  }
  const warnings: string[] = [...mammothWarnings];
  if (skippedRich) warnings.push("已跳过表格、图片等富内容，仅保留文本与标题结构。");
  const parsed = parseDocxBlocks(blocks, warnings);
  return { volumes: parsed.volumes, warnings };
}

/** 读取并解码文本文件（UTF-8 / GBK 等，jschardet 探测）。 */
export async function readTextFile(filePath: string): Promise<string> {
  const buffer = await readFile(filePath);
  const detected = jschardet.detect(buffer);
  const encoding = detected?.encoding?.toLowerCase() ?? "utf-8";
  const normalized =
    encoding === "utf-8" || encoding === "utf8" ? "utf-8" : encoding === "gb2312" || encoding === "gbk" ? "gbk" : encoding;
  try {
    return iconv.decode(buffer, normalized);
  } catch {
    return buffer.toString("utf8");
  }
}

function trimLine(line: string): string {
  return line.replace(/^\uFEFF/, "").trim();
}

/** TXT：按章节标题行切分；未识别到标题时整篇作为单章。 */
function parseTxt(text: string): { volumes: DraftImportPreviewVolume[]; warnings: string[] } {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const warnings: string[] = [];
  const chapters: Array<{ title: string; body: string[] }> = [];
  let current: { title: string; body: string[] } | undefined;
  for (const rawLine of lines) {
    const line = trimLine(rawLine);
    if (!line) {
      current?.body.push("");
      continue;
    }
    const match = CHAPTER_HEADING.exec(line);
    if (match) {
      const heading = match[1]!.trim();
      const subtitle = match[2]?.trim() ?? "";
      current = { title: subtitle ? `${heading} ${subtitle}` : heading, body: [] };
      chapters.push(current);
    } else {
      (current ??= { title: "", body: [] }).body.push(line);
    }
  }
  if (chapters.length === 0) {
    warnings.push("未识别到章节标题，整篇将作为单章导入。");
    chapters.push({ title: "全文", body: text.replace(/\r\n/g, "\n").split("\n").map(trimLine).filter(Boolean) });
  }
  return {
    volumes: [
      {
        title: "正文",
        chapters: chapters.map((chapter) => {
          const body = chapter.body.join("\n").replace(/\n{3,}/g, "\n\n").trim();
          return { title: chapter.title || "未命名章节", body, wordCount: countWords(body) };
        })
      }
    ],
    warnings
  };
}

/** Markdown：`#` 一级为卷、`##` 二级为章、`###` 三级并入当前章正文；无标题时单卷单章。 */
function parseMarkdown(text: string): { volumes: DraftImportPreviewVolume[]; warnings: string[] } {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const warnings: string[] = [];
  interface MarkdownChapter {
    title: string;
    bodyLines: string[];
  }
  interface MarkdownVolume {
    title: string;
    chapters: MarkdownChapter[];
  }
  const volumes: MarkdownVolume[] = [];
  let currentVolume: MarkdownVolume | undefined;
  let currentChapter: MarkdownChapter | undefined;

  const ensureVolume = (title: string): void => {
    currentVolume = { title, chapters: [] };
    volumes.push(currentVolume);
  };
  const ensureChapter = (title: string): void => {
    currentChapter = { title, bodyLines: [] };
    currentVolume!.chapters.push(currentChapter);
  };

  for (const rawLine of lines) {
    const line = trimLine(rawLine);
    if (!line) {
      if (currentChapter && currentChapter.bodyLines.length > 0) currentChapter.bodyLines.push("");
      continue;
    }
    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      const level = heading[1]!.length;
      const title = heading[2]!.trim() || "未命名";
      if (level === 1) {
        ensureVolume(title);
      } else if (level === 2) {
        if (!currentVolume) ensureVolume("正文");
        ensureChapter(title);
      } else {
        if (!currentVolume) ensureVolume("正文");
        if (!currentChapter) ensureChapter("未命名章节");
        currentChapter!.bodyLines.push(`### ${title}`);
      }
      continue;
    }
    if (!currentVolume) ensureVolume("正文");
    if (!currentChapter) ensureChapter("未命名章节");
    currentChapter!.bodyLines.push(line);
  }

  if (volumes.length === 0) {
    warnings.push("未识别到 Markdown 标题，整篇将作为单章导入。");
    volumes.push({
      title: "正文",
      chapters: [{ title: "全文", bodyLines: text.replace(/\r\n/g, "\n").split("\n").map(trimLine).filter(Boolean) }]
    });
  }

  const result: DraftImportPreviewVolume[] = volumes.map((volume) => ({
    title: volume.title,
    chapters: volume.chapters.map((chapter) => {
      const body = chapter.bodyLines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
      return { title: chapter.title, body, wordCount: countWords(body) };
    })
  }));
  if (result.every((volume) => volume.chapters.length === 1 && volume.chapters[0]!.body.length === 0)) {
    warnings.push("未识别到标题结构，正文将按单一场景导入。");
  }
  return { volumes: result, warnings };
}

export async function previewLegacyDraft(options: { filePath: string; format?: DraftImportFormat }): Promise<DraftImportPreview> {
  if (!options || typeof options.filePath !== "string" || !options.filePath.trim()) {
    throw new Error("导入文件路径不能为空。");
  }
  const filePath = options.filePath.trim();
  const extension = path.extname(filePath).toLowerCase();
  if (extension === ".doc") {
    throw new Error("仅支持 .docx 格式，旧版 .doc 不受支持。请先在 Word 中另存为 .docx。");
  }
  if (![".txt", ".md", ".markdown", ".docx"].includes(extension)) {
    throw new Error("不支持该文件格式。请选择 TXT、Markdown 或 DOCX 旧稿。");
  }
  const format: DraftImportFormat = options.format ?? (
    extension === ".md" || extension === ".markdown" ? "markdown" :
    extension === ".docx" ? "docx" : "txt"
  );
  if (format === "docx" && extension !== ".docx") {
    throw new Error("仅支持 .docx 格式，旧版 .doc 不受支持。");
  }
  let parsed: { volumes: DraftImportPreviewVolume[]; warnings: string[] };
  if (format === "docx") {
    parsed = await parseDocx(filePath);
  } else {
    const text = await readTextFile(filePath);
    parsed = format === "markdown" ? parseMarkdown(text) : parseTxt(text);
  }
  const fileName = path.basename(filePath);
  const projectTitle = fileName.replace(/\.[^.]+$/, "").trim() || "导入作品";
  const totalChapters = parsed.volumes.reduce((sum, volume) => sum + volume.chapters.length, 0);
  const totalWords = parsed.volumes.reduce(
    (sum, volume) => sum + volume.chapters.reduce((acc, chapter) => acc + chapter.wordCount, 0),
    0
  );
  return {
    format,
    fileName,
    projectTitle,
    volumes: parsed.volumes,
    totalChapters,
    totalWords,
    warnings: parsed.warnings
  };
}
