import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, BookOpen, Bookmark, ChartColumn, Copy, Highlighter, List, Quote, Scissors, Settings, X } from "lucide-react";
import DOMPurify from "dompurify";
import { Button, EmptyState, ShellPanel, TextInput } from "@/components/ui";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import { ReaderSettingsDrawer } from "@/features/library/ReaderSettingsDrawer";
import { ReaderSidePanel, type SidePanelTab } from "@/features/library/ReaderSidePanel";
import { useReaderExcerpt } from "@/features/library/useReaderExcerpt";
import type { ExcerptBuildContext } from "@/features/library/useReaderExcerpt";
import { useReaderProgress } from "@/hooks/useReaderProgress";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { getBookmarksByBook, getHighlightsByBook, saveBookmark, saveHighlight, deleteBookmark as removeBookmarkById, deleteHighlight } from "@/services/annotation-service";
import { saveTxtTocOverrides } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { BookmarkItem, ExcerptResult, ExcerptTarget, HighlightColor, HighlightItem } from "@/types/library";
import { formatDuration, readerShellClass, readerPaperClass, readerTextColor } from "@/utils/format";
import { getConverter, convertTextSync } from "@/utils/text-conversion";
import { renderMarkdownWithToc } from "@/features/library/toc/markdown-toc";
import { computeAnchorScrollTop, computeTextAnchor, currentAnchorIdFromSpans, type AnchorSpan } from "@/features/library/toc/anchor";
import type { TocEntry } from "@/features/library/toc/tree";
import { chaptersFromOverrides, splitTxtChapters } from "@/features/library/toc/txt-chapters";
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

interface DraftChapter {
  title: string;
  startIndex: number;
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
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const highlightsRef = useRef<HighlightItem[]>([]);
  const [tocCollapsed, setTocCollapsed] = useState(false);
  const [settingsDrawerOpen, setSettingsDrawerOpen] = useState(false);
  const [sidePanelTab, setSidePanelTab] = useState<SidePanelTab>("toc");
  const [currentAnchorId, setCurrentAnchorId] = useState<string | undefined>(undefined);
  const excerpt = useReaderExcerpt();

  // --- 目录编辑模式（TXT 手动修正章节表） ---
  const [tocEditMode, setTocEditMode] = useState(false);
  const [draftChapters, setDraftChapters] = useState<DraftChapter[]>([]);
  const [splitIndex, setSplitIndex] = useState<number | null>(null);
  const [renamingIndex, setRenamingIndex] = useState<number | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [savingOverrides, setSavingOverrides] = useState(false);

  // --- 目录数据：MD 从 token 流提取，TXT 从章节切分（或用户修正表）派生 ---
  const md = useMemo(
    () => (activeBook?.format === "md" ? renderMarkdownWithToc(content) : { html: "", toc: [] }),
    [activeBook?.format, content]
  );
  const markdownHtml = md.html;

  const tocOverrides = activeBook?.format === "txt" ? activeBook.text?.tocOverrides : undefined;
  const txtChapters = useMemo(() => {
    if (activeBook?.format !== "txt") return [];
    if (tocOverrides && tocOverrides.chapters.length > 0) return chaptersFromOverrides(content, tocOverrides.chapters);
    return splitTxtChapters(content);
  }, [activeBook?.format, content, tocOverrides]);

  const tocEntries: TocEntry[] = useMemo(() => {
    if (activeBook?.format === "md") {
      return md.toc.map((item) => ({ id: item.id, label: item.title, level: item.level }));
    }
    if (activeBook?.format === "txt") {
      if (txtChapters.length <= 1) return [];
      return txtChapters
        .map((ch, idx) => ({ level: 1, label: ch.title, id: `txt-chapter-${idx}` }))
        .filter((item) => item.label !== "");
    }
    return [];
  }, [activeBook?.format, md.toc, txtChapters]);

  const tocEntriesRef = useRef(tocEntries);
  tocEntriesRef.current = tocEntries;
  const txtChaptersRef = useRef(txtChapters);
  txtChaptersRef.current = txtChapters;
  const contentLengthRef = useRef(content.length);
  contentLengthRef.current = content.length;

  // --- 锚点 span 采集与缓存（进度锚定 / 当前章 / 书签跳转共用） ---
  const spansCacheRef = useRef<AnchorSpan[]>([]);
  const spansVersionRef = useRef("");

  const ensureSpans = useCallback((): AnchorSpan[] => {
    const scroller = scrollerRef.current;
    const format = activeBook?.format;
    if (!scroller || (format !== "txt" && format !== "md")) return [];
    const versionKey = [
      activeBook?.id,
      format,
      content.length,
      txtChapters.length,
      tocEntries.length,
      settings?.fontSize,
      settings?.lineHeight,
      settings?.letterSpacing,
      settings?.paragraphSpacing,
      settings?.pageMargin,
      settings?.fontFamily,
      settings?.textConversion,
      tocCollapsed,
      window.innerWidth
    ].join("|");
    if (spansVersionRef.current === versionKey && spansCacheRef.current.length > 0) return spansCacheRef.current;
    const scrollerTop = scroller.getBoundingClientRect().top;
    const scrollTopNow = scroller.scrollTop;
    const spans: AnchorSpan[] = [];
    if (format === "txt" && txtChapters.length > 1) {
      txtChapters.forEach((ch, idx) => {
        if (!ch.title) return;
        const el = document.getElementById(`txt-chapter-${idx}`);
        if (!el) return;
        spans.push({
          id: `txt-chapter-${idx}`,
          top: el.getBoundingClientRect().top - scrollerTop + scrollTopNow,
          title: ch.title,
          charStart: ch.startIndex,
          charEnd: ch.endIndex
        });
      });
    } else if (format === "md") {
      for (const entry of tocEntries) {
        const el = document.getElementById(entry.id);
        if (!el) continue;
        spans.push({ id: entry.id, top: el.getBoundingClientRect().top - scrollerTop + scrollTopNow, title: entry.label });
      }
    }
    // 空结果不缓存（DOM 可能尚未渲染完成），让下次调用重试
    if (spans.length > 0) {
      spansCacheRef.current = spans;
      spansVersionRef.current = versionKey;
    }
    return spans;
  }, [activeBook?.id, activeBook?.format, content.length, txtChapters, tocEntries, settings?.fontSize, settings?.lineHeight, settings?.letterSpacing, settings?.paragraphSpacing, settings?.pageMargin, settings?.fontFamily, settings?.textConversion, tocCollapsed]);

  const ensureSpansRef = useRef(ensureSpans);
  ensureSpansRef.current = ensureSpans;

  const getTextAnchor = useCallback(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return undefined;
    const spans = ensureSpansRef.current();
    // 有目录但锚点未就绪时不写锚点（等渲染完成）；无章节书用全局字符比例锚定
    if (tocEntriesRef.current.length > 0 && spans.length === 0) return undefined;
    return computeTextAnchor(spans, scroller.scrollTop, scroller.scrollHeight, scroller.clientHeight, contentLengthRef.current);
  }, []);

  const updateCurrentAnchor = useCallback(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    if (tocEntriesRef.current.length === 0) {
      setCurrentAnchorId((prev) => (prev === undefined ? prev : undefined));
      return;
    }
    const spans = ensureSpansRef.current();
    if (spans.length === 0) return;
    const current = currentAnchorIdFromSpans(spans, scroller.scrollTop);
    setCurrentAnchorId((prev) => (prev === current ? prev : current));
  }, []);

  // 布局参数变化（字号/收起/换书等）后重算当前章，首屏即有高亮
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => updateCurrentAnchor());
    return () => window.cancelAnimationFrame(frame);
  }, [updateCurrentAnchor, txtChapters, markdownHtml, tocCollapsed, activeBook?.id, settings?.fontSize, settings?.lineHeight, settings?.letterSpacing, settings?.paragraphSpacing, settings?.pageMargin, settings?.fontFamily]);

  const activeTextJump = useLibraryStore((state) => state.activeTextJump);
  const { scheduleSave, flushProgress, getCurrentLocation } = useReaderProgress(scrollerRef, getTextAnchor);
  const { recordInteraction, endTracking } = useReadingSessionTracker(scrollerRef, getCurrentLocation);

  // --- Annotations ---
  const loadAnnotations = useCallback(async (bookId: string) => {
    try {
      const [hl, bm] = await Promise.all([getHighlightsByBook(bookId), getBookmarksByBook(bookId)]);
      setHighlights(hl);
      highlightsRef.current = hl;
      setBookmarks(bm);
    } catch {
      // ignore
    }
  }, []);

  const addTxtHighlight = useCallback(async (charOffset: number, charLength: number, text: string, color: HighlightColor) => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    if (!bookId || !text.trim()) return;
    const now = new Date().toISOString();
    const currentHeading = findCurrentHeadingAnchor(scrollerRef.current);
    const item: HighlightItem = {
      id: crypto.randomUUID(),
      bookId,
      charOffset,
      charLength,
      text: text.slice(0, 2000),
      color,
      chapterTitle: currentHeading?.title,
      // 稳定锚点：TXT 记字符偏移，MD 记标题锚点 id（跨布局可跳回）
      locator: {
        version: 2,
        bookId,
        format: useLibraryStore.getState().activeBook?.format === "md" ? "markdown" : "txt",
        chapterId: currentHeading?.id,
        textOffset: charOffset,
        updatedAt: Date.now()
      },
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

  const scrollToCharOffset = useCallback((charOffset: number) => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    const target = computeAnchorScrollTop(ensureSpansRef.current(), { charOffset }, scroller.scrollHeight, scroller.clientHeight, contentLengthRef.current);
    if (typeof target === "number") {
      scroller.scrollTop = target;
    } else {
      // 锚点不可用（无章节/未渲染）：退回全局比例
      const maxScroll = scroller.scrollHeight - scroller.clientHeight;
      if (contentLengthRef.current > 0) scroller.scrollTop = Math.round((charOffset / contentLengthRef.current) * maxScroll);
    }
    window.requestAnimationFrame(() => updateCurrentAnchor());
  }, [updateCurrentAnchor]);

  const jumpToBookmark = useCallback((bm: BookmarkItem) => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    if (bm.href?.startsWith("#")) {
      document.getElementById(bm.href.slice(1))?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    if (typeof bm.charOffset === "number" && txtChaptersRef.current.length > 1) {
      scrollToCharOffset(bm.charOffset);
      return;
    }
    if (typeof bm.scrollTop === "number") {
      scroller.scrollTop = bm.scrollTop;
      window.requestAnimationFrame(() => updateCurrentAnchor());
    }
  }, [scrollToCharOffset, updateCurrentAnchor]);

  const addBookmarkAtCurrent = useCallback(async () => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    if (!bookId) return;
    const location = getCurrentLocation();
    const anchor = getTextAnchor();
    const chapterTitle = anchor?.headingPath?.[0];
    const item: BookmarkItem = {
      id: crypto.randomUUID(),
      bookId,
      label: chapterTitle || `书签 ${Math.round((location?.progressPercent ?? 0) * 100)}%`,
      chapterTitle,
      charOffset: anchor?.charOffset,
      scrollTop: location?.scroll?.scrollTop,
      href: anchor?.chapterRef ? `#${anchor.chapterRef}` : undefined,
      progressPercent: location?.progressPercent,
      createdAt: new Date().toISOString(),
    };
    const saved = await saveBookmark(item);
    setBookmarks((prev) => [saved, ...prev]);
    showToast({ tone: "success", title: "已添加书签", body: item.label });
  }, [getCurrentLocation, getTextAnchor, showToast]);

  const jumpToHighlight = useCallback((hl: HighlightItem) => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    if (typeof hl.charOffset === "number" && txtChaptersRef.current.length > 1) {
      scrollToCharOffset(hl.charOffset);
      return;
    }
    const anchorId = hl.locator?.chapterId ?? (hl.locator?.fragment ? hl.locator.fragment.replace(/^#/, "") : undefined);
    if (anchorId && document.getElementById(anchorId)) {
      document.getElementById(anchorId)?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    if (typeof hl.charOffset === "number" && contentLengthRef.current > 0) {
      const maxScroll = scroller.scrollHeight - scroller.clientHeight;
      scroller.scrollTop = Math.round((hl.charOffset / contentLengthRef.current) * maxScroll);
    }
  }, [scrollToCharOffset]);

  const didRestoreScrollRef = useRef(false);
  const restoreTimerRef = useRef<number | undefined>(undefined);
  useEffect(() => {
    didRestoreScrollRef.current = false;
    restoreTimerRef.current = undefined;
    return () => {
      if (restoreTimerRef.current !== undefined) window.clearTimeout(restoreTimerRef.current);
    };
  }, [activeBook?.id]);

  // 恢复阅读位置：优先按文本锚点（章节+章内比例）换算新布局下的位置，
  // 锚点不可用时回退保存的 scrollTop；章节锚点可能晚于首帧渲染，带重试。
  useEffect(() => {
    if (!settings?.restoreLastPosition) return;
    const loc = progress?.currentLocation;
    if (!loc || didRestoreScrollRef.current) return;
    const savedScrollTop = loc.scroll?.scrollTop;
    if (!loc.text?.chapterRef && typeof savedScrollTop !== "number") return;
    didRestoreScrollRef.current = true;
    const tryRestore = (attempt: number) => {
      const scroller = scrollerRef.current;
      if (!scroller) return;
      const needsSpans = Boolean(loc.text?.chapterRef) && tocEntriesRef.current.length > 0;
      const spans = needsSpans ? ensureSpansRef.current() : [];
      if (needsSpans && spans.length === 0) {
        if (attempt < 25) restoreTimerRef.current = window.setTimeout(() => tryRestore(attempt + 1), 100);
        return;
      }
      const anchorTarget = loc.text ? computeAnchorScrollTop(spans, loc.text, scroller.scrollHeight, scroller.clientHeight, contentLengthRef.current) : undefined;
      const target = anchorTarget ?? savedScrollTop;
      if (typeof target === "number") scroller.scrollTop = target;
      window.requestAnimationFrame(() => updateCurrentAnchor());
    };
    restoreTimerRef.current = window.setTimeout(() => tryRestore(0), 80);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeBook?.id, progress?.currentLocation, settings?.restoreLastPosition]);

  useEffect(() => {
    if (activeBook?.id) loadAnnotations(activeBook.id);
  }, [activeBook?.id, loadAnnotations]);

  // 搜索命中跳转：点搜索结果打开书后直接跳到命中处；显式跳转优先于位置恢复，
  // 章节锚点可能晚于首帧渲染，带重试；只消费属于当前书的请求。
  useEffect(() => {
    if (!activeBook || !activeTextJump || activeTextJump.bookId !== activeBook.id) return;
    didRestoreScrollRef.current = true;
    let cancelled = false;
    const tryJump = (attempt: number) => {
      if (cancelled) return;
      const scroller = scrollerRef.current;
      if (!scroller) return;
      const needsSpans = tocEntriesRef.current.length > 0;
      const spans = needsSpans ? ensureSpansRef.current() : [];
      if (needsSpans && spans.length === 0) {
        if (attempt < 25) restoreTimerRef.current = window.setTimeout(() => tryJump(attempt + 1), 100);
        return;
      }
      scrollToCharOffset(activeTextJump.charOffset);
      useLibraryStore.setState({ activeTextJump: undefined });
    };
    restoreTimerRef.current = window.setTimeout(() => tryJump(0), 80);
    return () => {
      cancelled = true;
    };
  }, [activeBook?.id, activeTextJump, scrollToCharOffset]);

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
  const [converterTick, setConverterTick] = useState(0);
  useEffect(() => {
    if (!settings?.textConversion || settings.textConversion === "none") {
      setConvertedContent(content);
      return;
    }
    let cancelled = false;
    getConverter(settings.textConversion).then((convert) => {
      if (!cancelled) {
        setConvertedContent(convert(content));
        setConverterTick((tick) => tick + 1);
      }
    });
    return () => { cancelled = true; };
  }, [content, settings?.textConversion]);

  // 分章正文的转换：切片基于原文偏移、逐章转换，避免整本转换字长变化让
  // 章节边界漂移（opencc 存在少量 1→多字映射）。转换器异步就绪后 tick 触发重渲染。
  const txtChapterBodies = useMemo(() => {
    if (activeBook?.format !== "txt" || txtChapters.length <= 1) return null;
    const mode = settings?.textConversion ?? "none";
    if (mode === "none") return null;
    return txtChapters.map((ch) => convertTextSync(content.slice(ch.contentStart, ch.endIndex), mode));
    // converterTick 参与依赖：转换器异步加载完成后重算
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeBook?.format, txtChapters, content, settings?.textConversion, converterTick]);

  const convertedMd = useMemo(
    // 繁简转换只替换文本不改变标题结构：按位置复用原文目录 id，锚点跨转换稳定
    () => (activeBook?.format === "md" ? renderMarkdownWithToc(convertedContent, md.toc) : { html: "", toc: [] }),
    [activeBook?.format, convertedContent, md.toc]
  );
  const convertedMarkdownHtml = convertedMd.html;

  const handleActivity = useCallback(() => {
    recordInteraction();
    scheduleSave();
  }, [recordInteraction, scheduleSave]);

  const handleScrollActivity = useCallback(() => {
    handleActivity();
    updateCurrentAnchor();
  }, [handleActivity, updateCurrentAnchor]);

  const jumpToTocEntry = useCallback((id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  // --- 目录编辑模式操作 ---
  const enterTocEditMode = useCallback(() => {
    const draft = txtChapters
      .filter((ch) => ch.title !== "")
      .map((ch) => ({ title: ch.title, startIndex: ch.startIndex }));
    setDraftChapters(draft);
    setSplitIndex(null);
    setRenamingIndex(null);
    setTocEditMode(true);
  }, [txtChapters]);

  const persistTocOverrides = useCallback(async (chapters: DraftChapter[] | null) => {
    const bookId = useLibraryStore.getState().activeBook?.id;
    if (!bookId) return;
    setSavingOverrides(true);
    try {
      const updated = await saveTxtTocOverrides({ bookId, overrides: chapters ? { version: 1, chapters } : null });
      useLibraryStore.setState({ activeBook: updated });
      showToast({
        tone: "success",
        title: chapters ? "章节目录已更新" : "已恢复自动识别",
        body: chapters ? `共 ${chapters.length} 章，全书跳转与进度章节已按新目录生效。` : "章节表已还原为自动识别结果。"
      });
      setTocEditMode(false);
      setSplitIndex(null);
      setRenamingIndex(null);
    } catch (error) {
      showToast({ tone: "error", title: "章节目录保存失败", body: error instanceof Error ? error.message : String(error) });
    } finally {
      setSavingOverrides(false);
    }
  }, [showToast]);

  const addChapterStartFromSelection = useCallback((charOffset: number) => {
    const text = content;
    if (!text || charOffset < 0 || charOffset >= text.length) return;
    const lineStart = text.lastIndexOf("\n", charOffset - 1) + 1;
    const nl = text.indexOf("\n", charOffset);
    const lineText = text.slice(lineStart, nl === -1 ? text.length : nl).trim();
    if (!lineText) {
      showToast({ tone: "warning", title: "无法设为章节起点", body: "请选中一行有内容的文字。" });
      return;
    }
    const entry: DraftChapter = { title: lineText.slice(0, 40), startIndex: lineStart };
    setTocEditMode((editing) => {
      if (editing) {
        setDraftChapters((prev) => {
          const withoutSameLine = prev.filter((ch) => ch.startIndex !== lineStart);
          const insertAt = withoutSameLine.findIndex((ch) => ch.startIndex > lineStart);
          const next = insertAt === -1 ? [...withoutSameLine, entry] : [...withoutSameLine.slice(0, insertAt), entry, ...withoutSameLine.slice(insertAt)];
          setRenamingIndex(next.findIndex((ch) => ch.startIndex === lineStart));
          return next;
        });
        return true;
      }
      const base = txtChaptersRef.current.filter((ch) => ch.title !== "").map((ch) => ({ title: ch.title, startIndex: ch.startIndex }));
      const withoutSameLine = base.filter((ch) => ch.startIndex !== lineStart);
      const insertAt = withoutSameLine.findIndex((ch) => ch.startIndex > lineStart);
      const next = insertAt === -1 ? [...withoutSameLine, entry] : [...withoutSameLine.slice(0, insertAt), entry, ...withoutSameLine.slice(insertAt)];
      setDraftChapters(next);
      setRenamingIndex(next.findIndex((ch) => ch.startIndex === lineStart));
      setSplitIndex(null);
      return true;
    });
    showToast({ tone: "info", title: "已加入新章节起点", body: "可在目录编辑中重命名，保存后生效。" });
  }, [content, showToast]);

  // 拆分目标章节内的行（含偏移），供"设为章节起点"挑选
  const splitLines = useMemo(() => {
    if (splitIndex === null) return [];
    const chapters = chaptersFromOverrides(content, draftChapters);
    const titled = chapters.filter((ch) => ch.title !== "");
    const chapter = titled[splitIndex];
    if (!chapter) return [];
    const lines: Array<{ start: number; text: string }> = [];
    let cursor = chapter.contentStart;
    const end = Math.min(chapter.endIndex, content.length);
    while (cursor < end && lines.length < 80) {
      const nl = content.indexOf("\n", cursor);
      const lineEnd = nl === -1 ? content.length : nl;
      const lineText = content.slice(cursor, lineEnd).trim();
      if (lineText) lines.push({ start: cursor, text: lineText });
      cursor = lineEnd + 1;
    }
    return lines.slice(1); // 第一行是章标题本身，不能再拆
  }, [splitIndex, draftChapters, content]);

  // 已读派生：目录顺序中位于当前章之前的章节（零存储；跳章翻阅会把中间章节计为已读，接受该近似）
  const currentTocIndexForRead = currentAnchorId ? tocEntries.findIndex((entry) => entry.id === currentAnchorId) : -1;
  const readIds = useMemo(
    () =>
      currentTocIndexForRead > 0
        ? new Set(tocEntries.slice(0, currentTocIndexForRead).map((entry) => entry.id))
        : new Set<string>(),
    [tocEntries, currentTocIndexForRead]
  );

  if (!activeBook || !settings) {
    return (
      <ShellPanel className="h-full border-0">
        <EmptyState title="没有打开资料" body="从书库中选择一份 TXT、Markdown 或 EPUB，即可浏览目录、复制选文或摘录到资料卡。" />
      </ShellPanel>
    );
  }

  const progressPercent = Math.round((progress?.progressPercent ?? 0) * 100);
  const isTxt = activeBook.format === "txt";
  const isMd = activeBook.format === "md";

  const currentTocIndex = currentAnchorId ? tocEntries.findIndex((entry) => entry.id === currentAnchorId) : -1;
  const tocSummary = currentTocIndex >= 0 ? `已读 ${currentTocIndex}/${tocEntries.length}` : undefined;

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

  // --- 目录编辑面板体 ---
  const tocEditBody = (
    <div className="flex min-h-0 flex-1 flex-col gap-2">
      <div className="rounded-md border border-copper/30 bg-copper/5 p-2 text-xs leading-5 text-paper-muted">
        修改后点「保存并完成」，全书目录、跳转与进度章节按新章节表生效；改动会随书籍保留。
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto pr-0.5">
        <div className="grid gap-1">
          {draftChapters.map((ch, i) => (
            <div key={`${ch.startIndex}-${i}`} className="rounded border border-paper-line bg-paper-panel px-2 py-1.5">
              {renamingIndex === i ? (
                <div className="flex items-center gap-1.5">
                  <TextInput
                    value={renameValue}
                    onChange={(event) => setRenameValue(event.target.value)}
                    className="h-7 flex-1 text-sm"
                    aria-label="章节标题"
                    autoFocus
                  />
                  <Button
                    variant="secondary"
                    className="h-7 px-2 text-xs"
                    onClick={() => {
                      const next = [...draftChapters];
                      next[i] = { ...next[i], title: renameValue.trim() || next[i].title };
                      setDraftChapters(next);
                      setRenamingIndex(null);
                    }}
                  >
                    确定
                  </Button>
                </div>
              ) : (
                <div className="flex items-start gap-1">
                  <span className="min-w-0 flex-1 break-all text-sm text-paper-ink">
                    <span className="mr-1.5 text-[11px] text-paper-muted">{i + 1}</span>
                    {ch.title}
                  </span>
                  <button className="shrink-0 rounded px-1 py-0.5 text-[11px] text-paper-muted hover:bg-paper-soft hover:text-paper-ink" onClick={() => { setRenamingIndex(i); setRenameValue(ch.title); }}>
                    重命名
                  </button>
                  <button className="shrink-0 rounded px-1 py-0.5 text-[11px] text-paper-muted hover:bg-paper-soft hover:text-paper-ink" onClick={() => setSplitIndex(splitIndex === i ? null : i)}>
                    拆分
                  </button>
                  <button
                    className="shrink-0 rounded px-1 py-0.5 text-[11px] text-paper-muted hover:bg-paper-soft hover:text-paper-ink disabled:opacity-40"
                    disabled={i === 0}
                    onClick={() => {
                      setDraftChapters((prev) => prev.filter((_, idx) => idx !== i));
                      setSplitIndex(null);
                    }}
                  >
                    合并到上一章
                  </button>
                </div>
              )}
              {splitIndex === i && (
                <div className="mt-1.5 border-t border-paper-line pt-1.5">
                  <div className="mb-1 text-[11px] text-paper-muted">点选一行作为新章节的标题行：</div>
                  <div className="grid max-h-48 gap-0.5 overflow-y-auto">
                    {splitLines.map((line) => (
                      <button
                        key={line.start}
                        className="truncate rounded px-1.5 py-1 text-left text-xs text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
                        title={line.text}
                        onClick={() => {
                          setDraftChapters((prev) => {
                            const next = [...prev];
                            next.splice(i + 1, 0, { title: line.text.slice(0, 40), startIndex: line.start });
                            return next;
                          });
                          setSplitIndex(null);
                        }}
                      >
                        {line.text}
                      </button>
                    ))}
                    {splitLines.length === 0 && <div className="px-1.5 py-1 text-xs text-paper-muted">此章没有可拆分的正文行。</div>}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t border-paper-line pt-2">
        <Button variant="secondary" className="h-8 text-xs" disabled={savingOverrides} onClick={() => { setTocEditMode(false); setSplitIndex(null); setRenamingIndex(null); }}>
          取消
        </Button>
        <Button className="h-8 text-xs" disabled={savingOverrides || draftChapters.length === 0} onClick={() => void persistTocOverrides(draftChapters)}>
          保存并完成
        </Button>
        {tocOverrides && (
          <Button variant="quiet" className="h-8 text-xs" disabled={savingOverrides} onClick={() => void persistTocOverrides(null)}>
            恢复自动识别
          </Button>
        )}
      </div>
    </div>
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
        <Button variant="quiet" onClick={() => void addBookmarkAtCurrent()}>
          <Bookmark size={16} />
          加书签
        </Button>
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
              {isTxt && (
                <button
                  className="rounded px-2 py-1 hover:bg-stone-700"
                  title="设为章节起点（目录编辑）"
                  onClick={() => {
                    addChapterStartFromSelection(selectionToolbar.charOffset);
                    setSelectionToolbar(null);
                  }}
                >
                  <Scissors size={14} />
                </button>
              )}
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
                    txtChapterBodies
                      ? txtChapterBodies[idx]
                      : convertedContent.slice(chapter.contentStart, chapter.endIndex)
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
          <ReaderSidePanel
            sidePanelTab={sidePanelTab}
            onTabChange={setSidePanelTab}
            onCollapse={() => setTocCollapsed(true)}
            progressPercent={progressPercent}
            tocTitle={isMd ? "Markdown 目录" : "章节目录"}
            tocEntries={tocEntries}
            currentTocId={currentAnchorId}
            readIds={readIds}
            tocSummary={tocSummary}
            tocHeaderExtra={
              isTxt && !tocEditMode && (tocEntries.length > 0 || tocOverrides) ? (
                <div className="mb-2">
                  <Button variant="secondary" className="h-7 text-xs" onClick={enterTocEditMode}>
                    编辑章节
                  </Button>
                </div>
              ) : undefined
            }
            tocBody={isTxt && tocEditMode ? tocEditBody : undefined}
            onTocJump={jumpToTocEntry}
            tocEmptyText={isMd ? "未检测到标题" : "未检测到章节"}
            highlights={highlights}
            onRemoveHighlight={(id) => {
              setHighlights((prev) => prev.filter((h) => h.id !== id));
              highlightsRef.current = highlightsRef.current.filter((h) => h.id !== id);
              void deleteHighlight(id);
            }}
            onJumpToHighlight={jumpToHighlight}
            onHighlightsChange={(next) => {
              highlightsRef.current = next;
              setHighlights(next);
            }}
            bookmarks={bookmarks}
            onAddBookmark={() => void addBookmarkAtCurrent()}
            onJumpToBookmark={jumpToBookmark}
            onRemoveBookmark={async (id) => {
              await removeBookmarkById(id);
              setBookmarks((prev) => prev.filter((b) => b.id !== id));
            }}
          />
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

// EPUB 阅读器连带 epubjs 体量很大，按 format 懒加载，TXT/MD 不再为其付出下载与解析成本。
const EpubReaderPage = lazy(() =>
  import("@/features/library/EpubReaderPage").then((m) => ({ default: m.EpubReaderPage }))
);

export function ReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  if (activeBook?.format === "epub") {
    return (
      <Suspense
        fallback={
          <ShellPanel className="grid h-full place-items-center border-0 text-sm text-paper-muted">
            正在打开 EPUB...
          </ShellPanel>
        }
      >
        <EpubReaderPage />
      </Suspense>
    );
  }
  return <TextReaderPage />;
}
