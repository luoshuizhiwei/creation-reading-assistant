import { getDesktopApi } from "@/services/ipc-client";
import type { LibraryBook } from "@/types/library";

export async function importBook(): Promise<LibraryBook[]> {
  return getDesktopApi().library.importBook();
}

export async function importEpub(): Promise<LibraryBook[]> {
  return getDesktopApi().library.importEpub();
}

export async function listBooks(): Promise<LibraryBook[]> {
  return getDesktopApi().library.listBooks();
}

export async function removeBook(bookId: string): Promise<LibraryBook[]> {
  return getDesktopApi().library.removeBook(bookId);
}
