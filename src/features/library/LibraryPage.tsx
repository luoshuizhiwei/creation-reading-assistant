import { useEffect } from "react";
import { ArrowLeft, BarChart3, BookOpen, FileText, Import, Settings, Trash2 } from "lucide-react";
import { Button, EmptyState, ShellPanel } from "@/components/ui";
import { useLibraryActions } from "@/hooks/useLibraryActions";
import { useLibraryStore } from "@/stores/library-store";
import { useAppStore } from "@/stores/app-store";

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

  useEffect(() => {
    void refreshBooks();
  }, [refreshBooks]);

  return (
    <div className="grid h-full grid-rows-[60px_1fr] overflow-hidden paper-shell">
      <header className="paper-topbar flex items-center gap-3 px-5">
        <div className="paper-title text-xl font-semibold text-copper">本地书库</div>
        <div className="min-w-0 flex-1 text-sm text-paper-muted">TXT / Markdown / EPUB 阅读入口</div>
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
          阅读统计
        </Button>
        <Button variant="quiet" onClick={() => setScreen("settings")}>
          <Settings size={16} />
          设置
        </Button>
        <Button variant="quiet" onClick={() => setScreen("start")}>
          <ArrowLeft size={16} />
          返回首页
        </Button>
      </header>

      <ShellPanel className="min-h-0 overflow-auto border-0 bg-transparent p-6 shadow-none">
        {loading ? (
          <div className="grid h-full place-items-center">
            <div className="text-sm text-paper-muted">正在加载书籍...</div>
          </div>
        ) : books.length === 0 ? (
          <EmptyState title="书库还是空的" body="导入 TXT、Markdown 或 EPUB 后，可以从这里打开阅读器并保存阅读进度。" />
        ) : (
          <div className="mx-auto grid max-w-7xl gap-4">
            <div className="flex items-end justify-between border-b border-paper-line pb-4">
              <div>
                <h1 className="paper-title text-2xl font-semibold">本地文档</h1>
                <p className="mt-1 text-sm text-paper-muted">共 {books.length} 本，进度和有效阅读时长保存在本地。</p>
              </div>
              <div className="paper-chip">列表视图</div>
            </div>
            <div className="motion-panel overflow-hidden rounded-xl border border-paper-line bg-paper-panel shadow-lift">
              <div className="grid grid-cols-[88px_1.4fr_2fr_1.2fr_1fr_88px] gap-4 border-b border-paper-line bg-paper-soft/50 px-4 py-3 text-xs font-medium text-paper-muted">
                <div>格式</div>
                <div>书籍</div>
                <div>本地路径</div>
                <div>阅读进度</div>
                <div>最近阅读</div>
                <div className="text-right">操作</div>
              </div>
              {books.map((book) => {
                const itemProgress = progress[book.id];
                const percent = Math.round((itemProgress?.progressPercent ?? 0) * 100);
                const sourcePath = book.originalPath ?? book.filePath;
                return (
                  <div
                    key={book.id}
                    role="button"
                    tabIndex={0}
                    className="group grid grid-cols-[88px_1.4fr_2fr_1.2fr_1fr_88px] items-center gap-4 border-b border-paper-line/70 px-4 py-4 transition duration-150 last:border-b-0 hover:-translate-y-0.5 hover:bg-paper-soft/35 hover:shadow-lift"
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
                          removeBookById(book.id);
                        }}
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </ShellPanel>
    </div>
  );
}

