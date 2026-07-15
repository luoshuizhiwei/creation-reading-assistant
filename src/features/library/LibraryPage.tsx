import { useEffect, useMemo, useRef, useState } from "react";
import { BarChart3, BookOpen, FileText, Filter, Import, Search, Settings, Trash2, X } from "lucide-react";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";
import { useUIStore } from "@/stores/ui-store";

type SortMode = "recent" | "title" | "progress";
type FormatFilter = "all" | "txt" | "epub" | "md";

function formatDate(value?: string): string {
  if (!value) return "未阅读";
  return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function formatDuration(ms = 0): string {
  const minutes = Math.floor(ms / 60_000);
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (hours <= 0) return `${restMinutes} 分钟`;
  return `${hours} 小时 ${restMinutes} 分钟`;
}

export function LibraryPage() {
  const books = useLibraryStore((state) => state.books);
  const progress = useLibraryStore((state) => state.progress);
  const loading = useLibraryStore((state) => state.loading);
  const { refreshBooks, importBooks, importEpubBooks, removeBookById, openReader } = useLibraryActions();
  const setScreen = useAppStore((state) => state.setScreen);
  const confirmAction = useUIStore((state) => state.confirmAction);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortMode, setSortMode] = useState<SortMode>("recent");
  const [formatFilter, setFormatFilter] = useState<FormatFilter>("all");
  const [showSortDropdown, setShowSortDropdown] = useState(false);
  const sortDropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!showSortDropdown) return;
    const handleMouseDown = (e: MouseEvent) => {
      if (sortDropdownRef.current && !sortDropdownRef.current.contains(e.target as Node)) {
        setShowSortDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleMouseDown);
    return () => document.removeEventListener("mousedown", handleMouseDown);
  }, [showSortDropdown]);

  useEffect(() => {
    void refreshBooks();
  }, [refreshBooks]);

  const filteredAndSorted = useMemo(() => {
    let result = [...books];

    // Format filter
    if (formatFilter !== "all") {
      result = result.filter((book) => book.format === formatFilter);
    }

    // Search filter
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter(
        (b) =>
          b.title.toLowerCase().includes(q) ||
          (b.author ?? "").toLowerCase().includes(q) ||
          (b.importLabel ?? "").toLowerCase().includes(q)
      );
    }

    // Sort
    result.sort((a, b) => {
      if (sortMode === "title") return a.title.localeCompare(b.title, "zh-Hans-CN");
      if (sortMode === "progress") {
        const pa = progress[a.id]?.progressPercent ?? 0;
        const pb = progress[b.id]?.progressPercent ?? 0;
        return pb - pa;
      }
      // recent: by lastReadAt desc
      const la = progress[a.id]?.lastReadAt ?? a.updatedAt;
      const lb = progress[b.id]?.lastReadAt ?? b.updatedAt;
      return lb.localeCompare(la);
    });

    return result;
  }, [books, progress, searchQuery, sortMode, formatFilter]);

  const hasActiveFilters = searchQuery.trim() !== "" || formatFilter !== "all";

  const handleRemoveBook = async (bookId: string, bookTitle: string) => {
    const confirmed = await confirmAction({
      title: `从书库移除「${bookTitle}」？`,
      body: `书籍文件不会被删除，仅移除该书的记录及阅读进度。\n\n${bookTitle}`,
      cancelLabel: "取消",
      confirmLabel: "移除",
      tone: "danger"
    });
    if (confirmed) {
      await removeBookById(bookId, { skipConfirm: true });
    }
  };

  return (
    <div className="desktop-page-scroll paper-shell">
      <div className="desktop-page-stack">
        <section className="desktop-page-hero motion-panel">
          <div>
            <div className="desktop-card-label">Local documents</div>
            <h2>本地书库工作区</h2>
            <p>按格式、进度和最近阅读筛选本地 TXT / Markdown / EPUB，点击整行即可进入深度阅读。</p>
          </div>
          <div className="desktop-page-actions">
            <Button variant="secondary" onClick={importBooks}>
              <Import size={16} />
              导入 TXT/Markdown
            </Button>
            <Button variant="secondary" onClick={importEpubBooks}>
              <Import size={16} />
              导入 EPUB
            </Button>
            <Button variant="quiet" onClick={() => setScreen("stats")}>
              <BarChart3 size={16} />
              统计
            </Button>
            <Button variant="quiet" onClick={() => setScreen("settings")}>
              <Settings size={16} />
              设置
            </Button>
          </div>
        </section>

        {loading ? (
          <div className="desktop-panel-card desktop-empty-wrap">
            <div className="text-sm text-paper-muted">正在加载书籍...</div>
          </div>
        ) : books.length === 0 ? (
          <div className="desktop-panel-card desktop-empty-wrap">
            <EmptyState title="书库还是空的" body="导入 TXT、Markdown 或 EPUB 后，可以从这里打开阅读器并保存阅读进度。" />
          </div>
        ) : (
          <div className="grid gap-4">
            {/* Top bar: heading + controls */}
            <div className="desktop-panel-card flex flex-col gap-3 p-4 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h1 className="paper-title text-2xl font-semibold">本地文档</h1>
                <p className="mt-1 text-sm text-paper-muted">共 {filteredAndSorted.length} / {books.length} 本，进度和有效阅读时长保存在本地。</p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {/* Sort dropdown */}
                <div className="relative" ref={sortDropdownRef}>
                  <button
                    className={`inline-flex items-center gap-1 rounded-md border px-3 py-1.5 text-xs font-medium transition hover:border-copper ${
                      showSortDropdown ? "border-copper bg-copper/5" : "border-paper-line bg-paper-panel"
                    }`}
                    onClick={() => setShowSortDropdown(!showSortDropdown)}
                  >
                    <Filter size={12} />
                    {sortMode === "recent" ? "最近阅读" : sortMode === "title" ? "书名" : "阅读进度"}
                  </button>
                  {showSortDropdown && (
                    <div className="absolute right-0 top-full mt-1 z-20 min-w-[160px] rounded-lg border border-paper-line bg-paper-panel shadow-paper p-1">
                      {([["recent", "最近阅读"], ["title", "书名"], ["progress", "阅读进度"]] as [SortMode, string][]).map(([mode, label]) => (
                        <button
                          key={mode}
                          className={`w-full rounded-md px-3 py-1.5 text-left text-sm transition ${
                            sortMode === mode ? "bg-copper text-white" : "hover:bg-paper-soft/50"
                          }`}
                          onClick={() => { setSortMode(mode); setShowSortDropdown(false); }}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                {/* Format filter chips */}
                <div className="relative inline-flex items-center gap-1 rounded-lg border border-paper-line bg-paper-panel p-0.5">
                  {(["all", "txt", "epub", "md"] as FormatFilter[]).map((f) => (
                    <button
                      key={f}
                      className={`rounded-md px-2 py-1 text-[11px] font-medium transition ${
                        formatFilter === f
                          ? "bg-copper text-white shadow-lift"
                          : "text-paper-muted hover:text-paper-ink"
                      }`}
                      onClick={() => setFormatFilter(f)}
                    >
                      {f === "all" ? "全部" : f.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Search bar */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-paper-muted" size={16} />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="按书名、作者或导入标签搜索..."
                className="paper-input w-full pl-9 pr-8 text-sm"
              />
              {searchQuery && (
                <button
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-0.5 text-paper-muted hover:text-paper-ink"
                  onClick={() => setSearchQuery("")}
                >
                  <X size={14} />
                </button>
              )}
            </div>

            {/* Active filter chips */}
            {hasActiveFilters && (
              <div className="flex flex-wrap items-center gap-2 text-xs text-paper-muted">
                <span>筛选条件：</span>
                {searchQuery && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-copper/30 bg-copper/10 px-2 py-0.5 text-copper">
                    "{searchQuery}"
                    <button onClick={() => setSearchQuery("")}><X size={12} /></button>
                  </span>
                )}
                {formatFilter !== "all" && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-moss/30 bg-moss/10 px-2 py-0.5 text-moss">
                    {formatFilter.toUpperCase()}
                    <button onClick={() => setFormatFilter("all")}><X size={12} /></button>
                  </span>
                )}
                <button
                  className="ml-1 rounded-md px-2 py-0.5 text-paper-muted hover:bg-paper-soft/50"
                  onClick={() => { setSearchQuery(""); setFormatFilter("all"); }}
                >
                  清除所有
                </button>
              </div>
            )}

            {/* Book table */}
            <div className="desktop-panel-card desktop-library-panel motion-panel overflow-hidden">
              <div className="desktop-library-grid-head">
                <div>格式</div>
                <div>书籍</div>
                <div>本地路径</div>
                <div>阅读进度</div>
                <div>最近阅读</div>
                <div className="text-right">操作</div>
              </div>
              {filteredAndSorted.length === 0 ? (
                <div className="py-10 text-center text-sm text-paper-muted">
                  {hasActiveFilters ? "没有匹配搜索结果的书。试试调整筛选条件。" : "书库暂无数据。"}
                </div>
              ) : (
                filteredAndSorted.map((book) => {
                  const itemProgress = progress[book.id];
                  const percent = Math.round((itemProgress?.progressPercent ?? 0) * 100);
                  const sourcePath = book.originalPath ?? book.filePath;
                  return (
                    <div
                      key={book.id}
                      role="button"
                      tabIndex={0}
                      className="desktop-library-row group"
                      onClick={() => openReader(book.id)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openReader(book.id);
                        }
                      }}
                    >
                      <div>
                        <span className="moss-chip uppercase">{book.format}</span>
                      </div>
                      <div className="min-w-0 text-left">
                        <div className="flex items-center gap-3">
                          <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg border border-paper-line bg-paper-soft/70 text-copper">
                            <FileText size={18} />
                          </div>
                          <div className="min-w-0">
                            <div className="flex min-w-0 items-center gap-2">
                              <h2 className="paper-title truncate text-base font-semibold group-hover:text-copper">{book.title}</h2>
                              {book.importLabel && <span className="paper-chip shrink-0">{book.importLabel}</span>}
                            </div>
                            <div className="mt-1 truncate text-xs text-paper-muted">{book.author ? `作者：${book.author}` : "未知作者"}</div>
                          </div>
                        </div>
                      </div>
                      <div className="library-path" title={sourcePath}>
                        {sourcePath}
                      </div>
                      <div className="grid gap-1">
                        <div className="flex items-center justify-between text-xs text-paper-muted">
                          <span>{percent}%</span>
                          <span>{formatDuration(itemProgress?.totalReadingTimeMs)}</span>
                        </div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-paper-soft" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
                          <div className="h-full rounded-full bg-moss" style={{ width: `${percent}%` }} />
                        </div>
                      </div>
                      <div className="font-mono text-xs text-paper-muted">{formatDate(itemProgress?.lastReadAt)}</div>
                      <div className="flex justify-end gap-1">
                        <Button
                          className="px-2"
                          variant="secondary"
                          aria-label="打开阅读"
                          onClick={(event) => {
                            event.stopPropagation();
                            openReader(book.id);
                          }}
                        >
                          <BookOpen size={15} />
                        </Button>
                        <button
                          className="grid h-9 w-9 place-items-center rounded-md text-paper-muted transition hover:bg-red-50 hover:text-red-700"
                          title="移除"
                          aria-label="移除书籍"
                          onClick={(event) => {
                            event.stopPropagation();
                            handleRemoveBook(book.id, book.title);
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
