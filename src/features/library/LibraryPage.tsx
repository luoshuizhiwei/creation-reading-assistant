import { useEffect, useMemo, useRef, useState } from "react";
import { BookOpen, Filter, Import, Search, Trash2, X } from "lucide-react";
import { Button, EmptyState, Tabs } from "@/components/ui";
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
  const sortTriggerRef = useRef<HTMLButtonElement>(null);

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
        <div className="library-toolbar">
          <div className="desktop-page-actions">
            {/* 批次 BD-26：这一排原本是接管族 `.desktop-page-actions button` 的宿主。删掉的
                族里有一条 `button:first-child:not([data-variant="quiet"]):not([data-variant="ghost"])`
                ——它不只改外观，还在**替组件决定 variant**：行首那颗被按成实心，其余按成
                面板底。所以族一删，「谁是主操作」这件事必须由 TSX 自己说清楚，否则就是
                可感知的降级（实测：族删除后本排首钮从实心变成描边）。这里把行首的
                「导入 TXT/Markdown」显式定为 primary（规格 §2.1 一个视图主操作 ≤ 1），
                其余两颗保持 secondary / quiet 不变。 */}
            <Button onClick={importBooks}>
              <Import size={16} />
              导入 TXT/Markdown
            </Button>
            <Button variant="secondary" onClick={importEpubBooks}>
              <Import size={16} />
              导入 EPUB
            </Button>
            <Button variant="quiet" onClick={() => setScreen("stats")}>
              <BookOpen size={16} />
              阅读统计
            </Button>
          </div>
        </div>

        {loading ? (
          <div className="desktop-panel-card desktop-empty-wrap">
            <div className="text-sm text-paper-muted">正在加载书籍...</div>
          </div>
        ) : books.length === 0 ? (
          <div className="desktop-panel-card desktop-empty-wrap">
            <EmptyState title="书库还是空的" body="导入 TXT、Markdown 或 EPUB 后，可以从这里打开资料、浏览目录并摘录选文。" />
          </div>
        ) : (
          <div className="grid gap-4">
            {/* Top bar: heading + controls */}
            <div className="desktop-panel-card flex flex-col gap-3 p-4 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h1 className="paper-title text-2xl font-semibold">书库</h1>
                <p className="mt-1 text-sm text-paper-muted">共 {filteredAndSorted.length} / {books.length} 本，进度和有效阅读时长保存在本地。</p>
                {/* BD-175：筛选后本数变化只写在屏幕上的这句里，读屏器听不到。常驻不可见区域播报。 */}
                <span className="sr-only" role="status">
                  {hasActiveFilters
                    ? filteredAndSorted.length > 0
                      ? `筛选后 ${filteredAndSorted.length} 本`
                      : "没有匹配搜索结果的书"
                    : ""}
                </span>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {/* Sort dropdown */}
                <div
                  className="relative"
                  ref={sortDropdownRef}
                  // BD-182：排序下拉只有外面点一下才关。键盘用户按 Esc 毫无反应，
                  // 只能 Tab 一圈再回来（BD-176 的浮层出口漏网点）。Esc 关菜单并把
                  // 焦点交还触发钮——菜单项被卸载，否则焦点会掉回 body。
                  onKeyDown={(event) => {
                    if (!showSortDropdown || event.key !== "Escape") return;
                    if (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229) return;
                    event.preventDefault();
                    event.stopPropagation();
                    setShowSortDropdown(false);
                    sortTriggerRef.current?.focus();
                  }}
                >
                  <button
                    ref={sortTriggerRef}
                    className={`inline-flex items-center gap-1 rounded-md border px-3 py-1.5 text-xs font-medium transition hover:border-copper ${
                      showSortDropdown ? "border-copper bg-copper/5" : "border-paper-line bg-paper-panel"
                    }`}
                    aria-expanded={showSortDropdown}
                    onClick={() => setShowSortDropdown(!showSortDropdown)}
                  >
                    <Filter size={12} />
                    {sortMode === "recent" ? "最近阅读" : sortMode === "title" ? "书名" : "阅读进度"}
                  </button>
                  {showSortDropdown && (
                    <div className="absolute right-0 top-full mt-1 z-20 min-w-[160px] rounded-md border border-paper-line bg-paper-panel [box-shadow:var(--shadow-2)] p-1">
                      {([["recent", "最近阅读"], ["title", "书名"], ["progress", "阅读进度"]] as [SortMode, string][]).map(([mode, label]) => (
                        <button
                          key={mode}
                          className={`w-full rounded-md px-3 py-1.5 text-left text-sm transition ${
                            sortMode === mode ? "bg-copper text-[color:var(--fg-on-solid)]" : "hover:bg-paper-soft/50"
                          }`}
                          aria-pressed={sortMode === mode}
                          onClick={() => { setSortMode(mode); setShowSortDropdown(false); }}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
                {/* Format filter chips */}
                <Tabs<FormatFilter>
                  variant="pill"
                  value={formatFilter}
                  onChange={setFormatFilter}
                  items={[
                    { id: "all", label: "全部" },
                    { id: "txt", label: "TXT" },
                    { id: "epub", label: "EPUB" },
                    { id: "md", label: "Markdown" }
                  ]}
                  ariaLabel="书籍格式筛选"
                />
              </div>
            </div>

            {/* Search bar */}
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-paper-muted" size={16} />
              {/* 批次 BD-77（36px 桶·搜索框前导图标让位槽）：pl-9→pl-8（36→32）。
                  36 是 32/40 的正中平票，就近不裁决 ⇒ 数「前导图标 + 输入框」这一族
                  在档票：SettingsSearch `pl-8`（图标 left-2.5 / size 13）、
                  ReaderSearchOverlay `pl-8`（同款，BD-71 刚收）、TocList `pl-8`——
                  32 三票，40 全仓零成员。真宿主实拍（/tmp/bd77-census.json，1360 窗
                  书库页）：图标左缘 260 / 右缘 276、输入框 x248，pl 36 时缝 9、
                  32 时缝 5、40 时缝 13；框盒 1080×22 与 scrollWidth 1078 三档全不动，
                  占位文字 13 字在 32 档仍有 1062px 可用，不截断。
                  ⚠ 如实记：这一族的缝本在 8–9px，是「13/14px 图标 + left-2.5」的组合
                  凑出来的；本条是 16px 图标 + left-3（右缘 28），要吃满 8px 缝就得写
                  36——那正是本批要清的离刻度值。32 的缝 5 与 40 的缝 13 相对族值 8–9
                  各自都偏 3–5px，缝这一项分不出高下，裁决落在票上：取 32（三票）。
                  也没有顺手挪图标（left-3→left-2.5 是另一件事，不在本批范围）。 */}
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="筛选书名、作者或导入标签…"
                aria-label="筛选书籍"
                className="paper-input w-full pl-8 pr-8 text-sm"
              />
              {searchQuery && (
                <button
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-full p-0.5 text-paper-muted hover:text-paper-ink"
                  onClick={() => setSearchQuery("")}
                  aria-label="清空搜索"
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
                    “{searchQuery}”
                    <button type="button" aria-label="清除搜索词" onClick={() => setSearchQuery("")}><X size={12} /></button>
                  </span>
                )}
                {formatFilter !== "all" && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-moss/30 bg-moss/10 px-2 py-0.5 text-moss">
                    {formatFilter.toUpperCase()}
                    <button type="button" aria-label="移除格式筛选" onClick={() => setFormatFilter("all")}><X size={12} /></button>
                  </span>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  className="ml-1"
                  onClick={() => { setSearchQuery(""); setFormatFilter("all"); }}
                >
                  清除所有
                </Button>
              </div>
            )}

            {/* Book table */}
            {/* BD-178：这是一张逐列对齐的记录表，但此前全由 div 拼接——读屏器只能线性念出
                "TXT 夜雨书 未知作者 …"，听不到"本地路径/阅读进度"这些列名与值的对应。
                用 ARIA 表格角色描述既有结构，不改 DOM 层级，display:grid 的列轨照旧。 */}
            <div className="desktop-panel-card desktop-library-panel motion-panel overflow-hidden">
              <div role="table" aria-label="书库书籍">
                <div className="desktop-library-grid-head" role="row">
                  <div role="columnheader">格式</div>
                  <div role="columnheader">书籍</div>
                  <div role="columnheader">本地路径</div>
                  <div role="columnheader">阅读进度</div>
                  <div role="columnheader">最近阅读</div>
                  <div role="columnheader" className="text-right">操作</div>
                </div>
                {filteredAndSorted.length === 0 ? (
                  <div role="row">
                    <div role="cell" className="py-10 text-center text-sm text-paper-muted">
                      {hasActiveFilters ? "没有匹配搜索结果的书。试试调整筛选条件。" : "书库暂无数据。"}
                    </div>
                  </div>
                ) : (
                  filteredAndSorted.map((book) => {
                  const itemProgress = progress[book.id];
                  const percent = Math.round((itemProgress?.progressPercent ?? 0) * 100);
                  const sourcePath = book.originalPath ?? book.filePath;
                  const displayPath = sourcePath ? sourcePath.split(/[/\\]/).pop() || sourcePath : "未知路径";
                  // BD-124：行容器去掉 role="button"/tabIndex/onKeyDown——行内「打开资料」「移除书籍」
                  // 两个按钮嵌在 role=button 的行里构成 nested-interactive（多一个 tab 停靠点、AT 报"按钮套按钮"）。
                  // 整行 onClick 只作鼠标便利（无 role 不触发该规则）；键盘路径由行内「打开资料」按钮承担。
                  return (
                    <div
                      key={book.id}
                      className="desktop-library-row group"
                      data-format={book.format}
                      role="row"
                      onClick={() => openReader(book.id)}
                    >
                      <div role="cell">
                        <span className={`format-chip format-chip--${book.format}`}>{book.format}</span>
                      </div>
                      <div role="cell" className="min-w-0 text-left">
                        <div className="flex items-center gap-3">
                          <div className="library-cover" aria-hidden="true">
                            <span className="library-cover-glyph">{book.format === "epub" ? "书" : book.format === "md" ? "M" : "文"}</span>
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
                      <div role="cell" className="library-path" title={`完整路径：${sourcePath}`}>
                        {displayPath}
                      </div>
                      <div role="cell" className="grid gap-1">
                        <div className="flex items-center justify-between text-xs text-paper-muted">
                          <span className="library-progress-percent">{percent}%</span>
                          <span>{formatDuration(itemProgress?.totalReadingTimeMs)}</span>
                        </div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-paper-soft" role="progressbar" aria-label={`${book.title} 阅读进度`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
                          <div className="library-progress-fill" style={{ width: `${percent}%` }} />
                        </div>
                      </div>
                      <div role="cell" className="font-mono text-xs text-paper-muted">{formatDate(itemProgress?.lastReadAt)}</div>
                      <div role="cell" className="flex justify-end gap-1">
                        <Button
                          className="px-2"
                          variant="secondary"
                          aria-label={`打开「${book.title}」`}
                          onClick={(event) => {
                            event.stopPropagation();
                            openReader(book.id);
                          }}
                        >
                          <BookOpen size={15} />
                        </Button>
                        <button
                          className="grid h-8 w-8 place-items-center rounded-md text-paper-muted transition hover:bg-[color:var(--proof-tint)] hover:text-[color:var(--proof-mark)]"
                          title="移除"
                          aria-label={`移除书籍「${book.title}」`}
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
          </div>
        )}
      </div>
    </div>
  );
}
