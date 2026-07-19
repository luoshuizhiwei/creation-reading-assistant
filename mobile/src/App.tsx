import { lazy, Suspense, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  Home,
  BookOpen,
  Sparkles,
  BarChart3,
  User
} from "lucide-react";
import { HomePage } from "./features/home/HomePage";
import type { ProfileSubPage } from "./features/profile/ProfilePage";
import { ConfirmDialog } from "./components/ConfirmDialog";
import { QrScanOverlay } from "./components/QrScanOverlay";
import { emptySnapshot } from "./utils/mobile-helpers";
import { idleReaderState } from "./features/reader/reader-model";
import { saveReaderEngineVersionForBook } from "./features/reader/engine-v2/engine-version";
import { GlobalSearchOverlay } from "./features/search/GlobalSearchOverlay";
import { OnboardingOverlay, hasCompletedOnboarding } from "./features/onboarding/OnboardingOverlay";
import {
  addMobileInspiration,
  loadMobileSnapshot,
  recoverInterruptedMobileImports,
  type MobileSnapshot
} from "./services/mobile-storage";
import { buildMobileStatsSummary } from "./services/mobile-stats";
import { useAppToast } from "./hooks/useAppToast";
import { useAppTheme } from "./hooks/useAppTheme";
import { useMobileReaderBook } from "./hooks/useMobileReaderBook";
import { useAppBackHandler } from "./hooks/useAppBackHandler";
import { useMobileSync } from "./hooks/useMobileSync";
import { useMobileImport } from "./hooks/useMobileImport";

const ShelfPage = lazy(() => import("./features/shelf/ShelfPage").then((module) => ({ default: module.ShelfPage })));
const InspirationPage = lazy(() => import("./features/inspiration/InspirationPage").then((module) => ({ default: module.InspirationPage })));
const StatsPage = lazy(() => import("./features/stats/StatsPage").then((module) => ({ default: module.StatsPage })));
const ProfilePage = lazy(() => import("./features/profile/ProfilePage").then((module) => ({ default: module.ProfilePage })));
const MobileReaderView = lazy(() => import("./features/reader/MobileReaderView").then((module) => ({ default: module.MobileReaderView })));
const MobileReaderV2View = lazy(() => import("./features/reader/MobileReaderV2View").then((module) => ({ default: module.MobileReaderV2View })));

type MainTab = "home" | "shelf" | "inspiration" | "stats" | "profile";

export function App() {
  const [tab, setTab] = useState<MainTab>("home");
  const [activeProfilePage, setActiveProfilePage] = useState<ProfileSubPage>();
  const [snapshot, setSnapshot] = useState<MobileSnapshot>(emptySnapshot);
  const [shelfSearchFocusToken, setShelfSearchFocusToken] = useState(0);
  const [showGlobalSearch, setShowGlobalSearch] = useState(false);
  const [focusedInspirationId, setFocusedInspirationId] = useState<string>();
  const [focusedNoteId, setFocusedNoteId] = useState<string>();
  const [readerJumpTarget, setReaderJumpTarget] = useState<{ bookId: string; progressPercent: number }>();
  const [showOnboarding, setShowOnboarding] = useState(() => !hasCompletedOnboarding());
  const [confirmDialog, setConfirmDialog] = useState<{
    title: string;
    message: string;
    onConfirm: () => void;
  } | null>(null);
  const [shelfInitialDetailBookId, setShelfInitialDetailBookId] = useState<string>();

  // appBackStateRef 持有最新的 UI 状态，供硬件返回键监听器安全读取（避免闭包陈旧）
  // readerBookRef / showQrScannerRef 用于跨 hook 同步子 hook 持有的状态到 appBackStateRef
  const appBackStateRef = useRef({
    activeProfilePage,
    confirmDialog,
    readerBook: undefined as unknown,
    showGlobalSearch: false,
    showQrScanner: false,
    tab
  });
  const readerBookRef = useRef<unknown>(undefined);
  const showQrScannerRef = useRef(false);

  const { message, setMessage } = useAppToast();
  const { appTheme, setAppTheme } = useAppTheme(setMessage);
  const stats = useMemo(() => buildMobileStatsSummary(snapshot, "total"), [snapshot]);
  const todayStats = useMemo(() => buildMobileStatsSummary(snapshot, "day"), [snapshot]);

  const {
    readerBook,
    setReaderBook,
    readerState,
    setReaderState,
    readerSettings,
    setReaderSettings,
    openBook,
    closeMobileReader,
    readMobileBookContent,
    readerContentCacheRef
  } = useMobileReaderBook({ snapshot, setSnapshot, setMessage });
  readerBookRef.current = readerBook;

  const {
    paired,
    pairingText,
    setPairingText,
    syncLogs,
    downloadingBookId,
    showQrScanner,
    setShowQrScanner,
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
  } = useMobileSync({ snapshot, setSnapshot, setMessage, readMobileBookContent, readerContentCacheRef });
  showQrScannerRef.current = showQrScanner;

  const {
    importFiles,
    importTasks,
    importHistory,
    retryImport,
    repairBookFile,
    clearImportHistory,
    dismissImportTask
  } = useMobileImport({ snapshot, setSnapshot, setMessage, readerContentCacheRef });

  const goTab = (nextTab: MainTab) => {
    setTab(nextTab);
    if (nextTab !== "profile") setActiveProfilePage(undefined);
    if (nextTab !== "inspiration") setFocusedInspirationId(undefined);
    if (nextTab !== "profile") setFocusedNoteId(undefined);
  };

  const openBookNormally = (book: Parameters<typeof openBook>[0]) => {
    setReaderJumpTarget(undefined);
    openBook(book);
  };

  useAppBackHandler({
    appBackStateRef,
    closeMobileReader,
    onProfileBack: () => setActiveProfilePage(undefined),
    onGoHome: () => goTab("home"),
    onConfirmClose: () => setConfirmDialog(null),
    onQrClose: () => setShowQrScanner(false),
    onGlobalSearchClose: () => setShowGlobalSearch(false),
    setMessage
  });

  useEffect(() => {
    void loadMobileSnapshot().then(async (loaded) => {
      const recoveredFiles = await recoverInterruptedMobileImports(loaded).catch(() => 0);
      setSnapshot(loaded);
      if (recoveredFiles > 0) setMessage(`已清理 ${recoveredFiles} 个中断导入留下的临时文件。`);
    });
  }, []);

  // 每次渲染后把最新 UI 状态写入 ref，供硬件返回键监听器读取
  useEffect(() => {
    appBackStateRef.current = {
      activeProfilePage,
      confirmDialog,
      readerBook: readerBookRef.current,
      showGlobalSearch,
      showQrScanner: showQrScannerRef.current,
      tab
    };
  });

  const openShelfSearch = () => {
    goTab("shelf");
    setShelfSearchFocusToken((current) => current + 1);
    setMessage("已打开书架搜索，可以直接输入书名、作者或导入标签。");
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
    const useV2Reader = readerState.bookId === readerBook.id && readerState.engineVersion === "v2" && readerBook.format !== "epub";
    return (
      <Suspense fallback={<div className="reader-loading-state" role="status">正在准备阅读器…</div>}>
        {useV2Reader ? (
          <MobileReaderV2View
            key={`${readerBook.id}:v2`}
            book={readerBook}
            content={readerState.visibleContent}
            loading={readerState.phase === "opening"}
            loadError={readerState.phase === "error" ? readerState.errorMessage : undefined}
            snapshot={snapshot}
            settings={readerSettings}
            onSettingsChange={setReaderSettings}
            onSnapshotChange={setSnapshot}
            onBack={() => {
              setReaderJumpTarget(undefined);
              closeMobileReader();
            }}
            onReload={() => {
              readerContentCacheRef.current.delete(readerBook.id);
              openBook(readerBook);
            }}
            onSwitchToLegacy={() => {
              saveReaderEngineVersionForBook(readerBook.id, "legacy");
              readerContentCacheRef.current.delete(readerBook.id);
              openBook(readerBook);
              setMessage("已切回稳定阅读内核。");
            }}
            onOpenInspiration={(inspirationId) => {
              setReaderBook(undefined);
              setReaderState(idleReaderState);
              setReaderJumpTarget(undefined);
              setFocusedInspirationId(inspirationId);
              goTab("inspiration");
              setMessage("已打开刚保存的阅读灵感。");
            }}
            onMessage={setMessage}
            initialProgressPercent={readerJumpTarget?.bookId === readerBook.id ? readerJumpTarget.progressPercent : undefined}
          />
        ) : (
        <MobileReaderView
          key={`${readerBook.id}:legacy`}
          book={readerBook}
          content={readerState.bookId === readerBook.id ? readerState.visibleContent : ""}
          loading={readerState.bookId === readerBook.id && readerState.phase === "opening"}
          loadError={readerState.bookId === readerBook.id && readerState.phase === "error" ? readerState.errorMessage : undefined}
          snapshot={snapshot}
          settings={readerSettings}
          onSettingsChange={setReaderSettings}
          onSnapshotChange={setSnapshot}
          onBack={() => {
            setReaderJumpTarget(undefined);
            closeMobileReader();
          }}
          onReload={() => {
            readerContentCacheRef.current.delete(readerBook.id);
            openBook(readerBook);
          }}
          onOpenInspiration={(inspirationId) => {
            setReaderBook(undefined);
            setReaderState(idleReaderState);
            setReaderJumpTarget(undefined);
            setFocusedInspirationId(inspirationId);
            goTab("inspiration");
            setMessage("已打开刚保存的阅读灵感。");
          }}
          onMessage={setMessage}
          initialProgressPercent={readerJumpTarget?.bookId === readerBook.id ? readerJumpTarget.progressPercent : undefined}
          onConfirm={setConfirmDialog}
        />
        )}
        {confirmDialog && (
          <ConfirmDialog
            title={confirmDialog.title}
            message={confirmDialog.message}
            onConfirm={confirmDialog.onConfirm}
            onCancel={() => setConfirmDialog(null)}
          />
        )}
      </Suspense>
    );
  }

  return (
    <main className="mobile-shell">
      <section className="phone-page">
        {message && <div className="toast-line" role="status" aria-live="polite">{message}</div>}
        <Suspense fallback={<div className="reader-loading-state" role="status">正在打开页面…</div>}>
        {tab === "home" && (
          <HomePage
            snapshot={snapshot}
            stats={stats}
            todayReadingMs={todayStats.totalReadingMs}
            onOpenBook={(book) => void openBookNormally(book)}
            onShowBookDetail={(bookId) => {
              setShelfInitialDetailBookId(bookId);
              goTab("shelf");
            }}
            onGo={goTab}
            onOpenGlobalSearch={() => setShowGlobalSearch(true)}
            onOpenInspiration={(inspirationId) => {
              setFocusedInspirationId(inspirationId);
              goTab("inspiration");
            }}
            onSnapshotChange={setSnapshot}
            onConfirm={setConfirmDialog}
          />
        )}
        {tab === "shelf" && (
          <ShelfPage
            snapshot={snapshot}
            downloadingBookId={downloadingBookId}
            searchFocusToken={shelfSearchFocusToken}
            initialDetailBookId={shelfInitialDetailBookId}
            onOpenBook={(book) => void openBookNormally(book)}
            onImport={(files) => void importFiles(files)}
            onDownloadBook={(book) => void downloadBookToMobile(book)}
            onCancelDownload={cancelBookDownload}
            onSnapshotChange={setSnapshot}
            onMessage={setMessage}
            onConfirm={setConfirmDialog}
            importTasks={importTasks}
            importHistory={importHistory}
            onRetryImport={(taskId) => void retryImport(taskId)}
            onRepairBook={(bookId, file) => void repairBookFile(bookId, file)}
            onClearImportHistory={clearImportHistory}
            onDismissImportTask={dismissImportTask}
          />
        )}
        {tab === "inspiration" && (
          <InspirationPage
            snapshot={snapshot}
            onSnapshotChange={setSnapshot}
            onConfirm={setConfirmDialog}
            onMessage={setMessage}
            onOpenBook={(book) => void openBookNormally(book)}
            initialSelectedId={focusedInspirationId}
          />
        )}
        {tab === "stats" && <StatsPage snapshot={snapshot} onGoToShelf={() => goTab("shelf")} />}
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
            lastSyncResult={lastSyncResult}
            syncing={syncing}
            onRetryFailedUploads={() => void retryFailedUploads()}
            onSnapshotChange={setSnapshot}
            onMessage={setMessage}
            onConfirm={setConfirmDialog}
            activePage={activeProfilePage}
            onSetActivePage={setActiveProfilePage}
            appTheme={appTheme}
            onAppThemeChange={setAppTheme}
            onOpenBook={(book) => void openBookNormally(book)}
            focusedNoteId={focusedNoteId}
            readerSettings={readerSettings}
            onReaderSettingsChange={setReaderSettings}
          />
        )}
        </Suspense>
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

      {showGlobalSearch && (
        <GlobalSearchOverlay
          snapshot={snapshot}
          onClose={() => setShowGlobalSearch(false)}
          onOpenBook={(book) => {
            setShowGlobalSearch(false);
            void openBookNormally(book);
          }}
          onOpenInspiration={(inspirationId) => {
            setShowGlobalSearch(false);
            setFocusedInspirationId(inspirationId);
            goTab("inspiration");
            setMessage("已打开对应灵感。");
          }}
          onOpenNote={(noteId) => {
            setShowGlobalSearch(false);
            setFocusedNoteId(noteId);
            setTab("profile");
            setActiveProfilePage("notes");
            setMessage("已打开对应笔记。");
          }}
          onOpenHighlight={(book, highlight) => {
            setShowGlobalSearch(false);
            setReaderJumpTarget({ bookId: book.id, progressPercent: highlight.progressPercent ?? 0 });
            void openBook(book);
          }}
        />
      )}

      {showOnboarding && (
        <OnboardingOverlay onClose={() => setShowOnboarding(false)} />
      )}
    </main>
  );
}
