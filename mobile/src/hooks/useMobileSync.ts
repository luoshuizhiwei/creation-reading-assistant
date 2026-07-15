import { useMemo, useRef, useState, type Dispatch, type SetStateAction, type MutableRefObject } from "react";
import {
  base64ToBlob,
  buildMobileSyncPushPayload
} from "../utils/mobile-helpers";
import {
  exportMobileSnapshot,
  getMobileDeviceId,
  mergeReadingProgress,
  saveMobileSnapshot,
  saveSyncedMobileBookBlob,
  type MobileSnapshot
} from "../services/mobile-storage";
import { createSyncClient, pairWithFirstReachable, parsePairingCandidates, type PairingInput } from "../services/sync-client";
import { isBookDownloaded } from "../features/shelf/book-status";
import type { MobileBook } from "../types/mobile";
import { addMobileLog } from "../services/mobile-logger";

/** 单条同步失败项 */
export interface SyncFailedItem {
  type: "upload-book" | "download-book" | "push" | "pull" | "pair";
  bookId?: string;
  title?: string;
  reason: string;
}

/** 结构化同步结果 */
export interface SyncResultDetail {
  timestamp: string;
  success: boolean;
  uploaded: {
    inspirations: number;
    books: number;
    progress: number;
    bookFiles: number;
  };
  downloaded: {
    inspirations: number;
    books: number;
    progress: number;
    sessions: number;
  };
  pendingDownloadCount: number;
  failedItems: SyncFailedItem[];
  durationMs: number;
  /** 同步前自动生成的本地快照 JSON（用于回滚） */
  snapshotBackupKey?: string;
}

const SNAPSHOT_BACKUP_KEY = "creation-reading-assistant-mobile-sync-backup";
const SNAPSHOT_BACKUP_MAX = 1;

function saveSnapshotBackup(snapshot: MobileSnapshot): string {
  try {
    const json = JSON.stringify(snapshot);
    localStorage.setItem(SNAPSHOT_BACKUP_KEY, json);
    return SNAPSHOT_BACKUP_KEY;
  } catch {
    return "";
  }
}

interface UseMobileSyncOptions {
  snapshot: MobileSnapshot;
  setSnapshot: Dispatch<SetStateAction<MobileSnapshot>>;
  setMessage: (message: string) => void;
  /** 读取本机书籍正文（来自 reader book hook） */
  readMobileBookContent: (book: MobileBook) => Promise<string | undefined>;
  /** 下载正文成功后清掉阅读器正文缓存 */
  readerContentCacheRef: MutableRefObject<Map<string, string>>;
}

/**
 * 移动端同步业务：配对、双向同步、正文下载/取消、上传本机书籍。
 * - paired / pairingText 维护配对状态，client 由 paired 派生
 * - syncLogs 保留最近 8 条同步日志
 * - downloadingBookId 标记当前下载中的书籍
 * - showQrScanner 控制 QR 扫码层显隐
 */
export function useMobileSync({
  snapshot,
  setSnapshot,
  setMessage,
  readMobileBookContent,
  readerContentCacheRef
}: UseMobileSyncOptions) {
  const [pairingText, setPairingText] = useState("");
  const [paired, setPaired] = useState<PairingInput>();
  const [syncLogs, setSyncLogs] = useState<string[]>([]);
  const [downloadingBookId, setDownloadingBookId] = useState<string>();
  const [showQrScanner, setShowQrScanner] = useState(false);
  const [lastSyncResult, setLastSyncResult] = useState<SyncResultDetail>();
  const [syncing, setSyncing] = useState(false);
  const downloadAbortRef = useRef<AbortController>();

  const client = useMemo(() => (paired ? createSyncClient(paired) : undefined), [paired]);

  const pendingDownloadCount = snapshot.books.filter((book) => !isBookDownloaded(book)).length;

  const appendSyncLog = (entry: string) => {
    const line = `${new Date().toLocaleTimeString("zh-CN", { hour12: false })} · ${entry}`;
    setSyncLogs((current) => [line, ...current].slice(0, 8));
  };

  const uploadMobileBookFiles = async (baseSnapshot: MobileSnapshot): Promise<{ uploaded: number; failed: SyncFailedItem[] }> => {
    if (!client) return { uploaded: 0, failed: [] };
    const localDeviceId = getMobileDeviceId();
    let uploaded = 0;
    const failed: SyncFailedItem[] = [];
    for (const book of baseSnapshot.books) {
      if (book.deletedAt || book.deviceId !== localDeviceId) continue;
      try {
        const content = await readMobileBookContent(book);
        if (!content) continue;
        setMessage(`正在上传《${book.title}》到电脑端书库……`);
        await client.uploadBookFile(
          book.id,
          book.originalFileName ?? `${book.id}.${book.format}`,
          book.format === "epub" ? base64ToBlob(content, "application/epub+zip") : content
        );
        uploaded += 1;
      } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        addMobileLog("warn", "局域网同步", `上传《${book.title}》失败：${reason}`, { code: "SYNC_BOOK_UPLOAD_FAILED" });
        failed.push({ type: "upload-book", bookId: book.id, title: book.title, reason });
      }
    }
    return { uploaded, failed };
  };

  const downloadBookToMobile = async (book: MobileBook) => {
    if (!client) {
      setMessage("请先连接电脑端，再下载正文。");
      return;
    }
    setDownloadingBookId(book.id);
    downloadAbortRef.current = new AbortController();
    try {
      appendSyncLog(`开始下载《${book.title}》正文`);
      setMessage(`正在下载《${book.title}》正文到手机本地……`);
      const blob = await client.downloadBookFile(book.id, downloadAbortRef.current.signal);
      let next: MobileSnapshot = snapshot;
      setSnapshot(prev => {
        next = prev;
        return prev;
      });
      const saved = await saveSyncedMobileBookBlob(next, book, blob);
      readerContentCacheRef.current.delete(book.id);
      setSnapshot(saved);
      appendSyncLog(`已下载《${book.title}》正文`);
      setMessage(`《${book.title}》正文已下载，可以离线阅读。`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      if (/abort/i.test(detail)) {
        appendSyncLog(`已取消下载《${book.title}》`);
        setMessage(`已取消下载《${book.title}》。`);
        return;
      }
      appendSyncLog(`下载《${book.title}》失败：${detail}`);
      addMobileLog("error", "局域网同步", `下载《${book.title}》失败：${detail}`, { code: "SYNC_BOOK_DOWNLOAD_FAILED" });
      setMessage(`下载正文失败：${detail}`);
    } finally {
      downloadAbortRef.current = undefined;
      setDownloadingBookId(undefined);
    }
  };

  const cancelBookDownload = () => {
    downloadAbortRef.current?.abort();
  };

  const connectLan = async (text = pairingText) => {
    try {
      const candidates = parsePairingCandidates(text);
      const { input, result } = await pairWithFirstReachable(candidates, ({ index, total, input: candidate }) => {
        setMessage(`正在尝试第 ${index}/${total} 个电脑地址：${candidate.host}:${candidate.port}`);
      });
      setPaired(input);
      setPairingText(input.pairingUrl ?? text);
      setMessage(`已连接电脑端：${result.device.name}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("warn", "局域网配对", detail, { code: "SYNC_PAIR_FAILED" });
      setMessage(`连接失败：${detail}。请确认电脑端“手机同步”服务仍在运行，手机和电脑在同一 Wi‑Fi；如果仍失败，关闭防火墙或尝试电脑端显示的备用地址。`);
    }
  };

  const scanPairingQrCode = async () => {
    setShowQrScanner(true);
    setMessage("请把电脑端二维码放进取景框；如果相机不可用，可以粘贴配对 URL。");
  };

  const handleQrScanResult = async (text: string) => {
    setShowQrScanner(false);
    setPairingText(text);
    setMessage("已识别二维码，正在连接电脑。");
    await connectLan(text);
  };

  const syncFromDesktop = async () => {
    if (!client) {
      setMessage("请先在“我的 / 同步”里粘贴电脑端配对 URL 或二维码载荷。");
      return;
    }
    setSyncing(true);
    const startedAt = Date.now();
    const timestamp = new Date().toISOString();
    const snapshotBackupKey = saveSnapshotBackup(snapshot);
    const failedItems: SyncFailedItem[] = [];
    try {
      const push = await client.push(buildMobileSyncPushPayload(snapshot));
      const uploadSummary = await uploadMobileBookFiles(snapshot);
      failedItems.push(...uploadSummary.failed);
      const pull = await client.pull();
      const localBookById = new Map(snapshot.books.map((item) => [item.id, item]));
      const mergedBooks = pull.books.map((item) => {
        const incoming = item.payload as MobileBook;
        const local = localBookById.get(incoming.id);
        if (!local) return incoming;
        // 保留移动端本地文件路径与状态，避免同步后已下载的正文变成"待下载"
        return {
          ...incoming,
          localContentPath: local.localContentPath ?? incoming.localContentPath,
          localFilePath: local.localFilePath ?? incoming.localFilePath,
          localUri: local.localUri ?? incoming.localUri,
          origin: local.origin ?? incoming.origin,
          contentStatus: local.contentStatus ?? incoming.contentStatus,
          coverDataUrl: local.coverDataUrl ?? incoming.coverDataUrl,
          lastOpenedAt: local.lastOpenedAt ?? incoming.lastOpenedAt
        };
      });
      const localProgressByBookId = new Map(snapshot.progress.map((item) => [item.bookId, item]));
      const mergedProgress = pull.progress.map((item) => {
        const remote = item.payload;
        const local = localProgressByBookId.get(remote.bookId);
        return local ? mergeReadingProgress(local, remote) : remote;
      });
      const merged: MobileSnapshot = {
        ...snapshot,
        inspirations: pull.inspirations.map((item) => item.payload),
        books: mergedBooks,
        progress: mergedProgress,
        sessions: pull.sessions.map((item) => item.payload),
        updatedAt: new Date().toISOString()
      };
      setSnapshot(merged);
      void saveMobileSnapshot(merged);
      const nextPendingDownloadCount = mergedBooks.filter((item) => !isBookDownloaded(item)).length;
      const durationMs = Date.now() - startedAt;
      const result: SyncResultDetail = {
        timestamp,
        success: true,
        uploaded: {
          inspirations: push.applied.inspirations,
          books: push.applied.books,
          progress: push.applied.progress,
          bookFiles: uploadSummary.uploaded
        },
        downloaded: {
          inspirations: pull.inspirations.length,
          books: pull.books.length,
          progress: pull.progress.length,
          sessions: pull.sessions.length
        },
        pendingDownloadCount: nextPendingDownloadCount,
        failedItems,
        durationMs,
        snapshotBackupKey
      };
      setLastSyncResult(result);
      appendSyncLog(`同步完成：上传 ${uploadSummary.uploaded} 个正文文件，下载 ${pull.books.length} 本书，待下载 ${nextPendingDownloadCount} 本${failedItems.length ? `，${failedItems.length} 项失败` : ""}`);
      setMessage(
        `同步完成：已上传 ${push.applied.inspirations} 条灵感、${push.applied.books} 本书、${push.applied.progress} 条进度、${uploadSummary.uploaded} 个正文文件；` +
          `电脑返回 ${pull.inspirations.length} 条灵感、${pull.books.length} 本书；` +
          `待下载正文 ${nextPendingDownloadCount} 本。${failedItems.length ? "有部分项失败，可在下方查看详情并重试。" : "需要阅读时在书架点“下载正文”。"}`
      );
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      addMobileLog("error", "局域网同步", detail, { code: "SYNC_FAILED" });
      const durationMs = Date.now() - startedAt;
      failedItems.push({ type: "pull", reason: detail });
      const result: SyncResultDetail = {
        timestamp,
        success: false,
        uploaded: { inspirations: 0, books: 0, progress: 0, bookFiles: 0 },
        downloaded: { inspirations: 0, books: 0, progress: 0, sessions: 0 },
        pendingDownloadCount,
        failedItems,
        durationMs,
        snapshotBackupKey
      };
      setLastSyncResult(result);
      appendSyncLog(`同步失败：${detail}`);
      setMessage(`同步失败：${detail}。已自动保留同步前本地快照，可在下方查看详情或重试。`);
    } finally {
      setSyncing(false);
    }
  };

  /** 重试上次同步中失败的正文上传 */
  const retryFailedUploads = async () => {
    if (!client || !lastSyncResult) return;
    const failedUploads = lastSyncResult.failedItems.filter((item) => item.type === "upload-book");
    if (!failedUploads.length) {
      setMessage("没有可重试的上传失败项。");
      return;
    }
    setSyncing(true);
    let retried = 0;
    let stillFailed = 0;
    for (const failed of failedUploads) {
      const book = snapshot.books.find((item) => item.id === failed.bookId);
      if (!book) continue;
      try {
        const content = await readMobileBookContent(book);
        if (!content) continue;
        setMessage(`正在重试上传《${book.title}》……`);
        await client.uploadBookFile(
          book.id,
          book.originalFileName ?? `${book.id}.${book.format}`,
          book.format === "epub" ? base64ToBlob(content, "application/epub+zip") : content
        );
        retried += 1;
      } catch {
        stillFailed += 1;
      }
    }
    setLastSyncResult((prev) => prev ? {
      ...prev,
      failedItems: prev.failedItems.filter((item) => item.type !== "upload-book" || stillFailed === 0 ? false : true)
    } : prev);
    setSyncing(false);
    appendSyncLog(`重试上传：成功 ${retried} 个${stillFailed ? `，仍失败 ${stillFailed} 个` : ""}`);
    setMessage(`重试完成：成功上传 ${retried} 个${stillFailed ? `，仍有 ${stillFailed} 个失败` : ""}。`);
  };

  return {
    paired,
    pairingText,
    setPairingText,
    syncLogs,
    downloadingBookId,
    showQrScanner,
    setShowQrScanner,
    client,
    pendingDownloadCount,
    lastSyncResult,
    syncing,
    downloadBookToMobile,
    cancelBookDownload,
    connectLan,
    scanPairingQrCode,
    handleQrScanResult,
    syncFromDesktop,
    retryFailedUploads
  };
}
