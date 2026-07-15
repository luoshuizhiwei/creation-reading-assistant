import type { BookFormat } from "../../../src/types/library";
import {
  beginPendingMobileBookWrite,
  completePendingMobileBookWrite,
  deleteMobileBookFile,
  getMobileBookStoragePath,
  recoverPendingMobileBookWrites,
  saveMobileBookBlob,
  saveMobileBookFile
} from "../storage/mobile-files";
import type { ImportedMobileBook, MobileBook, MobileSnapshot } from "../types/mobile";
import { inspectEpubForImport } from "../reader/mobile-reader-epubjs";
import { clearContinueRemoval } from "../features/shelf/continue-removal";
import {
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  getBaseFileName,
  getMobileBookFileExtension,
  getMobileDeviceId,
  getStoredBookFileName,
  isSupportedMobileBookFileName,
  nowIso,
  loadMobileSnapshot,
  saveMobileSnapshot
} from "./mobile-storage-core";

const MOBILE_READER_PREVIEW_CHARS = 64 * 1024;

function detectTitleFromFileName(fileName: string): string {
  return getBaseFileName(fileName).replace(/\.(txt|md|markdown|epub)$/i, "").replace(/[_-]+/g, " ").trim() || "未命名书籍";
}

function detectAuthor(content: string): string | undefined {
  const head = content.split(/\r?\n/).slice(0, 40).join("\n");
  const match = /(?:作者|Author)\s*[:：]\s*([^\n\r]{1,40})/i.exec(head) ?? /\bby\s+([^\n\r]{1,40})/i.exec(head);
  return match?.[1]?.trim();
}

function createReaderPreview(format: BookFormat, content: string): string | undefined {
  if (format === "epub") return undefined;
  const normalized = content.trim();
  if (!normalized) return undefined;
  return normalized.slice(0, MOBILE_READER_PREVIEW_CHARS);
}

export async function hashText(content: string): Promise<string> {
  const bytes = new TextEncoder().encode(content);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export async function createImportedMobileBook(fileName: string, content: string, size: number): Promise<ImportedMobileBook> {
  if (!isSupportedMobileBookFileName(fileName) || size <= 0) {
    throw new Error("unsupported-mobile-book-file");
  }
  const extension = getMobileBookFileExtension(fileName);
  const format: BookFormat = extension === "md" || extension === "markdown" ? "md" : extension === "epub" ? "epub" : "txt";
  const originalFileName = getBaseFileName(fileName);
  if (!/^[^<>:"|?*\u0000-\u001F]{1,180}$/.test(originalFileName)) {
    throw new Error("文件名包含异常字符，请重命名后再导入。");
  }
  if (format !== "epub" && !content.trim()) {
    throw new Error("正文为空，未导入。");
  }
  if (format === "epub" && !content.trim()) {
    throw new Error("EPUB 文件为空，未导入。");
  }
  if (format === "epub") {
    const normalizedBase64 = content.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
    let signature = "";
    try {
      signature = atob(normalizedBase64.slice(0, 8)).slice(0, 4);
    } catch {
      throw new Error("EPUB 文件不是有效的 ZIP 容器。");
    }
    if (!signature.startsWith("PK")) throw new Error("EPUB 文件不是有效的 ZIP 容器。");
    const metadata = await inspectEpubForImport(content, detectTitleFromFileName(originalFileName));
    return {
      title: metadata.title,
      author: metadata.author,
      description: metadata.description,
      language: metadata.language,
      publisher: metadata.publisher,
      format,
      originalFileName,
      content,
      size,
      contentHash: await hashText(content),
      coverDataUrl: metadata.coverDataUrl,
      epubToc: metadata.toc,
      epubTotalChapters: metadata.totalChapters
    };
  }
  return {
    title: detectTitleFromFileName(originalFileName),
    author: detectAuthor(content),
    format,
    originalFileName,
    content,
    size,
    contentHash: await hashText(content),
    readerPreview: createReaderPreview(format, content)
  };
}

export async function saveMobileBook(snapshot: MobileSnapshot, imported: ImportedMobileBook): Promise<MobileSnapshot> {
  const duplicateCount = snapshot.books.filter((book) => book.contentHash === imported.contentHash || book.originalFileName === imported.originalFileName).length;
  const id = `mobile-book-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`;
  const importedAt = nowIso();
  const expectedPath = getMobileBookStoragePath(id, imported.originalFileName);
  beginPendingMobileBookWrite(id, expectedPath);
  const storedFile = await saveMobileBookFile({
    bookId: id,
    originalFileName: imported.originalFileName,
    format: imported.format,
    content: imported.content
  });
  if (!storedFile?.localFilePath) {
    await deleteMobileBookFile(expectedPath).catch(() => undefined);
    completePendingMobileBookWrite(id);
    throw new Error("正文保存到手机本地失败，请检查存储空间或重新选择文件。");
  }
  const localContentPath = storedFile.localFilePath;
  const book: MobileBook = {
    id,
    title: imported.title,
    author: imported.author,
    description: imported.description,
    language: imported.language,
    publisher: imported.publisher,
    filePath: localContentPath,
    originalFileName: imported.originalFileName,
    originalFilePath: imported.originalFileName,
    originalPath: imported.originalFileName,
    format: imported.format,
    importedAt,
    updatedAt: importedAt,
    size: imported.size,
    contentHash: imported.contentHash,
    duplicateIndex: duplicateCount + 1,
    importLabel: duplicateCount ? `重复导入 #${duplicateCount + 1} · 导入于 ${new Date(importedAt).toLocaleDateString("zh-CN")}` : `导入于 ${new Date(importedAt).toLocaleDateString("zh-CN")}`,
    localUri: storedFile.localUri,
    localFilePath: localContentPath,
    localContentPath,
    origin: "local_import",
    contentStatus: "available",
    coverDataUrl: imported.coverDataUrl,
    epub: imported.format === "epub" ? {
      author: imported.author,
      description: imported.description,
      language: imported.language,
      publisher: imported.publisher,
      toc: imported.epubToc?.map((item) => ({
        id: item.id,
        label: item.title,
        href: item.href ?? "",
        level: item.level
      }))
    } : undefined,
    readerPreview: imported.readerPreview,
    revision: 1,
    deviceId: getMobileDeviceId()
  };
  const next = {
    ...snapshot,
    books: [book, ...snapshot.books],
    updatedAt: nowIso()
  };
  try {
    await saveMobileSnapshot(next);
    const committed = await loadMobileSnapshot();
    if (!committed.books.some((item) => item.id === id && item.localContentPath === localContentPath)) {
      throw new Error("书籍记录未能持久保存，已回滚本次导入。");
    }
  } catch (error) {
    await deleteMobileBookFile(localContentPath).catch(() => undefined);
    completePendingMobileBookWrite(id);
    throw error;
  }
  completePendingMobileBookWrite(id);
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${id}`);
  return next;
}

/**
 * 用用户重新选择的同一本文件修复旧的缺失记录。只替换设备私有正文和元数据，
 * bookId、进度、书签、笔记、灵感来源与书单关系全部保持不变。
 */
export async function repairMobileBookFromImport(
  snapshot: MobileSnapshot,
  bookId: string,
  imported: ImportedMobileBook
): Promise<MobileSnapshot> {
  const current = snapshot.books.find((book) => book.id === bookId);
  if (!current) throw new Error("要修复的书籍记录不存在。");
  if (current.format !== imported.format) throw new Error("所选文件格式与原书不一致，不能覆盖原记录。");
  if (current.contentStatus === "available" && current.localContentPath) {
    throw new Error("这本书的本地正文仍然可用，无需重新导入。");
  }
  const sameStableFile = current.contentHash
    ? current.contentHash === imported.contentHash
    : getBaseFileName(current.originalFileName).toLowerCase() === getBaseFileName(imported.originalFileName).toLowerCase();
  if (!sameStableFile) {
    throw new Error("所选文件与原书不匹配。为避免覆盖进度和笔记，请将它作为新书导入。");
  }

  const expectedPath = getMobileBookStoragePath(current.id, imported.originalFileName);
  beginPendingMobileBookWrite(current.id, expectedPath);
  const storedFile = await saveMobileBookFile({
    bookId: current.id,
    originalFileName: imported.originalFileName,
    format: imported.format,
    content: imported.content
  });
  if (!storedFile?.localFilePath) {
    await deleteMobileBookFile(expectedPath).catch(() => undefined);
    completePendingMobileBookWrite(current.id);
    throw new Error("重新导入的正文保存失败，原书籍记录未修改。");
  }

  const updatedAt = nowIso();
  const repaired: MobileBook = {
    ...current,
    title: imported.title || current.title,
    author: imported.author ?? current.author,
    description: imported.description ?? current.description,
    language: imported.language ?? current.language,
    publisher: imported.publisher ?? current.publisher,
    originalFileName: imported.originalFileName,
    size: imported.size,
    contentHash: imported.contentHash,
    filePath: storedFile.localFilePath,
    localFilePath: storedFile.localFilePath,
    localContentPath: storedFile.localFilePath,
    localUri: storedFile.localUri,
    origin: "local_import",
    contentStatus: "available",
    coverDataUrl: imported.coverDataUrl ?? current.coverDataUrl,
    readerPreview: imported.readerPreview,
    epub: imported.format === "epub" ? {
      ...current.epub,
      author: imported.author ?? current.epub?.author,
      description: imported.description ?? current.epub?.description,
      language: imported.language ?? current.epub?.language,
      publisher: imported.publisher ?? current.epub?.publisher,
      toc: imported.epubToc?.map((item) => ({ id: item.id, label: item.title, href: item.href ?? "", level: item.level })) ?? current.epub?.toc
    } : current.epub,
    updatedAt,
    revision: (current.revision ?? 0) + 1
  };
  const next: MobileSnapshot = {
    ...snapshot,
    books: snapshot.books.map((book) => book.id === current.id ? repaired : book),
    updatedAt
  };
  try {
    await saveMobileSnapshot(next);
    const committed = await loadMobileSnapshot();
    if (!committed.books.some((book) => book.id === current.id && book.contentStatus === "available" && book.localContentPath === storedFile.localFilePath)) {
      throw new Error("修复记录未能持久保存。");
    }
  } catch (error) {
    await deleteMobileBookFile(storedFile.localFilePath).catch(() => undefined);
    completePendingMobileBookWrite(current.id);
    throw error;
  }
  completePendingMobileBookWrite(current.id);

  const oldPath = current.localContentPath || current.localFilePath || (current.filePath?.startsWith("books/") ? current.filePath : undefined);
  if (oldPath && oldPath !== storedFile.localFilePath) await deleteMobileBookFile(oldPath).catch(() => undefined);
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${current.id}`);
  return next;
}

export async function recoverInterruptedMobileImports(snapshot: MobileSnapshot): Promise<number> {
  const paths = snapshot.books
    .map((book) => book.localContentPath || book.localFilePath || (book.filePath?.startsWith("books/") ? book.filePath : undefined))
    .filter((path): path is string => Boolean(path));
  return recoverPendingMobileBookWrites(paths);
}

export async function saveSyncedMobileBookFile(snapshot: MobileSnapshot, book: MobileBook, content: string): Promise<MobileSnapshot> {
  if (!content.trim()) {
    throw new Error("同步正文为空，未保存。");
  }
  const storedFile = await saveMobileBookFile({
    bookId: book.id,
    originalFileName: getStoredBookFileName(book),
    format: book.format,
    content
  });
  if (!storedFile?.localFilePath) {
    throw new Error("同步正文保存到手机本地失败。");
  }
  const localContentPath = storedFile.localFilePath;
  const nextBook: MobileBook = {
    ...book,
    filePath: localContentPath,
    localUri: storedFile.localUri ?? book.localUri,
    localFilePath: localContentPath,
    localContentPath,
    origin: "sync_downloaded",
    contentStatus: "available",
    readerPreview: createReaderPreview(book.format, content) ?? book.readerPreview,
    updatedAt: nowIso()
  };
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
  const next = {
    ...snapshot,
    books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
    updatedAt: nowIso()
  };
  try {
    await saveMobileSnapshot(next);
  } catch (error) {
    await deleteMobileBookFile(localContentPath);
    throw error;
  }
  return next;
}

export async function saveSyncedMobileBookBlob(snapshot: MobileSnapshot, book: MobileBook, blob: Blob): Promise<MobileSnapshot> {
  if (blob.size <= 0) {
    throw new Error("同步正文为空，未保存。");
  }
  const storedFile = await saveMobileBookBlob({
    bookId: book.id,
    originalFileName: getStoredBookFileName(book),
    format: book.format,
    blob
  });
  if (!storedFile?.localFilePath) {
    throw new Error("同步正文保存到手机本地失败。");
  }
  const localContentPath = storedFile.localFilePath;
  const nextBook: MobileBook = {
    ...book,
    filePath: localContentPath,
    localUri: storedFile.localUri ?? book.localUri,
    localFilePath: localContentPath,
    localContentPath,
    origin: "sync_downloaded",
    contentStatus: "available",
    size: blob.size || book.size,
    updatedAt: nowIso()
  };
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
  const next = {
    ...snapshot,
    books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
    updatedAt: nowIso()
  };
  try {
    await saveMobileSnapshot(next);
  } catch (error) {
    await deleteMobileBookFile(localContentPath);
    throw error;
  }
  return next;
}

export async function deleteMobileBook(snapshot: MobileSnapshot, bookId: string): Promise<MobileSnapshot> {
  const book = snapshot.books.find((item) => item.id === bookId);
  if (!book) return snapshot;

  const updatedAt = nowIso();
  const next: MobileSnapshot = {
    ...snapshot,
    books: snapshot.books.filter((item) => item.id !== book.id),
    progress: snapshot.progress.filter((item) => item.bookId !== book.id),
    sessions: snapshot.sessions.filter((item) => item.bookId !== book.id),
    notes: snapshot.notes.filter((item) => item.bookId !== book.id),
    highlights: snapshot.highlights.filter((item) => item.bookId !== book.id),
    shelves: snapshot.shelves.map((shelf) => shelf.bookIds.includes(book.id)
      ? {
          ...shelf,
          bookIds: shelf.bookIds.filter((id) => id !== book.id),
          updatedAt,
          revision: (shelf.revision ?? 0) + 1
        }
      : shelf),
    updatedAt
  };
  await saveMobileSnapshot(next);
  const committed = await loadMobileSnapshot();
  if (committed.books.some((item) => item.id === book.id)) {
    throw new Error("删除记录未能持久保存，书籍和正文均未删除。");
  }

  const localContentPath = book.localContentPath || book.localFilePath || book.filePath;
  try {
    if (localContentPath) await deleteMobileBookFile(localContentPath);
  } catch (error) {
    await saveMobileSnapshot(snapshot);
    throw new Error(`本地正文删除失败，已恢复书架记录：${error instanceof Error ? error.message : String(error)}`);
  }
  localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
  clearContinueRemoval(book.id);
  return next;
}
