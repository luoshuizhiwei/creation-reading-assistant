import type { MobileBook, MobileSnapshot } from "../../types/mobile";
import { progressFor } from "./book-progress";
import { isBookReadableOnDevice } from "./book-status";
import type { ShelfSortMode, ShelfStatusFilter } from "./shelf-types";

export interface ShelfFilterState {
  query: string;
  selectedShelfId: string;
  selectedCategoryId: string;
  selectedTagName: string;
  statusFilter: ShelfStatusFilter;
  sortMode: ShelfSortMode;
}

export function filterAndSortShelfBooks(snapshot: MobileSnapshot, filters: ShelfFilterState): MobileBook[] {
  const lowerQuery = filters.query.trim().toLowerCase();
  return snapshot.books
    .map((book, originalIndex) => ({ book, originalIndex }))
    .filter(({ book }) => `${book.title} ${book.author ?? ""} ${book.originalFileName ?? ""}`.toLowerCase().includes(lowerQuery))
    .filter(({ book }) => !filters.selectedShelfId || snapshot.shelves.find((shelf) => shelf.id === filters.selectedShelfId)?.bookIds.includes(book.id))
    .filter(({ book }) => !filters.selectedCategoryId || book.categoryIds?.includes(filters.selectedCategoryId))
    .filter(({ book }) => !filters.selectedTagName || book.tagNames?.includes(filters.selectedTagName))
    .filter(({ book }) => matchesStatusFilter(snapshot, book, filters.statusFilter))
    .sort((left, right) => compareShelfBooks(snapshot, left.book, right.book, filters.sortMode) || left.originalIndex - right.originalIndex)
    .map(({ book }) => book);
}

function matchesStatusFilter(snapshot: MobileSnapshot, book: MobileBook, filter: ShelfStatusFilter): boolean {
  if (filter === "all") return true;
  if (filter === "readable") return isBookReadableOnDevice(book);
  const progress = snapshot.progress.find((item) => item.bookId === book.id);
  const percent = progressFor(snapshot, book.id);
  if (filter === "completed") return progress?.completionState === "completed" || percent >= 99.5;
  if (filter === "reading") return Boolean(progress) && percent > 0.05 && percent < 99.5 && progress?.completionState !== "completed";
  return !progress || percent <= 0.05 || progress.completionState === "unread";
}

export function compareShelfBooks(snapshot: MobileSnapshot, left: MobileBook, right: MobileBook, sortMode: ShelfSortMode): number {
  if (sortMode === "title") return left.title.localeCompare(right.title, "zh-Hans-CN");
  if (sortMode === "progress") return progressFor(snapshot, right.id) - progressFor(snapshot, left.id);
  if (sortMode === "imported") return compareOptionalDates(left.importedAt, right.importedAt);
  const leftReadAt = snapshot.progress.find((item) => item.bookId === left.id)?.lastReadAt;
  const rightReadAt = snapshot.progress.find((item) => item.bookId === right.id)?.lastReadAt;
  return compareOptionalDates(leftReadAt, rightReadAt);
}

function compareOptionalDates(left?: string, right?: string): number {
  if (left && right) return right.localeCompare(left);
  if (left) return -1;
  if (right) return 1;
  return 0;
}

export function getBookTagNames(snapshot: MobileSnapshot): string[] {
  const names = new Set<string>();
  snapshot.tags.filter((tag) => tag.type === "book").forEach((tag) => names.add(tag.name));
  snapshot.books.forEach((book) => book.tagNames?.forEach((tag) => names.add(tag)));
  return [...names].sort((left, right) => left.localeCompare(right, "zh-Hans-CN"));
}

export function getReadableBookCount(snapshot: MobileSnapshot): number {
  return snapshot.books.filter(isBookReadableOnDevice).length;
}

export function getShelfFilterSummary(snapshot: MobileSnapshot, filters: Omit<ShelfFilterState, "sortMode" | "statusFilter">): string {
  const activeShelf = filters.selectedShelfId ? snapshot.shelves.find((item) => item.id === filters.selectedShelfId) : undefined;
  const activeCategory = filters.selectedCategoryId ? snapshot.categories.find((item) => item.id === filters.selectedCategoryId) : undefined;
  const parts: string[] = [];

  if (activeShelf) parts.push(`书单「${activeShelf.name}」`);
  if ((activeShelf && activeCategory) || (activeShelf && filters.selectedTagName)) parts.push(" / ");
  if (activeCategory) parts.push(`分类「${activeCategory.name}」`);
  if (activeCategory && filters.selectedTagName) parts.push(" / ");
  if (filters.selectedTagName) parts.push(`标签「${filters.selectedTagName}」`);
  if (filters.query) parts.push(`搜索"${filters.query}"`);

  return parts.join("");
}
