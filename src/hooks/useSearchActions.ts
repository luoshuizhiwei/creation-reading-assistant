import { useCallback, useRef } from "react";
import { listBooks } from "@/services/library-service";
import { openBook, openEpub } from "@/services/reader-service";
import { globalSearch } from "@/services/search-service";
import { useInspirationStore } from "@/stores/inspiration-store";
import { useLibraryStore } from "@/stores/library-store";
import { useSearchStore } from "@/stores/search-store";
import { useAppStore } from "@/stores/app-store";
import { useCreationStore } from "@/stores/creation-store";
import { useUIStore } from "@/stores/ui-store";
import { messageFromError } from "@/utils/format";
import type { SearchQuery, SearchResult } from "@/types/search";

const SEARCH_DEBOUNCE_MS = 300;
let searchRunSequence = 0;

export function useSearchActions() {
  const setKeyword = useSearchStore((state) => state.setKeyword);
  const setResults = useSearchStore((state) => state.setResults);
  const setOpen = useSearchStore((state) => state.setOpen);
  const setLoading = useSearchStore((state) => state.setLoading);
  const setError = useAppStore((state) => state.setError);
  const showToast = useUIStore((state) => state.showToast);
  const timerRef = useRef<number>();

  const runSearch = useCallback(
    (keyword: string) => {
      const sequence = ++searchRunSequence;
      setKeyword(keyword);
      if (timerRef.current) window.clearTimeout(timerRef.current);
      if (!keyword.trim()) {
        setLoading(false);
        setResults([]);
        return;
      }
      timerRef.current = window.setTimeout(() => {
        setLoading(true);
        const query: SearchQuery = { keyword, scopes: ["library", "inspiration"], limit: 60 };
        globalSearch(query)
          .then((results) => {
            if (sequence === searchRunSequence) setResults(results);
          })
          .catch((error) => {
            if (sequence === searchRunSequence) setError(messageFromError(error));
          })
          .finally(() => {
            if (sequence === searchRunSequence) setLoading(false);
          });
      }, SEARCH_DEBOUNCE_MS);
    },
    [setError, setKeyword, setLoading, setResults]
  );

  const openResult = useCallback(
    async (result: SearchResult) => {
      try {
        const appState = useAppStore.getState();
        const leaveGuard = useCreationStore.getState().leaveGuard;
        if (appState.screen === "projects" && leaveGuard && !(await leaveGuard())) return;
        if (result.target.bookId) {
          let books = useLibraryStore.getState().books;
          let book = books.find((item) => item.id === result.target.bookId);
          if (!book) {
            books = await listBooks();
            useLibraryStore.getState().setBooks(books);
            book = books.find((item) => item.id === result.target.bookId);
          }
          if (!book) throw new Error("未找到要打开的书籍");
          const payload = book.format === "epub" ? await openEpub(result.target.bookId) : await openBook(result.target.bookId);
          const content = "content" in payload ? payload.content : "";
          const epubUrl = "epubUrl" in payload ? payload.epubUrl : undefined;
          useLibraryStore.getState().setActiveBook(payload.book, content, epubUrl, result.target.epubHref);
          if (payload.progress) useLibraryStore.getState().setProgress(payload.progress);
          useLibraryStore.getState().setReaderSettings(payload.settings);
          useAppStore.getState().setScreen("reader");
          if (book.format === "epub" && !result.target.epubHref) {
            showToast({
              tone: "info",
              title: "EPUB 全文索引未建立",
              body: "已打开书籍首页；如需跳到具体章节，请先重新导入或等待索引生成。"
            });
          }
        } else if (result.target.inspirationId) {
          useInspirationStore.getState().setSelectedId(result.target.inspirationId);
          useAppStore.getState().setScreen("inspiration");
        }
        setOpen(false);
      } catch (error) {
        setError(messageFromError(error));
      }
    },
    [setError, setOpen, showToast]
  );

  return { runSearch, openResult };
}
