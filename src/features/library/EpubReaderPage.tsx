import { useCallback, useEffect, useMemo, useRef, useState, type WheelEvent } from "react";
import { ArrowLeft, BarChart3, BookOpen, ChevronLeft, ChevronRight, Lightbulb, Settings, X } from "lucide-react";
import ePub from "epubjs";
import type { Book, Location as EpubLocation, Rendition } from "epubjs";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { createInspiration } from "@/services/inspiration-service";
import { saveEpubLocation, updateReaderSettings } from "@/services/reader-service";
import { resetReaderSettings } from "@/services/settings-service";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { InspirationItem } from "@/types/inspiration";
import type { LibraryBook, ReaderSettings, ReadingLocation } from "@/types/library";
import { formatDuration, readerShellClass, readerBackgroundColor, readerTextColor } from "@/utils/format";

const EPUB_INITIAL_DISPLAY_TIMEOUT_MS = 8_000;

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
  if (settings.epubStyleMode === "publisher") {
    const readablePublisherTheme =
      settings.readerBackground === "night"
        ? {
            "body, p, div, span, section, article, h1, h2, h3, h4, h5, h6, li, td, th, blockquote": {
              color: `${readerTextColor(settings.readerBackground)} !important`
            },
            a: {
              color: "#e0b07b !important"
            }
          }
        : {};
    const themeName = settings.readerBackground === "night" ? "publisher-preserve-night" : "publisher-preserve";
    rendition.themes.register(themeName, {
      body: {
        background: "transparent !important"
      },
      ...readablePublisherTheme
    });
    rendition.themes.select(themeName);
    return;
  }
  rendition.themes.register("novel-workbench", {
    body: {
      color: `${readerTextColor(settings.readerBackground)} !important`,
      background: "transparent !important",
      "font-size": `${settings.fontSize}px !important`,
      "line-height": `${settings.lineHeight} !important`
    },
    p: {
      "line-height": `${settings.lineHeight} !important`
    },
    a: {
      color: `${readerTextColor(settings.readerBackground)} !important`
    }
  });
  rendition.themes.select("novel-workbench");
  rendition.themes.fontSize(`${settings.fontSize}px`);
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
  const toc = activeBook?.epub?.toc ?? [];
  const baseLocation = useMemo(() => (activeBook ? initialEpubLocation(activeBook, progress?.currentLocation) : undefined), [activeBook, progress?.currentLocation]);
  const locationRef = useRef<ReadingLocation | undefined>(baseLocation);
  const [loading, setLoading] = useState(true);
  const [tocCollapsed, setTocCollapsed] = useState(false);
  const [settingsDrawerOpen, setSettingsDrawerOpen] = useState(false);
  const [createdInspiration, setCreatedInspiration] = useState<InspirationItem>();

  useEffect(() => {
    locationRef.current = baseLocation;
  }, [baseLocation]);

  const getCurrentLocation = useCallback(() => locationRef.current, []);
  const { recordInteraction, endTracking } = useReadingSessionTracker(viewerRef, getCurrentLocation);

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

  const updateLocation = useCallback(
    (value?: EpubLocation) => {
      const book = useLibraryStore.getState().activeBook;
      if (!book || book.format !== "epub" || !locationRef.current) return;
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
    if (!activeBook || activeBook.format !== "epub" || !epubUrl || !settings || !viewerRef.current) return;
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
    const savedEpubLocation = settings.restoreLastPosition ? latestProgress?.currentLocation.epub : undefined;
    const restoreCfi = savedEpubLocation?.cfi;
    const restoreHref = savedEpubLocation?.href;
    initialTargetHrefRef.current = initialTargetHref;
    canSaveProgressRef.current = false;
    pendingInitialLocationRef.current = undefined;
    applyReaderTheme(rendition, settings);
    rendition.on("relocated", (location: EpubLocation) => updateLocation(location));
    rendition.on("selected", () => handleActivity());
    rendition.on("click", () => handleActivity());

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
        const restoreTarget = initialTargetHref || restoreHref || restoreCfi || undefined;
        const expectedHref = initialTargetHref || restoreHref;
        if (restoreTarget) {
          await displayWithTimeout(rendition, undefined);
          pendingInitialLocationRef.current = undefined;
        }
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
      void flushProgress();
      rendition.destroy();
      book.destroy();
      renditionRef.current = undefined;
      bookRef.current = undefined;
      pendingInitialLocationRef.current = undefined;
      canSaveProgressRef.current = true;
    };
  }, [activeBook?.id, epubUrl, flushProgress, handleActivity, settings?.restoreLastPosition, setError, updateLocation]);

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
  }, [settings?.fontSize, settings?.lineHeight, settings?.readerBackground, settings?.epubStyleMode]);

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
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [turnPage]);

  if (!activeBook || activeBook.format !== "epub" || !settings || !epubUrl) {
    return (
      <ShellPanel className="h-full border-0">
        <EmptyState title="没有打开 EPUB" body="从书库中选择一本 EPUB 书籍后，会在这里打开阅读器。" />
      </ShellPanel>
    );
  }

  const progressPercent = Math.round((locationRef.current?.progressPercent ?? progress?.progressPercent ?? 0) * 100);
  const currentTocItem = toc.find((item) => normalizeEpubHref(item.href) === normalizeEpubHref(locationRef.current?.epub?.href));

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
        <div className={`relative min-h-0 overflow-hidden ${readerShellClass(settings.readerBackground)}`} onWheel={handleWheelPageTurn}>
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
              <div className="mb-4 flex items-center justify-between gap-2">
                <div>
                  <div className="text-sm font-semibold text-paper-ink">目录</div>
                  <div className="mt-1 text-xs text-paper-muted">当前位置：{currentTocItem?.label ?? `${progressPercent}% 附近`}</div>
                </div>
                <Button variant="quiet" className="h-8 px-2" onClick={() => setTocCollapsed(true)}>
                  收起
                </Button>
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
                handleActivity();
              }}
            />
          </aside>
        </div>
      )}
    </div>
  );
}

