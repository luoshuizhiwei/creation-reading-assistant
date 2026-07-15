import { Fragment, useEffect, useMemo, useState, type ReactNode } from "react";
import { Search, X, Clock, Trash2, BookOpen, Sparkles, NotebookPen, Highlighter } from "lucide-react";
import type { MobileBook, MobileHighlight, MobileSnapshot } from "../../types/mobile";

/** 搜索结果类型 */
type SearchResultType = "book" | "inspiration" | "note" | "highlight";

/** 搜索历史条数上限 */
const SEARCH_HISTORY_MAX = 12;
const SEARCH_HISTORY_KEY = "creation-reading-assistant-mobile-search-history";

export interface GlobalSearchResult {
  type: SearchResultType;
  id: string;
  title: string;
  snippet: string;
  meta?: string;
  /** 命中的关键词上下文 */
  matchContext?: string;
  book?: MobileBook;
  raw: unknown;
}

function loadSearchHistory(): string[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string") : [];
  } catch {
    return [];
  }
}

function saveSearchHistory(history: string[]): void {
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(history.slice(0, SEARCH_HISTORY_MAX)));
  } catch {
    // 忽略
  }
}

/** 高亮搜索关键词 */
function renderHighlightedText(text: string, keyword: string): ReactNode {
  const trimmed = keyword.trim();
  if (!trimmed) return text;
  const escaped = trimmed.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const parts = text.split(new RegExp(`(${escaped})`, "gi"));
  return parts.map((part, index) => (
    part.toLocaleLowerCase() === trimmed.toLocaleLowerCase()
      ? <mark key={`${index}-${part}`}>{part}</mark>
      : <Fragment key={`${index}-${part}`}>{part}</Fragment>
  ));
}

/** 提取关键词上下文（前后各 20 字符） */
function extractContext(text: string, keyword: string): string {
  if (!keyword.trim()) return "";
  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const index = lowerText.indexOf(lowerKeyword);
  if (index < 0) return "";
  const start = Math.max(0, index - 20);
  const end = Math.min(text.length, index + keyword.length + 20);
  const prefix = start > 0 ? "…" : "";
  const suffix = end < text.length ? "…" : "";
  return prefix + text.slice(start, end) + suffix;
}

export function GlobalSearchOverlay({
  snapshot,
  onClose,
  onOpenBook,
  onOpenInspiration,
  onOpenNote,
  onOpenHighlight
}: {
  snapshot: MobileSnapshot;
  onClose: () => void;
  onOpenBook: (book: MobileBook) => void;
  onOpenInspiration: (inspirationId: string) => void;
  onOpenNote: (noteId: string) => void;
  onOpenHighlight: (book: MobileBook, highlight: MobileHighlight) => void;
}) {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [searchHistory, setSearchHistory] = useState<string[]>(() => loadSearchHistory());

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    saveSearchHistory(searchHistory);
  }, [searchHistory]);

  // 执行搜索
  const results = useMemo<GlobalSearchResult[]>(() => {
    const keyword = debouncedQuery.trim().toLowerCase();
    if (!keyword) return [];
    const allResults: GlobalSearchResult[] = [];

    // 搜索书籍
    for (const book of snapshot.books) {
      const haystack = `${book.title} ${book.author ?? ""} ${book.tagNames?.join(" ") ?? ""} ${book.originalFileName ?? ""}`.toLowerCase();
      if (haystack.includes(keyword)) {
        const titleMatch = book.title.toLowerCase().includes(keyword);
        allResults.push({
          type: "book",
          id: book.id,
          title: book.title,
          snippet: book.author ? `${book.author} · ${book.format.toUpperCase()}` : book.format.toUpperCase(),
          meta: "书籍",
          matchContext: titleMatch ? undefined : extractContext(`${book.title} ${book.author ?? ""}`, debouncedQuery),
          book,
          raw: book
        });
      }
    }

    // 搜索灵感
    for (const item of snapshot.inspirations) {
      const haystack = `${item.title} ${item.body} ${item.tags.join(" ")} ${item.source?.bookTitle ?? ""} ${item.source?.excerpt ?? ""}`.toLowerCase();
      if (haystack.includes(keyword)) {
        const titleMatch = item.title.toLowerCase().includes(keyword);
        allResults.push({
          type: "inspiration",
          id: item.id,
          title: item.title,
          snippet: item.body || item.source?.excerpt || "还没有正文",
          meta: `灵感 · ${item.tags.slice(0, 2).join("#")}`,
          matchContext: titleMatch ? extractContext(item.body || item.source?.excerpt || "", debouncedQuery) : undefined,
          raw: item
        });
      }
    }

    // 搜索笔记
    for (const note of snapshot.notes) {
      if (note.deletedAt) continue;
      const haystack = `${note.title} ${note.body} ${note.excerpt ?? ""}`.toLowerCase();
      if (haystack.includes(keyword)) {
        const titleMatch = note.title.toLowerCase().includes(keyword);
        allResults.push({
          type: "note",
          id: note.id,
          title: note.title,
          snippet: note.body || note.excerpt || "没有正文",
          meta: `笔记${note.chapterTitle ? ` · ${note.chapterTitle}` : ""}`,
          matchContext: titleMatch ? extractContext(note.body || note.excerpt || "", debouncedQuery) : undefined,
          raw: note
        });
      }
    }

    // 搜索高亮
    for (const highlight of snapshot.highlights) {
      const haystack = `${highlight.text} ${highlight.note ?? ""}`.toLowerCase();
      if (haystack.includes(keyword)) {
        const book = snapshot.books.find((b) => b.id === highlight.bookId);
        allResults.push({
          type: "highlight",
          id: highlight.id,
          title: highlight.text.slice(0, 40) + (highlight.text.length > 40 ? "…" : ""),
          snippet: highlight.note || "无备注",
          meta: `高亮${book ? ` · 《${book.title}》` : ""}`,
          matchContext: extractContext(highlight.text, debouncedQuery),
          book,
          raw: highlight
        });
      }
    }

    return allResults;
  }, [snapshot, debouncedQuery]);

  // 按类型分组
  const groupedResults = useMemo(() => {
    const groups = new Map<SearchResultType, GlobalSearchResult[]>();
    for (const result of results) {
      if (!groups.has(result.type)) groups.set(result.type, []);
      groups.get(result.type)!.push(result);
    }
    return groups;
  }, [results]);

  const saveToHistory = (keyword: string) => {
    const trimmed = keyword.trim();
    if (!trimmed) return;
    setSearchHistory((prev) => [trimmed, ...prev.filter((item) => item !== trimmed)].slice(0, SEARCH_HISTORY_MAX));
  };

  const handleSearch = (keyword: string) => {
    setQuery(keyword);
    if (keyword.trim()) saveToHistory(keyword);
  };

  const handleResultClick = (result: GlobalSearchResult) => {
    saveToHistory(debouncedQuery);
    if (result.type === "book" && result.book) {
      onOpenBook(result.book);
    } else if (result.type === "inspiration") {
      onOpenInspiration(result.id);
    } else if (result.type === "note") {
      onOpenNote(result.id);
    } else if (result.type === "highlight" && result.book) {
      onOpenHighlight(result.book, result.raw as MobileHighlight);
    }
    onClose();
  };

  const clearHistory = () => {
    setSearchHistory([]);
  };

  const typeLabel: Record<SearchResultType, string> = {
    book: "书籍",
    inspiration: "灵感",
    note: "笔记",
    highlight: "高亮"
  };

  const typeIcon: Record<SearchResultType, typeof BookOpen> = {
    book: BookOpen,
    inspiration: Sparkles,
    note: NotebookPen,
    highlight: Highlighter
  };

  return (
    <div className="global-search-overlay">
      <div className="global-search-backdrop" onClick={onClose} />
      <div className="global-search-panel">
        <header className="global-search-header">
          <label className="search-pill global-search-input-pill">
            <Search size={19} strokeWidth={2.2} />
            <input
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索书籍、灵感、笔记、摘录"
              onKeyDown={(event) => {
                if (event.key === "Enter" && query.trim()) saveToHistory(query);
              }}
            />
            {query && (
              <button className="global-search-clear-btn" onClick={() => setQuery("")} aria-label="清空">
                <X size={16} />
              </button>
            )}
          </label>
          <button className="ghost-button" onClick={onClose}>取消</button>
        </header>

        {!debouncedQuery.trim() ? (
          <div className="global-search-history">
            {searchHistory.length ? (
              <>
                <div className="global-search-history-header">
                  <h3>
                    <Clock size={14} />
                    搜索历史
                  </h3>
                  <button className="ghost-button" onClick={clearHistory} aria-label="清空历史">
                    <Trash2 size={14} />
                    清空
                  </button>
                </div>
                <div className="filter-chip-rail global-search-history-list">
                  {searchHistory.map((item) => (
                    <button key={item} className="filter-chip" onClick={() => handleSearch(item)}>
                      {item}
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <div className="global-search-empty">
                <Search size={32} strokeWidth={1.6} />
                <strong>搜索你的本地内容</strong>
                <p>输入书名、作者、标签、灵感内容、笔记或摘录，结果会按类型分组。</p>
              </div>
            )}
          </div>
        ) : results.length === 0 ? (
          <div className="global-search-empty">
            <Search size={32} strokeWidth={1.6} />
            <strong>没有找到「{debouncedQuery}」</strong>
            <p>换个关键词试试，或检查是否已导入相关书籍和灵感。</p>
          </div>
        ) : (
          <div className="global-search-results">
            <p className="global-search-result-count">找到 {results.length} 条结果</p>
            {Array.from(groupedResults.entries()).map(([type, items]) => {
              const Icon = typeIcon[type];
              return (
                <section key={type} className="global-search-group">
                  <h3 className="global-search-group-title">
                    <Icon size={14} />
                    {typeLabel[type]}
                    <span className="global-search-group-count">{items.length}</span>
                  </h3>
                  {items.map((result) => (
                    <button
                      key={`${result.type}-${result.id}`}
                      className="global-search-item"
                      onClick={() => handleResultClick(result)}
                    >
                      <div className="global-search-item-main">
                        <span className="global-search-item-title">
                          {renderHighlightedText(result.title, debouncedQuery)}
                        </span>
                        {result.matchContext && (
                          <span className="global-search-item-context">
                            {renderHighlightedText(result.matchContext, debouncedQuery)}
                          </span>
                        )}
                        <span className="global-search-item-meta">{result.meta}</span>
                      </div>
                    </button>
                  ))}
                </section>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
