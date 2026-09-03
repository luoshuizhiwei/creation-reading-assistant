import { useMemo } from "react";
import { ShellPanel } from "@/components/ui";
import { ReaderSidePanel, type SidePanelTab } from "@/features/library/ReaderSidePanel";
import type { BookmarkItem, EpubTocItem, HighlightItem } from "@/types/library";

export type { SidePanelTab };

export interface EpubSidePanelProps {
  tocCollapsed: boolean;
  onExpand(): void;
  onCollapse(): void;
  sidePanelTab: SidePanelTab;
  onTabChange(tab: SidePanelTab): void;
  progressPercent: number;
  currentTocItem?: EpubTocItem;
  toc: EpubTocItem[];
  /** 目录顺序中位于当前位置之前的章节（已读弱化） */
  readIds?: ReadonlySet<string>;
  /** 目录头部统计（如"已读 3/24"） */
  tocSummary?: string;
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
  readIds,
  tocSummary,
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
  const tocEntries = useMemo(
    () => toc.map((item) => ({ id: item.id, label: item.label, level: item.level })),
    [toc]
  );

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
    <ReaderSidePanel
      sidePanelTab={sidePanelTab}
      onTabChange={onTabChange}
      onCollapse={onCollapse}
      progressPercent={progressPercent}
      tocTitle="目录"
      tocEntries={tocEntries}
      currentTocId={currentTocItem?.id}
      readIds={readIds}
      tocSummary={tocSummary ?? (currentTocItem ? `当前位置：${currentTocItem.label}` : `${progressPercent}% 附近`)}
      onTocJump={(id) => {
        const item = toc.find((entry) => entry.id === id);
        if (item) onJumpToToc(item.href);
      }}
      tocEmptyText="未检测到目录"
      highlights={highlights}
      onRemoveHighlight={onRemoveHighlight}
      onJumpToHighlight={onJumpToHighlight}
      onHighlightsChange={onHighlightsChange}
      bookmarks={bookmarks}
      onAddBookmark={onAddBookmark}
      onJumpToBookmark={onJumpToBookmark}
      onRemoveBookmark={onRemoveBookmark}
    />
  );
}
