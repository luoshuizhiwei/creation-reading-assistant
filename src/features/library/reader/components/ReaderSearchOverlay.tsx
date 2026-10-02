import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { ChevronDown, ChevronUp, Search, X } from "lucide-react";
import { Button } from "@/components/ui";

export interface ReaderSearchOverlayProps {
  content: string;
  onJumpToOffset: (charOffset: number) => void;
  onClose: () => void;
}

export function ReaderSearchOverlay({
  content,
  onJumpToOffset,
  onClose
}: ReaderSearchOverlayProps) {
  const [keyword, setKeyword] = useState("");
  const [currentIndex, setCurrentIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const matches = useMemo(() => {
    const q = keyword.trim();
    if (!q || !content) return [];
    const lowerQuery = q.toLowerCase();
    const lowerContent = content.toLowerCase();
    const offsets: number[] = [];
    let pos = 0;
    const maxMatches = 1000;
    while (pos < lowerContent.length && offsets.length < maxMatches) {
      const idx = lowerContent.indexOf(lowerQuery, pos);
      if (idx === -1) break;
      offsets.push(idx);
      pos = idx + Math.max(1, q.length);
    }
    return offsets;
  }, [keyword, content]);

  useEffect(() => {
    if (matches.length > 0) {
      setCurrentIndex(0);
      onJumpToOffset(matches[0]);
    } else {
      setCurrentIndex(0);
    }
  }, [matches, onJumpToOffset]);

  const handleNext = useCallback(() => {
    if (matches.length === 0) return;
    const next = (currentIndex + 1) % matches.length;
    setCurrentIndex(next);
    onJumpToOffset(matches[next]);
  }, [currentIndex, matches, onJumpToOffset]);

  const handlePrev = useCallback(() => {
    if (matches.length === 0) return;
    const prev = (currentIndex - 1 + matches.length) % matches.length;
    setCurrentIndex(prev);
    onJumpToOffset(matches[prev]);
  }, [currentIndex, matches, onJumpToOffset]);

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      if (e.shiftKey) {
        handlePrev();
      } else {
        handleNext();
      }
    } else if (e.key === "Escape") {
      e.preventDefault();
      onClose();
    }
  };

  return (
    <div
      role="search"
      aria-label="全文检索"
      className="absolute top-12 right-6 z-30 flex items-center gap-1.5 rounded-md border border-paper-line bg-paper-panel/95 p-1.5 [box-shadow:var(--shadow-2)] backdrop-blur-md"
    >
      <div className="relative flex items-center">
        <Search size={14} className="absolute left-2.5 text-paper-muted pointer-events-none" />
        <input
          ref={inputRef}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="搜索正文 (Enter 下一处)..."
          className="paper-input h-7 w-48 pl-7 pr-2 text-xs"
          aria-label="搜索正文关键词"
        />
      </div>

      <div className="flex items-center text-xs text-paper-muted min-w-[54px] justify-center tabular-nums">
        {!keyword.trim() ? (
          <span className="text-[11px] text-paper-muted">待输入</span>
        ) : matches.length === 0 ? (
          <span className="text-[11px] text-[color:var(--proof-mark)]">无结果</span>
        ) : (
          <span>
            {currentIndex + 1} / {matches.length}
            {matches.length >= 1000 ? "+" : ""}
          </span>
        )}
      </div>

      <Button
        variant="quiet"
        className="h-7 w-7 p-0"
        title="上一处 (Shift+Enter)"
        aria-label="上一处"
        disabled={matches.length === 0}
        onClick={handlePrev}
      >
        <ChevronUp size={14} />
      </Button>

      <Button
        variant="quiet"
        className="h-7 w-7 p-0"
        title="下一处 (Enter)"
        aria-label="下一处"
        disabled={matches.length === 0}
        onClick={handleNext}
      >
        <ChevronDown size={14} />
      </Button>

      <Button
        variant="quiet"
        className="h-7 w-7 p-0 text-paper-muted hover:text-paper-ink"
        title="关闭搜索 (Esc)"
        aria-label="关闭搜索"
        onClick={onClose}
      >
        <X size={14} />
      </Button>
    </div>
  );
}
