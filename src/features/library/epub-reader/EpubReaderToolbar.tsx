import { ArrowLeft, BookOpen, Bookmark, BookmarkCheck, ChartColumn, Highlighter, Quote, Settings } from "lucide-react";
import { Button } from "@/components/ui";
import { formatDuration } from "@/utils/format";

export interface EpubReaderToolbarProps {
  title: string;
  progressPercent: number;
  totalReadingTimeMs?: number;
  isCurrentBookmarked: boolean;
  /** 当前书中是否有选中文字：无选区时高亮/摘录置灰（替代点击后的报错） */
  hasSelection?: boolean;
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
  hasSelection = false,
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
        {/* BD-229（WCAG 1.3.1 / G130）：这一屏此前一个标题元素都没有（实测 /tmp/bd229-epub-before.log
            h1count=0）——正文在 epub.js 的 iframe 里，外层读屏大纲整屏是空的。工具条上这句书名
            就是这一屏的正身（BD-228 的窗口标题也抄的它），给它标题语义。档口照旧：
            text-sm/600 写在类上，preflight 把标题档重置过，改 tag 零像素漂移。 */}
        <h1 className="truncate text-sm font-semibold text-paper-ink">{title}</h1>
        <div className="text-xs text-paper-muted">
          EPUB 阅读进度：{progressPercent}% · 本书累计 {formatDuration(totalReadingTimeMs)}
        </div>
      </div>
      <Button variant="quiet" onClick={onToggleBookmark} title={isCurrentBookmarked ? "移除书签" : "添加书签"}>
        {isCurrentBookmarked ? <BookmarkCheck size={16} /> : <Bookmark size={16} />}
        {isCurrentBookmarked ? "已书签" : "书签"}
      </Button>
      <Button variant="quiet" disabled={!hasSelection} onClick={onHighlightSelection} title={hasSelection ? "高亮选中文字（黄色）" : "先在书中选中文字"}>
        <Highlighter size={16} />
        高亮
      </Button>
      <Button variant="quiet" disabled={!hasSelection} onClick={onExcerpt} title={hasSelection ? "摘录到资料" : "先在书中选中文字"}>
        <Quote size={16} />
        摘录
      </Button>
      <Button variant="quiet" onClick={onOpenSettings}>
        <Settings size={16} />
        设置
      </Button>
      <Button variant="quiet" onClick={onOpenStats}>
        <ChartColumn size={16} />
        阅读统计
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
