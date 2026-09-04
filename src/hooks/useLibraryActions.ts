import { useCallback } from "react";
import { importBook, importEpub, listBooks, removeBook } from "@/services/library-service";
import { getBatchProgress, openBook, openEpub } from "@/services/reader-service";
import { useLibraryStore } from "@/stores/library-store";
import { useUIStore } from "@/stores/ui-store";
import { useAppStore } from "@/stores/app-store";
import { executeAction } from "@/utils/async-action";

async function hydrateProgress(bookIds: string[]) {
  if (bookIds.length === 0) return;
  const setProgress = useLibraryStore.getState().setProgress;
  const list = await getBatchProgress(bookIds);
  for (const progress of list) {
    setProgress(progress);
  }
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
    await executeAction(
      async () => {
        const books = await listBooks();
        setBooks(books);
        await hydrateProgress(books.map((book) => book.id));
      },
      { setLoading, setError }
    );
  }, [setBooks, setError, setLoading]);

  const importBooks = useCallback(async () => {
    await executeAction(
      async () => {
        const books = await importBook();
        setBooks(books);
        await hydrateProgress(books.map((book) => book.id));
        showToast({ tone: "success", title: "导入完成", body: "书籍已加入本地书库，点击书卡即可开始阅读。" });
      },
      { setLoading, setError }
    );
  }, [setBooks, setError, setLoading, showToast]);

  const importEpubBooks = useCallback(async () => {
    await executeAction(
      async () => {
        const books = await importEpub();
        setBooks(books);
        await hydrateProgress(books.map((book) => book.id));
        showToast({ tone: "success", title: "EPUB 导入完成", body: "书籍已加入本地书库，阅读器会保留原书样式。" });
      },
      { setLoading, setError }
    );
  }, [setBooks, setError, setLoading, showToast]);

  const removeBookById = useCallback(
    async (bookId: string, options?: { skipConfirm?: boolean }) => {
      if (!options?.skipConfirm) {
        const book = useLibraryStore.getState().books.find((item) => item.id === bookId);
        const confirmed = await confirmAction({
          title: `从书库移除「${book?.title ?? "这本书"}」？`,
          body: "书籍文件不会被删除，仅移除该书的记录及阅读进度。",
          cancelLabel: "取消",
          confirmLabel: "移除",
          tone: "danger"
        });
        if (!confirmed) return;
      }
      await executeAction(
        async () => {
          const books = await removeBook(bookId);
          setBooks(books);
          await hydrateProgress(books.map((book) => book.id));
          showToast({ tone: "success", title: "已从书库移除", body: "原始文件不会被删除。" });
        },
        { setLoading, setError }
      );
    },
    [confirmAction, setBooks, setError, setLoading, showToast]
  );

  const openReader = useCallback(
    async (bookId: string) => {
      await executeAction(
        async () => {
          const currentBook = useLibraryStore.getState().books.find((book) => book.id === bookId);
          const payload = currentBook?.format === "epub" ? await openEpub(bookId) : await openBook(bookId);
          setActiveBook(payload.book, "content" in payload ? payload.content : "", "epubUrl" in payload ? payload.epubUrl : undefined);
          if (payload.progress) useLibraryStore.getState().setProgress(payload.progress);
          setReaderSettings(payload.settings);
          setScreen("reader");
        },
        { setLoading, setError }
      );
    },
    [setActiveBook, setError, setLoading, setReaderSettings, setScreen]
  );

  return { refreshBooks, importBooks, importEpubBooks, removeBookById, openReader };
}