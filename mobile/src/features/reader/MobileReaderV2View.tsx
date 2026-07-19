import { useCallback, useEffect, useRef, useState, type MouseEvent } from "react";
import { ArrowLeft, BookmarkPlus, ChevronLeft, ChevronRight, Highlighter, List, NotebookPen, RotateCcw, Settings2, ShieldCheck, Sparkles, X } from "lucide-react";
import { addMobileReadingSession, saveMobileReadingProgress, type MobileSnapshot } from "../../services/mobile-storage";
import type { MobileBook, MobileReaderSettings } from "../../types/mobile";
import { formatDuration } from "../../utils/format";
import { useReaderSession } from "./hooks/useReaderSession";
import { useReaderV2Annotations } from "./hooks/useReaderV2Annotations";
import { ReaderInspirationSheet } from "./components/ReaderInspirationSheet";
import { ReaderV2AnnotationsPanel } from "./components/ReaderV2AnnotationsPanel";
import { ReaderController } from "./engine-v2/ReaderController";
import { ReaderEngineFactory, type ReaderEngineProvider } from "./engine-v2/ReaderEngineFactory";
import { ReaderProgressRepository } from "./engine-v2/ReaderProgressRepository";
import { TextReaderEngineV2 } from "./engine-v2/TextReaderEngineV2";
import {
  ReaderEngineError,
  readerFormatFromBook,
  readerPreferencesFromSettings,
  type ReaderLink,
  type ReaderLocator,
  type ReaderState as ReaderEngineState
} from "./engine-v2/types";

type V2Panel = "toc" | "annotations" | "settings" | null;

interface MobileReaderV2ViewProps {
  book: MobileBook;
  content: string;
  loading: boolean;
  loadError?: string;
  snapshot: MobileSnapshot;
  settings: MobileReaderSettings;
  onSettingsChange: (settings: MobileReaderSettings) => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onBack: () => void;
  onReload: () => void;
  onSwitchToLegacy: () => void;
  onOpenInspiration: (inspirationId: string) => void;
  onMessage: (message: string) => void;
  initialProgressPercent?: number;
}

const unsupportedLegacyProvider: ReaderEngineProvider = () => {
  throw new ReaderEngineError("unsupported-format", "V2 试用页不会创建 Legacy 引擎。", { retryable: false });
};

function loadingLabel(state: ReaderEngineState): string {
  if (state.status !== "loading") return "正在准备阅读器…";
  const labels: Record<typeof state.phase, string> = {
    validating: "正在校验正文…",
    opening: "正在打开书籍…",
    parsing: "正在整理章节…",
    mounting: "正在创建阅读页面…",
    "restoring-location": "正在恢复阅读位置…",
    ready: "即将完成…"
  };
  return labels[state.phase];
}

export function MobileReaderV2View({
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
  onSwitchToLegacy,
  onOpenInspiration,
  onMessage,
  initialProgressPercent
}: MobileReaderV2ViewProps) {
  const mountRef = useRef<HTMLDivElement>(null);
  const controllerRef = useRef<ReaderController>();
  const locationUnsubscribeRef = useRef<() => void>();
  const selectionUnsubscribeRef = useRef<() => void>();
  const snapshotRef = useRef(snapshot);
  const settingsRef = useRef(settings);
  const activeReadingMsRef = useRef(0);
  const closeReaderRef = useRef<() => void>(() => undefined);
  const closingRef = useRef(false);
  const [engineState, setEngineState] = useState<ReaderEngineState>({ status: "idle" });
  const [locator, setLocator] = useState<ReaderLocator>();
  const [toc, setToc] = useState<ReaderLink[]>([]);
  const [controlsVisible, setControlsVisible] = useState(true);
  const [panel, setPanel] = useState<V2Panel>(null);
  const [selectionText, setSelectionText] = useState("");
  const [selectionLocator, setSelectionLocator] = useState<ReaderLocator>();
  const [showInspirationSheet, setShowInspirationSheet] = useState(false);
  const [readerNotice, setReaderNotice] = useState("");
  const [lastSavedInspirationId, setLastSavedInspirationId] = useState("");
  const { lastReaderActivityRef, activeReadingMs } = useReaderSession({ bookId: book.id });

  snapshotRef.current = snapshot;
  settingsRef.current = settings;
  activeReadingMsRef.current = activeReadingMs;

  const updateLocation = useCallback(async () => {
    const state = controllerRef.current?.getState();
    if (state?.status !== "ready") return;
    const next = await state.engine.getCurrentLocator();
    if (next) setLocator(next);
  }, []);

  useEffect(() => {
    if (loading || loadError || !content.trim() || !mountRef.current) return undefined;
    let disposed = false;
    const persistence = {
      load: async (bookId: string) => snapshotRef.current.progress.find((item) => item.bookId === bookId),
      save: async (_bookId: string, nextLocator: ReaderLocator, legacyLocation: MobileSnapshot["progress"][number]["currentLocation"]) => {
        const progressPercent = Math.max(0, Math.min(100, (nextLocator.progression ?? 0) * 100));
        const next = await saveMobileReadingProgress(
          snapshotRef.current,
          book,
          progressPercent,
          0,
          legacyLocation
        );
        snapshotRef.current = next;
        if (!disposed) onSnapshotChange(next);
      }
    };
    const progressRepository = new ReaderProgressRepository(persistence);
    const factory = new ReaderEngineFactory(
      {
        txt: unsupportedLegacyProvider,
        markdown: unsupportedLegacyProvider,
        epub: unsupportedLegacyProvider
      },
      {
        txt: () => new TextReaderEngineV2("txt"),
        markdown: () => new TextReaderEngineV2("markdown")
      }
    );
    const controller = new ReaderController(factory, progressRepository);
    controllerRef.current = controller;
    const unsubscribeState = controller.subscribe((next) => {
      if (!disposed) setEngineState(next);
    });

    void controller.open(
      {
        book,
        format: readerFormatFromBook(book),
        content,
        preferences: readerPreferencesFromSettings(settingsRef.current)
      },
      mountRef.current,
      "v2"
    ).then(async (state) => {
      if (disposed || state.status !== "ready") return;
      setToc(await state.engine.getTableOfContents());
      locationUnsubscribeRef.current?.();
      locationUnsubscribeRef.current = state.engine.on("location", (next) => {
        if (!disposed) setLocator(next);
      });
      selectionUnsubscribeRef.current?.();
      selectionUnsubscribeRef.current = state.engine.on("selection", (selection) => {
        if (disposed) return;
        setSelectionText(selection?.text.slice(0, 800) ?? "");
        setSelectionLocator(selection?.locator);
      });
      if (initialProgressPercent !== undefined) {
        await state.engine.goTo({
          version: 2,
          bookId: book.id,
          format: readerFormatFromBook(book),
          progression: Math.max(0, Math.min(1, initialProgressPercent / 100)),
          updatedAt: Date.now()
        });
      }
      await updateLocation();
    }).catch((error) => {
      if (!disposed) {
        onMessage(`V2 阅读器打开失败：${error instanceof Error ? error.message : String(error)}`);
      }
    });

    return () => {
      disposed = true;
      locationUnsubscribeRef.current?.();
      locationUnsubscribeRef.current = undefined;
      selectionUnsubscribeRef.current?.();
      selectionUnsubscribeRef.current = undefined;
      unsubscribeState();
      if (controllerRef.current === controller) controllerRef.current = undefined;
      void controller.destroy();
    };
  }, [book, content, initialProgressPercent, loadError, loading, onMessage, onSnapshotChange, updateLocation]);

  useEffect(() => {
    const controller = controllerRef.current;
    if (!controller || controller.getState().status !== "ready") return;
    void controller.applyPreferences(readerPreferencesFromSettings(settings)).then(updateLocation).catch((error) => {
      onMessage(`阅读设置应用失败：${error instanceof Error ? error.message : String(error)}`);
    });
  }, [settings, onMessage, updateLocation]);

  useEffect(() => {
    const handleVisibility = () => {
      if (document.visibilityState === "hidden") void controllerRef.current?.flush();
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => document.removeEventListener("visibilitychange", handleVisibility);
  }, []);

  const leaveReader = useCallback(async (next: () => void) => {
    if (closingRef.current) return;
    closingRef.current = true;
    try {
      const controller = controllerRef.current;
      const state = controller?.getState();
      let finalLocator = locator;
      if (state?.status === "ready") {
        finalLocator = (await state.engine.getCurrentLocator()) ?? finalLocator;
        await controller?.flush();
      }
      const durationMs = Math.max(1_000, activeReadingMsRef.current);
      if (durationMs > 0 && finalLocator) {
        const latest = snapshotRef.current;
        const saved = await addMobileReadingSession(
          latest,
          book,
          durationMs,
          Math.max(0, Math.min(100, (finalLocator.progression ?? 0) * 100))
        );
        snapshotRef.current = saved;
        onSnapshotChange(saved);
      }
      await controller?.close();
    } catch (error) {
      onMessage(`退出阅读时保存失败：${error instanceof Error ? error.message : String(error)}`);
    } finally {
      next();
    }
  }, [book, locator, onMessage, onSnapshotChange]);

  closeReaderRef.current = () => {
    if (showInspirationSheet) {
      setShowInspirationSheet(false);
      return;
    }
    if (panel) {
      setPanel(null);
      return;
    }
    void leaveReader(onBack);
  };

  useEffect(() => {
    const handleReaderBack = (event: Event) => {
      event.preventDefault();
      closeReaderRef.current();
    };
    window.addEventListener("mobile-reader-back", handleReaderBack);
    return () => window.removeEventListener("mobile-reader-back", handleReaderBack);
  }, []);

  const move = async (direction: -1 | 1) => {
    const state = controllerRef.current?.getState();
    if (state?.status !== "ready") return;
    lastReaderActivityRef.current = Date.now();
    const moved = direction > 0 ? await state.engine.goForward() : await state.engine.goBackward();
    if (!moved) onMessage(direction > 0 ? "已经读到本书末尾。" : "已经在本书开头。");
    await updateLocation();
  };

  const handleReadingTap = (event: MouseEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    if (target.closest("button, a, input, textarea, select") || window.getSelection()?.toString().trim()) return;
    lastReaderActivityRef.current = Date.now();
    const ratio = event.clientX / Math.max(1, window.innerWidth);
    if (ratio < 0.28) void move(-1);
    else if (ratio > 0.72) void move(1);
    else setControlsVisible((value) => !value);
  };

  const patchSettings = (patch: Partial<MobileReaderSettings>) => {
    onSettingsChange({ ...settingsRef.current, ...patch });
  };

  const progressPercent = Math.max(0, Math.min(100, (locator?.progression ?? 0) * 100));
  const currentChapter = toc.find((item) => item.id === locator?.chapterId)?.title;
  const currentChapterLink = toc.find((item) => item.id === locator?.chapterId);
  const externalError = loadError;
  const engineError = engineState.status === "error" ? engineState.message : undefined;
  const errorMessage = externalError || engineError;
  const showLoading = loading || (!errorMessage && engineState.status !== "ready");

  const annotations = useReaderV2Annotations({
    book,
    snapshotRef,
    onSnapshotChange,
    onMessage,
    locator,
    chapterTitle: currentChapter,
    selectionText,
    selectionLocator,
    setSelectionText,
    lastReaderActivityRef
  });

  const openAnnotations = async () => {
    const state = controllerRef.current?.getState();
    if (state?.status === "ready") {
      const selection = await state.engine.getSelection?.();
      if (selection?.text) {
        setSelectionText(selection.text.slice(0, 800));
        setSelectionLocator(selection.locator);
      }
    }
    setPanel("annotations");
  };

  const handleSaveInspiration = (nextSnapshot: MobileSnapshot, savedId: string) => {
    snapshotRef.current = nextSnapshot;
    onSnapshotChange(nextSnapshot);
    setLastSavedInspirationId(savedId);
    setReaderNotice("已保存灵感，并记录了阅读位置。");
    setShowInspirationSheet(false);
    annotations.clearSelection();
    onMessage("灵感已保存到灵感中心。");
  };

  return (
    <main className={`reader-shell reader-v2-shell reader-bg-${settings.readerBackground} reader-mode-${settings.readerMode} ${controlsVisible || panel || errorMessage ? "" : "reader-chrome-hidden"}`}>
      <header className="reader-v2-topbar">
        <button className="reader-v2-icon-button" onClick={() => void leaveReader(onBack)} aria-label="返回书架">
          <ArrowLeft size={22} />
        </button>
        <div>
          <strong>{book.title}</strong>
          <span>{currentChapter || "V2 试验阅读"} · {progressPercent.toFixed(1)}% · {formatDuration(activeReadingMs)}</span>
        </div>
        <span className="reader-v2-badge"><ShieldCheck size={14} />V2</span>
      </header>

      <div className="reader-v2-viewport" onClick={handleReadingTap}>
        <div ref={mountRef} className="reader-v2-mount" />
        {showLoading ? (
          <div className="reader-v2-state" role="status">
            <strong>{loading ? "正在读取本地正文…" : loadingLabel(engineState)}</strong>
            <p>首次整理大文件可能需要几秒，离开页面会自动取消任务。</p>
          </div>
        ) : null}
        {errorMessage ? (
          <div className="reader-v2-state reader-v2-error" role="alert">
            <strong>这本书暂时无法用 V2 打开</strong>
            <p>{errorMessage}</p>
            <div>
              <button onClick={onReload}><RotateCcw size={17} />重试</button>
              <button className="secondary-button" onClick={() => void leaveReader(onSwitchToLegacy)}>切回稳定内核</button>
              <button className="secondary-button" onClick={() => void leaveReader(onBack)}>返回书架</button>
            </div>
          </div>
        ) : null}
      </div>

      {selectionText && !panel && !showInspirationSheet ? (
        <div className="reader-v2-selection-toolbar" role="toolbar" aria-label="选中文字操作">
          <button onClick={() => void annotations.addHighlight()}><Highlighter size={17} />高亮</button>
          <button onClick={() => void openAnnotations()}><NotebookPen size={17} />笔记</button>
          <button onClick={() => setShowInspirationSheet(true)}><Sparkles size={17} />灵感</button>
          <button onClick={annotations.clearSelection} aria-label="取消选择"><X size={17} /></button>
        </div>
      ) : null}

      {readerNotice ? (
        <div className="reader-toast" role="status" aria-live="polite">
          <span>{readerNotice}</span>
          {lastSavedInspirationId ? <button onClick={() => onOpenInspiration(lastSavedInspirationId)}>查看灵感</button> : null}
          <button onClick={() => setReaderNotice("")}>继续阅读</button>
        </div>
      ) : null}

      <footer className="reader-v2-bottombar">
        <button onClick={() => void move(-1)} aria-label="上一页"><ChevronLeft size={22} /><span>上一页</span></button>
        <button onClick={() => setPanel("toc")} aria-label="目录"><List size={21} /><span>目录</span></button>
        <button onClick={() => void openAnnotations()} aria-label="书签与笔记"><BookmarkPlus size={21} /><span>标注</span></button>
        <button onClick={() => setPanel("settings")} aria-label="设置"><Settings2 size={21} /><span>设置</span></button>
        <button onClick={() => void move(1)} aria-label="下一页"><ChevronRight size={22} /><span>下一页</span></button>
      </footer>

      {panel ? (
        <section className="reader-v2-panel" aria-label={panel === "toc" ? "目录" : panel === "annotations" ? "书签、笔记与灵感" : "阅读设置"}>
          <header>
            <button onClick={() => setPanel(null)} aria-label="返回阅读"><ArrowLeft size={21} /></button>
            <strong>{panel === "toc" ? "目录" : panel === "annotations" ? "标注与灵感" : "阅读设置"}</strong>
            <span />
          </header>
          {panel === "toc" ? (
            <div className="reader-v2-toc-list">
              {toc.map((item) => (
                <button
                  key={item.id}
                  className={item.id === locator?.chapterId ? "active" : ""}
                  style={{ paddingLeft: `${18 + Math.max(0, item.level - 1) * 14}px` }}
                  onClick={() => {
                    const state = controllerRef.current?.getState();
                    if (state?.status !== "ready") return;
                    void state.engine.goToChapter(item.id).then(async () => {
                      await updateLocation();
                      setPanel(null);
                    });
                  }}
                >
                  {item.title}
                </button>
              ))}
            </div>
          ) : panel === "annotations" ? (
            <ReaderV2AnnotationsPanel
              book={book}
              snapshot={snapshot}
              selectionText={selectionText}
              onAddBookmark={() => void annotations.addBookmark()}
              onAddHighlight={() => void annotations.addHighlight()}
              onAddNote={annotations.addNote}
              onAddInspiration={() => {
                setPanel(null);
                setShowInspirationSheet(true);
              }}
              onDeleteNote={(id) => void annotations.removeNote(id)}
              onDeleteHighlight={(id) => void annotations.removeHighlight(id)}
            />
          ) : (
            <div className="reader-v2-settings">
              <label>
                <span>阅读方式</span>
                <select value={settings.readerMode} onChange={(event) => patchSettings({ readerMode: event.target.value as MobileReaderSettings["readerMode"] })}>
                  <option value="paged">左右翻页</option>
                  <option value="scroll">上下滚动</option>
                </select>
              </label>
              <label>
                <span>字号 {settings.fontSize}px</span>
                <input type="range" min="14" max="30" step="1" value={settings.fontSize} onChange={(event) => patchSettings({ fontSize: Number(event.target.value) })} />
              </label>
              <label>
                <span>行距 {settings.lineHeight.toFixed(1)}</span>
                <input type="range" min="1.3" max="2.4" step="0.1" value={settings.lineHeight} onChange={(event) => patchSettings({ lineHeight: Number(event.target.value) })} />
              </label>
              <div className="reader-v2-theme-grid">
                {(["white", "warm", "green", "night"] as const).map((theme) => (
                  <button key={theme} className={settings.readerBackground === theme ? "active" : ""} onClick={() => patchSettings({ readerBackground: theme })}>
                    {{ white: "白纸", warm: "暖纸", green: "护眼", night: "夜间" }[theme]}
                  </button>
                ))}
              </div>
              <button className="secondary-button reader-v2-legacy-action" onClick={() => void leaveReader(onSwitchToLegacy)}>
                切回稳定内核
              </button>
              <p>V2 仍在灰度验证。书签、笔记、高亮和灵感已支持；朗读暂由稳定内核提供。</p>
            </div>
          )}
        </section>
      ) : null}

      {showInspirationSheet ? (
        <ReaderInspirationSheet
          book={book}
          snapshot={snapshot}
          selectionText={selectionText}
          currentProgress={progressPercent}
          currentChapter={currentChapterLink ? {
            id: currentChapterLink.id,
            title: currentChapterLink.title,
            level: currentChapterLink.level,
            index: currentChapterLink.index,
            href: currentChapterLink.href
          } : undefined}
          currentLocator={selectionLocator ?? locator}
          onSave={handleSaveInspiration}
          onClose={() => setShowInspirationSheet(false)}
        />
      ) : null}
    </main>
  );
}
