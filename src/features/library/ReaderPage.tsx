import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, BookOpen, ChartColumn, Copy, Highlighter, List, Quote, Settings, X } from "lucide-react";
import DOMPurify from "dompurify";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { EpubReaderPage } from "@/features/library/EpubReaderPage";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import { ReaderSettingsDrawer } from "@/features/library/ReaderSettingsDrawer";
import { useReaderExcerpt } from "@/features/library/useReaderExcerpt";
import type { ExcerptBuildContext } from "@/features/library/useReaderExcerpt";
import { useReaderProgress } from "@/hooks/useReaderProgress";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { getHighlightsByBook, saveHighlight } from "@/services/annotation-service";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { ExcerptResult, ExcerptTarget, HighlightColor, HighlightItem } from "@/types/library";
import { formatDuration, readerShellClass, readerPaperClass, readerTextColor } from "@/utils/format";
import { getConverter } from "@/utils/text-conversion";
import { renderMarkdownWithToc } from "@/features/library/toc/markdown-toc";
import { lastAnchorBeforeThreshold } from "@/features/library/toc/current";
import { TocList } from "@/features/library/toc/TocList";
import type { TocEntry } from "@/features/library/toc/tree";
import { splitTxtChapters } from "@/features/library/toc/txt-chapters";
export { splitTxtChapters } from "@/features/library/toc/txt-chapters";
export type { TxtChapter } from "@/features/library/toc/txt-chapters";


const HIGHLIGHT_COLORS_TXT: { value: HighlightColor; label: string; hex: string }[] = [
  { value: "yellow", label: "黄色", hex: "#fde047" },
  { value: "red", label: "红色", hex: "#fca5a5" },
  { value: "green", label: "绿色", hex: "#86efac" },
  { value: "blue", label: "蓝色", hex: "#93c5fd" },
  { value: "purple", label: "紫色", hex: "#d8b4fe" },
];

const HIGHLIGHT_MARK_STYLES: Record<HighlightColor, string> = {
  yellow: "background:rgba(255,235,59,0.4)",
  red: "background:rgba(244,67,54,0.3)",
  green: "background:rgba(76,175,80,0.3)",
  blue: "background:rgba(33,150,243,0.3)",
  purple: "background:rgba(156,39,176,0.3)",
};

function sanitizeMarkdownHtml(html: string): string {
  const clean = DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "a",
      "blockquote",
      "br",
      "code",
      "div",
      "em",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "hr",
      "li",
      "ol",
      "p",
      "pre",
      "s",
      "span",
      "strong",
      "table",
      "tbody",
      "td",
      "th",
      "thead",
      "tr",
      "ul"
    ],
    ALLOWED_ATTR: ["href", "title", "target", "rel", "id", "class"],
    ALLOW_DATA_ATTR: false,
    FORBID_ATTR: ["style"],
    ALLOWED_URI_REGEXP: /^(?:(?:(?:https?|mailto|file|ftp):)|#|\/)/i
  });
  return clean.replace(/<a\b([^>]*)>/gi, (match, attrs: string) => {
    if (/\btarget=/i.test(attrs) && /\brel=/i.test(attrs)) return match;
    const target = /\btarget=/i.test(attrs) ? "" : ' target="_blank"';
    const rel = /\brel=/i.test(attrs) ? "" : ' rel="noopener noreferrer"';
    return `<a${attrs}${target}${rel}>`;
  });
}

function renderPlainText(content: string) {
  return content.split(/\n{2,}/).map((paragraph, index) => (
    <p key={index} className="mb-5 whitespace-pre-wrap">
      {paragraph}
    </p>
  ));
}

function renderChapterParagraphs(text: string) {
  return text.split(/\n{2,}/).map((paragraph, index) => (
    <p key={index} className="mb-5 whitespace-pre-wrap">
      {paragraph}
    </p>
  ));
}

function findCurrentHeadingAnchor(scroller: HTMLDivElement | null): { id: string; title: string } | undefined {
  if (!scroller) return undefined;
  const headings = Array.from(scroller.querySelectorAll<HTMLElement>("h1, h2, h3, h4, h5, h6"));
  const containerTop = scroller.getBoundingClientRect().top;
  let best: HTMLElement | null = null;
  for (const h of headings) {
    const offset = h.getBoundingClientRect().top - containerTop;
    if (offset <= 20) best = h;
    else break;
  }
  if (!best?.id) return undefined;
  return { id: best.id, title: best.textContent?.trim() ?? "" };
}

function TextReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const content = useLibraryStore((state) => state.activeContent);
  const progress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const settings = useLibraryStore((state) => state.readerSettings);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setScreen = useAppStore((state) => state.setScreen);
  const showToast = useUIStore((state) => state.showToast);
  const scrollerRef = useRef<HTMLDivElement>(null);
  const [selectionToolbar, setSelectionToolbar] = useState<{
    visible: boolean;
    x: number;
    y: number;
    charOffset: number;
    charLength: number;
    text: string;
  } | null>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [highlights, setHighlights] = useState<HighlightItem[]>([]);
  const highlightsRef = useRef<HighlightItem[]>([]);
  const [tocCollapsed, setTocCollapsed] = useState(false);
  const [settingsDrawerOpen, setSettingsDrawerOpen] = useState(false);
  const [currentAnchorId, setCurrentAnchorId] = useState<string | undefined>(undefined);
  const { scheduleSave, flushProgress, getCurrentLocation } = useReaderProgress(scrollerRef);
  const excerpt = useReaderExcerpt();

  // --- Annotations ---
  const loadAnnotations = useCallback(async (bookId: string) => {
    try {
      const hl = await getHighlightsByBook(bookId);
      setHighlights(hl);
      highlightsRef.current = hl;
    } catch {
      // ignore
    }
  }, []);

  const addTxtHighlight = useCallback(async (charOffset: number, charLength: number, text: string, color: HighlightColor) => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    if (!bookId || !text.trim()) return;
    const now = new Date().toISOString();
    const item: HighlightItem = {
      id: crypto.randomUUID(),
      bookId,
      charOffset,
      charLength,
      text: text.slice(0, 2000),
      color,
      createdAt: now,
      updatedAt: now,
    };
    const saved = await saveHighlight(item);
    setHighlights((prev) => {
      const next = [...prev, saved];
      highlightsRef.current = next;
      return next;
    });
    showToast({ tone: "success", title: "已添加高亮", body: text.slice(0, 50) });
  }, [showToast]);


  const { recordInteraction, endTracking } = useReadingSessionTracker(scrollerRef, getCurrentLocation);

  const didRestoreScrollRef = useRef(false);
  const restoreTimerRef = useRef<number | undefined>(undefined);
  useEffect(() => {
    didRestoreScrollRef.current = false;
    restoreTimerRef.current = undefined;
    return () => {
      if (restoreTimerRef.current !== undefined) window.clearTimeout(restoreTimerRef.current);
    };
  }, [activeBook?.id]);

  useEffect(() => {
    const scroller = scrollerRef.current;
    const scrollTop = progress?.currentLocation?.scroll?.scrollTop;
    if (!settings?.restoreLastPosition || !scroller || typeof scrollTop !== "number") return;
    if (didRestoreScrollRef.current) return;
    didRestoreScrollRef.current = true;
    restoreTimerRef.current = window.setTimeout(() => {
      scroller.scrollTop = scrollTop;
    }, 80);
  }, [activeBook?.id, progress?.currentLocation?.scroll?.scrollTop, settings?.restoreLastPosition]);

  useEffect(() => {
    if (activeBook?.id) loadAnnotations(activeBook.id);
  }, [activeBook?.id, loadAnnotations]);

  // Click outside to close toolbar
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

  // --- 目录数据：MD 从 token 流提取，TXT 从章节切分派生 ---
  const md = useMemo(
    () => (activeBook?.format === "md" ? renderMarkdownWithToc(content) : { html: "", toc: [] }),
    [activeBook?.format, content]
  );
  const markdownHtml = md.html;
  const mdToc = md.toc;

  const txtChapters = useMemo(() => {
    if (activeBook?.format !== "txt") return [];
    return splitTxtChapters(content);
  }, [activeBook?.format, content]);

  const txtToc = useMemo(() => {
    if (txtChapters.length <= 1) return [];
    return txtChapters
      .map((ch, idx) => ({ level: 1, title: ch.title, id: `txt-chapter-${idx}` }))
      .filter((item) => item.title !== "");
  }, [txtChapters]);

  const tocEntries: TocEntry[] = useMemo(() => {
    if (activeBook?.format === "md") {
      return mdToc.map((item) => ({ id: item.id, label: item.title, level: item.level }));
    }
    if (activeBook?.format === "txt") {
      return txtToc.map((item) => ({ id: item.id, label: item.title, level: item.level }));
    }
    return [];
  }, [activeBook?.format, mdToc, txtToc]);

  // Render highlights as <mark> tags in the DOM
  useEffect(() => {
    const container = scrollerRef.current?.querySelector("article");
    if (!container || highlights.length === 0) return;

    const applyHighlight = (hl: HighlightItem) => {
      const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
      let node: Text | null;
      while ((node = walker.nextNode() as Text | null)) {
        if (node.parentElement?.tagName === "MARK") continue;
        const text = node.textContent ?? "";
        const idx = text.indexOf(hl.text);
        if (idx === -1) continue;
        try {
          const range = document.createRange();
          range.setStart(node, idx);
          range.setEnd(node, idx + hl.text.length);
          const mark = document.createElement("mark");
          mark.className = "txt-hl";
          mark.setAttribute("data-hl-id", hl.id);
          mark.style.cssText = HIGHLIGHT_MARK_STYLES[hl.color] || HIGHLIGHT_MARK_STYLES.yellow;
          range.surroundContents(mark);
        } catch {
          // ignore if range spans multiple nodes
        }
        break;
      }
    };

    for (const hl of highlights) {
      applyHighlight(hl);
    }

    return () => {
      container.querySelectorAll("mark.txt-hl").forEach((el) => {
        const parent = el.parentNode;
        if (parent) {
          while (el.firstChild) parent.insertBefore(el.firstChild, el);
          parent.removeChild(el);
          parent.normalize();
        }
      });
    };
  }, [highlights, content, activeBook?.format, markdownHtml]);

  // --- 繁简转换 ---
  const [convertedContent, setConvertedContent] = useState(content);
  useEffect(() => {
    if (!settings?.textConversion || settings.textConversion === "none") {
      setConvertedContent(content);
      return;
    }
    let cancelled = false;
    getConverter(settings.textConversion).then((convert) => {
      if (!cancelled) setConvertedContent(convert(content));
    });
    return () => { cancelled = true; };
  }, [content, settings?.textConversion]);

  const convertedMd = useMemo(
    () => (activeBook?.format === "md" ? renderMarkdownWithToc(convertedContent) : { html: "", toc: [] }),
    [activeBook?.format, convertedContent]
  );
  const convertedMarkdownHtml = convertedMd.html;

  // --- 当前章锚点跟踪（滚动模式）：缓存的锚点元素取"视口顶之上最近者" ---
  const anchorsRef = useRef<Map<string, HTMLElement>>(new Map());
  const anchorRafRef = useRef<number | undefined>(undefined);
  const tocAnchorKey = useMemo(() => tocEntries.map((entry) => entry.id).join("\n"), [tocEntries]);

  useEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller || !tocAnchorKey) {
      anchorsRef.current = new Map();
      setCurrentAnchorId(undefined);
      return;
    }
    const map = new Map<string, HTMLElement>();
    for (const id of tocAnchorKey.split("\n")) {
      const el = scroller.querySelector<HTMLElement>(`#${CSS.escape(id)}`);
      if (el) map.set(id, el);
    }
    anchorsRef.current = map;
    // 打开即计算一次当前章（恢复滚动位置后首屏就有高亮，不必等首次滚动）
    const containerTop = scroller.getBoundingClientRect().top;
    const offsets = Array.from(map, ([id, el]) => ({
      id,
      offsetTop: el.getBoundingClientRect().top - containerTop
    }));
    setCurrentAnchorId((prev) => {
      const current = lastAnchorBeforeThreshold(offsets, 40);
      return prev === current ? prev : current;
    });
  }, [tocAnchorKey, markdownHtml, convertedMarkdownHtml, content, activeBook?.format]);

  const updateCurrentAnchor = useCallback(() => {
    const scroller = scrollerRef.current;
    if (!scroller || anchorsRef.current.size === 0) return;
    const containerTop = scroller.getBoundingClientRect().top;
    const offsets = Array.from(anchorsRef.current, ([id, el]) => ({
      id,
      offsetTop: el.getBoundingClientRect().top - containerTop
    }));
    // anchorsRef 按目录顺序插入，即文档顺序，可安全地做"最后一个已越过阈值"
    const current = lastAnchorBeforeThreshold(offsets, 40);
    setCurrentAnchorId((prev) => (prev === current ? prev : current));
  }, []);

  useEffect(() => {
    return () => {
      if (anchorRafRef.current !== undefined) window.cancelAnimationFrame(anchorRafRef.current);
    };
  }, []);

  const handleActivity = useCallback(() => {
    recordInteraction();
    scheduleSave();
  }, [recordInteraction, scheduleSave]);

  const handleScrollActivity = useCallback(() => {
    handleActivity();
    if (anchorRafRef.current === undefined) {
      anchorRafRef.current = window.requestAnimationFrame(() => {
        anchorRafRef.current = undefined;
        updateCurrentAnchor();
      });
    }
  }, [handleActivity, updateCurrentAnchor]);

  const jumpToTocEntry = useCallback((id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  if (!activeBook || !settings) {
    return (
      <ShellPanel className="h-full border-0">
        <EmptyState title="没有打开资料" body="从书库中选择一份 TXT、Markdown 或 EPUB，即可浏览目录、复制选文或摘录到资料卡。" />
      </ShellPanel>
    );
  }

  const progressPercent = Math.round((progress?.progressPercent ?? 0) * 100);

  const openExcerptFromSelection = useCallback(() => {
    if (!selectionToolbar?.visible || !selectionToolbar.text.trim()) {
      showToast({ tone: "warning", title: "没有选中文字", body: "请先在正文中选中一段文字再摘录。" });
      return;
    }
    const location = getCurrentLocation();
    const currentHeading = findCurrentHeadingAnchor(scrollerRef.current);
    const headingHref = currentHeading ? `#${currentHeading.id}` : undefined;
    const ctx: ExcerptBuildContext = {
      bookId: activeBook.id,
      bookTitle: activeBook.title,
      bookAuthor: activeBook.author,
      format: activeBook.format,
      chapterTitle: currentHeading?.title,
      progressPercent: location?.progressPercent,
      excerpt: selectionToolbar.text,
      href: headingHref,
      charOffset: selectionToolbar.charOffset,
      charLength: selectionToolbar.charLength,
      scrollTop: location?.scroll?.scrollTop
    };
    void excerpt.openPicker(ctx);
    setSelectionToolbar(null);
    setShowColorPicker(false);
  }, [selectionToolbar, activeBook, getCurrentLocation, excerpt, showToast]);

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
      <header className="paper-topbar flex items-center gap-3 px-5">
        <BookOpen size={18} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold text-paper-ink">{activeBook.title}</div>
          <div className="text-xs text-paper-muted">
            阅读进度：{progressPercent}% · 本书累计 {formatDuration(progress?.totalReadingTimeMs)}
          </div>
        </div>
        <Button variant="quiet" onClick={() => setTocCollapsed((value) => !value)}>
          <List size={16} />
          目录
        </Button>
        <Button variant="quiet" onClick={() => setSettingsDrawerOpen(true)}>
          <Settings size={16} />
          设置
        </Button>
        <Button
          variant="quiet"
          onClick={async () => {
            await flushProgress();
            await endTracking("leave-reader");
            setScreen("stats");
          }}
        >
          <ChartColumn size={16} />
          统计
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
            setScreen("projects");
          }}
        >
          返回首页
        </Button>
      </header>

      <div className={`grid min-h-0 ${tocCollapsed ? "grid-cols-[1fr_56px]" : "grid-cols-[1fr_320px]"}`}>
        <div
          ref={scrollerRef}
          className={`min-h-0 overflow-auto ${readerShellClass(settings.readerBackground)}`}
          onScroll={handleScrollActivity}
          onWheel={handleActivity}
          onKeyDown={handleActivity}
          onPointerDown={handleActivity}
          onMouseUp={() => {
            handleActivity();
            const sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.toString().trim()) {
              setSelectionToolbar(null);
              return;
            }
            const text = sel.toString().trim().slice(0, 2000);
            if (!sel.rangeCount) return;
            const range = sel.getRangeAt(0);
            const rect = range.getBoundingClientRect();
            const scroller = scrollerRef.current;
            if (!scroller) return;
            // Compute charOffset relative to full content
            const selectedStr = sel.toString();
            // Try to find the selected text position in the article
            const articleEl = scroller.querySelector("article");
            if (!articleEl) return;
            const articleText = articleEl.textContent ?? "";
            const selStart = articleText.indexOf(selectedStr);
            const charOffset = selStart >= 0 ? selStart : 0;
            const charLength = selectedStr.length;
            setSelectionToolbar({
              visible: true,
              x: rect.left + rect.width / 2,
              y: rect.top - 8,
              charOffset,
              charLength,
              text,
            });
          }}
          tabIndex={0}
        >
          {/* Floating selection toolbar */}
          {selectionToolbar?.visible && (
            <div
              className="fixed z-50 flex items-center gap-0.5 rounded-lg bg-stone-800 px-2 py-1.5 text-sm text-white shadow-xl"
              style={{ left: selectionToolbar.x, top: selectionToolbar.y, transform: "translate(-50%, -100%)" }}
              onMouseDown={(e) => e.stopPropagation()}
            >
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
                    {HIGHLIGHT_COLORS_TXT.map((c) => (
                      <button
                        key={c.value}
                        className="h-5 w-5 rounded-full border border-stone-600 transition-transform hover:scale-110"
                        style={{ background: c.hex }}
                        title={c.label}
                        onClick={async (e) => {
                          e.stopPropagation();
                          await addTxtHighlight(selectionToolbar.charOffset, selectionToolbar.charLength, selectionToolbar.text, c.value);
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
                className="rounded px-2 py-1 hover:bg-stone-700 text-copper-300"
                title="摘录到资料"
                onClick={() => void openExcerptFromSelection()}
              >
                <Quote size={14} />
              </button>
              <button
                className="rounded px-2 py-1 hover:bg-stone-700"
                title="关闭"
                onClick={() => {
                  window.getSelection()?.removeAllRanges();
                  setSelectionToolbar(null);
                  setShowColorPicker(false);
                }}
              >
                <X size={14} />
              </button>
            </div>
          )}
          <article
            className={`mx-auto my-8 rounded-xl px-10 py-10 ${readerPaperClass(settings.readerBackground)} ${
              activeBook.format === "md" ? "markdown-reader reader-heading-scale" : ""
            }`}
            style={useMemo(
              () => ({
                maxWidth: 900,
                fontSize: settings.fontSize,
                lineHeight: settings.lineHeight,
                letterSpacing: `${settings.letterSpacing ?? 0}em`,
                fontFamily: settings.fontFamily ? `'${settings.fontFamily}', serif` : undefined,
                paddingLeft: settings.pageMargin,
                paddingRight: settings.pageMargin
              }),
              [settings.fontSize, settings.lineHeight, settings.letterSpacing, settings.pageMargin, settings.fontFamily]
            )}
          >
            {activeBook.format !== "md" && (
              <style>{`article p { margin-bottom: ${settings.paragraphSpacing ?? 1.0}em; }`}</style>
            )}
            {activeBook.format === "md" ? (
              <div dangerouslySetInnerHTML={{ __html: sanitizeMarkdownHtml(convertedMarkdownHtml) }} />
            ) : txtChapters.length > 1 ? (
              txtChapters.map((chapter, idx) => (
                <div key={idx}>
                  {chapter.title && (
                    <h2
                      id={`txt-chapter-${idx}`}
                      className="text-xl font-bold mb-4 mt-8 first:mt-0"
                      style={{ color: readerTextColor(settings.readerBackground) }}
                    >
                      {chapter.title}
                    </h2>
                  )}
                  {renderChapterParagraphs(
                    convertedContent.slice(chapter.contentStart, chapter.endIndex)
                  )}
                </div>
              ))
            ) : (
              renderPlainText(convertedContent)
            )}
          </article>
        </div>
        {tocCollapsed ? (
          <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
            <div className="grid gap-3">
              <button
                className="rounded-md border border-paper-line bg-paper-panel p-2 text-xs text-paper-muted hover:text-paper-ink"
                onClick={() => setTocCollapsed(false)}
              >
                目录
              </button>
              <div className="text-center text-[11px] leading-5 text-paper-muted">{progressPercent}%</div>
            </div>
          </ShellPanel>
        ) : (
          <ShellPanel className="flex min-h-0 flex-col overflow-hidden border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
            <div className="mb-3 flex items-center justify-between border-b border-paper-line pb-2">
              <div className="text-sm font-semibold text-paper-ink">
                {activeBook.format === "md" ? "Markdown 目录" : "章节目录"}
              </div>
              <Button variant="quiet" className="h-7 px-2 text-xs" onClick={() => setTocCollapsed(true)}>
                收起
              </Button>
            </div>
            <TocList
              className="min-h-0 flex-1"
              entries={tocEntries}
              currentId={currentAnchorId}
              onJump={jumpToTocEntry}
              emptyText={activeBook.format === "md" ? "未检测到标题" : "未检测到章节"}
            />
          </ShellPanel>
        )}
      </div>
      <ReaderSettingsDrawer
        open={settingsDrawerOpen}
        settings={settings}
        onClose={() => setSettingsDrawerOpen(false)}
        onSettingsChange={setReaderSettings}
        onAfterChange={() => handleActivity()}
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

export function ReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  if (activeBook?.format === "epub") return <EpubReaderPage />;
  return <TextReaderPage />;
}
