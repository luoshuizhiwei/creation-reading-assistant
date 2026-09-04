import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { X } from "lucide-react";
import DOMPurify from "dompurify";
import { Button, EmptyState, ShellPanel, TextInput } from "@/components/ui";
import { ReaderTopNav } from "./components/ReaderTopNav";
import { ReaderBottomBar } from "./components/ReaderBottomBar";
import { ReaderSearchOverlay } from "./components/ReaderSearchOverlay";
import { ExcerptPicker } from "@/features/library/ExcerptPicker";
import { ReaderSettingsDrawer } from "@/features/library/ReaderSettingsDrawer";
import { ReaderSidePanel, type SidePanelTab } from "@/features/library/ReaderSidePanel";
import { useReaderExcerpt, type ExcerptBuildContext } from "@/features/library/useReaderExcerpt";
import { useReaderProgress } from "@/hooks/useReaderProgress";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import {
  deleteBookmark as removeBookmarkById,
  deleteHighlight,
  getBookmarksByBook,
  getHighlightsByBook,
  saveBookmark,
  saveHighlight
} from "@/services/annotation-service";
import { saveTxtTocOverrides } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { BookmarkItem, ExcerptResult, ExcerptTarget, HighlightColor, HighlightItem } from "@/types/library";
import { readerPaperClass, readerShellClass, readerTextColor } from "@/utils/format";
import { convertTextSync, getConverter } from "@/utils/text-conversion";
import { renderMarkdownWithToc } from "@/features/library/toc/markdown-toc";
import { computeAnchorScrollTop, computeTextAnchor, currentAnchorIdFromSpans, type AnchorSpan } from "@/features/library/toc/anchor";
import type { TocEntry } from "@/features/library/toc/tree";
import { chaptersFromOverrides, splitTxtChapters } from "@/features/library/toc/txt-chapters";
import { ReaderSelectionToolbar, type SelectionToolbarState } from "./ReaderSelectionToolbar";
import { HIGHLIGHT_MARK_STYLES } from "./annotation-constants";

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

export interface TxtMarkdownReaderProps {
  bookId?: string;
  title?: string;
  content?: string;
  format?: "txt" | "md";
  author?: string;
  onBack?: () => void;
}

export function TxtMarkdownReader(props?: TxtMarkdownReaderProps) {
  const storeActiveBook = useLibraryStore((state) => state.activeBook);
  const storeContent = useLibraryStore((state) => state.activeContent);

  const activeBook = useMemo(() => {
    if (props?.bookId || props?.title || props?.format) {
      return {
        id: props.bookId ?? storeActiveBook?.id ?? "",
        title: props.title ?? storeActiveBook?.title ?? "",
        format: props.format ?? storeActiveBook?.format ?? "txt",
        author: props.author ?? storeActiveBook?.author,
        size: storeActiveBook?.size ?? 0,
        text: storeActiveBook?.text,
        ...storeActiveBook
      };
    }
    return storeActiveBook;
  }, [props?.bookId, props?.title, props?.format, props?.author, storeActiveBook]);

  const content = props?.content !== undefined ? props.content : storeContent;
  const progress = useLibraryStore((state) => (activeBook ? state.progress[activeBook.id] : undefined));
  const settings = useLibraryStore((state) => state.readerSettings);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setScreen = useAppStore((state) => state.setScreen);
  const showToast = useUIStore((state) => state.showToast);
  const scrollerRef = useRef<HTMLDivElement>(null);
  const [selectionToolbar, setSelectionToolbar] = useState<SelectionToolbarState | null>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [highlights, setHighlights] = useState<HighlightItem[]>([]);
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const highlightsRef = useRef<HighlightItem[]>([]);
  const [tocCollapsed, setTocCollapsed] = useState(false);
  const [settingsDrawerOpen, setSettingsDrawerOpen] = useState(false);
  const [sidePanelTab, setSidePanelTab] = useState<SidePanelTab>("toc");
  const [currentAnchorId, setCurrentAnchorId] = useState<string | undefined>(undefined);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [scrollMetrics, setScrollMetrics] = useState({ scrollTop: 0, scrollHeight: 1, clientHeight: 1 });
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
    if (spans.length > 0) {
      spansCacheRef.current = spans;
      spansVersionRef.current = versionKey;
    }
    return spans;
  }, [
    activeBook?.id,
    activeBook?.format,
    content.length,
    txtChapters,
    tocEntries,
    settings?.fontSize,
    settings?.lineHeight,
    settings?.letterSpacing,
    settings?.paragraphSpacing,
    settings?.pageMargin,
    settings?.fontFamily,
    settings?.textConversion,
    tocCollapsed
  ]);

  const ensureSpansRef = useRef(ensureSpans);
  ensureSpansRef.current = ensureSpans;

  const getTextAnchor = useCallback(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return undefined;
    const spans = ensureSpansRef.current();
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

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => updateCurrentAnchor());
    return () => window.cancelAnimationFrame(frame);
  }, [
    updateCurrentAnchor,
    txtChapters,
    markdownHtml,
    tocCollapsed,
    activeBook?.id,
    settings?.fontSize,
    settings?.lineHeight,
    settings?.letterSpacing,
    settings?.paragraphSpacing,
    settings?.pageMargin,
    settings?.fontFamily
  ]);

  const activeTextJump = useLibraryStore((state) => state.activeTextJump);
  const { scheduleSave, flushProgress, getCurrentLocation } = useReaderProgress(scrollerRef, getTextAnchor);
  const { recordInteraction, endTracking } = useReadingSessionTracker(scrollerRef, getCurrentLocation);

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

  const addTxtHighlight = useCallback(
    async (charOffset: number, charLength: number, text: string, color: HighlightColor) => {
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
        locator: {
          version: 2,
          bookId,
          format: useLibraryStore.getState().activeBook?.format === "md" ? "markdown" : "txt",
          chapterId: currentHeading?.id,
          textOffset: charOffset,
          updatedAt: Date.now()
        },
        createdAt: now,
        updatedAt: now
      };
      const saved = await saveHighlight(item);
      setHighlights((prev) => {
        const next = [...prev, saved];
        highlightsRef.current = next;
        return next;
      });
      showToast({ tone: "success", title: "已添加高亮", body: text.slice(0, 50) });
    },
    [showToast]
  );

  const scrollToCharOffset = useCallback(
    (charOffset: number) => {
      const scroller = scrollerRef.current;
      if (!scroller) return;
      const target = computeAnchorScrollTop(
        ensureSpansRef.current(),
        { charOffset },
        scroller.scrollHeight,
        scroller.clientHeight,
        contentLengthRef.current
      );
      if (typeof target === "number") {
        scroller.scrollTop = target;
      } else {
        const maxScroll = scroller.scrollHeight - scroller.clientHeight;
        if (contentLengthRef.current > 0) scroller.scrollTop = Math.round((charOffset / contentLengthRef.current) * maxScroll);
      }
      window.requestAnimationFrame(() => updateCurrentAnchor());
    },
    [updateCurrentAnchor]
  );

  const jumpToBookmark = useCallback(
    (bm: BookmarkItem) => {
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
    },
    [scrollToCharOffset, updateCurrentAnchor]
  );

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
      createdAt: new Date().toISOString()
    };
    const saved = await saveBookmark(item);
    setBookmarks((prev) => [saved, ...prev]);
    showToast({ tone: "success", title: "已添加书签", body: item.label });
  }, [getCurrentLocation, getTextAnchor, showToast]);

  const jumpToHighlight = useCallback(
    (hl: HighlightItem) => {
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
    },
    [scrollToCharOffset]
  );

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
      const anchorTarget = loc.text
        ? computeAnchorScrollTop(spans, loc.text, scroller.scrollHeight, scroller.clientHeight, contentLengthRef.current)
        : undefined;
      const target = anchorTarget ?? savedScrollTop;
      if (typeof target === "number") scroller.scrollTop = target;
      window.requestAnimationFrame(() => updateCurrentAnchor());
    };
    restoreTimerRef.current = window.setTimeout(() => tryRestore(0), 80);
  }, [activeBook?.id, progress?.currentLocation, settings?.restoreLastPosition, updateCurrentAnchor]);

  useEffect(() => {
    if (activeBook?.id) loadAnnotations(activeBook.id);
  }, [activeBook?.id, loadAnnotations]);

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
    return () => {
      cancelled = true;
    };
  }, [content, settings?.textConversion]);

  const txtChapterBodies = useMemo(() => {
    if (activeBook?.format !== "txt" || txtChapters.length <= 1) return null;
    const mode = settings?.textConversion ?? "none";
    if (mode === "none") return null;
    return txtChapters.map((ch) => convertTextSync(content.slice(ch.contentStart, ch.endIndex), mode));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeBook?.format, txtChapters, content, settings?.textConversion, converterTick]);

  const convertedMd = useMemo(
    () => (activeBook?.format === "md" ? renderMarkdownWithToc(convertedContent, md.toc) : { html: "", toc: [] }),
    [activeBook?.format, convertedContent, md.toc]
  );
  const convertedMarkdownHtml = convertedMd.html;

  const updateScrollMetrics = useCallback(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    setScrollMetrics({
      scrollTop: scroller.scrollTop,
      scrollHeight: scroller.scrollHeight,
      clientHeight: scroller.clientHeight
    });
  }, []);

  const handleActivity = useCallback(() => {
    recordInteraction();
    scheduleSave();
  }, [recordInteraction, scheduleSave]);

  const handleScrollActivity = useCallback(() => {
    handleActivity();
    updateCurrentAnchor();
    updateScrollMetrics();
  }, [handleActivity, updateCurrentAnchor, updateScrollMetrics]);

  useEffect(() => {
    const timer = window.setTimeout(updateScrollMetrics, 100);
    return () => window.clearTimeout(timer);
  }, [content, updateScrollMetrics, settings?.fontSize, settings?.lineHeight]);

  // --- 键盘快捷键监听：Ctrl+F 搜索、Escape 关闭浮层 ---
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "f") {
        e.preventDefault();
        setIsSearchOpen((prev) => !prev);
        return;
      }
      if (e.key === "Escape") {
        if (isSearchOpen) {
          setIsSearchOpen(false);
          return;
        }
        if (selectionToolbar?.visible) {
          setSelectionToolbar(null);
          setShowColorPicker(false);
          return;
        }
        if (settingsDrawerOpen) {
          setSettingsDrawerOpen(false);
          return;
        }
        if (tocEditMode) {
          setTocEditMode(false);
          return;
        }
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isSearchOpen, selectionToolbar?.visible, settingsDrawerOpen, tocEditMode]);

  const jumpToTocEntry = useCallback((id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, []);

  const enterTocEditMode = useCallback(() => {
    const draft = txtChapters
      .filter((ch) => ch.title !== "")
      .map((ch) => ({ title: ch.title, startIndex: ch.startIndex }));
    setDraftChapters(draft);
    setSplitIndex(null);
    setRenamingIndex(null);
    setTocEditMode(true);
  }, [txtChapters]);

  const persistTocOverrides = useCallback(
    async (chapters: DraftChapter[] | null) => {
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
    },
    [showToast]
  );

  const addChapterStartFromSelection = useCallback(
    (charOffset: number) => {
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
            const next =
              insertAt === -1
                ? [...withoutSameLine, entry]
                : [...withoutSameLine.slice(0, insertAt), entry, ...withoutSameLine.slice(insertAt)];
            setRenamingIndex(next.findIndex((ch) => ch.startIndex === lineStart));
            return next;
          });
          return true;
        }
        const base = txtChaptersRef.current.filter((ch) => ch.title !== "").map((ch) => ({ title: ch.title, startIndex: ch.startIndex }));
        const withoutSameLine = base.filter((ch) => ch.startIndex !== lineStart);
        const insertAt = withoutSameLine.findIndex((ch) => ch.startIndex > lineStart);
        const next =
          insertAt === -1
            ? [...withoutSameLine, entry]
            : [...withoutSameLine.slice(0, insertAt), entry, ...withoutSameLine.slice(insertAt)];
        setDraftChapters(next);
        setRenamingIndex(next.findIndex((ch) => ch.startIndex === lineStart));
        setSplitIndex(null);
        return true;
      });
      showToast({ tone: "info", title: "已加入新章节起点", body: "可在目录编辑中重命名，保存后生效。" });
    },
    [content, showToast]
  );

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
    return lines.slice(1);
  }, [splitIndex, draftChapters, content]);

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
  const currentChapterTitle = currentAnchorId
    ? tocEntries.find((entry) => entry.id === currentAnchorId)?.label
    : undefined;

  const totalPages = Math.max(1, Math.ceil(scrollMetrics.scrollHeight / Math.max(scrollMetrics.clientHeight, 1)));
  const currentPage = Math.min(
    totalPages,
    Math.max(
      1,
      Math.round(
        (scrollMetrics.scrollTop / Math.max(scrollMetrics.scrollHeight - scrollMetrics.clientHeight, 1)) *
          (totalPages - 1)
      ) + 1
    )
  );

  const handleSeekPercent = useCallback(
    (percent: number) => {
      const scroller = scrollerRef.current;
      if (!scroller) return;
      const maxScroll = scroller.scrollHeight - scroller.clientHeight;
      if (maxScroll > 0) {
        scroller.scrollTop = Math.round((percent / 100) * maxScroll);
        handleScrollActivity();
      }
    },
    [handleScrollActivity]
  );

  const handleBack = useCallback(async () => {
    await flushProgress();
    endTracking("leave-reader");
    if (props?.onBack) {
      props.onBack();
    } else {
      setScreen("library");
    }
  }, [flushProgress, endTracking, props?.onBack, setScreen]);

  const openExcerptFromSelection = () => {
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
  };

  const handleExcerptResult = (result: ExcerptResult, target: ExcerptTarget) => {
    if (result.success) {
      showToast({
        tone: "success",
        title: target.kind === "inbox" ? "已摘录到收件箱" : "已保存为资料卡",
        body: "选文和来源已保存，可继续阅读。"
      });
    } else {
      showToast({ tone: "error", title: "摘录失败", body: result.error });
    }
  };

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
                      const title = renameValue.trim() || ch.title;
                      setDraftChapters((prev) => prev.map((item, idx) => (idx === i ? { ...item, title } : item)));
                      setRenamingIndex(null);
                    }}
                  >
                    确定
                  </Button>
                  <Button variant="quiet" className="h-7 px-1.5 text-xs" onClick={() => setRenamingIndex(null)}>
                    取消
                  </Button>
                </div>
              ) : (
                <div className="flex items-center justify-between gap-1 text-xs">
                  <span className="truncate text-paper-ink font-medium">{ch.title}</span>
                  <div className="flex items-center gap-0.5 shrink-0">
                    <button
                      className="rounded px-1.5 py-0.5 text-paper-muted hover:bg-paper-soft hover:text-paper-ink"
                      onClick={() => {
                        setRenamingIndex(i);
                        setRenameValue(ch.title);
                      }}
                    >
                      重命名
                    </button>
                    <button
                      className={`rounded px-1.5 py-0.5 ${splitIndex === i ? "bg-copper text-white" : "text-paper-muted hover:bg-paper-soft hover:text-paper-ink"}`}
                      onClick={() => setSplitIndex((cur) => (cur === i ? null : i))}
                    >
                      从此处拆分
                    </button>
                    {draftChapters.length > 1 && (
                      <button
                        className="rounded px-1.5 py-0.5 text-red-500 hover:bg-red-50"
                        title="删除该章节起点"
                        onClick={() => {
                          setDraftChapters((prev) => prev.filter((_, idx) => idx !== i));
                          if (splitIndex === i) setSplitIndex(null);
                        }}
                      >
                        <X size={12} />
                      </button>
                    )}
                  </div>
                </div>
              )}
              {splitIndex === i && (
                <div className="mt-1.5 rounded border border-copper/30 bg-copper/5 p-2 text-xs">
                  <div className="mb-1 text-paper-muted">点击一行设为新章节起点：</div>
                  {splitLines.length === 0 ? (
                    <div className="text-paper-muted">未找到可用正文行，或章内正文过短。可在正文中选字点击剪刀添加。</div>
                  ) : (
                    <div className="max-h-40 overflow-y-auto grid gap-0.5">
                      {splitLines.map((l) => (
                        <button
                          key={l.start}
                          type="button"
                          className="truncate rounded px-1.5 py-1 text-left hover:bg-paper-soft hover:text-paper-ink"
                          onClick={() => {
                            const newEntry: DraftChapter = { title: l.text.slice(0, 40), startIndex: l.start };
                            setDraftChapters((prev) => {
                              const withoutSame = prev.filter((item) => item.startIndex !== l.start);
                              const insertAt = withoutSame.findIndex((item) => item.startIndex > l.start);
                              const next =
                                insertAt === -1
                                  ? [...withoutSame, newEntry]
                                  : [...withoutSame.slice(0, insertAt), newEntry, ...withoutSame.slice(insertAt)];
                              setRenamingIndex(next.findIndex((item) => item.startIndex === l.start));
                              return next;
                            });
                            setSplitIndex(null);
                          }}
                        >
                          {l.text}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-paper-line pt-2 text-xs">
        {tocOverrides ? (
          <button
            className="text-paper-muted underline hover:text-paper-ink disabled:opacity-50"
            disabled={savingOverrides}
            onClick={() => void persistTocOverrides(null)}
          >
            恢复自动识别
          </button>
        ) : (
          <span className="text-paper-muted">未保存修改</span>
        )}
        <div className="flex items-center gap-1.5">
          <Button variant="quiet" className="h-7 text-xs" disabled={savingOverrides} onClick={() => setTocEditMode(false)}>
            取消
          </Button>
          <Button
            className="h-7 text-xs"
            disabled={savingOverrides || draftChapters.length === 0}
            onClick={() => void persistTocOverrides(draftChapters)}
          >
            {savingOverrides ? "保存中..." : "保存并完成"}
          </Button>
        </div>
      </div>
    </div>
  );

  return (
    <div
      className={`reader-host desktop-page-root relative grid grid-rows-[auto_minmax(0,1fr)_auto] ${readerShellClass(settings.readerBackground)}`}
    >
      <ReaderTopNav
        title={activeBook.title}
        author={activeBook.author}
        currentChapterTitle={currentChapterTitle}
        progressPercent={progressPercent}
        totalReadingTimeMs={progress?.totalReadingTimeMs}
        isTocOpen={!tocCollapsed}
        isSearchOpen={isSearchOpen}
        onBack={handleBack}
        onToggleSearch={() => setIsSearchOpen((prev) => !prev)}
        onToggleToc={() => setTocCollapsed((prev) => !prev)}
        onOpenSettings={() => setSettingsDrawerOpen(true)}
      />

      {isSearchOpen && (
        <ReaderSearchOverlay
          content={convertedContent}
          onJumpToOffset={scrollToCharOffset}
          onClose={() => setIsSearchOpen(false)}
        />
      )}

      <div
        className={`reader-body grid min-h-0 ${
          tocCollapsed ? "grid-cols-[minmax(0,1fr)_48px]" : "grid-cols-[minmax(0,1fr)_320px]"
        }`}
      >
        <div
          ref={scrollerRef}
          className="reader-scroller min-h-0 overflow-y-auto px-6"
          onScroll={handleScrollActivity}
          onMouseUp={() => {
            const sel = window.getSelection();
            if (!sel || sel.isCollapsed || !sel.toString().trim()) {
              setSelectionToolbar(null);
              setShowColorPicker(false);
              return;
            }
            const text = sel.toString().trim().slice(0, 2000);
            if (!sel.rangeCount) return;
            const range = sel.getRangeAt(0);
            const rect = range.getBoundingClientRect();
            const scroller = scrollerRef.current;
            if (!scroller) return;
            const selectedStr = sel.toString();
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
              text
            });
          }}
          tabIndex={0}
        >
          {selectionToolbar && (
            <ReaderSelectionToolbar
              toolbar={selectionToolbar}
              showColorPicker={showColorPicker}
              setShowColorPicker={setShowColorPicker}
              isTxt={isTxt}
              onHighlight={async (color) => {
                await addTxtHighlight(selectionToolbar.charOffset, selectionToolbar.charLength, selectionToolbar.text, color);
                setSelectionToolbar(null);
                setShowColorPicker(false);
              }}
              onCopy={async () => {
                try {
                  await navigator.clipboard.writeText(selectionToolbar.text);
                  showToast({ tone: "success", title: "已复制", body: `${selectionToolbar.text.length} 字` });
                } catch {
                  showToast({ tone: "error", title: "复制失败" });
                }
                setSelectionToolbar(null);
              }}
              onExcerpt={openExcerptFromSelection}
              onAddChapterStart={(offset) => {
                addChapterStartFromSelection(offset);
                setSelectionToolbar(null);
              }}
              onClose={() => {
                window.getSelection()?.removeAllRanges();
                setSelectionToolbar(null);
                setShowColorPicker(false);
              }}
            />
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

      <ReaderBottomBar
        currentPage={currentPage}
        totalPages={totalPages}
        totalWords={content.length}
        progressPercent={progressPercent}
        totalReadingTimeMs={progress?.totalReadingTimeMs}
        onSeekPercent={handleSeekPercent}
      />

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
