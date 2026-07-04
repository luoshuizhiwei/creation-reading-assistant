import { useEffect, useMemo, useRef, useState, type ReactNode, type MouseEvent } from "react";
import React from "react";
import { App as CapacitorApp } from "@capacitor/app";
import { Capacitor } from "@capacitor/core";
import {
  Home,
  BookOpen,
  Sparkles,
  BarChart3,
  User,
  Clock,
  Book,
  Menu,
  Search,
  Star,
  Moon,
  Grid,
  List,
  MoreHorizontal,
  ChevronRight,
  Cloud,
  Tags,
  FolderTree,
  MessageSquare,
  Palette,
  Database,
  Shield,
  Info,
  RefreshCw,
  Upload,
  Download,
  Wifi,
  FileDown,
  FileUp,
  Trash2,
  CheckCircle2
} from "lucide-react";
import jsQR from "jsqr";
import type { LibraryBook } from "../../src/types/library";
import type { SyncEnvelope, SyncPushPayload } from "../../src/types/sync";
import type { AIRunAction } from "../../src/types/ai";
import { renderMobileDocument, type MobileReaderDocument } from "./reader/mobile-reader";
import {
  clearMobileAIApiKey,
  loadMobileAISettings,
  runMobileAIAction,
  saveMobileAISettings
} from "./services/mobile-ai";
import {
  clearWebDavPasswordSecret,
  loadWebDavPasswordSecret,
  saveWebDavPasswordSecret
} from "./services/mobile-webdav-secrets";
import {
  addMobileInspiration,
  addMobileInspirationVariant,
  addMobileNote,
  addMobileReadingSession,
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  createImportedMobileBook,
  deleteMobileInspiration,
  deleteMobileNote,
  exportMobileSnapshot,
  getMobileDeviceId,
  importMobileSnapshot,
  isSupportedMobileBookFileName,
  loadMobileSnapshot,
  saveMobileBook,
  saveMobileReadingProgress,
  saveMobileSnapshot,
  saveSyncedMobileBookBlob,
  saveSyncedMobileBookFile,
  saveSyncAccount,
  updateMobileInspiration,
  type MobileSnapshot
} from "./services/mobile-storage";
import {
  buildBookRanking,
  buildMobileStatsSummary,
  buildNoteInsights,
  buildReadingTimeline,
  getStatsPeriodTitle,
  isCurrentStatsPeriod,
  shiftStatsPeriodAnchor,
  statsPeriodLabels,
  type StatsPeriod
} from "./services/mobile-stats";
import { createSyncClient, pairWithFirstReachable, parsePairingCandidates, type PairingInput } from "./services/sync-client";
import { readMobileBookFile } from "./storage/mobile-files";
import { downloadWebDavSnapshot, testWebDavConnection, uploadWebDavSnapshot } from "./sync/webdav-sync";
import {
  MOBILE_APP_VERSION,
  checkForMobileUpdate,
  openMobileUpdateUrl,
  type MobileUpdateInfo
} from "./services/mobile-updates";
import type { InspirationStatus } from "../../src/types/inspiration";
import type { MobileBook, MobileReaderSettings, SyncAccount } from "./types/mobile";

type MainTab = "home" | "shelf" | "inspiration" | "stats" | "profile";
type ProfileSubPage = "sync" | "webdav" | "tags" | "categories" | "shelves" | "reading" | "notes" | "ai" | "appearance" | "storage" | "privacy" | "about";
type ShelfViewMode = "grid" | "list";
type ShelfSortMode = "recent" | "title" | "progress";
type ReaderDrawerTab = "toc" | "search" | "bookmarks" | "notes" | "inspirations";
type ReaderPanel = ReaderDrawerTab | "settings" | "book-info";
type ReaderSearchResult = { id: string; occurrenceIndex: number; snippet: string; progressPercent: number };
type MobileAppTheme = "system" | "light" | "dark";

const inspirationStatusOptions: Array<{ value: InspirationStatus; label: string; hint: string }> = [
  { value: "inbox", label: "收集箱", hint: "刚记下，之后再整理" },
  { value: "usable", label: "可使用", hint: "已经能转成素材" },
  { value: "polished", label: "已打磨", hint: "经过润色或扩写" },
  { value: "used", label: "已采用", hint: "已写入正文或方案" },
  { value: "archived", label: "归档", hint: "暂时不再处理" }
];

const getInspirationStatusLabel = (status: string): string => {
  if (status === "draft") return "可使用";
  return inspirationStatusOptions.find((item) => item.value === status)?.label ?? "收集箱";
};

const defaultReaderSettings: MobileReaderSettings = {
  fontSize: 18,
  lineHeight: 1.85,
  pageMargin: 22,
  paragraphSpacing: 1.15,
  readerBackground: "warm",
  readerMode: "paged",
  fontWeight: "regular",
  tapZoneMode: "three-zone",
  showProgressBar: true,
  keepAwake: false,
  brightness: 100
};

const shelfSortOptions: Array<{ value: ShelfSortMode; label: string }> = [
  { value: "recent", label: "最近" },
  { value: "title", label: "书名" },
  { value: "progress", label: "进度" }
];

function escapeReaderHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function buildPlainTextFallbackDocument(book: MobileBook, content: string): MobileReaderDocument {
  const safeContent = content.trim() || `${book.title}\n\n正文为空。可以返回书架重新导入，或在“我的 / 同步”里重新下载正文。`;
  return {
    title: book.title,
    format: book.format,
    html: safeContent
      .split(/\n{2,}/)
      .map((paragraph) => `<p>${escapeReaderHtml(paragraph.trim()).replace(/\n/g, "<br />")}</p>`)
      .join("\n"),
    plainText: safeContent,
    toc: [],
    wordCount: safeContent.replace(/\s/g, "").length
  };
}

const emptySnapshot: MobileSnapshot = {
  inspirations: [],
  books: [],
  progress: [],
  sessions: [],
  notes: [],
  tags: [],
  categories: [],
  shelves: [],
  syncAccounts: [],
  updatedAt: new Date().toISOString()
};

function formatDuration(ms: number): string {
  if (ms < 60_000) return `${Math.round(ms / 1000)} 秒`;
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `${minutes} 分钟`;
  return `${(minutes / 60).toFixed(1)} 小时`;
}

function formatCompactDateTime(value?: string): string {
  if (!value) return "时间未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "时间未知";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
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

function base64ToBlob(value: string, type = "application/octet-stream"): Blob {
  const binary = atob(value.replace(/^data:.*?;base64,/, ""));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return new Blob([bytes], { type });
}

function loadMobileAppTheme(): MobileAppTheme {
  const saved = localStorage.getItem("creation-reading-assistant-mobile-theme");
  return saved === "light" || saved === "dark" || saved === "system" ? saved : "system";
}

function downloadTextFile(fileName: string, content: string, type = "application/json"): void {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

function formatBytes(bytes?: number): string {
  const value = bytes ?? 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`;
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`;
}

async function readImportFileContent(file: File): Promise<string> {
  if (!isSupportedMobileBookFileName(file.name) || file.size <= 0) {
    throw new Error("unsupported-mobile-book-file");
  }
  return /\.epub$/i.test(file.name) ? arrayBufferToBase64(await file.arrayBuffer()) : file.text();
}

function progressFor(snapshot: MobileSnapshot, bookId: string): number {
  return snapshot.progress.find((item) => item.bookId === bookId)?.progressPercent ?? 0;
}

function progressFromSessionScroll(session: MobileSnapshot["sessions"][number], fallback: number): number {
  const scroll = session.endLocation?.scroll ?? session.startLocation?.scroll;
  if (!scroll) return fallback;
  const scrollable = Math.max(1, scroll.scrollHeight - scroll.containerHeight);
  return Math.min(100, Math.max(0, (scroll.scrollTop / scrollable) * 100));
}

function isBookDownloaded(book: MobileBook): boolean {
  return Boolean(book.localFilePath || book.filePath?.startsWith("books/"));
}

function bookStorageLabel(book: MobileBook): string {
  return isBookDownloaded(book) ? "本机可读" : "需下载正文";
}

function parseTagInput(value: string): string[] {
  return Array.from(new Set(
    value
      .split(/[,\s，、#]+/)
      .map((tag) => tag.trim())
      .filter(Boolean)
  )).slice(0, 12);
}

function getContinueBooks(snapshot: MobileSnapshot): MobileBook[] {
  const recentBookIds = new Set(snapshot.progress.sort((a, b) => b.lastReadAt.localeCompare(a.lastReadAt)).map((item) => item.bookId));
  const byProgress = [...recentBookIds]
    .map((bookId) => snapshot.books.find((book) => book.id === bookId))
    .filter((book): book is MobileBook => Boolean(book));
  return [...byProgress, ...snapshot.books.filter((book) => !recentBookIds.has(book.id))].slice(0, 8);
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
    books: snapshot.books.map((item) => toMobileSyncEnvelope("book", item.id, item)),
    progress: snapshot.progress.map((item) => toMobileSyncEnvelope("progress", item.bookId, item)),
    sessions: snapshot.sessions.map((item) => toMobileSyncEnvelope("session", item.id, item))
  };
}

export function App() {
  const [tab, setTab] = useState<MainTab>("home");
  const [activeProfilePage, setActiveProfilePage] = useState<ProfileSubPage>();
  const [snapshot, setSnapshot] = useState<MobileSnapshot>(emptySnapshot);
  const [pairingText, setPairingText] = useState("");
  const [paired, setPaired] = useState<PairingInput>();
  const [readerBook, setReaderBook] = useState<MobileBook>();
  const [readerContent, setReaderContent] = useState("");
  const [readerLoadingState, setReaderLoadingState] = useState<{
    bookId: string;
    status: "loading" | "ready" | "error";
    message?: string;
  }>();
  const [readerSettings, setReaderSettings] = useState<MobileReaderSettings>(defaultReaderSettings);
  const [appTheme, setAppThemeState] = useState<MobileAppTheme>(() => loadMobileAppTheme());
  const [message, setMessage] = useState("");
  const [showQrScanner, setShowQrScanner] = useState(false);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);
  const [downloadingBookId, setDownloadingBookId] = useState<string>();
  const [shelfSearchFocusToken, setShelfSearchFocusToken] = useState(0);
  const [confirmDialog, setConfirmDialog] = useState<{
    title: string;
    message: string;
    onConfirm: () => void;
  } | null>(null);
  const downloadAbortRef = useRef<AbortController>();
  const lastBackAtRef = useRef(0);
  const openBookRequestRef = useRef(0);
  const readerContentCacheRef = useRef(new Map<string, string>());

  useEffect(() => {
    void loadMobileSnapshot().then(setSnapshot);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.mobileTheme = appTheme;
    localStorage.setItem("creation-reading-assistant-mobile-theme", appTheme);
  }, [appTheme]);

  const client = useMemo(() => (paired ? createSyncClient(paired) : undefined), [paired]);

  useEffect(() => {
    if (!message) return;
    const timeout = window.setTimeout(() => setMessage(""), 4200);
    return () => window.clearTimeout(timeout);
  }, [message]);
  const stats = useMemo(() => buildMobileStatsSummary(snapshot, "total"), [snapshot]);

  const goTab = (nextTab: MainTab) => {
    setTab(nextTab);
    if (nextTab !== "profile") setActiveProfilePage(undefined);
  };

  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return undefined;
    let removeListener: (() => void) | undefined;
    void CapacitorApp.addListener("backButton", () => {
      if (confirmDialog) {
        setConfirmDialog(null);
        return;
      }
      if (showQrScanner) {
        setShowQrScanner(false);
        return;
      }
      if (readerBook) {
        const readerBackEvent = new Event("mobile-reader-back", { cancelable: true });
        window.dispatchEvent(readerBackEvent);
        if (!readerBackEvent.defaultPrevented) closeMobileReader();
        return;
      }
      if (tab === "profile" && activeProfilePage) {
        setActiveProfilePage(undefined);
        return;
      }
      if (tab !== "home") {
        goTab("home");
        return;
      }
      const now = Date.now();
      if (now - lastBackAtRef.current < 1800) {
        void CapacitorApp.exitApp();
        return;
      }
      lastBackAtRef.current = now;
      setMessage("再按一次返回键退出应用。");
    }).then((handle) => {
      removeListener = () => {
        void handle.remove();
      };
    });
    return () => removeListener?.();
  }, [activeProfilePage, confirmDialog, readerBook, showQrScanner, tab]);

  const closeMobileReader = () => {
    openBookRequestRef.current += 1;
    setReaderBook(undefined);
    setReaderLoadingState(undefined);
  };

  const openShelfSearch = () => {
    goTab("shelf");
    setShelfSearchFocusToken((current) => current + 1);
    setMessage("已打开书架搜索，可以直接输入书名、作者或导入标签。");
  };

  const setAppTheme = (theme: MobileAppTheme) => {
    setAppThemeState(theme);
    setMessage(theme === "system" ? "已切换为跟随系统外观。" : theme === "dark" ? "已切换为深色外观。" : "已切换为浅色外观。");
  };

  const appendSyncLog = (entry: string) => {
    const line = `${new Date().toLocaleTimeString("zh-CN", { hour12: false })} · ${entry}`;
    setSyncLogs((current) => [line, ...current].slice(0, 8));
  };

  const pendingDownloadCount = snapshot.books.filter((book) => !isBookDownloaded(book)).length;

  const readMobileBookContent = async (book: MobileBook): Promise<string | undefined> => {
    const savedContent = localStorage.getItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
    if (savedContent) return savedContent;
    return readMobileBookFile(book.localFilePath ?? (book.filePath?.startsWith("books/") ? book.filePath : undefined), book.format);
  };

  const openBook = (book: MobileBook) => {
    const requestId = openBookRequestRef.current + 1;
    openBookRequestRef.current = requestId;
    const cachedContent = readerContentCacheRef.current.get(book.id);
    setReaderBook(book);
    setReaderContent(cachedContent ?? "");
    setReaderLoadingState({
      bookId: book.id,
      status: cachedContent ? "ready" : "loading",
      message: cachedContent ? undefined : "正在读取手机本地正文…"
    });
    if (cachedContent) return;

    window.requestAnimationFrame(() => {
      void readMobileBookContent(book)
        .then((content) => {
          if (openBookRequestRef.current !== requestId) return;
          const nextContent = content ?? `${book.title}\n\n这本书来自同步或历史数据，正文文件还没有下载到手机。请先在“我的 / 同步”里点“立即同步”，或在书架重新导入本地文件。`;
          readerContentCacheRef.current.set(book.id, nextContent);
          setReaderContent(nextContent);
          setReaderLoadingState({ bookId: book.id, status: "ready" });
        })
        .catch((error) => {
          if (openBookRequestRef.current !== requestId) return;
          const detail = error instanceof Error ? error.message : String(error);
          const nextContent = `${book.title}\n\n读取本地正文失败：${detail}\n\n可以回到书架重新导入，或在“我的 / 同步”里重新下载正文。`;
          setReaderContent(nextContent);
          setReaderLoadingState({ bookId: book.id, status: "error", message: detail });
          setMessage(`读取《${book.title}》失败：${detail}`);
        });
    });
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
      const next = await saveSyncedMobileBookBlob(snapshot, book, blob);
      readerContentCacheRef.current.delete(book.id);
      setSnapshot(next);
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
      setMessage(`下载正文失败：${detail}`);
    } finally {
      downloadAbortRef.current = undefined;
      setDownloadingBookId(undefined);
    }
  };

  const cancelBookDownload = () => {
    downloadAbortRef.current?.abort();
  };

  const uploadMobileBookFiles = async (baseSnapshot: MobileSnapshot): Promise<{ uploaded: number; failed: number }> => {
    if (!client) return { uploaded: 0, failed: 0 };
    const localDeviceId = getMobileDeviceId();
    let uploaded = 0;
    let failed = 0;
    for (const book of baseSnapshot.books) {
      if (book.deletedAt || book.deviceId !== localDeviceId) continue;
      const content = await readMobileBookContent(book);
      if (!content) continue;
      try {
        setMessage(`正在上传《${book.title}》到电脑端书库……`);
        await client.uploadBookFile(
          book.id,
          book.originalFileName ?? `${book.id}.${book.format}`,
          book.format === "epub" ? base64ToBlob(content, "application/epub+zip") : content
        );
        uploaded += 1;
      } catch {
        failed += 1;
      }
    }
    return { uploaded, failed };
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
    try {
      const push = await client.push(buildMobileSyncPushPayload(snapshot));
      const uploadSummary = await uploadMobileBookFiles(snapshot);
      const pull = await client.pull();
      const next: MobileSnapshot = {
        ...snapshot,
        inspirations: pull.inspirations.map((item) => item.payload),
        books: pull.books.map((item) => item.payload as MobileBook),
        progress: pull.progress.map((item) => item.payload),
        sessions: pull.sessions.map((item) => item.payload),
        updatedAt: new Date().toISOString()
      };
      await saveMobileSnapshot(next);
      setSnapshot(next);
      const nextPendingDownloadCount = next.books.filter((book) => !isBookDownloaded(book as MobileBook)).length;
      appendSyncLog(`同步完成：电脑返回 ${pull.books.length} 本书，待下载正文 ${nextPendingDownloadCount} 本`);
      setMessage(
        `同步完成：已上传手机上的 ${push.applied.inspirations} 条灵感、${push.applied.books} 本书、${push.applied.progress} 条进度；` +
          `电脑返回 ${pull.inspirations.length} 条灵感、${pull.books.length} 本书；` +
          `待下载正文 ${nextPendingDownloadCount} 本。${uploadSummary.failed ? "有少量文件上传失败，可再次点击立即同步。" : "需要阅读时在书架点“下载正文”。"}`
      );
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      appendSyncLog(`同步失败：${detail}`);
      setMessage(detail);
    }
  };

  const importFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    const selectedFiles = Array.from(files);
    const bookFiles = selectedFiles.filter((file) => isSupportedMobileBookFileName(file.name) && file.size > 0);
    const skippedCount = selectedFiles.length - bookFiles.length;
    if (!bookFiles.length) {
      setMessage("没有找到可导入的 TXT / Markdown / EPUB 文件；已跳过系统隐藏文件。");
      return;
    }
    let next = snapshot;
    let importedCount = 0;
    for (const file of bookFiles) {
      const content = await readImportFileContent(file);
      const imported = await createImportedMobileBook(file.name, content, file.size);
      next = await saveMobileBook(next, imported);
      const savedBook = next.books.find((book) => book.contentHash === imported.contentHash && book.originalFileName === imported.originalFileName);
      if (savedBook) {
        readerContentCacheRef.current.set(savedBook.id, content);
        if (imported.format !== "epub" && file.size <= 3 * 1024 * 1024) {
          localStorage.setItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${savedBook.id}`, content);
        }
      }
      importedCount += 1;
    }
    setSnapshot(next);
    setMessage(
      skippedCount
        ? `已导入 ${importedCount} 本书，跳过 ${skippedCount} 个非书籍或系统文件；重复书籍会用“重复导入 #”标签区分。`
        : `已导入 ${importedCount} 本书；重复书籍会用“重复导入 #”标签区分。`
    );
  };

  const addQuickInspiration = async () => {
    const next = await addMobileInspiration(snapshot, {
      title: "新的灵感",
      body: "",
      tags: ["快速记录"]
    });
    setSnapshot(next);
    goTab("inspiration");
    setMessage("已创建一条空白灵感，可以继续补正文或交给 AI 打磨。");
  };

  const bottomTabs: Array<{ key: MainTab; label: string; icon: ReactNode }> = [
    { key: "home", label: "首页", icon: <Home size={24} /> },
    { key: "shelf", label: "书架", icon: <BookOpen size={24} /> },
    { key: "inspiration", label: "灵感", icon: <Sparkles size={24} /> },
    { key: "stats", label: "统计", icon: <BarChart3 size={24} /> },
    { key: "profile", label: "我的", icon: <User size={24} /> }
  ];

  if (readerBook) {
    return (
      <MobileReaderView
        book={readerBook}
        content={readerContent}
        loading={readerLoadingState?.bookId === readerBook.id && readerLoadingState.status === "loading"}
        loadError={readerLoadingState?.bookId === readerBook.id && readerLoadingState.status === "error" ? readerLoadingState.message : undefined}
        snapshot={snapshot}
        settings={readerSettings}
        onSettingsChange={setReaderSettings}
        onSnapshotChange={setSnapshot}
        onBack={closeMobileReader}
        onOpenInspiration={() => {
          setReaderBook(undefined);
          goTab("inspiration");
          setMessage("已打开灵感中心，刚保存的阅读灵感在列表最上方。");
        }}
        onMessage={setMessage}
      />
    );
  }

  return (
    <main className="mobile-shell">
      <section className="phone-page">
        {message && <div className="toast-line">{message}</div>}
        {tab === "home" && (
          <HomePage
            snapshot={snapshot}
            stats={stats}
            paired={Boolean(paired)}
            onOpenBook={(book) => void openBook(book)}
            onAddInspiration={() => void addQuickInspiration()}
            onGo={goTab}
            onSearchBooks={openShelfSearch}
          />
        )}
        {tab === "shelf" && (
          <ShelfPage
            snapshot={snapshot}
            downloadingBookId={downloadingBookId}
            searchFocusToken={shelfSearchFocusToken}
            onOpenBook={(book) => void openBook(book)}
            onImport={(files) => void importFiles(files)}
            onDownloadBook={(book) => void downloadBookToMobile(book)}
            onCancelDownload={cancelBookDownload}
            onSnapshotChange={setSnapshot}
            onMessage={setMessage}
          />
        )}
        {tab === "inspiration" && (
          <InspirationPage
            snapshot={snapshot}
            onSnapshotChange={setSnapshot}
            onConfirm={setConfirmDialog}
            onMessage={setMessage}
          />
        )}
        {tab === "stats" && <StatsPage snapshot={snapshot} />}
        {tab === "profile" && (
          <ProfilePage
            snapshot={snapshot}
            pairingText={pairingText}
            paired={Boolean(paired)}
            onPairingTextChange={setPairingText}
            onConnectLan={() => void connectLan()}
            onScanQr={() => void scanPairingQrCode()}
            onSyncDesktop={() => void syncFromDesktop()}
            pendingDownloadCount={pendingDownloadCount}
            syncLogs={syncLogs}
            onSnapshotChange={setSnapshot}
            onMessage={setMessage}
            onConfirm={setConfirmDialog}
            activePage={activeProfilePage}
            onSetActivePage={setActiveProfilePage}
            appTheme={appTheme}
            onAppThemeChange={setAppTheme}
            onOpenBook={(book) => void openBook(book)}
          />
        )}
      </section>

      {showQrScanner && (
        <QrScanOverlay
          onResult={(text) => void handleQrScanResult(text)}
          onClose={() => setShowQrScanner(false)}
          onFallbackPaste={() => {
            setShowQrScanner(false);
            setMessage("请粘贴电脑端配对 URL 或二维码载荷，然后点“连接电脑”。");
          }}
          onMessage={setMessage}
        />
      )}

      {!(tab === "profile" && activeProfilePage) && (
        <nav className="bottom-nav" aria-label="主导航">
          {bottomTabs.map((item) => (
            <button key={item.key} className={tab === item.key ? "active" : ""} onClick={() => goTab(item.key)}>
              <span>{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>
      )}

      {confirmDialog && (
        <ConfirmDialog
          title={confirmDialog.title}
          message={confirmDialog.message}
          onConfirm={confirmDialog.onConfirm}
          onCancel={() => setConfirmDialog(null)}
        />
      )}
    </main>
  );
}

function ConfirmDialog({
  title,
  message,
  onConfirm,
  onCancel
}: {
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onCancel();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  useEffect(() => {
    confirmRef.current?.focus();
  }, []);

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog-content" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby="confirm-dialog-message" onClick={(e) => e.stopPropagation()}>
        <h3 id="confirm-dialog-title" className="dialog-title">{title}</h3>
        <p id="confirm-dialog-message" className="dialog-message">{message}</p>
        <div className="dialog-actions">
          <button className="dialog-button cancel" onClick={onCancel}>
            取消
          </button>
          <button ref={confirmRef} className="dialog-button confirm" onClick={onConfirm}>
            确认
          </button>
        </div>
      </div>
    </div>
  );
}

function QrScanOverlay({
  onResult,
  onClose,
  onFallbackPaste,
  onMessage
}: {
  onResult: (text: string) => void;
  onClose: () => void;
  onFallbackPaste: () => void;
  onMessage: (message: string) => void;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let stream: MediaStream | undefined;
    let stopped = false;
    let timer: number | undefined;
    let timeout: number | undefined;

    const stop = () => {
      stopped = true;
      if (timer) window.clearTimeout(timer);
      if (timeout) window.clearTimeout(timeout);
      stream?.getTracks().forEach((track) => track.stop());
    };

    const scanFrame = () => {
      if (stopped) return;
      const video = videoRef.current;
      const canvas = canvasRef.current;
      const context = canvas?.getContext("2d", { willReadFrequently: true });
      if (video && canvas && context && video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth && video.videoHeight) {
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        context.drawImage(video, 0, 0, canvas.width, canvas.height);
        const imageData = context.getImageData(0, 0, canvas.width, canvas.height);
        const code = jsQR(imageData.data, imageData.width, imageData.height);
        if (code?.data) {
          stop();
          onResult(code.data);
          return;
        }
      }
      timer = window.setTimeout(scanFrame, 260);
    };

    const start = async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) throw new Error("mediaDevices unavailable");
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: "environment" } }
        });
        const video = videoRef.current;
        if (!video) return;
        video.srcObject = stream;
        await video.play();
        onMessage("摄像头已打开，请把电脑端二维码放进取景框。");
        timeout = window.setTimeout(() => {
          setError("扫码超时。请靠近二维码、提高电脑屏幕亮度，或改用粘贴配对 URL。");
        }, 45_000);
        scanFrame();
      } catch (err) {
        const message = formatQrScanError(err);
        setError(message);
        onMessage(message);
      }
    };

    void start();
    return stop;
  }, [onMessage, onResult]);

  return (
    <section className="qr-scan-overlay" role="dialog" aria-modal="true" aria-label="扫码连接电脑">
      <div className="qr-scan-panel">
        <header className="qr-scan-header">
          <div>
            <strong>扫码连接电脑</strong>
            <p>把电脑端二维码放进取景框，识别后会自动连接。</p>
          </div>
          <button className="ghost-button" onClick={onClose}>
            关闭
          </button>
        </header>
        <div className="qr-video-frame">
          <video ref={videoRef} className="qr-video" muted playsInline />
          <canvas ref={canvasRef} hidden />
          <div className="qr-corners" aria-hidden="true" />
        </div>
        {error && <p className="qr-scan-error">{error}</p>}
        <div className="qr-scan-actions">
          <button onClick={onFallbackPaste}>粘贴配对 URL</button>
          <button className="ghost-button" onClick={onClose}>
            稍后再扫
          </button>
        </div>
      </div>
    </section>
  );
}

function HomePage({
  snapshot,
  stats,
  paired,
  onOpenBook,
  onAddInspiration,
  onGo,
  onSearchBooks
}: {
  snapshot: MobileSnapshot;
  stats: { totalReadingMs: number; readingDays: number; completed: number; words: number; speed: number };
  paired: boolean;
  onOpenBook: (book: MobileBook) => void;
  onAddInspiration: () => void;
  onGo: (tab: MainTab) => void;
  onSearchBooks: () => void;
}) {
  const continueBooks = useMemo(() => getContinueBooks(snapshot), [snapshot.books, snapshot.progress]);
  return (
    <div className="screen-stack">
      <header className="mobile-header row-header home-header">
        <div>
          <p className="mini-label">创作阅读助手</p>
          <h1>首页</h1>
        </div>
        <button className="round-action" onClick={onSearchBooks} aria-label="打开书架搜索">
          <Search size={22} />
        </button>
      </header>

      <section className="home-summary-row" aria-label="阅读概览">
        <button className="home-summary-card" onClick={() => onGo("shelf")}>
          <span><Book size={21} /></span>
          <div>
            <small>累计阅读</small>
            <strong>{snapshot.books.length} 本</strong>
          </div>
        </button>
        <button className="home-summary-card" onClick={() => onGo("stats")}>
          <span><Clock size={21} /></span>
          <div>
            <small>阅读时长</small>
            <strong>{formatDuration(stats.totalReadingMs)}</strong>
          </div>
        </button>
      </section>

      <section className="section-block home-section">
        <div className="section-heading">
          <h2>继续阅读</h2>
          <button className="ghost-button" onClick={() => onGo("shelf")}>
            全部书籍 ›
          </button>
        </div>
        <div className="continue-strip">
          {continueBooks.length ? (
            continueBooks.map((book) => {
              const progress = progressFor(snapshot, book.id);
              return (
              <button key={book.id} className="continue-card" onClick={() => onOpenBook(book)}>
                <div className="book-cover compact">{book.title.slice(0, 2)}</div>
                <div className="continue-card-main">
                  <span>{book.title}</span>
                  <small>{book.author || "作者未知"}</small>
                  <div className="continue-progress-row">
                    <div className="book-progress-line" aria-label={`阅读进度 ${progress.toFixed(1)}%`}>
                      <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
                    </div>
                    <em>{progress.toFixed(1)}%</em>
                  </div>
                </div>
              </button>
            );
            })
          ) : (
            <button className="continue-empty" onClick={() => onGo("shelf")}>
              书架还空着，先导入一本 TXT、Markdown 或 EPUB。
            </button>
          )}
        </div>
      </section>

      <section className="home-inspiration-mini">
        <div>
          <p className="mini-label">灵感中心</p>
          <h2>读到有火花的地方，就把它留下</h2>
          <p>{snapshot.inspirations.length} 条灵感 · AI 候选保留在灵感里</p>
        </div>
        <button className="inspiration-fab compact-fab" onClick={onAddInspiration} aria-label="记录灵感">
          <Sparkles size={22} />
        </button>
      </section>
    </div>
  );
}

/* === Memoized Book Tile === */
const BookTile = React.memo(function BookTile({
  book,
  progress,
  downloaded,
  viewMode,
  onOpenBook,
  onShowDetail
}: {
  book: MobileBook;
  progress: number;
  downloaded: boolean;
  viewMode: ShelfViewMode;
  onOpenBook: (book: MobileBook) => void;
  onShowDetail: (bookId: string) => void;
}) {
  const tileRef = useRef<HTMLElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const tile = tileRef.current;
    if (!tile || !("IntersectionObserver" in window)) {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: "100px" }
    );
    observer.observe(tile);
    return () => observer.disconnect();
  }, []);

  return (
    <article
      ref={tileRef}
      className="book-tile"
      onClick={() => onOpenBook(book)}
      onTouchStart={(e) => {
        const target = e.currentTarget;
        const timeout = setTimeout(() => {
          onShowDetail(book.id);
        }, 500);
        target.dataset.longPressTimeout = String(timeout);
      }}
      onTouchEnd={(e) => {
        const target = e.currentTarget;
        const timeout = target.dataset.longPressTimeout;
        if (timeout) {
          clearTimeout(Number(timeout));
          delete target.dataset.longPressTimeout;
        }
      }}
      onTouchMove={(e) => {
        const target = e.currentTarget;
        const timeout = target.dataset.longPressTimeout;
        if (timeout) {
          clearTimeout(Number(timeout));
          delete target.dataset.longPressTimeout;
        }
      }}
    >
      {visible ? (
        <>
          <div className="book-cover">{book.title.slice(0, 4)}</div>
          <div className="book-meta">
            <h3>{book.title}</h3>
            {viewMode === "list" ? (
              <>
                <p>{book.author || "作者未知"}</p>
                <small>{progress.toFixed(2)}% · {book.format.toUpperCase()}</small>
                <div className="book-badges">
                  <em>{bookStorageLabel(book)}</em>
                  {book.duplicateIndex && book.duplicateIndex > 1 ? <em>{book.importLabel}</em> : null}
                </div>
              </>
            ) : (
              <small className="grid-progress-text">{progress > 0 ? `${progress.toFixed(1)}%` : "未读过"}</small>
            )}
            <div className="book-progress-line" aria-label={`阅读进度 ${progress.toFixed(1)}%`}>
              <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
            </div>
          </div>
          <button
            className="tile-more"
            onClick={(event) => {
              event.stopPropagation();
              onShowDetail(book.id);
            }}
            aria-label={`查看《${book.title}》详情`}
          >
            <MoreHorizontal size={14} strokeWidth={2.4} aria-hidden="true" />
          </button>
        </>
      ) : (
        <div className="book-cover" style={{ visibility: "hidden" }}>{book.title.slice(0, 4)}</div>
      )}
    </article>
  );
});

function ShelfPage({
  snapshot,
  downloadingBookId,
  searchFocusToken,
  onOpenBook,
  onImport,
  onDownloadBook,
  onCancelDownload,
  onSnapshotChange,
  onMessage
}: {
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  searchFocusToken: number;
  onOpenBook: (book: MobileBook) => void;
  onImport: (files: FileList | null) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
}) {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [viewMode, setViewMode] = useState<ShelfViewMode>("grid");
  const [sortMode, setSortMode] = useState<ShelfSortMode>("recent");
  const [selectedShelfId, setSelectedShelfId] = useState("");
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [selectedTagName, setSelectedTagName] = useState("");
  const [detailBookId, setDetailBookId] = useState("");
  const searchInputRef = useRef<HTMLInputElement>(null);
  const detailBook = detailBookId ? snapshot.books.find((book) => book.id === detailBookId) : undefined;

  useEffect(() => {
    if (!searchFocusToken) return;
    window.requestAnimationFrame(() => {
      searchInputRef.current?.focus();
      searchInputRef.current?.select();
    });
  }, [searchFocusToken]);

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(timer);
  }, [query]);

  // Memoize filtered and sorted books
  const filtered = useMemo(() => {
    const lowerQuery = debouncedQuery.toLowerCase();
    return snapshot.books
      .filter((book) => `${book.title} ${book.author ?? ""} ${book.importLabel ?? ""}`.toLowerCase().includes(lowerQuery))
      .filter((book) => !selectedShelfId || snapshot.shelves.find((shelf) => shelf.id === selectedShelfId)?.bookIds.includes(book.id))
      .filter((book) => !selectedCategoryId || book.categoryIds?.includes(selectedCategoryId))
      .filter((book) => !selectedTagName || book.tagNames?.includes(selectedTagName))
      .sort((left, right) => {
        if (sortMode === "title") return left.title.localeCompare(right.title, "zh-Hans-CN");
        if (sortMode === "progress") return progressFor(snapshot, right.id) - progressFor(snapshot, left.id);
        const leftProgress = snapshot.progress.find((item) => item.bookId === left.id)?.lastReadAt ?? left.updatedAt;
        const rightProgress = snapshot.progress.find((item) => item.bookId === right.id)?.lastReadAt ?? right.updatedAt;
        return rightProgress.localeCompare(leftProgress);
      });
  }, [snapshot.books, snapshot.progress, snapshot.shelves, debouncedQuery, selectedShelfId, selectedCategoryId, selectedTagName, sortMode]);
  const activeShelf = selectedShelfId ? snapshot.shelves.find((item) => item.id === selectedShelfId) : undefined;
  const activeCategory = selectedCategoryId ? snapshot.categories.find((item) => item.id === selectedCategoryId) : undefined;
  const bookTagNames = useMemo(() => {
    const names = new Set<string>();
    snapshot.tags.filter((tag) => tag.type === "book").forEach((tag) => names.add(tag.name));
    snapshot.books.forEach((book) => book.tagNames?.forEach((tag) => names.add(tag)));
    return [...names].sort((left, right) => left.localeCompare(right, "zh-Hans-CN"));
  }, [snapshot.books, snapshot.tags]);

  if (detailBook) {
    return (
      <BookDetailSheet
        book={detailBook}
        snapshot={snapshot}
        downloadingBookId={downloadingBookId}
        onClose={() => setDetailBookId("")}
        onOpenBook={(book) => {
          setDetailBookId("");
          onOpenBook(book);
        }}
        onDownloadBook={onDownloadBook}
        onCancelDownload={onCancelDownload}
        onSnapshotChange={onSnapshotChange}
        onMessage={onMessage}
      />
    );
  }

  return (
    <div className="screen-stack">
      <header className="mobile-header row-header">
        <div>
          <p className="mini-label">本地书库</p>
          <h1>书架</h1>
        </div>
        <label className="import-action" aria-label="导入本地书籍">
          <span className="import-action-plus">＋</span>
          <span>导入</span>
          <input hidden type="file" accept=".txt,.md,.markdown,.epub" multiple onChange={(event) => void onImport(event.currentTarget.files)} />
        </label>
      </header>

      <label className="search-pill">
        <Search size={19} strokeWidth={2.2} />
        <input ref={searchInputRef} value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`搜索 ${snapshot.books.length} 本书`} />
      </label>

      <section className="shelf-toolbar" aria-label="书架筛选和视图">
        <div className="shelf-subtoolbar">
          <div className="shelf-sort-chips" role="group" aria-label="书籍排序">
            {shelfSortOptions.map((option) => (
              <button
                key={option.value}
                className={sortMode === option.value ? "active" : ""}
                onClick={() => setSortMode(option.value)}
                type="button"
              >
                {option.label}{option.value === "recent" && sortMode === "recent" ? " ▾" : ""}
              </button>
            ))}
          </div>
          <div className="view-toggle" aria-label="书架视图">
            <button className={viewMode === "grid" ? "active" : ""} onClick={() => setViewMode("grid")} aria-label="网格视图">
              <Grid size={18} />
            </button>
            <button className={viewMode === "list" ? "active" : ""} onClick={() => setViewMode("list")} aria-label="列表视图">
              <List size={18} />
            </button>
          </div>
        </div>
      </section>

      {Boolean(snapshot.shelves.length || snapshot.categories.length || bookTagNames.length) && (
        <section className="shelf-filter-rails" aria-label="书单和分类筛选">
          {snapshot.shelves.length ? (
            <div className="filter-chip-rail">
              <button className={!selectedShelfId ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedShelfId("")}>
                全部书单
              </button>
              {snapshot.shelves.map((shelf) => (
                <button key={shelf.id} className={selectedShelfId === shelf.id ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedShelfId(shelf.id)}>
                  {shelf.name}
                </button>
              ))}
            </div>
          ) : null}
          {snapshot.categories.length ? (
            <div className="filter-chip-rail">
              <button className={!selectedCategoryId ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedCategoryId("")}>
                全部分类
              </button>
              {snapshot.categories.map((category) => {
                return (
                  <button key={category.id} className={selectedCategoryId === category.id ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedCategoryId(category.id)}>
                    {category.name}
                  </button>
                );
              })}
            </div>
          ) : null}
          {bookTagNames.length ? (
            <div className="filter-chip-rail">
              <button className={!selectedTagName ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedTagName("")}>
                全部标签
              </button>
              {bookTagNames.map((tag) => {
                return (
                  <button key={tag} className={selectedTagName === tag ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedTagName(tag)}>
                    {tag}
                  </button>
                );
              })}
            </div>
          ) : null}
        </section>
      )}

      {(activeShelf || activeCategory || selectedTagName || debouncedQuery) && (() => {
        const parts: string[] = [];
        if (activeShelf) parts.push(`书单「${activeShelf.name}」`);
        if ((activeShelf && activeCategory) || (activeShelf && selectedTagName)) parts.push(" / ");
        if (activeCategory) parts.push(`分类「${activeCategory.name}」`);
        if (activeCategory && selectedTagName) parts.push(" / ");
        if (selectedTagName) parts.push(`标签「${selectedTagName}」`);
        if (!parts.length) {
          if (debouncedQuery) parts.push(`搜索"${debouncedQuery}"`);
        } else {
          if (debouncedQuery) parts.push(`搜索"${debouncedQuery}"`);
        }
        return (
          <div className="active-filter-note">
            {parts.join("")}
            {" "}·
            <button onClick={() => {
              setQuery("");
              setSelectedShelfId("");
              setSelectedCategoryId("");
              setSelectedTagName("");
            }}>重置全部</button>
          </div>
        );
      })()}

      <section className={`book-grid ${viewMode === "list" ? "book-list" : ""}`}>
        {filtered.map((book) => (
          <BookTile
            key={book.id}
            book={book}
            progress={progressFor(snapshot, book.id)}
            downloaded={isBookDownloaded(book)}
            viewMode={viewMode}
            onOpenBook={onOpenBook}
            onShowDetail={setDetailBookId}
          />
        ))}
      </section>

      {!filtered.length && (
        <section className="empty-state shelf-empty-state">
          <BookOpen size={24} strokeWidth={1.9} />
          <strong>{snapshot.books.length ? "没有找到匹配的书" : "书架还空着"}</strong>
          <p>{snapshot.books.length ? "换个关键词，或先回到“全部”看看。" : "导入 TXT、Markdown 或 EPUB 后，这里会变成你的本地书架。"}</p>
          {snapshot.books.length ? (
            <button className="secondary" onClick={() => {
              setQuery("");
            }}>
              显示全部
            </button>
          ) : (
            <label className="empty-import-action">
              导入一本书
              <input hidden type="file" accept=".txt,.md,.markdown,.epub" multiple onChange={(event) => void onImport(event.currentTarget.files)} />
            </label>
          )}
        </section>
      )}
      {snapshot.books.length > 0 && (
        <p className="center-foot shelf-count-foot">共 {snapshot.books.length} 本书籍 · 当前显示 {filtered.length} 本</p>
      )}

    </div>
  );
}

function BookDetailSheet({
  book,
  snapshot,
  downloadingBookId,
  onClose,
  onOpenBook,
  onDownloadBook,
  onCancelDownload,
  onSnapshotChange,
  onMessage
}: {
  book: MobileBook;
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  onClose: () => void;
  onOpenBook: (book: MobileBook) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
}) {
  const progress = progressFor(snapshot, book.id);
  const downloaded = isBookDownloaded(book);
  const inspirationCount = snapshot.inspirations.filter((item) => item.source?.bookId === book.id).length;
  const bookSessions = snapshot.sessions
    .filter((item) => item.bookId === book.id)
    .sort((left, right) => right.startAt.localeCompare(left.startAt));
  const recentSessions = bookSessions.slice(0, 3);
  const bookNotes = snapshot.notes
    .filter((item) => item.bookId === book.id && !item.deletedAt)
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  const bookmarkCount = bookNotes.filter((item) => item.kind === "bookmark").length;
  const noteCount = bookNotes.filter((item) => item.kind !== "bookmark").length;
  const recentNotes = bookNotes.slice(0, 3);
  const bookTagNames = useMemo(() => {
    const names = new Set<string>();
    snapshot.tags.filter((tag) => tag.type === "book").forEach((tag) => names.add(tag.name));
    book.tagNames?.forEach((tag) => names.add(tag));
    return [...names].sort((left, right) => left.localeCompare(right, "zh-Hans-CN"));
  }, [book.tagNames, snapshot.tags]);

  const toggleBookTag = async (tagName: string) => {
    const timestamp = new Date().toISOString();
    const currentTagNames = book.tagNames ?? [];
    const inTag = currentTagNames.includes(tagName);
    const nextBook: MobileBook = {
      ...book,
      tagNames: inTag ? currentTagNames.filter((item) => item !== tagName) : [tagName, ...currentTagNames],
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inTag ? `已移除书籍标签「${tagName}」。` : `已给《${book.title}》添加标签「${tagName}」。`);
  };

  const toggleCategory = async (categoryId: string) => {
    const timestamp = new Date().toISOString();
    const targetCategory = snapshot.categories.find((item) => item.id === categoryId);
    if (!targetCategory) return;
    const currentCategoryIds = book.categoryIds ?? [];
    const inCategory = currentCategoryIds.includes(categoryId);
    const nextBook: MobileBook = {
      ...book,
      categoryIds: inCategory ? currentCategoryIds.filter((id) => id !== categoryId) : [categoryId, ...currentCategoryIds],
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      books: [nextBook, ...snapshot.books.filter((item) => item.id !== book.id)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inCategory ? `已从分类「${targetCategory.name}」移除《${book.title}》。` : `已把《${book.title}》加入分类「${targetCategory.name}」。`);
  };

  const toggleShelf = async (shelfId: string) => {
    const timestamp = new Date().toISOString();
    const targetShelf = snapshot.shelves.find((item) => item.id === shelfId);
    if (!targetShelf) return;
    const inShelf = targetShelf.bookIds.includes(book.id);
    const nextShelf = {
      ...targetShelf,
      bookIds: inShelf ? targetShelf.bookIds.filter((id) => id !== book.id) : [book.id, ...targetShelf.bookIds],
      updatedAt: timestamp,
      revision: (targetShelf.revision ?? 0) + 1
    };
    const next = {
      ...snapshot,
      shelves: [nextShelf, ...snapshot.shelves.filter((item) => item.id !== shelfId)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(inShelf ? `已从书单「${targetShelf.name}」移除《${book.title}》。` : `已把《${book.title}》加入书单「${targetShelf.name}」。`);
  };
  return (
    <div className="screen-stack book-detail-page" role="region" aria-label={`${book.title} 详情`}>
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={onClose}>← 返回</button>
        <div>
          <p className="mini-label">本地书库</p>
          <h1>书籍详情</h1>
        </div>
      </header>
      <section className="book-detail-hero">
        <div className="book-cover detail-cover">{book.title.slice(0, 4)}</div>
        <div>
          <p className="mini-label">{book.format.toUpperCase()} · {bookStorageLabel(book)}</p>
          <h3>{book.title}</h3>
          <p>{book.author || "作者未知"}</p>
          {book.importLabel && <span className="detail-badge">{book.importLabel}</span>}
        </div>
      </section>
      <div className="book-detail-progress">
        <div>
          <strong>{progress.toFixed(2)}%</strong>
          <span>阅读进度</span>
        </div>
        <div>
          <strong>{bookSessions.length}</strong>
          <span>阅读记录</span>
        </div>
        <div>
          <strong>{inspirationCount}</strong>
          <span>灵感</span>
        </div>
      </div>
      <div className="book-progress-line detail-line" aria-label={`阅读进度 ${progress.toFixed(1)}%`}>
        <span style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
      </div>
      <div className="book-detail-actions">
        <button onClick={() => (downloaded ? onOpenBook(book) : onDownloadBook(book))}>
          {downloaded ? "开始阅读" : "下载后阅读"}
        </button>
        <button
          className="secondary-button"
          disabled={downloaded && downloadingBookId !== book.id}
          onClick={() => {
            if (downloadingBookId === book.id) onCancelDownload();
            else onDownloadBook(book);
          }}
        >
          {downloadingBookId === book.id ? "取消下载" : downloaded ? "正文已下载" : "下载正文"}
        </button>
      </div>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>阅读记录</strong>
          <span>{formatDuration(snapshot.progress.find((item) => item.bookId === book.id)?.totalReadingTimeMs ?? 0)}累计</span>
        </div>
        {recentSessions.length ? recentSessions.map((session) => (
          <article key={session.id} className="book-detail-timeline-item">
            <div>
              <strong>{formatCompactDateTime(session.startAt)}</strong>
              <span>{formatDuration(session.activeDurationMs || session.durationMs)} · {session.status === "recovered" ? "异常恢复" : "已记录"}</span>
            </div>
            <em>{progressFromSessionScroll(session, progress).toFixed(1)}%</em>
          </article>
        )) : <p className="empty-hint">还没有阅读记录。开始阅读后，这里会显示最近几次阅读。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>书签与笔记</strong>
          <span>{bookmarkCount} 个书签 · {noteCount} 条笔记</span>
        </div>
        {recentNotes.length ? recentNotes.map((note) => (
          <article key={note.id} className="book-detail-note-preview">
            <strong>{note.kind === "bookmark" ? "书签" : "笔记"} · {(note.progressPercent ?? progress).toFixed(1)}%</strong>
            <p>{note.excerpt || note.body || note.chapterTitle || "当前位置"}</p>
          </article>
        )) : <p className="empty-hint">阅读时点“书签”或“笔记”，这本书的沉淀会集中在这里。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>所在书单</strong>
          <span>{snapshot.shelves.filter((item) => item.bookIds.includes(book.id)).length} 个</span>
        </div>
        {snapshot.shelves.length ? (
          <div className="shelf-chip-list">
            {snapshot.shelves.map((shelf) => {
              const active = shelf.bookIds.includes(book.id);
              return (
                <button key={shelf.id} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleShelf(shelf.id)}>
                  {active ? "✓ " : "+ "}{shelf.name}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有书单。可以在“我的 / 书单管理”里先创建。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>所属分类</strong>
          <span>{book.categoryIds?.length ?? 0} 个</span>
        </div>
        {snapshot.categories.length ? (
          <div className="shelf-chip-list">
            {snapshot.categories.map((category) => {
              const active = book.categoryIds?.includes(category.id) ?? false;
              return (
                <button key={category.id} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleCategory(category.id)}>
                  {active ? "✓ " : "+ "}{category.name}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有分类。可以在“我的 / 分类管理”里先创建。</p>}
      </section>
      <section className="book-detail-insights">
        <div className="book-detail-section-title">
          <strong>书籍标签</strong>
          <span>{book.tagNames?.length ?? 0} 个</span>
        </div>
        {bookTagNames.length ? (
          <div className="shelf-chip-list">
            {bookTagNames.map((tagName) => {
              const active = book.tagNames?.includes(tagName) ?? false;
              return (
                <button key={tagName} className={active ? "shelf-chip active" : "shelf-chip"} onClick={() => void toggleBookTag(tagName)}>
                  {active ? "✓ " : "+ "}{tagName}
                </button>
              );
            })}
          </div>
        ) : <p className="empty-hint">还没有书籍标签。可以在“我的 / 标签管理”里创建类型为“书籍”的标签。</p>}
      </section>
    </div>
  );
}

function InspirationPage({
  snapshot,
  onSnapshotChange,
  onConfirm,
  onMessage
}: {
  snapshot: MobileSnapshot;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
  onMessage: (message: string) => void;
}) {
  const [draftTitle, setDraftTitle] = useState("");
  const [draft, setDraft] = useState("");
  const [draftTags, setDraftTags] = useState("快速记录");
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editBody, setEditBody] = useState("");
  const [editTags, setEditTags] = useState("");
  const [editStatus, setEditStatus] = useState<MobileSnapshot["inspirations"][number]["status"]>("inbox");
  const [aiBusy, setAiBusy] = useState<string>();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const aiActions: Array<[AIRunAction, string]> = [
    ["polish", "润色"],
    ["expand", "扩写"],
    ["platform-style", "平台风格化"],
    ["conflict", "生成冲突"],
    ["humanize", "去 AI 味"]
  ];

  // Debounce search query
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(timer);
  }, [query]);

  // Memoize filtered inspirations
  const filtered = useMemo(() => {
    const lowerQuery = debouncedQuery.toLowerCase();
    return snapshot.inspirations.filter((item) =>
      `${item.title} ${item.body} ${item.tags.join(" ")} ${item.source?.bookTitle ?? ""}`.toLowerCase().includes(lowerQuery)
    );
  }, [snapshot.inspirations, debouncedQuery]);

  const addDraft = async () => {
    const tags = parseTagInput(draftTags);
    const next = await addMobileInspiration(snapshot, {
      title: draftTitle.trim() || "未命名灵感",
      body: draft,
      tags: tags.length ? tags : ["快速记录"]
    });
    onSnapshotChange(next);
    setDraftTitle("");
    setDraft("");
    setDraftTags("快速记录");
  };

  const startEdit = (item: typeof snapshot.inspirations[0]) => {
    setEditingId(item.id);
    setEditTitle(item.title);
    setEditBody(item.body);
    setEditTags(item.tags.join("，"));
    setEditStatus(((item.status as string) === "draft" ? "usable" : item.status) as InspirationStatus);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditTitle("");
    setEditBody("");
    setEditTags("");
    setEditStatus("inbox");
  };

  const saveEdit = async () => {
    if (!editingId) return;
    const next = await updateMobileInspiration(snapshot, editingId, {
      title: editTitle,
      body: editBody,
      tags: parseTagInput(editTags),
      status: editStatus
    });
    onSnapshotChange(next);
    cancelEdit();
  };

  const handleDelete = (id: string) => {
    onConfirm({
      title: "删除灵感",
      message: "确定要删除这条灵感吗？删除后无法恢复。",
      onConfirm: async () => {
        const next = await deleteMobileInspiration(snapshot, id);
        onSnapshotChange(next);
        if (selectedId === id) setSelectedId(null);
        onConfirm(null);
      }
    });
  };

  const runInspirationAI = async (id: string, action: AIRunAction) => {
    const item = snapshot.inspirations.find((entry) => entry.id === id);
    if (!item) return;
    const content = [item.body, item.source?.excerpt].filter(Boolean).join("\n\n").trim();
    if (!content) {
      onMessage("这条灵感还没有正文或来源摘录，先写一点内容再让 AI 打磨。");
      return;
    }
    setAiBusy(`${id}:${action}`);
    try {
      const result = await runMobileAIAction({
        action,
        title: item.title,
        content,
        platform: item.platformTags[0]
      });
      const next = await addMobileInspirationVariant(snapshot, id, result);
      onSnapshotChange(next);
      onMessage(`已生成「${aiActions.find(([key]) => key === action)?.[1] ?? "AI"}」候选版本，原文没有被覆盖。`);
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    } finally {
      setAiBusy(undefined);
    }
  };

  const copyInspirationVariant = async (content: string) => {
    try {
      await navigator.clipboard.writeText(content);
      onMessage("已复制 AI 候选版本。");
    } catch {
      onMessage("复制失败。可以长按候选文本手动选择复制。");
    }
  };

  const adoptInspirationVariant = async (id: string, content: string) => {
    const next = await updateMobileInspiration(snapshot, id, { body: content });
    onSnapshotChange(next);
    onMessage("已采用候选版本为正文；候选记录仍保留，可继续对比。");
  };

  const removeVariant = async (inspirationId: string, variantId: string) => {
    const item = snapshot.inspirations.find((i) => i.id === inspirationId);
    if (!item || !item.variants.length) return;
    const next = await updateMobileInspiration(snapshot, inspirationId, {
      variants: item.variants.filter((v) => v.id !== variantId)
    });
    onSnapshotChange(next);
  };

  const selectedItem = selectedId ? snapshot.inspirations.find((item) => item.id === selectedId) : undefined;

  if (selectedItem) {
    return (
      <div className="screen-stack inspiration-detail-screen">
        <header className="mobile-header row-header">
          <button className="ghost-button back-button" onClick={() => {
            cancelEdit();
            setSelectedId(null);
          }}>
            ← 返回
          </button>
          <div>
            <p className="mini-label">{getInspirationStatusLabel(selectedItem.status)} · {selectedItem.type}</p>
            <h1>灵感详情</h1>
          </div>
        </header>

        <section className="inspiration-detail-card">
          {editingId === selectedItem.id ? (
            <>
              <input
                type="text"
                value={editTitle}
                onChange={(event) => setEditTitle(event.target.value)}
                placeholder="灵感标题"
                className="inspiration-edit-title"
              />
              <textarea
                value={editBody}
                onChange={(event) => setEditBody(event.target.value)}
                placeholder="灵感内容"
                className="inspiration-edit-body"
              />
              <input
                type="text"
                value={editTags}
                onChange={(event) => setEditTags(event.target.value)}
                placeholder="标签，用逗号或空格分隔"
                className="inspiration-edit-title"
              />
              <div className="status-chip-group" role="radiogroup" aria-label="灵感状态">
                {inspirationStatusOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    role="radio"
                    aria-checked={editStatus === option.value}
                    className={editStatus === option.value ? "status-chip active" : "status-chip"}
                    onClick={() => setEditStatus(option.value)}
                  >
                    <strong>{option.label}</strong>
                    <span>{option.hint}</span>
                  </button>
                ))}
              </div>
              <div className="tag-row editable-tag-row">
                {parseTagInput(editTags).map((tag) => <span key={tag}>#{tag}</span>)}
                {!parseTagInput(editTags).length && <span>保存后这里会显示标签</span>}
              </div>
              <div className="inspiration-edit-actions">
                <button onClick={() => void saveEdit()}>保存</button>
                <button onClick={cancelEdit} className="secondary">取消</button>
              </div>
            </>
          ) : (
            <>
              <h2>{selectedItem.title}</h2>
              <p className="inspiration-detail-body">{selectedItem.body || "还没有正文。"}</p>
              {selectedItem.source && (
                <div className="source-card">
                  <strong>来源摘录</strong>
                  <span>{selectedItem.source.bookTitle || "未知书籍"} · {selectedItem.source.locationLabel || `${selectedItem.source.progressPercent?.toFixed(1) ?? 0}%`}</span>
                  {selectedItem.source.excerpt && <blockquote>{selectedItem.source.excerpt}</blockquote>}
                </div>
              )}
              <div className="tag-row">
                {selectedItem.tags.map((tag) => (
                  <span key={tag}>{tag}</span>
                ))}
              </div>
              <div className="inspiration-actions">
                <button onClick={() => startEdit(selectedItem)} className="secondary">编辑正文</button>
                <button onClick={() => void handleDelete(selectedItem.id)} className="danger">删除</button>
              </div>
            </>
          )}
        </section>

        <section className="inspiration-ai-panel">
          <div className="section-heading">
            <h2>AI 打磨</h2>
            <span>{selectedItem.variants.length} 个候选</span>
          </div>
          <p className="empty-hint">AI 结果只保存为候选版本，除非你手动点“采用为正文”。</p>
          <div className="ai-action-row" aria-label="AI 灵感打磨">
            {aiActions.map(([action, label]) => (
              <button
                key={action}
                className="secondary"
                disabled={Boolean(aiBusy)}
                onClick={() => void runInspirationAI(selectedItem.id, action)}
              >
                {aiBusy === `${selectedItem.id}:${action}` ? "生成中…" : label}
              </button>
            ))}
          </div>
          <div className="ai-variant-list">
            {selectedItem.variants.length ? selectedItem.variants.map((variant) => (
              <article key={variant.id} className="ai-variant-card">
                <div className="ai-variant-header">
                  <strong>{aiActions.find(([key]) => key === variant.kind)?.[1] ?? variant.kind} · {variant.model || "AI"}</strong>
                  <button className="variant-delete-button" onClick={() => void removeVariant(selectedItem.id, variant.id)} aria-label={`删除 ${variant.kind} 候选`}>×</button>
                </div>
                <p>{variant.content}</p>
                <div className="ai-variant-actions">
                  <button className="secondary" onClick={() => void copyInspirationVariant(variant.content)}>
                    复制候选
                  </button>
                  <button onClick={() => void adoptInspirationVariant(selectedItem.id, variant.content)}>
                    采用为正文
                  </button>
                </div>
              </article>
            )) : <p className="empty-hint">暂无候选。点击上方按钮后，候选会保存在这里。</p>}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="screen-stack">
      <header className="mobile-header">
        <p className="mini-label">先收进箱子，再用 AI 打磨成可写素材</p>
        <h1>灵感</h1>
      </header>

      <section className="quick-note-card">
        <div className="section-heading">
          <h2>快速记录</h2>
          <span>按书籍 / 标签筛选</span>
        </div>
        <input value={draftTitle} onChange={(event) => setDraftTitle(event.target.value)} placeholder="标题（可选，不填则为未命名灵感）" />
        <textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="写下一句设定、冲突点、人物小动作……" />
        <input value={draftTags} onChange={(event) => setDraftTags(event.target.value)} placeholder="标签，如 人物，冲突，世界观" />
        <button disabled={!draft.trim() && !draftTitle.trim()} onClick={() => void addDraft()}>
          保存灵感
        </button>
      </section>

      <label className="search-pill">
        🔍
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索标题、标签、来源摘录" />
      </label>

      <section className="inspiration-list">
        {filtered.map((item) => (
          <article key={item.id} className="inspiration-card compact-inspiration-card" onClick={() => setSelectedId(item.id)}>
            {editingId === item.id ? (
              <>
                <input
                  type="text"
                  value={editTitle}
                  onChange={(event) => setEditTitle(event.target.value)}
                  placeholder="灵感标题"
                  className="inspiration-edit-title"
                />
                <textarea
                  value={editBody}
                  onChange={(event) => setEditBody(event.target.value)}
                  placeholder="灵感内容"
                  className="inspiration-edit-body"
                />
                <div className="inspiration-edit-actions">
                  <button onClick={saveEdit}>保存</button>
                  <button onClick={cancelEdit} className="secondary">取消</button>
                </div>
              </>
            ) : (
              <>
                <div className="compact-inspiration-main">
                  <p className="mini-label">{getInspirationStatusLabel(item.status)} · {item.type}</p>
                  <h2>{item.title}</h2>
                  <p className="compact-inspiration-preview">{item.body || item.source?.excerpt || "还没有正文。"}</p>
                  <div className="compact-inspiration-meta">
                    {item.source?.bookTitle && <span>来源：{item.source.bookTitle}</span>}
                    {item.variants.length > 0 && <span>AI 候选 {item.variants.length}</span>}
                    {item.tags.slice(0, 3).map((tag) => <span key={tag}>#{tag}</span>)}
                  </div>
                </div>
              </>
            )}
          </article>
        ))}
      </section>
    </div>
  );
}

function StatsPage({
  snapshot
}: {
  snapshot: MobileSnapshot;
}) {
  const [statsPeriod, setStatsPeriod] = useState<StatsPeriod>("month");
  const [statsAnchor, setStatsAnchor] = useState(() => new Date());
  const periodStats = useMemo(() => buildMobileStatsSummary(snapshot, statsPeriod, statsAnchor), [snapshot, statsPeriod, statsAnchor]);
  const timeline = useMemo(() => buildReadingTimeline(snapshot, statsPeriod, 14, statsAnchor), [snapshot, statsPeriod, statsAnchor]);
  const ranking = useMemo(() => buildBookRanking(snapshot, statsPeriod, 5, statsAnchor), [snapshot, statsPeriod, statsAnchor]);
  const insights = useMemo(() => buildNoteInsights(snapshot, statsPeriod, 5, statsAnchor), [snapshot, statsPeriod, statsAnchor]);
  const maxTimelineMs = useMemo(() => Math.max(1, ...timeline.map((item) => item.durationMs)), [timeline]);
  const prevAnchor = useMemo(() => shiftStatsPeriodAnchor(statsAnchor, statsPeriod, -1), [statsAnchor, statsPeriod]);
  const prevPeriodStats = useMemo(() => buildMobileStatsSummary(snapshot, statsPeriod, prevAnchor), [snapshot, statsPeriod, prevAnchor]);
  const currentPeriod = isCurrentStatsPeriod(statsPeriod, statsAnchor);
  const changeStatsPeriod = (period: StatsPeriod) => {
    setStatsPeriod(period);
    setStatsAnchor(new Date());
  };
  const shiftStatsPeriod = (direction: -1 | 1) => {
    if (statsPeriod === "total") return;
    setStatsAnchor((current) => shiftStatsPeriodAnchor(current, statsPeriod, direction));
  };
  const statItems = useMemo(() => [
    ["阅读时间", formatDuration(periodStats.totalReadingMs)],
    ["阅读天数", `${periodStats.readingDays} 天`],
    ["累计读过", `${snapshot.books.length} 本`],
    ["读完书籍", `${periodStats.completed} 本`],
    ["在读书籍", `${periodStats.readingBooks} 本`],
    ["记录灵感", `${periodStats.inspirationCount} 条`],
    ["阅读字数", `${periodStats.words} 字`],
    ["阅读速度", `${periodStats.speed} 字/分钟`]
  ], [periodStats, snapshot.books.length]);
  return (
    <div className="screen-stack stats-screen">
      <header className="mobile-header row-header stats-header">
        <div>
          <p className="mini-label">阅读沉淀</p>
          <h1>阅读统计</h1>
        </div>
        <button className="round-action" aria-label="回到当前周期" onClick={() => setStatsAnchor(new Date())} disabled={currentPeriod}>
          <RefreshCw size={20} />
        </button>
      </header>

      <div className="range-tabs stats-period-tabs">
        {(Object.keys(statsPeriodLabels) as StatsPeriod[]).map((item) => (
          <button key={item} className={statsPeriod === item ? "active" : ""} onClick={() => changeStatsPeriod(item)}>
            {statsPeriodLabels[item]}
          </button>
        ))}
      </div>

      <div className="stats-period-title">
        <button className="round-action" aria-label="上一周期" disabled={statsPeriod === "total"} onClick={() => shiftStatsPeriod(-1)}>‹</button>
        <strong>{getStatsPeriodTitle(statsPeriod, statsAnchor)}</strong>
        <button className="round-action" aria-label="下一周期" disabled={statsPeriod === "total" || currentPeriod} onClick={() => shiftStatsPeriod(1)}>›</button>
      </div>

      <section className="stats-card stats-overview-card">
        <div className="stats-grid">
          {statItems.map(([label, value]) => (
            <article key={label}>
              <strong>{value}</strong>
              <span>{label}</span>
            </article>
          ))}
        </div>
      </section>

      <section className="stats-lite-card period-comparison-row">
        {prevPeriodStats.totalReadingMs > 0 && (
          <article key="comparison" className="comparison-item">
            <span className="comparison-label">较上一周期</span>
            {(() => {
              const metrics: Array<[string, number]> = [
                ["时长", periodStats.totalReadingMs - prevPeriodStats.totalReadingMs],
                ["天数", periodStats.readingDays - prevPeriodStats.readingDays],
                ["完读", periodStats.completed - prevPeriodStats.completed],
                ["在读", periodStats.readingBooks - prevPeriodStats.readingBooks],
                ["灵感", periodStats.inspirationCount - prevPeriodStats.inspirationCount],
                ["字数", periodStats.words - prevPeriodStats.words]
              ];
              return metrics.map(([label, diff]) => (
                <span key={label} className={`comparison-metric trend-${diff > 0 ? "up" : diff < 0 ? "down" : "neutral"}`}>
                  <span className="comparison-label-sm">{label}</span>
                  <span>{diff > 0 ? "+" : ""}{diff}</span>
                </span>
              ));
            })()}
          </article>
        )}
      </section>

      <section className="trend-card reading-heat-strip stats-lite-card">
        <div className="section-heading">
          <h2>阅读时间趋势</h2>
          <span>{periodStats.sessionCount ? `${periodStats.sessionCount} 次` : "暂无数据"}</span>
        </div>
        {timeline.length ? (
          <div className="heat-bars" aria-label="阅读时间热度条">
            {timeline
              .slice()
              .reverse()
              .map((item) => (
                <div key={item.dateKey} className="heat-bar-wrap">
                  <span className="heat-bar" style={{ height: `${Math.max(14, (item.durationMs / maxTimelineMs) * 86)}%` }} />
                  <small>{item.label}</small>
                </div>
              ))}
          </div>
        ) : (
          <p className="stats-empty">暂无数据</p>
        )}
      </section>

      <section className="stats-card reading-timeline stats-lite-card">
        <div className="section-heading">
          <h2>阅读时间线</h2>
          <span>最近记录</span>
        </div>
        {timeline.length ? (
          <div className="timeline-list">
            {timeline.map((item) => (
              <article key={item.dateKey} className="reading-timeline-item">
                <div className="timeline-dot" />
                <div>
                  <strong>{item.label}</strong>
                  <p>{item.bookTitles.slice(0, 2).join("、") || "未命名书籍"}</p>
                  <small>
                    {formatDuration(item.durationMs)} · {item.sessionCount} 次阅读{item.noteCount ? ` · ${item.noteCount} 条笔记` : ""}
                  </small>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="stats-empty">暂无数据</p>
        )}
      </section>

      <section className="stats-card book-ranking-list stats-lite-card">
        <div className="section-heading">
          <h2>阅读时长排行榜</h2>
          <span>按阅读时长</span>
        </div>
        {ranking.length ? (
          <div className="ranking-list">
            {ranking.map((item, index) => (
              <article key={item.bookId} className="book-ranking-item">
                <span className="ranking-index">{index + 1}</span>
                <div>
                  <strong>{item.title}</strong>
                  <p>{item.author || item.format} · {formatDuration(item.durationMs)} · {item.progressPercent.toFixed(2)}%</p>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="stats-empty">暂无数据</p>
        )}
      </section>

      <section className="stats-card note-insight-list stats-lite-card">
        <div className="section-heading">
          <h2>灵感与笔记</h2>
          <span>阅读沉淀</span>
        </div>
        {insights.length ? (
          <div className="insight-list">
            {insights.map((item) => (
              <article key={`${item.kind}-${item.id}`} className="note-insight-item">
                <span>{item.kind}</span>
                <div>
                  <strong>{item.title}</strong>
                  <p>{item.excerpt}</p>
                  {item.bookTitle && <small>来自《{item.bookTitle}》</small>}
                </div>
              </article>
            ))}
          </div>
        ) : (
          <p className="stats-empty">暂无数据</p>
        )}
      </section>
    </div>
  );
}

function ProfilePage({
  snapshot,
  pairingText,
  paired,
  onPairingTextChange,
  onConnectLan,
  onScanQr,
  onSyncDesktop,
  pendingDownloadCount,
  syncLogs,
  onSnapshotChange,
  onMessage,
  onConfirm,
  activePage,
  onSetActivePage,
  appTheme,
  onAppThemeChange,
  onOpenBook
}: {
  snapshot: MobileSnapshot;
  pairingText: string;
  paired: boolean;
  onPairingTextChange: (value: string) => void;
  onConnectLan: () => void;
  onScanQr: () => void;
  onSyncDesktop: () => void;
  pendingDownloadCount: number;
  syncLogs: string[];
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (value: string) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void } | null) => void;
  activePage: ProfileSubPage | undefined;
  onSetActivePage: (page: ProfileSubPage | undefined) => void;
  appTheme: MobileAppTheme;
  onAppThemeChange: (theme: MobileAppTheme) => void;
  onOpenBook: (book: MobileBook) => void;
}) {
  const [webdav, setWebdav] = useState({ endpoint: "", username: "", password: "" });
  const [webdavError, setWebdavError] = useState("");
  const [webdavTouched, setWebdavTouched] = useState(false);
  const [hasWebDavPassword, setHasWebDavPassword] = useState(false);
  const [newTagName, setNewTagName] = useState("");
  const [newTagType, setNewTagType] = useState<"book" | "inspiration" | "note">("book");
  const [newCategoryName, setNewCategoryName] = useState("");
  const [newShelfName, setNewShelfName] = useState("");
  const [editingManagerItem, setEditingManagerItem] = useState<{ kind: "tag" | "category" | "shelf"; id: string; name: string } | null>(null);
  const [editingManagerName, setEditingManagerName] = useState("");
  const [aiSettings, setAiSettings] = useState(() => loadMobileAISettings());
  const [aiKeyDraft, setAiKeyDraft] = useState("");
  const [profileMoreOpen, setProfileMoreOpen] = useState(false);
  const [updateInfo, setUpdateInfo] = useState<MobileUpdateInfo>();
  const [updateBusy, setUpdateBusy] = useState(false);
  const importSnapshotInputRef = useRef<HTMLInputElement>(null);

  const validateWebDavEndpoint = (url: string): string => {
    if (!url) return "";
    if (!/^https?:\/\//i.test(url)) return "地址必须以 http:// 或 https:// 开头";
    try {
      new URL(url);
      return "";
    } catch {
      return "地址格式不正确";
    }
  };

  const handleEndpointChange = (value: string) => {
    setWebdavTouched(true);
    setWebdav((current) => ({ ...current, endpoint: value }));
    setWebdavError(validateWebDavEndpoint(value));
  };

  const savedWebdavAccount = snapshot.syncAccounts.find((item) => item.provider === "webdav");

  useEffect(() => {
    if (!savedWebdavAccount || webdavTouched) return;
    const endpoint = savedWebdavAccount.endpoint ?? "";
    const username = savedWebdavAccount.username ?? "";
    setWebdav({ endpoint, username, password: "" });
    setWebdavError(validateWebDavEndpoint(endpoint));
  }, [savedWebdavAccount?.endpoint, savedWebdavAccount?.username, webdavTouched]);

  useEffect(() => {
    let active = true;
    void loadWebDavPasswordSecret()
      .then((password) => {
        if (active) setHasWebDavPassword(Boolean(password));
      })
      .catch(() => {
        if (active) setHasWebDavPassword(false);
      });
    return () => {
      active = false;
    };
  }, [activePage]);

  const webdavAccount: SyncAccount = savedWebdavAccount ?? {
    id: "webdav-preview",
    provider: "webdav",
    name: "WebDAV",
    endpoint: webdav.endpoint,
    username: webdav.username,
    enabled: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    revision: 1,
    deviceId: getMobileDeviceId()
  };

  const getWebDavCredentials = async () => {
    const savedPassword = await loadWebDavPasswordSecret();
    return {
      endpoint: webdav.endpoint,
      username: webdav.username,
      password: webdav.password.trim() || savedPassword || ""
    };
  };

  const testWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      const result = await testWebDavConnection(credentials);
      const next = await saveSyncAccount(snapshot, {
        provider: "webdav",
        name: "WebDAV",
        endpoint: webdav.endpoint,
        username: webdav.username,
        enabled: result.ok
      });
      if (result.ok && webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      onSnapshotChange(next);
      onMessage(result.message);
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const uploadWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      await uploadWebDavSnapshot(credentials, webdavAccount, snapshot);
      if (webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      onMessage("WebDAV 上传完成。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const downloadWebDav = async () => {
    try {
      const credentials = await getWebDavCredentials();
      const next = await downloadWebDavSnapshot(credentials, snapshot);
      if (webdav.password.trim()) {
        await saveWebDavPasswordSecret(webdav.password);
        setWebdav((current) => ({ ...current, password: "" }));
        setHasWebDavPassword(true);
      }
      await saveMobileSnapshot(next);
      onSnapshotChange(next);
      onMessage("WebDAV 下载完成，已合并到手机端。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const clearWebDavCredential = async () => {
    try {
      await clearWebDavPasswordSecret();
      setHasWebDavPassword(false);
      setWebdav((current) => ({ ...current, password: "" }));
      onMessage("已清除本机 WebDAV 密码 / token。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const saveSnapshotAndNotify = async (next: MobileSnapshot, message: string) => {
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(message);
  };

  const addTag = async () => {
    const name = newTagName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      tags: [
        {
          id: `mobile-tag-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          type: newTagType,
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.tags.filter((item) => !(item.name === name && item.type === newTagType))
      ],
      updatedAt: timestamp
    };
    setNewTagName("");
    await saveSnapshotAndNotify(next, `已添加标签「${name}」。`);
  };

  const addCategory = async () => {
    const name = newCategoryName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      categories: [
        {
          id: `mobile-category-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          sortOrder: snapshot.categories.length + 1,
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.categories.filter((item) => item.name !== name)
      ],
      updatedAt: timestamp
    };
    setNewCategoryName("");
    await saveSnapshotAndNotify(next, `已添加分类「${name}」。`);
  };

  const addShelf = async () => {
    const name = newShelfName.trim();
    if (!name) return;
    const timestamp = new Date().toISOString();
    const next = {
      ...snapshot,
      shelves: [
        {
          id: `mobile-shelf-${Date.now().toString(36)}-${Math.random().toString(16).slice(2, 8)}`,
          name,
          bookIds: [],
          createdAt: timestamp,
          updatedAt: timestamp,
          revision: 1,
          deviceId: getMobileDeviceId()
        },
        ...snapshot.shelves.filter((item) => item.name !== name)
      ],
      updatedAt: timestamp
    };
    setNewShelfName("");
    await saveSnapshotAndNotify(next, `已创建书单「${name}」。`);
  };

  const removeRecord = async (kind: "tag" | "category" | "shelf", id: string, name: string) => {
    onConfirm({
      title: `删除${kind === "tag" ? "标签" : kind === "category" ? "分类" : "书单"}`,
      message: `确定删除「${name}」吗？这不会删除书籍、灵感或笔记正文。`,
      onConfirm: async () => {
        const timestamp = new Date().toISOString();
        const targetTag = kind === "tag" ? snapshot.tags.find((item) => item.id === id) : undefined;
        const next = {
          ...snapshot,
          tags: kind === "tag" ? snapshot.tags.filter((item) => item.id !== id) : snapshot.tags,
          categories: kind === "category" ? snapshot.categories.filter((item) => item.id !== id) : snapshot.categories,
          shelves: kind === "shelf" ? snapshot.shelves.filter((item) => item.id !== id) : snapshot.shelves,
          books: kind === "tag" && targetTag?.type === "book"
            ? snapshot.books.map((book) => book.tagNames?.includes(name)
              ? {
                  ...book,
                  tagNames: book.tagNames.filter((tag) => tag !== name),
                  updatedAt: timestamp,
                  revision: (book.revision ?? 0) + 1
                }
              : book)
            : kind === "category"
              ? snapshot.books.map((book) => book.categoryIds?.includes(id)
                ? {
                    ...book,
                    categoryIds: book.categoryIds.filter((categoryId) => categoryId !== id),
                    updatedAt: timestamp,
                    revision: (book.revision ?? 0) + 1
                  }
                : book)
            : snapshot.books,
          inspirations: kind === "tag" && targetTag?.type === "inspiration"
            ? snapshot.inspirations.map((item) => item.tags.includes(name)
              ? {
                  ...item,
                  tags: item.tags.filter((tag) => tag !== name),
                  updatedAt: timestamp,
                  revision: item.revision + 1
                }
              : item)
            : snapshot.inspirations,
          updatedAt: timestamp
        };
        await saveSnapshotAndNotify(next, "已删除。");
        onConfirm(null);
      }
    });
  };

  const startRenameRecord = (kind: "tag" | "category" | "shelf", id: string, name: string) => {
    setEditingManagerItem({ kind, id, name });
    setEditingManagerName(name);
  };

  const cancelRenameRecord = () => {
    setEditingManagerItem(null);
    setEditingManagerName("");
  };

  const renameRecord = async () => {
    if (!editingManagerItem) return;
    const nextName = editingManagerName.trim();
    if (!nextName || nextName === editingManagerItem.name) {
      cancelRenameRecord();
      return;
    }
    const timestamp = new Date().toISOString();
    const { kind, id, name: oldName } = editingManagerItem;
    const targetTag = kind === "tag" ? snapshot.tags.find((item) => item.id === id) : undefined;
    const next: MobileSnapshot = {
      ...snapshot,
      tags: kind === "tag"
        ? snapshot.tags.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.tags,
      categories: kind === "category"
        ? snapshot.categories.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.categories,
      shelves: kind === "shelf"
        ? snapshot.shelves.map((item) => item.id === id ? { ...item, name: nextName, updatedAt: timestamp, revision: item.revision + 1 } : item)
        : snapshot.shelves,
      books: kind === "tag" && targetTag?.type === "book"
        ? snapshot.books.map((book) => book.tagNames?.includes(oldName)
          ? {
              ...book,
              tagNames: book.tagNames.map((tag) => tag === oldName ? nextName : tag),
              updatedAt: timestamp,
              revision: (book.revision ?? 0) + 1
            }
          : book)
        : snapshot.books,
      inspirations: kind === "tag" && targetTag?.type === "inspiration"
        ? snapshot.inspirations.map((item) => item.tags.includes(oldName)
          ? {
              ...item,
              tags: item.tags.map((tag) => tag === oldName ? nextName : tag),
              updatedAt: timestamp,
              revision: item.revision + 1
            }
          : item)
        : snapshot.inspirations,
      updatedAt: timestamp
    };
    cancelRenameRecord();
    await saveSnapshotAndNotify(next, `已重命名为「${nextName}」。`);
  };

  const removeBookFromShelf = async (shelfId: string, bookId: string) => {
    const timestamp = new Date().toISOString();
    const shelf = snapshot.shelves.find((item) => item.id === shelfId);
    if (!shelf) return;
    const book = snapshot.books.find((item) => item.id === bookId);
    const next: MobileSnapshot = {
      ...snapshot,
      shelves: snapshot.shelves.map((item) => item.id === shelfId
        ? {
            ...item,
            bookIds: item.bookIds.filter((id) => id !== bookId),
            updatedAt: timestamp,
            revision: item.revision + 1
          }
        : item),
      updatedAt: timestamp
    };
    await saveSnapshotAndNotify(next, `已从书单「${shelf.name}」移除${book ? `《${book.title}》` : "这本书"}。`);
  };

  const clearBookProgress = (book: MobileBook) => {
    onConfirm({
      title: "清除阅读进度",
      message: `确定清除《${book.title}》的阅读进度吗？阅读记录会保留。`,
      onConfirm: async () => {
        const timestamp = new Date().toISOString();
        const next: MobileSnapshot = {
          ...snapshot,
          progress: snapshot.progress.filter((item) => item.bookId !== book.id),
          updatedAt: timestamp
        };
        await saveSnapshotAndNotify(next, "已清除这本书的阅读进度。");
        onConfirm(null);
      }
    });
  };

  const removeNote = async (id: string) => {
    onConfirm({
      title: "删除笔记",
      message: "确定删除这条笔记/书签吗？删除后会从当前列表隐藏。",
      onConfirm: async () => {
        const next = await deleteMobileNote(snapshot, id);
        onSnapshotChange(next);
        onMessage("已删除笔记。");
        onConfirm(null);
      }
    });
  };

  const exportSnapshot = async () => {
    const content = await exportMobileSnapshot(snapshot);
    downloadTextFile(`creation-reading-assistant-mobile-${new Date().toISOString().slice(0, 10)}.json`, content);
    onMessage("已导出手机端数据快照。");
  };

  const importSnapshotFile = async (file?: File) => {
    if (!file) return;
    const text = await file.text();
    const next = await importMobileSnapshot(snapshot, text);
    onSnapshotChange(next);
    onMessage("已导入并合并数据快照；同 ID 数据会自动去重。");
  };

  const saveAiSettings = async () => {
    const next = await saveMobileAISettings({ ...aiSettings, apiKey: aiKeyDraft });
    setAiSettings(next);
    setAiKeyDraft("");
    onMessage("已保存手机端 AI 连接信息。API Key 仅保存在本机密钥库，不跨设备同步，灵感中心可直接生成候选版本。");
  };

  const clearAiKey = async () => {
    const next = await clearMobileAIApiKey();
    setAiSettings(next);
    setAiKeyDraft("");
    onMessage("已清除手机端 AI API Key。");
  };

  const testMobileAI = async () => {
    try {
      await saveAiSettings();
      const result = await runMobileAIAction({
        action: "polish",
        title: "测试连接",
        content: "一个角色在雨夜想起旧约定。"
      });
      onMessage(result.content ? "AI 连接可用，灵感中心可以生成候选版本。" : "AI 返回为空。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const checkUpdate = async () => {
    setUpdateBusy(true);
    try {
      const info = await checkForMobileUpdate();
      setUpdateInfo(info);
      onMessage(info.hasUpdate ? `发现新版本 v${info.latestVersion}。` : "当前已经是最新版本。");
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setUpdateInfo({
        currentVersion: MOBILE_APP_VERSION,
        latestVersion: MOBILE_APP_VERSION,
        hasUpdate: true,
        releaseUrl: "https://github.com/luoshuizhiwei/creation-reading-assistant/releases",
        notes: "更新源暂时不可访问。可以打开发布页手动查看最新安装包。"
      });
      onMessage(detail);
    } finally {
      setUpdateBusy(false);
    }
  };

  const openUpdate = () => {
    if (!updateInfo) return;
    openMobileUpdateUrl(updateInfo);
    onMessage(updateInfo.apkUrl ? "已打开新版 APK 下载。下载完成后按系统提示安装。" : "已打开 GitHub Release 页面。");
  };

  const totalBookBytes = snapshot.books.reduce((sum, book) => sum + (book.size ?? 0), 0);
  const localBookCount = snapshot.books.filter(isBookDownloaded).length;
  const tagTypeLabels = {
    book: "书籍",
    inspiration: "灵感",
    note: "笔记"
  } as const;
  const openProfileShortcut = (page: ProfileSubPage) => {
    setProfileMoreOpen(false);
    onSetActivePage(page);
  };

  const menuGroups: Array<{
    title: string;
    items: Array<{ label: string; desc?: string; icon: ReactNode; action?: () => void }>;
  }> = [
    {
      title: "数据管理",
      items: [
        { label: "同步状态", desc: paired ? "已连接电脑" : "从未同步", icon: <RefreshCw size={20} />, action: () => onSetActivePage("sync") },
        { label: "WebDAV 设置", desc: webdavAccount.endpoint ? "已配置账号" : "未配置", icon: <Cloud size={20} />, action: () => onSetActivePage("webdav") },
        { label: "标签管理", desc: `${snapshot.tags.length} 个`, icon: <Tags size={20} />, action: () => onSetActivePage("tags") },
        { label: "分类管理", desc: `${snapshot.categories.length} 个`, icon: <FolderTree size={20} />, action: () => onSetActivePage("categories") },
        { label: "书单管理", desc: `${snapshot.shelves.length} 个`, icon: <BookOpen size={20} />, action: () => onSetActivePage("shelves") },
        { label: "我的阅读", desc: `${snapshot.progress.length} 本有进度`, icon: <Book size={20} />, action: () => onSetActivePage("reading") },
        { label: "我的书评 / 笔记", desc: `${snapshot.notes.filter((item) => !item.deletedAt).length} 条`, icon: <MessageSquare size={20} />, action: () => onSetActivePage("notes") }
      ]
    },
    {
      title: "工具",
      items: [
        { label: "AI 助手", desc: aiSettings.baseUrl ? "已填写 Base URL" : "未配置", icon: <Sparkles size={20} />, action: () => onSetActivePage("ai") }
      ]
    },
    {
      title: "外观",
      items: [
        { label: "应用外观", desc: appTheme === "system" ? "跟随系统" : appTheme === "dark" ? "深色" : "浅色", icon: <Moon size={20} />, action: () => onSetActivePage("appearance") },
        { label: "主题配色", desc: "纸墨铜色", icon: <Palette size={20} />, action: () => onSetActivePage("appearance") }
      ]
    },
    {
      title: "系统资源",
      items: [
        { label: "存储管理", desc: `${localBookCount}/${snapshot.books.length} 本已落地`, icon: <Database size={20} />, action: () => onSetActivePage("storage") },
        { label: "隐私安全", desc: "本地优先", icon: <Shield size={20} />, action: () => onSetActivePage("privacy") },
        { label: "关于", desc: `版本 ${MOBILE_APP_VERSION}`, icon: <Info size={20} />, action: () => onSetActivePage("about") }
      ]
    }
  ];

  const renderSubpageHeader = (eyebrow: string, title: string) => (
    <header className="mobile-header row-header subpage-header">
      <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
        ← 返回
      </button>
      <div>
        <p className="mini-label">{eyebrow}</p>
        <h1>{title}</h1>
      </div>
    </header>
  );

  if (activePage === "tags") {
    const usageByType = new Map<string, { name: string; type: "book" | "inspiration" | "note"; count: number; managedId?: string }>();
    const ensureUsage = (name: string, type: "book" | "inspiration" | "note") => {
      const key = `${type}:${name}`;
      const current = usageByType.get(key) ?? { name, type, count: 0 };
      usageByType.set(key, current);
      return current;
    };
    snapshot.books.forEach((book) => book.tagNames?.forEach((tag) => {
      ensureUsage(tag, "book").count += 1;
    }));
    snapshot.inspirations.forEach((item) => item.tags.forEach((tag) => {
      ensureUsage(tag, "inspiration").count += 1;
    }));
    snapshot.tags.forEach((tag) => {
      ensureUsage(tag.name, tag.type).managedId = tag.id;
    });
    const tags = [...usageByType.values()].sort((left, right) => right.count - left.count || left.name.localeCompare(right.name, "zh-Hans-CN"));
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("数据管理", "标签管理")}
        <section className="subpage-card">
          <div className="inline-form tag-inline-form">
            <input value={newTagName} onChange={(event) => setNewTagName(event.target.value)} placeholder="新标签名称" />
            <select value={newTagType} onChange={(event) => setNewTagType(event.target.value as "book" | "inspiration" | "note")} aria-label="标签类型">
              <option value="book">书籍</option>
              <option value="inspiration">灵感</option>
              <option value="note">笔记</option>
            </select>
            <button onClick={() => void addTag()}>添加</button>
          </div>
          <div className="management-list">
            {tags.length ? tags.map((tag) => {
              const managed = tag.managedId ? snapshot.tags.find((item) => item.id === tag.managedId) : undefined;
              return (
                <article key={`${tag.type}:${tag.name}`} className="management-row">
                  <span className="profile-menu-icon"><Tags size={18} /></span>
                  <div>
                    <strong>{tag.name}</strong>
                    <small>{tagTypeLabels[tag.type]}标签 · {tag.count ? `${tag.count} 处使用` : "暂未使用"}</small>
                    {editingManagerItem?.kind === "tag" && editingManagerItem.id === managed?.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新标签名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                  </div>
                  {managed && (
                    <>
                      <button className="secondary mini-row-action" onClick={() => startRenameRecord("tag", managed.id, managed.name)}>重命名</button>
                      <button className="icon-danger" onClick={() => void removeRecord("tag", managed.id, managed.name)} aria-label={`删除标签 ${tag.name}`}><Trash2 size={17} /></button>
                    </>
                  )}
                </article>
              );
            }) : <p className="empty-hint">还没有标签。记录灵感或手动添加后，会在这里统一管理。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "categories") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("数据管理", "分类管理")}
        <section className="subpage-card">
          <div className="inline-form">
            <input value={newCategoryName} onChange={(event) => setNewCategoryName(event.target.value)} placeholder="新分类名称" />
            <button onClick={() => void addCategory()}>添加</button>
          </div>
          <div className="management-list">
            {snapshot.categories.length ? snapshot.categories.map((item) => {
              const categoryBooks = snapshot.books.filter((book) => book.categoryIds?.includes(item.id));
              return (
                <article key={item.id} className="management-row management-row-expanded">
                  <span className="profile-menu-icon"><FolderTree size={18} /></span>
                  <div>
                    <strong>{item.name}</strong>
                    <small>{categoryBooks.length} 本书 · {formatCompactDateTime(item.updatedAt)}</small>
                    {editingManagerItem?.kind === "category" && editingManagerItem.id === item.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新分类名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                    {categoryBooks.length > 0 && (
                      <div className="linked-book-list">
                        {categoryBooks.slice(0, 4).map((book) => (
                          <button key={book.id} onClick={() => onOpenBook(book)}>{book.title}</button>
                        ))}
                      </div>
                    )}
                  </div>
                  <button className="secondary mini-row-action" onClick={() => startRenameRecord("category", item.id, item.name)}>重命名</button>
                  <button className="icon-danger" onClick={() => void removeRecord("category", item.id, item.name)} aria-label={`删除分类 ${item.name}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有分类。创建后可在书籍详情里归类，并在书架顶部按分类筛选。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "shelves") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("数据管理", "书单管理")}
        <section className="subpage-card">
          <div className="inline-form">
            <input value={newShelfName} onChange={(event) => setNewShelfName(event.target.value)} placeholder="新书单名称" />
            <button onClick={() => void addShelf()}>创建</button>
          </div>
          <div className="management-list">
            {snapshot.shelves.length ? snapshot.shelves.map((item) => {
              const shelfBooks = item.bookIds.map((bookId) => snapshot.books.find((book) => book.id === bookId)).filter((book): book is MobileBook => Boolean(book));
              return (
                <article key={item.id} className="management-row management-row-expanded">
                  <span className="profile-menu-icon"><BookOpen size={18} /></span>
                  <div>
                    <strong>{item.name}</strong>
                    <small>{shelfBooks.length} 本书 · {formatCompactDateTime(item.updatedAt)}</small>
                    {editingManagerItem?.kind === "shelf" && editingManagerItem.id === item.id && (
                      <div className="manager-rename-row">
                        <input value={editingManagerName} onChange={(event) => setEditingManagerName(event.target.value)} aria-label="新书单名称" />
                        <button onClick={() => void renameRecord()}>保存</button>
                        <button className="secondary" onClick={cancelRenameRecord}>取消</button>
                      </div>
                    )}
                    {shelfBooks.length > 0 && (
                      <div className="linked-book-list">
                        {shelfBooks.slice(0, 4).map((book) => (
                          <span key={book.id}>
                            <button onClick={() => onOpenBook(book)}>{book.title}</button>
                            <button className="text-danger" onClick={() => void removeBookFromShelf(item.id, book.id)}>移除</button>
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                  <button className="secondary mini-row-action" onClick={() => startRenameRecord("shelf", item.id, item.name)}>重命名</button>
                  <button className="icon-danger" onClick={() => void removeRecord("shelf", item.id, item.name)} aria-label={`删除书单 ${item.name}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有书单。创建后可在书籍详情里收书，并在书架顶部按书单筛选。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "reading") {
    const progressItems = snapshot.progress
      .map((progress) => ({ progress, book: snapshot.books.find((book) => book.id === progress.bookId) }))
      .filter((item): item is { progress: typeof snapshot.progress[number]; book: MobileBook } => Boolean(item.book))
      .sort((left, right) => right.progress.lastReadAt.localeCompare(left.progress.lastReadAt));
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("阅读档案", "我的阅读")}
        <section className="profile-metric-strip">
          <article><strong>{snapshot.books.length}</strong><span>书架书籍</span></article>
          <article><strong>{progressItems.length}</strong><span>有进度</span></article>
          <article><strong>{formatDuration(snapshot.progress.reduce((sum, item) => sum + item.totalReadingTimeMs, 0))}</strong><span>累计时长</span></article>
        </section>
        <section className="subpage-card">
          <div className="management-list">
            {progressItems.length ? progressItems.map(({ progress, book }) => (
              <article key={book.id} className="management-row reading-row clickable-row" onClick={() => onOpenBook(book)}>
                <div className="book-cover mini-cover">{book.title.slice(0, 2)}</div>
                <div>
                  <strong>{book.title}</strong>
                  <small>{progress.progressPercent.toFixed(2)}% · {formatDuration(progress.totalReadingTimeMs)} · {formatCompactDateTime(progress.lastReadAt)}</small>
                  <div className="book-progress-line"><span style={{ width: `${Math.min(100, Math.max(0, progress.progressPercent))}%` }} /></div>
                </div>
                <button
                  className="secondary mini-row-action"
                  onClick={(event) => {
                    event.stopPropagation();
                    clearBookProgress(book);
                  }}
                >
                  清除进度
                </button>
              </article>
            )) : <p className="empty-hint">还没有阅读记录。打开一本书读一会儿，这里会自动生成档案。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "notes") {
    const notes = snapshot.notes.filter((item) => !item.deletedAt).sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("阅读沉淀", "我的书评 / 笔记")}
        <section className="subpage-card">
          <div className="management-list">
            {notes.length ? notes.map((note) => {
              const book = note.bookId ? snapshot.books.find((item) => item.id === note.bookId) : undefined;
              return (
                <article key={note.id} className="management-row note-row">
                  <span className="profile-menu-icon"><MessageSquare size={18} /></span>
                  <div>
                    <strong>{note.title}</strong>
                    <small>{note.kind === "bookmark" ? "书签" : "笔记"} · {book?.title ?? "无来源书籍"} · {(note.progressPercent ?? 0).toFixed(1)}%</small>
                    <p>{note.excerpt || note.body || note.chapterTitle || "暂无正文"}</p>
                  </div>
                  {book && <button className="secondary mini-row-action" onClick={() => onOpenBook(book)}>打开书</button>}
                  <button className="icon-danger" onClick={() => void removeNote(note.id)} aria-label={`删除笔记 ${note.title}`}><Trash2 size={17} /></button>
                </article>
              );
            }) : <p className="empty-hint">还没有笔记。阅读页选中文字后，可以保存为笔记或书签。</p>}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "ai") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("工具", "AI 助手")}
        <section className="subpage-card">
          <p className="subtle">手机端 AI Key 仅保存在本机密钥库。AI Key 不跨设备同步；这里配置 OpenAI-compatible 接口后，灵感中心可以直接生成候选版本，结果只进入 AI 候选，不覆盖原文。</p>
          <input value={aiSettings.baseUrl} onChange={(event) => setAiSettings((current) => ({ ...current, baseUrl: event.target.value }))} placeholder="https://dashscope.aliyuncs.com/compatible-mode/v1" />
          <input value={aiSettings.model} onChange={(event) => setAiSettings((current) => ({ ...current, model: event.target.value }))} placeholder="qwen-plus" />
          <label className="range-setting-row">
            <span>创造性 {aiSettings.temperature.toFixed(1)}</span>
            <input
              type="range"
              min="0"
              max="1.5"
              step="0.1"
              value={aiSettings.temperature}
              onChange={(event) => setAiSettings((current) => ({ ...current, temperature: Number(event.target.value) }))}
            />
          </label>
          <input
            type="password"
            value={aiKeyDraft}
            onChange={(event) => setAiKeyDraft(event.target.value)}
            placeholder={aiSettings.hasApiKey ? "已保存 API Key；留空则不修改" : "粘贴手机端 API Key"}
          />
          <div className="setting-check-row">
            <CheckCircle2 size={18} />
            <span>{aiSettings.hasApiKey ? "已保存 API Key；不会同步、导出或上传到 WebDAV。" : "还没有保存手机端 API Key。"}</span>
          </div>
          <div className="button-row settings-actions">
            <button onClick={() => void saveAiSettings()}>保存设置</button>
            <button onClick={() => void testMobileAI()}><Wifi size={17} />测试</button>
            <button className="secondary-button" onClick={() => void clearAiKey()}>清除 Key</button>
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "appearance") {
    const themes: Array<[MobileAppTheme, string, string]> = [
      ["system", "跟随系统", "随手机深浅色变化"],
      ["light", "浅色", "纸张感更强，适合白天"],
      ["dark", "深色", "夜间浏览设置页更安静"]
    ];
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("外观", "应用外观")}
        <section className="subpage-card">
          <p className="subtle">应用外观影响首页、书架、灵感、统计和设置；阅读页正文背景仍在阅读器设置里单独控制。</p>
          <div className="option-list">
            {themes.map(([value, title, desc]) => (
              <button key={value} className={appTheme === value ? "option-row active" : "option-row"} onClick={() => onAppThemeChange(value)}>
                <span><Moon size={18} /></span>
                <div><strong>{title}</strong><small>{desc}</small></div>
                {appTheme === value && <CheckCircle2 size={18} />}
              </button>
            ))}
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "storage") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("系统资源", "存储管理")}
        <section className="profile-metric-strip">
          <article><strong>{snapshot.books.length}</strong><span>书籍</span></article>
          <article><strong>{localBookCount}</strong><span>已下载正文</span></article>
          <article><strong>{formatBytes(totalBookBytes)}</strong><span>索引大小</span></article>
        </section>
        <section className="subpage-card">
          <p className="subtle">导出会保存灵感、书库元数据、进度、阅读记录、笔记、标签、分类和同步账号信息；不会导出 AI Key。</p>
          <input ref={importSnapshotInputRef} hidden type="file" accept="application/json,.json" onChange={(event) => void importSnapshotFile(event.currentTarget.files?.[0])} />
          <div className="button-row settings-actions">
            <button onClick={() => void exportSnapshot()}><FileDown size={17} />导出数据</button>
            <button onClick={() => importSnapshotInputRef.current?.click()}><FileUp size={17} />导入数据</button>
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "privacy") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("系统资源", "隐私安全")}
        <section className="subpage-card privacy-list">
          <article><Shield size={19} /><div><strong>本地优先</strong><p>没有账号服务器。书籍、灵感、进度和笔记默认保存在手机本地。</p></div></article>
          <article><Wifi size={19} /><div><strong>同步可控</strong><p>局域网同步需要你手动连接电脑；WebDAV 需要你主动配置地址。</p></div></article>
          <article><Sparkles size={19} /><div><strong>密钥隔离</strong><p>AI Key 和 WebDAV 密码 / token 仅保存在本机密钥库，不参与电脑同步、WebDAV 同步或数据导出。</p></div></article>
        </section>
      </div>
    );
  }

  if (activePage === "about") {
    return (
      <div className="screen-stack profile-subpage">
        {renderSubpageHeader("其他", "关于")}
        <section className="subpage-card about-card">
          <div className="avatar">读</div>
          <h2>创作阅读助手</h2>
          <p>Android 端 · 本地优先 · 灵感中心特色版</p>
          <div className="about-list">
            <span>版本：{MOBILE_APP_VERSION}</span>
            <span>支持：TXT / Markdown / EPUB</span>
            <span>同步：电脑局域网 / WebDAV</span>
          </div>
        </section>
        <section className="subpage-card update-card">
          <div>
            <p className="mini-label">应用更新</p>
            <h2>{updateInfo?.hasUpdate ? `发现 v${updateInfo.latestVersion}` : "检查新版本"}</h2>
            <p className="subtle">
              {updateInfo
                ? updateInfo.hasUpdate
                  ? `当前版本 ${updateInfo.currentVersion}，新版安装包可从应用内打开下载。`
                  : `当前版本 ${updateInfo.currentVersion}，已经是 GitHub Release 上的最新版本。`
                : "以后发布新版后，可以在这里检查并打开安装包下载；Android 仍会要求你确认安装。"}
            </p>
          </div>
          {updateInfo?.hasUpdate && (
            <div className="update-release-note">
              <strong>{updateInfo.apkName || `v${updateInfo.latestVersion}`}</strong>
              <p>{updateInfo.notes.slice(0, 180)}{updateInfo.notes.length > 180 ? "…" : ""}</p>
            </div>
          )}
          <div className="button-row settings-actions">
            <button disabled={updateBusy} onClick={() => void checkUpdate()}>
              <RefreshCw size={17} />{updateBusy ? "检查中…" : "检查更新"}
            </button>
            <button className="secondary-button" disabled={!updateInfo} onClick={openUpdate}>
              <Download size={17} />下载新版
            </button>
          </div>
        </section>
      </div>
    );
  }

  if (activePage === "sync") {
    return (
      <div className="screen-stack profile-subpage">
        <header className="mobile-header row-header subpage-header">
          <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
            ← 返回
          </button>
          <div>
            <p className="mini-label">局域网同步</p>
            <h1>同步状态</h1>
          </div>
        </header>

        <section className="subpage-card">
          <div className="sync-status-summary">
            <article>
              <Wifi size={18} />
              <span>{paired ? "已连接电脑" : "未连接电脑"}</span>
            </article>
            <article>
              <Download size={18} />
              <span>{pendingDownloadCount} 本待下载正文</span>
            </article>
          </div>
          <p className="subtle">电脑端开启同步服务后，可以扫码或粘贴配对 URL。同步只更新书架、灵感和进度，书籍正文可在书架按需下载。</p>
          <textarea value={pairingText} onChange={(event) => onPairingTextChange(event.target.value)} placeholder="粘贴电脑端配对 URL 或二维码载荷" />
          <div className="button-row settings-actions">
            <button onClick={onScanQr}>扫码</button>
            <button onClick={onConnectLan}>连接电脑</button>
            <button disabled={!paired} onClick={onSyncDesktop}>
              立即同步
            </button>
          </div>
          <details className="sync-log-panel">
            <summary>同步日志 / 最近一次错误</summary>
            {syncLogs.length ? syncLogs.map((item) => <p key={item}>{item}</p>) : <p>暂无同步日志。</p>}
          </details>
        </section>
      </div>
    );
  }

  if (activePage === "webdav") {
    return (
      <div className="screen-stack profile-subpage">
        <header className="mobile-header row-header subpage-header">
          <button className="ghost-button back-button" onClick={() => onSetActivePage(undefined)}>
            ← 返回
          </button>
          <div>
            <p className="mini-label">跨设备备份</p>
            <h1>WebDAV 设置</h1>
          </div>
        </header>

        <section className="subpage-card">
          <p className="subtle">同步目录固定为 .creation-reading-assistant/，会上传 manifest、records 和 books。WebDAV 密码 / token 仅保存在本机密钥库，AI Key 不参与同步。</p>
          <div className="input-field">
            <input
              value={webdav.endpoint}
              onChange={(event) => handleEndpointChange(event.target.value)}
              placeholder="https://example.com/dav"
              className={webdavError ? "input-error" : ""}
            />
            {webdavError && <p className="input-error-text">{webdavError}</p>}
          </div>
          <input value={webdav.username} onChange={(event) => {
            setWebdavTouched(true);
            setWebdav((current) => ({ ...current, username: event.target.value }));
          }} placeholder="用户名" />
          <input type="password" value={webdav.password} onChange={(event) => {
            setWebdavTouched(true);
            setWebdav((current) => ({ ...current, password: event.target.value }));
          }} placeholder={hasWebDavPassword ? "已保存密码 / token；留空则继续使用" : "密码或 token"} />
          <div className="setting-check-row">
            <CheckCircle2 size={18} />
            <span>{hasWebDavPassword ? "已在本机密钥库保存 WebDAV 密码 / token；不会导出或参与同步。" : "还没有保存 WebDAV 密码 / token。"}</span>
          </div>
          <div className="button-row settings-actions">
            <button onClick={() => void testWebDav()} disabled={!!webdavError || !webdav.endpoint}><Wifi size={17} />测试</button>
            <button onClick={() => void uploadWebDav()} disabled={!!webdavError || !webdav.endpoint}><Upload size={17} />上传</button>
            <button onClick={() => void downloadWebDav()} disabled={!!webdavError || !webdav.endpoint}><Download size={17} />下载</button>
            <button className="secondary-button" onClick={() => void clearWebDavCredential()} disabled={!hasWebDavPassword && !webdav.password}>清除凭证</button>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="screen-stack">
      <header className="mobile-header row-header profile-topbar">
        <div>
          <p className="mini-label">本地档案</p>
          <h1>我的</h1>
        </div>
        <button className="round-action" aria-label="更多" aria-expanded={profileMoreOpen} onClick={() => setProfileMoreOpen((value) => !value)}>
          <MoreHorizontal size={22} />
        </button>
      </header>

      {profileMoreOpen && (
        <section className="profile-more-panel" aria-label="更多快捷入口">
          <button onClick={() => openProfileShortcut("storage")}>
            <Database size={18} />
            <span>数据备份</span>
          </button>
          <button onClick={() => openProfileShortcut("privacy")}>
            <Shield size={18} />
            <span>隐私安全</span>
          </button>
          <button onClick={() => openProfileShortcut("about")}>
            <Info size={18} />
            <span>关于应用</span>
          </button>
        </section>
      )}

      <section className="profile-card compact-profile-card">
        <div className="avatar">人</div>
        <div>
          <h2>创作阅读者</h2>
          <p>设备：{getMobileDeviceId()}</p>
        </div>
      </section>

      <section className="profile-grid">
        <button onClick={() => onSetActivePage("sync")}>
          <strong>同步</strong>
          <span>{paired ? "local-desktop-lan 已连接" : "从未同步"}</span>
        </button>
        <button onClick={() => onSetActivePage("notes")}>
          <strong>笔记</strong>
          <span>{snapshot.notes.length} 条</span>
        </button>
      </section>

      {menuGroups.map((group) => (
        <section className="profile-menu-group" key={group.title}>
          <h2>{group.title}</h2>
          <div className="menu-list profile-menu-list">
            {group.items.map((item) => (
              <button key={item.label} onClick={item.action}>
                <span className="profile-menu-icon">{item.icon}</span>
                <span className="profile-menu-text">
                  <strong>{item.label}</strong>
                  {item.desc && <small>{item.desc}</small>}
                </span>
                <ChevronRight size={18} />
              </button>
            ))}
          </div>
        </section>
      ))}

    </div>
  );
}

function calculateReaderProgress(element: HTMLElement, mode: MobileReaderSettings["readerMode"]): number {
  const scrollable = mode === "paged"
    ? Math.max(1, element.scrollWidth - element.clientWidth)
    : Math.max(1, element.scrollHeight - element.clientHeight);
  const current = mode === "paged" ? element.scrollLeft : element.scrollTop;
  return Math.min(100, Math.max(0, (current / scrollable) * 100));
}

function scrollReaderToPercent(element: HTMLElement, progressPercent: number, mode: MobileReaderSettings["readerMode"]): void {
  const bounded = Math.min(100, Math.max(0, progressPercent));
  if (mode === "paged") {
    const scrollable = Math.max(0, element.scrollWidth - element.clientWidth);
    element.scrollTo({ left: (scrollable * bounded) / 100, behavior: "smooth" });
    return;
  }
  const scrollable = Math.max(0, element.scrollHeight - element.clientHeight);
  element.scrollTo({ top: (scrollable * bounded) / 100, behavior: "smooth" });
}

function findCurrentChapter(document: MobileReaderDocument, root?: HTMLElement | null, mode: MobileReaderSettings["readerMode"] = "scroll"): MobileReaderDocument["toc"][number] | undefined {
  if (!root || !document.toc.length) return document.toc[0];
  const marker = (mode === "paged" ? root.scrollLeft : root.scrollTop) + 96;
  let current = document.toc[0];
  for (const item of document.toc) {
    const element = root.querySelector<HTMLElement>(`#${item.id}`);
    const offset = mode === "paged" ? element?.offsetLeft : element?.offsetTop;
    if (element && offset !== undefined && offset <= marker) current = item;
  }
  return current;
}

function createReaderSearchResults(document: MobileReaderDocument, query: string): ReaderSearchResult[] {
  const keyword = query.trim();
  if (!keyword) return [];
  const text = document.plainText.replace(/\s+/g, " ");
  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const results: ReaderSearchResult[] = [];
  let fromIndex = 0;
  let occurrenceIndex = 0;
  while (results.length < 80) {
    const hitIndex = lowerText.indexOf(lowerKeyword, fromIndex);
    if (hitIndex < 0) break;
    const start = Math.max(0, hitIndex - 28);
    const end = Math.min(text.length, hitIndex + keyword.length + 42);
    results.push({
      id: `reader-search-${hitIndex}-${occurrenceIndex}`,
      occurrenceIndex,
      snippet: `${start > 0 ? "…" : ""}${text.slice(start, end)}${end < text.length ? "…" : ""}`,
      progressPercent: text.length ? (hitIndex / text.length) * 100 : 0
    });
    occurrenceIndex += 1;
    fromIndex = hitIndex + lowerKeyword.length;
  }
  return results;
}

function jumpToReaderSearchResult(root: HTMLElement, query: string, occurrenceIndex: number): boolean {
  const keyword = query.trim();
  if (!keyword) return false;
  const lowerKeyword = keyword.toLowerCase();
  const walker = window.document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let currentNode = walker.nextNode();
  let seen = 0;
  while (currentNode) {
    const node = currentNode as Text;
    const text = node.nodeValue ?? "";
    const lowerText = text.toLowerCase();
    let fromIndex = 0;
    while (fromIndex < lowerText.length) {
      const hitIndex = lowerText.indexOf(lowerKeyword, fromIndex);
      if (hitIndex < 0) break;
      if (seen === occurrenceIndex) {
        const range = window.document.createRange();
        range.setStart(node, hitIndex);
        range.setEnd(node, Math.min(text.length, hitIndex + keyword.length));
        const selection = window.getSelection();
        selection?.removeAllRanges();
        selection?.addRange(range);
        node.parentElement?.scrollIntoView({ behavior: "smooth", block: "center" });
        return true;
      }
      seen += 1;
      fromIndex = hitIndex + lowerKeyword.length;
    }
    currentNode = walker.nextNode();
  }
  return false;
}

const emptyReaderDocument = (title: string, format: MobileBook["format"]): MobileReaderDocument => ({
  title,
  format,
  html: "<p></p>",
  plainText: "",
  toc: [],
  wordCount: 0
});

function MobileReaderView({
  book,
  content,
  loading,
  loadError,
  snapshot,
  settings,
  onSettingsChange,
  onSnapshotChange,
  onBack,
  onOpenInspiration,
  onMessage
}: {
  book: MobileBook;
  content: string;
  loading?: boolean;
  loadError?: string;
  snapshot: MobileSnapshot;
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onBack: () => void;
  onOpenInspiration: (inspirationId: string) => void;
  onMessage: (message: string) => void;
}) {
  const scrollRef = useRef<HTMLElement>(null);
  const progressSaveTimer = useRef<number>();
  const wakeLockRef = useRef<{ release: () => Promise<void> } | undefined>();
  const readerSessionStartRef = useRef(Date.now());
  const readerSessionStartProgressRef = useRef(progressFor(snapshot, book.id));
  const lastReaderActivityRef = useRef(Date.now());
  const swipeStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const pinchStartRef = useRef<{ distance: number; fontSize: number } | null>(null);
  const [selectionText, setSelectionText] = useState("");
  const [readerPanel, setReaderPanel] = useState<ReaderPanel | null>(null);
  const [readerDrawerTab, setReaderDrawerTab] = useState<ReaderDrawerTab>("toc");
  const [readerControlsVisible, setReaderControlsVisible] = useState(false);
  const [readerNotice, setReaderNotice] = useState("");
  const [lastSavedInspirationId, setLastSavedInspirationId] = useState("");
  const [noteDraft, setNoteDraft] = useState("");
  const [readerSearchQuery, setReaderSearchQuery] = useState("");
  const [activeReadingMs, setActiveReadingMs] = useState(0);
  const [document, setDocument] = useState<MobileReaderDocument>(() => emptyReaderDocument(book.title, book.format));
  const [documentRendering, setDocumentRendering] = useState(false);
  const [documentError, setDocumentError] = useState("");
  const [readerTimeoutError, setReaderTimeoutError] = useState("");
  const [showLoadingHint, setShowLoadingHint] = useState(false);
  const [currentProgress, setCurrentProgress] = useState(() => progressFor(snapshot, book.id));
  const [currentChapter, setCurrentChapter] = useState<MobileReaderDocument["toc"][number]>();
  const [swipeDirection, setSwipeDirection] = useState<"left" | "right" | null>(null);
  const [pageTurnDirection, setPageTurnDirection] = useState<"forward" | "backward" | null>(null);
  const readerSearchResults = useMemo(() => createReaderSearchResults(document, readerSearchQuery), [document, readerSearchQuery]);

  const bookInspirations = useMemo(
    () => snapshot.inspirations.filter((item) => item.source?.bookId === book.id),
    [snapshot.inspirations, book.id]
  );

  useEffect(() => {
    let cancelled = false;
    if (!content) {
      setDocument(emptyReaderDocument(book.title, book.format));
      setDocumentError("");
      setDocumentRendering(false);
      return () => {
        cancelled = true;
      };
    }
    setDocumentRendering(true);
    setDocumentError("");
    void renderMobileDocument(book.format, content, book.title)
      .then((nextDocument) => {
        if (cancelled) return;
        if (!nextDocument.html.trim()) {
          setDocument(buildPlainTextFallbackDocument(book, content));
          setDocumentError("正文解析结果为空，已切换为纯文本兜底阅读。");
          return;
        }
        setDocument(nextDocument);
      })
      .catch((error) => {
        if (cancelled) return;
        const detail = error instanceof Error ? error.message : String(error);
        if (book.format === "epub") {
          setDocument(emptyReaderDocument(book.title, book.format));
          setDocumentError(`EPUB 解析失败：${detail || "未知错误"}`);
          return;
        }
        setDocument(buildPlainTextFallbackDocument(book, content));
        setDocumentError(`正文排版失败，已切换为纯文本兜底：${detail || "未知错误"}`);
      })
      .finally(() => {
        if (!cancelled) setDocumentRendering(false);
      });
    return () => {
      cancelled = true;
    };
  }, [book.format, book.title, content]);

  useEffect(() => {
    setReaderTimeoutError("");
    if (!loading && !documentRendering) return undefined;
    const timer = window.setTimeout(() => {
      setReaderTimeoutError("正文打开超时。可以返回书架重新导入，或在“我的 / 同步”里重新下载正文。");
    }, 12_000);
    return () => window.clearTimeout(timer);
  }, [loading, documentRendering, book.id]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const idleMs = Date.now() - lastReaderActivityRef.current;
      if (idleMs < 45_000) {
        setActiveReadingMs(Date.now() - readerSessionStartRef.current);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!loading && !documentRendering) {
      setShowLoadingHint(false);
      return undefined;
    }
    const timer = window.setTimeout(() => setShowLoadingHint(true), 220);
    return () => window.clearTimeout(timer);
  }, [documentRendering, loading]);

  useEffect(() => {
    const handleReaderBack = (event: Event) => {
      if (loadError || documentError || readerTimeoutError) return;
      if (readerPanel) {
        event.preventDefault();
        setReaderPanel(null);
        return;
      }
      if (readerNotice || selectionText || readerControlsVisible) {
        event.preventDefault();
        setReaderNotice("");
        clearSelectedText();
        setReaderControlsVisible(false);
      }
    };
    window.addEventListener("mobile-reader-back", handleReaderBack);
    return () => window.removeEventListener("mobile-reader-back", handleReaderBack);
  }, [readerPanel, readerNotice, selectionText, readerControlsVisible, loadError, documentError, readerTimeoutError]);

  useEffect(() => {
    const element = scrollRef.current;
    if (!element || !document.html) return;
    scrollReaderToPercent(element, currentProgress, settings.readerMode);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
  }, [document.html, settings.readerMode]);

  useEffect(() => {
    let cancelled = false;
    const releaseWakeLock = async () => {
      const lock = wakeLockRef.current;
      wakeLockRef.current = undefined;
      if (lock) {
        try {
          await lock.release();
        } catch {
          // Some Android WebViews reject release after the document is hidden; safe to ignore.
        }
      }
    };
    const requestWakeLock = async () => {
      await releaseWakeLock();
      if (!settings.keepAwake) return;
      const wakeLockApi = (navigator as Navigator & { wakeLock?: { request: (type: "screen") => Promise<{ release: () => Promise<void> }> } }).wakeLock;
      if (!wakeLockApi) {
        onMessage("当前设备暂不支持屏幕常亮，阅读设置已保留。");
        return;
      }
      try {
        const lock = await wakeLockApi.request("screen");
        if (cancelled) await lock.release();
        else wakeLockRef.current = lock;
      } catch {
        onMessage("屏幕常亮开启失败，请检查系统电池或权限设置。");
      }
    };
    void requestWakeLock();
    return () => {
      cancelled = true;
      void releaseWakeLock();
    };
  }, [settings.keepAwake]);

  const captureSelection = (_event?: React.SyntheticEvent) => {
    lastReaderActivityRef.current = Date.now();
    const text = window.getSelection()?.toString().trim() ?? "";
    setSelectionText(text.slice(0, 800));
  };

  const saveProgress = async (progressPercent = currentProgress) => {
    const bounded = Math.min(100, Math.max(0, progressPercent));
    setCurrentProgress(bounded);
    const next = await saveMobileReadingProgress(snapshot, book, bounded);
    onSnapshotChange(next);
  };

  const triggerReaderPageTurn = (direction: -1 | 1) => {
    setPageTurnDirection(direction > 0 ? "forward" : "backward");
    window.setTimeout(() => setPageTurnDirection(null), 320);
  };

  const finishReaderJump = (message: string) => {
    setReaderPanel(null);
    setReaderControlsVisible(false);
    setReaderNotice(message);
  };

  const jumpToReaderProgress = (progressPercent: number, message: string) => {
    const element = scrollRef.current;
    if (!element) return;
    const bounded = Math.min(100, Math.max(0, progressPercent));
    const direction = bounded >= currentProgress ? 1 : -1;
    lastReaderActivityRef.current = Date.now();
    scrollReaderToPercent(element, bounded, settings.readerMode);
    setCurrentProgress(bounded);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
    triggerReaderPageTurn(direction);
    finishReaderJump(message);
  };

  const jumpToChapter = (target: MobileReaderDocument["toc"][number] | undefined) => {
    const element = scrollRef.current;
    if (!element || !target) return;
    lastReaderActivityRef.current = Date.now();
    const chapterElement = element.querySelector<HTMLElement>(`#${target.id}`);
    if (chapterElement) {
      const targetOffset = settings.readerMode === "paged" ? chapterElement.offsetLeft : chapterElement.offsetTop;
      const currentOffset = settings.readerMode === "paged" ? element.scrollLeft : element.scrollTop;
      const direction = targetOffset >= currentOffset ? 1 : -1;
      if (settings.readerMode === "paged") {
        element.scrollTo({ left: Math.max(0, targetOffset - 12), behavior: "smooth" });
      } else {
        element.scrollTo({ top: Math.max(0, targetOffset - 72), behavior: "smooth" });
      }
      setCurrentChapter(target);
      triggerReaderPageTurn(direction);
      finishReaderJump(`已跳到：${target.title}`);
    }
  };

  const jumpToSearchResult = (result: ReaderSearchResult) => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const jumped = jumpToReaderSearchResult(element, readerSearchQuery, result.occurrenceIndex);
    if (!jumped) scrollReaderToPercent(element, result.progressPercent, settings.readerMode);
    if (jumped && settings.readerMode === "paged") {
      window.setTimeout(() => {
        const selectionElement = window.getSelection()?.anchorNode?.parentElement;
        if (selectionElement) element.scrollTo({ left: Math.max(0, selectionElement.offsetLeft - 12), behavior: "smooth" });
      }, 40);
    }
    setCurrentProgress(result.progressPercent);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
    triggerReaderPageTurn(result.progressPercent >= currentProgress ? 1 : -1);
    finishReaderJump(`已跳到搜索结果：${result.progressPercent.toFixed(1)}%`);
  };

  const openReaderDrawer = (tab: ReaderDrawerTab) => {
    setReaderDrawerTab(tab);
    setReaderPanel(tab);
    setReaderControlsVisible(false);
  };

  const moveChapter = (direction: -1 | 1) => {
    const toc = document.toc;
    if (!toc.length) {
      jumpToReaderProgress(currentProgress + direction * 4, direction > 0 ? "已向后翻动。" : "已向前翻动。");
      return;
    }
    const currentIndex = Math.max(0, toc.findIndex((item) => item.id === currentChapter?.id));
    const nextIndex = Math.min(toc.length - 1, Math.max(0, currentIndex + direction));
    jumpToChapter(toc[nextIndex]);
  };

  const turnReaderPage = (direction: -1 | 1) => {
    const element = scrollRef.current;
    if (!element) return;
    triggerReaderPageTurn(direction);
    if (settings.readerMode === "paged") {
      element.scrollTo({
        left: Math.min(element.scrollWidth, Math.max(0, element.scrollLeft + direction * element.clientWidth)),
        behavior: "smooth"
      });
    } else {
      element.scrollTo({
        top: Math.min(element.scrollHeight, Math.max(0, element.scrollTop + direction * element.clientHeight * 0.86)),
        behavior: "smooth"
      });
    }
    setReaderControlsVisible(false);
    setReaderNotice(direction > 0 ? "下一页" : "上一页");
  };

  const handleReaderScroll = () => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const nextProgress = calculateReaderProgress(element, settings.readerMode);
    setCurrentProgress(nextProgress);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
    setReaderControlsVisible(false);
    if (progressSaveTimer.current) window.clearTimeout(progressSaveTimer.current);
    progressSaveTimer.current = window.setTimeout(() => {
      void saveProgress(nextProgress);
    }, 900);
  };

  const runBackwardAction = () => {
    settings.readerMode === "paged" ? turnReaderPage(-1) : moveChapter(-1);
  };

  const runForwardAction = () => {
    settings.readerMode === "paged" ? turnReaderPage(1) : moveChapter(1);
  };

  /* Gesture: swipe left/right to navigate */
  const handleReaderTouchStart = (event: React.TouchEvent<HTMLElement>) => {
    lastReaderActivityRef.current = Date.now();
    const touches = event.touches;
    if (touches.length === 2) {
      // Pinch start
      const distance = Math.hypot(
        touches[0].clientX - touches[1].clientX,
        touches[0].clientY - touches[1].clientY
      );
      pinchStartRef.current = { distance, fontSize: settings.fontSize };
      return;
    }
    if (touches.length === 1) {
      swipeStartRef.current = { x: touches[0].clientX, y: touches[0].clientY, time: Date.now() };
    }
  };

  const handleReaderTouchMove = (event: React.TouchEvent<HTMLElement>) => {
    const touches = event.touches;
    if (touches.length === 2 && pinchStartRef.current) {
      // Pinch to zoom font size
      const distance = Math.hypot(
        touches[0].clientX - touches[1].clientX,
        touches[0].clientY - touches[1].clientY
      );
      const ratio = distance / pinchStartRef.current.distance;
      const nextFontSize = Math.round(pinchStartRef.current.fontSize * ratio);
      const clamped = Math.max(12, Math.min(36, nextFontSize));
      if (clamped !== settings.fontSize) {
        onSettingsChange({ ...settings, fontSize: clamped });
      }
    }
  };

  const handleReaderTouchEnd = (event: React.TouchEvent<HTMLElement>) => {
    pinchStartRef.current = null;
    if (!swipeStartRef.current) return;
    const touches = event.changedTouches;
    if (!touches.length) { swipeStartRef.current = null; return; }
    const dx = touches[0].clientX - swipeStartRef.current.x;
    const dy = touches[0].clientY - swipeStartRef.current.y;
    const elapsed = Date.now() - swipeStartRef.current.time;
    const absDx = Math.abs(dx);
    const absDy = Math.abs(dy);
    swipeStartRef.current = null;

    // Need at least 50px horizontal, within 400ms, and more horizontal than vertical
    if (absDx < 50 || elapsed > 400 || absDy > absDx) return;

    if (dx > 0) {
      // Swipe right → backward
      setSwipeDirection("right");
      runBackwardAction();
    } else {
      // Swipe left → forward
      setSwipeDirection("left");
      runForwardAction();
    }
    setTimeout(() => setSwipeDirection(null), 300);
  };

  const handleReaderTap = (event: MouseEvent<HTMLElement>) => {
    lastReaderActivityRef.current = Date.now();
    if ((window.getSelection()?.toString().trim() ?? "").length > 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const ratio = x / rect.width;
    const verticalRatio = y / rect.height;
    if (settings.tapZoneMode === "five-zone" && ratio >= 0.24 && ratio <= 0.76) {
      if (verticalRatio < 0.26) {
        runBackwardAction();
        return;
      }
      if (verticalRatio > 0.74) {
        runForwardAction();
        return;
      }
    }
    if (ratio < 0.24) {
      runBackwardAction();
      return;
    }
    if (ratio > 0.76) {
      runForwardAction();
      return;
    }
    setReaderControlsVisible((value) => !value);
  };

  const addReaderInspiration = async () => {
    const sourceExcerpt = selectionText || undefined;
    const next = await addMobileInspiration(snapshot, {
      title: `阅读灵感：${book.title}`,
      body: "",
      tags: ["阅读灵感", book.format.toUpperCase()],
      source: {
        bookId: book.id,
        bookTitle: book.title,
        bookAuthor: book.author,
        format: book.format,
        chapterTitle: document.toc[0]?.title,
        locationLabel: currentProgress ? `${currentProgress.toFixed(1)}%` : "当前位置附近",
        progressPercent: currentProgress,
        excerpt: sourceExcerpt,
        createdFrom: sourceExcerpt ? "reader-selection" : "reader-note",
        createdAt: new Date().toISOString()
      }
    });
    const saved = next.inspirations[0];
    setLastSavedInspirationId(saved.id);
    setReaderNotice(sourceExcerpt ? "已记录灵感，并把选中文字保存为来源摘录。" : "已记录灵感，并保存了当前书籍与阅读位置。");
    onSnapshotChange(next);
    onMessage(sourceExcerpt ? "已把选中文字作为来源摘录保存到灵感中心。" : "已记录来自当前书籍和阅读位置的灵感。");
  };

  const addReaderBookmark = async () => {
    const next = await addMobileNote(snapshot, {
      book,
      title: `书签：${currentChapter?.title ?? book.title}`,
      body: "",
      excerpt: selectionText || undefined,
      chapterTitle: currentChapter?.title,
      progressPercent: currentProgress,
      kind: "bookmark"
    });
    onSnapshotChange(next);
    setReaderNotice(`已添加书签：${currentProgress.toFixed(1)}%`);
    onMessage("已在当前阅读位置添加书签。");
  };

  const addReaderNote = async () => {
    const content = noteDraft.trim() || selectionText.trim();
    if (!content) {
      setReaderNotice("先选中文字，或在笔记抽屉里写一点内容。");
      return;
    }
    const next = await addMobileNote(snapshot, {
      book,
      title: `笔记：${currentChapter?.title ?? book.title}`,
      body: content,
      excerpt: selectionText || undefined,
      chapterTitle: currentChapter?.title,
      progressPercent: currentProgress,
      kind: "note"
    });
    onSnapshotChange(next);
    setNoteDraft("");
    setReaderNotice("已保存阅读笔记。");
    onMessage("已保存当前书籍的阅读笔记。");
  };

  const searchSelectedText = () => {
    const keyword = selectionText.trim();
    if (!keyword) return;
    setReaderSearchQuery(keyword.slice(0, 80));
    openReaderDrawer("search");
  };

  const copySelectedText = async () => {
    const text = selectionText.trim();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setReaderNotice("已复制选中文字。");
      onMessage("已复制选中文字。");
    } catch {
      setReaderNotice("复制失败，请使用系统选择菜单复制。");
    }
  };

  const clearSelectedText = () => {
    window.getSelection()?.removeAllRanges();
    setSelectionText("");
  };

  const closeReader = async () => {
    const elapsedMs = Math.max(1000, Date.now() - readerSessionStartRef.current);
    const saved = await saveMobileReadingProgress(snapshot, book, currentProgress);
    const next = await addMobileReadingSession(saved, book as LibraryBook, elapsedMs, currentProgress);
    onSnapshotChange(next);
    onBack();
  };

  const progressLabel = `${currentProgress.toFixed(2)}%`;
  const chapterIndex = document.toc.findIndex((item) => item.id === currentChapter?.id);
  const chapterLabel = currentChapter ? `${chapterIndex + 1}/${document.toc.length} · ${currentChapter.title}` : "正文";
  const bookNotes = snapshot.notes.filter((item) => item.bookId === book.id && item.kind !== "bookmark");
  const bookBookmarks = snapshot.notes.filter((item) => item.bookId === book.id && item.kind === "bookmark");
  const showSelectionToolbar = Boolean(selectionText) && !readerPanel;
  const savedBookReadingMs = snapshot.progress.find((item) => item.bookId === book.id)?.totalReadingTimeMs ?? 0;
  const sessionProgressDelta = Math.max(0, currentProgress - readerSessionStartProgressRef.current);
  const sessionWords = Math.round(document.wordCount * sessionProgressDelta / 100);
  const readerSpeed = activeReadingMs > 0 && sessionWords > 0 ? Math.round(sessionWords / Math.max(1, activeReadingMs / 60_000)) : 0;
  const readerEmptyMessage = !loading && !documentRendering && !document.html.trim()
    ? "没有读到正文内容。请返回书架重新导入本地文件，或在同步后下载正文。"
    : "";
  const readerErrorMessage = loadError || (book.format === "epub" ? documentError : "") || readerTimeoutError || readerEmptyMessage;
  const hasReadableDocument = !loading && !documentRendering && !readerErrorMessage && Boolean(document.html.trim());

  return (
    <main className={`reader-shell reader-bg-${settings.readerBackground} reader-mode-${settings.readerMode} reader-tap-${settings.tapZoneMode} ${readerControlsVisible ? "" : "reader-chrome-hidden"} ${readerErrorMessage ? "reader-has-error" : ""} ${loading || documentRendering ? "reader-is-loading" : ""}`}>
      <div className="reader-dim-layer" style={{ opacity: Math.max(0, Math.min(0.58, (100 - settings.brightness) / 100)) }} aria-hidden="true" />
      <header className="reader-topbar">
        <button className="ghost-button reader-icon-button" onClick={() => void closeReader()} aria-label="返回书架">
          ←
        </button>
        <div className="reader-title-block">
          <strong>{book.title}</strong>
          <p>
            {book.format.toUpperCase()} · {progressLabel} · 本次 {formatDuration(activeReadingMs)}
          </p>
        </div>
        <div className="reader-topbar-actions">
          <button className="ghost-button reader-icon-button" onClick={() => openReaderDrawer("search")} aria-label="搜索">
            <Search size={20} />
          </button>
          <button className="ghost-button reader-icon-button" onClick={() => openReaderDrawer("toc")} aria-label="目录">
            <Menu size={20} />
          </button>
          <button className="ghost-button reader-icon-button" onClick={() => setReaderPanel("book-info")} aria-label="书籍信息">
            ⋮
          </button>
        </div>
      </header>

      {readerPanel && (
        <section className={`reader-panel reader-panel-${readerPanel}`} role="dialog" aria-modal="true">
          <header className="reader-panel-header">
            <button className="ghost-button reader-icon-button" onClick={() => setReaderPanel(null)} aria-label="返回阅读">
              ←
            </button>
            <div>
              <p>{book.title}</p>
              <h2>
                {readerPanel === "settings" ? "阅读设置" :
                  readerPanel === "book-info" ? "书籍信息" :
                  readerDrawerTab === "toc" ? "目录" :
                  readerDrawerTab === "search" ? "搜索" :
                  readerDrawerTab === "bookmarks" ? "书签" :
                  readerDrawerTab === "inspirations" ? "灵感记录" : "笔记"}
              </h2>
            </div>
          </header>

          {readerPanel !== "settings" && readerPanel !== "book-info" && (
            <div className="reader-drawer-tabs reader-panel-tabs">
              {([
                ["toc", "目录"],
                ["search", `搜索 ${readerSearchResults.length}`],
                ["bookmarks", `书签 ${bookBookmarks.length}`],
                ["notes", `笔记 ${bookNotes.length}`],
                ["inspirations", `灵感 ${bookInspirations.length}`]
              ] as Array<[ReaderDrawerTab, string]>).map(([key, label]) => (
                <button key={key} className={readerDrawerTab === key ? "active" : ""} onClick={() => setReaderDrawerTab(key)}>
                  {label}
                </button>
              ))}
            </div>
          )}

          <div className="reader-panel-body">
            {readerPanel === "book-info" && (
              <div className="reader-info-panel">
                <div className="reader-info-cover">{book.title.slice(0, 4)}</div>
                <h3>{book.title}</h3>
                <p>{book.author || "作者未知"} · {book.format.toUpperCase()} · {progressLabel}</p>
                <dl>
                  <div><dt>当前章节</dt><dd>{chapterLabel}</dd></div>
                  <div><dt>总字数</dt><dd>{document.wordCount.toLocaleString("zh-CN")} 字</dd></div>
                  <div><dt>本次阅读</dt><dd>{formatDuration(activeReadingMs)}</dd></div>
                  <div><dt>来源文件</dt><dd>{book.originalFileName || book.originalFilePath || "本地导入"}</dd></div>
                </dl>
                {loadError && <p className="reader-error-line">正文读取失败：{loadError}</p>}
                <button onClick={() => setReaderPanel("settings")}>打开阅读设置</button>
              </div>
            )}

            {readerPanel === "settings" && (
              <div className="reader-settings-panel">
                <div className="reader-mode-grid" aria-label="阅读模式">
                  <button className={settings.readerMode === "paged" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, readerMode: "paged" })}>
                    横向分页
                  </button>
                  <button className={settings.readerMode === "scroll" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, readerMode: "scroll" })}>
                    上下滚动
                  </button>
                  <button className={settings.fontWeight === "bold" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, fontWeight: settings.fontWeight === "bold" ? "regular" : "bold" })}>
                    加粗
                  </button>
                  <button onClick={() => onSettingsChange(defaultReaderSettings)}>
                    重置
                  </button>
                </div>
                <div className="reader-mode-grid" aria-label="点击区域">
                  <button className={settings.tapZoneMode === "three-zone" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, tapZoneMode: "three-zone" })}>
                    三分区
                  </button>
                  <button className={settings.tapZoneMode === "five-zone" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, tapZoneMode: "five-zone" })}>
                    五分区
                  </button>
                  <button className={settings.showProgressBar ? "active" : ""} onClick={() => onSettingsChange({ ...settings, showProgressBar: !settings.showProgressBar })}>
                    进度条
                  </button>
                  <button className={settings.keepAwake ? "active" : ""} onClick={() => onSettingsChange({ ...settings, keepAwake: !settings.keepAwake })}>
                    常亮
                  </button>
                </div>
                <label>
                  字号
                  <input type="range" min="15" max="28" value={settings.fontSize} onChange={(event) => onSettingsChange({ ...settings, fontSize: Number(event.target.value) })} />
                </label>
                <label>
                  行距
                  <input type="range" min="1.4" max="2.4" step="0.05" value={settings.lineHeight} onChange={(event) => onSettingsChange({ ...settings, lineHeight: Number(event.target.value) })} />
                </label>
                <label>
                  边距
                  <input type="range" min="10" max="42" value={settings.pageMargin} onChange={(event) => onSettingsChange({ ...settings, pageMargin: Number(event.target.value) })} />
                </label>
                <label>
                  段距
                  <input type="range" min="0.7" max="1.8" step="0.05" value={settings.paragraphSpacing} onChange={(event) => onSettingsChange({ ...settings, paragraphSpacing: Number(event.target.value) })} />
                </label>
                <label>
                  亮度
                  <input type="range" min="45" max="100" value={settings.brightness} onChange={(event) => onSettingsChange({ ...settings, brightness: Number(event.target.value) })} />
                </label>
                <div className="reader-background-grid">
                  {(["white", "warm", "green", "night"] as const).map((background) => (
                    <button key={background} className={settings.readerBackground === background ? "active" : ""} onClick={() => onSettingsChange({ ...settings, readerBackground: background })}>
                      {background === "white" ? "白纸" : background === "warm" ? "暖纸" : background === "green" ? "护眼" : "夜间"}
                    </button>
                  ))}
                </div>
                <p className="subtle">横向分页更接近阅读 App；上下滚动适合查找和长文浏览。返回键会先回到正文，不会直接退出阅读。</p>
              </div>
            )}

            {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "toc" && (
              <div className="reader-toc-list">
                {document.toc.length ? document.toc.map((item) => (
                  <a
                    key={item.id}
                    href={`#${item.id}`}
                    onClick={(event) => {
                      event.preventDefault();
                      jumpToChapter(item);
                    }}
                  >
                    {item.title}
                  </a>
                )) : <p className="empty-hint">这本书暂未识别到目录。</p>}
              </div>
            )}

            {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "search" && (
              <div className="reader-search-panel">
                <label>
                  <span>搜索当前书籍</span>
                  <input value={readerSearchQuery} onChange={(event) => setReaderSearchQuery(event.target.value)} placeholder="输入书名、人名、设定或句子片段" autoFocus />
                </label>
                {readerSearchQuery.trim() ? (
                  <>
                    <p className="search-summary">找到 {readerSearchResults.length} 处，最多显示前 80 条。</p>
                    <div className="reader-search-results">
                      {readerSearchResults.length ? readerSearchResults.map((result) => (
                        <button key={result.id} onClick={() => jumpToSearchResult(result)}>
                          <strong>{result.progressPercent.toFixed(1)}%</strong>
                          <span>{result.snippet}</span>
                        </button>
                      )) : <p className="empty-hint">没有搜到。可以换一个更短的关键词。</p>}
                    </div>
                  </>
                ) : (
                  <p className="empty-hint">搜索会在当前 TXT / Markdown / EPUB 正文中查找，点击结果后直接跳到正文位置。</p>
                )}
              </div>
            )}

            {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "bookmarks" && (
              <div className="reader-note-list">
                <button onClick={() => void addReaderBookmark()}>在当前位置添加书签</button>
                {bookBookmarks.length ? bookBookmarks.map((item) => (
                  <article key={item.id} className="reader-note-item">
                    <strong>{item.title}</strong>
                    <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                    {item.excerpt && <p>{item.excerpt}</p>}
                    <button onClick={() => jumpToReaderProgress(item.progressPercent ?? 0, `已跳到书签：${(item.progressPercent ?? 0).toFixed(1)}%`)}>跳转</button>
                  </article>
                )) : <p className="empty-hint">还没有书签。阅读时点底部“书签”即可保存当前位置。</p>}
              </div>
            )}

            {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "notes" && (
              <div className="reader-note-list">
                <textarea value={noteDraft} onChange={(event) => setNoteDraft(event.target.value)} placeholder="写一条阅读笔记；如果你选中了正文，也会一起保存为摘录。" />
                <button onClick={() => void addReaderNote()}>保存笔记</button>
                {bookNotes.length ? bookNotes.map((item) => (
                  <article key={item.id} className="reader-note-item">
                    <strong>{item.title}</strong>
                    <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                    {item.excerpt && <blockquote>{item.excerpt}</blockquote>}
                    <p>{item.body}</p>
                    <button onClick={() => jumpToReaderProgress(item.progressPercent ?? 0, `已跳到笔记：${(item.progressPercent ?? 0).toFixed(1)}%`)}>跳转</button>
                  </article>
                )) : <p className="empty-hint">还没有笔记。可以先选中文字，再打开这里保存。</p>}
              </div>
            )}

            {readerPanel !== "settings" && readerPanel !== "book-info" && readerDrawerTab === "inspirations" && (
              <div className="reader-note-list">
                <button onClick={() => void addReaderInspiration()}>把当前位置记为灵感</button>
                {bookInspirations.length ? bookInspirations.map((item) => (
                  <article key={item.id} className="reader-note-item">
                    <strong>{item.title}</strong>
                    <span>{item.source?.locationLabel || `${item.source?.progressPercent?.toFixed(1) ?? 0}%`}</span>
                    {item.source?.excerpt && <blockquote>{item.source.excerpt}</blockquote>}
                    <p>{item.body || "还没有正文。"}</p>
                    <button onClick={() => onOpenInspiration(item.id)}>打开灵感</button>
                  </article>
                )) : <p className="empty-hint">这本书还没有灵感记录。选中文字后点“灵感”，来源会单独保存。</p>}
              </div>
            )}
          </div>
        </section>
      )}

      <section
        ref={scrollRef}
        className={`reader-scroll-container ${swipeDirection ? `swipe-${swipeDirection}` : ""}`}
        data-page-turn={pageTurnDirection ?? undefined}
        onClick={handleReaderTap}
        onScroll={handleReaderScroll}
        onMouseUp={captureSelection}
        onTouchStart={handleReaderTouchStart}
        onTouchMove={handleReaderTouchMove}
        onTouchEnd={(event) => {
          captureSelection(event);
          handleReaderTouchEnd(event);
        }}
      >
        {(loading || documentRendering) && showLoadingHint && (
          <div className="reader-loading-state" role="status" aria-live="polite">
            <strong>{loading ? "正在读取本地正文" : book.format === "epub" ? "正在解析 EPUB 章节" : "正在排版正文"}</strong>
            <p>{book.format === "epub" ? "EPUB 首次打开需要解析章节和图片；解析完成后会自动显示。" : "本地文件正在载入。小书通常会直接打开，大书可能需要几秒。"}</p>
          </div>
        )}
        {readerErrorMessage && (
          <div className="reader-loading-state reader-load-error" role="alert">
            <strong>{book.format === "epub" ? "EPUB 暂时打不开" : "正文暂时打不开"}</strong>
            <p>{readerErrorMessage}</p>
            <div className="reader-error-actions">
              <button onClick={() => void closeReader()}>返回书架</button>
              <button className="secondary-button" onClick={() => setReaderPanel("book-info")}>查看书籍信息</button>
            </div>
          </div>
        )}
        {!readerErrorMessage && documentError && (
          <div className="reader-loading-state reader-fallback-note" role="status">
            <strong>已启用兜底阅读</strong>
            <p>{documentError}</p>
          </div>
        )}
        {hasReadableDocument && (
          <article
            className="reader-content"
            style={{
              fontSize: `${settings.fontSize}px`,
              lineHeight: settings.lineHeight,
              padding: `${settings.pageMargin}px`,
              ["--reader-paragraph-spacing" as string]: `${settings.paragraphSpacing}em`,
              fontWeight: settings.fontWeight === "bold" ? 650 : 400
            }}
            dangerouslySetInnerHTML={{ __html: document.html }}
          />
        )}
      </section>

      {readerControlsVisible && (
        <div className="reader-zone-guide" aria-hidden="true">
          <span>{settings.readerMode === "paged" ? "上一页" : "上一章"}</span>
          <span>{settings.tapZoneMode === "five-zone" ? "上/下也可翻动" : "轻触隐藏菜单"}</span>
          <span>{settings.readerMode === "paged" ? "下一页" : "下一章"}</span>
        </div>
      )}

      {readerNotice && (
        <section className="reader-notice" role="status" aria-live="polite">
          <div>
            <strong>{readerNotice}</strong>
            <p>{selectionText ? `来源摘录：${selectionText.slice(0, 48)}${selectionText.length > 48 ? "…" : ""}` : `来源：${book.title} · ${currentProgress.toFixed(1)}%`}</p>
          </div>
          <div className="reader-notice-actions">
            <button disabled={!lastSavedInspirationId} onClick={() => onOpenInspiration(lastSavedInspirationId)}>
              查看灵感
            </button>
            <button onClick={() => setReaderNotice("")}>继续阅读</button>
          </div>
        </section>
      )}

      {showSelectionToolbar && (
        <section className="reader-selection-toolbar" role="toolbar" aria-label="选中文字操作">
          <p>{selectionText.slice(0, 42)}{selectionText.length > 42 ? "…" : ""}</p>
          <div>
            <button onClick={() => void addReaderInspiration()}>记为灵感</button>
            <button onClick={() => void addReaderNote()}>存笔记</button>
            <button onClick={searchSelectedText}>搜索</button>
            <button onClick={() => void copySelectedText()}>复制</button>
            <button className="ghost-button" onClick={clearSelectedText}>清除</button>
          </div>
        </section>
      )}

      <footer className="reader-bottom-sheet">
        <div className="reader-progress-chip reader-menu-chip" aria-live="polite">
          <span>{chapterLabel}</span>
          <strong>{progressLabel}</strong>
        </div>
        <div className="reader-stat-row reader-menu-stats">
          <span><strong>{formatDuration(savedBookReadingMs + activeReadingMs)}</strong>阅读</span>
          <span><strong>{readerSpeed ? `${readerSpeed}` : "—"}</strong>字/分</span>
          <span><strong>{snapshot.inspirations.filter((item) => item.source?.bookId === book.id).length}</strong>灵感</span>
          <span><strong>{bookBookmarks.length}</strong>书签</span>
        </div>
        <div className="reader-chapter-control-row">
          <button onClick={() => moveChapter(-1)}>上一章</button>
          {settings.showProgressBar ? (
            <input
              className="reader-progress-slider"
              type="range"
              min="0"
              max="100"
              step="0.1"
              value={currentProgress}
              onChange={(event) => {
                jumpToReaderProgress(Number(event.target.value), `已跳到 ${Number(event.target.value).toFixed(1)}%`);
                void saveProgress(Number(event.target.value));
              }}
            />
          ) : (
            <div className="reader-progress-disabled">{progressLabel}</div>
          )}
          <button onClick={() => moveChapter(1)}>下一章</button>
        </div>
        <div className="reader-actions reader-primary-actions">
          <button onClick={() => void addReaderInspiration()} aria-label="记为灵感">
            <Sparkles size={20} /><span>灵感</span>
          </button>
          <button onClick={() => openReaderDrawer("toc")}>
            <Menu size={20} /><span>目录</span>
          </button>
          <button onClick={() => openReaderDrawer("search")}>
            <Search size={20} /><span>搜索</span>
          </button>
          <button onClick={() => void addReaderBookmark()}>
            <Star size={20} /><span>书签</span>
          </button>
          <button onClick={() => setReaderPanel("settings")}>
            <span className="reader-aa-icon">Aa</span><span>设置</span>
          </button>
        </div>
      </footer>
    </main>
  );
}
