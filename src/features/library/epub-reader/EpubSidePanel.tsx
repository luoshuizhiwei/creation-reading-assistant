import { Bookmark, Highlighter, List, Trash2 } from "lucide-react";
import { Button, ShellPanel } from "@/components/ui";
import { saveHighlight } from "@/services/annotation-service";
import type { BookmarkItem, EpubTocItem, HighlightItem } from "@/types/library";
import { HIGHLIGHT_COLORS, highlightHex } from "./highlight-colors";

type SidePanelTab = "toc" | "highlights" | "bookmarks";

export interface EpubSidePanelProps {
  tocCollapsed: boolean;
  onExpand(): void;
  onCollapse(): void;
  sidePanelTab: SidePanelTab;
  onTabChange(tab: SidePanelTab): void;
  progressPercent: number;
  currentTocItem?: EpubTocItem;
  toc: EpubTocItem[];
  onJumpToToc(href: string): void;
  highlights: HighlightItem[];
  onRemoveHighlight(id: string): void;
  onJumpToHighlight(highlight: HighlightItem): void;
  onHighlightsChange(next: HighlightItem[]): void;
  bookmarks: BookmarkItem[];
  onAddBookmark(): void;
  onJumpToBookmark(bookmark: BookmarkItem): void;
  onRemoveBookmark(id: string): void;
}

export function EpubSidePanel({
  tocCollapsed,
  onExpand,
  onCollapse,
  sidePanelTab,
  onTabChange,
  progressPercent,
  currentTocItem,
  toc,
  onJumpToToc,
  highlights,
  onRemoveHighlight,
  onJumpToHighlight,
  onHighlightsChange,
  bookmarks,
  onAddBookmark,
  onJumpToBookmark,
  onRemoveBookmark
}: EpubSidePanelProps) {
  if (tocCollapsed) {
    return (
      <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
        <div className="grid gap-3">
          <button className="rounded-md border border-paper-line bg-paper-panel p-2 text-xs text-paper-muted hover:text-paper-ink" onClick={onExpand}>
            目录
          </button>
          <div className="text-center text-[11px] leading-5 text-paper-muted">{progressPercent}%</div>
        </div>
      </ShellPanel>
    );
  }

  return (
    <ShellPanel className="min-h-0 overflow-auto border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
      <div className="mb-3 flex items-center gap-1 border-b border-paper-line pb-2">
        <button
          className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "toc" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
          onClick={() => onTabChange("toc")}
        >
          <List size={13} className="mr-1 inline" />目录
        </button>
        <button
          className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "highlights" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
          onClick={() => onTabChange("highlights")}
        >
          <Highlighter size={13} className="mr-1 inline" />高亮{highlights.length > 0 ? ` (${highlights.length})` : ""}
        </button>
        <button
          className={`rounded-md px-2.5 py-1 text-xs font-medium transition ${sidePanelTab === "bookmarks" ? "bg-copper/10 text-copper" : "text-paper-muted hover:bg-paper-panel hover:text-paper-ink"}`}
          onClick={() => onTabChange("bookmarks")}
        >
          <Bookmark size={13} className="mr-1 inline" />书签{bookmarks.length > 0 ? ` (${bookmarks.length})` : ""}
        </button>
        <span className="flex-1" />
        <Button variant="quiet" className="h-7 px-2 text-xs" onClick={onCollapse}>
          收起
        </Button>
      </div>

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
                    onClick={() => onJumpToToc(item.href)}
                  >
                    <span className="line-clamp-2">{item.label}</span>
                  </button>
                ))}
              </div>
            )}
          </div>
        </>
      )}

      {sidePanelTab === "highlights" && (
        <div className="grid gap-2">
          {highlights.length === 0 ? (
            <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-4 text-center text-sm text-paper-muted">
              暂无高亮。在书中选中文字后点击“高亮”按钮即可添加。
            </div>
          ) : (
            highlights.map((hl) => (
              <div key={hl.id} className="group rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:shadow-lift">
                <div className="mb-1.5 flex items-center gap-2">
                  <span className="inline-block h-3 w-3 rounded-full" style={{ background: highlightHex(hl.color) }} />
                  {hl.chapterTitle && <span className="flex-1 truncate text-[11px] text-paper-muted">{hl.chapterTitle}</span>}
                  <button
                    className="rounded p-1 text-paper-muted opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                    onClick={() => onRemoveHighlight(hl.id)}
                    title="删除高亮"
                  >
                    <Trash2 size={12} />
                  </button>
                </div>
                <button
                  className="w-full text-left text-sm leading-5 text-paper-ink hover:text-copper"
                  onClick={() => onJumpToHighlight(hl)}
                >
                  <span className="line-clamp-3">{hl.text}</span>
                </button>
                {hl.note && <div className="mt-1.5 text-xs italic text-paper-muted">{hl.note}</div>}
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
                          onHighlightsChange(highlights.map((h) => (h.id === hl.id ? updated : h)));
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

      {sidePanelTab === "bookmarks" && (
        <div className="grid gap-2">
          <Button variant="secondary" className="h-8 text-xs" onClick={onAddBookmark}>
            <Bookmark size={14} />
            在当前位置添加书签
          </Button>
          {bookmarks.length === 0 ? (
            <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-4 text-center text-sm text-paper-muted">
              暂无书签。
            </div>
          ) : (
            bookmarks.map((bm) => (
              <div key={bm.id} className="group flex items-center gap-2 rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:shadow-lift">
                <Bookmark size={14} className="shrink-0 text-copper/60" />
                <button className="min-w-0 flex-1 text-left" onClick={() => onJumpToBookmark(bm)}>
                  <div className="truncate text-sm text-paper-ink hover:text-copper">{bm.label}</div>
                  <div className="mt-0.5 text-[11px] text-paper-muted">
                    {bm.chapterTitle ? `${bm.chapterTitle} · ` : ""}
                    {bm.progressPercent !== undefined ? `${Math.round(bm.progressPercent * 100)}%` : ""}
                    {" · "}{new Date(bm.createdAt).toLocaleDateString()}
                  </div>
                </button>
                <button
                  className="rounded p-1 text-paper-muted opacity-0 transition hover:text-red-500 group-hover:opacity-100"
                  onClick={() => onRemoveBookmark(bm.id)}
                  title="删除书签"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            ))
          )}
        </div>
      )}
    </ShellPanel>
  );
}
