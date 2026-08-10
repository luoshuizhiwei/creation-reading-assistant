import { readFile } from "node:fs/promises";
import path from "node:path";
import jschardet from "jschardet";
import * as iconv from "iconv-lite";
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

function countWords(text: string): number {
  return text.replace(/\s/g, "").length;
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
  const format: DraftImportFormat = options.format ?? (extension === ".md" || extension === ".markdown" ? "markdown" : "txt");
  const text = await readTextFile(filePath);
  const parsed = format === "markdown" ? parseMarkdown(text) : parseTxt(text);
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
