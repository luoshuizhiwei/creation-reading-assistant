import { useCallback, useRef, useState } from "react";
import {
  deleteBookmark,
  deleteHighlight,
  getBookmarksByBook,
  getHighlightsByBook,
  saveBookmark,
  saveHighlight
} from "@/services/annotation-service";
import type { BookmarkItem, HighlightItem } from "@/types/library";

/**
 * useBookAnnotations — 书籍批注（高亮与书签）通用生命周期 Hook
 *
 * 封装 TxtMarkdownReader 与 EpubReaderPage 共享的：
 * - highlights / bookmarks 状态管理与 ref 镜像
 * - 并行 loadAnnotations (getHighlightsByBook + getBookmarksByBook)
 * - 高亮与书签的 CRUD 操作与乐观状态更新
 */
export function useBookAnnotations() {
  const [highlights, setHighlights] = useState<HighlightItem[]>([]);
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([]);
  const highlightsRef = useRef<HighlightItem[]>([]);
  const bookmarksRef = useRef<BookmarkItem[]>([]);

  const loadAnnotations = useCallback(async (bookId: string) => {
    try {
      const [hl, bm] = await Promise.all([
        getHighlightsByBook(bookId),
        getBookmarksByBook(bookId)
      ]);
      setHighlights(hl);
      highlightsRef.current = hl;
      setBookmarks(bm);
      bookmarksRef.current = bm;
    } catch {
      // 容错：加载失败不阻塞阅读
    }
  }, []);

  const addHighlightItem = useCallback(async (item: HighlightItem): Promise<HighlightItem> => {
    const saved = await saveHighlight(item);
    setHighlights((prev) => {
      const next = [...prev, saved];
      highlightsRef.current = next;
      return next;
    });
    return saved;
  }, []);

  const removeHighlightItem = useCallback(async (id: string): Promise<void> => {
    await deleteHighlight(id);
    setHighlights((prev) => {
      const next = prev.filter((h) => h.id !== id);
      highlightsRef.current = next;
      return next;
    });
  }, []);

  const addBookmarkItem = useCallback(async (item: BookmarkItem): Promise<BookmarkItem> => {
    const saved = await saveBookmark(item);
    setBookmarks((prev) => {
      const next = [...prev, saved];
      bookmarksRef.current = next;
      return next;
    });
    return saved;
  }, []);

  const removeBookmarkItem = useCallback(async (id: string): Promise<void> => {
    await deleteBookmark(id);
    setBookmarks((prev) => {
      const next = prev.filter((b) => b.id !== id);
      bookmarksRef.current = next;
      return next;
    });
  }, []);

  return {
    highlights,
    bookmarks,
    highlightsRef,
    bookmarksRef,
    loadAnnotations,
    addHighlightItem,
    removeHighlightItem,
    addBookmarkItem,
    removeBookmarkItem,
    setHighlights,
    setBookmarks
  };
}
