import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import ePub from "epubjs";
import type Book from "epubjs/types/book";
import type Contents from "epubjs/types/contents";
import type { DisplayedLocation, Location, RenditionOptions } from "epubjs/types/rendition";
import type { NavItem } from "epubjs/types/navigation";
import type Section from "epubjs/types/section";
import type { MobileBook, MobileReaderSettings } from "../../../types/mobile";
import type { MobileReaderDocument } from "../../../reader/mobile-reader";
import { addMobileLog } from "../../../services/mobile-logger";

const EPUB_WEBVIEW_SAFE_BYTES = 48 * 1024 * 1024;
const EPUB_OPEN_TIMEOUT_MS = 12_000;

function readerThemeRules(settings: MobileReaderSettings) {
  const backgroundMap: Record<string, { bg: string; fg: string }> = {
    white: { bg: "#fafaf8", fg: "#1a1a1a" },
    warm: { bg: "#f3e8d8", fg: "#2b2118" },
    green: { bg: "#e8f0df", fg: "#1f291a" },
    night: { bg: "#1a1614", fg: "#e8ddd0" },
    "warm-yellow": { bg: "#f7f0d8", fg: "#3d2b1f" },
    "green-bean": { bg: "#e8f0e0", fg: "#2d332b" },
    "oled-black": { bg: "#000000", fg: "#b8b0a8" }
  };
  const theme = backgroundMap[settings.readerBackground] ?? backgroundMap.warm;
  return {
    html: { "background-color": theme.bg },
    body: {
      "font-size": `${settings.fontSize}px`,
      "line-height": String(settings.lineHeight),
      color: theme.fg,
      "background-color": theme.bg,
      padding: `0 ${settings.pageMargin}px`,
      "margin-bottom": `${settings.paragraphSpacing}em`
    },
    p: {
      "margin-bottom": `${settings.paragraphSpacing}em`,
      "text-indent": "2em"
    }
  };
}

export interface EpubReaderHandle {
  display(target: string): Promise<void>;
  display(target: number): Promise<void>;
  next(): Promise<void>;
  prev(): Promise<void>;
}

export interface EpubLocationInfo {
  cfi: string;
  href: string;
  progressPercent: number;
  chapterTitle?: string;
  pageIndex?: number;
  pageCount?: number;
}

interface EpubReaderViewProps {
  book: MobileBook;
  content: string;
  settings: MobileReaderSettings;
  initialCfi?: string;
  onLocationChange: (info: EpubLocationInfo) => void;
  onDocumentReady?: (document: MobileReaderDocument) => void;
  onReady: () => void;
  onError: (message: string) => void;
  /** 用户与 EPUB 内容发生交互（触摸/点击）时触发，用于更新阅读活跃时间 */
  onInteraction?: () => void;
  /** 点击左侧区域或向右滑动 */
  onBackward?: () => void;
  /** 点击右侧区域或向左滑动 */
  onForward?: () => void;
  /** 点击中央区域 */
  onToggleControls?: () => void;
}

function decodeBase64ToArrayBuffer(value: string): ArrayBuffer {
  let normalized = value.replace(/^data:.*?;base64,/, "").replace(/\s/g, "");
  // 兼容部分 bridge / 归档工具输出的 URL-safe base64
  normalized = normalized.replace(/-/g, "+").replace(/_/g, "/");
  while (normalized.length % 4 !== 0) normalized += "=";
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
}

function navItemToChapterTitle(navToc: NavItem[], href: string): string | undefined {
  const cleanHref = href.split("#")[0];
  for (const item of navToc) {
    if (item.href && item.href.split("#")[0] === cleanHref) return item.label || item.id;
    if (item.subitems?.length) {
      const found = navItemToChapterTitle(item.subitems, href);
      if (found) return found;
    }
  }
  return undefined;
}

function flattenNavItems(items: NavItem[], level = 1): Array<{ href: string; title: string; level: number }> {
  const result: Array<{ href: string; title: string; level: number }> = [];
  for (const item of items) {
    if (item.href) {
      result.push({ href: item.href.split("#")[0], title: item.label || item.id || "未命名章节", level });
    }
    if (item.subitems?.length) result.push(...flattenNavItems(item.subitems, level + 1));
  }
  return result;
}

export function isReadableEpubSpineItem(item: { linear?: boolean | string }): boolean {
  return item.linear !== false && item.linear !== "no";
}

function findAdjacentReadableSection(book: Book | null, current: Section | undefined, direction: -1 | 1): Section | undefined {
  if (!book || !current) return undefined;
  let index = current.index + direction;
  while (index >= 0) {
    const candidate = book.spine.get(index);
    if (!candidate) return undefined;
    if (isReadableEpubSpineItem(candidate)) return candidate;
    index += direction;
  }
  return undefined;
}

function buildEpubReaderDocument(book: Book, fallbackTitle: string): MobileReaderDocument {
  const title = book.packaging.metadata?.title || fallbackTitle;
  const spineItems = ((book.spine as unknown as { spineItems?: Array<{ href?: string; linear?: boolean | string }> }).spineItems ?? [])
    .filter(isReadableEpubSpineItem);
  const hrefToIndex = new Map<string, number>();
  spineItems.forEach((item, index) => {
    const href = item.href ?? "";
    if (!href) return;
    hrefToIndex.set(href, index);
    const fileName = href.split("/").pop();
    if (fileName) hrefToIndex.set(fileName, index);
  });
  const seen = new Set<number>();
  const toc = flattenNavItems(book.navigation?.toc ?? [])
    .map((item) => {
      const index = hrefToIndex.get(item.href) ?? hrefToIndex.get(item.href.split("/").pop() ?? "");
      if (index === undefined || seen.has(index)) return undefined;
      seen.add(index);
      return {
        id: `epub-chapter-${index}`,
        title: item.title,
        level: item.level,
        index,
        href: item.href
      };
    })
    .filter(Boolean) as MobileReaderDocument["toc"];
  if (!toc.length) {
    spineItems.forEach((item, index) => {
      toc.push({
        id: `epub-chapter-${index}`,
        title: item.href?.split("/").pop()?.replace(/\.[^.]+$/, "") || `章节 ${index + 1}`,
        level: 1,
        index,
        href: item.href
      });
    });
  }
  return {
    title,
    format: "epub",
    html: "",
    plainText: "",
    toc,
    wordCount: 0,
    currentTocIndex: 0,
    totalChapters: Math.max(1, spineItems.length || toc.length)
  };
}

export const EpubReaderView = forwardRef<EpubReaderHandle, EpubReaderViewProps>(function EpubReaderView(
  { book, content, settings, initialCfi, onLocationChange, onDocumentReady, onReady, onError, onInteraction, onBackward, onForward, onToggleControls },
  ref
) {
  const containerRef = useRef<HTMLDivElement>(null);
  const bookRef = useRef<Book | null>(null);
  const renditionRef = useRef<ReturnType<Book["renderTo"]> | null>(null);
  const touchStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const lastTouchTapAtRef = useRef(0);
  const onLocationChangeRef = useRef(onLocationChange);
  const onDocumentReadyRef = useRef(onDocumentReady);
  const onReadyRef = useRef(onReady);
  const onErrorRef = useRef(onError);
  const onInteractionRef = useRef(onInteraction);
  const onBackwardRef = useRef(onBackward);
  const onForwardRef = useRef(onForward);
  const onToggleControlsRef = useRef(onToggleControls);
  const [ready, setReady] = useState(false);

  // 保持回调引用最新，避免重建 rendition 时丢失闭包
  onLocationChangeRef.current = onLocationChange;
  onDocumentReadyRef.current = onDocumentReady;
  onReadyRef.current = onReady;
  onErrorRef.current = onError;
  onInteractionRef.current = onInteraction;
  onBackwardRef.current = onBackward;
  onForwardRef.current = onForward;
  onToggleControlsRef.current = onToggleControls;

  useImperativeHandle(ref, () => ({
    display: async (target: string | number) => {
      const rendition = renditionRef.current;
      if (!rendition) return;
      try {
        if (typeof target === "number") await rendition.display(target);
        else await rendition.display(target);
      } catch (error) {
        onErrorRef.current(`章节打开失败：${error instanceof Error ? error.message : String(error)}`);
      }
    },
    next: async () => {
      const rendition = renditionRef.current;
      if (!rendition) return;
      try {
        const href = rendition.location?.start?.href;
        const current = href ? bookRef.current?.spine.get(href) : undefined;
        if (current && !isReadableEpubSpineItem(current)) {
          const target = findAdjacentReadableSection(bookRef.current, current, 1);
          if (target?.href) {
            await rendition.display(target.href);
            return;
          }
        }
        await rendition.next();
      } catch (error) {
        onErrorRef.current(`下一页打开失败：${error instanceof Error ? error.message : String(error)}`);
      }
    },
    prev: async () => {
      const rendition = renditionRef.current;
      if (!rendition) return;
      try {
        const href = rendition.location?.start?.href;
        const current = href ? bookRef.current?.spine.get(href) : undefined;
        if (current && !isReadableEpubSpineItem(current)) {
          const target = findAdjacentReadableSection(bookRef.current, current, -1);
          if (target?.href) {
            await rendition.display(target.href);
            return;
          }
        }
        await rendition.prev();
      } catch (error) {
        onErrorRef.current(`上一页打开失败：${error instanceof Error ? error.message : String(error)}`);
      }
    }
  }));

  useEffect(() => {
    let cancelled = false;
    let timedOut = false;
    let initialized = false;
    let bookInstance: Book | null = null;
    let renditionInstance: ReturnType<Book["renderTo"]> | null = null;
    const contentInteractionCleanups = new Set<() => void>();
    const boundContentDocuments = new WeakSet<Document>();
    const openTimeout = window.setTimeout(() => {
      if (cancelled || initialized) return;
      timedOut = true;
      try {
        renditionInstance?.destroy();
        bookInstance?.destroy();
      } catch {
        // 超时清理失败不应阻止错误页显示。
      }
      renditionRef.current = null;
      bookRef.current = null;
      onErrorRef.current("EPUB 解析超过 12 秒，已停止等待。文件可能损坏或与当前阅读器不兼容。");
    }, EPUB_OPEN_TIMEOUT_MS);

    async function init() {
      try {
        if (book.size > EPUB_WEBVIEW_SAFE_BYTES) {
          throw new Error("EPUB 文件过大，当前手机阅读器为避免内存不足，暂不直接打开超过 48 MB 的 EPUB。");
        }
        const withoutPrefix = content.replace(/^data:.*?;base64,/, "");
        const normalized = /\s/.test(withoutPrefix) ? withoutPrefix.replace(/\s/g, "") : withoutPrefix;
        if (!normalized) throw new Error("EPUB 内容为空。");
        // 优先用 epubjs 原生 base64 解码
        bookInstance = ePub(normalized, { openAs: "base64" });
        try {
          await bookInstance.ready;
        } catch {
          bookInstance.destroy();
          const buffer = decodeBase64ToArrayBuffer(content);
          bookInstance = ePub(buffer, { openAs: "binary" });
          await bookInstance.ready;
        }
        if (cancelled || timedOut) {
          bookInstance.destroy();
          return;
        }
        bookRef.current = bookInstance;
        const readerDocument = buildEpubReaderDocument(bookInstance, book.title);
        onDocumentReadyRef.current?.(readerDocument);

        const isPaged = settings.readerMode === "paged";
        const flow: RenditionOptions["flow"] = isPaged ? "paginated" : "scrolled-doc";
        const options: RenditionOptions = {
          flow,
          spread: "none",
          resizeOnOrientationChange: true,
          snap: isPaged
        };

        renditionInstance = bookInstance.renderTo(containerRef.current!, options);
        renditionRef.current = renditionInstance;

        // 注册主题：根据背景、字号、行距等生成 CSS
        renditionInstance.themes.register("reader-theme", readerThemeRules(settings));
        renditionInstance.themes.select("reader-theme");

        // 监听位置变化，上报 CFI
        renditionInstance.on("relocated", (location: Location) => {
          const start: DisplayedLocation = location.start;
          const navToc = bookInstance?.navigation?.toc ?? [];
          const chapterTitle = navItemToChapterTitle(navToc, start.href);
          const percentage = Number(start.percentage);
          const cleanHref = start.href?.split("#")[0] ?? "";
          const tocPosition = readerDocument.toc.findIndex((item) => {
            const itemHref = item.href?.split("#")[0] ?? "";
            return !!cleanHref && (itemHref === cleanHref || itemHref.split("/").pop() === cleanHref.split("/").pop());
          });
          const reportedSpineIndex = Number((start as DisplayedLocation & { index?: number }).index);
          const resolvedSection = start.href ? bookInstance?.section(start.href) : undefined;
          // rendition 报告的 index 在部分 WebView 中固定为 0；目录位置与当前 href
          // 的对应关系更可靠，也与 totalChapters 使用同一个“可读章节”坐标系。
          const spineIndex = tocPosition >= 0
            ? tocPosition
            : (Number.isFinite(reportedSpineIndex) ? reportedSpineIndex : Number(resolvedSection?.index ?? 0));
          const spineCount = Math.max(1, readerDocument.totalChapters ?? readerDocument.toc.length);
          const displayedPage = Math.max(1, Number(start.displayed.page) || 1);
          const displayedTotal = Math.max(1, Number(start.displayed.total) || 1);
          const chapterFraction = Math.min(1, Math.max(0, (displayedPage - 1) / displayedTotal));
          const spineProgress = ((Math.min(spineCount - 1, Math.max(0, spineIndex)) + chapterFraction) / spineCount) * 100;
          // 某些 EPUB 在非首章仍报告 percentage=0；此时 0 不是可信的全书位置。
          const safeProgress = Number.isFinite(percentage) && percentage > 0
            ? percentage * 100
            : spineProgress;
          onLocationChangeRef.current({
            cfi: start.cfi,
            href: start.href,
            progressPercent: Math.min(100, Math.max(0, safeProgress)),
            chapterTitle,
            pageIndex: start.displayed.page > 0 ? start.displayed.page - 1 : 0,
            pageCount: start.displayed.total
          });
        });

        renditionInstance.on("displayError", (error: Error) => {
          onErrorRef.current(error?.message || "EPUB 渲染失败");
        });

        // 手势与点击区域控制：iframe 内事件不冒泡到父容器，需通过 rendition 代理
        renditionInstance.on("touchstart", (event: TouchEvent) => {
          onInteractionRef.current?.();
          const touches = event.touches;
          if (touches.length === 1) {
            touchStartRef.current = { x: touches[0].clientX, y: touches[0].clientY, time: Date.now() };
          }
        });

        const handleTapZone = (clientX: number, clientY: number, sourceView?: Window | null) => {
          const container = containerRef.current;
          if (!container) return;
          const rect = container.getBoundingClientRect();
          // epubjs 的事件来自章节 iframe，clientX/clientY 已经是 iframe 视口坐标，
          // 不能再减父容器的 left/top，否则中央点击会被误判成翻页区域。
          const viewportWidth = sourceView?.innerWidth || rect.width;
          const viewportHeight = sourceView?.innerHeight || rect.height;
          if (viewportWidth <= 0 || viewportHeight <= 0) return;
          const ratio = clientX / viewportWidth;
          const verticalRatio = clientY / viewportHeight;
          if (settings.tapZoneMode === "five-zone" && ratio >= 0.24 && ratio <= 0.76) {
            if (verticalRatio < 0.26) {
              onBackwardRef.current?.();
              return;
            }
            if (verticalRatio > 0.74) {
              onForwardRef.current?.();
              return;
            }
          }
          if (ratio < 0.24) {
            onBackwardRef.current?.();
            return;
          }
          if (ratio > 0.76) {
            onForwardRef.current?.();
            return;
          }
          onToggleControlsRef.current?.();
        };

        renditionInstance.on("touchend", (event: TouchEvent) => {
          const start = touchStartRef.current;
          touchStartRef.current = null;
          if (!start) return;
          const touches = event.changedTouches;
          if (!touches.length) return;
          const dx = touches[0].clientX - start.x;
          const dy = touches[0].clientY - start.y;
          const elapsed = Date.now() - start.time;
          const absDx = Math.abs(dx);
          const absDy = Math.abs(dy);

          // 翻页模式才响应左右滑动手势；滚动模式保留原生滚动
          if (settings.readerMode === "paged" && absDx >= 30 && elapsed <= 500 && absDy < absDx * 1.2) {
            if (dx > 0) {
              onBackwardRef.current?.();
            } else {
              onForwardRef.current?.();
            }
            return;
          }

          // 轻触区域判断（兼容三分区/五分区）
          if (absDx < 12 && absDy < 12 && elapsed < 300) {
            lastTouchTapAtRef.current = Date.now();
            handleTapZone(touches[0].clientX, touches[0].clientY, event.view);
          }
        });

        // 部分 Android System WebView / 模拟器不会把 iframe 内轻触转发成 epubjs touchend，
        // 但会派发 click。保留 click 兜底，并抑制 touchend 后的合成 click，避免一次点击翻两页。
        renditionInstance.on("click", (event: MouseEvent) => {
          onInteractionRef.current?.();
          if (Date.now() - lastTouchTapAtRef.current < 350) return;
          handleTapZone(event.clientX, event.clientY, event.view);
        });

        // epubjs 把章节渲染在 iframe 中。部分 Android System WebView 不会把 iframe
        // 内的触控事件转发给 rendition，因此不能只依赖 rendition.on("touchend")。
        // 每次章节内容创建后直接监听它自己的 document，并在 rendition 销毁前解绑。
        const bindContentInteractions = (contents: Contents) => {
          const contentDocument = contents.document;
          if (!contentDocument || boundContentDocuments.has(contentDocument)) return;
          boundContentDocuments.add(contentDocument);

          const root = contentDocument.documentElement as HTMLElement | null;
          if (root) root.style.touchAction = settings.readerMode === "paged" ? "pan-y" : "pan-x pan-y";

          const isInteractiveTarget = (target: EventTarget | null) =>
            target instanceof Element && Boolean(target.closest("a,button,input,textarea,select,[contenteditable='true']"));

          const handleDirectTouchStart = (event: TouchEvent) => {
            onInteractionRef.current?.();
            if (event.touches.length !== 1) {
              touchStartRef.current = null;
              return;
            }
            touchStartRef.current = {
              x: event.touches[0].clientX,
              y: event.touches[0].clientY,
              time: Date.now()
            };
          };

          const handleDirectTouchEnd = (event: TouchEvent) => {
            const start = touchStartRef.current;
            touchStartRef.current = null;
            const touch = event.changedTouches[0];
            if (!start || !touch) return;
            const dx = touch.clientX - start.x;
            const dy = touch.clientY - start.y;
            const elapsed = Date.now() - start.time;
            const absDx = Math.abs(dx);
            const absDy = Math.abs(dy);

            if (settings.readerMode === "paged" && absDx >= 30 && elapsed <= 600 && absDx > absDy * 1.1) {
              if (event.cancelable) event.preventDefault();
              lastTouchTapAtRef.current = Date.now();
              if (dx > 0) onBackwardRef.current?.();
              else onForwardRef.current?.();
              return;
            }

            if (absDx < 12 && absDy < 12 && elapsed < 350 && !isInteractiveTarget(event.target)) {
              if (event.cancelable) event.preventDefault();
              lastTouchTapAtRef.current = Date.now();
              handleTapZone(touch.clientX, touch.clientY, contents.window);
            }
          };

          const handleDirectClick = (event: MouseEvent) => {
            onInteractionRef.current?.();
            if (Date.now() - lastTouchTapAtRef.current < 400 || isInteractiveTarget(event.target)) return;
            handleTapZone(event.clientX, event.clientY, contents.window);
          };

          contentDocument.addEventListener("touchstart", handleDirectTouchStart, { capture: true, passive: true });
          contentDocument.addEventListener("touchend", handleDirectTouchEnd, { capture: true, passive: false });
          contentDocument.addEventListener("click", handleDirectClick, true);
          contentInteractionCleanups.add(() => {
            contentDocument.removeEventListener("touchstart", handleDirectTouchStart, true);
            contentDocument.removeEventListener("touchend", handleDirectTouchEnd, true);
            contentDocument.removeEventListener("click", handleDirectClick, true);
          });
        };

        renditionInstance.hooks.content.register(bindContentInteractions);

        // 初始显示
        const firstTarget = readerDocument.toc[0]?.href ?? 0;
        const restoredSection = initialCfi?.startsWith("epubcfi(")
          ? bookInstance.spine.get(initialCfi)
          : undefined;
        if (initialCfi && initialCfi.startsWith("epubcfi(") && restoredSection && isReadableEpubSpineItem(restoredSection)) {
          try {
            await renditionInstance.display(initialCfi);
          } catch {
            // 书籍重新导入或排版变化后旧 CFI 可能失效，不能因此让整本书打不开。
            if (typeof firstTarget === "number") await renditionInstance.display(firstTarget);
            else await renditionInstance.display(firstTarget);
          }
        } else {
          if (typeof firstTarget === "number") await renditionInstance.display(firstTarget);
          else await renditionInstance.display(firstTarget);
        }
        if (!cancelled && !timedOut) {
          initialized = true;
          window.clearTimeout(openTimeout);
          setReady(true);
          onReadyRef.current();
        }
      } catch (error) {
        if (!cancelled && !timedOut) {
          window.clearTimeout(openTimeout);
          const detail = error instanceof Error ? error.message : String(error);
          addMobileLog("error", "EPUB 阅读器", `${book.title}：${detail}`, { code: "EPUB_OPEN_FAILED" });
          onErrorRef.current(detail);
        }
      }
    }

    void init();

    return () => {
      cancelled = true;
      window.clearTimeout(openTimeout);
      setReady(false);
      contentInteractionCleanups.forEach((cleanup) => cleanup());
      contentInteractionCleanups.clear();
      try {
        renditionInstance?.destroy();
        bookInstance?.destroy();
      } catch {
        // 忽略销毁错误
      }
      renditionRef.current = null;
      bookRef.current = null;
    };
  }, [book.id, content, settings.readerMode]);

  //  settings 变化时更新主题（不重新初始化）
  useEffect(() => {
    const rendition = renditionRef.current;
    if (!rendition || !ready) return;
    rendition.themes.register("reader-theme", readerThemeRules(settings));
    rendition.themes.select("reader-theme");
    (rendition.resize as (width?: number, height?: number) => void)();
  }, [settings.fontSize, settings.lineHeight, settings.pageMargin, settings.paragraphSpacing, settings.readerBackground, ready]);

  return (
    <div
      ref={containerRef}
      className={`reader-epub-view reader-epub-${settings.readerMode}`}
      style={{
        width: "100%",
        height: "100%",
        overflow: "hidden",
        opacity: ready ? 1 : 0,
        transition: "opacity 200ms ease"
      }}
    />
  );
});
