import { useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import jsQR from "jsqr";
import type { LibraryBook } from "../../src/types/library";
import type { SyncEnvelope, SyncPushPayload } from "../../src/types/sync";
import { renderMobileDocument, type MobileReaderDocument } from "./reader/mobile-reader";
import {
  addMobileInspiration,
  addMobileNote,
  addMobileReadingSession,
  BOOK_CONTENT_STORAGE_KEY_PREFIX,
  createImportedMobileBook,
  getMobileDeviceId,
  loadMobileSnapshot,
  saveMobileBook,
  saveMobileReadingProgress,
  saveMobileSnapshot,
  saveSyncedMobileBookBlob,
  saveSyncedMobileBookFile,
  saveSyncAccount,
  type MobileSnapshot
} from "./services/mobile-storage";
import {
  buildBookRanking,
  buildMobileStatsSummary,
  buildNoteInsights,
  buildReadingTimeline,
  getStatsPeriodTitle,
  statsPeriodLabels,
  type StatsPeriod
} from "./services/mobile-stats";
import { createSyncClient, pairWithFirstReachable, parsePairingCandidates, type PairingInput } from "./services/sync-client";
import { readMobileBookFile } from "./storage/mobile-files";
import { downloadWebDavSnapshot, testWebDavConnection, uploadWebDavSnapshot } from "./sync/webdav-sync";
import type { MobileBook, MobileReaderSettings, SyncAccount } from "./types/mobile";

type MainTab = "home" | "shelf" | "inspiration" | "stats" | "profile";
type ShelfViewMode = "grid" | "list";
type ShelfFilterMode = "all" | "reading" | "downloaded" | "pending";
type ShelfSortMode = "recent" | "title" | "progress";
type ReaderDrawerTab = "toc" | "search" | "bookmarks" | "notes";
type ReaderSearchResult = { id: string; occurrenceIndex: number; snippet: string; progressPercent: number };

const defaultReaderSettings: MobileReaderSettings = {
  fontSize: 18,
  lineHeight: 1.85,
  pageMargin: 22,
  paragraphSpacing: 1.15,
  readerBackground: "warm",
  readerMode: "scroll",
  fontWeight: "regular",
  tapZoneMode: "three-zone",
  showProgressBar: true,
  keepAwake: false,
  brightness: 100
};

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

async function readImportFileContent(file: File): Promise<string> {
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
  const [snapshot, setSnapshot] = useState<MobileSnapshot>(emptySnapshot);
  const [pairingText, setPairingText] = useState("");
  const [paired, setPaired] = useState<PairingInput>();
  const [readerBook, setReaderBook] = useState<MobileBook>();
  const [readerContent, setReaderContent] = useState("");
  const [readerSettings, setReaderSettings] = useState<MobileReaderSettings>(defaultReaderSettings);
  const [message, setMessage] = useState("本地优先：没有网络也能阅读、记录灵感，回到同一网络后再同步。");
  const [showQrScanner, setShowQrScanner] = useState(false);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);
  const [downloadingBookId, setDownloadingBookId] = useState<string>();
  const downloadAbortRef = useRef<AbortController>();

  useEffect(() => {
    void loadMobileSnapshot().then(setSnapshot);
  }, []);

  const client = useMemo(() => (paired ? createSyncClient(paired) : undefined), [paired]);
  const stats = useMemo(() => buildMobileStatsSummary(snapshot, "total"), [snapshot]);

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

  const openBook = async (book: MobileBook) => {
    const content = await readMobileBookContent(book);
    setReaderContent(content ?? `${book.title}\n\n这本书来自同步或历史数据，正文文件还没有下载到手机。请先在“我的 / 同步”里点“立即同步”，或在书架重新导入本地文件。`);
    setReaderBook(book);
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
    let next = snapshot;
    for (const file of Array.from(files)) {
      const content = await readImportFileContent(file);
      const imported = await createImportedMobileBook(file.name, content, file.size);
      next = await saveMobileBook(next, imported);
    }
    setSnapshot(next);
    setMessage(`已导入 ${files.length} 本书；重复书籍会用“重复导入 #”标签区分。`);
  };

  const addQuickInspiration = async () => {
    const next = await addMobileInspiration(snapshot, {
      title: "新的灵感",
      body: "",
      tags: ["快速记录"]
    });
    setSnapshot(next);
    setTab("inspiration");
    setMessage("已创建一条空白灵感，可以继续补正文或交给 AI 打磨。");
  };

  const bottomTabs: Array<{ key: MainTab; label: string; icon: string }> = [
    { key: "home", label: "首页", icon: "⌂" },
    { key: "shelf", label: "书架", icon: "▥" },
    { key: "inspiration", label: "灵感", icon: "✦" },
    { key: "stats", label: "统计", icon: "▤" },
    { key: "profile", label: "我的", icon: "●" }
  ];

  if (readerBook) {
    return (
      <MobileReaderView
        book={readerBook}
        content={readerContent}
        snapshot={snapshot}
        settings={readerSettings}
        onSettingsChange={setReaderSettings}
        onSnapshotChange={setSnapshot}
        onBack={() => setReaderBook(undefined)}
        onOpenInspiration={() => {
          setReaderBook(undefined);
          setTab("inspiration");
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
            onGo={(nextTab) => setTab(nextTab)}
          />
        )}
        {tab === "shelf" && (
          <ShelfPage
            snapshot={snapshot}
            downloadingBookId={downloadingBookId}
            onOpenBook={(book) => void openBook(book)}
            onImport={(files) => void importFiles(files)}
            onDownloadBook={(book) => void downloadBookToMobile(book)}
            onCancelDownload={cancelBookDownload}
          />
        )}
        {tab === "inspiration" && <InspirationPage snapshot={snapshot} onSnapshotChange={setSnapshot} />}
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

      <nav className="bottom-nav" aria-label="主导航">
        {bottomTabs.map((item) => (
          <button key={item.key} className={tab === item.key ? "active" : ""} onClick={() => setTab(item.key)}>
            <span>{item.icon}</span>
            {item.label}
          </button>
        ))}
      </nav>
    </main>
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
  onGo
}: {
  snapshot: MobileSnapshot;
  stats: { totalReadingMs: number; readingDays: number; completed: number; words: number; speed: number };
  paired: boolean;
  onOpenBook: (book: MobileBook) => void;
  onAddInspiration: () => void;
  onGo: (tab: MainTab) => void;
}) {
  const continueBooks = getContinueBooks(snapshot);
  return (
    <div className="screen-stack">
      <header className="mobile-header">
        <p className="mini-label">创作阅读助手</p>
        <h1>首页</h1>
      </header>

      <section className="metric-grid">
        <article className="metric-card">
          <span className="metric-icon">▥</span>
          <p>累计阅读</p>
          <strong>{snapshot.books.length} 本</strong>
        </article>
        <article className="metric-card">
          <span className="metric-icon">◷</span>
          <p>阅读时长</p>
          <strong>{formatDuration(stats.totalReadingMs)}</strong>
        </article>
      </section>

      <section className="section-block">
        <div className="section-heading">
          <h2>继续阅读</h2>
          <button className="ghost-button" onClick={() => onGo("shelf")}>
            全部书籍 ›
          </button>
        </div>
        <div className="continue-strip">
          {continueBooks.length ? (
            continueBooks.map((book) => (
              <button key={book.id} className="continue-card" onClick={() => onOpenBook(book)}>
                <div className="book-cover compact">{book.title.slice(0, 2)}</div>
                <span>{book.title}</span>
                <small>{progressFor(snapshot, book.id).toFixed(2)}%</small>
              </button>
            ))
          ) : (
            <p className="empty-hint">导入一本 TXT、Markdown 或 EPUB，就能从这里继续阅读。</p>
          )}
        </div>
      </section>

      <section className="inspiration-quick-card">
        <div>
          <p className="mini-label">灵感中心</p>
          <h2>读到有火花的地方，就把它留下</h2>
          <p>手机端是随手捕捉灵感的入口；AI 候选版本留在灵感里，不覆盖原文。</p>
        </div>
        <button className="inspiration-fab" onClick={onAddInspiration}>
          ＋
        </button>
      </section>

      <section className="sync-status-card">
        <div>
          <h2>同步状态</h2>
          <p>{paired ? "已连接电脑局域网同步，可拉取书籍与灵感。" : "未连接电脑。也可以先离线使用，稍后再同步。"}</p>
          <p className="subtle">阅读目标不在本应用中启用，我们把首页空间留给继续阅读和灵感记录。</p>
        </div>
        <button onClick={() => onGo("profile")}>去同步</button>
      </section>
    </div>
  );
}

function ShelfPage({
  snapshot,
  downloadingBookId,
  onOpenBook,
  onImport,
  onDownloadBook,
  onCancelDownload
}: {
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  onOpenBook: (book: MobileBook) => void;
  onImport: (files: FileList | null) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
}) {
  const [query, setQuery] = useState("");
  const [viewMode, setViewMode] = useState<ShelfViewMode>("grid");
  const [filterMode, setFilterMode] = useState<ShelfFilterMode>("all");
  const [sortMode, setSortMode] = useState<ShelfSortMode>("recent");
  const [detailBookId, setDetailBookId] = useState("");
  const detailBook = detailBookId ? snapshot.books.find((book) => book.id === detailBookId) : undefined;
  const filtered = snapshot.books
    .filter((book) => `${book.title} ${book.author ?? ""} ${book.importLabel ?? ""}`.toLowerCase().includes(query.toLowerCase()))
    .filter((book) => {
      const progress = progressFor(snapshot, book.id);
      if (filterMode === "reading") return progress > 0 && progress < 100;
      if (filterMode === "downloaded") return isBookDownloaded(book);
      if (filterMode === "pending") return !isBookDownloaded(book);
      return true;
    })
    .sort((left, right) => {
      if (sortMode === "title") return left.title.localeCompare(right.title, "zh-Hans-CN");
      if (sortMode === "progress") return progressFor(snapshot, right.id) - progressFor(snapshot, left.id);
      const leftProgress = snapshot.progress.find((item) => item.bookId === left.id)?.lastReadAt ?? left.updatedAt;
      const rightProgress = snapshot.progress.find((item) => item.bookId === right.id)?.lastReadAt ?? right.updatedAt;
      return rightProgress.localeCompare(leftProgress);
    });
  return (
    <div className="screen-stack">
      <header className="mobile-header row-header">
        <div>
          <p className="mini-label">本地书库</p>
          <h1>书架</h1>
        </div>
        <label className="round-action">
          ＋
          <input hidden type="file" accept=".txt,.md,.markdown,.epub" multiple onChange={(event) => void onImport(event.currentTarget.files)} />
        </label>
      </header>

      <label className="search-pill">
        🔍
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={`搜索 ${snapshot.books.length} 本书`} />
      </label>

      <section className="shelf-toolbar" aria-label="书架筛选和视图">
        <div className="segmented-control">
          {([
            ["all", "全部"],
            ["reading", "在读"],
            ["downloaded", "已下载"],
            ["pending", "待下载"]
          ] as Array<[ShelfFilterMode, string]>).map(([key, label]) => (
            <button key={key} className={filterMode === key ? "active" : ""} onClick={() => setFilterMode(key)}>
              {label}
            </button>
          ))}
        </div>
        <div className="shelf-subtoolbar">
          <select value={sortMode} onChange={(event) => setSortMode(event.target.value as ShelfSortMode)} aria-label="书籍排序">
            <option value="recent">最近阅读</option>
            <option value="title">书名排序</option>
            <option value="progress">进度优先</option>
          </select>
          <div className="view-toggle" aria-label="书架视图">
            <button className={viewMode === "grid" ? "active" : ""} onClick={() => setViewMode("grid")}>▦</button>
            <button className={viewMode === "list" ? "active" : ""} onClick={() => setViewMode("list")}>☰</button>
          </div>
        </div>
      </section>

      <section className={`book-grid ${viewMode === "list" ? "book-list" : ""}`}>
        {filtered.map((book) => (
          <article key={book.id} className="book-tile" onClick={() => onOpenBook(book)}>
            <div className="book-cover">{book.title.slice(0, 4)}</div>
            <div className="book-meta">
              <h3>{book.title}</h3>
              <p>{book.author || "作者未知"}</p>
              <small>{progressFor(snapshot, book.id).toFixed(2)}% · {book.format.toUpperCase()}</small>
              <div className="book-badges">
                <em>{isBookDownloaded(book) ? "已下载正文" : "未下载正文"}</em>
                {book.duplicateIndex && book.duplicateIndex > 1 ? <em>{book.importLabel}</em> : null}
              </div>
              <div className="book-progress-line" aria-label={`阅读进度 ${progressFor(snapshot, book.id).toFixed(1)}%`}>
                <span style={{ width: `${Math.min(100, Math.max(0, progressFor(snapshot, book.id)))}%` }} />
              </div>
            </div>
            <button
              className="tile-more"
              onClick={(event) => {
                event.stopPropagation();
                setDetailBookId(book.id);
              }}
              aria-label="书籍详情"
            >
              ⋯
            </button>
          </article>
        ))}
      </section>

      {!filtered.length && <p className="empty-hint">书架还没有匹配结果。你可以点右上角导入本地书籍。</p>}
      <p className="center-foot">共 {snapshot.books.length} 本书籍</p>

      {detailBook && (
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
        />
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
  onCancelDownload
}: {
  book: MobileBook;
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  onClose: () => void;
  onOpenBook: (book: MobileBook) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
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
  return (
    <aside className="book-detail-sheet" role="dialog" aria-label={`${book.title} 详情`}>
      <div className="drawer-header">
        <h2>书籍详情</h2>
        <button className="ghost-button" onClick={onClose}>关闭</button>
      </div>
      <section className="book-detail-hero">
        <div className="book-cover detail-cover">{book.title.slice(0, 4)}</div>
        <div>
          <p className="mini-label">{book.format.toUpperCase()} · {downloaded ? "已下载正文" : "未下载正文"}</p>
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
    </aside>
  );
}

function InspirationPage({ snapshot, onSnapshotChange }: { snapshot: MobileSnapshot; onSnapshotChange: (snapshot: MobileSnapshot) => void }) {
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const filtered = snapshot.inspirations.filter((item) =>
    `${item.title} ${item.body} ${item.tags.join(" ")} ${item.source?.bookTitle ?? ""}`.toLowerCase().includes(query.toLowerCase())
  );
  const addDraft = async () => {
    const next = await addMobileInspiration(snapshot, {
      title: draft.split("\n")[0] || "快速记录",
      body: draft,
      tags: ["快速记录"]
    });
    onSnapshotChange(next);
    setDraft("");
  };
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
        <textarea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="写下一句设定、冲突点、人物小动作……" />
        <button disabled={!draft.trim()} onClick={() => void addDraft()}>
          保存灵感
        </button>
      </section>

      <label className="search-pill">
        🔍
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索标题、标签、来源摘录" />
      </label>

      <section className="inspiration-list">
        {filtered.map((item) => (
          <article key={item.id} className="inspiration-card">
            <p className="mini-label">{item.status} · {item.type}</p>
            <h2>{item.title}</h2>
            <p>{item.body || "还没有正文。"}</p>
            {item.source && (
              <div className="source-card">
                <strong>来源摘录</strong>
                <span>{item.source.bookTitle || "未知书籍"} · {item.source.locationLabel || `${item.source.progressPercent?.toFixed(1) ?? 0}%`}</span>
                {item.source.excerpt && <blockquote>{item.source.excerpt}</blockquote>}
              </div>
            )}
            <div className="tag-row">
              {item.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
            <details className="ai-variants">
              <summary>AI 候选版本（{item.variants.length}）</summary>
              {item.variants.length ? item.variants.map((variant) => <p key={variant.id}>{variant.content}</p>) : <p>暂无候选。稍后可在这里展示润色、扩写、去 AI 味结果。</p>}
            </details>
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
  const periodStats = useMemo(() => buildMobileStatsSummary(snapshot, statsPeriod), [snapshot, statsPeriod]);
  const timeline = useMemo(() => buildReadingTimeline(snapshot, statsPeriod), [snapshot, statsPeriod]);
  const ranking = useMemo(() => buildBookRanking(snapshot, statsPeriod), [snapshot, statsPeriod]);
  const insights = useMemo(() => buildNoteInsights(snapshot, statsPeriod), [snapshot, statsPeriod]);
  const maxTimelineMs = Math.max(1, ...timeline.map((item) => item.durationMs));
  const statItems = [
    ["阅读时间", formatDuration(periodStats.totalReadingMs)],
    ["阅读天数", `${periodStats.readingDays} 天`],
    ["累计读过", `${snapshot.books.length} 本`],
    ["读完书籍", `${periodStats.completed} 本`],
    ["在读书籍", `${periodStats.readingBooks} 本`],
    ["记录灵感", `${periodStats.inspirationCount} 条`],
    ["阅读字数", `${periodStats.words} 字`],
    ["阅读速度", `${periodStats.speed} 字/分钟`]
  ];
  return (
    <div className="screen-stack">
      <header className="mobile-header row-header">
        <div>
          <p className="mini-label">Reading stats</p>
          <h1>阅读统计</h1>
        </div>
        <button className="ghost-button">沉淀 {periodStats.noteCount + periodStats.inspirationCount}</button>
      </header>

      <div className="range-tabs stats-period-tabs">
        {(Object.keys(statsPeriodLabels) as StatsPeriod[]).map((item) => (
          <button key={item} className={statsPeriod === item ? "active" : ""} onClick={() => setStatsPeriod(item)}>
            {statsPeriodLabels[item]}
          </button>
        ))}
      </div>

      <section className="stats-card">
        <h2>{getStatsPeriodTitle(statsPeriod)}</h2>
        <div className="stats-grid">
          {statItems.map(([label, value]) => (
            <article key={label}>
              <strong>{value}</strong>
              <span>{label}</span>
            </article>
          ))}
        </div>
      </section>

      <section className="trend-card reading-heat-strip">
        <div className="section-heading">
          <h2>阅读时间趋势</h2>
          <span>{periodStats.sessionCount} 次</span>
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
          <p className="empty-hint">暂无趋势数据。打开一本书读一会儿，这里会自动长出记录。</p>
        )}
      </section>

      <section className="stats-card reading-timeline">
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
          <p className="empty-hint">还没有阅读 session。进入阅读页并返回后，会生成可追踪的阅读记录。</p>
        )}
      </section>

      <section className="stats-card book-ranking-list">
        <div className="section-heading">
          <h2>书籍排行</h2>
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
          <p className="empty-hint">暂无排行。读过的书会按时长自动排在这里。</p>
        )}
      </section>

      <section className="stats-card note-insight-list">
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
          <p className="empty-hint">还没有沉淀。阅读页选中文字后，可以直接记为灵感或笔记。</p>
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
  onMessage
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
}) {
  const [webdav, setWebdav] = useState({ endpoint: "", username: "", password: "" });
  const webdavAccount: SyncAccount = snapshot.syncAccounts.find((item) => item.provider === "webdav") ?? {
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

  const testWebDav = async () => {
    try {
      const result = await testWebDavConnection(webdav);
      const next = await saveSyncAccount(snapshot, {
        provider: "webdav",
        name: "WebDAV",
        endpoint: webdav.endpoint,
        username: webdav.username,
        enabled: result.ok
      });
      onSnapshotChange(next);
      onMessage(result.message);
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const uploadWebDav = async () => {
    try {
      await uploadWebDavSnapshot(webdav, webdavAccount, snapshot);
      onMessage("WebDAV 上传完成。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const downloadWebDav = async () => {
    try {
      const next = await downloadWebDavSnapshot(webdav, snapshot);
      await saveMobileSnapshot(next);
      onSnapshotChange(next);
      onMessage("WebDAV 下载完成，已合并到手机端。");
    } catch (error) {
      onMessage(error instanceof Error ? error.message : String(error));
    }
  };

  return (
    <div className="screen-stack">
      <header className="mobile-header row-header">
        <div>
          <p className="mini-label">本地档案</p>
          <h1>我的</h1>
        </div>
        <button className="round-action">…</button>
      </header>

      <section className="profile-card">
        <div className="avatar">人</div>
        <div>
          <h2>创作阅读者</h2>
          <p>设备：{getMobileDeviceId()}</p>
        </div>
      </section>

      <section className="profile-grid">
        <article>
          <strong>同步</strong>
          <span>{paired ? "local-desktop-lan 已连接" : "从未同步"}</span>
        </article>
        <article>
          <strong>笔记</strong>
          <span>{snapshot.notes.length} 条</span>
        </article>
      </section>

      <section className="settings-card">
        <h2>扫码连接电脑</h2>
        <p className="subtle">电脑端开启同步服务后，可以直接扫码；如果相机不可用，也可以粘贴配对 URL 或二维码载荷。手机会自动尝试电脑端提供的所有备用地址。</p>
        <p className="subtle">当前有 {pendingDownloadCount} 本书尚未下载正文；同步只更新书架和进度，阅读前可在书架点“下载正文”。</p>
        <textarea value={pairingText} onChange={(event) => onPairingTextChange(event.target.value)} placeholder="粘贴电脑端配对 URL 或二维码载荷" />
        <div className="button-row">
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

      <section className="settings-card">
        <h2>WebDAV 设置</h2>
        <p className="subtle">同步目录固定为 .creation-reading-assistant/，会上传 manifest、records 和 books。AI Key 不参与同步。</p>
        <input value={webdav.endpoint} onChange={(event) => setWebdav((current) => ({ ...current, endpoint: event.target.value }))} placeholder="https://example.com/dav" />
        <input value={webdav.username} onChange={(event) => setWebdav((current) => ({ ...current, username: event.target.value }))} placeholder="用户名" />
        <input type="password" value={webdav.password} onChange={(event) => setWebdav((current) => ({ ...current, password: event.target.value }))} placeholder="密码或 token（仅本次使用）" />
        <div className="button-row">
          <button onClick={() => void testWebDav()}>测试连接</button>
          <button onClick={() => void uploadWebDav()}>上传</button>
          <button onClick={() => void downloadWebDav()}>下载</button>
        </div>
      </section>

      <section className="menu-list">
        {["标签管理", "分类管理", "书单管理", "我的阅读", "我的书评 / 笔记", "AI 设置", "数据导入导出"].map((item) => (
          <button key={item}>
            <span>{item}</span>
            <strong>›</strong>
          </button>
        ))}
      </section>
    </div>
  );
}

function calculateScrollProgress(element: HTMLElement): number {
  const scrollable = Math.max(1, element.scrollHeight - element.clientHeight);
  return Math.min(100, Math.max(0, (element.scrollTop / scrollable) * 100));
}

function scrollToPercent(element: HTMLElement, progressPercent: number): void {
  const scrollable = Math.max(0, element.scrollHeight - element.clientHeight);
  element.scrollTo({ top: (scrollable * Math.min(100, Math.max(0, progressPercent))) / 100, behavior: "smooth" });
}

function findCurrentChapter(document: MobileReaderDocument, root?: HTMLElement | null): MobileReaderDocument["toc"][number] | undefined {
  if (!root || !document.toc.length) return document.toc[0];
  const markerTop = root.scrollTop + 96;
  let current = document.toc[0];
  for (const item of document.toc) {
    const element = root.querySelector<HTMLElement>(`#${item.id}`);
    if (element && element.offsetTop <= markerTop) current = item;
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
  html: "<p>正在打开书籍……</p>",
  plainText: "",
  toc: [],
  wordCount: 0
});

function MobileReaderView({
  book,
  content,
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
  const [selectionText, setSelectionText] = useState("");
  const [showToc, setShowToc] = useState(false);
  const [readerDrawerTab, setReaderDrawerTab] = useState<ReaderDrawerTab>("toc");
  const [showSettings, setShowSettings] = useState(false);
  const [readerControlsVisible, setReaderControlsVisible] = useState(true);
  const [readerNotice, setReaderNotice] = useState("");
  const [lastSavedInspirationId, setLastSavedInspirationId] = useState("");
  const [noteDraft, setNoteDraft] = useState("");
  const [readerSearchQuery, setReaderSearchQuery] = useState("");
  const [activeReadingMs, setActiveReadingMs] = useState(0);
  const [document, setDocument] = useState<MobileReaderDocument>(() => emptyReaderDocument(book.title, book.format));
  const [currentProgress, setCurrentProgress] = useState(() => progressFor(snapshot, book.id));
  const [currentChapter, setCurrentChapter] = useState<MobileReaderDocument["toc"][number]>();
  const readerSearchResults = useMemo(() => createReaderSearchResults(document, readerSearchQuery), [document, readerSearchQuery]);

  useEffect(() => {
    let cancelled = false;
    void renderMobileDocument(book.format, content, book.title).then((nextDocument) => {
      if (!cancelled) setDocument(nextDocument);
    });
    return () => {
      cancelled = true;
    };
  }, [book.format, book.title, content]);

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
    const element = scrollRef.current;
    if (!element || !document.html) return;
    const scrollable = Math.max(0, element.scrollHeight - element.clientHeight);
    element.scrollTop = (scrollable * currentProgress) / 100;
    setCurrentChapter(findCurrentChapter(document, element));
  }, [document.html]);

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

  const captureSelection = () => {
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

  const jumpToChapter = (target: MobileReaderDocument["toc"][number] | undefined) => {
    const element = scrollRef.current;
    if (!element || !target) return;
    lastReaderActivityRef.current = Date.now();
    const chapterElement = element.querySelector<HTMLElement>(`#${target.id}`);
    if (chapterElement) {
      element.scrollTo({ top: Math.max(0, chapterElement.offsetTop - 72), behavior: "smooth" });
      setCurrentChapter(target);
      setReaderControlsVisible(false);
    }
  };

  const jumpToSearchResult = (result: ReaderSearchResult) => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const jumped = jumpToReaderSearchResult(element, readerSearchQuery, result.occurrenceIndex);
    if (!jumped) scrollToPercent(element, result.progressPercent);
    setCurrentProgress(result.progressPercent);
    setReaderControlsVisible(false);
    setShowToc(false);
    setReaderNotice(`已跳到搜索结果：${result.progressPercent.toFixed(1)}%`);
  };

  const openReaderDrawer = (tab: ReaderDrawerTab) => {
    setReaderDrawerTab(tab);
    setShowToc(true);
    setReaderControlsVisible(false);
  };

  const moveChapter = (direction: -1 | 1) => {
    const toc = document.toc;
    if (!toc.length) {
      const element = scrollRef.current;
      if (element) scrollToPercent(element, currentProgress + direction * 4);
      return;
    }
    const currentIndex = Math.max(0, toc.findIndex((item) => item.id === currentChapter?.id));
    const nextIndex = Math.min(toc.length - 1, Math.max(0, currentIndex + direction));
    jumpToChapter(toc[nextIndex]);
  };

  const turnReaderPage = (direction: -1 | 1) => {
    const element = scrollRef.current;
    if (!element) return;
    element.scrollTo({
      top: Math.min(element.scrollHeight, Math.max(0, element.scrollTop + direction * element.clientHeight * 0.86)),
      behavior: "smooth"
    });
    setReaderControlsVisible(false);
  };

  const handleReaderScroll = () => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const nextProgress = calculateScrollProgress(element);
    setCurrentProgress(nextProgress);
    setCurrentChapter(findCurrentChapter(document, element));
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
  const showSelectionToolbar = Boolean(selectionText) && !showToc && !showSettings;
  const savedBookReadingMs = snapshot.progress.find((item) => item.bookId === book.id)?.totalReadingTimeMs ?? 0;
  const sessionProgressDelta = Math.max(0, currentProgress - readerSessionStartProgressRef.current);
  const sessionWords = Math.round(document.wordCount * sessionProgressDelta / 100);
  const readerSpeed = activeReadingMs > 0 && sessionWords > 0 ? Math.round(sessionWords / Math.max(1, activeReadingMs / 60_000)) : 0;

  return (
    <main className={`reader-shell reader-bg-${settings.readerBackground} reader-mode-${settings.readerMode} reader-tap-${settings.tapZoneMode} ${readerControlsVisible ? "" : "reader-chrome-hidden"}`}>
      <div className="reader-dim-layer" style={{ opacity: Math.max(0, Math.min(0.58, (100 - settings.brightness) / 100)) }} aria-hidden="true" />
      <header className="reader-topbar">
        <button className="ghost-button" onClick={() => void closeReader()}>
          ← 返回书架
        </button>
        <div>
          <strong>{book.title}</strong>
          <p>
            {book.format.toUpperCase()} · {progressLabel} · 本次 {formatDuration(activeReadingMs)}
          </p>
        </div>
        <div className="reader-topbar-actions">
          <button className="ghost-button" onClick={() => openReaderDrawer("search")}>
            搜索
          </button>
          <button className="ghost-button" onClick={() => openReaderDrawer("toc")}>
            目录
          </button>
        </div>
      </header>

      {showToc && (
        <aside className="reader-toc-drawer">
          <div className="drawer-header">
            <h2>{readerDrawerTab === "toc" ? "目录" : readerDrawerTab === "search" ? "搜索" : readerDrawerTab === "bookmarks" ? "书签" : "笔记"}</h2>
            <button className="ghost-button" onClick={() => setShowToc(false)}>关闭</button>
          </div>
          <div className="reader-drawer-tabs">
            {([
              ["toc", "目录"],
              ["search", `搜索 ${readerSearchResults.length}`],
              ["bookmarks", `书签 ${bookBookmarks.length}`],
              ["notes", `笔记 ${bookNotes.length}`]
            ] as Array<[ReaderDrawerTab, string]>).map(([key, label]) => (
              <button key={key} className={readerDrawerTab === key ? "active" : ""} onClick={() => setReaderDrawerTab(key)}>
                {label}
              </button>
            ))}
          </div>
          {readerDrawerTab === "toc" && (
            document.toc.length ? document.toc.map((item) => (
              <a
                key={item.id}
                href={`#${item.id}`}
                onClick={(event) => {
                  event.preventDefault();
                  jumpToChapter(item);
                  setShowToc(false);
                }}
              >
                {item.title}
              </a>
            )) : <p>这本书暂未识别到目录。</p>
          )}
          {readerDrawerTab === "search" && (
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
          {readerDrawerTab === "bookmarks" && (
            <div className="reader-note-list">
              <button onClick={() => void addReaderBookmark()}>在当前位置添加书签</button>
              {bookBookmarks.length ? bookBookmarks.map((item) => (
                <article key={item.id} className="reader-note-item">
                  <strong>{item.title}</strong>
                  <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                  {item.excerpt && <p>{item.excerpt}</p>}
                  <button onClick={() => {
                    const element = scrollRef.current;
                    if (element) scrollToPercent(element, item.progressPercent ?? 0);
                    setShowToc(false);
                  }}>跳转</button>
                </article>
              )) : <p className="empty-hint">还没有书签。阅读时点底部“书签”即可保存当前位置。</p>}
            </div>
          )}
          {readerDrawerTab === "notes" && (
            <div className="reader-note-list">
              <textarea value={noteDraft} onChange={(event) => setNoteDraft(event.target.value)} placeholder="写一条阅读笔记；如果你选中了正文，也会一起保存为摘录。" />
              <button onClick={() => void addReaderNote()}>保存笔记</button>
              {bookNotes.length ? bookNotes.map((item) => (
                <article key={item.id} className="reader-note-item">
                  <strong>{item.title}</strong>
                  <span>{item.chapterTitle || "当前位置"} · {(item.progressPercent ?? 0).toFixed(1)}%</span>
                  {item.excerpt && <blockquote>{item.excerpt}</blockquote>}
                  <p>{item.body}</p>
                </article>
              )) : <p className="empty-hint">还没有笔记。可以先选中文字，再打开这里保存。</p>}
            </div>
          )}
        </aside>
      )}

      <div className="reader-progress-chip" aria-live="polite">
        <span>{chapterLabel}</span>
        <strong>{progressLabel}</strong>
      </div>

      <section
        ref={scrollRef}
        className="reader-scroll-container"
        onClick={handleReaderTap}
        onScroll={handleReaderScroll}
        onMouseUp={captureSelection}
        onTouchEnd={captureSelection}
      >
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
            <button onClick={() => void addReaderInspiration()}>记灵感</button>
            <button onClick={() => void addReaderNote()}>存笔记</button>
            <button onClick={searchSelectedText}>搜索</button>
            <button onClick={() => void copySelectedText()}>复制</button>
            <button className="ghost-button" onClick={clearSelectedText}>清除</button>
          </div>
        </section>
      )}

      <footer className="reader-bottom-sheet">
        <div className="reader-stat-row">
          <span>
            <strong>{formatDuration(savedBookReadingMs + activeReadingMs)}</strong>
            阅读时长
          </span>
          <span>
            <strong>{currentProgress.toFixed(2)}%</strong>
            阅读进度
          </span>
          <span>
            <strong>{readerSpeed ? `${readerSpeed}` : "—"} 字/分钟</strong>
            阅读速度
          </span>
          <span>
            <strong>{snapshot.inspirations.filter((item) => item.source?.bookId === book.id).length} 条</strong>
            灵感
          </span>
        </div>
        {settings.showProgressBar && (
          <input className="reader-progress-slider" type="range" min="0" max="100" step="0.1" value={currentProgress} onChange={(event) => void saveProgress(Number(event.target.value))} />
        )}
        <div className="reader-actions">
          <button onClick={() => void addReaderInspiration()}>
            ✦<span>记为灵感</span>
          </button>
          <button onClick={() => openReaderDrawer("toc")}>
            ☰<span>目录</span>
          </button>
          <button onClick={() => openReaderDrawer("search")}>
            ⌕<span>搜索</span>
          </button>
          <button onClick={() => void addReaderBookmark()}>
            ☆<span>书签</span>
          </button>
          <button onClick={() => openReaderDrawer("notes")}>
            ✎<span>笔记</span>
          </button>
          <button onClick={() => void saveProgress(currentProgress)}>
            ✓<span>保存进度</span>
          </button>
          <button onClick={() => onSettingsChange({ ...settings, readerBackground: settings.readerBackground === "night" ? "warm" : "night" })}>
            ☾<span>夜间</span>
          </button>
          <button onClick={() => setShowSettings(true)}>
            ⚙<span>设置</span>
          </button>
        </div>
      </footer>

      {showSettings && (
        <aside className="reader-settings-drawer">
          <div className="drawer-header">
            <h2>阅读设置</h2>
            <button className="ghost-button" onClick={() => setShowSettings(false)}>关闭</button>
          </div>
          <div className="reader-mode-grid" aria-label="阅读模式">
            <button className={settings.readerMode === "scroll" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, readerMode: "scroll" })}>
              滚动
            </button>
            <button className={settings.readerMode === "paged" ? "active" : ""} onClick={() => onSettingsChange({ ...settings, readerMode: "paged" })}>
              分页
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
          <p className="subtle">三分区：左 / 中 / 右；五分区会额外把中间上方作为后退、中间下方作为前进。分页模式按页翻动，滚动模式按章节跳转。</p>
        </aside>
      )}
    </main>
  );
}
