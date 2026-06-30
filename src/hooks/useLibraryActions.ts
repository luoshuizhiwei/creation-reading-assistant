import { useCallback } from "react";
import { importBook, importEpub, listBooks, removeBook } from "@/services/library-service";
import { getProgress, openBook, openEpub } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";

function messageFromError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function hydrateProgress(bookIds: string[]) {
  const setProgress = useLibraryStore.getState().setProgress;
  await Promise.all(
    bookIds.map(async (bookId) => {
      const progress = await getProgress(bookId);
      if (progress) setProgress(progress);
    })
  );
}

export function useLibraryActions() {
  const setBooks = useLibraryStore((state) => state.setBooks);
  const setActiveBook = useLibraryStore((state) => state.setActiveBook);
  const setReaderSettings = useLibraryStore((state) => state.setReaderSettings);
  const setLoading = useLibraryStore((state) => state.setLoading);
  const setScreen = useAppStore((state) => state.setScreen);
  const setError = useAppStore((state) => state.setError);
  const confirmAction = useUIStore((state) => state.confirmAction);
  const showToast = useUIStore((state) => state.showToast);

  const refreshBooks = useCallback(async () => {
    setLoading(true);
    try {
      const books = await listBooks();
      setBooks(books);
      await hydrateProgress(books.map((book) => book.id));
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [setBooks, setError, setLoading]);

  const importBooks = useCallback(async () => {
    setLoading(true);
    try {
      const books = await importBook();
      setBooks(books);
      await hydrateProgress(books.map((book) => book.id));
      showToast({ tone: "success", title: "导入完成", body: "书籍已加入本地书库，点击书卡即可开始阅读。" });
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [setBooks, setError, setLoading, showToast]);

  const importEpubBooks = useCallback(async () => {
    setLoading(true);
    try {
      const books = await importEpub();
      setBooks(books);
      await hydrateProgress(books.map((book) => book.id));
      showToast({ tone: "success", title: "EPUB 导入完成", body: "书籍已加入本地书库，阅读器会保留原书样式。" });
    } catch (error) {
      setError(messageFromError(error));
    } finally {
      setLoading(false);
    }
  }, [setBooks, setError, setLoading, showToast]);

  const removeBookById = useCallback(
    async (bookId: string) => {
      const confirmed = await confirmAction({
        title: "移除这本书？",
        body: "只会从书库中移除记录和受管副本，不会删除你导入前的原始文件。",
        confirmLabel: "移除",
        tone: "danger"
      });
      if (!confirmed) return;
      setLoading(true);
      try {
        const books = await removeBook(bookId);
        setBooks(books);
        await hydrateProgress(books.map((book) => book.id));
        showToast({ tone: "success", title: "已从书库移除", body: "原始文件不会被删除。" });
      } catch (error) {
        setError(messageFromError(error));
      } finally {
        setLoading(false);
      }
    },
    [confirmAction, setBooks, setError, setLoading, showToast]
  );

  const openReader = useCallback(
    async (bookId: string) => {
      setLoading(true);
      try {
        const currentBook = useLibraryStore.getState().books.find((book) => book.id === bookId);
        const payload = currentBook?.format === "epub" ? await openEpub(bookId) : await openBook(bookId);
        setActiveBook(payload.book, "content" in payload ? payload.content : "", "epubUrl" in payload ? payload.epubUrl : undefined);
        if (payload.progress) useLibraryStore.getState().setProgress(payload.progress);
        setReaderSettings(payload.settings);
        setScreen("reader");
      } catch (error) {
        setError(messageFromError(error));
      } finally {
        setLoading(false);
      }
    },
    [setActiveBook, setError, setLoading, setReaderSettings, setScreen]
  );

  return { refreshBooks, importBooks, importEpubBooks, removeBookById, openReader };
}

