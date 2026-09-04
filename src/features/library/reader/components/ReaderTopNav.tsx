import { ArrowLeft, BookOpen, Search, Settings } from "lucide-react";
import { Button } from "@/components/ui";

export interface ReaderTopNavProps {
  title: string;
  author?: string;
  currentChapterTitle?: string;
  progressPercent: number;
  totalReadingTimeMs?: number;
  isTocOpen: boolean;
  isSearchOpen: boolean;
  onBack: () => void;
  onToggleSearch: () => void;
  onToggleToc: () => void;
  onOpenSettings: () => void;
}

export function ReaderTopNav({
  title,
  author,
  currentChapterTitle,
  progressPercent,
  isTocOpen,
  isSearchOpen,
  onBack,
  onToggleSearch,
  onToggleToc,
  onOpenSettings
}: ReaderTopNavProps) {
  return (
    <header className="reader-topbar flex items-center justify-between border-b border-paper-line/70 px-4 py-2 text-sm select-none">
      <div className="flex items-center gap-3 min-w-0">
        <Button
          variant="quiet"
          aria-label="返回书库"
          onClick={onBack}
          className="h-8 shrink-0 gap-1.5 px-2.5 text-xs text-paper-ink"
        >
          <ArrowLeft size={16} />
          <span>书库</span>
        </Button>
        <div className="flex items-baseline gap-2 min-w-0">
          <h1 className="text-base font-semibold text-paper-ink truncate max-w-[200px] sm:max-w-xs md:max-w-md" title={title}>
            {title}
          </h1>
          {author && (
            <span className="hidden sm:inline text-xs text-paper-muted truncate max-w-[120px]" title={author}>
              {author}
            </span>
          )}
          {currentChapterTitle && (
            <span
              className="hidden md:inline text-xs text-copper font-medium truncate max-w-[200px]"
              title={currentChapterTitle}
            >
              · {currentChapterTitle}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2 sm:gap-3 text-xs text-paper-muted shrink-0">
        <span className="tabular-nums font-medium">{progressPercent}%</span>

        <Button
          variant="quiet"
          aria-label="全文搜索"
          title="全文搜索 (Ctrl+F)"
          onClick={onToggleSearch}
          className={`h-8 w-8 p-0 ${isSearchOpen ? "text-copper bg-paper-soft/80" : ""}`}
        >
          <Search size={16} />
        </Button>

        <Button
          variant="quiet"
          aria-label={isTocOpen ? "收起目录" : "展开目录"}
          title={isTocOpen ? "收起目录" : "展开目录"}
          onClick={onToggleToc}
          className={`h-8 w-8 p-0 ${isTocOpen ? "text-copper bg-paper-soft/80" : ""}`}
        >
          <BookOpen size={16} />
        </Button>

        <Button
          variant="quiet"
          aria-label="阅读设置"
          title="阅读设置"
          onClick={onOpenSettings}
          className="h-8 w-8 p-0"
        >
          <Settings size={16} />
        </Button>
      </div>
    </header>
  );
}
