import { formatDuration } from "@/utils/format";

export interface ReaderBottomBarProps {
  currentPage: number;
  totalPages: number;
  totalWords: number;
  progressPercent: number;
  totalReadingTimeMs?: number;
  onSeekPercent?: (percent: number) => void;
}

export function ReaderBottomBar({
  currentPage,
  totalPages,
  totalWords,
  progressPercent,
  totalReadingTimeMs,
  onSeekPercent
}: ReaderBottomBarProps) {
  return (
    <footer className="reader-bottombar flex items-center justify-between gap-4 border-t border-paper-line/70 px-4 py-1.5 text-xs text-paper-muted select-none bg-paper-panel/75 backdrop-blur-sm">
      <div className="flex items-center gap-3 shrink-0">
        <span className="tabular-nums">
          第 {currentPage} / {totalPages} 页
        </span>
        <span className="hidden sm:inline text-paper-line">|</span>
        <span className="hidden sm:inline tabular-nums">
          {totalWords.toLocaleString()} 字
        </span>
      </div>

      <div className="flex flex-1 items-center justify-center gap-2 max-w-xs md:max-w-md mx-2">
        <input
          type="range"
          min={0}
          max={100}
          value={progressPercent}
          onChange={(e) => onSeekPercent?.(Number(e.target.value))}
          className="h-1.5 w-full cursor-pointer accent-copper bg-paper-line/60 rounded-full appearance-none"
          aria-label="阅读进度跳转"
          title={`进度：${progressPercent}%`}
        />
        <span className="text-[11px] tabular-nums shrink-0 w-9 text-right">{progressPercent}%</span>
      </div>

      <div className="flex items-center gap-3 shrink-0 text-right">
        {totalReadingTimeMs !== undefined && (
          <span className="tabular-nums">
            阅读时长 {formatDuration(totalReadingTimeMs)}
          </span>
        )}
      </div>
    </footer>
  );
}
