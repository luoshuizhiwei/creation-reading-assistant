import { Directory, Encoding, Filesystem } from "@capacitor/filesystem";
import type { BookFormat } from "../../../src/types/library";

const PENDING_BOOK_WRITES_KEY = "creation-reading-assistant-mobile-pending-book-writes";

interface PendingBookWrite {
  bookId: string;
  path: string;
  startedAt: string;
}

function readPendingBookWrites(): PendingBookWrite[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(PENDING_BOOK_WRITES_KEY) ?? "[]") as PendingBookWrite[];
    return Array.isArray(parsed) ? parsed.filter((item) => Boolean(item?.bookId && item?.path)) : [];
  } catch {
    return [];
  }
}

function writePendingBookWrites(items: PendingBookWrite[]): void {
  try {
    if (items.length) localStorage.setItem(PENDING_BOOK_WRITES_KEY, JSON.stringify(items));
    else localStorage.removeItem(PENDING_BOOK_WRITES_KEY);
  } catch {
    // 日志失败不阻止导入；普通异常路径仍会直接回滚文件。
  }
}

export function beginPendingMobileBookWrite(bookId: string, path: string): void {
  const next = readPendingBookWrites().filter((item) => item.bookId !== bookId);
  next.push({ bookId, path, startedAt: new Date().toISOString() });
  writePendingBookWrites(next);
}

export function completePendingMobileBookWrite(bookId: string): void {
  writePendingBookWrites(readPendingBookWrites().filter((item) => item.bookId !== bookId));
}

/** App 异常终止后，仅清理由导入日志确认、且没有正式书籍记录引用的私有文件。 */
export async function recoverPendingMobileBookWrites(activeBookPaths: Iterable<string>): Promise<number> {
  const active = new Set(activeBookPaths);
  const pending = readPendingBookWrites();
  const keep: PendingBookWrite[] = [];
  let removed = 0;
  for (const item of pending) {
    if (active.has(item.path)) continue;
    try {
      await deleteMobileBookFile(item.path);
      removed += 1;
    } catch {
      keep.push(item);
    }
  }
  writePendingBookWrites(keep);
  return removed;
}

/**
 * 中文 TXT 常见编码为 GBK/GB18030，而 Capacitor Filesystem 默认按 UTF-8 解码。
 * 此函数读取原始字节后自动检测编码：BOM > UTF-8 > GBK > GB18030 > 兜底 UTF-8。
 */
function decodeBase64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function decodeTextWithEncodingDetection(bytes: Uint8Array): string {
  // 1. BOM 检测
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder("utf-8").decode(bytes.subarray(3));
  }
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
    return new TextDecoder("utf-16le").decode(bytes.subarray(2));
  }
  // 2. 尝试 UTF-8 严格解码
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  } catch {
    // 不是合法 UTF-8，继续尝试 GBK
  }
  // 3. 尝试 GBK / GB18030
  for (const label of ["gb18030", "gbk", "big5"]) {
    try {
      return new TextDecoder(label, { fatal: true }).decode(bytes);
    } catch {
      // 继续尝试下一个编码
    }
  }
  // 4. 兜底：UTF-8 替换字符
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes);
}

export interface MobileBookFileInput {
  bookId: string;
  originalFileName: string;
  format: BookFormat;
  content: string;
}

export interface MobileBookBlobInput {
  bookId: string;
  originalFileName: string;
  format: BookFormat;
  blob: Blob;
}

export interface SavedMobileBookFile {
  localFilePath: string;
  localUri?: string;
  size?: number;
}

function safeFileName(name: string): string {
  return name.replace(/[\\/:*?"<>|]/g, "_").slice(0, 80) || "book";
}

export function getMobileBookStoragePath(bookId: string, originalFileName: string): string {
  return `books/${bookId}-${safeFileName(originalFileName)}`;
}

async function ensureBooksDirectory(): Promise<void> {
  await Filesystem.mkdir({ path: "books", directory: Directory.Data, recursive: true }).catch(() => undefined);
}

async function verifySavedBookFile(path: string): Promise<{ uri?: string; size?: number } | undefined> {
  try {
    const stat = await Filesystem.stat({ path, directory: Directory.Data });
    const size = typeof stat.size === "number" ? stat.size : 0;
    if (size <= 0) return undefined;
    const uri = await Filesystem.getUri({ path, directory: Directory.Data }).catch(() => undefined);
    return { uri: uri?.uri, size };
  } catch {
    return undefined;
  }
}

export async function statMobileBookFile(localPath?: string): Promise<{ uri?: string; size?: number } | undefined> {
  if (!localPath || /^[a-z]+:\/\//i.test(localPath)) return undefined;
  return verifySavedBookFile(localPath);
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}

export async function saveMobileBookFile(input: MobileBookFileInput): Promise<SavedMobileBookFile | undefined> {
  const path = getMobileBookStoragePath(input.bookId, input.originalFileName);
  try {
    await ensureBooksDirectory();
    if (input.format === "epub") {
      await Filesystem.writeFile({
        path,
        data: input.content,
        directory: Directory.Data
      });
    } else {
      await Filesystem.writeFile({
        path,
        data: input.content,
        directory: Directory.Data,
        encoding: Encoding.UTF8
      });
    }
    const verified = await verifySavedBookFile(path);
    if (!verified) return undefined;
    return {
      localFilePath: path,
      localUri: verified.uri,
      size: verified.size
    };
  } catch {
    await Filesystem.deleteFile({ path, directory: Directory.Data }).catch(() => undefined);
    return undefined;
  }
}

export async function saveMobileBookBlob(input: MobileBookBlobInput): Promise<SavedMobileBookFile | undefined> {
  const path = getMobileBookStoragePath(input.bookId, input.originalFileName);
  try {
    await ensureBooksDirectory();
    // 统一保存原始字节（base64），避免 blob.text() 强制 UTF-8 解码导致 GBK/GB18030 中文乱码
    const base64 = arrayBufferToBase64(await input.blob.arrayBuffer());
    await Filesystem.writeFile({
      path,
      data: base64,
      directory: Directory.Data
    });
    const verified = await verifySavedBookFile(path);
    if (!verified) return undefined;
    return {
      localFilePath: path,
      localUri: verified.uri,
      size: verified.size
    };
  } catch {
    await Filesystem.deleteFile({ path, directory: Directory.Data }).catch(() => undefined);
    return undefined;
  }
}

export async function deleteMobileBookFile(localPath?: string): Promise<void> {
  if (!localPath || /^[a-z]+:\/\//i.test(localPath)) return;
  try {
    await Filesystem.deleteFile({ path: localPath, directory: Directory.Data });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    if (/not found|does not exist|不存在/i.test(detail)) return;
    throw error;
  }
}

export async function readMobileBookFile(localPath?: string, format?: BookFormat): Promise<string | undefined> {
  if (!localPath) return undefined;
  if (/^[a-z]+:\/\//i.test(localPath)) return undefined;
  try {
    // 统一以 base64 读取原始字节，再自行检测编码
    const result = await Filesystem.readFile({
      path: localPath,
      directory: Directory.Data
    });
    if (typeof result.data !== "string") return undefined;
    if (format === "epub") {
      // EPUB 保持 base64 原样，交给 JSZip 解析
      return result.data;
    }
    // TXT/MD：检测编码，解决 GBK/GB18030 中文乱码
    const bytes = decodeBase64ToBytes(result.data);
    return decodeTextWithEncodingDetection(bytes);
  } catch {
    return undefined;
  }
}
