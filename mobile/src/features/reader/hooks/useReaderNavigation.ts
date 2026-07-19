import { useEffect, useLayoutEffect, useRef, useState } from "react";
import type { MutableRefObject, RefObject } from "react";
import {
  applyReaderExactLocationOffsets,
  calculateReaderProgress,
  findCurrentChapter,
  readerBookProgressFromLocal,
  readerChapterProgressFromCharOffset,
  readerLocalProgressFromBook,
  readerLocationExtrasFromViewport,
  restoreReaderViewportAfterLayout,
  scrollReaderToPercent
} from "../reader-navigation";
import { jumpToReaderSearchResult, type ReaderSearchResult } from "../reader-search";
import type { ReaderDrawerTab, ReaderPanel, ReaderSheet } from "../reader-model";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import { loadMobileSnapshot, saveMobileReadingProgress } from "../../../services/mobile-storage";
import type { MobileBook, MobileReaderSettings, MobileSnapshot } from "../../../types/mobile";
import type { ReadingLocation } from "../../../../../src/types/library";
import { READER_PROGRESS_SAVE_DEBOUNCE_MS } from "../reader-constants";

interface UseReaderNavigationOptions {
  book: MobileBook;
  document: MobileReaderDocument;
  settings: MobileReaderSettings;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  scrollRef: RefObject<HTMLElement | null>;
  /** 共享状态 */
  readerChapterIndex: number;
  setReaderChapterIndex: React.Dispatch<React.SetStateAction<number>>;
  currentChapter: MobileReaderDocument["toc"][number] | undefined;
  setCurrentChapter: React.Dispatch<React.SetStateAction<MobileReaderDocument["toc"][number] | undefined>>;
  currentProgress: number;
  setCurrentProgress: React.Dispatch<React.SetStateAction<number>>;
  /** UI 状态 */
  readerPanel: ReaderPanel | null;
  setReaderPanel: React.Dispatch<React.SetStateAction<ReaderPanel | null>>;
  readerSheet: ReaderSheet;
  setReaderSheet: React.Dispatch<React.SetStateAction<ReaderSheet>>;
  setReaderControlsVisible: React.Dispatch<React.SetStateAction<boolean>>;
  setReaderNotice: React.Dispatch<React.SetStateAction<string>>;
  setReaderDrawerTab: React.Dispatch<React.SetStateAction<ReaderDrawerTab>>;
  /** 来自 useReaderSession 的活跃引用 */
  lastReaderActivityRef: MutableRefObject<number>;
  /** 上次保存的精确阅读位置，用于首次打开时精确恢复 */
  initialLocation?: ReadingLocation;
  /** 搜索相关 */
  readerSearchQuery: string;
  setReaderSearchQuery: React.Dispatch<React.SetStateAction<string>>;
}

/**
 * 阅读器导航与翻页逻辑。
 * 负责：进度保存、章节跳转、翻页、滚动进度计算、视口恢复。
 * 关键约束：
 * - paged 模式向前翻章时使用 pendingScrollToEndRef 标记渲染后滚动到末尾
 * - renderAll 模式按全书进度滚动，单章模式按本地进度滚动
 * - 滚动模式边界（开头/末尾）显示 toast 而非静默返回
 * - 进度保存使用防抖（900ms）
 */
export function useReaderNavigation({
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
  initialLocation,
  readerSearchQuery,
  setReaderSearchQuery
}: UseReaderNavigationOptions) {
  const progressSaveTimer = useRef<number>();
  const progressWriteQueueRef = useRef<Promise<void>>(Promise.resolve());
  const pageTurnTimer = useRef<number>();
  const searchTimer = useRef<number>();
  const chapterTurnTimer = useRef<number>();
  const chapterTurnPendingRef = useRef(false);
  const restoredViewportKeyRef = useRef("");
  // paged 模式下向前翻章时，标记需要在渲染后滚动到末尾
  const pendingScrollToEndRef = useRef(false);
  const [pageTurnDirection, setPageTurnDirection] = useState<"forward" | "backward" | null>(null);
  const [swipeDirection, setSwipeDirection] = useState<"left" | "right" | null>(null);
  const [currentPageInfo, setCurrentPageInfo] = useState<{ pageIndex?: number; pageCount?: number }>({});
  const [chapterTurnPending, setChapterTurnPending] = useState(false);
  const [readerViewportReady, setReaderViewportReady] = useState(false);
  // 允许外部（如 EPUB 渲染视图）直接更新页码信息
  const updateCurrentPageInfo = (info: { pageIndex?: number; pageCount?: number }) => {
    setCurrentPageInfo(info);
  };
  // 组件卸载时清理所有定时器
  useEffect(() => {
    return () => {
      if (progressSaveTimer.current) window.clearTimeout(progressSaveTimer.current);
      if (pageTurnTimer.current) window.clearTimeout(pageTurnTimer.current);
      if (searchTimer.current) window.clearTimeout(searchTimer.current);
      if (chapterTurnTimer.current) window.clearTimeout(chapterTurnTimer.current);
    };
  }, []);

  const saveProgress = async (progressPercent = currentProgress, locationExtras?: Partial<ReadingLocation>) => {
    const bounded = Math.min(100, Math.max(0, progressPercent));
    setCurrentProgress(bounded);
    let savedSnapshot: MobileSnapshot | undefined;
    const write = progressWriteQueueRef.current
      .catch(() => undefined)
      .then(async () => {
        // 进度保存可能与灵感、笔记、同步等写入交错。每次从持久层读取最新快照，
        // 避免用渲染时捕获的旧 snapshot 把其他模块刚写入的数据覆盖掉。
        const latestSnapshot = await loadMobileSnapshot();
        savedSnapshot = await saveMobileReadingProgress(latestSnapshot, book, bounded, 0, locationExtras);
        onSnapshotChange(savedSnapshot);
      });
    progressWriteQueueRef.current = write.then(() => undefined, () => undefined);
    await write;
    return savedSnapshot;
  };
  // 使用 ref 持有最新的 saveProgress，避免防抖定时器触发时使用陈旧闭包
  const saveProgressRef = useRef(saveProgress);
  saveProgressRef.current = saveProgress;

  const triggerReaderPageTurn = (direction: -1 | 1) => {
    setPageTurnDirection(direction > 0 ? "forward" : "backward");
    if (pageTurnTimer.current) window.clearTimeout(pageTurnTimer.current);
    pageTurnTimer.current = window.setTimeout(() => {
      setPageTurnDirection(null);
      pageTurnTimer.current = undefined;
    }, 320);
  };

  const finishReaderJump = () => {
    setReaderPanel(null);
    setReaderSheet(null);
    setReaderControlsVisible(false);
    setReaderNotice("");
  };

  // 切换书籍时清理防抖定时器，避免旧书的进度写入新书状态
  const clearProgressSaveTimer = () => {
    if (progressSaveTimer.current) {
      window.clearTimeout(progressSaveTimer.current);
      progressSaveTimer.current = undefined;
    }
  };

  const flushProgress = async (progressPercent = currentProgress, locationExtras?: Partial<ReadingLocation>) => {
    clearProgressSaveTimer();
    return saveProgress(progressPercent, locationExtras);
  };

  const jumpToReaderProgress = (progressPercent: number) => {
    const element = scrollRef.current;
    if (!element) return;
    const bounded = Math.min(100, Math.max(0, progressPercent));
    const direction = bounded >= currentProgress ? 1 : -1;
    lastReaderActivityRef.current = Date.now();
    // renderAll 模式：所有章节已连续渲染，直接按全书进度滚动
    if (document.renderAllChapters) {
      scrollReaderToPercent(element, bounded, settings.readerMode);
      setCurrentProgress(bounded);
      setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
      triggerReaderPageTurn(direction);
      finishReaderJump();
      return;
    }
    const total = document.totalChapters ?? document.toc.length;
    if (total > 1) {
      const rawChapterProgress = (bounded / 100) * total;
      const nextChapterIndex = Math.min(total - 1, Math.max(0, Math.floor(rawChapterProgress)));
      const localProgress = Math.min(100, Math.max(0, (rawChapterProgress - nextChapterIndex) * 100));
      if (nextChapterIndex !== (document.currentTocIndex ?? readerChapterIndex)) {
        setReaderChapterIndex(nextChapterIndex);
        setCurrentProgress(bounded);
        setCurrentChapter(document.toc[nextChapterIndex]);
        triggerReaderPageTurn(direction);
        finishReaderJump();
        return;
      }
      scrollReaderToPercent(element, localProgress, settings.readerMode);
    } else {
      scrollReaderToPercent(element, bounded, settings.readerMode);
    }
    setCurrentProgress(bounded);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
    triggerReaderPageTurn(direction);
    finishReaderJump();
  };

  const jumpToChapter = (target: MobileReaderDocument["toc"][number] | undefined) => {
    const element = scrollRef.current;
    if (!target) return;
    lastReaderActivityRef.current = Date.now();
    // 滚动模式（所有章节已渲染）：直接滚动到对应 section 元素
    if (settings.readerMode === "scroll" && element) {
      const chapterElement = element.querySelector<HTMLElement>(`#${target.id}`);
      if (chapterElement) {
        const targetOffset = chapterElement.offsetTop;
        const direction = targetOffset >= element.scrollTop ? 1 : -1;
        element.scrollTo({ top: Math.max(0, targetOffset - 16), behavior: "auto" });
        setCurrentChapter(target);
        triggerReaderPageTurn(direction);
        finishReaderJump();
        return;
      }
    }
    if (typeof target.index === "number") {
      const total = document.totalChapters ?? document.toc.length;
      const nextIndex = Math.min(Math.max(0, target.index), Math.max(0, total - 1));
      setReaderChapterIndex(nextIndex);
      setCurrentProgress(total > 1 ? (nextIndex / total) * 100 : 0);
      setCurrentChapter(target);
      triggerReaderPageTurn(nextIndex >= (document.currentTocIndex ?? readerChapterIndex) ? 1 : -1);
      finishReaderJump();
      return;
    }
    if (!element) return;
    const chapterElement = element.querySelector<HTMLElement>(`#${target.id}`);
    if (chapterElement) {
      const targetOffset = settings.readerMode === "paged" ? chapterElement.offsetLeft : chapterElement.offsetTop;
      const currentOffset = settings.readerMode === "paged" ? element.scrollLeft : element.scrollTop;
      const direction = targetOffset >= currentOffset ? 1 : -1;
      if (settings.readerMode === "paged") {
        element.scrollTo({ left: Math.max(0, targetOffset - 12), behavior: "auto" });
      } else {
        element.scrollTo({ top: Math.max(0, targetOffset - 72), behavior: "auto" });
      }
      setCurrentChapter(target);
      triggerReaderPageTurn(direction);
      finishReaderJump();
    }
  };

  const jumpToSearchResult = (result: ReaderSearchResult) => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const jumped = jumpToReaderSearchResult(element, readerSearchQuery, result.occurrenceIndex);
    if (!jumped) scrollReaderToPercent(element, result.progressPercent, settings.readerMode);
    if (jumped && settings.readerMode === "paged") {
      if (searchTimer.current) window.clearTimeout(searchTimer.current);
      searchTimer.current = window.setTimeout(() => {
        const selectionElement = window.getSelection()?.anchorNode?.parentElement;
        if (selectionElement) element.scrollTo({ left: Math.max(0, selectionElement.offsetLeft - 12), behavior: "smooth" });
        searchTimer.current = undefined;
      }, 40);
    }
    const nextProgress = readerBookProgressFromLocal(document, result.progressPercent);
    setCurrentProgress(nextProgress);
    setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
    triggerReaderPageTurn(nextProgress >= currentProgress ? 1 : -1);
    finishReaderJump();
  };

  const openReaderDrawer = (tab: ReaderDrawerTab) => {
    setReaderDrawerTab(tab);
    setReaderPanel(tab);
    setReaderControlsVisible(false);
  };

  const moveChapter = (direction: -1 | 1) => {
    if (chapterTurnPendingRef.current) return false;
    const toc = document.toc;
    if (!toc.length) {
      jumpToReaderProgress(currentProgress + direction * 4);
      return true;
    }
    const currentIndex = Math.max(0, document.renderAllChapters
      ? (currentChapter?.index ?? toc.findIndex((item) => item.id === currentChapter?.id))
      : readerChapterIndex);
    const nextIndex = Math.min(toc.length - 1, Math.max(0, currentIndex + direction));
    if (nextIndex === currentIndex) {
      setReaderNotice(direction > 0 ? "已到全书末尾" : "已到全书开头");
      return false;
    }
    if (!document.renderAllChapters) {
      chapterTurnPendingRef.current = true;
      setChapterTurnPending(true);
      if (chapterTurnTimer.current) window.clearTimeout(chapterTurnTimer.current);
      chapterTurnTimer.current = window.setTimeout(() => {
        chapterTurnPendingRef.current = false;
        setChapterTurnPending(false);
        chapterTurnTimer.current = undefined;
      }, 1200);
    }
    jumpToChapter(toc[nextIndex]);
    return true;
  };

  const getPagedStep = (element: HTMLElement) => {
    const contentEl = element.querySelector(".reader-content") as HTMLElement | null;
    const computed = contentEl ? window.getComputedStyle(contentEl) : null;
    const columnWidth = computed ? parseFloat(computed.columnWidth || "0") : 0;
    const columnGap = computed ? parseFloat(computed.columnGap || "0") : 0;
    if (Number.isFinite(columnWidth) && columnWidth > 0) {
      return Math.max(1, columnWidth + (Number.isFinite(columnGap) ? columnGap : 0));
    }
    return Math.max(1, element.clientWidth);
  };

  const snapScrollToPage = (element: HTMLElement) => {
    if (settings.readerMode !== "paged") return;
    const pageStep = getPagedStep(element);
    const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
    const page = Math.round(element.scrollLeft / pageStep);
    const target = Math.min(maxScroll, Math.max(0, page * pageStep));
    if (Math.abs(element.scrollLeft - target) > 1) {
      element.scrollTo({ left: target, behavior: "auto" });
    }
  };

  const turnReaderPage = (direction: -1 | 1) => {
    const element = scrollRef.current;
    if (!element || chapterTurnPendingRef.current) return;
    triggerReaderPageTurn(direction);
    if (settings.readerMode === "paged") {
      const pageStep = getPagedStep(element);
      const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
      const currentPage = Math.round(element.scrollLeft / pageStep);
      const targetPage = currentPage + direction;
      const atEnd = element.scrollLeft >= maxScroll - 2;
      const atStart = element.scrollLeft <= 2;
      // 到达章节边界时切换到相邻章节
      if (direction > 0 && atEnd) {
        moveChapter(1);
        setReaderControlsVisible(false);
        return;
      }
      if (direction < 0 && atStart) {
        // 已在第一章第一页时不再向前翻，避免 moveChapter no-op 导致 pendingScrollToEndRef 留下脏标记
        const currentIndex = document.currentTocIndex ?? readerChapterIndex;
        if (currentIndex <= 0) {
          element.scrollTo({ left: 0, behavior: "auto" });
          setReaderControlsVisible(false);
          return;
        }
        pendingScrollToEndRef.current = true; // 标记渲染后跳到末页
        moveChapter(-1);
        setReaderControlsVisible(false);
        return;
      }
      const target = Math.min(maxScroll, Math.max(0, targetPage * pageStep));
      element.scrollTo({ left: target, behavior: "auto" });
    } else {
      // 滚动模式：所有章节已渲染为连续内容，直接滚动即可
      const maxScroll = element.scrollHeight - element.clientHeight;
      const atEnd = element.scrollTop >= maxScroll - 2;
      const atStart = element.scrollTop <= 2;
      if (direction > 0 && atEnd) {
        setReaderNotice("已到全书末尾");
        return;
      }
      if (direction < 0 && atStart) {
        setReaderNotice("已到全书开头");
        return;
      }
      element.scrollBy({
        top: direction * element.clientHeight * 0.86,
        behavior: "auto"
      });
    }
    setReaderControlsVisible(false);
    setReaderNotice("");
  };

  const handleReaderScroll = () => {
    const element = scrollRef.current;
    if (!element) return;
    lastReaderActivityRef.current = Date.now();
    const localProgress = calculateReaderProgress(element, settings.readerMode);
    const nextProgress = readerBookProgressFromLocal(document, localProgress);
    const nextChapter = findCurrentChapter(document, element, settings.readerMode);
    setCurrentProgress(nextProgress);
    setCurrentChapter(nextChapter);
    setReaderControlsVisible(false);
    // 实时更新当前页码（paged 模式），供底部进度 chip 展示
    if (settings.readerMode === "paged") {
      const pageStep = getPagedStep(element);
      const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
      const currentPage = maxScroll <= 0 ? 0 : Math.round(element.scrollLeft / pageStep);
      const totalPages = Math.max(1, Math.round(maxScroll / pageStep) + 1);
      setCurrentPageInfo({ pageIndex: currentPage, pageCount: totalPages });
    } else {
      setCurrentPageInfo({});
    }
    if (progressSaveTimer.current) window.clearTimeout(progressSaveTimer.current);
    progressSaveTimer.current = window.setTimeout(() => {
      const extras = readerLocationExtrasFromViewport(element, document, settings.readerMode, nextChapter);
      void saveProgressRef.current(nextProgress, extras);
    }, READER_PROGRESS_SAVE_DEBOUNCE_MS);
  };

  const runBackwardAction = () => {
    settings.readerMode === "paged" ? turnReaderPage(-1) : moveChapter(-1);
  };

  const runForwardAction = () => {
    settings.readerMode === "paged" ? turnReaderPage(1) : moveChapter(1);
  };

  // 单章 TXT/Markdown 切换完成后再允许下一次跨章操作，避免连续点击使用旧章节索引。
  useEffect(() => {
    if (!chapterTurnPendingRef.current || document.currentTocIndex !== readerChapterIndex) return undefined;
    let secondFrame = 0;
    const firstFrame = window.requestAnimationFrame(() => {
      secondFrame = window.requestAnimationFrame(() => {
        chapterTurnPendingRef.current = false;
        setChapterTurnPending(false);
        if (chapterTurnTimer.current) window.clearTimeout(chapterTurnTimer.current);
        chapterTurnTimer.current = undefined;
      });
    });
    return () => {
      window.cancelAnimationFrame(firstFrame);
      if (secondFrame) window.cancelAnimationFrame(secondFrame);
    };
  }, [document.currentTocIndex, document.html, readerChapterIndex]);

  // 视口恢复必须在浏览器绘制正文前开始，避免重新进书时先闪出章节第一页。
  // 保存位置只在每本书/阅读模式首次打开时应用；跨章节后不能再次套用旧位置。
  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element || !document.html) return;
    const viewportKey = `${book.id}:${settings.readerMode}`;
    const isInitialRestore = restoredViewportKeyRef.current !== viewportKey;
    // 如果是向前翻章（需要跳到上一章末页），覆盖恢复逻辑
    if (pendingScrollToEndRef.current && settings.readerMode === "paged") {
      pendingScrollToEndRef.current = false;
      setReaderViewportReady(false);
      // 等待布局完成后再滚动到末尾
      const frame = window.requestAnimationFrame(() => {
        const pageStep = getPagedStep(element);
        const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
        const lastPage = Math.round(maxScroll / pageStep);
        element.scrollTo({ left: Math.min(maxScroll, Math.max(0, lastPage * pageStep)), behavior: "auto" });
        setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
        setReaderViewportReady(true);
      });
      return () => window.cancelAnimationFrame(frame);
    }
    // 清除可能残留的标记（模式切换、书籍切换等场景下未消费的 pendingScrollToEnd）
    pendingScrollToEndRef.current = false;
    if (!isInitialRestore) {
      // 正向跨章后从新章节第一页开始。同步归零可避免旧章节的 scrollLeft
      // 在 Android WebView 合成下一帧时留下半页文字残影。
      if (settings.readerMode === "paged") {
        element.scrollLeft = 0;
        element.scrollTop = 0;
      } else {
        element.scrollTop = 0;
        element.scrollLeft = 0;
      }
      setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
      setReaderViewportReady(true);
      return;
    }
    setReaderViewportReady(false);
    // 优先按保存的精确位置恢复：charOffset > pageIndex > progressPercent
    let localProgress = readerLocalProgressFromBook(document, currentProgress);
    const savedCharOffset = initialLocation?.text?.charOffset;
    const savedPage = initialLocation?.page;
    if (typeof savedCharOffset === "number" && document.fullText) {
      localProgress = readerChapterProgressFromCharOffset(document, savedCharOffset);
    } else if (settings.readerMode === "paged" && typeof savedPage?.pageIndex === "number") {
      const pageStep = getPagedStep(element);
      const maxScroll = Math.max(0, element.scrollWidth - element.clientWidth);
      const totalPages = Math.max(1, Math.round(maxScroll / pageStep) + 1);
      localProgress = totalPages <= 1 ? 0 : (savedPage.pageIndex / (totalPages - 1)) * 100;
    }
    return restoreReaderViewportAfterLayout(element, localProgress, settings.readerMode, () => {
      const exactApplied = applyReaderExactLocationOffsets(element, settings.readerMode, initialLocation);
      if (settings.readerMode === "paged" && !exactApplied) snapScrollToPage(element);
      setCurrentChapter(findCurrentChapter(document, element, settings.readerMode));
      restoredViewportKeyRef.current = viewportKey;
      setReaderViewportReady(true);
    });
  }, [book.id, document.html, settings.readerMode]);

  return {
    pendingScrollToEndRef,
    readerViewportReady,
    chapterTurnPending,
    pageTurnDirection,
    triggerReaderPageTurn,
    swipeDirection,
    setSwipeDirection,
    currentPageInfo,
    setCurrentPageInfo: updateCurrentPageInfo,
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
  };
}
