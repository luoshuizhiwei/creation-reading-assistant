import { getDesktopApi } from "@/services/ipc-client";
import type { BookmarkItem, HighlightItem } from "@/types/library";

export async function getHighlightsByBook(bookId: string): Promise<HighlightItem[]> {
  return getDesktopApi().annotations.getHighlightsByBook(bookId);
}

export async function saveHighlight(item: HighlightItem): Promise<HighlightItem> {
  return getDesktopApi().annotations.saveHighlight(item);
}

export async function deleteHighlight(id: string): Promise<void> {
  return getDesktopApi().annotations.deleteHighlight(id);
}

export async function getBookmarksByBook(bookId: string): Promise<BookmarkItem[]> {
  return getDesktopApi().annotations.getBookmarksByBook(bookId);
}

export async function saveBookmark(item: BookmarkItem): Promise<BookmarkItem> {
  return getDesktopApi().annotations.saveBookmark(item);
}

export async function deleteBookmark(id: string): Promise<void> {
  return getDesktopApi().annotations.deleteBookmark(id);
}
