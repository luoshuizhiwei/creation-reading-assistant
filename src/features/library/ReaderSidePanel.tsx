import { useState, type ReactNode } from "react";
import { Bookmark, Highlighter, List, Trash2 } from "lucide-react";
import { Button, ShellPanel } from "@/components/ui";
import { saveHighlight } from "@/services/annotation-service";
import { TocList } from "@/features/library/toc/TocList";
import type { TocEntry } from "@/features/library/toc/tree";
import type { BookmarkItem, HighlightItem } from "@/types/library";
import { HIGHLIGHT_COLORS, highlightHex } from "./epub-reader/highlight-colors";

export type SidePanelTab = "toc" | "highlights" | "bookmarks";

/**
 * 三格式共用的阅读器侧栏面板（目录 / 高亮 / 书签 三 Tab）。
 * EPUB 与 TXT/MD 的差异全部通过 props 注入：目录跳转是 id 语义（EPUB 由宿主
 * 把 id 映射回 href），高亮/书签列表与操作完全同构。
 */
export interface ReaderSidePanelProps {
  sidePanelTab: SidePanelTab;
  onTabChange(tab: SidePanelTab): void;
  onCollapse(): void;
  progressPercent: number;

  tocTitle: string;
  tocEntries: TocEntry[];
  currentTocId?: string;
  /** 派生的已读集合（当前章之前的章节），用于行内弱化标记 */
  readIds?: ReadonlySet<string>;
  /** 目录 Tab 头部附加操作（如 TXT 的"编辑"按钮） */
  tocHeaderExtra?: ReactNode;
  /** 替换默认 TocList 的自定义目录体（如 TXT 目录编辑模式） */
  tocBody?: ReactNode;
  tocSummary?: string;
  onTocJump(id: string): void;
  tocEmptyText: string;

  highlights: HighlightItem[];
  onRemoveHighlight(id: string): void;
  onJumpToHighlight(highlight: HighlightItem): void;
  onHighlightsChange(next: HighlightItem[]): void;

  bookmarks: BookmarkItem[];
  onAddBookmark(): void;
  onJumpToBookmark(bookmark: BookmarkItem): void;
  onRemoveBookmark(id: string): void;
}

/**
 * 删除确认（两步点击，与收件箱"移出"一致）：
 * 第一次点击进入确认态，再次点击才执行删除；点击其它条目时自动重置。
 */
function useConfirmDelete() {
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const request = (id: string) => {
    if (confirmingId === id) return true;
    setConfirmingId(id);
    return false;
  };
  const reset = () => setConfirmingId(null);
  return { confirmingId, request, reset };
}

export function ReaderSidePanel({
  sidePanelTab,
  onTabChange,
  onCollapse,
  progressPercent,
  tocTitle,
  tocEntries,
  currentTocId,
  readIds,
  tocHeaderExtra,
  tocBody,
  tocSummary,
  onTocJump,
  tocEmptyText,
  highlights,
  onRemoveHighlight,
  onJumpToHighlight,
  onHighlightsChange,
  bookmarks,
  onAddBookmark,
  onJumpToBookmark,
  onRemoveBookmark
}: ReaderSidePanelProps) {
  const highlightDelete = useConfirmDelete();
  const bookmarkDelete = useConfirmDelete();
  return (
    <ShellPanel className="flex min-h-0 flex-col overflow-hidden border-y-0 border-r-0 bg-paper-soft/45 p-4 shadow-none">
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
        <div className="flex min-h-0 flex-1 flex-col">
          <div className="mb-2 flex items-baseline justify-between gap-2">
            <div className="text-sm font-semibold text-paper-ink">{tocTitle}</div>
            <div className="text-[11px] text-paper-muted">{tocSummary ?? `${progressPercent}% 附近`}</div>
          </div>
          {tocHeaderExtra}
          <div className="min-h-0 flex-1">
            {tocBody ?? (
              <TocList
                className="min-h-0 flex-1"
                entries={tocEntries}
                currentId={currentTocId}
                readIds={readIds}
                onJump={onTocJump}
                emptyText={tocEmptyText}
              />
            )}
          </div>
        </div>
      )}

      {sidePanelTab === "highlights" && (
        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="grid gap-2">
            {highlights.length === 0 ? (
              <div className="rounded-lg border border-dashed border-paper-line bg-paper-panel/70 p-4 text-center text-sm text-paper-muted">
                暂无高亮。在书中选中文字后点击“高亮”按钮即可添加。
              </div>
            ) : (
              highlights.map((hl) => (
                <div key={hl.id} className="group rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:[box-shadow:var(--shadow-2)]">
                  <div className="mb-1.5 flex items-center gap-2">
                    <span className="inline-block h-3 w-3 rounded-full" style={{ background: highlightHex(hl.color) }} />
                    {hl.chapterTitle && <span className="flex-1 truncate text-[11px] text-paper-muted">{hl.chapterTitle}</span>}
                    <button
                      className={`rounded p-1 text-paper-muted transition hover:text-[color:var(--proof-mark)] group-hover:opacity-100 ${highlightDelete.confirmingId === hl.id ? "opacity-100 bg-[color:var(--proof-tint)] text-[color:var(--proof-mark)]" : "opacity-0"}`}
                      onClick={() => {
                        if (highlightDelete.request(hl.id)) {
                          onRemoveHighlight(hl.id);
                          highlightDelete.reset();
                        }
                      }}
                      title={highlightDelete.confirmingId === hl.id ? "再次点击确认删除" : "删除高亮"}
                    >
                      {highlightDelete.confirmingId === hl.id ? "确认?" : <Trash2 size={12} />}
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
        </div>
      )}

      {sidePanelTab === "bookmarks" && (
        <div className="min-h-0 flex-1 overflow-y-auto">
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
                <div key={bm.id} className="group flex items-center gap-2 rounded-lg border border-paper-line bg-paper-panel p-3 transition hover:[box-shadow:var(--shadow-2)]">
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
                    className={`rounded p-1 text-paper-muted transition hover:text-[color:var(--proof-mark)] group-hover:opacity-100 ${bookmarkDelete.confirmingId === bm.id ? "opacity-100 bg-[color:var(--proof-tint)] text-[color:var(--proof-mark)]" : "opacity-0"}`}
                    onClick={() => {
                      if (bookmarkDelete.request(bm.id)) {
                        onRemoveBookmark(bm.id);
                        bookmarkDelete.reset();
                      }
                    }}
                    title={bookmarkDelete.confirmingId === bm.id ? "再次点击确认删除" : "删除书签"}
                  >
                    {bookmarkDelete.confirmingId === bm.id ? "确认?" : <Trash2 size={12} />}
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </ShellPanel>
  );
}
