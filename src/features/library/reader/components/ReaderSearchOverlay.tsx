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
  // BD-183：浮层打开时把焦点抢进输入框，关闭（Esc/关闭钮/Ctrl+F 再按一次）后节点
  // 直接消失，焦点掉回 body——键盘用户要从文档开头重新 Tab（同 BD-176 ReplacePanel
  // 的出口契约）。挂载时先记住是谁开的，卸载时还给它；开手已不在文档里就不抢焦点。
  const restoreFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const opener = document.activeElement;
    restoreFocusRef.current =
      opener instanceof HTMLElement && opener !== document.body ? opener : null;
    inputRef.current?.focus();
    return () => {
      const target = restoreFocusRef.current;
      restoreFocusRef.current = null;
      if (target?.isConnected) target.focus({ preventScroll: true });
    };
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
        {/* 批次 BD-71（28px 桶·搜索框图标让位槽）：pl-7→pl-8（28→32）。
            28 是 24/32 正中平票，就近不裁决 ⇒ 数「前导图标 + 输入框」这一族在档票：
            SettingsSearch `h-8 w-full pl-8 text-xs`（图标 left-2.5 / size 13）、
            TocList `pl-8`（同款图标）、LibraryPage `pl-9`（图标 left-3 / size 16）——
            在档只有 32 与 36 两档，24 侧零成员。
            真宿主实拍（/tmp/bd71-tsx.json，Ctrl+F 真浮层）：图标右缘 999、正文起笔
            1003 ⇒ 缝 4px；改 24 → 缝 0（字直接贴图标右缘），改 32 → 缝 8，
            与上面三条同族的 8–9px 缝对齐。框盒 192×28 与 scrollWidth 190 在
            24/28/32 三档全不动；占位文字实测 144.65px，32 档仍有 152px 可用，不截断。 */}
        <input
          ref={inputRef}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="搜索正文 (Enter 下一处)..."
          className="paper-input h-7 w-48 pl-8 pr-2 text-xs"
          aria-label="搜索正文关键词"
        />
      </div>

      {/* BD-180：命中位次只画不给——焦点始终留在输入框里，按 Enter 换一处、改一个字
          改计数，读屏器都收不到通知（BD-175 的漏网点）。视觉串原样保留并对读屏器
          隐藏，另挂一条常驻 role=status 的中文播报；sr-only 不占布局宽度。 */}
      <div className="flex items-center text-xs text-paper-muted min-w-[54px] justify-center tabular-nums">
        <span aria-hidden="true">
          {!keyword.trim() ? (
            <span className="text-[11px] text-paper-muted">待输入</span>
          ) : matches.length === 0 ? (
            <span className="text-[11px] text-[color:var(--proof-mark)]">无结果</span>
          ) : (
            <>
              {currentIndex + 1} / {matches.length}
              {matches.length >= 1000 ? "+" : ""}
            </>
          )}
        </span>
        <span className="sr-only" role="status">
          {!keyword.trim()
            ? ""
            : matches.length === 0
              ? "没有匹配结果"
              : `第 ${currentIndex + 1} 处，共 ${matches.length}${matches.length >= 1000 ? " 处以上" : " 处"}`}
        </span>
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
