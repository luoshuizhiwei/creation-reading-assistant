import type { SyncEnvelope, SyncPushPayload } from "../../../src/types/sync";
import { Capacitor } from "@capacitor/core";
import { Directory, Encoding, Filesystem } from "@capacitor/filesystem";
import { Share } from "@capacitor/share";
import type { MobileBook, MobileSnapshot } from "../types/mobile";
import {
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  isSupportedMobileBookFileName,
  sanitizeMobileBookForSync
} from "../services/mobile-storage";

export type MobileAppTheme = "system" | "light" | "dark";

export const MAX_MOBILE_IMPORT_BYTES = 80 * 1024 * 1024;
// EPUB 会在 Android WebView 中以 base64 解码并交给 epubjs，峰值内存明显高于原文件。
// 将单本上限收紧，避免“导入成功但打开即被系统杀进程”的误导体验。
export const MAX_MOBILE_EPUB_IMPORT_BYTES = 48 * 1024 * 1024;
export const HOT_READER_CACHE_BYTES = 512 * 1024;
export const MAX_READER_PREVIEW_CHARS = 64 * 1024;

export const emptySnapshot: MobileSnapshot = {
  inspirations: [],
  books: [],
  progress: [],
  sessions: [],
  notes: [],
  highlights: [],
  tags: [],
  categories: [],
  shelves: [],
  syncAccounts: [],
  updatedAt: new Date().toISOString()
};

export function base64ToBlob(value: string, type = "application/octet-stream"): Blob {
  const binary = atob(value.replace(/^data:.*?;base64,/, ""));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type });
}

export function loadMobileAppTheme(): MobileAppTheme {
  const saved = localStorage.getItem("creation-reading-assistant-mobile-theme");
  return saved === "light" || saved === "dark" || saved === "system" ? saved : "system";
}

export async function downloadTextFile(fileName: string, content: string, type = "application/json"): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    const safeName = fileName.replace(/[\\/:*?"<>|]/g, "_").slice(0, 120) || "export.json";
    const path = `exports/${safeName}`;
    await Filesystem.mkdir({ path: "exports", directory: Directory.Cache, recursive: true }).catch(() => undefined);
    await Filesystem.writeFile({ path, data: content, directory: Directory.Cache, encoding: Encoding.UTF8 });
    const { uri } = await Filesystem.getUri({ path, directory: Directory.Cache });
    await Share.share({ title: safeName, dialogTitle: "导出文件", files: [uri] });
    return;
  }
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function waitForBrowserPaint(): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, 0);
  });
}

export function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => reject(new Error(message)), timeoutMs);
    promise
      .then((value) => resolve(value))
      .catch((error) => reject(error))
      .finally(() => window.clearTimeout(timer));
  });
}

export function canCacheReaderText(format: MobileBook["format"], content: string, knownSize?: number): boolean {
  if (format === "epub") return false;
  const estimatedBytes = knownSize ?? new Blob([content]).size;
  return estimatedBytes <= HOT_READER_CACHE_BYTES;
}

export function writeReaderTextCache(bookId: string, content: string): boolean {
  try {
    localStorage.setItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${bookId}`, content);
    return true;
  } catch {
    localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${bookId}`);
    return false;
  }
}

export function buildReaderPreview(content: string): string {
  return content.trim().slice(0, MAX_READER_PREVIEW_CHARS);
}

export function stripMobileBookRuntimeFields(book: MobileBook): MobileBook {
  return sanitizeMobileBookForSync(book);
}

export function formatBytes(bytes?: number): string {
  const value = bytes ?? 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

export function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error ?? new Error("读取文件失败"));
    reader.onload = () => {
      // Strip data URL prefix (e.g. "data:application/epub+zip;base64,") — only return raw base64.
      const result = String(reader.result ?? "");
      const commaIndex = result.indexOf(",");
      resolve(commaIndex >= 0 ? result.slice(commaIndex + 1) : result);
    };
    reader.readAsDataURL(file);
  });
}

/** 导入文件时检测编码：BOM > UTF-8 > GBK/GB18030 > 兜底 UTF-8 */
export function decodeImportTextWithEncodingDetection(bytes: Uint8Array): string {
  return decodeImportTextWithEncodingInfo(bytes).text;
}

/** 导入文件时检测编码并返回编码标签：BOM > UTF-8 > GBK/GB18030 > 兜底 UTF-8 */
export function decodeImportTextWithEncodingInfo(bytes: Uint8Array): { text: string; encoding: string } {
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return { text: new TextDecoder("utf-8").decode(bytes.subarray(3)), encoding: "UTF-8 (BOM)" };
  }
  if (bytes.length >= 2 && bytes[0] === 0xff && bytes[1] === 0xfe) {
    return { text: new TextDecoder("utf-16le").decode(bytes.subarray(2)), encoding: "UTF-16LE" };
  }
  try {
    return { text: new TextDecoder("utf-8", { fatal: true }).decode(bytes), encoding: "UTF-8" };
  } catch {
    // 不是合法 UTF-8，继续尝试 GBK
  }
  for (const label of ["gb18030", "gbk", "big5"]) {
    try {
      return { text: new TextDecoder(label, { fatal: true }).decode(bytes), encoding: label.toUpperCase() };
    } catch {
      // 继续尝试下一个编码
    }
  }
  return { text: new TextDecoder("utf-8", { fatal: false }).decode(bytes), encoding: "UTF-8 (兜底)" };
}

export interface ImportFileContentResult {
  content: string;
  encoding: string;
}

export async function readImportFileContent(file: File): Promise<ImportFileContentResult> {
  if (!isSupportedMobileBookFileName(file.name) || file.size <= 0) {
    throw new Error("unsupported-mobile-book-file");
  }
  const isEpub = /\.epub$/i.test(file.name);
  const maxBytes = isEpub ? MAX_MOBILE_EPUB_IMPORT_BYTES : MAX_MOBILE_IMPORT_BYTES;
  if (file.size > maxBytes) {
    throw new Error(`文件过大，当前格式的移动端单本导入上限为 ${formatBytes(maxBytes)}。`);
  }
  if (isEpub) {
    return { content: await readFileAsBase64(file), encoding: "EPUB-base64" };
  }
  // TXT/MD：读取原始字节后检测编码，解决 GBK/GB18030 中文乱码
  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  const { text, encoding } = decodeImportTextWithEncodingInfo(bytes);
  return { content: text, encoding };
}

export function formatQrScanError(error: unknown): string {
  const detail = error instanceof Error ? error.message : String(error);
  if (/canceled|cancelled|cancel/i.test(detail)) return "已取消扫码。你也可以粘贴电脑端配对 URL 连接。";
  if (/permission|camera|NotAllowed/i.test(detail)) return "没有相机权限，无法扫码。请在系统设置里允许相机权限，或粘贴配对 URL。";
  if (/NotFound|device|mediaDevices/i.test(detail)) return "没有找到可用摄像头。请改用粘贴配对 URL 或二维码载荷连接。";
  if (/timeout|超时/i.test(detail)) return "扫码超时，请靠近二维码、提高屏幕亮度，或改用粘贴配对 URL。";
  return `扫码失败：${detail.slice(0, 80)}。你也可以粘贴电脑端配对 URL 或二维码载荷连接。`;
}

function toMobileSyncEnvelope<T extends { revision: number; deviceId: string; updatedAt: string; deletedAt?: string }>(
  type: SyncEnvelope<T>["type"],
  id: string,
  payload: T
): SyncEnvelope<T> {
  return {
    id,
    type,
    revision: payload.revision,
    deviceId: payload.deviceId,
    updatedAt: payload.updatedAt,
    deletedAt: payload.deletedAt,
    payload
  };
}

export function buildMobileSyncPushPayload(snapshot: MobileSnapshot): Omit<SyncPushPayload, "device"> {
  return {
    inspirations: snapshot.inspirations.map((item) => toMobileSyncEnvelope("inspiration", item.id, item)),
    books: snapshot.books.map((item) => toMobileSyncEnvelope("book", item.id, stripMobileBookRuntimeFields(item))),
    progress: snapshot.progress.map((item) => toMobileSyncEnvelope("progress", item.bookId, item)),
    sessions: snapshot.sessions.map((item) => toMobileSyncEnvelope("session", item.id, item))
  };
}
