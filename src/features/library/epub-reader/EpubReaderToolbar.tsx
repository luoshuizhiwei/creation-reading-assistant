import { ArrowLeft, BookOpen, Bookmark, BookmarkCheck, ChartColumn, Highlighter, Quote, Settings } from "lucide-react";
import { Button } from "@/components/ui";
import { formatDuration } from "@/utils/format";

export interface EpubReaderToolbarProps {
  title: string;
  progressPercent: number;
  totalReadingTimeMs?: number;
  isCurrentBookmarked: boolean;
  onToggleBookmark(): void;
  onHighlightSelection(): void;
  onExcerpt(): void;
  onOpenSettings(): void;
  onOpenStats(): void;
  onBackToLibrary(): void;
  onBackToHome(): void;
}

export function EpubReaderToolbar({
  title,
  progressPercent,
  totalReadingTimeMs,
  isCurrentBookmarked,
  onToggleBookmark,
  onHighlightSelection,
  onExcerpt,
  onOpenSettings,
  onOpenStats,
  onBackToLibrary,
  onBackToHome
}: EpubReaderToolbarProps) {
  return (
    <header className="paper-topbar flex items-center gap-3 px-5">
      <BookOpen size={18} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-semibold text-paper-ink">{title}</div>
        <div className="text-xs text-paper-muted">
          EPUB 阅读进度：{progressPercent}% · 本书累计 {formatDuration(totalReadingTimeMs)}
        </div>
      </div>
      <Button variant="quiet" onClick={onToggleBookmark} title={isCurrentBookmarked ? "移除书签" : "添加书签"}>
        {isCurrentBookmarked ? <BookmarkCheck size={16} /> : <Bookmark size={16} />}
        {isCurrentBookmarked ? "已书签" : "书签"}
      </Button>
      <Button variant="quiet" onClick={onHighlightSelection} title="高亮选中文字（黄色）">
        <Highlighter size={16} />
        高亮
      </Button>
      <Button variant="quiet" onClick={onExcerpt} title="摘录到资料">
        <Quote size={16} />
        摘录
      </Button>
      <Button variant="quiet" onClick={onOpenSettings}>
        <Settings size={16} />
        设置
      </Button>
      <Button variant="quiet" onClick={onOpenStats}>
        <ChartColumn size={16} />
        统计
      </Button>
      <Button variant="quiet" onClick={onBackToLibrary}>
        <ArrowLeft size={16} />
        返回书库
      </Button>
      <Button variant="quiet" onClick={onBackToHome}>
        <ArrowLeft size={16} />
        返回首页
      </Button>
    </header>
  );
}
