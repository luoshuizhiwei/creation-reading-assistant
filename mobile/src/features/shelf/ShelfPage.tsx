import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { BookOpen, Grid, List, Search, X, History, Loader2, CheckCircle2, AlertCircle, RotateCw, Trash2, MoreHorizontal, Plus, Info, Download, SlidersHorizontal } from "lucide-react";
import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import { BookTile } from "./BookTile";
import { progressFor, readerPositionFor } from "./book-progress";
import { getBookReadiness, getReadableBookLocalPath, isBookDownloaded, isBookReadableOnDevice } from "./book-status";
import { shelfSortOptions } from "./shelf-options";
import {
  filterAndSortShelfBooks,
  getBookTagNames,
  getReadableBookCount,
  getShelfFilterSummary
} from "./shelf-selectors";
import type { ShelfSortMode, ShelfStatusFilter, ShelfViewMode } from "./shelf-types";
import { BookDetailSheet } from "./BookDetailSheet";
import { deleteMobileBook, saveMobileSnapshot, BOOK_CONTENT_STORAGE_KEY_PREFIX } from "../../services/mobile-storage";
import { deleteMobileBookFile, statMobileBookFile } from "../../storage/mobile-files";
import { nowIso } from "../../services/mobile-storage-core";
import { formatBytes } from "../../utils/mobile-helpers";
import type { ImportTask, ImportHistoryEntry } from "../../hooks/useMobileImport";

type BatchSheetKind = "shelf" | "category" | "tag" | null;

/** 删除撤销窗口时长（毫秒） */
const UNDO_DELETE_WINDOW_MS = 5000;
const SHELF_VIEW_MODE_KEY = "creation-reading-assistant-mobile-shelf-view";
const SHELF_SORT_MODE_KEY = "creation-reading-assistant-mobile-shelf-sort";
const SHELF_STATUS_FILTER_KEY = "creation-reading-assistant-mobile-shelf-status";

const shelfStatusOptions: Array<{ value: ShelfStatusFilter; label: string }> = [
  { value: "all", label: "全部" },
  { value: "reading", label: "在读" },
  { value: "completed", label: "已完成" },
  { value: "unread", label: "未开始" },
  { value: "readable", label: "本机可读" }
];

const importPhaseLabels: Record<ImportTask["phase"], string> = {
  queued: "等待处理",
  validating: "正在校验文件",
  reading: "正在复制到应用",
  parsing: "正在验证 EPUB 结构",
  committing: "正在保存书架记录",
  done: "导入完成",
  error: "导入失败"
};

function readShelfPreference<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
  try {
    const saved = localStorage.getItem(key) as T | null;
    return saved && allowed.includes(saved) ? saved : fallback;
  } catch {
    return fallback;
  }
}

export interface ShelfPageProps {
  snapshot: MobileSnapshot;
  downloadingBookId?: string;
  searchFocusToken: number;
  initialDetailBookId?: string;
  onOpenBook: (book: MobileBook) => void;
  onImport: (files: FileList | null) => void;
  onDownloadBook: (book: MobileBook) => void;
  onCancelDownload: () => void;
  onSnapshotChange: (snapshot: MobileSnapshot) => void;
  onMessage: (message: string) => void;
  onConfirm: (dialog: { title: string; message: string; onConfirm: () => void }) => void;
  importTasks: ImportTask[];
  importHistory: ImportHistoryEntry[];
  onRetryImport: (taskId: string) => void;
  onRepairBook: (bookId: string, file?: File) => void;
  onClearImportHistory: () => void;
  onDismissImportTask: (taskId: string) => void;
}

export function ShelfPage({
  snapshot,
  downloadingBookId,
  searchFocusToken,
  initialDetailBookId,
  onOpenBook,
  onImport,
  onDownloadBook,
  onCancelDownload,
  onSnapshotChange,
  onMessage,
  onConfirm,
  importTasks,
  importHistory,
  onRetryImport,
  onRepairBook,
  onClearImportHistory,
  onDismissImportTask
}: ShelfPageProps) {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [viewMode, setViewMode] = useState<ShelfViewMode>(() => readShelfPreference(SHELF_VIEW_MODE_KEY, ["grid", "list"], "grid"));
  const [sortMode, setSortMode] = useState<ShelfSortMode>(() => readShelfPreference(SHELF_SORT_MODE_KEY, ["recent", "imported", "title", "progress"], "recent"));
  const [statusFilter, setStatusFilter] = useState<ShelfStatusFilter>(() => readShelfPreference(SHELF_STATUS_FILTER_KEY, ["all", "reading", "completed", "unread", "readable"], "all"));
  const [searchActive, setSearchActive] = useState(false);
  const [showPageMenu, setShowPageMenu] = useState(false);
  const [selectedShelfId, setSelectedShelfId] = useState("");
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [selectedTagName, setSelectedTagName] = useState("");
  const [detailBookId, setDetailBookId] = useState(initialDetailBookId ?? "");
  const [actionBookId, setActionBookId] = useState("");
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchSheet, setBatchSheet] = useState<BatchSheetKind>(null);
  const [undoInfo, setUndoInfo] = useState<{ books: MobileBook[]; snapshotBefore: MobileSnapshot; timer: number } | null>(null);
  const [showImportHistory, setShowImportHistory] = useState(false);
  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const [repairBookId, setRepairBookId] = useState("");
  const searchInputRef = useRef<HTMLInputElement>(null);
  const repairInputRef = useRef<HTMLInputElement>(null);
  const undoTimerRef = useRef<number | null>(null);
  const detailBook = detailBookId ? snapshot.books.find((book) => book.id === detailBookId) : undefined;
  const actionBook = actionBookId ? snapshot.books.find((book) => book.id === actionBookId) : undefined;

  // 导入队列活跃任务（pending/processing），用于显示浮动进度卡片
  const activeImportTasks = useMemo(() => importTasks.filter((task) => task.status === "pending" || task.status === "processing"), [importTasks]);
  const hasActiveImports = activeImportTasks.length > 0;

  useEffect(() => {
    if (!searchFocusToken) return;
    setSearchActive(true);
  }, [searchFocusToken]);

  useEffect(() => {
    if (!searchActive) return;
    window.requestAnimationFrame(() => {
      searchInputRef.current?.focus();
      searchInputRef.current?.select();
    });
  }, [searchActive]);

  useEffect(() => {
    try {
      localStorage.setItem(SHELF_VIEW_MODE_KEY, viewMode);
      localStorage.setItem(SHELF_SORT_MODE_KEY, sortMode);
      localStorage.setItem(SHELF_STATUS_FILTER_KEY, statusFilter);
    } catch {
      // 这些仅是当前设备 UI 偏好，存储失败不影响书籍数据。
    }
  }, [viewMode, sortMode, statusFilter]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 200);
    return () => clearTimeout(timer);
  }, [query]);

  // 硬件返回键：一次只关闭一层书架 UI，不影响全局或阅读器返回键。
  useEffect(() => {
    if (!detailBookId && !actionBookId && !selectionMode && !batchSheet && !showImportHistory && !showFilterPanel && !showPageMenu && !searchActive) return;
    const handler = (event: Event) => {
      event.preventDefault();
      if (showImportHistory) {
        setShowImportHistory(false);
        return;
      }
      if (showFilterPanel) {
        setShowFilterPanel(false);
        return;
      }
      if (batchSheet) {
        setBatchSheet(null);
        return;
      }
      if (selectionMode) {
        setSelectionMode(false);
        setSelectedIds(new Set());
        return;
      }
      if (actionBookId) {
        setActionBookId("");
        return;
      }
      if (showPageMenu) {
        setShowPageMenu(false);
        return;
      }
      if (searchActive) {
        setSearchActive(false);
        setQuery("");
        return;
      }
      setDetailBookId("");
    };
    window.addEventListener("mobile-tab-back", handler);
    return () => window.removeEventListener("mobile-tab-back", handler);
  }, [detailBookId, actionBookId, selectionMode, batchSheet, showImportHistory, showFilterPanel, showPageMenu, searchActive]);

  useEffect(() => {
    if (!actionBookId && !batchSheet && !showFilterPanel) return undefined;
    const previousOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = "hidden";
    return () => {
      document.documentElement.style.overflow = previousOverflow;
    };
  }, [actionBookId, batchSheet, showFilterPanel]);

  // 卸载时清理撤销定时器
  useEffect(() => {
    return () => {
      if (undoTimerRef.current) {
        window.clearTimeout(undoTimerRef.current);
      }
    };
  }, []);

  const filtered = useMemo(() => {
    return filterAndSortShelfBooks(snapshot, {
      query: debouncedQuery,
      selectedShelfId,
      selectedCategoryId,
      selectedTagName,
      statusFilter,
      sortMode
    });
  }, [snapshot, debouncedQuery, selectedShelfId, selectedCategoryId, selectedTagName, statusFilter, sortMode]);
  const bookTagNames = useMemo(() => getBookTagNames(snapshot), [snapshot]);
  const readableBookCount = useMemo(() => getReadableBookCount(snapshot), [snapshot]);
  const activeFilterSummary = useMemo(() => {
    return getShelfFilterSummary(snapshot, {
      query: debouncedQuery,
      selectedShelfId,
      selectedCategoryId,
      selectedTagName
    });
  }, [snapshot, debouncedQuery, selectedShelfId, selectedCategoryId, selectedTagName]);
  const readingBookCount = useMemo(() => snapshot.books.filter((book) => {
    const progress = progressFor(snapshot, book.id);
    const state = snapshot.progress.find((item) => item.bookId === book.id)?.completionState;
    return progress > 0.05 && progress < 99.5 && state !== "completed";
  }).length, [snapshot]);

  const toggleBookActions = useCallback((bookId: string) => {
    setActionBookId((current) => current === bookId ? "" : bookId);
    setShowPageMenu(false);
  }, []);

  const handleOpenBook = useCallback(async (book: MobileBook) => {
    setActionBookId("");
    const readiness = getBookReadiness(book);
    if (book.origin === "sync_placeholder" || book.contentStatus === "missing") {
      onMessage(`《${book.title}》当前设备暂无本地内容，请先下载正文或重新导入。`);
      return;
    }
    if (book.contentStatus === "downloading") {
      onMessage(`《${book.title}》正文仍在下载，请稍后再试。`);
      return;
    }
    if (book.contentStatus === "failed") {
      onMessage(`《${book.title}》正文保存失败，请重新导入或重新同步。`);
      return;
    }
    const localPath = getReadableBookLocalPath(book);
    if (!localPath) {
      onMessage(`《${book.title}》缺少本地正文路径，无法打开。`);
      return;
    }
    try {
      const stat = await statMobileBookFile(localPath);
      if (!stat?.size) {
        const updatedAt = nowIso();
        const next: MobileSnapshot = {
          ...snapshot,
          books: snapshot.books.map((item) => item.id === book.id ? {
            ...item,
            contentStatus: "missing" as const,
            updatedAt,
            revision: (item.revision ?? 0) + 1
          } : item),
          updatedAt
        };
        await saveMobileSnapshot(next);
        onSnapshotChange(next);
        onMessage(`《${book.title}》的本地正文文件已丢失，请重新导入或下载。`);
        return;
      }
      const normalizedBook = readiness.tone !== "ready" || book.size !== stat.size ? {
        ...book,
        size: stat.size,
        contentStatus: "available" as const
      } : book;
      if (normalizedBook !== book) {
        const next = {
          ...snapshot,
          books: snapshot.books.map((item) => item.id === book.id ? normalizedBook : item),
          updatedAt: nowIso()
        };
        await saveMobileSnapshot(next);
        onSnapshotChange(next);
      }
      onOpenBook(normalizedBook);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      onMessage(`《${book.title}》读取本地正文失败：${detail.slice(0, 80)}。请重试或重新导入。`);
    }
  }, [snapshot, onMessage, onOpenBook, onSnapshotChange]);

  const requestDeleteBook = (book: MobileBook) => {
    setActionBookId("");
    onConfirm({
      title: "删除书籍",
      message: `确定从书架删除《${book.title}》吗？本地正文文件、阅读进度、书签和笔记会一并移除，此操作不可撤销。`,
      onConfirm: async () => {
        const next = await deleteMobileBook(snapshot, book.id);
        onSnapshotChange(next);
        if (detailBookId === book.id) setDetailBookId("");
        onMessage(`已删除《${book.title}》。`);
      }
    });
  };

  // === 多选模式 ===
  const enterSelectionMode = () => {
    setSelectionMode(true);
    setSelectedIds(new Set());
    setActionBookId("");
  };

  const exitSelectionMode = () => {
    setSelectionMode(false);
    setSelectedIds(new Set());
    setBatchSheet(null);
  };

  const toggleSelected = (bookId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(bookId)) next.delete(bookId);
      else next.add(bookId);
      return next;
    });
  };

  const selectAllVisible = () => {
    setSelectedIds(new Set(filtered.map((book) => book.id)));
  };

  const selectedBooks = useMemo(() => {
    if (!selectedIds.size) return [];
    return snapshot.books.filter((book) => selectedIds.has(book.id));
  }, [snapshot.books, selectedIds]);

  const selectedDownloadableBooks = useMemo(() => {
    return selectedBooks.filter((book) => !isBookDownloaded(book));
  }, [selectedBooks]);

  const selectedCachedBooks = useMemo(() => {
    return selectedBooks.filter((book) => isBookDownloaded(book));
  }, [selectedBooks]);

  // === 批量操作 ===
  const persistBatchBookUpdate = async (updatedBooks: MobileBook[], message: string) => {
    const timestamp = nowIso();
    const bookMap = new Map(updatedBooks.map((book) => [book.id, book]));
    const next: MobileSnapshot = {
      ...snapshot,
      books: snapshot.books.map((book) => bookMap.get(book.id) ?? book),
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(message);
  };

  const batchAddToShelf = async (shelfId: string) => {
    const targetShelf = snapshot.shelves.find((item) => item.id === shelfId);
    if (!targetShelf) return;
    const timestamp = nowIso();
    const newBookIds = selectedBooks.map((book) => book.id);
    const existingBookIds = new Set(targetShelf.bookIds);
    const additions = newBookIds.filter((id) => !existingBookIds.has(id));
    if (!additions.length) {
      onMessage(`所选书籍都已在书单「${targetShelf.name}」中。`);
      setBatchSheet(null);
      return;
    }
    const nextShelf = {
      ...targetShelf,
      bookIds: [...additions, ...targetShelf.bookIds],
      updatedAt: timestamp,
      revision: (targetShelf.revision ?? 0) + 1
    };
    const next: MobileSnapshot = {
      ...snapshot,
      shelves: [nextShelf, ...snapshot.shelves.filter((item) => item.id !== shelfId)],
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    onMessage(`已把 ${additions.length} 本书加入书单「${targetShelf.name}」。`);
    setBatchSheet(null);
  };

  const batchSetCategory = async (categoryId: string) => {
    const targetCategory = snapshot.categories.find((item) => item.id === categoryId);
    if (!targetCategory) return;
    const timestamp = nowIso();
    const updatedBooks = selectedBooks.map((book) => {
      const currentIds = book.categoryIds ?? [];
      if (currentIds.includes(categoryId)) return book;
      return {
        ...book,
        categoryIds: [categoryId, ...currentIds],
        updatedAt: timestamp,
        revision: (book.revision ?? 0) + 1
      } as MobileBook;
    });
    await persistBatchBookUpdate(updatedBooks, `已把 ${updatedBooks.length} 本书归入分类「${targetCategory.name}」。`);
    setBatchSheet(null);
  };

  const batchAddTag = async (tagName: string) => {
    if (!tagName.trim()) return;
    const trimmed = tagName.trim();
    const timestamp = nowIso();
    let changedCount = 0;
    const updatedBooks = selectedBooks.map((book) => {
      const currentTags = book.tagNames ?? [];
      if (currentTags.includes(trimmed)) return book;
      changedCount += 1;
      return {
        ...book,
        tagNames: [trimmed, ...currentTags],
        updatedAt: timestamp,
        revision: (book.revision ?? 0) + 1
      } as MobileBook;
    });
    await persistBatchBookUpdate(updatedBooks, changedCount ? `已为 ${changedCount} 本书添加标签「${trimmed}」。` : `所选书籍都已有标签「${trimmed}」。`);
  };

  const batchRemoveTag = async (tagName: string) => {
    const trimmed = tagName.trim();
    const timestamp = nowIso();
    let changedCount = 0;
    const updatedBooks = selectedBooks.map((book) => {
      const currentTags = book.tagNames ?? [];
      if (!currentTags.includes(trimmed)) return book;
      changedCount += 1;
      return {
        ...book,
        tagNames: currentTags.filter((tag) => tag !== trimmed),
        updatedAt: timestamp,
        revision: (book.revision ?? 0) + 1
      } as MobileBook;
    });
    await persistBatchBookUpdate(updatedBooks, changedCount ? `已从 ${changedCount} 本书中移除标签「${trimmed}」。` : `所选书籍都没有标签「${trimmed}」。`);
  };

  const batchDownloadContent = () => {
    if (!selectedDownloadableBooks.length) {
      onMessage("所选书籍的正文都已经在本机。");
      return;
    }
    let index = 0;
    const downloadNext = () => {
      if (index >= selectedDownloadableBooks.length) {
        onMessage(`已开始下载 ${selectedDownloadableBooks.length} 本书的正文。`);
        return;
      }
      const book = selectedDownloadableBooks[index];
      index += 1;
      onDownloadBook(book);
      // 下载是异步流程，下一本间隔发起避免阻塞
      window.setTimeout(downloadNext, 400);
    };
    downloadNext();
    onMessage(`开始下载 ${selectedDownloadableBooks.length} 本书的正文……`);
  };

  const batchClearContentCache = async () => {
    if (!selectedCachedBooks.length) {
      onMessage("所选书籍没有本机正文可清理。");
      return;
    }
    const timestamp = nowIso();
    let clearedCount = 0;
    for (const book of selectedCachedBooks) {
      const localPath = book.localContentPath || book.localFilePath || (book.filePath?.startsWith("books/") ? book.filePath : undefined);
      if (localPath) {
        await deleteMobileBookFile(localPath).catch(() => undefined);
      }
      localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
      clearedCount += 1;
    }
    const updatedBooks = selectedCachedBooks.map((book) => ({
      ...book,
      contentStatus: "missing" as const,
      localContentPath: undefined,
      localFilePath: undefined,
      localUri: undefined,
      updatedAt: timestamp,
      revision: (book.revision ?? 0) + 1
    }));
    await persistBatchBookUpdate(updatedBooks, `已清理 ${clearedCount} 本书的本机正文缓存。`);
  };

  const batchDeleteBooksWithUndo = () => {
    if (!selectedBooks.length) return;
    const booksCopy = selectedBooks.map((book) => ({ ...book }));
    onConfirm({
      title: "批量删除书籍",
      message: `确定从书架删除选中的 ${selectedBooks.length} 本书吗？本地正文文件、阅读进度、书签和笔记会一并移除。`,
      onConfirm: async () => {
        // 立即从 snapshot 中移除（提供撤销窗口）
        const timestamp = nowIso();
        const removedIds = new Set(booksCopy.map((book) => book.id));
        const next: MobileSnapshot = {
          ...snapshot,
          books: snapshot.books.filter((book) => !removedIds.has(book.id)),
          progress: snapshot.progress.filter((item) => !removedIds.has(item.bookId)),
          sessions: snapshot.sessions.filter((item) => !removedIds.has(item.bookId)),
          notes: snapshot.notes.filter((item) => !item.bookId || !removedIds.has(item.bookId)),
          highlights: snapshot.highlights.filter((item) => !item.bookId || !removedIds.has(item.bookId)),
          shelves: snapshot.shelves.map((shelf) => ({
            ...shelf,
            bookIds: shelf.bookIds.filter((id) => !removedIds.has(id))
          })),
          updatedAt: timestamp
        };
        await saveMobileSnapshot(next);
        onSnapshotChange(next);

        // 设置撤销定时器：5 秒后真正删除文件
        if (undoTimerRef.current) {
          window.clearTimeout(undoTimerRef.current);
        }
        const timer = window.setTimeout(async () => {
          // 真正删除本地文件
          for (const book of booksCopy) {
            const localPath = book.localContentPath || book.localFilePath || (book.filePath?.startsWith("books/") ? book.filePath : undefined);
            if (localPath) {
              await deleteMobileBookFile(localPath).catch(() => undefined);
            }
            localStorage.removeItem(`${BOOK_CONTENT_STORAGE_KEY_PREFIX}${book.id}`);
          }
          undoTimerRef.current = null;
          setUndoInfo(null);
        }, UNDO_DELETE_WINDOW_MS);
        undoTimerRef.current = timer;
        setUndoInfo({ books: booksCopy, snapshotBefore: snapshot, timer });

        exitSelectionMode();
        onMessage(`已删除 ${booksCopy.length} 本书，5 秒内可撤销。`);
      }
    });
  };

  const undoBatchDelete = async () => {
    if (!undoInfo) return;
    if (undoTimerRef.current) {
      window.clearTimeout(undoTimerRef.current);
      undoTimerRef.current = null;
    }
    // 文件尚未删除；同时恢复阅读进度、会话、笔记、高亮和原书单归属。
    const restoredBooks = undoInfo.books;
    const timestamp = nowIso();
    const existingIds = new Set(snapshot.books.map((book) => book.id));
    const toRestore = restoredBooks.filter((book) => !existingIds.has(book.id));
    if (!toRestore.length) {
      setUndoInfo(null);
      onMessage("没有可恢复的书籍。");
      return;
    }
    const restoredIds = new Set(toRestore.map((book) => book.id));
    const before = undoInfo.snapshotBefore;
    const next: MobileSnapshot = {
      ...snapshot,
      books: [...toRestore, ...snapshot.books],
      progress: [...before.progress.filter((item) => restoredIds.has(item.bookId)), ...snapshot.progress.filter((item) => !restoredIds.has(item.bookId))],
      sessions: [...before.sessions.filter((item) => restoredIds.has(item.bookId)), ...snapshot.sessions.filter((item) => !restoredIds.has(item.bookId))],
      notes: [...before.notes.filter((item) => item.bookId && restoredIds.has(item.bookId)), ...snapshot.notes.filter((item) => !item.bookId || !restoredIds.has(item.bookId))],
      highlights: [...before.highlights.filter((item) => restoredIds.has(item.bookId)), ...snapshot.highlights.filter((item) => !restoredIds.has(item.bookId))],
      shelves: snapshot.shelves.map((shelf) => {
        const original = before.shelves.find((item) => item.id === shelf.id);
        const restoreIds = original?.bookIds.filter((id) => restoredIds.has(id)) ?? [];
        if (!restoreIds.length) return shelf;
        return { ...shelf, bookIds: [...new Set([...restoreIds, ...shelf.bookIds])] };
      }),
      updatedAt: timestamp
    };
    await saveMobileSnapshot(next);
    onSnapshotChange(next);
    setUndoInfo(null);
    onMessage(`已撤销删除 ${toRestore.length} 本书。`);
  };

  if (detailBook) {
    return (
      <BookDetailSheet
        book={detailBook}
        snapshot={snapshot}
        downloadingBookId={downloadingBookId}
        onClose={() => setDetailBookId("")}
        onOpenBook={(book) => {
          setDetailBookId("");
          void handleOpenBook(book);
        }}
        onDownloadBook={onDownloadBook}
        onCancelDownload={onCancelDownload}
        onSnapshotChange={onSnapshotChange}
        onMessage={onMessage}
        onConfirm={onConfirm}
      />
    );
  }

  const selectedCount = selectedIds.size;
  const visibleCount = filtered.length;

  return (
    <div className={`screen-stack ${selectionMode ? "shelf-selection-active" : ""}`}>
      <header className={`mobile-header shelf-page-header ${searchActive ? "is-searching" : ""}`}>
        {selectionMode ? (
          <>
            <h1>选择书籍</h1>
            <button className="shelf-header-action" onClick={exitSelectionMode} aria-label="退出多选">
              <X size={20} />
            </button>
          </>
        ) : searchActive ? (
          <div className="shelf-header-search">
            <Search size={20} aria-hidden="true" />
            <input
              ref={searchInputRef}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索书名、作者或文件名"
              aria-label="搜索书架"
            />
            {query ? <button onClick={() => setQuery("")} aria-label="清空搜索"><X size={18} /></button> : null}
            <button onClick={() => { setSearchActive(false); setQuery(""); }} aria-label="退出搜索">取消</button>
          </div>
        ) : (
          <>
            <h1>书架</h1>
            <div className="shelf-header-actions">
              <button className="shelf-header-action" onClick={() => setSearchActive(true)} aria-label="搜索书架">
                <Search size={21} />
              </button>
              <label className={`shelf-header-action shelf-import-action ${hasActiveImports ? "is-disabled" : ""}`} aria-label="导入本地书籍">
                <Plus size={22} />
                <input
                  hidden
                  type="file"
                  accept=".txt,.md,.markdown,.epub"
                  multiple
                  disabled={hasActiveImports}
                  onChange={(event) => {
                    const files = event.currentTarget.files;
                    onImport(files);
                    event.currentTarget.value = "";
                  }}
                />
              </label>
              <button className="shelf-header-action" onClick={() => setShowPageMenu((current) => !current)} aria-label="书架更多操作" aria-expanded={showPageMenu}>
                <MoreHorizontal size={22} />
              </button>
            </div>
            {showPageMenu ? (
              <div className="shelf-page-menu" role="menu">
                <button role="menuitem" onClick={() => { setShowPageMenu(false); enterSelectionMode(); }} disabled={!snapshot.books.length}>
                  <CheckCircle2 size={17} />批量管理
                </button>
                <button role="menuitem" onClick={() => { setShowPageMenu(false); setShowImportHistory(true); }}>
                  <History size={17} />导入历史
                  {(importTasks.length > 0 || importHistory.length > 0) ? <span>{importTasks.length + importHistory.length}</span> : null}
                </button>
              </div>
            ) : null}
          </>
        )}
      </header>

      {hasActiveImports && !selectionMode && (
        <section className="import-queue-card" aria-label="导入进度">
          <div className="import-queue-header">
            <Loader2 size={16} className="import-spinner" />
            <span className="import-queue-title">正在导入 {activeImportTasks.length} 本书</span>
          </div>
          <div className="import-queue-list">
            {activeImportTasks.slice(0, 3).map((task) => (
              <div key={task.id} className="import-queue-item">
                <span className="import-queue-name">{task.fileName}<small>{importPhaseLabels[task.phase]}</small></span>
                <div className="import-queue-progress">
                  <div className="import-queue-progress-bar is-indeterminate" />
                </div>
              </div>
            ))}
            {activeImportTasks.length > 3 && (
              <p className="import-queue-more">还有 {activeImportTasks.length - 3} 本等待中……</p>
            )}
          </div>
        </section>
      )}

      {selectionMode ? (
        <section className="shelf-selection-bar" aria-label="批量选择操作">
          <span className="selection-count">已选 {selectedCount} 本</span>
          <div className="selection-actions">
            <button className="ghost-button" onClick={selectAllVisible} disabled={visibleCount === 0}>
              全选{visibleCount ? `(${visibleCount})` : ""}
            </button>
            <button className="ghost-button" onClick={() => setSelectedIds(new Set())} disabled={selectedCount === 0}>
              清空
            </button>
          </div>
        </section>
      ) : (
        <>
          <div className="shelf-status-row">
            <div className="shelf-status-rail" role="tablist" aria-label="书籍状态筛选">
              {shelfStatusOptions.map((option) => (
                <button
                  key={option.value}
                  role="tab"
                  aria-selected={statusFilter === option.value}
                  className={statusFilter === option.value ? "active" : ""}
                  onClick={() => setStatusFilter(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <span className="shelf-stats-note">{snapshot.books.length} 本 · {readingBookCount} 在读 · {readableBookCount} 本机可读</span>
          </div>

          <section className="shelf-toolbar reading-shelf-toolbar" aria-label="书架排序和视图">
            <div className="shelf-subtoolbar">
              <label className="shelf-sort-select" aria-label="书籍排序方式">
                <select value={sortMode} onChange={(event) => setSortMode(event.target.value as ShelfSortMode)}>
                  {shelfSortOptions.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </label>
              <div className="shelf-subtoolbar-actions">
                {Boolean(snapshot.shelves.length || snapshot.categories.length || bookTagNames.length) && (
                  <button
                    className="shelf-filter-toggle"
                    onClick={() => setShowFilterPanel(true)}
                    aria-label="筛选"
                    aria-expanded={showFilterPanel}
                  >
                    <SlidersHorizontal size={16} />
                    <span>筛选</span>
                    {(selectedShelfId || selectedCategoryId || selectedTagName) && (
                      <span className="shelf-filter-dot" />
                    )}
                  </button>
                )}
                <div className="view-toggle" aria-label="书架视图">
                  <button className={viewMode === "grid" ? "active" : ""} onClick={() => setViewMode("grid")} aria-label="网格视图">
                    <Grid size={18} />
                  </button>
                  <button className={viewMode === "list" ? "active" : ""} onClick={() => setViewMode("list")} aria-label="列表视图">
                    <List size={18} />
                  </button>
                </div>
              </div>
            </div>
          </section>

          {activeFilterSummary && (
            <div className="active-filter-note">
              {activeFilterSummary}
              {" "}·
              <button onClick={() => {
                setQuery("");
                setSelectedShelfId("");
                setSelectedCategoryId("");
                setSelectedTagName("");
              }}>重置全部</button>
            </div>
          )}
        </>
      )}

      <section className={`book-grid ${viewMode === "list" ? "book-list" : ""} ${selectionMode ? "selection-grid" : ""}`}>
        {filtered.map((book) => (
          <BookTile
            key={book.id}
            book={book}
            progress={progressFor(snapshot, book.id)}
            position={readerPositionFor(snapshot, book.id)}
            downloaded={isBookReadableOnDevice(book)}
            viewMode={viewMode}
            selectionMode={selectionMode}
            selected={selectedIds.has(book.id)}
            actionsOpen={actionBookId === book.id}
            onOpenBook={(targetBook) => void handleOpenBook(targetBook)}
            onToggleActions={toggleBookActions}
            onToggleSelected={toggleSelected}
          />
        ))}
      </section>

      {!filtered.length && !selectionMode && (
        <section className="empty-state shelf-empty-state">
          <BookOpen size={24} strokeWidth={1.9} />
          <strong>{snapshot.books.length ? (debouncedQuery ? "没有找到匹配的书" : "当前筛选下没有书籍") : "书架还空着"}</strong>
          <p>{snapshot.books.length ? (debouncedQuery ? "换个书名或作者试试。" : "切换到“全部”，或清除书单、分类和标签筛选。") : "导入 TXT、Markdown 或 EPUB 开始本地阅读。"}</p>
          {snapshot.books.length ? (
            <button className="secondary" onClick={() => {
              setQuery("");
              setStatusFilter("all");
              setSelectedShelfId("");
              setSelectedCategoryId("");
              setSelectedTagName("");
            }}>
              显示全部
            </button>
          ) : (
            <label className="empty-import-action">
              导入一本书
              <input hidden type="file" accept=".txt,.md,.markdown,.epub" multiple onChange={(event) => {
                const files = event.currentTarget.files;
                onImport(files);
                event.currentTarget.value = "";
              }} />
            </label>
          )}
        </section>
      )}
      {snapshot.books.length > 0 && !selectionMode && (
        <p className="center-foot shelf-count-foot">共 {snapshot.books.length} 本书籍 · 当前显示 {filtered.length} 本</p>
      )}

      {selectionMode && (
        <nav className="shelf-batch-bar" aria-label="批量操作工具栏">
          <button
            className="batch-btn"
            disabled={selectedCount === 0}
            onClick={() => setBatchSheet("shelf")}
          >
            加入书单
          </button>
          <button
            className="batch-btn"
            disabled={selectedCount === 0}
            onClick={() => setBatchSheet("category")}
          >
            设置分类
          </button>
          <button
            className="batch-btn"
            disabled={selectedCount === 0}
            onClick={() => setBatchSheet("tag")}
          >
            标签
          </button>
          <button
            className="batch-btn"
            disabled={selectedDownloadableBooks.length === 0}
            onClick={batchDownloadContent}
          >
            下载正文{selectedDownloadableBooks.length ? `(${selectedDownloadableBooks.length})` : ""}
          </button>
          <button
            className="batch-btn"
            disabled={selectedCachedBooks.length === 0}
            onClick={() => void batchClearContentCache()}
          >
            清理缓存{selectedCachedBooks.length ? `(${selectedCachedBooks.length})` : ""}
          </button>
          <button
            className="batch-btn batch-btn-danger"
            disabled={selectedCount === 0}
            onClick={batchDeleteBooksWithUndo}
          >
            删除{selectedCount ? `(${selectedCount})` : ""}
          </button>
        </nav>
      )}

      {undoInfo && (
        <div className="shelf-undo-toast" role="status" aria-live="polite">
          <span>已删除 {undoInfo.books.length} 本书</span>
          <button onClick={() => void undoBatchDelete()}>撤销</button>
        </div>
      )}

      {actionBook ? createPortal(
        <>
          <div className="reader-sheet-mask shelf-action-mask" onClick={() => setActionBookId("")} />
          <aside className="reader-bottom-sheet-panel shelf-book-action-sheet" role="dialog" aria-modal="true" aria-label={`管理《${actionBook.title}》`}>
            <header className="reader-sheet-handle"><span className="reader-sheet-grabber" /></header>
            <div className="shelf-action-book-summary">
              <div className="shelf-action-cover" aria-hidden="true">{actionBook.title.slice(0, 4)}</div>
              <div>
                <h2>{actionBook.title}</h2>
                <p>{actionBook.author || "作者未知"} · {getBookReadiness(actionBook).label}</p>
              </div>
            </div>
            <div className="shelf-action-list">
              {isBookReadableOnDevice(actionBook) ? (
                <button onClick={() => void handleOpenBook(actionBook)}><BookOpen size={19} />继续阅读</button>
              ) : (actionBook.origin === "sync_placeholder" || actionBook.contentStatus === "missing") ? (
                <>
                  {actionBook.origin === "sync_placeholder" ? (
                    <button onClick={() => { setActionBookId(""); onDownloadBook(actionBook); }}><Download size={19} />下载正文到本机</button>
                  ) : null}
                  <button onClick={() => {
                    setRepairBookId(actionBook.id);
                    setActionBookId("");
                    window.setTimeout(() => repairInputRef.current?.click(), 0);
                  }}><RotateCw size={19} />重新选择文件修复正文</button>
                </>
              ) : null}
              <button onClick={() => { setActionBookId(""); setDetailBookId(actionBook.id); }}><Info size={19} />书籍详情与管理</button>
              <button className="danger" onClick={() => requestDeleteBook(actionBook)}><Trash2 size={19} />删除书籍</button>
            </div>
          </aside>
        </>,
        document.body
      ) : null}

      <input
        ref={repairInputRef}
        hidden
        type="file"
        accept=".txt,.md,.markdown,.epub"
        onChange={(event) => {
          const file = event.currentTarget.files?.[0];
          if (repairBookId && file) onRepairBook(repairBookId, file);
          setRepairBookId("");
          event.currentTarget.value = "";
        }}
      />

      {batchSheet && (
        <>
          <div className="reader-sheet-mask" onClick={() => setBatchSheet(null)} />
          <aside className="reader-bottom-sheet-panel shelf-batch-sheet" role="dialog" aria-modal="true">
            <header className="reader-sheet-handle">
              <span className="reader-sheet-grabber" />
            </header>
            <h4 className="reader-sheet-title">
              {batchSheet === "shelf" ? "加入书单" : batchSheet === "category" ? "设置分类" : "管理标签"}
              <span className="batch-sheet-subtitle">已选 {selectedCount} 本</span>
            </h4>
            {batchSheet === "shelf" && (
              <div className="batch-sheet-list">
                {snapshot.shelves.length ? snapshot.shelves.map((shelf) => (
                  <button
                    key={shelf.id}
                    className="batch-sheet-item"
                    onClick={() => void batchAddToShelf(shelf.id)}
                  >
                    <span>{shelf.name}</span>
                    <em>{shelf.bookIds.filter((id) => selectedIds.has(id)).length} 已在</em>
                  </button>
                )) : <p className="empty-hint">还没有书单，请先在“我的 / 书单管理”创建。</p>}
              </div>
            )}
            {batchSheet === "category" && (
              <div className="batch-sheet-list">
                {snapshot.categories.length ? snapshot.categories.map((category) => {
                  const alreadyInCount = selectedBooks.filter((book) => book.categoryIds?.includes(category.id)).length;
                  return (
                    <button
                      key={category.id}
                      className="batch-sheet-item"
                      onClick={() => void batchSetCategory(category.id)}
                    >
                      <span>{category.name}</span>
                      <em>{alreadyInCount ? `${alreadyInCount} 已在` : "加入"}</em>
                    </button>
                  );
                }) : <p className="empty-hint">还没有分类，请先在“我的 / 分类管理”创建。</p>}
              </div>
            )}
            {batchSheet === "tag" && (
              <BatchTagManager
                existingTags={bookTagNames}
                selectedBooks={selectedBooks}
                onAddTag={(name) => void batchAddTag(name)}
                onRemoveTag={(name) => void batchRemoveTag(name)}
              />
            )}
          </aside>
        </>
      )}

      {showFilterPanel && (
        <>
          <div className="reader-sheet-mask" onClick={() => setShowFilterPanel(false)} />
          <aside className="reader-bottom-sheet-panel shelf-filter-panel" role="dialog" aria-modal="true" aria-label="筛选">
            <header className="reader-sheet-handle">
              <span className="reader-sheet-grabber" />
            </header>
            <h4 className="reader-sheet-title">筛选</h4>
            <div className="shelf-filter-groups">
              {snapshot.shelves.length ? (
                <div className="shelf-filter-group">
                  <span className="shelf-filter-group-label">书单</span>
                  <div className="filter-chip-rail">
                    <button className={!selectedShelfId ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedShelfId("")}>
                      全部书单
                    </button>
                    {snapshot.shelves.map((shelf) => (
                      <button key={shelf.id} className={selectedShelfId === shelf.id ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedShelfId(shelf.id)}>
                        {shelf.name}
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
              {snapshot.categories.length ? (
                <div className="shelf-filter-group">
                  <span className="shelf-filter-group-label">分类</span>
                  <div className="filter-chip-rail">
                    <button className={!selectedCategoryId ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedCategoryId("")}>
                      全部分类
                    </button>
                    {snapshot.categories.map((category) => (
                      <button key={category.id} className={selectedCategoryId === category.id ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedCategoryId(category.id)}>
                        {category.name}
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
              {bookTagNames.length ? (
                <div className="shelf-filter-group">
                  <span className="shelf-filter-group-label">标签</span>
                  <div className="filter-chip-rail">
                    <button className={!selectedTagName ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedTagName("")}>
                      全部标签
                    </button>
                    {bookTagNames.map((tag) => (
                      <button key={tag} className={selectedTagName === tag ? "filter-chip active" : "filter-chip"} onClick={() => setSelectedTagName(tag)}>
                        {tag}
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          </aside>
        </>
      )}

      {showImportHistory && (
        <ImportHistoryPanel
          importTasks={importTasks}
          importHistory={importHistory}
          onRetryImport={onRetryImport}
          onClearImportHistory={onClearImportHistory}
          onDismissImportTask={onDismissImportTask}
          onClose={() => setShowImportHistory(false)}
        />
      )}
    </div>
  );
}

/** 导入历史二级页面：显示当前队列任务 + 持久化导入历史 */
function ImportHistoryPanel({
  importTasks,
  importHistory,
  onRetryImport,
  onClearImportHistory,
  onDismissImportTask,
  onClose
}: {
  importTasks: ImportTask[];
  importHistory: ImportHistoryEntry[];
  onRetryImport: (taskId: string) => void;
  onClearImportHistory: () => void;
  onDismissImportTask: (taskId: string) => void;
  onClose: () => void;
}) {
  const successCount = importHistory.filter((item) => item.status === "success").length;
  const failedCount = importHistory.filter((item) => item.status === "failed").length;

  return (
    <div className="import-history-panel">
      <header className="mobile-header row-header subpage-header">
        <button className="ghost-button back-button" onClick={onClose}>← 返回</button>
        <div>
          <p className="mini-label">导入记录</p>
          <h1>导入历史</h1>
        </div>
        {importHistory.length > 0 && (
          <button className="ghost-button import-history-clear-btn" onClick={onClearImportHistory} aria-label="清空历史">
            <Trash2 size={16} />
          </button>
        )}
      </header>

      {importTasks.length > 0 && (
        <section className="subpage-card import-history-section">
          <h2 className="section-heading">本次导入队列（{importTasks.length}）</h2>
          <div className="import-history-list">
            {importTasks.map((task) => (
              <div key={task.id} className={`import-history-item import-status-${task.status}`}>
                <div className="import-history-item-main">
                  <div className="import-history-item-header">
                    {task.status === "processing" || task.status === "pending" ? (
                      <Loader2 size={15} className="import-spinner" />
                    ) : task.status === "success" ? (
                      <CheckCircle2 size={15} />
                    ) : (
                      <AlertCircle size={15} />
                    )}
                    <span className="import-history-file-name">{task.fileName}</span>
                  </div>
                  <div className="import-history-item-meta">
                    <span>{formatBytes(task.fileSize)}</span>
                    {task.format && <span>· {task.format.toUpperCase()}</span>}
                    {task.encoding && <span>· {task.encoding}</span>}
                    <span>· {importPhaseLabels[task.phase]}</span>
                    {task.isDuplicate && <span className="import-duplicate-tag">重复</span>}
                    {task.bookTitle && <span>· 《{task.bookTitle}》</span>}
                    {task.error && <span className="import-error-text">· {task.error}</span>}
                  </div>
                  {task.status === "processing" && (
                    <div className="import-history-progress">
                      <div className="import-history-progress-bar is-indeterminate" />
                    </div>
                  )}
                </div>
                <div className="import-history-item-actions">
                  {task.status === "failed" && task.fileRef && (
                    <button className="mini-row-action" onClick={() => onRetryImport(task.id)}>
                      <RotateCw size={13} />重试
                    </button>
                  )}
                  {(task.status === "success" || task.status === "failed") && (
                    <button className="mini-row-action" onClick={() => onDismissImportTask(task.id)}>
                      <X size={13} />
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {importHistory.length > 0 ? (
        <section className="subpage-card import-history-section">
          <h2 className="section-heading">
            历史记录（{importHistory.length}）
            <span className="import-history-summary">成功 {successCount} · 失败 {failedCount}</span>
          </h2>
          <div className="import-history-list">
            {importHistory.map((entry) => (
              <div key={entry.id} className={`import-history-item import-status-${entry.status}`}>
                <div className="import-history-item-main">
                  <div className="import-history-item-header">
                    {entry.status === "success" ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />}
                    <span className="import-history-file-name">{entry.fileName}</span>
                  </div>
                  <div className="import-history-item-meta">
                    <span>{formatBytes(entry.fileSize)}</span>
                    {entry.format && <span>· {entry.format.toUpperCase()}</span>}
                    {entry.encoding && <span>· {entry.encoding}</span>}
                    {entry.isDuplicate && <span className="import-duplicate-tag">重复</span>}
                    {entry.bookTitle && <span>· 《{entry.bookTitle}》</span>}
                    {entry.error && <span className="import-error-text">· {entry.error}</span>}
                  </div>
                  <div className="import-history-item-time">
                    {new Date(entry.timestamp).toLocaleString("zh-CN", { hour12: false })}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : (
        <section className="empty-state import-history-empty">
          <History size={24} strokeWidth={1.9} />
          <strong>还没有导入记录</strong>
          <p>导入书籍后，这里会显示每次导入的结果、编码识别和失败原因。</p>
        </section>
      )}
    </div>
  );
}

/** 批量标签管理：显示已有标签（添加/移除）+ 新建标签输入 */
function BatchTagManager({
  existingTags,
  selectedBooks,
  onAddTag,
  onRemoveTag
}: {
  existingTags: string[];
  selectedBooks: MobileBook[];
  onAddTag: (name: string) => void;
  onRemoveTag: (name: string) => void;
}) {
  const [newTag, setNewTag] = useState("");
  const tagStats = useMemo(() => {
    const map = new Map<string, number>();
    for (const book of selectedBooks) {
      for (const tag of book.tagNames ?? []) {
        map.set(tag, (map.get(tag) ?? 0) + 1);
      }
    }
    return map;
  }, [selectedBooks]);

  const handleCreate = () => {
    const trimmed = newTag.trim();
    if (!trimmed) return;
    onAddTag(trimmed);
    setNewTag("");
  };

  return (
    <div className="batch-tag-manager">
      <div className="batch-tag-input-row">
        <input
          type="text"
          value={newTag}
          onChange={(e) => setNewTag(e.target.value)}
          placeholder="新建标签并添加到所选书籍"
          maxLength={20}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              handleCreate();
            }
          }}
        />
        <button className="secondary-button" onClick={handleCreate} disabled={!newTag.trim()}>
          添加
        </button>
      </div>
      <div className="batch-tag-list">
        {existingTags.length ? existingTags.map((tag) => {
          const count = tagStats.get(tag) ?? 0;
          return (
            <div key={tag} className="batch-tag-row">
              <span className="batch-tag-name">{tag}{count ? ` (${count}/${selectedBooks.length})` : ""}</span>
              <div className="batch-tag-ops">
                <button className="ghost-button" onClick={() => onAddTag(tag)} disabled={count === selectedBooks.length}>
                  添加
                </button>
                <button className="ghost-button text-danger" onClick={() => onRemoveTag(tag)} disabled={count === 0}>
                  移除
                </button>
              </div>
            </div>
          );
        }) : <p className="empty-hint">还没有书籍标签。在上方输入框新建一个，或去“我的 / 标签管理”创建。</p>}
      </div>
    </div>
  );
}
