import { useCallback, useEffect, useMemo, useRef, useState, type WheelEvent } from "react";
import ePub from "epubjs";
import type { Book, Location as EpubLocation, Rendition } from "epubjs";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import { useReaderExcerpt } from "@/features/library/useReaderExcerpt";
import type { ExcerptBuildContext } from "@/features/library/useReaderExcerpt";
import { EpubEmptyState } from "@/features/library/epub-reader/EpubEmptyState";
import { EpubPageTurnButtons } from "@/features/library/epub-reader/EpubPageTurnButtons";
import { EpubReaderToolbar } from "@/features/library/epub-reader/EpubReaderToolbar";
import { EpubSelectionToolbar, type SelectionToolbarState } from "@/features/library/epub-reader/EpubSelectionToolbar";
import { EpubSettingsDrawer } from "@/features/library/epub-reader/EpubSettingsDrawer";
import { EpubSidePanel } from "@/features/library/epub-reader/EpubSidePanel";
import { findCurrentTocItem, normalizeEpubHref } from "@/features/library/toc/current";
import { getHighlightsByBook, saveHighlight, deleteHighlight as removeHighlightById, getBookmarksByBook, saveBookmark, deleteBookmark as removeBookmarkById } from "@/services/annotation-service";
import { saveEpubLocation } from "@/services/reader-service";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { ExcerptResult, ExcerptTarget, BookmarkItem, HighlightColor, HighlightItem, ReadingLocation } from "@/types/library";
import { readerBackgroundColor } from "@/utils/format";
import { getConverter } from "@/utils/text-conversion";

import { HIGHLIGHT_COLOR_FILL } from "@/features/library/reader/annotation-constants";
import { Spinner } from "@/components/ui";
import {
  applyReaderTheme,
  displayWithTimeout,
  getDisplayedEpubLocation,
  hrefMatchesLocation,
  initialEpubLocation,
  locationFromEpub,
  shouldIgnoreKeydown,
  type SidePanelTab
} from "@/features/library/epub-reader/epub-engine";

export function EpubReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const epubUrl = useLibraryStore((state) => state.activeEpubUrl);
  const targetHref = useLibraryStore((state) => state.activeEpubTargetHref);
  const progress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const settings = useLibraryStore((state) => state.readerSettings);
  const setProgress = useLibraryStore((state) => state.setProgress);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setError = useAppStore((state) => state.setError);
  const setScreen = useAppStore((state) => state.setScreen);
  const showToast = useUIStore((state) => state.showToast);
  const viewerRef = useRef<HTMLDivElement>(null);
  const bookRef = useRef<Book | undefined>(undefined);
  const renditionRef = useRef<Rendition | undefined>(undefined);
  const progressTimerRef = useRef<number | undefined>(undefined);
  const initialTargetHrefRef = useRef<string | undefined>(undefined);
  const canSaveProgressRef = useRef(true);
  const pendingInitialLocationRef = useRef<ReadingLocation | undefined>(undefined);
  const wheelTurnLockRef = useRef<number | undefined>(undefined);
  const settingsRef = useRef(settings);
  const relocatedDebounceRef = useRef<number | undefined>(undefined);
  const toc = activeBook?.epub?.toc ?? [];
  const baseLocation = useMemo(() => (activeBook ? initialEpubLocation(activeBook, progress?.currentLocation) : undefined), [activeBook, progress?.currentLocation]);
  const locationRef = useRef<ReadingLocation | undefined>(baseLocation);
  const [loading, setLoading] = useState(true);
  const [tocCollapsed, setTocCollapsed] = useState(false);
  const [settingsDrawerOpen, setSettingsDrawerOpen] = useState(false);
  const [highlights, setHighlights] = useState<HighlightItem[]>([]);
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const [sidePanelTab, setSidePanelTab] = useState<SidePanelTab>("toc");
  const excerpt = useReaderExcerpt();
  const highlightsRef = useRef<HighlightItem[]>([]);
  const viewerContainerRef = useRef<HTMLDivElement>(null);
  const [selectionToolbar, setSelectionToolbar] = useState<{
    visible: boolean;
    x: number;
    y: number;
    cfiRange: string;
    text: string;
  } | null>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const toolbarOffsetRef = useRef({ x: 0, y: 0 });

  useEffect(() => {
    locationRef.current = baseLocation;
  }, [baseLocation]);

  const getCurrentLocation = useCallback(() => locationRef.current, []);
  const { recordInteraction, endTracking } = useReadingSessionTracker(viewerRef, getCurrentLocation);

  // --- Annotations ---
  const loadAnnotations = useCallback(async (bookId: string) => {
    try {
      const [hl, bm] = await Promise.all([
        getHighlightsByBook(bookId),
        getBookmarksByBook(bookId),
      ]);
      setHighlights(hl);
      highlightsRef.current = hl;
      setBookmarks(bm);
    } catch {
      // ignore
    }
  }, []);

  const renderHighlightsOnRendition = useCallback((rend: Rendition, items: HighlightItem[]) => {
    for (const hl of items) {
      if (!hl.cfiRange) continue;
      try {
        (rend.annotations as any).highlight(
          hl.cfiRange,
          {},
          () => {},
          `hl-${hl.id}`,
          {
            fill: HIGHLIGHT_COLOR_FILL[hl.color] || HIGHLIGHT_COLOR_FILL.yellow,
            "fill-opacity": "1",
            "mix-blend-mode": "multiply",
          }
        );
      } catch {
        // CFI not in current spine, ignore
      }
    }
  }, []);

  const addHighlight = useCallback(async (cfiRange: string, text: string, color: HighlightColor) => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    if (!bookId || !text.trim()) return;
    const toc2 = useLibraryStore.getState().activeBook?.epub?.toc ?? [];
    const loc = locationRef.current;
    const chapterTitle = toc2.find((item) => normalizeEpubHref(item.href) === normalizeEpubHref(loc?.epub?.href))?.label;
    const now = new Date().toISOString();
    const item: HighlightItem = {
      id: crypto.randomUUID(),
      bookId,
      cfiRange,
      text: text.slice(0, 2000),
      color,
      chapterTitle,
      createdAt: now,
      updatedAt: now,
    };
    const saved = await saveHighlight(item);
    setHighlights((prev) => {
      const next = [...prev, saved];
      highlightsRef.current = next;
      return next;
    });
    const rendition = renditionRef.current;
    if (rendition) renderHighlightsOnRendition(rendition, [saved]);
    showToast({ tone: "success", title: "已添加高亮", body: text.slice(0, 50) });
  }, [renderHighlightsOnRendition, showToast]);

  const removeHighlight = useCallback(async (id: string) => {
    await removeHighlightById(id);
    setHighlights((prev) => {
      const next = prev.filter((h) => h.id !== id);
      highlightsRef.current = next;
      return next;
    });
    // Remove annotation from rendition
    const rendition = renditionRef.current;
    if (rendition) {
      try {
        (rendition.annotations as any).remove(`hl-${id}`, "highlight");
      } catch {
        // ignore
      }
    }
  }, []);

  const addBookmark = useCallback(async () => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    const loc = locationRef.current;
    if (!bookId || !loc) return;
    const toc2 = useLibraryStore.getState().activeBook?.epub?.toc ?? [];
    const chapterTitle = toc2.find((item) => normalizeEpubHref(item.href) === normalizeEpubHref(loc?.epub?.href))?.label;
    const item: BookmarkItem = {
      id: crypto.randomUUID(),
      bookId,
      label: chapterTitle || `书签 ${Math.round((loc.progressPercent ?? 0) * 100)}%`,
      cfi: loc.epub?.cfi,
      href: loc.epub?.href,
      progressPercent: loc.progressPercent,
      chapterTitle,
      createdAt: new Date().toISOString(),
    };
    const saved = await saveBookmark(item);
    setBookmarks((prev) => [saved, ...prev]);
    showToast({ tone: "success", title: "已添加书签", body: item.label });
  }, [showToast]);

  const toggleBookmark = useCallback(async () => {
    const loc = locationRef.current;
    const currentCfi = loc?.epub?.cfi;
    const existing = bookmarks.find((b) => b.cfi === currentCfi);
    if (existing) {
      await removeBookmarkById(existing.id);
      setBookmarks((prev) => prev.filter((b) => b.id !== existing.id));
      showToast({ tone: "success", title: "已移除书签" });
    } else {
      await addBookmark();
    }
  }, [addBookmark, bookmarks, showToast]);

  const jumpToHighlight = useCallback(async (hl: HighlightItem) => {
    const rendition = renditionRef.current;
    if (!rendition || !hl.cfiRange) return;
    try {
      await rendition.display(hl.cfiRange);
      handleActivityRef.current();
    } catch {
      // ignore
    }
  }, []);

  const jumpToBookmark = useCallback(async (bm: BookmarkItem) => {
    const rendition = renditionRef.current;
    if (!rendition) return;
    try {
      const target = bm.cfi || bm.href;
      if (target) await rendition.display(target);
      handleActivityRef.current();
    } catch {
      // ignore
    }
  }, []);

  const isCurrentBookmarked = useMemo(() => {
    const cfi = locationRef.current?.epub?.cfi;
    return cfi ? bookmarks.some((b) => b.cfi === cfi) : false;
  }, [bookmarks, locationRef.current?.epub?.cfi]);

  const flushProgress = useCallback(async () => {
    const book = useLibraryStore.getState().activeBook;
    const location = locationRef.current;
    if (!book || book.format !== "epub" || !location) return undefined;
    try {
      const saved = await saveEpubLocation({ bookId: book.id, location });
      setProgress(saved);
      return saved;
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
      return undefined;
    }
  }, [setError, setProgress]);

  const scheduleProgressSave = useCallback(() => {
    const interval = useLibraryStore.getState().readerSettings?.tracking.progressSaveIntervalMs ?? 2_000;
    if (progressTimerRef.current) window.clearTimeout(progressTimerRef.current);
    progressTimerRef.current = window.setTimeout(() => void flushProgress(), interval);
  }, [flushProgress]);

  const handleActivity = useCallback(() => {
    recordInteraction();
    scheduleProgressSave();
  }, [recordInteraction, scheduleProgressSave]);

  useEffect(() => {
    settingsRef.current = settings;
    flushProgressRef.current = flushProgress;
    handleActivityRef.current = handleActivity;
    updateLocationRef.current = updateLocation;
  });

  const updateLocation = useCallback(
    async (value?: EpubLocation) => {
      const book = useLibraryStore.getState().activeBook;
      if (!book || book.format !== "epub" || !locationRef.current) return;
      if (!value) {
        const rendition = renditionRef.current;
        const currentLocation = rendition ? await getDisplayedEpubLocation(rendition) : undefined;
        if (currentLocation) value = currentLocation;
      }
      const nextLocation = locationFromEpub(bookRef.current, book, value, locationRef.current);
      if (!canSaveProgressRef.current) {
        pendingInitialLocationRef.current = nextLocation;
        return;
      }
      locationRef.current = nextLocation;
      handleActivity();
    },
    [handleActivity]
  );

  const flushProgressRef = useRef(flushProgress);
  const handleActivityRef = useRef(handleActivity);
  const updateLocationRef = useRef(updateLocation);

  const jumpToToc = useCallback(
    async (href: string) => {
      const rendition = renditionRef.current;
      if (!rendition) return;
      try {
        await rendition.display(href);
        const currentLocation = await Promise.resolve(rendition.currentLocation?.());
        if (currentLocation) {
          const currentEpubLocation = "start" in currentLocation ? currentLocation : { start: currentLocation };
          updateLocation(currentEpubLocation as EpubLocation);
        } else {
          handleActivity();
        }
        await flushProgress();
      } catch (error) {
        setError(error instanceof Error ? error.message : String(error));
      }
    },
    [flushProgress, handleActivity, setError, updateLocation]
  );

  const turnPage = useCallback(
    async (direction: "prev" | "next") => {
      const rendition = renditionRef.current;
      if (!rendition) return;
      try {
        if (direction === "next") await Promise.resolve(rendition.next());
        else await Promise.resolve(rendition.prev());
        handleActivity();
        const currentLocation = await Promise.resolve(rendition.currentLocation?.());
        if (currentLocation) {
          const currentEpubLocation = "start" in currentLocation ? currentLocation : { start: currentLocation };
          updateLocation(currentEpubLocation as EpubLocation);
        }
      } catch (error) {
        setError(error instanceof Error ? error.message : String(error));
      }
    },
    [handleActivity, setError, updateLocation]
  );

  const handleWheelPageTurn = useCallback(
    (event: WheelEvent<HTMLDivElement>) => {
      if (Math.abs(event.deltaY) < 18 || wheelTurnLockRef.current) return;
      event.preventDefault();
      wheelTurnLockRef.current = window.setTimeout(() => {
        wheelTurnLockRef.current = undefined;
      }, 360);
      void turnPage(event.deltaY > 0 ? "next" : "prev");
    },
    [turnPage]
  );

  useEffect(() => {
    if (!activeBook || activeBook.format !== "epub" || !epubUrl || !settingsRef.current || !viewerRef.current) return;
    let cancelled = false;
    setLoading(true);
    const book = ePub(epubUrl, { openAs: "epub" });
    const rendition = book.renderTo(viewerRef.current, {
      width: "100%",
      height: "100%",
      flow: "paginated",
      spread: "none"
    });
    bookRef.current = book;
    renditionRef.current = rendition;
    const initialTargetHref = useLibraryStore.getState().activeEpubTargetHref;
    const latestProgress = useLibraryStore.getState().progress[activeBook.id];
    const currentSettings = settingsRef.current;
    const savedEpubLocation = currentSettings.restoreLastPosition ? latestProgress?.currentLocation?.epub : undefined;
    const restoreCfi = savedEpubLocation?.cfi;
    const restoreHref = savedEpubLocation?.href;
    initialTargetHrefRef.current = initialTargetHref;
    canSaveProgressRef.current = false;
    pendingInitialLocationRef.current = undefined;
    applyReaderTheme(rendition, currentSettings);

    // 繁简转换 hook：在每页内容渲染后转换文本节点
    rendition.hooks.content.register(async (contents: any) => {
      const mode = settingsRef.current?.textConversion;
      if (!mode || mode === "none") return;
      try {
        const converter = await getConverter(mode as "s2t" | "t2s");
        const body = contents.document?.body;
        if (!body) return;
        const walker = contents.document.createTreeWalker(body, NodeFilter.SHOW_TEXT, null);
        let node: Node | null;
        while ((node = walker.nextNode())) {
          if (node.textContent) node.textContent = converter(node.textContent);
        }
      } catch {
        // ignore conversion errors
      }
    });

    rendition.on("relocated", (location: EpubLocation) => {
      if (relocatedDebounceRef.current) window.clearTimeout(relocatedDebounceRef.current);
      relocatedDebounceRef.current = window.setTimeout(() => {
        relocatedDebounceRef.current = undefined;
        updateLocationRef.current(location);
      }, 50);
    });
    rendition.on("selected", (cfiRange: string, contents: any) => {
      handleActivityRef.current();
      (window as any).__epubSelectedCfiRange = cfiRange;
      // Calculate selection position for floating toolbar
      try {
        const selection = contents?.window?.getSelection?.();
        if (selection && selection.rangeCount > 0) {
          const range = selection.getRangeAt(0);
          const rect = range.getBoundingClientRect();
          const iframeEl = contents.document?.defaultView?.frameElement;
          if (iframeEl && viewerContainerRef.current) {
            const iframeRect = iframeEl.getBoundingClientRect();
            const viewerRect = viewerContainerRef.current.getBoundingClientRect();
            const text = (selection.toString() || "").trim().slice(0, 2000);
            toolbarOffsetRef.current = { x: 0, y: 0 };
            setSelectionToolbar({
              visible: true,
              x: iframeRect.left - viewerRect.left + rect.left + rect.width / 2,
              y: iframeRect.top - viewerRect.top + rect.top - 8,
              cfiRange,
              text,
            });
          }
        }
      } catch {
        // ignore coordinate errors
      }
    });
    rendition.on("click", () => {
      handleActivityRef.current();
      setSelectionToolbar(null);
    });
    rendition.on("rendered", () => {
      // Restore highlights on each page render
      const currentItems = highlightsRef.current;
      if (currentItems.length > 0) {
        renderHighlightsOnRendition(rendition, currentItems);
      }
    });

    void (async () => {
      const commitDisplayedLocation = async (expectedHref?: string) => {
        const displayedLocation = await getDisplayedEpubLocation(rendition);
        if (displayedLocation && locationRef.current) {
          pendingInitialLocationRef.current = locationFromEpub(book, activeBook, displayedLocation, locationRef.current);
        }
        if (!hrefMatchesLocation(pendingInitialLocationRef.current, expectedHref)) return false;
        if (pendingInitialLocationRef.current) {
          locationRef.current = pendingInitialLocationRef.current;
          pendingInitialLocationRef.current = undefined;
          return true;
        }
        return false;
      };

      try {
        await book.ready;
        void book.locations.generate(1600).catch(() => undefined);
        if (cancelled) return;
        // Load annotations for this book
        loadAnnotations(activeBook.id);
        const restoreTarget = initialTargetHref || restoreHref || restoreCfi || undefined;
        const expectedHref = initialTargetHref || restoreHref;
        const result = await displayWithTimeout(rendition, restoreTarget);
        let committed = await commitDisplayedLocation(expectedHref);
        if (!cancelled && !initialTargetHref && restoreHref && restoreTarget !== restoreHref && (!committed || result === "timeout")) {
          pendingInitialLocationRef.current = undefined;
          await displayWithTimeout(rendition, restoreHref);
          committed = await commitDisplayedLocation(restoreHref);
        }
        if (!committed && !restoreTarget) {
          await commitDisplayedLocation(undefined);
        }
        if (!cancelled && initialTargetHref && useLibraryStore.getState().activeEpubTargetHref === initialTargetHref) {
          useLibraryStore.setState({ activeEpubTargetHref: undefined });
        }
      } catch (error) {
        setError(error instanceof Error ? error.message : String(error));
      } finally {
        initialTargetHrefRef.current = undefined;
        pendingInitialLocationRef.current = undefined;
        canSaveProgressRef.current = true;
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      if (progressTimerRef.current) window.clearTimeout(progressTimerRef.current);
      if (wheelTurnLockRef.current) window.clearTimeout(wheelTurnLockRef.current);
      if (relocatedDebounceRef.current) window.clearTimeout(relocatedDebounceRef.current);
      void flushProgressRef.current();
      rendition.destroy();
      book.destroy();
      renditionRef.current = undefined;
      bookRef.current = undefined;
      pendingInitialLocationRef.current = undefined;
      canSaveProgressRef.current = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeBook?.id, epubUrl]);

  useEffect(() => {
    const rendition = renditionRef.current;
    if (!targetHref || !rendition || targetHref === initialTargetHrefRef.current) return;
    let cancelled = false;

    void (async () => {
      try {
        await rendition.display(targetHref);
        if (cancelled) return;
        const currentLocation = await Promise.resolve(rendition.currentLocation?.());
        if (currentLocation) {
          const currentEpubLocation = "start" in currentLocation ? currentLocation : { start: currentLocation };
          updateLocation(currentEpubLocation as EpubLocation);
        } else {
          handleActivity();
        }
        await flushProgress();
        if (!cancelled && useLibraryStore.getState().activeEpubTargetHref === targetHref) {
          useLibraryStore.setState({ activeEpubTargetHref: undefined });
        }
      } catch (error) {
        setError(error instanceof Error ? error.message : String(error));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [flushProgress, handleActivity, setError, targetHref, updateLocation]);

  useEffect(() => {
    if (!settings || !renditionRef.current) return;
    applyReaderTheme(renditionRef.current, settings);
    viewerRef.current?.style.setProperty("--epub-reader-bg", readerBackgroundColor(settings.readerBackground));
  }, [settings?.fontSize, settings?.lineHeight, settings?.paragraphSpacing, settings?.letterSpacing, settings?.readerBackground, settings?.epubStyleMode, settings?.fontFamily]);

  // 繁简转换设置变化时重新渲染当前页
  useEffect(() => {
    const rendition = renditionRef.current;
    if (!rendition) return;
    try {
      const loc = rendition.currentLocation?.();
      const cfi = loc && "start" in loc ? (loc as any).start?.cfi : (loc as any)?.cfi;
      if (cfi) void rendition.display(cfi);
    } catch {
      // ignore
    }
  }, [settings?.textConversion]);

  useEffect(() => {
    const handleKeydown = (event: KeyboardEvent) => {
      if (shouldIgnoreKeydown(event) || !renditionRef.current) return;
      if (event.key === "ArrowRight" || event.key === "PageDown" || event.key === " ") {
        event.preventDefault();
        void turnPage("next");
      }
      if (event.key === "ArrowLeft" || event.key === "PageUp") {
        event.preventDefault();
        void turnPage("prev");
      }
      if (event.key === "Escape") {
        setSelectionToolbar(null);
      }
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [turnPage]);

  useEffect(() => {
    if (!selectionToolbar?.visible) return;
    const handleClickOutside = () => {
      setSelectionToolbar(null);
      setShowColorPicker(false);
    };
    const timer = window.setTimeout(() => {
      document.addEventListener("click", handleClickOutside);
    }, 100);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener("click", handleClickOutside);
    };
  }, [selectionToolbar?.visible]);

  if (!activeBook || activeBook.format !== "epub" || !settings || !epubUrl) {
    return <EpubEmptyState onBackToLibrary={() => setScreen("library")} onBackToHome={() => setScreen("projects")} />;
  }

  const progressPercent = Math.round((locationRef.current?.progressPercent ?? progress?.progressPercent ?? 0) * 100);
  const currentTocItem = findCurrentTocItem(toc, locationRef.current?.epub?.href);
  // 已读派生：目录顺序中位于当前项之前的章节（零存储；跳章翻阅会把中间章节计为已读，接受该近似）
  const currentTocIndex = currentTocItem ? toc.findIndex((item) => item.id === currentTocItem.id) : -1;
  const tocReadIds = useMemo(
    () => (currentTocIndex > 0 ? new Set(toc.slice(0, currentTocIndex).map((item) => item.id)) : new Set<string>()),
    [toc, currentTocIndex]
  );
  const tocSummary = currentTocIndex >= 0 ? `已读 ${currentTocIndex}/${toc.length}` : undefined;

  const dragHandlersRef = useRef<{ onMove?: (ev: MouseEvent) => void; onUp?: () => void } | null>(null);
  useEffect(() => {
    return () => {
      const handlers = dragHandlersRef.current;
      if (handlers?.onMove) document.removeEventListener("mousemove", handlers.onMove);
      if (handlers?.onUp) document.removeEventListener("mouseup", handlers.onUp);
    };
  }, []);

  const handleToolbarDragStart = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    const startX = e.clientX;
    const startY = e.clientY;
    const { x: baseX, y: baseY } = toolbarOffsetRef.current;
    const onMove = (ev: MouseEvent) => {
      toolbarOffsetRef.current = { x: baseX + (ev.clientX - startX), y: baseY + (ev.clientY - startY) };
      setSelectionToolbar((prev) => prev ? { ...prev } : null);
    };
    const onUp = () => {
      document.removeEventListener("mousemove", onMove);
      document.removeEventListener("mouseup", onUp);
      dragHandlersRef.current = null;
    };
    dragHandlersRef.current = { onMove, onUp };
    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  };

  const selectedTextFromEpub = (): string | undefined => {
    const fallback = window.getSelection()?.toString().trim();
    if (fallback) return fallback.slice(0, 2000);
    const renditionLike = renditionRef.current as unknown as { getContents?: () => Array<{ window?: Window }> };
    const contents = renditionLike.getContents?.() ?? [];
    for (const content of contents) {
      const selected = content.window?.getSelection?.()?.toString().trim();
      if (selected) return selected.slice(0, 2000);
    }
    return undefined;
  };

  const openExcerptFromSelection = useCallback(() => {
    const text = selectedTextFromEpub();
    if (!text) {
      showToast({ tone: "warning", title: "没有选中文字", body: "请先在书中选中一段文字再摘录。" });
      return;
    }
    const location = locationRef.current;
    const ctx: ExcerptBuildContext = {
      bookId: activeBook.id,
      bookTitle: activeBook.title,
      bookAuthor: activeBook.author,
      format: "epub",
      chapterTitle: currentTocItem?.label,
      progressPercent: location?.progressPercent,
      excerpt: text,
      href: location?.epub?.href,
      cfi: location?.epub?.cfi
    };
    void excerpt.openPicker(ctx);
    setSelectionToolbar(null);
    setShowColorPicker(false);
  }, [activeBook, currentTocItem?.label, excerpt, showToast]);

  const handleExcerptResult = useCallback(
    (result: ExcerptResult, target: ExcerptTarget) => {
      if (result.success) {
        showToast({
          tone: "success",
          title: target.kind === "inbox" ? "已摘录到收件箱" : "已保存为资料卡",
          body: "选文和来源已保存，可继续阅读。"
        });
      } else {
        showToast({ tone: "error", title: "摘录失败", body: result.error });
      }
    },
    [showToast]
  );

  return (
    <div className="reader-root grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <EpubReaderToolbar
        title={activeBook.title}
        progressPercent={progressPercent}
        totalReadingTimeMs={progress?.totalReadingTimeMs}
        isCurrentBookmarked={isCurrentBookmarked}
        hasSelection={selectionToolbar?.visible === true}
        onToggleBookmark={() => void toggleBookmark()}
        onHighlightSelection={async () => {
          const cfiRange = (window as any).__epubSelectedCfiRange;
          const text = selectedTextFromEpub();
          if (cfiRange && text) {
            await addHighlight(cfiRange, text, "yellow");
          } else {
            showToast({ tone: "error", title: "无法高亮", body: "请先在书中选中一段文字。" });
          }
        }}
        onExcerpt={() => void openExcerptFromSelection()}
        onOpenSettings={async () => {
          await flushProgress();
          setSettingsDrawerOpen(true);
        }}
        onOpenStats={async () => {
          await flushProgress();
          await endTracking("leave-reader");
          setScreen("stats");
        }}
        onBackToLibrary={async () => {
          await flushProgress();
          await endTracking("leave-reader");
          setScreen("library");
        }}
        onBackToHome={async () => {
          await flushProgress();
          await endTracking("leave-reader");
          setScreen("projects");
        }}
      />

      <div className={`grid min-h-0 ${tocCollapsed ? "grid-cols-[1fr_56px]" : "grid-cols-[1fr_320px]"}`}>
        <div ref={viewerContainerRef} className="relative min-h-0 overflow-hidden" onWheel={handleWheelPageTurn}>
          {loading && (
            <div className="absolute inset-0 z-10 flex items-center justify-center gap-2 bg-paper-panel/80 text-sm text-paper-muted">
              <Spinner size={18} /> 正在打开 EPUB...
            </div>
          )}
          <EpubSelectionToolbar
            toolbar={selectionToolbar as SelectionToolbarState | null}
            toolbarOffset={toolbarOffsetRef.current}
            showColorPicker={showColorPicker}
            onToggleColorPicker={() => setShowColorPicker((v) => !v)}
            onDragStart={handleToolbarDragStart}
            onHighlight={async (color) => {
              if (selectionToolbar) await addHighlight(selectionToolbar.cfiRange, selectionToolbar.text, color);
              setSelectionToolbar(null);
              setShowColorPicker(false);
            }}
            onCopy={async (text) => {
              try {
                await navigator.clipboard.writeText(text);
                showToast({ tone: "success", title: "已复制", body: `${text.length} 字` });
              } catch {
                showToast({ tone: "error", title: "复制失败" });
              }
              setSelectionToolbar(null);
            }}
            onExcerpt={() => void openExcerptFromSelection()}
            onClose={() => {
              try {
                const contents = (renditionRef.current as any)?.getContents?.();
                contents?.forEach((c: any) => c.window?.getSelection?.()?.removeAllRanges());
              } catch { /* ignore */ }
              setSelectionToolbar(null);
              setShowColorPicker(false);
            }}
          />
          <div
            ref={viewerRef}
            className="h-full w-full px-8 py-6"
            style={{ background: readerBackgroundColor(settings.readerBackground) }}
            onPointerDown={handleActivity}
          />
          <EpubPageTurnButtons onPrev={() => void turnPage("prev")} onNext={() => void turnPage("next")} />
        </div>

        <EpubSidePanel
          tocCollapsed={tocCollapsed}
          onExpand={() => setTocCollapsed(false)}
          onCollapse={() => setTocCollapsed(true)}
          sidePanelTab={sidePanelTab}
          onTabChange={setSidePanelTab}
          progressPercent={progressPercent}
          currentTocItem={currentTocItem}
          toc={toc}
          readIds={tocReadIds}
          tocSummary={tocSummary}
          onJumpToToc={(href) => void jumpToToc(href)}
          highlights={highlights}
          onRemoveHighlight={(id) => void removeHighlight(id)}
          onJumpToHighlight={(hl) => void jumpToHighlight(hl)}
          onHighlightsChange={(next) => {
            highlightsRef.current = next;
            setHighlights(next);
          }}
          bookmarks={bookmarks}
          onAddBookmark={() => void addBookmark()}
          onJumpToBookmark={(bm) => void jumpToBookmark(bm)}
          onRemoveBookmark={async (id) => {
            await removeBookmarkById(id);
            setBookmarks((prev) => prev.filter((b) => b.id !== id));
          }}
        />
      </div>
      <EpubSettingsDrawer
        open={settingsDrawerOpen}
        settings={settings}
        onClose={() => setSettingsDrawerOpen(false)}
        onSettingsChange={setReaderSettings}
        onAfterChange={() => {
          handleActivity();
          void updateLocation();
        }}
      />
      {excerpt.isPickerOpen && excerpt.pendingSource && (
        <ExcerptPicker
          source={excerpt.pendingSource}
          projects={excerpt.projects}
          isSubmitting={excerpt.isSubmitting}
          onClose={excerpt.closePicker}
          onSubmit={excerpt.submit}
          onResult={handleExcerptResult}
        />
      )}
    </div>
  );
}
