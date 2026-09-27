/**
 * 馆藏索引数据层（从 electron/main/index.ts 拆出，纯移动式重构）。
 *
 * 职责：library.json 与 EPUB 检索索引文件的读写、单条图书记录的结构规范化、
 * 内容哈希与重复导入判定。行为逐字保持，仅把顶层声明导出供主进程入口与同步层使用。
 */
import { createReadStream, existsSync } from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import type {
  BookFormat,
  EpubSearchIndex,
  EpubSearchIndexItem,
  LibraryBook,
  TxtTocOverrides
} from "../../src/types/library";
import {
  epubSearchIndexPath,
  isRecord,
  libraryPath,
  now,
  optionalString,
  readJson,
  withSyncMetadata,
  writeJson,
  writeLog
} from "./storage";


/** 严格解析用户手动修正的 TXT 章节表；非法/空数据一律丢弃，防止脏数据进索引。 */
export function normalizeTxtTocOverrides(value: unknown): TxtTocOverrides | undefined {
  if (!isRecord(value) || value.version !== 1 || !Array.isArray(value.chapters)) return undefined;
  const chapters: Array<{ title: string; startIndex: number }> = [];
  let prevStart = -1;
  for (const raw of value.chapters) {
    if (!isRecord(raw)) continue;
    const startIndex = typeof raw.startIndex === "number" && Number.isInteger(raw.startIndex) && raw.startIndex >= 0 ? raw.startIndex : -1;
    if (startIndex <= prevStart) continue;
    prevStart = startIndex;
    chapters.push({ title: optionalString(raw.title) ?? "", startIndex });
  }
  return chapters.length > 0 ? { version: 1, chapters } : undefined;
}

export function normalizeLibraryBook(value: unknown): LibraryBook | null {
  if (!isRecord(value)) return null;
  const id = optionalString(value.id);
  const filePath = optionalString(value.filePath);
  const title = optionalString(value.title);
  if (!id || !filePath || !title) return null;
  const format = normalizeBookFormat(value.format, filePath);
  const importedAt = optionalString(value.importedAt) ?? now();
  return withSyncMetadata(
    {
      id,
      title,
      filePath,
      originalPath: optionalString(value.originalPath),
      originalFileName: optionalString(value.originalFileName),
      originalFilePath: optionalString(value.originalFilePath),
      format,
      importedAt,
      updatedAt: optionalString(value.updatedAt) ?? importedAt,
      size: typeof value.size === "number" ? Math.max(0, value.size) : 0,
      contentHash: optionalString(value.contentHash),
      duplicateIndex: typeof value.duplicateIndex === "number" ? Math.max(1, value.duplicateIndex) : undefined,
      importLabel: optionalString(value.importLabel),
      author: optionalString(value.author),
      description: optionalString(value.description),
      language: optionalString(value.language),
      publisher: optionalString(value.publisher),
      coverPath: optionalString(value.coverPath),
      epub: isRecord(value.epub) ? (value.epub as LibraryBook["epub"]) : undefined,
      text: isRecord(value.text)
        ? {
            tocOverrides: normalizeTxtTocOverrides(value.text.tocOverrides)
          }
        : undefined,
      revision: typeof value.revision === "number" ? value.revision : undefined,
      deviceId: optionalString(value.deviceId),
      deletedAt: optionalString(value.deletedAt)
    },
    importedAt
  );
}

export async function readLibraryIndex(options: { includeDeleted?: boolean } = {}): Promise<LibraryBook[]> {
  const data = await readJson<{ books: unknown[] }>(libraryPath(), { books: [] });
  const books = Array.isArray(data.books) ? data.books : [];
  return books.map(normalizeLibraryBook).filter((book): book is LibraryBook => Boolean(book)).filter((book) => options.includeDeleted || !book.deletedAt);
}

export async function writeEpubSearchIndex(bookId: string, items: EpubSearchIndexItem[]): Promise<string> {
  const targetPath = epubSearchIndexPath(bookId);
  const index: EpubSearchIndex = {
    version: 1,
    bookId,
    updatedAt: now(),
    items
  };
  await writeJson(targetPath, index);
  return targetPath;
}

export async function readEpubSearchIndex(bookId: string): Promise<EpubSearchIndex | undefined> {
  // First check if the file exists at all — "not built yet" is not an error
  const indexPath = epubSearchIndexPath(bookId);
  if (!existsSync(indexPath)) return undefined;

  try {
    const index = await readJson<unknown>(indexPath, undefined);
    if (index === undefined) return undefined;
    if (!isRecord(index)) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "not-an-object" });
      return undefined;
    }
    if (index.version !== 1) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "unsupported-version" });
      return undefined;
    }
    if (index.bookId !== bookId) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "book-id-mismatch" });
      return undefined;
    }
    if (!Array.isArray(index.items)) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "items-not-array" });
      return undefined;
    }

    const items = index.items;
    const hasInvalidItem = items.some(
      (item) =>
        !isRecord(item) ||
        typeof item.id !== "string" ||
        typeof item.title !== "string" ||
        typeof item.href !== "string" ||
        typeof item.text !== "string"
    );
    if (hasInvalidItem) {
      await writeLog("warn", "EPUB search index ignored.", { bookId, reason: "invalid-item-shape" });
      return undefined;
    }

    return {
      version: 1,
      bookId,
      updatedAt: typeof index.updatedAt === "string" ? index.updatedAt : now(),
      items: items as EpubSearchIndexItem[]
    };
  } catch (error) {
    const isNotFound = error instanceof Error && (error as NodeJS.ErrnoException).code === "ENOENT";
    if (isNotFound) {
      // File disappeared between existsSync check and read — treat as not indexed
      return undefined;
    }
    // Actual data format / IO error — log as warning
    await writeLog("warn", "EPUB search index ignored.", {
      bookId,
      reason: "read-failed",
      error: error instanceof Error ? error.message : String(error)
    });
    return undefined;
  }
}

export async function writeLibraryIndex(books: LibraryBook[]): Promise<void> {
  await writeJson(libraryPath(), { books });
}

export function importDateLabel(date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${month}-${day}`;
}

export async function contentHash(filePath: string): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const hash = crypto.createHash("sha256");
    const stream = createReadStream(filePath);
    stream.on("data", (chunk) => hash.update(chunk));
    stream.on("end", () => resolve(hash.digest("hex")));
    stream.on("error", reject);
  });
}

export function duplicateImportInfo(existingBooks: LibraryBook[], hash: string): Pick<LibraryBook, "contentHash" | "duplicateIndex" | "importLabel"> {
  const duplicates = existingBooks.filter((book) => book.contentHash === hash);
  const duplicateIndex = duplicates.length + 1;
  return {
    contentHash: hash,
    duplicateIndex,
    importLabel: duplicateIndex > 1 ? `重复导入 #${duplicateIndex}` : `导入于 ${importDateLabel()}`
  };
}

export function extractTextBookMetadata(content: string, fallbackTitle: string): { title: string; author?: string } {
  const lines = content
    .replace(/^\uFEFF/, "")
    .split(/\r?\n/)
    .slice(0, 80)
    .map((line) => line.trim())
    .filter(Boolean);
  let title = fallbackTitle;
  let author: string | undefined;

  for (const line of lines) {
    const titleMatch = line.match(/^(?:书名|标题|Title)\s*[:：]\s*(.+)$/i);
    if (titleMatch?.[1]?.trim()) title = titleMatch[1].trim();
    const authorMatch = line.match(/^(?:作者|Author)\s*[:：]\s*(.+)$/i) ?? line.match(/^by\s+(.+)$/i);
    if (authorMatch?.[1]?.trim()) {
      author = authorMatch[1].replace(/^[:：]\s*/, "").trim();
      break;
    }
  }

  return { title, author };
}


export function normalizeBookFormat(value: unknown, filePath?: string): BookFormat {
  if (value === "txt" || value === "md" || value === "epub") return value;
  const ext = filePath ? path.extname(filePath).toLowerCase() : "";
  if (ext === ".epub") return "epub";
  if (ext === ".md" || ext === ".markdown") return "md";
  return "txt";
}
