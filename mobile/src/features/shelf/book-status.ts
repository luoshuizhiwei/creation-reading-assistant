import type { MobileBook } from "../../types/mobile";
import type { MobileSnapshot } from "../../services/mobile-storage";
import { formatCompactDateTime } from "../../utils/format";

export type BookReadinessTone = "ready" | "cloud" | "error";

export function formatBookProgress(progress: number): string {
  const normalized = Number.isFinite(progress) ? Math.min(100, Math.max(0, progress)) : 0;
  if (normalized <= 0.05) return "未读";
  if (normalized >= 99.5) return "已读完";
  return `${normalized.toFixed(normalized >= 10 ? 0 : 1)}%`;
}

export function formatLastReadLabel(snapshot: MobileSnapshot, book: MobileBook): string {
  const progress = snapshot.progress.find((item) => item.bookId === book.id);
  if (!progress?.lastReadAt) return "还没开始读";
  return `上次 ${formatCompactDateTime(progress.lastReadAt)}`;
}

export function isBookDownloaded(book: MobileBook): boolean {
  if (book.origin === "sync_placeholder") return false;
  if (book.contentStatus === "missing" || book.contentStatus === "failed" || book.contentStatus === "downloading") return false;
  return Boolean(book.localContentPath || book.localFilePath || book.filePath?.startsWith("books/"));
}

/**
 * 首页和阅读入口共用的“当前设备确实可读”判断。
 * 这里仅判断同步状态与本地文件指针；真正打开时仍由读取流程校验文件是否存在、能否解析。
 */
export function isBookReadableOnDevice(book: MobileBook): boolean {
  return isBookDisplayable(book) && getBookReadiness(book).tone === "ready";
}

/** 判断书籍是否应作为正常可读书籍在首页展示。排除占位、失败、缺失、下载中、空文件、仅有元数据等情况。 */
export function isBookDisplayable(book: MobileBook): boolean {
  if (book.origin === "sync_placeholder") return false;
  if (book.contentStatus === "failed") return false;
  if (book.contentStatus === "missing") return false;
  if (book.contentStatus === "downloading") return false;
  if (book.size <= 0) return false;
  // 仅有元数据：没有任何可指向内容或文件的标识
  if (!book.contentHash && !book.localContentPath && !book.localFilePath && !book.filePath) return false;
  return true;
}

export function getReadableBookLocalPath(book: MobileBook): string | undefined {
  return book.localContentPath ?? book.localFilePath ?? (book.filePath?.startsWith("books/") ? book.filePath : undefined);
}

export function getBookReadiness(book: MobileBook): { label: string; tone: BookReadinessTone } {
  if (book.origin === "sync_placeholder") return { label: "仅有书籍信息", tone: "cloud" };
  if (book.contentStatus === "failed") return { label: "正文保存失败", tone: "error" };
  if (book.contentStatus === "missing") return { label: "正文未在本机", tone: "cloud" };
  if (book.contentStatus === "downloading") return { label: "正文下载中", tone: "cloud" };
  if (!Number.isFinite(book.size) || book.size <= 0) return { label: "正文为空", tone: "error" };
  return isBookDownloaded(book) ? { label: "可离线阅读", tone: "ready" } : { label: "需下载正文", tone: "cloud" };
}

export function bookStorageLabel(book: MobileBook): string {
  return isBookDownloaded(book) ? "本机可读" : "需下载正文";
}
