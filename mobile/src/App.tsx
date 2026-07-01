import { useEffect, useMemo, useState } from "react";
import { BarcodeFormat, BarcodeScanner } from "@capacitor-mlkit/barcode-scanning";
import type { LibraryBook } from "../../src/types/library";
import { renderMobileDocument } from "./reader/mobile-reader";
import {
  addMobileInspiration,
  addMobileReadingSession,
  createImportedMobileBook,
  getMobileDeviceId,
  loadMobileSnapshot,
  saveMobileBook,
  saveMobileReadingProgress,
  saveMobileSnapshot,
  saveSyncAccount,
  type MobileSnapshot
} from "./services/mobile-storage";
import { createSyncClient, pairWithFirstReachable, parsePairingCandidates, type PairingInput } from "./services/sync-client";
import { downloadWebDavSnapshot, testWebDavConnection, uploadWebDavSnapshot } from "./sync/webdav-sync";
import type { MobileBook, MobileReaderSettings, SyncAccount } from "./types/mobile";

type MainTab = "home" | "shelf" | "inspiration" | "stats" | "profile";

const defaultReaderSettings: MobileReaderSettings = {
  fontSize: 18,
  lineHeight: 1.85,
  pageMargin: 22,
  readerBackground: "warm"
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

function progressFor(snapshot: MobileSnapshot, bookId: string): number {
  return snapshot.progress.find((item) => item.bookId === bookId)?.progressPercent ?? 0;
}

function getContinueBooks(snapshot: MobileSnapshot): MobileBook[] {
  const recentBookIds = new Set(snapshot.progress.sort((a, b) => b.lastReadAt.localeCompare(a.lastReadAt)).map((item) => item.bookId));
  const byProgress = [...recentBookIds]
    .map((bookId) => snapshot.books.find((book) => book.id === bookId))
    .filter((book): book is MobileBook => Boolean(book));
  return [...byProgress, ...snapshot.books.filter((book) => !recentBookIds.has(book.id))].slice(0, 8);
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

  useEffect(() => {
    void loadMobileSnapshot().then(setSnapshot);
  }, []);

  const client = useMemo(() => (paired ? createSyncClient(paired) : undefined), [paired]);
  const stats = useMemo(() => {
    const totalReadingMs = snapshot.progress.reduce((sum, item) => sum + item.totalReadingTimeMs, 0);
    const readingDays = new Set(snapshot.sessions.map((session) => session.dateKey)).size;
    const completed = snapshot.progress.filter((item) => item.completionState === "completed").length;
    const words = snapshot.books.reduce((sum, book) => sum + Math.max(0, Math.round(book.size / 2)), 0);
    const speed = totalReadingMs > 0 ? Math.round(words / Math.max(1, totalReadingMs / 60_000)) : 0;
    return {
      totalReadingMs,
      readingDays,
      completed,
      words,
      speed
    };
  }, [snapshot]);

  const openBook = (book: MobileBook) => {
    const savedContent = localStorage.getItem(`creation-reading-assistant-mobile-book-content:${book.id}`);
    setReaderContent(savedContent ?? `${book.title}\n\n这本书来自同步或历史数据，正文文件将在下一次完整文件同步后补齐。`);
    setReaderBook(book);
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
    try {
      setMessage("正在打开摄像头扫码，请对准电脑端二维码。");
      const support = await BarcodeScanner.isSupported();
      if (!support.supported) throw new Error("当前设备不支持扫码。");
      const permission = await BarcodeScanner.checkPermissions();
      if (permission.camera !== "granted" && permission.camera !== "limited") {
        const requested = await BarcodeScanner.requestPermissions();
        if (requested.camera !== "granted" && requested.camera !== "limited") throw new Error("没有相机权限，无法扫码。");
      }
      try {
        const module = await BarcodeScanner.isGoogleBarcodeScannerModuleAvailable();
        if (!module.available) {
          await BarcodeScanner.installGoogleBarcodeScannerModule();
          setMessage("扫码模块正在安装，请稍等几秒后再点一次“扫码”。也可以先粘贴配对 URL。");
          return;
        }
      } catch {
        // 非 Android 或旧设备可能不支持模块检查，继续尝试 scan()。
      }
      const result = await BarcodeScanner.scan({ formats: [BarcodeFormat.QrCode] });
      const text = result.barcodes[0]?.rawValue || result.barcodes[0]?.displayValue || "";
      if (!text) throw new Error("没有识别到二维码内容。");
      setPairingText(text);
      setMessage("已识别二维码，正在连接电脑。");
      await connectLan(text);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      setMessage(`扫码失败：${detail}。你也可以复制电脑端“配对 URL”或“二维码载荷”到输入框后连接。`);
    }
  };

  const syncFromDesktop = async () => {
    if (!client) {
      setMessage("请先在“我的 / 同步”里粘贴电脑端配对 URL 或二维码载荷。");
      return;
    }
    try {
      const pull = await client.pull();
      const next: MobileSnapshot = {
        ...snapshot,
        inspirations: pull.inspirations.map((item) => item.payload),
        books: pull.books.map((item) => item.payload),
        progress: pull.progress.map((item) => item.payload),
        sessions: pull.sessions.map((item) => item.payload),
        updatedAt: new Date().toISOString()
      };
      await saveMobileSnapshot(next);
      setSnapshot(next);
      setMessage(`同步完成：${pull.inspirations.length} 条灵感，${pull.books.length} 本书。`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : String(error));
    }
  };

  const importFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    let next = snapshot;
    for (const file of Array.from(files)) {
      const content = await file.text();
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
      <ReaderView
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
            onOpenBook={openBook}
            onAddInspiration={() => void addQuickInspiration()}
            onGo={(nextTab) => setTab(nextTab)}
          />
        )}
        {tab === "shelf" && <ShelfPage snapshot={snapshot} onOpenBook={openBook} onImport={(files) => void importFiles(files)} />}
        {tab === "inspiration" && <InspirationPage snapshot={snapshot} onSnapshotChange={setSnapshot} />}
        {tab === "stats" && <StatsPage snapshot={snapshot} stats={stats} />}
        {tab === "profile" && (
          <ProfilePage
            snapshot={snapshot}
            pairingText={pairingText}
            paired={Boolean(paired)}
            onPairingTextChange={setPairingText}
            onConnectLan={() => void connectLan()}
            onScanQr={() => void scanPairingQrCode()}
            onSyncDesktop={() => void syncFromDesktop()}
            onSnapshotChange={setSnapshot}
            onMessage={setMessage}
          />
        )}
      </section>

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

function ShelfPage({ snapshot, onOpenBook, onImport }: { snapshot: MobileSnapshot; onOpenBook: (book: MobileBook) => void; onImport: (files: FileList | null) => void }) {
  const [query, setQuery] = useState("");
  const filtered = snapshot.books.filter((book) => `${book.title} ${book.author ?? ""} ${book.importLabel ?? ""}`.toLowerCase().includes(query.toLowerCase()));
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

      <section className="book-grid">
        {filtered.map((book) => (
          <article key={book.id} className="book-tile" onClick={() => onOpenBook(book)}>
            <div className="book-cover">{book.title.slice(0, 4)}</div>
            <h3>{book.title}</h3>
            <p>{book.author || "作者未知"}</p>
            <small>{progressFor(snapshot, book.id).toFixed(2)}%</small>
            {book.duplicateIndex && book.duplicateIndex > 1 ? <em>{book.importLabel}</em> : null}
            <button className="tile-more" onClick={(event) => event.stopPropagation()} aria-label="更多">
              …
            </button>
          </article>
        ))}
      </section>

      {!filtered.length && <p className="empty-hint">书架还没有匹配结果。你可以点右上角导入本地书籍。</p>}
      <p className="center-foot">共 {snapshot.books.length} 本书籍</p>
    </div>
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
  snapshot,
  stats
}: {
  snapshot: MobileSnapshot;
  stats: { totalReadingMs: number; readingDays: number; completed: number; words: number; speed: number };
}) {
  const [range, setRange] = useState<"日" | "周" | "月" | "年" | "总">("月");
  const statItems = [
    ["阅读时间", formatDuration(stats.totalReadingMs)],
    ["阅读天数", `${stats.readingDays} 天`],
    ["累计读过", `${snapshot.books.length} 本`],
    ["读完书籍", `${stats.completed} 本`],
    ["在读书籍", `${snapshot.progress.filter((item) => item.completionState === "reading").length} 本`],
    ["记录灵感", `${snapshot.inspirations.length} 条`],
    ["阅读字数", `${stats.words} 字`],
    ["阅读速度", `${stats.speed} 字/分钟`]
  ];
  return (
    <div className="screen-stack">
      <header className="mobile-header row-header">
        <div>
          <p className="mini-label">Reading stats</p>
          <h1>阅读统计</h1>
        </div>
        <button className="ghost-button">筛选</button>
      </header>

      <div className="range-tabs">
        {(["日", "周", "月", "年", "总"] as const).map((item) => (
          <button key={item} className={range === item ? "active" : ""} onClick={() => setRange(item)}>
            {item}
          </button>
        ))}
      </div>

      <section className="stats-card">
        <h2>2026年6月 · {range}</h2>
        <div className="stats-grid">
          {statItems.map(([label, value]) => (
            <article key={label}>
              <strong>{value}</strong>
              <span>{label}</span>
            </article>
          ))}
        </div>
      </section>

      <section className="trend-card">
        <div className="section-heading">
          <h2>阅读时间趋势</h2>
          <span>▥</span>
        </div>
        <p className="empty-hint">暂无趋势图数据。后续会按阅读 session 绘制日/周/月曲线。</p>
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
        <textarea value={pairingText} onChange={(event) => onPairingTextChange(event.target.value)} placeholder="粘贴电脑端配对 URL 或二维码载荷" />
        <div className="button-row">
          <button onClick={onScanQr}>扫码</button>
          <button onClick={onConnectLan}>连接电脑</button>
          <button disabled={!paired} onClick={onSyncDesktop}>
            立即同步
          </button>
        </div>
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

function ReaderView({
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
  const [selectionText, setSelectionText] = useState("");
  const [showToc, setShowToc] = useState(false);
  const [readerNotice, setReaderNotice] = useState("");
  const [lastSavedInspirationId, setLastSavedInspirationId] = useState("");
  const document = useMemo(() => renderMobileDocument(book.format, content, book.title), [book.format, book.title, content]);
  const currentProgress = progressFor(snapshot, book.id);

  const captureSelection = () => {
    const text = window.getSelection()?.toString().trim() ?? "";
    setSelectionText(text.slice(0, 800));
  };

  const saveProgress = async (progressPercent: number) => {
    const next = await saveMobileReadingProgress(snapshot, book, progressPercent);
    onSnapshotChange(next);
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

  const closeReader = async () => {
    const next = await addMobileReadingSession(snapshot, book as LibraryBook, 30_000, currentProgress);
    onSnapshotChange(next);
    onBack();
  };

  return (
    <main className={`reader-shell reader-bg-${settings.readerBackground}`}>
      <header className="reader-topbar">
        <button className="ghost-button" onClick={() => void closeReader()}>
          ← 返回书架
        </button>
        <div>
          <strong>{book.title}</strong>
          <p>
            {book.format.toUpperCase()} · {currentProgress.toFixed(2)}%
          </p>
        </div>
        <button className="ghost-button" onClick={() => setShowToc((value) => !value)}>
          目录
        </button>
      </header>

      {showToc && (
        <aside className="toc-drawer">
          <h2>目录</h2>
          {document.toc.length ? document.toc.map((item) => <a key={item.id} href={`#${item.id}`}>{item.title}</a>) : <p>这本书暂未识别到目录。</p>}
        </aside>
      )}

      <article
        className="reader-content"
        style={{
          fontSize: `${settings.fontSize}px`,
          lineHeight: settings.lineHeight,
          padding: `${settings.pageMargin}px`
        }}
        onMouseUp={captureSelection}
        onTouchEnd={captureSelection}
        dangerouslySetInnerHTML={{ __html: document.html }}
      />

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

      <footer className="reader-toolbar">
        <button onClick={() => void addReaderInspiration()}>记为灵感</button>
        <button onClick={() => void saveProgress(Math.min(100, currentProgress + 2))}>保存进度</button>
        <label>
          字号
          <input
            type="range"
            min="15"
            max="28"
            value={settings.fontSize}
            onChange={(event) => onSettingsChange({ ...settings, fontSize: Number(event.target.value) })}
          />
        </label>
        <label>
          行距
          <input
            type="range"
            min="1.4"
            max="2.4"
            step="0.05"
            value={settings.lineHeight}
            onChange={(event) => onSettingsChange({ ...settings, lineHeight: Number(event.target.value) })}
          />
        </label>
        <label>
          背景
          <select value={settings.readerBackground} onChange={(event) => onSettingsChange({ ...settings, readerBackground: event.target.value as MobileReaderSettings["readerBackground"] })}>
            <option value="white">白纸</option>
            <option value="warm">暖纸</option>
            <option value="green">护眼</option>
            <option value="night">夜间</option>
          </select>
        </label>
      </footer>
    </main>
  );
}
