/**
 * 阅读器数据层（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：文本文件编码嗅探读取与体积上限、novel-workbench-epub 自定义协议的资源解析、
 * 阅读器打开载荷（正文 / EPUB / 进度 / 设置）的组装与位置保存。
 * 行为逐字保持，仅把顶层声明导出供主进程入口与 IPC 层使用。
 */
import { protocol } from "electron";
import { existsSync } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import path from "node:path";
import jschardet from "jschardet";
import iconv from "iconv-lite";
import type {
  ReaderBookPayload,
  ReaderEpubPayload,
  ReaderSettings,
  ReadingLocation,
  ReadingProgress,
  SaveProgressInput
} from "../../src/types/library";
import {
  EPUB_PROTOCOL_SCHEME,
  appLibraryFilesRoot,
  bookIdFromEpubProtocolUrl,
  epubUrlForBook,
  isInsidePath,
  writeLog
} from "./storage";
import { readLibraryIndex } from "./library-store";
import { getAppSettings, updateAppSettings } from "./settings-store";
import { getProgress, saveProgress } from "./progress-store";

export const MAX_SEARCH_TEXT_FILE_BYTES = 5 * 1024 * 1024;
export const MAX_READER_TEXT_FILE_BYTES = 50 * 1024 * 1024;

export async function readTextFile(filePath: string): Promise<string> {
  const buffer = await readFile(filePath);

  // 用前 64KB 检测编码
  const sampleBuffer = buffer.subarray(0, Math.min(buffer.length, 65536));
  const detection = jschardet.detect(sampleBuffer);

  let text: string;
  const encoding = detection.encoding?.toLowerCase() ?? "";

  if (encoding && encoding !== "utf-8" && encoding !== "ascii" && detection.confidence > 0.5) {
    // 非 UTF-8 编码，使用 iconv-lite 解码
    console.log(`[readTextFile] Detected non-UTF-8 encoding: ${detection.encoding} (confidence: ${detection.confidence.toFixed(2)})`);
    text = iconv.decode(buffer, detection.encoding);
  } else {
    text = buffer.toString("utf-8");
  }

  // 去除 BOM
  return text.replace(/^\uFEFF/, "");
}

export async function readTextFileIfWithinLimit(filePath: string, maxBytes = MAX_SEARCH_TEXT_FILE_BYTES): Promise<string> {
  try {
    const info = await stat(filePath);
    if (info.size > maxBytes) {
      await writeLog("warn", "Search skipped large text file.", { filePath, size: info.size, maxBytes });
      return "";
    }
    return await readTextFile(filePath);
  } catch {
    return "";
  }
}

export function registerEpubProtocol(): void {
  protocol.registerFileProtocol(EPUB_PROTOCOL_SCHEME, (request, callback) => {
    void (async () => {
      const bookId = bookIdFromEpubProtocolUrl(request.url);
      if (!bookId) {
        callback({ error: -6 });
        return;
      }
      const book = (await readLibraryIndex()).find((item) => item.id === bookId);
      if (!book || book.format !== "epub") {
        callback({ error: -6 });
        return;
      }
      const libraryRoot = path.resolve(appLibraryFilesRoot());
      const filePath = path.resolve(book.filePath);
      if (!isInsidePath(libraryRoot, filePath) || !existsSync(filePath)) {
        callback({ error: -6 });
        return;
      }
      callback({ path: filePath });
    })().catch(() => callback({ error: -2 }));
  });
}

export async function getReaderSettings(): Promise<ReaderSettings> {
  return (await getAppSettings()).reader;
}

export async function updateReaderSettings(patch: Partial<ReaderSettings>): Promise<ReaderSettings> {
  return (await updateAppSettings({ reader: patch })).reader;
}

export async function openBook(bookId: string): Promise<ReaderBookPayload> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) throw new Error("Book not found.");
  if (book.format === "epub") throw new Error("Use reader:openEpub for EPUB books.");
  const fileInfo = await stat(book.filePath);
  if (fileInfo.size > MAX_READER_TEXT_FILE_BYTES) {
    await writeLog("warn", "Text reader refused oversized file.", { bookId, filePath: book.filePath, size: fileInfo.size, maxBytes: MAX_READER_TEXT_FILE_BYTES });
    throw new Error("文件过大，当前桌面阅读器单本 TXT/Markdown 建议不超过 50MB；请分割后再导入。");
  }
  await writeLog("info", "Text reader opened.", { bookId, format: book.format });
  return {
    book,
    content: await readTextFile(book.filePath),
    progress: await getProgress(bookId),
    settings: await getReaderSettings()
  };
}

export async function openEpub(bookId: string): Promise<ReaderEpubPayload> {
  const book = (await readLibraryIndex()).find((item) => item.id === bookId);
  if (!book) throw new Error("Book not found.");
  if (book.format !== "epub") throw new Error("Book is not EPUB.");
  await writeLog("info", "EPUB reader opened.", { bookId });
  return {
    book,
    epubUrl: epubUrlForBook(book.id),
    progress: await getProgress(bookId),
    settings: await getReaderSettings(),
    toc: book.epub?.toc ?? []
  };
}

export async function saveEpubLocation(input: SaveProgressInput): Promise<ReadingProgress> {
  return saveProgress({
    bookId: input.bookId,
    location: {
      ...input.location,
      format: "epub",
      mode: "epub-cfi"
    }
  });
}

export async function getEpubLocation(bookId: string): Promise<ReadingLocation | undefined> {
  return (await getProgress(bookId))?.currentLocation;
}
