import { useCallback, useEffect, useMemo, useRef, useState, type WheelEvent } from "react";
import { ArrowLeft, BarChart3, BookOpen, Bookmark, BookmarkCheck, ChevronLeft, ChevronRight, Copy, GripHorizontal, Highlighter, Lightbulb, List, Settings, Trash2, X } from "lucide-react";
import ePub from "epubjs";
import type { Book, Location as EpubLocation, Rendition } from "epubjs";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { createInspiration } from "@/services/inspiration-service";
import { getHighlightsByBook, saveHighlight, deleteHighlight as removeHighlightById, getBookmarksByBook, saveBookmark, deleteBookmark as removeBookmarkById } from "@/services/annotation-service";
import { saveEpubLocation, updateReaderSettings } from "@/services/reader-service";
import { resetReaderSettings } from "@/services/settings-service";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { InspirationItem } from "@/types/inspiration";
import type { BookmarkItem, HighlightColor, HighlightItem, LibraryBook, ReaderSettings, ReadingLocation } from "@/types/library";
import { formatDuration, readerShellClass, readerBackgroundColor, readerTextColor } from "@/utils/format";
import { getConverter } from "@/utils/text-conversion";

const EPUB_INITIAL_DISPLAY_TIMEOUT_MS = 8_000;
const renditionRegisteredThemes = new WeakMap<Rendition, Set<string>>();

const HIGHLIGHT_COLOR_FILL: Record<HighlightColor, string> = {
  yellow: "rgba(255, 235, 59, 0.4)",
  red: "rgba(244, 67, 54, 0.3)",
  green: "rgba(76, 175, 80, 0.3)",
  blue: "rgba(33, 150, 243, 0.3)",
  purple: "rgba(156, 39, 176, 0.3)",
};

const HIGHLIGHT_COLORS: { value: HighlightColor; label: string; tw: string; hex: string }[] = [
  { value: "yellow", label: "黄色", tw: "bg-yellow-300", hex: "#fde047" },
  { value: "red", label: "红色", tw: "bg-red-300", hex: "#fca5a5" },
  { value: "green", label: "绿色", tw: "bg-green-300", hex: "#86efac" },
  { value: "blue", label: "蓝色", tw: "bg-blue-300", hex: "#93c5fd" },
  { value: "purple", label: "紫色", tw: "bg-purple-300", hex: "#d8b4fe" },
];

type SidePanelTab = "toc" | "highlights" | "bookmarks";

function initialEpubLocation(book: LibraryBook, existing?: ReadingLocation): ReadingLocation {
  if (existing?.format === "epub" && existing.mode === "epub-cfi") return existing;
  return {
    format: "epub",
    mode: "epub-cfi",
    progressPercent: existing?.progressPercent ?? 0,
    precision: "estimated",
    epub: {},
    sourceVersion: {
      fileSize: book.size
    },
    updatedAt: new Date().toISOString()
  };
}

function safePercent(value: unknown, fallback = 0): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.min(1, value)) : fallback;
}

function normalizeEpubHref(value?: string): string | undefined {
  return value?.split("#")[0];
}

function hrefMatchesLocation(location: ReadingLocation | undefined, expectedHref?: string): boolean {
  if (!expectedHref) return true;
  const actual = normalizeEpubHref(location?.epub?.href);
  const expected = normalizeEpubHref(expectedHref);
  return Boolean(actual && expected && actual === expected);
}

async function displayWithTimeout(rendition: Rendition, target?: string): Promise<"displayed" | "timeout"> {
  let timeoutId: number | undefined;
  try {
    return await Promise.race([
      Promise.resolve(rendition.display(target)).then(() => "displayed" as const),
      new Promise<"timeout">((resolve) => {
        timeoutId = window.setTimeout(() => resolve("timeout"), EPUB_INITIAL_DISPLAY_TIMEOUT_MS);
      })
    ]);
  } finally {
    if (timeoutId) window.clearTimeout(timeoutId);
  }
}

async function getDisplayedEpubLocation(rendition: Rendition): Promise<EpubLocation | undefined> {
  const currentLocation = await Promise.resolve(rendition.currentLocation?.());
  if (!currentLocation) return undefined;
  return ("start" in currentLocation ? currentLocation : { start: currentLocation }) as EpubLocation;
}

function locationFromEpub(book: Book | undefined, libraryBook: LibraryBook, value: EpubLocation | undefined, fallback: ReadingLocation): ReadingLocation {
  const start = value?.start;
  const cfi = typeof start?.cfi === "string" ? start.cfi : fallback.epub?.cfi;
  const href = typeof start?.href === "string" ? start.href : fallback.epub?.href;
  let progressPercent = safePercent(start?.percentage, fallback.progressPercent);
  if (
    progressPercent === 0 &&
    fallback.progressPercent > 0 &&
    normalizeEpubHref(href) === normalizeEpubHref(fallback.epub?.href)
  ) {
    progressPercent = fallback.progressPercent;
  }
  if (book && cfi) {
    try {
      const cfiProgressPercent = safePercent(book.locations.percentageFromCfi(cfi), progressPercent);
      progressPercent =
        cfiProgressPercent === 0 && progressPercent > 0 && normalizeEpubHref(href) === normalizeEpubHref(fallback.epub?.href)
          ? progressPercent
          : cfiProgressPercent;
    } catch {
      progressPercent = safePercent(start?.percentage, progressPercent);
    }
  }
  return {
    format: "epub",
    mode: "epub-cfi",
    progressPercent,
    precision: cfi ? "exact" : "estimated",
    epub: {
      cfi,
      href,
      spineIndex: typeof start?.index === "number" ? start.index : fallback.epub?.spineIndex
    },
    page: start?.displayed
      ? {
          pageIndex: Math.max(0, start.displayed.page - 1),
          pageCount: Math.max(1, start.displayed.total)
        }
      : fallback.page,
    sourceVersion: {
      fileSize: libraryBook.size
    },
    updatedAt: new Date().toISOString()
  };
}

function applyReaderTheme(rendition: Rendition, settings: ReaderSettings): void {
  const isPublisher = settings.epubStyleMode === "publisher";
  const themeName = isPublisher
    ? settings.readerBackground === "night"
      ? "publisher-preserve-night"
      : "publisher-preserve"
    : `novel-workbench-${settings.readerBackground}`;

  let registered = renditionRegisteredThemes.get(rendition);
  if (!registered) {
    registered = new Set();
    renditionRegisteredThemes.set(rendition, registered);
  }

  const textColor = readerTextColor(settings.readerBackground);
  if (!registered.has(themeName)) {
    const rules: Record<string, Record<string, string>> = {
      body: { background: "transparent !important" }
    };
    if (isPublisher && settings.readerBackground === "night") {
      rules["body, p, div, span, section, article, h1, h2, h3, h4, h5, h6, li, td, th, blockquote"] = {
        color: `${textColor} !important`
      };
      rules.a = { color: "#e0b07b !important" };
    } else if (!isPublisher) {
      rules.body = { color: `${textColor} !important`, background: "transparent !important" };
      rules.a = { color: `${textColor} !important` };
    }
    rendition.themes.register(themeName, rules);
    registered.add(themeName);
  }

  // Register paragraph spacing theme (needs to be re-registered when value changes)
  const paraSpacingThemeName = `para-spacing-${settings.paragraphSpacing ?? 1.0}`;
  if (!registered.has(paraSpacingThemeName)) {
    rendition.themes.register(paraSpacingThemeName, {
      "p, div": { "margin-bottom": `${settings.paragraphSpacing ?? 1.0}em !important` }
    });
    registered.add(paraSpacingThemeName);
  }

  rendition.themes.select(themeName);
  rendition.themes.select(paraSpacingThemeName);
  if (!isPublisher) {
    rendition.themes.override("font-size", `${settings.fontSize}px`, true);
    rendition.themes.override("line-height", `${settings.lineHeight}`, true);
    rendition.themes.override("letter-spacing", `${settings.letterSpacing ?? 0}em`, true);
  }
  if (settings.fontFamily) {
    rendition.themes.override("font-family", `'${settings.fontFamily}', serif`);
  }
}

function shouldIgnoreKeydown(event: KeyboardEvent): boolean {
  const target = event.target;
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement;
}

export function EpubReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const epubUrl = useLibraryStore((state) => state.activeEpubUrl);
  const targetHref = useLibraryStore((state) => state.activeEpubTargetHref);
  const progress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const settings = useLibraryStore((state) => state.readerSettings);
  const activeSession = useLibraryStore((state) => state.activeSession);
  const activity = useLibraryStore((state) => state.activity);
  const setProgress = useLibraryStore((state) => state.setProgress);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setSelectedId = useInspirationStore((state) => state.setSelectedId);
  const setError = useAppStore((state) => state.setError);
  const setScreen = useAppStore((state) => state.setScreen);
  const setReaderReturn = useAppStore((state) => state.setReaderReturn);
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
  const [createdInspiration, setCreatedInspiration] = useState<InspirationItem>();
  const [highlights, setHighlights] = useState<HighlightItem[]>([]);
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const [sidePanelTab, setSidePanelTab] = useState<SidePanelTab>("toc");
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
    return (
      <ShellPanel className="h-full border-0">
        <EmptyState title="没有打开 EPUB" body="从书库中选择一本 EPUB 书籍后，会在这里打开阅读器。" />
      </ShellPanel>
    );
  }

  const progressPercent = Math.round((locationRef.current?.progressPercent ?? progress?.progressPercent ?? 0) * 100);
  const currentTocItem = toc.find((item) => normalizeEpubHref(item.href) === normalizeEpubHref(locationRef.current?.epub?.href));

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
    };
    document.addEventListener("mousemove", onMove);
    document.addEventListener("mouseup", onUp);
  };

  const selectedTextFromEpub = (): string | undefined => {
    const fallback = window.getSelection()?.toString().trim();
    if (fallback) return fallback.slice(0, 800);
    const renditionLike = renditionRef.current as unknown as { getContents?: () => Array<{ window?: Window }> };
    const contents = renditionLike.getContents?.() ?? [];
    for (const content of contents) {
      const selected = content.window?.getSelection?.()?.toString().trim();
      if (selected) return selected.slice(0, 800);
    }
    return undefined;
  };

  const openCreatedInspiration = () => {
    if (!createdInspiration) return;
    setSelectedId(createdInspiration.id);
    setReaderReturn({
      bookId: activeBook.id,
      label: `返回阅读：《${activeBook.title}》`,
      fromScreen: "reader",
      progressLabel: `EPUB 阅读进度 ${progressPercent}% 附近`
    });
    setScreen("inspiration", { preserveReturn: true });
  };

  const createReadingInspiration = async () => {
    const location = locationRef.current;
    const excerpt = selectedTextFromEpub();
    const sourceProgress = location?.progressPercent === undefined ? undefined : location.progressPercent * 100;
    const item = await createInspiration({
      title: `阅读灵感：${activeBook.title}`,
      body: "",
      type: "note",
      status: "inbox",
      tags: ["阅读札记", "EPUB"],
      platformTags: [],
      sourceBookId: activeBook.id,
      sourceLocation: {
        format: "epub",
        progressPercent: sourceProgress,
        href: location?.epub?.href,
        cfi: location?.epub?.cfi,
        excerpt,
        createdFrom: excerpt ? "reader-selection" : "reader-note"
      },
      source: {
        bookId: activeBook.id,
        bookTitle: activeBook.title,
        bookAuthor: activeBook.author,
        format: "epub",
        chapterTitle: currentTocItem?.label,
        locationLabel: currentTocItem?.label ? `${currentTocItem.label} · ${progressPercent}% 附近` : `EPUB 阅读进度 ${progressPercent}% 附近`,
        progressPercent: sourceProgress,
        excerpt,
        href: location?.epub?.href,
        cfi: location?.epub?.cfi,
        createdFrom: excerpt ? "reader-selection" : "reader-note",
        createdAt: new Date().toISOString()
      }
    });
    useInspirationStore.getState().upsertItem(item);
    setCreatedInspiration(item);
    showToast({
      tone: "success",
      title: "已记录为灵感",
      body: excerpt ? "选中的 EPUB 文字已保存到来源摘录，可以继续阅读。" : "已记录当前章节和阅读进度，可以继续阅读。"
    });
  };

  return (
    <div className="grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <header className="paper-topbar flex items-center gap-3 px-5">
        <BookOpen size={18} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold text-paper-ink">{activeBook.title}</div>
          <div className="text-xs text-paper-muted">
            EPUB 阅读进度：{progressPercent}% · 本书累计 {formatDuration(progress?.totalReadingTimeMs)}
          </div>
        </div>
        <Button
          variant="quiet"
          onClick={() => void toggleBookmark()}
          title={isCurrentBookmarked ? "移除书签" : "添加书签"}
        >
          {isCurrentBookmarked ? <BookmarkCheck size={16} /> : <Bookmark size={16} />}
          {isCurrentBookmarked ? "已书签" : "书签"}
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            const cfiRange = (window as any).__epubSelectedCfiRange;
            const text = selectedTextFromEpub();
            if (cfiRange && text) {
              await addHighlight(cfiRange, text, "yellow");
            } else {
              showToast({ tone: "error", title: "无法高亮", body: "请先在书中选中一段文字。" });
            }
          }}
          title="高亮选中文字（黄色）"
        >
          <Highlighter size={16} />
          高亮
        </Button>
        <Button
          variant="quiet"
          onClick={() => void createReadingInspiration()}
        >
          <Lightbulb size={16} />
          记为灵感
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            await flushProgress();
            await endTracking("leave-reader");
            setScreen("stats");
          }}
        >
          <BarChart3 size={16} />
          统计
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            await flushProgress();
            setSettingsDrawerOpen(true);
          }}
        >
          <Settings size={16} />
          设置
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            await flushProgress();
            await endTracking("leave-reader");
            setScreen("library");
          }}
        >
          <ArrowLeft size={16} />
          返回书库
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            await flushProgress();
            await endTracking("leave-reader");
            setScreen("start");
          }}
        >
          返回首页
        </Button>
      </header>

      <div className={`grid min-h-0 ${tocCollapsed ? "grid-cols-[1fr_56px]" : "grid-cols-[1fr_320px]"}`}>
        <div ref={viewerContainerRef} className="relative min-h-0 overflow-hidden" onWheel={handleWheelPageTurn}>
          {loading && <div className="absolute inset-0 z-10 grid place-items-center bg-paper-panel/80 text-sm text-paper-muted">正在打开 EPUB...</div>}
          {createdInspiration && (
            <div className="motion-notice absolute left-1/2 top-4 z-20 flex w-[min(760px,calc(100%-32px))] -translate-x-1/2 items-center gap-3 rounded-xl border border-copper/25 bg-paper-panel/95 px-4 py-3 text-sm text-paper-muted shadow-paper">
              <span className="flex-1">已记录为灵感。你可以继续阅读，也可以现在查看灵感。</span>
              <Button variant="secondary" onClick={openCreatedInspiration}>
                查看灵感
              </Button>
              <Button variant="quiet" onClick={() => setCreatedInspiration(undefined)}>
                继续阅读
              </Button>
            </div>
          )}
          {/* Floating selection toolbar */}
          {selectionToolbar?.visible && (
            <div
              className="absolute z-50 flex items-center gap-0.5 rounded-lg bg-stone-800 px-2 py-1.5 text-sm text-white shadow-xl"
              style={{ left: selectionToolbar.x + toolbarOffsetRef.current.x, top: selectionToolbar.y + toolbarOffsetRef.current.y, transform: "translate(-50%, -100%)" }}
              onMouseDown={(e) => e.stopPropagation()}
            >
              <span
                className="mr-0.5 cursor-grab rounded px-0.5 py-0.5 text-stone-400 hover:text-white active:cursor-grabbing"
                onMouseDown={handleToolbarDragStart}
                title="拖动"
              >
                <GripHorizontal size={12} />
              </span>
              {/* Highlight with color picker */}
              <div className="relative">
                <button
                  className="rounded px-2 py-1 hover:bg-stone-700 text-yellow-400"
                  title="高亮"
                  onClick={(e) => {
                    e.stopPropagation();
                    setShowColorPicker((v) => !v);
                  }}
                >
                  <Highlighter size={14} />
                </button>
                {showColorPicker && (
                  <div
                    className="absolute top-full left-1/2 mt-1 flex -translate-x-1/2 gap-1 rounded-lg bg-stone-800 p-1.5 shadow-xl"
                    onMouseDown={(e) => e.stopPropagation()}
                  >
                    {HIGHLIGHT_COLORS.map((c) => (
                      <button
                        key={c.value}
                        className="h-5 w-5 rounded-full border border-stone-600 transition-transform hover:scale-110"
                        style={{ background: c.hex }}
                        title={c.label}
                        onClick={async (e) => {
                          e.stopPropagation();
                          await addHighlight(selectionToolbar.cfiRange, selectionToolbar.text, c.value);
                          setSelectionToolbar(null);
                          setShowColorPicker(false);
                        }}
                      />
                    ))}
                  </div>
                )}
              </div>
              <button
                className="rounded px-2 py-1 hover:bg-stone-700"
                title="复制"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(selectionToolbar.text);
                    showToast({ tone: "success", title: "已复制", body: `${selectionToolbar.text.length} 字` });
                  } catch {
                    showToast({ tone: "error", title: "复制失败" });
                  }
                  setSelectionToolbar(null);
                }}
              >
                <Copy size={14} />
              </button>
              <button
                className="rounded px-2 py-1 hover:bg-stone-700 text-amber-400"
                title="记为灵感"
                onClick={() => {
                  void createReadingInspiration();
                  setSelectionToolbar(null);
                }}
              >
                <Lightbulb size={14} />
              </button>
              <button
                className="rounded px-2 py-1 hover:bg-stone-700"
                title="关闭"
                onClick={() => {
                  try {
                    const contents = (renditionRef.current as any)?.getContents?.();
                    contents?.forEach((c: any) => c.window?.getSelection?.()?.removeAllRanges());
                  } catch { /* ignore */ }
                  setSelectionToolbar(null);
                  setShowColorPicker(false);
                }}
              >
                <X size={14} />
              </button>
            </div>
          )}
          <div
            ref={viewerRef}
            className="h-full w-full px-8 py-6"
            style={{ background: readerBackgroundColor(settings.readerBackground) }}
            onPointerDown={handleActivity}
          />
          <div className="pointer-events-none absolute inset-x-6 bottom-4 flex justify-between">
            <button
              className="pointer-events-auto inline-flex h-10 w-10 items-center justify-center rounded-full border border-paper-line bg-paper-panel/90 text-paper-muted shadow-lift hover:text-copper"
              onClick={() => void turnPage("prev")}
              title="上一页"
              aria-label="上一页"
            >
              <ChevronLeft size={18} />
            </button>
            <button
              className="pointer-events-auto inline-flex h-10 w-10 items-center justify-center rounded-full border border-paper-line bg-paper-panel/90 text-paper-muted shadow-lift hover:text-copper"
              onClick={() => void turnPage("next")}
              title="下一页"
              aria-label="下一页"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </div>

        <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
          {tocCollapsed ? (
            <div className="grid gap-3">
              <button className="rounded-md border border-paper-line bg-paper-panel p-2 text-xs text-paper-muted hover:text-paper-ink" onClick={() => setTocCollapsed(false)}>
                目录
              </button>
              <div className="text-center text-[11px] leading-5 text-paper-muted">{progressPercent}%</div>
            </div>
          ) : (
            <>
              {/* Tab bar */}
              <div className="mb-3 flex items-center gap-1 border-b border-paper-line pb-2">
                <button
                  className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "toc" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
                  onClick={() => setSidePanelTab("toc")}
                >
                  <List size={13} className="mr-1 inline" />目录
                </button>
                <button
                  className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "highlights" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
                  onClick={() => setSidePanelTab("highlights")}
                >
                  <Highlighter size={13} className="mr-1 inline" />高亮{highlights.length > 0 ? ` (${highlights.length})` : ""}
                </button>
                <button
                  className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "bookmarks" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
                  onClick={() => setSidePanelTab("bookmarks")}
                >
                  <Bookmark size={13} className="mr-1 inline" />书签{bookmarks.length > 0 ? ` (${bookmarks.length})` : ""}
                </button>
                <span className="flex-1" />
                <Button variant="quiet" className="h-7 px-2 text-xs" onClick={() => setTocCollapsed(true)}>
                  收起
                </Button>
              </div>

              {/* TOC tab */}
              {sidePanelTab === "toc" && (
                <>
                  <div className="mb-3 text-xs text-paper-muted">
                    当前位置：{currentTocItem?.label ?? `${progressPercent}% 附近`}
                  </div>
                  <div className="mb-5">
                    {toc.length === 0 ? (
                      <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">未检测到目录</div>
                    ) : (
                      <div className="grid gap-1">
                        {toc.map((item) => (
                          <button
                            key={item.id}
                            type="button"
                            className="rounded px-2 py-1.5 text-left text-sm text-paper-muted hover:bg-paper-panel hover:text-paper-ink"
                            style={{ paddingLeft: `${8 + Math.max(0, item.level) * 12}px` }}
                            title={item.label}
                            onClick={() => void jumpToToc(item.href)}
                          >
                            <span className="line-clamp-2">{item.label}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  {settings.tracking.showReadingStatsCards && (
                    <div className="mt-5 rounded-xl border border-paper-line bg-paper-panel p-3 text-xs leading-6 text-paper-muted shadow-lift">
                      <div className="font-semibold text-paper-ink">当前会话</div>
                      <div>状态：{activity.isTracking ? (activity.isUserActive ? "计时中" : "已暂停") : "未追踪"}</div>
                      <div>窗口：{activity.isWindowFocused ? "前台" : "后台"}</div>
                      <div>有效时长：{formatDuration(activeSession?.activeDurationMs)}</div>
                      <div>位置：{locationRef.current?.epub?.cfi ? "CFI 已记录" : "等待定位"}</div>
                    </div>
                  )}
                </>
              )}

              {/* Highlights tab */}
              {sidePanelTab === "highlights" && (
                <div className="grid gap-2">
                  {highlights.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-4 text-center text-sm text-paper-muted">
                      暂无高亮。在书中选中文字后点击“高亮”按钮即可添加。
                    </div>
                  ) : (
                    highlights.map((hl) => (
                      <div
                        key={hl.id}
                        className="group rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:shadow-lift"
                      >
                        <div className="mb-1.5 flex items-center gap-2">
                          <span
                            className="inline-block h-3 w-3 rounded-full"
                            style={{ background: HIGHLIGHT_COLORS.find((c) => c.value === hl.color)?.hex ?? "#fde047" }}
                          />
                          {hl.chapterTitle && (
                            <span className="flex-1 truncate text-[11px] text-paper-muted">{hl.chapterTitle}</span>
                          )}
                          <button
                            className="rounded p-1 text-paper-muted opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                            onClick={() => void removeHighlight(hl.id)}
                            title="删除高亮"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                        <button
                          className="w-full text-left text-sm leading-5 text-paper-ink hover:text-copper"
                          onClick={() => void jumpToHighlight(hl)}
                        >
                          <span className="line-clamp-3">{hl.text}</span>
                        </button>
                        {hl.note && (
                          <div className="mt-1.5 text-xs italic text-paper-muted">{hl.note}</div>
                        )}
                        {/* Color picker for this highlight */}
                        <div className="mt-2 flex items-center gap-1">
                          {HIGHLIGHT_COLORS.map((c) => (
                            <button
                              key={c.value}
                              className={`h-4 w-4 rounded-full border transition ${hl.color === c.value ? "border-paper-ink ring-1 ring-paper-ink/30" : "border-transparent opacity-60 hover:opacity-100"}`}
                              style={{ background: c.hex }}
                              title={c.label}
                              onClick={async () => {
                                if (hl.color !== c.value) {
                                  const updated = { ...hl, color: c.value, updatedAt: new Date().toISOString() };
                                  await saveHighlight(updated);
                                  setHighlights((prev) => {
                                    const next = prev.map((h) => (h.id === hl.id ? updated : h));
                                    highlightsRef.current = next;
                                    return next;
                                  });
                                }
                              }}
                            />
                          ))}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              )}

              {/* Bookmarks tab */}
              {sidePanelTab === "bookmarks" && (
                <div className="grid gap-2">
                  <Button
                    variant="secondary"
                    className="h-8 text-xs"
                    onClick={() => void addBookmark()}
                  >
                    <Bookmark size={14} />
                    在当前位置添加书签
                  </Button>
                  {bookmarks.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-4 text-center text-sm text-paper-muted">
                      暂无书签。
                    </div>
                  ) : (
                    bookmarks.map((bm) => (
                      <div
                        key={bm.id}
                        className="group flex items-center gap-2 rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:shadow-lift"
                      >
                        <Bookmark size={14} className="shrink-0 text-copper/60" />
                        <button
                          className="min-w-0 flex-1 text-left"
                          onClick={() => void jumpToBookmark(bm)}
                        >
                          <div className="truncate text-sm text-paper-ink hover:text-copper">{bm.label}</div>
                          <div className="mt-0.5 text-[11px] text-paper-muted">
                            {bm.chapterTitle ? `${bm.chapterTitle} · ` : ""}
                            {bm.progressPercent !== undefined ? `${Math.round(bm.progressPercent * 100)}%` : ""}
                            {" · "}{new Date(bm.createdAt).toLocaleDateString()}
                          </div>
                        </button>
                        <button
                          className="rounded p-1 text-paper-muted opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                          onClick={async () => {
                            await removeBookmarkById(bm.id);
                            setBookmarks((prev) => prev.filter((b) => b.id !== bm.id));
                          }}
                          title="删除书签"
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    ))
                  )}
                </div>
              )}
            </>
          )}
        </ShellPanel>
      </div>
      {settingsDrawerOpen && (
        <div className="absolute inset-0 z-30 bg-paper-ink/10 backdrop-blur-[1px]" onMouseDown={() => setSettingsDrawerOpen(false)}>
          <aside
            className="motion-drawer absolute right-0 top-0 h-full w-[360px] overflow-auto border-l border-paper-line bg-paper-panel p-5 shadow-paper"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between">
              <div>
                <div className="paper-title text-lg font-semibold">阅读设置</div>
                <div className="mt-1 text-xs text-paper-muted">EPUB 默认保留原书样式，需要统一排版时再切换。</div>
              </div>
              <button className="rounded-md p-2 text-paper-muted hover:bg-paper-soft hover:text-paper-ink" onClick={() => setSettingsDrawerOpen(false)}>
                <X size={17} />
              </button>
            </div>
            <ReaderSettingsPanel
              settings={settings}
              onReset={async () => {
                const next = await resetReaderSettings();
                setReaderSettings(next.reader);
                handleActivity();
              }}
              onChange={async (patch) => {
                const next = await updateReaderSettings(patch);
                setReaderSettings(next);
                void updateLocation();
              }}
            />
          </aside>
        </div>
      )}
    </div>
  );
}
