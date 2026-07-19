import { useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import React from "react";
import { App as CapacitorApp } from "@capacitor/app";
import {
  Search,
  MoreHorizontal,
  MessageSquare,
  Info,
  Menu,
  BarChart3,
  Sparkles,
  Sun,
  Headphones,
  Bot
} from "lucide-react";
import type { LibraryBook, ReadingLocation } from "../../../../src/types/library";
import {
  type ReaderDrawerTab,
  type ReaderPanel,
  type ReaderSheet
} from "./reader-model";
import { createReaderSearchResults } from "./reader-search";
import type { MobileReaderDocument } from "../../reader/mobile-reader";
import {
  addMobileReadingSession,
  loadMobileSnapshot,
  type MobileSnapshot
} from "../../services/mobile-storage";
import type { MobileBook, MobileReaderSettings } from "../../types/mobile";
import { formatDuration } from "../../utils/format";
import { bookReadingTimeMs, estimateBookReadingSpeed, progressFor } from "../shelf/book-progress";
import { useReaderSession } from "./hooks/useReaderSession";
import { useReaderWakeLock } from "./hooks/useReaderWakeLock";
import { useReaderDocument } from "./hooks/useReaderDocument";
import { useReaderNavigation } from "./hooks/useReaderNavigation";
import { useReaderGestures } from "./hooks/useReaderGestures";
import { useReaderAnnotations, HIGHLIGHT_COLOR_OPTIONS } from "./hooks/useReaderAnnotations";
import { readerLocationExtrasFromViewport } from "./reader-navigation";
import { ReaderPanel as ReaderPanelView } from "./components/ReaderPanel";
import { ReaderSheets } from "./components/ReaderSheets";
import { ReaderInspirationSheet } from "./components/ReaderInspirationSheet";
import { TTSPlayerBar } from "./components/TTSPlayerBar";
import { ReaderAIAssistSheet } from "./components/ReaderAIAssistSheet";
import { ReaderAIExplainSheet } from "./components/ReaderAIExplainSheet";
import { EpubReaderView, type EpubReaderHandle } from "./components/EpubReaderView";
import { EpubNavigatorAdapter, type EpubLocationInfo } from "./epub-navigator";
import { normalizeReaderLocator } from "./engine-v2/locator";
import { readerFormatFromBook, type ReaderLocator } from "./engine-v2/types";
import { READER_PROGRESS_SAVE_DEBOUNCE_MS } from "./reader-constants";
import { loadMobileAISettings } from "../../services/mobile-ai";
import {
  readerCharOffsetFromViewport,
  readerTocIndexFromCharOffset,
  scrollReaderToParagraph,
  READER_PARAGRAPH_SELECTOR
} from "./reader-navigation";

export function MobileReaderView({
  book,
  content,
  loading,
  loadError,
  snapshot,
  settings,
  onSettingsChange,
  onSnapshotChange,
  onBack,
  onReload,
  onOpenInspiration,
  onConfirm,
  onMessage,
  initialProgressPercent
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
  onReload: () => void;
  onOpenInspiration: (inspirationId: string) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
  onMessage: (message: string) => void;
  initialProgressPercent?: number;
}) {
  const scrollRef = useRef<HTMLElement>(null);
  const readerSessionStartProgressRef = useRef(progressFor(snapshot, book.id));
  // 持有最新的 closeReader，供硬件返回键事件回调调用，避免闭包陈旧导致进度丢失
  const closeReaderRef = useRef<() => void>(() => {});
  // EPUB 页面只依赖统一 Navigator；epub.js 的具体句柄留在适配层内部。
  const epubNavigator = useMemo(() => new EpubNavigatorAdapter(book.id), [book.id]);
  const bindEpubView = useCallback((handle: EpubReaderHandle | null) => {
    epubNavigator.bind(handle);
  }, [epubNavigator]);
  const epubSaveTimerRef = useRef<number>();
  const readerClosingRef = useRef(false);
  const readerSessionSavedRef = useRef(false);
  const lastBackgroundSaveAtRef = useRef(0);
  const initialProgressAppliedRef = useRef("");
  const [epubReady, setEpubReady] = useState(false);
  const [epubRenderError, setEpubRenderError] = useState("");

  // 跨 hook 共享状态
  const [selectionText, setSelectionText] = useState("");
  const [readerPanel, setReaderPanel] = useState<ReaderPanel | null>(null);
  const [readerDrawerTab, setReaderDrawerTab] = useState<ReaderDrawerTab>("toc");
  const [readerControlsVisible, setReaderControlsVisible] = useState(false);
  const [readerNotice, setReaderNotice] = useState("");
  const [lastSavedInspirationId, setLastSavedInspirationId] = useState("");
  const [noteDraft, setNoteDraft] = useState("");
  const [readerSearchQuery, setReaderSearchQuery] = useState("");
  const [readerOverflowVisible, setReaderOverflowVisible] = useState(false);
  const [readerSheet, setReaderSheet] = useState<ReaderSheet>(null);
  const [showHighlightColors, setShowHighlightColors] = useState(false);
  const [showTTSPlayer, setShowTTSPlayer] = useState(false);
  const [currentProgress, setCurrentProgress] = useState(() => progressFor(snapshot, book.id));
  const currentProgressRef = useRef(currentProgress);
  const [readerChapterIndex, setReaderChapterIndex] = useState(0);
  const [currentChapter, setCurrentChapter] = useState<MobileReaderDocument["toc"][number]>();
  const [aiConfigured, setAiConfigured] = useState(false);
  const [recentChapterIds, setRecentChapterIds] = useState<string[]>([]);
  const [rhythmNotice, setRhythmNotice] = useState("");
  const [ttsStartCharOffset, setTtsStartCharOffset] = useState(0);
  const [ttsCharOffset, setTtsCharOffset] = useState(0);
  const [ttsParagraphIndex, setTtsParagraphIndex] = useState(-1);
  const rhythmTimerRef = useRef<number>();
  const rhythmLastNotifiedRef = useRef(0);
  const ttsHighlightElRef = useRef<HTMLElement | null>(null);

  // 沉浸模式计时器：按用户设置在无交互后自动隐藏；0 表示保持显示。
  const immersiveTimerRef = useRef<number>();
  const clearImmersiveTimer = useCallback(() => {
    if (immersiveTimerRef.current) {
      window.clearTimeout(immersiveTimerRef.current);
      immersiveTimerRef.current = undefined;
    }
  }, []);
  const scheduleImmersiveHide = useCallback(() => {
    const delaySeconds = Math.min(10, Math.max(0, settings.autoHideControlsSeconds ?? 4));
    if (!settings.immersiveMode || delaySeconds === 0) return;
    clearImmersiveTimer();
    immersiveTimerRef.current = window.setTimeout(() => {
      setReaderControlsVisible(false);
    }, delaySeconds * 1000);
  }, [settings.immersiveMode, settings.autoHideControlsSeconds, clearImmersiveTimer]);
  const resetImmersiveTimer = useCallback(() => {
    if (!settings.immersiveMode || !readerControlsVisible) {
      clearImmersiveTimer();
      return;
    }
    scheduleImmersiveHide();
  }, [settings.immersiveMode, readerControlsVisible, clearImmersiveTimer, scheduleImmersiveHide]);
  const toggleReaderControls = useCallback(() => {
    setReaderControlsVisible((prev) => {
      const next = !prev;
      if (next) {
        scheduleImmersiveHide();
      } else {
        clearImmersiveTimer();
      }
      return next;
    });
  }, [scheduleImmersiveHide, clearImmersiveTimer]);

  // 组件卸载或切书时清理沉浸计时器
  useEffect(() => {
    return () => clearImmersiveTimer();
  }, [clearImmersiveTimer]);
  useEffect(() => {
    clearImmersiveTimer();
  }, [book.id, clearImmersiveTimer]);

  // hooks 实例化
  const { readerSessionStartRef, lastReaderActivityRef, activeReadingMs, setActiveReadingMs } = useReaderSession({ bookId: book.id });
  useReaderWakeLock({ keepAwake: settings.keepAwake, onMessage });

  // 护眼提醒：每达到设定间隔提示一次休息
  const eyeCareLastNotifiedRef = useRef(0);
  useEffect(() => {
    const intervalMin = settings.eyeCareReminderMinutes ?? 30;
    if (intervalMin <= 0) return;
    const threshold = intervalMin * 60_000;
    const nextNotifiedAt = eyeCareLastNotifiedRef.current + threshold;
    if (activeReadingMs >= nextNotifiedAt) {
      eyeCareLastNotifiedRef.current = Math.floor(activeReadingMs / threshold) * threshold;
      onMessage(`已连续阅读 ${intervalMin} 分钟，建议休息一下眼睛。`);
    }
  }, [activeReadingMs, settings.eyeCareReminderMinutes, onMessage]);
  // 切换书籍时重置护眼提醒计数
  useEffect(() => {
    eyeCareLastNotifiedRef.current = 0;
  }, [book.id]);

  // AI 服务配置状态（决定选中工具栏是否展示“AI 解读”按钮）
  useEffect(() => {
    const aiSettings = loadMobileAISettings();
    setAiConfigured(Boolean(aiSettings.baseUrl.trim() && aiSettings.model.trim() && aiSettings.hasApiKey));
  }, []);

  // 目录最近浏览记录（按书籍隔离）
  useEffect(() => {
    try {
      const raw = localStorage.getItem(`creation-reading-assistant-mobile-reader-recent-chapters-${book.id}`);
      setRecentChapterIds(raw ? (JSON.parse(raw) as string[]) : []);
    } catch {
      setRecentChapterIds([]);
    }
  }, [book.id]);

  // 阅读节奏提示
  const rhythmMinutes = settings.readingRhythmReminderEnabled ? (settings.readingRhythmReminderMinutes ?? 30) : 0;
  useEffect(() => {
    if (rhythmMinutes <= 0) return;
    const threshold = rhythmMinutes * 60_000;
    const nextAt = rhythmLastNotifiedRef.current + threshold;
    if (activeReadingMs >= nextAt) {
      rhythmLastNotifiedRef.current = Math.floor(activeReadingMs / threshold) * threshold;
      setRhythmNotice(`已读 ${rhythmMinutes} 分钟，注意休息`);
      if (rhythmTimerRef.current) window.clearTimeout(rhythmTimerRef.current);
      rhythmTimerRef.current = window.setTimeout(() => setRhythmNotice(""), 3000);
    }
  }, [activeReadingMs, rhythmMinutes]);
  useEffect(() => {
    if (rhythmMinutes <= 0) setRhythmNotice("");
  }, [rhythmMinutes]);
  useEffect(() => {
    rhythmLastNotifiedRef.current = 0;
    setRhythmNotice("");
  }, [book.id]);
  useEffect(() => {
    return () => {
      if (rhythmTimerRef.current) window.clearTimeout(rhythmTimerRef.current);
    };
  }, []);

  const savedReadingProgress = snapshot.progress.find((item) => item.bookId === book.id);
  const savedReadingLocation = savedReadingProgress?.currentLocation;
  // epubjs 会在阅读模式切换时重新挂载。优先使用本次阅读中最新的 CFI，
  // 避免回退到尚未落盘的旧进度，造成切换分页/滚动后章节瞬间跳回。
  const initialEpubCfi = epubNavigator.getLegacyLocationInfo()?.cfi ?? savedReadingLocation?.epub?.cfi;

  const {
    document,
    setDocument,
    documentRendering,
    documentError,
    readerTimeoutError,
    showLoadingHint,
    loadingHintLong
  } = useReaderDocument({
    book,
    content,
    loading,
    readerChapterIndex,
    readerMode: settings.readerMode,
    sessionStartProgress: readerSessionStartProgressRef.current,
    initialLocation: savedReadingLocation,
    setReaderChapterIndex,
    setCurrentChapter
  });

  const readerSearchResults = useMemo(() => createReaderSearchResults(document, readerSearchQuery), [document, readerSearchQuery]);

  const {
    chapterTurnPending,
    readerViewportReady,
    pageTurnDirection,
    triggerReaderPageTurn,
    swipeDirection,
    setSwipeDirection,
    currentPageInfo,
    setCurrentPageInfo,
    saveProgress,
    flushProgress,
    clearProgressSaveTimer,
    jumpToReaderProgress,
    jumpToChapter,
    jumpToSearchResult,
    openReaderDrawer,
    moveChapter,
    turnReaderPage,
    handleReaderScroll,
    runBackwardAction,
    runForwardAction,
    finishReaderJump
  } = useReaderNavigation({
    book,
    document,
    settings,
    onSnapshotChange,
    scrollRef,
    readerChapterIndex,
    setReaderChapterIndex,
    currentChapter,
    setCurrentChapter,
    currentProgress,
    setCurrentProgress,
    readerPanel,
    setReaderPanel,
    readerSheet,
    setReaderSheet,
    setReaderControlsVisible,
    setReaderNotice,
    setReaderDrawerTab,
    lastReaderActivityRef,
    initialLocation: savedReadingLocation,
    readerSearchQuery,
    setReaderSearchQuery
  });
  const flushProgressRef = useRef(flushProgress);
  flushProgressRef.current = flushProgress;
  currentProgressRef.current = currentProgress;
  // 持有最新的 saveProgress，避免 EPUB 进度保存防抖定时器使用陈旧闭包
  const saveProgressRef = useRef(saveProgress);
  saveProgressRef.current = saveProgress;

  // EPUB 专用：构建进度保存所需的 ReadingLocation 附加字段
  const buildEpubLocationExtras = useCallback((info: EpubLocationInfo, chapter?: MobileReaderDocument["toc"][number]): Partial<ReadingLocation> => {
    const extras: Partial<ReadingLocation> = {
      mode: "epub-cfi",
      precision: "exact",
      epub: {
        cfi: info.cfi,
        href: info.href,
        chapterRef: chapter?.id
      }
    };
    if (typeof info.pageIndex === "number") {
      extras.page = { pageIndex: info.pageIndex, pageCount: info.pageCount };
    }
    if (chapter) {
      extras.text = {
        headingPath: [chapter.title],
        anchorText: info.chapterTitle || chapter.title || ""
      };
    }
    return extras;
  }, []);

  const handleEpubLocationChange = useCallback((info: EpubLocationInfo) => {
    epubNavigator.updateLocation(info);
    setCurrentProgress(info.progressPercent);
    const matched = document.toc.find((item) => {
      if (!item.href || !info.href) return false;
      return item.href.split("#")[0] === info.href.split("#")[0];
    }) ?? currentChapter;
    setCurrentChapter(matched);
    setCurrentPageInfo({
      pageIndex: typeof info.pageIndex === "number" ? info.pageIndex : undefined,
      pageCount: typeof info.pageCount === "number" ? info.pageCount : undefined
    });
    if (epubSaveTimerRef.current) window.clearTimeout(epubSaveTimerRef.current);
    epubSaveTimerRef.current = window.setTimeout(() => {
      const extras = buildEpubLocationExtras(info, matched);
      void saveProgressRef.current(info.progressPercent, extras);
    }, READER_PROGRESS_SAVE_DEBOUNCE_MS);
  }, [document.toc, currentChapter, buildEpubLocationExtras, epubNavigator]);

  const handleEpubReady = useCallback(() => {
    epubNavigator.markReady();
    setEpubReady(true);
  }, [epubNavigator]);

  const handleEpubDocumentReady = useCallback((nextDocument: MobileReaderDocument) => {
    epubNavigator.updateDocument(nextDocument);
    setDocument(nextDocument);
    setCurrentChapter(nextDocument.toc[0]);
  }, [epubNavigator, setDocument]);

  const handleEpubError = useCallback((message: string) => {
    setEpubRenderError(message);
  }, []);

  const epubTurnPrev = useCallback(() => {
    triggerReaderPageTurn(-1);
    void epubNavigator.goBackward();
  }, [epubNavigator, triggerReaderPageTurn]);

  const epubTurnNext = useCallback(() => {
    triggerReaderPageTurn(1);
    void epubNavigator.goForward();
  }, [epubNavigator, triggerReaderPageTurn]);

  // 封装导航函数：EPUB 格式使用 epubjs 渲染视图，TXT/Markdown 使用原有滚动容器
  const handleJumpToChapter = useCallback((target: MobileReaderDocument["toc"][number] | undefined) => {
    if (book.format === "epub") {
      if (!target) return;
      void epubNavigator.goToChapter(target);
      finishReaderJump();
      return;
    }
    jumpToChapter(target);
  }, [book.format, epubNavigator, jumpToChapter, finishReaderJump]);

  const handleMoveChapter = useCallback((direction: -1 | 1) => {
    if (book.format === "epub") {
      if (direction < 0) void epubNavigator.goBackward();
      else void epubNavigator.goForward();
      finishReaderJump();
      return;
    }
    moveChapter(direction);
  }, [book.format, epubNavigator, moveChapter, finishReaderJump]);

  const handleJumpToReaderProgress = useCallback((progressPercent: number) => {
    if (book.format === "epub") {
      void epubNavigator.goToProgress(progressPercent);
      finishReaderJump();
      return;
    }
    jumpToReaderProgress(progressPercent);
  }, [book.format, epubNavigator, jumpToReaderProgress, finishReaderJump]);

  useEffect(() => {
    if (typeof initialProgressPercent !== "number") return;
    const bounded = Math.min(100, Math.max(0, initialProgressPercent));
    const targetKey = `${book.id}:${bounded.toFixed(3)}`;
    if (initialProgressAppliedRef.current === targetKey) return;
    if (book.format === "epub" && (!epubReady || document.toc.length === 0)) return;
    if (book.format !== "epub" && !document.html.trim()) return;
    initialProgressAppliedRef.current = targetKey;
    handleJumpToReaderProgress(bounded);
    setReaderNotice(`已定位到 ${bounded.toFixed(1)}% 的标注位置。`);
  }, [book.format, book.id, document.html, document.toc.length, epubReady, handleJumpToReaderProgress, initialProgressPercent]);

  // 目录 drawer 主动跳转：记录最近浏览章节
  const handleJumpToChapterFromDrawer = useCallback((target: MobileReaderDocument["toc"][number] | undefined) => {
    if (target?.id) {
      setRecentChapterIds((prev) => {
        const next = [target.id, ...prev.filter((id) => id !== target.id)].slice(0, 5);
        try {
          localStorage.setItem(`creation-reading-assistant-mobile-reader-recent-chapters-${book.id}`, JSON.stringify(next));
        } catch {
          // 忽略存储失败
        }
        return next;
      });
    }
    handleJumpToChapter(target);
  }, [book.id, handleJumpToChapter]);

  // 打开 TTS 面板：从当前文字阅读位置开始
  const openTTSPlayer = useCallback(() => {
    if (book.format === "epub") {
      setReaderNotice("EPUB 朗读正在适配章节文本，当前版本暂不支持。TXT 和 Markdown 可正常朗读。");
      return;
    }
    if (!document.fullText) {
      setReaderNotice("当前章节没有可朗读的文字。");
      return;
    }
    let globalOffset = savedReadingLocation?.text?.charOffset;
    if (typeof globalOffset !== "number" || globalOffset <= 0) {
      globalOffset = scrollRef.current
        ? readerCharOffsetFromViewport(scrollRef.current, document, settings.readerMode, currentChapter)
        : 0;
    }
    const start = document.renderAllChapters
      ? Math.max(0, globalOffset)
      : Math.max(0, globalOffset - (currentChapter?.startOffset ?? 0));
    setTtsStartCharOffset(start);
    setTtsCharOffset(start);
    setTtsParagraphIndex(-1);
    setShowTTSPlayer(true);
  }, [book.format, book.id, document, settings.readerMode, currentChapter, savedReadingLocation]);

  // TTS 进度同步到文字阅读视图
  const handleTTSProgressChange = useCallback((info: { charOffset: number; paragraphIndex: number }) => {
    setTtsCharOffset(info.charOffset);
    setTtsParagraphIndex(info.paragraphIndex);
    if (!settings.ttsSyncToReader || !scrollRef.current || !document.fullText || info.paragraphIndex < 0) return;
    const globalOffset = document.renderAllChapters
      ? info.charOffset
      : (currentChapter?.startOffset ?? 0) + info.charOffset;
    scrollReaderToParagraph(scrollRef.current, info.paragraphIndex, settings.readerMode);
    const nextProgress = Math.min(100, (globalOffset / document.fullText.length) * 100);
    setCurrentProgress(nextProgress);
    const nextChapterIndex = readerTocIndexFromCharOffset(document, globalOffset);
    const nextChapter = document.toc[nextChapterIndex];
    if (nextChapter && nextChapter.id !== currentChapter?.id) {
      setCurrentChapter(nextChapter);
    }
  }, [settings.ttsSyncToReader, settings.readerMode, document, currentChapter]);

  // 从 TTS 回到正文：停止朗读并滚动到当前朗读段落
  const handleSwitchToTextFromTTS = useCallback(() => {
    setShowTTSPlayer(false);
    if (!settings.ttsSyncToReader || !scrollRef.current || ttsParagraphIndex < 0) return;
    scrollReaderToParagraph(scrollRef.current, ttsParagraphIndex, settings.readerMode);
    if (!document.fullText) return;
    const globalOffset = document.renderAllChapters
      ? ttsCharOffset
      : (currentChapter?.startOffset ?? 0) + ttsCharOffset;
    const nextProgress = Math.min(100, (globalOffset / document.fullText.length) * 100);
    setCurrentProgress(nextProgress);
    const chapter = document.toc[readerTocIndexFromCharOffset(document, globalOffset)] ?? currentChapter;
    void saveProgress(nextProgress, {
      mode: settings.readerMode === "paged" ? "page" : "scroll",
      precision: "estimated",
      text: {
        charOffset: globalOffset,
        chapterRef: chapter?.id,
        headingPath: [chapter?.title ?? book.title],
        anchorText: document.fullText.slice(globalOffset, globalOffset + 80).replace(/\s+/g, " ").trim()
      }
    });
  }, [settings.ttsSyncToReader, settings.readerMode, ttsParagraphIndex, ttsCharOffset, document, currentChapter, saveProgress, book.title]);

  // 包装滚动与翻页动作，在沉浸模式下重置自动隐藏计时器
  const handleReaderScrollWithImmersive = useCallback(() => {
    handleReaderScroll();
    resetImmersiveTimer();
  }, [handleReaderScroll, resetImmersiveTimer]);
  const runBackwardWithImmersive = useCallback(() => {
    (book.format === "epub" ? epubTurnPrev : runBackwardAction)();
    resetImmersiveTimer();
  }, [book.format, epubTurnPrev, runBackwardAction, resetImmersiveTimer]);
  const runForwardWithImmersive = useCallback(() => {
    (book.format === "epub" ? epubTurnNext : runForwardAction)();
    resetImmersiveTimer();
  }, [book.format, epubTurnNext, runForwardAction, resetImmersiveTimer]);

  const {
    handleReaderTouchStart,
    handleReaderTouchMove,
    handleReaderTouchEnd,
    handleReaderTap
  } = useReaderGestures({
    settings,
    onSettingsChange,
    lastReaderActivityRef,
    runBackwardAction: runBackwardWithImmersive,
    runForwardAction: runForwardWithImmersive,
    onToggleControls: toggleReaderControls,
    setSwipeDirection,
    navigationBlocked: book.format !== "epub" && (documentRendering || chapterTurnPending || !readerViewportReady)
  });

  const openInspirationSheet = useCallback(() => {
    setReaderSheet("inspiration-sheet");
  }, []);

  const getCurrentAnnotationLocator = useCallback((): ReaderLocator | null => {
    if (book.format === "epub") return epubNavigator.getCurrentLocator();
    const element = scrollRef.current;
    const extras = element
      ? readerLocationExtrasFromViewport(element, document, settings.readerMode, currentChapter)
      : undefined;
    return normalizeReaderLocator({
      version: 2,
      bookId: book.id,
      format: readerFormatFromBook(book),
      progression: Math.min(1, Math.max(0, currentProgress / 100)),
      chapterId: currentChapter?.id,
      textOffset: extras?.text?.charOffset,
      paragraphIndex: extras?.paragraphIndex,
      updatedAt: Date.now()
    });
  }, [book, currentChapter, currentProgress, document, epubNavigator, settings.readerMode]);

  const handleSaveInspiration = useCallback((nextSnapshot: MobileSnapshot, savedId: string) => {
    setLastSavedInspirationId(savedId);
    setReaderNotice("已保存灵感，并记录了来源书籍与阅读位置。");
    onSnapshotChange(nextSnapshot);
    onMessage("灵感已保存到灵感中心。");
    setReaderSheet(null);
    setSelectionText("");
    window.getSelection()?.removeAllRanges();
  }, [onSnapshotChange, onMessage]);

  const {
    addReaderInspiration,
    addReaderBookmark,
    addReaderNote,
    addReaderHighlight,
    deleteReaderHighlight,
    updateReaderHighlightColor,
    highlightToNote,
    highlightToInspiration,
    searchSelectedText,
    copySelectedText,
    clearSelectedText,
    captureSelection
  } = useReaderAnnotations({
    book,
    document,
    snapshot,
    onSnapshotChange,
    onMessage,
    selectionText,
    setSelectionText,
    currentChapter,
    currentProgress,
    getCurrentLocator: getCurrentAnnotationLocator,
    noteDraft,
    setNoteDraft,
    setReaderNotice,
    setReaderSearchQuery,
    openReaderDrawer,
    openInspirationSheet,
    lastReaderActivityRef
  });

  // TXT/Markdown 的 Android 文本选择在 touchend 时可能尚未提交；监听宿主
  // selectionchange 才能稳定显示应用自己的“灵感/高亮/笔记”工具栏。
  useEffect(() => {
    if (book.format === "epub") return;
    const updateSelection = () => {
      window.requestAnimationFrame(() => captureSelection());
    };
    window.document.addEventListener("selectionchange", updateSelection);
    return () => window.document.removeEventListener("selectionchange", updateSelection);
  }, [book.format, captureSelection]);

  // 书籍切换时重置会话状态
  useEffect(() => {
    clearProgressSaveTimer();
    if (epubSaveTimerRef.current) window.clearTimeout(epubSaveTimerRef.current);
    const savedProgress = progressFor(snapshot, book.id);
    readerSessionStartRef.current = Date.now();
    readerSessionStartProgressRef.current = savedProgress;
    setReaderChapterIndex(0);
    setCurrentProgress(savedProgress);
    setCurrentChapter(undefined);
    setActiveReadingMs(0);
    setReaderPanel(null);
    setReaderControlsVisible(false);
    setReaderNotice("");
    setEpubReady(false);
    setEpubRenderError("");
    epubNavigator.clearLocation();
    setShowTTSPlayer(false);
    setTtsStartCharOffset(0);
    setTtsCharOffset(0);
    setTtsParagraphIndex(-1);
    readerClosingRef.current = false;
    readerSessionSavedRef.current = false;
  }, [book.id, epubNavigator]);

  useEffect(() => () => {
    epubNavigator.destroy();
  }, [epubNavigator]);

  // readerNotice 自动消失：简短提示 2.5 秒，灵感保存提示 5 秒
  useEffect(() => {
    if (!readerNotice) return undefined;
    const hasInspirationLink = readerNotice.includes("灵感");
    const timer = window.setTimeout(() => {
      setReaderNotice("");
    }, hasInspirationLink ? 5_000 : 2_500);
    return () => window.clearTimeout(timer);
  }, [readerNotice]);

  const buildCurrentLocationExtras = useCallback((): Partial<ReadingLocation> | undefined => {
    const epubLocation = epubNavigator.getLegacyLocationInfo();
    if (book.format === "epub" && epubLocation) {
      return buildEpubLocationExtras(epubLocation, currentChapter);
    }
    const scrollElement = scrollRef.current;
    return scrollElement
      ? readerLocationExtrasFromViewport(scrollElement, document, settings.readerMode, currentChapter)
      : undefined;
  }, [book.format, buildEpubLocationExtras, currentChapter, document, epubNavigator, settings.readerMode]);
  const buildCurrentLocationExtrasRef = useRef(buildCurrentLocationExtras);
  buildCurrentLocationExtrasRef.current = buildCurrentLocationExtras;

  // 应用切到后台或页面隐藏时立即保存进度，降低杀进程丢进度的概率。
  // visibilitychange 与 Capacitor appStateChange 在部分 Android WebView 会连续触发，
  // 用短时间去重并复用同一写入队列，避免两个旧快照互相覆盖。
  useEffect(() => {
    let removeAppStateListener: (() => void) | undefined;
    let disposed = false;
    const persistInBackground = () => {
      const now = Date.now();
      if (readerClosingRef.current || now - lastBackgroundSaveAtRef.current < 800) return;
      lastBackgroundSaveAtRef.current = now;
      void flushProgressRef.current(currentProgressRef.current, buildCurrentLocationExtrasRef.current()).catch((error) => {
        console.warn("后台保存阅读进度失败", error);
      });
    };
    const handleVisibilityChange = () => {
      if (window.document.visibilityState === "hidden") persistInBackground();
    };
    window.document.addEventListener("visibilitychange", handleVisibilityChange);
    void CapacitorApp.addListener("appStateChange", ({ isActive }) => {
      if (!isActive) persistInBackground();
    }).then((handle) => {
      if (disposed) {
        void handle.remove();
      } else {
        removeAppStateListener = () => { void handle.remove(); };
      }
    });
    return () => {
      disposed = true;
      window.document.removeEventListener("visibilitychange", handleVisibilityChange);
      removeAppStateListener?.();
    };
  }, []);

  // 硬件返回键：按优先级关闭二级页面/菜单/选中文字/控件，最后保存进度退出
  useEffect(() => {
    const handleReaderBack = (event: Event) => {
      const readerEmptyForBack = book.format !== "epub" && !loading && !documentRendering && !document.html.trim();
      const readerBlockingErrorForBack = Boolean(loadError || (book.format === "epub" ? documentError : "") || readerTimeoutError || readerEmptyForBack);
      if (readerBlockingErrorForBack) {
        event.preventDefault();
        onBack();
        return;
      }
      if (readerSheet) {
        event.preventDefault();
        setReaderSheet(null);
        return;
      }
      if (readerOverflowVisible) {
        event.preventDefault();
        setReaderOverflowVisible(false);
        return;
      }
      if (readerPanel) {
        event.preventDefault();
        setReaderPanel(null);
        return;
      }
      if (selectionText) {
        event.preventDefault();
        clearSelectedText();
        return;
      }
      if (readerControlsVisible) {
        event.preventDefault();
        setReaderControlsVisible(false);
        setReaderNotice("");
        return;
      }
      // 没有可关闭的二级/菜单时，走与 UI 返回按钮相同的保存流程，避免硬件返回键丢进度
      event.preventDefault();
      closeReaderRef.current();
    };
    window.addEventListener("mobile-reader-back", handleReaderBack);
    return () => window.removeEventListener("mobile-reader-back", handleReaderBack);
  }, [book.format, document.html, documentRendering, loadError, documentError, loading, onBack, readerPanel, readerSheet, readerOverflowVisible, readerTimeoutError, selectionText, readerControlsVisible]);

  const closeReader = () => {
    if (readerClosingRef.current) return;
    readerClosingRef.current = true;
    const elapsedMs = Math.max(1000, activeReadingMs);
    const progressToSave = currentProgress;
    const locationExtras = buildCurrentLocationExtras();
    // 先返回书架，进度和会话在后台保存，避免长时间异步阻塞页面退出
    void (async () => {
      try {
        const saved = await flushProgress(progressToSave, locationExtras) ?? await loadMobileSnapshot();
        if (readerSessionSavedRef.current) return;
        readerSessionSavedRef.current = true;
        const next = await addMobileReadingSession(saved, book as LibraryBook, elapsedMs, progressToSave, locationExtras);
        onSnapshotChange(next);
      } catch (error) {
        console.warn("保存阅读进度或会话失败", error);
      }
    })();
    onBack();
  };

  // 始终把最新的 closeReader 写入 ref，供硬件返回键事件回调安全调用
  closeReaderRef.current = closeReader;

  // 派生值：底部进度 chip 显示章节 + 页码 + 百分比
  const chapterIndex = document.toc.findIndex((item) => item.id === currentChapter?.id);
  const chapterLabel = currentChapter
    ? (currentChapter.level === 0 && currentChapter.title
        ? currentChapter.title
        : `第 ${chapterIndex + 1} 章${currentChapter.title ? ` · ${currentChapter.title}` : ""}`)
    : "正文";
  const pageLabel =
    typeof currentPageInfo.pageIndex === "number" && typeof currentPageInfo.pageCount === "number"
      ? `第 ${currentPageInfo.pageIndex + 1}/${currentPageInfo.pageCount} 页`
      : "";
  const progressLabel = pageLabel ? `${pageLabel} · ${currentProgress.toFixed(2)}%` : `${currentProgress.toFixed(2)}%`;
  const bookNotes = snapshot.notes.filter((item) => item.bookId === book.id && item.kind !== "bookmark");
  const bookBookmarks = snapshot.notes.filter((item) => item.bookId === book.id && item.kind === "bookmark");
  const bookHighlights = useMemo(
    () => snapshot.highlights.filter((item) => item.bookId === book.id),
    [snapshot.highlights, book.id]
  );
  const bookInspirations = useMemo(
    () => snapshot.inspirations.filter((item) => item.source?.bookId === book.id),
    [snapshot.inspirations, book.id]
  );
  const showSelectionToolbar = Boolean(selectionText) && !readerPanel;
  const savedBookReadingMs = bookReadingTimeMs(snapshot, book.id);
  const sessionProgressDelta = Math.max(0, currentProgress - readerSessionStartProgressRef.current);
  const readerSpeed = estimateBookReadingSpeed(snapshot, book, document.wordCount, activeReadingMs, sessionProgressDelta);
  // 估算读完本书剩余时间
  const effectiveWordCount = document.wordCount > 0 ? document.wordCount : Math.max(1, Math.round(book.size / 3));
  const remainingWords = Math.max(0, Math.round(effectiveWordCount * (1 - currentProgress / 100)));
  const estimatedRemainingMs = readerSpeed > 0 ? Math.round(remainingWords / readerSpeed * 60_000) : 0;
  const remainingTimeLabel = estimatedRemainingMs > 60_000 ? `· 预计还需 ${formatDuration(estimatedRemainingMs)}` : "";
  const readerEmptyMessage = !loading && !documentRendering && !document.html.trim() && book.format !== "epub"
    ? "没有读到正文内容。请返回书架重新导入本地文件，或在同步后下载正文。"
    : "";
  const readerErrorMessage = loadError || (book.format === "epub" ? documentError || epubRenderError : "") || readerTimeoutError || readerEmptyMessage;
  const initialDocumentRendering = documentRendering && !document.html.trim();
  const hasReadableDocument = !loading && !readerErrorMessage && (book.format === "epub" ? Boolean(content) : Boolean(document.html.trim()));
  const forceReaderChromeVisible = Boolean(readerControlsVisible || readerErrorMessage || loading || initialDocumentRendering || (book.format === "epub" && !epubReady));

  return (
    <main
      className={`reader-shell reader-format-${book.format} reader-bg-${settings.readerBackground} reader-mode-${settings.readerMode} reader-tap-${settings.tapZoneMode} reader-turn-${settings.pageTurnEffect ?? "none"} ${forceReaderChromeVisible ? "" : "reader-chrome-hidden"} ${readerErrorMessage ? "reader-has-error" : ""} ${loading || initialDocumentRendering ? "reader-is-loading" : ""} ${showTTSPlayer ? "tts-active" : ""} ${selectionText ? "selection-active" : ""} ${settings.immersiveMode ? "immersive-mode" : ""} ${settings.chineseTypography ? "chinese-typography" : ""}`}
      onPointerDown={resetImmersiveTimer}
    >
      <div className="reader-dim-layer" style={{ opacity: Math.max(0, Math.min(0.58, (100 - settings.brightness) / 100)) }} aria-hidden="true" />
      <header className="reader-topbar">
        <button className="ghost-button reader-icon-button" onClick={() => void closeReader()} aria-label="返回书架">
          ←
        </button>
        <div className="reader-title-block">
          <strong>{book.title}</strong>
          <p>
            {book.format.toUpperCase()} · {progressLabel}{remainingTimeLabel} · 本次 {formatDuration(activeReadingMs)}
          </p>
        </div>
        <div className="reader-topbar-actions">
          <button
            className="ghost-button reader-icon-button"
            onClick={() => {
              if (showTTSPlayer) {
                setShowTTSPlayer(false);
              } else {
                openTTSPlayer();
              }
            }}
            aria-label="听书"
          >
            <Headphones size={20} />
          </button>
          <button
            className="ghost-button reader-icon-button"
            onClick={() => setReaderSheet(readerSheet === "ai-assist-sheet" ? null : "ai-assist-sheet")}
            aria-label="AI 阅读辅助"
          >
            <Bot size={20} />
          </button>
          <div className="relative">
            <button
              className="ghost-button reader-icon-button"
              onClick={() => setReaderOverflowVisible(!readerOverflowVisible)}
              aria-label="更多"
            >
              <MoreHorizontal size={20} />
            </button>
            {readerOverflowVisible && (
              <div className="reader-overflow-dropdown">
                <button onClick={() => { setReaderOverflowVisible(false); openReaderDrawer("search"); }}>
                  <Search size={18} /><span>搜索</span>
                </button>
                <button onClick={() => { setReaderOverflowVisible(false); setReaderDrawerTab("highlights"); openReaderDrawer("highlights"); }}>
                  <MessageSquare size={18} /><span>高亮</span>
                </button>
                <button onClick={() => { setReaderOverflowVisible(false); setReaderDrawerTab("notes"); openReaderDrawer("notes"); }}>
                  <MessageSquare size={18} /><span>笔记</span>
                </button>
                <button onClick={() => { setReaderOverflowVisible(false); setReaderPanel("book-info"); }}>
                  <Info size={18} /><span>书籍信息</span>
                </button>
                <button onClick={() => { setReaderOverflowVisible(false); setReaderPanel("settings"); }}>
                  <Sun size={18} /><span>设置</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {(settings.showReaderInfo ?? true) && hasReadableDocument && !forceReaderChromeVisible && !readerPanel && !readerSheet && !selectionText && (
        <>
          <div className="reader-quiet-info reader-quiet-info-top" aria-hidden="true">
            <span className="reader-quiet-info-chapter">{chapterLabel}</span>
          </div>
          <div className="reader-quiet-info reader-quiet-info-bottom" aria-hidden="true">
            <span>{progressLabel}</span>
            <span>本次 {formatDuration(activeReadingMs)}</span>
          </div>
        </>
      )}

      {readerPanel && (
        <ReaderPanelView
          readerPanel={readerPanel}
          setReaderPanel={setReaderPanel}
          readerDrawerTab={readerDrawerTab}
          setReaderDrawerTab={setReaderDrawerTab}
          setReaderSheet={setReaderSheet}
          book={book}
          document={document}
          snapshot={snapshot}
          settings={settings}
          onSettingsChange={onSettingsChange}
          onSnapshotChange={onSnapshotChange}
          onBack={onBack}
          onConfirm={onConfirm}
          onMessage={onMessage}
          onOpenInspiration={onOpenInspiration}
          loadError={loadError}
          readerSearchQuery={readerSearchQuery}
          setReaderSearchQuery={setReaderSearchQuery}
          readerSearchResults={readerSearchResults}
          bookNotes={bookNotes}
          bookBookmarks={bookBookmarks}
          bookHighlights={bookHighlights}
          bookInspirations={bookInspirations}
          currentProgress={currentProgress}
          activeReadingMs={activeReadingMs}
          chapterLabel={chapterLabel}
          noteDraft={noteDraft}
          setNoteDraft={setNoteDraft}
          jumpToChapter={handleJumpToChapter}
          jumpToReaderProgress={handleJumpToReaderProgress}
          jumpToSearchResult={jumpToSearchResult}
          addReaderInspiration={addReaderInspiration}
          addReaderNote={addReaderNote}
          addReaderBookmark={addReaderBookmark}
          deleteReaderHighlight={deleteReaderHighlight}
          updateReaderHighlightColor={updateReaderHighlightColor}
          highlightToNote={highlightToNote}
          highlightToInspiration={highlightToInspiration}
        />
      )}

      <section
        ref={scrollRef}
        className={`reader-scroll-container ${swipeDirection ? `swipe-${swipeDirection}` : ""}`}
        data-page-turn={pageTurnDirection ?? undefined}
        // EPUB 正文位于 iframe 内，并由 EpubReaderView 自己处理点击区和滑动。
        // 如果外层 section 同时监听，Android 会把同一次物理触摸分别作为
        // iframe touch/click 与宿主 click 消费，造成一次点击连续翻多页甚至跨章。
        onClick={book.format === "epub" ? undefined : handleReaderTap}
        onScroll={handleReaderScrollWithImmersive}
        onMouseUp={captureSelection}
        onTouchStart={book.format === "epub" ? undefined : handleReaderTouchStart}
        onTouchMove={book.format === "epub" ? undefined : handleReaderTouchMove}
        onTouchEnd={(event) => {
          if (book.format === "epub") return;
          captureSelection(event);
          handleReaderTouchEnd(event);
        }}
      >
        {(loading || initialDocumentRendering || (book.format === "epub" && !epubReady && !epubRenderError)) && (
          <div className="reader-skeleton" role="status" aria-live="polite">
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <div className="reader-skeleton-line"></div>
            <p style={{ marginTop: "24px", textAlign: "center", color: "var(--md3-on-surface-variant)", fontSize: "13px" }}>
              {loading ? "正在读取本地正文..." : book.format === "epub" ? "正在解析 EPUB 章节..." : "正在排版正文..."}
            </p>
            {loadingHintLong && (
              <p style={{ marginTop: "12px", textAlign: "center", color: "var(--md3-on-surface-variant)", fontSize: "13px" }}>
                正文加载时间较长，可能是文件过大或格式异常。
              </p>
            )}
          </div>
        )}
        {readerErrorMessage && (
          <div className="reader-loading-state reader-load-error" role="alert">
            <strong>{book.format === "epub" ? "EPUB 暂时打不开" : "正文暂时打不开"}</strong>
            <p>{readerErrorMessage}</p>
            <div className="reader-error-actions">
              <button onClick={() => void closeReader()}>返回书架</button>
              <button className="secondary-button" onClick={() => onReload()}>重新打开</button>
              <button className="secondary-button" onClick={() => void closeReader()}>重新导入或下载正文</button>
            </div>
          </div>
        )}
        {!readerErrorMessage && documentError && (
          <div className="reader-loading-state reader-fallback-note" role="status">
            <strong>已启用兜底阅读</strong>
            <p>{documentError}</p>
          </div>
        )}
        {hasReadableDocument && book.format !== "epub" && (
          <article
            key={`${book.id}:${document.currentTocIndex ?? readerChapterIndex}:${settings.readerMode}`}
            className={`reader-content ${readerViewportReady ? "" : "reader-content-restoring"}`}
            style={{
              fontSize: `${settings.fontSize}px`,
              lineHeight: settings.lineHeight,
              padding: settings.readerMode === "paged" ? undefined : `${settings.pageMargin}px`,
              ["--reader-page-margin" as string]: `${settings.pageMargin}px`,
              ["--reader-paragraph-spacing" as string]: `${settings.paragraphSpacing}em`,
              ["--reader-line-height" as string]: String(settings.lineHeight),
              fontWeight: settings.fontWeight === "bold" ? 650 : 400
            }}
            onClick={(event) => {
              // 拦截 EPUB 内部目录/章节链接（已在前序步骤转为 data-reader-href），避免 WebView 原生导航跳回封面或空白页
              const target = event.target as HTMLElement;
              const link = target.closest("a[data-reader-href]") as HTMLAnchorElement | null;
              if (!link) return;
              event.preventDefault();
              event.stopPropagation();
              const rawHref = link.dataset.readerHref;
              if (!rawHref) return;
              const hrefWithoutHash = rawHref.split("#")[0];
              const hrefFileName = hrefWithoutHash.split("/").pop() ?? "";
              const tocItem = document.toc.find((item) => {
                if (!item.href) return false;
                const itemHref = item.href.split("#")[0];
                const itemFileName = itemHref.split("/").pop() ?? "";
                return itemHref === hrefWithoutHash || itemHref === hrefFileName || itemFileName === hrefFileName;
              });
              if (tocItem) {
                jumpToChapter(tocItem);
                return;
              }
              onMessage("暂时无法定位该章节，请使用目录跳转");
            }}
            dangerouslySetInnerHTML={{ __html: document.html }}
          />
        )}
        {hasReadableDocument && book.format === "epub" && (
          <EpubReaderView
            ref={bindEpubView}
            book={book}
            content={content}
            settings={settings}
            initialCfi={initialEpubCfi}
            onLocationChange={handleEpubLocationChange}
            onDocumentReady={handleEpubDocumentReady}
            onReady={handleEpubReady}
            onError={handleEpubError}
            onInteraction={() => { lastReaderActivityRef.current = Date.now(); }}
            onBackward={epubTurnPrev}
            onForward={epubTurnNext}
            onToggleControls={toggleReaderControls}
            onSelectionChange={setSelectionText}
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
        <div className="reader-toast" role="status" aria-live="polite">
          <span>{readerNotice}</span>
          {readerNotice.includes("灵感") && lastSavedInspirationId && (
            <button onClick={() => onOpenInspiration(lastSavedInspirationId)}>查看灵感</button>
          )}
          <button onClick={() => setReaderNotice("")}>继续阅读</button>
        </div>
      )}

      {rhythmNotice && (
        <div className="reader-rhythm-notice" role="status" aria-live="polite">
          {rhythmNotice}
        </div>
      )}

      {showSelectionToolbar && (
        <section className="reader-selection-toolbar" role="toolbar" aria-label="选中文字操作">
          <p>{selectionText.slice(0, 42)}{selectionText.length > 42 ? "…" : ""}</p>
          {showHighlightColors ? (
            <div className="reader-highlight-color-row" role="group" aria-label="选择高亮颜色">
              {HIGHLIGHT_COLOR_OPTIONS.map((opt) => (
                <button
                  key={opt.color}
                  className={`reader-color-dot reader-color-${opt.color}`}
                  onClick={() => { void addReaderHighlight(opt.color); setShowHighlightColors(false); }}
                  aria-label={`${opt.label}色高亮`}
                >
                  {opt.label}
                </button>
              ))}
              <button className="ghost-button" onClick={() => setShowHighlightColors(false)}>取消</button>
            </div>
          ) : (
            <div>
              <button onClick={() => setShowHighlightColors(true)}>高亮</button>
              {(settings.showAIExplainButton ?? true) && (
                <button
                  onClick={() => setReaderSheet("ai-explain-sheet")}
                  disabled={!aiConfigured}
                  title={aiConfigured ? "AI 解读选中文本" : "未配置 AI 服务"}
                  style={{ opacity: aiConfigured ? 1 : 0.5 }}
                >
                  AI 解读
                </button>
              )}
              <button onClick={() => void addReaderInspiration()}>记为灵感</button>
              <button onClick={() => void addReaderNote()}>存笔记</button>
              <button onClick={searchSelectedText}>搜索</button>
              <button onClick={() => void copySelectedText()}>复制</button>
              <button className="ghost-button" onClick={clearSelectedText}>清除</button>
            </div>
          )}
        </section>
      )}

      {/* 进度条已整合到底部菜单栏，跟随 chrome 显隐，不再使用浮动气泡 */}

      <ReaderSheets
        readerSheet={readerSheet}
        setReaderSheet={setReaderSheet}
        readerDrawerTab={readerDrawerTab}
        setReaderDrawerTab={setReaderDrawerTab}
        book={book}
        document={document}
        settings={settings}
        onSettingsChange={onSettingsChange}
        snapshot={snapshot}
        bookBookmarks={bookBookmarks}
        bookInspirations={bookInspirations}
        currentProgress={currentProgress}
        chapterLabel={chapterLabel}
        progressLabel={progressLabel}
        savedBookReadingMs={savedBookReadingMs}
        activeReadingMs={activeReadingMs}
        readerSpeed={readerSpeed}
        estimatedRemainingMs={estimatedRemainingMs}
        jumpToChapter={handleJumpToChapter}
        jumpToChapterFromDrawer={handleJumpToChapterFromDrawer}
        jumpToReaderProgress={handleJumpToReaderProgress}
        moveChapter={handleMoveChapter}
        addReaderInspiration={addReaderInspiration}
        addReaderBookmark={addReaderBookmark}
        saveProgress={async (progressPercent) => { await saveProgress(progressPercent); }}
        recentChapterIds={recentChapterIds}
        aiConfigured={aiConfigured}
      />

      {readerSheet === "inspiration-sheet" && (
        <ReaderInspirationSheet
          book={book}
          snapshot={snapshot}
          selectionText={selectionText}
          currentProgress={currentProgress}
          currentChapter={currentChapter}
          currentLocator={getCurrentAnnotationLocator()}
          onSave={handleSaveInspiration}
          onClose={() => setReaderSheet(null)}
        />
      )}

      {readerSheet === "ai-assist-sheet" && hasReadableDocument && (
        <ReaderAIAssistSheet
          bookTitle={book.title}
          chapterLabel={chapterLabel}
          chapterText={document.plainText || document.fullText || ""}
          selectedText={selectionText}
          onClose={() => setReaderSheet(null)}
        />
      )}

      {readerSheet === "ai-explain-sheet" && hasReadableDocument && (
        <ReaderAIExplainSheet
          book={book}
          snapshot={snapshot}
          selectionText={selectionText}
          currentProgress={currentProgress}
          currentChapter={currentChapter}
          currentLocator={getCurrentAnnotationLocator()}
          onSave={handleSaveInspiration}
          onClose={() => setReaderSheet(null)}
        />
      )}

      <footer className={`reader-bottom-sheet ${showTTSPlayer ? "tts-active" : ""} ${selectionText ? "selection-active" : ""}`}>
        <div className="reader-actions reader-primary-actions">
          <button onClick={() => openReaderDrawer("toc")} aria-label="目录">
            <Menu size={20} /><span>目录</span>
          </button>
          <button onClick={() => setReaderSheet(readerSheet === "progress-popover" ? null : "progress-popover")} aria-label="进度">
            <BarChart3 size={20} /><span>进度</span>
          </button>
          <button onClick={() => void addReaderInspiration()} aria-label="记为灵感">
            <Sparkles size={20} /><span>灵感</span>
          </button>
          <button onClick={() => setReaderSheet("theme-sheet")} aria-label="主题外观">
            <Sun size={20} /><span>主题</span>
          </button>
          <button onClick={() => setReaderPanel("settings")} aria-label="阅读设置">
            <span className="reader-aa-icon">Aa</span><span>设置</span>
          </button>
        </div>
      </footer>

      {showTTSPlayer && hasReadableDocument && (
        <TTSPlayerBar
          chapterLabel={chapterLabel}
          text={document.plainText || document.fullText || ""}
          bookId={book.id}
          bookTitle={book.title}
          bottomOffset={72}
          initialCharOffset={ttsStartCharOffset}
          onProgressChange={handleTTSProgressChange}
          onSwitchToText={handleSwitchToTextFromTTS}
        />
      )}
    </main>
  );
}
