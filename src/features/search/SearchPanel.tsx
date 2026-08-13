import { useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, BookOpen, FolderOpen, Inbox as InboxIcon, Library as LibraryIcon, PenLine, Search, Tags, X } from "lucide-react";
import { useSearchActions } from "@/hooks/useSearchActions";
import { useSearchStore } from "@/stores/search-store";
import { SOURCE_ORDER } from "@/features/search/aggregate";
import type { UnifiedSearchEntry, UnifiedSearchFilter, UnifiedSearchSourceId } from "@/types/search";
import "./search.css";

const FILTERS: Array<{ value: UnifiedSearchFilter; label: string }> = [
  { value: "all", label: "全部" },
  { value: "project", label: "项目" },
  { value: "body", label: "正文" },
  { value: "card", label: "卡片" },
  { value: "inbox", label: "收件箱" },
  { value: "library", label: "资料" }
];

const GROUP_LABELS: Record<UnifiedSearchSourceId, string> = {
  project: "项目",
  chapter: "章节",
  scene: "场景正文",
  card: "卡片",
  inbox: "收件箱",
  library: "资料书库",
  inspiration: "旧灵感"
};

const GROUP_ICONS: Record<UnifiedSearchSourceId, typeof PenLine> = {
  project: LibraryIcon,
  chapter: FolderOpen,
  scene: PenLine,
  card: Tags,
  inbox: InboxIcon,
  library: BookOpen,
  inspiration: InboxIcon
};

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tagName = target.tagName.toLowerCase();
  return tagName === "input" || tagName === "textarea" || tagName === "select" || target.isContentEditable;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * 把技术源路径格式化成可读的来源标签。
 * sources.ts 已对资料书库源做 "书库 · 文件名" 映射，但 SearchPanel
 * 自身再加一层防御性 mask：如果上游意外透出 Windows 绝对路径或
 * AppLibrary 内部目录，就降级为 "书库文件 · 资料"，避免暴露内部存储结构。
 */
function formatSourceLabel(entry: UnifiedSearchEntry): string {
  const label = entry.originLabel ?? "";
  if (entry.source === "library") {
    if (/^[A-Za-z]:[\\/]/.test(label) || label.includes("AppLibrary")) {
      return "书库文件 · 资料";
    }
    return label;
  }
  return label;
}

function HighlightedText({ text, keyword }: { text: string; keyword: string }) {
  const trimmed = keyword.trim();
  if (!trimmed) return <>{text}</>;
  const parts = text.split(new RegExp(`(${escapeRegExp(trimmed)})`, "ig"));
  return (
    <>
      {parts.map((part, index) =>
        part.toLowerCase() === trimmed.toLowerCase() ? (
          <mark key={`${part}-${index}`} className="uni-hit">
            {part}
          </mark>
        ) : (
          <span key={`${part}-${index}`}>{part}</span>
        )
      )}
    </>
  );
}

export function SearchPanel() {
  const open = useSearchStore((state) => state.open);
  const keyword = useSearchStore((state) => state.keyword);
  const filter = useSearchStore((state) => state.filter);
  const projectContextId = useSearchStore((state) => state.projectContextId);
  const allProjects = useSearchStore((state) => state.allProjects);
  const results = useSearchStore((state) => state.results);
  const loading = useSearchStore((state) => state.loading);
  const error = useSearchStore((state) => state.error);
  const setOpen = useSearchStore((state) => state.setOpen);
  const setKeyword = useSearchStore((state) => state.setKeyword);
  const setFilter = useSearchStore((state) => state.setFilter);
  const setAllProjects = useSearchStore((state) => state.setAllProjects);
  const reset = useSearchStore((state) => state.reset);
  const { runSearch, openResult, cancelSearch } = useSearchActions();

  const inputRef = useRef<HTMLInputElement>(null);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  const [activeIndex, setActiveIndex] = useState(-1);

  const grouped = useMemo(() => {
    return SOURCE_ORDER.map((source) => ({ source, items: results.filter((entry) => entry.source === source) })).filter(
      (group) => group.items.length > 0
    );
  }, [results]);

  const flatResults = useMemo(() => grouped.flatMap((group) => group.items), [grouped]);

  useEffect(() => {
    const handleKeydown = (event: KeyboardEvent) => {
      if (event.isComposing || event.keyCode === 229) return;
      const isSearchShortcut =
        (event.ctrlKey || event.metaKey) && (event.key.toLowerCase() === "k" || (event.shiftKey && event.key.toLowerCase() === "f"));
      if (!isSearchShortcut) return;
      if (isEditableTarget(event.target)) return;
      event.preventDefault();
      restoreFocusRef.current = document.activeElement as HTMLElement | null;
      setOpen(true);
    };
    window.addEventListener("keydown", handleKeydown);
    return () => window.removeEventListener("keydown", handleKeydown);
  }, [setOpen]);

  useEffect(() => {
    if (!open) return;
    const frame = window.requestAnimationFrame(() => inputRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [open]);

  useEffect(() => {
    if (!open) return;
    runSearch(keyword);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, filter, allProjects, projectContextId, open]);

  // 组件卸载时清理防抖 timer 并取消进行中的搜索，避免陈旧请求继续扫描/写状态。
  useEffect(() => {
    return () => cancelSearch();
  }, [cancelSearch]);

  useEffect(() => {
    setActiveIndex(-1);
  }, [keyword, filter, results]);

  if (!open) return null;

  const closeSearch = () => {
    cancelSearch();
    reset();
    setOpen(false);
    const target = restoreFocusRef.current;
    restoreFocusRef.current = null;
    if (target && typeof target.focus === "function") target.focus();
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    const composing = event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229;
    if (event.key === "Escape" && !composing) {
      event.preventDefault();
      closeSearch();
      return;
    }
    if (flatResults.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((current) => (current + 1) % flatResults.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((current) => (current <= 0 ? flatResults.length - 1 : current - 1));
    } else if (event.key === "Enter") {
      event.preventDefault();
      const target = flatResults[Math.max(0, activeIndex)];
      if (target) void openResult(target);
    }
  };

  return (
    <div className="uni-search-overlay" onMouseDown={closeSearch}>
      <div
        className="uni-search-shell motion-dialog"
        role="dialog"
        aria-label="全局搜索"
        aria-modal="true"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="uni-search-head">
          <Search size={17} className="uni-search-head-icon" />
          <input
            ref={inputRef}
            className="uni-search-input"
            placeholder="搜索项目、正文、卡片、收件箱与资料（Ctrl+K）"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={handleKeyDown}
          />
          <button type="button" className="uni-search-close" onClick={closeSearch} aria-label="关闭搜索">
            <X size={16} />
          </button>
        </div>

        <div className="uni-search-bar">
          <div className="uni-search-filters" role="group" aria-label="搜索范围">
            {FILTERS.map(({ value, label }) => (
              <button
                key={value}
                type="button"
                className={filter === value ? "active" : ""}
                onClick={() => setFilter(value)}
              >
                {label}
              </button>
            ))}
          </div>
          {projectContextId && (
            <button
              type="button"
              className={`uni-search-global ${allProjects ? "active" : ""}`}
              onClick={() => setAllProjects(!allProjects)}
            >
              {allProjects ? "全部项目" : "当前项目"}
            </button>
          )}
        </div>

        <div className="uni-search-results">
          {loading && (
            <p className="uni-search-state" role="status">
              正在搜索…
            </p>
          )}
          {!loading && error && (
            <p className="uni-search-error" role="alert">
              <AlertTriangle size={13} /> {error}
            </p>
          )}
          {!loading && keyword.trim() && !error && flatResults.length === 0 && (
            <p className="uni-search-state">没有找到与「{keyword.trim()}」匹配的内容。</p>
          )}
          {!loading &&
            grouped.map(({ source, items }) => {
              const Icon = GROUP_ICONS[source];
              return (
                <section key={source} className="uni-search-group" aria-label={GROUP_LABELS[source]}>
                  <h3>
                    <Icon size={12} /> {GROUP_LABELS[source]}
                  </h3>
                  <ul>
                    {items.map((entry) => {
                      const flatIndex = flatResults.indexOf(entry);
                      return (
                        <li key={entry.id}>
                          <button
                            type="button"
                            className={flatIndex === activeIndex ? "active" : ""}
                            onClick={() => void openResult(entry)}
                          >
                            <span className="uni-result-title">
                              <HighlightedText text={entry.title} keyword={keyword} />
                              {entry.pendingMigration && <em className="uni-result-pending">待迁移</em>}
                            </span>
                            {entry.snippet && (
                              <span className="uni-result-snippet">
                                <HighlightedText text={entry.snippet} keyword={keyword} />
                              </span>
                            )}
                            <span className="uni-result-origin text-paper-ink/70">{formatSourceLabel(entry)}</span>
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}
        </div>
      </div>
    </div>
  );
}
