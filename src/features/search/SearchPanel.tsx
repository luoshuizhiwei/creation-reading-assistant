import { useEffect, useMemo, useRef, useState } from "react";
import { Search, X } from "lucide-react";
import { Button } from "@/components/ui";
import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";
import type { SearchResult, SearchResultType } from "@/types/search";

const labels: Record<SearchResultType, string> = {
  book: "书籍",
  inspiration: "灵感箱"
};

function groupResults(results: SearchResult[]): Array<[SearchResultType, SearchResult[]]> {
  const order: SearchResultType[] = ["inspiration", "book"];
  const groups: Array<[SearchResultType, SearchResult[]]> = order.map((type) => [type, results.filter((result) => result.type === type)]);
  return groups.filter(([, items]) => items.length > 0);
}

function basename(sourcePath: string): string {
  return sourcePath.split(/[\\/]/).filter(Boolean).pop() ?? sourcePath;
}

function compactPath(sourcePath: string): string {
  const normalized = sourcePath.replaceAll("\\", "/");
  const parts = normalized.split("/").filter(Boolean);
  if (parts.length <= 2) return normalized;
  return `${parts.at(-2)}/${parts.at(-1)}`;
}

function formatSourceLabel(result: SearchResult): string | undefined {
  if (!result.sourcePath) return undefined;
  const normalized = result.sourcePath.replaceAll("\\", "/");
  if (/\/AppLibrary\//i.test(normalized)) {
    return `书库文件 · ${basename(result.sourcePath)}`;
  }
  if (result.type === "book") {
    return `书库来源 · ${compactPath(result.sourcePath)}`;
  }
  if (result.type === "inspiration") {
    return "全局灵感箱";
  }
  return `本地来源 · ${compactPath(result.sourcePath)}`;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function HighlightedText({ text, keyword, className = "" }: { text: string; keyword: string; className?: string }) {
  const trimmed = keyword.trim();
  if (!trimmed) return <span className={className}>{text}</span>;
  const parts = text.split(new RegExp(`(${escapeRegExp(trimmed)})`, "ig"));
  return (
    <span className={className}>
      {parts.map((part, index) =>
        part.toLowerCase() === trimmed.toLowerCase() ? (
          <mark key={`${part}-${index}`} className="search-hit">
            {part}
          </mark>
        ) : (
          <span key={`${part}-${index}`}>{part}</span>
        )
      )}
    </span>
  );
}

export function SearchPanel() {
  const [draft, setDraft] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const open = useSearchStore((state) => state.open);
  const results = useSearchStore((state) => state.results);
  const loading = useSearchStore((state) => state.loading);
  const setOpen = useSearchStore((state) => state.setOpen);
  const reset = useSearchStore((state) => state.reset);
  const { runSearch, openResult } = useSearchActions();
  const grouped = useMemo(() => groupResults(results), [results]);

  useEffect(() => {
    const handleKeydown = (event: KeyboardEvent) => {
      const isSearchShortcut = (event.ctrlKey || event.metaKey) && (event.key.toLowerCase() === "k" || (event.shiftKey && event.key.toLowerCase() === "f"));
      if (!isSearchShortcut) return;
      event.preventDefault();
      setOpen(true);
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [setOpen]);

  useEffect(() => {
    if (!open) return;
    runSearch(draft);
  }, [draft, open, runSearch]);

  useEffect(() => {
    if (!open) return;
    const frame = window.requestAnimationFrame(() => inputRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [open]);

  if (!open) return null;

  const closeSearch = () => {
    setDraft("");
    reset();
  };

  return (
    <div className="absolute inset-0 z-40 bg-paper-ink/14 px-6 pt-16 backdrop-blur-md" onMouseDown={closeSearch}>
      <div
        role="dialog"
        aria-label="全局搜索"
        aria-modal="true"
        className="motion-dialog mx-auto max-w-3xl overflow-hidden rounded-2xl border border-paper-line bg-paper-panel/98 shadow-paper"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="flex h-12 items-center gap-2 px-4">
          <Search size={16} className="text-paper-muted" />
          <input
            ref={inputRef}
            className="min-w-0 flex-1 bg-transparent text-sm text-paper-ink outline-none placeholder:text-paper-muted/60"
            placeholder="全局搜索灵感、书库、EPUB 正文（Ctrl+K）"
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
            }}
            onKeyDown={(event) => {
              if (event.key === "Escape") closeSearch();
            }}
          />
          <button className="rounded p-1 text-paper-muted hover:bg-paper-soft hover:text-paper-ink" title="关闭搜索" onClick={closeSearch}>
            <X size={15} />
          </button>
        </div>
        {draft.trim() && (
          <div className="max-h-[520px] overflow-auto border-t border-paper-line p-2">
            {loading ? (
              <div className="motion-notice rounded-xl border border-paper-line bg-paper-soft/45 px-4 py-5 text-center text-sm text-paper-muted">正在搜索...</div>
            ) : grouped.length === 0 ? (
              <div className="motion-notice rounded-xl border border-dashed border-paper-line bg-paper-soft/30 px-4 py-6 text-center text-sm text-paper-muted">
                没有找到匹配内容
              </div>
            ) : (
              <div className="grid gap-3">
                {grouped.map(([type, items]) => (
                  <section key={type}>
                    <div className="px-2 py-1 text-xs font-semibold text-paper-muted">{labels[type]}</div>
                    <div className="grid gap-1">
                      {items.map((result) => (
                        <button
                          key={result.id}
                          className="group grid rounded-xl border border-transparent px-3 py-2 text-left transition duration-150 hover:-translate-y-0.5 hover:border-copper/25 hover:bg-paper-soft/70 hover:shadow-lift"
                          onClick={() => openResult(result)}
                        >
                          <HighlightedText text={result.title} keyword={draft} className="truncate text-sm font-medium text-paper-ink group-hover:text-copper" />
                          <HighlightedText text={result.snippet} keyword={draft} className="mt-0.5 line-clamp-2 text-xs leading-5 text-paper-ink/70" />
                          {formatSourceLabel(result) && (
                            <HighlightedText
                              text={formatSourceLabel(result) ?? ""}
                              keyword={draft}
                              className="mt-0.5 truncate text-[11px] text-paper-ink/70"
                            />
                          )}
                        </button>
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
