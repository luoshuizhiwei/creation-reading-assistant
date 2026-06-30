import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, BarChart3, BookOpen, Lightbulb, Settings } from "lucide-react";
import MarkdownIt from "markdown-it";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { EpubReaderPage } from "@/features/library/EpubReaderPage";
import { ReaderSettingsPanel } from "@/features/library/ReaderSettingsPanel";
import { useReaderProgress } from "@/hooks/useReaderProgress";
import { useReadingSessionTracker } from "@/hooks/useReadingSessionTracker";
import { createInspiration } from "@/services/inspiration-service";
import { updateReaderSettings } from "@/services/reader-service";
import { resetReaderSettings } from "@/services/settings-service";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import type { InspirationItem } from "@/types/inspiration";
import { formatDuration, readerShellClass, readerPaperClass } from "@/utils/format";

const ALLOWED_LINK_PROTOCOLS = /^(https?|mailto|file|ftp):/i;

function sanitizeMarkdownHtml(html: string): string {
  return html.replace(/<a\s+([^>]*?)href="([^"]*?)"([^>]*?)>/gi, (_match, before, href, after) => {
    const protocol = href.replace(/\s/g, "").split(":")[0];
    if (href.startsWith("#") || ALLOWED_LINK_PROTOCOLS.test(href)) {
      return `<a ${before}href="${href}" rel="noopener noreferrer" target="_blank"${after}>`;
    }
    return `<a ${before}href="#" data-blocked-protocol="${protocol}"${after}>`;
  });
}

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true
});

interface MarkdownTocItem {
  id: string;
  title: string;
  level: number;
}

function slugify(value: string, index: number): string {
  const slug = value
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
  return slug || `heading-${index + 1}`;
}

function markdownToc(content: string): MarkdownTocItem[] {
  return content
    .replace(/\r\n/g, "\n")
    .split("\n")
    .map((line, index) => {
      const match = line.trim().match(/^(#{1,6})\s+(.+)$/);
      if (!match) return undefined;
      const title = match[2].replace(/[*_`~[\]()]/g, "").trim();
      return {
        id: slugify(title, index),
        title,
        level: match[1].length
      };
    })
    .filter((item): item is MarkdownTocItem => Boolean(item));
}

function renderMarkdownHtml(content: string, toc: MarkdownTocItem[]): string {
  let headingIndex = 0;
  const originalHeadingOpen =
    markdown.renderer.rules.heading_open ??
    ((tokens, idx, options, _env, self) => {
      return self.renderToken(tokens, idx, options);
    });

  markdown.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
    const tocItem = toc[headingIndex];
    if (tocItem) tokens[idx].attrSet("id", tocItem.id);
    headingIndex += 1;
    return originalHeadingOpen(tokens, idx, options, env, self);
  };

  return markdown.render(content);
}

function renderPlainText(content: string) {
  return content.split(/\n{2,}/).map((paragraph, index) => (
    <p key={index} className="mb-5 whitespace-pre-wrap">
      {paragraph}
    </p>
  ));
}

function progressLabel(progressPercent: number): string {
  return `阅读进度 ${Math.round(progressPercent)}% 附近`;
}

function TextReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  const content = useLibraryStore((state) => state.activeContent);
  const progress = useLibraryStore((state) => (state.activeBook ? state.progress[state.activeBook.id] : undefined));
  const settings = useLibraryStore((state) => state.readerSettings);
  const activeSession = useLibraryStore((state) => state.activeSession);
  const activity = useLibraryStore((state) => state.activity);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setScreen = useAppStore((state) => state.setScreen);
  const setReaderReturn = useAppStore((state) => state.setReaderReturn);
  const setSelectedId = useInspirationStore((state) => state.setSelectedId);
  const showToast = useUIStore((state) => state.showToast);
  const [createdInspiration, setCreatedInspiration] = useState<InspirationItem>();
  const scrollerRef = useRef<HTMLDivElement>(null);
  const { scheduleSave, flushProgress, getCurrentLocation } = useReaderProgress(scrollerRef);
  const { recordInteraction, endTracking } = useReadingSessionTracker(scrollerRef, getCurrentLocation);

  useEffect(() => {
    const scroller = scrollerRef.current;
    const location = progress?.currentLocation;
    const scrollTop = location?.scroll?.scrollTop;
    if (!settings?.restoreLastPosition || !scroller || typeof scrollTop !== "number") return;
    window.setTimeout(() => {
      scroller.scrollTop = scrollTop;
    }, 80);
  }, [activeBook?.id, progress?.currentLocation?.scroll?.scrollTop, settings?.restoreLastPosition]);

  const toc = useMemo(() => (activeBook?.format === "md" ? markdownToc(content) : []), [activeBook?.format, content]);
  const markdownHtml = useMemo(() => (activeBook?.format === "md" ? renderMarkdownHtml(content, toc) : ""), [activeBook?.format, content, toc]);

  if (!activeBook || !settings) {
    return (
      <ShellPanel className="h-full border-0">
        <EmptyState title="没有打开书籍" body="从书库中选择一本 TXT、Markdown 或 EPUB，开始阅读并记录进度。" />
      </ShellPanel>
    );
  }

  const progressPercent = Math.round((progress?.progressPercent ?? 0) * 100);

  const handleActivity = () => {
    recordInteraction();
    scheduleSave();
  };

  const handleSettingsChange = async (patch: Partial<typeof settings>) => {
    const next = await updateReaderSettings(patch);
    setReaderSettings(next);
    handleActivity();
  };

  const resetInlineReaderSettings = async () => {
    const next = await resetReaderSettings();
    setReaderSettings(next.reader);
    handleActivity();
  };

  const openCreatedInspiration = () => {
    if (!createdInspiration) return;
    setSelectedId(createdInspiration.id);
    setReaderReturn({
      bookId: activeBook.id,
      label: `返回阅读：《${activeBook.title}》`,
      fromScreen: "reader",
      progressLabel: progressLabel(progressPercent)
    });
    setScreen("inspiration", { preserveReturn: true });
  };

  const createReadingInspiration = async () => {
    const location = getCurrentLocation();
    const selectedText = window.getSelection()?.toString().trim().slice(0, 800);
    const sourceProgress = location?.progressPercent === undefined ? undefined : location.progressPercent * 100;
    const item = await createInspiration({
      title: `阅读灵感：${activeBook.title}`,
      body: "",
      type: "note",
      status: "inbox",
      tags: ["阅读札记", activeBook.format.toUpperCase()],
      platformTags: [],
      sourceBookId: activeBook.id,
      sourceLocation: {
        format: activeBook.format,
        progressPercent: sourceProgress,
        scrollTop: location?.scroll?.scrollTop,
        excerpt: selectedText,
        createdFrom: selectedText ? "reader-selection" : "reader-note"
      },
      source: {
        bookId: activeBook.id,
        bookTitle: activeBook.title,
        bookAuthor: activeBook.author,
        format: activeBook.format,
        locationLabel: progressLabel(sourceProgress ?? progressPercent),
        progressPercent: sourceProgress,
        excerpt: selectedText,
        scrollTop: location?.scroll?.scrollTop,
        createdFrom: selectedText ? "reader-selection" : "reader-note",
        createdAt: new Date().toISOString()
      }
    });
    setCreatedInspiration(item);
    showToast({
      tone: "success",
      title: "已记录为灵感",
      body: selectedText ? "选中的文字已保存到来源摘录，可以继续阅读。" : "已记录书名和当前位置，可以继续阅读。"
    });
  };

  return (
    <div className="grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <header className="paper-topbar flex items-center gap-3 px-5">
        <BookOpen size={18} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold text-paper-ink">{activeBook.title}</div>
          <div className="text-xs text-paper-muted">
            阅读进度：{progressPercent}% · 本书累计 {formatDuration(progress?.totalReadingTimeMs)}
          </div>
        </div>
        <Button variant="quiet" onClick={() => void createReadingInspiration()}>
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
            await endTracking("leave-reader");
            setScreen("settings");
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

      <div className="grid min-h-0 grid-cols-[1fr_320px]">
        <div
          ref={scrollerRef}
          className={`min-h-0 overflow-auto ${readerShellClass(settings.readerBackground)}`}
          onScroll={handleActivity}
          onWheel={handleActivity}
          onKeyDown={handleActivity}
          onPointerDown={handleActivity}
          onMouseUp={() => {
            if (window.getSelection()?.toString()) handleActivity();
          }}
          tabIndex={0}
        >
          {createdInspiration && (
            <div className="motion-notice sticky top-4 z-10 mx-auto mt-4 flex w-[min(760px,calc(100%-32px))] items-center gap-3 rounded-xl border border-copper/25 bg-paper-panel/95 px-4 py-3 text-sm text-paper-muted shadow-paper">
              <span className="flex-1">已记录为灵感。你可以继续阅读，也可以现在查看灵感。</span>
              <Button variant="secondary" onClick={openCreatedInspiration}>
                查看灵感
              </Button>
              <Button variant="quiet" onClick={() => setCreatedInspiration(undefined)}>
                继续阅读
              </Button>
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
                paddingLeft: settings.pageMargin,
                paddingRight: settings.pageMargin
              }),
              [settings.fontSize, settings.lineHeight, settings.pageMargin]
            )}
          >
            {activeBook.format === "md" ? <div dangerouslySetInnerHTML={{ __html: sanitizeMarkdownHtml(markdownHtml) }} /> : renderPlainText(content)}
          </article>
        </div>
        <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
          {activeBook.format === "md" && (
            <div className="mb-5">
              <div className="mb-3 text-sm font-semibold text-paper-ink">Markdown 目录</div>
              {toc.length === 0 ? (
                <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-3 text-sm text-paper-muted">未检测到标题</div>
              ) : (
                <div className="grid gap-1">
                  {toc.map((item) => (
                    <button
                      key={item.id}
                      className="rounded px-2 py-1.5 text-left text-sm text-paper-muted hover:bg-paper-panel hover:text-paper-ink"
                      style={{ paddingLeft: `${8 + Math.max(0, item.level - 1) * 12}px` }}
                      onClick={() => document.getElementById(item.id)?.scrollIntoView({ behavior: "smooth", block: "start" })}
                    >
                      {item.title}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          <ReaderSettingsPanel settings={settings} onChange={(patch) => void handleSettingsChange(patch)} onReset={() => void resetInlineReaderSettings()} />
          {settings.tracking.showReadingStatsCards && (
            <div className="mt-5 rounded-xl border border-paper-line bg-paper-panel p-3 text-xs leading-6 text-paper-muted shadow-lift">
              <div className="font-semibold text-paper-ink">当前会话</div>
              <div>状态：{activity.isTracking ? (activity.isUserActive ? "计时中" : "已暂停") : "未追踪"}</div>
              <div>窗口：{activity.isWindowFocused ? "前台" : "后台"}</div>
              <div>有效时长：{formatDuration(activeSession?.activeDurationMs)}</div>
            </div>
          )}
        </ShellPanel>
      </div>
    </div>
  );
}

export function ReaderPage() {
  const activeBook = useLibraryStore((state) => state.activeBook);
  if (activeBook?.format === "epub") return <EpubReaderPage />;
  return <TextReaderPage />;
}

